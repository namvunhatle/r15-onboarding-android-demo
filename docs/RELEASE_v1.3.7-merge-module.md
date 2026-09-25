# v1.3.7-merge-module — faster launch

XML Views only, single module (branch [`merge-module`](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/merge-module)). One track, Ogg Opus, as in 1.3.6. Same motion, audio and picture; the app just opens faster.

| Cold start, time to first frame | 1.3.6 | 1.3.7 |
| --- | --- | --- |
| XML Views | 1.57 s | **0.31 s** |

Measured on an Android 16 emulator (1080 × 2400, Apple M1 host). A physical phone is slower in absolute terms.

- **Release build** (R8, not debuggable), signed with a debug key for review. The APK drops from 10.6 MB to 3.6 MB.
- **Start-up work runs off the main thread.** Blurs, glows, native art and screenshots are prepared in the background. The first frame waits only for the splash, logo and glow.
- **Unchanged picture.** At 20:9 and 16:9 it matches 1.3.6 pixel for pixel: 16 timeline frames, plus both destination paths.

**Download:** `A7-XMLViews-1.3.7.apk` · Android 9+ · `namvunhatle.r15.onboarding.views.responsive` · installs over 1.3.6.

**Tested** on an Android 16 emulator. Not yet measured on a physical device. Same changes on `main`, Compose and XML Views: [v1.3.7](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.7).
