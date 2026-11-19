package com.hinnka.mycamera.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.lut.LutInfo
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.camera.SheetDim
import com.hinnka.mycamera.ui.camera.ViewfinderTextShadow
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.theme.PremiumGold
import kotlin.math.max

private class StripLook(val key: String, val lut: LutInfo, val caption: String?, val sectionStart: Boolean)

/**
 * One row of look thumbnails with their names. The sections (Favorites, Built-in, the categories) scroll past
 * with a small caption over each section's first look; empty sections are left out.
 * A tap picks a look, a tap on the chosen look calls [onSelectedClick], a long press calls [onLongPress].
 * [compact] is the photo editor's form for its 46 dp control on the photo: 44 dp thumbnails without names or
 * captions, None first, small gaps between the sections, and a tile at the end for [onManage].
 */
@Composable
fun LookStrip(
    luts: List<LutInfo>,
    currentLutId: String?,
    thumbnail: Bitmap?,
    categoryOrder: List<String>,
    onSelect: (String) -> Unit,
    onSelectedClick: () -> Unit,
    onLongPress: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onManage: (() -> Unit)? = null,
) {
    val favoriteText = stringResource(R.string.favorite)
    val builtInText = stringResource(R.string.built_in)
    val uncategorizedText = stringResource(R.string.uncategorized)
    val looks = remember(luts, categoryOrder, favoriteText, builtInText, uncategorizedText, compact) {
        val none = luts.firstOrNull { it.id == "none" }.takeIf { compact }
        listOfNotNull(none?.let { StripLook("none", it, null, false) }) +
            lutCategoryTabs(luts, categoryOrder, favoriteText, builtInText, uncategorizedText).flatMap { tab ->
            val caption = when (tab) {
                LutCategoryTab.Favorite -> favoriteText
                LutCategoryTab.BuiltIn -> builtInText
                LutCategoryTab.Uncategorized -> uncategorizedText
                is LutCategoryTab.Category -> tab.name
            }
            luts.filter { tab.contains(it) && it !== none }.mapIndexed { i, lut ->
                StripLook("${tab.stableKey()}/${lut.id}", lut, caption.takeIf { i == 0 && !compact }, i == 0)
            }
        }
    }
    val listState = rememberLazyListState()
    // Opens with the chosen look in view (its first appearance, so a favourite shows among the favourites).
    LaunchedEffect(looks) {
        val index = looks.indexOfFirst { it.lut.id == currentLutId }
        if (index >= 2) listState.scrollToItem(max(0, index - 2))
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            // The row fades out over 14 dp at both edges.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 14.dp.toPx() / size.width
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        items(looks, key = { it.key }) { look ->
            val onClick = { if (look.lut.id == currentLutId) onSelectedClick() else onSelect(look.lut.id) }
            val onLongClick = onLongPress?.let { { it(look.lut.id) } }
            if (compact) {
                CompactLookTile(
                    lut = look.lut,
                    selected = look.lut.id == currentLutId,
                    thumbnail = thumbnail,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    // 4 dp between looks, 12 dp between sections (None, favourites, each category).
                    modifier = if (look.sectionStart && look !== looks.first()) Modifier.padding(start = 8.dp) else Modifier
                )
            } else {
                LookStripItem(
                    lut = look.lut,
                    caption = look.caption,
                    selected = look.lut.id == currentLutId,
                    thumbnail = thumbnail,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    // 8 dp between looks, 20 dp between sections.
                    modifier = if (look.sectionStart && look !== looks.first()) Modifier.padding(start = 12.dp) else Modifier
                )
            }
        }
        if (onManage != null && !compact) {
            item(key = "manage") { ManageTile(onManage) }
        } else if (onManage != null) {
            item(key = "manage") {
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
                        .combinedClickable(onClick = onManage),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.AdjustmentsLine, contentDescription = stringResource(R.string.editor_manage_looks), tint = SheetDim, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LookStripItem(
    lut: LutInfo,
    caption: String?,
    selected: Boolean,
    thumbnail: Bitmap?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shadow = TextStyle(shadow = ViewfinderTextShadow)
    val shape = RoundedCornerShape(12.dp)
    val name = lut.getName()
    Column(
        modifier = modifier
            .width(64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(20.dp)) {
            if (caption != null) {
                Text(
                    text = caption.uppercase(),
                    color = SonySecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                    softWrap = false,
                    style = shadow,
                    // A long caption runs on over the next looks; only a section's first look has one.
                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .then(if (thumbnail == null || lut.id == "none") Modifier.background(Color.Black.copy(alpha = 0.4f)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (lut.id == "none") {
                Icon(AppIcons.CircleOffLine, contentDescription = null, tint = SheetDim, modifier = Modifier.size(20.dp))
            } else if (thumbnail != null) {
                LutPreviewImage(lut.id, name, thumbnail, modifier = Modifier.fillMaxSize())
            }
            if (lut.isVip) {
                Text(
                    text = stringResource(R.string.billing_vip_tag),
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(PremiumGold, RoundedCornerShape(bottomStart = 4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            // The chosen look is a menu choice: a white ring, like the chosen chips, not the accent.
            if (selected) Box(Modifier.matchParentSize().border(2.5.dp, SonyText, shape))
        }
        Text(
            text = name,
            color = SonyText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = shadow,
            modifier = Modifier
                .padding(top = 6.dp)
                .requiredWidth(68.dp)
                .height(28.dp)
        )
    }
}

/** The strip's last tile, shaped like a look: opens the look, frame and preset managers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.padding(start = 12.dp).width(64.dp).combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(64.dp).border(1.5.dp, SonyTrack, shape), contentAlignment = Alignment.Center) {
            Icon(AppIcons.FoldersLine, contentDescription = null, tint = SonySecondary, modifier = Modifier.size(24.dp))
        }
        Text(
            text = stringResource(R.string.quick_manage),
            color = SonyText,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = ViewfinderTextShadow),
            modifier = Modifier.padding(top = 6.dp).requiredWidth(68.dp).height(28.dp)
        )
    }
}

/** A look as a bare 44 dp thumbnail, for the photo editor's control on the photo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactLookTile(
    lut: LutInfo,
    selected: Boolean,
    thumbnail: Bitmap?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .then(if (thumbnail == null || lut.id == "none") Modifier.background(Color.Black.copy(alpha = 0.4f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) ModeAmber else Color.White.copy(alpha = 0.3f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (lut.id == "none") {
            Icon(AppIcons.CircleOffLine, contentDescription = lut.getName(), tint = SheetDim, modifier = Modifier.size(18.dp))
        } else if (thumbnail != null) {
            LutPreviewImage(lut.id, lut.getName(), thumbnail, modifier = Modifier.fillMaxSize())
        }
        if (lut.isVip) {
            Text(
                text = stringResource(R.string.billing_vip_tag),
                color = Color.Black,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 7.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(PremiumGold, RoundedCornerShape(bottomStart = 4.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
    }
}
