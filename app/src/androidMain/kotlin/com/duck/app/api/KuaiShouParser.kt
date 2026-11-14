package com.duck.app.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads a Kuaishou post without signing in.
 *
 * Two hops: the short link answers 302 with the real photo URL plus its cookies, and that
 * page embeds `window.INIT_STATE` with everything about the post. The widget id is the only
 * trick — `/fw/long-video/` serves a different payload than `/fw/photo/`, so the URL is
 * rewritten before the second request.
 */
class KuaiShouParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolve(input: String): ExtractionResult = withContext(Dispatchers.IO) {
        val url = extractUrl(input)
        if (!isKuaiShouUrl(url)) {
            return@withContext ExtractionResult(status = "error", message = "不是快手链接")
        }

        try {
            parseShare(url)
        } catch (e: Exception) {
            Log.d(TAG, "resolve failed: " + e.message)
            ExtractionResult(status = "error", message = e.message ?: "快手解析失败")
        }
    }

    fun isKuaiShouUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host == "kuaishou.com" ||
            host.endsWith(".kuaishou.com") ||
            host == "kwaicdn.com" ||
            host.endsWith(".kwaicdn.com") ||
            host == "gifshow.com" ||
            host.endsWith(".gifshow.com")
    }

    fun extractUrl(input: String): String {
        val text = input.trim()
        val all = Regex("https?://[^\\s\\u3000]+").findAll(text).map { it.value }.toList()
        return all.firstOrNull { isKuaiShouUrl(it) } ?: all.firstOrNull() ?: text
    }

    /**
     * Follows the share link by hand.
     *
     * The redirect carries the cookies the photo page expects, and following it
     * automatically would lose them, which is why this does not use the platform's
     * automatic redirect handling.
     */
    private fun parseShare(shareUrl: String): ExtractionResult {
        val first = open(shareUrl, cookie = null, followRedirects = false)
        val location = first.getHeaderField("Location")
            ?: throw Exception("快手短链没有跳转")
        first.disconnect()

        val photoUrl = location.replace("/fw/long-video/", "/fw/photo/")
        val cookies = firstCookies(shareUrl)
        val page = open(photoUrl, cookie = cookies, followRedirects = true)
        val html = page.inputStream.bufferedReader().use { it.readText() }
        page.disconnect()

        val start = html.indexOf("window.INIT_STATE")
        if (start < 0) return ExtractionResult(status = "error", message = "页面结构已变化，无法解析")
        val eq = html.indexOf('=', start)
        val end = html.indexOf("</script>", eq)
        if (eq < 0 || end < 0) return ExtractionResult(status = "error", message = "页面结构已变化，无法解析")

        val raw = html.substring(eq + 1, end).trim()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return ExtractionResult(status = "error", message = "快手数据解析失败")

        // The state is keyed by widget id; the post lives in whichever entry has both
        // result and photo.
        val photo = root.values
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.containsKey("result") && it.containsKey("photo") }
            ?.get("photo")?.jsonObject
            ?: return ExtractionResult(status = "error", message = "作品不存在或已被删除")

        val caption = photo["caption"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val author = photo["userName"]?.jsonPrimitive?.content
        val cover = (photo["coverUrls"]?.jsonArray)?.firstOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
        val stats = Stats(
            like = photo["likeCount"]?.jsonPrimitive?.content?.toLongOrNull(),
            comment = photo["commentCount"]?.jsonPrimitive?.content?.toLongOrNull(),
            share = photo["shareCount"]?.jsonPrimitive?.content?.toLongOrNull(),
            collect = null,
        )

        // Images first: a gallery post has an atlas list and no video.
        val images = pickImages(photo)

        val videoUrl = (photo["mainMvUrls"]?.jsonArray)?.firstOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?.takeIf { it.startsWith("http") }

        if (images.isNotEmpty() && videoUrl.isNullOrBlank()) {
            return ExtractionResult(
                status = "success",
                url = images.first(),
                urls = images,
                title = caption ?: "快手图文",
                author = author,
                thumbnail = cover ?: images.first(),
                ext = "jpg",
                platform = "快手",
                is_gallery = images.size > 1,
                media_kind = "gallery",
                like_count = stats.like,
                comment_count = stats.comment,
                share_count = stats.share,
                http_headers = CDN_HEADERS,
            )
        }

        if (videoUrl != null) {
            return ExtractionResult(
                status = "success",
                url = videoUrl,
                urls = listOf(videoUrl),
                title = caption ?: "快手视频",
                author = author,
                thumbnail = cover,
                ext = "mp4",
                platform = "快手",
                is_gallery = false,
                media_kind = "video",
                like_count = stats.like,
                comment_count = stats.comment,
                share_count = stats.share,
                http_headers = CDN_HEADERS,
            )
        }

        return ExtractionResult(status = "error", message = "作品里没有可下载的内容")
    }

    /**
     * The atlas images.
     *
     * `ext_params.atlas.list` holds path fragments that are meaningless without the CDN
     * host beside them in `cdn`, so the two are combined rather than listed separately.
     */
    private fun pickImages(photo: JsonObject): List<String> {
        val atlas = photo["ext_params"]?.jsonObject
            ?.get("atlas")?.jsonObject
            ?: return emptyList()
        val cdn = atlas["cdn"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: return emptyList()
        val list = atlas["list"]?.jsonArray ?: JsonArray(emptyList())
        return list.mapNotNull { entry ->
            val path = entry.jsonPrimitive.content
            if (path.isBlank()) null else "https://" + cdn + "/" + path.removePrefix("/")
        }
    }

    private fun open(url: String, cookie: String?, followRedirects: Boolean): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = followRedirects
            requestMethod = "GET"
            setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            setRequestProperty("Referer", "https://v.kuaishou.com/")
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        }

    /** Cookies from the first hop, which the photo page requires. */
    private fun firstCookies(shareUrl: String): String {
        val connection = open(shareUrl, cookie = null, followRedirects = false)
        return try {
            (connection.headerFields["Set-Cookie"] ?: emptyList())
                .map { it.substringBefore(';') }
                .filter { it.isNotBlank() }
                .joinToString("; ")
        } finally {
            connection.disconnect()
        }
    }

    private data class Stats(
        val like: Long?,
        val comment: Long?,
        val share: Long?,
        val collect: Long?,
    )

    private companion object {
        const val TAG = "KuaiShouParser"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000

        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                "Mobile/15E148 Safari/604.1"

        val CDN_HEADERS = mapOf(
            "Referer" to "https://v.kuaishou.com/",
            "User-Agent" to MOBILE_USER_AGENT,
        )
    }
}
