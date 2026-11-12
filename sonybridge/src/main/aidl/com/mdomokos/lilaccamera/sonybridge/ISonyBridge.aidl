package com.mdomokos.lilaccamera.sonybridge;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Sony's 960 fps super slow motion, driven through the ROM's own photo service. The caller owns the Camera2
 * session (1280x720 viewfinder plus a 1280x720 YUV reader, vendor session mode 0x8000, exposure cap) and the
 * output file; this privileged process owns the native calls, the H.264 encoder and the MP4 muxer, because the
 * photo service may only call back into a privileged process. Every method returns the native result: 0 is success.
 *
 * Call order: open, enterSuperSlow, (caller opens its camera session and waits for the viewfinder),
 * prepare, startVideo, trigger, stop, frameTimes, release. Always call release.
 */
interface ISonyBridge {
    /** Protocol version, 1. */
    int version();

    /**
     * What the library reports for the rear camera: "superSlow" (boolean, 960 fps available), "info" (strings
     * w/h/fps of each super slow size) and "frameNums" (ints, burst lengths). Needs no open session.
     */
    Bundle getCapabilities();

    /** Opens the native session for a camera (0 is the rear camera); an earlier one is released first. */
    int open(int camera);

    /**
     * Mode change to SUPER_SLOW_MOTION (the only mode this bridge uses) with a 1280x720 viewfinder, a video of
     * videoW x videoH played at 30 fps, captured at fps, burst of frames frames. Takes about 0.15 s; one that
     * takes over 2 s means the service is about to abort the mode.
     */
    int enterSuperSlow(int videoW, int videoH, int fps, int frames);

    /** Creates the encoder and muxer writing a video-only MP4 into file, and hands the encoder to the service. */
    int prepare(in ParcelFileDescriptor file);

    /** Starts the ordinary video recording; the trigger only works while it runs. */
    int startVideo();

    /** Fires the 960 fps burst. The service reports no completion; the burst has arrived when frameCount has grown by its length. */
    int trigger();

    /** Frames the encoder has delivered so far. */
    int frameCount();

    /**
     * One pair per frame the encoder delivered, in order: its time stamp in the file (microseconds) and the time the
     * frame arrived (SystemClock.elapsedRealtimeNanos). Lets the caller match sound to the video. Valid after stop,
     * until the next prepare.
     */
    long[] frameTimes();

    /** Ends the encoder, waits, closes the muxer and the file, then stops the native recording. */
    int stop();

    /** Closes the native session and any encoder still open. Safe to call at any time and more than once. */
    int release();
}
