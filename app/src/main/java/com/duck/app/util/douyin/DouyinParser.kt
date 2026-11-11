package com.duck.app.util.douyin

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Douyin extractor. No login, no signature, no cookies from the user.
 *
 * Two paths, both plain HTTPS:
 *  1. shares/video page (iesdouyin) with a ttwid cookie -> full item in the SSR payload
 *  2. slidesinfo API -> full item for regular videos
 *
 * Douyin hands out ttwid on the first response and only returns the SSR payload when
 * it comes back on the second request, so the page is fetched twice.
 */
object DouyinParser {

    private const val TAG = "DouyinParser"

    const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    private const val SLIDES_API = "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.contains("douyin.com")) return false
        return lower.contains("/video/") ||
            lower.contains("/note/") ||
            lower.contains("/share/") ||
            lower.contains("modal_id=") ||
            lower.contains("v.douyin.com")
    }

    fun extractId(url: String): String? {
        val patterns =
            listOf(
                Regex("/video/(\\d+)"),
                Regex("/note/(\\d+)"),
                Regex("[?&]modal_id=(\\d+)"),
                Regex("/share/(?:video|note|slides)/(\\d+)"),
                Regex("^\\s*(\\d{15,})\\s*$"),
            )
        for (p in patterns) {
            p.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    suspend fun resolveShortLink(url: String): String =
        withContext(Dispatchers.IO) {
            if (!url.contains("v.douyin.com")) return@withContext url
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { it.request.url.toString() }
            }.getOrDefault(url)
        }

    /**
     * Fetches the share page twice: the first response sets the ttwid cookie, the second
     * (now carrying it) contains the SSR payload with the item.
     */
    suspend fun parseViaPage(input: String): DouyinPost? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolveShortLink(input)
                val id = extractId(resolved) ?: return@runCatching null
                val url = "https://www.iesdouyin.com/share/video/" + id + "/"

                // Round 1: collect cookies.
                val cookieJar = linkedMapOf<String, String>()
                fetchPage(url, null)?.also { collectCookies(it, cookieJar) }
                if (cookieJar.isEmpty()) Log.w(TAG, "no cookies issued")

                // Round 2: reuse them so the server renders the item into the HTML.
                val cookie = cookieJar.entries.joinToString("; ") { it.key + "=" + it.value }
                val page = fetchPage(url, cookie.ifEmpty { null }) ?: return@runCatching null
                collectCookies(page, cookieJar)

                val detail = parseSsr(page) ?: run {
                    Log.i(TAG, "no SSR payload in share page")
                    return@runCatching null
                }
                normalize(detail)
            }.getOrNull()
        }

    /** Unsigned API path; reliable for regular videos, refused for image posts. */
    suspend fun parseViaApi(input: String): DouyinPost? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolveShortLink(input)
                val id = extractId(resolved) ?: return@runCatching null
                val url = SLIDES_API + "?aweme_ids=%5B" + id + "]"
                val request =
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Referer", "https://www.iesdouyin.com/share/video/" + id + "/")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val root = runCatching { JSONObject(response.body.string()) }.getOrNull()
                        ?: return@runCatching null
                    val details = root.optJSONArray("aweme_details") ?: return@runCatching null
                    if (details.length() == 0) {
                        val reason =
                            root.optJSONArray("filter_list")?.optJSONObject(0)?.optInt("reason") ?: -1
                        Log.i(TAG, "api refused, reason=" + reason)
                        return@runCatching null
                    }
                    normalize(details.getJSONObject(0))
                }
            }.getOrNull()
        }

    /** Page first (handles both kinds), API as the cheaper retry. */
    suspend fun parse(context: Context, input: String): Result<DouyinPost> =
        withContext(Dispatchers.IO) {
            parseViaPage(input)?.let { return@withContext Result.success(it) }
            parseViaApi(input)?.let { return@withContext Result.success(it) }
            Result.failure(IllegalStateException("无法解析该作品"))
        }

    // ---------------------------------------------------------------- http

    private data class Page(val body: String, val cookies: List<String>)

    private fun fetchPage(url: String, cookie: String?): String? =
        runCatching {
            val builder =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://www.iesdouyin.com/")
            if (cookie != null) builder.header("Cookie", cookie)
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                lastCookies = response.headers.values("Set-Cookie").mapNotNull { parseCookie(it) }
                body
            }
        }.getOrNull()

    private var lastCookies: List<Pair<String, String>> = emptyList()

    private fun collectCookies(body: String, jar: MutableMap<String, String>) {
        lastCookies.forEach { (k, v) -> jar[k] = v }
        lastCookies = emptyList()
    }

    private fun parseCookie(raw: String): Pair<String, String>? {
        val first = raw.substringBefore(";")
        val idx = first.indexOf('=')
        if (idx <= 0) return null
        return first.substring(0, idx) to first.substring(idx + 1)
    }

    // ---------------------------------------------------------------- parse

    private fun parseSsr(html: String): JSONObject? {
        val marker = "window._ROUTER_DATA"
        var start = html.indexOf(marker)
        if (start < 0) start = html.indexOf("_ROUTER_DATA")
        if (start < 0) return null

        val braceStart = html.indexOf('{', start)
        if (braceStart < 0) return null
        // Walk to the matching brace so nested objects survive.
        var depth = 0
        var end = -1
        for (i in braceStart until html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end < 0) return null

        val json = runCatching { JSONObject(html.substring(braceStart, end + 1)) }.getOrNull()
            ?: return null
        val loader = json.optJSONObject("loaderData") ?: return null

        for (key in loader.keys()) {
            val page = loader.optJSONObject(key) ?: continue
            val info = page.optJSONObject("videoInfoRes") ?: continue
            val items = info.optJSONArray("item_list") ?: continue
            if (items.length() > 0) return items.getJSONObject(0)
        }
        return null
    }

    fun normalize(detail: JSONObject): DouyinPost {
        val authorJson = detail.optJSONObject("author")
        val author =
            authorJson?.let {
                val avatar = it.optJSONObject("avatar_thumb")?.optJSONArray("url_list")
                DouyinAuthor(
                    uid = it.optString("sec_uid"),
                    secUid = it.optString("sec_uid"),
                    nickname = it.optString("nickname"),
                    signature = it.optString("signature"),
                    followerCount = it.optLong("follower_count"),
                    avatar = avatar?.optString(0) ?: "",
                )
            }

        val imagesJson = detail.optJSONArray("images")
        val images = mutableListOf<String>()
        if (imagesJson != null) {
            for (i in 0 until imagesJson.length()) {
                val img = imagesJson.optJSONObject(i) ?: continue
                pickUrl(img.optJSONArray("url_list"))?.let { images.add(it) }
            }
        }

        val videoJson = detail.optJSONObject("video")
        val videos = mutableListOf<DouyinVideo>()
        if (videoJson != null) {
            pickUrl(videoJson.optJSONObject("play_addr")?.optJSONArray("url_list"))?.let {
                videos.add(
                    DouyinVideo(
                        url = it.replace("playwm", "play"),
                        width = videoJson.optInt("width"),
                        height = videoJson.optInt("height"),
                        bitrate = videoJson.optInt("bit_rate"),
                    )
                )
            }
            val bitRates = videoJson.optJSONArray("bit_rate")
            if (bitRates != null) {
                for (i in 0 until bitRates.length()) {
                    val br = bitRates.optJSONObject(i) ?: continue
                    val u = pickUrl(br.optJSONObject("play_addr")?.optJSONArray("url_list"))
                        ?: continue
                    val cleaned = u.replace("playwm", "play")
                    if (videos.any { it.url == cleaned }) continue
                    videos.add(
                        DouyinVideo(
                            url = cleaned,
                            width = br.optInt("width"),
                            height = br.optInt("height"),
                            bitrate = br.optInt("bit_rate"),
                        )
                    )
                }
            }
            videos.sortByDescending { it.bitrate }
        }

        val cover = videoJson?.optJSONObject("cover")?.let { pickUrl(it.optJSONArray("url_list")) }
        val musicJson = detail.optJSONObject("music")
        val music =
            musicJson?.let {
                DouyinMusic(
                    title = it.optString("title"),
                    author = it.optString("author"),
                    url = pickUrl(it.optJSONObject("play_url")?.optJSONArray("url_list")) ?: "",
                    durationMs = it.optLong("duration"),
                )
            }
        val stats = detail.optJSONObject("statistics")

        return DouyinPost(
            id = detail.optString("aweme_id"),
            desc = detail.optString("desc"),
            createTime = detail.optLong("create_time"),
            type = if (images.isNotEmpty()) "note" else "video",
            author = author,
            cover = cover,
            videos = videos,
            images = images,
            music = music,
            likeCount = stats?.optLong("digg_count") ?: 0,
            commentCount = stats?.optLong("comment_count") ?: 0,
            shareCount = stats?.optLong("share_count") ?: 0,
            durationMs = videoJson?.optLong("duration") ?: 0,
        )
    }

    /** Prefers a non-webp variant so the file opens everywhere. */
    private fun pickUrl(list: JSONArray?): String? {
        if (list == null || list.length() == 0) return null
        for (i in 0 until list.length()) {
            val u = list.optString(i)
            if (u.isNotEmpty() && !u.endsWith(".webp")) return u
        }
        val first = list.optString(0)
        return first.ifEmpty { null }
    }
}
