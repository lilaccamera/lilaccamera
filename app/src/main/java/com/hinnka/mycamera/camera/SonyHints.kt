package com.hinnka.mycamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult

/** Sony (Xperia) driver keys for smile scores and scene and condition detection. */
internal object SonyHintKeys {
    const val LOG_TAG = "LilacHints"

    // Each lists the modes the camera supports, [0 1] when it has the feature.
    val AVAILABLE_SMILE = CameraCharacteristics.Key(
        "com.sonymobile.statistics.info.availableFaceSmileScoresMode", ByteArray::class.java,
    )
    val AVAILABLE_SCENE = CameraCharacteristics.Key(
        "com.sonymobile.statistics.info.availableSceneDetectMode", ByteArray::class.java,
    )
    val AVAILABLE_CONDITION = CameraCharacteristics.Key(
        "com.sonymobile.statistics.info.availableConditionDetectMode", ByteArray::class.java,
    )

    val SMILE_MODE = CaptureRequest.Key("com.sonymobile.statistics.faceSmileScoresMode", Byte::class.java)
    val SCENE_MODE = CaptureRequest.Key("com.sonymobile.statistics.sceneDetectMode", Byte::class.java)
    val CONDITION_MODE = CaptureRequest.Key("com.sonymobile.statistics.conditionDetectMode", Byte::class.java)

    // One score per face, 0 to 100, in the order of STATISTICS_FACES.
    val SMILE_SCORES = CaptureResult.Key("com.sonymobile.statistics.faceSmileScores", IntArray::class.java)
    val SCENE = CaptureResult.Key("com.sonymobile.statistics.scene", Byte::class.java)
    val CONDITION = CaptureResult.Key("com.sonymobile.statistics.condition", Byte::class.java)

    const val ON: Byte = 1
    // 1 / 0.145 m: the lens focused at 14.5 cm or closer.
    const val MACRO_DIOPTERS = 6.9f
    const val HINT_SETTLE_MS = 500L
}

/** Which of the hint keys one camera supports; a key the camera does not list can close it. */
internal data class SonyHintFeatures(
    val smile: Boolean = false,
    val scene: Boolean = false,
    val condition: Boolean = false,
) {
    companion object {
        fun of(characteristics: CameraCharacteristics, faceDetection: Boolean): SonyHintFeatures {
            fun has(key: CameraCharacteristics.Key<ByteArray>) =
                SonySubjectKeys.read(characteristics, key)?.contains(SonyHintKeys.ON) == true
            return SonyHintFeatures(
                // Sony's camera asks for smile scores together with face detection.
                smile = faceDetection && has(SonyHintKeys.AVAILABLE_SMILE),
                scene = has(SonyHintKeys.AVAILABLE_SCENE),
                condition = has(SonyHintKeys.AVAILABLE_CONDITION),
            )
        }
    }
}

/** What the camera says about the scene, shown as small icons only; nothing changes because of them. */
enum class SceneHint {
    BACKLIGHT, NIGHT, LOW_LIGHT, LANDSCAPE, PORTRAIT, DOCUMENT, FOOD, SPOTLIGHT, MACRO, TRIPOD, WALKING, MOTION;

    companion object {
        // Sony's own scenes from 100 up, else the standard Android scene mode numbers.
        fun ofScene(value: Int): SceneHint? = when (value) {
            102, 103 -> BACKLIGHT
            5, 6 -> NIGHT
            104 -> LOW_LIGHT
            4 -> LANDSCAPE
            3 -> PORTRAIT
            101 -> DOCUMENT
            107 -> FOOD
            106 -> SPOTLIGHT
            else -> null
        }

        fun ofCondition(value: Int): SceneHint? = when (value) {
            1 -> MOTION
            2 -> TRIPOD
            3 -> WALKING
            else -> null
        }
    }
}

/** The main face's smile score (0 to 100) from the preview frame [frame]. */
data class SmileScore(val score: Int, val frame: Long)
