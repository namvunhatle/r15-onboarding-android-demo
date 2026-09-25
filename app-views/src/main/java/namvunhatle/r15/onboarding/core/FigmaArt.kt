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
import namvunhatle.r15.onboarding.core.Zen.Color.Background.Accent
import namvunhatle.r15.onboarding.core.Zen.Color.Background.Support

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
    /** A ZEN text style ([ZenText]) in [face]. */
    constructor(face: Typeface, t: TextToken) : this(face, t.size, t.line, t.tracking)

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

fun Box.rect() = RectF(x, y, right, bottom)

private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/* ------------------------------------------------------------------ backgrounds ------------------------------------------------------------------ */

/**
 * P01 · Splash › BG › R15 (`15552:118420`). Frame fills: an image (smooth mesh, kept as a bitmap) under a black 0→100 %
 * ramp at 52 %; children: the grid (vector union), two blurred #51208D ellipses (baked, [A7Glow]), and a 40 % smoke
 * image (kept as a bitmap — it is a photo in Figma too).
 *
 * [box] is the whole visible screen: on a 20:9 phone the 360×800 frame, elsewhere the frame grown like a resized
 * Figma frame — image fill covers it, the ramp spans it, the smoke keeps to its top edge, and the grid and the glows
 * (larger than the frame already) just show more of themselves.
 */
class SplashBg(box: Box, private val base: Bitmap, private val glow: Bitmap, private val wisps: Bitmap, private val grid: Path) : FigmaArt(box) {
    private val full = box.rect()
    private val cover = (maxOf(box.w / Scene.W, box.h / Scene.H)).let { k ->
        RectF(box.cx - Scene.W * k / 2, box.cy - Scene.H * k / 2, box.cx + Scene.W * k / 2, box.cy + Scene.H * k / 2)
    }
    private val smoke = RectF(box.x, box.y, box.right, box.y + 139f * box.w / Scene.W)
    private val shade = Paint().apply {
        shader = LinearGradient(0f, box.y, 0f, box.bottom, A7Visual.SPLASH_RAMP and 0xFFFFFF, A7Visual.SPLASH_RAMP, Shader.TileMode.CLAMP)
        alpha = (A7Visual.SPLASH_RAMP_OPACITY * 255).toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = figmaLinear(
            floatArrayOf(-0.25712138f, 1.0826572f, 0.07550406f, -1.0826572f, -0.32707718f, 1.2325051f), GRID_W, GRID_H,
            A7Visual.SPLASH_GRID,
            floatArrayOf(0f, 0.33806920f, 0.69549137f, 1f),
        )
    }

    override fun paint(c: Canvas) {
        c.drawBitmap(base, null, cover, FILTER)
        c.drawRect(full, shade)
        c.withTranslation(-417.79883f, -358.01758f) { drawPath(grid, gridPaint) }
        c.drawBitmap(glow, null, full, FILTER)
        c.drawBitmap(wisps, null, smoke, FILTER)
    }

    companion object {
        const val GRID_W = 1123.4404f
        const val GRID_H = 1267.0859f
        /** Ellipse 182 / 183: #51208D, layer blur 121.1. */
        val GLOWS = listOf(
            A7Glow.Ellipse(199f, 427f, floatArrayOf(0.98277295f, 0.18481699f, 275.25586f, -0.18481699f, 0.98277295f, -111.93274f), A7Visual.SPLASH_GLOW, 1f, 121.1f),
            A7Glow.Ellipse(103f, -116f, 390f, 148f, A7Visual.SPLASH_GLOW, 1f, 121.1f),
        )
    }
}

/**
 * G01–G04 › BG › Spotlight BG: Support/Neutral/Deep + "Glow · main" (520×420, 70 %, blur 170) + "Glow · rim" (240×200,
 * 35 %, blur 110). The glow colours are tokens (G04's are not bound in Figma); size, opacity and blur are visual-only.
 */
class Spotlight(box: Box, private val baked: Bitmap) : FigmaArt(box) {
    private val full = box.rect()
    override fun paint(c: Canvas) = c.drawBitmap(baked, null, full, FILTER)

    companion object {
        /** Main glow top (rim sits 20 dp higher), main colour, rim colour — per screen. */
        val SPOTS = mapOf(
            "bg_G01" to Triple(430f, Accent.Gradient.DefaultRight, Accent.Gradient.DefaultLeft),
            "bg_G02a" to Triple(268f, Accent.Gradient.DefaultLeft, Accent.Gradient.DefaultRight),
            "bg_G02b" to Triple(268f, Support.Pink.Solid, Support.Violet.Solid),
            "bg_G02c" to Triple(268f, Support.Teal.Solid, Support.Blue.Solid),
            "bg_G03" to Triple(194f, Support.Violet.Solid, Support.Pink.Solid),
            "bg_G04" to Triple(72f, A7Visual.SPOT_G04_MAIN, A7Visual.SPOT_G04_RIM),
        )

        /** The spotlight over [box] (the visible screen, frame coordinates). */
        fun bake(id: String, box: Box): Bitmap {
            val (y, main, rim) = SPOTS.getValue(id)
            return A7Glow.bake(
                box, Support.Neutral.Deep,
                listOf(A7Glow.Ellipse(-80f, y, 520f, 420f, main, 0.7f, 170f), A7Glow.Ellipse(60f, y - 20f, 240f, 200f, rim, 0.35f, 110f)),
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

    // Display-1 size and ALL-CAPS-M tracking are tokens; Anton and its 92 % line height are not bound in Figma
    private val type = Type(anton, Zen.Typography.FontSize.Display1, Zen.Typography.FontSize.Display1 * 0.92f, Zen.Typography.LetterSpacing.ALLCAPSM)
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
    private val ink = t.paint(Zen.Color.Content.OnDarkOverlay.Strongest)
    private val r = Zen.CornerRadius.Large
    private val pad = Zen.Spacing.Padding.Medium

    override fun paint(c: Canvas) {
        c.withTranslation(cx, cy) {
            rotate(12f); translate(-84f, -60f)
            drawRoundRect(0f, 0f, 168f, 120f, r, r, fill)
            title.forEachIndexed { i, s -> text(Line(s, pad, 120f - pad - t.line * (title.size - i)), t, ink) }
        }
    }

    companion object {
        /** Variant colours of `A7 / Genre Tile`: gradient from the Solid to the Deep token (the angle is visual-only). */
        val PINK = Support.Pink.Solid to Support.Plum.Deep
        val ORANGE = Support.Orange.Solid to Support.Crimson.Deep
        val VIOLET = Support.Violet.Solid to Support.Indigo.Deep
        val BLUE = Support.Cyan.Solid to Support.Blue.Deep
    }
}

/**
 * A7 / Phone (`15553:122095`). Hero = 232×496, radius 32, #0E0E12, inside stroke 2 @ 10 % white, drop shadow
 * 0 24 48 @ 45 %; the screen is inset 8 with radius 24 and is an image fill in Figma, so it stays a bitmap.
 * Settled is the same component at [k] = 119.68 / 232.
 */
class Phone(box: Box, private val frame: RectF, private val screen: Bitmap) : FigmaArt(box) {
    private val k = frame.width() / 232f
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = A7Visual.PHONE_BODY }.dropShadow(24f * k, 48f * k, A7Visual.SHADOW_PHONE)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * k; color = Zen.Color.Border.Overlay.Subtle.Default }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = A7Visual.PHONE_HOLE }
    private val holeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * k; color = A7Visual.PHONE_HOLE_RIM }
    private val inner = RectF(frame.left + 8 * k, frame.top + 8 * k, frame.right - 8 * k, frame.bottom - 8 * k)
    // Hero radii (Giant, 2XLarge) scaled with the phone — the settled size is the hero at k, as in v1.3.3 (Figma's
    // Settled variant binds XLarge / Large instead)
    private val innerPath = Path().apply { addRoundRect(inner, Zen.CornerRadius._2XLarge * k, Zen.CornerRadius._2XLarge * k, Path.Direction.CW) }

    override fun paint(c: Canvas) {
        val r = Zen.CornerRadius.Giant * k
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
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Zen.Color.Background.WhiteSolid.Default }
    private val shadowed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke }.dropShadow(8f, 16f, A7Visual.SHADOW)
    private val ink = t.paint(Zen.Color.Content.OnWhiteOverlay.Strongest)

    override fun paint(c: Canvas) {
        c.withTranslation(cx, cy) {
            rotate(-rot)
            val hw = w / 2
            drawRoundRect(-hw - 4, -26f, hw + 4, 26f, 26f, 26f, shadowed) // outside stroke + its shadow
            drawRoundRect(-hw, -22f, hw, 22f, 22f, 22f, fill)
            text(Line(name, -hw + Zen.Spacing.Padding.Medium, -14f), t, ink)
        }
    }
}

/** G04 › Name ring › Center (`15554:123107`): headline stack + "Name slot" (dashed pill, inside stroke 2, dash 6/4). */
class NameCenter(box: Box, anton: Typeface, type: Type) : FigmaArt(box) {
    private val head = Headline(box, anton, listOf(
        Headline.Echo(Line("YOURS", 180.5f, 100f, center = true), WHITE),
        Headline.Echo(Line("IS NEXT.", 180.5f, 184f, center = true), Support.Pink.Solid, 1.5f, Align.OUTSIDE),
        Headline.Echo(Line("IS NEXT.", 180.5f, 178f, center = true), Accent.Gradient.DefaultLeft, 1.5f, Align.OUTSIDE),
        Headline.Echo(Line("IS NEXT.", 180.5f, 172f, center = true), WHITE_96),
    ))
    private val t = type
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Zen.Color.Border.Accent.Solid.Default; pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val ink = t.paint(Zen.Color.Content.OnDarkOverlay.Base)

    override fun paint(c: Canvas) {
        head.render(c)
        c.drawRoundRect(114f, 245f, 247f, 283f, 19f, 19f, dash)
        c.text(Line("Your name", 131f, 250f), t, ink)
    }

    companion object {
        /** Main headline lines (kept from v1.3.3) and "Headline · line 2" (the token). */
        val WHITE = A7Visual.HEADLINE_WHITE
        val WHITE_96 = Zen.Color.Content.OnDarkOverlay.Strongest
    }
}

/** Button/Main XLarge, Primary (CTA): pill 328×56 #BB4ABF, shadow 0 1 1 @ 4 %, label SemiBold 16/24 −0.4 white. */
class CtaButton(box: Box, type: Type, private val label: String) : FigmaArt(box) {
    private val t = type
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Zen.ButtonPrimary.Background.Default }.dropShadow(1f, 1f, Zen.Color.Shadow.Neutral.Light)
    private val ink = t.paint(Zen.ButtonPrimary.Content.Default)
    override fun paint(c: Canvas) {
        c.drawRoundRect(16f, 343f, 344f, 399f, 28f, 28f, fill) // Corner-Radius/Action/XLarge = pill
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
    private val white = Paint().apply { color = Zen.Color.Background.WhiteSolid.Default }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Support.Neutral.Subtle }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Zen.Color.Background.Warning.Solid.Default }
    private val head = semi12.paint(Zen.Color.Content.OnWhiteOverlay.Strongest)
    private val body = reg10.paint(Zen.Color.Content.OnWhiteOverlay.Base)
    private val ad = med10.paint(Zen.Color.Content.OnBrights)
    private val r = Zen.CornerRadius.Small

    override fun paint(c: Canvas) {
        c.drawRect(box.x, 692f, box.right, 752f, white) // full width of the screen
        c.drawRoundRect(12f, 704f, 48f, 740f, r, r, icon)
        c.text(Line("Advertiser headline", 60f, 706f), semi12, head)
        c.text(Line("One line from the ad network", 60f, 722f), reg10, body)
        c.drawRoundRect(322f, 714f, 348f, 730f, r, r, badge) // Rounded, on a 16 dp badge
        c.text(Line("Ad", 328f, 714f), med10, ad)
    }
}

/** Status bar (Material 3 kit): "9:30" Roboto 14/20 +0.25, Wi-Fi / signal / battery glyphs, punch-hole marker. */
class StatusBar(box: Box, private val time: Type, private val wifi: Drawable, private val signal: Drawable, private val battery: Drawable) : FigmaArt(box) {
    private val ink = time.paint(Project.White)
    private val cam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Zen.Color.Content.OnColors }

    override fun paint(c: Canvas) {
        // spans the screen: the time holds the left edge, the icons the right one
        val r = box.right - Scene.W
        c.text(Line("9:30", box.x + 16f, 10f), time, ink)
        icon(c, wifi, r + 298.66669f, 14.960938f, 14.6667f, 10.3733f)
        icon(c, signal, r + 313.33334f, 13.333984f, 13.3333f, 13.3333f)
        icon(c, battery, r + 331.75f, 12.916016f, 8.5f, 14.1667f)
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

/**
 * 03 · Interstitial (SDK) (`15560:138551`): the ad creative is the frame's image fill (FILL = cover, radius 24), with
 * the SDK chrome on top — "Ads progress" (two 4 px round-capped lines, 70 px played + the rest at 25 %) and the
 * "Skip ads" pill (white 17 %, Mona Sans Medium 12/16 +4 %, close-circle-fill). The creative is a bitmap in Figma too.
 *
 * [box] is the visible screen: the creative covers it and the chrome holds the top edge (the pill the right side,
 * the progress spans the width). Past the rounded corners is black — a real SDK activity's window.
 */
class Interstitial(box: Box, private val creative: Bitmap, type: Type, private val closeIcon: Path) : FigmaArt(box) {
    private val t = type
    private val black = Paint().apply { color = A7Visual.AD_WINDOW }
    private val frame = Path().apply { addRoundRect(box.rect(), Zen.CornerRadius._2XLarge, Zen.CornerRadius._2XLarge, Path.Direction.CW) }
    private val cover = (maxOf(box.w / creative.width, box.h / creative.height)).let { k ->
        RectF(box.cx - creative.width * k / 2, box.cy - creative.height * k / 2, box.cx + creative.width * k / 2, box.cy + creative.height * k / 2)
    }
    // as exported: the round caps sit inside each line's length, centred 6 dp down (not on the frame's 8 dp padding)
    private val top = box.y + 6f
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; color = A7Visual.AD_PROGRESS }
    private val rest = Paint(played).apply {
        val x0 = box.x + 16f + 74f; val x1 = box.right - 16f
        shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(A7Visual.AD_PROGRESS, A7Visual.AD_PROGRESS, A7Visual.AD_PROGRESS_REST, A7Visual.AD_PROGRESS_REST), floatArrayOf(0f, 0.25f, 0.25f, 1f), Shader.TileMode.CLAMP)
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AdKit.Background.BgOverlay }
    private val ink = t.paint(AdKit.Content.CtEmphasis)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AdKit.Content.CtMedium }

    override fun paint(c: Canvas) {
        c.drawRect(box.rect(), black)
        c.withClip(frame) { drawBitmap(creative, null, cover, FILTER) }
        c.drawLine(box.x + 16f + 2f, top, box.x + 16f + 70f - 2f, top, played)
        c.drawLine(box.x + 16f + 74f + 2f, top, box.right - 16f - 2f, top, rest)
        val px = box.right - 16f - 89f; val py = box.y + 16f
        c.drawRoundRect(px, py, px + 89f, py + 27f, 13.5f, 13.5f, pill)
        c.text(Line("Skip ads", px + 8f, py + 5.5f), t, ink)
        c.withTranslation(px + 67f + 4f / 3f, py + 5.5f + 4f / 3f) { drawPath(closeIcon, icon) }
    }

    companion object {
        /** close-circle-fill (13.33 × 13.33). */
        const val CLOSE = "M6.66667 13.3333 C2.98477 13.3333 0 10.3485 0 6.66667 C0 2.98477 2.98477 0 6.66667 0 C10.3485 0 13.3333 2.98477 13.3333 6.66667 C13.3333 10.3485 10.3485 13.3333 6.66667 13.3333 Z M6.66667 5.72387 L4.78105 3.83824 L3.83824 4.78105 L5.72387 6.66667 L3.83824 8.55227 L4.78105 9.49507 L6.66667 7.60947 L8.55227 9.49507 L9.49507 8.55227 L7.60947 6.66667 L9.49507 4.78105 L8.55227 3.83824 L6.66667 5.72387 Z"
    }
}
