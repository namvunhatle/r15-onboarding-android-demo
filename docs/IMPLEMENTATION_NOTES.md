# Implementation notes

[Back to README](../README.md)

## Architecture

The engine (package `…onboarding.core`) produces the animated scene state; the Views renderer (package `…onboarding.views`) draws it. On `main` the engine is a separate `:core` library shared with a Compose app; on this branch it lives in `app-views`.

```text
Frame callback → A7Player → Timeline + A7Script → Store
                     ↑                            ↓
               AudioTrack clock               XML Views
```

Engine source files live in `app-views/src/main/java/namvunhatle/r15/onboarding/core/`.

| File | Responsibility |
| --- | --- |
| `Timeline.kt` | Tweens, property storage, repeat/yoyo, callbacks, pause points, and reset |
| `Ease.kt` | Easing curves used by the script |
| `A7Script.kt` | Scene order, beat map, transitions, and final-scene idle loop |
| `A7Player.kt` | Playback clock, ad pause, destinations, press feedback, replay, and time inspection |
| `A7Audio.kt` | WAV loading, audio focus, streaming playback, timestamps, and fades |
| `Scene.kt` | Design coordinates, transform origins, tap areas, and track metadata |
| `A7Art.kt` | Bitmap preparation for the logo blur, glows, and light trail |
| `Later.kt` | The start-up pool: bakes and decodes off the main thread, waited on at first draw |
| `Immersive.kt` | System-bar handling and the time-inspection launch argument |
| `A7Native.kt` | Builds each Figma element as a native drawable, by timeline id |
| `FigmaArt.kt` | The Figma components drawn in code: text, shapes, gradients, shadows |
| `A7Glow.kt` | Figma layer blurs on the glow ellipses, computed at startup |
| `FigmaPaths.kt` | Vector geometry copied from Figma |


The Views renderer uses [MainActivity.kt](../app-views/src/main/java/namvunhatle/r15/onboarding/views/MainActivity.kt), [Widgets.kt](../app-views/src/main/java/namvunhatle/r15/onboarding/views/Widgets.kt), and generated XML.

## Animation and audio

A single master timeline advances all scene elements. The UI reads transforms and opacity from the shared store and applies them on each frame callback.

After Skip ads, the player starts an `AudioTrack` stream: initial silence, the intro mix, then a repeating background loop. When timestamps are available, the audio clock guides the visual timeline. The still re-entry interval lets the visual clock wait for audio startup. During motion, the player limits timing correction to ±25% of the frame interval.

The track has two bundled files:

- `future-pop_intro.ogg` — music, accents, and volume changes through beat 32.
- `future-pop_loop.ogg` — background music for the final scene.

Both are **Ogg Opus, 48 kHz stereo, 96 kbps, 120 ms packets** (330 KB together). `A7Audio` decodes them with `MediaExtractor` + `MediaCodec` during the splash, in parallel; this takes about 0.2–0.3 s on the emulator. The long packets matter: with Opus's default 20 ms packets, the per-packet decoder round trips made decoding take seconds.

The decoder drops Opus's pre-skip; `A7Audio` then cuts the last packet's padding so each file is exactly `introFrames` / `loopFrames` long (`tracks.json`). The drop and the G04 loop are therefore sample-accurate. If decoding has not finished when Skip ads is tapped, the player waits at most 1 s, then continues without music.

The common clock is useful as a reference for keeping the sequence together. Production code can use a different animation system if it preserves the same timing relationships.

## Rendering details

- Blur and glow bitmaps are prepared during startup. Motion animates their transforms and opacity.
- **Start-up work is off the main thread.** `A7Art` and `A7Native` submit every bake and decode to a three-thread pool (`Later.kt`) before the layout inflates, splash pieces first and screenshots last. A view waits on a piece only when it first draws it, and hidden views are not drawn, so only the splash, logo and glow are waited on. A pool job may wait on an earlier job, never on a later one.
- Some elements extend beyond their layout bounds. The renderers account for this when drawing rings, shadows, and the call bubble.
- The scene uses a **360 × 800** coordinate space. Each renderer scales the frame uniformly to fit, centers it, and fills the rest of a phone screen around it. See [Screen sizes](#screen-sizes).
- Font scaling is fixed. Status bars and the camera cutout are simulated artwork; the actual system bars are hidden.

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
| Interstitial (`15560:138551`) | Creative = the frame's image fill (FILL, radius 24). SDK chrome in code: "Ads progress" lines and the "Skip ads" pill (Mona Sans Medium, close-circle path) |

**Still bitmaps:** the splash image fill and smoke photo, the three phone screens, the logo record, the interstitial creative, and the three destination screens. These are images in Figma too, or screens owned by other products.

**Effect calibration.** Figma drop shadows follow CSS: σ = blur / 2. For layer blur, the closest fit to Figma's own export was σ = 0.42 × radius, applied to the whole ellipse and then cut by the frame (mean error 0.8/255 over the four spotlight screens).

**Per-frame cost.** Every native element except the spotlights and the interstitial is drawn into its own GPU layer (`LAYER_TYPE_HARDWARE`). The layer is drawn once at device resolution. After that, the timeline only transforms and fades it, so each frame costs the same as drawing a sprite. The spotlights are a single bitmap draw and skip the layer; the interstitial is static while it shows.

**Differences from the sprite archive (intentional).**

- Text and edges are sharper. The sprite archive scales 2× exports up to the screen density; the current build draws at the device's resolution.
- The status bar is no longer squashed. The sprite archive stretches a 46 dp export into the 40 dp bar.
- The P01 tagline and disclaimer sit 0.5–1.5 dp above today's Figma text boxes, matching where the v1.3.3 sprites put them.
- The white of "RINGTONES" and "IS NEXT." is 96 %, and the other headline lines are solid white, as in the v1.3.3 export. Today's Figma file uses 96 % on every headline line.

**Validation.** Checked on an API 36 emulator at 1080 × 2400 against the v1.3.3 APKs, at 16 timeline points from 1.0 s to 19.2 s. Mean difference per screen: 0.9–2.0/255 in both apps. At most 2.3 % of pixels differ by more than 24/255, all on glyph and shape edges. Both paths were walked through (Skip ads → G04 → paywall → AI). Frame-phase timings on the emulator matched `main`. As on `main`, this is not a physical-device test.

**Pixel review tool.** Launch with `--ez dump true` to write every native element, at 2× its box, to `Android/data/<package>/files/dump/`. Compare those files against the `main` sprites of the same name.

```sh
adb shell am start -n namvunhatle.r15.onboarding.views.responsive/namvunhatle.r15.onboarding.views.MainActivity --ez dump true
adb pull /sdcard/Android/data/namvunhatle.r15.onboarding.views.responsive/files/dump
```

**Interstitial vs. the v1.3.3 screenshot.** Mean difference 1.1/255 at 1080 × 2400. The screenshot's corners were white (its rounded corners exported onto a JPEG); the native version is black behind the radius, like an SDK window.

**Changing the art.** Colours, type, radii and spacing are design tokens: change the variable in Figma, re-export and run `tools/gen_tokens.py` ([Design tokens](DESIGN_TOKENS.md)). Visual-only colours are in `A7Visual.kt`. Geometry is in `FigmaArt.kt` or `A7Native.kt`, in Figma's frame coordinates. If an element's footprint changes, update its box in `manifest.json` and rerun `tools/gen_layout.py`, as on `main`.

## Screen sizes

The 360 × 800 frame keeps its v1.3.3 scale (fit, never distorted), so on a 20:9 phone nothing changes. On other phones the leftover screen goes to the sides (shorter screens, 16:9 = 45 dp each side) or above and below (taller ones, 21:9 = 20 dp each). `Viewport.kt` measures it; `Scene` turns it into boxes; both renderers read those boxes.

| Element | What it does with the extra screen |
| --- | --- |
| Splash background, spotlights | Drawn over the whole screen: the image fill covers it, the black ramp spans it, the smoke keeps to the top, and the glows are baked at screen size |
| Genre wall | Tiles cut by the frame edge are drawn whole. Each row gets one more tile at either end, repeating the far end of the row, while it is on screen |
| Stickers, G01 phone | Grown about their centre until the shadow is whole, so their motion is unchanged |
| Status bar, wave, headlines | Hold the top edge. The status bar spans the width: time at the left, icons at the right |
| Splash progress, disclaimer, banner ad | Hold the bottom edge. The banner spans the width |
| Interstitial | Creative covers the screen (16:9 shows it uncropped). Progress spans the width, the Skip pill and its tap area hold the top-right corner |
| Destination screens | Screenshots of other products stay at the frame. Each margin continues the screenshot's edge colour (`Bleed.kt`) |

Margins are capped at 60 dp (about 16:9 to 23:9). Beyond that — tablets, landscape — the rest is letterboxed as before.

**Validation.** API 36 emulators at 1080 × 1920 (16:9), 1080 × 2400 (20:9) and 1080 × 2520 (21:9), 17 timeline points each. At 20:9 both apps are pixel-identical to `main` except the interstitial (above). At 16:9 and 21:9, Compose and Views match each other at 0.3–3.1/255 mean. Skip ads → CTA → paywall → AI was tapped through in both apps on all three screens. Not tested on physical devices, foldables or in multi-window.

## Lifecycle

Leaving the app during the introduction resets playback to the splash. The activity does not apply that reset while it is waiting on the mock ad or showing a destination.

The ad exception anticipates an SDK that presents a separate activity. There is no SDK integration here, so production ad callbacks and interruption behavior still need device testing.

## Inspect a timeline time

Force-stop the selected build, then launch it with the `t` argument. This makes sure a new activity reads the requested time.

```sh
adb shell am force-stop namvunhatle.r15.onboarding.views.responsive
adb shell am start -n namvunhatle.r15.onboarding.views.responsive/namvunhatle.r15.onboarding.views.MainActivity --ef t 12.4
```

Time inspection suppresses timeline callbacks and audio. It is a visual review tool, not a way to test the interactive ad flow.

**Known limitation:** at or after the final scene's idle-loop start (19.47 s at 100 BPM), the master timeline freezes but the idle loop can still move stickers and the primary action. Screenshots taken after waiting may differ.

## Change the layout

`app-views/src/main/assets/manifest.json` stores each element's box: the exported sprite bounds on the archive branch and the native art bounds on `main`. Additional geometry and tap areas are defined in `Scene.kt`.

The main Views layout is generated. After changing the relevant exported coordinates or generator rules, run from the repository root:

```sh
python3 tools/gen_layout.py
```

This overwrites `app-views/src/main/res/layout/activity_main.xml`. Other layouts and custom drawing code may also need corresponding edits.

## Rebuild the audio — optional

The Ogg files needed to build and run the app are already included. Audio baking is only needed when changing the mix.

The baker requires Node.js, Playwright, Chrome, and the original web prototype source directory containing `src/tracks.json` and the processed tracks in `public/audio/`. That web source is **not included in this repository**.

```sh
cd tools/bake
npm install
node bake.mjs /absolute/path/to/prototype-a7 /tmp/a7-wav
cd ../..
tools/encode_audio.sh /tmp/a7-wav future-pop
```

The baker writes WAV mixes; `encode_audio.sh` (needs ffmpeg with libopus) turns them into the shipped Ogg files and prints their frame counts. Copy those into `introFrames` / `loopFrames` in `tracks.json`. The `npm run bake` shortcut assumes the original sibling-folder layout.

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
./gradlew :app-views:lintDebug
```

Production work includes adaptive layout, accessibility semantics and font scaling, real system bars, final-resolution assets, ad integration, billing, and the actual destination screens.
