package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraHeat

/**
 * Shown after a recording was saved and stopped for heat. The camera stays off while it is open;
 * [phoneNow] updates live so the user can wait for the phone to cool down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoHeatStopDialog(
    stopped: CameraHeat.Gauge?,
    phoneNow: CameraHeat.Gauge?,
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
                Spacer(Modifier.height(16.dp))
                // The thermometer is as tall as the two temperature lines beside it.
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeatThermometer(
                        outlineColor = colors.onSurface,
                        readingColor = colors.error,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                    Column {
                        if (stopped != null) {
                            Text(
                                text = stringResource(
                                    R.string.video_heat_reached,
                                    stringResource(stopped.source.label),
                                    stopped.nowC,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.error,
                            )
                        }
                        if (phoneNow != null) {
                            Text(
                                text = stringResource(
                                    R.string.video_heat_now,
                                    stringResource(phoneNow.source.label),
                                    phoneNow.nowC,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (phoneNow.isHot) colors.error else colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.video_heat_stopped_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertDialogDefaults.textContentColor,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBackToCamera, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.video_heat_back_to_camera))
                }
            }
        }
    }
}
