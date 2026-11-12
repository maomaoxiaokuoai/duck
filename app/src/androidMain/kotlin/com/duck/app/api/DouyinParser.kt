package com.duck.app.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
class DouyinParser {

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
    fun resolve(input: String, cookie: String? = null): ExtractionResult {
        val pageUrl = extractUrl(input)
        if (!isDouyinUrl(pageUrl)) {
            return ExtractionResult(
                status = "error",
                message = "仅支持抖音链接，其他站点已不再支持解析。"
            )
        }

        return try {
            val browserCookie = cookie?.takeIf { it.isNotBlank() }
            val finalUrl = followRedirects(pageUrl, browserCookie)
            val html = httpGet(finalUrl, browserCookie)
            parsePage(finalUrl, html, browserCookie)
        } catch (e: Exception) {
            ExtractionResult(status = "error", message = e.message ?: "抖音解析失败")
        }
    }

    // ---------------------------------------------------------------- URL handling

    /** Pulls the first http(s) link out of a share text and drops known tracking params. */
    fun extractUrl(input: String): String {
        val raw = Regex("https?://[^\\s]+").find(input.trim())?.value ?: input.trim()
        return raw
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

    private fun followRedirects(url: String, cookie: String?): String {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = open(current, cookie)
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

    private fun httpGet(url: String, cookie: String? = null): String {
        val connection = open(url, cookie)
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("服务器返回 ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, cookie: String? = null): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
        connection.setRequestProperty("Referer", "https://www.douyin.com/")
        connection.setRequestProperty("Accept", "text/html,application/json,*/*")
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        if (!cookie.isNullOrBlank()) {
            connection.setRequestProperty("Cookie", cookie)
        }
        return connection
    }

    // ---------------------------------------------------------------- page parsing

    private fun parsePage(pageUrl: String, html: String, cookie: String?): ExtractionResult {
        val routerData = extractRouterData(html)
            ?: return resolveFromSlidesInfo(pageUrl, cookie)

        val loaderData = routerData["loaderData"]?.asObject()
            ?: return resolveFromSlidesInfo(pageUrl, cookie)
        val videoKey = loaderData.keys.firstOrNull {
            it.startsWith("video_(id)") || it.startsWith("note_(id)")
        }
        val page = videoKey?.let { loaderData[it]?.asObject() }
            ?: return resolveFromSlidesInfo(pageUrl, cookie)

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

        if (items.isEmpty()) return resolveFromSlidesInfo(pageUrl, cookie)

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
    private fun resolveFromSlidesInfo(pageUrl: String, cookie: String?): ExtractionResult {
        val awemeId = Regex("/(?:video|note|share/video|share/note)/(\\d+)")
            .find(pageUrl)?.groupValues?.get(1)
            ?: Regex("\\d{15,}").find(pageUrl)?.value
            ?: return ExtractionResult(status = "error", message = "无法从链接中识别作品 ID")

        val body = runCatching {
            httpGet(SLIDES_INFO_ENDPOINT + "?aweme_id=" + awemeId, cookie)
        }.getOrElse {
            return ExtractionResult(status = "error", message = "抖音作品信息获取失败：" + it.message)
        }

        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()?.asObject()
            ?: return ExtractionResult(status = "error", message = "抖音返回了无法识别的数据")

        val detail = root["aweme_detail"]?.asObject()
            ?: root["data"]?.asObject()
            ?: return ExtractionResult(status = "error", message = "抖音作品不存在或已被删除")

        val items = extractMediaFromItem(detail)
        if (items.isEmpty()) {
            return ExtractionResult(status = "error", message = "未能从作品中解析出可下载的直链")
        }

        return ExtractionResult(
            status = "success",
            url = items.first().url,
            urls = items.map { it.url },
            title = detail["desc"]?.asString() ?: "抖音视频",
            author = detail["nickname"]?.asString() ?: "抖音",
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
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5
        const val SLIDES_INFO_ENDPOINT = "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/"

        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        val KNOWN_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "m4a", "mp3", "webm")
    }
}
