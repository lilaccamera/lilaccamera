package com.hinnka.mycamera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.FocusPointSource
import com.hinnka.mycamera.camera.MeteringMode
import com.hinnka.mycamera.utils.VibrationHelper
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 对焦指示器
 */
@Composable
fun FocusIndicator(
    position: Pair<Float, Float>?,
    meteringPosition: Pair<Float, Float>? = null,
    canMoveMeteringPoint: Boolean = false,
    onMeteringPointMove: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onFocusPointMove: (Float, Float, Float, Boolean) -> Unit = { _, _, _, _ -> },
    onMeteringPointMerge: () -> Unit = {},
    source: FocusPointSource = FocusPointSource.MANUAL,
    isFocusLocked: Boolean = false,
    isFocusing: Boolean,
    focusSuccess: Boolean?,
    // A fixed-focus camera (most front cameras): a tap only sets exposure, so only its circle shows.
    fixedFocus: Boolean = false,
    meteringMode: MeteringMode = MeteringMode.SYSTEM_DEFAULT,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(position != null) }
    val isSeparated = source == FocusPointSource.MANUAL && meteringPosition != null
    val currentFocusPosition by rememberUpdatedState(position)
    val currentIsSeparated by rememberUpdatedState(isSeparated)
    val currentMeteringPosition by rememberUpdatedState(meteringPosition ?: position)
    val currentOnMeteringPointMove by rememberUpdatedState(onMeteringPointMove)
    val currentOnFocusPointMove by rememberUpdatedState(onFocusPointMove)
    val currentOnMeteringPointMerge by rememberUpdatedState(onMeteringPointMerge)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        shadow = Shadow(Color.Black, Offset(0f, 1f), 3f),
    )
    val focusLabel = textMeasurer.measure(stringResource(R.string.camera_focus_point_label), labelStyle)
    val meteringLabel = textMeasurer.measure(stringResource(R.string.camera_metering_point_label), labelStyle)
    
    // 透明度动画
    val alpha by animateFloatAsState(
        targetValue = when {
            isSeparated -> 1f
            isFocusing -> 1f
            fixedFocus && position != null -> 1f
            source == FocusPointSource.EYE && position != null -> 0.9f
            isFocusLocked && focusSuccess != false -> 0.9f
            focusSuccess == true -> 0.8f
            isFocusLocked -> 0.6f
            focusSuccess == false -> 0.5f
            else -> 0f
        },
        animationSpec = tween(300),
        label = "focusAlpha"
    )

    // A tap mark works like a Sony camera's focus frame: white while focusing, green with a light tick
    // once focus locks, a white blink on a miss; it appears a little large and settles as focus locks.
    val isTapMark = source != FocusPointSource.EYE
    val scale by animateFloatAsState(
        targetValue = when {
            isTapMark -> if (focusSuccess == true) 1f else 1.25f
            focusSuccess == true -> 0.8f
            else -> 1f
        },
        animationSpec = if (isTapMark) tween(220, easing = CubicBezierEasing(0.3f, 1.35f, 0.5f, 1f)) else tween(300),
        label = "focusScale"
    )

    // 颜色
    val color = when {
        isTapMark -> if (focusSuccess == true) FocusGreen else Color.White
        focusSuccess == true -> Color.Green
        focusSuccess == false -> Color.Red
        else -> Color(0xFF64D8FF)
    }

    val context = LocalContext.current
    val vibration = remember(context) { VibrationHelper(context) }
    var blinkOff by remember { mutableStateOf(false) }
    LaunchedEffect(focusSuccess) {
        blinkOff = false
        if (!isTapMark || currentFocusPosition == null) return@LaunchedEffect
        when (focusSuccess) {
            true -> vibration.vibrate(VibrationHelper.LIGHT_TICK_MS)
            false -> repeat(3) { blinkOff = true; delay(120); blinkOff = false; delay(120) }
            null -> Unit
        }
    }
    // Once focus has settled the mark fades, as on a camera; a locked or split mark stays to show it.
    var faded by remember { mutableStateOf(false) }
    LaunchedEffect(position, focusSuccess, isFocusLocked, isSeparated) {
        if (position == null) return@LaunchedEffect  // cleared: keep fading, never flash back
        faded = false
        if (!isTapMark || (focusSuccess == null && !fixedFocus) || isFocusLocked || isSeparated) return@LaunchedEffect
        delay(1500)
        faded = true
    }
    val tapMarkAlpha by animateFloatAsState(if (isTapMark && faded) 0f else 1f, tween(300), label = "focusFade")

    LaunchedEffect(position) {
        if (position != null) {
            visible = true
        } else {
            delay(200)
            visible = false
        }
    }

    // While the mark fades out after the point is cleared it stays where it was.
    val lastPosition = remember { arrayOf(Pair(0.5f, 0.5f)) }
    if (position != null) lastPosition[0] = position
    val displayPosition = position ?: lastPosition[0]

    // Metering circles, as on a Sony camera: Spot's sits in the centre until a tap moves it with the focus
    // frame, and stays; Centre-weighted's is fixed in the centre. The other modes show none.
    val circleCenter = when (meteringMode) {
        MeteringMode.SPOT -> meteringPosition ?: position?.takeIf { source == FocusPointSource.MANUAL } ?: (0.5f to 0.5f)
        MeteringMode.CENTER_WEIGHTED -> 0.5f to 0.5f
        else -> null
    }
    if (circleCenter != null) {
        Canvas(modifier.fillMaxSize()) {
            val center = Offset(circleCenter.first * size.width, circleCenter.second * size.height)
            val radius = SpotCircleRadius.toPx()
            drawCircle(Color.Black.copy(alpha = 0.3f), radius, center, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, radius, center, style = Stroke(TapFrameLine.toPx()))
        }
    }

    AnimatedVisibility(visible = visible, modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(canMoveMeteringPoint, source, position != null) {
                        if (!canMoveMeteringPoint || source != FocusPointSource.MANUAL || position == null) {
                            return@pointerInput
                        }
                        val hitRadius = 24.dp.toPx()
                        val circleRadius = 11.dp.toPx()
                        val focusHalfSize = (TapFrameSize / 2).toPx()
                        val focusHitHalfSize = 38.dp.toPx()
                        val mergeRadius = 16.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val point = currentMeteringPosition ?: return@awaitEachGesture
                            val focus = currentFocusPosition ?: return@awaitEachGesture
                            val meteringCenter = Offset(point.first * size.width, point.second * size.height)
                            val focusCenter = Offset(focus.first * size.width, focus.second * size.height)
                            val meteringDistance = (down.position - meteringCenter).getDistance()
                            val focusDelta = down.position - focusCenter
                            val hitsMetering = meteringDistance <= hitRadius
                            val hitsFocus = currentIsSeparated &&
                                abs(focusDelta.x) <= focusHitHalfSize && abs(focusDelta.y) <= focusHitHalfSize
                            if (!hitsMetering && !hitsFocus) return@awaitEachGesture
                            // 命中范围重叠时优先圈本身，其余选择较近的标记；一次手势只拖动一个目标。
                            val draggingFocus = hitsFocus && (!hitsMetering ||
                                (meteringDistance > circleRadius && focusDelta.getDistance() < meteringDistance))
                            val center = if (draggingFocus) focusCenter else meteringCenter
                            // 只在越过拖动阈值后消费事件，保留原有点按与长按锁焦。
                            val start = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                if (currentEvent.changes.count { it.pressed } == 1) change.consume()
                            }
                                ?: return@awaitEachGesture
                            val grabOffset = center - down.position
                            var separatedDuringDrag = currentIsSeparated
                            var mergedDuringDrag = false
                            var lastFocusTarget: Offset? = null
                            var lastFocusAspectRatio = 1f
                            fun move(pointer: Offset) {
                                if (mergedDuringDrag) return
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                if (width <= 0f || height <= 0f) return
                                val radius = if (draggingFocus) focusHalfSize else circleRadius
                                val insetX = radius.coerceAtMost(width / 2f)
                                val insetY = radius.coerceAtMost(height / 2f)
                                val target = pointer + grabOffset
                                val normalized = Offset(
                                    target.x.coerceIn(insetX, width - insetX) / width,
                                    target.y.coerceIn(insetY, height - insetY) / height,
                                )
                                if (draggingFocus) {
                                    lastFocusTarget = normalized
                                    lastFocusAspectRatio = width / height
                                    currentOnFocusPointMove(normalized.x, normalized.y, width / height, false)
                                } else {
                                    val distanceToFocus = (target - focusCenter).getDistance()
                                    if (separatedDuringDrag && distanceToFocus <= mergeRadius) {
                                        mergedDuringDrag = true
                                        currentOnMeteringPointMerge()
                                    } else if (separatedDuringDrag || distanceToFocus > hitRadius) {
                                        // 拉出距离大于吸附距离，防止中心附近反复分离、合并。
                                        separatedDuringDrag = true
                                        currentOnMeteringPointMove(normalized.x, normalized.y, width / height)
                                    }
                                }
                            }
                            try {
                                move(start.position)
                                drag(start.id) { change ->
                                    if (currentEvent.changes.count { it.pressed } == 1) {
                                        change.consume()
                                        move(change.position)
                                    }
                                }
                            } finally {
                                // 包括多指接管或手势取消，均以最后接受的位置结束，避免留下未完成的 AF 状态。
                                lastFocusTarget?.let { target ->
                                    currentOnFocusPointMove(target.x, target.y, lastFocusAspectRatio, true)
                                }
                            }
                        }
                    }
            ) {
                val x = displayPosition.first * size.width
                val y = displayPosition.second * size.height
                val boxSize = (if (source == FocusPointSource.EYE) 24.dp else TapFrameSize).toPx() * scale
                val halfSize = boxSize / 2
                val cornerLength = 6.dp.toPx()
                val strokeWidth = 1.5.dp.toPx()

                val markAlpha = alpha * tapMarkAlpha * (if (blinkOff) 0.15f else 1f)
                val drawColor = color.copy(alpha = markAlpha)

                if (isTapMark && fixedFocus) {
                    // No focus frame: there is nothing to focus.
                } else if (isTapMark) {
                    val topLeft = Offset(x - halfSize, y - halfSize)
                    val frame = androidx.compose.ui.geometry.Size(boxSize, boxSize)
                    // A soft dark edge keeps the thin line readable on bright scenes.
                    drawRect(Color.Black.copy(alpha = 0.3f * markAlpha), topLeft, frame, style = Stroke(3.dp.toPx()))
                    drawRect(drawColor, topLeft, frame, style = Stroke(TapFrameLine.toPx()))
                } else {
                    // 左上角
                    drawLine(
                        color = drawColor,
                        start = Offset(x - halfSize, y - halfSize),
                        end = Offset(x - halfSize + cornerLength, y - halfSize),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = drawColor,
                        start = Offset(x - halfSize, y - halfSize),
                        end = Offset(x - halfSize, y - halfSize + cornerLength),
                        strokeWidth = strokeWidth
                    )

                    // 右上角
                    drawLine(
                        color = drawColor,
                        start = Offset(x + halfSize, y - halfSize),
                        end = Offset(x + halfSize - cornerLength, y - halfSize),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = drawColor,
                        start = Offset(x + halfSize, y - halfSize),
                        end = Offset(x + halfSize, y - halfSize + cornerLength),
                        strokeWidth = strokeWidth
                    )

                    // 左下角
                    drawLine(
                        color = drawColor,
                        start = Offset(x - halfSize, y + halfSize),
                        end = Offset(x - halfSize + cornerLength, y + halfSize),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = drawColor,
                        start = Offset(x - halfSize, y + halfSize),
                        end = Offset(x - halfSize, y + halfSize - cornerLength),
                        strokeWidth = strokeWidth
                    )

                    // 右下角
                    drawLine(
                        color = drawColor,
                        start = Offset(x + halfSize, y + halfSize),
                        end = Offset(x + halfSize - cornerLength, y + halfSize),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = drawColor,
                        start = Offset(x + halfSize, y + halfSize),
                        end = Offset(x + halfSize, y + halfSize - cornerLength),
                        strokeWidth = strokeWidth
                    )
                }

                if (isSeparated) {
                    val point = meteringPosition ?: displayPosition
                    val center = Offset(point.first * size.width, point.second * size.height)
                    val radius = SpotCircleRadius.toPx()
                    fun labelTopLeft(center: Offset, radius: Float, width: Int, height: Int): Offset {
                        val below = center.y + radius + 5.dp.toPx()
                        val top = if (below + height <= size.height) below
                            else center.y - radius - 5.dp.toPx() - height
                        return Offset(
                            (center.x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f)),
                            top.coerceIn(0f, (size.height - height).coerceAtLeast(0f)),
                        )
                    }
                    drawText(
                        focusLabel,
                        topLeft = labelTopLeft(Offset(x, y), halfSize, focusLabel.size.width, focusLabel.size.height),
                    )
                    drawText(
                        meteringLabel,
                        topLeft = labelTopLeft(center, radius, meteringLabel.size.width, meteringLabel.size.height),
                    )
                }
            }
        }
    }
}

private val TapFrameSize = 42.dp
private val TapFrameLine = 1.25.dp
private val SpotCircleRadius = 11.dp
// The bright green of a Sony camera's in-focus frame.
private val FocusGreen = Color(0xFF3FE03F)
