package com.hinnka.mycamera.lut

import android.graphics.Bitmap
import android.opengl.GLES30
import com.hinnka.mycamera.BuildConfig
import com.hinnka.mycamera.processor.ShadowCastFix
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.SystemPropertiesUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/**
 * The XZ1 Compact front camera's viewfinder for "Accurate" colour: its preview frames keep the violet-magenta
 * black floor and the darker exposure that the photos lose (every still request also targets a JPEG, which gets
 * Sony's still processing; preview requests cannot, it would cap them near 4 frames per second).
 *
 * About twice a second a small copy of the plain viewfinder frame is measured with the photos' own [ShadowCastFix],
 * and the fix, plus a brightness boost for the longer still exposure, is baked into a 33-cube colour table that
 * the colour pass samples once per pixel. Plain ("Original") preview captures skip it, so it never measures itself.
 */
internal class PreviewCastFixGl {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var pending: ByteBuffer? = null
    private var texture = 0
    private var lastSampleMs = 0L
    private var estimates = 0

    /** True once a table is on the GPU; the colour pass samples it only then. */
    val ready: Boolean get() = texture != 0

    /** GL thread, after a new camera frame: whether to read a viewfinder frame for the next estimate now. */
    fun wantsSample(nowMs: Long): Boolean {
        if (busy.get() || nowMs - lastSampleMs < SAMPLE_INTERVAL_MS) return false
        lastSampleMs = nowMs
        return true
    }

    /** Any thread: measures [frame] (recycled here) off the GL thread; [onReady] asks for a redraw. */
    fun estimate(frame: Bitmap, onReady: () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            frame.recycle()
            return
        }
        try {
            executor.execute {
                try {
                    val w = frame.width
                    val params = ShadowCastFix.estimate(w, frame.height) { y, rows, out -> frame.getPixels(out, 0, w, 0, y, w, rows) }
                    if (params != null) {
                        val boost = 1f + (MAX_BOOST - 1f) * params.floorWeight
                        if (estimates++ % LOG_EVERY == 0) PLog.d(TAG, "$params boost=%.2f".format(boost))
                        pending = bake(params, boost)
                        onReady()
                    }
                } catch (e: Exception) {
                    PLog.e(TAG, "estimate failed", e)
                } finally {
                    frame.recycle()
                    busy.set(false)
                }
            }
        } catch (e: Exception) { // executor shut down
            frame.recycle()
            busy.set(false)
        }
    }

    /** GL thread: uploads the newest table, if any. */
    fun upload() {
        val table = pending ?: return
        pending = null
        if (texture == 0) {
            texture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + TEXTURE_UNIT)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texture)
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA8, N, N, N, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, table)
            for (p in intArrayOf(GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_TEXTURE_MAG_FILTER)) {
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_LINEAR)
            }
            for (p in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R)) {
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_CLAMP_TO_EDGE)
            }
        } else {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + TEXTURE_UNIT)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texture)
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_3D, 0, 0, 0, 0, N, N, N, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, table)
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    fun bind(program: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + TEXTURE_UNIT)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uCastFixLut"), TEXTURE_UNIT)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /** GL thread: the correction is off (or the camera changed); the next one starts from a fresh estimate. */
    fun clear() {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        reset()
    }

    /** Context loss: the texture name is gone with the old context. */
    fun reset() {
        texture = 0
        pending = null
        lastSampleMs = 0L
    }

    fun release() {
        clear()
        executor.shutdown()
    }

    companion object {
        private const val TAG = "PreviewCastFix"
        private const val N = 33
        private const val TEXTURE_UNIT = 7
        private const val SAMPLE_INTERVAL_MS = 500L
        private const val LOG_EVERY = 20

        /**
         * Off by default: on the phone the estimate jumped between updates (floor weight 0 to 1 within seconds, so
         * the boost went between 1x and 3x). Debug builds: `adb shell setprop debug.lilac.previewcastfix on`,
         * read at each camera stream start.
         */
        val switchedOn: Boolean
            get() = BuildConfig.DEBUG && SystemPropertiesUtil.get("debug.lilac.previewcastfix")?.trim() == "on"

        /** Photo brightness over the cast-fixed viewfinder at 1 lux, linear: L* 23 / 12 in the mid-tones, 44 / 24 at the top. */
        const val MAX_BOOST = 3f

        /** Width of the viewfinder copy that is measured; the cast fix measures stills at 250 px too. */
        const val SAMPLE_LONG_EDGE = 250

        val GLSL = """
            uniform mediump sampler3D uCastFixLut;
            vec3 applyCastFix(vec3 srgb) {
                return texture(uCastFixLut, clamp(srgb, 0.0, 1.0) * ${(N - 1) / N.toFloat()} + ${0.5f / N}).rgb;
            }
        """.trimIndent()

        /** The cast fix of [p] and the [boost] for every node of an sRGB grid, as RGBA8, red fastest. */
        internal fun bake(p: ShadowCastFix.Params, boost: Float): ByteBuffer {
            val px = IntArray(N * N * N) { i ->
                val r = i % N; val g = i / N % N; val b = i / (N * N)
                (0xFF shl 24) or (level(r) shl 16) or (level(g) shl 8) or level(b)
            }
            ShadowCastFix.correct(px, px.size, p)
            val out = ByteBuffer.allocateDirect(px.size * 4).order(ByteOrder.nativeOrder())
            for (v in px) {
                out.put(boosted((v shr 16) and 0xFF, boost)).put(boosted((v shr 8) and 0xFF, boost))
                    .put(boosted(v and 0xFF, boost)).put(0xFF.toByte())
            }
            out.flip()
            return out
        }

        private fun level(node: Int) = (node * 255 + (N - 1) / 2) / (N - 1)

        private fun boosted(v: Int, boost: Float): Byte {
            if (boost == 1f) return v.toByte()
            val s = v / 255f
            val lin = if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
            val l = (lin * boost).coerceAtMost(1f)
            val e = if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1f / 2.4f) - 0.055f
            return (e * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
    }
}
