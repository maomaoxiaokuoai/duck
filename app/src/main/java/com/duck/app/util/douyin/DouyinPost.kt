package com.duck.app.util.douyin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parsed Douyin post, normalized so the download pipeline does not need to know
 * Douyin's raw shape.
 */
@Serializable
data class DouyinPost(
    val id: String = "",
    val desc: String = "",
    @SerialName("create_time") val createTime: Long = 0,
    /** "video" or "note" (image post). */
    val type: String = "video",
    val author: DouyinAuthor? = null,
    val cover: String? = null,
    /** Playable video variants, best first. */
    val videos: List<DouyinVideo> = emptyList(),
    /** Original image URLs for note posts. */
    val images: List<String> = emptyList(),
    val music: DouyinMusic? = null,
    @SerialName("like_count") val likeCount: Long = 0,
    @SerialName("comment_count") val commentCount: Long = 0,
    @SerialName("share_count") val shareCount: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
) {
    val isNote: Boolean get() = type == "note" || images.isNotEmpty()
}

@Serializable
data class DouyinAuthor(
    val uid: String = "",
    @SerialName("sec_uid") val secUid: String = "",
    val nickname: String = "",
    val signature: String = "",
    @SerialName("follower_count") val followerCount: Long = 0,
    val avatar: String = "",
)

@Serializable
data class DouyinVideo(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** Bytes, when the API reports it. */
    val size: Long = 0,
    /** kbps. */
    val bitrate: Int = 0,
    @SerialName("is_h265") val isH265: Boolean = false,
)

@Serializable
data class DouyinMusic(
    val title: String = "",
    val author: String = "",
    val url: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0,
)
