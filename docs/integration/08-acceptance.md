# 8 · Acceptance checks

[Integration guide](README.md) · previous: [Before release](07-before-release.md)

Result: proof that the integrated onboarding looks, sounds and behaves like the demo, apart from the intended replacements.

## 1. Frames against the demo

Install the demo, [v1.3.7-merge-module](https://github.com/namvunhatle/r15-onboarding-android-demo/releases/tag/v1.3.7-merge-module), and your debug build (time inspection on, activity exported in debug: see [Add to your app](01-add-to-app.md#6-check)). Capture the same timeline seconds from both:

```sh
shoot() { # $1 = package, $2 = activity, $3 = output prefix
  for t in 0.2 1.0 2.5 4.3 4.6 5.0 5.4 5.8 6.2 7.0 8.5 10.0 12.0 14.5 17.0 19.0; do
    adb shell am force-stop "$1"
    adb shell am start -W -n "$1/$2" --ef t "$t" > /dev/null
    sleep 2.5
    adb exec-out screencap -p > "$3_$t.png"
  done
}
shoot namvunhatle.r15.onboarding.views.responsive namvunhatle.r15.onboarding.views.MainActivity demo
shoot <your.app.id> <your.package>.onboarding.a7.A7OnboardingActivity ours
```

- Repeat on three screen shapes: `adb shell wm size 1080x1920` (16:9), the device default, and `adb shell wm size 1080x2520` (21:9). Finish with `adb shell wm size reset`.
- **Expected**: identical frames, except the intended changes: no painted status bar, and your ads in place of the mock ads.
- Stop at 19.0 s: from 19.47 s the final scene has an idle loop, so frames differ from one capture to the next.
- The demo reaches pixel-identical frames between its own builds this way. Treat any unexplained difference as a bug.

## 2. Behaviour

| Case | Expected |
| --- | --- |
| First launch | Onboarding opens. After a choice, the next launch goes straight to your first screen. |
| Interstitial ready | Opens at 5.30 s. When it closes: the logo returns, the ring burst plays, and the music starts on the drop. |
| Interstitial not ready | No frozen frame: the re-entry follows at once. |
| Native #1 | In G03 from 16.27 s, gone at 17.87 s. |
| Native #2 | In G04 from 19.87 s with the loading placeholder; the ad is revealed from 20.77 to 21.12 s. A different ad from #1. |
| Native with no fill | The slot never appears. The rest of G03 / G04 is unchanged. |
| Tap an action before 19.27 s | Nothing happens. |
| Explore AI Ringtones | Your paywall (AI branch). The music fades out over 0.8 s. |
| Browse ringtones | Your Home, no paywall. |
| Home button mid-trailer, then back | The trailer restarts from the splash. |
| Home button while the interstitial is up | No restart. |
| Ringer on silent or vibrate | No music. The picture is unchanged. |
| Another app playing music | No music, and the other app keeps playing. |
| Incoming call mid-trailer | The music stops on audio-focus loss. The picture continues. |
| Developer options → Remove animations | Fades only. Known gap: some splash movement remains. |
| Rotate the phone | Stays portrait, no restart. |
| Back button | The behaviour product chose ([Launch and exit](02-launch-and-exit.md#lifecycle)). |

Run the whole table on an **R8 release build** at least once. The minified build is the one that ships.

## 3. Performance

| Check | Target | How |
| --- | --- | --- |
| Cold start | About 0.5 s on a Pixel 5-class device; the demo takes 0.46 s | Release build: `adb shell am start -W -S -n <launcher activity>` → `TotalTime` (includes your router) |
| First frame | Background, progress bar, disclaimer and banner are there on the first frame; the logo spins in from 0.05 s | Screen recording of a cold start |
| Trailer | No visible stutter at the ring burst (6.07 s) and at each scene change | Profile GPU rendering, on a physical device |

Frame pacing and GPU memory have not been measured on physical devices yet. Add your numbers to [Performance](06-performance.md#not-measured-yet).
