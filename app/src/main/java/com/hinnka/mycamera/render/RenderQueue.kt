package com.hinnka.mycamera.render

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main-process side of queued RAW renders. The queue is the set of `photos/<id>/render_job.json`
 * files in state QUEUED, oldest DNG first; [RenderQueueService] works through it.
 */
object RenderQueue {
    private const val TAG = "RenderQueue"
    private const val PAUSED_FILE = "render_queue_paused"
    private const val PREFS_NAME = "render_queue"
    private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingPhotoIds = MutableStateFlow<Set<String>>(emptySet())

    /** Photos whose render is waiting or running, as of the last scan. */
    val pendingPhotoIds: StateFlow<Set<String>> = _pendingPhotoIds.asStateFlow()

    /** Tidies job files, then starts the queue if a job waits; while paused it shows the paused notice. */
    fun start(context: Context) {
        val appContext = context.applicationContext
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
     * Returns the QUEUED jobs, oldest first. Drops jobs whose DNG is gone and applies results a
     * finished render left behind.
     */
    suspend fun scan(context: Context): List<RenderJob> = withContext(Dispatchers.IO) {
        val dirs = GalleryManager.getPhotosBaseDir(context)
            .listFiles { dir -> File(dir, RenderJob.JOB_FILE).isFile }.orEmpty()
        val queued = dirs.mapNotNull { dir ->
            val photoId = dir.name
            val dng = GalleryManager.getDngFile(context, photoId)
            if (!dng.isFile) {
                PLog.w(TAG, "DNG of $photoId is gone; dropping its render job")
                File(dir, RenderJob.JOB_FILE).delete()
                RenderJob.viewfinderFile(context, photoId).delete()
                dir.delete() // only when nothing else is left
                return@mapNotNull null
            }
            val job = RenderJob.read(context, photoId) ?: return@mapNotNull null
            when (job.state) {
                RenderJob.State.QUEUED -> job to dng.lastModified()
                RenderJob.State.DONE -> {
                    GalleryManager.applyRenderResult(context, photoId)
                    null
                }
                RenderJob.State.FAILED -> null
            }
        }.sortedBy { it.second }.map { it.first }
        _pendingPhotoIds.value = queued.mapTo(HashSet()) { it.photoId }
        queued
    }

    fun isPaused(context: Context): Boolean = pausedFile(context).exists()

    fun setPaused(context: Context, paused: Boolean) {
        val file = pausedFile(context)
        if (paused) file.createNewFile() else file.delete()
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
