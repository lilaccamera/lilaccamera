---
hide:
  - toc
---

<div class="lilac-hero" markdown="0">
<img class="lilac-hero__icon" src="images/lilac-icon.svg" alt="" width="96" height="96">
<div class="lilac-hero__text">
<h1>Lilac Camera</h1>
<p class="lilac-hero__tagline">Stacked photos, RAW and 960 fps slow motion for the Xperia XZ1 Compact</p>
<div class="lilac-hero__buttons">
<a class="lilac-btn lilac-btn--primary" href="user/getting-started/">Get started</a>
</div>
</div>
</div>

Lilac Camera is a fork of [PhotonCamera](https://github.com/bjzhou/PhotonCamera) by bjzhou, with an interface rebuilt for small phone screens and processing adapted for low-memory phones.

<div class="lilac-cards" markdown="0">
<a class="lilac-card" href="user/photo-mode/"><strong>Photo and Night</strong><span>Stacking, how many frames, 12 MP and 2 MP, front camera colour</span></a>
<a class="lilac-card" href="user/pro-mode/"><strong>Pro and HDR</strong><span>RAW bursts, the render queue, keeping bright skies</span></a>
<a class="lilac-card" href="user/troubleshooting/"><strong>Troubleshooting</strong><span>Every message, what it means and what to do</span></a>
</div>

Lilac takes several frames for every photo and merges them, so less noise reaches the picture and more of the light does. It is built for the Sony Xperia XZ1 Compact (lilac): a 4.6-inch screen, 4 GB of memory and a capable 19 MP sensor that most camera apps leave underused.

## What it does differently

### What a camera app gets, and what Lilac does with it

A default camera app, such as Aperture on LineageOS, asks the camera for a photo and gets one finished JPEG: already denoised, sharpened and toned by Sony's image processor. Lilac asks for more, and does more with it.

<div class="lilac-compare" markdown="0" role="table" aria-label="A default camera app compared with Lilac">
<div class="lilac-compare__row lilac-compare__row--head" role="row"><span role="columnheader">A default camera app<small>such as Aperture</small></span><span role="columnheader" class="lilac-compare__lilac">Lilac</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/StackLine.svg" alt="">Each photo</div>
<div class="lilac-compare__row" role="row"><span role="cell">One finished JPEG, made by Sony's processor</span><span role="cell" class="lilac-compare__lilac">A burst of 3 to 20 frames merged into one, as GCam does: less noise, more fine detail</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/SensorLine.svg" alt="">Starting point</div>
<div class="lilac-compare__row" role="row"><span role="cell">Sony's finished picture: noise already smoothed away with some of the detail</span><span role="cell" class="lilac-compare__lilac">RAW frames from the sensor, merged before any processing (on sunplex07's LineageOS 23.2)</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/SunHighLine.svg" alt="">Bright windows and skies</div>
<div class="lilac-compare__row" role="row"><span role="cell">One exposure: what clips stays white</span><span role="cell" class="lilac-compare__lilac">GCam's HDR+ approach: a darker RAW burst keeps them, then GCam's HDRNet lifts the shadows</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/MoonStars.svg" alt="">Dim light</div>
<div class="lilac-compare__row" role="row"><span role="cell">One frame; Sony smooths the noise, and detail with it</span><span role="cell" class="lilac-compare__lilac">Night: 6 to 12 frames merged and brightened, planned from how steady you hold</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/FocusLine.svg" alt="">Focus</div>
<div class="lilac-compare__row" role="row"><span role="cell">Tap to focus on a point</span><span role="cell" class="lilac-compare__lilac">Sony's subject tracking, face selection, laser distance and half-press lock, as in Sony's own app</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/MovieCamera.svg" alt="">Slow motion</div>
<div class="lilac-compare__row" role="row"><span role="cell">None: the phone offers apps no high-speed mode</span><span role="cell" class="lilac-compare__lilac">960 fps bursts at 720p, through Sony's own slow-motion service (tested on sunplex07's LineageOS 23.2)</span></div>
</div>

How each step works, with diagrams and charts: [How a picture is made](user/how-pictures-are-made.md).

What that gives, measured side by side on this phone:

- In a scene with a bright window and monitor in a dark room, a 13-frame Pro burst kept most of the highlight detail, while Sony's own JPEG of the same exposure was white there and had about three times the fine noise.
- In a dark room, 10 to 20 stacked front-camera frames reached 1.6 to 2.3 times the signal-to-noise ratio of Sony's own camera app.

These are tests of one scene each, not a promise for every photo; how to get the most from each mode is in the guide.

### Lilac and GCam

If you have used GCam, Google's camera app ported to other phones, the ideas will be familiar: Lilac's Pro mode is built on the same HDR+ pipeline. PhotonCamera rebuilt it from the GCam ports MGC and AGC to run through Android's standard camera interface on the graphics chip, and Lilac carries it with GCam's own model files:

| GCam | In Lilac |
|---|---|
| HDR+: a burst of darker RAW frames, merged | Pro's HDR+ frames, and the [darker burst](user/hdr.md#the-darker-burst) |
| Sabre, the handheld multi-frame merge | the **Sabre** merge mode (default) |
| HDR+ spatial merge with bracketed frames | the **Spatial** merge mode |
| Learned auto exposure (scene models for long, short and portrait shots) | the same models decide how far shadows are lifted, capped by the [HDR ratio](user/hdr.md#the-hdr-ratio-slider) (9.8×, GCam's own default) |
| HDRNet tone mapping | the same HDRNet model |
| GCam's denoise tuning | its luma, chroma and Sabre denoise settings |

What Lilac adds for this phone:

- **It fits in 4 GB.** Pro photos are developed one at a time in a separate process that waits for free memory, instead of all at once in the camera.
- **Stacking without RAW.** Photo, Night and Portrait stack the processed frames, so they work on every lilac ROM; Pro is there when the ROM gives RAW.
- **Sony's camera features** that GCam does not know: subject tracking, the laser distance sensor, smile scores, the front camera's Accurate colour, and 960 fps slow motion.

### 960 fps slow motion

Press the camera key while recording video, and a burst at 960 frames a second plays back 32 times slower: 720p, one burst per recording, in bright light.

!!! note "Only tested on sunplex07's LineageOS 23.2"
    Slow motion runs through Sony's own slow-motion service (cacao), which that ROM keeps from Sony's software, together with the Lilac Sony Bridge. It has not been tested on any other ROM. See [slow motion](user/video.md#slow-motion).

### Made for a 4.6-inch screen

Lilac's interface is not PhotonCamera's: it was rebuilt for small screens, with every control placed for one thumb:

- **No top bar.** The picture starts at the top edge; the top line shows only settings you changed.
- **Everything at the bottom.** Modes are on the shutter itself (swipe it sideways), the zoom ring sits right above it, and quick settings rise from the bottom.
- **Gestures on the picture:** swipe down for the everyday settings, up for manual controls, sideways for looks.
- **Short words.** Every label and message is written to fit the screen in one line or two.
- **The editor too:** sections along the bottom, slim rulers you drag with one finger, controls that fade while your finger is on the photo, and a full view with nothing on top.

### Built for 4 GB of memory

Developing one 19 MP RAW needs about 1 GB. Lilac develops Pro photos one at a time in a separate process that waits for free memory, keeps its progress, and carries on when you swipe the app away. You can see the line and pause it.

### Uses the camera Sony built

Lilac uses Sony's own face detection, subject tracking, laser distance sensor, smile scores and two-stage camera key, the way Sony's camera app does. See [Sony hardware](user/hardware.md).

## Which phone and ROM

- **Phone:** Xperia XZ1 Compact (lilac). Other phones are not tested.
- **ROM:** any lilac ROM runs Photo, Night, Portrait and Video. Pro needs RAW, which comes from the raw19 shim in sunplex07's unofficial LineageOS 23.2 for lilac. Without it, Pro does not appear on the mode dial. Slow motion has only been tested on that ROM: it needs Sony's cacao slow-motion service, which the ROM keeps from Sony's software, and the Lilac Sony Bridge.
- **Android:** 11 or newer.

## How to get it

- **Stable:** comes inside lilac ROMs that include it. It updates with the ROM.
- **Beta:** published as GitHub releases of this project. It installs beside the ROM's copy under its own name, so both can be on the phone.

!!! note
    Lilac covers the features this fork changed. The colour tools and other features it shares with PhotonCamera work as before and are not covered here.

## Guide

**How it works**

- [How a picture is made](user/how-pictures-are-made.md): the camera's streams, stacking, RAW, the darker burst, memory
- [Focus and faces](user/focus.md): tap focus, the camera key, dim light, the two face detectors
- [Sony hardware](user/hardware.md): what Lilac uses from the camera, and vendor keys

**Using Lilac**

- [Getting started](user/getting-started.md): the camera screen, modes, quick settings, manual controls and symbols
- [Photo and Night](user/photo-mode.md): how many frames, 12 MP and 2 MP, front camera colour
- [Pro](user/pro-mode.md): HDR+ frames, the render queue, Photo or Pro
- [HDR](user/hdr.md): the darker burst and the HDR ratio
- [Portrait](user/portrait.md): the live guide, the blur after the shot, Quick or Depth
- [Video](user/video.md): stabilisation, slow motion, overheating
- [Editor](user/editor.md): editing photos and developing Pro photos again

[Troubleshooting](user/troubleshooting.md): every message, what it means and what to do.
