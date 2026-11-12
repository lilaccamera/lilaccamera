package com.hinnka.mycamera.slowmotion

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Writes the final file from the companion's video-only MP4: sets the rotation, closes the hole the camera
 * leaves in the time stamps while it reads the burst out, and lays the sound on.
 *
 * The video time stamps follow the clock: ordinary frames are stamped in real time, the burst frames (0.1 s of
 * real time) stretch over 3 or 6 s, and the service delivers nothing while it reads the burst out, which shows as a
 * jump of 0.8 to 1.5 s in the stamps. The sound is matched to the ordinary parts through the arrival time of their
 * frames and left silent from the start of the burst until the video continues.
 */
object SlowMotionRemux {
    private const val TAG = "SlowMotionRemux"

    /** Smallest time stamp step after the burst that counts as the read-out jump (ordinary frames are 33 ms apart). */
    private const val JUMP_MIN_US = 200_000L

    /** Close the jump so the picture does not freeze for a second while the sound is silent anyway. */
    private const val CLOSE_JUMP = true

    /**
     * [frameTimes]: the companion's pairs (file time stamp in microseconds, arrival in elapsed-realtime nanoseconds)
     * for every frame; [framesBeforeTrigger] frames came before the burst, [burstFrames] frames are the burst.
     * [audio] is null or has no samples when there is no sound. Returns the finished file, or null on failure.
     */
    fun finish(
        video: File, frameTimes: LongArray, framesBeforeTrigger: Int, burstFrames: Int,
        audio: SlowMotionAudio?, orientationDegrees: Int, out: File,
    ): File? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(video.path)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")
            }
            extractor.selectTrack(track)
            val videoFormat = extractor.getTrackFormat(track)

            val n = frameTimes.size / 2
            val p = LongArray(n) { frameTimes[2 * it] - frameTimes[0] } // file time of frame i, from 0
            val arrival = LongArray(n) { frameTimes[2 * it + 1] }
            val frameUs = if (n > 1) 1_000_000L / 30 else 0L
            val triggered = framesBeforeTrigger in 1 until n
            // the first frame after the largest step following the burst
            var jump = -1
            if (triggered) {
                val from = (framesBeforeTrigger + burstFrames).coerceAtMost(n - 1).coerceAtLeast(1)
                var best = JUMP_MIN_US
                for (i in from until n) if (p[i] - p[i - 1] > best) { best = p[i] - p[i - 1]; jump = i }
            }
            val shiftUs = if (CLOSE_JUMP && jump > 0) p[jump] - p[jump - 1] - frameUs else 0L

            // file time = arrival - offset, per ordinary stretch
            fun offset(range: IntRange): Long = range.map { arrival[it] - p[it] * 1000 }.sorted().let { it[it.size / 2] }
            val sound = audio?.takeIf { it.samples.isNotEmpty() && it.format != null && n > 0 }
            val audioFile = ArrayList<Pair<Long, SlowMotionAudio.Sample>>() // new file time, sample
            if (sound != null) {
                val offA = offset(0 until (if (jump > 0) framesBeforeTrigger else n))
                val offC = if (jump > 0) offset(jump until n) else 0L
                val endA = if (jump > 0) p[framesBeforeTrigger - 1] + frameUs else Long.MAX_VALUE
                for (s in sound.samples) {
                    val real = sound.startElapsedNs + s.timeUs * 1000
                    val a = (real - offA) / 1000
                    if (a in 0 until endA) { audioFile += a to s; continue }
                    if (jump > 0) {
                        val c = (real - offC) / 1000
                        if (c >= p[jump]) audioFile += (c - shiftUs) to s
                    }
                }
                PLog.i(TAG, "Sound: ${audioFile.size} of ${sound.samples.size} samples kept, jump at frame $jump, shift $shiftUs us")
            }

            val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(orientationDegrees)
            val videoTrack = muxer.addTrack(videoFormat)
            val audioTrack = if (sound != null) muxer.addTrack(sound.format!!) else -1
            muxer.start()
            val buffer = ByteBuffer.allocate(
                if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 4 shl 20
            )
            val info = MediaCodec.BufferInfo()
            var audioIndex = 0
            fun writeAudioUpTo(timeUs: Long) {
                while (audioIndex < audioFile.size && audioFile[audioIndex].first <= timeUs) {
                    val (t, s) = audioFile[audioIndex++]
                    info.set(0, s.data.size, t, s.flags)
                    muxer.writeSampleData(audioTrack, ByteBuffer.wrap(s.data), info)
                }
            }
            var k = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val t = extractor.sampleTime - (if (jump in 1..k) shiftUs else 0L)
                if (audioTrack >= 0) writeAudioUpTo(t)
                info.set(0, size, t, extractor.sampleFlags)
                muxer.writeSampleData(videoTrack, buffer, info)
                extractor.advance()
                k++
            }
            if (audioTrack >= 0) writeAudioUpTo(Long.MAX_VALUE)
            muxer.stop(); muxer.release()
            if (n > 0 && k != n) PLog.w(TAG, "Frame count differs: file has $k, companion saw $n; sound may be off")
            return out
        } catch (e: Exception) {
            PLog.e(TAG, "Could not finish the file", e)
            return null
        } finally {
            extractor.release()
        }
    }
}
