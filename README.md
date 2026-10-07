# R15 Onboarding (A7) — Android library

The R15 onboarding as **one Android view**: splash, trailer, the swipe-to-continue feed, G04 and its two exits. Ported
from the web prototype **1.6.6** (variant 1.6 · swipe teaser). This branch, `single-view`, packages it the way the R1
Mood Animation library is packaged: one folder to copy, one view to put on screen.

| Folder | What it is |
| --- | --- |
| `a7onboarding/` | **The library — the one folder to copy into the app.** `A7OnboardingView` + `A7Ads`. No ad SDK, no Compose. |
| `sample/` | Demo app: the view full screen, mock ads (`MockAds`), the three screens after the onboarding as screenshots. |
| `tools/` | Audio stem bake (`tools/bake/stems.*`, `tools/encode_stems.sh`) and the design-token generator. |

## Try it

```
./gradlew :sample:installRelease
```

The release build is R8-minified and signed with the debug key (a debuggable build opens about 3× slower).
`adb shell am start -n namvunhatle.r15.onboarding.sample/.MainActivity --ef t 12.4` opens frozen at 12.4 s.

## Add it to the app

```xml
<namvunhatle.r15.onboarding.A7OnboardingView android:id="@+id/onboarding"
    android:layout_width="match_parent" android:layout_height="match_parent" />
```

```kotlin
val a7 = findViewById<A7OnboardingView>(R.id.onboarding)
a7.ads = MyAds(this)               // your ad SDK behind A7Ads; null = no ads
a7.onExplore = { openPaywall() }    // "Explore AI Ringtones" → paywall → AI Ringtones
a7.onBrowse = { openHome() }        // "Browse ringtones" → Home, no paywall
a7.start()
```

Full steps, the ads contract and what the host owns: **[docs/INTEGRATION.md](docs/INTEGRATION.md)**.

## What is in the view

- **Drawn in code.** Text, headlines, buttons, tiles, stickers, phone frames, the lyric card's name reel, the feed cards,
  glows. Six bitmaps remain, all artwork that is an image in Figma too: the splash background and smoke, the logo, and
  the three phone screens.
- **Real status bar.** The scene draws edge to edge under the system status bar; its top 40 dp is kept free for it.
  Nothing is painted in its place.
- **Music mixed live.** Six Ogg Opus stems (≈ 710 KB): bed, accents, the teaser song, its accents. The feed's wait
  muffles the bed, the swipe opens it, and card 2's song comes in on the music's next beat. Music stays off on silent /
  vibrate or when another app plays music.
- **Ads are the host's.** The library only says when (`A7Ads`): splash banner, interstitial after the splash, native #1
  under the G03 call, native #2 on G04. Slots show a loading skeleton and collapse when there is no ad.
- **Any phone screen**, 16:9 to 23:9: backgrounds bleed and chrome holds the edges.
- **Reduced motion**: with system animations off, entrances become fades.

Design tokens (ZEN, 154 Figma variables): [docs/DESIGN_TOKENS.md](docs/DESIGN_TOKENS.md). Rendering details:
[docs/IMPLEMENTATION_NOTES.md](docs/IMPLEMENTATION_NOTES.md) (written for 1.3.x; the native-art and screen-size parts
still hold). History: [CHANGELOG.md](CHANGELOG.md).

## Not verified

Feel and sound on a physical phone: the emulator renders on the CPU and has no audio. The swipe's haptics, the music's
sync with the picture, and the drag-opened lowpass need a real device.
