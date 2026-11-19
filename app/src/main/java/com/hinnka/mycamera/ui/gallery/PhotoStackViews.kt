package com.hinnka.mycamera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.hinnka.mycamera.ui.camera.CondensedBold
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.hinnka.mycamera.ui.components.FramedTag
import com.hinnka.mycamera.ui.icons.AppIcons
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
 * ORIG (Pro original), R1, R2… (re-renders by age).
 */
internal fun versionTag(version: MediaData, versions: List<MediaData>): String = when {
    PhotoStacks.isCameraJpeg(version.metadata) -> "CAM"
    version.metadata?.captureMode == "jpg_max" -> stackedFrameCount(version)?.let { "×$it" } ?: "JPG"
    version.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) == null -> "ORIG"
    else -> "R" + (versions.takeWhile { it.id != version.id }
        .count { it.metadata?.customProperties?.get(RenderLook.LABEL_PROPERTY) != null } + 1)
}

/** A video's size as the camera names it: 4K, FHD, HD, or its short side. */
internal fun videoSizeName(width: Int, height: Int): String = minOf(width, height).let { short ->
    when {
        short >= 2160 -> "4K"
        short >= 1080 -> "FHD"
        short >= 720 -> "HD"
        else -> "${short}p"
    }
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
 * through [explain]; LIVE and HDR act instead. An on state shows in [accent]. A [burstCount] of 0 or a null [hdrOn] leaves that tag out.
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
    isProtected: Boolean,
    onLive: () -> Unit,
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
    if (burstCount > 0) add(ViewerTag("BURST $burstCount") { explain(context.getString(R.string.viewer_tag_burst_hint, burstCount)) })
    if (canPlayLive) {
        add(ViewerTag("LIVE", color = if (livePlaying) accent else SonyText, icon = Icons.Filled.PlayArrow, onClick = onLive))
    }
    if (hdrOn != null) add(ViewerTag(context.getString(R.string.hdr_label), color = if (hdrOn) accent else SonyText, onClick = onHdr))
    if (metadata?.computationalAperture != null) add(ViewerTag("PORT") { explain(context.getString(R.string.viewer_tag_portrait_hint)) })
    if (metadata?.hasAiDenoisedBase == true) add(ViewerTag("AI NR") { explain(context.getString(R.string.viewer_tag_ai_denoise_hint)) })
    if (isProtected) add(ViewerTag("", icon = AppIcons.LockLine) { explain(context.getString(R.string.viewer_tag_protected_hint)) })
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
            add(ViewerTag("${videoSizeName(w, h)} $fps", color = SonyDim, quiet = true) {
                explain(context.getString(R.string.viewer_tag_video_format_hint, w, h, fps))
            })
        }
    }
    if (versions.size > 1) {
        val tag = versionTag(version, versions)
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
 * The versions of a stack, oldest first, in a [PhotoStrip]: the shown version carries the viewfinder thumbnail's
 * edge line. [runningIds] render now (running arc), [waitingIds] wait (dots).
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
    ghost: Boolean = false,
) {
    PhotoStrip(
        items = versions,
        selectedIndex = versions.indexOfFirst { it.id == selectedId },
        key = { it.id },
        onSelect = { onSelect(versions[it]) },
        modifier = modifier,
        ghost = ghost,
    ) { _, version ->
        val thumbnail by produceState<Bitmap?>(null, version.id, version.thumbnailUri) {
            value = viewModel.getGridThumbnailBitmap(version)
        }
        thumbnail?.takeUnless { it.isRecycled }?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        StripLabel(versionTag(version, versions))
        when {
            version.id in runningIds -> StripRing(EdgeRingStyle.RUNNING, ModeAmber)
            version.id in waitingIds -> StripRing(EdgeRingStyle.DOTTED, SonyText)
            version.id == selectedId -> StripRing(EdgeRingStyle.SOLID, SonyText)
        }
    }
}

/**
 * A row of small tiles that scrolls under a fixed centre: the centred tile is the shown one. Flicking switches
 * live as each tile reaches the centre; a tap centres that one. [tile] draws a tile's picture, label and edge line.
 * A [ghost] strip (a neighbouring item's, during a swipe) is drawn still, with nothing to touch.
 */
@Composable
internal fun <T> PhotoStrip(
    items: List<T>,
    selectedIndex: Int,
    key: (T) -> Any,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    tile: @Composable BoxScope.(index: Int, item: T) -> Unit,
) {
    if (ghost) {
        val centre = selectedIndex.coerceAtLeast(0)
        Box(modifier.fillMaxWidth().height(StripHeight), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .wrapContentWidth(unbounded = true)
                    .offset(x = (StripTileSize + StripGap) * ((items.size - 1) / 2f - centre)),
                horizontalArrangement = Arrangement.spacedBy(StripGap),
            ) {
                items.forEachIndexed { index, item ->
                    Box(Modifier.size(StripTileSize).clip(StripTileShape).background(StripTileColor)) {
                        // Only the tiles that can be on screen load a picture.
                        if (abs(index - centre) <= 4) tile(index, item)
                    }
                }
            }
        }
        return
    }
    // Starting at the chosen tile, which the content padding centres, so a new strip never shows the first one first.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val currentCount by rememberUpdatedState(items.size)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Live selection: the item nearest the centre of the row is the shown one.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
        }.filterNotNull().distinctUntilChanged().collect { index ->
            if (listState.isScrollInProgress && index < currentCount) currentOnSelect(index)
        }
    }
    // An item chosen elsewhere (opening the photo, deleting one) is brought to the centre.
    LaunchedEffect(items.getOrNull(selectedIndex)?.let(key), items.size) {
        if (selectedIndex >= 0 && !listState.isScrollInProgress) listState.scrollToItem(selectedIndex)
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(StripHeight), contentAlignment = Alignment.Center) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            // Half the row on each side, so the first and last items can reach the centre too.
            contentPadding = PaddingValues(horizontal = (maxWidth - StripTileSize) / 2),
            horizontalArrangement = Arrangement.spacedBy(StripGap),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .size(StripTileSize)
                        .clip(StripTileShape)
                        .background(StripTileColor)
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Button) {
                            scope.launch { listState.animateScrollToItem(index) }
                            onSelect(index)
                        }
                ) {
                    tile(index, item)
                }
            }
        }
    }
}

/** A strip tile's label, in its bottom-left corner. */
@Composable
internal fun BoxScope.StripLabel(text: String, color: Color = SonyText) {
    Text(
        text = text,
        color = color,
        style = TextStyle(fontFamily = CondensedBold, fontSize = 9.5.sp, lineHeight = 10.5.sp),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(3.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    )
}

/** The viewfinder thumbnail's edge line, on a strip tile's corners. */
@Composable
internal fun BoxScope.StripRing(style: EdgeRingStyle, color: Color) {
    ThumbnailEdgeRing(style, color, Modifier.matchParentSize(), corner = StripTileCorner)
}

private val StripTileSize = 44.dp
private val StripTileCorner = 8.dp
private val StripTileShape = RoundedCornerShape(StripTileCorner)
private val StripTileColor = Color(0xFF333333)
private val StripGap = 8.dp
/** A strip's height: its tiles with 6 dp above and below. */
private val StripHeight = 56.dp

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

/**
 * The viewer's 40 dp grey pill button over the picture: an [icon], a [text], or both, with an optional
 * [trailing] icon. [description] names an icon-only pill for TalkBack.
 */
@Composable
internal fun ViewerPill(
    // Null draws the pill without taking touches.
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    trailing: ImageVector? = null,
    description: String? = null,
    fill: Color = com.hinnka.mycamera.ui.components.SonyDivider,
) {
    Row(
        modifier
            .height(40.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val tint = com.hinnka.mycamera.ui.components.SonyText
        if (icon != null) androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        if (text != null) Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        if (trailing != null) androidx.compose.material3.Icon(trailing, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
