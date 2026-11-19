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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.hinnka.mycamera.ui.components.FramedTag
import com.hinnka.mycamera.ui.components.SonyDim
import com.hinnka.mycamera.video.SlowedCopy
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
 * The tag in a strip thumbnail's corner: CAM (built-in camera), ×9 (Photo mode stack, JPG if it saved no count),
 * RAW (Pro original), R1, R2… (re-renders by age).
 */
internal fun versionTag(version: MediaData, versions: List<MediaData>): String = when {
    PhotoStacks.isCameraJpeg(version.metadata) -> "CAM"
    version.metadata?.captureMode == "jpg_max" -> stackedFrameCount(version)?.let { "×$it" } ?: "JPG"
    version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) == null -> "RAW"
    else -> "R" + (versions.takeWhile { it.id != version.id }
        .count { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) != null } + 1)
}

/** One tag on the viewer's top row: [quiet] tags are grey and the first to go when the row is full. */
internal data class ViewerTag(
    val text: String,
    val color: Color = SonyText,
    val quiet: Boolean = false,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

/**
 * What the viewer's top row says about [photo] (the item's own data; on the system tab the Lilac photo it came
 * from), in a fixed order. [version] is the shown version of a stack of [versions]. Plain tags explain themselves
 * through [explain]; LIVE, BURST and HDR act instead. An on state shows in [accent]. A [burstCount] of 0 or a null [hdrOn] leaves that tag out.
 */
internal fun viewerTags(
    context: Context,
    photo: MediaData,
    version: MediaData,
    versions: List<MediaData>,
    isRaw: Boolean,
    burstCount: Int,
    canPlayLive: Boolean,
    livePlaying: Boolean,
    hdrOn: Boolean?,
    videoRate: SlowedCopy.Rate?,
    videoSlowed: Boolean,
    onLive: () -> Unit,
    onBurst: () -> Unit,
    onHdr: () -> Unit,
    explain: (String) -> Unit,
    accent: Color,
): List<ViewerTag> = buildList {
    val metadata = photo.metadata
    if (isRaw) add(ViewerTag(context.getString(R.string.gallery_raw_badge)) { explain(context.getString(R.string.viewer_tag_raw_hint)) })
    stackedFrameCount(photo)?.let { n ->
        add(ViewerTag("×$n") { explain(context.getString(R.string.viewer_tag_stacked_hint, n)) })
    }
    metadata?.multipleExposureFrameCount?.takeIf { metadata.captureMode == "multiple_exposure" && it > 1 }?.let { n ->
        add(ViewerTag("ME ×$n") { explain(context.getString(R.string.viewer_tag_multiple_exposure_hint, n)) })
    }
    if (burstCount > 0) add(ViewerTag("BURST $burstCount", onClick = onBurst))
    if (canPlayLive) {
        add(ViewerTag("LIVE", color = if (livePlaying) accent else SonyText, icon = Icons.Filled.PlayArrow, onClick = onLive))
    }
    if (hdrOn != null) add(ViewerTag(context.getString(R.string.hdr_label), color = if (hdrOn) accent else SonyText, onClick = onHdr))
    if (metadata?.computationalAperture != null) add(ViewerTag("PORT") { explain(context.getString(R.string.viewer_tag_portrait_hint)) })
    if (metadata?.hasAiDenoisedBase == true) add(ViewerTag("AI NR") { explain(context.getString(R.string.viewer_tag_ai_denoise_hint)) })
    if (videoRate?.isHighFrameRate == true) {
        val fps = if (videoRate.captureFps >= 900f) 960 else 120
        add(ViewerTag("$fps FPS", color = if (videoSlowed) accent else SonyText) {
            explain(context.getString(R.string.viewer_tag_high_frame_rate_hint, fps))
        })
    }
    if (photo.isVideo) {
        val w = metadata?.videoWidth ?: 0
        val h = metadata?.videoHeight ?: 0
        val fps = metadata?.frameRate ?: 0
        if (w > 0 && h > 0 && fps > 0) {
            val short = minOf(w, h)
            val size = when {
                short >= 2160 -> "4K"
                short >= 1080 -> "FHD"
                short >= 720 -> "HD"
                else -> "${short}p"
            }
            add(ViewerTag("$size $fps", color = SonyDim, quiet = true) {
                explain(context.getString(R.string.viewer_tag_video_format_hint, w, h, fps))
            })
        }
    }
    if (versions.size > 1) {
        val tag = versionTag(version, versions).let { if (it == "RAW") "ORIG" else it }
        // A Photo mode stack's own tag already reads ×9.
        if (none { it.text == tag }) {
            val label = version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY)
            val about = when {
                PhotoStacks.isCameraJpeg(version.metadata) -> context.getString(R.string.photo_version_camera_jpeg_label)
                label != null -> RenderLook.shortLabel(label, versions.mapNotNull { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) })
                tag == "ORIG" -> context.getString(R.string.viewer_version_original)
                else -> stackedFrameCount(version)?.let { context.getString(R.string.photo_version_stacked, it) }
                    ?: context.getString(R.string.viewer_version_photo_mode)
            }
            add(ViewerTag(tag, color = SonyDim, quiet = true) { explain(context.getString(R.string.viewer_tag_version_hint, tag, about)) })
        }
    }
}

/**
 * The tags in one row as wide as they are, never wider than the space given: quiet tags go first, from the end,
 * then whatever still does not fit is left off at the end.
 */
@Composable
internal fun ViewerTagRow(tags: List<ViewerTag>, modifier: Modifier = Modifier) {
    Layout(
        content = { tags.forEach { FramedTag(it.text, color = it.color, icon = it.icon, onClick = it.onClick) } },
        modifier = modifier
    ) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        val kept = placeables.indices.toMutableList()
        fun width() = kept.sumOf { placeables[it].width } + gap * (kept.size - 1).coerceAtLeast(0)
        while (width() > constraints.maxWidth) {
            kept.remove(kept.lastOrNull { tags[it].quiet } ?: kept.last())
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width().coerceAtLeast(0), height) {
            var x = 0
            kept.forEach { i ->
                placeables[i].placeRelative(x, (height - placeables[i].height) / 2)
                x += placeables[i].width + gap
            }
        }
    }
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
