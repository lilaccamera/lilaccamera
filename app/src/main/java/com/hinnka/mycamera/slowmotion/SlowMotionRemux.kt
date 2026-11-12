package com.hinnka.mycamera.slowmotion

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.nio.ByteBuffer

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
     * for every frame; [triggerFrames] holds, per burst, how many frames had come before it, and [burstFrames] is
     * the length of a burst. [audio] is null or has no samples when there is no sound.
     * Returns the finished file, or null on failure.
     */
    fun finish(
        video: File, frameTimes: LongArray, triggerFrames: List<Int>, burstFrames: Int,
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
            val frameUs = 1_000_000L / 30

            // Ordinary stretches as frame ranges [from, to): before the first burst, between bursts, after the last.
            // A burst is followed by the read-out jump: the largest step in the stamps after its frames.
            class Stretch(val from: Int, val to: Int, val shiftUs: Long)
            val stretches = ArrayList<Stretch>()
            var from = 0
            var shift = 0L
            val triggers = triggerFrames.filter { it in 1 until n }.sorted()
            for ((k, trigger) in triggers.withIndex()) {
                val limit = if (k + 1 < triggers.size) triggers[k + 1] else n
                val search = (trigger + burstFrames).coerceAtMost(limit - 1).coerceAtLeast(trigger + 1)
                var jump = -1
                var best = JUMP_MIN_US
                for (i in search until limit) if (p[i] - p[i - 1] > best) { best = p[i] - p[i - 1]; jump = i }
                if (jump < 0) break
                stretches += Stretch(from, trigger, shift)
                if (CLOSE_JUMP) shift += p[jump] - p[jump - 1] - frameUs
                from = jump
            }
            stretches += Stretch(from, n, shift)
            // new file time of frame i
            fun newTime(i: Int): Long = p[i] - stretches.last { i >= it.from }.shiftUs

            // file time = arrival - offset, per ordinary stretch; the sound outside the stretches is dropped
            val sound = audio?.takeIf { it.samples.isNotEmpty() && it.format != null && n > 0 }
            val audioFile = ArrayList<Pair<Long, SlowMotionAudio.Sample>>() // new file time, sample
            if (sound != null) {
                val ranges = stretches.filter { it.to > it.from }.map { st ->
                    val offset = (st.from until st.to).map { arrival[it] - p[it] * 1000 }.sorted().let { it[it.size / 2] }
                    Triple(st, offset, p[st.from] to p[st.to - 1] + frameUs)
                }
                for (s in sound.samples) {
                    val real = sound.startElapsedNs + s.timeUs * 1000
                    for ((st, offset, span) in ranges) {
                        val t = (real - offset) / 1000
                        if (t >= span.first && t < span.second) { audioFile += (t - st.shiftUs) to s; break }
                    }
                }
                PLog.i(TAG, "Sound: ${audioFile.size} of ${sound.samples.size} samples kept, ${stretches.size} ordinary stretches")
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
                val t = if (k < n) newTime(k) else extractor.sampleTime
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
