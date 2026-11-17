---
icon: lucide/package
---

# ROM integration (lilac maintainers)

This page is for maintainers of ROMs for the Xperia XZ1 Compact (lilac) who want to ship Lilac Camera, with 960 fps slow motion, as part of the ROM.

## What you ship

Prebuilt APKs from a stable Lilac release (the one marked **Latest**; pre-releases are betas), imported as they are. Either both apps, or only the bridge:

| App | Package | Installed to | Why |
|---|---|---|---|
| Lilac Camera | `io.github.lilaccamera` | `/system/app` | the camera (optional) |
| Lilac Sony Bridge | `io.github.lilaccamera.sonybridge` | `/system/priv-app` | reaches Sony's slow-motion service for Lilac, and passes other apps' photo and video requests to it |

Lilac cannot be built in the ROM tree (it is a Gradle build with Compose and its own native libraries), so both come in as prebuilts. With only the bridge, users install Lilac from GitHub and get the same features.

## Importing them

Keep the release signatures (`presigned`); do not re-sign either APK. The bridge trusts Lilac by the certificate of the project's releases, pinned in the bridge, not by the key the bridge itself is signed with. Users update the ROM's copies from later releases like any app, without waiting for a new ROM build. A newer Lilac works with an older bridge: the interface between them only grows, and Lilac uses only what the installed bridge offers.

```text title="Android.bp"
android_app_import {
    name: "LilacCamera",
    apk: "LilacCamera.apk",
    // keeps the release signature, so later releases install over it
    presigned: true,
    dex_preopt: {
        enabled: true,
    },
}

android_app_import {
    name: "LilacSonyBridge",
    apk: "LilacSonyBridge.apk",
    presigned: true,
    // installs to /system/priv-app
    privileged: true,
    dex_preopt: {
        enabled: true,
    },
}
```

```text title="device.mk"
PRODUCT_PACKAGES += \
    LilacCamera \
    LilacSonyBridge
```

For the bridge alone, leave out the `LilacCamera` module and its line in `PRODUCT_PACKAGES`.

- **No privapp-permissions XML is needed.** The bridge asks for no privileged permission; it only uses the signature permission it declares itself.
- **Do not sign either app with the platform key.** Lilac does not need platform rights.

## What the ROM must already have

Lilac does not bring any of Sony's or the shim's files. Do not copy them from another ROM or firmware: a mismatched set can break the camera.

--8<-- "rom-requirements.md"

- **raw19 shim:** it wraps Sony's camera HAL as `/vendor/lib/hw/camera.lilac-real.so`. Ask the shim's author about it. Without it, Pro does not appear in Lilac.
- **cacao:** Sony's photo service from the stock vendor image, `cacaoserver` running as binder service `cacao`, with `/system/lib64/libcacao_client.so` and `/system/lib64/libimageprocessorjni.so`. Without it, the Slow-mo switch does not appear.

## What a system install adds

- **Capture requests from other apps.** Since Android 11, an app that asks the system to take a photo or video (a messenger's camera button, for example) is only offered camera apps that came with the system. As a system app, Lilac answers those requests with its own capture screen. With only the bridge, the bridge takes those requests and passes them to a Lilac installed from GitHub, so it is offered as "Lilac Camera" all the same; the bridge does this only while Lilac is installed as an ordinary app, so a system Lilac is never listed twice.
- **Default camera.** You can make Lilac the default camera, or replace the ROM's camera with it.
- **Compiled from the first start.** Dexpreopt compiles Lilac at build time. A sideloaded release runs uncompiled until Android compiles it in the background (or `adb shell cmd package compile -m speed -f io.github.lilaccamera`).

## Checking a build

On the built ROM, with adb:

```bash
adb shell pm path io.github.lilaccamera.sonybridge
adb shell "dumpsys package io.github.lilaccamera.sonybridge | grep -E 'privateFlags|IMAGE_PROCESSOR'"
adb shell service check cacao
```

Expected: the path is under `/system/priv-app/LilacSonyBridge/`, the flags include `PRIVILEGED`, the permission `com.sonymobile.permission.IMAGE_PROCESSOR` is listed, and `Service cacao: found`. On a debug-capable build, `bridge.sh check` from the release runs the same checks, looks for the shim, and reports the bridge's interface version, whether it passes on photo and video requests, and which Lilac it serves.

Then in Lilac: Video mode, swipe down on the picture, and the **Slow-mo** switch is there.
