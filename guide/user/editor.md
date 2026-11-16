# Photo editor

Open a photo in the gallery and tap the pencil. The button waits while a photo is still processing.

<!-- screenshot: the editor with the section bar -->

## Around the screen

- **Sections** along the bottom: Look, Light, Color, Effects, Detail, Portrait, Crop. A dot marks a section you changed. Videos have no Portrait or Crop.
- **Icons** along the top: cancel, undo, redo, HD, full view, compare and Save.
- **Compare:** hold the compare icon, or long-press the photo, to see the original. Long-press one setting's value to see the photo without just that change.
- **HD:** full-size preview while editing; slower. Without it the preview uses small copies, and Save is still full size.
- Tap a setting's name for a one-line explanation.

## The sections

- **Look:** the look (LUT) and its intensity, a frame, and presets. **Copy edits** and **Paste edits** carry your edits to another photo. **Save into look** stores the adjustments in the look itself, so every photo using it changes.
- **Light:** exposure, contrast, highlights, shadows, tonality and tone curves. Pro photos get Whites, Blacks and a **RAW** group.
- **Color:** temperature, tint, saturation, vibrance; Mixer works by colour, Grading by brightness, Calibration on the camera's red, green and blue.
- **Effects:** grain, vignette, fade, bloom, halation, soft light, flash, bleach, fringe and noise.
- **Detail:** sharpness, clarity, noise reduction, colour noise and AI denoise.
- **Portrait:** the blur, see [Portrait](portrait.md).
- **Crop:** shape, straighten (up to 45°), turn and flip.

## Pro photos are developed again

A Pro photo keeps its RAW file, so the editor develops the RAW again with your changes: a preview first, the full size when you save. Under Light › RAW you choose the rendering engine, colour profile (including imported DCP profiles), lens shading and the base look.

**Dynamic range › Re-render** rebuilds Lilac's HDR tone map from the RAW.

This changes the photo itself. To try settings without touching it, use **Re-render as a copy** from the gallery instead (see [Pro mode](pro-mode.md#re-render-as-a-copy)).

## AI denoise

Detail › AI denoise runs a neural network that rebuilds detail. It downloads its model (about 47 MB) the first time. It runs on a small preview copy first, then on the whole photo when you save.

!!! warning
    AI denoise is heavy for this phone. If a saved result looks wrong, turn it off and use Noise reduction instead.
