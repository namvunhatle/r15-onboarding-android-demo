# Credits

[Back to README](../README.md)

## Design and prototype

R15 onboarding artwork and screen exports come from the R15 design project. The Android sequence was adapted from the React/Vite web prototype v1.3.3.

The repository includes the assets used by this demo. Figma source files and the web source project are not included.

## Music

Track metadata is stored in [tracks.json](../core/src/main/assets/tracks.json).

| Track | Artist | Source | Demo tempo |
| --- | --- | --- | ---: |
| Future Pop Upbeat | JonasBlakewood | [Pixabay](https://pixabay.com/music/future-bass-future-pop-upbeat-569721/) | 100 BPM |
| Pop Upbeat | The_Mountain | [Pixabay](https://pixabay.com/music/dance-pop-upbeat-upbeat-pop-576584/) | 100 BPM |
| 80s-Synth | JonasBlakewood | [Pixabay](https://pixabay.com/music/pop-80s-synth-583367/) | 90 BPM |

The demo uses processed excerpts mixed with synthesized sound cues. The bundled WAV files contain the resulting intro and loop mixes. Music selection remains part of the prototype; these credits do not establish clearance for a separate production release.

## Fonts

**Be Vietnam Pro** — The Be Vietnam Pro Project Authors.

- [Project](https://github.com/bettergui/BeVietnamPro)
- [SIL Open Font License 1.1 and copyright notice](licenses/BeVietnamPro-OFL.txt)

**Anton** — The Anton Project Authors. Used for the headlines on branch `native-vector`.

- [Project](https://github.com/googlefonts/AntonFont)
- [SIL Open Font License 1.1 and copyright notice](licenses/Anton-OFL.txt)

## Replay icon

**Lucide** — Lucide Icons and Contributors.

- [Project](https://github.com/lucide-icons/lucide)
- [License and attribution notices](licenses/Lucide-LICENSE.txt)

## Dependencies

The apps use AndroidX Core; the Compose build also uses AndroidX Activity and Jetpack Compose. Gradle supplies the build wrapper. Playwright is used only by the optional audio-bake tool. These projects retain their respective licenses.

No repository-wide open-source license has been assigned to the R15-specific code and artwork in this release. Third-party license notices apply to their respective components.
