---
icon: lucide/house
hide:
  - toc
---

<div class="lilac-hero" markdown="0">
<img class="lilac-hero__icon" src="images/lilac-icon.svg" alt="" width="96" height="96">
<div class="lilac-hero__text">
<h1>Lilac Camera</h1>
<p class="lilac-hero__tagline">Sony's camera app for the Xperia XZ1 Compact, rebuilt for custom ROMs, with GCam multi-frame processing</p>
<div class="lilac-hero__buttons">
<a class="lilac-btn lilac-btn--primary" href="user/getting-started/">Get started</a>
<a class="lilac-btn lilac-btn--outline" href="rom/integration/">For ROM maintainers</a>
</div>
</div>
</div>

Lilac is a camera app for one phone. It rebuilds Sony's camera app, with all of the camera's hardware features, for custom ROMs, and adds GCam's multi-frame processing on top.

!!! note "Fully works on sunplex07's LineageOS 23.2 only"
    Other lilac ROMs get basic multi-frame stacking in Photo, Night, Portrait and Video, because they lack the raw19 camera shim and Sony's cacao service. [What each feature needs](#get-lilac)

<div class="lilac-cards lilac-cards--steps" markdown="0">
<a class="lilac-card" href="#sonys-camera-features"><span class="lilac-card__num">1</span><strong>Sony's camera features</strong><span>Faces, tracking, laser focus, the camera key, stabilisers and 960 fps slow motion</span></a>
<a class="lilac-card" href="#gcams-multi-frame-processing"><span class="lilac-card__num">2</span><strong>GCam's processing</strong><span>HDR+ and merged frames, on Sony's camera hardware</span></a>
<a class="lilac-card" href="#get-lilac"><span class="lilac-card__num">3</span><strong>What works on your ROM</strong><span>Full on sunplex07's LineageOS, basic elsewhere</span></a>
</div>

## Sony's camera features { .lilac-step data-step="1" }

Sony's camera app uses the phone's hardware fully but does not run on custom ROMs. Lilac brings its features back: face frames, subject tracking, the laser distance sensor, the two-stage camera key, smile shutter, Sony's own photo processing, Sony's video stabilisers, and 960 fps slow motion with 60 and 120 fps video. The aim is the whole of Sony's camera app; the [Sony camera features](user/hardware.md) page lists what is done and what is not yet.

No code from Sony's camera app is in Lilac. Its Sony features were rebuilt by studying how Sony's app talks to the phone's camera system, which settings it sends and which system services it calls, and making the same requests from Lilac's own code. Where a feature needs Sony's own services, such as cacao for slow motion, Lilac calls the ones already on the phone's ROM; it does not ship them.

The XZ1 Compact's sensor records at 960 frames a second, but on custom ROMs that went away with Sony's camera app. sunplex07's LineageOS 23.2 keeps Sony's slow-motion service, cacao, and Lilac reaches it through its companion app, the Lilac Sony Bridge. So 960 fps works again.

<a class="lilac-next" href="user/hardware/">Next: Sony's camera features, done and not yet</a>

## GCam's multi-frame processing { .lilac-step data-step="2" }

GCam processes photos well but knows nothing about Sony's camera. Lilac runs GCam's HDR+ processing on Sony's camera hardware, next to the Sony features above.

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
    | Learned auto exposure | the same models, capped by the [HDR ratio](user/hdr.md#the-hdr-ratio-slider) (8× by default; GCam's default is 9.8×) |
    | HDRNet tone mapping | the same HDRNet model |
    | Denoise tuning | GCam's luma, chroma and Sabre settings |

    What Lilac adds: Pro photos are developed one at a time in a separate process that waits for free memory, so they fit in 4 GB; Photo, Night and Portrait stack without RAW, on every ROM; and Sony's camera features that GCam does not know.

<a class="lilac-next" href="user/how-pictures-are-made/">Next: how a picture is made, from sensor to stacked photo</a>

## Get Lilac { .lilac-step data-step="3" }

--8<-- "rom-requirements.md"

Other phones are not tested. Testers can add the bridge themselves: [install the bridge](bridge/install.md). ROM maintainers: [ROM integration](rom/integration.md).

- **Stable:** `LilacCamera.apk` from the [latest release](https://github.com/lilaccamera/lilaccamera/releases/latest). Lilac ROMs ship this same APK, so a ROM's copy also updates from new releases.
- **Beta:** `LilacBeta.apk` from a [pre-release](https://github.com/lilaccamera/lilaccamera/releases). It installs beside the stable copy as **Lilac Beta**.

## Rebuilt for a 4.6-inch screen

Lilac's interface is not PhotonCamera's. The camera, the gallery and the editor were rebuilt around one rule: a 4.6-inch phone held in one hand.

<div class="lilac-split" markdown="0">
<figure class="lilac-fig lilac-fig--phone">
<svg viewBox="47 0 248 421" role="img" aria-labelledby="tz-t tz-d" class="phone">
<title id="tz-t">Lilac in one hand</title>
<desc id="tz-d">The Xperia XZ1 Compact with Lilac's camera screen. An animation shows how far a right thumb reaches from the lower edge, and a touch point moving between the buttons, all inside that reach. Numbered as in the list: 1 thumb reach, 2 picture gestures, 3 shutter with the zoom control, 4 camera key and volume keys on the right side.</desc>
<defs><linearGradient id="zw-g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#1C1C1C" stop-opacity="0"/><stop offset=".5" stop-color="#1C1C1C"/><stop offset="1" stop-color="#1C1C1C" stop-opacity="0"/></linearGradient><linearGradient id="xz-g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5f7189"/><stop offset=".6" stop-color="#8d8790"/><stop offset="1" stop-color="#4a4450"/></linearGradient>
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
<image href="icons/StopwatchLine.svg" x="159.4" y="53.3" width="9.6" height="9.6" class="ui-icon ui-amber" opacity="1.0"/>
<text x="171.0" y="60.5" class="ui-text" style="fill:#FF9C00">3s</text>
<g><rect x="131.6" y="285.3" width="76.8" height="19.2" rx="6.7" fill="#000"/><rect x="135.4" y="286.3" width="69.1" height="17.3" fill="url(#zw-g)"/><rect x="131.6" y="285.3" width="76.8" height="19.2" rx="6.7" fill="none" stroke="#4A4A4A" stroke-width="0.72"/><path d="M136.7 287.7V290.1" stroke="#000" stroke-opacity="0.16" stroke-width="0.77"/><path d="M136.0 287.7V290.1" stroke="rgb(36,36,36)" stroke-opacity="0.06" stroke-width="0.72"/><path d="M136.7 299.7V302.1" stroke="#000" stroke-opacity="0.16" stroke-width="0.77"/><path d="M136.0 299.7V302.1" stroke="rgb(36,36,36)" stroke-opacity="0.06" stroke-width="0.72"/><path d="M138.3 287.7V302.1" stroke="#000" stroke-opacity="0.31" stroke-width="0.77"/><path d="M137.5 287.7V302.1" stroke="rgb(44,44,44)" stroke-opacity="0.18" stroke-width="0.72"/><path d="M140.8 287.7V302.1" stroke="#000" stroke-opacity="0.45" stroke-width="0.77"/><path d="M140.1 287.7V302.1" stroke="rgb(55,55,55)" stroke-opacity="0.33" stroke-width="0.72"/><path d="M144.3 287.7V302.1" stroke="#000" stroke-opacity="0.58" stroke-width="0.77"/><path d="M143.5 287.7V302.1" stroke="rgb(69,69,69)" stroke-opacity="0.49" stroke-width="0.72"/><path d="M148.6 287.7V290.1" stroke="#000" stroke-opacity="0.69" stroke-width="0.77"/><path d="M147.8 287.7V290.1" stroke="rgb(84,84,84)" stroke-opacity="0.65" stroke-width="0.72"/><path d="M148.6 299.7V302.1" stroke="#000" stroke-opacity="0.69" stroke-width="0.77"/><path d="M147.8 299.7V302.1" stroke="rgb(84,84,84)" stroke-opacity="0.65" stroke-width="0.72"/><path d="M153.5 287.7V302.1" stroke="#000" stroke-opacity="0.78" stroke-width="0.77"/><path d="M152.7 287.7V302.1" stroke="rgb(98,98,98)" stroke-opacity="0.79" stroke-width="0.72"/><path d="M158.9 287.7V302.1" stroke="#000" stroke-opacity="0.85" stroke-width="0.77"/><path d="M158.2 287.7V302.1" stroke="rgb(109,109,109)" stroke-opacity="0.91" stroke-width="0.72"/><path d="M164.8 287.7V302.1" stroke="#000" stroke-opacity="0.89" stroke-width="0.77"/><path d="M164.0 287.7V302.1" stroke="rgb(117,117,117)" stroke-opacity="0.98" stroke-width="0.72"/><path d="M170.8 287.7V290.1" stroke="#000" stroke-opacity="0.90" stroke-width="0.77"/><path d="M170.0 287.7V290.1" stroke="rgb(120,120,120)" stroke-opacity="1.00" stroke-width="0.72"/><path d="M170.8 299.7V302.1" stroke="#000" stroke-opacity="0.90" stroke-width="0.77"/><path d="M170.0 299.7V302.1" stroke="rgb(120,120,120)" stroke-opacity="1.00" stroke-width="0.72"/><path d="M176.8 287.7V302.1" stroke="#000" stroke-opacity="0.89" stroke-width="0.77"/><path d="M176.0 287.7V302.1" stroke="rgb(117,117,117)" stroke-opacity="0.98" stroke-width="0.72"/><path d="M182.6 287.7V302.1" stroke="#000" stroke-opacity="0.85" stroke-width="0.77"/><path d="M181.8 287.7V302.1" stroke="rgb(109,109,109)" stroke-opacity="0.91" stroke-width="0.72"/><path d="M188.1 287.7V302.1" stroke="#000" stroke-opacity="0.78" stroke-width="0.77"/><path d="M187.3 287.7V302.1" stroke="rgb(98,98,98)" stroke-opacity="0.79" stroke-width="0.72"/><path d="M193.0 287.7V290.1" stroke="#000" stroke-opacity="0.69" stroke-width="0.77"/><path d="M192.2 287.7V290.1" stroke="rgb(84,84,84)" stroke-opacity="0.65" stroke-width="0.72"/><path d="M193.0 299.7V302.1" stroke="#000" stroke-opacity="0.69" stroke-width="0.77"/><path d="M192.2 299.7V302.1" stroke="rgb(84,84,84)" stroke-opacity="0.65" stroke-width="0.72"/><path d="M197.2 287.7V302.1" stroke="#000" stroke-opacity="0.58" stroke-width="0.77"/><path d="M196.5 287.7V302.1" stroke="rgb(69,69,69)" stroke-opacity="0.49" stroke-width="0.72"/><path d="M200.7 287.7V302.1" stroke="#000" stroke-opacity="0.45" stroke-width="0.77"/><path d="M199.9 287.7V302.1" stroke="rgb(55,55,55)" stroke-opacity="0.33" stroke-width="0.72"/><path d="M203.2 287.7V302.1" stroke="#000" stroke-opacity="0.31" stroke-width="0.77"/><path d="M202.5 287.7V302.1" stroke="rgb(44,44,44)" stroke-opacity="0.18" stroke-width="0.72"/><path d="M204.8 287.7V290.1" stroke="#000" stroke-opacity="0.16" stroke-width="0.77"/><path d="M204.0 287.7V290.1" stroke="rgb(36,36,36)" stroke-opacity="0.06" stroke-width="0.72"/><path d="M204.8 299.7V302.1" stroke="#000" stroke-opacity="0.16" stroke-width="0.77"/><path d="M204.0 299.7V302.1" stroke="rgb(36,36,36)" stroke-opacity="0.06" stroke-width="0.72"/><text transform="translate(170.0 294.9) scale(1.00 1)" y="2.7" text-anchor="middle" font-family="Roboto Condensed,Arial Narrow,sans-serif" font-stretch="condensed" font-size="7.7" font-weight="700" fill="#fff" fill-opacity="1.00">1</text><text transform="translate(192.2 294.9) scale(0.77 1)" y="2.7" text-anchor="middle" font-family="Roboto Condensed,Arial Narrow,sans-serif" font-stretch="condensed" font-size="7.7" font-weight="700" fill="#9C9A92" fill-opacity="0.67">2</text><path d="M167.1 308.3H172.9L170.0 305.0Z" fill="#FF9C00"/></g>
<image href="icons/Sliders.svg" x="97.0" y="289.2" width="11.5" height="11.5" class="ui-icon" opacity="1.0"/>
<image href="icons/Film.svg" x="231.4" y="289.2" width="11.5" height="11.5" class="ui-icon" opacity="1.0"/>
<rect x="91.8" y="318.4" width="22.0" height="22.0" rx="5.8" fill="none" stroke="#C9C7BF" stroke-width="0.96"/>
<rect x="94.2" y="320.7" width="17.3" height="17.3" rx="3.8" fill="url(#xz-g)"/>
<text x="136.4" y="331.9" text-anchor="middle" class="ui-mark">M</text>
<path d="M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4zM5 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z" transform="translate(197.8 323.6) scale(0.480)" fill="none" stroke="#E24B4A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="170.0" cy="329.4" r="18.7" fill="none" stroke="#C9C7BF" stroke-width="0.96"/>
<circle cx="170.0" cy="329.4" r="17.3" fill="url(#xz-face)"/>
<rect x="226.2" y="318.4" width="22.0" height="22.0" rx="5.8" fill="none" stroke="#C9C7BF" stroke-width="0.96"/>
<image href="icons/TurningArrows.svg" x="231.0" y="323.1" width="12.5" height="12.5" class="ui-icon" opacity="1.0"/>
<clipPath id="tz-clip"><rect x="83.6" y="49.0" width="172.8" height="307.2"/></clipPath>
<radialGradient id="tz-r" gradientUnits="userSpaceOnUse" cx="258.8" cy="385.0" r="201.6"><stop offset="0" stop-color="#B48BF5" stop-opacity=".55"/><stop offset=".82" stop-color="#B48BF5" stop-opacity=".28"/><stop offset="1" stop-color="#B48BF5" stop-opacity="0"/></radialGradient>
<g clip-path="url(#tz-clip)" class="tz-anim" opacity="0">
<animate attributeName="opacity" dur="9s" repeatCount="indefinite" values="0;1;1;0;0" keyTimes="0;.1;.72;.82;1"/>
<circle cx="258.8" cy="385.0" r="201.6" fill="url(#tz-r)"/>
<g><animateMotion dur="9s" repeatCount="indefinite" values="170.0,329.4;170.0,329.4;170.0,294.9;237.2,294.9;237.2,329.4;102.8,329.4;102.8,294.9;170.0,329.4;170.0,329.4" keyTimes="0;0.120;0.217;0.313;0.410;0.507;0.603;0.700;1" calcMode="spline" keySplines=".45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1;.45 0 .25 1"/>
<ellipse rx="7.2" ry="8.6" fill="#fff" fill-opacity=".45" stroke="#fff" stroke-opacity=".9" stroke-width="1.2"/>
</g>
</g>
<path d="M170.0 111.4L170.0 207.4" stroke="#fff" stroke-opacity=".85" stroke-width="2" stroke-linecap="round" marker-start="url(#tz-a)" marker-end="url(#tz-a)"/>
<path d="M126.8 159.4L213.2 159.4" stroke="#fff" stroke-opacity=".85" stroke-width="2" stroke-linecap="round" marker-start="url(#tz-a)" marker-end="url(#tz-a)"/>
<rect x="268.0" y="140.0" width="2.6" height="36.2" rx="1" class="xz-key"/>
<rect x="268.0" y="78.0" width="2.6" height="42.2" rx="1" class="xz-key xz-key--on"/>
<rect x="268.0" y="333.7" width="2.6" height="24.1" rx="1" class="xz-key xz-key--on"/>
<path d="M56.0 274.6L90.3 274.6" class="key-line"/><circle cx="56.0" cy="274.6" r="7" class="key-dot"/><text x="56.0" y="278.0" text-anchor="middle" class="key-num">1</text>
<path d="M56.0 121.0L160.4 121.0" class="key-line"/><circle cx="56.0" cy="121.0" r="7" class="key-dot"/><text x="56.0" y="124.4" text-anchor="middle" class="key-num">2</text>
<path d="M170.0 411.0L170.0 348.6" class="key-line"/><circle cx="170.0" cy="411.0" r="7" class="key-dot"/><text x="170.0" y="414.4" text-anchor="middle" class="key-num">3</text>
<path d="M286.0 309.6L270.6 345.8M286.0 302.6V99.0H270.6" class="key-line"/><circle cx="286.0" cy="309.6" r="7" class="key-dot"/><text x="286.0" y="313.0" text-anchor="middle" class="key-num">4</text>
</svg>
<figcaption>Held in the right hand, to scale. The glow is how far the thumb reaches.</figcaption>
</figure>
<ol class="lilac-key">
<li><b>Everything you press is in thumb reach.</b> Holding the phone in one hand, your thumb reaches the lower part of the screen, shaded in the animation. Every button sits there; the top of the screen is only picture.</li>
<li><b>The picture takes the gestures:</b> swipe down for everyday settings, up for manual controls, sideways for looks.</li>
<li><b>The shutter is the mode dial</b>, with the zoom control just above it, in reach of the thumb.</li>
<li><b>The side keys are camera controls:</b> half-press the camera key to focus, press fully to shoot; the volume keys shoot or zoom.</li>
</ol>
</div>

<a class="lilac-next" href="user/small-screen/">Next: how the camera screen and the editor are laid out</a>

## The guide

<div class="lilac-map" markdown="0">
<div><h3>How Lilac works</h3><ul>
<li><a href="user/hardware/">Sony camera features</a></li>
<li><a href="user/how-pictures-are-made/">How a picture is made</a></li>
<li><a href="user/small-screen/">Built for a small screen</a></li>
<li><a href="user/focus/">Focus and faces</a></li>
<li><a href="user/optimizations/">Optimizations and tweaks</a></li>
</ul></div>
<div><h3>Using Lilac</h3><ul>
<li><a href="user/getting-started/">Getting started</a></li>
<li><a href="user/photo-mode/">Photo and Night</a></li>
<li><a href="user/pro-mode/">Pro</a> · <a href="user/hdr/">HDR</a></li>
<li><a href="user/portrait/">Portrait</a> · <a href="user/video/">Video</a></li>
<li><a href="user/gallery/">Gallery and viewer</a> · <a href="user/editor/">Editor</a></li>
<li><a href="user/troubleshooting/">Troubleshooting</a></li>
</ul></div>
<div><h3>Testers and ROMs</h3><ul>
<li><a href="bridge/install/">Sony bridge</a></li>
<li><a href="rom/integration/">ROM integration</a></li>
</ul></div>
</div>

The guide explains what Lilac does differently. Camera basics, and the colour tools Lilac shares with PhotonCamera, are not covered.

<p class="lilac-credit">Lilac Camera is a fork of <a href="https://github.com/bjzhou/PhotonCamera">PhotonCamera</a> by bjzhou, with an interface rebuilt for small phone screens and processing adapted for low-memory phones.</p>
