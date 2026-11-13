package com.duck.app.api

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Resolves a Douyin share link into a direct, playable media URL.
 *
 * The app ships no external extraction engine any more, so everything here is plain HTTP:
 * short links are followed to the canonical note/video page, the SSR payload
 * (window._ROUTER_DATA) embedded in that page is read, and its slideshow / video entries
 * are turned into direct CDN URLs. When the page does not expose that payload (for example
 * when it renders a captcha instead) the slidesinfo endpoint is used as a fallback.
 *
 * Only direct links are produced here; downloading them is FileDownloader's job.
 */
class DouyinParser(private val signer: DouyinSigner? = null) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A single resolved media item: the direct URL plus the container it actually is. */
    data class Media(
        val url: String,
        val ext: String,
    )

    /**
     * Resolves [input] (a share text or link, optionally carrying tracking parameters) to
     * the media items behind it. Failures come back as an error result instead of throwing,
     * so callers never fall back to a removed engine.
     */
    suspend fun resolve(input: String): ExtractionResult {
        val pageUrl = extractUrl(input)
        if (!isDouyinUrl(pageUrl)) {
            return ExtractionResult(
                status = "error",
                message = "仅支持抖音链接，其他站点已不再支持解析。"
            )
        }

        return try {
            val finalUrl = followRedirects(pageUrl)
            val awemeId = extractAwemeId(finalUrl)

            // Videos are tried through slidesinfo first. It needs no signature and, as of
            // this writing, is not behind the captcha that the SSR page now shows for
            // video posts. Galleries are refused by that endpoint (filter reason 8), so a
            // failure here falls through to the signed page below.
            if (awemeId != null) {
                val fast = runCatching { resolveFromSlidesInfo(awemeId) }.getOrNull()
                if (fast != null && fast.status == "success") {
                    return fast
                }
            }

            // Douyin renders the SSR payload only for a signed request: the first call
            // just mints __ac_nonce, the signature unlocks the second.
            val nonce = fetchNonce(finalUrl)
            val signature = nonce?.let { signer?.sign(it) }
            val html = httpGet(finalUrl, nonce, signature)

            parsePage(finalUrl, html)
        } catch (e: Exception) {
            ExtractionResult(status = "error", message = e.message ?: "抖音解析失败")
        }
    }

    /** The numeric id in a douyin URL, or null when the shape is unfamiliar. */
    private fun extractAwemeId(url: String): String? =
        Regex("/?(?:video|note|share/video|share/note|share/slides)/(\\d{15,})")
            .find(url)?.groupValues?.get(1)
            ?: Regex("(\\d{18,20})").find(url)?.groupValues?.get(1)

    // ---------------------------------------------------------------- URL handling

    /** Pulls the first http(s) link out of a share text and drops known tracking params. */
    /**
     * Pulls the post link out of whatever the user pasted.
     *
     * Douyin's share text is a sentence with the link buried in it, often followed by
     * tracking tokens like "jCU:/ 08/01". A naive first-match grabs the link correctly,
     * but the whole sentence is scanned for a Douyin host first so a stray URL earlier
     * in the text cannot win.
     */
    fun extractUrl(input: String): String {
        val text = input.trim()
        val all = Regex("https?://[^\\s\u3000]+").findAll(text).map { it.value }.toList()

        val chosen = all.firstOrNull { isDouyinUrl(it) } ?: all.firstOrNull() ?: text

        return chosen
            .substringBefore('#')
            .replace(
                Regex(
                    "([?&])(previous_page|enter_from|share_token|utm_source|utm_medium|utm_campaign" +
                        "|share_app_id|wap_profile|schema_type|tt_from|from)=[^&]*"
                ),
                ""
            )
            .replace(Regex("[?&]+$"), "")
            .replace("?&", "?")
    }

    fun isDouyinUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host == "douyin.com" ||
            host.endsWith(".douyin.com") ||
            host == "iesdouyin.com" ||
            host.endsWith(".iesdouyin.com")
    }

    // ---------------------------------------------------------------- HTTP

    private fun followRedirects(url: String): String {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = open(current)
            connection.instanceFollowRedirects = false
            try {
                if (connection.responseCode !in 300..399) return current
                val location = connection.getHeaderField("Location") ?: return current
                current = when {
                    location.startsWith("http") -> location
                    location.startsWith("/") -> URL(URL(current), location).toString()
                    else -> current
                }
            } finally {
                connection.disconnect()
            }
        }
        return current
    }

    /** Fetches [url], attaching the nonce and signature that unlock the SSR payload. */
    /**
     * Performs the throwaway request whose only purpose is the __ac_nonce cookie.
     *
     * Douyin mints the nonce on the first hit and expects it echoed, signed, on the
     * second. This response body is an empty shell, so it is discarded.
     */
    private fun fetchNonce(url: String): String? {
        val connection = open(url)
        return try {
            connection.responseCode
            connection.headerFields["Set-Cookie"]
                .orEmpty()
                .asSequence()
                .firstOrNull { it.startsWith("__ac_nonce=") }
                ?.removePrefix("__ac_nonce=")
                ?.substringBefore(';')
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGet(
        url: String,
        nonce: String? = null,
        signature: String? = null,
    ): String {
        val connection = open(url)
        if (!nonce.isNullOrBlank()) {
            val jar = buildString {
                append("__ac_nonce=").append(nonce)
                if (!signature.isNullOrBlank()) {
                    append("; __ac_signature=").append(signature)
                }
            }
            connection.setRequestProperty("Cookie", jar)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("服务器返回 ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
        connection.setRequestProperty("Referer", "https://www.douyin.com/")
        connection.setRequestProperty("Accept", "text/html,application/json,*/*")
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        return connection
    }

    // ---------------------------------------------------------------- page parsing

    /**
     * Reads the __pace_f payload the signed page embeds.
     *
     * The modern page streams its data as repeated
     *   self.__pace_f.push([1,"<escaped json>"])
     * calls. One of those payloads carries the aweme object. Unlike the older
     * _ROUTER_DATA blob it uses camelCase keys (awemeId, playAddr, urlList), so it is
     * parsed separately rather than reshaped into the old path.
     */
    private fun extractPacePayload(html: String): JsonArray? {
        val prefix = "__pace_f.push([1,\""
        var index = html.indexOf(prefix)
        while (index >= 0) {
            val start = index + prefix.length
            var i = start
            var escaped = false
            while (i < html.length) {
                val c = html[i]
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') break
                i++
            }
            val raw = html.substring(start, i)
            val decoded = runCatching {
                json.parseToJsonElement("\"" + raw + "\"").jsonPrimitive.content
            }.getOrNull()
            if (decoded != null && decoded.contains("awemeId") && decoded.contains("\"detail\"")) {
                val arrayStart = decoded.indexOf('[')
                if (arrayStart >= 0) {
                    val array = runCatching {
                        json.parseToJsonElement(decoded.substring(arrayStart)).jsonArray
                    }.getOrNull()
                    if (array != null) return array
                }
            }
            index = html.indexOf(prefix, i)
        }
        return null
    }

    /** Turns one __pace_f aweme entry into a result. */
    private fun parsePaceAweme(array: JsonArray): ExtractionResult? {
        val entry = array.firstOrNull {
            it is JsonObject && it["aweme"] is JsonObject
        }?.jsonObject ?: return null

        val aweme = entry["aweme"]?.jsonObject ?: return null
        val detail = aweme["detail"]?.jsonObject ?: return null

        val items = mutableListOf<Media>()

        detail["images"]?.asArray()?.forEach { image ->
            val urls = image.jsonObject["urlList"]?.asArray()
                ?.mapNotNull { it.asString() }
                .orEmpty()
            val best = urls.lastOrNull { it.startsWith("http") } ?: urls.firstOrNull()
            if (best != null) items += Media(best, extensionOf(best, "jpg"))
        }

        val videoUrls = detail["video"]?.asObject()
            ?.get("playAddr")?.asArray()
            ?.mapNotNull { it.jsonObject["src"]?.asString() }
            .orEmpty()
        videoUrls.firstOrNull { it.startsWith("http") }?.let {
            items += Media(playUrl(it), "mp4")
        }

        if (items.isEmpty()) return null

        val author = detail["authorInfo"]?.asObject()?.get("nickname")?.asString()
        val cover = detail["video"]?.asObject()
            ?.get("cover")?.asString()

        return ExtractionResult(
            status = "success",
            url = items.first().url,
            urls = items.map { it.url },
            title = detail["desc"]?.asString()?.takeIf { it.isNotBlank() } ?: "抖音作品",
            author = author ?: "抖音",
            thumbnail = cover ?: items.first().url,
            ext = items.first().ext,
            is_gallery = items.size > 1,
        )
    }

    private fun parsePage(pageUrl: String, html: String): ExtractionResult {
        Log.d(TAG, "page len=" + html.length + " pace=" + html.split("__pace_f").size + " router=" + html.split("_ROUTER_DATA").size + " aweme=" + html.split("awemeId").size)
        // The signed page streams __pace_f; older captures still carry _ROUTER_DATA.
        extractPacePayload(html)?.let { array ->
            parsePaceAweme(array)?.let { return it }
        }

        val routerData = extractRouterData(html)
            ?: return extractAwemeId(pageUrl)?.let { resolveFromSlidesInfo(it) } ?: ExtractionResult(status = "error", message = "无法识别作品 ID")

        val loaderData = routerData["loaderData"]?.asObject()
            ?: return extractAwemeId(pageUrl)?.let { resolveFromSlidesInfo(it) } ?: ExtractionResult(status = "error", message = "无法识别作品 ID")
        val videoKey = loaderData.keys.firstOrNull {
            it.startsWith("video_(id)") || it.startsWith("note_(id)")
        }
        val page = videoKey?.let { loaderData[it]?.asObject() }
            ?: return extractAwemeId(pageUrl)?.let { resolveFromSlidesInfo(it) } ?: ExtractionResult(status = "error", message = "无法识别作品 ID")

        var title: String? = null
        var author: String? = null
        var thumbnail: String? = null
        var items: List<Media> = emptyList()

        page["videoInfoRes"]?.asObject()?.let { info ->
            val detail = info["item_list"]?.asArray()?.firstOrNull()?.asObject()
            if (detail != null) {
                title = detail["desc"]?.asString()?.takeIf { it.isNotBlank() }
                author = detail["nickname"]?.asString()
                thumbnail = detail["video"]?.asObject()
                    ?.get("cover")?.asObject()
                    ?.get("url_list")?.asArray()?.firstOrNull()?.asString()
                items = extractMediaFromItem(detail)
            }
        }

        if (items.isEmpty()) {
            items = extractMediaFromPage(page)
        }

        if (items.isEmpty()) return extractAwemeId(pageUrl)?.let { resolveFromSlidesInfo(it) } ?: ExtractionResult(status = "error", message = "无法识别作品 ID")

        return ExtractionResult(
            status = "success",
            url = items.first().url,
            urls = items.map { it.url },
            title = title ?: "抖音视频",
            author = author ?: "抖音",
            thumbnail = thumbnail ?: items.first().url,
            ext = items.first().ext,
            is_gallery = items.size > 1
        )
    }

    /** Collects every direct URL carried by an item_list entry (images and/or video). */
    private fun extractMediaFromItem(item: JsonObject): List<Media> {
        val result = mutableListOf<Media>()

        item["images"]?.asArray()?.forEach { image ->
            val urls = image.jsonObject["url_list"]?.asArray()?.mapNotNull { it.asString() }.orEmpty()
            val best = urls.lastOrNull { it.startsWith("http") } ?: urls.firstOrNull()
            if (best != null) {
                result += Media(best, extensionOf(best, "jpg"))
            }
        }

        val direct = item["video"]?.asObject()
            ?.get("play_addr")?.asObject()
            ?.get("url_list")?.asArray()
            ?.mapNotNull { it.asString() }
            ?.firstOrNull { it.startsWith("http") }
        if (direct != null) {
            result += Media(playUrl(direct), extensionOf(direct, "mp4"))
        }

        return result
    }

    /** Fallback for SSR shapes where the media sits directly on the page object. */
    private fun extractMediaFromPage(page: JsonObject): List<Media> {
        val result = mutableListOf<Media>()

        page["images"]?.asArray()?.forEach { image ->
            val urls = image.jsonObject["url_list"]?.asArray()?.mapNotNull { it.asString() }.orEmpty()
            val best = urls.lastOrNull { it.startsWith("http") } ?: urls.firstOrNull()
            if (best != null) {
                result += Media(best, extensionOf(best, "jpg"))
            }
        }

        val direct = page["video"]?.asObject()
            ?.get("play_addr")?.asObject()
            ?.get("url_list")?.asArray()
            ?.mapNotNull { it.asString() }
            ?.firstOrNull { it.startsWith("http") }
        if (direct != null) {
            result += Media(playUrl(direct), extensionOf(direct, "mp4"))
        }

        return result
    }

    /**
     * play_addr entries carry an /playwm/ (watermarked) path. Asking the CDN for /play/
     * returns the cleaner stream, and the URL stays a plain progressive download.
     */
    private fun playUrl(url: String): String = url.replace("/playwm/", "/play/")

    // ---------------------------------------------------------------- slidesinfo fallback

    /**
     * Reads the slideshow metadata endpoint. It answers with a JSON object holding the same
     * url_list shapes, so the extraction logic above is reused for the video-only case.
     */
    private fun resolveFromSlidesInfo(awemeId: String): ExtractionResult {
        val body = runCatching {
            httpGet(SLIDES_INFO_ENDPOINT + "?aweme_ids=[" + awemeId + "]")
        }.getOrElse {
            return ExtractionResult(status = "error", message = "抖音作品信息获取失败：" + it.message)
        }

        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()?.asObject()
            ?: return ExtractionResult(status = "error", message = "抖音返回了无法识别的数据")

        // The endpoint answers with a list under "aweme_details"; aweme_detail and data are
        // kept as fallbacks for the older single-object shape.
        val detail = root["aweme_details"]?.asArray()?.firstOrNull()?.asObject()
            ?: root["aweme_detail"]?.asObject()
            ?: root["data"]?.asObject()
            ?: return ExtractionResult(status = "error", message = "抖音作品不存在或已被删除")

        val items = extractMediaFromItem(detail)
        if (items.isEmpty()) {
            return ExtractionResult(status = "error", message = "未能从作品中解析出可下载的直链")
        }

        val stats = detail["statistics"]?.asObject()
        val authorName = detail["author"]?.asObject()?.get("nickname")?.asString()

        return ExtractionResult(
            status = "success",
            url = items.first().url,
            urls = items.map { it.url },
            title = detail["desc"]?.asString() ?: "抖音视频",
            author = authorName,
            create_time = detail["create_time"]?.asString()?.toLongOrNull(),
            platform = "抖音",
            like_count = stats?.get("digg_count")?.asString()?.toLongOrNull(),
            comment_count = stats?.get("comment_count")?.asString()?.toLongOrNull(),
            share_count = stats?.get("share_count")?.asString()?.toLongOrNull(),
            collect_count = stats?.get("collect_count")?.asString()?.toLongOrNull(),
            duration = detail["video"]?.asObject()?.get("duration")?.asString()?.toLongOrNull(),
            media_size = detail["video"]?.asObject()
                ?.get("play_addr")?.asObject()
                ?.get("data_size")?.asString()?.toLongOrNull(),
            media_kind = when {
                (detail["images"]?.asArray()?.size ?: 0) > 0 -> "gallery"
                else -> "video"
            },
            resolved_at = System.currentTimeMillis(),
            // The post carries a separate mp3 of its soundtrack, distinct from the
            // video container. Downloading that is what makes an audio save actually
            // audio rather than a renamed mp4.
            audio_url = detail["music"]?.asObject()
                ?.get("play_url")?.asObject()
                ?.get("url_list")?.asArray()?.firstOrNull()?.asString(),
            thumbnail = detail["video"]?.asObject()
                ?.get("cover")?.asObject()
                ?.get("url_list")?.asArray()?.firstOrNull()?.asString() ?: items.first().url,
            ext = items.first().ext,
            is_gallery = items.size > 1
        )
    }

    // ---------------------------------------------------------------- helpers

    /** Reads and decodes the window._ROUTER_DATA = {...} payload embedded in the page. */
    private fun extractRouterData(html: String): JsonObject? {
        val marker = html.indexOf("_ROUTER_DATA")
        if (marker < 0) return null

        val assignment = html.indexOf('=', marker)
        if (assignment < 0) return null

        val start = html.indexOf('{', assignment)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until html.length) {
            val c = html[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching {
                            json.parseToJsonElement(html.substring(start, i + 1)).jsonObject
                        }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private fun extensionOf(url: String, fallback: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in KNOWN_EXTENSIONS) ext else fallback
    }

    private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull()

    private fun JsonPrimitive.contentOrNull(): String? =
        if (isString) content else content.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "DouyinParser"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5
        const val SLIDES_INFO_ENDPOINT = "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/"

        // Douyin serves a trimmed share page to mobile agents; the full SSR payload that
        // carries __pace_f is only rendered for a desktop browser string.
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        val KNOWN_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "m4a", "mp3", "webm")
    }
}
