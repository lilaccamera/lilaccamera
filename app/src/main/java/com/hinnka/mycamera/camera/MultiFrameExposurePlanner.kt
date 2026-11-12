package com.hinnka.mycamera.camera

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class MultiFrameLongExposurePlan(
    val sensitivityIso: Int,
    val exposureTimeNs: Long,
    val targetExposureProduct: Double,
    val plannedExposureProduct: Double,
    val plannedDeltaEv: Double,
    val exposureTimeUpperLimitNs: Long,
    val isoUpperLimited: Boolean,
    val shutterUpperLimited: Boolean,
    val upperLimitsProduceLowerExposureThanBase: Boolean,
)

internal data class HighlightUnderexposurePlan(
    val sensitivityIso: Int,
    val exposureTimeNs: Long,
    val clippedFraction: Float,
    val plannedDeltaEv: Double,
)

internal object MultiFrameExposurePlanner {
    private val LN_2 = ln(2.0)

    fun planLongExposure(
        baseIso: Int,
        baseExposureTimeNs: Long,
        isoLower: Int,
        isoUpper: Int,
        exposureTimeLowerNs: Long,
        exposureTimeUpperNs: Long,
        targetExposureEv: Float = MultiFrameConfig.LONG_FRAME_EXPOSURE_EV.toFloat(),
    ): MultiFrameLongExposurePlan {
        require(baseIso > 0 && baseExposureTimeNs > 0L) { "Base exposure must be positive" }
        require(isoLower > 0 && isoUpper >= isoLower) { "Invalid ISO range" }
        require(exposureTimeLowerNs > 0L && exposureTimeUpperNs >= exposureTimeLowerNs) {
            "Invalid exposure-time range"
        }

        val maximumLongExposureTimeNs = min(
            exposureTimeUpperNs,
            max(
                MultiFrameConfig.LONG_FRAME_MAX_EXPOSURE_TIME_NS,
                baseExposureTimeNs,
            ),
        )
        require(maximumLongExposureTimeNs >= exposureTimeLowerNs) {
            "Sensor minimum exposure time exceeds the long-frame shutter limit"
        }

        val exposureMultiplier = 2.0.pow(MultiFrameConfig.normalizeLongFrameExposureEv(targetExposureEv).toDouble())
        val baseExposureProduct = baseIso.toDouble() * baseExposureTimeNs.toDouble()
        val targetExposureProduct = baseExposureProduct * exposureMultiplier
        val unboundedExposureTimeNs = baseExposureTimeNs.toDouble() * exposureMultiplier
        val initialExposureTimeNs = unboundedExposureTimeNs
            .roundToLong()
            .coerceIn(exposureTimeLowerNs, maximumLongExposureTimeNs)
        val sensitivityIso = (targetExposureProduct / initialExposureTimeNs.toDouble())
            .roundToInt()
            .coerceIn(isoLower, isoUpper)
        val exposureTimeNs = (targetExposureProduct / sensitivityIso.toDouble())
            .roundToLong()
            .coerceIn(exposureTimeLowerNs, maximumLongExposureTimeNs)
        val plannedExposureProduct = sensitivityIso.toDouble() * exposureTimeNs.toDouble()

        return MultiFrameLongExposurePlan(
            sensitivityIso = sensitivityIso,
            exposureTimeNs = exposureTimeNs,
            targetExposureProduct = targetExposureProduct,
            plannedExposureProduct = plannedExposureProduct,
            plannedDeltaEv = ln(plannedExposureProduct / baseExposureProduct) / LN_2,
            exposureTimeUpperLimitNs = maximumLongExposureTimeNs,
            isoUpperLimited = sensitivityIso == isoUpper &&
                plannedExposureProduct < targetExposureProduct,
            shutterUpperLimited = unboundedExposureTimeNs > maximumLongExposureTimeNs.toDouble(),
            upperLimitsProduceLowerExposureThanBase = sensitivityIso == isoUpper &&
                exposureTimeNs == maximumLongExposureTimeNs &&
                plannedExposureProduct < baseExposureProduct,
        )
    }

    /**
     * Darker same-exposure burst when the viewfinder shows clipped highlights, as HDR+ does: the merge
     * removes the extra noise and the tone mapping lifts the shadows. Darkening by k EV costs 2^k light,
     * merging N frames gains N, so k is capped at log2(N) to keep shadows no noisier than one normal frame.
     * ISO goes down first; the shutter (auto exposure's flicker-safe choice) only shortens at the ISO floor.
     * Returns null when nothing changes.
     */
    fun planHighlightUnderexposure(
        viewfinderHistogram: IntArray?,
        frameCount: Int,
        baseIso: Int,
        baseExposureTimeNs: Long,
        isoLower: Int,
        exposureTimeLowerNs: Long,
    ): HighlightUnderexposurePlan? {
        val histogram = viewfinderHistogram?.takeIf { it.size == 256 } ?: return null
        if (frameCount < 2 || baseIso <= 0 || baseExposureTimeNs <= 0L) return null
        val total = histogram.sum()
        if (total <= 0) return null
        val clipped = (CLIPPED_LUMA..255).sumOf { histogram[it] }.toFloat() / total
        val wantedEv = CLIP_STEPS.lastOrNull { clipped >= it.first }?.second ?: return null
        val ev = min(wantedEv, ln(frameCount.toDouble()) / LN_2)
        val target = baseIso.toDouble() * baseExposureTimeNs.toDouble() / 2.0.pow(ev)
        val iso = (target / baseExposureTimeNs).roundToInt().coerceIn(min(isoLower, baseIso), baseIso)
        val exposureTimeNs = (target / iso).roundToLong()
            .coerceIn(min(exposureTimeLowerNs, baseExposureTimeNs), baseExposureTimeNs)
        if (iso == baseIso && exposureTimeNs == baseExposureTimeNs) return null
        return HighlightUnderexposurePlan(
            sensitivityIso = iso,
            exposureTimeNs = exposureTimeNs,
            clippedFraction = clipped,
            plannedDeltaEv = ln(iso.toDouble() * exposureTimeNs / (baseIso.toDouble() * baseExposureTimeNs)) / LN_2,
        )
    }

    private const val CLIPPED_LUMA = 250
    // Share of the viewfinder at white -> EV to darken; HDR+ caps its own underexposure near 3 EV.
    private val CLIP_STEPS = listOf(0.005f to 1.0, 0.02f to 2.0, 0.05f to 3.0)
}
