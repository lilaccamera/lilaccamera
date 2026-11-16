---
icon: lucide/play
---

# Getting started

## First launch

Lilac asks for the camera and the microphone. On Android 13 and newer it also asks for notifications. They are optional: they only show the progress of Pro photos that finish in the background.

There is no tour. A small pill, **How to use Lilac**, sits above the shutter in Photo mode until you open it once. It walks through modes, quick settings, manual controls, zoom and looks. To see it again, open quick settings and tap the info mark next to the mode name.

<!-- screenshot: the camera screen in Photo mode, with the How to use Lilac pill -->

## The camera screen

- **Top line:** small marks for anything not at its default, such as a timer (`3s`), Live, Smile, flash or a light reading like `12 lux`. Tap them to open the everyday row. Sony's [scene icons](hardware.md#scene-and-condition-detection) can appear at the right end.
- **Above the shutter:** quick settings ![Quick settings](../icons/Sliders.svg){ .icon } on the left, the zoom ring in the middle, looks ![Looks](../icons/Film.svg){ .icon } on the right.
- **Bottom row:** the latest photo on the left, the shutter with the mode dial in the middle, the camera switch ![Switch camera](../icons/TurningArrows.svg){ .icon } on the right.

Tap the picture to focus and meter there. A long press locks focus. Pinch to zoom.

## Modes

Swipe sideways on the shutter to change mode, or tap the faint mode mark beside it. From left to right:

| Mode | Mark | For |
|---|---|---|
| Night | ![Night](../icons/MoonStars.svg){ .icon } | dim scenes; stacks every shot |
| Portrait | ![Portrait](../icons/User.svg){ .icon } | people, with the background blurred |
| Pro | <span class="mode-mark">M</span> | RAW photos, developed by Lilac |
| Photo | <span class="mode-mark mode-mark--auto">AUTO</span> | everyday shots |
| Video | ![Video](../icons/MovieCamera.svg){ .icon } | video |

A short flick moves one mode; a slow swipe can cross them all. The dial stops at Night and at Video. It is locked while a video records.

Pro is only on the dial when the camera gives apps RAW frames. On the front camera, or on a ROM without the raw19 shim, the dial has no M.

## Quick settings

Tap quick settings ![Quick settings](../icons/Sliders.svg){ .icon }. A sheet rises from the bottom; tap the picture or drag the sheet down to close it.

- The **everyday row** on top: Live, timer, Smile, aspect and flash in the photo modes; resolution, frame rate, torch and stabilisation in Video. Tap an item to step to its next value. You can also swipe down on the picture to show just this row.
- Below it, the current mode's own group comes first, outlined in amber, then the settings for all photo modes.
- Tap a setting's name for a one-line explanation. Long-press it to open the full setting in Settings.

## Symbols on the camera screen

| Symbol | Means |
|---|---|
| ![Timer](../icons/StopwatchLine.svg){ .icon } | self-timer (3, 5 or 10 s) |
| ![Live](../icons/LivePhotoLine.svg){ .icon } | Live photo: a short video with the photo |
| ![Smile](../icons/MoodSmileLine.svg){ .icon } | Smile shutter: shoots when the face smiles |
| ![Flash](../icons/BoltLine.svg){ .icon } ![Flash off](../icons/BoltOffLine.svg){ .icon } | flash on or off |
| ![Torch](../icons/BulbLine.svg){ .icon } ![Torch off](../icons/BulbOffLine.svg){ .icon } | torch on or off |
| ![Multi-exposure](../icons/MultiExposureLine.svg){ .icon } | multi-exposure: several shots in one picture |
| ![Low light](../icons/SunLowLine.svg){ .icon } | the light reading, such as `12 lux`, in dim light |
| ![Fast](../icons/RunLine.svg){ .icon } ![Accurate](../icons/MoonStars.svg){ .icon } | Night's Fast or Accurate switch |
| ![Info](../icons/InfoLine.svg){ .icon } | opens the guide for the current mode |
| ![Temperature](../icons/ThermometerOutline.svg){ .icon } | the phone is warming up while recording |

## Manual controls

??? info "How to use the manual controls"
    Swipe up on the picture. The values appear along its bottom edge: **EV** (exposure), **Tv** (shutter), **ISO**, **AF** (focus) and **AWB** (white balance). Portrait adds **Av** (blur strength); Night adds **Frames**.

    - A value on Auto shows the camera's live reading, such as 1/60 or ISO 400.
    - Drag a value sideways to change it. A ruler shows while you drag.
    - Tap a value to keep its ruler open. Tap **A** on the ruler, or double-tap the value, to go back to Auto.
    - A value you set by hand gets a small amber dot and stays on screen.
    - Focus has an **H** button for hyperfocal focus.
    - EV only works while exposure is on Auto.
    - Pro opens with the values showing. In other modes they hide about 5 s after your last touch.
    - The front camera has no manual shutter or ISO. Tapping them says "Not available on this camera".

## Zoom

Pull the ring above the shutter sideways. The further you pull, the faster it zooms; it springs back when you let go. Tap **W** or **T** to step, or hold them to keep zooming. The ring stops at 4×; pinching reaches the full range. With Volume Key Action set to Focal Switch, the volume buttons step the zoom too (see [Buttons on the side](#buttons-on-the-side)).

## Buttons on the side

The XZ1 Compact's side buttons work as camera controls, so you can shoot without reaching across the screen:

- **Camera key, half-press:** focuses and holds focus and exposure while you keep it there. See [Focus and faces](focus.md#the-camera-key).
- **Camera key, full press:** takes the photo, or starts and stops a video. During a slow-motion recording it fires the 960 fps burst.
- **Volume buttons:** take the photo by default. In Settings › System & Controls › **Volume Key Action** you can make them **Focal Switch** (zoom: up steps in, down steps out, one stop at a time, like tapping T or W), **Exposure Comp.** (up brightens, down darkens) or **Volume Control** (they change the volume as usual).

## Looks

A look is a colour style (a LUT). Swipe sideways on the picture to step through your favourite looks. Tap ![Looks](../icons/Film.svg){ .icon } for all of them, with Intensity, colour recipe, frame and presets.

In Night that button becomes Night's Fast or Accurate switch instead. Looks cannot change while a video records.

## The latest photo

Tap the thumbnail to open the gallery. Its edge shows processing: a running arc while a photo is processed, a dotted ring while photos wait, pause bars when processing is paused, and an amber ring when Android keeps stopping a Pro photo for memory. See [Pro mode](pro-mode.md#the-render-queue).
