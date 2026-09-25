# Changelog

All Android builds share the v1.3.3 web prototype's timeline, audio and scene script. Releases: [GitHub](https://github.com/namvunhatle/r15-onboarding-android-demo/releases).

## Unreleased

Same fixes as branch `merge-module`, in both apps:

- Splash progress bar opens at **80 %** on the first frame, then eases to 100 % over the 5 s splash (was a linear 0 → 100 %).
- G01: the phone's box now runs below the screen (body and shadow, to y 1084). It stopped at the screen edge, so the rising overshoot lifted the cut edge and left a gap under the phone.
- G03: the G02 phone now shrinks onto the call phone's exact body (scale 0.516, +9.5 dp), so only the screen changes. Scaling to the sprite boxes (0.623) made the phone jump 17 % smaller at the swap.
- G03 bubble reads “Hey Sam calling…”, without the comma.

## 1.3.4 — design tokens

- Colours, type, corner radii and spacing reference the **ZEN variables** bound in the Figma A7 frames, instead of copied values. The pipeline is `tools/tokens/export_tokens.js`, then `a7_figma_tokens.json` (154 variables), then `tools/gen_tokens.py`. It generates `ZenTokens.kt` (`Zen`, `AdKit`, `Project`, `Aosp`, with Figma paths kept) and `zen_tokens.xml` for the XML layouts.
- **Visual-only values** are kept apart in `A7Visual.kt` / `a7_visual.xml`, and nowhere else. These are values Figma draws without a variable: splash art, glow geometry, gradients, shadows and device chrome. They also include v1.3.3 values the demo keeps over a token Figma now binds; each entry names that token. See [Design tokens](docs/DESIGN_TOKENS.md).
- The picture is unchanged. On a 20:9 emulator, all 34 screenshots across both apps are within 1/255 of 1.3.3-R. The difference comes from token opacities replacing hand-rounded ones.
- `versionCode` is now 2, so 1.3.4 installs over 1.3.3-R. The application IDs are the same.

## 1.3.3-R — responsive phones

- The scene fills 16:9 to 23:9 phone screens instead of letterboxing. Backgrounds bleed, the genre wall grows, and the status bar, headlines, splash progress and banner hold the screen edges.
- The interstitial is native (Figma `15560:138551`): the creative covers the screen, and the SDK chrome is drawn and anchored.
- Application IDs are `…compose.responsive` / `…views.responsive`.

## 1.3.3-native — native art

- Figma text, shapes, gradients, shadows and blurs are drawn in code instead of sprites. The layout is still a fixed 360 × 800 frame.

## 1.3.3 — sprites

- First Android port of the web prototype, with Figma-exported sprites. The code is kept on `archive/v1.3.3-sprites`.
