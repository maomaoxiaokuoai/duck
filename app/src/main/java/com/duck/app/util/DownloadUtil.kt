package com.duck.app.util

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.util.Log
import androidx.annotation.CheckResult
import com.duck.app.App
import com.duck.app.R
import com.duck.app.util.douyin.DouyinAdapter
import com.duck.app.util.douyin.DouyinDownloader
import com.duck.app.ui.page.settings.network.Cookie
import com.duck.app.util.FileUtil
import com.duck.app.util.PreferenceUtil.AUDIO_CONVERT
import com.duck.app.util.PreferenceUtil.AUDIO_FORMAT
import com.duck.app.util.PreferenceUtil.AUDIO_QUALITY
import com.duck.app.util.PreferenceUtil.COMMAND_DIRECTORY
import com.duck.app.util.PreferenceUtil.COOKIES
import com.duck.app.util.PreferenceUtil.CONCURRENT
import com.duck.app.util.PreferenceUtil.CONVERT_SUBTITLE
import com.duck.app.util.PreferenceUtil.CROP_ARTWORK
import com.duck.app.util.PreferenceUtil.DEBUG
import com.duck.app.util.PreferenceUtil.DOWNLOAD_ARCHIVE
import com.duck.app.util.PreferenceUtil.EMBED_METADATA
import com.duck.app.util.PreferenceUtil.EMBED_SUBTITLE
import com.duck.app.util.PreferenceUtil.EMBED_THUMBNAIL
import com.duck.app.util.PreferenceUtil.EXTRACT_AUDIO
import com.duck.app.util.PreferenceUtil.FORCE_IPV4
import com.duck.app.util.PreferenceUtil.FORMAT_SORTING
import com.duck.app.util.PreferenceUtil.KEEP_SUBTITLE_FILES
import com.duck.app.util.PreferenceUtil.MERGE_OUTPUT_MKV
import com.duck.app.util.PreferenceUtil.OUTPUT_TEMPLATE
import com.duck.app.util.PreferenceUtil.PLAYLIST
import com.duck.app.util.PreferenceUtil.PRIVATE_DIRECTORY
import com.duck.app.util.PreferenceUtil.PRIVATE_MODE
import com.duck.app.util.PreferenceUtil.PROXY
import com.duck.app.util.PreferenceUtil.PROXY_URL
import com.duck.app.util.PreferenceUtil.RATE_LIMIT
import com.duck.app.util.PreferenceUtil.RESTRICT_FILENAMES
import com.duck.app.util.PreferenceUtil.SDCARD_DOWNLOAD
import com.duck.app.util.PreferenceUtil.SDCARD_URI
import com.duck.app.util.PreferenceUtil.SORTING_FIELDS
import com.duck.app.util.PreferenceUtil.SPONSORBLOCK
import com.duck.app.util.PreferenceUtil.SUBDIRECTORY_EXTRACTOR
import com.duck.app.util.PreferenceUtil.SUBDIRECTORY_PLAYLIST_TITLE
import com.duck.app.util.PreferenceUtil.SUBTITLE
import com.duck.app.util.PreferenceUtil.SUBTITLE_LANGUAGE
import com.duck.app.util.PreferenceUtil.THUMBNAIL
import com.duck.app.util.PreferenceUtil.USER_AGENT
import com.duck.app.util.PreferenceUtil.USER_AGENT_STRING
import com.duck.app.util.PreferenceUtil.AUTO_SUBTITLE
import com.duck.app.util.PreferenceUtil.AUTO_TRANSLATED_SUBTITLES
]

import com.duck.app.util.douyin.DouyinParser
import com.duck.app.util.PreferenceUtil.getBoolean
import com.duck.app.util.PreferenceUtil.getInt
import com.duck.app.util.PreferenceUtil.getString
import com.duck.app.util.PreferenceUtil.getLong

/**
 * Coordinates media extraction and downloading.
 *
 * There is no yt-dlp here: extraction is handled per site by its own extractor
 * (currently Douyin) and files are fetched directly with OkHttp.
 */
object DownloadUtil {

    private const val TAG = "DownloadUtil"

    const val BASENAME = "%(title).200B"

    const val EXTENSION = ".%(ext)s"

    const val OUTPUT_TEMPLATE_DEFAULT = BASENAME + EXTENSION

    const val OUTPUT_TEMPLATE_ID = BASENAME + " %(id)s" + EXTENSION

    data class DownloadPreferences(
        val extractAudio: Boolean = PreferenceUtil.getValue(EXTRACT_AUDIO),
        val createThumbnail: Boolean = PreferenceUtil.getValue(THUMBNAIL),
        val downloadPlaylist: Boolean = PreferenceUtil.getValue(PLAYLIST),
        val subdirectoryExtractor: Boolean = PreferenceUtil.getValue(SUBDIRECTORY_EXTRACTOR),
        val subdirectoryPlaylistTitle: Boolean = SUBDIRECTORY_PLAYLIST_TITLE.getBoolean(),
        val commandDirectory: String = COMMAND_DIRECTORY.getString(),
        val downloadSubtitle: Boolean = PreferenceUtil.getValue(SUBTITLE),
        val embedSubtitle: Boolean = EMBED_SUBTITLE.getBoolean(),
        val keepSubtitle: Boolean = KEEP_SUBTITLE_FILES.getBoolean(),
        val subtitleLanguage: String = SUBTITLE_LANGUAGE.getString(),
        val autoSubtitle: Boolean = PreferenceUtil.getValue(AUTO_SUBTITLE),
        val autoTranslatedSubtitles: Boolean = AUTO_TRANSLATED_SUBTITLES.getBoolean(),
        val convertSubtitle: Int = CONVERT_SUBTITLE.getInt(),
        val concurrentFragments: Int = CONCURRENT.getInt(),
        val sponsorBlock: Boolean = PreferenceUtil.getValue(SPONSORBLOCK),
        val sponsorBlockCategory: String = PreferenceUtil.getSponsorBlockCategories(),
        val cookies: Boolean = COOKIES.getBoolean(),
        val aria2c: Boolean = PreferenceUtil.getValue(ARIA2C),
        val audioFormat: Int = AUDIO_FORMAT.getInt(),
        val audioQuality: Int = AUDIO_QUALITY.getInt(),
        val convertAudio: Boolean = AUDIO_CONVERT.getBoolean(),
        val formatSorting: Boolean = FORMAT_SORTING.getBoolean(),
        val sortingFields: String = SORTING_FIELDS.getString(),
        val audioConvertFormat: Int = PreferenceUtil.getAudioConvertFormat(),
        val videoFormat: Int = PreferenceUtil.getVideoFormat(),
        val formatIdString: String = "",
        val videoResolution: Int = PreferenceUtil.getVideoResolution(),
        val privateMode: Boolean = PreferenceUtil.getValue(PRIVATE_MODE),
        val rateLimit: Boolean = PreferenceUtil.getValue(RATE_LIMIT),
        val maxDownloadRate: String = PreferenceUtil.getMaxDownloadRate(),
        val privateDirectory: Boolean = PreferenceUtil.getValue(PRIVATE_DIRECTORY),
        val cropArtwork: Boolean = PreferenceUtil.getValue(CROP_ARTWORK),
        val sdcard: Boolean = PreferenceUtil.getValue(SDCARD_DOWNLOAD),
        val sdcardUri: String = SDCARD_URI.getString(),
        val embedThumbnail: Boolean = EMBED_THUMBNAIL.getBoolean(),
        val videoClips: List<VideoClip> = emptyList(),
        val splitByChapter: Boolean = false,
        val debug: Boolean = DEBUG.getBoolean(),
        val proxy: Boolean = PROXY.getBoolean(),
        val proxyUrl: String = PROXY_URL.getString(),
        val newTitle: String = "",
        val userAgentString: String = USER_AGENT_STRING.run {
            if (USER_AGENT.getBoolean()) getString() else ""
        },
        val outputTemplate: String = OUTPUT_TEMPLATE.getString(),
        val useDownloadArchive: Boolean = DOWNLOAD_ARCHIVE.getBoolean(),
        val embedMetadata: Boolean = EMBED_METADATA.getBoolean(),
        val restrictFilenames: Boolean = RESTRICT_FILENAMES.getBoolean(),
        val supportAv1HardwareDecoding: Boolean = checkIfAv1HardwareAccelerated(),
        val forceIpv4: Boolean = FORCE_IPV4.getBoolean(),
        val mergeAudioStream: Boolean = false,
        val mergeToMkv: Boolean = (downloadSubtitle && embedSubtitle) || MERGE_OUTPUT_MKV.getBoolean()
    )

    @CheckResult
    fun getCookieListFromDatabase(): Result<List<Cookie>> = runCatching {
        val cookiesFile = FileUtil.getCookiesFile()
        if (!cookiesFile.exists()) return@runCatching emptyList()
        val db = SQLiteDatabase.openDatabase(
            cookiesFile.absolutePath, null, OPEN_READONLY
        )
        val cursor = db.rawQuery("SELECT * FROM cookies", null)
        val cookies = mutableListOf<Cookie>()
        cursor.use {
            while (it.moveToNext()) {
                cookies.add(
                    Cookie(
                        name = it.getString(it.getColumnIndexOrThrow(CookieScheme.NAME)),
                        value = it.getString(it.getColumnIndexOrThrow(CookieScheme.VALUE)),
                        secure = it.getInt(it.getColumnIndexOrThrow(CookieScheme.SECURE)) == 1,
                        expiry = it.getLong(it.getColumnIndexOrThrow(CookieScheme.EXPIRY)),
                        domain = it.getString(it.getColumnIndexOrThrow(CookieScheme.HOST)),
                        path = it.getString(it.getColumnIndexOrThrow(CookieScheme.PATH)),
                    )
                )
            }
        }
        db.close()
        cookies
    }

    fun List<Cookie>.toCookiesFileContent(): String =
        joinToString(separator = "\n", postfix = "\n") {
            buildString {
                append(it.domain).append("\t")
                append(if (!it.includeSubdomains) "FALSE" else "TRUE").append("\t")
                append(it.path).append("\t")
                append(if (it.secure) "TRUE" else "FALSE").append("\t")
                append(it.expiry).append("\t")
                append(it.name).append("\t")
                append(it.value)
            }
        }

    fun getCookiesContentFromDatabase(): Result<String> =
        getCookieListFromDatabase().mapCatching { it.toCookiesFileContent() }

    @CheckResult
    fun fetchVideoInfoFromUrl(
        url: String,
        playlistItem: Int = 0,
        preferences: DownloadPreferences = DownloadPreferences()
    ): Result<VideoInfo> {
        return try {
            if (DouyinParser.matches(url)) {
                val post = kotlinx.coroutines.runBlocking {
                    DouyinParser.parse(App.context, url)
                }.getOrThrow()
                Result.success(DouyinAdapter.toVideoInfo(post))
            } else {
                Result.failure(UnsupportedOperationException("unsupported link"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "extract failed", e)
            Result.failure(e)
        }
    }

    @CheckResult
    fun downloadVideo(
        videoInfo: VideoInfo? = null,
        playlistUrl: String = "",
        playlistItem: Int = 0,
        taskId: String,
        downloadPreferences: DownloadPreferences,
        progressCallback: ((Float, Long, String) -> Unit)?
    ): Result<List<String>> {
        if (videoInfo == null) {
            return Result.failure(Throwable("no video info"))
        }
        return kotlinx.coroutines.runBlocking {
            DouyinDownloader.download(
                videoInfo = videoInfo,
                preferences = downloadPreferences,
                taskId = taskId,
                progressCallback = progressCallback
            )
        }
    }


    private fun connectWithDelimiter(vararg strings: String, delimiter: String): String =
        strings.filter { it.isNotEmpty() }.joinToString(delimiter)

    private fun DownloadPreferences.toAudioFormatSorter(): String = this.run {
        val format = when (audioFormat) {
            M4A -> "acodec:aac"
            OPUS -> "acodec:opus"
            else -> ""
        }
        val quality = when (audioQuality) {
            HIGH -> "abr~192"
            MEDIUM -> "abr~128"
            LOW -> "abr~64"
            else -> ""
        }
        return@run connectWithDelimiter(format, quality, delimiter = ",")
    }


    private fun DownloadPreferences.toVideoFormatSorter(): String = this.run {
        val format = when (videoFormat) {
            FORMAT_COMPATIBILITY -> "proto,vcodec:h264,ext"
            FORMAT_QUALITY -> if (supportAv1HardwareDecoding) {
                "vcodec:av01"
            } else {
                "vcodec:vp9.2"
            }

            else -> ""
        }
        val res = when (videoResolution) {
            1 -> "res:2160"
            2 -> "res:1440"
            3 -> "res:1080"
            4 -> "res:720"
            5 -> "res:480"
            6 -> "res:360"
            7 -> "+res"
            else -> ""
        }
        return@run connectWithDelimiter(format, res, delimiter = ",")
    }

    fun DownloadPreferences.toFormatSorter(): String = connectWithDelimiter(
        this.toVideoFormatSorter(), this.toAudioFormatSorter(), delimiter = ","
    )

    private fun checkIfAv1HardwareAccelerated(): Boolean {
        runCatching {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            list.codecInfos.forEach { info ->
                if (info.isEncoder) return@forEach
                info.supportedTypes.forEach { type ->
                    if (type.contains("av01", ignoreCase = true)) return true
                }
            }
        }
        return false
    }
}
