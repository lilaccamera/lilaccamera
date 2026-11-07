package com.hinnka.mycamera.render

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Debug
import android.os.IBinder
import android.os.Process
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.db.GalleryMediaStore
import com.hinnka.mycamera.raw.RawColorCalibrationPolicy
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.RenderProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Renders one queued RAW photo from its DNG and [RenderJob] in the `:render` process, then ends the
 * process: the GPU driver keeps hundreds of MB that only process exit frees. The main process binds
 * it once per job, follows [ACTION_STAGE] broadcasts and decides retries.
 */
class RawRenderService : Service() {
    enum class Stage { STARTED, RENDERING, DONE, FAILED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var currentStage = Stage.STARTED

    override fun onBind(intent: Intent): IBinder {
        val photoId = intent.getStringExtra(EXTRA_PHOTO_ID)
        when {
            photoId == null -> PLog.e(TAG, "Bound without a photo id")
            !RenderProcess.isCurrent -> {
                PLog.e(TAG, "Refusing to render $photoId outside the ${RenderProcess.PROCESS_SUFFIX} process")
                broadcast(photoId, Stage.FAILED)
            }
            // A rebind while the render runs must not start a second one in this process.
            !renderClaimed.compareAndSet(false, true) -> PLog.w(TAG, "Render already running; ignoring $photoId")
            else -> scope.launch { renderAndExit(photoId) }
        }
        return Binder()
    }

    private suspend fun renderAndExit(photoId: String) {
        broadcast(photoId, Stage.STARTED)
        // Repeats the current stage with a fresh memory reading for the queue notification.
        val memoryTicker = scope.launch {
            while (isActive) {
                delay(MEMORY_REPORT_INTERVAL_MS)
                broadcast(photoId, currentStage)
            }
        }
        val rendered = render(photoId)
        memoryTicker.cancel()
        broadcast(photoId, if (rendered) Stage.DONE else Stage.FAILED)
        PLog.i(TAG, "Render of $photoId ${if (rendered) "done" else "failed"}; ending the render process")
        stopSelf()
        Process.killProcess(Process.myPid())
    }

    private suspend fun render(photoId: String): Boolean {
        var viewfinder: Bitmap? = null
        return try {
            val job = RenderJob.read(this, photoId) ?: return false
            check(job.state == RenderJob.State.QUEUED) { "Render job for $photoId is ${job.state}" }
            RawColorCalibrationPolicy.mode = job.rawColorCalibrationMode
            val photoDir = RenderJob.photoDir(this, photoId)
            // Tried before the saved JPEG: the checkpoint also keeps the HDR outputs.
            val checkpoint = if (job.rawRendered) RawRenderCheckpoint.read(this, photoId) else null
            if (checkpoint == null && job.jpegDone && finishFromSavedJpeg(job, photoDir)) {
                markDone(job)
                return true
            }
            val resultFile = File(photoDir, GalleryMediaStore.RENDER_RESULT_FILE)
            val photoFile = GalleryManager.getPhotoFile(this, photoId)
            // Published by temp file and rename each, so present means complete.
            val jpegSaved = checkpoint != null && job.jpegDone && photoFile.isFile && resultFile.isFile
            if (!jpegSaved) {
                // Outputs of an attempt that never reached DONE: loadMetadata would build on the stale
                // result, and a stale JPEG would hide a render that produced none.
                resultFile.delete()
                photoFile.delete()
            }
            // Leftovers of an attempt killed while writing a checkpoint.
            if (checkpoint == null) RawRenderCheckpoint.delete(this, photoId)
            val dngFile = GalleryManager.getDngFile(this, photoId)
            check(dngFile.isFile) { "No DNG for $photoId" }
            val metadata = checkpoint?.metadata
                ?: checkNotNull(GalleryManager.loadMetadata(this, photoId)) { "No metadata for $photoId" }
            if (checkpoint != null) {
                PLog.w(TAG, "Resuming $photoId from its RAW render checkpoint; RAW render skipped, JPEG kept=$jpegSaved")
            } else {
                viewfinder = RenderJob.viewfinderFile(this, photoId).takeIf { it.isFile }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
            // The callbacks below add to what is on disk, so each write keeps the other's flag.
            var current = job.copy(rawRendered = checkpoint != null)
            broadcast(photoId, Stage.RENDERING)
            GalleryManager.renderRawDngPhotoOutputs(
                context = this,
                photoId = photoId,
                dngFile = dngFile,
                aspectRatio = job.resolvedAspectRatio(),
                metadata = metadata,
                rotation = job.rotation,
                exposureBias = job.exposureBias,
                photoProcessor = ContentRepository.getInstance(this).photoProcessor,
                sharpeningValue = job.sharpeningValue,
                noiseReductionValue = job.noiseReductionValue,
                chromaNoiseReductionValue = job.chromaNoiseReductionValue,
                photoQuality = job.photoQuality,
                shouldAutoSave = job.shouldAutoSave,
                captureExposureInputs = job.exposureInputs(viewfinder),
                rawNoiseProfileId = job.rawNoiseProfileId,
                onJpegSaved = {
                    if (RenderJob.write(this, current.copy(jpegDone = true))) {
                        current = current.copy(jpegDone = true)
                        PLog.i(TAG, "JPEG and render result of $photoId saved; a retry keeps them")
                    }
                },
                onRawRendered = { result, rendered ->
                    if (RawRenderCheckpoint.write(this, photoId, result, rendered) &&
                        RenderJob.write(this, current.copy(rawRendered = true))
                    ) {
                        current = current.copy(rawRendered = true)
                        PLog.i(TAG, "RAW render of $photoId checkpointed; a retry skips it")
                    }
                },
                resumeFrom = checkpoint?.rawResult,
                jpegSaved = jpegSaved,
            )
            // The render returns without output when the DNG decode fails.
            check(photoFile.isFile) { "Render produced no JPEG for $photoId" }
            check(resultFile.isFile) { "Render saved no result for $photoId" }
            markDone(current)
            true
        } catch (e: Exception) {
            // The job stays QUEUED; the main-process queue counts the attempt.
            PLog.e(TAG, "Render of $photoId failed", e)
            false
        } finally {
            viewfinder?.recycle()
        }
    }

    private fun markDone(job: RenderJob) {
        check(RenderJob.write(this, job.copy(state = RenderJob.State.DONE))) { "Cannot mark ${job.photoId} done" }
        RawRenderCheckpoint.delete(this, job.photoId)
    }

    /**
     * Finishes a job whose earlier attempt was killed after saving the JPEG and its render result,
     * without the RAW render or a [RawRenderCheckpoint]. Detail HDR and an HDR export need the RAW
     * render's linear HDR reference, which only the checkpoint keeps, so rebuilding them would repeat
     * the render that was killed: they are skipped, and an auto-save exports the SDR JPEG. Returns false, so the caller renders
     * again from the DNG, when the saved files are unusable.
     */
    private suspend fun finishFromSavedJpeg(job: RenderJob, photoDir: File): Boolean {
        val photoId = job.photoId
        // loadMetadata falls back to the gallery row, so the result file itself must exist.
        val metadata = File(photoDir, GalleryMediaStore.RENDER_RESULT_FILE).takeIf { it.isFile }
            ?.let { runCatching { GalleryManager.loadMetadata(this, photoId) }.getOrNull() }
        // Published by temp file and rename, so a decodable file is the complete JPEG.
        val jpeg = metadata?.let { GalleryManager.loadOriginalBitmap(this, photoId) }
        if (metadata == null || jpeg == null) {
            PLog.w(TAG, "Saved JPEG or render result of $photoId unusable; rendering again from the DNG")
            return false
        }
        PLog.w(
            TAG,
            "Keeping the JPEG of $photoId from an attempt killed after saving it; RAW render skipped, " +
                "detail HDR skipped (hdr=${metadata.manualHdrEffectEnabled}), SDR export=${job.shouldAutoSave}",
        )
        broadcast(photoId, Stage.RENDERING)
        val photoProcessor = ContentRepository.getInstance(this).photoProcessor
        try {
            // The same post-JPEG steps renderRawDngPhotoOutputs runs, from the saved JPEG.
            if ((metadata.computationalAperture ?: 0f) > 0f && !GalleryManager.getBokehFile(this, photoId).isFile) {
                GalleryManager.generateBokehPhoto(this, photoId, metadata, jpeg)
            }
            GalleryManager.updateThumbnail(this, photoId, photoProcessor, metadata, jpeg)
            if (job.shouldAutoSave) {
                // shortcut: a kill between this export and DONE exports again on the next attempt;
                // add an exported marker if duplicates show up.
                GalleryManager.exportPhoto(
                    this, photoId, jpeg, photoProcessor, metadata.copy(manualHdrEffectEnabled = false),
                    job.sharpeningValue, job.noiseReductionValue, job.chromaNoiseReductionValue, job.photoQuality,
                )
            }
        } finally {
            jpeg.recycle()
        }
        return true
    }

    private fun broadcast(photoId: String, stage: Stage) {
        currentStage = stage
        // Total PSS of this process, GPU driver memory included: what decides a low-memory kill.
        val pssKb = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
        sendBroadcast(
            Intent(ACTION_STAGE)
                .setPackage(packageName)
                .putExtra(EXTRA_PHOTO_ID, photoId)
                .putExtra(EXTRA_STAGE, stage.name)
                .putExtra(EXTRA_PSS_KB, pssKb)
        )
    }

    companion object {
        private const val TAG = "RawRenderService"
        const val ACTION_STAGE = "com.hinnka.mycamera.render.action.STAGE"
        const val EXTRA_PHOTO_ID = "com.hinnka.mycamera.render.extra.PHOTO_ID"
        /** A [Stage] name. */
        const val EXTRA_STAGE = "com.hinnka.mycamera.render.extra.STAGE"
        /** Total PSS of the render process in kB, an Int. */
        const val EXTRA_PSS_KB = "com.hinnka.mycamera.render.extra.PSS_KB"
        private const val MEMORY_REPORT_INTERVAL_MS = 2_000L

        // One job per process lifetime: the process exits after it.
        private val renderClaimed = AtomicBoolean(false)
    }
}
