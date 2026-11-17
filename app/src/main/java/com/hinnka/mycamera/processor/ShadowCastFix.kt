package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import com.hinnka.mycamera.BuildConfig
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.SystemPropertiesUtil
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Removes the pink cast that the camera's own YUV frames carry in dim light, on finished Photo mode
 * images (8-bit sRGB-encoded ARGB).
 *
 * On the Xperia XZ1 Compact a lens-covered frame is not black: the sensor's black level is not fully
 * subtracted, and the white balance gains then tint the leftover floor (rear camera at ISO 5120: Cb +2.3;
 * front camera: about 10 to 15 levels of luma with a magenta or blue tint that changes from shot to shot).
 * It shows as pink, hazy shadows and a magenta cast in dim mid-tones, more on the front camera.
 *
 * Four steps, all measured per photo on a copy about 250 px wide, in linear light:
 * 1. Black floor: the 1st percentile of each channel. Removed as a whole when the darkest tones pile up
 *    at one level, as a floor does (20th / 1st green percentile below 3; dim shots measured 2.1 to 3.1).
 *    Dark but real colours spread out (bright shots measured 4.4 to 9.3): above 4.5 only its colour
 *    (R and B above G) is removed. In between it fades.
 * 2. Shadow colour: the median colour offset (linear R-G, B-G) of the darkest quarter of tones, which the
 *    leftover floor turns blue or magenta depending on the gains. In dim photos all of it is removed; in
 *    brighter ones only the share R and B have in common (a leftover floor is never green, as green has the
 *    smallest gain), so red or blue dark objects keep their colour. Full strength up to that quarter's top
 *    brightness, fading to none two stops above it; the brightness taken out is given back equally to all
 *    channels, so this step changes colour only.
 * 3. Magenta tint: the median colour of the flattest nearly neutral areas (walls, ceilings) gives the
 *    grey; only its magenta share is removed, never added, so warm or cool light keeps its colour. Full
 *    strength up to the grey's brightness, fading to none two stops above it, so bright areas keep theirs.
 * 4. Shadow lift, dim photos only: all channels scaled by up to 1.5 in the darkest quarter, fading out two
 *    stops above, to give back the detail and brightness step 1 takes. Not in photos with essentially no
 *    light, where it would only show noise.
 */
object ShadowCastFix {
    private const val TAG = "ShadowCastFix"
    private const val SMALL_WIDTH = 250
    private const val MAX_CAST = 0.5f // largest grey cast, log units (x1.65)
    private const val GREY_WIN = 0.6f // nearly neutral: R/G and B/G within this log distance of 1
    private const val FLAT_Q = 0.2f // flattest 20 % of nearly neutral pixels
    private const val MIN_GREY_FRAC = 0.02f // grey candidates covering less of the image fade the tint out
    private const val LUM_LO = 0.01f
    private const val LUM_HI = 0.85f
    private const val MAX_WHOLE_BLACK = 0.012f // linear, about 28 levels
    private const val MAX_COLOUR_BLACK = 0.004f
    private const val MAX_SHADOW = 0.03f // largest shadow colour offset, linear
    private const val LIFT = 0.5f // shadow lift in dim photos: x1.5 in the darkest quarter (x2 showed too much noise)
    private const val LIFT_DARK = 0.0065f // median linear luma (about 19 levels): no lift at or below, nothing to show but noise
    private const val LIFT_LIGHT = 0.012f // about 30 levels: full lift at or above
    private const val FADE_RANGE = 4f // tint fades out between the grey's brightness and 4x it (two stops)
    private const val BAND_ROWS = 64
    private const val ENCODE_STEPS = 16384
    private const val WEIGHT_STEPS = 4096
    private val LUMA = floatArrayOf(0.2126f, 0.7152f, 0.0722f)

    private val toLinear = FloatArray(256) { srgbToLinear(it / 255f) }
    private val toSrgb8 by lazy {
        IntArray(ENCODE_STEPS + 1) { (linearToSrgb(it / ENCODE_STEPS.toFloat()) * 255f + 0.5f).toInt().coerceIn(0, 255) }
    }

    class Params(
        val black: FloatArray, // per channel, linear
        val floorWeight: Float, // 1 = dim photo: whole floor and shadow colour removed; 0 = only the floor's colour
        val shadow: FloatArray, // shadow colour offset removed per channel (G is 0), linear
        val shadowLuma: Float, // top of the darkest quarter of tones
        val lift: Float, // shadow lift at the darkest quarter (0.5 = x1.5)
        val tint: Float, // magenta removed, log units
        val gains: FloatArray,
        val greyLuma: Float,
    ) {
        val isIdentity: Boolean
            get() = black.all { it < 1e-5f } && tint <= 0f && shadow.all { abs(it) < 1e-5f } && lift <= 0f

        override fun toString() =
            ("black=%.4f,%.4f,%.4f floorWeight=%.2f shadow=%.4f,%.4f below %.4f lift=%.2f tint=%.3f " +
                "gains=%.3f,%.3f,%.3f greyLuma=%.3f").format(
                black[0], black[1], black[2], floorWeight, shadow[0], shadow[2], shadowLuma, lift, tint,
                gains[0], gains[1], gains[2], greyLuma,
            )
    }

    /**
     * Corrects [bitmap] in place, 64 rows at a time. Returns the parameters used, or null if skipped.
     * Debug builds with `adb shell setprop debug.lilac.castfix.keep on` first save the image as it came in to
     * `uncorrected.jpg` in [photoDir], to tune the correction on the app's own output.
     */
    fun apply(bitmap: Bitmap, photoDir: File? = null): Params? {
        if (bitmap.config != Bitmap.Config.ARGB_8888 || !bitmap.isMutable) {
            PLog.w(TAG, "skipped: config=${bitmap.config} mutable=${bitmap.isMutable}")
            return null
        }
        if (BuildConfig.DEBUG && photoDir != null && SystemPropertiesUtil.get("debug.lilac.castfix.keep")?.trim() == "on") {
            runCatching { File(photoDir, "uncorrected.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
                .onFailure { PLog.w(TAG, "could not keep the uncorrected image", it) }
        }
        val start = System.currentTimeMillis()
        val w = bitmap.width
        val h = bitmap.height
        val params = estimate(w, h) { y, rows, out -> bitmap.getPixels(out, 0, w, 0, y, w, rows) }
        if (params == null || params.isIdentity) {
            PLog.d(TAG, "no correction ($params) in ${System.currentTimeMillis() - start} ms")
            return params
        }
        val bands = (h + BAND_ROWS - 1) / BAND_ROWS
        inParallel(bands) { from, to ->
            val band = IntArray(w * BAND_ROWS)
            for (i in from until to) {
                val y = i * BAND_ROWS
                val rows = min(BAND_ROWS, h - y)
                bitmap.getPixels(band, 0, w, 0, y, w, rows)
                correct(band, w * rows, params)
                bitmap.setPixels(band, 0, w, 0, y, w, rows)
            }
        }
        PLog.d(TAG, "${w}x$h $params in ${System.currentTimeMillis() - start} ms")
        return params
    }

    /**
     * Splits [count] items into contiguous ranges and runs [work] on each in its own thread. Half the cores
     * less one (3 of 8 on a Snapdragon 835): the camera, the viewfinder and other apps keep the rest.
     * Each thread holds one band buffer (about 1 MB at 4000 px wide), so memory stays small.
     */
    private fun inParallel(count: Int, work: (from: Int, to: Int) -> Unit) {
        val threads = (Runtime.getRuntime().availableProcessors() / 2 - 1).coerceIn(1, count.coerceAtLeast(1))
        val chunk = (count + threads - 1) / threads
        var failure: Throwable? = null
        (1 until threads).map { t ->
            thread(name = "ShadowCastFix-$t") {
                try { work(t * chunk, min(count, (t + 1) * chunk)) } catch (e: Throwable) { failure = e }
            }
        }.also { work(0, min(count, chunk)) }.forEach { it.join() }
        failure?.let { throw it }
    }

    /**
     * Measures the correction. [read] fills its array with `rows` full rows of ARGB pixels from row `y`;
     * it is called from several threads at once, for different rows.
     */
    internal fun estimate(width: Int, height: Int, read: (y: Int, rows: Int, out: IntArray) -> Unit): Params? {
        val step = max(1, width / SMALL_WIDTH)
        val sw = width / step
        val sh = height / step
        if (sw < 8 || sh < 8) return null
        val n = sw * sh
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        val area = (step * step).toFloat()
        inParallel(sh) { from, to ->
            val rows = IntArray(width * step)
            for (by in from until to) {
                read(by * step, step, rows)
                for (bx in 0 until sw) {
                    var sr = 0f
                    var sg = 0f
                    var sb = 0f
                    for (yy in 0 until step) {
                        val base = yy * width + bx * step
                        for (xx in 0 until step) {
                            val p = rows[base + xx]
                            sr += toLinear[(p shr 16) and 0xFF]
                            sg += toLinear[(p shr 8) and 0xFF]
                            sb += toLinear[p and 0xFF]
                        }
                    }
                    val i = by * sw + bx
                    r[i] = sr / area
                    g[i] = sg / area
                    b[i] = sb / area
                }
            }
        }

        val medianLuma = median(FloatArray(n) { LUMA[0] * r[it] + LUMA[1] * g[it] + LUMA[2] * b[it] })
        val liftGate = ((medianLuma - LIFT_DARK) / (LIFT_LIGHT - LIFT_DARK)).coerceIn(0f, 1f)

        // 1. Black floor.
        val q1 = floatArrayOf(quantile(r, 0.01f), quantile(g, 0.01f), quantile(b, 0.01f))
        val pileUp = quantile(g, 0.2f) / max(q1[1], 1e-6f)
        val floorWeight = ((4.5f - pileUp) / 1.5f).coerceIn(0f, 1f)
        val colour = floatArrayOf((q1[0] - q1[1]).coerceIn(0f, MAX_COLOUR_BLACK), 0f, (q1[2] - q1[1]).coerceIn(0f, MAX_COLOUR_BLACK))
        val black = FloatArray(3) { c -> floorWeight * q1[c].coerceIn(0f, MAX_WHOLE_BLACK) + (1f - floorWeight) * colour[c] }
        for (i in 0 until n) {
            r[i] = max(r[i] - black[0], 0f) / (1f - black[0])
            g[i] = max(g[i] - black[1], 0f) / (1f - black[1])
            b[i] = max(b[i] - black[2], 0f) / (1f - black[2])
        }

        // 2. Shadow colour.
        var y = FloatArray(n) { LUMA[0] * r[it] + LUMA[1] * g[it] + LUMA[2] * b[it] }
        val shadowLuma = quantile(y, 0.25f)
        val darkFrom = quantile(y, 0.01f)
        var dark = 0
        for (i in 0 until n) if (y[i] >= darkFrom && y[i] <= shadowLuma) dark++
        val dr = FloatArray(dark)
        val db = FloatArray(dark)
        var k = 0
        for (i in 0 until n) if (y[i] >= darkFrom && y[i] <= shadowLuma) {
            dr[k] = r[i] - g[i]
            db[k] = b[i] - g[i]
            k++
        }
        val sr = median(dr).coerceIn(-MAX_SHADOW, MAX_SHADOW)
        val sb = median(db).coerceIn(-MAX_SHADOW, MAX_SHADOW)
        val common = max(min(sr, sb), 0f)
        val shadow = floatArrayOf(floorWeight * sr + (1f - floorWeight) * common, 0f, floorWeight * sb + (1f - floorWeight) * common)
        if (shadowLuma > 0f && (shadow[0] != 0f || shadow[2] != 0f)) {
            for (i in 0 until n) {
                val wt = fade(y[i], shadowLuma)
                val nr = max(r[i] - wt * shadow[0], 0f)
                val nb = max(b[i] - wt * shadow[2], 0f)
                val back = LUMA[0] * (r[i] - nr) + LUMA[2] * (b[i] - nb)
                r[i] = nr + back
                g[i] += back
                b[i] = nb + back
            }
            y = FloatArray(n) { LUMA[0] * r[it] + LUMA[1] * g[it] + LUMA[2] * b[it] }
        }

        // 3. Grey of the flattest nearly neutral areas.
        val cv = FloatArray(n)
        for (cy in 0 until sh) for (cx in 0 until sw) {
            var s = 0f
            var s2 = 0f
            for (dy in -2..2) for (dx in -2..2) {
                val v = y[(cy + dy).coerceIn(0, sh - 1) * sw + (cx + dx).coerceIn(0, sw - 1)]
                s += v
                s2 += v * v
            }
            val mean = s / 25f
            cv[cy * sw + cx] = sqrt(max(s2 / 25f - mean * mean, 0f)) / max(mean, 1e-4f)
        }
        val cr = FloatArray(n) { ln(max(r[it], 1e-5f) / max(g[it], 1e-5f)) }
        val cb = FloatArray(n) { ln(max(b[it], 1e-5f) / max(g[it], 1e-5f)) }
        val ok = BooleanArray(n) { y[it] > LUM_LO && y[it] < LUM_HI && max(abs(cr[it]), abs(cb[it])) < GREY_WIN }
        var tint = 0f
        var greyLuma = 0f
        val okCount = ok.count { it }
        if (okCount >= 100) {
            val okCv = FloatArray(okCount)
            k = 0
            for (i in 0 until n) if (ok[i]) okCv[k++] = cv[i]
            val flat = quantile(okCv, FLAT_Q)
            var selCount = 0
            for (i in 0 until n) if (ok[i] && cv[i] <= flat) selCount++
            val sel = IntArray(selCount)
            k = 0
            for (i in 0 until n) if (ok[i] && cv[i] <= flat) sel[k++] = i
            var er = median(FloatArray(sel.size) { cr[sel[it]] })
            var eb = median(FloatArray(sel.size) { cb[sel[it]] })
            val big = max(abs(er), abs(eb))
            if (big > MAX_CAST) {
                er *= MAX_CAST / big
                eb *= MAX_CAST / big
            }
            val confidence = min(1f, sel.size / n.toFloat() / MIN_GREY_FRAC)
            tint = max((er + eb) / 2f * confidence, 0f) // only ever remove magenta
            greyLuma = median(FloatArray(sel.size) { y[sel[it]] })
        }
        val gains = floatArrayOf(exp(-tint), 1f, exp(-tint))
        val grey = floatArrayOf(exp(tint), 1f, exp(tint)) // the cast grey keeps its brightness
        val norm = dot(grey) / dot(FloatArray(3) { grey[it] * gains[it] })
        for (c in 0..2) gains[c] *= norm
        val lift = if (shadowLuma > 0f) LIFT * floorWeight * liftGate else 0f
        return Params(black, floorWeight, shadow, shadowLuma, lift, tint, gains, greyLuma)
    }

    /** Applies [p] to the first [count] ARGB pixels of [pixels]; alpha is kept. */
    internal fun correct(pixels: IntArray, count: Int, p: Params) {
        val enc = toSrgb8
        val b0 = p.black[0]; val b1 = p.black[1]; val b2 = p.black[2]
        val s0 = 1f / (1f - b0); val s1 = 1f / (1f - b1); val s2 = 1f / (1f - b2)
        val weight = if (p.tint > 0f && p.greyLuma > 0f) {
            FloatArray(WEIGHT_STEPS + 1) { fade(it / WEIGHT_STEPS.toFloat(), p.greyLuma) }
        } else null
        val shadowWeight = if (p.shadowLuma > 0f && p.shadow.any { it != 0f }) {
            FloatArray(WEIGHT_STEPS + 1) { fade(it / WEIGHT_STEPS.toFloat(), p.shadowLuma) }
        } else null
        val liftWeight = if (p.lift > 0f && p.shadowLuma > 0f) {
            FloatArray(WEIGHT_STEPS + 1) { 1f + p.lift * fade(it / WEIGHT_STEPS.toFloat(), p.shadowLuma) }
        } else null
        val o0 = p.shadow[0]; val o2 = p.shadow[2]
        val g0 = p.gains[0] - 1f; val g1 = p.gains[1] - 1f; val g2 = p.gains[2] - 1f
        for (i in 0 until count) {
            val px = pixels[i]
            var lr = max(toLinear[(px shr 16) and 0xFF] - b0, 0f) * s0
            var lg = max(toLinear[(px shr 8) and 0xFF] - b1, 0f) * s1
            var lb = max(toLinear[px and 0xFF] - b2, 0f) * s2
            if (shadowWeight != null) {
                val ws = shadowWeight[((LUMA[0] * lr + LUMA[1] * lg + LUMA[2] * lb) * WEIGHT_STEPS).toInt().coerceIn(0, WEIGHT_STEPS)]
                val nr = max(lr - ws * o0, 0f)
                val nb = max(lb - ws * o2, 0f)
                val back = LUMA[0] * (lr - nr) + LUMA[2] * (lb - nb) // brightness back, equally: colour only
                lr = nr + back
                lg += back
                lb = nb + back
            }
            if (weight != null) {
                val yv = LUMA[0] * lr + LUMA[1] * lg + LUMA[2] * lb
                val wt = weight[(yv * WEIGHT_STEPS).toInt().coerceIn(0, WEIGHT_STEPS)]
                lr *= 1f + wt * g0
                lg *= 1f + wt * g1
                lb *= 1f + wt * g2
            }
            if (liftWeight != null) {
                val k = liftWeight[((LUMA[0] * lr + LUMA[1] * lg + LUMA[2] * lb) * WEIGHT_STEPS).toInt().coerceIn(0, WEIGHT_STEPS)]
                lr *= k
                lg *= k
                lb *= k
            }
            pixels[i] = (px and 0xFF000000.toInt()) or
                (enc[(lr * ENCODE_STEPS + 0.5f).toInt().coerceIn(0, ENCODE_STEPS)] shl 16) or
                (enc[(lg * ENCODE_STEPS + 0.5f).toInt().coerceIn(0, ENCODE_STEPS)] shl 8) or
                enc[(lb * ENCODE_STEPS + 0.5f).toInt().coerceIn(0, ENCODE_STEPS)]
        }
    }

    /** 1 at or below [y0], 0 at [FADE_RANGE] times [y0] and above, smooth in log brightness between. */
    private fun fade(y: Float, y0: Float): Float {
        val t = (ln(max(y, 1e-6f) / (FADE_RANGE * y0)) / ln(1f / FADE_RANGE)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun dot(v: FloatArray) = LUMA[0] * v[0] + LUMA[1] * v[1] + LUMA[2] * v[2]

    private fun median(v: FloatArray) = quantile(v, 0.5f)

    /** Linear-interpolated quantile, as numpy's default. */
    private fun quantile(v: FloatArray, q: Float): Float {
        if (v.isEmpty()) return 0f
        val s = v.copyOf().also { it.sort() }
        val pos = q * (s.size - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }

    private fun srgbToLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(v: Float) = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}
