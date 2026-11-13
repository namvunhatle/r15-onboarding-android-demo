package namvunhatle.r15.onboarding.core

import androidx.core.graphics.ColorUtils

/**
 * R15 Christmas 2026 skin for the onboarding. One switch: [enabled] = false gives the original v1.3.7 onboarding,
 * unchanged. Timeline, audio, layout, copy and ads are never touched by the skin.
 *
 * Status: PROPOSED, approved on the web preview (https://claude.ai/artifact/6MTjkaqsKAenpCx16UGAnU) on 2026-11-13.
 * Figma: R15 · Christmas, section `16847:3604`, kit `16849:17`.
 *
 * What it changes:
 * - Splash: Christmas background + top garland with the bell (`xmas_splash_bg`, `xmas_top_garland`), flush with the
 *   top edge, behind the status bar. The splash icon is the dressed Christmas icon (`xmas_logo_dressed`) that drops on
 *   a red ribbon from the garland, bounces, swings and bursts gold sparkles + snow ([XmasOverlay]). Whenever the demo
 *   shows the logo larger than splash size (zoom into the ad, after the ad) the round plain icon (`xmas_logo_round`) is
 *   used and the art fades as it grows. Rings turn gold, the progress fill is primary.
 * - Intro G01–G04, light touch: colours hue-mapped by [tint] (blue/indigo → wine red, violet → red, pink/magenta →
 *   gold, teal → pine); a garland without the bell under the status bar; gentle snow that never falls over the ads;
 *   the CTA decor on "Explore AI Ringtones" (primary CTA only); the G04 name stickers muted (42 %, half saturation,
 *   no shadow) so they no longer read as buttons.
 * - Home destination: themed Home screenshot and the one-time magic moment ([MagicMomentView]).
 *
 * Not changed: the interstitial and the two native ads (third-party content), the text, the timeline.
 */
object XmasTheme {
    /** Season switch. Turn off after Christmas 2026. */
    @JvmField var enabled = true

    /** Primary: one colour, no gradient. */
    val PRIMARY = 0xFFF6C453.toInt()

    /** Text and icons on primary. */
    val ON_PRIMARY = 0xFF4A0F14.toInt()

    /** Splash and burst rings (white in the base design). */
    val RING = 0xFFFFE296.toInt()

    /** Design dp: the garland's light string (ribbon anchor on the splash). */
    const val GARLAND_WIRE_Y = 22f

    /**
     * Christmas hue map for the intro (same function as the web preview's `XmasRecolor`), in HSL: blue/indigo
     * (200–260°) → wine red 350°, violet (260–300°) → red, magenta/pink (300–340°) → gold 0–42°, teal/cyan
     * (160–200°) → pine 145°. Neutrals (saturation < 12 %) and every other hue are untouched; alpha is kept.
     * Identity when [enabled] is false.
     */
    fun tint(color: Int): Int {
        if (!enabled) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        var h = hsl[0]; var s = hsl[1]
        if (s < 0.12f) return color
        val h0 = h
        when {
            h >= 200f && h < 260f -> { h = 350f; s *= 0.95f }
            h >= 260f && h < 300f -> h = 350f + (h - 260f) / 40f * 10f
            h >= 300f && h <= 340f -> h = (h - 300f) / 40f * 42f
            h > 340f && h < 350f -> h = 42f
            h >= 160f && h < 200f -> h = 145f
        }
        if (h == h0) return color
        if (h >= 360f) h -= 360f
        val out = ColorUtils.HSLToColor(floatArrayOf(h, s, hsl[2]))
        return (color and 0xFF000000.toInt()) or (out and 0xFFFFFF)
    }
}
