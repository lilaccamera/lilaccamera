package com.hinnka.mycamera.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.nio.ByteBuffer

/**
 * High-frame-rate clips (120 fps and up) are ordinary real-time MP4s marked with `com.android.capture.fps`; other apps
 * play them at full speed. A slowed copy keeps the video samples as they are and stretches their time stamps.
 */
object SlowedCopy {
    private const val TAG = "SlowedCopy"

    /** Playback runs this many times slower; the clip is then shown at 30 fps. */
    const val FACTOR = 4

    /** Capture rate from which a clip opens slowed. */
    const val HIGH_FRAME_RATE_FPS = 119f

    /** The capture rate the MP4 is marked with (0 when unmarked) and the clip's length in milliseconds. */
    data class Rate(val captureFps: Float, val durationMs: Long) {
        val isHighFrameRate get() = captureFps >= HIGH_FRAME_RATE_FPS
    }

    fun readRate(context: Context, uri: Uri): Rate {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Rate(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            PLog.w(TAG, "No capture rate for $uri: ${e.message}")
            Rate(0f, 0L)
        } finally {
            retriever.release()
        }
    }

    /** Writes [source]'s video track to [out] with every time stamp times [FACTOR], no sound, no re-encoding. */
    fun write(context: Context, source: Uri, out: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            if (format.containsKey(MediaFormat.KEY_ROTATION)) m.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
            val videoTrack = m.addTrack(format)
            m.start()
            val buffer = ByteBuffer.allocate(
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 4 shl 20
            )
            val info = MediaCodec.BufferInfo()
            var frames = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime * FACTOR, extractor.sampleFlags)
                m.writeSampleData(videoTrack, buffer, info)
                extractor.advance()
                frames++
            }
            m.stop()
            PLog.i(TAG, "Slowed copy written: $frames frames, ${out.length()} bytes")
            return frames > 0
        } catch (e: Exception) {
            PLog.e(TAG, "Could not write the slowed copy", e)
            out.delete()
            return false
        } finally {
            extractor.release()
            runCatching { muxer?.release() }
        }
    }
}
