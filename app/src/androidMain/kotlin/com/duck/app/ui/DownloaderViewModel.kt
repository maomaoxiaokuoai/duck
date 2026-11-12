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

enum class TerminalTheme(
    val displayName: String,
    val background: Long,
    val text: Long,
    val header: Long
) {
    CLASSIC("Classic Green", 0xFF0C0C0C, 0xFF00FF66, 0xFF1E1E1E),
    MOCHA("Mocha", 0xFF1E1E2E, 0xFFF5E0DC, 0xFF181825),
    PINK("Pink Sakura", 0xFF1F121F, 0xFFFF85A2, 0xFF2E1A2E),
    DRACULA("Dracula", 0xFF282A36, 0xFFF8F8F2, 0xFF21222C)
}

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val douyinParser = DouyinParser()
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "duck_db")
        .addMigrations(com.duck.app.db.MIGRATION_1_2, com.duck.app.db.MIGRATION_2_3)
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
    
    var terminalTheme = mutableStateOf(
        TerminalTheme.valueOf(prefs.getString("terminal_theme", TerminalTheme.MOCHA.name) ?: TerminalTheme.MOCHA.name)
    )
    
    var isNavBarBlurEnabled = mutableStateOf(prefs.getBoolean("nav_bar_blur", true))
    var isNavBarOpaque = mutableStateOf(prefs.getBoolean("nav_bar_opaque", false))
    var isNavBarTrueGlass = mutableStateOf(prefs.getBoolean("nav_bar_true_glass", false))
    var isMaterialShapesEnabled = mutableStateOf(prefs.getBoolean("material_shapes", false))
    fun toggleNavBarBlur(enabled: Boolean) {
        isNavBarBlurEnabled.value = enabled
        prefs.edit().putBoolean("nav_bar_blur", enabled).apply()
    }
    
    fun toggleNavBarOpaque(enabled: Boolean) {
        isNavBarOpaque.value = enabled
        prefs.edit().putBoolean("nav_bar_opaque", enabled).apply()
    }
    
    fun toggleNavBarTrueGlass(enabled: Boolean) {
        isNavBarTrueGlass.value = enabled
        prefs.edit().putBoolean("nav_bar_true_glass", enabled).apply()
    }
    
    
    fun toggleMaterialShapes(enabled: Boolean) {
        isMaterialShapesEnabled.value = enabled
        prefs.edit().putBoolean("material_shapes", enabled).apply()
    }    
    fun updateTerminalTheme(theme: TerminalTheme) {
        terminalTheme.value = theme
        prefs.edit().putString("terminal_theme", theme.name).apply()
        logToConsole("Terminal theme changed to: ${theme.displayName}")
    }

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
    var updateAvailable = mutableStateOf<Pair<String, String>?>(null)
    var updateDownloadProgress = mutableStateOf(-1f)
    var isCheckingUpdates = mutableStateOf(false)
    var updateCheckMessage = mutableStateOf<String?>(null)

    fun downloadUpdate(context: android.content.Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateDownloadProgress.value = 0f
                val connection = java.net.URL(url).openConnection()
                connection.connect()
                
                val fileLength = connection.contentLength
                val input = connection.getInputStream()
                
                val fileName = "update.apk"
                val apkFile = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
                val output = java.io.FileOutputStream(apkFile)
                
                val data = ByteArray(8192)
                var total: Long = 0
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
                    com.duck.app.util.AppUpdater(context).installApk(fileName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateDownloadProgress.value = -1f
                    android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
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

    }

    private val downloader = FileDownloader(application, client)

    init {
        if (autoCheckUpdates.value) {
            checkForUpdates(manual = false)
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            isCheckingUpdates.value = true
            updateCheckMessage.value = if (manual) "Checking for updates..." else null
            try {
                val response: HttpResponse = client.get("https://api.github.com/repos/maomaoxiaokuoai/duck/releases/latest")
                if (response.status.value in 200..299) {
                    val responseBody = response.bodyAsText()
                    val tagRegex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val urlRegex = "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"".toRegex()
                    val match = tagRegex.find(responseBody)
                    val urlMatch = urlRegex.find(responseBody)
                    if (match != null) {
                        val latestVersion = match.groupValues[1]
                        val apkUrl = urlMatch?.groupValues?.getOrNull(1) ?: "https://github.com/maomaoxiaokuoai/duck/releases/latest"
                        val currentVersion = "v" + com.duck.app.BuildConfig.VERSION_NAME
                        
                        val cleanLatest = latestVersion.substringBefore("-").removePrefix("v").trim()
                        val cleanCurrent = com.duck.app.BuildConfig.VERSION_NAME.substringBefore("-").removePrefix("v").trim()
                        
                        var isNewer = false
                        try {
                            val lParts = cleanLatest.split('.').map { it.toIntOrNull() ?: 0 }
                            val cParts = cleanCurrent.split('.').map { it.toIntOrNull() ?: 0 }
                            val maxLen = maxOf(lParts.size, cParts.size)
                            for (i in 0 until maxLen) {
                                val l = lParts.getOrNull(i) ?: 0
                                val c = cParts.getOrNull(i) ?: 0
                                if (l > c) {
                                    isNewer = true
                                    break
                                } else if (l < c) {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback if parsing fails
                            isNewer = latestVersion != currentVersion && !latestVersion.contains("beta", ignoreCase = true)
                        }

                        if (isNewer && !latestVersion.contains("beta", ignoreCase = true)) {
                            updateAvailable.value = Pair(latestVersion, apkUrl)
                            updateCheckMessage.value = "New version $latestVersion available!"
                        } else {
                            if (manual) {
                                updateCheckMessage.value = "Up to date ($currentVersion)"
                            }
                        }
                    } else if (manual) {
                        updateCheckMessage.value = "No releases found."
                    }
                } else if (manual) {
                    updateCheckMessage.value = "Check failed (HTTP ${response.status.value})"
                }
            } catch (e: Exception) {
                logToConsole("Update check failed: ${e.message}")
                if (manual) {
                    updateCheckMessage.value = "Check failed: ${e.message}"
                }
            } finally {
                isCheckingUpdates.value = false
            }
        }
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

    var downloadPath = mutableStateOf("Download/duck")
    var selectedFolderUri = mutableStateOf<String?>(null)
    var selectedFolderName = mutableStateOf<String?>(null)

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
            logToConsole("解析抖音链接: ${douyinParser.extractUrl(url)}")

            val result = douyinParser.resolve(url, readCookieHeader())
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

    fun fetchPreview(url: String, quality: String, mode: String = "auto") {
        currentPreviewJob?.cancel()
        currentPreviewJob = viewModelScope.launch {
            clearConsole()
            logToConsole("----------------------------------------")
            logToConsole("[SYSTEM] 正在获取抖音作品信息...")
            logToConsole("[INFO] URL: $url")
            logToConsole("[INFO] 模式: $mode")
            logToConsole("----------------------------------------")
            try {
                val result = performExtraction(url)

                if (result.status == "success") {
                    logToConsole("[SUCCESS] 解析成功")
                    logToConsole("[INFO] 标题: '${result.title}'")
                    logToConsole("[INFO] 作者: ${result.author}")
                    logToConsole("[INFO] 直链: ${result.url?.take(80)}...")
                }
                _previewMetadata.value = result
            } catch (e: Exception) {
                logToConsole("预览异常: ${e.message}")
                _previewMetadata.value = ExtractionResult(status = "error", message = e.message ?: "解析失败")
            }
        }
    }

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
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            _uiState.value = DownloadState.Downloading(0f)
            val notificationId = System.currentTimeMillis().toInt()
            lastNotificationId = notificationId
            logToConsole("开始处理: $url")

            try {
                logToConsole("正在解析抖音直链...")
                val result = performExtraction(url)
                if (result.status != "success") {
                    throw Exception(result.message ?: "抖音解析失败")
                }

                val isGallery = result.is_gallery == true
                val urlsToDownload = result.urls?.takeIf { it.isNotEmpty() }
                    ?: result.url?.let { listOf(it) }
                    ?: throw Exception("未能解析出可下载的直链")
                val totalFiles = urlsToDownload.size

                if (isGallery && totalFiles > 1) {
                    downloadGallery(
                        urls = urlsToDownload,
                        result = result,
                        sourceUrl = url,
                        mode = mode,
                        notificationId = notificationId
                    )
                } else {
                    downloadSingle(
                        downloadUrl = urlsToDownload.first(),
                        result = result,
                        sourceUrl = url,
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
        val targetPath = galleryTargetPath(mode)
        val sanitizedTitle = sanitizeFileName(title)
        var succeededCount = 0

        logToConsole("开始下载图集，共 ${urls.size} 个文件...")

        urls.forEachIndexed { index, downloadUrl ->
            val fileName = "${sanitizedTitle}_${index + 1}_${System.currentTimeMillis()}.$extension"

            downloader.downloadFile(
                url = downloadUrl,
                fileName = fileName,
                relativePath = targetPath,
                customFolderUri = selectedFolderUri.value,
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
                author = result.author
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
        notificationId: Int
    ) {
        val isImage = result.ext in IMAGE_EXTENSIONS
        val extension = result.ext ?: if (isImage) "jpg" else "mp4"
        val title = result.title ?: "抖音作品"
        val targetPath = galleryTargetPath(mode, isImage)
        val fileName = "${sanitizeFileName(title)}_${System.currentTimeMillis()}.$extension"

        logToConsole("正在下载: $fileName")
        logToConsole("保存目录: $targetPath")

        downloader.downloadFile(
            url = downloadUrl,
            fileName = fileName,
            relativePath = targetPath,
            customFolderUri = selectedFolderUri.value,
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
                        author = result.author
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

    /** Picks the MediaStore bucket for a download, keeping galleries in Pictures. */
    private fun galleryTargetPath(mode: String, isImage: Boolean = true): String {
        val configured = downloadPath.value
        return when {
            !isImage -> configured
            configured.contains("Picture", ignoreCase = true) -> configured
            mode == "audio" -> "Music/duck"
            else -> "Pictures/duck"
        }
    }

    private fun folderUriFor(targetPath: String): Uri {
        selectedFolderUri.value?.let { return Uri.parse(it) }

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
     * Reads the cookie jar imported from Downloads settings, if the user provided one.
     * Douyin serves some posts only to logged-in visitors; without a jar the request simply
     * goes out anonymous and the parser reports whatever it could resolve.
     */
    private fun readCookieHeader(): String? {
        val path = prefs.getString(COOKIE_SETTING_KEY, null)
        if (path.isNullOrBlank()) return null
        return runCatching {
            java.io.File(path).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun sanitizeFileName(title: String): String =
        title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_").take(60)

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
        /** Must match the key used by the Downloads settings screen. */
        const val COOKIE_SETTING_KEY = "douyin_cookies_path"

        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
