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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.GalleryManager
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
 * Queued and failed RAW renders with the queue's live state. Draws only small CPU-decoded
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
                if (paused) Notice(stringResource(R.string.render_queue_pausing))
                RenderProgressBar(status.progress)
                // Memory is awaited before a photo starts, so this and the photo's own lines never show together.
                if (status.waitingForMemory) Notice(stringResource(status.stageText))
                status.currentPhotoId?.let { photoId ->
                    StatusLine(stringResource(R.string.render_queue_photo, photoId))
                    StatusLine(
                        stringResource(
                            R.string.render_queue_joined,
                            stringResource(status.stageText),
                            DateUtils.formatElapsedTime(((now - status.photoStartedAt) / 1000).coerceAtLeast(0L))
                        )
                    )
                    if (status.resumed && status.stageText != R.string.render_queue_stage_resumed) {
                        StatusLine(stringResource(R.string.render_queue_stage_resumed))
                    }
                    RenderProgressBar(status.photoProgress)
                }
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
            if (jobs.isEmpty()) {
                StatusLine(stringResource(R.string.render_queue_empty))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(jobs, key = { it.photoId }) { job ->
                    RenderJobRow(
                        job = job,
                        stageText = status.stageText.takeIf { status.running && job.photoId == status.currentPhotoId },
                        onRetry = { scope.launch { RenderQueue.retry(context, listOf(job)) } }
                    )
                }
            }
        }
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
private fun RenderProgressBar(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color = AccentColor,
        trackColor = Color.White.copy(alpha = 0.15f)
    )
}

@Composable
private fun RenderJobRow(job: RenderJob, stageText: Int?, onRetry: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, job.photoId) {
        value = withContext(Dispatchers.IO) {
            // Software decode (never a hardware bitmap): thumbnails are at most 512 px, a quarter fits the row.
            BitmapFactory.decodeFile(
                GalleryManager.getThumbnailFile(context, job.photoId).path,
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
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = stringResource(
                    when {
                        job.state == RenderJob.State.FAILED -> R.string.render_queue_stage_failed
                        stageText != null -> stageText
                        else -> R.string.render_queue_job_waiting
                    }
                ),
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.render_queue_attempts, job.attempts),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        if (job.state == RenderJob.State.FAILED) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.render_queue_retry), color = AccentColor)
            }
        }
    }
}
