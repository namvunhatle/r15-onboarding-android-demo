package namvunhatle.r15.onboarding.views

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import namvunhatle.r15.onboarding.core.A7Art
import namvunhatle.r15.onboarding.core.Css
import namvunhatle.r15.onboarding.core.El
import namvunhatle.r15.onboarding.core.Prop
import namvunhatle.r15.onboarding.core.Scene
import namvunhatle.r15.onboarding.core.Viewport
import kotlin.math.min
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/**
 * Holds the 360×800 design frame at its design size (dp) and scales itself to fit the screen — the XML twin of the
 * web's `transform: scale(s)` and of the Compose build's scaled Density. Children lay out in design dp.
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
        val a = ctx.obtainStyledAttributes(attrs, R.styleable.CssBox)
        fill0 = a.getColor(R.styleable.CssBox_fill, 0)
        fill1 = a.getColor(R.styleable.CssBox_fill2, fill0)
        angle = a.getFloat(R.styleable.CssBox_fillAngle, 180f)
        stop0 = a.getFloat(R.styleable.CssBox_fillStop0, 0f)
        stop1 = a.getFloat(R.styleable.CssBox_fillStop1, 1f)
        radius = a.getDimension(R.styleable.CssBox_cornerRadius, 0f)
        strokeColor = a.getColor(R.styleable.CssBox_strokeColor, 0)
        strokeWidth = a.getDimension(R.styleable.CssBox_strokeWidth, 0f)
        shadowY = a.getDimension(R.styleable.CssBox_shadowY, 0f)
        shadowBlur = a.getDimension(R.styleable.CssBox_shadowBlur, 0f)
        shadowColor = a.getColor(R.styleable.CssBox_shadowColor, 0)
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
        val a = ctx.obtainStyledAttributes(attrs, R.styleable.RingView)
        paint.strokeWidth = a.getDimension(R.styleable.RingView_ringStroke, dp(1.5f))
        paint.color = (a.getFloat(R.styleable.RingView_ringAlpha, 0.28f) * 255).roundToInt() shl 24 or 0xFFFFFF
        a.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(width / 2f, height / 2f, width / 2f - paint.strokeWidth / 2, paint)
    }

    override fun hasOverlappingRendering() = false
}

/** Draws pre-rendered art ([A7Art.Baked]) centred on this view at its own dp size — it may overflow, like a CSS shadow. */
class BakedView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    val layers = ArrayList<A7Art.Baked>()

    /** Opacity applied per draw call, no offscreen layer — a layer would be clipped to this view's bounds and cut
     *  off the glow that overflows them while it fades (CSS never clips a shadow). */
    override fun hasOverlappingRendering() = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        for (b in layers) {
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
                i == played - 1 -> 0xF5FFFFFF.toInt()
                i < played - 1 -> Scene.ACCENT
                else -> 0x38FFFFFF
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
