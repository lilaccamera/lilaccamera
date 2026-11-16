# Troubleshooting

Each message is quoted as the app shows it.

## Taking photos

**"Still saving the last photo."**
:   A Pro shot was pressed while the camera still held the previous RAW frame. No photo was taken. Wait until the shutter looks normal again, then press. Quick taps in a row are ignored in Pro.

**"Not ready: still taking the last photo. No photo was taken."**
:   The previous capture had not finished. Wait for the ring around the shutter to finish, then press again.

**"Pro shot failed. Camera restarted, try again."**
:   The camera dropped a RAW frame or reported an error during the shot. Lilac reopened the camera to clear it. That shot is lost; press the shutter again.

**"Pro failed again. Restart the app. Still failing? Restart the phone."**
:   A second RAW failure after the restart. Pro refuses further shots until the app restarts. Swipe Lilac away from Recents and open it again. If Pro still fails, restart the phone: that clears memory the camera driver keeps.

**There is no Pro on the mode dial**
:   Pro needs RAW frames from the camera. The front camera has none, and on the XZ1 Compact the back camera only gives them on a ROM with the raw19 shim. Photo mode still works, with stacking, HDR and looks.

**"Not available on this camera"**
:   You tapped the shutter speed or ISO on the front camera, which has no manual control of them. Use EV instead.

## Pro photos that wait or stop

Pro photos are finished in the background; see [the render queue](pro-mode.md#the-render-queue).

**"Waiting for free memory"**
:   Each photo waits for about 450 MB of free memory before it starts, and starts anyway after a minute. Close apps you are not using.

**"Short of memory"** / **"Android keeps stopping it for memory. Swipe the app away to let it finish."**
:   Android stopped the same photo twice to free memory. Swipe Lilac Camera away from Recents: processing carries on and usually finishes, because closing the app frees its graphics memory. If it still stops, restart the phone. The thumbnail's ring turns amber while this lasts.

**"Couldn’t process after 3 tries. The RAW file is kept."**
:   The photo failed three times or hung for five minutes. Nothing is lost. Open the gallery, tap the layers icon (it shows **!**) and tap **Try again**. Restarting the phone first gives it the most room.

**"Processing starts when you open the app, or tap Process now."**
:   Processing was stopped, for example by Force stop. Tap **Process now**. Avoid Force stop: it halts processing until the app opens again (progress at save points is kept).

**"Only single-frame Pro photos can be re-rendered"**
:   Merged Pro bursts cannot be re-rendered as a copy, because their noise reduction is baked in.

## Video

**"Camera sensor at 66 °C: too hot to record. Let the phone cool down."**
:   A temperature is already at its stop point, so recording does not start. The source can also read "Phone body" or "Battery". Let the phone cool.

**"Recording stopped"** / **"Your video is saved. The camera is off so the phone can cool down."**
:   The phone reached a stop point while recording. The video is saved. The screen shows how far the phone still has to cool; wait for "Cool enough to record", then tap **Back to camera**. See [Video](video.md#overheating).

**"Stop recording to switch mode"** / **"Locked while recording"**
:   Modes and settings do not change during a recording. Stop it first.

## After a long session

Camera and graphics drivers keep some memory until the phone restarts. Before a long session of Pro shots, restart the phone.
