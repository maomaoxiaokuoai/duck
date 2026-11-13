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
 * github.com is slow or blocked on many networks, so every launch races a set of
 * mirrors instead of trusting a remembered one: the network the app runs on changes
 * (home, mobile data, VPN), and a cached winner would be the wrong one. The probe is
 * cheap - one small JSON request per mirror, all in parallel - and it runs behind the
 * UI, so the user never waits for it.
 *
 * A mirror only counts as usable when it returns the release JSON itself. A mirror
 * that answers 200 with an error page, or 307 to somewhere unreachable, is discarded.
 */
class GithubMirror {

    /** One mirror: where to read metadata, and how to rewrite a download link. */
    private data class Mirror(
        val label: String,
        val api: String,
        /** Prefix a github.com download path gets prepended with, empty for direct. */
        val downloadPrefix: String,
    )

    private val mirrors = listOf(
        Mirror(
            label = "gh-proxy.com",
            api = "https://gh-proxy.com/https://api.github.com" + API_PATH,
            downloadPrefix = "https://gh-proxy.com/https://github.com/",
        ),
        Mirror(
            label = "gh.noki.icu",
            api = "https://gh.noki.icu/https://api.github.com" + API_PATH,
            downloadPrefix = "https://gh.noki.icu/https://github.com/",
        ),
        Mirror(
            label = "api.github.com",
            api = "https://api.github.com" + API_PATH,
            downloadPrefix = "https://github.com/",
        ),
    )

    /**
     * Races every mirror and returns the release served by the fastest one.
     *
     * Null means nothing was reachable; callers surface that as a check failure
     * rather than silently retrying forever.
     */
    suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            mirrors.map { mirror ->
                async {
                    val started = System.currentTimeMillis()
                    val body = getText(mirror.api)
                    val elapsed = System.currentTimeMillis() - started
                    if (body != null && body.contains(MARK_VERSION)) {
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
     * Rewrites a github.com download link to travel through the chosen mirror.
     *
     * Takes the path from "/releases/download/" onward so the result points at the
     * same asset; a link without that marker is returned untouched.
     */
    /**
     * Rewrites a github.com download link to travel through the chosen mirror.
     *
     * Everything after "github.com/" is carried over, because that tail still holds the
     * owner and repository. Slicing from "/releases/download/" instead dropped both, and
     * the mirror answered 403 for a path that does not exist.
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
                setRequestProperty("Accept", "application/vnd.github+json")
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

    /** Release metadata plus the route that served it. */
    data class Release(
        val body: String,
        val mirrorLabel: String,
        val downloadPrefix: String,
    )

    private companion object {
        const val TAG = "GithubMirror"
        const val PROBE_TIMEOUT_MS = 10_000
        const val MARK_VERSION = "\"tag_name\""
        const val API_PATH = "/repos/maomaoxiaokuoai/duck/releases/latest"
    }
}
