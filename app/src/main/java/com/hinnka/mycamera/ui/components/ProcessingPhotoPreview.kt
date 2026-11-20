package com.hinnka.mycamera.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.hinnka.mycamera.gallery.ProcessingPhoto
import com.hinnka.mycamera.ui.camera.autoRotate

/** A capture preview never asks the gallery renderer to read unfinished output files. */
@Composable
fun ProcessingPhotoPreview(photo: ProcessingPhoto, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "processingPhoto")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "processingPhotoSweep"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val preview = photo.thumbnail?.takeUnless { it.isRecycled }
        val imageModifier = Modifier.fillMaxSize().autoRotate(matchParentSize = true)
            .drawWithContent {
                drawContent()
                val distance = size.width + size.height
                val center = sweep * distance
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                        start = Offset(center - distance * 0.22f, 0f),
                        end = Offset(center, size.height * 0.35f)
                    )
                )
            }
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = photo.photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        } else {
            AsyncImage(
                model = photo.photo.thumbnailUri,
                contentDescription = photo.photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        }
        // Labelled by the viewer's processing chip (RenderQueuePhotoChip), so one label says in camera or RAW queue.
    }
}
