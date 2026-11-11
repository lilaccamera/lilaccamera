package com.hinnka.mycamera.processor

import com.hinnka.mycamera.ml.SubjectMask

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.ml.DepthModelManager
import com.hinnka.mycamera.ml.RelativeDepthMap
import com.hinnka.mycamera.ml.RelativeDepthMapFile
import com.hinnka.mycamera.ml.SharedDepthEstimator
import com.hinnka.mycamera.ml.SharedSubjectMaskEstimator
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * Handles the post-processing of the Depth Map for high-quality optical bokeh.
 * This class coordinates the GPU compute pipeline for edge refinement (Guided Filter)
 * and realistic bokeh convolution.
 */
/** What a blur render is doing, for the editor's status line. */
enum class BokehStage { Subject, Depth, Render }

/** A photo's own Quick or Depth blur choice, kept in its custom properties; none follows the camera's setting. */
object PortraitBlurMetadata {
    const val QUICK_KEY = "lilac.portrait.quickBlur"

    fun quickBlur(metadata: com.hinnka.mycamera.gallery.MediaMetadata?): Boolean? =
        metadata?.customProperties?.get(QUICK_KEY)?.toBooleanStrictOrNull()

    /** The photo's own depth of field, −10 (shallow) to 10 (deep); none (0) is the automatic one. */
    const val DEPTH_OF_FIELD_KEY = "lilac.portrait.depthOfField"

    fun depthOfField(metadata: com.hinnka.mycamera.gallery.MediaMetadata?): Int? =
        metadata?.customProperties?.get(DEPTH_OF_FIELD_KEY)?.toIntOrNull()?.coerceIn(-10, 10)

    /** How much a depth of field widens the automatic in-focus range: ¼ at −10, 1 at 0, 3 at 10. */
    fun focalWidthScale(depthOfField: Int?): Float {
        val d = (depthOfField ?: 0).coerceIn(-10, 10) / 10f
        return if (d <= 0f) Math.pow(0.25, -d.toDouble()).toFloat() else Math.pow(3.0, d.toDouble()).toFloat()
    }
}

class DepthBokehProcessor(
    context: Context,
    /** True when the user chose the quick blur, which never uses the depth model. */
    private val quickBlurRequested: suspend () -> Boolean = { false },
) {
    companion object {
        private const val TAG = "DepthBokehProcessor"
    }

    private val appContext = context.applicationContext
    private val processor = OglBokehProcessor()
    private val mutex = Mutex()

    /**
     * Applies optical-grade computational bokeh to the high-res image.
     * 
     * @param originalImage The high resolution RGB image (e.g. 12MP).
     * @param focusPoint The normalized coordinates (0.0 - 1.0) where the user focused.
     * @param aperture The simulated aperture value (e.g., 1.4 for heavy blur, 16.0 for none).
     * @param bokehStyle The optical point-spread profile used for defocused highlights.
     * @param quickBlur The photo's own Quick (true) or Depth (false) choice; null follows the camera's setting.
     * @param onStage Called as the render moves on to finding the subject, estimating depth and rendering.
     * @param onMaps Called with the subject cut-out and, for Depth, the depth map, once both are known; they
     *        describe the original image and let a caller show a cheap preview while the GPU renders.
     * @param onRenderStart Called right before the GPU render, which cannot be cancelled once started;
     *        until then the call can still be cancelled.
     * @return A new Bitmap with the bokeh applied.
     */
    suspend fun applyHighQualityBokeh(
        context: Context,
        photoId: String?,
        originalImage: Bitmap,
        focusX: Float?,
        focusY: Float?,
        aperture: Float,
        bokehStyle: BokehStyle = BokehStyle.DEFAULT,
        quickBlur: Boolean? = null,
        focalWidthScale: Float = 1f,
        onStage: (BokehStage) -> Unit = {},
        onMaps: (SubjectMask, RelativeDepthMap?) -> Unit = { _, _ -> },
        onRenderStart: () -> Unit = {},
        /** For a smaller copy of the photo: the photo's own long edge (see OglBokehProcessor.applyBokeh). */
        photoLongEdge: Int? = null,
    ): Bitmap = mutex.withLock {
        if (aperture > 16.0f || aperture <= 0f) {
            return originalImage
        }

        var depthMap: RelativeDepthMap? = null
        var floatDepthFile: java.io.File? = null
        val quickRequested = quickBlur ?: quickBlurRequested()
        if (!quickRequested && photoId != null) {
            floatDepthFile = GalleryManager.getFloatDepthFile(context, photoId)
            if (floatDepthFile.exists()) {
                depthMap = try {
                    RelativeDepthMapFile.read(floatDepthFile)
                } catch (error: Exception) {
                    PLog.w(TAG, "Unable to read floating-point depth cache", error)
                    null
                }
            }

            // Existing photos may only have the former 8-bit PNG cache. Read it
            // once and migrate it without changing its already-quantized values.
            if (depthMap == null) {
                val legacyDepthFile = GalleryManager.getDepthFile(context, photoId)
                if (legacyDepthFile.exists()) {
                    val legacyBitmap = BitmapFactory.decodeFile(legacyDepthFile.absolutePath)
                    if (legacyBitmap != null) {
                        try {
                            depthMap = RelativeDepthMap.fromBitmap(legacyBitmap)
                        } finally {
                            legacyBitmap.recycle()
                        }
                    }
                }
            }
        }

        // Without an installed depth model the blur is a quick one, evenly strong behind the subject.
        var quickBlur = false
        if (quickRequested || (depthMap == null && !DepthModelManager.isInstalled(appContext))) {
            quickBlur = true
        } else if (depthMap == null) {
            onStage(BokehStage.Depth)
            depthMap = SharedDepthEstimator.estimateDepth(appContext, originalImage)

            if (depthMap != null && floatDepthFile != null) {
                try {
                    RelativeDepthMapFile.write(floatDepthFile, depthMap)
                } catch (error: Exception) {
                    PLog.w(TAG, "Unable to write floating-point depth cache", error)
                }
            }
        } else if (floatDepthFile != null && !floatDepthFile.exists()) {
            // Persist a successfully migrated legacy cache as the new versioned format.
            try {
                RelativeDepthMapFile.write(floatDepthFile, depthMap)
            } catch (error: Exception) {
                PLog.w(TAG, "Unable to migrate floating-point depth cache", error)
            }
        }

        var result: Bitmap? = null
        if (depthMap != null || quickBlur) {
            onStage(BokehStage.Subject)
            val subjectMask = SharedSubjectMaskEstimator.estimateMask(appContext, originalImage, depthMap)
            if (subjectMask == null) {
                PLog.e(TAG, "U2NetP subject mask unavailable; bokeh was not applied")
                return@withLock originalImage
            }
            // The subject is near, everything else far. The mask itself is the stand-in depth, and
            // it is not cached: an installed depth model must be able to replace it.
            onMaps(subjectMask, if (quickBlur) null else depthMap)
            if (quickBlur) {
                depthMap = RelativeDepthMap(subjectMask.width, subjectMask.height, subjectMask.values.copyOf())
            }
            val resolvedDepth = checkNotNull(depthMap)
            val resolvedFocusX = focusX ?: 0.5f
            val resolvedFocusY = focusY ?: 0.5f
            val focusMask = subjectMask.sample(resolvedFocusX, resolvedFocusY)
            // The strongest subject coverage within 3 % of the focus point: tells a tap just off the
            // subject (high nearby) from a hole in the cut-out at the subject (low everywhere).
            var focusMaskNear = 0f
            for (dy in -3..3) for (dx in -3..3) {
                focusMaskNear = maxOf(focusMaskNear, subjectMask.sample(resolvedFocusX + dx * 0.01f, resolvedFocusY + dy * 0.01f))
            }
            val preparedDepth = DepthBokehDepthPreprocessor.prepare(
                resolvedDepth,
                resolvedFocusX,
                resolvedFocusY,
                subjectMask,
            )
            PLog.d(
                TAG,
                "Prepared bokeh depth: focus=($resolvedFocusX,$resolvedFocusY) image=${originalImage.width}x${originalImage.height} " +
                    "quick=$quickBlur inverted=${preparedDepth.inverted} focusDepth=${preparedDepth.focusDepth} " +
                    "focusMask=$focusMask focusMaskNear=$focusMaskNear maskRegion=${subjectMask.region} normalScore=${preparedDepth.normalScore} invertedScore=${preparedDepth.invertedScore}"
            )
            currentCoroutineContext().ensureActive()
            onStage(BokehStage.Render)
            onRenderStart()
            val bokehResult = processor.applyBokeh(
                originalImage,
                preparedDepth.depthMap,
                subjectMask,
                preparedDepth.focusDepth,
                resolvedFocusX,
                resolvedFocusY,
                aperture,
                bokehStyle,
                // The quick blur has no depth to focus a layer by: it always protects the subject.
                focusOnSubject = quickBlur || focusMask >= 0.5f,
                // The quick blur's depth is the cut-out itself, so only it keeps the subject's own range.
                focalRangeFromPoint = !quickBlur,
                focalWidthScale = focalWidthScale,
                photoLongEdge = photoLongEdge,
            )
            result = bokehResult
        }

        return result ?: originalImage
    }

    fun close() = Unit
}
