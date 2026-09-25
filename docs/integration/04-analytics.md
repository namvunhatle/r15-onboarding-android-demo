# 4 · Analytics

[Integration guide](README.md) · previous: [Ads](03-ads.md) · next: [Rules that must not change](05-rules.md)

Result: events that measure the onboarding's two goals, and where each one fires. The demo sends no events. **Event names are proposals**: rename them to your analytics conventions.

## The two goals

The onboarding is judged on both goals at once, not on one of them:

1. **More new users reach Home.**
2. **More taps on AI Ringtones.**

They pull against each other: a shorter flow helps the first goal, and an extra AI step helps the second. Track both, so a change that helps one goal cannot quietly cost the other.

## Events

| Event | Parameters | Fires | Hook |
| --- | --- | --- | --- |
| `onboarding_start` | — | Onboarding activity created | `onCreate` |
| `onboarding_restart` | — | Trailer restarted after the app left the foreground | `onStop`, in the `player.replay()` branch |
| `onboarding_interstitial` | `result`: `shown`, `not_ready`, `failed` | 5.30 s | Your `OnboardingAds.showInterstitial` |
| `onboarding_scene` | `scene`: `G01`, `G02`, `G03`, `G04` | 6.67, 9.07, 13.27, 18.07 s | `A7Script` (below) |
| `onboarding_native` | `slot`: `1`, `2`; `result`: `impression`, `no_fill` | When the SDK reports it | Your `OnboardingAds.loadNative` |
| `onboarding_cta` | `action`: `ai_ringtones`, `browse` | Tap on one of the two final actions | `exit(Dest.PAYWALL)`, `exit(Dest.HOME)` |
| `onboarding_leave` | `at`: last `scene` | Back button, or the app closed before a choice | Your back handler |
| `paywall_view` | `source`: `onboarding` | Your paywall opens from the AI branch | Your paywall |
| `home_view` | `source`: `onboarding` | Home opens from the onboarding | Your Home |

**Goal 1** is the funnel `onboarding_start` → `home_view` (`source = onboarding`). **Goal 2** is `onboarding_cta` with `action = ai_ringtones`. The scene events show where users drop off.

## Scene events from the timeline

The script already has a callback mechanism: `t.call(time, fn)`, used today for the interstitial (`onAd`) and the idle loop (`onIdle`). Add one more callback:

```kotlin
// A7Script.build(…): add a parameter  onScene: (String) -> Unit
t.call(times.tG01) { onScene("G01") }
t.call(times.tG02a) { onScene("G02") }
t.call(times.tG03) { onScene("G03") }
t.call(times.tG04) { onScene("G04") }
```

```kotlin
// A7Player
var onScene: (String) -> Unit = {}
// in rebuild():
A7Script.build(main, scene, times, reduced, onAd = { … }, onIdle = ::startIdle, onScene = { onScene(it) })
```

Then set `player.onScene = { analytics.log("onboarding_scene", "scene" to it) }` in `onCreate`.

- The callbacks fire once per pass through the trailer. A restart fires them again. Keep that if you want restarts in the funnel; otherwise dedupe per activity instance.
- Time inspection (`--ef t`, `seekFrozen`) skips callbacks, so review screenshots send no events.
- The times above are **timeline seconds**: they exclude the wait on the interstitial. For wall-clock durations, log `SystemClock.elapsedRealtime()` relative to `onboarding_start`.
