package namvunhatle.r15.onboarding

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import namvunhatle.r15.onboarding.core.A7Art
import namvunhatle.r15.onboarding.core.A7Native
import namvunhatle.r15.onboarding.core.A7Player
import namvunhatle.r15.onboarding.core.Box
import namvunhatle.r15.onboarding.core.Css
import namvunhatle.r15.onboarding.core.El
import namvunhatle.r15.onboarding.core.Prop
import namvunhatle.r15.onboarding.core.Scene
import namvunhatle.r15.onboarding.core.viewport
import namvunhatle.r15.onboarding.views.BakedView
import namvunhatle.r15.onboarding.views.DesignFrame
import namvunhatle.r15.onboarding.views.FeedView
import namvunhatle.r15.onboarding.views.LyricView
import namvunhatle.r15.onboarding.views.RadialView
import namvunhatle.r15.onboarding.views.WaveView
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The A7 onboarding (R15, prototype 1.6.6 · swipe teaser) as ONE view. Put it full screen, set [ads] and the two exits,
 * call [start]:
 *
 * ```kotlin
 * val a7 = findViewById<A7OnboardingView>(R.id.onboarding)
 * a7.ads = MyAds(this)                       // optional: no ads = the trailer just runs on
 * a7.onExplore = { openPaywallThenAi() }      // "Explore AI Ringtones"
 * a7.onBrowse = { openHome() }                // "Browse ringtones"
 * a7.start()
 * ```
 *
 * It brings the scene (drawn in code, 6 artwork bitmaps), the music (live-mixed stems), the swipe, haptics, and the
 * restart when the user leaves mid-trailer. The host owns the window: full screen, edge to edge, portrait; the real
 * status bar sits over the scene's top 40 dp, which the design keeps free for it.
 *
 * Inside: the scene is declared in XML (`a7_onboarding.xml`) and every vsync the shared timeline writes transform +
 * opacity, which this class copies onto the views. It never animates anything by itself.
 */
class A7OnboardingView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs), Choreographer.FrameCallback {
    /** The host's ad SDK, or null for none. Set before [start]. */
    var ads: A7Ads? = null
    /** "Explore AI Ringtones" (the AI branch: paywall, then the AI screen). The music fades out as it is called. */
    var onExplore: (() -> Unit)? = null
    /** "Browse ringtones" (Home, no paywall). The music fades out as it is called. */
    var onBrowse: (() -> Unit)? = null

    private val activity: Activity = generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()
    private val dp = resources.displayMetrics.density
    private lateinit var player: A7Player
    private lateinit var frame: DesignFrame
    private lateinit var tc: TextView
    private lateinit var wave: WaveView
    private lateinit var lyric: LyricView
    private lateinit var feed: FeedView

    /** view ↔ store element, in layout order. */
    private class Bound(val v: View, val el: El, val ox: Float, val oy: Float, val zoom: El?) { var shown = true; var collapsed = false }
    private val bound = ArrayList<Bound>()
    private var ui = -1
    private var tcLeft = -1
    private var running = false
    private var started = false
    private var left = false
    /** Each ad slot's loading skeleton (its child at inflation; ad views are added beside it). */
    private val skeletons = HashMap<Int, View>()
    private val hots = ArrayList<View>()

    /** Build the scene and run. Call once, after the view is in the window's content. */
    fun start() {
        check(!started) { "start() once; use restart()" }
        started = true
        val scene = Scene(activity, activity.viewport())
        // Start every bake and decode on the start-up pool first, so they run while the layout inflates.
        val art = A7Art(context)
        val native = A7Native(context, scene)
        LayoutInflater.from(context).inflate(R.layout.a7_onboarding, this, true)
        player = A7Player(context, scene)
        player.listener = events

        frame = findViewById(R.id.a7_frame)
        if (!scene.vp.isFrame) fill(scene)
        wave = findViewById(R.id.a7_wave_bars)
        wave.clock = player.store["clock"]
        wave.bars = Scene.WAVE_BARS.map { player.store[it] }
        lyric = findViewById(R.id.a7_lyric)
        lyric.bind(player.store, native.semibold)
        feed = findViewById(R.id.a7_feed)
        feed.bind(player.store, { native.feedCard1.value }, native.regular, native.medium, native.semibold)
        frame.findViewWithTag<RadialView>("callglow").radial(intArrayOf(0x8CBB4ABF.toInt(), 0x2EBB4ABF, 0x00BB4ABF), floatArrayOf(0f, 0.4f, 0.7f))
        frame.findViewWithTag<RadialView>("bloom").radial(intArrayOf(0xFFFFF0FF.toInt(), 0xE6E7AAFF.toInt(), 0x99783CAA.toInt(), 0x003C145A), floatArrayOf(0f, 0.3f, 0.55f, 0.72f))
        frame.findViewWithTag<RadialView>("touch").radial(intArrayOf(0xF2FFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.35f, 0.7f))
        for (id in listOf(R.id.a7_nat1, R.id.a7_nat2)) skeletons[id] = findViewById<ViewGroup>(id).getChildAt(0)
        // native #2's loading state pulses on the timeline
        findViewById<ViewGroup>(R.id.a7_nat2_skel).let { s -> for (i in 0 until s.childCount) s.getChildAt(i).tag = "nat2_skel_$i" }

        // Figma elements drawn natively: the art is the view's background, sized to the sprite box the layout gives it.
        // LAYERED ones get a hardware layer: drawn once, then the timeline only moves, scales and fades the layer.
        native.ids.forEach { id ->
            frame.findViewWithTag<View>(id).apply {
                background = native.art(id)
                if (id in native.layered) setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
        (frame.findViewWithTag<BakedView>("icon")).layers += listOf(art.iconGlow, art.icon)
        (frame.findViewWithTag<BakedView>("logoB")).layers += art.iconBlur
        (frame.findViewWithTag<BakedView>("dot")).layers += art.dot
        listOf("ghost0", "ghost1", "ghost2").forEach { frame.findViewWithTag<BakedView>(it).layers += art.ghost }

        prepare(frame)
        val zoom = player.store["zoom"]
        collect(frame) { v, id ->
            val z = when (id) { "logo", "logoB" -> zoom; "callzoom" -> player.store["callzoom"]; else -> null }
            bound += Bound(v, player.store[id], scene.origin(id).first, scene.origin(id).second, z)
        }
        tc = frame.findViewWithTag("wave_tc")

        hot(R.id.a7_hot_cta, "g04_cta") { leave(onExplore) }
        hot(R.id.a7_hot_secondary, "g04_secondary") { leave(onBrowse) }
        swipe()

        ads?.run {
            preload(activity)
            banner(activity, findViewById(R.id.a7_banner))
            native(activity, findViewById(R.id.a7_nat1), 1, slotResult(R.id.a7_nat1))
        }
        if (isAttachedToWindow && windowVisibility == VISIBLE) resume()
    }

    /** Back to the first frame of the splash (review tool, or after the host's own back navigation). */
    fun restart() {
        if (!started) return
        left = false
        player.replay()
    }

    /** Debug / review: open frozen at second [t] of the timeline (no events, no audio). */
    fun seekFrozen(t: Double) = player.seekFrozen(t)

    // ---------------------------------------------------------------- events from the player

    private val events = object : A7Player.Listener {
        override fun onAd() {
            var closed = false
            val close = { post { if (!closed) { closed = true; player.adClosed() } } }
            val a = ads
            if (a == null) close() else a.showInterstitial(activity) { close() }
        }

        override fun onNative2() {
            ads?.native(activity, findViewById(R.id.a7_nat2), 2, slotResult(R.id.a7_nat2))
                ?: collapse(R.id.a7_nat2)
        }

        override fun onWaitChanged(waiting: Boolean) {
            findViewById<View>(R.id.a7_swipe_pill).isClickable = waiting
        }

        override fun onHaptic(kind: Int) {
            performHapticFeedback(
                when (kind) {
                    0 -> HapticFeedbackConstants.CLOCK_TICK
                    1 -> HapticFeedbackConstants.VIRTUAL_KEY
                    2 -> HapticFeedbackConstants.KEYBOARD_TAP
                    else -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                },
            )
        }
    }

    /** An ad slot's answer: the ad is in (its skeleton fades) or there is none (the slot collapses). */
    private fun slotResult(slotId: Int): (Boolean) -> Unit = { ok ->
        post {
            val sk = skeletons[slotId]
            if (!ok) collapse(slotId)
            else if (sk != null) {
                sk.bringToFront() // the ad goes under it, whatever index the host used
                sk.animate().alpha(0f).setDuration(350).withEndAction { sk.visibility = GONE }
            }
        }
    }

    private fun collapse(slotId: Int) {
        val v = findViewById<View>(slotId)
        bound.firstOrNull { it.v === v }?.collapsed = true
        v.visibility = INVISIBLE
    }

    private fun leave(go: (() -> Unit)?) {
        if (left) return
        left = true
        player.leave()
        go?.invoke()
    }

    // ---------------------------------------------------------------- touch

    @SuppressLint("ClickableViewAccessibility")
    private fun hot(id: Int, pressTag: String, onClick: () -> Unit) {
        val enabled = { player.ctaEnabled() && !left }
        val v = findViewById<View>(id)
        hots += v
        v.visibility = GONE
        v.setOnTouchListener { _, e ->
            if (!enabled()) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> player.press(pressTag, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> player.press(pressTag, false)
            }
            false
        }
        v.setOnClickListener { if (enabled()) onClick() }
    }

    /** The feed takes the finger 1:1 (in design dp: the frame is scaled); a fling, a long enough drag or a tap commits. */
    @SuppressLint("ClickableViewAccessibility")
    private fun swipe() {
        val hit = findViewById<View>(R.id.a7_hot_swipe)
        var y0 = 0f; var t0 = 0L; var ly = 0f; var lt = 0L; var vel = 0f
        val unit = { dp * frame.scaleX } // px per design dp
        hit.setOnTouchListener { _, e ->
            if (player.store["hot_swipe"][Prop.POINTER] <= 0f) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { y0 = e.rawY; ly = y0; t0 = e.eventTime; lt = t0; vel = 0f; player.dragStart() }
                MotionEvent.ACTION_MOVE -> {
                    vel = (e.rawY - ly) / unit() / maxOf(1L, e.eventTime - lt)
                    ly = e.rawY; lt = e.eventTime
                    player.dragMove((e.rawY - y0) / unit())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = (e.rawY - y0) / unit()
                    player.dragEnd(dy, vel, abs(dy) < 8 && e.eventTime - t0 < 350)
                }
            }
            true
        }
        // the pill is also a button (tap / TalkBack / keyboard), so nobody gets stuck
        findViewById<View>(R.id.a7_swipe_pill).setOnClickListener { player.commit() }
    }

    // ---------------------------------------------------------------- layout

    /**
     * Not 20:9: the scene fills the screen around the design frame ([namvunhatle.r15.onboarding.core.Viewport]).
     * The layout holds the v1.3.3 boxes; this moves the ones [Scene] changed and adds the extra wall tiles.
     */
    private fun fill(scene: Scene) {
        frame.vp = scene.vp
        val tiles = frame.findViewWithTag<ViewGroup>("tiles")
        scene.extraTiles.keys.forEach { id ->
            tiles.addView(View(context).apply { tag = id; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, 0, LayoutParams(0, 0))
        }
        (A7Native.IDS + scene.extraTiles.keys + "sp_strip").forEach { id -> place(frame.findViewWithTag(id), scene.pos.getValue(id)) }
        place(frame.findViewWithTag("wave"), scene.wave)
        place(frame.findViewWithTag("sp_track"), scene.spTrack)
    }

    private fun px(v: Float) = (v * dp).roundToInt()

    private fun place(v: View, b: Box) {
        val lp = v.layoutParams as MarginLayoutParams
        lp.width = px(b.w); lp.height = px(b.h); lp.leftMargin = px(b.x); lp.topMargin = px(b.y)
        v.layoutParams = lp
    }

    /** Nothing is clipped to its parent (CSS overflow: visible) except by the design frame; CSS text-shadow → shadow layer. */
    private fun prepare(v: View) {
        if (v is ViewGroup) {
            if (v !== frame && v.id != R.id.a7_nat1 && v.id != R.id.a7_nat2 && v.id != R.id.a7_banner) { v.clipChildren = false; v.clipToPadding = false }
            for (i in 0 until v.childCount) prepare(v.getChildAt(i))
        }
        if (v is TextView && v.shadowRadius > 0f) v.setShadowLayer(Css.shadowRadius(v.shadowRadius * dp), 0f, 0f, v.shadowColor)
    }

    private fun collect(v: View, fn: (View, String) -> Unit) {
        (v.tag as? String)?.let { fn(v, it) }
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), fn)
    }

    // ---------------------------------------------------------------- frame loop + lifecycle

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        player.frame(frameTimeNanos)
        apply()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun apply() {
        for (b in bound) {
            val v = b.v; val e = b.el
            val shown = e.shown && !b.collapsed
            if (shown != b.shown) { v.visibility = if (shown) VISIBLE else INVISIBLE; b.shown = shown }
            if (!shown) continue
            val k = b.zoom?.let { exp(it[Prop.K]) } ?: 1f
            v.pivotX = b.ox * v.width; v.pivotY = b.oy * v.height
            v.translationX = e.x * dp; v.translationY = e.y * dp
            v.scaleX = e.sx * k; v.scaleY = e.sy * k
            v.rotation = e.rot
            v.alpha = e.alpha.coerceIn(0f, 1f)
        }
        wave.invalidate()
        if (lyric.isShown) lyric.invalidate()
        if (feed.isShown) feed.invalidate()
        val left = ((1 - player.store["clock"][Prop.P]) * player.times.tCount).roundToInt().coerceAtLeast(0)
        if (left != tcLeft) { tcLeft = left; tc.text = "-0:" + left.toString().padStart(2, '0') }
        if (player.uiVersion != ui) ui = player.uiVersion
        // the G04 buttons take touches only once they are live (web: pointer-events) — before that the feed's swipe
        // area lies under them
        val live = player.ctaEnabled() && !this.left
        for (h in hots) if ((h.visibility == VISIBLE) != live) h.visibility = if (live) VISIBLE else GONE
    }

    private fun resume() {
        if (running || !started) return
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun pause() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        // Leaving mid-trailer (home, call, lock) restarts it — but not on the ad (a real SDK opens its own Activity,
        // which stops this one), not once the trailer is over, and not after a CTA (the host is navigating).
        if (!player.atAd && !player.done && !left) player.replay()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) resume() else pause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (windowVisibility == VISIBLE) resume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        if (started) player.release()
        ads?.release()
    }
}
