# Lilac Camera

Lilac Camera is a fork of [PhotonCamera](https://github.com/bjzhou/PhotonCamera) by bjzhou, adapted for low-memory phones.

## What it is

A camera app that takes several frames for each photo and merges them. Merging gives less noise in dim light and keeps detail in bright skies.

It is built and tested on one phone: the Sony Xperia XZ1 Compact (codename lilac). The phone has 4 GB of memory, so Lilac keeps the heavy work small and does it in the background.

## Which phone and ROM

- **Phone:** Xperia XZ1 Compact (lilac). Other phones are not tested.
- **ROM:** a lilac ROM that ships the raw19 shim. The shim gives apps the camera's RAW frames, which Pro mode needs. Without it, Photo mode still works and Pro does not appear on the mode dial.
- **Android:** 11 or newer.

## How to get it

- **Stable:** comes inside lilac ROMs that include it. It updates with the ROM.
- **Beta:** published as GitHub releases of this project. It installs beside the ROM's copy under its own name, so both can be on the phone.

!!! note
    Lilac covers the features this fork changed. The colour tools and other features it shares with PhotonCamera work as before and are not covered here.

## Guide

**Using Lilac**

- [Getting started](user/getting-started.md): the camera screen, modes, quick settings, manual controls and symbols
- [Photo and Night](user/photo-mode.md): how stacking works, how many frames, 12 MP and 2 MP, front camera colour
- [Pro](user/pro-mode.md): RAW, HDR+ frames, the render queue, Photo or Pro
- [HDR](user/hdr.md): the darker burst and the HDR ratio
- [Portrait](user/portrait.md): the live guide, the blur after the shot, Quick or Depth
- [Video](user/video.md): stabilisation, slow motion, overheating
- [Editor](user/editor.md): editing photos and developing Pro photos again

**How it works**

- [Focus and faces](user/focus.md): tap focus, the camera key, dim light, the two face detectors
- [Sony hardware](user/hardware.md): what Lilac uses from the camera, and vendor keys

[Troubleshooting](user/troubleshooting.md): every message, what it means and what to do.
