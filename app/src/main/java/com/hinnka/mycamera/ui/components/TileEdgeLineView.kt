package com.hinnka.mycamera.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.SystemClock
import android.view.View
import kotlin.math.roundToInt

/**
 * [ThumbnailEdgeRing] for View tiles: a 2 dp line on the tile's own rounded edge. RUNNING laps an [accent] arc
 * round a faint track, DOTTED shows even dots, SOLID the whole edge.
 */
internal class TileEdgeLineView(context: Context) : View(context) {
    var style: EdgeRingStyle? = null
        set(value) {
            if (field != value) { field = value; invalidate() }
        }
    var accent: Int = 0xFFFF9C00.toInt()
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val density = resources.displayMetrics.density
    private val stroke = 2 * density
    private val corner = 4 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    private val edge = Path()
    private var length = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Inset by half the stroke so the whole line sits inside the tile's clip.
        val inset = stroke / 2
        edge.reset()
        edge.addRoundRect(inset, inset, w - inset, h - inset, corner - inset, corner - inset, Path.Direction.CW)
        length = PathMeasure(edge, false).length
    }

    override fun onDraw(canvas: Canvas) {
        if (length <= 0f) return
        when (style) {
            EdgeRingStyle.RUNNING -> {
                paint.color = accent
                paint.alpha = 64
                paint.strokeCap = Paint.Cap.BUTT
                paint.pathEffect = null
                canvas.drawPath(edge, paint)
                // One dash as long as the arc, one period per lap: on a closed edge it runs across the start with no seam.
                val arc = length * 0.25f
                val phase = (SystemClock.uptimeMillis() % LAP_MS) / LAP_MS.toFloat()
                paint.alpha = 255
                paint.strokeCap = Paint.Cap.ROUND
                paint.pathEffect = DashPathEffect(floatArrayOf(arc, length - arc), (length - phase * length) % length)
                canvas.drawPath(edge, paint)
                // Frames are asked for only while drawn: a detached or hidden tile stops the lap by itself.
                if (isShown) postInvalidateOnAnimation()
            }
            EdgeRingStyle.DOTTED -> {
                // A whole number of periods round the edge keeps the spacing even where the line closes.
                val count = (length / (8 * density)).roundToInt().coerceAtLeast(4)
                val period = length / count
                // Round caps add half the stroke at each end.
                val on = (period - 3.5f * density - stroke).coerceAtLeast(0.01f)
                paint.color = 0x99E0E0E0.toInt()
                paint.strokeCap = Paint.Cap.ROUND
                paint.pathEffect = DashPathEffect(floatArrayOf(on, period - on), 0f)
                canvas.drawPath(edge, paint)
            }
            EdgeRingStyle.SOLID -> {
                paint.color = 0xFFE0E0E0.toInt()
                paint.strokeCap = Paint.Cap.BUTT
                paint.pathEffect = null
                canvas.drawPath(edge, paint)
            }
            null -> Unit
        }
    }

    private companion object {
        const val LAP_MS = 1600L
    }
}
