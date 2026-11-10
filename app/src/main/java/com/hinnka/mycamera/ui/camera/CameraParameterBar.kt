package com.hinnka.mycamera.ui.camera

import android.hardware.camera2.CameraMetadata
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraState
import com.hinnka.mycamera.camera.CameraUtils
import com.hinnka.mycamera.video.CaptureMode
import kotlin.math.roundToLong


@Composable
fun CameraParameterBar(
    state: CameraState,
    selectedParameter: CameraParameter?,
    onParameterClick: (CameraParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cameraParameterItems(state).forEach { item ->
            ParameterItem(
                item = item,
                isSelected = selectedParameter == item.parameter,
                onClick = { onParameterClick(item.parameter) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

internal data class CameraParameterItem(
    val parameter: CameraParameter,
    val label: String,
    val value: String?,
    val isEnabled: Boolean = true,
    val isWarning: Boolean = false,
    /** Shown when a disabled item is tapped, so the tap is never silent. */
    @StringRes val disabledMessage: Int? = null,
    /** Set by hand rather than by the camera: the value always shows, with an amber dot. */
    val isManual: Boolean = false,
)

@Composable
internal fun cameraParameterItems(state: CameraState): List<CameraParameterItem> {
    // Match the mode switcher: PHOTO capture also backs the RAW professional mode.
    val showValues = state.captureMode == CaptureMode.VIDEO ||
        (state.useRaw && state.isRawSupported)
    val evManual = state.exposureCompensation != 0
    val shutterManual = !state.isShutterSpeedAuto
    val isoManual = !state.isIsoAuto
    val focusManual = !state.isAutoFocus
    val wbManual = state.awbMode != CameraMetadata.CONTROL_AWB_MODE_AUTO
    return listOf(
        CameraParameterItem(
            CameraParameter.EXPOSURE_COMPENSATION,
            stringResource(R.string.camera_parameter_exposure),
            if (showValues || evManual) CameraUtils.formatExposureCompensation(
                state.exposureCompensation, state.getExposureCompensationStep()
            ) else null,
            isEnabled = state.isAutoExposure,
            isManual = evManual
        ),
        CameraParameterItem(
            CameraParameter.SHUTTER_SPEED,
            stringResource(R.string.camera_parameter_shutter),
            if (showValues || shutterManual) formatShutterSpeedValue(state.shutterSpeed) else null,
            isEnabled = state.isManualSensorSupported,
            isWarning = showValues && state.isPreviewExposureLimited(),
            disabledMessage = R.string.manual_sensor_unavailable,
            isManual = shutterManual
        ),
        CameraParameterItem(
            CameraParameter.ISO,
            stringResource(R.string.camera_parameter_iso),
            if (showValues || isoManual) state.iso.toString() else null,
            isEnabled = state.isManualSensorSupported,
            disabledMessage = R.string.manual_sensor_unavailable,
            isManual = isoManual
        ),
        CameraParameterItem(
            CameraParameter.FOCUS,
            stringResource(R.string.camera_parameter_focus),
            if (showValues || focusManual) formatFocusDistance(state.focusDistance) else null,
            isEnabled = state.minimumFocusDistance > 0f,
            isManual = focusManual
        ),
        CameraParameterItem(
            CameraParameter.WHITE_BALANCE,
            stringResource(R.string.camera_parameter_white_balance),
            if (showValues || wbManual) formatWhiteBalanceValue(state) else null,
            isEnabled = state.canAdjustWhiteBalance || state.actualAwbTemperature != null,
            isManual = wbManual
        )
    )
}

@Composable
internal fun ParameterItem(
    item: CameraParameterItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    val titleOnly = item.value == null
    val context = LocalContext.current
    val valueColor by animateColorAsState(
        targetValue = when {
            item.isWarning -> Color(0xFFFF6666)
            !item.isEnabled -> Color.White.copy(alpha = 0.38f)
            isSelected -> AccentColor
            else -> Color.White.copy(alpha = 0.95f)
        },
        label = "parameterValueColor"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !item.isEnabled -> Color.White.copy(alpha = 0.3f)
            isSelected -> AccentColor.copy(alpha = if (titleOnly) 1f else 0.8f)
            titleOnly -> Color.White.copy(alpha = 0.9f)
            else -> Color.White.copy(alpha = 0.6f)
        },
        label = "parameterLabelColor"
    )
    Box(
        modifier = modifier
            .then(
                if (vertical) Modifier.size(48.dp, 76.dp)
                else Modifier.height(CameraControlsLayoutDefaults.ParameterBarHeight)
            )
            .then(if (titleOnly) Modifier.clip(RoundedCornerShape(8.dp)) else Modifier)
            .semantics { selected = isSelected }
            .clickable(enabled = item.isEnabled || item.disabledMessage != null, role = Role.Button) {
                if (item.isEnabled) onClick()
                else item.disabledMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (vertical) {
                Modifier.requiredSize(76.dp, 48.dp).rotate(90f)
            } else {
                Modifier.fillMaxSize()
            },
            contentAlignment = Alignment.Center
        ) {
            if (item.value == null) {
                PhotoParameterTitle(
                    label = item.label,
                    color = labelColor,
                    isSelected = isSelected,
                    isEnabled = item.isEnabled
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.value,
                            color = valueColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(shadow = ViewfinderTextShadow),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (item.isManual) ManualDot(Modifier.padding(start = 4.dp))
                    }
                    Text(
                        text = item.label,
                        color = labelColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = ViewfinderTextShadow),
                        maxLines = 1
                    )
                }
            }
            if (isSelected && !titleOnly) {
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(16.dp, 2.dp)
                        .clip(CircleShape)
                        .background(AccentColor)
                )
            }
        }
    }
}

@Composable
private fun PhotoParameterTitle(
    label: String,
    color: Color,
    isSelected: Boolean,
    isEnabled: Boolean
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.Black.copy(alpha = 0.12f)
            isSelected -> AccentColor.copy(alpha = 0.16f)
            else -> Color.Black.copy(alpha = 0.28f)
        },
        label = "photoParameterBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.White.copy(alpha = 0.05f)
            isSelected -> AccentColor.copy(alpha = 0.6f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        label = "photoParameterBorder"
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .height(34.dp)
            .background(backgroundColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = ViewfinderTextShadow),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal fun formatShutterSpeedValue(value: Long): String {
    return when {
        value <= 0L -> "0"
        value < 1_000_000_000L -> "1/${(1_000_000_000.0 / value).roundToLong()}"
        else -> CameraUtils.formatShutterSpeed(value)
    }
}

internal fun formatFocusDistance(value: Float): String {
    return if (value <= 0.01f) "∞"
    else {
        val meters = 1.0f / value
        if (meters >= 1.0f) String.format("%.1fm", meters)
        else String.format("%dcm", (meters * 100).toInt())
    }
}

@Composable
private fun formatWhiteBalanceValue(state: CameraState): String {
    val measuredValue = state.actualAwbTemperature?.let { "${it}K" }
    return when (state.awbMode) {
        CameraMetadata.CONTROL_AWB_MODE_OFF -> "${state.awbTemperature}K"
        CameraMetadata.CONTROL_AWB_MODE_AUTO -> measuredValue ?: stringResource(R.string.camera_focus_auto)
        else -> measuredValue ?: stringResource(R.string.unknown)
    }
}

/** Marks a value set by hand. */
@Composable
private fun ManualDot(modifier: Modifier = Modifier) {
    Box(modifier.size(5.dp).background(ModeAmber, CircleShape))
}

/**
 * The values set by hand while the manual panel is closed: plain numbers with an amber dot, centred on the
 * picture's bottom edge like the active-setting marks on its top edge. A tap opens the panel at that value.
 */
@Composable
internal fun ManualValuesLine(
    state: CameraState,
    onValueClick: (CameraParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = cameraParameterItems(state).filter { it.isManual && it.value != null }
    if (items.isEmpty()) return
    val textStyle = TextStyle(shadow = ViewfinderTextShadow, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Color.White)
    Row(
        modifier = modifier.height(TopEdgeLineHeight),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .height(TopEdgeLineHeight)
                    .clickable(role = Role.Button) { onValueClick(item.parameter) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ISO alone is a bare number, so it keeps its name; the others read by their unit.
                val text = if (item.parameter == CameraParameter.ISO) "${item.label} ${item.value}" else item.value!!
                Text(text, style = textStyle, maxLines = 1, softWrap = false)
                ManualDot(Modifier.padding(start = 4.dp))
            }
        }
    }
}
