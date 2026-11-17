package io.github.lilaccamera.sonybridge;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import io.github.lilaccamera.sonybridge.IFrameSink;

/**
 * Sony's 960 fps super slow motion, driven through the ROM's own photo service. The caller owns the Camera2
 * session (1280x720 viewfinder plus a 1280x720 YUV reader, vendor session mode 0x8000, exposure cap) and the
 * output file; this privileged process owns the native calls, the H.264 encoder and the MP4 muxer, because the
 * photo service may only call back into a privileged process. Every method returns the native result: 0 is success.
 *
 * Call order: open, enterSuperSlow, (caller opens its camera session and waits for the viewfinder),
 * prepare, startVideo, trigger, stop, frameTimes, release. Always call release.
 *
 * The interface only grows: new methods go at the end (transaction codes follow the order) and raise the version,
 * so an older bridge keeps working with a newer Lilac, which checks version() before using a newer call.
 */
interface ISonyBridge {
    /**
     * Interface version. 1: 960 fps slow motion (everything below). 2: the bridge trusts Lilac by pinned
     * certificate and forwards other apps' photo and video requests to it; no new calls. 3: Sony's video modes
     * (high frame rates and stabilisers) with their frames handed to the caller: enterVideoMode, startFrames,
     * releaseFrame, stopFrames, and the "highFps", "steadyShot" and "intelligentActive" lists in getCapabilities.
     */
    int version();

    /**
     * What the library reports for the rear camera: "superSlow" (boolean, 960 fps available), "info" (strings
     * w/h/fps of each super slow size) and "frameNums" (ints, burst lengths); from version 3 also "highFps",
     * "steadyShot" and "intelligentActive" (strings w/h/fps of each size those video modes offer). Needs no open session.
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

    /** Closes the native session and any encoder or frame hand-over still open. Safe to call at any time and more than once. */
    int release();

    // ---- Version 3: Sony's video modes. Order: open, enterVideoMode, (caller opens its camera in mode 0x8003 with a
    // viewfinder and a one-buffer YUV reader at the preview size, TEMPLATE_RECORD, AE range (30, 30), and waits
    // about 1.5 s), startFrames, ... stopFrames, (startFrames again for the next recording), release. ----

    /**
     * Mode change with no camera device open: mode 0 normal (60 and 120 fps are normal at that rate), 1 SteadyShot,
     * 2 Intelligent Active. Needs open first.
     */
    int enterVideoMode(int mode, int previewW, int previewH, int videoW, int videoH, int fps);

    /**
     * Starts Sony's recording into a bridge-owned reader and sends each frame to sink. At most 4 frames are out at
     * once; a newer frame is dropped while 4 wait for releaseFrame. Intelligent Active frames arrive about 1.5 s
     * after their sensor time.
     */
    int startFrames(IFrameSink sink);

    /** Gives a frame back once the caller's GPU has finished reading it. */
    oneway void releaseFrame(int frameId);

    /** Stops Sony's recording, waits up to 1 s for the frames still out, then frees them all. */
    int stopFrames();
}
