package com.hinnka.mycamera.ui.camera

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlinx.coroutines.delay

/** What the note line says: why a press did nothing, or that something happened. */
@Stable
internal class NoteLineState {
    var text by mutableIntStateOf(0)
        private set
    /** A note put together at run time, shown in place of [text]. */
    var line by mutableStateOf<String?>(null)
        private set
    var shown by mutableStateOf(false)
        private set
    // Counts every note, so the same note said again holds the line for its full time.
    var serial by mutableIntStateOf(0)
        private set

    fun say(@StringRes text: Int) {
        this.text = text
        icon = null
        holdMs = 1600L
        line = null
        shown = true
        serial++
    }

    /** The icon and time on screen of the current note; a plain note has the info icon for 1.6 s. */
    var icon by mutableStateOf<ImageVector?>(null)
        private set
    var holdMs = 1600L
        private set

    fun say(line: String, icon: ImageVector? = null, holdMs: Long = 1600L) {
        text = -1
        this.icon = icon
        this.holdMs = holdMs
        this.line = line
        shown = true
        serial++
    }

    fun hide() {
        shown = false
    }
}

/** A short note in place of a toast, shown for about 1.6 s. */
@Composable
internal fun NoteLine(state: NoteLineState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.serial) {
        delay(state.holdMs)
        state.hide()
    }
    AnimatedVisibility(
        visible = state.shown && state.text != 0,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        // The same pill as the mode guides' offers, since it is information too; a tap puts it away.
        ModeGuidePill(icon = state.icon ?: AppIcons.InfoLine, text = state.line ?: stringResource(state.text), onClick = state::hide)
    }
}
