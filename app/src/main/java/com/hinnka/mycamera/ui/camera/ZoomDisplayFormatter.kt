package com.hinnka.mycamera.ui.camera

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 格式化变焦倍率显示。
 */
internal fun formatZoomRatioLabel(ratio: Float): String {
    val roundedToTenth = (ratio * 10).roundToInt() / 10f
    return if (roundedToTenth < 10f) {
        String.format(Locale.US, "%.1fx", roundedToTenth)
    } else {
        "${ratio.roundToInt()}x"
    }
}

/**
 * 显示模式：变焦倍率 or 35mm等效焦距
 */
enum class ZoomDisplayMode {
    ZOOM_RATIO,      // 显示 0.6x, 1x, 2x 等
    FOCAL_LENGTH;    // 显示 35mm, 50mm, 85mm 等

    fun next(): ZoomDisplayMode {
        return if (this == ZOOM_RATIO) FOCAL_LENGTH else ZOOM_RATIO
    }

    companion object {
        fun fromPersistedName(name: String?): ZoomDisplayMode {
            return entries.firstOrNull { it.name == name } ?: FOCAL_LENGTH
        }
    }
}
