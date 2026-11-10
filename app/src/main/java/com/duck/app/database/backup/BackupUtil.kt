package com.duck.app.database.backup

import android.content.Context
import android.text.format.DateFormat
import com.duck.app.App
import com.duck.app.R
import com.duck.app.database.objects.CommandTemplate
import com.duck.app.database.objects.DownloadedVideoInfo
import com.duck.app.database.objects.OptionShortcut
import com.duck.app.util.DatabaseUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

object BackupUtil {
    private val format = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportTemplatesToJson() =
        exportTemplatesToJson(
            templates = DatabaseUtil.getTemplateList(),
            shortcuts = DatabaseUtil.getShortcutList()
        )

    fun exportTemplatesToJson(
        templates: List<CommandTemplate>,
        shortcuts: List<OptionShortcut>
    ): String {
        return format.encodeToString(
            Backup(
                templates = templates, shortcuts = shortcuts
            )
        )
    }

    fun List<DownloadedVideoInfo>.toJsonString(): String {
        return format.encodeToString(Backup(downloadHistory = this))
    }

    fun List<DownloadedVideoInfo>.toURLListString(): String {
        return this.map { it.videoUrl }.joinToString(separator = "\n") { it }
    }

    fun String.decodeToBackup(): Result<Backup> {
        return format.runCatching {
            decodeFromString<Backup>(this@decodeToBackup)
        }
    }

    fun getDownloadHistoryExportFilename(context: Context): String {
        return listOf(
            context.getString(R.string.app_name),
            App.packageInfo.versionName,
            Date().toString()
        ).joinToString(separator = "-") { it }
    }

    enum class BackupDestination {
        File, Clipboard
    }

    enum class BackupType {
        DownloadHistory, URLList, CommandTemplate, CommandShortcut
    }
}