# R15 Onboarding (A7) — Android library

The R15 onboarding as **one Android view**: splash → interstitial → trailer → swipe-to-continue feed → G04 with two
exits. Ported from web prototype **1.6.6** (variant 1.6 · swipe teaser). Packaged like the R1 Mood Animation library:
**copy one folder, put one view on screen.**

```kotlin
a7.ads = MyAds(this)               // the app's ad SDK behind A7Ads (null = no ads)
a7.onExplore = { openPaywall() }    // "Explore AI Ringtones"  → paywall → AI Ringtones
a7.onBrowse = { openHome() }        // "Browse ringtones"      → Home, no paywall
a7.start()
```

**Demo APK:** [release v1.6.6-single-view](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.6.6-single-view) ·
`./gradlew :sample:installRelease`

## Structure

```text
a7onboarding/                     ◀ THE LIBRARY — the one folder to copy into the app
├─ src/main/java/namvunhatle/r15/onboarding/
│  ├─ A7OnboardingView.kt         public · the view: start(), restart(), onExplore, onBrowse, ads
│  ├─ A7Ads.kt                    public · what the app implements with its ad SDK (banner, interstitial, 2 natives)
│  ├─ core/                       internal engine — the app never calls it
│  │  ├─ A7Script.kt              the trailer: every beat, tween and ease (port of the web's master timeline)
│  │  ├─ A7Player.kt              runs the timeline, the wait for the swipe, drag / commit, haptic cues
│  │  ├─ A7Mixer.kt               music: 6 stems mixed live, lowpass, limiter; the picture follows its clock
│  │  ├─ Timeline.kt · Ease.kt    GSAP-like timeline and eases
│  │  ├─ Scene.kt · Viewport.kt   element boxes in the 360 × 800 frame; fitting any 16:9–23:9 screen
│  │  ├─ A7Native.kt · FigmaArt.kt   Figma elements drawn in code (headlines, tiles, phones, stickers, buttons)
│  │  ├─ A7Art.kt · A7Glow.kt · Bleed.kt   baked glows / blurs, done off the main thread (Later.kt)
│  │  └─ ZenTokens.kt · A7Visual.kt   ZEN design tokens (generated) · the few values Figma has no token for
│  └─ views/Widgets.kt            custom views: DesignFrame, CssBox, LyricView (name reel), FeedView, WaveView, …
├─ src/main/res/                  layout a7_onboarding.xml (the scene), fonts, 6 artwork bitmaps, tokens — all a7_ / zen_
└─ src/main/assets/a7/            manifest.json (Figma boxes) · audio/ (6 Ogg Opus stems, ≈ 710 KB)

sample/                           demo app — NOT for the production app
└─ …/sample/  MainActivity.kt (host + screenshots of paywall / AI / Home) · MockAds.kt · MockArt.kt · Immersive.kt

tools/                            bake/stems.* + encode_stems.sh (audio) · gen_tokens.py + tokens/ (design tokens)
docs/                             INTEGRATION · ARCHITECTURE · EXPERIENCE · DESIGN_TOKENS · CREDITS · history/
```

**Two public types, everything else is internal.** The app sees `A7OnboardingView` and `A7Ads`; `core/` and
`views/` are the implementation.

## Where to change what

| To change… | Edit |
| --- | --- |
| When something happens (beats, durations, eases) | `core/A7Script.kt` |
| How an element looks (Figma geometry, text, gradients) | `core/FigmaArt.kt`, `core/A7Native.kt`; the lyric card / feed in `views/Widgets.kt` |
| Colours, type, radii, spacing | Figma variables → `tools/gen_tokens.py` ([DESIGN_TOKENS](docs/DESIGN_TOKENS.md)) |
| Where an element sits | `res/layout/a7_onboarding.xml` and `core/Scene.kt` |
| The swipe (threshold, fling, hints) | `core/A7Player.kt` |
| Music / sound cues | `tools/bake/stems.html` → re-bake ([ARCHITECTURE § Audio](docs/ARCHITECTURE.md#audio)) |
| Ads | the app's own `A7Ads` implementation — not the library |

## What is in the view

- **Drawn in code.** Six bitmaps remain, all artwork that is an image in Figma too: splash background and smoke, the
  logo, the three phone screens.
- **Real status bar** over the scene; its top 40 dp is kept free for it.
- **Music mixed live** from six stems: the wait muffles the bed, the drag opens it, card 2's song lands on the beat.
  Off on silent / vibrate or when another app plays music.
- **Ads are the app's.** The library only says *when* (`A7Ads`); slots show a skeleton and collapse without an ad.
- **Any phone screen** 16:9–23:9 · **reduced motion** turns entrances into fades.

## Docs

| | |
| --- | --- |
| [INTEGRATION](docs/INTEGRATION.md) | 4 steps to put it in the app · the `A7Ads` contract · what the app owns |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | how it runs: timeline, player, mixer, rendering, screen sizes, debugging |
| [EXPERIENCE](docs/EXPERIENCE.md) | the flow, scene by scene, with timings |
| [DESIGN_TOKENS](docs/DESIGN_TOKENS.md) | ZEN tokens: naming, regeneration |
| [CHANGELOG](CHANGELOG.md) · [CREDITS](docs/CREDITS.md) | history · music, fonts, icons |

**Not verified on a physical phone yet:** sound, audio / picture sync, haptics. The emulator renders on the CPU.
