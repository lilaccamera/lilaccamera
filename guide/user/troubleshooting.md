---
icon: lucide/life-buoy
---

# Troubleshooting

Each message is quoted as the app shows it. Tap a question to see the answer.

## Taking photos

??? question "“Still saving the last photo.”"
    A Pro shot was pressed while the camera still held the previous RAW frame. No photo was taken. Wait until the shutter looks normal again, then press. Quick taps in a row are ignored in Pro.

??? question "“Not ready: still taking the last photo. No photo was taken.”"
    The previous capture had not finished. Wait for the ring around the shutter to finish, then press again.

??? question "“Pro shot failed. Camera restarted, try again.”"
    The camera dropped a RAW frame or reported an error during the shot. Lilac reopened the camera to clear it. That shot is lost; press the shutter again.

??? question "“Pro failed again. Restart the app. Still failing? Restart the phone.”"
    A second RAW failure after the restart. Pro refuses further shots until the app restarts. Swipe Lilac away from Recents and open it again. If Pro still fails, restart the phone: that clears memory the camera driver keeps.

??? question "There is no Pro on the mode dial"
    Pro needs the raw19 shim on the ROM and works on the back camera only; see [Pro](pro-mode.md). Photo mode still works.

??? question "“Not available on this camera”"
    You tapped the shutter speed or ISO on the front camera, which has no manual control of them. Use EV instead.

## Pro photos that wait or stop

Pro photos are finished in the background; see [the render queue](pro-mode.md#the-render-queue).

??? question "“Waiting for free memory”"
    The photo waits for free memory before it starts; see [the render queue](pro-mode.md#the-render-queue). Close apps you are not using.

??? question "“Short of memory” / “Android keeps stopping it for memory. Swipe the app away to let it finish.”"
    Android stopped the same photo twice to free memory. Swipe Lilac Camera away from Recents; if it still stops, restart the phone. See [when renders keep stopping](pro-mode.md#when-renders-keep-stopping).

??? question "“Couldn’t process after 3 tries. The RAW file is kept.”"
    The photo failed three times or hung for five minutes. Nothing is lost. Open the gallery, tap the render queue button in its top line (it shows **!**) and tap **Try again**. Restarting the phone first gives it the most room.

??? question "“Processing starts when you open the app, or tap Process now.”"
    Processing was stopped, for example by Force stop. Tap **Process now**.

??? question "“Only single-frame Pro photos can be re-rendered”"
    Merged Pro bursts cannot be re-rendered as a copy; see [Re-render as a copy](pro-mode.md#re-render-as-a-copy).

## Video

??? question "“Camera sensor at 66 °C: too hot to record. Let the phone cool down.”"
    A temperature is already at its stop point, so recording does not start. The source can also read "Phone body" or "Battery". Let the phone cool.

??? question "“Recording stopped” / “Your video is saved. The camera is off so the phone can cool down.”"
    The phone reached a stop point while recording. The video is saved. The screen shows how far the phone still has to cool; wait for "Cool enough to record", then tap **Back to camera**. See [Video](video.md#overheating).

??? question "“Stop recording to switch mode” / “Locked while recording”"
    Modes and settings do not change during a recording. Stop it first.
