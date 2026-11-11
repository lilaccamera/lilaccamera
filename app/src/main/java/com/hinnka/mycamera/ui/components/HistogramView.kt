package com.hinnka.mycamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

private const val HistogramBins = 32

/**
 * The picture's brightness as a small outlined graph: 32 bins, square-root heights so dark and bright
 * tails stay visible, a light fill and a 1 dp outline with a soft shadow to read over the picture.
 *
 * @param histogram 256 brightness counts
 */
@Composable
fun HistogramView(
    histogram: IntArray?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (histogram == null || histogram.isEmpty()) return@Canvas
        val perBin = (histogram.size + HistogramBins - 1) / HistogramBins
        val bins = FloatArray(HistogramBins) { bin ->
            var sum = 0L
            for (i in bin * perBin until minOf(histogram.size, (bin + 1) * perBin)) sum += histogram[i]
            sum.toFloat()
        }
        val max = bins.max()
        if (max <= 0f) return@Canvas

        val line = 1.dp.toPx()
        val height = size.height - line
        val path = Path().apply {
            moveTo(0f, size.height)
            bins.forEachIndexed { i, count ->
                lineTo(i * size.width / (HistogramBins - 1), line + height - height * sqrt(count / max))
            }
            lineTo(size.width, size.height)
            close()
        }
        translate(top = line) {
            drawPath(path, Color.Black.copy(alpha = 0.45f), style = Stroke(width = line * 2))
        }
        drawPath(path, Color.White.copy(alpha = 0.28f))
        drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(width = line))
    }
}
