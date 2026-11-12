package com.hinnka.mycamera.camera

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal object HdrBracketConfig {
    const val YUV_LONG_EV = 2.2f
    const val YUV_SHORT_EV = -1.5f
    const val YUV_LONG_MAX_SHUTTER_NS = 10_000_000L
    private const val CLIPPED_LUMA = 250
    private const val DARK_LUMA = 24

    /**
     * Bracket EVs from the viewfinder histogram: the short frame goes darker the more is clipped, the long
     * frame brighter the more is near black. Falls back to the fixed [YUV_SHORT_EV] and [YUV_LONG_EV].
     */
    fun adaptiveEvs(viewfinderHistogram: IntArray?): Pair<Float, Float> {
        val histogram = viewfinderHistogram?.takeIf { it.size == 256 } ?: return YUV_SHORT_EV to YUV_LONG_EV
        val total = histogram.sum().toFloat().takeIf { it > 0f } ?: return YUV_SHORT_EV to YUV_LONG_EV
        val clipped = (CLIPPED_LUMA..255).sumOf { histogram[it] } / total
        val dark = (0..DARK_LUMA).sumOf { histogram[it] } / total
        val short = when {
            clipped < 0.005f -> -1f
            clipped < 0.02f -> -1.5f
            clipped < 0.05f -> -2f
            else -> -2.5f
        }
        val long = when {
            dark < 0.05f -> 1f
            dark < 0.15f -> 2f
            dark < 0.30f -> 2.5f
            else -> 3f
        }
        return short to long
    }

    fun planManualExposure(
        baseIso: Int,
        baseShutterNs: Long,
        evOffset: Float,
        isoLower: Int,
        isoUpper: Int,
        shutterLowerNs: Long,
        shutterUpperNs: Long,
        longShutterLimitNs: Long = YUV_LONG_MAX_SHUTTER_NS,
    ): Pair<Int, Long> {
        val multiplier = 2.0.pow(evOffset.toDouble())
        val targetProduct = baseIso.toDouble() * baseShutterNs.toDouble() * multiplier
        val effectiveShutterUpperNs = if (evOffset > 0f) {
            minOf(shutterUpperNs, longShutterLimitNs).coerceAtLeast(shutterLowerNs)
        } else {
            shutterUpperNs
        }
        val shutterFirst = (baseShutterNs.toDouble() * multiplier)
            .roundToLong()
            .coerceIn(shutterLowerNs, effectiveShutterUpperNs)
        val isoForShutter = (targetProduct / shutterFirst.toDouble())
            .roundToInt()
            .coerceIn(isoLower, isoUpper)
        val shutterForIso = (targetProduct / isoForShutter.toDouble())
            .roundToLong()
            .coerceIn(shutterLowerNs, effectiveShutterUpperNs)

        return Pair(isoForShutter, shutterForIso)
    }
}
