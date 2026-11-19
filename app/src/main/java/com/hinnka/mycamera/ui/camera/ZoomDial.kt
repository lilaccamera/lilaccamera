package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraInfo
import com.hinnka.mycamera.camera.LensType
import com.hinnka.mycamera.data.ZoomControl
import com.hinnka.mycamera.ui.components.SonySecondary
import com.hinnka.mycamera.ui.components.SonyText
import com.hinnka.mycamera.ui.prefs.preferenceValue
import com.hinnka.mycamera.utils.VibrationHelper
import com.hinnka.mycamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private const val WHEEL_RADIUS = 72f      // dp
private const val WHEEL_DEG = 40f         // degrees of wheel per 1x
private const val PILL_STEP = 40f         // dp per 1x
private const val MAGNET = 0.07f          // a stop holds the control this close
private const val SETTLE = 0.15f          // let go this close to a stop and it settles there
private const val STRETCH = 0.3f          // past the ends the control moves at this fraction
private val SlotEdge = Color(0xFF4A4A4A)
private val Glow = Color(0xFF1C1C1C)
private val Orange = Color(0xFFFF9C00)

/** The control's height, without the 13 dp gap to the shutter that holds the thumbwheel's index. */
internal fun zoomDialHeight(kind: ZoomControl) = 40.dp

/**
 * Where a zoom sits on the control, in stops. The wheel spaces its stops evenly, one every fourth line, and zooms
 * evenly between neighbouring stops, so its numbers fit whatever the stops are; the pill is linear (1x per level).
 * Past the ends it carries on at the end segment's rate.
 */
private class DialScale(knots: List<Float>, private val even: Boolean) {
    private val k = knots.sorted().fold(mutableListOf<Float>()) { acc, v -> if (acc.isEmpty() || v - acc.last() > 0.01f) acc.add(v); acc }

    private fun segment(i: Int) = i.coerceIn(0, maxOf(0, k.size - 2))

    fun pos(z: Float): Float {
        if (!even || k.size < 2) return z
        var i = 0
        while (i < k.size - 2 && z > k[i + 1]) i++
        i = segment(i)
        return i + (z - k[i]) / (k[i + 1] - k[i])
    }

    fun zoom(p: Float): Float {
        if (!even || k.size < 2) return p
        val i = segment(kotlin.math.floor(p).toInt())
        return k[i] + (p - i) * (k[i + 1] - k[i])
    }
}

/** "1.6×", or "2×" on a whole number. */
internal fun zoomText(z: Float): String {
    val whole = z.roundToInt()
    return if (abs(z - whole) < 0.05f) "$whole×" else String.format(Locale.US, "%.1f×", z)
}

/**
 * The thumbwheel or the simple pill, centred above the shutter (13 dp clear of it). The wheel's rim and the pill's disc
 * follow the finger; a stop holds the control for a moment and ticks, even while a flick coasts past it; past the ends
 * it stretches and springs back; let go near a stop and it settles there; a tap on a number goes there.
 * [onReading] gets "1.6× · 40 mm" at every change, for the reading over the control.
 */
@Composable
internal fun ZoomDial(
    kind: ZoomControl,
    // The picture lies under the control: the wheel drops its black slot and draws in white.
    overPicture: Boolean,
    viewModel: CameraViewModel,
    zoomRatio: Float,
    availableCameras: List<CameraInfo>,
    currentCameraId: String,
    enabled: Boolean,
    onZoom: (Float) -> Unit,
    onReading: (String) -> Unit,
    // 0 at rest, 1 folded away while the mode list is open.
    fold: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val wheel = kind != ZoomControl.PILL
    val currentCamera = availableCameras.find { it.cameraId == currentCameraId }
    val mainCamera =
        availableCameras.find { it.lensType == if (currentCamera?.lensType == LensType.FRONT) LensType.FRONT else LensType.BACK_MAIN }
    val customFocalLengths by preferenceValue { it.customFocalLengths }
    val hiddenFocalLengths by preferenceValue { it.hiddenFocalLengths }
    val lensZoomStops = viewModel.calculateLensZoomStops(availableCameras, currentCamera)
    val zMax = minOf(COLLAR_MAX_ZOOM, viewModel.globalMaxZoom)
    val zMin = minOf(viewModel.globalMinZoom, zMax)
    val stops = viewModel.allZoomStops(lensZoomStops, mainCamera, currentCamera, customFocalLengths, hiddenFocalLengths)
        .filter { it in zMin..zMax }
    val mm = mainCamera?.focalLength35mmEquivalent ?: 0f

    val scale = remember(stops, zMin, zMax, wheel) { DialScale(stops + zMin + zMax, even = wheel) }
    val stopPos = stops.map { scale.pos(it) }

    val currentStops by rememberUpdatedState(stops)
    val currentScale by rememberUpdatedState(scale)
    val currentStopPos by rememberUpdatedState(stopPos)
    val currentMin by rememberUpdatedState(zMin)
    val currentMax by rememberUpdatedState(zMax)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnReading by rememberUpdatedState(onReading)
    val currentMm by rememberUpdatedState(mm)

    val context = LocalContext.current
    val haptics = remember(context) { SpacedBuzz(VibrationHelper(context)) }
    val scope = rememberCoroutineScope()
    // What the control shows; it runs past the ends while stretched, and leads the camera's own value.
    var shown by remember { mutableFloatStateOf(zoomRatio) }
    var busy by remember { mutableStateOf(false) }  // held, or moving on its own
    var job by remember { mutableStateOf<Job?>(null) }
    // Zoom set elsewhere (a pinch, a lens switch) moves the control when it is at rest.
    LaunchedEffect(zoomRatio) { if (!busy && abs(shown - zoomRatio) > 0.001f) shown = zoomRatio }

    fun show(z: Float) {
        val last = shown
        val lo = minOf(last, z)
        val hi = maxOf(last, z)
        if (currentStops.any { it in lo..hi && abs(it - last) > 0.001f }) haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
        shown = z
        val clamped = z.coerceIn(currentMin, currentMax)
        currentOnZoom(clamped)
        val millimetres = currentMm * clamped
        currentOnReading(if (millimetres > 0f) "${zoomText(clamped)} · ${millimetres.roundToInt()} mm" else zoomText(clamped))
    }

    // Moves to the zoom [to], evenly on the control.
    fun ease(to: Float, ms: Int, then: (() -> Unit)? = null) {
        job?.cancel()
        busy = true
        job = scope.launch {
            val from = currentScale.pos(shown)
            val target = currentScale.pos(to)
            val t0 = withFrameMillis { it }
            while (true) {
                val k = ((withFrameMillis { it } - t0) / ms.toFloat()).coerceAtMost(1f)
                show(if (k >= 1f) to else currentScale.zoom(from + (target - from) * (1f - (1f - k).pow(3))))
                if (k >= 1f) break
            }
            busy = false
            then?.invoke()
        }
    }

    fun settle() {
        val z = shown
        if (z < currentMin || z > currentMax) return ease(z.coerceIn(currentMin, currentMax), 260) { settle() }
        val p = currentScale.pos(z)
        val near = currentStops.minByOrNull { abs(currentScale.pos(it) - p) }
        val off = near?.let { abs(currentScale.pos(it) - p) }
        if (near != null && off != null && off < SETTLE && off > 0.0005f) ease(near, 200) else busy = false
    }

    fun step(direction: Int) {
        val z = shown
        val target = if (direction > 0) currentStops.firstOrNull { it > z + 0.05f } else currentStops.lastOrNull { it < z - 0.05f }
        if (target != null) ease(target, 320)
    }

    val textMeasurer = rememberTextMeasurer()
    val description = stringResource(R.string.zoom_collar)
    val wide = stringResource(R.string.zoom_collar_wide)
    val telephoto = stringResource(R.string.zoom_collar_telephoto)
    val height = zoomDialHeight(kind)
    // The box: 8 dp over the control, down to the top of the mode zone (the index draws below it, over the gap).
    val boxHeight = height + 11.dp
    val numberStyle = TextStyle(fontFamily = CondensedBold, fontSize = if (wheel) 16.sp else 14.sp)
    val discStyle = TextStyle(fontFamily = CondensedBold, fontSize = 12.5.sp, color = Color.Black)

    Canvas(
        modifier = modifier
            .offset(y = -(111.dp + height) / 2)
            .size(176.dp, boxHeight)
            .graphicsLayer { alpha = 1f - fold() }
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = zoomText(zoomRatio)
                customActions = listOf(
                    CustomAccessibilityAction(wide) { step(-1); true },
                    CustomAccessibilityAction(telephoto) { step(1); true }
                )
                if (!enabled) disabled()
            }
            .pointerInput(enabled, wheel) {
                if (!enabled) return@pointerInput
                val perPx = if (wheel) -1f / (WHEEL_RADIUS * WHEEL_DEG * PI.toFloat() / 180f) / density else 1f / PILL_STEP / density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (fold() > 0f) return@awaitEachGesture
                    down.consume()
                    job?.cancel()
                    busy = true
                    val p0 = currentScale.pos(shown)
                    val pMin = currentScale.pos(currentMin)
                    val pMax = currentScale.pos(currentMax)
                    var moved = false
                    var dx = 0f
                    val history = ArrayDeque<Pair<Long, Float>>()
                    var lastTime = down.uptimeMillis
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            lastTime = change.uptimeMillis
                            break
                        }
                        dx += change.position.x - change.previousPosition.x
                        if (!moved && abs(dx) > viewConfiguration.touchSlop) moved = true
                        if (!moved) continue
                        change.consume()
                        var p = p0 + dx * perPx
                        p = when {
                            p < pMin -> pMin - (pMin - p) * STRETCH
                            p > pMax -> pMax + (p - pMax) * STRETCH
                            else -> p
                        }
                        currentStopPos.firstOrNull { abs(it - p) < MAGNET }?.let { p = it }
                        history.addLast(change.uptimeMillis to p)
                        if (history.size > 5) history.removeFirst()
                        show(currentScale.zoom(p))
                    }
                    if (!moved) {
                        // A tap on a number goes there.
                        val x = (down.position.x - size.width / 2f) / density
                        val target = if (wheel) {
                            val ph = asin((x / WHEEL_RADIUS).coerceIn(-1f, 1f)) * 180f / PI.toFloat()
                            val p = currentScale.pos(shown) + ph / WHEEL_DEG
                            if (abs(ph) > 10f) currentStops.minByOrNull { abs(currentScale.pos(it) - p) }
                                ?.takeIf { abs(currentScale.pos(it) - p) < 0.3f } else null
                        } else {
                            currentStops.minByOrNull { abs(it - (1f + (x + 60f) / PILL_STEP)) }
                        }
                        if (target != null && abs(target - shown) > 0.02f) ease(target, 320) else busy = false
                        return@awaitEachGesture
                    }
                    val first = history.firstOrNull()
                    val last = history.lastOrNull()
                    val v = if (first != null && last != null && last.first > first.first && lastTime - last.first < 80) {
                        (last.second - first.second) / (last.first - first.first)
                    } else 0f
                    if (abs(v) > 0.0015f && shown in currentMin..currentMax) {
                        // A flick coasts, slowing like a weighted wheel.
                        job = scope.launch {
                            var vel = v
                            var p = currentScale.pos(shown)
                            var t = withFrameMillis { it }
                            while (true) {
                                val now = withFrameMillis { it }
                                val dt = (now - t).toFloat()
                                t = now
                                vel *= 0.993f.pow(dt)
                                p += vel * dt
                                if (p < pMin || p > pMax) {
                                    show(currentScale.zoom(p.coerceIn(pMin, pMax)))
                                    break
                                }
                                show(currentScale.zoom(p))
                                if (abs(vel) <= 0.0004f) break
                            }
                            settle()
                        }
                    } else {
                        settle()
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = 8.dp.toPx() + height.toPx() / 2f
        if (wheel) drawWheel(cx, cy, scale.pos(shown), stopPos, stops, scale.pos(zMax), textMeasurer, numberStyle, overPicture)
        else drawPill(cx, cy, shown, zMin, zMax, textMeasurer, numberStyle, discStyle)
    }
}

private fun rad(d: Float) = d * PI.toFloat() / 180f

private fun DrawScope.drawWheel(
    cx: Float, cy: Float, p: Float, stopPos: List<Float>, stops: List<Float>, pMax: Float,
    measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, seeThrough: Boolean
) {
    val dp = density
    val r = WHEEL_RADIUS * dp
    // A 40 dp slot, as tall as the pill, with 30 dp lines.
    val y0 = cy - 15 * dp
    val y1 = cy + 15 * dp
    val gap = 10 * dp
    // Over the picture: no black slot or glow, a white outline and white lines that stay readable on any picture.
    if (!seeThrough) {
        drawRoundRect(Color.Black, Offset(cx - 80 * dp, cy - 20 * dp), Size(160 * dp, 40 * dp), CornerRadius(14 * dp))
        drawRect(
            Brush.horizontalGradient(listOf(Glow.copy(alpha = 0f), Glow, Glow.copy(alpha = 0f)), cx - 72 * dp, cx + 72 * dp),
            Offset(cx - 72 * dp, y0 - 3 * dp), Size(144 * dp, y1 - y0 + 6 * dp)
        )
    }
    drawRoundRect(
        if (seeThrough) Color.White else SlotEdge,
        Offset(cx - 80 * dp, cy - 20 * dp), Size(160 * dp, 40 * dp), CornerRadius(14 * dp), style = Stroke(1.5f * dp)
    )
    val atEnd = p > pMax - 0.02f
    val minusX = cx - 71 * dp

    // The numbers first, so a line that would cross one is split round it.
    class Label(val x: Float, val half: Float, val ph: Float, val layout: androidx.compose.ui.text.TextLayoutResult)
    val labels = stops.indices.mapNotNull { i ->
        val ph = (stopPos[i] - p) * WHEEL_DEG
        if (abs(ph) > 84f) return@mapNotNull null
        // Numbers fade towards the wheel's edges like its lines, so they also read over the picture.
        val fade = cos(rad(ph)).pow(1.5f)
        val base = if (abs(ph) < WHEEL_DEG * 0.18f) Color.White else if (seeThrough) Color.White.copy(alpha = 0.75f) else SonySecondary
        val layout = measurer.measure(
            zoomText(stops[i]).removeSuffix("×"),
            style.copy(
                color = base.copy(alpha = base.alpha * fade),
                shadow = if (seeThrough) ViewfinderTextShadow.copy(color = ViewfinderTextShadow.color.copy(alpha = ViewfinderTextShadow.color.alpha * fade)) else null
            )
        )
        Label(cx + r * sin(rad(ph)), layout.size.width / 2f * cos(rad(ph)), ph, layout)
    }

    fun line(ph: Float) {
        if (abs(ph) >= 90f) return
        val x = cx + r * sin(rad(ph))
        val c = cos(rad(ph))
        val shade = (34 + 86 * c * c) / 255f
        val op = if (atEnd && abs(x - minusX) < 8 * dp) 0f else c.pow(1.6f)
        if (op <= 0f) return
        val col = if (seeThrough) Color.White.copy(alpha = op * (0.45f + 0.5f * c * c)) else Color(shade, shade, shade, op)
        val sx = x + (if (ph == 0f) 1f else sign(ph)) * 1.6f * dp
        // The groove beside each line; none over the picture, where it reads as a dark bar.
        val shadow = if (seeThrough) Color.Transparent else Color.Black.copy(alpha = 0.9f * c)
        // A stop's own line is split round its number; a line beside a number fades at its middle, more the closer it
        // comes, so it never pops in or out as the wheel turns.
        if (labels.any { abs(it.ph - ph) < 0.5f }) {
            listOf(y0 to cy - gap, cy + gap to y1).forEach { (a, b) ->
                drawLine(shadow, Offset(sx, a), Offset(sx, b), 1.6f * dp)
                drawLine(col, Offset(x, a), Offset(x, b), 1.5f * dp)
            }
            return
        }
        val keep = labels.minOfOrNull { ((abs(it.x - x) - it.half) / (6f * dp)).coerceIn(0f, 1f) } ?: 1f
        if (keep >= 1f) {
            drawLine(shadow, Offset(sx, y0), Offset(sx, y1), 1.6f * dp)
            drawLine(col, Offset(x, y0), Offset(x, y1), 1.5f * dp)
            return
        }
        fun faded(c: Color) = Brush.verticalGradient(
            0f to c, (gap * 0.4f) / (y1 - y0) to c, 0.5f to c.copy(alpha = c.alpha * keep),
            1f - (gap * 0.4f) / (y1 - y0) to c, 1f to c,
            startY = y0, endY = y1
        )
        drawLine(faded(shadow), Offset(sx, y0), Offset(sx, y1), 1.6f * dp)
        drawLine(faded(col), Offset(x, y0), Offset(x, y1), 1.5f * dp)
    }

    // A line every 10 degrees of wheel, so every stop (every fourth line) has its own line, split round its number.
    val rot = p * WHEEL_DEG
    for (k in 0 until 18) line(((((k * 10f - rot) % 180f) + 270f) % 180f) - 90f)
    labels.forEach { l ->
        val c = cos(rad(l.ph))
        scale(c, 1f, pivot = Offset(l.x, cy)) {
            drawText(l.layout, topLeft = Offset(l.x - l.layout.size.width / 2f, cy - l.layout.size.height / 2f))
        }
    }
    if (atEnd) drawLine(if (seeThrough) Color.White else SonySecondary, Offset(minusX - 4.5f * dp, cy), Offset(minusX + 4.5f * dp, cy), 1.75f * dp, StrokeCap.Round)
    // The index, under the slot.
    drawPath(
        Path().apply {
            moveTo(cx - 6 * dp, cy + 28 * dp)
            lineTo(cx + 6 * dp, cy + 28 * dp)
            lineTo(cx, cy + 21 * dp)
            close()
        },
        Orange
    )
}

private fun DrawScope.drawPill(
    cx: Float, cy: Float, z: Float, zMin: Float, zMax: Float,
    measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, discStyle: TextStyle
) {
    val dp = density
    // Levels at 40 dp steps, 1x at 60 dp left of centre.
    fun px(v: Float) = cx + (-60f + (v - 1f) * PILL_STEP) * dp
    // A white outline: the grey one disappears over a picture.
    drawRoundRect(Color.White, Offset(cx - 80 * dp, cy - 20 * dp), Size(160 * dp, 40 * dp), CornerRadius(20 * dp), style = Stroke(1.5f * dp))
    val x = px(z.coerceIn(zMin - 0.15f, zMax + 0.15f))
    for (n in 1..4) {
        if (n < zMin - 0.01f || n > zMax + 0.01f) continue
        val op = ((abs(x - px(n.toFloat())) / dp - 12f) / 14f).coerceIn(0f, 1f)
        if (op <= 0f) continue
        val layout = measurer.measure(n.toString(), style.copy(color = SonySecondary))
        drawText(layout, topLeft = Offset(px(n.toFloat()) - layout.size.width / 2f, cy - layout.size.height / 2f), alpha = op)
    }
    drawCircle(SonyText, 16 * dp, Offset(x, cy))
    val label = measurer.measure(zoomText(z.coerceIn(zMin, zMax)), discStyle)
    drawText(label, topLeft = Offset(x - label.size.width / 2f, cy - label.size.height / 2f))
}
