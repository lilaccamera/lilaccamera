---
icon: lucide/sun
---

# HDR

A bright window and a dark room can be seven stops apart: one is 128 times brighter than the other. No single exposure holds both. HDR in Lilac means keeping the bright parts from turning white and lifting the dark parts, without the flat, grey look of heavy HDR.

## Two ways to keep a window

A window that is white in a frame stays white, however many frames you merge: merging lowers noise, but it cannot bring back what was never recorded. So Lilac changes the exposure before it shoots, in two different ways:

- **Photo mode: [exposure bracketing](#exposure-bracketing).** Next to the normal frames it takes one darker and one brighter frame, and fuses the three.
- **Pro: [the darker burst](#the-darker-burst).** Every frame of the RAW burst is shot darker, and the merge averages the noise away.

Which one to use, and why they differ: [Bracketing or a darker burst](#bracketing-or-a-darker-burst).

## Exposure bracketing { #exposure-bracketing }

**Bracketing** is a Photo mode setting: the multi-frame stack gets two extra shots at other exposures, so bright skies and dark shadows both keep detail. It is on by default for new installs.

Switch it with **Bracketing** in quick settings (Shooting tab, next to Multi-frame), or in Settings › Photo. When it cannot be used, the row says why (see [When it is off](#when-it-is-off)).

### What one press does

1. Lilac looks at the viewfinder and picks how far to go: the more of it is white, the darker the dark frame; the more of it is black, the brighter the bright frame.
2. It shoots the normal frames (the Multi-frame count, 5 by default), one darker frame and one brighter frame. With 5 frames that is 7 shots.
3. The normal frames are stacked, as in any Photo shot, which lowers their noise.
4. The stack, the dark frame and the bright frame are fused: for each part of the picture, the fusion takes the frame where it is best exposed. Windows come from the dark frame, shadows from the bright one, the rest from the stack.

| White in the viewfinder | Dark frame | Black in the viewfinder | Bright frame |
|---|---|---|---|
| under 0.5 % | 1 stop darker | under 5 % | 1 stop brighter |
| 0.5 to 2 % | 1.5 stops | 5 to 15 % | 2 stops |
| 2 to 5 % | 2 stops | 15 to 30 % | 2.5 stops |
| 5 % or more | 2.5 stops | 30 % or more | 3 stops |

The bright frame is not allowed a long shutter, so it is not blurred by shake: its shutter stays at 1/100 s or shorter, and ISO makes up the rest. Hold the phone very steady and it may go longer.

### When it is off

- **Back camera only.** The front camera has no manual exposure, so its extra frames would come out the same as the others.
- **Needs Multi-frame.** With Multi-frame off there is no stack to add to.
- **12 MP or 8 MP, not full size.** A bracketed stack at 19 MP runs a 4 GB phone out of memory, so at full size the row reads "12 MP or less" and the switch does nothing. Photo mode starts at 12 MP.
- **Photo mode only.** Night plans its own frames, and Pro uses [the darker burst](#the-darker-burst).

If you go back from the gallery to the camera while a stack is still being fused, Lilac asks first ("Photo still processing"): opening the camera now may run the phone out of memory. The photo is safe either way. Stay in the gallery, go to the home screen, or open the camera anyway.

## Bracketing or a darker burst

Both keep what a normal exposure loses to white. They get there differently:

| | Photo: exposure bracketing | Pro: the darker burst |
|---|---|---|
| Frames | 3 different exposures: normal (several), darker, brighter | all the same exposure, darker by 1 to 3 stops |
| Made from | Sony's finished 8-bit frames | RAW, before Sony's processing |
| Highlights come from | the one dark frame | every frame |
| Shadows come from | the bright frame and the stack | the merge: averaging many dark, noisy frames |
| Joined by | exposure fusion, which picks the best-exposed parts of each frame | merge, then tone mapping lifts the shadows (capped by the [HDR ratio](#the-hdr-ratio-slider)) |
| Extra frames | 2 | none; the whole burst is darker |
| Time and memory | light at 12 MP; Photo mode's own speed | slow, 15 to 22 s for 13 frames; heavy, about 1 GB |
| Camera | back | back |

The difference in one line: **bracketing measures the shadows with a brighter frame; the darker burst measures them with many dark frames and averages the noise out.**

So a bracketed Photo shot is quick and handles an ordinary contrast, such as a sunny street with shade. It has only one dark frame to hold the highlights and one bright frame for the shadows, both taken from Sony's already-processed picture. The darker burst has far more data in the shadows and the highlights, because it starts from RAW and merges many frames. In Lilac's tests only a darker Pro burst held a scene as hard as a bright window next to a dark room. Use Pro for those, and Photo bracketing for the rest.

Pro also has its own **HDR+ bracket exposure** (Spatial merge mode only, off by default). That is a third thing, with RAW frames at different exposures; see [Pro](pro-mode.md#hdr-frames). It is not worth it on this phone.

## The darker burst

In Pro, Lilac checks the viewfinder before a burst. If part of it is already pure white, it shoots the whole burst darker, so the bright parts keep their detail:

| White in the viewfinder | Burst shot darker by |
|---|---|
| 0.5 % | 1 stop |
| 2 % | 2 stops |
| 5 % or more | 3 stops |

Darker frames are noisier in the shadows, but the merge averages that noise away. The tone mapping then lifts the shadows back up. So the highlights come from a dark exposure and the shadows from the merge.

It never goes darker than the frame count can pay back: at most 1 stop for 2 frames, 1.6 for 3, 2 for 4 and 3 for 8 or more. It lowers ISO first and only then shortens the shutter.

It happens by itself when:

- ISO and shutter are both on Auto,
- HDR+ frames is 2 or more,
- the merge mode is Sabre (the default).

## The HDR ratio slider

**HDR ratio** in Pro's quick settings, just under HDR+ frames: 1× to 20×, default 8×. Double-tap it to go back to 8×.

It is a **limit**, not a strength. Lilac's tone mapping uses Google's learned auto-exposure to decide how many times brighter the shadows may be made than the highlights. The slider caps that ratio.

- The scene often wants less than the cap. Then raising it changes nothing.
- Lowering it gives a gentler lift: darker shadows, a more ordinary-looking photo. 4× or 2.5× look like a regular photo.
- When the cap bites, the difference is shared: shadows are lifted less and highlights brought down less.
- A big face in the frame lowers the cap, to no less than 6×.

The value in force is saved with each photo, so it applies to new shots. The same setting is **HDR Ratio limit** in Settings.

## What the photo used

Open a Pro photo, then its info. The **HDR · ratio 6.8×** row expands to:

- **Ratio used:** what the tone mapping applied.
- **Scene wanted:** what the scene asked for before the limit.
- **Ratio limit:** your HDR ratio when the photo was taken.
- **Viewfinder match:** how far the brightness was pulled toward the viewfinder picture. Merged bursts keep their own brightness, so this is near 0.

If "Scene wanted" is above "Ratio limit", the slider made a difference.

## Ultra HDR

**Ultra HDR** in quick settings (off by default) adds a gain map, so photos look brighter on HDR screens. The gain map is built from the RAW, so it works for Pro photos. Each photo in the gallery has its own HDR switch and strength.

Sony's own still HDR can only be turned on by hand as a vendor key; see [Sony camera features](hardware.md#vendor-keys).
