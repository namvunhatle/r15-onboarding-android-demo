# v1.3.3 — Android onboarding demo

First GitHub release of the R15 Android motion prototype, based on the web prototype v1.3.3.

## Downloads

- **A7-Compose-1.3.3.apk** — Jetpack Compose implementation.
- **A7-XMLViews-1.3.3.apk** — XML Views implementation.
- **SHA256SUMS.txt** — checksums for the two APKs.
- **THIRD_PARTY_NOTICES.txt** — font and icon notices distributed with the APKs.

Both APKs are debug builds for review, require Android 9 / API 28 or later, and can be installed together.

## Included

- Splash, mock interstitial, ring burst, and the G01–G04 introduction.
- Shared animation timeline and three music options with baked sound cues.
- Two native-ad placeholders.
- AI and catalog paths ending in screen mocks.
- Replay, track switching, and timeline-time inspection controls.
- English README, experience guide, implementation notes, and asset credits.

## Validation

Both debug apps built successfully from the standalone source. Both app lint tasks completed with no errors. This is build validation, not a physical-device performance test.

## Known limitations

- The scene scales a fixed 360 × 800 frame. Other aspect ratios show black bars.
- Replaying during a destination transition can leave that destination visible. Wait for the transition to finish before replaying; relaunch the app if needed.
- Time inspection still allows idle movement in the final scene.
- Reduced motion is partial; some splash movement remains.
- System bars and destination screens are artwork. Ads, billing, and AI generation are mocked.
- Physical-device smoothness and audio timing remain unverified.

These issues are documented in this release and have not been fixed as part of publishing it.

Start with the [README](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/v1.3.3#readme), then read the [experience guide](https://github.com/namvunhatle/r15-onboarding-android-demo/blob/v1.3.3/docs/EXPERIENCE.md) or [implementation notes](https://github.com/namvunhatle/r15-onboarding-android-demo/blob/v1.3.3/docs/IMPLEMENTATION_NOTES.md).
