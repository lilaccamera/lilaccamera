# Sony hardware

The XZ1 Compact's camera does more than Android's standard camera interface shows. Sony's camera software publishes extra controls, and Lilac uses the ones that help.

## The phone's cameras

| | Back | Front |
|---|---|---|
| Sensor | Sony IMX400, 19 MP (5056 × 3792) | 8 MP (3264 × 2448) |
| RAW | yes, through the raw19 shim | no |
| Manual ISO and shutter | yes, ISO 50 to 3200 | no |
| Focus | autofocus with a laser distance sensor | fixed |
| Stabilisation | none in the lens | none |
| Flash | LED | the screen lights the face |

Above ISO 320 the back sensor's gain is digital rather than analog.

## What Lilac uses

From the camera:

- **Face detection** on the camera chip, up to five faces: face frames, focus, exposure, Portrait and the self-timer lamp. See [Focus and faces](focus.md#faces).
- **Subject tracking** (back camera): tap an object and the camera follows it.
- **Focus on the app's area**, so focus goes where Lilac points it.
- **Exposure on faces.**
- **The laser distance reading** (back camera): only as a hint that the subject is far.
- **Smile scores**, for the smile shutter.
- **Scene and condition detection:** small icons for backlight, night, low light, landscape, portrait, document, food, spotlight, tripod, walking and motion. They only inform; Lilac changes nothing because of them.
- **Sony's still processing** on the front camera, for the Accurate colour.
- **The two-stage camera key:** half-press to lock, full press to shoot.

From the phone: the gyroscope (steadiest frame, Night's shake plan), the light sensor (the Night offer), the flash LED and the vibrator.

## Vendor keys

Android apps talk to the camera through Camera2, a standard set of controls such as exposure time, ISO and focus. A camera's software can add its own controls on top, called **vendor keys**, with names like `com.sonymobile.control.afRegionMode`. They are not documented. An app finds them in the lists the camera reports, and has to know each key's type and values.

They are risky: a key the camera does not accept, or a wrong value, makes the camera refuse the request, and Android closes the camera. So Lilac sends each Sony key only to a camera that lists it. The front camera, for one, closes on the laser key.

??? info "The Sony keys Lilac uses"
    | Key (`com.sonymobile.…`) | For |
    |---|---|
    | `statistics.objectSelectTrigger`, `objectSelectTriggerArea` | start and stop subject tracking at a tap |
    | `statistics.objectSelectArea` | where the tracked subject is |
    | `statistics.faceSelectTrigger`, `faceSelectArea` | choose the main face |
    | `control.aeRegionMode` = face | exposure follows faces |
    | `control.afRegionMode` = user | focus on the app's area |
    | `statistics.tofMeasureMode`, `tofMeasureArea` | the laser distance reading |
    | `statistics.faceSmileScoresMode`, `faceSmileScores` | smile scores |
    | `statistics.sceneDetectMode`, `scene`, `conditionDetectMode`, `condition` | scene and condition icons |

??? info "Keys found but not used"
    These exist on the phone, in Sony's own camera app, but Lilac does not use them: still HDR (`control.stillHdrMode`), Sony's ISO and shutter hints, a power-save mode for heat, skin smoothing, Sony's distortion correction (it did nothing in this camera path), an amber-blue colour nudge, the colour-of-light sensor, Sony's own exposure modes (meanings unknown), and dual-camera controls (this phone has one back camera).

### Custom vendor keys

Settings › Focus & Lens › Camera2 Extensions › **Custom Vendor Keys** lets you add a key yourself: its full name, whether it goes on each request (Capture key) or on the camera session (Session key), its type (int32 or u8), the value and the lens (0 is the back camera, 1 the front).

For example, Sony's still HDR: `com.sonymobile.control.stillHdrMode`, Capture key, u8, value 1, lens 0. In a test it about halved the blown-out area, on automatic exposure only, at the cost of about a third of a stop of light, and did nothing for the shadows.

!!! warning
    A key the camera does not accept stops the camera. If it will not open after you add a key, delete the key and restart the app.

## What Lilac does not reach

Sony's own photo and video service (it does Sony's multi-frame noise reduction, slow motion and SteadyShot video) only talks to privileged system apps. Lilac reaches it only for [slow motion](video.md#slow-motion), through a separate bridge app the ROM must ship.
