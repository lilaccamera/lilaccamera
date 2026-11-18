<p align="center">
  <img src="guide/images/lilac-icon.svg" alt="Lilac Camera logo" width="96" height="96">
</p>

<h1 align="center">Lilac Camera</h1>

<p align="center">Sony's camera app for the Xperia XZ1 Compact, rebuilt for custom ROMs, with GCam multi-frame processing</p>

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


Lilac rebuilds Sony's camera app for custom ROMs, with all of the camera's hardware features, and adds GCam's multi-frame processing on top.

**Fully works on sunplex07's LineageOS 23.2 only.** Other lilac ROMs get basic multi-frame stacking in Photo, Night, Portrait and Video, because they lack the raw19 camera shim and Sony's cacao service.

| Feature | Needs on the ROM | Tested on |
|---|---|---|
| Photo, Night, Portrait, Video: multi-frame stacking | any lilac ROM, Android 11 or newer | |
| Sony's faces, subject tracking, laser focus and camera key | Sony's camera driver; expected on any lilac ROM | sunplex07's LineageOS 23.2 only |
| Pro (RAW): GCam's HDR+ and HDRNet | the raw19 camera shim | sunplex07's LineageOS 23.2 |
| 960 fps slow motion | Sony's cacao service and the Lilac Sony Bridge | sunplex07's LineageOS 23.2 only |

Other phones are not tested. Testers can add the bridge themselves: [install the bridge](https://lilaccamera.github.io/bridge/install/). ROM maintainers: [ROM integration](https://lilaccamera.github.io/rom/integration/).

## Sony's camera features

Sony's camera app uses the phone's hardware fully but does not run on custom ROMs. Lilac brings its features back: face frames, subject tracking, the laser distance sensor, the two-stage camera key, smile shutter, Sony's own photo processing, and 960 fps slow motion. The aim is the whole of Sony's camera app; [Sony camera features](https://lilaccamera.github.io/user/hardware/) lists what is done and what is not yet.

The XZ1 Compact's sensor records at 960 frames a second, but on custom ROMs that went away with Sony's camera app. sunplex07's LineageOS 23.2 keeps Sony's slow-motion service, cacao, and Lilac reaches it through its companion app, the Lilac Sony Bridge. More in [Video](https://lilaccamera.github.io/user/video/#slow-motion).

## GCam's multi-frame processing

GCam processes photos well but knows nothing about Sony's camera. Lilac runs GCam's HDR+ processing on Sony's camera hardware. Compared with a default camera app such as Aperture:

| | Default camera app | Lilac |
|---|---|---|
| **Each photo** | One finished JPEG from Sony's processor | 3 to 20 frames merged into one: less noise, more detail |
| **Starting point** | Sony's picture, noise already smoothed away with detail | RAW frames merged before any processing (GCam) |
| **Bright skies** | One exposure: what clips stays white | A darker RAW burst; HDRNet lifts the shadows (GCam) |
| **Dim light** | One frame, heavily smoothed | Night: 6 to 12 frames merged and brightened |
| **Focus** | Tap a point | Subject tracking, face choice, laser distance (Sony) |
| **Slow motion** | None | 960 fps at 720p (Sony) |

*GCam* means the same HDR+ pipeline and models as Google's camera app. *Sony* means Sony's own camera features. [What comes from GCam](https://lilaccamera.github.io/#gcams-multi-frame-processing) lists which Lilac setting matches which GCam step.

## Built for a 4.6-inch screen

Lilac's interface is not PhotonCamera's. The camera and the editor were rebuilt around one rule: a 4.6-inch phone held in one hand. Every button is in thumb reach, the viewfinder takes gestures, the shutter is the mode dial, and the side keys are camera controls. More in [Built for a small screen](https://lilaccamera.github.io/user/small-screen/).

## Get Lilac

- **Stable:** `LilacCamera.apk` from the [latest release](https://github.com/lilaccamera/lilaccamera/releases/latest). Lilac ROMs ship this same APK, so a ROM's copy also updates from new releases.
- **Beta:** `LilacBeta.apk` from a [pre-release](https://github.com/lilaccamera/lilaccamera/releases). It installs beside the stable copy as **Lilac Beta**.

## The guide

The [user guide](https://lilaccamera.github.io/) explains what Lilac does differently; its source is in [`guide/`](guide/index.md). Camera basics, and the colour tools Lilac shares with PhotonCamera, are not covered there.

- **How Lilac works:** [Sony camera features](https://lilaccamera.github.io/user/hardware/), [how a picture is made](https://lilaccamera.github.io/user/how-pictures-are-made/), [built for a small screen](https://lilaccamera.github.io/user/small-screen/), [focus and faces](https://lilaccamera.github.io/user/focus/), [optimizations and tweaks](https://lilaccamera.github.io/user/optimizations/)
- **Using Lilac:** [getting started](https://lilaccamera.github.io/user/getting-started/), [Photo and Night](https://lilaccamera.github.io/user/photo-mode/), [Pro](https://lilaccamera.github.io/user/pro-mode/), [HDR](https://lilaccamera.github.io/user/hdr/), [Portrait](https://lilaccamera.github.io/user/portrait/), [Video](https://lilaccamera.github.io/user/video/), [Editor](https://lilaccamera.github.io/user/editor/), [Troubleshooting](https://lilaccamera.github.io/user/troubleshooting/)
- **Testers and ROMs:** [slow-motion bridge](https://lilaccamera.github.io/bridge/install/) (adb script or root file browser), [ROM integration](https://lilaccamera.github.io/rom/integration/)

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
