---
icon: lucide/user-round
---

# Portrait

Portrait blurs the background behind a person. The blur is added after the shot, and the original stays with the photo, so you can change or remove the blur later.

## The viewfinder is a guide

While you frame, Lilac shows the camera's face frames and softly blurs around each person. This live blur is only a preview, drawn quickly on the graphics chip:

- It shows only for a near face: none for a face further than about 1.7 m, full strength from about 0.7 m (a selfie at arm's length).
- Each face, up to five, keeps its own sharp area.
- With no face in view the picture stays plain.

The real blur is made after the shot, from the full photo.

## What happens after the shot

1. Lilac finds the subject and cuts it out. It works best on people; pets and objects work too.
2. It blurs the rest, centred on the face it saw just before you pressed. A tapped focus point wins over the face.
3. The photo is saved with the clean original and the blur settings beside it.

The blur is worked out at a reduced size (1536 pixels on the long edge) and laid over the full photo, which keeps memory use low. It takes a few seconds.

## Quick or Depth

Choose under **Portrait blur** in quick settings.

| | Quick (default) | Depth |
|---|---|---|
| Blur | even across the background | grows with distance, like a real lens |
| Speed | instant | slower |
| Needs | nothing | a depth model, about 100 MB, downloaded once |

Choosing Depth without the model opens the Portrait guide with a **Download depth model** button. You can also import another compatible TFLite depth model.

## Strength and style

- **Av** among the manual values sets the blur strength, like an aperture: lower numbers blur more. The default is f/2.0.
- **Bokeh style** in quick settings shapes the out-of-focus highlights: Natural (round), Swirl (an old lens) or Bubble (bright rings).

## Change it later

Open the photo in the [editor](editor.md) and go to **Portrait**: aperture (or Off), depth of field, focus on the face or a point you tap, bokeh style, and Quick or Depth. Pro photos can get a blur there too, though Pro itself never blurs.
