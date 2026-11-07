package com.hinnka.mycamera.render

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.db.GalleryMediaStore
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.RenderProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Queued RAW renders. The queue is the set of `photos/<id>/render_job.json` files in state QUEUED,
 * oldest DNG first; [RenderQueueService] works through it in the `:queue` process. The main process
 * follows it through [ACTION_STATUS] and [ACTION_PHOTO_ENDED] broadcasts and applies render results to Room.
 */
object RenderQueue {
    private const val TAG = "RenderQueue"
    private const val PAUSED_FILE = "render_queue_paused"
    private const val PREFS_NAME = "render_queue"
    private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
    private const val JOB_LOCK_FILE = "render_queue.lock"
    /** With no status for this long, the main process checks whether `:queue` still lives. */
    private const val STATUS_SILENCE_MS = 5_000L
    /** Holds back new renders while Pro shots are taken; a render and a RAW capture together got renders killed. */
    private const val CAMERA_HOLD_FILE = "render_camera_hold"
    /** New renders start this long after the last Pro shot was saved. */
    private const val CAMERA_IDLE_MS = 5_000L
    /** While a Pro shot is in flight; it ends early when the photo is saved, and by itself if a failure never says so. */
    private const val CAMERA_BUSY_HOLD_MS = 60_000L

    /** `:queue` to main: the [Status] in [EXTRA_STATUS], the pending and failed ids in [EXTRA_PENDING_IDS], [EXTRA_FAILED_IDS]. */
    const val ACTION_STATUS = "com.hinnka.mycamera.render.action.QUEUE_STATUS"
    /** Main to `:queue`: send [ACTION_STATUS] now, for a main process that just started following. */
    const val ACTION_REQUEST_STATUS = "com.hinnka.mycamera.render.action.REQUEST_QUEUE_STATUS"
    /** `:queue` to main: the photo in [RawRenderService.EXTRA_PHOTO_ID] ended in the [RenderJob.State] named in [EXTRA_JOB_STATE]. */
    const val ACTION_PHOTO_ENDED = "com.hinnka.mycamera.render.action.PHOTO_ENDED"
    const val EXTRA_STATUS = "com.hinnka.mycamera.render.extra.STATUS"
    const val EXTRA_PENDING_IDS = "com.hinnka.mycamera.render.extra.PENDING_IDS"
    const val EXTRA_FAILED_IDS = "com.hinnka.mycamera.render.extra.FAILED_IDS"
    const val EXTRA_JOB_STATE = "com.hinnka.mycamera.render.extra.JOB_STATE"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingPhotoIds = MutableStateFlow<Set<String>>(emptySet())

    /** Photos whose render is waiting or running, as of the last scan. */
    val pendingPhotoIds: StateFlow<Set<String>> = _pendingPhotoIds.asStateFlow()

    private val _failedPhotoIds = MutableStateFlow<Set<String>>(emptySet())

    /** Photos whose render gave up, as of the last scan. */
    val failedPhotoIds: StateFlow<Set<String>> = _failedPhotoIds.asStateFlow()

    /** What [RenderQueueService] is doing; [running] is false while the service is stopped. */
    data class Status(
        val running: Boolean = false,
        val waitingForMemory: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val currentPhotoId: String? = null,
        @StringRes val stageText: Int = 0,
        val renderPssKb: Int? = null,
        /** The current photo's share done, 0..1. */
        val photoProgress: Float = 0f,
        /** [android.os.SystemClock.elapsedRealtime] when the current photo's render was started, 0 if none. */
        val photoStartedAt: Long = 0L,
        /** True once the current photo's render resumed from a checkpoint. */
        val resumed: Boolean = false,
        /** Estimated time until the queue is through. */
        val leftMs: Long = 0L,
        /** Photos rendered in this run, in order; empty once the queue stops. */
        val finishedPhotoIds: List<String> = emptyList(),
        /** Shown while pause is requested: where the current photo's render will stop, or null with none rendering. */
        val pausingText: String? = null,
    ) : java.io.Serializable {
        /** Photos done plus the current photo's share, over all photos, 0..1. */
        val progress: Float get() = if (total == 0) 0f else ((done + photoProgress) / total).coerceIn(0f, 1f)
    }

    internal val jobMutex = Mutex()

    /**
     * Runs [block] holding the job lock, which orders a skip, retry or unskip against
     * [RenderQueueService] starting or booking that job's render. Job files are written from the
     * main process and `:queue`, so it is a file lock ([JOB_LOCK_FILE]); the mutex in front of it
     * serialises this process's coroutines, as one process cannot hold the same file lock twice.
     * [block] runs in the caller's context.
     */
    internal suspend inline fun <T> withJobLock(context: Context, block: () -> T): T = jobMutex.withLock {
        val channel = withContext(Dispatchers.IO) { RandomAccessFile(jobLockFile(context), "rw").channel }
        try {
            withContext(Dispatchers.IO) { channel.lock() }
            block()
        } finally {
            // Closing the channel releases the lock.
            channel.close()
        }
    }

    internal fun jobLockFile(context: Context) = File(context.filesDir, JOB_LOCK_FILE)

    /** `:queue`, under [withJobLock]: the photo [RenderQueueService] is rendering, from the attempt count until the outcome is booked. */
    internal var renderingPhotoId: String? = null

    /** `:queue`, under [withJobLock]: [renderingPhotoId] was skipped, so its render's end is booked as a skip. */
    internal var renderSkipped = false

    private val _status = MutableStateFlow(Status())

    /** Main process: the latest [ACTION_STATUS] from `:queue`, or a stopped [Status] while no service runs. */
    val status: StateFlow<Status> = _status.asStateFlow()

    /** `:queue`: tells the main process (if it runs) what the service is doing, with this process's pending and failed ids. */
    internal fun publishStatus(context: Context, status: Status) {
        context.sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(context.packageName)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_PENDING_IDS, _pendingPhotoIds.value.toTypedArray())
                .putExtra(EXTRA_FAILED_IDS, _failedPhotoIds.value.toTypedArray())
        )
    }

    /** `:queue`: the render of [photoId] was booked as [state]; the main process applies a DONE result to Room. */
    internal fun publishPhotoEnded(context: Context, photoId: String, state: RenderJob.State) {
        context.sendBroadcast(
            Intent(ACTION_PHOTO_ENDED)
                .setPackage(context.packageName)
                .putExtra(RawRenderService.EXTRA_PHOTO_ID, photoId)
                .putExtra(EXTRA_JOB_STATE, state.name)
        )
    }

    private val followingService = AtomicBoolean(false)

    /** Handled one at a time, so a status never overtakes the scan a photo's end started. */
    private val serviceEvents = Channel<Intent>(Channel.UNLIMITED)

    /** Main process: follows `:queue` broadcasts from now on and asks for the current status. */
    private fun followService(context: Context) {
        if (!followingService.compareAndSet(false, true)) return
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    serviceEvents.trySend(intent)
                }
            },
            IntentFilter().apply {
                addAction(ACTION_STATUS)
                addAction(ACTION_PHOTO_ENDED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            for (intent in serviceEvents) {
                when (intent.action) {
                    // The scan applies the photo's render result to Room and refreshes the gallery.
                    ACTION_PHOTO_ENDED -> scan(context)
                    ACTION_STATUS -> {
                        val status = IntentCompat.getSerializableExtra(intent, EXTRA_STATUS, Status::class.java) ?: continue
                        _status.value = status
                        if (status.running) {
                            _pendingPhotoIds.value = intent.getStringArrayExtra(EXTRA_PENDING_IDS).orEmpty().toSet()
                            _failedPhotoIds.value = intent.getStringArrayExtra(EXTRA_FAILED_IDS).orEmpty().toSet()
                        } else {
                            scan(context)
                        }
                    }
                }
            }
        }
        // A `:queue` killed without its last broadcast would leave the status running forever.
        scope.launch {
            _status.collectLatest { status ->
                if (!status.running) return@collectLatest
                while (true) {
                    delay(STATUS_SILENCE_MS)
                    if (processPid(context, RenderProcess.QUEUE_PROCESS_SUFFIX) == null) {
                        PLog.w(TAG, "Render queue process is gone; restarting it once the camera is idle")
                        _status.value = Status()
                        // Killed for low memory, `:queue` is not restarted by the system; waiting jobs sat until the next app start.
                        // Restarting while the camera still shoots or stacks would get it killed again, so wait for the camera hold.
                        scope.launch {
                            awaitCameraHold(context)
                            PLog.i(TAG, "Camera idle; restarting the render queue")
                            start(context)
                        }
                        return@collectLatest
                    }
                }
            }
        }
        context.sendBroadcast(Intent(ACTION_REQUEST_STATUS).setPackage(context.packageName))
    }

    /**
     * Main process: a Pro (RAW) shot was taken, so new renders wait until it is saved, at most
     * [CAMERA_BUSY_HOLD_MS]. Photo mode shots never hold renders: their stacking has not collided with them.
     */
    fun holdForProShot(context: Context) = writeCameraHold(context, CAMERA_BUSY_HOLD_MS)

    /** Main process: a photo finished in the camera; a hold still on ends [CAMERA_IDLE_MS] from now. */
    fun endCameraHoldSoon(context: Context) {
        if (cameraHoldLeftMs(context) > 0) writeCameraHold(context, CAMERA_IDLE_MS)
    }

    private fun writeCameraHold(context: Context, ms: Long) {
        // Time since boot, the same in every process; this phone's wall clock jumps after a restart.
        runCatching { cameraHoldFile(context).writeText((SystemClock.elapsedRealtime() + ms).toString()) }
            .onFailure { PLog.w(TAG, "Cannot write the camera hold", it) }
    }

    /** 0 when no hold is on, including one longer than any hold: written before a phone restart reset the clock. */
    private fun cameraHoldLeftMs(context: Context): Long {
        val until = runCatching { cameraHoldFile(context).readText().trim().toLong() }.getOrNull() ?: return 0
        val left = until - SystemClock.elapsedRealtime()
        return if (left in 1..CAMERA_BUSY_HOLD_MS) left else 0
    }

    /** Main process: the gallery or photo view is open, so renders may start now. */
    fun releaseCameraHold(context: Context) {
        cameraHoldFile(context).delete()
        start(context)
    }

    /** Both processes: returns once the camera holds no more renders back (or the queue is paused). */
    suspend fun awaitCameraHold(context: Context) {
        var logged = false
        while (!isPaused(context)) {
            if (withContext(Dispatchers.IO) { cameraHoldLeftMs(context) } == 0L) return
            if (!logged) PLog.i(TAG, "Holding renders while a Pro shot is saved")
            logged = true
            delay(500)
        }
    }

    private fun cameraHoldFile(context: Context) = File(context.filesDir, CAMERA_HOLD_FILE)

    /** Main process: tidies job files, then starts the queue if a job waits; while paused it shows the paused notice. */
    fun start(context: Context) {
        val appContext = context.applicationContext
        GalleryManager.followRenderQueue(appContext)
        followService(appContext)
        scope.launch {
            val waiting = scan(appContext).size
            if (waiting == 0) return@launch
            if (isPaused(appContext)) {
                RenderQueueService.notifyPaused(appContext, waiting)
                return@launch
            }
            try {
                appContext.startForegroundService(Intent(appContext, RenderQueueService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the app left the foreground meanwhile.
                PLog.w(TAG, "Cannot start the render queue now; it starts on the next app start", e)
            }
        }
    }

    /**
     * Returns the QUEUED jobs, oldest first. Drops jobs whose DNG is gone and, in the main process,
     * applies results a finished render left behind (`:queue` leaves them to the main process).
     */
    suspend fun scan(context: Context): List<RenderJob> = withContext(Dispatchers.IO) {
        val dirs = GalleryManager.getPhotosBaseDir(context)
            .listFiles { dir -> File(dir, RenderJob.JOB_FILE).isFile }.orEmpty()
        val failed = HashSet<String>()
        val queued = dirs.mapNotNull { dir ->
            val photoId = dir.name
            val dng = GalleryManager.getDngFile(context, photoId)
            if (!dng.isFile) {
                PLog.w(TAG, "DNG of $photoId is gone; dropping its render job")
                File(dir, RenderJob.JOB_FILE).delete()
                RenderJob.viewfinderFile(context, photoId).delete()
                RawRenderCheckpoint.delete(context, photoId)
                dir.delete() // only when nothing else is left
                return@mapNotNull null
            }
            val job = RenderJob.read(context, photoId) ?: return@mapNotNull null
            // A finished job's checkpoint is left over from a render killed before it deleted it.
            if (job.state != RenderJob.State.QUEUED) RawRenderCheckpoint.delete(context, photoId)
            when (job.state) {
                RenderJob.State.QUEUED -> job to dng.lastModified()
                RenderJob.State.DONE -> {
                    if (RenderProcess.isMain) GalleryManager.applyRenderResult(context, photoId)
                    null
                }
                RenderJob.State.FAILED -> {
                    failed += photoId
                    null
                }
                RenderJob.State.SKIPPED -> null
            }
        }.sortedBy { it.second }.map { it.first }
        _pendingPhotoIds.value = queued.mapTo(HashSet()) { it.photoId }
        _failedPhotoIds.value = failed
        queued
    }

    /** QUEUED, FAILED and SKIPPED jobs, oldest DNG first. Read-only (unlike [scan]), so a screen may call it any time. */
    suspend fun jobs(context: Context): List<RenderJob> = withContext(Dispatchers.IO) {
        GalleryManager.getPhotosBaseDir(context)
            .listFiles { dir -> File(dir, RenderJob.JOB_FILE).isFile }.orEmpty()
            .mapNotNull { dir -> RenderJob.read(context, dir.name)?.takeIf { it.state != RenderJob.State.DONE } }
            .sortedBy { GalleryManager.getDngFile(context, it.photoId).lastModified() }
    }

    /**
     * Puts the FAILED ones of [jobs] back in the queue with a fresh attempt count (a saved JPEG
     * stays marked; the checkpoint went with FAILED), then starts the queue, which also picks up jobs an interrupted run left QUEUED.
     */
    suspend fun retry(context: Context, jobs: List<RenderJob>) {
        withJobLock(context) {
            withContext(Dispatchers.IO) {
                jobs.filter { it.state == RenderJob.State.FAILED }
                    .forEach { RenderJob.write(context, it.copy(state = RenderJob.State.QUEUED, attempts = 0, rawRendered = false)) }
            }
        }
        start(context)
    }

    /**
     * Takes a QUEUED job out of the queue, keeping its DNG (the next scan deletes its checkpoint). A
     * render of it running now is ended at once; [RenderQueueService] books that end as a skip.
     * While `:queue` lives, the service does it there, as only it knows which photo renders; the
     * app is in the foreground, so it may start the service.
     */
    suspend fun skip(context: Context, photoId: String) {
        if (processPid(context, RenderProcess.QUEUE_PROCESS_SUFFIX) != null) {
            try {
                context.startForegroundService(
                    Intent(context, RenderQueueService::class.java)
                        .setAction(RenderQueueService.ACTION_SKIP)
                        .putExtra(RawRenderService.EXTRA_PHOTO_ID, photoId)
                )
                return
            } catch (e: IllegalStateException) {
                // The app left the foreground meanwhile. The job lock still orders this skip against a render
                // start; a render running now finishes and is booked as a skip unless it rewrote its job.
                PLog.w(TAG, "Cannot reach the render queue service; skipping $photoId here", e)
            }
        }
        // No `:queue`, so no render runs and none can start without the job lock.
        skipJob(context, photoId)
    }

    /**
     * `:queue`: [RenderQueueService.ACTION_SKIP]. In this process's scope, not the service's: a
     * paused queue stops the service at once, which must not cancel the skip.
     */
    internal fun launchSkip(context: Context, photoId: String) {
        val appContext = context.applicationContext
        scope.launch { skipJob(appContext, photoId) }
    }

    /** The skip itself, in the process that calls it. */
    private suspend fun skipJob(context: Context, photoId: String) {
        withContext(Dispatchers.IO) {
            withJobLock<Unit>(context) {
                val job = RenderJob.read(context, photoId)?.takeIf { it.state == RenderJob.State.QUEUED } ?: return@withJobLock
                if (!RenderJob.write(context, job.copy(state = RenderJob.State.SKIPPED))) return@withJobLock
                PLog.i(TAG, "Skipped $photoId")
                if (renderingPhotoId == photoId) {
                    renderSkipped = true
                    // Same uid, so this process may end it; a render not started yet refuses the SKIPPED job.
                    renderProcessPid(context)?.let {
                        PLog.i(TAG, "Ending render process $it of skipped $photoId")
                        Process.killProcess(it)
                    }
                }
            }
            scan(context)
        }
    }

    /**
     * Puts a SKIPPED job back in the queue with a fresh attempt count, then starts the queue. The
     * skip deleted the checkpoint; a saved JPEG stays marked only while it and its render result exist.
     */
    suspend fun unskip(context: Context, photoId: String) {
        withContext(Dispatchers.IO) {
            withJobLock<Unit>(context) {
                val job = RenderJob.read(context, photoId)?.takeIf { it.state == RenderJob.State.SKIPPED } ?: return@withJobLock
                val jpegSaved = job.jpegDone && GalleryManager.getPhotoFile(context, photoId).isFile &&
                    File(RenderJob.photoDir(context, photoId), GalleryMediaStore.RENDER_RESULT_FILE).isFile
                RenderJob.write(
                    context,
                    job.copy(state = RenderJob.State.QUEUED, attempts = 0, rawRendered = false, jpegDone = jpegSaved),
                )
            }
        }
        start(context)
    }

    /** The `:render` process's pid, or null when none runs. */
    internal fun renderProcessPid(context: Context): Int? = processPid(context, RenderProcess.PROCESS_SUFFIX)

    /** The pid of this app's process named with [suffix], or null when none runs. */
    private fun processPid(context: Context, suffix: String): Int? {
        val processName = context.packageName + suffix
        return context.getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.firstOrNull { it.processName == processName }?.pid
    }

    fun isPaused(context: Context): Boolean = pausedFile(context).exists()

    fun setPaused(context: Context, paused: Boolean) {
        val file = pausedFile(context)
        if (paused) file.createNewFile() else file.delete()
    }

    /** What the phone can still hand out without killing anything, as the system counts it. */
    fun freeMemoryMb(context: Context): Int {
        val info = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return (info.availMem / (1024 * 1024)).toInt()
    }

    private fun pausedFile(context: Context) = File(context.filesDir, PAUSED_FILE)

    /** True the first time only, and only while POST_NOTIFICATIONS is not granted (API 33+). */
    fun shouldAskNotificationPermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)) return false
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
        return true
    }
}
