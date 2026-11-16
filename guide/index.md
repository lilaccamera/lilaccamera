---
icon: lucide/house
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

<a class="lilac-next" href="user/how-pictures-are-made/">Next: how a picture is made, from sensor to stacked photo</a>

## Rebuilt for a 4.6-inch screen { .lilac-step data-step="2" }

Lilac's interface is not PhotonCamera's. The camera and the editor were rebuilt around one rule: a 4.6-inch phone held in one hand.

<div class="lilac-split" markdown="0">
<figure class="lilac-fig lilac-fig--phone">
<svg viewBox="47 0 248 421" role="img" aria-labelledby="tz-t tz-d" class="phone">
<title id="tz-t">Lilac in one hand</title>
<desc id="tz-d">The Xperia XZ1 Compact with Lilac's camera screen. An animation shows how far a right thumb reaches from the lower edge, and a touch point moving between the buttons, all inside that reach. Numbered as in the list: 1 thumb reach, 2 picture gestures, 3 shutter with the zoom ring, 4 camera key and volume keys on the right side.</desc>
<defs><linearGradient id="xz-g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5f7189"/><stop offset=".6" stop-color="#8d8790"/><stop offset="1" stop-color="#4a4450"/></linearGradient>
<linearGradient id="xz-face" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#D7D7D7"/></linearGradient>
<marker id="tz-a" viewBox="0 0 8 8" refX="6" refY="4" markerWidth="4" markerHeight="4" orient="auto-start-reverse"><path d="M0 0L8 4L0 8z" fill="#fff" fill-opacity=".85"/></marker></defs>
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
<clipPath id="tz-clip"><rect x="83.6" y="49.0" width="172.8" height="307.2"/></clipPath>
<radialGradient id="tz-r" gradientUnits="userSpaceOnUse" cx="258.8" cy="385.0" r="201.6"><stop offset="0" stop-color="#B48BF5" stop-opacity=".55"/><stop offset=".82" stop-color="#B48BF5" stop-opacity=".28"/><stop offset="1" stop-color="#B48BF5" stop-opacity="0"/></radialGradient>
<g clip-path="url(#tz-clip)" class="tz-anim" opacity="0">
<animate attributeName="opacity" dur="9s" repeatCount="indefinite" values="0;1;1;0;0" keyTimes="0;.1;.72;.82;1"/>
<circle cx="258.8" cy="385.0" r="201.6" fill="url(#tz-r)"/>
<g><animateMotion dur="9s" repeatCount="indefinite" values="170.0,326.0;170.0,326.0;170.0,295.3;237.2,296.7;237.2,326.0;102.8,326.0;102.8,296.7;170.0,326.0;170.0,326.0" keyTimes="0;0.120;0.217;0.313;0.410;0.507;0.603;0.700;1" calcMode="spline" keySplines=".45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1"/>
<ellipse rx="7.2" ry="8.6" fill="#fff" fill-opacity=".45" stroke="#fff" stroke-opacity=".9" stroke-width="1.2"/>
</g>
</g>
<path d="M170.0 111.4L170.0 207.4" stroke="#fff" stroke-opacity=".85" stroke-width="2" stroke-linecap="round" marker-start="url(#tz-a)" marker-end="url(#tz-a)"/>
<path d="M126.8 159.4L213.2 159.4" stroke="#fff" stroke-opacity=".85" stroke-width="2" stroke-linecap="round" marker-start="url(#tz-a)" marker-end="url(#tz-a)"/>
<rect x="268.0" y="182.9" width="2.6" height="36.2" rx="1" class="xz-key"/>
<rect x="268.0" y="231.2" width="2.6" height="42.2" rx="1" class="xz-key xz-key--on"/>
<rect x="268.0" y="333.7" width="2.6" height="24.1" rx="1" class="xz-key xz-key--on"/>
<path d="M56.0 274.6L90.3 274.6" class="key-line"/><circle cx="56.0" cy="274.6" r="7" class="key-dot"/><text x="56.0" y="278.0" text-anchor="middle" class="key-num">1</text>
<path d="M56.0 121.0L160.4 121.0" class="key-line"/><circle cx="56.0" cy="121.0" r="7" class="key-dot"/><text x="56.0" y="124.4" text-anchor="middle" class="key-num">2</text>
<path d="M170.0 411.0L170.0 345.2" class="key-line"/><circle cx="170.0" cy="411.0" r="7" class="key-dot"/><text x="170.0" y="414.4" text-anchor="middle" class="key-num">3</text>
<path d="M286.0 309.6L270.6 345.8" class="key-line"/><circle cx="286.0" cy="309.6" r="7" class="key-dot"/><text x="286.0" y="313.0" text-anchor="middle" class="key-num">4</text>
</svg>
<figcaption>Held in the right hand, to scale. The glow is how far the thumb reaches.</figcaption>
</figure>
<ol class="lilac-key">
<li><b>Everything you press is in thumb reach.</b> Holding the phone in one hand, your thumb reaches the lower part of the screen, shaded in the animation. Every button sits there; the top of the screen is only picture.</li>
<li><b>The picture takes the gestures:</b> swipe down for everyday settings, up for manual controls, sideways for looks.</li>
<li><b>The shutter is the mode dial</b>, with the zoom ring curved round its top like a compact camera's zoom lever.</li>
<li><b>The side keys are camera controls:</b> half-press the camera key to focus, press fully to shoot; the volume keys shoot or zoom.</li>
</ol>
</div>

<a class="lilac-next" href="user/small-screen/">Next: how the camera screen and the editor are laid out</a>

## 960 fps slow motion is back { .lilac-step data-step="3" }

The XZ1 Compact's sensor records at 960 frames a second, but on custom ROMs that went away with Sony's camera app. sunplex07's LineageOS 23.2 keeps Sony's slow-motion service, cacao, and Lilac reaches it through its companion app, the Lilac Sony Bridge. So 960 fps works again.

!!! note "Only tested on sunplex07's LineageOS 23.2"
    Other ROMs may not ship Sony's cacao service.

<a class="lilac-next" href="user/video/#slow-motion">Next: slow motion in Video mode, and its limits</a>

## Get Lilac

--8<-- "rom-requirements.md"

Other phones are not tested. Testers can add the bridge themselves: [install the bridge](bridge/install.md). ROM maintainers: [ROM integration](rom/integration.md).

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
