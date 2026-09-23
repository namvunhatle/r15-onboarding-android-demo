package namvunhatle.r15.onboarding.core

import kotlin.math.floor
import kotlin.math.min

/** Animatable properties. Only transform + opacity (and three scalars the UI derives drawing from). */
enum class Prop { X, Y, SX, SY, ROT, ALPHA, K, P, POINTER }

/** One animated element's current values. The UI reads these every frame; it never animates on its own. */
class El internal constructor(val id: String) {
    internal val v = FloatArray(Prop.entries.size).also {
        it[Prop.SX.ordinal] = 1f; it[Prop.SY.ordinal] = 1f; it[Prop.ALPHA.ordinal] = 1f; it[Prop.POINTER.ordinal] = 1f
    }
    operator fun get(p: Prop) = v[p.ordinal]
    operator fun set(p: Prop, f: Float) { v[p.ordinal] = f }
    val x get() = v[0]
    val y get() = v[1]
    val sx get() = v[2]
    val sy get() = v[3]
    val rot get() = v[4]
    val alpha get() = v[5]
    /** GSAP autoAlpha: at 0 the element is `visibility: hidden` — not drawn, not hit. */
    val shown get() = v[5] > 0f
}

class Store {
    private val els = LinkedHashMap<String, El>()
    operator fun get(id: String): El = els.getOrPut(id) { El(id) }
    /** Back to defaults (a rebuild must start from a clean splash — web: clearProps). El references stay valid. */
    fun resetAll() { val d = El("").v; for (el in els.values) d.copyInto(el.v) }
    internal fun snapshot(): Map<String, FloatArray> = els.mapValues { it.value.v.copyOf() }
    internal fun restore(s: Map<String, FloatArray>) {
        for ((id, el) in els) {
            val saved = s[id]
            if (saved != null) saved.copyInto(el.v) else El(id).v.copyInto(el.v)
        }
    }
}

/** Tween vars, GSAP-style names: x y scale scaleX scaleY rotation autoAlpha opacity k p pointer. */
typealias V = Map<Prop, Float>

fun v(vararg pairs: Pair<String, Number>): V {
    val m = LinkedHashMap<Prop, Float>()
    for ((k, n) in pairs) {
        val f = n.toFloat()
        when (k) {
            "x" -> m[Prop.X] = f
            "y" -> m[Prop.Y] = f
            "scale" -> { m[Prop.SX] = f; m[Prop.SY] = f }
            "scaleX" -> m[Prop.SX] = f
            "scaleY" -> m[Prop.SY] = f
            "rotation" -> m[Prop.ROT] = f
            "autoAlpha", "opacity" -> m[Prop.ALPHA] = f
            "k" -> m[Prop.K] = f
            "p" -> m[Prop.P] = f
            "pointer" -> m[Prop.POINTER] = f
            else -> error("unknown prop $k")
        }
    }
    return m
}

internal class Tween(
    val el: El, val start: Double, val dur: Double,
    val from: V?, val to: V, val ease: Ease,
    val repeat: Int, val yoyo: Boolean, val rel: Boolean,
) {
    private val props = to.keys.toTypedArray()
    private val s = FloatArray(props.size)
    private val e = FloatArray(props.size)
    var inited = false; private set
    private var last = -1.0
    val total = if (repeat < 0) Double.POSITIVE_INFINITY else dur * (repeat + 1)
    val done get() = inited && last >= total

    fun reset() { inited = false; last = -1.0 }

    /** Same order of operations as GSAP: startAt (`from`) is applied, THEN start values are read — lazily, on first render. */
    fun render(local0: Double) {
        val local = min(local0, total)
        if (inited && local == last) return
        if (!inited) {
            from?.forEach { (p, f) -> el[p] = f }
            for (i in props.indices) { s[i] = el[props[i]]; e[i] = if (rel) s[i] + to.getValue(props[i]) else to.getValue(props[i]) }
            inited = true
        }
        last = local
        val r = if (dur <= 0.0) 1f else {
            var c = floor(local / dur)
            var f = local / dur - c
            if (local >= total) { c = repeat.toDouble(); f = 1.0 }
            if (yoyo && c.toInt() % 2 == 1) f = 1.0 - f
            ease.at(f.toFloat())
        }
        for (i in props.indices) el[props[i]] = s[i] + (e[i] - s[i]) * r
    }

    /** Put back what this tween found (GSAP context.revert()). */
    fun revert() { if (inited) for (i in props.indices) el[props[i]] = s[i] }
}

/**
 * A GSAP-like timeline, forward-only. Children render in start order (ties: insertion order), exactly like
 * GSAP's linked list, so when two tweens touch the same property the later-starting one wins while both run.
 * Jumping backwards = [reset] + render at the new time (GSAP renders the same end state).
 */
class Timeline(val store: Store) {
    private val tweens = ArrayList<Tween>()
    private class Call(val at: Double, val fn: () -> Unit) { var fired = false }
    private val calls = ArrayList<Call>()
    private val pauses = ArrayList<Double>()
    private var initial: Map<String, FloatArray>? = null
    var time = 0.0; private set

    private fun add(t: Tween) {
        var i = tweens.size
        while (i > 0 && tweens[i - 1].start > t.start) i--
        tweens.add(i, t)
    }

    fun fromTo(
        ids: List<String>, from: V, to: V, at: Double, dur: Double, ease: Ease = Ease.default,
        stagger: Double = 0.0, repeat: Int = 0, yoyo: Boolean = false, immediate: Boolean = false,
    ) = ids.forEachIndexed { i, id ->
        val el = store[id]
        // immediateRender (GSAP's default for fromTo): the `from` state shows before the tween starts.
        if (immediate) from.forEach { (p, f) -> el[p] = f }
        add(Tween(el, at + i * stagger, dur, from, to, ease, repeat, yoyo, false))
    }

    fun to(
        ids: List<String>, to: V, at: Double, dur: Double, ease: Ease = Ease.default,
        stagger: Double = 0.0, repeat: Int = 0, yoyo: Boolean = false, rel: Boolean = false,
    ) = ids.forEachIndexed { i, id -> add(Tween(store[id], at + i * stagger, dur, null, to, ease, repeat, yoyo, rel)) }

    fun set(ids: List<String>, to: V, at: Double) = to(ids, to, at, 0.0)

    /** gsap.set at build time — part of the initial state. */
    fun init(ids: List<String>, vars: V) = ids.forEach { id -> vars.forEach { (p, f) -> store[id][p] = f } }

    fun call(at: Double, fn: () -> Unit) { calls.add(Call(at, fn)) }
    fun addPause(at: Double) { pauses.add(at) }

    /** Freeze the built state as "time 0" — call once after building. */
    fun seal() { initial = store.snapshot() }

    fun reset() {
        initial?.let(store::restore)
        tweens.forEach(Tween::reset)
        calls.forEach { it.fired = false }
        time = 0.0
    }

    /** Advance to [t]. Stops on a pause point and returns true. `events = false` = GSAP seek(t, suppressEvents). */
    fun advanceTo(t: Double, events: Boolean = true): Boolean {
        var target = t
        var hit = false
        if (events) pauses.filter { it > time && it <= target }.minOrNull()?.let { target = it; hit = true }
        render(target)
        for (c in calls) {
            if (c.fired || c.at > target) continue
            c.fired = true
            if (events) c.fn()
        }
        time = target
        return hit
    }

    private fun render(t: Double) {
        for (tw in tweens) {
            if (tw.start > t) break
            tw.render(t - tw.start)
        }
    }

    /** Undo every tween in this timeline (idle loop stop). */
    fun revert() { for (i in tweens.indices.reversed()) tweens[i].revert() }

    /** Drop finished tweens (free-running effects timeline only). */
    fun prune() { tweens.removeAll { it.done } }
}
