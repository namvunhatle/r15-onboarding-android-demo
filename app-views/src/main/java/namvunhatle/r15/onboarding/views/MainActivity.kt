package namvunhatle.r15.onboarding.views

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import namvunhatle.r15.onboarding.core.A7Art
import namvunhatle.r15.onboarding.core.A7Native
import namvunhatle.r15.onboarding.core.A7Player
import namvunhatle.r15.onboarding.core.A7Visual
import namvunhatle.r15.onboarding.core.Bleed
import namvunhatle.r15.onboarding.core.Box
import namvunhatle.r15.onboarding.core.Css
import namvunhatle.r15.onboarding.core.Dest
import namvunhatle.r15.onboarding.core.El
import namvunhatle.r15.onboarding.core.Prop
import namvunhatle.r15.onboarding.core.Scene
import namvunhatle.r15.onboarding.core.dumpExtra
import namvunhatle.r15.onboarding.core.immersive
import namvunhatle.r15.onboarding.core.seekExtra
import namvunhatle.r15.onboarding.core.viewport
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A7 onboarding — XML Views build. The scene is declared in XML (res/layout, generated from the Figma export) and
 * its Figma elements are drawn natively by :core ([A7Native]);
 * every vsync the shared timeline (:core) writes transform + opacity, and this class copies them onto the views.
 */
class MainActivity : Activity(), Choreographer.FrameCallback {
    private lateinit var player: A7Player
    private lateinit var frame: DesignFrame
    private lateinit var fab: View
    private lateinit var tc: TextView
    private lateinit var wave: WaveView
    private val dp by lazy { resources.displayMetrics.density }

    /** view ↔ store element, in layout order. */
    private class Bound(val v: View, val el: El, val ox: Float, val oy: Float, val zoom: El?) { var shown = true }
    private val bound = ArrayList<Bound>()
    private var ui = -1
    private var tcLeft = -1
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        setContentView(R.layout.activity_main)
        val scene = Scene(this, viewport())
        player = A7Player(this, scene)
        seekExtra()?.let(player::seekFrozen)
        val art = A7Art(this)

        frame = findViewById(R.id.frame)
        if (!scene.vp.isFrame) fill(scene)
        fab = findViewById(R.id.fab)
        wave = findViewById(R.id.wave_bars)
        wave.clock = player.store["clock"]
        wave.bars = Scene.WAVE_BARS.map { player.store[it] }

        // The two native slots come from one layout: tag + tint each copy.
        nativeSlot(R.id.nat1, "nat1", A7Visual.NATIVE_TINT_1)
        nativeSlot(R.id.nat2, "nat2", A7Visual.NATIVE_TINT_2)

        // Figma elements drawn natively: the art is the view's background, sized to the sprite box the layout gives it.
        // LAYERED ones get a hardware layer: drawn once, then the timeline only moves, scales and fades the layer.
        val native = A7Native(this, scene)
        if (dumpExtra()) native.dump(getExternalFilesDir("dump")!!)
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
        collect(frame) { v, id -> bound += Bound(v, player.store[id], scene.origin(id).first, scene.origin(id).second, if (id == "logo" || id == "logoB") zoom else null) }
        tc = frame.findViewWithTag("wave_tc")

        hot(R.id.hot_skip, null, { player.atAd }) { player.skipAd() }
        val ctaOk = { player.ctaEnabled() && player.dest == null }
        hot(R.id.hot_cta, "g04_cta", ctaOk) { player.go(Dest.PAYWALL) }
        hot(R.id.hot_secondary, "g04_secondary", ctaOk) { player.go(Dest.HOME) }
        hot(R.id.hot_paywall_close, null, { player.dest == Dest.PAYWALL }) { player.go(Dest.AI) }
        hot(R.id.hot_subscribe, null, { player.dest == Dest.PAYWALL }) { player.go(Dest.AI) }
        fab.setOnClickListener { player.replay() }
        // Long-press = next track — a review tool, not part of the onboarding.
        fab.setOnLongClickListener { Toast.makeText(this, "Nhạc: " + player.nextTrack(), Toast.LENGTH_SHORT).show(); true }
    }

    /**
     * Not 20:9: the scene fills the screen around the design frame ([namvunhatle.r15.onboarding.core.Viewport]).
     * The layout holds the v1.3.3 boxes; this moves the ones [Scene] changed, adds the extra wall tiles, and
     * surrounds each destination screenshot with its [Bleed].
     */
    private fun fill(scene: Scene) {
        val view = scene.view
        frame.vp = scene.vp
        val tiles = frame.findViewWithTag<ViewGroup>("tiles")
        scene.extraTiles.keys.forEach { id ->
            tiles.addView(View(this).apply { tag = id; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, 0, FrameLayout.LayoutParams(0, 0))
        }
        (A7Native.IDS + scene.extraTiles.keys).forEach { id -> place(frame.findViewWithTag(id), scene.pos.getValue(id)) }
        place(frame.findViewWithTag("wave"), scene.wave)
        place(frame.findViewWithTag("sp_track"), scene.spTrack)
        place(findViewById(R.id.punch), scene.punch)
        place(findViewById(R.id.hot_skip), scene.hotSkip)
        // full-screen layers take the whole viewport (their touches included); their children shift back to the frame
        (listOf("bridge") + Dest.entries.map(A7Player::destId)).forEach { id ->
            val box = frame.findViewWithTag<ViewGroup>(id)
            place(box, view)
            for (i in 0 until box.childCount) {
                val child = box.getChildAt(i)
                val lp = child.layoutParams as ViewGroup.MarginLayoutParams
                if (child is ImageView) {
                    lp.width = px(Scene.W); lp.height = px(Scene.H)
                    box.background = Bleed((child.drawable as BitmapDrawable).bitmap, scene.vp)
                }
                lp.leftMargin += px(-view.x); lp.topMargin += px(-view.y)
                child.layoutParams = lp
            }
        }
    }

    private fun px(v: Float) = (v * dp).roundToInt()

    private fun place(v: View, b: Box) {
        val lp = v.layoutParams as ViewGroup.MarginLayoutParams
        lp.width = px(b.w); lp.height = px(b.h); lp.leftMargin = px(b.x); lp.topMargin = px(b.y)
        v.layoutParams = lp
    }

    private fun nativeSlot(id: Int, tag: String, tint: IntArray) {
        val (c0, c1) = tint
        val slot = findViewById<CssBox>(id)
        slot.tag = tag
        slot.findViewById<CssBox>(R.id.nat_icon).tint(c0, c1, 135f)
        slot.findViewById<CssBox>(R.id.nat_media).tint(c0, c1, 135f)
        if (tag == "nat2") {
            val skel = slot.findViewById<ViewGroup>(R.id.nat_skel)
            skel.visibility = View.VISIBLE
            skel.tag = "nat2_skel"
            for (i in 0 until skel.childCount) skel.getChildAt(i).tag = "nat2_skel_$i"
        }
    }

    /** Nothing is clipped to its parent (CSS overflow: visible) except by the design frame; CSS text-shadow → shadow layer. */
    private fun prepare(v: View) {
        if (v is ViewGroup) {
            if (v !== frame) { v.clipChildren = false; v.clipToPadding = false }
            for (i in 0 until v.childCount) prepare(v.getChildAt(i))
        }
        if (v is TextView && v.shadowRadius > 0f) v.setShadowLayer(Css.shadowRadius(v.shadowRadius * dp), 0f, 0f, v.shadowColor)
    }

    private fun collect(v: View, fn: (View, String) -> Unit) {
        (v.tag as? String)?.let { fn(v, it) }
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), fn)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun hot(id: Int, pressTag: String?, enabled: () -> Boolean, onClick: () -> Unit) {
        val v = findViewById<View>(id)
        v.setOnTouchListener { _, e ->
            if (!enabled()) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressTag?.let { player.press(it, true) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressTag?.let { player.press(it, false) }
            }
            false
        }
        v.setOnClickListener { if (enabled()) onClick() }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        player.frame(frameTimeNanos)
        apply()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun apply() {
        for (b in bound) {
            val v = b.v; val e = b.el
            val shown = e.shown
            if (shown != b.shown) { v.visibility = if (shown) View.VISIBLE else View.INVISIBLE; b.shown = shown }
            if (!shown) continue
            val k = b.zoom?.let { exp(it[Prop.K]) } ?: 1f
            v.pivotX = b.ox * v.width; v.pivotY = b.oy * v.height
            v.translationX = e.x * dp; v.translationY = e.y * dp
            v.scaleX = e.sx * k; v.scaleY = e.sy * k
            v.rotation = e.rot
            v.alpha = e.alpha.coerceIn(0f, 1f)
        }
        wave.invalidate()
        val span = player.times.tG04 - player.times.tG01
        val left = ((1 - player.store["clock"][Prop.P]) * span).roundToInt().coerceAtLeast(0)
        if (left != tcLeft) { tcLeft = left; tc.text = "-0:" + left.toString().padStart(2, '0') }
        if (player.uiVersion != ui) {
            ui = player.uiVersion
            fab.visibility = if (player.atAd || player.dest != null) View.VISIBLE else View.GONE
            // the destination opened last goes on top
            player.destOrder.forEach { d -> frame.findViewWithTag<View>(A7Player.destId(d)).bringToFront() }
        }
    }

    override fun onStart() {
        super.onStart()
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onStop() {
        super.onStop()
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        // Leaving mid-trailer restarts it — but not on the ad (a real SDK opens its
        // own Activity) and not on a destination.
        if (!isChangingConfigurations && !player.atAd && player.dest == null) player.replay()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
