package com.hinnka.mycamera.ui.gallery

import android.graphics.BitmapFactory
import android.icu.text.MessageFormat
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.camera.ViewfinderTextShadow
import com.hinnka.mycamera.ui.components.SonySecondary
import com.hinnka.mycamera.ui.components.SonyText
import com.hinnka.mycamera.ui.components.SonyTrack
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.render.RawRenderService
import com.hinnka.mycamera.render.RenderJob
import com.hinnka.mycamera.render.RenderQueue
import com.hinnka.mycamera.ui.components.AppModalBottomSheet
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Queued, failed and skipped RAW renders with the queue's live state. Draws only small CPU-decoded
 * thumbnails and drops the gallery's bitmap caches, so the render gets as much memory as possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderQueueScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by RenderQueue.status.collectAsState()
    val pendingIds by RenderQueue.pendingPhotoIds.collectAsState()
    val failedIds by RenderQueue.failedPhotoIds.collectAsState()
    var freeMb by remember { mutableIntStateOf(RenderQueue.freeMemoryMb(context)) }
    var paused by remember { mutableStateOf(RenderQueue.isPaused(context)) }
    var jobs by remember { mutableStateOf(emptyList<RenderJob>()) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmSkipId by remember { mutableStateOf<String?>(null) }
    var showMemoryInfo by remember { mutableStateOf(false) }
    val skipText = stringResource(R.string.render_queue_skip)
    val skip: (String) -> Unit = { photoId -> scope.launch { RenderQueue.skip(context, photoId) } }
    val setPaused: (Boolean) -> Unit = { pause ->
        RenderQueue.setPaused(context, pause)
        paused = pause
        if (!pause) RenderQueue.start(context)
    }
    val processAll: () -> Unit = {
        RenderQueue.setPaused(context, false)
        paused = false
        scope.launch { RenderQueue.retry(context, jobs) }
    }

    LaunchedEffect(Unit) { viewModel.dropBitmapCaches() }
    LaunchedEffect(Unit) {
        while (true) {
            freeMb = RenderQueue.freeMemoryMb(context)
            // The notification's Pause / Resume changes it too.
            paused = RenderQueue.isPaused(context)
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    // Every scan of the queue updates these sets, so the list follows renders and retries.
    LaunchedEffect(pendingIds, failedIds, status.currentPhotoId) {
        jobs = RenderQueue.jobs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.render_queue_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMemoryInfo = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.render_memory_info_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "summary") {
                StatusCard(
                    status = status,
                    paused = paused,
                    waitingCount = pendingIds.size,
                    failedCount = failedIds.size,
                    freeMb = freeMb,
                    onPause = { setPaused(true) },
                    onResume = { setPaused(false) },
                    onProcessAll = processAll,
                )
            }
            item(key = "hint") {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(R.string.render_queue_separate_hint, R.string.render_queue_hint).forEach {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(status.finishedPhotoIds, key = { it }) { photoId ->
                RenderJobRow(photoId, label = stringResource(R.string.render_queue_finished_row))
            }
            items(jobs.filter { it.photoId !in status.finishedPhotoIds }, key = { it.photoId }) { job ->
                val triedText = pluralStringResource(R.plurals.render_queue_attempts, job.attempts, job.attempts)
                    .takeIf { job.attempts > 0 }
                when {
                    job.state == RenderJob.State.FAILED -> RenderJobRow(
                        job.photoId,
                        label = pluralStringResource(R.plurals.render_queue_failed_after, job.attempts, job.attempts),
                        failed = true,
                        action = stringResource(R.string.render_queue_retry),
                        onAction = { scope.launch { RenderQueue.retry(context, listOf(job)) } }
                    )
                    job.state == RenderJob.State.SKIPPED -> RenderJobRow(
                        job.photoId,
                        label = stringResource(R.string.render_queue_skipped),
                        action = stringResource(R.string.render_queue_render),
                        onAction = { scope.launch { RenderQueue.unskip(context, job.photoId) } }
                    )
                    status.running && job.photoId == status.currentPhotoId -> RenderJobRow(
                        job.photoId,
                        progress = status.photoProgress,
                        label = stringResource(
                            R.string.render_queue_joined,
                            stringResource(status.stageText),
                            DateUtils.formatElapsedTime(((now - status.photoStartedAt) / 1000).coerceAtLeast(0L))
                        ),
                        detail = stringResource(R.string.render_queue_stage_resumed)
                            .takeIf { status.resumed && status.stageText != R.string.render_queue_stage_resumed },
                        action = skipText,
                        onAction = { confirmSkipId = job.photoId }
                    )
                    // Stopped at a checkpoint (or killed after one); a resume continues from it.
                    paused && (job.jpegDone || job.rawRendered) -> RenderJobRow(
                        job.photoId,
                        progress = if (job.jpegDone) RawRenderService.Stage.HDR.start else RawRenderService.Stage.CHECKPOINT.end,
                        label = stringResource(
                            if (job.jpegDone) R.string.render_queue_paused_after_jpeg else R.string.render_queue_paused_after_raw
                        ),
                        detail = triedText,
                        action = skipText,
                        onAction = { skip(job.photoId) }
                    )
                    else -> RenderJobRow(
                        job.photoId,
                        label = stringResource(R.string.render_queue_job_waiting),
                        detail = triedText,
                        action = skipText,
                        onAction = { skip(job.photoId) }
                    )
                }
            }
        }
    }
    if (showMemoryInfo) MemoryInfoSheet(freeMb = freeMb, onDismiss = { showMemoryInfo = false })
    // Only the photo being rendered asks: skipping it throws away the render's progress.
    confirmSkipId?.let { photoId ->
        AlertDialog(
            onDismissRequest = { confirmSkipId = null },
            title = { Text(stringResource(R.string.render_queue_skip_confirm_title)) },
            text = { Text(stringResource(R.string.render_queue_skip_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSkipId = null
                    skip(photoId)
                }) {
                    Text(skipText)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkipId = null }) {
                    Text(stringResource(R.string.render_queue_keep_processing))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/** What the queue does now in one sentence, its overall progress, memory, and the one action that fits the state. */
@Composable
private fun StatusCard(
    status: RenderQueue.Status,
    paused: Boolean,
    waitingCount: Int,
    failedCount: Int,
    freeMb: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onProcessAll: () -> Unit,
) {
    val headline: String
    val supporting: String?
    when {
        status.running && paused -> {
            headline = stringResource(R.string.render_queue_pausing_title)
            supporting = status.pausingText ?: stringResource(R.string.render_queue_pausing)
        }
        // Killed again and again: tell the user what frees enough memory.
        status.running && status.memoryKills >= RenderQueue.MEMORY_HINT_KILLS -> {
            headline = stringResource(R.string.render_memory_struggle_title)
            supporting = stringResource(R.string.render_memory_struggle)
        }
        // Memory is awaited before a photo starts, so no photo row shows a step meanwhile.
        status.running && status.waitingForMemory -> {
            headline = stringResource(R.string.render_queue_stage_waiting_memory)
            supporting = stringResource(R.string.render_queue_waiting_memory_hint)
        }
        status.running -> {
            headline = stringResource(R.string.render_queue_running, (status.done + 1).coerceAtMost(status.total), status.total)
            supporting = status.leftMs.takeIf { it >= 1_000L }
                ?.let { stringResource(R.string.render_queue_time_left, DateUtils.formatElapsedTime(it / 1000)) }
        }
        paused -> {
            headline = stringResource(R.string.render_queue_paused)
            supporting = if (waitingCount > 0) {
                pluralStringResource(R.plurals.render_queue_paused_detail, waitingCount, waitingCount)
            } else stringResource(R.string.render_queue_paused_empty_hint)
        }
        waitingCount > 0 -> {
            headline = pluralStringResource(R.plurals.render_queue_waiting_count, waitingCount, waitingCount)
            supporting = stringResource(R.string.render_queue_idle_hint)
        }
        failedCount > 0 -> {
            headline = pluralStringResource(R.plurals.render_failed_title, failedCount, failedCount)
            supporting = stringResource(R.string.render_queue_failed_hint)
        }
        else -> {
            headline = stringResource(R.string.render_queue_empty)
            supporting = stringResource(R.string.render_queue_empty_hint)
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = headline, style = MaterialTheme.typography.titleLarge)
            supporting?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status.running) {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }
            // Shown on purpose: how close a render is to being killed for memory.
            Text(
                text = status.renderPssKb?.takeIf { status.running }
                    ?.let { stringResource(R.string.render_queue_memory, it / 1024, freeMb) }
                    ?: stringResource(R.string.render_queue_memory_free, freeMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                paused -> Button(onClick = onResume) { Text(stringResource(R.string.render_queue_resume)) }
                status.running -> {
                    FilledTonalButton(onClick = onPause) { Text(stringResource(R.string.render_queue_pause)) }
                    Text(
                        text = stringResource(R.string.render_queue_pause_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                waitingCount > 0 -> Button(onClick = onProcessAll) { Text(stringResource(R.string.render_queue_render_now)) }
                failedCount > 0 -> Button(onClick = onProcessAll) { Text(stringResource(R.string.render_queue_retry_all)) }
            }
        }
    }
}

@Composable
private fun RenderJobRow(
    photoId: String,
    label: String,
    progress: Float? = null,
    detail: String? = null,
    failed: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, photoId) {
        value = withContext(Dispatchers.IO) {
            // Software decode (never a hardware bitmap): thumbnails are at most 512 px, a quarter fits the row.
            BitmapFactory.decodeFile(
                GalleryManager.getThumbnailFile(context, photoId).path,
                BitmapFactory.Options().apply { inSampleSize = 4 }
            )?.asImageBitmap()
        }
    }
    ListItem(
        headlineContent = {
            Text(text = label, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = if (progress == null && detail == null) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    detail?.let { Text(it) }
                    progress?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                thumbnail?.let {
                    Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        },
        trailingContent = action?.let { { TextButton(onClick = onAction) { Text(it) } } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Plain-language help on why processing needs memory and how to free it. Opaque sheet: a blurred backdrop would cost memory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryInfoSheet(freeMb: Int, onDismiss: () -> Unit) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.render_memory_info_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.render_memory_info_free_now, freeMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MemoryInfoRow(AppIcons.Photo, R.string.render_memory_info_why_title, R.string.render_memory_info_why)
            MemoryInfoRow(Icons.Filled.Info, R.string.render_memory_info_tight_title, R.string.render_memory_info_tight)
            MemoryInfoRow(AppIcons.Timer, R.string.render_memory_info_waiting_title, R.string.render_memory_info_waiting)
            MemoryInfoHeading(R.string.render_memory_info_free_title)
            MemoryInfoRow(Icons.Filled.Close, null, R.string.render_memory_info_close_apps)
            MemoryInfoRow(Icons.Filled.KeyboardArrowUp, null, R.string.render_memory_info_swipe)
            MemoryInfoRow(AppIcons.RestartAlt, null, R.string.render_memory_info_restart)
            MemoryInfoHeading(R.string.render_memory_info_avoid_title)
            MemoryInfoRow(Icons.Filled.Warning, null, R.string.render_memory_info_force_stop)
        }
    }
}

@Composable
private fun MemoryInfoHeading(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun MemoryInfoRow(icon: ImageVector, title: Int?, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            title?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The photo viewer's processing line: a queued RAW photo's queue state with the one button that acts on it, and
 * Queue, which opens [RenderQueueScreen]. A photo still finishing in the camera ([processingInCamera]) gets the
 * line without buttons. Draws nothing otherwise. A [ghost] line (a neighbouring item's, during a swipe) takes no touches.
 */
@Composable
fun RenderQueuePhotoChip(
    photoId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    processingInCamera: Boolean = false,
    ghost: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by RenderQueue.status.collectAsState()
    val pendingIds by RenderQueue.pendingPhotoIds.collectAsState()
    val failedIds by RenderQueue.failedPhotoIds.collectAsState()
    // Render now and Render reorder or rewrite job files without always changing the keys below.
    var reload by remember { mutableIntStateOf(0) }
    // Skipped jobs and the queue order are only in the job files; every scan changes one of these keys.
    val jobs by produceState(emptyList<RenderJob>(), pendingIds, failedIds, status.running, status.currentPhotoId, reload) {
        value = RenderQueue.jobs(context)
    }
    var pauseAsked by remember { mutableIntStateOf(0) }
    val paused = remember(jobs, status.pausingText, pauseAsked) { RenderQueue.isPaused(context) }
    val processing = status.running && photoId == status.currentPhotoId
    val job = jobs.firstOrNull { it.photoId == photoId }
    if (!processing && job == null) {
        if (processingInCamera) {
            ProcessingLine(modifier, { CircularProgressIndicator(Modifier.size(24.dp), color = SonyText, strokeWidth = 2.5.dp) },
                stringResource(R.string.processing_chip_in_camera))
        }
        return
    }
    val failed = !processing && job?.state == RenderJob.State.FAILED
    val setPaused: (Boolean) -> Unit = { pause ->
        RenderQueue.setPaused(context, pause)
        if (!pause) RenderQueue.start(context)
        pauseAsked++
    }
    val icon: @Composable () -> Unit
    val text: String
    var detail: String? = null
    var button: Pair<String, () -> Unit>? = null
    when {
        // Queue only: the queue screen says how to let it finish.
        status.running && status.memoryKills >= RenderQueue.MEMORY_HINT_KILLS &&
            (processing || job?.state == RenderJob.State.QUEUED) -> {
            icon = { LineStateIcon(AppIcons.AlertLine, SonyText) }
            text = stringResource(R.string.render_memory_struggle_title)
            detail = stringResource(R.string.viewer_proc_struggle_line)
        }
        processing -> {
            icon = { ProgressRing(status.photoProgress) }
            if (paused) {
                // The render stops only at its next checkpoint.
                text = stringResource(R.string.viewer_proc_pausing)
                detail = status.pausingText ?: stringResource(R.string.render_queue_pausing)
                button = stringResource(R.string.render_queue_resume) to { setPaused(false) }
            } else {
                text = stringResource(R.string.viewer_proc_rendering, (status.photoProgress * 100).roundToInt())
                detail = status.stageText.takeIf { it != 0 }?.let { stringResource(it) }
                button = stringResource(R.string.render_queue_pause) to { setPaused(true) }
            }
        }
        failed -> {
            icon = { LineStateIcon(AppIcons.AlertLine, MaterialTheme.colorScheme.error) }
            text = stringResource(R.string.viewer_proc_failed)
            detail = stringResource(R.string.viewer_proc_raw_safe)
            button = stringResource(R.string.viewer_proc_retry) to { scope.launch { RenderQueue.retry(context, listOfNotNull(job)) } }
        }
        job?.state == RenderJob.State.SKIPPED -> {
            icon = { LineStateIcon(AppIcons.PauseLine, SonySecondary) }
            text = stringResource(R.string.render_chip_skipped)
            detail = stringResource(R.string.viewer_proc_raw_safe)
            button = stringResource(R.string.viewer_proc_render) to {
                scope.launch {
                    RenderQueue.unskip(context, photoId)
                    reload++
                }
            }
        }
        paused -> {
            icon = { LineStateIcon(AppIcons.PauseLine, SonySecondary) }
            text = stringResource(R.string.render_chip_paused)
            detail = stringResource(R.string.viewer_proc_paused_line)
            button = stringResource(R.string.render_queue_resume) to { setPaused(false) }
        }
        else -> {
            val position = jobs.filter { it.state == RenderJob.State.QUEUED && it.photoId != status.currentPhotoId }
                .indexOfFirst { it.photoId == photoId } + 1
            icon = { LineStateIcon(AppIcons.ClockLine, SonySecondary) }
            if (position == 1 && status.running && status.waitingForMemory) {
                text = stringResource(R.string.render_queue_stage_waiting_memory)
            } else {
                text = stringResource(R.string.viewer_proc_waiting)
                // RuleBasedNumberFormat is not in the public SDK; MessageFormat's ordinal type uses it internally.
                val ordinal = MessageFormat("{0,ordinal}", context.resources.configuration.locales[0]).format(arrayOf(position))
                detail = stringResource(R.string.viewer_proc_in_line, ordinal)
                val next = stringResource(R.string.viewer_proc_next)
                button = stringResource(R.string.viewer_proc_render_now) to {
                    scope.launch {
                        RenderQueue.renderNext(context, photoId)
                        reload++
                    }
                    android.widget.Toast.makeText(context, next, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val description = stringResource(R.string.render_chip_description, listOfNotNull(text, detail).joinToString(" · "))
    ProcessingLine(modifier, icon, text, detail, failed) {
        button?.let { (label, action) -> ViewerPill(onClick = action.takeUnless { ghost }, text = label) }
        ViewerPill(
            onClick = onClick.takeUnless { ghost },
            text = stringResource(R.string.viewer_proc_queue),
            trailing = AppIcons.ChevronRightLine,
            description = description,
        )
    }
}

/** 28 dp: the line's state icon, or its progress ring, sits in this box. */
private val ProcessingIconSize = 28.dp

/** The processing line's layout: the state icon, a main line with an optional [detail] under it, then [buttons]. */
@Composable
private fun ProcessingLine(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    text: String,
    detail: String? = null,
    error: Boolean = false,
    buttons: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(ProcessingIconSize), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f)) {
            val shadow = TextStyle(shadow = ViewfinderTextShadow)
            Text(text, color = if (error) MaterialTheme.colorScheme.error else SonyText, fontSize = 14.sp, lineHeight = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = shadow)
            if (detail != null) {
                Text(detail, color = SonySecondary, fontSize = 12.sp, lineHeight = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = shadow)
            }
        }
        buttons()
    }
}

@Composable
private fun LineStateIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}

/** The render's share done as an orange arc over a dark track. */
@Composable
private fun ProgressRing(progress: Float) {
    val amber = ModeAmber
    Canvas(Modifier.size(ProcessingIconSize)) {
        val stroke = 3.dp.toPx()
        val inset = 3.dp.toPx()  // an 11 dp radius in the 28 dp box
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(SonyTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(amber, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
            style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}
