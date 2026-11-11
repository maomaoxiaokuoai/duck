package com.duck.app.util.douyin

import android.util.Log
import com.duck.app.App
import com.duck.app.util.DownloadUtil
import com.duck.app.util.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads Douyin media straight from the CDN.
 *
 * yt-dlp needs fresh cookies for Douyin, but the extracted item already contains
 * signed CDN URLs, so the bytes can be pulled directly.
 */
object DouyinDownloader {

    private const val TAG = "DouyinDownloader"

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
                if (formats.isEmpty()) throw IllegalStateException("没有可下载的地址")

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
                    Log.d(TAG, "downloading " + format.url + " -> " + target.name)
                    fetch(format.url!!, target, index, formats.size, progressCallback)
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

    private fun fetch(
        url: String,
        target: File,
        index: Int,
        total: Int,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ) {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", DouyinParser.USER_AGENT)
                .header("Referer", "https://www.iesdouyin.com/")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP " + response.code)
            val body = response.body
            val length = body.contentLength()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (length > 0) {
                            val percent = ((written * 100) / length).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                val overall = ((index + percent / 100.0) / total * 100).toInt()
                                progressCallback?.invoke(overall.toFloat(), written, target.name)
                            }
                        }
                    }
                }
            }
        }
        progressCallback?.invoke(((index + 1).toFloat() / total) * 100f, target.length(), target.name)
    }

    /** Keeps names usable on every filesystem. */
    private fun sanitize(raw: String): String {
        var name = raw.map { ch ->
            if (ch in badChars) ' ' else ch
        }.joinToString("")
        name = name.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        if (name.length > 80) name = name.take(80).trim()
        return name.ifEmpty { "douyin_" + System.currentTimeMillis() }
    }

    private val badChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\r', '\n', '\t')
}