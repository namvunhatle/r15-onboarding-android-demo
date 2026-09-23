# Experience

[Back to README](../README.md)

This document describes the behavior in **v1.3.3**. Scene IDs match the names used in the animation script and exported assets.

## Flow

```text
Splash → mock interstitial → Skip ads → re-entry → animated introduction
                                                     ↓
                                              Yours is next.
                                               /           \
                                Explore AI Ringtones    Browse ringtones
                                         ↓                    ↓
                                    Paywall mock          Home mock
                                         ↓
                           Close or Subscribe → AI mock
```

The introduction advances automatically. It has no skip control. **Skip ads** closes only the mock interstitial.

## Scenes

| Scene | What appears | Motion and behavior |
| --- | --- | --- |
| P01 · Splash | Logo, tagline, progress strip, and ad artwork | The logo enters and pulses. The progress strip fills over five seconds. |
| P02 · Ad transition | Enlarging logo | The logo expands and dissolves before the interstitial covers the scene. |
| Interstitial | Full-screen ad image and Skip ads tap area | The timeline pauses until the reviewer taps Skip ads. There is no ad SDK. |
| P03 · Re-entry | Logo at its resting size | A nominal 450 ms hold gives the sequence a visual restart after the ad. Audio startup can extend this hold. |
| P04 · Ring burst | Expanding rings, genre tiles, and a moving light | The light moves toward the progress wave while the tiles assemble into the catalog wall. |
| G01 · Thousands of ringtones | Catalog tiles and a phone preview | The phone rises into view. The progress wave counts down toward G04. |
| G02a · Same song. | Song card and Sam name label | The card enters, followed by the name label. |
| G02b/c · Any name. | Emma and Jake labels, plus name stickers | The names swap on the beat. Sound cues mark each change. No song is generated. |
| G03 · Rings for you. | Incoming-call preview and “Hey Sam calling…” bubble | Ring pulses and word highlights build the call example. Native ad #1 enters after the phrase lands. |
| G04 · Yours is next. | Name stickers, two actions, and native ad #2 | The actions enter. The second ad shows a loading placeholder before its content appears. The scene remains open for a choice. |

## Destinations

| Action | Destination | What the demo actually does |
| --- | --- | --- |
| Explore AI Ringtones | Paywall | Slides in a screenshot with close and subscribe tap areas |
| Close paywall | AI Ringtones | Opens the AI screen screenshot |
| Subscribe | AI Ringtones | Opens the same screenshot; no billing flow |
| Browse ringtones | Home | Opens the Home screenshot directly |

The destination screenshots end the demo. Catalog browsing, ringtone setup, generation, and subscription management are outside its scope.

## Timing

Times below are **timeline seconds**, not elapsed time since launch. Time spent waiting on the interstitial is excluded. Audio startup and frame scheduling can also affect elapsed time.

The default track runs at **100 BPM**, so one beat is **0.6 seconds**. The alternate 80s-Synth track uses 90 BPM and changes the beat-based timings.

| Event | Timeline time at 100 BPM |
| --- | ---: |
| Splash progress completes | 5.00 s |
| Mock interstitial appears | 5.30 s |
| Timeline pauses for Skip ads | 5.31 s |
| Re-entry begins | 5.32 s |
| Fly-through begins | 5.77 s |
| Ring burst / musical drop, beat 0 | 6.07 s |
| G01, beat 1 | 6.67 s |
| G02a heading, beat 5 | 9.07 s |
| Sam, beat 6 | 9.67 s |
| Emma, beat 8 | 10.87 s |
| Jake, beat 10 | 12.07 s |
| G03, beat 12 | 13.27 s |
| “Hey”, beat 14 | 14.47 s |
| “Sam”, beat 14.5 | 14.77 s |
| “calling…”, beat 15 | 15.07 s |
| Native ad #1, beat 17 | 16.27 s |
| G04, beat 20 | 18.07 s |
| Primary action becomes available, beat 22 | 19.27 s |
| Secondary action enters, beat 22.5 | 19.57 s |
| Native ad #2 loading placeholder enters, beat 23 | 19.87 s |
| Native ad #2 placeholder starts fading, beat 24.5 | 20.77 s |

The second ad's content is fully revealed about 0.35 seconds after its placeholder starts fading. The final scene continues with idle movement until a destination is chosen.

Exact timings and easing curves are in [A7Script.kt](../core/src/main/java/namvunhatle/r15/onboarding/core/A7Script.kt). The script is the source for implementation details when a summary here is insufficient.

## Audio

The demo uses instrumental background music with baked plucks, swaps, pops, ring sounds, and word accents. The name examples are visual; the music does not sing the names.

Playback is requested after Skip ads. It remains off if the device is in silent/vibrate mode or another app is already playing music. It also requires audio focus. Choosing a destination fades the audio out.

Track selection is for review. The included tracks and credits are listed in [Credits](CREDITS.md).

## What to preserve in a production implementation

- The order of the scenes and the two destination paths.
- The brief return to the brand after the interstitial.
- The relationship between the musical beats and the name/call transitions.
- The separation between the two native-ad placements.
- The primary and secondary choices on the final scene.

Responsive layout, system bars, accessibility, real ad behavior, and billing need their own implementation decisions. The prototype's fixed canvas and screenshot tap areas do not define those solutions.
