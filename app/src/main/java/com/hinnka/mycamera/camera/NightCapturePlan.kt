package com.hinnka.mycamera.camera

import android.util.Range
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Frame count and per-frame exposure of a low-light burst, and how much to brighten the merged photo. */
data class NightCapturePlan(
    val frames: Int,
    val iso: Int,
    val exposureTimeNs: Long,
    val brightenGain: Float,
)

/**
 * Low-light burst exposure from how steady the phone is. Noise follows the light collected (shutter
 * time times frame count), not the ISO, so each frame gets the longest shutter the measured shake
 * allows, a modest ISO, and the merge is brightened afterwards by what the extra frames make up.
 */
object NightCapturePlanner {
    const val STEADINESS_WINDOW_MS = 500L
    /** Auto's frame count where no plan can be made (the front camera), and its usual count before the press. */
    const val AUTO_FRAMES = 12
    // shortcut: Auto stays within the 12 frames tested at 12 MP on lilac; raise after a memory test at 16-20.
    private const val AUTO_MIN_FRAMES = 6

    private const val NS_PER_S = 1_000_000_000.0
    private const val MIN_SHUTTER_NS = 1_000_000_000L / 30
    private const val MAX_SHUTTER_NS = 1_000_000_000L / 4
    // shortcut: guessed motion blur allowance per frame; set it from handheld tests at 1/15, 1/8 and 1/4 s.
    private const val BLUR_BUDGET_PX = 2.0
    // Handheld tests: ISO 800 at 1/15 s merged cleanly, colour blotches grew above it; short frames need more.
    private const val STEADY_ISO_CAP = 800
    private const val SHAKY_ISO_CAP = 1600
    private const val SHAKY_SHUTTER_NS = 1_000_000_000L / 20
    private const val MAX_BRIGHTEN_GAIN = 3.5f

    /**
     * @param aeIso, aeExposureNs what auto exposure chose for the viewfinder.
     * @param angularSpeedRadS root-mean-square rotation speed just before the shot.
     * @param focalLengthPx focal length in pixels of the captured frame.
     * @param frames the frame count set by hand, or 0 for Auto: enough frames to make up the light.
     * @param fast 1/30 s frames whatever the steadiness, for shaky hands or moving subjects.
     */
    fun plan(
        aeIso: Int,
        aeExposureNs: Long,
        angularSpeedRadS: Float,
        focalLengthPx: Float,
        frames: Int,
        isoRange: Range<Int>,
        exposureRangeNs: Range<Long>?,
        fast: Boolean = false,
    ): NightCapturePlan {
        // As bright as auto exposure.
        val target = aeIso.toDouble() * aeExposureNs
        val steadyNs = BLUR_BUDGET_PX / (max(angularSpeedRadS, 1e-4f) * focalLengthPx) * NS_PER_S
        var shutter = if (fast) MIN_SHUTTER_NS else steadyNs.toLong().coerceIn(MIN_SHUTTER_NS, MAX_SHUTTER_NS)
        // A brighter scene does not need the longest shutter: stop where the lowest ISO is enough.
        shutter = min(shutter, (target / isoRange.lower).toLong())
        exposureRangeNs?.let { shutter = shutter.coerceIn(it.lower, it.upper) }
        shutter = shutter.coerceAtLeast(1L)
        val isoCap = if (shutter < SHAKY_SHUTTER_NS) SHAKY_ISO_CAP else STEADY_ISO_CAP
        val iso = (target / shutter).roundToInt()
            .coerceIn(isoRange.lower, max(isoRange.lower, min(isoCap, isoRange.upper)))
        // Brightening lifts noise like a higher ISO would; merging N frames cuts it by about sqrt(N),
        // so up to sqrt(N) the result is no noisier than one frame at the higher ISO (3.5x at 12 frames).
        // Auto takes just enough frames for the brightening the scene needs.
        val neededGain = (target / (iso.toDouble() * shutter)).toFloat()
        val count = if (frames > 0) frames else autoFrames(neededGain)
        val maxGain = sqrt(count.toFloat()).coerceIn(1f, MAX_BRIGHTEN_GAIN)
        val gain = neededGain.coerceIn(1f, maxGain)
        return NightCapturePlan(count, iso, shutter, gain)
    }

    /** Auto: merging N frames allows brightening by sqrt(N) without more noise, so N = gain squared. */
    internal fun autoFrames(neededGain: Float): Int =
        ceil(neededGain * neededGain).toInt().coerceIn(AUTO_MIN_FRAMES, AUTO_FRAMES)
}
