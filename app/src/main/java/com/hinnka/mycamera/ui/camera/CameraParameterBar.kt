package com.hinnka.mycamera.ui.camera

import android.hardware.camera2.CameraMetadata
import android.widget.Toast
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraState
import com.hinnka.mycamera.camera.CameraUtils
import com.hinnka.mycamera.camera.DeviceQuirks
import com.hinnka.mycamera.video.CaptureMode
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue


@Composable
fun CameraParameterBar(
    state: CameraState,
    selectedParameter: CameraParameter?,
    onParameterClick: (CameraParameter) -> Unit,
    modifier: Modifier = Modifier,
    // A sideways drag on a value changes it through its ruler; called first with the dragged value.
    scrub: RulerScrub? = null,
    onScrubStart: (CameraParameter) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cameraParameterItems(state).forEach { item ->
            ParameterItem(
                item = item,
                isSelected = selectedParameter == item.parameter,
                onClick = { onParameterClick(item.parameter) },
                modifier = Modifier.weight(1f),
                scrub = scrub?.takeIf { item.isEnabled },
                onScrubStart = { onScrubStart(item.parameter) }
            )
        }
    }
}

internal data class CameraParameterItem(
    val parameter: CameraParameter,
    val label: String,
    val value: String,
    val isEnabled: Boolean = true,
    val isWarning: Boolean = false,
    /** Shown when a disabled item is tapped, so the tap is never silent. */
    @StringRes val disabledMessage: Int? = null,
    /** Set by hand rather than by the camera: the value always shows, with an amber dot. */
    val isManual: Boolean = false,
    /** Always on the picture's bottom edge while the panel is closed, as this text (Night's plan line). */
    val lineText: String? = null,
)

@Composable
internal fun cameraParameterItems(state: CameraState): List<CameraParameterItem> {
    // The preview's over-exposure warning is about the RAW and video pipelines.
    val warnPreviewLimit = state.captureMode == CaptureMode.VIDEO ||
        (state.useRaw && state.isRawSupported)
    val evManual = state.exposureCompensation != 0
    val shutterManual = !state.isShutterSpeedAuto
    val isoManual = !state.isIsoAuto
    val focusManual = !state.isAutoFocus
    val wbManual = state.awbMode != CameraMetadata.CONTROL_AWB_MODE_AUTO
    // A value on auto shows the camera's live reading. Readings arrive with every preview frame, so they
    // are sampled a few times a second and shown in standard steps, which keeps the numbers calm.
    val liveShutter = sampled(state.shutterSpeed)
    val liveIso = sampled(state.iso)
    val liveFocus = sampled(state.focusDistance)
    val liveWb = sampled(state.actualAwbTemperature)
    // Portrait leads with its aperture, always shown like a value set by hand.
    val aperture = if (showsPortraitAperture(state)) listOf(
        CameraParameterItem(
            CameraParameter.APERTURE,
            stringResource(R.string.camera_parameter_aperture),
            stringResource(R.string.virtual_aperture_viewfinder_hint, formatApertureValue(state.virtualAperture)),
            isManual = true
        )
    ) else emptyList()
    // Night leads with its frame count, and its line says the plan.
    val frames = if (showsNightFrames(state)) listOf(
        CameraParameterItem(
            CameraParameter.FRAMES,
            stringResource(R.string.night_frames),
            state.nightFrames.toString(),
            isManual = state.nightFrameCount > 0,
            lineText = nightPlanLine(state)
        )
    ) else emptyList()
    return frames + aperture + listOfNotNull(
        CameraParameterItem(
            CameraParameter.EXPOSURE_COMPENSATION,
            stringResource(R.string.camera_parameter_exposure),
            CameraUtils.formatExposureCompensation(state.exposureCompensation, state.getExposureCompensationStep()),
            isEnabled = state.isAutoExposure,
            isManual = evManual
        ),
        CameraParameterItem(
            CameraParameter.SHUTTER_SPEED,
            stringResource(R.string.camera_parameter_shutter),
            if (shutterManual) formatShutterSpeedValue(state.shutterSpeed) else formatShutterReading(liveShutter),
            isEnabled = state.isManualSensorSupported,
            isWarning = warnPreviewLimit && state.isPreviewExposureLimited(),
            disabledMessage = R.string.manual_sensor_unavailable,
            isManual = shutterManual
        ),
        CameraParameterItem(
            CameraParameter.ISO,
            stringResource(R.string.camera_parameter_iso),
            when {
                isoManual -> state.iso.toString()
                // Without manual sensor control (this phone's front camera) the reported ISO barely moves and
                // lies far outside the camera's own range, so it would read as a fixed number.
                !state.isManualSensorSupported -> stringResource(R.string.camera_parameter_focus_auto)
                else -> nearestIsoStep(liveIso).toString()
            },
            isEnabled = state.isManualSensorSupported,
            disabledMessage = R.string.manual_sensor_unavailable,
            isManual = isoManual
        ),
        // No manual focus in Portrait; one still set from another mode shows, so it can be reset.
        if (state.isPortraitMode && !focusManual) null else CameraParameterItem(
            CameraParameter.FOCUS,
            stringResource(R.string.camera_parameter_focus),
            when {
                focusManual -> formatFocusDistance(state.focusDistance)
                // Locked by a tap or long press: the distance it locked at.
                state.isFocusLocked && liveFocus > 0f -> formatFocusDistance(liveFocus)
                // Focusing on its own: a moving distance says little, so it reads Auto.
                else -> stringResource(R.string.camera_parameter_focus_auto)
            },
            isEnabled = state.minimumFocusDistance > 0f,
            isManual = focusManual
        ),
        CameraParameterItem(
            CameraParameter.WHITE_BALANCE,
            stringResource(R.string.camera_parameter_white_balance),
            formatWhiteBalanceValue(state, liveWb),
            isEnabled = state.canAdjustWhiteBalance || state.actualAwbTemperature != null,
            isManual = wbManual
        )
    )
}

/** [value], sampled at most every [periodMs]. */
@Composable
private fun <T> sampled(value: T, periodMs: Long = 250L): T {
    val latest by rememberUpdatedState(value)
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(periodMs) {
        while (true) {
            shown = latest
            delay(periodMs)
        }
    }
    return shown
}

// Shutter speeds and ISO in the usual third-stop steps.
private val ShutterDenominators = intArrayOf(
    8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320, 250, 200, 160,
    125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4, 3
)
private val IsoSteps = intArrayOf(
    25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500,
    3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600
)

/** A live shutter reading as the nearest standard step, such as 1/60; long exposures as seconds. */
internal fun formatShutterReading(exposureNs: Long): String {
    if (exposureNs <= 0L) return formatShutterSpeedValue(exposureNs)
    val denominator = 1_000_000_000.0 / exposureNs
    if (denominator < 2.5) return CameraUtils.formatShutterSpeed(exposureNs)
    val nearest = ShutterDenominators.minBy { abs(ln(it / denominator)) }
    return "1/$nearest"
}

/** A live ISO reading as the nearest standard third-stop value, such as 400. */
internal fun nearestIsoStep(iso: Int): Int =
    if (iso <= 0) iso else IsoSteps.minBy { abs(ln(it.toDouble() / iso)) }

@Composable
internal fun ParameterItem(
    item: CameraParameterItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
    scrub: RulerScrub? = null,
    onScrubStart: () -> Unit = {},
) {
    val context = LocalContext.current
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(if (pressed) 60 else 180), label = "press")
    val valueColor by animateColorAsState(
        targetValue = when {
            item.isWarning -> Color(0xFFFF6666)
            !item.isEnabled -> Color.White.copy(alpha = 0.38f)
            isSelected -> ModeAmber
            else -> Color.White
        },
        label = "parameterValueColor"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !item.isEnabled -> Color.White.copy(alpha = 0.3f)
            isSelected -> Color.White
            else -> Color.White.copy(alpha = 0.78f)
        },
        label = "parameterLabelColor"
    )
    Box(
        modifier = modifier
            .then(
                if (vertical) Modifier.size(48.dp, 76.dp)
                else Modifier.height(CameraControlsLayoutDefaults.ParameterBarHeight)
            )
            .semantics { selected = isSelected }
            .then(
                if (scrub != null) {
                    Modifier.pointerInput(item.parameter) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                onScrubStart()
                                scrub.start(item.parameter)
                            },
                            onDragEnd = { scrub.end() },
                            onDragCancel = { scrub.end() },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                scrub.dragBy(delta)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            // On the picture a pressed box looks out of place: the value dims under the finger instead.
            .clickable(
                interactionSource = pressSource,
                indication = null,
                enabled = item.isEnabled || item.disabledMessage != null,
                role = Role.Button
            ) {
                if (item.isEnabled) onClick()
                else item.disabledMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            }
            .graphicsLayer { alpha = pressAlpha },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (vertical) {
                Modifier.requiredSize(76.dp, 48.dp).rotate(90f)
            } else {
                Modifier.fillMaxSize()
            },
            contentAlignment = Alignment.Center
        ) {
            // Text only, with shadows to read over the picture: a small name above, the value below.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.label,
                    color = labelColor,
                    fontSize = 10.5.sp,
                    style = TextStyle(shadow = ViewfinderTextShadow),
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.value,
                        color = valueColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = ViewfinderTextShadow),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (item.isManual) ManualDot(Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

internal fun formatShutterSpeedValue(value: Long): String {
    return when {
        value <= 0L -> "0"
        value < 1_000_000_000L -> "1/${(1_000_000_000.0 / value).roundToLong()}"
        else -> CameraUtils.formatShutterSpeed(value)
    }
}

internal fun formatFocusDistance(value: Float): String {
    return if (value <= 0.01f) "∞"
    else {
        val meters = 1.0f / value
        if (meters >= 1.0f) String.format("%.1fm", meters)
        else String.format("%dcm", (meters * 100).toInt())
    }
}

@Composable
private fun formatWhiteBalanceValue(state: CameraState, liveTemperature: Int?): String {
    // The reading is an estimate from the camera's colour gains: whole hundreds of Kelvin are enough.
    val measuredValue = liveTemperature?.let { "${(it + 50) / 100 * 100}K" }
    return when (state.awbMode) {
        CameraMetadata.CONTROL_AWB_MODE_OFF -> "${state.awbTemperature}K"
        CameraMetadata.CONTROL_AWB_MODE_AUTO -> measuredValue ?: stringResource(R.string.camera_focus_auto)
        else -> measuredValue ?: stringResource(R.string.unknown)
    }
}

/** Marks a value set by hand. */
@Composable
private fun ManualDot(modifier: Modifier = Modifier) {
    Box(modifier.size(5.dp).background(ModeAmber, CircleShape))
}

/**
 * The values set by hand while the manual panel is closed: plain numbers with an amber dot, centred on the
 * picture's bottom edge like the active-setting marks on its top edge. A tap opens the panel at that value.
 */
@Composable
internal fun ManualValuesLine(
    state: CameraState,
    onValueClick: (CameraParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = cameraParameterItems(state).filter { it.isManual || it.lineText != null }
    if (items.isEmpty()) return
    val textStyle = TextStyle(shadow = ViewfinderTextShadow, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Color.White)
    Row(
        modifier = modifier.height(TopEdgeLineHeight),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .height(TopEdgeLineHeight)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        onValueClick(item.parameter)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ISO and EV are bare numbers, so they keep their name; the others read by their unit.
                val text = when (item.parameter) {
                    CameraParameter.ISO -> "${item.label} ${item.value}"
                    CameraParameter.EXPOSURE_COMPENSATION -> "${item.value} ${item.label}"
                    else -> item.lineText ?: item.value
                }
                Text(text, style = textStyle, maxLines = 1, softWrap = false)
                if (item.isManual) ManualDot(Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** Night shows and sets its frame count with the manual values. */
internal fun showsNightFrames(state: CameraState): Boolean =
    state.isNightMode && state.captureMode == CaptureMode.PHOTO && !state.useRaw

/**
 * Night's one line: before the press the frame count and about how long to hold still; during the shot
 * "Hold still" while it measures the shake, then the plan it chose.
 */
@Composable
private fun nightPlanLine(state: CameraState): String {
    val plan = state.nightShotPlan
    return when {
        state.isCapturing && state.nightSteadying -> stringResource(R.string.night_hold_still)
        state.isCapturing && plan != null ->
            stringResource(R.string.night_line_plan, plan.frames, formatShutterSpeedValue(plan.exposureTimeNs))
        state.isCapturing -> stringResource(R.string.night_line_frames_capturing, state.activeMultiFrameCount)
        else -> stringResource(R.string.night_line, state.nightFrames, formatHoldSeconds(nightHoldSeconds(state)))
    }
}

/**
 * About how long a Night shot takes: the back camera measures the shake for 0.5 s, then takes frames of about
 * 1/15 s (Fast: 1/30 s); lilac's front camera starts at once and its frames with the JPEG come about 240 ms apart.
 */
internal fun nightHoldSeconds(state: CameraState): Float {
    val planned = state.isManualSensorSupported
    val spacing = when {
        state.nightFast -> 1f / 30f
        planned -> 1f / 15f
        state.frontStillJpeg && DeviceQuirks.isLilac -> 0.24f
        else -> 1f / 30f
    }
    return (if (planned) 0.5f else 0f) + state.nightFrames * spacing
}

/** Rounded up to the next half second: "1.5", "3". */
internal fun formatHoldSeconds(seconds: Float): String {
    val halves = kotlin.math.ceil(seconds * 2f).toInt().coerceAtLeast(1)
    return if (halves % 2 == 0) (halves / 2).toString() else "${halves / 2}.5"
}

/** Portrait photos show and set their virtual aperture with the manual values. */
internal fun showsPortraitAperture(state: CameraState): Boolean =
    state.isPortraitMode && state.captureMode == CaptureMode.PHOTO && state.isVirtualApertureEnabled
