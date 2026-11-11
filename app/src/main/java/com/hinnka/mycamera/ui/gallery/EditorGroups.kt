package com.hinnka.mycamera.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.lut.CurveUtils
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.components.CurveChannel
import com.hinnka.mycamera.ui.components.LilacDim
import com.hinnka.mycamera.ui.components.getCurvePoints
import com.hinnka.mycamera.ui.components.identityPoints
import com.hinnka.mycamera.ui.components.parsePoints
import com.hinnka.mycamera.ui.components.toFloatArray

/**
 * Curve: the curve's box on screen, the visible part of the photo inside [free] (clear of the edge icons and the
 * control layer) with a margin, so every point can be reached and none sits on the screen edge.
 */
internal fun curveBox(contentBounds: Rect?, free: Rect, inset: Float): Rect? {
    val b = contentBounds?.takeIf { it.width > 0f && it.height > 0f } ?: return null
    val visible = b.intersect(free)
    val box = Rect(visible.left + inset, visible.top + inset, visible.right - inset, visible.bottom - inset)
    return box.takeIf { it.width > 1f && it.height > 1f }
}

internal fun curvePoints(recipe: ColorRecipeParams, channel: CurveChannel): List<Pair<Float, Float>> =
    parsePoints(recipe.getCurvePoints(channel) ?: identityPoints())

/** The recipe with one channel's curve; a straight line is stored as no curve. */
internal fun ColorRecipeParams.withCurve(channel: CurveChannel, points: List<Pair<Float, Float>>?): ColorRecipeParams {
    val array = points?.toFloatArray()?.takeUnless { points.size <= 2 && CurveUtils.isIdentityCurve(it) }
    return when (channel) {
        CurveChannel.MASTER -> copy(masterCurvePoints = array)
        CurveChannel.RED -> copy(redCurvePoints = array)
        CurveChannel.GREEN -> copy(greenCurvePoints = array)
        CurveChannel.BLUE -> copy(blueCurvePoints = array)
    }
}

/** A channel's curve when the editor opened, from the "recipe.curves" snapshot (master, red, green, blue). */
internal fun openCurve(openCurves: Any?, channel: CurveChannel): List<Pair<Float, Float>>? =
    ((openCurves as? List<*>)?.getOrNull(channel.ordinal) as? List<*>)
        ?.map { it as Float }?.toFloatArray()?.let(::parsePoints)

/** The nearest point within [radius] of [position], or -1. */
internal fun nearestCurvePoint(points: List<Pair<Float, Float>>, box: Rect, position: Offset, radius: Float): Int {
    var best = -1
    var bestDistance = radius
    points.forEachIndexed { i, (x, y) ->
        val d = (Offset(box.left + x * box.width, box.top + (1f - y) * box.height) - position).getDistance()
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

/** The curve on the photo: grid, the straight line, faint other channels, the active curve and its points. */
@Composable
internal fun CurveOverlay(
    contentBounds: Rect?,
    free: Rect,
    points: List<Pair<Float, Float>>,
    channel: CurveChannel,
    others: List<Pair<CurveChannel, FloatArray>>,
    selected: Int?,
) {
    Canvas(Modifier.fillMaxSize()) {
        val box = curveBox(contentBounds, free, CURVE_INSET_DP.dp.toPx()) ?: return@Canvas
        val shade = Color.Black.copy(alpha = 0.45f)
        val grid = Color.White.copy(alpha = 0.18f)
        for (i in 1..3) {
            val x = box.left + box.width * i / 4f
            val y = box.top + box.height * i / 4f
            drawLine(grid, Offset(x, box.top), Offset(x, box.bottom), strokeWidth = 1f)
            drawLine(grid, Offset(box.left, y), Offset(box.right, y), strokeWidth = 1f)
        }
        drawRect(Color.White.copy(alpha = 0.3f), box.topLeft, box.size, style = Stroke(1f))
        drawLine(
            Color.White.copy(alpha = 0.35f), Offset(box.left, box.bottom), Offset(box.right, box.top), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )
        others.forEach { (c, p) -> drawCurve(p, box, channelColor(c).copy(alpha = 0.4f), 1.5.dp.toPx(), shade) }
        val color = channelColor(channel)
        drawCurve(points.toFloatArray(), box, color, 2.dp.toPx(), shade)
        val radius = 7.dp.toPx()
        points.forEachIndexed { i, (x, y) ->
            val center = Offset(box.left + x * box.width, box.top + (1f - y) * box.height)
            drawCircle(Color(0xFF141413), radius, center)
            drawCircle(if (i == selected) ModeAmber else color, radius, center, style = Stroke(2.dp.toPx()))
            if (i == selected) drawCircle(ModeAmber, radius - 4.dp.toPx(), center)
        }
    }
}

private fun DrawScope.drawCurve(points: FloatArray, box: Rect, color: Color, width: Float, shade: Color) {
    val lut = CurveUtils.evaluateCurve(points)
    val path = Path()
    for (i in 0 until CurveUtils.LUT_SIZE) {
        val x = box.left + i / (CurveUtils.LUT_SIZE - 1f) * box.width
        val y = box.top + (1f - lut[i]) * box.height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    // A dark edge under the line keeps it readable on a light photo.
    drawPath(path, shade, style = Stroke(width = width + 2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

private fun channelColor(channel: CurveChannel): Color =
    if (channel == CurveChannel.MASTER) Color.White else channel.color

/** The channel picker in Curve's path row: All, R, G, B in their colours, each with a dot once changed. */
@Composable
internal fun CurveChannelChip(label: String, channel: CurveChannel, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(32.dp)
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val color = when {
            selected -> channelColor(channel)
            channel == CurveChannel.MASTER -> LilacDim
            else -> channel.color.copy(alpha = 0.6f)
        }
        Text(label, color = color, fontSize = 14.sp)
        if (changed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 3.dp)
                    .size(5.dp)
                    .background(ModeAmber, CircleShape)
            )
        }
    }
}

internal const val CURVE_INSET_DP = 14f
internal const val CURVE_GRAB_DP = 30f
