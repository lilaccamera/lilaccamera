package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.icons.AppIcons

internal fun basicsGuideSteps() = listOf(
    GuideStep(AppIcons.ModeSwipeLine, R.string.basics_guide_modes_title, R.string.basics_guide_modes),
    GuideStep(AppIcons.SwipeDownLine, R.string.basics_guide_quick_title, R.string.basics_guide_quick),
    GuideStep(AppIcons.SwipeUpLine, R.string.basics_guide_manual_title, R.string.basics_guide_manual),
    GuideStep(AppIcons.ZoomRingLine, R.string.basics_guide_zoom_title, R.string.basics_guide_zoom),
    GuideStep(AppIcons.Film, R.string.basics_guide_looks_title, R.string.basics_guide_looks),
)

/** The camera's gestures, one per step: modes, quick settings, manual controls, zoom and looks. */
@Composable
internal fun BoxScope.BasicsGuide(onDismiss: () -> Unit) {
    ModeGuide(
        steps = basicsGuideSteps(),
        startStep = 0,
        onDismiss = onDismiss,
    )
}
