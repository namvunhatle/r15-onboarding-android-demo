# 5 · Rules that must not change

[Integration guide](README.md) · previous: [Analytics](04-analytics.md) · next: [Performance](06-performance.md)

These are product and design decisions, not implementation details. Each one looks like something to simplify, and each has a reason. To change one, ask product or design first. Do not change it in code.

## Flow and monetisation

| Rule | Why |
| --- | --- |
| The splash lasts a **fixed 5 s**. | It is the load window for the interstitial and the banner. |
| **Two native ads inside the onboarding**: #1 in G03, after the "Hey Sam calling…" line lands; #2 in G04, below the actions. | Product requirement. The positions are product's choice. |
| Native #2 is a **separate request**, with its loading placeholder. | Two slots show two different ads, and the placeholder shows the second one as a new load. |
| A paywall inside the onboarding, **only on the AI branch**. Browse ringtones → Home, no paywall. | A paywall inside the onboarding is required. On the browse path it would stand between the user and Home, which is goal 1 ([Analytics](04-analytics.md#the-two-goals)). |
| **Ads are never presented as a Premium benefit**: no "No ads" row, headline or tier on any paywall. | Product decision. The demo's paywall screenshot breaks it ("No ads, ever"), and that screenshot is not the spec. |
| **No skip button** in the trailer. "Skip ads" closes only the interstitial. | Product decision. The progress wave and its countdown show how much is left. |

## Sound

| Rule | Why |
| --- | --- |
| Instrumental music, one track (Future Pop Upbeat). | Product decision: no vocals, not a track from the app's catalog. |
| **No mute or speaker toggle.** Music plays only with the ringer on normal and no other app playing music, and stops when another app takes audio focus (`A7Audio.allowed()`). | Product decision. Sound follows the phone's own state instead of a control on screen. |
| The accents (pluck, whoosh, pop, ring, tick) are baked into the mix, louder than the music. | They tell the user what the motion means. Keep the mixed files; do not re-mix them in code. |
| After the drop, **the picture follows the audio clock** (`A7Player.frame`: `AudioTrack` timestamps, at most ±25 % rate correction). | Every event sits on the 100 BPM beat grid (0.6 s). A separate timer drifts off the beat within seconds. |

## Motion and art

| Rule | Why |
| --- | --- |
| Do not retime or re-ease single events. Timing changes go through the beat map in `A7Script`. | The beat map is the design. One moved event breaks the beat grid. |
| Elements **appear small and grow**; they never pop in larger and shrink back. | A later version broke these four rules and was rejected as "rough". Measured: sudden jumps went from 61 to 117, and frames with five or more moving elements from 35 to 86. |
| **No stepwise effects** (typing, a jumping cursor). | Same. |
| **One event per half beat**; no stacking of events on one beat. | Same. |
| An element **settles before it leaves**. | Same. |
| Animate **transform and opacity only**. Blurs and glows are baked; a blur is a crossfade to a pre-blurred copy (`logoB`), never an animated radius. | A live blur of this size costs a full-screen pass per frame. |
| The phone screens inside the trailer show a **fixed scene** (cover art "Morning Glow"), not the user's real templates. | Design decision: the trailer shows the idea, not the user's data. |
| Headlines use **Anton**, outside the ZEN design system. | Design decision, pending the design-system owner's sign-off. Do not swap in a ZEN font. |
| With "Remove animations" on, only fades remain (`A7Player.reduced`). | Accessibility. The demo's support is partial: some splash movement remains. |
