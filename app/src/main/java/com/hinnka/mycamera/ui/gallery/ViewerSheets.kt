package com.hinnka.mycamera.ui.gallery

import android.content.Context
import android.graphics.ColorSpace
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.PhotoStacks
import com.hinnka.mycamera.hdr.HdrGainmapStrength
import com.hinnka.mycamera.lut.VideoExportOption
import com.hinnka.mycamera.lut.VideoExportResolution
import com.hinnka.mycamera.lut.VideoExportSupport
import com.hinnka.mycamera.raw.RawPhotonHdrMetadata
import com.hinnka.mycamera.render.RenderJob
import com.hinnka.mycamera.render.RenderLook
import com.hinnka.mycamera.ui.camera.GroupGold
import com.hinnka.mycamera.ui.camera.GroupMagenta
import com.hinnka.mycamera.ui.camera.GroupRed
import com.hinnka.mycamera.ui.components.CustomSlider
import com.hinnka.mycamera.ui.settings.SettingsDim
import com.hinnka.mycamera.ui.settings.SettingsLine
import com.hinnka.mycamera.ui.settings.SettingsQuiet
import com.hinnka.mycamera.ui.settings.SettingsRowDivider
import com.hinnka.mycamera.ui.settings.SettingsRowFrame
import com.hinnka.mycamera.ui.settings.SettingsSheet
import com.hinnka.mycamera.ui.settings.SettingsSwitch
import com.hinnka.mycamera.ui.settings.SettingsText
import com.hinnka.mycamera.ui.settings.rememberSettingsTick
import com.hinnka.mycamera.ui.theme.WarningYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** The More sheet's groups, in the order they show, each with its title colour. */
internal enum class MoreGroup(val color: Color) {
    ITEM(GroupRed),
    LOOK(GroupMagenta),
    // A "Show on the picture" group (gold) goes here, between Look and Export, once it has a row.
    EXPORT(GroupGold),
}

/** One More row; a press while it is not [enabled] or [isLoading] says [whyNot] (or that the viewer is busy). */
internal data class GalleryMoreAction(
    val group: MoreGroup,
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val secondLine: String? = null,
    val enabled: Boolean = true,
    val isLoading: Boolean = false,
    val whyNot: String? = null,
)

/** A sheet group's title: a 3 dp bar in the group's colour and the name in small capitals. */
@Composable
internal fun ViewerGroupHeader(title: String, color: Color, expanded: Boolean? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .padding(bottom = 1.dp)
                .size(3.dp, 11.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            title.uppercase(),
            color = SettingsText,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f),
        )
        if (expanded != null) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = SettingsQuiet,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A Settings row with an icon before it; the whole row takes the press. */
@Composable
internal fun ViewerSheetRow(
    icon: ImageVector?,
    title: String,
    secondLine: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = SettingsText.copy(alpha = if (enabled) 1f else 0.38f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        SettingsRowFrame(title, secondLine, enabled, onClick = null, modifier = Modifier.weight(1f), trailing = trailing)
    }
}

/** A choice with an optional second line; the chosen one is a light fill with black text. */
@Composable
internal fun ViewerOption(
    title: String,
    secondLine: String?,
    chosen: Boolean,
    enabled: Boolean = true,
    secondLineColor: Color = SettingsDim,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (chosen) SettingsText else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = (if (chosen) Color.Black else SettingsText).copy(alpha = if (enabled) 1f else 0.38f), fontSize = 16.sp)
        if (secondLine != null) {
            Text(secondLine, color = if (chosen) Color.Black.copy(alpha = 0.6f) else secondLineColor, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Cancel and the sheet's action, side by side at its foot. */
@Composable
internal fun ViewerSheetButtons(go: String, onCancel: () -> Unit, onGo: () -> Unit, goDimmed: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SheetButton(stringResource(R.string.cancel), SettingsLine, SettingsText, onCancel)
        SheetButton(go, SettingsText.copy(alpha = if (goDimmed) 0.38f else 1f), Color.Black, onGo)
    }
}

@Composable
private fun RowScope.SheetButton(text: String, fill: Color, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ViewerMoreSheet(
    isVideo: Boolean,
    actions: List<GalleryMoreAction>,
    explain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = stringResource(R.string.viewer_busy)
    val tick = rememberSettingsTick()
    SettingsSheet(stringResource(R.string.viewer_more), onDismiss) {
        MoreGroup.entries.forEach { group ->
            val rows = actions.filter { it.group == group }
            if (rows.isEmpty()) return@forEach
            val title = when (group) {
                MoreGroup.ITEM -> stringResource(if (isVideo) R.string.viewer_group_this_video else R.string.viewer_group_this_photo)
                MoreGroup.LOOK -> stringResource(R.string.viewer_group_look)
                MoreGroup.EXPORT -> stringResource(R.string.export)
            }
            ViewerGroupHeader(title, group.color)
            rows.forEachIndexed { i, action ->
                if (i > 0) SettingsRowDivider()
                ViewerSheetRow(
                    icon = action.icon,
                    title = action.text,
                    secondLine = action.secondLine,
                    enabled = action.enabled,
                    onClick = {
                        if (action.enabled && !action.isLoading) {
                            tick()
                            action.onClick()
                        } else {
                            explain(action.whyNot ?: busy)
                        }
                    },
                ) {
                    if (action.isLoading) {
                        CircularProgressIndicator(color = SettingsText, modifier = Modifier.size(18.dp), strokeWidth = 1.8.dp)
                    }
                }
            }
        }
    }
}

/** HDR display: the gain-map switch and its strength; the slider rests while HDR is off. */
@Composable
internal fun ViewerHdrSheet(
    enabled: Boolean,
    strength: Float,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onStrengthChangeFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheet(stringResource(R.string.viewer_hdr_display), onDismiss, stringResource(R.string.viewer_hdr_display_about)) {
        Box(Modifier.padding(horizontal = 8.dp)) {
            SettingsRowFrame(stringResource(R.string.viewer_hdr_show), null, true, onClick = { onEnabledChange(!enabled) }) {
                SettingsSwitch(enabled, onEnabledChange)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hdr_strength_label), color = SettingsText.copy(alpha = if (enabled) 1f else 0.38f), fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${(strength * 100f).roundToInt()} %", color = SettingsDim, fontSize = 14.sp)
        }
        Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            CustomSlider(
                value = strength,
                onValueChange = onStrengthChange,
                valueRange = HdrGainmapStrength.MIN..HdrGainmapStrength.MAX,
                enabled = enabled,
                onValueChangeFinished = onStrengthChangeFinished,
            )
        }
    }
}

/** Asks before a photo (or every burst frame) is exported. */
@Composable
internal fun ViewerExportSheet(allFrames: Boolean, onExport: () -> Unit, onDismiss: () -> Unit) {
    SettingsSheet(
        stringResource(if (allFrames) R.string.viewer_export_all_frames else R.string.export),
        onDismiss,
        stringResource(if (allFrames) R.string.export_all_confirm else R.string.export_confirm),
    ) {
        ViewerSheetButtons(stringResource(R.string.export), onCancel = onDismiss, onGo = onExport)
    }
}

@Composable
private fun videoExportResolutionLabel(resolution: VideoExportResolution): String {
    return stringResource(
        when (resolution) {
            VideoExportResolution.ORIGINAL -> R.string.export_video_resolution_original
            VideoExportResolution.UHD_8K -> R.string.export_video_resolution_8k
            VideoExportResolution.UHD_4K -> R.string.export_video_resolution_4k
            VideoExportResolution.FHD_1080P -> R.string.export_video_resolution_1080p
            VideoExportResolution.HD_720P -> R.string.export_video_resolution_720p
        }
    )
}

/** The video export's size picker; a size that can't be picked says why. */
@Composable
internal fun ViewerExportVideoSheet(
    options: List<VideoExportOption>,
    loading: Boolean,
    exporting: Boolean,
    selected: VideoExportOption?,
    onSelect: (VideoExportOption) -> Unit,
    onExport: (VideoExportOption) -> Unit,
    explain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    SettingsSheet(stringResource(R.string.export_video), onDismiss, stringResource(R.string.export_video_confirm)) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SettingsText)
            }
        } else {
            options.forEach { option ->
                val selectable = option.isSelectable && !exporting
                val status = when (option.support) {
                    VideoExportSupport.SUPPORTED -> null
                    VideoExportSupport.MAY_FAIL -> R.string.export_video_resolution_may_fail
                    VideoExportSupport.UNSUPPORTED -> R.string.export_video_resolution_unsupported
                    VideoExportSupport.SOURCE_TOO_SMALL -> R.string.export_video_resolution_source_too_small
                }
                ViewerOption(
                    title = videoExportResolutionLabel(option.resolution),
                    secondLine = if (status == null) {
                        stringResource(R.string.export_video_resolution_dimensions, option.outputWidth, option.outputHeight)
                    } else {
                        stringResource(R.string.export_video_resolution_details, option.outputWidth, option.outputHeight, stringResource(status))
                    },
                    chosen = selected == option,
                    enabled = selectable,
                    secondLineColor = if (option.support == VideoExportSupport.MAY_FAIL) WarningYellow else SettingsDim,
                ) {
                    when {
                        selectable -> onSelect(option)
                        exporting -> explain(context.getString(R.string.viewer_busy))
                        status != null -> explain(context.getString(status))
                    }
                }
            }
        }
        ViewerSheetButtons(
            stringResource(R.string.export),
            onCancel = onDismiss,
            goDimmed = selected == null || loading,
            onGo = {
                when {
                    loading -> explain(context.getString(R.string.viewer_busy))
                    selected == null -> explain(context.getString(R.string.export_video_select_resolution))
                    else -> onExport(selected)
                }
            },
        )
    }
}

/**
 * Info on the shown item. [metadata] is the capture's (on the system tab, the Lilac photo an exported copy came
 * from); the version, frames and HDR lines read the shown version's own.
 */
@Composable
internal fun ViewerInfoSheet(
    photo: MediaData,
    metadata: MediaMetadata?,
    showName: Boolean,
    fileSize: Long,
    colorSpace: ColorSpace?,
    brightness: Float?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The full look of this version; the badge only shows what sets it apart. The original's look is in its render job.
    val versionInfo by produceState<String?>(null, photo.id) {
        val own = photo.metadata
        value = when {
            PhotoStacks.isCameraJpeg(own) -> context.getString(R.string.photo_info_version_camera_jpeg)
            own?.customProperties?.get(RenderLook.LABEL_PROPERTY) != null ->
                own.customProperties[RenderLook.LABEL_PROPERTY]
            own != null -> withContext(Dispatchers.IO) {
                // Only Pro photos rendered in the queue have a job; reading a missing one logs an error.
                val jobFile = File(RenderJob.photoDir(context, photo.id), RenderJob.JOB_FILE)
                if (jobFile.isFile) RenderJob.read(context, photo.id) else null
            }
                ?.let { job -> withContext(Dispatchers.IO) { context.getString(R.string.photo_info_version_original, RenderLook.fromPhoto(own, job).label(context)) } }
            else -> null
        }
    }
    // What this version's folder really holds, so the sheet never names data that was not kept.
    val savedFiles by produceState<List<File>>(emptyList(), photo.id) {
        value = withContext(Dispatchers.IO) { GalleryManager.savedPhotoFiles(context, photo.id) }
    }
    var savedExpanded by remember(photo.id) { mutableStateOf(false) }
    val na = "N/A"

    SettingsSheet(stringResource(R.string.viewer_info), onDismiss) {
        if (photo.isVideo) {
            metadata?.let {
                ViewerGroupHeader(stringResource(R.string.viewer_group_video), GroupRed)
                InfoRow(stringResource(R.string.video_info_duration), photo.getFormattedDuration())
                InfoRow(stringResource(R.string.video_info_mime), it.mimeType ?: (photo.mimeType ?: na))
                InfoRow(stringResource(R.string.video_info_frame_rate), it.frameRate?.toString() ?: na)
                InfoRow(stringResource(R.string.video_info_bitrate), it.bitrate?.let { bitrate -> "${bitrate / 1000} kbps" } ?: na)
                InfoRow(
                    stringResource(R.string.video_info_has_audio),
                    it.hasAudio?.let { hasAudio -> stringResource(if (hasAudio) R.string.yes else R.string.no) } ?: na
                )
                InfoRow(stringResource(R.string.video_info_rotation), it.rotationDegrees?.toString() ?: na)
            }
        } else {
            val own = photo.metadata
            val frames = own?.multipleExposureFrameCount?.takeIf { it > 1 }
            if (metadata != null || frames != null) ViewerGroupHeader(stringResource(R.string.viewer_group_capture), GroupRed)
            metadata?.let {
                InfoRow(stringResource(R.string.photo_info_aperture), it.aperture ?: na)
                InfoRow(stringResource(R.string.photo_info_shutter_speed), it.shutterSpeed ?: na)
                InfoRow(stringResource(R.string.photo_info_iso), it.iso?.toString() ?: na)
                it.exposureBias?.let { ev -> InfoRow(stringResource(R.string.viewer_info_exposure), "%+.1f EV".format(ev)) }
                InfoRow(stringResource(R.string.photo_info_focal_length), it.focalLength35mm ?: na)
                InfoRow("LV", "%.2f".format(it.lv))
                InfoRow(stringResource(R.string.photo_info_brightness), "%.2f".format(brightness))
            }
            if (frames != null) {
                InfoRow(
                    stringResource(R.string.photo_info_frames),
                    stringResource(
                        if (own?.captureMode == "multiple_exposure") R.string.photo_info_frames_multiple_exposure
                        else R.string.photo_info_frames_stacked,
                        frames,
                    ),
                )
            }
            own?.customProperties?.let { props ->
                val used = RawPhotonHdrMetadata.read(props) ?: return@let
                ViewerGroupHeader(stringResource(R.string.photo_info_hdr, used), GroupMagenta)
                InfoText(stringResource(R.string.photo_info_hdr_used, used))
                RawPhotonHdrMetadata.readWanted(props)?.let { InfoText(stringResource(R.string.photo_info_hdr_wanted, it)) }
                RawPhotonHdrMetadata.readLimit(props)?.let { InfoText(stringResource(R.string.photo_info_hdr_limit, it)) }
                RawPhotonHdrMetadata.readInputExposureEv(props)?.let { InfoText(stringResource(R.string.photo_info_hdr_viewfinder, it)) }
            }
        }
        versionInfo?.let {
            ViewerGroupHeader(stringResource(R.string.photo_info_version), GroupMagenta)
            InfoText(it)
        }

        ViewerGroupHeader(stringResource(R.string.viewer_group_file), GroupGold)
        if (showName) InfoRow(stringResource(R.string.name), photo.displayName)
        InfoRow(stringResource(R.string.photo_info_date), photo.getFormattedDate())
        InfoRow(stringResource(R.string.photo_info_resolution), photo.getResolution())
        InfoRow(stringResource(R.string.photo_info_size), photo.copy(size = fileSize).getFormattedSize())
        val names = savedFiles.map { it.name }
        if (!photo.isVideo) {
            val mainType = when {
                "original.heic" in names -> "HEIC"
                "original.jxl" in names -> "JPEG XL"
                "original.jpg" in names -> "JPEG"
                else -> photo.mimeType?.substringAfter('/')?.uppercase()
            }
            mainType?.let { type ->
                InfoRow(
                    stringResource(R.string.photo_info_file_type),
                    if ("original.dng" in names) stringResource(R.string.photo_info_file_type_from_raw, type) else type,
                )
            }
        }
        colorSpace?.let { InfoRow(stringResource(R.string.color_space), it.name) }

        if (!photo.isVideo) {
            val extras = savedFiles
                .filter { it.name !in MAIN_PHOTO_FILES && !it.name.endsWith(".tmp") }
                .sortedBy { it.name }
            if (extras.isNotEmpty()) {
                ViewerGroupHeader(stringResource(R.string.viewer_group_saved_with), GroupGold, savedExpanded) {
                    savedExpanded = !savedExpanded
                }
                if (savedExpanded) {
                    extras.forEach { file ->
                        InfoRow(savedFileLabel(context, file.name), android.text.format.Formatter.formatShortFileSize(context, file.length()))
                    }
                }
            }
        }
    }
}

private val MAIN_PHOTO_FILES = setOf("original.jpg", "original.heic", "original.jxl")

/** A plain name for a file kept in a photo's folder; unknown files show their file name. */
private fun savedFileLabel(context: Context, name: String): String = when (name) {
    "original.dng" -> context.getString(R.string.photo_info_saved_dng)
    "viewfinder.jpg" -> context.getString(R.string.photo_info_saved_viewfinder)
    "render_job.json" -> context.getString(R.string.photo_info_saved_render_job)
    "thumbnail.jpg" -> context.getString(R.string.photo_info_saved_thumbnail)
    "detail_hdr.jpg" -> context.getString(R.string.photo_info_saved_detail_hdr)
    "original_hdr.bin" -> context.getString(R.string.photo_info_saved_hdr)
    "video.mp4" -> context.getString(R.string.photo_info_saved_video)
    "bokeh.jpg" -> context.getString(R.string.photo_info_saved_bokeh)
    "ai_denoise.jpg" -> context.getString(R.string.photo_info_saved_ai_denoise)
    "preview.jpg" -> context.getString(R.string.photo_info_saved_preview)
    else -> name
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SettingsDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(value, color = SettingsText, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
    SettingsRowDivider()
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        color = SettingsText,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
    SettingsRowDivider()
}
