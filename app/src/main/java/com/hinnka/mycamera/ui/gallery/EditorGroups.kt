package com.hinnka.mycamera.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.RecipeParam
import com.hinnka.mycamera.ui.components.fromDisplayValue
import com.hinnka.mycamera.ui.components.getParamColor
import com.hinnka.mycamera.ui.components.toDisplayValue
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.lut.CurveUtils
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.ui.camera.ModeAmber
import com.hinnka.mycamera.ui.components.CurveChannel
import com.hinnka.mycamera.ui.components.LilacDim
import com.hinnka.mycamera.ui.components.getCurvePoints
import com.hinnka.mycamera.ui.components.identityPoints
import com.hinnka.mycamera.ui.components.parsePoints
import com.hinnka.mycamera.ui.components.toFloatArray

/**
 * Curve: the curve's box on screen, the visible part of the photo inside [free] (clear of the edge icons and the
 * control layer) with a margin, so every point can be reached and none sits on the screen edge.
 */
internal fun curveBox(contentBounds: Rect?, free: Rect, inset: Float): Rect? {
    val b = contentBounds?.takeIf { it.width > 0f && it.height > 0f } ?: return null
    val visible = b.intersect(free)
    val box = Rect(visible.left + inset, visible.top + inset, visible.right - inset, visible.bottom - inset)
    return box.takeIf { it.width > 1f && it.height > 1f }
}

internal fun curvePoints(recipe: ColorRecipeParams, channel: CurveChannel): List<Pair<Float, Float>> =
    parsePoints(recipe.getCurvePoints(channel) ?: identityPoints())

/** The recipe with one channel's curve; a straight line is stored as no curve. */
internal fun ColorRecipeParams.withCurve(channel: CurveChannel, points: List<Pair<Float, Float>>?): ColorRecipeParams {
    val array = points?.toFloatArray()?.takeUnless { points.size <= 2 && CurveUtils.isIdentityCurve(it) }
    return when (channel) {
        CurveChannel.MASTER -> copy(masterCurvePoints = array)
        CurveChannel.RED -> copy(redCurvePoints = array)
        CurveChannel.GREEN -> copy(greenCurvePoints = array)
        CurveChannel.BLUE -> copy(blueCurvePoints = array)
    }
}

/** A channel's curve when the editor opened, from the "recipe.curves" snapshot (master, red, green, blue). */
internal fun openCurve(openCurves: Any?, channel: CurveChannel): List<Pair<Float, Float>>? =
    ((openCurves as? List<*>)?.getOrNull(channel.ordinal) as? List<*>)
        ?.map { it as Float }?.toFloatArray()?.let(::parsePoints)

/** The nearest point within [radius] of [position], or -1. */
internal fun nearestCurvePoint(points: List<Pair<Float, Float>>, box: Rect, position: Offset, radius: Float): Int {
    var best = -1
    var bestDistance = radius
    points.forEachIndexed { i, (x, y) ->
        val d = (Offset(box.left + x * box.width, box.top + (1f - y) * box.height) - position).getDistance()
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

/** The curve on the photo: grid, the straight line, faint other channels, the active curve and its points. */
@Composable
internal fun CurveOverlay(
    contentBounds: Rect?,
    free: Rect,
    points: List<Pair<Float, Float>>,
    channel: CurveChannel,
    others: List<Pair<CurveChannel, FloatArray>>,
    selected: Int?,
) {
    Canvas(Modifier.fillMaxSize()) {
        val box = curveBox(contentBounds, free, CURVE_INSET_DP.dp.toPx()) ?: return@Canvas
        val shade = Color.Black.copy(alpha = 0.45f)
        val grid = Color.White.copy(alpha = 0.18f)
        for (i in 1..3) {
            val x = box.left + box.width * i / 4f
            val y = box.top + box.height * i / 4f
            drawLine(grid, Offset(x, box.top), Offset(x, box.bottom), strokeWidth = 1f)
            drawLine(grid, Offset(box.left, y), Offset(box.right, y), strokeWidth = 1f)
        }
        drawRect(Color.White.copy(alpha = 0.3f), box.topLeft, box.size, style = Stroke(1f))
        drawLine(
            Color.White.copy(alpha = 0.35f), Offset(box.left, box.bottom), Offset(box.right, box.top), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )
        others.forEach { (c, p) -> drawCurve(p, box, channelColor(c).copy(alpha = 0.4f), 1.5.dp.toPx(), shade) }
        val color = channelColor(channel)
        drawCurve(points.toFloatArray(), box, color, 2.dp.toPx(), shade)
        val radius = 7.dp.toPx()
        points.forEachIndexed { i, (x, y) ->
            val center = Offset(box.left + x * box.width, box.top + (1f - y) * box.height)
            drawCircle(Color(0xFF141413), radius, center)
            drawCircle(if (i == selected) ModeAmber else color, radius, center, style = Stroke(2.dp.toPx()))
            if (i == selected) drawCircle(ModeAmber, radius - 4.dp.toPx(), center)
        }
    }
}

private fun DrawScope.drawCurve(points: FloatArray, box: Rect, color: Color, width: Float, shade: Color) {
    val lut = CurveUtils.evaluateCurve(points)
    val path = Path()
    for (i in 0 until CurveUtils.LUT_SIZE) {
        val x = box.left + i / (CurveUtils.LUT_SIZE - 1f) * box.width
        val y = box.top + (1f - lut[i]) * box.height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    // A dark edge under the line keeps it readable on a light photo.
    drawPath(path, shade, style = Stroke(width = width + 2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

private fun channelColor(channel: CurveChannel): Color =
    if (channel == CurveChannel.MASTER) Color.White else channel.color

/** The channel picker in Curve's path row: All, R, G, B in their colours, each with a dot once changed. */
@Composable
internal fun CurveChannelChip(label: String, channel: CurveChannel, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    val color = when {
        selected -> channelColor(channel)
        channel == CurveChannel.MASTER -> LilacDim
        else -> channel.color.copy(alpha = 0.6f)
    }
    GroupPickChip(label, color, selected, changed, onClick)
}

/** One choice in a group's path row (a channel, a range), with a dot once anything under it changed. */
@Composable
internal fun GroupPickChip(label: String, color: Color, selected: Boolean, changed: Boolean, onClick: () -> Unit, width: Int = 38) {
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(32.dp)
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 14.sp, maxLines = 1)
        if (changed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 3.dp)
                    .size(5.dp)
                    .background(ModeAmber, CircleShape)
            )
        }
    }
}

/** Mixer's colour picker: a dot in the colour, ringed when picked, with a change dot. */
@Composable
internal fun ColorDotChip(color: Color, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(32.dp)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(if (selected) 20.dp else 14.dp)
                .background(color, CircleShape)
                .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
        )
        if (changed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(5.dp)
                    .background(ModeAmber, CircleShape)
            )
        }
    }
}

/**
 * A number inside Mixer, Grading or Calibration, on the ruler. [key] is the kind (Hue, Amount...), so the selected
 * kind stays when another colour or range is picked; the opening value is [openKey] in the snapshot, or its
 * [openIndex] when the key holds a list.
 */
internal class GroupValue(
    val key: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val explanation: Int,
    val steps: List<Float>,
    val get: (ColorRecipeParams) -> Float,
    val set: (ColorRecipeParams, Float) -> ColorRecipeParams,
    val display: (Float) -> Int,
    val signed: Boolean,
    val default: Float,
    val openKey: String,
    val openIndex: Int? = null,
    /** A hue: the ruler shows the colour under each tick, and the value reads in degrees. */
    val hue: Boolean = false,
) {
    fun text(value: Float): String {
        val n = display(value)
        return when {
            hue -> "$n°"
            signed && n > 0 -> "+$n"
            signed && n < 0 -> "−${-n}"
            else -> "$n"
        }
    }

    fun tick(value: Float): String = text(value).removePrefix("+")

    fun open(snapshotValue: Any?): Float =
        (if (openIndex != null) (snapshotValue as? List<*>)?.getOrNull(openIndex) else snapshotValue) as? Float ?: default
}

private fun paramValue(key: String, param: RecipeParam, @StringRes label: Int, icon: ImageVector, @StringRes explanation: Int) =
    GroupValue(
        key, label, icon, explanation,
        steps = (-10..10).map { param.fromDisplayValue(it.toFloat()) },
        get = param::getValue,
        set = { r, v -> param.setValue(r, v) },
        display = { param.toDisplayValue(it).roundToInt() },
        signed = true,
        default = param.defaultValue,
        openKey = "recipe.${param.name}",
    )

/** Mixer's colours: the recipe's prefix, its name, and its colour. */
internal val MixerColors = listOf(
    "SKIN" to R.string.recipe_lch_skin, "RED" to R.string.recipe_lch_red, "ORANGE" to R.string.recipe_lch_orange,
    "YELLOW" to R.string.recipe_lch_yellow, "GREEN" to R.string.recipe_lch_green, "CYAN" to R.string.recipe_lch_cyan,
    "BLUE" to R.string.recipe_lch_blue, "PURPLE" to R.string.recipe_lch_purple, "MAGENTA" to R.string.recipe_lch_magenta,
)

internal fun mixerColor(index: Int): Color = getParamColor(RecipeParam.valueOf("${MixerColors[index].first}_HUE"))

internal fun mixerValues(index: Int): List<GroupValue> {
    val c = MixerColors[index].first
    return listOf(
        paramValue("hue", RecipeParam.valueOf("${c}_HUE"), R.string.editor_hue, AppIcons.PaletteLine, R.string.editor_explain_mixer_hue),
        paramValue("chroma", RecipeParam.valueOf("${c}_CHROMA"), R.string.editor_chroma, AppIcons.DropletLine, R.string.editor_explain_mixer_chroma),
        paramValue("light", RecipeParam.valueOf("${c}_LIGHTNESS"), R.string.editor_light, AppIcons.SunLine, R.string.editor_explain_mixer_light),
    )
}

/** Calibration's primaries (its Lightness has never had a control). */
internal val CalibrationChannels = listOf(
    Triple("RED", "R", R.string.recipe_lch_red), Triple("GREEN", "G", R.string.recipe_lch_green),
    Triple("BLUE", "B", R.string.recipe_lch_blue),
)

internal fun calibrationColor(index: Int): Color =
    getParamColor(RecipeParam.valueOf("PRIMARY_${CalibrationChannels[index].first}_HUE"))

internal fun calibrationValues(index: Int): List<GroupValue> {
    val c = CalibrationChannels[index].first
    return listOf(
        paramValue("hue", RecipeParam.valueOf("PRIMARY_${c}_HUE"), R.string.editor_hue, AppIcons.PaletteLine, R.string.editor_explain_calibration_hue),
        paramValue("saturation", RecipeParam.valueOf("PRIMARY_${c}_SATURATION"), R.string.recipe_param_saturation, AppIcons.DropletLine, R.string.editor_explain_calibration_saturation),
    )
}

/** Grading's ranges, by their place in the "recipe.grading" snapshot list. */
internal val GradingRanges = listOf(
    R.string.recipe_param_shadows, R.string.editor_mid, R.string.editor_high, R.string.editor_global,
)

private fun ColorRecipeParams.gradingField(i: Int): Float = when (i) {
    0 -> gradingShadowHue; 1 -> gradingShadowAmount; 2 -> gradingShadowLuminance
    3 -> gradingMidtoneHue; 4 -> gradingMidtoneAmount; 5 -> gradingMidtoneLuminance
    6 -> gradingHighlightHue; 7 -> gradingHighlightAmount; 8 -> gradingHighlightLuminance
    9 -> gradingBalance
    else -> gradingBlending
}

private fun ColorRecipeParams.withGradingField(i: Int, v: Float): ColorRecipeParams = when (i) {
    0 -> copy(gradingShadowHue = v); 1 -> copy(gradingShadowAmount = v); 2 -> copy(gradingShadowLuminance = v)
    3 -> copy(gradingMidtoneHue = v); 4 -> copy(gradingMidtoneAmount = v); 5 -> copy(gradingMidtoneLuminance = v)
    6 -> copy(gradingHighlightHue = v); 7 -> copy(gradingHighlightAmount = v); 8 -> copy(gradingHighlightLuminance = v)
    9 -> copy(gradingBalance = v)
    else -> copy(gradingBlending = v)
}

private fun gradingValue(
    key: String, i: Int, @StringRes label: Int, icon: ImageVector, @StringRes explanation: Int,
    signed: Boolean, default: Float = 0f, hue: Boolean = false,
) = GroupValue(
    key, label, icon, explanation,
    // Hue in 5° steps round the colour circle; the rest on the app's 0..10 or −10..10 scale.
    steps = when {
        hue -> (0..355 step 5).map { it / 360f }
        signed -> (-10..10).map { it / 10f }
        else -> (0..10).map { it / 10f }
    },
    get = { it.gradingField(i) },
    set = { r, v -> r.withGradingField(i, v) },
    display = { if (hue) (it * 360f).roundToInt() else (it * 10f).roundToInt() },
    signed = signed,
    default = default,
    openKey = "recipe.grading",
    openIndex = i,
    hue = hue,
)

internal fun gradingValues(range: Int): List<GroupValue> =
    if (range == 3) listOf(
        gradingValue("balance", 9, R.string.recipe_grading_balance, AppIcons.CircleHalfLine, R.string.editor_explain_grading_balance, signed = true),
        gradingValue("blending", 10, R.string.recipe_grading_blending, AppIcons.BlurLine, R.string.editor_explain_grading_blending, signed = false, default = 0.5f),
    ) else listOf(
        gradingValue("hue", range * 3, R.string.editor_hue, AppIcons.PaletteLine, R.string.editor_explain_grading_hue, signed = false, hue = true),
        gradingValue("amount", range * 3 + 1, R.string.editor_amount, AppIcons.DropletLine, R.string.editor_explain_grading_amount, signed = false),
        gradingValue("luminance", range * 3 + 2, R.string.recipe_grading_luminance, AppIcons.SunLine, R.string.editor_explain_grading_luminance, signed = true),
    )

/** The colour of a hue value (0..1), for the ruler's band. */
internal fun hueColor(value: Float): Color = Color.hsv((value * 360f).mod(360f), 0.78f, 1f)

internal const val CURVE_INSET_DP = 14f
internal const val CURVE_GRAB_DP = 30f
