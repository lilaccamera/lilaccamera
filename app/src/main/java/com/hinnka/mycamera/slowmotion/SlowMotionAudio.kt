package com.hinnka.mycamera.slowmotion

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.SystemClock
import com.hinnka.mycamera.utils.PLog

/**
 * Records the microphone as AAC into memory (a recording is seconds long), so the sound can be laid onto the
 * video-only file afterwards. Sample times are microseconds since [startElapsedNs] (SystemClock.elapsedRealtimeNanos).
 */
class SlowMotionAudio(private val channels: Int = 1) {
    class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)

    var startElapsedNs = 0L
        private set
    val samples = mutableListOf<Sample>()
    var format: MediaFormat? = null
        private set

    private var record: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /** False when the microphone or the encoder is not available; the recording then goes on without sound. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(RATE, mask, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.CAMCORDER, RATE, mask, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 4)
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000 * channels)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            record = rec; encoder = enc
            running = true
            rec.startRecording()
            thread = Thread { pump(rec, enc) }.also { it.start() }
            true
        } catch (e: Exception) {
            PLog.w(TAG, "No sound for this recording: ${e.message}")
            release()
            false
        }
    }

    private fun pump(rec: AudioRecord, enc: MediaCodec) {
        val pcm = ByteArray(RATE / 50 * 2 * channels) // 20 ms
        val info = MediaCodec.BufferInfo()
        var frames = 0L
        var ended = false
        var clocked = false
        while (!ended) {
            if (running) {
                val n = rec.read(pcm, 0, pcm.size)
                if (n > 0) {
                    // the clock of the first sample: now, minus what this first read holds
                    if (!clocked) startElapsedNs = SystemClock.elapsedRealtimeNanos() - n / (2 * channels) * 1_000_000_000L / RATE
                    clocked = true
                    val i = enc.dequeueInputBuffer(20_000)
                    if (i >= 0) {
                        enc.getInputBuffer(i)!!.apply { clear(); put(pcm, 0, n) }
                        enc.queueInputBuffer(i, 0, n, frames * 1_000_000L / RATE, 0)
                        frames += n / (2 * channels)
                    }
                }
            } else {
                val i = enc.dequeueInputBuffer(100_000)
                if (i >= 0) { enc.queueInputBuffer(i, 0, 0, frames * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM); ended = true }
            }
            while (true) {
                val o = enc.dequeueOutputBuffer(info, 0)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { format = enc.outputFormat; continue }
                if (o < 0) break
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val d = ByteArray(info.size)
                    enc.getOutputBuffer(o)!!.apply { position(info.offset); get(d) }
                    samples += Sample(d, info.presentationTimeUs, info.flags)
                }
                enc.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ended = true
            }
        }
    }

    /** Stops the microphone and waits for the encoder to flush; [samples] and [format] are complete afterwards. */
    fun stop() {
        running = false
        runCatching { thread?.join(3_000) }
        release()
    }

    private fun release() {
        running = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        record = null; encoder = null; thread = null
    }

    private companion object {
        const val TAG = "SlowMotionAudio"
        const val RATE = 48_000
    }
}
