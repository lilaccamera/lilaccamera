---
icon: lucide/aperture
---

# Pro

Pro shoots **RAW**: the sensor's own data, before Sony's processing and noise reduction. Lilac then develops the photo itself, so fine detail is not smeared away. Pro is always full size (19 MP) and works on the back camera only.

Pro needs RAW from the camera. On the XZ1 Compact that comes from the raw19 shim in sunplex07's LineageOS 23.2 for lilac. Without it the mode dial has no Pro.

## HDR+ frames

Like Photo mode, Pro can merge several frames. Lilac calls it **HDR+**: it is the pipeline of Google's camera app (GCam), with GCam's merge, learned exposure and HDRNet tone mapping (see [what comes from GCam](../index.md#gcams-multi-frame-processing)). The idea: merging many frames removes noise, so the photo can be exposed darker to keep the highlights, and the shadows are lifted back afterwards. It has nothing to do with Sony's HDR.

**HDR+ frames** in quick settings sets how many RAW frames one press takes: 1 to 20, default 3. One frame means a single RAW shot.

Merging RAW is cleaner than merging Photo mode's frames: RAW is linear, Lilac knows this sensor's noise, and the processing runs once on the merged frame. Why: [Pro: stacking RAW](how-pictures-are-made.md#pro-stacking-raw).

Each RAW frame is a full-size still, so frames come about 0.44 s apart: 3 frames take about 1.5 s, 13 frames about 6 s. Hold still, or lean on something, for 3 or more frames. A merged burst takes 15 to 22 s from press to saved photo.

??? info "HDR+ settings in Settings › Professional mode"
    - **HDR+ merge mode:** **Sabre** (default) merges frames all taken at the same exposure. **Spatial** also allows bracket exposure.
    - **HDR+ bracket exposure** (Spatial only, off by default): the burst gets one darker frame for highlights and some brighter frames for shadows. Of N frames, one is short, about a quarter are long and the rest normal.
    - **Long frame exposure** (+2.5 EV) and **Short frame exposure** (−1.6 EV): how much brighter or darker those frames are asked to be.
    - **HDR Ratio limit:** the same as the HDR ratio slider; see [HDR](hdr.md).

    On this phone bracket exposure is not worth it: the long frame is capped to keep it sharp, so it is often no brighter than the normal one, and bursts take 40 to 50 s. In a hard high-contrast test a darker Sabre burst did better. Leave it off; Lilac makes Sabre bursts darker by itself when the scene needs it (see [HDR](hdr.md#the-darker-burst)).

## Photo or Pro

| | Photo (Multi-frame) | Pro (HDR+ frames) |
|---|---|---|
| Merges | Sony's finished frames | RAW frames, before Sony's processing |
| Size | Full, 12 MP or 8 MP | always full, 19 MP |
| Speed | fast; frames 1/30 s apart, finished in the camera | slow; frames 0.44 s apart, 15 to 22 s for a 13-frame burst |
| Memory | light at 12 MP | heavy: about 1 GB to develop one RAW |
| Highlights | white in every frame stays white | shot darker when needed, lifted later |
| Later changes | look and edits | the RAW can be developed again |
| Wins for | people, moving subjects, quick shots, the front camera | landscapes, still scenes, high contrast, editing later |

## The render queue

A **single-frame** Pro shot is saved as a RAW file (DNG) at once and developed later, in the background, one photo at a time. Merged bursts are developed in the camera straight away instead.

Why wait? Developing one 19 MP RAW needs about 1 GB of memory. On a 4 GB phone often only 0.5 to 1.5 GB is free. So:

- **Renders wait while you shoot.** They start about 5 s after the last Pro shot, or when you open the gallery. Shooting and developing at once would run out of memory.
- **Each photo waits for free memory**, about 450 MB, and starts anyway after a minute: "Waiting for free memory".
- **Rendering runs in its own process.** You can swipe Lilac away from Recents; processing carries on, and closing the app frees its graphics memory.
- **Progress is saved** at save points (RAW developed, JPEG created), so an interrupted photo continues where it was.

<!-- screenshot: the Processing photos screen -->

Open it from the gallery's layers icon, or from the chip on a waiting photo. The thumbnail's edge on the camera screen shows its progress too.

- **Pause** waits for the next save point, so no work is lost, then holds new photos until you tap **Resume**. Pause and Resume are also in the notification.
- **Skip** takes a photo out of the line. Its RAW file is kept; tap **Process** to put it back.
- **Try again** retries a photo that failed. **Try all again** retries all of them.
- **Process now** starts the line when it is idle.

### When renders keep stopping

If Android stops the same photo twice for memory, the queue says **Short of memory** and the thumbnail's ring turns amber.

1. Swipe Lilac Camera away from Recents. Processing continues and usually finishes.
2. If it still stops, restart the phone. Camera and graphics drivers keep some memory until a restart.
3. Before a long session of Pro shots, restart the phone first.

Do not use Force stop in Android's settings: it halts processing until the app opens again.

## Re-render as a copy

A finished single-frame Pro photo can be developed again with other settings, as a new copy beside the original. Open the photo, then the more menu › **Re-render as a copy**. Choose to start from this photo's settings or the app's, then change the noise model, noise reduction, colour noise, sharpening, exposure, highlights, shadows, lens shading or Ultra HDR. The copy goes into the render queue.

Merged bursts cannot be re-rendered: their noise reduction is part of the merge. To change a photo itself instead, use the [editor](editor.md#pro-photos-are-developed-again).
