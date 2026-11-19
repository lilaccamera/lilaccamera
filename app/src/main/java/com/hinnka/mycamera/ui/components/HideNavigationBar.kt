package com.hinnka.mycamera.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Screens that want the bar hidden right now. The editor closing over the viewer must not bring it back:
// during the switch both are composed, and whichever leaves last decides.
private var navigationBarHiders = 0

/** Runs the screen immersive: the navigation bar hides while this is composed and a swipe shows it for a moment. */
@Composable
internal fun HideNavigationBar() {
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view, lifecycle) {
        val controller = context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        fun hide() {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.navigationBars())
        }
        navigationBarHiders++
        hide()
        // Coming back from a screen that showed the bar (or from another app), hide it again.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hide() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (--navigationBarHiders == 0) controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
