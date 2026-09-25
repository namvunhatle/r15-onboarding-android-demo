# Design tokens

[Back to README](../README.md)

The A7 screens in Figma are built with **ZEN** variables. The code uses those variables by name. It does not copy their resolved values.

```text
Figma (variables bound in the A7 frames)
  └─ tools/tokens/export_tokens.js     read-only export
       └─ tools/tokens/a7_figma_tokens.json   snapshot in the repo: 154 variables and their resolved values
            └─ tools/gen_tokens.py
                 ├─ app-views/…/core/ZenTokens.kt      native drawing
                 └─ app-views/src/main/res/values/zen_tokens.xml   XML layouts: colors, dimens
```

Values without a variable are in [`A7Visual.kt`](../app-views/src/main/java/namvunhatle/r15/onboarding/core/A7Visual.kt) and its XML copy, [`a7_visual.xml`](../app-views/src/main/res/values/a7_visual.xml). No other source file contains a raw colour.

## Using a token

A token keeps its Figma path:

| Figma variable | Kotlin | XML |
| --- | --- | --- |
| `Color/Background/Accent/Solid/Default` | `Zen.Color.Background.Accent.Solid.Default` (ARGB `Int`) | `@color/zen_color_background_accent_solid_default` |
| `Corner-Radius/2XLarge` | `Zen.CornerRadius._2XLarge` (dp) | `@dimen/zen_corner_radius_2xlarge` |
| `Typography/Font-Size/Heading-4` + line height + letter spacing | `ZenText.Heading4` (`TextToken`) | `@dimen/zen_typography_font_size_heading_4` |
| Ad SDK kit `content/ct-emphasis` | `AdKit.Content.CtEmphasis` | `@color/adkit_content_ct_emphasis` |

- **Letter spacing** tokens are in Figma pixels. Divide by the font size for Android's `em` values.
- **Values** are what each variable resolves to in the locked ZEN modes: Light, Global - Base 14, Brand Emphasis - S1 and Base Colors Zen.
- **Other token sets:** `AdKit` holds the ad SDK kit's variables, used by the interstitial only. `Project` holds the R15 file's own variables. `Aosp` holds the Material status-bar kit.

**Regenerating after a Figma change.** Export again with `tools/tokens/export_tokens.js`; the file's header explains how. Then run `python3 tools/gen_tokens.py`. Do not edit `ZenTokens.kt` or `zen_tokens.xml` by hand.

**Pixel check.** Switching to tokens moved some opacities by at most 1/255. Examples: 96 % → the token's 95.7 %, and 6 % → 6.3 %. On a 1080 × 2400 emulator, 34 screenshots across both apps differ from 1.3.3-R by at most 1/255 per channel.

## What uses which token

| Element | Tokens |
| --- | --- |
| Tagline, disclaimer | `On-Dark-Overlay/Strongest`, `/Base`; Heading-4, Caption |
| Splash progress | `Background/Neutral/Subtle/Default`, `Background/Active/Accent/Solid` |
| Banner ad | `White-Solid`, `Support/Neutral/Subtle`, `On-White-Overlay/Strongest` and `/Base`, `Warning/Solid`, `On-Brights`; Body-Small, Caption; radius Small |
| Status bar | `Project.White`, `Content/On-Colors` |
| Spotlights G01–G03 | base `Support/Neutral/Deep`; glows `Accent/Gradient/Default-Left` and `/Right`, `Support/Pink`, `/Violet`, `/Teal`, `/Blue` `Solid` |
| Headline echoes | Same colour tokens per screen; size Display-1, tracking ALL-CAPS-M; line 2 `On-Dark-Overlay/Strongest` |
| Genre tiles | `Support/*/Solid` → `Support/*/Deep`; title `On-Dark-Overlay/Strongest`, Heading-1; radius Large; padding Medium |
| Phone | rim `Border/Overlay/Subtle/Default`; radii Giant and 2XLarge |
| Name stickers | `White-Solid`, stroke `Support/{Yellow,Cyan,Pink,Green}/Solid`, text `On-White-Overlay/Strongest`, Heading-4; padding Medium |
| G04 name slot, CTA, secondary | `Border/Accent/Solid`; `Button-Primary/Background` and `/Content`, `Shadow/Neutral/Light`; Button-Label-XL and -L; `On-Dark-Overlay/Base` |
| Lyric card | border `Border/Overlay/Subtle`; tag `Tag/Background`, `Tag/Border`, `Content/Neutral/Strongest`; pill `Accent/Solid`, `On-Accent`; text Body-Base, Heading-2, Heading-4, `On-Dark-Overlay/Strongest`, `/Base` and `/Light`; radii 2XLarge and Base; padding and gaps |
| Bubble | `Accent/Solid/Default`, `On-Accent/Default` |
| Wave | `Active/Accent/Solid`, `On-Dark-Overlay/Strongest`, `/Disabled`; timecode `/Base` and Body-Base size |
| Native ad | `White-Solid`, headline `On-White-Overlay/Strongest`; radii Large and Small |
| Interstitial | `AdKit.Background.BgOverlay`, `AdKit.Content.CtEmphasis` and `CtMedium`, AdKit label-md type; radius 2XLarge |

## Visual-only values: no token in Figma

These are drawn without a variable in Figma, so they stay literal. The colours are in `A7Visual`. The geometry stays with the drawing code (`FigmaArt.kt`, `A7Glow.kt`, the renderers).

| Element | Values | Figma |
| --- | --- | --- |
| **Splash background** | 52 % black ramp; grid gradient `#405231 → #282847 → #1B1B3D → #474765` with its `gradientTransform`; two `#51208D` ellipses at layer blur 121.1; image fill and 40 % smoke photo (bitmaps) | `P01 › BG › R15` (`15552:118420`) |
| **Spotlight glows: geometry** | main 520 × 420 at 70 %, blur 170; rim 240 × 200 at 35 %, blur 110; top per screen. The colours are tokens. | `Glow · main / rim` |
| **G04 spotlight colours** | `#BB4ABF` main, `#E854B2` rim. They match two tokens, but Figma does not bind them. | `G04 › Spotlight BG` |
| **Blur model** | σ = 0.42 × Figma radius, baked at startup (`A7Glow`) | calibration, not design |
| **Genre tile gradient** | transform `[0.8, 0.6, −0.2; −0.6, 0.8, 0.4]`, 12° wall rotation | `A7 / Genre Tile` |
| **Drop shadows** | phone 0 24 48 at 45 % in Figma, drawn as 0 8 16 at 35 % (see `A7Visual.SHADOW_PHONE`); stickers 0 8 16 at 35 %; lyric card 0 20 40 at 35 %; bubble 0 12 24 at 35 % | effects are not bound |
| **Phone body** | `#0E0E12`; punch hole `#050507` / `#2A2A33`; inset 8 | `A7 / Phone` |
| **Headline type** | Anton, 92 % line height, 1.5 px echo strokes, echo offsets | family and line height are not bound |
| **Ring burst flash** | radial: white 95 % → `rgba(231,170,255,.55)` at 35 % → transparent accent at 70 % | web v1.3.3 |
| **Rings** | burst 55 / 30 / 14 %, G03 28 %, emit 45 % white | web v1.3.3 |
| **Logo glow, burst dot, comet ghosts** | `#33FFFFFF` glow; white dot; ghost `#F3D6FF` | web v1.3.3 |
| **Bubble glow** | text shadows: `#FFD6FF` at 90 % blur 22 and white at 95 % blur 10; white "Sam" chip | web v1.3.3 |
| **Interstitial chrome** | progress yellow `#FFFF00` + white 30 %, 4 px round caps; "Skip ads" tracking +4 %; black window behind the 24 radius | `03 · Interstitial (SDK)` |
| **Native ad mock** | per-slot tints (`#E8ECFF/#D9E2FF`, `#FFF1E3/#FFE2C7`); skeleton `#EEF0F3` | demo only |
| **Demo chrome** | letterbox `#050507`, replay button | not part of the design |

## Kept from v1.3.3: Figma binds a token, the demo draws something else

The demo matches the v1.3.3 build 1:1. Where the Figma file has since bound a different token, the code keeps the v1.3.3 value in `A7Visual` and names the token. Production should use the token.

| Element | Demo draws | Figma token |
| --- | --- | --- |
| Headline main lines | solid white | `On-Dark-Overlay/Strongest` (95.7 %) |
| G02c headline echoes | the G02b pink / violet stack | `Support/Teal/Solid`, `Support/Blue/Solid` |
| Splash mark rings | white 18 % | `Border/Overlay/Pale` (6.7 %), `/Subtle` (10.2 %) |
| Settled phone radii | hero radii × 0.52 | `Corner-Radius/XLarge` and `Large` |
| Lyric card fill | opaque `#221C2C → #121018`, so the phone text does not show through | `Support/Neutral/Pale` (3 %) |
| Bubble text | 18 / 24 −0.4 | Body-Base 14 / 20 −0.32 |
| Wave timecode | no letter spacing | Body-Base −0.32 |
| Divider | white 8 % | `Border/Neutral/Pale/Default` (`#010101` at 6.3 %, dark on dark) |
| Native ad | body `#666666`, badge `#FBBD23` with `#0D0D0D` 11 px, Install `#FDE8F5` / `#7A1F84` 16 / 24 | `On-White-Overlay/Base`, `Warning/Solid` + `On-Brights` + Caption, `Accent/Subtle` + `Content/Accent/Base` + Button-Label-L |
