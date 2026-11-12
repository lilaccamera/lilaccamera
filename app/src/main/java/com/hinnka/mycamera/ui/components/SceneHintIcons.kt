package com.hinnka.mycamera.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.camera.SceneHint
import com.hinnka.mycamera.ui.camera.autoRotate
import com.hinnka.mycamera.ui.icons.AppIcons

/**
 * What the camera reports about the scene, as Sony's camera shows it: plain icons at the right end of the picture's
 * top edge, after a hairline and without the setting marks' shadow, so they read as status, not settings. Not
 * tappable; each turns in place with the phone.
 */
@Composable
fun SceneHintIcons(hints: List<SceneHint>, modifier: Modifier = Modifier) {
    AnimatedContent(hints, modifier, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "sceneHints") { shown ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shown.isNotEmpty()) {
                Box(Modifier.width(0.5.dp).height(14.dp).background(Color.White.copy(alpha = 0.35f)))
                Spacer(Modifier.width(4.dp))
            }
            for (hint in shown) {
                Icon(hint.icon(), contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.autoRotate().size(20.dp))
            }
        }
    }
}

private fun SceneHint.icon() = when (this) {
    SceneHint.BACKLIGHT -> AppIcons.HazeLine
    SceneHint.NIGHT -> AppIcons.MoonLine
    SceneHint.LOW_LIGHT -> AppIcons.CandleLine
    SceneHint.LANDSCAPE -> AppIcons.MountainLine
    SceneHint.PORTRAIT -> AppIcons.UserLine
    SceneHint.DOCUMENT -> AppIcons.FileTextLine
    SceneHint.FOOD -> AppIcons.KitchenLine
    SceneHint.SPOTLIGHT -> AppIcons.LampLine
    SceneHint.MACRO -> AppIcons.MacroLine
    SceneHint.TRIPOD -> AppIcons.TripodLine
    SceneHint.WALKING -> AppIcons.WalkLine
    SceneHint.MOTION -> AppIcons.RunLine
}
