package com.hinnka.mycamera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.PhotoStacks
import com.hinnka.mycamera.render.RenderLook
import com.hinnka.mycamera.viewmodel.GalleryViewModel

/**
 * The top-left badge: "RAW", "RAW · NR 0.42" for a re-render (what sets it apart in its stack),
 * the camera JPEG's "before" label, or null for a photo that is none of these.
 */
internal fun photoBadgeLabel(context: Context, version: MediaData, versions: List<MediaData>, isRaw: Boolean): String? {
    if (PhotoStacks.isCameraJpeg(version.metadata)) return context.getString(R.string.photo_version_camera_jpeg_label)
    val raw = context.getString(R.string.gallery_raw_badge)
    val label = version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) ?: return raw.takeIf { isRaw }
    val stackLabels = versions.mapNotNull { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) }
    return "$raw · ${RenderLook.shortLabel(label, stackLabels)}"
}

/** The tag in a strip thumbnail's corner: JPG (camera JPEG), RAW (original), R1, R2… (re-renders by age). */
private fun versionTag(version: MediaData, versions: List<MediaData>): String = when {
    PhotoStacks.isCameraJpeg(version.metadata) -> "JPG"
    version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) == null -> "RAW"
    else -> "R" + (versions.takeWhile { it.id != version.id }
        .count { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) != null } + 1)
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
    val currentVersions by rememberUpdatedState(versions)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    // Scrubbing: a sideways drag selects the thumbnail under the finger; consumed, so the pager never pages.
    fun selectAt(x: Float, thumb: Dp) {
        val slot = with(density) { (thumb + StripGap).toPx() }
        val start = with(density) { StripPadding.toPx() }
        val index = ((x - start) / slot).toInt().coerceIn(0, currentVersions.lastIndex)
        currentVersions.getOrNull(index)?.let(currentOnSelect)
    }
    // More versions than fit at 48 dp shrink the thumbnails (to 24 dp at least), so every version stays reachable.
    BoxWithConstraints(modifier) {
    val thumb = ((maxWidth - StripPadding * 2 - StripGap * (versions.size - 1)) / versions.size).coerceIn(24.dp, StripThumb)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .pointerInput(thumb) {
                detectHorizontalDragGestures(onDragStart = { selectAt(it.x, thumb) }) { change, _ ->
                    change.consume()
                    selectAt(change.position.x, thumb)
                }
            }
            .padding(StripPadding),
        horizontalArrangement = Arrangement.spacedBy(StripGap)
    ) {
        versions.forEach { version ->
            val selected = version.id == selectedId
            Column(
                modifier = Modifier
                    .width(thumb)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onSelect(version) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val thumbnail by produceState<Bitmap?>(null, version.id, version.thumbnailUri) {
                    value = viewModel.getGridThumbnailBitmap(version)
                }
                Box(
                    modifier = Modifier
                        .size(thumb)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A1A))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.6f else 0.15f)),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    thumbnail?.takeUnless { it.isRecycled }?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(thumb)
                        )
                    }
                    if (version.id in processingIds) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                    Text(
                        text = versionTag(version, versions),
                        color = Color.White,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp)
                    )
                }
            }
        }
    }
    }
}

private val StripThumb = 48.dp
private val StripGap = 8.dp
private val StripPadding = 8.dp

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
