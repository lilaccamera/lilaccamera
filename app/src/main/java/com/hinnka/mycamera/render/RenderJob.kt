package com.hinnka.mycamera.render

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.GsonBuilder
import com.hinnka.mycamera.camera.AspectRatio
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.preview.PortraitMaskSnapshot
import com.hinnka.mycamera.raw.RawCaptureExposureInputs
import com.hinnka.mycamera.raw.RawColorCalibrationMode
import com.hinnka.mycamera.raw.RawSceneExposureDeviceLimits
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * `photos/<id>/render_job.json`: what a queued RAW render needs beyond the DNG and the gallery
 * metadata, snapshotted at capture. The viewfinder frame is [VIEWFINDER_FILE] next to it.
 * Plain fields only, so the file round-trips through Gson.
 */
data class RenderJob(
    val photoId: String,
    // renderRawDngPhotoOutputs arguments
    val aspectRatio: String,
    val rotation: Int,
    val exposureBias: Float?,
    val sharpeningValue: Float,
    val noiseReductionValue: Float,
    val chromaNoiseReductionValue: Float,
    val photoQuality: Int,
    val shouldAutoSave: Boolean,
    val rawNoiseProfileId: String,
    val rawColorCalibrationMode: RawColorCalibrationMode,
    // RawCaptureExposureInputs, without the viewfinder frame
    val viewfinderMirroredHorizontally: Boolean,
    val viewfinderPreviewToCaptureRotationDegrees: Int,
    val sceneExposureDeviceLimits: RawSceneExposureDeviceLimits?,
    val maxHdrRatio: Float?,
    val portraitMask: PortraitMask?,
    val version: Int = VERSION,
    val state: State = State.QUEUED,
    /** Counted by the main-process queue, never by the render. */
    val attempts: Int = 0,
    /** Set by the render once `original.jpg` and its render result are both saved. */
    val jpegDone: Boolean = false,
) {
    enum class State { QUEUED, DONE, FAILED }

    /** [PortraitMaskSnapshot] with its confidence as base64 little-endian float32. */
    data class PortraitMask(
        val width: Int,
        val height: Int,
        val confidence: String,
        val sampleElapsedRealtimeNanos: Long,
        val cameraId: String?,
        val sensorOrientationDegrees: Int,
        val isFrontFacing: Boolean,
        val previewToCaptureRotationDegrees: Int,
    ) {
        fun toSnapshot(): PortraitMaskSnapshot {
            val bytes = ByteBuffer.wrap(Base64.getDecoder().decode(confidence)).order(ByteOrder.LITTLE_ENDIAN)
            val values = FloatArray(bytes.remaining() / Float.SIZE_BYTES).also { bytes.asFloatBuffer().get(it) }
            // The snapshot's init validates size and range.
            return PortraitMaskSnapshot(
                width, height, values, sampleElapsedRealtimeNanos, cameraId,
                sensorOrientationDegrees, isFrontFacing, previewToCaptureRotationDegrees,
            )
        }

        companion object {
            fun of(mask: PortraitMaskSnapshot): PortraitMask {
                val bytes = ByteBuffer.allocate(mask.confidence.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                bytes.asFloatBuffer().put(mask.confidence)
                return PortraitMask(
                    mask.width, mask.height, Base64.getEncoder().encodeToString(bytes.array()),
                    mask.sampleElapsedRealtimeNanos, mask.cameraId, mask.sensorOrientationDegrees,
                    mask.isFrontFacing, mask.previewToCaptureRotationDegrees,
                )
            }
        }
    }

    fun resolvedAspectRatio(): AspectRatio = AspectRatio.valueOf(aspectRatio)

    fun exposureInputs(viewfinder: Bitmap?) = RawCaptureExposureInputs(
        capturePreviewThumbnail = viewfinder,
        capturePortraitMask = portraitMask?.toSnapshot(),
        viewfinderMirroredHorizontally = viewfinderMirroredHorizontally,
        viewfinderPreviewToCaptureRotationDegrees = viewfinderPreviewToCaptureRotationDegrees,
        sceneExposureDeviceLimits = sceneExposureDeviceLimits,
        maxHdrRatio = maxHdrRatio,
    )

    // Gson skips constructors, so a field missing from the file is null even when typed non-null.
    @Suppress("SENSELESS_COMPARISON")
    private fun isValid(expectedPhotoId: String): Boolean =
        version == VERSION && photoId == expectedPhotoId && state != null &&
            rawNoiseProfileId != null && rawColorCalibrationMode != null &&
            aspectRatio != null && AspectRatio.valueOfOrNull(aspectRatio) != null &&
            runCatching { portraitMask?.toSnapshot() }.isSuccess

    companion object {
        private const val TAG = "RenderJob"
        const val VERSION = 1
        const val JOB_FILE = "render_job.json"
        const val VIEWFINDER_FILE = "viewfinder.jpg"
        private const val VIEWFINDER_QUALITY = 95
        private val gson = GsonBuilder().serializeSpecialFloatingPointValues().create()

        fun photoDir(context: Context, photoId: String): File =
            requireNotNull(GalleryManager.getDngFile(context, photoId).parentFile)

        fun viewfinderFile(context: Context, photoId: String) = File(photoDir(context, photoId), VIEWFINDER_FILE)

        /**
         * Writes [job], and first [viewfinder] if given, each through a temp file and rename, so a
         * job file on disk always has its complete viewfinder frame. Returns false on failure.
         */
        fun write(context: Context, job: RenderJob, viewfinder: Bitmap? = null): Boolean {
            val dir = photoDir(context, job.photoId)
            return try {
                dir.mkdirs()
                if (viewfinder != null) {
                    writeAtomically(File(dir, VIEWFINDER_FILE)) { file ->
                        FileOutputStream(file).use {
                            check(viewfinder.compress(Bitmap.CompressFormat.JPEG, VIEWFINDER_QUALITY, it))
                        }
                    }
                }
                writeAtomically(File(dir, JOB_FILE)) { it.writeText(gson.toJson(job)) }
                true
            } catch (e: Exception) {
                PLog.e(TAG, "Failed to write render job for ${job.photoId}", e)
                false
            }
        }

        /** The job for [photoId], or null when it is missing, unreadable or of an unknown version. */
        fun read(context: Context, photoId: String): RenderJob? {
            val file = File(photoDir(context, photoId), JOB_FILE)
            if (!file.isFile) {
                PLog.e(TAG, "No render job for $photoId")
                return null
            }
            val job = try {
                gson.fromJson(file.readText(), RenderJob::class.java)
            } catch (e: Exception) {
                PLog.e(TAG, "Unreadable render job for $photoId", e)
                return null
            }
            if (job == null || !job.isValid(photoId)) {
                PLog.e(TAG, "Rejected render job for $photoId (version ${job?.version})")
                return null
            }
            return job
        }

        private fun writeAtomically(target: File, write: (File) -> Unit) {
            val temp = File(target.parentFile, "${target.name}.tmp")
            try {
                write(temp)
                check(temp.renameTo(target)) { "Cannot rename ${temp.absolutePath}" }
            } finally {
                temp.delete()
            }
        }
    }
}
