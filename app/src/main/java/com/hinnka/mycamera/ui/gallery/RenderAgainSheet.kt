package com.hinnka.mycamera.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.db.GalleryMediaStore
import com.hinnka.mycamera.processor.DenoiseStrength
import com.hinnka.mycamera.raw.MeteringSystem
import com.hinnka.mycamera.raw.RawNoiseProfileInfo
import com.hinnka.mycamera.raw.RawNoiseProfileManager
import com.hinnka.mycamera.render.RenderJob
import com.hinnka.mycamera.render.RenderLook
import com.hinnka.mycamera.ui.components.AppModalBottomSheet
import com.hinnka.mycamera.ui.components.SliderSettingItem
import com.hinnka.mycamera.ui.components.SwitchSettingItem
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.theme.AccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** A photo that can be rendered again, with the look it was rendered with. */
internal data class RenderAgainSource(val metadata: MediaMetadata, val look: RenderLook)

/** The checks of RenderQueue.renderAgain, so the UI offers it only where it works. */
internal suspend fun loadRenderAgainSource(context: Context, photoId: String): RenderAgainSource? =
    withContext(Dispatchers.IO) {
        val job = RenderJob.read(context, photoId)?.takeIf { it.state == RenderJob.State.DONE }
            ?: return@withContext null
        val metadata = GalleryMediaStore.getPhotoData(context, photoId)?.metadata ?: return@withContext null
        if (!GalleryManager.getDngFile(context, photoId).isFile) return@withContext null
        RenderAgainSource(metadata, RenderLook.fromPhoto(metadata, job))
    }

internal fun appRenderLook(context: Context, cameraId: String?): RenderLook = RenderLook.fromPreferences(
    ContentRepository.getInstance(context).userPreferencesRepository.current,
    cameraId,
)

/**
 * Picks the look of a re-rendered copy. [source] is the single photo being re-rendered; null for a
 * batch, which starts from [appLook] and marks no changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenderAgainSheet(
    title: String,
    confirmText: String,
    source: RenderAgainSource?,
    appLook: RenderLook,
    isBusy: Boolean,
    onRender: (RenderLook) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val photoLook = source?.look
    var startFromApp by remember { mutableStateOf(photoLook == null) }
    var look by remember { mutableStateOf(photoLook ?: appLook) }
    var showMore by remember { mutableStateOf(false) }
    val profiles by produceState(emptyList<RawNoiseProfileInfo>()) {
        value = withContext(Dispatchers.IO) {
            ContentRepository.getInstance(context).rawNoiseProfileManager.getAvailableProfiles()
        }
    }
    val noiseModels = (
        listOf(RawNoiseProfileManager.DEFAULT_PROFILE_ID, RawNoiseProfileManager.SYSTEM_PROFILE_ID) +
            profiles.filterNot { it.isBuiltIn }.map { it.id } +
            listOfNotNull(photoLook?.noiseProfileId, appLook.noiseProfileId)
        ).distinct()
    val accent = AccentColor
    fun tintIf(changed: (RenderLook) -> Boolean): Color? = photoLook?.takeIf(changed)?.let { accent }

    AppModalBottomSheet(
        containerColor = GallerySheetSurface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        scrimColor = Color.Black.copy(alpha = 0.64f),
        onDismissRequest = onDismissRequest,
        contentColor = GalleryToolbarContent,
        dragHandle = {
            BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = GalleryToolbarContent.copy(alpha = 0.24f))
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = title,
                    color = GalleryToolbarContent,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.render_again_subtitle),
                    color = GalleryToolbarContent.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = GalleryToolbarContent.copy(alpha = 0.09f))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp),
            ) {
                if (photoLook != null && photoLook != appLook) {
                    RenderAgainSegments(
                        label = stringResource(R.string.render_again_start_from),
                        options = listOf(false, true),
                        selected = startFromApp,
                        optionLabel = {
                            stringResource(if (it) R.string.render_again_app_settings else R.string.render_again_this_photo)
                        },
                        selectedColor = null,
                        onSelect = {
                            startFromApp = it
                            look = if (it) appLook else photoLook
                        },
                    )
                }
                RenderAgainSegments(
                    label = stringResource(R.string.render_again_noise_model),
                    options = noiseModels,
                    selected = look.noiseProfileId,
                    optionLabel = { noiseModelName(context, it, profiles) },
                    selectedColor = tintIf { it.noiseProfileId != look.noiseProfileId },
                    onSelect = { look = look.copy(noiseProfileId = it) },
                )
                SliderSettingItem(
                    title = stringResource(R.string.render_again_noise_reduction),
                    value = look.noiseReduction,
                    valueRange = DenoiseStrength.valueRange,
                    onValueChange = { look = look.copy(noiseReduction = snap(it, 100)) },
                    valueTextFormatter = ::twoDecimals,
                    valueColor = tintIf { twoDecimals(it.noiseReduction) != twoDecimals(look.noiseReduction) },
                )
                SliderSettingItem(
                    title = stringResource(R.string.render_again_colour_noise),
                    value = look.chromaNoiseReduction,
                    valueRange = DenoiseStrength.valueRange,
                    onValueChange = { look = look.copy(chromaNoiseReduction = snap(it, 100)) },
                    valueTextFormatter = ::twoDecimals,
                    valueColor = tintIf { twoDecimals(it.chromaNoiseReduction) != twoDecimals(look.chromaNoiseReduction) },
                )
                SliderSettingItem(
                    title = stringResource(R.string.render_again_sharpening),
                    value = look.sharpening,
                    valueRange = 0f..1f,
                    onValueChange = { look = look.copy(sharpening = snap(it, 100)) },
                    valueTextFormatter = ::twoDecimals,
                    valueColor = tintIf { twoDecimals(it.sharpening) != twoDecimals(look.sharpening) },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showMore = !showMore }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.render_again_more_settings),
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp).rotate(if (showMore) 90f else 0f),
                    )
                }
                if (showMore) {
                    SliderSettingItem(
                        title = stringResource(R.string.render_again_exposure),
                        value = look.rawExposureCompensation,
                        valueRange = MeteringSystem.RAW_EXPOSURE_MIN_EV..MeteringSystem.RAW_EXPOSURE_MAX_EV,
                        onValueChange = { look = look.copy(rawExposureCompensation = snap(it, 10)) },
                        valueTextFormatter = { context.getString(R.string.render_again_exposure_value, signed(it, 1)) },
                        valueColor = tintIf { signed(it.rawExposureCompensation, 1) != signed(look.rawExposureCompensation, 1) },
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.render_again_highlights),
                        value = look.rawHighlightsAdjustment,
                        valueRange = -1f..1f,
                        onValueChange = { look = look.copy(rawHighlightsAdjustment = snap(it, 100)) },
                        valueTextFormatter = { signed(it, 2) },
                        valueColor = tintIf { signed(it.rawHighlightsAdjustment, 2) != signed(look.rawHighlightsAdjustment, 2) },
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.render_again_shadows),
                        value = look.rawShadowsAdjustment,
                        valueRange = -1f..1f,
                        onValueChange = { look = look.copy(rawShadowsAdjustment = snap(it, 100)) },
                        valueTextFormatter = { signed(it, 2) },
                        valueColor = tintIf { signed(it.rawShadowsAdjustment, 2) != signed(look.rawShadowsAdjustment, 2) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.render_again_lens_shading),
                        checked = look.lensShadingCorrection,
                        onCheckedChange = { look = look.copy(lensShadingCorrection = it) },
                        titleColor = tintIf { it.lensShadingCorrection != look.lensShadingCorrection } ?: Color.White,
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.render_again_ultra_hdr),
                        checked = look.ultraHdr,
                        onCheckedChange = { look = look.copy(ultraHdr = it) },
                        titleColor = tintIf { it.ultraHdr != look.ultraHdr } ?: Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.render_again_preview, look.label(context)),
                color = GalleryToolbarContent.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = clipboardText(context, look, source?.metadata, profiles)
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(text, text))
                        Toast.makeText(context, R.string.render_again_settings_copied, Toast.LENGTH_SHORT).show()
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, GalleryToolbarContent.copy(alpha = 0.24f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GalleryToolbarContent),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(stringResource(R.string.render_again_copy_text), fontSize = 14.sp, maxLines = 1)
                }
                Button(
                    onClick = { onRender(look) },
                    enabled = !isBusy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.6f),
                        disabledContentColor = Color.Black,
                    ),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 1.8.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(confirmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> RenderAgainSegments(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    selectedColor: Color?,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Rows of at most three keep each segment wide enough at 360 dp.
            options.chunked(if (options.size == 4) 2 else 3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(GalleryToolbarButton)
                        .padding(3.dp),
                ) {
                    row.forEach { option ->
                        val isSelected = option == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 34.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = optionLabel(option),
                                color = when {
                                    !isSelected -> Color.White.copy(alpha = 0.6f)
                                    else -> selectedColor ?: Color.White
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun noiseModelName(context: Context, id: String, profiles: List<RawNoiseProfileInfo>): String = when (id) {
    RawNoiseProfileManager.XZ1C_PROFILE_ID -> context.getString(R.string.render_again_noise_measured)
    RawNoiseProfileManager.SYSTEM_PROFILE_ID -> context.getString(R.string.render_again_noise_system)
    else -> profiles.firstOrNull { it.id == id }?.let { info -> info.nameResId?.let(context::getString) ?: info.getName() } ?: id
}

private fun clipboardText(
    context: Context,
    look: RenderLook,
    metadata: MediaMetadata?,
    profiles: List<RawNoiseProfileInfo>,
): String {
    fun onOff(on: Boolean) = context.getString(if (on) R.string.render_again_clipboard_on else R.string.render_again_clipboard_off)
    return listOfNotNull(
        context.getString(
            R.string.render_again_clipboard,
            noiseModelName(context, look.noiseProfileId, profiles),
            twoDecimals(look.noiseReduction),
            twoDecimals(look.chromaNoiseReduction),
            twoDecimals(look.sharpening),
            signed(look.rawExposureCompensation, 1),
            signed(look.rawHighlightsAdjustment, 2),
            signed(look.rawShadowsAdjustment, 2),
            onOff(look.lensShadingCorrection),
            onOff(look.ultraHdr),
        ),
        metadata?.iso?.let { context.getString(R.string.render_again_clipboard_iso, it) },
        // Stored like "1/30s".
        metadata?.shutterSpeed?.takeIf { it.isNotBlank() }?.removeSuffix("s")?.let { "$it s" },
    ).joinToString(" · ")
}

private fun snap(value: Float, stepsPerUnit: Int): Float = (value * stepsPerUnit).roundToInt() / stepsPerUnit.toFloat()

private fun twoDecimals(value: Float): String = "%.2f".format(Locale.US, value)

/** Signed, with no sign on zero: "+0.3", "-0.25", "0.0". */
private fun signed(value: Float, decimals: Int): String {
    val text = "%+.${decimals}f".format(Locale.US, value)
    return if (text.drop(1).all { it == '0' || it == '.' }) text.drop(1) else text
}
