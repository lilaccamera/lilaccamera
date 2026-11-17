<p align="center">
  <img src="guide/images/lilac-icon.svg" alt="Lilac Camera logo" width="96" height="96">
</p>

<h1 align="center">Lilac Camera</h1>

<p align="center">Stacked photos, RAW and 960 fps slow motion for the Xperia XZ1 Compact</p>

<p align="center">
  <a href="https://lilaccamera.github.io/"><img src="https://img.shields.io/badge/Read%20the%20user%20guide-7550D4?style=for-the-badge" alt="Read the user guide"></a>
</p>

<p align="center">
  <a href="https://lilaccamera.github.io/user/getting-started/">Get started</a> ·
  <a href="https://github.com/lilaccamera/lilaccamera/releases">Beta releases</a> ·
  <a href="https://lilaccamera.github.io/rom/integration/">For ROM maintainers</a>
</p>

Lilac is a camera app for one phone. It does three things a default camera app cannot:

1. **The best of both worlds.** GCam's photo processing on Sony's camera hardware.
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

- **Everything you press is in thumb reach.** Every button sits in the lower part of the screen; the top is only picture.
- **The picture takes the gestures:** swipe down for everyday settings, up for manual controls, sideways for looks.
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

- **Stable:** inside lilac ROMs that include it; it updates with the ROM.
- **Beta:** [GitHub releases](https://github.com/lilaccamera/lilaccamera/releases) of this project. It installs beside the ROM's copy under its own name.

## The guide

The [user guide](https://lilaccamera.github.io/) explains what Lilac does differently; its source is in [`guide/`](guide/index.md). Camera basics, and the colour tools Lilac shares with PhotonCamera, are not covered there.

- **How Lilac works:** [how a picture is made](https://lilaccamera.github.io/user/how-pictures-are-made/), [Sony hardware](https://lilaccamera.github.io/user/hardware/), [built for a small screen](https://lilaccamera.github.io/user/small-screen/), [focus and faces](https://lilaccamera.github.io/user/focus/)
- **Using Lilac:** [getting started](https://lilaccamera.github.io/user/getting-started/), [Photo and Night](https://lilaccamera.github.io/user/photo-mode/), [Pro](https://lilaccamera.github.io/user/pro-mode/), [HDR](https://lilaccamera.github.io/user/hdr/), [Portrait](https://lilaccamera.github.io/user/portrait/), [Video](https://lilaccamera.github.io/user/video/), [Editor](https://lilaccamera.github.io/user/editor/), [Troubleshooting](https://lilaccamera.github.io/user/troubleshooting/)
- **Testers and ROMs:** [slow-motion bridge](https://lilaccamera.github.io/bridge/install/), [ROM integration](https://lilaccamera.github.io/rom/integration/)

<details>
<summary><b>What PhotonCamera came with</b></summary>

Lilac is a fork of [PhotonCamera](https://github.com/bjzhou/PhotonCamera) by bjzhou, an open-source Android camera application focused on static photography, designed to simulate the handling and image quality of modern mirrorless digital cameras. This is the feature list of the original app. The upstream README is also available in [English](./README_EN.md), [简体中文](./README_CN.md) and [日本語](./README_JA.md).

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
