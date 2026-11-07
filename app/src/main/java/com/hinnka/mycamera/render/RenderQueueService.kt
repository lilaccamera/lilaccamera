package com.hinnka.mycamera.render

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hinnka.mycamera.R
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.RenderProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service in the `:queue` process that renders the [RenderQueue] one photo at a time,
 * each in a fresh `:render` process ([RawRenderService]). Its own process, so the UI process (and
 * the GPU memory it holds) can end while photos render. It touches only job files, never Room or
 * settings: it tells the main process about each photo's end ([RenderQueue.ACTION_PHOTO_ENDED]) and
 * its progress ([RenderQueue.ACTION_STATUS]). Pause takes effect between photos, or at the current
 * photo's next checkpoint, which the render stops at.
 */
class RenderQueueService : Service() {
    // Main thread: start commands, stage broadcasts and the loop's stop decision never interleave.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var lastStartId = 0
    private var rescanRequested = false
    private var done = 0
    /** Photos rendered (DONE) in this run, in order; the screen lists them until the queue stops. */
    private val finishedIds = mutableListOf<String>()
    private var total = 0
    private var currentPhotoId: String? = null
    private var stage: RawRenderService.Stage? = null
    private val lowMemoryRetries = mutableMapOf<String, Int>()
    private var renderPssKb: Int? = null
    private var failedThisRun = 0
    private var waitingForMemory = false
    private var stageStartedAt = 0L
    private var photoStartedAt = 0L
    private var resumed = false
    private var rawRenderRan = false
    /** The current photo's job as it was before its render started. */
    private var startedJob: RenderJob? = null
    /** The photo whose render reported PAUSED. */
    private var pausedPhotoId: String? = null
    private var statusSentAt = 0L
    /** A throttled [RenderQueue.ACTION_STATUS] that is due. */
    private var statusSend: Job? = null

    private val statusRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (loop?.isActive == true) sendStatus(immediately = true)
        }
    }

    private val stageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val photoId = intent.getStringExtra(RawRenderService.EXTRA_PHOTO_ID)
            val received = RawRenderService.Stage.entries
                .firstOrNull { it.name == intent.getStringExtra(RawRenderService.EXTRA_STAGE) } ?: return
            // Booked before the check: it may arrive once the photo is no longer current.
            if (received == RawRenderService.Stage.PAUSED) pausedPhotoId = photoId
            if (photoId != currentPhotoId) return
            // Only the notification follows stages: after DONE, FAILED or PAUSED the render ends its process,
            // which the connection reports.
            if (received != stage) {
                val now = SystemClock.elapsedRealtime()
                if (stage == RawRenderService.Stage.RENDERING && received == RawRenderService.Stage.CHECKPOINT) {
                    rawRenderAvgMs += (now - stageStartedAt - rawRenderAvgMs) / ++rawRenderCount
                    rawRenderRan = true
                }
                if (received == RawRenderService.Stage.RESUMED) resumed = true
                stageStartedAt = now
            }
            stage = received
            renderPssKb = intent.getIntExtra(RawRenderService.EXTRA_PSS_KB, 0).takeIf { it > 0 }
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        ContextCompat.registerReceiver(
            this, stageReceiver, IntentFilter(RawRenderService.ACTION_STAGE), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, statusRequestReceiver, IntentFilter(RenderQueue.ACTION_REQUEST_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Every scan in this process (the loop's own, one per start command, a skip) updates the set, so a
        // photo taken mid-render counts at once instead of after the current photo.
        scope.launch {
            RenderQueue.pendingPhotoIds.collect { pending ->
                if (loop?.isActive != true) return@collect
                // A scan that read the job just before it turned DONE may still list it.
                total = done + pending.count { it !in finishedIds }
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> RenderQueue.setPaused(this, true)
            ACTION_RESUME -> RenderQueue.setPaused(this, false)
            ACTION_SKIP -> intent.getStringExtra(RawRenderService.EXTRA_PHOTO_ID)?.let { RenderQueue.launchSkip(this, it) }
        }
        // Every start comes from startForegroundService or a getForegroundService action.
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        try {
            // Not ServiceCompat: on Android 16 it dropped mediaProcessing and passed type none, which is refused.
            if (type != 0) startForeground(NOTIFICATION_ID, buildProgress(), type)
            else startForeground(NOTIFICATION_ID, buildProgress())
        } catch (e: Exception) {
            // E.g. ForegroundServiceStartNotAllowedException once the app is in the background. A crash here
            // would repeat on every app start while a job waits; the job stays queued for the next start.
            PLog.e(TAG, "Cannot run the render queue in the foreground; trying again on the next app start", e)
            if (loop?.isActive != true) stopSelf(startId)
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java).cancel(PAUSED_NOTIFICATION_ID)
        lastStartId = startId
        rescanRequested = true
        if (loop?.isActive != true) {
            loop = scope.launch { runQueue() }
        } else {
            // A capture in the main process starts the service again: count its job now, not after this photo.
            scope.launch { RenderQueue.scan(this@RenderQueueService) }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        // A running render finishes on its own; its DONE result is applied by the next scan.
        PLog.w(TAG, "Foreground time limit reached; stopping, queued jobs stay queued")
        loop?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        RenderQueue.publishStatus(this, RenderQueue.Status())
        scope.cancel()
        unregisterReceiver(stageReceiver)
        unregisterReceiver(statusRequestReceiver)
        super.onDestroy()
    }

    private suspend fun runQueue() {
        done = 0
        finishedIds.clear()
        while (true) {
            rescanRequested = false
            val queued = RenderQueue.scan(this)
            total = done + queued.size
            if (queued.isEmpty()) {
                if (rescanRequested) continue // a job may have been written after the scan began
                stopQueue()
                return
            }
            if (RenderQueue.isPaused(this)) {
                stopQueue()
                notifyPaused(this, queued.size)
                return
            }
            attempt(queued.first())
        }
    }

    /** Renders [job] once and books the outcome. */
    private suspend fun attempt(queued: RenderJob) {
        val photoId = queued.photoId
        if (queued.attempts >= MAX_ATTEMPTS) return markFailed(queued)
        awaitRenderProcessGone()
        // Before the memory wait: the camera's memory is back once it stops shooting.
        RenderQueue.awaitCameraHold(this)
        awaitFreeMemory()
        // Pause applies between photos, including while waiting for memory; nothing is counted.
        if (RenderQueue.isPaused(this)) return
        // Under the lock, a skip lands either before this (the job is no longer QUEUED) or on this render.
        val job = RenderQueue.withJobLock(this) {
            val current = withContext(Dispatchers.IO) { RenderJob.read(this@RenderQueueService, photoId) }
                ?.takeIf { it.state == RenderJob.State.QUEUED } ?: return // skipped while it waited
            // Counted before binding, so a render that takes this process down still counts.
            if (!withContext(Dispatchers.IO) { RenderJob.write(this@RenderQueueService, current.copy(attempts = current.attempts + 1)) }) {
                PLog.e(TAG, "Cannot count the attempt for $photoId; stopping the queue")
                return stopQueue()
            }
            RenderQueue.renderingPhotoId = photoId
            RenderQueue.renderSkipped = false
            current
        }
        val startedAt = System.currentTimeMillis()
        startedJob = job
        pausedPhotoId = null
        val finished = renderOnce(photoId)
        // A hung render is killed at once and counts as a failed attempt.
        awaitRenderProcessGone(if (finished) RENDER_EXIT_TIMEOUT_MS else 0L)
        var retryAfterLowMemory = false
        var ended: RenderJob.State? = null
        RenderQueue.withJobLock<Unit>(this) {
            val after = withContext(Dispatchers.IO) { RenderJob.read(this@RenderQueueService, photoId) }
            // The flag too: the render may have written its job (still QUEUED) after the skip did.
            val skipped = RenderQueue.renderSkipped || after?.state == RenderJob.State.SKIPPED
            RenderQueue.renderingPhotoId = null
            when {
                after == null -> PLog.w(TAG, "Render job for $photoId disappeared")
                after.state == RenderJob.State.DONE -> {
                    // The main process applies the render result to Room, now if it runs, else at its next start.
                    ended = RenderJob.State.DONE
                    // Full renders only: a resumed one would pull the estimate down.
                    if (rawRenderRan) photoAvgMs += (SystemClock.elapsedRealtime() - photoStartedAt - photoAvgMs) / ++photoCount
                    done++
                    finishedIds += photoId
                }
                // The user ended it: neither a failure nor a low-memory kill; the loop goes on with the next photo.
                skipped -> {
                    PLog.i(TAG, "Render of $photoId skipped; not counting it")
                    withContext(Dispatchers.IO) {
                        RenderJob.write(this@RenderQueueService, after.copy(state = RenderJob.State.SKIPPED, attempts = job.attempts))
                        RawRenderCheckpoint.delete(this@RenderQueueService, photoId)
                    }
                    ended = RenderJob.State.SKIPPED
                }
                // Not a failure: the render stopped at a checkpoint and kept its progress; the loop then stops paused.
                pausedPhotoId == photoId -> {
                    PLog.i(TAG, "Render of $photoId paused (rawRendered=${after.rawRendered}, jpegDone=${after.jpegDone}); not counting it")
                    withContext(Dispatchers.IO) { RenderJob.write(this@RenderQueueService, after.copy(attempts = job.attempts)) }
                }
                // Capped, so a phone that always kills the render does not retry (and heat up) forever.
                wasLowMemoryKill(startedAt) &&
                    lowMemoryRetries.merge(photoId, 1, Int::plus)!! <= MAX_LOW_MEMORY_RETRIES -> {
                    PLog.w(TAG, "Render of $photoId was killed for low memory; retrying without counting it")
                    withContext(Dispatchers.IO) { RenderJob.write(this@RenderQueueService, after.copy(attempts = job.attempts)) }
                    retryAfterLowMemory = true
                }
                after.attempts >= MAX_ATTEMPTS -> {
                    markFailed(after)
                    ended = RenderJob.State.FAILED
                }
                else -> PLog.w(TAG, "Render of $photoId failed (attempt ${after.attempts} of $MAX_ATTEMPTS)")
            }
        }
        ended?.let { state ->
            lowMemoryRetries.remove(photoId)
            // Scanned first, so no status sent after this still lists the photo as pending.
            RenderQueue.scan(this)
            RenderQueue.publishPhotoEnded(this, photoId, state)
        }
        // Outside the lock, so a skip meanwhile does not wait for it.
        if (retryAfterLowMemory) delay(LOW_MEMORY_RETRY_DELAY_MS)
    }

    private suspend fun markFailed(job: RenderJob) {
        // The DNG stays; only the job leaves the queue.
        PLog.e(TAG, "Render of ${job.photoId} failed $MAX_ATTEMPTS times; giving up")
        withContext(Dispatchers.IO) {
            RenderJob.write(this@RenderQueueService, job.copy(state = RenderJob.State.FAILED))
            RawRenderCheckpoint.delete(this@RenderQueueService, job.photoId)
        }
        done++
        failedThisRun++
        notifyFailed(this, failedThisRun)
    }

    /** Binds the render service once; false if its process did not go away within [RENDER_TIMEOUT_MS]. */
    private suspend fun renderOnce(photoId: String): Boolean {
        val ended = CompletableDeferred<Unit>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
            override fun onServiceDisconnected(name: ComponentName) { ended.complete(Unit) }
            override fun onBindingDied(name: ComponentName) { ended.complete(Unit) }
            override fun onNullBinding(name: ComponentName) { ended.complete(Unit) }
        }
        currentPhotoId = photoId
        stage = null
        renderPssKb = null
        resumed = false
        rawRenderRan = false
        photoStartedAt = SystemClock.elapsedRealtime()
        updateNotification()
        return try {
            val intent = Intent(this, RawRenderService::class.java).putExtra(RawRenderService.EXTRA_PHOTO_ID, photoId)
            // Not perceptible: under memory pressure the system kills the render (the queue retries it)
            // before this process. Without it the bind raised :render above the queue, which was
            // killed instead and left the queue stopped until the next app start.
            if (bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_NOT_PERCEPTIBLE)) {
                (withTimeoutOrNull(RENDER_TIMEOUT_MS) { ended.await() } != null).also { finished ->
                    if (!finished) PLog.e(TAG, "Render of $photoId did not finish in ${RENDER_TIMEOUT_MS / 1000} s")
                }
            } else {
                PLog.e(TAG, "Cannot bind the render service for $photoId")
                true
            }
        } finally {
            // After onDestroy the system has already dropped the connection.
            runCatching { unbindService(connection) }
            currentPhotoId = null
        }
    }

    /**
     * Waits until no `:render` process is listed. The render service ends its process after each
     * job, and a bind into a still-running one would be ignored. One that outlives the timeout
     * (left from an earlier app run, or stuck) is killed.
     */
    private suspend fun awaitRenderProcessGone(killAfterMs: Long = RENDER_EXIT_TIMEOUT_MS) {
        val deadline = SystemClock.elapsedRealtime() + killAfterMs
        var killed = false
        while (true) {
            val pid = RenderQueue.renderProcessPid(this) ?: return
            if (!killed && SystemClock.elapsedRealtime() > deadline) {
                PLog.w(TAG, "Render process $pid still running; killing it")
                Process.killProcess(pid)
                killed = true
            }
            delay(PROCESS_POLL_MS)
        }
    }

    /**
     * Waits until the phone has room for a render's peak before starting it: a render the system
     * kills halfway wastes its time and makes the system close other apps. Gives up after
     * [MEMORY_WAIT_MAX_MS] and starts anyway, so a phone that never has that much still renders.
     */
    private suspend fun awaitFreeMemory() {
        val deadline = SystemClock.elapsedRealtime() + MEMORY_WAIT_MAX_MS
        while (freeMemoryMb() < MIN_FREE_MB_TO_START && SystemClock.elapsedRealtime() < deadline) {
            if (RenderQueue.isPaused(this)) break
            if (!waitingForMemory) PLog.i(TAG, "Waiting for free memory: ${freeMemoryMb()} MB < $MIN_FREE_MB_TO_START MB")
            waitingForMemory = true
            updateNotification()
            delay(MEMORY_POLL_MS)
        }
        waitingForMemory = false
    }

    private fun freeMemoryMb(): Int = RenderQueue.freeMemoryMb(this)

    private suspend fun wasLowMemoryKill(since: Long): Boolean {
        val processName = packageName + RenderProcess.PROCESS_SUFFIX
        val activityManager = getSystemService(ActivityManager::class.java)
        // The exit record can land shortly after the process is gone.
        repeat(2) {
            activityManager.getHistoricalProcessExitReasons(packageName, 0, EXIT_REASONS_TO_READ)
                .firstOrNull { it.processName == processName && it.timestamp >= since }
                ?.let { return it.reason == ApplicationExitInfo.REASON_LOW_MEMORY }
            delay(EXIT_RECORD_WAIT_MS)
        }
        return false
    }

    /** Leaves the foreground and stops, unless a newer start command already arrived. */
    private fun stopQueue() {
        loop?.cancel()
        sendStatus(immediately = true)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun updateNotification() {
        if (loop?.isActive == true) {
            val status = currentStatus()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildProgress(status))
            sendStatus()
        }
    }

    /** Broadcasts the status to the main process, at most once per [STATUS_INTERVAL_MS] unless [immediately]. */
    private fun sendStatus(immediately: Boolean = false) {
        if (!immediately && statusSend?.isActive == true) return // a send is already due and reads the status then
        statusSend?.cancel()
        val wait = if (immediately) 0L else statusSentAt + STATUS_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait <= 0L) {
            broadcastStatus()
        } else {
            statusSend = scope.launch {
                delay(wait)
                broadcastStatus()
            }
        }
    }

    private fun broadcastStatus() {
        statusSentAt = SystemClock.elapsedRealtime()
        RenderQueue.publishStatus(this, if (loop?.isActive == true) currentStatus() else RenderQueue.Status())
    }

    private fun currentStatus(): RenderQueue.Status {
        val photoProgress = photoProgress()
        return RenderQueue.Status(
            running = true,
            waitingForMemory = waitingForMemory,
            done = done,
            total = total,
            currentPhotoId = currentPhotoId,
            stageText = stageText(),
            renderPssKb = renderPssKb,
            photoProgress = photoProgress,
            photoStartedAt = photoStartedAt,
            resumed = resumed,
            leftMs = ((total - done - photoProgress) * photoAvgMs).toLong().coerceAtLeast(0L),
            finishedPhotoIds = finishedIds.toList(),
            pausingText = pausingText(photoProgress),
            memoryKills = lowMemoryRetries.values.maxOrNull() ?: 0,
        )
    }

    /** What a pause would wait for now: the render stops only at its next checkpoint, if one is left. */
    private fun pausingText(photoProgress: Float): String? {
        val job = startedJob?.takeIf { currentPhotoId != null } ?: return null
        val next = when (stage) {
            RawRenderService.Stage.RENDERING, RawRenderService.Stage.CHECKPOINT -> RawRenderService.Stage.CHECKPOINT
            RawRenderService.Stage.JPEG -> RawRenderService.Stage.JPEG
            RawRenderService.Stage.HDR, RawRenderService.Stage.SAVING, RawRenderService.Stage.DONE -> null
            // Before the render takes its path, the job's saved progress says which checkpoints are left.
            else -> when {
                job.jpegDone -> null
                job.rawRendered -> RawRenderService.Stage.JPEG
                else -> RawRenderService.Stage.CHECKPOINT
            }
        }
        return when (next) {
            RawRenderService.Stage.CHECKPOINT -> getString(
                R.string.render_queue_pausing_after_raw,
                DateUtils.formatElapsedTime(
                    ((RawRenderService.Stage.CHECKPOINT.end - photoProgress) * photoAvgMs / 1000).toLong().coerceAtLeast(0L)
                ),
            )
            RawRenderService.Stage.JPEG -> getString(R.string.render_queue_pausing_after_jpeg)
            else -> getString(R.string.render_queue_pausing)
        }
    }

    /** The current photo's share done: its stage's start, advanced by time inside the RAW render. */
    private fun photoProgress(): Float {
        val current = stage?.takeIf { currentPhotoId != null } ?: return 0f
        if (current != RawRenderService.Stage.RENDERING) return current.start
        // The renderer reports no progress of its own; capped so it never looks done early.
        val ratio = (SystemClock.elapsedRealtime() - stageStartedAt) / rawRenderAvgMs
        return current.start + (current.end - current.start) * ratio.coerceAtMost(RAW_RENDER_PROGRESS_CAP)
    }

    /** The render process's memory and what the phone has left, to see how close a render is to a kill. */
    private fun memoryText(): String {
        val freeMb = freeMemoryMb()
        return renderPssKb?.let { getString(R.string.render_queue_memory, it / 1024, freeMb) }
            ?: getString(R.string.render_queue_memory_free, freeMb)
    }

    private fun stageText(): Int = if (waitingForMemory) R.string.render_queue_stage_waiting_memory else when (stage) {
        null -> R.string.render_queue_stage_waiting
        RawRenderService.Stage.STARTED -> R.string.render_queue_stage_started
        RawRenderService.Stage.RENDERING -> R.string.render_queue_stage_rendering
        RawRenderService.Stage.CHECKPOINT -> R.string.render_queue_stage_checkpoint
        RawRenderService.Stage.RESUMED -> R.string.render_queue_stage_resumed
        RawRenderService.Stage.JPEG -> R.string.render_queue_stage_jpeg
        RawRenderService.Stage.HDR -> R.string.render_queue_stage_hdr
        RawRenderService.Stage.SAVING -> R.string.render_queue_stage_done
        RawRenderService.Stage.DONE -> R.string.render_queue_stage_finished
        RawRenderService.Stage.FAILED -> R.string.render_queue_stage_failed
        RawRenderService.Stage.PAUSED -> R.string.render_queue_stage_paused
    }

    private fun buildProgress(status: RenderQueue.Status = currentStatus()): Notification {
        val paused = RenderQueue.isPaused(this)
        val stageText = getString(status.stageText)
        val struggling = !paused && status.memoryKills >= RenderQueue.MEMORY_HINT_KILLS
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (total == 0) getString(R.string.render_queue_channel)
                else getString(
                    R.string.render_queue_title_left,
                    (done + 1).coerceAtMost(total),
                    total,
                    DateUtils.formatElapsedTime(status.leftMs / 1000),
                )
            )
            // What happens now (or where a pause will stop) in the text; memory, kept as an indicator, in the header.
            .setContentText(
                if (struggling) getString(R.string.render_memory_struggle_short)
                else status.pausingText?.takeIf { paused }
                    ?: if (status.resumed && stage != RawRenderService.Stage.RESUMED) {
                        getString(R.string.render_queue_joined, stageText, getString(R.string.render_queue_stage_resumed))
                    } else stageText
            )
            // Expanded, the notification says how to get a photo Android keeps stopping through.
            .setStyle(if (struggling) NotificationCompat.BigTextStyle().bigText(getString(R.string.render_memory_struggle)) else null)
            .setSubText(memoryText())
            .setContentIntent(openAppIntent(this))
            .setProgress(PROGRESS_MAX, (status.progress * PROGRESS_MAX).toInt(), total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                0,
                getString(if (paused) R.string.render_queue_resume else R.string.render_queue_pause),
                actionIntent(this, if (paused) ACTION_RESUME else ACTION_PAUSE),
            )
            .build()
    }

    companion object {
        private const val TAG = "RenderQueueService"
        private const val CHANNEL_ID = "raw_render_queue"
        private const val NOTIFICATION_ID = 12042
        private const val PAUSED_NOTIFICATION_ID = 12043
        private const val FAILED_NOTIFICATION_ID = 12044
        private const val FAILED_CHANNEL_ID = "raw_render_failed"
        private const val ACTION_PAUSE = "com.hinnka.mycamera.render.action.PAUSE"
        private const val ACTION_RESUME = "com.hinnka.mycamera.render.action.RESUME"
        /** Skips the photo in [RawRenderService.EXTRA_PHOTO_ID], ending its render if it runs ([RenderQueue.skip]). */
        const val ACTION_SKIP = "com.hinnka.mycamera.render.action.SKIP"
        private const val STATUS_INTERVAL_MS = 1_000L
        private const val MAX_ATTEMPTS = 3
        private const val MAX_LOW_MEMORY_RETRIES = 5
        // Measured on a 4 GB phone: renders that started with 200-330 MB free were killed, ones that
        // started with 517 and 690-940 MB free finished. GPU and camera driver pools hold memory that
        // availMem does not count but a render can still get. shortcut: few data points; retune with more.
        private const val MIN_FREE_MB_TO_START = 450
        private const val MEMORY_WAIT_MAX_MS = 60_000L
        private const val MEMORY_POLL_MS = 3_000L
        private const val LOW_MEMORY_RETRY_DELAY_MS = 5_000L
        // A full-size render takes about 25 s on a 2017 phone.
        private const val RENDER_TIMEOUT_MS = 300_000L
        private const val RENDER_EXIT_TIMEOUT_MS = 180_000L
        private const val PROCESS_POLL_MS = 200L
        private const val EXIT_REASONS_TO_READ = 4
        private const val EXIT_RECORD_WAIT_MS = 1_000L
        private const val PROGRESS_MAX = 1000
        private const val RAW_RENDER_PROGRESS_CAP = 0.97f

        // Running means over this process's renders (main thread only), so estimates follow the
        // phone; they last as long as the :queue process. First guesses from a 5056x3792 render on a
        // 2017 phone: RAW render 24 s of 35 s.
        private var rawRenderAvgMs = 24_000f
        private var rawRenderCount = 0
        private var photoAvgMs = 35_000f
        private var photoCount = 0

        private fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.render_queue_channel), NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getForegroundService(
            context,
            action.hashCode(),
            Intent(context, RenderQueueService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Tapping a queue notification opens the app, as the failed notice always did. */
        private fun openAppIntent(context: Context): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

        /**
         * A normal (not silent) notice that photos could not be finished, on its own channel so it
         * is seen after the progress notification has gone; tapping it opens the app.
         */
        fun notifyFailed(context: Context, count: Int) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    FAILED_CHANNEL_ID,
                    context.getString(R.string.render_failed_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            val text = context.getString(R.string.render_failed_text)
            val notification = NotificationCompat.Builder(context, FAILED_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.resources.getQuantityString(R.plurals.render_failed_title, count, count))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
            manager.notify(FAILED_NOTIFICATION_ID, notification)
        }

        /** The notice left while paused; Resume starts the service again. */
        fun notifyPaused(context: Context, waiting: Int) {
            createChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.render_queue_paused))
                .setContentText(context.resources.getQuantityString(R.plurals.render_queue_paused_detail, waiting, waiting))
                .setContentIntent(openAppIntent(context))
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(0, context.getString(R.string.render_queue_resume), actionIntent(context, ACTION_RESUME))
                .build()
            context.getSystemService(NotificationManager::class.java).notify(PAUSED_NOTIFICATION_ID, notification)
        }
    }
}
