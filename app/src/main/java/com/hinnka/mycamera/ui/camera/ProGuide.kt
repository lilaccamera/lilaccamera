package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.icons.AppIcons

internal fun proGuideSteps() = listOf(
    GuideStep(AppIcons.SensorLine, R.string.pro_guide_raw_title, R.string.pro_guide_raw),
    GuideStep(AppIcons.StackLine, R.string.pro_guide_merge_title, R.string.pro_guide_merge),
    GuideStep(AppIcons.PhotoLine, R.string.pro_guide_render_title, R.string.pro_guide_render),
)

/** How Pro works: RAW before the camera's own processing, merged HDR+ frames, and the app's own rendering. */
@Composable
internal fun BoxScope.ProGuide(onDismiss: () -> Unit) {
    ModeGuide(
        steps = proGuideSteps(),
        startStep = 0,
        onDismiss = onDismiss,
    )
}
