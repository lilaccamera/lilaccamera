package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Progress drawn in place of the shutter's outline, at the same line width: the whole ring takes the
 * colour faintly and the progress fills it in full colour.
 */
@Composable
internal fun CaptureProgressRing(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        val strokeWidth = CameraControlsLayoutDefaults.LineWidth.toPx()
        val radius = size.minDimension / 2f - strokeWidth / 2f
        // Opaque, so the grey outline underneath does not show through.
        drawCircle(Color.Black, radius, style = Stroke(width = strokeWidth))
        drawCircle(color.copy(alpha = color.alpha * 0.35f), radius, style = Stroke(width = strokeWidth))
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle.coerceIn(0f, 360f),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
