---
icon: lucide/plug
---

# Install the Sony bridge

Some of Lilac's features need a small companion app, the **Lilac Sony Bridge**, installed as a privileged system app. If your ROM ships it, you are done: this page is not for you. This page is for **testers** whose ROM has Sony's slow-motion service but not the bridge.

!!! warning "Before you start"
    - You need a computer with **adb**, and on the phone **USB debugging** and **Rooted debugging** (Settings › System › Developer options). Rooted debugging exists only on debug-capable (userdebug) builds such as sunplex07's LineageOS 23.2.
    - The install writes to the system partition and **reboots the phone**.
    - It has only been tested on **sunplex07's unofficial LineageOS 23.2 for lilac**.
    - Not for everyday users: if you just want slow motion, ask your ROM maintainer to include the bridge.

## What it does, and what it leaves alone

- **Unlocks** 960 fps slow motion at 720p in Lilac's Video mode.
- **Adds** [1080p at 60 fps and 720p at 120 fps](#more-frame-rates) to Video.
- **Adds** [Sony's video stabilisation](#sony-stabilisation), Steady and Active.
- **Offers Lilac to other apps:** [see below](#photo-and-video-requests-from-other-apps).
- **Adds** one folder, `/system/priv-app/LilacSonyBridge`, with one APK in it.
- **Does not touch** vendor files, SELinux settings or the bootloader. `bridge.sh uninstall` removes everything it added.

Why a separate app: Sony's slow-motion service (cacao) only talks to privileged system apps, and its libraries can only be loaded by one. Lilac itself stays an ordinary app you can update; the bridge does the privileged part.

## What the bridge adds

These features exist **only in Lilac**, and only with the bridge. Without it, Video simply does not offer them.

### Photo and video requests from other apps

When an app asks for a photo or a video (a messenger's camera button, a contact photo, a file upload), Android lists only cameras that came with the system. The bridge is one, so it adds a **Lilac Camera** entry to that list and hands the request to Lilac. The picture goes back to the app that asked.

- The entry shows only while a Lilac signed with this project's key is installed, and only while that Lilac is **not** itself a system app. A system Lilac is listed on its own.
- After installing Lilac, **open it once**. That is when the bridge notices it.
- If the entry is missing, run `bash bridge.sh check`: the forwarder line says `on` or `off`.

### More frame rates

Video can record at:

- **1080p at 60 fps**
- **720p at 120 fps**

Both are for the **rear camera at 16:9** and need good light. They show in the frame-rate step only when the bridge is installed. Details: [video](../user/video.md#frame-rates).

120 fps files play at full speed in other apps. Lilac's gallery plays them four times slower, and asks **Slowed** or **Full speed** when you share or export one.

### Sony stabilisation

The **Stabilise** item in Video steps through **Off**, **EIS+**, **Steady** and **Active**, and offers each only where it works. **Steady** and **Active** are Sony's own SteadyShot and Intelligent Active. Which size and rate allow which: [video](../user/video.md#stabilisation).

## Lilac and the bridge

- **Any newer Lilac works with your bridge:** the interface between them only grows, and Lilac uses only what the installed bridge offers. A feature that needs a newer bridge says so in the release notes.
- **Signature:** the bridge serves only a Lilac signed with the certificate of this project's releases (Lilac and, Lilac Beta), whatever key the bridge itself is signed with. A Lilac built and signed by anyone else is refused.

## Install

From the [GitHub release](https://github.com/lilaccamera/lilaccamera/releases/latest) of your Lilac, download `LilacSonyBridge.apk` and `bridge.sh`. Connect the phone, then on the computer:

1. **Check the phone first.** Nothing is changed:

    ```bash
    bash bridge.sh check
    ```

    It reports `cacao: found` (Sony's slow-motion service and its libraries) and `raw19 shim: found` (the RAW shim Pro mode needs).

2. **Install**, always naming the downloaded APK:

    ```bash
    bash bridge.sh install LilacSonyBridge.apk
    ```

    The script checks the phone again, copies the APK into place and reboots.

3. **Check again** after the reboot. The package line should show `/system/priv-app/LilacSonyBridge/`, and the flags `PRIVILEGED`. The request forwarder line says `on` and names the Lilac it serves once Lilac is installed; if Lilac was installed after the reboot, open it once first (that is when Lilac connects to the bridge).

4. In Lilac, switch to Video and swipe down on the picture: a **Slow-mo** switch appears. See [slow motion](../user/video.md#slow-motion). The new frame rates and stabilisers appear in the everyday row.

## Without adb: a root file browser

If you have root on the phone but not rooted adb, you can do by hand what `bridge.sh install` does. This has not been tried on the phone.

- **Prepare:** download `LilacSonyBridge.apk` as above. A copy of the bridge installed as a normal app hides the system copy, so uninstall it first if you have one (Settings › Apps).
- **Check the ROM:** these files must exist, `/system/lib64/libcacao_client.so` and `/system/lib64/libimageprocessorjni.so` (Sony's slow-motion service) and `/vendor/lib/hw/camera.lilac-real.so` (the RAW shim, only for Pro).
- **Unlock:** in the file browser, give it root and mount `/system` read-write (most have a switch for this).
- **Place:** create the folder `/system/priv-app/LilacSonyBridge` and copy the APK into it under exactly the name `LilacSonyBridge.apk`.
- **Permissions:** set the file's permissions to `rw-r--r--` (644), owned by root. The script also sets the SELinux label `u:object_r:system_file:s0`, which a file browser cannot; a file made inside that folder normally gets it anyway. If the bridge does not work after the reboot, check the label first (`ls -Z`).
- **Reboot:** mount `/system` read-only again if the browser asks, then **reboot**. Android picks up system apps only at boot.
- **Check:** run the checks in step 3 above with normal adb, or look for **Lilac Sony Bridge** in Settings › Apps with system apps shown. Then do step 4.

## What the warnings mean

**`cacao: missing … Slow motion will not work without it.`**
:   The ROM does not have Sony's slow-motion service or its libraries. The bridge cannot help on this ROM; the script asks `Install anyway? [y/N]` and stops unless you type `y`.

**`raw19 shim: missing … Pro mode needs it for RAW.`**
:   The ROM has no RAW shim. Slow motion does not need it, but Pro mode will not be available. Type `y` to install anyway.

**`Could not check the phone; nothing was changed.`**
:   adb could not reach the phone. Check the cable, USB debugging and Rooted debugging, then try again. The script never reports "missing" when it simply could not check.

## Undo

```bash
bash bridge.sh uninstall
```

This deletes the bridge's folder and reboots. Lilac keeps working; the Slow-mo switch, the extra frame rates, Steady and Active, and the Lilac Camera entry for other apps disappear.

If you built from source, use `sonybridge/tools/bridge.sh` and your own `sonybridge-debug.apk` with a debug-signed Lilac.
