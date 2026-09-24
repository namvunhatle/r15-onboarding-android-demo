# R15 Onboarding — Android Motion Prototype

An interactive demo of the R15 onboarding sequence: motion, audio cues, ad placement, and the paths into AI Ringtones and the ringtone catalog.

Built for the Android development team from the React/Vite web prototype **v1.3.3**. The repository contains two native implementations: **Jetpack Compose** and **XML Views**. Both use the same timeline and assets.

Use it to review the experience and discuss implementation. Ads, purchases, AI generation, and destination screens are mocked.

**Native art.** Figma shapes, text, gradients, shadows, and blurs are drawn in code, following the v1.3.3 motion, audio, and fixed 360 × 800 layout. Only Figma image fills and the destination mocks remain bitmaps. See [Native art](docs/IMPLEMENTATION_NOTES.md#native-art-main).

**Any phone screen** (branch `responsive-phone`). The scene fills 16:9 to 23:9 phones instead of showing black bars: backgrounds bleed, the genre wall grows, and chrome holds the screen edges. See [Screen sizes](docs/IMPLEMENTATION_NOTES.md#screen-sizes-responsive-phone).

**Developers:** start with [`FigmaArt.kt`](core/src/main/java/namvunhatle/r15/onboarding/core/FigmaArt.kt), where each Figma component is drawn with its Figma values.

## Try it

- **[Open the web demo](https://prototype-a7.vercel.app)** to watch the sequence in a browser. The live site may change after this release.
- **[Download the responsive version 1.3.3-R](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.3-R)** — this branch. It installs beside the native-art release.
- **[Download the current native-art version](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.3-native)**. Requires Android 9 / API 28 or later.
- **[Build from source](#build-locally)** if you want to inspect or change the implementation.

| Build | APK | Application ID |
| --- | --- | --- |
| Compose | [A7-Compose-1.3.3-native.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.3-native/A7-Compose-1.3.3-native.apk) | `namvunhatle.r15.onboarding.compose.vector` |
| XML Views | [A7-XMLViews-1.3.3-native.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.3-native/A7-XMLViews-1.3.3-native.apk) | `namvunhatle.r15.onboarding.views.vector` |
| Compose · responsive | [A7-Compose-1.3.3-R.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.3-R/A7-Compose-1.3.3-R.apk) | `namvunhatle.r15.onboarding.compose.responsive` |
| XML Views · responsive | [A7-XMLViews-1.3.3-R.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.3-R/A7-XMLViews-1.3.3-R.apk) | `namvunhatle.r15.onboarding.views.responsive` |

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
| `app-compose/` | Compose renderer and activity |
| `app-views/` | XML layouts, custom views, and activity |
| `tools/` | Layout generation and optional audio baking |

- [Experience](docs/EXPERIENCE.md) — what each scene demonstrates.
- [Implementation notes](docs/IMPLEMENTATION_NOTES.md) — where to make changes and what needs production work.
- [Credits](docs/CREDITS.md) — music, fonts, icons, and asset sources.

The earlier sprite implementation is preserved on [`archive/v1.3.3-sprites`](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/archive/v1.3.3-sprites).

## Known limits

- **Phones only.** Screens from about 16:9 to 23:9 are filled; tablets and landscape are still letterboxed. Destination screens are screenshots, so their margins show edge colour, not layout.
- **Replay during a transition can leave an old destination visible.** Wait for the destination to settle before replaying. Relaunch the app if it occurs.
- **Time inspection does not fully freeze the final scene.** Its idle animation can keep running.
- **Reduced motion is partial.** Some splash movement remains when system animations are disabled.
- Font scaling is fixed. Status bars and the camera cutout are drawn into the demo; destination screens are screenshots with tap areas.
- **Startup is about 0.3 s slower than the sprite build** (emulator), because fonts load and glows are blurred at launch. The 5 s splash covers it.
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

These commands build the current native-art version. The project pins Gradle 9.7.1, AGP 9.4.1, Kotlin Compose compiler 2.4.20, and Compose BOM 2026.09.00. The Gradle wrapper is included; the first build needs network access to download dependencies. Windows users can run `gradlew.bat`.
