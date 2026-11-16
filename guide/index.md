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
<svg viewBox="0 0 340 264" role="img" aria-labelledby="ph-t ph-d" class="phone">
<title id="ph-t">Lilac's camera screen on a 4.6-inch phone</title>
<desc id="ph-d">The camera screen at the phone's 720 by 1280 proportions. The picture fills the top, with a thin line of marks for settings you changed. Swipe down on it for everyday settings, up for manual controls, sideways for looks. Below it: quick settings, the zoom ring and the looks button; then the last photo, the shutter with the mode dial, and the camera switch.</desc>
<defs><linearGradient id="ph-g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5b6f8f"/><stop offset=".55" stop-color="#8a7f86"/><stop offset="1" stop-color="#3a3340"/></linearGradient>
<marker id="ph-a" viewBox="0 0 8 8" refX="6" refY="4" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0L8 4L0 8z" fill="#fff"/></marker></defs>
<rect x="100" y="6" width="140" height="243.6" rx="16" class="bezel"/>
<rect x="106" y="14" width="128" height="227.6" fill="#000"/>
<rect x="106" y="14" width="128" height="170.7" fill="url(#ph-g)"/>
<text x="170.0" y="24" text-anchor="middle" class="ui-text">3s · 12 lux</text>
<path d="M130 50V80" class="gesture" marker-end="url(#ph-a)"/>
<path d="M210 146V116" class="gesture" marker-end="url(#ph-a)"/>
<path d="M156.0 166H128" class="gesture" marker-end="url(#ph-a)"/><path d="M184.0 166H212" class="gesture" marker-end="url(#ph-a)"/>
<image href="icons/Sliders.svg" x="114.0" y="193.7" width="12" height="12" class="ui-icon"/>
<image href="icons/Film.svg" x="214.0" y="193.7" width="12" height="12" class="ui-icon"/>
<rect x="144.0" y="193.66666666666666" width="52" height="12" rx="6" class="ui-ring"/><text x="170.0" y="202.66666666666666" text-anchor="middle" class="ui-text">W  1×  T</text>
<rect x="111" y="219.66666666666666" width="18" height="18" rx="4" fill="url(#ph-g)" stroke="#fff" stroke-width="1.2"/>
<circle cx="170.0" cy="228.66666666666666" r="13" class="ui-shutter"/><circle cx="170.0" cy="228.66666666666666" r="10" fill="#fff"/>
<text x="146.0" y="231.66666666666666" text-anchor="middle" class="ui-mark">M</text><text x="194.0" y="231.66666666666666" text-anchor="middle" class="ui-mark">▶</text>
<image href="icons/TurningArrows.svg" x="213.5" y="222.2" width="13" height="13" class="ui-icon"/>
<path d="M88.0 20.0L150.0 21.0" class="leader"/><circle cx="150.0" cy="21.0" r="2" class="leader-dot"/><text x="92" y="24" text-anchor="end" class="sub"><tspan x="92" dy="0">Only changed</tspan><tspan x="92" dy="12">settings show</tspan></text>
<path d="M88.0 62.0L130.0 64.0" class="leader"/><circle cx="130.0" cy="64.0" r="2" class="leader-dot"/><text x="92" y="66" text-anchor="end" class="sub"><tspan x="92" dy="0">Swipe down:</tspan><tspan x="92" dy="12">everyday row</tspan></text>
<path d="M88.0 160.0L130.0 166.0" class="leader"/><circle cx="130.0" cy="166.0" r="2" class="leader-dot"/><text x="92" y="164" text-anchor="end" class="sub"><tspan x="92" dy="0">Swipe sideways:</tspan><tspan x="92" dy="12">next look</tspan></text>
<path d="M88.0 199.7L120.0 199.7" class="leader"/><circle cx="120.0" cy="199.7" r="2" class="leader-dot"/><text x="92" y="203.66666666666666" text-anchor="end" class="sub"><tspan x="92" dy="0">Quick settings</tspan></text>
<path d="M88.0 228.7L120.0 228.7" class="leader"/><circle cx="120.0" cy="228.7" r="2" class="leader-dot"/><text x="92" y="232.66666666666666" text-anchor="end" class="sub"><tspan x="92" dy="0">Last photo</tspan></text>
<path d="M252.0 124.0L210.0 130.0" class="leader"/><circle cx="210.0" cy="130.0" r="2" class="leader-dot"/><text x="248" y="128" text-anchor="start" class="sub"><tspan x="248" dy="0">Swipe up:</tspan><tspan x="248" dy="12">manual controls</tspan></text>
<path d="M252.0 181.7L190.0 197.7" class="leader"/><circle cx="190.0" cy="197.7" r="2" class="leader-dot"/><text x="248" y="185.66666666666666" text-anchor="start" class="sub"><tspan x="248" dy="0">Zoom ring</tspan></text>
<path d="M252.0 203.7L220.0 202.7" class="leader"/><circle cx="220.0" cy="202.7" r="2" class="leader-dot"/><text x="248" y="207.66666666666666" text-anchor="start" class="sub"><tspan x="248" dy="0">Looks</tspan></text>
<path d="M252.0 234.7L181.0 234.7" class="leader"/><circle cx="181.0" cy="234.7" r="2" class="leader-dot"/><text x="248" y="238.66666666666666" text-anchor="start" class="sub"><tspan x="248" dy="0">Shutter: swipe</tspan><tspan x="248" dy="12">for modes</tspan></text>
</svg>
<figcaption>At the XZ1 Compact's proportions (720 × 1280): the picture takes the gestures, the buttons sit at the bottom.</figcaption>
</figure>

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
