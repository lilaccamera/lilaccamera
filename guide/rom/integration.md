---
icon: lucide/package
---

# ROM integration (lilac maintainers)

This page is for maintainers of ROMs for the Xperia XZ1 Compact (lilac) who want to ship Lilac Camera, with 960 fps slow motion, as part of the ROM.

## What you ship

Two prebuilt APKs from a stable Lilac release (the one marked **Latest**; pre-releases are betas), imported as they are:

| App | Package | Installed to | Why |
|---|---|---|---|
| Lilac Camera | `io.github.lilaccamera` | `/system/app` | the camera |
| Lilac Sony Bridge | `io.github.lilaccamera.sonybridge` | `/system/priv-app` | reaches Sony's slow-motion service for Lilac |

Lilac cannot be built in the ROM tree (it is a Gradle build with Compose and its own native libraries), so both come in as prebuilts. Take them from the **same release**: they are a [matched pair](../bridge/install.md#lilac-and-the-bridge-are-a-matched-pair).

## Importing them

Keep the release signature (`presigned`). Both APKs are then signed with the same key, so the pair always matches, and users can update the ROM's copies from later releases like any app, without waiting for a new ROM build.

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
    // signed with the same key as LilacCamera
    presigned: true,
    // installs to /system/priv-app
    privileged: true,
}
```

```text title="device.mk"
PRODUCT_PACKAGES += \
    LilacCamera \
    LilacSonyBridge
```

- **No privapp-permissions XML is needed.** The bridge asks for no privileged permission; it only uses the signature permission it declares itself.
- **Do not sign either app with the platform key.** Lilac does not need platform rights, and the bridge must not trust platform-signed apps.
- **If your ROM must re-sign** prebuilts, use `default_dev_cert: true` for both instead. The pair still matches, and the bridge also serves Lilac Beta (it trusts the release key's certificate). Users can then update the ROM's copies only through the ROM.

!!! warning "Release keys"
    When re-signing, a ROM signed with AOSP's public test keys makes the bridge reachable by any app signed with those keys, as long as it uses Lilac's package name. Re-sign only with your own private keys.

## What the ROM must already have

Lilac does not bring any of Sony's or the shim's files. Do not copy them from another ROM or firmware: a mismatched set can break the camera.

--8<-- "rom-requirements.md"

- **raw19 shim:** it wraps Sony's camera HAL as `/vendor/lib/hw/camera.lilac-real.so`. Ask the shim's author about it. Without it, Pro does not appear in Lilac.
- **cacao:** Sony's photo service from the stock vendor image, `cacaoserver` running as binder service `cacao`, with `/system/lib64/libcacao_client.so` and `/system/lib64/libimageprocessorjni.so`. Without it, the Slow-mo switch does not appear.

## What a system install adds

- **Capture requests from other apps.** Since Android 11, an app that asks the system to take a photo or video (a messenger's camera button, for example) is only offered camera apps that came with the system. As a system app, Lilac answers those requests with its own capture screen.
- **Default camera.** You can make Lilac the default camera, or replace the ROM's camera with it.
- **Compiled from the first start.** Dexpreopt compiles Lilac at build time. A sideloaded release runs uncompiled until Android compiles it in the background (or `adb shell cmd package compile -m speed -f io.github.lilaccamera`).

## Checking a build

On the built ROM, with adb:

```bash
adb shell pm path io.github.lilaccamera.sonybridge
adb shell "dumpsys package io.github.lilaccamera.sonybridge | grep -E 'privateFlags|IMAGE_PROCESSOR'"
adb shell service check cacao
```

Expected: the path is under `/system/priv-app/LilacSonyBridge/`, the flags include `PRIVILEGED`, the permission `com.sonymobile.permission.IMAGE_PROCESSOR` is listed, and `Service cacao: found`. On a debug-capable build, `bridge.sh check` from the release runs the same checks and also looks for the shim.

Then in Lilac: Video mode, swipe down on the picture, and the **Slow-mo** switch is there.
