# v1.3.4-merge-module — XML Views only, one module

The 1.3.4 demo for teams that build with **XML Views only**. On `main`, a shared `:core` library holds the engine and every asset for two apps (Compose and XML Views). Here `:core` is merged into `:app-views` and the Compose app is removed. Code, resources and assets now all live in `app-views/`. This release is built from the [`merge-module`](https://github.com/namvunhatle/r15-onboarding-android-demo/tree/merge-module) branch at this tag.

The app behaves the same as the XML Views build of [v1.3.4](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.4). Motion, audio, layout and design tokens are unchanged.

## Download

| APK | Renderer | Application ID |
| --- | --- | --- |
| **A7-XMLViews-1.3.4-merge-module.apk** | XML Views | `namvunhatle.r15.onboarding.views.responsive` |

This is a debug APK for review and needs Android 9 / API 28 or later. It uses the same application ID and `versionCode` as the 1.3.4 XML Views build, so it installs over that build. `SHA256SUMS.txt` lists the checksum. `THIRD_PARTY_NOTICES.txt` covers the bundled third-party assets.

## Where things moved

| On `main` | On `merge-module` |
| --- | --- |
| `core/src/main/java/…/onboarding/core/` | `app-views/src/main/java/…/onboarding/core/`, with the package name unchanged |
| `core/src/main/res/`: images, fonts, vectors, `zen_tokens.xml`, `a7_visual.xml`, theme | `app-views/src/main/res/` |
| `core/src/main/assets/`: audio, `manifest.json`, `tracks.json` | `app-views/src/main/assets/` |
| `app-compose/` | removed |

- `A7Art.kt` and `A7Native.kt` now import `namvunhatle.r15.onboarding.views.R`.
- The scripts in `tools/` (`gen_tokens.py`, `gen_layout.py`, `bake`) write to the new paths.
- Kotlin stays at 2.4.20, the compiler `main` uses. It is pinned on the root `buildscript` classpath because, without the Compose plugin, AGP 9.4.1 falls back to its bundled 2.2.10.

## Checks

This APK was compared with the XML Views APK built from `main` at 1.3.4:

- All 85 resource and asset files are byte-identical, and the 1,522 resource-table entries match.
- The bytecode of all 203 app classes matches, apart from dex offsets, `R` class names, and Kotlin's module suffix on `internal` members (`$core` → `$app_views`).
- Lint reports 0 errors.

These checks compare the two APK files. The app has **not** been launched from this build: the emulator was unavailable, so no screenshots were compared.

## Known limits

The limits are the same as for 1.3.4. See the [README](https://github.com/namvunhatle/r15-onboarding-android-demo/blob/v1.3.4-merge-module/README.md#known-limits).
