package com.duck.app.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads a Bilibili video without signing in.
 *
 * Three requests: resolve the short link, ask the view API for the post, then ask the
 * play API for the stream. None of them need a cookie, but the play API caps an
 * anonymous caller at 480p — higher tiers are tied to the account, so this is the
 * ceiling rather than a missing parameter.
 *
 * Note that Bilibili serves audio and video as separate DASH tracks. The video track
 * alone has no sound, which is why the download joins them.
 */
class BiliParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolve(input: String): ExtractionResult = withContext(Dispatchers.IO) {
        val url = extractUrl(input)
        if (!isBiliUrl(url)) {
            return@withContext ExtractionResult(status = "error", message = "不是 B 站链接")
        }

        try {
            parseVideo(url)
        } catch (e: Exception) {
            Log.d(TAG, "resolve failed: " + e.message)
            ExtractionResult(status = "error", message = e.message ?: "B 站解析失败")
        }
    }

    fun isBiliUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host == "bilibili.com" ||
            host.endsWith(".bilibili.com") ||
            host == "b23.tv" ||
            host.endsWith(".b23.tv")
    }

    fun extractUrl(input: String): String {
        val text = input.trim()
        val all = Regex("https?://[^\\s\\u3000]+").findAll(text).map { it.value }.toList()
        return all.firstOrNull { isBiliUrl(it) } ?: all.firstOrNull() ?: text
    }

    private fun parseVideo(url: String): ExtractionResult {
        val bvid = resolveBvid(url)
            ?: return ExtractionResult(status = "error", message = "无法识别视频号")

        // The view endpoint carries both the metadata and the play count; the play
        // endpoint has to be asked separately because it needs the cid from here.
        val info = getJson(VIEW_URL + bvid)
        val code = info["code"]?.asStringOrNull()?.toIntOrNull()
        if (code != null && code != 0) {
            return ExtractionResult(status = "error", message = "视频不可用（code=" + code + "）")
        }
        val data = info["data"]?.jsonObject
            ?: return ExtractionResult(status = "error", message = "视频不存在或已被删除")

        val cid = data["cid"]?.asStringOrNull()
            ?: return ExtractionResult(status = "error", message = "取不到视频分片 id")

        val stat = data["stat"]?.jsonObject
        val owner = data["owner"]?.jsonObject

        val play = getJson(PLAY_URL + bvid + "&cid=" + cid + "&qn=127&fnval=4048&fourk=1")
        val streams = play["data"]?.jsonObject

        // DASH is what Bilibili returns for anything above the legacy merged stream, and
        // it splits audio out; the merged durl is the fallback for old posts.
        val videoTracks = streams?.get("dash")?.jsonObject?.get("video")?.jsonArray
        val audioTracks = streams?.get("dash")?.jsonObject?.get("audio")?.jsonArray
        val merged = streams?.get("durl")?.jsonArray

        val videoUrl = videoTracks?.bestVideoUrl()
            ?: merged?.firstOrNull()?.jsonObject?.get("url")?.asStringOrNull()
        if (videoUrl == null) {
            return ExtractionResult(status = "error", message = "取不到播放地址")
        }

        // An anonymous caller only gets lower tiers, so the height here is the real one.
        val bestTrack = videoTracks?.bestVideoTrack()
        val resolution = bestTrack?.let { track ->
            val w = track["width"]?.asStringOrNull()
            val h = track["height"]?.asStringOrNull()
            if (w != null && h != null) w + "×" + h else null
        }

        return ExtractionResult(
            status = "success",
            url = videoUrl,
            urls = listOf(videoUrl),
            title = data["title"]?.asStringOrNull()?.takeIf { it.isNotBlank() } ?: "B 站视频",
            author = owner?.get("name")?.asStringOrNull(),
            // The API hands out http:// image links, which are blocked as mixed
            // content in an https app, so the cover came out blank.
            thumbnail = data["pic"]?.asStringOrNull()?.replaceFirst("http://", "https://"),
            ext = "mp4",
            platform = "B站",
            is_gallery = false,
            media_kind = "video",
            like_count = stat?.get("like")?.asStringOrNull()?.toLongOrNull(),
            comment_count = stat?.get("reply")?.asStringOrNull()?.toLongOrNull(),
            share_count = stat?.get("share")?.asStringOrNull()?.toLongOrNull(),
            collect_count = stat?.get("favorite")?.asStringOrNull()?.toLongOrNull(),
            play_count = stat?.get("view")?.asStringOrNull()?.toLongOrNull(),
            duration = data["duration"]?.asStringOrNull()?.toLongOrNull()?.times(1000),
            max_resolution = resolution,
            // The separated audio track is saved beside the video when present.
            audio_url = audioTracks?.bestAudioUrl(),
            http_headers = CDN_HEADERS,
        )
    }

    /**
     * The BV id behind a link.
     *
     * Share links come as b23.tv and answer 302 with the real address, so the redirect
     * is followed by hand and the id read out of the path.
     */
    private fun resolveBvid(url: String): String? {
        Regex("(BV[0-9A-Za-z]{10})").find(url)?.let { return it.value }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
        }
        val location = try {
            connection.getHeaderField("Location")
        } finally {
            connection.disconnect()
        } ?: return null
        return Regex("(BV[0-9A-Za-z]{10})").find(location)?.value
    }

    private fun getJson(url: String): JsonObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
            setRequestProperty("Referer", "https://www.bilibili.com/")
        }
        val text = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        return json.parseToJsonElement(text).jsonObject
    }

    /** The highest-resolution track, which is the first one Bilibili lists. */
    private fun JsonArray.bestVideoTrack(): JsonObject? =
        mapNotNull { it as? JsonObject }
            .maxByOrNull { (it["id"]?.asStringOrNull()?.toIntOrNull() ?: 0) }

    private fun JsonArray.bestVideoUrl(): String? =
        bestVideoTrack()?.get("baseUrl")?.asStringOrNull()
            ?: bestVideoTrack()?.get("base_url")?.asStringOrNull()

    private fun JsonArray.bestAudioUrl(): String? =
        mapNotNull { it as? JsonObject }
            .maxByOrNull { (it["id"]?.asStringOrNull()?.toIntOrNull() ?: 0) }
            ?.let { it["baseUrl"]?.asStringOrNull() ?: it["base_url"]?.asStringOrNull() }

    private fun JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private companion object {
        const val TAG = "BiliParser"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000

        const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view?bvid="
        const val PLAY_URL = "https://api.bilibili.com/x/player/playurl?bvid="

        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /** Bilibili's CDN rejects a request whose referer is not its own site. */
        val CDN_HEADERS = mapOf(
            "Referer" to "https://www.bilibili.com/",
            "User-Agent" to DESKTOP_USER_AGENT,
        )
    }
}
