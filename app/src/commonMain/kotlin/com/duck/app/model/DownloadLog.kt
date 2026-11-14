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

    /** Views; null when the platform does not publish one. */
    val playCount: Long? = null,
    /** Clip length in milliseconds. */
    val duration: Long? = null,
    /** video, gallery or audio, so the list can say what was saved. */
    val mediaKind: String? = null,
    /** Bytes on disk, as reported by MediaStore after the write. */
    val fileSize: Long? = null,

    /** How many files this entry saved; a gallery is more than one. */
    val imageCount: Int? = null,

    /**
     * Content URIs of every file saved by a gallery download, comma separated.
     *
     * [path] holds only the first, which is what the list thumbnail shows. The
     * sheet needs the whole set to page through what was actually saved.
     */
    val imagePaths: String? = null,
)
