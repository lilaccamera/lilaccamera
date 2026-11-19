package com.hinnka.mycamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.data.ZoomControl
import com.hinnka.mycamera.ui.camera.ZoomCollarControl
import com.hinnka.mycamera.ui.camera.ZoomDialControl
import com.hinnka.mycamera.ui.camera.ZoomStops
import com.hinnka.mycamera.ui.camera.rememberZoomStops
import com.hinnka.mycamera.viewmodel.CameraViewModel

/** The Zoom control row: its sheet shows each control working, to try before choosing. */
@Composable
internal fun ZoomControlSetting(viewModel: CameraViewModel, current: ZoomControl, onPick: (ZoomControl) -> Unit) {
    val names = ZoomControl.entries.associateWith { control ->
        stringResource(
            when (control) {
                ZoomControl.THUMBWHEEL -> R.string.settings_zoom_control_thumbwheel
                ZoomControl.PILL -> R.string.settings_zoom_control_pill
                ZoomControl.COLLAR -> R.string.settings_zoom_control_collar
            }
        )
    }
    val title = stringResource(R.string.settings_zoom_control)
    var open by remember { mutableStateOf(false) }
    SettingsValueRow(title, null, names.getValue(current), enabled = true) { open = true }
    if (!open) return

    val state by viewModel.state.collectAsState()
    val stops = rememberZoomStops(viewModel, state.availableCameras, state.getCurrentCameraInfo()?.cameraId ?: "0")
    val tick = rememberSettingsTick()
    // One zoom for all three, so the others follow whichever is turned.
    val scope = rememberCoroutineScope()
    var zoom by remember(stops.min) { mutableFloatStateOf(1f.coerceIn(stops.min, stops.max)) }
    SettingsSheet(title, onDismiss = { open = false }, description = stringResource(R.string.settings_zoom_control_try)) {
        ZoomControl.entries.forEach { control ->
            val on = control == current
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) SettingsText else Color.Transparent)
                    // Picking keeps the sheet open, so the others can still be tried.
                    .clickable {
                        tick()
                        onPick(control)
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(names.getValue(control), color = if (on) Color.Black else SettingsText, fontSize = 16.sp, maxLines = 1)
            }
            ZoomPreview(
                control, stops, zoom,
                onZoom = { zoom = it },
                // The collar's steps jump; ease them so the other two turn along.
                onStep = { target ->
                    scope.launch { animate(zoom, target, animationSpec = tween(320)) { v, _ -> zoom = v } }
                }
            )
        }
    }
}

/** One control on its own, for trying; it zooms nothing. */
@Composable
private fun ZoomPreview(control: ZoomControl, stops: ZoomStops, zoom: Float, onZoom: (Float) -> Unit, onStep: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().height(68.dp)) {
        if (control == ZoomControl.COLLAR) {
            // The collar lifts its own box 53 dp off its centre; this puts the box's top 7 dp down.
            ZoomCollarControl(
                zoomStops = stops,
                zoomRatio = zoom,
                enabled = true,
                onZoom = onZoom,
                onStop = onStep,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 60.dp)
            )
        } else {
            // The box starts 8 dp over the control; the wheel's index hangs 8 dp under it.
            ZoomDialControl(
                kind = control,
                overPicture = false,
                zoomStops = stops,
                zoomRatio = zoom,
                enabled = true,
                onZoom = onZoom,
                onReading = {},
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp)
            )
        }
    }
}
