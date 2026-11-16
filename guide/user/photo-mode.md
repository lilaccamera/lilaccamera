# Photo and Night

Photo mode is for everyday shots. It can merge several frames into one photo to lower noise, which Lilac calls **stacking**. Night is a mode that always stacks, and also brightens.

## How stacking works

A single photo from a small sensor is noisy, mostly in the dark parts. Noise is random, so it differs from frame to frame, while the scene stays the same. Averaging several frames keeps the scene and cancels much of the noise.

In Photo mode Lilac:

1. takes a quick burst of frames,
2. picks the steadiest early frame as the reference, using the gyroscope,
3. lines every other frame up with it, in small tiles,
4. for each pixel, averages in the frames that agree with the reference and gives less weight to those that differ because something moved, so moving things do not leave ghosts,
5. keeps the brightness of one frame. Stacking lowers noise; it does not brighten. Night does both.

It runs on the phone's graphics chip and keeps all the frames in memory at once.

Turn it on with **Multi-frame** in quick settings: Off, or 3 to 20 frames. Picking a count turns it on. A stacked photo shows **Stacked · N frames** in the gallery.

### Where the frames come from

The camera can hand an app frames in different forms:

| Stream | What it is | Used by |
|---|---|---|
| **YUV** | finished frames from Sony's image processor: already denoised, sharpened and toned, 8 bits per colour, 30 per second | Photo, Night, Portrait |
| **Camera JPEG** | Sony's full still pipeline, as the stock camera makes it: slower, but with Sony's own colours and black level | the front camera's Accurate colour; kept as a version with Camera JPEG |
| **RAW** | the sensor's own data, before any of Sony's processing | [Pro](pro-mode.md) |

Because Photo mode stacks YUV frames, it starts from Sony's processing. Stacking lowers the noise that is left, but it cannot bring back shadow detail Sony already crushed, or highlights that are white in every frame. Pro stacks RAW frames instead; see [Photo or Pro](pro-mode.md#photo-or-pro).

**Camera JPEG** in quick settings keeps an extra version of each stacked or Pro shot to compare against. For a Photo stack it is the reference frame before stacking; for Pro it is Sony's own JPEG of the first frame. In the gallery it shows as **Built-in camera**.

### How many frames

Averaging N frames lowers noise by the square root of N. Each extra frame helps less than the one before:

| Frames | Noise left |
|---|---|
| 1 | 100 % |
| 3 | 58 % |
| 5 | 45 % |
| 10 | 32 % |
| 20 | 22 % |

On a tripod Lilac reaches these numbers. Handheld, shake and movement cost frames.

- **5 to 10 frames** is where most of the gain is.
- **Above about 12 handheld**, blur from shake grows faster than noise falls. 20 frames last about a second.
- **On a tripod in the dark**, 20 frames are still worth it, but Night is usually the better choice.
- Colour blotches improve less than grain, because Sony's processing leaves coarse colour noise.

### Getting the best results

- Hold still until the ring around the shutter closes: it closes when the last frame arrives.
- Use **12 MP** on the back camera (below).
- In a dim room, use Night rather than a high frame count.
- Wait for the thumbnail to finish before the next shot.

## Resolution: 12 MP on the back, 2 MP on the front

**Resolution** in quick settings sets the photo size in Photo, Night and Portrait. Pro is always full size.

### Back camera: use 12 MP

The back sensor has 19 MP (5056 × 3792). Full size is the default, but 12 MP (4000 × 3000) works better on this phone:

- **Faster viewfinder and focus.** A full-size stream puts the sensor in its slowest mode, 10 frames a second. At 12 MP the viewfinder runs about twice as fast, and focus is about 1.7 times faster, because focusing is counted in frames.
- **Less memory.** A 19 MP frame takes 29 MB, a 12 MP frame 12 MB. A 20-frame stack needs about 575 MB at full size against 240 MB at 12 MP, on a phone with 4 GB shared with everything else.
- **Shorter bursts,** so less shake.
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
