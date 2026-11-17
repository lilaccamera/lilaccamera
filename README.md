<p align="center">
  <img src="guide/images/lilac-icon.svg" alt="Lilac Camera logo" width="96" height="96">
</p>

<h1 align="center">Lilac Camera</h1>

<p align="center">Stacked photos, RAW and 960 fps slow motion for the Xperia XZ1 Compact</p>

<p align="center">Started as a fork of <a href="https://github.com/bjzhou/PhotonCamera">PhotonCamera</a> by bjzhou.</p>

<p align="center">
  <a href="https://lilaccamera.github.io/"><img src="https://img.shields.io/badge/Read%20the%20user%20guide-7550D4?style=for-the-badge" alt="Read the user guide"></a>
</p>

<p align="center">
  <a href="https://lilaccamera.github.io/user/how-pictures-are-made/">How a picture is made</a> ·
  <a href="https://lilaccamera.github.io/#get-lilac">Get started</a> ·
  <a href="https://github.com/lilaccamera/lilaccamera/releases">Releases</a> ·
  <a href="https://lilaccamera.github.io/rom/integration/">For ROM maintainers</a>
</p>


1. **GCam's photo processing on Sony's camera hardware**, with all of the sony camera hardware features.
2. **Built for a small screen.** The camera and the editor are rebuilt for one thumb on 4.6 inches.
3. **960 fps slow motion.** Sony's super slow motion, working again on a custom ROM.

## The best of both worlds

GCam processes photos well but knows nothing about Sony's camera. Sony's camera app uses the hardware fully but does not run on custom ROMs. Lilac brings the two together: GCam's HDR+ processing, and the Sony camera features that only Sony's own app used, such as subject tracking, the laser distance sensor and 960 fps slow motion.

Compared with a default camera app such as Aperture:

| | Default camera app | Lilac |
|---|---|---|
| **Each photo** | One finished JPEG from Sony's processor | 3 to 20 frames merged into one: less noise, more detail |
| **Starting point** | Sony's picture, noise already smoothed away with detail | RAW frames merged before any processing (GCam) |
| **Bright skies** | One exposure: what clips stays white | A darker RAW burst; HDRNet lifts the shadows (GCam) |
| **Dim light** | One frame, heavily smoothed | Night: 6 to 12 frames merged and brightened |
| **Focus** | Tap a point | Subject tracking, face choice, laser distance (Sony) |
| **Slow motion** | None | 960 fps at 720p (Sony) |

*GCam* means the same HDR+ pipeline and models as Google's camera app. *Sony* means Sony's own camera features. [What comes from GCam](https://lilaccamera.github.io/#the-best-of-both-worlds) lists which Lilac setting matches which GCam step.

## Built for a 4.6-inch screen

Lilac's interface is not PhotonCamera's. The camera and the editor were rebuilt around one rule: a 4.6-inch phone held in one hand.

- **Everything you press is in thumb reach.** Every button sits in the lower part of the screen; the top is only viewfinder.
- **The viewfinder takes gestures:** swipe down for everyday settings, up for manual controls, sideways for looks.
- **The shutter is the mode dial**, with the zoom ring curved round its top like a compact camera's zoom lever.
- **The side keys are camera controls:** half-press the camera key to focus, press fully to shoot; the volume keys shoot or zoom.

More in [Built for a small screen](https://lilaccamera.github.io/user/small-screen/).

## 960 fps slow motion is back

The XZ1 Compact's sensor records at 960 frames a second, but on custom ROMs that went away with Sony's camera app. sunplex07's LineageOS 23.2 keeps Sony's slow-motion service, cacao, and Lilac reaches it through its companion app, the Lilac Sony Bridge. So 960 fps works again. Only tested on sunplex07's LineageOS 23.2; other ROMs may not ship Sony's cacao service. More in [Video](https://lilaccamera.github.io/user/video/#slow-motion).

## Get Lilac

| Feature | Needs on the ROM | Tested on |
|---|---|---|
| Photo, Night, Portrait, Video | any lilac ROM, Android 11 or newer | |
| Pro (RAW) | the raw19 camera shim | sunplex07's LineageOS 23.2 |
| 960 fps slow motion | Sony's cacao service and the Lilac Sony Bridge | sunplex07's LineageOS 23.2 only |

Other phones are not tested. Testers can add the bridge themselves: [install the bridge](https://lilaccamera.github.io/bridge/install/). ROM maintainers: [ROM integration](https://lilaccamera.github.io/rom/integration/).

- **Stable:** `LilacCamera.apk` from the [latest release](https://github.com/lilaccamera/lilaccamera/releases/latest). Lilac ROMs ship this same APK, so a ROM's copy also updates from new releases.
- **Beta:** `LilacBeta.apk` from a [pre-release](https://github.com/lilaccamera/lilaccamera/releases). It installs beside the stable copy as **Lilac Beta**.

## The guide

The [user guide](https://lilaccamera.github.io/) explains what Lilac does differently; its source is in [`guide/`](guide/index.md). Camera basics, and the colour tools Lilac shares with PhotonCamera, are not covered there.

- **How Lilac works:** [how a picture is made](https://lilaccamera.github.io/user/how-pictures-are-made/), [Sony hardware](https://lilaccamera.github.io/user/hardware/), [built for a small screen](https://lilaccamera.github.io/user/small-screen/), [focus and faces](https://lilaccamera.github.io/user/focus/)
- **Using Lilac:** [getting started](https://lilaccamera.github.io/user/getting-started/), [Photo and Night](https://lilaccamera.github.io/user/photo-mode/), [Pro](https://lilaccamera.github.io/user/pro-mode/), [HDR](https://lilaccamera.github.io/user/hdr/), [Portrait](https://lilaccamera.github.io/user/portrait/), [Video](https://lilaccamera.github.io/user/video/), [Editor](https://lilaccamera.github.io/user/editor/), [Troubleshooting](https://lilaccamera.github.io/user/troubleshooting/)
- **Testers and ROMs:** [slow-motion bridge](https://lilaccamera.github.io/bridge/install/), [ROM integration](https://lilaccamera.github.io/rom/integration/)

<details>
<summary><b>Run Lilac on sunplex07's LineageOS, with the slow-motion bridge</b></summary>

Photo, Night, Portrait and Video need nothing but the Lilac APK. Two features need more from the ROM:

- **Pro (RAW)** needs the raw19 camera shim. The bridge does not change that.
- **960 fps slow motion at 720p** in Video mode needs Sony's cacao service plus the **Lilac Sony Bridge**. That is all the bridge enables. It is a small app with no icon: Sony's slow-motion service only talks to privileged system apps, so the bridge runs that part while Lilac stays an ordinary app you can update.

sunplex07's LineageOS 23.2 for lilac has both the shim and cacao; it is the only ROM this was tested on. If your ROM ships the bridge, skip this.

The bridge must sit in `/system/priv-app/`, which is on the read-only system partition. There are two ways to put it there; either one **reboots the phone**:

- **With adb and `bridge.sh`** (steps 1 to 6). This needs a computer with adb and **rooted adb**: USB debugging and **Rooted debugging** on (Settings › System › Developer options), which exists only on debug-capable (userdebug) builds such as sunplex07's. The script runs `adb root` and remounts the system partition itself. `bridge.sh check` and the checks in step 5 work without root.
- **With a root file browser** (below the steps), if you have root on the phone but not rooted adb.

1. **Install Lilac** (`LilacCamera.apk`) from the [latest release](https://github.com/lilaccamera/lilaccamera/releases/latest) like any APK.
2. **Download `LilacSonyBridge.apk` and `bridge.sh` from the same release.** The two apps talk through an interface that can change between releases, so install the bridge again whenever Lilac updates.
3. **Check the phone.** Nothing is changed:

    ```bash
    bash bridge.sh check
    ```

    You want `cacao: found` and `raw19 shim: found`. Without cacao, slow motion cannot work on this ROM; without the shim, Pro mode is missing. The script asks before installing anyway.

4. **Install the bridge.** The script copies the APK to `/system/priv-app/LilacSonyBridge/` and reboots:

    ```bash
    bash bridge.sh install LilacSonyBridge.apk
    ```

5. **Check after the reboot:**

    ```bash
    adb shell pm path io.github.lilaccamera.sonybridge
    adb shell "dumpsys package io.github.lilaccamera.sonybridge | grep -E 'privateFlags|IMAGE_PROCESSOR'"
    ```

    The path is under `/system/priv-app/LilacSonyBridge/` and the flags include `PRIVILEGED`.

6. **Use it:** in Lilac, switch to Video and swipe down on the picture. A **Slow-mo** switch appears.

**With a root file browser instead of the script.** This does by hand what `bridge.sh install` does. It has not been tried on the phone.

- **Prepare:** do steps 1 and 2 above. A copy of the bridge installed as a normal app hides the system copy, so uninstall it first if you have one (Settings › Apps).
- **Check the ROM** without the script: these files must exist, `/system/lib64/libcacao_client.so` and `/system/lib64/libimageprocessorjni.so` (Sony's slow-motion service) and `/vendor/lib/hw/camera.lilac-real.so` (the RAW shim, only for Pro).
- **Unlock:** in the file browser, give it root and mount `/system` read-write (most have a switch for this).
- **Place:** create the folder `/system/priv-app/LilacSonyBridge` and copy the APK into it under exactly the name `LilacSonyBridge.apk`.
- **Permissions:** set the file's permissions to `rw-r--r--` (644), owned by root. The script also sets the SELinux label `u:object_r:system_file:s0`, which a file browser cannot; a file made inside that folder normally gets it anyway. If the bridge does not work after the reboot, check the label first (`ls -Z`).
- **Reboot:** mount `/system` read-only again if the browser asks, then **reboot**. Android picks up system apps only at boot.
- **Check:** run the adb checks from step 5 above (they work with normal adb), or look for **Lilac Sony Bridge** in Settings › Apps with system apps shown. Then do step 6 above.

To undo, run `bash bridge.sh uninstall`, or delete `/system/priv-app/LilacSonyBridge` in the file browser and reboot. Lilac keeps working and only the Slow-mo switch goes away. The script touches no vendor files, SELinux settings or bootloader. If you built from source, use `sonybridge/tools/bridge.sh` and your own `sonybridge-debug.apk` with a debug-signed Lilac. More in the [bridge guide](https://lilaccamera.github.io/bridge/install/).

</details>

<details>
<summary><b>Optimizations for a 4 GB phone</b></summary>

### Start-up and mode switches

Measured on an Xperia XZ1 Compact over adb, release builds, medians of 6 cold starts (4 to 7 runs for the switches), in seconds from the launch command. "Before" is Lilac as it was before this start-up work.

| | Before | Lilac |
|---|---|---|
| Camera connected | 1.02 | **0.40** |
| First frame, Photo | 1.78 | **1.41** |
| First frame, Pro | 1.59 | **1.25** |
| Video → Photo, tap to first frame | 1.45 (camera opened twice) | **0.99** |
| Photo → Pro, tap to first frame | 0.87 | 0.81 |
| Reopen after Home, camera already closed | | 0.80 to 0.84 |

What changed:

- **The camera starts before the screen is ready.** It opens as soon as the settings are loaded and configures its session before the preview exists, so it connects at 0.40 s instead of 1.02 s.
- **The splash no longer waits for the gallery.** The gallery list and other warm-up work run after the first frame.
- **A mode switch opens the camera once.** Video → Photo used to open it twice, and Photo ↔ Pro waited 0.25 to 0.3 s for the settings to be saved first.
- **No black cover.** The old switch showed black for 1.07 to 1.32 s. Now the viewfinder fades in over 150 ms on the first drawn frame, and Photo ↔ Pro has no cover at all.
- **Changing the LUT no longer restarts the camera.**
- **Leaving the app keeps the camera open for a few seconds,** so a quick return is instant.

For comparison, Open Camera reaches its first frame at 0.90 s and Aperture at 1.52 s. Lilac's remaining gap in Photo mode is the full-resolution sensor readout (about 0.26 s), kept on purpose for stacking quality. Debug builds start about 3 times slower (3.5 s against 1.1 s to a live preview) because Android does not compile them, and a freshly sideloaded release is uncompiled until Android's idle job runs (first frame 1.45 s, 1.26 s after `adb shell cmd package compile -m speed -f io.github.lilaccamera`).

### Memory

A 19 MP photo needs more graphics memory than a 4 GB phone can spare, so Android's low-memory killer used to end the app or its render process.

- **Full-size RAW buffers are freed after each photo,** about 612 MB at 5056x3792, instead of staying resident for the next one.
- **The HDR outputs free what they no longer read.** The render process peaked at 1.42 GB while building the gain map; its full-size buffers are now released before that stage.
- **Bracketed HDR fusion peaks about a third lower** than its former 1.5 GB at 19 MP, with the same maths and the same output.
- **The render queue waits for free memory.** It starts a render at 450 MB free and waits at most 1 minute (it used to wait for 600 MB for up to 2 minutes). After a stacked Photo shot about 500 MB stay free, and a render started at 517 MB finished where renders started at 200 to 330 MB were killed.
- **The gallery detail view renders at 1.5 times the screen's long edge** (1920 px on 1280x720) instead of up to 4096 px, which left about 300 MB of graphics memory in the app. Deep zoom is a little softer.
- **The editor works on small copies** (a half-size RAW preview, 2048 px for denoise and blur) and Save repeats the work in full. An HD switch previews at full size.

These are measurements from test builds on one phone, not a benchmark suite. The numbers will differ on other phones and ROMs.

</details>

<details>
<summary><b>Build from source</b></summary>

You need JDK 17 and the Android SDK with platform 36, NDK 29.0.14206865 and CMake 3.22.1 (install them in the SDK Manager). Put the SDK path in `local.properties`, which Android Studio writes for you:

```properties
sdk.dir=/path/to/Android/sdk
```

Then build Lilac and the [slow-motion bridge](https://lilaccamera.github.io/bridge/install/) from the repository root. The Gradle wrapper downloads Gradle itself.

```bash
./gradlew assembleDefaultDebug :sonybridge:assembleDebug
```

- **Lilac:** `app/build/outputs/apk/default/debug/app-default-debug.apk`. It installs as `io.github.lilaccamera.debug`, beside a ROM's copy.
- **Bridge:** `sonybridge/build/outputs/apk/debug/sonybridge-debug.apk`. It needs a system install; follow the [bridge page](https://lilaccamera.github.io/bridge/install/). The bridge serves a Lilac signed with its own key or with the key of this project's releases; both debug builds use the repository's public debug key.
- **Release builds:** `assembleDefaultRelease` signs with your own key, passed as `-PRELEASE_STORE_FILE`, `-PRELEASE_STORE_PASSWORD`, `-PRELEASE_KEY_ALIAS` and `-PRELEASE_KEY_PASSWORD`. Add `-Pbeta` for the beta (`io.github.lilaccamera.beta`, named Lilac Beta). Never share a debug build: its key is public.

</details>

<details>
<summary><b>Make a release (maintainers)</b></summary>

GitHub Actions builds and signs every release; nothing is uploaded by hand.

1. **Set the version** in `app/build.gradle.kts`: raise `versionCode` by one (betas too, or the update will not install) and set `versionName`, for example `0.2.0` for a stable release or `0.2.0-beta.1` for a beta. Commit and push to `main`.
2. **Draft a release** on GitHub with the tag `v` plus the version name (`v0.2.0`, `v0.2.0-beta.1`), targeting `main`. Tick **Set as a pre-release** for a beta, and only for a beta.
3. **Publish it.** The *Release APKs* workflow attaches the files after about 15 minutes:
    - a stable release: `LilacCamera.apk`, `LilacSonyBridge.apk` and `bridge.sh`;
    - a pre-release: `LilacBeta.apk`, `LilacSonyBridge.apk` and `bridge.sh`.

    It stops with an error if the tag does not match the version name, or if the pre-release tick does not match the version (a suffix means beta).
4. **The guide** at lilaccamera.github.io is rebuilt for stable releases only, so it never describes features only a beta has. To rebuild it by hand, run the *Docs site* workflow.

The workflows need these repository secrets: `RELEASE_KEYSTORE` (the keystore, base64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, and `DOCS_DEPLOY_KEY` (a deploy key with write access to `lilaccamera.github.io`). The bridge trusts the release key's certificate by its SHA-256 (`RELEASE_CERT` in `sonybridge/.../BridgeService.kt`); a new release key needs a new value there and a new bridge everywhere.

</details>

<details>
<summary><b>What PhotonCamera came with</b></summary>

Lilac started as a fork of [PhotonCamera](https://github.com/bjzhou/PhotonCamera) by bjzhou, an open-source Android camera application focused on static photography, designed to simulate the handling and image quality of modern mirrorless digital cameras. This is the feature list of the original app. The upstream README is also available in [English](./README_EN.md), [简体中文](./README_CN.md) and [日本語](./README_JA.md).

### 1. Advanced LUT Support
* **Multi-format Compatibility**: Supports importing and applying `.cube`, `.png` (Halfs/Fulls), and `.xmp` profile files.
* **Real-time Preview**: High-performance shaders enable real-time LUT filtering with "What You See Is What You Get" (WYSIWYG).
* **Custom Imports**: Easily import your own LUT libraries to create a unique color signature.

### 2. Deep Color Recipes
A professional-grade color adjustment system allowing fine-tuning across multiple dimensions:
* **Basic Adjustments**: Exposure, Contrast, Highlights, Shadows, Saturation, Temperature, Tint.
* **Artistic Effects**: Color effects, Vignette, Grain, Fade, Bleach Bypass.
* **Pro Filters**: Bloom, Dispersion, Noise, Low-pixel aesthetics.

### 3. Motion Photos
* **Industry Unique**: The only open-source project providing multi-vendor adaptation (Xiaomi, Samsung, Pixel, etc.) for Motion Photos on Android.
* **Dynamic Moments**: Capture short video clips alongside your still images.

### 4. High-Speed Burst
* **Performance Peak**: High-speed burst mode with no limit on the number of frames.
* **LUT Integration**: Supports applying LUT filters in real-time during burst sequences.

### 5. Multi-frame Synthesis & Super Resolution
* **Quality Enhancement**: Enhances image quality through advanced multi-frame stacking.
* **Noise Reduction**: Provides effective noise reduction while focusing on preserving natural details.

### 6. Large Aperture Bokeh
* **AI-Driven**: Integrates the **midas-v2** depth detection local AI model, optimized for Qualcomm chips.
* **Precise Depth**: Offers accurate depth sensing for natural background blur transitions (ongoing refinements).

### 7. Phantom Mode
* **Raw Quality**: Directly bridges with the system camera for image capture while applying Photon Camera's LUT engine. This bypasses the typical "bad image quality" and "over-sharpening" issues found in standard third-party camera APIs.

### 8. AI Color Simulation
* **Smart Stylization**: Utilizes **Google Nano Banana 2** technology to analyze reference photos, restore original colors, and extract color profiles to generate custom LUTs.

</details>

## License

Except for third-party components, models, fonts, LUTs, and other assets that carry their own license notices in this repository, the source code is licensed under the [Apache License 2.0](./LICENSE).

Questions and bug reports: [open an issue](https://github.com/lilaccamera/lilaccamera/issues).
