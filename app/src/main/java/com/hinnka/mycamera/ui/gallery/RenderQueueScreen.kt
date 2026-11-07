package com.hinnka.mycamera.ui.gallery

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.render.RawRenderService
import com.hinnka.mycamera.render.RenderJob
import com.hinnka.mycamera.render.RenderQueue
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                Text(
                    text = stringResource(R.string.render_queue_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
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
