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

## What Lilac does

A default camera app gets one finished photo from the phone. Lilac takes several frames and merges them, starting from the sensor's RAW data where the ROM allows. Its Pro mode runs GCam's HDR+ pipeline, and it uses Sony's own camera features.

<div class="lilac-compare" markdown="0" role="table" aria-label="A default camera app compared with Lilac">
<div class="lilac-compare__row lilac-compare__row--head" role="row"><span role="columnheader">Default camera app<small>such as Aperture</small></span><span role="columnheader" class="lilac-compare__lilac">Lilac</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/StackLine.svg" alt="">Each photo</div>
<div class="lilac-compare__row" role="row"><span role="cell">One finished JPEG from Sony's processor</span><span role="cell" class="lilac-compare__lilac">3 to 20 frames merged into one: less noise, more detail</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/SensorLine.svg" alt="">Starting point</div>
<div class="lilac-compare__row" role="row"><span role="cell">Sony's picture, noise already smoothed away with detail</span><span role="cell" class="lilac-compare__lilac">RAW frames merged before any processing <span class="lilac-tag lilac-tag--gcam">GCam</span></span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/SunHighLine.svg" alt="">Bright skies</div>
<div class="lilac-compare__row" role="row"><span role="cell">One exposure: what clips stays white</span><span role="cell" class="lilac-compare__lilac">A darker RAW burst; HDRNet lifts the shadows <span class="lilac-tag lilac-tag--gcam">GCam</span></span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/MoonStars.svg" alt="">Dim light</div>
<div class="lilac-compare__row" role="row"><span role="cell">One frame, heavily smoothed</span><span role="cell" class="lilac-compare__lilac">Night: 6 to 12 frames merged and brightened</span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/FocusLine.svg" alt="">Focus</div>
<div class="lilac-compare__row" role="row"><span role="cell">Tap a point</span><span role="cell" class="lilac-compare__lilac">Subject tracking, face choice, laser distance <span class="lilac-tag lilac-tag--sony">Sony</span></span></div>
<div class="lilac-compare__topic" role="rowheader"><img class="icon" src="icons/MovieCamera.svg" alt="">Slow motion</div>
<div class="lilac-compare__row" role="row"><span role="cell">None</span><span role="cell" class="lilac-compare__lilac">960 fps at 720p <span class="lilac-tag lilac-tag--sony">Sony</span></span></div>
</div>
<p class="lilac-legend" markdown="0"><span class="lilac-tag lilac-tag--gcam">GCam</span> the same HDR+ pipeline and models as Google's camera app &nbsp; <span class="lilac-tag lilac-tag--sony">Sony</span> Sony's own camera features</p>

??? info "What comes from GCam"
    PhotonCamera rebuilt GCam's HDR+ pipeline from the GCam ports MGC and AGC, to run through Android's standard camera interface on the graphics chip. Lilac's Pro mode carries it with GCam's own model files:

    | GCam | In Lilac |
    |---|---|
    | HDR+: a burst of darker RAW frames | Pro's HDR+ frames and the [darker burst](user/hdr.md#the-darker-burst) |
    | Sabre, the handheld merge | the **Sabre** merge mode (default) |
    | HDR+ spatial merge with brackets | the **Spatial** merge mode |
    | Learned auto exposure | the same models, capped by the [HDR ratio](user/hdr.md#the-hdr-ratio-slider) (9.8×, GCam's default) |
    | HDRNet tone mapping | the same HDRNet model |
    | Denoise tuning | GCam's luma, chroma and Sabre settings |

    What Lilac adds: Pro photos are developed one at a time in a separate process that waits for free memory, so they fit in 4 GB; Photo, Night and Portrait stack without RAW, on every ROM; and Sony's camera features that GCam does not know.

??? info "Measured on this phone"
    - A bright window and monitor in a dark room: a 13-frame Pro burst kept most of the highlight detail. Sony's own JPEG of the same exposure was white there, with about three times the fine noise.
    - A dark room: 10 to 20 stacked front-camera frames reached 1.6 to 2.3 times the signal-to-noise ratio of Sony's camera app.

    One scene each, not a promise for every photo. How each step works: [How a picture is made](user/how-pictures-are-made.md).

## Rebuilt for a 4.6-inch screen

Lilac's interface is not PhotonCamera's: it was rebuilt so every control is in reach of one thumb on a small phone.

<figure class="lilac-fig lilac-fig--phone" markdown="0">
<svg viewBox="0 0 340 421" role="img" aria-labelledby="xz-t xz-d" class="phone">
<title id="xz-t">Lilac on the Xperia XZ1 Compact</title>
<desc id="xz-d">The Xperia XZ1 Compact with Lilac's camera screen, numbered as in the key below: 1 top line, 2 picture, 3 zoom ring, 4 quick settings, 5 looks, 6 last photo, 7 shutter and mode dial, 8 camera switch.</desc>
<defs><linearGradient id="xz-g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5f7189"/><stop offset=".6" stop-color="#8d8790"/><stop offset="1" stop-color="#4a4450"/></linearGradient>
<linearGradient id="xz-face" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#D7D7D7"/></linearGradient></defs>
<rect x="72.0" y="8" width="196.0" height="389.0" rx="0" class="xz-body"/>
<rect x="73.2" y="9.2" width="193.6" height="386.6" rx="0" class="xz-glass"/>
<rect x="151.9" y="27.3" width="36.2" height="2.4" rx="1.2" class="xz-slot"/>
<circle cx="117.2" cy="28.5" r="4.8" class="xz-cam"/>
<circle cx="222.8" cy="28.5" r="1.8" class="xz-slot"/>
<rect x="151.9" y="375.4" width="36.2" height="2.4" rx="1.2" class="xz-slot"/>
<rect x="83.6" y="49.0" width="172.8" height="307.2" fill="#000"/>
<rect x="83.6" y="49.0" width="172.8" height="230.4" fill="url(#xz-g)"/>
<image href="icons/StopwatchLine.svg" x="159.4" y="53.3" width="9.6" height="9.6" class="ui-icon" opacity="1.0"/>
<text x="171.0" y="60.5" class="ui-text">3s</text>
<path d="M145.3 300.4A35.5 35.5 0 0 1 194.7 300.4A6.2 6.2 0 0 1 186.0 309.4A23.0 23.0 0 0 0 154.0 309.4A6.2 6.2 0 0 1 145.3 300.4Z" fill="rgba(0,0,0,.4)" stroke="#C9C7BF" stroke-width="0.96"/>
<path d="M162.4 293.2A33.6 33.6 0 0 1 177.6 293.2A4.3 4.3 0 0 1 175.6 301.7A25.0 25.0 0 0 0 164.4 301.7A4.3 4.3 0 0 1 162.4 293.2Z" fill="rgba(255,255,255,.16)"/>
<text x="154.5" y="303.1" text-anchor="middle" class="ui-end">W</text>
<text x="185.5" y="303.1" text-anchor="middle" class="ui-end">T</text>
<text x="170.0" y="299.1" text-anchor="middle" class="ui-knob">1×</text>
<image href="icons/Sliders.svg" x="97.0" y="290.9" width="11.5" height="11.5" class="ui-icon" opacity="1.0"/>
<image href="icons/Film.svg" x="231.4" y="290.9" width="11.5" height="11.5" class="ui-icon" opacity="1.0"/>
<rect x="91.8" y="315.0" width="22.0" height="22.0" rx="5.8" fill="none" stroke="#C9C7BF" stroke-width="0.96"/>
<rect x="94.2" y="317.3" width="17.3" height="17.3" rx="3.8" fill="url(#xz-g)"/>
<text x="136.4" y="328.5" text-anchor="middle" class="ui-mark">M</text>
<path d="M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4zM5 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z" transform="translate(197.8 320.2) scale(0.480)" fill="none" stroke="#E24B4A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="170.0" cy="326.0" r="18.7" fill="none" stroke="#C9C7BF" stroke-width="0.96"/>
<circle cx="170.0" cy="326.0" r="17.3" fill="url(#xz-face)"/>
<rect x="226.2" y="315.0" width="22.0" height="22.0" rx="5.8" fill="none" stroke="#C9C7BF" stroke-width="0.96"/>
<image href="icons/TurningArrows.svg" x="231.0" y="319.7" width="12.5" height="12.5" class="ui-icon" opacity="1.0"/>
<path d="M56.0 59.6L155.6 59.6" class="key-line"/><circle cx="56.0" cy="59.6" r="7" class="key-dot"/><text x="56.0" y="63.0" text-anchor="middle" class="key-num">1</text>
<path d="M56.0 164.2L112.4 164.2" class="key-line"/><circle cx="56.0" cy="164.2" r="7" class="key-dot"/><text x="56.0" y="167.6" text-anchor="middle" class="key-num">2</text>
<path d="M284.0 274.6L182.1 292.6" class="key-line"/><circle cx="284.0" cy="274.6" r="7" class="key-dot"/><text x="284.0" y="278.0" text-anchor="middle" class="key-num">3</text>
<path d="M56.0 296.7L97.0 296.7" class="key-line"/><circle cx="56.0" cy="296.7" r="7" class="key-dot"/><text x="56.0" y="300.1" text-anchor="middle" class="key-num">4</text>
<path d="M284.0 298.6L243.0 296.7" class="key-line"/><circle cx="284.0" cy="298.6" r="7" class="key-dot"/><text x="284.0" y="302.0" text-anchor="middle" class="key-num">5</text>
<path d="M56.0 332.2L92.2 331.3" class="key-line"/><circle cx="56.0" cy="332.2" r="7" class="key-dot"/><text x="56.0" y="335.6" text-anchor="middle" class="key-num">6</text>
<path d="M170.0 411.0L170.0 345.2" class="key-line"/><circle cx="170.0" cy="411.0" r="7" class="key-dot"/><text x="170.0" y="414.4" text-anchor="middle" class="key-num">7</text>
<path d="M284.0 337.0L247.8 332.2" class="key-line"/><circle cx="284.0" cy="337.0" r="7" class="key-dot"/><text x="284.0" y="340.4" text-anchor="middle" class="key-num">8</text>
</svg>
<figcaption>Lilac on the Xperia XZ1 Compact, to scale.</figcaption>
</figure>

<ol class="lilac-key" markdown="0">
<li><b>Top line</b> marks only settings you changed, such as a timer.</li>
<li><b>Picture</b>: tap to focus. Swipe down for everyday settings, up for manual controls, sideways for the next look.</li>
<li><b>Zoom ring</b>: pull it sideways, or tap W or T.</li>
<li><b>Quick settings</b></li>
<li><b>Looks</b></li>
<li><b>Last photo</b>; its edge shows processing.</li>
<li><b>Shutter</b>: swipe it sideways to change mode. The marks beside it are the next modes.</li>
<li><b>Switch camera</b></li>
</ol>

The editor follows the same rules: sections along the bottom, slim rulers you drag with one finger, and controls that fade while your finger is on the photo. See [Getting started](user/getting-started.md).

## 960 fps slow motion is back

The XZ1 Compact's sensor records at 960 frames a second, but on custom ROMs that went away with Sony's camera app. sunplex07's LineageOS 23.2 keeps Sony's slow-motion service, cacao, and Lilac reaches it through its companion app, the Lilac Sony Bridge. So 960 fps works again: 720p bursts that play back 32 times slower.

!!! note "Only tested on sunplex07's LineageOS 23.2"
    Other ROMs may not ship Sony's cacao service. Details: [slow motion](user/video.md#slow-motion).

## Which ROM you need

| Feature | Needs | Tested on |
|---|---|---|
| Photo, Night, Portrait, Video | any lilac ROM, Android 11 or newer | |
| Pro (RAW) | the raw19 camera shim | sunplex07's LineageOS 23.2 |
| 960 fps slow motion | Sony's cacao service and the Lilac Sony Bridge | sunplex07's LineageOS 23.2 only |

On a ROM without the shim, Pro does not appear on the mode dial. Other phones are not tested.

## How to get it

- **Stable:** inside lilac ROMs that include it; it updates with the ROM.
- **Beta:** GitHub releases of this project. It installs beside the ROM's copy under its own name.

??? note "All guide pages"
    **How it works:** [How a picture is made](user/how-pictures-are-made.md) · [Focus and faces](user/focus.md) · [Sony hardware](user/hardware.md)

    **Using Lilac:** [Getting started](user/getting-started.md) · [Photo and Night](user/photo-mode.md) · [Pro](user/pro-mode.md) · [HDR](user/hdr.md) · [Portrait](user/portrait.md) · [Video](user/video.md) · [Editor](user/editor.md) · [Troubleshooting](user/troubleshooting.md)

    The colour tools and other features Lilac shares with PhotonCamera work as before and are not covered here.
