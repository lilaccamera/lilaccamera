package com.hinnka.mycamera.ui.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.settings.SettingsSheet
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
    SettingsSheet(
        title = stringResource(
            if (choice.forExport) R.string.hfr_export_title else R.string.hfr_share_title,
            choice.captureFps,
        ),
        onDismiss = { viewModel.highFrameRateChoice = null },
        description = stringResource(R.string.hfr_choice_line),
    ) {
        ViewerOption(
            stringResource(R.string.hfr_choice_slowed),
            stringResource(R.string.hfr_choice_slowed_detail) + length(choice.durationMs?.times(SlowedCopy.FACTOR)),
            chosen = false,
        ) { pick(true) }
        ViewerOption(
            stringResource(R.string.hfr_choice_full),
            stringResource(R.string.hfr_choice_full_detail) + length(choice.durationMs),
            chosen = false,
        ) { pick(false) }
    }
}

/** "32 s", or "1:05" from a minute on. */
fun formatLength(ms: Long): String {
    val s = (ms + 500) / 1000
    return if (s < 60) "$s s" else "%d:%02d".format(s / 60, s % 60)
}
