# v1.3.7 — faster launch

Compose and XML Views, from `main`. Same motion, audio and picture as 1.3.6. The apps just open faster.

| Cold start, time to first frame | 1.3.6 | 1.3.7 |
| --- | --- | --- |
| XML Views | 1.55 s | **0.25 s** |
| Compose | 1.85 s | **0.31 s** |

Measured on an Android 16 emulator (1080 × 2400, Apple M1 host). A physical phone is slower in absolute terms.

- **Release builds** (R8, not debuggable), signed with a debug key for review. The Compose APK drops from 43 MB to 21 MB.
- **Start-up work runs off the main thread.** Blurs, glows, native art and screenshots are prepared in the background. The first frame waits only for the splash, logo and glow.
- **Unchanged picture.** At 20:9, 16:9 and 21:9, both apps match 1.3.6 pixel for pixel: 16 timeline frames, plus both destination paths.

| APK | Application ID |
| --- | --- |
| `A7-Compose-1.3.7.apk` | `namvunhatle.r15.onboarding.compose.responsive` |
| `A7-XMLViews-1.3.7.apk` | `namvunhatle.r15.onboarding.views.responsive` |

Android 9+ · installs over 1.3.6 · **tested** on an Android 16 emulator, both apps. Not yet measured on a physical device.
