package com.duck.app.util.douyin

import com.duck.app.util.Format
import com.duck.app.util.VideoInfo

/**
 * Converts a parsed [DouyinPost] into the app's generic [VideoInfo] so the existing
 * format selection and download pipeline can handle Douyin without special cases.
 */
object DouyinAdapter {

    /** Douyin ids are numeric; the generic pipeline expects a stable string key. */
    private const val EXTRACTOR = "douyin"

    fun toVideoInfo(post: DouyinPost): VideoInfo {
        val formats =
            if (post.isNote) {
                // Image posts: expose every image as its own downloadable "format".
                post.images.mapIndexed { index, url ->
                    Format(
                        formatId = "image-" + (index + 1),
                        url = url,
                        ext = extOf(url),
                        formatNote = "图片 " + (index + 1),
                    )
                }
            } else {
                post.videos.take(1).mapIndexed { index, video ->
                    Format(
                        formatId = "video-" + (index + 1),
                        url = video.url,
                        ext = "mp4",
                        width = video.width.toDouble(),
                        height = video.height.toDouble(),
                        fileSize = video.size.toDouble(),
                        formatNote =
                            if (video.width > 0 && video.height > 0) {
                                video.height.toString() + "p"
                            } else {
                                "视频 " + (index + 1)
                            },
                    )
                }
            }

        return VideoInfo(
            id = post.id,
            title = shortTitle(post),
            formats = formats,
            thumbnail = post.cover,
            description = post.desc,
            uploader = post.author?.nickname,
            uploaderId = post.author?.uid,
            duration = post.durationMs / 1000.0,
            webpageUrl = "https://www.douyin.com/video/" + post.id,
            likeCount = post.likeCount.toInt(),
            commentCount = post.commentCount.toInt(),
            channel = post.author?.nickname,
            extractor = EXTRACTOR,
            extractorKey = EXTRACTOR,
            displayId = post.id,
            fulltitle = post.desc,
        )
    }

    /** Direct URL for the highest-quality asset, used by the quick-download path. */
    fun bestUrl(post: DouyinPost): String? =
        if (post.isNote) post.images.firstOrNull()
        else post.videos.maxByOrNull { it.size }?.url
    /** First meaningful line of the caption, trimmed to a sane filename length. */
    private val TRAILING_PUNCT = charArrayOf(0x3002.toChar(), '.', 0xFF0C.toChar(), ',', ' ', 0x0A.toChar())

    private fun shortTitle(post: DouyinPost): String {
        val first = post.desc.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return post.id
        val cleaned = first.trimEnd(*TRAILING_PUNCT)
        return if (cleaned.length > 50) cleaned.take(50).trim() else cleaned
    }
    /** Douyin serves jpeg and webp; keep the real extension so files open correctly. */
    private fun extOf(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".gif") -> "gif"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".jpeg"), path.endsWith(".jpg") -> "jpg"
            else -> "jpg"
        }
    }
}
