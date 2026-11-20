package com.hinnka.mycamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.hinnka.mycamera.ui.components.AppModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.utils.VibrationHelper

/** True inside the Settings screen: the shared rows take Settings' black look and open choices in sheets. */
val LocalSettingsLook = staticCompositionLocalOf { false }

/** The row Settings was opened at: [center] scrolls the row (bounds in root) to the middle, [done] clears the request. */
internal class SettingsFlashRequest(
    val title: String,
    val center: suspend (Rect) -> Unit,
    val done: () -> Unit,
)

internal val LocalSettingsFlash = compositionLocalOf<SettingsFlashRequest?> { null }

/** When this row is the one asked for, scrolls it to the middle and flashes it #393939, fading over 1.6 s. */
@Composable
internal fun Modifier.settingsFlash(title: String): Modifier {
    val request = LocalSettingsFlash.current?.takeIf { it.title == title } ?: return this
    val flash = remember { Animatable(0f) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(request) {
        try {
            delay(300) // after the tab switch and the page's scroll to the top
            // Unclipped bounds: a row below the visible part of the page must still be found.
            coordinates?.takeIf { it.isAttached }?.let { request.center(Rect(it.positionInRoot(), it.size.toSize())) }
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1600))
        } finally {
            request.done()
        }
    }
    return this
        .onGloballyPositioned { coordinates = it }
        .drawBehind { drawRect(SettingsRaised.copy(alpha = flash.value)) }
}

internal val SettingsText = Color(0xFFE0E0E0)
internal val SettingsDim = Color(0xFF9E9E9E)
internal val SettingsQuiet = Color(0xFF999999)
internal val SettingsLine = Color(0xFF222222)
internal val SettingsRaised = Color(0xFF393939)

private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

@Composable
internal fun rememberSettingsTick(): () -> Unit {
    val context = LocalContext.current
    val helper = remember(context) { VibrationHelper(context) }
    return { helper.vibrate(VibrationHelper.LIGHT_TICK_MS) }
}

@Composable
internal fun SettingsRowDivider() = HorizontalDivider(thickness = 1.dp, color = SettingsLine)

@Composable
internal fun SettingsSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color.Black,
        checkedTrackColor = SettingsText,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = SettingsQuiet,
        uncheckedTrackColor = SettingsRaised,
        uncheckedBorderColor = Color.Transparent,
        disabledCheckedTrackColor = SettingsText.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = SettingsRaised.copy(alpha = 0.38f),
        disabledUncheckedBorderColor = Color.Transparent,
    )
)

/** A Settings row: name, optional second line, and [trailing] at the right end. */
@Composable
internal fun SettingsRowFrame(
    title: String,
    secondLine: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    expandableSecondLine: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    // A cut-off second line on a row with no sheet opens in full when tapped, and folds again.
    var expanded by remember { mutableStateOf(false) }
    var cutOff by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .settingsFlash(title)
            .heightIn(min = if (secondLine == null) 56.dp else 64.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SettingsText.copy(alpha = if (enabled) 1f else 0.38f), fontSize = 16.sp)
            if (secondLine != null) {
                Text(
                    secondLine,
                    color = SettingsDim.copy(alpha = if (enabled) 1f else 0.5f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) cutOff = it.hasVisualOverflow },
                    modifier = if (expandableSecondLine && (cutOff || expanded)) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded }
                    } else Modifier,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

/** A row whose right end shows its value; tapping it opens a sheet. */
@Composable
internal fun SettingsValueRow(
    title: String,
    secondLine: String?,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxValue = maxWidth * 0.45f
        SettingsRowFrame(title, secondLine, enabled, onClick) {
            Text(
                value,
                color = SettingsDim.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxValue),
            )
        }
    }
}

@Composable
internal fun SettingsChevron(enabled: Boolean = true) = Icon(
    imageVector = AppIcons.ChevronRight,
    contentDescription = null,
    tint = SettingsQuiet.copy(alpha = if (enabled) 1f else 0.5f),
)

/**
 * Black bottom sheet with a hairline top edge and the setting's name. In a turned window (only the viewer and the
 * gallery turn) it is a full-height panel from the right edge instead, with the same look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    title: String,
    onDismiss: () -> Unit,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val lineFadePx = with(LocalDensity.current) { 24.dp.toPx() }
    val window = LocalWindowInfo.current.containerSize
    if (window.width > window.height) {
        SidePanel(onDismiss) {
            SheetBody(
                Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    // Taps on the panel never reach the scrim, which closes it.
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } }
                    .background(Color.Black, PanelShape)
                    .border(1.dp, Brush.horizontalGradient(listOf(SettingsRaised, Color.Transparent), endX = lineFadePx), PanelShape),
                title, description, content,
            )
        }
        return
    }
    // The app's sheet keeps the status bar hidden when the screen behind it hides it.
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Black,
        shape = SheetShape,
        dragHandle = null,
    ) {
        SheetBody(
            Modifier
                .fillMaxWidth()
                // On the content, not the sheet: the sheet's modifier reaches a full-screen layer.
                // The line follows the rounded top edge and fades out down the sides.
                .border(1.dp, Brush.verticalGradient(listOf(SettingsRaised, Color.Transparent), endY = lineFadePx), SheetShape),
            title, description, content,
        )
    }
}

/** A sheet's scrolling content: the name, an optional [description], then [content]. */
@Composable
private fun SheetBody(
    modifier: Modifier,
    title: String,
    description: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
    ) {
        Text(
            title,
            color = SettingsText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        )
        if (description != null) {
            Text(
                description,
                color = SettingsDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        content()
    }
}

/**
 * A modal panel sliding in from the right edge over the bottom sheet's scrim; a tap on the scrim or Back closes it.
 * Its window shows or hides the system bars as the screen behind it does, as the bottom sheet's does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SidePanel(onDismiss: () -> Unit, panel: @Composable () -> Unit) {
    val statusBarsVisible = WindowInsets.areStatusBarsVisible
    val navigationBarsVisible = WindowInsets.areNavigationBarsVisible
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = generateSequence(LocalView.current.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        DisposableEffect(window, statusBarsVisible, navigationBarsVisible) {
            window?.setDimAmount(0f)  // the scrim below dims instead
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }?.apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (statusBarsVisible) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
                if (navigationBarsVisible) show(WindowInsetsCompat.Type.navigationBars()) else hide(WindowInsetsCompat.Type.navigationBars())
            }
            onDispose { }
        }
        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BottomSheetDefaults.ScrimColor)
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss)
            )
            AnimatedVisibility(
                shown,
                Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(220, easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f))) { it },
            ) { panel() }
        }
    }
}

private val PanelShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)

/** Single choice: the chosen option is a light fill with black text. Picking one ticks and closes. */
@Composable
internal fun SettingsChoiceSheet(
    title: String,
    options: List<String>,
    chosen: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    description: String? = null,
) {
    val tick = rememberSettingsTick()
    SettingsSheet(title, onDismiss, description) {
        if (note != null) {
            Text(note, color = SettingsDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
        }
        options.forEachIndexed { i, option ->
            val on = i == chosen
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) SettingsText else Color.Transparent)
                    .clickable {
                        tick()
                        onPick(i)
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(option, color = if (on) Color.Black else SettingsText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A slider row: shows the value; a tap opens a sheet with the value at 20 sp over [slider].
 * A row with [toggleValue] (on/off plus a level) keeps its switch in the sheet beside the name.
 */
@Composable
fun SettingsSliderRow(
    title: String,
    description: String?,
    value: Float,
    valueText: String,
    rowEnabled: Boolean,
    toggleValue: Boolean?,
    onToggleChange: (Boolean) -> Unit,
    slider: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingsValueRow(title, description, valueText, rowEnabled) { open = true }
    if (!open) return
    SettingsSheet(title, onDismiss = { open = false }, description = description) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(valueText, color = SettingsText, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (toggleValue != null) SettingsSwitch(toggleValue, onToggleChange)
        }
        Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { slider() }
    }
}
