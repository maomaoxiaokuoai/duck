package com.duck.app.api

import kotlinx.serialization.Serializable

/**
 * The resolved media behind a user-supplied link.
 *
 * The downloader UI and the preview sheet read this directly, and it describes exactly what
 * a resolution result needs to carry: direct URL(s), title/author/thumbnail and the
 * container extension.
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
    val http_headers: Map<String, String>? = null
)
