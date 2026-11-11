package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import com.hinnka.mycamera.ml.RelativeDepthMap
import com.hinnka.mycamera.ml.SubjectMask
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A cheap stand-in for the background blur, shown while the real one renders and while the aperture or the
 * focus ring is dragged: a small copy of the photo blurred on the CPU and mixed back by how far each place is
 * from the focus. It follows the renderer's rules (the cut-out subject stays sharp in Quick, or when the focus
 * is on the subject; otherwise depth decides) but is only a rough guide to the amount and the sharp area.
 * It never touches the GPU.
 *
 * The blur is built at a few strengths ([LEVELS]); the editor cross-fades between the two around the aperture
 * under the finger, so the preview grows smoothly instead of stepping from stop to stop.
 */
internal object BlurPreview {
    private const val MAX_EDGE = 480
    private const val GRID = 8  // weights are worked out every 8 px and blended in between

    /** Blur strengths, as 2 / f-number (the renderer's radius rule): sharp, f/16, f/8, about f/4.5, f/2.8, f/2, f/1.2. */
    val LEVELS = floatArrayOf(0f, 0.125f, 0.25f, 0.45f, 0.7f, 1f, 1.67f)

    /** Blur strength for an f-number; Off (0) is sharp. */
    fun strength(aperture: Float): Float = if (aperture > 0f) 2f / aperture else 0f

    /** A small copy of the photo and how strongly each place of it is blurred, for one focus. */
    class Prepared(val width: Int, val height: Int, val sharp: IntArray, val weight: FloatArray)

    /**
     * @param base the photo as the editor shows it, without the blur
     * @param toSource maps a point on [base] (fractions of its size) to the original image the maps describe
     * @param focusX the focus point in the original image, as a fraction of its width (likewise [focusY])
     */
    fun prepare(
        base: Bitmap,
        toSource: (Float, Float) -> Pair<Float, Float>,
        mask: SubjectMask,
        depth: RelativeDepthMap?,
        focusX: Float,
        focusY: Float,
        quick: Boolean,
        widthScale: Float = 1f,
    ): Prepared {
        val scale = min(1f, MAX_EDGE.toFloat() / max(base.width, base.height))
        val w = max(1, (base.width * scale).roundToInt())
        val h = max(1, (base.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(base, w, h, true)
        val sharp = IntArray(w * h)
        small.getPixels(sharp, 0, w, 0, 0, w, h)
        if (small !== base) small.recycle()

        // As the renderer: Quick keeps the cut-out sharp and blurs the rest evenly; with depth, the blur grows
        // with the distance from the focus point's depth, and the cut-out stays sharp when the focus is on it.
        val subjectFocus = mask.sample(focusX, focusY) >= 0.5f
        val focusDepth = depth?.let { sampleDepth(it, focusX, focusY) } ?: 0f
        fun weightAt(px: Float, py: Float): Float {
            val (sx, sy) = toSource(px, py)
            if (quick || depth == null) return 1f - mask.sample(sx, sy)
            val distance = abs(sampleDepth(depth, sx, sy) - focusDepth)
            // The renderer ramps the blur in over a wide depth range; a steep ramp looked stronger than it.
            // A chosen depth of field widens or narrows the sharp slice and its ramp, as in the renderer.
            val byDepth = ((distance - 0.05f * widthScale) / (0.5f * widthScale)).coerceIn(0f, 1f)
            return if (subjectFocus) byDepth * (1f - mask.sample(sx, sy)) else byDepth
        }

        val gw = w / GRID + 2
        val gh = h / GRID + 2
        val grid = FloatArray(gw * gh) { i ->
            weightAt(((i % gw) * GRID).toFloat() / w, ((i / gw) * GRID).toFloat() / h)
        }
        val weight = FloatArray(w * h)
        for (y in 0 until h) {
            val gy = y.toFloat() / GRID
            val y0 = gy.toInt().coerceAtMost(gh - 2)
            val fy = gy - y0
            for (x in 0 until w) {
                val gx = x.toFloat() / GRID
                val x0 = gx.toInt().coerceAtMost(gw - 2)
                val fx = gx - x0
                val top = grid[y0 * gw + x0] * (1 - fx) + grid[y0 * gw + x0 + 1] * fx
                val bottom = grid[(y0 + 1) * gw + x0] * (1 - fx) + grid[(y0 + 1) * gw + x0 + 1] * fx
                weight[y * w + x] = (top * (1 - fy) + bottom * fy).coerceIn(0f, 1f)
            }
        }
        return Prepared(w, h, sharp, weight)
    }

    /** The preview at one blur [strength] (see [LEVELS]). */
    fun layer(prepared: Prepared, strength: Float): Bitmap {
        val w = prepared.width
        val h = prepared.height
        // The renderer's largest blur disc is about 2 % of the long edge at f/2; two box passes of radius r spread
        // about as far as a disc of radius 2r, so r is half of that; more looked stronger than the render.
        val radius = (MAX_EDGE * 0.01f * strength).roundToInt()
        if (radius < 1) return Bitmap.createBitmap(prepared.sharp, w, h, Bitmap.Config.ARGB_8888)
        val blurred = boxBlur(boxBlur(prepared.sharp, w, h, radius), w, h, radius)
        val out = IntArray(w * h) { i -> mix(prepared.sharp[i], blurred[i], prepared.weight[i]) }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sampleDepth(depth: RelativeDepthMap, x: Float, y: Float): Float {
        val px = (x.coerceIn(0f, 1f) * (depth.width - 1)).roundToInt()
        val py = (y.coerceIn(0f, 1f) * (depth.height - 1)).roundToInt()
        return depth.values[py * depth.width + px]
    }

    private fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // A box blur along rows, then columns; twice in a row it is close to a Gaussian.
    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        blurPass(src, tmp, w, h, r, horizontal = true)
        val out = IntArray(src.size)
        blurPass(tmp, out, w, h, r, horizontal = false)
        return out
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1)
                return src[if (horizontal) line * w + c else c * w + line]
            }
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = at(i)
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until length) {
                dst[if (horizontal) line * w + i else i * w + line] =
                    (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val add = at(i + r + 1)
                val remove = at(i - r)
                sr += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                sb += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }
}
