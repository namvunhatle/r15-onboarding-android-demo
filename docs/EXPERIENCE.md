# Experience

[Back to README](../README.md)

The onboarding as the user sees it, **1.6.6** (web variant 1.6 · swipe teaser). Scene ids match `A7Script.kt`.

## Flow

```text
Splash (5 s) → interstitial (app's ad) → re-entry → ring burst → trailer
   G01 catalog → G02 same song, any name → G03 the phone rings → feed: WAITS for a swipe
   → card 2 plays → fly into the phone → G04 Yours is next.
                                            /                \
                              Explore AI Ringtones      Browse ringtones
                               (app: paywall → AI)        (app: Home)
```

One stop for the user: the swipe on the feed (or a tap on its pill). Everything else runs on the music.

## Scenes

| Scene | What appears | Behaviour |
| --- | --- | --- |
| P01 · Splash | Logo, tagline, progress, "This action may contain ads.", banner slot | Progress opens at 80 % and eases to 100 % over 5 s |
| Interstitial | The app's ad | The trailer waits on its re-entry frame until the ad closes |
| P03 · Re-entry · P04 · Ring burst | Logo at rest, then rings, 4 genre tiles, a light | The light lands on the progress wave on beat 1 |
| G01 · Thousands of ringtones | Genre wall, phone with a song list | Countdown wave runs to beat 19 |
| G02 · Same song. Any name. | Phone (Morning Glow) + lyric card "Hey [name]" / "pick up, it's for you ♪" | The name reel rolls Sam → Emma → Jake (Mia peeks: there are more) |
| G03 · Rings for you. | Phone ringing, bubble “Hey Sam pick up…”, native #1 | Words light on the half beat; native #1 after the line lands |
| Feed (wait) | The phone shows the AI feed; card 2 peeks; pill "Swipe for the next ringtone" | Pauses. Glow breathes, one hint per bar; the bed is muffled |
| Card 2 | Summer Crush · Pop · for Emma | Lands on the music's next beat; its song takes over; EMMA lights |
| Fly-in | Camera into the phone | EMMA lifts off card 2 and becomes G04's Emma sticker |
| G04 · Yours is next. | 6 name stickers, "With any name you like ↓", two buttons, native #2 | Native #2 shows a skeleton until the app's ad arrives |

## Timing

Timeline seconds (time on the interstitial and in the wait excluded). 100 BPM: one beat = 0.6 s, beat 0 = 6.07 s.

| Event | Beat | Time |
| --- | ---: | ---: |
| Interstitial | — | 5.30 |
| Ring burst / drop | 0 | 6.07 |
| G01 | 1 | 6.67 |
| G02 · Sam / Emma / Jake | 6 / 8 / 10 | 9.67 / 10.87 / 12.07 |
| G03 | 12 | 13.27 |
| "Hey" · "Sam" · "pick up…" | 14 · 14½ · 15 | 14.47 · 14.77 · 15.07 |
| Native #1 | 17 | 16.27 |
| Feed · card 2 peeks + pill | 18 · 18½ | 16.87 · 17.17 |
| **Wait** | 19½ | 17.77 |
| Card 2 lands (on the music's next beat after the swipe) | 20 | 18.07 |
| EMMA lights | 20½ | 18.37 |
| Headline + wave leave · fly-in | 22½ · 23 | 19.57 · 19.87 |
| G04 · Emma sticker lands | 24 | 20.47 |
| Buttons live, caption | 26 | 21.67 |
| Native #2 slot | 27 | 22.27 |

## Audio

Instrumental music with synthesized cues; the names are visual, the music does not sing them. Starts when the
interstitial closes. Off on silent / vibrate, when another app plays music, or without audio focus. Fades out on
either button. Tracks: [CREDITS](CREDITS.md).

## What must not change in production

- The order of scenes, the single swipe stop, and the two exits (paywall only on the AI branch).
- The beat relationships: names, words and card 2's landing on the music.
- Two separate native placements; nothing tappable next to an ad; the swipe area clear of native #1.
- The brief return to the brand after the interstitial.
