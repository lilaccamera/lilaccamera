# Focus and faces

Lilac focuses the way Sony's own camera app does: it hands the work to the camera's own subject tracking and face detection, and adds a few rules for this phone's weak spots.

## Tap to focus

On the back camera a tap does more than focus on a point:

- **Tap an object:** Sony's tracking locks on to it, and the focus mark follows it as it moves. Exposure follows too. Tap it again to go back to automatic.
- **Tap a face frame:** that face becomes the main face.
- **Tap nothing in particular**, such as a plain wall or a dark area: Lilac notices the tracking has nothing to hold and focuses on the spot you tapped instead.

The focus frame is a thin square: white while focusing, green with a short tick when focus locks, three white blinks on a miss. It fades 1.5 s later.

A tapped point clears itself about 11 s after the last tap or focus result. To keep focus or exposure fixed for longer, set them by hand among the manual values.

The front camera has fixed focus, so a tap only sets the exposure.

## The camera key

On the XZ1 Compact's two-stage camera key:

- **Half-press** locks focus and exposure on the tracked object or the main face, or the centre if there is neither, for as long as you hold it. An amber **AE-L** mark shows that exposure is held.
- **Full press** shoots.

Letting go during a burst waits until the burst has finished.

## Focus in dim light

The camera finds focus by looking for contrast. In dim light a small area has too little to go on. So:

- **From ISO 1600** any small focus area (a tap, a small face, the half-press centre) is grown to a fifth of the frame around its centre. A half-press on a far background went from missing to focusing in about 1.5 s.
- **On a miss with nothing near:** the back camera has a small laser that measures distance in the middle of the frame, up to about 2 m. When focus fails, the target is in that middle zone and the laser sees nothing near, Lilac sets the lens to its hyperfocal distance (about 4.3 m), which keeps everything from about 2 m to infinity sharp. Any new tap, a laser reading or a scene change hands focus back to the camera.
- **After two misses in a row** Lilac stops starting new searches on its own, so the picture does not keep pumping in and out. A tap, a new face or a change of subject starts again.

Lower resolutions also focus faster: see [Photo and Night](photo-mode.md#back-camera-use-12-mp).

## Faces

Two face detectors work together.

| | In the camera | In the app |
|---|---|---|
| Runs on | Sony's camera chip, every frame, free | the phone's processor, a small neural network |
| Finds | up to five faces, a box and a score each | faces and the line of the eyes, upright faces only |
| Cost | none | 55 to 130 ms each time |

Lilac uses the camera's faces for nearly everything, because they cost nothing:

- **Face frames, focus and exposure** follow the camera's boxes. A face keeps its frame for a moment after it is lost, so frames do not flicker.
- **Smile shutter** uses Sony's smile score, 0 to 100 for each face.
- **The self-timer lamp** blinks while a face is in view.
- **Portrait's viewfinder** keeps a sharp area around each of the camera's faces.

The app's own detector is called only when it adds something:

- on a tap that missed the camera's faces, to check whether you tapped a face,
- twice a second while an object is tracked,
- on a half-press with nothing selected, for up to 0.4 s,
- for Portrait, to find the line of the eyes,
- for **Human Eye AF** (Settings › Focus & Lens, off by default), which focuses on a steady eye.

Turning the phone sideways is fine: the app turns the picture upright before it looks.

## Metering

**Metering** in quick settings decides which part of the frame sets the exposure: **Auto** (the camera decides), **Centre**, **Spot** (a small circle that follows your tap; drag it to meter elsewhere), **Avg** (the whole frame) or **High** (keeps bright areas from turning white).
