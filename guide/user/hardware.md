---
icon: lucide/cpu
---

# Sony camera features

Lilac aims to bring everything Sony's own camera app does to custom ROMs, and to add GCam's multi-frame processing on top. Sony's camera software publishes more than Android's standard camera interface shows, and Lilac uses the parts it can reach. This page lists what is done and what is not.

## Sony's camera app and Lilac

| Sony's camera | In Lilac |
|---|---|
| Face detection | **Done.** Light face frames on both cameras; focus and exposure follow the main face |
| Subject tracking | **Done.** Tap an object and it is followed (back camera) |
| Focus and exposure marks, the half-press | **Done.** Sony-style focus frame, metering circle, AE-L |
| Laser distance sensor | **Partly.** A far-focus fallback in the dark |
| Two-stage camera key | **Done.** Half-press holds focus, full press shoots |
| Smile shutter | **Done** |
| Scene and condition recognition | **Partly.** Icons on the top line; nothing changes because of them |
| Sony's photo processing | **Done on the front camera**, as Accurate colour; **Camera JPEG** keeps Sony's photo in Pro |
| Super slow motion, 960 fps | **Done**, at 720p. Needs Sony's cacao service and the Lilac Sony Bridge on the ROM |
| Still HDR | **By hand.** Only as a [custom vendor key](#custom-vendor-keys) |
| Multi-frame noise reduction | **Not used.** Lilac merges the frames itself |
| Video stabilisation (SteadyShot) | **Not yet.** Lilac's own EIS+ replaces it |

What each ROM provides, and which ROM was tested: [Get Lilac](../index.md#get-lilac).

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

The camera's own features that Lilac uses, in detail. Open ![](../icons/CameraLine.svg){ .icon } on a card to see what each one does in the app.

<div class="lilac-features" markdown="0">
<div class="lilac-feature"><img src="../icons/User.svg" alt="" class="lilac-feature__icon"><div><strong>Face detection</strong><span>On the camera chip, up to five faces, at no cost to the phone.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>Lilac draws the face frames and points focus and exposure at faces. Portrait keeps a sharp area round each face in the viewfinder, and the self-timer lamp blinks while a face is in view. See <a href="focus.md#faces">Faces</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/FocusCenteredLine.svg" alt="" class="lilac-feature__icon"><div><strong>Subject tracking</strong><span>Back camera: the camera follows an object as it moves.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>Tap an object and the focus mark follows it as it moves, with the exposure. A half-press of the camera key locks on to it. See <a href="focus.md#tap-to-focus">Tap to focus</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/FocusLine.svg" alt="" class="lilac-feature__icon"><div><strong>Focus area</strong><span>The camera focuses on the area the app gives it.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>Focus goes where you tap. In dim light Lilac makes a small area bigger, so the camera has enough contrast to focus. See <a href="focus.md#focus-in-dim-light">Focus in dim light</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/ExposureLine.svg" alt="" class="lilac-feature__icon"><div><strong>Exposure on faces</strong><span>The camera meters on the faces it finds.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>Faces come out well exposed without tapping them. See <a href="focus.md#faces">Faces</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/MountainLine.svg" alt="" class="lilac-feature__icon"><div><strong>Laser distance sensor</strong><span>Back camera: measures distance in the middle of the frame, up to about 2 m.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>When focus fails in the dark and the laser sees nothing near, Lilac sets the lens so everything from about 2 m to infinity is sharp. See <a href="focus.md#focus-in-dim-light">Focus in dim light</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/MoodSmileLine.svg" alt="" class="lilac-feature__icon"><div><strong>Smile scores</strong><span>How much each face smiles, 0 to 100.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>Smile shutter takes a photo each time the main face smiles enough. See <a href="photo-mode.md#smile-shutter">Smile shutter</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/PaletteLine.svg" alt="" class="lilac-feature__icon"><div><strong>Still processing</strong><span>Sony's full photo pipeline, with the true black level.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>The front camera's Accurate colour, in Photo, Portrait and Night, removes the pink cast from dim shadows. In Pro, Camera JPEG keeps Sony's own photo to compare with. See <a href="photo-mode.md#front-camera-colour-fast-or-accurate">Front camera colour</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/CameraLine.svg" alt="" class="lilac-feature__icon"><div><strong>Two-stage camera key</strong><span>A half-press and a full press, like a compact camera.</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>A half-press focuses and holds focus and exposure; a full press takes the photo or starts and stops a video; during slow motion it fires the burst. See <a href="getting-started.md#buttons-on-the-side">Buttons on the side</a>.</p></details></div></div>
<div class="lilac-feature"><img src="../icons/MovieCamera.svg" alt="" class="lilac-feature__icon"><div><strong>Super slow motion service</strong><span>Sony's cacao service records 960 frames a second (ROM support needed).</span><details class="lilac-feature__enables"><summary><img src="../icons/CameraLine.svg" alt=""><span class="lilac-sr">What it does in Lilac</span></summary><p>Video mode's Slow-mo switch records a 960 fps burst at 720p that plays back in slow motion. See <a href="video.md#slow-motion">Slow motion</a>.</p></details></div></div>
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
