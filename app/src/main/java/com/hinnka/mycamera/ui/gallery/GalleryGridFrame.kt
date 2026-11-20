package com.hinnka.mycamera.ui.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.hinnka.mycamera.utils.VibrationHelper
import com.hinnka.mycamera.viewmodel.GalleryTab
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Holds the grid (first child) and switches Lilac / System with a sideways swipe. System lies to the right of
 * Lilac, so the swipe only moves towards the other tab and never past either end.
 */
internal class GalleryGridFrame(context: Context) : FrameLayout(context) {
    var tab = GalleryTab.PHOTON
    // Off while selecting and while refreshing.
    var swipeEnabled = true
    var onSwitch: (GalleryTab) -> Unit = {}

    private val dp = resources.displayMetrics.density
    private val grid: View get() = getChildAt(0)
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var dragging = false

    /** Brings a newly shown tab's grid in from the side it lies on. */
    fun slideIn() {
        grid.translationX = (if (tab == GalleryTab.SYSTEM) 60 else -60) * dp
        grid.alpha = 0f
        settle()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                // The fast scroller owns the right-hand strip.
                tracking = swipeEnabled && ev.x < width - 24 * dp
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > 12 * dp && abs(dx) > 1.5f * abs(dy)) {
                    tracking = false
                    if (towardsOtherTab(dx) != 0f) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return false
        val m = towardsOtherTab(ev.x - downX)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                grid.animate().cancel()
                grid.translationX = m * 0.5f
                grid.alpha = 1f - min(0.5f, abs(m) / (400 * dp))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (ev.actionMasked == MotionEvent.ACTION_UP && abs(m) > 50 * dp) {
                    VibrationHelper(context).vibrate(VibrationHelper.LIGHT_TICK_MS)
                    // The new tab's grid replaces this one and slides in.
                    onSwitch(if (tab == GalleryTab.PHOTON) GalleryTab.SYSTEM else GalleryTab.PHOTON)
                } else {
                    settle()
                }
            }
        }
        return true
    }

    // The part of a drag that points towards the other tab: leftwards on Lilac, rightwards on System.
    private fun towardsOtherTab(dx: Float) = if (tab == GalleryTab.PHOTON) min(0f, dx) else max(0f, dx)

    private fun settle() {
        grid.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(Ease).start()
    }

    private companion object {
        val Ease = PathInterpolator(.2f, .8f, .3f, 1f)
    }
}
