package namvunhatle.r15.onboarding.core

import android.content.Context
import android.provider.Settings
import kotlin.math.max
import kotlin.math.min

enum class Dest { PAYWALL, AI, HOME }

/**
 * Runs the trailer: one master [Timeline] on a frame clock that is slaved to the audio clock once music plays,
 * plus the idle loop and small free-running effects (press feedback, destination slide-in).
 * UI layers call [frame] once per vsync, then read [store] — they never animate by themselves.
 */
class A7Player(ctx: Context, val scene: Scene) {
    val store = Store()
    private val app = ctx.applicationContext
    val audio = A7Audio(app)

    /** prefers-reduced-motion ≈ "Remove animations" (animator duration scale 0). */
    val reduced = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** The one onboarding track (Future Pop Upbeat); there is no track switching. */
    val track: Track? = scene.tracks.firstOrNull()
    lateinit var times: A7Times; private set
    private lateinit var main: Timeline
    private var idle: Timeline? = null
    private val fx = Timeline(store)
    private var fxTime = 0.0

    var atAd = false; private set
    var dest: Dest? = null; private set
    /** Destination draw order, last = on top. */
    val destOrder = ArrayList<Dest>()
    private var paused = false
    private var frozen = false
    private var lastNanos = 0L

    /** Bumped whenever [atAd] / [dest] change, so UIs can re-read them cheaply. */
    var uiVersion = 0; private set

    init { rebuild() }

    private fun rebuild() {
        stopIdle()
        times = A7Times(track?.bpm ?: 120)
        store.resetAll()
        main = Timeline(store)
        A7Script.build(main, scene, times, reduced, onAd = { atAd = true; uiVersion++ }, onIdle = ::startIdle)
        main.seal()
        audio.prepare(track)
    }

    /** Called once per vsync with the frame's timestamp. */
    fun frame(frameTimeNanos: Long) {
        val dt = if (lastNanos == 0L) 0.0 else min((frameTimeNanos - lastNanos) / 1e9, 0.1)
        lastNanos = frameTimeNanos
        fxTime += dt
        fx.advanceTo(fxTime); fx.prune()
        idle?.let { it.advanceTo(it.time + dt) }
        if (paused || frozen) return

        var target = main.time + dt
        // Output just started, no timestamp yet: hold the still P03 frame rather than run ahead of the sound.
        if (audio.pending && main.time < A7Times.T_BURST - 0.05) target = main.time
        audio.timelineTime(frameTimeNanos)?.let { a ->
            target = if (main.time < A7Times.T_BURST) {
                // Still hold after the ad (P03): the screen does not move, so the picture can simply wait for
                // (or jump to) the audio clock — this is where the output latency gets absorbed.
                max(main.time, a)
            } else {
                // In motion: never jump — nudge the rate by at most ±25 % toward the audio clock.
                target + (a - target).coerceIn(-0.25 * dt, 0.25 * dt)
            }
        }
        if (main.advanceTo(target)) paused = true
    }

    /** Timeline time now (for debugging / screenshots). */
    val time get() = main.time

    fun skipAd() {
        if (!atAd) return
        atAd = false; uiVersion++
        paused = false
        // The Skip tap starts the audio stream; its first frames are silence up to the drop's pre-roll.
        audio.start(A7Times.T_PAUSE)
    }

    fun go(d: Dest) {
        stopIdle()
        dest = d; destOrder.remove(d); destOrder.add(d); uiVersion++
        val id = destId(d)
        val r = reduced
        fx.fromTo(listOf(id), v("x" to if (r) 0f else scene.view.w, "autoAlpha" to if (r) 0 else 1), v("x" to 0, "autoAlpha" to 1), fxTime, if (r) 0.2 else 0.4, Ease.power3Out)
        audio.stop(fade = 0.8)
    }

    fun press(id: String, down: Boolean) {
        if (down) stopIdle()
        fx.to(listOf(id), v("scale" to if (down) 0.96 else 1), fxTime, if (down) 0.08 else 0.2, Ease.power2Out)
    }

    fun ctaEnabled() = store["hot_g04"][Prop.POINTER] > 0f

    fun replay() {
        audio.stop()
        stopIdle()
        hideDests()
        atAd = false; paused = false; frozen = false; uiVersion++
        main.reset()
        main.advanceTo(0.0)
    }

    /** `?t=12.4` equivalent: open frozen at that second (review, screenshots). No events, no audio. */
    fun seekFrozen(t: Double) {
        main.reset()
        main.advanceTo(t, events = false)
        frozen = true
        if (t >= times.tEnd) startIdle()
    }

    private fun hideDests() {
        for (d in Dest.entries) { val el = store[destId(d)]; el[Prop.ALPHA] = 0f; el[Prop.X] = 0f }
        dest = null; destOrder.clear()
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
        fun destId(d: Dest) = "dest_" + d.name.lowercase()
    }
}
