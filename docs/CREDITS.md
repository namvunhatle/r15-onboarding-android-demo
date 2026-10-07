# Credits

[Back to README](../README.md)

## Design and prototype

R15 onboarding artwork and screen exports come from the R15 design project. The Android sequence was adapted from the React/Vite web prototype 1.6.6.

The repository includes the assets used by this demo. Figma source files and the web source project are not included.

## Music

Stems and their lengths: `A7Mixer.STEMS` in `a7onboarding/…/core/A7Mixer.kt`.

| Track | Artist | Source | Demo tempo |
| --- | --- | --- | ---: |
| Future Pop Upbeat | JonasBlakewood | [Pixabay](https://pixabay.com/music/future-bass-future-pop-upbeat-569721/) | 100 BPM — the trailer's bed |
| Pop Upbeat | The_Mountain | [Pixabay](https://pixabay.com/music/dance-pop-upbeat-upbeat-pop-576584/) | 100 BPM — card 2's song |

The demo uses processed excerpts mixed with synthesized sound cues. The bundled Ogg Opus files are stems (music, cues) mixed on the device. Music selection remains part of the prototype; these credits do not establish clearance for a separate production release.

## Fonts

**Be Vietnam Pro** — The Be Vietnam Pro Project Authors.

- [Project](https://github.com/bettergui/BeVietnamPro)
- [SIL Open Font License 1.1 and copyright notice](licenses/BeVietnamPro-OFL.txt)

**Anton** — The Anton Project Authors. Used for the headlines.

- [Project](https://github.com/googlefonts/AntonFont)
- [SIL Open Font License 1.1 and copyright notice](licenses/Anton-OFL.txt)

**Mona Sans** — The Mona Sans Project Authors. Used by the sample's mock interstitial "Skip ads" (static Medium instance, ASCII subset); not in the library.

- [Project](https://github.com/github/mona-sans)
- [SIL Open Font License 1.1 and copyright notice](licenses/MonaSans-OFL.txt)

## Icons

**Lucide** — Lucide Icons and Contributors.

Replay (sample) and the swipe pill's chevron (library).

- [Project](https://github.com/lucide-icons/lucide)
- [License and attribution notices](licenses/Lucide-LICENSE.txt)

## Dependencies

The library and the sample use AndroidX Core only. Gradle supplies the build wrapper. Playwright is used only by the optional audio-bake tool. These projects retain their respective licenses.

No repository-wide open-source license has been assigned to the R15-specific code and artwork in this release. Third-party license notices apply to their respective components.
