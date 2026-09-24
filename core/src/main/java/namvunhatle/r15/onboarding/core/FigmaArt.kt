package namvunhatle.r15.onboarding.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import kotlin.math.roundToInt

/**
 * A Figma node drawn natively — vector shapes, real text, gradients and effects from the node's own properties.
 *
 * Content is written in design-frame dp, i.e. Figma's coordinates on the 360×800 screen, so every number can be read
 * against the inspector. It is clipped to [box]: the rectangle the v1.3.3 sprite of the same element occupied. The
 * shared timeline animates that box (translation, scale around its origin, opacity), so keeping it unchanged keeps the
 * motion identical to the sprite build.
 */
abstract class FigmaArt(val box: Box) : Drawable() {
    protected abstract fun paint(c: Canvas)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = b.width() / box.w
        canvas.withTranslation(b.left.toFloat(), b.top.toFloat()) {
            scale(s, s)
            translate(-box.x, -box.y)
            clipRect(box.x, box.y, box.x + box.w, box.y + box.h)
            paint(this)
        }
    }

    // Opacity is animated on the element's layer, never on the drawable.
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A Figma text style. [tracking] = letter spacing in px, [line] = line height in px (Figma "fixed" or % × size). */
class Type(val face: Typeface, val size: Float, val line: Float, val tracking: Float = 0f) {
    fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face; textSize = size; letterSpacing = tracking / size; this.color = color
        this.style = style; strokeWidth = stroke; strokeJoin = Paint.Join.MITER
        // Figma lays glyphs out unhinted at fractional positions; so do we (hinting snaps edges by up to ½ px).
        hinting = Paint.HINTING_OFF; isSubpixelText = true
    }

    /** Baseline of a line whose line box starts at [top]: the glyphs sit centred in the line box, as in Figma. */
    fun baseline(p: Paint, top: Float): Float {
        val fm = p.fontMetrics
        return top + (line - (fm.descent - fm.ascent)) / 2 - fm.ascent
    }
}

/** One line of text. [x] is the left edge, or the centre when [center]. */
class Line(val text: String, val x: Float, val top: Float, val center: Boolean = false)

fun Canvas.text(l: Line, t: Type, p: Paint) {
    p.textAlign = if (l.center) Paint.Align.CENTER else Paint.Align.LEFT
    drawText(l.text, l.x, t.baseline(p, l.top), p)
}

/** Figma stroke alignments. */
enum class Align { INSIDE, CENTER, OUTSIDE }

/**
 * A Figma linear gradient. [t] is the node's `gradientTransform` (m00, m01, m02, m10, m11, m12): it maps the node's
 * unit box into gradient space, where the ramp runs from (0, ½) to (1, ½). Its inverse, scaled to [w]×[h], becomes the
 * shader's local matrix, so non-square boxes keep Figma's skewed isolines instead of an approximated angle.
 */
fun figmaLinear(t: FloatArray, w: Float, h: Float, colors: IntArray, stops: FloatArray? = null): Shader {
    val g = Matrix().apply { setValues(floatArrayOf(t[0], t[1], t[2], t[3], t[4], t[5], 0f, 0f, 1f)) }
    val local = Matrix().apply { g.invert(this); postScale(w, h) }
    return LinearGradient(0f, 0f, 1f, 0f, colors, stops, Shader.TileMode.CLAMP).apply { setLocalMatrix(local) }
}

/** A Figma `DROP_SHADOW` (offset, blur, colour) on a paint: CSS semantics, σ = blur / 2. Units follow the canvas. */
fun Paint.dropShadow(dy: Float, blur: Float, color: Int) = apply { setShadowLayer(Css.shadowRadius(blur), 0f, dy, color) }

private fun argb(a: Float, rgb: Int) = ((a * 255f + .5f).toInt() shl 24) or (rgb and 0xFFFFFF)
private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/* ------------------------------------------------------------------ backgrounds ------------------------------------------------------------------ */

/**
 * P01 · Splash › BG › R15 (`15552:118420`). Frame fills: an image (smooth mesh, kept as a bitmap) under a black 0→100 %
 * ramp at 52 %; children: the grid (vector union), two blurred #51208D ellipses (baked, [A7Glow]), and a 40 % smoke
 * image (kept as a bitmap — it is a photo in Figma too).
 */
class SplashBg(box: Box, private val base: Bitmap, private val glow: Bitmap, private val wisps: Bitmap, private val grid: Path) : FigmaArt(box) {
    private val shade = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 800f, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP); alpha = (0.52f * 255).toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = figmaLinear(
            floatArrayOf(-0.25712138f, 1.0826572f, 0.07550406f, -1.0826572f, -0.32707718f, 1.2325051f), GRID_W, GRID_H,
            intArrayOf(0xFF405231.toInt(), 0xFF282847.toInt(), 0xFF1B1B3D.toInt(), 0xFF474765.toInt()),
            floatArrayOf(0f, 0.33806920f, 0.69549137f, 1f),
        )
    }

    override fun paint(c: Canvas) {
        c.drawBitmap(base, null, FULL, FILTER)
        c.drawRect(FULL, shade)
        c.withTranslation(-417.79883f, -358.01758f) { drawPath(grid, gridPaint) }
        c.drawBitmap(glow, null, FULL, FILTER)
        c.drawBitmap(wisps, null, RectF(0f, 0f, 360f, 139f), FILTER)
    }

    companion object {
        const val GRID_W = 1123.4404f
        const val GRID_H = 1267.0859f
        val FULL = RectF(0f, 0f, Scene.W, Scene.H)
        /** Ellipse 182 / 183: #51208D, layer blur 121.1. */
        val GLOWS = listOf(
            A7Glow.Ellipse(199f, 427f, floatArrayOf(0.98277295f, 0.18481699f, 275.25586f, -0.18481699f, 0.98277295f, -111.93274f), 0xFF51208D.toInt(), 1f, 121.1f),
            A7Glow.Ellipse(103f, -116f, 390f, 148f, 0xFF51208D.toInt(), 1f, 121.1f),
        )
    }
}

/** G01–G04 › BG › Spotlight BG: #0D0D0D + "Glow · main" (520×420, 70 %, blur 170) + "Glow · rim" (240×200, 35 %, blur 110). */
class Spotlight(box: Box, private val baked: Bitmap) : FigmaArt(box) {
    override fun paint(c: Canvas) = c.drawBitmap(baked, null, SplashBg.FULL, FILTER)

    companion object {
        /** Main glow top (rim sits 20 dp higher), main colour, rim colour — per screen. */
        val SPOTS = mapOf(
            "bg_G01" to Triple(430f, 0xFF4752F0, 0xFFBB4ABF), "bg_G02a" to Triple(268f, 0xFFBB4ABF, 0xFF4752F0),
            "bg_G02b" to Triple(268f, 0xFFE854B2, 0xFF7755E7), "bg_G02c" to Triple(268f, 0xFF02B19C, 0xFF0A85FF),
            "bg_G03" to Triple(194f, 0xFF7755E7, 0xFFE854B2), "bg_G04" to Triple(72f, 0xFFBB4ABF, 0xFFE854B2),
        )

        fun bake(id: String): Bitmap {
            val (y, main, rim) = SPOTS.getValue(id)
            return A7Glow.bake(
                Scene.W, Scene.H, 0xFF0D0D0D.toInt(),
                listOf(A7Glow.Ellipse(-80f, y, 520f, 420f, main.toInt(), 0.7f, 170f), A7Glow.Ellipse(60f, y - 20f, 240f, 200f, rim.toInt(), 0.35f, 110f)),
            )
        }
    }
}

/* ------------------------------------------------------------------ type-led art ------------------------------------------------------------------ */

/**
 * Headline stacks (G01–G04 "Headline", Anton 52 / 92 %): a white line plus stroke-only echoes offset below it.
 * Figma draws children back to front; so does [lines]. Outside strokes are the stroke outline minus the glyphs,
 * computed once as a path — no offscreen layer per frame.
 */
class Headline(box: Box, anton: Typeface, private val lines: List<Echo>) : FigmaArt(box) {
    /** [stroke] = 0 → filled white line; otherwise a [stroke]-wide echo aligned [align] to the glyph outline. */
    class Echo(val line: Line, val color: Int, val stroke: Float = 0f, val align: Align = Align.CENTER)

    private val type = Type(anton, 52f, 52f * 0.92f)
    private val ops: List<Pair<Paint, Path?>> = lines.map { e ->
        when {
            e.stroke == 0f -> type.paint(e.color) to null
            e.align == Align.CENTER -> type.paint(e.color, Paint.Style.STROKE, e.stroke) to null
            else -> {
                val p = type.paint(e.color).apply { textAlign = if (e.line.center) Paint.Align.CENTER else Paint.Align.LEFT }
                val glyphs = Path().also { p.getTextPath(e.line.text, 0, e.line.text.length, e.line.x, type.baseline(p, e.line.top), it) }
                val outline = Path()
                @Suppress("DEPRECATION")
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = e.stroke * 2; strokeJoin = Paint.Join.MITER }
                    .getFillPath(glyphs, outline)
                outline.op(glyphs, Path.Op.DIFFERENCE)
                p to outline
            }
        }
    }

    override fun paint(c: Canvas) = render(c)

    /** Draw into a canvas that is already in frame dp (lets other art embed the stack). */
    fun render(c: Canvas) = lines.forEachIndexed { i, e ->
        val (p, path) = ops[i]
        if (path != null) c.drawPath(path, p) else c.text(e.line, type, p)
    }
}

/** Plain text nodes (tagline, disclaimer). */
class TextArt(box: Box, private val type: Type, color: Int, private val lines: List<Line>) : FigmaArt(box) {
    private val p = type.paint(color)
    override fun paint(c: Canvas) = lines.forEach { c.text(it, type, p) }
}

/* ------------------------------------------------------------------ components ------------------------------------------------------------------ */

/**
 * A7 / Genre Tile (`15553:122266`): 168×120, radius 16, linear gradient (same transform on every variant), title
 * Be Vietnam Pro SemiBold 32/40 −1.44 at 95.7 % white, bottom-anchored at padding 16. On the wall it is rotated 12°
 * about its centre ([cx], [cy]); the sprite build cut it at the screen edge, so does [box].
 */
class GenreTile(box: Box, private val cx: Float, private val cy: Float, c0: Int, c1: Int, type: Type, private val title: List<String>) : FigmaArt(box) {
    private val t = type
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = figmaLinear(floatArrayOf(0.8f, 0.6f, -0.2f, -0.6f, 0.8f, 0.4f), 168f, 120f, intArrayOf(c0, c1)) }
    private val ink = t.paint(argb(0.95686275f, 0xFFFFFF))

    override fun paint(c: Canvas) {
        c.withTranslation(cx, cy) {
            rotate(12f); translate(-84f, -60f)
            drawRoundRect(0f, 0f, 168f, 120f, 16f, 16f, fill)
            title.forEachIndexed { i, s -> text(Line(s, 16f, 104f - t.line * (title.size - i)), t, ink) }
        }
    }

    companion object {
        /** Variant colours of `A7 / Genre Tile`. */
        val PINK = 0xFFE854B2.toInt() to 0xFF46004E.toInt()
        val ORANGE = 0xFFED8002.toInt() to 0xFF590023.toInt()
        val VIOLET = 0xFF7755E7.toInt() to 0xFF0F0075.toInt()
        val BLUE = 0xFF10AEDA.toInt() to 0xFF002B56.toInt()
    }
}

/**
 * A7 / Phone (`15553:122095`). Hero = 232×496, radius 32, #0E0E12, inside stroke 2 @ 10 % white, drop shadow
 * 0 24 48 @ 45 %; the screen is inset 8 with radius 24 and is an image fill in Figma, so it stays a bitmap.
 * Settled is the same component at [k] = 119.68 / 232.
 */
class Phone(box: Box, private val frame: RectF, private val screen: Bitmap) : FigmaArt(box) {
    private val k = frame.width() / 232f
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E0E12.toInt() }.dropShadow(24f * k, 48f * k, argb(0.45f, 0))
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * k; color = argb(0.1f, 0xFFFFFF) }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF050507.toInt() }
    private val holeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * k; color = 0xFF2A2A33.toInt() }
    private val inner = RectF(frame.left + 8 * k, frame.top + 8 * k, frame.right - 8 * k, frame.bottom - 8 * k)
    private val innerPath = Path().apply { addRoundRect(inner, 24 * k, 24 * k, Path.Direction.CW) }

    override fun paint(c: Canvas) {
        val r = 32f * k
        c.drawRoundRect(frame, r, r, body)
        c.withClip(innerPath) { drawBitmap(screen, null, inner, FILTER) }
        val cx = frame.centerX(); val cy = frame.top + 22f * k; val hr = 5f * k
        c.drawCircle(cx, cy, hr, hole)
        c.drawCircle(cx, cy, hr - 0.5f * k, holeRim)
        val i = k
        c.drawRoundRect(frame.left + i, frame.top + i, frame.right - i, frame.bottom - i, r - i, r - i, rim)
    }
}

/**
 * A7 / Name Sticker (`15554:123066`): white pill h 44, padding 16/8, outside stroke 4 in the variant colour, drop
 * shadow 0 8 16 @ 35 %, name Be Vietnam Pro SemiBold 20/28 −0.72 at 95 % black. [cx], [cy] = centre, [rot] = Figma
 * rotation (counter-clockwise positive).
 */
class Sticker(box: Box, type: Type, private val name: String, private val w: Float, private val cx: Float, private val cy: Float, private val rot: Float, stroke: Int) : FigmaArt(box) {
    private val t = type
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val shadowed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke }.dropShadow(8f, 16f, argb(0.35f, 0))
    private val ink = t.paint(argb(0.95f, 0))

    override fun paint(c: Canvas) {
        c.withTranslation(cx, cy) {
            rotate(-rot)
            val hw = w / 2
            drawRoundRect(-hw - 4, -26f, hw + 4, 26f, 26f, 26f, shadowed) // outside stroke + its shadow
            drawRoundRect(-hw, -22f, hw, 22f, 22f, 22f, fill)
            text(Line(name, -hw + 16f, -14f), t, ink)
        }
    }
}

/** G04 › Name ring › Center (`15554:123107`): headline stack + "Name slot" (dashed pill, inside stroke 2, dash 6/4). */
class NameCenter(box: Box, anton: Typeface, type: Type) : FigmaArt(box) {
    private val head = Headline(box, anton, listOf(
        Headline.Echo(Line("YOURS", 180.5f, 100f, center = true), WHITE),
        Headline.Echo(Line("IS NEXT.", 180.5f, 184f, center = true), 0xFFE854B2.toInt(), 1.5f, Align.OUTSIDE),
        Headline.Echo(Line("IS NEXT.", 180.5f, 178f, center = true), 0xFFBB4ABF.toInt(), 1.5f, Align.OUTSIDE),
        Headline.Echo(Line("IS NEXT.", 180.5f, 172f, center = true), WHITE_96),
    ))
    private val t = type
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFBB4ABF.toInt(); pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val ink = t.paint(argb(0.8f, 0xFFFFFF))

    override fun paint(c: Canvas) {
        head.render(c)
        c.drawRoundRect(114f, 245f, 247f, 283f, 19f, 19f, dash)
        c.text(Line("Your name", 131f, 250f), t, ink)
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val WHITE_96 = 0xF5FFFFFF.toInt()
    }
}

/** Button/Main XLarge, Primary (CTA): pill 328×56 #BB4ABF, shadow 0 1 1 @ 4 %, label SemiBold 16/24 −0.4 white. */
class CtaButton(box: Box, type: Type, private val label: String) : FigmaArt(box) {
    private val t = type
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBB4ABF.toInt() }.dropShadow(1f, 1f, argb(0.04f, 0))
    private val ink = t.paint(0xFFFFFFFF.toInt())
    override fun paint(c: Canvas) {
        c.drawRoundRect(16f, 343f, 344f, 399f, 28f, 28f, fill)
        c.text(Line(label, 180f, 359f, center = true), t, ink)
    }
}

/** Plain label on a transparent Button/Flat. */
class FlatButton(box: Box, type: Type, private val label: String, private val top: Float, color: Int) : FigmaArt(box) {
    private val t = type
    private val ink = t.paint(color)
    override fun paint(c: Canvas) = c.text(Line(label, 180f, top, center = true), t, ink)
}

/** A7 / Ad Slot · Banner (`15553:121168`) as placed under the P01 progress block. */
class BannerAd(box: Box, private val semi12: Type, private val reg10: Type, private val med10: Type) : FigmaArt(box) {
    private val white = Paint().apply { color = 0xFFFFFFFF.toInt() }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.06f, 0x010101) }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFBA1A.toInt() }
    private val head = semi12.paint(argb(0.95f, 0))
    private val body = reg10.paint(argb(0.64f, 0))
    private val ad = med10.paint(argb(0.86f, 0))

    override fun paint(c: Canvas) {
        c.drawRect(0f, 692f, 360f, 752f, white)
        c.drawRoundRect(12f, 704f, 48f, 740f, 8f, 8f, icon)
        c.text(Line("Advertiser headline", 60f, 706f), semi12, head)
        c.text(Line("One line from the ad network", 60f, 722f), reg10, body)
        c.drawRoundRect(322f, 714f, 348f, 730f, 8f, 8f, badge)
        c.text(Line("Ad", 328f, 714f), med10, ad)
    }
}

/** Status bar (Material 3 kit): "9:30" Roboto 14/20 +0.25, Wi-Fi / signal / battery glyphs, punch-hole marker. */
class StatusBar(box: Box, private val time: Type, private val wifi: Drawable, private val signal: Drawable, private val battery: Drawable) : FigmaArt(box) {
    private val ink = time.paint(0xFFFFFFFF.toInt())
    private val cam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    override fun paint(c: Canvas) {
        c.text(Line("9:30", 16f, 10f), time, ink)
        icon(c, wifi, 298.66669f, 14.960938f, 14.6667f, 10.3733f)
        icon(c, signal, 313.33334f, 13.333984f, 13.3333f, 13.3333f)
        icon(c, battery, 331.75f, 12.916016f, 8.5f, 14.1667f)
        c.drawCircle(180f, 20f, 12f, cam)
    }

    /** Drawable bounds are whole units; draw at 1/16 dp so the fractional Figma sizes survive. */
    private fun icon(c: Canvas, d: Drawable, x: Float, y: Float, w: Float, h: Float) {
        c.withTranslation(x, y) {
            scale(1 / 16f, 1 / 16f)
            d.setBounds(0, 0, (w * 16).roundToInt(), (h * 16).roundToInt()); d.draw(this)
        }
    }
}
