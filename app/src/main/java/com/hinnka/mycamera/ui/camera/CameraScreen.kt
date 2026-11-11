package com.hinnka.mycamera.ui.camera

import androidx.activity.compose.BackHandler
import com.hinnka.mycamera.ml.DepthModelManager
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.ui.prefs.rememberUserPreferencesRepository
import com.hinnka.mycamera.data.UserPreferences
import com.hinnka.mycamera.ui.prefs.rememberPreference
import com.hinnka.mycamera.ui.prefs.preferenceValue
import android.hardware.camera2.CaptureRequest
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.MyCameraApplication
import com.hinnka.mycamera.R
import com.hinnka.mycamera.processor.BokehStyle
import com.hinnka.mycamera.camera.AspectRatio
import com.hinnka.mycamera.camera.CameraState
import com.hinnka.mycamera.camera.DeviceQuirks
import com.hinnka.mycamera.camera.MultiFrameConfig
import com.hinnka.mycamera.camera.FocusPointSource
import com.hinnka.mycamera.camera.LensType
import com.hinnka.mycamera.camera.PhotoCaptureProgress
import com.hinnka.mycamera.data.CaptureButtonStyle
import com.hinnka.mycamera.data.DevelopAnimationStyle
import com.hinnka.mycamera.model.CameraPreset
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.LutSelectorMode
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.render.RenderQueue
import com.hinnka.mycamera.gallery.GalleryManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hinnka.mycamera.ui.components.*
import com.hinnka.mycamera.utils.OrientationObserver
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.StartupGate
import com.hinnka.mycamera.utils.VibrationHelper
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import com.hinnka.mycamera.video.CaptureMode
import com.hinnka.mycamera.video.VideoAspectRatio
import com.hinnka.mycamera.video.VideoFpsPreset
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.video.VideoResolutionPreset
import com.hinnka.mycamera.video.VideoStabilizationMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.components.EdgeRingStyle
import com.hinnka.mycamera.ui.components.ThumbnailCorner
import com.hinnka.mycamera.ui.components.ThumbnailEdgeRing
import com.hinnka.mycamera.ui.settings.SettingsTarget
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.Canvas

/**
 * 主相机界面
 */
enum class ActivePanel {
    NONE,
    SETTINGS,
    FILTERS,
    EDIT,
    PRESETS
}

internal enum class CameraShootingMode {
    PORTRAIT,
    PROFESSIONAL,
    PHOTO,
    VIDEO
}

private const val PreviewTransitionRevealDurationMillis = 130
private const val PreviewMorphDurationMillis = 280
private const val ControlsArrivalDurationMillis = 200
private val ControlsArrivalEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// The controls rise and fade in once per app start, not again after the gallery or a mode switch.
private var controlsArrivalPlayed = false
// Whether the manual values show, per shooting mode, for this app run: Pro opens with them, the others
// without, and a swipe that hides or shows them is remembered until the app restarts.
private val manualControlsShownByMode = mutableMapOf<CameraShootingMode, Boolean>()
private const val ZoomStopAnimationCompletionThreshold = 0.001f
private const val RawCaptureTapDebounceMillis = 1000L
private const val DefaultShutterSpeedNs = 1_000_000_000f / 60f
private const val DefaultIso = 100f
private const val DefaultAwbTemperature = 5000f
private const val DefaultFocusDistance = 0f
private val XpanViewfinderInnerBorder = 4.dp
private val XpanSideControlReservedWidth = 56.dp

private fun formatVirtualApertureFStop(aperture: Float): String {
    val rounded = aperture.roundToInt()
    return if (aperture == rounded.toFloat()) rounded.toString() else aperture.toString()
}

/**
 * The picture starts below a display cutout only. The status bar is hidden and shows transiently on a
 * swipe; following its inset would make the picture jump while it shows.
 */
@Composable
private fun cameraTopCutoutInset(): Dp = with(LocalDensity.current) {
    WindowInsets.displayCutout.getTop(this).toDp()
}

private fun Bitmap.copyForCaptureAnimation(): Bitmap? {
    if (isRecycled) return null
    val copyConfig = if (config == Bitmap.Config.HARDWARE) {
        Bitmap.Config.ARGB_8888
    } else {
        config ?: Bitmap.Config.ARGB_8888
    }
    return copy(copyConfig, false) ?: copy(Bitmap.Config.ARGB_8888, false)
}

private fun Bitmap.recycleIfAlive() {
    if (!isRecycled) {
        recycle()
    }
}

@Composable
private fun PanelDismissPreviewOverlay(
    bounds: Rect?,
    parentBounds: Rect?,
    dimBackground: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bounds = bounds ?: return
    if (bounds.width <= 0f || bounds.height <= 0f) return

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val parentLeft = parentBounds?.left ?: 0f
    val parentTop = parentBounds?.top ?: 0f
    val width = with(density) { bounds.width.toDp() }
    val height = with(density) { bounds.height.toDp() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (bounds.left - parentLeft).roundToInt(),
                    y = (bounds.top - parentTop).roundToInt()
                )
            }
            .size(width = width, height = height)
            .then(
                if (dimBackground) {
                    Modifier.background(Color.Black.copy(alpha = 0.6f))
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures {
                    currentOnDismiss()
                }
            }
    )
}

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    galleryViewModel: GalleryViewModel,
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenSettingsAt: (SettingsTarget) -> Unit = {},
    onFilterManagementClick: (String?) -> Unit,
    onFrameManagementClick: () -> Unit,
    onToolboxClick: () -> Unit,
    onPresetEditClick: (String?) -> Unit,
    onPresetManagementClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferencesRepository = rememberUserPreferencesRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val isCameraInitialized by viewModel.isInitialized.collectAsState()
    val controlsArrival = remember { Animatable(if (controlsArrivalPlayed) 1f else 0f) }
    LaunchedEffect(isCameraInitialized) {
        if (!isCameraInitialized || controlsArrivalPlayed) return@LaunchedEffect
        controlsArrivalPlayed = true
        controlsArrival.animateTo(1f, tween(ControlsArrivalDurationMillis, easing = ControlsArrivalEasing))
    }
    val latestPhoto by galleryViewModel.latestPhoto.collectAsState()
    val showLevelIndicator by preferenceValue { it.showLevelIndicator }
    val focusPeakingEnabled by preferenceValue { it.focusPeakingEnabled }
    val portraitQuickBlur by preferenceValue { it.portraitQuickBlur }
    val portraitIntroSeen by preferenceValue { it.portraitIntroSeen }
    val portraitDepthReminderSeen by preferenceValue { it.portraitDepthReminderSeen }
    val proGuideSeen by preferenceValue { it.proGuideSeen }
    val basicsGuideSeen by preferenceValue { it.basicsGuideSeen }
    val portraitBokehStyle by preferenceValue { BokehStyle.fromPersistedName(it.portraitBokehStyle) }
    val eyeFocusEnabled by preferenceValue { it.eyeFocusEnabled }
    val keepScreenOn by preferenceValue { it.keepScreenOn }
    val currentLutId by viewModel.currentLutId.collectAsState()
    val lutSwipeState = rememberLutSwipeState()
    val currentRecipeParams by viewModel.currentRecipeParams.collectAsState()
    val lutSelectorMode by viewModel.lutSelectorMode.collectAsState()
    val activePresetId by preferenceValue { it.activePresetId }
    val activePresetModified by viewModel.isActivePresetModified.collectAsState()
    val customPresets by preferenceValue { it.customPresets }
    val currentBaselineRecipeParams by viewModel.currentBaselineRecipeParams.collectAsState()
    val categoryOrder by preferenceValue { it.categoryOrder }
    val useRaw by preferenceValue { it.useRaw }
    val useJpgMax by preferenceValue { it.useJpgMax }
    val jpgMultiFrameCount by preferenceValue { it.jpgMultiFrameDenoiseFrameCount }
    val hdrPlusFrameCount by preferenceValue { it.hdrPlusFrameCount }
    val useMultipleExposure by preferenceValue { it.useMultipleExposure }
    val useRawMax by preferenceValue { it.useRawMax }
    val ultraHdrEnabled by preferenceValue { it.ultraHdrGainMapEnabled }
    val useLivePhoto by preferenceValue { it.useLivePhoto }
    val enableDevelopAnimation by preferenceValue { it.enableDevelopAnimation }
    var showHistogram by rememberPreference(UserPreferences::showHistogram)
    var saveCameraJpeg by rememberPreference(UserPreferences::saveCameraJpeg)
    var videoTorchEnabled by rememberPreference(UserPreferences::videoTorchEnabled)
    var videoAudioInputId by rememberPreference(UserPreferences::videoAudioInputId)
    val developAnimationStyle by preferenceValue { it.developAnimationStyle }
    val phantomMode by preferenceValue { it.phantomMode }
    val topSheetAspectRatios by preferenceValue { it.topSheetAspectRatios }
    val videoCodec by preferenceValue { it.videoCodec }
    val videoAudioInputOptions by viewModel.videoAudioInputOptions.collectAsState()
    val phantomPipPreview by preferenceValue { it.phantomPipPreview }
    val rawDcpId by preferenceValue { it.rawDcpId }
    val rawDcpIdsByLens by preferenceValue { it.rawDcpIdsByLens }
    val rawHncsFilmCurveMode by preferenceValue { it.rawHncsFilmCurveMode }
    val rawColorEngine by preferenceValue { it.rawRenderingEngine }
    val rawToneMappingParameters by preferenceValue { it.rawToneMappingParameters }
    val rawSpectralFilmStock by preferenceValue { it.rawSpectralFilmStock }
    val rawSpectralFilmSelection by viewModel.rawSpectralFilmSelection.collectAsState()
    val rawSpectralFilmPrint by preferenceValue { it.rawSpectralFilmPrint }
    val rawFotufilmDocument by viewModel.rawFotufilmDocument.collectAsState()
    val multipleExposureState = viewModel.multipleExposureState
    val canStartShutterAnimation by viewModel.canStartShutterAnimation.collectAsState()
    val previewFrameRendered by viewModel.previewFrameRendered.collectAsState()
    val currentCaptureModeForEffects by rememberUpdatedState(state.captureMode)
    var previewRecipeParamsOverride by remember(currentLutId, activePresetId) {
        mutableStateOf<ColorRecipeParams?>(null)
    }
    var pendingCaptureAnimationBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureAnimationJob by remember { mutableStateOf<Job?>(null) }
    var cameraScreenBounds by remember { mutableStateOf<Rect?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var viewfinderAreaBounds by remember { mutableStateOf<Rect?>(null) }
    var filterButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var captureButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var parameterBarTop by remember { mutableStateOf<Float?>(null) }
    var parameterRulerBounds by remember { mutableStateOf<Rect?>(null) }
    var galleryThumbnailBounds by remember { mutableStateOf<Rect?>(null) }
    var captureAnimationSnapshot by remember { mutableStateOf<CaptureAnimationSnapshot?>(null) }
    var previewTransitionActive by remember { mutableStateOf(true) }
    var previewTransitionRevealing by remember { mutableStateOf(false) }
    // A switch that only changes the pipeline (Photo <-> Pro) shows no cover; the controls change over the viewfinder.
    var previewTransitionCovered by remember { mutableStateOf(true) }
    // The frame being left, kept (cropped to the new viewfinder) under the cover until the live view lands.
    var previewTransitionFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewTransitionToken by remember { mutableIntStateOf(0) }
    var previewTransitionAwaitingResume by remember { mutableStateOf(false) }
    var previewTransitionSawPause by remember { mutableStateOf(false) }
    var hasPlayedInitialPreviewTransition by remember { mutableStateOf(false) }
    var rawCaptureTapLocked by remember { mutableStateOf(false) }
    var zoomStopAnimationJob by remember { mutableStateOf<Job?>(null) }
    val lensSwitchTransition = remember(viewModel, scope) { LensSwitchTransitionState(scope, viewModel) }

    DisposableEffect(lensSwitchTransition) {
        onDispose { lensSwitchTransition.cancel() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        lensSwitchTransition.cancel()
        viewModel.setLocationTrackingForeground(false)
    }

    fun discardTransientLookEdits() {
        previewRecipeParamsOverride = null
    }

    LaunchedEffect(currentRecipeParams) {
        if (previewRecipeParamsOverride?.isSameAs(currentRecipeParams) == true) {
            previewRecipeParamsOverride = null
        }
    }

    // Surface may become ready while persisted camera settings are still being restored.
    // Retain the exact instance and open only after initialization has completed.
    var previewSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var isCameraPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(isCameraInitialized) {
        isCameraPrepared = isCameraInitialized && viewModel.prepareCamera()
    }
    // prepareCamera opens the camera ahead of the preview; don't leave it open if this screen
    // goes away before the preview surface arrived.
    DisposableEffect(viewModel) {
        onDispose { viewModel.closeCameraWithoutPreview() }
    }

    LaunchedEffect(
        isCameraInitialized,
        isCameraPrepared,
        previewSurfaceTexture,
    ) {
        val surfaceTexture = previewSurfaceTexture ?: return@LaunchedEffect
        if (!isCameraInitialized || !isCameraPrepared) return@LaunchedEffect
        viewModel.openCamera(surfaceTexture)
    }

    // UI State
    var activePanel by remember { mutableStateOf(ActivePanel.NONE) }
    var selectedParameter by remember { mutableStateOf(CameraParameter.EXPOSURE_COMPENSATION) }
    var showParameterRuler by remember(state.captureMode, state.useRaw) { mutableStateOf(false) }
    // The mode dial's state lives here so the manual values can give way while its list is open.
    val modeLine = remember {
        ModeLineState(
            when {
                state.captureMode == CaptureMode.VIDEO -> CameraShootingMode.VIDEO
                state.useRaw && state.isRawSupported -> CameraShootingMode.PROFESSIONAL
                state.isPortraitMode -> CameraShootingMode.PORTRAIT
                else -> CameraShootingMode.PHOTO
            },
            state.isRawSupported
        )
    }
    // Vertical swipes on the picture: up shows the manual values on its bottom edge, down drops the
    // everyday quick settings onto its top edge. Each swipe first closes what the other one opened.
    // Keyed by the mode chosen on the dial, not the one the camera reports: the camera only reports the
    // new mode once it has reloaded, which would bring the old mode's values back for a moment.
    val manualControlsMode = modeLine.shownMode
    var manualControlsShown by remember(manualControlsMode) {
        mutableStateOf(
            manualControlsShownByMode[manualControlsMode] ?: (manualControlsMode == CameraShootingMode.PROFESSIONAL)
        )
    }
    LaunchedEffect(manualControlsMode, manualControlsShown) {
        manualControlsShownByMode[manualControlsMode] = manualControlsShown
    }
    // A sideways drag on a value shows its ruler while dragging and for a moment after.
    val rulerScrub = remember { RulerScrub() }
    var scrubRulerShown by remember { mutableStateOf(false) }
    LaunchedEffect(rulerScrub.active) {
        if (rulerScrub.active) scrubRulerShown = true else {
            delay(700)
            scrubRulerShown = false
        }
    }
    var lastValueTap by remember { mutableStateOf<Pair<CameraParameter, Long>?>(null) }
    var everydayRowShown by remember { mutableStateOf(false) }
    // A value set by hand (EV, ISO, shutter, focus, white balance) stays visible while the panel is closed,
    // as a compact line of just those values, so a shot is never taken with a fixed value nobody can see.
    val anyManualValue = state.exposureCompensation != 0 || !state.isIsoAuto || !state.isShutterSpeedAuto ||
        !state.isAutoFocus || state.awbMode != CaptureRequest.CONTROL_AWB_MODE_AUTO
    val isXpan = state.aspectRatio == AspectRatio.XPAN
    val burstCapturingCount = viewModel.burstImageCount

    var isGhostPermissionFlowActive by remember { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }

    val ghostLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { _ ->
            // Results are handled via the ON_RESUME lifecycle effect to avoid self-reference issues
        }
    )

    // The RAW render queue's progress notification; the queue works without it.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    val dcpImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importRawDcps(uris) { _, _ -> }
            }
        }
    )

    // 当打开滤镜面板时，生成预览图
    LaunchedEffect(activePanel) {
        if (activePanel == ActivePanel.FILTERS) {
            viewModel.generateThumbnail()
        }
    }

    LaunchedEffect(state.useRaw, state.captureMode) {
        if (!state.useRaw || state.captureMode != CaptureMode.PHOTO) {
            rawCaptureTapLocked = false
        }
    }

    // 从后台返回时检查并恢复相机，刷新最新照片
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkAndRecoverCamera()
        viewModel.setLocationTrackingForeground(true)
        galleryViewModel.refreshLatestPhoto()

        // Handle automated ghost mode permission sequence
        if (isGhostPermissionFlowActive) {
            val hasOverlay = Settings.canDrawOverlays(context)
            val hasFiles = Environment.isExternalStorageManager()

            if (hasOverlay && !hasFiles) {
                // Overlay granted, now request files
                ghostLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        ("package:${context.packageName}").toUri()
                    )
                )
            } else if (hasOverlay) {
                // All permissions granted
                isGhostPermissionFlowActive = false
                if (!phantomMode) {
                    viewModel.togglePhantomMode()
                }
            } else {
                // If overlay is still missing after returning, user might have cancelled
                // We stop the automatic flow to avoid getting stuck
                isGhostPermissionFlowActive = false
            }
        }

        viewModel.updateLut()
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingCaptureAnimationBitmap?.recycleIfAlive()
            pendingCaptureAnimationBitmap = null
            captureAnimationJob?.cancel()
        }
    }

    LaunchedEffect(enableDevelopAnimation, state.captureMode) {
        if (!enableDevelopAnimation || state.captureMode != CaptureMode.PHOTO) {
            pendingCaptureAnimationBitmap?.recycleIfAlive()
            pendingCaptureAnimationBitmap = null
            captureAnimationJob?.cancel()
            captureAnimationSnapshot = null
        }
    }

    // 监听照片保存完成事件，立即刷新缩略图
    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect {
            galleryViewModel.refreshLatestPhoto()
            MyCameraApplication.updateWidgets(context)
        }
    }

    // 显影在拍摄请求完成时开始，不等待图像处理或保存。
    LaunchedEffect(Unit) {
        viewModel.captureCompletedEvent.collect { metadata ->
            if (!enableDevelopAnimation || currentCaptureModeForEffects != CaptureMode.PHOTO) {
                pendingCaptureAnimationBitmap?.recycleIfAlive()
                pendingCaptureAnimationBitmap = null
                captureAnimationSnapshot = null
                return@collect
            }
            val rootOffset = cameraScreenBounds?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero
            val sourceBounds = previewBounds?.translate(-rootOffset)
            val targetBounds = galleryThumbnailBounds?.translate(-rootOffset)
            val animationStyle = developAnimationStyle

            fun startCaptureAnimation(bitmap: Bitmap) {
                if (sourceBounds == null || targetBounds == null || !scope.isActive ||
                    !enableDevelopAnimation || currentCaptureModeForEffects != CaptureMode.PHOTO
                ) {
                    bitmap.recycleIfAlive()
                    return
                }
                captureAnimationJob?.cancel()
                captureAnimationJob = scope.launch {
                    var processedBitmap: Bitmap? = null
                    try {
                        processedBitmap = if (animationStyle == DevelopAnimationStyle.INSTANT_PRINT) {
                            viewModel.renderCaptureAnimationFrame(bitmap, metadata)
                        } else {
                            viewModel.applyLut(bitmap)
                        }
                        processedBitmap.copyForCaptureAnimation()?.let {
                            captureAnimationSnapshot = CaptureAnimationSnapshot(
                                bitmap = it.asImageBitmap(),
                                sourceBounds = sourceBounds,
                                targetBounds = targetBounds,
                                style = animationStyle
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        com.hinnka.mycamera.utils.PLog.e("CaptureAnimation", "Failed to prepare capture animation", e)
                    } finally {
                        processedBitmap?.recycleIfAlive()
                        if (processedBitmap !== bitmap) bitmap.recycleIfAlive()
                    }
                }
            }

            pendingCaptureAnimationBitmap?.let(::startCaptureAnimation)
                ?: viewModel.glSurfaceView?.capturePreviewFrame(::startCaptureAnimation)
            pendingCaptureAnimationBitmap = null
        }
    }

    val previewSize = state.currentPreviewSize
    val previewAspectRatio = state.getPreviewAspectRatio()
    // Black at start-up, the held frame at a switch; it fades out when the first live frame is drawn.
    val previewTransitionCoverAlpha = animateFloatAsState(
        targetValue = if (previewTransitionActive && previewTransitionCovered && !previewTransitionRevealing) 1f else 0f,
        animationSpec = if (previewTransitionActive && previewTransitionRevealing) {
            tween(durationMillis = PreviewTransitionRevealDurationMillis, easing = LinearEasing)
        } else {
            snap()
        },
        label = "previewTransitionCoverAlpha"
    )
    // The held frame moves from the old picture rectangle to the new one while the camera restarts.
    // Drawn in a Compose layer above the preview: the preview surface itself changes size only once.
    val previewMorphProgress = remember { Animatable(1f) }
    var previewMorphFrom by remember { mutableStateOf<Rect?>(null) }
    var previewMorphTo by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(previewBounds) {
        val target = previewBounds ?: return@LaunchedEffect
        val shown = previewMorphTo
        if (previewTransitionFrame == null || shown == null || shown == target) {
            previewMorphFrom = target
            previewMorphTo = target
            return@LaunchedEffect
        }
        previewMorphFrom = androidx.compose.ui.geometry.lerp(previewMorphFrom ?: shown, shown, previewMorphProgress.value)
        previewMorphTo = target
        previewMorphProgress.snapTo(0f)
        previewMorphProgress.animateTo(1f, tween(PreviewMorphDurationMillis, easing = FastOutSlowInEasing))
    }

    fun cancelZoomStopAnimation() {
        zoomStopAnimationJob?.cancel()
        zoomStopAnimationJob = null
    }

    fun animateCurrentLensToZoomStop(targetZoom: Float) {
        cancelZoomStopAnimation()
        val startZoom = viewModel.zoomRatioByMain
        if (abs(startZoom - targetZoom) <= ZoomStopAnimationCompletionThreshold) {
            viewModel.setZoomRatio(targetZoom)
            return
        }
        zoomStopAnimationJob = scope.launch {
            val progress = Animatable(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = resolveZoomStopAnimationDurationMillis(startZoom, targetZoom),
                    easing = FastOutSlowInEasing
                )
            ) {
                viewModel.setZoomRatio(
                    interpolateZoomRatio(
                        startZoom = startZoom,
                        targetZoom = targetZoom,
                        progress = value
                    )
                )
            }
            viewModel.setZoomRatio(targetZoom)
        }
    }

    LaunchedEffect(previewTransitionActive) {
        if (!previewTransitionActive) previewTransitionFrame = null
    }

    fun runPreviewTransition(covered: Boolean = true, onSwitch: () -> Unit) {
        cancelZoomStopAnimation()
        lensSwitchTransition.cancel()
        fun closeCover() {
            previewTransitionCovered = covered
            previewTransitionActive = true
            previewTransitionRevealing = false
            previewTransitionToken += 1
            previewTransitionAwaitingResume = true
            previewTransitionSawPause = false
        }
        if (!covered) closeCover()
        scope.launch {
            if (covered) {
                // Copy the last frame before the camera stops; it is held over the picture until the new one arrives.
                previewTransitionFrame = viewModel.glSurfaceView?.captureLastFrame()?.asImageBitmap()
                previewMorphFrom = previewBounds
                previewMorphTo = previewBounds
                previewMorphProgress.snapTo(1f)
                closeCover()
            }
            onSwitch()
        }
    }

    fun switchToLensWithPreviewTransition(cameraId: String) {
        lensSwitchTransition.cancelPendingSwitch()
        if (cameraId == state.getCurrentCameraInfo()?.cameraId) return
        val targetCamera = state.availableCameras.firstOrNull { it.cameraId == cameraId } ?: return
        val targetZoom = targetCamera.displayIntrinsicZoomRatio.takeIf { it > 0f }
            ?: targetCamera.intrinsicZoomRatio.takeIf { it > 0f }
            ?: 1f
        if (viewModel.isVideoLensLocked()) {
            animateCurrentLensToZoomStop(targetZoom)
            return
        }
        cancelZoomStopAnimation()
        lensSwitchTransition.switchLens(cameraId, targetZoom) { viewModel.switchToLens(cameraId) }
    }

    fun setZoomWithPreviewTransition(targetZoom: Float) {
        lensSwitchTransition.cancelPendingSwitch()
        cancelZoomStopAnimation()
        if (viewModel.isVideoLensLocked()) {
            viewModel.setZoomRatio(targetZoom)
            return
        }
        if (viewModel.isCurrentLensCustomZoomRatioStop(targetZoom)) {
            viewModel.setZoomRatio(targetZoom)
            return
        }
        val currentCamera = state.getCurrentCameraInfo()
        val currentCameraId = currentCamera?.cameraId ?: "0"
        val camera = viewModel.findOptimalLens(
            targetZoom,
            state.availableCameras,
            currentCameraId
        )
        if (camera != null && camera.cameraId != currentCamera?.cameraId) {
            lensSwitchTransition.switchLens(camera.cameraId, targetZoom) {
                viewModel.switchToLensAndSetZoomRatio(camera.cameraId, targetZoom)
            }
        } else {
            viewModel.setZoomRatio(targetZoom)
        }
    }

    fun animateZoomStopWithPreviewTransition(targetZoom: Float) {
        lensSwitchTransition.cancelPendingSwitch()
        cancelZoomStopAnimation()
        if (viewModel.isVideoLensLocked() ||
            viewModel.isCurrentLensCustomZoomRatioStop(targetZoom)
        ) {
            animateCurrentLensToZoomStop(targetZoom)
            return
        }
        val currentCamera = state.getCurrentCameraInfo()
        val currentCameraId = currentCamera?.cameraId ?: "0"
        val camera = viewModel.findOptimalLens(
            targetZoom,
            state.availableCameras,
            currentCameraId
        )
        if (camera != null && camera.cameraId != currentCamera?.cameraId) {
            lensSwitchTransition.switchLens(camera.cameraId, targetZoom) {
                viewModel.switchToLensAndSetZoomRatio(camera.cameraId, targetZoom)
            }
        } else {
            animateCurrentLensToZoomStop(targetZoom)
        }
    }

    var showProUnavailableDialog by remember { mutableStateOf(false) }

    fun switchCameraWithPreviewTransition() {
        runPreviewTransition { viewModel.switchCamera() }
    }

    fun setShootingModeWithPreviewTransition(mode: CameraShootingMode) {
        if (mode == CameraShootingMode.PROFESSIONAL && !state.isRawSupported) {
            showProUnavailableDialog = true
            return
        }
        if (
            mode == CameraShootingMode.PROFESSIONAL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            RenderQueue.shouldAskNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val targetCaptureMode = when (mode) {
            CameraShootingMode.PROFESSIONAL,
            CameraShootingMode.PORTRAIT,
            CameraShootingMode.PHOTO -> CaptureMode.PHOTO
            CameraShootingMode.VIDEO -> CaptureMode.VIDEO
        }
        val targetUseRaw = when (mode) {
            CameraShootingMode.PROFESSIONAL -> true
            CameraShootingMode.PORTRAIT,
            CameraShootingMode.PHOTO -> false
            CameraShootingMode.VIDEO -> null
        }
        val targetPortrait = mode == CameraShootingMode.PORTRAIT
        if (
            targetCaptureMode == state.captureMode &&
            (targetUseRaw == null || targetUseRaw == state.useRaw)
        ) {
            // Photo and Portrait share the camera setup, so only the portrait flag changes.
            viewModel.setPortraitMode(targetPortrait)
            return
        }

        runPreviewTransition(covered = targetCaptureMode != state.captureMode) {
            if (targetCaptureMode == CaptureMode.VIDEO && state.aspectRatio == AspectRatio.XPAN) {
                viewModel.setAspectRatio(AspectRatio.RATIO_4_3)
            }
            viewModel.setShootingMode(targetCaptureMode, targetUseRaw)
            viewModel.setPortraitMode(targetPortrait)
        }
    }

    // A mode chosen on the dial while the camera is still loading the last one waits for that load to finish,
    // so a session is never torn down mid-configuration; only the latest choice is kept. The dial itself
    // stays free meanwhile.
    var queuedShootingMode by remember { mutableStateOf<CameraShootingMode?>(null) }
    val modeSwitchReady = !previewTransitionActive && !lensSwitchTransition.isActive
    LaunchedEffect(modeSwitchReady, queuedShootingMode) {
        val mode = queuedShootingMode ?: return@LaunchedEffect
        if (modeSwitchReady) {
            queuedShootingMode = null
            setShootingModeWithPreviewTransition(mode)
        }
    }

    fun selectShootingMode(mode: CameraShootingMode) {
        if (modeSwitchReady) {
            queuedShootingMode = null
            setShootingModeWithPreviewTransition(mode)
        } else {
            queuedShootingMode = mode
        }
    }

    fun presetTargetAspectRatio(preset: CameraPreset?): AspectRatio {
        return try {
            AspectRatio.valueOf(preset?.aspectRatio ?: AspectRatio.RATIO_4_3.name)
        } catch (_: Exception) {
            AspectRatio.RATIO_4_3
        }
    }

    fun presetRequiresPreviewTransition(preset: CameraPreset?): Boolean {
        if (state.captureMode == CaptureMode.VIDEO) return false
        val targetAspectRatio = presetTargetAspectRatio(preset)
        return targetAspectRatio != state.aspectRatio
    }

    val previewShowingFrames = state.isPreviewActive && state.previewFirstFrameTimestampNs != null
    LaunchedEffect(previewTransitionToken, previewShowingFrames, previewTransitionAwaitingResume) {
        if (!previewTransitionAwaitingResume) return@LaunchedEffect

        if (!previewShowingFrames) {
            previewTransitionSawPause = true
            return@LaunchedEffect
        }

        if (previewTransitionSawPause) {
            // Reveal on the restarted camera's first frame, not a fixed 80 ms after it starts.
            previewTransitionRevealing = true
            delay(PreviewTransitionRevealDurationMillis.toLong())
            previewTransitionActive = false
            previewTransitionAwaitingResume = false
            previewTransitionSawPause = false
        }
    }

    val shouldKeepScreenOn = keepScreenOn || state.videoRecordingState.isRecording

    DisposableEffect(activity, shouldKeepScreenOn) {
        if (shouldKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(previewTransitionToken) {
        if (previewTransitionToken == 0) return@LaunchedEffect
        delay(700)
        if (previewTransitionAwaitingResume) {
            previewTransitionRevealing = true
            delay(PreviewTransitionRevealDurationMillis.toLong())
            previewTransitionActive = false
            previewTransitionAwaitingResume = false
            previewTransitionSawPause = false
        }
    }

    LaunchedEffect(previewFrameRendered) {
        // Deferred start-up work (gallery list, warm-ups) waits for the first camera frame.
        if (previewFrameRendered) StartupGate.open()
    }

    LaunchedEffect(
        state.isPreviewActive,
        previewFrameRendered,
        state.captureMode,
        hasPlayedInitialPreviewTransition,
        canStartShutterAnimation
    ) {
        val canRevealInitialPreview =
            canStartShutterAnimation ||
                state.captureMode == CaptureMode.VIDEO
        // Open on the camera's first frame: the black splash and this cover read as one closed shutter.
        if (hasPlayedInitialPreviewTransition || !state.isPreviewActive ||
            !previewFrameRendered || !canRevealInitialPreview
        ) return@LaunchedEffect
        previewTransitionActive = true
        previewTransitionRevealing = true
        delay(PreviewTransitionRevealDurationMillis.toLong())
        previewTransitionActive = false
        hasPlayedInitialPreviewTransition = true
    }

    var showGhostPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.showGhostPermissions) {
        if (viewModel.showGhostPermissions) {
            showGhostPermissionDialog = true
            viewModel.showGhostPermissions = false
        }
    }

    if (viewModel.showPaymentDialog) {
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

    if (viewModel.showEnhancedStabilizationUnavailableDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEnhancedStabilizationUnavailableDialog,
            title = {
                Text(
                    text = stringResource(R.string.eis_plus_unavailable_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.eis_plus_unavailable_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissEnhancedStabilizationUnavailableDialog) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (viewModel.showVideoHeatStopDialog) {
        VideoHeatStopDialog(
            stopped = viewModel.videoHeatStop,
            phoneNow = viewModel.videoHeatPhone,
            coolFromC = viewModel.videoHeatCoolFromC,
            onBackToCamera = viewModel::dismissVideoHeatStopDialog,
        )
    }

    if (showProUnavailableDialog) {
        ProModeUnavailableDialog(
            isFrontCamera = state.getCurrentCameraInfo()?.lensFacing ==
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT,
            onDismiss = { showProUnavailableDialog = false },
        )
    }

    if (showGhostPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showGhostPermissionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.ghost_mode_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.ghost_mode_dialog_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ghost_mode_permissions_required),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ghost_mode_overlay_permission),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ghost_mode_file_permission),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGhostPermissionDialog = false
                        isGhostPermissionFlowActive = true
                        if (!Settings.canDrawOverlays(context)) {
                            ghostLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    ("package:${context.packageName}").toUri()
                                )
                            )
                        } else if (!Environment.isExternalStorageManager()) {
                            ghostLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    ("package:${context.packageName}").toUri()
                                )
                            )
                        } else {
                            isGhostPermissionFlowActive = false
                            viewModel.togglePhantomMode()
                        }
                    }
                ) {
                    Text(stringResource(R.string.ghost_mode_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGhostPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                cameraScreenBounds = coordinates.boundsInRoot()
            }
    ) {

        val backgroundPainter = rememberBackgroundPainter(viewModel)

        val isVideoMode = state.captureMode == CaptureMode.VIDEO
        val isPhotoStyleMode = state.captureMode != CaptureMode.VIDEO
        val setParameterAuto: (CameraParameter) -> Unit = { parameter ->
            when (parameter) {
                CameraParameter.EXPOSURE_COMPENSATION -> viewModel.setExposureCompensation(0)
                CameraParameter.SHUTTER_SPEED -> viewModel.setShutterSpeedAuto(true)
                CameraParameter.ISO -> viewModel.setIsoAuto(true)
                CameraParameter.FOCUS -> viewModel.setAutoFocus(true)
                CameraParameter.WHITE_BALANCE ->
                    viewModel.setAwbMode(android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO)
            }
        }
        // A tap toggles the value's ruler at once; a second tap on it within 320 ms puts it back to auto.
        val onParameterClick: (CameraParameter) -> Unit = { parameter ->
            val now = android.os.SystemClock.uptimeMillis()
            val last = lastValueTap
            if (last != null && last.first == parameter && now - last.second < 320L) {
                lastValueTap = null
                setParameterAuto(parameter)
            } else {
                lastValueTap = parameter to now
                val wasSelected = selectedParameter == parameter
                selectedParameter = parameter
                showParameterRuler = !wasSelected || !showParameterRuler
            }
        }
        val rulerShown = showParameterRuler || scrubRulerShown
        LaunchedEffect(showParameterRuler) {
            if (!showParameterRuler) {
                parameterRulerBounds = null
            }
        }
        // The everyday row gives way to any panel and to a recording.
        LaunchedEffect(activePanel, state.videoRecordingState.isRecording) {
            if (activePanel != ActivePanel.NONE || state.videoRecordingState.isRecording) everydayRowShown = false
        }
        // XPAN keeps its manual values on the side bar, so there a swipe only moves the everyday row.
        val onPictureSwipe by rememberUpdatedState<(Boolean) -> Unit> { up ->
            if (up) {
                if (everydayRowShown) everydayRowShown = false else if (!isXpan) manualControlsShown = true
            } else if (manualControlsShown && !isXpan) {
                manualControlsShown = false
                showParameterRuler = false
            } else {
                everydayRowShown = true
            }
        }

        val density = LocalDensity.current
        val width = with(density) { constraints.maxWidth.toDp() }
        val height = with(density) { constraints.maxHeight.toDp() }
        val topInset = cameraTopCutoutInset()
        val navigationBarHeight = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
        val contentHeight = (height - navigationBarHeight).coerceAtLeast(0.dp)
        val viewfinderFramePadding = if (isXpan) XpanViewfinderInnerBorder * 2 else 0.dp
        val sideControlSpace = if (isXpan) XpanSideControlReservedWidth else 0.dp
        val maxCardWidth = (width - sideControlSpace * 2)
            .coerceAtLeast(viewfinderFramePadding)
        // XPAN runs the full height, from the top inset to the bottom edge, between its side bars.
        val maxCardHeight = if (isXpan) {
            (contentHeight - topInset).coerceAtLeast(viewfinderFramePadding)
        } else {
            contentHeight
        }
        val maxViewfinderContentWidth = (maxCardWidth - viewfinderFramePadding)
            .coerceAtLeast(0.dp)
        val maxViewfinderContentHeight = (maxCardHeight - viewfinderFramePadding)
            .coerceAtLeast(0.dp)
        val viewfinderContentWidth = minOf(
            maxViewfinderContentWidth,
            maxViewfinderContentHeight * previewAspectRatio
        )
        val cardWidth = viewfinderContentWidth + viewfinderFramePadding
        val cardHeight = viewfinderContentWidth / previewAspectRatio + viewfinderFramePadding
        val xpanSideWidth = ((width - cardWidth) / 2).coerceAtLeast(0.dp)
        val placeViewfinderBelowInset = contentHeight - cardHeight >= topInset
        // Balance small previews between the top edge and the collapsed controls. Reserving the
        // controls first prevents the downward shift from pushing parameters back into the image.
        val extraViewfinderTopSpacing = if (isXpan) {
            0.dp
        } else {
            ((contentHeight - topInset - cardHeight -
                CameraControlsLayoutDefaults.CollapsedControlsHeight) / 2).coerceAtLeast(0.dp)
        }
        val viewfinderTop = if (placeViewfinderBelowInset) {
            topInset + extraViewfinderTopSpacing
        } else {
            ((contentHeight - cardHeight) / 2).coerceAtLeast(0.dp)
        }

        val parameterRuler = @Composable { rulerModifier: Modifier ->
            val whiteBalanceCurrentValue = if (
                state.awbMode == android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO
            ) {
                state.actualAwbTemperature?.toFloat() ?: state.awbTemperature.toFloat()
            } else {
                state.awbTemperature.toFloat()
            }
            ParameterRuler(
                parameter = selectedParameter,
                currentValue = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.exposureCompensation * state.getExposureCompensationStep()
                    CameraParameter.SHUTTER_SPEED -> state.shutterSpeed.toFloat()
                    CameraParameter.ISO -> state.iso.toFloat()
                    CameraParameter.FOCUS -> state.focusDistance
                    CameraParameter.WHITE_BALANCE -> whiteBalanceCurrentValue
                },
                minValue = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.getExposureCompensationRange().lower * state.getExposureCompensationStep()
                    CameraParameter.SHUTTER_SPEED -> state.getManualShutterSpeedRange().lower.toFloat()
                    CameraParameter.ISO -> state.getIsoRange().lower.toFloat()
                    CameraParameter.FOCUS -> 0f
                    CameraParameter.WHITE_BALANCE -> state.awbTemperatureMin.toFloat()
                },
                maxValue = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.getExposureCompensationRange().upper * state.getExposureCompensationStep()
                    CameraParameter.SHUTTER_SPEED -> state.getManualShutterSpeedRange().upper.toFloat()
                    CameraParameter.ISO -> state.getIsoRange().upper.toFloat()
                    CameraParameter.FOCUS -> state.minimumFocusDistance
                    CameraParameter.WHITE_BALANCE -> state.awbTemperatureMax.toFloat()
                },
                isAdjustable = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.isAutoExposure
                    CameraParameter.SHUTTER_SPEED, CameraParameter.ISO -> state.isManualSensorSupported
                    CameraParameter.FOCUS -> state.minimumFocusDistance > 0f
                    CameraParameter.WHITE_BALANCE -> state.canAdjustWhiteBalance
                },
                isAutoMode = when (selectedParameter) {
                    CameraParameter.SHUTTER_SPEED -> state.isShutterSpeedAuto
                    CameraParameter.ISO -> state.isIsoAuto
                    CameraParameter.FOCUS -> state.isAutoFocus
                    CameraParameter.WHITE_BALANCE ->
                        state.awbMode == android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO
                    CameraParameter.EXPOSURE_COMPENSATION -> false
                },
                showAutoButton = when (selectedParameter) {
                    CameraParameter.SHUTTER_SPEED, CameraParameter.ISO, CameraParameter.WHITE_BALANCE, CameraParameter.FOCUS -> true
                    else -> false
                },
                isAutoModeToggleEnabled = when (selectedParameter) {
                    CameraParameter.WHITE_BALANCE -> state.canAdjustWhiteBalance
                    CameraParameter.FOCUS -> state.minimumFocusDistance > 0f
                    else -> true
                },
                valueStep = state.getExposureCompensationStep(),
                showHyperfocalButton = selectedParameter == CameraParameter.FOCUS && state.minimumFocusDistance > 0f,
                hyperfocalEnabled = state.isHyperfocalFocusEnabled,
                onSetAuto = { setParameterAuto(selectedParameter) },
                scrub = rulerScrub,
                onHyperfocalToggle = { enabled ->
                    viewModel.setHyperfocalFocusEnabled(enabled)
                },
                onValueChange = { value ->
                    when (selectedParameter) {
                        CameraParameter.EXPOSURE_COMPENSATION -> viewModel.setExposureCompensation((value / state.getExposureCompensationStep()).roundToInt())
                        CameraParameter.SHUTTER_SPEED -> viewModel.setShutterSpeed(value.toLong())
                        CameraParameter.ISO -> viewModel.setIso(value.toInt())
                        CameraParameter.FOCUS -> viewModel.setFocusDistance(value)
                        CameraParameter.WHITE_BALANCE -> viewModel.setAwbTemperature(value.toInt())
                    }
                },
                onAutoModeToggle = {
                    when (selectedParameter) {
                        CameraParameter.SHUTTER_SPEED -> viewModel.setShutterSpeedAuto(!state.isShutterSpeedAuto)
                        CameraParameter.ISO -> viewModel.setIsoAuto(!state.isIsoAuto)
                        CameraParameter.WHITE_BALANCE -> {
                            if (state.canAdjustWhiteBalance) {
                                if (state.awbMode == android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO) {
                                    viewModel.setAwbMode(android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_OFF)
                                } else {
                                    viewModel.setAwbMode(android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO)
                                }
                            }
                        }

                        CameraParameter.FOCUS -> viewModel.setAutoFocus(!state.isAutoFocus)
                        else -> {}
                    }
                },
                modifier = rulerModifier,
            )
        }

        val parameterBar = @Composable {
                activeParameter: CameraParameter?,
                onParameterClick: (CameraParameter) -> Unit ->
            CameraParameterBar(
                state = state,
                selectedParameter = activeParameter,
                onParameterClick = onParameterClick,
                scrub = rulerScrub,
                onScrubStart = { parameter -> selectedParameter = parameter }
            )
        }

        val viewfinder = @Composable {
            Card(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight),
                shape = RoundedCornerShape(if (isXpan) XpanViewfinderInnerBorder else 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .padding(if (isXpan) XpanViewfinderInnerBorder else 0.dp)
                        .fillMaxSize()
                        .background(Color.Black)
                        .onGloballyPositioned { coordinates ->
                            previewBounds = coordinates.boundsInRoot()
                        }
                        .pointerInput(state.availableCameras) {
                            val switchThreshold = max(size.width * 0.25f, 96.dp.toPx())
                            val flingVelocity = 900.dp.toPx()
                            val minFlingDistance = 48.dp.toPx()
                            val touchSlop = viewConfiguration.touchSlop
                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                var gestureStartedInPreviewControl: Boolean? = null
                                var childConsumedDrag = false
                                // 横向滑动切换 LUT：先越过 touch slop 并确认以横向为主才进入拖动，
                                // 竖向/斜向手势与点按不会触发。
                                var swipeStart: Offset? = null
                                var swipeDownTime = 0L
                                var swipeDecided = false
                                var swipeTracking = false
                                var verticalTracking = false
                                val velocityTracker = VelocityTracker()
                                fun cancelSwipe() {
                                    if (swipeTracking) lutSwipeState.cancel()
                                    swipeTracking = false
                                    verticalTracking = false
                                    swipeDecided = true
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (gestureStartedInPreviewControl == null) {
                                        val firstPressedChange =
                                            event.changes.firstOrNull { it.pressed }
                                        val currentPreviewBounds = previewBounds
                                        gestureStartedInPreviewControl =
                                            if (firstPressedChange != null && currentPreviewBounds != null) {
                                                val positionInRoot =
                                                    currentPreviewBounds.topLeft + firstPressedChange.position
                                                // The footer's top: the Looks and Q buttons, above the shutter.
                                                val footerTop = captureButtonBounds?.let { it.top - 53.dp.toPx() }
                                                parameterRulerBounds?.contains(positionInRoot) == true ||
                                                    // Quick settings open: a tap still closes them and focuses.
                                                    activePanel != ActivePanel.NONE ||
                                                    (footerTop != null && positionInRoot.y > footerTop)
                                            } else {
                                                false
                                            }
                                    }
                                    if (gestureStartedInPreviewControl == true) {
                                        if (event.changes.all { !it.pressed }) {
                                            viewModel.isZooming = false
                                            break
                                        }
                                        continue
                                    }
                                    if (event.changes.size >= 2) {
                                        // Pinch to zoom
                                        childConsumedDrag = true
                                        cancelSwipe()
                                        viewModel.isZooming = true
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f) {
                                            val nextZoom =
                                                (viewModel.zoomRatioByMain * zoom).coerceIn(
                                                    viewModel.globalMinZoom,
                                                    viewModel.globalMaxZoom
                                                )
                                            setZoomWithPreviewTransition(nextZoom)
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (event.changes.size == 1) {
                                        // Single finger -> horizontal drag for LUT switch
                                        val change = event.changes[0]
                                        if (change.isConsumed && change.position != change.previousPosition) {
                                            childConsumedDrag = true
                                            cancelSwipe()
                                        }
                                        if (change.pressed && !childConsumedDrag) {
                                            val start = swipeStart ?: change.position.also {
                                                swipeStart = it
                                                swipeDownTime = change.uptimeMillis
                                                velocityTracker.resetTracking()
                                            }
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val dx = change.position.x - start.x
                                            val dy = change.position.y - start.y
                                            if (!swipeDecided && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                                swipeDecided = true
                                                val previousLut = viewModel.peekPreviousLut()
                                                val nextLut = viewModel.peekNextLut()
                                                val horizontal = abs(dx) > abs(dy) * 1.5f
                                                // 按住超过长按时长后才移动属于长按锁焦，不再作为滑动切换
                                                val afterLongPress =
                                                    change.uptimeMillis - swipeDownTime >= longPressTimeout
                                                if (
                                                    horizontal &&
                                                    !afterLongPress &&
                                                    nextLut != null &&
                                                    // The look stays fixed during a recording.
                                                    !state.videoRecordingState.isRecording &&
                                                    nextLut.id != currentLutId
                                                ) {
                                                    swipeTracking = true
                                                    lutSwipeState.begin(
                                                        width = size.width,
                                                        threshold = switchThreshold,
                                                        previousName = previousLut?.getName(),
                                                        nextName = nextLut.getName(),
                                                    )
                                                } else if (
                                                    abs(dy) > abs(dx) * 1.5f &&
                                                    !afterLongPress &&
                                                    !state.videoRecordingState.isRecording &&
                                                    !viewModel.showPortraitIntro &&
                                                    !viewModel.showProGuide &&
                                                    !viewModel.showBasicsGuide
                                                ) {
                                                    verticalTracking = true
                                                }
                                            }
                                            if (verticalTracking) {
                                                // Taken by the swipe: no tap focus or long-press lock at its end.
                                                change.consume()
                                            }
                                            if (swipeTracking) {
                                                // 消费事件，使预览上的点按对焦 / 长按锁焦随之取消
                                                change.consume()
                                                lutSwipeState.drag(sign(dx) * max(abs(dx) - touchSlop, 0f))
                                            }
                                        } else if (!change.pressed && verticalTracking) {
                                            change.consume()
                                            verticalTracking = false
                                            val dy = change.position.y - (swipeStart?.y ?: change.position.y)
                                            if (abs(dy) >= 40.dp.toPx()) onPictureSwipe(dy < 0)
                                        } else if (!change.pressed && swipeTracking) {
                                            change.consume()
                                            val direction = lutSwipeState.release(
                                                velocityX = velocityTracker.calculateVelocity().x,
                                                flingVelocity = flingVelocity,
                                                minFlingDistance = minFlingDistance,
                                            )
                                            swipeTracking = false
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
                                    }

                                    if (event.changes.all { !it.pressed }) {
                                        cancelSwipe()
                                        viewModel.isZooming = false
                                        break
                                    }
                                }
                            }
                        },
                ) {
                    val currentCameraId = state.currentCameraId
                    val calibrationOffset by viewModel.getCameraOrientationOffset(currentCameraId)
                        .collectAsState(initial = 0)
                    val eyeFocusActive = eyeFocusEnabled &&
                        viewModel.isEyeFocusRuntimeAvailable &&
                        state.isAutoFocus &&
                        !state.isHyperfocalFocusEnabled &&
                        (state.focusPoint == null || state.focusPointSource == FocusPointSource.EYE)
                    val portraitMaskActive = state.captureMode == CaptureMode.PHOTO &&
                        state.useRaw &&
                        state.isRawSupported

                    // 相机准备完成后再创建 Surface，首次只打开最终选中的镜头。
                    if (isCameraPrepared) {
                        CameraPreviewGL(
                            aspectRatio = previewAspectRatio,
                            previewSize = previewSize,
                            captureSize = state.currentCaptureSize,
                            captureMode = state.captureMode,
                            sensorOrientation = state.getCurrentCameraInfo()?.sensorOrientation
                                ?: 0,
                            lensFacing = if (state.getCurrentCameraInfo()?.lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) 0 else 1,
                            calibrationOffset = calibrationOffset,
                            baselineLut = viewModel.currentBaselineLutConfig,
                            currentLut = viewModel.currentLutConfig,
                            enginePreviewPlan = viewModel.currentEnginePreviewPlan,
                            baselineColorRecipeParams = currentBaselineRecipeParams,
                            colorRecipeParams = previewRecipeParamsOverride ?: currentRecipeParams,
                            focusPoint = state.focusPoint,
                            meteringPoint = state.meteringPoint,
                            canMoveMeteringPoint = state.supportsPointMetering &&
                                !state.isCapturing && activePanel == ActivePanel.NONE,
                            onMeteringPointMove = viewModel::moveMeteringPoint,
                            onFocusPointMove = viewModel::moveFocusPoint,
                            onMeteringPointMerge = viewModel::mergeMeteringPoint,
                            focusPointSource = state.focusPointSource,
                            isFocusLocked = state.isFocusLocked,
                            isFocusing = state.isFocusing,
                            focusSuccess = state.focusSuccess,
                            meteringMode = state.effectiveMeteringMode,
                            onSurfaceTextureReady = { surfaceTexture ->
                                previewSurfaceTexture = surfaceTexture
                            },
                            onSurfaceDestroyed = { surfaceTexture ->
                                if (previewSurfaceTexture === surfaceTexture) {
                                    previewSurfaceTexture = null
                                }
                                viewModel.closeCamera(surfaceTexture)
                            },
                            onTap = { x, y, w, h ->
                                // Quick settings, the everyday row and an open ruler close and the tap still focuses;
                                // the manual values stay (only a swipe down hides them). Other panels only close.
                                everydayRowShown = false
                                showParameterRuler = false
                                scrubRulerShown = false
                                if (activePanel == ActivePanel.SETTINGS) activePanel = ActivePanel.NONE
                                if (state.isFocusLocked) {
                                    viewModel.unlockFocus()
                                } else if (activePanel != ActivePanel.NONE) {
                                    activePanel = ActivePanel.NONE
                                } else {
                                    viewModel.focusOnPoint(x, y, w, h)
                                }
                            },
                            onLongPress = { x, y, w, h ->
                                if (activePanel != ActivePanel.NONE) {
                                    activePanel = ActivePanel.NONE
                                } else {
                                    viewModel.lockFocusOnPoint(x, y, w, h)
                                }
                            },
                            onHistogramUpdated = { viewModel.handleHistogramUpdate(it) },
                            onMeteringUpdated = { w, l -> viewModel.handleMeteringUpdate(w, l) },
                            onHighlightPointUpdated = { hx, hy ->
                                viewModel.handleHighlightPointUpdate(
                                    hx,
                                    hy
                                )
                            },
                            onEyeFocusInputAvailable = if (eyeFocusActive || portraitMaskActive || state.isPortraitMode) {
                                viewModel::handleEyeFocusInputUpdate
                            } else {
                                null
                            },
                            onFirstPreviewFrame = viewModel::onFirstPreviewFrame,
                            onGLSurfaceViewReady = {
                                viewModel.glSurfaceView = it
                            },
                            livePhotoRecorder = viewModel.livePhotoRecorder,
                            videoLogProfile = state.videoConfig.logProfile,
                            isEyeFocusBusy = viewModel.isEyeFocusBusy,
                            isAutoFocus = state.isAutoFocus,
                            focusPeakingEnabled = focusPeakingEnabled && !state.isHyperfocalFocusEnabled,
                            stabilizationCoordinator = viewModel.realtimeStabilizationCoordinator,
                            stabilizationInputSize = if (
                                state.captureMode == CaptureMode.VIDEO &&
                                state.videoConfig.stabilizationMode ==
                                    VideoStabilizationMode.ENHANCED
                            ) {
                                state.videoCapabilities
                                    .enhancedStabilizationInputSizesByResolution[
                                        state.videoConfig.resolution
                                    ] ?: previewSize
                            } else {
                                previewSize
                            },
                            photoPreviewStabilizationEnabled =
                                state.photoPreviewStabilizationEnabled,
                            videoPreviewStabilizationEnabled =
                                state.videoConfig.stabilizationMode ==
                                    VideoStabilizationMode.ENHANCED,
                            videoPreviewStabilizationStrength =
                                state.videoConfig.enhancedStabilizationStrength,
                            videoPreviewStabilizationLookahead =
                                state.videoConfig.enhancedStabilizationLookahead,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Black at start-up until the camera's first frame, then a quick fade-in. Later switches
                    // are never covered: the held frame above moves to the new picture and fades out.
                    if (!hasPlayedInitialPreviewTransition) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = previewTransitionCoverAlpha.value }
                                .background(Color.Black)
                        )
                    }

                    LensSwitchTransitionOverlay(lensSwitchTransition, Modifier.fillMaxSize())

                    // While the camera is off to cool down, show black instead of the last frozen frame.
                    if (viewModel.showVideoHeatStopDialog) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }

                    val showVirtualApertureIndicator =
                        state.captureMode != CaptureMode.VIDEO &&
                            state.isVirtualApertureEnabled
                    if (showVirtualApertureIndicator) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    top = TopEdgeLineHeight + 8.dp,
                                    end = 12.dp
                                )
                                .align(Alignment.TopEnd),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (showVirtualApertureIndicator) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.virtual_aperture_viewfinder_hint,
                                            formatVirtualApertureFStop(state.virtualAperture)
                                        ),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        style = TextStyle(shadow = ViewfinderTextShadow),
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 3.dp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        multipleExposureState.previewBitmap?.let { previewBitmap ->
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .autoRotate(matchParentSize = true),
                                alpha = 0.45f,
                                contentScale = ContentScale.Crop
                            )
                        }

                        // 网格线覆盖
                        if (state.showGrid) {
                            GridOverlay(
                                aspectRatio = previewAspectRatio,
                                style = state.gridStyle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 水平仪覆盖
                        if (showLevelIndicator) {
                            LevelIndicatorOverlay(
                                aspectRatio = previewAspectRatio,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 倒计时覆盖
                        if (state.countdownValue > 0) {
                            CountdownOverlay(
                                countdownValue = state.countdownValue,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (state.burstCapturing && burstCapturingCount > 0) {
                            BurstCaptureOverlay(
                                count = burstCapturingCount,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = TopEdgeLineHeight)
                            )
                        }

                        if (useMultipleExposure && state.captureMode == CaptureMode.PHOTO) {
                            MultipleExposureOverlay(
                                state = multipleExposureState,
                                onFinish = { viewModel.finishMultipleExposureSession() },
                                onUndo = { viewModel.undoLastMultipleExposureFrame() },
                                onCancel = { viewModel.cancelMultipleExposureSession() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = TopEdgeLineHeight)
                            )
                        }

                    }

                    if (isXpan) {
                        CornerOverlay(
                            modifier = Modifier.fillMaxSize(),
                            radius = XpanViewfinderInnerBorder,
                            color = Color.Black
                        )
                    }

                    LutSwipeIndicator(state = lutSwipeState)
                }
            }

            if (isXpan) {
                // Center both side bars in the space above the capture controls, which
                // overlap the lower part of the XPAN viewfinder.
                val sideControlsBottom = minOf(
                    viewfinderTop + cardHeight,
                    contentHeight - CameraControlsLayoutDefaults.CaptureAreaHeight -
                        CameraControlsLayoutDefaults.CaptureClearance
                )
                val sideControlsHeight = (sideControlsBottom - viewfinderTop)
                    .coerceIn(0.dp, cardHeight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .padding(bottom = cardHeight - sideControlsHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(xpanSideWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        ZoomControlBarVerticel(
                            viewModel = viewModel,
                            zoomRatio = viewModel.zoomRatioByMain,
                            availableCameras = state.availableCameras,
                            currentCameraId = state.getCurrentCameraInfo()?.cameraId ?: "0",
                            onZoomChange = { setZoomWithPreviewTransition(it) },
                            onZoomStopClick = { animateZoomStopWithPreviewTransition(it) },
                            onLensSwitch = { lensId -> switchToLensWithPreviewTransition(lensId) },
                            onFilterClick = {
                                activePanel =
                                    if (activePanel == ActivePanel.FILTERS) ActivePanel.NONE else ActivePanel.FILTERS
                            },
                            onFilterButtonBoundsChanged = { bounds ->
                                filterButtonBounds = bounds
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(xpanSideWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraParameterBarVerticel(
                            state = state,
                            selectedParameter = selectedParameter.takeIf {
                                showParameterRuler
                            },
                            onParameterClick = onParameterClick,
                        )
                    }
                }
            }
        }


        val controls = @Composable { modifier: Modifier ->
            Controls(
                state = state,
                viewModel = viewModel,
                galleryViewModel = galleryViewModel,
                latestPhoto = latestPhoto,
                useMultipleExposure = useMultipleExposure,
                multipleExposureState = multipleExposureState,
                onGalleryThumbnailBoundsChanged = { bounds ->
                    galleryThumbnailBounds = bounds
                },
                onCaptureButtonBoundsChanged = { bounds ->
                    captureButtonBounds = bounds
                },
                onSwitchCameraClick = ::switchCameraWithPreviewTransition,
                onCaptureModeSelected = ::selectShootingMode,
                previewPending = !previewTransitionRevealing &&
                    (!hasPlayedInitialPreviewTransition || previewTransitionAwaitingResume),
                onCaptureTap = {
                    val shouldDebounceRawCapture =
                        state.useRaw && state.captureMode == CaptureMode.PHOTO
                    if (shouldDebounceRawCapture) {
                        if (rawCaptureTapLocked) {
                            return@Controls
                        }
                        rawCaptureTapLocked = true
                        scope.launch {
                            delay(RawCaptureTapDebounceMillis)
                            rawCaptureTapLocked = false
                        }
                    }

                    if (enableDevelopAnimation && state.captureMode == CaptureMode.PHOTO) {
                        viewModel.glSurfaceView?.capturePreviewFrame(maxLongEdge = 1440) { bitmap ->
                            if (scope.isActive && enableDevelopAnimation &&
                                currentCaptureModeForEffects == CaptureMode.PHOTO
                            ) {
                                pendingCaptureAnimationBitmap?.recycleIfAlive()
                                pendingCaptureAnimationBitmap = bitmap
                            } else {
                                bitmap.recycleIfAlive()
                            }
                        }
                    }
                    viewModel.capture()
                },
                onGalleryClick = {
                    onGalleryClick()
                },
                isXpan = isXpan,
                onLooksClick = {
                    activePanel = if (activePanel == ActivePanel.FILTERS) ActivePanel.NONE else ActivePanel.FILTERS
                },
                onLooksBoundsChanged = { bounds ->
                    filterButtonBounds = bounds
                },
                modeLine = modeLine,
                onQuickSettingsClick = {
                    activePanel = if (activePanel == ActivePanel.SETTINGS) ActivePanel.NONE else ActivePanel.SETTINGS
                },
                zoomCollar = if (!isXpan && activePanel == ActivePanel.NONE) {
                    { fold ->
                        ZoomCollar(
                            viewModel = viewModel,
                            zoomRatio = viewModel.zoomRatioByMain,
                            availableCameras = state.availableCameras,
                            currentCameraId = state.getCurrentCameraInfo()?.cameraId ?: "0",
                            enabled = state.getCurrentCameraInfo() != null,
                            onZoom = { setZoomWithPreviewTransition(it) },
                            onStop = { animateZoomStopWithPreviewTransition(it) },
                            fold = fold
                        )
                    }
                } else {
                    null
                },
                sideControlInset = if (isXpan) {
                    (xpanSideWidth - CameraControlsLayoutDefaults.SideButtonSize) / 2
                } else {
                    null
                },
                modifier = modifier
            )
        }

        val quickSheetCover by animateFloatAsState(
            targetValue = if (activePanel == ActivePanel.SETTINGS) 1f else 0f,
            animationSpec = tween(160),
            label = "quickSheetCover"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paint(backgroundPainter, contentScale = ContentScale.Crop)
                .navigationBarsPadding(),
        ) {
            // The viewfinder is always the bottom layer. Its selected aspect ratio is fitted
            // to the full available area before camera controls are overlaid.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .align(Alignment.TopCenter)
                    .offset(y = viewfinderTop)
                    .onGloballyPositioned { coordinates ->
                        viewfinderAreaBounds = coordinates.boundsInRoot()
                    },
                contentAlignment = Alignment.Center
            ) {
                viewfinder()
            }

            previewTransitionFrame?.let { frame ->
                var layerOrigin by remember { mutableStateOf(Offset.Zero) }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { layerOrigin = it.positionInRoot() }
                ) {
                    val cover = previewTransitionCoverAlpha.value
                    val from = previewMorphFrom
                    val to = previewMorphTo
                    if (cover <= 0f || from == null || to == null) return@Canvas
                    val rect = androidx.compose.ui.geometry.lerp(from, to, previewMorphProgress.value).translate(-layerOrigin)
                    // Fades on the new camera's first frame, even while it is still moving.
                    drawCenterCropped(frame, rect, alpha = cover)
                }
            }

            // A picture reaching below the top of the shutter gets a soft scrim under the footer controls.
            // It only ever fades: while a held frame moves from or to a picture under the shutter, it stays
            // until that move has finished, and comes up as soon as the new picture will reach there, so it is
            // never added over a picture or taken away from one.
            val shutterTopAboveBottom = CameraControlsLayoutDefaults.BottomPadding +
                (CameraControlsLayoutDefaults.ModeZoneHeight + CameraControlsLayoutDefaults.CaptureButtonSize) / 2
            val liveUnderShutter = viewfinderTop + cardHeight > contentHeight - shutterTopAboveBottom
            val heldFrameUnderShutter by remember {
                derivedStateOf {
                    val top = captureButtonBounds?.top ?: return@derivedStateOf false
                    val from = previewMorphFrom
                    val to = previewMorphTo
                    // The move, not the held frame's fade: the frame may fade out before it has arrived.
                    if (previewTransitionFrame == null || previewMorphProgress.value >= 1f || from == null || to == null) {
                        false
                    } else {
                        from.bottom > top || to.bottom > top
                    }
                }
            }
            val footerScrim by animateFloatAsState(
                targetValue = if (liveUnderShutter || heldFrameUnderShutter) 1f else 0f,
                animationSpec = tween(220),
                label = "footerScrim"
            )
            if (footerScrim > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .graphicsLayer { alpha = footerScrim }
                        .background(
                            Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.45f))
                        )
                )
            }

            // Active settings and the histogram on the picture's top edge, while quick settings are closed and
            // nothing records (the recording line takes their place). A tap opens quick settings.
            val topEdgeMarks = buildList {
                if (isVideoMode) {
                    if (state.videoConfig.torchEnabled && state.videoCapabilities.supportsTorch) {
                        add(TopEdgeMark(AppIcons.BulbLine))
                    }
                    if (state.videoConfig.logProfile != VideoLogProfile.OFF) add(TopEdgeMark(null, "LOG"))
                } else {
                    if (useLivePhoto) add(TopEdgeMark(AppIcons.LivePhotoLine))
                    if (state.timerSeconds > 0) add(TopEdgeMark(AppIcons.StopwatchLine, "${state.timerSeconds}s"))
                    // The front camera has no flash, whatever the stored flash mode says.
                    if (state.flashMode != 0 && state.videoCapabilities.supportsTorch) {
                        add(TopEdgeMark(if (state.flashMode == 2) AppIcons.BulbLine else AppIcons.BoltLine))
                    }
                    if (useMultipleExposure) add(TopEdgeMark(AppIcons.MultiExposureLine))
                }
            }
            val topEdgeHistogram = state.histogram.takeIf { isPhotoStyleMode && showHistogram }
            AnimatedVisibility(
                visible = activePanel != ActivePanel.SETTINGS && !state.videoRecordingState.isRecording &&
                    !everydayRowShown && (topEdgeMarks.isNotEmpty() || topEdgeHistogram != null),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = viewfinderTop)
            ) {
                TopEdgeLine(
                    marks = topEdgeMarks,
                    histogram = topEdgeHistogram,
                    onClick = { everydayRowShown = true }
                )
            }

            // While recording: time, heat and format, centred just below the picture's top edge.
            // It drops in a few dp and fades in when recording starts, and leaves the same way.
            val recordingHudDrop = with(LocalDensity.current) { 8.dp.roundToPx() }
            AnimatedVisibility(
                visible = isVideoMode && state.videoRecordingState.isRecording,
                enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -recordingHudDrop } +
                    fadeIn(tween(200)),
                exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -recordingHudDrop } +
                    fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = viewfinderTop + 8.dp)
            ) {
                RecordingHud(
                    elapsedMs = state.videoRecordingState.elapsedMs,
                    isHot = state.videoRecordingState.isHot,
                    heatGauge = state.videoRecordingState.heatGauge,
                    videoConfig = state.videoConfig
                )
            }

            // Portrait offers its guide in a pill centred just above the picture's bottom, the manual
            // values and the shutter, until the guide has been opened once. After that the same pill
            // reminds once, while the depth model is missing, and opens the guide at its download.
            val depthModelState by DepthModelManager.observe(context).collectAsState()
            val depthModelMissing = depthModelState is DepthModelDownloadState.Missing ||
                depthModelState is DepthModelDownloadState.Failed
            val showDepthReminder = portraitIntroSeen && !portraitDepthReminderSeen && depthModelMissing
            // Guide pills sit 8 dp above the highest of the picture's bottom, the manual values and the shutter.
            val guidePillBottom = listOfNotNull(
                previewBounds?.bottom,
                parameterBarTop.takeIf { !isXpan && (manualControlsShown || anyManualValue) },
                captureButtonBounds?.top,
            ).minOrNull()?.let { it - (cameraScreenBounds?.top ?: 0f) }
            val guidePillPlacement = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, ((guidePillBottom ?: 0f) - 8.dp.toPx()).roundToInt() - placeable.height)
                    }
                }
            if (
                state.isPortraitMode && state.captureMode == CaptureMode.PHOTO &&
                (!portraitIntroSeen || showDepthReminder) &&
                activePanel == ActivePanel.NONE && !viewModel.showPortraitIntro
            ) {
                if (guidePillBottom != null) {
                    ModeGuidePill(
                        icon = if (showDepthReminder) AppIcons.DownloadLine else AppIcons.InfoLine,
                        text = stringResource(if (showDepthReminder) R.string.portrait_depth_pill else R.string.portrait_guide_pill),
                        onClick = {
                            if (showDepthReminder) {
                                preferencesRepository.updateAsync { this.portraitDepthReminderSeen = true }
                                viewModel.showPortraitHelp(atBlur = true)
                            } else {
                                viewModel.showPortraitHelp()
                            }
                        },
                        modifier = guidePillPlacement
                    )
                }
            }
            // Pro offers its guide the same way, until it has been opened once.
            val proMode = state.captureMode == CaptureMode.PHOTO && state.useRaw && state.isRawSupported &&
                !state.isPortraitMode
            if (
                proMode && !proGuideSeen && guidePillBottom != null &&
                activePanel == ActivePanel.NONE && !viewModel.showProGuide
            ) {
                ModeGuidePill(
                    icon = AppIcons.InfoLine,
                    text = stringResource(R.string.pro_guide_pill),
                    onClick = { viewModel.showProHelp() },
                    modifier = guidePillPlacement
                )
            }
            // Photo mode offers the camera basics the same way, on every launch until opened once.
            val photoMode = state.captureMode == CaptureMode.PHOTO && !proMode && !state.isPortraitMode
            if (
                photoMode && !basicsGuideSeen && guidePillBottom != null &&
                activePanel == ActivePanel.NONE && !viewModel.showBasicsGuide
            ) {
                ModeGuidePill(
                    icon = AppIcons.InfoLine,
                    text = stringResource(R.string.basics_guide_pill),
                    onClick = { viewModel.showBasicsHelp() },
                    modifier = guidePillPlacement
                )
            }

            CameraBottomControlsLayout(
                viewfinderBottom = viewfinderTop + cardHeight,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The footer gives way under the quick settings sheet so it doesn't show through.
                        alpha = controlsArrival.value * (1f - quickSheetCover)
                        translationY = (1f - controlsArrival.value) * 16.dp.toPx()
                    },
                parameterRuler = {
                    AnimatedVisibility(
                        visible = rulerShown,
                        // Gives way with the footer while the mode list is open.
                        modifier = Modifier.graphicsLayer { alpha = 1f - modeLine.side },
                        enter = expandVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Bottom
                        ) + fadeIn(tween(160)),
                        exit = shrinkVertically(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Bottom
                        ) + fadeOut(tween(120))
                    ) {
                        parameterRuler(
                            Modifier
                                .then(if (isXpan) Modifier.width(viewfinderContentWidth) else Modifier)
                                .onGloballyPositioned { coordinates ->
                                    // Only while it shows: during its exit animation it keeps reporting its
                                    // full rectangle, which would leave swipes there ignored after it has gone.
                                    parameterRulerBounds = coordinates.boundsInRoot().takeIf { rulerShown }
                                }
                        )
                    }
                },
                parameterBar = {
                    if (!isXpan) {
                        // Swipes that start on the values themselves work too: down closes, up opens.
                        Box(
                            Modifier
                                .onGloballyPositioned { parameterBarTop = it.boundsInRoot().top }
                                // Gives way with the footer while the mode list is open.
                                .graphicsLayer { alpha = 1f - modeLine.side }
                                .pointerInput(Unit) {
                                    var dragged = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { dragged = 0f },
                                        onDragEnd = {
                                            if (abs(dragged) >= 40.dp.toPx()) onPictureSwipe(dragged < 0)
                                        },
                                        onVerticalDrag = { change, amount ->
                                            dragged += amount
                                            change.consume()
                                        }
                                    )
                                }
                        ) {
                            AnimatedVisibility(
                                visible = manualControlsShown,
                                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 }
                            ) {
                                parameterBar(selectedParameter.takeIf { rulerShown }, onParameterClick)
                            }
                            // Closed: only the values set by hand; a tap opens the panel at that value.
                            AnimatedVisibility(
                                visible = !manualControlsShown && anyManualValue,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(120)),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                ManualValuesLine(
                                    state = state,
                                    onValueClick = { parameter ->
                                        manualControlsShown = true
                                        selectedParameter = parameter
                                        showParameterRuler = true
                                    }
                                )
                            }
                        }
                    }
                },
                captureControls = {
                    controls(Modifier.fillMaxWidth())
                }
            )
        }
        val panelDismissBounds = if (isXpan) {
            viewfinderAreaBounds ?: previewBounds
        } else {
            previewBounds
        }

        if (activePanel != ActivePanel.NONE && activePanel != ActivePanel.SETTINGS) {
            PanelDismissPreviewOverlay(
                // A tap on the picture closes the open panel; quick settings leave the picture undimmed.
                bounds = panelDismissBounds,
                parentBounds = cameraScreenBounds,
                dimBackground = false,
                onDismiss = { activePanel = ActivePanel.NONE }
            )
        }

        // Quick settings: a sheet from the bottom, opened by Q.
        // Back closes it instead of leaving the camera.
        BackHandler(enabled = activePanel == ActivePanel.SETTINGS) { activePanel = ActivePanel.NONE }
        CameraTopSheet(
            visible = activePanel == ActivePanel.SETTINGS,
            captureMode = state.captureMode,
            flashMode = state.flashMode,
            onFlashToggle = { viewModel.toggleFlash() },
            timerSeconds = state.timerSeconds,
            onTimerToggle = { viewModel.toggleTimer() },
            useLivePhoto = useLivePhoto,
            onLivePhotoToggle = { viewModel.setUseLivePhoto(!state.useLivePhoto) },
            aspectRatio = state.aspectRatio,
            topSheetAspectRatios = topSheetAspectRatios,
            onAspectRatioChange = { runPreviewTransition { viewModel.setAspectRatio(it) } },
            videoConfig = state.videoConfig,
            videoCapabilities = state.videoCapabilities,
            onVideoResolutionClick = { cycleVideoResolution(state)?.let(viewModel::setVideoResolution) },
            onVideoFpsClick = { cycleVideoFps(state)?.let(viewModel::setVideoFps) },
            onVideoTorchToggle = { videoTorchEnabled = !state.videoConfig.torchEnabled },
            onVideoStabilizationModeChange = viewModel::setVideoStabilizationMode,
            showHistogram = showHistogram,
            onHistogramChange = { showHistogram = it },
            videoAspectRatio = state.videoConfig.aspectRatio,
            onVideoAspectRatioChange = { runPreviewTransition { viewModel.setVideoAspectRatio(it) } },
            videoLogProfile = state.videoConfig.logProfile,
            availableVideoLogProfiles = state.videoCapabilities.availableLogProfiles,
            onVideoLogProfileChange = { viewModel.setVideoLogProfile(it) },
            videoBitrate = state.videoConfig.bitrate,
            onVideoBitrateChange = { viewModel.setVideoBitrate(it) },
            videoCodec = videoCodec,
            onVideoCodecChange = { preferencesRepository.updateAsync { this.videoCodec = it } },
            videoAudioInputId = state.videoConfig.audioInputId,
            videoAudioInputOptions = videoAudioInputOptions,
            onVideoAudioInputChange = { videoAudioInputId = it },
            useRaw = useRaw && state.isRawSupported,
            isRawSupported = state.isRawSupported,
            rawDcpId = rawDcpId,
            rawDcpIdsByLens = rawDcpIdsByLens,
            rawDcpLensOptions = rawDcpLensOptions(state.availableCameras),
            availableDcps = viewModel.availableDcps,
            rawHncsFilmCurveMode = rawHncsFilmCurveMode,
            rawRenderingEngine = rawColorEngine,
            rawToneMappingParameters = rawToneMappingParameters,
            rawSpectralFilmSelection = rawSpectralFilmSelection ?: SpectralFilmSelection(rawSpectralFilmStock ?: "kodak_portra_400"),
            rawSpectralFilmPrint = rawSpectralFilmPrint ?: "kodak_portra_endura",
            ultraHdrEnabled = ultraHdrEnabled,
            onUltraHdrToggle = { preferencesRepository.updateAsync { this.ultraHdrGainMapEnabled = it } },
            isFrontCamera = state.getCurrentCameraInfo()?.lensType == LensType.FRONT,
            photoResolutionCapMp = state.photoResolutionCapMp,
            frontPhotoResolutionCapMp = state.frontPhotoResolutionCapMp,
            photoResolutionAdjustable = state.photoResolutionAdjustable,
            onPhotoResolutionCapChange = viewModel::setPhotoResolutionCapMp,
            onFrontPhotoResolutionCapChange = viewModel::setFrontPhotoResolutionCapMp,
            saveCameraJpeg = saveCameraJpeg,
            onSaveCameraJpegChange = { saveCameraJpeg = it },
            isPortraitMode = state.isPortraitMode,
            portraitQuickBlur = portraitQuickBlur,
            onPortraitQuickBlurChange = viewModel::setPortraitQuickBlur,
            portraitBokehStyle = portraitBokehStyle,
            onPortraitBokehStyleChange = viewModel::setPortraitBokehStyle,
            onPortraitHelp = viewModel::showPortraitHelp,
            onProHelp = viewModel::showProHelp,
            onBasicsHelp = viewModel::showBasicsHelp,
            onOpenSettingsAt = onOpenSettingsAt,
            multiFrameCount = jpgMultiFrameCount,
            onMultiFrameCountChange = { viewModel.setJpgMultiFrameDenoiseFrameCountAndEnable(it) },
            hdrPlusFrameCount = hdrPlusFrameCount,
            hdrPlusMinFrames = if (state.hdrPlusBracketExposureEnabled) MultiFrameConfig.MIN_HDR_PLUS_BRACKET_FRAME_COUNT
            else MultiFrameConfig.MIN_HDR_PLUS_FRAME_COUNT,
            onHdrPlusFrameCountChange = { viewModel.setHdrPlusFrameCount(it) },
            onRawDcpChange = { viewModel.setRawDcpId(it) },
            onRawDcpIdsByLensChange = { viewModel.setRawDcpIdsByLens(it) },
            onRawHncsFilmCurveModeChange = { viewModel.setRawHncsFilmCurveMode(it) },
            onImportRawDcp = { dcpImportLauncher.launch(arrayOf("*/*")) },
            onDeleteRawDcp = { dcp ->
                viewModel.deleteRawDcp(dcp.id) { success ->
                    android.widget.Toast.makeText(
                        context,
                        if (success) R.string.raw_dcp_delete_success else R.string.raw_dcp_delete_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRawColorEngineChange = { viewModel.setRawColorEngine(it) },
            onRawToneMappingParametersChange = { preferencesRepository.updateAsync { this.rawToneMappingParameters = it } },
            onRawSpectralFilmSelectionChange = { viewModel.setRawSpectralFilmSelection(it) },
            onRawSpectralFilmPrintChange = { viewModel.setRawSpectralFilmPrint(it) },
            rawFotufilmDocument = rawFotufilmDocument,
            onRawFotufilmDocumentChange = { viewModel.setRawFotufilmDocument(it) },
            meteringMode = state.meteringMode,
            onMeteringModeChange = { viewModel.setMeteringMode(it) },
            lensChoices = remember(state.availableCameras, state.getCurrentCameraInfo()?.lensType) {
                quickLensChoices(state.availableCameras, state.getCurrentCameraInfo())
            },
            currentCameraId = state.getCurrentCameraInfo()?.cameraId,
            onLensSelect = { switchToLensWithPreviewTransition(it) },
            onFilterManageClick = {
                activePanel = ActivePanel.NONE
                onFilterManagementClick(null)
            },
            onFrameManageClick = {
                activePanel = ActivePanel.NONE
                onFrameManagementClick()
            },
            onPresetManageClick = {
                activePanel = ActivePanel.NONE
                onPresetManagementClick()
            },
            onToolboxClick = {
                activePanel = ActivePanel.NONE
                onToolboxClick()
            },
            onMoreSettingsClick = {
                activePanel = ActivePanel.NONE
                onSettingsClick()
            },
            useJpgMax = useJpgMax,
            onJpgMaxToggle = {
                viewModel.setUseJpgMax(it)
            },
            useMultipleExposure = useMultipleExposure,
            onMultipleExposureToggle = { viewModel.setUseMultipleExposure(it) },
            onDismiss = { activePanel = ActivePanel.NONE },
            everydayRowOnTop = everydayRowShown && activePanel == ActivePanel.NONE,
            everydayRowOnTopModifier = Modifier.align(Alignment.TopCenter).padding(top = viewfinderTop),
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (viewModel.showPortraitIntro) {
            PortraitGuide(
                startStep = if (viewModel.portraitGuideAtBlur) PortraitGuideBlurStep else 0,
                quickBlur = portraitQuickBlur,
                onUseDepth = { viewModel.setPortraitQuickBlur(false) },
                onDismiss = viewModel::dismissPortraitIntro,
            )
        }

        if (viewModel.showProGuide) {
            ProGuide(onDismiss = viewModel::dismissProGuide)
        }

        if (viewModel.showBasicsGuide) {
            BasicsGuide(onDismiss = viewModel::dismissBasicsGuide)
        }

        // During navigation measurement or window resizing, the viewport can be
        // shorter than the top inset. The panel then has no available vertical space.
        val filterPanelTop = minOf(topInset, maxHeight)
        val fallbackFilterPanelBottom = when {
            isXpan -> maxHeight - 48.dp
            isVideoMode -> maxHeight - 170.dp
            else -> viewfinderTop + cardHeight - 48.dp
        }
        val filterPanelAnchorY = if (isXpan) {
            captureButtonBounds?.top
        } else {
            // Above the Looks button and the zoom collar beside it.
            filterButtonBounds?.top
        }
        val requestedFilterPanelBottom = filterPanelAnchorY?.let { anchorY ->
            val parentTop = cameraScreenBounds?.top ?: 0f
            with(density) { (anchorY - parentTop).toDp() - 8.dp }
        } ?: fallbackFilterPanelBottom
        val filterPanelBottom = requestedFilterPanelBottom.coerceIn(filterPanelTop, maxHeight)

        AnimatedVisibility(
            activePanel == ActivePanel.FILTERS,
            enter = if (isXpan) {slideInVertically(initialOffsetY = { it })} else fadeIn(),
            exit = if (isXpan) {slideOutVertically(targetOffsetY = { it })} else fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(filterPanelBottom)
                    .padding(top = filterPanelTop),
                contentAlignment = Alignment.BottomCenter
            ) {
                val allPresets by viewModel.allPresets.collectAsState()
                val defaultPresetName = stringResource(R.string.preset_new_preset_default)
                val isVideoLog = isVideoMode && state.videoConfig.logProfile.isEnabled

                // LUT 选择器 (内嵌 Presets 列表与统一控制，无多余背景遮挡)
                LutSelector(
                    availableLuts = viewModel.selectableLutList,
                    currentLutId = currentLutId,
                    thumbnail = viewModel.previewThumbnail,
                    onLutSelected = { viewModel.setLut(it) },
                    allPresets = allPresets,
                    presetModeEnabled = !isVideoLog,
                    activePresetId = activePresetId,
                    activePresetModified = activePresetModified,
                    selectedMode = if (isVideoLog) LutSelectorMode.Style else lutSelectorMode,
                    onModeSelected = { viewModel.setLutSelectorMode(it) },
                    availableFrames = viewModel.availableFrameList,
                    currentFrameId = viewModel.currentFrameId,
                    onFrameSelected = if (isVideoLog) null else viewModel::setFrame,
                    onFrameManagementClick = {
                        activePanel = ActivePanel.NONE
                        onFrameManagementClick()
                    },
                    onPresetSelected = { preset ->
                        discardTransientLookEdits()
                        if (presetRequiresPreviewTransition(preset)) {
                            runPreviewTransition { viewModel.applyPreset(preset) }
                        } else {
                            viewModel.applyPreset(preset)
                        }
                    },
                    onCreatePresetClick = {
                        viewModel.prepareCurrentSettingsPresetDraft(defaultPresetName)
                        onPresetEditClick(null)
                    },
                    onResetPresetClick = {
                        discardTransientLookEdits()
                        val preset = allPresets.firstOrNull { it.id == activePresetId }
                        if (presetRequiresPreviewTransition(preset)) {
                            runPreviewTransition { viewModel.resetActivePreset() }
                        } else {
                            viewModel.resetActivePreset()
                        }
                    },
                    onSavePresetClick = viewModel::saveCurrentSettingsToActivePreset,
                    onPresetManagementClick = onPresetManagementClick,
                    onEditClick = {
                        activePanel = ActivePanel.EDIT
                    },
                    onManageClick = { lutId ->
                        activePanel = ActivePanel.NONE
                        onFilterManagementClick(lutId)
                    },
                    categoryOrder = categoryOrder,
                    headerContent = if (isVideoLog) {
                        {
                            VideoLogLutModeSelector(
                                mode = state.videoConfig.logLutMode,
                                enabled = !state.videoRecordingState.isRecording &&
                                    !state.videoRecordingState.isProcessing,
                                onModeSelected = viewModel::setVideoLogLutMode
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 10.dp, end = 6.dp)
                )
            }
        }

        if (activePanel == ActivePanel.EDIT) {
            LutEditBottomSheet(
                lutId = currentLutId,
                initialParams = previewRecipeParamsOverride ?: currentRecipeParams,
                onParamsPreviewChange = { previewRecipeParamsOverride = it },
                showEffects = true,
                onDismiss = {
                    previewRecipeParamsOverride = null
                    activePanel = ActivePanel.FILTERS
                }
            )
        }

        captureAnimationSnapshot?.let { snapshot ->
            CaptureAnimationOverlay(
                snapshot = snapshot,
                modifier = Modifier.fillMaxSize(),
                onFinished = {
                    if (captureAnimationSnapshot?.id == snapshot.id) {
                        captureAnimationSnapshot = null
                    }
                }
            )
        }

    }
}

@Composable
fun MultipleExposureOverlay(
    state: com.hinnka.mycamera.viewmodel.MultipleExposureSessionState,
    onFinish: () -> Unit,
    onUndo: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(8.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .wrapContentWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xD90D1117),
                                Color(0xB8141B22)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Layers,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = "${state.capturedCount}/${state.targetCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = ViewfinderTextShadow
                    )
                )

                if (state.isSessionActive) {
                    IconButton(
                        onClick = onUndo,
                        enabled = state.capturedCount > 0 && !state.isProcessing,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            AppIcons.AutoMirroredOutlinedUndo,
                            contentDescription = stringResource(R.string.multiple_exposure_undo),
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onFinish,
                        enabled = state.canFinish,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.multiple_exposure_finish),
                            modifier = Modifier.size(16.dp),
                            tint = AccentColor
                        )
                    }

                    IconButton(
                        onClick = onCancel,
                        enabled = !state.isProcessing,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.multiple_exposure_cancel),
                            modifier = Modifier.size(16.dp),
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    state: CameraState,
    viewModel: CameraViewModel,
    galleryViewModel: GalleryViewModel,
    latestPhoto: com.hinnka.mycamera.gallery.MediaData?,
    useMultipleExposure: Boolean,
    multipleExposureState: com.hinnka.mycamera.viewmodel.MultipleExposureSessionState,
    onGalleryThumbnailBoundsChanged: (Rect) -> Unit,
    onCaptureButtonBoundsChanged: (Rect) -> Unit,
    onSwitchCameraClick: () -> Unit,
    onCaptureModeSelected: (CameraShootingMode) -> Unit,
    // The camera is not showing live frames yet or again: the shutter shows its busy look, as when it cannot shoot.
    previewPending: Boolean,
    onCaptureTap: () -> Unit,
    onGalleryClick: () -> Unit,
    // XPAN keeps its filter button on the side bar and has no Looks button or collar.
    isXpan: Boolean,
    onLooksClick: () -> Unit,
    onLooksBoundsChanged: (Rect) -> Unit,
    onQuickSettingsClick: () -> Unit,
    modeLine: ModeLineState,
    zoomCollar: (@Composable (fold: () -> Float) -> Unit)?,
    sideControlInset: Dp?,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val captureButtonStyle by preferenceValue { it.captureButtonStyle }
    val captureButtonColor by preferenceValue { it.captureButtonColor }
    val captureButtonImagePath by preferenceValue { it.captureButtonImagePath }
    val captureProcessingState by viewModel.captureProcessingState.collectAsState()
    val capturedThumbnail by viewModel.capturedThumbnail.collectAsState()
    // Same rule as the lilac refusal in Camera2Controller.reservePhotoCapture.
    val captureBusy = state.captureBusy

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CameraControlsLayoutDefaults.BottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shootingMode = when {
                state.captureMode == CaptureMode.VIDEO -> CameraShootingMode.VIDEO
                state.useRaw && state.isRawSupported -> CameraShootingMode.PROFESSIONAL
                state.isPortraitMode -> CameraShootingMode.PORTRAIT
                else -> CameraShootingMode.PHOTO
            }
            val line = modeLine
            if (!isXpan) Spacer(Modifier.height(CameraControlsLayoutDefaults.CollarRise))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CameraControlsLayoutDefaults.ModeZoneHeight),
                contentAlignment = Alignment.Center
            ) {
                // First, so the side controls drawn above it keep their own touches.
                ModeLine(
                    state = line,
                    selected = shootingMode,
                    proAvailable = state.isRawSupported,
                    // Free while a mode loads: a further choice waits for that load (see selectShootingMode).
                    enabled = !state.videoRecordingState.isRecording &&
                        !state.videoRecordingState.isProcessing,
                    recording = state.videoRecordingState.isRecording,
                    videoProcessing = state.videoRecordingState.isProcessing,
                    onSelect = onCaptureModeSelected,
                    modifier = Modifier.matchParentSize(),
                    collar = zoomCollar?.let { collar -> @Composable { collar { line.fold } } }
                ) { shutterModifier ->
                    // The face follows the mode under the shutter while the dial turns.
                    CaptureButton(
                        captureMode = if (line.shownMode == CameraShootingMode.VIDEO) CaptureMode.VIDEO else CaptureMode.PHOTO,
                        isProfessionalMode = line.shownMode == CameraShootingMode.PROFESSIONAL,
                        isCapturing = state.isCapturing,
                        isCaptureBusy = captureBusy,
                        isCountdownActive = state.countdownValue > 0,
                        photoCaptureProgress = state.photoCaptureProgress,
                        isBurstCapturing = state.burstCapturing,
                        isVideoRecording = state.videoRecordingState.isRecording,
                        isVideoProcessing = state.videoRecordingState.isProcessing,
                        isPaused = state.videoRecordingState.isPaused,
                        allowLongPress = state.captureMode == CaptureMode.PHOTO &&
                            !useMultipleExposure,
                        multipleExposureEnabled = useMultipleExposure && state.captureMode == CaptureMode.PHOTO,
                        multipleExposureProgress = multipleExposureState.capturedCount.toFloat() /
                                multipleExposureState.targetCount.coerceAtLeast(1).toFloat(),
                        customStyle = captureButtonStyle,
                        customColor = captureButtonColor,
                        customImagePath = captureButtonImagePath,
                        previewPending = previewPending,
                        // The XPAN picture runs behind the shutter.
                        seeThrough = isXpan,
                        onTap = onCaptureTap,
                        onLongPressStart = { viewModel.startContinuousCapture() },
                        onLongPressEnd = { viewModel.stopContinuousCapture() },
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                onCaptureButtonBoundsChanged(coordinates.boundsInRoot())
                            }
                            .then(shutterModifier),
                    )
                }

                val thumbnailPhotoInset = CameraControlsLayoutDefaults.LineWidth + CameraControlsLayoutDefaults.LineGap
                val thumbnailPhotoInsetPx = with(LocalDensity.current) { thumbnailPhotoInset.toPx() }
                val recording = state.videoRecordingState.isRecording
                // While recording, Pause takes the thumbnail's place and Snapshot the switch's.
                if (!recording) Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = sideControlInset ?: CameraControlsLayoutDefaults.SideMargin)
                        .onGloballyPositioned { coordinates ->
                            // The capture fly-in lands on the photo, inside the frame.
                            onGalleryThumbnailBoundsChanged(coordinates.boundsInRoot().deflate(thumbnailPhotoInsetPx))
                        }
                        // After the bounds above, so making room for the mode list does not move the fly-in target.
                        .graphicsLayer { makeRoom(line.side, -1f) }
                        .autoRotate()
                ) {
                    GalleryThumbnail(
                        latestPhoto = latestPhoto,
                        viewModel = galleryViewModel,
                        capturedThumbnail = capturedThumbnail,
                        onCapturedThumbnailLoaded = viewModel::acknowledgeCapturedThumbnail,
                        photoInset = thumbnailPhotoInset,
                        onClick = onGalleryClick
                    )
                    val queueStatus by RenderQueue.status.collectAsState()
                    val queuePending by RenderQueue.pendingPhotoIds.collectAsState()
                    val unfinishedPhotos by GalleryManager.processingPhotos.collectAsState()
                    val appContext = LocalContext.current.applicationContext
                    val queuePaused = remember(queueStatus, queuePending) { RenderQueue.isPaused(appContext) }
                    // Photos in the camera (Photo mode stacking, a Pro shot until its DNG) and in the RAW queue, each
                    // once; the capture count also covers a shot whose frames are still arriving, before it registers.
                    val unfinishedCount = maxOf(unfinishedPhotos.size, captureProcessingState.pendingCount)
                    val rawRendering = queueStatus.running && queueStatus.currentPhotoId != null && !queueStatus.waitingForMemory
                    val thumbnailProcessing = when {
                        captureProcessingState.pendingCount > 0 || rawRendering -> ThumbnailProcessing.RUNNING
                        unfinishedCount == 0 -> null
                        queuePaused -> ThumbnailProcessing.PAUSED
                        else -> ThumbnailProcessing.WAITING
                    }
                    key(capturedThumbnail?.captureId) {
                        GalleryProcessingOverlay(
                            processing = thumbnailProcessing,
                            unfinishedCount = unfinishedCount,
                            struggling = queueStatus.memoryKills >= RenderQueue.MEMORY_HINT_KILLS,
                            completedCount = captureProcessingState.completedCount,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                if (recording) {
                    FramedFooterButton(
                        onClick = {
                            if (state.videoRecordingState.isPaused) {
                                viewModel.resumeVideoRecording()
                            } else {
                                viewModel.pauseVideoRecording()
                            }
                        },
                        icon = if (state.videoRecordingState.isPaused) AppIcons.PlayLine else AppIcons.PauseLine,
                        contentDescription = stringResource(
                            if (state.videoRecordingState.isPaused) R.string.render_queue_resume else R.string.render_queue_pause
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = sideControlInset ?: CameraControlsLayoutDefaults.SideMargin)
                    )
                    FramedFooterButton(
                        onClick = { viewModel.captureVideoFrame() },
                        icon = AppIcons.CameraLine,
                        contentDescription = stringResource(R.string.take_photo),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = sideControlInset ?: CameraControlsLayoutDefaults.SideMargin)
                    )
                } else {
                    val switchSpin = remember { Animatable(0f) }
                    val spinScope = rememberCoroutineScope()
                    val switchContext = LocalContext.current
                    val vibration = remember(switchContext) { VibrationHelper(switchContext) }
                    val switchShape = RoundedCornerShape(ThumbnailCorner)
                    PhysicalButton(
                        onClick = {
                            // Taps during the spin are ignored, so one spin is one switch.
                            if (!state.videoRecordingState.isProcessing && !switchSpin.isRunning) {
                                onSwitchCameraClick()
                                spinScope.launch {
                                    switchSpin.animateTo(switchSpin.value + 180f, tween(560, easing = BackEasing))
                                    vibration.vibrate(VibrationHelper.LIGHT_TICK_MS)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = sideControlInset ?: CameraControlsLayoutDefaults.SideMargin)
                            .graphicsLayer { makeRoom(line.side, 1f) }
                            .size(CameraControlsLayoutDefaults.SideButtonSize)
                            .autoRotate()
                            .border(CameraControlsLayoutDefaults.LineWidth, CameraControlsLayoutDefaults.LineColor, switchShape),
                        shape = switchShape,
                        backgroundColor = Color.Transparent
                    ) {
                        Icon(
                            imageVector = AppIcons.TurningArrows,
                            contentDescription = stringResource(R.string.switch_camera),
                            tint = CameraControlsLayoutDefaults.LineColor.copy(
                                alpha = if (state.videoRecordingState.isProcessing) 0.35f else 1f
                            ),
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer { rotationZ = switchSpin.value }
                        )
                    }
                }

                // Quick settings mirror Looks on the left column; not while recording, as the settings can't change then.
                if (!state.videoRecordingState.isRecording && !state.videoRecordingState.isProcessing) {
                    PhysicalButton(
                        onClick = onQuickSettingsClick,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = sideControlInset ?: CameraControlsLayoutDefaults.SideMargin)
                            .offset(y = -LooksRise)
                            .size(CameraControlsLayoutDefaults.SideButtonSize)
                            .graphicsLayer {
                                translationY = -10.dp.toPx() * line.side
                                alpha = 1f - line.side
                            }
                            .autoRotate(),
                        shape = RoundedCornerShape(ThumbnailCorner),
                        backgroundColor = Color.Transparent
                    ) {
                        Icon(
                            imageVector = AppIcons.Sliders,
                            contentDescription = stringResource(R.string.quick_settings),
                            tint = CameraControlsLayoutDefaults.LineColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Looks can't change during a recording.
                if (!isXpan && !recording) {
                    PhysicalButton(
                        onClick = onLooksClick,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = CameraControlsLayoutDefaults.SideMargin)
                            // Level with the zoom collar's knob.
                            .offset(y = -LooksRise)
                            .size(CameraControlsLayoutDefaults.SideButtonSize)
                            // Before the lift, so the filter panel's anchor stays put.
                            .onGloballyPositioned { coordinates ->
                                onLooksBoundsChanged(coordinates.boundsInRoot())
                            }
                            .graphicsLayer {
                                translationY = -10.dp.toPx() * line.side
                                alpha = 1f - line.side
                            }
                            .autoRotate(),
                        shape = RoundedCornerShape(ThumbnailCorner),
                        backgroundColor = Color.Transparent
                    ) {
                        Icon(
                            imageVector = AppIcons.Film,
                            contentDescription = stringResource(R.string.filters_panel),
                            tint = CameraControlsLayoutDefaults.LineColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// The zoom collar's knob centre above the shutter centre: halfway through the 48 to 74 dp band.
private val LooksRise = 61.dp

/** While the mode list opens, a side control slides 44 dp outward, shrinks a little and fades. */
private fun GraphicsLayerScope.makeRoom(side: Float, direction: Float) {
    translationX = direction * 44.dp.toPx() * side
    scaleX = 1f - 0.12f * side
    scaleY = scaleX
    alpha = 1f - side
}

/** Fills [rect] with [image], cropped around its centre to the rectangle's shape. */
private fun DrawScope.drawCenterCropped(image: ImageBitmap, rect: Rect, alpha: Float = 1f) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val scale = maxOf(rect.width / image.width, rect.height / image.height)
    val srcWidth = (rect.width / scale).roundToInt().coerceIn(1, image.width)
    val srcHeight = (rect.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        alpha = alpha
    )
}

/** A footer button in the switch's 48 dp frame: a 2 dp outline, then a line icon. */
@Composable
private fun FramedFooterButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(ThumbnailCorner)
    PhysicalButton(
        onClick = onClick,
        modifier = modifier
            .size(CameraControlsLayoutDefaults.SideButtonSize)
            .autoRotate()
            .border(CameraControlsLayoutDefaults.LineWidth, CameraControlsLayoutDefaults.LineColor, shape),
        shape = shape,
        backgroundColor = Color.Transparent
    ) {
        Icon(icon, contentDescription = contentDescription, tint = CameraControlsLayoutDefaults.LineColor, modifier = Modifier.size(24.dp))
    }
}

// Back easing (c = 1.9): swings about 12 % past the target and settles.
internal val BackEasing = Easing { t -> val u = t - 1f; 1f + 2.9f * u * u * u + 1.9f * u * u }

/** Shared breathing for the waiting shutter ring and thumbnail edge; both start when waiting starts, so they stay in step. */
@Composable
private fun rememberRawBusyPulse(): Float =
    rememberInfiniteTransition(label = "rawBusyPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200), RepeatMode.Reverse),
        label = "rawBusyPulse"
    ).value

/**
 * What the camera thumbnail's edge says about unfinished photos, the same in every mode: something is
 * processing now (Photo mode at once, Pro after its hold), photos wait their turn, or the RAW queue is paused.
 */
private enum class ThumbnailProcessing { RUNNING, WAITING, PAUSED }

/** Keeps render progress independent of the shutter and the optional photo fly-in animation. */
@Composable
private fun GalleryProcessingOverlay(
    processing: ThumbnailProcessing?,
    unfinishedCount: Int,
    struggling: Boolean,
    completedCount: Long,
    modifier: Modifier = Modifier
) {
    val completionAlpha = remember { Animatable(0f) }
    var lastAcknowledgedCompletion by remember { mutableLongStateOf(completedCount) }
    var hadUnfinished by remember { mutableStateOf(unfinishedCount > 0) }
    val thumbnailShape = RoundedCornerShape(ThumbnailCorner)

    LaunchedEffect(unfinishedCount, completedCount) {
        if (unfinishedCount > 0) {
            hadUnfinished = true
            completionAlpha.snapTo(0f)
        } else if (hadUnfinished || completedCount != lastAcknowledgedCompletion) {
            // The last unfinished photo, Photo mode or RAW, is done.
            hadUnfinished = false
            lastAcknowledgedCompletion = completedCount
            // A neutral highlight acknowledges completion without implying success on failure.
            completionAlpha.snapTo(0f)
            completionAlpha.animateTo(0.75f, tween(durationMillis = 120))
            completionAlpha.animateTo(0f, tween(durationMillis = 700))
        }
    }

    // Amber once Android keeps stopping a RAW render; the gallery's chip and queue screen say what to do.
    val tint = if (struggling) Color(0xFFFFB300) else Color.White
    val description = processing?.let {
        stringResource(
            when (it) {
                ThumbnailProcessing.RUNNING -> R.string.thumbnail_processing_running
                ThumbnailProcessing.WAITING -> R.string.thumbnail_processing_waiting
                ThumbnailProcessing.PAUSED -> R.string.thumbnail_processing_paused
            },
            unfinishedCount.coerceAtLeast(1),
        )
    }
    Box(
        modifier = modifier.then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        // Idle frame line; the processing states below draw their edge in its place.
        if (processing == null) {
            Box(Modifier.matchParentSize().border(CameraControlsLayoutDefaults.LineWidth, CameraControlsLayoutDefaults.LineColor, thumbnailShape))
        }
        if (processing == ThumbnailProcessing.PAUSED) {
            // The one state that needs the user (Resume), so the only one that covers the photo.
            Box(Modifier.matchParentSize().clip(thumbnailShape).background(Color.Black.copy(alpha = 0.35f)))
            Canvas(Modifier.size(16.dp)) {
                val bar = size.width * 0.3f
                val corner = CornerRadius(bar / 2)
                drawRoundRect(Color.White, topLeft = Offset(size.width * 0.12f, 0f), size = androidx.compose.ui.geometry.Size(bar, size.height), cornerRadius = corner)
                drawRoundRect(Color.White, topLeft = Offset(size.width * 0.88f - bar, 0f), size = androidx.compose.ui.geometry.Size(bar, size.height), cornerRadius = corner)
            }
        }
        if (processing != null) {
            ThumbnailEdgeRing(
                style = if (processing == ThumbnailProcessing.RUNNING) EdgeRingStyle.RUNNING else EdgeRingStyle.DOTTED,
                color = tint,
                modifier = Modifier.matchParentSize()
            )
        }
        // One count for all unfinished photos, Photo mode and RAW together; a single one shows by the edge alone.
        if (unfinishedCount >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    // At least as wide as tall: a round badge for one digit, a pill beyond.
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unfinishedCount.toString(),
                    color = Color(0xFF2C2C2A),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                )
            }
        }
        // Done: the same edge line flashes once and fades, so finishing reads as the end of the running arc.
        if (completionAlpha.value > 0f) {
            ThumbnailEdgeRing(
                style = EdgeRingStyle.SOLID,
                color = Color.White,
                alpha = { completionAlpha.value },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}


/**
 * 拍照按钮
 */
@Composable
fun CaptureButton(
    captureMode: CaptureMode,
    isProfessionalMode: Boolean,
    isCapturing: Boolean,
    isCaptureBusy: Boolean = false,
    isCountdownActive: Boolean = false,
    photoCaptureProgress: PhotoCaptureProgress?,
    isBurstCapturing: Boolean,
    isVideoRecording: Boolean,
    isVideoProcessing: Boolean,
    isPaused: Boolean,
    allowLongPress: Boolean,
    multipleExposureEnabled: Boolean,
    multipleExposureProgress: Float,
    customStyle: CaptureButtonStyle,
    customColor: Int,
    customImagePath: String?,
    previewPending: Boolean = false,
    // Over a picture that runs behind the shutter: no dark disc, and a see-through face inside the full ring.
    seeThrough: Boolean = false,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressColor = AccentColor
    val showMultipleExposureProgress = captureMode == CaptureMode.PHOTO && multipleExposureEnabled
    val showCaptureProgress = captureMode == CaptureMode.PHOTO && isCapturing
    val showVideoProcessingProgress = captureMode == CaptureMode.VIDEO && isVideoProcessing
    // While a shot would be refused (Pro: RAW still saving; Photo: buffers full): grey centre and a breathing
    // white ring (capture uses a spinning accent ring); a small pop and a light tick when it is usable again.
    // Every ring takes the place of the outline, at its width, one at a time.
    val showBusy = (captureMode == CaptureMode.PHOTO && isCaptureBusy && !isCapturing) || previewPending
    // During the self-timer a press cancels it, so the centre shows a cross instead of a second shot.
    val showCancelTimer = captureMode == CaptureMode.PHOTO && isCountdownActive && !isCapturing
    val showActivityProgress = showCaptureProgress || showVideoProcessingProgress
    val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
    var isPressed by remember { mutableStateOf(false) }
    val customImage = remember(customImagePath) {
        customImagePath
            ?.let(::File)
            ?.takeIf(File::isFile)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isCapturing || isVideoRecording || isVideoProcessing) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "captureScale"
    )
    // Opaque neutral grey on top of the centre: a dark tint over the yellow would read as muddy olive.
    // Not while the camera reopens after a mode switch: the face keeps the new mode's colour and the ring breathes.
    val busyTint by animateFloatAsState(
        targetValue = if ((showBusy && !previewPending) || showCancelTimer) 1f else 0f,
        animationSpec = tween(250),
        label = "captureBusyTint"
    )
    val context = LocalContext.current
    val vibration = remember(context) { VibrationHelper(context) }
    val readyPop = remember { Animatable(1f) }
    var wasBusy by remember { mutableStateOf(false) }
    // Tick only for someone who pressed while the shutter was busy: after every mode switch it would
    // mostly point out how long the switch took. The pop still shows readiness.
    var pressedWhileBusy by remember { mutableStateOf(false) }
    val currentShowBusy by rememberUpdatedState(showBusy)
    LaunchedEffect(showBusy) {
        if (wasBusy && !showBusy) {
            if (pressedWhileBusy) {
                // A short pulse: the XZ1 Compact's vibrator has no strength control, plays VirtualKey as a
                // 50 ms buzz and gives no feelable tick for TICK effects.
                vibration.vibrate(VibrationHelper.LIGHT_TICK_MS)
            }
            pressedWhileBusy = false
            readyPop.animateTo(1.04f, tween(110))
            readyPop.animateTo(1f, spring(dampingRatio = 0.6f))
        }
        wasBusy = showBusy
    }

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "videoPulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500)),
        label = "captureProgressRotation"
    )

    val captureProgress = rememberPhotoCaptureProgress(
        photoCaptureProgress.takeIf { isCapturing && captureMode == CaptureMode.PHOTO }
    )

    val currentDisabled by rememberUpdatedState(
        isCapturing ||
            isVideoProcessing ||
            (captureMode == CaptureMode.VIDEO && isVideoRecording)
    )

    PhysicalButton(
        modifier = modifier
            .size(CameraControlsLayoutDefaults.CaptureButtonSize)
            .scale(scale * readyPop.value)
            .pointerInput(allowLongPress) {
                var isLongPressStarted = false
                detectTapGestures(
                    onTap = {
                        if (!isCapturing && !isVideoProcessing) {
                            onTap()
                        }
                    },
                    onLongPress = if (allowLongPress) {
                        {
                            if (!currentDisabled) {
                                isLongPressStarted = true
                                onLongPressStart()
                            }
                        }
                    } else null,
                    onPress = {
                        isPressed = true
                        isLongPressStarted = false
                        if (currentShowBusy) pressedWhileBusy = true
                        try {
                            // A swipe on the mode dial took the press: no ready tick for it later.
                            if (!tryAwaitRelease()) pressedWhileBusy = false
                        } finally {
                            isPressed = false
                            if (isLongPressStarted) {
                                onLongPressEnd()
                            }
                        }
                    }
                )
            },
        shape = CircleShape,
        backgroundColor = if (seeThrough) Color.Transparent else PhysicalButtonDefaults.BackgroundColor,
        contentAlignment = Alignment.Center
    ) {
        val ringColor = when {
            captureMode == CaptureMode.VIDEO && isVideoProcessing ->
                Color.White.copy(alpha = 0.5f)
            captureMode == CaptureMode.VIDEO && isVideoRecording -> {
                if (isPaused) Color.White.copy(alpha = 0.8f)
                else Color.Red.copy(alpha = pulseAlpha)
            }
            else -> CameraControlsLayoutDefaults.LineColor
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = CameraControlsLayoutDefaults.LineWidth,
                    color = ringColor,
                    shape = CircleShape
                )
        )

        // Center shutter surface
        val centerPadding by animateDpAsState(
            targetValue = when {
                captureMode == CaptureMode.VIDEO && isVideoRecording -> 21.dp
                else -> CameraControlsLayoutDefaults.LineWidth + CameraControlsLayoutDefaults.LineGap
            },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
            label = "centerPadding"
        )
        val centerCorner by animateDpAsState(
            targetValue = if (captureMode == CaptureMode.VIDEO && isVideoRecording) 8.dp else 36.dp,
            label = "centerCorner"
        )
        val defaultCenterBackground = when {
            captureMode == CaptureMode.VIDEO -> {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6668),
                            Color(0xFFE4383C)
                        )
                    )
                )
            }
            isProfessionalMode -> Modifier.background(AccentColor)
            else -> {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFD7D7D7)
                        )
                    )
                )
            }
        }
        val useCustomAppearance = captureMode != CaptureMode.VIDEO
        val centerBackgroundModifier = when {
            useCustomAppearance && customStyle == CaptureButtonStyle.COLOR ->
                Modifier.background(Color(customColor))
            else -> defaultCenterBackground
        }

        Box(
            modifier = Modifier
                .padding(centerPadding)
                .fillMaxSize()
                .graphicsLayer { if (seeThrough) alpha = SeeThroughFaceAlpha }
                .clip(RoundedCornerShape(centerCorner))
                .then(centerBackgroundModifier)
        ) {
            if (
                useCustomAppearance &&
                customStyle == CaptureButtonStyle.IMAGE &&
                customImage != null
            ) {
                AsyncImage(
                    model = customImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .autoRotate(matchParentSize = true)
                )
            }
            if (busyTint > 0f) {
                Box(Modifier.fillMaxSize().background(Color(0xFF3C3C3C).copy(alpha = busyTint)))
            }
        }
        if (showCancelTimer) {
            Icon(
                AppIcons.CloseLine,
                contentDescription = stringResource(R.string.capture_cancel_timer),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        if (showBusy && !showActivityProgress) {
            CaptureProgressRing(
                color = Color.White.copy(alpha = rememberRawBusyPulse()),
                startAngle = -90f,
                sweepAngle = 360f,
                modifier = Modifier.progressSemantics()
            )
        }
        if (showMultipleExposureProgress && !showActivityProgress && !showBusy) {
            CaptureProgressRing(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * multipleExposureProgress.coerceIn(0f, 1f)
            )
        }
        if (showActivityProgress) {
            val isIndeterminate = isBurstCapturing || showVideoProcessingProgress
            CaptureProgressRing(
                color = progressColor,
                startAngle = if (isIndeterminate) rotation - 90f else -90f,
                sweepAngle = if (isIndeterminate) 90f else 360f * captureProgress.value,
                modifier = if (isIndeterminate) Modifier.progressSemantics()
                    else Modifier.progressSemantics(captureProgress.value)
            )
        }
    }
}

// The shutter face over a picture: enough to read as the shutter and show its mode colour and busy grey.
private const val SeeThroughFaceAlpha = 0.3f

@Composable
private fun rememberPhotoCaptureProgress(progress: PhotoCaptureProgress?): State<Float> {
    val fraction = remember(progress) {
        mutableFloatStateOf(progress?.fractionAt(SystemClock.elapsedRealtimeNanos()) ?: 0f)
    }
    LaunchedEffect(progress) {
        if (progress == null) return@LaunchedEffect
        while (isActive && fraction.floatValue < 1f) {
            withFrameNanos {
                fraction.floatValue = progress.fractionAt(SystemClock.elapsedRealtimeNanos())
            }
        }
    }
    return fraction
}

private fun cycleVideoResolution(state: CameraState): VideoResolutionPreset? {
    return nextOption(state.videoConfig.resolution, state.videoCapabilities.availableResolutions)
}

private fun cycleVideoFps(state: CameraState): VideoFpsPreset? {
    return nextOption(state.videoConfig.fps, state.videoCapabilities.availableFps)
}

private fun cycleVideoAspectRatio(state: CameraState): VideoAspectRatio? {
    return nextOption(state.videoConfig.aspectRatio, state.videoCapabilities.availableAspectRatios)
}

private fun cycleVideoLogProfile(state: CameraState): VideoLogProfile? {
    return nextOption(state.videoConfig.logProfile, state.videoCapabilities.availableLogProfiles)
}

private fun <T> nextOption(current: T, options: List<T>): T? {
    if (options.isEmpty()) return null
    val currentIndex = options.indexOf(current)
    return if (currentIndex == -1) {
        options.firstOrNull()
    } else {
        options[(currentIndex + 1) % options.size]
    }
}


fun Modifier.autoRotate(
    dx: Dp = 0.dp,
    dy: Dp = 0.dp,
    matchParentSize: Boolean = false
): Modifier = composed {
    val rotationDegrees = OrientationObserver.uiRotationDegrees
    val targetDegrees = if (rotationDegrees != 0f) rotationDegrees - 180 else 0f

    val animatedDegrees by animateFloatAsState(
        targetValue = targetDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "rotationAnimation"
    )

    layout { measurable, constraints ->
        val rad = Math.toRadians(animatedDegrees.toDouble())
        val cos = abs(cos(rad))
        val sin = abs(sin(rad))

        // 核心优化：匹配父布局大小时，如果旋转接近 90 度，交换约束，
        // 从而让子组件（如 AsyncImage）按旋转后的方向进行测量，实现“铺满”效果。
        val modifiedConstraints = if (matchParentSize && sin > 0.5f) {
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        } else {
            constraints
        }

        val placeable = measurable.measure(modifiedConstraints)
        val width = placeable.width
        val height = placeable.height

        if (matchParentSize) {
            val visualWidth = width * cos + height * sin
            val visualHeight = width * sin + height * cos

            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            // 计算缩放：确保即使在动画中，内容也能刚好填满或不超出边界
            val scale = min(
                if (visualWidth > 0) containerWidth / visualWidth else 1.0,
                if (visualHeight > 0) containerHeight / visualHeight else 1.0
            ).toFloat().coerceAtMost(1.0f)

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeRelativeWithLayer(
                    (constraints.maxWidth - width) / 2,
                    (constraints.maxHeight - height) / 2
                ) {
                    rotationZ = animatedDegrees
                    scaleX = scale
                    scaleY = scale
                }
            }
        } else {
            val newWidth = (placeable.width * cos + placeable.height * sin).toInt()
            val newHeight = (placeable.width * sin + placeable.height * cos).toInt()

            val nDx = dx.toPx() * sin
            val nDy = dy.toPx() * sin

            layout(newWidth, newHeight) {
                placeable.placeRelativeWithLayer(
                    x = (newWidth - placeable.width) / 2 + nDx.toInt(),
                    y = (newHeight - placeable.height) / 2 + nDy.toInt()
                ) {
                    rotationZ = animatedDegrees
                }
            }
        }
    }
}

@Composable
private fun CornerOverlay(
    modifier: Modifier = Modifier,
    radius: Dp,
    color: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val frame = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
        }
        val roundedContent = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
                )
            )
        }
        frame.op(frame, roundedContent, PathOperation.Difference)
        drawPath(frame, color)
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

/** Explains a tap on the greyed Pro button; on the lilac back camera it names where RAW comes from, so users of other ROMs can find it. */
@Composable
private fun ProModeUnavailableDialog(isFrontCamera: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The shim only serves the back camera; the front camera never has RAW, so it gets the plain message.
    val showShimInfo = DeviceQuirks.isLilac && !isFrontCamera
    val paragraphs = when {
        isFrontCamera -> listOf(R.string.pro_unavailable_needs_raw_front, R.string.pro_unavailable_use_back_camera)
        showShimInfo -> listOf(R.string.pro_unavailable_needs_raw_system, R.string.pro_unavailable_lilac_shim, R.string.pro_unavailable_photo_mode)
        else -> listOf(R.string.pro_unavailable_needs_raw_camera, R.string.pro_unavailable_photo_mode)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.pro_unavailable_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                paragraphs.forEach {
                    Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = if (showShimInfo) {
            {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, LILAC_ROM_THREAD_URL.toUri()))
                    }.onFailure { PLog.w("CameraScreen", "No app to open $LILAC_ROM_THREAD_URL", it) }
                }) {
                    Text(stringResource(R.string.pro_unavailable_open_xda))
                }
            }
        } else null,
    )
}

private const val LILAC_ROM_THREAD_URL =
    "https://xdaforums.com/t/rom-a16-lineageos-23-2-unofficial-lilac-stable.4797464/"
