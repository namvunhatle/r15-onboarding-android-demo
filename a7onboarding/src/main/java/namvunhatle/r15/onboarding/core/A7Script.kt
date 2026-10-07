package namvunhatle.r15.onboarding.core

import namvunhatle.r15.onboarding.core.Ease.Companion.backOut
import namvunhatle.r15.onboarding.core.Scene.Companion.BGS
import namvunhatle.r15.onboarding.core.Scene.Companion.BRINGS
import namvunhatle.r15.onboarding.core.Scene.Companion.EMITS
import namvunhatle.r15.onboarding.core.Scene.Companion.G04_STICKERS
import namvunhatle.r15.onboarding.core.Scene.Companion.G3RINGS
import namvunhatle.r15.onboarding.core.Scene.Companion.GHOSTS
import namvunhatle.r15.onboarding.core.Scene.Companion.HEADS
import namvunhatle.r15.onboarding.core.Scene.Companion.LX
import namvunhatle.r15.onboarding.core.Scene.Companion.LY
import namvunhatle.r15.onboarding.core.Scene.Companion.MINIS
import namvunhatle.r15.onboarding.core.Scene.Companion.MINI_WALLS
import namvunhatle.r15.onboarding.core.Scene.Companion.SRINGS
import namvunhatle.r15.onboarding.core.Scene.Companion.WAVE_BARS
import namvunhatle.r15.onboarding.core.Scene.Companion.EMMA_X
import namvunhatle.r15.onboarding.core.Scene.Companion.EMMA_Y
import namvunhatle.r15.onboarding.core.Scene.Companion.FEED_FIT
import namvunhatle.r15.onboarding.core.Scene.Companion.FEED_PEEK
import kotlin.math.hypot
import kotlin.math.ln

/**
 * The A7 trailer — a line-by-line port of the master timeline in prototype-a7 1.6.6 `src/App.tsx`
 * (variant 1.6 · swipe teaser). Same beat map, same times, same eases. Comments keep the web's wording where it
 * explains a choice.
 *
 * Everything after the drop sits on the track's beat grid (bar = 4 beats):
 *   drop ─ G01 +1 ─ G02a +5 (card +5½, Sam +6) ─ G02b +8 ─ G02c +10 ─ G03 +12 ─ feed +18 ─ WAIT +19½
 *   ─ (swipe) card 2 lands +20 ─ chrome leaves +22½ ─ fly-in +23 ─ G04 +24
 * After the wait the timeline runs on: [A7Player] lands +20 on the music's next beat and shifts its audio clock.
 */
class A7Times(bpm: Int) {
    val beat = 60.0 / bpm
    fun b(n: Double) = T_DROP + n * beat
    val tG01 = b(1.0)
    val tG02a = b(5.0)
    val tG03 = b(12.0)
    val tWait = b(N_WAIT)
    val tPlay = b(N_PLAY)
    val tG04 = b(G4)
    val tEnd = tG04 + 1.4
    /** The countdown runs G01 → b19 and reaches 0 just before the feed stops. */
    val tCount = b(N_ASK) - tG01

    companion object {
        const val T_ZOOM = 4.4 // splash logo starts zooming (P02)
        const val T_AD = 5.3 // P02 held, then interstitial.show()
        const val T_PAUSE = T_AD + 0.01 // timeline waits here for "Skip ads"
        const val T_RE = T_AD + 0.02 // ad closed → P03 re-entry: splash logo back at rest (brand anchor)
        const val T_BURST = T_RE + 0.45 // 450 ms still hold, then P04 (ref `15560:138340`)
        const val T_DROP = T_BURST + 0.3 // the music's drop = the ring burst

        // v1.6 beats: the call ends and the phone shows the AI feed · next card peeks · countdown 0 · pause ·
        // card 2 lands (on the music's next beat after the swipe) · chrome leaves · fly into the phone · G04
        const val N_FEED = 18.0
        const val N_HINT = 18.5
        const val N_ASK = 19.0
        const val N_WAIT = 19.5
        const val N_PLAY = 20.0
        const val N_CLEAR = 22.5
        const val N_FLY = 23.0
        const val G4 = 24.0
    }
}

object A7Script {
    // G02 → G03 phone hand-off, from the phone bodies in A7Native (g02: 232 wide at y 192 in a box from y 168,
    // origin top-centre; g03: 119.68 wide at y 189.9).
    private const val G03_FIT = 119.68 / 232.0
    private const val G03_DY = 189.9 - (168.0 + (192.0 - 168.0) * G03_FIT)
    /** Lyric-card name reel: Sam → Emma → Jake, Mia only peeks. One slot = 46 dp. */
    val REEL = listOf("Sam", "Emma", "Jake", "Mia")
    const val REEL_H = 46f
    private const val GHOST_OPACITY = 0.28f
    private const val GHOST_SCALE = 0.78f

    fun build(tl: Timeline, scene: Scene, times: A7Times, reduced: Boolean, onAd: () -> Unit, onWait: () -> Unit, onNative2: () -> Unit, onIdle: () -> Unit) {
        val R = reduced
        val TILES = scene.tiles
        val REST_TILES = scene.restTiles
        // the dot lands on the first wave bar
        val DOT_WX = scene.wave.x - 8f + 1f
        val DOT_WY = scene.wave.y + 14f - 8f
        val t = tl
        fun b(n: Double) = times.b(n)
        fun one(id: String) = listOf(id)
        val beat = times.beat

        // Appear / disappear helpers. Reduced motion keeps only the opacity half.
        fun inn(ids: List<String>, at: Double, from: V, dur: Double = 0.5, ease: Ease = backOut(1.8f), stagger: Double = 0.0) =
            if (R) t.fromTo(ids, v("autoAlpha" to 0), v("autoAlpha" to 1), at, 0.25, Ease.none)
            else t.fromTo(ids, v("autoAlpha" to 0) + from, v("autoAlpha" to 1, "x" to 0, "y" to 0, "scale" to 1, "rotation" to 0), at, dur, ease, stagger)
        fun out(ids: List<String>, at: Double, to: V, dur: Double = 0.3, ease: Ease = Ease.power2In, stagger: Double = 0.0) =
            if (R) t.to(ids, v("autoAlpha" to 0), at, 0.2)
            else t.to(ids, v("autoAlpha" to 0) + to, at, dur, ease, stagger)
        fun fadeIn(ids: List<String>, at: Double, dur: Double = 0.5) =
            t.fromTo(ids, v("autoAlpha" to 0), v("autoAlpha" to 1), at, dur, Ease.power1InOut)

        val logo = one("logo")
        val logoB = one("logoB") // pre-blurred copy: crossfading to it = a blur ramp with no per-frame filter work
        val icon = one("icon")
        val srings = SRINGS.map { it.id }
        val emits = EMITS.map { it.id }
        val brings = BRINGS.map { it.id }
        val g3rings = G3RINGS.map { it.id }

        // Everything after the splash starts hidden.
        t.init(
            BGS + HEADS + listOf("g01_phone", "g02_phone", "g03_phone", "g03_bubble", "nat1", "nat2") + G04_STICKERS +
                listOf("g04_center", "g04_cta", "g04_secondary", "g04cap", "carry_name") + TILES +
                listOf("wave", "card", "divider", "feedwrap", "swipe_pill", "touch", "callglow", "bloom") + brings + g3rings + emits + srings +
                listOf("flash", "dot") + GHOSTS + listOf("logoB"),
            v("autoAlpha" to 0),
        )
        t.init(listOf("hot_g04", "hot_swipe"), v("pointer" to 0))

        // Zoom is driven in log space (k = ln scale: equal ratio per frame = a steady camera push).
        // "Zoom up and dissolve": rings fly past first, the record swells, goes soft, fades out.
        fun dissolve(at: Double, dur: Double, reach: Double, ease: Ease) {
            t.fromTo(one("zoom"), v("k" to 0), v("k" to ln(reach)), at, dur, ease)
            t.to(srings, v("scale" to 1.8), at + dur * 0.15, dur * 0.85, Ease.power2In, stagger = 0.03)
            t.to(srings, v("autoAlpha" to 0), at + dur * 0.35, dur * 0.45, Ease.power1In, stagger = 0.03)
            // sharp → soft while it is already big, then the soft copy fades out as it keeps flying
            t.fromTo(logoB, v("autoAlpha" to 0), v("autoAlpha" to 1), at + dur * 0.5, dur * 0.2, Ease.power1Out)
            t.to(icon, v("autoAlpha" to 0), at + dur * 0.55, dur * 0.2, Ease.power1In)
            t.to(logoB, v("autoAlpha" to 0), at + dur * 0.7, dur * 0.3, Ease.power1In)
        }

        /* ---------------- P01 · Splash (0–5 s) ---------------- */
        // Record dropped onto the turntable: spins in, lands with overshoot.
        t.fromTo(icon, v("scale" to 0, "rotation" to -120, "autoAlpha" to 0), v("scale" to 1, "rotation" to 0, "autoAlpha" to 1), 0.05, 0.7, backOut(1.6f), immediate = true)
        inn(srings, 0.3, v("scale" to 0.4), 0.6, backOut(1.4f), stagger = 0.08)
        if (!R) {
            // Heartbeat on the beat grid: a thump + one ripple per beat.
            var i = 0
            while (0.9 + i * beat < A7Times.T_ZOOM - 0.2) {
                val at = 0.9 + i * beat
                t.to(icon, v("scale" to 1.09), at, 0.07, Ease.power2Out)
                t.to(icon, v("scale" to 1), at + 0.07, 0.33, Ease.power3Out)
                t.fromTo(one(emits[i % 2]), v("scale" to 1, "autoAlpha" to 0.55), v("scale" to 2.1, "autoAlpha" to 0), at, 0.9, Ease.power2Out)
                if (i % 2 == 0) {
                    t.to(srings, v("scale" to 1.04), at + 0.02, 0.08, Ease.power2Out, stagger = 0.04)
                    t.to(srings, v("scale" to 1), at + 0.1, 0.3, Ease.power2Out, stagger = 0.04)
                }
                i++
            }
        }
        t.fromTo(one("tagline"), v("y" to 12, "autoAlpha" to 0), v("y" to 0, "autoAlpha" to 1), 0.35, 0.5, Ease.power2Out, immediate = true)
        // Opens at 80 % on the first frame, then eases the last 20 % over the 5 s splash (user ruling 2026-09-25).
        t.fromTo(one("sp_fill"), v("scaleX" to 0.8), v("scaleX" to 1), 0.0, 5.0, Ease.power1Out, immediate = true)

        /* ---------------- P02 · zoom up and dissolve ---------------- */
        out(one("tagline"), A7Times.T_ZOOM - 0.2, v("y" to -8), 0.25)
        if (R) {
            t.to(logo, v("autoAlpha" to 0), A7Times.T_ZOOM, 0.3)
        } else {
            val tPush = A7Times.T_ZOOM - 0.1
            val push = A7Times.T_AD - 0.1 - tPush
            dissolve(tPush, push, 5.0, Ease.sineIn)
            t.fromTo(one("splash_bg"), v("scale" to 1), v("scale" to 1.08), tPush, push, Ease.power2In)
        }

        /* ---------------- Interstitial — the app's ad SDK shows it (A7Ads); the timeline waits until it closes ---------------- */
        t.call(A7Times.T_AD, onAd)
        t.addPause(A7Times.T_PAUSE)

        /* ---------------- P03 · Re-entry: splash logo back at rest, no chrome ---------------- */
        t.set(listOf("sp_track", "sp_note", "sp_strip"), v("autoAlpha" to 0), A7Times.T_RE)
        t.set(one("zoom"), v("k" to 0), A7Times.T_RE)
        t.set(logo + icon, v("autoAlpha" to 1), A7Times.T_RE)
        t.set(srings, v("autoAlpha" to 1, "scale" to 1), A7Times.T_RE)
        t.set(logoB, v("autoAlpha" to 0), A7Times.T_RE)
        t.set(one("splash_bg"), v("scale" to 1), A7Times.T_RE)
        // (web: music is scheduled here, T_BURST − 0.25. Android starts the audio stream when the ad closes instead,
        //  so the output latency is absorbed by this still hold — see A7Player.)

        /* ---------------- P04 · Ring burst — keyframe `15560:138820`, opened by a fly-through ---------------- */
        if (R) {
            t.to(logo, v("autoAlpha" to 0), A7Times.T_BURST, 0.25)
            fadeIn(brings, b(0.0), 0.2)
            t.to(brings, v("autoAlpha" to 0), b(0.0) + 0.45, 0.3)
        } else {
            // 1 · fly-through: same zoom-and-dissolve as P02, faster and deeper
            dissolve(A7Times.T_BURST, 0.5, 7.0, Ease.sineIn)
            t.fromTo(one("dot"), v("x" to LX - 8, "y" to LY - 8, "scale" to 0, "autoAlpha" to 1), v("scale" to 1.6), A7Times.T_BURST + 0.22, 0.14, Ease.power2Out)
            t.to(one("dot"), v("scale" to 1), A7Times.T_BURST + 0.36, 0.25, backOut(3f))
            t.fromTo(one("flash"), v("scale" to 0.2, "autoAlpha" to 0), v("scale" to 0.9, "autoAlpha" to 0.6), b(0.0) - 0.04, 0.12, Ease.power2Out)
            t.to(one("flash"), v("autoAlpha" to 0), b(0.0) + 0.08, 0.25, Ease.power1Out)
            // 2 · three thin rings: reach keyframe size inside the beat, then keep opening past the edges
            t.fromTo(brings, v("scale" to 0.3, "autoAlpha" to 0), v("scale" to 1, "autoAlpha" to 1), b(0.0) - 0.05, 0.42, Ease.power2Out, stagger = 0.04)
            t.to(brings, v("scale" to 1.6, "autoAlpha" to 0), b(0.0) + 0.4, 0.45, Ease.power1In, stagger = 0.04)
            // splash background dims under the burst instead of being wiped by an iris
            t.to(one("splash_bg"), v("opacity" to 0.45), b(0.0), 0.5, Ease.power1In)
            // 4 · dot flies to the wave with a comet trail during the burst — lands ON beat 1
            (listOf("dot") + GHOSTS).forEachIndexed { i, el ->
                val at = b(0.0) + 0.15 + i * 0.035
                val dur = b(1.0) - b(0.0) - 0.15
                if (i > 0) t.set(one(el), v("x" to LX - 8, "y" to LY - 8, "autoAlpha" to 0.5 - i * 0.12, "scale" to 1 - i * 0.2), at)
                t.to(one(el), v("x" to DOT_WX), at, dur, Ease.power2InOut)
                t.to(one(el), v("y" to DOT_WY), at, dur, Ease.power3Out)
                if (i > 0) t.to(one(el), v("autoAlpha" to 0), at + dur - 0.05, 0.1)
            }
            t.to(one("dot"), v("scale" to 0.3, "autoAlpha" to 0), b(1.0) + 0.05, 0.2, Ease.power2In)
        }
        // 3 · G01's spotlight comes in under the rings; the splash goes once it is covered
        fadeIn(one("bg_G01"), b(0.0) + 0.25, 0.4)
        t.set(listOf("splash_bg") + logo + logoB, v("autoAlpha" to 0), b(0.0) + 0.7)
        t.init(MINI_WALLS, v("rotation" to 12))
        if (R) TILES.forEachIndexed { i, k -> inn(one(k), b(0.0) + 0.1 + i * 0.03, v()) }
        else {
            // 4 tiles: pop out small around the centre on the drop (keyframe 05) …
            MINIS.forEachIndexed { i, m ->
                val (cx, cy) = scene.wallCenter(m.wall)
                t.fromTo(
                    one(m.wall),
                    v("x" to LX - cx, "y" to LY - cy, "scale" to 0.08, "rotation" to 0, "autoAlpha" to 0),
                    v("x" to LX + m.dx - cx, "y" to LY + m.dy - cy, "scale" to m.tw / 168f, "rotation" to m.rot, "autoAlpha" to 1),
                    b(0.0) + i * 0.03, 0.32, backOut(1.7f),
                )
                // … then grow into their wall slot — same element all the way, nothing to hand over
                t.to(one(m.wall), v("x" to 0, "y" to 0, "scale" to 1, "rotation" to 12), b(0.0) + 0.42 + i * 0.02, 0.45, Ease.power3InOut)
            }
            // the other 5 tiles pop out of the centre as the wall assembles, nearest first
            fun dist(k: String) = scene.center(k).let { (x, y) -> hypot(x - LX, y - LY) }
            REST_TILES.sortedBy(::dist).forEachIndexed { i, k ->
                val (cx, cy) = scene.center(k)
                inn(one(k), b(0.0) + 0.5 + i * 0.04, v("x" to (LX - cx) * 0.9f, "y" to (LY - cy) * 0.9f, "scale" to 0.2, "rotation" to if (i % 2 == 1) 25 else -25), 0.55, backOut(1.6f))
            }
        }
        if (!R) {
            // camera shake on beat 1 (keyframes → consecutive tweens, GSAP's per-keyframe default ease)
            var at = b(1.0)
            for ((x, y, d) in listOf(Triple(-4, 3, 0.05), Triple(3, -2, 0.05), Triple(-1, 1, 0.05), Triple(0, 0, 0.06))) {
                t.to(one("cam"), v("x" to x, "y" to y), at, d)
                at += d
            }
        }

        /* Every accent below STARTS on a beat or an off-beat (½) — single-overshoot eases only. */
        val pop = backOut(2.5f)

        /* ---------------- G01 · Thousands of ringtones (beat 1) ---------------- */
        inn(one("g01_head"), b(1.0), v("scale" to 1.35), 0.45, backOut(2.2f))
        t.set(one("wave"), v("autoAlpha" to 1), b(1.0))
        if (!R) {
            t.fromTo(WAVE_BARS, v("scaleY" to 0), v("scaleY" to 1), b(1.0), 0.3, backOut(2.5f), stagger = 0.008)
            t.fromTo(one("wave_tc"), v("autoAlpha" to 0, "x" to -6), v("autoAlpha" to 1, "x" to 0), b(1.0) + 0.3, 0.3)
        }
        // Countdown: A7 / Progress Wave, G01 → G04 (UI paints bars + "-0:SS" from clock.p).
        t.fromTo(one("clock"), v("p" to 0), v("p" to 1), times.tG01, times.tCount, Ease.none)
        inn(one("g01_phone"), b(2.0), v("y" to 330), 0.8, backOut(1.1f))
        if (!R) t.to(one("tiles"), v("x" to -12), times.tG01, times.tG02a - times.tG01, Ease.none)

        /* ---------------- G02a · Same song. — head b5 · card b5½ · "Sam" b6 (holds 2 beats) ---------------- */
        out(TILES, b(5.0) - 0.2, v("y" to 50, "scale" to 0.85), 0.35, Ease.power2In, stagger = 0.02)
        fadeIn(one("bg_G02a"), b(5.0) - 0.1)
        t.set(one("bg_G01"), v("autoAlpha" to 0), b(5.0) + 0.4)
        out(one("g01_head"), b(5.0) - 0.15, v("y" to -14), 0.15)
        inn(one("g02a_head"), b(5.0), v("scale" to 1.35), 0.45, backOut(2.2f))
        if (R) {
            out(one("g01_phone"), b(5.0) - 0.1, v())
            fadeIn(one("g02_phone"), b(5.0), 0.3)
        } else {
            // The phone swaps screens at rest, then rises into the G02 slot.
            t.fromTo(one("g02_phone"), v("y" to 324, "autoAlpha" to 0), v("autoAlpha" to 1), b(5.0) - 0.2, 0.14, Ease.none)
            t.set(one("g01_phone"), v("autoAlpha" to 0), b(5.0) - 0.06)
            t.to(one("g02_phone"), v("y" to 0), b(5.0) - 0.06, 0.65, Ease.power3InOut)
        }
        // Card is opaque from its first frame (a fading card lets the phone's own text ghost through).
        if (R) fadeIn(one("card"), b(5.5), 0.25)
        else {
            t.set(one("card"), v("autoAlpha" to 1), b(5.5))
            t.fromTo(one("card"), v("y" to 140, "scale" to 0.92), v("y" to 0, "scale" to 1), b(5.5), 0.5, backOut(1.4f))
        }
        /* The name reel, picker-style (1.6.4): only the current name wears the pill; the last and next names are bare
           text, small (78 %) and faint, so the slot reads as "one of many". Mia only ever peeks: there are more. */
        val pills = REEL.indices.map { "pill$it" }
        val bgs = REEL.indices.map { "pillbg$it" }
        t.init(one("reel_col"), v("y" to 0))
        t.init(pills, v("opacity" to 0))
        t.init(pills.drop(1), v("scale" to GHOST_SCALE))
        t.init(bgs.drop(1), v("opacity" to 0))
        if (R) fadeIn(one(pills[0]), b(6.0), 0.25)
        else t.fromTo(one(pills[0]), v("autoAlpha" to 0, "scaleX" to 0.2), v("autoAlpha" to 1, "scaleX" to 1), b(6.0), 0.35, pop)
        t.fromTo(one(pills[1]), v("opacity" to 0), v("opacity" to GHOST_OPACITY), b(6.0) + 0.25, 0.4, Ease.power1InOut)
        fun roll(i: Int, at: Double) {
            val s0 = at - 0.08
            val d = if (R) 0.2 else 0.42
            val e = Ease.power3InOut
            if (R) t.set(one("reel_col"), v("y" to -i * REEL_H), at)
            else t.to(one("reel_col"), v("y" to -i * REEL_H), s0, d, e)
            // the name leaving the slot sheds its pill and shrinks back into the list …
            t.to(one(pills[i - 1]), v("opacity" to GHOST_OPACITY, "scale" to GHOST_SCALE), s0, d, e)
            t.to(one(bgs[i - 1]), v("opacity" to 0), s0, d * 0.6, Ease.power1Out)
            // … the next one grows into it and the pill fades in under it
            t.to(one(pills[i]), v("opacity" to 1, "scale" to 1), s0, d, e)
            t.to(one(bgs[i]), v("opacity" to 1), s0 + d * 0.4, d * 0.6, Ease.power1In)
            t.to(one(pills[i + 1]), v("opacity" to GHOST_OPACITY), s0 + d * 0.3, d, Ease.power1InOut)
            if (i >= 2) t.to(one(pills[i - 2]), v("opacity" to 0), s0, d * 0.6, Ease.power1Out)
        }

        /* ---------------- G02b · Any name. — "Emma" b8 (1.6.5: no stickers, the reel is the only thing moving) ---------------- */
        fadeIn(one("bg_G02b"), b(8.0) - 0.1)
        t.set(one("bg_G02a"), v("autoAlpha" to 0), b(8.0) + 0.4)
        out(one("g02a_head"), b(8.0) - 0.1, v("scale" to 0.9), 0.1)
        inn(one("g02b_head"), b(8.0), v("scale" to 1.35), 0.4, backOut(2.2f))
        roll(1, b(8.0))
        if (!R) {
            t.to(one("card"), v("scale" to 1.03), b(8.0), 0.06, Ease.power2Out)
            t.to(one("card"), v("scale" to 1), b(8.0) + 0.06, 0.3, Ease.power2Out)
        }

        /* ---------------- G02c · "Jake" b10 ---------------- */
        fadeIn(one("bg_G02c"), b(10.0) - 0.1)
        t.set(one("bg_G02b"), v("autoAlpha" to 0), b(10.0) + 0.4)
        roll(2, b(10.0))
        if (!R) {
            t.to(one("g02b_head"), v("scale" to 1.1), b(10.0), 0.06, Ease.power2Out)
            t.to(one("g02b_head"), v("scale" to 1), b(10.0) + 0.06, 0.3, backOut(3f))
            t.to(one("card"), v("scale" to 1.03), b(10.0), 0.06, Ease.power2Out)
            t.to(one("card"), v("scale" to 1), b(10.0) + 0.06, 0.3, Ease.power2Out)
        }

        /* ---------------- G03 · Rings for you. — head b12 · ring b13 · Hey b14 · Sam b14½ · pick up b15 · ring b16 · ad b17 ---------------- */
        if (R) out(one("card"), b(12.0) - 0.1, v(), 0.2)
        else {
            t.to(one("card"), v("y" to 420), b(12.0) - 0.15, 0.4, Ease.power2In)
            t.set(one("card"), v("autoAlpha" to 0), b(12.0) + 0.25)
        }
        fadeIn(one("bg_G03"), b(12.0) - 0.1)
        t.set(one("bg_G02c"), v("autoAlpha" to 0), b(12.0) + 0.4)
        out(one("g02b_head"), b(12.0) - 0.1, v("scale" to 0.9), 0.1)
        inn(one("g03_head"), b(12.0), v("scale" to 1.35), 0.45, backOut(2.2f))
        if (R) {
            out(one("g02_phone"), b(12.0) - 0.1, v())
            fadeIn(one("g03_phone"), b(12.0), 0.3)
        } else {
            // Hero phone settles onto the G03 phone's exact body (232 → 119.68 wide, top 180.4 → 189.9), so the swap
            // changes only the screen. Scaling to the sprite boxes (0.623) left a 17 % jump in the native build.
            t.to(one("g02_phone"), v("scale" to G03_FIT, "y" to G03_DY), b(12.0) - 0.1, 0.5, Ease.power3InOut)
            t.fromTo(one("g03_phone"), v("autoAlpha" to 0), v("autoAlpha" to 1), b(12.0) + 0.4, 0.1, Ease.none)
            t.set(one("g02_phone"), v("autoAlpha" to 0), b(12.0) + 0.5)
        }
        inn(g3rings, b(12.5), v("scale" to 0.6), 0.5, Ease.power2Out, stagger = 0.06)
        if (!R) {
            // "ring ring": a vibration burst + ring pulse
            for (at in listOf(b(13.0), b(16.0))) {
                t.fromTo(one("g03_phone"), v("rotation" to -3), v("rotation" to 3), at, 0.05, Ease.sineInOut, repeat = 5, yoyo = true)
                t.to(one("g03_phone"), v("rotation" to 0), at + 0.3, 0.05)
                t.fromTo(g3rings, v("scale" to 1), v("scale" to 1.08), at, 0.1, Ease.power2Out, stagger = 0.04)
                t.to(g3rings, v("scale" to 1), at + 0.1, 0.3, Ease.power2Out, stagger = 0.04)
            }
        }
        /* The ringtone "sings" the bubble, one word per half beat: “Hey (b14) · Sam (b14½) · pick up…” (b15).
           Words go dim → lit; the lit glow is a pre-rendered copy faded in (no animated shadow). */
        t.init(listOf("bw_hey", "bw_sam", "bw_call"), v("opacity" to 0.45))
        t.init(listOf("bw_hey_g", "bw_call_g"), v("opacity" to 0))
        t.init(one("chip"), v("autoAlpha" to 0, "scale" to 0.6))
        inn(one("g03_bubble"), b(14.0), v("scale" to 0, "rotation" to -18), 0.45, pop)
        fun lite(c: String, at: Double) {
            t.to(one(c), v("opacity" to 1), at, 0.12, Ease.power1Out)
            t.fromTo(one("${c}_g"), v("opacity" to 0), v("opacity" to 1), at, 0.1, Ease.power1Out)
            t.to(one("${c}_g"), v("opacity" to 0.35), at + 0.15, 0.6, Ease.power2Out)
            if (!R) t.fromTo(one(c), v("scale" to 1.18), v("scale" to 1), at, 0.35, backOut(2.5f))
        }
        lite("bw_hey", b(14.0))
        t.to(one("bw_sam"), v("opacity" to 1), b(14.5), 0.1)
        t.to(one("chip"), v("autoAlpha" to 1, "scale" to 1), b(14.5), if (R) 0.2 else 0.35, if (R) Ease.none else pop)
        lite("bw_call", b(15.0))
        if (!R) {
            // one more glow across the whole line when the phone rings again
            t.to(one("g03_bubble"), v("scale" to 1.07), b(16.0), 0.08, Ease.power2Out)
            t.to(one("g03_bubble"), v("scale" to 1), b(16.0) + 0.08, 0.4, Ease.power2Out)
            t.fromTo(listOf("bw_hey_g", "bw_call_g"), v("opacity" to 0.35), v("opacity" to 1), b(16.0), 0.08)
            t.to(listOf("bw_hey_g", "bw_call_g"), v("opacity" to 0.35), b(16.0) + 0.1, 0.5, Ease.power2Out)
        }
        // native ad only after the line has fully landed; it stays until the swipe
        inn(one("nat1"), b(17.0), v("y" to 60), 0.45, Ease.power3Out)

        /* ---------------- 1.6 · the feed ----------------
           b18: the call ends — the phone shows the AI feed card it was ringing with (Morning Glow · for Sam)
           b18½: card 2 peeks at the bottom + "Swipe up" · b19½: pause until the swipe (A7Player) */
        out(one("g03_bubble"), b(A7Times.N_FEED) - 0.1, v("scale" to 0.85), 0.2)
        out(g3rings, b(A7Times.N_FEED) - 0.1, v("scale" to 1.15), 0.4, Ease.power1Out) // the call is over
        t.init(one("feed_stack"), v("y" to 0))
        fadeIn(one("feedwrap"), b(A7Times.N_FEED), 0.3)
        if (R) t.set(one("feed_stack"), v("y" to -FEED_PEEK), b(A7Times.N_HINT))
        else t.fromTo(one("feed_stack"), v("y" to 0), v("y" to -FEED_PEEK), b(A7Times.N_HINT), 0.5, Ease.power3Out)
        inn(one("swipe_pill"), b(A7Times.N_HINT), v("y" to 12, "scale" to 0.9), 0.4, Ease.power3Out)
        out(one("wave_tc"), b(A7Times.N_HINT), v(), 0.3) // the count is done — no frozen -0:00 while the feed waits
        fadeIn(one("callglow"), b(A7Times.N_ASK), 0.6)
        t.call(times.tWait, onWait)
        t.addPause(times.tWait + 0.001)
        val sw = times.tWait + 0.002
        out(one("swipe_pill"), sw, v("y" to 8), 0.2)

        /* ---------------- card 2 · the teaser: another template, another name ----------------
           (the stack itself is moved by the swipe, outside the timeline) name lights b20½ · progress b20 → b23 */
        t.init(one("c2_name"), v("opacity" to 0.35))
        t.to(one("c2_name"), v("opacity" to 1), b(A7Times.N_PLAY + 0.5), 0.12, Ease.power1Out)
        if (!R) t.fromTo(one("c2_name"), v("scale" to 1.18), v("scale" to 1), b(A7Times.N_PLAY + 0.5), 0.35, backOut(2.5f))
        t.fromTo(one("c2_wave"), v("p" to 0), v("p" to 1), b(A7Times.N_PLAY), b(A7Times.N_FLY) - b(A7Times.N_PLAY), Ease.none)

        /* ---------------- fly into the phone's screen → G04 (b22½ clear · b23 fly · b24 land) ----------------
           1.6.5: one exit per half beat — the top chrome at b22½, the ad with the camera at b23 — and EMMA is the
           object the eye follows across the cut. */
        val s2 = b(A7Times.N_FLY)
        out(one("g03_head"), b(A7Times.N_CLEAR), v("y" to -14), 0.2)
        out(one("wave"), b(A7Times.N_CLEAR), v("y" to -10), 0.25)
        out(one("nat1"), s2, v("y" to 40), 0.3)
        val span = b(A7Times.G4) - s2
        if (R) out(one("callzoom"), s2, v(), 0.3)
        else {
            // the camera flies into the phone's screen — the same log-space push as the opening fly-through
            t.fromTo(one("callzoom"), v("k" to 0), v("k" to ln(5.2)), s2, span, Ease.sineIn)
            t.fromTo(one("bloom"), v("scale" to 0.25, "autoAlpha" to 0), v("scale" to 1.3, "autoAlpha" to 1), s2 + span * 0.3, span * 0.7, Ease.power2In)
            t.to(one("callzoom"), v("autoAlpha" to 0), s2 + span * 0.72, span * 0.25, Ease.power1In)
            t.to(one("bloom"), v("autoAlpha" to 0, "scale" to 1.6), b(A7Times.G4), 0.6, Ease.power2Out)

            /* EMMA lifts off card 2 — same word, same size, same spot — and stays out of the zoom, so it is the one
               thing that does not rush past. Up first, then across; both ends at rest, so it neither jumps off the
               card nor snaps into the slot. On the way the white word fades into the white sticker. */
            val carry = listOf("g04_emma", "carry_name")
            val (ex, ey) = scene.center("g04_emma")
            t.set(one("c2_name"), v("autoAlpha" to 0), s2)
            t.set(one("carry_name"), v("autoAlpha" to 1), s2)
            t.set(carry, v("x" to EMMA_X - ex), s2)
            t.fromTo(carry, v("y" to EMMA_Y - ey, "scale" to FEED_FIT), v("y" to 0, "scale" to 1), s2, span, Ease.power2InOut)
            t.to(carry, v("x" to 0), s2 + span * 0.15, span * 0.85, Ease.sineInOut)
            t.to(one("carry_name"), v("autoAlpha" to 0), s2 + span * 0.2, span * 0.4, Ease.power1In)
            t.fromTo(one("g04_emma"), v("autoAlpha" to 0), v("autoAlpha" to 1), s2 + span * 0.25, span * 0.4, Ease.power1Out)
        }

        /* ---------------- G04 · Yours is next. — head b24 · stickers every ¼ beat · CTA b26 ---------------- */
        fun g(n: Double) = b(A7Times.G4 + n)
        fadeIn(one("bg_G04"), g(0.0) - 0.1)
        t.set(one("bg_G03"), v("autoAlpha" to 0), g(0.0) + 0.4)
        inn(one("g04_center"), g(0.0), v("scale" to 1.35), 0.45, backOut(2.2f))
        // Emma came in with the name (above); reduced motion just shows it on the downbeat
        if (R) inn(one("g04_emma"), g(0.0), v())
        G04_STICKERS.filter { it != "g04_emma" }.forEachIndexed { i, k ->
            val (cx, cy) = scene.center(k)
            inn(one(k), g(0.5 + i * 0.25), v("x" to 180 - cx, "y" to 190 - cy, "scale" to 0, "rotation" to if (i % 2 == 1) 30 else -30), 0.5, backOut(1.9f))
        }
        inn(one("g04_cta"), g(2.0), v("y" to 30), 0.5, backOut(1.6f))
        // where names go — inside, through this button (no promise of typing one now)
        inn(one("g04cap"), g(2.0), v("y" to -6), 0.45, Ease.power2Out)
        inn(one("g04_secondary"), g(2.5), v("y" to 20), 0.45, Ease.power3Out)
        t.set(one("hot_g04"), v("pointer" to 1), g(2.0))
        t.set(one("callzoom"), v("autoAlpha" to 0), g(0.0) + 0.1)
        // Native #2 is a fresh request: the slot comes in with its loading state, the ad fills it when it arrives.
        t.call(g(3.0), onNative2)
        inn(one("nat2"), g(3.0), v("y" to 40), 0.4, Ease.power3Out)
        fadeIn(one("divider"), g(3.0), 0.3)
        if (!R) t.fromTo((0..3).map { "nat2_skel_$it" }, v("opacity" to 1), v("opacity" to 0.45), g(3.2), 0.3, Ease.sineInOut, repeat = 3, yoyo = true)
        t.call(times.tEnd, onIdle)
    }

    /** Idle loop on G04: CTA breathes, stickers float. Killed on tap. */
    fun idle(tl: Timeline) {
        tl.to(listOf("g04_cta"), v("scale" to 1.03), 0.0, 0.8, Ease.sineInOut, repeat = -1, yoyo = true)
        G04_STICKERS.forEachIndexed { i, k ->
            tl.to(listOf(k), v("y" to 6), i * 0.15, 1.3 + (i % 3) * 0.25, Ease.sineInOut, repeat = -1, yoyo = true, rel = true)
        }
    }
}
