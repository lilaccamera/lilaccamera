package com.hinnka.mycamera.processor

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Noise allowance for the YUV merge's motion check, measured from the burst itself.
 *
 * The check models frame noise as shot plus read noise of fine, pixel-sized grain, and expects its
 * smoothing to remove most of it. Processed YUV frames carry coarse, blotchy noise that the
 * smoothing barely reduces, so at higher ISO and brighter levels plain noise exceeds the model and
 * whole frames are merged as if they had moved. This compares the model with the differences the
 * check actually sees between the reference and an aligned frame, per brightness level, and widens
 * the model where the frames differ more. It never narrows it.
 */
internal object YuvMeasuredNoise {
    /** One value per point of the noise estimates LUT, at signal x / (POINTS - 1). */
    const val POINTS = MgcSpatialNoiseEstimatesLut.WIDTH
    const val MAX_SCALE = 16f
    private const val CHANNELS = 3
    private const val MIN_SAMPLES = 300
    // The median absolute value of zero-mean Gaussian noise is 0.6745 sigma; the median keeps
    // moving areas (a minority of the samples) from counting as noise.
    private const val MAD_TO_SIGMA = 1.4826f

    /**
     * @property scales multiplier per LUT point, 1 to [MAX_SCALE]; levels without enough samples take
     *   the nearest measured level, and all are 1 when nothing was measured.
     * @property ratios measured over modelled variance per LUT point before clamping (NaN: too few samples).
     * @property offsets median signed difference per LUT point, averaged over the channels (NaN: too few).
     * @property counts samples per LUT point.
     */
    class Result(val scales: FloatArray, val ratios: FloatArray, val offsets: FloatArray, val counts: IntArray)

    /**
     * @param samples interleaved (dR, dG, dB, signal) differences; signal < 0 marks a sample to skip.
     * @param modelVariance the variance the check allows for one channel of the difference at a
     *   signal level, after its own smoothing.
     */
    fun measure(samples: FloatArray, modelVariance: (signal: Float, channel: Int) -> Float): Result {
        val count = samples.size / 4
        val binOf = IntArray(count) { i ->
            val signal = samples[i * 4 + 3]
            if (signal < 0f || !signal.isFinite()) -1 else (signal.coerceAtMost(1f) * (POINTS - 1)).roundToInt()
        }
        val counts = IntArray(POINTS)
        for (bin in binOf) if (bin >= 0) counts[bin]++

        val ratios = FloatArray(POINTS) { Float.NaN }
        val offsets = FloatArray(POINTS) { Float.NaN }
        for (bin in 0 until POINTS) {
            val n = counts[bin]
            if (n < MIN_SAMPLES) continue
            val signal = bin.toFloat() / (POINTS - 1)
            var ratioSum = 0f
            var offsetSum = 0f
            for (channel in 0 until CHANNELS) {
                val values = FloatArray(n)
                var k = 0
                for (i in 0 until count) if (binOf[i] == bin) values[k++] = samples[i * 4 + channel]
                values.sort()
                offsetSum += values[n / 2]
                for (j in 0 until n) values[j] = abs(values[j])
                values.sort()
                val sigma = MAD_TO_SIGMA * values[n / 2]
                ratioSum += sigma * sigma / modelVariance(signal, channel).coerceAtLeast(1e-12f)
            }
            ratios[bin] = ratioSum / CHANNELS
            offsets[bin] = offsetSum / CHANNELS
        }
        val measured = ratios.indices.filter { !ratios[it].isNaN() }
        val scales = FloatArray(POINTS) { bin ->
            if (measured.isEmpty()) {
                1f
            } else {
                // Levels the frame has too little of take the nearest measured level.
                ratios[measured.minBy { abs(it - bin) }].coerceIn(1f, MAX_SCALE)
            }
        }
        return Result(scales, ratios, offsets, counts)
    }
}
