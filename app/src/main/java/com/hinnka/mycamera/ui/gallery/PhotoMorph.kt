package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The viewer's and the editor's side of the navigation between them: while it runs, the picture moves from where one
 * screen draws it to where the other does, and the rows around it slide off and on at their edges.
 * [active] when the latest navigation went between the two screens (not to or from any other).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class PhotoMorph(val shared: SharedTransitionScope, val screen: AnimatedVisibilityScope, val active: Boolean) {
    val running: Boolean get() = active && screen.transition.currentState != screen.transition.targetState
}

val LocalPhotoMorph = compositionLocalOf<PhotoMorph?> { null }

const val PHOTO_MORPH_MS = 260
val PhotoMorphEasing = CubicBezierEasing(.2f, .8f, .3f, 1f)

/**
 * The picture that moves: [bitmap] drawn at [bounds] (px, in the parent box). Only one picture moves at a time, so
 * one key serves both ends, also when a save shows a new version.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BoxScope.PhotoMorphProxy(bitmap: Bitmap, bounds: Rect) {
    val morph = LocalPhotoMorph.current ?: return
    val density = LocalDensity.current
    with(morph.shared) {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .sharedElement(
                    rememberSharedContentState("photo-morph"),
                    morph.screen,
                    boundsTransform = BoundsTransform { _, _ -> tween(PHOTO_MORPH_MS, easing = PhotoMorphEasing) },
                )
        )
    }
}

/** A row at the top ([top]) or bottom edge: during the move it slides a little off that edge as it fades. */
@Composable
fun Modifier.photoMorphEdge(top: Boolean): Modifier {
    val morph = LocalPhotoMorph.current?.takeIf { it.active } ?: return this
    val shift = with(LocalDensity.current) { 16.dp.roundToPx() } * (if (top) -1 else 1)
    val spec = tween<IntOffset>(PHOTO_MORPH_MS, easing = PhotoMorphEasing)
    val fade = tween<Float>(PHOTO_MORPH_MS, easing = PhotoMorphEasing)
    return with(morph.screen) {
        this@photoMorphEdge.animateEnterExit(
            enter = fadeIn(fade) + slideInVertically(spec) { shift },
            exit = fadeOut(fade) + slideOutVertically(spec) { shift },
        )
    }
}
