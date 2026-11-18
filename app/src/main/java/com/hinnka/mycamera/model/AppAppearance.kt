package com.hinnka.mycamera.model

object AppAppearance {
    // Sony orange, the camera screen's colour for "on".
    const val DEFAULT_ACCENT_COLOR: Int = 0xFFFF9C00.toInt()

    // Interface accents are opaque; transparency belongs to individual UI states.
    fun opaqueAccent(color: Int): Int = color or 0xFF000000.toInt()
}
