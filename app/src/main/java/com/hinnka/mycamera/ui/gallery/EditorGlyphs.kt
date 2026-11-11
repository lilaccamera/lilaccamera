package com.hinnka.mycamera.ui.gallery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Icons drawn from the value they stand for, so they explain the setting and follow it while the ruler moves.
 * The tone glyphs are a curve in a box, with the part of the tones the setting moves shaded; at 0 each still
 * shows its shaded zone, so it explains itself before it is touched.
 */
internal enum class EditorGlyph {
    Contrast, Highlights, Shadows, Whites, Blacks, Tonality, Fade, Vignette, Sharpness, Bloom, Halation, SoftLight,
    Temp, Tint, Saturation, Vibrance, Clarity, NoiseReduction, ColorNoise, AiDenoise
}

private const val BOX = "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12a3 3 0 0 1 3 -3z"
private const val STROKE = 1.75f
private const val ZONE_ALPHA = 0.4f
private const val LINE_STROKE = 2f

private fun f1(x: Float) = String.format(Locale.ROOT, "%.1f", x)

// The curve stays inside the box.
private fun cl(x: Float) = f1(x.coerceIn(1.5f, 22.5f))

private fun zone(x: Float, w: Float) = "M${f1(x)} 3h${f1(w)}v18h${f1(-w)}z"

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${f1(cx - r)} ${f1(cy)}a${f1(r)} ${f1(r)} 0 1 0 ${f1(2 * r)} 0a${f1(r)} ${f1(r)} 0 1 0 ${f1(-2 * r)} 0"

// A four-pointed sparkle centred on (cx, cy), reaching h each way.
private fun sparkle(cx: Float, cy: Float, h: Float): String {
    val a = "a${f1(h)} ${f1(h)} 0 0 0"
    return "M${f1(cx)} ${f1(cy - h)}$a ${f1(h)} ${f1(h)}$a ${f1(-h)} ${f1(h)}$a ${f1(-h)} ${f1(-h)}$a ${f1(h)} ${f1(-h)}z"
}

private const val DROPLET = "M7.502 19.423c2.602 2.105 6.395 2.105 8.996 0c2.602 -2.105 3.262 -5.708 1.566 -8.546" +
    "l-4.89 -7.26c-.42 -.625 -1.287 -.803 -1.936 -.397a1.376 1.376 0 0 0 -.41 .397l-4.893 7.26" +
    "c-1.695 2.838 -1.035 6.441 1.567 8.546z"

/**
 * The glyph for [k], the value as a fraction of the far end of its range on its own side (−1 to 1), drawn in
 * [color]. The colour is part of the image: Fade darkens with black, which a tint would turn into [color].
 */
internal fun editorGlyph(glyph: EditorGlyph, k: Float, color: Color): ImageVector {
    val k1 = k.coerceIn(-1f, 1f)
    val b = ImageVector.Builder(
        name = "EditorGlyph.$glyph",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    fun fill(path: String, alpha: Float = ZONE_ALPHA, fillColor: Color = color, evenOdd: Boolean = false) {
        b.addPath(
            pathData = addPathNodes(path),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(fillColor),
            fillAlpha = alpha,
        )
    }
    fun line(path: String, width: Float = STROKE) {
        b.addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(color),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    fun tone(zones: List<Triple<Float, Float, Float>>, curve: String) {
        zones.forEach { (x, w, a) -> fill(zone(x, w), a) }
        line(BOX)
        line(curve)
    }
    when (glyph) {
        // An S that grows for more contrast and flattens for less.
        EditorGlyph.Contrast -> tone(
            listOf(Triple(3f, 6f, 0.22f), Triple(15f, 6f, 0.22f)),
            "M3 21C9 ${cl(15 + 6 * k1)} 15 ${cl(9 - 6 * k1)} 21 3"
        )
        EditorGlyph.Highlights -> tone(
            listOf(Triple(15f, 6f, ZONE_ALPHA)),
            "M3 21L12 12Q16.5 ${cl(7.5f - 9 * k1)} 21 ${cl(3 - 7 * min(0f, k1))}"
        )
        EditorGlyph.Shadows -> tone(
            listOf(Triple(3f, 6f, ZONE_ALPHA)),
            "M3 ${cl(21 - 7 * max(0f, k1))}Q7.5 ${cl(16.5f - 9 * k1)} 12 12L21 3"
        )
        EditorGlyph.Whites -> tone(
            listOf(Triple(16f, 5f, ZONE_ALPHA)),
            if (k1 >= 0) "M3 21L${f1(21 - 6 * k1)} 3H21" else "M3 21L21 ${f1(3 - 6 * k1)}"
        )
        EditorGlyph.Blacks -> tone(
            listOf(Triple(3f, 5f, ZONE_ALPHA)),
            if (k1 >= 0) "M3 ${f1(21 - 6 * k1)}L21 3" else "M3 21H${f1(3 - 6 * k1)}L21 3"
        )
        EditorGlyph.Tonality -> tone(listOf(Triple(9f, 6f, ZONE_ALPHA)), "M3 21Q12 ${cl(12 - 11 * k1)} 21 3")
        // A small photo (sun and hills) whose right half is washed out; the wash spreads and thickens.
        EditorGlyph.Fade -> {
            fill("M13.5 8.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", alpha = 1f)
            fill("M3 18L9 11.5L13.5 16L16 13.5L21 18.5V18a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3z", alpha = 1f)
            line(BOX)
            val wash = zone(12 - 9 * k1, 9 + 9 * k1)
            fill(wash, alpha = 0.45f + 0.2f * k1, fillColor = Color.Black)
            fill(wash, alpha = 0.12f + 0.2f * k1)
        }
        // A frame whose dark corners grow.
        EditorGlyph.Vignette -> {
            val a = abs(k1)
            val r = 6.5f - 1.5f * a
            fill(
                "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V6a3 3 0 0 1 3 -3z" +
                    "M12 ${f1(12 - r)}a${f1(r)} ${f1(r)} 0 1 0 .01 0z",
                alpha = 0.25f + 0.5f * a,
                evenOdd = true
            )
            line(BOX)
        }
        // An edge whose overshoot grows.
        EditorGlyph.Sharpness -> line("M3 16H8L10 ${f1(17 + 3 * k1)}V${f1(7 - 3 * k1)}L12 8H21")
        // The next three are the line icons of their settings, drawn at the line icons' weight, with one part
        // that moves a little with the value.
        // Bloom: a sun whose rays spread outwards.
        EditorGlyph.Bloom -> {
            val inner = 8f + 0.5f * k1
            val outer = 10f + 1.5f * k1
            val rays = (0 until 8).joinToString("") { i ->
                val a = Math.toRadians(45.0 * i)
                val cx = cos(a).toFloat()
                val sy = sin(a).toFloat()
                "M${f1(12 + inner * cx)} ${f1(12 + inner * sy)}L${f1(12 + outer * cx)} ${f1(12 + outer * sy)}"
            }
            line("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0$rays", LINE_STROKE)
        }
        // Halation: a sun rising off the horizon.
        EditorGlyph.Halation -> {
            val dy = 3.5f * k1.coerceAtLeast(0f)
            val cy = 18f - dy
            val dx = sqrt(16f - dy * dy)
            val large = if (dy > 0f) 1 else 0
            val d = 0.7071f
            line(
                "M3 18h18M${f1(12 - dx)} 18A4 4 0 $large 1 ${f1(12 + dx)} 18" +
                    "M12 ${f1(cy - 5.5f)}V${f1(cy - 7f)}" +
                    "M${f1(12 - 5.5f * d)} ${f1(cy - 5.5f * d)}L${f1(12 - 7f * d)} ${f1(cy - 7f * d)}" +
                    "M${f1(12 + 5.5f * d)} ${f1(cy - 5.5f * d)}L${f1(12 + 7f * d)} ${f1(cy - 7f * d)}",
                LINE_STROKE
            )
        }
        // Temp: the mercury rises and falls.
        EditorGlyph.Temp -> {
            fill(circle(12f, 16.9f, 1.6f), alpha = 1f)
            line("M10 13.5a4 4 0 1 0 4 0v-8.5a2 2 0 0 0 -4 0v8.5M12 16.9V${f1(9f - 4f * k1)}", LINE_STROKE)
        }
        // Tint: the line across the half-filled drop moves up or down.
        EditorGlyph.Tint -> {
            val y = 14f - 3f * k1
            val half = sqrt(36f - (y - 14f) * (y - 14f))
            line("M6.8 11a6 6 0 1 0 10.396 0l-5.197 -8l-5.2 8zM${f1(12 - half)} ${f1(y)}H${f1(12 + half)}", LINE_STROKE)
        }
        // Saturation: the drop fills from empty (black and white) to full.
        EditorGlyph.Saturation -> {
            fill(circle(12f, 14.5f, 1f + 3.2f * (k1 + 1f) / 2f))
            line(DROPLET, LINE_STROKE)
        }
        // Vibrance: the swatch's colour dot grows.
        EditorGlyph.Vibrance -> {
            fill(circle(17f, 17f, 0.6f + 1.4f * (k1 + 1f) / 2f), alpha = 1f)
            line(
                "M19 3h-4a2 2 0 0 0 -2 2v12a4 4 0 0 0 8 0v-12a2 2 0 0 0 -2 -2M13 7.35l-2 -2a2 2 0 0 0 -2.828 0" +
                    "l-2.828 2.828a2 2 0 0 0 0 2.828l9 9M7.3 13h-2.3a2 2 0 0 0 -2 2v4a2 2 0 0 0 2 2h12",
                LINE_STROKE
            )
        }
        // Clarity: the shaded half grows.
        EditorGlyph.Clarity -> {
            val r = 5f + 2f * k1
            val half = "M12 ${f1(12 - r)}a${f1(r)} ${f1(r)} 0 0 0 0 ${f1(2 * r)}z"
            fill(half)
            line("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0$half", LINE_STROKE)
        }
        // Noise reduction: the wand's sparkles grow.
        EditorGlyph.NoiseReduction -> {
            val h = 1.2f + 1.2f * k1.coerceAtLeast(0f)
            line("M6 21l15 -15l-3 -3l-15 15l3 3M15 6l3 3" + sparkle(9f, 5f, h) + sparkle(19f, 15f, h), LINE_STROKE)
        }
        // Colour noise: three colour circles that draw together as the colour noise goes.
        EditorGlyph.ColorNoise -> {
            val d = 3.6f - 0.8f * k1.coerceAtLeast(0f)  // a little, so the three circles stay recognisable
            line(
                circle(12f, 12.5f - d, 4.6f) + circle(12f - d * 0.866f, 12.5f + d * 0.5f, 4.6f) +
                    circle(12f + d * 0.866f, 12.5f + d * 0.5f, 4.6f),
                LINE_STROKE
            )
        }
        // AI denoise: the big sparkle grows when it is on.
        EditorGlyph.AiDenoise -> line(
            sparkle(18f, 18f, 2f) + sparkle(18f, 6f, 2f) + sparkle(9f, 12f, 4.5f + 1.5f * k1.coerceAtLeast(0f)),
            LINE_STROKE
        )
        // Soft light: a circle whose hatched half spreads across it.
        EditorGlyph.SoftLight -> {
            val x = 12f - 4f * k1.coerceAtLeast(0f)
            val half = sqrt(81f - (x - 12f) * (x - 12f))
            val hatch = listOf(6f, 9f, 12f, 15f, 18f).joinToString("") { y ->
                val right = 12f + sqrt(81f - (y - 12f) * (y - 12f))
                "M${f1(x)} ${f1(y)}H${f1(right)}"
            }
            line("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M${f1(x)} ${f1(12 - half)}V${f1(12 + half)}$hatch", LINE_STROKE)
        }
    }
    return b.build()
}
