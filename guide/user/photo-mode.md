---
icon: lucide/camera
---

# Photo and Night

Photo mode is for everyday shots. It can merge several frames into one photo to lower noise, which Lilac calls **stacking**. Night is a mode that always stacks, and also brightens.

## Stacking

Photo mode merges a quick burst of frames into one photo with much less noise, which Lilac calls **stacking**. It stacks the camera's processed YUV frames, so it works on every ROM and with moving people, but it starts from Sony's processing. How it works, and how it differs from Pro's RAW stacking: [How a picture is made](how-pictures-are-made.md#how-stacking-works).

It is on by default, at 5 frames. Change it with **Multi-frame** in quick settings: Off, or 3 to 20 frames. Picking a count turns it on. **5 to 10 frames** give most of the gain; why more frames help less and less: [How many frames](how-pictures-are-made.md#how-many-frames). A stacked photo shows **Stacked · N frames** in the gallery.

**Camera JPEG** in quick settings keeps an extra version of each stacked or Pro shot to compare against. For a Photo stack it is the reference frame before stacking; for Pro it is Sony's own JPEG of the first frame. In the gallery it shows as **Built-in camera**.

### Bracketing: a brighter and a darker frame

**Bracketing** (quick settings, on by default on the back camera) adds one darker and one brighter frame to the stack, so a bright sky or window keeps its detail and the shadows are not lost. It needs Multi-frame and 12 MP or less. How it works, and how it differs from Pro's darker burst: [Exposure bracketing](hdr.md#exposure-bracketing).

### Getting the best results

- Hold still until the ring around the shutter closes: it closes when the last frame arrives.
- Keep the back camera at **12 MP**, the default (below).
- In a dim room, use Night rather than a high frame count.
- Wait for the thumbnail to finish before the next shot.

## Resolution: 12 MP on the back, 2 MP on the front

**Resolution** in quick settings sets the photo size in Photo, Night and Portrait. Pro is always full size.

### Back camera: use 12 MP

The back sensor has 19 MP (5056 × 3792). 12 MP (4000 × 3000) is the default, because it works better on this phone than full size:

- **Faster viewfinder and focus.** A full-size stream puts the sensor in its slowest mode, 10 frames a second. At 12 MP the viewfinder runs about twice as fast, and focus is about 1.7 times faster, because focusing is counted in frames.
- **Less memory.** A 19 MP frame takes 29 MB, a 12 MP frame 12 MB, so a stack needs less than half the memory; see [memory on a 4 GB phone](how-pictures-are-made.md#memory-on-a-4-gb-phone).
- **Shorter bursts,** so less shake.
- **Bracketing fits.** A bracketed stack at full size runs out of memory, so Bracketing needs 12 MP or less.
- The extra pixels carry little real detail: the lens and Sony's noise reduction limit what reaches them.

### Front camera: 2 MP is the default, keep it

The front sensor has 8 MP. At any size above 1920 × 1080, Sony's processing crushes the picture in dim light: shadows go black and colours turn loud, already in the viewfinder. At 2 MP (1600 × 1200) the sensor combines each 2 × 2 block of pixels into one:

- the full view is kept,
- noise is 25 to 40 % lower than the 8 MP frame shrunk to the same size,
- detail is about the same, because the fixed-focus lens cannot resolve the sensor's tiny pixels anyway,
- frames are a quarter of the size, so more of them fit in a stack.

Night always shoots the front camera at 2 MP.

## Front camera colour: Fast or Accurate

In dim light the front camera's normal stream shows a pink or violet cast in the shadows.

The sensor adds a small fixed offset to every reading, its black level. Sony's software removes it for still photos but leaves it in the normal stream. White balance then multiplies red and blue more than green in warm, dim light, so that grey offset turns magenta.

No camera setting removes it. The only things that do are a larger sensor mode, which crushes the shadows (above), or Sony's still processing. So the front camera has a **Colour** choice in quick settings (Photo and Portrait):

| | Fast (default) | Accurate |
|---|---|---|
| How | the plain stream, 30 frames a second | every shot also asks for a small JPEG, so Sony's still processing runs, with its true colours |
| Exposure | about 1/30 s per frame | about 1/15 s per frame, so hold still longer |
| Bursts | 30 frames a second | about 4 frames a second |
| Viewfinder | shows the cast | corrected about twice a second, close to the photo |

With Fast, saved photos still get a correction that removes the cast from the shadows without changing brightness. Only the viewfinder keeps it.

## Night

Night is for dim scenes. It always stacks, and brightens the result.

- **Frames:** Auto picks 6 to 12 frames from how much light is missing and how steady you are. Set 4 to 20 by hand with **Frames** among the manual values.
- **Before the shot** the bottom of the picture shows a plan such as "12 frames · hold 1.5 s".
- **On press** it shows "Hold still" while it measures shake for half a second, then the plan it chose, such as "12 × 1/15 s · hold still".
- **Brightening:** up to 3.5 times, never more than the square root of the frame count, so more frames allow more brightening without more noise.

The film button becomes Night's switch: ![Fast](../icons/RunLine.svg){ .icon } **Fast** or ![Accurate](../icons/MoonStars.svg){ .icon } **Accurate** (the default).

| | Back camera | Front camera |
|---|---|---|
| Fast | 1/30 s frames | 30 frames a second; colours may tint |
| Accurate | each frame as long as your steadiness allows, 1/30 to 1/4 s | Sony's true colours, slower |

When the light sensor reads under 20 lux for two seconds in Photo mode, a **Low light · Night** pill offers to switch. Tap it to roll the dial to Night.

## Smile shutter

![Smile](../icons/MoodSmileLine.svg){ .icon } **Smile** in the everyday row: Off, S, M or L (a small, average or big smile). Press the shutter once to arm it: "Shoots on each smile. Press to stop." It shoots when the main face smiles enough, at most once a second. Press again to stop. It uses Sony's smile scores from the camera; see [Focus and faces](focus.md#faces).
