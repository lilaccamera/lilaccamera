package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraHeat

private val RowIconSize = 28.dp
private val RowIconGap = 12.dp

/**
 * Shown after a recording was saved and stopped for heat. The camera stays off while it is open.
 * One card, two rows laid out alike: what stopped the recording, and the phone body cooling live
 * toward [CameraHeat.COOLED_C] (from [coolFromC], its temperature when cooling started).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoHeatStopDialog(
    stopped: CameraHeat.Gauge?,
    phoneNow: CameraHeat.Gauge?,
    coolFromC: Int?,
    onBackToCamera: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(onDismissRequest = onBackToCamera) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.video_heat_stopped_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.video_heat_stopped_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertDialogDefaults.textContentColor,
                )
                if (stopped != null || phoneNow != null) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerHighest,
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            if (stopped != null) {
                                HeatRow(
                                    label = stringResource(R.string.video_heat_stopped_by),
                                    name = stringResource(stopped.source.label),
                                    tempC = stopped.nowC,
                                    color = colors.error,
                                )
                            }
                            if (stopped != null && phoneNow != null) {
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            }
                            if (phoneNow != null) {
                                CoolingRow(phoneNow, coolFromC)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBackToCamera, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.video_heat_back_to_camera))
                }
            }
        }
    }
}

@Composable
private fun CoolingRow(phoneNow: CameraHeat.Gauge, coolFromC: Int?) {
    val colors = MaterialTheme.colorScheme
    val target = CameraHeat.COOLED_C
    val cool = phoneNow.nowC <= target
    val color = if (cool) colors.primary else colors.error
    val from = maxOf(coolFromC ?: phoneNow.nowC, target + 1)
    val progress = if (cool) 1f else ((from - phoneNow.nowC).toFloat() / (from - target)).coerceIn(0.04f, 1f)
    HeatRow(
        label = stringResource(R.string.video_heat_cooling),
        name = stringResource(phoneNow.source.label),
        tempC = phoneNow.nowC,
        color = color,
    )
    // Under the text column, so the bar lines up with the label and name above it; the target ends the bar.
    Column(Modifier.padding(start = RowIconSize + RowIconGap, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.onSurface.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.video_heat_degrees, target),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (cool) {
                stringResource(R.string.video_heat_cool_enough)
            } else {
                stringResource(R.string.video_heat_to_go, phoneNow.nowC - target)
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Thermometer, a small label over the reading's name, and the temperature large on the right. */
@Composable
private fun HeatRow(label: String, name: String, tempC: Int, color: Color) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeatThermometer(
            outlineColor = colors.onSurface,
            readingColor = color,
            modifier = Modifier.size(RowIconSize),
        )
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.video_heat_degrees, tempC),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
        )
    }
}
