# How a picture is made

This page explains what happens between the sensor and the saved photo: which pictures the camera hands to Lilac, how Lilac merges them, and why it works the way it does on a phone with 4 GB of memory.

## Why one frame is not enough

A sensor counts light. In dim light, or in the dark parts of any scene, it counts so little that random variation, **noise**, is a large part of what it records. A bigger lens or sensor would collect more light; a phone cannot have either.

What a phone can do is take several frames and merge them. The scene is the same in every frame; the noise is different each time. Averaging keeps the scene and cancels much of the noise. Lilac calls this **stacking**.

## The camera's three streams

An app does not read the sensor directly. It asks the camera for pictures in a given form, called a stream, and the camera's software decides what reaches it.

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 330" role="img" aria-labelledby="ps-t ps-d">
<title id="ps-t">Three streams from one sensor</title>
<desc id="ps-d">The sensor feeds three streams. Sony's image processor gives YUV frames, which Lilac stacks in Photo, Night and Portrait. Sony's still pipeline gives a camera JPEG, used for the front camera's Accurate colour. The raw19 shim gives 10-bit RAW frames, which Lilac merges and develops in Pro.</desc>
<defs><marker id="ps-a" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" class="arrow-head"/></marker></defs>
<rect x="60" y="6" width="220" height="40" rx="8" class="box-sony"/>
<text x="170" y="24" text-anchor="middle" class="strong">Sensor</text>
<text x="170" y="39" text-anchor="middle" class="sub">Sony IMX400 · 19 MP</text>
<path d="M24 46V286" class="line"/><path d="M60 26H24" class="line"/>
<g>
<path d="M24 98H34" class="line"/>
<rect x="34" y="70" width="128" height="56" rx="8" class="box-sony"/>
<text x="98" y="92" text-anchor="middle">Sony's processor</text>
<text x="98" y="109" text-anchor="middle" class="sub">YUV · 30 per second</text>
<path d="M162 98H186" class="line" marker-end="url(#ps-a)"/>
<rect x="188" y="70" width="146" height="56" rx="8" class="box-lilac"/>
<text x="261" y="92" text-anchor="middle">Photo · Night · Portrait</text>
<text x="261" y="109" text-anchor="middle" class="sub">stacked by Lilac</text>
</g>
<g>
<path d="M24 178H34" class="line"/>
<rect x="34" y="150" width="128" height="56" rx="8" class="box-sony"/>
<text x="98" y="172" text-anchor="middle">Sony's still pipeline</text>
<text x="98" y="189" text-anchor="middle" class="sub">camera JPEG · slow</text>
<path d="M162 178H186" class="line" marker-end="url(#ps-a)"/>
<rect x="188" y="150" width="146" height="56" rx="8" class="box-lilac"/>
<text x="261" y="172" text-anchor="middle">Front camera colour</text>
<text x="261" y="189" text-anchor="middle" class="sub">Accurate mode</text>
</g>
<g>
<path d="M24 258H34" class="line"/>
<rect x="34" y="230" width="128" height="56" rx="8" class="box-sony"/>
<text x="98" y="252" text-anchor="middle">raw19 shim (ROM)</text>
<text x="98" y="269" text-anchor="middle" class="sub">RAW · 10-bit</text>
<path d="M162 258H186" class="line" marker-end="url(#ps-a)"/>
<rect x="188" y="230" width="146" height="56" rx="8" class="box-lilac"/>
<text x="261" y="252" text-anchor="middle">Pro</text>
<text x="261" y="269" text-anchor="middle" class="sub">merged and developed</text>
</g>
<rect x="34" y="304" width="12" height="12" rx="3" class="box-sony"/><text x="52" y="314" class="sub">the phone's camera</text>
<rect x="188" y="304" width="12" height="12" rx="3" class="box-lilac"/><text x="206" y="314" class="sub">Lilac</text>
</svg>
<figcaption>One sensor, three streams. Lilac's work is in the lilac boxes.</figcaption>
</figure>

| | YUV | Camera JPEG | RAW |
|---|---|---|---|
| What it is | Sony's finished frames | Sony's full still photo | the sensor's own data |
| Processing | denoised, sharpened, toned by Sony | Sony's whole still pipeline | none |
| Depth | 8 bits per channel | 8 bits, compressed | 10 bits, linear |
| Speed | up to 30 frames a second | slow; the sensor switches mode | one full-size frame every 0.44 s |
| Size | any size up to 19 MP | any size | full size only, about 38 MB |
| Available | on every ROM | on every ROM | only with the raw19 shim |
| Lilac uses it for | Photo, Night, Portrait | front camera Accurate colour, comparison copies | Pro |

**YUV** is what most camera apps work from: the same frames that normally become the JPEG, before compression. They are quick and cheap, but Sony has already made its choices: its noise reduction smooths fine detail, and what it crushed to black or clipped to white is gone.

**RAW** is the sensor's reading before any of that. Sony's camera software on the XZ1 Compact does not offer RAW to apps at all. **sunplex07's unofficial LineageOS 23.2 for lilac** adds it with the raw19 camera shim, which sits between Android and Sony's camera library. The shim only delivers a full-size RAW frame when the same request also asks for a full-size JPEG, so every Pro frame carries a JPEG that Lilac throws away (or keeps, with Camera JPEG on).

**Camera JPEG** is Sony's complete still pipeline, as Sony's own app gets it. Lilac uses it where Sony's processing does something no setting can: on the front camera, Sony's still processing removes the sensor's black offset that tints the normal stream pink in dim light. See [front camera colour](photo-mode.md#front-camera-colour-fast-or-accurate).

## How stacking works

The idea is the same in Photo and Pro; the material differs.

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 330" role="img" aria-labelledby="st-t st-d">
<title id="st-t">The steps of stacking</title>
<desc id="st-d">Five steps: take a burst; pick the steadiest frame as reference; line the other frames up with it tile by tile; merge each pixel, averaging frames that agree and leaving out those where something moved; the result is one photo with the same brightness and much less noise.</desc>
<defs><marker id="st-a" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" class="arrow-head"/></marker></defs>
<rect x="70" y="8" width="262" height="46" rx="8" class="box-lilac"/>
<circle cx="34" cy="31" r="15" class="num"/><text x="34" y="36" text-anchor="middle" class="num-t">1</text>
<text x="84" y="28" class="strong">Take a burst</text><text x="84" y="45" class="sub">frames a moment apart</text>
<path d="M34 46V80" class="line" marker-end="url(#st-a)"/>
<rect x="70" y="72" width="262" height="46" rx="8" class="box-lilac"/>
<circle cx="34" cy="95" r="15" class="num"/><text x="34" y="100" text-anchor="middle" class="num-t">2</text>
<text x="84" y="92" class="strong">Pick a reference</text><text x="84" y="109" class="sub">the steadiest frame, by gyroscope</text>
<path d="M34 110V144" class="line" marker-end="url(#st-a)"/>
<rect x="70" y="136" width="262" height="46" rx="8" class="box-lilac"/>
<circle cx="34" cy="159" r="15" class="num"/><text x="34" y="164" text-anchor="middle" class="num-t">3</text>
<text x="84" y="156" class="strong">Line the frames up</text><text x="84" y="173" class="sub">tile by tile, small shifts</text>
<path d="M34 174V208" class="line" marker-end="url(#st-a)"/>
<rect x="70" y="200" width="262" height="46" rx="8" class="box-lilac"/>
<circle cx="34" cy="223" r="15" class="num"/><text x="34" y="228" text-anchor="middle" class="num-t">4</text>
<text x="84" y="220" class="strong">Merge each pixel</text><text x="84" y="237" class="sub">agrees: averaged in; moved: left out</text>
<path d="M34 238V272" class="line" marker-end="url(#st-a)"/>
<rect x="70" y="264" width="262" height="46" rx="8" class="box-lilac"/>
<circle cx="34" cy="287" r="15" class="num"/><text x="34" y="292" text-anchor="middle" class="num-t">5</text>
<text x="84" y="284" class="strong">One photo</text><text x="84" y="301" class="sub">same brightness, much less noise</text>
</svg>
<figcaption>Stacking, step by step.</figcaption>
</figure>

1. **Burst.** One press takes several frames in quick succession.
2. **Reference.** One frame is the anchor the others are matched to. In Photo mode it is the steadiest of the first four, read from the gyroscope; in Pro it is the sharpest, judged from the pixels.
3. **Alignment.** Hands move between frames, so each frame is split into tiles and every tile is shifted to line up with the reference.
4. **Merge.** For each pixel Lilac asks: does this frame agree with the reference, allowing for the noise expected at this brightness? If yes, it is averaged in. If not, something moved there, and the frame counts for less, so moving people do not leave ghosts. The expected noise is measured for each frame, so at high ISO plain noise is not mistaken for movement.
5. **Result.** One photo at the brightness of one frame, with much less noise.

### Photo mode: stacking the processed stream

Photo mode stacks YUV frames on the phone's graphics chip, with all frames in memory at once. A burst at 30 frames a second is over quickly, so it works handheld and with people.

It starts from Sony's processing, so it inherits it: Sony's noise reduction leaves coarse colour blotches that stacking reduces less than grain, and detail Sony already smoothed or crushed does not come back.

### Pro: stacking RAW

Pro stacks RAW frames before anything has touched them:

- **Linear data.** RAW values are proportional to the light, so frames add up exactly, like photon counts.
- **A noise model of this sensor.** Lilac uses a noise profile measured on the XZ1 Compact, so it knows how much of each pixel is noise at each brightness. The profile the shim reports is about twice too high and would make the merge throw away good detail.
- **Processing once, at the end.** After the merge come GCam's denoise tuning, its learned auto exposure and its HDRNet tone mapping, then Lilac's colour and look, on one clean frame instead of many noisy ones. The merge itself is GCam's Sabre by default; see [what comes from GCam](../index.md#the-best-of-both-worlds).

The cost is speed: each RAW frame is a full-size still, so frames come 0.44 s apart and need a steadier hand or a support.

### How many frames

Averaging N frames leaves 1/√N of the noise. Each extra frame helps less than the one before:

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 256" role="img" aria-labelledby="nz-t nz-d">
<title id="nz-t">Noise left after stacking</title>
<desc id="nz-d">Noise falls with the square root of the frame count: 58 % at 3 frames, 45 % at 5, 32 % at 10, 22 % at 20. Tripod measurements match it: 58 to 62 % at 3 frames, 20 to 34 % at 20.</desc>
<rect x="104.9" y="22" width="73.7" height="170" class="band"/>
<text x="141.8" y="36" text-anchor="middle" class="sub">most of the gain</text>
<path d="M46 192.0H326" class="grid"/><text x="40" y="196.0" text-anchor="end" class="tick">0%</text>
<path d="M46 149.5H326" class="grid"/><text x="40" y="153.5" text-anchor="end" class="tick">25%</text>
<path d="M46 107.0H326" class="grid"/><text x="40" y="111.0" text-anchor="end" class="tick">50%</text>
<path d="M46 64.5H326" class="grid"/><text x="40" y="68.5" text-anchor="end" class="tick">75%</text>
<path d="M46 22.0H326" class="grid"/><text x="40" y="26.0" text-anchor="end" class="tick">100%</text>
<text x="46.0" y="208" text-anchor="middle" class="tick">1</text>
<text x="104.9" y="208" text-anchor="middle" class="tick">5</text>
<text x="178.6" y="208" text-anchor="middle" class="tick">10</text>
<text x="252.3" y="208" text-anchor="middle" class="tick">15</text>
<text x="326.0" y="208" text-anchor="middle" class="tick">20</text>
<path d="M46 192H326" class="axis"/>
<path d="M46.0 22.0 L 49.7 39.9 L 53.4 53.2 L 57.1 63.5 L 60.7 71.8 L 64.4 78.7 L 68.1 84.5 L 71.8 89.5 L 75.5 93.9 L 79.2 97.7 L 82.8 101.1 L 86.5 104.2 L 90.2 107.0 L 93.9 109.5 L 97.6 111.9 L 101.3 114.0 L 104.9 116.0 L 108.6 117.8 L 112.3 119.5 L 116.0 121.1 L 119.7 122.6 L 123.4 124.0 L 127.1 125.3 L 130.7 126.6 L 134.4 127.7 L 138.1 128.9 L 141.8 129.9 L 145.5 130.9 L 149.2 131.9 L 152.8 132.8 L 156.5 133.7 L 160.2 134.5 L 163.9 135.3 L 167.6 136.1 L 171.3 136.8 L 174.9 137.6 L 178.6 138.2 L 182.3 138.9 L 186.0 139.5 L 189.7 140.2 L 193.4 140.7 L 197.1 141.3 L 200.7 141.9 L 204.4 142.4 L 208.1 142.9 L 211.8 143.4 L 215.5 143.9 L 219.2 144.4 L 222.8 144.9 L 226.5 145.3 L 230.2 145.7 L 233.9 146.2 L 237.6 146.6 L 241.3 147.0 L 244.9 147.4 L 248.6 147.7 L 252.3 148.1 L 256.0 148.5 L 259.7 148.8 L 263.4 149.2 L 267.1 149.5 L 270.7 149.8 L 274.4 150.1 L 278.1 150.5 L 281.8 150.8 L 285.5 151.1 L 289.2 151.4 L 292.8 151.6 L 296.5 151.9 L 300.2 152.2 L 303.9 152.5 L 307.6 152.7 L 311.3 153.0 L 314.9 153.3 L 318.6 153.5 L 322.3 153.7 L 326.0 154.0" class="series"/>
<circle cx="46.0" cy="22.0" r="4.5" class="dot"><title>1 frames: 100 % of the noise left</title></circle>
<circle cx="75.5" cy="93.9" r="4.5" class="dot"><title>3 frames: 58 % of the noise left</title></circle>
<circle cx="104.9" cy="116.0" r="4.5" class="dot"><title>5 frames: 45 % of the noise left</title></circle>
<circle cx="178.6" cy="138.2" r="4.5" class="dot"><title>10 frames: 32 % of the noise left</title></circle>
<circle cx="326.0" cy="154.0" r="4.5" class="dot"><title>20 frames: 22 % of the noise left</title></circle>
<g class="measured"><title>3 frames, measured on a tripod: 58 to 62 %</title><path d="M84.5 86.6V93.4" class="range"/><path d="M80.5 86.6h8M80.5 93.4h8" class="range"/></g>
<g class="measured"><title>20 frames, measured on a tripod: 20 to 34 %</title><path d="M335.0 134.2V158.0" class="range"/><path d="M331.0 134.2h8M331.0 158.0h8" class="range"/></g>
<text x="63.7" y="65.4" class="sub">√N law</text>
<text x="186.0" y="226" text-anchor="middle" class="sub">frames merged</text>
<circle cx="52" cy="242" r="4.5" class="dot"/><text x="62" y="246" class="sub">√N law</text>
<path d="M142 236v12M138 236h8M138 248h8" class="range"/><text x="152" y="246" class="sub">measured on a tripod</text>
</svg>
<figcaption>Noise left after merging, compared with a single frame. Hover a point for its value.</figcaption>
</figure>

??? info "The numbers behind the chart"
    | Frames | Noise left (√N law) | Measured on a tripod |
    |---|---|---|
    | 1 | 100 % | |
    | 3 | 58 % | 58 to 62 % |
    | 5 | 45 % | |
    | 10 | 32 % | |
    | 20 | 22 % | 20 to 34 % |

    Measured in Photo mode at 12 MP, ISO 800 and ISO 3200, each stack against its own single frame.

- On a tripod Lilac reaches the law: the merge keeps nearly every frame.
- Handheld, shake and movement cost frames: a gently shaken 12-frame burst was worth about 8.
- **5 to 10 frames** give most of the gain. Beyond about 12 handheld, blur from a longer burst grows faster than noise falls.
- Night goes further: it also brightens, by up to √N, so more frames allow more brightening without more noise.

## Keeping bright skies and windows

A merge lowers noise, but cannot bring back light that was never recorded. If a window is white in every frame, it is white in the result. So in Pro Lilac changes the exposure before it shoots:

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 292" role="img" aria-labelledby="db-t db-d">
<title id="db-t">How a darker burst keeps a bright window</title>
<desc id="db-d">Illustration on a scale of seven stops from a dark room to a bright window. One frame at normal exposure loses the window to white. A burst shot three stops darker keeps the window but its shadows are noisy. Merging thirteen frames removes most of that noise, so the whole scene is kept, and tone mapping lifts the room back up.</desc>
<defs><linearGradient id="db-g"><stop offset="0" class="g0"/><stop offset="1" class="g1"/></linearGradient>
<pattern id="db-h" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0V6" class="hatch"/></pattern></defs>
<text x="18" y="16" class="strong">The scene</text>
<rect x="18" y="24" width="304" height="18" rx="4" fill="url(#db-g)" class="scene"/>
<text x="18" y="56" class="sub">dark room</text><text x="322" y="56" text-anchor="end" class="sub">bright window</text>
<text x="18" y="84" class="strong">One frame, normal exposure</text>
<rect x="18.0" y="92" width="50.1" height="16" rx="4" fill="url(#db-h)" class="noisy"><title>lost in noise</title></rect>
<rect x="70.1" y="92" width="191.1" height="16" rx="4" class="bar"><title>recorded with detail</title></rect>
<rect x="263.2" y="92" width="58.8" height="16" rx="4" class="clip"><title>clipped to white</title></rect>
<text x="18" y="124" class="sub">the window turns white</text>
<text x="18" y="152" class="strong">Each frame of the burst, 3 stops darker</text>
<rect x="18.0" y="160" width="128.3" height="16" rx="4" fill="url(#db-h)" class="noisy"><title>lost in noise</title></rect>
<rect x="148.3" y="160" width="173.7" height="16" rx="4" class="bar"><title>recorded with detail</title></rect>
<text x="18" y="192" class="sub">window kept, the room is noisy</text>
<text x="18" y="220" class="strong">13 frames merged</text>
<rect x="18.0" y="228" width="47.9" height="16" rx="4" fill="url(#db-h)" class="noisy"><title>lost in noise</title></rect>
<rect x="67.9" y="228" width="254.1" height="16" rx="4" class="bar"><title>recorded with detail</title></rect>
<text x="18" y="260" class="sub">noise averaged away; the room is lifted back</text>
<rect x="18" y="272" width="12" height="10" rx="3" class="bar"/><text x="34" y="281" class="sub">detail</text>
<rect x="88" y="272" width="12" height="10" rx="3" fill="url(#db-h)" class="noisy"/><text x="104" y="281" class="sub">noise</text>
<rect x="158" y="272" width="12" height="10" rx="3" class="clip"/><text x="174" y="281" class="sub">white</text>
</svg>
<figcaption>An illustration on a scale of seven stops (each stop doubles the light).</figcaption>
</figure>

When part of the viewfinder is already white, Lilac shoots the whole burst darker: 1 stop at 0.5 % white, 2 stops at 2 %, 3 stops at 5 %. Each frame then keeps the window but has noisier shadows; merging averages that noise away; the tone mapping lifts the room back up. It never goes darker than the frame count can pay back, about √N in light, so the shadows end up no noisier than one normal frame.

How far the shadows may be lifted is capped by the [HDR ratio](hdr.md#the-hdr-ratio-slider). Photo mode cannot do this: it shoots every frame at Sony's exposure.

## Memory on a 4 GB phone

Merging needs every frame in memory at once, and developing a RAW needs about 1 GB. On a 4 GB phone shared with Android and other apps, often only 0.5 to 1.5 GB is free.

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 214" role="img" aria-labelledby="mm-t mm-d">
<title id="mm-t">Memory needed against memory free</title>
<desc id="mm-d">A 20-frame stack needs about 575 MB at 19 MP, 240 MB at 12 MP and 58 MB for the 2 MP front camera. Developing one RAW needs about 1 GB. A 4 GB phone often has only 500 to 1500 MB free.</desc>
<rect x="183.0" y="16" width="130.0" height="150" class="band"/>
<text x="248.0" y="27" text-anchor="middle" class="sub">often free</text>
<text x="110" y="51.0" text-anchor="end" class="tick">20 frames, 19 MP</text>
<rect x="118" y="36" width="74.8" height="22" rx="4" class="bar"><title>Photo stack at full size: about 575 MB</title></rect>
<text x="198.8" y="51.0" class="tick">575 MB</text>
<text x="110" y="87.0" text-anchor="end" class="tick">20 frames, 12 MP</text>
<rect x="118" y="72" width="31.2" height="22" rx="4" class="bar"><title>Photo stack at 12 MP: about 240 MB</title></rect>
<text x="155.2" y="87.0" class="tick">240 MB</text>
<text x="110" y="123.0" text-anchor="end" class="tick">20 frames, front 2 MP</text>
<rect x="118" y="108" width="7.5" height="22" rx="4" class="bar"><title>front-camera stack: about 58 MB</title></rect>
<text x="131.5" y="123.0" class="tick">58 MB</text>
<text x="110" y="159.0" text-anchor="end" class="tick">one RAW develop</text>
<rect x="118" y="144" width="130.0" height="22" rx="4" class="bar"><title>Pro, one 19 MP photo: about 1000 MB</title></rect>
<text x="254.0" y="159.0" class="tick">1 GB</text>
<text x="118.0" y="192" text-anchor="middle" class="tick">0</text>
<text x="183.0" y="192" text-anchor="middle" class="tick">500</text>
<text x="248.0" y="192" text-anchor="middle" class="tick">1000</text>
<text x="313.0" y="192" text-anchor="middle" class="tick">1500</text>
<text x="222.0" y="208" text-anchor="middle" class="sub">MB</text>
</svg>
<figcaption>Memory needed, against what is often free. Hover a bar for details.</figcaption>
</figure>

That shapes the design:

- **12 MP on the back camera** in Photo mode needs less than half the memory of full size, and the viewfinder and focus run about twice as fast. See [resolution](photo-mode.md#resolution-12-mp-on-the-back-2-mp-on-the-front).
- **2 MP on the front camera** uses the sensor's own 2 × 2 pixel binning: less noise, a quarter of the memory.
- **Pro photos are developed one at a time, in their own process,** which waits for enough free memory and saves its progress. Closing the app frees its graphics memory for the render. See [the render queue](pro-mode.md#the-render-queue).

## From press to photo

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 300" role="img" aria-labelledby="cf-t cf-d">
<title id="cf-t">What happens after you press</title>
<desc id="cf-d">Photo: a burst of frames 1/30 s apart, merged on the graphics chip, saved within seconds. Pro single frame: the RAW is saved at once; about 5 s after the last shot, and once enough memory is free, a separate process develops it into the photo. Pro burst: frames 0.44 s apart, merged and developed in the camera, 15 to 22 s for 13 frames.</desc>
<defs><marker id="cf-a" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" class="arrow-head"/></marker></defs>
<text x="8" y="20" class="strong">Photo</text>
<rect x="8" y="30" width="98" height="50" rx="8" class="box-lilac"/>
<text x="57" y="51" text-anchor="middle">burst</text><text x="57" y="68" text-anchor="middle" class="sub">1/30 s apart</text>
<path d="M108 55H122" class="line" marker-end="url(#cf-a)"/>
<rect x="124" y="30" width="98" height="50" rx="8" class="box-lilac"/>
<text x="173" y="51" text-anchor="middle">merge</text><text x="173" y="68" text-anchor="middle" class="sub">graphics chip</text>
<path d="M224 55H238" class="line" marker-end="url(#cf-a)"/>
<rect x="240" y="30" width="98" height="50" rx="8" class="box-lilac"/>
<text x="289" y="51" text-anchor="middle">saved</text><text x="289" y="68" text-anchor="middle" class="sub">seconds</text>
<text x="8" y="116" class="strong">Pro, 1 frame</text>
<rect x="8" y="126" width="98" height="50" rx="8" class="box-lilac"/>
<text x="57" y="147" text-anchor="middle">RAW saved</text><text x="57" y="164" text-anchor="middle" class="sub">at once</text>
<path d="M108 151H122" class="line" marker-end="url(#cf-a)"/>
<rect x="124" y="126" width="98" height="50" rx="8" class="box-sony"/>
<text x="173" y="147" text-anchor="middle">waits</text><text x="173" y="164" text-anchor="middle" class="sub">for free memory</text>
<path d="M224 151H238" class="line" marker-end="url(#cf-a)"/>
<rect x="240" y="126" width="98" height="50" rx="8" class="box-lilac"/>
<text x="289" y="147" text-anchor="middle">developed</text><text x="289" y="164" text-anchor="middle" class="sub">own process</text>
<text x="8" y="212" class="strong">Pro burst</text>
<rect x="8" y="222" width="98" height="50" rx="8" class="box-lilac"/>
<text x="57" y="243" text-anchor="middle">burst</text><text x="57" y="260" text-anchor="middle" class="sub">0.44 s apart</text>
<path d="M108 247H122" class="line" marker-end="url(#cf-a)"/>
<rect x="124" y="222" width="98" height="50" rx="8" class="box-lilac"/>
<text x="173" y="243" text-anchor="middle">merge</text><text x="173" y="260" text-anchor="middle" class="sub">and develop</text>
<path d="M224 247H238" class="line" marker-end="url(#cf-a)"/>
<rect x="240" y="222" width="98" height="50" rx="8" class="box-lilac"/>
<text x="289" y="243" text-anchor="middle">saved</text><text x="289" y="260" text-anchor="middle" class="sub">15 to 22 s</text>
</svg>
<figcaption>What happens after you press, in order. Not to scale.</figcaption>
</figure>

- **Photo:** the burst is over in a fraction of a second; hold still until the ring around the shutter closes. The merge runs right after, and the photo is saved within seconds.
- **Pro, one frame:** the RAW file is saved at once. Developing waits until about 5 s after your last Pro shot, so shooting and developing never compete for memory, then runs in the background. The thumbnail's edge shows its progress.
- **Pro, burst:** frames come 0.44 s apart, so hold still for the whole burst, then the merge and develop run in the camera: 15 to 22 s for 13 frames.
