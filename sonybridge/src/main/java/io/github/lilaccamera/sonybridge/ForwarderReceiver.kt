package io.github.lilaccamera.sonybridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-checks the photo and video request forwarder at boot and whenever a package is fully removed. */
class ForwarderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = CaptureForwardActivity.refresh(context)
}
