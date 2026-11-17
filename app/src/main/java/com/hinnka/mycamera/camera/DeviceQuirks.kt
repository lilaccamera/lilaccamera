package com.hinnka.mycamera.camera

import android.os.Build

/** Per-device workarounds. lilac = Sony Xperia XZ1 Compact on LineageOS with the raw19 RAW shim. */
object DeviceQuirks {
    val isLilac: Boolean = Build.DEVICE == "lilac"

    /** Video rates above the camera's listed fixed ranges give a black viewfinder here (60 with only 30 listed). */
    val onlyListedVideoFps: Boolean get() = isLilac
}
