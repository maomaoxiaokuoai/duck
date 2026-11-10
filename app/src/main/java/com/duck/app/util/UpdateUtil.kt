package com.duck.app.util

import android.util.Log
import com.duck.app.App.Companion.context
import com.duck.app.util.PreferenceUtil.getInt
import com.duck.app.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateUtil {

    private const val TAG = "UpdateUtil"

    private const val ytdlpNightlyBuildRelease =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            val channel =
                when (YT_DLP_UPDATE_CHANNEL.getInt()) {
                    YT_DLP_NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                    else -> YoutubeDL.UpdateChannel.STABLE
                }

            YoutubeDL.getInstance()
                .updateYoutubeDL(appContext = context, updateChannel = channel)
                .also {
                    if (it == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(context)?.let {
                            PreferenceUtil.encodeString(YT_DLP_VERSION, it)
                        }
                    }
                    val now = System.currentTimeMillis()
                    YT_DLP_UPDATE_TIME.updateLong(now)
                }
        }
}
