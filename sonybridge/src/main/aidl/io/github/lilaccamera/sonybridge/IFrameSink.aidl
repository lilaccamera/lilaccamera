package io.github.lilaccamera.sonybridge;

import android.hardware.HardwareBuffer;

/** Lilac's side of the frame hand-over (interface version 3). */
oneway interface IFrameSink {
    /**
     * One recording frame at the video size: format PRIVATE, readable by the GPU. timestampNs is the sensor time on the
     * SystemClock.elapsedRealtimeNanos clock. The bridge keeps the buffer until ISonyBridge.releaseFrame(frameId).
     */
    void onFrame(in HardwareBuffer buffer, long timestampNs, int frameId);
}
