package com.duck.app.util

import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Wraps a still and a short clip into an Android motion photo.
 *
 * A motion photo is an ordinary JPEG with the video appended after the image data and an
 * XMP block describing the split, so anything that only understands JPEG still sees a
 * normal photo. Google Photos, the system gallery and Samsung Gallery all read that XMP
 * and animate the still on their own — no custom player required, no extra file beside it.
 *
 * The layout is:
 *
 *     FFD8                     SOI
 *     FFE1 <len> <xmp>         APP1 holding the Motion Photo XMP packet
 *     ...original JPEG...      the rest of the still, unchanged
 *     <mp4>                    appended verbatim
 *
 * The XMP declares the video as the last item and its length, which is what the viewer
 * uses to find the boundary.
 */
object MotionPhotoWriter {

    private const val TAG = "MotionPhotoWriter"

    /** How far back from the end an EOI marker may sit: JPEG allows a little trailing pad. */
    private const val EOI_TAIL_BYTES = 64

    /**
     * Returns [jpeg] with [video] appended and the XMP block inserted, or null when the
     * inputs do not look like the expected formats.
     *
     * The packet states how long the primary image is, and that figure includes the APP1
     * segment holding the packet itself - a value that depends on how many digits the
     * figure has. Two passes narrow it: the first measures a placeholder packet, the
     * second writes the real figure, and any growth the digits caused is corrected by
     * rebuilding until the declared length matches the bytes actually written.
     */
    fun build(jpeg: ByteArray, video: ByteArray): ByteArray? {
        if (!isJpeg(jpeg) || !isMp4(video)) {
            Log.d(TAG, "unexpected input, jpeg=" + isJpeg(jpeg) + " mp4=" + isMp4(video))
            return null
        }

        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)

        // A still is everything before the clip: the JPEG plus the segment inserted.
        // The segment is 2 marker bytes + the length field, which counts itself.
        var stillLength = jpeg.size
        var payload = ByteArray(0)
        // The figure feeds its own size, so settle it; two rounds are enough in
        // practice because a length only shifts by a digit at a boundary.
        repeat(4) {
            val candidate = namespace +
                buildXmp(stillLength, video.size).toByteArray(Charsets.UTF_8)
            // 2 marker bytes + 2 length bytes + the payload itself.
            val total = jpeg.size + 4 + candidate.size
            if (total == stillLength && payload.isNotEmpty()) return@repeat
            stillLength = total
            payload = candidate
        }

        if (payload.isEmpty()) return null
        if (payload.size + 2 > 0xFFFF) {
            Log.d(TAG, "xmp too large: " + payload.size)
            return null
        }

        val out = ByteArrayOutputStream(jpeg.size + video.size + payload.size + 4)
        out.write(jpeg, 0, 2) // SOI
        out.write(0xFF)
        out.write(0xE1)
        out.write((payload.size + 2) shr 8)
        out.write((payload.size + 2) and 0xFF)
        out.write(payload)
        out.write(jpeg, 2, jpeg.size - 2) // the rest of the still
        out.write(video)
        return out.toByteArray()
    }

    /**
     * The XMP packet the motion photo spec defines.
     *
     * Two namespaces matter and both come straight from the format document: the camera
     * one (prefix `Camera`) carries the flag that says this file is a motion photo, and
     * the container one (prefix `Container`) is the directory that says where the video
     * starts. A reader finds the clip by walking back from the end of the file by the
     * MotionPhoto item's Length, so that number has to be exact.
     *
     * The old `GCamera` prefix was the micro-video v1 spelling; the spec says readers
     * must ignore those attributes, so a viewer that follows the spec would not have
     * treated the file as a motion photo at all.
     */
    private fun buildXmp(stillLength: Int, videoLength: Int): String = buildString {
        append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>")
        append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">")
        append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">")
        append("<rdf:Description rdf:about=\"\"")
        append(" xmlns:Camera=\"http://ns.google.com/photos/1.0/camera/\"")
        append(" xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"")
        append(" xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"")
        append(" Camera:MotionPhoto=\"1\"")
        append(" Camera:MotionPhotoVersion=\"1\"")
        append(" Camera:MotionPhotoPresentationTimestampUs=\"0\">")
        append("<Container:Directory><rdf:Seq>")
        // The primary image must be the first entry, and its length is what precedes
        // the video in this file.
        append("<rdf:li rdf:parseType=\"Resource\">")
        append("<Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"")
        append(stillLength)
        append("\"/>")
        append("</rdf:li>")
        // The video must be last, and nothing may follow it.
        append("<rdf:li rdf:parseType=\"Resource\">")
        append("<Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"")
        append(videoLength)
        append("\"/>")
        append("</rdf:li>")
        append("</rdf:Seq></Container:Directory>")
        append("</rdf:Description></rdf:RDF></x:xmpmeta>")
        append("<?xpacket end=\"w\"?>")
    }

    /**
     * True for a JPEG that is both SOI-led and EOI-terminated.
     *
     * The SOI check alone accepted a download the connection cut short, and the result
     * was a motion photo whose still no viewer could decode. A short pad after EOI is
     * normal, so the marker is searched for in the tail instead of being required in
     * the last two bytes.
     */
    private fun isJpeg(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return false
        val from = (bytes.size - EOI_TAIL_BYTES).coerceAtLeast(2)
        for (i in bytes.size - 2 downTo from) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) return true
        }
        return false
    }

    private fun isMp4(bytes: ByteArray): Boolean =
        bytes.size > 12 && String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp"
}
