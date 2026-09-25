# 6 · Performance

[Integration guide](README.md) · previous: [Rules that must not change](05-rules.md) · next: [Before release](07-before-release.md)

Result: launch stays under half a second, and the trailer only moves layers.

## Launch time

Cold start, time to first frame (`adb shell am start -W -S`, `TotalTime`):

| Build | Pixel 5 (Android 14) | Emulator (Android 16, Apple M1 host) |
| --- | --- | --- |
| 1.3.6, debug | not measured | 1.57 s |
| 1.3.7, R8 release | **0.46 s** | 0.31 s |

Most of that gain comes from the build type alone: on the emulator, the same code opened in about 1.4 s as a debug build and in 0.43 s as a release build. **Measure only release builds**; debug numbers say nothing about production.

## What runs at start

| Work | Thread | When it is needed |
| --- | --- | --- |
| Read `manifest.json`, `tracks.json`; build the timeline | Main (`Scene`, `A7Player`) | First frame |
| Inflate the layout, bind views to the timeline | Main | First frame |
| Splash pieces: background (image decode + glow bake), disclaimer, banner, logo and its glow, tagline | Start-up pool (`Later`). Each view's first draw waits for its piece. | 0–0.35 s |
| Six spotlight backgrounds, headlines, tiles, stickers, phones, buttons, blurred logo, light trail | Start-up pool | From 4.4 s; ready long before |
| Ogg Opus decode of the music | `A7Audio`'s own thread, during the splash (0.2–0.35 s on the emulator) | At "Skip ads"; `skipAd()` waits up to 1 s |

`Later.kt` is the start-up pool: three daemon threads. `A7Art` and `A7Native` submit every bake before `setContentView`, splash pieces first. XML Views never draws a hidden view, so the first frame waits only for what the splash shows.

## Rules

- **Never call `Later.value` in `onCreate`**: it blocks the main thread until that bake finishes. Read it where it is first drawn (`LaterDrawable`, `BakedView`).
- **A pool job may wait only on a `Later` submitted before it.** The pool runs jobs in order, so waiting on a later job can deadlock the pool.
- **Keep the hardware layers** on the elements in `A7Native.LAYERED`: each is drawn once, then the timeline only moves, scales and fades the layer. Without them, text, paths and shadows redraw every frame.
- **Per frame, only transforms and alpha change** (`apply()`). Do not invalidate the art or change a view's size during the trailer.
- The pool's threads stay alive, idle, after the onboarding. They hold no bitmaps: every bitmap goes with the activity.

## Not measured yet

- Frame pacing on physical devices. The emulator renders on the CPU, so its frame times do not transfer.
- GPU memory of the cached layers on a physical device.
