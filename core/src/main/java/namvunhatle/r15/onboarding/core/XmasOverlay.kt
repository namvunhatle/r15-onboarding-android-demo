package namvunhatle.r15.onboarding.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Christmas 2026 layer of the onboarding ([XmasTheme]), one View for both UIs. It sits in the design frame above the
 * scene and below the status bar, the interstitial and the destinations, and draws in frame dp (360×800):
 *
 * - the intro garland (no bell; the splash background carries its own, with the bell) flush with the top edge;
 * - gentle snow (port of R1 `SnowOverlayView`: far / mid / near-blurred / crystal layers, wind + gusts), clipped out
 *   of the splash banner ad and the two native ads;
 * - the CTA decor on the G04 primary button ("Explore AI Ringtones"): right − 117, top − 28 dp, 133×50 dp, following
 *   the button's transform and opacity;
 * - on the splash: the dressed icon dropping on a red ribbon from the garland (1.05 s drop + bounce, 2.8 s damped
 *   swing), a burst of gold sparkles + snow when it lands (0.64 s) and three slow twinkles. The baked round icon
 *   underneath is hidden while the dressed one shows ([A7Player.FrameHook]); as the demo zooms the logo up (into the
 *   ad) the dressed art fades out between 1.2× and 1.7×, and the round icon takes over after the splash.
 *
 * Prefers-reduced-motion: no snow, no drop, no burst; the icon is shown in place.
 */
@SuppressLint("ViewConstructor")
class XmasOverlay(ctx: Context, private val player: A7Player) : View(ctx), A7Player.FrameHook {
    private val store = player.store
    private val scene = player.scene
    private val reduced = player.reduced

    private fun bmp(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false })
    private val garland = bmp(R.drawable.xmas_intro_garland)
    private val dressed = bmp(R.drawable.xmas_logo_dressed)
    private val ctaDecor = bmp(R.drawable.xmas_cta_decor)
    private val sparkle = bmp(R.drawable.xmas_magic_sparkle)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        player.hook = this
    }

    /* ---------------- splash icon state, kept in step with the timeline ---------------- */

    private var hidIcon = false
    private var savedIcon = 1f
    private var armed = true
    private var dropT0 = -1.0
    private var onSplash = false

    override fun before(store: Store) {
        if (hidIcon) { store["icon"][Prop.ALPHA] = savedIcon; hidIcon = false }
    }

    override fun after(store: Store) {
        val track = store["sp_track"]
        onSplash = track.shown && track.alpha > 0.05f
        val icon = store["icon"]
        savedIcon = icon.alpha
        val a = icon.alpha * store["logo"].alpha
        if (onSplash && a < 0.05f) armed = true
        else if (armed && onSplash && a >= 0.05f) { armed = false; dropT0 = player.time; parts.clear(); burstDone = false }
        // the dressed icon replaces the round one while it is at splash size
        if (onSplash && ratio() < 1.7f) { icon[Prop.ALPHA] = 0f; hidIcon = true }
    }

    override fun reset() { hidIcon = false; armed = true; dropT0 = -1.0; parts.clear(); burstDone = false }

    /** Logo scale relative to the splash size (the camera zoom is log-space: e^k). */
    private fun ratio() = store["logo"].sx * exp(store["zoom"][Prop.K]) * store["icon"].sx

    /* ---------------- drawing ---------------- */

    private var lastDraw = 0L

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastDraw == 0L) 0f else min(0.05f, (now - lastDraw) / 1000f)
        lastDraw = now
        val k = width / Scene.W
        c.save()
        c.scale(k, k)
        val cam = store["cam"]
        if (!reduced) snow(c, dt, cam)
        if (!onSplash) c.drawBitmap(garland, null, rect.apply { set(0f, 0f, Scene.W, 100f) }, paint)
        cta(c, cam)
        if (onSplash) splashIcon(c, dt)
        c.restore()
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun cta(c: Canvas, cam: El) {
        val el = store["g04_cta"]
        if (!el.shown || el.alpha < 0.02f) return
        val b = scene.pos.getValue("g04_cta")
        val ox = b.x + b.w * 0.5f; val oy = b.y + b.h * 0.5f // press scale is about the button centre
        val right = ox + (b.x + b.w - ox) * el.sx + el.x + cam.x
        val top = oy + (b.y - oy) * el.sy + el.y + cam.y
        paint.alpha = (255 * el.alpha * cam.alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        c.drawBitmap(ctaDecor, null, rect.apply { set(right - 117f, top - 28f, right + 16f, top + 22f) }, paint)
        paint.alpha = 255
    }

    /* ---------------- snow ---------------- */

    private class Flake(var x: Float, var y: Float, val r: Float, val v: Float, val o: Float, val par: Float, val blur: Boolean, val ph: Float, val crystal: Boolean)

    private val rnd = Random(2026)
    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    // far, mid, near (soft), crystals: count, radius, speed, opacity, parallax
    private val flakes = ArrayList<Flake>().apply {
        val layers = listOf(
            floatArrayOf(18f, .9f, 1.7f, 16f, 30f, .30f, .60f, .35f, 0f),
            floatArrayOf(10f, 1.9f, 3.2f, 38f, 68f, .70f, .95f, .7f, 0f),
            floatArrayOf(2f, 5.5f, 8.5f, 95f, 150f, .28f, .50f, 1.35f, 1f),
            floatArrayOf(3f, 6.5f, 9.5f, 42f, 62f, .9f, .9f, .85f, 2f),
        )
        for (l in layers) repeat(l[0].toInt()) {
            add(Flake(r(0f, Scene.W), r(-20f, Scene.H), r(l[1], l[2]), r(l[3], l[4]), r(l[5], l[6]), l[7], l[8] == 1f, r(0f, 6.28f), l[8] == 2f))
        }
    }
    private var snowT = 0f
    private var gustStart = -99f; private var gustAmp = 0f; private var gustNext = 6f
    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val crystalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val adClip = Path()

    private fun wind(t: Float): Float {
        val g = t - gustStart
        val gust = if (g > 0 && g < 3.8f) gustAmp * (if (g < 1.2f) sin(g / 1.2f * PI.toFloat() / 2) else 1 - (g - 1.2f) / 2.6f) else 0f
        return 14f * (.35f + .55f * sin(.13f * t + 1) + .3f * sin(.31f * t + 2.1f) + .15f * sin(.73f * t)) + gust
    }

    private fun snow(c: Canvas, dt: Float, cam: El) {
        snowT += dt
        if (snowT > gustNext) { gustStart = snowT; gustAmp = r(1.4f, 2.8f) * 14f * (if (rnd.nextBoolean()) -1 else 1); gustNext = snowT + r(6f, 14f) }
        val w = wind(snowT)
        // never over an ad: the splash banner and the two native slots
        adClip.rewind()
        val strip = store["sp_strip"]
        if (strip.shown && strip.alpha > 0.02f) scene.pos.getValue("sp_strip").let { b -> adClip.addRect(b.x - 6 + strip.x + cam.x, b.y - 6 + strip.y + cam.y, b.x + b.w + 6 + strip.x + cam.x, b.y + b.h + 6 + strip.y + cam.y, Path.Direction.CW) }
        for (id in listOf("nat1", "nat2")) {
            val el = store[id]
            if (el.shown && el.alpha > 0.02f) Scene.NATIVE.let { b -> adClip.addRect(b.x - 6 + el.x + cam.x, b.y - 6 + el.y + cam.y, b.x + b.w + 6 + el.x + cam.x, b.y + b.h + 6 + el.y + cam.y, Path.Direction.CW) }
        }
        c.save()
        if (!adClip.isEmpty) c.clipOutPathCompat(adClip)
        for (f in flakes) {
            f.x += (w * f.par + sin(snowT * .9f + f.ph) * 6f) * dt; f.y += f.v * dt
            if (f.y > Scene.H + 10) { f.y = -10f; f.x = r(0f, Scene.W) }
            if (f.x < -10) f.x = Scene.W + 10; if (f.x > Scene.W + 10) f.x = -10f
            if (f.crystal) {
                crystalPaint.alpha = (230 * f.o).toInt()
                c.save(); c.translate(f.x, f.y)
                repeat(6) {
                    c.rotate(60f)
                    c.drawLine(0f, 0f, 0f, -f.r, crystalPaint)
                    c.drawLine(0f, -f.r * .55f, f.r * .25f, -f.r * .75f, crystalPaint)
                    c.drawLine(0f, -f.r * .55f, -f.r * .25f, -f.r * .75f, crystalPaint)
                }
                c.restore()
            } else {
                // the near layer is soft: a wider, fainter disc instead of a per-frame blur
                softPaint.alpha = (255 * f.o * (if (f.blur) .6f else 1f)).toInt()
                c.drawCircle(f.x, f.y, if (f.blur) f.r * 1.35f else f.r, softPaint)
            }
        }
        c.restore()
    }

    private fun Canvas.clipOutPathCompat(p: Path) {
        if (android.os.Build.VERSION.SDK_INT >= 26) clipOutPath(p)
        else @Suppress("DEPRECATION") clipPath(p, android.graphics.Region.Op.DIFFERENCE)
    }

    /* ---------------- splash icon ---------------- */

    private class Part(val star: Boolean, var x: Float, var y: Float, var vx: Float, var vy: Float, val g: Float, val life: Float, val sz: Float, var rot: Float, val spin: Float) { var age = 0f }
    private val parts = ArrayList<Part>()
    private var burstDone = false
    private val ribbon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbonLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFD66B.toInt() }
    private val knot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = XmasTheme.PRIMARY }
    private val snowDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    /** Drop: 0 → 60 % overshoot → bounce, on cubic-bezier(.3, 0, .4, 1) over 1.05 s. [d] = start height above rest. */
    private fun dropY(t: Float, d: Float): Float {
        if (t >= 1.05f) return 0f
        val u = bezier(t / 1.05f)
        val keys = floatArrayOf(0f, .6f, .76f, .89f, 1f)
        val vals = floatArrayOf(-d, 4.4f, -2.5f, .8f, 0f)
        for (i in 0 until 4) if (u <= keys[i + 1]) return vals[i] + (vals[i + 1] - vals[i]) * (u - keys[i]) / (keys[i + 1] - keys[i])
        return 0f
    }

    /** Damped swing after the drop, 2.8 s, ease-out. */
    private fun swing(t: Float): Float {
        val s = t - 1.05f
        if (s <= 0f || s >= 2.8f) return 0f
        val u = 1 - (1 - s / 2.8f) * (1 - s / 2.8f)
        val keys = floatArrayOf(0f, .14f, .34f, .54f, .74f, 1f)
        val vals = floatArrayOf(0f, 5f, -3.6f, 2.2f, -1f, 0f)
        for (i in 0 until 5) if (u <= keys[i + 1]) return vals[i] + (vals[i + 1] - vals[i]) * (u - keys[i]) / (keys[i + 1] - keys[i])
        return 0f
    }

    /** CSS cubic-bezier(.3, 0, .4, 1): y for x = [x] (Newton on x(s)). */
    private fun bezier(x: Float): Float {
        val x1 = .3f; val x2 = .4f; val y1 = 0f; val y2 = 1f
        var s = x
        repeat(6) {
            val xs = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s - x
            val d = 3 * (1 - s) * (1 - s) * x1 + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (1 - x2)
            if (d != 0f) s = (s - xs / d).coerceIn(0f, 1f)
        }
        return 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s
    }

    private fun splashIcon(c: Canvas, dt: Float) {
        val logo = store["logo"]; val icon = store["icon"]; val cam = store["cam"]
        val ratio = ratio()
        val fade = (1f - (ratio - 1.2f) / 0.5f).coerceIn(0f, 1f)
        val a = (savedIcon * logo.alpha).coerceIn(0f, 1f) * fade
        val t = if (dropT0 < 0 || reduced) 9f else (player.time - dropT0).toFloat()
        val cx = Scene.LX + logo.x + icon.x + cam.x
        val cy = Scene.LY + logo.y + icon.y + cam.y
        val art = 96f * ratio * 0.86f // the dressed tile sits 7 % inside the 96 dp record
        val dy = if (reduced) 0f else dropY(t, cy + art)
        val top = cy - art / 2 + dy
        if (a > 0.01f && ratio < 1.3f) {
            // ribbon from the garland's light string to the icon
            val y0 = XmasTheme.GARLAND_WIRE_Y
            if (top > y0) {
                val wR = 7f
                ribbon.shader = LinearGradient(cx - wR / 2, 0f, cx + wR / 2, 0f, intArrayOf(0xFF8E1428.toInt(), 0xFFE8394E.toInt(), 0xFF8E1428.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
                ribbon.alpha = (255 * min(1f, a * 1.2f)).toInt()
                ribbonLine.alpha = (217 * min(1f, a * 1.2f)).toInt()
                knot.alpha = ribbon.alpha
                c.drawRect(cx - wR / 2, y0, cx + wR / 2, top + art * 0.06f, ribbon)
                c.drawRect(cx - .6f, y0, cx + .6f, top + art * 0.06f, ribbonLine)
                c.drawOval(cx - 6f, top + art * .06f - 4f, cx + 6f, top + art * .06f + 4f, knot)
            }
        }
        if (a > 0.01f) {
            c.save()
            c.rotate(swing(t), cx, top) // CSS rotate(+) is clockwise, as is Canvas.rotate
            paint.alpha = (255 * a).toInt()
            c.drawBitmap(dressed, null, rect.apply { set(cx - art / 2, top, cx + art / 2, top + art) }, paint)
            paint.alpha = 255
            c.restore()
        }
        if (reduced || ratio >= 1.3f) return
        if (!burstDone && t > 0.64f) { burst(cx, cy - art * .05f); burstDone = true }
        if (t > 1.4f && a > 0.01f) for (i in 0 until 3) {
            val ang = t * .9f + i * 2.09f; val rr = 96f * ratio * .72f; val tw = .5f + .5f * sin(t * 3 + i * 1.7f)
            val sx = cx + cos(ang) * rr; val sy = cy + sin(ang) * rr * .8f; val z = 10f + 8f * tw
            paint.alpha = (255 * (.35f + .65f * tw) * a).toInt()
            c.drawBitmap(sparkle, null, rect.apply { set(sx - z, sy - z, sx + z, sy + z) }, paint)
        }
        paint.alpha = 255
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age > p.life) { it.remove(); continue }
            p.vy += p.g * dt; p.x += p.vx * dt; p.y += p.vy * dt
            val f = 1 - p.age / p.life
            if (p.star) {
                p.rot += p.spin * dt
                val z = p.sz * (.4f + .6f * f)
                c.save(); c.translate(p.x, p.y); c.rotate(p.rot * 57.3f)
                paint.alpha = (255 * f).toInt()
                c.drawBitmap(sparkle, null, rect.apply { set(-z, -z, z, z) }, paint)
                c.restore()
            } else {
                snowDot.alpha = (242 * f).toInt()
                c.drawCircle(p.x, p.y, p.sz, snowDot)
            }
        }
        paint.alpha = 255
    }

    private fun burst(cx: Float, cy: Float) {
        repeat(18) {
            val ang = r(0f, 6.28f); val v = r(70f, 190f)
            parts += Part(true, cx, cy, cos(ang) * v, sin(ang) * v - 40f, 120f, r(.7f, 1.15f), r(10f, 22f), r(0f, 6.28f), r(-4f, 4f))
        }
        repeat(22) {
            parts += Part(false, cx + r(-40f, 40f), cy - r(30f, 46f), r(-40f, 40f), r(-80f, -20f), 220f, r(.8f, 1.3f), r(1.4f, 3.2f), 0f, 0f)
        }
    }
}
