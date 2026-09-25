# 7 · Before release

[Integration guide](README.md) · previous: [Performance](06-performance.md) · next: [Acceptance checks](08-acceptance.md)

Result: no review tool, mock or demo setting ships to users.

## Review tools

| Tool | Code | Action |
| --- | --- | --- |
| Replay button | Layout: `fab` view. Activity: the `fab` field, `findViewById(R.id.fab)`, `fab.setOnClickListener { player.replay() }`, the `fab.visibility` line in `apply()`. Drawables: `fab`, `ic_replay`. | Delete |
| Time inspection (`--ef t`) | `seekExtra()` in `Immersive.kt`; `seekExtra()?.let(player::seekFrozen)` in `onCreate`; `A7Player.seekFrozen` | Keep it **debug-only** (`if (BuildConfig.DEBUG)`): the [acceptance frames](08-acceptance.md) use it |
| Element dump (`--ez dump`) | `dumpExtra()` in `Immersive.kt`; `if (dumpExtra()) native.dump(…)` in `onCreate`; `A7Native.dump` | Debug-only, or delete |
| `tools/` (layout, token and audio generators) | Not part of the app | Keep in the repo only if you regenerate the layout, tokens or audio |

## Mocks

| Mock | Replaced in |
| --- | --- |
| Paywall, AI Ringtones and Home screenshots, and their tap areas | [Launch and exit → Exit](02-launch-and-exit.md#exit) |
| Painted status bar and camera punch-hole | [Launch and exit → System bars](02-launch-and-exit.md#system-bars) |
| Interstitial (`bridge`, `hot_skip`) | [Ads → Interstitial](03-ads.md#interstitial) |
| Native ad content and tints | [Ads → Native ads](03-ads.md#native-ads) |
| Splash banner art (`BannerAd`) | [Ads → Splash banner](03-ads.md#splash-banner) |

## Demo settings

| Setting | Where | Action |
| --- | --- | --- |
| Release build signed with the **debug key** | `buildTypes.release.signingConfig` in the demo's `build.gradle.kts` | Use your release signing |
| App label "A7 · XML Views · Responsive" and icon `ic_a7_launcher` | Demo manifest | Do not copy ([Add to your app](01-add-to-app.md#4-declare-the-activity)) |
| Launcher intent filter on the activity | Demo manifest | Do not copy: your router opens the onboarding |

## Leftover check

When the tables above are done, none of these should remain in your source:

```sh
grep -rnE "hot_skip|hot_paywall_close|hot_subscribe|inter_fill|mona_sans|ic_sb_|ic_replay|R\.id\.fab|BannerAd|Interstitial\(|StatusBar\(|screenshot\(" app/src/main
```

`dest_*` and `bridge` still appear in `A7Script` and `A7Player`. Those are timeline ids with no view bound to them, so they are harmless ([Launch and exit](02-launch-and-exit.md#2-remove-the-screenshot-mocks)).
