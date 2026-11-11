package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True while the footer is white (front flash lights the face with it): its light ink turns dark. */
internal val LocalLightFooter = compositionLocalOf { false }

private val DarkFooterInk = Color(0xFF1F1F1F)

/** This light ink colour, or near-black with the same opacity on a white footer. */
@Composable
internal fun Color.onFooter(): Color = if (LocalLightFooter.current) DarkFooterInk.copy(alpha = alpha) else this

/** Shared dimensions keep preview placement and control measurement in agreement. */
internal object CameraControlsLayoutDefaults {
    val ParameterBarHeight = 48.dp
    val CaptureButtonSize = 80.dp
    val SideButtonSize = 48.dp
    // The mode swipe zone, centred on the shutter; its centre sits 63 dp above the bottom.
    val ModeZoneWidth = 240.dp
    val ModeZoneHeight = 100.dp
    val BottomPadding = 13.dp
    val CaptureClearance = 8.dp

    // Footer grid: side controls centred 40 dp from the screen edges, columns 70 dp apart.
    val SideMargin = 16.dp
    val ColumnPitch = 70.dp
    // Every framed footer control: a 2 dp line, a 2 dp gap, then the face.
    val LineWidth = 2.dp
    val LineGap = 2.dp
    val LineColor = Color(0xFFC9C7BF)
    // Mode marks that are not the current mode.
    val MarkColor = Color(0xFFD3D1C7)

    val CaptureAreaHeight = ModeZoneHeight + BottomPadding
    // The zoom collar's top above the shutter's top: 8 dp off the shutter, 26 dp deep.
    val CollarClearance = 8.dp + 26.dp
    // Room above the mode zone for the zoom collar.
    val CollarRise = CaptureButtonSize / 2 + CollarClearance - ModeZoneHeight / 2
    val CollapsedControlsHeight = CaptureAreaHeight + CollarRise + CaptureClearance + ParameterBarHeight
}

/**
 * Measures the controls in one pass so placement uses their actual heights, including the
 * ruler's expand/collapse animation. The parameter row stays in its collapsed position;
 * the ruler expands above it. The shutter stays at the bottom.
 */
@Composable
internal fun CameraBottomControlsLayout(
    viewfinderBottom: Dp,
    parameterRuler: @Composable () -> Unit,
    parameterBar: @Composable () -> Unit,
    captureControls: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            Box(contentAlignment = Alignment.Center) { parameterRuler() }
            Box(contentAlignment = Alignment.Center) { parameterBar() }
            Box(contentAlignment = Alignment.Center) { captureControls() }
        }
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val (ruler, parameters, capture) = measurables.map { it.measure(looseConstraints) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val clearance = CameraControlsLayoutDefaults.CaptureClearance.roundToPx()
        val captureTop = height - capture.height
        val controlsBottom = captureTop - clearance
        val previewBottom = viewfinderBottom.roundToPx().coerceAtMost(height)
        val spaceBelowPreview = controlsBottom - previewBottom

        // Choose the parameter row's anchor from the collapsed controls so the ruler's
        // animated height cannot switch placement branches or move the parameter row.
        // Start outside controls at the preview edge instead of leaving all spare space
        // above them. Any remaining space belongs between the parameters and the shutter.
        val parameterTop = when {
            // XPAN uses a side parameter row; its ruler remains above the shutter.
            parameters.height == 0 -> controlsBottom
            spaceBelowPreview >= parameters.height -> previewBottom
            else -> minOf(controlsBottom, previewBottom - clearance) - parameters.height
        }
        val rulerTop = parameterTop - ruler.height

        layout(width, height) {
            capture.placeRelative((width - capture.width) / 2, captureTop)
            parameters.placeRelative((width - parameters.width) / 2, parameterTop)
            ruler.placeRelative((width - ruler.width) / 2, rulerTop)
        }
    }
}
