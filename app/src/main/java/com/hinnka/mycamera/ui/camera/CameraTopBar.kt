package com.hinnka.mycamera.ui.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.hinnka.mycamera.ui.components.HistogramView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.R
import com.hinnka.mycamera.video.VideoConfig
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.video.VideoResolutionPreset
import com.hinnka.mycamera.video.VideoStabilizationMode
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.camera.CameraHeat

@Composable
internal fun videoStabilizationModeLabel(mode: VideoStabilizationMode): String {
    return when (mode) {
        VideoStabilizationMode.OFF ->
            stringResource(R.string.settings_video_stabilization_mode_off)
        VideoStabilizationMode.EIS ->
            stringResource(R.string.settings_video_stabilization_mode_eis)
        VideoStabilizationMode.OIS ->
            stringResource(R.string.settings_video_stabilization_mode_ois)
        VideoStabilizationMode.ENHANCED ->
            stringResource(R.string.settings_video_stabilization_mode_enhanced)
    }
}

private val HeatWarningColor = Color(0xFFFF8A3D)
private val HeatStopColor = Color(0xFFFF5449)

/** Thermometer drawn in two colours: the outline and the reading inside it. */
@Composable
internal fun HeatThermometer(outlineColor: Color, readingColor: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Icon(AppIcons.ThermometerOutline, contentDescription = null, tint = outlineColor, modifier = Modifier.matchParentSize())
        Icon(AppIcons.ThermometerReading, contentDescription = null, tint = readingColor, modifier = Modifier.matchParentSize())
    }
}

/**
 * Heat now, a bar filling from the warning point to the stop point, and the stop limit.
 * The bar is [barWidth] wide and centred, so it lines up under the recording timer above it.
 */
@Composable
private fun HeatGauge(gauge: CameraHeat.Gauge?, barWidth: Dp, modifier: Modifier = Modifier) {
    val color = if ((gauge?.fraction ?: 0f) >= 2f / 3f) HeatStopColor else HeatWarningColor
    val textStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val description = gauge?.let {
        stringResource(R.string.video_heat_gauge_description, stringResource(it.source.label), it.nowC, it.limitC)
    } ?: stringResource(R.string.video_heat_warning_no_temp)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (gauge == null) {
            HeatThermometer(outlineColor = Color.White, readingColor = color, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.video_heat_warning_no_temp), color = color, style = textStyle)
            return@Row
        }
        // Equal-width sides keep the bar centred under the timer.
        Row(
            modifier = Modifier.width(HeatGaugeSideWidth),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeatThermometer(outlineColor = Color.White, readingColor = color, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.video_heat_degrees, gauge.nowC), color = color, style = textStyle)
        }
        Box(
            Modifier
                .width(barWidth)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(gauge.fraction.coerceIn(0.06f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Row(
            modifier = Modifier.width(HeatGaugeSideWidth),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Text(
                stringResource(R.string.video_heat_degrees, gauge.limitC),
                color = Color.White.copy(alpha = 0.8f),
                style = textStyle.copy(fontWeight = FontWeight.Normal)
            )
        }
    }
}

private val HeatGaugeSideWidth = 80.dp

/** The recording time, then either the heat gauge or the video format, centred under each other. */
@Composable
internal fun RecordingHud(
    elapsedMs: Long,
    isHot: Boolean,
    heatGauge: CameraHeat.Gauge?,
    videoConfig: VideoConfig,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recBlink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recDotAlpha"
    )
    val density = LocalDensity.current
    var timerWidth by remember { mutableStateOf(0.dp) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.onSizeChanged { timerWidth = with(density) { it.width.toDp() } },
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            border = when {
                !isHot -> BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                (heatGauge?.fraction ?: 0f) >= 2f / 3f -> BorderStroke(1.5.dp, HeatStopColor)
                else -> BorderStroke(1.5.dp, HeatWarningColor)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = dotAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRecordingDuration(elapsedMs),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        if (isHot) {
            HeatGauge(heatGauge, barWidth = timerWidth, modifier = Modifier.padding(top = 4.dp))
        } else Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (videoConfig.resolution == VideoResolutionPreset.MAX) {
                    stringResource(R.string.video_resolution_max)
                } else {
                    videoConfig.resolution.displayName
                },
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
            Text(
                text = "${videoConfig.fps.fps} FPS",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            if (videoConfig.logProfile != VideoLogProfile.OFF) {
                Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                Text(
                    text = "LOG",
                    color = AccentColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatRecordingDuration(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

/** One active-setting mark on the top edge: a 20 dp line icon, with a short value after it if any. */
internal data class TopEdgeMark(val icon: ImageVector?, val text: String? = null)

/**
 * The top edge of the picture while everything is closed: marks for settings that change how a shot is
 * taken and differ from the default, then the histogram. White with a soft shadow; it never turns.
 */
@Composable
internal fun TopEdgeLine(
    marks: List<TopEdgeMark>,
    histogram: IntArray?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = TextStyle(shadow = ViewfinderTextShadow, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Color.White)
    Row(
        modifier = modifier
            .height(TopEdgeLineHeight)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        marks.forEach { mark ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                mark.icon?.let { icon ->
                    Box {
                        // A dark copy one dp lower keeps the thin white line readable on a bright picture.
                        Icon(icon, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(y = 1.dp).size(20.dp))
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                mark.text?.let { Text(it, style = textStyle, maxLines = 1) }
            }
        }
        if (histogram != null) {
            HistogramView(histogram = histogram, modifier = Modifier.size(width = 64.dp, height = 22.dp))
        }
    }
}

// Room kept free on the picture's top edge for the line of active-setting marks.
internal val TopEdgeLineHeight = 38.dp
