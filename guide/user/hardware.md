---
icon: lucide/cpu
---

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

Each Sony hardware feature below, and what it makes possible in Lilac:

<div class="lilac-features" markdown="0">
<div class="lilac-feature"><img src="../icons/User.svg" alt="" class="lilac-feature__icon"><div><strong>Face detection</strong><span>On the camera chip, up to five faces, at no cost to the phone.</span><span class="lilac-feature__enables">In Lilac: <a href="focus.md#faces">face frames, focus and exposure on faces</a> · <a href="portrait.md">Portrait's viewfinder</a> · the self-timer lamp</span></div></div>
<div class="lilac-feature"><img src="../icons/FocusCenteredLine.svg" alt="" class="lilac-feature__icon"><div><strong>Subject tracking</strong><span>Back camera: the camera follows an object as it moves.</span><span class="lilac-feature__enables">In Lilac: <a href="focus.md#tap-to-focus">tap to track</a> · <a href="focus.md#the-camera-key">half-press locks on the tracked object</a></span></div></div>
<div class="lilac-feature"><img src="../icons/FocusLine.svg" alt="" class="lilac-feature__icon"><div><strong>Focus area</strong><span>The camera focuses on the area the app gives it.</span><span class="lilac-feature__enables">In Lilac: <a href="focus.md#tap-to-focus">tap to focus</a> · <a href="focus.md#focus-in-dim-light">larger focus areas in dim light</a></span></div></div>
<div class="lilac-feature"><img src="../icons/ExposureLine.svg" alt="" class="lilac-feature__icon"><div><strong>Exposure on faces</strong><span>The camera meters on the faces it finds.</span><span class="lilac-feature__enables">In Lilac: exposure that follows faces</span></div></div>
<div class="lilac-feature"><img src="../icons/MountainLine.svg" alt="" class="lilac-feature__icon"><div><strong>Laser distance sensor</strong><span>Back camera: measures distance in the middle of the frame, up to about 2 m.</span><span class="lilac-feature__enables">In Lilac: <a href="focus.md#focus-in-dim-light">the far-focus fallback when focus fails</a></span></div></div>
<div class="lilac-feature"><img src="../icons/MoodSmileLine.svg" alt="" class="lilac-feature__icon"><div><strong>Smile scores</strong><span>How much each face smiles, 0 to 100.</span><span class="lilac-feature__enables">In Lilac: <a href="photo-mode.md#smile-shutter">Smile shutter</a></span></div></div>
<div class="lilac-feature"><img src="../icons/PaletteLine.svg" alt="" class="lilac-feature__icon"><div><strong>Still processing</strong><span>Sony's full photo pipeline, with the true black level.</span><span class="lilac-feature__enables">In Lilac: <a href="photo-mode.md#front-camera-colour-fast-or-accurate">front camera Accurate colour</a> · Night's Accurate on the front camera · Pro's Camera JPEG copy</span></div></div>
<div class="lilac-feature"><img src="../icons/CameraLine.svg" alt="" class="lilac-feature__icon"><div><strong>Two-stage camera key</strong><span>A half-press and a full press, like a compact camera.</span><span class="lilac-feature__enables">In Lilac: <a href="getting-started.md#buttons-on-the-side">focus lock, shutter and video start</a> · the slow-motion burst</span></div></div>
<div class="lilac-feature"><img src="../icons/MovieCamera.svg" alt="" class="lilac-feature__icon"><div><strong>Super slow motion service</strong><span>Sony's cacao service records 960 frames a second (ROM support needed).</span><span class="lilac-feature__enables">In Lilac: <a href="video.md#slow-motion">960 fps slow motion</a></span></div></div>
</div>

### Scene and condition detection

Sony's camera recognises the scene and how the phone is held. Lilac shows these icons at the right end of the top line. They only inform; Lilac changes nothing because of them.

<div class="lilac-scenes" markdown="0">
<span class="lilac-scene"><img src="../icons/HazeLine.svg" alt="" class="lilac-feature__icon"><small>Backlight</small></span>
<span class="lilac-scene"><img src="../icons/MoonLine.svg" alt="" class="lilac-feature__icon"><small>Night</small></span>
<span class="lilac-scene"><img src="../icons/CandleLine.svg" alt="" class="lilac-feature__icon"><small>Low light</small></span>
<span class="lilac-scene"><img src="../icons/MountainLine.svg" alt="" class="lilac-feature__icon"><small>Landscape</small></span>
<span class="lilac-scene"><img src="../icons/UserLine.svg" alt="" class="lilac-feature__icon"><small>Portrait</small></span>
<span class="lilac-scene"><img src="../icons/FileTextLine.svg" alt="" class="lilac-feature__icon"><small>Document</small></span>
<span class="lilac-scene"><img src="../icons/KitchenLine.svg" alt="" class="lilac-feature__icon"><small>Food</small></span>
<span class="lilac-scene"><img src="../icons/LampLine.svg" alt="" class="lilac-feature__icon"><small>Spotlight</small></span>
<span class="lilac-scene"><img src="../icons/MacroLine.svg" alt="" class="lilac-feature__icon"><small>Macro</small></span>
<span class="lilac-scene"><img src="../icons/TripodLine.svg" alt="" class="lilac-feature__icon"><small>Tripod</small></span>
<span class="lilac-scene"><img src="../icons/WalkLine.svg" alt="" class="lilac-feature__icon"><small>Walking</small></span>
<span class="lilac-scene"><img src="../icons/RunLine.svg" alt="" class="lilac-feature__icon"><small>Motion</small></span>
</div>

From the phone: the gyroscope (the steadiest frame, Night's shake plan), the light sensor (the Night offer), the flash LED and the vibrator.

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

Sony's own photo and video service (it does Sony's multi-frame noise reduction, slow motion and SteadyShot video) only talks to privileged system apps. Lilac reaches it only for [slow motion](video.md#slow-motion), through a separate [bridge app](../bridge/install.md) the ROM must ship.
