package com.hinnka.mycamera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMeteringMapperTest {
    private val activeArray = CameraCoordinateRect(0, 0, 4000, 3000)

    @Test
    fun controlZoomUsesPostZoomActiveArrayCoordinates() {
        val mapping = map(
            x = 0.25f,
            y = 0.5f,
            zoomMode = PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY,
            scalerCrop = CameraCoordinateRect(1000, 750, 3000, 2250),
        )

        assertEquals(activeArray, mapping.visibleRegion)
        assertEquals(1000, mapping.centerX)
        assertEquals(1500, mapping.centerY)
    }

    @Test
    fun scalerCropZoomMapsPointInsideCropCoordinates() {
        val crop = CameraCoordinateRect(1000, 750, 3000, 2250)
        val mapping = map(
            x = 0.25f,
            y = 0.5f,
            zoomMode = PreviewMeteringZoomMode.SCALER_CROP_REGION,
            scalerCrop = crop,
        )

        assertEquals(crop, mapping.visibleRegion)
        assertEquals(1500, mapping.centerX)
        assertEquals(1500, mapping.centerY)
    }

    @Test
    fun scalerCropEdgeTapNeverFallsOutsideVisibleZoomRegion() {
        val crop = CameraCoordinateRect(1000, 750, 3000, 2250)
        val mapping = map(
            x = 0f,
            y = 0f,
            zoomMode = PreviewMeteringZoomMode.SCALER_CROP_REGION,
            scalerCrop = crop,
        )
        val region = PreviewMeteringMapper.buildCenteredRegion(
            mapping = mapping,
            widthFraction = 0.1f,
            heightFraction = 0.1f,
        )

        assertEquals(crop.left, mapping.centerX)
        assertEquals(crop.top, mapping.centerY)
        assertEquals(crop.left, region.left)
        assertEquals(crop.top, region.top)
        assertTrue(region.right <= crop.right)
        assertTrue(region.bottom <= crop.bottom)
    }

    @Test
    fun aspectRatioCropMatchesVisibleSixteenByNineFieldOfView() {
        val mapping = map(
            x = 0.5f,
            y = 0f,
            zoomMode = PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY,
            previewAspectRatio = 16f / 9f,
        )

        assertEquals(CameraCoordinateRect(0, 375, 4000, 2625), mapping.visibleRegion)
        assertEquals(2000, mapping.centerX)
        assertEquals(375, mapping.centerY)
    }

    @Test
    fun portraitPreviewAndNinetyDegreeSensorMapIntoRotatedVisibleRegion() {
        val mapping = map(
            x = 0f,
            y = 0f,
            zoomMode = PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY,
            previewAspectRatio = 9f / 16f,
            sensorOrientation = 90,
        )

        assertEquals(CameraCoordinateRect(0, 375, 4000, 2625), mapping.visibleRegion)
        assertEquals(0, mapping.centerX)
        assertEquals(2624, mapping.centerY)
    }

    @Test
    fun afRegionRemainsTenPercentOfVisibleCropAtEveryScalerZoom() {
        val twoTimesMapping = map(
            x = 0.5f,
            y = 0.5f,
            zoomMode = PreviewMeteringZoomMode.SCALER_CROP_REGION,
            scalerCrop = CameraCoordinateRect(1000, 750, 3000, 2250),
        )
        val fourTimesMapping = map(
            x = 0.5f,
            y = 0.5f,
            zoomMode = PreviewMeteringZoomMode.SCALER_CROP_REGION,
            scalerCrop = CameraCoordinateRect(1500, 1125, 2500, 1875),
        )

        val twoTimesRegion = PreviewMeteringMapper.buildCenteredRegion(twoTimesMapping, 0.1f)
        val fourTimesRegion = PreviewMeteringMapper.buildCenteredRegion(fourTimesMapping, 0.1f)

        assertEquals(200, twoTimesRegion.width)
        assertEquals(150, twoTimesRegion.height)
        assertEquals(100, fourTimesRegion.width)
        assertEquals(75, fourTimesRegion.height)
    }

    @Test
    fun frontFacingPreviewMirrorsHorizontalSensorCoordinate() {
        val mapping = map(
            x = 0.2f,
            y = 0.5f,
            zoomMode = PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY,
            isFrontFacing = true,
        )

        assertEquals(3199, mapping.centerX)
        assertEquals(1500, mapping.centerY)
    }

    @Test
    fun activeArrayOriginIsPreservedInCameraCoordinates() {
        val offsetActiveArray = CameraCoordinateRect(100, 200, 4100, 3200)
        val mapping = map(
            x = 0f,
            y = 0f,
            zoomMode = PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY,
            activeArray = offsetActiveArray,
        )

        assertEquals(offsetActiveArray, mapping.visibleRegion)
        assertEquals(100, mapping.centerX)
        assertEquals(200, mapping.centerY)
    }

    @Test
    fun frontCamera270MirrorsTheViewAfterRotating() {
        // View (0.2, 0.3) un-mirrors to (0.8, 0.3), and a 270 sensor puts that at sensor (1 - 0.3, 0.8).
        val mapping = map(
            x = 0.2f,
            y = 0.3f,
            zoomMode = PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY,
            previewAspectRatio = 3f / 4f,
            sensorOrientation = 270,
            isFrontFacing = true,
        )

        assertEquals(activeArray, mapping.visibleRegion)
        assertEquals(2799, mapping.centerX)
        assertEquals(2399, mapping.centerY)
    }

    @Test
    fun cameraRectMapsBackToTheViewBoxItCameFrom() {
        val crop = CameraCoordinateRect(500, 300, 3500, 2700)
        for (orientation in listOf(0, 90, 180, 270)) {
            for (front in listOf(false, true)) {
                val a = map(0.2f, 0.3f, PreviewMeteringZoomMode.SCALER_CROP_REGION, crop, 3f / 4f, orientation, front)
                val b = map(0.6f, 0.8f, PreviewMeteringZoomMode.SCALER_CROP_REGION, crop, 3f / 4f, orientation, front)
                val rect = CameraCoordinateRect(
                    minOf(a.centerX, b.centerX), minOf(a.centerY, b.centerY),
                    maxOf(a.centerX, b.centerX), maxOf(a.centerY, b.centerY),
                )
                val view = requireNotNull(PreviewMeteringMapper.mapRectToView(rect, a))
                val label = "orientation=$orientation front=$front"
                assertEquals(label, 0.2f, view.left, 1e-3f)
                assertEquals(label, 0.3f, view.top, 1e-3f)
                assertEquals(label, 0.6f, view.right, 1e-3f)
                assertEquals(label, 0.8f, view.bottom, 1e-3f)
            }
        }
    }

    private fun map(
        x: Float,
        y: Float,
        zoomMode: PreviewMeteringZoomMode,
        scalerCrop: CameraCoordinateRect? = null,
        previewAspectRatio: Float = 4f / 3f,
        sensorOrientation: Int = 0,
        isFrontFacing: Boolean = false,
        activeArray: CameraCoordinateRect = this.activeArray,
    ): PreviewMeteringMapping {
        val mapping = PreviewMeteringMapper.mapPoint(
            normalizedX = x,
            normalizedY = y,
            activeArray = activeArray,
            scalerCropRegion = scalerCrop,
            zoomMode = zoomMode,
            previewViewAspectRatio = previewAspectRatio,
            sensorOrientationDegrees = sensorOrientation,
            isFrontFacing = isFrontFacing,
        )
        assertNotNull(mapping)
        return requireNotNull(mapping)
    }
}
