package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.hinnka.mycamera.utils.VibrationHelper
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** Parameters supported by the manual control ruler. */
enum class CameraParameter {
    EXPOSURE_COMPENSATION,
    SHUTTER_SPEED,
    ISO,
    FOCUS,
    WHITE_BALANCE,
    /** Portrait's virtual aperture: the blur strength, in the viewfinder and in the saved photo. */
    APERTURE
}

/** Third stops from f/1.0 to f/16, as on a lens's aperture ring. */
internal val PortraitApertureStops = listOf(
    1.0f, 1.1f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.2f, 2.5f, 2.8f, 3.2f, 3.5f, 4.0f, 4.5f, 5.0f, 5.6f,
    6.3f, 7.1f, 8.0f, 9.0f, 10f, 11f, 13f, 14f, 16f
)
private val FullApertureStops = setOf(1.0f, 1.4f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f, 11f, 16f)

internal val CameraParameterRulerHeight = 44.dp
private val ParameterRulerTickSpacing = 12.dp

/**
 * A sideways drag on a manual value that moves that value's ruler. The ruler attaches itself while it is
 * shown; a drag that starts before the ruler appears is kept and applied once it attaches.
 */
@Stable
class RulerScrub {
    /** A drag on a value is in progress. */
    var active by mutableStateOf(false)
        private set
    private var target: CameraParameter? = null
    private var pending = 0f
    private var attached: CameraParameter? = null
    private var onStart: (() -> Unit)? = null
    private var onDrag: ((Float) -> Unit)? = null

    fun start(parameter: CameraParameter) {
        target = parameter
        pending = 0f
        active = true
        if (attached == parameter) onStart?.invoke()
    }

    fun dragBy(px: Float) {
        if (attached == target) onDrag?.invoke(px) else pending += px
    }

    fun end() {
        active = false
    }

    internal fun attach(parameter: CameraParameter, start: () -> Unit, drag: (Float) -> Unit) {
        attached = parameter
        onStart = start
        onDrag = drag
        if (active && target == parameter) {
            start()
            if (pending != 0f) drag(pending)
            pending = 0f
        }
    }

    internal fun detach(parameter: CameraParameter) {
        if (attached != parameter) return
        attached = null
        onStart = null
        onDrag = null
    }
}

/**
 * A slim ruler above the manual values: ticks, numbers on the main steps and an amber needle at the current
 * value, transparent with shadows. Drag it, or the value below it, to change the value; tap a tick to jump
 * there; double-tap to go back to auto. The A at its start switches auto; on the focus ruler an H at its end
 * switches hyperfocal focus.
 */
@Composable
fun ParameterRuler(
    parameter: CameraParameter,
    currentValue: Float,
    minValue: Float,
    maxValue: Float,
    isAdjustable: Boolean,
    isAutoMode: Boolean,
    showAutoButton: Boolean,
    isAutoModeToggleEnabled: Boolean = true,
    valueStep: Float = 1f / 3f,
    showHyperfocalButton: Boolean = false,
    hyperfocalEnabled: Boolean = false,
    onHyperfocalToggle: ((Boolean) -> Unit)? = null,
    onValueChange: (Float) -> Unit,
    onAutoModeToggle: () -> Unit,
    onSetAuto: () -> Unit,
    scrub: RulerScrub? = null,
    modifier: Modifier = Modifier
) {
    val scaleValues = remember(parameter, minValue, maxValue, valueStep) {
        getScaleValues(parameter, minValue, maxValue, valueStep)
    }
    var selectedValue by remember(parameter, scaleValues) { mutableFloatStateOf(currentValue) }
    var scrollIndex by remember(parameter, scaleValues) {
        mutableFloatStateOf(closestScaleIndex(scaleValues, currentValue).toFloat())
    }
    var isDragging by remember(parameter, scaleValues) { mutableStateOf(false) }
    val currentValueState by rememberUpdatedState(currentValue)
    val adjustableState by rememberUpdatedState(isAdjustable && scaleValues.size > 1)
    val autoModeState by rememberUpdatedState(isAutoMode)
    val onValueChangeState by rememberUpdatedState(onValueChange)
    val onSetAutoState by rememberUpdatedState(onSetAuto)
    val context = LocalContext.current
    // This phone does not play the system's tick effects: a short pulse, spaced, on the labelled steps only.
    val ticks = remember(context) { SpacedBuzz(VibrationHelper(context)) }
    val spacingPx = with(LocalDensity.current) { ParameterRulerTickSpacing.toPx() }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(currentValue, isDragging, scaleValues) {
        // Capture results continue to arrive during a gesture; they must not pull the ruler back.
        if (!isDragging) {
            selectedValue = currentValue
            scrollIndex = closestScaleIndex(scaleValues, currentValue).toFloat()
        }
    }

    fun selectValue(value: Float, force: Boolean = false) {
        val clamped = value.coerceIn(minValue, maxValue)
        val changed = clamped != selectedValue
        selectedValue = clamped
        if (changed && isMajorStep(parameter, clamped, scaleValues.indexOf(clamped))) {
            ticks.buzz(VibrationHelper.LIGHT_TICK_MS)
        }
        // Manual setters also leave auto mode, including when the selected value is unchanged.
        if (changed || force || autoModeState || clamped != currentValueState) {
            onValueChangeState(clamped)
        }
    }

    fun startDrag() {
        isDragging = adjustableState
        if (isDragging) {
            scrollIndex = closestScaleIndex(scaleValues, currentValueState).toFloat()
            selectValue(scaleValues[scrollIndex.roundToInt()], force = true)
        }
    }

    fun dragBy(delta: Float) {
        if (!isDragging || !adjustableState) return
        scrollIndex = (scrollIndex - delta / spacingPx).coerceIn(0f, scaleValues.lastIndex.toFloat())
        val value = scaleValues[scrollIndex.roundToInt()]
        if (value != selectedValue) selectValue(value)
    }

    // A drag on the value below moves this ruler.
    if (scrub != null) {
        val startState by rememberUpdatedState(::startDrag)
        val dragState by rememberUpdatedState<(Float) -> Unit>(::dragBy)
        DisposableEffect(scrub, parameter, scaleValues) {
            scrub.attach(parameter, { startState() }, { dragState(it) })
            onDispose { scrub.detach(parameter) }
        }
        LaunchedEffect(scrub.active) {
            if (!scrub.active) isDragging = false
        }
    }

    val displayedValue = if (isDragging) selectedValue else currentValue
    Row(
        modifier = modifier.fillMaxWidth().height(CameraParameterRulerHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            if (showAutoButton) {
                RulerMark(text = stringResource(R.string.camera_parameter_auto), on = isAutoMode, enabled = isAutoModeToggleEnabled, onClick = onAutoModeToggle)
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()
                .semantics {
                    stateDescription = formatParameterValue(parameter, displayedValue)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        closestScaleIndex(scaleValues, displayedValue).toFloat(),
                        0f..scaleValues.lastIndex.toFloat(),
                        (scaleValues.size - 2).coerceAtLeast(0)
                    )
                    if (isAdjustable && scaleValues.size > 1) {
                        setProgress { index ->
                            selectValue(scaleValues[index.roundToInt().coerceIn(scaleValues.indices)], force = true)
                            true
                        }
                    }
                }
                .pointerInput(parameter, scaleValues) {
                    detectTapGestures(
                        onDoubleTap = { onSetAutoState() },
                        onTap = { position ->
                            if (adjustableState) {
                                val index = (scrollIndex + (position.x - size.width / 2f) / spacingPx)
                                    .roundToInt().coerceIn(scaleValues.indices)
                                selectValue(scaleValues[index], force = true)
                            }
                        }
                    )
                }
                .pointerInput(parameter, scaleValues) {
                    detectHorizontalDragGestures(
                        onDragStart = { startDrag() },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, delta ->
                            if (isDragging) change.consume()
                            dragBy(delta)
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = size.width / 2f
                val shadow = Color.Black.copy(alpha = 0.6f)
                val dimmed = if (isAdjustable) 1f else 0.5f
                val labelStyle = TextStyle(
                    color = Color.White.copy(alpha = dimmed),
                    fontSize = 10.5.sp,
                    shadow = ViewfinderTextShadow
                )
                scaleValues.indices.forEach { index ->
                    val x = center + (index - scrollIndex) * spacingPx
                    if (x < -40.dp.toPx() || x > size.width + 40.dp.toPx()) return@forEach
                    val major = isMajorStep(parameter, scaleValues[index], index)
                    val top = if (major) 6.dp.toPx() else 10.dp.toPx()
                    val bottom = top + if (major) 16.dp.toPx() else 10.dp.toPx()
                    drawLine(shadow, Offset(x + 1f, top + 1f), Offset(x + 1f, bottom + 1f), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = (if (major) 1f else 0.7f) * dimmed), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                    if (major) {
                        val label = textMeasurer.measure(formatParameterValue(parameter, scaleValues[index]), labelStyle)
                        drawText(label, topLeft = Offset(x - label.size.width / 2f, 26.dp.toPx()))
                    }
                }
                // The needle marks the current value.
                drawLine(shadow, Offset(center + 1f, 2.dp.toPx() + 1f), Offset(center + 1f, 26.dp.toPx() + 1f), 2.5.dp.toPx(), StrokeCap.Round)
                drawLine(ModeAmber, Offset(center, 2.dp.toPx()), Offset(center, 26.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            if (showHyperfocalButton && onHyperfocalToggle != null) {
                RulerMark(
                    text = stringResource(R.string.camera_parameter_hyperfocal_mark),
                    on = hyperfocalEnabled,
                    enabled = true,
                    onClick = { onHyperfocalToggle(!hyperfocalEnabled) }
                )
            }
        }
    }
}

/** The labelled steps: whole EV, full stops of shutter and ISO, every 1000 K, every fifth focus step. */
private fun isMajorStep(parameter: CameraParameter, value: Float, index: Int): Boolean = when (parameter) {
    CameraParameter.EXPOSURE_COMPENSATION -> abs(value - value.roundToInt()) < 0.05f
    CameraParameter.SHUTTER_SPEED -> {
        val ns = value.toLong()
        if (ns >= 1_000_000_000L) (ns / 1_000_000_000L).toInt() in setOf(1, 2, 4, 8, 15, 30)
        else (1_000_000_000.0 / ns).roundToInt() in setOf(8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2)
    }
    CameraParameter.ISO -> value.roundToInt() in setOf(50, 100, 200, 400, 800, 1600, 3200, 6400, 12800)
    CameraParameter.WHITE_BALANCE -> value.roundToInt() % 1000 == 0
    CameraParameter.FOCUS -> index % 5 == 0
    CameraParameter.APERTURE -> value in FullApertureStops
}

/** A round 30 dp mark at the ruler's ends: amber when on. */
@Composable
private fun RulerMark(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            .then(
                if (on) Modifier.background(ModeAmber, CircleShape)
                else Modifier.border(1.5.dp, Color.White.copy(alpha = if (enabled) 0.85f else 0.3f), CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (on) Color(0xFF412402) else Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            style = if (on) TextStyle.Default else TextStyle(shadow = ViewfinderTextShadow)
        )
    }
}

private fun closestScaleIndex(values: List<Float>, value: Float): Int =
    values.indices.minByOrNull { abs(values[it] - value) } ?: 0

private fun formatParameterValue(parameter: CameraParameter, value: Float): String = when (parameter) {
    CameraParameter.EXPOSURE_COMPENSATION -> when {
        abs(value) < 0.05f -> "0"
        value > 0f -> String.format("+%.1f", value)
        else -> String.format("%.1f", value)
    }
    CameraParameter.SHUTTER_SPEED -> formatShutterSpeedValue(value.toLong())
    CameraParameter.ISO -> value.roundToInt().toString()
    CameraParameter.WHITE_BALANCE -> "${value.roundToInt()}K"
    CameraParameter.FOCUS -> formatFocusDistance(value)
    CameraParameter.APERTURE -> formatApertureValue(value)
}

/** 2 and 2.8, not 2.0. */
internal fun formatApertureValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)

private fun getScaleValues(parameter: CameraParameter, minValue: Float, maxValue: Float, valueStep: Float): List<Float> {
    if (maxValue <= minValue) return listOf(minValue)
    return when (parameter) {
        CameraParameter.EXPOSURE_COMPENSATION -> {
            (0..((maxValue - minValue) / valueStep).roundToInt()).map { minValue + it * valueStep }
        }

        CameraParameter.SHUTTER_SPEED -> {
            // Common shutter speeds in log scale
            // Values are in nanoseconds, show as fractions
            listOf(
                minValue,
                1_000_000_000L / 12000,  // 1/12000
                1_000_000_000L / 8000,  // 1/8000
                1_000_000_000L / 4000,
                1_000_000_000L / 3200,
                1_000_000_000L / 2500,
                1_000_000_000L / 2000,
                1_000_000_000L / 1600,
                1_000_000_000L / 1250,
                1_000_000_000L / 1000,
                1_000_000_000L / 800,
                1_000_000_000L / 640,
                1_000_000_000L / 500,
                1_000_000_000L / 400,
                1_000_000_000L / 320,
                1_000_000_000L / 250,
                1_000_000_000L / 200,
                1_000_000_000L / 160,
                1_000_000_000L / 125,
                1_000_000_000L / 100,
                1_000_000_000L / 80,
                1_000_000_000L / 60,
                1_000_000_000L / 50,
                1_000_000_000L / 40,
                1_000_000_000L / 30,
                1_000_000_000L / 25,
                1_000_000_000L / 20,
                1_000_000_000L / 15,
                1_000_000_000L / 13,
                1_000_000_000L / 10,
                1_000_000_000L / 8,
                1_000_000_000L / 6,
                1_000_000_000L / 5,
                1_000_000_000L / 4,
                1_000_000_000L / 3,
                1_000_000_000L / 2,
                1_000_000_000L / 1,
                1_000_000_000L * 2,
                1_000_000_000L * 3,
                1_000_000_000L * 4,
                1_000_000_000L * 5,
                1_000_000_000L * 6,
                1_000_000_000L * 8,
                1_000_000_000L * 10,
                1_000_000_000L * 13,
                1_000_000_000L * 15,
                1_000_000_000L * 20,
                1_000_000_000L * 25,
                1_000_000_000L * 30,
                maxValue
            ).toSet().map { it.toFloat() }.filter { it in minValue..maxValue }
        }

        CameraParameter.ISO -> {
            // Standard ISO values
            listOf(
                minValue,
                50f,
                64f,
                80f,
                100f,
                125f,
                160f,
                200f,
                250f,
                320f,
                400f,
                500f,
                640f,
                800f,
                1000f,
                1250f,
                1600f,
                2000f,
                2500f,
                3200f,
                4000f,
                5000f,
                6400f,
                8000f,
                12800f,
                maxValue
            )
                .toSet()
                .map { it }
                .filter { it in minValue..maxValue }
        }

        CameraParameter.WHITE_BALANCE -> {
            // Color temperature presets
            generateSequence(minValue) { it + 100f }
                .takeWhile { it <= maxValue }
                .filter { it in minValue..maxValue }
                .toList()
        }

        CameraParameter.APERTURE -> PortraitApertureStops

        CameraParameter.FOCUS -> {
            val steps = 20
            val list = mutableListOf<Float>()
            for (i in 0..steps) {
                list.add(minValue + (maxValue - minValue) * i / steps)
            }
            list
        }

    }.plus(listOf(minValue, maxValue)).filter { it in minValue..maxValue }.distinct().sorted()
}
