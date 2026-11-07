package com.hinnka.mycamera.utils

import android.app.Application

/**
 * The `:render` process renders one queued RAW photo and then exits. It shares the app's files with
 * the main process, so it reads settings and the gallery but never writes DataStore or Room.
 */
object RenderProcess {
    /** Matches `android:process` of the render service. */
    const val PROCESS_SUFFIX = ":render"

    val isCurrent: Boolean by lazy { Application.getProcessName().endsWith(PROCESS_SUFFIX) }
}
