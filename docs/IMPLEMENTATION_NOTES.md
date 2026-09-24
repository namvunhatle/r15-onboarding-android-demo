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
| `A7Native.kt` | Builds each Figma element as a native drawable, by timeline id |
| `FigmaArt.kt` | The Figma components drawn in code: text, shapes, gradients, shadows |
| `A7Glow.kt` | Figma layer blurs on the glow ellipses, computed at startup |
| `FigmaPaths.kt` | Vector geometry copied from Figma |


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

## Native art (`main`)

On [`archive/v1.3.3-sprites`](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/archive/v1.3.3-sprites), most of the scene is PNG/JPG sprites exported from the Figma section `15552:115354`. On `main`, those elements are drawn by code from the Figma node's own properties, in the same boxes. The shared timeline therefore moves, scales and fades exactly the same rectangles.

| Element | How it is drawn now |
| --- | --- |
| Headlines (`g0*_head`, `g04_center`) | Anton text. Center-aligned echoes are stroked text. Outside-aligned echoes are the stroke outline minus the glyphs, computed once as a path |
| Tagline, disclaimer, buttons, banner ad, stickers, genre tiles | Be Vietnam Pro text with Figma letter spacing and line boxes. Rounded rectangles, gradients from the Figma `gradientTransform`, and drop shadows from Figma offset, blur and colour |
| Phone frames | Rounded body, inside stroke, drop shadow and punch-hole in code. The screen is an image fill in Figma and stays a bitmap |
| Spotlight backgrounds, splash glows | Figma `LAYER_BLUR` ellipses, blurred at startup into small bitmaps (`A7Glow`), because a live blur of this size costs a full-screen pass every frame |
| Splash background | Figma image fill (bitmap) + 52 % black ramp + tilted grid (vector union from `fillGeometry`) + glows + 40 % smoke photo (bitmap) |
| Status bar | Roboto text and three `VectorDrawable` icons copied from the Figma paths |

**Still bitmaps:** the splash image fill and smoke photo, the three phone screens, the logo record, the mock interstitial, and the three destination screens. These are images in Figma too, or screens owned by other products.

**Effect calibration.** Figma drop shadows follow CSS: σ = blur / 2. For layer blur, the closest fit to Figma's own export was σ = 0.42 × radius, applied to the whole ellipse and then cut by the frame (mean error 0.8/255 over the four spotlight screens).

**Per-frame cost.** Every native element except the spotlights is drawn into its own GPU layer: `CompositingStrategy.Offscreen` in Compose and `LAYER_TYPE_HARDWARE` in Views. The layer is drawn once at device resolution. After that, the timeline only transforms and fades it, so each frame costs the same as drawing a sprite. The spotlights are a single bitmap draw and skip the layer.

**Differences from the sprite archive (intentional).**

- Text and edges are sharper. The sprite archive scales 2× exports up to the screen density; the current build draws at the device's resolution.
- The status bar is no longer squashed. The sprite archive stretches a 46 dp export into the 40 dp bar.
- The P01 tagline and disclaimer sit 0.5–1.5 dp above today's Figma text boxes, matching where the v1.3.3 sprites put them.
- The white of "RINGTONES" and "IS NEXT." is 96 %, and the other headline lines are solid white, as in the v1.3.3 export. Today's Figma file uses 96 % on every headline line.

**Validation.** Checked on an API 36 emulator at 1080 × 2400 against the v1.3.3 APKs, at 16 timeline points from 1.0 s to 19.2 s. Mean difference per screen: 0.9–2.0/255 in both apps. At most 2.3 % of pixels differ by more than 24/255, all on glyph and shape edges. Both paths were walked through (Skip ads → G04 → paywall → AI). Frame-phase timings on the emulator matched `main`. As on `main`, this is not a physical-device test.

**Pixel review tool.** Launch with `--ez dump true` to write every native element, at 2× its box, to `Android/data/<package>/files/dump/`. Compare those files against the `main` sprites of the same name.

```sh
adb shell am start -n namvunhatle.r15.onboarding.compose.vector/namvunhatle.r15.onboarding.compose.MainActivity --ez dump true
adb pull /sdcard/Android/data/namvunhatle.r15.onboarding.compose.vector/files/dump
```

**Changing the art.** Edit the numbers in `FigmaArt.kt` or `A7Native.kt`; they are written in Figma's frame coordinates. If an element's footprint changes, update its box in `manifest.json` and rerun `tools/gen_layout.py`, as on `main`.

## Lifecycle

Leaving the app during the introduction resets playback to the splash. The activity does not apply that reset while it is waiting on the mock ad or showing a destination.

The ad exception anticipates an SDK that presents a separate activity. There is no SDK integration here, so production ad callbacks and interruption behavior still need device testing.

## Inspect a timeline time

Force-stop the selected build, then launch it with the `t` argument. This makes sure a new activity reads the requested time.

```sh
adb shell am force-stop namvunhatle.r15.onboarding.compose.vector
adb shell am start -n namvunhatle.r15.onboarding.compose.vector/namvunhatle.r15.onboarding.compose.MainActivity --ef t 12.4
```

For XML Views, use the package `namvunhatle.r15.onboarding.views.vector` and activity `namvunhatle.r15.onboarding.views.MainActivity`. Time inspection suppresses timeline callbacks and audio. It is a visual review tool, not a way to test the interactive ad flow.

**Known limitation:** at or after the final scene's idle-loop start (19.47 s at 100 BPM), the master timeline freezes but the idle loop can still move stickers and the primary action. Screenshots taken after waiting may differ.

## Change the layout

`core/src/main/assets/manifest.json` stores each element's box: the exported sprite bounds on the archive branch and the native art bounds on `main`. Additional geometry and tap areas are defined in `Scene.kt`.

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

Known issues retained in the **current native-art build**:

| Issue | Impact | Review workaround |
| --- | --- | --- |
| Fixed aspect ratio | Black bars on devices that differ from 20:9 | Use a 20:9 viewport to inspect the reference composition |
| Replay during destination entry | An unfinished transition can make the old destination visible again after reset | Wait until the transition finishes before replaying; relaunch if stuck |
| Time inspection runs the final idle loop | The final scene is not a stable still image | Account for idle movement when comparing final-scene screenshots |
| Partial reduced motion | Some splash rotation, scaling, and other movement remains | Do not treat this build as a completed reduced-motion implementation |

Physical-device frame pacing, speaker/headphone playback, Bluetooth latency, and a range of screen sizes have not been verified. The earlier emulator measurements used CPU rendering and do not establish production performance.

Preparation of the archived sprite release on 2026-09-23 rebuilt both debug apps from independent source and ran both app lint tasks successfully. Lint reported no errors; warnings remain for prototype choices such as fixed positioning, fixed text sizes, and portrait orientation.

To run the project's lint tasks:

```sh
./gradlew :app-compose:lintDebug :app-views:lintDebug
```

Production work includes adaptive layout, accessibility semantics and font scaling, real system bars, final-resolution assets, ad integration, billing, and the actual destination screens.
