package com.hinnka.mycamera.ui.gallery

import com.hinnka.mycamera.raw.RawPhotonHdrMetadata
import com.hinnka.mycamera.data.UserPreferences
import com.hinnka.mycamera.ui.prefs.rememberPreference
import com.hinnka.mycamera.ui.components.AppModalBottomSheet
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.hinnka.mycamera.ui.theme.WarningYellow
import com.hinnka.mycamera.R
import androidx.compose.ui.res.painterResource
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.PhotoStacks
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.hinnka.mycamera.video.SlowedCopy
import androidx.media3.common.C
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import coil.request.ImageRequest
import coil.compose.AsyncImage
import com.hinnka.mycamera.hdr.HdrGainmapStrength
import com.hinnka.mycamera.lut.isVideoTransformerExportSupported
import com.hinnka.mycamera.lut.VideoExportOption
import com.hinnka.mycamera.lut.VideoExportResolution
import com.hinnka.mycamera.lut.VideoExportSupport
import com.hinnka.mycamera.lut.VideoLutEffect
import com.hinnka.mycamera.ui.camera.autoRotate
import com.hinnka.mycamera.ui.components.CustomSlider
import com.hinnka.mycamera.ui.components.PhysicalButton
import com.hinnka.mycamera.ui.components.ProcessingPhotoPreview
import com.hinnka.mycamera.utils.DeviceUtil
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.render.RenderQueue
import com.hinnka.mycamera.viewmodel.GalleryTab
import kotlinx.coroutines.Dispatchers
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.ui.res.vectorResource
import com.hinnka.mycamera.ui.components.EdgeRingStyle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.roundToInt
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlin.math.max
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import kotlinx.coroutines.flow.Flow
import me.saket.telephoto.zoomable.ZoomableImage as TelephotoImage
import me.saket.telephoto.zoomable.ZoomableImageSource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import com.hinnka.mycamera.render.RenderJob
import com.hinnka.mycamera.render.RenderLook
import androidx.compose.runtime.produceState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.semantics.Role
import com.hinnka.mycamera.ui.camera.ShadowedLineIcon
import com.hinnka.mycamera.ui.camera.SpacedBuzz
import com.hinnka.mycamera.ui.camera.ViewfinderTextShadow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.exoplayer.SeekParameters
import com.hinnka.mycamera.ui.components.HideNavigationBar
import com.hinnka.mycamera.ui.components.LabelledIcon
import com.hinnka.mycamera.ui.components.SonyText
import com.hinnka.mycamera.utils.OrientationObserver
import com.hinnka.mycamera.utils.VibrationHelper
import me.saket.telephoto.zoomable.ZoomableState

internal val GalleryToolbarButton = Color(0xFF242424)
internal val GalleryToolbarContent = Color(0xFFF2F2F2)
internal val GallerySheetSurface = Color(0xFF171717)

private class VideoPreviewHandle {
    private var activeLease: Lease? = null

    /** The shown video's player, for the controls in the item row; null while none is attached. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    fun attach(player: ExoPlayer): Lease {
        release()
        this.player = player
        return Lease(player).also { activeLease = it }
    }

    fun release(lease: Lease) {
        if (activeLease === lease) {
            activeLease = null
            player = null
        }
        lease.release()
    }

    fun release() {
        activeLease?.release()
        activeLease = null
        player = null
    }

    class Lease(private val player: ExoPlayer) {
        private var isReleased = false

        fun release() {
            if (isReleased) return
            isReleased = true
            player.release()
        }
    }
}

@Composable
private fun videoExportResolutionLabel(resolution: VideoExportResolution): String {
    return stringResource(
        when (resolution) {
            VideoExportResolution.ORIGINAL -> R.string.export_video_resolution_original
            VideoExportResolution.UHD_8K -> R.string.export_video_resolution_8k
            VideoExportResolution.UHD_4K -> R.string.export_video_resolution_4k
            VideoExportResolution.FHD_1080P -> R.string.export_video_resolution_1080p
            VideoExportResolution.HD_720P -> R.string.export_video_resolution_720p
        }
    )
}

/**
 * 照片详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    viewModel: GalleryViewModel,
    initialIndex: Int = 0,
    selectedTab: GalleryTab? = null,
    photoId: String? = null,
    isExpanded: Boolean = false,
    onBack: () -> Unit = {},
    onGoToGallery: (() -> Unit)? = null,
    // Opened from the grid: the first action reads Gallery, and swiping down still goes to the camera.
    fromGrid: Boolean = false,
    onGoToCamera: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onOpenRenderQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allPhotos by viewModel.currentPhotos.collectAsState()
    val stacks = remember(allPhotos) { PhotoStacks.of(allPhotos) }
    // Pages are photo stacks; the strip picks which version of a stack shows (its newest until then).
    val photos = stacks.tiles
    val shownVersionIds = remember { mutableStateMapOf<String, String>() }
    fun shownVersion(tile: MediaData): MediaData = stacks.versionsOf(tile)
        .firstOrNull { it.id == shownVersionIds[PhotoStacks.stackIdOf(tile)] } ?: tile
    fun showVersion(photo: MediaData) {
        shownVersionIds[PhotoStacks.stackIdOf(photo)] = photo.id
    }
    val processingPhotos by viewModel.processingPhotos.collectAsState()
    var previousProcessingIds by remember { mutableStateOf(processingPhotos.keys.toSet()) }
    LaunchedEffect(processingPhotos.keys) {
        (previousProcessingIds - processingPhotos.keys).forEach(viewModel::invalidatePreviewCache)
        previousProcessingIds = processingPhotos.keys.toSet()
    }
    val context = LocalContext.current
    // Opening a photo (often straight from the camera's thumbnail): waiting RAW renders may start now.
    LaunchedEffect(Unit) { RenderQueue.releaseCameraHold(context) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    // A tap on the photo hides the bars and labels; the version strip only dims.
    var overlaysHidden by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportAllFrames by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isExportingDng by remember { mutableStateOf(false) }
    var isCopyingSettings by remember { mutableStateOf(false) }
    var isPastingSettings by remember { mutableStateOf(false) }
    val isVideoExporting = viewModel.isVideoExporting
    val videoExportProgress = viewModel.videoExportProgress
    var showVideoExportConfirmDialog by remember { mutableStateOf(false) }
    var videoExportOptions by remember { mutableStateOf<List<VideoExportOption>>(emptyList()) }
    var selectedVideoExportOption by remember { mutableStateOf<VideoExportOption?>(null) }
    var isLoadingVideoExportOptions by remember { mutableStateOf(false) }
    val isSharing by viewModel.isSharing.collectAsState()
    val hasCopiedEditSettings by viewModel.hasCopiedEditSettings.collectAsState()
    var showMoreSheet by remember { mutableStateOf(false) }
    var showRenderAgainSheet by remember { mutableStateOf(false) }
    var isRenderingAgain by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showHdrStrengthPanel by remember { mutableStateOf(false) }
    val videoPreviewHandle = remember { VideoPreviewHandle() }

    val currentColorSpace = remember { mutableStateOf<ColorSpace?>(null) }

    // Activity Result Launcher for delete confirmation
    val deletePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || viewModel.selectedTab == GalleryTab.PHOTON) {
            // User confirmed deletion or we are in PHOTON tab (delete internal photo anyway)
            viewModel.deletePhotoAfterConfirmation { success ->
                if (success && viewModel.currentPhotos.value.isEmpty()) {
                    onBack()
                }
            }
        } else {
            // User cancelled deletion in SYSTEM tab
            viewModel.clearDeleteRequest()
        }
    }

    // "Delete all versions" deletes a list, which asks once for all exported copies.
    val batchDeletePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || viewModel.selectedTab == GalleryTab.PHOTON) {
            viewModel.deleteBatchPhotosAfterConfirmation {
                if (viewModel.currentPhotos.value.isEmpty()) onBack()
            }
        } else {
            viewModel.clearBatchDeleteRequest()
        }
    }
    LaunchedEffect(viewModel.batchDeletePendingIntent) {
        viewModel.batchDeletePendingIntent?.let { pendingIntent ->
            try {
                batchDeletePhotoLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                viewModel.clearBatchDeleteRequest()
            }
        }
    }

    val systemDeletePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.deleteSystemPhotoAfterConfirmation { success ->
                if (success && viewModel.currentPhotos.value.isEmpty()) {
                    onBack()
                }
            }
        } else {
            viewModel.clearDeleteRequest()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != null) {
            viewModel.selectTab(selectedTab)
        }
    }

    // Monitor deletePendingIntent and launch system delete dialog
    LaunchedEffect(viewModel.deletePendingIntent) {
        viewModel.deletePendingIntent?.let { pendingIntent ->
            try {
                deletePhotoLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                // Failed to launch, clear the request
                viewModel.clearDeleteRequest()
            }
        }
    }

    LaunchedEffect(viewModel.systemDeletePendingIntent) {
        viewModel.systemDeletePendingIntent?.let { pendingIntent ->
            try {
                systemDeletePhotoLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                viewModel.clearDeleteRequest()
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = remember(initialIndex, photoId) {
            // 初始页只在首次组合时计算，后续通过 LaunchedEffect 跳转
            if (photoId != null) {
                val index = allPhotos.firstOrNull { it.id == photoId }?.let(stacks::tileIndexOf) ?: -1
                if (index != -1) index else initialIndex
            } else {
                initialIndex
            }
        },
        pageCount = { photos.size }
    )

    // 记录是否已经执行过初始的 photoId 跳转
    var initialJumpDone by rememberSaveable(photoId) { mutableStateOf(false) }
    // 记录上次的照片数量，用于判断是否有新照片增加
    var lastPhotosCount by rememberSaveable { mutableIntStateOf(photos.size) }

    // 同步当前索引
    val currentPhoto = photos.getOrNull(pagerState.currentPage)?.let(::shownVersion)
    LaunchedEffect(pagerState.currentPage, currentPhoto?.id, currentPhoto?.id in processingPhotos) {
        // The view model indexes every version, not the stacks this pager pages through.
        allPhotos.indexOfFirst { it.id == currentPhoto?.id }.takeIf { it >= 0 }?.let(viewModel::setCurrentPhoto)
        currentColorSpace.value = null
    }

    // 在快到底部时加载更多系统照片
    LaunchedEffect(pagerState.currentPage, photos.size) {
        if (pagerState.currentPage >= photos.size - 5) {
            viewModel.loadCurrentTabMore()
        }
    }

    LaunchedEffect(viewModel.selectedTab, allPhotos, viewModel.currentPhotoIndex) {
        val target = allPhotos.getOrNull(viewModel.currentPhotoIndex) ?: return@LaunchedEffect
        showVersion(target)
        val targetIndex = stacks.tileIndexOf(target)
        if (targetIndex in photos.indices && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(photos.size, isExpanded) {
        // 仅在分屏模式且照片数量增加（通常是新拍摄）时才自动跳到第一张
        if (isExpanded == true && photos.size > lastPhotosCount) {
            pagerState.scrollToPage(0)
        }
        lastPhotosCount = photos.size
    }

    // 当 photoId 提供时，确保在照片列表加载后自动跳转到该照片（仅执行一次）
    LaunchedEffect(photos, photoId) {
        if (photoId != null && initialJumpDone == false) {
            val index = allPhotos.firstOrNull { it.id == photoId }?.let { showVersion(it); stacks.tileIndexOf(it) } ?: -1
            if (index != -1) {
                // 即使 index == currentPage 也执行跳转，以应对 HorizontalPager 内部键位同步导致的索引偏移
                pagerState.scrollToPage(index)
                initialJumpDone = true
            }
        }
    }

    val isCurrentPhotoProcessing = currentPhoto?.id?.let(processingPhotos::containsKey) == true

    LaunchedEffect(currentPhoto?.id, isCurrentPhotoProcessing) {
        if (isCurrentPhotoProcessing) {
            isZoomed = false
            showDeleteDialog = false
            showInfoDialog = false
            showExportDialog = false
            showVideoExportConfirmDialog = false
            showMoreSheet = false
            showRenderAgainSheet = false
            showHdrStrengthPanel = false
        }
    }
    var renderAgainSource by remember(currentPhoto?.id) { mutableStateOf<RenderAgainSource?>(null) }
    // Also re-checked when the More sheet opens: a queued Pro photo becomes eligible once rendered.
    LaunchedEffect(currentPhoto?.id, showMoreSheet) {
        val photo = currentPhoto?.takeIf { it.isImage } ?: return@LaunchedEffect
        renderAgainSource = loadRenderAgainSource(context, photo.id)
    }

    LaunchedEffect(showVideoExportConfirmDialog, currentPhoto?.id) {
        if (!showVideoExportConfirmDialog || isCurrentPhotoProcessing || currentPhoto?.isVideo != true) return@LaunchedEffect
        selectedVideoExportOption = null
        videoExportOptions = emptyList()
        isLoadingVideoExportOptions = true
        try {
            videoExportOptions = viewModel.getVideoExportOptions(currentPhoto)
            selectedVideoExportOption = videoExportOptions.firstOrNull {
                it.resolution == VideoExportResolution.ORIGINAL && it.isSelectable
            }
        } finally {
            isLoadingVideoExportOptions = false
        }
    }
    val preparingEditPhotoId = viewModel.preparingEditPhotoId
    var hdrStrengthSliderValue by remember(currentPhoto?.id) {
        mutableFloatStateOf(currentPhoto?.let { viewModel.getManualHdrStrength(it) } ?: HdrGainmapStrength.DEFAULT)
    }
    LaunchedEffect(currentPhoto?.id, currentPhoto?.metadata?.hdrEffectStrength) {
        hdrStrengthSliderValue = currentPhoto?.let { viewModel.getManualHdrStrength(it) } ?: HdrGainmapStrength.DEFAULT
    }
    LaunchedEffect(currentPhoto?.id) {
        showHdrStrengthPanel = false
    }
    val isCurrentRawPhoto = !isCurrentPhotoProcessing && currentPhoto?.let {
        it.isImage && (viewModel.selectedTab == GalleryTab.PHOTON || it.relatedPhoto != null) && viewModel.isRaw(it.id)
    } == true
    var displayPhotoSize by remember(currentPhoto?.id) { mutableLongStateOf(currentPhoto?.size ?: 0L) }

    LaunchedEffect(currentPhoto?.id, currentPhoto?.size, currentPhoto?.uri, currentPhoto?.sourceUri, isCurrentPhotoProcessing) {
        if (isCurrentPhotoProcessing) return@LaunchedEffect
        val photo = currentPhoto ?: return@LaunchedEffect
        displayPhotoSize = photo.size
        if (displayPhotoSize > 0L) return@LaunchedEffect

        var resolvedSize = withContext(Dispatchers.IO) {
            resolveCurrentMediaSize(context, photo)
        }
        if (resolvedSize <= 0L && viewModel.awaitPreparedPhotoReady(photo)) {
            resolvedSize = withContext(Dispatchers.IO) {
                maxOf(
                    resolveCurrentMediaSize(context, photo),
                    viewModel.getInternalPhotoSize(photo.id)
                )
            }
        }
        if (resolvedSize > 0L) {
            displayPhotoSize = resolvedSize
        }
    }

    fun leaveDetail(onNavigate: () -> Unit) {
        viewModel.requestGalleryScrollToPhoto(photos.getOrNull(pagerState.currentPage)?.id)
        onNavigate()
    }

    BackHandler(enabled = !isExpanded) {
        leaveDetail(onBack)
    }
    // Immersive like the editor; a split-screen pane leaves the system bars alone.
    if (!isExpanded) HideNavigationBar()

    val haptics = remember(context) { SpacedBuzz(VibrationHelper(context)) }
    fun explain(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // The position shows while the pager moves and for a moment after, then fades.
    var showCount by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            showCount = true
        } else {
            delay(700)
            showCount = false
        }
    }
    val countAlpha by animateFloatAsState(
        if (showCount) 1f else 0f, tween(if (showCount) 100 else 250), label = "countAlpha"
    )

    // A tap on the LIVE tag plays the shown stack's motion once.
    var liveTagStack by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentPhoto?.id) { liveTagStack = null }
    LaunchedEffect(liveTagStack) {
        // shortcut: a motion that never loops back (its clip is missing) is let go after 10 s; read the clip length if that shows.
        if (liveTagStack != null) {
            delay(10_000)
            liveTagStack = null
        }
    }
    // ¼× or 1× as chosen on the shown video's player; null until chosen, when 120 fps clips play slowed.
    var videoSlowedChoice by remember(videoPreviewHandle.player) { mutableStateOf<Boolean?>(null) }
    // On the system tab the tags describe the Lilac photo an exported copy came from.
    val tagPhoto = currentPhoto?.let { if (viewModel.selectedTab == GalleryTab.SYSTEM) it.relatedPhoto ?: it else it }
    val tagIsRaw by produceState(false, currentPhoto?.id) {
        value = false
        val photo = currentPhoto?.takeIf { it.isImage } ?: return@produceState
        value = withContext(Dispatchers.IO) { viewModel.isRawMedia(photo) }
    }
    // The shown burst's frames, oldest first, and the cover among them: the frame the photo's own file is a copy of.
    var burstReload by remember { mutableIntStateOf(0) }
    val burst by produceState(BurstFrames(), currentPhoto?.id, currentPhoto?.id?.let { viewModel.photoRefreshKeys[it] }, burstReload) {
        val photo = currentPhoto?.takeIf { it.isImage && it.isBurstPhoto }
        if (photo == null) {
            value = BurstFrames()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val files = GalleryManager.getBurstPhotos(context, photo.id)
            val main = GalleryManager.getPhotoFile(context, photo.id)
            BurstFrames(photo.id, files, if (main.exists()) files.indexOfFirst { it.length() == main.length() } else -1)
        }
    }
    // The frame shown per burst photo; none, or the cover, shows the burst photo itself.
    val burstFrame = remember { mutableStateMapOf<String, Int>() }
    // A burst that is also a stack shows its versions in the strip instead of its frames.
    val burstStrip = burst.photoId == currentPhoto?.id && burst.files.isNotEmpty() &&
        photos.getOrNull(pagerState.currentPage)?.let(stacks::versionsOf).orEmpty().size <= 1
    val shownFrameIndex = burst.photoId?.let { burstFrame[it] }
        ?.takeIf { burstStrip && it in burst.files.indices && it != burst.cover }
    val shownFrame = shownFrameIndex?.let(burst.files::get)
    // Burst frames export one by one, each decoded whole, as copies of the photo named burstN.
    fun exportBurstFrames(photo: MediaData, files: List<File>, indices: List<Int>, onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            var allSuccess = true
            indices.forEach { i ->
                val success = suspendCancellableCoroutine { continuation ->
                    val bitmap = BitmapFactory.decodeFile(files[i].path)
                    viewModel.exportPhoto(photo, bitmap, suffix = "burst${i + 1}") { continuation.resume(it) }
                }
                if (!success) allSuccess = false
            }
            withContext(Dispatchers.Main) { onDone(allSuccess) }
        }
    }
    val currentVideoRate by produceState<SlowedCopy.Rate?>(null, currentPhoto?.id) {
        value = null
        value = currentPhoto?.takeIf { it.isVideo }?.let { viewModel.videoRate(it) }
    }
    val canShowHdr = !isCurrentPhotoProcessing &&
        currentPhoto != null &&
        currentPhoto.isImage &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !DeviceUtil.isHarmonyOS &&
        viewModel.canToggleManualHdrEnhance(currentPhoto)

    // Swipe down leaves for the camera; only when not zoomed, so it never fights a pan.
    val swipeDown = remember { Animatable(0f) }
    val swipeDownEnabled = !isExpanded && !isZoomed && currentPhoto != null
    LaunchedEffect(swipeDownEnabled) { if (!swipeDownEnabled) swipeDown.snapTo(0f) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (isExpanded) Modifier.systemBarsPadding() else Modifier)
    ) {
        val screenWidth = maxWidth
        val fadeDistance = with(LocalDensity.current) { 160.dp.toPx() }
        // The rows fade as the page follows a downward swipe.
        val rowsFade = Modifier.graphicsLayer { alpha = (1f - swipeDown.value / fadeDistance).coerceIn(0f, 1f) }
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_photos),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
        } else {
                HorizontalPager(
                    state = pagerState,
                    // Keyed by stack: a re-render or a deleted version changes the tile, not the page.
                    key = { page -> if (page < photos.size) PhotoStacks.stackIdOf(photos[page]) else page },
                    // The swipe-down handler sits outside the page's moving layer, so it reads the finger, not the page.
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(swipeDownEnabled) {
                            if (!swipeDownEnabled) return@pointerInput
                            val leaveAt = 110.dp.toPx()
                            fun settle() { scope.launch { swipeDown.animateTo(0f, spring()) } }
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (swipeDown.value > leaveAt) leaveDetail(onGoToCamera ?: onBack)
                                    // Also when leaving: the camera may first ask about photos still stacking.
                                    settle()
                                },
                                onDragCancel = { settle() },
                            ) { change, dy ->
                                change.consume()
                                scope.launch { swipeDown.snapTo((swipeDown.value + dy).coerceAtLeast(0f)) }
                            }
                        }
                        .graphicsLayer { translationY = swipeDown.value },
                    userScrollEnabled = !isZoomed,
                    beyondViewportPageCount = 1
                ) { page ->
                    val photo = photos.getOrNull(page)?.let(::shownVersion)
                    if (photo != null) {
                        val processingPhoto = processingPhotos[photo.id]
                        val waitingForImage = processingPhoto != null && !processingPhoto.isDisplayReady
                        // Keyed by stack, so switching versions keeps the zoom and position.
                        key(PhotoStacks.stackIdOf(photo), waitingForImage) {
                            if (processingPhoto != null && waitingForImage) {
                                ProcessingPhotoPreview(processingPhoto, Modifier.fillMaxSize())
                            } else {

                                var showOrigin by remember { mutableStateOf(false) }
                                var isPlaying by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier.fillMaxSize().pointerInput(photo.id, photo.isImage, photo.isMotionPhoto) {
                                        if (!photo.isImage) return@pointerInput
                                        awaitPointerEventScope {
                                            while (true) {
                                                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                                if (downEvent.type == PointerEventType.Press && downEvent.changes.size == 1) {
                                                    // Twice the touch slop: a resting finger jitters; a real drag still cancels.
                                                    val touchSlop = viewConfiguration.touchSlop * 2
                                                    val initialPosition = downEvent.changes[0].position
                                                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                                    var upEvent: PointerEvent? = null
                                                    var isMultiTouch = false
                                                    var isMoved = false

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

                                                    if (!isMultiTouch && !isMoved && upEvent == null) {
                                                        if (photo.isMotionPhoto) {
                                                            isPlaying = true
                                                        } else {
                                                            showOrigin = true
                                                        }
                                                        while (true) {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            if (event.type == PointerEventType.Release || event.changes.size > 1) {
                                                                break
                                                            }
                                                        }
                                                        showOrigin = false
                                                        isPlaying = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    if (photo.isVideo) {
                                        VideoDetailPlayer(
                                            photo = photo,
                                            isActive = page == pagerState.currentPage &&
                                                !viewModel.isEditing &&
                                                !showVideoExportConfirmDialog &&
                                                !isVideoExporting,
                                            viewModel = viewModel,
                                            previewHandle = videoPreviewHandle,
                                            // Under the bars, which a tap hides as on photos.
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) { detectTapGestures(onTap = { overlaysHidden = !overlaysHidden }) }
                                        )
                                    } else {
                                        val forceSystemOrigin = viewModel.selectedTab == GalleryTab.SYSTEM &&
                                            photo.relatedPhoto == null
                                        ZoomableImage(
                                            photo = photo,
                                            frame = if (page == pagerState.currentPage) shownFrame else null,
                                            colorSpace = currentColorSpace,
                                            showOrigin = showOrigin || forceSystemOrigin,
                                            isActive = page == pagerState.currentPage,
                                            isScrollInProgress = pagerState.isScrollInProgress,
                                            viewModel = viewModel,
                                            onTap = { overlaysHidden = !overlaysHidden },
                                            onZoomChange = { zoomed ->
                                                if (page == pagerState.currentPage) {
                                                    isZoomed = zoomed
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        MotionPhotoPlayer(
                                            photo = photo,
                                            isPlaying = isPlaying || liveTagStack == PhotoStacks.stackIdOf(photo),
                                            onLoop = { liveTagStack = null },
                                            viewModel = viewModel,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
        val currentVersions = photos.getOrNull(pagerState.currentPage)?.let(stacks::versionsOf).orEmpty()

        // Top row: the grid, the position beside it, the tags centred on the whole width, and a slot on the right.
        if (currentPhoto != null && tagPhoto != null && !overlaysHidden) {
            Box(Modifier.fillMaxWidth().height(ViewerTopRowHeight).then(rowsFade)) {
                if (onGoToGallery != null) {
                    Box(
                        Modifier
                            .size(ViewerTopRowHeight)
                            .clickable {
                                haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
                                leaveDetail(onGoToGallery)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ShadowedLineIcon(AppIcons.GridLine, 22.dp, SonyText)
                    }
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = SonyText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    style = LocalTextStyle.current.copy(shadow = ViewfinderTextShadow),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = ViewerTopRowHeight)
                        .graphicsLayer { alpha = countAlpha }
                )
                val tags = viewerTags(
                    context = context,
                    photo = tagPhoto,
                    version = currentPhoto,
                    versions = currentVersions,
                    isRaw = tagIsRaw,
                    burstCount = if (isCurrentPhotoProcessing || burst.photoId != currentPhoto.id) 0 else burst.files.size,
                    canPlayLive = !isCurrentPhotoProcessing && currentPhoto.isImage && currentPhoto.isMotionPhoto,
                    livePlaying = liveTagStack != null,
                    hdrOn = if (canShowHdr) viewModel.isManualHdrEnhanceEnabled(currentPhoto) else null,
                    videoRate = currentVideoRate,
                    videoSlowed = (videoSlowedChoice ?: currentVideoRate?.isHighFrameRate) == true,
                    onLive = { liveTagStack = PhotoStacks.stackIdOf(currentPhoto) },
                    onHdr = { showHdrStrengthPanel = !showHdrStrengthPanel },
                    explain = ::explain,
                    accent = com.hinnka.mycamera.ui.camera.ModeAmber,
                )
                // The right slot (the rating's place) stays empty, so the tags keep to the centre.
                ViewerTagRow(
                    tags,
                    Modifier
                        .align(Alignment.Center)
                        .widthIn(max = screenWidth - ViewerTopRowHeight * 2)
                )
            }
        }

        // Item row, above the actions: the version strip, and the render chip (above the strip when both show).
        // In full view the strip stays, dimmed and lower, so versions can still be switched.
        val stripBottom by animateDpAsState(
            if (overlaysHidden) VersionStripRest - FullViewStripDrop else VersionStripRest,
            label = "stripBottom"
        )
        val stripAlpha by animateFloatAsState(if (overlaysHidden) 0.6f else 1f, label = "stripAlpha")
        val hasStrip = currentPhoto != null && (currentVersions.size > 1 || burstStrip)
        if (currentPhoto != null && currentVersions.size > 1) {
            val renderPendingIds by RenderQueue.pendingPhotoIds.collectAsState()
            val renderStatus by RenderQueue.status.collectAsState()
            val runningIds = processingPhotos.keys +
                listOfNotNull(renderStatus.currentPhotoId.takeIf { renderStatus.running })
            PhotoVersionStrip(
                versions = currentVersions,
                selectedId = currentPhoto.id,
                runningIds = runningIds,
                waitingIds = renderPendingIds - runningIds,
                viewModel = viewModel,
                onSelect = ::showVersion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = stripBottom)
                    .alpha(stripAlpha)
                    .then(rowsFade)
            )
        } else if (currentPhoto != null && burstStrip) {
            val burstId = currentPhoto.id
            val selectedFrame = shownFrameIndex ?: burst.cover
            PhotoStrip(
                items = burst.files,
                selectedIndex = selectedFrame,
                key = { it.name },
                onSelect = { if (it == burst.cover) burstFrame.remove(burstId) else burstFrame[burstId] = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = stripBottom)
                    .alpha(stripAlpha)
                    .then(rowsFade)
            ) { index, file ->
                AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (index == burst.cover) StripLabel("★", com.hinnka.mycamera.ui.camera.ModeAmber) else StripLabel("${index + 1}")
                if (index == selectedFrame) StripRing(EdgeRingStyle.SOLID, SonyText)
            }
        }
        if (currentPhoto != null && !overlaysHidden && viewModel.selectedTab == GalleryTab.PHOTON) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (hasStrip) VersionStripRest + VersionStripHeight else ViewerActionRowHeight)
                    .then(if (hasStrip) Modifier else Modifier.height(ViewerItemRowHeight))
                    .then(rowsFade),
                contentAlignment = Alignment.Center
            ) {
                RenderQueuePhotoChip(
                    photoId = currentPhoto.id,
                    onClick = onOpenRenderQueue,
                    processingInCamera = isCurrentPhotoProcessing,
                )
            }
        }

        val videoPlayer = videoPreviewHandle.player
        if (currentPhoto != null && currentPhoto.isVideo && videoPlayer != null) {
            key(currentPhoto.id, videoPlayer) {
                VideoTransport(
                    player = videoPlayer,
                    photo = currentPhoto,
                    rate = currentVideoRate,
                    slowed = (videoSlowedChoice ?: currentVideoRate?.isHighFrameRate) == true,
                    onSlowed = { videoSlowedChoice = it },
                    barsShown = !overlaysHidden,
                    keepsSlowPart = viewModel.selectedTab == GalleryTab.PHOTON,
                    explain = ::explain,
                    fade = rowsFade,
                )
            }
        }

        // Action row on the camera's 70 dp grid: back to where the viewer came from, Share, Edit, Delete, More.
        if (!overlaysHidden) {
            val inCamera = stringResource(R.string.viewer_busy_in_camera)
            val busy = stringResource(R.string.viewer_busy)
            val noPhoto = stringResource(R.string.no_photos)
            // Why a control does nothing right now, or null when it works.
            fun whyNot(working: Boolean = false): String? = when {
                currentPhoto == null -> noPhoto
                isCurrentPhotoProcessing -> inCamera
                working -> busy
                else -> null
            }
            val actions = listOf(
                if (isExpanded) null else ViewerAction(
                    icon = if (fromGrid) AppIcons.GridLine else AppIcons.CameraLine,
                    label = stringResource(if (fromGrid) R.string.gallery else R.string.camera),
                    whyNot = null,
                    onClick = { leaveDetail(onBack) }
                ),
                ViewerAction(AppIcons.ShareLine, stringResource(R.string.share), whyNot(isSharing)) {
                    currentPhoto?.let(viewModel::sharePhoto)
                },
                if (currentPhoto?.isImage == true || currentPhoto?.isVideo == true) {
                    val editWhyNot = whyNot(preparingEditPhotoId == currentPhoto.id)
                        ?: shownFrame?.let { stringResource(R.string.viewer_burst_edit_cover) }
                    ViewerAction(AppIcons.AdjustmentsLine, stringResource(R.string.edit), editWhyNot) {
                        viewModel.prepareCurrentPhotoForEdit(
                            index = allPhotos.indexOf(currentPhoto),
                            onReady = onEdit,
                            onFailure = {
                                Toast.makeText(context, R.string.gallery_prepare_edit_failed, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } else null,
                ViewerAction(AppIcons.TrashLine, stringResource(R.string.delete), whyNot()) { showDeleteDialog = true },
                ViewerAction(AppIcons.DotsLine, stringResource(R.string.viewer_more), whyNot()) { showMoreSheet = true },
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(ViewerActionRowHeight)
                    .then(rowsFade)
            ) {
                actions.forEachIndexed { slot, action ->
                    if (action == null) return@forEachIndexed
                    // Slots centred at x 40, 110, 180, 250, 320 of a 360 dp width.
                    Box(
                        Modifier
                            .offset(x = screenWidth * ((40f + 70f * slot) / 360f) - ViewerActionSlotWidth / 2)
                            .width(ViewerActionSlotWidth)
                            .fillMaxHeight()
                            .clickable(role = Role.Button) {
                                val why = action.whyNot
                                if (why != null) {
                                    explain(why)
                                } else {
                                    haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
                                    action.onClick()
                                }
                            }
                    ) {
                        LabelledIcon(
                            icon = action.icon,
                            label = action.label,
                            tint = SonyText,
                            shadowed = true,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 11.dp)
                                .alpha(if (action.whyNot == null) 1f else 0.38f)
                                .autoRotate()
                        )
                    }
                }
            }
        }

        if (!overlaysHidden && showHdrStrengthPanel && canShowHdr && currentPhoto != null) {
            HdrStrengthPanel(
                enabled = viewModel.isManualHdrEnhanceEnabled(currentPhoto),
                strength = hdrStrengthSliderValue,
                onEnabledChange = {
                    viewModel.toggleManualHdrEnhance(currentPhoto) { success ->
                        if (!success) {
                            Toast.makeText(context, R.string.hdr_toggle_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onStrengthChange = { hdrStrengthSliderValue = it },
                onStrengthChangeFinished = {
                    viewModel.setManualHdrStrength(currentPhoto, hdrStrengthSliderValue) { success ->
                        if (!success) {
                            Toast.makeText(context, R.string.hdr_strength_update_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                // Under the top row, below the HDR tag.
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = ViewerTopRowHeight)
            )
        }
    }

    var deleteExportedPref by rememberPreference(UserPreferences::deleteExported)

    // 删除确认对话框
    if (showDeleteDialog && !isCurrentPhotoProcessing) {
        val exportedPhotosCount = remember(currentPhoto, currentPhoto?.metadata, currentPhoto?.metadata?.exportedUris) {
            val baseCount = currentPhoto?.metadata?.exportedUris?.size ?: 0
            val sourceUri = currentPhoto?.metadata?.sourceUri
            val isVideoAndCaptured = currentPhoto?.isVideo == true &&
                    currentPhoto.metadata?.isImported != true &&
                    !sourceUri.isNullOrBlank()
            baseCount + (if (isVideoAndCaptured) 1 else 0)
        }
        var deleteExportedState by remember(showDeleteDialog) { mutableStateOf(deleteExportedPref) }
        val deleteVersions = currentPhoto?.let(stacks::versionsOf).orEmpty()

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_confirm))
                    if (viewModel.selectedTab == GalleryTab.PHOTON) {
                        if (exportedPhotosCount > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteExportedState = !deleteExportedState }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = deleteExportedState,
                                    onCheckedChange = { deleteExportedState = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AccentColor,
                    checkmarkColor = OnAccentColor,
                                        uncheckedColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentPhoto?.isVideo == true) {
                                        stringResource(R.string.delete_exported_videos_count, exportedPhotosCount)
                                    } else {
                                        stringResource(R.string.delete_exported_photos_count, exportedPhotosCount)
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                fun resolveDeleteExported(): Boolean {
                    if (viewModel.selectedTab == GalleryTab.PHOTON && exportedPhotosCount > 0) {
                        deleteExportedPref = deleteExportedState
                        return deleteExportedState
                    }
                    return true
                }
                if (deleteVersions.size > 1) {
                    DeleteVersionsButtons(
                        thisVersionText = stringResource(R.string.delete_this_version),
                        allVersionsText = pluralStringResource(R.plurals.delete_all_versions, deleteVersions.size, deleteVersions.size),
                        onDeleteThis = {
                            currentPhoto?.let { photo ->
                                // The view model keeps its index after a delete, so point it at the next newer
                                // version first; the newest one leaves the next older in its place. Either stays in this stack.
                                deleteVersions.getOrNull(deleteVersions.indexOf(photo) + 1)?.let { newer ->
                                    showVersion(newer)
                                    viewModel.setCurrentPhoto(allPhotos.indexOf(newer))
                                }
                                viewModel.requestDeletePhoto(photo, resolveDeleteExported())
                            }
                            showDeleteDialog = false
                        },
                        onDeleteAll = {
                            viewModel.deleteSelectedPhotos(
                                resolveDeleteExported(),
                                deleteVersions.filterNot { it.id in processingPhotos }
                            )
                            showDeleteDialog = false
                        },
                        onCancel = { showDeleteDialog = false },
                    )
                } else if (burstStrip) {
                    DeleteVersionsButtons(
                        thisVersionText = stringResource(R.string.viewer_delete_this_frame),
                        allVersionsText = stringResource(R.string.viewer_delete_whole_burst),
                        onDeleteThis = {
                            val frame = shownFrame
                            if (frame == null) {
                                explain(context.getString(R.string.viewer_burst_cover_delete))
                            } else {
                                scope.launch {
                                    withContext(Dispatchers.IO) { frame.delete() }
                                    burstReload++
                                }
                            }
                            showDeleteDialog = false
                        },
                        onDeleteAll = {
                            currentPhoto?.let { viewModel.requestDeletePhoto(it, resolveDeleteExported()) }
                            showDeleteDialog = false
                        },
                        onCancel = { showDeleteDialog = false },
                    )
                } else {
                    TextButton(
                        onClick = {
                            currentPhoto?.let { viewModel.requestDeletePhoto(it, resolveDeleteExported()) }
                            showDeleteDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = Color.Red)
                    }
                }
            },
            dismissButton = {
                if (deleteVersions.size <= 1 && !burstStrip) {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
            containerColor = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // 导出确认对话框
    if (showExportDialog && !isCurrentPhotoProcessing) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(if (exportAllFrames) R.string.viewer_export_all_frames else R.string.export)) },
            text = {
                Text(stringResource(if (exportAllFrames) R.string.export_all_confirm else R.string.export_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        currentPhoto?.let {
                            isSaving = true
                            val all = exportAllFrames
                            val onDone = { success: Boolean ->
                                isSaving = false
                                if (success) {
                                    Toast.makeText(context, if (all) R.string.export_all_success else R.string.export_success, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                            when {
                                all -> exportBurstFrames(it, burst.files, burst.files.indices.toList(), onDone)
                                shownFrameIndex != null -> exportBurstFrames(it, burst.files, listOf(shownFrameIndex), onDone)
                                else -> viewModel.exportPhoto(it, onComplete = onDone)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.export), color = AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // 视频导出确认对话框
    if (showVideoExportConfirmDialog && !isCurrentPhotoProcessing) {
        AlertDialog(
            onDismissRequest = {
                showVideoExportConfirmDialog = false
                selectedVideoExportOption = null
            },
            title = { Text(stringResource(R.string.export_video)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.export_video_select_resolution))
                    if (isLoadingVideoExportOptions) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        videoExportOptions.forEach { option ->
                            val selectable = option.isSelectable && !isVideoExporting
                            val statusText = when (option.support) {
                                VideoExportSupport.SUPPORTED -> null
                                VideoExportSupport.MAY_FAIL -> R.string.export_video_resolution_may_fail
                                VideoExportSupport.UNSUPPORTED -> R.string.export_video_resolution_unsupported
                                VideoExportSupport.SOURCE_TOO_SMALL -> R.string.export_video_resolution_source_too_small
                            }
                            val statusColor = when (option.support) {
                                VideoExportSupport.SUPPORTED -> MaterialTheme.colorScheme.primary
                                VideoExportSupport.MAY_FAIL -> WarningYellow
                                VideoExportSupport.UNSUPPORTED,
                                VideoExportSupport.SOURCE_TOO_SMALL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = selectable) {
                                        selectedVideoExportOption = option
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedVideoExportOption == option,
                                    onClick = { selectedVideoExportOption = option },
                                    enabled = selectable,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = videoExportResolutionLabel(option.resolution),
                                        color = if (selectable) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        },
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = if (statusText == null) {
                                            stringResource(
                                                R.string.export_video_resolution_dimensions,
                                                option.outputWidth,
                                                option.outputHeight,
                                            )
                                        } else {
                                            stringResource(
                                                R.string.export_video_resolution_details,
                                                option.outputWidth,
                                                option.outputHeight,
                                                stringResource(statusText),
                                            )
                                        },
                                        color = statusColor,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.export_video_confirm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedVideoExportOption != null && !isLoadingVideoExportOptions,
                    onClick = {
                        val exportOption = selectedVideoExportOption ?: return@TextButton
                        showVideoExportConfirmDialog = false
                        selectedVideoExportOption = null
                        currentPhoto?.let { photo ->
                            viewModel.exportVideo(photo, exportOption) { success, _ ->
                                val msgRes = if (success) R.string.export_video_success else R.string.export_video_failed
                                Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.export_video))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVideoExportConfirmDialog = false
                    selectedVideoExportOption = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    HighFrameRateChoiceDialog(viewModel)

    // 照片信息对话框
    if (showInfoDialog && currentPhoto != null && !isCurrentPhotoProcessing) {
        val infoPhoto = currentPhoto.relatedPhoto ?: currentPhoto
        val infoMetadata = infoPhoto.metadata ?: currentPhoto.metadata
        // The full look of this version; the badge only shows what sets it apart. The original's look is in its render job.
        val versionInfo by produceState<String?>(null, currentPhoto.id) {
            val metadata = currentPhoto.metadata
            value = when {
                PhotoStacks.isCameraJpeg(metadata) -> context.getString(R.string.photo_info_version_camera_jpeg)
                metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) != null ->
                    metadata.customProperties[RenderLook.LABEL_PROPERTY]
                metadata != null -> withContext(Dispatchers.IO) {
                    // Only Pro photos rendered in the queue have a job; reading a missing one logs an error.
                    val jobFile = java.io.File(RenderJob.photoDir(context, currentPhoto.id), RenderJob.JOB_FILE)
                    if (jobFile.isFile) RenderJob.read(context, currentPhoto.id) else null
                }
                    ?.let { job -> withContext(Dispatchers.IO) { context.getString(R.string.photo_info_version_original, RenderLook.fromPhoto(metadata, job).label(context)) } }
                else -> null
            }
        }
        // What this version's folder really holds, so the panel never names data that was not kept.
        val savedFiles by produceState<List<java.io.File>>(emptyList(), currentPhoto.id) {
            value = withContext(Dispatchers.IO) { GalleryManager.savedPhotoFiles(context, currentPhoto.id) }
        }
        var savedExpanded by remember(currentPhoto.id) { mutableStateOf(false) }
        var hdrExpanded by remember(currentPhoto.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(if (currentPhoto.isVideo) R.string.video_info else R.string.photo_info)) },
            text = {
                Column {
                    if (viewModel.selectedTab == GalleryTab.SYSTEM) {
                        InfoRow(stringResource(R.string.name), currentPhoto.displayName)
                    }
                    InfoRow(stringResource(R.string.photo_info_date), currentPhoto.getFormattedDate())
                    InfoRow(stringResource(R.string.photo_info_resolution), currentPhoto.getResolution())
                    InfoRow(stringResource(R.string.photo_info_size), currentPhoto.copy(size = displayPhotoSize).getFormattedSize())
                    versionInfo?.let { InfoRow(stringResource(R.string.photo_info_version), it) }
                    if (!currentPhoto.isVideo) {
                        val names = savedFiles.map { it.name }
                        val mainType = when {
                            "original.heic" in names -> "HEIC"
                            "original.jxl" in names -> "JPEG XL"
                            "original.jpg" in names -> "JPEG"
                            else -> currentPhoto.mimeType?.substringAfter('/')?.uppercase()
                        }
                        mainType?.let { type ->
                            InfoRow(
                                stringResource(R.string.photo_info_file_type),
                                if ("original.dng" in names) stringResource(R.string.photo_info_file_type_from_raw, type) else type,
                            )
                        }
                        val frameMetadata = currentPhoto.metadata
                        frameMetadata?.multipleExposureFrameCount?.takeIf { it > 1 }?.let { count ->
                            InfoRow(
                                stringResource(R.string.photo_info_frames),
                                stringResource(
                                    if (frameMetadata.captureMode == "multiple_exposure") {
                                        R.string.photo_info_frames_multiple_exposure
                                    } else {
                                        R.string.photo_info_frames_stacked
                                    },
                                    count,
                                ),
                            )
                        }
                        frameMetadata?.customProperties?.let { props ->
                            val used = RawPhotonHdrMetadata.read(props) ?: return@let
                            InfoCollapsibleHeader(
                                stringResource(R.string.photo_info_hdr, used),
                                hdrExpanded,
                            ) { hdrExpanded = !hdrExpanded }
                            if (hdrExpanded) {
                                InfoSubRow(stringResource(R.string.photo_info_hdr_used, used))
                                RawPhotonHdrMetadata.readWanted(props)?.let {
                                    InfoSubRow(stringResource(R.string.photo_info_hdr_wanted, it))
                                }
                                RawPhotonHdrMetadata.readLimit(props)?.let {
                                    InfoSubRow(stringResource(R.string.photo_info_hdr_limit, it))
                                }
                                RawPhotonHdrMetadata.readInputExposureEv(props)?.let {
                                    InfoSubRow(stringResource(R.string.photo_info_hdr_viewfinder, it))
                                }
                            }
                        }
                        val extras = savedFiles
                            .filter { it.name !in MAIN_PHOTO_FILES && !it.name.endsWith(".tmp") }
                            .sortedBy { it.name }
                        if (extras.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { savedExpanded = !savedExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.photo_info_saved_with),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (savedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                )
                            }
                            if (savedExpanded) {
                                extras.forEach { file ->
                                    Text(
                                        text = "${savedFileLabel(context, file.name)} · " +
                                            android.text.format.Formatter.formatShortFileSize(context, file.length()),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    infoMetadata?.let {
                        if (currentPhoto.isVideo) {
                            InfoRow(stringResource(R.string.video_info_duration), currentPhoto.getFormattedDuration())
                            InfoRow(stringResource(R.string.video_info_mime), it.mimeType ?: (currentPhoto.mimeType ?: "N/A"))
                            InfoRow(stringResource(R.string.video_info_frame_rate), it.frameRate?.toString() ?: "N/A")
                            InfoRow(stringResource(R.string.video_info_bitrate), it.bitrate?.let { bitrate -> "${bitrate / 1000} kbps" } ?: "N/A")
                            InfoRow(stringResource(R.string.video_info_has_audio), it.hasAudio?.let { hasAudio -> if (hasAudio) stringResource(R.string.yes) else stringResource(R.string.no) } ?: "N/A")
                            InfoRow(stringResource(R.string.video_info_rotation), it.rotationDegrees?.toString() ?: "N/A")
                        } else {
                            InfoRow(stringResource(R.string.photo_info_focal_length), it.focalLength35mm ?: "N/A")
                            InfoRow(stringResource(R.string.photo_info_aperture), it.aperture ?: "N/A")
                            InfoRow(stringResource(R.string.photo_info_iso), it.iso?.toString() ?: "N/A")
                            InfoRow(stringResource(R.string.photo_info_shutter_speed), it.shutterSpeed ?: "N/A")
                            InfoRow("LV", "%.2f".format(it.lv))
                            InfoRow(stringResource(R.string.photo_info_brightness), "%.2f".format(viewModel.currentBrightness[currentPhoto.id]))
                        }
                    }
                    currentColorSpace.value?.let {
                        InfoRow(stringResource(R.string.color_space), it.name)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // 更多 BottomSheet
    if (showMoreSheet && currentPhoto != null && !isCurrentPhotoProcessing) {
        val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val exportAllIcon = ImageVector.vectorResource(R.drawable.ic_output_all)
        val moreActions = buildList {
            add(
                GalleryMoreAction(
                    icon = AppIcons.InfoLine,
                    text = context.getString(if (currentPhoto.isVideo) R.string.video_info else R.string.photo_info),
                    onClick = {
                        showMoreSheet = false
                        showInfoDialog = true
                    }
                )
            )
            if (currentPhoto.isImage && (viewModel.selectedTab == GalleryTab.PHOTON || currentPhoto.relatedPhoto != null)) {
                add(
                    GalleryMoreAction(
                        icon = AppIcons.Output,
                        text = context.getString(R.string.export),
                        isLoading = isSaving,
                        enabled = !isCopyingSettings && !isPastingSettings,
                        onClick = {
                            exportAllFrames = false
                            showExportDialog = true
                        }
                    )
                )
            }
            if (burstStrip) {
                add(
                    GalleryMoreAction(
                        icon = AppIcons.StarBorder,
                        text = context.getString(R.string.burst_use_as_cover),
                        onClick = {
                            val frame = shownFrame
                            if (frame == null) {
                                explain(context.getString(R.string.viewer_burst_already_cover))
                            } else {
                                showMoreSheet = false
                                viewModel.setMainBurstPhoto(currentPhoto, frame) { success ->
                                    explain(context.getString(if (success) R.string.burst_cover_set else R.string.burst_cover_failed))
                                }
                            }
                        }
                    )
                )
                add(
                    GalleryMoreAction(
                        icon = exportAllIcon,
                        text = context.getString(R.string.viewer_export_all_frames),
                        isLoading = isSaving,
                        enabled = !isCopyingSettings && !isPastingSettings,
                        onClick = {
                            exportAllFrames = true
                            showExportDialog = true
                        }
                    )
                )
            }

            if (currentPhoto.isVideo) {
                add(
                    GalleryMoreAction(
                        icon = AppIcons.Output,
                        text = if (isVideoExporting && videoExportProgress > 0) {
                            "$videoExportProgress%"
                        } else {
                            context.getString(R.string.export)
                        },
                        isLoading = isVideoExporting,
                        enabled = !isCopyingSettings && !isPastingSettings,
                        onClick = {
                            if (isVideoTransformerExportSupported()) {
                                videoPreviewHandle.release()
                                showVideoExportConfirmDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.export_video_requires_android12,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                )
            }

            add(
                GalleryMoreAction(
                    icon = AppIcons.ContentCopy,
                    text = context.getString(R.string.copy_settings),
                    isLoading = isCopyingSettings,
                    enabled = !isCopyingSettings &&
                        !isPastingSettings &&
                        !isSharing &&
                        !isSaving,
                    onClick = {
                        isCopyingSettings = true
                        viewModel.copyPhotoSettings(currentPhoto) { success ->
                            isCopyingSettings = false
                            if (success) {
                                showMoreSheet = false
                            }
                            Toast.makeText(
                                context,
                                if (success) {
                                    R.string.copy_settings_success
                                } else {
                                    R.string.copy_settings_failed
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            )

            add(
                GalleryMoreAction(
                    icon = AppIcons.ContentCopy,
                    text = context.getString(R.string.paste_settings),
                    isLoading = isPastingSettings,
                    enabled = hasCopiedEditSettings &&
                        !isCopyingSettings &&
                        !isPastingSettings &&
                        !isSharing &&
                        !isSaving,
                    onClick = {
                        isPastingSettings = true
                        viewModel.pasteCopiedSettingsToPhoto(currentPhoto) { success ->
                            isPastingSettings = false
                            if (success) {
                                showMoreSheet = false
                            }
                            Toast.makeText(
                                context,
                                if (success) {
                                    R.string.paste_settings_success
                                } else {
                                    R.string.paste_settings_failed
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            )

            if (currentPhoto.isImage && viewModel.isRaw(currentPhoto.id)) {
                val isRefreshing = viewModel.refreshingPhotos.contains(currentPhoto.id)
                add(
                    GalleryMoreAction(
                        icon = Icons.Default.Refresh,
                        text = context.getString(R.string.viewer_refresh_preview),
                        isLoading = isRefreshing,
                        enabled = !isRefreshing,
                        onClick = {
                            showMoreSheet = false
                            viewModel.refreshRawPreview(currentPhoto) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) R.string.refresh_success else R.string.refresh_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                )
            }

            if (isCurrentRawPhoto) {
                add(
                    GalleryMoreAction(
                        iconText = context.getString(R.string.dng_format),
                        text = context.getString(R.string.dng_format),
                        isLoading = isExportingDng,
                        enabled = !isSaving &&
                            !isExportingDng &&
                            !isCopyingSettings &&
                            !isPastingSettings,
                        onClick = {
                            showMoreSheet = false
                            isExportingDng = true
                            viewModel.exportDng(currentPhoto) { success ->
                                isExportingDng = false
                                Toast.makeText(
                                    context,
                                    if (success) R.string.export_dng_success else R.string.export_dng_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                )
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        AppModalBottomSheet(
            containerColor = GallerySheetSurface,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            scrimColor = Color.Black.copy(alpha = 0.64f),
            onDismissRequest = { showMoreSheet = false },
            sheetState = moreSheetState,
            contentColor = GalleryToolbarContent,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    width = 36.dp,
                    height = 4.dp,
                    color = GalleryToolbarContent.copy(alpha = 0.24f)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        text = stringResource(R.string.more_options),
                        color = GalleryToolbarContent,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = GalleryToolbarContent.copy(alpha = 0.09f))
                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    moreActions.chunked(4).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowActions.forEach { action ->
                                GalleryMoreActionCard(
                                    action = action,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowActions.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (renderAgainSource != null) {
                    // Busy like the tiles above while an export, DNG export or settings copy/paste runs.
                    val renderAgainEnabled = !isSaving && !isExportingDng && !isCopyingSettings && !isPastingSettings
                    Spacer(modifier = Modifier.height(14.dp))
                    PhysicalButton(
                        enabled = renderAgainEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .alpha(if (renderAgainEnabled) 1f else 0.38f)
                            .shadow(
                                elevation = 4.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.15f),
                                spotColor = Color.Black.copy(alpha = 0.15f)
                            ),
                        onClick = {
                            showMoreSheet = false
                            showRenderAgainSheet = true
                        },
                        shape = CircleShape,
                        backgroundColor = GalleryToolbarButton
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Tune,
                                contentDescription = null,
                                tint = GalleryToolbarContent,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.render_again),
                                color = GalleryToolbarContent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = GalleryToolbarContent.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val renderAgainPhoto = currentPhoto
    val renderAgainFrom = renderAgainSource
    if (showRenderAgainSheet && renderAgainPhoto != null && renderAgainFrom != null) {
        RenderAgainSheet(
            title = stringResource(R.string.render_again),
            confirmText = stringResource(R.string.render_again_render),
            source = renderAgainFrom,
            appLook = remember(renderAgainFrom) { appRenderLook(context, renderAgainFrom.metadata.cameraId) },
            isBusy = isRenderingAgain,
            onRender = { look ->
                isRenderingAgain = true
                scope.launch {
                    val message = when (RenderQueue.renderAgain(context, renderAgainPhoto.id, look)) {
                        is RenderQueue.RenderAgainResult.Queued -> {
                            showRenderAgainSheet = false
                            R.string.render_again_queued
                        }
                        RenderQueue.RenderAgainResult.SameLook -> R.string.render_again_same
                        RenderQueue.RenderAgainResult.NotEligible -> R.string.render_again_not_eligible
                        RenderQueue.RenderAgainResult.Failed -> R.string.render_again_failed
                    }
                    isRenderingAgain = false
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onDismissRequest = { showRenderAgainSheet = false }
        )
    }
}

@Composable
private fun HdrStrengthPanel(
    enabled: Boolean,
    strength: Float,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onStrengthChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x881E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.width(200.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hdr_label),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccentColor,
                        checkedTrackColor = AccentColor,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.hdr_strength_label),
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 9.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(strength * 100f).roundToInt()}%",
                        color = AccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
                CustomSlider(
                    value = strength,
                    onValueChange = onStrengthChange,
                    valueRange = HdrGainmapStrength.MIN..HdrGainmapStrength.MAX,
                    enabled = enabled,
                    onValueChangeFinished = onStrengthChangeFinished,
                )
            }
        }
    }
}

private data class GalleryMoreAction(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val iconText: String? = null,
    val enabled: Boolean = true,
    val contentColor: Color = GalleryToolbarContent,
    val isLoading: Boolean = false
)

@Composable
private fun GalleryMoreActionCard(
    action: GalleryMoreAction,
    modifier: Modifier = Modifier
) {
    val isInteractive = action.enabled && !action.isLoading
    Column(
        modifier = modifier.alpha(if (action.enabled || action.isLoading) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhysicalButton(
            modifier = Modifier
                .size(44.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.15f),
                    spotColor = Color.Black.copy(alpha = 0.15f)
                ),
            onClick = action.onClick,
            enabled = isInteractive,
            shape = CircleShape,
            backgroundColor = GalleryToolbarButton
        ) {
            if (action.isLoading) {
                CircularProgressIndicator(
                    color = action.contentColor,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 1.8.dp
                )
            } else if (action.icon != null) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.text,
                    tint = action.contentColor,
                    modifier = Modifier.size(19.dp)
                )
            } else if (action.iconText != null) {
                Text(
                    text = action.iconText,
                    color = action.contentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = action.text,
            color = GalleryToolbarContent.copy(alpha = 0.86f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val MAIN_PHOTO_FILES = setOf("original.jpg", "original.heic", "original.jxl")

/** A plain name for a file kept in a photo's folder; unknown files show their file name. */
private fun savedFileLabel(context: android.content.Context, name: String): String = when (name) {
    "original.dng" -> context.getString(R.string.photo_info_saved_dng)
    "viewfinder.jpg" -> context.getString(R.string.photo_info_saved_viewfinder)
    "render_job.json" -> context.getString(R.string.photo_info_saved_render_job)
    "thumbnail.jpg" -> context.getString(R.string.photo_info_saved_thumbnail)
    "detail_hdr.jpg" -> context.getString(R.string.photo_info_saved_detail_hdr)
    "original_hdr.bin" -> context.getString(R.string.photo_info_saved_hdr)
    "video.mp4" -> context.getString(R.string.photo_info_saved_video)
    "bokeh.jpg" -> context.getString(R.string.photo_info_saved_bokeh)
    "ai_denoise.jpg" -> context.getString(R.string.photo_info_saved_ai_denoise)
    "preview.jpg" -> context.getString(R.string.photo_info_saved_preview)
    else -> name
}

@Composable
private fun InfoCollapsibleHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun InfoSubRow(text: String) {
    Text(text = text, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

private fun resolveCurrentMediaSize(context: Context, photo: MediaData): Long {
    if (photo.size > 0L) return photo.size

    val candidates = listOfNotNull(photo.uri, photo.sourceUri).distinctBy { it.toString() }
    candidates.forEach { uri ->
        val size = resolveUriSize(context, uri)
        if (size > 0L) return size
    }

    return photo.size
}

private fun resolveUriSize(context: Context, uri: Uri): Long {
    if (uri.scheme == null || uri.scheme == "file") {
        return uri.path?.let { File(it).takeIf(File::exists)?.length() } ?: 0L
    }

    if (uri.scheme == "content") {
        val queriedSize = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        }.getOrDefault(0L)
        if (queriedSize > 0L) return queriedSize

        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it > 0L } ?: 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    return 0L
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun rememberVideoPlayerView(player: ExoPlayer?): PlayerView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playerView = remember(context) {
        LayoutInflater.from(context).inflate(R.layout.view_motion_photo_player, null) as PlayerView
    }

    DisposableEffect(player, playerView, lifecycle) {
        fun updatePlayback() {
            val isForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!isForeground && player?.playWhenReady == true) {
                PLog.d("GalleryVideoPlayback", "Pausing playback while the screen is not resumed.")
                player.pause()
            }
            playerView.keepScreenOn = isForeground && player?.isPlaying == true
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updatePlayback()
            }
        }
        val observer = LifecycleEventObserver { _, _ -> updatePlayback() }
        playerView.player = player
        player?.addListener(listener)
        lifecycle.addObserver(observer)
        updatePlayback()

        onDispose {
            lifecycle.removeObserver(observer)
            player?.removeListener(listener)
            playerView.keepScreenOn = false
            playerView.player = null
        }
    }

    return playerView
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoDetailPlayer(
    photo: MediaData,
    isActive: Boolean,
    viewModel: GalleryViewModel,
    previewHandle: VideoPreviewHandle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mediaUri = remember(photo.id, photo.uri, photo.sourceUri) {
        photo.sourceUri ?: photo.uri
    }

    PLog.d("VideoDetailPlayer", "VideoDetailPlayer recomposing/initializing. photoId: ${photo.id}, isActive: $isActive, mediaUri: $mediaUri")

    val contentRepository = remember {
        com.hinnka.mycamera.data.ContentRepository.getInstance(context)
    }

    var lutConfig by remember { mutableStateOf<com.hinnka.mycamera.lut.LutConfig?>(null) }
    var recipeParams by remember { mutableStateOf<com.hinnka.mycamera.model.ColorRecipeParams?>(null) }
    val refreshKey = viewModel.photoRefreshKeys[photo.id] ?: 0L

    LaunchedEffect(photo.id, refreshKey) {
        withContext(Dispatchers.IO) {
            PLog.d("VideoDetailPlayer", "Loading video metadata from DB.")
            val metadata = com.hinnka.mycamera.gallery.GalleryManager.loadMetadata(context, photo.id) ?: photo.metadata
            val lutId = metadata?.lutId
            val params = metadata?.colorRecipeParams
            PLog.d("VideoDetailPlayer", "Metadata loaded. lutId: $lutId, recipeEnabled: ${params != null}")

            val config = if (lutId != null) {
                contentRepository.lutManager.loadLut(lutId)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                lutConfig = config
                recipeParams = params
            }
        }
    }

    // Maintain the video LUT effect
    val videoLutEffect = remember(photo.id, mediaUri) {
        PLog.d("VideoDetailPlayer", "Instantiating new VideoLutEffect.")
        VideoLutEffect(lutConfig, recipeParams)
    }
    val videoOutputSize by videoLutEffect.outputSize.collectAsState()
    LaunchedEffect(videoOutputSize) {
        videoOutputSize?.let { size ->
            PLog.d("VideoDetailPlayer", "Effect output size: photo=${photo.id}, size=${size.width}x${size.height}")
        }
    }

    // Update effect parameters dynamically on the GL pipeline without reconstruction
    LaunchedEffect(lutConfig, recipeParams) {
        PLog.d("VideoDetailPlayer", "Updating VideoLutEffect params. lut: ${lutConfig?.title}, recipe: ${recipeParams != null}")
        videoLutEffect.update(lutConfig, recipeParams)
    }

    val exoPlayer = remember(photo.id, mediaUri, isActive) {
        if (!isActive) return@remember null
        PLog.d("VideoDetailPlayer", "Creating ExoPlayer for detail video.")
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setVideoEffects(listOf(videoLutEffect))

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    PLog.d("VideoDetailPlayer", "ExoPlayer state changed: $state")
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    PLog.w("VideoDetailPlayer", "ExoPlayer encountered error!", error)
                }
            })
        }
    }

    val playerView = rememberVideoPlayerView(exoPlayer)

    DisposableEffect(exoPlayer, previewHandle) {
        val previewLease = exoPlayer?.let(previewHandle::attach)
        onDispose {
            if (previewLease != null) {
                PLog.d("VideoDetailPlayer", "Disposing ExoPlayer.")
                previewHandle.release(previewLease)
            }
        }
    }

    LaunchedEffect(exoPlayer, isActive, lifecycle) {
        PLog.d("VideoDetailPlayer", "VideoDetailPlayer isActive changed: $isActive, player: $exoPlayer")
        if (exoPlayer != null && isActive) {
            delay(150) // Wait for transitions to complete and EGL surface to be fully ready
            PLog.d("VideoDetailPlayer", "Preparing and starting ExoPlayer.")
            if (viewModel.videoRate(photo).isHighFrameRate) {
                exoPlayer.setPlaybackSpeed(1f / SlowedCopy.FACTOR)
                exoPlayer.volume = 0f
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
    }

    if (exoPlayer != null) {
      Box(modifier = modifier.autoRotate(matchParentSize = true)) {
        AndroidView(
            factory = {
                PLog.d("VideoDetailPlayer", "Creating PlayerView factory.")
                playerView
            },
            update = {
                PLog.d("VideoDetailPlayer", "Updating PlayerView with player.")
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                // With effects, Player.videoSize may remain UNKNOWN. Fit the Surface itself
                // to the effect output so a paused/ended buffer has no baked-in letterboxing
                // that would be stretched when autoRotate swaps the container dimensions.
                videoOutputSize?.let { size ->
                    it.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
                        .setAspectRatio(size.width.toFloat() / size.height)
                }
                // VideoTransport draws the controls in the viewer's rows.
                it.useController = false
                it.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                it.isVisible = true
            },
            modifier = Modifier.fillMaxSize()
        )
      }
    } else {
        // Show video thumbnail with a play icon when player is not active
        Box(
            modifier = modifier.autoRotate(matchParentSize = true),
            contentAlignment = Alignment.Center
        ) {
            val transformation = remember(photo) {
                viewModel.getPhotoTransformation(photo)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.thumbnailUri)
                    .crossfade(true)
                    .apply {
                        if (transformation != null) {
                            transformations(transformation)
                        }
                    }
                    .build(),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

/**
 * The shown video's controls: play / pause in the middle of the picture, the timeline in the item row, and
 * ¼× / 1× with sound just above it on the right. Only the controls hide in full view; the frame loop that
 * follows the position (and switches the speed at the slow part's ends) keeps running.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun BoxScope.VideoTransport(
    player: ExoPlayer,
    photo: MediaData,
    rate: SlowedCopy.Rate?,
    slowed: Boolean,
    onSlowed: (Boolean) -> Unit,
    barsShown: Boolean,
    // Only a Lilac video keeps its slow part; a System tab item changes this playback only.
    keepsSlowPart: Boolean,
    explain: (String) -> Unit,
    fade: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(rate?.durationMs ?: 0L) }
    var paused by remember { mutableStateOf(true) }
    // Where the finger holds the timeline; shown instead of the player's position, which lags at sync frames.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var muted by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        fun read() {
            // Not before the player is prepared, so the paused-only pills don't flash while a clip opens.
            paused = player.playbackState != Player.STATE_IDLE &&
                (!player.playWhenReady || player.playbackState == Player.STATE_ENDED)
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durationMs = it }
            positionMs = player.currentPosition
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = read()
        }
        player.addListener(listener)
        read()
        onDispose { player.removeListener(listener) }
    }

    // The 120 fps slow part: the whole clip until its ends are dragged. A 960 fps clip has its slow part recorded in.
    val hasSlowPart = slowed && rate?.isHighFrameRate == true && rate.captureFps < 900f
    var slowPart by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var canKeepSlowPart by remember { mutableStateOf(false) }
    LaunchedEffect(photo.id) {
        if (!keepsSlowPart) return@LaunchedEffect
        val metadata = withContext(Dispatchers.IO) { GalleryManager.loadMetadata(context, photo.id) } ?: return@LaunchedEffect
        canKeepSlowPart = true
        slowPart = metadata.customProperties[SlowPartProperty]?.split(',')?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size == 2 && it[0] < it[1] }?.let { it[0] to it[1] }
    }
    fun inSlowPart(ms: Long) = !hasSlowPart || slowPart?.let { ms in it.first..it.second } ?: true

    fun applySpeed() {
        val speed = if (slowed && inSlowPart(positionMs)) 1f / SlowedCopy.FACTOR else 1f
        if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
        // Slowed clips play without sound, as their slowed copies do.
        player.volume = if (slowed || muted) 0f else 1f
    }
    LaunchedEffect(player, slowed, muted, slowPart) { applySpeed() }
    // The loop outlives this composition's [slowed]; it calls the latest one.
    val applySpeedNow by rememberUpdatedState(::applySpeed)
    LaunchedEffect(player, paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            positionMs = player.currentPosition
            applySpeedNow()
        }
    }
    fun seek(ms: Long, exact: Boolean) {
        player.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        player.seekTo(ms)
        positionMs = ms
    }
    val shownMs = scrubMs ?: positionMs

    if (!barsShown) return
    Box(
        Modifier
            .align(Alignment.Center)
            .then(fade)
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClickLabel = stringResource(if (paused) R.string.video_play else R.string.video_pause)) {
                if (paused) {
                    if (player.playbackState == Player.STATE_ENDED) seek(0L, exact = true)
                    player.play()
                } else {
                    player.pause()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(if (paused) AppIcons.PlayLine else AppIcons.PauseLine, contentDescription = null, tint = SonyText, modifier = Modifier.size(30.dp))
    }

    // Paused: step a frame either way, or keep this one as a photo.
    if (paused) {
        val frameRate by produceState(30, photo.id) {
            value = withContext(Dispatchers.IO) { (GalleryManager.loadMetadata(context, photo.id) ?: photo.metadata)?.frameRate }
                ?.takeIf { it > 0 } ?: 30
        }
        fun step(frames: Int) {
            // To the first millisecond of the frame, so rounding never shows the same frame twice.
            val frame = Math.round(shownMs * frameRate / 1000.0) + frames
            seek(kotlin.math.ceil(frame * 1000.0 / frameRate).toLong().coerceIn(0L, durationMs), exact = true)
        }
        var savingFrame by remember { mutableStateOf(false) }
        val busy = stringResource(R.string.viewer_busy)
        val saved = stringResource(R.string.video_frame_saved)
        val failed = stringResource(R.string.video_frame_save_failed)
        val pillFill = com.hinnka.mycamera.ui.components.SonyDivider.copy(alpha = 0.85f)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ViewerActionRowHeight + ViewerItemRowHeight + 48.dp)
                .height(48.dp)
                .then(fade),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerPill(onClick = { step(-1) }, icon = AppIcons.ChevronLeftLine, description = stringResource(R.string.video_previous_frame), fill = pillFill)
            ViewerPill(
                onClick = {
                    if (savingFrame) {
                        explain(busy)
                    } else {
                        savingFrame = true
                        scope.launch {
                            explain(if (saveVideoFrame(context, photo, shownMs) != null) saved else failed)
                            savingFrame = false
                        }
                    }
                },
                icon = AppIcons.PhotoDownLine,
                text = stringResource(R.string.video_save_frame),
                fill = pillFill,
            )
            ViewerPill(onClick = { step(1) }, icon = AppIcons.ChevronRightLine, description = stringResource(R.string.video_next_frame), fill = pillFill)
        }
    }

    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = ViewerActionRowHeight + ViewerItemRowHeight)
            .height(40.dp)
            .then(fade),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rate?.isHighFrameRate == true) {
            val speedLabel = stringResource(R.string.hfr_speed_button_desc)
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(com.hinnka.mycamera.ui.components.SonyDivider)
                    .padding(2.dp)
                    .semantics { contentDescription = speedLabel }
            ) {
                listOf(true to "¼×", false to "1×").forEach { (slow, label) ->
                    val chosen = slow == slowed
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .widthIn(min = 38.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (chosen) com.hinnka.mycamera.ui.components.ChosenFill else Color.Transparent)
                            .clickable(role = Role.Tab) { onSlowed(slow) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (chosen) Color.Black else com.hinnka.mycamera.ui.components.SonySecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        val noSound = stringResource(R.string.video_slowed_no_sound)
        Box(
            Modifier
                .size(44.dp, 48.dp)
                .clickable(role = Role.Switch, onClickLabel = stringResource(R.string.video_sound)) {
                    if (slowed) explain(noSound) else muted = !muted
                },
            contentAlignment = Alignment.Center
        ) {
            ShadowedLineIcon(if (slowed || muted) AppIcons.VolumeOffLine else AppIcons.VolumeLine, 22.dp, SonyText)
        }
    }

    // The timeline takes the whole width: scrubbing on a small screen needs every millimetre.
    val slowPartHint = stringResource(R.string.video_slow_part_hint)
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = ViewerActionRowHeight + (ViewerItemRowHeight - 48.dp) / 2)
            .fillMaxWidth()
            .height(48.dp)
            .then(fade)
            .pointerInput(player, hasSlowPart) {
                val margin = 16.dp.toPx()
                fun msAt(x: Float) = (((x - margin) / (size.width - 2 * margin)).coerceIn(0f, 1f) * durationMs).toLong()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    // A slow part's end within reach of the finger is dragged instead of the position.
                    val ends = (slowPart ?: (0L to durationMs)).toList()
                    val end = if (hasSlowPart && durationMs > 0) {
                        ends.indices.minBy { kotlin.math.abs(margin + ends[it] * (size.width - 2 * margin) / durationMs - down.position.x) }
                            .takeIf { kotlin.math.abs(margin + ends[it] * (size.width - 2 * margin) / durationMs - down.position.x) <= 20.dp.toPx() }
                    } else null
                    if (end != null && !slowPartHintShown) {
                        slowPartHintShown = true
                        explain(slowPartHint)
                    }
                    var x = down.position.x
                    fun follow() {
                        if (end == null) {
                            scrubMs = msAt(x)
                            seek(msAt(x), exact = false)
                        } else {
                            val (a, b) = slowPart ?: (0L to durationMs)
                            val gap = durationMs / 20
                            slowPart = if (end == 0) msAt(x).coerceAtMost(b - gap).coerceAtLeast(0L) to b
                            else a to msAt(x).coerceAtLeast(a + gap).coerceAtMost(durationMs)
                        }
                    }
                    try {
                        follow()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            x = change.position.x
                            follow()
                        }
                    } finally {
                        if (end == null) {
                            seek(msAt(x), exact = true)
                            scrubMs = null
                        } else {
                            val part = slowPart
                            if (canKeepSlowPart && part != null) {
                                scope.launch(Dispatchers.IO) {
                                    GalleryManager.updateMetadata(context, photo.id) {
                                        it.copy(customProperties = it.customProperties + (SlowPartProperty to "${part.first},${part.second}"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 16.dp)
    ) {
        val timeStyle = LocalTextStyle.current.copy(shadow = ViewfinderTextShadow)
        Text(clipTime(shownMs), color = com.hinnka.mycamera.ui.components.SonySecondary, fontSize = 11.sp, style = timeStyle,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp))
        Text(clipTime(durationMs), color = com.hinnka.mycamera.ui.components.SonySecondary, fontSize = 11.sp, style = timeStyle,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp))
        val amber = com.hinnka.mycamera.ui.camera.ModeAmber
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            fun xOf(ms: Long) = if (durationMs > 0) size.width * (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val track = 4.dp.toPx()
            val trackTop = 26.dp.toPx()
            drawRoundRect(com.hinnka.mycamera.ui.components.SonyTrack, Offset(0f, trackTop), Size(size.width, track), CornerRadius(track / 2))
            val part = if (hasSlowPart) slowPart ?: (0L to durationMs) else null
            if (part != null) {
                drawRoundRect(amber.copy(alpha = 0.45f), Offset(xOf(part.first), 24.dp.toPx()),
                    Size(xOf(part.second) - xOf(part.first), 8.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            drawRoundRect(SonyText, Offset(0f, trackTop), Size(xOf(shownMs), track), CornerRadius(track / 2))
            if (part != null) {
                listOf(part.first, part.second).forEach { ms ->
                    drawRoundRect(amber, Offset(xOf(ms) - 3.dp.toPx(), 16.dp.toPx()), Size(6.dp.toPx(), 24.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
            }
            drawCircle(SonyText, 8.dp.toPx(), Offset(xOf(shownMs), trackTop + track / 2))
        }
    }
}

/** Where a 120 fps clip's slow part is kept, as "startMs,endMs", in the video's Lilac metadata. */
private const val SlowPartProperty = "slowPartMs"

/** The slow part's hint shows on the first drag of its ends in a run of the app. */
private var slowPartHintShown = false

private fun clipTime(ms: Long): String = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/**
 * Saves the video frame at [positionMs], at its own size, as a new Lilac photo with the video's look, through the
 * same import as a picked photo. Returns the photo's id, or null when it failed.
 */
private suspend fun saveVideoFrame(context: Context, photo: MediaData, positionMs: Long): String? = withContext(Dispatchers.IO) {
    val retriever = android.media.MediaMetadataRetriever()
    val file = File(context.cacheDir, "video_frame_${System.currentTimeMillis()}.jpg")
    try {
        retriever.setDataSource(context, photo.sourceUri ?: photo.uri)
        val bitmap = retriever.getFrameAtTime(positionMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            ?: return@withContext null
        val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val codedWidth = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        // The retriever turns frames upright; a frame still in the coded size (90° or 270°) gets the turn as EXIF.
        val upright = rotation % 180 == 0 || bitmap.width != codedWidth || bitmap.width == bitmap.height
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmap.recycle()
        }
        if (!upright) {
            androidx.exifinterface.media.ExifInterface(file).apply {
                setAttribute(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    (if (rotation == 90) androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
                    else androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270).toString()
                )
                saveAttributes()
            }
        }
        val lutId = (GalleryManager.loadMetadata(context, photo.id) ?: photo.metadata)?.lutId
        GalleryManager.importPhoto(context, Uri.fromFile(file), lutId)?.also { GalleryManager.notifyPhotoLibraryChanged() }
    } catch (e: Exception) {
        PLog.e("VideoTransport", "Could not save the frame at $positionMs ms of ${photo.id}", e)
        null
    } finally {
        retriever.release()
        file.delete()
    }
}

/**
 * 可缩放的图片组件
 * 使用 Telephoto 库支持大尺寸图片查看
 */
@Composable
private fun ZoomableImage(
    photo: MediaData,
    // A burst frame shown in place of the photo's own picture.
    frame: File?,
    colorSpace: MutableState<ColorSpace?>,
    showOrigin: Boolean,
    isActive: Boolean,
    isScrollInProgress: Boolean,
    viewModel: GalleryViewModel,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val density = LocalDensity.current
    // Pictures no taller than 3:4 rest between the top row and the item row; taller ones, or any picture while
    // the screen is turned, fit the whole screen.
    val rests = OrientationObserver.uiRotationDegrees == 0f &&
        bitmap?.let { it.width >= it.height * 0.749f } == true
    val contentScale = remember(rests, density) {
        if (rests) with(density) { RestScale(ViewerTopRowHeight.toPx(), (ViewerItemRowHeight + ViewerActionRowHeight).toPx()) }
        else ContentScale.Fit
    }
    val windowSize = LocalWindowInfo.current.containerSize
    // Enough zoom for the double-tap's pixel-for-pixel view, in either screen orientation.
    val oneToOneZoom = bitmap?.takeIf { it.width > 0 && it.height > 0 && windowSize.width > 0 && windowSize.height > 0 }?.let { shown ->
        val source = Size(shown.width.toFloat(), shown.height.toFloat())
        listOf(
            Size(windowSize.width.toFloat(), windowSize.height.toFloat()),
            Size(windowSize.height.toFloat(), windowSize.width.toFloat()),
        ).maxOf { 1f / contentScale.computeScaleFactor(source, it).scaleX }
    } ?: 1f
    val maxZoom = max(max(photo.width, photo.height) / 500f, max(oneToOneZoom, MinDoubleTapZoom) * 1.05f)
    val zoomableState = rememberZoomableImageState(
        zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = maxZoom))
    )

    LaunchedEffect(zoomableState.zoomableState.zoomFraction) {
        onZoomChange((zoomableState.zoomableState.zoomFraction ?: 0f) > 0.01f)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val displayPhoto = if (viewModel.selectedTab == GalleryTab.SYSTEM) {
            photo.relatedPhoto ?: photo
        } else {
            photo
        }
        // 使用 hashCode() 代替 toJson() 序列化，避免 composition 时做 JSON 序列化
        val metadataHash = remember(displayPhoto.metadata, photo.metadata) {
            displayPhoto.metadata?.hashCode() ?: photo.metadata?.hashCode() ?: 0
        }

        var hdrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var showHdr by remember { mutableStateOf(false) }
        val hdrAlpha by animateFloatAsState(
            targetValue = if (showHdr) 1f else 0f,
            animationSpec = tween(durationMillis = 750, easing = LinearOutSlowInEasing),
            label = "hdrFadeIn"
        )
        val refreshKey = viewModel.photoRefreshKeys[displayPhoto.id] ?: 0L
        val isSettledActive = isActive && !isScrollInProgress
        // 1.5x the screen's long edge (not 4096): a 4096 px LUT pass bloats the GPU driver memory that renders need.
        val settledMaxEdge = (max(windowSize.width, windowSize.height) * 1.5f).toInt().coerceIn(1024, 4096)
        val previewMaxEdge = if (isSettledActive) settledMaxEdge else 1024

        LaunchedEffect(displayPhoto.id, metadataHash, showOrigin, refreshKey, isSettledActive, frame) {
            isLoading = bitmap == null
            if (frame != null) {
                // The frame goes through the photo's own look, capped like the photo; it has no HDR detail.
                val frameBitmap = withContext(Dispatchers.IO) {
                    GalleryManager.loadBitmap(context, Uri.fromFile(frame), previewMaxEdge)
                }
                bitmap = frameBitmap?.let {
                    viewModel.getPreviewBitmap(
                        displayPhoto,
                        showOrigin = showOrigin,
                        bitmap = it,
                        ignoreDenoise = !isSettledActive,
                        maxEdge = previewMaxEdge,
                        cacheTag = frame.name
                    )
                }
                colorSpace.value = bitmap?.colorSpace
                isLoading = bitmap == null
                hdrBitmap = null
                return@LaunchedEffect
            }
            bitmap = viewModel.getPreviewBitmap(
                displayPhoto,
                showOrigin = showOrigin,
                ignoreDenoise = !isSettledActive,
                maxEdge = previewMaxEdge
            )
            if (bitmap == null && viewModel.awaitPreparedPhotoReady(displayPhoto)) {
                bitmap = viewModel.getPreviewBitmap(
                    displayPhoto,
                    showOrigin = showOrigin,
                    ignoreDenoise = !isSettledActive,
                    maxEdge = previewMaxEdge
                )
            }
            colorSpace.value = bitmap?.colorSpace
            isLoading = bitmap == null

            if (displayPhoto.metadata?.manualHdrEffectEnabled == true && isSettledActive) {
                hdrBitmap = viewModel.getDetailBitmap(displayPhoto, maxEdge = previewMaxEdge)
                hdrBitmap?.let {
                    colorSpace.value = it.colorSpace
                }
            } else {
                hdrBitmap = null
            }
        }

        LaunchedEffect(hdrBitmap, showOrigin, isActive) {
            delay(300)
            showHdr = hdrBitmap != null && !showOrigin && isActive
        }

        bitmap?.let {
            TelephotoImage(
                image = remember(it) { BitmapImageSource(it) },
                contentDescription = displayPhoto.displayName,
                contentScale = contentScale,
                alignment = if (rests) RestAlignment else Alignment.Center,
                state = zoomableState,
                onClick = { onTap() },
                onLongClick = {},
                onDoubleClick = OneToOneDoubleClick,
                modifier = Modifier.fillMaxSize().autoRotate(matchParentSize = true)
            )
        }

        hdrBitmap?.takeIf { showHdr }?.let {
            TelephotoImage(
                image = remember(it) { BitmapImageSource(it) },
                contentDescription = displayPhoto.displayName,
                contentScale = contentScale,
                alignment = if (rests) RestAlignment else Alignment.Center,
                state = zoomableState,
                onClick = { onTap() },
                onLongClick = {},
                onDoubleClick = OneToOneDoubleClick,
                modifier = Modifier.fillMaxSize().alpha(hdrAlpha).autoRotate(matchParentSize = true)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = AccentColor,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MotionPhotoPlayer(
    photo: MediaData,
    isPlaying: Boolean,
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    onLoop: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnLoop by rememberUpdatedState(onLoop)
    val videoFile = remember(photo.id) {
        viewModel.getMotionPhotoVideo(photo)
    }

    if (!photo.isMotionPhoto || videoFile == null || !videoFile.exists()) {
        if (photo.isMotionPhoto) {
            PLog.w("MotionPhotoPlayer", "Motion Photo video file missing: ${photo.id}")
        }
        return
    }

    val contentRepository = remember {
        com.hinnka.mycamera.data.ContentRepository.getInstance(context)
    }

    var lutConfig by remember { mutableStateOf<com.hinnka.mycamera.lut.LutConfig?>(null) }
    var recipeParams by remember { mutableStateOf<com.hinnka.mycamera.model.ColorRecipeParams?>(null) }
    var effectMetadataLoaded by remember(photo.id) { mutableStateOf(false) }
    val refreshKey = viewModel.photoRefreshKeys[photo.id] ?: 0L

    LaunchedEffect(photo.id, refreshKey) {
        effectMetadataLoaded = false
        withContext(Dispatchers.IO) {
            PLog.d("MotionPhotoPlayer", "Loading video metadata from DB.")
            val metadata = com.hinnka.mycamera.gallery.GalleryManager.loadMetadata(context, photo.id) ?: photo.metadata
            val applyEffects = metadata?.applyEffectsToVideo == true
            val lutId = if (applyEffects) metadata.lutId else null
            val params = if (applyEffects) metadata.colorRecipeParams else null
            PLog.d("MotionPhotoPlayer", "Metadata loaded. applyEffects: $applyEffects, lutId: $lutId, recipeEnabled: ${params != null}")

            val config = if (lutId != null) {
                contentRepository.lutManager.loadLut(lutId)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                lutConfig = config
                recipeParams = params
                effectMetadataLoaded = true
            }
        }
    }

    // Maintain the video LUT effect
    val videoLutEffect = remember {
        PLog.d("MotionPhotoPlayer", "Instantiating new VideoLutEffect.")
        VideoLutEffect(lutConfig, recipeParams)
    }

    // Update effect parameters dynamically on the GL pipeline without reconstruction
    LaunchedEffect(lutConfig, recipeParams) {
        PLog.d("MotionPhotoPlayer", "Updating VideoLutEffect params. lut: ${lutConfig?.title}, recipe: ${recipeParams != null}")
        videoLutEffect.update(lutConfig, recipeParams)
    }

    if (!effectMetadataLoaded) return

    val hasVideoEffects = lutConfig != null || recipeParams != null
    val shouldApplyVideoEffects = hasVideoEffects

    var isReadyToShow by remember(photo.id) { mutableStateOf(false) }

    val exoPlayer = remember(photo.id, videoFile.absolutePath, shouldApplyVideoEffects) {
        ExoPlayer.Builder(context).build().apply {
            PLog.d(
                "MotionPhotoPlayer",
                "Creating ExoPlayer for ${photo.id}, video=${videoFile.absolutePath}, size=${videoFile.length()}, effects=$shouldApplyVideoEffects"
            )
            repeatMode = Player.REPEAT_MODE_ONE
            if (shouldApplyVideoEffects) {
                setVideoEffects(listOf(videoLutEffect))
            }
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isReadyToShow = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) currentOnLoop()
                }

                override fun onPlayerError(error: PlaybackException) {
                }
            })
            prepare()
        }
    }

    val playerView = rememberVideoPlayerView(exoPlayer)

    LaunchedEffect(exoPlayer, isPlaying, shouldApplyVideoEffects, lifecycle) {
        if (isPlaying) {
            isReadyToShow = false
            delay(150)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
            if (exoPlayer.mediaItemCount == 0) {
                PLog.d("MotionPhotoPlayer", "Preparing Motion Photo player after PlayerView attach: ${photo.id}")
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                exoPlayer.prepare()
            }
            exoPlayer.seekTo(0)
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            isReadyToShow = false
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            playerView
        },
        update = {
            it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            it.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            it.isVisible = true
            it.alpha = if (isPlaying && isReadyToShow) 1f else 0f
        },
        modifier = modifier.autoRotate(matchParentSize = true)
    )
}

/** Shows an already decoded [bitmap] in the first frame; a coil request resolves a frame later and flashes empty. */
private class BitmapImageSource(private val bitmap: Bitmap) : ZoomableImageSource {
    @Composable
    override fun resolve(canvasSize: Flow<Size>): ZoomableImageSource.ResolveResult = remember(bitmap) {
        ZoomableImageSource.ResolveResult(
            delegate = ZoomableImageSource.PainterDelegate(BitmapPainter(bitmap.asImageBitmap()))
        )
    }
}

/** The version strip's height: 44 dp tiles plus 6 dp above and below. */
private val VersionStripHeight = 56.dp

// The viewer's rows, from the screen's edges: the top row, the item row above the action row.
private val ViewerTopRowHeight = 48.dp
private val ViewerItemRowHeight = 56.dp
private val ViewerActionRowHeight = 64.dp
private val ViewerActionSlotWidth = 70.dp
// The strip centred in the item row, and how far it drops in full view.
private val VersionStripRest = ViewerActionRowHeight + (ViewerItemRowHeight - VersionStripHeight) / 2
private val FullViewStripDrop = 56.dp

/** A burst photo's frames, oldest first, and which one the photo's own file copies (-1: none). */
private class BurstFrames(val photoId: String? = null, val files: List<File> = emptyList(), val cover: Int = -1)

/** One control on the action row; [whyNot] is said instead when it cannot run now. */
private class ViewerAction(val icon: ImageVector, val label: String, val whyNot: String?, val onClick: () -> Unit)

/**
 * Fits a picture between the top row and the item row ([top] and [bottom] px kept free), as wide as the screen
 * allows; with [RestAlignment] it rests in that band rather than in the screen's middle.
 */
private class RestScale(private val top: Float, private val bottom: Float) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val scale = min(dstSize.width / srcSize.width, (dstSize.height - top - bottom) / srcSize.height)
        return ScaleFactor(scale, scale)
    }
}

// The free space above and below the picture split as the rows are: 48 above, 120 below.
private val RestAlignment = BiasAlignment(0f, 48f / 168f * 2f - 1f)

// Double-tap shows the preview pixel for pixel, never less than a clear step in.
private const val MinDoubleTapZoom = 1.25f

/** Double-tap: back to fit when zoomed, otherwise one preview pixel per screen pixel, centred at the tap. */
private object OneToOneDoubleClick : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        val animation = tween<Float>(240)
        if ((state.zoomFraction ?: 0f) > 0.01f) {
            state.resetZoom(animation)
            return
        }
        val fitted = state.contentTransformation.scaleMetadata.initialScale.scaleX
        if (fitted <= 0f) return
        state.zoomTo((1f / fitted).coerceAtLeast(MinDoubleTapZoom), centroid, animation)
    }
}
