# Lilac Sony Bridge

A small privileged system app that lets Lilac Camera use Sony's 960 fps super slow motion on the Xperia XZ1 Compact
(`lilac`) running LineageOS with Sony's stock vendor image, and passes other apps' photo and video requests to Lilac.
Lilac itself stays an ordinary, updatable app.

## What it is for

The ROM ships Sony's photo service (`cacaoserver`, binder service `cacao`) and the libraries to talk to it
(`/system/lib64/libcacao_client.so`, `/system/lib64/libimageprocessorjni.so`). Plain Camera2 offers no high-speed modes
on this phone; Sony's own route is the only way to the sensor's 960 fps burst. Three things make it unusable from an
ordinary app, and they decide the design:

1. The two libraries are not on the public library list, so only a system app can `System.load` them.
2. The photo service accepts only callers in the SELinux domains `priv_app`, `platform_app`, `system_app`,
   `system_server` and a few media domains. Calls from an ordinary app are denied, and so is the service's call
   back into an ordinary app. **The video encoder surface therefore has to live in the privileged process.**
3. The service checks the permission `com.sonymobile.permission.IMAGE_PROCESSOR`, which nothing on the ROM declares.
   The bridge declares it itself at `signature` level.

## How Lilac and the bridge work together

```
Lilac Camera (normal app)                      LilacSonyBridge (priv-app, uid of its own)
-------------------------                      -----------------------------------------
Camera2 session in Sony's shape  ----------->  (the camera HAL feeds the viewfinder and the reader)
SlowMotionRecorder  --- binder (ISonyBridge) -> BridgeService
   owns the sequence and its timing              owns the native session (BypassCamera), the H.264 encoder
   passes the output file descriptor             and the MP4 muxer; writes a video-only MP4 into that descriptor
SlowMotionAudio: microphone -> AAC
SlowMotionRemux: video file + sound + frame times -> the final MP4 in the normal video folder
```

- **Interface:** `src/main/aidl/.../ISonyBridge.aidl` (compiled into both apps; the app adds that folder as an AIDL source
  directory). It only grows: new methods go at the end (the transaction codes follow the method order) and raise
  `version()` (`BridgeService.INTERFACE_VERSION`). Lilac's `SonyBridgeClient` reads the version once on connect and
  offers each feature only from the version that brought it, so a newer Lilac works with an older bridge (a ROM's).
  Version 1: 960 fps slow motion. Version 2: pinned certificates and the request forwarder; no new calls.
- **Access control (pinned certificates):** every call checks that the caller's package is `io.github.lilaccamera` or
  starts with `io.github.lilaccamera.` **and** that one of its signing certificates (the whole signing history, so a
  rotated key keeps working) has a SHA-256 in `LilacTrust.PINNED`: today the certificate of the project's GitHub
  releases; the beta's goes beside it once that key exists. A release bridge trusts nothing else, so the key the bridge
  itself is signed with does not matter: a ROM may ship it presigned, or sign it with any key, AOSP's public test keys
  included, without opening it to other apps. Only a debug bridge (debuggable) also trusts its own key, so debug
  builds of the two pair up. The check is one pure function, `LilacTrust.isTrustedCaller`, unit-tested in
  `src/test`.
- **Cleanup:** the bridge releases Sony's session when the client unbinds or dies. The client always calls `release`.
- **Visibility:** Lilac declares a `<queries>` entry for the bridge package. The feature is offered only when the device
  is `lilac`, the bridge is installed and `getCapabilities()` reports 960 fps at 1280x720.
- **Class names:** `com.sonymobile.imageprocessor.bypasscamera2.BypassCamera` and `BypassCameraParameters.Capability`
  mirror names the ROM's native library binds to by name. They contain no Sony code. Do not rename them or shrink the
  app (minification is off).

## The request forwarder

Since Android 11, `IMAGE_CAPTURE`, `IMAGE_CAPTURE_SECURE` and `VIDEO_CAPTURE` requests that name no app are offered
only to system apps. `CaptureForwardActivity` (no window, `Theme.NoDisplay`, labelled "Lilac Camera") takes them as the
system app it is, names Lilac's `com.hinnka.mycamera.CaptureActivity` in a copy of the request, adds
`FLAG_ACTIVITY_FORWARD_RESULT`, starts it and finishes. The copy keeps the caller's `ClipData` and read/write grant
flags, so the grant on the caller's output file passes on; Lilac's answer goes straight back to the caller. The bridge
handles no result.

- **Which Lilac:** the stable package if installed and trusted (as above), else the beta.
- **When it is on:** the activity is disabled in the manifest. `CaptureForwardActivity.refresh` enables it only while a
  trusted Lilac is installed **and** that Lilac is not a system app (a system Lilac answers the requests itself; both
  would be listed). It runs at `BOOT_COMPLETED` and `PACKAGE_FULLY_REMOVED` (`ForwarderReceiver`) and whenever Lilac
  binds (Lilac binds once per start, when its camera first opens, in any mode). A Lilac installed after boot is therefore offered
  from its first start. A request that arrives with Lilac gone is answered "cancelled" and the forwarder switches off.
- Each check logs `forwarder on|off, serves <package>, interface <n>`; `tools/bridge.sh check` shows that line and the
  activity's state.

## The call sequence (Lilac's `SlowMotionRecorder` and `Camera2Controller`)

Mode 0 (`SUPER_SLOW_MOTION`) is the only mode used; mode 1 (`SUPER_SLOW_SHOT`) leaves the service wedged.

1. **Close the camera device.** The mode change does not return while any camera device is open, even without a session.
2. `open(0)`, then `enterSuperSlow(width, height, 960, frames)`: returns in a few milliseconds. Lilac gives up
   if it takes more than 2 s (a slow mode change ends with the service aborting the mode).
3. **Reopen the camera** with exactly two outputs, a 1280x720 viewfinder and a 1280x720 `YUV_420_888` reader (2 buffers),
   in vendor session mode `0x8000`, with the request key `com.sonymobile.control.exposureTimeLimit` =
   `ceil(1e9 / 960)` = 1041667 ns. Any other session shape makes the mode change time out.
4. Wait about 1.5 s for the viewfinder to settle.
5. `prepare(fd)`: the bridge creates the encoder and muxer and hands the encoder surface to the service (callback 4).
6. `startVideo()`: Sony's record press (callback 0). The trigger only works while this runs.
7. `trigger()` any time later: the burst. The service sends no callback for it; the burst has arrived when
   `frameCount()` has grown by the burst length (about 1.5 s later). While it is read out the viewfinder stands still.
8. `stop()` (encoder closed, 0.5 s wait, native stop), `frameTimes()`, `release()`.

## The finished file

The bridge writes video only. Ordinary frames are stamped in real time, the burst frames (0.1 s or 0.18 s of real time)
stretch over 3 s or 6 s, and the readout shows as a hole of 0.8 to 1.5 s in the time stamps. `frameTimes()` returns, per
frame, its stamp in the file and its arrival time (elapsed realtime). `SlowMotionRemux` uses them to:

- set the rotation (sensor orientation plus the phone's rotation: the service draws the camera's own orientation),
- close the hole in the time stamps,
- lay the microphone sound onto the ordinary stretches (matched through the frame arrival times),
- fill everything else with silent AAC frames. A hole in the sound track made the gallery's player stall where
  the burst starts, because players take the sound as their clock.

## Installing the bridge (needs `adb root`, a userdebug ROM)

```bash
./gradlew :sonybridge:assembleDebug              # signed with the debug key; use assembleRelease for the release key
sonybridge/tools/bridge.sh install               # push to /system/priv-app/LilacSonyBridge, chmod 644, chcon, reboot
sonybridge/tools/bridge.sh check                 # installed, privileged, permission, cacao, interface version, forwarder
sonybridge/tools/bridge.sh uninstall             # delete the folder and reboot
```

What `install` does: `adb root`, remount `/` read-write, `mkdir /system/priv-app/LilacSonyBridge`, push the APK there,
`chmod 644`, `chcon u:object_r:system_file:s0`, `sync`, reboot. A system app is only scanned at boot, so every new build
needs a push and a reboot; `adb install` of a system app puts an update under `/data/app` that loses the library
access, which is why the script first uninstalls any normal install of the package. The remount afterwards reports
"busy"; the reboot restores it. Nothing else is changed: no vendor file, no SELinux setting, no trim area.

The bridge asks for no privileged permission, so the ROM's privileged-permission allowlist check does not apply.
A release bridge serves only Lilac builds signed with a pinned certificate; a debug bridge also serves debug-signed
Lilac builds. Any other Lilac is refused.

Install a Lilac build beside the others for testing with `-PdebugIdSuffix=slowmo`
(`io.github.lilaccamera.debug.slowmo`); the debug-signed bridge serves it.

## Test tools (debug builds, Lilac in the foreground in video mode)

- `tools/slowmo-test.sh`: one recording through the debug broadcast, then `ffprobe` checks of the file.
- `tools/slowmo-len.sh`: several recordings, burst length per run.

The debug broadcast: `adb shell am broadcast -a io.github.lilaccamera.SLOWMO -p <package> --es cmd start|trigger|stop`
(extras `--ei width 1280 --ei height 720 --ei frames 91|177`). Do not pass empty string arguments; the shell drops them.
The experiments for a second burst (re-arm variants) were run with a temporary debug command and are not part of the code; their results are under Findings.

## Findings and limits

- **One burst per recording.** A second trigger returns 0 and the service answers at once with callback 3 and no frames.
  Handing the encoder over again, a restart of the recording in the same native session (stop, prepare, start video),
  and waits of 3 to 10 s did not re-arm it. A mode change while the camera streams stops the stream and wedges the
  pipeline. Only the full cycle (camera closed, mode change, camera reopened) re-arms; three recordings in a row work.
- **1280x720 only.** 1080p was tried: the mode change reported a capture size of 1920x720 and failed, or blocked
  for 100 s, and left the camera provider stuck until a reboot.
- **Burst length** 91 or 177 frames (0.095 s or 0.18 s). 177 came out as 177 in a controlled series of 11 runs; some
  earlier runs with the same request gave 91 for a reason not found.
- **The burst really is 960 fps:** a screen flashing at 30 Hz shows a brightness period of 32 frames inside the burst.
- **Light:** the exposure is capped at 1/960 s, so the burst is dark in dim scenes.
- **No torch or stabilisation setting** is applied in the slow-motion session.
- **Heat:** the camera sensor reached 65 C after several recordings in a row; Lilac refuses to start at that point.

## Troubleshooting

| Symptom | Meaning |
|---|---|
| `enterSuperSlow` blocks for tens of seconds, then the mode is stopped | A camera device was open when the mode was changed, or the camera provider is stuck from an earlier failure. Close all camera apps; if it persists, reboot the phone (restarting `cacaoserver` does not clear the provider's state). |
| `UnsatisfiedLinkError` / "not accessible for the namespace" | The bridge is not installed under `/system/priv-app`. |
| `avc: denied { find } for name=cacao` | The bridge runs as an ordinary app (installed under `/system/app` or normally). |
| `Permission failure: com.sonymobile.permission.IMAGE_PROCESSOR` | The bridge's manifest lost the permission lines. |
| Bind refused, "not allowed to use the Sony bridge" | Lilac's package is not `io.github.lilaccamera[.*]`, or its certificate is not pinned (a release bridge refuses debug-signed Lilac builds). |
| Other apps do not list Lilac | `bridge.sh check` shows the forwarder off: Lilac is missing, not signed with a pinned certificate, or a system app (then it is listed itself). |
| Everything returns 0 but no frames | The video recording was not started before the trigger, or the mode was stopped by a timeout. |
| `nativeFinalize` hangs | The service is wedged: `adb root`, `adb shell 'stop cacaoserver; start cacaoserver'`. |
| A finished file stops playing where the burst starts | The sound track has a hole (see above). |
