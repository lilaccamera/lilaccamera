package com.hinnka.mycamera.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.ml.DepthModelManager
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlinx.coroutines.launch

private val GuideText = Color(0xFFE8E6DF)
private val GuideDim = Color(0xFFB4B2A9)
private val GuideGrey = Color(0xFF888780)

/** One step of a mode guide: a line icon, a short title, one or two sentences, and optional content under them. */
internal class GuideStep(
    val icon: ImageVector,
    val title: Int,
    val text: Int,
    val extra: (@Composable () -> Unit)? = null,
)

/** The index of the step about Quick and Depth blur, for opening the guide there. */
internal const val PortraitGuideBlurStep = 1

/**
 * A small pill near the bottom of the picture in Portrait: first it offers the guide, until that has been
 * opened once; then, while the depth model is missing, it reminds once that Depth blur needs it.
 */
@Composable
internal fun ModeGuidePill(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(shadow = ViewfinderTextShadow)
        )
    }
}

/**
 * How Portrait works: faces in the viewfinder, Quick and Depth blur (with the depth model download), the
 * subject cut-out, and editing later.
 */
@Composable
internal fun BoxScope.PortraitGuide(
    startStep: Int,
    quickBlur: Boolean,
    onUseDepth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModeGuide(
        steps = listOf(
            GuideStep(AppIcons.User, R.string.portrait_guide_faces_title, R.string.portrait_guide_faces),
            GuideStep(AppIcons.ApertureLine, R.string.portrait_guide_blur_title, R.string.portrait_guide_blur) {
                DepthModelStatus(quickBlur = quickBlur, onUseDepth = onUseDepth, onDownload = { DepthModelManager.download(context) })
            },
            GuideStep(AppIcons.ScissorsLine, R.string.portrait_guide_subject_title, R.string.portrait_guide_subject),
            GuideStep(AppIcons.Sliders, R.string.portrait_guide_edit_title, R.string.portrait_guide_edit),
        ),
        startStep = startStep,
        onDismiss = onDismiss,
    )
}

/**
 * A mode explained as short steps in a sheet from the bottom, one step per page with dots, Back and
 * Next. A tap outside, the ×, Back or the last step's Done closes it.
 */
@Composable
internal fun BoxScope.ModeGuide(
    steps: List<GuideStep>,
    startStep: Int,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = startStep.coerceIn(0, steps.lastIndex)) { steps.size }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onDismiss)

    // A tap on the picture or the footer closes the guide.
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            // Nearly opaque: the guide is read as text, so the controls under it must not show through.
            .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 6.dp, bottom = 10.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (i == pager.currentPage) GuideText else GuideGrey.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 40.dp, height = 40.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.CloseLine, contentDescription = stringResource(R.string.close), tint = GuideDim, modifier = Modifier.size(20.dp))
            }
        }
        // A fixed height, so moving between steps never makes the sheet jump.
        // Pages start at the top, so each step's icon sits at the same height whatever its text length.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(204.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.Top
        ) { page ->
            val step = steps[page]
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(step.icon, contentDescription = null, tint = CameraControlsLayoutDefaults.LineColor, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(step.title), color = GuideText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(step.text),
                    color = GuideDim,
                    fontSize = 13.sp,
                    lineHeight = 1.4.em,
                    // Icon and title are centred; the sentences read better from a straight left edge.
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                step.extra?.let { extra ->
                    Spacer(Modifier.height(12.dp))
                    extra()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val first = pager.currentPage == 0
            val last = pager.currentPage == steps.lastIndex
            GuideButton(stringResource(R.string.back), enabled = !first) {
                scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
            }
            GuideButton(stringResource(if (last) R.string.depth_model_done else R.string.portrait_guide_next), enabled = true) {
                if (last) onDismiss() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

/** The depth model on the Blur step: download with progress, the failure, or Use Depth once installed. */
@Composable
private fun DepthModelStatus(quickBlur: Boolean, onUseDepth: () -> Unit, onDownload: () -> Unit) {
    val context = LocalContext.current
    val modelState = DepthModelManager.observe(context).collectAsState().value
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (modelState) {
            is DepthModelDownloadState.Ready -> {
                if (quickBlur) {
                    GuideButton(stringResource(R.string.portrait_guide_use_depth), enabled = true, outlined = true, onClick = onUseDepth)
                } else {
                    Text(stringResource(R.string.portrait_guide_depth_on), color = ModeAmber, fontSize = 13.sp)
                }
            }
            is DepthModelDownloadState.Downloading -> {
                val progress = modelState.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(200.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.width(200.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.depth_model_downloading), color = GuideDim, fontSize = 12.sp)
            }
            else -> {
                if (modelState is DepthModelDownloadState.Failed) {
                    Text(
                        stringResource(R.string.depth_model_download_failed),
                        color = GuideDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                }
                GuideButton(stringResource(R.string.portrait_intro_download), enabled = true, outlined = true, onClick = onDownload)
            }
        }
    }
}

@Composable
private fun GuideButton(text: String, enabled: Boolean, outlined: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .then(if (outlined) Modifier.border(1.25.dp, ModeAmber.copy(alpha = 0.8f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !enabled -> GuideGrey.copy(alpha = 0.4f)
                outlined -> ModeAmber
                else -> GuideText
            },
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
