package com.duck.app.api

import kotlinx.serialization.Serializable

/**
 * The resolved media behind a user-supplied link.
 *
 * Alongside the direct URLs it carries what the post says about itself: who published it,
 * when, and how it was received. The history screen and its detail sheet render those, so
 * they travel with the resolution rather than being looked up again.
 */
@Serializable
data class ExtractionResult(
    val status: String,
    val url: String? = null,
    val urls: List<String>? = null,
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val size: Long? = null,
    val ext: String? = null,
    val is_gallery: Boolean? = null,
    val message: String? = null,
    val audio_url: String? = null,
    val max_resolution: String? = null,
    val available_qualities: List<String>? = null,
    val http_headers: Map<String, String>? = null,
    /** Unix seconds as reported by the platform. */
    val create_time: Long? = null,
    /** Human label of the source, e.g. 抖音. */
    val platform: String? = "抖音",
    val like_count: Long? = null,
    val comment_count: Long? = null,
    val share_count: Long? = null,
    val collect_count: Long? = null,
    /** Views; only some platforms publish this. */
    val play_count: Long? = null,
    /** Clip length in milliseconds, null when the source did not report it. */
    val duration: Long? = null,
    /** Byte size the platform reports for the primary media. */
    val media_size: Long? = null,
    /** video, gallery or audio. */
    val media_kind: String? = null,
    /** Unix millis when this resolution completed, for the preview card. */
    val resolved_at: Long? = null,
    /**
     * For each entry in [urls], the short clip that turns it into a live photo.
     *
     * Xiaohongshu's moving images are a still plus a two-second MP4; when this entry
     * is present the pair can be written out as an Android motion photo, which the
     * system gallery then plays. Null entries are ordinary static images.
     */
    val live_video_urls: List<String?>? = null,
)