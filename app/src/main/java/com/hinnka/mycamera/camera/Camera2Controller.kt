package com.hinnka.mycamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.HardwareBuffer
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.hinnka.mycamera.BuildConfig
import com.hinnka.mycamera.raw.ColorSpace as RawColorSpace
import com.hinnka.mycamera.raw.DngSdkColorSpec
import com.hinnka.mycamera.raw.RawEnginePreviewSource
import com.hinnka.mycamera.raw.RawCameraCalibration
import com.hinnka.mycamera.raw.RawColorCalibrationMode
import com.hinnka.mycamera.raw.RawColorCalibrationPolicy
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.utils.RawProcessor
import com.hinnka.mycamera.utils.SystemPropertiesUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import com.hinnka.mycamera.livephoto.LivePhotoRecorder
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.lut.VideoColorEffectLayer
import com.hinnka.mycamera.model.SafeImage
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.processor.MgcRawMaxMode
import com.hinnka.mycamera.stabilization.ExternalLensStabilizationConfig
import com.hinnka.mycamera.stabilization.RealtimeStabilizationCoordinator
import com.hinnka.mycamera.stabilization.StabilizationImageReader
import com.hinnka.mycamera.stabilization.normalizeStabilizationLookahead
import com.hinnka.mycamera.stabilization.normalizeStabilizationStrength
import com.hinnka.mycamera.utils.DeviceUtil
import com.hinnka.mycamera.utils.OrientationObserver
import com.hinnka.mycamera.slowmotion.SlowMotionRecorder
import com.hinnka.mycamera.slowmotion.BurstPhase
import com.hinnka.mycamera.slowmotion.SlowMotionSpec
import com.hinnka.mycamera.slowmotion.SonyBridgeClient
import com.hinnka.mycamera.slowmotion.SonyVideoMode
import io.github.lilaccamera.sonybridge.IFrameSink
import com.hinnka.mycamera.video.CaptureMode
import com.hinnka.mycamera.video.VideoAspectRatio
import com.hinnka.mycamera.video.VIDEO_AUDIO_INPUT_AUTO
import com.hinnka.mycamera.video.VideoBitratePreset
import com.hinnka.mycamera.video.VideoCapabilitiesResolver
import com.hinnka.mycamera.video.VideoFpsPreset
import com.hinnka.mycamera.video.VideoEncoderColorRequest
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.video.VideoLogLutMode
import com.hinnka.mycamera.video.VideoRecorder
import com.hinnka.mycamera.video.StabiliseNotice
import com.hinnka.mycamera.video.VideoConfig
import com.hinnka.mycamera.video.VideoRecordingPath
import com.hinnka.mycamera.video.VideoResolutionPreset
import com.hinnka.mycamera.video.VideoRecordingState
import com.hinnka.mycamera.video.VideoStabilizationMode
import com.hinnka.mycamera.video.resolveSurfaceTextureVideoOrientationDegrees
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

sealed interface EnhancedStabilizationFallback {
    data class Video(val mode: VideoStabilizationMode) : EnhancedStabilizationFallback
    data object PhotoPreview : EnhancedStabilizationFallback
}

/**
 * Camera2 相机控制器
 *
 * 使用原生 Camera2 API 直接控制相机，支持：
 * - 绑定隐藏的物理摄像头（通过探测发现的 Camera ID）
 * - 手动曝光控制（ISO、快门速度）
 * - 变焦控制
 */
class Camera2Controller(private val context: Context) {

    companion object {
        private const val COMPANION_JPEG_SHOTS_KEPT = 8
        private const val SLOW_MOTION_DEFAULT_FRAMES = 177
        // Exposure the scene would need at 1/960 s, as an ISO: above this the burst comes out dark and noisy.
        private const val SLOW_MOTION_DARK_ISO = 1600
        private const val TAG = "Camera2Controller"

        // 自定义错误代码
        const val ERROR_CAMERA_DISCONNECTED = 1000
        const val ERROR_CAMERA_OPEN_FAILED = 1001
        const val ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE = 1002
        const val ERROR_CAMERA_SESSION_CONFIG_FAILED = 1003

        private const val CAPTURE_READER_MAX_IMAGES = 50
        // lilac (XZ1 Compact) raw19 shim: a failed RAW capture breaks RAW for every app until the camera is reopened.
        private val IS_LILAC = DeviceQuirks.isLilac
        private const val CAPTURE_IMAGE_DELIVERY_TIMEOUT_MS = 10_000L
        private const val BURST_CAPTURE_BATCH_SIZE = 8

        // 拍照状态机常量
        private const val PRECAPTURE_TIMEOUT_MS = 3_000L
        private const val PRECAPTURE_MAX_WAIT_FRAMES = 60L
        private const val MULTI_FRAME_TORCH_WARMUP_TIMEOUT_MS = 2_000L
        private const val EIS_PLUS_INPUT_WATCHDOG_MS = 2_000L
        private const val OPPO_SUPER_STABILIZATION_REAR_SESSION_MODE = 0x8028
        private const val OPPO_SUPER_STABILIZATION_FRONT_SESSION_MODE = 0x802B

        // 场景变化检测阈值
        private const val SCENE_CHANGE_EXPOSURE_RATIO = 1.5   // 曝光乘积变化判定为场景变化
        private const val SCENE_CHANGE_FOCUS_DISTANCE_DELTA = 0.2f // 焦距跳变阈值（diopters），对焦锁定后逐帧跟踪
        private const val FOCUS_LOCK_SETTLE_FRAMES = 5        // 对焦锁定后等待镜头稳定的帧数
        private const val SCENE_CHANGE_CONFIRM_FRAMES = 3     // 连续 N 帧检测到变化才确认
        private const val EYE_TARGET_RECENT_MS = 1_500L
        private const val MAX_SUBJECT_FACES = 5
        private const val SUBJECT_LOG_MS = 200L
        // The driver drops faces for a few frames at a time; a frame stays this long before it goes.
        private const val FACE_SHOW_MS = 400L
        private const val MAIN_FACE_HOLD_MS = 1_000L
        private const val FACE_MATCH_MIN_DISTANCE = 0.08f
        // Another face this much larger for this long becomes the main face, unless the user chose the main face.
        private const val RIVAL_AREA_RATIO = 1.5f
        private const val RIVAL_HOLD_MS = 2_000L
        // A half-press with no face in sight waits this long for the app's detector before locking on the centre.
        private const val HALF_PRESS_FACE_WAIT_MS = 400L
        private const val FACE_RESELECT_AFTER_MS = 500L
        private const val FACE_RESELECT_EVERY_MS = 1_000L
        // Face metering stays on this long after the last face, so a blink of the detector does not flip exposure.
        private const val FACE_AE_HOLD_MS = 1_000L
        private const val TRACK_FAINT_MS = 500L
        private const val TRACK_STOP_MS = 3_000L
        private const val TRACK_BOX_FOLLOW = 0.6f
        private const val TRACK_GROWTH_LIMIT = 4f
        // A tap on no real object: in its first moments the driver's box wanders off or is lost. Results in the first
        // TRACK_SETTLE_MS may still carry the previous object's box.
        private const val TRACK_SETTLE_MS = 300L
        private const val TRACK_CHECK_MS = 1_500L
        private const val TRACK_DRIFT_LIMIT = 0.10f
        private const val TRACK_FACE_TAKEOVER_MS = 200L
        private const val TAP_CHECK_WAIT_MS = 150L
        private const val FACE_FRAME_SCALE = 1.2f
        private const val FACE_FRAME_CHIN_SHIFT = 0.05f
        // Subject AF: the region follows the subject without restarting the driver's AF on every frame.
        private const val SUBJECT_AF_MIN_INTERVAL_MS = 150L
        private const val SUBJECT_AF_MOVE_FRACTION = 0.03f
        private const val SUBJECT_AF_RESIZE_FRACTION = 0.10f
        // A one-shot AF scan when the subject came nearer or went away, or continuous AF has not settled on it.
        private const val AF_KICK_RESIZE_FRACTION = 0.25f
        private const val AF_KICK_UNFOCUSED_MS = 1_500L
        private const val AF_KICK_EVERY_MS = 1_500L
        // At 10 fps a full search takes up to 25 frames after a 6-frame wait; 2 s cut searches short.
        private const val AF_KICK_TIMEOUT_MS = 4_000L
        // Too dark to find focus: after this many misses in a row the lens stops sweeping until the subject changes.
        private const val AF_KICK_MISS_LIMIT = 2
        // In dim light a small AF region holds too little detail for the driver's search; it grows to this share.
        private const val DIM_AF_ISO = 1600
        private const val DIM_AF_REGION_FRACTION = 0.20f
        // A late answer can still turn the new track into a face; after this it is ignored.
        private const val TAP_CHECK_GIVE_UP_MS = 1_000L
        private const val TAP_FACE_MARGIN = 0.15f
        // Half the tap mark (42 dp), the area that clears a selection when tapped again.
        private const val TAP_MARK_HALF_DP = 21f
        private const val AF_REGION_WIDTH_FRACTION = 0.10f
        private const val AF_REGION_HEIGHT_FRACTION = 0.10f
        private const val SPOT_AE_REGION_FRACTION = 0.06f
        private const val CENTER_WEIGHTED_AE_REGION_FRACTION = 0.40f
        private const val HIGHLIGHT_AE_REGION_FRACTION = 0.16f
        private const val DEFAULT_HYPERFOCAL_FOCAL_LENGTH_MM = 4.0f
        private const val DEFAULT_HYPERFOCAL_APERTURE = 1.8f
        private const val HYPERFOCAL_COC_DIAGONAL_DIVISOR = 1500.0
        private const val NO_IMAGE_READER_FORMAT = -1
        private const val AWB_TEMPERATURE_MIN = 2000
        private const val AWB_TEMPERATURE_MAX = 8000
        private val FORCED_VENDOR_SESSION_PARAMETER_KEYS = setOf(
            VendorCaptureKey.VIVO_FORCE_SENSOR_MODE
        )
    }

    private data class PhysicalOutputFailureKey(
        val openCameraId: String,
        val physicalCameraId: String,
        val captureMode: CaptureMode,
        val readerFormat: Int
    )

    private data class CameraOpenRequest(
        /** Null opens the camera before the preview surface exists; [openCamera] attaches it later. */
        val surfaceTexture: SurfaceTexture?,
        val preserveVideoRecording: Boolean,
    )

    /** A photo session configured with a deferred preview output, waiting for the preview surface. */
    private class DeferredPreview(
        val output: OutputConfiguration,
        val buildRequest: (Surface) -> CaptureRequest.Builder,
        val openGeneration: Long,
        val sessionGeneration: Long,
    ) {
        var session: CameraCaptureSession? = null
    }

    private enum class CameraDeviceLifecycle {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING,
    }

    private data class HyperfocalFocusResult(
        val cameraId: String,
        val focalLengthMm: Float,
        val aperture: Float,
        val circleOfConfusionMm: Float,
        val distanceMeters: Float,
        val focusDistanceDiopters: Float
    )

    private data class WhiteBalanceResultSnapshot(
        val awbMode: Int,
        val colorTemperature: Int?,
        val colorTint: Int?,
        val gains: RggbChannelVector?,
        val transform: ColorSpaceTransform?
    )

    private data class ManualWhiteBalanceAnchor(
        val controlPath: WhiteBalanceControlPath,
        val baseTemperature: Int,
        val colorTint: Int?,
        val gains: RggbChannelVector?,
        val transform: ColorSpaceTransform?
    )

    private data class NormalizedRgb(
        val red: Float,
        val green: Float,
        val blue: Float
    )

    private data class InitialSessionParametersResult(
        val applied: Boolean,
        val usedVendorParameters: Boolean
    )

    private enum class StillFlashPhase {
        APPLYING_SESSION,
        WAITING_AF_CONVERGENCE,
        WAITING_AE_AF,
    }

    private data class StillFlashRequestTag(
        val generation: Long,
        val phase: StillFlashPhase,
        val isTrigger: Boolean,
    )

    private data class StillFlashPrecapture(
        val generation: Long,
        val afMode: Int,
        val triggerAf: Boolean,
        var phase: StillFlashPhase = StillFlashPhase.APPLYING_SESSION,
        var triggerFrameNumber: Long? = null,
        var triggerSequenceId: Int? = null,
        var repeatingSequenceId: Int? = null,
        var firstResultFrameNumber: Long? = null,
        var lastResult: TotalCaptureResult? = null,
        var aeReady: Boolean = false,
        var afReady: Boolean = false,
    )

    private data class MultiFrameTorchWarmupRequestTag(
        val generation: Long,
        val isAePrecaptureTrigger: Boolean = false,
    )

    private enum class WhiteBalanceControlPath {
        CCT,
        MATRIX,
        UNAVAILABLE
    }

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val cameraDiscovery = CameraDiscovery(context)

    // --- 拍照状态机相关 ---

    // 缓存拍照所需的设备和 Reader，供状态机回调使用
    private var pendingCaptureDevice: CameraDevice? = null
    private var pendingCaptureReader: ImageReader? = null
    private var pendingCaptureBaseExposureResult: CaptureResult? = null
    private var precaptureGeneration = 0L
    private var stillFlashPrecapture: StillFlashPrecapture? = null
    private var stillFlashCaptureState: CameraState? = null
    // Keep the capture-owned AF mode until the still request finishes and AF is cancelled.
    private var stillFlashAfMode: Int? = null
    private var stillFlashFocusDistance: Float? = null
    private var precaptureTimeoutRunnable: Runnable? = null
    private var multiFrameTorchWarmupGeneration = 0L
    private var activeMultiFrameTorchWarmupGeneration = 0L
    private var multiFrameTorchWarmupTimeoutRunnable: Runnable? = null
    private var lastMultiFrameTorchWarmupResult: TotalCaptureResult? = null
    private var pendingMultiFrameTorchWarmupAction: ((TotalCaptureResult?) -> Unit)? = null
    private var multiFrameTorchWarmupNeedsAePrecapture = false
    private var multiFrameTorchWarmupPrecaptureSubmitted = false
    private var multiFrameTorchWarmupTriggerResultSeen = false
    private var isMultiFrameTorchCaptureActive = false
    private var isContinuousBurstTorchActive = false
    private var livePhotoVideoStartTimestampUs: Long? = null
    @Volatile
    private var isCaptureFocusFrozen = false
    // ---------------------

    private var cameraDevice: CameraDevice? = null
    private var cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSED
    private var pendingCameraOpenRequest: CameraOpenRequest? = null
    @Volatile
    private var releaseCloseLatch: java.util.concurrent.CountDownLatch? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSessionGeneration: Long = 0L
    private val previewUpdateScheduled = AtomicBoolean(false)
    private var pendingVendorSessionParameterRestart = false
    private var oppoSuperStabilizationEnabled = false

    private var previewSurface: Surface? = null
    private var previewSurfaceTexture: SurfaceTexture? = null
    // Camera opened before the preview surface existed: its generation, the settings it was
    // configured with, and the preview buffer size the surface must get when it attaches.
    private var surfacelessOpenGeneration = -1L
    private var surfacelessOpenSettings: List<Any?>? = null
    private var previewBufferSize = Size(0, 0)
    private var deferredPreview: DeferredPreview? = null
    private var deferredPreviewUnsupported = false
    private var imageReader: ImageReader? = null
    private var stabilizationImageReader: StabilizationImageReader? = null
    // lilac raw19 shim: serves full-res RAW only when the same request also targets a full-size JPEG; the JPEG is discarded
    private var rawCompanionJpegReader: ImageReader? = null
    // lilac front camera, low-light YUV stills: the same slot holds a small discarded JPEG (see frontStillJpegReader)
    var frontStillJpegActive = false
        private set
    private var frontStillJpegStallNs = 0L
    // Measured burst frame spacing by still format and size: the first frame of the next burst has no measurement
    // yet, and exposure times underestimate a camera that waits between frames.
    private val measuredFrameSpacingNs = mutableMapOf<Triple<Int, Int, Int>, Long>()
    private val ambientLightReader = AmbientLightReader(context)

    // 降噪等级 (0=Off, 1=Fast, 2=High Quality, 3=ZSL, 4=Minimal)
    private var nrLevel = NoiseReductionLevel.DEFAULT

    // 锐化等级 (0=Off, 1=Fast, 2=High Quality, 3=Zero Shutter Lag/Real-time)
    private var edgeLevel = 1

    private var videoNrLevel = NoiseReductionLevel.DEFAULT
    private var videoEdgeLevel = 1

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private val burstGyroRecorder = BurstGyroRecorder(context)

    private val cameraCharacteristicsCache = ConcurrentHashMap<String, CameraCharacteristics>()
    private var cachedCharacteristics: CameraCharacteristics? = null
    private var cachedCharacteristicsCameraId: String = ""
    private var activeOpenCameraId: String = ""
    private var activeOutputPhysicalCameraId: String? = null
    private val failedPhysicalOutputProfiles = mutableSetOf<PhysicalOutputFailureKey>()
    // "streamCameraId:format" profiles whose high-resolution-only capture size failed session configuration
    private val failedHighResolutionCaptureProfiles = mutableSetOf<String>()
    private var activeHighResolutionCaptureProfile: String? = null
    private var cachedSensorOrientation: Int = 0
    private var cachedLensFacing: Int = CameraCharacteristics.LENS_FACING_BACK
    private var cachedHardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
    private var isManualSensorSupported = false
    private var isManualPostProcessingSupported = false
    private var isFlashSupported = false
    private var maxAfRegions = 0
    private var maxAeRegions = 0
    private var pointMeteringViewAspectRatio: Float? = null
    private var availableAfModes: IntArray = intArrayOf()
    private var availableEdgeModes: IntArray = intArrayOf()
    private var availableNoiseReductionModes: IntArray = intArrayOf()
    private var availableTonemapModes: IntArray = intArrayOf()
    private var availableColorCorrectionAberrationModes: IntArray = intArrayOf()
    private var availableHotPixelModes: IntArray = intArrayOf()
    private var availableShadingModes: IntArray = intArrayOf()
    private var availableDistortionCorrectionModes: IntArray = intArrayOf()
    private var availableVideoStabilizationModes: IntArray = intArrayOf()
    private var availableOpticalStabilizationModes: IntArray = intArrayOf()
    private var availableLensShadingMapModes: IntArray = intArrayOf()
    private var availableFaceDetectModes: IntArray = intArrayOf()
    private var maxFaceCount: Int = 0
    private var faceDetectMode: Int = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
    private var availableColorCorrectionModes: IntArray = intArrayOf()
    private var awbColorTemperatureRange: Range<Int>? = null
    private var lastWhiteBalanceResult: WhiteBalanceResultSnapshot? = null
    private var manualWhiteBalanceAnchor: ManualWhiteBalanceAnchor? = null
    @Volatile
    private var requestedRawCaptureEnabled = false
    private var isRawSupported = false
    private var isP010Supported = false
    private var isStreamUseCaseSupported = false
    private var availableStreamUseCases: LongArray = longArrayOf()
    private var isZslControlSupported: Boolean? = null
    private var availableCaptureRequestKeyNames: Set<String>? = null
    private var availableAeModes: IntArray = intArrayOf()
    private var availableAwbModes: IntArray = intArrayOf()
    private var videoCaptureStatsWindowStartMs: Long = 0L
    private var videoCaptureStatsFrames: Int = 0
    private var videoCaptureStatsFirstTimestampNs: Long = 0L
    private var stabilizationInputStatsFirstTimestampNs: Long = 0L
    private var stabilizationInputStatsFrames: Int = 0
    private var stabilizationInputFrameCount: Long = 0L
    private var mirrorFrontCameraEnabled: Boolean = true
    @Volatile
    private var cameraOpenGeneration: Long = 0L

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    // Kept out of CameraState, which every frame copies whole.
    private val _subjectOverlay = MutableStateFlow(SubjectOverlay())
    val subjectOverlay: StateFlow<SubjectOverlay> = _subjectOverlay.asStateFlow()
    /** The driver's faces and the main one, every frame while Portrait is on; called on the camera thread. */
    @Volatile
    var onPortraitFaces: ((List<ViewRect>, Int) -> Unit)? = null
    // Why the app's own face detector should look at the viewfinder now ("tap", "track", "face"), or null for no work.
    private val _faceCheckReason = MutableStateFlow<String?>(null)
    val faceCheckReason: StateFlow<String?> = _faceCheckReason.asStateFlow()

    @Volatile
    var retainCaptureSettingsEnabled: Boolean = false
        private set
    private var retainedCaptureSettings: CustomCaptureSettings? = null
    private var pendingRetainedAwbTemperature: Int? = null

    // Live Photo 录制器
    val livePhotoRecorder = LivePhotoRecorder(context)
    val realtimeStabilizationCoordinator = RealtimeStabilizationCoordinator(context)
    val videoRecorder = VideoRecorder(context, realtimeStabilizationCoordinator)
    var onEnhancedStabilizationUnavailable: ((EnhancedStabilizationFallback) -> Unit)? = null
    var onVideoSaved: ((Uri?) -> Unit)? = null
    /** The recording was saved and stopped because the phone got too hot; the reading that reached its limit, if any. */
    var onVideoStoppedForHeat: ((CameraHeat.Gauge?) -> Unit)? = null
    private var previousVideoStabilizationMode = VideoStabilizationMode.OIS
    private val enhancedStabilizationUnavailableCameraIds =
        ConcurrentHashMap.newKeySet<String>()

    private var slowMotionDebugReceiver: android.content.BroadcastReceiver? = null

    init {
        if (BuildConfig.DEBUG && DeviceQuirks.isLilac) registerSlowMotionDebugReceiver()
        realtimeStabilizationCoordinator.onEnhancedStabilizationUnavailable = {
            handleEnhancedStabilizationUnavailable(
                reason = "Camera HAL stabilization conflicts with algorithmic stabilization"
            )
        }
    }

    private var videoRecordingStartElapsedMs: Long = 0L
    private var videoRecordingPausedMs: Long = 0L
    private var videoRecordingPauseStartElapsedMs: Long = 0L
    private var lastHeatCheckElapsedMs: Long = 0L
    private val videoRecordingTicker = object : Runnable {
        override fun run() {
            var recordingState = _state.value.videoRecordingState
            if (!recordingState.isRecording) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastHeatCheckElapsedMs >= 1000L) {
                lastHeatCheckElapsedMs = now
                val heat = CameraHeat.read(context)
                if (heat.level == CameraHeat.Level.STOP) {
                    PLog.w(TAG, "Stopping video recording: too hot, $heat")
                    stopVideoRecording()
                    onVideoStoppedForHeat?.invoke(heat.closest?.takeIf { it.fraction >= 1f })
                    return
                }
                if (heat.level == CameraHeat.Level.WARN && !recordingState.isHot) {
                    PLog.w(TAG, "Video recording hot warning: $heat")
                }
                val isHot = recordingState.isHot || heat.level == CameraHeat.Level.WARN
                recordingState = recordingState.copy(isHot = isHot, heatGauge = heat.closest.takeIf { isHot })
            }
            if (recordingState.isPaused) {
                _state.update { s -> s.copy(videoRecordingState = recordingState) }
                cameraHandler?.postDelayed(this, 250)
                return
            }
            val elapsed =
                (now - videoRecordingStartElapsedMs - videoRecordingPausedMs).coerceAtLeast(
                    0L
                )
            _state.update { s -> s.copy(
                videoRecordingState = recordingState.copy(elapsedMs = elapsed)
            ) }
            cameraHandler?.postDelayed(this, 250)
        }
    }

    // 缓存 CaptureResult 和 Image 用于配对 (timestamp -> Data)
    private val pendingResults = ConcurrentHashMap<Long, TotalCaptureResult>()
    private val pendingImages = ConcurrentHashMap<Long, SafeImage>()
    private val pendingFrameMetadata = ConcurrentHashMap<Long, CapturedFrameMetadata>()
    private val pendingCaptureStartedTimestamps = ConcurrentHashMap<Long, Long>()
    private val pendingCloseReaders = mutableListOf<ImageReader>()
    private val openImagesCount = AtomicInteger(0)
    private var imageReaderMaxImages = CAPTURE_READER_MAX_IMAGES

    // Owned by cameraHandler. Acquired images are counted atomically until their
    // processing owner closes them; reservations cover buffers still in the HAL.
    private class PhotoCaptureContext(
        val id: Long,
        var state: CameraState,
        val reader: ImageReader,
        val expectedFrames: Int,
        val characteristics: CameraCharacteristics?,
        val physicalCameraId: String?,
        val continuousBurst: Boolean = false,
        val captureTime: Long = System.currentTimeMillis(),
        val exposureCompensationStep: Float,
    ) {
        var reservedFrames = expectedFrames
        var submitted = false
        var sequenceCompleted = false
        var failed = false
        var delivered = false
        var livePhotoStartTimestampUs: Long? = null
        var animationCaptureResult: TotalCaptureResult? = null
        var timeout: Runnable? = null
        val timestamps = mutableSetOf<Long>()
        val startedFrameNumbers = mutableSetOf<Long>()
        val expectedImageTimestamps = mutableSetOf<Long>()
        val timestampByFrameNumber = mutableMapOf<Long, Long>()
        val lostImageFrameNumbers = mutableSetOf<Long>()
        val acquiredTimestamps = mutableSetOf<Long>()
        val frames = sortedMapOf<Long, CapturedPhotoFrame>()
        var companionJpegTaken = false
    }

    private var nextPhotoCaptureId = 0L
    private var activePhotoCapture: PhotoCaptureContext? = null
    // Focus releases asked for while a still capture runs, sent once it has ended.
    private val focusReleasesAfterCapture = ArrayList<() -> Unit>()
    // Exposure for the YUV burst being captured, when it is planned for low light.
    private var nightPlan: NightCapturePlan? = null
    private var pendingContinuousBurstCapture: PhotoCaptureContext? = null
    private val photoCaptures = mutableMapOf<Long, PhotoCaptureContext>()
    private val photoCaptureByTimestamp = mutableMapOf<Long, PhotoCaptureContext>()
    private val discardedCaptureTimestamps = linkedSetOf<Long>()

    private var burstCapturing = false
    // 保留最近的一个结果作为后备
    @Volatile
    private var lastCaptureResult: TotalCaptureResult? = null

    @Volatile
    private var enginePreviewEnabled = false
    private val _enginePreviewSource = MutableStateFlow<RawEnginePreviewSource?>(null)
    val enginePreviewSource: StateFlow<RawEnginePreviewSource?> = _enginePreviewSource.asStateFlow()
    private var enginePreviewCharacteristics: CameraCharacteristics? = null
    private var enginePreviewCalibration: RawCameraCalibration? = null
    private var enginePreviewCalibrationMode: RawColorCalibrationMode? = null

    fun setEnginePreviewMetadataEnabled(enabled: Boolean) {
        enginePreviewEnabled = enabled
        if (!enabled) _enginePreviewSource.value = null
    }

    private fun updateEnginePreviewSource(result: TotalCaptureResult) {
        if (!enginePreviewEnabled) return
        val physicalId = activeOutputPhysicalCameraId
            ?: result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
        val cameraId = physicalId ?: getActiveOpenCameraId()
        val frame = physicalId?.let { result.physicalCameraResults[it] } ?: result
        val characteristics = getCameraCharacteristicsOrNull(cameraId, "Engine preview") ?: run {
            _enginePreviewSource.value = null
            return
        }
        val gains = frame.get(CaptureResult.COLOR_CORRECTION_GAINS)
            ?: result.get(CaptureResult.COLOR_CORRECTION_GAINS)
        if (gains == null) {
            _enginePreviewSource.value = null
            return
        }
        val calibrationMode = RawColorCalibrationPolicy.mode
        if (enginePreviewCharacteristics !== characteristics || enginePreviewCalibrationMode != calibrationMode) {
            enginePreviewCalibration = RawColorCalibrationPolicy.fromCharacteristics(characteristics).toRawCalibration()
            enginePreviewCharacteristics = characteristics
            enginePreviewCalibrationMode = calibrationMode
            PLog.d(TAG, "Engine preview source camera=$cameraId")
        }
        val mode = frame.get(CaptureResult.COLOR_CORRECTION_MODE)
            ?: result.get(CaptureResult.COLOR_CORRECTION_MODE)
        val transform = if (mode == CaptureResult.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX) {
            frame.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
                ?: result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
        } else null
        val source = RawEnginePreviewSource(
            cameraId = cameraId,
            iso = frame.get(CaptureResult.SENSOR_SENSITIVITY) ?: result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100,
            calibration = requireNotNull(enginePreviewCalibration),
            whiteBalanceGains = listOf(gains.red, gains.greenEven, gains.greenOdd, gains.blue),
            camera2Transform = transform?.let { matrix ->
                List(9) { index -> matrix.getElement(index % 3, index / 3).toFloat() }
            },
        )
        if (enginePreviewEnabled) _enginePreviewSource.value = source
    }

    // 场景变化检测：用于替代固定延迟恢复连续对焦
    private var isFocusLockedWaitingForSceneChange = false
    // A tap that moves the metering region changes exposure itself; the scene reference waits until AE settles.
    private var sceneExposureReferencePending = false
    // Results already in flight when a point-focus trigger is sent still report the previous lock.
    private var pointAfTriggerPending = false
    private var focusLockedReferenceIso: Int = 0
    private var focusLockedReferenceExposureNs: Long = 0L
    private var focusLockedReferenceDistance: Float = 0f
    private var focusLockSettleFrames = 0       // 对焦锁定后等待镜头稳定的帧数
    private var sceneChangeFrameCount = 0
    private var eyeTargetLastSeenElapsedMs = 0L
    private var focusModeBeforeHyperfocal: Boolean? = null
    private var focusDistanceBeforeHyperfocal: Float? = null

    // 高光优先测光：最亮区域坐标（归一化 0-1）及平滑状态
    @Volatile
    private var highlightPointX: Float = 0.5f
    @Volatile
    private var highlightPointY: Float = 0.5f
    private var highlightPointSmoothedX: Float = 0.5f
    private var highlightPointSmoothedY: Float = 0.5f
    private var highlightPointInitialized = false
    private var lastSentHighlightPointX: Float = -1f
    private var lastSentHighlightPointY: Float = -1f

    // 图片拍摄回调（携带 CaptureInfo, CameraCharacteristics 和 CaptureResult 用于 RAW 处理）
    var onImageCaptured: ((SafeImage, CaptureInfo, CameraCharacteristics?, CaptureResult?, CapturedFrameMetadata?) -> Unit)? = null
    var onPhotoCaptureReady: ((CapturedPhoto) -> Unit)? = null
    var onPhotoCaptureCompleted: ((Long, CaptureInfo) -> Unit)? = null
    var onPhotoCaptureFailed: ((Long) -> Unit)? = null
    var onHdrBracketCaptureFailed: (() -> Unit)? = null
    /** Pro: when [keepRawCompanionJpeg], gets the bytes of each shot's first companion JPEG with the shot id. */
    var onRawCompanionJpeg: ((Long, ByteArray) -> Unit)? = null
    @Volatile
    var keepRawCompanionJpeg = false
    /** Sensor timestamp of each Pro shot's first frame to its shot id, until its camera JPEG arrives (oldest dropped). */
    private val companionJpegShotIds = LinkedHashMap<Long, Long>()

    private fun reservedImageCount(): Int = photoCaptures.values.sumOf { it.reservedFrames }

    /** The failed RAW shot that made us reopen the camera; cleared by the next good RAW shot. */
    @Volatile
    private var rawFailureBeforeReopen: PhotoCaptureContext? = null

    /** Set when RAW failed again after a reopen; only an app restart clears it. */
    @Volatile
    private var rawBlockedUntilAppRestart = false

    private fun showLongToast(resId: Int) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, resId, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** A shutter press that takes no photo says so, so the user does not press again and get a late extra shot. */
    private fun showCaptureBusyMessage() {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, com.hinnka.mycamera.R.string.capture_busy, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** lilac: the first failed RAW shot reopens the camera (that clears the shim); a second one in a row blocks RAW. */
    private fun markRawHalPoisoned(shot: PhotoCaptureContext, why: String) {
        if (!IS_LILAC || !isRawCaptureReader(shot.reader) || rawBlockedUntilAppRestart) return
        val earlier = rawFailureBeforeReopen
        if (earlier === shot) return // one shot can report several failures
        if (earlier != null) {
            rawBlockedUntilAppRestart = true
            PLog.e(TAG, "lilac RAW HAL poisoned again after a camera reopen ($why); RAW blocked until app restart")
            showLongToast(com.hinnka.mycamera.R.string.raw_hal_restart_required)
            return
        }
        rawFailureBeforeReopen = shot
        PLog.e(TAG, "lilac RAW HAL poisoned ($why); reopening the camera")
        showLongToast(com.hinnka.mycamera.R.string.raw_failed_camera_reopened)
        // the error-4 path releases the preview texture, so keep it now and reopen after this callback returns
        val surfaceTexture = previewSurfaceTexture ?: return
        cameraHandler?.post { openCamera(surfaceTexture) }
    }

    private fun reservePhotoCapture(
        state: CameraState,
        reader: ImageReader,
        continuousBurst: Boolean = false,
    ): PhotoCaptureContext? {
        val frameCount = if (continuousBurst) BURST_CAPTURE_BATCH_SIZE else captureImageRequestCount(state, reader)
        val occupied = openImagesCount.get() + reservedImageCount()
        if (occupied + frameCount > CAPTURE_READER_MAX_IMAGES) {
            PLog.w(TAG, "Capture rejected: occupied=$occupied requested=$frameCount capacity=$CAPTURE_READER_MAX_IMAGES")
            showCaptureBusyMessage()
            return null
        }
        // The raw19 shim fails a RAW still started while a previous RAW image is still open, and RAW stays broken until the camera reopens.
        if (IS_LILAC && isRawCaptureReader(reader) && occupied > 0) {
            PLog.w(TAG, "lilac: RAW capture refused while a RAW image is still open: occupied=$occupied")
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, com.hinnka.mycamera.R.string.raw_capture_busy, android.widget.Toast.LENGTH_SHORT).show()
            }
            return null
        }
        val shot = PhotoCaptureContext(
            id = ++nextPhotoCaptureId,
            state = state,
            reader = reader,
            expectedFrames = frameCount,
            characteristics = activeOutputPhysicalCameraId
                ?.let { getCameraCharacteristicsOrNull(it, "capture snapshot") }
                ?: getActiveOpenCameraCharacteristics(),
            physicalCameraId = activeOutputPhysicalCameraId,
            continuousBurst = continuousBurst,
            exposureCompensationStep = state.getExposureCompensationStep(),
        )
        photoCaptures[shot.id] = shot
        updateCaptureBusy()
        PLog.d(TAG, "Capture reserved: id=${shot.id} frames=$frameCount occupied=$occupied")
        return shot
    }

    private fun registerPhotoTimestamp(shot: PhotoCaptureContext, timestamp: Long) {
        if (shot.delivered) {
            discardCaptureTimestamp(timestamp)
            return
        }
        shot.timestamps.add(timestamp)
        photoCaptureByTimestamp[timestamp] = shot
        // The camera JPEG often arrives after the shot is delivered and unmapped, so its shot is kept apart.
        if (keepRawCompanionJpeg && !shot.continuousBurst && !shot.companionJpegTaken) {
            shot.companionJpegTaken = true
            synchronized(companionJpegShotIds) {
                companionJpegShotIds[timestamp] = shot.id
                while (companionJpegShotIds.size > COMPANION_JPEG_SHOTS_KEPT) {
                    companionJpegShotIds.remove(companionJpegShotIds.keys.first())
                }
            }
        }
        if (pendingImages.containsKey(timestamp)) {
            accountAcquiredPhotoImage(shot, timestamp)
            if (shot.failed) pendingImages.remove(timestamp)?.close()
        }
    }

    private fun accountAcquiredPhotoImage(shot: PhotoCaptureContext, timestamp: Long) {
        if (shot.acquiredTimestamps.add(timestamp)) {
            check(shot.reservedFrames > 0) { "Capture ${shot.id} received more images than requested" }
            shot.reservedFrames--
        }
    }

    private fun discardCaptureTimestamp(timestamp: Long) {
        discardedCaptureTimestamps.add(timestamp)
        while (discardedCaptureTimestamps.size > CAPTURE_READER_MAX_IMAGES * 4) {
            discardedCaptureTimestamps.remove(discardedCaptureTimestamps.first())
        }
        photoCaptureByTimestamp.remove(timestamp)
        pendingImages.remove(timestamp)?.close()
        pendingResults.remove(timestamp)
        pendingFrameMetadata.remove(timestamp)
    }

    private fun failPhotoCapture(shot: PhotoCaptureContext, reason: String, drainQueue: Boolean = true) {
        if (shot.failed || shot.delivered) return
        shot.failed = true
        clearPhotoCaptureProgress(shot)
        shot.timeout?.let { cameraHandler?.removeCallbacks(it) }
        shot.timeout = null
        shot.timestamps.forEach { timestamp ->
            pendingImages.remove(timestamp)?.close()
            pendingResults.remove(timestamp)
            pendingFrameMetadata.remove(timestamp)
        }
        shot.frames.values.forEach { it.image.close() }
        shot.frames.clear()
        PLog.e(TAG, "Capture failed: id=${shot.id} reason=$reason")
        if (!shot.continuousBurst) onPhotoCaptureFailed?.invoke(shot.id)
        settleFailedPhotoCapture(shot)
        if (drainQueue) deliverNextPhotoCaptureIfReady()
    }

    private fun disposeFailedPhotoCapture(shot: PhotoCaptureContext) {
        shot.reservedFrames = 0
        shot.delivered = true
        shot.timeout?.let { cameraHandler?.removeCallbacks(it) }
        shot.timeout = null
        photoCaptures.remove(shot.id)
        updateCaptureBusy()
        shot.startedFrameNumbers.forEach { pendingCaptureStartedTimestamps.remove(it) }
        shot.startedFrameNumbers.clear()
        shot.timestamps.forEach(::discardCaptureTimestamp)
    }

    private fun settleFailedPhotoCapture(shot: PhotoCaptureContext) {
        if (!shot.failed || shot.delivered) return
        // A failed frame does not cancel its sibling requests. Hold their slots
        // until the sequence tells us which requests actually produced images.
        if (shot.submitted && !shot.sequenceCompleted) {
            // If the HAL never completes or aborts the sequence, isCapturing would stay true forever.
            if (shot.timeout == null) {
                val timeout = Runnable {
                    shot.timeout = null
                    PLog.e(TAG, "Failed capture sequence never completed: id=${shot.id}")
                    finishPhotoCaptureSequence(shot, successful = false)
                }
                shot.timeout = timeout
                cameraHandler?.postDelayed(timeout, CAPTURE_IMAGE_DELIVERY_TIMEOUT_MS)
            }
            return
        }
        shot.timeout?.let { cameraHandler?.removeCallbacks(it) }
        shot.timeout = null
        shot.reservedFrames = if (shot.submitted) {
            shot.expectedImageTimestamps.count { it !in shot.acquiredTimestamps }
        } else {
            0
        }
        if (shot.reservedFrames == 0) {
            disposeFailedPhotoCapture(shot)
        } else if (shot.timeout == null) {
            val timeout = Runnable {
                PLog.e(TAG, "Failed capture image drain timed out: id=${shot.id} remaining=${shot.reservedFrames}")
                disposeFailedPhotoCapture(shot)
            }
            shot.timeout = timeout
            cameraHandler?.postDelayed(timeout, CAPTURE_IMAGE_DELIVERY_TIMEOUT_MS)
        }
    }

    private fun finishPhotoCaptureSequence(shot: PhotoCaptureContext, successful: Boolean) {
        shot.sequenceCompleted = true
        nightPlan = null
        clearPhotoCaptureProgress(shot)
        if (!successful) failPhotoCapture(shot, "capture sequence aborted")
        if (shot.failed) settleFailedPhotoCapture(shot)
        if (activePhotoCapture === shot) {
            activePhotoCapture = null
            if (shot.expectedFrames > 1) {
                PLog.i(TAG, "Ambient light at the shot: ${ambientLightReader.latestLux ?: "no reading"} lux " +
                    "(${ambientLightReader.readings} readings), front still JPEG=$frontStillJpegActive")
            }
            stopShotSensors()
            _state.update {
                it.copy(isCapturing = false, hdrBracketCapturing = false, hdrBracketFrameCount = 0)
            }
            resetPreviewAfterCapture(keepLivePhotoCapture = successful && !shot.failed)
        }
        if (!shot.failed) {
            if (isRawCaptureReader(shot.reader)) rawFailureBeforeReopen = null
            if (!shot.continuousBurst) {
                val captureInfo = rebuildCaptureInfo(
                    result = shot.animationCaptureResult,
                    imageWidth = shot.reader.width,
                    imageHeight = shot.reader.height,
                    latitude = shot.state.latitude,
                    longitude = shot.state.longitude,
                    effectiveCharacteristics = shot.characteristics,
                    captureState = shot.state,
                    captureTime = shot.captureTime,
                    exposureCompensationStep = shot.exposureCompensationStep,
                )
                try {
                    onPhotoCaptureCompleted?.invoke(shot.id, captureInfo)
                } catch (e: Exception) {
                    PLog.e(TAG, "Capture completion notification failed: id=${shot.id}", e)
                }
            }
            deliverPhotoCaptureIfReady(shot)
            if (!shot.delivered && !hasAllPhotoFrames(shot)) {
                val timeout = Runnable {
                    failPhotoCapture(shot, "image delivery timed out after sequence completion")
                    disposeFailedPhotoCapture(shot)
                }
                shot.timeout = timeout
                cameraHandler?.postDelayed(timeout, CAPTURE_IMAGE_DELIVERY_TIMEOUT_MS)
            }
        }
        if (shot.continuousBurst) {
            if (successful && !shot.failed) {
                checkBurstCaptureContinue()
            } else if (_state.value.burstCapturing) {
                burstCapturing = false
                _state.update { it.copy(burstCapturing = false, isCapturing = false) }
                resetPreviewAfterCapture()
            }
        }
    }

    private fun hasAllPhotoFrames(shot: PhotoCaptureContext): Boolean =
        shot.acquiredTimestamps.size == shot.expectedFrames &&
            if (shot.continuousBurst) {
                shot.timestamps.none { pendingResults.containsKey(it) || pendingImages.containsKey(it) }
            } else {
                shot.frames.size == shot.expectedFrames
            }

    private fun deliverNextPhotoCaptureIfReady() {
        photoCaptures.values.filter { !it.continuousBurst && !it.failed }.minByOrNull { it.id }
            ?.let(::deliverPhotoCaptureIfReady)
    }

    private fun deliverPhotoCaptureIfReady(shot: PhotoCaptureContext) {
        if (shot.failed || shot.delivered || !shot.sequenceCompleted ||
            !hasAllPhotoFrames(shot)
        ) return
        shot.timeout?.let { cameraHandler?.removeCallbacks(it) }
        shot.timeout = null
        // Delivery preserves shutter order even when a preceding reader buffer is late.
        if (!shot.continuousBurst && photoCaptures.values.any { !it.continuousBurst && !it.failed && it.id < shot.id }) return
        shot.delivered = true
        shot.timeout?.let { cameraHandler?.removeCallbacks(it) }
        shot.timeout = null
        photoCaptures.remove(shot.id)
        shot.timestamps.forEach { photoCaptureByTimestamp.remove(it) }
        if (shot.continuousBurst) return
        val photo = CapturedPhoto(shot.id, shot.state, shot.frames.values.toList())
        shot.frames.clear()
        val callback = onPhotoCaptureReady
        if (callback == null) {
            photo.frames.forEach { it.image.close() }
            onPhotoCaptureFailed?.invoke(shot.id)
            deliverNextPhotoCaptureIfReady()
            return
        }
        try {
            callback(photo)
        } catch (e: Exception) {
            photo.frames.forEach { it.image.close() }
            onPhotoCaptureFailed?.invoke(shot.id)
            PLog.e(TAG, "Photo capture callback failed: id=${shot.id}", e)
        }
        deliverNextPhotoCaptureIfReady()
    }

    private fun clearPhotoCaptureProgress(shot: PhotoCaptureContext) {
        _state.update {
            if (it.photoCaptureProgress?.captureId == shot.id) {
                it.copy(photoCaptureProgress = null)
            } else {
                it
            }
        }
    }

    private fun submitPhotoCapture(
        shot: PhotoCaptureContext,
        session: CameraCaptureSession,
        requests: List<CaptureRequest>,
        baseExposureResult: CaptureResult? = lastCaptureResult,
    ) {
        check(requests.size == shot.expectedFrames) {
            "Capture ${shot.id} reserved ${shot.expectedFrames} frames but built ${requests.size} requests"
        }
        shot.livePhotoStartTimestampUs = livePhotoVideoStartTimestampUs
        val captureSurface = shot.reader.surface
        // Sum the actual request plan so HDR frames with different exposures contribute
        // their own duration. Keep one timeline for the entire photo, including frame gaps.
        val spacingKey = Triple(shot.reader.imageFormat, shot.reader.width, shot.reader.height)
        // A request that also targets a JPEG waits for the JPEG encoder between frames (the front camera's still JPEG,
        // the full-size JPEG beside every lilac RAW frame: advertised 500 ms, measured 0.27 to 0.36 s).
        val jpegStallNs = when {
            frontStillJpegActive -> frontStillJpegStallNs
            shot.reader.imageFormat == ImageFormat.RAW_SENSOR || shot.reader.imageFormat == ImageFormat.RAW10 ->
                rawCompanionJpegReader?.let { jpeg ->
                    runCatching {
                        shot.characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?.getOutputStallDuration(ImageFormat.JPEG, Size(jpeg.width, jpeg.height))
                    }.getOrNull()
                } ?: 0L
            else -> 0L
        }
        val exposureDurationNs = requests.sumOf { request ->
            val manualExposure = request.get(CaptureRequest.CONTROL_AE_MODE) ==
                CaptureRequest.CONTROL_AE_MODE_OFF
            val exposureNs = if (manualExposure) {
                request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) ?: shot.state.shutterSpeed
            } else {
                baseExposureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: shot.state.shutterSpeed
            }
            val frameDurationNs = if (manualExposure) {
                request.get(CaptureRequest.SENSOR_FRAME_DURATION)
            } else {
                baseExposureResult?.get(CaptureResult.SENSOR_FRAME_DURATION)
            }
            maxOf(exposureNs, frameDurationNs ?: 0L, jpegStallNs, 1L)
        }
        // Until frames arrive, the ring runs on the spacing measured in the last burst of this kind, else on the
        // exposure plan; the ring has no other way to find out, and it cannot hold back once started.
        val guessedFrameNs = measuredFrameSpacingNs[spacingKey] ?: (exposureDurationNs / requests.size)
        var firstStartTimestamp = 0L
        var startedFrames = 0
        var completedFrames = 0
        // elapsedRealtime when each frame started, and the measured mean delay from a frame's start to its result
        val frameStartedElapsedNs = HashMap<Long, Long>()
        var arrivalDelayNs = -1L
        var lastEstimatedEndNs = 0L
        val callback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long,
            ) {
                if (shot.delivered) {
                    discardCaptureTimestamp(timestamp)
                    return
                }
                shot.startedFrameNumbers.add(frameNumber)
                shot.timestampByFrameNumber[frameNumber] = timestamp
                pendingCaptureStartedTimestamps[frameNumber] = timestamp
                registerPhotoTimestamp(shot, timestamp)
                startedFrames++
                if (startedFrames == 1) firstStartTimestamp = timestamp
                // The ring shows how long to hold still: it aims to fill when the last frame's result arrives, from
                // the guess above and, from the second frame on, the measured spacing (the camera may report no
                // exposure, as lilac's front camera does, or wait between frames for a JPEG), plus the measured
                // delay from a frame's start to its result.
                // Sensor timestamps may use a different clock from elapsedRealtime, so they only give the spacing.
                val nowNs = SystemClock.elapsedRealtimeNanos()
                frameStartedElapsedNs[frameNumber] = nowNs
                val frameNs = if (startedFrames >= 2) {
                    ((timestamp - firstStartTimestamp) / (startedFrames - 1)).also { measuredFrameSpacingNs[spacingKey] = it }
                } else {
                    guessedFrameNs
                }
                if (activePhotoCapture === shot && !shot.continuousBurst && !shot.failed) {
                    lastEstimatedEndNs = nowNs + frameNs * (requests.size - startedFrames) +
                        (if (arrivalDelayNs >= 0) arrivalDelayNs else frameNs)
                    val progress = PhotoCaptureProgress(captureId = shot.id, estimatedEndElapsedNs = lastEstimatedEndNs)
                    _state.update { s ->
                        val current = s.photoCaptureProgress
                        if (current == null || current.captureId == shot.id) s.copy(photoCaptureProgress = progress) else s
                    }
                }
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                val timestamp = getCaptureTimestamp(result)
                shot.startedFrameNumbers.remove(result.frameNumber)
                val arrivedNs = SystemClock.elapsedRealtimeNanos()
                frameStartedElapsedNs.remove(result.frameNumber)?.let { startedNs ->
                    val delayNs = arrivedNs - startedNs
                    arrivalDelayNs = if (arrivalDelayNs < 0) delayNs else (arrivalDelayNs * completedFrames + delayNs) / (completedFrames + 1)
                }
                completedFrames++
                if (completedFrames == requests.size) {
                    PLog.d(TAG, "Capture ring: last frame arrived ${(arrivedNs - lastEstimatedEndNs) / 1_000_000} ms " +
                        "after the estimate")
                    _state.update { s ->
                        val current = s.photoCaptureProgress
                        if (current?.captureId != shot.id) s
                        else s.copy(photoCaptureProgress = current.copy(estimatedEndElapsedNs = arrivedNs, lastFrameArrived = true))
                    }
                }
                if (timestamp == null) {
                    failPhotoCapture(shot, "capture result has no timestamp")
                    return
                }
                shot.timestampByFrameNumber[result.frameNumber] = timestamp
                if (result.frameNumber !in shot.lostImageFrameNumbers) {
                    shot.expectedImageTimestamps.add(timestamp)
                }
                registerPhotoTimestamp(shot, timestamp)
                if (shot.failed || shot.delivered) {
                    settleFailedPhotoCapture(shot)
                    return
                }
                captureFrameMetadata(result, timestamp)?.let { pendingFrameMetadata[timestamp] = it }
                if (shot.animationCaptureResult == null) {
                    shot.animationCaptureResult = result
                }
                val image = pendingImages.remove(timestamp)
                if (image != null) {
                    processAndTriggerCapture(image, result, shot)
                } else {
                    pendingResults[timestamp] = result
                }
                if (request.tag !is MultiFrameCaptureRole || request.tag == MultiFrameCaptureRole.BASE) {
                    lastCaptureResult = result
                }
                if (shot.state.hdrBracketCapturing) logHdrBracketActualExposure(request, result)
            }

            override fun onCaptureSequenceCompleted(
                session: CameraCaptureSession,
                sequenceId: Int,
                frameNumber: Long,
            ) {
                finishPhotoCaptureSequence(shot, successful = true)
            }

            override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
                finishPhotoCaptureSequence(shot, successful = false)
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure,
            ) {
                val timestamp = pendingCaptureStartedTimestamps.remove(failure.frameNumber)
                shot.startedFrameNumbers.remove(failure.frameNumber)
                if (failure.wasImageCaptured() && timestamp != null) {
                    shot.expectedImageTimestamps.add(timestamp)
                } else if (!failure.wasImageCaptured()) {
                    shot.lostImageFrameNumbers.add(failure.frameNumber)
                    timestamp?.let { shot.expectedImageTimestamps.remove(it) }
                }
                if (failure.reason == CaptureFailure.REASON_ERROR) {
                    markRawHalPoisoned(shot, "frame ${failure.frameNumber} failed")
                }
                failPhotoCapture(shot, "frame ${failure.frameNumber} failed: ${failure.reason}")
            }

            override fun onCaptureBufferLost(
                session: CameraCaptureSession,
                request: CaptureRequest,
                target: Surface,
                frameNumber: Long,
            ) {
                if (target == captureSurface) {
                    shot.lostImageFrameNumbers.add(frameNumber)
                    shot.timestampByFrameNumber[frameNumber]?.let { shot.expectedImageTimestamps.remove(it) }
                    markRawHalPoisoned(shot, "image buffer lost for frame $frameNumber")
                    failPhotoCapture(shot, "image buffer lost for frame $frameNumber")
                }
            }
        }
        session.captureBurst(requests, callback, cameraHandler)
        shot.submitted = true
        // A HAL that silently drops a frame never ends the sequence; fail the shot rather than wait forever.
        // RAW bursts on the XZ1 Compact run about 0.3 s per frame beyond the exposure, so allow 1 s per frame.
        val estimatedDurationNs = maxOf(exposureDurationNs, guessedFrameNs * requests.size)
        val stallTimeoutMs = estimatedDurationNs / 1_000_000 + requests.size * 1_000L + CAPTURE_IMAGE_DELIVERY_TIMEOUT_MS
        cameraHandler?.postDelayed({
            if (shot.sequenceCompleted || shot.failed || shot.delivered) return@postDelayed
            PLog.e(TAG, "Capture sequence stalled: id=${shot.id} after ${stallTimeoutMs} ms")
            markRawHalPoisoned(shot, "capture sequence stalled")
            failPhotoCapture(shot, "capture sequence stalled")
        }, stallTimeoutMs)
    }

    private fun trackImage(image: Image?): SafeImage? {
        if (image != null) {
            openImagesCount.getAndIncrement()
            updateCaptureBusy()
        }
        return image?.let { SafeImage(it, this) }
    }

    private fun getCaptureTimestamp(result: TotalCaptureResult): Long? {
        val frameNumber = result.frameNumber
        val startedTimestamp = pendingCaptureStartedTimestamps.remove(frameNumber)
        return result.get(CaptureResult.SENSOR_TIMESTAMP) ?: startedTimestamp
    }

    private fun stopShotSensors(closingCamera: Boolean = false) {
        // Bracketing reads the gyro at the press to size its long shutter, so it keeps running with the viewfinder.
        // shortcut: turning bracketing off leaves it on until the next shot or camera close; fine for a test switch.
        if (closingCamera || !_state.value.isJpgMaxHdrEnabled) burstGyroRecorder.stop()
        ambientLightReader.stop()
    }

    /**
     * lilac front camera, "Accurate" colour: a small JPEG output whose images are dropped. A still request that targets a
     * JPEG gets Sony's still processing for its YUV output too: about 1/15 s instead of 1/30 s and colours as in
     * Sony's own JPEG, without the violet-magenta black floor of plain YUV requests. It costs about 240 ms per frame,
     * and it holds per request only, so every burst frame carries it.
     */
    private fun frontStillJpegReader(captureFormat: Int, characteristics: CameraCharacteristics): ImageReader? {
        if (!DeviceQuirks.isLilac || !_state.value.frontStillJpeg ||
            captureFormat == ImageFormat.RAW_SENSOR || captureFormat == ImageFormat.RAW10 ||
            characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT
        ) return null
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        // any size works the same and is as slow; the smallest at least 640 wide keeps the encoder's work small
        val size = sizes.filter { it.width >= 640 }.minByOrNull { it.width.toLong() * it.height }
            ?: sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
        // measured on lilac: frames about 240 ms apart against an advertised 200 ms stall
        frontStillJpegStallNs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputStallDuration(ImageFormat.JPEG, size)?.takeIf { it > 0L } ?: 200_000_000L
        PLog.i(TAG, "Front still JPEG on: ${size.width}x${size.height} beside the YUV stills, images dropped, " +
            "stall ${frontStillJpegStallNs / 1_000_000} ms")
        return ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader ->
                while (true) {
                    val image = reader.acquireNextImage() ?: break
                    image.close()
                }
            }, cameraHandler)
        }
    }

    /** The id of the shot whose first companion JPEG has [timestamp], when it is to be kept. */
    private fun takeRawCompanionJpeg(timestamp: Long): Long? =
        if (!keepRawCompanionJpeg) null else synchronized(companionJpegShotIds) { companionJpegShotIds.remove(timestamp) }

    private fun processOrBufferImageForCaptureResult(image: SafeImage) {
        val timestamp = image.timestamp
        if (timestamp in discardedCaptureTimestamps) {
            image.close()
            return
        }
        val shot = photoCaptureByTimestamp[timestamp]
        if (shot != null) {
            if (shot.delivered) {
                image.close()
                return
            }
            accountAcquiredPhotoImage(shot, timestamp)
            if (shot.failed) {
                image.close()
                settleFailedPhotoCapture(shot)
                return
            }
        }
        val pendingResult = pendingResults.remove(timestamp)
        if (pendingResult != null && shot != null) {
            processAndTriggerCapture(image, pendingResult, shot)
        } else {
            pendingImages.put(timestamp, image)?.close()
            // An Image callback can precede onCaptureStarted. Keep it until its
            // request timestamp arrives; never assign it using current UI state.
            if (shot == null) {
                cameraHandler?.postDelayed({
                    if (photoCaptureByTimestamp[timestamp] == null &&
                        pendingImages.remove(timestamp, image)
                    ) {
                        PLog.w(TAG, "Discarding image without a capture request: timestamp=$timestamp")
                        image.close()
                    }
                }, CAPTURE_IMAGE_DELIVERY_TIMEOUT_MS)
            }
        }
    }

    private fun gyroWindowExposureNs(
        result: CaptureResult?,
        sensorTimestampNs: Long,
        shot: PhotoCaptureContext,
    ): Long {
        val request = result?.request
        val manualExposure = request?.get(CaptureRequest.CONTROL_AE_MODE) == CaptureRequest.CONTROL_AE_MODE_OFF ||
            request?.get(CaptureRequest.CONTROL_MODE) == CaptureRequest.CONTROL_MODE_OFF
        val frameNumber = result?.frameNumber
        return GyroWindowSpan.exposureNs(
            resultExposureNs = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            manualRequestExposureNs = request?.get(CaptureRequest.SENSOR_EXPOSURE_TIME)?.takeIf { manualExposure },
            frameDurationNs = result?.get(CaptureResult.SENSOR_FRAME_DURATION),
            startTimestampNs = sensorTimestampNs,
            neighbourStartTimestampsNs = frameNumber?.let {
                listOf(shot.timestampByFrameNumber[it - 1], shot.timestampByFrameNumber[it + 1])
            }.orEmpty(),
        )
    }

    private fun captureFrameMetadata(
        result: TotalCaptureResult,
        sensorTimestampNs: Long,
    ): CapturedFrameMetadata? {
        val exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return null
        val sensitivityIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return null
        val exposureProduct = RawExposureMath.productOrNull(exposureTimeNs, sensitivityIso)
            ?: return null
        val desiredExposureProduct = RawExposureMath.productOrNull(
            result.request.get(CaptureRequest.SENSOR_EXPOSURE_TIME),
            result.request.get(CaptureRequest.SENSOR_SENSITIVITY),
        )
        val channelNoiseProfile = captureChannelNoiseProfile(result)
        val framePhysicalCameraId = activeOutputPhysicalCameraId
            ?: result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
        val sensorCharacteristics = framePhysicalCameraId
            ?.let { cameraId ->
                getCameraCharacteristicsOrNull(cameraId, "RAW frame gain limits")
            }
            ?: getActiveOpenCameraCharacteristics()
        val sensitivityLimits = sensorSensitivityLimits(sensorCharacteristics)
        return CapturedFrameMetadata(
            sensorTimestampNs = sensorTimestampNs,
            frameNumber = result.frameNumber,
            exposureTimeNs = exposureTimeNs,
            sensitivityIso = sensitivityIso,
            minimumSensitivityIso = sensitivityLimits.minimumIso,
            maximumAnalogSensitivityIso = sensitivityLimits.maximumAnalogIso,
            exposureProduct = exposureProduct,
            focusDistanceDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: Float.NaN,
            lensState = result.get(CaptureResult.LENS_STATE),
            rollingShutterSkewNs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW),
            gyroWindow = burstGyroRecorder.exposureWindow(sensorTimestampNs, exposureTimeNs),
            channelNoiseProfile = channelNoiseProfile,
            multiFrameCaptureRole = result.request.tag as? MultiFrameCaptureRole,
            desiredExposureProduct = desiredExposureProduct,
            dynamicBlackLevelByCfaPosition = result
                .get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
                ?.takeIf { it.size >= 4 }
                ?.copyOf(4),
        )
    }

    private data class SensorSensitivityLimits(
        val minimumIso: Int,
        val maximumAnalogIso: Int,
    )

    private fun sensorSensitivityLimits(
        characteristics: CameraCharacteristics?,
    ): SensorSensitivityLimits = SensorSensitivityLimits(
        minimumIso = characteristics
            ?.readMetadataOrNull(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ?.lower
            ?: 0,
        maximumAnalogIso = characteristics
            ?.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
            ?: 0,
    )

    private fun captureChannelNoiseProfile(result: CaptureResult): FloatArray? {
        return result.get(CaptureResult.SENSOR_NOISE_PROFILE)
            ?.takeIf { it.isNotEmpty() }
            ?.let { profile ->
                FloatArray(profile.size * 2) { coefficientIndex ->
                    val pair = profile[coefficientIndex / 2]
                    val coefficient = if ((coefficientIndex and 1) == 0) {
                        pair.first
                    } else {
                        pair.second
                    }
                    coefficient.toFloat().takeIf { it.isFinite() && it >= 0f } ?: 0f
                }
            }
    }

    private fun logRawColorMatrices(
        characteristics: CameraCharacteristics,
        result: CaptureResult,
    ) {
        val matrices = listOf(
            "ColorMatrix1" to characteristics.get(
                CameraCharacteristics.SENSOR_COLOR_TRANSFORM1
            ),
            "ColorMatrix2" to characteristics.get(
                CameraCharacteristics.SENSOR_COLOR_TRANSFORM2
            ),
            "ForwardMatrix1" to characteristics.get(
                CameraCharacteristics.SENSOR_FORWARD_MATRIX1
            ),
            "ForwardMatrix2" to characteristics.get(
                CameraCharacteristics.SENSOR_FORWARD_MATRIX2
            ),
        )
        val illuminant1 = characteristics.get(
            CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1
        )
        val illuminant2 = characteristics.get(
            CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2
        )

        PLog.i(
            TAG,
            buildString {
                appendLine(
                    "RAW color matrices: frame=${result.frameNumber}, " +
                        "timestamp=${result.get(CaptureResult.SENSOR_TIMESTAMP)}, " +
                        "referenceIlluminant1=$illuminant1, referenceIlluminant2=$illuminant2"
                )
                matrices.forEachIndexed { index, (name, matrix) ->
                    append(name)
                    append(" = ")
                    append(formatColorSpaceTransform(matrix))
                    if (index != matrices.lastIndex) appendLine()
                }
            }
        )
    }

    private fun formatColorSpaceTransform(transform: ColorSpaceTransform?): String {
        if (transform == null) return "null"
        return (0 until 3).joinToString(prefix = "[", postfix = "]", separator = ", ") { row ->
            (0 until 3).joinToString(prefix = "[", postfix = "]", separator = ", ") { col ->
                val value = transform.getElement(col, row)
                "${value.numerator}/${value.denominator} (${value.toDouble()})"
            }
        }
    }

    // 快门音效播放回调
    var onPlayShutterSound: (() -> Unit)? = null

    // Live Photo 录制状态
    var onLivePhotoVideoCaptured: ((java.io.File, Long) -> Unit)? = null

    // 相机错误回调（供上层处理错误恢复）
    // errorCode: CameraDevice 的错误代码或自定义错误码
    // canRetry: 是否可以重试打开相机
    var onCameraError: ((errorCode: Int, message: String, canRetry: Boolean) -> Unit)? = null

    /**
     * Runs on the camera thread: false before the preview stream can stop (its buffers are then
     * freed), true once a new preview session runs. The preview renderer must stop sampling the
     * camera texture synchronously, so this is not a StateFlow.
     */
    var onPreviewStreamActive: ((Boolean) -> Unit)? = null

    fun onImageRelease() {
        if (openImagesCount.decrementAndGet() == 0) {
            checkAndClosePendingReaders()
        }
        updateCaptureBusy()
    }

    /**
     * Mirrors the refusals in [reservePhotoCapture] so the shutter button can show them before a press:
     * the lilac RAW guard, and buffers too full for the next shot (Photo mode shots still being stacked hold their frames).
     */
    private fun updateCaptureBusy() {
        val reader = imageReader
        val open = openImagesCount.get()
        val rawBusy = IS_LILAC && isRawCaptureReader(reader) && open > 0
        val full = reader != null &&
            open + reservedImageCount() + captureImageRequestCount(_state.value, reader) > CAPTURE_READER_MAX_IMAGES
        val busy = rawBusy || full
        _state.update { if (it.captureBusy == busy) it else it.copy(captureBusy = busy) }
    }

    private fun resolveImageReaderMaxImages(): Int = CAPTURE_READER_MAX_IMAGES

    private fun captureImageRequestCount(state: CameraState, reader: ImageReader): Int = when {
        shouldUseJpgMaxHdrCapture(state, isRawCaptureReader(reader)) && !shouldUseMultiFrameTorch(state) ->
            buildHdrBracketEvOffsets(resolveHdrBracketZeroEvFrameCount(state)).size
        state.requiresMultiFrameCaptureSequence -> state.activeMultiFrameCount
        else -> 1
    }

    private fun canAcquireImage(logPrefix: String): Boolean {
        val openImages = openImagesCount.get()
        if (openImages >= imageReaderMaxImages) {
            PLog.w(TAG, "$logPrefix ($openImages/$imageReaderMaxImages), skipping acquire")
            return false
        }
        return true
    }

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            val tag = request.tag as? StillFlashRequestTag ?: return
            val pending = stillFlashPrecapture ?: return
            if (tag.generation == pending.generation && tag.phase == pending.phase && tag.isTrigger) {
                pending.triggerFrameNumber = frameNumber
            }
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure,
        ) {
            if (request.get(CaptureRequest.CONTROL_AF_TRIGGER) == CaptureRequest.CONTROL_AF_TRIGGER_START) {
                pointAfTriggerPending = false
            }
            val flashTag = request.tag as? StillFlashRequestTag
            if (flashTag != null) {
                val pending = stillFlashPrecapture ?: return
                if (flashTag.generation == pending.generation && flashTag.phase == pending.phase) {
                    abortStillFlashPrecapture("request failed: frame=${failure.frameNumber} reason=${failure.reason}")
                }
                return
            }
        }

        override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
            if (session !== captureSession) return
            stillFlashPrecapture?.let { pending ->
                if (sequenceId == pending.triggerSequenceId || sequenceId == pending.repeatingSequenceId) {
                    abortStillFlashPrecapture("capture sequence aborted: $sequenceId")
                    return
                }
            }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            if (session === captureSession && _state.value.captureMode == CaptureMode.VIDEO) updateSlowMotionDark(result)

            if (session === captureSession && _state.value.previewFirstFrameTimestampNs == null) {
                result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { timestamp ->
                    _state.update { it.copy(previewFirstFrameTimestampNs = timestamp) }
                }
            }

            realtimeStabilizationCoordinator.submitCaptureResult(
                result = result,
                request = request,
                outputPhysicalCameraId = activeOutputPhysicalCameraId,
            )

            lastCaptureResult = result
            logVideoCaptureStats(result)
            if (session === captureSession) updateEnginePreviewSource(result)

            // 处理拍照状态机
            processStillFlashPrecapture(request, result)
            processMultiFrameTorchWarmupState(request, result)
            if (session === captureSession) {
                readLaser(result)
                handleSubjectResult(result)
                handleHintResult(result)
            }

            // 监听对焦状态
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            val afStateChanged = afState != null && afState != lastAfState
            if (afStateChanged) {
                lastAfState = afState
            }
            if (pointAfTriggerPending && (
                    result.request.get(CaptureRequest.CONTROL_AF_TRIGGER) == CaptureRequest.CONTROL_AF_TRIGGER_START ||
                        afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN
                    )
            ) {
                pointAfTriggerPending = false
            }
            if (_state.value.isAutoFocus && _state.value.isFocusing && !pointAfTriggerPending) {
                when (afState) {
                    CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> {
                        logFocusTerminalResult(result, afState)
                        _state.update { s -> s.copy(isFocusing = false, focusSuccess = true) }
                        // 只在首次锁定时记录一次，后续 AF 狩猎重新锁定不再覆盖
                        if (!_state.value.isFocusLocked && !isFocusLockedWaitingForSceneChange) {
                            recordFocusLockExposure(result)
                        }
                    }

                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
                        logFocusTerminalResult(result, afState)
                        _state.update { s -> s.copy(isFocusing = false, focusSuccess = false) }
                        if (!_state.value.isFocusLocked && !isFocusLockedWaitingForSceneChange) {
                            recordFocusLockExposure(result)
                        }
                        if (_state.value.focusPointSource == FocusPointSource.MANUAL && parkLensIfFar("tap focus missed")) {
                            // The parked lens is the new reference, so the scene watch does not read the move as a change.
                            focusLockedReferenceDistance = previewRequestBuilder?.get(CaptureRequest.LENS_FOCUS_DISTANCE) ?: 0f
                            focusLockSettleFrames = FOCUS_LOCK_SETTLE_FRAMES
                        }
                    }
                }
            }

            // 独立测光会主动改变曝光，期间保留用户选定的 AF 点，直到重新点按或解锁。
            // 其余状态在对焦后持续监测曝光变化和焦距跳变。
            if (_state.value.isAutoFocus && isFocusLockedWaitingForSceneChange && _state.value.meteringPoint == null && !shutterKeyHold) {
                // 对焦锁定后前几帧镜头还在微调，跳过不检测
                if (focusLockSettleFrames > 0) {
                    focusLockSettleFrames--
                } else {
                    val currentIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                    val currentExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                    val currentFocusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
                    var sceneChanged = false

                    if (sceneExposureReferencePending &&
                        result.get(CaptureResult.CONTROL_AE_STATE) != CaptureResult.CONTROL_AE_STATE_SEARCHING
                    ) {
                        updateFocusLockSceneReference(result)
                        sceneExposureReferencePending = false
                    }

                    // 1. 曝光变化检测
                    if (!sceneExposureReferencePending && focusLockedReferenceIso > 0 && focusLockedReferenceExposureNs > 0 &&
                        currentIso > 0 && currentExposure > 0L
                    ) {
                        val refProduct = focusLockedReferenceIso.toDouble() * focusLockedReferenceExposureNs.toDouble()
                        val curProduct = currentIso.toDouble() * currentExposure.toDouble()
                        if (refProduct > 0) {
                            val ratio = if (curProduct > refProduct) curProduct / refProduct else refProduct / curProduct
                            if (ratio > SCENE_CHANGE_EXPOSURE_RATIO) {
//                                PLog.d(TAG, "scene change: exposure ratio=$ratio")
                                sceneChanged = true
                            }
                        }
                    }

                    // 2. 焦距跳变检测：逐帧跟踪，CONTINUOUS_PICTURE 模式下 AF 系统持续工作
                    //    当场景距离变化时，AF 会重新对焦导致焦距大幅跳变
                    if (focusLockedReferenceDistance > 0f && currentFocusDistance > 0f) {
                        val delta = abs(currentFocusDistance - focusLockedReferenceDistance)
                        if (delta > SCENE_CHANGE_FOCUS_DISTANCE_DELTA) {
//                            PLog.d(TAG, "scene change: focusDistance delta=$delta (ref=$focusLockedReferenceDistance, cur=$currentFocusDistance)")
                            sceneChanged = true
                        }
                    }

                    if (sceneChanged) {
                        if (isEyeTargetRecentlySeen()) {
                            // 人眼目标仍在持续更新时，以当前状态作为新的参考，避免场景变化
                            // 检测把正在跟踪的人眼区域恢复成全画面连续对焦。
                            updateFocusLockSceneReference(result)
                            sceneChangeFrameCount = 0
                        } else {
                            sceneChangeFrameCount++
                            if (sceneChangeFrameCount >= SCENE_CHANGE_CONFIRM_FRAMES) {
                                PLog.d(TAG, "Scene changed: tap focus released")
                                restoreContinuousAf()
                            }
                        }
                    } else {
                        sceneChangeFrameCount = 0
                    }
                }
            }

            // 获取相机实际使用的参数
            val actualIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val actualExposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
            // 判断是否为自动曝光模式（包括所有 AE_MODE_ON 的变体）
            val isAutoExposure = aeMode == CaptureResult.CONTROL_AE_MODE_ON
                    || aeMode == CaptureResult.CONTROL_AE_MODE_ON_AUTO_FLASH
                    || aeMode == CaptureResult.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            val exposureCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0
            val awbMode = result.get(CaptureResult.CONTROL_AWB_MODE) ?: _state.value.awbMode
            val whiteBalanceResult = readWhiteBalanceResult(result, awbMode)
            lastWhiteBalanceResult = whiteBalanceResult
            val actualAwbTemperature =
                whiteBalanceResult.colorTemperature ?: whiteBalanceResult.gains?.let(::estimateKelvinFromRggbGains)
            val actualAwbGains = whiteBalanceResult.gains?.toStateGains()
            val awbRange = resolveAwbTemperatureRange()
            val canAdjustWhiteBalance =
                if (_state.value.awbMode == CameraMetadata.CONTROL_AWB_MODE_OFF) {
                    manualWhiteBalanceAnchor != null
                } else {
                    canAdjustManualWhiteBalance(whiteBalanceResult)
                }
            val aperture = result.get(CaptureResult.LENS_APERTURE)
            val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f

            // 关键修复：只在自动曝光模式下更新 ISO 和快门速度
            // 手动模式下保持用户设置不变（因为预览使用的是限制后的曝光时间，不是用户设置的值）
            _state.update { s -> s.copy(
                iso = if (isAutoExposure && _state.value.isIsoAuto) actualIso ?: _state.value.iso else _state.value.iso,
                shutterSpeed = if (isAutoExposure && _state.value.isShutterSpeedAuto) actualExposureTimeNs
                    ?: _state.value.shutterSpeed else _state.value.shutterSpeed,
                // In-flight results must not undo a user's WB mode change. A session without
                // a manual anchor still reports the automatic fallback used by the request.
                awbMode = if (_state.value.awbMode == CameraMetadata.CONTROL_AWB_MODE_OFF &&
                    manualWhiteBalanceAnchor == null
                ) awbMode else _state.value.awbMode,
                actualAwbTemperature = actualAwbTemperature,
                actualAwbTint = whiteBalanceResult.colorTint,
                actualAwbGains = actualAwbGains,
                canAdjustWhiteBalance = canAdjustWhiteBalance,
                supportsCctWhiteBalance = supportsCctWhiteBalance(),
                awbTemperatureMin = awbRange.lower,
                awbTemperatureMax = awbRange.upper,
                physicalAperture = aperture ?: _state.value.physicalAperture,
                // 手动对焦保留用户目标值，避免镜头移动中的回报值改变后续拍摄和预览请求。
                focusDistance = if (_state.value.isAutoFocus) focusDistance else _state.value.focusDistance
            ) }
            pendingRetainedAwbTemperature?.let { temperature ->
                if (canAdjustManualWhiteBalance(whiteBalanceResult)) {
                    setAwbTemperature(temperature)
                    if (pendingRetainedAwbTemperature == null) {
                        PLog.d(TAG, "Restored retained white balance: ${temperature}K")
                    }
                }
            }
        }
    }

    private var lastAfState: Int? = null

    private fun logVideoCaptureStats(result: TotalCaptureResult) {
        if (!_state.value.videoRecordingState.isRecording) return

        val sensorTimestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val nowMs = SystemClock.elapsedRealtime()
        if (videoCaptureStatsWindowStartMs == 0L) {
            videoCaptureStatsWindowStartMs = nowMs
            videoCaptureStatsFrames = 1
            videoCaptureStatsFirstTimestampNs = sensorTimestampNs
            return
        }

        videoCaptureStatsFrames += 1
        val elapsedMs = nowMs - videoCaptureStatsWindowStartMs
        if (elapsedMs < 1000L) return

        val sensorElapsedNs =
            (sensorTimestampNs - videoCaptureStatsFirstTimestampNs).coerceAtLeast(0L)
        val callbackFps = videoCaptureStatsFrames * 1000f / elapsedMs.toFloat()
        val sensorFps = if (sensorElapsedNs > 0L && videoCaptureStatsFrames > 1) {
            (videoCaptureStatsFrames - 1) * 1_000_000_000f / sensorElapsedNs.toFloat()
        } else {
            0f
        }
        val fpsRange = result.readMetadataOrNull(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
        PLog.i(
            TAG,
            "Video capture stats: requested=${_state.value.videoConfig.fps.fps}, " +
                "aeRange=$fpsRange, callbackFps=${"%.1f".format(callbackFps)}, " +
                "sensorFps=${"%.1f".format(sensorFps)}, preview=${_state.value.currentPreviewSize.width}x${_state.value.currentPreviewSize.height}"
        )

        videoCaptureStatsWindowStartMs = nowMs
        videoCaptureStatsFrames = 1
        videoCaptureStatsFirstTimestampNs = sensorTimestampNs
    }

    /** MGC forced flash: AE converged, AF locked when supported, AWB unrestricted. */
    private fun processStillFlashPrecapture(request: CaptureRequest, result: TotalCaptureResult) {
        val pending = stillFlashPrecapture ?: return
        val tag = request.tag as? StillFlashRequestTag ?: return
        if (tag.generation != pending.generation || tag.phase != pending.phase) return

        if (tag.isTrigger && pending.triggerFrameNumber == null) {
            pending.triggerFrameNumber = result.frameNumber
        }
        if (pending.phase != StillFlashPhase.APPLYING_SESSION) {
            val triggerFrame = pending.triggerFrameNumber ?: return
            if (result.frameNumber < triggerFrame) return
        }
        if (pending.firstResultFrameNumber == null) {
            pending.firstResultFrameNumber = result.frameNumber
        }
        pending.lastResult = result

        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
        val afMode = result.get(CaptureResult.CONTROL_AF_MODE)
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        PLog.d(
            TAG,
            "Still flash 3A: generation=${pending.generation} phase=${pending.phase} " +
                "frame=${result.frameNumber} trigger=${tag.isTrigger} " +
                "aeMode=$aeMode ae=$aeState afMode=$afMode af=$afState " +
                "awb=${result.get(CaptureResult.CONTROL_AWB_STATE)} " +
                "flash=${result.get(CaptureResult.FLASH_STATE)}",
        )
        when (pending.phase) {
            StillFlashPhase.APPLYING_SESSION -> {
                val flashMode = result.get(CaptureResult.FLASH_MODE)
                if (aeMode == request.get(CaptureRequest.CONTROL_AE_MODE) &&
                    afMode == pending.afMode &&
                    flashMode == request.get(CaptureRequest.FLASH_MODE)
                ) {
                    val continuousAf = pending.afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                        pending.afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    submitStillFlashPhase(
                        pending,
                        if (pending.triggerAf && continuousAf) StillFlashPhase.WAITING_AF_CONVERGENCE
                        else StillFlashPhase.WAITING_AE_AF,
                    )
                    return
                }
            }
            StillFlashPhase.WAITING_AF_CONVERGENCE -> {
                // CANCEL releases any previous continuous-AF lock before starting a new sweep.
                if (afMode == pending.afMode &&
                    (afState == CaptureResult.CONTROL_AF_STATE_INACTIVE ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED)
                ) {
                    submitStillFlashPhase(pending, StillFlashPhase.WAITING_AE_AF)
                    return
                }
            }
            StillFlashPhase.WAITING_AE_AF -> {
                // The trigger result is eligible. Like MGC's per-key listeners, retain each
                // completed condition while waiting for the other; AWB is not a prerequisite.
                pending.aeReady = pending.aeReady ||
                    (aeMode == request.get(CaptureRequest.CONTROL_AE_MODE) &&
                        (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED))
                pending.afReady = pending.afReady || !pending.triggerAf ||
                    (afMode == pending.afMode &&
                        (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED))
                if (pending.aeReady && pending.afReady) {
                    completePrecapture(result, "AE converged and AF ready")
                    return
                }
            }
        }
        if (result.frameNumber - pending.firstResultFrameNumber!! > PRECAPTURE_MAX_WAIT_FRAMES) {
            onStillFlashPhaseTimeout(pending, "frame limit")
        }
    }

    private fun createPhotoPreparationRequestBuilder(device: CameraDevice): CaptureRequest.Builder {
        val previewTarget = checkNotNull(previewSurface) {
            "Preview surface unavailable for photo preparation"
        }
        val stabilizationTarget = if (shouldUseAlgorithmicStabilization()) {
            checkNotNull(stabilizationImageReader) {
                "Stabilization reader unavailable for photo preparation"
            }.surface
        } else {
            null
        }
        return device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            // Preserve the configured preview outputs, including the EIS YUV reader. Both
            // repeating and trigger requests must keep feeding it through previewCallback
            // so each stabilization image has matching CaptureResult metadata.
            addTarget(previewTarget)
            stabilizationTarget?.let(::addTarget)
        }
    }

    private fun buildStillFlashPreviewRequest(
        device: CameraDevice,
        pending: StillFlashPrecapture,
        isTrigger: Boolean,
    ): CaptureRequest {
        val triggerAe = isTrigger && pending.phase == StillFlashPhase.WAITING_AE_AF
        return createPhotoPreparationRequestBuilder(device).apply {
            applyBaseCameraSettings(
                builder = this,
                isCapture = false,
                useStillFlashAeMode = true,
                useStillFlashTrigger = triggerAe,
                currentState = checkNotNull(stillFlashCaptureState),
            )
            set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW)
            set(CaptureRequest.CONTROL_AE_LOCK, false)
            set(CaptureRequest.CONTROL_AF_MODE, pending.afMode)
            set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                when {
                    !isTrigger || !pending.triggerAf -> CaptureRequest.CONTROL_AF_TRIGGER_IDLE
                    pending.phase == StillFlashPhase.WAITING_AF_CONVERGENCE -> CaptureRequest.CONTROL_AF_TRIGGER_CANCEL
                    else -> CaptureRequest.CONTROL_AF_TRIGGER_START
                },
            )
            setAePrecaptureTriggerIfSupported(
                this,
                if (triggerAe) CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
                else CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
            )
            previewRequestBuilder?.let { preview ->
                preview.get(CaptureRequest.CONTROL_AF_REGIONS)?.let { set(CaptureRequest.CONTROL_AF_REGIONS, it) }
                preview.get(CaptureRequest.CONTROL_AE_REGIONS)?.let { set(CaptureRequest.CONTROL_AE_REGIONS, it) }
            }
            setTag(StillFlashRequestTag(pending.generation, pending.phase, isTrigger))
        }.build()
    }

    private fun submitStillFlashPhase(pending: StillFlashPrecapture, phase: StillFlashPhase) {
        if (stillFlashPrecapture !== pending) return
        val session = captureSession
        val device = cameraDevice
        val handler = cameraHandler
        if (session == null || device == null || previewSurface == null || handler == null) {
            abortStillFlashPrecapture("camera unavailable")
            return
        }
        precaptureTimeoutRunnable?.let(handler::removeCallbacks)
        pending.phase = phase
        pending.triggerFrameNumber = null
        pending.triggerSequenceId = null
        pending.repeatingSequenceId = null
        pending.firstResultFrameNumber = null
        pending.lastResult = null
        pending.aeReady = false
        pending.afReady = !pending.triggerAf
        try {
            val repeating = buildStillFlashPreviewRequest(device, pending, isTrigger = false)
            if (phase != StillFlashPhase.APPLYING_SESSION) {
                val trigger = buildStillFlashPreviewRequest(device, pending, isTrigger = true)
                pending.triggerSequenceId = session.capture(trigger, previewCallback, handler)
            }
            pending.repeatingSequenceId = session.setRepeatingRequest(repeating, previewCallback, handler)
            val timeout = Runnable {
                if (stillFlashPrecapture === pending && pending.phase == phase) {
                    onStillFlashPhaseTimeout(pending, "time limit")
                }
            }
            precaptureTimeoutRunnable = timeout
            handler.postDelayed(timeout, PRECAPTURE_TIMEOUT_MS)
            PLog.i(TAG, "Still flash phase submitted: generation=${pending.generation} phase=$phase " +
                "afMode=${pending.afMode} triggerAf=${pending.triggerAf} " +
                "aeMode=${repeating.get(CaptureRequest.CONTROL_AE_MODE)} " +
                "stabilizationTarget=${shouldUseAlgorithmicStabilization()}")
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to submit still flash phase $phase", e)
            abortStillFlashPrecapture("phase submission failed: $phase")
        }
    }

    private fun onStillFlashPhaseTimeout(pending: StillFlashPrecapture, reason: String) {
        PLog.w(TAG, "Still flash wait expired: generation=${pending.generation} phase=${pending.phase} " +
            "reason=$reason aeReady=${pending.aeReady} afReady=${pending.afReady}")
        if (pending.lastResult == null) {
            abortStillFlashPrecapture("no capture results for ${pending.phase}")
            return
        }
        when (pending.phase) {
            StillFlashPhase.APPLYING_SESSION -> abortStillFlashPrecapture("flash session did not apply")
            StillFlashPhase.WAITING_AF_CONVERGENCE -> submitStillFlashPhase(pending, StillFlashPhase.WAITING_AE_AF)
            StillFlashPhase.WAITING_AE_AF -> completePrecapture(pending.lastResult, reason)
        }
    }

    private fun abortStillFlashPrecapture(reason: String) {
        PLog.e(TAG, "Still flash preparation failed: $reason")
        // The reserved shot has not been submitted; resetPreviewAfterCapture fails it and
        // releases its buffers, AF ownership and pending capture references together.
        resetPreviewAfterCapture()
    }

    /**
     * CameraX does not wait for an AF lock when continuous AF, manual focus, or an unknown AF
     * mode is active. For a one-shot AF mode it waits for one of the terminal AF states.
     */
    private fun isTorchWarmupAfReady(result: CaptureResult): Boolean {
        val afMode = result.get(CaptureResult.CONTROL_AF_MODE)
        if (afMode == null ||
            afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
            afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ||
            afMode == CaptureRequest.CONTROL_AF_MODE_OFF
        ) {
            return true
        }

        return when (result.get(CaptureResult.CONTROL_AF_STATE)) {
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED,
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> true

            else -> false
        }
    }

    private fun completePrecapture(result: CaptureResult?, reason: String) {
        val pending = stillFlashPrecapture ?: return
        pendingCaptureBaseExposureResult = result ?: pendingCaptureBaseExposureResult
        clearPrecaptureTracking()
        PLog.i(
            TAG,
            "Precapture complete: generation=${pending.generation}, reason=$reason, " +
                "aeReady=${pending.aeReady} afReady=${pending.afReady} " +
                "ae=${result?.get(CaptureResult.CONTROL_AE_STATE)} " +
                "af=${result?.get(CaptureResult.CONTROL_AF_STATE)} " +
                "iso=${result?.get(CaptureResult.SENSOR_SENSITIVITY)} " +
                "shutter=${result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)}",
        )
        runCaptureSequence()
    }

    private fun clearPrecaptureTracking() {
        precaptureTimeoutRunnable?.let { cameraHandler?.removeCallbacks(it) }
        precaptureTimeoutRunnable = null
        stillFlashPrecapture = null
    }

    private fun processMultiFrameTorchWarmupState(
        request: CaptureRequest,
        result: TotalCaptureResult,
    ) {
        val tag = request.tag as? MultiFrameTorchWarmupRequestTag ?: return
        if (tag.generation != activeMultiFrameTorchWarmupGeneration) return

        lastMultiFrameTorchWarmupResult = result
        if (tag.isAePrecaptureTrigger) {
            multiFrameTorchWarmupTriggerResultSeen = true
            return
        }

        if (multiFrameTorchWarmupNeedsAePrecapture &&
            !multiFrameTorchWarmupPrecaptureSubmitted
        ) {
            if (isTorchSessionUpdateApplied(request, result)) {
                submitMultiFrameTorchWarmupPrecapture(tag.generation)
            }
            return
        }

        if (multiFrameTorchWarmupNeedsAePrecapture &&
            !multiFrameTorchWarmupTriggerResultSeen
        ) {
            return
        }

        if (isTorchWarmup3aReady(result)) {
            completeMultiFrameTorchWarmup(result, "torch and 3A ready")
        }
    }

    private fun isTorchSessionUpdateApplied(
        request: CaptureRequest,
        result: TotalCaptureResult,
    ): Boolean {
        val requestedAeMode = request.get(CaptureRequest.CONTROL_AE_MODE)
        val appliedAeMode = result.get(CaptureResult.CONTROL_AE_MODE)
        val requestedFlashMode = request.get(CaptureRequest.FLASH_MODE)
        val appliedFlashMode = result.get(CaptureResult.FLASH_MODE)
        return (appliedAeMode == null || appliedAeMode == requestedAeMode) &&
            (appliedFlashMode == null || appliedFlashMode == requestedFlashMode)
    }

    private fun submitMultiFrameTorchWarmupPrecapture(generation: Long) {
        if (multiFrameTorchWarmupPrecaptureSubmitted) return

        val session = captureSession
        val device = cameraDevice
        val handler = cameraHandler
        if (session == null || device == null || previewSurface == null || handler == null) {
            completeMultiFrameTorchWarmup(lastMultiFrameTorchWarmupResult, "torch precapture unavailable")
            return
        }

        try {
            val triggerRequest = createPhotoPreparationRequestBuilder(device).apply {
                set(
                    CaptureRequest.CONTROL_CAPTURE_INTENT,
                    CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW
                )
                applyBaseCameraSettings(this, isCapture = false)
                if (_state.value.isAutoExposure) {
                    set(
                        CaptureRequest.CONTROL_AE_MODE,
                        resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                    )
                }
                set(CaptureRequest.CONTROL_AE_LOCK, false)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                setAePrecaptureTriggerIfSupported(
                    this,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START,
                )
                setTag(
                    MultiFrameTorchWarmupRequestTag(
                        generation = generation,
                        isAePrecaptureTrigger = true,
                    )
                )
            }.build()

            multiFrameTorchWarmupPrecaptureSubmitted = true
            session.capture(triggerRequest, previewCallback, handler)
            PLog.d(
                TAG,
                "Multi-frame torch precapture submitted: generation=$generation, " +
                    "aeMode=${triggerRequest.get(CaptureRequest.CONTROL_AE_MODE)}, " +
                    "stabilizationTarget=${shouldUseAlgorithmicStabilization()}"
            )
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to submit multi-frame torch precapture", e)
            completeMultiFrameTorchWarmup(lastMultiFrameTorchWarmupResult, "torch precapture submission failed")
        }
    }

    private fun isTorchWarmup3aReady(result: CaptureResult): Boolean {
        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val aeReady = aeMode == CaptureResult.CONTROL_AE_MODE_OFF ||
            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED
        val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)
        val awbReady = awbState == null || awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED
        return aeReady && awbReady && isTorchWarmupAfReady(result)
    }

    private fun completeMultiFrameTorchWarmup(
        result: TotalCaptureResult?,
        reason: String,
    ) {
        val generation = activeMultiFrameTorchWarmupGeneration
        if (generation == 0L) return

        val onReady = pendingMultiFrameTorchWarmupAction
        clearMultiFrameTorchWarmupTracking()
        PLog.i(
            TAG,
            "Multi-frame torch warm-up complete: generation=$generation, reason=$reason, " +
                "aeState=${result?.get(CaptureResult.CONTROL_AE_STATE)}, " +
                "flashState=${result?.get(CaptureResult.FLASH_STATE)}, " +
                "iso=${result?.get(CaptureResult.SENSOR_SENSITIVITY)}, " +
                "shutter=${result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)}"
        )
        onReady?.invoke(result)
    }

    private fun clearMultiFrameTorchWarmupTracking() {
        multiFrameTorchWarmupTimeoutRunnable?.let { cameraHandler?.removeCallbacks(it) }
        multiFrameTorchWarmupTimeoutRunnable = null
        activeMultiFrameTorchWarmupGeneration = 0L
        lastMultiFrameTorchWarmupResult = null
        pendingMultiFrameTorchWarmupAction = null
        multiFrameTorchWarmupNeedsAePrecapture = false
        multiFrameTorchWarmupPrecaptureSubmitted = false
        multiFrameTorchWarmupTriggerResultSeen = false
    }

    private fun abortMultiFrameTorchWarmup(reason: String, error: Throwable? = null) {
        if (error != null) {
            PLog.e(TAG, "Multi-frame torch warm-up failed: $reason", error)
        } else {
            PLog.e(TAG, "Multi-frame torch warm-up failed: $reason")
        }
        clearMultiFrameTorchWarmupTracking()
        pendingCaptureDevice = null
        pendingCaptureReader = null
        pendingCaptureBaseExposureResult = null
        burstCapturing = false
        pendingContinuousBurstCapture?.let {
            failPhotoCapture(it, "continuous burst preparation cancelled")
        }
        pendingContinuousBurstCapture = null
        isMultiFrameTorchCaptureActive = false
        isContinuousBurstTorchActive = false
        _state.update { s -> s.copy(isCapturing = false, burstCapturing = false) }
        resetPreviewAfterCapture()
    }

    /**
     * 运行最终的拍照序列
     */
    private fun runCaptureSequence() {
        val device = pendingCaptureDevice
        val reader = pendingCaptureReader
        val baseExposureResult = pendingCaptureBaseExposureResult
        if (device != null && reader != null) {
            performCapture(device, reader, baseExposureResult)
        }
        // 清理缓存的数据
        pendingCaptureDevice = null
        pendingCaptureReader = null
        pendingCaptureBaseExposureResult = null
    }

    /**
     * 多帧模式不能连续触发单次主闪。先用持续补光让 AE 收敛，再保持补光完成整组拍摄。
     */
    private fun runMultiFrameTorchWarmupSequence(
        onReady: (TotalCaptureResult?) -> Unit,
    ) {
        val session = captureSession
        val device = cameraDevice
        val handler = cameraHandler
        if (session == null || device == null || previewSurface == null || handler == null) {
            abortMultiFrameTorchWarmup("camera session unavailable")
            return
        }

        val generation = ++multiFrameTorchWarmupGeneration
        activeMultiFrameTorchWarmupGeneration = generation
        lastMultiFrameTorchWarmupResult = null
        pendingMultiFrameTorchWarmupAction = onReady
        multiFrameTorchWarmupPrecaptureSubmitted = false
        multiFrameTorchWarmupTriggerResultSeen = false

        try {
            val torchRequestBuilder = createPhotoPreparationRequestBuilder(device).apply {
                set(
                    CaptureRequest.CONTROL_CAPTURE_INTENT,
                    CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW
                )
                applyBaseCameraSettings(this, isCapture = false)
                if (_state.value.isAutoExposure) {
                    set(
                        CaptureRequest.CONTROL_AE_MODE,
                        resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                    )
                }
                set(CaptureRequest.CONTROL_AE_LOCK, false)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                setAePrecaptureTriggerIfSupported(
                    this,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
                )
                setTag(MultiFrameTorchWarmupRequestTag(generation))
            }
            val torchRequest = torchRequestBuilder.build()

            session.setRepeatingRequest(torchRequest, previewCallback, handler)
            multiFrameTorchWarmupNeedsAePrecapture = _state.value.isAutoExposure &&
                torchRequest.get(CaptureRequest.CONTROL_AE_MODE) != CaptureRequest.CONTROL_AE_MODE_OFF &&
                isAePrecaptureSupported()
            val timeout = Runnable {
                if (activeMultiFrameTorchWarmupGeneration != generation) return@Runnable
                val latestResult = lastMultiFrameTorchWarmupResult
                PLog.w(
                    TAG,
                    "Multi-frame torch warm-up timeout: generation=$generation, " +
                        "aeState=${latestResult?.get(CaptureResult.CONTROL_AE_STATE)}, " +
                        "flashState=${latestResult?.get(CaptureResult.FLASH_STATE)}"
                )
                completeMultiFrameTorchWarmup(latestResult, "timeout")
            }
            multiFrameTorchWarmupTimeoutRunnable = timeout
            handler.postDelayed(timeout, MULTI_FRAME_TORCH_WARMUP_TIMEOUT_MS)
            PLog.i(
                TAG,
                "Multi-frame torch warm-up started: generation=$generation, " +
                    "aeMode=${torchRequest.get(CaptureRequest.CONTROL_AE_MODE)}, " +
                    "flashMode=${torchRequest.get(CaptureRequest.FLASH_MODE)}, " +
                    "stabilizationTarget=${shouldUseAlgorithmicStabilization()}"
            )
        } catch (e: Exception) {
            abortMultiFrameTorchWarmup("request submission", e)
        }
    }

    /**
     * 运行预取序列（预闪）
     */
    private fun runPrecaptureSequence() {
        val state = activePhotoCapture?.state ?: _state.value
        stillFlashCaptureState = state
        val afMode = if (state.isAutoFocus) {
            previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)
                ?: resolveAutoFocusMode(state.captureMode)
        } else {
            CaptureRequest.CONTROL_AF_MODE_OFF
        }
        val triggerAf = state.isAutoFocus && supportsAfTrigger(afMode) &&
            isCaptureRequestKeyAvailable(CaptureRequest.CONTROL_AF_TRIGGER.name)
        val pending = StillFlashPrecapture(++precaptureGeneration, afMode, triggerAf)
        stillFlashPrecapture = pending
        stillFlashAfMode = afMode
        stillFlashFocusDistance = if (afMode == CaptureRequest.CONTROL_AF_MODE_OFF) {
            previewRequestBuilder?.get(CaptureRequest.LENS_FOCUS_DISTANCE)
                ?: resolveValidFocusDistance(pendingCaptureBaseExposureResult, state)
        } else {
            null
        }
        isCaptureFocusFrozen = true
        submitStillFlashPhase(pending, StillFlashPhase.APPLYING_SESSION)
    }

    // ==================== 初始化 ====================

    /**
     * 初始化相机
     */
    fun initialize() {
        PLog.i(TAG, "初始化相机控制器")
        startBackgroundThread()
    }

    private suspend fun runOnCameraThread(action: () -> Boolean): Boolean =
        suspendCancellableCoroutine { continuation ->
            val handler = cameraHandler
            if (handler == null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            handler.post {
                val result = action()
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /** Runs coroutine code on the camera thread, in order with the controller's own camera work. */
    val cameraDispatcher: CoroutineDispatcher
        get() = checkNotNull(cameraHandler) { "Camera thread not started" }.asCoroutineDispatcher()

    /**
     * 在首次打开前完成相机发现，让调用方可以先确定最终镜头和倍率。
     */
    suspend fun prepareCameras(): Boolean = runOnCameraThread {
        if (_state.value.availableCameras.isNotEmpty()) {
            return@runOnCameraThread true
        }
        try {
            discoverCameras()
            if (_state.value.availableCameras.isEmpty()) {
                handleCameraOpenFailure(
                    cameraId = "",
                    errorCode = ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE,
                    message = "未发现可用摄像头",
                    error = IllegalStateException("Camera discovery returned no available camera"),
                )
                false
            } else {
                true
            }
        } catch (error: Exception) {
            handleCameraOpenFailure(
                cameraId = "",
                errorCode = ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE,
                message = "摄像头列表加载失败",
                error = error,
            )
            false
        }
    }

    /**
     * 在创建预览 Surface 前确定最终尺寸，避免首次打开过程中因尺寸变化重建 Surface。
     */
    suspend fun preparePreviewSize(): Boolean = runOnCameraThread {
        try {
            val state = _state.value
            val cameraId = state.getCurrentCameraInfo()?.getOpenCameraId()
                ?: state.currentCameraId
            if (cameraId.isEmpty()) {
                false
            } else {
                val characteristics = getCameraCharacteristicsCached(cameraId)
                val previewSize = when (state.captureMode) {
                    CaptureMode.PHOTO -> CameraUtils.getFixedPreviewSize(
                        characteristics,
                        state.aspectRatio,
                    )
                    CaptureMode.VIDEO -> {
                        availableTonemapModes = characteristics.get(
                            CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES
                        ) ?: intArrayOf()
                        availableVideoStabilizationModes = characteristics.get(
                            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
                        ) ?: intArrayOf()
                        availableOpticalStabilizationModes = characteristics.get(
                            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
                        ) ?: intArrayOf()
                        isFlashSupported = characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) ?: false
                        refreshVideoCapabilities(characteristics)
                    }
                }
                _state.update { it.copy(currentPreviewSize = previewSize) }
                true
            }
        } catch (error: Exception) {
            handleCameraOpenFailure(
                cameraId = _state.value.currentCameraId,
                errorCode = ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE,
                message = "相机特性不可用",
                error = error,
            )
            false
        }
    }

    private fun startBackgroundThread() {
        cameraThread = HandlerThread("CameraBackground").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopBackgroundThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            PLog.e(TAG, "Error stopping background thread", e)
        }
    }

    /**
     * 发现所有可用摄像头（包括隐藏摄像头）
     */
    private fun discoverCameras(preferredCameraId: String? = null) {
        val cameras = cameraDiscovery.discoverAllCameras()

        PLog.d(TAG, "Discovered ${cameras.size} cameras:")
        PLog.d(TAG, "发现 ${cameras.size} 个摄像头")
        cameras.forEach { cam ->
            PLog.d(
                TAG,
                "  - ${cam.cameraId}: ${cam.lensType}, intrinsicZoom=${cam.intrinsicZoomRatio}, " +
                        "displayZoom=${cam.displayIntrinsicZoomRatio}"
            )
            PLog.d(
                TAG,
                "摄像头: ${cam.cameraId}, 类型: ${cam.lensType}, 变焦: ${cam.displayIntrinsicZoomRatio}, " +
                        "提交倍率基准: ${cam.intrinsicZoomRatio}"
            )
        }

        // 默认选择主摄
        val defaultCamera = cameras.firstOrNull { it.cameraId == preferredCameraId }
            ?: cameras.firstOrNull { it.lensType == LensType.BACK_MAIN }
            ?: cameras.firstOrNull { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
            ?: cameras.firstOrNull()

        PLog.i(TAG, "选择默认摄像头: ${defaultCamera?.cameraId}, 类型: ${defaultCamera?.lensType}")

        _state.update { s -> s.copy(
            availableCameras = cameras,
            currentCameraId = defaultCamera?.cameraId ?: "",
            currentLensType = defaultCamera?.lensType ?: LensType.BACK_MAIN
        ) }
    }

    fun refreshCameraList() {
        PLog.i(TAG, "刷新摄像头列表")
        val currentCameraId = _state.value.currentCameraId
        cameraDiscovery.clearCache()
        discoverCameras(preferredCameraId = currentCameraId.takeIf { it.isNotEmpty() })
    }

    private fun getCurrentOpenCameraId(): String {
        val state = _state.value
        return state.getCurrentCameraInfo()?.getOpenCameraId() ?: state.currentCameraId
    }

    private fun getActiveOpenCameraId(): String {
        return activeOpenCameraId.takeIf { it.isNotEmpty() } ?: getCurrentOpenCameraId()
    }

    private fun getCameraCharacteristicsCached(cameraId: String): CameraCharacteristics {
        require(cameraId.isNotEmpty()) { "cameraId must not be empty" }
        cachedCharacteristics?.let { characteristics ->
            if (cachedCharacteristicsCameraId == cameraId) {
                return characteristics
            }
        }
        cameraCharacteristicsCache[cameraId]?.let { return it }
        return cameraManager.getCameraCharacteristics(cameraId).also {
            it.associateMetadataCameraId(cameraId)
            cameraCharacteristicsCache[cameraId] = it
        }
    }

    private fun getCameraCharacteristicsOrNull(cameraId: String, reason: String): CameraCharacteristics? {
        if (cameraId.isEmpty()) return null
        return try {
            getCameraCharacteristicsCached(cameraId)
        } catch (e: Exception) {
            PLog.v(TAG, "Failed to load characteristics for camera $cameraId during $reason: ${e.message}")
            null
        }
    }

    private fun cacheActiveCameraCharacteristics(
        cameraId: String,
        characteristics: CameraCharacteristics
    ) {
        cachedCharacteristics = characteristics
        cachedCharacteristicsCameraId = cameraId
        cameraCharacteristicsCache[cameraId] = characteristics
    }

    private fun getActiveOpenCameraCharacteristics(): CameraCharacteristics? {
        val openCameraId = getActiveOpenCameraId()
        return getCameraCharacteristicsOrNull(openCameraId, "active open camera")
    }

    private fun clearCameraCapabilityCache() {
        _enginePreviewSource.value = null
        enginePreviewCharacteristics = null
        enginePreviewCalibration = null
        enginePreviewCalibrationMode = null
        cachedCharacteristics = null
        cachedCharacteristicsCameraId = ""
        activeOpenCameraId = ""
        activeOutputPhysicalCameraId = null
        cachedSensorOrientation = 0
        cachedLensFacing = CameraCharacteristics.LENS_FACING_BACK
        cachedHardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        isManualSensorSupported = false
        isManualPostProcessingSupported = false
        isFlashSupported = false
        maxAfRegions = 0
        maxAeRegions = 0
        availableAfModes = intArrayOf()
        availableAeModes = intArrayOf()
        availableAwbModes = intArrayOf()
        availableEdgeModes = intArrayOf()
        availableNoiseReductionModes = intArrayOf()
        availableTonemapModes = intArrayOf()
        availableColorCorrectionAberrationModes = intArrayOf()
        availableHotPixelModes = intArrayOf()
        availableShadingModes = intArrayOf()
        availableDistortionCorrectionModes = intArrayOf()
        availableVideoStabilizationModes = intArrayOf()
        availableOpticalStabilizationModes = intArrayOf()
        availableLensShadingMapModes = intArrayOf()
        availableFaceDetectModes = intArrayOf()
        maxFaceCount = 0
        faceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        sonyFeatures = SonySubjectFeatures()
        resetSubjectState()
        resetHintState(SonyHintFeatures())
        availableColorCorrectionModes = intArrayOf()
        awbColorTemperatureRange = null
        lastWhiteBalanceResult = null
        manualWhiteBalanceAnchor = null
        isRawSupported = false
        isP010Supported = false
        isStreamUseCaseSupported = false
        availableStreamUseCases = longArrayOf()
        lastAfState = null
        isZslControlSupported = null
        availableCaptureRequestKeyNames = null
    }

    private fun clearCameraSessionState(reason: String, closeImageReader: Boolean = true) {
        burstCapturing = false
        _state.update { it.copy(burstCapturing = false) }
        photoCaptures.values.toList().forEach {
            failPhotoCapture(it, "session cleared: $reason", drainQueue = false)
            disposeFailedPhotoCapture(it)
        }
        activePhotoCapture = null
        runFocusReleasesAfterCapture()
        pendingContinuousBurstCapture = null
        pendingImages.values.toList().forEach(SafeImage::close)
        pendingImages.clear()
        pendingResults.clear()
        pendingFrameMetadata.clear()
        pendingCaptureStartedTimestamps.clear()
        photoCaptureByTimestamp.clear()
        _state.update {
            it.copy(
                isCapturing = false,
                hdrBracketCapturing = false,
                hdrBracketFrameCount = 0,
                previewFirstFrameTimestampNs = null,
            )
        }
        previewSessionGeneration++
        previewUpdateScheduled.set(false)
        livePhotoVideoStartTimestampUs = null
        clearPrecaptureTracking()
        clearMultiFrameTorchWarmupTracking()
        isMultiFrameTorchCaptureActive = false
        isContinuousBurstTorchActive = false
        pendingCaptureDevice = null
        pendingCaptureReader = null
        pendingCaptureBaseExposureResult = null
        clearCaptureFocusState("session cleared: $reason")
        deferredPreview = null
        safeCloseCaptureSession(captureSession, reason)
        captureSession = null
        _enginePreviewSource.value = null
        previewRequestBuilder = null
        safeReleasePreviewSurface(reason)
        if (closeImageReader) {
            safeCloseImageReader(imageReader)
            imageReader = null
            safeCloseImageReader(rawCompanionJpegReader)
            rawCompanionJpegReader = null
            closeStabilizationImageReader()
        }
    }

    private fun safeReleasePreviewSurface(reason: String) {
        val surface = previewSurface
        previewSurface = null
        previewSurfaceTexture = null
        try {
            surface?.release()
        } catch (e: Exception) {
            PLog.w(TAG, "Ignoring error while releasing preview surface ($reason): ${e.message}")
        }
    }

    private fun clearCameraRuntimeState(reason: String, closeImageReader: Boolean = true) {
        clearCameraSessionState(reason, closeImageReader)
        clearCameraCapabilityCache()
    }

    private fun setCameraInactive(resetVideoState: Boolean = true) {
        _state.update { s ->
            s.copy(
                isPreviewActive = false,
                isCapturing = false,
                actualAwbTemperature = null,
                actualAwbTint = null,
                actualAwbGains = null,
                canAdjustWhiteBalance = false,
                videoRecordingState = if (resetVideoState) VideoRecordingState() else s.videoRecordingState
            )
        }
    }

    private fun closeCameraDeviceSafely(camera: CameraDevice?, reason: String) {
        try {
            camera?.close()
        } catch (e: Exception) {
            PLog.w(TAG, "Ignoring exception while closing camera device ($reason): ${e.message}")
        }
    }

    private fun closeCameraDeviceAndAwait(camera: CameraDevice, reason: String) {
        if (cameraDevice === camera) {
            cameraDevice = null
        }
        cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSING
        closeCameraDeviceSafely(camera, reason)
    }

    private fun handleCameraDeviceClosed(camera: CameraDevice) {
        if (cameraDevice === camera) {
            cameraDevice = null
        }
        cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSED
        PLog.d(TAG, "Camera device closed: ${camera.id}")
        releaseSonyVideoMode()
        openPendingCameraRequest()
        if (cameraDeviceLifecycle == CameraDeviceLifecycle.CLOSED) {
            releaseCloseLatch?.countDown()
        }
    }

    private fun handleCameraDeviceUnavailable(
        camera: CameraDevice,
        reason: String,
        resetVideoState: Boolean = true
    ) {
        cameraOpenGeneration++
        videoRecorder.forceStop()
        stopVideoRecordingTicker()
        clearCameraSessionState(reason)
        closeCameraDeviceAndAwait(camera, reason)
        clearCameraCapabilityCache()
        livePhotoRecorder.stopRecording()
        setCameraInactive(resetVideoState)
    }

    private fun handleCameraOpenFailure(
        cameraId: String,
        errorCode: Int,
        message: String,
        error: Exception
    ) {
        cameraOpenGeneration++
        PLog.e(TAG, "Camera open failed for $cameraId: $message", error)
        val device = cameraDevice
        cameraDevice = null
        if (device != null) {
            closeCameraDeviceAndAwait(device, "open failure")
        } else {
            cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSED
        }
        clearCameraRuntimeState("open failure: $cameraId")
        livePhotoRecorder.stopRecording()
        setCameraInactive(resetVideoState = true)
        onCameraError?.invoke(errorCode, message, true)
    }

    private fun handlePreviewSessionFailure(
        reason: String,
        openGeneration: Long,
        error: Exception? = null
    ) {
        if (openGeneration != cameraOpenGeneration) {
            PLog.w(TAG, "Ignoring stale preview session failure: $reason")
            return
        }
        cameraOpenGeneration++
        if (error != null) {
            PLog.e(TAG, "Preview session failed: $reason", error)
        } else {
            PLog.e(TAG, "Preview session failed: $reason")
        }
        if (videoRecorder.isRecording()) {
            videoRecorder.forceStop()
            stopVideoRecordingTicker()
        }
        val device = cameraDevice
        if (device != null) {
            closeCameraDeviceAndAwait(device, "preview session failure: $reason")
        } else {
            cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSED
        }
        clearCameraRuntimeState("preview session failure: $reason")
        livePhotoRecorder.stopRecording()
        setCameraInactive(resetVideoState = true)
        onCameraError?.invoke(
            ERROR_CAMERA_SESSION_CONFIG_FAILED,
            "预览会话配置失败",
            true
        )
    }

    private fun resolveActiveFocusCharacteristics(
        fallbackCharacteristics: CameraCharacteristics? = null
    ): Pair<String, CameraCharacteristics>? {
        val state = _state.value
        val camera = state.getCurrentCameraInfo()
        val targetZoomRatioByMain = getTargetZoomRatioByMain(state, camera)
        val candidateIds = buildList {
            activeOutputPhysicalCameraId?.let(::add)
            camera?.getBoundPhysicalCameraId(targetZoomRatioByMain)?.let(::add)
            camera?.baseCameraId?.let(::add)
            camera?.cameraId?.takeUnless { camera.isVirtualIszLens }?.let(::add)
            activeOpenCameraId.takeIf { it.isNotEmpty() }?.let(::add)
            state.currentCameraId.takeIf { it.isNotEmpty() }?.let(::add)
        }.distinct()

        for (cameraId in candidateIds) {
            getCameraCharacteristicsOrNull(cameraId, "focus characteristics")?.let {
                return cameraId to it
            }
        }

        return (fallbackCharacteristics ?: getActiveOpenCameraCharacteristics())?.let {
            val fallbackId = activeOpenCameraId.takeIf { id -> id.isNotEmpty() }
                ?: state.currentCameraId
            fallbackId to it
        }
    }

    private fun refreshActiveFocusLimit() {
        val focusCharacteristics = resolveActiveFocusCharacteristics()?.second
        val minimumFocusDistance =
            focusCharacteristics?.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        if (focusCharacteristics != null) {
            maxAfRegions = focusCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            availableAfModes =
                focusCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        }
        _state.update { s -> s.copy(
            minimumFocusDistance = minimumFocusDistance,
            supportsPointAutoFocus = supportsPointAutoFocus(
                minimumFocusDistance = minimumFocusDistance,
                maxRegions = maxAfRegions,
                afModes = availableAfModes,
            ),
        ) }
    }

    private fun supportsPointAutoFocus(
        minimumFocusDistance: Float,
        maxRegions: Int,
        afModes: IntArray,
    ): Boolean {
        return minimumFocusDistance > 0f &&
            maxRegions > 0 &&
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)
    }

    private fun refreshVideoCapabilities(characteristics: CameraCharacteristics? = null): Size {
        val openCameraId = getCurrentOpenCameraId()
        if (openCameraId.isEmpty()) {
            return _state.value.currentPreviewSize
        }

        val resolvedCharacteristics = characteristics
            ?: getCameraCharacteristicsOrNull(openCameraId, "video capabilities")
        if (resolvedCharacteristics == null) {
            PLog.e(TAG, "Failed to load video capabilities")
            return _state.value.currentPreviewSize
        }

        val requestedConfig = _state.value.videoConfig
        val sonyVideoModesHere =
            if (DeviceQuirks.isLilac && !isCurrentCameraFrontFacing() && !_state.value.slowMotion.enabled) sonyVideoModes
            else emptyList()
        val snapshot = VideoCapabilitiesResolver.resolve(
            characteristics = resolvedCharacteristics,
            requestedConfig = requestedConfig,
            algorithmicStabilizationSupported =
                realtimeStabilizationCoordinator.isCameraSupported(resolvedCharacteristics) &&
                    !isEnhancedStabilizationUnavailableForCurrentCamera(),
            sonyVideoModes = sonyVideoModesHere,
        )
        if (snapshot.config.logProfile != requestedConfig.logProfile) {
            PLog.w(
                TAG,
                "Video Log capability resolved: camera=$openCameraId, " +
                    "requested=${requestedConfig.logProfile.name}, resolved=${snapshot.config.logProfile.name}, " +
                    "available=${snapshot.capabilities.availableLogProfiles.map { it.name }}, " +
                    "lifecycle=$cameraDeviceLifecycle"
            )
        }

        if (_state.value.videoConfig.stabilizationMode == VideoStabilizationMode.ENHANCED) {
            val enhancedInput = snapshot.capabilities
                .enhancedStabilizationInputSizesByResolution[snapshot.config.resolution]
            if (snapshot.config.stabilizationMode == VideoStabilizationMode.ENHANCED) {
                PLog.i(
                    TAG,
                    "EIS+ capability accepted: resolution=${snapshot.config.resolution.displayName}, " +
                        "fps=${snapshot.config.fps.fps}, yuvInput=$enhancedInput"
                )
            } else {
                PLog.e(
                    TAG,
                    "EIS+ capability rejected: resolution=${snapshot.config.resolution.displayName}, " +
                        "fps=${snapshot.config.fps.fps}, yuvInput=$enhancedInput, " +
                        "reason=missing YUV output for the selected resolution"
                )
            }
        }

        // With Sony's stabilisers on offer the resolver already picked the fallback; the note line says which.
        val enhancedFallbackMode = if (
            sonyVideoModesHere.isEmpty() &&
            _state.value.videoConfig.stabilizationMode == VideoStabilizationMode.ENHANCED &&
            snapshot.config.stabilizationMode != VideoStabilizationMode.ENHANCED
        ) {
            resolvePreviousVideoStabilizationMode(
                snapshot.capabilities.availableStabilizationModes
            )
        } else {
            null
        }
        val resolvedConfig = enhancedFallbackMode?.let { fallbackMode ->
            snapshot.config.copy(stabilizationMode = fallbackMode)
        } ?: snapshot.config
        val stabiliseNotice = if (
            sonyVideoModesHere.isNotEmpty() && resolvedConfig.stabilizationMode != requestedConfig.stabilizationMode
        ) {
            StabiliseNotice(
                requestedConfig.stabilizationMode, resolvedConfig.stabilizationMode,
                resolvedConfig.resolution, resolvedConfig.fps.fps, failed = false, serial = ++stabiliseNoticeSerial,
            )
        } else {
            null
        }
        _state.update { s -> s.copy(
            videoConfig = resolvedConfig,
            videoCapabilities = snapshot.capabilities,
            stabiliseNotice = stabiliseNotice ?: s.stabiliseNotice,
            currentPreviewSize = if (_state.value.captureMode == CaptureMode.VIDEO) {
                snapshot.previewSize
            } else {
                _state.value.currentPreviewSize
            }
        ) }
        if (enhancedFallbackMode != null) {
            previousVideoStabilizationMode = enhancedFallbackMode
            notifyEnhancedStabilizationUnavailable(
                EnhancedStabilizationFallback.Video(enhancedFallbackMode)
            )
        }
        return snapshot.previewSize
    }

    private fun startVideoRecordingTicker() {
        stopVideoRecordingTicker()
        videoRecordingStartElapsedMs = SystemClock.elapsedRealtime()
        cameraHandler?.post(videoRecordingTicker)
    }

    private fun stopVideoRecordingTicker() {
        cameraHandler?.removeCallbacks(videoRecordingTicker)
    }

    // ==================== 相机控制 ====================

    /**
     * 打开相机并开始预览
     *
     * @param surfaceTexture SurfaceTexture 用于预览
     */
    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture, preserveVideoRecording: Boolean = false) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                openCamera(surfaceTexture, preserveVideoRecording)
            }
            return
        }
        if (
            surfacelessOpenGeneration == cameraOpenGeneration &&
            previewSurfaceTexture == null &&
            pendingCameraOpenRequest == null &&
            surfacelessOpenSettings == surfacelessOpenSettings()
        ) {
            attachPreviewSurface(surfaceTexture)
            return
        }
        pendingCameraOpenRequest = CameraOpenRequest(surfaceTexture, preserveVideoRecording)
        when (cameraDeviceLifecycle) {
            CameraDeviceLifecycle.CLOSED -> openPendingCameraRequest()
            CameraDeviceLifecycle.OPENING,
            CameraDeviceLifecycle.OPEN -> closeCameraInternal(
                preserveVideoRecording = preserveVideoRecording,
                clearPendingOpen = false,
            )
            CameraDeviceLifecycle.CLOSING -> {
                PLog.d(TAG, "Camera open queued while previous device is closing")
            }
        }
    }

    /**
     * Opens the camera in Photo mode before the preview surface exists, so opening and session
     * configuration overlap the UI start-up. [openCamera] later attaches the surface to this open.
     */
    fun openCameraWithoutPreview() {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { openCameraWithoutPreview() }
            return
        }
        if (
            _state.value.captureMode != CaptureMode.PHOTO ||
            cameraDeviceLifecycle != CameraDeviceLifecycle.CLOSED ||
            pendingCameraOpenRequest != null
        ) {
            return
        }
        pendingCameraOpenRequest = CameraOpenRequest(surfaceTexture = null, preserveVideoRecording = false)
        openPendingCameraRequest()
    }

    /** Closes a camera opened by [openCameraWithoutPreview] whose preview surface never arrived. */
    fun closeCameraWithoutPreview() {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { closeCameraWithoutPreview() }
            return
        }
        if (surfacelessOpenGeneration == cameraOpenGeneration && previewSurfaceTexture == null) {
            PLog.d(TAG, "Closing camera opened without a preview surface")
            closeCameraInternal(preserveVideoRecording = false, clearPendingOpen = true)
        }
    }

    // What the session of a camera opened without a surface was built from; if any of it changed
    // before the surface arrived, the camera reopens instead of reusing that session.
    private fun surfacelessOpenSettings(): List<Any?> = _state.value.let {
        listOf(
            it.currentCameraId, it.captureMode, it.aspectRatio, requestedRawCaptureEnabled,
            it.useP010, it.useP3ColorSpace, it.photoResolutionCapMp, it.activeFrontPhotoResolutionCapMp, it.frontStillJpeg,
            it.multiFrameOutputScale,
            it.photoPreviewStabilizationEnabled, it.useLivePhoto,
            it.vendorCaptureSettingsByLens, it.customVendorKeySettings,
        )
    }

    private fun attachPreviewSurface(surfaceTexture: SurfaceTexture) {
        surfaceTexture.setDefaultBufferSize(previewBufferSize.width, previewBufferSize.height)
        previewSurfaceTexture = surfaceTexture
        previewSurface = Surface(surfaceTexture)
        PLog.d(TAG, "Preview surface attached to the open camera: deferredSession=${deferredPreview != null}")
        // Still opening: onOpened creates the session with this surface.
        if (cameraDevice == null) return
        if (deferredPreview != null) {
            finalizeDeferredPreview()
        } else {
            createPreviewSession()
        }
    }

    private fun openPendingCameraRequest() {
        if (cameraDeviceLifecycle != CameraDeviceLifecycle.CLOSED) return
        val request = pendingCameraOpenRequest ?: return
        pendingCameraOpenRequest = null
        cameraDeviceLifecycle = CameraDeviceLifecycle.OPENING
        openCameraNow(request)
    }

    @SuppressLint("MissingPermission")
    private fun openCameraNow(request: CameraOpenRequest) {
        val surfaceTexture = request.surfaceTexture
        val preserveVideoRecording = request.preserveVideoRecording
        clearPointFocusState()
        val openGeneration = ++cameraOpenGeneration

        // 确保在权限已授予后才发现相机（延迟初始化）
        if (_state.value.availableCameras.isEmpty()) {
            PLog.i(TAG, "首次打开相机，开始发现可用摄像头")
            try {
                discoverCameras()
            } catch (error: Exception) {
                handleCameraOpenFailure(
                    cameraId = "",
                    errorCode = ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE,
                    message = "摄像头列表加载失败",
                    error = error
                )
                return
            }
        }

        val cameraId = _state.value.currentCameraId
        val selectedCamera = _state.value.getCurrentCameraInfo()
        val openCameraId = selectedCamera?.getOpenCameraId() ?: cameraId
        val targetZoomRatioByMain = getTargetZoomRatioByMain(_state.value, selectedCamera)
        val captureMode = _state.value.captureMode
        if (cameraId.isEmpty()) {
            handleCameraOpenFailure(
                cameraId = cameraId,
                errorCode = ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE,
                message = "未发现可用摄像头",
                error = IllegalStateException("Camera discovery returned no available camera")
            )
            return
        }

        activeOpenCameraId = openCameraId
        val requestedPhysicalCameraId = resolveRequestedPhysicalCameraId(_state.value, selectedCamera)
        var outputPhysicalCameraId = requestedPhysicalCameraId
        activeOutputPhysicalCameraId = outputPhysicalCameraId

        PLog.i(
            TAG,
            "Opening camera: selected=$cameraId, open=$openCameraId, targetZoom=$targetZoomRatioByMain, " +
                    "physicalOutput=$outputPhysicalCameraId, " +
                    "mode: ${captureMode.name}"
        )

        var previewSize = _state.value.currentPreviewSize
        availableCaptureRequestKeyNames = null

        try {
            try {
                val openCharacteristics = getCameraCharacteristicsCached(openCameraId)
                cacheActiveCameraCharacteristics(openCameraId, openCharacteristics)
                availableCaptureRequestKeyNames = loadAvailableCaptureRequestKeyNames(openCharacteristics)

                // 缓存固定属性（传感器方向、镜头朝向、硬件级别）
                // 这些值在相机生命周期内不会改变，避免在每帧预览中重复获取
                cachedSensorOrientation = openCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                cachedLensFacing = openCharacteristics.get(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_BACK
                cachedHardwareLevel = openCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED

                val hardwareLevelName = when (cachedHardwareLevel) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                    else -> "UNKNOWN($cachedHardwareLevel)"
                }

                // 更新硬件能力缓存
                val capabilities =
                    openCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isStreamUseCaseSupported = capabilities.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE
                    )
                    availableStreamUseCases = openCharacteristics.get(
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES
                    ) ?: longArrayOf()
                } else {
                    isStreamUseCaseSupported = false
                    availableStreamUseCases = longArrayOf()
                }
                var capabilityCameraId = outputPhysicalCameraId ?: openCameraId
                val capabilityCharacteristics = if (capabilityCameraId == openCameraId) {
                    openCharacteristics
                } else {
                    getCameraCharacteristicsOrNull(
                        capabilityCameraId,
                        "physical output capability preload"
                    ) ?: run {
                        PLog.w(
                            TAG,
                            "Physical output camera $capabilityCameraId characteristics unavailable; " +
                                    "falling back to open camera $openCameraId"
                        )
                        outputPhysicalCameraId = null
                        activeOutputPhysicalCameraId = null
                        capabilityCameraId = openCameraId
                        openCharacteristics
                    }
                }
                realtimeStabilizationCoordinator.configureCamera(
                    cameraId = capabilityCameraId,
                    characteristics = capabilityCharacteristics,
                    timingCharacteristics = openCharacteristics,
                )
                if (_state.value.captureMode == CaptureMode.PHOTO &&
                    _state.value.photoPreviewStabilizationEnabled &&
                    !realtimeStabilizationCoordinator.isCurrentCameraSupported
                ) {
                    PLog.e(
                        TAG,
                        "Disabling photo preview stabilization: current camera is unsupported"
                    )
                    _state.update { s -> s.copy(
                        photoPreviewStabilizationEnabled = false
                    ) }
                    notifyEnhancedStabilizationUnavailable(
                        EnhancedStabilizationFallback.PhotoPreview
                    )
                }
                isManualSensorSupported =
                    capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) &&
                        openCharacteristics.readMetadataOrNull(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) != null &&
                        openCharacteristics.readMetadataOrNull(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) != null
                isManualPostProcessingSupported =
                    capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
                isFlashSupported = openCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                maxAfRegions = openCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
                maxAeRegions = openCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
                val maxAwbRegions = openCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0
                availableAfModes =
                    openCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                availableEdgeModes =
                    openCharacteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: intArrayOf()
                availableNoiseReductionModes =
                    openCharacteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                        ?: intArrayOf()
                availableTonemapModes =
                    openCharacteristics.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
                availableColorCorrectionAberrationModes =
                    openCharacteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
                        ?: intArrayOf()
                availableHotPixelModes =
                    openCharacteristics.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
                        ?: intArrayOf()
                availableShadingModes =
                    openCharacteristics.get(CameraCharacteristics.SHADING_AVAILABLE_MODES) ?: intArrayOf()
                availableDistortionCorrectionModes =
                    openCharacteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
                        ?: intArrayOf()
                availableVideoStabilizationModes =
                    openCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                        ?: intArrayOf()
                availableOpticalStabilizationModes =
                    openCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                        ?: intArrayOf()
                availableLensShadingMapModes =
                    openCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
                        ?: intArrayOf()
                availableFaceDetectModes =
                    openCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                        ?: intArrayOf()
                maxFaceCount =
                    openCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0
                faceDetectMode = resolveFaceDetectMode(availableFaceDetectModes, maxFaceCount)
                sonyFeatures = SonySubjectFeatures.of(
                    openCharacteristics,
                    faceDetection = faceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF,
                )
                resetSubjectState()
                Log.i(SonySubjectKeys.LOG_TAG, "camera=$capabilityCameraId $sonyFeatures")
                resetHintState(SonyHintFeatures.of(
                    openCharacteristics,
                    faceDetection = faceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF,
                ))
                Log.i(SonyHintKeys.LOG_TAG, "camera=$capabilityCameraId $hintFeatures")
                availableColorCorrectionModes = loadAvailableColorCorrectionModes(openCharacteristics)
                awbColorTemperatureRange = loadAwbColorTemperatureRange(openCharacteristics)
                lastWhiteBalanceResult = null
                isRawSupported = resolveRawCaptureOutput(capabilityCharacteristics, outputPhysicalCameraId) != null

                availableAeModes =
                    openCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
                availableAwbModes =
                    openCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()

                PLog.i(
                    TAG,
                    "Flash capabilities: available=$isFlashSupported, " +
                            "aeModes=${availableAeModes.joinToString()}, " +
                            "alwaysFlash=${availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)}, " +
                            "aeOn=${availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON)}, " +
                            "aeOff=${availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)}"
                )
                PLog.i(
                    TAG,
                    "Face detection: available=${availableFaceDetectModes.joinToString()}, " +
                            "maxFaces=$maxFaceCount, selected=${faceDetectModeName(faceDetectMode)}"
                )

                isP010Supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        isOutputFormatAdvertised(capabilityCharacteristics, ImageFormat.YCBCR_P010) &&
                        (outputPhysicalCameraId?.let {
                            !isPhysicalOutputProfileFailed(it, ImageFormat.YCBCR_P010)
                        } ?: true)
                val resolvedVideoPreviewSize = refreshVideoCapabilities(openCharacteristics)
                // In every mode: connecting also tells the bridge Lilac is here, so it offers Lilac to other apps.
                checkSlowMotionAvailable()
                previewSize = when (captureMode) {
                    CaptureMode.VIDEO -> resolvedVideoPreviewSize
                    CaptureMode.PHOTO -> CameraUtils.getFixedPreviewSize(openCharacteristics, _state.value.aspectRatio)
                }
                surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)

                if (_state.value.useLivePhoto && captureMode == CaptureMode.PHOTO) {
                    livePhotoRecorder.startRecording()
                }

                PLog.i(
                    TAG, "Camera characteristics cached - selected=$cameraId, open=$openCameraId, " +
                            "capability=$capabilityCameraId, Level: $hardwareLevelName, " +
                            "ManualSensor: $isManualSensorSupported, ManualPost: $isManualPostProcessingSupported, " +
                            "RAW: $isRawSupported, P010: $isP010Supported, " +
                            "StreamUseCase: $isStreamUseCaseSupported " +
                            "[${availableStreamUseCases.joinToString()}], " +
                            "MaxRegions(AF/AE/AWB): $maxAfRegions/$maxAeRegions/$maxAwbRegions, " +
                            "AF modes: ${availableAfModes.joinToString()}, " +
                            "AWB modes: ${availableAwbModes.joinToString()}, " +
                            "ColorCorrection modes: ${availableColorCorrectionModes.joinToString()}, " +
                            "CCT range: ${awbColorTemperatureRange}"
                )

                val selectableNrModes = buildSelectableNoiseReductionModes(availableNoiseReductionModes)

                val focusCharacteristics = resolveActiveFocusCharacteristics(openCharacteristics)?.second
                    ?: openCharacteristics
                val minimumFocusDistance =
                    focusCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
                maxAfRegions =
                    focusCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
                availableAfModes =
                    focusCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                val supportsPointAutoFocus = supportsPointAutoFocus(
                    minimumFocusDistance = minimumFocusDistance,
                    maxRegions = maxAfRegions,
                    afModes = availableAfModes,
                )

                // A camera without manual exposure (such as a front camera) always meters on its own. A manual
                // ISO or shutter left from another camera would otherwise freeze its readings and grey out EV.
                val supportsManualExposure = isManualSensorSupported &&
                    availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
                _state.update { currentState ->
                    currentState.copy(
                        useRaw = requestedRawCaptureEnabled,
                        isRawSupported = isRawSupported,
                        isManualSensorSupported = isManualSensorSupported,
                        isIsoAuto = currentState.isIsoAuto || !supportsManualExposure,
                        isShutterSpeedAuto = currentState.isShutterSpeedAuto || !supportsManualExposure,
                        exposureBias = if (supportsManualExposure) currentState.exposureBias
                        else currentState.exposureCompensation * currentState.getExposureCompensationStep(),
                        isP010Supported = isP010Supported,
                        availableNrModes = selectableNrModes,
                        supportsCctWhiteBalance = supportsCctWhiteBalance(),
                        canAdjustWhiteBalance = false,
                        actualAwbTemperature = null,
                        actualAwbTint = null,
                        actualAwbGains = null,
                        awbTemperatureMin = resolveAwbTemperatureRange().lower,
                        awbTemperatureMax = resolveAwbTemperatureRange().upper,
                        currentPreviewSize = previewSize,
                        currentCaptureSize = if (captureMode == CaptureMode.VIDEO) {
                            previewSize
                        } else {
                            currentState.currentCaptureSize
                        },
                        minimumFocusDistance = minimumFocusDistance,
                        supportsPointAutoFocus = supportsPointAutoFocus,
                        supportsPointMetering = maxAeRegions > 0,
                    )
                }
                refreshHyperfocalFocusDistanceIfEnabled(updatePreview = false)
                applyRetainedCaptureSettings()
            } catch (e: Exception) {
                handleCameraOpenFailure(
                    cameraId = openCameraId,
                    errorCode = ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE,
                    message = "相机特性不可用",
                    error = e
                )
                surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
                return
            }

            // 配置 SurfaceTexture
            previewSurfaceTexture = surfaceTexture
            previewSurface = surfaceTexture?.let(::Surface)

            if (captureMode == CaptureMode.PHOTO) {
                val aspectRatio = state.value.aspectRatio
                val effectivelyUseRaw = requestedRawCaptureEnabled && isRawSupported
                val openCharacteristics = getCameraCharacteristicsCached(openCameraId)
                var outputCameraIdForStreams = outputPhysicalCameraId ?: openCameraId
                var outputCharacteristicsForStreams = if (outputCameraIdForStreams == openCameraId) {
                    openCharacteristics
                } else {
                    getCameraCharacteristicsCached(outputCameraIdForStreams)
                }
                var rawOutput = if (effectivelyUseRaw) {
                    resolveRawCaptureOutput(outputCharacteristicsForStreams, outputPhysicalCameraId)
                } else {
                    null
                }
                val wantsP010 = !effectivelyUseRaw &&
                        isP010Supported &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        state.value.useP010
                var captureFormat = if (rawOutput != null) {
                    rawOutput.format
                } else if (wantsP010 && isOutputFormatAdvertised(outputCharacteristicsForStreams, ImageFormat.YCBCR_P010)) {
                    ImageFormat.YCBCR_P010
                } else {
                    ImageFormat.YUV_420_888
                }
                if (outputPhysicalCameraId != null && isPhysicalOutputProfileFailed(outputPhysicalCameraId, captureFormat)) {
                    val failedPhysicalCameraId = outputPhysicalCameraId
                    PLog.w(
                        TAG,
                        "Skipping previously failed physical output profile: " +
                                "physicalCameraId=$failedPhysicalCameraId, format=${imageFormatToString(captureFormat)}. " +
                                "Falling back to logical output for this profile."
                    )
                    outputPhysicalCameraId = null
                    activeOutputPhysicalCameraId = null
                    outputCameraIdForStreams = openCameraId
                    outputCharacteristicsForStreams = openCharacteristics
                    rawOutput = null
                    captureFormat = ImageFormat.YUV_420_888
                }
                previewSize = CameraUtils.getFixedPreviewSize(outputCharacteristicsForStreams, aspectRatio)
                surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
                previewBufferSize = previewSize
                val highResolutionProfile = "$outputCameraIdForStreams:$captureFormat"
                val captureSizeSelection = if (rawOutput != null) {
                    null
                } else {
                    CameraUtils.selectCaptureSize(
                        outputCharacteristicsForStreams,
                        aspectRatio,
                        captureFormat,
                        includeHighResolution = highResolutionProfile !in failedHighResolutionCaptureProfiles,
                        maxMegapixels = if (
                            outputCharacteristicsForStreams.get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_FRONT
                        ) {
                            _state.value.activeFrontPhotoResolutionCapMp
                        } else {
                            _state.value.photoResolutionCapMp
                        },
                    )
                }
                val captureSize = rawOutput?.size ?: captureSizeSelection!!.size
                activeHighResolutionCaptureProfile =
                    highResolutionProfile.takeIf { captureSizeSelection?.isHighResolution == true }
                captureSizeSelection?.let { selection ->
                    PLog.i(
                        TAG,
                        "Capture size candidates: selected=${selection.size.width}x${selection.size.height}, " +
                                "highResolution=${selection.isHighResolution}, " +
                                "maxStandard=${selection.maxStandardSize?.let { "${it.width}x${it.height}" }}, " +
                                "maxHighResolution=${selection.maxHighResolutionSize?.let { "${it.width}x${it.height}" }}, " +
                                "highResolutionDisabled=${highResolutionProfile in failedHighResolutionCaptureProfiles}"
                    )
                    val maxPixels = listOfNotNull(selection.maxStandardSize, selection.maxHighResolutionSize)
                        .maxOfOrNull { it.width.toLong() * it.height } ?: 0L
                    // Same 5% slack as the cap itself: at or below 8 MP, Full, 12 MP and 8 MP all pick one size.
                    _state.update { it.copy(photoResolutionAdjustable = maxPixels > 8 * 1_050_000L) }
                }

                val isP3Supported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    outputCharacteristicsForStreams.get(CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)
                        ?.getSupportedColorSpaces(captureFormat)
                        ?.contains(ColorSpace.Named.DISPLAY_P3) == true
                } else false

                _state.update { s -> s.copy(
                    isP3Supported = isP3Supported,
                    currentCaptureSize = captureSize
                ) }

                val readerMaxImages = resolveImageReaderMaxImages()
                imageReaderMaxImages = readerMaxImages

                PLog.d(
                    TAG,
                    "拍照尺寸: ${captureSize.width}x${captureSize.height}, 预览尺寸: ${previewSize.width}x${previewSize.height}, 格式: ${
                        when (captureFormat) {
                            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
                            ImageFormat.RAW10 -> "RAW10"
                            ImageFormat.YCBCR_P010 -> "P010"
                            else -> "YUV"
                        }
                    }, stream=$outputCameraIdForStreams, isP3Supported: $isP3Supported, imageReaderMaxImages: $readerMaxImages"
                )
                imageReader = ImageReader.newInstance(
                    captureSize.width,
                    captureSize.height,
                    captureFormat,
                    readerMaxImages
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        try {
                            while (canAcquireImage("Too many open images")) {
                                val image = trackImage(reader.acquireNextImage()) ?: break
                                processOrBufferImageForCaptureResult(image)
                            }
                        } catch (e: Exception) {
                            PLog.e(TAG, "Error in onImageAvailable", e)
                            photoCaptures.values.filter { it.reader === reader }.toList()
                                .forEach { failPhotoCapture(it, "image acquisition failed") }
                        }
                    }, cameraHandler)
                }
                safeCloseImageReader(rawCompanionJpegReader)
                rawCompanionJpegReader = if (captureFormat == ImageFormat.RAW_SENSOR || captureFormat == ImageFormat.RAW10) {
                    // shortcut: assumes JPEG is offered at the RAW size (true on lilac); check the stream map before upstreaming
                    ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2).apply {
                        // drain anything that arrives so the stream can never stall the camera,
                        // and drop the freed buffer so the discarded JPEG holds no memory between shots
                        setOnImageAvailableListener({ reader ->
                            // Next, not latest: a burst's first frame is the one kept.
                            while (true) {
                                val image = reader.acquireNextImage() ?: break
                                val bytes = try {
                                    takeRawCompanionJpeg(image.timestamp)?.let { shotId ->
                                        val buffer = image.planes[0].buffer
                                        shotId to ByteArray(buffer.remaining()).also(buffer::get)
                                    }
                                } catch (e: Exception) {
                                    PLog.e(TAG, "Cannot copy the camera JPEG", e)
                                    null
                                } finally {
                                    image.close()
                                }
                                bytes?.let { (shotId, jpeg) -> onRawCompanionJpeg?.invoke(shotId, jpeg) }
                            }
                            reader.discardFreeBuffers()
                        }, cameraHandler)
                    }
                } else frontStillJpegReader(captureFormat, outputCharacteristicsForStreams)
                frontStillJpegActive = rawCompanionJpegReader != null &&
                    captureFormat != ImageFormat.RAW_SENSOR && captureFormat != ImageFormat.RAW10

            } else {
                safeCloseImageReader(imageReader)
                imageReader = null
                safeCloseImageReader(rawCompanionJpegReader)
                rawCompanionJpegReader = null
                frontStillJpegActive = false
                activeHighResolutionCaptureProfile = null
                _state.update { s -> s.copy(isP3Supported = false) }
            }

            if (surfaceTexture == null) {
                surfacelessOpenGeneration = openGeneration
                surfacelessOpenSettings = surfacelessOpenSettings()
            }
            prepareSonyVideoMode()
            PLog.d(TAG, "Opening camera: open=$openCameraId, selected=$cameraId, previewSurface=${surfaceTexture != null}")

            cameraManager.openCamera(openCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (
                        openGeneration != cameraOpenGeneration ||
                        cameraDeviceLifecycle != CameraDeviceLifecycle.OPENING
                    ) {
                        PLog.w(TAG, "Ignoring stale camera open callback: camera=${camera.id}")
                        closeCameraDeviceAndAwait(camera, "stale camera open")
                        return
                    }
                    PLog.d(TAG, "Camera opened: ${camera.id}")
                    cameraDeviceLifecycle = CameraDeviceLifecycle.OPEN
                    cameraDevice = camera
                    if (restartCameraForRawCaptureOutputMismatch("camera opened")) {
                        return
                    }
                    createPreviewSession(openGeneration = openGeneration)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    if (openGeneration != cameraOpenGeneration) {
                        closeCameraDeviceAndAwait(camera, "stale camera disconnect")
                        return
                    }
                    PLog.w(TAG, "Camera disconnected: ${camera.id} - 相机被其他应用或系统接管")
                    handleCameraDeviceUnavailable(
                        camera = camera,
                        reason = "camera disconnected: ${camera.id}"
                    )

                    // 通知上层：相机断开连接，可以在 onResume 时重试
                    onCameraError?.invoke(
                        ERROR_CAMERA_DISCONNECTED,
                        "相机已被其他应用或系统接管",
                        true  // canRetry = true
                    )
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    if (openGeneration != cameraOpenGeneration) {
                        closeCameraDeviceAndAwait(camera, "stale camera error")
                        return
                    }
                    val errorMessage = when (error) {
                        ERROR_CAMERA_IN_USE ->
                            "相机正在被其他应用使用"

                        ERROR_MAX_CAMERAS_IN_USE ->
                            "已达到相机最大打开数量"

                        ERROR_CAMERA_DISABLED ->
                            "相机被系统策略禁用"

                        ERROR_CAMERA_DEVICE ->
                            "相机设备遇到严重错误"

                        ERROR_CAMERA_SERVICE ->
                            "相机服务遇到严重错误"

                        else -> "未知相机错误 ($error)"
                    }

                    PLog.e(TAG, "Camera error: ${camera.id}, error=$error - $errorMessage")
                    if (error == ERROR_CAMERA_DEVICE) {
                        // only while a RAW shot is in flight: preview-only error 4s (e.g. turning on preview stabilization in Pro mode) leave the shim healthy
                        activePhotoCapture?.let { markRawHalPoisoned(it, "camera error 4 during capture") }
                    }
                    handleCameraDeviceUnavailable(
                        camera = camera,
                        reason = "camera error ${camera.id}: $error"
                    )

                    // 判断是否可以重试
                    val canRetry = when (error) {
                        ERROR_CAMERA_IN_USE,
                        ERROR_MAX_CAMERAS_IN_USE -> true

                        ERROR_CAMERA_DISABLED,
                        ERROR_CAMERA_DEVICE,
                        ERROR_CAMERA_SERVICE -> false

                        else -> false
                    }

                    // 通知上层
                    onCameraError?.invoke(error, errorMessage, canRetry)
                    _state.update { s -> s.copy(isCapturing = false) }
                }

                override fun onClosed(camera: CameraDevice) {
                    handleCameraDeviceClosed(camera)
                }
            }, cameraHandler)

        } catch (e: Exception) {
            handleCameraOpenFailure(
                cameraId = openCameraId,
                errorCode = ERROR_CAMERA_OPEN_FAILED,
                message = "相机打开失败",
                error = e
            )
        }
    }

    fun updateHistogram(histogram: IntArray) {
        _state.update { s -> s.copy(histogram = histogram) }
    }

    fun calculateAutoMetering(totalWeight: Double, weightedSumLuminance: Double) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { calculateAutoMetering(totalWeight, weightedSumLuminance) }
            return
        }
        val currentState = _state.value
        if (!currentState.isAutoExposure && (currentState.isIsoAuto || currentState.isShutterSpeedAuto)) {

            // --- 1. 计算亮度 ---
            val rawAvgLuminance = if (totalWeight > 0) weightedSumLuminance / totalWeight else 0.0

            // 保护：如果画面全黑，避免除以0或Log错误
            if (rawAvgLuminance < 1.0) return

            // --- 2. 按自动调整的参数选择测光域 ---
            val currentShutter = currentState.shutterSpeed
            val clampedPreviewTime = currentShutter.coerceAtMost(getMaxPreviewExposureTime(currentState))
            // 手动快门、自动 ISO：以实际预览亮度为目标。超过预览快门上限后，
            // 不能用拍摄快门放大测光亮度，否则会持续降低 ISO，反而压暗取景器。
            // 手动 ISO、自动快门：仍需换算拍摄亮度，才能在预览曝光不再变化时
            // 求出正确的长曝光快门，而不是一直增加到传感器上限。
            val exposureRatio = if (currentState.isShutterSpeedAuto) {
                currentShutter.toDouble() / clampedPreviewTime.toDouble()
            } else {
                1.0
            }
            val meteredLuminance = rawAvgLuminance * exposureRatio

            val targetLuminance = 128.0 // Target (Gamma Corrected 18% Gray)

            // --- 3. 计算 EV 误差 ---
            // 使用 Log2 计算差了多少档光圈 (Stops)
            // 这是一个更符合人眼和相机光学的度量方式
            val evErrorStops = ln(targetLuminance / meteredLuminance) / ln(2.0)

            // --- 4. 稳定性控制 (Deadband) ---
            // 如果误差在 +/- 0.3 EV (约 1/3 档) 以内，认为曝光准确，不调整
            // 这能极大减少画面“呼吸感”
            if (abs(evErrorStops) < 0.3) {
                return
            }

            // --- 5. 计算修正系数 (P控制 + 阻尼) ---
            // 阻尼系数 0.2 ~ 0.5 比较合适，太小收敛慢，太大容易震荡
            val damping = 0.3
            // 限制单次最大调整幅度，防止突变 (例如限制在 +/- 1 EV 内)
            val limitedEvError = evErrorStops.coerceIn(-1.0, 1.0)
            val correctionFactor = 2.0.pow(limitedEvError * damping)

            // --- 6. 应用调整 ---
            var newIso = currentState.iso
            var newShutter = currentState.shutterSpeed
            var needsUpdate = false

            if (currentState.isIsoAuto) {
                // 快门优先模式：快门固定，调 ISO
                val calculatedIso = (currentState.iso * correctionFactor).toInt()
                val range = currentState.getIsoRange()
                val clampedIso = calculatedIso.coerceIn(range.lower, range.upper)

                // 只有变化量超过一定阈值才应用（防止 ISO 在 100 和 101 之间跳动）
                if (abs(clampedIso - currentState.iso) > currentState.iso * 0.05) {
                    newIso = clampedIso
                    needsUpdate = true
                }
            } else {
                // ISO 优先模式：ISO 固定，调快门
                val calculatedShutter = (currentState.shutterSpeed * correctionFactor).toLong()
                val range = currentState.getManualShutterSpeedRange()
                val clampedShutter = calculatedShutter.coerceIn(range.lower, range.upper)

                if (abs(clampedShutter - currentState.shutterSpeed) > currentState.shutterSpeed * 0.05) {
                    newShutter = clampedShutter
                    needsUpdate = true
                }
            }

            // --- 7. 下发指令 ---
            if (needsUpdate) {
                PLog.d(
                    TAG,
                    "SEMI_AUTO_METERING isoAuto=${currentState.isIsoAuto} " +
                        "previewLuma=$rawAvgLuminance meteredLuma=$meteredLuminance " +
                        "exposureRatio=$exposureRatio evError=$evErrorStops " +
                        "previewShutterNs=$clampedPreviewTime " +
                        "captureShutterNs=$currentShutter->$newShutter " +
                        "iso=${currentState.iso}->$newIso",
                )
                // 更新状态
                _state.update { s -> s.copy(iso = newIso, shutterSpeed = newShutter) }

                // 关键修复：检查相机和会话是否仍然有效
                val device = cameraDevice
                val session = captureSession
                val builder = previewRequestBuilder

                if (device == null || session == null || builder == null) {
                    PLog.v(TAG, "calculateAutoMetering: camera not ready, skipping update")
                    return
                }

                try {
                    applyExposureSettings(builder, _state.value, false)
                    session.setRepeatingRequest(
                        builder.build(),
                        previewCallback,
                        cameraHandler
                    )
                } catch (e: CameraAccessException) {
                    PLog.e(TAG, "Failed to update exposure: ${e.message}")
                } catch (e: IllegalStateException) {
                    PLog.w(TAG, "Failed to update exposure - camera closed: ${e.message}")
                } catch (e: Exception) {
                    PLog.e(TAG, "Failed to update exposure: ${e.message}")
                }
            }
        }
    }

    private fun createPreviewSession(
        forceWithoutVendorSessionParameters: Boolean = false,
        openGeneration: Long = cameraOpenGeneration
    ) {
        val handler = cameraHandler ?: return
        if (Looper.myLooper() != handler.looper) {
            handler.post { createPreviewSession(forceWithoutVendorSessionParameters, openGeneration) }
            return
        }
        if (openGeneration != cameraOpenGeneration) {
            PLog.w(TAG, "Skipping stale preview session creation")
            return
        }
        val device = cameraDevice ?: return
        val surface = previewSurface
        val captureMode = _state.value.captureMode
        // Camera opened before the GL preview existed: configure the photo session now with a
        // deferred preview output and add the surface when it arrives.
        val deferPreview = surface == null && captureMode == CaptureMode.PHOTO && !deferredPreviewUnsupported
        if (surface == null && !deferPreview) return
        deferredPreview = null
        // createCaptureSession closes the previous session before onConfigured runs.
        // Stop exposing it while the replacement is being configured.
        invalidateCaptureSession(captureSession, "preview session replacement")
        val sonyMode = enteredSonyMode
        lastSonySessionMode = sonyMode.takeIf { captureMode == CaptureMode.VIDEO && surface != null && slowMotionSpec == null }
        if (captureMode == CaptureMode.VIDEO && slowMotionSpec != null && surface != null) {
            createSlowMotionSession(device, surface, handler, openGeneration)
            return
        }
        if (captureMode == CaptureMode.VIDEO && sonyMode != null && surface != null) {
            createSonyVideoSession(device, surface, handler, openGeneration)
            return
        }
        val reader = imageReader
        val stabilizationReader = ensureStabilizationImageReader()
        val useEnhancedStabilization = shouldUseVideoEnhancedStabilization()
        val videoSurface = if (
            captureMode == CaptureMode.VIDEO &&
            _state.value.videoRecordingState.shouldAttachCameraInput() &&
            !useEnhancedStabilization
        ) {
            videoRecorder.cameraInputSurface
        } else {
            null
        }
        val sessionGeneration = ++previewSessionGeneration
        pendingVendorSessionParameterRestart = false
        val templateType = if (
            captureMode == CaptureMode.VIDEO &&
            !useEnhancedStabilization
        ) {
            CameraDevice.TEMPLATE_RECORD
        } else {
            CameraDevice.TEMPLATE_PREVIEW
        }
        PLog.i(
            TAG,
            "Creating repeating request: " +
                "template=${if (templateType == CameraDevice.TEMPLATE_PREVIEW) "PREVIEW" else "RECORD"}, " +
                "captureMode=$captureMode, enhancedStabilization=$useEnhancedStabilization"
        )
        var vendorSessionParametersApplied = false

        val buildPreviewRequest = { target: Surface ->
            device.createCaptureRequest(templateType).apply {
                addTarget(target)
                stabilizationReader?.surface?.let(::addTarget)
                videoSurface?.let(::addTarget)

                // 应用所有相机参数（曝光、白平衡、闪光灯、变焦）
                applyBaseCameraSettings(this, isCapture = false)
            }
        }

        try {
            previewRequestBuilder = surface?.let(buildPreviewRequest)

            val surfaces = mutableListOf(surface)
            stabilizationReader?.surface?.let(surfaces::add)
            videoSurface?.let(surfaces::add)
            if (captureMode == CaptureMode.PHOTO) {
                val captureReader = reader ?: return
                surfaces += captureReader.surface
            }

            if (captureMode == CaptureMode.VIDEO) {
                val sessionOperatingMode = resolveVideoSessionOperatingMode()
                PLog.i(
                    TAG,
                    "Creating video session: operatingMode=0x${sessionOperatingMode.toString(16)}, " +
                        "oppoSuperStabilization=$oppoSuperStabilizationEnabled, " +
                        "lensFacing=${if (isCurrentCameraFrontFacing()) "front" else "rear"}"
                )
                val sessionConfig = SessionConfiguration(
                    sessionOperatingMode,
                    buildList {
                        add(createOutputConfiguration(surface))
                        stabilizationReader?.surface?.let { eisSurface ->
                            add(createOutputConfiguration(eisSurface))
                        }
                        videoSurface?.let { encoderSurface ->
                            add(createOutputConfiguration(encoderSurface))
                        }
                    },
                    Executor { handler.post(it) },
                    object : CameraCaptureSession.StateCallback() {
                        override fun onClosed(session: CameraCaptureSession) {
                            invalidateCaptureSession(session, "video session closed")
                        }

                        override fun onConfigured(session: CameraCaptureSession) {
                            if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
                                PLog.w(TAG, "Closing stale video preview session")
                                safeCloseCaptureSession(session, "stale video preview session")
                                return
                            }
                            closeUnusedStabilizationImageReader(stabilizationReader)
                            onSessionConfigured(session, openGeneration, sessionGeneration)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
                                safeCloseCaptureSession(session, "stale video configure failure")
                                return
                            }
                            PLog.e(TAG, "Video session configuration failed")
                            safeCloseCaptureSession(session, "video configure failure")
                            if (vendorSessionParametersApplied &&
                                retryPreviewSessionWithoutVendorSessionParameters(
                                    reason = "video configure failed",
                                    openGeneration = openGeneration
                                )
                            ) {
                                return
                            }
                            if (retryPreviewSessionWithoutPhysicalOutput("video configure failed", openGeneration)) {
                                return
                            }
                            handlePreviewSessionFailure("video configure failed", openGeneration)
                        }
                    }
                )
                vendorSessionParametersApplied = applyInitialSessionParameters(
                    sessionConfig = sessionConfig,
                    device = device,
                    templateType = templateType,
                    includeVendorSessionParameters = !forceWithoutVendorSessionParameters
                ).usedVendorParameters
                device.createCaptureSession(sessionConfig)
                return
            }

            // Android 9+ 使用 SessionConfiguration
            val readerFormat = reader?.imageFormat ?: ImageFormat.YUV_420_888
            if (_state.value.isJpgMaxHdrEnabled) burstGyroRecorder.start(cameraHandler)
            PLog.i(
                TAG,
                "Creating preview session: readerFormat=${imageFormatToString(readerFormat)}, " +
                        "isP010Supported=$isP010Supported"
            )
            val previewOutput = createOutputConfiguration(surface)
            val outputConfigs = buildList {
                add(previewOutput)
                stabilizationReader?.surface?.let { eisSurface ->
                    add(createOutputConfiguration(eisSurface))
                }
                reader?.surface?.let { captureSurface ->
                    add(createOutputConfiguration(captureSurface))
                }
                rawCompanionJpegReader?.surface?.let { add(createOutputConfiguration(it)) }
            }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                Executor { handler.post(it) },
                object : CameraCaptureSession.StateCallback() {
                    override fun onClosed(session: CameraCaptureSession) {
                        invalidateCaptureSession(session, "photo session closed")
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
                            PLog.w(TAG, "Closing stale preview session")
                            safeCloseCaptureSession(session, "stale preview session")
                            return
                        }
                        closeUnusedStabilizationImageReader(stabilizationReader)
                        if (deferPreview) {
                            val pending = deferredPreview
                            if (pending == null) {
                                safeCloseCaptureSession(session, "stale deferred preview session")
                                return
                            }
                            PLog.d(TAG, "Deferred preview session configured: surfaceReady=${previewSurface != null}")
                            pending.session = session
                            finalizeDeferredPreview()
                            return
                        }
                        onSessionConfigured(session, openGeneration, sessionGeneration)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
                            safeCloseCaptureSession(session, "stale configure failure")
                            return
                        }
                        PLog.e(
                            TAG,
                            "Session configuration failed: readerFormat=${imageFormatToString(readerFormat)}, " +
                                    "sessionColorSpace=${if (shouldUseP3ColorSpace()) "DISPLAY_P3" else "DEFAULT"}"
                        )
                        safeCloseCaptureSession(session, "photo configure failure")
                        if (deferPreview) {
                            fallBackFromDeferredPreview("configure failed", openGeneration)
                            return
                        }
                        if (vendorSessionParametersApplied &&
                            retryPreviewSessionWithoutVendorSessionParameters(
                                reason = "photo configure failed",
                                openGeneration = openGeneration
                            )
                        ) {
                            return
                        }
                        if (retryPhotoSessionWithoutHighResolutionCapture("photo configure failed", openGeneration)) {
                            return
                        }
                        if (retryPreviewSessionWithoutPhysicalOutput("photo configure failed", openGeneration)) {
                            return
                        }
                        handlePreviewSessionFailure("photo configure failed", openGeneration)
                    }
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (shouldUseP3ColorSpace()) {
                    sessionConfig.setColorSpace(ColorSpace.Named.DISPLAY_P3)
                }
            }
            vendorSessionParametersApplied = applyInitialSessionParameters(
                sessionConfig = sessionConfig,
                device = device,
                templateType = templateType,
                includeVendorSessionParameters = !forceWithoutVendorSessionParameters
            ).usedVendorParameters
            if (deferPreview) {
                deferredPreview = DeferredPreview(previewOutput, buildPreviewRequest, openGeneration, sessionGeneration)
                PLog.d(TAG, "Creating photo session with a deferred preview output: ${previewBufferSize.width}x${previewBufferSize.height}")
            }
            device.createCaptureSession(sessionConfig)
        } catch (e: IllegalStateException) {
            PLog.w(TAG, "Failed to create preview session", e)
            if (deferPreview) {
                fallBackFromDeferredPreview("create session illegal state: ${e.message}", openGeneration)
                return
            }
            if (vendorSessionParametersApplied &&
                retryPreviewSessionWithoutVendorSessionParameters(
                    reason = "create session illegal state: ${e.message}",
                    openGeneration = openGeneration
                )
            ) {
                return
            }
            if (retryPreviewSessionWithoutPhysicalOutput(
                    "create session illegal state: ${e.message}",
                    openGeneration
                )
            ) {
                return
            }
            handlePreviewSessionFailure("create session illegal state", openGeneration, e)
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to create preview session", e)
            if (deferPreview) {
                fallBackFromDeferredPreview("create session exception: ${e.message}", openGeneration)
                return
            }
            if (vendorSessionParametersApplied &&
                retryPreviewSessionWithoutVendorSessionParameters(
                    reason = "create session exception: ${e.message}",
                    openGeneration = openGeneration
                )
            ) {
                return
            }
            if (retryPreviewSessionWithoutPhysicalOutput(
                    "create session exception: ${e.message}",
                    openGeneration
                )
            ) {
                return
            }
            handlePreviewSessionFailure("create session exception", openGeneration, e)
        }
    }

    private fun ensureStabilizationImageReader(): StabilizationImageReader? {
        val handler = cameraHandler ?: return null
        val state = _state.value
        if (!shouldUseAlgorithmicStabilization()) return null
        val size = when (state.captureMode) {
            CaptureMode.PHOTO -> state.currentPreviewSize
            CaptureMode.VIDEO -> state.videoCapabilities
                .enhancedStabilizationInputSizesByResolution[state.videoConfig.resolution]
                ?: return null
        }
        if (size.width <= 0 || size.height <= 0) return null
        stabilizationImageReader?.let { existing ->
            if (existing.width == size.width && existing.height == size.height) {
                return existing
            }
            closeStabilizationImageReader()
        }
        return StabilizationImageReader(
            size.width,
            size.height,
            RealtimeStabilizationCoordinator.STABILIZATION_IMAGE_READER_MAX_IMAGES,
        ).also { reader ->
            reader.setOnImageAvailableListener(handler) { source ->
                while (true) {
                    val image = try {
                        source.acquireNextImage()
                    } catch (error: IllegalStateException) {
                        PLog.e(TAG, "Cannot acquire MGC YUV image", error)
                        null
                    } ?: break
                    logStabilizationInputStats(image)
                    realtimeStabilizationCoordinator.submitStabilizationImage(image)
                }
            }
            stabilizationImageReader = reader
            stabilizationInputStatsFirstTimestampNs = 0L
            stabilizationInputStatsFrames = 0
            PLog.i(TAG, "MGC YUV input configured: ${size.width}x${size.height}")
        }
    }

    private fun logStabilizationInputStats(image: StabilizationImageReader.AcquiredImage) {
        val timestampNs = image.timestampNs
        if (timestampNs <= 0L) return
        stabilizationInputFrameCount += 1L
        if (stabilizationInputStatsFirstTimestampNs == 0L) {
            stabilizationInputStatsFirstTimestampNs = timestampNs
            stabilizationInputStatsFrames = 1
            return
        }

        stabilizationInputStatsFrames += 1
        val elapsedNs = timestampNs - stabilizationInputStatsFirstTimestampNs
        if (elapsedNs < 1_000_000_000L) return
        val actualFps = if (stabilizationInputStatsFrames > 1 && elapsedNs > 0L) {
            (stabilizationInputStatsFrames - 1) * 1_000_000_000f / elapsedNs.toFloat()
        } else {
            0f
        }
        PLog.i(
            TAG,
            "EIS+ YUV input stats: requested=${_state.value.videoConfig.fps.fps}, " +
                "actual=${"%.1f".format(actualFps)}, " +
                "frames=$stabilizationInputStatsFrames, size=${image.width}x${image.height}"
        )
        stabilizationInputStatsFirstTimestampNs = timestampNs
        stabilizationInputStatsFrames = 1
    }

    private fun scheduleEnhancedStabilizationInputWatchdog(
        sessionGeneration: Long,
        baselineFrameCount: Long = stabilizationInputFrameCount,
    ) {
        val handler = cameraHandler ?: return
        handler.postDelayed({
            if (sessionGeneration != previewSessionGeneration ||
                !shouldUseAlgorithmicStabilization()
            ) {
                return@postDelayed
            }
            if (stabilizationInputFrameCount == baselineFrameCount) {
                disableAlgorithmicStabilizationForMissingInput(sessionGeneration)
                return@postDelayed
            }
            scheduleEnhancedStabilizationInputWatchdog(
                sessionGeneration = sessionGeneration,
                baselineFrameCount = stabilizationInputFrameCount,
            )
        }, EIS_PLUS_INPUT_WATCHDOG_MS)
    }

    private fun disableAlgorithmicStabilizationForMissingInput(sessionGeneration: Long) {
        if (sessionGeneration != previewSessionGeneration ||
            !shouldUseAlgorithmicStabilization()
        ) {
            return
        }
        handleEnhancedStabilizationUnavailable(
            reason = "YUV input produced no frames for ${EIS_PLUS_INPUT_WATCHDOG_MS}ms"
        )
    }

    private fun handleEnhancedStabilizationUnavailable(reason: String) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { handleEnhancedStabilizationUnavailable(reason) }
            return
        }

        val state = _state.value
        currentStabilizationCameraId()
            .takeIf { it.isNotEmpty() }
            ?.let(enhancedStabilizationUnavailableCameraIds::add)
        when {
            state.captureMode == CaptureMode.VIDEO &&
                state.videoConfig.stabilizationMode == VideoStabilizationMode.ENHANCED -> {
                val fallbackMode = resolvePreviousVideoStabilizationMode(
                    state.videoCapabilities.availableStabilizationModes
                )
                PLog.e(
                    TAG,
                    "Disabling EIS+: $reason at " +
                        "${state.videoConfig.resolution.displayName}@" +
                        "${state.videoConfig.fps.fps}; fallback=$fallbackMode"
                )
                previousVideoStabilizationMode = fallbackMode
                _state.update { s -> s.copy(
                    videoConfig = state.videoConfig.copy(stabilizationMode = fallbackMode)
                ) }
                refreshVideoCapabilities()
                previousVideoStabilizationMode =
                    _state.value.videoConfig.stabilizationMode
                notifyEnhancedStabilizationUnavailable(
                    EnhancedStabilizationFallback.Video(
                        _state.value.videoConfig.stabilizationMode
                    )
                )
                if (_state.value.videoRecordingState.isRecording) {
                    videoRecorder.forceStop()
                } else if (cameraDevice != null && previewSurface != null) {
                    createPreviewSession(openGeneration = cameraOpenGeneration)
                }
            }

            state.captureMode == CaptureMode.PHOTO &&
                state.photoPreviewStabilizationEnabled -> {
                PLog.e(TAG, "Disabling photo preview stabilization: $reason")
                _state.update { s -> s.copy(photoPreviewStabilizationEnabled = false) }
                refreshVideoCapabilities()
                notifyEnhancedStabilizationUnavailable(
                    EnhancedStabilizationFallback.PhotoPreview
                )
                if (cameraDevice != null && previewSurface != null) {
                    createPreviewSession(openGeneration = cameraOpenGeneration)
                }
            }

            else -> PLog.w(TAG, "Ignoring stale stabilization unavailable callback: $reason")
        }
    }

    private fun notifyEnhancedStabilizationUnavailable(
        fallback: EnhancedStabilizationFallback
    ) {
        try {
            onEnhancedStabilizationUnavailable?.invoke(fallback)
        } catch (error: RuntimeException) {
            PLog.w(TAG, "Stabilization unavailable callback failed: ${error.message}")
        }
    }

    private fun closeUnusedStabilizationImageReader(activeReader: StabilizationImageReader?) {
        if (activeReader != null) return
        closeStabilizationImageReader()
    }

    private fun closeStabilizationImageReader() {
        val reader = stabilizationImageReader ?: return
        stabilizationImageReader = null
        // Stop acquisition first, then release queued frames. Renderer leases retain both
        // their source image and its reader until the upload/sampling has finished.
        reader.close()
        realtimeStabilizationCoordinator.discardStabilizationImages(reader)
    }

    private fun retryPreviewSessionWithoutVendorSessionParameters(
        reason: String,
        openGeneration: Long
    ): Boolean {
        if (openGeneration != cameraOpenGeneration) return false
        PLog.w(TAG, "Retrying preview session without vendor session parameters: $reason")
        createPreviewSession(
            forceWithoutVendorSessionParameters = true,
            openGeneration = openGeneration
        )
        return true
    }

    private fun retryPhotoSessionWithoutHighResolutionCapture(
        reason: String,
        openGeneration: Long
    ): Boolean {
        val profile = activeHighResolutionCaptureProfile ?: return false
        val surfaceTexture = previewSurfaceTexture ?: return false
        val handler = cameraHandler ?: return false
        if (!failedHighResolutionCaptureProfiles.add(profile)) return false
        activeHighResolutionCaptureProfile = null
        PLog.w(TAG, "Retrying photo session without high-resolution capture size: profile=$profile, reason=$reason")
        // The standard-size capture needs a new ImageReader.
        return handler.post {
            if (openGeneration == cameraOpenGeneration) {
                openCamera(surfaceTexture)
            }
        }
    }

    private fun retryPreviewSessionWithoutPhysicalOutput(
        reason: String,
        openGeneration: Long
    ): Boolean {
        val failedPhysicalCameraId = activeOutputPhysicalCameraId ?: return false
        rememberPhysicalOutputProfileFailure(failedPhysicalCameraId, reason = reason)
        if (isRawCaptureReader(imageReader)) {
            val remainingRawOutput = getCameraCharacteristicsOrNull(failedPhysicalCameraId, "RAW output retry")
                ?.let { resolveRawCaptureOutput(it, failedPhysicalCameraId) }
            val surfaceTexture = previewSurfaceTexture
            val handler = cameraHandler
            if (remainingRawOutput != null && surfaceTexture != null && handler != null) {
                PLog.w(
                    TAG,
                    "Retrying physical RAW output: physicalCameraId=$failedPhysicalCameraId, " +
                            "format=${imageFormatToString(remainingRawOutput.format)}, reason=$reason"
                )
                // The other format needs its own ImageReader and advertised dimensions.
                return handler.post {
                    if (openGeneration == cameraOpenGeneration) {
                        openCamera(surfaceTexture)
                    }
                }
            }
        }
        PLog.w(
            TAG,
            "Retrying preview session without physical output binding: " +
                    "physicalCameraId=$failedPhysicalCameraId, " +
                    "format=${imageFormatToString(imageReader?.imageFormat ?: NO_IMAGE_READER_FORMAT)}, " +
                    "reason=$reason"
        )
        activeOutputPhysicalCameraId = null
        createPreviewSession(openGeneration = openGeneration)
        return true
    }

    /** A null [surface] makes the deferred preview output that [finalizeDeferredPreview] completes. */
    private fun createOutputConfiguration(surface: Surface?): OutputConfiguration {
        val config = surface?.let(::OutputConfiguration)
            ?: OutputConfiguration(previewBufferSize, SurfaceTexture::class.java)
        return config.apply {
            activeOutputPhysicalCameraId?.let { physicalCameraId ->
                setPhysicalCameraId(physicalCameraId)
                PLog.i(
                    TAG,
                    "OutputConfiguration bound to physicalCameraId=$physicalCameraId " +
                            "(openCameraId=$activeOpenCameraId)"
                )
            }
        }
    }

    private fun applyInitialSessionParameters(
        sessionConfig: SessionConfiguration,
        device: CameraDevice,
        templateType: Int,
        includeVendorSessionParameters: Boolean
    ): InitialSessionParametersResult {
        val state = _state.value
        val sessionKeys = try {
            getActiveOpenCameraCharacteristics()?.availableSessionKeys
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to query available session keys", e)
            null
        }

        val shouldApplyStandardSessionParameters =
            sessionKeys?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE) == true ||
                sessionKeys?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE) == true
        val vendorSessionValues = if (includeVendorSessionParameters) {
            forcedVendorSessionParameterValues(state)
        } else {
            emptyMap()
        }
        val customVendorSessionKeys = if (includeVendorSessionParameters) {
            customVendorSessionParameterKeys(state)
        } else {
            emptyList()
        }

        if (
            !shouldApplyStandardSessionParameters &&
            vendorSessionValues.isEmpty() &&
            customVendorSessionKeys.isEmpty()
        ) {
            return InitialSessionParametersResult(applied = false, usedVendorParameters = false)
        }

        try {
            val builder = device.createCaptureRequest(templateType)
            if (shouldApplyStandardSessionParameters) {
                applyStabilizationSettings(builder, state)
            }
            if (vendorSessionValues.isNotEmpty()) {
                applyVendorCaptureSettings(
                    builder = builder,
                    lensId = state.currentCameraId,
                    values = vendorSessionValues,
                    target = "session"
                )
            }
            applyCustomVendorKeys(
                builder = builder,
                lensId = state.currentCameraId,
                keys = customVendorSessionKeys,
                target = "session"
            )
            sessionConfig.setSessionParameters(builder.build())
            PLog.d(
                TAG,
                "Initial session parameters applied: standard=$shouldApplyStandardSessionParameters, " +
                        "vendorKeys=${vendorSessionValues.keys.map { it.requestKeyName }}, " +
                        "customVendorKeys=${customVendorSessionKeys.map { it.keyName }}"
            )
            return InitialSessionParametersResult(
                applied = true,
                usedVendorParameters =
                    vendorSessionValues.isNotEmpty() || customVendorSessionKeys.isNotEmpty()
            )
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to set initial session parameters", e)
            return InitialSessionParametersResult(applied = false, usedVendorParameters = false)
        }
    }

    private fun getTargetZoomRatioByMain(
        state: CameraState,
        camera: CameraInfo?
    ): Float {
        return camera?.let { it.intrinsicZoomRatio * state.zoomRatio } ?: state.zoomRatio
    }

    private fun resolveRequestedPhysicalCameraId(
        state: CameraState = _state.value,
        camera: CameraInfo? = state.getCurrentCameraInfo()
    ): String? {
        return camera
            ?.getBoundPhysicalCameraId(getTargetZoomRatioByMain(state, camera))
    }

    private fun resolveOutputPhysicalCameraId(
        state: CameraState = _state.value,
        camera: CameraInfo? = state.getCurrentCameraInfo()
    ): String? {
        if (state.videoConfig.shouldLockLens(
                captureMode = state.captureMode,
                isRecording = state.videoRecordingState.isRecording
            )
        ) {
            return activeOutputPhysicalCameraId
        }
        return resolveRequestedPhysicalCameraId(state, camera)
            ?.takeUnless { isPhysicalOutputProfileFailed(it) }
    }

    private fun physicalOutputFailureKey(
        physicalCameraId: String,
        readerFormat: Int = imageReader?.imageFormat ?: NO_IMAGE_READER_FORMAT
    ): PhysicalOutputFailureKey? {
        val openCameraId = activeOpenCameraId.takeIf { it.isNotEmpty() } ?: return null
        return PhysicalOutputFailureKey(
            openCameraId = openCameraId,
            physicalCameraId = physicalCameraId,
            captureMode = _state.value.captureMode,
            readerFormat = readerFormat
        )
    }

    private fun isPhysicalOutputProfileFailed(
        physicalCameraId: String,
        readerFormat: Int = imageReader?.imageFormat ?: NO_IMAGE_READER_FORMAT
    ): Boolean {
        return physicalOutputFailureKey(physicalCameraId, readerFormat)?.let { key ->
            failedPhysicalOutputProfiles.contains(key)
        } ?: false
    }

    private fun rememberPhysicalOutputProfileFailure(
        physicalCameraId: String,
        readerFormat: Int = imageReader?.imageFormat ?: NO_IMAGE_READER_FORMAT,
        reason: String
    ) {
        val key = physicalOutputFailureKey(physicalCameraId, readerFormat) ?: return
        if (failedPhysicalOutputProfiles.add(key)) {
            PLog.w(
                TAG,
                "Physical output profile disabled: open=${key.openCameraId}, " +
                        "physical=${key.physicalCameraId}, mode=${key.captureMode}, " +
                        "format=${imageFormatToString(key.readerFormat)}, reason=$reason"
            )
            updateCurrentOutputSupportAfterPhysicalFailure(key)
        }
    }

    private fun updateCurrentOutputSupportAfterPhysicalFailure(key: PhysicalOutputFailureKey) {
        val selectedPhysicalCameraId = resolveRequestedPhysicalCameraId()
        if (selectedPhysicalCameraId != key.physicalCameraId) return

        when (key.readerFormat) {
            ImageFormat.RAW_SENSOR, ImageFormat.RAW10 -> {
                isRawSupported = getCameraCharacteristicsOrNull(key.physicalCameraId, "RAW output support")
                    ?.let { resolveRawCaptureOutput(it, key.physicalCameraId) } != null
                _state.update { s -> s.copy(isRawSupported = isRawSupported) }
            }

            ImageFormat.YCBCR_P010 -> {
                isP010Supported = false
                _state.update { s -> s.copy(isP010Supported = false) }
            }
        }
    }

    private fun isOutputFormatAdvertised(
        characteristics: CameraCharacteristics,
        format: Int
    ): Boolean {
        val outputFormats = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.outputFormats
            ?: return false
        return outputFormats.contains(format)
    }

    private fun resolveRawCaptureOutput(
        characteristics: CameraCharacteristics,
        physicalCameraId: String?,
    ): CameraUtils.RawCaptureOutput? = CameraUtils.getRawCaptureOutput(characteristics) { format ->
        physicalCameraId == null || !isPhysicalOutputProfileFailed(physicalCameraId, format)
    }

    /** Runs once both the deferred photo session and the preview surface are ready. */
    private fun finalizeDeferredPreview() {
        val pending = deferredPreview ?: return
        val session = pending.session ?: return
        val surface = previewSurface ?: return
        deferredPreview = null
        try {
            pending.output.addSurface(surface)
            session.finalizeOutputConfigurations(listOf(pending.output))
            previewRequestBuilder = pending.buildRequest(surface)
        } catch (e: Exception) {
            PLog.w(TAG, "Deferred preview output could not be finalized", e)
            safeCloseCaptureSession(session, "deferred preview finalize failure")
            fallBackFromDeferredPreview("finalize failed: ${e.message}", pending.openGeneration)
            return
        }
        onSessionConfigured(session, pending.openGeneration, pending.sessionGeneration)
    }

    // Deferred outputs are tried once per process; afterwards the session is created the regular
    // way with the real surface, which has its own retries.
    private fun fallBackFromDeferredPreview(reason: String, openGeneration: Long) {
        PLog.w(TAG, "Deferred preview output unavailable ($reason); waiting for the preview surface")
        deferredPreviewUnsupported = true
        deferredPreview = null
        if (previewSurface != null) createPreviewSession(openGeneration = openGeneration)
    }

    private fun onSessionConfigured(
        session: CameraCaptureSession,
        openGeneration: Long = cameraOpenGeneration,
        sessionGeneration: Long = previewSessionGeneration
    ) {
        if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
            PLog.w(TAG, "Ignoring stale configured session")
            safeCloseCaptureSession(session, "stale configured session")
            return
        }
        captureSession = session
        _state.update { it.copy(previewFirstFrameTimestampNs = null) }

        try {
            // 根据测光模式设置默认 AE 区域
            applyMeteringRegions()

            // 开始预览
            // 关键修复: 不再动态添加 surface，因为已经在创建 builder 时添加了
            previewRequestBuilder?.let { builder ->
                session.setRepeatingRequest(builder.build(), previewCallback, cameraHandler)
            }
            onPreviewStreamActive?.invoke(true)
            if (shouldUseAlgorithmicStabilization()) {
                scheduleEnhancedStabilizationInputWatchdog(sessionGeneration)
            }
            if (_state.value.captureMode == CaptureMode.VIDEO &&
                _state.value.videoRecordingState.shouldAttachCameraInput() &&
                videoRecorder.cameraInputSurface != null
            ) {
                if (videoRecorder.onCameraInputStarted()) {
                    startVideoRecordingTicker()
                }
            }

            _state.update { s -> s.copy(isPreviewActive = true) }
            PLog.d(TAG, "Preview started")
            settleFrontAutoExposure(sessionGeneration)

        } catch (e: CameraAccessException) {
            PLog.e(TAG, "Failed to start preview")
            handlePreviewSessionFailure("start preview camera access", openGeneration, e)
        } catch (e: IllegalStateException) {
            PLog.e(TAG, "Failed to start preview - illegal state")
            handlePreviewSessionFailure("start preview illegal state", openGeneration, e)
        } catch (e: IllegalArgumentException) {
            PLog.e(TAG, "Failed to start preview - unconfigured surface")
            handlePreviewSessionFailure("start preview unconfigured surface", openGeneration, e)
        }
    }

    /**
     * lilac front camera at full size: the viewfinder's brightness swings for as long as the camera is open,
     * with the same request on every frame, until one focus trigger is sent (a tap or a half-press does this).
     * An exposure lock alone did not stop it. Send that trigger once after the preview starts.
     */
    private fun settleFrontAutoExposure(sessionGeneration: Long) {
        val handler = cameraHandler ?: return
        if (!DeviceQuirks.isLilac || !isFrontCameraOpen() || _state.value.captureMode != CaptureMode.PHOTO) return
        handler.postDelayed({
            if (sessionGeneration != previewSessionGeneration || shutterKeyHold) return@postDelayed
            val builder = previewRequestBuilder ?: return@postDelayed
            val previousAfMode = builder.get(CaptureRequest.CONTROL_AF_MODE) ?: return@postDelayed
            if (!submitOneShotAfTrigger(CaptureRequest.CONTROL_AF_MODE_AUTO, "settle front exposure")) return@postDelayed
            handler.postDelayed({
                if (sessionGeneration != previewSessionGeneration || _state.value.focusPoint != null) return@postDelayed
                builder.set(CaptureRequest.CONTROL_AF_MODE, previousAfMode)
                updatePreview()
            }, 1100)
        }, 200)
    }

    private fun imageFormatToString(format: Int): String {
        return when (format) {
            NO_IMAGE_READER_FORMAT -> "NO_IMAGE_READER"
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.PRIVATE -> "PRIVATE"
            ImageFormat.YCBCR_P010 -> "YCBCR_P010"
            ImageFormat.YUV_420_888 -> "YUV_420_888"
            ImageFormat.JPEG -> "JPEG"
            else -> format.toString()
        }
    }

    private fun isRawCaptureReader(reader: ImageReader?): Boolean {
        return reader?.imageFormat == ImageFormat.RAW_SENSOR || reader?.imageFormat == ImageFormat.RAW10
    }

    /**
     * RAW is an ImageReader/session-level choice. If the desired state changes after an
     * ImageReader has already been created, changing [CameraState.useRaw] alone leaves the
     * active capture output in the old format.
     *
     * This check also runs from CameraDevice.onOpened so a preference restored while the
     * initial camera open is in flight is applied before the first preview session starts.
     */
    private fun restartCameraForRawCaptureOutputMismatch(reason: String): Boolean {
        val currentState = _state.value
        if (currentState.captureMode != CaptureMode.PHOTO) return false

        val currentReader = imageReader ?: return false
        val expectsRawOutput = requestedRawCaptureEnabled && isRawSupported
        val hasRawOutput = isRawCaptureReader(currentReader)
        if (expectsRawOutput == hasRawOutput) return false

        val surfaceTexture = previewSurfaceTexture ?: return false
        if (cameraDevice == null) {
            PLog.d(
                TAG,
                "RAW capture output update is waiting for camera open: " +
                        "reason=$reason, expectedRaw=$expectsRawOutput, actualRaw=$hasRawOutput"
            )
            return false
        }

        PLog.i(
            TAG,
            "Restarting camera for RAW capture output update: " +
                    "reason=$reason, expectedRaw=$expectsRawOutput, actualRaw=$hasRawOutput"
        )
        openCamera(surfaceTexture)
        return true
    }

// ==================== 统一参数配置 ====================

    private fun resolveFaceDetectMode(availableModes: IntArray, maximumFaceCount: Int): Int {
        if (maximumFaceCount <= 0) {
            return CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        }
        return when {
            availableModes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) ->
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL

            availableModes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) ->
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE

            else -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        }
    }

    private fun faceDetectModeName(mode: Int): String {
        return when (mode) {
            CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL -> "FULL"
            CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE -> "SIMPLE"
            else -> "OFF"
        }
    }

    /**
     * 将当前状态中的相机参数应用到 CaptureRequest.Builder
     *
     * 统一应用共享参数，并显式处理预览与拍摄阶段的必要差异
     *
     * @param builder 需要配置的 Builder
     * @param isCapture 是否为拍摄请求（预览时某些参数有限制）
     * @param isRawCapture 是否为 RAW 拍摄请求（跳过 RAW 不需要的 ISP 后处理参数）
     * @param useStillFlashAeMode 是否强制进入静态拍摄的闪光 AE 流程；全自动曝光下的强制闪光预览在设备支持
     * ON_ALWAYS_FLASH 时也会使用同一 AE 模式，预闪和主闪由同一闪光 AE 流程控制
     * @param useStillFlashTrigger 是否允许当前请求本身触发 SINGLE 闪光；会话更新和预览请求必须关闭
     * @param currentState 本次请求使用的设置；单帧闪光从预闪到主闪使用快门按下时的快照
     */
    private fun applyBaseCameraSettings(
        builder: CaptureRequest.Builder,
        isCapture: Boolean = false,
        isRawCapture: Boolean = false,
        disableZslForHdrCapture: Boolean = false,
        useStillFlashAeMode: Boolean = isCapture,
        useStillFlashTrigger: Boolean = isCapture,
        currentState: CameraState = _state.value,
    ) {
        // every RAW still request passes through here; the raw19 shim needs the JPEG beside the RAW (see rawCompanionJpegReader)
        // and on the lilac front camera in low light every YUV still request carries the small JPEG (see frontStillJpegReader)
        if (isCapture && (isRawCapture || frontStillJpegActive)) rawCompanionJpegReader?.surface?.let { builder.addTarget(it) }

        // 1. 曝光设置
        applyExposureSettings(
            builder = builder,
            state = currentState,
            isCapture = isCapture,
            useStillFlashAeMode = useStillFlashAeMode
        )

        // 2. 白平衡设置
        applyWhiteBalanceSettings(builder, currentState, isCapture)

        // 3. 闪光灯设置（触发权限与 AE 模式分开控制）
        applyFlashSettings(builder, currentState, isCapture, useStillFlashTrigger)

        // 4. 变焦设置
        applyZoomSettings(builder, currentState, isCapture, isRawCapture)

        // 5. 对焦设置
        applyFocusSettings(builder, currentState)

        if (!isRawCapture) {
            // 6. 图像质量设置（锐化、降噪）
            applyImageQualitySettings(builder, currentState)

        }

        // 8. 防抖设置
        applyStabilizationSettings(builder, currentState, isCapture)

        if (!isRawCapture) {
            // 9. 静态拍照后处理质量设置
            applyStillPostProcessingSettings(builder, currentState, isCapture)
        }

        // 10. 统计信息设置
        if (availableLensShadingMapModes.contains(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
            builder.set(
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON
            )
        }
        if (faceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
            builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode)
        }
        applySonySubjectKeys(builder)
        applySonyHintKeys(builder)

        if (disableZslForHdrCapture) {
            setZslDisabledIfSupported(builder)
        }
        applyVendorCaptureSettings(builder, isCapture)
        applyCustomVendorCaptureRequestKeys(builder, isCapture)
        if (isCapture) {
            PLog.i(
                TAG,
                "RAW_CROP_TRACE stage=CAMERA2_REQUEST isRaw=$isRawCapture " +
                    "selectedCamera=${currentState.currentCameraId} openCamera=${getActiveOpenCameraId()} " +
                    "physicalOutput=$activeOutputPhysicalCameraId stateZoom=${currentState.zoomRatio} " +
                    "requestZoom=${builder.get(CaptureRequest.CONTROL_ZOOM_RATIO)} " +
                    "requestScalerCrop=${builder.get(CaptureRequest.SCALER_CROP_REGION)} " +
                    "requestDistortion=${builder.get(CaptureRequest.DISTORTION_CORRECTION_MODE)}"
            )
        }
    }

    private fun forcedVendorSessionParameterValues(state: CameraState): Map<VendorCaptureKey, Int> {
        val lensId = state.currentCameraId
        val settings = state.vendorCaptureSettingsByLens.settingsFor(lensId)
        if (!settings.isEnabled) return emptyMap()

        return settings.values.filterKeys { key ->
            FORCED_VENDOR_SESSION_PARAMETER_KEYS.contains(key)
        }
    }

    private fun customVendorSessionParameterKeys(state: CameraState): List<CustomVendorKey> {
        return state.customVendorKeySettings.keysFor(
            cameraId = state.currentCameraId,
            target = CustomVendorKeyTarget.SESSION_PARAMETER
        )
    }

    private fun applyVendorCaptureSettings(builder: CaptureRequest.Builder, isCapture: Boolean) {
        val state = _state.value
        val lensId = state.currentCameraId
        val settings = state.vendorCaptureSettingsByLens.settingsFor(lensId)
        if (!settings.isEnabled) return

        applyVendorCaptureSettings(
            builder = builder,
            lensId = lensId,
            values = settings.values,
            target = if (isCapture) "capture" else "preview"
        )
    }

    private fun applyVendorCaptureSettings(
        builder: CaptureRequest.Builder,
        lensId: String,
        values: Map<VendorCaptureKey, Int>,
        target: String
    ) {
        values.forEach { (key, value) ->
            val normalizedValue = key.normalizeValue(value)
            try {
                when (key.valueType) {
                    VendorCaptureValueType.INT -> {
                        builder.set(
                            CaptureRequest.Key(key.requestKeyName, Int::class.java),
                            normalizedValue
                        )
                    }

                    VendorCaptureValueType.BYTE -> {
                        builder.set(
                            CaptureRequest.Key(key.requestKeyName, Byte::class.java),
                            normalizedValue.toByte()
                        )
                    }
                }
                PLog.d(TAG, "Applied vendor $target key for lens $lensId: ${key.requestKeyName}=$normalizedValue")
            } catch (e: Exception) {
                PLog.w(TAG, "Failed to apply vendor $target key for lens $lensId: ${key.requestKeyName}", e)
            }
        }
    }

    private fun applyCustomVendorCaptureRequestKeys(
        builder: CaptureRequest.Builder,
        isCapture: Boolean
    ) {
        val state = _state.value
        val lensId = state.currentCameraId
        val keys = state.customVendorKeySettings.keysFor(
            cameraId = lensId,
            target = CustomVendorKeyTarget.CAPTURE_REQUEST
        )
        applyCustomVendorKeys(
            builder = builder,
            lensId = lensId,
            keys = keys,
            target = if (isCapture) "capture" else "preview"
        )
    }

    private fun applyCustomVendorKeys(
        builder: CaptureRequest.Builder,
        lensId: String,
        keys: List<CustomVendorKey>,
        target: String
    ) {
        keys.forEach { key ->
            try {
                when (key.valueType) {
                    CustomVendorKeyValueType.INT32 -> {
                        builder.set(
                            CaptureRequest.Key(key.keyName, Int::class.java),
                            key.normalizedValue
                        )
                    }

                    CustomVendorKeyValueType.U8 -> {
                        // Camera2 exposes byte vendor tags as Java Byte. Values 128..255
                        // keep their unsigned bit pattern when converted to Byte.
                        builder.set(
                            CaptureRequest.Key(key.keyName, Byte::class.java),
                            key.normalizedValue.toByte()
                        )
                    }
                }
                PLog.d(
                    TAG,
                    "Applied custom vendor $target key for lens $lensId: " +
                        "${key.keyName}=${key.normalizedValue} (${key.valueType})"
                )
            } catch (e: Exception) {
                PLog.w(
                    TAG,
                    "Failed to apply custom vendor $target key for lens $lensId: ${key.keyName}",
                    e
                )
            }
        }
    }

    private fun isCaptureRequestKeyAvailable(requestKeyName: String): Boolean {
        val keyNames = availableCaptureRequestKeyNames
            ?: loadAvailableCaptureRequestKeyNames(getActiveOpenCameraCharacteristics()).also {
                availableCaptureRequestKeyNames = it
            }
        return keyNames.contains(requestKeyName)
    }

    private fun loadAvailableCaptureRequestKeyNames(characteristics: CameraCharacteristics?): Set<String> {
        if (characteristics == null) return emptySet()
        return try {
            characteristics.availableCaptureRequestKeys
                .map { it.name }
                .toSet()
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to query available capture request keys", e)
            emptySet()
        }
    }

    private fun loadAvailableColorCorrectionModes(characteristics: CameraCharacteristics): IntArray {
        return if (Build.VERSION.SDK_INT >= 36) {
            characteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES)
                ?: intArrayOf(
                    CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                    CameraMetadata.COLOR_CORRECTION_MODE_FAST,
                    CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY
                )
        } else {
            intArrayOf(
                CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                CameraMetadata.COLOR_CORRECTION_MODE_FAST,
                CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY
            )
        }
    }

    private fun loadAwbColorTemperatureRange(characteristics: CameraCharacteristics): Range<Int>? {
        return if (Build.VERSION.SDK_INT >= 36) {
            characteristics.readMetadataOrNull(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE)
        } else {
            null
        }
    }

    private fun resolveAwbTemperatureRange(): Range<Int> {
        return Range(AWB_TEMPERATURE_MIN, AWB_TEMPERATURE_MAX)
    }

    private fun coerceCctAwbTemperature(kelvin: Int): Int {
        val advertisedRange = awbColorTemperatureRange ?: return kelvin
        return kelvin.coerceIn(advertisedRange.lower, advertisedRange.upper)
    }

    private fun supportsCctWhiteBalance(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        if (!availableColorCorrectionModes.contains(CameraMetadata.COLOR_CORRECTION_MODE_CCT)) return false
        if (!availableAwbModes.contains(CameraMetadata.CONTROL_AWB_MODE_OFF)) return false
        if (awbColorTemperatureRange == null) return false
        return isCaptureRequestKeyAvailable(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE.name) &&
                isCaptureRequestKeyAvailable(CaptureRequest.COLOR_CORRECTION_COLOR_TINT.name)
    }

    private fun supportsManualMatrixWhiteBalance(): Boolean {
        return isManualPostProcessingSupported &&
                availableAwbModes.contains(CameraMetadata.CONTROL_AWB_MODE_OFF) &&
                isCaptureRequestKeyAvailable(CaptureRequest.COLOR_CORRECTION_MODE.name) &&
                isCaptureRequestKeyAvailable(CaptureRequest.COLOR_CORRECTION_GAINS.name) &&
                isCaptureRequestKeyAvailable(CaptureRequest.COLOR_CORRECTION_TRANSFORM.name)
    }

    private fun canUseManualMatrixWhiteBalance(snapshot: WhiteBalanceResultSnapshot?): Boolean {
        return supportsManualMatrixWhiteBalance() &&
                snapshot?.gains != null &&
                (hasColorMatrixWhiteBalanceTransformSupport() || snapshot.transform != null)
    }

    private fun hasColorMatrixWhiteBalanceTransformSupport(): Boolean {
        val characteristics = resolveActiveWhiteBalanceCharacteristics() ?: return false
        return characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) != null ||
                characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2) != null
    }

    private fun readWhiteBalanceResult(result: CaptureResult, awbMode: Int): WhiteBalanceResultSnapshot {
        val cctTemperature = if (Build.VERSION.SDK_INT >= 36) {
            result.get(CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE)
        } else {
            null
        }
        val cctTint = if (Build.VERSION.SDK_INT >= 36) {
            result.get(CaptureResult.COLOR_CORRECTION_COLOR_TINT)
        } else {
            null
        }
        return WhiteBalanceResultSnapshot(
            awbMode = awbMode,
            colorTemperature = cctTemperature,
            colorTint = cctTint,
            gains = readColorCorrectionGains(result),
            transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
        )
    }

    private fun readColorCorrectionGains(result: CaptureResult): RggbChannelVector? {
        return result.readMetadataOrNull(CaptureResult.COLOR_CORRECTION_GAINS, getActiveOpenCameraId())
    }

    private fun resolveWhiteBalanceControlPath(
        snapshot: WhiteBalanceResultSnapshot? = lastWhiteBalanceResult
    ): WhiteBalanceControlPath {
        if (snapshot == null) return WhiteBalanceControlPath.UNAVAILABLE
        if (canUseManualMatrixWhiteBalance(snapshot)) {
            return WhiteBalanceControlPath.MATRIX
        }
        if (supportsCctWhiteBalance() &&
            snapshot.colorTemperature != null &&
            snapshot.colorTint != null
        ) {
            return WhiteBalanceControlPath.CCT
        }
        return WhiteBalanceControlPath.UNAVAILABLE
    }

    private fun canAdjustManualWhiteBalance(snapshot: WhiteBalanceResultSnapshot? = lastWhiteBalanceResult): Boolean {
        return resolveWhiteBalanceControlPath(snapshot) != WhiteBalanceControlPath.UNAVAILABLE
    }

    private fun createManualWhiteBalanceAnchor(
        snapshot: WhiteBalanceResultSnapshot?,
        baseTemperature: Int
    ): ManualWhiteBalanceAnchor? {
        return when (resolveWhiteBalanceControlPath(snapshot)) {
            WhiteBalanceControlPath.CCT -> {
                val tint = snapshot?.colorTint ?: return null
                ManualWhiteBalanceAnchor(
                    controlPath = WhiteBalanceControlPath.CCT,
                    baseTemperature = baseTemperature,
                    colorTint = tint,
                    gains = null,
                    transform = null
                )
            }

            WhiteBalanceControlPath.MATRIX -> {
                val gains = snapshot?.gains ?: return null
                val transform = buildColorMatrixWhiteBalanceTransform(gains)
                    ?: snapshot.transform
                    ?: return null
                ManualWhiteBalanceAnchor(
                    controlPath = WhiteBalanceControlPath.MATRIX,
                    baseTemperature = baseTemperature,
                    colorTint = null,
                    gains = gains,
                    transform = transform
                )
            }

            WhiteBalanceControlPath.UNAVAILABLE -> null
        }
    }

    private fun RggbChannelVector.toStateGains(): WhiteBalanceGains {
        return WhiteBalanceGains(
            red = red,
            greenEven = greenEven,
            greenOdd = greenOdd,
            blue = blue
        )
    }

    private fun setZslDisabledIfSupported(builder: CaptureRequest.Builder) {
        if (!isZslControlAvailable()) return
        builder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false)
    }

    private fun isZslControlAvailable(): Boolean {
        isZslControlSupported?.let { return it }
        val supported = try {
            getActiveOpenCameraCharacteristics()
                ?.availableCaptureRequestKeys
                ?.contains(CaptureRequest.CONTROL_ENABLE_ZSL)
                ?: true
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to query available capture request keys for ZSL control", e)
            true
        }
        isZslControlSupported = supported
        if (!supported) {
            PLog.i(TAG, "CONTROL_ENABLE_ZSL is not listed in available capture request keys")
        }
        return supported
    }

    /**
     * 应用连续自动对焦设置。
     *
     * 一些设备不会很好地处理未声明支持的 AF 模式，或者在单次 AF 触发后保持旧触发状态。
     * 这里统一按能力选择默认 AF 模式，并显式复位触发器，避免预览请求停在不稳定状态。
     */
    private fun applyAutoFocusSettings(builder: CaptureRequest.Builder, state: CameraState) {
        val afMode = resolveAutoFocusMode(state.captureMode)

        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)

        if (_state.value.currentAfMode != afMode) {
            _state.update { s -> s.copy(currentAfMode = afMode) }
        }
    }

    private fun applyFocusSettings(builder: CaptureRequest.Builder, state: CameraState) {
        if (applyFrozenCaptureFocus(builder)) return
        if (state.isAutoFocus) {
            applyAutoFocusSettings(builder, state)
            return
        }

        val clampedDistance = if (state.minimumFocusDistance > 0f) {
            state.focusDistance.coerceIn(0f, state.minimumFocusDistance)
        } else {
            0f
        }

        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDistance)

        if (_state.value.currentAfMode != CaptureRequest.CONTROL_AF_MODE_OFF) {
            _state.update { s -> s.copy(currentAfMode = CaptureRequest.CONTROL_AF_MODE_OFF) }
        }
    }

    /** Keep preview updates from resetting the single-flash AF sweep/lock. */
    private fun applyFrozenCaptureFocus(builder: CaptureRequest.Builder): Boolean {
        if (!isCaptureFocusFrozen) return false
        val afMode = stillFlashAfMode ?: return false
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
        if (afMode == CaptureRequest.CONTROL_AF_MODE_OFF) {
            stillFlashFocusDistance?.let { builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, it) }
        }
        return true
    }

    private fun resolveAutoFocusMode(captureMode: CaptureMode): Int {
        if (availableAfModes.isEmpty()) {
            return CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }

        val preferredModes = if (captureMode == CaptureMode.VIDEO) {
            intArrayOf(
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
        } else {
            intArrayOf(
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
        }

        return preferredModes.firstOrNull { availableAfModes.contains(it) }
            ?: availableAfModes.first()
    }

    private fun getMaxPreviewExposureTime(state: CameraState): Long {
        return state.getPreviewExposureTimeLimitNs()
    }

    private fun coerceManualShutterSpeed(state: CameraState, value: Long): Long {
        val range = state.getManualShutterSpeedRange()
        return value.coerceIn(range.lower, range.upper)
    }

    private fun coercePreviewExposureTime(state: CameraState): Long {
        return state.shutterSpeed.coerceAtMost(getMaxPreviewExposureTime(state))
    }

    /**
     * 应用曝光设置
     *
     * 统一管理 CONTROL_AE_MODE，确保与闪光灯模式正确配合
     */
    private fun resolveSupportedAeMode(preferredMode: Int): Int {
        if (availableAeModes.contains(preferredMode)) return preferredMode
        if (availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON)) {
            return CaptureRequest.CONTROL_AE_MODE_ON
        }
        if (availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)) {
            return CaptureRequest.CONTROL_AE_MODE_OFF
        }
        // CameraX deliberately falls back to OFF instead of selecting an arbitrary AE mode.
        // This is also required for FLASH_MODE_TORCH, whose AE mode may only be ON or OFF.
        return CaptureRequest.CONTROL_AE_MODE_OFF
    }

    private fun isAePrecaptureSupported(): Boolean {
        return cachedHardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY &&
            isCaptureRequestKeyAvailable(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER.name)
    }

    private fun setAePrecaptureTriggerIfSupported(
        builder: CaptureRequest.Builder,
        trigger: Int,
    ) {
        if (isAePrecaptureSupported()) {
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, trigger)
        }
    }

    private fun resolveStillFlashAeMode(): Int {
        return if (availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)) {
            resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        } else {
            resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
        }
    }

    private fun shouldUsePreviewStillFlashAeMode(state: CameraState): Boolean {
        return isFlashSupported &&
            state.captureMode == CaptureMode.PHOTO &&
            state.flashMode == CameraMetadata.FLASH_MODE_SINGLE &&
            state.isIsoAuto &&
            state.isShutterSpeedAuto &&
            availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
    }

    private fun applyExposureSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean,
        useStillFlashAeMode: Boolean = isCapture
    ) {
        if (state.captureMode == CaptureMode.VIDEO) {
            applyVideoFpsRange(builder, state.videoConfig.fps.fps)
        }

        val effectiveUseStillFlashAeMode = useStillFlashAeMode ||
            (!isCapture && shouldUsePreviewStillFlashAeMode(state))

        // 根据曝光模式和闪光灯模式联合决定 AE_MODE
        val aeMode = when {
            // 1. 全自动曝光：静态闪光拍摄及其预览会话使用闪光 AE 模式；真正的预闪仍由
            // CONTROL_AE_PRECAPTURE_TRIGGER 或静态拍摄请求触发
            state.isIsoAuto && state.isShutterSpeedAuto -> {
                if (state.captureMode == CaptureMode.VIDEO) {
                    resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                } else {
                    when {
                        // The front camera's "flash on" is the screen: its requests stay in plain auto exposure.
                        isFlashSupported &&
                                state.flashMode == CameraMetadata.FLASH_MODE_SINGLE &&
                                effectiveUseStillFlashAeMode ->
                            resolveStillFlashAeMode()

                        else -> resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                    }
                }
            }
            // 2. 手动曝光或半自动曝光：尝试使用 OFF 模式，如果设备不支持则退而求其次使用 ON
            else -> {
                if (isManualSensorSupported && availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)) {
                    CaptureRequest.CONTROL_AE_MODE_OFF
                } else {
                    resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                }
            }
        }

        builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)

        // 如果是全自动曝光，设置曝光补偿
        if (state.isIsoAuto && state.isShutterSpeedAuto) {
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, state.exposureCompensation)
        } else {
            // 手动曝光 / 半自动曝光：手动设置 ISO 和快门
            // 只有在支持 MANUAL_SENSOR 的设备上才设置，否则保持自动
            if (isManualSensorSupported) {
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, state.iso)

                // 预览时限制曝光时间，防止画面卡死；拍摄时使用完整的用户设置
                val exposureTime = if (isCapture) {
                    state.shutterSpeed
                } else {
                    coercePreviewExposureTime(state)
                }
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
            }

            // 拍摄时设置低帧率范围以支持长曝光
            if (isCapture && state.captureMode == CaptureMode.PHOTO) {
                try {
                    val characteristics = getActiveOpenCameraCharacteristics() ?: return
                    val availableFpsRanges =
                        characteristics.readMetadataOrNull(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    val lowestFpsRange = availableFpsRanges?.minByOrNull { it.upper }
                    lowestFpsRange?.let {
                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                    }
                } catch (e: Exception) {
                    PLog.e(TAG, "Failed to set FPS range", e)
                }
            }
        }
    }

    private fun calculateRawMinShutterAdjustedExposure(
        state: CameraState,
        baseIso: Int,
        baseShutter: Long
    ): Pair<Int, Long> {
        if (!shouldApplyRawMinShutterLimit(state)) {
            return Pair(baseIso, baseShutter)
        }
        if (baseIso <= 0 || baseShutter <= 0L) return Pair(baseIso, baseShutter)

        val isoRange = state.getIsoRange()
        val shutterRange = state.getShutterSpeedRange()
        val targetShutterLimit = state.rawMinShutterSpeedNs.coerceIn(shutterRange.lower, shutterRange.upper)
        if (baseShutter == targetShutterLimit) return Pair(baseIso, baseShutter)

        val exposureProduct = baseIso.toDouble() * baseShutter.toDouble()
        val adjusted = if (baseShutter < targetShutterLimit) {
            if (baseIso <= isoRange.lower) {
                Pair(baseIso, baseShutter)
            } else {
                val isoAtLimit = (exposureProduct / targetShutterLimit).roundToInt()
                if (isoAtLimit >= isoRange.lower) {
                    Pair(isoAtLimit.coerceIn(isoRange.lower, isoRange.upper), targetShutterLimit)
                } else {
                    val shutterAtMinIso = (exposureProduct / isoRange.lower).roundToLong()
                        .coerceIn(baseShutter, targetShutterLimit)
                        .coerceIn(shutterRange.lower, shutterRange.upper)
                    Pair(isoRange.lower, shutterAtMinIso)
                }
            }
        } else {
            val isoAtLimit = (exposureProduct / targetShutterLimit).roundToInt()
            if (isoAtLimit <= isoRange.upper) {
                Pair(isoAtLimit.coerceIn(isoRange.lower, isoRange.upper), targetShutterLimit)
            } else {
                val shutterAtMaxIso = (exposureProduct / isoRange.upper).roundToLong()
                    .coerceIn(targetShutterLimit, baseShutter)
                    .coerceIn(shutterRange.lower, shutterRange.upper)
                Pair(isoRange.upper, shutterAtMaxIso)
            }
        }

        if (adjusted.first != baseIso || adjusted.second != baseShutter) {
            PLog.d(
                TAG,
                "RAW min shutter override: ISO=$baseIso->${adjusted.first}, shutter=$baseShutter->${adjusted.second}, min=$targetShutterLimit"
            )
        }
        return adjusted
    }

    private fun shouldApplyRawMinShutterLimit(state: CameraState): Boolean {
        return state.captureMode == CaptureMode.PHOTO &&
                state.rawMinShutterSpeedNs > 0L &&
                state.isIsoAuto &&
                state.isShutterSpeedAuto &&
                state.flashMode != CameraMetadata.FLASH_MODE_SINGLE
    }

    private fun applyVideoFpsRange(builder: CaptureRequest.Builder, targetFps: Int) {
        // Sony's route records at its own rate; the camera's viewfinder stays at 30 like in Sony's app.
        if (enteredSonyMode != null || _state.value.videoCapabilities.sonyRouteFps.any { it.fps == targetFps }) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(30, 30))
            return
        }
        val characteristics = getActiveOpenCameraCharacteristics() ?: return
        val availableRanges =
            characteristics.readMetadataOrNull(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return
        
        // 寻找完全匹配的固定帧率区间，例如 [60, 60]
        val exactRange = availableRanges.firstOrNull { it.lower == targetFps && it.upper == targetFps }
        
        val resolvedRange = if (exactRange != null) {
            exactRange
        } else {
            // 如果设备未宣传 [60, 60]，但为了稳定吐出 60fps，我们强行构造并锁定固定区间 [targetFps, targetFps]
            val forced = android.util.Range(targetFps, targetFps)
            PLog.w(
                TAG,
                "Camera characteristics do not advertise exact $targetFps fps range, forcing $forced. " +
                    "Advertised ranges=${availableRanges.joinToString()}"
            )
            forced
        }
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, resolvedRange)
    }

    /**
     * 应用白平衡设置
     */
    private fun applyWhiteBalanceSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean
    ) {
        if (state.awbMode != CameraMetadata.CONTROL_AWB_MODE_OFF) {
            applyAutoWhiteBalanceSettings(builder, state, isCapture)
            return
        }

        val anchor = manualWhiteBalanceAnchor
        when (anchor?.controlPath ?: WhiteBalanceControlPath.UNAVAILABLE) {
            WhiteBalanceControlPath.CCT -> applyCctWhiteBalanceSettings(builder, state, isCapture, anchor)
            WhiteBalanceControlPath.MATRIX -> applyMatrixWhiteBalanceSettings(builder, state, isCapture, anchor)
            WhiteBalanceControlPath.UNAVAILABLE -> applyAutoWhiteBalanceSettings(
                builder = builder,
                state = state.copy(awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO),
                isCapture = isCapture
            )
        }
    }

    private fun applyAutoWhiteBalanceSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean
    ) {
        // Debug builds: `adb shell setprop debug.lilac.awbmode <CONTROL_AWB_MODE>` forces one of the camera's
        // presets, to test whether a fixed white balance keeps the front camera's dim blacks consistent.
        val forcedMode = if (BuildConfig.DEBUG) SystemPropertiesUtil.get("debug.lilac.awbmode")?.trim()?.toIntOrNull() else null
        val requestedMode = forcedMode ?: state.awbMode
        val awbMode = if (availableAwbModes.isEmpty() || requestedMode in availableAwbModes) {
            requestedMode
        } else {
            CameraMetadata.CONTROL_AWB_MODE_AUTO
        }
        builder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode)
        val shouldLockAwb = state.videoConfig.shouldLockWhiteBalance(
            captureMode = state.captureMode,
            isRecording = state.videoRecordingState.isRecording
        ) &&
            getActiveOpenCameraCharacteristics()
                ?.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, shouldLockAwb)

        // 自动白平衡：拍照优先高质量色彩校正，预览维持快速路径
        if (isManualPostProcessingSupported) {
            val colorCorrectionMode = if (isCapture && state.captureMode == CaptureMode.PHOTO) {
                CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY
            } else {
                CaptureRequest.COLOR_CORRECTION_MODE_FAST
            }
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, colorCorrectionMode)
        }
    }

    private fun applyCctWhiteBalanceSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean,
        anchor: ManualWhiteBalanceAnchor?
    ) {
        if (Build.VERSION.SDK_INT < 36 || anchor?.colorTint == null) {
            applyAutoWhiteBalanceSettings(
                builder = builder,
                state = state.copy(awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO),
                isCapture = isCapture
            )
            return
        }
        val range = resolveAwbTemperatureRange()
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_CCT)
        builder.set(
            CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE,
            coerceCctAwbTemperature(state.awbTemperature.coerceIn(range.lower, range.upper))
        )
        builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, anchor.colorTint)
    }

    private fun applyMatrixWhiteBalanceSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean,
        anchor: ManualWhiteBalanceAnchor?
    ) {
        val resolvedAnchor = anchor ?: return applyAutoWhiteBalanceSettings(
            builder = builder,
            state = state.copy(awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO),
            isCapture = isCapture
        )
        val gains = resolvedAnchor.gains ?: return applyAutoWhiteBalanceSettings(
            builder = builder,
            state = state.copy(awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO),
            isCapture = isCapture
        )
        val resolvedGains = resolveManualMatrixGains(state.awbTemperature, resolvedAnchor, gains)
        val transform = buildColorMatrixWhiteBalanceTransform(resolvedGains)
            ?: resolvedAnchor.transform
            ?: return applyAutoWhiteBalanceSettings(
                builder = builder,
                state = state.copy(awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO),
                isCapture = isCapture
            )
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, resolvedGains)
        builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, transform)
    }

    private fun buildColorMatrixWhiteBalanceTransform(gains: RggbChannelVector): ColorSpaceTransform? {
        val characteristics = resolveActiveWhiteBalanceCharacteristics() ?: return null
        val calibration = RawColorCalibrationPolicy.fromCharacteristics(characteristics)
        val colorMatrix1 = calibration.colorMatrix1?.let(::extractMatrix3x3)
        val colorMatrix2 = calibration.colorMatrix2?.let(::extractMatrix3x3)
        if (colorMatrix1 == null && colorMatrix2 == null) return null

        val matrix = DngSdkColorSpec.computeCameraToWorkingMatrix(
            colorMatrix1 = colorMatrix1,
            colorMatrix2 = colorMatrix2,
            forwardMatrix1 = calibration.forwardMatrix1?.let(::extractMatrix3x3),
            forwardMatrix2 = calibration.forwardMatrix2?.let(::extractMatrix3x3),
            calibrationIlluminant1 = calibration.illuminant1,
            calibrationIlluminant2 = calibration.illuminant2,
            cameraCalibration1 = calibration.calibration1?.let(::extractMatrix3x3),
            cameraCalibration2 = calibration.calibration2?.let(::extractMatrix3x3),
            whiteBalanceGains = floatArrayOf(gains.red, gains.greenEven, gains.greenOdd, gains.blue),
            workingColorSpace = RawColorSpace.SRGB
        ) ?: return null

        return removeWhiteBalanceFromColorTransform(matrix, gains).toColorSpaceTransform()
    }

    private fun removeWhiteBalanceFromColorTransform(
        rawCameraToSrgb: FloatArray,
        gains: RggbChannelVector
    ): FloatArray {
        val redGain = gains.red.coerceAtLeast(1e-3f)
        val greenGain = ((gains.greenEven + gains.greenOdd) * 0.5f).coerceAtLeast(1e-3f)
        val blueGain = gains.blue.coerceAtLeast(1e-3f)
        val result = rawCameraToSrgb.copyOf()

        // Camera2 applies COLOR_CORRECTION_GAINS before COLOR_CORRECTION_TRANSFORM.
        // DNG-style ColorMatrix math bakes CameraNeutral/WB into the matrix, so divide
        // matrix columns by the gains to avoid applying white balance twice.
        for (row in 0 until 3) {
            result[row * 3] /= redGain
            result[row * 3 + 1] /= greenGain
            result[row * 3 + 2] /= blueGain
        }
        return result
    }

    private fun resolveActiveWhiteBalanceCharacteristics(): CameraCharacteristics? {
        val candidateIds = buildList {
            activeOutputPhysicalCameraId?.let(::add)
            getCurrentOpenCameraId().takeIf { it.isNotEmpty() }?.let(::add)
            _state.value.currentCameraId.takeIf { it.isNotEmpty() }?.let(::add)
        }.distinct()

        for (cameraId in candidateIds) {
            getCameraCharacteristicsOrNull(cameraId, "white balance color matrix")?.let { return it }
        }
        return getActiveOpenCameraCharacteristics()
    }

    private fun extractMatrix3x3(transform: ColorSpaceTransform): FloatArray {
        return FloatArray(9) { index ->
            val row = index / 3
            val col = index % 3
            transform.getElement(col, row).toFloat()
        }
    }

    private fun FloatArray.toColorSpaceTransform(): ColorSpaceTransform? {
        if (size != 9 || any { !it.isFinite() }) return null
        val rationals = Array(9) { index ->
            val value = this[index].coerceIn(-1.5f, 3f)
            Rational((value * 1_000_000f).roundToInt(), 1_000_000)
        }
        return ColorSpaceTransform(rationals)
    }

    /**
     * 应用闪光灯设置
     *
     * 注意：只设置 FLASH_MODE，AE_MODE 由 applyExposureSettings 统一管理
     *
     * @param isCapture 是否为拍摄请求（预览时某些闪光模式需要特殊处理）
     */
    private fun applyFlashSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean,
        useStillFlashTrigger: Boolean = isCapture,
    ) {
        if (!isFlashSupported) {
            builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            return
        }

        if (state.captureMode == CaptureMode.VIDEO) {
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (state.videoConfig.torchEnabled) {
                    CameraMetadata.FLASH_MODE_TORCH
                } else {
                    CameraMetadata.FLASH_MODE_OFF
                }
            )
            return
        }

        when (state.flashMode) {
            CameraMetadata.FLASH_MODE_SINGLE -> {
                if (!state.isIsoAuto || !state.isShutterSpeedAuto) {
                    // Manual/semi-manual exposure has no AE precapture convergence.
                    // Fire a single flash only for the still request.
                    builder.set(
                        CaptureRequest.FLASH_MODE,
                        if (isCapture) {
                            CameraMetadata.FLASH_MODE_SINGLE
                        } else {
                            CameraMetadata.FLASH_MODE_OFF
                        }
                    )
                } else {
                    val alwaysFlashAeSupported = availableAeModes.contains(
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    )
                    if (alwaysFlashAeSupported) {
                        // AE_MODE_ON_ALWAYS_FLASH owns precapture and main-flash control.
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                    } else {
                        // Some devices advertise AE_MODE_ON but not ON_ALWAYS_FLASH. Keep the
                        // repeating session at FLASH_OFF; only the one-shot precapture request
                        // and the final still request use FLASH_SINGLE.
                        builder.set(
                            CaptureRequest.FLASH_MODE,
                            if (isCapture || useStillFlashTrigger) {
                                CameraMetadata.FLASH_MODE_SINGLE
                            } else {
                                CameraMetadata.FLASH_MODE_OFF
                            }
                        )
                    }
                }
            }

            CameraMetadata.FLASH_MODE_TORCH -> {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            }

            else -> {
                builder.set(
                    CaptureRequest.FLASH_MODE,
                    if (selfTimerLampOn && !isCapture) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF
                )
            }
        }
    }

    // The flash LED as a self-timer lamp, like a camera's front lamp; only while the flash is off.
    private var selfTimerLampOn = false

    fun setSelfTimerLamp(on: Boolean) {
        if (!isFlashSupported || selfTimerLampOn == on) return
        selfTimerLampOn = on
        previewRequestBuilder?.apply {
            applyFlashSettings(this, _state.value, isCapture = false)
            updatePreview()
        }
    }

    /** Whether the camera's own face detection sees a face in the latest preview frame. */
    fun facesInView(): Boolean = (lastCaptureResult?.get(CaptureResult.STATISTICS_FACES)?.size ?: 0) > 0

    /**
     * 应用变焦设置
     */
    private fun applyZoomSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean = false,
        isRawCapture: Boolean = false,
    ) {
        val openCameraId = getActiveOpenCameraId()
        if (openCameraId.isEmpty()) return

        try {
            val characteristics = resolveZoomRequestCharacteristics(openCameraId)
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            val zoomRatioRange = characteristics.readMetadataOrNull(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            val minZoom = zoomRatioRange?.lower ?: 1f
            val maxSupportedZoom = zoomRatioRange?.upper ?: maxZoom
            val userZoomRatio = state.zoomRatio.coerceIn(minZoom, maxSupportedZoom)
            val zoomRatio = userZoomRatio
            val activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            // On lilac a cropped RAW still is sent through the camera's second image front-end, which the RAW
            // shim cannot copy, and the shim is poisoned. Ask for the full sensor; the RAW is cropped in software.
            val fullSensorRaw = IS_LILAC && isCapture && isRawCapture

            applyZoomRequestSettings(
                builder = builder,
                zoomRatio = if (fullSensorRaw) 1f else zoomRatio,
                activeRect = activeRect,
                zoomRatioRange = zoomRatioRange,
                resetCropAtUnitZoom = fullSensorRaw
            )
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to apply zoom settings", e)
        }
    }

    private fun resolveZoomRequestCharacteristics(openCameraId: String): CameraCharacteristics {
        val physicalCameraId = activeOutputPhysicalCameraId
        if (physicalCameraId != null) {
            getCameraCharacteristicsOrNull(physicalCameraId, "physical zoom request")?.let {
                return it
            }
        }
        return getCameraCharacteristicsCached(openCameraId)
    }

    private fun applyZoomRequestSettings(
        builder: CaptureRequest.Builder,
        zoomRatio: Float,
        activeRect: Rect?,
        zoomRatioRange: android.util.Range<Float>?,
        resetCropAtUnitZoom: Boolean
    ) {
        if (shouldUseControlZoomRatio(zoomRatioRange)) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
            return
        }

        activeRect ?: return
        if (zoomRatio <= 1f && !resetCropAtUnitZoom) return

        zoomRatioRange?.let {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1f)
        }
        builder.set(CaptureRequest.SCALER_CROP_REGION, buildCenteredCropRegion(activeRect, zoomRatio))
    }

    private fun shouldUseControlZoomRatio(zoomRatioRange: android.util.Range<Float>?): Boolean {
        zoomRatioRange ?: return false
        return _state.value.availableCameras.size <= 1 || zoomRatioRange.lower < 1f
    }

    private fun buildCenteredCropRegion(activeRect: Rect, zoomRatio: Float): Rect {
        val safeZoomRatio = zoomRatio.coerceAtLeast(1f)
        if (safeZoomRatio == 1f) return Rect(activeRect)

        val cropWidth = (activeRect.width() / safeZoomRatio).roundToInt().coerceAtLeast(1)
        val cropHeight = (activeRect.height() / safeZoomRatio).roundToInt().coerceAtLeast(1)
        val cropLeft = activeRect.left + (activeRect.width() - cropWidth) / 2
        val cropTop = activeRect.top + (activeRect.height() - cropHeight) / 2
        return Rect(
            cropLeft,
            cropTop,
            cropLeft + cropWidth,
            cropTop + cropHeight
        )
    }

    private fun Rect.toCameraCoordinateRect(): CameraCoordinateRect {
        return CameraCoordinateRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }

    private fun CameraCoordinateRect.toAndroidRect(): Rect {
        return Rect(left, top, right, bottom)
    }

    /**
     * 应用防抖设置
     *
     * 视频模式按用户选项启用 EIS/OIS。EIS+始终请求关闭 Camera2 EIS 和 HAL OIS；
     * 仅当 HAL 忽略 OIS OFF 时，才用逐时刻镜头内参或 OIS 位移修正滚快门网格。
     */
    private fun applyStabilizationSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean = false,
    ) {
        try {
            if (state.captureMode == CaptureMode.VIDEO) {
                val mode = state.videoConfig.stabilizationMode
                val enhancedSelected = mode == VideoStabilizationMode.ENHANCED
                val requestedVideoMode = if (enhancedSelected) {
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF.takeIf {
                        availableVideoStabilizationModes.contains(it)
                    }
                } else {
                    resolveVideoStabilizationRequestMode(mode)
                }
                requestedVideoMode?.let { videoStabilizationMode ->
                    builder.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        videoStabilizationMode
                    )
                }
                if (enhancedSelected && isCaptureRequestKeyAvailable(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE.name
                    )
                ) {
                    // Never enable OIS proactively for EIS+. Some HALs default TEMPLATE_PREVIEW
                    // to OIS ON or ignore OFF; the coordinator fuses telemetry only for that
                    // forced-ON case and disables EIS+ if no correction stream is available.
                    builder.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF,
                    )
                } else if (availableOpticalStabilizationModes.contains(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                ) {
                    builder.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        if (mode == VideoStabilizationMode.OIS) {
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                        } else {
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                        }
                    )
                }
                applyOisDataMode(
                    builder,
                    enhancedSelected &&
                        realtimeStabilizationCoordinator.shouldRequestOisSamples,
                )
                return
            }

            val enhancedPhotoPreview = state.photoPreviewStabilizationEnabled && !isCapture
            // 部分 HAL 的预览模板默认开启厂商 EIS，EIS+ 预览需与视频分支一致显式关闭。
            if (enhancedPhotoPreview &&
                availableVideoStabilizationModes.contains(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            ) {
                builder.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            }
            if (availableOpticalStabilizationModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
                builder.set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    if (enhancedPhotoPreview) {
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    } else {
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    }
                )
            }
            applyOisDataMode(builder, enhancedPhotoPreview)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to apply stabilization settings", e)
        }
    }

    private fun applyOisDataMode(builder: CaptureRequest.Builder, enabled: Boolean) {
        if (!isCaptureRequestKeyAvailable(CaptureRequest.STATISTICS_OIS_DATA_MODE.name)) return
        // A listed request key does not mean ON is supported; HALs without OIS samples reject
        // the whole request, which ends in a camera error.
        val onSupported = getActiveOpenCameraCharacteristics()
            ?.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES)
            ?.contains(CaptureRequest.STATISTICS_OIS_DATA_MODE_ON) == true
        builder.set(
            CaptureRequest.STATISTICS_OIS_DATA_MODE,
            if (enabled && onSupported) {
                CaptureRequest.STATISTICS_OIS_DATA_MODE_ON
            } else {
                CaptureRequest.STATISTICS_OIS_DATA_MODE_OFF
            },
        )
    }

    private fun resolveVideoStabilizationRequestMode(mode: VideoStabilizationMode): Int? {
        return when (mode) {
            VideoStabilizationMode.EIS -> when {
                isPreviewVideoStabilizationAvailable() ->
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION

                availableVideoStabilizationModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) ->
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON

                else -> null
            }

            VideoStabilizationMode.ENHANCED,
            VideoStabilizationMode.OIS,
            VideoStabilizationMode.SONY_STEADYSHOT, // Sony's service stabilises, not the camera request
            VideoStabilizationMode.SONY_ACTIVE,
            VideoStabilizationMode.OFF -> {
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    .takeIf { availableVideoStabilizationModes.contains(it) }
            }
        }
    }

    private fun isPreviewVideoStabilizationAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                availableVideoStabilizationModes.contains(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                )
    }

    /**
     * 应用图像质量设置（锐化、降噪）
     *
     * 照片与视频使用各自的参数，视频预览和录制保持一致。
     *
     * @param builder 需要配置的 Builder
     * @param currentState 当前拍摄模式及多帧配置
     */
    private fun applyImageQualitySettings(builder: CaptureRequest.Builder, currentState: CameraState) {
        try {
            val isVideo = currentState.captureMode == CaptureMode.VIDEO
            val selectedEdgeLevel = if (isVideo) videoEdgeLevel else edgeLevel
            val selectedNrLevel = if (isVideo) videoNrLevel else nrLevel
            val isBurst = !isVideo && currentState.requiresMultiFrameCaptureSequence
            val effectiveEdgeLevel = if (isBurst && selectedEdgeLevel == 2) 1 else selectedEdgeLevel
            val edgeMode = when (effectiveEdgeLevel) {
                0 -> CaptureRequest.EDGE_MODE_OFF
                1 -> CaptureRequest.EDGE_MODE_FAST
                2 -> CaptureRequest.EDGE_MODE_HIGH_QUALITY
                3 -> if (availableEdgeModes.contains(CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG)) {
                    CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG
                } else {
                    CaptureRequest.EDGE_MODE_FAST
                }

                else -> CaptureRequest.EDGE_MODE_FAST
            }
            if (availableEdgeModes.contains(edgeMode)) {
                builder.set(CaptureRequest.EDGE_MODE, edgeMode)
            } else if (availableEdgeModes.contains(CaptureRequest.EDGE_MODE_FAST)) {
                builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
            }
            val effectiveNrLevel = if (
                isBurst && selectedNrLevel == NoiseReductionLevel.HIGH_QUALITY
            ) {
                NoiseReductionLevel.FAST
            } else {
                selectedNrLevel
            }
            val noiseReductionMode = when (effectiveNrLevel) {
                NoiseReductionLevel.OFF -> CaptureRequest.NOISE_REDUCTION_MODE_OFF
                NoiseReductionLevel.MINIMAL -> if (availableNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)) {
                    CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                } else {
                    CaptureRequest.NOISE_REDUCTION_MODE_FAST
                }

                NoiseReductionLevel.FAST -> CaptureRequest.NOISE_REDUCTION_MODE_FAST
                NoiseReductionLevel.HIGH_QUALITY -> CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                NoiseReductionLevel.ZERO_SHUTTER_LAG -> if (availableNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)) {
                    CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG
                } else {
                    CaptureRequest.NOISE_REDUCTION_MODE_FAST
                }

                else -> CaptureRequest.NOISE_REDUCTION_MODE_FAST
            }

            if (availableNoiseReductionModes.contains(noiseReductionMode)) {
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode)
            } else if (availableNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST)) {
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to apply image quality settings", e)
        }
    }

    private fun applyStillPostProcessingSettings(
        builder: CaptureRequest.Builder,
        state: CameraState,
        isCapture: Boolean
    ) {
        if (!isCapture || state.captureMode != CaptureMode.PHOTO) {
            applyFastStillPostProcessingSettings(builder)
            return
        }

        applyHighQualityStillPostProcessingSettings(builder)
    }

    private fun applyFastStillPostProcessingSettings(builder: CaptureRequest.Builder) {
        selectBestMode(
            availableColorCorrectionAberrationModes,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF
        )?.let { builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, it) }

        selectBestMode(
            availableHotPixelModes,
            CaptureRequest.HOT_PIXEL_MODE_FAST,
            CaptureRequest.HOT_PIXEL_MODE_OFF
        )?.let { builder.set(CaptureRequest.HOT_PIXEL_MODE, it) }

        selectBestMode(
            availableShadingModes,
            CaptureRequest.SHADING_MODE_FAST,
            CaptureRequest.SHADING_MODE_OFF
        )?.let { builder.set(CaptureRequest.SHADING_MODE, it) }

        selectBestMode(
            availableDistortionCorrectionModes,
            CaptureRequest.DISTORTION_CORRECTION_MODE_FAST,
            CaptureRequest.DISTORTION_CORRECTION_MODE_OFF
        )?.let { builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, it) }
    }

    private fun applyHighQualityStillPostProcessingSettings(builder: CaptureRequest.Builder) {
        selectBestMode(
            availableColorCorrectionAberrationModes,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF
        )?.let { builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, it) }

        selectBestMode(
            availableHotPixelModes,
            CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY,
            CaptureRequest.HOT_PIXEL_MODE_FAST,
            CaptureRequest.HOT_PIXEL_MODE_OFF
        )?.let { builder.set(CaptureRequest.HOT_PIXEL_MODE, it) }

        selectBestMode(
            availableShadingModes,
            CaptureRequest.SHADING_MODE_HIGH_QUALITY,
            CaptureRequest.SHADING_MODE_FAST,
            CaptureRequest.SHADING_MODE_OFF
        )?.let { builder.set(CaptureRequest.SHADING_MODE, it) }

        selectBestMode(
            availableDistortionCorrectionModes,
            CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY,
            CaptureRequest.DISTORTION_CORRECTION_MODE_FAST,
            CaptureRequest.DISTORTION_CORRECTION_MODE_OFF
        )?.let { builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, it) }
    }

    private fun selectBestMode(availableModes: IntArray, vararg preferredModes: Int): Int? {
        return preferredModes.firstOrNull { availableModes.contains(it) }
    }

    private fun buildSelectableNoiseReductionModes(hardwareModes: IntArray): IntArray {
        val orderedModes = mutableListOf<Int>()
        val preferredOrder = listOf(
            CaptureRequest.NOISE_REDUCTION_MODE_OFF,
            CaptureRequest.NOISE_REDUCTION_MODE_FAST,
            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY,
            CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG,
            CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
        )
        preferredOrder.forEach { mode ->
            if (hardwareModes.contains(mode)) {
                orderedModes += mode
            }
        }
        return orderedModes.toIntArray()
    }

    /**
     * 设置锐化等级
     */
    fun setEdgeLevel(level: Int) {
        edgeLevel = level
    }

    /**
     * 设置降噪等级
     */
    fun setNRLevel(level: Int) {
        val normalizedLevel = NoiseReductionLevel.normalize(level)
        nrLevel = normalizedLevel
        _state.update { s -> s.copy(nrLevel = normalizedLevel) }
    }

    fun setVideoImageQualitySettings(nrLevel: Int, edgeLevel: Int) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setVideoImageQualitySettings(nrLevel, edgeLevel) }
            return
        }

        val normalizedNrLevel = NoiseReductionLevel.normalize(nrLevel)
        if (videoNrLevel == normalizedNrLevel && videoEdgeLevel == edgeLevel) return
        videoNrLevel = normalizedNrLevel
        videoEdgeLevel = edgeLevel
        PLog.d(TAG, "Video image quality settings: nr=$videoNrLevel, edge=$videoEdgeLevel")

        val currentState = _state.value
        if (currentState.captureMode == CaptureMode.VIDEO) {
            previewRequestBuilder?.let { builder ->
                applyImageQualitySettings(builder, currentState)
                updatePreview()
            }
        }
    }

    fun setVendorCaptureSettingsByLens(settingsByLens: VendorCaptureSettingsByLens) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                setVendorCaptureSettingsByLens(settingsByLens)
            }
            return
        }

        val previousState = _state.value
        if (previousState.vendorCaptureSettingsByLens == settingsByLens) return

        val previousVendorSessionValues = forcedVendorSessionParameterValues(previousState)
        val updatedState = previousState.copy(vendorCaptureSettingsByLens = settingsByLens)
        val updatedVendorSessionValues = forcedVendorSessionParameterValues(updatedState)

        _state.update { s -> s.copy(vendorCaptureSettingsByLens = settingsByLens) }
        PLog.d(TAG, "Vendor capture lens settings count: ${settingsByLens.settingsByLensId.size}")

        val shouldRecreateSession =
            previousVendorSessionValues != updatedVendorSessionValues &&
                    cameraDevice != null &&
                    previewSurface != null
        if (shouldRecreateSession) {
            if (_state.value.videoRecordingState.isRecording) {
                pendingVendorSessionParameterRestart = true
                PLog.w(
                    TAG,
                    "Vendor session parameter changed during recording; keeping active request until session restart"
                )
                return
            }
            PLog.d(
                TAG,
                "Recreating preview session for vendor session parameter change: " +
                        "old=$previousVendorSessionValues, new=$updatedVendorSessionValues"
            )
            createPreviewSession(openGeneration = cameraOpenGeneration)
            return
        }

        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    fun setCustomVendorKeySettings(settings: CustomVendorKeySettings) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                setCustomVendorKeySettings(settings)
            }
            return
        }

        val previousState = _state.value
        if (previousState.customVendorKeySettings == settings) return

        val previousSessionKeys = customVendorSessionParameterKeys(previousState)
        val updatedState = previousState.copy(customVendorKeySettings = settings)
        val updatedSessionKeys = customVendorSessionParameterKeys(updatedState)

        _state.update { s -> s.copy(customVendorKeySettings = settings) }
        PLog.d(TAG, "Custom vendor key count: ${settings.keys.size}")

        val shouldRecreateSession =
            previousSessionKeys != updatedSessionKeys &&
                cameraDevice != null &&
                previewSurface != null
        if (shouldRecreateSession) {
            if (_state.value.videoRecordingState.isRecording) {
                pendingVendorSessionParameterRestart = true
                PLog.w(
                    TAG,
                    "Custom vendor session parameter changed during recording; " +
                        "keeping active request until session restart"
                )
                return
            }
            PLog.d(
                TAG,
                "Recreating preview session for custom vendor session parameter change: " +
                    "old=${previousSessionKeys.map { it.keyName }}, " +
                    "new=${updatedSessionKeys.map { it.keyName }}"
            )
            createPreviewSession(openGeneration = cameraOpenGeneration)
            return
        }

        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    /**
     * 设置是否使用 RAW 格式拍照
     */
    fun setUseRaw(
        enabled: Boolean,
        reconfigureCaptureOutputIfNeeded: Boolean = true
    ) {
        requestedRawCaptureEnabled = enabled
        _state.update { it.copy(useRaw = enabled) }
        PLog.d(TAG, "RAW 格式拍照: $enabled")

        if (!reconfigureCaptureOutputIfNeeded) return

        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                restartCameraForRawCaptureOutputMismatch("RAW setting changed")
            }
            return
        }
        restartCameraForRawCaptureOutputMismatch("RAW setting changed")
    }

    fun setRawMinShutterSpeedNs(value: Long) {
        val resolvedValue = value.coerceAtLeast(0L)
        _state.update { s -> s.copy(rawMinShutterSpeedNs = resolvedValue) }
        PLog.d(TAG, "RAW 最低快门速度: $resolvedValue ns")
    }

    /**
     * 设置虚化模拟光圈大小
     */
    fun setAperture(value: Float) {
        _state.update { s -> s.copy(virtualAperture = value) }
        PLog.d(TAG, "设置虚拟光圈: $value")
    }

    fun setPortraitMode(enabled: Boolean) {
        _state.update { s -> s.copy(isPortraitMode = enabled) }
    }

    /**
     * 启用/禁用虚拟光圈控制
     */
    fun setVirtualApertureEnabled(enabled: Boolean) {
        _state.update { s -> s.copy(isVirtualApertureEnabled = enabled) }
        PLog.d(TAG, "虚拟光圈开关: $enabled")
    }

    /**
     * 获取当前摄像头 ID
     */
    fun getCurrentCameraId(): String {
        return _state.value.currentCameraId
    }

    /** 获取传感器方向（供外部 YUV 处理使用） */
    fun getSensorOrientation(): Int {
        return cachedSensorOrientation
    }

    /**
     * 获取镜头朝向
     */
    fun getLensFacing(): Int {
        return cachedLensFacing
    }

    /**
     * 获取最后一次拍摄结果（用于异步获取 EXIF 信息）
     */
    fun getLastCaptureResult(): CaptureResult? {
        return lastCaptureResult
    }

// ==================== 镜头切换 ====================

    /**
     * 获取所有后置摄像头
     */
    fun getBackCameras(): List<CameraInfo> {
        return _state.value.availableCameras.filter {
            it.lensFacing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    /**
     * 切换摄像头（前后置切换）
     */
    fun switchCamera() {
        val currentLensType = _state.value.currentLensType

        val nextLensType = if (currentLensType == LensType.FRONT) {
            LensType.BACK_MAIN
        } else {
            LensType.FRONT
        }

        switchToLens(nextLensType)
    }

    /**
     * 切换到指定的镜头类型
     */
    fun switchToLens(lensType: LensType) {
        val cameras = _state.value.availableCameras
        val currentLensType = _state.value.currentLensType

        if (currentLensType == lensType) return

        val targetCamera = cameras.find { it.lensType == lensType }

        targetCamera?.let { cam ->
            PLog.d(TAG, "Switching to lens: $lensType, cameraId: ${cam.cameraId}")
            _state.update { s -> s.copy(
                currentCameraId = cam.cameraId,
                currentLensType = cam.lensType,
                zoomRatio = 1f
            ) }
        } ?: PLog.w(TAG, "Camera with lens type $lensType not found")
    }

    /**
     * 切换到指定的相机 ID
     */
    fun switchToCameraId(cameraId: String) {
        val cameras = _state.value.availableCameras
        val targetCamera = cameras.find { it.cameraId == cameraId }

        targetCamera?.let { cam ->
            PLog.d(TAG, "Switching to camera ID: $cameraId")
            _state.update { s -> s.copy(
                currentCameraId = cam.cameraId,
                currentLensType = cam.lensType,
                zoomRatio = 1f
            ) }
        } ?: PLog.w(TAG, "Camera with ID $cameraId not found")
    }

// ==================== 曝光控制 ====================

    /** Serialize user changes and their snapshots with the asynchronous focus controls. */
    fun updateUserCaptureSettings(
        update: Camera2Controller.() -> Unit,
        onUpdated: (CustomCaptureSettings) -> Unit,
    ) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { updateUserCaptureSettings(update, onUpdated) }
            return
        }
        update(this)
        val settings = snapshotCustomCaptureSettings()
        if (retainCaptureSettingsEnabled) {
            retainedCaptureSettings = settings
            onUpdated(settings)
        }
    }

    fun setCaptureSettingsRetention(
        enabled: Boolean,
        savedSettings: CustomCaptureSettings? = null,
        onConfigured: ((CustomCaptureSettings?) -> Unit)? = null,
    ) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setCaptureSettingsRetention(enabled, savedSettings, onConfigured) }
            return
        }
        retainCaptureSettingsEnabled = enabled
        retainedCaptureSettings = if (enabled) savedSettings ?: snapshotCustomCaptureSettings() else null
        if (!enabled) pendingRetainedAwbTemperature = null
        onConfigured?.invoke(retainedCaptureSettings)
    }

    private fun snapshotCustomCaptureSettings(): CustomCaptureSettings {
        val current = _state.value
        return CustomCaptureSettings.fromState(current).copy(
            // Preserve the mode to return to when hyperfocal focus is turned off.
            isAutoFocus = focusModeBeforeHyperfocal ?: current.isAutoFocus,
            focusDistance = focusDistanceBeforeHyperfocal ?: current.focusDistance,
            awbMode = if (pendingRetainedAwbTemperature != null) {
                CameraMetadata.CONTROL_AWB_MODE_OFF
            } else current.awbMode,
            awbTemperature = pendingRetainedAwbTemperature ?: current.awbTemperature,
        )
    }

    /** Apply after lens capabilities are loaded, before constructing the first preview request. */
    private fun applyRetainedCaptureSettings() {
        val settings = retainedCaptureSettings ?: return
        val current = _state.value
        val evStep = current.getExposureCompensationStep()
        val evRange = current.getExposureCompensationRange()
        val compensation = if (evStep > 0f) {
            (settings.exposureCompensationEv / evStep).roundToInt()
                .coerceIn(evRange.lower, evRange.upper)
        } else 0
        val supportsManualExposure = isManualSensorSupported &&
            availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
        val isoAuto = settings.isIsoAuto || !supportsManualExposure
        val shutterAuto = settings.isShutterSpeedAuto || !supportsManualExposure
        val isoRange = current.getIsoRange()
        val restoredIso = settings.iso.coerceIn(isoRange.lower, isoRange.upper)
        val restoredShutter = coerceManualShutterSpeed(current, settings.shutterSpeedNs)
        val manualExposureAdjustment = if (!isoAuto && !shutterAuto) {
            (ln(restoredIso.toDouble() / settings.iso) +
                ln(restoredShutter.toDouble() / settings.shutterSpeedNs)) / ln(2.0)
        } else 0.0
        _state.update { s -> s.copy(
            exposureCompensation = compensation,
            exposureBias = if (isoAuto || shutterAuto) {
                compensation * evStep
            } else settings.exposureBiasEv + manualExposureAdjustment.toFloat(),
            isIsoAuto = isoAuto,
            iso = restoredIso,
            isShutterSpeedAuto = shutterAuto,
            shutterSpeed = restoredShutter,
        ) }
        setAutoFocus(settings.isAutoFocus || current.minimumFocusDistance <= 0f)
        if (!settings.isAutoFocus) setFocusDistance(settings.focusDistance)
        if (settings.isHyperfocalFocusEnabled) setHyperfocalFocusEnabled(true)

        // Manual WB needs a fresh CCT or gains/transform anchor from this camera session.
        manualWhiteBalanceAnchor = null
        if (settings.awbMode == CameraMetadata.CONTROL_AWB_MODE_OFF) {
            setAwbMode(CameraMetadata.CONTROL_AWB_MODE_AUTO)
            pendingRetainedAwbTemperature = settings.awbTemperature
        } else {
            setAwbMode(settings.awbMode)
        }
        PLog.d(TAG, "Restored custom capture settings for camera=${current.currentCameraId}: $settings")
    }

    /**
     * 设置曝光补偿
     */
    fun setExposureCompensation(value: Int) {
        val range = _state.value.getExposureCompensationRange()
        val evStep = _state.value.getExposureCompensationStep()
        val clampedValue = value.coerceIn(range.lower, range.upper)
        val exposureBias = clampedValue * evStep
        _state.update { s -> s.copy(exposureCompensation = clampedValue, exposureBias = exposureBias) }

        previewRequestBuilder?.apply {
            // 使用统一的曝光设置方法，确保与闪光灯模式正确配合
            applyExposureSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /**
     * 设置 ISO
     */
    fun setIso(value: Int) {
        val currentState = _state.value
        val range = currentState.getIsoRange()
        val clampedValue = value.coerceIn(range.lower, range.upper)

        val evStep = currentState.getExposureCompensationStep()
        val sliderBias = currentState.exposureCompensation * evStep
        // 只有当两者都是手动时，才需要计算并叠加曝光偏移
        val isFullManual = !currentState.isShutterSpeedAuto

        val newBias = if (isFullManual) {
            val deltaEv = if (currentState.iso > 0) {
                ln(clampedValue.toDouble() / currentState.iso.toDouble()) / ln(2.0)
            } else 0.0
            currentState.exposureBias + deltaEv.toFloat()
        } else {
            sliderBias
        }

        _state.update { s -> s.copy(
            iso = clampedValue,
            isIsoAuto = false,
            exposureBias = newBias
        ) }

        previewRequestBuilder?.apply {
            applyExposureSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /**
     * 设置快门速度
     *
     * 注意：照片预览会限制最大曝光时间为 1/15 秒，视频模式限制为 1/6 秒
     * 照片拍摄请求会使用完整的用户设置
     */
    fun setShutterSpeed(value: Long) {
        val currentState = _state.value
        val clampedValue = coerceManualShutterSpeed(currentState, value)

        val evStep = currentState.getExposureCompensationStep()
        val sliderBias = currentState.exposureCompensation * evStep
        // 只有当两者都是手动时，才需要计算并叠加曝光偏移
        val isFullManual = !currentState.isIsoAuto

        val newBias = if (isFullManual) {
            val deltaEv = if (currentState.shutterSpeed > 0) {
                ln(clampedValue.toDouble() / currentState.shutterSpeed.toDouble()) / ln(2.0)
            } else 0.0
            currentState.exposureBias + deltaEv.toFloat()
        } else {
            sliderBias
        }

        _state.update { s -> s.copy(
            shutterSpeed = clampedValue,
            isShutterSpeedAuto = false,
            exposureBias = newBias
        ) }

        previewRequestBuilder?.apply {
            applyExposureSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /* ... flash mode ... */

    fun setFlashMode(value: Int) {
        _state.update { s -> s.copy(flashMode = value) }

        previewRequestBuilder?.apply {
            // 预览不发送预闪触发器；支持 ON_ALWAYS_FLASH 时由 AE 模式保留闪光会话配置，
            // 实际闪光仍只由静态拍摄/预闪序列触发。
            applyBaseCameraSettings(this, isCapture = false)
            setAePrecaptureTriggerIfSupported(
                this,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
            )
            updatePreview()
        }
    }

    /**
     * 设置自动曝光模式 (Legacy / Global)
     */
    fun setAutoExposure(enabled: Boolean) {
        val currentState = _state.value
        val evStep = currentState.getExposureCompensationStep()
        val sliderBias = currentState.exposureCompensation * evStep

        _state.update { s -> s.copy(
            isIsoAuto = enabled,
            isShutterSpeedAuto = enabled,
            exposureBias = if (enabled) sliderBias else currentState.exposureBias
        ) }

        previewRequestBuilder?.apply {
            applyExposureSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /**
     * 设置 ISO 自动模式
     */
    fun setIsoAuto(enabled: Boolean) {
        val currentState = _state.value
        val evStep = currentState.getExposureCompensationStep()
        val sliderBias = currentState.exposureCompensation * evStep

        // 只要 ISO 变成自动，或者快门已经是自动，都属于自动/半自动模式，重置偏移量
        val isAutoOrSemi = enabled || currentState.isShutterSpeedAuto
        val exposureBias = if (isAutoOrSemi) sliderBias else currentState.exposureBias

        _state.update { s -> s.copy(
            isIsoAuto = enabled,
            exposureBias = exposureBias
        ) }
        previewRequestBuilder?.apply {
            applyExposureSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /**
     * 设置快门自动模式
     */
    fun setShutterSpeedAuto(enabled: Boolean) {
        val currentState = _state.value
        val evStep = currentState.getExposureCompensationStep()
        val sliderBias = currentState.exposureCompensation * evStep

        // 只要快门变成自动，或者 ISO 已经是自动，都属于自动/半自动模式，重置偏移量
        val isAutoOrSemi = enabled || currentState.isIsoAuto
        val exposureBias = if (isAutoOrSemi) sliderBias else currentState.exposureBias

        _state.update { s -> s.copy(
            isShutterSpeedAuto = enabled,
            exposureBias = exposureBias
        ) }
        previewRequestBuilder?.apply {
            applyExposureSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /**
     * 设置白平衡模式
     */
    fun setAwbMode(mode: Int) {
        pendingRetainedAwbTemperature = null
        val normalizedMode = if (availableAwbModes.isEmpty() || mode in availableAwbModes) {
            mode
        } else {
            CameraMetadata.CONTROL_AWB_MODE_AUTO
        }

        if (normalizedMode == CameraMetadata.CONTROL_AWB_MODE_OFF) {
            val snapshot = lastWhiteBalanceResult
            if (!canAdjustManualWhiteBalance(snapshot)) {
                PLog.w(TAG, "Manual white balance ignored: current CCT or gains/transform result is unavailable")
                _state.update { s -> s.copy(
                    awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO,
                    canAdjustWhiteBalance = false
                ) }
                previewRequestBuilder?.apply {
                    applyWhiteBalanceSettings(this, _state.value, false)
                    updatePreview()
                }
                return
            }

            val range = resolveAwbTemperatureRange()
            val initialTemperature = (
                    snapshot?.colorTemperature
                        ?: _state.value.actualAwbTemperature
                        ?: snapshot?.gains?.let(::estimateKelvinFromRggbGains)
                        ?: _state.value.awbTemperature
                    ).coerceIn(range.lower, range.upper)
            val anchor = createManualWhiteBalanceAnchor(snapshot, initialTemperature)
            if (anchor == null) {
                PLog.w(TAG, "Manual white balance ignored: failed to freeze current white balance result")
                _state.update { s -> s.copy(
                    awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO,
                    canAdjustWhiteBalance = false
                ) }
                previewRequestBuilder?.apply {
                    applyWhiteBalanceSettings(this, _state.value, false)
                    updatePreview()
                }
                return
            }
            manualWhiteBalanceAnchor = anchor
            _state.update { s -> s.copy(
                awbMode = CameraMetadata.CONTROL_AWB_MODE_OFF,
                awbTemperature = initialTemperature,
                canAdjustWhiteBalance = true
            ) }
        } else {
            manualWhiteBalanceAnchor = null
            _state.update { s -> s.copy(awbMode = normalizedMode) }
        }

        previewRequestBuilder?.apply {
            applyWhiteBalanceSettings(this, _state.value, false)
            updatePreview()
        }
    }

    /**
     * 设置白平衡色温（Kelvin）
     *
     * App 可调范围为 2000K - 8000K；CCT 下发时再按设备上报范围夹住。
     * 没有当前真实 CCT 或 RGGB gains + color transform 时忽略手动调整。
     */
    fun setAwbTemperature(kelvin: Int) {
        val range = resolveAwbTemperatureRange()
        val clampedKelvin = kelvin.coerceIn(range.lower, range.upper)
        val snapshot = lastWhiteBalanceResult
        val anchor = manualWhiteBalanceAnchor ?: createManualWhiteBalanceAnchor(
            snapshot = snapshot,
            baseTemperature = snapshot?.colorTemperature
                ?: _state.value.actualAwbTemperature
                ?: snapshot?.gains?.let(::estimateKelvinFromRggbGains)
                ?: clampedKelvin
        )?.also { manualWhiteBalanceAnchor = it }
        if (anchor == null) {
            PLog.w(TAG, "Manual white balance temperature ignored: current CCT or gains/transform result is unavailable")
            return
        }

        pendingRetainedAwbTemperature = null

        _state.update { s -> s.copy(
            awbTemperature = clampedKelvin,
            awbMode = CameraMetadata.CONTROL_AWB_MODE_OFF,
            canAdjustWhiteBalance = true
        ) }

        previewRequestBuilder?.apply {
            applyWhiteBalanceSettings(this, _state.value, false)
            PLog.d(
                TAG,
                "AWB temperature set to: ${clampedKelvin}K (${anchor.controlPath.name})"
            )
            updatePreview()
        }
    }

    /**
     * 设置测光模式
     */
    fun setMeteringMode(mode: MeteringMode) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setMeteringMode(mode) }
            return
        }
        _state.update { s -> s.copy(meteringMode = mode, meteringPoint = null) }
        if (mode == MeteringMode.HIGHLIGHT_PRIORITY) {
            highlightPointInitialized = false
            lastSentHighlightPointX = -1f
            lastSentHighlightPointY = -1f
        }
        applyMeteringRegions()
        updatePreview()
        PLog.d(TAG, "测光模式: $mode")
    }

    /**
     * 更新高光区域坐标（由 GL 测光回调调用）
     * 使用 EMA 平滑防止 AE 频繁跳动
     */
    fun updateHighlightPoint(x: Float, y: Float) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { updateHighlightPoint(x, y) }
            return
        }
        highlightPointX = x
        highlightPointY = y
        if (!highlightPointInitialized) {
            highlightPointSmoothedX = x
            highlightPointSmoothedY = y
            highlightPointInitialized = true
        } else {
            val alpha = 0.1 // 降低平滑系数，增加稳定性
            highlightPointSmoothedX = (alpha * x + (1 - alpha) * highlightPointSmoothedX).toFloat()
            highlightPointSmoothedY = (alpha * y + (1 - alpha) * highlightPointSmoothedY).toFloat()
        }
        if (_state.value.effectiveMeteringMode == MeteringMode.HIGHLIGHT_PRIORITY) {
            // 计算当前平滑点与上次发送点的位移距离
            val dist = hypot(
                highlightPointSmoothedX.toDouble() - lastSentHighlightPointX,
                highlightPointSmoothedY.toDouble() - lastSentHighlightPointY
            )
            
            // 只有位移超过 8% (0.08) 或者这是初始化后的第一帧，才更新测光区域并触发预览
            // 这样可以避免微小的坐标抖动导致 AE 系统频繁重算测光
            if (dist > 0.08 || lastSentHighlightPointX < 0) {
                lastSentHighlightPointX = highlightPointSmoothedX
                lastSentHighlightPointY = highlightPointSmoothedY
                
                applyMeteringRegions()
                updatePreview()
            }
        }
    }

    /**
     * 根据当前测光模式设置默认 AE 区域
     *
     * 系统默认模式清除自定义 AE 区域，由 Camera2/设备自行决定测光行为；
     * 点测光和高光优先模式设置加权区域；
     * 平均测光模式设置全画面 AE 区域。
     */
    private fun applyMeteringRegions() {
        val builder = previewRequestBuilder ?: return
        val currentState = _state.value
        val mode = currentState.effectiveMeteringMode
        if (mode == MeteringMode.SYSTEM_DEFAULT) {
            clearCustomAeRegions(builder)
            return
        }
        if (maxAeRegions <= 0) return
        try {
            val point = if (mode == MeteringMode.HIGHLIGHT_PRIORITY && highlightPointInitialized) {
                highlightPointSmoothedX to highlightPointSmoothedY
            } else if (mode == MeteringMode.CENTER_WEIGHTED) {
                0.5f to 0.5f  // centre-weighted stays centred; a tap only focuses
            } else {
                currentState.meteringReferencePoint ?: (0.5f to 0.5f)
            }
            val mapping = mapPreviewMeteringPoint(point.first, point.second, pointMeteringViewAspectRatio)
                ?: return
            val rect = if (mode == MeteringMode.AVERAGE) {
                mapping.visibleRegion
            } else {
                val fraction = when (mode) {
                    MeteringMode.SPOT -> SPOT_AE_REGION_FRACTION
                    MeteringMode.CENTER_WEIGHTED -> CENTER_WEIGHTED_AE_REGION_FRACTION
                    MeteringMode.HIGHLIGHT_PRIORITY -> HIGHLIGHT_AE_REGION_FRACTION
                }
                PreviewMeteringMapper.buildCenteredRegion(mapping, fraction)
            }
            builder.set(
                CaptureRequest.CONTROL_AE_REGIONS,
                arrayOf(MeteringRectangle(rect.toAndroidRect(), MeteringRectangle.METERING_WEIGHT_MAX))
            )
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to apply metering regions", e)
        }
    }

    // AF 和 AE 共享预览裁切、前摄镜像和变焦坐标映射。
    private fun mapPreviewMeteringPoint(
        normX: Float,
        normY: Float,
        previewViewAspectRatio: Float?,
    ): PreviewMeteringMapping? {
        val builder = previewRequestBuilder ?: return null
        val openCameraId = getActiveOpenCameraId().takeIf { it.isNotEmpty() } ?: return null
        val characteristics = resolveZoomRequestCharacteristics(openCameraId)
        val activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        val zoomRatioRange = characteristics.readMetadataOrNull(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        val zoomMode = if (shouldUseControlZoomRatio(zoomRatioRange)) {
            PreviewMeteringZoomMode.POST_ZOOM_ACTIVE_ARRAY
        } else {
            PreviewMeteringZoomMode.SCALER_CROP_REGION
        }
        val requestedScalerCrop = if (zoomMode == PreviewMeteringZoomMode.SCALER_CROP_REGION) {
            builder.get(CaptureRequest.SCALER_CROP_REGION)
                ?: buildCenteredCropRegion(activeRect, _state.value.zoomRatio)
        } else {
            null
        }
        return PreviewMeteringMapper.mapPoint(
            normalizedX = normX,
            normalizedY = normY,
            activeArray = activeRect.toCameraCoordinateRect(),
            scalerCropRegion = requestedScalerCrop?.toCameraCoordinateRect(),
            zoomMode = zoomMode,
            previewViewAspectRatio = previewViewAspectRatio?.takeIf { it.isFinite() && it > 0f }
                ?: _state.value.getPreviewAspectRatio(),
            sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                ?: getSensorOrientation(),
            isFrontFacing = (characteristics.get(CameraCharacteristics.LENS_FACING) ?: getLensFacing()) ==
                CameraCharacteristics.LENS_FACING_FRONT,
        )
    }

    /** 拖动测光圈仅更新 AE，保留原有 AF 区域与镜头位置。 */
    fun moveMeteringPoint(normX: Float, normY: Float, viewAspectRatio: Float) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { moveMeteringPoint(normX, normY, viewAspectRatio) }
            return
        }
        val currentState = _state.value
        if (!normX.isFinite() || !normY.isFinite() || !viewAspectRatio.isFinite() || viewAspectRatio <= 0f) return
        if (isCaptureFocusFrozen || currentState.isCapturing || !currentState.supportsPointMetering) return
        if (previewRequestBuilder == null || currentState.focusPoint == null ||
            currentState.focusPointSource != FocusPointSource.MANUAL) return
        val point = normX.coerceIn(0f, 1f) to normY.coerceIn(0f, 1f)
        pointMeteringViewAspectRatio = viewAspectRatio
        _state.update { s -> s.copy(meteringPoint = point) }
        if (currentState.meteringPoint == null) {
            PLog.d(TAG, "Separate metering point: focus=${currentState.focusPoint} metering=$point")
        }
        applyMeteringRegions()
        updatePreview()
    }

    fun mergeMeteringPoint() {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { mergeMeteringPoint() }
            return
        }
        val currentState = _state.value
        if (isCaptureFocusFrozen || currentState.isCapturing || currentState.meteringPoint == null) return
        _state.update { s -> s.copy(meteringPoint = null) }
        applyMeteringRegions()
        updatePreview()
        PLog.d(TAG, "Merge metering point into focus=${currentState.focusPoint}")
    }

    private fun clearCustomAeRegions(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
    }

    private fun clearCustomAfRegions(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
    }

    /**
     * 将色温(Kelvin)转换为 RggbChannelVector
     *
     * 先估算光源 RGB，再取倒数作为 RAW Bayer 通道补偿增益。
     *
     * @param kelvin 色温值
     * @return RggbChannelVector 白平衡增益
     */
    private fun kelvinToRggbGains(kelvin: Int): RggbChannelVector {
        val illuminant = kelvinToNormalizedRgb(kelvin)
        val redGain = 1f / illuminant.red.coerceAtLeast(1e-3f)
        val greenGain = 1f / illuminant.green.coerceAtLeast(1e-3f)
        val blueGain = 1f / illuminant.blue.coerceAtLeast(1e-3f)
        val minGain = minOf(redGain, greenGain, blueGain).coerceAtLeast(1e-3f)

        return RggbChannelVector(
            (redGain / minGain).coerceIn(1f, 4f),
            (greenGain / minGain).coerceIn(1f, 4f),
            (greenGain / minGain).coerceIn(1f, 4f),
            (blueGain / minGain).coerceIn(1f, 4f)
        )
    }

    private fun resolveManualMatrixGains(
        targetKelvin: Int,
        anchor: ManualWhiteBalanceAnchor,
        frozenGains: RggbChannelVector
    ): RggbChannelVector {
        if (abs(targetKelvin - anchor.baseTemperature) <= 25) {
            return frozenGains
        }

        val baseGains = kelvinToRggbGains(anchor.baseTemperature)
        val targetGains = kelvinToRggbGains(targetKelvin)
        return RggbChannelVector(
            scaleFrozenWhiteBalanceGain(frozenGains.red, targetGains.red, baseGains.red),
            scaleFrozenWhiteBalanceGain(frozenGains.greenEven, targetGains.greenEven, baseGains.greenEven),
            scaleFrozenWhiteBalanceGain(frozenGains.greenOdd, targetGains.greenOdd, baseGains.greenOdd),
            scaleFrozenWhiteBalanceGain(frozenGains.blue, targetGains.blue, baseGains.blue)
        )
    }

    private fun scaleFrozenWhiteBalanceGain(
        frozenGain: Float,
        targetGain: Float,
        baseGain: Float
    ): Float {
        return (frozenGain * targetGain / baseGain.coerceAtLeast(1e-3f)).coerceAtLeast(1f)
    }

    private fun estimateKelvinFromRggbGains(gains: RggbChannelVector): Int {
        val range = resolveAwbTemperatureRange()
        val lower = range.lower.coerceAtLeast(1000)
        val upper = range.upper.coerceAtLeast(lower)
        val targetRed = gains.red.coerceAtLeast(1e-3f)
        val targetGreen = ((gains.greenEven + gains.greenOdd) / 2f).coerceAtLeast(1e-3f)
        val targetBlue = gains.blue.coerceAtLeast(1e-3f)
        var bestKelvin = lower
        var bestDistance = Double.MAX_VALUE

        var kelvin = lower
        while (kelvin <= upper) {
            val candidate = kelvinToRggbGains(kelvin)
            val candidateGreen = ((candidate.greenEven + candidate.greenOdd) / 2f).coerceAtLeast(1e-3f)
            val distance =
                abs(ln((candidate.red / targetRed).toDouble())) +
                        abs(ln((candidateGreen / targetGreen).toDouble())) * 0.5 +
                        abs(ln((candidate.blue / targetBlue).toDouble()))
            if (distance < bestDistance) {
                bestDistance = distance
                bestKelvin = kelvin
            }
            kelvin += 50
        }

        return (bestKelvin / 50f).roundToInt() * 50
    }

    private fun kelvinToNormalizedRgb(kelvin: Int): NormalizedRgb {
        val temperature = kelvin.coerceIn(1000, 40000) / 100.0f

        var red: Float
        var green: Float
        var blue: Float

        // 计算红色分量
        if (temperature <= 66) {
            red = 255f
        } else {
            red = (329.698727446 * (temperature - 60.0).pow(-0.1332047592)).toFloat()
            red = red.coerceIn(0f, 255f)
        }

        // 计算绿色分量
        if (temperature <= 66) {
            green = (99.4708025861 * ln(temperature.toDouble()) - 161.1195681661).toFloat()
        } else {
            green = (288.1221695283 * (temperature - 60.0).pow(-0.0755148492)).toFloat()
        }
        green = green.coerceIn(0f, 255f)

        // 计算蓝色分量
        if (temperature >= 66) {
            blue = 255f
        } else if (temperature <= 19) {
            blue = 0f
        } else {
            blue = (138.5177312231 * ln((temperature - 10).toDouble()) - 305.0447927307).toFloat()
            blue = blue.coerceIn(0f, 255f)
        }

        return NormalizedRgb(
            red = (red / 255f).coerceIn(0f, 1f),
            green = (green / 255f).coerceIn(0f, 1f),
            blue = (blue / 255f).coerceIn(0f, 1f)
        )
    }

    /**
     * 更新预览
     */
    private fun updatePreview() {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            if (!previewUpdateScheduled.compareAndSet(false, true)) return
            handler.post {
                previewUpdateScheduled.set(false)
                updatePreview()
            }
            return
        }

        // The flash sequence owns repeating requests until the still capture is finished.
        // Parameter setters may update the preview builder; apply them when restoring preview.
        if (stillFlashCaptureState != null) return

        // 检查相机和会话是否仍然有效，避免在相机关闭后的回调中调用 setRepeatingRequest。
        val device = cameraDevice
        val session = captureSession
        val builder = previewRequestBuilder

        if (device == null || session == null || builder == null) {
            PLog.v(TAG, "updatePreview: camera not ready (device=$device, session=$session, builder=$builder)")
            return
        }

        try {
            applyFrozenCaptureFocus(builder)
            session.setRepeatingRequest(builder.build(), previewCallback, cameraHandler)
        } catch (e: CameraAccessException) {
            PLog.e(TAG, "Failed to update preview", e)
        } catch (e: IllegalStateException) {
            // 相机已关闭或处于错误状态
            PLog.w(TAG, "Failed to update preview - camera closed or in error state", e)
        } catch (e: IllegalArgumentException) {
            PLog.w(TAG, "Failed to update preview - request settings error", e)
        }
    }

// ==================== 变焦控制 ====================

    /**
     * 设置变焦倍数
     * 注意：优先使用 Camera2 CONTROL_ZOOM_RATIO；设备不支持时才回退到 SCALER_CROP_REGION。
     */
    fun setZoomRatio(ratio: Float) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                setZoomRatio(ratio)
            }
            return
        }
        val requestedRatio = sanitizeZoomRatio(ratio)
        _state.update { s -> s.copy(zoomRatio = requestedRatio) }
        val builder = previewRequestBuilder ?: return
        val openCameraId = activeOpenCameraId.takeIf { it.isNotEmpty() } ?: return

        try {
            val characteristics = resolveZoomRequestCharacteristics(openCameraId)
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            val zoomRatioRange = characteristics.readMetadataOrNull(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            val minZoom = zoomRatioRange?.lower ?: 1f
            val maxSupportedZoom = zoomRatioRange?.upper ?: maxZoom
            val clampedRatio = requestedRatio.coerceIn(minZoom, maxSupportedZoom)

            _state.update { s -> s.copy(zoomRatio = clampedRatio) }
            if (recreateSessionForPhysicalZoomIfNeeded(_state.value)) {
                PLog.d(TAG, "setZoomRatio: $ratio -> $clampedRatio (physical output session recreated)")
                return
            }

            val activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            builder.apply {
                applyZoomRequestSettings(
                    builder = this,
                    zoomRatio = clampedRatio,
                    activeRect = activeRect,
                    zoomRatioRange = zoomRatioRange,
                    resetCropAtUnitZoom = true
                )
            }
            if (_state.value.meteringPoint != null) {
                applyMeteringRegions()
            }
            if (cameraDevice != null && captureSession != null) {
                updatePreview()
            }

            val zoomMode = if (shouldUseControlZoomRatio(zoomRatioRange)) {
                "CONTROL_ZOOM_RATIO"
            } else {
                "SCALER_CROP_REGION"
            }
            val applyTiming = if (captureSession == null) "builder-only" else "preview"
            PLog.d(TAG, "setZoomRatio: $ratio -> $clampedRatio ($zoomMode, $applyTiming)")

        } catch (e: Exception) {
            PLog.e(TAG, "Failed to set zoom", e)
        }
    }

    private fun sanitizeZoomRatio(ratio: Float): Float {
        return if (ratio.isFinite()) ratio.coerceAtLeast(0.01f) else 1f
    }

    private fun recreateSessionForPhysicalZoomIfNeeded(state: CameraState): Boolean {
        val desiredPhysicalCameraId = resolveOutputPhysicalCameraId(state)
        if (desiredPhysicalCameraId == activeOutputPhysicalCameraId) return false
        if (cameraDevice == null || previewSurface == null) return false

        PLog.i(
            TAG,
            "Recreating session for zoom physical output: " +
                    "old=$activeOutputPhysicalCameraId, new=$desiredPhysicalCameraId, " +
                    "targetZoom=${getTargetZoomRatioByMain(state, state.getCurrentCameraInfo())}"
        )

        activeOutputPhysicalCameraId = desiredPhysicalCameraId
        refreshActiveFocusLimit()
        refreshHyperfocalFocusDistanceIfEnabled(updatePreview = false)
        safeCloseCaptureSession(captureSession, "physical zoom output changed")
        captureSession = null
        _enginePreviewSource.value = null
        createPreviewSession(openGeneration = cameraOpenGeneration)
        return true
    }

    /**
     * 设置自动对焦开关
     */
    fun setAutoFocus(auto: Boolean) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setAutoFocus(auto) }
            return
        }
        clearHyperfocalFocusMemory()
        clearPointFocusState()
        _state.update { s -> s.copy(
            isAutoFocus = auto,
            isHyperfocalFocusEnabled = false,
            hyperfocalDistanceMeters = 0f,
            isFocusLocked = false
        ) }
        if (!isCaptureFocusFrozen) {
            previewRequestBuilder?.apply {
                clearCustomAfRegions(this)
                applyFocusSettings(this, _state.value)
                applyMeteringRegions()
                updatePreview()
            }
        } else {
            PLog.d(TAG, "Auto-focus mode update deferred until multi-frame capture completes")
        }
    }

    /**
     * 设置对焦距离 (0.0 ~ minimumFocusDistance)
     */
    fun setFocusDistance(distance: Float) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setFocusDistance(distance) }
            return
        }
        val minFocusDistance = _state.value.minimumFocusDistance
        if (minFocusDistance <= 0) return

        val clampedDistance = distance.coerceIn(0f, minFocusDistance)
        clearHyperfocalFocusMemory()
        _state.update { s -> s.copy(
            focusDistance = clampedDistance,
            isHyperfocalFocusEnabled = false,
            hyperfocalDistanceMeters = 0f,
            isFocusLocked = false
        ) }

        if (!_state.value.isAutoFocus && !isCaptureFocusFrozen) {
            previewRequestBuilder?.apply {
                applyFocusSettings(this, _state.value)
                updatePreview()
            }
        } else if (isCaptureFocusFrozen) {
            PLog.d(TAG, "Manual focus-distance update deferred until multi-frame capture completes")
        }
    }

    fun setHyperfocalFocusEnabled(enabled: Boolean) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setHyperfocalFocusEnabled(enabled) }
            return
        }
        if (enabled) {
            applyHyperfocalFocus(storePreviousFocus = true, updatePreview = true)
        } else {
            restoreFocusBeforeHyperfocal(updatePreview = true)
        }
    }

    private fun refreshHyperfocalFocusDistanceIfEnabled(updatePreview: Boolean) {
        if (!_state.value.isHyperfocalFocusEnabled) return
        applyHyperfocalFocus(storePreviousFocus = false, updatePreview = updatePreview)
    }

    private fun applyHyperfocalFocus(
        storePreviousFocus: Boolean,
        updatePreview: Boolean
    ): Boolean {
        refreshActiveFocusLimit()

        val minFocusDistance = _state.value.minimumFocusDistance
        if (minFocusDistance <= 0f) {
            PLog.w(TAG, "Hyperfocal focus unavailable: manual focus is not supported")
            if (_state.value.isHyperfocalFocusEnabled) {
                restoreFocusBeforeHyperfocal(updatePreview = updatePreview)
            }
            return false
        }

        val result = calculateHyperfocalFocusResult()
        if (result == null) {
            PLog.w(TAG, "Hyperfocal focus unavailable: unable to resolve physical camera optics")
            if (_state.value.isHyperfocalFocusEnabled) {
                restoreFocusBeforeHyperfocal(updatePreview = updatePreview)
            }
            return false
        }

        if (storePreviousFocus && !_state.value.isHyperfocalFocusEnabled) {
            focusModeBeforeHyperfocal = _state.value.isAutoFocus
            focusDistanceBeforeHyperfocal = _state.value.focusDistance
        }

        val clampedFocusDistance = result.focusDistanceDiopters.coerceIn(0f, minFocusDistance)
        clearPointFocusState()
        _state.update { s -> s.copy(
            isAutoFocus = false,
            focusDistance = clampedFocusDistance,
            isHyperfocalFocusEnabled = true,
            hyperfocalDistanceMeters = result.distanceMeters,
            isFocusLocked = false
        ) }

        if (updatePreview && !isCaptureFocusFrozen) {
            previewRequestBuilder?.apply {
                clearCustomAfRegions(this)
                applyFocusSettings(this, _state.value)
                applyMeteringRegions()
                updatePreview()
            }
        }

        PLog.i(
            TAG,
            "Hyperfocal focus enabled: camera=${result.cameraId}, " +
                    "f=${result.focalLengthMm}mm, N=${result.aperture}, " +
                    "c=${result.circleOfConfusionMm}mm, H=${result.distanceMeters}m, " +
                    "focus=${clampedFocusDistance}D"
        )
        return true
    }

    private fun restoreFocusBeforeHyperfocal(updatePreview: Boolean) {
        val restoreAutoFocus = focusModeBeforeHyperfocal ?: true
        val restoreFocusDistance = focusDistanceBeforeHyperfocal
            ?.coerceIn(0f, _state.value.minimumFocusDistance.takeIf { it > 0f } ?: Float.MAX_VALUE)
            ?: _state.value.focusDistance

        clearHyperfocalFocusMemory()
        clearPointFocusState()
        _state.update { s -> s.copy(
            isAutoFocus = restoreAutoFocus,
            focusDistance = restoreFocusDistance,
            isHyperfocalFocusEnabled = false,
            hyperfocalDistanceMeters = 0f,
            isFocusLocked = false
        ) }

        if (updatePreview && !isCaptureFocusFrozen) {
            previewRequestBuilder?.apply {
                clearCustomAfRegions(this)
                applyFocusSettings(this, _state.value)
                applyMeteringRegions()
                updatePreview()
            }
        }

        PLog.i(TAG, "Hyperfocal focus disabled: restoreAutoFocus=$restoreAutoFocus")
    }

    private fun clearHyperfocalFocusMemory() {
        focusModeBeforeHyperfocal = null
        focusDistanceBeforeHyperfocal = null
    }

    private fun calculateHyperfocalFocusResult(): HyperfocalFocusResult? {
        val (cameraId, characteristics) = resolveActiveFocusCharacteristics() ?: return null

        val focalLengthMm = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_HYPERFOCAL_FOCAL_LENGTH_MM

        val aperture = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_HYPERFOCAL_APERTURE

        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?: return null
        val diagonal = hypot(
            physicalSize.width.toDouble(),
            physicalSize.height.toDouble()
        )
        if (!diagonal.isFinite() || diagonal <= 0.0) return null

        val circleOfConfusionMm = (diagonal / HYPERFOCAL_COC_DIAGONAL_DIVISOR).toFloat()
        if (!circleOfConfusionMm.isFinite() || circleOfConfusionMm <= 0f) return null

        val hyperfocalDistanceMm =
            (focalLengthMm * focalLengthMm) / (aperture * circleOfConfusionMm) + focalLengthMm
        val hyperfocalDistanceMeters = hyperfocalDistanceMm / 1000f
        if (!hyperfocalDistanceMeters.isFinite() || hyperfocalDistanceMeters <= 0f) return null

        return HyperfocalFocusResult(
            cameraId = cameraId,
            focalLengthMm = focalLengthMm,
            aperture = aperture,
            circleOfConfusionMm = circleOfConfusionMm,
            distanceMeters = hyperfocalDistanceMeters,
            focusDistanceDiopters = 1f / hyperfocalDistanceMeters
        )
    }

// ==================== 对焦控制 ====================

    private fun logFocusTerminalResult(result: CaptureResult, afState: Int) {
        PLog.d(
            TAG,
            "Focus terminal: afState=$afState " +
                "source=${_state.value.focusPointSource} " +
                "afRegions=${result.get(CaptureResult.CONTROL_AF_REGIONS)?.contentToString()} " +
                "zoom=${result.get(CaptureResult.CONTROL_ZOOM_RATIO)} " +
                "crop=${result.get(CaptureResult.SCALER_CROP_REGION)} " +
                "lensDistance=${result.get(CaptureResult.LENS_FOCUS_DISTANCE)}"
        )
    }

    private fun recordFocusLockExposure(result: CaptureResult) {
        updateFocusLockSceneReference(result)
        sceneExposureReferencePending = true
        isFocusLockedWaitingForSceneChange = true
        focusLockSettleFrames = FOCUS_LOCK_SETTLE_FRAMES
        sceneChangeFrameCount = 0
    }

    private fun updateFocusLockSceneReference(result: CaptureResult) {
        focusLockedReferenceIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
        focusLockedReferenceExposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
        focusLockedReferenceDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
    }

    /** [spot] keeps a separate metering point there instead of clearing it with the focus point. */
    private fun restoreContinuousAf(spot: Pair<Float, Float>? = null, spotViewAspectRatio: Float? = null) {
        if (isCaptureFocusFrozen) {
            PLog.d(TAG, "Continuous AF restore deferred while multi-frame focus is frozen")
            return
        }
        clearPointFocusState(spot, spotViewAspectRatio)
        previewRequestBuilder?.apply {
            // 解锁触屏 AF 不能改变用户选择的手动对焦模式。
            applyFocusSettings(this, _state.value)
            clearCustomAfRegions(this)
            applySonySubjectKeys(this)
            applyMeteringRegions()
            updatePreview()
        }
    }

    fun unlockFocus() {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { unlockFocus() }
            return
        }
        if (deferWhileCapturing(::unlockFocus)) return
        PLog.d(TAG, "Unlock focus")
        restoreContinuousAf()
    }

    /**
     * Holds [action] until the still capture in progress has ended. An AF cancel or AF mode change sent during a
     * burst reaches the camera right after the burst's last request; the XZ1 Compact HAL then drops the RAW frame
     * still in flight, the capture sequence never completes, and the camera stays stuck until the camera service
     * restarts.
     */
    private fun deferWhileCapturing(action: () -> Unit): Boolean {
        if (activePhotoCapture == null) return false
        focusReleasesAfterCapture += action
        return true
    }

    private fun runFocusReleasesAfterCapture() {
        if (activePhotoCapture != null || focusReleasesAfterCapture.isEmpty()) return
        val actions = focusReleasesAfterCapture.toList()
        focusReleasesAfterCapture.clear()
        PLog.d(TAG, "Sending ${actions.size} focus release(s) held during the capture")
        actions.forEach { it() }
    }

    private fun clearPointFocusState(spot: Pair<Float, Float>? = null, spotViewAspectRatio: Float? = null) {
        pointMeteringViewAspectRatio = spotViewAspectRatio
        isFocusLockedWaitingForSceneChange = false
        sceneChangeFrameCount = 0
        focusLockedReferenceIso = 0
        focusLockedReferenceExposureNs = 0L
        focusLockedReferenceDistance = 0f
        focusLockSettleFrames = 0
        eyeTargetLastSeenElapsedMs = 0L
        pointAfTriggerPending = false
        _state.update { s -> s.copy(
            focusPoint = null,
            meteringPoint = spot,
            focusPointSource = FocusPointSource.MANUAL,
            isFocusLocked = false,
            isFocusing = false,
            focusSuccess = null
        ) }
    }

    /**
     * 点击对焦
     */
    fun focusOnPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int, allowSubject: Boolean = true) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        // A tap on the current selection's mark clears it, back to automatic.
        val markHalf = TAP_MARK_HALF_DP * context.resources.displayMetrics.density
        fun onMark(point: Pair<Float, Float>?) =
            point != null && abs(point.first * viewWidth - x) <= markHalf && abs(point.second * viewHeight - y) <= markHalf
        val overlay = _subjectOverlay.value
        if (overlay.tracking && onMark(overlay.trackBox?.let { it.centerX to it.centerY } ?: overlay.trackStart)) {
            cameraHandler?.post { clearSelection("object") }
            return
        }
        if (_state.value.focusPointSource == FocusPointSource.MANUAL && onMark(_state.value.focusPoint)) {
            Log.i(SonySubjectKeys.LOG_TAG, "selection cleared (point)")
            unlockFocus()
            return
        }
        if (allowSubject && selectSubjectAt(x / viewWidth, y / viewHeight, viewWidth.toFloat() / viewHeight)) return
        focusOnNormalizedPoint(
            normX = x / viewWidth,
            normY = y / viewHeight,
            source = FocusPointSource.MANUAL,
            previewViewAspectRatio = viewWidth.toFloat() / viewHeight,
        )
    }

    // A half-pressed camera key holds focus and exposure, like a camera's shutter button with AE lock.
    private var shutterKeyHold = false

    fun setShutterKeyHold(held: Boolean) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { setShutterKeyHold(held) }
            return
        }
        if (!held && deferWhileCapturing { setShutterKeyHold(false) }) return
        shutterKeyHold = held
        if (held) {
            // A half-press locks focus on the subject: the tracked object (a driver that tracks during the lock keeps
            // following it) or the main face.
            if (trackingSinceMs > 0L) {
                if (sonyFeatures.trackingDuringLock) lockSubjectAf("object", subjectAfRegion)
            } else if (_state.value.focusPoint == null) {
                // A tap or eye point keeps its own lock.
                keptFaces.firstOrNull { it.id == mainFaceId }?.let { lockSubjectAf("face ${it.id}", it.sensor) }
            }
        } else {
            halfPressCheck?.let { cameraHandler?.removeCallbacks(it) }
            halfPressCheck = null
            unparkLens()
            if (subjectAfLocked) {
                subjectAfLocked = false
                submitOneShot(
                    "half-press released: AF cancel",
                    set = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL) },
                    reset = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE) },
                )
            }
        }
        val aeLockAvailable = getActiveOpenCameraCharacteristics()
            ?.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
        previewRequestBuilder?.apply {
            if (aeLockAvailable) set(CaptureRequest.CONTROL_AE_LOCK, held)
            updatePreview()
        }
        _state.update { s -> s.copy(
            shutterKeyHeld = held,
            exposureHeld = held && aeLockAvailable && _state.value.isAutoExposure,
        ) }
    }

    // The pending half-press search for a face: runs the centre lock if the app's detector does not answer in time.
    private var halfPressCheck: Runnable? = null
    private var halfPressOutcomePending = false

    /** AF START on the subject's region in continuous mode; exposure is held by the half-press itself. */
    private fun lockSubjectAf(kind: String, sensor: CameraCoordinateRect?) {
        val state = _state.value
        val visible = mapPreviewMeteringPoint(0.5f, 0.5f, null)?.visibleRegion
        val region = sensor?.let { visible?.let(it::intersect) ?: it }
        if (maxAfRegions <= 0 || !state.supportsPointAutoFocus || !state.isAutoFocus || eyeFocusOwnsAf || region == null) {
            Log.i(SonySubjectKeys.LOG_TAG, "half-press: $kind, exposure lock only")
            return
        }
        val builder = previewRequestBuilder ?: return
        subjectAfRegion = region
        subjectAfSentMs = SystemClock.elapsedRealtime()
        afKickPending = false
        applySonySubjectKeys(builder)
        subjectAfLocked = submitOneShot(
            "half-press: $kind, AF start on $region",
            set = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START) },
            reset = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE) },
        )
        halfPressOutcomePending = subjectAfLocked
        afTriggerAwaited = subjectAfLocked
    }

    /**
     * A half-press with no subject: a face the camera reports now; else focus starts on the centre at once while the
     * app's detector looks for a face for up to 400 ms. A face found moves the lock onto it; otherwise the centre
     * lock stays and its mark appears. Call after [setShutterKeyHold].
     */
    fun halfPressFindFace() {
        val handler = cameraHandler ?: return
        if (Looper.myLooper() != handler.looper) {
            handler.post { halfPressFindFace() }
            return
        }
        if (!shutterKeyHold) return
        keptFaces.firstOrNull { it.id == mainFaceId }?.let {
            lockSubjectAf("face ${it.id}", it.sensor)
            return
        }
        // Fixed focus (the front camera): a face would only change exposure, which the half-press already holds.
        if (!_state.value.supportsPointAutoFocus) {
            lockCentre()
            return
        }
        val centre = mapPreviewMeteringPoint(0.5f, 0.5f, null)?.let {
            PreviewMeteringMapper.buildCenteredRegion(it, AF_REGION_WIDTH_FRACTION, AF_REGION_HEIGHT_FRACTION)
        }
        if (centre == null) {
            lockCentre()
            return
        }
        lockSubjectAf("centre (searching for a face)", centre)
        val fallback = Runnable {
            halfPressCheck = null
            showCentreMark()
        }
        halfPressCheck = fallback
        _faceCheckReason.value = "half"
        handler.postDelayed(fallback, HALF_PRESS_FACE_WAIT_MS)
    }

    /** No face after all: the centre lock already running shows its mark, as today's centre lock does. */
    private fun showCentreMark() {
        if (!shutterKeyHold) return
        Log.i(SonySubjectKeys.LOG_TAG, "half-press: centre")
        // isFocusing lets the usual AF result handling turn the mark green or blink it from the lock's state.
        _state.update { s -> s.copy(
            focusPoint = 0.5f to 0.5f,
            meteringPoint = null,
            focusPointSource = FocusPointSource.MANUAL,
            isFocusLocked = true,
            isFocusing = true,
            focusSuccess = null,
        ) }
    }

    private fun lockCentre() {
        if (!shutterKeyHold) return
        Log.i(SonySubjectKeys.LOG_TAG, "half-press: centre")
        focusOnNormalizedPoint(0.5f, 0.5f, FocusPointSource.MANUAL, lockFocus = true)
    }

    /** Focuses again on the tapped point (a half-press after a miss), keeping the exposure point as it is. */
    fun refocusTapPoint() {
        val point = _state.value.focusPoint?.takeIf { _state.value.focusPointSource == FocusPointSource.MANUAL } ?: return
        focusOnNormalizedPoint(
            normX = point.first,
            normY = point.second,
            source = FocusPointSource.MANUAL,
            previewViewAspectRatio = pointMeteringViewAspectRatio,
            preserveMeteringPoint = true,
        )
    }

    /** 拖动中更新 AF 区域，松手后扫描一次；整个过程保持独立 AE 区域。 */
    fun moveFocusPoint(normX: Float, normY: Float, viewAspectRatio: Float, finished: Boolean) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { moveFocusPoint(normX, normY, viewAspectRatio, finished) }
            return
        }
        val currentState = _state.value
        if (currentState.meteringPoint == null || currentState.focusPointSource != FocusPointSource.MANUAL) return
        if (!normX.isFinite() || !normY.isFinite() || !viewAspectRatio.isFinite() || viewAspectRatio <= 0f) return
        focusOnNormalizedPoint(
            normX = normX,
            normY = normY,
            source = FocusPointSource.MANUAL,
            lockFocus = currentState.isFocusLocked,
            previewViewAspectRatio = viewAspectRatio,
            preserveMeteringPoint = true,
            triggerFocus = finished,
        )
    }

    fun focusOnNormalizedPoint(
        normX: Float,
        normY: Float,
        source: FocusPointSource,
        lockFocus: Boolean = false,
        previewViewAspectRatio: Float? = null,
        preserveMeteringPoint: Boolean = false,
        triggerFocus: Boolean = true,
    ) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                focusOnNormalizedPoint(
                    normX = normX,
                    normY = normY,
                    source = source,
                    lockFocus = lockFocus,
                    previewViewAspectRatio = previewViewAspectRatio,
                    preserveMeteringPoint = preserveMeteringPoint,
                    triggerFocus = triggerFocus,
                )
            }
            return
        }
        if (isCaptureFocusFrozen || _state.value.isCapturing) {
            PLog.d(TAG, "Ignoring focus update while capture focus is frozen")
            return
        }
        if (!_state.value.isAutoFocus) {
            PLog.d(TAG, "Ignoring point-focus AF trigger while manual focus is active: source=$source")
            return
        }
        if (
            source == FocusPointSource.EYE &&
            !_state.value.supportsPointAutoFocus
        ) {
            PLog.d(TAG, "Ignoring eye-focus AF trigger on a fixed-focus camera")
            return
        }
        if (
            source == FocusPointSource.EYE &&
            _state.value.focusPoint != null &&
            _state.value.focusPointSource == FocusPointSource.MANUAL
        ) {
            PLog.d(TAG, "Ignoring stale eye-focus update while manual point focus is active")
            return
        }
        val openCameraId = getActiveOpenCameraId()
        if (openCameraId.isEmpty()) return

        // 重置场景变化检测状态（新的对焦覆盖旧的）
        isFocusLockedWaitingForSceneChange = false
        sceneChangeFrameCount = 0
        if (source == FocusPointSource.MANUAL) {
            eyeTargetLastSeenElapsedMs = 0L
        } else {
            eyeTargetLastSeenElapsedMs = SystemClock.elapsedRealtime()
        }

        try {
            val builder = previewRequestBuilder ?: return
            val normalizedX = normX.coerceIn(0f, 1f)
            val normalizedY = normY.coerceIn(0f, 1f)
            val mapping = mapPreviewMeteringPoint(normalizedX, normalizedY, previewViewAspectRatio) ?: return
            if (source == FocusPointSource.MANUAL && !preserveMeteringPoint) {
                pointMeteringViewAspectRatio = previewViewAspectRatio
            }

            // 存储UI坐标用于显示对焦框
            _state.update { s -> s.copy(
                focusPoint = Pair(normalizedX, normalizedY),
                meteringPoint = if (preserveMeteringPoint) _state.value.meteringPoint else null,
                focusPointSource = source,
                isFocusLocked = lockFocus,
                isFocusing = triggerFocus,
                focusSuccess = null
            ) }
            // AF 区域保持为可见画面的 10%，避免 SCALER_CROP_REGION 变焦后区域相对画面膨胀。
            val afRect = dimLightAfRegion(PreviewMeteringMapper.buildCenteredRegion(
                mapping = mapping,
                widthFraction = AF_REGION_WIDTH_FRACTION,
                heightFraction = AF_REGION_HEIGHT_FRACTION,
            ), mapping.visibleRegion).toAndroidRect()
            val afRegion = MeteringRectangle(afRect, MeteringRectangle.METERING_WEIGHT_MAX)

            if (triggerFocus) {
                PLog.d(
                    TAG,
                    "Focus mapping: ui=($normalizedX,$normalizedY) " +
                        "source=$source preserveMetering=$preserveMeteringPoint " +
                        "coordinateCamera=${activeOutputPhysicalCameraId ?: openCameraId} " +
                        "requestZoom=${builder.get(CaptureRequest.CONTROL_ZOOM_RATIO)} " +
                        "requestCrop=${builder.get(CaptureRequest.SCALER_CROP_REGION)} " +
                        "visible=${mapping.visibleRegion} center=(${mapping.centerX},${mapping.centerY}) " +
                        "af=$afRect metering=${_state.value.effectiveMeteringMode}"
                )
            }

            if (maxAfRegions > 0) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(afRegion))
                // Sony's driver steers AF to the region only in its user AF region mode.
                if (subjectAfRegion == null) applySonySubjectKeys(builder)
            }
            // 人眼跟踪只接管 AF，不移动用户当前的测光区域。
            if (source == FocusPointSource.MANUAL && !preserveMeteringPoint) {
                applyMeteringRegions()
            }
            val afMode = CaptureRequest.CONTROL_AF_MODE_AUTO
            _state.update { s -> s.copy(currentAfMode = afMode) }
            if (!triggerFocus) {
                updatePreview()
            } else if (!submitOneShotAfTrigger(afMode, "point focus")) {
                PLog.w(TAG, "Unable to submit point-focus AF trigger")
                _state.update { s -> s.copy(isFocusing = false, focusSuccess = false) }
            }

            // 对焦后通过场景变化检测自动恢复连续对焦（不再使用固定延迟）

        } catch (e: Exception) {
            PLog.e(TAG, "Failed to focus", e)
            _state.update { s -> s.copy(isFocusing = false, focusSuccess = false) }
        }
    }

    /**
     * 更新人眼跟踪框的位置，但不重复触发一次 AF 扫描。
     * 实际的 AF 触发由调用方根据目标位移和节流策略决定。
     */
    fun updateEyeFocusTarget(normX: Float, normY: Float) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { updateEyeFocusTarget(normX, normY) }
            return
        }
        if (!normX.isFinite() || !normY.isFinite()) return
        val currentState = _state.value
        if (!currentState.isAutoFocus) return
        if (currentState.focusPoint != null && currentState.focusPointSource == FocusPointSource.MANUAL) {
            return
        }
        eyeTargetLastSeenElapsedMs = SystemClock.elapsedRealtime()
        _state.update { s -> s.copy(
            focusPoint = Pair(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f)),
            focusPointSource = FocusPointSource.EYE,
            isFocusLocked = false,
        ) }
    }

    fun cancelEyeFocus(reason: String) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { cancelEyeFocus(reason) }
            return
        }
        if (_state.value.focusPointSource != FocusPointSource.EYE) return
        PLog.d(TAG, "Cancel eye focus: $reason")
        if (isCaptureFocusFrozen || !_state.value.supportsPointAutoFocus) {
            clearPointFocusState()
            return
        }
        if (_state.value.isAutoFocus) {
            restoreContinuousAf()
        } else {
            previewRequestBuilder?.apply {
                clearCustomAfRegions(this)
                applyFocusSettings(this, _state.value)
                applyMeteringRegions()
                updatePreview()
            }
            clearPointFocusState()
        }
    }

    // ==================== Sony smile scores and scene hints ====================

    private var hintFeatures = SonyHintFeatures()
    private val _smileScore = MutableStateFlow<SmileScore?>(null)
    /** The main face's smile score on the latest preview frame, or null with no face or no smile scores. */
    val smileScore: StateFlow<SmileScore?> = _smileScore.asStateFlow()
    private val _sceneHints = MutableStateFlow<List<SceneHint>>(emptyList())
    /** What the camera reports about the scene (one scene, then one condition), for icons only. */
    val sceneHints: StateFlow<List<SceneHint>> = _sceneHints.asStateFlow()
    private var pendingHints: List<SceneHint> = emptyList()
    private var pendingHintsSinceMs = 0L
    private var lastHintLogMs = 0L

    private fun resetHintState(features: SonyHintFeatures) {
        hintFeatures = features
        pendingHints = emptyList()
        _smileScore.value = null
        _sceneHints.value = emptyList()
    }

    private fun applySonyHintKeys(builder: CaptureRequest.Builder) {
        try {
            if (hintFeatures.smile) builder.set(SonyHintKeys.SMILE_MODE, SonyHintKeys.ON)
            if (hintFeatures.scene) builder.set(SonyHintKeys.SCENE_MODE, SonyHintKeys.ON)
            if (hintFeatures.condition) builder.set(SonyHintKeys.CONDITION_MODE, SonyHintKeys.ON)
        } catch (e: Exception) {
            Log.w(SonyHintKeys.LOG_TAG, "Sony hint keys rejected", e)
        }
    }

    /** Runs after [handleSubjectResult], so the main face is this frame's. Camera thread. */
    private fun handleHintResult(result: CaptureResult) {
        if (hintFeatures == SonyHintFeatures()) return
        val now = SystemClock.elapsedRealtime()
        val faces = result.get(CaptureResult.STATISTICS_FACES).orEmpty().take(MAX_SUBJECT_FACES)
        val scores = if (hintFeatures.smile) SonySubjectKeys.read(result, SonyHintKeys.SMILE_SCORES) else null
        // Scores come in the driver's face order; the main face is the driver face nearest its kept box.
        val main = keptFaces.firstOrNull { it.id == mainFaceId && it.fromDriver && now - it.seenMs <= FACE_SHOW_MS }?.sensor
        val mainIndex = main?.let { m ->
            faces.indices.minByOrNull { i ->
                val b = faces[i].bounds
                hypot((b.centerX() - (m.left + m.right) / 2).toFloat(), (b.centerY() - (m.top + m.bottom) / 2).toFloat())
            }
        }
        _smileScore.value = mainIndex?.let { scores?.getOrNull(it) }?.let { SmileScore(it, result.frameNumber) }

        val scene = if (hintFeatures.scene) SonySubjectKeys.read(result, SonyHintKeys.SCENE)?.toInt()?.and(0xFF) else null
        val condition = if (hintFeatures.condition) SonySubjectKeys.read(result, SonyHintKeys.CONDITION)?.toInt()?.and(0xFF) else null
        // Macro is the app's own, as in Sony's camera: the lens focused at 14.5 cm or closer.
        val lens = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
        val macro = _state.value.supportsPointAutoFocus && lens != null && lens >= SonyHintKeys.MACRO_DIOPTERS
        val hints = listOfNotNull(
            if (macro) SceneHint.MACRO else scene?.let(SceneHint::ofScene),
            condition?.let(SceneHint::ofCondition),
        )
        // A hint shows once reported for a moment, so a result flickering between two scenes does not blink the icons.
        if (hints != pendingHints) {
            pendingHints = hints
            pendingHintsSinceMs = now
        } else if (now - pendingHintsSinceMs >= SonyHintKeys.HINT_SETTLE_MS) {
            _sceneHints.value = hints
        }

        if (now - lastHintLogMs >= SUBJECT_LOG_MS) {
            lastHintLogMs = now
            Log.d(
                SonyHintKeys.LOG_TAG,
                "faceSmileScores=${scores?.contentToString()} faces=${faces.size} mainIndex=$mainIndex " +
                    "scene=$scene condition=$condition lens=$lens shown=${_sceneHints.value}",
            )
        }
    }

    // ==================== Sony driver faces and object tracking ====================

    private var sonyFeatures = SonySubjectFeatures()
    /** The camera reports face boxes itself (STATISTICS_FACES). */
    val hasDriverFaces: Boolean get() = faceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
    // Camera thread only. 0 = not tracking.
    private var trackingSinceMs = 0L
    private var trackingLostSinceMs = 0L
    private var trackStart: Pair<Float, Float>? = null
    private var trackViewAspectRatio: Float? = null
    private var subjectAfLocked = false
    private var trackBox: ViewRect? = null
    // Area of the first box the driver found; a box grown far past it has usually lost the object.
    private var trackFirstArea = 0f
    private var trackTooBig = false
    private var trackFaceSinceMs = 0L
    private var lastSubjectLogMs = 0L
    private var facesLastSeenMs = 0L
    // Faces matched frame to frame by their centres, so each keeps its id while the driver reorders them.
    // fromDriver is false for a face only the app's detector has seen so far.
    // sensor is the face in camera coordinates, unenlarged: the AF region.
    private class KeptFace(
        val id: Int,
        var box: ViewRect,
        var seenMs: Long,
        var fromDriver: Boolean = true,
        var sensor: CameraCoordinateRect? = null,
    )
    // A tap off the driver's faces: tracking starts unless the app's detector finds a face there first.
    private class TapCheck(val x: Float, val y: Float, val startedMs: Long, val startTracking: Runnable) {
        var trackingStarted = false
    }
    private var tapCheck: TapCheck? = null
    private val keptFaces = mutableListOf<KeptFace>()
    private var nextFaceId = 0
    private var mainFaceId = -1
    private var mainFaceUserChosen = false
    private var rivalFaceId = -1
    private var rivalSinceMs = 0L
    private var driverMismatchSinceMs = 0L
    private var lastFaceSelectMs = 0L
    private var faceAeActive = false
    // The driver's own metering mode, restored when face metering ends.
    private var aeRegionModeBaseline: Byte? = null
    private var afRegionModeBaseline: Byte? = null
    /** Eye focus is on and owns the AF regions. */
    @Volatile
    var eyeFocusOwnsAf = false
    private var subjectAfRegion: CameraCoordinateRect? = null
    private var subjectAfSentMs = 0L
    private var afFocusedMs = 0L
    private var afKickArea = 0f
    private var afKickMs = 0L
    private var afKickPending = false
    // A search the app started (kick, half-press) reads no lock until its own trigger's result: results already in
    // flight still carry the previous lock.
    private var afTriggerAwaited = false
    private var afKickMisses = 0
    // The laser's last reading (-1 none) and zone, [left, right, top, bottom] in percent of the frame.
    private var laserStrength = -1
    private var laserZone: IntArray? = null

    private fun resetSubjectState() {
        trackingSinceMs = 0L
        trackingLostSinceMs = 0L
        trackStart = null
        trackBox = null
        subjectAfLocked = false
        trackFaceSinceMs = 0L
        facesLastSeenMs = 0L
        keptFaces.clear()
        tapCheck?.let { cameraHandler?.removeCallbacks(it.startTracking) }
        tapCheck = null
        halfPressCheck?.let { cameraHandler?.removeCallbacks(it) }
        halfPressCheck = null
        _faceCheckReason.value = null
        mainFaceId = -1
        mainFaceUserChosen = false
        rivalFaceId = -1
        driverMismatchSinceMs = 0L
        faceAeActive = false
        aeRegionModeBaseline = null
        afRegionModeBaseline = null
        subjectAfRegion = null
        afKickPending = false
        _subjectOverlay.value = SubjectOverlay()
    }

    /** Dim light: an AF region smaller than [DIM_AF_REGION_FRACTION] of the visible frame grows around its centre. */
    private fun dimLightAfRegion(
        rect: CameraCoordinateRect,
        visible: CameraCoordinateRect? = mapPreviewMeteringPoint(0.5f, 0.5f, null)?.visibleRegion,
    ): CameraCoordinateRect {
        if (visible == null || _state.value.iso < DIM_AF_ISO) return rect
        val w = maxOf(rect.width, (visible.width * DIM_AF_REGION_FRACTION).toInt())
        val h = maxOf(rect.height, (visible.height * DIM_AF_REGION_FRACTION).toInt())
        if (w == rect.width && h == rect.height) return rect
        val left = ((rect.left + rect.right) / 2 - w / 2).coerceIn(visible.left, maxOf(visible.left, visible.right - w))
        val top = ((rect.top + rect.bottom) / 2 - h / 2).coerceIn(visible.top, maxOf(visible.top, visible.bottom - h))
        return CameraCoordinateRect(left, top, left + w, top + h).intersect(visible) ?: rect
    }

    private fun readLaser(result: CaptureResult) {
        if (!sonyFeatures.laser) return
        val area = SonySubjectKeys.read(result, SonySubjectKeys.TOF_MEASURE_AREA) ?: return
        if (area.size < 5 || area[4] < 0 || area[1] <= area[0] || area[3] <= area[2]) return
        laserStrength = area[4]
        laserZone = area.copyOf(4)
    }

    private fun isLensParked(): Boolean = _state.value.isAutoFocus &&
        previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE) == CaptureRequest.CONTROL_AF_MODE_OFF

    /**
     * A search that failed on a subject inside the laser's zone with no laser return was looking at something beyond
     * about 2 m (a black surface at 30 cm still returns): park the lens at the hyperfocal distance, sharp from about
     * half that distance to infinity, instead of where the sweep gave up. The next tap, search or scene change resumes AF.
     */
    private fun parkLensIfFar(reason: String): Boolean {
        val zone = laserZone
        if (!sonyFeatures.laser || laserStrength != 0 || zone == null || isCaptureFocusFrozen) return false
        val builder = previewRequestBuilder ?: return false
        val region = builder.get(CaptureRequest.CONTROL_AF_REGIONS)?.firstOrNull()?.rect ?: return false
        val characteristics = getActiveOpenCameraCharacteristics() ?: return false
        val array = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return false
        val x = region.exactCenterX() * 100f / array.width()
        val y = region.exactCenterY() * 100f / array.height()
        if (x < zone[0] || x > zone[1] || y < zone[2] || y > zone[3]) return false
        val diopters = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)?.takeIf { it > 0f }
            ?: calculateHyperfocalFocusResult()?.focusDistanceDiopters
            ?: return false
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
        updatePreview()
        Log.i(SonySubjectKeys.LOG_TAG, "laser far: lens parked at $diopters D ($reason, region at $x,$y %)")
        return true
    }

    /** Back to the AF mode the settings ask for, if [parkLensIfFar] parked the lens. */
    private fun unparkLens() {
        if (!isLensParked()) return
        previewRequestBuilder?.let {
            applyFocusSettings(it, _state.value)
            updatePreview()
        }
        Log.i(SonySubjectKeys.LOG_TAG, "lens unparked")
    }

    private fun applySonySubjectKeys(builder: CaptureRequest.Builder) {
        try {
            if (sonyFeatures.objectTracking) {
                builder.set(SonySubjectKeys.OBJECT_SELECT_TRIGGER, SonySubjectKeys.TRIGGER_IDLE)
            }
            if (sonyFeatures.faceAe) {
                builder.set(
                    SonySubjectKeys.AE_REGION_MODE,
                    if (faceAeActive) SonySubjectKeys.AE_REGION_MODE_FACE else aeRegionModeBaseline,
                )
            }
            if (sonyFeatures.laser) builder.set(SonySubjectKeys.TOF_MEASURE_MODE, 1.toByte())
            subjectAfRegion?.let {
                val region = dimLightAfRegion(it)
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(region.toAndroidRect(), MeteringRectangle.METERING_WEIGHT_MAX)))
            }
            if (sonyFeatures.afUserRegion) {
                builder.set(
                    SonySubjectKeys.AF_REGION_MODE,
                    if (subjectAfRegion != null || _state.value.focusPoint != null) SonySubjectKeys.AF_REGION_MODE_USER else afRegionModeBaseline,
                )
            }
        } catch (e: Exception) {
            Log.w(SonySubjectKeys.LOG_TAG, "Sony subject keys rejected", e)
        }
    }

    /** Sends [set] on one request, then resumes the repeating request after [reset], as the AF trigger does. */
    private fun submitOneShot(
        reason: String,
        set: (CaptureRequest.Builder) -> Unit,
        reset: (CaptureRequest.Builder) -> Unit,
    ): Boolean {
        val session = captureSession ?: return false
        val builder = previewRequestBuilder ?: return false
        var submitted = false
        try {
            set(builder)
            session.capture(builder.build(), previewCallback, cameraHandler)
            submitted = true
            Log.i(SonySubjectKeys.LOG_TAG, "sent $reason")
        } catch (e: Exception) {
            Log.w(SonySubjectKeys.LOG_TAG, "failed to send $reason", e)
        } finally {
            reset(builder)
        }
        if (submitted) updatePreview()
        return submitted
    }

    private fun sendSonyTrigger(
        triggerKey: CaptureRequest.Key<Byte>,
        trigger: Byte,
        areaKey: CaptureRequest.Key<IntArray>?,
        point: PreviewMeteringMapping?,
        idle: Byte?,
    ): Boolean = submitOneShot(
        "${triggerKey.name}=$trigger at ${point?.let { "${it.centerX},${it.centerY}" }}",
        set = { builder ->
            builder.set(triggerKey, trigger)
            if (areaKey != null && point != null) {
                builder.set(areaKey, intArrayOf(point.centerX, point.centerY, point.centerX + 1, point.centerY + 1))
            }
        },
        reset = { builder ->
            builder.set(triggerKey, idle)
            if (areaKey != null) builder.set(areaKey, null)
        },
    )

    /**
     * A tap on a face makes it the main face; elsewhere, a camera whose driver tracks objects starts tracking there.
     * Returns false when the tap should focus as before. Called on the main thread.
     */
    private fun selectSubjectAt(normX: Float, normY: Float, viewAspectRatio: Float): Boolean {
        val state = _state.value
        if (state.isCapturing || previewRequestBuilder == null) return false
        val face = _subjectOverlay.value.faces.firstOrNull { it.box.contains(normX, normY) }
        val canTrack = sonyFeatures.objectTracking && state.isAutoFocus && !state.isPortraitMode
        if (face == null && !canTrack) return false
        cameraHandler?.post {
            when {
                face == null -> beginTapCheck(normX, normY, viewAspectRatio)
                face.id == mainFaceId && mainFaceUserChosen -> clearSelection("face")
                else -> selectFace(face, viewAspectRatio)
            }
        } ?: return false
        return true
    }

    private fun beginTapCheck(normX: Float, normY: Float, viewAspectRatio: Float) {
        tapCheck?.let { cameraHandler?.removeCallbacks(it.startTracking) }
        lateinit var check: TapCheck
        check = TapCheck(normX, normY, SystemClock.elapsedRealtime(), Runnable {
            if (tapCheck !== check) return@Runnable
            check.trackingStarted = true
            startTracking(normX, normY, viewAspectRatio)
        })
        tapCheck = check
        _faceCheckReason.value = "tap"
        // The tap stays responsive: tracking starts if the detector has not answered by then.
        cameraHandler?.postDelayed(check.startTracking, TAP_CHECK_WAIT_MS)
    }

    /** Faces the app's detector found in the viewfinder, asked for by [faceCheckReason]. */
    fun onAppFaces(boxes: List<ViewRect>, durationMs: Long, reason: String) {
        val handler = cameraHandler ?: return
        if (Looper.myLooper() != handler.looper) {
            handler.post { onAppFaces(boxes, durationMs, reason) }
            return
        }
        Log.i(SonySubjectKeys.LOG_TAG, "blazeface reason=$reason ms=$durationMs faces=${boxes.size} ${boxes.joinToString(" ")}")
        val now = SystemClock.elapsedRealtime()
        // A face the driver has not found yet stays on screen while the app's detector still sees it.
        keptFaces.firstOrNull { it.id == mainFaceId && !it.fromDriver }?.let { main ->
            boxes.minByOrNull { hypot(it.centerX - main.box.centerX, it.centerY - main.box.centerY) }
                ?.takeIf { hypot(it.centerX - main.box.centerX, it.centerY - main.box.centerY) < maxOf(main.box.width, main.box.height) }
                ?.let { main.box = it; main.seenMs = now; main.sensor = viewRectToSensor(it) }
        }
        halfPressCheck?.let { fallback ->
            cameraHandler?.removeCallbacks(fallback)
            halfPressCheck = null
            val box = boxes.maxByOrNull { it.width * it.height }
            if (box == null) {
                fallback.run()
            } else {
                val face = KeptFace(nextFaceId++, box, now, fromDriver = false, sensor = viewRectToSensor(box))
                keptFaces.add(face)
                mainFaceId = face.id
                mainFaceUserChosen = false
                sendFaceSelect(box, null)
                lockSubjectAf("blazeface face ${face.id}", face.sensor)
            }
        }
        val check = tapCheck
        if (check != null) {
            val face = boxes.firstOrNull { box ->
                val marginX = box.width * TAP_FACE_MARGIN
                val marginY = box.height * TAP_FACE_MARGIN
                ViewRect(box.left - marginX, box.top - marginY, box.right + marginX, box.bottom + marginY).contains(check.x, check.y)
            }
            if (face != null) {
                cameraHandler?.removeCallbacks(check.startTracking)
                tapCheck = null
                selectAppFace(face, now)
                return
            }
            if (!check.trackingStarted) {
                cameraHandler?.removeCallbacks(check.startTracking)
                check.startTracking.run()
            }
            tapCheck = null
        }
        val box = trackBox
        if (trackingSinceMs > 0L && box != null) {
            boxes.firstOrNull { box.contains(it.centerX, it.centerY) }?.let { selectAppFace(it, now) }
        }
    }

    private fun selectAppFace(box: ViewRect, now: Long) {
        val face = KeptFace(nextFaceId++, box, now, fromDriver = false, sensor = viewRectToSensor(box))
        keptFaces.add(face)
        selectFace(SubjectFace(face.id, box), null)
    }

    /** Drops the user's subject: continuous AF on the whole frame and the app's own choice of main face again. */
    private fun clearSelection(kind: String) {
        stopTracking("cleared")
        mainFaceUserChosen = false
        mainFaceId = -1
        rivalFaceId = -1
        unparkLens()
        if (subjectAfRegion != null) {
            subjectAfRegion = null
            afKickPending = false
            previewRequestBuilder?.let {
                clearCustomAfRegions(it)
                applySonySubjectKeys(it)
                updatePreview()
            }
        }
        Log.i(SonySubjectKeys.LOG_TAG, "selection cleared ($kind)")
    }

    private fun selectFace(face: SubjectFace, viewAspectRatio: Float?) {
        stopTracking("face chosen")
        if (_state.value.focusPoint != null) restoreContinuousAf()
        mainFaceId = face.id
        afKickMisses = 0
        mainFaceUserChosen = true
        rivalFaceId = -1
        Log.i(SonySubjectKeys.LOG_TAG, "main face=${face.id} (chosen)")
        sendFaceSelect(face.box, viewAspectRatio)
    }

    private fun sendFaceSelect(box: ViewRect, viewAspectRatio: Float?) {
        if (!sonyFeatures.faceSelect) return
        val point = mapPreviewMeteringPoint(box.centerX, box.centerY, viewAspectRatio) ?: return
        lastFaceSelectMs = SystemClock.elapsedRealtime()
        driverMismatchSinceMs = 0L
        sendSonyTrigger(
            SonySubjectKeys.FACE_SELECT_TRIGGER, SonySubjectKeys.TRIGGER_START,
            SonySubjectKeys.FACE_SELECT_TRIGGER_AREA, point, idle = null,
        )
    }

    private fun startTracking(normX: Float, normY: Float, viewAspectRatio: Float) {
        if (isCaptureFocusFrozen || _state.value.isCapturing) return
        // The driver focuses and meters on the tracked object itself, with continuous AF and no regions.
        // Spot metering moves to the tap at once, so it never falls back to the centre while tracking starts.
        val state = _state.value
        val spot = (normX to normY).takeIf { state.meteringMode == MeteringMode.SPOT && state.supportsPointMetering }
        if (state.focusPoint != null) {
            restoreContinuousAf(spot, viewAspectRatio)
        } else if (spot != null) {
            pointMeteringViewAspectRatio = viewAspectRatio
            _state.update { it.copy(meteringPoint = spot) }
            applyMeteringRegions()
        }
        val builder = previewRequestBuilder ?: return
        val afMode = resolveAutoFocusMode(_state.value.captureMode)
        builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        val point = mapPreviewMeteringPoint(normX, normY, viewAspectRatio) ?: return
        if (trackingSinceMs > 0L) {
            sendSonyTrigger(SonySubjectKeys.OBJECT_SELECT_TRIGGER, SonySubjectKeys.TRIGGER_STOP, null, null, SonySubjectKeys.TRIGGER_IDLE)
        }
        // An AF lock left from an earlier trigger would hold the lens still for the whole track: cancel it here.
        val sent = submitOneShot(
            "objectSelectTrigger=${SonySubjectKeys.TRIGGER_START} at ${point.centerX},${point.centerY} afMode=$afMode +cancel",
            set = {
                it.set(SonySubjectKeys.OBJECT_SELECT_TRIGGER, SonySubjectKeys.TRIGGER_START)
                it.set(
                    SonySubjectKeys.OBJECT_SELECT_TRIGGER_AREA,
                    intArrayOf(point.centerX, point.centerY, point.centerX + 1, point.centerY + 1),
                )
                it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            },
            reset = {
                it.set(SonySubjectKeys.OBJECT_SELECT_TRIGGER, SonySubjectKeys.TRIGGER_IDLE)
                it.set(SonySubjectKeys.OBJECT_SELECT_TRIGGER_AREA, null)
                it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            },
        )
        if (!sent) return
        trackingSinceMs = SystemClock.elapsedRealtime()
        trackingLostSinceMs = 0L
        afKickMisses = 0
        subjectAfLocked = false
        trackStart = normX to normY
        trackViewAspectRatio = viewAspectRatio
        trackBox = null
        trackFirstArea = 0f
        trackTooBig = false
        trackFaceSinceMs = 0L
        if (faceAeActive) setFaceAe(false)
        _subjectOverlay.update { it.copy(tracking = true, trackStart = trackStart, trackBox = null, trackLost = false) }
    }

    /** Tracking found no real object at the tap: focus on the tapped spot as a fixed point instead. */
    private fun trackToTapPoint(point: Pair<Float, Float>, reason: String) {
        if (trackStart != point) return
        val aspect = trackViewAspectRatio
        stopTracking("no object ($reason)")
        subjectAfRegion = null
        afKickPending = false
        focusOnNormalizedPoint(point.first, point.second, FocusPointSource.MANUAL, previewViewAspectRatio = aspect)
    }

    /** Ends tracking, back to plain continuous AF; [reason] goes to the log. */
    fun stopTracking(reason: String) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { stopTracking(reason) }
            return
        }
        if (trackingSinceMs == 0L) return
        trackingSinceMs = 0L
        trackingLostSinceMs = 0L
        trackStart = null
        trackBox = null
        sendSonyTrigger(SonySubjectKeys.OBJECT_SELECT_TRIGGER, SonySubjectKeys.TRIGGER_STOP, null, null, SonySubjectKeys.TRIGGER_IDLE)
        Log.i(SonySubjectKeys.LOG_TAG, "tracking stopped: $reason")
        _subjectOverlay.update { it.copy(tracking = false, trackStart = null, trackBox = null, trackLost = false) }
    }

    private fun setFaceAe(active: Boolean) {
        faceAeActive = active
        previewRequestBuilder?.let {
            applySonySubjectKeys(it)
            updatePreview()
        }
        Log.i(SonySubjectKeys.LOG_TAG, "aeRegionMode=${if (active) SonySubjectKeys.AE_REGION_MODE_FACE else aeRegionModeBaseline}")
    }

    /**
     * The driver's face box is a square on the middle of the face that cuts through forehead and chin. Grown and
     * moved toward the chin (the person's down, from the phone's rotation) it holds the whole face.
     */
    private fun wholeFaceFrame(box: ViewRect): ViewRect {
        val rotation = Math.toRadians(OrientationObserver.captureRotationDegrees.toDouble())
        val centerX = box.centerX + sin(rotation).toFloat() * box.width * FACE_FRAME_CHIN_SHIFT
        val centerY = box.centerY + cos(rotation).toFloat() * box.height * FACE_FRAME_CHIN_SHIFT
        val halfWidth = box.width * FACE_FRAME_SCALE / 2
        val halfHeight = box.height * FACE_FRAME_SCALE / 2
        return ViewRect(
            (centerX - halfWidth).coerceAtLeast(0f), (centerY - halfHeight).coerceAtLeast(0f),
            (centerX + halfWidth).coerceAtMost(1f), (centerY + halfHeight).coerceAtMost(1f),
        )
    }

    /** A viewfinder box in camera coordinates, through the same mapping as a tap. */
    private fun viewRectToSensor(box: ViewRect): CameraCoordinateRect? {
        val a = mapPreviewMeteringPoint(box.left, box.top, null) ?: return null
        val b = mapPreviewMeteringPoint(box.right, box.bottom, null) ?: return null
        return CameraCoordinateRect(
            minOf(a.centerX, b.centerX), minOf(a.centerY, b.centerY), maxOf(a.centerX, b.centerX), maxOf(a.centerY, b.centerY),
        ).takeUnless { it.isEmpty }
    }

    /**
     * Continuous AF on the subject: the driver does not steer AF to its tracker or the chosen face by itself, so the
     * subject becomes the AF region. Cameras without AF regions (the fixed-focus front) never get one.
     */
    private fun updateSubjectAf(
        subject: CameraCoordinateRect?,
        visible: CameraCoordinateRect,
        afState: Int?,
        state: CameraState,
        now: Long,
    ) {
        val builder = previewRequestBuilder ?: return
        // The half-press holds focus where it is.
        if (shutterKeyHold) return
        // A tap point and eye focus set their own AF regions.
        val allowed = maxAfRegions > 0 && state.supportsPointAutoFocus && state.isAutoFocus && !eyeFocusOwnsAf &&
            state.focusPoint == null
        val rect = subject?.intersect(visible)?.takeIf { allowed }
        if (rect == null) {
            if (subjectAfRegion != null) {
                subjectAfRegion = null
                afKickPending = false
                unparkLens()
                if (state.focusPoint == null) clearCustomAfRegions(builder)
                applySonySubjectKeys(builder)
                updatePreview()
                Log.i(SonySubjectKeys.LOG_TAG, "subject AF off, afRegionMode=$afRegionModeBaseline")
            }
            return
        }
        val area = rect.width.toFloat() * rect.height
        val last = subjectAfRegion
        val changed = last == null || builder.get(CaptureRequest.CONTROL_AF_REGIONS).isNullOrEmpty() ||
            (now - subjectAfSentMs >= SUBJECT_AF_MIN_INTERVAL_MS && (
                abs((rect.left + rect.right) - (last.left + last.right)) / 2f > visible.width * SUBJECT_AF_MOVE_FRACTION ||
                    abs((rect.top + rect.bottom) - (last.top + last.bottom)) / 2f > visible.height * SUBJECT_AF_MOVE_FRACTION ||
                    abs(area / (last.width.toFloat() * last.height) - 1f) > SUBJECT_AF_RESIZE_FRACTION
                ))
        if (changed) {
            if (last == null) {
                afFocusedMs = now
                afKickArea = 0f
                afKickMisses = 0
            }
            subjectAfRegion = rect
            subjectAfSentMs = now
            applySonySubjectKeys(builder)
            updatePreview()
            Log.i(
                SonySubjectKeys.LOG_TAG,
                "AF region $rect afRegionMode=${if (sonyFeatures.afUserRegion) SonySubjectKeys.AF_REGION_MODE_USER else "-"}",
            )
        }

        if (afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
            afFocusedMs = now
            afKickMisses = 0
            if (afKickArea == 0f) afKickArea = area
        }
        if (afKickPending) {
            val locked = !afTriggerAwaited && (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
            if (locked || now - afKickMs > AF_KICK_TIMEOUT_MS) {
                afKickPending = false
                afKickArea = area
                afKickMisses = if (locked && afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) 0 else afKickMisses + 1
                if (afKickMisses == AF_KICK_MISS_LIMIT) Log.i(SonySubjectKeys.LOG_TAG, "AF kicks paused after $afKickMisses misses")
                if (afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED && locked &&
                    parkLensIfFar("subject search missed after ${now - afKickMs} ms")
                ) {
                    afFocusedMs = now
                    return
                }
                submitOneShot(
                    "AF cancel after kick: af=$afState after ${now - afKickMs} ms",
                    set = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL) },
                    reset = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE) },
                )
            }
            return
        }
        if (now - afKickMs < AF_KICK_EVERY_MS) return
        val parked = isLensParked()
        if (parked) afFocusedMs = now
        val reason = when {
            afKickArea > 0f && abs(area / afKickArea - 1f) > AF_KICK_RESIZE_FRACTION -> "size ${area / afKickArea}x"
            parked && laserStrength > 0 -> "laser return $laserStrength"
            afKickMisses < AF_KICK_MISS_LIMIT && now - afFocusedMs > AF_KICK_UNFOCUSED_MS -> "unfocused ${now - afFocusedMs} ms"
            else -> return
        }
        if (reason.startsWith("size")) afKickMisses = 0
        unparkLens()
        afKickMs = now
        afTriggerAwaited = true
        afKickPending = submitOneShot(
            "AF kick ($reason) on $rect",
            set = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START) },
            reset = { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE) },
        )
    }

    /** Matches this frame's faces to the kept ones by nearest centre; a face missing a moment keeps its frame. */
    private fun updateKeptFaces(detected: List<Pair<ViewRect, CameraCoordinateRect>>, now: Long) {
        val unmatched = keptFaces.toMutableList()
        for ((box, sensor) in detected) {
            val match = unmatched.minByOrNull { hypot(it.box.centerX - box.centerX, it.box.centerY - box.centerY) }
                ?.takeIf {
                    hypot(it.box.centerX - box.centerX, it.box.centerY - box.centerY) <
                        maxOf(it.box.width, it.box.height, FACE_MATCH_MIN_DISTANCE)
                }
            if (match != null) {
                match.box = box
                match.seenMs = now
                match.fromDriver = true
                match.sensor = sensor
                unmatched.remove(match)
            } else {
                keptFaces.add(KeptFace(nextFaceId++, box, now, sensor = sensor))
            }
        }
        // The main face is remembered longer than it is shown, so a short absence does not hand it to someone else.
        keptFaces.removeAll { now - it.seenMs > if (it.id == mainFaceId) MAIN_FACE_HOLD_MS else FACE_SHOW_MS }
    }

    /**
     * The main face is ours and sticky. One the user chose stays until it has gone or another tap; one the app chose
     * (the largest) also gives way to a clearly larger face.
     */
    private fun updateMainFace(visible: List<KeptFace>, now: Long) {
        if (keptFaces.none { it.id == mainFaceId }) {
            mainFaceId = visible.maxByOrNull { it.box.width * it.box.height }?.id ?: -1
            mainFaceUserChosen = false
            rivalFaceId = -1
            return
        }
        if (mainFaceUserChosen) return
        val main = keptFaces.first { it.id == mainFaceId }
        val rival = visible.filter { it.id != mainFaceId }.maxByOrNull { it.box.width * it.box.height }
        if (rival == null || rival.box.width * rival.box.height <= main.box.width * main.box.height * RIVAL_AREA_RATIO) {
            rivalFaceId = -1
            return
        }
        if (rival.id != rivalFaceId) {
            rivalFaceId = rival.id
            rivalSinceMs = now
        } else if (now - rivalSinceMs >= RIVAL_HOLD_MS) {
            mainFaceId = rival.id
            rivalFaceId = -1
            Log.i(SonySubjectKeys.LOG_TAG, "main face=${rival.id} (larger)")
        }
    }

    private fun handleSubjectResult(result: CaptureResult) {
        if (faceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF && !sonyFeatures.objectTracking) return
        val state = _state.value
        // No trigger may slip between the frames of a shot.
        if (state.isCapturing) return
        val now = SystemClock.elapsedRealtime()
        if (trackingSinceMs > 0L && (state.isPortraitMode || !state.isAutoFocus)) {
            stopTracking(if (state.isPortraitMode) "portrait" else "manual focus")
        }
        val mapping = mapPreviewMeteringPoint(0.5f, 0.5f, null) ?: return

        val raw = result.get(CaptureResult.STATISTICS_FACES).orEmpty().take(MAX_SUBJECT_FACES)
        val detected = raw.mapNotNull { face ->
            val sensor = face.bounds.toCameraCoordinateRect()
            PreviewMeteringMapper.mapRectToView(sensor, mapping)?.let { wholeFaceFrame(it) to sensor }
        }
        if (detected.isNotEmpty()) facesLastSeenMs = now
        updateKeptFaces(detected, now)
        val visible = keptFaces.filter { now - it.seenMs <= FACE_SHOW_MS }
        updateMainFace(visible, now)
        val main = visible.firstOrNull { it.id == mainFaceId }
        onPortraitFaces?.takeIf { state.isPortraitMode }?.invoke(visible.map { it.box }, visible.indexOf(main))

        // The driver picks its own face now and then; keep it focusing on ours.
        val selectArea = if (sonyFeatures.faceSelect) SonySubjectKeys.read(result, SonySubjectKeys.FACE_SELECT_AREA) else null
        if (main != null && selectArea != null && selectArea.size >= 5 && trackingSinceMs == 0L) {
            val driverBox = PreviewMeteringMapper.mapRectToView(
                CameraCoordinateRect(selectArea[0], selectArea[1], selectArea[2], selectArea[3]), mapping,
            )
            val agrees = selectArea[4] != 0 && driverBox != null && main.box.contains(driverBox.centerX, driverBox.centerY)
            if (agrees) {
                driverMismatchSinceMs = 0L
            } else if (driverMismatchSinceMs == 0L) {
                driverMismatchSinceMs = now
            } else if (now - driverMismatchSinceMs >= FACE_RESELECT_AFTER_MS && now - lastFaceSelectMs >= FACE_RESELECT_EVERY_MS) {
                sendFaceSelect(main.box, null)
            }
        }

        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        if (result.request.get(CaptureRequest.CONTROL_AF_TRIGGER) == CaptureRequest.CONTROL_AF_TRIGGER_START ||
            afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN
        ) {
            afTriggerAwaited = false
        }
        val focused = state.supportsPointAutoFocus && (afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
            afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED)

        var objectArea: IntArray? = null
        if (trackingSinceMs > 0L) {
            objectArea = SonySubjectKeys.read(result, SonySubjectKeys.OBJECT_SELECT_AREA)
            val box = objectArea?.takeIf { it.size >= 5 && it[4] == 0 }?.let {
                PreviewMeteringMapper.mapRectToView(CameraCoordinateRect(it[0], it[1], it[2], it[3]), mapping)
            }
            if (box == null) {
                if (trackingLostSinceMs == 0L) trackingLostSinceMs = now
                if (now - trackingLostSinceMs >= TRACK_STOP_MS) stopTracking("lost")
            } else {
                // Any sighting before the stop means the driver found the object again.
                trackingLostSinceMs = 0L
                trackBox = trackBox?.let { old ->
                    fun ease(from: Float, to: Float) = from + (to - from) * TRACK_BOX_FOLLOW
                    ViewRect(ease(old.left, box.left), ease(old.top, box.top), ease(old.right, box.right), ease(old.bottom, box.bottom))
                } ?: box
                val area = box.width * box.height
                if (trackFirstArea == 0f) trackFirstArea = area
                val tooBig = area > trackFirstArea * TRACK_GROWTH_LIMIT
                if (tooBig != trackTooBig) Log.i(SonySubjectKeys.LOG_TAG, "tracked box ${if (tooBig) "grew past" else "back within"} ${TRACK_GROWTH_LIMIT}x: $box")
                trackTooBig = tooBig
                // As on a Sony Alpha, a face found on the tracked object takes over.
                val face = visible.firstOrNull { it.box.contains(box.centerX, box.centerY) || box.contains(it.box.centerX, it.box.centerY) }
                if (face == null) {
                    trackFaceSinceMs = 0L
                } else if (trackFaceSinceMs == 0L) {
                    trackFaceSinceMs = now
                } else if (now - trackFaceSinceMs >= TRACK_FACE_TAKEOVER_MS) {
                    selectFace(SubjectFace(face.id, face.box), null)
                }
            }
            val age = now - trackingSinceMs
            val start = trackStart
            if (start != null && age in TRACK_SETTLE_MS..TRACK_CHECK_MS) {
                val reason = when {
                    box != null && (abs(box.centerX - start.first) > TRACK_DRIFT_LIMIT ||
                        abs(box.centerY - start.second) > TRACK_DRIFT_LIMIT) -> "drifted to ${box.centerX},${box.centerY}"
                    trackingLostSinceMs > 0L && now - trackingLostSinceMs >= TRACK_SETTLE_MS -> "lost"
                    else -> null
                }
                // After this result is handled, so its subject AF does not undo the tap's region.
                if (reason != null) cameraHandler?.post { trackToTapPoint(start, reason) }
            }
        }

        // The tracked object, or else the main face; a lost object keeps its last region until tracking stops.
        val subject = if (trackingSinceMs > 0L) {
            objectArea?.takeIf { it.size >= 5 && it[4] == 0 }?.let { CameraCoordinateRect(it[0], it[1], it[2], it[3]) }
                ?: subjectAfRegion
        } else {
            keptFaces.firstOrNull { it.id == mainFaceId }?.sensor
        }
        if (sonyFeatures.afUserRegion && subjectAfRegion == null && afRegionModeBaseline == null) {
            afRegionModeBaseline = SonySubjectKeys.read(result, SonySubjectKeys.AF_REGION_MODE_RESULT)
                ?.takeIf { it != SonySubjectKeys.AF_REGION_MODE_USER }
        }
        updateSubjectAf(subject, mapping.visibleRegion, afState, state, now)

        // Face metering only in the default metering mode, and never mid-lock: the half-press holds exposure and
        // a tap point waits for the exposure to change.
        if (sonyFeatures.faceAe) {
            if (!faceAeActive && aeRegionModeBaseline == null) {
                aeRegionModeBaseline = SonySubjectKeys.read(result, SonySubjectKeys.AE_REGION_MODE_RESULT)
                    ?.takeIf { it != SonySubjectKeys.AE_REGION_MODE_FACE }
            }
            val wantFaceAe = state.effectiveMeteringMode == MeteringMode.SYSTEM_DEFAULT && state.isAutoExposure &&
                trackingSinceMs == 0L && facesLastSeenMs > 0L && now - facesLastSeenMs <= FACE_AE_HOLD_MS
            if (wantFaceAe != faceAeActive && !shutterKeyHold && !isFocusLockedWaitingForSceneChange) setFaceAe(wantFaceAe)
        }

        val check = tapCheck
        if (check != null && check.trackingStarted && now - check.startedMs > TAP_CHECK_GIVE_UP_MS) tapCheck = null
        if (halfPressOutcomePending && !afTriggerAwaited && (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
        ) {
            halfPressOutcomePending = false
            val focusedLock = afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
            Log.i(SonySubjectKeys.LOG_TAG, "half-press lock: ${if (focusedLock) "focused" else "missed"} after ${now - subjectAfSentMs} ms")
            if (!focusedLock) parkLensIfFar("half-press missed")
        }
        _faceCheckReason.value = when {
            halfPressCheck != null -> "half"
            tapCheck != null -> "tap"
            trackingSinceMs > 0L -> "track"
            keptFaces.any { it.id == mainFaceId && !it.fromDriver } -> "face"
            else -> null
        }

        val lostFor = if (trackingLostSinceMs > 0L) now - trackingLostSinceMs else 0L
        _subjectOverlay.value = SubjectOverlay(
            faces = visible.map { SubjectFace(it.id, it.box) },
            mainFace = main?.id ?: -1,
            focused = focused,
            tracking = trackingSinceMs > 0L,
            trackStart = trackStart,
            trackBox = trackBox,
            trackLost = lostFor >= TRACK_FAINT_MS,
        )

        if (now - lastSubjectLogMs >= SUBJECT_LOG_MS) {
            lastSubjectLogMs = now
            Log.d(
                SonySubjectKeys.LOG_TAG,
                "faces=${raw.size} ${raw.joinToString(" ") { it.bounds.toShortString() }} " +
                    "kept=${visible.joinToString(",") { "${it.id}" }} main=$mainFaceId " +
                    "faceSelect=${selectArea?.contentToString()} " +
                    (if (trackingSinceMs > 0L) "object=${objectArea?.contentToString()} lostMs=$lostFor big=$trackTooBig " else "") +
                    "afMode=${result.get(CaptureResult.CONTROL_AF_MODE)} af=$afState " +
                    "lens=${result.get(CaptureResult.LENS_FOCUS_DISTANCE)} " +
                    "aeMode=${if (sonyFeatures.faceAe) SonySubjectKeys.read(result, SonySubjectKeys.AE_REGION_MODE_RESULT) else "-"} " +
                    "faceAe=$faceAeActive laser=$laserStrength iso=${result.get(CaptureResult.SENSOR_SENSITIVITY)} " +
                    "exp=${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)}"
            )
        }
    }

    private fun isEyeTargetRecentlySeen(): Boolean {
        return _state.value.focusPointSource == FocusPointSource.EYE &&
            eyeTargetLastSeenElapsedMs > 0L &&
            SystemClock.elapsedRealtime() - eyeTargetLastSeenElapsedMs <= EYE_TARGET_RECENT_MS
    }

// ==================== 其他设置 ====================

    /**
     * 设置画面比例
     */
    fun setAspectRatio(ratio: AspectRatio) {
        _state.update { s -> s.copy(aspectRatio = ratio) }
    }

    /**
     * 设置 LUT 启用状态
     */
    // Neither LUT flag shapes the capture session (the preview LUT is applied in GL), so switching
    // them must not restart the camera.
    fun setLutEnabled(enabled: Boolean) {
        if (_state.value.lutEnabled == enabled) return
        _state.update { s -> s.copy(lutEnabled = enabled) }
    }

    fun setLogLutActive(isLogLut: Boolean) {
        if (_state.value.isLogLutActive == isLogLut) return
        _state.update { s -> s.copy(isLogLutActive = isLogLut) }
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (_state.value.captureMode == mode ||
            _state.value.videoRecordingState.isRecording ||
            _state.value.videoRecordingState.isProcessing
        ) {
            return
        }
        _state.update { current ->
            current.copy(
                captureMode = mode,
                videoConfig = if (mode == CaptureMode.VIDEO) {
                    current.videoConfig
                } else {
                    current.videoConfig.copy(logProfile = VideoLogProfile.OFF)
                },
                countdownValue = 0,
                isCapturingLivePhoto = false
            ).let { state ->
                if (mode == CaptureMode.VIDEO) {
                    state.copy(shutterSpeed = coerceManualShutterSpeed(state, state.shutterSpeed))
                } else {
                    state
                }
            }
        }
        if (mode == CaptureMode.VIDEO) {
            livePhotoRecorder.stopRecording()
            refreshVideoCapabilities()
        } else {
            stopVideoRecordingTicker()
            // Like Video above, Photo gets its preview size with the mode change. Otherwise the size only
            // changes once the camera has reopened, the preview view is rebuilt a second time and the
            // camera opens twice (about 0.4 s lost on every switch out of Video).
            val photoCameraId = _state.value.getCurrentCameraInfo()?.getOpenCameraId()
                ?: _state.value.currentCameraId
            val photoPreviewSize = getCameraCharacteristicsOrNull(photoCameraId, "photo preview size")
                ?.let { CameraUtils.getFixedPreviewSize(it, _state.value.aspectRatio) }
            _state.update { s -> s.copy(
                videoRecordingState = VideoRecordingState(),
                currentPreviewSize = photoPreviewSize ?: _state.value.currentPreviewSize,
            ) }
            if (_state.value.useLivePhoto) {
                livePhotoRecorder.startRecording()
            }
        }
    }

    private var resolutionBeforeOpenGate: VideoResolutionPreset? = null

    fun setVideoResolution(resolution: VideoResolutionPreset) {
        val currentConfig = _state.value.videoConfig
        _state.update { s -> s.copy(
            videoConfig = currentConfig.copy(
                resolution = resolution,
            )
        ) }
        refreshVideoCapabilities()
        // a rate this size does not have (120 at 1080p, say) fell back: say so
        if (_state.value.videoConfig.fps != currentConfig.fps) {
            _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(rateNotice = s.slowMotion.rateNotice + 1)) }
        }
    }

    fun setVideoFps(fps: VideoFpsPreset) {
        val currentConfig = _state.value.videoConfig
        _state.update { s -> s.copy(
            videoConfig = currentConfig.copy(
                fps = fps,
            )
        ) }
        refreshVideoCapabilities()
        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    fun setVideoAspectRatio(aspectRatio: VideoAspectRatio) {
        val currentConfig = _state.value.videoConfig
        val enteringOpenGate = aspectRatio == VideoAspectRatio.OPEN_GATE &&
            currentConfig.aspectRatio != VideoAspectRatio.OPEN_GATE
        val leavingOpenGate = aspectRatio != VideoAspectRatio.OPEN_GATE &&
            currentConfig.aspectRatio == VideoAspectRatio.OPEN_GATE
        if (enteringOpenGate) {
            resolutionBeforeOpenGate = currentConfig.resolution
        }
        val resolution = when {
            enteringOpenGate -> VideoResolutionPreset.MAX
            leavingOpenGate && currentConfig.resolution == VideoResolutionPreset.MAX ->
                resolutionBeforeOpenGate ?: currentConfig.resolution
            else -> currentConfig.resolution
        }
        _state.update { s -> s.copy(
            videoConfig = currentConfig.copy(aspectRatio = aspectRatio, resolution = resolution)
        ) }
        refreshVideoCapabilities()
    }

    fun setVideoLogLutMode(mode: VideoLogLutMode) {
        if (_state.value.videoRecordingState.isRecording || _state.value.videoRecordingState.isProcessing) return
        _state.update { s -> s.copy(videoConfig = _state.value.videoConfig.copy(logLutMode = mode)) }
    }

    fun setVideoLogProfile(logProfile: VideoLogProfile) {
        val previousProfile = _state.value.videoConfig.logProfile
        val resolvedProfile = if (_state.value.captureMode == CaptureMode.VIDEO) {
            logProfile
        } else {
            VideoLogProfile.OFF
        }
        _state.update { s -> s.copy(videoConfig = _state.value.videoConfig.copy(logProfile = resolvedProfile)) }
        refreshVideoCapabilities()
        val appliedProfile = _state.value.videoConfig.logProfile
        if (previousProfile != appliedProfile || logProfile != appliedProfile) {
            PLog.d(
                TAG,
                "Video Log selection: requested=${logProfile.name}, previous=${previousProfile.name}, " +
                    "applied=${appliedProfile.name}, mode=${_state.value.captureMode}, " +
                    "lifecycle=$cameraDeviceLifecycle"
            )
        }
        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    fun setVideoStabilizationMode(mode: VideoStabilizationMode) {
        val previousConfig = _state.value.videoConfig
        if (mode == VideoStabilizationMode.ENHANCED &&
            isEnhancedStabilizationUnavailableForCurrentCamera()
        ) {
            PLog.w(
                TAG,
                "Ignoring EIS+ selection because it was already verified unavailable for " +
                    currentStabilizationCameraId()
            )
            return
        }
        when {
            mode == VideoStabilizationMode.ENHANCED &&
                previousConfig.stabilizationMode != VideoStabilizationMode.ENHANCED -> {
                previousVideoStabilizationMode = previousConfig.stabilizationMode
            }

            mode != VideoStabilizationMode.ENHANCED -> {
                previousVideoStabilizationMode = mode
            }
        }
        _state.update { s -> s.copy(
            videoConfig = previousConfig.copy(
                stabilizationMode = mode,
            )
        ) }
        refreshVideoCapabilities()
        val resolvedConfig = _state.value.videoConfig
        if (
            mode == VideoStabilizationMode.ENHANCED &&
            resolvedConfig.stabilizationMode != VideoStabilizationMode.ENHANCED
        ) {
            PLog.e(
                TAG,
                "EIS+ rejected for ${resolvedConfig.resolution.displayName}@" +
                    "${resolvedConfig.fps.fps}: camera does not expose a matching YUV input"
            )
        }
        val outputGeometryChanged = previousConfig.resolution != resolvedConfig.resolution ||
            previousConfig.fps != resolvedConfig.fps
        // Sony's mode changes only with the camera closed: the caller reopens it.
        if (sonyRouteChanges(previousConfig, resolvedConfig)) return
        if (_state.value.captureMode == CaptureMode.VIDEO &&
            !_state.value.videoRecordingState.isRecording &&
            previousConfig.stabilizationMode != resolvedConfig.stabilizationMode &&
            !outputGeometryChanged
        ) {
            createPreviewSession()
            return
        }
        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    fun setOppoSuperStabilizationEnabled(enabled: Boolean) {
        val resolvedEnabled = enabled && DeviceUtil.isOppo
        if (oppoSuperStabilizationEnabled == resolvedEnabled) return
        oppoSuperStabilizationEnabled = resolvedEnabled
        PLog.i(TAG, "OPPO super stabilization enabled=$resolvedEnabled")
        if (_state.value.captureMode == CaptureMode.VIDEO &&
            !_state.value.videoRecordingState.isRecording &&
            cameraDevice != null && previewSurface != null
        ) {
            createPreviewSession(openGeneration = cameraOpenGeneration)
        }
    }

    fun setVideoBitrate(bitrate: VideoBitratePreset) {
        _state.update { s -> s.copy(videoConfig = _state.value.videoConfig.copy(bitrate = bitrate)) }
        refreshVideoCapabilities()
    }

    fun setVideoCodec(codec: com.hinnka.mycamera.video.VideoCodec) {
        _state.update { s -> s.copy(videoConfig = _state.value.videoConfig.copy(codec = codec)) }
        refreshVideoCapabilities()
    }

    fun setVideoAudioInputId(audioInputId: String) {
        val normalizedAudioInputId = audioInputId.ifBlank { VIDEO_AUDIO_INPUT_AUTO }
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(audioInputId = normalizedAudioInputId)
        ) }
        videoRecorder.setPreferredAudioInputId(normalizedAudioInputId)
    }

    fun setVideoEnhancedStabilizationStrength(strength: Float) {
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(
                enhancedStabilizationStrength = normalizeStabilizationStrength(strength)
            )
        ) }
    }

    fun setVideoEnhancedStabilizationLookahead(lookahead: Int) {
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(
                enhancedStabilizationLookahead = normalizeStabilizationLookahead(lookahead)
            )
        ) }
    }

    fun setExternalLensStabilizationConfig(config: ExternalLensStabilizationConfig) {
        realtimeStabilizationCoordinator.setExternalLensStabilizationConfig(config)
    }

    /** Caps the Photo-mode (YUV) capture size of the back cameras; 0 = full sensor size. RAW is always full size. */
    fun setPhotoResolutionCapMp(megapixels: Int) {
        val megapixels = quickCap(megapixels, 12)
        if (_state.value.photoResolutionCapMp == megapixels) return
        _state.update { s -> s.copy(photoResolutionCapMp = megapixels) }
        if (!isFrontCameraOpen()) restartPhotoSession("photo resolution cap: $megapixels MP")
    }

    /** Caps the front camera's Photo-mode capture size; 0 = full sensor size. */
    fun setFrontPhotoResolutionCapMp(megapixels: Int) = updateFrontSession("front photo resolution cap: $megapixels MP") {
        it.copy(frontPhotoResolutionCapMp = quickCap(megapixels, 2))
    }

    /** Front camera "Accurate" colour (see frontStillJpegReader); the JPEG output is part of the session. */
    fun setFrontAccurateColour(enabled: Boolean) = updateFrontSession("front accurate colour: $enabled") {
        it.copy(frontAccurateColour = enabled)
    }

    /** Night on the mode dial: every Photo shot is a stack planned for low light (see planNightCapture). */
    fun setNightMode(enabled: Boolean) = updateFrontSession("night mode: $enabled") {
        it.copy(isNightMode = enabled)
    }

    /** Night's Fast choice: 1/30 s frames on the back camera; on lilac's front camera, no JPEG in the requests. */
    fun setNightFast(fast: Boolean) = updateFrontSession("night fast: $fast") { it.copy(nightFast = fast) }

    /** Night's frame count; 0 = Auto. */
    fun setNightFrameCount(frames: Int) {
        _state.update { s -> s.copy(nightFrameCount = if (frames > 0) MultiFrameConfig.normalizeDenoiseFrameCount(frames) else 0) }
    }

    // The front camera's still JPEG and capture size are part of the session, so a change to either reopens it.
    private fun updateFrontSession(reason: String, change: (CameraState) -> CameraState) {
        val session = { s: CameraState -> s.frontStillJpeg to s.activeFrontPhotoResolutionCapMp }
        val before = session(_state.value)
        _state.update(change)
        if (session(_state.value) != before && isFrontCameraOpen()) restartPhotoSession(reason)
    }

    private fun isFrontCameraOpen(): Boolean =
        _state.value.getCurrentCameraInfo()?.lensFacing == CameraCharacteristics.LENS_FACING_FRONT

    private fun restartPhotoSession(reason: String) {
        // The capture outputs are fixed when the camera opens, so a running YUV camera must reopen.
        val reader = imageReader ?: return
        if (isRawCaptureReader(reader) || _state.value.captureMode != CaptureMode.PHOTO || _state.value.isCapturing) return
        val surfaceTexture = previewSurfaceTexture ?: return
        if (cameraDevice == null) return
        PLog.i(TAG, "Restarting camera for $reason")
        openCamera(surfaceTexture)
    }

    fun setPhotoPreviewStabilizationEnabled(enabled: Boolean) {
        if (enabled && IS_LILAC) {
            // lilac: the extra stabilization stream makes the HAL refuse requests (-38) and close the camera.
            PLog.w(TAG, "Photo preview stabilization blocked on lilac")
            if (_state.value.photoPreviewStabilizationEnabled) setPhotoPreviewStabilizationEnabled(false)
            return
        }
        if (enabled && cameraDevice != null &&
            !realtimeStabilizationCoordinator.isCurrentCameraSupported
        ) {
            if (_state.value.photoPreviewStabilizationEnabled) {
                _state.update { s -> s.copy(
                    photoPreviewStabilizationEnabled = false
                ) }
            }
            PLog.e(TAG, "Photo preview stabilization rejected: current camera is unsupported")
            notifyEnhancedStabilizationUnavailable(
                EnhancedStabilizationFallback.PhotoPreview
            )
            return
        }
        if (_state.value.photoPreviewStabilizationEnabled == enabled) return
        _state.update { s -> s.copy(photoPreviewStabilizationEnabled = enabled) }
        if (_state.value.captureMode == CaptureMode.PHOTO &&
            cameraDevice != null && previewSurface != null
        ) {
            createPreviewSession(openGeneration = cameraOpenGeneration)
            return
        }
        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    fun setVideoRecordingPath(recordingPath: VideoRecordingPath, recordingTreeUri: String? = null) {
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(
                recordingPath = recordingPath,
                recordingTreeUri = recordingTreeUri?.takeIf { it.isNotBlank() }
            )
        ) }
    }

    fun setVideoTorchEnabled(enabled: Boolean) {
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(
                torchEnabled = enabled && _state.value.videoCapabilities.supportsTorch
            )
        ) }
        previewRequestBuilder?.apply {
            applyBaseCameraSettings(this, isCapture = false)
            updatePreview()
        }
    }

    fun setVideoLensLockEnabled(enabled: Boolean) {
        if (_state.value.videoConfig.lensLockEnabled == enabled) return
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(lensLockEnabled = enabled)
        ) }
    }

    fun setVideoWhiteBalanceLockEnabled(enabled: Boolean) {
        if (_state.value.videoConfig.whiteBalanceLockEnabled == enabled) return
        _state.update { s -> s.copy(
            videoConfig = _state.value.videoConfig.copy(whiteBalanceLockEnabled = enabled)
        ) }
        previewRequestBuilder?.apply {
            applyWhiteBalanceSettings(this, _state.value, isCapture = false)
            updatePreview()
        }
    }

    fun setMirrorFrontCameraEnabled(enabled: Boolean) {
        mirrorFrontCameraEnabled = enabled
    }

    fun startVideoRecording(
        creativeLutConfig: LutConfig? = null,
        creativeRecipeParams: ColorRecipeParams? = null,
        orientationOffsetDegrees: Int = 0
    ) {
        if (_state.value.slowMotion.enabled && _state.value.slowMotion.available && !isCurrentCameraFrontFacing()) {
            startSlowMotion(SlowMotionSpec(frames = SLOW_MOTION_DEFAULT_FRAMES))
            return
        }
        if (_state.value.captureMode != CaptureMode.VIDEO ||
            _state.value.videoRecordingState.isRecording ||
            _state.value.videoRecordingState.isProcessing
        ) {
            return
        }

        if (_state.value.videoCapabilities.availableFps.isEmpty()) {
            onCameraError?.invoke(
                -1,
                context.getString(com.hinnka.mycamera.R.string.camera_video_fps_unavailable),
                false,
            )
            return
        }

        val startHeat = CameraHeat.read(context)
        if (startHeat.level == CameraHeat.Level.STOP) {
            PLog.w(TAG, "Not starting video recording: too hot, $startHeat")
            val stopGauge = startHeat.closest?.takeIf { it.fraction >= 1f }
            val message = if (stopGauge != null) {
                context.getString(
                    com.hinnka.mycamera.R.string.video_too_hot_to_start,
                    context.getString(stopGauge.source.label),
                    stopGauge.nowC
                )
            } else {
                context.getString(com.hinnka.mycamera.R.string.video_too_hot_to_start_no_temp)
            }
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }

        videoCaptureStatsWindowStartMs = 0L
        videoCaptureStatsFrames = 0
        videoCaptureStatsFirstTimestampNs = 0L
        val isFrontCamera = isCurrentCameraFrontFacing()
        val sonyMode = enteredSonyMode
        val useEnhancedStabilization = sonyMode == null && shouldUseVideoEnhancedStabilization()
        val cameraInputSize = if (sonyMode != null) {
            Size(sonyMode.width, sonyMode.height)
        } else if (useEnhancedStabilization) {
            _state.value.videoCapabilities.enhancedStabilizationInputSizesByResolution[
                _state.value.videoConfig.resolution
            ]
        } else {
            _state.value.videoCapabilities.cameraInputSizesByResolution[
                _state.value.videoConfig.resolution
            ]
        } ?: _state.value.currentPreviewSize
        val outputSize = _state.value.videoConfig.resolveOutputSize(
            _state.value.videoCapabilities.openGatePortraitAspectRatio,
            cameraInputSize
        )
        val shouldFlipEncodedFrame = isFrontCamera && mirrorFrontCameraEnabled
        val logProfile = _state.value.videoConfig.logProfile
        val monitorOnly = logProfile.isEnabled &&
            _state.value.videoConfig.logLutMode == VideoLogLutMode.MONITOR_ONLY
        val recordingLut = creativeLutConfig?.takeIf {
            !monitorOnly && logProfile.matchesLut(it.curve, it.colorSpace)
        }
        val recordingRecipe = creativeRecipeParams.takeUnless { monitorOnly }
        val colorLayers = buildList {
            if (recordingLut != null || recordingRecipe?.isDefault() == false) {
                add(VideoColorEffectLayer(recordingLut, recordingRecipe))
            }
        }
        PLog.d(
            TAG,
            "Video color output: log=${logProfile.name}, mode=${_state.value.videoConfig.logLutMode}, " +
                "monitorLut=${creativeLutConfig?.title}, recordingLut=${recordingLut?.title}"
        )
        val started = videoRecorder.startRecording(
            size = outputSize,
            cameraInputSize = cameraInputSize,
            fps = _state.value.videoConfig.fps.fps,
            // Sony's rates keep the chosen quality per frame: the bitrate grows with the rate
            bitrateMbps = _state.value.videoConfig.bitrate.bitrateMbps * (sonyMode?.fps ?: 30) / 30,
            codecMime = _state.value.videoConfig.codec.mimeType,
            colorConfig = VideoEncoderColorRequest(
                logProfile = logProfile,
                hasActiveLut = recordingLut != null && (recordingRecipe?.lutIntensity ?: 1f) > 0f
            ),
            colorLayers = colorLayers,
            cameraTimestampSource = if (sonyMode != null) {
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME // Sony's frames carry elapsedRealtimeNanos
            } else {
                getActiveOpenCameraCharacteristics()
                    ?.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
                    ?: CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN
            },
            sensorOrientationDegrees = _state.value.getCurrentCameraInfo()?.sensorOrientation
                ?: cachedSensorOrientation,
            orientationHintDegrees = resolveDedicatedVideoOrientationHintDegrees(orientationOffsetDegrees),
            flipEncodedFrame = shouldFlipEncodedFrame,
            enhancedStabilization = useEnhancedStabilization,
            enhancedStabilizationStrength =
                _state.value.videoConfig.enhancedStabilizationStrength,
            enhancedStabilizationLookahead =
                _state.value.videoConfig.enhancedStabilizationLookahead,
            recordingPath = _state.value.videoConfig.recordingPath,
            recordingTreeUri = _state.value.videoConfig.recordingTreeUri,
            externalFrameInput = sonyMode != null,
            onError = { message ->
                PLog.e(TAG, "Video recording error: $message")
                onCameraError?.invoke(-1, message, false)
            }
        ) { uri ->
            PLog.i(TAG, "Video saved: $uri")
            val completeRecording = Runnable {
                _state.update { s -> s.copy(videoRecordingState = VideoRecordingState()) }
                if (sonyMode != null) {
                    sonyRecording = false
                    // a fresh mode change and session for the next recording, as in every tested run
                    if (cameraDevice != null && enteredSonyMode != null) previewSurfaceTexture?.let { openCamera(it) }
                } else if (cameraDevice != null && previewSurface != null) {
                    createPreviewSession(openGeneration = cameraOpenGeneration)
                }
                onVideoSaved?.invoke(uri)
            }
            cameraHandler?.post(completeRecording) ?: completeRecording.run()
        }
        if (!started) {
            return
        }
        _state.update { s -> s.copy(
            videoRecordingState = VideoRecordingState(isRecording = true, elapsedMs = 0L)
        ) }
        videoRecordingPausedMs = 0L
        if (sonyMode != null) startSonyFrames() else createPreviewSession(openGeneration = cameraOpenGeneration)
    }

    private fun isCurrentCameraFrontFacing(): Boolean {
        return cachedLensFacing == CameraCharacteristics.LENS_FACING_FRONT ||
            _state.value.getCurrentCameraInfo()?.lensType == LensType.FRONT
    }

    // ==================== 960 fps slow motion (lilac, through the Sony bridge companion app) ====================

    private var slowMotion: SlowMotionRecorder? = null
    private var slowMotionSpec: SlowMotionSpec? = null
    private var slowMotionReader: ImageReader? = null
    private var slowMotionTexture: SurfaceTexture? = null
    private var slowMotionCycling = false // the camera is being closed and reopened on purpose
    /**
     * Starts a slow-motion recording: ordinary video, then a 960 fps burst on [triggerSlowMotion], then ordinary
     * video until [stopVideoRecording]. Rear camera of the lilac phone, video mode, camera open. Returns false when
     * it cannot start. While it runs the camera session has the shape Sony's service needs (see [SlowMotionSpec]).
     */
    fun startSlowMotion(spec: SlowMotionSpec): Boolean {
        val state = _state.value
        if (!DeviceQuirks.isLilac || state.captureMode != CaptureMode.VIDEO || slowMotion != null ||
            state.videoRecordingState.isRecording || state.videoRecordingState.isProcessing ||
            cameraDevice == null || previewSurface == null || isCurrentCameraFrontFacing()
        ) {
            PLog.w(TAG, "Slow motion cannot start now")
            return false
        }
        if (CameraHeat.read(context).level == CameraHeat.Level.STOP) {
            PLog.w(TAG, "Not starting slow motion: too hot")
            return false
        }
        val recorder = SlowMotionRecorder(
            context, spec, state.videoConfig.recordingPath, state.videoConfig.recordingTreeUri, channels = 1,
            orientationDegrees = slowMotionOrientationDegrees(),
            listener = object : SlowMotionRecorder.Listener {
                override fun onEntered() {
                    // the service now waits for a camera session of its shape: reopen the camera, its first session is that one
                    cameraHandler?.post {
                        slowMotionCycling = false
                        slowMotionTexture?.let { openCamera(it) }
                    }
                }

                override fun onRecording() {
                    cameraHandler?.post {
                        _state.update { s -> s.copy(
                            videoRecordingState = VideoRecordingState(isRecording = true, elapsedMs = 0L),
                            slowMotion = s.slowMotion.copy(active = true, canTrigger = true)
                        ) }
                        videoRecordingPausedMs = 0L
                        startVideoRecordingTicker()
                    }
                }

                override fun onBurstProgress(frames: Int) {
                    _state.update { st -> st.copy(slowMotion = st.slowMotion.copy(burstPhase = BurstPhase.CAPTURING, burstFrames = frames, burstTotal = spec.frames)) }
                }

                override fun onBurstDone(arrived: Boolean) {
                    PLog.i(TAG, "Slow-motion burst done, arrived=$arrived")
                    cameraHandler?.post {
                        slowMotionBursts++
                        _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(
                            bursts = slowMotionBursts,
                            canTrigger = slowMotionBursts < SlowMotionSpec.MAX_BURSTS,
                            burstPhase = if (arrived) BurstPhase.DONE else BurstPhase.NONE,
                            burstFrames = if (arrived) spec.frames else 0
                        )) }
                        // the check mark stays for a moment
                        cameraHandler?.postDelayed({
                            _state.update { s -> if (s.slowMotion.burstPhase == BurstPhase.DONE) s.copy(slowMotion = s.slowMotion.copy(burstPhase = BurstPhase.NONE)) else s }
                        }, 2000)
                    }
                }

                override fun onFinished(uri: Uri?) {
                    cameraHandler?.post {
                        endSlowMotion()
                        onVideoSaved?.invoke(uri)
                    }
                }

                override fun onError(message: String) {
                    cameraHandler?.post {
                        endSlowMotion()
                        onCameraError?.invoke(-1, message, false)
                    }
                }
            }
        )
        val texture = previewSurfaceTexture ?: return false
        slowMotion = recorder
        slowMotionSpec = spec
        slowMotionBursts = 0
        _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(bursts = 0)) }
        slowMotionTexture = texture
        // Sony's service changes mode only while no camera device is open (with one open, even without a session,
        // the mode change does not return until it is closed): close the camera, change the mode, reopen the camera
        // in the session shape the service expects.
        slowMotionCycling = true
        closeCameraInternal(preserveVideoRecording = false, clearPendingOpen = false)
        whenCameraClosed { recorder.enter() }
        return true
    }

    /**
     * Rotation for the finished file. Sony's service draws the frames in the camera's own orientation (no
     * SurfaceTexture transform as in the normal video path), so the sensor orientation is added to the phone's rotation.
     */
    private fun slowMotionOrientationDegrees(): Int {
        val deviceRotation = OrientationObserver.captureRotationDegrees.toInt()
        val sensor = _state.value.getCurrentCameraInfo()?.sensorOrientation ?: cachedSensorOrientation
        return Math.floorMod(deviceRotation + sensor, 360)
    }

    private fun whenCameraClosed(attemptsLeft: Int = 60, action: () -> Unit) {
        when {
            cameraDeviceLifecycle == CameraDeviceLifecycle.CLOSED -> action()
            attemptsLeft > 0 -> cameraHandler?.postDelayed({ whenCameraClosed(attemptsLeft - 1, action) }, 50)
            else -> {
                PLog.e(TAG, "The camera did not close for slow motion")
                slowMotion?.abort()
                endSlowMotion()
                onCameraError?.invoke(-1, "The camera would not start slow motion", false)
            }
        }
    }

    /** Fires the 960 fps burst of the running slow-motion recording. */
    fun triggerSlowMotion() {
        if (!_state.value.slowMotion.canTrigger) return
        setSlowMotionCanTrigger(false) // until this burst has arrived
        slowMotion?.trigger()
    }

    private fun setSlowMotionCanTrigger(value: Boolean) {
        _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(canTrigger = value)) }
    }

    /** The switch next to resolution and fps; it only takes effect for the next recording. */
    fun setSlowMotionEnabled(enabled: Boolean, becauseOfResolution: Boolean = false) {
        if (_state.value.videoRecordingState.isRecording || _state.value.videoRecordingState.isProcessing) return
        _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(
            enabled = enabled && s.slowMotion.available,
            resolutionNotice = s.slowMotion.resolutionNotice + if (becauseOfResolution) 1 else 0
        )) }
        refreshVideoCapabilities() // slow motion and Sony's high frame rates exclude each other
    }

    /** Asks the companion once whether 960 fps exists here (off the main thread); the switch appears when it does. */
    private fun checkSlowMotionAvailable() {
        if (slowMotionChecked || !DeviceQuirks.isLilac) return
        slowMotionChecked = true
        Thread {
            val caps = SonyBridgeClient.queryCapabilities(context)
            val ok = caps != null && (SlowMotionSpec.VIEWFINDER_WIDTH to SlowMotionSpec.VIEWFINDER_HEIGHT) in caps.sizes
            PLog.i(TAG, "Slow motion available: $ok ($caps)")
            _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(available = ok)) }
            sonyVideoModes = caps?.videoModes.orEmpty()
            // Sony's rates join the frame-rate choices once known
            if (sonyVideoModes.isNotEmpty()) cameraHandler?.post { refreshVideoCapabilities() }
        }.apply { isDaemon = true }.start()
    }

    private var slowMotionChecked = false
    private var slowMotionBursts = 0
    private var darkSmoothedIso = 0f
    private var lastDarkLogMs = 0L

    /** Watches how much light the scene gives: the ISO it would need at 1/960 s, from the viewfinder's own exposure. */
    private fun updateSlowMotionDark(result: TotalCaptureResult) {
        val sm = _state.value.slowMotion
        if (!sm.available || !sm.enabled) return
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
        val needed = iso * exposureNs / SlowMotionSpec(frames = 91).exposureLimitNs.toFloat()
        darkSmoothedIso = if (darkSmoothedIso == 0f) needed else darkSmoothedIso * 0.9f + needed * 0.1f
        if (SystemClock.elapsedRealtime() - lastDarkLogMs > 1000) {
            lastDarkLogMs = SystemClock.elapsedRealtime()
            PLog.d(TAG, "Slow-motion light: iso=$iso exposure=${exposureNs / 1000} us, needs ISO ${darkSmoothedIso.toInt()} at 1/960 s")
        }
        val dark = if (sm.dark) darkSmoothedIso > SLOW_MOTION_DARK_ISO * 0.8f else darkSmoothedIso > SLOW_MOTION_DARK_ISO
        if (dark != sm.dark) _state.update { s -> s.copy(slowMotion = s.slowMotion.copy(dark = dark)) }
    }

    private fun stopSlowMotion() {
        stopVideoRecordingTicker()
        _state.update { s -> s.copy(videoRecordingState = s.videoRecordingState.copy(isRecording = false, isProcessing = true), slowMotion = s.slowMotion.copy(canTrigger = false)) }
        slowMotion?.stop()
    }

    /** Camera closing or screen left before a normal stop: give Sony's service back at once, no file is saved. */
    private fun abortSlowMotion() {
        slowMotion?.abort()
        endSlowMotion(reopen = false)
    }

    /** Back to the ordinary video session once slow motion is over (saved, failed or aborted). */
    private fun endSlowMotion(reopen: Boolean = true) {
        val reader = slowMotionReader
        slowMotion = null
        slowMotionSpec = null
        slowMotionReader = null
        slowMotionTexture = null
        slowMotionCycling = false
        stopVideoRecordingTicker()
        _state.update { s -> s.copy(videoRecordingState = VideoRecordingState(), slowMotion = s.slowMotion.copy(active = false, canTrigger = false, burstPhase = BurstPhase.NONE)) }
        previewSurfaceTexture?.setDefaultBufferSize(previewBufferSize.width, previewBufferSize.height)
        if (reopen && cameraDevice != null && previewSurface != null) {
            createPreviewSession(openGeneration = cameraOpenGeneration)
        }
        // the old session is closed by the new one; the reader goes after it
        cameraHandler?.post { reader?.close() } ?: reader?.close()
    }

    /**
     * The only session shape Sony's service accepts: a 1280x720 viewfinder and a 1280x720 YUV reader, nothing else,
     * opened in vendor mode 0x8000, with the exposure capped to one frame of the 960 fps capture.
     */
    private fun createSlowMotionSession(device: CameraDevice, surface: Surface, handler: Handler, openGeneration: Long) {
        val spec = slowMotionSpec ?: return
        val sessionGeneration = ++previewSessionGeneration
        pendingVendorSessionParameterRestart = false
        previewSurfaceTexture?.setDefaultBufferSize(SlowMotionSpec.VIEWFINDER_WIDTH, SlowMotionSpec.VIEWFINDER_HEIGHT)
        val reader = slowMotionReader ?: ImageReader.newInstance(
            SlowMotionSpec.VIEWFINDER_WIDTH, SlowMotionSpec.VIEWFINDER_HEIGHT, ImageFormat.YUV_420_888, 2
        ).also { r ->
            r.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
            slowMotionReader = r
        }
        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.Key(SlowMotionSpec.EXPOSURE_LIMIT_KEY, Long::class.javaObjectType), spec.exposureLimitNs)
            }
            PLog.i(TAG, "Creating slow-motion session: mode 0x${SlowMotionSpec.SESSION_MODE.toString(16)}, exposure cap ${spec.exposureLimitNs} ns")
            device.createCaptureSession(SessionConfiguration(
                SlowMotionSpec.SESSION_MODE,
                listOf(createOutputConfiguration(surface), createOutputConfiguration(reader.surface)),
                Executor { handler.post(it) },
                object : CameraCaptureSession.StateCallback() {
                    override fun onClosed(session: CameraCaptureSession) {
                        invalidateCaptureSession(session, "slow-motion session closed")
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
                            safeCloseCaptureSession(session, "stale slow-motion session")
                            return
                        }
                        onSessionConfigured(session, openGeneration, sessionGeneration)
                        // the service wants a settled viewfinder before the recording starts
                        handler.postDelayed({
                            if (sessionGeneration == previewSessionGeneration) slowMotion?.startRecording()
                        }, SlowMotionSpec.VIEWFINDER_SETTLE_MS)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        PLog.e(TAG, "Slow-motion session configuration failed")
                        safeCloseCaptureSession(session, "slow-motion configure failure")
                        slowMotion?.abort()
                        endSlowMotion()
                        onCameraError?.invoke(-1, "The camera would not start slow motion", false)
                    }
                }
            ))
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to create the slow-motion session", e)
            slowMotion?.abort()
            endSlowMotion()
            onCameraError?.invoke(-1, "The camera would not start slow motion", false)
        }
    }

    // ==================== Sony's video route (lilac: 60 and 120 fps through the Sony bridge) ====================

    private var stabiliseNoticeSerial = 0

    /** Sony's video modes the bridge reported (version 3 and later). */
    @Volatile private var sonyVideoModes: List<SonyVideoMode> = emptyList()
    private var sonyVideoBridge: SonyBridgeClient? = null
    /** The mode the bridge entered for the camera now opening or open; its session then has Sony's shape. */
    private var enteredSonyMode: SonyVideoMode? = null
    private var sonyVideoReader: ImageReader? = null
    /** The Sony mode of the last camera session, null when it was an ordinary one: decides one mode change or two. */
    private var lastSonySessionMode: SonyVideoMode? = null

    /** True when going from [before] to [after] enters, leaves or changes Sony's video mode. */
    fun sonyRouteChanges(before: VideoConfig, after: VideoConfig): Boolean =
        before.stabilizationMode != after.stabilizationMode &&
            (before.stabilizationMode.sonyMode != null || after.stabilizationMode.sonyMode != null)

    /** The mode the current video settings need, or null for the camera's own route. */
    private fun wantedSonyVideoMode(): SonyVideoMode? {
        val state = _state.value
        if (!DeviceQuirks.isLilac || state.captureMode != CaptureMode.VIDEO || slowMotion != null ||
            state.slowMotion.enabled || isCurrentCameraFrontFacing()
        ) return null
        val fps = state.videoConfig.fps
        val mode = state.videoConfig.stabilizationMode.sonyMode
            ?: SonyVideoMode.NORMAL.takeIf { fps in state.videoCapabilities.sonyRouteFps }
            ?: return null
        val resolution = state.videoConfig.resolution
        return sonyVideoModes.firstOrNull {
            it.mode == mode && it.fps == fps.fps &&
                maxOf(it.width, it.height) == resolution.longEdge && minOf(it.width, it.height) == resolution.shortEdge
        }
    }

    /**
     * Runs while no camera device is open, just before one opens: Sony's service changes mode only then. Enters the
     * mode the video settings need; if the bridge refuses, the rate falls back to 30 and the screen says so.
     */
    private fun prepareSonyVideoMode() {
        enteredSonyMode = null
        val wanted = wantedSonyVideoMode() ?: return
        val client = sonyVideoBridge ?: SonyBridgeClient(context) {
            PLog.w(TAG, "Sony bridge died while in a Sony video mode")
            cameraHandler?.post { if (enteredSonyMode != null) onSonyVideoFailed() }
        }.also { sonyVideoBridge = it }
        val started = SystemClock.elapsedRealtime()
        val result = try {
            if (!client.connect() || client.version < SonyBridgeClient.VIDEO_MODES_SINCE) -100 else {
                val bridge = client.bridge ?: throw IllegalStateException("bridge gone")
                // Sony's camera software takes its streaming rate from the previous Sony mode change; an ordinary camera
                // session in between resets it to 30 fps. So the mode is changed twice (the second change costs about
                // 200 ms) unless the last session was Sony's at this same mode.
                var r = 0
                repeat(if (lastSonySessionMode == wanted) 1 else 2) {
                    if (r == 0) r = bridge.open(0).takeIf { it != 0 } ?: bridge.enterVideoMode(
                        wanted.mode, SonyVideoMode.PREVIEW_WIDTH, SonyVideoMode.PREVIEW_HEIGHT, wanted.width, wanted.height, wanted.fps
                    )
                }
                r
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Sony video mode change failed", e)
            -99
        }
        PLog.i(TAG, "Sony video mode $wanted entered: result=$result in ${SystemClock.elapsedRealtime() - started} ms")
        if (result == 0) {
            enteredSonyMode = wanted
        } else {
            releaseSonyVideoMode()
            fallBackFromSonyRate()
        }
    }

    /** A recording through Sony's route is running: its frames come from the bridge, not from the camera session. */
    @Volatile private var sonyRecording = false

    private val sonyFrameSink = object : IFrameSink.Stub() {
        override fun onFrame(buffer: HardwareBuffer, timestampNs: Long, frameId: Int) {
            lastSonyFrameNs = timestampNs
            val bridge = sonyVideoBridge?.bridge
            videoRecorder.submitExternalFrame(buffer, timestampNs) {
                buffer.close()
                runCatching { bridge?.releaseFrame(frameId) }
            }
        }
    }

    /** Sony's prepare and start wait for the service's callbacks (up to seconds): off the camera thread. */
    private fun startSonyFrames() {
        sonyRecording = true
        Thread({
            val result = try {
                sonyVideoBridge?.bridge?.startFrames(sonyFrameSink) ?: -100
            } catch (e: Exception) {
                PLog.e(TAG, "Sony frames did not start", e)
                -99
            }
            PLog.i(TAG, "Sony frames started: result=$result")
            cameraHandler?.post {
                if (result == 0 && videoRecorder.onCameraInputStarted()) {
                    startVideoRecordingTicker()
                } else {
                    sonyRecording = false
                    videoRecorder.forceStop()
                    _state.update { s -> s.copy(videoRecordingState = VideoRecordingState()) }
                    onCameraError?.invoke(-1, "Sony's video route did not start", false)
                }
            }
        }, "sony-frames-start").start()
    }

    /** Sensor time of the newest frame from Sony's route (same clock as elapsedRealtimeNanos). */
    @Volatile private var lastSonyFrameNs = 0L

    /**
     * The file ends at the press. Intelligent Active hands each frame over about 1.5 s after it was taken, so Sony's
     * stop waits until a frame from the press time has arrived (or 2.5 s); then Sony's stop, then the file.
     */
    private fun stopSonyRecording() {
        val pressNs = SystemClock.elapsedRealtimeNanos()
        videoRecorder.endAt(pressNs)
        stopVideoRecordingTicker()
        _state.update { s -> s.copy(
            videoRecordingState = s.videoRecordingState.copy(isRecording = false, isPaused = false, isProcessing = true)
        ) }
        val bridge = sonyVideoBridge?.bridge
        Thread({
            val waitUntil = SystemClock.elapsedRealtime() + 2_500
            while (lastSonyFrameNs < pressNs && SystemClock.elapsedRealtime() < waitUntil) Thread.sleep(10)
            PLog.i(TAG, "Sony tail waited: newest frame ${(lastSonyFrameNs - pressNs) / 1_000_000} ms after the press")
            val result = runCatching { bridge?.stopFrames() }.getOrElse { PLog.e(TAG, "Sony frames stop failed", it); -99 }
            PLog.i(TAG, "Sony frames stopped: result=$result")
            videoRecorder.stopRecording()
        }, "sony-frames-stop").start()
    }

    /** Gives Sony's native session back; only with the camera closed (Sony's order). Slow motion keeps its own. */
    private fun releaseSonyVideoMode() {
        enteredSonyMode = null
        val client = sonyVideoBridge ?: return
        if (slowMotion != null) return
        runCatching { client.bridge?.release() }
        sonyVideoReader?.let { reader -> cameraHandler?.post { reader.close() } ?: reader.close() }
        sonyVideoReader = null
    }

    /**
     * The settings ask for a Sony mode that cannot run now: a Sony stabiliser goes off, a Sony rate goes back to
     * 30 fps, with a notice.
     */
    private fun fallBackFromSonyRate() {
        val config = _state.value.videoConfig
        if (config.stabilizationMode.sonyMode != null) {
            _state.update { s -> s.copy(
                videoConfig = config.copy(stabilizationMode = VideoStabilizationMode.OFF),
                stabiliseNotice = StabiliseNotice(
                    config.stabilizationMode, VideoStabilizationMode.OFF, config.resolution, config.fps.fps,
                    failed = true, serial = ++stabiliseNoticeSerial,
                ),
            ) }
        } else {
            _state.update { s -> s.copy(
                videoConfig = config.copy(fps = VideoFpsPreset.FPS_30),
                slowMotion = s.slowMotion.copy(rateNotice = s.slowMotion.rateNotice + 1),
            ) }
        }
        refreshVideoCapabilities()
    }

    /** The bridge or Sony's session failed while in a Sony mode: reopen at 30 fps. */
    private fun onSonyVideoFailed() {
        fallBackFromSonyRate()
        previewSurfaceTexture?.let { openCamera(it) }
    }

    /**
     * Sony's video session shape: vendor mode 0x8003, a 1280x720 viewfinder and a one-buffer 1280x720 YUV reader,
     * TEMPLATE_RECORD at 30 fps. Sony's service draws the recording itself, from its mode change.
     */
    private fun createSonyVideoSession(device: CameraDevice, surface: Surface, handler: Handler, openGeneration: Long) {
        val sessionGeneration = ++previewSessionGeneration
        pendingVendorSessionParameterRestart = false
        previewSurfaceTexture?.setDefaultBufferSize(SonyVideoMode.PREVIEW_WIDTH, SonyVideoMode.PREVIEW_HEIGHT)
        val reader = sonyVideoReader ?: ImageReader.newInstance(
            SonyVideoMode.PREVIEW_WIDTH, SonyVideoMode.PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 1
        ).also { r ->
            r.setOnImageAvailableListener({ it.acquireNextImage()?.close() }, handler) // one buffer: latest needs two
            sonyVideoReader = r
        }
        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(30, 30))
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                if (_state.value.videoConfig.torchEnabled) set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
            PLog.i(TAG, "Creating Sony video session for $enteredSonyMode: mode 0x${SonyVideoMode.SESSION_MODE.toString(16)}")
            device.createCaptureSession(SessionConfiguration(
                SonyVideoMode.SESSION_MODE,
                listOf(createOutputConfiguration(surface), createOutputConfiguration(reader.surface)),
                Executor { handler.post(it) },
                object : CameraCaptureSession.StateCallback() {
                    override fun onClosed(session: CameraCaptureSession) {
                        invalidateCaptureSession(session, "Sony video session closed")
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        if (openGeneration != cameraOpenGeneration || sessionGeneration != previewSessionGeneration) {
                            safeCloseCaptureSession(session, "stale Sony video session")
                            return
                        }
                        onSessionConfigured(session, openGeneration, sessionGeneration)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        PLog.e(TAG, "Sony video session configuration failed")
                        safeCloseCaptureSession(session, "Sony video configure failure")
                        onSonyVideoFailed()
                    }
                }
            ))
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to create the Sony video session", e)
            onSonyVideoFailed()
        }
    }

    /**
     * Debug builds on lilac: `adb shell am broadcast -a io.github.lilaccamera.SLOWMO -p <package> --es cmd start`
     * (extras `--ei frames 91`, `--ei width 1280 --ei height 720`), then `--es cmd trigger`, then `--es cmd stop`.
     * Needs the camera open in video mode.
     */
    private fun registerSlowMotionDebugReceiver() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                when (intent.getStringExtra("cmd")) {
                    "start" -> cameraHandler?.post {
                        startSlowMotion(SlowMotionSpec(
                            intent.getIntExtra("width", 1280), intent.getIntExtra("height", 720), intent.getIntExtra("frames", 91)
                        ))
                    }
                    "trigger" -> triggerSlowMotion()
                    "stop" -> cameraHandler?.post { stopVideoRecording() }
                }
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter("io.github.lilaccamera.SLOWMO"), Context.RECEIVER_EXPORTED)
        slowMotionDebugReceiver = receiver
    }

    private fun resolveVideoSessionOperatingMode(): Int {
        if (!oppoSuperStabilizationEnabled || !DeviceUtil.isOppo) {
            return SessionConfiguration.SESSION_REGULAR
        }
        return if (isCurrentCameraFrontFacing()) {
            OPPO_SUPER_STABILIZATION_FRONT_SESSION_MODE
        } else {
            OPPO_SUPER_STABILIZATION_REAR_SESSION_MODE
        }
    }

    private fun shouldUseVideoEnhancedStabilization(): Boolean {
        val state = _state.value
        return state.videoConfig.stabilizationMode == VideoStabilizationMode.ENHANCED &&
            state.captureMode == CaptureMode.VIDEO &&
            VideoStabilizationMode.ENHANCED in
                state.videoCapabilities.availableStabilizationModes &&
            !isCurrentCameraFrontFacing() &&
            realtimeStabilizationCoordinator.isCurrentCameraSupported
    }

    private fun shouldUseAlgorithmicStabilization(): Boolean {
        val state = _state.value
        return when (state.captureMode) {
            CaptureMode.PHOTO -> state.photoPreviewStabilizationEnabled &&
                realtimeStabilizationCoordinator.isCurrentCameraSupported
            CaptureMode.VIDEO -> shouldUseVideoEnhancedStabilization()
        }
    }

    private fun currentStabilizationCameraId(): String {
        return activeOutputPhysicalCameraId
            ?.takeIf { it.isNotEmpty() }
            ?: getActiveOpenCameraId()
    }

    private fun isEnhancedStabilizationUnavailableForCurrentCamera(): Boolean {
        return currentStabilizationCameraId() in enhancedStabilizationUnavailableCameraIds
    }

    private fun resolvePreviousVideoStabilizationMode(
        availableModes: List<VideoStabilizationMode>
    ): VideoStabilizationMode {
        return previousVideoStabilizationMode.takeIf { mode ->
            mode != VideoStabilizationMode.ENHANCED && mode in availableModes
        } ?: when {
            VideoStabilizationMode.OIS in availableModes -> VideoStabilizationMode.OIS
            VideoStabilizationMode.EIS in availableModes -> VideoStabilizationMode.EIS
            else -> VideoStabilizationMode.OFF
        }
    }

    private fun preferredHardwareVideoStabilizationMode(): VideoStabilizationMode {
        val availableModes = _state.value.videoCapabilities.availableStabilizationModes
        return when {
            VideoStabilizationMode.OIS in availableModes -> VideoStabilizationMode.OIS
            VideoStabilizationMode.EIS in availableModes -> VideoStabilizationMode.EIS
            else -> VideoStabilizationMode.OFF
        }
    }

    fun pauseVideoRecording() {
        if (!_state.value.videoRecordingState.isRecording || _state.value.videoRecordingState.isPaused) return
        videoRecorder.pauseRecording()
        if (videoRecorder.usesDedicatedCameraInput && !sonyRecording) {
            updateDedicatedVideoRepeatingTarget(includeVideoSurface = false)
        }
        videoRecordingPauseStartElapsedMs = SystemClock.elapsedRealtime()
        _state.update { s -> s.copy(
            videoRecordingState = _state.value.videoRecordingState.copy(isPaused = true)
        ) }
    }

    fun resumeVideoRecording() {
        if (!_state.value.videoRecordingState.isRecording || !_state.value.videoRecordingState.isPaused) return
        if (videoRecorder.usesDedicatedCameraInput && !sonyRecording &&
            !updateDedicatedVideoRepeatingTarget(includeVideoSurface = true)
        ) {
            PLog.e(TAG, "Cannot resume dedicated video input because the recording surface is unavailable")
            return
        }
        videoRecorder.resumeRecording()
        videoRecordingPausedMs += SystemClock.elapsedRealtime() - videoRecordingPauseStartElapsedMs
        _state.update { s -> s.copy(
            videoRecordingState = _state.value.videoRecordingState.copy(isPaused = false)
        ) }
    }

    fun stopVideoRecording() {
        if (!_state.value.videoRecordingState.isRecording) return
        if (slowMotion != null) {
            stopSlowMotion()
            return
        }
        if (sonyRecording) {
            stopSonyRecording()
            return
        }
        videoCaptureStatsWindowStartMs = 0L
        videoCaptureStatsFrames = 0
        videoCaptureStatsFirstTimestampNs = 0L
        stopVideoRecordingTicker()
        _state.update { s -> s.copy(
            videoRecordingState = _state.value.videoRecordingState.copy(
                isRecording = false,
                isPaused = false,
                isProcessing = videoRecorder.usesDedicatedCameraInput
            )
        ) }
        if (videoRecorder.usesDedicatedCameraInput && !_state.value.videoRecordingState.isPaused) {
            updateDedicatedVideoRepeatingTarget(includeVideoSurface = false)
        } else {
            previewRequestBuilder?.apply {
                applyWhiteBalanceSettings(this, _state.value, isCapture = false)
                updatePreview()
            }
        }
        videoRecorder.stopRecording()
        if (pendingVendorSessionParameterRestart && cameraDevice != null && previewSurface != null) {
            PLog.d(TAG, "Restarting preview session after recording for pending vendor session parameter change")
            createPreviewSession(openGeneration = cameraOpenGeneration)
        }
    }

    private fun updateDedicatedVideoRepeatingTarget(includeVideoSurface: Boolean): Boolean {
        val device = cameraDevice ?: return false
        val session = captureSession ?: return false
        val previewTarget = previewSurface ?: return false
        val encoderTarget = videoRecorder.cameraInputSurface
        if (includeVideoSurface && encoderTarget == null) return false

        return try {
            val useEnhancedStabilization = shouldUseVideoEnhancedStabilization()
            val templateType = if (useEnhancedStabilization) {
                CameraDevice.TEMPLATE_PREVIEW
            } else {
                CameraDevice.TEMPLATE_RECORD
            }
            PLog.i(
                TAG,
                "Updating dedicated video repeating request: " +
                    "template=${if (templateType == CameraDevice.TEMPLATE_PREVIEW) "PREVIEW" else "RECORD"}, " +
                    "enhancedStabilization=$useEnhancedStabilization, " +
                    "includeVideoSurface=$includeVideoSurface"
            )
            val builder = device.createCaptureRequest(templateType).apply {
                addTarget(previewTarget)
                stabilizationImageReader?.surface
                    ?.takeIf { useEnhancedStabilization }
                    ?.let(::addTarget)
                encoderTarget
                    ?.takeIf { includeVideoSurface && !useEnhancedStabilization }
                    ?.let(::addTarget)
                applyBaseCameraSettings(this, isCapture = false)
            }
            previewRequestBuilder = builder
            session.setRepeatingRequest(builder.build(), previewCallback, cameraHandler)
            true
        } catch (e: Exception) {
            PLog.e(
                TAG,
                "Failed to ${if (includeVideoSurface) "attach" else "detach"} dedicated video target",
                e
            )
            false
        }
    }

    private fun resolveDedicatedVideoOrientationHintDegrees(orientationOffsetDegrees: Int): Int {
        // UI 在反向竖屏时保持 0°，录像必须使用包含 180° 的拍摄方向。
        val captureRotationDegrees = OrientationObserver.captureRotationDegrees.toInt()
        val orientationHintDegrees = resolveSurfaceTextureVideoOrientationDegrees(
            deviceRotationDegrees = captureRotationDegrees,
            calibrationOffsetDegrees = orientationOffsetDegrees,
        )
        PLog.d(
            TAG,
            "Video orientation: capture=$captureRotationDegrees, " +
                "calibrationOffset=$orientationOffsetDegrees, orientationHint=$orientationHintDegrees"
        )
        return orientationHintDegrees
    }

// ==================== 延时拍摄和网格线 ====================

    /**
     * 设置延时拍摄秒数
     */
    fun setTimerSeconds(seconds: Int) {
        _state.update { s -> s.copy(timerSeconds = seconds) }
    }

    /**
     * 设置倒计时值（用于UI显示）
     */
    fun setCountdownValue(value: Int) {
        _state.update { s -> s.copy(countdownValue = value) }
    }

    /**
     * 设置是否显示网格线
     */
    fun setShowGrid(show: Boolean) {
        _state.update { s -> s.copy(showGrid = show) }
    }

    fun setGridStyle(style: GridStyle) {
        _state.update { s -> s.copy(gridStyle = style) }
    }

    fun setRawCfaCorrectionModes(modes: Map<String, String>) {
        _state.update { s -> s.copy(rawCfaCorrectionModes = modes.toMap()) }
    }

    /**
     * Another app's photo is a quick one: always stacked, whatever the settings say, and at most
     * 12 MP from the back cameras and 2 MP from the front.
     */
    var quickCapture = false

    private fun quickCap(megapixels: Int, limit: Int): Int = when {
        !quickCapture -> megapixels
        megapixels == 0 -> limit
        else -> minOf(megapixels, limit)
    }

    fun setMultiFrameOutputScale(outputScale: Float?) {
        val currentState = _state.value
        val normalizedScale = (outputScale ?: MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE.takeIf { quickCapture })
            ?.let(MultiFrameConfig::normalizeOutputScale)
        _state.update { s -> s.copy(
            multiFrameOutputScale = normalizedScale,
        ) }
    }

    fun setUseMultipleExposure(useMultipleExposure: Boolean) {
        _state.update { s -> s.copy(useMultipleExposure = useMultipleExposure) }
    }

    fun onHdrBracketFramesCollected() {
        _state.update { s -> s.copy(
            hdrBracketCapturing = false,
            hdrBracketFrameCount = 0
        ) }
    }

    fun setJpgMultiFrameDenoiseFrameCount(frameCount: Int) {
        _state.update { s -> s.copy(
            jpgMultiFrameDenoiseFrameCount = MultiFrameConfig.normalizeDenoiseFrameCount(frameCount),
        ) }
    }

    fun setHdrPlusMergeMode(mode: MgcRawMaxMode) {
        val currentState = _state.value
        _state.update { s -> s.copy(
            hdrPlusMergeMode = mode,
            hdrPlusBracketExposureEnabled = currentState.hdrPlusBracketExposureEnabled &&
                mode.supportsBracketExposure,
        ) }
    }

    fun setHdrPlusFrameCount(frameCount: Int) {
        val currentState = _state.value
        _state.update { s -> s.copy(
            hdrPlusFrameCount = MultiFrameConfig.normalizeHdrPlusFrameCount(
                frameCount,
                bracketExposureEnabled = currentState.hdrPlusMergeMode.supportsBracketExposure &&
                    currentState.hdrPlusBracketExposureEnabled,
            ),
        ) }
    }

    fun setUseJpgMaxHdrComposition(enabled: Boolean) {
        _state.update { s -> s.copy(useJpgMaxHdrComposition = enabled) }
    }

    fun setHdrPlusBracketExposureEnabled(enabled: Boolean) {
        val currentState = _state.value
        val bracketExposureEnabled = enabled && currentState.hdrPlusMergeMode.supportsBracketExposure
        _state.update { s -> s.copy(
            hdrPlusBracketExposureEnabled = bracketExposureEnabled,
            hdrPlusFrameCount = MultiFrameConfig.normalizeHdrPlusFrameCount(
                currentState.hdrPlusFrameCount,
                bracketExposureEnabled = bracketExposureEnabled,
            ),
        ) }
    }

    fun setHdrPlusLongFrameExposureEv(value: Float) {
        _state.update { s -> s.copy(
            hdrPlusLongFrameExposureEv = MultiFrameConfig.normalizeLongFrameExposureEv(value),
        ) }
    }

    fun setHdrPlusShortFrameExposureEv(value: Float) {
        _state.update { s -> s.copy(
            hdrPlusShortFrameExposureEv = MultiFrameConfig.normalizeShortFrameExposureEv(value),
        ) }
    }


    fun setCapturingLivePhoto(enabled: Boolean) {
        _state.update { s -> s.copy(isCapturingLivePhoto = enabled) }
    }

    fun setApplyUltraHDR(enabled: Boolean) {
        _state.update { s -> s.copy(applyUltraHDR = enabled) }
    }

// ==================== 拍照 ====================

    private fun supportsAfTrigger(afMode: Int): Boolean {
        return afMode == CaptureRequest.CONTROL_AF_MODE_AUTO ||
            afMode == CaptureRequest.CONTROL_AF_MODE_MACRO ||
            afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
            afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
    }

    private fun resolveValidFocusDistance(result: CaptureResult?, state: CameraState): Float? {
        val resultDistance = result?.get(CaptureResult.LENS_FOCUS_DISTANCE)
            ?.takeIf { it.isFinite() && it >= 0f }
        if (resultDistance != null) return resultDistance
        return if (!state.isAutoFocus) {
            state.focusDistance.takeIf { it.isFinite() && it >= 0f }
        } else {
            null
        }
    }

    private fun submitOneShotAfTrigger(
        afMode: Int,
        reason: String,
    ): Boolean {
        val session = captureSession ?: return false
        val builder = previewRequestBuilder ?: return false
        var submitted = false
        try {
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            pointAfTriggerPending = true
            session.capture(builder.build(), previewCallback, cameraHandler)
            submitted = true
        } catch (e: Exception) {
            pointAfTriggerPending = false
            PLog.e(TAG, "Failed to submit one-shot AF trigger for $reason", e)
        } finally {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        }

        if (submitted) {
            try {
                session.setRepeatingRequest(builder.build(), previewCallback, cameraHandler)
            } catch (e: Exception) {
                // The one-shot request is already queued and its callback can still complete the lock.
                PLog.w(TAG, "Unable to resume IDLE repeating request after $reason AF trigger", e)
            }
        }
        return submitted
    }

    private fun prepareCaptureLighting(
        device: CameraDevice,
        reader: ImageReader,
        baseExposureResult: CaptureResult?,
    ) {
        val currentState = _state.value
        val useMultiFrameTorch = shouldUseMultiFrameTorch(currentState)
        val needsPrecapture =
            isFlashSupported &&
            isAePrecaptureSupported() &&
            currentState.flashMode == CameraMetadata.FLASH_MODE_SINGLE &&
            currentState.isIsoAuto &&
            currentState.isShutterSpeedAuto &&
            resolveStillFlashAeMode() != CaptureRequest.CONTROL_AE_MODE_OFF

        if (useMultiFrameTorch) {
            pendingCaptureDevice = device
            pendingCaptureReader = reader
            pendingCaptureBaseExposureResult = baseExposureResult
            isMultiFrameTorchCaptureActive = true
            PLog.d(TAG, "Starting multi-frame torch warm-up")
            runMultiFrameTorchWarmupSequence { torchResult ->
                pendingCaptureBaseExposureResult = torchResult ?: pendingCaptureBaseExposureResult
                runCaptureSequence()
            }
        } else if (needsPrecapture) {
            pendingCaptureDevice = device
            pendingCaptureReader = reader
            pendingCaptureBaseExposureResult = baseExposureResult
            PLog.d(TAG, "启动状态机拍照流程")
            runPrecaptureSequence()
        } else {
            PLog.d(TAG, "直接拍照")
            performCapture(device, reader, baseExposureResult)
        }
    }

    /**
     * 拍照
     */
    fun capture(onAccepted: ((Long) -> Unit)? = null) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                capture(onAccepted)
            }
            return
        }
        if (_state.value.captureMode != CaptureMode.PHOTO) return
        if (_state.value.isCapturing) {
            PLog.d(TAG, "Ignoring capture request while a capture is already active")
            showCaptureBusyMessage()
            return
        }
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        if (captureSession == null) return
        if (isRawCaptureReader(reader) && rawBlockedUntilAppRestart) {
            PLog.w(TAG, "RAW capture blocked: lilac RAW failed again after a camera reopen")
            showLongToast(com.hinnka.mycamera.R.string.raw_hal_restart_required)
            return
        }
        val shot = reservePhotoCapture(_state.value, reader) ?: return
        activePhotoCapture = shot
        _state.update { it.copy(isCapturing = true, photoCaptureProgress = null) }
        try {
            onAccepted?.invoke(shot.id)
        } catch (e: Exception) {
            failPhotoCapture(shot, "capture acceptance callback failed")
            finishPhotoCaptureSequence(shot, successful = false)
            PLog.e(TAG, "Capture acceptance callback failed", e)
            return
        }

        try {
            livePhotoVideoStartTimestampUs = null
            if (_state.value.useLivePhoto) {
                snapshotLivePhoto()
            }

            val baseExposureResult = lastCaptureResult
            // 关键修复：每次拍照前重置拍摄结果
            lastCaptureResult = null

            PLog.i(
                TAG,
                "开始拍照 - 闪光模式: ${_state.value.flashMode}, ISO模式: ${if (_state.value.isIsoAuto) "自动" else "手动(${_state.value.iso})"}"
            )

            if (!_state.value.useLivePhoto) {
                // 播放快门音效
                onPlayShutterSound?.invoke()
            }

            _state.update { s -> s.copy(isCapturing = true) }

            // 只有在【自动曝光 + 单次闪光】时才使用预闪流程
            // 手动曝光模式下，AE_PRECAPTURE_TRIGGER 不生效（因为 AE_MODE=OFF），直接拍照
            val currentState = _state.value
            if (currentState.requiresMultiFrameCaptureSequence) {
                burstGyroRecorder.start(cameraHandler)
                ambientLightReader.start(cameraHandler)
            }
            nightPlan = null
            val planNight = isNightPlanCandidate(currentState)
            _state.update { s -> s.copy(nightShotPlan = null, nightSteadying = planNight) }
            if (planNight) {
                // Measure how steady the phone is before choosing the exposure.
                val start = { startNightCapture(device, reader, baseExposureResult) }
                cameraHandler?.postDelayed(start, NightCapturePlanner.STEADINESS_WINDOW_MS) ?: start()
            } else {
                prepareCaptureLighting(device, reader, baseExposureResult)
            }

        } catch (e: Exception) {
            PLog.e(TAG, "Failed to capture", e)
            PLog.e(TAG, "拍照失败", e)
            _state.update { s -> s.copy(isCapturing = false) }
            stopShotSensors()
            resetPreviewAfterCapture()
        }
    }

    // The plan sets the exposure by hand, so a camera without manual exposure (lilac's front) shoots at once.
    private fun isNightPlanCandidate(state: CameraState): Boolean =
        state.isNightMode &&
            isManualSensorSupported && availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF) &&
            state.isJpgMaxEnabled &&
            !state.isJpgMaxHdrEnabled &&
            state.requiresMultiFrameCaptureSequence &&
            state.isAutoExposure &&
            !shouldUseMultiFrameTorch(state)

    private fun startNightCapture(device: CameraDevice, reader: ImageReader, baseExposureResult: CaptureResult?) {
        try {
            check(cameraDevice === device && imageReader === reader && captureSession != null) {
                "camera changed while measuring steadiness"
            }
            nightPlan = planNightCapture(_state.value, reader, baseExposureResult)?.also { plan ->
                activePhotoCapture?.let {
                    it.state = it.state.copy(nightBrightenGain = plan.brightenGain, nightShotPlan = plan)
                }
            }
            // The burst reads its frame count from the state, so the plan goes there before it starts.
            _state.update { s -> s.copy(nightShotPlan = nightPlan, nightSteadying = false) }
            prepareCaptureLighting(device, reader, baseExposureResult)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to start low-light capture", e)
            nightPlan = null
            _state.update { s -> s.copy(nightShotPlan = null, nightSteadying = false) }
            _state.update { s -> s.copy(isCapturing = false) }
            stopShotSensors()
            resetPreviewAfterCapture()
        }
    }

    private fun planNightCapture(
        state: CameraState,
        reader: ImageReader,
        baseExposureResult: CaptureResult?,
    ): NightCapturePlan? {
        val characteristics = getActiveOpenCameraCharacteristics()
        val aeIso = baseExposureResult?.get(CaptureResult.SENSOR_SENSITIVITY)?.takeIf { it > 0 }
        val aeExposureNs = baseExposureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.takeIf { it > 0L }
        val focalMm = characteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()?.takeIf { it > 0f }
        val sensorWidthMm = characteristics?.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?.width?.takeIf { it > 0f }
        val shake = burstGyroRecorder.recentAngularSpeed(NightCapturePlanner.STEADINESS_WINDOW_MS * 1_000_000L)
        val manualExposure = isManualSensorSupported &&
            availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
        if (!manualExposure || aeIso == null || aeExposureNs == null || focalMm == null ||
            sensorWidthMm == null || shake == null
        ) {
            PLog.w(
                TAG,
                "Low-light plan unavailable: manual=$manualExposure aeIso=$aeIso aeShutter=$aeExposureNs " +
                    "focal=$focalMm sensorWidth=$sensorWidthMm shake=$shake",
            )
            return null
        }
        // The frame spans the sensor's long side, so this is the focal length in frame pixels.
        val focalPx = focalMm / sensorWidthMm * maxOf(reader.width, reader.height)
        val plan = NightCapturePlanner.plan(
            aeIso = aeIso,
            aeExposureNs = aeExposureNs,
            angularSpeedRadS = shake,
            focalLengthPx = focalPx,
            frames = state.nightFrameCount,
            isoRange = state.getIsoRange(),
            exposureRangeNs = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
            fast = state.nightFast,
        )
        PLog.i(
            TAG,
            "Low-light plan: shake=%.4f rad/s focalPx=%.0f ae=ISO%d %.1fms -> frames=%d ISO%d %.1fms gain=%.2f".format(
                shake, focalPx, aeIso, aeExposureNs / 1e6, plan.frames,
                plan.iso, plan.exposureTimeNs / 1e6, plan.brightenGain,
            ),
        )
        return plan
    }

    private fun performHdrBracketCapture(
        device: CameraDevice,
        reader: ImageReader,
        session: CameraCaptureSession,
        zeroEvFrameCount: Int,
        playShutterSound: Boolean,
        baseExposureResult: CaptureResult?
    ) {
        val currentState = _state.value
        val normalizedZeroEvFrameCount = zeroEvFrameCount.coerceIn(
            0,
            MultiFrameConfig.MAX_FRAME_COUNT
        )
        val evOffsets = buildHdrBracketEvOffsets(normalizedZeroEvFrameCount)
        val hdrFrameCount = evOffsets.size
        _state.update { s -> s.copy(
            isCapturing = true,
            hdrBracketCapturing = true,
            hdrBracketFrameCount = hdrFrameCount
        ) }

        try {
            if (playShutterSound && !_state.value.useLivePhoto) {
                onPlayShutterSound?.invoke()
            }
            val manualBaseExposure = resolveHdrBracketManualBaseExposure(currentState, baseExposureResult)
            logHdrBracketBaseExposure(currentState, baseExposureResult, manualBaseExposure)
            val longShutterLimitNs = hdrBracketLongShutterLimitNs(reader, manualBaseExposure?.second)
            PLog.i(TAG, "HDR bracket plan: evs=$evOffsets longShutterLimit=${longShutterLimitNs / 1e6}ms")
            val requests = evOffsets.mapIndexed { index, evOffset ->
                device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    setTag(index)
                    addTarget(reader.surface)
                    applyBaseCameraSettings(
                        builder = this,
                        isCapture = true,
                        isRawCapture = false,
                        disableZslForHdrCapture = true
                    )
                    applyHdrBracketExposure(this, currentState, evOffset, manualBaseExposure, longShutterLimitNs)
                    setAePrecaptureTriggerIfSupported(
                        this,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
                    )
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    if (currentState.awbMode != CameraMetadata.CONTROL_AWB_MODE_OFF) {
                        set(CaptureRequest.CONTROL_AWB_LOCK, true)
                    }
                    copyStillFocusSettingsFromPreview(this)
                    applyMultiFrameCaptureConsistency(
                        builder = this,
                        state = currentState,
                        baseResult = baseExposureResult,
                        lockExposure = false,
                        isRawCapture = false,
                    )
                    PLog.d(TAG, "HDR bracket request[$index]: ev=$evOffset, manual=${manualBaseExposure != null}")
                }.build()
            }

            val shot = activePhotoCapture ?: error("Missing HDR capture reservation")
            shot.state = shot.state.copy(hdrBracketCapturing = true, hdrBracketFrameCount = hdrFrameCount)
            submitPhotoCapture(shot, session, requests, baseExposureResult)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to capture HDR bracket", e)
            _state.update { s -> s.copy(
                isCapturing = false,
                hdrBracketCapturing = false,
                hdrBracketFrameCount = 0
            ) }
            stopShotSensors()
            onHdrBracketCaptureFailed?.invoke()
            resetPreviewAfterCapture()
        }
    }

    private fun buildHdrBracketEvOffsets(zeroEvFrameCount: Int): List<Float> {
        val zeroCount = zeroEvFrameCount.coerceAtLeast(1)
        val (shortEv, longEv) = HdrBracketConfig.adaptiveEvs(_state.value.histogram)
        return buildList {
            add(0f)
            add(longEv)
            add(shortEv)
            repeat((zeroCount - 1).coerceAtLeast(0)) {
                add(0f)
            }
        }
    }

    /**
     * 执行实际的拍照操作
     */
    private fun resolveHdrBracketManualBaseExposure(
        state: CameraState,
        baseExposureResult: CaptureResult?
    ): Pair<Int, Long>? {
        if (!isManualSensorSupported || !availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)) {
            return null
        }
        val baseIso = if (state.isAutoExposure) {
            baseExposureResult?.get(CaptureResult.SENSOR_SENSITIVITY)
        } else {
            state.iso
        } ?: return null
        val baseShutter = if (state.isAutoExposure) {
            baseExposureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        } else {
            state.shutterSpeed
        } ?: return null
        if (baseIso <= 0 || baseShutter <= 0L) return null
        return Pair(baseIso, baseShutter)
    }

    private fun logHdrBracketBaseExposure(
        state: CameraState,
        baseExposureResult: CaptureResult?,
        manualBaseExposure: Pair<Int, Long>?
    ) {
        val resultIso = baseExposureResult?.get(CaptureResult.SENSOR_SENSITIVITY)
        val resultShutter = baseExposureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val resultCompensation = baseExposureResult?.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
        PLog.d(
            TAG,
            "HDR bracket base exposure: auto=${state.isAutoExposure}, " +
                    "userComp=${state.exposureCompensation}, userBias=${state.exposureBias}, " +
                    "resultIso=$resultIso, resultShutter=$resultShutter, resultComp=$resultCompensation, " +
                    "manualBase=${manualBaseExposure?.first}/${manualBaseExposure?.second}"
        )
    }

    private fun logHdrBracketActualExposure(request: CaptureRequest, result: CaptureResult) {
        val requestIndex = request.tag as? Int
        val actualIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val actualShutter = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val actualCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
        val afMode = result.get(CaptureResult.CONTROL_AF_MODE)
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
        val lensState = result.get(CaptureResult.LENS_STATE)
        PLog.d(
            TAG,
            "HDR bracket result[$requestIndex]: ISO=$actualIso, shutter=$actualShutter, " +
                    "aeComp=$actualCompensation, aeMode=$aeMode, afMode=$afMode, " +
                    "afState=$afState, focus=$focusDistance, lensState=$lensState"
        )
    }

    private fun applyHdrBracketExposure(
        builder: CaptureRequest.Builder,
        state: CameraState,
        evOffset: Float,
        manualBaseExposure: Pair<Int, Long>?,
        longShutterLimitNs: Long,
    ) {
        if (manualBaseExposure != null) {
            val (iso, shutter) = calculateHdrBracketManualExposure(
                baseIso = manualBaseExposure.first,
                baseShutter = manualBaseExposure.second,
                evOffset = evOffset,
                state = state,
                longShutterLimitNs = longShutterLimitNs,
            )
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutter)
            PLog.d(TAG, "HDR bracket manual exposure: ev=$evOffset, ISO=$iso, shutter=$shutter")
            return
        }

        val compensation = calculateHdrBracketExposureCompensation(state, evOffset)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, compensation)
        PLog.d(TAG, "HDR bracket AE compensation: ev=$evOffset, compensation=$compensation")
    }

    private fun calculateHdrBracketManualExposure(
        baseIso: Int,
        baseShutter: Long,
        evOffset: Float,
        state: CameraState,
        longShutterLimitNs: Long,
    ): Pair<Int, Long> {
        val isoRange = state.getIsoRange()
        val shutterRange = state.getShutterSpeedRange()
        return HdrBracketConfig.planManualExposure(
            baseIso = baseIso,
            baseShutterNs = baseShutter,
            evOffset = evOffset,
            isoLower = isoRange.lower,
            isoUpper = isoRange.upper,
            shutterLowerNs = shutterRange.lower,
            shutterUpperNs = shutterRange.upper,
            longShutterLimitNs = longShutterLimitNs,
        )
    }

    /**
     * The long frame lights the shadows. Rather than 1/100 s plus ISO gain, it may take as long a shutter as
     * the hands allow (Night's gyro rule): more light, less noise. Never shorter than the base frame.
     */
    private fun hdrBracketLongShutterLimitNs(reader: ImageReader, baseShutterNs: Long?): Long {
        val floor = maxOf(HdrBracketConfig.YUV_LONG_MAX_SHUTTER_NS, baseShutterNs ?: 0L)
        val characteristics = getActiveOpenCameraCharacteristics() ?: return floor
        val focalMm = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()?.takeIf { it > 0f } ?: return floor
        val sensorWidthMm = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?.width?.takeIf { it > 0f } ?: return floor
        // The gyro runs with the viewfinder here, so measure before the tap: the press itself shakes a tripod.
        val shake = burstGyroRecorder.recentAngularSpeed(windowNs = 800_000_000L, skipLatestNs = 250_000_000L)
            ?: return floor
        val focalPx = focalMm / sensorWidthMm * maxOf(reader.width, reader.height)
        return maxOf(floor, NightCapturePlanner.steadyShutterNs(shake, focalPx))
    }

    private fun calculateHdrBracketExposureCompensation(state: CameraState, evOffset: Float): Int {
        val evStep = state.getExposureCompensationStep().takeIf { it > 0f } ?: return state.exposureCompensation
        val range = state.getExposureCompensationRange()
        val steps = roundHdrBracketCompensationSteps(evOffset, evStep)
        return (state.exposureCompensation + steps).coerceIn(range.lower, range.upper)
    }

    private fun roundHdrBracketCompensationSteps(evOffset: Float, evStep: Float): Int {
        if (evOffset == 0f) return 0
        val magnitude = (abs(evOffset / evStep) + 0.0001f).roundToInt()
        return if (evOffset < 0f) -magnitude else magnitude
    }

    private fun copyStillFocusSettingsFromPreview(builder: CaptureRequest.Builder) {
        // Single and multi-frame stills inherit preview focus without starting a new AF sweep.
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        previewRequestBuilder?.let { preview ->
            preview.get(CaptureRequest.CONTROL_AF_MODE)?.let {
                builder.set(CaptureRequest.CONTROL_AF_MODE, it)
            }
            preview.get(CaptureRequest.LENS_FOCUS_DISTANCE)?.let {
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, it)
            }
            preview.get(CaptureRequest.CONTROL_AF_REGIONS)?.let {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, it)
            }
            preview.get(CaptureRequest.CONTROL_AE_REGIONS)?.let {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, it)
            }
        }
        applyFrozenCaptureFocus(builder)
    }

    private fun applyMultiFrameCaptureConsistency(
        builder: CaptureRequest.Builder,
        state: CameraState,
        baseResult: CaptureResult?,
        lockExposure: Boolean,
        isRawCapture: Boolean,
    ) {
        if (!state.requiresMultiFrameCaptureSequence) return

        if (lockExposure) {
            val useMultiFrameTorch = isMultiFrameTorchCaptureActive
            if (!useMultiFrameTorch) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            val requestUsesManualExposure =
                builder.get(CaptureRequest.CONTROL_AE_MODE) == CaptureRequest.CONTROL_AE_MODE_OFF
            if (isRawCapture && !requestUsesManualExposure && !useMultiFrameTorch) {
                val lockedIso = baseResult?.get(CaptureResult.SENSOR_SENSITIVITY)
                val lockedExposure = baseResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                val canUseManualExposure = isManualSensorSupported &&
                        availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF) &&
                        lockedIso != null && lockedIso > 0 &&
                        lockedExposure != null && lockedExposure > 0L
                if (canUseManualExposure) {
                    // Auto exposure can report ISO above the manual range (extra digital gain),
                    // which the HAL refuses in a manual request. Above the analog limit the gain is
                    // digital, so a lower ISO at the same shutter loses nothing the render cannot
                    // restore, and the flicker-safe shutter auto exposure chose is kept.
                    val isoRange = state.getIsoRange()
                    val fittedIso = checkNotNull(lockedIso).coerceIn(isoRange.lower, isoRange.upper)
                    if (fittedIso != lockedIso) {
                        PLog.i(
                            TAG,
                            "Multi-frame locked ISO$lockedIso is outside " +
                                "ISO${isoRange.lower}-${isoRange.upper}; using ISO$fittedIso",
                        )
                    }
                    // Bracketed bursts plan their own short frame from this base, so only same-exposure bursts go darker.
                    val darker = if (state.isHdrPlusBracketExposureEnabled) null else
                        MultiFrameExposurePlanner.planHighlightUnderexposure(
                            viewfinderHistogram = state.histogram,
                            frameCount = state.activeMultiFrameCount,
                            baseIso = fittedIso,
                            baseExposureTimeNs = checkNotNull(lockedExposure),
                            isoLower = isoRange.lower,
                            exposureTimeLowerNs = state.getShutterSpeedRange().lower,
                        )
                    darker?.let {
                        PLog.i(
                            TAG,
                            "RAW burst highlight underexposure: clipped=${"%.3f".format(it.clippedFraction)} " +
                                "frames=${state.activeMultiFrameCount} ev=${"%.2f".format(it.plannedDeltaEv)} " +
                                "ISO$fittedIso ${lockedExposure}ns -> ISO${it.sensitivityIso} ${it.exposureTimeNs}ns",
                        )
                    }
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, darker?.sensitivityIso ?: fittedIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, darker?.exposureTimeNs ?: lockedExposure)
                }
            }
            val plan = nightPlan
            if (!isRawCapture && plan != null && !requestUsesManualExposure && !useMultiFrameTorch) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.exposureTimeNs)
            }

            if (builder.get(CaptureRequest.CONTROL_AE_MODE) != CaptureRequest.CONTROL_AE_MODE_OFF) {
                val aeLockAvailable = getActiveOpenCameraCharacteristics()
                    ?.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
                if (aeLockAvailable) {
                    builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                }
            }
        }

        val awbLockAvailable = getActiveOpenCameraCharacteristics()
            ?.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
        if (state.awbMode != CameraMetadata.CONTROL_AWB_MODE_OFF && awbLockAvailable) {
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
        }

        PLog.d(
            TAG,
            "Multi-frame capture locked exposure=$lockExposure " +
                    "ae=${builder.get(CaptureRequest.CONTROL_AE_MODE)} " +
                    "flash=${builder.get(CaptureRequest.FLASH_MODE)} " +
                    "iso=${builder.get(CaptureRequest.SENSOR_SENSITIVITY)} " +
                    "shutter=${builder.get(CaptureRequest.SENSOR_EXPOSURE_TIME)} " +
                    "af=${builder.get(CaptureRequest.CONTROL_AF_MODE)} " +
                    "focus=${builder.get(CaptureRequest.LENS_FOCUS_DISTANCE)}"
        )
    }

    private fun buildMultiFrameShortCaptureRequest(
        device: CameraDevice,
        reader: ImageReader,
        state: CameraState,
        baseResult: CaptureResult?,
        baseRequest: CaptureRequest,
        isRawCapture: Boolean,
    ): CaptureRequest {
        return device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            applyBaseCameraSettings(this, isCapture = true, isRawCapture = isRawCapture)
            setAePrecaptureTriggerIfSupported(
                this,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
            )
            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            copyStillFocusSettingsFromPreview(this)
            applyMultiFrameCaptureConsistency(
                builder = this,
                state = state,
                baseResult = baseResult,
                lockExposure = true,
                isRawCapture = isRawCapture,
            )
            applyMultiFrameShortExposure(
                builder = this,
                state = state,
                baseResult = baseResult,
                baseRequest = baseRequest,
            )
            setTag(MultiFrameCaptureRole.SHORT)
        }.build()
    }

    private fun applyMultiFrameShortExposure(
        builder: CaptureRequest.Builder,
        state: CameraState,
        baseResult: CaptureResult?,
        baseRequest: CaptureRequest,
    ) {
        val baseIso = baseRequest.get(CaptureRequest.SENSOR_SENSITIVITY)
            ?: baseResult?.get(CaptureResult.SENSOR_SENSITIVITY)
        val baseShutter = baseRequest.get(CaptureRequest.SENSOR_EXPOSURE_TIME)
            ?: baseResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val canUseManualExposure = isManualSensorSupported &&
            availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF) &&
            baseIso != null && baseIso > 0 &&
            baseShutter != null && baseShutter > 0L
        if (canUseManualExposure) {
            val manualBaseIso = checkNotNull(baseIso)
            val manualBaseShutter = checkNotNull(baseShutter)
            val (shortIso, shortShutter) = calculateMultiFrameShortExposure(
                baseIso = manualBaseIso,
                baseShutter = manualBaseShutter,
                state = state,
            )
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, shortIso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shortShutter)
            val achievedRatio = shortIso.toDouble() * shortShutter.toDouble() /
                (manualBaseIso.toDouble() * manualBaseShutter.toDouble())
            PLog.i(
                TAG,
                "Multi-frame short request: base=ISO$manualBaseIso/${manualBaseShutter}ns " +
                    "short=ISO$shortIso/${shortShutter}ns " +
                    "targetEv=${state.hdrPlusShortFrameExposureEv} exposureRatio=$achievedRatio",
            )
            return
        }

        val shortEv = state.hdrPlusShortFrameExposureEv
        val compensation = calculateHdrBracketExposureCompensation(state, shortEv)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, compensation)
        PLog.w(
            TAG,
            "Manual sensor exposure unavailable for multi-frame short request; " +
                "using AE compensation=$compensation for ${shortEv}EV",
        )
    }

    private fun calculateMultiFrameShortExposure(
        baseIso: Int,
        baseShutter: Long,
        state: CameraState,
    ): Pair<Int, Long> {
        val isoRange = state.getIsoRange()
        val shutterRange = state.getShutterSpeedRange()
        val exposureMultiplier = 2.0.pow(state.hdrPlusShortFrameExposureEv.toDouble())
        val targetProduct = baseIso.toDouble() * baseShutter.toDouble() * exposureMultiplier
        val initialShutter = (baseShutter.toDouble() * exposureMultiplier)
            .roundToLong()
            .coerceIn(shutterRange.lower, shutterRange.upper)
        val shortIso = (targetProduct / initialShutter.toDouble())
            .roundToInt()
            .coerceIn(isoRange.lower, isoRange.upper)
        val shortShutter = (targetProduct / shortIso.toDouble())
            .roundToLong()
            .coerceIn(shutterRange.lower, shutterRange.upper)
        return shortIso to shortShutter
    }

    private fun buildMultiFrameLongCaptureRequest(
        device: CameraDevice,
        reader: ImageReader,
        state: CameraState,
        baseResult: CaptureResult?,
        baseRequest: CaptureRequest,
        isRawCapture: Boolean,
    ): CaptureRequest? {
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            applyBaseCameraSettings(this, isCapture = true, isRawCapture = isRawCapture)
            setAePrecaptureTriggerIfSupported(
                this,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
            )
            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            copyStillFocusSettingsFromPreview(this)
            applyMultiFrameCaptureConsistency(
                builder = this,
                state = state,
                baseResult = baseResult,
                lockExposure = true,
                isRawCapture = isRawCapture,
            )
        }
        if (!applyMultiFrameLongExposure(builder, state, baseResult, baseRequest)) return null
        builder.setTag(MultiFrameCaptureRole.LONG)
        return builder.build()
    }

    private fun applyMultiFrameLongExposure(
        builder: CaptureRequest.Builder,
        state: CameraState,
        baseResult: CaptureResult?,
        baseRequest: CaptureRequest,
    ): Boolean {
        val baseIso = baseRequest.get(CaptureRequest.SENSOR_SENSITIVITY)
            ?: baseResult?.get(CaptureResult.SENSOR_SENSITIVITY)
        val baseShutter = baseRequest.get(CaptureRequest.SENSOR_EXPOSURE_TIME)
            ?: baseResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val canUseManualExposure = isManualSensorSupported &&
            availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF) &&
            baseIso != null && baseIso > 0 &&
            baseShutter != null && baseShutter > 0L
        if (canUseManualExposure) {
            val manualBaseIso = checkNotNull(baseIso)
            val manualBaseShutter = checkNotNull(baseShutter)
            val isoRange = state.getIsoRange()
            val shutterRange = state.getShutterSpeedRange()
            val sensorCharacteristics = activeOutputPhysicalCameraId?.let { cameraId ->
                getCameraCharacteristicsOrNull(cameraId, "multi-frame long exposure")
            } ?: getActiveOpenCameraCharacteristics()
            val reportedMaxAnalogIso = sensorCharacteristics
                ?.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
                ?.takeIf { it > 0 }
            val longFrameIsoUpper = minOf(
                isoRange.upper,
                reportedMaxAnalogIso
                    ?: MultiFrameConfig.LONG_FRAME_FALLBACK_MAX_ANALOG_SENSITIVITY,
            )
            if (longFrameIsoUpper < isoRange.lower) {
                PLog.w(
                    TAG,
                    "Multi-frame long ISO limit ISO$longFrameIsoUpper is below the sensor " +
                        "minimum ISO${isoRange.lower}; long slots will remain normal",
                )
                return false
            }
            val plan = runCatching {
                MultiFrameExposurePlanner.planLongExposure(
                    baseIso = manualBaseIso,
                    baseExposureTimeNs = manualBaseShutter,
                    isoLower = isoRange.lower,
                    isoUpper = longFrameIsoUpper,
                    exposureTimeLowerNs = shutterRange.lower,
                    exposureTimeUpperNs = shutterRange.upper,
                    targetExposureEv = state.hdrPlusLongFrameExposureEv,
                )
            }.onFailure { error ->
                PLog.e(TAG, "Unable to plan bounded multi-frame long exposure", error)
            }.getOrNull()
            if (plan != null) {
                if (plan.upperLimitsProduceLowerExposureThanBase) {
                    PLog.w(
                        TAG,
                        "Multi-frame long exposure cannot reach the normal frame after both " +
                            "limits: base=ISO$manualBaseIso/${manualBaseShutter}ns " +
                            "bounded=ISO${plan.sensitivityIso}/${plan.exposureTimeNs}ns " +
                            "maxAnalogIso=$longFrameIsoUpper; long slots will remain normal",
                    )
                    return false
                }
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, plan.sensitivityIso)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.exposureTimeNs)
                PLog.i(
                    TAG,
                    "Multi-frame long request: base=ISO$manualBaseIso/${manualBaseShutter}ns " +
                        "long=ISO${plan.sensitivityIso}/${plan.exposureTimeNs}ns " +
                        "targetEv=${state.hdrPlusLongFrameExposureEv} " +
                        "plannedEv=${plan.plannedDeltaEv} isoUpperLimited=${plan.isoUpperLimited} " +
                        "shutterUpperLimited=${plan.shutterUpperLimited} " +
                        "maxAnalogIso=$longFrameIsoUpper " +
                        "shutterUpperLimitNs=${plan.exposureTimeUpperLimitNs}",
                )
                return true
            }
        }
        PLog.w(
            TAG,
            "Manual sensor exposure unavailable for bounded multi-frame long request; " +
                "long slots will remain normal",
        )
        return false
    }

    private fun shouldUseJpgMaxHdrCapture(state: CameraState, isRawCapture: Boolean): Boolean {
        return state.captureMode == CaptureMode.PHOTO &&
                state.isJpgMaxHdrEnabled &&
                !isMultiFrameTorchCaptureActive &&
                !isRawCapture &&
                !state.burstCapturing &&
                !state.hdrBracketCapturing &&
                !state.useMultipleExposure &&
                !state.useLivePhoto
    }

    private fun shouldUseMultiFrameTorch(state: CameraState): Boolean {
        return state.requiresMultiFrameCaptureSequence &&
                isFlashSupported &&
                state.flashMode == CameraMetadata.FLASH_MODE_SINGLE
    }

    private fun shouldUseContinuousBurstTorch(state: CameraState): Boolean {
        return state.burstCapturing &&
                isFlashSupported &&
                state.flashMode == CameraMetadata.FLASH_MODE_SINGLE
    }

    private fun resolveHdrBracketZeroEvFrameCount(state: CameraState): Int {
        return if (state.isMultiFrameEnabled) {
            state.activeMultiFrameCount
        } else {
            0
        }
    }

    private fun performCapture(
        device: CameraDevice,
        reader: ImageReader,
        baseExposureResult: CaptureResult? = lastCaptureResult
    ) {
        try {
            val isRawCapture = isRawCaptureReader(reader)
            val currentState = stillFlashCaptureState ?: _state.value
            val useMultiFrameTorch = isMultiFrameTorchCaptureActive
            if (shouldUseJpgMaxHdrCapture(currentState, isRawCapture)) {
                val session = captureSession ?: run {
                    PLog.e(TAG, "Failed to capture JPGmax bracket: capture session unavailable")
                    _state.update { s -> s.copy(isCapturing = false) }
                    onHdrBracketCaptureFailed?.invoke()
                    resetPreviewAfterCapture()
                    return
                }
                PLog.d(TAG, "JPGmax YUV bracket capture")
                performHdrBracketCapture(
                    device = device,
                    reader = reader,
                    session = session,
                    zeroEvFrameCount = resolveHdrBracketZeroEvFrameCount(currentState),
                    playShutterSound = false,
                    baseExposureResult = baseExposureResult
                )
                return
            }
            val session = captureSession
                ?: throw IllegalStateException("Capture session unavailable")

            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)

                // Do not mirror still capture frames to the GL preview SurfaceTexture.
                // Some camera HALs occasionally deliver a still-capture buffer that
                // SurfaceTexture.updateTexImage cannot bind as an external OES image.
                // if (!isRawCapture && shouldMirrorStillCaptureToPreview()) {
                //     previewSurface?.let { addTarget(it) }
                // }

                // 应用所有相机参数（曝光、白平衡、闪光灯、变焦）
                // isCapture = true 确保使用完整的曝光时间（不限制长曝光）
                applyBaseCameraSettings(
                    this,
                    isCapture = true,
                    isRawCapture = isRawCapture,
                    currentState = currentState,
                )

                // A previous multi-frame/torch sequence may have used AE_LOCK. Standard
                // single-frame flash capture must let the flash AE routine choose exposure.
                if (!currentState.requiresMultiFrameCaptureSequence) {
                    set(CaptureRequest.CONTROL_AE_LOCK, false)
                }

                if (useMultiFrameTorch) {
                    if (currentState.isAutoExposure) {
                        set(
                            CaptureRequest.CONTROL_AE_MODE,
                            resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                        )
                    }
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }

                if (isRawCapture && shouldApplyRawMinShutterLimit(currentState)) {
                    if (isManualSensorSupported) {
                        val (adjustedIso, adjustedShutter) = calculateRawMinShutterAdjustedExposure(
                            currentState,
                            currentState.iso,
                            currentState.shutterSpeed
                        )
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        set(CaptureRequest.SENSOR_SENSITIVITY, adjustedIso)
                        set(CaptureRequest.SENSOR_EXPOSURE_TIME, adjustedShutter)
                        PLog.d(
                            TAG,
                            "Capture RAW min shutter override: minShutter=${currentState.rawMinShutterSpeedNs}, ISO=$adjustedIso, shutter=$adjustedShutter"
                        )
                    } else {
                        PLog.w(TAG, "RAW min shutter requires MANUAL_SENSOR support")
                    }
                }

                // 强制将此请求的触发器设为 IDLE，防止携带预览中的触发状态
                setAePrecaptureTriggerIfSupported(
                    this,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
                )

                copyStillFocusSettingsFromPreview(this)

                applyMultiFrameCaptureConsistency(
                    builder = this,
                    state = currentState,
                    baseResult = baseExposureResult,
                    lockExposure = true,
                    isRawCapture = isRawCapture,
                )

                PLog.d(
                    TAG,
                    "Capture request built and ready to send: " +
                        "aeMode=${get(CaptureRequest.CONTROL_AE_MODE)}, " +
                        "aeTrigger=${get(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)}, " +
                        "aeLock=${get(CaptureRequest.CONTROL_AE_LOCK)}, " +
                        "flashMode=${get(CaptureRequest.FLASH_MODE)}, " +
                        "iso=${get(CaptureRequest.SENSOR_SENSITIVITY)}, " +
                        "shutter=${get(CaptureRequest.SENSOR_EXPOSURE_TIME)}"
                )
            }

            if (currentState.requiresMultiFrameCaptureSequence) {
                // Burst Mode
                val frameCount = currentState.activeMultiFrameCount
                captureBuilder.setTag(MultiFrameCaptureRole.BASE)
                val baseRequest = captureBuilder.build()
                val useHdrPlusBracketExposurePlan =
                    currentState.isHdrPlusBracketExposureEnabled && !useMultiFrameTorch
                val requests = if (!useHdrPlusBracketExposurePlan) {
                    List(frameCount) { baseRequest }
                } else {
                    val normalFrameCount = MultiFrameConfig.hdrPlusNormalFrameCount(frameCount)
                    val shortFrameCount = MultiFrameConfig.hdrPlusShortFrameCount(frameCount)
                    val longFrameCount = MultiFrameConfig.hdrPlusLongFrameCount(frameCount)
                    val shortRequest = if (shortFrameCount > 0) {
                        buildMultiFrameShortCaptureRequest(
                            device = device,
                            reader = reader,
                            state = currentState,
                            baseResult = baseExposureResult,
                            baseRequest = baseRequest,
                            isRawCapture = isRawCapture,
                        )
                    } else {
                        null
                    }
                    val longRequest = if (longFrameCount > 0) {
                        buildMultiFrameLongCaptureRequest(
                            device = device,
                            reader = reader,
                            state = currentState,
                            baseResult = baseExposureResult,
                            baseRequest = baseRequest,
                            isRawCapture = isRawCapture,
                        )
                    } else {
                        null
                    }
                    val radianceRequests = buildList(
                        MultiFrameConfig.hdrPlusCaptureFrameCount(frameCount)
                    ) {
                        repeat(normalFrameCount) {
                            add(baseRequest)
                        }
                        shortRequest?.let(::add)
                        repeat(longFrameCount) {
                            add(longRequest ?: baseRequest)
                        }
                    }
                    val scheduledLongFrameCount = if (longRequest != null) longFrameCount else 0
                    PLog.i(
                        TAG,
                        "HDR+ burst plan: normalFrames=$normalFrameCount shortFrames=" +
                            "$shortFrameCount " +
                            "longFrames=$scheduledLongFrameCount " +
                            "fallbackNormalFrames=${longFrameCount - scheduledLongFrameCount} " +
                            "shortTargetEv=${currentState.hdrPlusShortFrameExposureEv} " +
                            "longTargetEv=${currentState.hdrPlusLongFrameExposureEv} " +
                            "longNominalMaxShutterNs=" +
                            "${MultiFrameConfig.LONG_FRAME_MAX_EXPOSURE_TIME_NS} " +
                            "total=${radianceRequests.size}",
                    )
                    radianceRequests
                }
                if (useMultiFrameTorch) {
                    PLog.i(
                        TAG,
                        "Multi-frame torch denoise burst plan: frames=${requests.size} " +
                            "hdr=false aeMode=${baseRequest.get(CaptureRequest.CONTROL_AE_MODE)} " +
                            "aeLock=${baseRequest.get(CaptureRequest.CONTROL_AE_LOCK)} " +
                            "flashMode=${baseRequest.get(CaptureRequest.FLASH_MODE)}",
                    )
                } else if (currentState.isJpgMaxEnabled) {
                    PLog.i(
                        TAG,
                        "JPGmax denoise burst plan: zeroEvFrames=${requests.size} hdr=false",
                    )
                } else if (!useHdrPlusBracketExposurePlan) {
                    PLog.i(
                        TAG,
                        "RAWmax same-exposure burst plan: baseFrames=${requests.size} " +
                            "hdr=false shortFrames=0 longFrames=0",
                    )
                }

                val shot = activePhotoCapture ?: error("Missing multi-frame capture reservation")
                submitPhotoCapture(shot, session, requests, baseExposureResult)
            } else {
                val shot = activePhotoCapture ?: error("Missing single capture reservation")
                submitPhotoCapture(shot, session, listOf(captureBuilder.build()), baseExposureResult)
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to perform capture", e)
            _state.update { s -> s.copy(isCapturing = false) }
            stopShotSensors()
            resetPreviewAfterCapture()
        }
    }

    private fun resetPreviewAfterCapture(keepLivePhotoCapture: Boolean = false) {
        pendingContinuousBurstCapture?.let {
            failPhotoCapture(it, "continuous burst preparation cancelled")
        }
        pendingContinuousBurstCapture = null
        activePhotoCapture?.takeIf { !it.submitted }?.let { shot ->
            failPhotoCapture(shot, "capture preparation cancelled")
            activePhotoCapture = null
            _state.update { it.copy(isCapturing = false, hdrBracketCapturing = false, hdrBracketFrameCount = 0) }
        }
        if (!keepLivePhotoCapture) {
            livePhotoVideoStartTimestampUs?.let(livePhotoRecorder::cancelCapture)
            livePhotoVideoStartTimestampUs = null
        }
        // 重置拍照状态机
        clearPrecaptureTracking()
        clearMultiFrameTorchWarmupTracking()
        isMultiFrameTorchCaptureActive = false
        isContinuousBurstTorchActive = false
        pendingCaptureDevice = null
        pendingCaptureReader = null
        pendingCaptureBaseExposureResult = null

        // 关键修复：检查相机和会话是否仍然有效
        val device = cameraDevice
        val session = captureSession
        val builder = previewRequestBuilder
        val afTriggerModeToCancel = stillFlashAfMode?.takeIf {
            supportsAfTrigger(it) && isCaptureRequestKeyAvailable(CaptureRequest.CONTROL_AF_TRIGGER.name)
        }

        if (device == null || session == null || builder == null) {
            PLog.v(TAG, "resetPreviewAfterCapture: camera not ready, skipping")
            clearCaptureFocusState("preview unavailable after capture")
            runFocusReleasesAfterCapture()
            return
        }

        try {
            if (afTriggerModeToCancel != null) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, afTriggerModeToCancel)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            }
            setAePrecaptureTriggerIfSupported(
                builder,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL,
            )
            // The preview builder also targets the stabilization ImageReader. Keep this one-shot
            // AF/AE reset request on the normal preview callback so its YUV image has matching
            // CaptureResult metadata; otherwise the stabilizer must emit an identity-pose frame.
            session.capture(builder.build(), previewCallback, cameraHandler)

            clearCaptureFocusState("capture sequence finished")
            applyBaseCameraSettings(builder, isCapture = false)
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            setAePrecaptureTriggerIfSupported(
                builder,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
            )
            session.setRepeatingRequest(builder.build(), previewCallback, cameraHandler)
            PLog.i(TAG, "Capture preview restored: cancelledAfMode=$afTriggerModeToCancel " +
                "previewAfMode=${builder.get(CaptureRequest.CONTROL_AF_MODE)}")
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to reset preview", e)
            clearCaptureFocusState("preview reset failure")
        }
        runFocusReleasesAfterCapture()
    }

    private fun clearCaptureFocusState(reason: String) {
        val hadFocusState = isCaptureFocusFrozen
        stillFlashAfMode = null
        stillFlashFocusDistance = null
        stillFlashCaptureState = null
        isCaptureFocusFrozen = false
        if (hadFocusState) {
            PLog.i(TAG, "Capture focus state released: reason=$reason")
        }
    }


    /**
     * 构建 CaptureInfo
     *
     * 从 CaptureResult 和 CameraCharacteristics 提取拍摄信息
     */
    fun rebuildCaptureInfo(
        result: CaptureResult?,
        imageWidth: Int,
        imageHeight: Int,
        latitude: Double? = null,
        longitude: Double? = null,
        effectiveCharacteristics: CameraCharacteristics? = null,
        captureState: CameraState = _state.value,
        captureTime: Long = System.currentTimeMillis(),
        exposureCompensationStep: Float = captureState.getExposureCompensationStep(),
    ): CaptureInfo {
        val openCameraId = getCurrentOpenCameraId()
        val zoomRatio = captureState.zoomRatio

        // 从 CameraCharacteristics 获取镜头固定信息
        var aperture: Float? = null
        var characteristicsForMetadata: CameraCharacteristics? = null

        try {
            val characteristics = effectiveCharacteristics ?: getCameraCharacteristicsCached(openCameraId)
            characteristicsForMetadata = characteristics

            // 光圈值（取第一个可用光圈）
            val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            aperture = apertures?.firstOrNull()

        } catch (e: Exception) {
            PLog.e(TAG, "Failed to get camera characteristics for EXIF", e)
        }

        // 从 CaptureResult 获取曝光信息
        val exposureTime = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: captureState.shutterSpeed
        val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY) ?: captureState.iso
        val awbModeForExif = result?.get(CaptureResult.CONTROL_AWB_MODE) ?: captureState.awbMode
        val whiteBalance = if (awbModeForExif == CameraMetadata.CONTROL_AWB_MODE_AUTO) 0 else 1
        val flashState = result?.get(CaptureResult.FLASH_STATE) ?: captureState.flashMode

        // 如果有实时的光圈/焦距，使用实时值
        result?.get(CaptureResult.LENS_APERTURE)?.let { aperture = it }
        val resolvedFocalLength = resolveCaptureFocalLength(
            characteristics = characteristicsForMetadata,
            captureResultFocalLength = result?.get(CaptureResult.LENS_FOCAL_LENGTH),
            zoomRatio = zoomRatio,
            captureState = captureState,
        )

        return CaptureInfo(
            exposureTime = exposureTime,
            iso = iso,
            aperture = aperture,
            focalLength = resolvedFocalLength.focalLength,
            focalLength35mm = resolvedFocalLength.focalLength35mm,
            exposureBias = captureState.exposureBias,
            exposureCompensation = captureState.run {
                exposureCompensation * exposureCompensationStep
            },
            whiteBalance = whiteBalance,
            flashState = flashState,
            // 传给下游的方向永远是 NORMAL (1)
            orientation = ExifInterface.ORIENTATION_NORMAL,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            captureTime = captureTime,
            latitude = latitude,
            longitude = longitude,
            colorSpace = when {
                captureState.isP3Supported && captureState.useP3ColorSpace -> ColorSpace.Named.DISPLAY_P3
                else -> ColorSpace.Named.SRGB
            }
        )
    }

    private data class ResolvedCaptureFocalLength(
        val focalLength: Float?,
        val focalLength35mm: Int?
    )

    private fun resolveCaptureFocalLength(
        characteristics: CameraCharacteristics?,
        captureResultFocalLength: Float?,
        zoomRatio: Float,
        captureState: CameraState = _state.value,
    ): ResolvedCaptureFocalLength {
        val selectedCamera = captureState.getCurrentCameraInfo()
        val selectedCameraFocalLength = selectedCamera?.focalLength?.takeIf { it > 0f }
        val selectedCameraFocalLength35mm = selectedCamera?.focalLength35mmEquivalent?.takeIf { it > 0f }
        val characteristicsFocalLength = characteristics
            ?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
            ?.takeIf { it > 0f }

        val baseFocalLength = selectedCameraFocalLength
            ?: captureResultFocalLength?.takeIf { it > 0f }
            ?: characteristicsFocalLength
        val baseFocalLength35mm = selectedCameraFocalLength35mm
            ?: characteristics?.let(::calculate35mmEquivalent)?.toFloat()

        return ResolvedCaptureFocalLength(
            focalLength = baseFocalLength?.times(zoomRatio),
            focalLength35mm = baseFocalLength35mm?.times(zoomRatio)?.roundToInt()
        )
    }

    private fun shouldUseP3ColorSpace(): Boolean {
        return _state.value.isP3Supported && _state.value.useP3ColorSpace
    }

    /**
     * 计算等效35mm焦距
     *
     * 基于传感器尺寸计算裁切系数
     */
    private fun calculate35mmEquivalent(characteristics: CameraCharacteristics): Int? {
        try {
            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

            if (focalLengths == null || focalLengths.isEmpty() || sensorSize == null) {
                return null
            }

            val focalLength = focalLengths[0]

            // 计算传感器对角线
            val sensorDiagonal = kotlin.math.sqrt(
                (sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble()
            ).toFloat()

            // 35mm 全画幅对角线 (36mm x 24mm)
            val filmDiagonal = 43.2666f

            if (sensorDiagonal <= 0) return null

            return (focalLength * filmDiagonal / sensorDiagonal).roundToInt()
        } catch (e: Exception) {
            return null
        }
    }

// ==================== 生命周期 ====================

    /**
     * 关闭相机
     */
    fun closeCamera(
        preserveVideoRecording: Boolean = false,
        expectedSurfaceTexture: SurfaceTexture? = null
    ) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                closeCamera(preserveVideoRecording, expectedSurfaceTexture)
            }
            return
        }
        val expectedSurfaceIsCurrent = previewSurfaceTexture === expectedSurfaceTexture
        val expectedSurfaceIsPending = pendingCameraOpenRequest?.surfaceTexture === expectedSurfaceTexture
        if (
            expectedSurfaceTexture != null &&
            !expectedSurfaceIsCurrent &&
            !expectedSurfaceIsPending
        ) {
            PLog.d(
                TAG,
                "closeCamera skipped for stale SurfaceTexture: expected=" +
                        "${System.identityHashCode(expectedSurfaceTexture)}, current=" +
                        "${previewSurfaceTexture?.let { System.identityHashCode(it) }}"
            )
            return
        }
        closeCameraInternal(
            preserveVideoRecording = preserveVideoRecording,
            clearPendingOpen = true,
        )
    }

    private fun closeCameraInternal(
        preserveVideoRecording: Boolean,
        clearPendingOpen: Boolean,
    ) {
        if (clearPendingOpen) {
            pendingCameraOpenRequest = null
        }
        try {
            stopShotSensors(closingCamera = true)
            pendingFrameMetadata.clear()
            cameraOpenGeneration++
            val lifecycleBeforeClose = cameraDeviceLifecycle
            if (slowMotion != null && !slowMotionCycling && !_state.value.videoRecordingState.isRecording) abortSlowMotion()
            val keepVideoRecording = preserveVideoRecording && _state.value.videoRecordingState.isRecording
            if (keepVideoRecording) {
                PLog.d(TAG, "Closing camera while keeping active video recording")
            }
            if (_state.value.videoRecordingState.isRecording && !keepVideoRecording) {
                stopVideoRecording()
            } else if (!keepVideoRecording && !_state.value.videoRecordingState.isProcessing) {
                videoRecorder.forceStop()
                stopVideoRecordingTicker()
                _state.update { s -> s.copy(videoRecordingState = VideoRecordingState()) }
            }
            val videoStateAfterStopRequest = _state.value.videoRecordingState
            if (videoStateAfterStopRequest.isProcessing) {
                PLog.d(TAG, "Closing camera while video recording is finalizing")
            }

            clearCameraSessionState("closeCamera")
            val deviceToClose = cameraDevice
            cameraDevice = null
            clearCameraCapabilityCache()

            _state.update { s ->
                if (keepVideoRecording) {
                    s.copy(isPreviewActive = false)
                } else {
                    s.copy(
                        isPreviewActive = false,
                        videoRecordingState = if (videoStateAfterStopRequest.isProcessing) {
                            videoStateAfterStopRequest
                        } else {
                            VideoRecordingState()
                        }
                    )
                }
            }

            // 停止 Live Photo 录制，释放旧环境下的 EGL 资源
            livePhotoRecorder.stopRecording()

            when {
                deviceToClose != null -> {
                    closeCameraDeviceAndAwait(deviceToClose, "closeCamera")
                }
                lifecycleBeforeClose == CameraDeviceLifecycle.OPENING -> {
                    // CameraManager 没有取消打开的 API；等待其回调后立即关闭。
                    cameraDeviceLifecycle = CameraDeviceLifecycle.OPENING
                }
                lifecycleBeforeClose == CameraDeviceLifecycle.CLOSING -> {
                    cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSING
                }
                else -> {
                    cameraDeviceLifecycle = CameraDeviceLifecycle.CLOSED
                    openPendingCameraRequest()
                }
            }
            PLog.d(TAG, "Camera close requested: lifecycle=$cameraDeviceLifecycle")
        } catch (e: Exception) {
            PLog.e(TAG, "Error closing camera", e)
        }
    }

    private fun invalidateCaptureSession(session: CameraCaptureSession?, reason: String) {
        // A delayed close callback from the old session must not clear its replacement.
        if (session == null || captureSession !== session) return
        onPreviewStreamActive?.invoke(false)
        captureSession = null
        _enginePreviewSource.value = null
        previewRequestBuilder = null
        val wasBurstCapturing = _state.value.burstCapturing
        burstCapturing = false
        _state.update {
            it.copy(
                isPreviewActive = false,
                previewFirstFrameTimestampNs = null,
                burstCapturing = false,
                isCapturing = if (wasBurstCapturing) false else it.isCapturing,
            )
        }
        if (wasBurstCapturing) {
            // Release burst preparation without submitting requests to the retired session.
            resetPreviewAfterCapture()
        }
        PLog.d(TAG, "Capture session invalidated: $reason")
    }

    private fun safeCloseCaptureSession(session: CameraCaptureSession?, reason: String) {
        invalidateCaptureSession(session, reason)
        try {
            session?.close()
        } catch (e: SecurityException) {
            PLog.w(TAG, "Ignoring SecurityException while closing capture session ($reason): ${e.message}")
        } catch (e: Exception) {
            PLog.e(TAG, "Error closing capture session ($reason)", e)
        }
    }

    private fun safeCloseImageReader(reader: ImageReader?) {
        reader?.let {
            if (openImagesCount.get() == 0) {
                it.close()
                PLog.d(TAG, "ImageReader closed immediately")
            } else {
                synchronized(pendingCloseReaders) {
                    pendingCloseReaders.add(it)
                }
                PLog.d(TAG, "ImageReader added to pending close list, open images: ${openImagesCount.get()}")
            }
        }
    }

    private fun checkAndClosePendingReaders() {
        synchronized(pendingCloseReaders) {
            val iterator = pendingCloseReaders.iterator()
            while (iterator.hasNext()) {
                val reader = iterator.next()
                try {
                    reader.close()
                    PLog.d(TAG, "Closed pending ImageReader")
                } catch (e: Exception) {
                    PLog.e(TAG, "Error closing pending ImageReader", e)
                }
                iterator.remove()
            }
        }
    }

    private fun processAndTriggerCapture(
        image: SafeImage,
        result: TotalCaptureResult?,
        shot: PhotoCaptureContext,
    ) {
        try {
            // Reject malformed HAL buffers while this capture still owns its failure/cleanup path.
            image.validateBufferLayout()
            val width = image.width
            val height = image.height
            val reportedPhysicalCameraId = result
                ?.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            val effectivePhysicalCameraId = shot.physicalCameraId
                ?: reportedPhysicalCameraId
            var effectiveCharacteristics = effectivePhysicalCameraId
                ?.let { cameraId ->
                    getCameraCharacteristicsOrNull(cameraId, "captured RAW physical camera")
                }
                ?: shot.characteristics
            var effectiveResult: CaptureResult? = effectivePhysicalCameraId
                ?.let { physicalId -> result?.physicalCameraResults?.get(physicalId) }
                ?: result
            if (IS_LILAC && image.format == ImageFormat.RAW_SENSOR && result != null && shot.state.zoomRatio > 1f) {
                // The request went out at the full sensor (applyZoomSettings); keep the framed crop for the RAW.
                shot.characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { activeRect ->
                    RawProcessor.rememberFramedCrop(result.request, buildCenteredCropRegion(activeRect, shot.state.zoomRatio))
                }
            }

            // For RAW images, ensure characteristics match image dimensions to avoid DngCreator crash.
            // This is especially important for logical multi-camera devices where the RAW might come from a physical sub-camera.
            if (image.format == ImageFormat.RAW_SENSOR && result != null) {
                val checkMatch = { chars: CameraCharacteristics? ->
                    val pixelArray = chars?.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    val preCorrectionArray = chars?.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
                    val matchesPixelArray = pixelArray != null &&
                            pixelArray.width == width &&
                            pixelArray.height == height
                    val matchesPreCorrectionArray = preCorrectionArray != null &&
                            preCorrectionArray.width() == width &&
                            preCorrectionArray.height() == height
                    matchesPixelArray || matchesPreCorrectionArray
                }

                if (!checkMatch(effectiveCharacteristics)) {
                    PLog.d(TAG, "RAW dimensions $width x $height mismatch logical characteristics. Searching physical cameras...")
                    for ((physicalId, physicalResult) in result.physicalCameraResults) {
                        try {
                            val physicalChars = getCameraCharacteristicsCached(physicalId)
                            if (checkMatch(physicalChars)) {
                                PLog.i(TAG, "Found matching physical camera $physicalId for RAW image")
                                effectiveCharacteristics = physicalChars
                                effectiveResult = physicalResult
                                break
                            }
                        } catch (e: Exception) {
                            PLog.w(TAG, "Failed to check physical camera $physicalId", e)
                        }
                    }
                }
            }

//            if (
//                image.format == ImageFormat.RAW_SENSOR &&
//                effectiveCharacteristics != null &&
//                effectiveResult != null
//            ) {
//                logRawColorMatrices(effectiveCharacteristics, effectiveResult)
//            }

            // 构建 CaptureInfo
            val captureInfo = rebuildCaptureInfo(
                result = effectiveResult ?: result,
                imageWidth = width,
                imageHeight = height,
                latitude = shot.state.latitude,
                longitude = shot.state.longitude,
                effectiveCharacteristics = effectiveCharacteristics,
                captureState = shot.state,
                captureTime = shot.captureTime,
                exposureCompensationStep = shot.exposureCompensationStep,
            ).copy(livePhotoVideoStartTimestampUs = shot.livePhotoStartTimestampUs)
            val frameResult = effectiveResult ?: result
            val sensorTimestampNs = frameResult?.get(CaptureResult.SENSOR_TIMESTAMP) ?: image.timestamp
            val exposureTimeNs = frameResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
            val sensitivityIso = frameResult?.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
            val frozenMetadata = pendingFrameMetadata.remove(image.timestamp)
            val gyroWindow = frozenMetadata?.gyroWindow
                ?: burstGyroRecorder.exposureWindow(
                    sensorTimestampNs,
                    gyroWindowExposureNs(frameResult, sensorTimestampNs, shot),
                )
            val sensitivityLimits = sensorSensitivityLimits(effectiveCharacteristics)
            val frameMetadata = if (image.format == ImageFormat.RAW_SENSOR && frameResult != null) {
                CapturedFrameMetadata(
                    sensorTimestampNs = sensorTimestampNs,
                    frameNumber = frozenMetadata?.frameNumber ?: result?.frameNumber ?: -1L,
                    exposureTimeNs = exposureTimeNs,
                    sensitivityIso = sensitivityIso,
                    minimumSensitivityIso = sensitivityLimits.minimumIso
                        .takeIf { it > 0 }
                        ?: frozenMetadata?.minimumSensitivityIso
                        ?: 0,
                    maximumAnalogSensitivityIso = sensitivityLimits.maximumAnalogIso
                        .takeIf { it > 0 }
                        ?: frozenMetadata?.maximumAnalogSensitivityIso
                        ?: 0,
                    exposureProduct = RawExposureMath.productOrNull(
                        exposureTimeNs,
                        sensitivityIso,
                    ) ?: frozenMetadata?.exposureProduct ?: 0.0,
                    desiredExposureProduct = RawExposureMath.productOrNull(
                        frameResult.request.get(CaptureRequest.SENSOR_EXPOSURE_TIME),
                        frameResult.request.get(CaptureRequest.SENSOR_SENSITIVITY),
                    ) ?: frozenMetadata?.desiredExposureProduct,
                    focusDistanceDiopters = frameResult
                        .get(CaptureResult.LENS_FOCUS_DISTANCE)
                        ?: Float.NaN,
                    lensState = frameResult.get(CaptureResult.LENS_STATE),
                    rollingShutterSkewNs = frameResult.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW),
                    gyroWindow = gyroWindow,
                    // Resolve noise from the same result as this RAW's ISO/CFA. The early
                    // snapshot belongs to the logical result, while dimension matching above
                    // may select a different physical sensor. If its profile is absent, let
                    // the noise resolver use its declared fallback, not another sensor's model.
                    channelNoiseProfile = captureChannelNoiseProfile(frameResult),
                    multiFrameCaptureRole = frozenMetadata?.multiFrameCaptureRole
                        ?: (frameResult.request.tag as? MultiFrameCaptureRole),
                    dynamicBlackLevelByCfaPosition = frameResult
                        .get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
                        ?.takeIf { it.size >= 4 }
                        ?.copyOf(4)
                        ?: frozenMetadata?.dynamicBlackLevelByCfaPosition?.copyOf(),
                )
            } else {
                null
            }

            if (shot.continuousBurst) {
                val callback = onImageCaptured
                if (callback != null) {
                    callback(image, captureInfo, effectiveCharacteristics, frameResult, frameMetadata)
                } else {
                    image.close()
                }
            } else {
                shot.frames.put(
                    image.timestamp,
                    CapturedPhotoFrame(
                        image,
                        captureInfo,
                        effectiveCharacteristics,
                        frameResult,
                        frameMetadata,
                        gyroWindow = gyroWindow,
                    ),
                )?.image?.close()
            }
            deliverPhotoCaptureIfReady(shot)
        } catch (e: Exception) {
            PLog.e(TAG, "Error processing joined capture data", e)
            image.close()
            failPhotoCapture(shot, "failed to construct captured frame")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        slowMotionDebugReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        slowMotionDebugReceiver = null
        val handler = cameraHandler
        if (handler != null) {
            val latch = java.util.concurrent.CountDownLatch(1)
            releaseCloseLatch = latch
            handler.post {
                closeCamera()
                if (cameraDeviceLifecycle == CameraDeviceLifecycle.CLOSED) {
                    latch.countDown()
                }
            }
            try {
                latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                PLog.e(TAG, "Interrupted while waiting for camera close during release", e)
            } finally {
                releaseCloseLatch = null
            }
        } else {
            closeCamera()
        }
        burstGyroRecorder.release()
        videoRecorder.release()
        stopBackgroundThread()
        cameraDiscovery.clearCache()
    }

    /**
     * 设置是否启用 Live Photo
     */
    fun setUseLivePhoto(enabled: Boolean) {
        _state.update { s -> s.copy(useLivePhoto = enabled) }
        if (enabled && _state.value.captureMode == CaptureMode.PHOTO) {
            livePhotoRecorder.startRecording()
        } else {
            livePhotoRecorder.stopRecording()
        }
    }

    fun setUseP010(enabled: Boolean) {
        _state.update { s -> s.copy(useP010 = enabled) }
    }

    fun setUseP3ColorSpace(enabled: Boolean) {
        _state.update { s -> s.copy(useP3ColorSpace = enabled) }
    }

    fun setLocation(latitude: Double?, longitude: Double?) {
        PLog.d(TAG, "setLocation: $latitude, $longitude")
        _state.update { s -> s.copy(latitude = latitude, longitude = longitude) }
    }

    /**
     * 执行 Live Photo 快照（在按下快门时尽早调用，以确定“之前”的时间范围）
     */
    fun snapshotLivePhoto() {
        livePhotoVideoStartTimestampUs = livePhotoRecorder.snapshot()
    }

    /**
     * 开始后台录制导出视频（在获得照片精确时间戳后调用）
     * @param timestampUs 精确的拍照瞬间时间戳（纳秒/1000）
     */
    fun recordLivePhotoVideo(
        timestampUs: Long? = null,
        captureStartTimestampUs: Long? = null,
        onCaptured: ((java.io.File, Long) -> Unit)? = null,
    ) {
        livePhotoRecorder.recordVideo(timestampUs, captureStartTimestampUs) { file, timestamp ->
            onCaptured?.invoke(file, timestamp)
            onLivePhotoVideoCaptured?.invoke(file, timestamp)
        }
    }

    /**
     * 启动连拍
     */
    fun startBurstCapture(onAccepted: (() -> Unit)? = null) {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { startBurstCapture(onAccepted) }
            return
        }
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val reader = imageReader ?: return
        val isStartingNewBurst = !_state.value.burstCapturing
        if (isStartingNewBurst && (_state.value.isCapturing || photoCaptures.values.any { it.continuousBurst })) return
        if (!isStartingNewBurst && onAccepted != null) return
        val shot = pendingContinuousBurstCapture ?: reservePhotoCapture(
            _state.value.copy(burstCapturing = true, isCapturing = true),
            reader,
            continuousBurst = true,
        )
        pendingContinuousBurstCapture = null
        if (shot == null) {
            if (!isStartingNewBurst) {
                burstCapturing = false
                _state.update { it.copy(burstCapturing = false, isCapturing = false) }
                resetPreviewAfterCapture()
            }
            return
        }
        if (isStartingNewBurst) {
            livePhotoVideoStartTimestampUs = null
            PLog.d(TAG, "Start Burst Capture")
            _state.update { it.copy(burstCapturing = true, isCapturing = true) }
            burstCapturing = true
            try {
                onAccepted?.invoke()
            } catch (e: Exception) {
                failPhotoCapture(shot, "continuous burst acceptance callback failed")
                burstCapturing = false
                _state.update { it.copy(burstCapturing = false, isCapturing = false) }
                resetPreviewAfterCapture()
                PLog.e(TAG, "Continuous burst acceptance callback failed", e)
                return
            }
        }
        if (isStartingNewBurst && shouldUseContinuousBurstTorch(_state.value)) {
            pendingContinuousBurstCapture = shot
            PLog.d(TAG, "Starting continuous burst torch warm-up")
            runMultiFrameTorchWarmupSequence {
                if (!_state.value.burstCapturing) return@runMultiFrameTorchWarmupSequence
                isContinuousBurstTorchActive = true
                startBurstCapture()
            }
            return
        }

        try {
            // Apply capture intent
            val isRawCapture = isRawCaptureReader(imageReader)
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                imageReader?.surface?.let { addTarget(it) }
                if (!isRawCapture && shouldMirrorStillCaptureToPreview()) {
                    previewSurface?.let { addTarget(it) }
                } else if (isRawCapture) {
                    PLog.d(TAG, "RAW burst capture uses RAW target only to avoid unstable RAW+preview still requests")
                }

                applyBaseCameraSettings(this, isCapture = true, isRawCapture = isRawCapture)

                if (isContinuousBurstTorchActive) {
                    if (_state.value.isAutoExposure) {
                        set(
                            CaptureRequest.CONTROL_AE_MODE,
                            resolveSupportedAeMode(CaptureRequest.CONTROL_AE_MODE_ON)
                        )
                        val aeLockAvailable = getActiveOpenCameraCharacteristics()
                            ?.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
                        if (aeLockAvailable) {
                            set(CaptureRequest.CONTROL_AE_LOCK, true)
                        }
                    }
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }

                setAePrecaptureTriggerIfSupported(
                    this,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
                )

                builder.get(CaptureRequest.CONTROL_AF_MODE)?.let { set(CaptureRequest.CONTROL_AF_MODE, it) }
                builder.get(CaptureRequest.LENS_FOCUS_DISTANCE)?.let { set(CaptureRequest.LENS_FOCUS_DISTANCE, it) }
                builder.get(CaptureRequest.CONTROL_AF_REGIONS)?.let { set(CaptureRequest.CONTROL_AF_REGIONS, it) }
                builder.get(CaptureRequest.CONTROL_AE_REGIONS)?.let { set(CaptureRequest.CONTROL_AE_REGIONS, it) }
            }

            val request = captureBuilder.build()
            val requests = mutableListOf<CaptureRequest>()
            for (i in 0 until BURST_CAPTURE_BATCH_SIZE) {
                requests.add(request)
            }
            submitPhotoCapture(shot, session, requests)
        } catch (e: Exception) {
            failPhotoCapture(shot, "continuous burst submission failed")
            PLog.e(TAG, "Failed to start hardware burst capture", e)
            burstCapturing = false
            _state.update { s -> s.copy(burstCapturing = false, isCapturing = false) }
            resetPreviewAfterCapture()
        }
    }

    private fun shouldMirrorStillCaptureToPreview(): Boolean {
        val isLivePhotoCapture = _state.value.useLivePhoto || _state.value.isCapturingLivePhoto
        if (isLivePhotoCapture) {
            PLog.i(
                TAG,
                "Skipping preview target on still capture to avoid Live Photo flash frame and preview flicker"
            )
            return false
        }
        return true
    }

    private fun checkBurstCaptureContinue() {
        if (!state.value.burstCapturing) return
        if (CAPTURE_READER_MAX_IMAGES - openImagesCount.get() - reservedImageCount() < BURST_CAPTURE_BATCH_SIZE) {
            burstCapturing = false
            _state.update { it.copy(burstCapturing = false, isCapturing = false) }
            resetPreviewAfterCapture()
            return
        }
        startBurstCapture()
    }

    /**
     * 停止连拍
     */
    fun stopBurstCapture() {
        val handler = cameraHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post {
                stopBurstCapture()
            }
            return
        }
        if (!_state.value.burstCapturing) return
        PLog.d(TAG, "Stop Burst Capture")
        val session = captureSession
        // Disable continuation before aborting; completion callbacks must not queue another batch.
        burstCapturing = false
        _state.update { it.copy(burstCapturing = false, isCapturing = false) }
        try {
            session?.abortCaptures()
        } catch (e: CameraAccessException) {
            PLog.w(TAG, "Camera inaccessible during burst stop", e)
            invalidateCaptureSession(session, "camera inaccessible during burst stop")
        } catch (e: IllegalStateException) {
            // The framework can close the session before its onClosed callback reaches us.
            PLog.w(TAG, "Capture session closed during burst stop", e)
            invalidateCaptureSession(session, "session closed during burst stop")
        } finally {
            resetPreviewAfterCapture()
        }
    }
}
