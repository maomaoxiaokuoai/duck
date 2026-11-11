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
 * Douyin extractor that needs no login and no signature.
 *
 * The iesdouyin slidesinfo API serves full item details (author, stats, play URLs,
 * covers) for regular videos with a plain mobile UA. That is the fast path and
 * covers most posts.
 *
 * Notes (image posts) are filtered server-side with reason=8 on that endpoint, and
 * their share page no longer embeds SSR data, so they fall through to
 * [DouyinWebParser], which renders the real page in a WebView.
 */
object DouyinParser {

    private const val TAG = "DouyinParser"

    /** Mobile Safari UA: the API rejects desktop UAs outright. */
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

    /** True when [url] points at a Douyin post. */
    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.contains("douyin.com")) return false
        return lower.contains("/video/") ||
            lower.contains("/note/") ||
            lower.contains("/share/") ||
            lower.contains("modal_id=") ||
            lower.matches(Regex(".*v\\.douyin\\.com/\\w+.*"))
    }

    /** Accepts a share link, a canonical URL or a bare id and returns the numeric post id. */
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

    /** Resolves v.douyin.com short links to their destination. */
    suspend fun resolveShortLink(url: String): String =
        withContext(Dispatchers.IO) {
            if (!url.contains("v.douyin.com")) return@withContext url
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { it.request.url.toString() }
            }.getOrDefault(url)
        }

    /**
     * Fast unsigned path. Returns null when the API refuses the item (notes), so the
     * caller can escalate to the WebView parser.
     */
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
                    val body = response.body.string()
                    val root = runCatching { JSONObject(body) }.getOrNull() ?: return@runCatching null
                    val details = root.optJSONArray("aweme_details") ?: return@runCatching null
                    if (details.length() == 0) {
                        val filter = root.optJSONArray("filter_list")
                        val reason =
                            if (filter != null && filter.length() > 0) {
                                filter.optJSONObject(0)?.optInt("reason") ?: -1
                            } else -1
                        Log.i(TAG, "api refused item, reason=" + reason)
                        return@runCatching null
                    }
                    normalize(details.getJSONObject(0))
                }
            }.getOrNull()
        }

    /** Maps a raw aweme object onto [DouyinPost]. */
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
                val list = img.optJSONArray("url_list") ?: continue
                // Prefer a non-webp variant; Douyin sometimes only ships webp.
                var pick = ""
                for (j in 0 until list.length()) {
                    val u = list.optString(j)
                    if (u.isNotEmpty() && !u.endsWith(".webp")) {
                        pick = u
                        break
                    }
                }
                if (pick.isEmpty() && list.length() > 0) pick = list.optString(0)
                if (pick.isNotEmpty()) images.add(pick)
            }
        }

        val videoJson = detail.optJSONObject("video")
        val videos = mutableListOf<DouyinVideo>()
        if (videoJson != null) {
            val playAddr = videoJson.optJSONObject("play_addr")
            val direct = playAddr?.optJSONArray("url_list")
            if (direct != null && direct.length() > 0) {
                videos.add(
                    DouyinVideo(
                        url = direct.optString(0).replace("playwm", "play"),
                        width = videoJson.optInt("width"),
                        height = videoJson.optInt("height"),
                        bitrate = videoJson.optInt("bit_rate"),
                    )
                )
            }
            // Additional renditions, best first.
            val bitRates = videoJson.optJSONArray("bit_rate")
            if (bitRates != null) {
                for (i in 0 until bitRates.length()) {
                    val br = bitRates.optJSONObject(i) ?: continue
                    val addr = br.optJSONObject("play_addr")?.optJSONArray("url_list") ?: continue
                    val url = addr.optString(0)
                    if (url.isEmpty() || videos.any { it.url == url }) continue
                    videos.add(
                        DouyinVideo(
                            url = url.replace("playwm", "play"),
                            width = br.optInt("width"),
                            height = br.optInt("height"),
                            bitrate = br.optInt("bit_rate"),
                        )
                    )
                }
            }
            videos.sortByDescending { it.bitrate }
        }

        val cover =
            videoJson?.optJSONObject("cover")?.optJSONArray("url_list")?.let { list ->
                var pick = ""
                for (j in 0 until list.length()) {
                    val u = list.optString(j)
                    if (u.isNotEmpty() && !u.endsWith(".webp")) {
                        pick = u
                        break
                    }
                }
                pick.ifEmpty { list.optString(0) }
            }

        val musicJson = detail.optJSONObject("music")
        val music =
            musicJson?.let {
                DouyinMusic(
                    title = it.optString("title"),
                    author = it.optString("author"),
                    url = it.optJSONObject("play_url")?.optJSONArray("url_list")?.optString(0) ?: "",
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

    /** Entry point: API first, then the WebView renderer for notes. */
    suspend fun parse(context: Context, input: String): Result<DouyinPost> =
        withContext(Dispatchers.IO) {
            parseViaApi(input)?.let { return@withContext Result.success(it) }
            Log.i(TAG, "api path unavailable, falling back to WebView")
            DouyinWebParser.parse(context, input)
        }
}
