package com.hinnka.mycamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.icons.AppIcons

/**
 * One searchable row: its name, tab, section, choice labels, and the row to flash instead when
 * this one is drawn inside another ([flashAs]) or only shows while [parent] is on.
 */
internal class SettingsEntry(
    val title: Int,
    val group: SettingsGroup,
    val section: Int?,
    val choices: List<Int> = emptyList(),
    val flashAs: Int? = null,
    val parent: Int? = null,
    val titleArg: Int? = null,
) {
    /** The row's name as drawn, filling in [titleArg] for names like "%1$s Save Path". */
    fun name(context: android.content.Context): String =
        titleArg?.let { context.getString(title, context.getString(it)) } ?: context.getString(title)
}

private val NR_LEVELS = listOf(
    R.string.settings_nr_level_off, R.string.settings_nr_level_fast, R.string.settings_nr_level_high_quality,
    R.string.settings_nr_level_zsl, R.string.settings_nr_level_minimal,
)
private val RENDERING_ENGINES = listOf(
    R.string.settings_raw_color_engine_adobe_curve, R.string.settings_raw_color_engine_agx,
    R.string.settings_raw_color_engine_darktable_sigmoid, R.string.settings_raw_color_engine_darktable_filmic,
    R.string.settings_raw_color_engine_spectral_film, R.string.settings_raw_color_engine_hncs,
    R.string.settings_raw_color_engine_lumix, R.string.settings_raw_color_engine_canon,
    R.string.settings_raw_color_engine_leica, R.string.settings_raw_color_engine_fotufilm,
)

/** Every row of Settings, in page order. Rows hidden on every phone this fork runs on are left out. */
internal val SETTINGS_INDEX: List<SettingsEntry> = buildList {
    fun row(
        group: SettingsGroup, section: Int?, title: Int, choices: List<Int> = emptyList(),
        flashAs: Int? = null, parent: Int? = null, titleArg: Int? = null,
    ) = add(SettingsEntry(title, group, section, choices, flashAs, parent, titleArg))

    SettingsGroup.SHOOTING.let { g ->
        R.string.settings_section_assist.let { s ->
            row(g, s, R.string.settings_grid_lines)
            row(g, s, R.string.settings_grid_style, listOf(
                R.string.grid_style_thirds, R.string.grid_style_diagonals, R.string.grid_style_golden_ratio,
                R.string.grid_style_golden_triangle, R.string.grid_style_golden_spiral,
            ), parent = R.string.settings_grid_lines)
            row(g, s, R.string.settings_level_indicator)
            row(g, s, R.string.settings_focus_peaking)
        }
        R.string.settings_section_capture.let { s ->
            row(g, s, R.string.settings_top_sheet_aspect_ratios)
            row(g, s, R.string.settings_mirror_front_camera)
            row(g, s, R.string.settings_multiple_exposure_count)
        }
        R.string.settings_group_storage.let { s ->
            row(g, s, R.string.settings_photo_quality)
            row(g, s, R.string.settings_use_heic_export)
            row(g, s, R.string.settings_use_jpeg_444_export)
            row(g, s, R.string.settings_storage_path_title, titleArg = R.string.settings_storage_path_photo_title_arg)
            row(g, s, R.string.settings_auto_save)
            row(g, s, R.string.settings_save_location)
        }
    }
    SettingsGroup.VIDEO.let { g ->
        R.string.settings_group_image.let { s ->
            row(g, s, R.string.settings_nr_level, NR_LEVELS)
            row(g, s, R.string.settings_edge_level, NR_LEVELS.dropLast(1))
        }
        R.string.settings_group_stabilisation.let { s ->
            row(g, s, R.string.settings_video_stabilization_default, listOf(
                R.string.settings_video_stabilization_mode_off, R.string.settings_video_stabilization_mode_eis,
                R.string.settings_video_stabilization_mode_ois, R.string.settings_video_stabilization_mode_enhanced,
                R.string.video_stabilise_steady, R.string.video_stabilise_active,
            ))
            row(g, s, R.string.settings_oppo_super_stabilization)
            row(g, s, R.string.settings_video_stabilization_strength)
            row(g, s, R.string.settings_video_stabilization_lookahead)
            row(g, s, R.string.settings_external_lens_stabilization)
        }
        R.string.settings_group_recording.let { s ->
            row(g, s, R.string.settings_separate_video_lut)
            row(g, s, R.string.settings_video_lut, parent = R.string.settings_separate_video_lut)
            row(g, s, R.string.settings_video_lock_lens)
            row(g, s, R.string.settings_video_lock_white_balance)
            row(g, s, R.string.settings_storage_path_title, titleArg = R.string.settings_storage_path_video_title_arg)
        }
    }
    SettingsGroup.PHOTO.let { g ->
        row(g, null, R.string.settings_multi_frame_denoise_frame_count)
        row(g, null, R.string.settings_jpg_multi_frame_denoise_output_scale)
        row(g, null, R.string.settings_nr_level, NR_LEVELS)
        row(g, null, R.string.settings_edge_level, NR_LEVELS.dropLast(1))
        row(g, null, R.string.quick_bracketing)
        row(g, null, R.string.settings_use_p010)
        row(g, null, R.string.settings_use_p3_color_space)
    }
    SettingsGroup.PRO.let { g ->
        R.string.settings_professional_group_max_hdr.let { s ->
            row(g, s, R.string.settings_raw_max_spatial_mode,
                listOf(R.string.settings_raw_max_mode_spatial, R.string.settings_raw_max_mode_sabre))
            row(g, s, R.string.settings_hdr_plus_frame_count)
            row(g, s, R.string.settings_hdr_plus_bracket_exposure)
            row(g, s, R.string.settings_hdr_plus_long_frame_ev, parent = R.string.settings_hdr_plus_bracket_exposure)
            row(g, s, R.string.settings_hdr_plus_short_frame_ev, parent = R.string.settings_hdr_plus_bracket_exposure)
            row(g, s, R.string.settings_ml_ae_max_hdr_ratio)
            row(g, s, R.string.settings_raw_min_shutter_speed)
        }
        R.string.settings_professional_group_image_quality.let { s ->
            row(g, s, R.string.settings_raw_color_calibration_mode, listOf(
                R.string.settings_raw_color_calibration_mode_auto, R.string.settings_raw_color_calibration_mode_dng_default,
                R.string.settings_raw_color_calibration_mode_built_in,
            ))
            row(g, s, R.string.settings_raw_digital_zoom_resampling)
            row(g, s, R.string.settings_raw_output_upscale_mode,
                listOf(R.string.settings_raw_output_upscale_mode_lanczos, R.string.settings_raw_output_upscale_mode_raisr))
            row(g, s, R.string.settings_raw_max_output_scale)
            row(g, s, R.string.settings_raw_max_default_sharpening)
            row(g, s, R.string.raw_noise_profile_title)
            row(g, s, R.string.settings_raw_max_default_luma_denoise)
            row(g, s, R.string.settings_raw_max_default_chroma_denoise)
        }
        R.string.settings_raw_group_development.let { s ->
            // Drawn by the shared rendering panel, so a hit flashes the whole block.
            row(g, s, R.string.settings_raw_color_engine, RENDERING_ENGINES, flashAs = s)
            row(g, s, R.string.settings_raw_exposure_compensation, flashAs = s)
            row(g, s, R.string.settings_raw_highlights_adjustment, flashAs = s)
            row(g, s, R.string.settings_raw_shadows_adjustment, flashAs = s)
            row(g, s, R.string.settings_raw_blacks_adjustment, flashAs = s)
            row(g, s, R.string.settings_raw_whites_adjustment, flashAs = s)
            row(g, s, R.string.settings_baseline_raw_title, flashAs = s)
        }
        R.string.settings_raw_group_capture_output.let { s ->
            row(g, s, R.string.settings_ultra_hdr_gain_map)
            row(g, s, R.string.settings_export_dng_with_raw_export,
                listOf(R.string.settings_professional_export_jpg, R.string.settings_professional_export_raw_jpg))
        }
        R.string.settings_raw_group_sensor_correction.let { s ->
            row(g, s, R.string.settings_raw_lens_shading_correction)
            row(g, s, R.string.settings_raw_dng_metadata_corrections)
        }
    }
    SettingsGroup.LENS.let { g ->
        row(g, R.string.settings_focus_lens_group_autofocus, R.string.settings_eye_focus)
        R.string.settings_group_lenses.let { s ->
            row(g, s, R.string.settings_default_focal_length)
            row(g, s, R.string.settings_main_camera_id)
            row(g, s, R.string.settings_macro_camera_id)
            row(g, s, R.string.settings_camera_orientation)
        }
        R.string.settings_focus_lens_group_virtual_lens_depth.let { s ->
            row(g, s, R.string.depth_model_download_title)
            row(g, s, R.string.settings_add_isz_lens)
            row(g, s, R.string.settings_default_virtual_aperture)
        }
        R.string.settings_focus_lens_group_lens_discovery.let { s ->
            row(g, s, R.string.settings_logical_multi_camera_discovery)
            row(g, s, R.string.settings_logical_camera_binding_whitelist)
            row(g, s, R.string.settings_custom_lens_ids)
            row(g, s, R.string.settings_lens_id_blacklist)
            row(g, s, R.string.settings_custom_vendor_keys)
        }
    }
    SettingsGroup.SETUP.let { g ->
        R.string.settings_group_interface.let { s ->
            row(g, s, R.string.settings_zoom_control, listOf(
                R.string.settings_zoom_control_thumbwheel, R.string.settings_zoom_control_pill, R.string.settings_zoom_control_collar,
            ))
            row(g, s, R.string.settings_language)
            row(g, s, R.string.settings_background)
            row(g, s, R.string.settings_accent_color)
            row(g, s, R.string.settings_panel_group_titles)
            row(g, s, R.string.settings_capture_button)
            row(g, s, R.string.settings_widget_theme, listOf(
                R.string.settings_widget_theme_system, R.string.settings_widget_theme_light, R.string.settings_widget_theme_dark,
            ))
            row(g, s, R.string.settings_auto_rotate)
            row(g, s, R.string.settings_color_palette)
            row(g, s, R.string.settings_develop_animation)
            row(g, s, R.string.settings_animation_style, listOf(
                R.string.settings_develop_animation_style_film, R.string.settings_develop_animation_style_instant_print,
            ), parent = R.string.settings_develop_animation)
        }
        R.string.settings_group_controls.let { s ->
            row(g, s, R.string.settings_retain_capture_settings)
            row(g, s, R.string.settings_shutter_sound)
            row(g, s, R.string.settings_shutter_sound_single, parent = R.string.settings_shutter_sound)
            row(g, s, R.string.settings_shutter_sound_burst, flashAs = R.string.settings_shutter_sound_single,
                parent = R.string.settings_shutter_sound)
            row(g, s, R.string.settings_vibration)
            row(g, s, R.string.settings_keep_screen_on)
            row(g, s, R.string.settings_window_screen_brightness)
            row(g, s, R.string.settings_screen_hdr)
            row(g, s, R.string.settings_volume_key_action, listOf(
                R.string.settings_volume_key_action_none, R.string.settings_volume_key_action_capture,
                R.string.settings_volume_key_action_exposure, R.string.settings_volume_key_action_zoom,
            ))
        }
        R.string.settings_group_content.let { s ->
            row(g, s, R.string.settings_filter_management)
            row(g, s, R.string.settings_frame_management)
            row(g, s, R.string.settings_preset_management)
        }
        R.string.ai_service.let { s ->
            row(g, s, R.string.settings_show_lut_creator)
            row(g, s, R.string.settings_openai_api_key)
            row(g, s, R.string.settings_openai_base_url)
            row(g, s, R.string.settings_ai_model)
        }
        R.string.settings_group_backup.let { s ->
            row(g, s, R.string.settings_backup_settings)
            row(g, s, R.string.settings_restore_settings)
        }
        R.string.settings_section_help_about.let { s ->
            row(g, s, R.string.settings_log_viewer)
            row(g, s, R.string.settings_check_update)
            row(g, s, R.string.settings_report_problem)
            row(g, s, R.string.settings_credit)
        }
    }
}

/**
 * The Search tab: a field just above the tab bar, with matching rows stacked above it.
 * [hidden] holds rows this phone doesn't show; [offParents] the switches that are off, whose
 * nested rows are hidden, so a hit on such a row flashes its switch. [onOpen] gets the tab and
 * the name of the row to flash.
 */
@Composable
internal fun SettingsSearchPage(
    hidden: Set<Int>,
    offParents: Set<Int>,
    onOpen: (SettingsGroup, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    // The app's window pans for the keyboard, which would shift this page up on top of its own
    // keyboard padding; while Search is open the window resizes instead.
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(window) {
        val before = window?.attributes?.softInputMode
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (before != null) window.setSoftInputMode(before) }
    }
    class Hit(val entry: SettingsEntry, val name: String, val place: String, val text: String)
    val all = remember(context, hidden) {
        SETTINGS_INDEX.filter { it.title !in hidden }.map { e ->
            val name = e.name(context)
            val section = e.section?.let { context.getString(it) }
            val place = listOfNotNull(context.getString(e.group.title), section).joinToString(" › ")
            Hit(e, name, place, (listOfNotNull(name, section) + e.choices.map { context.getString(it) }).joinToString(" ").lowercase())
        }
    }
    val needle = query.trim().lowercase()
    val hits = if (needle.isEmpty()) emptyList() else all.filter { needle in it.text }

    Column(modifier.imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), reverseScrolling = true)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (hits.isEmpty()) {
                Text(
                    stringResource(if (needle.isEmpty()) R.string.settings_search_hint else R.string.settings_search_none),
                    color = SettingsDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            hits.forEach { hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable {
                            keyboard?.hide()
                            val e = hit.entry
                            val target = if (e.parent != null && e.parent in offParents) e.parent else e.flashAs
                            onOpen(e.group, target?.let { context.getString(it) } ?: e.name(context))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .padding(vertical = 14.dp)
                            .background(hit.entry.group.color)
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hit.name, color = SettingsText, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hit.place, color = SettingsDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SettingsRowDivider()
            }
        }
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SettingsLine)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.SearchLine, contentDescription = null, tint = SettingsQuiet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.settings_search_placeholder), color = SettingsQuiet, fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SettingsText, fontSize = 16.sp),
                    cursorBrush = SolidColor(SettingsText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    AppIcons.CloseLine,
                    contentDescription = null,
                    tint = SettingsQuiet,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { query = "" }
                        .padding(14.dp),
                )
            }
        }
    }
}
