package com.hinnka.mycamera.ui.gallery

import com.hinnka.mycamera.data.UserPreferences
import com.hinnka.mycamera.ui.prefs.rememberPreference
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.utils.PLog
import android.graphics.Rect
import android.graphics.Bitmap
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.TextView
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.ui.graphics.toArgb
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.PhotoStacks
import com.hinnka.mycamera.gallery.ProcessingPhoto
import com.hinnka.mycamera.render.RenderLook
import com.hinnka.mycamera.render.RenderQueue
import com.hinnka.mycamera.ui.components.ActionRow
import com.hinnka.mycamera.ui.components.EdgeRingStyle
import com.hinnka.mycamera.ui.components.RowAction
import com.hinnka.mycamera.ui.components.UndoBanner
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import com.hinnka.mycamera.ui.components.TileEdgeLineView
import coil.load
import coil.dispose
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.utils.OrientationObserver
import com.hinnka.mycamera.viewmodel.GalleryBatchOperation
import com.hinnka.mycamera.viewmodel.GalleryTab
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.graphics.RenderEffect
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.hinnka.mycamera.video.SlowedCopy
import androidx.recyclerview.widget.DiffUtil
import android.os.Parcelable
import kotlin.math.abs
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.components.HaloIcon
import com.hinnka.mycamera.ui.components.HaloText
import com.hinnka.mycamera.ui.components.HideNavigationBar
import com.hinnka.mycamera.ui.components.SonyText
import com.hinnka.mycamera.ui.components.SonyDivider
import com.hinnka.mycamera.ui.settings.rememberSettingsTick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * 相册浏览界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPhotoClick: (GalleryTab, Int) -> Unit,
    onNavigateToEdit: (String?) -> Unit = {},
    onRenderQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allPhotos by viewModel.currentPhotos.collectAsState()
    // Deleted items waiting for Undo leave the grid at once.
    val hiddenIds = viewModel.hiddenPhotoIds
    val stacks = remember(allPhotos, hiddenIds) { PhotoStacks.of(allPhotos.filterNot { it.id in hiddenIds }) }
    // One tile per photo stack: selection and batch actions act on the version the tile shows.
    val photos = stacks.tiles
    val processingPhotos by viewModel.processingPhotos.collectAsState()
    val renderPendingIds by RenderQueue.pendingPhotoIds.collectAsState()
    val renderFailedIds by RenderQueue.failedPhotoIds.collectAsState()
    // Only the photo being rendered, so the status' progress ticks do not rebind the grid.
    val renderingId by remember {
        RenderQueue.status.map { it.currentPhotoId.takeIf { _ -> it.running } }.distinctUntilChanged()
    }.collectAsState(null)
    var previousProcessingIds by remember { mutableStateOf(processingPhotos.keys.toSet()) }
    LaunchedEffect(processingPhotos.keys) {
        (previousProcessingIds - processingPhotos.keys).forEach(viewModel::invalidatePreviewCache)
        previousProcessingIds = processingPhotos.keys.toSet()
        viewModel.selectedPhotos.filter { it.id in processingPhotos }.forEach(viewModel::togglePhotoSelection)
    }
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val isSystemLoadingMore by viewModel.isSystemLoadingMore.collectAsState()
    val isPhotonLoadingMore by viewModel.isPhotonLoadingMore.collectAsState()
    val isSharing by viewModel.isSharing.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isPastingSettings by viewModel.isPastingSettings.collectAsState()
    val hasCopiedEditSettings by viewModel.hasCopiedEditSettings.collectAsState()
    val batchOperationProgress by viewModel.batchOperationProgress.collectAsState()
    val isSelectionMode = viewModel.isSelectionMode
    val selectedPhotos = viewModel.selectedPhotos
    val selectedImageCount = selectedPhotos.count { it.isImage }
    val selectedTab = viewModel.selectedTab
    val hasPermission = viewModel.hasGalleryPermission
    val isBatchOperationRunning = isExporting || isPastingSettings
    // Re-render dims when no selected photo has a RAW to render again from; unknown while checking.
    val selectedIds = selectedPhotos.map { it.id }
    val canRenderAgain by produceState<Boolean?>(null, selectedIds) {
        value = null
        value = selectedIds.any { RenderQueue.renderAgainSource(context, it) != null }
    }
    val scope = rememberCoroutineScope()
    var shouldRenderGrid by rememberSaveable { mutableStateOf(false) }
    val tick = rememberSettingsTick()

    BackHandler(enabled = isSelectionMode) {
        if (!isBatchOperationRunning) {
            viewModel.exitSelectionMode()
        }
    }

    fun importSelectedMedia(selection: SelectedVisualMedia) {
        val uris = selection.uris
        if (uris.isEmpty()) return
        val uniqueUris = uris.distinct().also { distinct ->
            if (distinct.size != uris.size) {
                PLog.d("GalleryScreen", "Ignoring ${uris.size - distinct.size} duplicate selected media URI(s)")
            }
        }
        val videoUris = uniqueUris.map { selection.pairedVideoUris[it] }
        viewModel.importPhotos(uniqueUris, videoUris) { importedIds ->
            if (importedIds.size == 1) {
                val newPhotoId = importedIds.first()
                viewModel.setCurrentPhotoById(newPhotoId)
                viewModel.enterEditMode()
                onNavigateToEdit(newPhotoId)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMediaWithLivePhoto()
    ) { selection ->
        PLog.d(
            "GalleryScreen",
            "Received photo picker result: ${selection.uris.size} media URI(s), ${selection.pairedVideoUris.size} Live Photo pair(s)"
        )
        importSelectedMedia(selection)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.checkGalleryPermission()
        }
    }

    // Activity Result Launcher for batch delete confirmation
    val batchDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || viewModel.selectedTab == GalleryTab.PHOTON) {
            // User confirmed deletion or we are in PHOTON tab (delete internal photos anyway)
            viewModel.deleteBatchPhotosAfterConfirmation()
        } else {
            // User cancelled deletion in SYSTEM tab
            viewModel.clearBatchDeleteRequest()
        }
    }

    // Monitor batchDeletePendingIntent and launch system delete dialog
    LaunchedEffect(viewModel.batchDeletePendingIntent) {
        viewModel.batchDeletePendingIntent?.let { pendingIntent ->
            try {
                batchDeleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                // Failed to launch, clear the request
                viewModel.clearBatchDeleteRequest()
            }
        }
    }

    // The shown tiles a delete asks about, while its sheet is open.
    var deleteSheet by remember { mutableStateOf<List<MediaData>?>(null) }
    var deleteExportedPref by rememberPreference(UserPreferences::deleteExported)
    // Copies a delete may also remove: a photo's exports, and the system's file of a video Lilac captured.
    fun exportedCopies(photos: List<MediaData>): Int = photos.sumOf { photo ->
        val metadata = photo.metadata
        val capturedVideo = photo.isVideo && metadata?.isImported != true && !metadata?.sourceUri.isNullOrBlank()
        (metadata?.exportedUris?.size ?: 0) + if (capturedVideo) 1 else 0
    }
    fun deleteTargets(shown: List<MediaData>, all: Boolean): List<MediaData> =
        if (all) shown.flatMap(stacks::versionsOf).distinct().filterNot { it.id in processingPhotos } else shown
    // App-owned files go after an Undo window; with their exported copies, through the system's dialog at once.
    fun runDelete(photos: List<MediaData>, withCopies: Boolean) {
        if (photos.isEmpty()) return
        if (withCopies) {
            viewModel.deleteSelectedPhotos(true, photos)
        } else {
            viewModel.deleteWithUndo(photos, null, context.resources.getQuantityString(R.plurals.gallery_deleted_count, photos.size, photos.size))
            viewModel.exitSelectionMode()
        }
    }
    // The Delete action: the sheet only when there is a choice; other apps' files go to the system trash.
    fun startDelete() {
        if (selectedTab == GalleryTab.SYSTEM) return viewModel.deleteSelectedPhotos()
        val shown = viewModel.unprotected(selectedPhotos.toList())
        when {
            shown.isEmpty() -> Unit
            shown.any { stacks.versionsOf(it).size > 1 } || exportedCopies(shown) > 0 -> deleteSheet = shown
            else -> runDelete(shown, false)
        }
    }
    var batchRenderAgain by remember { mutableStateOf<BatchRenderAgain?>(null) }
    var isBatchRenderingAgain by remember { mutableStateOf(false) }

    // 首次进入时先让导航过渡完成，再挂载重型网格，避免首屏动画和列表构建抢主线程。
    LaunchedEffect(Unit) {
        // Leaving the camera for the gallery: waiting RAW renders may start now.
        RenderQueue.releaseCameraHold(context)
        viewModel.loadCurrentTabData()
        delay(300L)
        shouldRenderGrid = true
    }

    fun loadCurrentTabData() {
        scope.launch {
            viewModel.loadCurrentTabData()
        }
    }

    // 监听生命周期，onResume 时刷新列表
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadCurrentTabData()
            }
            // A pending delete runs when the app goes to the background, not only after its Undo window.
            if (event == Lifecycle.Event.ON_STOP) viewModel.commitPendingDelete()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.commitPendingDelete()
        }
    }



    // Immersive like the viewer, so the bars do not flash between the grid and a photo.
    HideNavigationBar()
    Scaffold(
        containerColor = Color.Black,
        modifier = modifier
    ) { paddingValues ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = {
                loadCurrentTabData()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pullState,
            indicator = {
                // Below the line floating over the grid.
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isLoading,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                )
            }
        ) {
            if (selectedTab == GalleryTab.SYSTEM && !hasPermission) {
                // 权限缺失提示
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.permission_required_gallery),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    android.Manifest.permission.READ_MEDIA_IMAGES,
                                    android.Manifest.permission.READ_MEDIA_VIDEO
                                )
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(permission)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SonyText, contentColor = Color.Black)
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            } else if (photos.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.no_photos),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (!shouldRenderGrid) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = SonyText,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                val gridEntries = remember(stacks, context) {
                    buildGalleryGridEntries(
                        context = context,
                        photos = stacks.tiles,
                        versionCount = { stacks.versionsOf(it).size },
                    )
                }
                GalleryRecyclerGrid(
                    entries = gridEntries,
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    selectedPhotos = selectedPhotos,
                    processingPhotos = processingPhotos,
                    failedIds = renderFailedIds,
                    runningIds = processingPhotos.keys + listOfNotNull(renderingId),
                    pendingIds = renderPendingIds,
                    isSelectionMode = isSelectionMode,
                    isSelectionEnabled = !isBatchOperationRunning,
                    isLoadingMore = (selectedTab == GalleryTab.SYSTEM && isSystemLoadingMore) ||
                            (selectedTab == GalleryTab.PHOTON && isPhotonLoadingMore),
                    onPhotoClick = onPhotoClick,
                    onLoadMore = { scope.launch { viewModel.loadCurrentTabMore() } },
                    isRefreshing = isLoading,
                    onSelectTab = { tab -> scope.launch { viewModel.selectTab(tab) } },
                    modifier = Modifier.fillMaxSize()
                )
            }
            GalleryTopLine(
                isSelectionMode = isSelectionMode,
                selectedCount = selectedPhotos.size,
                enabled = !isBatchOperationRunning,
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab != selectedTab) tick()
                    scope.launch {
                        viewModel.selectTab(tab)
                    }
                },
                pendingCount = renderPendingIds.size,
                failedCount = renderFailedIds.size,
                onBack = {
                    if (isSelectionMode) {
                        viewModel.exitSelectionMode()
                    } else {
                        onBack()
                    }
                },
                onSelectAll = {
                    val selectable = photos.filterNot { it.id in processingPhotos }
                    val allSelected = selectable.all { photo -> selectedPhotos.any { it.id == photo.id } }
                    if (allSelected) {
                        selectedPhotos.toList().forEach(viewModel::togglePhotoSelection)
                    } else {
                        selectable.filter { photo -> selectedPhotos.none { it.id == photo.id } }
                            .forEach(viewModel::togglePhotoSelection)
                    }
                },
                onRenderQueue = onRenderQueueClick,
                onImport = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
            )
            val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
            AnimatedVisibility(
                visible = isSelectionMode && selectedPhotos.isNotEmpty(),
                enter = if (landscape) fadeIn() else slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = if (landscape) fadeOut() else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = if (landscape) Modifier.align(Alignment.CenterEnd).width(GalleryActionRowHeight).fillMaxHeight()
                else Modifier.align(Alignment.BottomStart).fillMaxWidth().height(GalleryActionRowHeight)
            ) {
                val busy = stringResource(R.string.viewer_busy)
                val running = if (isBatchOperationRunning) busy else null
                val progress = batchOperationProgress?.let { it.completed.toFloat() / it.total.coerceAtLeast(1) }
                ActionRow(
                    listOf(
                        RowAction(
                            AppIcons.FileExportLine, stringResource(R.string.export),
                            running ?: when {
                                selectedTab != GalleryTab.PHOTON -> stringResource(R.string.gallery_export_lilac_only)
                                selectedImageCount == 0 -> stringResource(R.string.gallery_export_photos_only)
                                else -> null
                            }
                        ) {
                            viewModel.exportSelectedPhotos { count ->
                                if (count > 0) Toast.makeText(context, R.string.export_complete, Toast.LENGTH_SHORT).show()
                            }
                        },
                        RowAction(AppIcons.ShareLine, stringResource(R.string.share), running ?: busy.takeIf { isSharing }) {
                            viewModel.shareSelectedPhotos()
                        },
                        RowAction(
                            AppIcons.ClipboardLine, stringResource(R.string.gallery_paste_look),
                            running ?: when {
                                !hasCopiedEditSettings -> stringResource(R.string.viewer_paste_nothing)
                                selectedImageCount == 0 -> stringResource(R.string.gallery_paste_needs_photo)
                                else -> null
                            }
                        ) {
                            viewModel.pasteCopiedSettingsToSelectedPhotos { successCount, total ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.paste_settings_complete, successCount, total),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        RowAction(AppIcons.TrashLine, stringResource(R.string.delete), running, ::startDelete),
                        RowAction(
                            AppIcons.RefreshLine, stringResource(R.string.gallery_re_render),
                            running ?: stringResource(R.string.render_again_not_eligible).takeIf { canRenderAgain == false }
                        ) {
                            val ids = selectedPhotos.map { it.id }
                            scope.launch {
                                val sources = ids.mapNotNull { loadRenderAgainSource(context, it) }
                                if (sources.isEmpty()) {
                                    Toast.makeText(context, R.string.render_again_not_eligible, Toast.LENGTH_SHORT).show()
                                } else {
                                    batchRenderAgain = BatchRenderAgain(
                                        photoIds = ids,
                                        eligibleCount = sources.size,
                                        appLook = appRenderLook(context, sources.first().metadata.cameraId),
                                    )
                                }
                            }
                        },
                    ),
                    vertical = landscape,
                    // Export, Share and Paste progress as a line along the row's top edge (turned: its start edge).
                    modifier = Modifier.fillMaxSize().drawBehind {
                        if (progress == null) return@drawBehind
                        val line = 2.dp.toPx()
                        if (landscape) drawRect(SonyText, size = Size(line, size.height * progress))
                        else drawRect(SonyText, size = Size(size.width * progress, line))
                    }
                )
            }
            UndoBanner(
                message = viewModel.pendingDelete?.message,
                restartKey = viewModel.pendingDelete,
                onUndo = { viewModel.undoPendingDelete() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (landscape) 0.dp else GalleryActionRowHeight, end = if (landscape) GalleryActionRowHeight else 0.dp),
            )
        }
    }

    HighFrameRateChoiceDialog(viewModel)

    deleteSheet?.let { shown ->
        val anyStack = shown.any { stacks.versionsOf(it).size > 1 }
        ViewerDeleteSheet(
            thisOne = if (anyStack) stringResource(R.string.delete_shown_versions) else null,
            all = if (anyStack) stringResource(R.string.delete_all_versions_selected) else null,
            allFirst = false,
            copies = { exportedCopies(deleteTargets(shown, it)) },
            videos = shown.all { it.isVideo },
            copiesFirst = deleteExportedPref,
            onDelete = { all, withCopies ->
                deleteSheet = null
                val targets = deleteTargets(shown, all)
                if (exportedCopies(targets) > 0) deleteExportedPref = withCopies
                // Shown tiles were checked already; this only finds protected versions behind them.
                runDelete(if (all) viewModel.unprotected(targets) else targets, withCopies)
            },
            onDismiss = { deleteSheet = null },
        )
    }

    batchRenderAgain?.let { batch ->
        RenderAgainSheet(
            title = pluralStringResource(R.plurals.render_again_batch_title, batch.eligibleCount, batch.eligibleCount),
            confirmText = pluralStringResource(R.plurals.render_again_batch_render, batch.eligibleCount, batch.eligibleCount),
            source = null,
            appLook = batch.appLook,
            isBusy = isBatchRenderingAgain,
            onRender = { look ->
                isBatchRenderingAgain = true
                scope.launch {
                    val result = RenderQueue.renderAgainBatch(context, batch.photoIds, look)
                    isBatchRenderingAgain = false
                    batchRenderAgain = null
                    viewModel.exitSelectionMode()
                    val message = listOf(
                        R.string.render_again_batch_queued to result.queued,
                        R.string.render_again_batch_skipped to result.skippedSame + result.skippedIneligible,
                        R.string.render_again_batch_failed to result.failed,
                    ).filter { it.second > 0 }.joinToString(" · ") { (text, count) -> context.getString(text, count) }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onDismissRequest = { batchRenderAgain = null }
        )
    }
}

private val GalleryActionRowHeight = 64.dp

/** The selection a batch re-render applies to; ineligible photos stay in [photoIds] to be counted as skipped. */
private data class BatchRenderAgain(val photoIds: List<String>, val eligibleCount: Int, val appLook: RenderLook)

/**
 * The line floating over the grid, with no fill: back, the Lilac / System tabs, the render queue and import;
 * while selecting, close, the count and the selection's actions.
 */
@Composable
private fun GalleryTopLine(
    isSelectionMode: Boolean,
    selectedCount: Int,
    enabled: Boolean,
    selectedTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit,
    pendingCount: Int,
    failedCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onRenderQueue: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineButton(
            if (isSelectionMode) AppIcons.CloseLine else AppIcons.ArrowLeftLine,
            stringResource(R.string.back),
            enabled,
            onBack
        )
        if (isSelectionMode) {
            Box(Modifier.padding(start = 4.dp)) {
                HaloText(stringResource(R.string.items_selected, selectedCount), fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            LineButton(AppIcons.ChecksLine, stringResource(R.string.select_all), enabled, onSelectAll)
        } else {
            GalleryTab.entries.forEach { tab ->
                val chosen = tab == selectedTab
                Box(
                    Modifier
                        .size(84.dp, 48.dp)
                        .clickable(interactionSource = null, indication = null, role = Role.Tab) { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    HaloText(
                        stringResource(if (tab == GalleryTab.PHOTON) R.string.gallery_photon else R.string.gallery_system),
                        color = if (chosen) SonyText else Color(0xFFBDBDBD),
                        letterSpacing = 0.3.sp
                    )
                    if (chosen) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(37.dp, 3.dp)
                                .background(SonyText, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            LineButton(
                AppIcons.StackLine,
                stringResource(R.string.render_queue_button_description, pendingCount, failedCount),
                true,
                onRenderQueue
            ) {
                val failed = failedCount > 0
                if (failed || pendingCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .height(16.dp)
                            .widthIn(min = 16.dp)
                            .background(if (failed) Color(TAG_FAILED_COLOR) else AccentColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // The "!" says it too, not only the error colour.
                        Text(
                            if (failed) "!" else pendingCount.toString(),
                            color = if (failed) Color.White else OnAccentColor,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LineButton(AppIcons.PhotoPlusLine, stringResource(R.string.import_photo), true, onImport)
        }
    }
}

/** A 48 dp icon button of the floating line; [badge] sits over its corner. */
@Composable
private fun LineButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    badge: @Composable BoxScope.() -> Unit = {}
) {
    Box {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
            HaloIcon(icon, tint = if (enabled) SonyText else SonyText.copy(alpha = 0.38f))
        }
        badge()
    }
}

@Composable
private fun GalleryRecyclerGrid(
    entries: List<GalleryGridEntry>,
    selectedTab: GalleryTab,
    viewModel: GalleryViewModel,
    selectedPhotos: List<MediaData>,
    processingPhotos: Map<String, ProcessingPhoto>,
    failedIds: Set<String>,
    runningIds: Set<String>,
    pendingIds: Set<String>,
    isSelectionMode: Boolean,
    isSelectionEnabled: Boolean,
    isLoadingMore: Boolean,
    onPhotoClick: (GalleryTab, Int) -> Unit,
    onLoadMore: () -> Unit,
    isRefreshing: Boolean,
    onSelectTab: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPhotoIds = selectedPhotos.map { it.id }.toSet()
    val accent = AccentColor.toArgb()
    val adapter = remember {
        GalleryRecyclerAdapter()
    }

    // Preserve scroll state for each tab separately
    var photonScrollState by rememberSaveable { mutableStateOf<Parcelable?>(null) }
    var systemScrollState by rememberSaveable { mutableStateOf<Parcelable?>(null) }

    val currentScrollState = if (selectedTab == GalleryTab.PHOTON) photonScrollState else systemScrollState
    val configuration = LocalConfiguration.current
    val spanCount = if (configuration.screenWidthDp > configuration.screenHeightDp) 5 else 3
    // The tab whose grid was last shown, so a tab change slides the new grid in but opening the screen does not.
    val shownTab = remember { arrayOf(selectedTab) }

    key(selectedTab) {
        val currentTab = selectedTab
        val rvHolder = remember { arrayOfNulls<RecyclerView>(1) }
        DisposableEffect(Unit) {
            onDispose {
                val state = rvHolder[0]?.layoutManager?.onSaveInstanceState()
                if (currentTab == GalleryTab.PHOTON) {
                    photonScrollState = state
                } else {
                    systemScrollState = state
                }
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                val container = GalleryGridFrame(context).apply { tab = currentTab }
                val density = context.resources.displayMetrics.density
                val spacingPx = (4f * density).toInt().coerceAtLeast(1)
                val recyclerView = RecyclerView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    layoutManager = StaggeredGridLayoutManager(spanCount, RecyclerView.VERTICAL).apply {
                        gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                        // Restore state for this specific tab
                        currentScrollState?.let { onRestoreInstanceState(it) }
                    }
                    // The grid runs under the floating top line and starts below it at rest.
                    setPadding(spacingPx / 2, (56 * density).toInt(), spacingPx / 2, (16 * density).toInt())
                    clipToPadding = false
                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    itemAnimator = null
                    setHasFixedSize(false)
                    addItemDecoration(GalleryGridSpacingDecoration(spacingPx))
                    this.adapter = adapter
                    rvHolder[0] = this

                    // Save state when scrolling stops
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                val state = layoutManager?.onSaveInstanceState()
                                if (selectedTab == GalleryTab.PHOTON) {
                                    photonScrollState = state
                                } else {
                                    systemScrollState = state
                                }
                            }
                        }
                    })
                }
                container.addView(recyclerView)
                // Between the grid and the fast scroller, so the scroller stays crisp.
                container.addView(GridFadeView(context))

                val fastScroller = GalleryFastScrollerView(context, recyclerView).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply { topMargin = (56 * density).toInt() }
                }
                container.addView(fastScroller)

                if (shownTab[0] != currentTab) container.slideIn()
                shownTab[0] = currentTab
                container
            },
            update = { container ->
                container.swipeEnabled = !isSelectionMode && !isRefreshing
                container.onSwitch = onSelectTab
                val recyclerView = container.getChildAt(0) as RecyclerView
                (container.getChildAt(1) as GridFadeView).fadeBottom = isSelectionMode
                val fastScroller = container.getChildAt(2) as GalleryFastScrollerView
                // Room for the floating action row while selecting: 72 dp under the grid, or turned, a 64 dp column
                // at the end, with the fast scroller to its left.
                val density = container.resources.displayMetrics.density
                val half = (4f * density).toInt().coerceAtLeast(1) / 2
                val column = if (isSelectionMode && spanCount == 5) (64 * density).toInt() else 0
                recyclerView.setPadding(
                    half, recyclerView.paddingTop, half + column,
                    ((if (isSelectionMode && column == 0) 72 else 16) * density).toInt()
                )
                (fastScroller.layoutParams as FrameLayout.LayoutParams).let {
                    if (it.rightMargin != column) {
                        it.rightMargin = column
                        fastScroller.layoutParams = it
                    }
                }
                val layoutManager = recyclerView.layoutManager as? StaggeredGridLayoutManager
                if (layoutManager != null && layoutManager.spanCount != spanCount) {
                    layoutManager.spanCount = spanCount
                }

                fastScroller.onScrollEnd = {
                    val state = recyclerView.layoutManager?.onSaveInstanceState()
                    if (selectedTab == GalleryTab.PHOTON) {
                        photonScrollState = state
                    } else {
                        systemScrollState = state
                    }
                }

                adapter.bindState(
                    entries = entries,
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    selectedPhotoIds = selectedPhotoIds,
                    processingPhotos = processingPhotos,
                    failedIds = failedIds,
                    runningIds = runningIds,
                    waitingIds = pendingIds - runningIds,
                    accent = accent,
                    isSelectionMode = isSelectionMode,
                    isSelectionEnabled = isSelectionEnabled,
                    isLoadingMore = isLoadingMore,
                    isLandscape = OrientationObserver.isUiLandscape,
                    rotationDegrees = OrientationObserver.uiRotationDegrees,
                    onPhotoClick = { tab, index ->
                        val state = recyclerView.layoutManager?.onSaveInstanceState()
                        if (tab == GalleryTab.PHOTON) {
                            photonScrollState = state
                        } else {
                            systemScrollState = state
                        }
                        onPhotoClick(tab, index)
                    },
                    onLoadMore = onLoadMore
                )
                if (recyclerView.adapter == null) {
                    recyclerView.adapter = adapter
                }
                // Ensure layout is recalculated if entries changed to prevent gaps
                (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()

                fastScroller.setEntries(entries)

                // 详情页传回照片 ID；日期标题和全宽照片使网格位置不同于照片索引。
                val scrollTarget = viewModel.galleryScrollTarget
                if (layoutManager != null && scrollTarget?.tab == selectedTab) {
                    val targetPosition = entries.indexOfFirst {
                        it is GalleryGridEntry.Photo && it.photo.id == scrollTarget.photoId
                    }
                    if (targetPosition >= 0) {
                        layoutManager.scrollToPositionWithOffset(targetPosition, 0)
                        PLog.d("GalleryScreen", "Located ${scrollTarget.photoId} at grid position $targetPosition")
                        viewModel.consumeGalleryScrollTarget(scrollTarget)
                    }
                }
            }
        )
    }
}

private class GalleryFastScrollerView(
    context: Context,
    private val recyclerView: RecyclerView
) : View(context) {
    var onScrollEnd: (() -> Unit)? = null
    private val density = context.resources.displayMetrics.density
    private val thumbWidth = (4 * density).toInt()
    private val thumbHeight = (56 * density).toInt()
    private val trackWidth = (6 * density).toInt()
    private val touchAreaWidth = (24 * density).toInt()
    private val bubbleMargin = (48 * density).toInt()

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x29FFFFFF.toInt() // White with 0.16 alpha
    }
    // The date pill is the viewer's filled pill.
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SonyDivider.toArgb()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SonyText.toArgb()
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics
        )
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var entries: List<GalleryGridEntry> = emptyList()
    private var isDragging = false
    private var lastTouchY = 0f
    private var thumbAlpha = 0f
    private var dragLabel: String? = null

    private val hideRunnable = Runnable {
        animateThumb(0f)
    }

    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isDragging) {
                    showThumb()
                    invalidate()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && !isDragging) {
                    postDelayed(hideRunnable, 900)
                } else {
                    removeCallbacks(hideRunnable)
                    showThumb()
                }
            }
        })
    }

    fun setEntries(entries: List<GalleryGridEntry>) {
        this.entries = entries
        invalidate()
    }

    private fun showThumb() {
        removeCallbacks(hideRunnable)
        if (thumbAlpha < 1f) {
            animateThumb(1f)
        }
    }

    private fun animateThumb(targetAlpha: Float) {
        animate().alpha(targetAlpha).setDuration(180).start()
        thumbAlpha = targetAlpha
    }

    override fun onDraw(canvas: Canvas) {
        if (entries.isEmpty() || thumbAlpha == 0f && !isDragging) return

        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        val offset = recyclerView.computeVerticalScrollOffset()

        if (range <= extent) return

        val usableHeight = height - thumbHeight
        val progress = offset.toFloat() / (range - extent).toFloat()
        val thumbTop = (progress * usableHeight).coerceIn(0f, usableHeight.toFloat())

        // Draw track
        val trackLeft = width - trackWidth.toFloat()
        canvas.drawRoundRect(trackLeft, 0f, width.toFloat(), height.toFloat(), 999f, 999f, trackPaint)

        // Draw thumb
        val thumbLeft = width - thumbWidth.toFloat() - (1 * density)
        canvas.drawRoundRect(thumbLeft, thumbTop, width.toFloat() - (1 * density), thumbTop + thumbHeight, 999f, 999f, thumbPaint)

        // Draw bubble
        if (isDragging && dragLabel != null) {
            val label = dragLabel!!
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val bubblePaddingH = 14 * density
            val bubbleHeight = 40 * density
            val bubblePaddingV = (bubbleHeight - textBounds.height()) / 2f
            val bubbleWidth = textBounds.width() + bubblePaddingH * 2
            val bubbleRight = width - bubbleMargin.toFloat()
            val bubbleLeft = bubbleRight - bubbleWidth
            val bubbleCenterY = thumbTop + thumbHeight / 2f
            val bubbleTop = (bubbleCenterY - bubbleHeight / 2f).coerceIn(0f, height - bubbleHeight)
            val bubbleBottom = bubbleTop + bubbleHeight

            canvas.drawRoundRect(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom, 999f, 999f, bubblePaint)
            canvas.drawText(label, bubbleLeft + bubbleWidth / 2f, bubbleTop + bubblePaddingV + textBounds.height(), textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (x < width - touchAreaWidth) return false
                isDragging = true
                showThumb()
                scrollTo(y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    scrollTo(y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    dragLabel = null
                    onScrollEnd?.invoke()
                    postDelayed(hideRunnable, 900)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scrollTo(y: Float) {
        val usableHeight = height - thumbHeight
        val progress = ((y - thumbHeight / 2f) / usableHeight).coerceIn(0f, 1f)
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount > 0) {
            val targetPos = (progress * (itemCount - 1)).toInt()
            recyclerView.scrollToPosition(targetPos)
            dragLabel = entries.getOrNull(targetPos)?.toFastScrollDateLabel(context)
            invalidate()
        }
    }

    private fun GalleryGridEntry.toFastScrollDateLabel(context: Context): String {
        return when (this) {
            is GalleryGridEntry.Header -> title
            is GalleryGridEntry.Photo -> {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                sdf.format(java.util.Date(photo.dateAdded))
            }
        }
    }
}

/**
 * Fades the grid into the black background under the floating line, and over the bottom while selecting.
 * A black overlay on the black background looks the same as a mask on the grid, without an offscreen layer per frame.
 */
private class GridFadeView(context: Context) : View(context) {
    private val dp = context.resources.displayMetrics.density
    private val fadeHeight = 120 * dp
    // The photos show at 20 %, 50 % and 85 % at the edge, at 40 dp and at 84 dp, and fully from 120 dp.
    private val paint = Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, fadeHeight,
            intArrayOf(0xCC000000.toInt(), 0x80000000.toInt(), 0x26000000, 0),
            floatArrayOf(0f, 40 * dp / fadeHeight, 84 * dp / fadeHeight, 1f),
            Shader.TileMode.CLAMP
        )
    }
    var fadeBottom = false
        set(value) {
            if (field != value) invalidate()
            field = value
        }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), fadeHeight, paint)
        if (fadeBottom) {
            canvas.save()
            canvas.scale(1f, -1f, 0f, height / 2f)
            canvas.drawRect(0f, 0f, width.toFloat(), fadeHeight, paint)
            canvas.restore()
        }
    }
}

private class GalleryGridSpacingDecoration(
    private val spacingPx: Int
) : RecyclerView.ItemDecoration() {
    private val halfSpacingPx = spacingPx / 2

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.set(halfSpacingPx, halfSpacingPx, halfSpacingPx, halfSpacingPx)
    }
}

private class GalleryRecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var entries: List<GalleryGridEntry> = emptyList()
    private var selectedTab: GalleryTab = GalleryTab.PHOTON
    private var viewModel: GalleryViewModel? = null
    private var selectedPhotoIds: Set<String> = emptySet()
    private var processingPhotos: Map<String, ProcessingPhoto> = emptyMap()
    private var failedIds: Set<String> = emptySet()
    private var runningIds: Set<String> = emptySet()
    private var waitingIds: Set<String> = emptySet()
    private var accent: Int = 0
    // Tag facts read from disk once per item, so a rebind never reads again.
    private val burstCounts = HashMap<String, Int>()
    private val videoRates = HashMap<String, SlowedCopy.Rate>()
    // Each rate read opens a MediaMetadataRetriever; a fast fling must not open one per video tile at once.
    private val rateGate = Semaphore(2)
    private var isSelectionMode: Boolean = false
    private var isSelectionEnabled: Boolean = true
    private var isLoadingMore: Boolean = false
    private var isLandscape: Boolean = false
    private var rotationDegrees: Float = 0f
    private var onPhotoClick: (GalleryTab, Int) -> Unit = { _, _ -> }
    private var onLoadMore: () -> Unit = {}

    init {
        setHasStableIds(true)
    }

    fun bindState(
        entries: List<GalleryGridEntry>,
        selectedTab: GalleryTab,
        viewModel: GalleryViewModel,
        selectedPhotoIds: Set<String>,
        processingPhotos: Map<String, ProcessingPhoto>,
        failedIds: Set<String>,
        runningIds: Set<String>,
        waitingIds: Set<String>,
        accent: Int,
        isSelectionMode: Boolean,
        isSelectionEnabled: Boolean,
        isLoadingMore: Boolean,
        isLandscape: Boolean,
        rotationDegrees: Float,
        onPhotoClick: (GalleryTab, Int) -> Unit,
        onLoadMore: () -> Unit
    ) {
        val oldEntries = this.entries
        val oldLoadingMore = this.isLoadingMore
        val oldSelectedPhotoIds = this.selectedPhotoIds
        val oldProcessingPhotos = this.processingPhotos
        val oldFailedIds = this.failedIds
        val oldRunningIds = this.runningIds
        val oldWaitingIds = this.waitingIds
        val oldAccent = this.accent
        val oldIsSelectionMode = this.isSelectionMode
        val oldIsSelectionEnabled = this.isSelectionEnabled
        val oldIsLandscape = this.isLandscape
        val oldRotationDegrees = this.rotationDegrees

        this.entries = entries
        this.selectedTab = selectedTab
        this.viewModel = viewModel
        this.selectedPhotoIds = selectedPhotoIds
        this.processingPhotos = processingPhotos
        this.failedIds = failedIds
        this.runningIds = runningIds
        this.waitingIds = waitingIds
        this.accent = accent
        this.isSelectionMode = isSelectionMode
        this.isSelectionEnabled = isSelectionEnabled
        this.isLoadingMore = isLoadingMore
        this.isLandscape = isLandscape
        this.rotationDegrees = rotationDegrees
        this.onPhotoClick = onPhotoClick
        this.onLoadMore = onLoadMore

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldEntries.size + if (oldLoadingMore) 1 else 0
            override fun getNewListSize(): Int = entries.size + if (isLoadingMore) 1 else 0

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldIsLoading = oldItemPosition >= oldEntries.size
                val newIsLoading = newItemPosition >= entries.size
                if (oldIsLoading && newIsLoading) return true
                if (oldIsLoading || newIsLoading) return false

                val oldEntry = oldEntries[oldItemPosition]
                val newEntry = entries[newItemPosition]
                return when {
                    oldEntry is GalleryGridEntry.Header && newEntry is GalleryGridEntry.Header -> oldEntry.key == newEntry.key
                    oldEntry is GalleryGridEntry.Photo && newEntry is GalleryGridEntry.Photo -> oldEntry.photo.id == newEntry.photo.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                if (oldItemPosition >= oldEntries.size || newItemPosition >= entries.size) return true

                val oldEntry = oldEntries[oldItemPosition]
                val newEntry = entries[newItemPosition]

                // If anything about the UI state changed, we should rebind
                if (oldIsSelectionMode != isSelectionMode || oldIsLandscape != isLandscape || oldRotationDegrees != rotationDegrees) return false
                if (oldAccent != accent) return false

                return when {
                    oldEntry is GalleryGridEntry.Header && newEntry is GalleryGridEntry.Header ->
                        oldEntry == newEntry && oldIsSelectionEnabled == isSelectionEnabled &&
                            oldSelectedPhotoIds == selectedPhotoIds &&
                            oldProcessingPhotos.keys == processingPhotos.keys
                    oldEntry is GalleryGridEntry.Photo && newEntry is GalleryGridEntry.Photo -> {
                        val id = oldEntry.photo.id
                        val wasSelected = id in oldSelectedPhotoIds
                        val isSelected = id in selectedPhotoIds
                        wasSelected == isSelected && oldEntry.photo == newEntry.photo &&
                            oldEntry.versionCount == newEntry.versionCount &&
                            oldProcessingPhotos[id] == processingPhotos[id] &&
                            (id in oldFailedIds) == (id in failedIds) &&
                            (id in oldRunningIds) == (id in runningIds) &&
                            (id in oldWaitingIds) == (id in waitingIds)
                    }

                    else -> false
                }
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = entries.size + if (isLoadingMore) 1 else 0

    override fun getItemId(position: Int): Long {
        if (position >= entries.size) return "loading_more".hashCode().toLong()
        return when (val entry = entries[position]) {
            is GalleryGridEntry.Header -> entry.key.hashCode().toLong()
            is GalleryGridEntry.Photo -> entry.photo.id.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position >= entries.size) return VIEW_TYPE_LOADING
        return when (entries[position]) {
            is GalleryGridEntry.Header -> VIEW_TYPE_HEADER
            is GalleryGridEntry.Photo -> VIEW_TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_header, parent, false)
            )

            VIEW_TYPE_LOADING -> LoadingHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_loading, parent, false)
            )

            else -> PhotoHolder(GalleryPhotoItemView(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val params = (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)
            ?: StaggeredGridLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        params.isFullSpan = position >= entries.size || entries[position].isFullSpan()
        holder.itemView.layoutParams = params

        if (position >= entries.size) {
            return
        }

        when (val entry = entries[position]) {
            is GalleryGridEntry.Header -> {
                val selectable = entry.photos.filterNot { it.id in processingPhotos }
                (holder as HeaderHolder).bind(
                    title = entry.title,
                    isSelectionMode = isSelectionMode,
                    isEnabled = isSelectionEnabled && selectable.isNotEmpty(),
                    isChecked = selectable.isNotEmpty() && selectable.all { it.id in selectedPhotoIds },
                    onClick = { viewModel?.togglePhotoGroupSelection(entry.photos) }
                )
            }

            is GalleryGridEntry.Photo -> {
                val photo = entry.photo
                val model = viewModel ?: return
                if (position == entries.lastIndex) {
                    onLoadMore()
                }
                (holder as PhotoHolder).bind(
                    photo = photo,
                    versionCount = entry.versionCount,
                    processingPhoto = processingPhotos[photo.id],
                    tags = {
                        gridTags(
                            context = holder.itemView.context,
                            photo = photo,
                            versionCount = entry.versionCount,
                            tab = selectedTab,
                            isRaw = model.isRawInGallery(photo.id),
                            burstCount = burstCounts[photo.id] ?: 0,
                            videoRate = videoRates[photo.id],
                            isProtected = model.isProtected(photo),
                            failed = photo.id in failedIds,
                        )
                    },
                    loadTagFacts = {
                        if (photo.isImage && photo.isBurstPhoto && photo.id !in burstCounts) {
                            burstCounts[photo.id] = withContext(Dispatchers.IO) {
                                GalleryManager.getBurstPhotos(holder.itemView.context, photo.id).size
                            }
                        }
                        if (photo.isVideo && photo.id !in videoRates) {
                            videoRates[photo.id] = rateGate.withPermit { model.videoRate(photo) }
                        }
                    },
                    edge = when (photo.id) {
                        in runningIds -> EdgeRingStyle.RUNNING
                        in waitingIds -> EdgeRingStyle.DOTTED
                        else -> null
                    },
                    accent = accent,
                    viewModel = model,
                    isSelected = photo.id in selectedPhotoIds,
                    isSelectionMode = isSelectionMode,
                    isLandscape = isLandscape,
                    rotationDegrees = rotationDegrees,
                    scope = scope,
                    onClick = {
                        if (isSelectionMode) {
                            if (photo.id !in processingPhotos) model.togglePhotoSelection(photo)
                        } else {
                            // entry.index is the tile's page in the photo view; the view model indexes all versions.
                            model.setCurrentPhotoById(photo.id)
                            onPhotoClick(selectedTab, entry.index)
                        }
                    },
                    onLongClick = {
                        if (photo.id in processingPhotos || (!isSelectionMode && (photo.id in runningIds || photo.id in waitingIds))) {
                            Toast.makeText(holder.itemView.context, R.string.gallery_busy_not_selectable, Toast.LENGTH_SHORT).show()
                        } else {
                            if (!isSelectionMode) {
                                model.enterSelectionMode()
                            }
                            model.togglePhotoSelection(photo)
                        }
                    }
                )
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is PhotoHolder) {
            holder.recycle()
        }
        super.onViewRecycled(holder)
    }

    private fun GalleryGridEntry.isFullSpan(): Boolean =
        this is GalleryGridEntry.Header ||
                (this is GalleryGridEntry.Photo && photo.shouldUseFullLineSpan())

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView = view.findViewById<TextView>(R.id.gallery_header_title)
        private val selection = view.findViewById<CheckBox>(R.id.gallery_header_selection)

        fun bind(
            title: String,
            isSelectionMode: Boolean,
            isEnabled: Boolean,
            isChecked: Boolean,
            onClick: () -> Unit
        ) {
            titleView.text = title
            selection.contentDescription = title
            selection.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            selection.isEnabled = isEnabled
            selection.alpha = if (isEnabled) 1f else 0.38f
            selection.isChecked = isChecked
            selection.setButtonDrawable(
                if (isChecked) R.drawable.ic_gallery_check_circle else R.drawable.ic_gallery_radio_unchecked
            )
            selection.setOnClickListener { onClick() }
        }
    }
    private class LoadingHolder(view: View) : RecyclerView.ViewHolder(view)
    private class PhotoHolder(
        private val view: GalleryPhotoItemView
    ) : RecyclerView.ViewHolder(view) {
        private var job: Job? = null
        private var processingPhotoId: String? = null

        fun bind(
            photo: MediaData,
            versionCount: Int,
            processingPhoto: ProcessingPhoto?,
            tags: () -> List<GridTag>,
            loadTagFacts: suspend () -> Unit,
            edge: EdgeRingStyle?,
            accent: Int,
            viewModel: GalleryViewModel,
            isSelected: Boolean,
            isSelectionMode: Boolean,
            isLandscape: Boolean,
            rotationDegrees: Float,
            scope: CoroutineScope,
            onClick: () -> Unit,
            onLongClick: () -> Unit
        ) {
            job?.cancel()
            if (processingPhoto == null && processingPhotoId == photo.id) {
                viewModel.invalidatePreviewCache(photo.id)
            }
            processingPhotoId = processingPhoto?.photo?.id
            view.bindStatic(
                photo = photo,
                versionCount = versionCount,
                viewModel = viewModel,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode && processingPhoto == null,
                isLandscape = isLandscape,
                rotationDegrees = rotationDegrees,
                onClick = onClick,
                onLongClick = onLongClick
            )
            view.bindTags(tags())
            view.bindQueueState(edge, accent, chosen = isSelectionMode && isSelected)
            view.bindProcessingPreview(processingPhoto)
            if (processingPhoto == null) {
                job = scope.launch {
                    val bitmap = viewModel.getGridThumbnailBitmap(photo)
                    view.bindThumbnail(photo.id, bitmap)
                    // BURST n and 120 / 960 FPS need a disk read; their tags appear once known.
                    loadTagFacts()
                    view.bindTags(tags())
                }
            }
        }

        fun recycle() {
            job?.cancel()
            job = null
            processingPhotoId = null
            view.clearThumbnail()
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_PHOTO = 1
        const val VIEW_TYPE_LOADING = 2
    }
}

private fun MediaData.shouldUseFullLineSpan(): Boolean {
    if (!isImage) return false
    val width = width.takeIf { it > 0 } ?: return false
    val height = height.takeIf { it > 0 } ?: return false
    return width.toFloat() / height.toFloat() >= 2.2f
}

private class GalleryPhotoItemView(context: Context) : FrameLayout(context) {
    private val contentLayer: FrameLayout
    private val imageView: ImageView
    private val selectionOverlay: View
    private val selectionIcon: ImageView
    private val tagViews: List<TextView>
    private var aspectRatio: Float = 1f
    private var boundPhotoId: String? = null
    private val edgeLine = TileEdgeLineView(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.item_gallery_photo, this, true)
        clipToOutline = true
        background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_gallery_photo_item)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        contentLayer = findViewById(R.id.gallery_photo_content)
        imageView = findViewById(R.id.gallery_photo_image)
        selectionOverlay = findViewById(R.id.gallery_photo_selection_overlay)
        selectionIcon = findViewById(R.id.gallery_photo_selection_icon)
        tagViews = listOf(findViewById(R.id.gallery_photo_tag_1), findViewById(R.id.gallery_photo_tag_2))
        // In the content layer, so in landscape the line turns with the picture and stays on the tile's edge.
        contentLayer.addView(edgeLine, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        clipChildren = false
        clipToPadding = false
    }

    fun bindStatic(
        photo: MediaData,
        versionCount: Int,
        viewModel: GalleryViewModel,
        isSelected: Boolean,
        isSelectionMode: Boolean,
        isLandscape: Boolean,
        rotationDegrees: Float,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        boundPhotoId = photo.id
        aspectRatio = photo.galleryAspectRatio(isLandscape)
        imageView.dispose()
        imageView.setImageDrawable(null)
        selectionOverlay.visibility = if (isSelectionMode && isSelected) VISIBLE else GONE
        selectionIcon.visibility = if (isSelectionMode) VISIBLE else GONE
        selectionIcon.setImageResource(
            if (isSelected) R.drawable.ic_gallery_check_circle else R.drawable.ic_gallery_radio_unchecked
        )


        setOnClickListener { onClick() }
        setOnLongClickListener {
            onLongClick()
            true
        }

        val targetDegrees = if (rotationDegrees != 0f) rotationDegrees - 180f else 0f
        if (contentLayer.rotation != targetDegrees) {
            contentLayer.animate()
                .rotation(targetDegrees)
                .setDuration(300)
                .start()
        }
        requestLayout()
    }

    fun bindTags(tags: List<GridTag>) {
        val iconSize = (10 * resources.displayMetrics.density).toInt()
        tagViews.forEachIndexed { i, view ->
            val tag = tags.getOrNull(i)
            view.visibility = if (tag == null) GONE else VISIBLE
            if (tag == null) return@forEachIndexed
            view.text = tag.text
            view.contentDescription = tag.description
            view.setTextColor(if (tag.failed) TAG_FAILED_COLOR else TAG_COLOR)
            view.setBackgroundResource(if (tag.failed) R.drawable.bg_gallery_tile_tag_failed else R.drawable.bg_gallery_tile_tag)
            val icon = tag.icon.takeIf { it != 0 }?.let { ContextCompat.getDrawable(context, it)?.mutate() }
            icon?.setBounds(0, 0, iconSize, iconSize)
            icon?.setTint(TAG_COLOR)
            view.setCompoundDrawablesRelative(icon, null, null, null)
            view.compoundDrawablePadding = if (tag.text.isEmpty()) 0 else iconSize / 5
        }
    }

    fun bindThumbnail(photoId: String, bitmap: Bitmap?) {
        if (boundPhotoId != photoId || bitmap == null || bitmap.isRecycled) return
        val bitmapAspectRatio = bitmap.galleryBitmapAspectRatio()
        if (abs(aspectRatio - bitmapAspectRatio) > 0.01f) {
            aspectRatio = bitmapAspectRatio
            requestLayout()
        }
        imageView.setImageBitmap(bitmap)
    }

    /** A rendering or waiting tile carries the edge line, its picture softened and dimmed; a [chosen] one the solid edge. */
    fun bindQueueState(style: EdgeRingStyle?, accent: Int, chosen: Boolean) {
        edgeLine.style = if (chosen) EdgeRingStyle.SOLID else style
        edgeLine.accent = accent
        edgeLine.contentDescription = if (style == null || chosen) null else context.getString(R.string.gallery_photo_processing)
        val busy = style != null
        imageView.colorFilter = if (busy) PorterDuffColorFilter(0xFFCCCCCC.toInt(), PorterDuff.Mode.MULTIPLY) else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android's blur radius is about 1.7 sigma: 3.5 dp gives the 2 dp sigma of the design's blur.
            val radius = 3.5f * resources.displayMetrics.density
            imageView.setRenderEffect(if (busy) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
        }
    }

    fun bindProcessingPreview(processingPhoto: ProcessingPhoto?) {
        if (processingPhoto == null) return
        val thumbnail = processingPhoto.thumbnail?.takeUnless { it.isRecycled }
        if (thumbnail != null) {
            bindThumbnail(processingPhoto.photo.id, thumbnail)
        } else {
            imageView.load(processingPhoto.photo.thumbnailUri)
        }
    }

    fun clearThumbnail() {
        boundPhotoId = null
        edgeLine.style = null
        imageView.dispose()
        imageView.setImageDrawable(null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = (width / aspectRatio.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1)

        setMeasuredDimension(width, height)

        val isRotated = OrientationObserver.isUiLandscape
        val childWidth: Int
        val childHeight: Int

        if (isRotated) {
            childWidth = height
            childHeight = width
        } else {
            childWidth = width
            childHeight = height
        }

        val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)

        // Measure contentLayer and other potential children
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                child.measure(childWidthSpec, childHeightSpec)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight
                val childLeft = (width - childWidth) / 2
                val childTop = (height - childHeight) / 2
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
            }
        }
    }
}

private fun Bitmap.galleryBitmapAspectRatio(): Float {
    val resolvedWidth = width.takeIf { it > 0 } ?: return 1f
    val resolvedHeight = height.takeIf { it > 0 } ?: return 1f
    return if (OrientationObserver.isUiLandscape) {
        resolvedHeight.toFloat() / resolvedWidth.toFloat()
    } else {
        resolvedWidth.toFloat() / resolvedHeight.toFloat()
    }
}

private const val TAG_COLOR = 0xFFE0E0E0.toInt()
private const val TAG_FAILED_COLOR = 0xFFEA2546.toInt()

/** A tile tag: [icon] (a drawable, 0 for none) goes before [text]. */
private data class GridTag(
    val text: String,
    @DrawableRes val icon: Int = 0,
    val failed: Boolean = false,
    val description: String? = null,
)

/**
 * The tile's tags, in the viewer's words, at most two: a failed render first, then what kind of shot it is,
 * then the lock, then the rest. [burstCount] 0 and a null [videoRate] leave those tags out until known.
 */
private fun gridTags(
    context: Context,
    photo: MediaData,
    versionCount: Int,
    tab: GalleryTab,
    isRaw: Boolean,
    burstCount: Int,
    videoRate: SlowedCopy.Rate?,
    isProtected: Boolean,
    failed: Boolean,
): List<GridTag> = buildList {
    if (failed) add(GridTag("FAILED", failed = true))
    if (isRaw && photo.isImage) add(GridTag(context.getString(R.string.gallery_raw_badge)))
    stackedFrameCount(photo)?.let { add(GridTag("×$it")) }
    val metadata = photo.metadata
    metadata?.multipleExposureFrameCount?.takeIf { metadata.captureMode == "multiple_exposure" && it > 1 }?.let {
        add(GridTag("ME ×$it"))
    }
    if (burstCount > 0) add(GridTag("BURST $burstCount"))
    if (photo.isImage && photo.isMotionPhoto) add(GridTag("LIVE", R.drawable.ic_gallery_play))
    if (photo.isVideo) add(GridTag(photo.getFormattedDuration(), R.drawable.ic_gallery_play))
    if (videoRate?.isHighFrameRate == true) add(GridTag(if (videoRate.captureFps >= 900f) "960 FPS" else "120 FPS"))
    if (isProtected) {
        add(GridTag("", R.drawable.ic_gallery_tag_lock, description = context.getString(R.string.viewer_tag_protected_hint)))
    }
    if (versionCount > 1) {
        add(
            GridTag(
                versionCount.toString(), R.drawable.ic_material_layers_24,
                description = context.resources.getQuantityString(R.plurals.gallery_stack_versions, versionCount, versionCount)
            )
        )
    }
    if (tab == GalleryTab.PHOTON && photo.sourceUri != null) add(GridTag("IMPORT", description = context.getString(R.string.imported)))
    if (tab == GalleryTab.SYSTEM && photo.relatedPhoto != null) add(GridTag("LILAC"))
}.take(2)

private fun MediaData.galleryAspectRatio(isLandscape: Boolean): Float {
    val rawWidth = if (isVideo) {
        width
    } else {
        width
    }
    val rawHeight = if (isVideo) {
        height
    } else {
        height
    }
    val rotationDegrees = if (isVideo) metadata?.rotationDegrees ?: 0 else 0
    val shouldSwapDimensions = rotationDegrees == 90 || rotationDegrees == 270
    val resolvedWidth = if (shouldSwapDimensions) rawHeight else rawWidth
    val resolvedHeight = if (shouldSwapDimensions) rawWidth else rawHeight
    return if (resolvedWidth > 0 && resolvedHeight > 0) {
        if (isLandscape) {
            resolvedHeight.toFloat() / resolvedWidth.toFloat()
        } else {
            resolvedWidth.toFloat() / resolvedHeight.toFloat()
        }
    } else {
        1f
    }
}
