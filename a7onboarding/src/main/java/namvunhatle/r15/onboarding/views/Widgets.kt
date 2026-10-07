package namvunhatle.r15.onboarding.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import namvunhatle.r15.onboarding.R
import namvunhatle.r15.onboarding.core.A7Art
import namvunhatle.r15.onboarding.core.A7Script
import namvunhatle.r15.onboarding.core.A7Visual
import namvunhatle.r15.onboarding.core.Css
import namvunhatle.r15.onboarding.core.El
import namvunhatle.r15.onboarding.core.Later
import namvunhatle.r15.onboarding.core.Prop
import namvunhatle.r15.onboarding.core.Scene
import namvunhatle.r15.onboarding.core.Store
import namvunhatle.r15.onboarding.core.Viewport
import namvunhatle.r15.onboarding.core.Zen
import kotlin.math.min
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/**
 * Holds the 360×800 design frame at its design size (dp) and scales itself to fit the screen — the XML twin of the
 * web's `transform: scale(s)`. Children lay out in design dp.
 *
 * With a [vp] margin the view is the whole visible screen (so it draws and takes touches there too) and the frame
 * sits inside it at padding = the margin: children still lay out in frame coordinates.
 */
class DesignFrame(ctx: Context, attrs: AttributeSet?) : FrameLayout(ctx, attrs) {
    var vp = Viewport.FRAME
        set(v) {
            field = v
            // the padding is screen the scene fills, not a gutter; the root still clips this view to its bounds
            clipToPadding = false; clipChildren = v.isFrame
            setPadding(dp(v.mx).roundToInt(), dp(v.my).roundToInt(), dp(v.mx).roundToInt(), dp(v.my).roundToInt())
        }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(dp(vp.view.w).roundToInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(vp.view.h).roundToInt(), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val p = parent as View
        val s = min(p.width / dp(Scene.W), p.height / dp(Scene.H))
        pivotX = 0f; pivotY = 0f
        scaleX = s; scaleY = s
        translationX = (p.width - measuredWidth * s) / 2 - left
        translationY = (p.height - measuredHeight * s) / 2 - top
    }
}

/**
 * A box painted the CSS way: fill (flat or `linear-gradient(<angle>deg, c0 s0, c1 s1)`), border, rounded corners and a
 * `box-shadow` drawn with Paint.setShadowLayer (hardware-accelerated on API 28+). Children may follow ([clipChildrenToShape]).
 */
open class CssBox(ctx: Context, attrs: AttributeSet?) : FrameLayout(ctx, attrs) {
    var fill0 = 0; var fill1 = 0; var angle = 180f; var stop0 = 0f; var stop1 = 1f
    var radius = 0f; var strokeColor = 0; var strokeWidth = 0f
    var shadowY = 0f; var shadowBlur = 0f; var shadowColor = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var shader: Shader? = null // rebuilt only when size or colours change (onDraw runs every frame)

    init {
        setWillNotDraw(false)
        val a = ctx.obtainStyledAttributes(attrs, R.styleable.A7CssBox)
        fill0 = a.getColor(R.styleable.A7CssBox_a7_fill, 0)
        fill1 = a.getColor(R.styleable.A7CssBox_a7_fill2, fill0)
        angle = a.getFloat(R.styleable.A7CssBox_a7_fillAngle, 180f)
        stop0 = a.getFloat(R.styleable.A7CssBox_a7_fillStop0, 0f)
        stop1 = a.getFloat(R.styleable.A7CssBox_a7_fillStop1, 1f)
        radius = a.getDimension(R.styleable.A7CssBox_a7_cornerRadius, 0f)
        strokeColor = a.getColor(R.styleable.A7CssBox_a7_strokeColor, 0)
        strokeWidth = a.getDimension(R.styleable.A7CssBox_a7_strokeWidth, 0f)
        shadowY = a.getDimension(R.styleable.A7CssBox_a7_shadowY, 0f)
        shadowBlur = a.getDimension(R.styleable.A7CssBox_a7_shadowBlur, 0f)
        shadowColor = a.getColor(R.styleable.A7CssBox_a7_shadowColor, 0)
        a.recycle()
    }

    fun tint(c0: Int, c1: Int, deg: Float) { fill0 = c0; fill1 = c1; angle = deg; shader = null; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { shader = null }

    private fun gradient(): Shader = shader ?: Css.linear(angle, width.toFloat(), height.toFloat()).let { g ->
        LinearGradient(g[0], g[1], g[2], g[3], intArrayOf(fill0, fill1), floatArrayOf(stop0, stop1), Shader.TileMode.CLAMP)
    }.also { shader = it }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = min(radius, min(w, h) / 2)
        rect.set(0f, 0f, w, h)
        if (shadowBlur > 0f) {
            paint.reset(); paint.isAntiAlias = true; paint.color = 0
            paint.setShadowLayer(Css.shadowRadius(shadowBlur), 0f, shadowY, shadowColor)
            canvas.drawRoundRect(rect, r, r, paint)
        }
        paint.reset(); paint.isAntiAlias = true
        if (fill0 != fill1) paint.shader = gradient() else paint.color = fill0
        canvas.drawRoundRect(rect, r, r, paint)
        if (strokeWidth > 0f) {
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth; paint.color = strokeColor
            val i = strokeWidth / 2
            rect.inset(i, i)
            canvas.drawRoundRect(rect, r - i, r - i, paint)
        }
    }
}

/** A stroked circle filling its box (the web's `.ring`: CSS border, so the stroke sits inside the box). */
class RingView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        val a = ctx.obtainStyledAttributes(attrs, R.styleable.A7RingView)
        paint.strokeWidth = a.getDimension(R.styleable.A7RingView_a7_ringStroke, dp(1.5f))
        paint.color = (a.getFloat(R.styleable.A7RingView_a7_ringAlpha, 0.28f) * 255).roundToInt() shl 24 or (A7Visual.RING and 0xFFFFFF)
        a.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(width / 2f, height / 2f, width / 2f - paint.strokeWidth / 2, paint)
    }

    override fun hasOverlappingRendering() = false
}

/** Draws pre-rendered art ([A7Art.Baked]) centred on this view at its own dp size — it may overflow, like a CSS shadow. */
class BakedView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    /** Baked on the start-up pool; the first draw waits for them. */
    val layers = ArrayList<Later<A7Art.Baked>>()

    /** Opacity applied per draw call, no offscreen layer — a layer would be clipped to this view's bounds and cut
     *  off the glow that overflows them while it fades (CSS never clips a shadow). */
    override fun hasOverlappingRendering() = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        for (l in layers) {
            val b = l.value
            val d = dp(b.dp)
            dst.set(width / 2f - d / 2, height / 2f - d / 2, width / 2f + d / 2, height / 2f + d / 2)
            canvas.drawBitmap(b.bitmap, null, dst, paint)
        }
    }
}

/** A7 / Progress Wave bars: played = accent, head = white + accent glow; each bar's scaleY comes from the store. */
class WaveView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    lateinit var clock: El
    lateinit var bars: List<El>
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Scene.ACCENT
        maskFilter = BlurMaskFilter(Css.shadowRadius(8f * ctx.resources.displayMetrics.density), BlurMaskFilter.Blur.NORMAL)
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        if (!::bars.isInitialized) return
        val u = dp(1f)
        val played = (clock[Prop.P] * bars.size).roundToInt()
        val step = (282f - 3f) / (bars.size - 1)
        bars.forEachIndexed { i, el ->
            val h = Scene.WAVE_H[i] * el.sy * u
            if (h <= 0f) return@forEachIndexed
            val x = i * step * u
            val top = 14f * u - h / 2
            val r = min(1.5f * u, h / 2)
            if (i == played - 1) {
                rect.set(x - u, top - u, x + 4 * u, top + h + u)
                canvas.drawRoundRect(rect, r + u, r + u, glow)
            }
            paint.color = when {
                i == played - 1 -> Zen.Color.Content.OnDarkOverlay.Strongest
                i < played - 1 -> Zen.Color.Background.Active.Accent.Solid
                else -> Zen.Color.Content.OnDarkOverlay.Disabled
            }
            rect.set(x, top, x + 3 * u, top + h)
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}

/** A group whose content may overflow (text-shadow glow): fades per draw call instead of through a clipped layer. */
class GlowFrame(ctx: Context, attrs: AttributeSet?) : FrameLayout(ctx, attrs) {
    override fun hasOverlappingRendering() = false
}

/** One line of text in design dp: font, size, letter spacing (px, as CSS), colour. */
private class Txt(ctx: Context, face: Typeface, size: Float, trackingPx: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face; textSize = size * ctx.resources.displayMetrics.density; letterSpacing = trackingPx / size; this.color = color
    }
    /** Baseline that centres the text vertically on [cy] (px). */
    fun baseline(cy: Float) = cy - (p.ascent() + p.descent()) / 2
}

/**
 * A7 / Lyric Card's content, 1.6.4 (`.line1` / `.reel` / `.line2` in index.css): "Hey" + the name reel + one still
 * line. The reel is a window over a column of names; only the current one wears the pill (its own layer, so it can
 * fade away from a name without the text going with it), the last and next ones are bare text at 28 %, 78 %. The
 * window's top and bottom fade out (static mask). Animated by the timeline: `reel_col` (y), `pill<i>` (opacity +
 * scale, origin left-centre), `pillbg<i>` (opacity).
 */
class LyricView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private var store: Store? = null
    private lateinit var hey: Txt
    private lateinit var name: Txt
    private lateinit var line2: Txt
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val r = RectF()

    fun bind(store: Store, semibold: Typeface) {
        this.store = store
        hey = Txt(context, semibold, 32f, -0.96f, 0xF5FFFFFF.toInt())
        name = Txt(context, semibold, 32f, -0.96f, 0xFFFFFFFF.toInt())
        line2 = Txt(context, semibold, 20f, -0.72f, 0x99FFFFFF.toInt())
        glow.color = 0; glow.setShadowLayer(dp(14f) / 2, 0f, dp(4f), 0x52BB4ABF)
        hi.strokeWidth = dp(1f); hi.color = 0x38FFFFFF
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val st = store ?: return
        val u = dp(1f)
        // line 1: 52 tall at y 62 (card padding), centred on y 88
        val cy = 88 * u
        c.drawText("Hey", 24 * u, hey.baseline(cy), hey.p)
        val px = 24 * u + hey.p.measureText("Hey") + 10 * u // pill column x (reel left 22 dp further in)
        // reel window: y 19 → 157, from 22 dp left of the pills to the card's right edge
        val wl = px - 22 * u; val wt = 19 * u; val wb = 157 * u
        val save = c.saveLayer(wl, wt, width.toFloat(), wb, null)
        val colY = st["reel_col"].y * u
        A7Script.REEL.forEachIndexed { i, n ->
            val e = st["pill$i"]
            val a = e.alpha.coerceIn(0f, 1f)
            if (a <= 0f) return@forEachIndexed
            val top = 66 * u + i * A7Script.REEL_H * u + colY
            val w = name.p.measureText(n) + 28 * u
            c.withSave {
                translate(px, top + 22 * u); scale(e.sx, e.sy); translate(0f, -22 * u)
                val ba = st["pillbg$i"].alpha.coerceIn(0f, 1f) * a
                if (ba > 0f) {
                    r.set(0f, 0f, w, 44 * u)
                    bg.shader = LinearGradient(0f, 0f, 0f, 44 * u, 0xFFC95ACD.toInt(), 0xFFA93FAE.toInt(), Shader.TileMode.CLAMP)
                    val al = (ba * 255).toInt()
                    glow.alpha = al; drawRoundRect(r, 14 * u, 14 * u, glow)
                    bg.alpha = al; drawRoundRect(r, 14 * u, 14 * u, bg)
                    // inset 0 1px 0 white 22 %: a 1 px highlight along the top edge
                    hi.alpha = (0x38 * ba).toInt()
                    withClip(r) { drawLine(14 * u, 0.5f * u, w - 14 * u, 0.5f * u, hi) }
                }
                name.p.alpha = (a * 255).toInt()
                drawText(n, 14 * u, name.baseline(22 * u), name.p)
            }
        }
        // the window fades out at the top and bottom (mask-image 0 → 34 % → 66 % → 100 %)
        mask.shader = LinearGradient(0f, wt, 0f, wb, intArrayOf(0, -0x1000000, -0x1000000, 0), floatArrayOf(0f, 0.34f, 0.66f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(wl, wt, width.toFloat(), wb, mask)
        c.restoreToCount(save)
        // line 2: 28 tall at y 166 (line 1 bottom 114 + gap 52)
        c.drawText("pick up, it’s for you ♪", 24 * u, line2.baseline(180 * u), line2.p)
    }
}

/**
 * 1.6 · the AI feed on the G03 phone's screen (`.feedwrap` in index.css). Built in the G02 phone's screen space
 * (216 × 481) and scaled by [Scene.FEED_FIT] onto the phone's body. Card 1 is the G02 screen (Morning Glow · for Sam),
 * card 2 is "Summer Crush · Pop · for Emma", drawn here from gradients like the web's CSS (no new asset). The stack's
 * y comes from `feed_stack` (feed units), card 2's name from `c2_name`, its progress from `c2_wave`.
 */
class FeedView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private var store: Store? = null
    private var card1: (() -> Bitmap)? = null
    private lateinit var title: Txt
    private lateinit var sub: Txt
    private lateinit var name: Txt
    private lateinit var status: Txt
    private lateinit var tag: Txt
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val r = RectF()
    private val clip = Path()

    fun bind(store: Store, card1: () -> Bitmap, regular: Typeface, medium: Typeface, semibold: Typeface) {
        this.store = store; this.card1 = card1
        title = Txt(context, semibold, 17f, -0.4f, -1)
        sub = Txt(context, regular, 8f, 0f, 0xBFFFFFFF.toInt())
        name = Txt(context, semibold, 31f, -0.5f, -1)
        status = Txt(context, medium, 7.5f, 0f, -1)
        tag = Txt(context, semibold, 9f, 0f, 0xFF0D0D0D.toInt())
        listOf(title, sub, name).forEach { it.p.textAlign = Paint.Align.CENTER }
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val st = store ?: return
        val u = dp(1f)
        val f = Scene.FEED
        val k = Scene.FEED_FIT
        c.withTranslation(f.x * u, f.y * u) {
            scale(k, k)
            // in feed units (216 × 481) from here
            val w = Scene.FEED_SCR.w * u; val h = Scene.FEED_SCR.h * u
            clip.reset(); clip.addRoundRect(0f, 0f, w, h, 26 * u, 26 * u, Path.Direction.CW)
            withClip(clip) {
                p.shader = null; p.color = 0xFF111012.toInt(); drawRect(0f, 0f, w, h, p)
                translate(0f, st["feed_stack"].y * u)
                card1?.invoke()?.let { r.set(0f, 0f, w, h); drawBitmap(it, null, r, p) }
                aiTag(this, u)
                translate(0f, h)
                card2(this, st, u, w, h)
            }
        }
    }

    private fun aiTag(c: Canvas, u: Float) {
        val tw = tag.p.measureText("Made by AI") + 12 * u
        r.set(17 * u, 44 * u, 17 * u + tw, 60 * u)
        p.shader = null; p.color = 0xFFFCFCFC.toInt()
        c.drawRoundRect(r, 8 * u, 8 * u, p)
        c.drawText("Made by AI", 23 * u, tag.baseline(52 * u), tag.p)
    }

    private fun card2(c: Canvas, st: Store, u: Float, w: Float, h: Float) {
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF7B4636.toInt(), 0xFF3D2420.toInt(), 0xFF121011.toInt()), floatArrayOf(0f, 0.38f, 0.62f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        c.drawText("9:30", 16 * u, status.baseline(13 * u), status.p)
        p.color = 0xFF000000.toInt(); c.drawCircle(108 * u, 13 * u, 4 * u, p)
        // cover: warm sky, low sun, two swells
        r.set(9.5f * u, 36.5f * u, 206.5f * u, 232.5f * u)
        c.withClip(Path().apply { addRoundRect(r, 12 * u, 12 * u, Path.Direction.CW) }) {
            p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0xFFFF8F6B.toInt(), 0xFFFFB36B.toInt(), 0xFFFFD88A.toInt()), floatArrayOf(0f, 0.45f, 0.7f), Shader.TileMode.CLAMP)
            drawRect(r, p)
            val sx = r.left + 99 * u; val sy = r.top + 106 * u
            p.shader = RadialGradient(sx, sy, 36 * u, intArrayOf(0xFFFFF8DC.toInt(), 0xFFFFE9A8.toInt(), 0x00FFE9A8), floatArrayOf(0f, 0.6f / 0.72f, 1f), Shader.TileMode.CLAMP)
            drawCircle(sx, sy, 36 * u, p)
            p.shader = null
            p.color = 0xFF2FA8B5.toInt(); drawOval(r.left - 40 * u, r.top + 128 * u, r.left + 160 * u, r.top + 248 * u, p)
            p.color = 0xFF1D7590.toInt(); drawOval(r.left + 70 * u, r.top + 142 * u, r.left + 260 * u, r.top + 252 * u, p)
        }
        aiTag(c, u)
        c.drawText("Summer Crush", w / 2, title.baseline(259 * u), title.p)
        c.drawText("Pop · for Emma", w / 2, sub.baseline(277 * u), sub.p)
        // EMMA lights on b20½ (opacity .35 → 1, scale 1.18 → 1 about 50 % 60 %); it lifts off at the fly-in
        val e = st["c2_name"]
        val a = e.alpha.coerceIn(0f, 1f)
        if (a > 0f) c.withSave {
            translate(w / 2, (294 + 38 * 0.6f) * u); scale(e.sx, e.sy); translate(-w / 2, -(294 + 38 * 0.6f) * u)
            name.p.alpha = (a * 255).toInt()
            drawText("EMMA", w / 2, name.baseline(313 * u), name.p)
        }
        // waveform: 22 bars, 104 wide at y 344, each lights as the teaser plays (b20 → b23)
        val prog = st["c2_wave"][Prop.P]
        val step = (104f - 2f) / 21f
        for (i in 0 until 22) {
            val bh = maxOf(4f, Scene.WAVE_H[i] * 0.6f) * u
            val x = (56f + i * step) * u
            p.color = if (prog > 0f && i <= prog * 22) -1 else 0x59FFFFFF
            r.set(x, 353 * u - bh / 2, x + 2 * u, 353 * u + bh / 2)
            c.drawRoundRect(r, u, u, p)
        }
    }
}

/** A radial glow filling its box: [colors] at [stops] of the radius (CSS radial-gradient(circle, …)). */
class RadialView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var colors = intArrayOf(0, 0)
    private var stops = floatArrayOf(0f, 1f)

    fun radial(colors: IntArray, stops: FloatArray) { this.colors = colors; this.stops = stops; p.shader = null; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { p.shader = null }
    override fun hasOverlappingRendering() = false

    override fun onDraw(c: Canvas) {
        val rad = width / 2f
        if (p.shader == null) p.shader = RadialGradient(rad, height / 2f, rad, colors, stops, Shader.TileMode.CLAMP)
        c.drawCircle(rad, height / 2f, rad, p)
    }
}
