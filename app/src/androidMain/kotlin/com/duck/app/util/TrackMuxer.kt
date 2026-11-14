package com.duck.app.util

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins a video track and an audio track into one file without re-encoding.
 *
 * Bilibili serves anything above 720P as two separate files — a silent video and an
 * audio track. Downloading only the video gives a clip with no sound, so the two are
 * pulled apart by the platform muxer and written into a single MP4 here.
 *
 * Both tracks are copied sample by sample; nothing is decoded, so quality is untouched
 * and a long video takes seconds rather than minutes.
 */
object TrackMuxer {

    private const val TAG = "TrackMuxer"
    private const val BUFFER_SIZE = 1024 * 1024
    private const val MAX_SAMPLE_SIZE = 512 * 1024

    /**
     * Writes [videoFile] and [audioFile] into [outputFile].
     *
     * Returns true when the output was produced. A false result leaves the caller free to
     * keep the silent video, which is still usable.
     */
    fun mux(videoFile: File, audioFile: File, outputFile: File): Boolean {
        var muxer: MediaMuxer? = null
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val videoTrack = videoExtractor.firstTrackOf("video/")
                ?: run {
                    Log.d(TAG, "no video track in " + videoFile.name)
                    return false
                }
            val audioTrack = audioExtractor.firstTrackOf("audio/")
                ?: run {
                    Log.d(TAG, "no audio track in " + audioFile.name)
                    return false
                }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoOut = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val audioOut = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
            val info = MediaExtractor()

            // The video half first, keeping its timestamps.
            var videoSamples = 0
            while (true) {
                val size = videoExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (size > MAX_SAMPLE_SIZE) {
                    Log.d(TAG, "video sample too large: " + size)
                    return false
                }
                val flags = if (videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodecBufferFlags.SYNC
                } else {
                    0
                }
                muxer.writeSampleData(
                    videoOut,
                    buffer,
                    android.media.MediaCodec.BufferInfo().apply {
                        set(0, size, videoExtractor.sampleTime, flags)
                    },
                )
                videoExtractor.advance()
                videoSamples++
            }

            // Then the audio, same way.
            var audioSamples = 0
            while (true) {
                val size = audioExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (size > MAX_SAMPLE_SIZE) {
                    Log.d(TAG, "audio sample too large: " + size)
                    return false
                }
                val flags = if (audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodecBufferFlags.SYNC
                } else {
                    0
                }
                muxer.writeSampleData(
                    audioOut,
                    buffer,
                    android.media.MediaCodec.BufferInfo().apply {
                        set(0, size, audioExtractor.sampleTime, flags)
                    },
                )
                audioExtractor.advance()
                audioSamples++
            }

            Log.d(TAG, "muxed " + videoSamples + " video / " + audioSamples + " audio samples")
            return videoSamples > 0 && audioSamples > 0
        } catch (e: Exception) {
            Log.d(TAG, "mux failed: " + e.message)
            return false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
        }
    }

    /** Index of the first track whose mime type starts with [prefix], or null. */
    private fun MediaExtractor.firstTrackOf(prefix: String): Int? {
        for (i in 0 until trackCount) {
            val mime = getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return null
    }

    /** The sync flag value the muxer expects, kept out of the loop for readability. */
    private object MediaCodecBufferFlags {
        const val SYNC = android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
}
