package com.duck.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.duck.app.App
import com.duck.app.R
import com.duck.app.util.PreferenceUtil.getLong
import com.duck.app.util.PreferenceUtil.updateLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Silent self-update.
 *
 * Runs on every app start, in the background:
 *  1. fetch the newest release (GitHub API, with mirror fallbacks)
 *  2. compare with the installed version
 *  3. download the matching ABI APK quietly
 *  4. hand off to the system installer
 *
 * The system installer still needs the "install unknown apps" permission; we surface that
 * once and remember the answer so later updates go through without user interaction.
 */
object SilentUpdater {

    private const val TAG = "SilentUpdater"

    /** Delay so the first launch animation is not competing with network work. */
    private const val START_DELAY_MS = 3_000L

    /** Don't hammer the API if the app is opened repeatedly. */
    private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L

    @Volatile private var running = false

    fun scheduleOnStart(context: Context = App.context) {
        if (!PreferenceUtil.isAutoUpdateEnabled()) {
            Log.d(TAG, "auto update disabled")
            return
        }
        App.applicationScope.launchUpdate(context)
    }

    private fun CoroutineScope.launchUpdate(context: Context) {
        if (running) return
        running = true
        launch(Dispatchers.IO) {
            try {
                delay(START_DELAY_MS)
                if (!PreferenceUtil.isNetworkAvailableForDownload()) {
                    Log.d(TAG, "offline, skip")
                    return@launch
                }
                val now = System.currentTimeMillis()
                if (now - LAST_CHECK_TIME.getLong() < CHECK_INTERVAL_MS) {
                    Log.d(TAG, "checked recently, skip")
                    return@launch
                }
                LAST_CHECK_TIME.updateLong(now)

                val release =
                    UpdateUtil.checkForUpdate(context)
                        ?: run {
                            Log.d(TAG, "already up to date")
                            return@launch
                        }
                Log.i(TAG, "new version available: " + release.name)

                // Download quietly; progress is reported through the normal notification.
                UpdateUtil.downloadApk(context = context, latestRelease = release).collect { status ->
                    if (status is UpdateUtil.DownloadStatus.Finished) {
                        Log.i(TAG, "apk downloaded: " + status.file.absolutePath)
                        withContext(Dispatchers.Main) { install(status.file, context) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "silent update failed", e)
            } finally {
                running = false
            }
        }
    }

    /** Launch the system installer, requesting the permission first if needed. */
    fun install(apk: File, context: Context = App.context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Log.i(TAG, "install permission missing, opening settings")
            openInstallPermissionSettings(context)
            return
        }
        UpdateUtil.installLatestApk(context = context, apk = apk)
    }

    private fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            it.printStackTrace()
            ToastUtil.makeToast(R.string.app_update_failed)
        }
    }
}
