<p align="center">
  <img src="docs/media/icon-256.png" width="128" alt="HorizonCraft VR">
</p>

<h1 align="center">HorizonCraft VR</h1>

<p align="center">
  Standalone VR for the <b>Meta Quest 1</b> and newer Quests, on Horizon: Minecraft: Java Edition 26.3 rewritten in Rust
</p>

<p align="center">
  <a href="https://github.com/Bubbagum88/HorizonCraft-VR/releases"><img src="https://img.shields.io/github/v/release/Bubbagum88/HorizonCraft-VR?include_prereleases&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Meta%20Quest-1%20%7C%202%20%7C%20Pro%20%7C%203%20%7C%203S-1C1E20" alt="Meta Quest 1, 2, Pro, 3 and 3S">
  <img src="https://img.shields.io/badge/Minecraft-Java%2026.3-5D9C3F" alt="Minecraft Java 26.3">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="License MIT"></a>
</p>

> **Unofficial fan project.** Not affiliated with, endorsed by or connected to Mojang, Microsoft or Meta.
> You need to **own Minecraft: Java Edition**. This repository and its releases contain **no Mojang files**: the app
> downloads them from Mojang on the headset.

HorizonCraft VR is a standalone APK for Meta Quest headsets: no PC, no streaming.
It plays Minecraft: Java Edition 26.3 in VR, with motion controls in the spirit of Vivecraft and QuestCraft.

**This fork** of [Boboetto/HorizonCraft-VR](https://github.com/Boboetto/HorizonCraft-VR) makes it run on the
**original Quest 1** (Snapdragon 835, Adreno 540), and ships a second APK for the Quest 2, Pro, 3 and 3S with the
same changes:

- **Runs on the Adreno 540:** the Quest 1's GPU driver mishandles shaders that ask a storage buffer's length; those
  shaders are rewritten so the world loads instead of crashing.
- **Opens in VR every time,** the first launch included (it used to land flat on the panel's display).
- **Two APKs:** the Quest 1's VR runtime aborts on device names it doesn't know, so its APK names only `quest|quest2`;
  the other names `quest2|questpro|quest3|quest3s`.
- **Lighter rendering:** 1.0x resolution without MSAA as a choice, the vignette drawn inside the hand pass (one
  full-screen pass fewer per eye), and the wrist hotbar kept on the arm on frames the headset repeats.
- **Facing follows the head, aiming follows the right hand:** name tags, sounds and the head other players see follow
  your head; mining, placing and hitting go where the right controller points.
- **Crouch to the floor:** the view follows a real crouch down 1.1 m.
- **Comfort options:** snap turning (30, 45 or 90 degrees) and teleport movement.
- **A QuestCraft controls layout** to swap to (below).
- **The recipe book** sits beside the crafting menu instead of over it.
- **The setup screen** says which step it is on, the time left for downloads, and what it is preparing.

## Features

- Stereo VR with head tracking; both arms follow the Touch controllers, items in the hands
- Hotbar on the left forearm: hover it with the right controller and pull the trigger to pick a slot
- A crosshair for each hand, lying on the face of the block it aims at
- The left trigger uses and places along the left hand while the right trigger attacks along the right one
- Gestures: swing a sword at a mob, strike blocks with a tool to mine, bring food to the mouth to eat,
  breaststroke to swim, or sprint in water to swim where you look, two-handed bow
- Menus on a panel in the room with a pointer for each hand, 360° title panorama
- Walk where you look with the left stick, turn with the right one (smooth or snap), or teleport
- A Quality setup tuned for the Quest 3 (MSAA 4x antialiasing, 90 Hz, 6 chunks, anisotropic filtering), with a resolution step and a performance boost in the launcher, plus frame pacing against micro-stutter
- Hands, tools and held items stop at blocks; a tool strikes with its tip
- Chat on the panel in the room, typed with the Quest's own keyboard
- In-app updates: the launcher shows an **Update** button when a new release is out (in this fork it still checks
  the upstream releases, whose APKs can't install over this fork's: don't use it for now)

## Controls

The launcher's **Controls** button picks a layout: **Horizon** (the default, this table) or **QuestCraft** (the next one).

| Input | In play | In a menu |
|---|---|---|
| Left stick | walk (where you look) | – |
| Right stick | turn | scroll |
| Right trigger | attack / mine / pick a wrist slot | click with the right pointer |
| Left trigger | use / place along the left hand | click with the left pointer |
| A | jump | confirm |
| B | chat (B again closes it) | back; in an inventory or container: quick move (craft all, send the stack to the other side) |
| Y | inventory | close the inventory |
| X | drop one item | drop one item (carried stack or slot) |
| X held + Y | drop the rest of the stack | drop the rest of the stack |
| Left grip | swap the item with the off hand | – |
| Both grips | debug screen (F3) | – |
| Right grip | – | place one item of the carried stack; with the trigger: quick move |
| Left grip (in a container) | – | take half of the stack |
| Both grips (in a container) | – | move every stack of that item to the other side |
| Left / right stick click | sprint / sneak | – |
| Menu button | pause | – |

### QuestCraft layout

| Input | In play | In a menu |
|---|---|---|
| Left stick | walk (where you look) | – |
| Right stick left / right | turn (snap if Turning is set to snap) | scroll |
| Right stick up / down | next / previous hotbar slot | scroll |
| Right trigger | attack / mine | click with the right pointer |
| Left trigger | use / place | click with the left pointer |
| Right grip (hold) | sprint | place one item of the carried stack |
| Left grip (hold) | sneak | take half of the stack (in a container) |
| A | jump | confirm |
| B | – | place one item of the carried stack (right click) |
| X | pause | same as the Menu button |
| Y | inventory | close the inventory |
| Menu button | pause | – |

Chat and dropping items have no button in this layout yet.

With **Movement: Teleport**, push the left stick ahead to aim along the left controller at a block top up to 8
blocks away, and let go to move there. In water or on a ladder the stick walks.

## Install

1. Turn on **developer mode** for your headset: in the **Meta Horizon** app on your phone → Menu → Devices →
   your headset → Headset settings → Developer mode (Meta asks you to create a free developer account once).
2. Download the APK for your headset from the [releases](https://github.com/Bubbagum88/HorizonCraft-VR/releases):
   - **Quest 1:** `HorizonCraft-VR-quest1-v*.apk`
   - **Quest 2, Pro, 3 and 3S:** `HorizonCraft-VR-quest3-v*.apk`

   Already have upstream HorizonCraft VR? This fork's APKs are signed with a different key, so uninstall it first
   (that deletes its worlds on the headset).
3. Install it with **SideQuest**: connect the headset to the computer with its USB-C cable, accept "Allow USB debugging"
   in the headset, then drag the APK onto the SideQuest window.
   (Or drag it onto **Meta Quest Developer Hub**.)
4. In the headset, open **App Library → Unknown sources → HorizonCraft VR**.

## First start

Press **Download game files** and wait a few minutes, on Wi-Fi:

1. the headset downloads the official Minecraft: Java Edition 26.3 files from Mojang (about 700 MB);
2. HorizonCraft VR prepares its game data on the headset itself, with a Java runtime built into the app (about a minute);
3. the game starts. Next time it opens straight away.

Later updates: install the next APK from this fork's releases over the old one. Your worlds stay.

The launcher's settings also pick **Resolution**, **Antialiasing MSAA**, **Turning** (smooth or snap 30/45/90),
**Movement** (walk or teleport) and **Controls** (Horizon or QuestCraft).

No computer needed after installing. You need to own Minecraft: Java Edition.

## In italiano

1. **Installa:** attiva la modalità sviluppatore (app Meta Horizon sul telefono → Dispositivi → visore →
   Impostazioni → Modalità sviluppatore) e installa l'APK dell'ultima release trascinandolo su **SideQuest**.
2. **Apri** HorizonCraft VR da **Libreria → Origini sconosciute**.
3. **Premi "Download game files"** e aspetta qualche minuto col Wi-Fi: il visore scarica i file ufficiali da Mojang,
   prepara da solo i dati di gioco e avvia HorizonCraft VR. Nessun PC necessario. Serve possedere Minecraft: Java Edition.

## Building

```sh
OPENXR_LOADER=/path/to/libopenxr_loader.so ANDROID_HOME=~/android/sdk ./packaging/android/build_apk.sh
```

`libopenxr_loader.so` is the Khronos OpenXR loader for Android (`org.khronos.openxr:openxr_loader_for_android`,
Apache 2.0). For the first start to prepare the game data on the headset, set `HORIZON_APK_ASSETS` to a folder with
`horizon-jre/` (a Java 25 runtime for Android arm64, plus the NDK's `libc++_shared.so` in its `lib/`) and
`horizon-prepare/` (`research-lock.json`, `mods/horizon/src/test/java/dev/horizonlab/parity/` and the compiled
`exporters/` from `horizon-prepare compile-exporters`). The VR code is in `crates/horizon-xr`, the renderer's `render_xr` and
`crates/horizon-client/src/game/android_input.rs`.

This fork's CI builds the native library (`.github/workflows/android-lib.yml`) and the Java base APKs
(`.github/workflows/android-java.yml`: `base.apk` for the Quest 1, `base-quest3.apk` for the newer headsets),
which are combined with the library and signed into the release APKs.

## Credits

- **Horizon**, Minecraft: Java Edition rewritten in Rust, by its authors (MIT)
- **Horizon for Android** by [Rayan-XOO](https://github.com/Rayan-XOO/horizon) (MIT)
- **HorizonCraft VR** by [Boboetto](https://github.com/Boboetto/HorizonCraft-VR) (MIT), which this fork builds on
- The Khronos OpenXR loader (Apache 2.0)
- The Java 25 runtime for Android from [AngelAuraMC/angelauramc-openjdk-build](https://github.com/AngelAuraMC/angelauramc-openjdk-build)
  (OpenJDK, GPLv2 with the Classpath Exception; source: [openjdk/jdk](https://github.com/openjdk/jdk)), its licences in the APK's `assets/horizon-jre/legal`
- Inspired by Vivecraft and QuestCraft

Minecraft is a trademark of Mojang Synergies AB. Meta Quest is a trademark of Meta Platforms, Inc.
