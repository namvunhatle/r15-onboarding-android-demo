package namvunhatle.r15.onboarding

import android.app.Activity
import android.view.ViewGroup

/**
 * Everything the onboarding needs from an ad SDK — and nothing more. The library itself depends on no ad SDK: the host
 * app implements this with whatever it already uses (AdMob, a mediation layer, its own wrapper); the sample app uses a
 * mock (MockAds).
 *
 * The onboarding decides WHEN (the beats are design), the host decides WHAT. Every call comes on the main thread, and
 * every callback may be called on any thread — the view posts it back.
 *
 * Placement rules the library already keeps for you: native #1 sits under the feed and the swipe area ends 9 dp above
 * it (no accidental clicks); nothing tappable is placed next to an ad; native #2 is a fresh request, never native #1
 * moved down; the interstitial comes after the splash, never on top of content.
 */
interface A7Ads {
    /** The onboarding starts (first frame of the splash): start loading the interstitial and native #1. */
    fun preload(activity: Activity) {}

    /** Splash banner. Put a banner view into [slot] (360 × 60 design dp; scaled with the scene). */
    fun banner(activity: Activity, slot: ViewGroup) {}

    /**
     * The interstitial, at the end of the 5 s splash. The trailer waits on its re-entry frame until [onClosed] is called
     * — exactly once: when the ad is dismissed, failed to show, or was not ready. Not ready = call it right away.
     *
     * ⚠️ AdMob's policy forbids interstitials on app launch and recommends App Open Ads there. Which format fills this
     * slot is the host's (MO's) decision; the onboarding only gives the moment.
     */
    fun showInterstitial(activity: Activity, onClosed: () -> Unit) = onClosed()

    /**
     * A native ad into [slot] (328 × 256 design dp). [which] 1 = under the G03 call (requested at start, shown at
     * b17), 2 = on G04 (a fresh request when G04 lands). Call [onResult] once: `true` when the ad view is in the slot
     * (the loading skeleton then fades), `false` when there is none (the slot collapses).
     */
    fun native(activity: Activity, slot: ViewGroup, which: Int, onResult: (Boolean) -> Unit) = onResult(false)

    /** The onboarding view is gone: destroy what was loaded. */
    fun release() {}
}
