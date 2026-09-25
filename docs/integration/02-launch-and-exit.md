# 2 · Launch and exit

[Integration guide](README.md) · previous: [Add to your app](01-add-to-app.md) · next: [Ads](03-ads.md)

Result: your app opens the onboarding on first run. Its two actions open your real paywall and Home, and the screenshot mocks are gone.

## First run

Open the onboarding from the activity that decides your first screen:

```kotlin
// Your launcher / router activity
if (!OnboardingState.isDone(this) && Build.VERSION.SDK_INT >= 28) {
    startActivity(Intent(this, A7OnboardingActivity::class.java))
} else {
    startActivity(yourHomeIntent)
}
finish()
```

```kotlin
object OnboardingState {
    private const val PREFS = "a7_onboarding"
    fun isDone(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean("done", false)
    fun markDone(c: Context) = c.getSharedPreferences(PREFS, 0).edit().putBoolean("done", true).apply()
}
```

**Open decision: when onboarding counts as done.** The proposal in this guide marks it done when the user taps one of the two final actions ([Exit](#exit)). Someone who leaves mid-trailer then sees it again on the next launch.

## System splash (Android 12+)

Android 12+ always shows a system splash when the app starts. It comes from the theme of the activity that starts the task: **your launcher activity**, not the onboarding.

- The demo sets the splash to the onboarding's background colour (`#262551`) with **no icon** (`values-v31/themes.xml`). The system splash then hands over to the onboarding's own splash as a plain colour change, with one logo on screen, not two.
- If your launcher theme shows the app icon (SplashScreen API), first-run users see your icon, then the onboarding's logo. **Open decision** for design.

## Exit

The demo ends on three screenshots (`Dest.PAYWALL`, `Dest.AI`, `Dest.HOME`) that slide over the trailer. In production, the two final actions leave the onboarding for your real screens.

| Action (tap area) | Demo | Production |
| --- | --- | --- |
| **Explore AI Ringtones** (`hot_cta`) | Paywall screenshot | Your paywall, entered from the AI branch, then the AI Ringtones screen |
| Paywall close (`hot_paywall_close`) | AI Ringtones screenshot | Your paywall's close. **Open decision**: the demo goes to AI Ringtones. |
| Subscribe (`hot_subscribe`) | AI Ringtones screenshot, no billing | After a successful purchase: AI Ringtones |
| **Browse ringtones** (`hot_secondary`) | Home screenshot | Your Home, **no paywall** |

⚠️ The demo's paywall screenshot lists **"No ads, ever"**. Do not carry that line into your paywall: removing ads is never presented as a Premium benefit ([Rules](05-rules.md)).

### 1. Route the two actions

```kotlin
// A7OnboardingActivity
private fun exit(to: Dest) {
    player.go(to)                   // stops the idle loop, fades the music out (0.8 s), and keeps onStop() from restarting the trailer
    OnboardingState.markDone(this)
    startActivity(routes.fromOnboarding(this, to))   // your navigation: PAYWALL → paywall (AI branch), HOME → Home
    finish()
}
```

In `onCreate`, point the two G04 tap areas at it:

```kotlin
hot(R.id.hot_cta, "g04_cta", ctaOk) { exit(Dest.PAYWALL) }
hot(R.id.hot_secondary, "g04_secondary", ctaOk) { exit(Dest.HOME) }
```

- `ctaOk` stays: the actions are live only from 19.27 s, when the CTA has landed (`hot_g04` pointer in `A7Script`), and only until one is chosen.
- The music fade finishes after `finish()`. It runs on a main-thread `Handler`, and `stop(fade)` detaches the stream first, so `release()` in `onDestroy` does not cut it. If your next screen starts audio, the two overlap for up to 0.8 s.
- **Transition.** The demo slides the destination in from the right: 400 ms, `power3Out` (= `DecelerateInterpolator(1.5f)`). Use that, or your app's standard screen transition.

### 2. Remove the screenshot mocks

| Remove | Where |
| --- | --- |
| Layers `dest_paywall`, `dest_ai`, `dest_home`, including `hot_paywall_close` and `hot_subscribe` | Layout (`activity_main.xml`, renamed `a7_onboarding.xml`) |
| The "destination screenshots" block | `A7OnboardingActivity.onCreate` |
| `hot(R.id.hot_paywall_close, …)` and `hot(R.id.hot_subscribe, …)` | `A7OnboardingActivity.onCreate` |
| The `Dest.entries` part of the full-screen loop | `A7OnboardingActivity.fill` |
| `player.destOrder.forEach { … bringToFront() }` | `A7OnboardingActivity.apply` |
| `shots`, `screenshot()`, `SHOTS` | `A7Native` |
| `Bleed.kt` (only the screenshots use it) | `core/` |
| `dest_ai.jpg`, `dest_home.jpg`, `dest_paywall.jpg` | `drawable-nodpi/` |

Keep `Dest` and `A7Player.go()`. `go()` still animates the ids `dest_*` in the timeline store. With no views bound to them, this does nothing visible.

## Lifecycle

| Event | Demo behaviour | Production |
| --- | --- | --- |
| App leaves the foreground mid-trailer (Home, call, lock) | `onStop` restarts the trailer from the splash (`player.replay()`) | Keep, unless product decides otherwise |
| The interstitial opens its own activity | `onStop` does not restart while `player.atAd` is true | **Keep**: otherwise every ad restarts the onboarding |
| After `exit()` | `player.dest` is set, so `onStop` does not restart | — |
| Process death | Starts again from the splash (no saved state) | Acceptable: the trailer lasts about 20 s |
| Rotation, resize | Portrait-locked; `configChanges` avoids recreation | Keep |
| **Back button** | Not handled: the system finishes the activity, which closes the app on first run | **Open decision**. The demo extends `android.app.Activity`. Extend `ComponentActivity` to use `onBackPressedDispatcher`: nothing in the class depends on plain `Activity`. |
| Screen timeout | `FLAG_KEEP_SCREEN_ON` (set in `immersive()`) | Keep: the trailer runs about 20 s without a touch |

## System bars

The demo hides both system bars (`immersive()` in `core/Immersive.kt`). It **paints** a status bar instead: `statusbar`, with a fixed "9:30" clock and fake Wi-Fi, signal and battery icons, plus a camera punch-hole, `punch`. The painted bar must go either way.

**Remove the painted bar:**

| Remove | Where |
| --- | --- |
| `statusbar` view and `punch` view | Layout |
| `"statusbar"` in `A7Native.IDS`, and its branch in `make()` | `A7Native` |
| `place(findViewById(R.id.punch), scene.punch)` | `A7OnboardingActivity.fill` |
| `StatusBar` class | `FigmaArt.kt` |
| `ic_sb_battery`, `ic_sb_signal`, `ic_sb_wifi` | `drawable/` |

Remove an id from `A7Native.IDS` and its view **together**. `onCreate` and `fill` look up every id in `IDS` by tag, so a missing view crashes.

**Then choose (open decision, design):**

| Option | Change in `immersive()` | Result |
| --- | --- | --- |
| A · Bars stay hidden | None | Full-bleed, like a video. The top 40 dp shows the scene's background. |
| B · Real bars | Drop `hide(WindowInsetsCompat.Type.systemBars())`. Keep `setDecorFitsSystemWindows(false)`. Set `isAppearanceLightStatusBars = false` (light icons on the dark scene). | The real status bar sits in the top 40 dp band, which the design keeps free for it. With 3-button navigation, check the bottom-anchored splash elements (progress bar, disclaimer, banner) against the navigation bar. |
