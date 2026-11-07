package com.hinnka.mycamera.utils

import android.app.Application

/**
 * The app's processes. `:render` renders one queued RAW photo and then exits; `:queue` runs the
 * render queue's foreground service, so the UI process can end while photos render. Both share
 * the app's files with the main process; neither writes DataStore or Room.
 */
object RenderProcess {
    /** Matches `android:process` of the render service. */
    const val PROCESS_SUFFIX = ":render"

    /** Matches `android:process` of the render queue service. */
    const val QUEUE_PROCESS_SUFFIX = ":queue"

    private val processName: String by lazy { Application.getProcessName() }

    /** True in `:render`. */
    val isCurrent: Boolean by lazy { processName.endsWith(PROCESS_SUFFIX) }

    /** True in `:queue`. */
    val isQueue: Boolean by lazy { processName.endsWith(QUEUE_PROCESS_SUFFIX) }

    /** True in the main (UI) process, the only one that writes DataStore and Room. */
    val isMain: Boolean by lazy { ':' !in processName }
}
