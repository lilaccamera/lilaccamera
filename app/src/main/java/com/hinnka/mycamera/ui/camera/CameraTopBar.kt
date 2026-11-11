package com.hinnka.mycamera.ui.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.video.VideoConfig
import com.hinnka.mycamera.video.VideoRecordingPath
import android.os.StatFs
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
internal fun HeatGauge(gauge: CameraHeat.Gauge?, barWidth: Dp, modifier: Modifier = Modifier) {
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

private val RecordingDotColor = Color(0xFFE24B4A)

/**
 * The recording time in a pill at the centre of the top-edge line: a blinking red dot, or a pause mark with
 * the time dimmed. Outlined in the footer's 2 dp line, amber or red instead while the phone is hot.
 */
@Composable
internal fun RecordingPill(elapsedMs: Long, isPaused: Boolean, isHot: Boolean, heatGauge: CameraHeat.Gauge?) {
    val outline = when {
        !isHot -> CameraControlsLayoutDefaults.LineColor
        (heatGauge?.fraction ?: 0f) >= 2f / 3f -> HeatStopColor
        else -> HeatWarningColor
    }
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(CameraControlsLayoutDefaults.LineWidth, outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPaused) {
                Icon(AppIcons.PauseLine, null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                val dotAlpha by rememberInfiniteTransition(label = "recBlink").animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
                    label = "recDotAlpha"
                )
                Box(Modifier.size(8.dp).clip(CircleShape).background(RecordingDotColor.copy(alpha = dotAlpha)))
            }
            Text(
                text = formatRecordingDuration(elapsedMs),
                color = if (isPaused) Color.White.copy(alpha = 0.6f) else Color.White,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1
            )
        }
    }
}

/** The recording time still free on the storage, at the end of the top-edge line; red when nearly full. */
@Composable
internal fun SpaceLeftMark(secondsLeft: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            if (secondsLeft >= 60) "${secondsLeft / 60}m" else "${secondsLeft}s",
            style = TopEdgeTextStyle.copy(fontFeatureSettings = "tnum"),
            color = if (secondsLeft < 120) HeatStopColor else Color.White,
            maxLines = 1
        )
        Text(
            stringResource(R.string.video_space_left),
            style = TopEdgeTextStyle.copy(fontSize = 12.sp),
            color = Color(0xFFB4B2A9),
            maxLines = 1
        )
    }
}

/**
 * Seconds of video that still fit at the chosen bitrate, read every 5 s while recording.
 * Null when not recording, or for a folder chosen in the system picker, which has no simple free-space reading.
 */
@Composable
internal fun rememberRecordingSecondsLeft(recording: Boolean, config: VideoConfig): Long? {
    val context = LocalContext.current
    var secondsLeft by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(recording, config.recordingPath, config.bitrate) {
        secondsLeft = null
        if (!recording || config.recordingPath == VideoRecordingPath.EXTERNAL_TREE) return@LaunchedEffect
        val bitsPerSecond = config.bitrate.bitrateMbps * 1_000_000L + 192_000L  // video plus stereo audio
        while (true) {
            // The app's own external folder is on the same storage as DCIM.
            val freeBytes = withContext(Dispatchers.IO) {
                runCatching { context.getExternalFilesDir(null)?.let { StatFs(it.path).availableBytes } }.getOrNull()
            }
            secondsLeft = freeBytes?.let { it * 8 / bitsPerSecond }
            delay(5_000)
        }
    }
    return secondsLeft
}

private fun formatRecordingDuration(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = totalSeconds / 60L % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

/** A 2 dp line icon in white, with a dark copy one dp lower that keeps it readable on a bright picture. */
@Composable
private fun ShadowedLineIcon(icon: ImageVector, size: Dp) {
    Box {
        Icon(icon, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(y = 1.dp).size(size))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size))
    }
}

private val TopEdgeTextStyle = TextStyle(shadow = ViewfinderTextShadow, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Color.White)

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
    modifier: Modifier = Modifier,
    center: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val tapModifier = Modifier
        .height(TopEdgeLineHeight)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    val markRow: @Composable () -> Unit = {
        marks.forEach { mark ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                mark.icon?.let { ShadowedLineIcon(it, 20.dp) }
                mark.text?.let { Text(it, style = TopEdgeTextStyle, maxLines = 1) }
            }
        }
        if (histogram != null) {
            HistogramView(histogram = histogram, modifier = Modifier.size(width = 64.dp, height = 22.dp))
        }
    }
    if (center == null) {
        Row(
            modifier = modifier.then(tapModifier),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            markRow()
            trailing?.invoke()
        }
        return
    }
    // Equal sides keep the centre piece in the middle of the screen, whatever the marks beside it.
    Row(modifier = modifier.fillMaxWidth().then(tapModifier), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) { markRow() }
        Spacer(Modifier.width(20.dp))
        center()
        Spacer(Modifier.width(20.dp))
        Box(Modifier.weight(1f)) { trailing?.invoke() }
    }
}

// Room kept free on the picture's top edge for the line of active-setting marks.
internal val TopEdgeLineHeight = 38.dp
