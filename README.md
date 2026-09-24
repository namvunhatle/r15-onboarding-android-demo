# R15 Onboarding — Android Motion Prototype

An interactive demo of the R15 onboarding sequence: motion, audio cues, ad placement, and the paths into AI Ringtones and the ringtone catalog.

Built for the Android development team from the React/Vite web prototype **v1.3.3**. The repository contains two native implementations: **Jetpack Compose** and **XML Views**. Both use the same timeline and assets.

Use it to review the experience and discuss implementation. Ads, purchases, AI generation, and destination screens are mocked.

> **Branch `native-vector`.** The Figma artwork is drawn natively instead of shipped as exported images: vector paths, real text, gradients, and effects built from the Figma node properties. The motion, audio, and layout are unchanged from v1.3.3, and screenshots match `main` at every checked timeline point. Bitmaps remain only where Figma itself uses an image. See [Native art](docs/IMPLEMENTATION_NOTES.md#native-art-branch-native-vector).
>
> This branch builds as `namvunhatle.r15.onboarding.compose.vector` and `namvunhatle.r15.onboarding.views.vector` ("A7 · … · Native"), so it installs next to the v1.3.3 apps for side-by-side comparison. It has no GitHub release; build it locally.

## Try it

- **[Open the web demo](https://prototype-a7.vercel.app)** to watch the sequence in a browser. The live site may change after this release.
- **[Download Android v1.3.3](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.3)** to try it on a device. Requires Android 9 / API 28 or later.

| Build | APK | Application ID |
| --- | --- | --- |
| Compose | [A7-Compose-1.3.3.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.3/A7-Compose-1.3.3.apk) | `namvunhatle.r15.onboarding.compose` |
| XML Views | [A7-XMLViews-1.3.3.apk](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/download/v1.3.3/A7-XMLViews-1.3.3.apk) | `namvunhatle.r15.onboarding.views` |

These are debug APKs for review. You can install both on the same device. If Android asks, allow installation from the app used to open the download.

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

The replay button appears on the interstitial and destination screens. Track switching is a review control, not part of the proposed onboarding.

## Limits of this release

- **Fixed 360 × 800 layout.** The whole scene scales to fit. Other aspect ratios show black bars; responsive layouts are not implemented.
- **Replay during a transition can leave an old destination visible.** Wait for the destination to settle before replaying. Relaunch the app if it occurs.
- **Time inspection does not fully freeze the final scene.** Its idle animation can keep running.
- **Reduced motion is partial.** Some splash movement remains when system animations are disabled.
- Font scaling is fixed. Status bars and the camera cutout are drawn into the demo; destination screens are screenshots with tap areas.
- Smoothness and audio timing have not been verified on physical devices.

These limitations apply to both implementations. See [Validation and known limitations](docs/IMPLEMENTATION_NOTES.md#validation-and-known-limitations) for the review scope.

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

The project pins Gradle 9.7.1, AGP 9.4.1, Kotlin Compose compiler 2.4.20, and Compose BOM 2026.09.00. The Gradle wrapper is included; the first build needs network access to download dependencies. Windows users can run `gradlew.bat`.

## Read the code

| Folder | Purpose |
| --- | --- |
| `core/` | Timeline, scene script, playback state, audio, geometry, shared assets, and the native Figma art |
| `app-compose/` | Compose renderer and activity |
| `app-views/` | XML layouts, custom views, and activity |
| `tools/` | Layout generation and optional audio baking |

- [Experience](docs/EXPERIENCE.md) — what each scene demonstrates.
- [Implementation notes](docs/IMPLEMENTATION_NOTES.md) — where to make changes and what needs production work.
- [Credits](docs/CREDITS.md) — music, fonts, icons, and asset sources.
