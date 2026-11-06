package com.hinnka.mycamera.camera

import android.os.Build

/** Per-device workarounds. lilac = Sony Xperia XZ1 Compact on LineageOS with the raw19 RAW shim. */
object DeviceQuirks {
    val isLilac: Boolean = Build.DEVICE == "lilac"
}
