package com.hinnka.mycamera.ui.camera

import kotlinx.coroutines.delay
import com.hinnka.mycamera.raw.fotufilm.FotufilmDocument
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.R
import com.hinnka.mycamera.processor.BokehStyle
import com.hinnka.mycamera.ui.settings.SettingsTarget
import com.hinnka.mycamera.camera.AspectRatio
import com.hinnka.mycamera.camera.CameraInfo
import com.hinnka.mycamera.camera.LensType
import com.hinnka.mycamera.camera.MultiFrameConfig
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalViewConfiguration
import com.hinnka.mycamera.camera.MeteringMode
import com.hinnka.mycamera.raw.DcpInfo
import com.hinnka.mycamera.raw.HncsFilmCurveMode
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.raw.RawToneMappingParameters
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.ui.components.AppModalBottomSheet
import com.hinnka.mycamera.ui.components.RawDcpLensOption
import com.hinnka.mycamera.ui.components.RawRenderingEngineSettingsPanel
import com.hinnka.mycamera.ui.components.rawColorEngineDescription
import com.hinnka.mycamera.ui.components.rawRenderingEngineName
import com.hinnka.mycamera.video.*
import com.hinnka.mycamera.video.VideoCodec
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.components.ChosenFill
import com.hinnka.mycamera.ui.components.LilacChip
import com.hinnka.mycamera.ui.components.LilacDim
import com.hinnka.mycamera.ui.components.LilacLabel
import com.hinnka.mycamera.ui.components.LilacSecondary
import com.hinnka.mycamera.ui.components.LilacText
import com.hinnka.mycamera.ui.components.LilacTextStyle
import com.hinnka.mycamera.ui.components.SonyDim
import com.hinnka.mycamera.ui.components.SonyDivider
import com.hinnka.mycamera.ui.components.SonySecondary
import com.hinnka.mycamera.ui.components.SonyText
import com.hinnka.mycamera.ui.components.SonyTrack
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale
import com.hinnka.mycamera.raw.RawSceneExposureMath

// Quick settings sheet: a row of everyday settings floating above a translucent list in outlined groups.
internal val SheetEasing = CubicBezierEasing(0.3f, 1.35f, 0.5f, 1f)
internal val SheetText = LilacText
internal val SheetLabel = LilacLabel
internal val SheetDim = LilacSecondary
internal val SheetGrey = LilacDim
// The quick settings and Looks panels: black over the picture, so they meet the phone's black bezel without an edge.
internal val SheetPanel = Color.Black.copy(alpha = 0.82f)
internal val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val SheetTextStyle = LilacTextStyle

/** One camera the Lens row can switch to: a second camera at the same zoom, or a macro camera. */
internal data class QuickLensChoice(val cameraId: String, val label: String)

/**
 * Lens choices for phones with several cameras at one zoom or a macro camera; empty otherwise,
 * so the row never shows on a phone without them.
 */
internal fun quickLensChoices(cameras: List<CameraInfo>, current: CameraInfo?): List<QuickLensChoice> {
    val front = current?.lensType == LensType.FRONT
    val zoomable = cameras
        .filter { it.displayIntrinsicZoomRatio > 0f }
        .filter { if (front) it.lensType == LensType.FRONT else it.lensType != LensType.FRONT && it.lensType != LensType.BACK_MACRO }
        .sortedWith(compareBy<CameraInfo>({ it.displayIntrinsicZoomRatio }, { it.cameraId }))
    val variants = mutableListOf<QuickLensChoice>()
    val groups = mutableListOf<MutableList<CameraInfo>>()
    zoomable.forEach { camera ->
        val group = groups.lastOrNull()
        if (group != null && abs(group.first().displayIntrinsicZoomRatio - camera.displayIntrinsicZoomRatio) <= 0.01f) {
            group += camera
        } else {
            groups += mutableListOf(camera)
        }
    }
    groups.filter { it.size > 1 }.forEach { group ->
        group.forEachIndexed { index, camera ->
            variants += QuickLensChoice(camera.cameraId, "${formatZoomRatioLabel(camera.displayIntrinsicZoomRatio)} ${index + 1}")
        }
    }
    val macros = if (front) emptyList() else cameras.filter { it.lensType == LensType.BACK_MACRO }
    macros.forEachIndexed { index, camera ->
        variants += QuickLensChoice(camera.cameraId, if (macros.size > 1) "M${index + 1}" else "")
    }
    return variants
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CameraTopSheet(
    visible: Boolean,
    captureMode: CaptureMode,
    // Everyday row
    flashMode: Int,
    onFlashToggle: () -> Unit,
    timerSeconds: Int,
    onTimerToggle: () -> Unit,
    smileThreshold: Int,
    onSmileToggle: () -> Unit,
    useLivePhoto: Boolean,
    onLivePhotoToggle: () -> Unit,
    aspectRatio: AspectRatio,
    topSheetAspectRatios: List<AspectRatio>,
    onAspectRatioChange: (AspectRatio) -> Unit,
    videoConfig: VideoConfig,
    videoCapabilities: VideoCapabilities,
    onVideoResolutionClick: () -> Unit,
    onVideoFpsClick: () -> Unit,
    onVideoTorchToggle: () -> Unit,
    onVideoStabilizationModeChange: (VideoStabilizationMode) -> Unit,
    slowMotionAvailable: Boolean,
    slowMotionEnabled: Boolean,
    onSlowMotionToggle: () -> Unit,
    // List
    showHistogram: Boolean,
    onHistogramChange: (Boolean) -> Unit,
    videoAspectRatio: VideoAspectRatio,
    onVideoAspectRatioChange: (VideoAspectRatio) -> Unit,
    videoLogProfile: VideoLogProfile,
    availableVideoLogProfiles: List<VideoLogProfile>,
    onVideoLogProfileChange: (VideoLogProfile) -> Unit,
    videoBitrate: VideoBitratePreset,
    onVideoBitrateChange: (VideoBitratePreset) -> Unit,
    videoCodec: VideoCodec,
    onVideoCodecChange: (VideoCodec) -> Unit,
    videoAudioInputId: String,
    videoAudioInputOptions: List<VideoAudioInputOption>,
    onVideoAudioInputChange: (String) -> Unit,
    useRaw: Boolean,
    isRawSupported: Boolean,
    rawDcpId: String?,
    rawDcpIdsByLens: Map<String, String?> = emptyMap(),
    rawDcpLensOptions: List<RawDcpLensOption> = emptyList(),
    availableDcps: List<DcpInfo>,
    rawHncsFilmCurveMode: HncsFilmCurveMode,
    rawRenderingEngine: RawRenderingEngine,
    rawToneMappingParameters: RawToneMappingParameters,
    rawSpectralFilmSelection: SpectralFilmSelection?,
    rawSpectralFilmPrint: String?,
    rawFotufilmDocument: FotufilmDocument?,
    ultraHdrEnabled: Boolean,
    onUltraHdrToggle: (Boolean) -> Unit,
    isFrontCamera: Boolean,
    photoResolutionCapMp: Int,
    frontPhotoResolutionCapMp: Int,
    photoResolutionAdjustable: Boolean,
    onPhotoResolutionCapChange: (Int) -> Unit,
    onFrontPhotoResolutionCapChange: (Int) -> Unit,
    frontAccurateColour: Boolean,
    onFrontAccurateColourChange: (Boolean) -> Unit,
    saveCameraJpeg: Boolean,
    onSaveCameraJpegChange: (Boolean) -> Unit,
    bracketing: Boolean,
    onBracketingChange: (Boolean) -> Unit,
    bracketBlockedBySize: Boolean,
    isPortraitMode: Boolean = false,
    isNightMode: Boolean = false,
    portraitQuickBlur: Boolean = true,
    onPortraitQuickBlurChange: (Boolean) -> Unit = {},
    portraitBokehStyle: BokehStyle = BokehStyle.NATURAL,
    onPortraitBokehStyleChange: (BokehStyle) -> Unit = {},
    onPortraitHelp: () -> Unit = {},
    // Opens the Pro guide from the mode name.
    onProHelp: () -> Unit = {},
    // Opens the camera basics guide from the Photo mode name.
    onBasicsHelp: () -> Unit = {},
    // Long press on a setting's name opens Settings at that setting.
    onOpenSettingsAt: (SettingsTarget) -> Unit = {},
    multiFrameCount: Int,
    // Choosing a frame count also switches multi-frame on.
    onMultiFrameCountChange: (Int) -> Unit,
    hdrPlusFrameCount: Int,
    hdrPlusMinFrames: Int,
    onHdrPlusFrameCountChange: (Int) -> Unit,
    hdrRatioLimit: Float,
    onHdrRatioLimitChange: (Float) -> Unit,
    onRawDcpChange: (String?) -> Unit,
    onRawDcpIdsByLensChange: ((Map<String, String?>) -> Unit)? = null,
    onRawHncsFilmCurveModeChange: (HncsFilmCurveMode) -> Unit,
    onImportRawDcp: () -> Unit,
    onDeleteRawDcp: (DcpInfo) -> Unit,
    onRawColorEngineChange: (RawRenderingEngine) -> Unit,
    onRawToneMappingParametersChange: (RawToneMappingParameters) -> Unit,
    onRawSpectralFilmSelectionChange: (SpectralFilmSelection?) -> Unit,
    onRawSpectralFilmPrintChange: (String?) -> Unit,
    onRawFotufilmDocumentChange: (FotufilmDocument) -> Unit,
    meteringMode: MeteringMode,
    onMeteringModeChange: (MeteringMode) -> Unit,
    lensChoices: List<QuickLensChoice>,
    currentCameraId: String?,
    onLensSelect: (String) -> Unit,
    onFilterManageClick: () -> Unit,
    onFrameManageClick: () -> Unit,
    onPresetManageClick: () -> Unit,
    onToolboxClick: () -> Unit,
    onMoreSettingsClick: () -> Unit,
    useJpgMax: Boolean,
    onJpgMaxToggle: (Boolean) -> Unit,
    useMultipleExposure: Boolean,
    onMultipleExposureToggle: (Boolean) -> Unit,
    multipleExposureCount: Int,
    onMultipleExposureCountChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    // The everyday row on its own on the picture's top edge, dropped there by a swipe down.
    everydayRowOnTop: Boolean = false,
    everydayRowOnTopModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var showRenderingEngineSheet by rememberSaveable { mutableStateOf(false) }
    var showContentManagementOptions by rememberSaveable { mutableStateOf(false) }
    // One explanation and one inline picker (Log, Microphone) open at a time.
    var explained by remember { mutableStateOf<String?>(null) }
    var openPicker by remember { mutableStateOf<String?>(null) }
    // Shooting (0) or Output (1); opens on Shooting and keeps the last pick while the camera screen lives.
    var qTab by remember { mutableIntStateOf(0) }
    fun handleContentManagementAction(action: () -> Unit) {
        showContentManagementOptions = false
        action()
    }

    LaunchedEffect(visible, captureMode) {
        showContentManagementOptions = false
        explained = null
        openPicker = null
    }

    LaunchedEffect(useRaw) {
        if (!useRaw) {
            showRenderingEngineSheet = false
        }
    }

    // Each aspect change rebuilds the camera session, so taps only move the label; the last one
    // applies once the taps pause. Kept outside the sheet so closing it still applies the choice.
    var pendingAspect by remember { mutableStateOf<AspectRatio?>(null) }
    LaunchedEffect(pendingAspect) {
        val target = pendingAspect ?: return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        if (target != aspectRatio) onAspectRatioChange(target)
        // If the camera refuses the ratio, the label falls back to the real one.
        kotlinx.coroutines.delay(2000)
        pendingAspect = null
    }
    LaunchedEffect(aspectRatio) {
        if (aspectRatio == pendingAspect) pendingAspect = null
    }
    val shownAspect = pendingAspect ?: aspectRatio

    val riseOffset = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.roundToPx() }
    val isProfessionalMode = captureMode == CaptureMode.PHOTO && useRaw && isRawSupported
    val isVideo = captureMode == CaptureMode.VIDEO

    // The everyday settings: a tap steps to the next value. Shown in the sheet and on the top edge.
    val everydayItems: @Composable RowScope.() -> Unit = {
        val itemModifier = Modifier.weight(1f)
        if (isVideo) {
            VideoEverydayItems(
                videoConfig = videoConfig,
                videoCapabilities = videoCapabilities,
                onResolutionClick = onVideoResolutionClick,
                onFpsClick = onVideoFpsClick,
                onTorchToggle = onVideoTorchToggle,
                onStabilizationModeChange = onVideoStabilizationModeChange,
                slowMotionAvailable = slowMotionAvailable,
                slowMotionEnabled = slowMotionEnabled,
                onSlowMotionToggle = onSlowMotionToggle,
                itemModifier = itemModifier
            )
        } else {
            PhotoEverydayItems(
                useLivePhoto = useLivePhoto,
                onLivePhotoToggle = onLivePhotoToggle,
                timerSeconds = timerSeconds,
                onTimerToggle = onTimerToggle,
                smileThreshold = smileThreshold,
                onSmileToggle = onSmileToggle,
                aspectRatio = shownAspect,
                onAspectClick = {
                    val ratios = AspectRatio.sanitizeTopSheetRatios(topSheetAspectRatios)
                    pendingAspect = ratios[(ratios.indexOf(shownAspect) + 1).mod(ratios.size)]
                },
                // The front camera flashes with the screen: off or on, a torch left from the back camera counts as off.
                flashMode = if (isFrontCamera && flashMode != 1) 0 else flashMode,
                // Refreshed from the open camera's data in Photo mode too.
                flashAvailable = videoCapabilities.supportsTorch || isFrontCamera,
                onFlashToggle = onFlashToggle,
                itemModifier = itemModifier
            )
        }
    }

    // Swipe down on the picture: the everyday row drops onto its top edge over a light scrim.
    val dropOffset = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.roundToPx() }
    AnimatedVisibility(
        visible = everydayRowOnTop,
        enter = slideInVertically(tween(260, easing = SheetEasing)) { -dropOffset } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(180)) { -dropOffset } + fadeOut(tween(150)),
        modifier = everydayRowOnTopModifier
    ) {
        CompositionLocalProvider(LocalEverydayTurns provides false) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 14.dp),
                content = everydayItems
            )
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(260, easing = SheetEasing)) { riseOffset } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(200)) { riseOffset } + fadeOut(tween(160)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Drag down closes; the handler also keeps touches on empty parts of the sheet from reaching the footer.
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = { if (dragged > 50.dp.toPx()) onDismiss() },
                        onVerticalDrag = { change, amount ->
                            dragged += amount
                            change.consume()
                        }
                    )
                }
        ) {
            // Everyday row: floats on the picture above the list.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 6.dp),
                content = everydayItems
            )
            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // shortcut: plain black, no blur (Compose can't blur a GL preview surface behind it); add a GL blur pass if the plain sheet hides too much.
                    .background(SheetPanel, SheetShape)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                // The front camera has few rows that apply: one page, no tabs.
                val tabbed = isVideo || !isFrontCamera
                SheetTabRow(
                    tabbed = tabbed,
                    tab = qTab,
                    onTab = { qTab = it },
                    onToolboxClick = onToolboxClick,
                    onContentManagementClick = { showContentManagementOptions = !showContentManagementOptions },
                    onSettingsClick = onMoreSettingsClick
                )
                Box(Modifier.padding(horizontal = 10.dp)) {
                    ContentManagementOptionsPanel(
                        visible = showContentManagementOptions,
                        onFilterManageClick = { handleContentManagementAction(onFilterManageClick) },
                        onFrameManageClick = { handleContentManagementAction(onFrameManageClick) },
                        onPresetManageClick = { handleContentManagementAction(onPresetManageClick) }
                    )
                }
                if (showContentManagementOptions) Spacer(Modifier.height(8.dp))

                val rows = QuickRows(
                    explained = explained,
                    onExplain = { key -> explained = if (explained == key) null else key },
                    onOpenSettingsAt = onOpenSettingsAt
                )

                // Group bodies; each page decides the titles, since a page with one group shows none.
                val lensGroup: @Composable (first: Boolean) -> Unit = { first ->
                    if (lensChoices.isNotEmpty()) {
                        QuickGroup(stringResource(R.string.quick_lens), GroupRed, divider = !first) {
                            rows.Setting(
                                key = "lens",
                                label = stringResource(R.string.quick_lens),
                                explanation = stringResource(R.string.quick_explain_lens),
                                last = true
                            ) {
                                // shortcut: one line of choices, so about six lenses fit; wrap them if a phone offers more.
                                lensChoices.forEach { choice ->
                                    QuickChoice(
                                        text = choice.label.ifEmpty { stringResource(R.string.quick_lens_macro) },
                                        selected = choice.cameraId == currentCameraId,
                                        minWidth = 42.dp,
                                        onClick = { onLensSelect(choice.cameraId) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Multi-frame and multi-exposure exclude each other: the one given way slides to Off as soon as
                // the other is moved, not only once the camera has switched.
                var stackPick by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(useJpgMax, useMultipleExposure) {
                    if (stackPick == "mf" && !useMultipleExposure || stackPick == "mexp" && !useJpgMax) stackPick = null
                }
                val stackGroup: @Composable (first: Boolean, onGuide: (() -> Unit)?) -> Unit = { first, onGuide ->
                    QuickGroup(stringResource(R.string.quick_group_stacking), GroupRed, onGuide = onGuide, divider = !first) {
                        rows.Setting(
                            key = "mf",
                            label = stringResource(R.string.quick_multi_frame),
                            explanation = stringResource(R.string.settings_multi_frame_denoise_frame_count_description),
                            settingsTarget = SettingsTarget.MULTI_FRAME
                        ) {
                            FrameCountSlider(
                                frames = if (useJpgMax && stackPick != "mexp") multiFrameCount else 0,
                                min = MultiFrameConfig.MIN_DENOISE_FRAME_COUNT,
                                withOff = true,
                                onFramesChange = { frames ->
                                    if (frames == 0) {
                                        onJpgMaxToggle(false)
                                    } else {
                                        stackPick = "mf"
                                        onMultiFrameCountChange(frames)
                                    }
                                }
                            )
                        }
                        // The front camera cannot bracket: it has no manual exposure.
                        if (!isFrontCamera) {
                            rows.Setting(
                                key = "bracket",
                                label = stringResource(R.string.quick_bracketing),
                                explanation = stringResource(R.string.quick_explain_bracketing)
                            ) {
                                when {
                                    !useJpgMax -> QuickFixed(stringResource(R.string.quick_bracketing_needs_multi_frame))
                                    bracketBlockedBySize -> QuickFixed(stringResource(R.string.quick_bracketing_needs_12mp))
                                    else -> QuickSwitch(bracketing) { onBracketingChange(!bracketing) }
                                }
                            }
                        }
                        rows.Setting(
                            key = "mexp",
                            label = stringResource(R.string.quick_multi_exposure),
                            explanation = stringResource(R.string.quick_explain_multiple_exposure),
                            settingsTarget = SettingsTarget.MULTIPLE_EXPOSURE,
                            last = true
                        ) {
                            // Shots per picture, or Off.
                            FrameCountSlider(
                                frames = if (useMultipleExposure && stackPick != "mf") multipleExposureCount else 0,
                                min = 2,
                                max = 9,
                                countLabel = R.plurals.quick_shot_count,
                                withOff = true,
                                onFramesChange = { shots ->
                                    if (shots == 0) {
                                        onMultipleExposureToggle(false)
                                    } else {
                                        stackPick = "mexp"
                                        onMultipleExposureCountChange(shots)
                                        if (!useMultipleExposure) onMultipleExposureToggle(true)
                                    }
                                }
                            )
                        }
                    }
                }

                val portraitGroup: @Composable (first: Boolean) -> Unit = { first ->
                    QuickGroup(stringResource(R.string.capture_mode_portrait), GroupRed, onGuide = onPortraitHelp, divider = !first) {
                        rows.Setting(
                            key = "pblur",
                            label = stringResource(R.string.portrait_blur),
                            explanation = stringResource(R.string.quick_explain_portrait_blur),
                            onLongClick = onPortraitHelp
                        ) {
                            QuickChoice(
                                text = stringResource(R.string.portrait_blur_quick),
                                selected = portraitQuickBlur,
                                minWidth = 56.dp,
                                onClick = { onPortraitQuickBlurChange(true) }
                            )
                            QuickChoice(
                                text = stringResource(R.string.portrait_blur_depth),
                                selected = !portraitQuickBlur,
                                minWidth = 56.dp,
                                onClick = { onPortraitQuickBlurChange(false) }
                            )
                        }
                        rows.Setting(
                            key = "pbokeh",
                            label = stringResource(R.string.gallery_bokeh_style_title),
                            explanation = stringResource(R.string.quick_explain_portrait_bokeh),
                            last = true
                        ) {
                            listOf(
                                BokehStyle.NATURAL to R.string.gallery_bokeh_style_natural,
                                BokehStyle.DEFAULT to R.string.gallery_bokeh_style_default,
                                BokehStyle.BUBBLE to R.string.gallery_bokeh_style_bubble,
                            ).forEach { (style, label) ->
                                QuickChoice(
                                    text = stringResource(label),
                                    selected = portraitBokehStyle == style,
                                    minWidth = 48.dp,
                                    onClick = { onPortraitBokehStyleChange(style) }
                                )
                            }
                        }
                    }
                }

                val hdrGroup: @Composable (first: Boolean) -> Unit = { first ->
                    QuickGroup(stringResource(R.string.quick_group_hdr_plus), GroupMagenta, onGuide = onProHelp, divider = !first) {
                        rows.Setting(
                            key = "hdrp",
                            label = stringResource(R.string.quick_hdr_plus_frames),
                            explanation = stringResource(R.string.settings_hdr_plus_frame_count_description),
                            settingsTarget = SettingsTarget.HDR_PLUS
                        ) {
                            FrameCountSlider(
                                frames = hdrPlusFrameCount,
                                min = hdrPlusMinFrames,
                                withOff = false,
                                onFramesChange = onHdrPlusFrameCountChange
                            )
                        }
                        rows.Setting(
                            key = "hdrr",
                            label = stringResource(R.string.quick_hdr_ratio),
                            explanation = stringResource(R.string.settings_ml_ae_max_hdr_ratio_description),
                            settingsTarget = SettingsTarget.HDR_PLUS,
                            last = true
                        ) {
                            HdrRatioSlider(hdrRatioLimit, onHdrRatioLimitChange)
                        }
                    }
                }

                val exposureGroup: @Composable () -> Unit = {
                    QuickGroup(stringResource(R.string.quick_group_exposure), GroupMagenta) {
                        rows.Setting(
                            key = "meter",
                            label = stringResource(R.string.metering_mode),
                            explanation = stringResource(R.string.quick_explain_metering)
                        ) {
                            listOf(
                                MeteringMode.SYSTEM_DEFAULT to R.string.quick_metering_auto,
                                MeteringMode.CENTER_WEIGHTED to R.string.quick_metering_centre,
                                MeteringMode.SPOT to R.string.metering_spot,
                                MeteringMode.AVERAGE to R.string.quick_metering_average,
                                MeteringMode.HIGHLIGHT_PRIORITY to R.string.quick_metering_highlight,
                            ).forEach { (mode, label) ->
                                QuickChoice(
                                    text = stringResource(label),
                                    selected = meteringMode == mode,
                                    minWidth = 42.dp,
                                    onClick = { onMeteringModeChange(mode) }
                                )
                            }
                        }
                        rows.Setting(
                            key = "hist",
                            quiet = true,
                            label = stringResource(R.string.histogram),
                            explanation = stringResource(R.string.quick_explain_histogram),
                            last = true
                        ) {
                            QuickSwitch(showHistogram, quiet = true) { onHistogramChange(!showHistogram) }
                        }
                    }
                }

                // The file: Rendering (Pro), Resolution, Colour (front), Camera JPEG, Ultra HDR (back).
                val outputGroup: @Composable (title: Boolean) -> Unit = { title ->
                    QuickGroup(if (title) stringResource(R.string.quick_group_output) else null, GroupGold, divider = title) {
                        if (isProfessionalMode) {
                            rows.Setting(
                                key = "engine",
                                label = stringResource(R.string.quick_rendering),
                                explanation = rawColorEngineDescription(rawRenderingEngine),
                                settingsTarget = SettingsTarget.RAW_DEVELOPMENT
                            ) {
                                QuickLink(rawRenderingEngineName(rawRenderingEngine), open = false) {
                                    showRenderingEngineSheet = true
                                }
                            }
                        }
                        rows.Setting(
                            key = "res",
                            // Pro is always full size, so its row only reports that.
                            quiet = isProfessionalMode,
                            label = stringResource(R.string.photo_resolution),
                            explanation = stringResource(
                                if (isFrontCamera && !isProfessionalMode) R.string.quick_explain_resolution_front
                                else R.string.quick_explain_resolution
                            )
                        ) {
                            when {
                                // Pro (RAW) is always full size: the RAW stream only exists at full sensor size.
                                isProfessionalMode -> QuickFixed(stringResource(R.string.quick_resolution_full_raw))
                                // Night always shoots the front camera's binned 2 MP; Photo's choice comes back after.
                                isFrontCamera && isNightMode -> QuickFixed(resolutionLabel(2))
                                isFrontCamera -> listOf(0, 2).forEach { cap ->
                                    QuickChoice(
                                        text = resolutionLabel(cap),
                                        selected = frontPhotoResolutionCapMp == cap,
                                        minWidth = 56.dp,
                                        onClick = { onFrontPhotoResolutionCapChange(cap) }
                                    )
                                }
                                // A camera of 8 MP or less picks one size for every cap.
                                !photoResolutionAdjustable -> QuickFixed(stringResource(R.string.photo_resolution_full))
                                else -> listOf(0, 12, 8).forEach { cap ->
                                    QuickChoice(
                                        text = resolutionLabel(cap),
                                        selected = photoResolutionCapMp == cap,
                                        minWidth = 56.dp,
                                        onClick = { onPhotoResolutionCapChange(cap) }
                                    )
                                }
                            }
                        }
                        // Only where the camera's still processing differs from its YUV frames (lilac's front camera).
                        // Night has its own Fast / Accurate switch on the footer, with the same icons.
                        if (isFrontCamera && !isProfessionalMode && !isNightMode && com.hinnka.mycamera.camera.DeviceQuirks.isLilac) {
                            rows.Setting(
                                key = "colour",
                                label = stringResource(R.string.quick_colour),
                                explanation = stringResource(R.string.quick_explain_colour_front)
                            ) {
                                listOf(false, true).forEach { accurate ->
                                    QuickChoice(
                                        text = stringResource(
                                            if (accurate) R.string.quick_colour_accurate else R.string.quick_colour_fast
                                        ),
                                        selected = frontAccurateColour == accurate,
                                        minWidth = 56.dp,
                                        icon = if (accurate) AppIcons.MoonStars else AppIcons.RunLine,
                                        onClick = { onFrontAccurateColourChange(accurate) }
                                    )
                                }
                            }
                        }
                        rows.Setting(
                            key = "jpeg",
                            quiet = true,
                            label = stringResource(R.string.camera_jpeg),
                            explanation = stringResource(R.string.quick_explain_camera_jpeg),
                            last = isFrontCamera
                        ) {
                            QuickSwitch(saveCameraJpeg, quiet = true) { onSaveCameraJpegChange(!saveCameraJpeg) }
                        }
                        // The front camera makes no gain map.
                        if (!isFrontCamera) {
                            rows.Setting(
                                key = "uhdr",
                                quiet = true,
                                label = stringResource(R.string.settings_ultra_hdr_gain_map),
                                explanation = stringResource(R.string.quick_explain_ultra_hdr),
                                settingsTarget = SettingsTarget.ULTRA_HDR,
                                last = true
                            ) {
                                QuickSwitch(ultraHdrEnabled, quiet = true) { onUltraHdrToggle(!ultraHdrEnabled) }
                            }
                        }
                    }
                }

                val videoPictureGroup: @Composable () -> Unit = {
                    QuickGroup(stringResource(R.string.quick_group_picture), GroupRed, divider = false) {
                        rows.Setting(
                            key = "vasp",
                            label = stringResource(R.string.video_aspect_chip),
                            explanation = stringResource(R.string.quick_explain_video_aspect)
                        ) {
                            VideoAspectRatio.entries.forEach { ratio ->
                                QuickChoice(
                                    text = videoAspectRatioLabel(ratio),
                                    selected = videoAspectRatio == ratio,
                                    minWidth = 56.dp,
                                    onClick = { onVideoAspectRatioChange(ratio) }
                                )
                            }
                        }
                        rows.Setting(
                            key = "log",
                            label = stringResource(R.string.video_log_chip),
                            explanation = stringResource(R.string.quick_explain_log),
                            last = openPicker != "log"
                        ) {
                            QuickLink(videoLogProfileLabel(videoLogProfile), open = openPicker == "log") {
                                openPicker = if (openPicker == "log") null else "log"
                            }
                        }
                        InlinePicker(visible = openPicker == "log") {
                            availableVideoLogProfiles.forEach { profile ->
                                VideoOptionChip(
                                    title = videoLogProfileLabel(profile),
                                    selected = videoLogProfile == profile,
                                    onClick = { onVideoLogProfileChange(profile) }
                                )
                            }
                        }
                    }
                }

                val videoSoundGroup: @Composable () -> Unit = {
                    QuickGroup(stringResource(R.string.quick_group_sound), GroupGold) {
                        rows.Setting(
                            key = "mic",
                            quiet = true,
                            label = stringResource(R.string.video_microphone_title),
                            explanation = stringResource(R.string.quick_explain_microphone),
                            last = openPicker != "mic"
                        ) {
                            QuickLink(
                                selectedVideoAudioInputLabel(videoAudioInputId, videoAudioInputOptions),
                                open = openPicker == "mic"
                            ) {
                                openPicker = if (openPicker == "mic") null else "mic"
                            }
                        }
                        InlinePicker(visible = openPicker == "mic") {
                            VideoOptionChip(
                                title = stringResource(R.string.video_microphone_auto),
                                selected = videoAudioInputId == VIDEO_AUDIO_INPUT_AUTO,
                                onClick = { onVideoAudioInputChange(VIDEO_AUDIO_INPUT_AUTO) }
                            )
                            videoAudioInputOptions.forEach { option ->
                                VideoOptionChip(
                                    title = videoAudioInputLabel(option),
                                    selected = videoAudioInputId == option.id,
                                    onClick = { onVideoAudioInputChange(option.id) }
                                )
                            }
                        }
                    }
                }

                // One tab, so no title: Recording.
                val videoRecordingGroup: @Composable () -> Unit = {
                    QuickGroup(divider = false) {
                        rows.Setting(
                            key = "bitrate",
                            label = stringResource(R.string.quick_bitrate),
                            explanation = stringResource(R.string.quick_explain_bitrate)
                        ) {
                            VideoBitratePreset.entries.forEach { bitrate ->
                                QuickChoice(
                                    text = bitrate.bitrateMbps.toString(),
                                    selected = videoBitrate == bitrate,
                                    minWidth = 42.dp,
                                    onClick = { onVideoBitrateChange(bitrate) }
                                )
                            }
                        }
                        rows.Setting(
                            key = "codec",
                            quiet = true,
                            label = stringResource(R.string.quick_codec),
                            explanation = stringResource(R.string.quick_explain_codec),
                            last = true
                        ) {
                            VideoCodec.entries.forEach { codec ->
                                QuickChoice(
                                    text = codec.displayName,
                                    selected = videoCodec == codec,
                                    minWidth = 56.dp,
                                    onClick = { onVideoCodecChange(codec) }
                                )
                            }
                        }
                    }
                }

                // Shooting: what changes the shot. The mode's own group carries its guide.
                val shooting: @Composable () -> Unit = {
                    if (isVideo) {
                        videoPictureGroup()
                        videoSoundGroup()
                    } else {
                        lensGroup(true)
                        val afterLens = lensChoices.isEmpty()
                        when {
                            isPortraitMode -> {
                                portraitGroup(afterLens)
                                stackGroup(false, null)
                            }
                            isProfessionalMode -> hdrGroup(afterLens)
                            else -> stackGroup(afterLens, onBasicsHelp)
                        }
                        exposureGroup()
                    }
                }
                // Output: what changes the file.
                val output: @Composable () -> Unit = {
                    if (isVideo) videoRecordingGroup() else outputGroup(false)
                }

                if (tabbed) {
                    QuickTabPages(tab = qTab, onTab = { qTab = it }, pages = listOf(shooting, output))
                } else {
                    QuickScroll {
                        stackGroup(true, onBasicsHelp)
                        exposureGroup()
                        outputGroup(true)
                    }
                }
            }
        }
    }

    if (showRenderingEngineSheet) {
        AppModalBottomSheet(
            onDismissRequest = { showRenderingEngineSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            Text(
                text = stringResource(R.string.settings_raw_color_engine),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // Fill the sheet so switching engines only changes the scroll range, never the anchors.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                RawRenderingEngineSettingsPanel(
                    selectedDcpId = rawDcpId,
                    rawDcpIdsByLens = rawDcpIdsByLens,
                    dcpLensOptions = rawDcpLensOptions,
                    availableDcps = availableDcps,
                    rawRenderingEngine = rawRenderingEngine,
                    rawToneMappingParameters = rawToneMappingParameters,
                    spectralFilmSelection = rawSpectralFilmSelection,
                    spectralFilmPrint = rawSpectralFilmPrint,
                    onSelectDcp = onRawDcpChange,
                    onRawDcpIdsByLensChange = onRawDcpIdsByLensChange,
                    onImportDcp = onImportRawDcp,
                    onDeleteDcp = onDeleteRawDcp,
                    hncsFilmCurveMode = rawHncsFilmCurveMode,
                    onHncsFilmCurveModeChange = onRawHncsFilmCurveModeChange,
                    onRawColorEngineChange = onRawColorEngineChange,
                    onRawToneMappingParametersChange = onRawToneMappingParametersChange,
                    onSpectralFilmSelectionChange = onRawSpectralFilmSelectionChange,
                    onSpectralFilmPrintChange = onRawSpectralFilmPrintChange,
                    fotufilmDocument = rawFotufilmDocument,
                    onFotufilmDocumentChange = onRawFotufilmDocumentChange,
                    inlineEngineOptions = true,
                    showToneMappingControls = true,
                )
            }
        }
    }
}

@Composable
private fun RowScope.PhotoEverydayItems(
    useLivePhoto: Boolean,
    onLivePhotoToggle: () -> Unit,
    timerSeconds: Int,
    onTimerToggle: () -> Unit,
    smileThreshold: Int,
    onSmileToggle: () -> Unit,
    aspectRatio: AspectRatio,
    onAspectClick: () -> Unit,
    flashMode: Int,
    flashAvailable: Boolean,
    onFlashToggle: () -> Unit,
    itemModifier: Modifier
) {
    val on = stringResource(R.string.quick_on)
    val off = stringResource(R.string.quick_off)
    EverydayItem(
        label = "${stringResource(R.string.quick_live)} ${if (useLivePhoto) on else off}",
        active = useLivePhoto,
        onClick = onLivePhotoToggle,
        modifier = itemModifier
    ) { color -> Icon(AppIcons.LivePhotoLine, contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
    EverydayItem(
        label = "${stringResource(R.string.timer)} ${if (timerSeconds > 0) "${timerSeconds}s" else off}",
        active = timerSeconds > 0,
        onClick = onTimerToggle,
        modifier = itemModifier
    ) { color -> Icon(AppIcons.StopwatchLine, contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
    EverydayItem(
        label = "${stringResource(R.string.smile)} ${smileSizeLetter(smileThreshold) ?: off}",
        active = smileThreshold > 0,
        onClick = onSmileToggle,
        modifier = itemModifier
    ) { color -> Icon(AppIcons.MoodSmileLine, contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
    EverydayItem(
        label = aspectRatio.getDisplayName(),
        active = false,
        onClick = onAspectClick,
        modifier = itemModifier
    ) { color -> AspectIcon(aspectRatio, color) }
    EverydayItem(
        label = "${stringResource(R.string.flash)} ${
            when (flashMode) {
                1 -> on
                2 -> stringResource(R.string.quick_torch)
                else -> off
            }
        }",
        active = flashAvailable && flashMode != 0,
        enabled = flashAvailable,
        onClick = onFlashToggle,
        modifier = itemModifier
    ) { color ->
        Icon(
            imageVector = when (flashMode) {
                1 -> AppIcons.BoltLine
                2 -> AppIcons.BulbLine
                else -> AppIcons.BoltOffLine
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun RowScope.VideoEverydayItems(
    videoConfig: VideoConfig,
    videoCapabilities: VideoCapabilities,
    onResolutionClick: () -> Unit,
    onFpsClick: () -> Unit,
    onTorchToggle: () -> Unit,
    onStabilizationModeChange: (VideoStabilizationMode) -> Unit,
    slowMotionAvailable: Boolean,
    slowMotionEnabled: Boolean,
    onSlowMotionToggle: () -> Unit,
    itemModifier: Modifier
) {
    val resolution = if (videoConfig.resolution == VideoResolutionPreset.MAX) {
        stringResource(R.string.video_resolution_max)
    } else {
        videoConfig.resolution.displayName
    }
    EverydayItem(stringResource(R.string.photo_resolution), active = false, onClick = onResolutionClick, modifier = itemModifier) { color ->
        EverydayValue(resolution, color)
    }
    EverydayItem(stringResource(R.string.quick_fps), active = false, onClick = onFpsClick, modifier = itemModifier) { color ->
        EverydayValue(videoConfig.fps.fps.toString(), color)
    }
    if (slowMotionAvailable) {
        EverydayItem(
            label = "${stringResource(R.string.slow_motion_chip)} ${stringResource(if (slowMotionEnabled) R.string.quick_on else R.string.quick_off)}",
            active = slowMotionEnabled,
            onClick = onSlowMotionToggle,
            modifier = itemModifier
        ) { color -> EverydayValue("960", color) }
    }
    val torch = videoConfig.torchEnabled
    EverydayItem(
        label = "${stringResource(R.string.video_torch_chip)} ${stringResource(if (torch) R.string.quick_on else R.string.quick_off)}",
        active = torch,
        // Slow motion's camera session carries no torch and no stabilisation setting.
        enabled = videoCapabilities.supportsTorch && !slowMotionEnabled,
        onClick = onTorchToggle,
        modifier = itemModifier
    ) { color ->
        Icon(
            imageVector = if (torch) AppIcons.BulbLine else AppIcons.BulbOffLine,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
    val modes = videoCapabilities.availableStabilizationModes
    if (modes.size > 1) {
        EverydayItem(
            label = stringResource(R.string.quick_stabilise),
            active = false,
            enabled = !slowMotionEnabled,
            onClick = {
                onStabilizationModeChange(modes[(modes.indexOf(videoConfig.stabilizationMode) + 1).mod(modes.size)])
            },
            modifier = itemModifier
        ) { color -> EverydayValue(videoStabilizationModeLabel(videoConfig.stabilizationMode), color) }
    }
}

// Everyday items turn with the phone in the sheet; on the picture's top edge they stay upright.
private val LocalEverydayTurns = staticCompositionLocalOf { true }

/** An everyday setting: its icon or value, and its name with the value underneath. Turns with the phone. */
@Composable
private fun EverydayItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    big: @Composable (Color) -> Unit
) {
    val color = when {
        !enabled -> SheetText.copy(alpha = 0.35f)
        active -> ModeAmber
        else -> SheetText
    }
    // The item floats on the picture, so a pressed box would look out of place: the icon and its name
    // shrink and dim under the finger instead.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 180), label = "press")
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 1f - 0.12f * press
                    scaleY = 1f - 0.12f * press
                    alpha = 1f - 0.4f * press
                }
                .then(if (LocalEverydayTurns.current) Modifier.autoRotate() else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) { big(color) }
            Text(
                text = label,
                color = if (active || !enabled) color else SheetDim,
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
                style = SheetTextStyle
            )
        }
    }
}

@Composable
private fun EverydayValue(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = SheetTextStyle)
}

/** An outline of the frame in that ratio, upright, at most 22 dp. */
@Composable
private fun AspectIcon(ratio: AspectRatio, color: Color) {
    val w = ratio.heightRatio.toFloat()
    val h = ratio.widthRatio.toFloat()
    val scale = 22f / maxOf(w, h)
    Box(
        Modifier
            .size(width = maxOf(8f, w * scale).dp, height = (h * scale).dp)
            .border(1.5.dp, color, RoundedCornerShape(2.dp))
    )
}

@Composable
internal fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
    }
}

// Group title colours, as Sony's camera tabs: Shooting red, Exposure magenta, Setup gold.
internal val GroupRed = Color(0xFFEA2546)
internal val GroupMagenta = Color(0xFFF54FBA)
internal val GroupGold = Color(0xFFB28E02)

/**
 * A group of rows: [title] with a bar in [color] on top (a tap on it opens [onGuide] when given, shown by an
 * info mark), parted from the group above by a line unless [divider] is false.
 */
@Composable
internal fun QuickGroup(
    title: String? = null,
    color: Color = GroupRed,
    onGuide: (() -> Unit)? = null,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (divider) GroupDivider() else Spacer(Modifier.height(6.dp))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            val guideDescription = stringResource(R.string.mode_guide_open, title)
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .then(
                        if (onGuide != null) {
                            Modifier.clickable(onClick = onGuide).semantics { contentDescription = guideDescription }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(width = 3.dp, height = 11.dp).background(color))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = title.uppercase(),
                    color = SonyText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    maxLines = 1
                )
                if (onGuide != null) {
                    Icon(AppIcons.InfoLine, contentDescription = null, tint = SonySecondary, modifier = Modifier.padding(start = 5.dp).size(12.dp))
                }
            }
        }
        content()
    }
}

/** The sheet's top row: the Shooting / Output tabs on the left, then Toolbox, Content management and Settings. */
@Composable
private fun SheetTabRow(
    tabbed: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    onToolboxClick: () -> Unit,
    onContentManagementClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tabbed) {
            SheetTab(stringResource(R.string.quick_tab_shooting), tab == 0, GroupRed) { onTab(0) }
            SheetTab(stringResource(R.string.quick_tab_output), tab == 1, GroupGold) { onTab(1) }
        }
        Spacer(Modifier.weight(1f))
        if (tabbed) Box(Modifier.width(1.dp).height(24.dp).background(SonyTrack))
        Spacer(Modifier.width(6.dp))
        HeaderIcon(AppIcons.ToolboxLine, stringResource(R.string.toolbox_title), onToolboxClick)
        HeaderIcon(AppIcons.FoldersLine, stringResource(R.string.settings_section_management), onContentManagementClick)
        HeaderIcon(AppIcons.SettingsLine, stringResource(R.string.settings_title), onSettingsClick)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SonyDivider))
}

@Composable
private fun SheetTab(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) SonyText else SonySecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.44f).height(3.dp).background(color))
    }
}

/**
 * The tabs' pages side by side in one track, so the sheet keeps the taller page's height whichever tab shows.
 * A tap on a tab or a sideways swipe over 50 dp slides to the other page over 250 ms.
 */
@Composable
private fun QuickTabPages(tab: Int, onTab: (Int) -> Unit, pages: List<@Composable () -> Unit>) {
    val position by animateFloatAsState(tab.toFloat(), tween(250, easing = FastOutSlowInEasing), label = "qTab")
    val currentTab by rememberUpdatedState(tab)
    val currentOnTab by rememberUpdatedState(onTab)
    QuickScroll(
        Modifier.pointerInput(pages.size) {
            val swipe = 50.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var dx = 0f
                var dy = 0f
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    // A slider or the list's own scroll took the drag.
                    if (!change.pressed || change.isConsumed) break
                    val moved = change.positionChange()
                    dx += moved.x
                    dy += moved.y
                    if (abs(dx) > swipe && abs(dx) > 1.5f * abs(dy)) {
                        val next = (currentTab + if (dx < 0) 1 else -1).coerceIn(0, pages.size - 1)
                        if (next != currentTab) currentOnTab(next)
                        change.consume()
                        break
                    }
                }
            }
        }
    ) {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            pages.forEachIndexed { i, page ->
                Column(Modifier.fillMaxWidth().graphicsLayer { translationX = (i - position) * size.width }) { page() }
            }
        }
    }
}

/** The list under the tab row: scrolls past 300 dp, fading out over its last 28 dp while more lies below. */
@Composable
private fun QuickScroll(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberScrollState()
    Column(
        modifier = modifier
            .heightIn(max = 300.dp)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (state.canScrollForward) {
                    val fade = 28.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - fade, size.height),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
                        size = androidx.compose.ui.geometry.Size(size.width, fade),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
            .verticalScroll(state),
        content = content
    )
}

/** Groups are parted by a 1 dp line and a 6 dp gap, with no boxes around them. */
@Composable
private fun GroupDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SonyDivider))
    Spacer(Modifier.height(6.dp))
}

/** List rows: the name on the left (tap: explanation, long press: Settings), the choices on the right. */
internal class QuickRows(
    val explained: String?,
    val onExplain: (String) -> Unit,
    val onOpenSettingsAt: (SettingsTarget) -> Unit
) {
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Setting(
        key: String,
        label: String,
        explanation: String,
        settingsTarget: SettingsTarget? = null,
        last: Boolean = false,
        // A long press that does something else than opening Settings.
        onLongClick: (() -> Unit)? = null,
        // A small orange dot after the name: something here differs from what was saved.
        marked: Boolean = false,
        // A set-and-forget row: shorter and grey, still a 48 dp target.
        quiet: Boolean = false,
        content: @Composable RowScope.() -> Unit
    ) {
        val open = explained == key
        val rowHeight = if (quiet) 48.dp else 56.dp
        // The choices drop to a second line, still right-aligned, when they don't fit beside the name.
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LongerLongPress {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(rowHeight)
                        .combinedClickable(
                            onClick = { onExplain(key) },
                            onLongClick = onLongClick ?: settingsTarget?.let { target -> { onOpenSettingsAt(target) } }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (open) Color.White else if (quiet) SonySecondary else SonyText,
                        fontSize = if (quiet) 14.sp else 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (marked) {
                        Box(Modifier.padding(start = 5.dp).size(5.dp).background(ModeAmber, CircleShape))
                    }
                }
            }
            Row(
                modifier = Modifier.height(rowHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
        if (open) {
            Text(
                text = explanation,
                color = SonySecondary,
                fontSize = 14.sp,
                lineHeight = 1.4.em,
                modifier = Modifier.padding(end = 8.dp, bottom = if (last) 11.dp else 8.dp)
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

@Composable
internal fun QuickChoice(text: String, selected: Boolean, minWidth: Dp, icon: ImageVector? = null, onClick: () -> Unit) =
    LilacChip(
        text = text,
        selected = selected,
        onClick = onClick,
        minWidth = maxOf(minWidth, 48.dp),
        height = 40.dp,
        touchHeight = 48.dp,
        fontSize = 15.sp,
        horizontalPadding = 10.dp,
        icon = icon,
        textColor = SonySecondary
    )

/** Grey track and thumb when off; a light track with a black thumb on the right when on. [quiet] is smaller. */
@Composable
private fun QuickSwitch(checked: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val width = if (quiet) 46.dp else 52.dp
    val height = if (quiet) 28.dp else 32.dp
    val thumb = if (quiet) 16.dp else 20.dp
    val inset = 6.dp
    val knob by androidx.compose.animation.core.animateDpAsState(if (checked) width - inset - thumb else inset, tween(150), label = "quickSwitch")
    Box(
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = width, height = height).background(if (checked) SonyText else SonyTrack, RoundedCornerShape(height / 2))) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knob)
                    .size(thumb)
                    .background(if (checked) Color.Black else SonyDim, CircleShape)
            )
        }
    }
}

/** A value that opens a picker: an inline list under the row, or a sheet. */
@Composable
internal fun QuickLink(value: String, open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, color = SonySecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Icon(
            imageVector = if (open) AppIcons.ChevronUpLine else AppIcons.ChevronRightLine,
            contentDescription = null,
            tint = SheetGrey,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun QuickFixed(text: String) {
    Text(
        text = text,
        color = SonySecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(end = 6.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlinePicker(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

/**
 * A frame count as a slider, whole counts from [min] up to [max]. With [withOff] the left end is Off
 * (reported as 0); without it a single frame is the inactive state.
 */
@Composable
private fun FrameCountSlider(
    frames: Int,
    min: Int,
    withOff: Boolean,
    onFramesChange: (Int) -> Unit,
    max: Int = MultiFrameConfig.MAX_FRAME_COUNT,
    // Frame counts show the bare number: the row's name already says what is counted.
    countLabel: Int? = null
) {
    val start = if (withOff) min - 1 else min
    // The value while dragging, until the saved setting reaches it: saves can land late and one drag step at a
    // time (switching multi-frame on reconfigures the camera), and showing each would make the slider jump back.
    // After 2 s without a change the saved setting shows anyway, in case the choice was refused.
    var dragged by remember { mutableStateOf<Int?>(null) }
    val savedShown = if (withOff && frames <= 0) 0 else frames.coerceIn(min, max)
    LaunchedEffect(savedShown) { if (savedShown == dragged) dragged = null }
    LaunchedEffect(dragged) {
        if (dragged != null) {
            delay(2000)
            dragged = null
        }
    }
    val currentOnFramesChange by rememberUpdatedState(onFramesChange)
    val shown = dragged ?: savedShown
    val target = ((if (shown == 0) start else shown) - start).toFloat() / (max - start)
    // Follows the finger directly; any other change (the other stacking slider taking over) glides there.
    val position = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (dragged != null) position.snapTo(target) else position.animateTo(target, tween(250, easing = FastOutSlowInEasing))
    }
    QuickSlider(
        text = if (shown == 0) stringResource(R.string.quick_frames_off)
        else countLabel?.let { pluralStringResource(it, shown, shown) } ?: shown.toString(),
        fraction = position.value,
        onFraction = { f ->
            val position = (start + f * (max - start)).roundToInt()
            val value = if (withOff && position <= start) 0 else position
            if (value != dragged) {
                dragged = value
                currentOnFramesChange(value)
            }
        }
    )
}

/** HDR ratio limit, 1 to 20 in 0.5 steps; a double tap restores the default. Shown while dragging, as saves land late. */
@Composable
private fun HdrRatioSlider(limit: Float, onLimitChange: (Float) -> Unit) {
    val min = RawSceneExposureMath.MIN_CONFIGURED_MAX_HDR_RATIO
    val max = RawSceneExposureMath.MAX_CONFIGURED_MAX_HDR_RATIO
    val default = RawSceneExposureMath.FAST_MOMENTS_MAX_HDR_RATIO
    var dragged by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(limit) { if (limit == dragged) dragged = null }
    LaunchedEffect(dragged) {
        if (dragged != null) {
            delay(2000)
            dragged = null
        }
    }
    val shown = dragged ?: limit
    QuickSlider(
        text = String.format(Locale.getDefault(), "%.1f×", shown),
        fraction = (shown - min) / (max - min),
        onFraction = { f ->
            val value = ((min + f * (max - min)) * 2f).roundToInt() / 2f
            if (value != dragged) {
                dragged = value
                onLimitChange(value)
            }
        },
        onDoubleTap = { dragged = default; onLimitChange(default) }
    )
}

/**
 * The sheet's slider: the value on the left, a 112 dp track with 10 dp to touch on each side; reports 0..1
 * while dragging.
 */
@Composable
internal fun QuickSlider(
    text: String,
    fraction: Float,
    onFraction: (Float) -> Unit,
    onDoubleTap: (() -> Unit)? = null
) {
    val trackWidth = 112.dp
    val currentOnFraction by rememberUpdatedState(onFraction)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    Row(
        modifier = Modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = SonySecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(66.dp)
        )
        // At the left end the knob reaches 10 dp past the track; keep it clear of the value.
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(trackWidth + 20.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    val edge = 10.dp.toPx()
                    fun select(x: Float) = currentOnFraction(((x - edge) / (size.width - 2 * edge)).coerceIn(0f, 1f))
                    var lastDown = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val doubleTap = currentOnDoubleTap
                        if (doubleTap != null && down.uptimeMillis - lastDown < viewConfiguration.doubleTapTimeoutMillis) {
                            lastDown = 0L
                            doubleTap()
                            return@awaitEachGesture
                        }
                        lastDown = down.uptimeMillis
                        select(down.position.x)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) select(change.position.x)
                            change.consume()
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(SonyTrack, RoundedCornerShape(2.dp))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .width(trackWidth * fraction)
                    .height(4.dp)
                    .background(SonyText, RoundedCornerShape(2.dp))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = trackWidth * fraction)
                    .size(20.dp)
                    .background(SonyText, CircleShape)
            )
        }
    }
}

@Composable
private fun resolutionLabel(capMp: Int): String =
    if (capMp == 0) stringResource(R.string.photo_resolution_full) else stringResource(R.string.quick_resolution_mp, capMp)

@Composable
private fun VideoOptionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) ChosenFill else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun selectedVideoAudioInputLabel(
    selectedAudioInputId: String,
    options: List<VideoAudioInputOption>
): String {
    if (selectedAudioInputId == VIDEO_AUDIO_INPUT_AUTO) {
        return stringResource(R.string.video_microphone_auto)
    }
    val option = options.firstOrNull { it.id == selectedAudioInputId }
    return option?.let { videoAudioInputLabel(it) } ?: stringResource(R.string.video_microphone_disconnected)
}

@Composable
private fun videoAudioInputLabel(option: VideoAudioInputOption): String {
    val baseLabel = when (option.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> stringResource(R.string.video_microphone_builtin)
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> stringResource(R.string.video_microphone_wired)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> stringResource(R.string.video_microphone_bluetooth)
        AudioDeviceInfo.TYPE_BLE_HEADSET -> stringResource(R.string.video_microphone_ble)
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> stringResource(R.string.video_microphone_usb)
        AudioDeviceInfo.TYPE_HDMI -> stringResource(R.string.video_microphone_hdmi)
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> stringResource(R.string.video_microphone_line_in)
        else -> stringResource(R.string.video_microphone_external)
    }
    val customName = option.productName
        ?.takeUnless { it.equals(Build.MODEL, ignoreCase = true) }
        ?.takeUnless { it.equals(Build.DEVICE, ignoreCase = true) }
        ?.takeUnless { it.equals(Build.PRODUCT, ignoreCase = true) }
    val suffix = when {
        option.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && !option.address.isNullOrBlank() -> option.address
        customName != null && customName != baseLabel -> customName
        else -> null
    }
    return if (suffix != null) "$baseLabel (${suffix})" else baseLabel
}

@Composable
private fun videoAspectRatioLabel(aspectRatio: VideoAspectRatio): String {
    return when (aspectRatio) {
        VideoAspectRatio.RATIO_16_9 -> stringResource(R.string.video_aspect_16_9)
        VideoAspectRatio.RATIO_21_9 -> stringResource(R.string.video_aspect_21_9)
        VideoAspectRatio.OPEN_GATE -> stringResource(R.string.video_aspect_open_gate)
    }
}

@Composable
private fun videoLogProfileLabel(profile: VideoLogProfile): String {
    return when (profile) {
        VideoLogProfile.OFF -> stringResource(R.string.video_log_off)
        else -> profile.displayName
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSettingGrid(
    columns: Int = 3,
    itemHeight: Dp = 48.dp,
    content: @Composable (Modifier) -> Unit
) {
    // Keep a consistent column count as the available photo controls change.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = columns
    ) {
        content(Modifier.weight(1f).height(itemHeight))
    }
}

@Composable
private fun QuickSettingLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    maxFontSize: TextUnit = 10.sp
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.copy(
            color = color,
            fontWeight = fontWeight,
            fontSize = maxFontSize,
            lineHeight = 1.2.em
        ),
        maxLines = 2,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = maxFontSize)
    )
}

@Composable
internal fun ContentManagementOptionsPanel(
    visible: Boolean,
    onFilterManageClick: () -> Unit,
    onFrameManageClick: () -> Unit,
    onPresetManageClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            QuickSettingGrid { itemModifier ->
                QuickSettingButton(
                    title = stringResource(R.string.settings_filter_management),
                    icon = AppIcons.AutoAwesome,
                    onClick = onFilterManageClick,
                    modifier = itemModifier
                )
                QuickSettingButton(
                    title = stringResource(R.string.settings_frame_management),
                    icon = AppIcons.BorderBottom,
                    onClick = onFrameManageClick,
                    modifier = itemModifier
                )
                QuickSettingButton(
                    title = stringResource(R.string.settings_preset_management),
                    icon = AppIcons.Bookmark,
                    onClick = onPresetManageClick,
                    modifier = itemModifier
                )
            }
        }
    }
}

/** Tiles wait 1.5x the system long press, so a slow tap still toggles instead of opening Settings. */
@Composable
private fun LongerLongPress(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val longer = remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis: Long get() = base.longPressTimeoutMillis * 3 / 2
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides longer, content = content)
}

@Composable
fun QuickSettingButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickSettingLabel(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}


/** The smile shutter's size of smile as one letter: S small, M average, L big; null when off. */
internal fun smileSizeLetter(threshold: Int): String? = when {
    threshold <= 0 -> null
    threshold < 55 -> "S"
    threshold < 70 -> "M"
    else -> "L"
}
