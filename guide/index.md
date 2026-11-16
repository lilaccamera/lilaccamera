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
<a class="lilac-btn lilac-btn--outline" href="rom/integration/">For ROM maintainers</a>
</div>
</div>
</div>

Lilac is a camera app for one phone. It does three things a default camera app cannot:

<div class="lilac-cards lilac-cards--steps" markdown="0">
<a class="lilac-card" href="#the-best-of-both-worlds"><span class="lilac-card__num">1</span><strong>The best of both worlds</strong><span>GCam's photo processing on Sony's camera hardware</span></a>
<a class="lilac-card" href="#rebuilt-for-a-46-inch-screen"><span class="lilac-card__num">2</span><strong>Built for a small screen</strong><span>Camera and editor rebuilt for one thumb on 4.6 inches</span></a>
<a class="lilac-card" href="#960-fps-slow-motion-is-back"><span class="lilac-card__num">3</span><strong>960 fps slow motion</strong><span>Sony's super slow motion, working again on a custom ROM</span></a>
</div>

## The best of both worlds { .lilac-step data-step="1" }

GCam processes photos well but knows nothing about Sony's camera. Sony's camera app uses the hardware fully but does not run on custom ROMs. Lilac brings the two together: GCam's HDR+ processing, and the Sony camera features that only Sony's own app used, such as subject tracking, the laser distance sensor and 960 fps slow motion.

Compared with a default camera app:

<div class="lilac-compare" markdown="0" role="table" aria-label="A default camera app compared with Lilac">
<div class="lilac-compare__row lilac-compare__row--head" role="row"><span role="columnheader" class="lilac-compare__topic"></span><span role="columnheader">Default camera app<small>such as Aperture</small></span><span role="columnheader" class="lilac-compare__lilac">Lilac</span></div>
<div class="lilac-compare__row" role="row"><span role="rowheader" class="lilac-compare__topic"><img class="icon" src="icons/StackLine.svg" alt="">Each photo</span><span role="cell">One finished JPEG from Sony's processor</span><span role="cell" class="lilac-compare__lilac">3 to 20 frames merged into one: less noise, more detail</span></div>
<div class="lilac-compare__row" role="row"><span role="rowheader" class="lilac-compare__topic"><img class="icon" src="icons/SensorLine.svg" alt="">Starting point</span><span role="cell">Sony's picture, noise already smoothed away with detail</span><span role="cell" class="lilac-compare__lilac">RAW frames merged before any processing <span class="lilac-tag lilac-tag--gcam">GCam</span></span></div>
<div class="lilac-compare__row" role="row"><span role="rowheader" class="lilac-compare__topic"><img class="icon" src="icons/SunHighLine.svg" alt="">Bright skies</span><span role="cell">One exposure: what clips stays white</span><span role="cell" class="lilac-compare__lilac">A darker RAW burst; HDRNet lifts the shadows <span class="lilac-tag lilac-tag--gcam">GCam</span></span></div>
<div class="lilac-compare__row" role="row"><span role="rowheader" class="lilac-compare__topic"><img class="icon" src="icons/MoonStars.svg" alt="">Dim light</span><span role="cell">One frame, heavily smoothed</span><span role="cell" class="lilac-compare__lilac">Night: 6 to 12 frames merged and brightened</span></div>
<div class="lilac-compare__row" role="row"><span role="rowheader" class="lilac-compare__topic"><img class="icon" src="icons/FocusLine.svg" alt="">Focus</span><span role="cell">Tap a point</span><span role="cell" class="lilac-compare__lilac">Subject tracking, face choice, laser distance <span class="lilac-tag lilac-tag--sony">Sony</span></span></div>
<div class="lilac-compare__row" role="row"><span role="rowheader" class="lilac-compare__topic"><img class="icon" src="icons/MovieCamera.svg" alt="">Slow motion</span><span role="cell">None</span><span role="cell" class="lilac-compare__lilac">960 fps at 720p <span class="lilac-tag lilac-tag--sony">Sony</span></span></div>
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

    One scene each, not a promise for every photo.

<a class="lilac-next" href="user/how-pictures-are-made/">Next: how a picture is made, from sensor to stacked photo</a>

## Rebuilt for a 4.6-inch screen { .lilac-step data-step="2" }

Lilac's interface is not PhotonCamera's: the camera and the editor were rebuilt so every control is in reach of one thumb on a small phone.

<figure class="lilac-fig lilac-fig--strip" markdown="0">
<svg viewBox="0 0 340 161" role="img" aria-labelledby="fs-t fs-d" class="footer-strip">
<title id="fs-t">The bottom of Lilac's camera screen</title>
<desc id="fs-d">The bottom of the picture, then the black footer: quick settings on the left and looks on the right, level with the zoom ring that curves round the top of the shutter; below them the last photo, the shutter with the next modes' marks beside it, and the camera switch.</desc>
<defs><linearGradient id="fs-g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6d6670"/><stop offset="1" stop-color="#4a4450"/></linearGradient>
<linearGradient id="fs-face" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#D7D7D7"/></linearGradient></defs>
<rect x="0" y="0" width="340" height="160.6" rx="12" fill="#000"/>
<path d="M0 12A12 12 0 0 1 12 0H328A12 12 0 0 1 340 12V9.4H0Z" fill="url(#fs-g)"/>
<path d="M121.5 50.8A69.9 69.9 0 0 1 218.5 50.8A12.3 12.3 0 0 1 201.5 68.4A45.3 45.3 0 0 0 138.5 68.4A12.3 12.3 0 0 1 121.5 50.8Z" fill="rgba(0,0,0,.4)" stroke="#C9C7BF" stroke-width="1.89"/>
<path d="M155.1 36.6A66.1 66.1 0 0 1 184.9 36.6A8.5 8.5 0 0 1 181.0 53.2A49.1 49.1 0 0 0 159.0 53.2A8.5 8.5 0 0 1 155.1 36.6Z" fill="rgba(255,255,255,.16)"/>
<text x="139.5" y="55.7" text-anchor="middle" style="fill:#9C9A92;font-size:9.4px;font-weight:500">W</text>
<text x="200.5" y="55.7" text-anchor="middle" style="fill:#9C9A92;font-size:9.4px;font-weight:500">T</text>
<text x="170.0" y="47.7" text-anchor="middle" style="fill:#EF9F27;font-size:11.3px;font-weight:600">1×</text>
<image href="icons/Sliders.svg" x="26.4" y="32.1" width="22.7" height="22.7" class="ui-icon"/>
<image href="icons/Film.svg" x="290.9" y="32.1" width="22.7" height="22.7" class="ui-icon"/>
<rect x="16.1" y="79.3" width="43.4" height="43.4" rx="11.3" fill="none" stroke="#C9C7BF" stroke-width="1.89"/>
<rect x="20.8" y="84.1" width="34.0" height="34.0" rx="7.6" fill="url(#fs-g)"/>
<text x="103.9" y="105.8" text-anchor="middle" opacity="0.75" style="fill:#D3D1C7;font-size:13.2px;font-weight:500">M</text>
<path d="M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4zM5 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z" transform="translate(224.8 89.7) scale(0.944)" fill="none" stroke="#E24B4A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="170.0" cy="101.1" r="36.8" fill="none" stroke="#C9C7BF" stroke-width="1.89"/><circle cx="170.0" cy="101.1" r="34.0" fill="url(#fs-face)"/>
<rect x="280.5" y="79.3" width="43.4" height="43.4" rx="11.3" fill="none" stroke="#C9C7BF" stroke-width="1.89"/>
<image href="icons/TurningArrows.svg" x="289.9" y="88.8" width="24.6" height="24.6" class="ui-icon"/>
</svg>
<figcaption>The bottom of the camera screen: zoom ring, mode dial and buttons, all in thumb reach.</figcaption>
</figure>

<a class="lilac-next" href="user/small-screen/">Next: how the camera screen and the editor are laid out</a>

## 960 fps slow motion is back { .lilac-step data-step="3" }

The XZ1 Compact's sensor records at 960 frames a second, but on custom ROMs that went away with Sony's camera app. sunplex07's LineageOS 23.2 keeps Sony's slow-motion service, cacao, and Lilac reaches it through its companion app, the Lilac Sony Bridge. So 960 fps works again: 720p bursts that play back 32 times slower.

!!! note "Only tested on sunplex07's LineageOS 23.2"
    Other ROMs may not ship Sony's cacao service.

<a class="lilac-next" href="user/video/#slow-motion">Next: slow motion in Video mode, and its limits</a>

## Get Lilac

| Feature | Needs | Tested on |
|---|---|---|
| Photo, Night, Portrait, Video | any lilac ROM, Android 11 or newer | |
| Pro (RAW) | the raw19 camera shim | sunplex07's LineageOS 23.2 |
| 960 fps slow motion | Sony's cacao service and the [Lilac Sony Bridge](bridge/install.md) | sunplex07's LineageOS 23.2 only |

On a ROM without the shim, Pro does not appear on the mode dial. Other phones are not tested.

- **Stable:** inside lilac ROMs that include it; it updates with the ROM.
- **Beta:** GitHub releases of this project. It installs beside the ROM's copy under its own name.

## The guide

<div class="lilac-map" markdown="0">
<div><h3>How Lilac works</h3><ul>
<li><a href="user/how-pictures-are-made/">How a picture is made</a></li>
<li><a href="user/small-screen/">Built for a small screen</a></li>
<li><a href="user/focus/">Focus and faces</a></li>
<li><a href="user/hardware/">Sony hardware</a></li>
</ul></div>
<div><h3>Using Lilac</h3><ul>
<li><a href="user/getting-started/">Getting started</a></li>
<li><a href="user/photo-mode/">Photo and Night</a></li>
<li><a href="user/pro-mode/">Pro</a> · <a href="user/hdr/">HDR</a></li>
<li><a href="user/portrait/">Portrait</a> · <a href="user/video/">Video</a></li>
<li><a href="user/editor/">Editor</a></li>
<li><a href="user/troubleshooting/">Troubleshooting</a></li>
</ul></div>
<div><h3>Testers and ROMs</h3><ul>
<li><a href="bridge/install/">Slow-motion bridge</a></li>
<li><a href="rom/integration/">ROM integration</a></li>
</ul></div>
</div>

The guide explains what Lilac does differently. Camera basics, and the colour tools Lilac shares with PhotonCamera, are not covered.

<p class="lilac-credit">Lilac Camera is a fork of <a href="https://github.com/bjzhou/PhotonCamera">PhotonCamera</a> by bjzhou, with an interface rebuilt for small phone screens and processing adapted for low-memory phones.</p>
