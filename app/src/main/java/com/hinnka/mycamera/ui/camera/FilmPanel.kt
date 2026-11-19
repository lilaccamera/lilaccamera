package com.hinnka.mycamera.ui.camera

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.frame.FrameInfo
import com.hinnka.mycamera.lut.LutInfo
import com.hinnka.mycamera.model.CameraPreset
import com.hinnka.mycamera.model.RecipeParam
import com.hinnka.mycamera.ui.components.LookStrip
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.settings.presetDisplayName
import com.hinnka.mycamera.video.VideoLogLutMode
import kotlin.math.roundToInt

/**
 * The FILM panel: the look strip floating on the picture above a sheet built like quick settings, with
 * Intensity, Color recipe, Frame and Preset in one view. With a Log profile in Video it offers the Log looks
 * and the Log LUT choice instead of frames and presets.
 */
@Composable
internal fun FilmPanel(
    visible: Boolean,
    luts: List<LutInfo>,
    currentLutId: String?,
    thumbnail: Bitmap?,
    categoryOrder: List<String>,
    onLutSelected: (String) -> Unit,
    onEditClick: () -> Unit,
    onLutManageClick: (String?) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    frames: List<FrameInfo>,
    currentFrameId: String?,
    onFrameSelected: (String?) -> Unit,
    onFrameManageClick: () -> Unit,
    presets: List<CameraPreset>,
    activePresetId: String?,
    activePresetModified: Boolean,
    onPresetSelected: (CameraPreset) -> Unit,
    onNewPresetClick: () -> Unit,
    onResetPresetClick: () -> Unit,
    onUpdatePresetClick: () -> Unit,
    onPresetManageClick: () -> Unit,
    // Video with a Log profile
    isVideoLog: Boolean,
    logLutMode: VideoLogLutMode,
    logLutModeEnabled: Boolean,
    onLogLutModeChange: (VideoLogLutMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContentManagement by rememberSaveable { mutableStateOf(false) }
    var explained by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible, isVideoLog) {
        showContentManagement = false
        explained = null
    }
    val riseOffset = with(LocalDensity.current) { 24.dp.roundToPx() }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(260, easing = SheetEasing)) { riseOffset } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(200)) { riseOffset } + fadeOut(tween(160)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Drag down closes; the handler also keeps touches on empty parts of the sheet from reaching the footer.
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = { if (dragged > 50.dp.toPx()) onDismiss() },
                        onVerticalDrag = { change, amount ->
                            dragged += amount
                            change.consume()
                        }
                    )
                }
        ) {
            LookStrip(
                luts = luts,
                currentLutId = currentLutId,
                thumbnail = thumbnail,
                categoryOrder = categoryOrder,
                onSelect = onLutSelected,
                onSelectedClick = onEditClick,
                onLongPress = onLutManageClick,
                onManage = { showContentManagement = !showContentManagement }
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // shortcut: plain black like quick settings, no blur (Compose can't blur the GL preview behind it).
                    .background(SheetPanel, SheetShape)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                Box(Modifier.padding(horizontal = 10.dp)) {
                    ContentManagementOptionsPanel(
                        visible = showContentManagement,
                        onFilterManageClick = { onLutManageClick(null) },
                        onFrameManageClick = onFrameManageClick,
                        onPresetManageClick = onPresetManageClick
                    )
                }
                if (showContentManagement) Spacer(Modifier.height(8.dp))

                val rows = QuickRows(
                    explained = explained,
                    onExplain = { key -> explained = if (explained == key) null else key },
                    onOpenSettingsAt = {}
                )
                QuickGroup(stringResource(R.string.quick_group_look), GroupMagenta, divider = false) {
                    // None has no strength to set.
                    if (currentLutId != null && currentLutId != "none") {
                        val percent = (RecipeParam.LUT_INTENSITY.clamp(intensity) * 100).roundToInt()
                        rows.Setting(
                            key = "intensity",
                            label = stringResource(R.string.film_intensity),
                            explanation = stringResource(R.string.film_explain_intensity)
                        ) {
                            QuickSlider(
                                text = "$percent%",
                                fraction = percent / 100f,
                                onFraction = { onIntensityChange((it * 100).roundToInt() / 100f) },
                                onDoubleTap = { onIntensityChange(1f) }
                            )
                        }
                    }
                    rows.Setting(
                        key = "recipe",
                        label = stringResource(R.string.film_color_recipe),
                        explanation = stringResource(R.string.edit_sync_lut_recipe_description),
                        last = true
                    ) {
                        QuickLink(stringResource(R.string.edit), open = false, onClick = onEditClick)
                    }
                }

                if (isVideoLog) {
                    QuickGroup(stringResource(R.string.quick_group_log_video), GroupRed) {
                        rows.Setting(
                            key = "loglut",
                            label = stringResource(R.string.film_log_lut),
                            explanation = stringResource(
                                if (logLutMode == VideoLogLutMode.MONITOR_ONLY) R.string.video_log_lut_monitor_hint
                                else R.string.video_log_lut_bake_hint
                            ),
                            last = true
                        ) {
                            VideoLogLutMode.entries.forEach { mode ->
                                QuickChoice(
                                    text = stringResource(
                                        if (mode == VideoLogLutMode.MONITOR_ONLY) R.string.video_log_lut_monitor_only
                                        else R.string.video_log_lut_bake_in
                                    ),
                                    selected = logLutMode == mode,
                                    minWidth = 0.dp,
                                    // Fixed while recording.
                                    onClick = { if (logLutModeEnabled) onLogLutModeChange(mode) }
                                )
                            }
                        }
                    }
                } else {
                    QuickGroup(stringResource(R.string.frame), GroupGold) {
                        rows.Setting(
                            key = "frame",
                            label = stringResource(R.string.frame),
                            explanation = stringResource(R.string.settings_frame_description),
                            last = true
                        ) {
                            ChoiceScroller(selectedIndex = frames.indexOfFirst { it.id == currentFrameId } + 1) {
                                QuickChoice(stringResource(R.string.none), currentFrameId == null, 0.dp) { onFrameSelected(null) }
                                frames.forEach { frame ->
                                    QuickChoice(frame.name, frame.id == currentFrameId, 0.dp) { onFrameSelected(frame.id) }
                                }
                            }
                        }
                    }
                    QuickGroup(stringResource(R.string.film_preset), GroupGold) {
                        rows.Setting(
                            key = "preset",
                            label = stringResource(R.string.film_preset),
                            explanation = stringResource(R.string.film_explain_preset)
                        ) {
                            ChoiceScroller(selectedIndex = presets.indexOfFirst { it.id == activePresetId }) {
                                presets.forEach { preset ->
                                    val active = preset.id == activePresetId
                                    QuickChoice(
                                        text = presetDisplayName(preset) + if (active && activePresetModified) "*" else "",
                                        selected = active,
                                        minWidth = 0.dp
                                    ) { onPresetSelected(preset) }
                                }
                            }
                        }
                        val changed = activePresetModified && presets.any { it.id == activePresetId }
                        rows.Setting(
                            key = "current",
                            label = stringResource(R.string.film_current),
                            explanation = stringResource(
                                if (changed) R.string.film_explain_current_changed else R.string.film_explain_current_new
                            ),
                            last = true,
                            marked = changed
                        ) {
                            if (changed) {
                                ActionChip(AppIcons.UndoLine, stringResource(R.string.preset_reset), onResetPresetClick)
                                ActionChip(AppIcons.CheckLine, stringResource(R.string.preset_update), onUpdatePresetClick)
                                ActionChip(AppIcons.PlusLine, stringResource(R.string.preset_save_as), onNewPresetClick)
                            } else {
                                ActionChip(AppIcons.PlusLine, stringResource(R.string.preset_new), onNewPresetClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Choices that scroll sideways (frames, presets), at most [maxWidth] wide, fading in at the left edge. */
@Composable
internal fun ChoiceScroller(selectedIndex: Int, maxWidth: Dp = 252.dp, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Opens with the chosen one near the middle; choices are about 70 dp wide.
    LaunchedEffect(Unit) {
        if (selectedIndex > 1) scroll.scrollTo(with(density) { ((selectedIndex - 1) * 70).dp.roundToPx() })
    }
    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(0f to Color.Transparent, 12.dp.toPx() / size.width to Color.Black),
                    blendMode = BlendMode.DstIn
                )
            }
            .horizontalScroll(scroll)
            .padding(start = 12.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** A plain action: a small grey line icon and its name. */
@Composable
private fun ActionChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = SheetGrey, modifier = Modifier.size(15.dp))
        Text(text, color = SheetText, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}
