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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.GalleryManager
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
 * Main-process foreground service that renders the [RenderQueue] one photo at a time, each in a
 * fresh `:render` process ([RawRenderService]). Pause takes effect between photos.
 */
class RenderQueueService : Service() {
    // Main thread: start commands, stage broadcasts and the loop's stop decision never interleave.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var lastStartId = 0
    private var rescanRequested = false
    private var done = 0
    private var total = 0
    private var currentPhotoId: String? = null
    private var stage: RawRenderService.Stage? = null
    private val lowMemoryRetries = mutableMapOf<String, Int>()
    private var renderPssKb: Int? = null
    private var failedThisRun = 0
    private var waitingForMemory = false

    private val stageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(RawRenderService.EXTRA_PHOTO_ID) != currentPhotoId) return
            val received = RawRenderService.Stage.entries
                .firstOrNull { it.name == intent.getStringExtra(RawRenderService.EXTRA_STAGE) } ?: return
            // Only the notification follows stages: after DONE or FAILED the render ends its process,
            // which the connection reports.
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> RenderQueue.setPaused(this, true)
            ACTION_RESUME -> RenderQueue.setPaused(this, false)
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
        if (loop?.isActive != true) loop = scope.launch { runQueue() }
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
        RenderQueue.publishStatus(RenderQueue.Status())
        scope.cancel()
        unregisterReceiver(stageReceiver)
        super.onDestroy()
    }

    private suspend fun runQueue() {
        done = 0
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
    private suspend fun attempt(job: RenderJob) {
        val photoId = job.photoId
        if (job.attempts >= MAX_ATTEMPTS) return markFailed(job)
        awaitRenderProcessGone()
        awaitFreeMemory()
        // Pause applies between photos, including while waiting for memory; nothing is counted.
        if (RenderQueue.isPaused(this)) return
        // Counted before binding, so a render that takes the main process down still counts.
        if (!withContext(Dispatchers.IO) { RenderJob.write(this@RenderQueueService, job.copy(attempts = job.attempts + 1)) }) {
            PLog.e(TAG, "Cannot count the attempt for $photoId; stopping the queue")
            return stopQueue()
        }
        val startedAt = System.currentTimeMillis()
        val finished = renderOnce(photoId)
        // A hung render is killed at once and counts as a failed attempt.
        awaitRenderProcessGone(if (finished) RENDER_EXIT_TIMEOUT_MS else 0L)
        val after = withContext(Dispatchers.IO) { RenderJob.read(this@RenderQueueService, photoId) }
        when {
            after == null -> PLog.w(TAG, "Render job for $photoId disappeared")
            after.state == RenderJob.State.DONE -> {
                GalleryManager.applyRenderResult(this, photoId)
                done++
            }
            // Capped, so a phone that always kills the render does not retry (and heat up) forever.
            wasLowMemoryKill(startedAt) &&
                lowMemoryRetries.merge(photoId, 1, Int::plus)!! <= MAX_LOW_MEMORY_RETRIES -> {
                PLog.w(TAG, "Render of $photoId was killed for low memory; retrying without counting it")
                withContext(Dispatchers.IO) { RenderJob.write(this@RenderQueueService, after.copy(attempts = job.attempts)) }
                delay(LOW_MEMORY_RETRY_DELAY_MS)
            }
            after.attempts >= MAX_ATTEMPTS -> markFailed(after)
            else -> PLog.w(TAG, "Render of $photoId failed (attempt ${after.attempts} of $MAX_ATTEMPTS)")
        }
    }

    private suspend fun markFailed(job: RenderJob) {
        // The DNG stays; only the job leaves the queue.
        PLog.e(TAG, "Render of ${job.photoId} failed $MAX_ATTEMPTS times; giving up")
        withContext(Dispatchers.IO) { RenderJob.write(this@RenderQueueService, job.copy(state = RenderJob.State.FAILED)) }
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
        val processName = packageName + RenderProcess.PROCESS_SUFFIX
        val activityManager = getSystemService(ActivityManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + killAfterMs
        var killed = false
        while (true) {
            val process = activityManager.runningAppProcesses?.firstOrNull { it.processName == processName } ?: return
            if (!killed && SystemClock.elapsedRealtime() > deadline) {
                PLog.w(TAG, "Render process ${process.pid} still running; killing it")
                Process.killProcess(process.pid)
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
        RenderQueue.publishStatus(RenderQueue.Status())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun updateNotification() {
        if (loop?.isActive == true) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildProgress())
            RenderQueue.publishStatus(
                RenderQueue.Status(true, waitingForMemory, done, total, currentPhotoId, stageText(), renderPssKb)
            )
        }
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
        RawRenderService.Stage.DONE -> R.string.render_queue_stage_done
        RawRenderService.Stage.FAILED -> R.string.render_queue_stage_failed
    }

    private fun buildProgress(): Notification {
        val paused = RenderQueue.isPaused(this)
        val stageText = stageText()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (total == 0) getString(R.string.render_queue_channel)
                else getString(R.string.render_queue_title, (done + 1).coerceAtMost(total), total)
            )
            // Memory first: it is what the user watches; the stage goes in the header.
            .setContentText(memoryText())
            .setSubText(getString(stageText))
            .setProgress(total, done, total == 0)
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
        private const val MAX_ATTEMPTS = 3
        private const val MAX_LOW_MEMORY_RETRIES = 5
        // Measured on a 4 GB phone: renders that started with 690-940 MB free finished, those that
        // started with 200-330 MB free were killed (the render peaks near 1.4 GB, reclaiming caches).
        private const val MIN_FREE_MB_TO_START = 600
        private const val MEMORY_WAIT_MAX_MS = 120_000L
        private const val MEMORY_POLL_MS = 3_000L
        private const val LOW_MEMORY_RETRY_DELAY_MS = 5_000L
        // A full-size render takes about 25 s on a 2017 phone.
        private const val RENDER_TIMEOUT_MS = 300_000L
        private const val RENDER_EXIT_TIMEOUT_MS = 180_000L
        private const val PROCESS_POLL_MS = 200L
        private const val EXIT_REASONS_TO_READ = 4
        private const val EXIT_RECORD_WAIT_MS = 1_000L

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
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val notification = NotificationCompat.Builder(context, FAILED_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.resources.getQuantityString(R.plurals.render_failed_title, count, count))
                .setContentText(context.getString(R.string.render_failed_text))
                .setContentIntent(open)
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
                .setContentTitle(context.getString(R.string.render_queue_paused, waiting))
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(0, context.getString(R.string.render_queue_resume), actionIntent(context, ACTION_RESUME))
                .build()
            context.getSystemService(NotificationManager::class.java).notify(PAUSED_NOTIFICATION_ID, notification)
        }
    }
}
