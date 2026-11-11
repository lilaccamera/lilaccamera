package com.hinnka.mycamera.ui.gallery

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hinnka.mycamera.raw.fotufilm.FotufilmCatalogue
import com.hinnka.mycamera.raw.fotufilm.FotufilmDocument
import com.hinnka.mycamera.raw.fotufilm.FotufilmRuntime
import com.hinnka.mycamera.ui.components.FotufilmStrings
import com.hinnka.mycamera.ui.components.SHOWN_LIGHT_FIELDS
import com.hinnka.mycamera.ui.components.SHOWN_SECTIONS
import com.hinnka.mycamera.ui.components.documentForStock
import com.hinnka.mycamera.ui.components.withNumber
import com.hinnka.mycamera.ui.components.withValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.lut.LutInfo
import com.hinnka.mycamera.raw.CanonPictureStyle
import com.hinnka.mycamera.raw.DcpInfo
import com.hinnka.mycamera.raw.HncsFilmCurveMode
import com.hinnka.mycamera.raw.LumixPhotoStyle
import com.hinnka.mycamera.raw.RawAdaptiveExposureMode
import com.hinnka.mycamera.raw.RawCfaCorrection
import com.hinnka.mycamera.raw.RawProfileToneMapMode
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.raw.RawToneMappingParameters
import com.hinnka.mycamera.raw.RawWhiteLevelCorrection
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.raw.SpectralFilmTuning
import com.hinnka.mycamera.raw.SpectralFilmUiInfo
import com.hinnka.mycamera.ui.components.RawEmbeddedDngProfileOption
import com.hinnka.mycamera.ui.components.rawColorEngineDescription
import com.hinnka.mycamera.ui.components.rawRenderingEngineName
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import java.util.Locale
import kotlin.math.roundToInt

/** A RAW value's ruler: it only sets the edit state; the photo is saved and re-rendered once, on release. */
internal class RawRuler(
    val steps: List<Float>,
    val value: Float,
    val start: Float,
    val label: (Float) -> String,
    val onChange: (Float) -> Unit,
    val labelEvery: Int? = null,
    /** In place of the usual save and re-render on release. */
    val onRelease: (() -> Unit)? = null,
)

/**
 * One value in Light › RAW or its Sensor group: chips ([choices], then grey [actions]) or a [ruler]. Each change
 * is written to the photo and re-rendered from the RAW file, as RAW edits always have been.
 */
internal class RawItem(
    val key: String,
    val name: String,
    val value: String,
    val icon: ImageVector,
    val changed: Boolean,
    val explanation: String,
    /** Back to the value the photo had when the editor opened. */
    val reset: () -> Unit,
    val choices: List<EditorChoice> = emptyList(),
    val actions: List<EditorChoice> = emptyList(),
    val ruler: RawRuler? = null,
)

/** A level of Light › RAW: its items, and the Fotufilm sections the film has controls in (changed or not). */
internal class RawLevel(val items: List<RawItem>, val filmSections: Map<String, Boolean>)

private fun evText(v: Float) = trimmed(v) + " EV"

private fun trimmed(v: Float) = String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')

/** Ruler steps from [from] to [to] by [step], with the opening value added when it falls between. */
private fun steps(from: Float, to: Float, step: Float): List<Float> {
    val n = ((to - from) / step).roundToInt()
    return (0..n).map { ((from + it * step) * 1000f).roundToInt() / 1000f }
}

@Composable
internal fun rawGroupItems(
    viewModel: GalleryViewModel,
    photo: MediaData,
    level: EditorGroup,
    changedKeys: Set<String>,
    imported: Boolean,
    embeddedProfiles: List<RawEmbeddedDngProfileOption>,
    activeEmbeddedProfileId: String?,
    availableLuts: List<LutInfo>,
    commit: () -> Unit,
    refresh: () -> Unit,
    reRender: () -> Unit,
    importDcp: () -> Unit,
    deleteDcp: (DcpInfo) -> Unit,
): RawLevel {
    val sensor = level == EditorGroup.Sensor
    val onText = stringResource(R.string.editor_on)
    val offText = stringResource(R.string.editor_off)
    val noneText = stringResource(R.string.none)
    val done: (Boolean) -> Unit = { refresh() }

    val film = fotufilmItems(viewModel, level, commit)
    if (level.filmSection != null) return RawLevel(film.items, film.filmSections)

    if (sensor) {
        val blackMode by viewModel.editRawBlackLevelMode.collectAsState()
        val blackCustom by viewModel.editRawCustomBlackLevel.collectAsState()
        val whiteMode by viewModel.editRawWhiteLevelMode.collectAsState()
        val whiteCustom by viewModel.editRawCustomWhiteLevel.collectAsState()
        val cfa by viewModel.editRawCfaCorrectionMode.collectAsState()
        val openBlack = viewModel.editOpenValue("rawBlackLevel") as? Pair<*, *>
        val openWhite = viewModel.editOpenValue("rawWhiteLevel") as? Pair<*, *>
        val customText = stringResource(R.string.settings_black_level_custom)

        // A custom level is typed in Settings › RAW; here it shows as a chip once it is the photo's.
        val blackOptions = listOf(
            RawCfaCorrection.MODE_DEFAULT to stringResource(R.string.settings_black_level_default),
            "0" to "0", "16" to "16", "64" to "64", "256" to "256", "512" to "512",
        ) + if (blackMode == "Custom" || openBlack?.first == "Custom") {
            listOf("Custom" to "$customText ${trimmed(if (blackMode == "Custom") blackCustom else openBlack?.second as? Float ?: 0f)}")
        } else emptyList()
        val whiteOptions = listOf(
            RawWhiteLevelCorrection.MODE_DEFAULT to stringResource(R.string.settings_white_level_default),
            RawWhiteLevelCorrection.MODE_RAW10 to stringResource(R.string.settings_white_level_raw10),
            RawWhiteLevelCorrection.MODE_RAW12 to stringResource(R.string.settings_white_level_raw12),
            RawWhiteLevelCorrection.MODE_RAW14 to stringResource(R.string.settings_white_level_raw14),
            RawWhiteLevelCorrection.MODE_RAW_SENSOR to stringResource(R.string.settings_white_level_raw_sensor),
        ) + if (whiteMode == RawWhiteLevelCorrection.MODE_CUSTOM || openWhite?.first == RawWhiteLevelCorrection.MODE_CUSTOM) {
            val v = if (whiteMode == RawWhiteLevelCorrection.MODE_CUSTOM) whiteCustom else openWhite?.second as? Float ?: 0f
            listOf(RawWhiteLevelCorrection.MODE_CUSTOM to "${stringResource(R.string.settings_white_level_custom)} ${trimmed(v)}")
        } else emptyList()
        val cfaOptions = listOf(
            RawCfaCorrection.MODE_DEFAULT to R.string.settings_cfa_correction_default,
            RawCfaCorrection.MODE_2X2_RGGB to R.string.settings_cfa_correction_2x2_rggb,
            RawCfaCorrection.MODE_2X2_GRBG to R.string.settings_cfa_correction_2x2_grbg,
            RawCfaCorrection.MODE_2X2_GBRG to R.string.settings_cfa_correction_2x2_gbrg,
            RawCfaCorrection.MODE_2X2_BGGR to R.string.settings_cfa_correction_2x2_bggr,
            RawCfaCorrection.MODE_4X4_RGGB to R.string.settings_cfa_correction_4x4_rggb,
            RawCfaCorrection.MODE_4X4_GRBG to R.string.settings_cfa_correction_4x4_grbg,
            RawCfaCorrection.MODE_4X4_GBRG to R.string.settings_cfa_correction_4x4_gbrg,
            RawCfaCorrection.MODE_4X4_BGGR to R.string.settings_cfa_correction_4x4_bggr,
            RawCfaCorrection.MODE_8X8_RGGB to R.string.settings_cfa_correction_8x8_rggb,
            RawCfaCorrection.MODE_8X8_GRBG to R.string.settings_cfa_correction_8x8_grbg,
            RawCfaCorrection.MODE_8X8_GBRG to R.string.settings_cfa_correction_8x8_gbrg,
            RawCfaCorrection.MODE_8X8_BGGR to R.string.settings_cfa_correction_8x8_bggr,
        ).map { (mode, label) -> mode to stringResource(label) }

        fun chips(options: List<Pair<String, String>>, current: String, pick: (String) -> Unit) =
            options.map { (mode, label) -> EditorChoice(label, null, mode == current) { pick(mode) } }

        return RawLevel(listOf(
            RawItem(
                "blackLevel", stringResource(R.string.editor_black_level),
                blackOptions.firstOrNull { it.first == blackMode }?.second ?: blackMode, AppIcons.MoonStars,
                "rawBlackLevel" in changedKeys, stringResource(R.string.settings_raw_black_level_correction_description),
                reset = { (openBlack?.first as? String)?.let { viewModel.saveRawBlackLevelMode(photo, it, done) } },
                choices = chips(blackOptions, blackMode) { viewModel.saveRawBlackLevelMode(photo, it, done) },
            ),
            RawItem(
                "whiteLevel", stringResource(R.string.editor_white_level),
                whiteOptions.firstOrNull { it.first == whiteMode }?.second ?: whiteMode, AppIcons.SunHighLine,
                "rawWhiteLevel" in changedKeys, stringResource(R.string.settings_raw_white_level_correction_description),
                reset = { (openWhite?.first as? String)?.let { viewModel.saveRawWhiteLevelMode(photo, it, done) } },
                choices = chips(whiteOptions, whiteMode) { viewModel.saveRawWhiteLevelMode(photo, it, done) },
            ),
            RawItem(
                "cfa", stringResource(R.string.editor_cfa),
                cfaOptions.firstOrNull { it.first == cfa }?.second ?: cfa, AppIcons.GridView,
                "rawCfa" in changedKeys, stringResource(R.string.settings_raw_cfa_correction_description),
                reset = { (viewModel.editOpenValue("rawCfa") as? String)?.let { viewModel.saveRawCfaCorrectionMode(photo, it, done) } },
                choices = chips(cfaOptions, cfa) { viewModel.saveRawCfaCorrectionMode(photo, it, done) },
            ),
        ), film.filmSections)
    }

    val engine by viewModel.editRawRenderingEngine.collectAsState()
    val tone by viewModel.editRawToneMappingParameters.collectAsState()
    val dcpId by viewModel.editRawDcpId.collectAsState()
    val hncsCurve by viewModel.editRawHncsFilmCurveMode.collectAsState()
    val lensShading by viewModel.editRawLensShadingCorrectionEnabled.collectAsState()
    val baseLook by viewModel.editRawBaselineLutId.collectAsState()
    val spectralFilm by viewModel.editRawSpectralFilmStock.collectAsState()
    val print by viewModel.editRawSpectralFilmPrint.collectAsState()
    val cDensity by viewModel.editRawSpectralFilmCDensityGain.collectAsState()
    val mDensity by viewModel.editRawSpectralFilmMDensityGain.collectAsState()
    val yDensity by viewModel.editRawSpectralFilmYDensityGain.collectAsState()
    val openTone = viewModel.editOpenValue("rawToneMapping") as? RawToneMappingParameters ?: tone

    fun saveTone(t: RawToneMappingParameters) = viewModel.saveRawToneMappingParameters(photo, t, done)

    // A choice stored in the tone-mapping parameters.
    fun <T> toneChoice(
        key: String, name: String, icon: ImageVector, explanation: String,
        options: List<Pair<T, String>>, get: (RawToneMappingParameters) -> T,
        set: (RawToneMappingParameters, T) -> RawToneMappingParameters,
    ) = RawItem(
        key, name, options.firstOrNull { it.first == get(tone) }?.second.orEmpty(), icon,
        get(tone) != get(openTone), explanation,
        reset = { saveTone(set(tone, get(openTone))) },
        choices = options.map { (v, label) -> EditorChoice(label, null, v == get(tone)) { saveTone(set(tone, v)) } },
    )

    // A number stored in the tone-mapping parameters, on a ruler.
    fun toneRuler(
        key: String, name: String, icon: ImageVector, explanation: String, steps: List<Float>,
        get: (RawToneMappingParameters) -> Float, set: (RawToneMappingParameters, Float) -> RawToneMappingParameters,
        text: (Float) -> String, tick: (Float) -> String = text, labelEvery: Int? = null,
    ) = RawItem(
        key, name, text(get(tone)), icon, get(tone) != get(openTone), explanation,
        reset = {
            viewModel.editRawToneMappingParameters.value = set(viewModel.editRawToneMappingParameters.value, get(openTone)).normalized()
            commit()
        },
        ruler = RawRuler(
            steps = get(openTone).let { s -> if (s in steps) steps else (steps + s).sorted() },
            value = get(tone),
            start = get(openTone),
            label = tick,
            onChange = { v ->
                viewModel.editRawToneMappingParameters.value = set(viewModel.editRawToneMappingParameters.value, v).normalized()
            },
            labelEvery = labelEvery,
        ),
    )

    val items = mutableListOf<RawItem>()
    val engineNames = RawRenderingEngine.entries.associateWith { rawRenderingEngineName(it) }
    val openEngine = viewModel.editOpenValue("rawEngine") as? RawRenderingEngine
    items += RawItem(
        "engine", stringResource(R.string.editor_engine), engineNames.getValue(engine), AppIcons.Tune,
        "rawEngine" in changedKeys, rawColorEngineDescription(engine),
        reset = { openEngine?.let { viewModel.saveRawColorEngine(photo, it, done) } },
        choices = RawRenderingEngine.entries.map { e ->
            EditorChoice(engineNames.getValue(e), null, e == engine) { viewModel.saveRawColorEngine(photo, e, done) }
        },
    )

    val deviceText = stringResource(R.string.settings_raw_canon_color_matrix_device)
    val matrixText = stringResource(R.string.editor_matrix)
    val styleText = stringResource(R.string.editor_style)
    val colorCorrectionText = stringResource(R.string.settings_raw_camera_color_matching)
    val colorCorrectionExplain = stringResource(R.string.settings_raw_camera_color_matching_description)
    val onOff = listOf(false to offText, true to onText)

    when (engine) {
        RawRenderingEngine.AdobeCurve -> {
            val dcps = viewModel.availableDcps
            val selectedDcp = dcps.firstOrNull { it.id == dcpId }
            val cameraDefault = stringResource(R.string.editor_camera_default)
            val profileName = selectedDcp?.getName()
                ?: embeddedProfiles.firstOrNull { it.id == activeEmbeddedProfileId }?.name
                ?: cameraDefault
            val openDcp = viewModel.editOpenValue("rawDcp") as? String
            val openEmbedded = viewModel.editOpenValue("rawEmbeddedProfile") as? String
            fun chooseEmbedded(p: RawEmbeddedDngProfileOption) {
                viewModel.selectRawEmbeddedDngProfileForEdit(p.id, p.hasProfileGainTableMap)
                commit()
            }
            items += RawItem(
                "profile", stringResource(R.string.editor_profile), profileName, AppIcons.ColorFilterLine,
                "rawDcp" in changedKeys || "rawEmbeddedProfile" in changedKeys,
                stringResource(R.string.editor_explain_raw_profile),
                reset = {
                    val embedded = embeddedProfiles.firstOrNull { it.id == openEmbedded }
                    if (openDcp == null && embedded != null) chooseEmbedded(embedded)
                    else viewModel.saveRawDcpSelection(photo, openDcp, done)
                },
                // The profiles in the file stand for the camera's own; without any, "Camera default" does.
                choices = (if (embeddedProfiles.isEmpty()) {
                    listOf(EditorChoice(cameraDefault, null, dcpId == null) { viewModel.saveRawDcpSelection(photo, null, done) })
                } else {
                    embeddedProfiles.map { p ->
                        EditorChoice(p.name, null, dcpId == null && p.id == activeEmbeddedProfileId) { chooseEmbedded(p) }
                    }
                }) + dcps.map { dcp ->
                    EditorChoice(
                        dcp.getName(), null, dcp.id == dcpId,
                        onLongClick = if (dcp.isBuiltIn) null else ({ deleteDcp(dcp) }),
                    ) { viewModel.saveRawDcpSelection(photo, dcp.id, done) }
                },
                actions = listOf(EditorChoice(stringResource(R.string.editor_import_dcp), AppIcons.DownloadLine, false) { importDcp() }),
            )
            items += toneChoice(
                "toneCurve", stringResource(R.string.editor_tone_curve), AppIcons.ChartLine,
                stringResource(R.string.settings_raw_profile_tone_map_description),
                listOf(
                    RawProfileToneMapMode.Default to stringResource(R.string.settings_raw_profile_tone_map_default),
                    RawProfileToneMapMode.Profile to stringResource(R.string.editor_profile),
                    RawProfileToneMapMode.OppoMaster to stringResource(R.string.settings_raw_profile_tone_map_oppo_master),
                ),
                { it.profileToneMapMode }, { t, v -> t.withProfileToneMapMode(v) },
            )
        }
        RawRenderingEngine.Hncs -> {
            val openCurve = (viewModel.editOpenValue("rawHncs") as? List<*>)?.getOrNull(2) as? HncsFilmCurveMode
            val curves = listOf(
                HncsFilmCurveMode.Standard to stringResource(R.string.settings_raw_hncs_film_curve_standard),
                HncsFilmCurveMode.Reproduction to stringResource(R.string.settings_raw_hncs_film_curve_reproduction),
            )
            items += RawItem(
                "hncsCurve", stringResource(R.string.editor_tone_curve), curves.first { it.first == hncsCurve }.second,
                AppIcons.ChartLine, openCurve != null && hncsCurve != openCurve,
                stringResource(R.string.settings_raw_hncs_film_curve_description),
                reset = { openCurve?.let { viewModel.saveRawHncsFilmCurveMode(photo, it, done) } },
                choices = curves.map { (mode, label) ->
                    EditorChoice(label, null, mode == hncsCurve) { viewModel.saveRawHncsFilmCurveMode(photo, mode, done) }
                },
            )
            items += toneChoice(
                "hncsColor", stringResource(R.string.settings_raw_camera_color_matching), AppIcons.SwatchLine, colorCorrectionExplain, onOff,
                { it.hncsColorMatchingEnabled }, { t, v -> t.copy(hncsColorMatchingEnabled = v) },
            )
        }
        RawRenderingEngine.Lumix -> {
            items += toneChoice(
                "lumixStyle", stringResource(R.string.editor_style), AppIcons.PaletteLine,
                stringResource(R.string.settings_raw_lumix_photo_style_description),
                LumixPhotoStyle.entries.map { it to stringResource(lumixStyleLabel(it)) },
                { it.lumixPhotoStyle }, { t, v -> t.copy(lumixPhotoStyle = v) },
            )
            items += toneChoice(
                "lumixColor", stringResource(R.string.settings_raw_camera_color_matching), AppIcons.SwatchLine, colorCorrectionExplain, onOff,
                { it.lumixColorMatchingEnabled }, { t, v -> t.copy(lumixColorMatchingEnabled = v) },
            )
        }
        RawRenderingEngine.Canon -> {
            items += toneChoice(
                "canonStyle", stringResource(R.string.editor_style), AppIcons.PaletteLine,
                stringResource(R.string.settings_raw_canon_picture_style_description),
                CanonPictureStyle.entries.map { it to stringResource(canonStyleLabel(it)) },
                { it.canonPictureStyle }, { t, v -> t.copy(canonPictureStyle = v) },
            )
            items += toneRuler(
                "canonEv", "EV", AppIcons.ExposureLine,
                stringResource(R.string.settings_raw_canon_exposure_compensation_description),
                steps(RawToneMappingParameters.CANON_EXPOSURE_COMPENSATION_MIN, RawToneMappingParameters.CANON_EXPOSURE_COMPENSATION_MAX, 0.1f),
                { it.canonExposureCompensationEv }, { t, v -> t.copy(canonExposureCompensationEv = v) },
                text = { (if (it > 0f) "+" else if (it < 0f) "−" else "") + String.format(Locale.ROOT, "%.1f", kotlin.math.abs(it)) },
                tick = { it.roundToInt().toString() }, labelEvery = 10,
            )
            items += toneChoice(
                "canonMatrix", stringResource(R.string.editor_matrix), AppIcons.GridView,
                stringResource(R.string.settings_raw_canon_color_matrix_description),
                listOf(false to deviceText, true to stringResource(R.string.settings_raw_canon_color_matrix_r5)),
                { it.canonUseR5ColorMatrix }, { t, v -> t.copy(canonUseR5ColorMatrix = v) },
            )
        }
        RawRenderingEngine.Leica -> {
            items += toneChoice(
                "leicaMatrix", stringResource(R.string.editor_matrix), AppIcons.GridView,
                stringResource(R.string.settings_raw_leica_color_matrix_description),
                listOf(
                    false to stringResource(R.string.settings_raw_leica_color_matrix_device),
                    true to stringResource(R.string.settings_raw_leica_color_matrix_m9),
                ),
                { it.leicaUseM9ColorMatrix }, { t, v -> t.copy(leicaUseM9ColorMatrix = v) },
            )
        }
        RawRenderingEngine.AgX -> {
            items += toneRuler(
                "agxWhite", stringResource(R.string.editor_white), AppIcons.SunLine, stringResource(R.string.editor_explain_agx_white),
                steps(0.25f, RawToneMappingParameters.AGX_WHITE_RELATIVE_EXPOSURE_MAX, 0.25f),
                { it.agxWhiteRelativeExposure }, { t, v -> t.copy(agxWhiteRelativeExposure = v) }, ::evText, ::trimmed,
            )
            items += toneRuler(
                "agxBlack", stringResource(R.string.editor_black), AppIcons.MoonStars, stringResource(R.string.editor_explain_agx_black),
                steps(RawToneMappingParameters.AGX_BLACK_RELATIVE_EXPOSURE_MIN, -0.25f, 0.25f),
                { it.agxBlackRelativeExposure }, { t, v -> t.copy(agxBlackRelativeExposure = v) }, ::evText, ::trimmed,
            )
            items += toneRuler(
                "agxShoulder", stringResource(R.string.editor_shoulder), AppIcons.ChartLine, stringResource(R.string.editor_explain_agx_shoulder),
                steps(0.1f, RawToneMappingParameters.AGX_SHOULDER_MAX, 0.1f),
                { it.agxShoulder }, { t, v -> t.copy(agxShoulder = v) }, ::trimmed,
            )
            items += toneRuler(
                "agxToe", stringResource(R.string.editor_toe), AppIcons.ChartLine, stringResource(R.string.editor_explain_agx_toe),
                steps(0.1f, RawToneMappingParameters.AGX_TOE_MAX, 0.1f),
                { it.agxToe }, { t, v -> t.copy(agxToe = v) }, ::trimmed,
            )
        }
        RawRenderingEngine.DarktableFilmic -> {
            items += toneRuler(
                "filmicWhite", stringResource(R.string.editor_white), AppIcons.SunLine, stringResource(R.string.editor_explain_agx_white),
                steps(0.25f, RawToneMappingParameters.FILMIC_WHITE_RELATIVE_EXPOSURE_MAX, 0.25f),
                { it.filmicWhiteRelativeExposure }, { t, v -> t.copy(filmicWhiteRelativeExposure = v) }, ::evText, ::trimmed,
            )
            items += toneRuler(
                "filmicBlack", stringResource(R.string.editor_black), AppIcons.MoonStars, stringResource(R.string.editor_explain_agx_black),
                steps(RawToneMappingParameters.FILMIC_BLACK_RELATIVE_EXPOSURE_MIN, -0.25f, 0.25f),
                { it.filmicBlackRelativeExposure }, { t, v -> t.copy(filmicBlackRelativeExposure = v) }, ::evText, ::trimmed,
            )
        }
        RawRenderingEngine.Spektrafilm -> {
            val openFilm = viewModel.editOpenValue("rawSpectralFilm") as? List<*>
            items += RawItem(
                "film", stringResource(R.string.editor_film), spectralFilm?.let(SpectralFilmUiInfo::getFilmDisplayName) ?: noneText,
                AppIcons.Film, spectralFilm != openFilm?.getOrNull(0), stringResource(R.string.editor_explain_film),
                reset = {
                    viewModel.saveRawSpectralFilmSelection(photo, (openFilm?.getOrNull(0) as? String)?.let(::SpectralFilmSelection), done)
                },
                choices = listOf(EditorChoice(noneText, null, spectralFilm == null) { viewModel.saveRawSpectralFilmSelection(photo, null, done) }) +
                    SpectralFilmUiInfo.availableFilms.map { id ->
                        EditorChoice(SpectralFilmUiInfo.getFilmDisplayName(id), null, id == spectralFilm) {
                            viewModel.saveRawSpectralFilmSelection(photo, SpectralFilmSelection(id), done)
                        }
                    },
            )
            // A slide film is its own print; only negatives take a paper and dye densities.
            if (spectralFilm != null && !SpectralFilmUiInfo.isPositiveFilm(spectralFilm)) {
                items += RawItem(
                    "print", stringResource(R.string.editor_print), print?.let(SpectralFilmUiInfo::getPrintDisplayName) ?: noneText,
                    AppIcons.Image, print != openFilm?.getOrNull(1), stringResource(R.string.editor_explain_print),
                    reset = { viewModel.saveRawSpectralFilmPrint(photo, openFilm?.getOrNull(1) as? String, done) },
                    choices = listOf(EditorChoice(noneText, null, print == null) { viewModel.saveRawSpectralFilmPrint(photo, null, done) }) +
                        SpectralFilmUiInfo.availablePrints.map { id ->
                            EditorChoice(SpectralFilmUiInfo.getPrintDisplayName(id), null, id == print) {
                                viewModel.saveRawSpectralFilmPrint(photo, id, done)
                            }
                        },
                )
                val densitySteps = steps(SpectralFilmTuning.MIN_DENSITY_GAIN, SpectralFilmTuning.MAX_DENSITY_GAIN, 0.05f)
                fun percent(v: Float) = "${(v * 100f).roundToInt()}%"
                listOf(
                    Triple("densityC", R.string.editor_cyan, Triple(cDensity, 2, viewModel.editRawSpectralFilmCDensityGain)),
                    Triple("densityM", R.string.editor_magenta, Triple(mDensity, 3, viewModel.editRawSpectralFilmMDensityGain)),
                    Triple("densityY", R.string.editor_yellow, Triple(yDensity, 4, viewModel.editRawSpectralFilmYDensityGain)),
                ).forEach { (key, name, rest) ->
                    val (value, openIndex, flow) = rest
                    val open = openFilm?.getOrNull(openIndex) as? Float ?: 1f
                    items += RawItem(
                        key, stringResource(name), percent(value), AppIcons.DropletLine, value != open,
                        stringResource(R.string.editor_explain_density),
                        reset = {
                            flow.value = open
                            commit()
                        },
                        ruler = RawRuler(
                            steps = if (open in densitySteps) densitySteps else (densitySteps + open).sorted(),
                            value = value, start = open, label = { "${(it * 100f).roundToInt()}" },
                            onChange = { flow.value = it }, labelEvery = 4,
                        ),
                    )
                }
            }
        }
        // Fotufilm: the film here, its sections as groups.
        RawRenderingEngine.Fotufilm -> items += film.items
        else -> Unit  // Sigmoid has no values of its own.
    }

    items += RawItem(
        "lensShading", stringResource(R.string.editor_lens_shading), if (lensShading) onText else offText,
        AppIcons.CircleHalfLine, "rawLensShading" in changedKeys,
        stringResource(R.string.settings_raw_lens_shading_correction_description),
        reset = {
            (viewModel.editOpenValue("rawLensShading") as? Boolean)?.let { viewModel.saveRawLensShadingCorrectionEnabled(photo, it, done) }
        },
        choices = onOff.map { (on, label) ->
            EditorChoice(label, null, on == lensShading) { viewModel.saveRawLensShadingCorrectionEnabled(photo, on, done) }
        },
    )

    // Photon HDR can be switched on an imported RAW only; Re-render rebuilds its tone map on any RAW.
    fun setHdr(on: Boolean) {
        viewModel.selectRawAdaptiveExposureModeForEdit(if (on) RawAdaptiveExposureMode.PHOTON_HDR else RawAdaptiveExposureMode.OFF)
        commit()
    }
    items += RawItem(
        "dynamicRange", stringResource(R.string.editor_dynamic_range), if (tone.usePhotonHdr) onText else offText,
        AppIcons.ContrastLine, tone.usePhotonHdr != openTone.usePhotonHdr,
        stringResource(R.string.editor_explain_dynamic_range),
        reset = { if (tone.usePhotonHdr != openTone.usePhotonHdr) setHdr(openTone.usePhotonHdr) },
        choices = if (imported) onOff.map { (on, label) -> EditorChoice(label, null, on == tone.usePhotonHdr) { setHdr(on) } } else emptyList(),
        actions = listOf(EditorChoice(stringResource(R.string.editor_rerender), AppIcons.RestartAlt, false) { reRender() }),
    )

    val openBase = viewModel.editOpenValue("rawBaseLook") as? String
    items += RawItem(
        "baseLook", stringResource(R.string.editor_base_look),
        availableLuts.firstOrNull { it.id == baseLook }?.getName() ?: noneText, AppIcons.FiltersLine,
        "rawBaseLook" in changedKeys, stringResource(R.string.editor_explain_base_look),
        reset = { viewModel.saveRawBaselineLutSelection(photo, openBase, done) },
        choices = listOf(EditorChoice(noneText, null, baseLook == null) { viewModel.saveRawBaselineLutSelection(photo, null, done) }) +
            availableLuts.map { lut ->
                EditorChoice(lut.getName(), null, lut.id == baseLook) { viewModel.saveRawBaselineLutSelection(photo, lut.id, done) }
            },
    )
    return RawLevel(items, film.filmSections)
}

/**
 * Fotufilm's controls, from its catalogue for the photo's film: the film itself on the RAW level, the rest in the
 * group of their catalogue section (the film's format and the camera preflash in Lab). Each change is saved and
 * re-rendered once; a ruler's value changes the document only on release.
 */
@Composable
private fun fotufilmItems(viewModel: GalleryViewModel, level: EditorGroup, commit: () -> Unit): RawLevel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by viewModel.editRawRenderingEngine.collectAsState()
    val document by viewModel.editRawFotufilmDocument.collectAsState()
    val doc = document?.takeIf { engine == RawRenderingEngine.Fotufilm } ?: return RawLevel(emptyList(), emptyMap())
    val catalogue by produceState<FotufilmCatalogue?>(null, doc) {
        value = withContext(Dispatchers.Default) { runCatching { FotufilmRuntime.catalogue(context, doc) }.getOrNull() } ?: value
    }
    val current = catalogue ?: return RawLevel(emptyList(), emptyMap())
    var drag by remember { mutableStateOf<Pair<String, Float>?>(null) }
    val open = viewModel.editOpenValue("rawFotufilm") as? FotufilmDocument
    val onText = stringResource(R.string.editor_on)
    val offText = stringResource(R.string.editor_off)

    fun setDoc(next: FotufilmDocument) {
        viewModel.editRawFotufilmDocument.value = next
        commit()
    }
    fun changeStock(id: String) {
        scope.launch {
            withContext(Dispatchers.Default) { documentForStock(context, doc, id) }?.let(::setDoc)
        }
    }
    fun sectionOf(c: FotufilmCatalogue.Control): String? = when {
        c.field == FotufilmDocument.STOCK_FIELD -> null
        c.section == "filmStock" || c.field in SHOWN_LIGHT_FIELDS -> "filmLab"
        else -> c.section
    }

    val visible = current.controls.filter { c ->
        c.available && c.kind !is FotufilmCatalogue.Kind.Other &&
            (c.section in SHOWN_SECTIONS || c.field in SHOWN_LIGHT_FIELDS) &&
            (c.foldsUnder == null || current.control(c.foldsUnder)?.moved == true)
    }
    fun changed(c: FotufilmCatalogue.Control): Boolean =
        if (c.field == FotufilmDocument.STOCK_FIELD) doc.stockId != open?.stockId
        else doc.values[c.field] != open?.values?.get(c.field)

    val sections = visible.mapNotNull { c -> sectionOf(c)?.let { it to changed(c) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, flags) -> flags.any { it } }
    val wanted = level.filmSection
    val items = visible.filter { sectionOf(it) == wanted }.map { c ->
        val name = FotufilmStrings.title(c.field)
        val explanation = FotufilmStrings.description(c.field) ?: name
        val openValue = open?.values?.get(c.field)
        val reset: () -> Unit = {
            if (c.field == FotufilmDocument.STOCK_FIELD) {
                open?.stockId?.let(::changeStock)
            } else {
                setDoc(if (openValue == null) doc.without(c.field) else doc.with(c.field, openValue))
            }
        }
        when (val kind = c.kind) {
            is FotufilmCatalogue.Kind.Slider -> {
                val scale = kind.scale
                val stored = ((c.value as? FotufilmDocument.Value.Number)?.value ?: scale.neutral).toFloat()
                val start = ((openValue as? FotufilmDocument.Value.Number)?.value ?: scale.neutral).toFloat()
                val value = drag?.takeIf { it.first == c.field }?.second ?: stored
                // The track's own detents, else about fifty steps across it; the resting and stored values stay on it.
                val steps = (if (scale.stops.isNotEmpty()) scale.stops.map { it.toFloat() }
                else (0..50).map { (scale.min + (scale.max - scale.min) * it / 50.0).toFloat() })
                    .plus(listOf(scale.neutral.toFloat(), stored, start)).distinct().sorted()
                fun text(v: Float) = FotufilmStrings.formatValue(context, scale, v)
                RawItem(
                    "film:${c.field}", name, text(value), AppIcons.AdjustmentsLine, changed(c), explanation, reset,
                    ruler = RawRuler(
                        steps = steps, value = value, start = start, label = ::text,
                        onChange = { drag = c.field to it },
                        onRelease = {
                            drag?.let { (_, v) -> setDoc(doc.withNumber(c, v.toDouble())) }
                            drag = null
                        },
                    ),
                )
            }
            is FotufilmCatalogue.Kind.Chips -> {
                val stored = (c.value as? FotufilmDocument.Value.Number)?.value ?: kind.scale.neutral
                val selected = kind.chips.minByOrNull { abs(it.value - stored) }
                RawItem(
                    "film:${c.field}", name, selected?.let { FotufilmStrings.chip(c.field, it) }.orEmpty(),
                    AppIcons.AdjustmentsLine, changed(c), explanation, reset,
                    choices = kind.chips.map { chip ->
                        EditorChoice(FotufilmStrings.chip(c.field, chip), null, chip == selected) {
                            setDoc(doc.withValue(c, if (chip.value == kind.scale.neutral) null else FotufilmDocument.Value.Number(chip.value)))
                        }
                    },
                )
            }
            is FotufilmCatalogue.Kind.Toggle -> {
                val on = (c.value as? FotufilmDocument.Value.Flag)?.value ?: kind.restingOn
                RawItem(
                    "film:${c.field}", name, if (on) onText else offText, AppIcons.AdjustmentsLine, changed(c), explanation, reset,
                    choices = listOf(false to offText, true to onText).map { (v, label) ->
                        EditorChoice(label, null, v == on) {
                            setDoc(doc.withValue(c, if (v == kind.restingOn) null else FotufilmDocument.Value.Flag(v)))
                        }
                    },
                )
            }
            is FotufilmCatalogue.Kind.Menu -> {
                val selected = (c.value as? FotufilmDocument.Value.Choice)?.id ?: kind.defaultChoice
                val stock = c.field == FotufilmDocument.STOCK_FIELD
                RawItem(
                    "film:${c.field}", name,
                    kind.choices.firstOrNull { it.id == selected }?.let { FotufilmStrings.choice(c.field, kind.menu, it) }.orEmpty(),
                    if (stock) AppIcons.Film else AppIcons.AdjustmentsLine, changed(c), explanation, reset,
                    choices = kind.choices.map { choice ->
                        EditorChoice(FotufilmStrings.choice(c.field, kind.menu, choice), null, choice.id == selected) {
                            when {
                                choice.id == selected -> Unit
                                stock -> changeStock(choice.id)
                                else -> setDoc(doc.withValue(c, if (choice.id == kind.defaultChoice) null else FotufilmDocument.Value.Choice(choice.id)))
                            }
                        }
                    },
                )
            }
            FotufilmCatalogue.Kind.Other -> null
        }
    }.filterNotNull()
    return RawLevel(items, sections)
}

private fun lumixStyleLabel(style: LumixPhotoStyle): Int = when (style) {
    LumixPhotoStyle.Standard -> R.string.settings_raw_lumix_style_standard
    LumixPhotoStyle.Vivid -> R.string.settings_raw_lumix_style_vivid
    LumixPhotoStyle.Natural -> R.string.settings_raw_lumix_style_natural
    LumixPhotoStyle.Landscape -> R.string.settings_raw_lumix_style_landscape
    LumixPhotoStyle.Portrait -> R.string.settings_raw_lumix_style_portrait
    LumixPhotoStyle.Monochrome -> R.string.settings_raw_lumix_style_monochrome
    LumixPhotoStyle.LMonochrome -> R.string.settings_raw_lumix_style_l_monochrome
    LumixPhotoStyle.LMonochromeD -> R.string.settings_raw_lumix_style_l_monochrome_d
    LumixPhotoStyle.LMonochromeS -> R.string.settings_raw_lumix_style_l_monochrome_s
    LumixPhotoStyle.LeicaMonochrome -> R.string.settings_raw_lumix_style_leica_monochrome
    LumixPhotoStyle.LClassicNeo -> R.string.settings_raw_lumix_style_l_classicneo
    LumixPhotoStyle.Flat -> R.string.settings_raw_lumix_style_flat
    LumixPhotoStyle.CinelikeD2 -> R.string.settings_raw_lumix_style_cinelike_d2
    LumixPhotoStyle.CinelikeV2 -> R.string.settings_raw_lumix_style_cinelike_v2
    LumixPhotoStyle.Like709 -> R.string.settings_raw_lumix_style_709like
    LumixPhotoStyle.VLog -> R.string.settings_raw_lumix_style_vlog
}

private fun canonStyleLabel(style: CanonPictureStyle): Int = when (style) {
    CanonPictureStyle.Standard -> R.string.settings_raw_canon_style_standard
    CanonPictureStyle.Portrait -> R.string.settings_raw_canon_style_portrait
    CanonPictureStyle.Landscape -> R.string.settings_raw_canon_style_landscape
    CanonPictureStyle.Neutral -> R.string.settings_raw_canon_style_neutral
    CanonPictureStyle.Faithful -> R.string.settings_raw_canon_style_faithful
    CanonPictureStyle.Monochrome -> R.string.settings_raw_canon_style_monochrome
}
