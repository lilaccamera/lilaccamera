package com.hinnka.mycamera.ui.gallery

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.RecipeParam
import com.hinnka.mycamera.ui.components.displayValueRange
import com.hinnka.mycamera.ui.components.exposureSteps
import com.hinnka.mycamera.ui.components.fromDisplayValue
import com.hinnka.mycamera.ui.components.toDisplayValue
import com.hinnka.mycamera.ui.icons.AppIcons
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a setting is stored and when its change reaches the photo. */
internal enum class SettingKind {
    /** A recipe value: the preview re-renders while the ruler moves. */
    Recipe,

    /** A RAW render tone value: saved and re-rendered from the DNG when the finger lifts. */
    RawTone,

    /** Noise reduction: the denoised base is rebuilt when the finger lifts. */
    Denoise,

    /** AI denoise: Off or On. */
    AiDenoise,
}

/**
 * The settings of Light, Color, Effects and Detail, in their order on screen. One exposure set per photo:
 * RAW photos show the RAW render's Exposure, Highlights, Shadows, Whites and Blacks, JPEGs the recipe's
 * Exposure, Highlights and Shadows.
 */
internal enum class EditorSetting(
    val section: EditorSection,
    val kind: SettingKind,
    @StringRes val label: Int,
    @StringRes val explanation: Int,
    /** The change key from the view model's opening snapshot. */
    val key: String,
    val param: RecipeParam? = null,
    val glyph: EditorGlyph? = null,
    val rawOnly: Boolean = false,
    val jpegOnly: Boolean = false,
) {
    Exposure(EditorSection.Light, SettingKind.Recipe, R.string.recipe_param_exposure, R.string.editor_explain_exposure, "recipe.EXPOSURE", RecipeParam.EXPOSURE, jpegOnly = true),
    RawExposure(EditorSection.Light, SettingKind.RawTone, R.string.recipe_param_exposure, R.string.editor_explain_raw_exposure, "rawExposure", rawOnly = true),
    Contrast(EditorSection.Light, SettingKind.Recipe, R.string.recipe_param_contrast, R.string.editor_explain_contrast, "recipe.CONTRAST", RecipeParam.CONTRAST, EditorGlyph.Contrast),
    Highlights(EditorSection.Light, SettingKind.Recipe, R.string.recipe_param_highlights, R.string.editor_explain_highlights, "recipe.HIGHLIGHTS", RecipeParam.HIGHLIGHTS, EditorGlyph.Highlights, jpegOnly = true),
    RawHighlights(EditorSection.Light, SettingKind.RawTone, R.string.recipe_param_highlights, R.string.editor_explain_highlights, "rawHighlights", glyph = EditorGlyph.Highlights, rawOnly = true),
    Shadows(EditorSection.Light, SettingKind.Recipe, R.string.recipe_param_shadows, R.string.editor_explain_shadows, "recipe.SHADOWS", RecipeParam.SHADOWS, EditorGlyph.Shadows, jpegOnly = true),
    RawShadows(EditorSection.Light, SettingKind.RawTone, R.string.recipe_param_shadows, R.string.editor_explain_shadows, "rawShadows", glyph = EditorGlyph.Shadows, rawOnly = true),
    Whites(EditorSection.Light, SettingKind.RawTone, R.string.editor_whites, R.string.editor_explain_whites, "rawWhitePoint", glyph = EditorGlyph.Whites, rawOnly = true),
    Blacks(EditorSection.Light, SettingKind.RawTone, R.string.editor_blacks, R.string.editor_explain_blacks, "rawBlackPoint", glyph = EditorGlyph.Blacks, rawOnly = true),
    Tonality(EditorSection.Light, SettingKind.Recipe, R.string.recipe_palette_tone, R.string.editor_explain_tonality, "recipe.TONALITY", RecipeParam.TONALITY, EditorGlyph.Tonality),

    Temp(EditorSection.Color, SettingKind.Recipe, R.string.recipe_param_temperature, R.string.editor_explain_temp, "recipe.TEMPERATURE", RecipeParam.TEMPERATURE, EditorGlyph.Temp),
    Tint(EditorSection.Color, SettingKind.Recipe, R.string.recipe_param_tint, R.string.editor_explain_tint, "recipe.TINT", RecipeParam.TINT, EditorGlyph.Tint),
    Saturation(EditorSection.Color, SettingKind.Recipe, R.string.recipe_param_saturation, R.string.editor_explain_saturation, "recipe.SATURATION", RecipeParam.SATURATION, EditorGlyph.Saturation),
    Vibrance(EditorSection.Color, SettingKind.Recipe, R.string.recipe_param_color, R.string.editor_explain_vibrance, "recipe.COLOR", RecipeParam.COLOR, EditorGlyph.Vibrance),

    Grain(EditorSection.Effects, SettingKind.Recipe, R.string.recipe_param_film_grain, R.string.editor_explain_grain, "recipe.FILM_GRAIN", RecipeParam.FILM_GRAIN, EditorGlyph.Grain),
    Vignette(EditorSection.Effects, SettingKind.Recipe, R.string.recipe_param_vignette, R.string.editor_explain_vignette, "recipe.VIGNETTE", RecipeParam.VIGNETTE, EditorGlyph.Vignette),
    Fade(EditorSection.Effects, SettingKind.Recipe, R.string.recipe_param_fade, R.string.editor_explain_fade, "recipe.FADE", RecipeParam.FADE, EditorGlyph.Fade),
    Bloom(EditorSection.Effects, SettingKind.Recipe, R.string.recipe_param_bloom, R.string.editor_explain_bloom, "recipe.BLOOM", RecipeParam.BLOOM, EditorGlyph.Bloom),
    Halation(EditorSection.Effects, SettingKind.Recipe, R.string.recipe_param_halation, R.string.editor_explain_halation, "recipe.HALATION", RecipeParam.HALATION, EditorGlyph.Halation),
    SoftLight(EditorSection.Effects, SettingKind.Recipe, R.string.editor_soft_light, R.string.editor_explain_soft_light, "recipe.SOFT_LIGHT", RecipeParam.SOFT_LIGHT, EditorGlyph.SoftLight),
    Flash(EditorSection.Effects, SettingKind.Recipe, R.string.recipe_param_flash, R.string.editor_explain_flash, "recipe.FLASH", RecipeParam.FLASH, EditorGlyph.Flash),
    Bleach(EditorSection.Effects, SettingKind.Recipe, R.string.editor_bleach, R.string.editor_explain_bleach, "recipe.BLEACH_BYPASS", RecipeParam.BLEACH_BYPASS, EditorGlyph.Bleach),
    Fringe(EditorSection.Effects, SettingKind.Recipe, R.string.editor_fringe, R.string.editor_explain_fringe, "recipe.CHROMATIC_ABERRATION", RecipeParam.CHROMATIC_ABERRATION, EditorGlyph.Fringe),
    Noise(EditorSection.Effects, SettingKind.Recipe, R.string.editor_noise, R.string.editor_explain_noise, "recipe.NOISE", RecipeParam.NOISE, EditorGlyph.Noise),

    Sharpness(EditorSection.Detail, SettingKind.Recipe, R.string.recipe_param_sharpness, R.string.editor_explain_sharpness, "recipe.SHARPNESS", RecipeParam.SHARPNESS, EditorGlyph.Sharpness),
    Clarity(EditorSection.Detail, SettingKind.Recipe, R.string.recipe_param_clarity, R.string.editor_explain_clarity, "recipe.CLARITY", RecipeParam.CLARITY, EditorGlyph.Clarity),
    NoiseReduction(EditorSection.Detail, SettingKind.Denoise, R.string.editor_noise_reduction, R.string.settings_noise_reduction_description, "noiseReduction", glyph = EditorGlyph.NoiseReduction),
    ColorNoise(EditorSection.Detail, SettingKind.Denoise, R.string.editor_color_noise, R.string.settings_chroma_noise_reduction_description, "chromaNoiseReduction", glyph = EditorGlyph.ColorNoise),
    AiDenoise(EditorSection.Detail, SettingKind.AiDenoise, R.string.editor_ai_denoise, R.string.editor_explain_ai_denoise, "aiDenoise", glyph = EditorGlyph.AiDenoise);

    val icon: ImageVector
        get() = when (this) {
            Exposure, RawExposure -> AppIcons.ExposureLine
            Temp -> AppIcons.TemperatureLine
            Tint -> AppIcons.DropletHalfLine
            Saturation -> AppIcons.DropletLine
            Vibrance -> AppIcons.SwatchLine
            Grain -> AppIcons.GrainLine
            Bloom -> AppIcons.SunHighLine
            Halation -> AppIcons.HalationLine
            SoftLight -> AppIcons.BlurLine
            Flash -> AppIcons.BoltLine
            Bleach -> AppIcons.DropletOffLine
            Fringe -> AppIcons.RainbowLine
            Noise -> AppIcons.TextureLine
            Clarity -> AppIcons.ClarityLine
            NoiseReduction -> AppIcons.WandLine
            ColorNoise -> AppIcons.ColorFilterLine
            AiDenoise -> AppIcons.SparklesLine
            else -> AppIcons.AdjustmentsLine  // drawn settings use their glyph instead
        }

    fun shownFor(isRaw: Boolean, isVideo: Boolean): Boolean =
        !(rawOnly && !isRaw) && !(jpegOnly && isRaw) && !(this == AiDenoise && isVideo)

    /** The ruler's stops, as stored values. */
    val steps: List<Float>
        get() = when {
            param == RecipeParam.EXPOSURE -> exposureSteps
            param != null -> {
                val range = param.displayValueRange()
                (range.start.roundToInt()..range.endInclusive.roundToInt()).map { param.fromDisplayValue(it.toFloat()) }
            }
            this == RawExposure -> (-40..40).map { it / 10f }
            kind == SettingKind.RawTone -> (-10..10).map { it / 10f }
            kind == SettingKind.Denoise -> (0..20).map { it / 10f }
            else -> emptyList()
        }

    /** Every how many steps the ruler draws a label, when its own spacing rule doesn't fit the setting. */
    val labelEvery: Int?
        get() = if (param == RecipeParam.EXPOSURE) 3 else null

    /** The value in the units the user sees: EV for exposure, the app's −10..10 or 0..10 scale otherwise. */
    fun display(value: Float): Float = when {
        param != null -> param.toDisplayValue(value)
        this == RawExposure || kind == SettingKind.Denoise -> value
        else -> value * 10f
    }

    /** The value as the value row shows it: "+0.3", "−4", "7", "0.5". */
    fun format(value: Float): String {
        val d = display(value)
        return when {
            param == RecipeParam.EXPOSURE || this == RawExposure -> signed(d, decimals = 1)
            kind == SettingKind.Denoise -> String.format(Locale.ROOT, "%.1f", d)
            else -> {
                val i = d.roundToInt()
                if (signedScale) signed(i.toFloat(), decimals = 0) else "$i"
            }
        }
    }

    /** A tick label on the ruler: like [format], without the plus sign or a trailing ".0". */
    fun tickLabel(value: Float): String = format(value).removePrefix("+").removeSuffix(".0")

    /** The value as a fraction of the far end of its range on its side, for the drawn icons (−1 to 1). */
    fun fraction(value: Float): Float {
        if (kind == SettingKind.AiDenoise) return value
        val d = display(value)
        val s = steps.map(::display)
        val lo = s.minOrNull() ?: 0f
        val hi = s.maxOrNull() ?: 0f
        return when {
            d > 0f && hi > 0f -> d / hi
            d < 0f && lo < 0f -> -d / lo
            else -> 0f
        }.coerceIn(-1f, 1f)
    }

    private val signedScale: Boolean
        get() = steps.firstOrNull()?.let(::display)?.let { it < 0f } == true

    companion object {
        fun forSection(section: EditorSection, isRaw: Boolean, isVideo: Boolean): List<EditorSetting> =
            entries.filter { it.section == section && it.shownFor(isRaw, isVideo) }
    }
}

// "−" (U+2212) rather than a hyphen, as the camera's own values show.
private fun signed(value: Float, decimals: Int): String {
    val text = String.format(Locale.ROOT, "%.${decimals}f", abs(value))
    val zero = text.all { it == '0' || it == '.' }
    return when {
        zero -> text
        value < 0f -> "−$text"
        else -> "+$text"
    }
}

/**
 * The groups: a setting that needs its own picker (a channel, a colour, a range) gets a level of its own.
 * [keys] are the change keys that put a dot on the group.
 */
internal enum class EditorGroup(
    val section: EditorSection,
    @StringRes val label: Int,
    val keys: Set<String>,
    val rawOnly: Boolean = false,
) {
    Curve(EditorSection.Light, R.string.editor_group_curve, setOf("recipe.curves")),
    Raw(
        EditorSection.Light, R.string.editor_group_raw,
        setOf(
            "rawLensShading", "rawDro", "rawBlackLevel", "rawWhiteLevel", "rawCfa", "rawDcp", "rawEmbeddedProfile",
            "rawHncs", "rawBaseLook", "rawEngine", "rawToneMapping", "rawSpectralFilm", "rawFotufilm",
        ),
        rawOnly = true
    ),
    Mixer(
        EditorSection.Color, R.string.editor_group_mixer,
        listOf("SKIN", "RED", "ORANGE", "YELLOW", "GREEN", "CYAN", "BLUE", "PURPLE", "MAGENTA")
            .flatMap { c -> listOf("HUE", "CHROMA", "LIGHTNESS").map { "recipe.${c}_$it" } }
            .toSet()
    ),
    Grading(EditorSection.Color, R.string.editor_group_grading, setOf("recipe.grading")),
    Calibration(
        EditorSection.Color, R.string.editor_group_calibration,
        listOf("RED", "GREEN", "BLUE")
            .flatMap { c -> listOf("HUE", "SATURATION", "LIGHTNESS").map { "recipe.PRIMARY_${c}_$it" } }
            .toSet()
    );

    val icon: ImageVector
        get() = when (this) {
            Curve -> AppIcons.ChartLine
            Raw -> AppIcons.PhotoSensorLine
            Mixer -> AppIcons.PaletteLine
            Grading -> AppIcons.CircleHalfLine
            Calibration -> AppIcons.SlidersLine
        }

    companion object {
        fun forSection(section: EditorSection, isRaw: Boolean): List<EditorGroup> =
            entries.filter { it.section == section && (!it.rawOnly || isRaw) }
    }
}
