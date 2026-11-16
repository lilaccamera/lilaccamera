# Lilac Camera

Lilac Camera is a fork of [PhotonCamera](https://github.com/bjzhou/PhotonCamera) by bjzhou, adapted for low-memory phones.

Lilac takes several frames for every photo and merges them, so less noise reaches the picture and more of the light does. It is built for the Sony Xperia XZ1 Compact (lilac): a 4.6-inch screen, 4 GB of memory and a capable 19 MP sensor that most camera apps leave underused.

## What it does differently

### More of each picture comes from the sensor

A phone camera hands apps finished pictures: already denoised, sharpened and toned by the phone's image processor. Lilac starts earlier where it can, and merges frames wherever it can.

- **Photo mode stacks the processed stream,** the same pictures that normally become the JPEG, so it works on any ROM.
- **Pro mode stacks RAW frames,** the sensor's own data before any processing, and develops the photo itself. RAW needs a ROM that gives it to apps: on the XZ1 Compact that is **sunplex07's unofficial LineageOS 23.2 for lilac**, with its raw19 camera shim.

<figure class="lilac-fig" markdown="0">
<svg viewBox="0 0 340 330" role="img" aria-labelledby="hs-t hs-d">
<title id="hs-t">Three streams from one sensor</title>
<desc id="hs-d">The sensor feeds three streams. Sony's image processor gives YUV frames, which Lilac stacks in Photo, Night and Portrait. Sony's still pipeline gives a camera JPEG, used for the front camera's Accurate colour. The raw19 shim gives 10-bit RAW frames, which Lilac merges and develops in Pro.</desc>
<defs><marker id="hs-a" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" class="arrow-head"/></marker></defs>
<rect x="60" y="6" width="220" height="40" rx="8" class="box-sony"/>
<text x="170" y="24" text-anchor="middle" class="strong">Sensor</text>
<text x="170" y="39" text-anchor="middle" class="sub">Sony IMX400 · 19 MP</text>
<path d="M24 46V286" class="line"/><path d="M60 26H24" class="line"/>
<g>
<path d="M24 98H34" class="line"/>
<rect x="34" y="70" width="128" height="56" rx="8" class="box-sony"/>
<text x="98" y="92" text-anchor="middle">Sony's processor</text>
<text x="98" y="109" text-anchor="middle" class="sub">YUV · 30 per second</text>
<path d="M162 98H186" class="line" marker-end="url(#hs-a)"/>
<rect x="188" y="70" width="146" height="56" rx="8" class="box-lilac"/>
<text x="261" y="92" text-anchor="middle">Photo · Night · Portrait</text>
<text x="261" y="109" text-anchor="middle" class="sub">stacked by Lilac</text>
</g>
<g>
<path d="M24 178H34" class="line"/>
<rect x="34" y="150" width="128" height="56" rx="8" class="box-sony"/>
<text x="98" y="172" text-anchor="middle">Sony's still pipeline</text>
<text x="98" y="189" text-anchor="middle" class="sub">camera JPEG · slow</text>
<path d="M162 178H186" class="line" marker-end="url(#hs-a)"/>
<rect x="188" y="150" width="146" height="56" rx="8" class="box-lilac"/>
<text x="261" y="172" text-anchor="middle">Front camera colour</text>
<text x="261" y="189" text-anchor="middle" class="sub">Accurate mode</text>
</g>
<g>
<path d="M24 258H34" class="line"/>
<rect x="34" y="230" width="128" height="56" rx="8" class="box-sony"/>
<text x="98" y="252" text-anchor="middle">raw19 shim (ROM)</text>
<text x="98" y="269" text-anchor="middle" class="sub">RAW · 10-bit</text>
<path d="M162 258H186" class="line" marker-end="url(#hs-a)"/>
<rect x="188" y="230" width="146" height="56" rx="8" class="box-lilac"/>
<text x="261" y="252" text-anchor="middle">Pro</text>
<text x="261" y="269" text-anchor="middle" class="sub">merged and developed</text>
</g>
<rect x="34" y="304" width="12" height="12" rx="3" class="box-sony"/><text x="52" y="314" class="sub">the phone's camera</text>
<rect x="188" y="304" width="12" height="12" rx="3" class="box-lilac"/><text x="206" y="314" class="sub">Lilac</text>
</svg>
<figcaption>One sensor, three streams. How each works: <a href="user/how-pictures-are-made/">How a picture is made</a>.</figcaption>
</figure>

What that gives, measured side by side on this phone:

- In a scene with a bright window and monitor in a dark room, a 13-frame Pro burst kept most of the highlight detail, while Sony's own JPEG of the same exposure was white there and had about three times the fine noise.
- In a dark room, 10 to 20 stacked front-camera frames reached 1.6 to 2.3 times the signal-to-noise ratio of Sony's own camera app.

These are tests of one scene each, not a promise for every photo; how to get the most from each mode is in the guide.

### Made for a 4.6-inch screen

Every control was placed for one thumb on a small phone:

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
- **ROM:** any lilac ROM runs Photo, Night, Portrait and Video. Pro needs RAW, which comes from the raw19 shim in sunplex07's unofficial LineageOS 23.2 for lilac. Without it, Pro does not appear on the mode dial.
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
