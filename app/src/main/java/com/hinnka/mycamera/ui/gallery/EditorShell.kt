package com.hinnka.mycamera.ui.gallery

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.components.LilacDim
import com.hinnka.mycamera.ui.components.LilacIconTint
import com.hinnka.mycamera.ui.components.LilacLabel
import com.hinnka.mycamera.ui.components.LilacSecondary
import com.hinnka.mycamera.ui.components.LilacText
import com.hinnka.mycamera.ui.components.LilacTextStyle
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlin.math.min

// The photo editor's layout, in dp: edge icons over the photo's top, the photo, and a 160 dp block at the
// bottom with a value row and a path row. The one active control lies on the photo's bottom edge.
internal const val EDITOR_BLOCK_DP = 160f
internal const val EDITOR_EDGE_DP = 40f
internal const val EDITOR_CONTROL_DP = 46f

/**
 * The editor's sections, in workflow order; the two canvas tools sit at the end after a small gap, so hiding
 * them for a video moves nothing else. [keys] are the settings (by [com.hinnka.mycamera.viewmodel.GalleryViewModel.editChangedKeys])
 * whose change puts a dot on the section.
 */
internal enum class EditorSection(@StringRes val label: Int, val keys: Set<String> = emptySet(), val gap: Boolean = false) {
    Look(R.string.editor_section_look, setOf("look", "lutIntensity", "frame", "applyEffectsToVideo")),
    // Light, Color, Effects and Detail take their keys from their settings and groups (EditorAdjustments.kt).
    Light(R.string.editor_section_light),
    Color(R.string.editor_section_color),
    Effects(R.string.editor_section_effects),
    Detail(R.string.editor_section_detail),
    Portrait(R.string.editor_section_portrait, setOf("aperture", "bokeh", "focus", "depth", "depthOfField"), gap = true),
    Crop(R.string.editor_section_crop, setOf("crop", "cropRatio", "rotation", "straighten", "mirror"));

    val icon: ImageVector
        get() = when (this) {
            Look -> AppIcons.MovieLine
            Light -> AppIcons.SunLine
            Color -> AppIcons.PaletteLine
            Effects -> AppIcons.SparklesLine
            Detail -> AppIcons.FocusLine
            Portrait -> AppIcons.User
            Crop -> AppIcons.CropLine
        }
}

/** Where the photo goes, in dp. */
internal data class EditorRect(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Fits a photo of [aspect] (width / height) into a screen of [width] × [height] dp: above the block while
 * editing, and centred below the edge icons when it is short enough to leave them clear; the whole screen in
 * [full] view.
 */
internal fun editorPhotoRect(aspect: Float, width: Float, height: Float, full: Boolean): EditorRect {
    if (full) {
        val w = min(width, height * aspect)
        val h = w / aspect
        return EditorRect((width - w) / 2f, (height - h) / 2f, w, h)
    }
    val area = height - EDITOR_BLOCK_DP
    val w = min(width, area * aspect)
    val h = w / aspect
    val y = if (h <= area - EDITOR_EDGE_DP) EDITOR_EDGE_DP + (area - EDITOR_EDGE_DP - h) / 2f else (area - h) / 2f
    return EditorRect((width - w) / 2f, y, w, h)
}

/** An icon with a soft dark shadow, readable on any photo without a band behind it. */
@Composable
private fun ShadowedIcon(icon: ImageVector, description: String?, tint: Color, size: Float) {
    Box {
        Icon(
            icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.offset(y = 1.dp).size(size.dp)
        )
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size.dp))
    }
}

/**
 * The document actions on the photo's top edge: Cancel, Undo, Redo, a render spinner, Full view, Compare
 * (while held) and Save. They fade while a finger is on the photo or the ruler.
 */
@Composable
internal fun EditorEdgeIcons(
    busy: Boolean,
    rendering: Boolean,
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    fullView: Boolean,
    saving: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFullView: () -> Unit,
    onCompare: (Boolean) -> Unit,
    onSave: () -> Unit,
    hd: Boolean = false,
    onHd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fade by animateFloatAsState(if (busy) 0.12f else 1f, tween(200), label = "edgeIcons")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EDITOR_EDGE_DP.dp)
            .graphicsLayer { alpha = fade }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EdgeIcon(AppIcons.CloseLine, stringResource(R.string.cancel), onClick = onCancel)
        EdgeIcon(AppIcons.UndoLine, stringResource(R.string.editor_undo), dim = !undoAvailable, onClick = onUndo)
        EdgeIcon(AppIcons.RedoLine, stringResource(R.string.editor_redo), dim = !redoAvailable, onClick = onRedo)
        Spacer(Modifier.weight(1f))
        val spin by animateFloatAsState(if (rendering) 1f else 0f, tween(200), label = "spinner")
        Box(Modifier.width(24.dp).graphicsLayer { alpha = spin }, contentAlignment = Alignment.Center) {
            if (spin > 0f) {
                val turn = rememberInfiniteTransition(label = "spin")
                val angle by turn.animateFloat(
                    0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "spinAngle"
                )
                Icon(
                    AppIcons.LoaderLine, contentDescription = null, tint = ModeAmber,
                    modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = angle }
                )
            }
        }
        // HD preview: amber while the editor previews at full size.
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .clickable(role = Role.Switch, onClick = onHd),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "HD",
                color = if (hd) ModeAmber else LilacText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = LilacTextStyle
            )
        }
        EdgeIcon(
            if (fullView) AppIcons.MinimizeLine else AppIcons.MaximizeLine,
            stringResource(if (fullView) R.string.editor_exit_full_view else R.string.editor_full_view),
            onClick = onFullView
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onCompare(true)
                        waitForUpOrCancellation()
                        onCompare(false)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ShadowedIcon(AppIcons.ContrastLine, stringResource(R.string.editor_compare), Color.White, 21f)
        }
        Box(
            modifier = Modifier
                .width(60.dp)
                .fillMaxHeight()
                .clickable(enabled = !saving, role = Role.Button, onClick = onSave),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.save),
                color = ModeAmber.copy(alpha = if (saving) 0.4f else 1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                style = LilacTextStyle
            )
        }
    }
}

@Composable
private fun EdgeIcon(icon: ImageVector, description: String, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .graphicsLayer { alpha = if (dim) 0.35f else 1f }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        ShadowedIcon(icon, description, Color.White, 21f)
    }
}

/** The sections as a tab bar along the bottom; a dot marks a section with changes. */
@Composable
internal fun EditorSectionRow(
    sections: List<EditorSection>,
    selected: EditorSection,
    changed: (EditorSection) -> Boolean,
    onSelect: (EditorSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The row reaches the screen's bottom edge, with its icons and labels centred between the line above and
    // the edge.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .drawWithContent {
                drawContent()
                drawRect(Color(0xFF2C2C2A), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        sections.forEach { section ->
            val on = section == selected
            val color = if (on) ModeAmber else LilacSecondary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (section.gap) Modifier.padding(start = 8.dp) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab
                    ) { onSelect(section) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(section.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Text(stringResource(section.label), color = color, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
                }
                if (changed(section)) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(x = 14.5.dp, y = 7.dp)
                            .size(5.dp)
                            .background(ModeAmber, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * One setting in the value row: an icon that shows what it does, the value, and the name. A [wide] item holds
 * a name (a look, a frame) rather than a number. Widths never depend on the values, so changing a value never
 * moves the items beside it.
 */
internal data class EditorValue(
    val key: String,
    val name: String,
    val value: String,
    val icon: ImageVector,
    val changed: Boolean = false,
    val wide: Boolean = false,
    /** Drawn in place of [icon], following the value: [fraction] is its share of the range, −1 to 1. */
    val glyph: EditorGlyph? = null,
    val fraction: Float = 0f,
    /** A group: shown as "Name ›", and a tap goes one level deeper. */
    val group: Boolean = false,
    /** Shown faint: it still works, but has no effect right now (the blur's options while the blur is Off). */
    val dimmed: Boolean = false,
)

/** The settings of the current level, side by side; the row scrolls sideways with faded edges when they don't fit. */
@Composable
internal fun EditorValueRow(
    values: List<EditorValue>,
    selectedKey: String?,
    onClick: (EditorValue) -> Unit,
    modifier: Modifier = Modifier,
    onHold: (EditorValue, Boolean) -> Unit = { _, _ -> },
) {
    // When every item fits at its own width, the items share the row equally; otherwise each keeps its width
    // (80 dp for a number, 96 dp for a name) and the row scrolls.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val natural = values.sumOf { if (it.wide) 96 else 80 }
        val shared = if (natural <= maxWidth.value) maxWidth / values.size.coerceAtLeast(1) else null
        ValueRowItems(values, selectedKey, shared, onClick, onHold)
    }
}

@Composable
private fun ValueRowItems(
    values: List<EditorValue>,
    selectedKey: String?,
    shared: Dp?,
    onClick: (EditorValue) -> Unit,
    onHold: (EditorValue, Boolean) -> Unit,
) {
    val scroll = rememberScrollState()
    val canScroll = scroll.maxValue > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (canScroll) {
                    val edge = 16.dp.toPx()
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, edge),
                        size = androidx.compose.ui.geometry.Size(edge, size.height),
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - edge, size.width),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width - edge, 0f),
                        size = androidx.compose.ui.geometry.Size(edge, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
            .horizontalScroll(scroll)
    ) {
        values.forEach { v ->
            EditorValueItem(v, v.key == selectedKey, shared ?: if (v.wide) 96.dp else 80.dp, { onClick(v) }, { onHold(v, it) })
        }
    }
}

@Composable
private fun EditorValueItem(
    item: EditorValue,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onHold: (Boolean) -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold by rememberUpdatedState(onHold)
    Column(
        modifier = Modifier
            .width(width)
            .height(84.dp)
            .graphicsLayer { alpha = if (item.dimmed) 0.45f else 1f }
            // A tap, or a long press that lasts while the finger stays down. A drag belongs to the row's sideways
            // scroll, which takes the pointer and ends either.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var finished = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation().also { finished = true }
                    }
                    if (finished) {
                        if (up != null) currentOnClick()
                        return@awaitEachGesture
                    }
                    if (currentEvent.changes.any { it.isConsumed }) return@awaitEachGesture
                    currentOnHold(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        currentOnHold(false)
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick { currentOnClick(); true }
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        val iconColor = if (selected) ModeAmber else LilacIconTint
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            val glyph = item.glyph
            if (glyph != null) {
                val vector = remember(glyph, item.fraction, iconColor) { editorGlyph(glyph, item.fraction, iconColor) }
                Icon(vector, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            } else {
                Icon(item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
        }
        if (item.group) {
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = LilacText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Icon(AppIcons.ChevronRightLine, contentDescription = null, tint = LilacDim, modifier = Modifier.size(16.dp))
                if (item.changed) {
                    Box(Modifier.padding(start = 2.dp).size(5.dp).background(ModeAmber, CircleShape))
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            // A long name steps down in size, wrapping onto two lines from 13 sp, before it is cut short. Two lines
            // spill a few dp into the gaps around the 24 dp value line, so the icon and the name stay where they
            // are. The room for the change dot is kept free either way, so the size doesn't jump when it appears.
            Row(
                Modifier
                    .height(24.dp)
                    .wrapContentHeight(unbounded = true)
                    .heightIn(max = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = item.value,
                    style = TextStyle(
                        color = if (selected) ModeAmber else LilacText,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 1.1.em
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 17.sp, stepSize = 1.sp),
                    modifier = Modifier.widthIn(max = width - 17.dp)
                )
                if (item.changed) {
                    Box(Modifier.padding(start = 4.dp).size(5.dp).background(ModeAmber, CircleShape))
                }
            }
            Text(
                text = item.name,
                color = if (selected) Color.White else LilacSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,  // the theme's 24 sp line would cut the name's bottom off
                maxLines = 1,
                modifier = Modifier.height(14.dp)
            )
        }
    }
}

/** A short line in the control layer, for a setting whose control has nothing to show. */
@Composable
internal fun EditorControlHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(EDITOR_CONTROL_DP.dp).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = LilacDim, fontSize = 12.sp, textAlign = TextAlign.Center, style = LilacTextStyle)
    }
}

/** "Discard changes?", drawn inside the editor's window so the hidden system bars stay hidden. */
@Composable
internal fun EditorDiscardDialog(
    message: String,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    title: String = stringResource(R.string.frame_editor_discard_title),
    keepText: String = stringResource(R.string.editor_keep_editing),
    actionText: String = stringResource(R.string.discard),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onKeep)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 36.dp)
                .fillMaxWidth()
                .background(Color(0xFF1C1C1B), RoundedCornerShape(18.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp)
        ) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(message, color = LilacSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                DialogButton(keepText, LilacText, onKeep)
                DialogButton(actionText, ModeAmber, onDiscard)
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .background(Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The path row inside a group: ← and the group's name (with its parent two levels down), the group's own
 * picker, and Reset when the group has one. It takes the sections row's place, 70 dp to the bottom edge.
 */
@Composable
internal fun EditorGroupRow(
    title: String,
    parentTitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    picker: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .drawWithContent {
                drawContent()
                drawRect(Color.White.copy(alpha = 0.08f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onBack
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(AppIcons.ArrowLeftLine, contentDescription = stringResource(R.string.editor_back), tint = LilacText, modifier = Modifier.size(20.dp))
            if (parentTitle != null) {
                Text("$parentTitle ›", color = LilacDim, fontSize = 15.sp, maxLines = 1)
            }
            Text(title, color = LilacText, fontSize = 15.sp, maxLines = 1)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            picker?.invoke(this)
        }
        if (onReset != null) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onReset)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(AppIcons.UndoLine, contentDescription = null, tint = LilacDim, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.editor_reset), color = LilacDim, fontSize = 13.sp)
            }
        }
    }
}

/** The explanation of one setting, on request: a card floating above the control layer. */
@Composable
internal fun EditorExplanationCard(name: String, text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append(name) }
            append(" · ")
            append(text)
        },
        color = LilacLabel,
        fontSize = 12.5.sp,
        lineHeight = 1.45.em,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xDB141413), shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** Portrait's focus point: the camera's thin 42 dp ring, dashed while the blur is Off. */
@Composable
internal fun PortraitFocusRing(center: androidx.compose.ui.geometry.Offset, off: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val radius = 21.dp.toPx()
        val dash = if (off) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
        val alpha = if (off) 0.6f else 1f
        drawCircle(
            Color.Black.copy(alpha = 0.45f * alpha), radius, center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), pathEffect = dash)
        )
        drawCircle(
            Color.White.copy(alpha = alpha), radius, center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.25.dp.toPx(), pathEffect = dash)
        )
    }
}

/** A short note on the photo's bottom edge with one action chip, for a control that can't act yet. */
@Composable
internal fun PortraitNote(text: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(EDITOR_CONTROL_DP.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = LilacText, fontSize = 12.5.sp, style = LilacTextStyle, maxLines = 1)
        com.hinnka.mycamera.ui.components.LilacChip(
            text = action,
            selected = true,
            onClick = onAction,
            height = 36.dp,
            fill = Color(0xB8141413),
            fontSize = 13.sp,
            horizontalPadding = 13.dp,
        )
    }
}

/**
 * The editor's small UI state that isn't saved, in one object, so the screen's main lambda captures one value for
 * all of it: Android rejects a method that takes more than 255.
 */
@androidx.compose.runtime.Stable
internal class EditorUiState {
    // Taps on the value row: the last one, to tell a double tap, and a second tap's card waiting out that window.
    var lastTapKey by mutableStateOf<String?>(null)
    var lastTapAt by mutableLongStateOf(0L)
    var pendingCard by mutableStateOf<kotlinx.coroutines.Job?>(null)
    // The recipe as it was when a long press on one setting began, to put back when it ends.
    var heldRecipe by mutableStateOf<com.hinnka.mycamera.model.ColorRecipeParams?>(null)
    // The Crop value held down: the photo shows without that one change, and without the frame.
    var heldCrop by mutableStateOf<String?>(null)
    var pendingPhotoTap by mutableStateOf<kotlinx.coroutines.Job?>(null)
    // Curve: the selected point, and the points while a finger drags one.
    var curveSelected by mutableStateOf<Int?>(null)
    var curveDrag by mutableStateOf<List<Pair<Float, Float>>?>(null)
    // Light › RAW › Profile: the imported DCP a long press asked to delete.
    var deleteDcpAsk by mutableStateOf<com.hinnka.mycamera.raw.DcpInfo?>(null)
    // RAW: the edit state developed at half size, the photo's base while editing; Save develops it in full.
    var rawPreviewBase by mutableStateOf<android.graphics.Bitmap?>(null)
    var previewDeveloping by mutableStateOf(false)
    var previewAgain by mutableStateOf(false)
    // Save is developing the RAW in full before the edit saves.
    var savingRaw by mutableStateOf(false)
    // RAW: this session's small renders by edit state, the state last developed (null: as opened), and whether an
    // earlier look is on screen.
    val rawLooks = mutableListOf<Pair<Map<String, Any?>, android.graphics.Bitmap>>()
    var developedRaw by mutableStateOf<Map<String, Any?>?>(null)
    var rawLookShown by mutableStateOf(false)
    // Develop timings, for the status line's estimates: whole develops and stages, per kind and engine.
    val rawDevelopMs = mutableStateMapOf<String, Long>()
    val rawStageMs = mutableStateMapOf<String, Long>()
    var developStartedAt by mutableLongStateOf(0L)
    var stageStartedAt by mutableLongStateOf(0L)
    var lastStage by mutableStateOf<com.hinnka.mycamera.raw.RawDevelopStage?>(null)
    var stageKind by mutableStateOf("")
    // The edit state the preview and Crop's picture show, and whether Crop's view was on screen last: a mode
    // switch keeps the previous view until the new one's picture shows the current edit.
    var previewFor by mutableStateOf<Any?>(null)
    var cropFor by mutableStateOf<Any?>(null)
    var lastShownCrop by mutableStateOf(false)
}
