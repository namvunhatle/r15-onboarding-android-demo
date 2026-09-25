# Integration guide

[Back to README](../../README.md)

How to move the A7 onboarding from this demo into the production app. Written against branch `merge-module`, **v1.3.7** (XML Views, one module).

The motion, art and audio are production code: copy them as they are. The ads, the paywall and the destination screens are mocks: replace them. The rest of this guide says which is which, and where each change goes.

## What is real and what is mock

| Part | In this demo | In the production app |
| --- | --- | --- |
| Timeline, scene script, player (`Timeline`, `A7Script`, `A7Player`) | Production code | Copy unchanged |
| Native art (`FigmaArt`, `A7Native`, `A7Art`, `A7Glow`, `Later`) | Production code | Copy unchanged |
| XML layout and custom views (`activity_main.xml`, `Widgets.kt`) | Production code | Copy, rename resources |
| Music (`A7Audio`, Ogg Opus assets) | Production code | Copy unchanged |
| Splash banner, interstitial, native ads #1 and #2 | **Mock art** | Your ad SDK ([Ads](03-ads.md)) |
| Paywall, AI Ringtones, Home | **Screenshots** | Your screens ([Launch and exit](02-launch-and-exit.md)) |
| Status bar and camera punch-hole | **Painted** | Remove ([Launch and exit](02-launch-and-exit.md#system-bars)) |
| Replay button, `--ef t`, `--ez dump` | Review tools | Remove or keep debug-only ([Before release](07-before-release.md)) |

## Steps

| # | Page | Result |
| --- | --- | --- |
| 1 | [Add to your app](01-add-to-app.md) | The onboarding builds inside your app module, with no resource clashes |
| 2 | [Launch and exit](02-launch-and-exit.md) | Your app opens it on first run; its two actions open your real screens |
| 3 | [Ads](03-ads.md) | The four ad placements call your ad layer |
| 4 | [Analytics](04-analytics.md) | Events for the onboarding's two goals |
| 5 | [Rules that must not change](05-rules.md) | What product has decided, and why |
| 6 | [Performance](06-performance.md) | How launch stays under half a second |
| 7 | [Before release](07-before-release.md) | Review tools and mocks removed |
| 8 | [Acceptance checks](08-acceptance.md) | How to verify the result against the demo APK |

Read [Rules that must not change](05-rules.md) before you change any timing, placement or flow.

## Open decisions

These are not decided yet. The guide shows where each one plugs in.

| Decision | Owner | Where it plugs in |
| --- | --- | --- |
| Ad SDK | Product / ad ops | One interface, `OnboardingAds` ([Ads](03-ads.md#the-ad-interface)) |
| System bars during onboarding: hidden or visible | Design | `immersive()` ([Launch and exit](02-launch-and-exit.md#system-bars)) |
| Android 12+ system splash: app icon or none | Design | Your launcher activity's theme ([Launch and exit](02-launch-and-exit.md#system-splash-android-12)) |
| Where the paywall's close button goes (demo: AI Ringtones) | Product | Your paywall ([Launch and exit](02-launch-and-exit.md#exit)) |
| When onboarding counts as done | Product | `OnboardingState.markDone` ([Launch and exit](02-launch-and-exit.md#first-run)) |
| Back button during onboarding | Product | `onBackPressedDispatcher` ([Launch and exit](02-launch-and-exit.md#lifecycle)) |
| Analytics event names | Analytics | [Analytics](04-analytics.md) |

## Reference

- [Experience](../EXPERIENCE.md): scene order and the timeline in seconds.
- [Implementation notes](../IMPLEMENTATION_NOTES.md): how the engine, art and audio work.
- [Design tokens](../DESIGN_TOKENS.md): which Figma variable each value uses.
- Demo APK to compare against: [v1.3.7-merge-module](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.7-merge-module).

**Coordinates.** All boxes in this guide are in **design dp**: the 360 × 800 frame of the Figma file. `DesignFrame` scales the frame uniformly to the screen, and `Scene` moves edge-anchored elements on phones that are not 20:9. **Times** are **timeline seconds**: the wait on the interstitial is excluded. See [Experience](../EXPERIENCE.md#timing).
