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
 * Two hops. The share link answers 302 with the photo id, and that id is posted to the
 * H5 endpoint, which answers with the full photo object. The endpoint needs no signature,
 * no cookie and no login, but it must be POST — a GET is refused by the risk check.
 *
 * Reading the widget id out of the share URL matters: `/fw/long-video/` and `/fw/photo/`
 * serve different payloads, and only the latter is the post page.
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
     * Follows the share link to the photo id, then asks the H5 endpoint for the post.
     *
     * The redirect is not followed automatically because its target is the page rather
     * than the data; the id inside it is what the endpoint wants.
     */
    private fun parseShare(shareUrl: String): ExtractionResult {
        val photoId = resolvePhotoId(shareUrl)
            ?: return ExtractionResult(status = "error", message = "无法从链接中识别作品 ID")

        val body = "{\"photoId\":\"" + photoId + "\"}"
        val connection = (URL(SIMPLE_INFO_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            setRequestProperty("Referer", "https://www.kuaishou.com/fw/photo/" + photoId)
        }

        val response = try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }

        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return ExtractionResult(status = "error", message = "快手返回内容异常")

        val resultCode = root["result"]?.jsonPrimitive?.content?.toIntOrNull()
        if (resultCode != null && resultCode != 1) {
            return ExtractionResult(
                status = "error",
                message = "作品不可用（result=" + resultCode + "）",
            )
        }

        val photo = root["photo"]?.jsonObject
            ?: return ExtractionResult(status = "error", message = "作品不存在或已被删除")

        val caption = photo["caption"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val author = photo["userName"]?.jsonPrimitive?.content
        val cover = photo["coverUrls"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
        val like = photo["likeCount"]?.jsonPrimitive?.content?.toLongOrNull()
        val comment = photo["commentCount"]?.jsonPrimitive?.content?.toLongOrNull()
        val share = photo["shareCount"]?.jsonPrimitive?.content?.toLongOrNull()

        val images = pickImages(photo)
        val videoUrl = photo["mainMvUrls"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?.takeIf { it.startsWith("http") }

        // A gallery carries an atlas list and no video; a video post is the reverse.
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
                like_count = like,
                comment_count = comment,
                share_count = share,
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
                like_count = like,
                comment_count = comment,
                share_count = share,
            )
        }

        return ExtractionResult(status = "error", message = "作品里没有可下载的内容")
    }

    /**
     * The photo id behind a share link.
     *
     * The short link answers 302 with a URL whose last path segment is the id; both
     * `/fw/photo/` and `/fw/long-video/` shapes are handled.
     */
    private fun resolvePhotoId(shareUrl: String): String? {
        if (shareUrl.contains("/fw/photo/") || shareUrl.contains("/fw/long-video/")) {
            return shareUrl.substringAfter("/fw/photo/", shareUrl.substringAfter("/fw/long-video/", ""))
                .substringBefore('?').substringBefore('/').takeIf { it.isNotBlank() }
        }

        val connection = (URL(shareUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            setRequestProperty("Referer", "https://v.kuaishou.com/")
        }

        val location = try {
            connection.getHeaderField("Location")
        } finally {
            connection.disconnect()
        } ?: return null

        val at = location.indexOf("/fw/")
        if (at < 0) return null
        return location.substring(at)
            .substringAfter("/photo/", location.substringAfter("/long-video/", ""))
            .substringBefore('?')
            .substringBefore('/')
            .takeIf { it.isNotBlank() }
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
        val cdn = atlas["cdn"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: atlas["cdnList"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("cdn")?.jsonPrimitive?.content
            ?: return emptyList()
        val list = atlas["list"]?.jsonArray ?: JsonArray(emptyList())
        return list.mapNotNull { entry ->
            val path = entry.jsonPrimitive.content
            if (path.isBlank()) null else "https://" + cdn + "/" + path.removePrefix("/")
        }
    }

    private companion object {
        const val TAG = "KuaiShouParser"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000

        /**
         * The H5 endpoint. It answers without a signature, a cookie or a login, but only
         * to POST; a GET is refused with 21 or 2001 by the risk check.
         */
        const val SIMPLE_INFO_URL =
            "https://www.kuaishou.com/rest/wd/ugH5App/photo/simple/info?kpn=KUAISHOU&subBiz=PHOTO"

        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                "Mobile/15E148 Safari/604.1"
    }
}
