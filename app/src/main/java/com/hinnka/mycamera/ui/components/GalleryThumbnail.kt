package com.hinnka.mycamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import com.hinnka.mycamera.viewmodel.CapturedThumbnail
import com.hinnka.mycamera.ui.icons.AppIcons
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.roundToInt

/**
 * 相册入口缩略图组件
 * 显示最近拍摄的照片缩略图，点击进入相册
 */
@Composable
fun GalleryThumbnail(
    latestPhoto: MediaData?,
    viewModel: GalleryViewModel,
    onClick: () -> Unit,
    capturedThumbnail: CapturedThumbnail? = null,
    onCapturedThumbnailLoaded: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val refreshKey = latestPhoto?.id?.let { viewModel.getPreparedPhotoThumbnailRefreshKey(it) } ?: 0L
    var finalThumbnailLoaded by remember(capturedThumbnail?.captureId) { mutableStateOf(false) }
    val finalCaptureId = capturedThumbnail?.takeIf {
        it.processingFinished && it.savedPhotoId == latestPhoto?.id
    }?.captureId
    val capturePreview = capturedThumbnail?.bitmap?.takeUnless { finalThumbnailLoaded || it.isRecycled }

    ThumbnailTile(onClick = onClick, modifier = modifier) {
        if (latestPhoto != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(latestPhoto.thumbnailUri)
                    .memoryCacheKey(
                        "gallery_thumbnail_${latestPhoto.id}_${latestPhoto.thumbnailUri}_${refreshKey}_${finalCaptureId}"
                    )
                    .crossfade(capturePreview == null)
                    .build(),
                onSuccess = {
                    if (finalCaptureId != null) {
                        finalThumbnailLoaded = true
                        onCapturedThumbnailLoaded(finalCaptureId)
                    }
                },
                contentDescription = "Gallery",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (latestPhoto.isVideo) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            ThumbnailGloss()
        } else {
            // 没有照片时显示图标
            Icon(
                imageVector = AppIcons.PhotoLibrary,
                contentDescription = "Gallery",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        // Keep the shutter preview above the file-backed image until this same capture's
        // final file has decoded. Older saves and loading placeholders cannot replace it.
        if (capturePreview != null) {
            Image(
                bitmap = capturePreview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The gallery thumbnail's size and corner, shared by the viewfinder thumbnail, its edge line and the version strip. */
internal val ThumbnailSize = 48.dp
internal val ThumbnailCorner = 10.dp
private val ThumbnailInnerCorner = 7.dp

/** The viewfinder's thumbnail tile: one look for the camera's gallery button and the photo view's version strip. */
@Composable
internal fun ThumbnailTile(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    PhysicalButton(
        modifier = modifier.size(ThumbnailSize),
        onClick = onClick,
        shape = RoundedCornerShape(ThumbnailCorner),
        contentShape = RoundedCornerShape(ThumbnailInnerCorner),
        content = content
    )
}

/** The light-to-dark sheen over a thumbnail photo. */
@Composable
internal fun ThumbnailGloss() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.14f)
                    )
                )
            )
    )
}

internal enum class EdgeRingStyle { RUNNING, DOTTED, SOLID }

/**
 * The thumbnail's edge line, one weight and rounded ends in every style: a bright arc running round it
 * (processing), evenly spaced round dots (waiting), or the whole edge (done, fading by [alpha]).
 */
@Composable
internal fun ThumbnailEdgeRing(
    style: EdgeRingStyle,
    color: Color,
    modifier: Modifier,
    alpha: () -> Float = { 1f },
) {
    val density = LocalDensity.current
    val strokePx = with(density) { 2.5.dp.toPx() }
    val cornerPx = with(density) { ThumbnailCorner.toPx() }
    val periodTargetPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 3.5.dp.toPx() }
    // Only animates while running, so a waiting or paused thumbnail asks for no frames.
    val phase = if (style == EdgeRingStyle.RUNNING) {
        rememberInfiniteTransition(label = "thumbnailRing").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
            label = "thumbnailRingPhase"
        )
    } else null
    val edge = remember { Path() }
    val measure = remember { androidx.compose.ui.graphics.PathMeasure() }
    val round = androidx.compose.ui.graphics.StrokeCap.Round
    Canvas(modifier) {
        val inset = strokePx / 2
        edge.reset()
        edge.addRoundRect(
            RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(cornerPx - inset))
        )
        measure.setPath(edge, false)
        val length = measure.length
        when (style) {
            EdgeRingStyle.RUNNING -> {
                drawPath(edge, color.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx))
                // Read here, in the draw phase: a frame only redraws, it does not recompose, so the lap stays smooth.
                val arcLength = length * 0.25f
                val start = (phase?.value ?: 0f) * length
                // One dash as long as the arc, one period per lap, shifted by the phase. On a closed edge the
                // dash continues across the edge's start point, so no seam: two pieces joined there left a stray stub.
                drawPath(
                    edge,
                    color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        strokePx,
                        cap = round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(arcLength, length - arcLength),
                            (length - start) % length
                        )
                    )
                )
            }
            EdgeRingStyle.DOTTED -> {
                // A whole number of dash periods round the edge, so the spacing is even where the line closes.
                val count = (length / periodTargetPx).roundToInt().coerceAtLeast(4)
                val period = length / count
                // Round caps add half the stroke at each end, so the drawn dash is on + stroke long.
                val on = (period - gapPx - strokePx).coerceAtLeast(0.01f)
                drawPath(
                    edge,
                    color.copy(alpha = 0.6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        strokePx,
                        cap = round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(on, period - on))
                    )
                )
            }
            EdgeRingStyle.SOLID -> drawPath(
                edge,
                color.copy(alpha = alpha().coerceIn(0f, 1f)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx)
            )
        }
    }
}
