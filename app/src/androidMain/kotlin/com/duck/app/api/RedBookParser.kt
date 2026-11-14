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
 * Reads a Xiaohongshu note without signing in.
 *
 * The site is far more permissive than Douyin: a mobile user agent plus the
 * `__INITIAL_STATE__` blob the page embeds is the whole recipe. There is no request
 * signature to compute and no challenge page to pass.
 *
 * The one trap is the agent string. A desktop UA is redirected to the login page, and that
 * page still contains a well-formed state object — just with an empty `noteData.data` —
 * so the failure reads as a missing field rather than a redirect.
 */
class RedBookParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolve(input: String): ExtractionResult = withContext(Dispatchers.IO) {
        val url = extractUrl(input)
        if (!isRedBookUrl(url)) {
            return@withContext ExtractionResult(
                status = "error",
                message = "不是小红书链接",
            )
        }

        try {
            parsePage(fetch(url))
        } catch (e: Exception) {
            Log.d(TAG, "resolve failed: " + e.message)
            ExtractionResult(status = "error", message = e.message ?: "小红书解析失败")
        }
    }

    /** True for every domain a shared note link can land on. */
    fun isRedBookUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host == "xiaohongshu.com" ||
            host.endsWith(".xiaohongshu.com") ||
            // The share shortener has shipped on several TLDs over time; all of them are
            // the same service, and the app once only knew .com, which rejected real links.
            host == "xhslink.com" ||
            host.endsWith(".xhslink.com") ||
            host == "xhslink.cn" ||
            host.endsWith(".xhslink.cn")
    }

    /**
     * The bare link inside whatever was pasted.
     *
     * Share text arrives as a sentence with the link in the middle and an invite code
     * after it.
     */
    fun extractUrl(input: String): String {
        val text = input.trim()
        val all = Regex("https?://[^\\s\\u3000]+").findAll(text).map { it.value }.toList()
        return all.firstOrNull { isRedBookUrl(it) } ?: all.firstOrNull() ?: text
    }

    private fun parsePage(html: String): ExtractionResult {
        val stateAt = html.indexOf("window.__INITIAL_STATE__")
        if (stateAt < 0) return ExtractionResult(status = "error", message = "页面结构已变化，无法解析")

        val eq = html.indexOf('=', stateAt)
        val end = html.indexOf("</script>", eq)
        if (eq < 0 || end < 0) return ExtractionResult(status = "error", message = "页面结构已变化，无法解析")

        // The blob is JavaScript, not JSON: it uses bare `undefined` where JSON wants null.
        val raw = html.substring(eq + 1, end).trim().replace("undefined", "null")

        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return ExtractionResult(status = "error", message = "小红书数据解析失败")

        // Current mobile layout nests the note at noteData.data.noteData; the desktop
        // layout used note.noteDetailMap[noteId].note. Both are still served.
        val note = root["noteData"]?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("noteData")?.jsonObject
            ?: run {
                val noteNode = root["note"]?.jsonObject ?: return@run null
                val id = noteNode["currentNoteId"]?.jsonPrimitive?.content
                if (id.isNullOrBlank() || id == "undefined") null
                else noteNode["noteDetailMap"]?.jsonObject
                    ?.get(id)?.jsonObject
                    ?.get("note")?.jsonObject
            }
            ?: return ExtractionResult(status = "error", message = "笔记不存在或需要登录才能查看")

        val stats = note["interactInfo"]?.jsonObject
        val author = note["user"]?.jsonObject
            ?.let { it["nickName"]?.jsonPrimitive?.content ?: it["nickname"]?.jsonPrimitive?.content }

        val videoUrl = pickVideoUrl(note)
        if (videoUrl.isNotBlank()) {
            return ExtractionResult(
                status = "success",
                url = videoUrl,
                urls = listOf(videoUrl),
                title = noteTitle(note) ?: "小红书视频",
                author = author,
                thumbnail = pickCover(note),
                ext = "mp4",
                platform = "小红书",
                is_gallery = false,
                media_kind = "video",
                like_count = countOf(stats, "likedCount"),
                comment_count = countOf(stats, "commentCount"),
                share_count = countOf(stats, "shareCount"),
                collect_count = countOf(stats, "collectedCount"),
            )
        }

        val images = pickImages(note)
        if (images.isEmpty()) {
            return ExtractionResult(status = "error", message = "笔记不存在或需要登录才能查看")
        }

        return ExtractionResult(
            status = "success",
            url = images.first(),
            urls = images,
            title = noteTitle(note) ?: "小红书图文",
            author = author,
            thumbnail = images.first(),
            ext = "jpg",
            platform = "小红书",
            is_gallery = images.size > 1,
            media_kind = "gallery",
            like_count = countOf(stats, "likedCount"),
            comment_count = countOf(stats, "commentCount"),
            share_count = countOf(stats, "shareCount"),
            collect_count = countOf(stats, "collectedCount"),
        )
    }


    /**
     * The note title.
     *
     * Video notes often leave \`title\` empty and put the caption in \`desc\`; the card
     * showed the fallback string for those until this looked in both places.
     */
    private fun noteTitle(note: JsonObject): String? =
        note["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: note["desc"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    /** The still shown on the card: the first page of the gallery, else the video frame. */
    private fun pickCover(note: JsonObject): String? =
        pickImages(note).firstOrNull()
            ?: note["video"]?.jsonObject
                ?.get("image")?.jsonObject
                ?.get("firstFrame")?.jsonPrimitive?.content

    /**
     * The h265 stream first.
     *
     * Xiaohongshu burns the author name and logo into the h264 share stream; the h265
     * transcode is clean. Older notes only carry h264, hence the fallback.
     */
    private fun pickVideoUrl(note: JsonObject): String {
        val stream = note["video"]?.jsonObject
            ?.get("media")?.jsonObject
            ?.get("stream")?.jsonObject
            ?: return ""
        for (codec in listOf("h265", "h264")) {
            stream[codec]?.jsonArray
                ?.firstOrNull { it is JsonObject }
                ?.jsonObject
                ?.get("masterUrl")?.jsonPrimitive?.content
                ?.takeIf { it.startsWith("http") }
                ?.let { return it }
        }
        return ""
    }

    /**
     * Every page of a gallery.
     *
     * A live photo (小红书's moving image) is still one page here: it carries both a still
     * and a short clip, and the still is what the gallery grid shows.
     */
    private fun pickImages(note: JsonObject): List<String> {
        val list = note["imageList"]?.jsonArray ?: JsonArray(emptyList())
        return list.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            // urlDefault/url are the flat fields; infoList carries the same URLs keyed by
            // scene, with H5_DTL being the full-size one.
            val flat = item["urlDefault"]?.jsonPrimitive?.content
                ?: item["url"]?.jsonPrimitive?.content
            val fromInfo = item["infoList"]?.jsonArray
                ?.firstOrNull { it is JsonObject && it.jsonObject["imageScene"]?.jsonPrimitive?.content == "H5_DTL" }
                ?.jsonObject
                ?.get("url")?.jsonPrimitive?.content
            val url = fromInfo ?: flat ?: return@mapNotNull null
            unwatermarked(url)
        }
    }

    /**
     * Rebuilds a watermarked image URL against the clean host.
     *
     * Only paths under `notes_pre_post` can be rebuilt this way; anything else keeps its
     * original URL because the new path would 404.
     */
    private fun unwatermarked(original: String): String {
        val marker = "notes_pre_post/"
        val at = original.indexOf(marker)
        if (at < 0) return original
        val tail = original.substring(at + marker.length)
        val fileId = tail.substringBefore('!').substringBefore('?')
        if (fileId.isBlank()) return original
        val spectrum = if (tail.contains("spectrum/")) "spectrum/" else ""
        return "https://ci.xiaohongshu.com/$marker$spectrum$fileId?imageView2/format/jpg"
    }

    /**
     * Reads a count that may arrive as a number, a plain string, or a string with thousand
     * separators and a K/M suffix — `22.9K` is common on large notes.
     */
    private fun countOf(stats: JsonObject?, key: String): Long? {
        val text = stats?.get(key)?.jsonPrimitive?.content?.trim() ?: return null
        if (text.isEmpty()) return null
        val multiplier = when {
            text.endsWith("K", ignoreCase = true) -> 1_000L
            text.endsWith("M", ignoreCase = true) -> 1_000_000L
            text.endsWith("万") -> 10_000L
            else -> 1L
        }
        val numeric = text.trimEnd('K', 'k', 'M', 'm', '万').replace(",", "")
        return numeric.toDoubleOrNull()?.let { (it * multiplier).toLong() }
    }

    private fun fetch(url: String): String {
        var connection: HttpURLConnection? = null
        var current = url
        var hops = 0
        try {
            while (hops < MAX_REDIRECTS) {
                connection = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", MOBILE_USER_AGENT)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
                    setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank()) break
                    current = if (location.startsWith("http")) location
                    else URL(URL(current), location).toString()
                    hops++
                    continue
                }
                if (code !in 200..299) throw Exception("小红书返回 " + code)
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
            throw Exception("重定向次数过多")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "RedBookParser"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5

        // A desktop agent gets redirected to the login page, so this must stay mobile.
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                "Mobile/15E148 Safari/604.1"
    }
}
