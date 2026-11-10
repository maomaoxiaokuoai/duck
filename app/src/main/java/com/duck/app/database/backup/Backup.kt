package com.duck.app.database.backup

import com.duck.app.database.objects.CommandTemplate
import com.duck.app.database.objects.DownloadedVideoInfo
import com.duck.app.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null
)