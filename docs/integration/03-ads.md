# 3 · Ads

[Integration guide](README.md) · previous: [Launch and exit](02-launch-and-exit.md) · next: [Analytics](04-analytics.md)

Result: the four ad placements call your ad layer, and the mock ad art is gone. **The ad SDK is not chosen yet.** This page defines the contract between the onboarding and your ad layer, so the SDK can be plugged in later without touching the timeline.

## The four placements

| Placement | Demo mock | On screen (timeline s) | Box (design dp) |
| --- | --- | --- | --- |
| Splash banner | `sp_strip`, drawn by `BannerAd` | 0 → 5.32, hidden with the splash | 360 × 60 at (0, 692). Holds the bottom edge; full screen width on phones wider than 20:9. |
| Interstitial | `bridge` layer: `bridge_full` art + `hot_skip` | Opens at 5.30; the timeline **waits** at 5.31 | Full screen |
| Native #1 | `nat1` (`native_ad.xml`, lavender tint) | In at 16.27 (G03), out at 17.87 | 328 × 256 at (16, 480) |
| Native #2 | `nat2` (`native_ad.xml`, orange tint) + `nat2_skel` | In at 19.87 (G04) as a loading placeholder. Placeholder fades out from 20.77; ad fully visible at 21.12. | 328 × 256 at (16, 480) |

The timeline only moves, scales and fades the slot views (`sp_strip`, `nat1`, `nat2`). Whatever your SDK puts inside a slot moves with it.

## The ad interface

Everything the onboarding needs from your ad layer. Implement it once the SDK is chosen.

```kotlin
/** The onboarding's only dependency on ads. All callbacks on the main thread. */
interface OnboardingAds {
    /** Splash banner into [container]; it is on screen 0–5.32 s. */
    fun loadBanner(container: ViewGroup)

    /** Show the interstitial if one is ready. Call [onDone] when it closes, fails, or was not ready. Never wait for a load here. */
    fun showInterstitial(activity: Activity, onDone: () -> Unit)

    /** Fill native slot [index] (1 or 2). Two separate requests: two different ads. [onResult] false = no fill. */
    fun loadNative(slot: ViewGroup, index: Int, onResult: (filled: Boolean) -> Unit)

    fun destroy()
}
```

Hold one instance in `A7OnboardingActivity`, and call `ads.destroy()` from `onDestroy`.

**Preload.** The splash lasts a fixed 5 s so that the interstitial can load ([Rules](05-rules.md)). Request the interstitial and both natives no later than `onCreate`, or earlier, at app start.

## Interstitial

**Contract.** At 5.30 s the script calls `onAd` (`A7Script`: `t.call(A7Times.T_AD, onAd)`), which sets `player.atAd = true`. At 5.31 s the timeline waits. `player.skipAd()` releases it: the logo comes back at rest (P03), then the ring burst and the music start. `skipAd()` also starts the audio, so call it on the main thread.

**Wire it** in `apply()`, inside the existing `if (player.uiVersion != ui) { … }` block:

```kotlin
if (player.atAd) ads.showInterstitial(this) { player.skipAd() }
```

- `atAd` turns true once each time the trailer reaches 5.30 s, and `uiVersion` changes with it. The call therefore runs once per pass, including after a restart.
- **No ad ready → `onDone` at once.** The trailer continues straight into the re-entry. Do not hold the user on a frozen frame while an ad loads.
- The SDK's own activity stops ours. `onStop` does not restart the trailer while `player.atAd` is true ([Launch and exit](02-launch-and-exit.md#lifecycle)). Keep that check.

**Remove the mock:**

| Remove | Where |
| --- | --- |
| `bridge` layer, with `bridge_full` and `hot_skip` | Layout |
| `hot(R.id.hot_skip, …)` | `onCreate` |
| `place(findViewById(R.id.hot_skip), scene.hotSkip)` and `"bridge"` in the full-screen loop | `fill` (with the screenshots also gone, delete the loop) |
| `"bridge_full"` in `A7Native.IDS`, its `make()` branch, class `Interstitial` | `A7Native`, `FigmaArt.kt` |
| `inter_fill.jpg`, `mona_sans_medium.ttf` | Resources |

The script still fades the id `bridge` at 5.30 and 5.32 s. With no view bound to it, this does nothing.

## Native ads

**Slots.** `nat1` and `nat2` are two copies of `native_ad.xml`, tagged in `nativeSlot()`. The outer `CssBox` is the white card (`Corner-Radius/Large`). Its children are the mock ad: icon, headline, body, "Ad" badge, media and install button. The last child, `nat_skel`, is native #2's loading placeholder.

1. In `native_ad.xml`, delete the mock children and keep `nat_skel`.
2. In `nativeSlot()`, delete the two `tint(…)` lines. Keep the tagging and the `nat_skel` setup: the timeline finds the slots by tag.
3. Add your SDK's native view **below** `nat_skel`, so the placeholder covers it until it fades:

```kotlin
// onCreate, after collect(frame) { … } has bound the views to the timeline
listOf(R.id.nat1 to 1, R.id.nat2 to 2).forEach { (id, n) ->
    val slot = findViewById<ViewGroup>(id)
    ads.loadNative(slot, n) { filled -> if (!filled) hideSlot("nat$n", slot) }
}
```

```kotlin
/** No fill: take the slot off the timeline so apply() never shows it. */
private fun hideSlot(tag: String, slot: View) {
    bound.removeAll { it.el.id == tag }
    slot.visibility = View.INVISIBLE
}
```

- Inside `loadNative`, add the ad view with `slot.addView(adView, 0)`: index 0 keeps it under `nat_skel`.
- Call it **after** `collect(frame)`. `apply()` sets each bound view's visibility every time the timeline shows or hides it, so hiding a slot means removing it from `bound`.
- **Native #2 is a separate request** for a different ad. The placeholder plays on every run: it is part of the design, showing a fresh load. The ad must be filled by 20.77 s for the reveal to show it. Treat a slot that is still empty when it enters (16.27 s for #1, 19.87 s for #2) as no fill. Agree the exact request timing with ad ops.
- The card box is fixed at 328 × 256 dp. If your SDK's template needs another size, change the slot in `native_ad.xml` and check G03 and G04 in the [acceptance frames](08-acceptance.md).

## Splash banner

`sp_strip` is drawn today (`BannerAd` in `FigmaArt.kt`). Give it a real container:

| Change | Where |
| --- | --- |
| Replace `<View android:tag="sp_strip" …/>` with `<FrameLayout android:id="@+id/a7_banner" android:tag="sp_strip" …/>`, same size and margins | Layout |
| Remove `"sp_strip"` from `A7Native.IDS` and its `make()` branch; delete class `BannerAd` | `A7Native`, `FigmaArt.kt` |
| Add `place(frame.findViewWithTag("sp_strip"), scene.pos.getValue("sp_strip"))` (until now `IDS` placed it) | `fill` |
| `ads.loadBanner(findViewById(R.id.a7_banner))` | `onCreate` |

The script fades `sp_strip` out at 5.32 s, together with the splash progress and the disclaimer. The container keeps the tag, so the banner goes with them.

Keep the disclaimer under the progress bar, `sp_note`: "This action may contain ads."
