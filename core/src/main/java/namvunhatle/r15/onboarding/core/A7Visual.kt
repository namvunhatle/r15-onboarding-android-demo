package namvunhatle.r15.onboarding.core

/**
 * Visual-only colours: what the A7 design draws **without** a Figma variable behind it. Everything else comes from
 * [Zen] / [AdKit] / [Project] (ZenTokens.kt, generated from Figma).
 *
 * Two kinds, kept apart:
 * - **No token** — the Figma node has no variable (glows, gradients, shadows, the splash art, device chrome).
 * - **Kept from v1.3.3** — Figma binds a token here, but the v1.3.3 build this demo matches draws something else. The
 *   comment names the token, so production can switch to it.
 *
 * The full list with geometry (blur radii, gradient transforms, shadow offsets) is in docs/DESIGN_TOKENS.md.
 */
object A7Visual {
    private fun argb(a: Float, rgb: Int) = ((a * 255f + .5f).toInt() shl 24) or (rgb and 0xFFFFFF)

    /* ---------------- no token ---------------- */

    /** P01 › BG › R15: black 0→100 % ramp at 52 %, grid gradient stops, and Ellipse 182/183 (layer blur 121.1). */
    val SPLASH_RAMP = 0xFF000000.toInt()
    const val SPLASH_RAMP_OPACITY = 0.52f
    val SPLASH_GRID = intArrayOf(0xFF405231.toInt(), 0xFF282847.toInt(), 0xFF1B1B3D.toInt(), 0xFF474765.toInt())
    val SPLASH_GLOW = 0xFF51208D.toInt()

    /** G04 › Spotlight BG glows are not bound (G01–G03 are). Same values as Accent/Gradient/Default-Left and Support/Pink/Solid. */
    val SPOT_G04_MAIN = 0xFFBB4ABF.toInt()
    val SPOT_G04_RIM = 0xFFE854B2.toInt()

    /** Drop shadows: black at 45 % (phone), 35 % (stickers, lyric card, bubble). */
    val SHADOW_PHONE = argb(0.45f, 0)
    val SHADOW = argb(0.35f, 0)

    /** A7 / Phone: body and the punch-hole marker. */
    val PHONE_BODY = 0xFF0E0E12.toInt()
    val PHONE_HOLE = 0xFF050507.toInt()
    val PHONE_HOLE_RIM = 0xFF2A2A33.toInt()

    /** Status-bar punch hole (a real one is black; Figma marks the camera with a white dot). */
    val PUNCH = 0xFF000000.toInt()
    val PUNCH_LIGHT = 0xFF1D2230.toInt()
    val PUNCH_MID = 0xFF07080C.toInt()

    /** Ring burst flash: radial white 95 % → rgba(231,170,255,.55) → transparent accent. */
    val FLASH_CORE = argb(0.95f, 0xFFFFFF)
    val FLASH_MID = 0x8CE7AAFF.toInt()
    val FLASH_EDGE = 0x00BB4ABF

    /** Logo record glow, the burst dot (white, with a white inner glow), and the comet trail ghosts. */
    val ICON_GLOW = 0x33FFFFFF
    val DOT = 0xFFFFFFFF.toInt()
    val GHOST = 0xFFF3D6FF.toInt()

    /** Rings (splash mark, burst, G03): white; each ring's opacity is in Scene. */
    val RING = 0xFFFFFFFF.toInt()

    /** G03 bubble: the lit words' glow (two text shadows) and the white "Sam" chip. */
    val BUBBLE_GLOW_UNDER = argb(0.9f, 0xFFD6FF)
    val BUBBLE_GLOW_TOP = argb(0.95f, 0xFFFFFF)
    val BUBBLE_CHIP = 0xFFFFFFFF.toInt()

    /** 03 · Interstitial (SDK) › Ads progress: played yellow, rest white 30 % (lines carry no variable). */
    val AD_PROGRESS = 0xFFFFFF00.toInt()
    val AD_PROGRESS_REST = argb(0.3f, 0xFFFFFF)
    /** Behind the interstitial's rounded corners — the SDK window. */
    val AD_WINDOW = 0xFF000000.toInt()

    /** Native ad mock: each slot's demo tint (two requests, two ads) and the loading skeleton. */
    val NATIVE_TINT_1 = intArrayOf(0xFFE8ECFF.toInt(), 0xFFD9E2FF.toInt())
    val NATIVE_TINT_2 = intArrayOf(0xFFFFF1E3.toInt(), 0xFFFFE2C7.toInt())
    val NATIVE_SKELETON = 0xFFEEF0F3.toInt()

    /** Demo chrome, not the onboarding: letterbox behind the scene, replay button. */
    val LETTERBOX = 0xFF050507.toInt()
    val FAB = 0x990A0A0E.toInt()
    val FAB_RIM = 0x33FFFFFF

    /* ---------------- kept from v1.3.3 (Figma binds a token) ---------------- */

    /** Headline main lines: solid white. Figma: Color/Content/On-Dark-Overlay/Strongest (95.7 %). */
    val HEADLINE_WHITE = 0xFFFFFFFF.toInt()

    /** Splash mark rings: 18 % white. Figma: Color/Border/Overlay/Pale/Default (6.7 %) and Subtle/Default (10.2 %). */
    const val SPLASH_RING_OPACITY = 0.18f

    /**
     * Lyric card: opaque gradient, so the phone's own text cannot ghost through. Figma: fill
     * Color/Background/Support/Neutral/Pale (see-through, 3 %).
     */
    val CARD_TOP = 0xFF221C2C.toInt()
    val CARD_BOTTOM = 0xFF121018.toInt()

    /** Divider above native #1: white 8 %. Figma: Color/Border/Neutral/Pale/Default (#010101 at 6.3 %, dark). */
    val DIVIDER = 0x14FFFFFF

    /**
     * Native ad mock, v1.3.3 web values (its text sizes too). Figma binds: body On-White-Overlay/Base, badge
     * Warning/Solid with On-Brights text, Install Accent/Subtle with Content/Accent/Base, label Button-Label-L.
     */
    val NATIVE_BODY = 0xFF666666.toInt()
    val NATIVE_BADGE = 0xFFFBBD23.toInt()
    val NATIVE_BADGE_TEXT = 0xFF0D0D0D.toInt()
    val NATIVE_INSTALL = 0xFFFDE8F5.toInt()
    val NATIVE_INSTALL_TEXT = 0xFF7A1F84.toInt()
}
