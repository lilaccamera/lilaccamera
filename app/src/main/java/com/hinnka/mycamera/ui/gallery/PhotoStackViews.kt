package com.hinnka.mycamera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.PhotoStacks
import com.hinnka.mycamera.render.RenderLook
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.viewmodel.GalleryViewModel

/** The photo view's label for a version: the full re-render look, or what the camera JPEG is. */
internal fun photoVersionLabel(context: Context, metadata: MediaMetadata?): String? = when {
    PhotoStacks.isCameraJpeg(metadata) -> context.getString(R.string.photo_version_camera_jpeg_label)
    else -> metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY)
}

/** One word under a version in the strip: "Before" (camera JPEG), the re-render's short look, or "Original". */
private fun versionCaption(context: Context, version: MediaData, stackLabels: List<String>): String {
    if (PhotoStacks.isCameraJpeg(version.metadata)) return context.getString(R.string.photo_version_before)
    val label = version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY)
        ?: return context.getString(R.string.photo_version_original)
    return RenderLook.shortLabel(label, stackLabels)
}

/** Switches the shown version of a stack, oldest first; [processingIds] still render and show a spinner. */
@Composable
internal fun PhotoVersionStrip(
    versions: List<MediaData>,
    selectedId: String,
    processingIds: Set<String>,
    viewModel: GalleryViewModel,
    onSelect: (MediaData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stackLabels = versions.mapNotNull { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        versions.forEach { version ->
            val selected = version.id == selectedId
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onSelect(version) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val thumbnail by produceState<Bitmap?>(null, version.id, version.thumbnailUri) {
                    value = viewModel.getGridThumbnailBitmap(version)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A1A))
                        .border(
                            if (selected) BorderStroke(2.dp, AccentColor) else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    thumbnail?.takeUnless { it.isRecycled }?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    if (version.id in processingIds) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }
                Text(
                    text = versionCaption(context, version, stackLabels),
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Stacked delete choices for a photo stack with more than one version, Cancel last. */
@Composable
internal fun DeleteVersionsButtons(
    thisVersionText: String,
    allVersionsText: String,
    onDeleteThis: () -> Unit,
    onDeleteAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        TextButton(onClick = onDeleteThis) { Text(thisVersionText, color = Color.Red) }
        TextButton(onClick = onDeleteAll) { Text(allVersionsText, color = Color.Red) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}
