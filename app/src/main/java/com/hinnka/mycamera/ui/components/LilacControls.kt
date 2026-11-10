package com.hinnka.mycamera.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.camera.SpacedBuzz
import com.hinnka.mycamera.ui.camera.ViewfinderTextShadow
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.utils.VibrationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// The camera's warm greys, shared by the camera's quick settings and the photo editor.
internal val LilacText = Color(0xFFE8E6DF)
internal val LilacLabel = Color(0xFFC2C0B6)
internal val LilacSecondary = Color(0xFFB4B2A9)
internal val LilacDim = Color(0xFF888780)
internal val LilacIconTint = Color(0xFFC9C7BF)
internal val LilacTextStyle = TextStyle(shadow = ViewfinderTextShadow)

/**
 * A choice in a row of choices: amber text and a thin amber outline when selected. [fill] gives it a
 * see-through backing for use over a picture; an [action] chip (one that opens something) is grey.
 */
@Composable
internal fun LilacChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    height: Dp = 26.dp,
    fill: Color = Color.Transparent,
    fontSize: TextUnit = 12.5.sp,
    horizontalPadding: Dp = 6.dp,
    icon: ImageVector? = null,
    action: Boolean = false,
) {
    val shape = RoundedCornerShape(height / 2)
    val color = when {
        selected -> ModeAmber
        action -> LilacSecondary
        else -> LilacText
    }
    Row(
        modifier = modifier
            .widthIn(min = minWidth)
            .height(height)
            .clip(shape)
            .background(fill, shape)
            .then(if (selected) Modifier.border(1.25.dp, ModeAmber.copy(alpha = 0.8f), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (action) LilacDim else color, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

private val RulerSettleEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)  // ease-out cubic
private const val RULER_IDLE_MS = 1600L
private const val RULER_SETTLE_MS = 180
private val RulerNiceSteps = intArrayOf(1, 2, 4, 5, 10, 20, 25, 50, 100)

/** Spacing between two steps of a ruler with [count] values, in dp: wide for few steps, dense for many. */
internal fun lilacRulerStepDp(count: Int, stops: Boolean): Float =
    if (stops) 26f else (600f / (count - 1).coerceAtLeast(1)).roundToInt().toFloat().coerceIn(6f, 24f)

/** Every how many steps a ruler draws a label, so labels are at least 46 dp apart. */
internal fun lilacRulerLabelEvery(stepDp: Float): Int = RulerNiceSteps.firstOrNull { it * stepDp >= 46f } ?: 100

/**
 * The ruler for one number, laid over the bottom edge of a picture: ticks that slide under a fixed amber
 * needle and latch on [values], and a reset circle on the left that returns to [start]. [onChange] runs on
 * every new step while the finger moves, [onRelease] once when it lifts (do expensive work there). With
 * [stops] every value is a labelled stop that clicks (an aperture ring); otherwise the ruler clicks only when
 * the value crosses [start]. Untouched for 1.6 s, the ruler fades back so the picture shows through.
 */
@Composable
internal fun LilacRuler(
    values: List<Float>,
    value: Float,
    start: Float,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
    onRelease: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    stops: Boolean = false,
    onTouch: (Boolean) -> Unit = {},
) {
    if (values.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = remember(context) { SpacedBuzz(VibrationHelper(context)) }
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentStart by rememberUpdatedState(start)

    val stepDp = lilacRulerStepDp(values.size, stops)
    val labelEvery = lilacRulerLabelEvery(stepDp)
    val stepPx = with(density) { stepDp.dp.toPx() }
    fun indexOf(v: Float) = values.indices.minBy { abs(values[it] - v) }

    var index by remember(values) { mutableIntStateOf(indexOf(value)) }
    var dragging by remember { mutableStateOf(false) }
    // The strip's shift under the needle, in px: 0 puts the first value under it.
    val shift = remember(values) { Animatable(-index * stepPx) }
    // An outside change (reset, undo, another photo) moves the strip without a drag.
    LaunchedEffect(values, value) {
        if (dragging) return@LaunchedEffect
        val i = indexOf(value)
        index = i
        shift.snapTo(-i * stepPx)
    }

    var touchedAt by remember { mutableLongStateOf(0L) }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(touchedAt, dragging) {
        idle = false
        if (dragging) return@LaunchedEffect
        delay(RULER_IDLE_MS)
        idle = true
    }
    val fade by animateFloatAsState(if (idle) 0.4f else 1f, tween(300), label = "rulerIdle")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f))))
    ) {
        val atStart = values[index] == start
        Box(
            modifier = Modifier
                .offset(x = 8.dp, y = 5.dp)
                .size(36.dp)
                .graphicsLayer { alpha = fade * (if (atStart) 0.35f else 1f) }
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .clickable(role = Role.Button) {
                    touchedAt = System.nanoTime()
                    haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
                    onReset()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.UndoLine, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }

        BoxWithConstraints(
            modifier = Modifier
                .padding(start = 48.dp, end = 8.dp)
                .fillMaxSize()
                .graphicsLayer {
                    alpha = fade
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .pointerInput(values) {
                    val slop = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnTouch(true)
                        touchedAt = System.nanoTime()
                        val shift0 = shift.value
                        var moved = false
                        var dx = 0f
                        scope.launch { shift.stop() }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dx += change.positionChange().x
                            change.consume()
                            if (!moved && abs(dx) < slop) continue
                            if (!moved) {
                                moved = true
                                dragging = true
                            }
                            val last = values.size - 1
                            val s = (shift0 + dx).coerceIn(-last * stepPx - 24.dp.toPx(), 24.dp.toPx())
                            scope.launch { shift.snapTo(s) }
                            val i = (-s / stepPx).roundToInt().coerceIn(0, last)
                            if (i != index) {
                                index = i
                                val v = values[i]
                                if (stops || v == currentStart) haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
                                currentOnChange(v)
                            }
                        }
                        currentOnTouch(false)
                        touchedAt = System.nanoTime()
                        if (moved) {
                            dragging = false
                            scope.launch { shift.animateTo(-index * stepPx, tween(RULER_SETTLE_MS, easing = RulerSettleEasing)) }
                            currentOnRelease()
                        }
                    }
                }
        ) {
            val trackPx = constraints.maxWidth.toFloat()
            val needleX = trackPx * 151f / 304f
            val labelStyle = LilacTextStyle.copy(color = LilacText, fontSize = 10.5.sp)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dp = density.density
                val origin = needleX + shift.value
                values.forEachIndexed { i, v ->
                    val x = origin + i * stepPx
                    if (x < -30 * dp || x > trackPx + 30 * dp) return@forEachIndexed
                    val labelled = stops || i % labelEvery == 0
                    val major = labelled || v == 0f
                    drawRect(
                        color = if (major) Color.White else Color.White.copy(alpha = 0.65f),
                        topLeft = Offset(x - 0.5f * dp, (if (major) 7f else 12f) * dp),
                        size = Size(1f * dp, (if (major) 15f else 9f) * dp)
                    )
                    if (labelled) {
                        val text = textMeasurer.measure(label(v), labelStyle)
                        drawText(text, topLeft = Offset(x - text.size.width / 2f, 26f * dp))
                    }
                }
                // The ends of the track fade out over 18 dp.
                val edge = 18f * dp
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, edge),
                    size = Size(edge, size.height),
                    blendMode = BlendMode.DstIn
                )
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - edge, size.width),
                    topLeft = Offset(size.width - edge, 0f),
                    size = Size(edge, size.height),
                    blendMode = BlendMode.DstIn
                )
                drawRoundRect(
                    color = ModeAmber,
                    topLeft = Offset(needleX - 1.25f * dp, 3f * dp),
                    size = Size(2.5f * dp, 24f * dp),
                    cornerRadius = CornerRadius(1.25f * dp)
                )
            }
        }
    }
}
