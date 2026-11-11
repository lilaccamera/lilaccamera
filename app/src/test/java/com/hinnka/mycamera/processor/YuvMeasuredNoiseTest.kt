package com.hinnka.mycamera.processor

import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class YuvMeasuredNoiseTest {
    private val model = { _: Float, _: Int -> 1e-4f }

    /** (dR, dG, dB, signal) samples: Gaussian differences of the given variance at one signal level. */
    private fun samples(signal: Float, variance: Float, count: Int, random: Random, outliers: Double = 0.0) =
        FloatArray(count * 4) { i ->
            when {
                i % 4 == 3 -> signal
                random.nextDouble() < outliers -> 0.5f   // a moving area: far above any noise
                else -> (random.nextGaussian() * Math.sqrt(variance.toDouble())).toFloat()
            }
        }

    @Test
    fun noisierFramesWidenTheModelByTheMeasuredRatio() {
        val scales = YuvMeasuredNoise.measure(samples(4f / 9f, 3e-4f, 20000, Random(1)), model).scales
        assertEquals(3f, scales[4], 0.15f)
    }

    @Test
    fun movingAreasDoNotCountAsNoise() {
        val scales = YuvMeasuredNoise.measure(samples(4f / 9f, 3e-4f, 20000, Random(2), outliers = 0.15), model).scales
        // 15 % motion shifts the median a little (about 1.5 times the variance), nowhere near the
        // 800-fold variance of the moving samples themselves.
        assertEquals(4.4f, scales[4], 0.6f)
    }

    @Test
    fun theModelIsNeverNarrowed() {
        val scales = YuvMeasuredNoise.measure(samples(4f / 9f, 2e-5f, 20000, Random(3)), model).scales
        assertEquals(1f, scales[4], 0f)
    }

    @Test
    fun scalesAreCapped() {
        val scales = YuvMeasuredNoise.measure(samples(4f / 9f, 1f, 20000, Random(4)), model).scales
        assertEquals(YuvMeasuredNoise.MAX_SCALE, scales[4], 0f)
    }

    @Test
    fun levelsWithoutSamplesTakeTheNearestMeasuredLevel() {
        val dark = samples(1f / 9f, 2e-4f, 5000, Random(5))
        val bright = samples(7f / 9f, 6e-4f, 5000, Random(6))
        val scales = YuvMeasuredNoise.measure(dark + bright, model).scales
        assertEquals(2f, scales[0], 0.2f)
        assertEquals(2f, scales[3], 0.2f)
        assertEquals(6f, scales[5], 0.6f)
        assertEquals(6f, scales[9], 0.6f)
    }

    @Test
    fun skippedOrTooFewSamplesLeaveTheModelAlone() {
        val skipped = FloatArray(4000 * 4) { i -> if (i % 4 == 3) -1f else 0.4f }
        assertArrayEquals(FloatArray(YuvMeasuredNoise.POINTS) { 1f }, YuvMeasuredNoise.measure(skipped, model).scales, 0f)
        val few = samples(0.5f, 1e-2f, 100, Random(7))
        assertArrayEquals(FloatArray(YuvMeasuredNoise.POINTS) { 1f }, YuvMeasuredNoise.measure(few, model).scales, 0f)
    }

    @Test
    fun reportsRawRatiosAndSignedOffsets() {
        val shifted = samples(4f / 9f, 3e-4f, 20000, Random(8)).also { a ->
            for (i in a.indices) if (i % 4 != 3) a[i] += 0.01f
        }
        val result = YuvMeasuredNoise.measure(shifted, model)
        assertEquals(0.01f, result.offsets[4], 0.002f)
        assertEquals(20000, result.counts[4])
        assertEquals(true, result.ratios[0].isNaN())
    }

    @Test
    fun lutScalesEveryVarianceAtItsSignalPoint() {
        val one = FloatArray(4) { 0.01f }
        val plain = MgcSpatialNoiseEstimatesLut.create(one, one, one, one)
        val scale = FloatArray(MgcSpatialNoiseEstimatesLut.WIDTH) { it.toFloat() + 1f }
        val scaled = MgcSpatialNoiseEstimatesLut.create(one, one, one, one, scale)
        for (i in plain.indices) {
            val x = (i / MgcSpatialNoiseEstimatesLut.CHANNELS) % MgcSpatialNoiseEstimatesLut.WIDTH
            assertEquals(plain[i] * scale[x], scaled[i], 1e-7f)
        }
    }
}
