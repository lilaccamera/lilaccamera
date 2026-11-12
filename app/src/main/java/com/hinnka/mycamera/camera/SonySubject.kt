package com.hinnka.mycamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult

/** Sony (Xperia) driver keys for face selection, object tracking and face exposure. */
internal object SonySubjectKeys {
    const val LOG_TAG = "LilacFaces"

    val AVAILABLE_OBJECT_TRACKING = CameraCharacteristics.Key(
        "com.sonymobile.statistics.info.availableObjectTracking", Boolean::class.java,
    )
    val AVAILABLE_TRACKING_FOCUS_DURING_LOCK = CameraCharacteristics.Key(
        "com.sonymobile.statistics.info.availableTrackingFocusDuringLock", Boolean::class.java,
    )
    val AE_AVAILABLE_REGION_MODES = CameraCharacteristics.Key(
        "com.sonymobile.control.aeAvailableRegionModes", ByteArray::class.java,
    )
    val AF_AVAILABLE_REGION_MODES = CameraCharacteristics.Key(
        "com.sonymobile.control.afAvailableRegionModes", ByteArray::class.java,
    )

    // Trigger: 0 idle (every repeating request), 1 start, 2 stop. Area: a 1x1 box at the point, in active-array coordinates.
    val OBJECT_SELECT_TRIGGER = CaptureRequest.Key("com.sonymobile.statistics.objectSelectTrigger", Byte::class.java)
    val OBJECT_SELECT_TRIGGER_AREA = CaptureRequest.Key("com.sonymobile.statistics.objectSelectTriggerArea", IntArray::class.java)
    val FACE_SELECT_TRIGGER = CaptureRequest.Key("com.sonymobile.statistics.faceSelectTrigger", Byte::class.java)
    val FACE_SELECT_TRIGGER_AREA = CaptureRequest.Key("com.sonymobile.statistics.faceSelectTriggerArea", IntArray::class.java)
    val AE_REGION_MODE = CaptureRequest.Key("com.sonymobile.control.aeRegionMode", Byte::class.java)
    // 0 centre, 1 multi, 2 face, 3 user (CONTROL_AF_REGIONS).
    val AF_REGION_MODE = CaptureRequest.Key("com.sonymobile.control.afRegionMode", Byte::class.java)

    // [left, top, right, bottom, flag]: for objects the flag means lost, for faces that a face is selected.
    val OBJECT_SELECT_AREA = CaptureResult.Key("com.sonymobile.statistics.objectSelectArea", IntArray::class.java)
    val FACE_SELECT_AREA = CaptureResult.Key("com.sonymobile.statistics.faceSelectArea", IntArray::class.java)
    val AE_REGION_MODE_RESULT = CaptureResult.Key("com.sonymobile.control.aeRegionMode", Byte::class.java)
    val AF_REGION_MODE_RESULT = CaptureResult.Key("com.sonymobile.control.afRegionMode", Byte::class.java)

    const val TRIGGER_IDLE: Byte = 0
    const val TRIGGER_START: Byte = 1
    const val TRIGGER_STOP: Byte = 2
    const val AE_REGION_MODE_FACE: Byte = 4
    const val AF_REGION_MODE_USER: Byte = 3

    // A key the driver does not define throws instead of returning null.
    fun <T> read(characteristics: CameraCharacteristics, key: CameraCharacteristics.Key<T>): T? =
        runCatching { characteristics.get(key) }.getOrNull()

    fun <T> read(result: CaptureResult, key: CaptureResult.Key<T>): T? = runCatching { result.get(key) }.getOrNull()
}

/**
 * What one camera's driver can do, read from that camera's characteristics. The request-key list names the Sony
 * keys on every camera, so it cannot tell them apart; sending an unsupported key can close the camera.
 */
internal data class SonySubjectFeatures(
    val objectTracking: Boolean = false,
    val trackingDuringLock: Boolean = false,
    val faceSelect: Boolean = false,
    val faceAe: Boolean = false,
    // The driver focuses on CONTROL_AF_REGIONS only in its "user" AF region mode.
    val afUserRegion: Boolean = false,
) {
    companion object {
        fun of(characteristics: CameraCharacteristics, faceDetection: Boolean): SonySubjectFeatures {
            val tracking = SonySubjectKeys.read(characteristics, SonySubjectKeys.AVAILABLE_OBJECT_TRACKING) == true
            return SonySubjectFeatures(
                objectTracking = tracking,
                trackingDuringLock = tracking &&
                    SonySubjectKeys.read(characteristics, SonySubjectKeys.AVAILABLE_TRACKING_FOCUS_DURING_LOCK) == true,
                // No characteristic announces face select; the camera whose driver tracks objects also picks faces.
                faceSelect = tracking && faceDetection,
                faceAe = faceDetection && SonySubjectKeys.read(characteristics, SonySubjectKeys.AE_AVAILABLE_REGION_MODES)
                    ?.contains(SonySubjectKeys.AE_REGION_MODE_FACE) == true,
                afUserRegion = SonySubjectKeys.read(characteristics, SonySubjectKeys.AF_AVAILABLE_REGION_MODES)
                    ?.contains(SonySubjectKeys.AF_REGION_MODE_USER) == true,
            )
        }
    }
}

/** A face kept across frames: [id] stays with the person while the driver reorders its list. */
data class SubjectFace(val id: Int, val box: ViewRect)

/** Face and tracking frames for the viewfinder, in view coordinates. */
data class SubjectOverlay(
    val faces: List<SubjectFace> = emptyList(),
    // The id of the main face, or -1.
    val mainFace: Int = -1,
    // The camera reports focus on the subject; never on a fixed-focus camera.
    val focused: Boolean = false,
    val tracking: Boolean = false,
    // The tap, and the tracked object once the driver reports it; the tap mark follows its centre.
    val trackStart: Pair<Float, Float>? = null,
    val trackBox: ViewRect? = null,
    val trackLost: Boolean = false,
)
