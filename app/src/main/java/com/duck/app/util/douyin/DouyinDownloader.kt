package com.duck.app.util.douyin

import android.util.Log
import com.duck.app.App
import com.duck.app.util.DownloadUtil
import com.duck.app.util.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-connection HTTP downloader for Douyin media.
 *
 * No yt-dlp, no cookies: the extractor already holds a signed CDN URL, so the file
 * is fetched directly and split into byte ranges that download in parallel.
 * Servers that ignore Range fall back to a single stream.
 */
object DouyinDownloader {

    private const val TAG = "DouyinDownloader"

    /** Connections per file. Douyin CDN tolerates this well; more gives diminishing returns. */
    private const val CONNECTIONS = 4

    /** Below this size the handshake cost outweighs the parallelism. */
    private const val MIN_SEGMENT_SIZE = 512L * 1024

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    suspend fun download(
        videoInfo: VideoInfo,
        preferences: DownloadUtil.DownloadPreferences,
        taskId: String,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val formats = videoInfo.formats.orEmpty().filter { !it.url.isNullOrEmpty() }
                if (formats.isEmpty()) throw IllegalStateException("no downloadable url")

                val dir = resolveDir(preferences)
                if (!dir.exists()) dir.mkdirs()

                val title = sanitize(videoInfo.title.ifEmpty { videoInfo.id })
                val saved = mutableListOf<String>()

                formats.forEachIndexed { index, format ->
                    val ext = format.ext ?: "mp4"
                    val name =
                        if (formats.size == 1) title + "." + ext
                        else title + "_" + (index + 1) + "." + ext
                    val target = File(dir, name)
                    Log.d(TAG, "fetch " + format.url + " -> " + target.name)
                    fetchFile(format.url!!, target, index, formats.size, progressCallback)
                    saved.add(target.absolutePath)
                }

                saved
            }
        }

    private fun resolveDir(preferences: DownloadUtil.DownloadPreferences): File =
        when {
            preferences.privateDirectory -> File(App.privateDownloadDir)
            preferences.extractAudio -> File(App.audioDownloadDir)
            else -> File(App.videoDownloadDir)
        }

    private suspend fun fetchFile(
        url: String,
        target: File,
        fileIndex: Int,
        fileCount: Int,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ) {
        val total = probeSize(url)
        val connections =
            when {
                total <= 0 -> 1
                total < MIN_SEGMENT_SIZE -> 1
                else -> CONNECTIONS
            }

        val written = AtomicLong(0)
        fun report() {
            val done = written.get()
            val filePercent = if (total > 0) done.toDouble() / total else 0.0
            val overall = ((fileIndex + filePercent) / fileCount * 100).toInt()
            progressCallback?.invoke(overall.toFloat(), done, target.name)
        }

        if (connections <= 1) {
            fetchRange(url, target, 0, -1) { delta -> written.addAndGet(delta); report() }
        } else {
            val chunk = total / connections
            RandomAccessFile(target, "rw").use { it.setLength(total) }
            coroutineScope {
                (0 until connections).map { i ->
                    async(Dispatchers.IO) {
                        val start = i * chunk
                        val end = if (i == connections - 1) total - 1 else (start + chunk - 1)
                        fetchRange(url, target, start, end) { delta ->
                            written.addAndGet(delta)
                            report()
                        }
                    }
                }.awaitAll()
            }
        }

        progressCallback?.invoke(((fileIndex + 1).toFloat() / fileCount) * 100f, total, target.name)
    }

    /** Reads Content-Length via a Range probe; -1 when unknown. */
    private fun probeSize(url: String): Long =
        runCatching {
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", DouyinParser.USER_AGENT)
                    .header("Range", "bytes=0-0")
                    .build()
            client.newCall(request).execute().use { response ->
                val range = response.header("Content-Range")
                if (range != null && range.contains("/")) {
                    range.substringAfterLast("/").trim().toLongOrNull() ?: -1
                } else {
                    response.body.contentLength()
                }
            }
        }.getOrDefault(-1)

    /** Downloads [start]..[end] (inclusive) into [target]; end < 0 means whole file. */
    private fun fetchRange(
        url: String,
        target: File,
        start: Long,
        end: Long,
        onBytes: (Long) -> Unit,
    ) {
        val builder =
            Request.Builder()
                .url(url)
                .header("User-Agent", DouyinParser.USER_AGENT)
                .header("Referer", "https://www.iesdouyin.com/")
        if (end >= 0) builder.header("Range", "bytes=" + start + "-" + end)

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP " + response.code)
            val input = response.body.byteStream()
            val buffer = ByteArray(64 * 1024)

            // Position the writer: append for whole-file, seek for ranges.
            val file = RandomAccessFile(target, "rw")
            try {
                if (end >= 0) file.seek(start) else file.seek(file.length())
                input.use {
                    while (true) {
                        val read = it.read(buffer)
                        if (read <= 0) break
                        file.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            } finally {
                file.close()
            }
        }
    }

    /** Keeps names usable on every filesystem. */
    private fun sanitize(raw: String): String {
        var name = raw.map { ch -> if (ch in badChars) ' ' else ch }.joinToString("")
        name = name.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        if (name.length > 80) name = name.take(80).trim()
        return name.ifEmpty { "douyin_" + System.currentTimeMillis() }
    }

    private val badChars =
        charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\r', '\n', '\t')
}