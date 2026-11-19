package com.hinnka.mycamera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.components.EdgeRingStyle
import com.hinnka.mycamera.ui.components.SonyText
import com.hinnka.mycamera.ui.components.ThumbnailEdgeRing
import com.hinnka.mycamera.ui.components.ThumbnailGloss
import com.hinnka.mycamera.ui.components.ThumbnailSize
import com.hinnka.mycamera.ui.components.ThumbnailTile
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Frames merged into a Photo mode stack, when the shot saved it (shots before 2026-11-08 did not). */
internal fun stackedFrameCount(version: MediaData): Int? = version.metadata
    ?.takeIf { it.captureMode == "jpg_max" }
    ?.multipleExposureFrameCount
    ?.takeIf { it > 1 }

/**
 * The top-left badge: "RAW", "RAW · NR 0.42" for a re-render (what sets it apart in its stack),
 * the built-in camera's label, "Stacked · 9 frames" for a Photo mode stack, or null for a photo that is none of these.
 */
internal fun photoBadgeLabel(context: Context, version: MediaData, versions: List<MediaData>, isRaw: Boolean): String? {
    if (PhotoStacks.isCameraJpeg(version.metadata)) return context.getString(R.string.photo_version_camera_jpeg_label)
    val raw = context.getString(R.string.gallery_raw_badge)
    val label = version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY)
        ?: return if (isRaw) raw else stackedFrameCount(version)?.let { context.getString(R.string.photo_version_stacked, it) }
    val stackLabels = versions.mapNotNull { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) }
    return "$raw · ${RenderLook.shortLabel(label, stackLabels)}"
}

/**
 * The tag in a strip thumbnail's corner: CAM (built-in camera), ×9 (Photo mode stack, JPG if it saved no count),
 * RAW (Pro original), R1, R2… (re-renders by age).
 */
private fun versionTag(version: MediaData, versions: List<MediaData>): String = when {
    PhotoStacks.isCameraJpeg(version.metadata) -> "CAM"
    version.metadata?.captureMode == "jpg_max" -> stackedFrameCount(version)?.let { "×$it" } ?: "JPG"
    version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) == null -> "RAW"
    else -> "R" + (versions.takeWhile { it.id != version.id }
        .count { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) != null } + 1)
}

/**
 * The versions of a stack, oldest first, as a row that scrolls under a fixed centre: the centred thumbnail is the
 * shown version and carries the viewfinder thumbnail's edge line. Flicking switches versions live as each one
 * reaches the centre; a tap centres that one. [runningIds] render now (running arc), [waitingIds] wait (dots).
 */
@Composable
internal fun PhotoVersionStrip(
    versions: List<MediaData>,
    selectedId: String,
    runningIds: Set<String>,
    waitingIds: Set<String>,
    viewModel: GalleryViewModel,
    onSelect: (MediaData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentVersions by rememberUpdatedState(versions)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Live selection: the item nearest the centre of the row is the shown version.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
        }.filterNotNull().distinctUntilChanged().collect { index ->
            if (listState.isScrollInProgress) currentVersions.getOrNull(index)?.let(currentOnSelect)
        }
    }
    // A version chosen elsewhere (opening the photo, deleting one) is brought to the centre.
    LaunchedEffect(selectedId, versions.size) {
        val index = versions.indexOfFirst { it.id == selectedId }
        if (index >= 0 && !listState.isScrollInProgress) listState.scrollToItem(index)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            // Half the row on each side, so the first and last versions can reach the centre too.
            contentPadding = PaddingValues(horizontal = (maxWidth - ThumbnailSize) / 2),
            horizontalArrangement = Arrangement.spacedBy(StripGap),
        ) {
            itemsIndexed(versions, key = { _, version -> version.id }) { index, version ->
                val selected = version.id == selectedId
                val thumbnail by produceState<Bitmap?>(null, version.id, version.thumbnailUri) {
                    value = viewModel.getGridThumbnailBitmap(version)
                }
                // The edge line lies over the whole tile, as on the viewfinder thumbnail.
                Box {
                ThumbnailTile(
                    onClick = { scope.launch { listState.animateScrollToItem(index) }; onSelect(version) },
                    modifier = Modifier.semantics { this.selected = selected }
                ) {
                    thumbnail?.takeUnless { it.isRecycled }?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        ThumbnailGloss()
                    }
                    Text(
                        text = versionTag(version, versions),
                        color = Color.White,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp)
                    )
                }
                when {
                    version.id in runningIds -> ThumbnailEdgeRing(EdgeRingStyle.RUNNING, ModeAmber, Modifier.matchParentSize())
                    version.id in waitingIds -> ThumbnailEdgeRing(EdgeRingStyle.DOTTED, SonyText, Modifier.matchParentSize())
                    selected -> ThumbnailEdgeRing(EdgeRingStyle.SOLID, SonyText, Modifier.matchParentSize())
                }
                }
            }
        }
    }
}

private val StripGap = 8.dp

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
