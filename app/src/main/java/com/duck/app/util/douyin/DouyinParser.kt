package com.duck.app.util.douyin

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Douyin (douyin.com) extractor.
 *
 * Douyin serves the post data inline in the HTML as a streamed React payload
 * (self.__pace_f.push). It only does that for requests carrying a valid __ac_signature
 * cookie, which [DouyinSigner] produces.
 */
object DouyinParser {

    private const val TAG = "DouyinParser"

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val BASE = "https://www.douyin.com"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    /** True when [url] points at a Douyin post. */
    fun matches(url: String): Boolean =
        url.contains("douyin.com", ignoreCase = true) &&
            (url.contains("/video/") || url.contains("/note/") || url.contains("/share/"))

    /** Accepts a share link, a canonical URL or a bare id and returns the numeric post id. */
    fun extractId(url: String): String? {
        val patterns =
            listOf(
                Regex("/video/(\\d+)"),
                Regex("/note/(\\d+)"),
                Regex("[?&]modal_id=(\\d+)"),
                Regex("^\\s*(\\d{15,})\\s*$"),
            )
        for (p in patterns) {
            p.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    /** Resolves short (v.douyin.com) links to their final destination. */
    suspend fun resolveShortLink(url: String): String =
        withContext(Dispatchers.IO) {
            if (!url.contains("v.douyin.com")) return@withContext url
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { it.request.url.toString() }
            }.getOrDefault(url)
        }

    /**
     * Fetches and parses a post. Retries once with a fresh signature, since Douyin
     * invalidates them aggressively.
     */
    suspend fun parse(context: Context, input: String): Result<DouyinPost> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolveShortLink(input)
                val id = extractId(resolved) ?: throw IllegalArgumentException("链接里没有作品 ID")
                Log.d(TAG, "parsing id " + id)

                var lastError: Exception? = null
                repeat(2) { attempt ->
                    try {
                        val nonce = fetchNonce(id)
                        val signature = DouyinSigner.sign(context, nonce)
                        if (signature == null) Log.w(TAG, "no signature, trying unsigned")

                        val cookie =
                            buildString {
                                append("__ac_nonce=").append(nonce)
                                if (signature != null) append("; __ac_signature=").append(signature)
                            }
                        val html = fetchHtml(id, cookie)
                        val detail = parseSsr(html) ?: throw IllegalStateException("页面里没有作品数据")
                        return@withContext normalize(detail)
                    } catch (e: Exception) {
                        Log.w(TAG, "attempt " + (attempt + 1) + " failed: " + e.message)
                        lastError = e
                        delay(400)
                    }
                }
                throw lastError ?: IllegalStateException("解析失败")
            }
        }

    /** Requests the page once and reads the __ac_nonce cookie Douyin hands out. */
    private fun fetchNonce(id: String): String {
        val request =
            Request.Builder()
                .url(BASE + "/note/" + id)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
        client.newCall(request).execute().use { response ->
            val cookies = response.headers.values("Set-Cookie")
            for (c in cookies) {
                val m = Regex("__ac_nonce=([^;]+)").find(c)
                if (m != null) return m.groupValues[1]
            }
        }
        throw IllegalStateException("没有拿到 __ac_nonce")
    }

    private fun fetchHtml(id: String, cookie: String): String {
        val request =
            Request.Builder()
                .url(BASE + "/note/" + id)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cookie", cookie)
                .header("Referer", BASE + "/")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP " + response.code)
            return response.body.string()
        }
    }

    /** Pulls the aweme detail object out of the streamed SSR payload. */
    private fun parseSsr(html: String): JSONObject? {
        var start = html.indexOf("self.__pace_f.push([1,")
        if (start < 0) return null

        val payloadStart = html.indexOf("[1,\"", start)
        if (payloadStart < 0) return null
        val from = payloadStart + 4
        val to = html.indexOf("])</script>", from)
        if (to < 0) return null

        var raw = html.substring(from, to)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        val js = raw.indexOf('{')
        val je = raw.lastIndexOf('}')
        if (js < 0 || je < 0) return null
        var json = raw.substring(js, je + 1)
        json = json.replace("\"\$undefined\"", "null")
        json = json.replace(Regex("\"\\\$L\\d+\""), "null")
        json = json.replace(Regex("\"\\\$D[^\"]*\""), "null")

        val candidates =
            listOf(json, json.replace(Regex(",\\s*}"), "}").replace(Regex(",\\s*]"), "]"))
        for (candidate in candidates) {
            try {
                val root = JSONObject(candidate)
                val aweme = root.optJSONObject("aweme") ?: continue
                val detail = aweme.optJSONObject("detail") ?: continue
                return detail
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    /** Maps Douyin's raw detail object onto [DouyinPost]. */
    private fun normalize(detail: JSONObject): DouyinPost {
        val authorJson = detail.optJSONObject("authorInfo")
        val author =
            authorJson?.let {
                DouyinAuthor(
                    uid = it.optString("uid"),
                    secUid = it.optString("secUid"),
                    nickname = it.optString("nickname"),
                    signature = it.optString("signature"),
                    followerCount = it.optLong("followerCount"),
                    avatar = it.optString("avatarUri"),
                )
            }

        val imagesJson = detail.optJSONArray("images")
        val imageUrls = mutableListOf<String>()
        if (imagesJson != null) {
            for (i in 0 until imagesJson.length()) {
                val img = imagesJson.optJSONObject(i) ?: continue
                val list = img.optJSONArray("urlList") ?: img.optJSONArray("downloadUrlList")
                if (list != null && list.length() > 0) {
                    list.optString(0).takeIf { it.isNotEmpty() }?.let { imageUrls.add(it) }
                }
            }
        }

        val videoJson = detail.optJSONObject("video")
        val videos = mutableListOf<DouyinVideo>()
        if (videoJson != null) {
            val bitRates = videoJson.optJSONArray("bitRateList")
            if (bitRates != null) {
                for (i in 0 until bitRates.length()) {
                    val br = bitRates.optJSONObject(i) ?: continue
                    val url = firstSrc(br.optJSONArray("playAddr")) ?: continue
                    videos.add(
                        DouyinVideo(
                            url = url,
                            width = br.optInt("width"),
                            height = br.optInt("height"),
                            size = br.optLong("dataSize"),
                            bitrate = br.optInt("bitRate"),
                        )
                    )
                }
            }
            if (videos.isEmpty()) {
                firstSrc(videoJson.optJSONArray("playAddr"))?.let {
                    videos.add(
                        DouyinVideo(
                            url = it,
                            width = videoJson.optInt("width"),
                            height = videoJson.optInt("height"),
                            size = videoJson.optLong("dataSize"),
                        )
                    )
                }
            }
            videos.sortByDescending { it.size }
        }

        val coverArray = videoJson?.optJSONArray("coverUrlList")
        val cover =
            if (coverArray != null && coverArray.length() > 0) coverArray.optString(0) else null

        val musicJson = detail.optJSONObject("music")
        val music =
            musicJson?.let {
                val playUrl = it.optJSONObject("playUrl")?.optJSONArray("urlList")
                DouyinMusic(
                    title = it.optString("title"),
                    author = it.optString("author"),
                    url = playUrl?.optString(0) ?: "",
                    durationMs = it.optLong("duration"),
                )
            }

        val stats = detail.optJSONObject("statistics")

        return DouyinPost(
            id = detail.optString("awemeId"),
            desc = detail.optString("desc"),
            createTime = detail.optLong("createTime"),
            type = if (imageUrls.isNotEmpty()) "note" else "video",
            author = author,
            cover = cover,
            videos = videos,
            images = imageUrls,
            music = music,
            likeCount = stats?.optLong("diggCount") ?: 0,
            commentCount = stats?.optLong("commentCount") ?: 0,
            shareCount = stats?.optLong("shareCount") ?: 0,
            durationMs = videoJson?.optLong("duration") ?: 0,
        )
    }

    private fun firstSrc(array: JSONArray?): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val src = item.optString("src")
            if (src.isNotEmpty()) return src
        }
        return null
    }
}
