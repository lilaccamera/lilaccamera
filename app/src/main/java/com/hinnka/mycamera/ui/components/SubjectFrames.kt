package com.hinnka.mycamera.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.hinnka.mycamera.ui.icons.AppIcons
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.camera.SubjectOverlay
import com.hinnka.mycamera.camera.ViewRect

/**
 * The camera's face frames and the tracked object's mark, as on a Sony Alpha: light squares, the main face brighter
 * and green once focus is on it, other faces faint. Faces hide while an object is tracked; they stay tappable.
 * With the smile shutter on ([smileThreshold] over 0) the main face's right edge is its smile gauge.
 */
@Composable
fun SubjectFrames(overlay: SubjectOverlay, modifier: Modifier = Modifier, smileScore: Int? = null, smileThreshold: Int = 0) {
    // When tracking ends (lost, or the mark tapped again) the mark fades out where it was.
    val lastTracking = remember { arrayOfNulls<SubjectOverlay>(1) }
    if (overlay.tracking) lastTracking[0] = overlay
    lastTracking[0]?.let { key(it.trackStart) { TrackingFrame(it, shown = overlay.tracking, modifier = modifier) } }
    if (overlay.tracking) return
    for (face in overlay.faces) {
        key(face.id) {
            val main = face.id == overlay.mainFace
            FaceFrame(
                face.box,
                main = main,
                focused = overlay.focused,
                smile = if (main && smileThreshold > 0) (smileScore ?: 0) / 100f else null,
                smileMark = smileThreshold / 100f,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun FaceFrame(face: ViewRect, main: Boolean, focused: Boolean, smile: Float?, smileMark: Float, modifier: Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(300)) }
    val rect = animatedRect(face)
    // Eased: the driver's score jumps a few points from frame to frame.
    val smileLevel by animateFloatAsState(smile ?: 0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow), label = "smileLevel")
    val smileIcon = rememberVectorPainter(AppIcons.MoodSmileLine)
    Canvas(modifier.fillMaxSize()) {
        val scale = 0.5f + 0.5f * appear.value
        val width = rect.width * size.width * scale
        val height = rect.height * size.height * scale
        val topLeft = Offset(rect.centerX * size.width - width / 2, rect.centerY * size.height - height / 2)
        val alpha = appear.value * (if (main) 0.55f else 0.22f)
        val line = if (main) TapFrameLine else 1.dp
        val color = if (main && focused) FocusGreen else Color.White
        if (smile == null) {
            drawRect(color.copy(alpha = alpha), topLeft, Size(width, height), style = Stroke(line.toPx()))
            return@Canvas
        }
        // The right edge is the smile gauge: it fills from the bottom with the score up to a smile icon sitting at the
        // level that takes a shot; fill and icon turn full white once it is reached. Neither line runs through the icon.
        val frameColor = color.copy(alpha = alpha)
        val stroke = line.toPx()
        val x = topLeft.x + width
        val bottom = topLeft.y + height
        fun edge(from: Offset, to: Offset) = drawLine(frameColor, from, to, stroke, StrokeCap.Square)
        edge(topLeft, Offset(x, topLeft.y))
        edge(topLeft, Offset(topLeft.x, bottom))
        edge(Offset(topLeft.x, bottom), Offset(x, bottom))
        val iconHalf = 7.dp.toPx()
        val markY = bottom - height * smileMark
        edge(Offset(x, topLeft.y), Offset(x, markY - iconHalf))
        edge(Offset(x, markY + iconHalf), Offset(x, bottom))
        val reached = smile >= smileMark
        val fillColor = Color.White.copy(alpha = appear.value * (if (reached) 1f else 0.8f))
        val fillTop = bottom - height * smileLevel
        val fillWidth = 3.dp.toPx()
        if (fillTop < bottom) drawLine(fillColor, Offset(x, bottom), Offset(x, maxOf(fillTop, markY + iconHalf)), fillWidth)
        if (fillTop < markY - iconHalf) drawLine(fillColor, Offset(x, markY - iconHalf), Offset(x, fillTop), fillWidth)
        val iconColor = if (reached) Color.White.copy(alpha = appear.value) else frameColor
        translate(x - iconHalf, markY - iconHalf) {
            with(smileIcon) { draw(Size(2 * iconHalf, 2 * iconHalf), colorFilter = ColorFilter.tint(iconColor)) }
        }
    }
}

/** While an object is tracked the tap mark follows it: the camera's box only says where the object is. */
@Composable
private fun TrackingFrame(overlay: SubjectOverlay, shown: Boolean, modifier: Modifier) {
    val start = overlay.trackStart ?: return
    val target = overlay.trackBox?.let { it.centerX to it.centerY } ?: start
    val x by animateFloatAsState(target.first, FrameSpring, label = "trackX")
    val y by animateFloatAsState(target.second, FrameSpring, label = "trackY")
    val alpha by animateFloatAsState(
        when {
            !shown -> 0f
            overlay.trackLost -> 0.35f
            else -> 1f
        },
        tween(200),
        label = "trackAlpha",
    )
    Canvas(modifier.fillMaxSize()) {
        val half = TapFrameSize.toPx() / 2
        val topLeft = Offset(x * size.width - half, y * size.height - half)
        val frame = Size(2 * half, 2 * half)
        val color = if (overlay.focused) FocusGreen else Color.White
        // The same soft dark edge as the tap mark, so the two read as one mark.
        drawRect(Color.Black.copy(alpha = 0.3f * alpha), topLeft, frame, style = Stroke(3.dp.toPx()))
        drawRect(color.copy(alpha = alpha), topLeft, frame, style = Stroke(TapFrameLine.toPx()))
    }
}

// Critically damped: frames glide at display rate over the driver's coarse, irregular steps, without overshoot.
private val FrameSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

@Composable
private fun animatedRect(target: ViewRect, spec: AnimationSpec<Float> = FrameSpring): ViewRect {
    val left by animateFloatAsState(target.left, spec, label = "rectLeft")
    val top by animateFloatAsState(target.top, spec, label = "rectTop")
    val right by animateFloatAsState(target.right, spec, label = "rectRight")
    val bottom by animateFloatAsState(target.bottom, spec, label = "rectBottom")
    return ViewRect(left, top, right, bottom)
}

