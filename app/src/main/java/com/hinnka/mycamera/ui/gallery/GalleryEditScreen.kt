package com.hinnka.mycamera.ui.gallery

import com.hinnka.mycamera.ui.prefs.preferenceValue
import android.annotation.SuppressLint
import android.graphics.RectF
import com.hinnka.mycamera.camera.AspectRatio
import java.util.Locale
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.ImageBitmap
import com.hinnka.mycamera.processor.BokehStage
import com.hinnka.mycamera.processor.PortraitBlurMetadata
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import com.hinnka.mycamera.ui.components.HideNavigationBar
import kotlinx.coroutines.Job
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.hinnka.mycamera.ui.theme.PremiumGold
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.R
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.PostEditGeometry
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.RecipeParam
import com.hinnka.mycamera.model.toEffectParams
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.ml.DepthModelManager
import com.hinnka.mycamera.ml.AiDenoiseModelManager
import com.hinnka.mycamera.ml.AiDenoiseModelState
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.raw.SpectralFilmTuning
import com.hinnka.mycamera.raw.DngEmbeddedProfile
import com.hinnka.mycamera.raw.RawAdaptiveExposureMode
import com.hinnka.mycamera.processor.DenoiseStrength
import com.hinnka.mycamera.processor.BokehStyle
import com.hinnka.mycamera.ui.components.*
import com.hinnka.mycamera.ui.components.RawEditPanel
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.ZoomSpec

import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.hinnka.mycamera.raw.RawDemosaicProcessor
import com.hinnka.mycamera.raw.RawDevelopStage
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.raw.DcpInfo
import com.hinnka.mycamera.lut.CurveUtils
import com.hinnka.mycamera.lut.creator.OklchConverter
import com.hinnka.mycamera.lut.VideoLutEffect
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.ui.camera.autoRotate
import com.hinnka.mycamera.frame.FrameInfo
import com.hinnka.mycamera.lut.LutInfo
import com.hinnka.mycamera.model.CameraPreset
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.ui.settings.presetDisplayName
import com.hinnka.mycamera.ui.camera.ChoiceScroller
import com.hinnka.mycamera.ui.camera.NoteLineState
import com.hinnka.mycamera.ui.camera.NoteLine
import com.hinnka.mycamera.ui.components.RawEditPanelContentMode
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import androidx.compose.ui.input.pointer.util.VelocityTracker

// How long the explanation card stays open.
private const val EXPLANATION_CARD_MS = 4000L

// Two taps on the same setting closer than this are a double tap.
private const val DOUBLE_TAP_MS = 300L

// Look intensity, 0 to 200 % in 5 % steps.
private val IntensitySteps = List(41) { it * 5 / 100f }

private data class EmbeddedDngProfileUiState(
    val options: List<RawEmbeddedDngProfileOption> = emptyList(),
    val defaultProfileId: String? = null,
)

private data class PreviewRenderSignature(
    val photoId: String,
    /** RAW: the quick preview develop the photo renders on, until Save develops it in full. */
    val rawPreviewBase: Bitmap?,
    /** The preview's long edge: 2048, or 4096 with the HD preview. */
    val maxEdge: Int,
    /** The edit chain's small copy (AI denoise, blur, noise reduction) the photo renders on while editing. */
    val editChainBase: Bitmap?,
    val editCropRect: RectF?,
    val refreshKey: Long,
    val editLutId: String?,
    val editPhotoRecipeParams: ColorRecipeParams?,
    val editLutRecipeParams: ColorRecipeParams?,
    val editLutConfig: Any?,
    val editFrameId: String?,
    val editFrameCustomProperties: Map<String, String>,
    val editSharpening: Float,
    val editNoiseReduction: Float,
    val editChromaNoiseReduction: Float,
    val editRawExposureCompensation: Float,
    val editRawAutoExposure: Boolean,
    val editRawHighlightsAdjustment: Float,
    val editRawShadowsAdjustment: Float,
    val editRawBlackPointCorrection: Float,
    val editRawWhitePointCorrection: Float,
    val editRawLensShadingCorrectionEnabled: Boolean,
    val editRawDROMode: String,
    val editRawBlackLevelMode: String,
    val editRawCustomBlackLevel: Float,
    val editRawWhiteLevelMode: String,
    val editRawCustomWhiteLevel: Float,
    val editRawCfaCorrectionMode: String,
    val editRawDcpId: String?,
    val editRawEmbeddedDngProfileId: String?,
    val editRawHncsProfileId: String?,
    val editRawHncsRenderIntent: String,
    val editRawHncsFilmCurveMode: String,
    val editRawRenderingEngine: String,
    val editRawBaselineLutId: String?,
    val editRawBaselineRecipeParams: ColorRecipeParams?,
    val editComputationalAperture: Float?,
    val editBokehStyle: BokehStyle,
    val editFocusX: Float?,
    val editFocusY: Float?,
    val editRotationDegrees: Int,
    val editStraightenDegrees: Float,
    val editMirrorHorizontal: Boolean,
    val showOrigin: Boolean,
    val cropView: Boolean,
    val portraitView: Boolean,
    /** RAW: the recipe that stands in for tone values the shown develop wasn't made with ([rawToneStandIn]). */
    val rawToneStandIn: ColorRecipeParams? = null,
)

/**
 * 照片编辑界面
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    FlowPreview::class
)
@Composable
fun GalleryEditScreen(
    viewModel: GalleryViewModel,
    cameraViewModel: CameraViewModel,
    onBack: () -> Unit,
    onOpenFrameEditor: (String) -> Unit,
    onFilterManagementClick: (String?) -> Unit,
    onCreatePresetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val depthModelState by remember(context.applicationContext) {
        DepthModelManager.observe(context)
    }.collectAsState()
    var showDepthModelDownloadDialog by remember { mutableStateOf(false) }
    // Depth was chosen while its model is missing: applied once the model is installed.
    var pendingDepth by remember { mutableStateOf(false) }
    val depthModelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { DepthModelManager.importModel(context, it) }
    }

    LaunchedEffect(depthModelState, pendingDepth) {
        if (depthModelState is DepthModelDownloadState.Ready && pendingDepth) {
            viewModel.setPortraitQuick(false)
            if ((viewModel.editComputationalAperture.value ?: 0f) <= 0f) {
                viewModel.setComputationalAperture((viewModel.editOpenValue("aperture") as? Float)?.takeIf { it > 0f } ?: 2f)
            }
            pendingDepth = false
            showDepthModelDownloadDialog = false
        }
    }

    val userPreferencesRepository = remember {
        com.hinnka.mycamera.data.ContentRepository.getInstance(context).userPreferencesRepository
    }
    val userPreferences by userPreferencesRepository.userPreferences.collectAsState(
        initial = com.hinnka.mycamera.data.UserPreferences()
    )
    val currentPhotos by viewModel.currentPhotos.collectAsState()
    val currentPhoto = currentPhotos.getOrNull(viewModel.currentPhotoIndex)
    val editSourcePhoto = currentPhoto?.relatedPhoto ?: currentPhoto
    val editLutId by viewModel.editLutId.collectAsState()
    val editLutRecipeParams by viewModel.editLutRecipeParams.collectAsState()
    val editPhotoRecipeParams by viewModel.editPhotoRecipeParams.collectAsState()
    val editLutConfig = viewModel.editLutConfig
    val availableLuts = viewModel.selectableEditLuts
    val videoSourceProfile = viewModel.editVideoSourceProfile
    val lutSwipeState = rememberLutSwipeState()
    val density = LocalDensity.current
    val showPaymentDialog = viewModel.showPaymentDialog
    val isPurchased by viewModel.isPurchased.collectAsState()
    val hasCopiedEditSettings by viewModel.hasCopiedEditSettings.collectAsState()

    var isSaving by remember { mutableStateOf(false) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    val defaultPresetName = stringResource(R.string.preset_new_preset_default)

    // 预览 Bitmap 状态
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropPreviewBitmap by remember(editSourcePhoto?.id) { mutableStateOf<Bitmap?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 边框编辑状态
    val editFrameId by viewModel.editFrameId.collectAsState()
    val availableFrames = viewModel.availableFrames
    var editFrameCustomProperties by remember { mutableStateOf(emptyMap<String, String>()) }

    val editSharpening by viewModel.editSharpening.collectAsState()
    val editNoiseReduction by viewModel.editNoiseReduction.collectAsState()
    val editChromaNoiseReduction by viewModel.editChromaNoiseReduction.collectAsState()
    val editRawExposureCompensation by viewModel.editRawExposureCompensation.collectAsState()
    val editRawAutoExposure by viewModel.editRawAutoExposure.collectAsState()
    val editRawHighlightsAdjustment by viewModel.editRawHighlightsAdjustment.collectAsState()
    val editRawShadowsAdjustment by viewModel.editRawShadowsAdjustment.collectAsState()
    val editRawBlackPointCorrection by viewModel.editRawBlackPointCorrection.collectAsState()
    val editRawWhitePointCorrection by viewModel.editRawWhitePointCorrection.collectAsState()
    val editRawLensShadingCorrectionEnabled by viewModel.editRawLensShadingCorrectionEnabled.collectAsState()
    val editRawDROMode by viewModel.editRawDROMode.collectAsState()
    val editRawBlackLevelMode by viewModel.editRawBlackLevelMode.collectAsState()
    val editRawCustomBlackLevel by viewModel.editRawCustomBlackLevel.collectAsState()
    val editRawWhiteLevelMode by viewModel.editRawWhiteLevelMode.collectAsState()
    val editRawCustomWhiteLevel by viewModel.editRawCustomWhiteLevel.collectAsState()
    val editRawCfaCorrectionMode by viewModel.editRawCfaCorrectionMode.collectAsState()
    val editRawDcpId by viewModel.editRawDcpId.collectAsState()
    val editRawEmbeddedDngProfileId by
        viewModel.editRawEmbeddedDngProfileId.collectAsState()
    val editRawHncsProfileId by viewModel.editRawHncsProfileId.collectAsState()
    val editRawHncsRenderIntent by viewModel.editRawHncsRenderIntent.collectAsState()
    val editRawHncsFilmCurveMode by viewModel.editRawHncsFilmCurveMode.collectAsState()
    val editRawBaselineLutId by viewModel.editRawBaselineLutId.collectAsState()
    val editRawBaselineRecipeParams by viewModel.editRawBaselineRecipeParams.collectAsState()
    val editRawColorEngine by viewModel.editRawRenderingEngine.collectAsState()
    val editRawToneMappingParameters by viewModel.editRawToneMappingParameters.collectAsState()
    val editRawSpectralFilmStock by viewModel.editRawSpectralFilmStock.collectAsState()
    val editRawSpectralFilmPrint by viewModel.editRawSpectralFilmPrint.collectAsState()
    val editRawFotufilmDocument by viewModel.editRawFotufilmDocument.collectAsState()
    val editRawSpectralFilmCDensityGain by viewModel.editRawSpectralFilmCDensityGain.collectAsState()
    val editRawSpectralFilmMDensityGain by viewModel.editRawSpectralFilmMDensityGain.collectAsState()
    val editRawSpectralFilmYDensityGain by viewModel.editRawSpectralFilmYDensityGain.collectAsState()
    val availableDcps = viewModel.availableDcps
    
    val editComputationalAperture by viewModel.editComputationalAperture.collectAsState()
    val editBokehStyle by viewModel.editBokehStyle.collectAsState()
    val isBokehProcessing by viewModel.isBokehProcessing.collectAsState()
    val isPhotonDenoising by viewModel.isPhotonDenoising.collectAsState()
    val editFocusX by viewModel.editFocusPointX.collectAsState()
    val editFocusY by viewModel.editFocusPointY.collectAsState()

    val editCropRect by viewModel.editCropRect.collectAsState()
    val editCropAspectOption by viewModel.editCropAspectOption.collectAsState()
    val editRotationDegrees by viewModel.editRotationDegrees.collectAsState()
    val editStraightenDegrees by viewModel.editStraightenDegrees.collectAsState()
    val editMirrorHorizontal by viewModel.editMirrorHorizontal.collectAsState()

    val editAiDenoiseEnabled by viewModel.editAiDenoiseEnabled.collectAsState()

    val isRaw = editSourcePhoto?.let { viewModel.isRaw(it.id) } ?: false
    val refreshKey = editSourcePhoto?.id?.let { viewModel.photoRefreshKeys[it] } ?: 0L
    val embeddedDngProfileUiState = embeddedDngProfiles(editSourcePhoto, isRaw, refreshKey)
    val activeEmbeddedDngProfileId = editRawEmbeddedDngProfileId
        ?.takeIf { selectedId ->
            embeddedDngProfileUiState.options.any { it.id == selectedId }
        }
        ?: embeddedDngProfileUiState.defaultProfileId

    // The editor's small, unsaved UI state in one object: the screen's main lambda may take at most 255 values.
    val ui = remember(editSourcePhoto?.id) { EditorUiState() }
    // HD preview: the editor previews at full size (slower, exactly what Save gives) instead of small copies.
    val hdPreviewState = preferenceValue { it.editorHdPreview }
    val hdPreview by hdPreviewState
    var showOrigin by remember { mutableStateOf(false) }

    // 编辑标签页状态
    // Where the editor is: the section, the setting picked in each section, and full view. Saved, so a
    // return from the frame or preset editor lands in the same place.
    var section by rememberSaveable { mutableStateOf(EditorSection.Look) }
    var selections by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    var fullView by rememberSaveable { mutableStateOf(false) }
    // The groups entered inside the section, outermost first, as EditorGroup names joined by "/".
    var groupPath by rememberSaveable { mutableStateOf("") }
    // The setting whose explanation card is open.
    var explainKey by remember { mutableStateOf<String?>(null) }
    // Short notes that explain a press, in the camera's guide pill, where the explanation card sits.
    val noteLine = remember { NoteLineState() }
    LaunchedEffect(noteLine.serial) {
        if (noteLine.shown) explainKey = null
    }
    // Taps on the value row: the last one, to tell a double tap, and a second tap's card waiting out that window.
    // Portrait: the aperture while the ruler moves (rendered once, on release), the photo's place on screen
    // inside the zoomable view, and the focus ring while a finger drags it.
    var apertureDrag by remember { mutableStateOf<Float?>(null) }
    // The depth of field while its ruler moves (rendered once, on release).
    var dofDrag by remember { mutableStateOf<Float?>(null) }
    var contentBounds by remember { mutableStateOf<Rect?>(null) }
    var ringGrab by remember { mutableStateOf(false) }
    var ringDrag by remember { mutableStateOf<Offset?>(null) }
    // The recipe as it was when a long press on one setting began, to put back when it ends.
    // The Crop value held down: the photo shows without that one change, and without the frame.
    var showDiscardDialog by remember { mutableStateOf(false) }
    var fingerOnPhoto by remember { mutableStateOf(false) }
    var fingerOnRuler by remember { mutableStateOf(false) }
    // Heavy work (a blur, a RAW develop) starts only once the user pauses: tell the view model about touches.
    LaunchedEffect(fingerOnPhoto, fingerOnRuler) {
        viewModel.noteEditorInteraction(fingerOnPhoto || fingerOnRuler)
    }
    val cropView = section == EditorSection.Crop && currentPhoto?.isVideo != true
    var isZoomed by remember { mutableStateOf(false) }
    // Curve: the channel being edited, its selected point, and the points while a finger drags one (the recipe
    // changes once, when the finger lifts).
    var curveChannel by rememberSaveable { mutableStateOf(CurveChannel.MASTER) }
    // Light › RAW › Profile: the imported DCP a long press asked to delete.
    val curveOpen = groupPath.substringAfterLast("/") == EditorGroup.Curve.name
    // Mixer, Grading and Calibration: the colour, range or primary picked in each.
    var mixerPick by rememberSaveable { mutableIntStateOf(1) }
    var gradingPick by rememberSaveable { mutableIntStateOf(0) }
    var calibrationPick by rememberSaveable { mutableIntStateOf(0) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    var previewRenderRequestId by remember { mutableLongStateOf(0L) }
    // The edit chain's small copy (AI denoise, blur, JPEG noise reduction), the base on screen while editing.
    val editChainBase by viewModel.editChainBase.collectAsState()

    // shortcut: a rough stand-in, exact only in the midtones (brightening clips early, darkening can't bring back
    // what the develop clipped); it ends by itself when a develop of the current tone values lands.
    // While the RAW tone rulers move, the shown develop gets the difference drawn on with the recipe's own Exposure,
    // Highlights and Shadows, added to the recipe's values, and Whites/Blacks as a master curve.
    fun rawToneStandIn(): ColorRecipeParams? {
        if (!isRaw) return null
        // The develop on screen: the edit chain's picture lags a new develop while AI denoise or the blur runs.
        val developed = (if (editChainBase != null) viewModel.editChainRawState else ui.developedRaw)
            ?: viewModel.editRawState(open = true)
        fun was(key: String) = developed[key] as? Float ?: 0f
        val exposure = editRawExposureCompensation - was("rawExposure")
        val highlights = editRawHighlightsAdjustment - was("rawHighlights")
        val shadows = editRawShadowsAdjustment - was("rawShadows")
        val levelsChanged = editRawWhitePointCorrection != was("rawWhitePoint") ||
            editRawBlackPointCorrection != was("rawBlackPoint")
        if (exposure == 0f && highlights == 0f && shadows == 0f && !levelsChanged) return null
        val base = editPhotoRecipeParams ?: editLutRecipeParams
        return base.copy(
            exposure = base.exposure + exposure,
            highlights = base.highlights + highlights,
            shadows = base.shadows + shadows,
            masterCurvePoints = if (!levelsChanged) base.masterCurvePoints else rawLevelsCurve(
                was("rawBlackPoint"), was("rawWhitePoint"),
                editRawBlackPointCorrection, editRawWhitePointCorrection,
                base.masterCurvePoints,
            ),
        )
    }

    fun currentPreviewSignature(fast: Boolean = false): PreviewRenderSignature? {
        val photo = editSourcePhoto ?: return null
        val rawDevelopIsBaked = isRaw
        val inCrop = section == EditorSection.Crop
        return PreviewRenderSignature(
            photoId = photo.id,
            rawPreviewBase = ui.rawPreviewBase,
            maxEdge = if (hdPreview) 4096 else 2048,
            editChainBase = editChainBase,
            editCropRect = if (inCrop) null else editCropRect,
            refreshKey = refreshKey,
            editLutId = editLutId,
            editPhotoRecipeParams = editPhotoRecipeParams,
            editLutRecipeParams = editLutRecipeParams,
            // Read here, not from the screen's local copy: the render effects keep this function from their first
            // composition, so a look loaded later never matched the screen's state and Crop never showed.
            editLutConfig = viewModel.editLutConfig,
            editFrameId = editFrameId,
            editFrameCustomProperties = editFrameCustomProperties.toMap(),
            editSharpening = if (rawDevelopIsBaked) 0f else editSharpening,
            // 降噪烘焙在底图里（RAW 开发 / JPG 降噪基底），松手后通过 refreshKey 刷新。
            editNoiseReduction = 0f,
            editChromaNoiseReduction = 0f,
            editRawExposureCompensation = if (fast || rawDevelopIsBaked) 0f else editRawExposureCompensation,
            editRawAutoExposure = if (fast || rawDevelopIsBaked) false else editRawAutoExposure,
            editRawHighlightsAdjustment = if (fast || rawDevelopIsBaked) 0f else editRawHighlightsAdjustment,
            editRawShadowsAdjustment = if (fast || rawDevelopIsBaked) 0f else editRawShadowsAdjustment,
            editRawBlackPointCorrection = if (fast || rawDevelopIsBaked) 0f else editRawBlackPointCorrection,
            editRawWhitePointCorrection = if (fast || rawDevelopIsBaked) 0f else editRawWhitePointCorrection,
            editRawLensShadingCorrectionEnabled = if (fast || rawDevelopIsBaked) false else editRawLensShadingCorrectionEnabled,
            editRawDROMode = if (fast || rawDevelopIsBaked) "" else editRawDROMode,
            editRawBlackLevelMode = if (fast || rawDevelopIsBaked) "" else editRawBlackLevelMode,
            editRawCustomBlackLevel = if (fast || rawDevelopIsBaked) 0f else editRawCustomBlackLevel,
            editRawWhiteLevelMode = if (fast || rawDevelopIsBaked) "" else editRawWhiteLevelMode,
            editRawCustomWhiteLevel = if (fast || rawDevelopIsBaked) 0f else editRawCustomWhiteLevel,
            editRawCfaCorrectionMode = if (fast || rawDevelopIsBaked) "" else editRawCfaCorrectionMode,
            editRawDcpId = if (fast || rawDevelopIsBaked) null else editRawDcpId,
            editRawEmbeddedDngProfileId =
                if (fast || rawDevelopIsBaked) null else editRawEmbeddedDngProfileId,
            editRawHncsProfileId =
                if (fast || rawDevelopIsBaked) null else editRawHncsProfileId,
            editRawHncsRenderIntent =
                if (fast || rawDevelopIsBaked) "" else editRawHncsRenderIntent.assetValue,
            editRawHncsFilmCurveMode =
                if (fast || rawDevelopIsBaked) "" else editRawHncsFilmCurveMode.persistedValue,
            editRawRenderingEngine = if (fast || rawDevelopIsBaked) "" else editRawColorEngine.name,
            // RAW develop controls above are baked into original.jpg, while baseline color
            // correction is still applied by PhotoProcessor to that bitmap preview.
            editRawBaselineLutId = editRawBaselineLutId,
            editRawBaselineRecipeParams = editRawBaselineRecipeParams,
            editComputationalAperture = if (fast) 0f else editComputationalAperture,
            editBokehStyle = editBokehStyle,
            editFocusX = editFocusX,
            editFocusY = editFocusY,
            // Crop draws the turn, flip and straightening on its canvas: changing them there renders nothing.
            editRotationDegrees = if (inCrop) 0 else editRotationDegrees,
            editStraightenDegrees = if (inCrop) 0f else editStraightenDegrees,
            editMirrorHorizontal = if (inCrop) false else editMirrorHorizontal,
            showOrigin = showOrigin,
            cropView = section == EditorSection.Crop,
            portraitView = section == EditorSection.Portrait && !fullView,
            rawToneStandIn = rawToneStandIn(),
        )
    }
    // Effects and the screen hold this one reference rather than every value the function reads: that keeps
    // their compiled methods small enough for ART to compile.
    val previewSignature: (Boolean) -> PreviewRenderSignature? = ::currentPreviewSignature

    // RAW: this session's renders, small, by the edit state they show. Going back to one (an engine tried before)
    // shows it at once while the RAW develops again behind it, so looks can be compared without waiting.
    // The RAW state the photo's files were last developed from; null: as the editor opened.
    fun rawLook(state: Map<String, Any?>): Bitmap? = ui.rawLooks.firstOrNull { it.first == state }?.second
    fun rawDeveloped(): Boolean = viewModel.editRawState() == (ui.developedRaw ?: viewModel.editRawState(open = true))

    suspend fun renderPreview(
        photo: MediaData,
        maxEdge: Int,
        cancelStaleResult: Boolean
    ) {
        val requestId = ++previewRenderRequestId
        val isCropPreview = section == EditorSection.Crop
        // The state this picture shows: a mode switch waits for a picture of the current state.
        val renderedFor = previewSignature(false)
        val bitmap = withContext(Dispatchers.IO) {
            viewModel.getPreviewBitmap(
                photo,
                useGlobalEdit = true,
                showOrigin = showOrigin,
                ignoreCrop = isCropPreview,
                ignoreStraighten = isCropPreview,
                ignoreTurn = isCropPreview,
                // Portrait shows the photo without its frame, so the focus ring lands on the photo itself; full
                // view has no ring and shows the frame.
                ignoreFrame = isCropPreview || (section == EditorSection.Portrait && !fullView),
                bitmap = (editChainBase ?: ui.rawPreviewBase.takeIf { isRaw }).takeIf { !showOrigin },
                recipeParamsOverride = renderedFor?.rawToneStandIn,
                maxEdge = maxEdge
            )
        }
        if ((!cancelStaleResult || requestId == previewRenderRequestId) && bitmap != null) {
            if (isCropPreview) {
                if (section == EditorSection.Crop) {
                    cropPreviewBitmap = bitmap
                    ui.cropFor = renderedFor
                }
            } else if (isRaw && !rawDeveloped() && rawLook(viewModel.editStateKey()) != null) {
                // An older develop finished while an earlier look is shown for the newer state: keep that look.
            } else {
                previewBitmap = bitmap
                ui.previewFor = renderedFor
            }
        }
        // The newest render ends the wait, also when it produced nothing, so the spinner can't stay on.
        if (requestId == previewRenderRequestId) isLoadingPreview = false
    }



    LaunchedEffect(editSourcePhoto) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        editFrameCustomProperties = photo.metadata?.customProperties
            ?: viewModel.getEditCustomProperties(photo.id)
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey, isRaw) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        if (photo.isVideo) return@LaunchedEffect
        snapshotFlow {
            // RAW: only the tone stand-in follows the finger; everything else waits for the full pass.
            previewSignature(true)?.takeIf { !isRaw || it.rawToneStandIn != null }
        }
            .filter { it != null }
            .conflate()
            .collect {
                renderPreview(photo, maxEdge = 1440, cancelStaleResult = false)
            }
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey) {
        cropPreviewBitmap = null
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        if (photo.isVideo) return@LaunchedEffect
        // A new refresh key means the photo's files changed (a finished blur, a RAW re-render): the spinner
        // stays on until the preview shows them, not only while they were being made.
        isLoadingPreview = true
        snapshotFlow {
            previewSignature(false)
        }
            .filter { it != null }
            .conflate()
            .debounce(250)
            .collectLatest {
                // 2048 px is about three screens' worth on this phone: sharp for a 2x zoom, a quarter of the
                // GPU work of 4096. Only the screen uses it; the saved edit is rendered from the files.
                renderPreview(photo, maxEdge = if (hdPreview) 4096 else 2048, cancelStaleResult = true)
            }
    }

    LaunchedEffect(editSourcePhoto) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        thumbnailBitmap = withContext(Dispatchers.IO) {
            viewModel.loadThumbnail(photo)
        }
    }

    if (currentPhoto == null) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }
    val currentEditSourcePhoto = editSourcePhoto ?: currentPhoto
    val currentEditMetadata = currentEditSourcePhoto.metadata ?: viewModel.currentMediaMetadata
    val aiDenoiseModelState by remember(context.applicationContext) {
        AiDenoiseModelManager.observe(context)
    }.collectAsState()
    // AI denoise was switched on while its model is missing; applied once the model is installed.
    var pendingAiDenoise by remember { mutableStateOf(false) }
    val showAiDenoiseFailure = {
        noteLine.say(R.string.ai_denoise_failed)
    }
    // While editing, AI denoise runs on the small copy (the edit chain); Save runs it on the whole photo.
    val startAiDenoise = { viewModel.setAiDenoiseEnabled(true) }
    LaunchedEffect(aiDenoiseModelState, pendingAiDenoise) {
        if (aiDenoiseModelState is AiDenoiseModelState.Ready && pendingAiDenoise) {
            pendingAiDenoise = false
            startAiDenoise()
        }
    }
    val sections = if (currentPhoto.isVideo) {
        EditorSection.entries - EditorSection.Portrait - EditorSection.Crop
    } else {
        EditorSection.entries
    }
    // Reading every edit value here makes this recompute on the frame a value changes, so a change dot
    // appears and goes at once rather than with the next unrelated recomposition.
    val applyEffectsToVideo by viewModel.editApplyEffectsToVideo.collectAsState()
    val editPortraitQuick by viewModel.editPortraitQuick.collectAsState()
    val editDepthOfField by viewModel.editDepthOfField.collectAsState()
    val editState = remember(
        editLutId, editPhotoRecipeParams, editFrameId, applyEffectsToVideo, editSharpening, editNoiseReduction,
        editChromaNoiseReduction, editAiDenoiseEnabled, editRawExposureCompensation, editRawAutoExposure,
        editRawHighlightsAdjustment, editRawShadowsAdjustment, editRawBlackPointCorrection, editRawWhitePointCorrection,
        editRawLensShadingCorrectionEnabled, editRawDROMode, editRawBlackLevelMode, editRawCustomBlackLevel,
        editRawWhiteLevelMode, editRawCustomWhiteLevel, editRawCfaCorrectionMode, editRawDcpId, editRawEmbeddedDngProfileId,
        editRawHncsProfileId, editRawHncsRenderIntent, editRawHncsFilmCurveMode, editRawBaselineLutId, editRawColorEngine,
        editRawToneMappingParameters, editRawSpectralFilmStock, editRawSpectralFilmPrint, editRawSpectralFilmCDensityGain,
        editRawSpectralFilmMDensityGain, editRawSpectralFilmYDensityGain, editRawFotufilmDocument, editComputationalAperture,
        editBokehStyle, editFocusX, editFocusY, editCropRect, editCropAspectOption, editRotationDegrees,
        editStraightenDegrees, editMirrorHorizontal, editPortraitQuick, editDepthOfField,
    ) { viewModel.editStateKey() }
    val changedKeys = remember(editState) { viewModel.editChangedKeys() }
    // Each finished change is one undo step: the state is recorded once no finger is on the photo or a ruler
    // (a drag changes it many times) and no long press is comparing.
    LaunchedEffect(editState, fingerOnPhoto, fingerOnRuler, ui.heldRecipe) {
        if (!fingerOnPhoto && !fingerOnRuler && ui.heldRecipe == null) viewModel.recordEditStep()
    }
    val openIntensity = viewModel.editOpenValue("lutIntensity") as? Float ?: 1f
    val categoryOrder by preferenceValue { it.categoryOrder }
    val allPresets by cameraViewModel.allPresets.collectAsState()
    // The look strip's thumbnails are made from a small copy: each look renders on the shared GPU thread.
    val stripThumbnail = remember(thumbnailBitmap) {
        thumbnailBitmap?.let { Bitmap.createScaledBitmap(it, 132, (132f * it.height / it.width).roundToInt().coerceAtLeast(1), true) }
    }
    var showSaveIntoLook by remember { mutableStateOf(false) }
    // A photo saved with the built-in "none" look keeps that id until another look is picked.
    val hasLook = editLutId != null && editLutId != "none"
    val isAiDenoising by viewModel.isAiDenoising.collectAsState()
    val aiDenoiseProgress by viewModel.aiDenoiseProgress.collectAsState()
    LaunchedEffect(fullView) {
        if (fullView) explainKey = null
    }
    // The explanation card closes by itself once there has been time to read it.
    LaunchedEffect(explainKey) {
        if (explainKey != null) {
            delay(EXPLANATION_CARD_MS)
            explainKey = null
        }
    }

    val portraitQuick = editPortraitQuick ?: userPreferences.portraitQuickBlur
    // Gesture handlers on the photo are set up once and outlive this composition: they read the mode here.
    val currentPortraitQuick by rememberUpdatedState(portraitQuick)
    val blurProgress by viewModel.blurProgress.collectAsState()
    val blurMaps by viewModel.blurMaps.collectAsState()

    // A blur was asked for and its result isn't on screen yet: from the render's start until the preview
    // has reloaded with it.
    // The edit chain: AI denoise, the blur and JPEG noise reduction on a small copy while editing (see
    // updateEditChain); its result is the photo's base on screen.
    val editChainBusy by viewModel.editChainBusy.collectAsState()
    val waitingForPause by viewModel.waitingForPause.collectAsState()
    var noiseReleased by remember(editSourcePhoto?.id) {
        mutableStateOf((viewModel.editOpenValue("noiseReduction") as? Float ?: 0f) to (viewModel.editOpenValue("chromaNoiseReduction") as? Float ?: 0f))
    }
    LaunchedEffect(
        editSourcePhoto?.id, ui.rawPreviewBase, editAiDenoiseEnabled, editComputationalAperture, editFocusX, editFocusY,
        editBokehStyle, editPortraitQuick, editDepthOfField, noiseReleased, hdPreview,
    ) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        if (photo.isVideo) return@LaunchedEffect
        viewModel.updateEditChain(
            photo, ui.rawPreviewBase.takeIf { isRaw }, noiseReleased.first, noiseReleased.second, fullSize = hdPreview,
            rawState = ui.developedRaw,
        )
    }
    var awaitingBlur by remember { mutableStateOf(false) }
    val blurPending by viewModel.blurPending.collectAsState()
    LaunchedEffect(isBokehProcessing, blurPending, isLoadingPreview) {
        if (isBokehProcessing || blurPending) awaitingBlur = true else if (!isLoadingPreview) awaitingBlur = false
    }

    val portraitOpen = section == EditorSection.Portrait && !currentPhoto.isVideo
    // The ruler's place between two stops while it is dragged, so the preview's blur grows with the finger.
    var apertureScroll by remember { mutableStateOf<Float?>(null) }
    val previewStrength = apertureScroll?.let { position ->
        val lower = position.toInt().coerceIn(0, ApertureStops.lastIndex)
        val upper = (lower + 1).coerceAtMost(ApertureStops.lastIndex)
        val t = position - lower
        BlurPreview.strength(ApertureStops[lower]) * (1 - t) + BlurPreview.strength(ApertureStops[upper]) * t
    } ?: BlurPreview.strength(apertureDrag ?: editComputationalAperture ?: 0f)

    val previewDepthOfField = dofDrag?.roundToInt() ?: editDepthOfField
    val blurLayers by portraitBlurLayers(
        viewModel = viewModel,
        photo = currentEditSourcePhoto,
        portraitOpen = portraitOpen,
        editPhotoRecipeParams = editPhotoRecipeParams,
        editLutId = editLutId,
        editCropRect = editCropRect,
        editRotationDegrees = editRotationDegrees,
        editStraightenDegrees = editStraightenDegrees,
        editMirrorHorizontal = editMirrorHorizontal,
        blurMaps = blurMaps,
        ringDrag = ringDrag,
        editFocusX = editFocusX,
        editFocusY = editFocusY,
        portraitQuick = portraitQuick,
        previewDepthOfField = previewDepthOfField,
        previewStrength = previewStrength,
        contentBounds = { contentBounds },
    )

    // Portrait's focus ring: where the focus point is on the photo, and on screen inside the zoomable view.
    val focusOnPhoto = remember(
        editFocusX, editFocusY, editCropRect, editRotationDegrees, editStraightenDegrees, editMirrorHorizontal,
    ) { viewModel.focusPointOnPhoto() }

    fun ringCenter(focus: Pair<Float, Float>?): Offset? {
        val b = contentBounds?.takeIf { it.width > 0f && it.height > 0f } ?: return null
        return focus?.let { (x, y) -> Offset(b.left + x * b.width, b.top + y * b.height) }
    }

    // Sets the focus at a place on screen, mapped onto the photo, and renders the blur once.
    fun placeFocus(position: Offset) {
        if (currentPortraitQuick) {
            noteLine.say(R.string.editor_quick_focus_hint)
            return
        }
        val b = contentBounds?.takeIf { it.width > 0f && it.height > 0f } ?: return
        viewModel.setFocusPoint(
            ((position.x - b.left) / b.width).coerceIn(0f, 1f),
            ((position.y - b.top) / b.height).coerceIn(0f, 1f),
        )
    }

    fun setCurve(channel: CurveChannel, points: List<Pair<Float, Float>>?) {
        viewModel.setPhotoRecipeParams((editPhotoRecipeParams ?: editLutRecipeParams).withCurve(channel, points))
    }

    fun leaveEditor() {
        viewModel.exitEditMode()
        onBack()
    }

    // Cancel asks first when something changed since the editor opened.
    fun requestLeave() {
        if (viewModel.editChangedKeys().isNotEmpty()) showDiscardDialog = true else leaveEditor()
    }

    // Back goes up one step: the question, the explanation card, a group, full view, then leaving.
    BackHandler {
        when {
            showDiscardDialog -> showDiscardDialog = false
            explainKey != null -> explainKey = null
            groupPath.isNotEmpty() -> groupPath = groupPath.substringBeforeLast("/", "")
            fullView -> fullView = false
            else -> requestLeave()
        }
    }

    // The editor runs immersive: the navigation bar hides too, and comes back when the editor closes.
    HideNavigationBar()

    val previewSourceWidth = previewBitmap?.width?.takeIf { it > 0 }
        ?: currentPhoto.width.takeIf { it > 0 }
    val previewSourceHeight = previewBitmap?.height?.takeIf { it > 0 }
        ?: currentPhoto.height.takeIf { it > 0 }
    // Save is developing the RAW in full before the edit saves.
    // How long the last develop with each engine took in this session, for the status line's estimate.
    // The RAW develop's stages as they pass, timed per kind (preview or full) and engine for the next estimate.
    val developStage by remember { RawDemosaicProcessor.getInstance().developStage }.collectAsState()
    LaunchedEffect(developStage) {
        val now = SystemClock.uptimeMillis()
        ui.lastStage?.let { ui.rawStageMs[ui.stageKind + it.name] = now - ui.stageStartedAt }
        if (ui.lastStage == null && developStage != null) {
            ui.developStartedAt = now
            val full = ui.savingRaw || viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id)
            ui.stageKind = "${if (full) "full" else "preview"}:${viewModel.editRawRenderingEngine.value.name}:"
        }
        ui.lastStage = developStage
        ui.stageStartedAt = now
    }

    val isRefreshingRawPreview = viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id)

    // One heavy GPU job at a time: while a blur, a RAW develop, AI denoise or noise reduction runs, a press that
    // would start another says which is running and does nothing.
    // [allowBlur]: Portrait's own controls may change a blur still rendering; the newer one replaces it.
    fun gpuBusyNote(allowBlur: Boolean = false): Int? = when {
        isBokehProcessing && !allowBlur -> R.string.editor_busy_blur
        developStage != null || viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id) -> R.string.editor_busy_raw
        isAiDenoising -> R.string.editor_busy_ai
        isPhotonDenoising -> R.string.editor_busy_noise
        else -> null
    }
    fun blockedByGpu(allowBlur: Boolean = false): Boolean =
        gpuBusyNote(allowBlur)?.let { noteLine.say(it); true } ?: false
    fun placeFocusIfFree(position: Offset) {
        if (!blockedByGpu(allowBlur = true)) placeFocus(position)
    }

    val rawRefresh = rawPreviewRefresh(viewModel, ui, currentEditSourcePhoto, isRaw, hdPreviewState, scope)
    fun requestRawPreviewRefresh(forceRegeneratePhotonPgtm: Boolean = false) = rawRefresh(forceRegeneratePhotonPgtm)

    // Undo and Redo show where the change was: the section (and group) of a changed setting, unless one is in view.
    fun showStepPlace(keys: Set<String>) {
        val places = keys.mapNotNull { k ->
            val g = EditorGroup.entries.firstOrNull { k in it.keys }
            val s = g?.section ?: EditorSection.entries.firstOrNull { k in it.keys }
                ?: EditorSetting.entries.firstOrNull { it.key == k }?.section ?: return@mapNotNull null
            s to generateSequence(g) { it.parent }.toList().asReversed().joinToString("/") { it.name }
        }
        val shown = places.any { (s, path) ->
            s == section && (groupPath == path || path.isNotEmpty() && groupPath.startsWith("$path/"))
        }
        if (shown) return
        places.firstOrNull()?.let { (s, path) ->
            section = s
            groupPath = path
        }
    }

    // Undo ([back]) and Redo: one finished change at a time.
    fun stepHistory(back: Boolean) {
        if (isSaving) return
        viewModel.recordEditStep()
        val keys = viewModel.editHistoryStepKeys(back)
        if (keys == null) {
            noteLine.say(if (back) R.string.editor_nothing_to_undo else R.string.editor_nothing_to_redo)
            return
        }
        val heavy = setOf("aiDenoise", "aperture", "bokeh", "depth", "depthOfField", "focus", "noiseReduction", "chromaNoiseReduction")
        if (keys.any { it in heavy || it.startsWith("raw") } && blockedByGpu(allowBlur = true)) return
        val rawBefore = viewModel.editRawState()
        viewModel.stepEditHistory(back)
        ui.curveSelected = null
        ui.curveDrag = null
        explainKey = null
        if (!isRaw) {
            noiseReleased = viewModel.editNoiseReduction.value to viewModel.editChromaNoiseReduction.value
        } else if (viewModel.editRawState() != rawBefore || "noiseReduction" in keys || "chromaNoiseReduction" in keys) {
            viewModel.persistCurrentRawEditMetadata(currentEditSourcePhoto) { requestRawPreviewRefresh() }
        }
        showStepPlace(keys)
    }
    // The handlers below hold this one reference rather than every value the function reads, as with
    // previewSignature.
    val historyStep: (Boolean) -> Unit = ::stepHistory

    val rawDcpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val photo = editSourcePhoto ?: return@rememberLauncherForActivityResult
        scope.launch {
            val importedDcps = viewModel.importRawDcps(uris)
            val failedCount = uris.size - importedDcps.size
            importedDcps.lastOrNull()?.let {
                // The library copy is for every photo at once; choosing it for this photo is an edit like any other.
                viewModel.saveRawDcpSelection(photo, it.id) { requestRawPreviewRefresh() }
            }
            noteLine.say(
                when {
                    importedDcps.isEmpty() -> R.string.editor_dcp_import_failed
                    failedCount > 0 -> R.string.editor_dcp_import_partial
                    else -> R.string.editor_dcp_imported
                }
            )
        }
    }

    val rawBusy = isRefreshingRawPreview || ui.pendingRawRefresh || ui.previewDeveloping || ui.previewAgain
    // A RAW state seen before shows its earlier look at once; the develop behind it replaces it when done.
    LaunchedEffect(editState, ui.developedRaw, rawBusy) {
        if (!isRaw) return@LaunchedEffect
        val look = rawLook(editState)
        ui.rawLookShown = look != null && !rawDeveloped()
        if (ui.rawLookShown) {
            previewBitmap = look
            ui.previewFor = previewSignature(false)
        }
    }
    // Once the photo shows its current state, a small copy is kept (the last six).
    LaunchedEffect(previewBitmap, isLoadingPreview, rawBusy, editChainBusy, editState, ui.developedRaw) {
        val shown = previewBitmap ?: return@LaunchedEffect
        if (!isRaw || rawBusy || editChainBusy || isLoadingPreview || showOrigin || !rawDeveloped()) return@LaunchedEffect
        if (section == EditorSection.Crop || section == EditorSection.Portrait) return@LaunchedEffect
        if (rawLook(editState) != null) return@LaunchedEffect
        val scale = 1440f / max(shown.width, shown.height)
        val small = if (scale >= 1f) shown else withContext(Dispatchers.Default) {
            Bitmap.createScaledBitmap(shown, (shown.width * scale).roundToInt(), (shown.height * scale).roundToInt(), true)
        }
        ui.rawLooks.add(0, editState to small)
        while (ui.rawLooks.size > 6) ui.rawLooks.removeAt(ui.rawLooks.lastIndex)
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .animateContentSize()
        ) {
            ReferencePhoto(userPreferences.referencePhotoUrl) {
                scope.launch { userPreferencesRepository.update { this.referencePhotoUrl = null } }
            }

            // The photo, placed by its shape: above the tool block while editing, the whole screen in full view.
            // Crop keeps the old frame tool, over the area between the edge icons and the control layer.
            val screenW = with(density) { viewportSize.width.toDp().value }
            val screenH = with(density) { viewportSize.height.toDp().value }
            val photoAspect = if (previewSourceWidth != null && previewSourceHeight != null) {
                previewSourceWidth.toFloat() / previewSourceHeight
            } else {
                3f / 4f
            }
            // Switching to or from Crop shows the new view once its picture shows the current edit; until then
            // the previous view stays, so an old picture never flashes up.
            val currentSignature = previewSignature(false)
            val viewReady = if (cropView) {
                cropPreviewBitmap != null && ui.cropFor == currentSignature
            } else {
                previewBitmap != null && ui.previewFor == currentSignature
            }
            val showCrop = if (viewReady) cropView else ui.lastShownCrop && cropPreviewBitmap != null
            SideEffect { ui.lastShownCrop = showCrop }
            // Full view: the box is the whole screen and the photo fits inside it, so a zoom can fill the screen.
            val targetRect = when {
                showCrop -> EditorRect(0f, EDITOR_EDGE_DP, screenW, screenH - EDITOR_BLOCK_DP - EDITOR_CONTROL_DP - EDITOR_EDGE_DP)
                fullView -> EditorRect(0f, 0f, screenW, screenH)
                else -> editorPhotoRect(photoAspect, screenW, screenH, false)
            }
            val photoMotion = tween<Float>(250)
            val photoX by animateFloatAsState(targetRect.x, photoMotion, label = "photoX")
            val photoY by animateFloatAsState(targetRect.y, photoMotion, label = "photoY")
            val photoW by animateFloatAsState(targetRect.w, photoMotion, label = "photoW")
            val photoH by animateFloatAsState(targetRect.h, photoMotion, label = "photoH")
            // Curve: the part of the photo box clear of the edge icons above and the control layer below.
            val curveFree = with(density) {
                Rect(
                    0f, (EDITOR_EDGE_DP - photoY).coerceAtLeast(0f).dp.toPx(), photoW.dp.toPx(),
                    (screenH - EDITOR_BLOCK_DP - EDITOR_CONTROL_DP - photoY).coerceAtMost(photoH).dp.toPx()
                )
            }
            val curveFreeNow by rememberUpdatedState(curveFree)

            Box(
                modifier = Modifier
                    .offset(photoX.dp, photoY.dp)
                    .size(photoW.dp, photoH.dp)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        // Any finger on the photo fades the edge icons.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            fingerOnPhoto = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                fingerOnPhoto = false
                            }
                        }
                    }
                    .pointerInput(isZoomed, currentPhoto.isVideo, section, fullView) {
                        if (
                            !isZoomed &&
                            !currentPhoto.isVideo &&
                            section == EditorSection.Look &&
                            !fullView
                        ) {
                            val switchThreshold = max(size.width * 0.25f, 96.dp.toPx())
                            val flingVelocity = 900.dp.toPx()
                            val minFlingDistance = 48.dp.toPx()
                            val touchSlop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, start)
                                var swipeDecided = false
                                var swipeTracking = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) {
                                        // 多指（缩放）时放弃切换
                                        if (swipeTracking) lutSwipeState.cancel()
                                        break
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (swipeTracking) {
                                            val direction = lutSwipeState.release(
                                                velocityX = velocityTracker.calculateVelocity().x,
                                                flingVelocity = flingVelocity,
                                                minFlingDistance = minFlingDistance,
                                            )
                                            val selectedLut = when {
                                                direction > 0 -> viewModel.switchToPreviousLut()
                                                direction < 0 -> viewModel.switchToNextLut()
                                                else -> null
                                            }
                                            if (selectedLut != null) {
                                                lutSwipeState.commit(direction, selectedLut.getName())
                                            } else {
                                                lutSwipeState.cancel()
                                            }
                                        }
                                        break
                                    }
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val dx = change.position.x - start.x
                                    val dy = change.position.y - start.y
                                    if (!swipeDecided && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                        swipeDecided = true
                                        val nextLut = viewModel.peekNextLut()
                                        // 先确认以横向为主，竖向/斜向手势不触发切换
                                        val horizontal = abs(dx) > abs(dy) * 1.5f
                                        if (horizontal && nextLut != null && nextLut.id != editLutId) {
                                            swipeTracking = true
                                            lutSwipeState.begin(
                                                width = size.width,
                                                threshold = switchThreshold,
                                                previousName = viewModel.peekPreviousLut()?.getName(),
                                                nextName = nextLut.getName(),
                                            )
                                        } else {
                                            break
                                        }
                                    }
                                    if (swipeTracking) {
                                        change.consume()
                                        lutSwipeState.drag(sign(dx) * max(abs(dx) - touchSlop, 0f))
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(section, fullView) {
                        if (section != EditorSection.Portrait || fullView || currentPhoto.isVideo) return@pointerInput
                        val grabRadius = 30.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (currentPortraitQuick) return@awaitEachGesture
                            val ring = ringCenter(viewModel.focusPointOnPhoto()) ?: return@awaitEachGesture
                            if ((down.position - ring).getDistance() > grabRadius) return@awaitEachGesture
                            // The press is the ring's: zoom and pan leave it alone, and so do tap and long press.
                            ringGrab = true
                            down.consume()
                            var position = down.position
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.size > 1) return@awaitEachGesture  // a second finger: no move
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    change.consume()
                                    if (!change.pressed) break
                                    position = change.position
                                    ringDrag = position
                                }
                                // One render, when the finger lifts.
                                placeFocusIfFree(position)
                            } finally {
                                ringGrab = false
                                ringDrag = null
                            }
                        }
                    }
                    .pointerInput(curveOpen, fullView) {
                        if (!curveOpen || fullView || currentPhoto.isVideo) return@pointerInput
                        val grabRadius = CURVE_GRAB_DP.dp.toPx()
                        val inset = CURVE_INSET_DP.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val box = curveBox(contentBounds, curveFreeNow, inset)
                                ?: return@awaitEachGesture
                            val channel = curveChannel
                            val points = curvePoints(editPhotoRecipeParams ?: editLutRecipeParams, channel)
                            val slop = viewConfiguration.touchSlop
                            val index = nearestCurvePoint(points, box, down.position, grabRadius)
                            if (index < 0) {
                                // Empty space: a still tap adds a point where it lifts; a move or a second finger
                                // is the photo's pan or zoom.
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.size > 1) return@awaitEachGesture
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if ((change.position - down.position).getDistance() > slop) return@awaitEachGesture
                                    if (change.pressed) continue
                                    val x = (change.position.x - box.left) / box.width
                                    val y = (1f - (change.position.y - box.top) / box.height).coerceIn(0f, 1f)
                                    when {
                                        x !in 0.02f..0.98f -> noteLine.say(R.string.editor_curve_hint)
                                        points.any { abs(it.first - x) < 0.04f } -> noteLine.say(R.string.editor_curve_no_room)
                                        else -> {
                                            val added = (points + (x to y)).sortedBy { it.first }
                                            setCurve(channel, added)
                                            ui.curveSelected = added.indexOfFirst { it.first == x }
                                        }
                                    }
                                    return@awaitEachGesture
                                }
                            }
                            // A press on a point is the point's: it is selected, and a drag moves it, slower than
                            // the finger so the finger doesn't hide it.
                            ringGrab = true
                            down.consume()
                            ui.curveSelected = index
                            val origin = points[index]
                            var moved = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.size > 1) return@awaitEachGesture  // a second finger: no move
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    change.consume()
                                    if (!change.pressed) break
                                    val delta = change.position - down.position
                                    if (!moved && delta.getDistance() <= slop) continue
                                    moved = true
                                    val current = ui.curveDrag ?: points
                                    val x = safeCoerceX(origin.first + delta.x / box.width * CURVE_DRAG_SENSITIVITY, index, current)
                                    val y = (origin.second - delta.y / box.height * CURVE_DRAG_SENSITIVITY).coerceIn(0f, 1f)
                                    ui.curveDrag = current.toMutableList().also { it[index] = x to y }
                                }
                                ui.curveDrag?.let { setCurve(channel, it) }
                            } finally {
                                ringGrab = false
                                ui.curveDrag = null
                            }
                        }
                    }
                    .pointerInput(currentPhoto.isVideo) {
                        if (!currentPhoto.isVideo) awaitPointerEventScope {
                            while (true) {
                                // 确认第一个手指按下，且当前只有一个指针
                                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                if (downEvent.type == PointerEventType.Press && downEvent.changes.size == 1 && !ringGrab) {
                                    // A press while a tap waits out the double-tap window is the double tap's
                                    // second half: the zoom handles it, and the waiting tap is dropped.
                                    val secondTap = ui.pendingPhotoTap?.isActive == true
                                    ui.pendingPhotoTap?.cancel()
                                    val touchSlop = viewConfiguration.touchSlop
                                    val initialPosition = downEvent.changes[0].position
                                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                    var upEvent: PointerEvent? = null
                                    var isMultiTouch = false
                                    var isMoved = false

                                    // 期间如果出现第二个手指或位移过大，立即标志并退出
                                    withTimeoutOrNull(longPressTimeout) {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.changes.size > 1) {
                                                isMultiTouch = true
                                                break
                                            }

                                            val currentPosition = event.changes[0].position
                                            if ((currentPosition - initialPosition).getDistance() > touchSlop) {
                                                isMoved = true
                                                break
                                            }

                                            if (event.type == PointerEventType.Release) {
                                                upEvent = event
                                                break
                                            }
                                        }
                                    }

                                    // 如果既没有多指操作也没有明显位移，才根据结果执行逻辑
                                    if (!isMultiTouch && !isMoved) {
                                        if (upEvent != null) {
                                            if (!secondTap) {
                                                val tapAt = upEvent!!.changes[0].position
                                                ui.pendingPhotoTap = scope.launch {
                                                    delay(300)
                                                    // A tap opens full view, and a tap in full view comes back. In
                                                    // Portrait a tap sets the focus instead; full view is the edge icon.
                                                    when {
                                                        fullView -> fullView = false
                                                        section == EditorSection.Portrait -> placeFocusIfFree(tapAt)
                                                        section == EditorSection.Crop -> noteLine.say(R.string.editor_crop_tap_hint)
                                                        // In Curve a tap adds a point (the curve's own handler).
                                                        groupPath.substringAfterLast("/") == EditorGroup.Curve.name -> Unit
                                                        else -> fullView = true
                                                    }
                                                }
                                            }
                                        } else {
                                            // 确认为长按：显示原图. Not in Crop or Curve: there a held finger is about to
                                            // drag the frame or add a point.
                                            if (section != EditorSection.Crop && groupPath.substringAfterLast("/") != EditorGroup.Curve.name) {
                                                showOrigin = true
                                            }
                                            // 继续监控直到手指抬起，或者变成多指（开始缩放）
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.type == PointerEventType.Release || event.changes.size > 1) {
                                                    break
                                                }
                                            }
                                            showOrigin = false
                                        }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // The blur on screen against the blur asked for: the picture's base is the chain's small copy (made
                // with the chain's blur settings) or the photo's files (the settings it opened with). While they
                // differ the cheap blur shows, from the frame a setting changes, so the old blur never flashes up.
                val shownSignature = ui.previewFor as? PreviewRenderSignature
                val shownBlur: Any? = when {
                    shownSignature == null -> Unit
                    shownSignature.editChainBase == null -> viewModel.openBlurKey()
                    shownSignature.editChainBase === editChainBase -> viewModel.editChainBlurKey
                    else -> Unit
                }
                EditorPhotoContent(
                    viewModel = viewModel,
                    ui = ui,
                    currentPhoto = currentPhoto,
                    currentEditSourcePhoto = currentEditSourcePhoto,
                    videoSourceProfile = videoSourceProfile,
                    editLutConfig = editLutConfig,
                    recipe = editPhotoRecipeParams ?: editLutRecipeParams,
                    showOrigin = showOrigin,
                    fullView = fullView,
                    showCrop = showCrop,
                    cropPreviewBitmap = cropPreviewBitmap,
                    previewBitmap = previewBitmap,
                    editCropRect = editCropRect,
                    editCropAspectOption = editCropAspectOption,
                    editRotationDegrees = editRotationDegrees,
                    editStraightenDegrees = editStraightenDegrees,
                    editMirrorHorizontal = editMirrorHorizontal,
                    lutSwipeState = lutSwipeState,
                    blurStale = portraitOpen && shownBlur != viewModel.currentBlurKey(),
                    portraitOpen = portraitOpen,
                    portraitQuick = portraitQuick,
                    previewStrength = previewStrength,
                    blurLayers = blurLayers,
                    awaitingBlur = awaitingBlur,
                    apertureDrag = apertureDrag,
                    apertureScroll = apertureScroll,
                    dofDrag = dofDrag,
                    editComputationalAperture = editComputationalAperture,
                    contentBounds = contentBounds,
                    ringDrag = ringDrag,
                    focusCenter = ringCenter(focusOnPhoto),
                    curveOpen = curveOpen,
                    curveFree = curveFree,
                    curveChannel = curveChannel,
                    onZoomChange = { isZoomed = it },
                    onContentBounds = { contentBounds = it },
                )
            }

            if (!fullView) {
                val effectiveRecipe = editPhotoRecipeParams ?: editLutRecipeParams
                val intensity = effectiveRecipe.lutIntensity
                val groupStack = groupPath.split("/").filter { it.isNotEmpty() }.map(EditorGroup::valueOf)
                val group = groupStack.lastOrNull()
                val levelKey = (listOf(section.name) + groupStack.map { it.name }).joinToString("/")

                fun settingValue(s: EditorSetting): Float = when (s) {
                    EditorSetting.RawExposure -> editRawExposureCompensation
                    EditorSetting.RawHighlights -> editRawHighlightsAdjustment
                    EditorSetting.RawShadows -> editRawShadowsAdjustment
                    EditorSetting.Whites -> editRawWhitePointCorrection
                    EditorSetting.Blacks -> editRawBlackPointCorrection
                    EditorSetting.NoiseReduction -> editNoiseReduction
                    EditorSetting.ColorNoise -> editChromaNoiseReduction
                    EditorSetting.AiDenoise -> if (editAiDenoiseEnabled) 1f else 0f
                    EditorSetting.Aperture -> apertureDrag ?: editComputationalAperture ?: 0f
                    EditorSetting.DepthOfField -> dofDrag ?: (editDepthOfField ?: 0).toFloat()
                    EditorSetting.Focus, EditorSetting.Bokeh, EditorSetting.Depth -> 0f
                    else -> s.param!!.getValue(effectiveRecipe)
                }

                // Where a setting's reset returns: its value when the editor opened.
                fun openValue(s: EditorSetting): Float {
                    if (s == EditorSetting.DepthOfField) return ((viewModel.editOpenValue(s.key) as? Int) ?: 0).toFloat()
                    val open = viewModel.editOpenValue(s.key) as? Float ?: s.param?.defaultValue ?: 0f
                    // A shot's own aperture lands on the nearest stop of the ring.
                    return if (s.kind == SettingKind.Aperture && open !in ApertureStops) {
                        ApertureStops.filter { it > 0f }.minBy { abs(it - open) }
                    } else {
                        open
                    }
                }

                fun setSetting(s: EditorSetting, v: Float) {
                    when (s) {
                        EditorSetting.RawExposure -> viewModel.setRawExposureCompensation(v)
                        EditorSetting.RawHighlights -> viewModel.setRawHighlightsAdjustment(v)
                        EditorSetting.RawShadows -> viewModel.setRawShadowsAdjustment(v)
                        EditorSetting.Whites -> viewModel.setRawWhitePointCorrection(v)
                        EditorSetting.Blacks -> viewModel.setRawBlackPointCorrection(v)
                        EditorSetting.NoiseReduction -> viewModel.setNoiseReduction(v)
                        EditorSetting.ColorNoise -> viewModel.setChromaNoiseReduction(v)
                        // The aperture only moves the ruler; the blur renders once, on release.
                        EditorSetting.Aperture -> apertureDrag = v
                        EditorSetting.DepthOfField -> dofDrag = v
                        else -> s.param?.let { param ->
                            val recipe = editPhotoRecipeParams ?: editLutRecipeParams
                            // The effects go through their own group, as they always have: it also clears the
                            // retired legacy halation value a look may still carry.
                            val effect = EffectType.entries.firstOrNull { it.recipeParam == param }
                            viewModel.setPhotoRecipeParams(
                                effect?.setValue(recipe.toEffectParams(), v)?.applyTo(recipe) ?: param.setValue(recipe, v)
                            )
                        }
                    }
                }

                // RAW tone values and noise reduction are saved and rendered once, when the finger lifts.
                fun releaseSetting(s: EditorSetting) {
                    when {
                        s.kind == SettingKind.RawTone ->
                            viewModel.persistCurrentRawEditMetadata(currentEditSourcePhoto) { requestRawPreviewRefresh() }
                        s.kind == SettingKind.Denoise && isRaw ->
                            viewModel.persistCurrentRawEditMetadata(currentEditSourcePhoto) { success ->
                                if (success) requestRawPreviewRefresh()
                            }
                        // JPEG noise reduction: the edit chain denoises its small copy with the released strengths.
                        s.kind == SettingKind.Denoise -> noiseReleased = editNoiseReduction to editChromaNoiseReduction
                        s.kind == SettingKind.DepthOfField -> dofDrag?.let { v ->
                            awaitingBlur = true  // the cheap blur stays until the new blur shows
                            dofDrag = null
                            viewModel.setDepthOfField(v.roundToInt())
                        }
                        s.kind == SettingKind.Aperture -> apertureDrag?.let { v ->
                            awaitingBlur = true  // the cheap blur stays until the new blur shows
                            apertureDrag = null
                            viewModel.setComputationalAperture(v.takeIf { it > 0f })
                        }
                    }
                }

                fun setAiDenoise(enabled: Boolean) {
                    if (isAiDenoising || enabled == editAiDenoiseEnabled) return
                    // The model is downloaded (or imported) the first time AI denoise is switched on.
                    if (enabled && !AiDenoiseModelManager.isInstalled(context)) pendingAiDenoise = true
                    else viewModel.setAiDenoiseEnabled(enabled)
                }

                val faceText = stringResource(R.string.editor_face)
                val pointText = stringResource(R.string.editor_point)
                val naturalText = stringResource(R.string.gallery_bokeh_style_natural)
                val swirlText = stringResource(R.string.gallery_bokeh_style_default)
                val bubbleText = stringResource(R.string.gallery_bokeh_style_bubble)
                val quickText = stringResource(R.string.portrait_blur_quick)
                val depthText = stringResource(R.string.portrait_blur_depth)
                val blurOff = (apertureDrag ?: editComputationalAperture ?: 0f) <= 0f
                val openFocus = viewModel.editOpenValue("focus") as? Pair<*, *>
                val focusMoved = (editFocusX to editFocusY) != openFocus
                val quick = portraitQuick
                // Aperture is the blur's on/off switch: turning it on starts at the shot's own aperture.
                fun turnBlurOn() {
                    val open = viewModel.editOpenValue("aperture") as? Float
                    viewModel.setComputationalAperture(open?.takeIf { it > 0f } ?: 2f)
                }

                fun chooseDepth() {
                    if (DepthModelManager.isInstalled(context)) {
                        viewModel.setPortraitQuick(false)
                        if (blurOff) turnBlurOn()
                    } else {
                        // The depth model is downloaded (or imported) the first time it is chosen.
                        pendingDepth = true
                        showDepthModelDownloadDialog = true
                    }
                }


                fun choiceOptions(s: EditorSetting): List<EditorChoice> = when (s) {
                    // Face: the point saved with the shot. Point: a place you tapped or dragged the ring to.
                    EditorSetting.Focus -> listOf(
                        EditorChoice(faceText, AppIcons.MoodSmileLine, !focusMoved) {
                            viewModel.restoreFocusPoint(openFocus?.first as? Float, openFocus?.second as? Float)
                        },
                        EditorChoice(pointText, AppIcons.FocusCenteredLine, focusMoved) {
                            if (!focusMoved) noteLine.say(R.string.editor_focus_tap_hint)
                        },
                    )
                    EditorSetting.Bokeh -> listOf(
                        Triple(BokehStyle.NATURAL, naturalText, AppIcons.CircleLine),
                        Triple(BokehStyle.DEFAULT, swirlText, AppIcons.RotateClockwiseLine),
                        Triple(BokehStyle.BUBBLE, bubbleText, AppIcons.CircleDotLine),
                    ).map { (style, label, icon) ->
                        EditorChoice(label, icon, editBokehStyle == style) { viewModel.setBokehStyle(style) }
                    }
                    // Blur: picking a mode while the blur is Off turns it on in that mode; tapping the mode in use
                    // turns the blur off.
                    EditorSetting.Depth -> listOf(
                        EditorChoice(quickText, AppIcons.BoltLine, !blurOff && quick) {
                            when {
                                blurOff -> {
                                    viewModel.setPortraitQuick(true)
                                    turnBlurOn()
                                }
                                quick -> viewModel.setComputationalAperture(null)
                                else -> viewModel.setPortraitQuick(true)
                            }
                        },
                        EditorChoice(depthText, AppIcons.StackLine, !blurOff && !quick) {
                            when {
                                !blurOff && !quick -> viewModel.setComputationalAperture(null)
                                else -> chooseDepth()
                            }
                        },
                    )
                    else -> emptyList()
                }

                fun resetChoice(s: EditorSetting) {
                    when (s) {
                        EditorSetting.Focus -> viewModel.restoreFocusPoint(openFocus?.first as? Float, openFocus?.second as? Float)
                        EditorSetting.Bokeh -> (viewModel.editOpenValue("bokeh") as? BokehStyle)?.let(viewModel::setBokehStyle)
                        EditorSetting.Depth -> viewModel.setPortraitQuick(viewModel.editOpenValue("depth") as? Boolean)
                        else -> Unit
                    }
                }

                val settings = if (group == null) {
                    // Depth of field acts on the depth map; Quick has none.
                    EditorSetting.forSection(section, isRaw, currentPhoto.isVideo)
                        .filterNot { it == EditorSetting.DepthOfField && quick }
                } else {
                    emptyList()
                }
                // Light › RAW and its Sensor group: saved to the photo and re-rendered from the RAW file.
                fun commitRaw() {
                    viewModel.persistCurrentRawEditMetadata(currentEditSourcePhoto) { requestRawPreviewRefresh() }
                }
                val rawLevel = if (isRaw && (group == EditorGroup.Raw || group?.parent == EditorGroup.Raw)) {
                    rawGroupItems(
                        viewModel = viewModel,
                        photo = currentEditSourcePhoto,
                        level = group,
                        changedKeys = changedKeys,
                        imported = currentEditMetadata?.isImported == true,
                        embeddedProfiles = embeddedDngProfileUiState.options,
                        activeEmbeddedProfileId = activeEmbeddedDngProfileId,
                        availableLuts = availableLuts,
                        commit = ::commitRaw,
                        refresh = { requestRawPreviewRefresh() },
                        reRender = {
                            requestRawPreviewRefresh(forceRegeneratePhotonPgtm = true)
                            noteLine.say(R.string.editor_rerendering)
                        },
                        importDcp = { rawDcpLauncher.launch(arrayOf("*/*")) },
                        deleteDcp = { ui.deleteDcpAsk = it },
                    )
                } else {
                    null
                }
                val rawItems = rawLevel?.items.orEmpty()
                // A film change can leave a Fotufilm group with nothing in it: go up to RAW and say so.
                val filmSection = group?.filmSection
                if (filmSection != null && rawLevel != null && rawLevel.filmSections.isNotEmpty() && filmSection !in rawLevel.filmSections) {
                    LaunchedEffect(filmSection) {
                        groupPath = EditorGroup.Raw.name
                        noteLine.say(R.string.editor_film_section_gone)
                    }
                }

                val groups = if (group == null) {
                    EditorGroup.forSection(section, isRaw)
                } else {
                    EditorGroup.entries.filter {
                        it.parent == group && (!it.rawOnly || isRaw) &&
                            (it.filmSection == null || rawLevel?.filmSections?.containsKey(it.filmSection) == true)
                    }
                }
                val groupTiles = groupTiles(groups, rawLevel?.filmSections, changedKeys)
                // Crop: the photo's size once turned (Original's shape), and the frame's shape on it.
                val cropBaseW = currentEditSourcePhoto.metadata?.width?.takeIf { it > 0 } ?: currentEditSourcePhoto.width.coerceAtLeast(1)
                val cropBaseH = currentEditSourcePhoto.metadata?.height?.takeIf { it > 0 } ?: currentEditSourcePhoto.height.coerceAtLeast(1)
                val (turnedW, turnedH) = PostEditGeometry.rotatedDimensions(cropBaseW, cropBaseH, editRotationDegrees)
                val (straightW, straightH) = PostEditGeometry.straightenedDimensions(turnedW, turnedH, editStraightenDegrees)
                val frameShape = editCropRect?.takeIf { it.height() > 0f }?.let { it.width() * straightW / (it.height() * straightH) }
                    ?: (turnedW.toFloat() / turnedH)
                // The ratio chips: the common ones, then the user's own camera ratios. Each is turned to the photo.
                val customCameraRatios by cameraViewModel.availablePhotoAspectRatios.collectAsState()
                val ratioChoices = listOf(
                    AspectRatio.RATIO_1_1, AspectRatio.RATIO_4_3, AspectRatio.RATIO_3_2, AspectRatio.RATIO_16_9,
                    AspectRatio.RATIO_21_9, AspectRatio.XPAN,
                ) + customCameraRatios.filterNot { it in AspectRatio.entries }
                fun chooseRatio(r: AspectRatio) {
                    val landscape = turnedW >= turnedH
                    viewModel.setCropAspectOption(
                        if (landscape) CropAspectOption.FromAspectRatio(r)
                        else CropAspectOption.Custom(r.heightRatio.toFloat(), r.widthRatio.toFloat())
                    )
                }
                val openStraighten = viewModel.editOpenValue("straighten") as? Float ?: 0f
                // Back to the ratio and frame the editor opened with; if the photo was turned since, the old frame
                // no longer fits, so the opening ratio is fitted afresh.
                fun restoreCropRatio() {
                    val openOption = CropAspectOption.fromShapeKey(viewModel.editOpenValue("cropRatio"))
                    val sameGeometry = editRotationDegrees == viewModel.editOpenValue("rotation") &&
                        editMirrorHorizontal == viewModel.editOpenValue("mirror") &&
                        editStraightenDegrees == openStraighten
                    if (sameGeometry) {
                        viewModel.editCropAspectOption.value = openOption
                        viewModel.setCropRect(viewModel.editOpenValue("crop") as? RectF)
                    } else {
                        viewModel.setCropAspectOption(openOption)
                    }
                }

                // Curve: each channel against its curve when the editor opened.
                val openCurves = viewModel.editOpenValue("recipe.curves")
                fun curveChanged(c: CurveChannel): Boolean =
                    effectiveRecipe.getCurvePoints(c)?.toList() != (openCurves as? List<*>)?.getOrNull(c.ordinal)
                val curveNow = curvePoints(effectiveRecipe, curveChannel)
                val curvePoint = ui.curveSelected?.let { curveNow.getOrNull(it) }
                fun deleteCurvePoint() {
                    when {
                        curvePoint == null -> noteLine.say(R.string.editor_curve_select_point)
                        curveNow.size <= 2 -> noteLine.say(R.string.editor_curve_two_points)
                        else -> {
                            setCurve(curveChannel, curveNow - curvePoint)
                            ui.curveSelected = null
                        }
                    }
                }

                // Mixer, Grading, Calibration: the numbers of the picked colour, range or primary.
                fun groupValuesOf(g: EditorGroup?, pick: Int): List<GroupValue> = when (g) {
                    EditorGroup.Mixer -> mixerValues(pick)
                    EditorGroup.Grading -> gradingValues(pick)
                    EditorGroup.Calibration -> calibrationValues(pick)
                    else -> emptyList()
                }
                val groupValues = groupValuesOf(
                    group,
                    when (group) {
                        EditorGroup.Mixer -> mixerPick
                        EditorGroup.Grading -> gradingPick
                        else -> calibrationPick
                    }
                )
                fun groupOpen(v: GroupValue): Float = v.open(viewModel.editOpenValue(v.openKey))
                fun groupChanged(v: GroupValue): Boolean = v.get(effectiveRecipe) != groupOpen(v)
                fun setGroupValue(v: GroupValue, x: Float) {
                    viewModel.setPhotoRecipeParams(v.set(editPhotoRecipeParams ?: editLutRecipeParams, x))
                }

                val values = when {
                    rawItems.isNotEmpty() -> rawItems.map {
                        EditorValue(it.key, it.name, it.value, it.icon, it.changed, wide = it.ruler == null)
                    } + groupTiles
                    groupValues.isNotEmpty() -> groupValueTiles(groupValues, effectiveRecipe, ::groupChanged)
                    group == EditorGroup.Curve -> curveValues(
                        point = curvePoint,
                        changed = curveChanged(curveChannel),
                        deletable = curvePoint != null && curveNow.size > 2,
                    )
                    group != null -> emptyList()
                    section == EditorSection.Crop -> cropValues(
                        option = editCropAspectOption,
                        ratioChoices = ratioChoices,
                        frameShape = frameShape,
                        straighten = editStraightenDegrees,
                        rotation = editRotationDegrees,
                        mirror = editMirrorHorizontal,
                        changedKeys = changedKeys,
                    )
                    section == EditorSection.Look -> lookValues(
                        lookName = availableLuts.find { it.id == editLutId }?.getName(),
                        intensity = intensity,
                        isVideo = currentPhoto.isVideo,
                        isMotionPhoto = currentPhoto.isMotionPhoto,
                        frameName = availableFrames.find { it.id == editFrameId }?.name,
                        preset = allPresets.find { it.id == viewModel.appliedPresetId }
                            ?.let { presetDisplayName(it) + if (viewModel.appliedPresetChanged()) "*" else "" },
                        applyEffectsToVideo = applyEffectsToVideo,
                        changedKeys = changedKeys,
                    )
                    else -> settingValues(
                        settings = settings,
                        valueOf = ::settingValue,
                        choiceLabel = { s -> choiceOptions(s).firstOrNull { it.selected }?.label.orEmpty() },
                        quick = quick,
                        blurOff = blurOff,
                        aiDenoiseEnabled = editAiDenoiseEnabled,
                        isAiDenoising = isAiDenoising,
                        aiDenoiseProgress = aiDenoiseProgress,
                        changedKeys = changedKeys,
                    ) + groupTiles
                }
                val selectable = values.filterNot { it.group || it.key in setOf("rotation", "mirror", "curveDelete") }
                val selectedKey = selectable.firstOrNull { it.key == selections[levelKey] }?.key ?: selectable.firstOrNull()?.key
                val selectedSetting = settings.firstOrNull { it.key == selectedKey }
                val selectedGroupValue = groupValues.firstOrNull { it.key == selectedKey }
                val selectedRawItem = rawItems.firstOrNull { it.key == selectedKey }

                fun sectionChanged(s: EditorSection): Boolean = when (s) {
                    EditorSection.Light, EditorSection.Color, EditorSection.Effects, EditorSection.Detail ->
                        EditorSetting.forSection(s, isRaw, currentPhoto.isVideo).any { it.key in changedKeys } ||
                            EditorGroup.forSection(s, isRaw).any { g -> g.keys.any { it in changedKeys } }
                    else -> s.keys.any { it in changedKeys }
                }

                EditorEdgeIcons(
                    busy = fingerOnPhoto || fingerOnRuler,
                    rendering = isLoadingPreview || isBokehProcessing || isPhotonDenoising ||
                        isRefreshingRawPreview || isAiDenoising || isSaving || rawBusy || editChainBusy,
                    hd = hdPreview,
                    onHd = onHd@{
                        if (blockedByGpu()) return@onHd
                        viewModel.setEditorHdPreview(!hdPreview)
                        noteLine.say(if (hdPreview) R.string.editor_hd_off else R.string.editor_hd_on)
                    },
                    undoAvailable = viewModel.editHistoryIndex > 0,
                    redoAvailable = viewModel.editHistoryIndex < viewModel.editHistoryLast,
                    fullView = false,
                    saving = isSaving,
                    onCancel = ::requestLeave,
                    onUndo = { historyStep(true) },
                    onRedo = { historyStep(false) },
                    onFullView = { fullView = true },
                    onCompare = { showOrigin = it },
                    onSave = {
                        val currentLut = availableLuts.find { it.id == editLutId }
                        if (currentLut?.isVip == true && !isPurchased) {
                            viewModel.showPaymentDialog = true
                        } else if (isRefreshingRawPreview) {
                            noteLine.say(R.string.editor_raw_busy)
                        } else {
                            isSaving = true
                            // AI denoise, the blur and noise reduction run at full size and write their files,
                            // then the edit saves.
                            fun saveEdits(rawDeveloped: Boolean = false) {
                                ui.savingRaw = false
                                viewModel.commitEditChain(currentEditSourcePhoto, rawDeveloped) { committed ->
                                    if (!committed) {
                                        isSaving = false
                                        showAiDenoiseFailure()
                                        return@commitEditChain
                                    }
                                    viewModel.saveEditMetadata(currentPhoto) { success ->
                                        isSaving = false
                                        if (success) {
                                            onBack()
                                        } else {
                                            // Still editing: changes go back to the small copies until the next Save.
                                            viewModel.deferEditWrites = true
                                        }
                                    }
                                }
                            }
                            // RAW changes are written now and the RAW developed in full, then the edit saves.
                            if (isRaw && viewModel.rawEditsPending) {
                                ui.savingRaw = true
                                viewModel.writeRawEdits(currentEditSourcePhoto) { written ->
                                    if (!written) {
                                        isSaving = false
                                        ui.savingRaw = false
                                        noteLine.say(R.string.editor_raw_save_failed)
                                    } else {
                                        viewModel.refreshRawPreview(currentEditSourcePhoto) { developed ->
                                            if (developed) {
                                                saveEdits(rawDeveloped = true)
                                            } else {
                                                isSaving = false
                                                ui.savingRaw = false
                                                noteLine.say(R.string.editor_raw_save_failed)
                                            }
                                        }
                                    }
                                }
                            } else {
                                saveEdits()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                EditorStatus(
                    ui = ui,
                    isRaw = isRaw,
                    isSaving = isSaving,
                    blurProgress = blurProgress,
                    awaitingBlur = awaitingBlur,
                    isLoadingPreview = isLoadingPreview,
                    editRawColorEngine = editRawColorEngine,
                    developStage = developStage,
                    rawBusy = rawBusy,
                    isRefreshingRawPreview = isRefreshingRawPreview,
                    hdPreview = hdPreview,
                    isAiDenoising = isAiDenoising,
                    aiDenoiseProgress = aiDenoiseProgress,
                    isPhotonDenoising = isPhotonDenoising,
                    waitingForPause = waitingForPause,
                )

                EditorExplanation(
                    explainKey = explainKey,
                    section = section,
                    group = group,
                    settings = settings,
                    rawItems = rawItems,
                    groupValues = groupValues,
                    mixerPick = mixerPick,
                    gradingPick = gradingPick,
                    calibrationPick = calibrationPick,
                    lookName = availableLuts.find { it.id == editLutId }?.getName(),
                    recipe = effectiveRecipe,
                )

                EditorControlLayer {
                    when {
                        group == EditorGroup.Curve -> EditorControlHint(stringResource(R.string.editor_curve_hint))
                        selectedRawItem != null -> RawItemControl(
                            item = selectedRawItem,
                            onRelease = ::commitRaw,
                            onTouch = { fingerOnRuler = it },
                        )
                        selectedGroupValue != null -> {
                            val v = selectedGroupValue
                            GroupValueControl(
                                v = v,
                                value = v.get(effectiveRecipe),
                                start = groupOpen(v),
                                onSet = { setGroupValue(v, it) },
                                onTouch = { fingerOnRuler = it },
                            )
                        }
                        group != null -> EditorControlHint(
                            stringResource(R.string.editor_section_not_built, stringResource(group.label))
                        )
                        selectedSetting != null -> SettingControl(
                            setting = selectedSetting,
                            value = settingValue(selectedSetting),
                            start = openValue(selectedSetting),
                            aiDenoiseEnabled = editAiDenoiseEnabled,
                            blurOff = blurOff,
                            quick = quick,
                            choices = choiceOptions(selectedSetting),
                            onAiDenoise = ::setAiDenoise,
                            onTurnBlurOn = ::turnBlurOn,
                            onChooseDepth = ::chooseDepth,
                            onChange = { setSetting(selectedSetting, it) },
                            onRelease = { releaseSetting(selectedSetting) },
                            onApertureScroll = { apertureScroll = it },
                            onTouch = { fingerOnRuler = it },
                        )
                        selectedKey == "cropRatio" || selectedKey == "straighten" -> CropControl(
                            viewModel = viewModel,
                            noteLine = noteLine,
                            selectedKey = selectedKey,
                            option = editCropAspectOption,
                            ratioChoices = ratioChoices,
                            landscape = turnedW >= turnedH,
                            straighten = editStraightenDegrees,
                            openStraighten = openStraighten,
                            onRatio = ::chooseRatio,
                            onTouch = { fingerOnRuler = it },
                        )
                        section != EditorSection.Look -> EditorControlHint(
                            stringResource(R.string.editor_section_not_built, stringResource(section.label))
                        )
                        else -> LookControl(
                            viewModel = viewModel,
                            lutSwipeState = lutSwipeState,
                            selectedKey = selectedKey,
                            availableLuts = availableLuts,
                            editLutId = editLutId,
                            // A Log video shows no thumbnails: they would show the flat Log picture.
                            thumbnail = stripThumbnail.takeUnless { videoSourceProfile?.isEnabled == true },
                            categoryOrder = categoryOrder,
                            availableFrames = availableFrames,
                            editFrameId = editFrameId,
                            allPresets = allPresets,
                            hasCopiedEditSettings = hasCopiedEditSettings,
                            hasLook = hasLook,
                            applyEffectsToVideo = applyEffectsToVideo,
                            intensity = intensity,
                            openIntensity = openIntensity,
                            onFilterManagementClick = onFilterManagementClick,
                            onOpenFrameEditor = onOpenFrameEditor,
                            onCopyEdits = {
                                viewModel.copyCurrentEditSettings(editFrameCustomProperties)
                                noteLine.say(R.string.copy_settings_success)
                            },
                            onPasteEdits = {
                                if (!hasCopiedEditSettings) {
                                    noteLine.say(R.string.editor_paste_nothing)
                                } else {
                                    viewModel.pasteCopiedEditSettingsToCurrentEdit()?.let {
                                        editFrameCustomProperties = it
                                        noteLine.say(R.string.paste_settings_success)
                                    }
                                }
                            },
                            onSaveAsPreset = {
                                cameraViewModel.draftPreset = viewModel.createPresetFromCurrentEdit(
                                    cameraViewModel.prepareCurrentSettingsPresetDraft(defaultPresetName)
                                )
                                onCreatePresetClick()
                            },
                            onSaveIntoLook = { showSaveIntoLook = true },
                            onIntensity = {
                                viewModel.setPhotoRecipeParams(
                                    RecipeParam.LUT_INTENSITY.setValue(editPhotoRecipeParams ?: editLutRecipeParams, it)
                                )
                            },
                            onTouch = { fingerOnRuler = it },
                        )
                    }
                    // A control that would start a second GPU job is held while one runs: a touch says why.
                    val busyNote = gpuBusyNote(allowBlur = section == EditorSection.Portrait)
                    if (busyNote != null && (selectedRawItem != null || selectedSetting?.startsGpuWork(isRaw) == true)) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInput(busyNote) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false).consume()
                                        noteLine.say(busyNote)
                                        do {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { it.consume() }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                        )
                    }
                }

                // The tool block: the settings of the current level above the path row. It lightens one step for
                // each group you are inside, so the depth shows without reading.
                EditorToolBlock(
                    depth = groupStack.size,
                    values = values,
                    selectedKey = selectedKey,
                    onClick = { item ->
                        val now = SystemClock.uptimeMillis()
                        val doubleTap = item.key == ui.lastTapKey && now - ui.lastTapAt < DOUBLE_TAP_MS
                        ui.lastTapKey = item.key
                        ui.lastTapAt = if (doubleTap) 0L else now
                        ui.pendingCard?.cancel()
                        when {
                            item.group -> {
                                // The colour groups say what they work on as they open: they look alike.
                                explainKey = item.key.takeIf {
                                    it.removePrefix("group:") in GroupIntros.keys.map { g -> g.name }
                                }
                                groupPath = (groupStack.map { it.name } + item.key.removePrefix("group:")).joinToString("/")
                            }
                            item.key == "rotation" -> {
                                explainKey = null
                                viewModel.rotateEditClockwise()
                            }
                            item.key == "mirror" -> {
                                explainKey = null
                                viewModel.toggleEditHorizontalMirror()
                            }
                            item.key == "curveDelete" -> {
                                explainKey = null
                                deleteCurvePoint()
                            }
                            // A double tap returns the setting to where it was when the editor opened.
                            doubleTap && (rawItems.any { it.key == item.key } ||
                                settings.firstOrNull { it.key == item.key }?.startsGpuWork(isRaw) == true) &&
                                blockedByGpu(allowBlur = section == EditorSection.Portrait) -> explainKey = null
                            doubleTap -> {
                                explainKey = null
                                settings.firstOrNull { it.key == item.key }?.let { s ->
                                    if (s.kind == SettingKind.Choice) {
                                        resetChoice(s)
                                    } else if (s.kind == SettingKind.AiDenoise) {
                                        setAiDenoise(openValue(s) == 1f || viewModel.editOpenValue(s.key) == true)
                                    } else {
                                        setSetting(s, openValue(s))
                                        releaseSetting(s)
                                    }
                                }
                                if (item.key == "cropRatio") restoreCropRatio()
                                if (item.key == "straighten") viewModel.setStraightenDegrees(openStraighten)
                                groupValues.firstOrNull { it.key == item.key }?.let { setGroupValue(it, groupOpen(it)) }
                                rawItems.firstOrNull { it.key == item.key }?.reset?.invoke()
                                // The selected point only: back onto the channel's curve as it was when the
                                // editor opened, at the same input.
                                if (item.key == "curvePoint") {
                                    val i = ui.curveSelected?.takeIf { it < curveNow.size }
                                    if (i == null) {
                                        noteLine.say(R.string.editor_curve_select_point)
                                    } else {
                                        val x = curveNow[i].first
                                        val lut = CurveUtils.evaluateCurve(openCurve(openCurves, curveChannel)?.toFloatArray())
                                        val y = lut[(x * (CurveUtils.LUT_SIZE - 1)).roundToInt()]
                                        setCurve(curveChannel, curveNow.toMutableList().also { it[i] = x to y })
                                    }
                                }
                                if (item.key == "intensity") {
                                    viewModel.setPhotoRecipeParams(
                                        RecipeParam.LUT_INTENSITY.setValue(editPhotoRecipeParams ?: editLutRecipeParams, openIntensity)
                                    )
                                }
                            }
                            // A second, single tap on the selected setting opens or closes its explanation,
                            // once it can't be the first half of a double tap any more.
                            item.key == selectedKey -> ui.pendingCard = scope.launch {
                                delay(DOUBLE_TAP_MS)
                                explainKey = if (explainKey == item.key) null else item.key
                            }
                            else -> {
                                explainKey = null
                                selections = HashMap(selections).apply { put(levelKey, item.key) }
                            }
                        }
                    },
                    // A long press shows the photo without this one setting's change while it lasts.
                    onHold = { item, holding ->
                        val s = settings.firstOrNull { it.key == item.key }
                        val live = item.key == "intensity" || s?.kind == SettingKind.Recipe
                        when {
                            item.group || item.key == "curveDelete" -> Unit
                            section == EditorSection.Crop -> ui.heldCrop = item.key.takeIf { holding }
                            groupValues.any { it.key == item.key } && holding -> {
                                val v = groupValues.first { it.key == item.key }
                                val recipe = editPhotoRecipeParams ?: editLutRecipeParams
                                ui.heldRecipe = recipe
                                viewModel.setPhotoRecipeParams(v.set(recipe, groupOpen(v)))
                            }
                            groupValues.any { it.key == item.key } -> ui.heldRecipe?.let {
                                ui.heldRecipe = null
                                viewModel.setPhotoRecipeParams(it)
                            }
                            item.key == "curvePoint" && holding -> {
                                val recipe = editPhotoRecipeParams ?: editLutRecipeParams
                                ui.heldRecipe = recipe
                                viewModel.setPhotoRecipeParams(recipe.withCurve(curveChannel, openCurve(openCurves, curveChannel)))
                            }
                            item.key == "curvePoint" -> ui.heldRecipe?.let {
                                ui.heldRecipe = null
                                viewModel.setPhotoRecipeParams(it)
                            }
                            !live && holding -> noteLine.say(R.string.editor_compare_one_unavailable)
                            !live -> Unit
                            holding -> {
                                val recipe = editPhotoRecipeParams ?: editLutRecipeParams
                                ui.heldRecipe = recipe
                                viewModel.setPhotoRecipeParams(
                                    if (s != null) s.param!!.setValue(recipe, openValue(s))
                                    else RecipeParam.LUT_INTENSITY.setValue(recipe, openIntensity)
                                )
                            }
                            else -> ui.heldRecipe?.let {
                                ui.heldRecipe = null
                                viewModel.setPhotoRecipeParams(it)
                            }
                        }
                    },
                    pathRow = {
                        if (group == null) {
                            EditorSectionRow(
                                sections = sections,
                                selected = section,
                                changed = ::sectionChanged,
                                onSelect = {
                                    explainKey = null
                                    groupPath = ""
                                    section = it
                                }
                            )
                        } else {
                            val curve = group == EditorGroup.Curve
                            EditorGroupRow(
                                title = stringResource(group.label),
                                parentTitle = groupStack.getOrNull(groupStack.size - 2)?.let { stringResource(it.label) },
                                onBack = { groupPath = groupStack.dropLast(1).joinToString("/") { it.name } },
                                // Reset: Curve's four curves, or RAW's values, back to where they were when the editor
                                // opened.
                                onReset = when (group) {
                                    EditorGroup.Curve -> {
                                        {
                                            if (CurveChannel.entries.none(::curveChanged)) {
                                                noteLine.say(R.string.editor_curve_nothing_to_reset)
                                            } else {
                                                viewModel.setPhotoRecipeParams(
                                                    CurveChannel.entries.fold(effectiveRecipe) { r, c -> r.withCurve(c, openCurve(openCurves, c)) }
                                                )
                                                ui.curveSelected = null
                                            }
                                        }
                                    }
                                    EditorGroup.Raw -> {
                                        {
                                            if (blockedByGpu()) {
                                                Unit
                                            } else if (EditorGroup.Raw.keys.none { it in changedKeys }) {
                                                noteLine.say(R.string.editor_raw_nothing_to_reset)
                                            } else {
                                                viewModel.restoreRawGroupToOpen()
                                                commitRaw()
                                            }
                                        }
                                    }
                                    else -> null
                                },
                                picker = when (group) {
                                    EditorGroup.Mixer -> {
                                        {
                                            MixerColors.indices.forEach { i ->
                                                ColorDotChip(mixerColor(i), i == mixerPick, groupValuesOf(group, i).any(::groupChanged)) {
                                                    mixerPick = i
                                                }
                                            }
                                        }
                                    }
                                    EditorGroup.Grading -> {
                                        {
                                            GradingRanges.forEachIndexed { i, label ->
                                                GroupPickChip(
                                                    stringResource(label), if (i == gradingPick) LilacText else LilacDim, i == gradingPick,
                                                    groupValuesOf(group, i).any(::groupChanged), { gradingPick = i }, width = 60
                                                )
                                            }
                                        }
                                    }
                                    EditorGroup.Calibration -> {
                                        {
                                            CalibrationChannels.forEachIndexed { i, (_, short, _) ->
                                                GroupPickChip(
                                                    short, calibrationColor(i).copy(alpha = if (i == calibrationPick) 1f else 0.6f),
                                                    i == calibrationPick, groupValuesOf(group, i).any(::groupChanged), { calibrationPick = i }
                                                )
                                            }
                                        }
                                    }
                                    EditorGroup.Curve -> {
                                        {
                                            listOf(
                                                CurveChannel.MASTER to stringResource(R.string.editor_curve_all),
                                                CurveChannel.RED to "R", CurveChannel.GREEN to "G", CurveChannel.BLUE to "B",
                                            ).forEach { (c, label) ->
                                                CurveChannelChip(label, c, c == curveChannel, curveChanged(c)) {
                                                    curveChannel = c
                                                    ui.curveSelected = null
                                                }
                                            }
                                        }
                                    }
                                    else -> null
                                }
                            )
                        }
                    },
                )
            }

            NoteLine(
                state = noteLine,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (EDITOR_BLOCK_DP + EDITOR_CONTROL_DP + 8f).dp)
            )

            EditorDialogs(
                ui = ui,
                viewModel = viewModel,
                noteLine = noteLine,
                scope = scope,
                photo = currentEditSourcePhoto,
                editRawDcpId = editRawDcpId,
                saveIntoLookName = if (showSaveIntoLook) availableLuts.find { it.id == editLutId }?.getName().orEmpty() else null,
                discard = showDiscardDialog,
                onCloseSaveIntoLook = { showSaveIntoLook = false },
                onCloseDiscard = { showDiscardDialog = false },
                onLeave = ::leaveEditor,
                onRawRefresh = { requestRawPreviewRefresh() },
            )
        }
    }

    EditorModelDialogs(
        viewModel = viewModel,
        payment = showPaymentDialog,
        aiDenoiseModel = aiDenoiseModelState.takeIf { pendingAiDenoise },
        depthModel = depthModelState.takeIf { showDepthModelDownloadDialog },
        onImportDepthModel = { depthModelImportLauncher.launch(arrayOf("*/*")) },
        onDismissAiDenoise = { pendingAiDenoise = false },
        onDismissDepth = {
            showDepthModelDownloadDialog = false
            pendingDepth = false
        },
    )
}

/**
 * What the photo box shows: the video, Crop's frame tool or the zoomable photo, and on top the look swipe, Portrait's
 * cheap blur and focus ring, and the curve.
 */
@Composable
private fun BoxScope.EditorPhotoContent(
    viewModel: GalleryViewModel,
    ui: EditorUiState,
    currentPhoto: MediaData,
    currentEditSourcePhoto: MediaData,
    videoSourceProfile: VideoLogProfile?,
    editLutConfig: LutConfig?,
    recipe: ColorRecipeParams,
    showOrigin: Boolean,
    fullView: Boolean,
    showCrop: Boolean,
    cropPreviewBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    editCropRect: RectF?,
    editCropAspectOption: CropAspectOption,
    editRotationDegrees: Int,
    editStraightenDegrees: Float,
    editMirrorHorizontal: Boolean,
    lutSwipeState: LutSwipeState,
    blurStale: Boolean,
    portraitOpen: Boolean,
    portraitQuick: Boolean,
    previewStrength: Float,
    blurLayers: List<ImageBitmap?>,
    awaitingBlur: Boolean,
    apertureDrag: Float?,
    apertureScroll: Float?,
    dofDrag: Float?,
    editComputationalAperture: Float?,
    contentBounds: Rect?,
    ringDrag: Offset?,
    focusCenter: Offset?,
    curveOpen: Boolean,
    curveFree: Rect,
    curveChannel: CurveChannel,
    onZoomChange: (Boolean) -> Unit,
    onContentBounds: (Rect) -> Unit,
) {
    val previewMediaModifier = Modifier.fillMaxSize()

    // 显示预览
    if (currentPhoto.isVideo) {
        if (videoSourceProfile != null) {
            VideoEditPlayer(
                photo = currentPhoto,
                lutConfig = if (showOrigin) null else editLutConfig,
                recipeParams = if (showOrigin) null else recipe,
                sourceLogProfile = videoSourceProfile,
                modifier = previewMediaModifier
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    } else if (showCrop && cropPreviewBitmap != null) {
        val geometryBaseWidth = currentEditSourcePhoto.metadata?.width?.takeIf { it > 0 }
            ?: currentEditSourcePhoto.width.coerceAtLeast(1)
        val geometryBaseHeight = currentEditSourcePhoto.metadata?.height?.takeIf { it > 0 }
            ?: currentEditSourcePhoto.height.coerceAtLeast(1)
        val (straightenSourceWidth, straightenSourceHeight) =
            PostEditGeometry.rotatedDimensions(
                geometryBaseWidth,
                geometryBaseHeight,
                editRotationDegrees
            )
        val (straightenedWidth, straightenedHeight) =
            PostEditGeometry.straightenedDimensions(
                straightenSourceWidth,
                straightenSourceHeight,
                editStraightenDegrees
            )
        val cropAspectRatio = when (editCropAspectOption) {
            CropAspectOption.Free -> null
            CropAspectOption.Original ->
                straightenSourceWidth.toFloat() / straightenSourceHeight
            else -> editCropAspectOption.getAspectRatioValue(
                straightenSourceWidth,
                straightenSourceHeight
            )
        }
        // Keep the no-black-corner constraint stable while the user drags a free crop
        // handle. Recomputing it from editCropRect on every pointer move changes the
        // pointerInput key and cancels the active gesture after each small movement.
        val effectiveCropAspectRatio = remember(
            currentEditSourcePhoto.id,
            editRotationDegrees,
            editStraightenDegrees,
            editCropAspectOption,
            straightenSourceWidth,
            straightenSourceHeight
        ) {
            cropAspectRatio
                ?: editCropRect?.takeIf { it.width() > 0f && it.height() > 0f }?.let {
                    it.width() * straightenedWidth /
                        (it.height() * straightenedHeight)
                }
                ?: straightenSourceWidth.toFloat() / straightenSourceHeight
        }
        val cropBounds = PostEditGeometry.straightenSafeCropRectForAspect(
            width = straightenSourceWidth,
            height = straightenSourceHeight,
            straightenDegrees = editStraightenDegrees,
            pixelAspect = effectiveCropAspectRatio
        )
        // A held value shows the photo without that one change (the frame hides, it no longer fits).
        val heldTurn = ui.heldCrop == "rotation"
        val heldFlip = ui.heldCrop == "mirror"
        CropOverlay(
            bitmap = cropPreviewBitmap,
            cropRect = editCropRect ?: cropBounds,
            cropAspectRatio = cropAspectRatio,
            straightenDegrees = when {
                ui.heldCrop == "straighten" -> 0f
                heldFlip -> -editStraightenDegrees  // flipping also mirrored the angle
                else -> editStraightenDegrees
            },
            geometrySourceWidth = straightenSourceWidth,
            geometrySourceHeight = straightenSourceHeight,
            onCropRectChanged = { rect -> viewModel.setCropRect(rect) },
            aspectOption = editCropAspectOption,
            contentPadding = 24.dp,
            rotationDegrees = if (heldTurn) 0 else editRotationDegrees,
            mirrorHorizontal = editMirrorHorizontal && !heldFlip,
            showFrame = ui.heldCrop == null,
            modifier = previewMediaModifier
        )
    } else {
        ZoomableEditImage(
            previewBitmap = previewBitmap,
            resetKey = fullView,
            contentDescription = stringResource(R.string.edit),
            onZoomChange = onZoomChange,
            onContentBounds = onContentBounds,
            modifier = previewMediaModifier
        )
    }

    LutSwipeIndicator(state = lutSwipeState)

    // Portrait: the cheap preview while the blur renders or a control is dragged, cross-fading between
    // the two prepared strengths around the one wanted, so it feels like more steps than there are.
    val previewBounds = contentBounds
    val layers = blurLayers
    val cheapWanted = portraitOpen && !fullView && (previewStrength > 0f || apertureScroll != null) &&
        (awaitingBlur || blurStale || apertureDrag != null || apertureScroll != null || ringDrag != null || dofDrag != null)
    // On at once; off only after the new blur has cross-faded in underneath (the picture's own fade
    // takes about 200 ms), then fading out over it, so no moment shows the photo without a blur.
    val cheapAlpha by animateFloatAsState(
        targetValue = if (cheapWanted) 1f else 0f,
        animationSpec = if (cheapWanted) snap() else tween(durationMillis = 300, delayMillis = 300),
        label = "cheapBlur",
    )
    if (layers.isNotEmpty() && previewBounds != null && portraitOpen && !fullView && cheapAlpha > 0f) {
        val levels = BlurPreview.LEVELS
        val upper = levels.indexOfFirst { it >= previewStrength }.let { if (it < 0) levels.lastIndex else it }
        val lower = (upper - 1).coerceAtLeast(0)
        val t = if (upper == lower) 1f else ((previewStrength - levels[lower]) / (levels[upper] - levels[lower])).coerceIn(0f, 1f)
        val under = layers[lower] ?: layers[upper]
        val over = layers[upper] ?: layers[lower]
        val dstOffset = IntOffset(previewBounds.left.roundToInt(), previewBounds.top.roundToInt())
        val dstSize = IntSize(previewBounds.width.roundToInt(), previewBounds.height.roundToInt())
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = cheapAlpha }) {
            under?.let { drawImage(it, dstOffset = dstOffset, dstSize = dstSize) }
            if (over != null && over !== under) drawImage(over, dstOffset = dstOffset, dstSize = dstSize, alpha = t)
        }
    }

    // Portrait: the focus ring, on the photo the whole time the section is open (not in Quick,
    // which keeps the cut-out subject sharp wherever the focus is).
    if (portraitOpen && !fullView && !portraitQuick) {
        val center = ringDrag ?: focusCenter
        if (center != null) {
            PortraitFocusRing(
                center = center,
                off = (apertureDrag ?: editComputationalAperture ?: 0f) <= 0f
            )
        }
    }

    if (curveOpen && !fullView && !currentPhoto.isVideo) {
        val points = ui.curveDrag ?: curvePoints(recipe, curveChannel)
        CurveOverlay(
            contentBounds = contentBounds,
            free = curveFree,
            points = points,
            channel = curveChannel,
            // The other channels that bend the tones, faint behind the one being edited.
            others = CurveChannel.entries.filter { it != curveChannel }
                .mapNotNull { c -> recipe.getCurvePoints(c)?.let { c to it } },
            selected = ui.curveSelected?.takeIf { it < points.size },
        )
    }
}

/** What the blur, the RAW develop, AI denoise or noise reduction is doing, under the edge icons, while it works. */
@Composable
private fun BoxScope.EditorStatus(
    ui: EditorUiState,
    isRaw: Boolean,
    isSaving: Boolean,
    blurProgress: GalleryViewModel.BlurProgress?,
    awaitingBlur: Boolean,
    isLoadingPreview: Boolean,
    editRawColorEngine: RawRenderingEngine,
    developStage: RawDevelopStage?,
    rawBusy: Boolean,
    isRefreshingRawPreview: Boolean,
    hdPreview: Boolean,
    isAiDenoising: Boolean,
    aiDenoiseProgress: Float,
    isPhotonDenoising: Boolean,
    waitingForPause: Boolean,
) {
    // What the blur is doing.
    val blurStatus = when (blurProgress?.stage) {
        BokehStage.Subject -> stringResource(R.string.editor_blur_subject)
        BokehStage.Depth -> stringResource(R.string.editor_blur_depth)
        BokehStage.Render -> stringResource(R.string.editor_blur_render)
        null -> if (awaitingBlur && isLoadingPreview) stringResource(R.string.editor_blur_loading) else null
    }?.let { text ->
        val seconds = blurProgress?.expectedMs?.let { ((it + 500) / 1000).toInt() }
            ?: if (blurProgress?.stage == BokehStage.Depth) 15 else null
        if (seconds != null && seconds >= 1) stringResource(R.string.editor_about_seconds, text, seconds) else text
    }
    // What the RAW develop is doing, the same way: which of its six stages, and how long is left, from
    // the stage times of the last develop of the same kind with the same engine.
    val engineName = rawRenderingEngineName(editRawColorEngine)
    val stage = developStage.takeIf { isRaw && (rawBusy || ui.savingRaw) }
    val rawStatus = when {
        stage != null -> {
            val full = ui.savingRaw || isRefreshingRawPreview
            val kind = stringResource(
                when {
                    ui.savingRaw -> R.string.editor_raw_kind_saving
                    full || hdPreview -> R.string.editor_raw_kind_full
                    ui.rawLookShown -> R.string.editor_raw_kind_earlier
                    else -> R.string.editor_raw_kind_preview
                }
            )
            val label = when (stage) {
                RawDevelopStage.Reading -> stringResource(R.string.editor_raw_stage_reading)
                RawDevelopStage.Demosaic -> stringResource(R.string.editor_raw_stage_demosaic)
                RawDevelopStage.ToneMap -> stringResource(R.string.editor_raw_stage_tone)
                RawDevelopStage.NoiseReduction -> stringResource(R.string.editor_raw_stage_noise)
                RawDevelopStage.Look -> stringResource(R.string.editor_raw_stage_look, engineName)
                RawDevelopStage.Finishing -> stringResource(R.string.editor_raw_stage_finishing)
            }
            // The step and what it does; the wheel in the top row shows it is working. (A bar from the last
            // develop's times jumped: a kept RAW skips the first, slowest step.)
            stringResource(R.string.editor_raw_status, kind, stage.ordinal + 1, RawDevelopStage.entries.size, label)
        }
        isRaw && ui.savingRaw -> stringResource(R.string.editor_raw_saving)
        isAiDenoising -> stringResource(
            if (isSaving) R.string.editor_saving_ai else R.string.editor_ai_preview,
            (aiDenoiseProgress * 100).roundToInt(),
        )
        isPhotonDenoising -> stringResource(if (isSaving) R.string.editor_saving_noise else R.string.editor_noise_preview)
        waitingForPause -> stringResource(R.string.editor_waiting_pause)
        isRaw && rawBusy && ui.rawLookShown -> stringResource(R.string.editor_raw_earlier, engineName)
        isRaw && rawBusy -> stringResource(R.string.editor_raw_developing, engineName)
        else -> null
    }
    (blurStatus ?: rawStatus)?.let { status ->
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = EDITOR_EDGE_DP.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = status, color = LilacText, fontSize = 12.sp, style = LilacTextStyle)
        }
    }
}

/** The explanation of the selected setting, on a second tap on it. */
@Composable
private fun BoxScope.EditorExplanation(
    explainKey: String?,
    section: EditorSection,
    group: EditorGroup?,
    settings: List<EditorSetting>,
    rawItems: List<RawItem>,
    groupValues: List<GroupValue>,
    mixerPick: Int,
    gradingPick: Int,
    calibrationPick: Int,
    lookName: String?,
    recipe: ColorRecipeParams,
) {
    val explained = settings.firstOrNull { it.key == explainKey }
    val rawItem = rawItems.firstOrNull { it.key == explainKey }
    val groupValue = groupValues.firstOrNull { it.key == explainKey }
    val (name, text) = when {
        explained != null -> {
            // A look's own exposure, highlights or shadows stay applied on a RAW photo, hidden; say so.
            val lookPart = when (explained) {
                EditorSetting.RawExposure -> EditorSetting.Exposure
                EditorSetting.RawHighlights -> EditorSetting.Highlights
                EditorSetting.RawShadows -> EditorSetting.Shadows
                else -> null
            }?.let { part ->
                val v = part.param!!.getValue(recipe)
                if (v != part.param.defaultValue) {
                    " " + stringResource(R.string.editor_look_also_sets, stringResource(part.label), part.format(v))
                } else {
                    ""
                }
            }.orEmpty()
            stringResource(explained.label) to stringResource(explained.explanation) + lookPart
        }
        rawItem != null -> rawItem.name to rawItem.explanation
        groupValue != null -> {
            val pickName = when (group) {
                EditorGroup.Mixer -> stringResource(MixerColors[mixerPick].second)
                EditorGroup.Grading -> stringResource(GradingRanges[gradingPick])
                else -> stringResource(CalibrationChannels[calibrationPick].third)
            }
            "$pickName ${stringResource(groupValue.label)}" to stringResource(groupValue.explanation)
        }
        explainKey?.startsWith("group:") == true -> {
            val g = EditorGroup.valueOf(explainKey.removePrefix("group:"))
            stringResource(g.label) to stringResource(GroupIntros.getValue(g))
        }
        explainKey == "curvePoint" ->
            stringResource(R.string.editor_group_curve) to stringResource(R.string.editor_explain_curve)
        explainKey != null && section == EditorSection.Crop -> if (explainKey == "straighten") {
            stringResource(R.string.crop_straighten) to stringResource(R.string.editor_explain_straighten)
        } else {
            stringResource(R.string.editor_ratio) to stringResource(R.string.editor_explain_crop_ratio)
        }
        explainKey != null && section == EditorSection.Look -> when (explainKey) {
            "look" -> (lookName ?: stringResource(R.string.none)) to stringResource(R.string.editor_explain_look)
            "frame" -> stringResource(R.string.frame) to stringResource(R.string.editor_explain_frame)
            "preset" -> stringResource(R.string.editor_preset) to stringResource(R.string.editor_explain_preset)
            "livePhoto" -> stringResource(R.string.settings_use_live_photo) to stringResource(R.string.editor_explain_live_photo)
            else -> stringResource(R.string.editor_intensity) to stringResource(R.string.editor_explain_intensity)
        }
        else -> return
    }
    EditorExplanationCard(
        name = name,
        text = text,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 8.dp, end = 8.dp, bottom = (EDITOR_BLOCK_DP + EDITOR_CONTROL_DP + 8f).dp)
    )
}

/** The one active control, on the photo's bottom edge. */
@Composable
private fun BoxScope.EditorControlLayer(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = EDITOR_BLOCK_DP.dp)
            .fillMaxWidth()
            .height(EDITOR_CONTROL_DP.dp),
        content = content,
    )
}

/** Light › RAW and its groups: the selected value's ruler, or its chips and grey actions. */
@Composable
private fun RawItemControl(item: RawItem, onRelease: () -> Unit, onTouch: (Boolean) -> Unit) {
    val r = item.ruler
    if (r != null) {
        LilacRuler(
            values = r.steps,
            value = r.value,
            start = r.start,
            label = r.label,
            onChange = r.onChange,
            onRelease = r.onRelease ?: onRelease,
            onReset = item.reset,
            labelEvery = r.labelEvery,
            onTouch = onTouch,
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ChoiceScroller(item.choices.indexOfFirst { it.selected }.coerceAtLeast(0), maxWidth = 360.dp) {
                item.choices.forEach { c ->
                    EditorChip(text = c.label, selected = c.selected, onClick = c.onClick, onLongClick = c.onLongClick)
                }
                item.actions.forEach { a ->
                    EditorChip(text = a.label, selected = false, icon = a.icon, action = true, onClick = a.onClick)
                }
            }
        }
    }
}

/** Mixer, Grading and Calibration: the ruler of the picked colour's, range's or primary's selected value. */
@Composable
private fun GroupValueControl(v: GroupValue, value: Float, start: Float, onSet: (Float) -> Unit, onTouch: (Boolean) -> Unit) {
    LilacRuler(
        values = if (start in v.steps) v.steps else (v.steps + start).sorted(),
        value = value,
        start = start,
        label = v::tick,
        onChange = onSet,
        onRelease = {},
        onReset = { onSet(start) },
        // Hue: every 30°.
        labelEvery = if (v.hue) 6 else null,
        onTouch = onTouch,
        tickColor = if (v.hue) ::hueColor else null,
    )
}

/** A section's own setting: AI denoise's switch, Portrait's choices and notes, or the setting's ruler. */
@Composable
private fun SettingControl(
    setting: EditorSetting,
    value: Float,
    start: Float,
    aiDenoiseEnabled: Boolean,
    blurOff: Boolean,
    quick: Boolean,
    choices: List<EditorChoice>,
    onAiDenoise: (Boolean) -> Unit,
    onTurnBlurOn: () -> Unit,
    onChooseDepth: () -> Unit,
    onChange: (Float) -> Unit,
    onRelease: () -> Unit,
    onApertureScroll: (Float?) -> Unit,
    onTouch: (Boolean) -> Unit,
) {
    val offText = stringResource(R.string.editor_off)
    when {
        setting.kind == SettingKind.AiDenoise -> Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(false to offText, true to stringResource(R.string.editor_on)).forEach { (enabled, text) ->
                LilacChip(
                    text = text,
                    selected = aiDenoiseEnabled == enabled,
                    onClick = { onAiDenoise(enabled) },
                    height = 36.dp,
                    fill = Color(0xB8141413),
                    fontSize = 13.sp,
                    horizontalPadding = 13.dp,
                )
            }
        }
        // With the blur Off its options have nothing to act on: say so, and offer to turn it on.
        (setting.kind == SettingKind.Choice && setting != EditorSetting.Depth ||
            setting == EditorSetting.DepthOfField) && blurOff -> PortraitNote(
            text = stringResource(R.string.editor_blur_off),
            action = stringResource(R.string.editor_turn_on),
            onAction = onTurnBlurOn
        )
        setting == EditorSetting.Focus && quick -> PortraitNote(
            text = stringResource(R.string.editor_quick_keeps_subject),
            action = stringResource(R.string.editor_use_depth),
            onAction = onChooseDepth
        )
        setting.kind == SettingKind.Choice -> Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (blurOff && setting != EditorSetting.Depth) 0.5f else 1f },
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            choices.forEach { option ->
                LilacChip(
                    text = option.label,
                    selected = option.selected,
                    onClick = option.onClick,
                    height = 36.dp,
                    fill = Color(0xB8141413),
                    fontSize = 13.sp,
                    horizontalPadding = 13.dp,
                    icon = option.icon,
                )
            }
        }
        else -> {
            val aperture = setting.kind == SettingKind.Aperture
            val steps = setting.steps.let { if (aperture || start in it) it else (it + start).sorted() }
            val autoText = stringResource(R.string.editor_auto)
            val shallowText = stringResource(R.string.editor_shallow)
            val deepText = stringResource(R.string.editor_deep)
            LilacRuler(
                values = steps,
                value = value,
                start = start,
                label = {
                    when {
                        aperture && it == 0f -> offText
                        setting.kind == SettingKind.DepthOfField -> when (it) {
                            0f -> autoText
                            -10f -> shallowText
                            10f -> deepText
                            else -> "${abs(it.roundToInt())}"
                        }
                        else -> setting.tickLabel(it)
                    }
                },
                stops = aperture,
                onScroll = { if (aperture) onApertureScroll(it) },
                onChange = onChange,
                onRelease = onRelease,
                onReset = {
                    onChange(start)
                    onRelease()
                },
                labelEvery = setting.labelEvery,
                onTouch = onTouch
            )
        }
    }
}

/** Crop's ratio chips and straightening ruler. */
@Composable
private fun CropControl(
    viewModel: GalleryViewModel,
    noteLine: NoteLineState,
    selectedKey: String?,
    option: CropAspectOption,
    ratioChoices: List<AspectRatio>,
    landscape: Boolean,
    straighten: Float,
    openStraighten: Float,
    onRatio: (AspectRatio) -> Unit,
    onTouch: (Boolean) -> Unit,
) {
    if (selectedKey == "cropRatio") Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val selectedIndex = when {
            option.isOriginal -> 1
            option.isFree -> 2
            else -> 3 + ratioChoices.indexOfFirst { sameShape(option, it) }.coerceAtLeast(0)
        }
        ChoiceScroller(selectedIndex, maxWidth = 360.dp) {
            // Turn swaps a fixed shape between portrait and landscape.
            val turnable = option.hasFixedRatio && option.widthRatio != option.heightRatio
            EditorChip(
                text = stringResource(R.string.editor_turn), selected = false,
                icon = AppIcons.RotateClockwiseLine, action = true, dim = !turnable,
                onClick = {
                    if (turnable) viewModel.setCropAspectOption(CropAspectOption.Custom(option.heightRatio, option.widthRatio))
                    else noteLine.say(R.string.editor_turn_needs_shape)
                }
            )
            EditorChip(
                text = stringResource(R.string.crop_original), selected = option.isOriginal,
                onClick = { viewModel.setCropAspectOption(CropAspectOption.Original) }
            )
            EditorChip(
                text = stringResource(R.string.crop_free), selected = option.isFree,
                onClick = { viewModel.setCropAspectOption(CropAspectOption.Free) }
            )
            ratioChoices.forEach { r ->
                EditorChip(
                    text = if (r == AspectRatio.XPAN) "XPAN"
                    else if (landscape) "${r.widthRatio}:${r.heightRatio}"
                    else "${r.heightRatio}:${r.widthRatio}",
                    selected = sameShape(option, r),
                    onClick = { onRatio(r) }
                )
            }
        }
    } else {
        val steps = (-90..90).map { it / 2f }.let { if (openStraighten in it) it else (it + openStraighten).sorted() }
        LilacRuler(
            values = steps,
            value = straighten,
            start = openStraighten,
            label = { "${it.roundToInt()}" },
            // Only the canvas redraws while it moves: no render.
            onChange = { viewModel.setStraightenDegrees(it) },
            onRelease = {},
            onReset = { viewModel.setStraightenDegrees(openStraighten) },
            labelEvery = 10,
            onTouch = onTouch
        )
    }
}

/** Look's controls: the look strip, the frames, the presets with copy and paste, Live photo, and Intensity. */
@Composable
private fun LookControl(
    viewModel: GalleryViewModel,
    lutSwipeState: LutSwipeState,
    selectedKey: String?,
    availableLuts: List<LutInfo>,
    editLutId: String?,
    thumbnail: Bitmap?,
    categoryOrder: List<String>,
    availableFrames: List<FrameInfo>,
    editFrameId: String?,
    allPresets: List<CameraPreset>,
    hasCopiedEditSettings: Boolean,
    hasLook: Boolean,
    applyEffectsToVideo: Boolean,
    intensity: Float,
    openIntensity: Float,
    onFilterManagementClick: (String?) -> Unit,
    onOpenFrameEditor: (String) -> Unit,
    onCopyEdits: () -> Unit,
    onPasteEdits: () -> Unit,
    onSaveAsPreset: () -> Unit,
    onSaveIntoLook: () -> Unit,
    onIntensity: (Float) -> Unit,
    onTouch: (Boolean) -> Unit,
) {
    when {
        selectedKey == "look" -> LookStrip(
            luts = availableLuts,
            currentLutId = editLutId ?: "none",
            thumbnail = thumbnail,
            categoryOrder = categoryOrder,
            onSelect = { id ->
                viewModel.setEditLut(id)
                availableLuts.find { it.id == id }?.let { lutSwipeState.announce(it.getName()) }
            },
            onSelectedClick = {
                availableLuts.find { it.id == editLutId }?.let { lutSwipeState.announce(it.getName()) }
            },
            onLongPress = { onFilterManagementClick(it) },
            onManage = { onFilterManagementClick(null) },
            compact = true,
            modifier = Modifier.fillMaxSize()
        )
        selectedKey == "frame" -> {
            val frames = listOf<FrameInfo?>(null) + availableFrames
            val selected = availableFrames.find { it.id == editFrameId }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChoiceScroller(frames.indexOfFirst { it?.id == editFrameId }.coerceAtLeast(0), maxWidth = 360.dp) {
                    frames.forEach { frame ->
                        EditorChip(
                            text = frame?.name ?: stringResource(R.string.none),
                            selected = frame?.id == editFrameId,
                            onClick = {
                                // A second tap on an editable frame opens it in the frame editor.
                                if (frame?.id == editFrameId) frame?.takeIf { it.isEditable }?.let { onOpenFrameEditor(it.id) }
                                else viewModel.setEditFrame(frame?.id)
                            }
                        )
                    }
                    if (selected?.isEditable == true) {
                        EditorChip(
                            text = stringResource(R.string.editor_edit_frame), selected = false,
                            icon = AppIcons.FrameLine, action = true,
                            onClick = { onOpenFrameEditor(selected.id) }
                        )
                    }
                }
            }
        }
        selectedKey == "preset" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ChoiceScroller(allPresets.indexOfFirst { it.id == viewModel.appliedPresetId }.coerceAtLeast(0), maxWidth = 360.dp) {
                allPresets.forEach { preset ->
                    val name = presetDisplayName(preset)
                    EditorChip(
                        text = name,
                        selected = preset.id == viewModel.appliedPresetId,
                        onClick = {
                            viewModel.applyPresetToEdit(preset)
                            lutSwipeState.announce(name)
                        }
                    )
                }
                EditorChip(
                    text = stringResource(R.string.editor_copy_edits), selected = false,
                    icon = AppIcons.ContentCopy, action = true,
                    onClick = onCopyEdits
                )
                EditorChip(
                    text = stringResource(R.string.editor_paste_edits), selected = false,
                    icon = AppIcons.ContentCopy, action = true,
                    dim = !hasCopiedEditSettings,
                    onClick = onPasteEdits
                )
                EditorChip(
                    text = stringResource(R.string.save_as_preset), selected = false,
                    icon = AppIcons.Bookmark, action = true,
                    onClick = onSaveAsPreset
                )
                if (hasLook) {
                    EditorChip(
                        text = stringResource(R.string.editor_save_into_look), selected = false,
                        icon = AppIcons.FiltersLine, action = true,
                        onClick = onSaveIntoLook
                    )
                }
            }
        }
        selectedKey == "livePhoto" -> Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(false to stringResource(R.string.editor_off), true to stringResource(R.string.editor_on)).forEach { (apply, text) ->
                EditorChip(text = text, selected = applyEffectsToVideo == apply, onClick = { viewModel.setApplyEffectsToVideo(apply) })
            }
        }
        selectedKey == "intensity" && !hasLook ->
            EditorControlHint(stringResource(R.string.editor_intensity_no_look))
        selectedKey == "intensity" -> LilacRuler(
            values = IntensitySteps,
            value = intensity,
            start = openIntensity,
            label = { (it * 100).roundToInt().toString() },
            onChange = onIntensity,
            onRelease = {},
            onReset = { onIntensity(openIntensity) },
            onTouch = onTouch
        )
        else -> EditorControlHint(stringResource(R.string.editor_not_built))
    }
}

/** True when [option] is ratio [r], either way round. */
private fun sameShape(option: CropAspectOption, r: AspectRatio): Boolean {
    if (!option.hasFixedRatio || option.heightRatio == 0f) return false
    val a = option.widthRatio / option.heightRatio
    val wide = r.widthRatio.toFloat() / r.heightRatio
    return abs(a - wide) / wide < 0.01f || abs(a - 1f / wide) * wide < 0.01f
}

/** Crop's values: the frame's ratio and straightening, then Rotate and Flip, which act on every tap. */
@Composable
private fun cropValues(
    option: CropAspectOption,
    ratioChoices: List<AspectRatio>,
    frameShape: Float,
    straighten: Float,
    rotation: Int,
    mirror: Boolean,
    changedKeys: Set<String>,
): List<EditorValue> {
    val ratioText = when {
        option.isFree -> stringResource(R.string.crop_free)
        option.isOriginal -> stringResource(R.string.crop_original)
        else -> ratioChoices.firstOrNull { sameShape(option, it) }?.let { r ->
            when {
                r == AspectRatio.XPAN -> "XPAN"
                option.widthRatio >= option.heightRatio -> "${r.widthRatio}:${r.heightRatio}"
                else -> "${r.heightRatio}:${r.widthRatio}"
            }
        } ?: String.format(Locale.ROOT, "%.2f:1", option.widthRatio / option.heightRatio)
    }
    return listOf(
        EditorValue(
            "cropRatio", stringResource(R.string.editor_ratio), ratioText,
            AppIcons.CropLine,
            // A frame moved by turning or straightening alone is those values' change, not the ratio's.
            "cropRatio" in changedKeys || "crop" in changedKeys &&
                listOf("rotation", "straighten", "mirror").none { it in changedKeys },
            glyph = EditorGlyph.CropRatio, fraction = (frameShape - 1f) / (frameShape + 1f)
        ),
        EditorValue(
            "straighten", stringResource(R.string.crop_straighten),
            String.format(Locale.ROOT, "%.1f", straighten).removeSuffix(".0") + "°",
            AppIcons.CropLine, "straighten" in changedKeys,
            glyph = EditorGlyph.Straighten, fraction = straighten / 45f
        ),
        // Rotate and Flip act on every tap; they are never the selected control.
        EditorValue(
            "rotation", stringResource(R.string.rotate), "$rotation°",
            AppIcons.RotateClockwiseLine, "rotation" in changedKeys,
            glyph = EditorGlyph.Rotate, fraction = rotation / 360f
        ),
        EditorValue(
            "mirror", stringResource(R.string.editor_flip),
            stringResource(if (mirror) R.string.editor_on else R.string.editor_off),
            AppIcons.CropLine, "mirror" in changedKeys,
            glyph = EditorGlyph.Flip, fraction = if (mirror) 1f else 0f
        ),
    )
}

/** Look's values: the look and its intensity, then (photos only) the frame and preset, and Live photo. */
@Composable
private fun lookValues(
    lookName: String?,
    intensity: Float,
    isVideo: Boolean,
    isMotionPhoto: Boolean,
    frameName: String?,
    preset: String?,
    applyEffectsToVideo: Boolean,
    changedKeys: Set<String>,
): List<EditorValue> = listOf(
    EditorValue(
        "look", stringResource(R.string.editor_look),
        lookName ?: stringResource(R.string.none),
        AppIcons.FiltersLine, "look" in changedKeys, wide = true
    ),
    EditorValue(
        "intensity", stringResource(R.string.editor_intensity), "${(intensity * 100).roundToInt()}%",
        AppIcons.AdjustmentsLine, "lutIntensity" in changedKeys
    ),
) + if (isVideo) emptyList() else listOf(
    EditorValue(
        "frame", stringResource(R.string.frame),
        frameName ?: stringResource(R.string.none),
        AppIcons.FrameLine, "frame" in changedKeys, wide = true
    ),
    EditorValue(
        "preset", stringResource(R.string.editor_preset),
        preset ?: "—",
        AppIcons.BookmarkLine, wide = true
    ),
) + if (isMotionPhoto) listOf(
    EditorValue(
        "livePhoto", stringResource(R.string.settings_use_live_photo),
        stringResource(if (applyEffectsToVideo) R.string.editor_on else R.string.editor_off),
        AppIcons.MovieLine, "applyEffectsToVideo" in changedKeys
    )
) else emptyList()

/** A section's own settings as value tiles. */
@Composable
private fun settingValues(
    settings: List<EditorSetting>,
    valueOf: (EditorSetting) -> Float,
    choiceLabel: (EditorSetting) -> String,
    quick: Boolean,
    blurOff: Boolean,
    aiDenoiseEnabled: Boolean,
    isAiDenoising: Boolean,
    aiDenoiseProgress: Float,
    changedKeys: Set<String>,
): List<EditorValue> {
    val offText = stringResource(R.string.editor_off)
    val onText = stringResource(R.string.editor_on)
    val autoText = stringResource(R.string.editor_auto)
    val shallowValue = stringResource(R.string.editor_shallow_value)
    val deepValue = stringResource(R.string.editor_deep_value)
    val subjectText = stringResource(R.string.editor_subject)
    // Lower keeps less in focus, higher more; words say which way, as signs alone did not.
    fun depthOfFieldText(v: Float): String {
        val n = v.roundToInt()
        return when {
            n < 0 -> shallowValue.format(-n)
            n > 0 -> deepValue.format(n)
            else -> autoText
        }
    }
    return settings.map { s ->
        val v = valueOf(s)
        EditorValue(
            key = s.key,
            name = stringResource(s.label),
            value = when {
                s.kind == SettingKind.Aperture -> if (v > 0f) "f/${formatAperture(v)}" else offText
                s.kind == SettingKind.DepthOfField -> depthOfFieldText(v)
                s == EditorSetting.Focus && quick -> subjectText
                s == EditorSetting.Depth && blurOff -> offText
                s.kind == SettingKind.Choice -> choiceLabel(s)
                s.kind != SettingKind.AiDenoise -> s.format(v)
                isAiDenoising -> "${(aiDenoiseProgress * 100).roundToInt()}%"
                aiDenoiseEnabled -> onText
                else -> offText
            },
            icon = s.icon,
            changed = s.key in changedKeys,
            glyph = s.glyph,
            fraction = s.fraction(v),
            dimmed = s.section == EditorSection.Portrait && s != EditorSetting.Aperture &&
                s != EditorSetting.Depth && blurOff,
        )
    }
}

/**
 * The tool block: the settings of the current level above the path row. It lightens one step for each group you are
 * inside ([depth]), so the depth shows without reading.
 */
@Composable
private fun BoxScope.EditorToolBlock(
    depth: Int,
    values: List<EditorValue>,
    selectedKey: String?,
    onClick: (EditorValue) -> Unit,
    onHold: (EditorValue, Boolean) -> Unit,
    pathRow: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(EDITOR_BLOCK_DP.dp)
            .background(
                when (depth) {
                    0 -> Color.Black
                    1 -> Color(0xFF222220)
                    else -> Color(0xFF2E2E2C)
                }
            )
    ) {
        Spacer(Modifier.height(2.dp))
        if (values.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(84.dp))
        } else {
            EditorValueRow(values = values, selectedKey = selectedKey, onClick = onClick, onHold = onHold)
        }
        Spacer(Modifier.height(4.dp))
        pathRow()
    }
}

/** The groups of the current level, as tiles that open them. */
@Composable
private fun groupTiles(groups: List<EditorGroup>, filmSections: Map<String, Boolean>?, changedKeys: Set<String>): List<EditorValue> =
    groups.map { g ->
        EditorValue(
            key = "group:${g.name}",
            name = stringResource(g.label),
            value = "",
            icon = g.icon,
            changed = g.filmSection?.let { filmSections?.get(it) } ?: g.keys.any { it in changedKeys },
            wide = true,
            group = true,
        )
    }

/** Mixer, Grading and Calibration: the picked colour's, range's or primary's values. */
@Composable
private fun groupValueTiles(values: List<GroupValue>, recipe: ColorRecipeParams, changed: (GroupValue) -> Boolean): List<EditorValue> =
    values.map { v ->
        EditorValue(v.key, stringResource(v.label), v.text(v.get(recipe)), v.icon, changed(v))
    }

/** Curve's values: the selected point, and Delete, which acts on a tap, as Rotate does; never the selected control. */
@Composable
private fun curveValues(point: Pair<Float, Float>?, changed: Boolean, deletable: Boolean): List<EditorValue> = listOf(
    EditorValue(
        "curvePoint", stringResource(R.string.editor_curve_point),
        point?.let { (x, y) -> "${(x * 255).roundToInt()} → ${(y * 255).roundToInt()}" } ?: "—",
        AppIcons.CircleDotLine, changed
    ),
    EditorValue(
        "curveDelete", stringResource(R.string.editor_curve_delete), if (point != null) "✕" else "—",
        AppIcons.CloseLine, dimmed = !deletable
    ),
)

/** The editor's questions: delete an imported DCP, save the edit into its look, and discard the changes. */
@Composable
private fun EditorDialogs(
    ui: EditorUiState,
    viewModel: GalleryViewModel,
    noteLine: NoteLineState,
    scope: CoroutineScope,
    photo: MediaData,
    editRawDcpId: String?,
    saveIntoLookName: String?,
    discard: Boolean,
    onCloseSaveIntoLook: () -> Unit,
    onCloseDiscard: () -> Unit,
    onLeave: () -> Unit,
    onRawRefresh: () -> Unit,
) {
    ui.deleteDcpAsk?.let { dcp ->
        EditorDiscardDialog(
            title = stringResource(R.string.editor_delete_dcp_title),
            message = stringResource(R.string.editor_delete_dcp_message, dcp.getName()),
            keepText = stringResource(R.string.cancel),
            actionText = stringResource(R.string.delete),
            onKeep = { ui.deleteDcpAsk = null },
            onDiscard = {
                ui.deleteDcpAsk = null
                val wasChosen = editRawDcpId == dcp.id
                viewModel.deleteRawDcp(photo, dcp.id) { success ->
                    noteLine.say(if (success) R.string.raw_dcp_delete_success else R.string.raw_dcp_delete_failed)
                    if (success && wasChosen) onRawRefresh()
                }
            }
        )
    }

    if (saveIntoLookName != null) {
        EditorDiscardDialog(
            title = stringResource(R.string.editor_save_into_look_title, saveIntoLookName),
            message = stringResource(R.string.editor_save_into_look_message),
            keepText = stringResource(R.string.cancel),
            actionText = stringResource(R.string.editor_save_into_look),
            onKeep = onCloseSaveIntoLook,
            onDiscard = {
                onCloseSaveIntoLook()
                scope.launch { if (viewModel.saveRecipeIntoLook()) noteLine.say(R.string.editor_saved_into_look) }
            }
        )
    }

    if (discard) {
        EditorDiscardDialog(
            message = stringResource(R.string.editor_discard_message),
            onKeep = onCloseDiscard,
            onDiscard = {
                onCloseDiscard()
                onLeave()
            }
        )
    }
}

/** Portrait's cheap blur: the preview's layers, one per BlurPreview.LEVELS strength, as state read where drawn. */
@Composable
private fun portraitBlurLayers(
    viewModel: GalleryViewModel,
    photo: MediaData,
    portraitOpen: Boolean,
    editPhotoRecipeParams: ColorRecipeParams?,
    editLutId: String?,
    editCropRect: RectF?,
    editRotationDegrees: Int,
    editStraightenDegrees: Float,
    editMirrorHorizontal: Boolean,
    blurMaps: GalleryViewModel.BlurMaps?,
    ringDrag: Offset?,
    editFocusX: Float?,
    editFocusY: Float?,
    portraitQuick: Boolean,
    previewDepthOfField: Int?,
    previewStrength: Float,
    contentBounds: () -> Rect?,
): State<List<ImageBitmap?>> {
    // Portrait's cheap preview: the photo as edited but without the blur, small, loaded once per edit state
    // (one ordinary preview render, never while dragging), then blurred on the CPU from the subject cut-out.
    var sharpBase by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        portraitOpen, photo.id, editPhotoRecipeParams, editLutId,
        editCropRect, editRotationDegrees, editStraightenDegrees, editMirrorHorizontal,
    ) {
        if (!portraitOpen) return@LaunchedEffect
        viewModel.prepareBlurMaps(photo)
        sharpBase = withContext(Dispatchers.IO) {
            viewModel.getPreviewBitmap(
                photo,
                useGlobalEdit = true,
                ignoreFrame = true,
                ignoreBokeh = true,
                maxEdge = 720,
            )
        }
    }

    // The preview at each of BlurPreview.LEVELS, made on the CPU whenever the photo, the focus or the mode
    // changes; the strength in use comes first so the preview shows at once, the rest follow for scrolling.
    val blurLayers = remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    LaunchedEffect(portraitOpen, sharpBase, blurMaps, ringDrag, editFocusX, editFocusY, portraitQuick, previewDepthOfField) {
        val base = sharpBase
        val maps = blurMaps?.takeIf { it.photoId == photo.id }
        if (!portraitOpen || base == null || maps == null) {
            blurLayers.value = emptyList()
            return@LaunchedEffect
        }
        delay(60)  // a burst of drags only computes the last one
        val bounds = contentBounds()
        val focus = ringDrag?.takeIf { bounds != null && bounds.width > 0f }?.let { p ->
            viewModel.displayPointToSource(
                ((p.x - bounds!!.left) / bounds.width).coerceIn(0f, 1f),
                ((p.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
            )
        } ?: ((editFocusX ?: 0.5f) to (editFocusY ?: 0.5f))
        val prepared = withContext(Dispatchers.Default) {
            BlurPreview.prepare(
                base = base,
                toSource = viewModel::displayPointToSource,
                mask = maps.mask,
                depth = maps.depth,
                focusX = focus.first,
                focusY = focus.second,
                quick = portraitQuick,
                widthScale = PortraitBlurMetadata.focalWidthScale(previewDepthOfField),
            )
        }
        val layers = MutableList<ImageBitmap?>(BlurPreview.LEVELS.size) { null }
        for (level in BlurPreview.LEVELS.indices.sortedBy { abs(BlurPreview.LEVELS[it] - previewStrength) }) {
            layers[level] = withContext(Dispatchers.Default) {
                BlurPreview.layer(prepared, BlurPreview.LEVELS[level]).asImageBitmap()
            }
            blurLayers.value = layers.toList()
        }
    }
    return blurLayers
}

/** RAW: the profiles embedded in the photo's DNG, read again whenever the file changes. */
@Composable
private fun embeddedDngProfiles(photo: MediaData?, isRaw: Boolean, refreshKey: Long): EmbeddedDngProfileUiState {
    val context = LocalContext.current
    val dngProfileStamp = remember(photo?.id, refreshKey, isRaw) {
        photo
            ?.takeIf { isRaw }
            ?.let { GalleryManager.getDngFile(context, it.id) }
            ?.takeIf { it.exists() }
            ?.let { it.lastModified() xor it.length() }
            ?: 0L
    }
    val embeddedProfileFallbackName = stringResource(
        R.string.settings_raw_profile_tone_map_embedded
    )
    var embeddedDngProfileUiState by remember(photo?.id, dngProfileStamp) {
        mutableStateOf(EmbeddedDngProfileUiState())
    }

    LaunchedEffect(
        photo?.id,
        dngProfileStamp,
        isRaw,
        embeddedProfileFallbackName,
    ) {
        embeddedDngProfileUiState = if (photo != null && isRaw) {
            withContext(Dispatchers.IO) {
                val dngFile = GalleryManager.getDngFile(context, photo.id)
                val profiles = DngEmbeddedProfile.readAllFrom(dngFile)
                val visibleProfiles = profiles.filterNot { it.isPhotonHdr }
                PLog.d(
                    "GalleryEditScreen",
                    "DNG embedded profiles: " + profiles.joinToString { profile ->
                        "${profile.id}:${profile.profileName}" +
                            "(pgtm=${profile.hasProfileGainTableMap},photon=${profile.isPhotonHdr})"
                    }
                )
                EmbeddedDngProfileUiState(
                    options = visibleProfiles.map { profile ->
                        RawEmbeddedDngProfileOption(
                            id = profile.id,
                            name = profile.profileName
                                .trim()
                                .takeUnless {
                                    it.isEmpty() || it.equals("Embedded", ignoreCase = true)
                                }
                                ?: embeddedProfileFallbackName,
                            hasProfileGainTableMap = profile.hasProfileGainTableMap,
                        )
                    },
                    defaultProfileId = visibleProfiles
                        .firstOrNull { it.id == DngEmbeddedProfile.PRIMARY_PROFILE_ID }
                        ?.id,
                )
            }
        } else {
            EmbeddedDngProfileUiState()
        }
    }
    return embeddedDngProfileUiState
}

/** The purchase dialog, and the downloads of the AI denoise and depth models the first time each is chosen. */
@Composable
private fun EditorModelDialogs(
    viewModel: GalleryViewModel,
    payment: Boolean,
    aiDenoiseModel: AiDenoiseModelState?,
    depthModel: DepthModelDownloadState?,
    onImportDepthModel: () -> Unit,
    onDismissAiDenoise: () -> Unit,
    onDismissDepth: () -> Unit,
) {
    val context = LocalContext.current
    if (payment) {
        val activity = context.findActivity()
        PaymentDialog(
            onDismiss = { viewModel.showPaymentDialog = false },
            onPurchase = {
                if (activity != null) {
                    viewModel.purchase(activity)
                }
                viewModel.showPaymentDialog = false
            }
        )
    }

    if (aiDenoiseModel != null) {
        AiDenoiseModelDownloadDialog(
            state = aiDenoiseModel,
            onDownload = { AiDenoiseModelManager.download(context) },
            onDismiss = {
                onDismissAiDenoise()
                viewModel.setAiDenoiseEnabled(false)
            }
        )
    }

    if (depthModel != null) {
        DepthModelDownloadDialog(
            state = depthModel,
            onDownload = { DepthModelManager.download(context) },
            onImport = onImportDepthModel,
            onDismiss = onDismissDepth
        )
    }
}

/**
 * RAW: develops the photo again after a RAW change. While editing that is the quick preview develop, one at a time
 * (changes made meanwhile develop once more when it ends); Re-render writes the edits and develops in full. Returns
 * the request, which takes whether Photon HDR's tone map is to be rebuilt.
 */
@Composable
private fun rawPreviewRefresh(
    viewModel: GalleryViewModel,
    ui: EditorUiState,
    photo: MediaData,
    isRaw: Boolean,
    hdPreview: State<Boolean>,
    scope: CoroutineScope,
): (Boolean) -> Unit {
    val context = LocalContext.current

    fun refreshRawPreview(forceRegeneratePhotonPgtm: Boolean) {
        // shortcut: assumes the files develop from the state saved just before; a change saved while this
        // starts could be cached under the wrong state. Only a few small earlier looks are kept, so the cost is low.
        val requested = viewModel.editRawState()
        val engineAtStart = viewModel.editRawRenderingEngine.value.name
        val startedAt = SystemClock.uptimeMillis()
        viewModel.refreshRawPreview(
            photo = photo,
            forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
        ) { success ->
            if (success) {
                ui.developedRaw = requested
                ui.rawDevelopMs["full:$engineAtStart"] = SystemClock.uptimeMillis() - startedAt
                ui.rawPreviewBase = null
            }
        }
    }

    fun developRawPreview() {
        if (ui.previewDeveloping) {
            ui.previewAgain = true
            return
        }
        ui.previewDeveloping = true
        scope.launch {
            // A develop holds the GPU for seconds: it waits until the user pauses, then takes the settings as
            // they are by then.
            viewModel.awaitEditorPause()
            ui.previewAgain = false
            val state = viewModel.editRawState()
            val meta = viewModel.editRawPreviewMetadata(photo)
            val engineAtStart = viewModel.editRawRenderingEngine.value.name
            val startedAt = SystemClock.uptimeMillis()
            val bitmap = GalleryManager.developRawPreview(context, photo.id, meta, halfSize = !hdPreview.value)
            if (bitmap != null) {
                ui.rawPreviewBase = bitmap
                ui.developedRaw = state
                ui.rawDevelopMs[engineAtStart] = SystemClock.uptimeMillis() - startedAt
            }
            ui.previewDeveloping = false
            if (ui.previewAgain) {
                ui.previewAgain = false
                developRawPreview()
            }
        }
    }

    fun requestRawPreviewRefresh(forceRegeneratePhotonPgtm: Boolean) {
        if (!isRaw) return
        if (viewModel.deferEditWrites && !forceRegeneratePhotonPgtm) {
            developRawPreview()
            return
        }
        if (viewModel.rawEditsPending) {
            viewModel.writeRawEdits(photo) { requestRawPreviewRefresh(forceRegeneratePhotonPgtm) }
            return
        }
        if (viewModel.refreshingPhotos.contains(photo.id)) {
            ui.pendingPgtmRegeneration = ui.pendingPgtmRegeneration || forceRegeneratePhotonPgtm
            ui.pendingRawRefresh = true
            return
        }
        refreshRawPreview(forceRegeneratePhotonPgtm)
    }

    val isRefreshingRawPreview = viewModel.refreshingPhotos.contains(photo.id)
    LaunchedEffect(isRefreshingRawPreview, ui.pendingRawRefresh, photo.id) {
        if (!isRefreshingRawPreview && ui.pendingRawRefresh) {
            val forceRegeneratePhotonPgtm = ui.pendingPgtmRegeneration
            ui.pendingRawRefresh = false
            ui.pendingPgtmRegeneration = false
            refreshRawPreview(forceRegeneratePhotonPgtm)
        }
    }
    // Switching HD develops the waiting RAW changes again at the other size.
    LaunchedEffect(hdPreview.value) {
        if (isRaw && viewModel.rawEditsPending) developRawPreview()
    }
    return ::requestRawPreviewRefresh
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** The reference photo set from a film's page: a small card that can be dragged, enlarged or pinned away. */
@Composable
private fun ReferencePhoto(referencePhotoUrl: String?, onClose: () -> Unit) {
    var isMinimized by remember { mutableStateOf(false) }
    var isLarge by remember { mutableStateOf(false) }

    referencePhotoUrl?.let { url ->
        val density = androidx.compose.ui.platform.LocalDensity.current
        val initialOffsetX = remember(density) { with(density) { 20.dp.toPx() } }
        val initialOffsetY = remember(density) { with(density) { 80.dp.toPx() } }

        var offsetX by remember { mutableStateOf(initialOffsetX) }
        var offsetY by remember { mutableStateOf(initialOffsetY) }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .zIndex(10f)
        ) {
            if (isMinimized) {
                Card(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { isMinimized = false },
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.5.dp, Color.White)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.PushPin,
                            contentDescription = "Show Reference",
                            tint = AccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                val cardSize = if (isLarge) 240.dp else 120.dp
                Card(
                    modifier = Modifier
                        .width(cardSize),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            coil.compose.AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .placeholder(R.mipmap.ic_launcher)
                                    .build(),
                                contentDescription = "Reference Photo",
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .clickable { isMinimized = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.PushPin,
                                        contentDescription = "Minimize",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .clickable { onClose() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .clickable { isLarge = !isLarge },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isLarge) stringResource(R.string.zoom_out) else stringResource(R.string.zoom_in),
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用于编辑界面的可缩放图片组件
 * 使用 Telephoto 库支持大尺寸图片查看和缩放
 */
@Composable
private fun ZoomableEditImage(
    previewBitmap: Bitmap?,
    resetKey: Any?,
    contentDescription: String,
    onZoomChange: (isZoomed: Boolean) -> Unit = {},
    onContentBounds: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxZoom = previewBitmap?.let { max(it.width, it.height) / 500f } ?: 1f
    val zoomableState = rememberZoomableImageState(
        zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = maxZoom))
    )

    LaunchedEffect(zoomableState.zoomableState.zoomFraction) {
        onZoomChange((zoomableState.zoomableState.zoomFraction ?: 0f) > 0.01f)
    }

    LaunchedEffect(resetKey) {
        zoomableState.zoomableState.resetZoom()
    }

    // Where the photo is drawn, zoomed and panned, so marks on it (the focus ring) follow it.
    val currentOnContentBounds by rememberUpdatedState(onContentBounds)
    LaunchedEffect(zoomableState) {
        snapshotFlow { zoomableState.zoomableState.transformedContentBounds }
            .collect { currentOnContentBounds(it) }
    }

    val model = ImageRequest.Builder(context)
        .data(previewBitmap)
        .crossfade(true)
        .build()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ZoomableAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            state = zoomableState,
            onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 3f),
            modifier = Modifier.fillMaxSize()
        )
    }
}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoEditPlayer(
    photo: MediaData,
    lutConfig: LutConfig?,
    recipeParams: ColorRecipeParams?,
    sourceLogProfile: VideoLogProfile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaUri = remember(photo.id, photo.uri, photo.sourceUri) {
        photo.sourceUri ?: photo.uri
    }
    
    PLog.d("VideoEditPlayer", "VideoEditPlayer composable recomposing/initializing. photoId: ${photo.id}, mediaUri: $mediaUri")

    var isPlayerActive by remember { mutableStateOf(false) }
    LaunchedEffect(photo.id) {
        delay(150) // Wait for the transitions to complete and EGL context release
        isPlayerActive = true
    }

    val videoLutEffect = remember(photo.id, mediaUri, sourceLogProfile) {
        PLog.d("VideoEditPlayer", "Instantiating new VideoLutEffect.")
        VideoLutEffect(lutConfig, recipeParams, sourceLogProfile)
    }
    
    val exoPlayer = remember(photo.id, mediaUri, isPlayerActive, videoLutEffect) {
        if (!isPlayerActive) return@remember null
        PLog.d("VideoEditPlayer", "Re-creating loopable ExoPlayer instance for video preview.")
        ExoPlayer.Builder(context, VideoEditRenderersFactory(context, sourceLogProfile.isEnabled)).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            repeatMode = Player.REPEAT_MODE_ONE
            setVideoEffects(listOf(videoLutEffect))
            prepare()
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    PLog.d("VideoEditPlayer", "ExoPlayer state changed: $state")
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    PLog.e("VideoEditPlayer", "ExoPlayer encountered playback error!", error)
                }
            })
        }
    }

    LaunchedEffect(lutConfig, recipeParams, exoPlayer) {
        PLog.d("VideoEditPlayer", "Updating VideoLutEffect with lutConfig: ${lutConfig?.title}, recipeParams: ${recipeParams != null}")
        videoLutEffect.update(lutConfig, recipeParams)

        // A paused player does not submit another frame to the GL effect pipeline. REDRAW asks the
        // Media3 video graph to process its retained frame again without seeking or changing the
        // playback state, so the controller stays hidden and the new LUT is visible immediately.
        if (exoPlayer != null && !exoPlayer.playWhenReady) {
            exoPlayer.setVideoEffects(VideoFrameProcessor.REDRAW)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            if (exoPlayer != null) {
                PLog.d("VideoEditPlayer", "Disposing loopable ExoPlayer.")
                exoPlayer.release()
            }
        }
    }

    if (exoPlayer != null) {
        AndroidView(
            factory = {
                PLog.d("VideoEditPlayer", "Creating PlayerView factory.")
                LayoutInflater.from(context).inflate(R.layout.view_motion_photo_player, null) as PlayerView
            },
            update = {
                PLog.d("VideoEditPlayer", "Updating PlayerView with ExoPlayer.")
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                it.useController = true
                it.controllerAutoShow = true
                it.player = exoPlayer
                it.visibility = android.view.View.VISIBLE
            },
            modifier = modifier.autoRotate(matchParentSize = true)
        )
    } else {
        Spacer(modifier = modifier.fillMaxSize())
    }
}

/** A choice on the photo's bottom edge, as the editor's other choices: 36 dp on a dark see-through fill. */
@Composable
private fun EditorChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: Boolean = false,
    dim: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    LilacChip(
        text = text,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        height = 36.dp,
        fill = Color(0xB8141413),
        fontSize = 13.sp,
        horizontalPadding = 13.dp,
        icon = icon,
        action = action,
        modifier = Modifier.graphicsLayer { alpha = if (dim) 0.5f else 1f }
    )
}

/**
 * A master curve standing in for the RAW develop's Blacks/Whites levels (raw/RawAdjustmentPass): undoes the levels
 * the shown develop was made with and applies the current ones, in linear light, then the recipe's own master curve.
 */
private fun rawLevelsCurve(blacksWas: Float, whitesWas: Float, blacks: Float, whites: Float, master: FloatArray?): FloatArray {
    fun blackLevel(b: Float) = b.coerceIn(-1f, 1f).let { if (it < 0f) -it * 0.18f else -it * 0.12f }
    fun whiteLevel(w: Float, black: Float) = w.coerceIn(-1f, 1f)
        .let { if (it > 0f) 1f - 0.28f * it else 1f + 0.55f * -it }
        .coerceAtLeast(black + 0.05f)
    val blWas = blackLevel(blacksWas)
    val wlWas = whiteLevel(whitesWas, blWas)
    val bl = blackLevel(blacks)
    val wl = whiteLevel(whites, bl)
    val masterLut = CurveUtils.evaluateCurve(master)
    val points = FloatArray(34)
    for (k in 0..16) {
        val x = k / 16f
        val scene = blWas + OklchConverter.srgbToLinear(x) * (wlWas - blWas)
        val y = OklchConverter.linearToSrgb(((scene - bl) / (wl - bl)).coerceIn(0f, 1f))
        points[2 * k] = x
        points[2 * k + 1] = masterLut[(y * (CurveUtils.LUT_SIZE - 1)).roundToInt()]
    }
    return points
}
