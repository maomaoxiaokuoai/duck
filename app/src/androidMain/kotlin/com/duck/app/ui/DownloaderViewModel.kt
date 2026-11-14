package com.duck.app.ui


import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duck.app.util.DownloadState
import com.duck.app.util.FileDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import io.ktor.client.HttpClient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

import androidx.room.Room
import com.duck.app.db.AppDatabase
import com.duck.app.model.DownloadLog
import com.duck.app.util.NotificationHelper

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.duck.app.api.DouyinParser
import com.duck.app.api.ExtractionResult
import com.duck.app.ui.theme.AppTheme

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.duck.app.util.MotionPhotoWriter


class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    /** Builds the __ac_signature the Douyin page needs; a hidden WebView runs their JSVM. */
    /** Races GitHub mirrors so updates never connect to github.com directly. */
    private val githubMirror = com.duck.app.util.GithubMirror()
    private val douyinSigner = com.duck.app.api.DouyinSigner(application)
    private val douyinParser = DouyinParser(douyinSigner)
    private val redBookParser = com.duck.app.api.RedBookParser()
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "duck_db")
        .addMigrations(
            com.duck.app.db.MIGRATION_1_2,
            com.duck.app.db.MIGRATION_2_3,
            com.duck.app.db.MIGRATION_3_4,
            com.duck.app.db.MIGRATION_4_5,
            com.duck.app.db.MIGRATION_5_6,
            com.duck.app.db.MIGRATION_6_7,
            com.duck.app.db.MIGRATION_7_8,
        )
        // A history table is only convenience data, so if a migration path is ever
        // missing the app should still open with an empty list instead of crashing on
        // launch. The destructive fallback is the lesser harm here; the alternative is
        // an install that cannot start at all.
        .fallbackToDestructiveMigration()
        .build()
    private val logDao = db.downloadLogDao()
    private val notificationHelper = NotificationHelper(application)

    private suspend fun saveThumbnailToCache(thumbnailUrl: String?, mediaPath: String?): String? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val thumbDir = java.io.File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val thumbFile = java.io.File(thumbDir, "thumb_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            
            // Remote image URL from preview
            if (!thumbnailUrl.isNullOrEmpty() && (thumbnailUrl.startsWith("http://") || thumbnailUrl.startsWith("https://"))) {
                java.net.URL(thumbnailUrl).openStream().use { input ->
                    thumbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext thumbFile.absolutePath
            }

            // Local video/media file frame extraction
            if (!mediaPath.isNullOrEmpty()) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    if (mediaPath.startsWith("content://") || mediaPath.startsWith("file://")) {
                        retriever.setDataSource(context, Uri.parse(mediaPath))
                    } else {
                        retriever.setDataSource(mediaPath)
                    }
                    val bitmap = retriever.frameAtTime
                    if (bitmap != null) {
                        thumbFile.outputStream().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        return@withContext thumbFile.absolutePath
                    }
                } catch (e: Exception) {
                    // Ignore retriever failure
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext thumbnailUrl ?: mediaPath
    }
    
    private val prefs = application.getSharedPreferences("duck_prefs", android.content.Context.MODE_PRIVATE)

    fun resetDownloadState() {
        _uiState.value = DownloadState.Idle
    }
    
    private val _consoleLogs = MutableStateFlow<List<String>>(listOf("duck@terminal:~ $ app initialized"))
    val consoleLogs: StateFlow<List<String>> = _consoleLogs

    fun logToConsole(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _consoleLogs.value = _consoleLogs.value + "[$timestamp] $message"
        android.util.Log.d("DuckApp", message)
    }

    fun clearConsole() {
        _consoleLogs.value = listOf("duck@terminal:~ $ console cleared")
    }

    fun saveSetting(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getSetting(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    suspend fun hasDownloadedUrl(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            logDao.hasUrl(url) > 0
        }
    }

    var autoCheckUpdates = mutableStateOf(prefs.getBoolean("auto_check_updates", true))
    /** A pending update: version tag, accelerated APK link, and the release notes. */
    var updateAvailable = mutableStateOf<UpdateInfo?>(null)
    var updateDownloadProgress = mutableStateOf(-1f)
    var isCheckingUpdates = mutableStateOf(false)
    var updateCheckMessage = mutableStateOf<String?>(null)

    /**
     * Downloads the release APK and hands it to the package installer.
     *
     * The request carries a User-Agent because the mirrors reject bare requests, and a
     * failure is surfaced as a short Chinese message rather than the raw exception: the
     * user cannot act on a stack of query parameters.
     */
    fun downloadUpdate(context: android.content.Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var connection: java.net.HttpURLConnection? = null
            try {
                updateDownloadProgress.value = 0f
                connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "duck-updater")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                val status = connection.responseCode
                if (status !in 200..299) {
                    throw java.io.IOException("HTTP " + status)
                }

                val fileLength = connection.contentLengthLong
                val input = connection.inputStream

                val apkFile = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                    ),
                    "duck-update.apk",
                )
                val output = java.io.FileOutputStream(apkFile)

                val data = ByteArray(8192)
                var total = 0L
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        updateDownloadProgress.value = total.toFloat() / fileLength.toFloat()
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                updateDownloadProgress.value = -1f
                updateAvailable.value = null
                withContext(Dispatchers.Main) {
                    com.duck.app.util.AppUpdater(context).installApk(apkFile.name)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateDownloadProgress.value = -1f
                    val reason = when {
                        e.message?.contains("HTTP") == true -> e.message.orEmpty()
                        e is java.net.UnknownHostException -> "网络不可用"
                        e is java.net.SocketTimeoutException -> "连接超时"
                        else -> "下载中断"
                    }
                    android.widget.Toast.makeText(
                        context,
                        "更新下载失败：" + reason + "，请稍后重试",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun toggleAutoCheckUpdates(enabled: Boolean) {
        autoCheckUpdates.value = enabled
        prefs.edit().putBoolean("auto_check_updates", enabled).apply()
        logToConsole("Auto version check ${if (enabled) "enabled" else "disabled"}")
    }

    private val client = HttpClient(io.ktor.client.engine.android.Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        // Douyin hands out several links for the same clip and the first is often a 302
        // to a signed CDN URL. Without this plugin the client treated that 302 as a
        // failure, which is why one post downloaded and the next reported an error.
        // Follow redirects: Douyin's first url_list entry answers 302 to a signed CDN
        // URL. maxJumps is the only setting needed, and it is stable across Ktor 2.x.
        install(io.ktor.client.plugins.HttpRedirect)
    }

    private val downloader = FileDownloader(application, client)

    init {
        if (autoCheckUpdates.value) {
            checkForUpdates(manual = false)
        }
    }

    /**
     * Looks for a newer release, going through whichever mirror is fastest.
     *
     * github.com is slow or unreachable on a lot of networks, so the metadata comes
     * from a raced mirror (see GithubMirror) and the download link is rewritten to
     * that same mirror. Nothing ever talks to github.com directly.
     */
    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            isCheckingUpdates.value = true
            if (manual) updateCheckMessage.value = "正在检查更新..."
            try {
                val release = githubMirror.fetchLatestRelease()
                if (release == null) {
                    if (manual) updateCheckMessage.value = "检查失败，所有加速节点均不可用"
                    return@launch
                }

                val body = release.body

                // The feed is XML; pull the first entry, which is the newest release.
                val entry = body.substringAfter("<entry>", "").substringBefore("</entry>")
                if (entry.isBlank()) {
                    if (manual) updateCheckMessage.value = "没有找到发布版本"
                    return@launch
                }

                val latestVersion = Regex("<title>([^<]+)</title>")
                    .find(entry)?.groupValues?.get(1)?.trim()
                    ?: run {
                        if (manual) updateCheckMessage.value = "无法识别版本号"
                        return@launch
                    }

                // The feed carries the release page, not the asset. The asset name
                // follows a fixed pattern, so build the direct link from the tag.
                val tag = latestVersion.removePrefix("v")
                val assetName = "duck-" + tag + "-arm64-v8a-release.apk"
                val rawUrl = "https://github.com/maomaoxiaokuoai/duck/releases/download/" +
                    latestVersion + "/" + assetName
                val apkUrl = githubMirror.accelerateDownloadUrl(rawUrl, release.downloadPrefix)

                val current = com.duck.app.BuildConfig.VERSION_NAME
                val isNewer = isVersionNewer(latestVersion, current)

                if (isNewer && !latestVersion.contains("beta", ignoreCase = true)) {
                    updateAvailable.value = UpdateInfo(
                        version = latestVersion,
                        apkUrl = apkUrl,
                        notes = extractReleaseNotes(entry),
                    )
                    updateCheckMessage.value = "发现新版本 " + latestVersion
                    logToConsole("更新可用: " + latestVersion + " via " + release.mirrorLabel)
                } else if (manual) {
                    updateCheckMessage.value = "已是最新版本 v" + current
                }
            } catch (e: Exception) {
                logToConsole("检查更新失败: " + e.message)
                if (manual) updateCheckMessage.value = "检查失败: " + e.message
            } finally {
                isCheckingUpdates.value = false
            }
        }
    }

    /**
     * Pulls the notes out of a feed entry.
     *
     * The feed embeds HTML that is itself entity-escaped, so tags are stripped and the
     * common entities decoded before the text reaches the dialog.
     */
    private fun extractReleaseNotes(entry: String): String {
        val raw = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
            .find(entry)?.groupValues?.get(1)
            ?: return ""

        val decoded = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")

        val text = decoded
            .replace(Regex("<li[^>]*>"), "\n\u00B7 ")
            .replace(Regex("</(h[1-6]|p|li|ul)>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace("\n\u00B7 ".toRegex()) { "\n\u00B7 " }
            .trim()

        return stripMarkdown(text)
    }

    /**
     * Flattens release notes to plain text.
     *
     * The dialog renders Text, not Markdown, so headings and emphasis markers would
     * otherwise show up as literal asterisks and hashes.
     */
    private fun stripMarkdown(raw: String): String {
        return raw.lineSequence()
            .map { line ->
                line
                    .replace(Regex("^#{1,6}\\s*"), "")
                    .replace("**", "")
                    .replace("__", "")
                    .trimEnd()
            }
            .map { line ->
                val bullet = Regex("^\\s*[-*+]\\s+(.*)$").find(line)
                if (bullet != null) {
                    val rest = bullet.groupValues[1].trim()
                    if (rest.isEmpty()) "" else "\u00B7 " + rest
                } else {
                    line
                }
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** True when [remote] is a higher semantic version than [local]. */
    private fun isVersionNewer(remote: String, local: String): Boolean {
        val cleanRemote = remote.substringBefore("-").removePrefix("v").trim()
        val cleanLocal = local.substringBefore("-").removePrefix("v").trim()
        val remoteParts = cleanRemote.split('.').map { it.toIntOrNull() ?: 0 }
        val localParts = cleanLocal.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrNull(i) ?: 0
            val l = localParts.getOrNull(i) ?: 0
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private var currentDownloadJob: Job? = null

    /** The in-flight preview request, cancelled when a newer one starts. */
    private var currentPreviewJob: Job? = null
    private var lastNotificationId: Int? = null

    private val activeTempFiles = java.util.Collections.synchronizedList(mutableListOf<java.io.File>())
    private val activeDownloadUris = java.util.Collections.synchronizedList(mutableListOf<Uri>())

    private val _uiState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val uiState: StateFlow<DownloadState> = _uiState

    private val _previewMetadata = MutableStateFlow<ExtractionResult?>(null)
    val previewMetadata: StateFlow<ExtractionResult?> = _previewMetadata

    /**
     * Where finished media lands.
     *
     * Movies/duck and Pictures/duck are both indexed by MediaStore, so the gallery and
     * video apps pick files up immediately. A Download/ folder stays invisible to them.
     */
    var downloadPath = mutableStateOf("Movies/duck")

    // Appearance Settings
    var themeMode = mutableIntStateOf(prefs.getInt("theme_mode", 0))
    var selectedTheme = mutableStateOf(AppTheme.valueOf(prefs.getString("app_theme", AppTheme.Dynamic.name) ?: AppTheme.Dynamic.name))

    fun updateThemeMode(mode: Int) {
        themeMode.intValue = mode
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun updateSelectedTheme(theme: AppTheme) {
        selectedTheme.value = theme
        prefs.edit().putString("app_theme", theme.name).apply()
    }
    
    // External Share Support
    private val _externalUrl = MutableStateFlow<String?>(null)
    val externalUrl: StateFlow<String?> = _externalUrl

    val downloadHistory = logDao.getAllLogs()

    fun handleSharedUrl(url: String) {
        _externalUrl.value = url
    }

    fun consumeSharedUrl() {
        _externalUrl.value = null
    }

    /**
     * Resolves [url] to direct media links.
     *
     * Douyin is the only supported source: resolution is pure HTTP via DouyinParser, and
     * every other host is reported as unsupported. There is no engine selection and no
     * fallback chain any more, so the URL is the only input that matters.
     */
    private suspend fun performExtraction(url: String): ExtractionResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Both parsers accept a share sentence, so each cleans the input
            // itself and only the matching site is contacted.
            val redBookLink = redBookParser.extractUrl(url)
            val isRedBook = redBookParser.isRedBookUrl(redBookLink)
            if (isRedBook) {
                logToConsole("解析小红书链接: " + redBookLink)
            } else {
                logToConsole("解析抖音链接: " + douyinParser.extractUrl(url))
            }

val result = if (isRedBook) redBookParser.resolve(url) else douyinParser.resolve(url)
            if (result.status == "success") {
                if (result.is_gallery == true) {
                    logToConsole("解析成功：图集，共 ${result.urls?.size ?: 0} 个直链")
                } else {
                    logToConsole("解析成功：直链已获取 (${result.ext})")
                }
            } else {
                logToConsole("解析失败：${result.message}")
            }
            result
        }

    /**
     * Fills in the byte size of a gallery.
     *
     * A gallery is many files, so its size is the sum of every page. The platform does
     * not report that, and no single page does either, so each image is asked for its
     * Content-Length. Failures are ignored: a size is nice to have, not a reason to
     * fail a preview that already resolved.
     */
    private suspend fun withGallerySize(result: ExtractionResult): ExtractionResult {
        val urls = result.urls.orEmpty().filter { it.isNotBlank() }
        if (result.is_gallery != true || urls.isEmpty()) return result
        if ((result.media_size ?: 0L) > 0L) return result

        val total = kotlinx.coroutines.coroutineScope {
            val probes = urls.map { url ->
                // A probe that fails is worth zero, not an exception: the preview
                // already resolved, and a missing size is cosmetic.
                async(Dispatchers.IO) {
                    runCatching {
                        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "HEAD"
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            instanceFollowRedirects = true
                            setRequestProperty("Referer", "https://www.douyin.com/")
                        }
                        val length = connection.contentLengthLong
                        connection.disconnect()
                        length.coerceAtLeast(0L)
                    }.getOrDefault(0L)
                }
            }
            probes.sumOf { it.await() }
        }

        return if (total > 0L) result.copy(media_size = total) else result
    }

    fun fetchPreview(url: String, quality: String, mode: String = "auto") {
        val link = douyinParser.extractUrl(url)
        currentPreviewJob?.cancel()
        currentPreviewJob = viewModelScope.launch {
            clearConsole()
            logToConsole("----------------------------------------")
            logToConsole("[SYSTEM] 正在获取抖音作品信息...")
            logToConsole("[INFO] URL: $link")
            logToConsole("[INFO] 模式: $mode")
            logToConsole("----------------------------------------")
            try {
                val result = performExtraction(link)

                if (result.status == "success") {
                    logToConsole("[SUCCESS] 解析成功")
                    logToConsole("[INFO] 标题: '${result.title}'")
                    logToConsole("[INFO] 作者: ${result.author}")
                    logToConsole("[INFO] 直链: ${result.url?.take(80)}...")
                }
                // Preview only. Downloading is a separate, explicit step: pasting a link
                // is a request to find out what it is, and the bolt is the shortcut for
                // callers that want the file straight away.
                _previewMetadata.value = withGallerySize(result)
            } catch (e: Exception) {
                logToConsole("预览异常: ${e.message}")
                _previewMetadata.value = ExtractionResult(status = "error", message = e.message ?: "解析失败")
            }
        }
    }

    /** True when [url] looks like something the parser can handle. */
    /**
     * Pulls the link out of whatever was copied.
     *
     * A Douyin share is a sentence with the link inside it, so the raw clipboard text is
     * not a URL and cannot be checked as one. Callers run it through here first, then ask
     * whether the result is a Douyin link.
     */
    /**
     * The bare link inside a share sentence, whichever site it belongs to.
     *
     * Each parser knows its own hosts, so the RedBook one is asked first and
     * only falls through to the Douyin cleaner when it finds nothing.
     */
    fun extractUrl(input: String): String {
        val redBook = redBookParser.extractUrl(input)
        if (redBookParser.isRedBookUrl(redBook)) return redBook
        return douyinParser.extractUrl(input)
    }

    /** True for a link this app can parse, from either site. */
    fun isDouyinUrl(url: String): Boolean =
        douyinParser.isDouyinUrl(url) || redBookParser.isRedBookUrl(url)

    fun clearPreview() {
        _previewMetadata.value = null
        logToConsole("Cleared preview metadata")
    }

    fun setPreviewMetadata(result: com.duck.app.api.ExtractionResult) {
        _previewMetadata.value = result
    }

    /**
     * Resolves [url] with DouyinParser and streams the resulting direct links to disk.
     *
     * There is exactly one path now: parse, then plain HTTP download. A failed parse is
     * reported to the user; nothing falls back to a removed engine.
     */
    fun downloadMedia(url: String, quality: String, mode: String = "auto") {
        // Everything downstream keys off the link, and callers may hand over a whole
        // share-sentence. Normalising once here means the parser, the log rows and the
        // duplicate check all see the same clean URL.
        val link = douyinParser.extractUrl(url)
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            _uiState.value = DownloadState.Downloading(0f)
            val notificationId = System.currentTimeMillis().toInt()
            lastNotificationId = notificationId
            logToConsole("开始处理: $link")

            try {
                logToConsole("正在解析抖音直链...")
                val result = performExtraction(link)
                if (result.status != "success") {
                    throw Exception(result.message ?: "抖音解析失败")
                }

                val isGallery = result.is_gallery == true

                // Audio saves the post's own mp3, not the video container. Falling back to
                // the video URL would put a renamed mp4 in Music/duck, which is what used to
                // happen and played as silence in most audio apps.
                if (mode == "audio") {
                    val audioUrl = result.audio_url
                        ?: throw Exception("这个作品没有独立音频")
                    downloadSingle(
                        downloadUrl = audioUrl,
                        result = result,
                        sourceUrl = link,
                        mode = mode,
                        notificationId = notificationId,
                        forceExt = "mp3",
                    )
                    return@launch
                }

                val urlsToDownload = result.urls?.takeIf { it.isNotEmpty() }
                    ?: result.url?.let { listOf(it) }
                    ?: throw Exception("未能解析出可下载的直链")
                val totalFiles = urlsToDownload.size

                if (isGallery && totalFiles > 1) {
                    downloadGallery(
                        urls = urlsToDownload,
                        result = result,
                        sourceUrl = link,
                        mode = mode,
                        notificationId = notificationId
                    )
                } else {
                    downloadSingle(
                        downloadUrl = urlsToDownload.first(),
                        result = result,
                        sourceUrl = link,
                        mode = mode,
                        notificationId = notificationId
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is kotlinx.coroutines.CancellationException) {
                    _uiState.value = DownloadState.Idle
                    lastNotificationId?.let { notificationHelper.cancelNotification(it) }
                    logToConsole("下载已取消")
                } else {
                    val errorMsg = e.message ?: "未知错误"
                    _uiState.value = DownloadState.Error(errorMsg)
                    val cachedThumb = saveThumbnailToCache(_previewMetadata.value?.thumbnail, null)
                    logDao.insertLog(
                        DownloadLog(
                            title = "下载失败",
                            url = url,
                            status = "Error: $errorMsg",
                            thumbnailPath = cachedThumb
                        )
                    )
                    logToConsole("下载失败: $errorMsg")
                }
            }
        }
    }

    /** Downloads every image of a Douyin slideshow post into Pictures/duck. */
    private suspend fun downloadGallery(
        urls: List<String>,
        result: ExtractionResult,
        sourceUrl: String,
        mode: String,
        notificationId: Int
    ) {
        val title = result.title ?: "抖音图集"
        val extension = result.ext ?: "jpg"
        val targetPath = mediaTargetPath(mode, isImage = true)
        val sanitizedTitle = sanitizeFileName(title)
        var succeededCount = 0
        // Every saved URI, so the detail sheet can show what this entry actually
        // wrote rather than re-reading the post and showing images never downloaded.
        val savedPaths = mutableListOf<String>()

        logToConsole("开始下载图集，共 ${urls.size} 个文件...")

        urls.forEachIndexed { index, downloadUrl ->
            val fileName = "${sanitizedTitle}_${index + 1}_${System.currentTimeMillis()}.$extension"

            downloader.downloadFile(
                url = downloadUrl,
                fileName = fileName,
                relativePath = targetPath,
                customFolderUri = null,
                httpHeaders = result.http_headers
            ).collect { state ->
                if (state is DownloadState.Downloading) {
                    val overallProgress = (index + state.progress) / urls.size
                    _uiState.value = DownloadState.Downloading(
                        overallProgress,
                        state.downloadedBytes,
                        state.totalBytes,
                        state.speedBps
                    )
                    notificationHelper.showProgressNotification(
                        notificationId,
                        "$title (${index + 1}/${urls.size})",
                        (overallProgress * 100).toInt()
                    )
                }
                if (state is DownloadState.Success) {
                    succeededCount++
                    downloader.finalizeFile(Uri.parse(state.path), fileName)
                    savedPaths += state.path
                    logToConsole("文件 ${index + 1}/${urls.size} 已保存")
                }
                if (state is DownloadState.Error) {
                    logToConsole("文件 ${index + 1}/${urls.size} 下载失败: ${state.message}")
                }
            }
        }

        if (succeededCount == 0) {
            throw Exception("图集下载失败，未能保存任何文件")
        }

        _uiState.value = DownloadState.Success("")
        val cachedThumb = saveThumbnailToCache(_previewMetadata.value?.thumbnail, null)
        logDao.insertLog(
            DownloadLog(
                title = "$title ($succeededCount 张)",
                url = sourceUrl,
                status = "Success",
                thumbnailPath = cachedThumb,
                author = result.author,
                createTime = result.create_time,
                platform = result.platform ?: "抖音",
                likeCount = result.like_count,
                commentCount = result.comment_count,
                shareCount = result.share_count,
                collectCount = result.collect_count,
                duration = result.duration,
                mediaKind = result.media_kind ?: if (result.is_gallery == true) "gallery" else "video",
                imageCount = succeededCount,
                imagePaths = savedPaths.joinToString(",").takeIf { it.isNotBlank() },
                fileSize = result.media_size,
            )
        )
        notificationHelper.showProgressNotification(notificationId, title, 100)
        logToConsole("图集下载完成: $succeededCount/${urls.size}")
    }

    /** Downloads a single direct link. */
    private suspend fun downloadSingle(
        downloadUrl: String,
        result: ExtractionResult,
        sourceUrl: String,
        mode: String,
        notificationId: Int,
        /** Overrides the container extension, e.g. when saving the mp3 track. */
        forceExt: String? = null,
    ) {
        val isImage = result.ext in IMAGE_EXTENSIONS
        val extension = forceExt ?: result.ext ?: if (isImage) "jpg" else "mp4"
        val title = result.title ?: "抖音作品"
        val targetPath = mediaTargetPath(mode, isImage)
        val fileName = "${sanitizeFileName(title)}_${System.currentTimeMillis()}.$extension"

        logToConsole("正在下载: $fileName")
        logToConsole("保存目录: $targetPath")

        val liveClip = result.urls.orEmpty().indexOf(downloadUrl)
            .takeIf { it >= 0 }
            ?.let { result.live_video_urls?.getOrNull(it) }

        if (!liveClip.isNullOrBlank() && isImage) {
            val merged = downloadMotionPhoto(downloadUrl, liveClip, fileName, notificationId)
            if (merged != null) {
                logToConsole("动图已保存: $targetPath/$fileName")
                val cachedThumb = saveThumbnailToCache(_previewMetadata.value?.thumbnail, null)
                logDao.insertLog(
                    DownloadLog(
                        title = title,
                        url = sourceUrl,
                        status = "Success",
                        path = targetPath + "/" + fileName,
                        thumbnailPath = cachedThumb,
                        author = result.author,
                        createTime = result.create_time,
                        platform = result.platform ?: "抖音",
                        likeCount = result.like_count,
                        commentCount = result.comment_count,
                        shareCount = result.share_count,
                        collectCount = result.collect_count,
                        duration = result.duration,
                        mediaKind = "live",
                        fileSize = merged.size.toLong(),
                    )
                )
                _uiState.value = DownloadState.Success(path = targetPath + "/" + fileName)
                return
            }
            logToConsole("动图合成失败，改为保存静态图")
        }

        downloader.downloadFile(
            url = downloadUrl,
            fileName = fileName,
            relativePath = targetPath,
            customFolderUri = null,
            httpHeaders = result.http_headers
        ).collect { state ->
            _uiState.value = state
            if (state is DownloadState.Downloading) {
                notificationHelper.showProgressNotification(
                    notificationId,
                    title,
                    (state.progress * 100).toInt()
                )
            }
            if (state is DownloadState.Success) {
                val savedUri = Uri.parse(state.path)
                downloader.finalizeFile(savedUri, fileName)
                val cachedThumb = saveThumbnailToCache(_previewMetadata.value?.thumbnail, state.path)
                logDao.insertLog(
                    DownloadLog(
                        title = title,
                        url = sourceUrl,
                        status = "Success",
                        path = state.path,
                        thumbnailPath = cachedThumb,
                        author = result.author,
                        createTime = result.create_time,
                        platform = result.platform ?: "抖音",
                        likeCount = result.like_count,
                        commentCount = result.comment_count,
                        shareCount = result.share_count,
                        collectCount = result.collect_count,
                        duration = result.duration,
                        mediaKind = if (mode == "audio") "audio" else (result.media_kind ?: "video"),
                        fileSize = queryFileSize(savedUri) ?: result.media_size,
                    )
                )
                logToConsole("文件已保存: $targetPath/$fileName")
                notificationHelper.showProgressNotification(
                    notificationId,
                    title,
                    100,
                    folderUriFor(targetPath)
                )
            }
            if (state is DownloadState.Error) {
                logToConsole("下载失败: ${state.message}")
            }
        }
    }

    /**
     * Picks the MediaStore bucket for a download.
     *
     * Images and video go to separate folders on purpose: both live under the standard
     * Pictures and Movies trees, which is what makes the system gallery index them.
     * A single folder under Download/ would not be scanned, which is why the split is
     * enforced rather than left to whatever the user typed in the path setting.
     */
    private fun mediaTargetPath(mode: String, isImage: Boolean): String = when {
        isImage -> "Pictures/duck"
        mode == "audio" -> "Music/duck"
        else -> "Movies/duck"
    }

    /**
     * The referer the CDN for [url] expects.
     *
     * Each site rejects downloads that claim to come from the other one, so the header
     * has to follow the host rather than being fixed.
     */
    /** The site a direct link belongs to, by host. */
    private fun platformOf(url: String): String {
        val host = runCatching { java.net.URL(url).host.lowercase() }.getOrNull().orEmpty()
        return when {
            host.contains("xiaohongshu") || host.contains("xhscdn") || host.contains("xhslink") -> "小红书"
            host.contains("douyin") || host.contains("amemv") || host.contains("iesdouyin") -> "抖音"
            else -> "抖音"
        }
    }
    private fun previewReferer(url: String): Map<String, String> {
        val host = runCatching { java.net.URL(url).host.lowercase() }.getOrNull().orEmpty()
        return if (host.contains("xhscdn") || host.contains("xiaohongshu")) {
            mapOf(
                "Referer" to "https://www.xiaohongshu.com/",
                "User-Agent" to MOBILE_USER_AGENT,
            )
        } else {
            mapOf("Referer" to "https://www.douyin.com/")
        }
    }


    private fun folderUriFor(targetPath: String): Uri {
        val mediaDir = when {
            targetPath.startsWith("Pictures") ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            targetPath.startsWith("Music") ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            targetPath.startsWith("Download", ignoreCase = true) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            else ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        return Uri.parse("content://media/external/file/").buildUpon()
            .appendQueryParameter("path", mediaDir.absolutePath + "/" + targetPath)
            .build()
    }

    /**
     * A filename that survives every filesystem this app writes to.
     *
     * Everything outside letters, digits and CJK becomes an underscore, which also covers
     * path separators. A title made entirely of emoji collapses to underscores, and an
     * empty one would leave a name starting with "_"; both get a readable fallback.
     */

    /**
     * Bytes actually written for [uri].
     *
     * The platform-reported size is a hint; this asks the file that was just saved, which
     * is what the history row should show. Returns null when the query fails.
     */
    private fun queryFileSize(uri: Uri): Long? = runCatching {
        getApplication<android.app.Application>().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            pfd.statSize.takeIf { it > 0 }
        }
    }.getOrNull()

    /**
     * Saves one image from a gallery post.
     *
     * A gallery download saves every page at once; this is for the grid, where the
     * user taps one tile and expects only that image on disk.
     */
    /**
     * Saves one image from a gallery post.
     *
     * [title] becomes the history row and the file name, so a saved page still says
     * which post it came from.
     *
     * When [liveVideoUrl] is present the page is a live photo: the still and the clip
     * are written out as a single Android motion photo, which the system gallery then
     * animates on its own. That is why the result is still a .jpg and not two files.
     */
    /**
     * Saves several picked pages of a gallery.
     *
     * Calling [downloadOneImage] in a loop cancelled itself: each call cancels the
     * previous job first, so only the last pick ever landed. This runs them in one
     * job and reports a single history row for the batch.
     */
    fun downloadPickedImages(imageUrls: List<String>, title: String? = null) {
        if (imageUrls.isEmpty()) return
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            val notificationId = System.currentTimeMillis().toInt()
            lastNotificationId = notificationId
            val savedPaths = mutableListOf<String>()
            var savedSize = 0L
            try {
                _uiState.value = DownloadState.Downloading(0f)
                val safe = sanitizeFileName(title ?: "image")
                val platform = platformOf(imageUrls.first())
                val cachedThumb = saveThumbnailToCache(imageUrls.first(), null)

                imageUrls.forEachIndexed { index, imageUrl ->
                    val name = safe + "_" + (index + 1) + "_" + System.currentTimeMillis() + ".jpg"
                    downloader.downloadFile(
                        url = imageUrl,
                        fileName = name,
                        relativePath = "Pictures/duck",
                        httpHeaders = previewReferer(imageUrl),
                    ).collect { state ->
                        if (state is DownloadState.Downloading) {
                            // Overall progress across the batch, not per file.
                            val done = (index + state.progress) / imageUrls.size
                            _uiState.value = DownloadState.Downloading(done.coerceIn(0f, 1f))
                        }
                        if (state is DownloadState.Success) {
                            downloader.finalizeFile(Uri.parse(state.path), name)
                            savedPaths += state.path
                            logToConsole("图片 " + (index + 1) + "/" + imageUrls.size + " 已保存")
                        }
                    }
                }

                if (savedPaths.isEmpty()) {
                    throw Exception("没有图片保存成功")
                }

                savedPaths.forEach { path ->
                    queryFileSize(Uri.parse(path))?.let { savedSize += it }
                }

                logDao.insertLog(
                    DownloadLog(
                        title = title ?: "图片",
                        url = imageUrls.first(),
                        status = "Success",
                        path = savedPaths.first(),
                        thumbnailPath = cachedThumb,
                        platform = platform,
                        mediaKind = "gallery",
                        imageCount = savedPaths.size,
                        imagePaths = savedPaths.joinToString(","),
                        fileSize = savedSize.takeIf { it > 0 },
                    )
                )
                _uiState.value = DownloadState.Success(path = savedPaths.first())
            } catch (e: Exception) {
                _uiState.value = DownloadState.Error(e.message ?: "图片下载失败")
            }
        }
    }

    fun downloadOneImage(imageUrl: String, title: String? = null, liveVideoUrl: String? = null) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            val notificationId = System.currentTimeMillis().toInt()
            lastNotificationId = notificationId
            try {
                _uiState.value = DownloadState.Downloading(0f)
                val safe = sanitizeFileName(title ?: "image")
                val name = safe + "_" + System.currentTimeMillis() + ".jpg"
                val cachedThumb = saveThumbnailToCache(imageUrl, null)
                var savedPath: String? = null
                var savedSize = 0L

                if (!liveVideoUrl.isNullOrBlank()) {
                    val merged = downloadMotionPhoto(imageUrl, liveVideoUrl, name, notificationId)
                    if (merged != null) {
                        savedPath = "Pictures/duck/" + name
                        savedSize = merged.size.toLong()
                        _uiState.value = DownloadState.Success(path = savedPath)
                        logToConsole("动图已保存: Pictures/duck/" + name)
                    } else {
                        throw Exception("动图合成失败")
                    }
                } else {
                    downloader.downloadFile(
                        url = imageUrl,
                        fileName = name,
                        relativePath = "Pictures/duck",
                        httpHeaders = previewReferer(imageUrl),
                    ).collect { state ->
                        _uiState.value = state
                        if (state is DownloadState.Downloading) savedSize = state.downloadedBytes
                        if (state is DownloadState.Success) {
                            downloader.finalizeFile(Uri.parse(state.path), name)
                            savedPath = state.path
                            logToConsole("图片已保存: Pictures/duck/" + name)
                        }
                    }
                }

                if (savedPath != null) {
                    logDao.insertLog(
                        DownloadLog(
                            title = title ?: "图片",
                            url = imageUrl,
                            status = "Success",
                            path = savedPath,
                            thumbnailPath = cachedThumb,
                            // Follow the URL, not an assumption: this path also
                            // serves Douyin galleries, and labelling those as
                            // Xiaohongshu made the history list lie.
                            platform = platformOf(imageUrl),
                            mediaKind = if (liveVideoUrl != null) "live" else "gallery",
                            fileSize = savedSize.takeIf { it > 0 },
                            // The sheet reads these; without them a single-pick
                            // record had nothing to show and fell back to the
                            // post's first image over and over.
                            imageCount = 1,
                            imagePaths = savedPath,
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DownloadState.Error(e.message ?: "图片下载失败")
            }
        }
    }

    /**
     * Downloads a still and its clip, then writes them as one motion photo.
     *
     * Both halves are fetched into memory because the XMP block has to declare the
     * clip's exact length before it can be prepended, and that length is only known
     * once the clip has fully arrived.
     */
    private suspend fun downloadMotionPhoto(
        imageUrl: String,
        videoUrl: String,
        fileName: String,
        notificationId: Int,
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val still = fetchBytes(imageUrl)
            val clip = fetchBytes(videoUrl)
            val merged = MotionPhotoWriter.build(still, clip)
                ?: return@runCatching null
            saveImageBytes(fileName, merged)
            merged
        }.getOrNull()
    }

    /** Reads a URL fully into memory; the live-photo halves are only a few hundred KB. */
    private fun fetchBytes(url: String): ByteArray {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Referer", "https://www.xiaohongshu.com/")
            setRequestProperty("User-Agent", System.getProperty("http.agent") ?: "duck")
        }
        return connection.inputStream.use { it.readBytes() }.also { connection.disconnect() }
    }

    /** Writes bytes straight into the gallery and clears the pending flag. */
    private suspend fun saveImageBytes(fileName: String, bytes: ByteArray) {
        val uri = downloader.createMediaStoreUri(fileName, "Pictures/duck")
            ?: throw Exception("无法创建文件")
        getApplication<android.app.Application>().contentResolver
            .openOutputStream(uri)?.use { it.write(bytes) } ?: throw Exception("无法写入文件")
        downloader.finalizeFile(uri, fileName)
    }
    private fun sanitizeFileName(title: String): String {
        val cleaned = title
            .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
            .trim('_')
            .take(60)
        return cleaned.ifBlank { "douyin" }
    }

    fun openSavedFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath), "*/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            logToConsole("Could not open downloads folder: ${e.message}")
        }
    }

    fun cancelDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        _uiState.value = DownloadState.Idle

        lastNotificationId?.let { notificationHelper.cancelNotification(it) }

        synchronized(activeTempFiles) {
            activeTempFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            activeTempFiles.clear()
        }

        val resolver = getApplication<Application>().contentResolver
        synchronized(activeDownloadUris) {
            activeDownloadUris.forEach { uri ->
                try {
                    resolver.delete(uri, null, null)
                } catch (e: Exception) {
                    logToConsole("URI deletion notice: ${e.message}")
                }
            }
            activeDownloadUris.clear()
        }

        logToConsole("Download cancelled. Leftover files removed successfully.")
    }

    fun deleteLog(log: DownloadLog) {
        viewModelScope.launch { 
            log.thumbnailPath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists() && file.path.contains("thumbnails")) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            logDao.deleteLog(log) 
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch { 
            try {
                val context = getApplication<Application>()
                val thumbDir = java.io.File(context.cacheDir, "thumbnails")
                if (thumbDir.exists()) thumbDir.deleteRecursively()
            } catch (_: Exception) {}
            logDao.deleteAllLogs() 
        }
    }

    fun resetState() {
        _uiState.value = DownloadState.Idle
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }

    override fun onCleared() {
        super.onCleared()
        douyinSigner.dispose()
    }
}

/**
 * A pending update, as offered to the user.
 *
 * [notes] is the release body from GitHub, so the dialog can say what actually
 * changed instead of only naming a version.
 */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String = "",
)

/** The agent the Xiaohongshu CDN expects. */
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
        "Mobile/15E148 Safari/604.1"
