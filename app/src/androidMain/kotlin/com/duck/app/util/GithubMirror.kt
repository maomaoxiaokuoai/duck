package com.duck.app.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds the fastest working route to this project's GitHub releases.
 *
 * The version comes from the releases atom feed rather than the REST API. The API
 * allows 60 requests per hour per IP, and the free Cloudflare Workers that proxy it
 * share one egress address, so through a mirror it reports zero remaining most of the
 * time. The atom feed is served from github.com itself, carries the same tag, notes and
 * link, and is not rate limited.
 *
 * Every launch races the mirrors instead of trusting a remembered one: the network
 * changes between home, mobile data and a VPN, and a cached winner is often the slow
 * one. The probe is one small request per mirror, in parallel, behind the UI.
 */
class GithubMirror {

    /** One mirror: where to read the feed, and how to rewrite a download link. */
    private data class Mirror(
        val label: String,
        val feed: String,
        val downloadPrefix: String,
    )

    private val mirrors = listOf(
        // Self-hosted: it cannot be shut down by someone else, so it belongs in the
        // race even when a public mirror happens to be faster today.
        Mirror(
            label = "accelerate.772399.xyz",
            feed = "https://accelerate.772399.xyz/https://github.com/OWNER/REPO/releases.atom",
            downloadPrefix = "https://accelerate.772399.xyz/https://github.com/",
        ),
        Mirror(
            label = "gh-proxy.com",
            feed = "https://gh-proxy.com/https://github.com/OWNER/REPO/releases.atom",
            downloadPrefix = "https://gh-proxy.com/https://github.com/",
        ),
        Mirror(
            label = "gh.noki.icu",
            feed = "https://gh.noki.icu/https://github.com/OWNER/REPO/releases.atom",
            downloadPrefix = "https://gh.noki.icu/https://github.com/",
        ),
        Mirror(
            label = "github.com",
            feed = "https://github.com/OWNER/REPO/releases.atom",
            downloadPrefix = "https://github.com/",
        ),
    ).map { it.copy(feed = it.feed.replace("OWNER", OWNER).replace("REPO", REPO)) }

    /**
     * Races every mirror and returns the feed served by the fastest one.
     *
     * Null means nothing was reachable; callers surface that as a check failure.
     */
    suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            mirrors.map { mirror ->
                async {
                    val started = System.currentTimeMillis()
                    val body = getText(mirror.feed)
                    val elapsed = System.currentTimeMillis() - started
                    if (body != null && body.contains(MARK_ENTRY)) {
                        Log.d(TAG, mirror.label + " answered in " + elapsed + "ms")
                        Triple(mirror, body, elapsed)
                    } else {
                        null
                    }
                }
            }.awaitAll()
        }

        val best = results.filterNotNull().minByOrNull { it.third } ?: return@withContext null
        val (mirror, body, elapsed) = best
        Log.d(TAG, "using " + mirror.label + " (" + elapsed + "ms)")

        Release(
            body = body,
            mirrorLabel = mirror.label,
            downloadPrefix = mirror.downloadPrefix,
        )
    }

    /**
     * The file names attached to one release.
     *
     * The Atom feed only carries the release page, so the asset names cannot be read
     * from it. Guessing them broke whenever the naming changed - one release shipped
     * per-ABI files, the next shipped a single APK - and the download answered 404.
     * Asking the API removes the guess; an empty result means the caller should fall
     * back to the patterns it knows.
     */
    suspend fun fetchAssetNames(tag: String): List<String> = withContext(Dispatchers.IO) {
        val body = getText(
            "https://api.github.com/repos/maomaoxiaokuoai/duck/releases/tags/" + tag,
        ) ?: return@withContext emptyList()

        // Read names out of the JSON without pulling in a parser for one field.
        Regex(""name\s*":\s*"([^"]+\.apk)"")
            .findAll(body)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    /**
     * Rewrites a github.com download link to travel through the chosen mirror.
     *
     * Everything after "github.com/" is carried over, because that tail holds the
     * owner and repository. Slicing from "/releases/download/" dropped both, and the
     * mirror answered 403 for a path that does not exist.
     */
    fun accelerateDownloadUrl(url: String, downloadPrefix: String): String {
        val host = "github.com/"
        val at = url.indexOf(host)
        if (at < 0) return url
        return downloadPrefix + url.substring(at + host.length)
    }

    private fun getText(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "duck-updater")
                setRequestProperty("Accept", "application/atom+xml")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.d(TAG, "probe failed: " + e.message)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Feed body plus the route that served it. */
    data class Release(
        val body: String,
        val mirrorLabel: String,
        val downloadPrefix: String,
    )

    private companion object {
        const val TAG = "GithubMirror"
        const val PROBE_TIMEOUT_MS = 10_000
        const val MARK_ENTRY = "<entry>"
        const val OWNER = "maomaoxiaokuoai"
        const val REPO = "duck"
    }
}
