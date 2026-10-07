# Integrating the A7 onboarding

[Back to README](../README.md) · how it works inside: [ARCHITECTURE](ARCHITECTURE.md)

The onboarding is one view, `A7OnboardingView`, in the library module `a7onboarding/`. Four steps.

## 1. Copy the folder

Copy `a7onboarding/` into the project root, then:

```kotlin
// settings.gradle.kts
include(":a7onboarding")

// app/build.gradle.kts
dependencies { implementation(project(":a7onboarding")) }
```

Requirements: minSdk 28, compileSdk 37, Java 17, AGP 9 (built-in Kotlin). Dependencies: `androidx.core:core-ktx` only.
Keep `namespace = "namvunhatle.r15.onboarding"`: the code refers to its resources through it. Every resource is
prefixed `a7_` (or `zen_` / `adkit_` for the design tokens), and assets live under `assets/a7/`, so nothing clashes with
the app.

## 2. Give it a full-screen window

The view expects the whole screen, portrait, edge to edge. The system status bar stays visible on top of the scene:

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false // light icons
```

Theme: transparent status bar, `windowLayoutInDisplayCutoutMode = shortEdges`, window background `#262551` (the
splash's corner colour, so the system splash hands over without a flash). The sample's `Immersive.kt` and
`themes.xml` are a working example. Whether the navigation bar stays hidden is the app's choice; the sample hides it.

## 3. Put the view on screen and start it

```kotlin
val a7 = findViewById<A7OnboardingView>(R.id.onboarding)
a7.ads = MyAds(this)
a7.onExplore = { /* paywall, then AI Ringtones */ }
a7.onBrowse = { /* Home */ }
a7.start()
```

- `onExplore` / `onBrowse` fire from G04's two buttons. The music fades out as they fire; the view then stays on its
  last frame, so the app can slide its next screen over it, then remove it.
- Leaving mid-trailer (home, call, lock) restarts it next time it is visible — but not while the interstitial is up,
  and not once G04 is reached.
- `restart()` goes back to the splash. `seekFrozen(t)` opens frozen at second `t` (review only).

## 4. Implement `A7Ads`

The library has no ad SDK. The app implements the interface with its own (AdMob, mediation, its wrapper):

| Method | When | What the app does |
| --- | --- | --- |
| `preload(activity)` | first frame of the splash | start loading the interstitial and native #1 |
| `banner(activity, slot)` | start | add a banner view to `slot` (360 × 60 design dp, at the bottom of the splash) |
| `showInterstitial(activity, onClosed)` | end of the 5 s splash | show it; call `onClosed()` once when it is dismissed, fails, or is not ready |
| `native(activity, slot, which, onResult)` | `which` 1: start (shown at b17 under the G03 call) · 2: when G04 lands | add the ad view to `slot` (328 × 256 design dp); `onResult(true)` when it is in, `false` when there is none |
| `release()` | the view is detached | destroy what was loaded |

- The trailer **waits** on its re-entry frame until `onClosed()`. Not ready = call it right away.
- A slot shows a loading skeleton until `onResult(true)`, then fades it; `onResult(false)` collapses the slot.
- Slots are scaled with the 360 × 800 design frame (≈ 1.14× on a 20:9 phone).
- Placement is design and is kept by the library: the swipe area ends 9 dp above native #1, nothing tappable sits next
  to an ad, native #2 is a fresh request.
- ⚠️ AdMob forbids interstitials on app launch and recommends an App Open Ad there. Which format fills the post-splash
  moment is a product (MO) decision; the interface does not change.

`sample/…/MockAds.kt` is a complete mock implementation: a full-screen dialog for the interstitial, drawn native ads.

## What the app owns

The window and status bar, the ad SDK, the paywall / AI Ringtones / Home screens, analytics, and when the onboarding
counts as done (proposal: when one of G04's buttons is tapped).
