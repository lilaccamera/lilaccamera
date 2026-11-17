package com.hinnka.mycamera.ui.camera

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraInfo
import com.hinnka.mycamera.camera.LensType
import com.hinnka.mycamera.ui.prefs.preferenceValue
import com.hinnka.mycamera.utils.VibrationHelper
import com.hinnka.mycamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

// The collar zooms no further than this, and leaves out the stops above it; pinch zoom keeps the full range.
internal const val COLLAR_MAX_ZOOM = 4f
// Zoom speed at a full turn: the ratio changes by a factor of e per second.
private const val ZOOM_RATE = 1.0f

private val InnerRadius = 48.dp      // the shutter's 40 dp and an 8 dp gap
private val OuterRadius = 74.dp
private val MidRadius = 61.dp        // the knob's centre, W and T
private val KnobInner = 52.dp
private val KnobOuter = 70.dp
private const val SPAN = 44f         // degrees each side of straight up
private const val KNOB_SPAN = 13f
private const val END_ANGLE = 32f    // where W and T sit
private const val MAX_TURN = 24f     // degrees at a full pull
private const val PRESS_TURN = 0.25f // a press on W or T tilts the collar this far at once
private const val HOLD_TURN = 0.7f
private const val HOLD_MS = 260L
private const val SPRING_MS = 340
private const val STOP_TOLERANCE = 0.05f
private val EndColor = Color(0xFF9C9A92)

// The collar's box, in dp from the shutter centre: it covers the touch area of [onZoomCollar].
private val BoxHalfWidth = 62.dp
private val BoxTop = 80.dp
private val BoxBottom = 26.dp

/** Degrees clockwise from straight up. */
private fun angleFromUp(o: Offset) = (atan2(o.x, -o.y) * 180f / PI).toFloat()

/** Whether a press lands on the collar: the band with a slightly larger margin, mostly outward. */
internal fun Density.onZoomCollar(fromShutterCentre: Offset): Boolean {
    val r = fromShutterCentre.getDistance()
    return r >= (InnerRadius - 2.dp).toPx() && r <= (OuterRadius + 6.dp).toPx() &&
        abs(angleFromUp(fromShutterCentre)) <= SPAN + 4f
}

/**
 * A zoom lever round the top of the shutter, like the one round a compact camera's shutter release. Pulled round,
 * it turns about the shutter's centre and zooms, faster the further it turns, then springs back. A tap on W or T
 * steps one stop that way, a hold zooms that way, and a tap on the knob walks the stops up and back down.
 */
@Composable
internal fun ZoomCollar(
    viewModel: CameraViewModel,
    zoomRatio: Float,
    availableCameras: List<CameraInfo>,
    currentCameraId: String,
    enabled: Boolean,
    onZoom: (Float) -> Unit,
    onStop: (Float) -> Unit,
    // 0 at rest, 1 folded into the shutter while the mode list is open.
    fold: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val currentCamera = availableCameras.find { it.cameraId == currentCameraId }
    val mainCamera =
        availableCameras.find { it.lensType == if (currentCamera?.lensType == LensType.FRONT) LensType.FRONT else LensType.BACK_MAIN }

    // The app's zoom stops, with the user's custom and hidden focal lengths.
    val customFocalLengths by preferenceValue { it.customFocalLengths }
    val hiddenFocalLengths by preferenceValue { it.hiddenFocalLengths }
    val lensZoomStops = viewModel.calculateLensZoomStops(availableCameras, currentCamera)
    val zoomStops = viewModel.allZoomStops(lensZoomStops, mainCamera, currentCamera, customFocalLengths, hiddenFocalLengths)
    // shortcut: the collar does not offer lens variants or macro lenses; a quick-settings line should show them,
    // only on phones that have them.

    val maxZoom = minOf(COLLAR_MAX_ZOOM, viewModel.globalMaxZoom)
    val stops = zoomStops.filter { it <= maxZoom }

    val currentStops by rememberUpdatedState(stops)
    val currentMin by rememberUpdatedState(viewModel.globalMinZoom)
    val currentMax by rememberUpdatedState(maxZoom)
    val currentZoom by rememberUpdatedState(zoomRatio)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnStop by rememberUpdatedState(onStop)

    val context = LocalContext.current
    val haptics = remember(context) { SpacedBuzz(VibrationHelper(context)) }
    val scope = rememberCoroutineScope()
    // -1 turned fully towards W, 1 fully towards T.
    var turn by remember { mutableFloatStateOf(0f) }
    // The ratio the collar has asked for while it is held; the camera's own value can lag behind a lens switch.
    var gestureZoom by remember { mutableStateOf<Float?>(null) }
    var knobDirection by remember { mutableIntStateOf(1) }

    fun stepTarget(direction: Int): Float? {
        val z = currentZoom
        return if (direction > 0) {
            currentStops.firstOrNull { it > z + STOP_TOLERANCE }
        } else {
            currentStops.lastOrNull { it < z - STOP_TOLERANCE }
        }
    }

    fun step(direction: Int) {
        val target = stepTarget(direction) ?: return
        haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
        currentOnStop(target)
    }

    // The knob walks up to the last stop, then back down to the first.
    fun stepFromKnob() {
        if (stepTarget(knobDirection) == null) knobDirection = -knobDirection
        step(knobDirection)
    }

    val label = collarZoomLabel(gestureZoom ?: zoomRatio)
    val description = stringResource(R.string.zoom_collar)
    val wide = stringResource(R.string.zoom_collar_wide)
    val telephoto = stringResource(R.string.zoom_collar_telephoto)
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .offset(y = -(BoxTop + BoxBottom) / 2)
            .size(BoxHalfWidth * 2, BoxTop - BoxBottom)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val centre = Offset(BoxHalfWidth.toPx(), BoxTop.toPx())
                val endOffset = 16.dp.toPx()
                var springJob: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val from = down.position - centre
                    if (fold() > 0f || !onZoomCollar(from)) return@awaitEachGesture
                    down.consume()
                    springJob?.cancel()
                    // W end, knob, T end.
                    val side = when {
                        from.x < -endOffset -> -1
                        from.x > endOffset -> 1
                        else -> 0
                    }
                    val startAngle = angleFromUp(from)
                    var moved = false
                    var held = false
                    var rate = 0f
                    var z = currentZoom

                    // Every stop crossed or reached on the way gives a tick.
                    fun zoomTo(next: Float) {
                        if (currentStops.any { (z < it && it <= next) || (next <= it && it < z) }) {
                            haptics.buzz(VibrationHelper.LIGHT_TICK_MS)
                        }
                        z = next
                        gestureZoom = next
                        currentOnZoom(next)
                    }

                    val zoomJob = scope.launch {
                        var last = withFrameNanos { it }
                        while (true) {
                            val now = withFrameNanos { it }
                            val dt = (now - last) / 1e9f
                            last = now
                            if (rate != 0f) {
                                // A ratio already outside the collar's range, set by pinching, is not pulled into it.
                                val next = (z * exp(rate * ZOOM_RATE * dt))
                                    .coerceIn(minOf(currentMin, z), maxOf(currentMax, z))
                                if (next != z) zoomTo(next)
                            }
                        }
                    }
                    val holdJob = if (side != 0) {
                        turn = side * PRESS_TURN
                        scope.launch {
                            delay(HOLD_MS)
                            held = true
                            rate = side * HOLD_TURN
                            turn = rate
                        }
                    } else {
                        null
                    }
                    try {
                        var lifted = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                lifted = true
                                change.consume()
                                break
                            }
                            if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                holdJob?.cancel()
                            }
                            if (moved) {
                                val d = ((angleFromUp(change.position - centre) - startAngle + 540f) % 360f) - 180f
                                rate = (d / MAX_TURN).coerceIn(-1f, 1f)
                                turn = rate
                            }
                            change.consume()
                        }
                        holdJob?.cancel()
                        zoomJob.cancel()
                        if (moved || held) {
                            settleContinuousZoomStop(currentStops, z).snapZoomStop?.let { if (it != z) zoomTo(it) }
                        } else if (lifted) {
                            if (side == 0) stepFromKnob() else step(side)
                        }
                    } finally {
                        holdJob?.cancel()
                        zoomJob.cancel()
                        gestureZoom = null
                        springJob = scope.launch {
                            animate(turn, 0f, animationSpec = tween(SPRING_MS, easing = BackEasing)) { v, _ -> turn = v }
                        }
                    }
                }
            }
            // After the pointer input, so touches are tested against the collar at rest.
            .graphicsLayer {
                rotationZ = turn * MAX_TURN
                transformOrigin = TransformOrigin(0.5f, BoxTop / (BoxTop - BoxBottom))
                val f = fold()
                scaleX = 1f - 0.18f * f
                scaleY = scaleX
                alpha = (1f - f) * (if (enabled) 1f else 0.4f)
            }
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(wide) { step(-1); true },
                    CustomAccessibilityAction(telephoto) { step(1); true }
                )
                if (!enabled) disabled()
            }
    ) {
        val endStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
        val wLayout = remember(textMeasurer) { textMeasurer.measure("W", endStyle) }
        val tLayout = remember(textMeasurer) { textMeasurer.measure("T", endStyle) }
        val lineColor = CameraControlsLayoutDefaults.LineColor.onFooter()
        val litColor = Color.White.onFooter()
        // The band's shade and the knob's glow swap places on a white footer.
        val light = LocalLightFooter.current
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val c = Offset(BoxHalfWidth.toPx(), BoxTop.toPx())
                    val band = bandPath(c, InnerRadius.toPx(), OuterRadius.toPx(), SPAN)
                    val knob = bandPath(c, KnobInner.toPx(), KnobOuter.toPx(), KNOB_SPAN)
                    val stroke = Stroke(CameraControlsLayoutDefaults.LineWidth.toPx())
                    val mid = MidRadius.toPx()
                    onDrawBehind {
                        drawPath(band, (if (light) Color.White else Color.Black).copy(alpha = 0.4f))
                        drawPath(band, lineColor, style = stroke)
                        drawPath(knob, (if (light) Color.Black else Color.White).copy(alpha = 0.16f))
                        for ((layout, angle, lit) in listOf(
                            Triple(wLayout, -END_ANGLE, turn < -0.05f),
                            Triple(tLayout, END_ANGLE, turn > 0.05f)
                        )) {
                            val p = c + polar(mid, angle)
                            rotate(angle, pivot = p) {
                                drawText(
                                    layout,
                                    color = if (lit) litColor else EndColor,
                                    topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f)
                                )
                            }
                        }
                    }
                }
        )
        // Centred on the knob; only the number turns upright in landscape.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = BoxTop - MidRadius - 20.dp)
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = litColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.autoRotate()
            )
        }
    }
}

/** "1×" near a whole ratio, otherwise one decimal. */
private fun collarZoomLabel(z: Float): String {
    val whole = z.roundToInt()
    return if (abs(z - whole) < 0.05f) "$whole×" else String.format(Locale.US, "%.1f×", z)
}

/** A point [r] from the centre, [angle] degrees clockwise from straight up. */
private fun polar(r: Float, angle: Float): Offset {
    val rad = angle * PI.toFloat() / 180f
    return Offset(r * sin(rad), -r * cos(rad))
}

/** An arc band from [r0] to [r1] round [c], [span] degrees each side of straight up, with round ends. */
private fun bandPath(c: Offset, r0: Float, r1: Float, span: Float) = Path().apply {
    val capRadius = (r1 - r0) / 2
    val capCentre = (r0 + r1) / 2
    arcTo(Rect(c, r1), -span - 90f, 2 * span, forceMoveTo = true)
    arcTo(Rect(c + polar(capCentre, span), capRadius), span - 90f, 180f, forceMoveTo = false)
    arcTo(Rect(c, r0), span - 90f, -2 * span, forceMoveTo = false)
    arcTo(Rect(c + polar(capCentre, -span), capRadius), -span + 90f, 180f, forceMoveTo = false)
    close()
}
