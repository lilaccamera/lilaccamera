package com.hinnka.mycamera.ui.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.hinnka.mycamera.R
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.ui.components.PhysicalButton
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "CaptureForCaller"

/**
 * Another app asked for a photo. The camera screen then shows only Close, the shutter with its zoom
 * collar and the camera switch, and takes one photo at a time.
 */
class CaptureForCaller(val close: () -> Unit) {
    /** From the shutter press until the photo is on the check screen or was retaken. */
    var busy by mutableStateOf(false)
}

val LocalCaptureForCaller = staticCompositionLocalOf<CaptureForCaller?> { null }

/** Waits for the photo, shows it with Retake and Use, and hands it to the app that asked. */
@Composable
fun CaptureForCallerFlow(viewModel: CameraViewModel, request: CaptureForCaller) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var shot by remember { mutableStateOf<Pair<String, File>?>(null) }
    var rendering by remember { mutableStateOf(false) }

    fun discard(photoId: String, file: File?) = scope.launch(NonCancellable) {
        file?.delete()
        // The photo belongs to the app that asked; it stays neither in this gallery nor in the system's.
        val context = activity.applicationContext
        GalleryManager.loadMetadata(context, photoId)?.exportedUris?.forEach { exported ->
            runCatching { context.contentResolver.delete(Uri.parse(exported), null, null) }
        }
        GalleryManager.deletePhoto(context, photoId)
    }

    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect { photoId ->
            photoId ?: return@collect
            // A second photo from the camera key while the first is being prepared or checked.
            if (rendering || shot != null) {
                discard(photoId, null)
                return@collect
            }
            rendering = true
            val file = renderForCaller(activity.applicationContext, photoId)
            rendering = false
            if (file != null) {
                shot = photoId to file
            } else {
                discard(photoId, null)
                request.busy = false
            }
        }
    }

    shot?.let { (photoId, file) ->
        CaptureReview(
            file = file,
            onRetake = {
                shot = null
                request.busy = false
                discard(photoId, file)
            },
            onUse = {
                val results = resultsFor(activity, file)
                scope.launch {
                    discard(photoId, file).join()
                    finishWith(activity, results)
                }
            }
        )
    }
}

/** The saved photo with its look, as a JPEG in the cache: the same steps as sharing a photo. */
private suspend fun renderForCaller(context: Context, photoId: String): File? = withContext(Dispatchers.IO) {
    try {
        val repository = ContentRepository.getInstance(context)
        val metadata = GalleryManager.loadMetadata(context, photoId) ?: MediaMetadata()
        val bitmap = repository.photoProcessor.process(context, photoId, metadata) ?: return@withContext null
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "capture_$photoId.jpg")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, repository.userPreferencesRepository.latest().photoQuality, it)
        }
        bitmap.recycle()
        file
    } catch (e: Exception) {
        PLog.e(TAG, "Failed to prepare the photo for the calling app", e)
        null
    }
}

/**
 * Only a content URI of another app is written to: the calling app names the target, and a file path
 * or one of this app's own providers would let it overwrite this app's files.
 */
internal fun isSafeCaptureOutput(uri: Uri, ownPackage: String): Boolean =
    uri.scheme == "content" && uri.authority?.startsWith(ownPackage) == false

/** The results to try, best first; null stands for "cancelled". Writes the photo if the caller named a target. */
private fun resultsFor(activity: Activity, file: File): List<Intent?> {
    val output = IntentCompat.getParcelableExtra(activity.intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)
    return try {
        if (output != null) {
            require(isSafeCaptureOutput(output, activity.packageName)) { "Refused output target $output" }
            activity.contentResolver.openOutputStream(output, "wt")!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            listOf(Intent())
        } else {
            // No target named: the capture contract is a small picture in "data".
            listOfNotNull(
                decodeSampled(file, 320),
                decodeSampled(file, 96)?.copy(Bitmap.Config.RGB_565, false),
            ).map { Intent().putExtra("data", it) } + null
        }
    } catch (e: Exception) {
        PLog.e(TAG, "Failed to hand the photo to the calling app", e)
        listOf(null)
    }
}

/** Finishing is what sends the result, and it throws for a picture the system will not carry: the next one follows. */
private fun finishWith(activity: Activity, results: List<Intent?>) {
    for (result in results) {
        try {
            activity.setResult(if (result != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
            activity.finish()
            return
        } catch (e: RuntimeException) {
            PLog.w(TAG, "Result refused, trying a smaller one", e)
        }
    }
}

private fun decodeSampled(file: File, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun CaptureReview(file: File, onRetake: () -> Unit, onUse: () -> Unit) {
    val picture = remember(file) { decodeSampled(file, 1280)?.asImageBitmap() }
    BackHandler(onBack = onRetake)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Covers the camera: presses must not reach it.
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
    ) {
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            )
        }
        // The camera footer's places: Retake where Close was, Use where the shutter was.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = CameraControlsLayoutDefaults.BottomPadding)
                .height(CameraControlsLayoutDefaults.ModeZoneHeight)
        ) {
            FramedFooterButton(
                onClick = onRetake,
                icon = AppIcons.RotateClockwiseLine,
                contentDescription = stringResource(R.string.capture_retake),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = CameraControlsLayoutDefaults.SideMargin)
            )
            val line = CameraControlsLayoutDefaults.LineWidth
            PhysicalButton(
                onClick = onUse,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(CameraControlsLayoutDefaults.CaptureButtonSize)
                    .border(line, Color.White, CircleShape)
                    .padding(line + CameraControlsLayoutDefaults.LineGap),
                shape = CircleShape,
                backgroundColor = Color.White
            ) {
                Icon(
                    AppIcons.CheckLine,
                    contentDescription = stringResource(R.string.capture_use_photo),
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp).autoRotate()
                )
            }
        }
    }
}
