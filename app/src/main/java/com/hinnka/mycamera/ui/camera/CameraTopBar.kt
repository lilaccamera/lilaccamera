package com.hinnka.mycamera.ui.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.layout.Layout
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
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.hinnka.mycamera.video.VideoStabilizationMode
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.camera.CameraHeat

/** Short names for the everyday Stabilise item and its notes. */
internal fun videoStabilizationModeLabel(context: Context, mode: VideoStabilizationMode): String {
    return when (mode) {
        VideoStabilizationMode.OFF -> context.getString(R.string.settings_video_stabilization_mode_off)
        VideoStabilizationMode.SONY_STEADYSHOT -> context.getString(R.string.video_stabilise_steady)
        VideoStabilizationMode.SONY_ACTIVE -> context.getString(R.string.video_stabilise_active)
        VideoStabilizationMode.EIS, VideoStabilizationMode.OIS, VideoStabilizationMode.ENHANCED -> mode.displayName
    }
}

@Composable
internal fun videoStabilizationModeLabel(mode: VideoStabilizationMode): String =
    videoStabilizationModeLabel(LocalContext.current, mode)

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
internal fun ShadowedLineIcon(icon: ImageVector, size: Dp) {
    Box {
        Icon(icon, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(y = 1.dp).size(size))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size))
    }
}

private val TopEdgeTextStyle = TextStyle(shadow = ViewfinderTextShadow, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Color.White)

/** One active-setting mark on the top edge, drawn as the everyday row draws it; [color] null is the accent. */
internal data class TopEdgeMark(val badge: Badge, val color: Color? = null)

// Sony's camera writes values in icons with a bold condensed face.
private val CondensedBold = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))

/**
 * A setting as Sony's camera draws it: an [icon] with its value as a small subscript ([sub]), the value alone
 * as [text] (in an outlined box when [framed]), or an icon followed by a plain reading (both [icon] and [text]).
 */
internal data class Badge(
    val icon: ImageVector? = null,
    val sub: String? = null,
    val text: String? = null,
    val framed: Boolean = false
)

/** Draws a [Badge]: 24 dp icons in the everyday row, 22 dp on the top edge ([small]), where icons get a shadow. */
@Composable
internal fun BadgeView(badge: Badge, color: Color, small: Boolean = false) {
    val icon = badge.icon
    val text = badge.text
    if (icon != null && text != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            BadgeIcon(icon, if (small) 22.dp else 24.dp, color, small)
            Text(text, style = TopEdgeTextStyle.copy(color = color), maxLines = 1)
        }
        return
    }
    val tight = TextStyle(
        fontFamily = CondensedBold,
        shadow = ViewfinderTextShadow,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    )
    if (text != null) {
        val size = when {
            badge.framed -> if (small) 12.5.sp else 14.sp
            small -> 15.sp
            else -> 17.sp
        }
        Box(Modifier.height(if (small) 28.dp else 32.dp).widthIn(min = if (small) 24.dp else 32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = color,
                maxLines = 1,
                style = tight.copy(fontSize = size, letterSpacing = (-0.3).sp),
                modifier = if (badge.framed) {
                    Modifier
                        .border(1.5.dp, color, RoundedCornerShape(3.dp))
                        .padding(start = 3.dp, end = 3.dp, top = 2.dp, bottom = 1.dp)
                } else {
                    Modifier
                }
            )
        }
        return
    }
    // The subscript starts a little right of the icon's centre, on the badge's bottom edge, and may run past it.
    val subStart = if (small) 6.dp else 7.dp
    Layout(
        content = {
            if (icon != null) BadgeIcon(icon, if (small) 22.dp else 24.dp, color, small)
            badge.sub?.let {
                Text(it, color = color, maxLines = 1, style = tight.copy(fontSize = if (small) 10.5.sp else 11.sp, letterSpacing = (-0.2).sp))
            }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = maxOf((if (small) 24.dp else 32.dp).roundToPx(), placeables.firstOrNull()?.width ?: 0)
        val height = (if (small) 28.dp else 32.dp).roundToPx()
        layout(width, height) {
            placeables.getOrNull(0)?.let { it.place((width - it.width) / 2, (height - it.height) / 2) }
            placeables.getOrNull(1)?.let { it.place(width / 2 + subStart.roundToPx(), height - it.height) }
        }
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector, size: Dp, color: Color, shadow: Boolean) {
    Box {
        if (shadow) Icon(icon, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(y = 1.dp).size(size))
        Icon(icon, null, tint = color, modifier = Modifier.size(size))
    }
}

/**
 * The top edge of the picture while everything is closed: marks for settings that change how a shot is
 * taken and differ from the default, then the histogram. Orange with a soft shadow; it never turns.
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
    val accent = ModeAmber
    val markRow: @Composable () -> Unit = {
        marks.forEach { mark -> BadgeView(mark.badge, mark.color ?: accent, small = true) }
        if (histogram != null) {
            HistogramView(histogram = histogram, modifier = Modifier.size(width = 64.dp, height = 22.dp))
        }
    }
    if (center == null) {
        Row(
            modifier = modifier.then(tapModifier),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
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
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.End),
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
