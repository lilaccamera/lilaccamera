package com.hinnka.mycamera.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.video.SlowedCopy
import com.hinnka.mycamera.viewmodel.GalleryViewModel

/** "Slowed or Full speed" for high-frame-rate videos being shared or exported; shows while the view model asks. */
@Composable
fun HighFrameRateChoiceDialog(viewModel: GalleryViewModel) {
    val choice = viewModel.highFrameRateChoice ?: return
    fun pick(slowed: Boolean) {
        viewModel.highFrameRateChoice = null
        choice.onPick(slowed)
    }
    fun length(ms: Long?) = ms?.let { " · " + formatLength(it) } ?: ""
    AlertDialog(
        onDismissRequest = { viewModel.highFrameRateChoice = null },
        title = {
            Text(
                stringResource(
                    if (choice.forExport) R.string.hfr_export_title else R.string.hfr_share_title,
                    choice.captureFps,
                )
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.hfr_choice_line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ChoiceRow(
                    stringResource(R.string.hfr_choice_slowed),
                    stringResource(R.string.hfr_choice_slowed_detail) + length(choice.durationMs?.times(SlowedCopy.FACTOR)),
                ) { pick(true) }
                ChoiceRow(
                    stringResource(R.string.hfr_choice_full),
                    stringResource(R.string.hfr_choice_full_detail) + length(choice.durationMs),
                ) { pick(false) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.highFrameRateChoice = null }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ChoiceRow(title: String, detail: String, onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** "32 s", or "1:05" from a minute on. */
fun formatLength(ms: Long): String {
    val s = (ms + 500) / 1000
    return if (s < 60) "$s s" else "%d:%02d".format(s / 60, s % 60)
}
