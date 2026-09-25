# R15 Onboarding — Android Motion Prototype

An interactive demo of the R15 onboarding sequence: motion, audio cues, ad placement, and the paths into AI Ringtones and the ringtone catalog.

Built for the Android development team from the React/Vite web prototype **v1.3.3**. The repository contains two native implementations: **Jetpack Compose** and **XML Views**. Both use the same timeline and assets.

Use it to review the experience and discuss implementation. Ads, purchases, AI generation, and destination screens are mocked.

**Native art.** Figma shapes, text, gradients, shadows, and blurs are drawn in code, following the v1.3.3 motion, audio, and 360 × 800 composition. Only Figma image fills and the destination mocks remain bitmaps. See [Native art](docs/IMPLEMENTATION_NOTES.md#native-art-main).

**Any phone screen.** The scene fills 16:9 to 23:9 phones instead of showing black bars: backgrounds bleed, the genre wall grows, and chrome holds the screen edges. See [Screen sizes](docs/IMPLEMENTATION_NOTES.md#screen-sizes).

**Developers:** start with [`FigmaArt.kt`](core/src/main/java/namvunhatle/r15/onboarding/core/FigmaArt.kt), where each Figma component is drawn with its Figma values.

## Design tokens

The A7 screens in Figma are built with the **ZEN design system**: 154 variables are bound across the frames, from ZEN plus the ad SDK kit used by the interstitial. The code uses each of those variables by name, not a copy of its value. When a variable changes in Figma, one regeneration updates both apps.

**Naming.** A token keeps its Figma path:

| In Figma | Compose and native drawing | XML layouts |
| --- | --- | --- |
| `Color/Background/Accent/Solid/Default` | `Zen.Color.Background.Accent.Solid.Default` (ARGB `Int`) | `@color/zen_color_background_accent_solid_default` |
| `Corner-Radius/Large` | `Zen.CornerRadius.Large` (dp) | `@dimen/zen_corner_radius_large` |
| Text style Heading-4: size, line height, letter spacing | `ZenText.Heading4` | `@dimen/zen_typography_font_size_heading_4`, `…line_height_heading_4` |
| Ad SDK kit `background/bg-overlay` | `AdKit.Background.BgOverlay` | `@color/adkit_background_bg_overlay` |

```kotlin
Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Zen.ButtonPrimary.Background.Default } // native drawing
Type(semiBold, ZenText.Heading4)                                                   // a ZEN text style
Color(Zen.Color.Content.OnDarkOverlay.Strongest)                                    // Compose
```

```xml
android:textColor="@color/zen_color_content_on_dark_overlay_base"
android:textSize="@dimen/zen_typography_font_size_heading_4"
```

The values are those of the ZEN modes the design locks: **Light · Global - Base 14 · Brand Emphasis - S1 · Base Colors Zen**. Letter-spacing tokens are Figma pixels; divide by the font size to get Android's `em` units.

**Where they come from.**

1. `tools/tokens/export_tokens.js` reads the variables bound in the A7 frames from the Figma file. It is read-only and writes nothing to Figma.
2. `tools/tokens/a7_figma_tokens.json` is the committed snapshot: each variable, its collection, and its resolved value.
3. `python3 tools/gen_tokens.py` generates `ZenTokens.kt` and `zen_tokens.xml`. Never edit those two files by hand.

**What is not a token.** Some values have no Figma variable behind them. They are drawn literally and live only in `A7Visual.kt` and its XML copy, `a7_visual.xml`; no other file contains a raw colour.

- Splash background art: the grid gradient, the purple glows, the black ramp, the smoke.
- Spotlight glow sizes, opacities and blurs. The glow colours themselves are tokens.
- Gradient angles, drop shadows, the phone body, the ring-burst flash and ring opacities.
- The interstitial's progress bar, the native-ad mock tints, and the demo's own chrome.

A second group in the same file keeps values that Figma now binds to a token but this demo draws differently: solid-white headlines, the opaque lyric card, the bubble's type size and a few others. Each entry names the token that production should use instead.

The full token map, the visual-only table and the regeneration steps are in [Design tokens](docs/DESIGN_TOKENS.md).

## Try it

- **[Open the web demo](https://prototype-a7.vercel.app)** to watch the sequence in a browser. The live site may change after this release.
- **[Download version 1.3.6](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.6)**. Requires Android 9 / API 28 or later.
- **[Build from source](#build-locally)** if you want to inspect or change the implementation.

| Build | APK | Application ID |
| --- | --- | --- |
| Compose | [A7-Compose-1.3.6.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.6/A7-Compose-1.3.6.apk) | `namvunhatle.r15.onboarding.compose.responsive` |
| XML Views | [A7-XMLViews-1.3.6.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.6/A7-XMLViews-1.3.6.apk) | `namvunhatle.r15.onboarding.views.responsive` |

Both APKs are debug builds for review. If Android asks, allow installation from the app used to open the download.

## What to look for

1. The splash logo expands and dissolves into a mock interstitial.
2. Tap **Skip ads**. The logo returns briefly, then the ring burst opens the introduction.
3. Watch the catalog scene, the **Same song. Any name.** sequence, and the incoming-call example.
4. At **Yours is next.**, choose a path:
   - **Explore AI Ringtones** → paywall mock → AI screen mock.
   - **Browse ringtones** → Home screen mock.

Closing the paywall or tapping its subscribe area opens the AI mock. No purchase is made. Two separate native-ad placeholders appear during the introduction.

See [Experience](docs/EXPERIENCE.md) for the scene sequence and timing.

## Review controls

| Action | Result |
| --- | --- |
| Tap the circular replay button | Restart from the splash |
| Hold the replay button | Cycle through three music tracks and a music-off option; restart |
| Open with a timeline time | Inspect a scene without playing its audio; see [instructions](docs/IMPLEMENTATION_NOTES.md#inspect-a-timeline-time) |
| Open with `--ez dump true` | Save each native element as a PNG, for comparison with Figma; see [Native art](docs/IMPLEMENTATION_NOTES.md#native-art-main) |

The replay button appears on the interstitial and destination screens. Track switching is a review control, not part of the proposed onboarding.

## Read the code

| Folder | Purpose |
| --- | --- |
| `core/` | Timeline, scene script, playback state, audio, geometry, and shared assets |
| `core/…/FigmaArt.kt` | **Start here for Figma → code:** each Figma component drawn natively, with its Figma values |
| `core/…/A7Native.kt` | Which element uses which component, and what stays a bitmap |
| `core/…/A7Glow.kt` | Figma layer blurs, computed at startup |
| `core/…/Viewport.kt`, `Scene.kt` | Screen size → which elements bleed, grow or hold an edge |
| `core/…/ZenTokens.kt`, `res/values/zen_tokens.xml` | Figma variables as code, generated by `tools/gen_tokens.py` — do not edit |
| `core/…/A7Visual.kt`, `res/values/a7_visual.xml` | The only raw colours: values Figma draws without a variable, and v1.3.3 values kept over a token |
| `app-compose/` | Compose renderer and activity |
| `app-views/` | XML layouts, custom views, and activity |
| `tools/` | Layout generation and optional audio baking |

- [Experience](docs/EXPERIENCE.md) — what each scene demonstrates.
- [Implementation notes](docs/IMPLEMENTATION_NOTES.md) — where to make changes and what needs production work.
- [Design tokens](docs/DESIGN_TOKENS.md) — what uses which token, and every visual-only value with its Figma node.
- [Changelog](CHANGELOG.md) — what changed in each version.
- [Credits](docs/CREDITS.md) — music, fonts, icons, and asset sources.

## Known limits

- **Phones only.** Screens from about 16:9 to 23:9 are filled; tablets and landscape are still letterboxed. Destination screens are screenshots, so their margins show edge colour, not layout.
- **Replay during a transition can leave an old destination visible.** Wait for the destination to settle before replaying. Relaunch the app if it occurs.
- **Time inspection does not fully freeze the final scene.** Its idle animation can keep running.
- **Reduced motion is partial.** Some splash movement remains when system animations are disabled.
- Font scaling is fixed. Status bars and the camera cutout are drawn into the demo; destination screens are screenshots with tap areas.
- **Launch does about 0.3 s of extra work** on the emulator: fonts load and glows are blurred at startup. The 5 s splash covers it.
- Smoothness, audio timing, and the GPU memory of the cached element layers have not been measured on physical devices.

These limitations apply to both Compose and XML Views in this version. See [Validation and known limitations](docs/IMPLEMENTATION_NOTES.md#validation-and-known-limitations) for the review scope.

## Build locally

Open this repository in Android Studio, configure **JDK 17**, and install **Android SDK Platform 37**. Let Android Studio create `local.properties`, or set `ANDROID_HOME` to your SDK directory.

```sh
./gradlew :app-compose:assembleDebug :app-views:assembleDebug
```

Install either build on a connected device:

```sh
adb install -r app-compose/build/outputs/apk/debug/app-compose-debug.apk
adb install -r app-views/build/outputs/apk/debug/app-views-debug.apk
```

These commands build version 1.3.6. The project pins Gradle 9.7.1, AGP 9.4.1, Kotlin Compose compiler 2.4.20, and Compose BOM 2026.09.00. The Gradle wrapper is included; the first build needs network access to download dependencies. Windows users can run `gradlew.bat`.
