package com.hinnka.mycamera.ui.gallery

import com.hinnka.mycamera.ui.prefs.preferenceValue
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.hinnka.mycamera.ui.camera.LutEditBottomSheet
import com.hinnka.mycamera.ui.camera.LutEditorTarget
import com.hinnka.mycamera.ui.components.*
import com.hinnka.mycamera.ui.components.RawEditPanel
import com.hinnka.mycamera.ui.components.RawBaselineColorCorrectionBottomSheet
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
import com.hinnka.mycamera.lut.VideoLutEffect
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.ui.camera.autoRotate
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
    val isDepthModelInstalled = DepthModelManager.isInstalled(context)
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
    var showBaselineLutEditSheet by remember { mutableStateOf(false) }
    var baselineLutEditId by remember { mutableStateOf<String?>(null) }
    var showRawBaselineLutSelectorSheet by remember { mutableStateOf(false) }

    // 预览 Bitmap 状态
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropPreviewBitmap by remember(editSourcePhoto?.id) { mutableStateOf<Bitmap?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageHistogram by remember { mutableStateOf<ImageHistogram?>(null) }

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
    val dngProfileStamp = remember(editSourcePhoto?.id, refreshKey, isRaw) {
        editSourcePhoto
            ?.takeIf { isRaw }
            ?.let { GalleryManager.getDngFile(context, it.id) }
            ?.takeIf { it.exists() }
            ?.let { it.lastModified() xor it.length() }
            ?: 0L
    }
    val embeddedProfileFallbackName = stringResource(
        R.string.settings_raw_profile_tone_map_embedded
    )
    var embeddedDngProfileUiState by remember(editSourcePhoto?.id, dngProfileStamp) {
        mutableStateOf(EmbeddedDngProfileUiState())
    }

    LaunchedEffect(
        editSourcePhoto?.id,
        dngProfileStamp,
        isRaw,
        embeddedProfileFallbackName,
    ) {
        val photo = editSourcePhoto
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
    val activeEmbeddedDngProfileId = editRawEmbeddedDngProfileId
        ?.takeIf { selectedId ->
            embeddedDngProfileUiState.options.any { it.id == selectedId }
        }
        ?: embeddedDngProfileUiState.defaultProfileId

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
    // Taps on the value row: the last one, to tell a double tap, and a second tap's card waiting out that window.
    var lastTapKey by remember { mutableStateOf<String?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var pendingCard by remember { mutableStateOf<Job?>(null) }
    // Portrait: the aperture while the ruler moves (rendered once, on release), the photo's place on screen
    // inside the zoomable view, and the focus ring while a finger drags it.
    var apertureDrag by remember { mutableStateOf<Float?>(null) }
    var contentBounds by remember { mutableStateOf<Rect?>(null) }
    var ringGrab by remember { mutableStateOf(false) }
    var ringDrag by remember { mutableStateOf<Offset?>(null) }
    // The recipe as it was when a long press on one setting began, to put back when it ends.
    var heldRecipe by remember { mutableStateOf<ColorRecipeParams?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var fingerOnPhoto by remember { mutableStateOf(false) }
    var fingerOnRuler by remember { mutableStateOf(false) }
    var pendingPhotoTap by remember { mutableStateOf<Job?>(null) }
    val cropView = section == EditorSection.Crop && currentPhoto?.isVideo != true
    var isZoomed by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val isBaselineLutEditSheetVisible = showBaselineLutEditSheet && baselineLutEditId != null

    var previewRenderRequestId by remember { mutableLongStateOf(0L) }
    val shouldCalculateImageHistogram = isBaselineLutEditSheetVisible

    fun currentPreviewSignature(fast: Boolean = false): PreviewRenderSignature? {
        val photo = editSourcePhoto ?: return null
        val rawDevelopIsBaked = isRaw
        return PreviewRenderSignature(
            photoId = photo.id,
            refreshKey = refreshKey,
            editLutId = editLutId,
            editPhotoRecipeParams = editPhotoRecipeParams,
            editLutRecipeParams = editLutRecipeParams,
            editLutConfig = editLutConfig,
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
            editRotationDegrees = editRotationDegrees,
            // 裁剪页通过 Compose Canvas 实时校正，不触发防抖后的 Bitmap 重渲染。
            editStraightenDegrees = if (section == EditorSection.Crop) 0f else editStraightenDegrees,
            editMirrorHorizontal = editMirrorHorizontal,
            showOrigin = showOrigin,
            cropView = section == EditorSection.Crop,
            portraitView = section == EditorSection.Portrait
        )
    }

    suspend fun renderPreview(
        photo: MediaData,
        maxEdge: Int,
        cancelStaleResult: Boolean
    ) {
        val requestId = ++previewRenderRequestId
        val isCropPreview = section == EditorSection.Crop
        val bitmap = withContext(Dispatchers.IO) {
            viewModel.getPreviewBitmap(
                photo,
                useGlobalEdit = true,
                showOrigin = showOrigin,
                ignoreCrop = isCropPreview,
                ignoreStraighten = isCropPreview,
                // Portrait shows the photo without its frame, so the focus ring lands on the photo itself.
                ignoreFrame = isCropPreview || section == EditorSection.Portrait,
                maxEdge = maxEdge
            )
        }
        if ((!cancelStaleResult || requestId == previewRenderRequestId) && bitmap != null) {
            if (isCropPreview) {
                if (section == EditorSection.Crop) {
                    cropPreviewBitmap = bitmap
                }
            } else {
                previewBitmap = bitmap
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

    val rawDcpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val photo = editSourcePhoto ?: return@rememberLauncherForActivityResult
        scope.launch {
            val importedDcps = viewModel.importRawDcps(uris)
            val failedCount = uris.size - importedDcps.size
            importedDcps.lastOrNull()?.let {
                viewModel.saveRawDcpSelection(photo, it.id) {
                    viewModel.refreshRawPreview(
                        photo = photo,
                    )
                }
            }
            when {
                importedDcps.size == 1 && failedCount == 0 -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_success, importedDcps.first().getName()), Toast.LENGTH_SHORT).show()
                }
                importedDcps.isNotEmpty() && failedCount == 0 -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_success_count, importedDcps.size), Toast.LENGTH_SHORT).show()
                }
                importedDcps.isNotEmpty() -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_partial, importedDcps.size, failedCount), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey, isRaw) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        // RAW decoding is already the expensive source load. Running the fast JPEG-style pass
        // before the full pass decodes the same DNG twice for every refresh.
        if (photo.isVideo || isRaw) return@LaunchedEffect
        snapshotFlow {
            currentPreviewSignature(true)
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
            currentPreviewSignature()
        }
            .filter { it != null }
            .conflate()
            .debounce(250)
            .collectLatest {
                renderPreview(photo, maxEdge = 4096, cancelStaleResult = true)
            }
    }

    LaunchedEffect(editSourcePhoto) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        thumbnailBitmap = withContext(Dispatchers.IO) {
            viewModel.loadThumbnail(photo)
        }
    }

    LaunchedEffect(shouldCalculateImageHistogram, previewBitmap) {
        val bitmap = previewBitmap
        if (!shouldCalculateImageHistogram || bitmap == null || bitmap.isRecycled) {
            imageHistogram = null
            return@LaunchedEffect
        }

        imageHistogram = try {
            withContext(Dispatchers.Default) {
                ImageHistogram.fromBitmap(bitmap)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PLog.e(
                "GalleryEditScreen",
                "Failed to calculate histogram from the current preview",
                error,
            )
            null
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
        Toast.makeText(context, context.getString(R.string.ai_denoise_failed), Toast.LENGTH_SHORT).show()
    }
    val startAiDenoise = {
        viewModel.applyAiDenoise(photo = currentEditSourcePhoto) { success ->
            if (!success) {
                viewModel.setAiDenoiseEnabled(false)
                showAiDenoiseFailure()
            }
        }
    }
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
    val changedKeys = remember(
        editLutId, editPhotoRecipeParams, editFrameId, applyEffectsToVideo, editSharpening, editNoiseReduction,
        editChromaNoiseReduction, editAiDenoiseEnabled, editRawExposureCompensation, editRawAutoExposure,
        editRawHighlightsAdjustment, editRawShadowsAdjustment, editRawBlackPointCorrection, editRawWhitePointCorrection,
        editRawLensShadingCorrectionEnabled, editRawDROMode, editRawBlackLevelMode, editRawCustomBlackLevel,
        editRawWhiteLevelMode, editRawCustomWhiteLevel, editRawCfaCorrectionMode, editRawDcpId, editRawEmbeddedDngProfileId,
        editRawHncsProfileId, editRawHncsRenderIntent, editRawHncsFilmCurveMode, editRawBaselineLutId, editRawColorEngine,
        editRawToneMappingParameters, editRawSpectralFilmStock, editRawSpectralFilmPrint, editRawSpectralFilmCDensityGain,
        editRawSpectralFilmMDensityGain, editRawSpectralFilmYDensityGain, editRawFotufilmDocument, editComputationalAperture,
        editBokehStyle, editFocusX, editFocusY, editCropRect, editCropAspectOption, editRotationDegrees,
        editStraightenDegrees, editMirrorHorizontal, editPortraitQuick,
    ) { viewModel.editChangedKeys() }
    val openIntensity = remember(currentEditSourcePhoto.id) { (editPhotoRecipeParams ?: editLutRecipeParams).lutIntensity }
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
    var awaitingBlur by remember { mutableStateOf(false) }
    LaunchedEffect(isBokehProcessing, isLoadingPreview) {
        if (isBokehProcessing) awaitingBlur = true else if (!isLoadingPreview) awaitingBlur = false
    }

    // Portrait's cheap preview: the photo as edited but without the blur, small, loaded once per edit state
    // (one ordinary preview render, never while dragging), then blurred on the CPU from the subject cut-out.
    val portraitOpen = section == EditorSection.Portrait && !currentPhoto.isVideo
    var sharpBase by remember(currentEditSourcePhoto.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        portraitOpen, currentEditSourcePhoto.id, editPhotoRecipeParams, editLutId,
        editCropRect, editRotationDegrees, editStraightenDegrees, editMirrorHorizontal,
    ) {
        if (!portraitOpen) return@LaunchedEffect
        viewModel.prepareBlurMaps(currentEditSourcePhoto)
        sharpBase = withContext(Dispatchers.IO) {
            viewModel.getPreviewBitmap(
                currentEditSourcePhoto,
                useGlobalEdit = true,
                ignoreFrame = true,
                ignoreBokeh = true,
                maxEdge = 720,
            )
        }
    }
    // The ruler's place between two stops while it is dragged, so the preview's blur grows with the finger.
    var apertureScroll by remember { mutableStateOf<Float?>(null) }
    val previewStrength = apertureScroll?.let { position ->
        val lower = position.toInt().coerceIn(0, ApertureStops.lastIndex)
        val upper = (lower + 1).coerceAtMost(ApertureStops.lastIndex)
        val t = position - lower
        BlurPreview.strength(ApertureStops[lower]) * (1 - t) + BlurPreview.strength(ApertureStops[upper]) * t
    } ?: BlurPreview.strength(apertureDrag ?: editComputationalAperture ?: 0f)

    // The preview at each of BlurPreview.LEVELS, made on the CPU whenever the photo, the focus or the mode
    // changes; the strength in use comes first so the preview shows at once, the rest follow for scrolling.
    var blurLayers by remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    LaunchedEffect(portraitOpen, sharpBase, blurMaps, ringDrag, editFocusX, editFocusY, portraitQuick) {
        val base = sharpBase
        val maps = blurMaps?.takeIf { it.photoId == currentEditSourcePhoto.id }
        if (!portraitOpen || base == null || maps == null) {
            blurLayers = emptyList()
            return@LaunchedEffect
        }
        delay(60)  // a burst of drags only computes the last one
        val bounds = contentBounds
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
            )
        }
        val layers = MutableList<ImageBitmap?>(BlurPreview.LEVELS.size) { null }
        for (level in BlurPreview.LEVELS.indices.sortedBy { abs(BlurPreview.LEVELS[it] - previewStrength) }) {
            layers[level] = withContext(Dispatchers.Default) {
                BlurPreview.layer(prepared, BlurPreview.LEVELS[level]).asImageBitmap()
            }
            blurLayers = layers.toList()
        }
    }

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
            Toast.makeText(context, R.string.editor_quick_focus_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val b = contentBounds?.takeIf { it.width > 0f && it.height > 0f } ?: return
        viewModel.setFocusPoint(
            ((position.x - b.left) / b.width).coerceIn(0f, 1f),
            ((position.y - b.top) / b.height).coerceIn(0f, 1f),
        )
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
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.navigationBars()) }
    }

    val previewSourceWidth = previewBitmap?.width?.takeIf { it > 0 }
        ?: currentPhoto.width.takeIf { it > 0 }
    val previewSourceHeight = previewBitmap?.height?.takeIf { it > 0 }
        ?: currentPhoto.height.takeIf { it > 0 }
    var pendingRawPreviewRefresh by remember(currentEditSourcePhoto.id) { mutableStateOf(false) }
    var pendingPhotonPgtmRegeneration by remember(currentEditSourcePhoto.id) { mutableStateOf(false) }
    var pendingRawRefreshToast by remember(currentEditSourcePhoto.id) { mutableStateOf(false) }
    val isRefreshingRawPreview = viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id)

    fun refreshRawPreview(
        showResultToast: Boolean = false,
        forceRegeneratePhotonPgtm: Boolean = false,
    ) {
        viewModel.refreshRawPreview(
            photo = currentEditSourcePhoto,
            forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
        ) { success ->
            if (showResultToast) {
                Toast.makeText(
                    context,
                    if (success) R.string.refresh_success else R.string.refresh_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun requestRawPreviewRefresh(
        showResultToast: Boolean = false,
        forceRegeneratePhotonPgtm: Boolean = false,
    ) {
        if (!isRaw) return
        if (viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id)) {
            pendingPhotonPgtmRegeneration = pendingPhotonPgtmRegeneration || forceRegeneratePhotonPgtm
            pendingRawRefreshToast = pendingRawRefreshToast || showResultToast
            pendingRawPreviewRefresh = true
            return
        }
        refreshRawPreview(
            showResultToast = showResultToast,
            forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
        )
    }

    LaunchedEffect(isRefreshingRawPreview, pendingRawPreviewRefresh, currentEditSourcePhoto.id) {
        if (!isRefreshingRawPreview && pendingRawPreviewRefresh) {
            val forceRegeneratePhotonPgtm = pendingPhotonPgtmRegeneration
            val showResultToast = pendingRawRefreshToast
            pendingRawPreviewRefresh = false
            pendingPhotonPgtmRegeneration = false
            pendingRawRefreshToast = false
            refreshRawPreview(
                showResultToast = showResultToast,
                forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
            )
        }
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
            // Draggable Floating Reference Photo
            val referencePhotoUrl = userPreferences.referencePhotoUrl
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
                                                .clickable {
                                                    scope.launch {
                                                        userPreferencesRepository.update { this.referencePhotoUrl = null }
                                                    }
                                                },
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

            // The photo, placed by its shape: above the tool block while editing, the whole screen in full view.
            // Crop keeps the old frame tool, over the area between the edge icons and the control layer.
            val screenW = with(density) { viewportSize.width.toDp().value }
            val screenH = with(density) { viewportSize.height.toDp().value }
            val photoAspect = if (previewSourceWidth != null && previewSourceHeight != null) {
                previewSourceWidth.toFloat() / previewSourceHeight
            } else {
                3f / 4f
            }
            val targetRect = if (cropView) {
                EditorRect(0f, EDITOR_EDGE_DP, screenW, screenH - EDITOR_BLOCK_DP - EDITOR_CONTROL_DP - EDITOR_EDGE_DP)
            } else {
                editorPhotoRect(photoAspect, screenW, screenH, fullView)
            }
            val photoMotion = tween<Float>(250)
            val photoX by animateFloatAsState(targetRect.x, photoMotion, label = "photoX")
            val photoY by animateFloatAsState(targetRect.y, photoMotion, label = "photoY")
            val photoW by animateFloatAsState(targetRect.w, photoMotion, label = "photoW")
            val photoH by animateFloatAsState(targetRect.h, photoMotion, label = "photoH")

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
                                placeFocus(position)
                            } finally {
                                ringGrab = false
                                ringDrag = null
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
                                    val secondTap = pendingPhotoTap?.isActive == true
                                    pendingPhotoTap?.cancel()
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
                                                pendingPhotoTap = scope.launch {
                                                    delay(300)
                                                    // A tap opens full view, and a tap in full view comes back. In
                                                    // Portrait a tap sets the focus instead; full view is the edge icon.
                                                    when {
                                                        fullView -> fullView = false
                                                        section == EditorSection.Portrait -> placeFocus(tapAt)
                                                        section != EditorSection.Crop -> fullView = true
                                                    }
                                                }
                                            }
                                        } else {
                                            // 确认为长按：显示原图
                                            showOrigin = true
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
                val previewMediaModifier = Modifier.fillMaxSize()

                // 显示预览
                if (currentPhoto.isVideo) {
                    if (videoSourceProfile != null) {
                        VideoEditPlayer(
                            photo = currentPhoto,
                            lutConfig = if (showOrigin) null else editLutConfig,
                            recipeParams = if (showOrigin) null else (editPhotoRecipeParams ?: editLutRecipeParams),
                            sourceLogProfile = videoSourceProfile,
                            modifier = previewMediaModifier
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (cropView && cropPreviewBitmap != null) {
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
                    CropOverlay(
                        bitmap = cropPreviewBitmap,
                        cropRect = editCropRect ?: cropBounds,
                        cropAspectRatio = cropAspectRatio,
                        straightenDegrees = editStraightenDegrees,
                        geometrySourceWidth = straightenSourceWidth,
                        geometrySourceHeight = straightenSourceHeight,
                        onCropRectChanged = { rect -> viewModel.setCropRect(rect) },
                        aspectOption = editCropAspectOption,
                        contentPadding = 28.dp,
                        modifier = previewMediaModifier
                    )
                } else {
                    ZoomableEditImage(
                        previewBitmap = previewBitmap,
                        resetKey = fullView,
                        contentDescription = stringResource(R.string.edit),
                        onZoomChange = {
                            isZoomed = it
                        },
                        onContentBounds = { contentBounds = it },
                        modifier = previewMediaModifier
                    )
                }

                LutSwipeIndicator(state = lutSwipeState)

                // Portrait: the cheap preview while the blur renders or a control is dragged, cross-fading between
                // the two prepared strengths around the one wanted, so it feels like more steps than there are.
                val previewBounds = contentBounds
                val layers = blurLayers
                if (
                    layers.isNotEmpty() && previewBounds != null && portraitOpen && !fullView &&
                    (previewStrength > 0f || apertureScroll != null) &&
                    (awaitingBlur || apertureDrag != null || apertureScroll != null || ringDrag != null)
                ) {
                    val levels = BlurPreview.LEVELS
                    val upper = levels.indexOfFirst { it >= previewStrength }.let { if (it < 0) levels.lastIndex else it }
                    val lower = (upper - 1).coerceAtLeast(0)
                    val t = if (upper == lower) 1f else ((previewStrength - levels[lower]) / (levels[upper] - levels[lower])).coerceIn(0f, 1f)
                    val under = layers[lower] ?: layers[upper]
                    val over = layers[upper] ?: layers[lower]
                    val dstOffset = IntOffset(previewBounds.left.roundToInt(), previewBounds.top.roundToInt())
                    val dstSize = IntSize(previewBounds.width.roundToInt(), previewBounds.height.roundToInt())
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        under?.let { drawImage(it, dstOffset = dstOffset, dstSize = dstSize) }
                        if (over != null && over !== under) drawImage(over, dstOffset = dstOffset, dstSize = dstSize, alpha = t)
                    }
                }

                // Portrait: the focus ring, on the photo the whole time the section is open (not in Quick,
                // which keeps the cut-out subject sharp wherever the focus is).
                if (portraitOpen && !fullView && !portraitQuick) {
                    val center = ringDrag ?: ringCenter(focusOnPhoto)
                    if (center != null) {
                        PortraitFocusRing(
                            center = center,
                            off = (apertureDrag ?: editComputationalAperture ?: 0f) <= 0f
                        )
                    }
                }
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
                    EditorSetting.Focus, EditorSetting.Bokeh, EditorSetting.Depth -> 0f
                    else -> s.param!!.getValue(effectiveRecipe)
                }

                // Where a setting's reset returns: its value when the editor opened.
                fun openValue(s: EditorSetting): Float {
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
                        s.kind == SettingKind.Denoise -> viewModel.refreshPhotonDenoiseBase(currentEditSourcePhoto)
                        s.kind == SettingKind.Aperture -> apertureDrag?.let { v ->
                            apertureDrag = null
                            viewModel.setComputationalAperture(v.takeIf { it > 0f })
                        }
                    }
                }

                fun setAiDenoise(enabled: Boolean) {
                    if (isAiDenoising || enabled == editAiDenoiseEnabled) return
                    viewModel.setAiDenoiseEnabled(enabled)
                    if (enabled) {
                        if (AiDenoiseModelManager.isInstalled(context)) startAiDenoise() else pendingAiDenoise = true
                    } else {
                        viewModel.resetAiDenoise(photo = currentEditSourcePhoto) { success ->
                            if (!success) {
                                viewModel.setAiDenoiseEnabled(true)
                                showAiDenoiseFailure()
                            }
                        }
                    }
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
                            if (!focusMoved) Toast.makeText(context, R.string.editor_focus_tap_hint, Toast.LENGTH_SHORT).show()
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
                    EditorSetting.forSection(section, isRaw, currentPhoto.isVideo)
                } else {
                    emptyList()
                }
                val groups = if (group == null) EditorGroup.forSection(section, isRaw) else emptyList()
                val offText = stringResource(R.string.editor_off)
                val subjectText = stringResource(R.string.editor_subject)
                val onText = stringResource(R.string.editor_on)
                val values = when {
                    group != null -> emptyList()
                    section == EditorSection.Look -> listOf(
                        EditorValue(
                            "look", stringResource(R.string.editor_look),
                            availableLuts.find { it.id == editLutId }?.getName() ?: stringResource(R.string.none),
                            AppIcons.FiltersLine, "look" in changedKeys, wide = true
                        ),
                        EditorValue(
                            "intensity", stringResource(R.string.editor_intensity), "${(intensity * 100).roundToInt()}%",
                            AppIcons.AdjustmentsLine, "lutIntensity" in changedKeys
                        ),
                        EditorValue(
                            "frame", stringResource(R.string.frame),
                            availableFrames.find { it.id == editFrameId }?.name ?: stringResource(R.string.none),
                            AppIcons.FrameLine, "frame" in changedKeys, wide = true
                        ),
                        EditorValue("preset", stringResource(R.string.editor_preset), "—", AppIcons.BookmarkLine, wide = true),
                    )
                    else -> settings.map { s ->
                        val v = settingValue(s)
                        EditorValue(
                            key = s.key,
                            name = stringResource(s.label),
                            value = when {
                                s.kind == SettingKind.Aperture -> if (v > 0f) "f/${formatAperture(v)}" else offText
                                s == EditorSetting.Focus && quick -> subjectText
                                s == EditorSetting.Depth && blurOff -> offText
                                s.kind == SettingKind.Choice -> choiceOptions(s).firstOrNull { it.selected }?.label.orEmpty()
                                s.kind != SettingKind.AiDenoise -> s.format(v)
                                isAiDenoising -> "${(aiDenoiseProgress * 100).roundToInt()}%"
                                editAiDenoiseEnabled -> onText
                                else -> offText
                            },
                            icon = s.icon,
                            changed = s.key in changedKeys,
                            glyph = s.glyph,
                            fraction = s.fraction(v),
                            dimmed = s.section == EditorSection.Portrait && s != EditorSetting.Aperture &&
                                s != EditorSetting.Depth && blurOff,
                        )
                    } + groups.map { g ->
                        EditorValue(
                            key = "group:${g.name}",
                            name = stringResource(g.label),
                            value = "",
                            icon = g.icon,
                            changed = g.keys.any { it in changedKeys },
                            wide = true,
                            group = true,
                        )
                    }
                }
                val selectable = values.filterNot { it.group }
                val selectedKey = selectable.firstOrNull { it.key == selections[levelKey] }?.key ?: selectable.firstOrNull()?.key
                val selectedSetting = settings.firstOrNull { it.key == selectedKey }

                fun sectionChanged(s: EditorSection): Boolean = when (s) {
                    EditorSection.Light, EditorSection.Color, EditorSection.Effects, EditorSection.Detail ->
                        EditorSetting.forSection(s, isRaw, currentPhoto.isVideo).any { it.key in changedKeys } ||
                            EditorGroup.forSection(s, isRaw).any { g -> g.keys.any { it in changedKeys } }
                    else -> s.keys.any { it in changedKeys }
                }

                EditorEdgeIcons(
                    busy = fingerOnPhoto || fingerOnRuler,
                    rendering = isLoadingPreview || isBokehProcessing || isPhotonDenoising ||
                        isRefreshingRawPreview || isAiDenoising || isSaving,
                    undoAvailable = false,
                    redoAvailable = false,
                    fullView = false,
                    saving = isSaving,
                    onCancel = ::requestLeave,
                    onUndo = { Toast.makeText(context, R.string.editor_undo_later, Toast.LENGTH_SHORT).show() },
                    onRedo = { Toast.makeText(context, R.string.editor_undo_later, Toast.LENGTH_SHORT).show() },
                    onFullView = { fullView = true },
                    onCompare = { showOrigin = it },
                    onSave = {
                        val currentLut = availableLuts.find { it.id == editLutId }
                        if (currentLut?.isVip == true && !isPurchased) {
                            viewModel.showPaymentDialog = true
                        } else {
                            isSaving = true
                            viewModel.saveEditMetadata(currentPhoto) { success ->
                                isSaving = false
                                if (success) {
                                    onBack()
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // What the blur is doing, under the edge icons, while it works.
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
                if (blurStatus != null) {
                    Text(
                        text = blurStatus,
                        color = LilacText,
                        fontSize = 12.sp,
                        style = LilacTextStyle,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = EDITOR_EDGE_DP.dp)
                    )
                }

                // The explanation of the selected setting, on a second tap on it.
                val explained = settings.firstOrNull { it.key == explainKey }
                if (explained != null) {
                    // A look's own exposure, highlights or shadows stay applied on a RAW photo, hidden; say so.
                    val lookPart = when (explained) {
                        EditorSetting.RawExposure -> EditorSetting.Exposure
                        EditorSetting.RawHighlights -> EditorSetting.Highlights
                        EditorSetting.RawShadows -> EditorSetting.Shadows
                        else -> null
                    }?.let { part ->
                        val v = part.param!!.getValue(effectiveRecipe)
                        if (v != part.param.defaultValue) {
                            " " + stringResource(R.string.editor_look_also_sets, stringResource(part.label), part.format(v))
                        } else {
                            ""
                        }
                    }.orEmpty()
                    EditorExplanationCard(
                        name = stringResource(explained.label),
                        text = stringResource(explained.explanation) + lookPart,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 8.dp, end = 8.dp, bottom = (EDITOR_BLOCK_DP + EDITOR_CONTROL_DP + 8f).dp)
                    )
                } else if (explainKey == "intensity" && section == EditorSection.Look) {
                    EditorExplanationCard(
                        name = stringResource(R.string.editor_intensity),
                        text = stringResource(R.string.editor_explain_intensity),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 8.dp, end = 8.dp, bottom = (EDITOR_BLOCK_DP + EDITOR_CONTROL_DP + 8f).dp)
                    )
                }

                // The one active control, on the photo's bottom edge.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = EDITOR_BLOCK_DP.dp)
                        .fillMaxWidth()
                        .height(EDITOR_CONTROL_DP.dp)
                ) {
                    when {
                        group != null -> EditorControlHint(
                            stringResource(R.string.editor_section_not_built, stringResource(group.label))
                        )
                        selectedSetting?.kind == SettingKind.AiDenoise -> Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(false to offText, true to onText).forEach { (enabled, text) ->
                                LilacChip(
                                    text = text,
                                    selected = editAiDenoiseEnabled == enabled,
                                    onClick = { setAiDenoise(enabled) },
                                    height = 36.dp,
                                    fill = Color(0xB8141413),
                                    fontSize = 13.sp,
                                    horizontalPadding = 13.dp,
                                )
                            }
                        }
                        // With the blur Off its options have nothing to act on: say so, and offer to turn it on.
                        selectedSetting?.kind == SettingKind.Choice && selectedSetting != EditorSetting.Depth && blurOff -> PortraitNote(
                            text = stringResource(R.string.editor_blur_off),
                            action = stringResource(R.string.editor_turn_on),
                            onAction = ::turnBlurOn
                        )
                        selectedSetting == EditorSetting.Focus && quick -> PortraitNote(
                            text = stringResource(R.string.editor_quick_keeps_subject),
                            action = stringResource(R.string.editor_use_depth),
                            onAction = ::chooseDepth
                        )
                        selectedSetting?.kind == SettingKind.Choice -> Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (blurOff && selectedSetting != EditorSetting.Depth) 0.5f else 1f },
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            choiceOptions(selectedSetting).forEach { option ->
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
                        selectedSetting != null -> {
                            val aperture = selectedSetting.kind == SettingKind.Aperture
                            val start = openValue(selectedSetting)
                            val steps = selectedSetting.steps.let { if (aperture || start in it) it else (it + start).sorted() }
                            LilacRuler(
                                values = steps,
                                value = settingValue(selectedSetting),
                                start = start,
                                label = { if (aperture && it == 0f) offText else selectedSetting.tickLabel(it) },
                                stops = aperture,
                                onScroll = { if (aperture) apertureScroll = it },
                                onChange = { setSetting(selectedSetting, it) },
                                onRelease = { releaseSetting(selectedSetting) },
                                onReset = {
                                    setSetting(selectedSetting, start)
                                    releaseSetting(selectedSetting)
                                },
                                labelEvery = selectedSetting.labelEvery,
                                onTouch = { fingerOnRuler = it }
                            )
                        }
                        section != EditorSection.Look -> EditorControlHint(
                            stringResource(R.string.editor_section_not_built, stringResource(section.label))
                        )
                        selectedKey == "intensity" && editLutId == null ->
                            EditorControlHint(stringResource(R.string.editor_intensity_no_look))
                        selectedKey == "intensity" -> LilacRuler(
                            values = IntensitySteps,
                            value = intensity,
                            start = openIntensity,
                            label = { (it * 100).roundToInt().toString() },
                            onChange = {
                                viewModel.setPhotoRecipeParams(
                                    RecipeParam.LUT_INTENSITY.setValue(editPhotoRecipeParams ?: editLutRecipeParams, it)
                                )
                            },
                            onRelease = {},
                            onReset = {
                                viewModel.setPhotoRecipeParams(
                                    RecipeParam.LUT_INTENSITY.setValue(editPhotoRecipeParams ?: editLutRecipeParams, openIntensity)
                                )
                            },
                            onTouch = { fingerOnRuler = it }
                        )
                        else -> EditorControlHint(stringResource(R.string.editor_not_built))
                    }
                }

                // The tool block: the settings of the current level above the path row. It lightens one step for
                // each group you are inside, so the depth shows without reading.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(EDITOR_BLOCK_DP.dp)
                        .background(
                            when (groupStack.size) {
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
                        EditorValueRow(
                            values = values,
                            selectedKey = selectedKey,
                            onClick = { item ->
                                val now = SystemClock.uptimeMillis()
                                val doubleTap = item.key == lastTapKey && now - lastTapAt < DOUBLE_TAP_MS
                                lastTapKey = item.key
                                lastTapAt = if (doubleTap) 0L else now
                                pendingCard?.cancel()
                                when {
                                    item.group -> {
                                        explainKey = null
                                        groupPath = (groupStack.map { it.name } + item.key.removePrefix("group:")).joinToString("/")
                                    }
                                    // A double tap returns the setting to where it was when the editor opened.
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
                                        if (item.key == "intensity") {
                                            viewModel.setPhotoRecipeParams(
                                                RecipeParam.LUT_INTENSITY.setValue(editPhotoRecipeParams ?: editLutRecipeParams, openIntensity)
                                            )
                                        }
                                    }
                                    // A second, single tap on the selected setting opens or closes its explanation,
                                    // once it can't be the first half of a double tap any more.
                                    item.key == selectedKey -> pendingCard = scope.launch {
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
                                    item.group -> Unit
                                    !live && holding -> Toast.makeText(context, R.string.editor_compare_one_unavailable, Toast.LENGTH_SHORT).show()
                                    !live -> Unit
                                    holding -> {
                                        val recipe = editPhotoRecipeParams ?: editLutRecipeParams
                                        heldRecipe = recipe
                                        viewModel.setPhotoRecipeParams(
                                            if (s != null) s.param!!.setValue(recipe, openValue(s))
                                            else RecipeParam.LUT_INTENSITY.setValue(recipe, openIntensity)
                                        )
                                    }
                                    else -> heldRecipe?.let {
                                        heldRecipe = null
                                        viewModel.setPhotoRecipeParams(it)
                                    }
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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
                        EditorGroupRow(
                            title = stringResource(group.label),
                            parentTitle = groupStack.getOrNull(groupStack.size - 2)?.let { stringResource(it.label) },
                            onBack = { groupPath = groupStack.dropLast(1).joinToString("/") { it.name } }
                        )
                    }
                }
            }

            if (showDiscardDialog) {
                EditorDiscardDialog(
                    message = if (isRaw && changedKeys.any { it.startsWith("raw") }) {
                        stringResource(R.string.editor_discard_message) + " " + stringResource(R.string.editor_discard_raw_saved)
                    } else {
                        stringResource(R.string.editor_discard_message)
                    },
                    onKeep = { showDiscardDialog = false },
                    onDiscard = {
                        showDiscardDialog = false
                        viewModel.restorePortraitOnDiscard()
                        leaveEditor()
                    }
                )
            }
        }
    }

    if (isBaselineLutEditSheetVisible) {
        LutEditBottomSheet(
            lutId = baselineLutEditId!!,
            editorTarget = LutEditorTarget.BASELINE_RAW,
            imageHistogram = imageHistogram,
            onDismiss = {
                showBaselineLutEditSheet = false
                showRawBaselineLutSelectorSheet = true
            }
        )
    }

    if (showRawBaselineLutSelectorSheet) {
        RawBaselineColorCorrectionBottomSheet(
            selectedLutId = editRawBaselineLutId,
            availableLuts = availableLuts,
            thumbnail = thumbnailBitmap,
            containerColor = Color(0x151A1A1A),
            onSelectLut = { lutId ->
                viewModel.saveRawBaselineLutSelection(currentEditSourcePhoto, lutId)
            },
            onEditRecipe = { lutId ->
                baselineLutEditId = lutId
                showRawBaselineLutSelectorSheet = false
                showBaselineLutEditSheet = true
            },
            onDismiss = {
                showRawBaselineLutSelectorSheet = false
            }
        )
    }

    if (showPaymentDialog) {
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

    if (pendingAiDenoise) {
        AiDenoiseModelDownloadDialog(
            state = aiDenoiseModelState,
            onDownload = { AiDenoiseModelManager.download(context) },
            onDismiss = {
                pendingAiDenoise = false
                viewModel.setAiDenoiseEnabled(false)
            }
        )
    }

    if (showDepthModelDownloadDialog) {
        DepthModelDownloadDialog(
            state = depthModelState,
            onDownload = { DepthModelManager.download(context) },
            onImport = { depthModelImportLauncher.launch(arrayOf("*/*")) },
            onDismiss = {
                showDepthModelDownloadDialog = false
                pendingDepth = false
            }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
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
