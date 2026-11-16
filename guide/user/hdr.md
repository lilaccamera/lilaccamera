# HDR

A bright window and a dark room can be seven stops apart: one is 128 times brighter than the other. No single exposure holds both. HDR in Lilac means keeping the bright parts from turning white and lifting the dark parts, without the flat, grey look of heavy HDR.

## Photo mode cannot do it, Pro can

Photo mode shoots every frame at the exposure Sony picks for the whole scene. A window that is white in one frame is white in all of them. Merging lowers noise, but it cannot bring back what was never recorded.

Pro starts from RAW, before Sony's tone curve, and Lilac chooses the exposure. That is where Lilac's HDR happens.

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

**HDR ratio** in Pro's quick settings, just under HDR+ frames: 1× to 20×, default 9.8×. Double-tap it to go back to 9.8×.

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

!!! note "Not in this version"
    Photo mode has no HDR bracketing: its bracket fusion works at 12 MP but runs out of memory at full size on this phone, so it is switched off. Sony's own still HDR can only be turned on by hand as a vendor key; see [Sony hardware](hardware.md#vendor-keys).
