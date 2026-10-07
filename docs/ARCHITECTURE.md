# Architecture

[Back to README](../README.md)

How `A7OnboardingView` runs. Paths are under `a7onboarding/src/main/java/namvunhatle/r15/onboarding/`.

## One frame

```text
Choreographer vsync
  └─ A7OnboardingView.doFrame
       ├─ A7Player.frame(t)          advance the timeline: frame clock, slaved to A7Mixer's audio clock once music plays
       │    └─ Timeline → Store      every element's x, y, scale, rotation, opacity (+ 3 scalars: zoom k, progress p, pointer)
       └─ apply()                    copy each Store element onto its view (by android:tag) · redraw the lyric card and the feed
```

Nothing animates by itself: views are moved only by `apply()`, so the whole trailer is one deterministic timeline.
Views are tagged in `res/layout/a7_onboarding.xml`; the tag is the element id used in `A7Script`.

## Files

| File | Role |
| --- | --- |
| `A7OnboardingView.kt` | Public. Builds the scene, binds views to the store, touch (swipe, CTAs), ad slots, lifecycle |
| `A7Ads.kt` | Public. The ad contract the app implements |
| `core/A7Script.kt` | The trailer: a line-by-line port of the web's GSAP master timeline (1.6.6). `A7Times` = the beat map |
| `core/A7Player.kt` | Runs the timeline; the 1.6 wait (glow, hints), drag / commit, the time-stretch onto the music's beat; haptic cues |
| `core/A7Mixer.kt` | Music: decodes 6 Opus stems during the splash, mixes them live into one `AudioTrack`; its timestamp is the clock |
| `core/Timeline.kt`, `Ease.kt` | GSAP-like forward-only timeline (fromTo / to / set / call / pause) and eases |
| `core/Scene.kt`, `Viewport.kt` | Boxes of every element in the 360 × 800 frame (`assets/a7/manifest.json` + constants), adapted to the screen |
| `core/A7Native.kt`, `FigmaArt.kt` | Figma elements drawn in code, one `Drawable` per element, in the element's own box |
| `core/A7Art.kt`, `A7Glow.kt`, `Bleed.kt`, `Later.kt` | Glows and blurs baked once at start, on a 3-thread pool, before the layout inflates |
| `core/ZenTokens.kt`, `A7Visual.kt` | ZEN tokens (generated, see [DESIGN_TOKENS](DESIGN_TOKENS.md)) · visual-only values |
| `views/Widgets.kt` | `DesignFrame` (scales the frame to the screen), `CssBox`, `RingView`, `BakedView`, `WaveView`, `LyricView`, `FeedView`, `RadialView` |

## The 1.6 wait and the swipe

At beat 19½ the timeline pauses on the feed (`A7Script` → `onWait`). While it waits, `A7Player` runs two side timelines:
the call glow breathing (sine, 2 beats) and one hint per bar on the music's downbeat (stack nudge + touch dot).

- **Drag:** the finger moves `feed_stack` 1:1 (rubber band downward), fades the pill, and opens the bed's lowpass.
- **Commit** (70 dp, a fling > 0.45 dp/ms, a tap, or the pill as a button): `A7Mixer.commit()` picks the music's next
  beat ≥ 0.3 s away. The timeline's beat 19½ → 20 is time-stretched to land exactly on it; the mixer fades the bed into
  that beat and starts card 2's song and accents on it. From then on the timeline runs `shift` behind the music.
- The swipe area (y 150–515) ends 9 dp above native #1. G04's buttons take touches only once they are live.

## Audio

Six stems in `assets/a7/audio/`, rendered offline by the web prototype's own Web Audio graph (`tools/bake/stems.html`):

| Stem | Content |
| --- | --- |
| `bed_intro` / `bed_loop` | Future Pop, with its ducking; then bars 5–8 looping (the wait and G04) |
| `sfx_intro` | every accent before the swipe (plucks, whooshes, rings, ticks, pops) |
| `teaser_intro` / `teaser_loop` | Pop Upbeat from its drop — card 2's song, which carries on into G04 |
| `sfx_tail` | after the swipe: name pluck, riser, boom on G04, sticker pops |

`A7Mixer` sums them per 5 ms block: lowpass on the bed (20 kHz → 900 Hz over b17½–b19½, then the drag), lowpass +
gain dip on the teaser under the riser, then a limiter shaped like Web Audio's compressor (with its makeup gain), so
levels match the web. Accent stems are stored at ×0.5 and multiplied back.

Music needs audio focus, ringer mode normal and no other music playing; otherwise the trailer runs silent on the frame
clock. Rebuild after changing a sound (needs Node, Playwright, ffmpeg with libopus, and the web prototype folder):

```sh
cd tools/bake && npm install && node stems.mjs /path/to/prototype-a7_versions/1.6.0-swipe-teaser /tmp/a7-stems
cd ../.. && tools/encode_stems.sh /tmp/a7-stems
```

`encode_stems.sh` prints each stem's frame count: copy them into `A7Mixer.STEMS` (decoded PCM is trimmed to exactly
that length, so beats and loops stay sample-accurate).

## Rendering

- **Drawn in code**: Anton headlines with echoes, Be Vietnam Pro text with Figma tracking, gradients from Figma's
  `gradientTransform`, drop shadows (σ = blur / 2), layer blurs baked at start (σ = 0.42 × radius), phone frames,
  the lyric card's reel (`LyricView`), the feed cards (`FeedView`: card 2's cover is pure gradients).
- **Bitmaps (6)**: splash image fill and smoke, logo, the three phone screens — images in Figma too.
- **Per-frame cost**: each native element is drawn once into its own GPU layer; afterwards the timeline only
  transforms and fades it.
- **Text** uses fixed dp sizes: the frame is art-directed and scaled as a whole, so it ignores the font scale.

## Screen sizes

The 360 × 800 frame keeps its scale (fit, never distorted). On a 20:9 phone nothing moves. Elsewhere the extra screen
(sides on 16:9, top / bottom on 21:9, up to 60 dp) is filled: backgrounds bleed, the genre wall adds tiles, headlines
and the wave hold the top edge, the splash progress and banner hold the bottom. Beyond that (tablets, landscape) the
rest is letterboxed.

## Lifecycle

`start()` once. Leaving while the trailer runs (home, call, lock) restarts it the next time the view is visible — except
while the interstitial is up (a real SDK shows its own activity), once G04 is reached, or after a CTA. Detaching the
view releases the audio and calls `A7Ads.release()`.

## Debugging

```sh
adb shell am start -n namvunhatle.r15.onboarding.sample/.MainActivity --ef t 12.4   # sample only: open frozen at 12.4 s
```

`A7OnboardingView.seekFrozen(t)` does the same from code. It suppresses callbacks and audio: a visual review tool.

## Verified / not verified

Verified on API 36 emulators at 1080 × 2400 and 1080 × 1920 (CPU rendering, no audio): the whole path — splash, ad,
trailer, wait, drag, commit, fly-in, G04, both exits, native #2's skeleton → ad. Lint: 0 errors.

Not verified: sound and its sync with the picture, haptics, frame pacing on a physical phone, foldables, multi-window.
