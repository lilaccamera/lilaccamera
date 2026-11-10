package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.ml.DepthModelManager

/**
 * Shown the first time Portrait is chosen, and on a long press of the Portrait blur tile: how the mode
 * finds faces and the subject, that the blur can be changed later, and the two blur kinds. The depth model
 * can be downloaded right here.
 */
@Composable
internal fun PortraitIntroDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val modelState = DepthModelManager.observe(context).collectAsState().value
    val busy = modelState is DepthModelDownloadState.Downloading
    val ready = modelState is DepthModelDownloadState.Ready
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portrait_intro_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            // Scrolls on small screens; the text is longer than one 4.6-inch screen can show at large font sizes.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.portrait_intro_viewfinder), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.portrait_intro_subject), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.portrait_intro_edit), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.portrait_intro_blur), style = MaterialTheme.typography.bodyMedium)
                when {
                    ready -> Text(stringResource(R.string.portrait_intro_ready), style = MaterialTheme.typography.bodyMedium)
                    busy -> {
                        val progress = (modelState as DepthModelDownloadState.Downloading).progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(stringResource(R.string.depth_model_downloading), style = MaterialTheme.typography.bodySmall)
                    }
                    modelState is DepthModelDownloadState.Failed ->
                        Text(stringResource(R.string.depth_model_download_failed), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (ready || busy) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (ready) R.string.depth_model_done else R.string.portrait_intro_quick))
                }
            } else {
                TextButton(onClick = { DepthModelManager.download(context) }) {
                    Text(stringResource(R.string.portrait_intro_download))
                }
            }
        },
        dismissButton = if (ready || busy) null else {
            {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.portrait_intro_quick)) }
            }
        },
    )
}
