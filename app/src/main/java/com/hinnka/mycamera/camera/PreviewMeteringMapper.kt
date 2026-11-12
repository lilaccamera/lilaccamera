package com.hinnka.mycamera.camera

import kotlin.math.roundToInt

/**
 * Camera2 3A region 的变焦坐标空间。
 *
 * CONTROL_ZOOM_RATIO 使用变焦后的 active-array 坐标空间；SCALER_CROP_REGION
 * 则继续使用变焦前的 active-array 坐标空间，3A region 必须落在 crop 内。
 */
internal enum class PreviewMeteringZoomMode {
    POST_ZOOM_ACTIVE_ARRAY,
    SCALER_CROP_REGION,
}

internal data class CameraCoordinateRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = (right - left).coerceAtLeast(0)

    val height: Int
        get() = (bottom - top).coerceAtLeast(0)

    val isEmpty: Boolean
        get() = width <= 0 || height <= 0

    fun intersect(other: CameraCoordinateRect): CameraCoordinateRect? {
        val intersection = CameraCoordinateRect(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = minOf(right, other.right),
            bottom = minOf(bottom, other.bottom),
        )
        return intersection.takeUnless { it.isEmpty }
    }
}

internal data class PreviewMeteringMapping(
    val centerX: Int,
    val centerY: Int,
    val visibleRegion: CameraCoordinateRect,
    val sensorOrientationDegrees: Int = 0,
    val isFrontFacing: Boolean = false,
)

/** A box in the viewfinder, normalized to its width and height (origin top left). */
data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * 把最终显示画面的归一化坐标映射到 Camera2 AF/AE region 坐标。
 */
internal object PreviewMeteringMapper {
    fun mapPoint(
        normalizedX: Float,
        normalizedY: Float,
        activeArray: CameraCoordinateRect,
        scalerCropRegion: CameraCoordinateRect?,
        zoomMode: PreviewMeteringZoomMode,
        previewViewAspectRatio: Float,
        sensorOrientationDegrees: Int,
        isFrontFacing: Boolean,
    ): PreviewMeteringMapping? {
        if (activeArray.isEmpty) return null

        val zoomRegion = when (zoomMode) {
            PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY -> activeArray
            PreviewMeteringZoomMode.SCALER_CROP_REGION -> {
                scalerCropRegion?.intersect(activeArray) ?: activeArray
            }
        }
        val visibleRegion = centerCropToPreviewAspect(
            region = zoomRegion,
            previewViewAspectRatio = previewViewAspectRatio,
            sensorOrientationDegrees = sensorOrientationDegrees,
        )

        // A front preview is shown mirrored: undo the mirror on the view's own x axis before rotating.
        val uiX = normalizedX.coerceIn(0f, 1f).let { if (isFrontFacing) 1f - it else it }
        val uiY = normalizedY.coerceIn(0f, 1f)
        val (rotatedX, rotatedY) = when (Math.floorMod(sensorOrientationDegrees, 360)) {
            0 -> uiX to uiY
            90 -> uiY to (1f - uiX)
            180 -> (1f - uiX) to (1f - uiY)
            270 -> (1f - uiY) to uiX
            else -> uiX to uiY
        }
        val sensorX = rotatedX
        val sensorY = rotatedY

        return PreviewMeteringMapping(
            centerX = mapNormalizedCoordinate(
                normalized = sensorX,
                start = visibleRegion.left,
                length = visibleRegion.width,
            ),
            centerY = mapNormalizedCoordinate(
                normalized = sensorY,
                start = visibleRegion.top,
                length = visibleRegion.height,
            ),
            visibleRegion = visibleRegion,
            sensorOrientationDegrees = sensorOrientationDegrees,
            isFrontFacing = isFrontFacing,
        )
    }

    /** The inverse of [mapPoint] for a box in camera coordinates (faces, a tracked object), clipped to the view. */
    fun mapRectToView(rect: CameraCoordinateRect, mapping: PreviewMeteringMapping): ViewRect? {
        val visible = mapping.visibleRegion
        if (visible.isEmpty) return null
        fun toView(x: Int, y: Int): Pair<Float, Float> {
            val rotatedX = (x - visible.left).toFloat() / (visible.width - 1).coerceAtLeast(1)
            val rotatedY = (y - visible.top).toFloat() / (visible.height - 1).coerceAtLeast(1)
            val (viewX, viewY) = when (Math.floorMod(mapping.sensorOrientationDegrees, 360)) {
                90 -> (1f - rotatedY) to rotatedX
                180 -> (1f - rotatedX) to (1f - rotatedY)
                270 -> rotatedY to (1f - rotatedX)
                else -> rotatedX to rotatedY
            }
            return (if (mapping.isFrontFacing) 1f - viewX else viewX) to viewY
        }
        val a = toView(rect.left, rect.top)
        val b = toView(rect.right, rect.bottom)
        val left = minOf(a.first, b.first).coerceIn(0f, 1f)
        val right = maxOf(a.first, b.first).coerceIn(0f, 1f)
        val top = minOf(a.second, b.second).coerceIn(0f, 1f)
        val bottom = maxOf(a.second, b.second).coerceIn(0f, 1f)
        return ViewRect(left, top, right, bottom).takeIf { right > left && bottom > top }
    }

    fun buildCenteredRegion(
        mapping: PreviewMeteringMapping,
        widthFraction: Float,
        heightFraction: Float = widthFraction,
    ): CameraCoordinateRect {
        val bounds = mapping.visibleRegion
        val regionWidth = fractionToSize(widthFraction, bounds.width)
        val regionHeight = fractionToSize(heightFraction, bounds.height)
        val left = (mapping.centerX - regionWidth / 2)
            .coerceIn(bounds.left, bounds.right - regionWidth)
        val top = (mapping.centerY - regionHeight / 2)
            .coerceIn(bounds.top, bounds.bottom - regionHeight)
        return CameraCoordinateRect(
            left = left,
            top = top,
            right = left + regionWidth,
            bottom = top + regionHeight,
        )
    }

    private fun centerCropToPreviewAspect(
        region: CameraCoordinateRect,
        previewViewAspectRatio: Float,
        sensorOrientationDegrees: Int,
    ): CameraCoordinateRect {
        if (!previewViewAspectRatio.isFinite() || previewViewAspectRatio <= 0f) return region

        val orientation = Math.floorMod(sensorOrientationDegrees, 360)
        val swapsAxes = orientation == 90 || orientation == 270
        val targetSensorAspect = if (swapsAxes) {
            1f / previewViewAspectRatio
        } else {
            previewViewAspectRatio
        }
        if (!targetSensorAspect.isFinite() || targetSensorAspect <= 0f) return region

        val regionAspect = region.width.toFloat() / region.height
        return if (regionAspect > targetSensorAspect) {
            val croppedWidth = (region.height * targetSensorAspect)
                .roundToInt()
                .coerceIn(1, region.width)
            val cropLeft = region.left + (region.width - croppedWidth) / 2
            CameraCoordinateRect(
                left = cropLeft,
                top = region.top,
                right = cropLeft + croppedWidth,
                bottom = region.bottom,
            )
        } else {
            val croppedHeight = (region.width / targetSensorAspect)
                .roundToInt()
                .coerceIn(1, region.height)
            val cropTop = region.top + (region.height - croppedHeight) / 2
            CameraCoordinateRect(
                left = region.left,
                top = cropTop,
                right = region.right,
                bottom = cropTop + croppedHeight,
            )
        }
    }

    private fun mapNormalizedCoordinate(normalized: Float, start: Int, length: Int): Int {
        if (length <= 1) return start
        return (start + normalized.coerceIn(0f, 1f) * (length - 1))
            .roundToInt()
            .coerceIn(start, start + length - 1)
    }

    private fun fractionToSize(fraction: Float, fullSize: Int): Int {
        if (fullSize <= 1) return fullSize.coerceAtLeast(1)
        val safeFraction = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        return (fullSize * safeFraction).roundToInt().coerceIn(1, fullSize)
    }
}
