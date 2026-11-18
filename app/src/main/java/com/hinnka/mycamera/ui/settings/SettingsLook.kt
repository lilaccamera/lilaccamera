package com.hinnka.mycamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.utils.VibrationHelper

/** True inside the Settings screen: the shared rows take Settings' black look and open choices in sheets. */
val LocalSettingsLook = staticCompositionLocalOf { false }

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
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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

/** Black bottom sheet with a hairline top edge and the setting's name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Black,
        shape = SheetShape,
        dragHandle = null,
        modifier = Modifier.border(
            1.dp,
            // The line follows the rounded top edge and fades out down the sides.
            Brush.verticalGradient(0f to SettingsRaised, 0.04f to SettingsRaised, 0.08f to Color.Transparent),
            SheetShape,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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
            content()
        }
    }
}

/** Single choice: the chosen option is a light fill with black text. Picking one ticks and closes. */
@Composable
internal fun SettingsChoiceSheet(
    title: String,
    options: List<String>,
    chosen: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    val tick = rememberSettingsTick()
    SettingsSheet(title, onDismiss) {
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
    SettingsSheet(title, onDismiss = { open = false }) {
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
