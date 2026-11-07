package com.hinnka.mycamera.ui.gallery

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.render.RawRenderService
import com.hinnka.mycamera.render.RenderJob
import com.hinnka.mycamera.render.RenderQueue
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.OnAccentColor
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.render_queue_screen_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when {
                    status.running -> stringResource(
                        R.string.render_queue_title_left,
                        (status.done + 1).coerceAtMost(status.total),
                        status.total,
                        DateUtils.formatElapsedTime(status.leftMs / 1000)
                    )
                    paused -> stringResource(R.string.render_queue_paused, pendingIds.size)
                    else -> stringResource(R.string.render_queue_idle, pendingIds.size)
                },
                color = if (paused && !status.running) AccentColor else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (status.running) {
                if (paused) status.pausingText?.let { Notice(it) }
                RenderProgressBar(status.progress)
                // Memory is awaited before a photo starts, so this never shows beside a current photo's row.
                if (status.waitingForMemory) Notice(stringResource(status.stageText))
            }
            StatusLine(
                status.renderPssKb?.takeIf { status.running }
                    ?.let { stringResource(R.string.render_queue_memory, it / 1024, freeMb) }
                    ?: stringResource(R.string.render_queue_memory_free, freeMb)
            )
            Text(
                text = stringResource(R.string.render_queue_hint),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Text(
                text = stringResource(R.string.render_queue_pause_hint),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        RenderQueue.setPaused(context, false)
                        paused = false
                        scope.launch { RenderQueue.retry(context, jobs) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = OnAccentColor)
                ) {
                    Text(stringResource(R.string.render_queue_render_now))
                }
                OutlinedButton(
                    onClick = {
                        RenderQueue.setPaused(context, !paused)
                        paused = !paused
                        if (!paused) RenderQueue.start(context)
                    }
                ) {
                    Text(
                        text = stringResource(if (paused) R.string.render_queue_resume else R.string.render_queue_pause),
                        color = Color.White
                    )
                }
            }
            if (jobs.isEmpty() && status.finishedPhotoIds.isEmpty()) {
                StatusLine(stringResource(R.string.render_queue_empty))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(status.finishedPhotoIds, key = { it }) { photoId ->
                    RenderJobRow(photoId, progress = 1f, label = stringResource(R.string.render_queue_stage_finished))
                }
                items(jobs.filter { it.photoId !in status.finishedPhotoIds }, key = { it.photoId }) { job ->
                    when {
                        job.state == RenderJob.State.FAILED -> RenderJobRow(
                            job.photoId,
                            progress = 1f,
                            label = pluralStringResource(R.plurals.render_queue_failed_after, job.attempts, job.attempts),
                            failed = true,
                            action = stringResource(R.string.render_queue_retry),
                            onAction = { scope.launch { RenderQueue.retry(context, listOf(job)) } }
                        )
                        job.state == RenderJob.State.SKIPPED -> RenderJobRow(
                            job.photoId,
                            progress = 0f,
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
                            detail = stringResource(R.string.render_queue_attempts, job.attempts).takeIf { job.attempts > 0 },
                            action = skipText,
                            onAction = { skip(job.photoId) }
                        )
                        else -> RenderJobRow(
                            job.photoId,
                            progress = 0f,
                            label = stringResource(R.string.render_queue_job_waiting),
                            detail = stringResource(R.string.render_queue_attempts, job.attempts).takeIf { job.attempts > 0 },
                            action = skipText,
                            onAction = { skip(job.photoId) }
                        )
                    }
                }
            }
        }
    }
    // Only the photo being rendered asks: skipping it throws away the render's progress.
    confirmSkipId?.let { photoId ->
        AlertDialog(
            onDismissRequest = { confirmSkipId = null },
            text = { Text(stringResource(R.string.render_queue_skip_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSkipId = null
                    skip(photoId)
                }) {
                    Text(skipText, color = AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkipId = null }) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF2D2D2D),
            textContentColor = Color.White
        )
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text = text, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
}

@Composable
private fun Notice(text: String) {
    Text(text = text, color = AccentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun RenderProgressBar(progress: Float, color: Color = AccentColor) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color = color,
        trackColor = Color.White.copy(alpha = 0.15f)
    )
}

@Composable
private fun RenderJobRow(
    photoId: String,
    progress: Float,
    label: String,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            thumbnail?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, color = Color.White, fontSize = 14.sp)
            detail?.let { Text(text = it, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
            RenderProgressBar(progress, if (failed) MaterialTheme.colorScheme.error else AccentColor)
        }
        action?.let {
            TextButton(onClick = onAction) {
                Text(it, color = AccentColor)
            }
        }
    }
}
