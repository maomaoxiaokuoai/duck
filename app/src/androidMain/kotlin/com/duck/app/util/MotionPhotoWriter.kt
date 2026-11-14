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
     */
    fun build(jpeg: ByteArray, video: ByteArray): ByteArray? {
        if (!isJpeg(jpeg) || !isMp4(video)) {
            Log.d(TAG, "unexpected input, jpeg=" + isJpeg(jpeg) + " mp4=" + isMp4(video))
            return null
        }

        val xmp = buildXmp(video.size)
        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
        val payload = namespace + xmp.toByteArray(Charsets.UTF_8)

        // The APP1 length field counts itself, so it is payload + 2.
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
     * The XMP packet Android, Google Photos and Samsung Gallery look for.
     *
     * Two items are declared: the primary image and the appended video. The viewer reads
     * the video's Length to know how many trailing bytes belong to it.
     */
    private fun buildXmp(videoLength: Int): String = buildString {
        append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>")
        append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">")
        append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">")
        append("<rdf:Description rdf:about=\"\"")
        append(" xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"")
        append(" xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"")
        append(" xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"")
        append(" GCamera:MotionPhoto=\"1\"")
        append(" GCamera:MotionPhotoVersion=\"1\"")
        append(" GCamera:MotionPhotoPresentationTimestampUs=\"0\">")
        append("<Container:Directory><rdf:Seq>")
        append("<rdf:li rdf:parseType=\"Resource\">")
        append("<Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\"/>")
        append("</rdf:li>")
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
