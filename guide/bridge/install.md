---
icon: lucide/plug
---

# Install the slow-motion bridge

960 fps slow motion needs a small companion app, the **Lilac Sony Bridge**, installed as a privileged system app. If your ROM ships it, you are done: this page is not for you. This page is for **testers** whose ROM has Sony's slow-motion service but not the bridge.

!!! warning "Before you start"
    - You need a computer with **adb**, and on the phone **USB debugging** and **Rooted debugging** (Settings › System › Developer options). Rooted debugging exists only on debug-capable (userdebug) builds such as sunplex07's LineageOS 23.2.
    - The install writes to the system partition and **reboots the phone**.
    - It has only been tested on **sunplex07's unofficial LineageOS 23.2 for lilac**.
    - Not for everyday users: if you just want slow motion, ask your ROM maintainer to include the bridge.

## What it does, and what it leaves alone

- **Unlocks** 960 fps slow motion at 720p in Lilac's Video mode.
- **Offers Lilac to other apps:** when an app asks for a photo or video (a messenger's camera button, a contact photo), Android lists only cameras that came with the system. The bridge is one, so it lists "Lilac Camera" and passes the request on to Lilac. It does this only while Lilac is installed as an ordinary app.
- **Adds** one folder, `/system/priv-app/LilacSonyBridge`, with one APK in it.
- **Does not touch** vendor files, SELinux settings or the bootloader. `bridge.sh uninstall` removes everything it added.

Why a separate app: Sony's slow-motion service (cacao) only talks to privileged system apps, and its libraries can only be loaded by one. Lilac itself stays an ordinary app you can update; the bridge does the privileged part.

## Lilac and the bridge

- **Any newer Lilac works with your bridge:** the interface between them only grows, and Lilac uses only what the installed bridge offers. A feature that needs a newer bridge says so in the release notes.
- **Signature:** the bridge serves only a Lilac signed with the certificate of this project's releases (Lilac and, once it has its own key, Lilac Beta), whatever key the bridge itself is signed with. A Lilac built and signed by anyone else is refused.

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

4. In Lilac, switch to Video and swipe down on the picture: a **Slow-mo** switch appears. See [slow motion](../user/video.md#slow-motion).

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

This deletes the bridge's folder and reboots. Lilac keeps working; the Slow-mo switch disappears.
