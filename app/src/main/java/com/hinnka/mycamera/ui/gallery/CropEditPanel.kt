package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.AspectRatio
import com.hinnka.mycamera.gallery.PostEditGeometry
import com.hinnka.mycamera.ui.theme.AccentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.hinnka.mycamera.ui.icons.AppIcons

/**
 * 裁剪比例选项
 */
sealed class CropAspectOption(
    val displayName: String,
    val widthRatio: Float,
    val heightRatio: Float
) {
    /** 自由裁剪 */
    data object Free : CropAspectOption("FREE", 0f, 0f)

    /** 原始比例 */
    data object Original : CropAspectOption("ORIGINAL", 0f, 0f)

    /** 来自 AspectRatio 枚举的预设比例 */
    data class FromAspectRatio(val ratio: AspectRatio) : CropAspectOption(
        ratio.getDisplayName(),
        ratio.widthRatio.toFloat(),
        ratio.heightRatio.toFloat()
    )

    /** 自定义比例 */
    data class Custom(val w: Float, val h: Float) : CropAspectOption(
        "${w.toInt()}:${h.toInt()}", w, h
    )

    val isFree get() = this is Free
    val isOriginal get() = this is Original
    val hasFixedRatio get() = !isFree && !isOriginal

    /** The shape alone, so one ratio written two ways (4:3 and 8:6, after turning twice) compares equal. */
    val shapeKey: Any
        get() = when {
            isFree -> "free"
            isOriginal -> "original"
            else -> (widthRatio / heightRatio * 1000f).roundToInt()
        }

    companion object {
        /** The choice back from its [shapeKey]. */
        fun fromShapeKey(key: Any?): CropAspectOption = when (key) {
            "free" -> Free
            is Int -> Custom(key / 1000f, 1f)
            else -> Original
        }
    }

    /**
     * 获取宽高比 (w/h)，根据图像方向自动调整
     */
    fun getAspectRatioValue(imageW: Int, imageH: Int): Float? {
        if (isFree) return null
        if (isOriginal) return imageW.toFloat() / imageH.toFloat()
        if (heightRatio == 0f) return null
        return widthRatio / heightRatio
    }
}

/**
 * 裁剪叠加层 - 在图片上方显示裁剪框
 */
@Composable
fun CropOverlay(
    bitmap: Bitmap?,
    cropRect: RectF,
    cropAspectRatio: Float? = null,
    straightenDegrees: Float = 0f,
    geometrySourceWidth: Int? = null,
    geometrySourceHeight: Int? = null,
    onCropRectChanged: (RectF) -> Unit,
    aspectOption: CropAspectOption,
    contentPadding: Dp = 0.dp,
    rotationDegrees: Int = 0,
    mirrorHorizontal: Boolean = false,
    showFrame: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (bitmap == null) return

    // [bitmap] may come unturned and unflipped: the turn and flip are then drawn here, so a tap on Rotate or
    // Flip shows at once without a new render. The geometry below works on the turned size.
    val quarterTurned = PostEditGeometry.normalizeRotation(rotationDegrees) % 180 != 0
    val turnedWidth = if (quarterTurned) bitmap.height else bitmap.width
    val turnedHeight = if (quarterTurned) bitmap.width else bitmap.height
    val cropSourceWidth = geometrySourceWidth?.takeIf { it > 0 } ?: turnedWidth
    val cropSourceHeight = geometrySourceHeight?.takeIf { it > 0 } ?: turnedHeight
    val normalizedStraightenDegrees = PostEditGeometry.normalizeStraightenDegrees(straightenDegrees)
    val straightenedImageSize = remember(turnedWidth, turnedHeight, normalizedStraightenDegrees) {
        PostEditGeometry.straightenedDimensions(
            turnedWidth,
            turnedHeight,
            normalizedStraightenDegrees
        )
    }
    val imageWidth = straightenedImageSize.first.toFloat()
    val imageHeight = straightenedImageSize.second.toFloat()
    val cropGeometrySize = remember(
        cropSourceWidth,
        cropSourceHeight,
        normalizedStraightenDegrees
    ) {
        PostEditGeometry.straightenedDimensions(
            cropSourceWidth,
            cropSourceHeight,
            normalizedStraightenDegrees
        )
    }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val contentPaddingPx = with(density) { contentPadding.toPx() }
    val dp = density.density

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragHandle by remember { mutableStateOf<DragHandle?>(null) }

    val safeCropRect = remember(
        cropRect.left,
        cropRect.top,
        cropRect.right,
        cropRect.bottom
    ) {
        normalizeCropRect(cropRect, minSize = 0.001f)
    }
    val currentCropRect by rememberUpdatedState(safeCropRect)

    // 计算图片在容器中的实际显示区域
    val imageDisplayRect = remember(containerSize, imageWidth, imageHeight, contentPaddingPx) {
        if (containerSize.width == 0 || containerSize.height == 0) {
            Rect.Zero
        } else {
            calculateImageDisplayRect(
                containerSize.width.toFloat(),
                containerSize.height.toFloat(),
                imageWidth,
                imageHeight,
                contentPaddingPx
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        // 裁剪页持有未应用水平校正的底图。这里直接在绘制阶段旋转，滑杆的每次
        // value change 都能在下一帧显示，无需等待后台重新生成 Bitmap。
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageDisplayRect != Rect.Zero) {
                val pixelScale = min(
                    imageDisplayRect.width / imageWidth,
                    imageDisplayRect.height / imageHeight
                )
                val sourceDisplayWidth = (bitmap.width * pixelScale).roundToInt().coerceAtLeast(1)
                val sourceDisplayHeight = (bitmap.height * pixelScale).roundToInt().coerceAtLeast(1)
                val destinationOffset = IntOffset(
                    x = (imageDisplayRect.center.x - sourceDisplayWidth / 2f).roundToInt(),
                    y = (imageDisplayRect.center.y - sourceDisplayHeight / 2f).roundToInt()
                )
                // The saved photo's order, innermost first: turn, then flip, then straighten.
                val center = imageDisplayRect.center
                rotate(degrees = normalizedStraightenDegrees, pivot = center) {
                    scale(scaleX = if (mirrorHorizontal) -1f else 1f, scaleY = 1f, pivot = center) {
                        rotate(degrees = rotationDegrees.toFloat(), pivot = center) {
                            drawImage(
                                image = imageBitmap,
                                dstOffset = destinationOffset,
                                dstSize = IntSize(sourceDisplayWidth, sourceDisplayHeight),
                                filterQuality = FilterQuality.Medium
                            )
                        }
                    }
                }
            }
        }

        // 裁剪叠加层
        if (imageDisplayRect != Rect.Zero && showFrame) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        imageDisplayRect,
                        aspectOption,
                        cropAspectRatio,
                        normalizedStraightenDegrees,
                        cropSourceWidth,
                        cropSourceHeight,
                        dp
                    ) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragHandle = detectDragHandle(
                                    point = offset,
                                    frame = frameOnScreen(currentCropRect, imageDisplayRect),
                                    free = aspectOption.isFree,
                                    dp = dp
                                )
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val handle = dragHandle ?: return@detectDragGestures

                                val dx = dragAmount.x / imageDisplayRect.width
                                val dy = dragAmount.y / imageDisplayRect.height
                                val newRect = if (handle == DragHandle.CENTER) {
                                    PostEditGeometry.translateCropRectWithinStraightenedSource(
                                        rect = currentCropRect,
                                        deltaX = dx,
                                        deltaY = dy,
                                        width = cropSourceWidth,
                                        height = cropSourceHeight,
                                        straightenDegrees = normalizedStraightenDegrees
                                    )
                                } else {
                                    val proposedRect = moveCropRect(
                                        rect = currentCropRect,
                                        handle = handle,
                                        dx = dx,
                                        dy = dy,
                                        fixedAspect = cropAspectRatio
                                            ?: aspectOption.getAspectRatioValue(turnedWidth, turnedHeight),
                                        imageAspect = cropGeometrySize.first.toFloat() /
                                            cropGeometrySize.second
                                    )
                                    PostEditGeometry.clampCropRectChangeToStraightenedSource(
                                        current = currentCropRect,
                                        proposed = proposedRect,
                                        width = cropSourceWidth,
                                        height = cropSourceHeight,
                                        straightenDegrees = normalizedStraightenDegrees
                                    )
                                }

                                onCropRectChanged(newRect)
                            },
                            onDragEnd = {
                                dragHandle = null
                            },
                            onDragCancel = {
                                dragHandle = null
                            }
                        )
                    }
            ) {
                drawCropOverlay(frameOnScreen(safeCropRect, imageDisplayRect), aspectOption.isFree, dp)
            }
        }
    }
}

/**
 * 拖拽手柄类型
 */
private enum class DragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    CENTER
}

/**
 * 计算图片在容器中的适配显示区域（Fit 模式）
 */
private fun calculateImageDisplayRect(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    contentPadding: Float = 0f
): Rect {
    val horizontalPadding = min(contentPadding, containerWidth / 2f)
    val verticalPadding = min(contentPadding, containerHeight / 2f)
    val availableWidth = (containerWidth - horizontalPadding * 2f).coerceAtLeast(1f)
    val availableHeight = (containerHeight - verticalPadding * 2f).coerceAtLeast(1f)
    val imageAspect = imageWidth / imageHeight
    val containerAspect = availableWidth / availableHeight

    val displayWidth: Float
    val displayHeight: Float

    if (imageAspect > containerAspect) {
        displayWidth = availableWidth
        displayHeight = availableWidth / imageAspect
    } else {
        displayHeight = availableHeight
        displayWidth = availableHeight * imageAspect
    }

    val offsetX = horizontalPadding + (availableWidth - displayWidth) / 2f
    val offsetY = verticalPadding + (availableHeight - displayHeight) / 2f

    return Rect(offsetX, offsetY, offsetX + displayWidth, offsetY + displayHeight)
}

private fun frameOnScreen(cropRect: RectF, imageDisplayRect: Rect) = Rect(
    imageDisplayRect.left + cropRect.left * imageDisplayRect.width,
    imageDisplayRect.top + cropRect.top * imageDisplayRect.height,
    imageDisplayRect.left + cropRect.right * imageDisplayRect.width,
    imageDisplayRect.top + cropRect.bottom * imageDisplayRect.height
)

/**
 * The part of the frame a finger at [point] takes, in screen pixels: a corner from a zone centred 10 dp inside
 * it (a finger needn't land on the line), in [free] crops an edge from a strip reaching 14 dp outside to 22 dp
 * inside, else the inside, which moves the frame. Zones shrink on a small frame so its middle stays movable.
 */
private fun detectDragHandle(point: Offset, frame: Rect, free: Boolean, dp: Float): DragHandle? {
    val reach = min(30f * dp, frame.minDimension / 3f)
    val inset = min(10f * dp, frame.minDimension / 6f)
    listOf(
        DragHandle.TOP_LEFT to Offset(frame.left + inset, frame.top + inset),
        DragHandle.TOP_RIGHT to Offset(frame.right - inset, frame.top + inset),
        DragHandle.BOTTOM_LEFT to Offset(frame.left + inset, frame.bottom - inset),
        DragHandle.BOTTOM_RIGHT to Offset(frame.right - inset, frame.bottom - inset),
    ).map { (handle, at) -> handle to (point - at).getDistance() }
        .filter { it.second <= reach }
        .minByOrNull { it.second }
        ?.let { return it.first }
    if (free) {
        val outside = 14f * dp
        val inside = min(22f * dp, frame.minDimension / 3f)
        val clear = min(24f * dp, frame.minDimension / 3f)
        val alongX = point.x > frame.left + clear && point.x < frame.right - clear
        val alongY = point.y > frame.top + clear && point.y < frame.bottom - clear
        when {
            alongX && point.y in (frame.top - outside)..(frame.top + inside) -> return DragHandle.TOP
            alongX && point.y in (frame.bottom - inside)..(frame.bottom + outside) -> return DragHandle.BOTTOM
            alongY && point.x in (frame.left - outside)..(frame.left + inside) -> return DragHandle.LEFT
            alongY && point.x in (frame.right - inside)..(frame.right + outside) -> return DragHandle.RIGHT
        }
    }
    return if (frame.contains(point)) DragHandle.CENTER else null
}

/**
 * 根据拖拽更新裁剪矩形
 */
private fun moveCropRect(
    rect: RectF,
    handle: DragHandle,
    dx: Float, dy: Float,
    fixedAspect: Float?,
    imageAspect: Float
): RectF {
    val minSize = 0.1f // 最小裁剪区域为图片的 10%
    val result = normalizeCropRect(rect, minSize)

    when (handle) {
        DragHandle.CENTER -> {
            val w = result.width()
            val h = result.height()
            val maxLeft = (1f - w).coerceAtLeast(0f)
            val maxTop = (1f - h).coerceAtLeast(0f)
            val newLeft = (result.left + dx).coerceIn(0f, maxLeft)
            val newTop = (result.top + dy).coerceIn(0f, maxTop)
            result.set(newLeft, newTop, newLeft + w, newTop + h)
        }
        else -> {
            if (fixedAspect != null) {
                moveWithFixedAspect(result, handle, dx, dy, fixedAspect, imageAspect, minSize)
            } else {
                moveWithFreeAspect(result, handle, dx, dy, minSize)
            }
        }
    }

    // 确保在边界内
    result.left = result.left.coerceIn(0f, 1f)
    result.top = result.top.coerceIn(0f, 1f)
    result.right = result.right.coerceIn(0f, 1f)
    result.bottom = result.bottom.coerceIn(0f, 1f)

    // 确保最小尺寸
    if (result.width() < minSize || result.height() < minSize) {
        return normalizeCropRect(rect, minSize)
    }

    return result
}

private fun normalizeCropRect(rect: RectF, minSize: Float = 0.1f): RectF {
    val rawLeft = min(rect.left.safeCropValue(0f), rect.right.safeCropValue(1f))
    val rawTop = min(rect.top.safeCropValue(0f), rect.bottom.safeCropValue(1f))
    val rawRight = max(rect.left.safeCropValue(0f), rect.right.safeCropValue(1f))
    val rawBottom = max(rect.top.safeCropValue(0f), rect.bottom.safeCropValue(1f))

    var left = rawLeft.coerceIn(0f, 1f)
    var top = rawTop.coerceIn(0f, 1f)
    var right = rawRight.coerceIn(0f, 1f)
    var bottom = rawBottom.coerceIn(0f, 1f)

    val safeMinSize = minSize.coerceIn(0f, 1f)
    if (right - left < safeMinSize) {
        val centerX = ((left + right) / 2f).coerceIn(0f, 1f)
        left = (centerX - safeMinSize / 2f).coerceIn(0f, 1f - safeMinSize)
        right = left + safeMinSize
    }
    if (bottom - top < safeMinSize) {
        val centerY = ((top + bottom) / 2f).coerceIn(0f, 1f)
        top = (centerY - safeMinSize / 2f).coerceIn(0f, 1f - safeMinSize)
        bottom = top + safeMinSize
    }

    return RectF(left, top, right, bottom)
}

private fun Float.safeCropValue(fallback: Float): Float {
    return if (isFinite()) this else fallback
}

/**
 * 自由比例拖拽
 */
private fun moveWithFreeAspect(
    rect: RectF,
    handle: DragHandle,
    dx: Float, dy: Float,
    minSize: Float
) {
    when (handle) {
        DragHandle.TOP_LEFT -> {
            rect.left = min(rect.left + dx, rect.right - minSize)
            rect.top = min(rect.top + dy, rect.bottom - minSize)
        }
        DragHandle.TOP_RIGHT -> {
            rect.right = max(rect.right + dx, rect.left + minSize)
            rect.top = min(rect.top + dy, rect.bottom - minSize)
        }
        DragHandle.BOTTOM_LEFT -> {
            rect.left = min(rect.left + dx, rect.right - minSize)
            rect.bottom = max(rect.bottom + dy, rect.top + minSize)
        }
        DragHandle.BOTTOM_RIGHT -> {
            rect.right = max(rect.right + dx, rect.left + minSize)
            rect.bottom = max(rect.bottom + dy, rect.top + minSize)
        }
        DragHandle.TOP -> {
            rect.top = min(rect.top + dy, rect.bottom - minSize)
        }
        DragHandle.BOTTOM -> {
            rect.bottom = max(rect.bottom + dy, rect.top + minSize)
        }
        DragHandle.LEFT -> {
            rect.left = min(rect.left + dx, rect.right - minSize)
        }
        DragHandle.RIGHT -> {
            rect.right = max(rect.right + dx, rect.left + minSize)
        }
        else -> {}
    }
}

/**
 * 固定比例拖拽
 * aspectRatio = targetW_ratio / targetH_ratio (裁剪比例，如 4:3 → 4/3)
 * imageAspect = image_w / image_h (原图宽高比)
 *
 * 裁剪框的归一化坐标中，实际宽高比 = (rect.width * imageWidth) / (rect.height * imageHeight)
 * 为了让裁剪结果的像素比 = aspectRatio，需要：
 *   rect.width / rect.height = aspectRatio / imageAspect
 */
private fun moveWithFixedAspect(
    rect: RectF,
    handle: DragHandle,
    dx: Float, dy: Float,
    targetAspect: Float,
    imageAspect: Float,
    minSize: Float
) {
    // 在归一化坐标中，保持 w/h = targetAspect / imageAspect
    val normalizedAspect = targetAspect / imageAspect

    when (handle) {
        DragHandle.TOP_LEFT, DragHandle.TOP_RIGHT, DragHandle.BOTTOM_LEFT, DragHandle.BOTTOM_RIGHT -> {
            // The opposite corner stays put; at the photo's edge the frame stops growing instead of freezing.
            val left = handle == DragHandle.TOP_LEFT || handle == DragHandle.BOTTOM_LEFT
            val top = handle == DragHandle.TOP_LEFT || handle == DragHandle.TOP_RIGHT
            val anchorX = if (left) rect.right else rect.left
            val anchorY = if (top) rect.bottom else rect.top
            val movedX = if (left) rect.left + dx else rect.right + dx
            var w = max(abs(movedX - anchorX), minSize)
            var h = w / normalizedAspect
            val maxW = if (left) anchorX else 1f - anchorX
            val maxH = if (top) anchorY else 1f - anchorY
            if (h > maxH) { h = maxH; w = h * normalizedAspect }
            if (w > maxW) { w = maxW; h = w / normalizedAspect }
            if (w >= minSize && h >= minSize) {
                rect.left = if (left) anchorX - w else anchorX
                rect.right = if (left) anchorX else anchorX + w
                rect.top = if (top) anchorY - h else anchorY
                rect.bottom = if (top) anchorY else anchorY + h
            }
        }
        DragHandle.TOP, DragHandle.BOTTOM -> {
            val primaryDy = if (handle == DragHandle.TOP) dy else dy
            val newTop = if (handle == DragHandle.TOP) min(rect.top + primaryDy, rect.bottom - minSize) else rect.top
            val newBottom = if (handle == DragHandle.BOTTOM) max(rect.bottom + primaryDy, rect.top + minSize) else rect.bottom
            val newHeight = newBottom - newTop
            val newWidth = newHeight * normalizedAspect
            val centerX = (rect.left + rect.right) / 2f
            val newLeft = centerX - newWidth / 2f
            val newRight = centerX + newWidth / 2f
            if (newLeft >= 0f && newRight <= 1f && newWidth >= minSize && newHeight >= minSize) {
                rect.top = newTop
                rect.bottom = newBottom
                rect.left = newLeft
                rect.right = newRight
            }
        }
        DragHandle.LEFT, DragHandle.RIGHT -> {
            val newLeft = if (handle == DragHandle.LEFT) min(rect.left + dx, rect.right - minSize) else rect.left
            val newRight = if (handle == DragHandle.RIGHT) max(rect.right + dx, rect.left + minSize) else rect.right
            val newWidth = newRight - newLeft
            val newHeight = newWidth / normalizedAspect
            val centerY = (rect.top + rect.bottom) / 2f
            val newTop = centerY - newHeight / 2f
            val newBottom = centerY + newHeight / 2f
            if (newTop >= 0f && newBottom <= 1f && newWidth >= minSize && newHeight >= minSize) {
                rect.left = newLeft
                rect.right = newRight
                rect.top = newTop
                rect.bottom = newBottom
            }
        }
        else -> {}
    }
}

/** The dimming outside [frame], its line, a thirds grid, corner brackets inside it, and in [free] crops a bar inside each edge. */
private fun DrawScope.drawCropOverlay(frame: Rect, free: Boolean, dp: Float) {
    val (left, top, right, bottom) = listOf(frame.left, frame.top, frame.right, frame.bottom)
    val dimColor = Color.Black.copy(alpha = 0.6f)
    drawRect(dimColor, Offset(0f, 0f), Size(size.width, top))
    drawRect(dimColor, Offset(0f, bottom), Size(size.width, size.height - bottom))
    drawRect(dimColor, Offset(0f, top), Size(left, bottom - top))
    drawRect(dimColor, Offset(right, top), Size(size.width - right, bottom - top))

    drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(width = 1f * dp))
    val gridColor = Color.White.copy(alpha = 0.3f)
    for (i in 1..2) {
        val x = left + (right - left) * i / 3f
        val y = top + (bottom - top) * i / 3f
        drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 0.75f * dp)
        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 0.75f * dp)
    }

    val stroke = 3f * dp
    val half = stroke / 2f
    val length = min(20f * dp, frame.minDimension / 3f)
    for ((x, sx) in listOf(left + half to 1f, right - half to -1f)) {
        for ((y, sy) in listOf(top + half to 1f, bottom - half to -1f)) {
            drawLine(Color.White, Offset(x - sx * half, y), Offset(x + sx * length, y), strokeWidth = stroke)
            drawLine(Color.White, Offset(x, y - sy * half), Offset(x, y + sy * length), strokeWidth = stroke)
        }
    }
    if (free) {
        val bar = 12f * dp
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        drawLine(Color.White, Offset(cx - bar, top + half), Offset(cx + bar, top + half), strokeWidth = stroke)
        drawLine(Color.White, Offset(cx - bar, bottom - half), Offset(cx + bar, bottom - half), strokeWidth = stroke)
        drawLine(Color.White, Offset(left + half, cy - bar), Offset(left + half, cy + bar), strokeWidth = stroke)
        drawLine(Color.White, Offset(right - half, cy - bar), Offset(right - half, cy + bar), strokeWidth = stroke)
    }
}

/**
 * 根据目标比例计算居中的初始裁剪区域（归一化坐标 0-1）
 */
fun calculateInitialCropRect(
    imageWidth: Int,
    imageHeight: Int,
    aspectOption: CropAspectOption,
    cropBounds: RectF = RectF(0f, 0f, 1f, 1f),
    originalAspectRatio: Float = imageWidth.toFloat() / imageHeight
): RectF {
    if (aspectOption.isFree) {
        return RectF(cropBounds)
    }

    val targetAspect = if (aspectOption.isOriginal) {
        originalAspectRatio
    } else {
        aspectOption.getAspectRatioValue(imageWidth, imageHeight)
    } ?: return RectF(cropBounds)
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()

    // 裁剪框在归一化坐标中的比例 = targetAspect / imageAspect
    val normalizedAspect = targetAspect / imageAspect

    val cropW: Float
    val cropH: Float

    val boundsAspect = cropBounds.width() / cropBounds.height()
    if (normalizedAspect > boundsAspect) {
        cropW = cropBounds.width()
        cropH = cropW / normalizedAspect
    } else {
        cropH = cropBounds.height()
        cropW = normalizedAspect
            .times(cropH)
    }

    val left = cropBounds.centerX() - cropW / 2f
    val top = cropBounds.centerY() - cropH / 2f

    return RectF(left, top, left + cropW, top + cropH)
}
