# Video

## Settings

In Video the everyday row in quick settings holds **resolution**, **frame rate** (30 fps by default), **torch** and **stabilisation** (only when the camera offers more than one kind). Tap one to step to its next value.

The list below holds:

- **Aspect:** 16:9, 21:9 or Open gate (the whole sensor).
- **Log:** a flat picture to colour-grade later. The looks panel then offers a Log LUT that is either only for monitoring or baked into the video.
- **Bitrate, Mbps:** 30 (default), 60, 90, 120 or 250. Higher keeps more detail and makes bigger files.
- **Codec:** H.264 (default) plays everywhere; H.265 makes smaller files.
- **Microphone:** which microphone records the sound.

While recording, the top line shows the time, and the space left once under 20 minutes remain. The footer gives **Pause** and **Snapshot** (a photo from the video).

## Stabilisation

The XZ1 Compact has no stabiliser in the lens, and its camera offers no electronic stabilisation of its own. Video stabilisation is the app's:

- **Enhanced (EIS+)** reads the gyroscope and corrects shake and the rolling-shutter wobble on the graphics chip, between the camera and the video encoder. It looks a few frames ahead, so the picture runs slightly behind.
- **Off** records the plain picture.

Pick it under **Stabilise** in the everyday row, or set the default in Settings › Video, where **EIS+ Strength** and **EIS+ Lookahead Frames** tune it.

Use 30 or 60 fps with EIS+: at 24 fps the camera refuses the frame rate and closes.

## Slow motion

The back sensor can record a very short burst at **960 frames a second** into its own memory. Lilac writes that burst into a normal 30 fps video, so it plays back 32 times slower.

!!! note "Needs the Lilac Sony Bridge"
    Slow motion goes through Sony's own super-slow-motion service, which only a privileged system app can reach. Your ROM must ship Lilac's companion app, the Lilac Sony Bridge. The bridge and Lilac are a matched pair: they must come from the same release, and the bridge only serves a Lilac signed with a key it trusts. Without it the slow-motion switch does not appear. Ask your ROM maintainer.

**How to use it**

1. In Video, swipe down on the picture and turn **Slow-mo** on. It switches to 720p; the top edge shows **Super slow-mo 960**.
2. Press the shutter to start recording. Starting takes a few seconds while the camera switches to Sony's mode.
3. At the moment that matters, press the camera key, or the amber ![Slow-mo burst](../icons/BoltLine.svg){ .icon } button in the footer. A counter shows the frames, then a tick.
4. Press the shutter to stop.

**Limits**

- **720p only.** Choosing another resolution turns slow motion off.
- **One burst per recording.** The burst button greys out after it fires; start a new recording for another.
- **The burst is short:** 177 frames, under a fifth of a second of real time, which plays as about 6 seconds.
- **It needs bright light:** each frame is at most 1/960 s. "Too dark for slow-mo" warns when it would need more than about ISO 1600. Under mains lighting, expect flicker.
- **Back camera only.** Torch and stabilisation are off while it is on.
- The rest of the recording is normal 30 fps video with sound; the slow part is silent.
- The heat limits below apply; several recordings in a row heat the sensor quickly.

## Overheating

Long recordings heat the phone. Lilac watches its temperatures once a second while recording and stops before the phone gets too hot.

| Reading | Warns at | Stops at |
|---|---|---|
| Camera sensor | 59 °C | 65 °C |
| Phone body | 47 °C | 52 °C |
| Battery | 47 °C | 50 °C |

- **Warning:** from a warning point, a gauge appears under the recording time with the temperature and the stop point. It turns red two thirds of the way there.
- **Stop:** the video is saved first, then the camera turns off and a card shows what stopped it and how far the phone still has to cool. At 42 °C on the phone body it says "Cool enough to record"; tap **Back to camera**.
- **Too hot to start:** if a reading is already at its stop point, recording does not start.

The phone body reads much cooler than the camera sensor. They are different sensors: the camera's image sensor runs about 13 °C warmer than the board. Cooling to 42 °C on the board lets the sensor start again near 55 °C, with room for another clip.

Heat is only checked while recording video; photo modes are not affected.
