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
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads a Xiaohongshu note without signing in.
 *
 * The site is far more permissive than Douyin: a mobile user agent plus the
 * `__INITIAL_STATE__` blob the page embeds is the whole recipe. There is no request
 * signature to compute, and no challenge page to pass.
 *
 * The one trap is the agent string. A desktop UA is redirected to the login page, and the
 * login page still contains a well-formed `__INITIAL_STATE__` — just with an empty
 * `noteData.data` — so the failure looks like a missing field rather than a redirect.
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
            val html = fetch(url)
            parsePage(html)
        } catch (e: Exception) {
            Log.d(TAG, "resolve failed: " + e.message)
            ExtractionResult(status = "error", message = e.message ?: "小红书解析失败")
        }
    }

    /** True for the domains a shared note link can land on. */
    fun isRedBookUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host == "xiaohongshu.com" ||
            host.endsWith(".xiaohongshu.com") ||
            host == "xhslink.com" ||
            host.endsWith(".xhslink.com")
    }

    /**
     * The bare link inside whatever was pasted.
     *
     * Xiaohongshu shares arrive as a sentence too, with the link in the middle and an
     * invite code after it.
     */
    fun extractUrl(input: String): String {
        val text = input.trim()
        val all = Regex("https?://[^\\s\\u3000]+").findAll(text).map { it.value }.toList()
        return all.firstOrNull { isRedBookUrl(it) } ?: all.firstOrNull() ?: text
    }

    private fun parsePage(html: String): ExtractionResult {
        val arrayStart = html.indexOf("window.__INITIAL_STATE__")
        if (arrayStart < 0) {
            return ExtractionResult(status = "error", message = "页面结构已变化，无法解析")
        }

        val eq = html.indexOf('=', arrayStart)
        val end = html.indexOf("</script>", eq)
        if (eq < 0 || end < 0) {
            return ExtractionResult(status = "error", message = "页面结构已变化，无法解析")
        }

        // The blob is JavaScript, not JSON: it uses bare `undefined` where JSON wants
        // null, and it is not quoted. Swapping that token is enough to parse it.
        val raw = html.substring(eq + 1, end)
            .trim()
            .replace("undefined", "null")

        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return ExtractionResult(status = "error", message = "小红书数据解析失败")

        // Current mobile layout nests the note at noteData.data.noteData; the desktop
        // layout used note.noteDetailMap[noteId].note. Both are still in use.
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

        // Videos first: a post is either a video or a gallery, never both.
        val videoUrl = pickVideoUrl(note)
        if (videoUrl.isNotBlank()) {
            val user = note["user"]?.jsonObject
            return ExtractionResult(
                status = "success",
                url = videoUrl,
                urls = listOf(videoUrl),
                title = note["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: "小红书视频",
                author = user?.get("nickName")?.jsonPrimitive?.content
                    ?: user?.get("nickname")?.jsonPrimitive?.content,
                thumbnail = firstImageUrl(note),
                ext = "mp4",
                platform = "小红书",
                is_gallery = false,
                media_kind = "video",
            )
        }

        val images = (note["imageList"]?.jsonArray ?: JsonArray(emptyList()))
            .mapNotNull { it as? JsonObject }
            .mapNotNull { item ->
                val original = item["urlDefault"]?.jsonPrimitive?.content
                    ?: item["url"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                unwatermarkedImage(original)
            }

        if (images.isEmpty()) {
            return ExtractionResult(status = "error", message = "笔记不存在或需要登录才能查看")
        }

        val user = note["user"]?.jsonObject
        return ExtractionResult(
            status = "success",
            url = images.first(),
            urls = images,
            title = note["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "小红书图文",
            author = user?.get("nickName")?.jsonPrimitive?.content
                ?: user?.get("nickname")?.jsonPrimitive?.content,
            thumbnail = images.first(),
            ext = "jpg",
            platform = "小红书",
            is_gallery = images.size > 1,
            media_kind = "gallery",
        )
    }

    /**
     * The h265 stream first.
     *
     * Xiaohongshu burns the author name and its logo into the h264 share stream; the h265
     * transcode is clean. Older notes only carry h264, hence the fallback.
     */
    private fun pickVideoUrl(note: JsonObject): String {
        val stream = note["video"]?.jsonObject
            ?.get("media")?.jsonObject
            ?.get("stream")?.jsonObject
            ?: return ""
        for (codec in listOf("h265", "h264")) {
            val list = stream[codec]?.jsonArray ?: continue
            list.firstOrNull { it is JsonObject }
                ?.jsonObject
                ?.get("masterUrl")?.jsonPrimitive?.content
                ?.takeIf { it.startsWith("http") }
                ?.let { return it }
        }
        return ""
    }

    private fun firstImageUrl(note: JsonObject): String? =
        note["imageList"]?.jsonArray?.firstOrNull()
            ?.jsonObject
            ?.let { it["urlDefault"]?.jsonPrimitive?.content ?: it["url"]?.jsonPrimitive?.content }

    /**
     * Swaps the CDN host on watermarked images.
     *
     * Only images served from `notes_pre_post` can be rebuilt this way; anything else
     * keeps its original URL because the new path would 404.
     */
    private fun unwatermarkedImage(original: String): String {
        if (!original.contains("notes_pre_post")) return original
        val fileId = original.substringAfterLast('/').substringBefore('!')
        if (fileId.isBlank()) return original
        val spectrum = if (original.contains("spectrum/")) "spectrum/" else ""
        return "https://ci.xiaohongshu.com/notes_pre_post/" + spectrum + fileId +
            "?imageView2/format/jpg"
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
                val reader: BufferedReader = connection.inputStream.bufferedReader()
                return reader.use { it.readText() }
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
