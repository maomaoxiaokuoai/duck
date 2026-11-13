package com.duck.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One finished download, as shown in the history list.
 *
 * Keeps the post metadata the list and its detail sheet display, so opening an entry does
 * not require re-resolving the link.
 */
@Entity(tableName = "download_logs")
data class DownloadLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String,
    val path: String? = null,
    val thumbnailPath: String? = null,
    val author: String? = null,
    /** Unix seconds the post was published, null when the source did not report it. */
    val createTime: Long? = null,
    val platform: String? = "抖音",
    val likeCount: Long? = null,
    val commentCount: Long? = null,
    val shareCount: Long? = null,
    val collectCount: Long? = null,
    /** Clip length in milliseconds. */
    val duration: Long? = null,
    /** video, gallery or audio, so the list can say what was saved. */
    val mediaKind: String? = null,
)
