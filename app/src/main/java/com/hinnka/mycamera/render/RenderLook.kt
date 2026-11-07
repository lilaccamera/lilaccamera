package com.hinnka.mycamera.render

import android.content.Context
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.data.UserPreferences
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.lut.ChromaDenoiseDefaults
import com.hinnka.mycamera.raw.RawDenoiseDefaults
import com.hinnka.mycamera.raw.RawNoiseProfileManager
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.raw.RawSharpeningDefaults
import com.hinnka.mycamera.raw.fotufilm.FotufilmDocumentStorage
import java.util.Locale

/**
 * The tunable look of a single-frame RAW render: what a capture takes from the settings and what
 * "render again" may change. Everything else in the metadata and [RenderJob] stays as captured.
 */
data class RenderLook(
    val noiseReduction: Float,
    val chromaNoiseReduction: Float,
    val sharpening: Float,
    val rawExposureCompensation: Float,
    val rawHighlightsAdjustment: Float,
    val rawShadowsAdjustment: Float,
    val lensShadingCorrection: Boolean,
    val renderingEngine: RawRenderingEngine,
    val ultraHdr: Boolean,
    val noiseProfileId: String,
) {
    fun applyTo(metadata: MediaMetadata): MediaMetadata = metadata.copy(
        sharpening = sharpening,
        noiseReduction = noiseReduction,
        chromaNoiseReduction = chromaNoiseReduction,
        rawExposureCompensation = rawExposureCompensation,
        rawHighlightsAdjustment = rawHighlightsAdjustment,
        rawShadowsAdjustment = rawShadowsAdjustment,
        rawLensShadingCorrectionEnabled = lensShadingCorrection,
        rawRenderingEngine = renderingEngine,
        manualHdrEffectEnabled = ultraHdr,
        // A capture records the default film only when it renders with Fotufilm.
        fotufilmDocument = metadata.fotufilmDocument
            ?: FotufilmDocumentStorage.encode(FotufilmDocumentStorage.defaultDocument())
                ?.takeIf { renderingEngine == RawRenderingEngine.Fotufilm },
    )

    fun applyTo(job: RenderJob): RenderJob = job.copy(
        sharpeningValue = sharpening,
        noiseReductionValue = noiseReduction,
        chromaNoiseReductionValue = chromaNoiseReduction,
        rawNoiseProfileId = noiseProfileId,
    )

    /** Like "NR 0.50 · CNR 0.30 · Sharp 0.30 · UHDR"; settings at their defaults are left out. */
    /** [profileName] skips looking the noise model up, which reads the imported-profile list from disk. */
    fun label(context: Context, profileName: String? = null): String = buildList {
        add("NR %.2f".format(Locale.US, noiseReduction))
        add("CNR %.2f".format(Locale.US, chromaNoiseReduction))
        add("Sharp %.2f".format(Locale.US, sharpening))
        if (rawExposureCompensation != 0f) add("EV %+.2f".format(Locale.US, rawExposureCompensation))
        if (rawHighlightsAdjustment != 0f) add("Hi %+.2f".format(Locale.US, rawHighlightsAdjustment))
        if (rawShadowsAdjustment != 0f) add("Shad %+.2f".format(Locale.US, rawShadowsAdjustment))
        if (!lensShadingCorrection) add("LSC off")
        if (renderingEngine != RawRenderingEngine.AdobeCurve) add(renderingEngine.name)
        if (ultraHdr) add("UHDR")
        if (noiseProfileId != RawNoiseProfileManager.DEFAULT_PROFILE_ID) add(profileName ?: noiseProfileName(context))
    }.joinToString(LABEL_SEPARATOR)

    private fun noiseProfileName(context: Context): String = when (noiseProfileId) {
        RawNoiseProfileManager.SYSTEM_PROFILE_ID -> "System"
        RawNoiseProfileManager.XZ1C_PROFILE_ID -> "Measured"
        else -> ContentRepository.getInstance(context).rawNoiseProfileManager.getAvailableProfiles()
            .firstOrNull { it.id == noiseProfileId }
            ?.let { info -> info.nameResId?.let(context::getString) ?: info.getName() }
            ?: noiseProfileId
    }

    companion object {
        /** [MediaMetadata.customProperties] key of a re-render's [label]. */
        const val LABEL_PROPERTY = "renderAgainLabel"
        private const val LABEL_SEPARATOR = " · "

        /** What a capture now would use, by the same rules as the capture. */
        fun fromPreferences(userPrefs: UserPreferences?, cameraId: String?): RenderLook {
            val denoise = resolveCaptureDenoiseStrengths(isRawCapture = true, isMergedCapture = false, userPrefs)
            return RenderLook(
                noiseReduction = denoise.editableLuma,
                chromaNoiseReduction = denoise.editableChroma,
                sharpening = resolveCaptureSharpening(isRawCapture = true, userPrefs),
                rawExposureCompensation = userPrefs?.rawExposureCompensation ?: 0f,
                rawHighlightsAdjustment = userPrefs?.rawHighlightsAdjustment ?: 0f,
                rawShadowsAdjustment = userPrefs?.rawShadowsAdjustment ?: 0f,
                lensShadingCorrection = userPrefs?.rawLensShadingCorrectionEnabled ?: true,
                renderingEngine = resolveCaptureRawRenderingEngine(userPrefs),
                ultraHdr = defaultHdrEffectEnabled(hasEmbeddedGainmap = false, userPrefs),
                noiseProfileId = userPrefs?.rawNoiseProfileIdForLens(cameraId) ?: RawNoiseProfileManager.DEFAULT_PROFILE_ID,
            )
        }

        /** What [metadata] and [job] render with, resolved as GalleryManager.renderRawDngPhotoOutputs does. */
        fun fromPhoto(metadata: MediaMetadata, job: RenderJob) = RenderLook(
            noiseReduction = metadata.noiseReduction ?: job.noiseReductionValue,
            chromaNoiseReduction = metadata.chromaNoiseReduction
                ?: ChromaDenoiseDefaults.forRawCapture(job.chromaNoiseReductionValue),
            sharpening = metadata.sharpening ?: RawSharpeningDefaults.normalize(job.sharpeningValue),
            rawExposureCompensation = metadata.rawExposureCompensation ?: 0f,
            rawHighlightsAdjustment = metadata.rawHighlightsAdjustment ?: 0f,
            rawShadowsAdjustment = metadata.rawShadowsAdjustment ?: 0f,
            // Pro captures always record it; the render's fallback is the current setting, default on.
            lensShadingCorrection = metadata.rawLensShadingCorrectionEnabled ?: true,
            renderingEngine = metadata.rawRenderingEngine,
            ultraHdr = metadata.manualHdrEffectEnabled,
            noiseProfileId = job.rawNoiseProfileId,
        )

        /**
         * A strip caption for [label]: its first part that some other re-render of the same stack in
         * [stackLabels] lacks, so siblings read apart ("Sharp 0.60"); else its first part ("NR 0.80").
         */
        fun shortLabel(label: String, stackLabels: List<String>): String {
            val parts = label.split(LABEL_SEPARATOR)
            val others = stackLabels.filter { it != label }.map { it.split(LABEL_SEPARATOR) }
            return parts.firstOrNull { part -> others.any { part !in it } } ?: parts.first()
        }

        fun fileSuffixOf(label: String): String =
            label.replace(LABEL_SEPARATOR, "_").replace(" ", "").replace(Regex("[^A-Za-z0-9._+-]"), "")
    }
}

internal fun resolveCaptureSharpening(
    isRawCapture: Boolean,
    userPrefs: UserPreferences?,
): Float = if (isRawCapture) {
    userPrefs?.rawMaxSharpening ?: RawSharpeningDefaults.DEFAULT_STRENGTH
} else {
    0f
}.let(RawSharpeningDefaults::normalize)

internal data class CaptureDenoiseStrengths(
    val editableLuma: Float,
    val editableChroma: Float,
    val bakedLuma: Float?,
    val bakedChroma: Float?,
)

/**
 * RAW captures take the RAWmax luma / chroma strengths from the settings. A merge bakes them into
 * its default denoise before the DNG is written, so its editable strengths start at 0; a single
 * frame has no default denoise, so the same strengths become its editable initial values and the
 * user-adjustment denoise applies them (docs/photon-multiscale-denoise.md §10.3).
 */
internal fun resolveCaptureDenoiseStrengths(
    isRawCapture: Boolean,
    isMergedCapture: Boolean,
    userPrefs: UserPreferences?,
): CaptureDenoiseStrengths {
    if (!isRawCapture) return CaptureDenoiseStrengths(0f, 0f, null, null)
    val luma = RawDenoiseDefaults.normalize(
        userPrefs?.rawMaxNoiseReduction ?: RawDenoiseDefaults.RAW_MAX_LUMA_STRENGTH
    )
    val chroma = RawDenoiseDefaults.normalize(
        userPrefs?.rawMaxChromaNoiseReduction ?: RawDenoiseDefaults.RAW_MAX_CHROMA_STRENGTH
    )
    return if (isMergedCapture) {
        CaptureDenoiseStrengths(editableLuma = 0f, editableChroma = 0f, bakedLuma = luma, bakedChroma = chroma)
    } else {
        CaptureDenoiseStrengths(editableLuma = luma, editableChroma = chroma, bakedLuma = null, bakedChroma = null)
    }
}

internal fun resolveCaptureRawRenderingEngine(userPrefs: UserPreferences?): RawRenderingEngine =
    userPrefs?.rawRenderingEngine ?: RawRenderingEngine.AdobeCurve

internal fun defaultHdrEffectEnabled(hasEmbeddedGainmap: Boolean, userPrefs: UserPreferences?): Boolean =
    hasEmbeddedGainmap || (userPrefs?.ultraHdrGainMapEnabled ?: false)
