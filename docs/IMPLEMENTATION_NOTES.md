# Implementation notes

[Back to README](../README.md)

## Architecture

Both apps read the same animated scene state. They differ in how they draw it.

```text
Frame callback → A7Player → Timeline + A7Script → Store
                     ↑                            ↓
               AudioTrack clock          Compose or XML Views
```

Core source files live in `core/src/main/java/namvunhatle/r15/onboarding/core/`.

| File | Responsibility |
| --- | --- |
| `Timeline.kt` | Tweens, property storage, repeat/yoyo, callbacks, pause points, and reset |
| `Ease.kt` | Easing curves used by the script |
| `A7Script.kt` | Scene order, beat map, transitions, and final-scene idle loop |
| `A7Player.kt` | Playback clock, ad pause, destinations, press feedback, replay, and time inspection |
| `A7Audio.kt` | WAV loading, audio focus, streaming playback, timestamps, and fades |
| `Scene.kt` | Design coordinates, transform origins, tap areas, and track metadata |
| `A7Art.kt` | Bitmap preparation for the logo blur, glows, and light trail |
| `Immersive.kt` | System-bar handling and the time-inspection launch argument |

The Compose renderer is [A7Screen.kt](../app-compose/src/main/java/namvunhatle/r15/onboarding/compose/A7Screen.kt). The Views renderer uses [MainActivity.kt](../app-views/src/main/java/namvunhatle/r15/onboarding/views/MainActivity.kt), [Widgets.kt](../app-views/src/main/java/namvunhatle/r15/onboarding/views/Widgets.kt), and generated XML.

## Animation and audio

A single master timeline advances all scene elements. The UI reads transforms and opacity from the shared store. Compose binds those values through `graphicsLayer`; Views applies them on each frame callback.

After Skip ads, the player starts an `AudioTrack` stream: initial silence, the intro mix, then a repeating background loop. When timestamps are available, the audio clock guides the visual timeline. The still re-entry interval lets the visual clock wait for audio startup. During motion, the player limits timing correction to ±25% of the frame interval.

Each track has two bundled files:

- `<id>_intro.wav` — music, accents, and volume changes through beat 32.
- `<id>_loop.wav` — background music for the final scene.

The player expects **44.1 kHz, stereo, 16-bit PCM WAV files with a 44-byte header**. Its reader is specific to the output of the supplied bake tool; it is not a general WAV decoder.

The common clock is useful as a reference for keeping the sequence together. Production code can use a different animation system if it preserves the same timing relationships.

## Rendering details

- Blur and glow bitmaps are prepared during startup. Motion animates their transforms and opacity.
- Some elements extend beyond their layout bounds. The renderers account for this when drawing rings, shadows, and the call bubble.
- Compose removes hidden overlay tap areas from composition. Setting alpha to zero alone would leave them able to intercept input.
- The scene uses a fixed **360 × 800** coordinate space. Each renderer scales the entire frame uniformly and centers it on a dark background.
- Font scaling is fixed. Status bars and the camera cutout are simulated artwork; the actual system bars are hidden.

This layout preserves the reference composition. It does **not** provide responsive layouts for different device aspect ratios. Wider or taller viewports show black bars.

## Lifecycle

Leaving the app during the introduction resets playback to the splash. The activity does not apply that reset while it is waiting on the mock ad or showing a destination.

The ad exception anticipates an SDK that presents a separate activity. There is no SDK integration here, so production ad callbacks and interruption behavior still need device testing.

## Inspect a timeline time

Force-stop the selected build, then launch it with the `t` argument. This makes sure a new activity reads the requested time.

```sh
adb shell am force-stop namvunhatle.r15.onboarding.compose
adb shell am start -n namvunhatle.r15.onboarding.compose/.MainActivity --ef t 12.4
```

For XML Views, replace `compose` with `views` in both commands. Time inspection suppresses timeline callbacks and audio. It is a visual review tool, not a way to test the interactive ad flow.

**Known limitation:** at or after the final scene's idle-loop start (19.47 s at 100 BPM), the master timeline freezes but the idle loop can still move stickers and the primary action. Screenshots taken after waiting may differ.

## Change the layout

`core/src/main/assets/manifest.json` stores exported sprite positions and bounds. Additional geometry and tap areas are defined in `Scene.kt`.

The main Views layout is generated. After changing the relevant exported coordinates or generator rules, run from the repository root:

```sh
python3 tools/gen_layout.py
```

This overwrites `app-views/src/main/res/layout/activity_main.xml`. Other layouts, custom drawing code, and Compose geometry may also need corresponding edits. Review both renderers after a layout change.

## Rebuild the audio — optional

The WAV files needed to build and run the app are already included. Audio baking is only needed when changing the mix.

The baker requires Node.js, Playwright, Chrome, and the original web prototype source directory containing `src/tracks.json` and the processed tracks in `public/audio/`. That web source is **not included in this repository**.

```sh
cd tools/bake
npm install
node bake.mjs /absolute/path/to/prototype-a7 ../../core/src/main/assets/audio
```

The existing `npm run bake` shortcut assumes the original sibling-folder layout. Use the explicit command above for a standalone checkout.

## Validation and known limitations

The original development notes record an API 36 emulator at 1080 × 2400, comparisons at 15 visual checkpoints against the web prototype, and walkthroughs of both destination paths. These are reported manual checks, not an automated regression suite.

A separate code review exercised the shared Kotlin timeline/player with Android and audio stubs. At nine sampled times, direct seeking and stepped playback had no state differences above the test's 0.02 threshold. That check does not verify rendering, sound, or device performance.

Known issues carried into **v1.3.3**:

| Issue | Impact | Review workaround |
| --- | --- | --- |
| Fixed aspect ratio | Black bars on devices that differ from 20:9 | Use a 20:9 viewport to inspect the reference composition |
| Replay during destination entry | An unfinished transition can make the old destination visible again after reset | Wait until the transition finishes before replaying; relaunch if stuck |
| Time inspection runs the final idle loop | The final scene is not a stable still image | Account for idle movement when comparing final-scene screenshots |
| Partial reduced motion | Some splash rotation, scaling, and other movement remains | Do not treat this build as a completed reduced-motion implementation |

Physical-device frame pacing, speaker/headphone playback, Bluetooth latency, and a range of screen sizes have not been verified. The earlier emulator measurements used CPU rendering and do not establish production performance.

Release preparation on 2026-09-23 rebuilt both debug apps from an independent copy of this repository and ran both app lint tasks successfully. Lint reported no errors; warnings remain for prototype choices such as fixed positioning, fixed text sizes, and portrait orientation.

To run the project's lint tasks:

```sh
./gradlew :app-compose:lintDebug :app-views:lintDebug
```

Production work includes adaptive layout, accessibility semantics and font scaling, real system bars, final-resolution assets, ad integration, billing, and the actual destination screens.
