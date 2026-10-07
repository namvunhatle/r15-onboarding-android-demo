# v1.3.3-native — Android onboarding demo

Native-art version of the R15 onboarding motion prototype. This release is built from the `main` source at this tag. It keeps the v1.3.3 animation timeline, audio and fixed 360 × 800 layout while drawing Figma text, shapes, gradients and many effects in code.

## Downloads

| APK | Renderer | Application ID |
| --- | --- | --- |
| **A7-Compose-1.3.3-native.apk** | Jetpack Compose | `namvunhatle.r15.onboarding.compose.vector` |
| **A7-XMLViews-1.3.3-native.apk** | XML Views | `namvunhatle.r15.onboarding.views.vector` |

Both are debug APKs for review, require Android 9 / API 28 or later, and install alongside the [archived sprite builds](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.3). `SHA256SUMS.txt` lists the APK checksums; `THIRD_PARTY_NOTICES.txt` accompanies their bundled third-party assets.

## What changed

- Headlines, labels, buttons, stickers, genre tiles, phone frames and status-bar details are drawn from Figma geometry and typography in native code.
- Blurred spotlights and glows are prepared at startup; animation moves and fades the resulting layers.
- Image fills remain bitmaps: the phone screens, splash image and smoke, record logo, mock interstitial and destination screens.
- Compose and XML Views use the same timeline, audio and native-art definitions.

The two APKs built successfully, and both app lint tasks completed. APK signatures and checksums were verified before upload. The [implementation notes](https://github.com/namvunhatle/r15-onboarding-android-demo/blob/v1.3.3-native/docs/IMPLEMENTATION_NOTES.md#native-art-main) record the visual comparisons made on an emulator. Physical-device performance and audio timing remain unverified.

## Known limits

- The scene still scales a fixed 360 × 800 frame. Other aspect ratios show black bars.
- Replaying while a destination is entering can leave that destination visible after reset.
- Time inspection can still run the final scene's idle animation.
- Reduced motion is partial.
- Ads, purchases, AI generation and destination screens are mocked. The real system bars are hidden.

Start with the [README](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/v1.3.3-native#readme) for controls and build instructions. The previous sprite implementation remains on [`archive/v1.3.3-sprites`](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/archive/v1.3.3-sprites).
