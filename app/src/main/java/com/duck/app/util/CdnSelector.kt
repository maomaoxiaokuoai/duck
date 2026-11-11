package com.duck.app.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Picks the fastest reachable GitHub mirror.
 *
 * Direct access to api.github.com / github.com is unreliable on many networks, so every
 * request goes through a mirror by default. Mirrors change often, so the list is probed
 * in parallel and the lowest-latency one wins; the result is cached for the session and
 * persisted so later launches start warm.
 *
 * Measured on 2026-11-11 against a 43 MB release asset:
 *   gh-proxy.com   17.2 MB in 20s   <- fastest, chosen as default
 *   ghfast.top      2.9 MB in 20s
 *   gh.geekertao.top 1.5 MB in 20s
 *   gh-proxy.net    reachable but slow
 */
object CdnSelector {

    private const val TAG = "CdnSelector"

    /** Probe result is reused for this long before re-measuring. */
    private const val PROBE_TTL_MS = 6 * 60 * 60 * 1000L

    /** Per-mirror probe timeout; a mirror that cannot answer quickly is not worth using. */
    private const val PROBE_TIMEOUT_MS = 4_000L

    /**
     * Known-good mirrors, ordered by measured throughput. The order only breaks ties:
     * actual latency decides at runtime.
     */
    private val MIRRORS =
        listOf(
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://gh.geekertao.top/",
            "https://gh-proxy.net/",
        )

    /** Endpoint used when every probe fails. */
    private const val FALLBACK = "https://gh-proxy.com/"

    private val probeClient =
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

    @Volatile private var chosen: String? = null
    @Volatile private var chosenAt: Long = 0L

    /**
     * Returns the mirror prefix to put in front of a GitHub URL, including the trailing slash.
     * Never returns empty while any mirror answers.
     */
    suspend fun preferredMirror(): String = withContext(Dispatchers.IO) {
        val cached = chosen
        if (cached != null && System.currentTimeMillis() - chosenAt < PROBE_TTL_MS) {
            return@withContext cached
        }
        val probeUrl = PREFERENCE_PROBE_URL
        val results =
            coroutineScope {
                MIRRORS.map { base ->
                    async {
                        val ms = measure(base + probeUrl)
                        Pair(base, ms)
                    }
                }.awaitAll()
            }

        val best = results.filter { it.second > 0 }.minByOrNull { it.second }?.first
        if (best != null) {
            Log.i(TAG, "fastest mirror: " + best + " (" + results.first { it.first == best }.second + " ms)")
            chosen = best
            chosenAt = System.currentTimeMillis()
            best
        } else {
            Log.w(TAG, "no mirror responded, using fallback")
            FALLBACK
        }
    }

    /** Mirrors in probe order, for retry loops when the chosen one fails mid-download. */
    suspend fun allMirrors(): List<String> = withContext(Dispatchers.IO) {
        val first = preferredMirror()
        listOf(first) + MIRRORS.filter { it != first } + FALLBACK
    }

    /** Milliseconds to first byte, or -1 when unreachable. */
    private fun measure(url: String): Long {
        return try {
            val request = Request.Builder().url(url).head().build()
            val start = System.nanoTime()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return -1
                (System.nanoTime() - start) / 1_000_000
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Small, always-available object to probe with. The releases API is used because the
     * mirrors rewrite it too; a HEAD against a real asset would be heavier.
     */
    private const val PREFERENCE_PROBE_URL =
        "https://api.github.com/repos/maomaoxiaokuoai/duck/releases?per_page=1"
}
