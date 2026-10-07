package namvunhatle.r15.onboarding.core

import android.content.Context
import android.provider.Settings
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Runs the trailer: one master [Timeline] on a frame clock that is slaved to the music once it plays, plus the idle
 * loop, the 1.6 wait (glow + hints) and small free-running effects (press feedback, the swipe's own motion).
 * The UI calls [frame] once per vsync, then reads [store] — it never animates by itself.
 *
 * Events go out through [listener]; ads are the host's (A7Ads), the player only says when.
 */
class A7Player(ctx: Context, val scene: Scene) {
    interface Listener {
        /** The interstitial moment (end of the splash). Call [adClosed] when the ad is gone (or right away). */
        fun onAd()
        /** Native #2's slot is coming in (G04): request a fresh ad for it. */
        fun onNative2()
        /** The feed waits for the swipe / the swipe was taken. */
        fun onWaitChanged(waiting: Boolean)
        /** Haptic: 0 = threshold tick · 1 = swipe taken · 2 = card 2 lands · 3 = G04 lands. */
        fun onHaptic(kind: Int)
    }

    val store = Store()
    private val app = ctx.applicationContext
    val audio = A7Mixer(app)
    var listener: Listener? = null

    /** prefers-reduced-motion ≈ "Remove animations" (animator duration scale 0). */
    val reduced = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    val times = A7Times(A7Mixer.BPM)
    private lateinit var main: Timeline
    private var idle: Timeline? = null
    private val fx = Timeline(store)
    private var fxTime = 0.0

    var atAd = false; private set
    /** The feed stops at b19½ and waits for the swipe. */
    var waiting = false; private set
    /** The trailer reached its end (G04 settled, CTAs live). */
    var done = false; private set
    private var paused = false
    private var frozen = false
    private var lastNanos = 0L

    // the wait: breathing glow + one hint per bar, both dropped on the swipe
    private var glow: Timeline? = null
    private var hints: Timeline? = null
    private var nextHint = Double.NaN // music time (or wait clock without music) of the next hint
    private var waitClock = 0.0
    private var dragging = false
    private var pastThreshold = false
    // the swipe: the timeline runs tWait → tPlay while the music runs from [stretchFrom] to the landing beat [stretchTo]
    private var stretching = false
    private var stretchFrom = 0.0
    private var stretchTo = 0.0
    // landing haptics, on the frame clock
    private val haptics = ArrayList<Pair<Double, Int>>()
    private var hapticClock = 0.0

    /** Bumped whenever [atAd] / [waiting] / [done] change, so UIs can re-read them cheaply. */
    var uiVersion = 0; private set

    init { rebuild() }

    private fun rebuild() {
        stopIdle()
        store.resetAll()
        main = Timeline(store)
        A7Script.build(
            main, scene, times, reduced,
            onAd = { atAd = true; uiVersion++; listener?.onAd() },
            onWait = ::startWait,
            onNative2 = { listener?.onNative2() },
            onIdle = { done = true; uiVersion++; startIdle() },
        )
        main.seal()
        audio.prepare()
    }

    /** Called once per vsync with the frame's timestamp. */
    fun frame(frameTimeNanos: Long) {
        val dt = if (lastNanos == 0L) 0.0 else min((frameTimeNanos - lastNanos) / 1e9, 0.1)
        lastNanos = frameTimeNanos
        fxTime += dt
        fx.advanceTo(fxTime); fx.prune()
        idle?.let { it.advanceTo(it.time + dt) }
        pumpHaptics(dt)
        if (waiting) { waitFrame(frameTimeNanos, dt); return }
        if (paused || frozen) return

        val music = audio.musicTime(frameTimeNanos)
        var target = main.time + dt
        // Output just started, no timestamp yet: hold the still P03 frame rather than run ahead of the sound.
        if (audio.pending && main.time < A7Times.T_BURST - 0.05) target = main.time
        if (stretching) {
            // swipe taken: tWait → tPlay in exactly the time the music needs to reach the landing beat
            val k = (times.tPlay - times.tWait) / (stretchTo - stretchFrom)
            val m = music ?: (stretchFrom + (main.time - times.tWait) / k + dt)
            if (m >= stretchTo) {
                stretching = false
                target = max(times.tPlay, m - audio.shift)
            } else target = times.tWait + (m - stretchFrom) * k
        } else if (music != null) {
            val a = music - audio.shift
            target = if (main.time < A7Times.T_BURST) {
                // Still hold after the ad (P03): the screen does not move, so the picture can simply wait for
                // (or jump to) the audio clock — this is where the output latency gets absorbed.
                max(main.time, a)
            } else {
                // In motion: never jump — nudge the rate by at most ±25 % toward the audio clock.
                target + (a - target).coerceIn(-0.25 * dt, 0.25 * dt)
            }
        }
        // Never backwards: the swipe's stretch starts a hair before the wait's pause point, and going back past it
        // would hit the pause again.
        if (main.advanceTo(max(target, main.time))) paused = true
    }

    /** Timeline time now (for debugging / screenshots). */
    val time get() = main.time

    /** The interstitial is gone (closed, failed, or there was none): the trailer goes on and the music starts. */
    fun adClosed() {
        if (!atAd) return
        atAd = false; uiVersion++
        paused = false
        // Its first frames are silence up to the drop's pre-roll; the still re-entry hold absorbs the output latency.
        audio.start(A7Times.T_PAUSE)
    }

    // ---------------------------------------------------------------- 1.6 · the wait and the swipe

    private fun startWait() {
        waiting = true; paused = true; uiVersion++
        store["hot_swipe"][Prop.POINTER] = 1f
        // even sine breathing on the glow behind the phone — no pulse on the beat
        if (!reduced) glow = Timeline(store).also {
            it.fromTo(listOf("callglow"), v("opacity" to 1), v("opacity" to 0.5), 0.0, times.beat * 2, Ease.sineInOut, repeat = -1, yoyo = true)
        }
        waitClock = 0.0
        nextHint = Double.NaN
        listener?.onWaitChanged(true)
    }

    private fun waitFrame(frameTimeNanos: Long, dt: Double) {
        glow?.let { it.advanceTo(it.time + dt) }
        hints?.let { it.advanceTo(it.time + dt) }
        waitClock += dt
        if (dragging || reduced) return
        // one hint per bar, on the music's downbeat (the first one right away)
        val music = audio.musicTime(frameTimeNanos)
        val now = music ?: waitClock
        if (nextHint.isNaN()) {
            hint()
            nextHint = if (music != null) {
                val n = floor((music - A7Times.T_DROP) / times.beat) + 1
                times.b(n + ((4 - (n % 4)) % 4))
            } else waitClock + 4 * times.beat
        } else if (now >= nextHint) {
            hint()
            nextHint += 4 * times.beat
        }
    }

    /** The stack nudges up and a touch dot travels up the screen. */
    private fun hint() {
        val h = Timeline(store)
        h.to(listOf("feed_stack"), v("y" to -Scene.FEED_PEEK - 90), 0.0, 0.55, Ease.power2InOut)
        h.to(listOf("feed_stack"), v("y" to -Scene.FEED_PEEK), 0.55, 0.6, Ease.sineInOut)
        h.fromTo(listOf("touch"), v("y" to 0, "autoAlpha" to 0, "scale" to 0.7), v("autoAlpha" to 1, "scale" to 1), 0.0, 0.15, Ease.power1Out)
        h.to(listOf("touch"), v("y" to -110), 0.15, 0.55, Ease.power2InOut)
        h.to(listOf("touch"), v("autoAlpha" to 0), 0.7, 0.25, Ease.power1In)
        hints = h
    }

    /** Finger down on the feed: the hint stops where it is, the finger takes the stack. */
    fun dragStart() {
        if (!waiting) return
        dragging = true; pastThreshold = false
        hints = null
        store["touch"][Prop.ALPHA] = 0f
    }

    /** [dy] = finger travel in frame dp (< 0 = up). 1:1 with the finger up, a rubber band down. */
    fun dragMove(dy: Float) {
        if (!waiting || !dragging) return
        val up = if (dy < 0) dy else dy * 0.25f
        store["feed_stack"][Prop.Y] = -Scene.FEED_PEEK + up / Scene.FEED_FIT
        val p = max(0f, -dy) / THRESHOLD
        store["swipe_pill"][Prop.ALPHA] = 1 - min(1f, p) * 0.7f
        audio.open(p)
        if (p >= 1 && !pastThreshold) { pastThreshold = true; listener?.onHaptic(0) }
        if (p < 1) pastThreshold = false
    }

    /** Finger up: far enough, a fling, or a tap takes the swipe; otherwise back to the peek and the hints go on. */
    fun dragEnd(dy: Float, velocity: Float, tap: Boolean) {
        if (!waiting || !dragging) return
        dragging = false
        if (dy < -THRESHOLD || velocity < -FLING || tap) { commit(); return }
        fx.to(listOf("feed_stack"), v("y" to -Scene.FEED_PEEK), fxTime, 0.45, Ease.power3Out)
        fx.to(listOf("swipe_pill"), v("autoAlpha" to 1), fxTime, 0.3)
        audio.open(0f)
        nextHint = Double.NaN
    }

    /**
     * Swipe taken (drag, fling, tap, or the pill as a button): card 2 lands on the music's next beat ≥ 0.3 s away, and
     * the timeline's tWait → tPlay stretch is time-scaled to arrive with it, so the teaser and G04 stay on the grid.
     */
    fun commit() {
        if (!waiting) return
        waiting = false; dragging = false; uiVersion++
        glow = null; hints = null
        store["hot_swipe"][Prop.POINTER] = 0f
        store["touch"][Prop.ALPHA] = 0f
        listener?.onHaptic(1)
        listener?.onWaitChanged(false)
        val from = store["feed_stack"][Prop.Y]
        val music = audio.musicTime(System.nanoTime())
        val land = music?.let { audio.commit(it) }
        if (music != null && land != null) { stretchFrom = music; stretchTo = land }
        else { stretchFrom = times.tWait; stretchTo = times.tPlay } // no music: the stretch runs at normal speed
        val d = stretchTo - stretchFrom
        stretching = true
        paused = false
        // the stack finishes the swipe: card 2 settles on the beat
        fx.fromTo(listOf("feed_stack"), v("y" to from), v("y" to -Scene.FEED_SCR.h), fxTime, if (reduced) min(d, 0.25) else d, Ease.power3Out)
        hapticClock = 0.0
        haptics.add(d to 2)
        haptics.add(d + (A7Times.G4 - A7Times.N_PLAY) * times.beat to 3)
    }

    private fun pumpHaptics(dt: Double) {
        if (haptics.isEmpty()) return
        hapticClock += dt
        val due = haptics.filter { hapticClock >= it.first }
        haptics.removeAll(due)
        due.forEach { listener?.onHaptic(it.second) }
    }

    // ---------------------------------------------------------------- end of the trailer

    fun press(id: String, down: Boolean) {
        if (down) stopIdle()
        fx.to(listOf(id), v("scale" to if (down) 0.96 else 1), fxTime, if (down) 0.08 else 0.2, Ease.power2Out)
    }

    fun ctaEnabled() = store["hot_g04"][Prop.POINTER] > 0f

    /** Leaving for the host's next screen: the music fades with it. */
    fun leave() {
        stopIdle()
        audio.stop(fade = 0.8)
    }

    fun replay() {
        audio.stop()
        stopIdle()
        glow = null; hints = null; haptics.clear()
        atAd = false; waiting = false; dragging = false; stretching = false; done = false
        paused = false; frozen = false; uiVersion++
        main.reset()
        main.advanceTo(0.0)
    }

    /** Debug: open frozen at second [t] (review, screenshots). No events, no audio. */
    fun seekFrozen(t: Double) {
        main.reset()
        main.advanceTo(t, events = false)
        frozen = true
        if (t >= times.tEnd) startIdle()
    }

    private fun startIdle() {
        if (reduced) return
        stopIdle()
        idle = Timeline(store).also { A7Script.idle(it) }
    }

    private fun stopIdle() {
        idle?.revert()
        idle = null
    }

    fun release() = audio.release()

    companion object {
        /** Swipe threshold (frame dp of finger travel) and fling speed (dp / ms), as the web. */
        const val THRESHOLD = 70f
        const val FLING = 0.45f
    }
}
