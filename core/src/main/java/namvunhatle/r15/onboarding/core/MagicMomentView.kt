package namvunhatle.r15.onboarding.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * R15 Christmas magic moment: 3 chuông rơi + tuần lộc ánh sáng chạy qua, 5,0 giây, chạy 1 lần.
 * Vẽ bằng code, thay cho file Lottie r15_magic_moment.json (2,15 MB).
 *
 * Ảnh dùng (để trong res/drawable-nodpi, đổi sang WebP):
 *   xmas_magic_deer_0 … xmas_magic_deer_7, xmas_magic_bell, xmas_magic_sparkle, xmas_magic_dust
 * Glow, dây ruy băng, vòng sóng âm và 2 luồng vệt sáng vẽ bằng code.
 *
 * Cách dùng trên Home, khi (1) user mới xong onboarding vào Home, hoặc (2) vừa cài nhạc chuông xong và về Home:
 *     MagicMomentView.play(homeContentContainer)   // container nằm dưới bottom nav
 * View tự gỡ khi hết 300 frame. Không nhận chạm. Không chạy khi tắt animation hoặc bật tiết kiệm pin.
 *
 * Toạ độ nội bộ là "comp px" 1080 × 2400 (= 360 × 800 dp ở 3x), scale theo chiều rộng view.
 */
class MagicMomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), Choreographer.FrameCallback {

    var onFinished: (() -> Unit)? = null

    /** Remove itself from its parent when done. Compose hosts (AndroidView) set this to false and hide it instead. */
    var removeOnFinish = true

    private var startNanos = 0L
    private var frame = 0f
    private var running = false
    private var sprites: Sprites? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.rgb(255, 219, 128) }
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val goldGlow = glowPaint(255, 214, 107)
    private val warmGlow = glowPaint(255, 240, 176)
    private val bellRibbon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-8f, 0f, 8f, 0f, intArrayOf(0xFF8E1428.toInt(), 0xFFE8394E.toInt(), 0xFF8E1428.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    }
    private val bellRibbonHi = Paint().apply { color = Color.argb(170, 255, 154, 164) }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start() {
        if (!motionAllowed()) { finish(); return }
        if (sprites == null) sprites = Sprites.load(context)
        startNanos = 0L
        frame = 0f
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun finish() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        sprites = null
        if (removeOnFinish) (parent as? ViewGroup)?.removeView(this) else invalidate()
        onFinished?.invoke()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (startNanos == 0L) startNanos = frameTimeNanos
        frame = (frameTimeNanos - startNanos) / 1_000_000_000f * FPS
        if (frame >= END) { finish(); return }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (running) { running = false; Choreographer.getInstance().removeFrameCallback(this); sprites = null }
    }

    private fun motionAllowed(): Boolean {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val saver = context.getSystemService<PowerManager>()?.isPowerSaveMode == true
        return scale > 0f && !saver
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val sp = sprites ?: return
        if (!running || width == 0) return
        val t = frame.toDouble()
        canvas.save()
        val k = width / W
        canvas.scale(k, k)

        // 1. wide warm glow behind everything
        if (t >= DEER_IN && t < 248) {
            canvas.save(); canvas.translate(540f, 640f)
            val ws = key(t, d(DEER_IN, 246.0), d(420.0, 520.0), IO) / 100
            canvas.scale(ws.toFloat(), ws.toFloat())
            glow(canvas, goldGlow, key(t, d(DEER_IN, DEER_IN + 34, 200.0, 246.0), d(0.0, 55.0, 50.0, 0.0), LIN) / 100)
            canvas.restore()
        }

        // 2. bells: bell 2 at the back, bell 0 in front
        for (bi in BELLS.indices.reversed()) {
            val b = BELLS[bi]
            val bx = b[0]; val ly = b[1]; val sc = b[2]; val dl = b[3]
            val exit = 246.0 + bi * 10; val s0 = dl + 44; val sgn = if (bi == 1) -1.0 else 1.0
            val y = key(t, d(dl, dl + 24, dl + 31, dl + 38, dl + 44, dl + 50, exit, exit + 34),
                d(-340.0, ly + 40 * sc, ly - 24 * sc, ly + 12 * sc, ly - 6, ly, ly, -360.0),
                arrayOf(IN, OUT, IO, IO, IO, LIN, IN))
            canvas.save(); canvas.translate(bx.toFloat(), y.toFloat())
            if (t >= s0 - 4 && t < exit + 1) {
                canvas.save(); canvas.translate(0f, (120 * sc).toFloat())
                val gs = (key(t, d(s0 - 4, 140.0, exit), d(90 * sc, 150 * sc, 130 * sc), IO) / 100).toFloat()
                canvas.scale(gs, gs)
                glow(canvas, goldGlow, key(t, d(s0 - 4, s0 + 10, DEER_IN + 40, 200.0, exit), d(0.0, 60.0, 70.0, 60.0, 0.0), LIN) / 100)
                canvas.restore()
            }
            canvas.save(); canvas.translate(0f, (6 * sc).toFloat()); canvas.scale(sc.toFloat(), 1f)
            canvas.drawRect(-8f, -1500f, 8f, 0f, bellRibbon)
            canvas.drawRect(-3f, -1500f, 0f, 0f, bellRibbonHi)
            canvas.restore()
            val bt = d(s0, s0 + 8, s0 + 16, s0 + 24, s0 + 32, s0 + 40, s0 + 48, s0 + 56, 150.0 + bi * 6, 158.0 + bi * 6, 166.0 + bi * 6, 174.0 + bi * 6, 182.0 + bi * 6)
            val bv = d(0.0, -16 * sgn, 13 * sgn, -10 * sgn, 7 * sgn, -4 * sgn, 2 * sgn, 0.0, 0.0, 7.0, -5.0, 3.0, 0.0)
            sprite(canvas, sp.bell, 0.0, 0.0, 130f, 42f, sc, sc, key(t, bt, bv, IO), 1.0)
            if (t >= s0 && t < s0 + 35) {
                val rs = (key(t, d(s0, s0 + 34), d(16.0, 100 * sc), OUT) / 100).toFloat()
                canvas.save(); canvas.translate(0f, (150 * sc).toFloat()); canvas.scale(rs, rs)
                ring.alpha = a255(key(t, d(s0, s0 + 34), d(70.0, 0.0), LIN) / 100)
                canvas.drawCircle(0f, 0f, 280f, ring)
                canvas.restore()
            }
            canvas.restore()
        }

        // 3. sparkles around the bells
        for (wi in TWINKLES.indices.reversed()) {
            val w = TWINKLES[wi]
            if (t < w.s || t >= w.s + 22) continue
            val z = key(t, d(w.s, w.s + 8, w.s + 22), d(0.0, w.sz, 0.0), IO) / 100
            sprite(canvas, sp.sparkle, w.x, w.y, 60f, 60f, z, z, key(t, d(w.s, w.s + 22), d(0.0, 45.0), LIN),
                key(t, d(w.s, w.s + 8, w.s + 22), d(0.0, 100.0, 0.0), LIN) / 100)
        }

        // 4. appearance flash
        if (t >= DEER_IN - 10 && t < DEER_IN + 44) {
            canvas.save(); canvas.translate(FLASH[0].toFloat(), FLASH[1].toFloat())
            val fs = (key(t, d(DEER_IN - 10, DEER_IN + 44), d(30.0, 130.0), OUT) / 100).toFloat()
            canvas.scale(fs, fs)
            glow(canvas, warmGlow, key(t, d(DEER_IN - 10, DEER_IN + 2, DEER_IN + 44), d(0.0, 95.0, 0.0), LIN) / 100)
            canvas.restore()
        }
        if (t >= DEER_IN - 8 && t < DEER_IN + 26) {
            val ss = key(t, d(DEER_IN - 8, DEER_IN + 2, DEER_IN + 26), d(0.0, 150.0, 0.0), IO) / 100
            sprite(canvas, sp.sparkle, FLASH[0], FLASH[1], 60f, 60f, ss, ss, key(t, d(DEER_IN - 8, DEER_IN + 26), d(0.0, 60.0), LIN),
                key(t, d(DEER_IN - 8, DEER_IN, DEER_IN + 26), d(0.0, 100.0, 0.0), LIN) / 100)
        }

        // 5. two light streams (front hooves, hind hooves) under the deer, then 6. the deer
        if (t >= DEER_IN && t < DEER_OUT + 1) {
            val op = key(t, d(DEER_IN, DEER_IN + 10, DEER_OUT - 14, DEER_OUT), d(0.0, 100.0, 100.0, 0.0), LIN) / 100
            ribbon(canvas, true, t, 66.0, 3.0, -6.0, .5, 16, CREAM, AMBER, .75, op)
            ribbon(canvas, true, t, 54.0, 2.4, -22.0, 1.0, 14, CREAM, AMBER, .65, op)
            ribbon(canvas, true, t, 44.0, 1.8, -40.0, 1.0, 14, CREAM, AMBER, .55, op)
            ribbon(canvas, true, t, 60.0, 44.0, -20.0, 2.0, 16, AMBER, AMBER, .13, op)
            ribbon(canvas, false, t, 84.0, 3.6, 6.0, .5, 18, CREAM, AMBER, .8, op)
            ribbon(canvas, false, t, 70.0, 2.8, 24.0, 1.0, 16, CREAM, AMBER, .7, op)
            ribbon(canvas, false, t, 56.0, 2.2, 44.0, 1.0, 14, CREAM, AMBER, .6, op)
            ribbon(canvas, false, t, 76.0, 56.0, 22.0, 2.0, 16, AMBER, AMBER, .15, op)

            val da = key(t, d(DEER_IN, DEER_IN + 8, DEER_OUT - 14, DEER_OUT), d(0.0, 100.0, 100.0, 0.0), LIN) / 100
            val s = pathV(PS, t) / 100
            canvas.save()
            canvas.translate(pathV(PX, t).toFloat(), pathV(PY, t).toFloat())
            canvas.rotate(rot(t).toFloat())
            canvas.scale((s * squashX(t)).toFloat(), s.toFloat())
            canvas.translate(0f, gaitBob(t).toFloat())
            canvas.rotate(gaitPitch(t).toFloat())
            canvas.save(); canvas.translate(-20f, -20f); canvas.scale(.95f, .7f); glow(canvas, goldGlow, .38 * da); canvas.restore()
            paint.alpha = a255(da)
            canvas.drawBitmap(sp.deer[floor(t / STEP).toInt() % POSES], -220f, -180f, paint)
            canvas.restore()
        }

        // 7. stars and dust falling from the streams, on top
        for (qi in PARTS.indices.reversed()) {
            val q = PARTS[qi]
            if (t < q.s || t >= q.s + q.life) continue
            val u = OUT.ease((t - q.s) / q.life)
            val x = q.x + (q.x2 - q.x) * u; val y = q.y + (q.y2 - q.y) * u
            if (q.star) {
                val t5 = d(q.s, q.s + 5, q.s + q.life * .5, q.s + q.life * .65, q.s + q.life)
                val z = key(t, t5, d(0.0, q.sz, q.sz * .55, q.sz * .9, 0.0), IO) / 100
                sprite(canvas, sp.sparkle, x, y, 60f, 60f, z, z, key(t, d(q.s, q.s + q.life), d(0.0, q.spin), LIN),
                    key(t, d(q.s, q.s + 4, q.s + q.life * .5, q.s + q.life * .65, q.s + q.life), d(0.0, 100.0, 55.0, 95.0, 0.0), LIN) / 100)
            } else {
                val z = key(t, d(q.s, q.s + q.life), d(q.sz, q.sz * .4), LIN) / 100
                sprite(canvas, sp.dust, x, y, 20f, 20f, z, z, 0.0, key(t, d(q.s, q.s + 3, q.s + q.life), d(0.0, 90.0, 0.0), LIN) / 100)
            }
        }
        canvas.restore()
    }

    private fun glow(canvas: Canvas, p: Paint, alpha: Double) {
        if (alpha <= 0) return
        p.alpha = a255(alpha)
        canvas.drawCircle(0f, 0f, 180f, p)
    }

    private fun sprite(canvas: Canvas, bmp: Bitmap, x: Double, y: Double, ax: Float, ay: Float, sx: Double, sy: Double, deg: Double, alpha: Double) {
        if (alpha <= 0 || sx == 0.0) return
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        if (deg != 0.0) canvas.rotate(deg.toFloat())
        canvas.scale(sx.toFloat(), sy.toFloat())
        paint.alpha = a255(alpha)
        canvas.drawBitmap(bmp, -ax, -ay, paint)
        canvas.restore()
    }

    // reused buffers for the light streams
    private val spX = DoubleArray(19); private val spY = DoubleArray(19)
    private val tx = DoubleArray(19); private val ty = DoubleArray(19)
    private val px = DoubleArray(38); private val py = DoubleArray(38)

    private fun ribbon(canvas: Canvas, front: Boolean, t: Double, hist: Double, width: Double, offset: Double, wave: Double, n: Int,
                       head: IntArray, tail: IntArray, opH: Double, layerOp: Double) {
        if (layerOp <= 0) return
        for (i in 0..n) {
            val p = pairPt(max(DEER_IN, t - hist * i / n), front)
            spX[i] = p[0]; spY[i] = p[1]
        }
        for (i in 0..n) {
            val a = max(0, i - 1); val c = min(n, i + 1)
            var dx = spX[c] - spX[a]; var dy = spY[c] - spY[a]; var l = sqrt(dx * dx + dy * dy)
            if (l < 1e-3) { dx = -1.0; dy = 0.0; l = 1.0 }
            tx[i] = dx / l; ty[i] = dy / l
        }
        val s = pathV(PS, t) / 152
        val m = 2 * (n + 1)
        for (i in 0..n) {
            val u = i.toDouble() / n
            val w = width * s * (1 - u).pow(.85) * (if (u < .08) .55 + 5.6 * u else 1.0) + .4
            val off = offset * s * u.pow(.9) + wave * s * sin(u * 5.2 + t * .09) * u
            val nx = -ty[i]; val ny = tx[i]
            px[i] = spX[i] + nx * (off + w / 2); py[i] = spY[i] + ny * (off + w / 2)
            px[m - 1 - i] = spX[i] + nx * (off - w / 2); py[m - 1 - i] = spY[i] + ny * (off - w / 2)
        }
        path.rewind()
        path.moveTo(px[0].toFloat(), py[0].toFloat())
        for (i in 0 until m) {
            val i0 = (i - 1 + m) % m; val i2 = (i + 1) % m; val i3 = (i + 2) % m
            path.cubicTo(
                (px[i] + (px[i2] - px[i0]) / 6).toFloat(), (py[i] + (py[i2] - py[i0]) / 6).toFloat(),
                (px[i2] - (px[i3] - px[i]) / 6).toFloat(), (py[i2] - (py[i3] - py[i]) / 6).toFloat(),
                px[i2].toFloat(), py[i2].toFloat())
        }
        path.close()
        val h = pairPt(t, front)
        var tl = pairPt(max(DEER_IN, t - hist), front)
        if (abs(tl[0] - h[0]) + abs(tl[1] - h[1]) < 2) tl = doubleArrayOf(h[0] - 2, h[1])
        val mid = IntArray(3) { (head[it] + tail[it]) / 2 }
        fun mix(a: IntArray, b: IntArray, f: Double) = IntArray(3) { (a[it] + (b[it] - a[it]) * f).toInt() }
        fun col(c: IntArray, a: Double) = Color.argb(a255(a), c[0], c[1], c[2])
        val c3 = mix(head, mid, .3 / .55)
        ribbonPaint.shader = LinearGradient(h[0].toFloat(), h[1].toFloat(), tl[0].toFloat(), tl[1].toFloat(),
            intArrayOf(col(head, opH * .08), col(c3, opH), col(mid, opH * (1 - .25 / .7)), col(tail, 0.0)),
            floatArrayOf(0f, .3f, .55f, 1f), Shader.TileMode.CLAMP)
        ribbonPaint.alpha = a255(layerOp)
        canvas.drawPath(path, ribbonPaint)
    }

    private class Sprites(val deer: Array<Bitmap>, val bell: Bitmap, val sparkle: Bitmap, val dust: Bitmap) {
        companion object {
            fun load(ctx: Context): Sprites {
                val o = BitmapFactory.Options().apply { inScaled = false }
                fun bmp(id: Int): Bitmap = BitmapFactory.decodeResource(ctx.resources, id, o)
                val deer = intArrayOf(
                    R.drawable.xmas_magic_deer_0, R.drawable.xmas_magic_deer_1, R.drawable.xmas_magic_deer_2, R.drawable.xmas_magic_deer_3,
                    R.drawable.xmas_magic_deer_4, R.drawable.xmas_magic_deer_5, R.drawable.xmas_magic_deer_6, R.drawable.xmas_magic_deer_7,
                )
                return Sprites(Array(POSES) { bmp(deer[it]) }, bmp(R.drawable.xmas_magic_bell), bmp(R.drawable.xmas_magic_sparkle), bmp(R.drawable.xmas_magic_dust))
            }
        }
    }

    // ------------------------------------------------------------------ timeline maths (same as web/magic-moment.js)

    private class Ease(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val linear: Boolean = false) {
        fun ease(x: Double): Double {
            if (linear) return x.coerceIn(0.0, 1.0)
            if (x <= 0) return 0.0
            if (x >= 1) return 1.0
            var lo = 0.0; var hi = 1.0; var t = x
            repeat(22) { t = (lo + hi) / 2; if (s(x1, x2, t) < x) lo = t else hi = t }
            return s(y1, y2, t)
        }
        private fun s(a: Double, b: Double, t: Double): Double { val m = 1 - t; return 3 * m * m * t * a + 3 * m * t * t * b + t * t * t }
    }

    private class Part(val s: Double, val star: Boolean, val life: Double, val x: Double, val y: Double, val x2: Double, val y2: Double, val sz: Double, val spin: Double)
    private class Twinkle(val s: Double, val x: Double, val y: Double, val sz: Double)

    /** mulberry32, identical to the web preview so both show the same particles. */
    private class Rng(seed: Int) {
        private var a = seed
        fun next(): Double {
            a += 0x6D2B79F5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    companion object {
        private const val TAG = "xmas_magic_moment"
        private const val W = 1080f
        private const val FPS = 60f
        private const val END = 300f
        private const val DEER_IN = 54.0
        private const val DEER_OUT = 232.0
        private const val STEP = 3
        private const val POSES = 8

        private val LIN = Ease(0.0, 0.0, 1.0, 1.0, linear = true)
        private val IO = Ease(.42, 0.0, .58, 1.0)
        private val IN = Ease(.5, 0.0, .9, .8)
        private val OUT = Ease(.1, .3, .5, 1.0)

        private val PT = d(54.0, 92.0, 130.0, 160.0, 184.0, 204.0, 226.0)
        private val PX = d(150.0, 290.0, 450.0, 600.0, 760.0, 930.0, 1180.0)
        private val PY = d(380.0, 560.0, 770.0, 900.0, 950.0, 890.0, 790.0)
        private val PS = d(24.0, 56.0, 98.0, 132.0, 148.0, 152.0, 152.0)
        private val FRONT = arrayOf(d(58.0, 66.0), d(32.0, 22.0), d(4.0, -2.0), d(-24.0, -18.0), d(-8.0, -72.0), d(30.0, -48.0), d(62.0, -14.0), d(72.0, 40.0))
        private val HIND = arrayOf(d(-12.0, 8.0), d(-42.0, -30.0), d(-66.0, -58.0), d(-48.0, -14.0), d(22.0, -26.0), d(40.0, -8.0), d(26.0, 6.0), d(4.0, 10.0))
        private val BELLS = arrayOf(d(540.0, 420.0, .86, 0.0), d(300.0, 300.0, .62, 14.0), d(790.0, 350.0, .70, 28.0))
        private val AMBER = intArrayOf(255, 189, 71)   // (1, .74, .28)
        private val CREAM = intArrayOf(255, 242, 199)  // (1, .95, .78)

        /** Thêm lớp hiệu ứng vào [container] (nằm dưới bottom nav) và chạy 1 lần. */
        fun play(container: ViewGroup, onFinished: (() -> Unit)? = null): MagicMomentView? {
            if (container.findViewWithTag<MagicMomentView>(TAG) != null) return null
            val v = MagicMomentView(container.context)
            v.tag = TAG
            v.onFinished = onFinished
            container.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            v.start()
            return v
        }

        private fun d(vararg v: Double) = v
        private fun a255(a: Double) = (a.coerceIn(0.0, 1.0) * 255 + .5).toInt()

        private fun key(t: Double, tt: DoubleArray, v: DoubleArray, e: Ease): Double = keyed(t, tt, v, e, null)
        private fun key(t: Double, tt: DoubleArray, v: DoubleArray, e: Array<Ease>): Double = keyed(t, tt, v, null, e)
        private fun keyed(t: Double, tt: DoubleArray, v: DoubleArray, one: Ease?, per: Array<Ease>?): Double {
            val n = tt.size
            if (t <= tt[0]) return v[0]
            if (t >= tt[n - 1]) return v[n - 1]
            var i = 0
            while (t > tt[i + 1]) i++
            val e = one ?: per!![min(i, per.size - 1)]
            val u = e.ease((t - tt[i]) / (tt[i + 1] - tt[i]))
            return v[i] + (v[i + 1] - v[i]) * u
        }

        private fun cr(p0: Double, p1: Double, p2: Double, p3: Double, u: Double) =
            .5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)

        private fun pathV(v: DoubleArray, t: Double): Double {
            val n = PT.size
            if (t <= PT[0]) return v[0]
            if (t >= PT[n - 1]) return v[n - 1]
            var i = 0
            while (t > PT[i + 1]) i++
            val u = (t - PT[i]) / (PT[i + 1] - PT[i])
            return cr(v[max(0, i - 1)], v[i], v[i + 1], v[min(n - 1, i + 2)], u)
        }

        private fun squashX(t: Double) = .62 + .38 * min(1.0, max(0.0, (t - PT[0]) / 90))
        private fun rot(t: Double): Double {
            val dx = pathV(PX, t + 1) - pathV(PX, t - 1); val dy = pathV(PY, t + 1) - pathV(PY, t - 1)
            return atan2(dy, dx) * 180 / PI * .38 - 6
        }
        private fun poseBob(k: Int) = -9 * sin(2 * PI * k / POSES) - 3
        private fun posePitch(k: Int) = -5 * cos(2 * PI * k / POSES)
        private fun md(a: Int) = ((a % POSES) + POSES) % POSES
        private fun gaitBob(t: Double): Double { val k = t / STEP; val a = floor(k).toInt(); val f = k - a; return poseBob(md(a)) * (1 - f) + poseBob(md(a + 1)) * f }
        private fun gaitPitch(t: Double): Double { val k = t / STEP; val a = floor(k).toInt(); val f = k - a; return posePitch(md(a)) * (1 - f) + posePitch(md(a + 1)) * f }

        private fun deerPt(t: Double, lx: Double, ly: Double): DoubleArray {
            val pr = gaitPitch(t) * PI / 180
            var gx = lx * cos(pr) - ly * sin(pr); var gy = lx * sin(pr) + ly * cos(pr) + gaitBob(t)
            val s = pathV(PS, t) / 100
            gx *= s * squashX(t); gy *= s
            val r = rot(t) * PI / 180
            return doubleArrayOf(pathV(PX, t) + gx * cos(r) - gy * sin(r), pathV(PY, t) + gx * sin(r) + gy * cos(r))
        }

        private fun leg(u: Double, front: Boolean, off: Double): DoubleArray {
            val tb = if (front) FRONT else HIND
            val x = (((u + off + (if (front) 0.0 else .5)) % 1) + 1) % 1 * 8
            val a = floor(x).toInt() % 8; val b = (a + 1) % 8
            var f = x - floor(x); f = f * f * (3 - 2 * f)
            return doubleArrayOf(tb[a][0] + (tb[b][0] - tb[a][0]) * f, tb[a][1] + (tb[b][1] - tb[a][1]) * f)
        }
        private fun seg(x: Double, y: Double, deg: Double, len: Double): DoubleArray {
            val r = deg * PI / 180
            return doubleArrayOf(x + sin(r) * len, y + cos(r) * len)
        }
        private fun hoof(u: Double, front: Boolean): DoubleArray {
            val hx = if (front) 195.0 else 115.0; val hy = if (front) 141.0 else 137.0
            var sx = 0.0; var sy = 0.0
            for (off in if (front) d(0.0, .11) else d(0.0, .09)) {
                val a = leg(u, front, off); val k = seg(hx, hy, a[0], 32.0); val f = seg(k[0], k[1], a[1], 36.0)
                sx += f[0]; sy += f[1]
            }
            return doubleArrayOf(sx / 2, sy / 2)
        }
        private fun hoofMean(front: Boolean): DoubleArray {
            var sx = 0.0; var sy = 0.0
            for (i in 0 until 48) { val h = hoof(i / 48.0, front); sx += h[0]; sy += h[1] }
            return doubleArrayOf(sx / 48, sy / 48)
        }
        private val FM = hoofMean(true)
        private val HM = hoofMean(false)
        private fun pairPt(t: Double, front: Boolean): DoubleArray {
            val h = hoof(t / (STEP * POSES), front); val m = if (front) FM else HM
            return deerPt(t, m[0] + (h[0] - m[0]) * .25 - 160, m[1] + (h[1] - m[1]) * .25 - 130)
        }

        private val FLASH = deerPt(DEER_IN, -58.0, -6.0)
        private val PARTS: List<Part>
        private val TWINKLES: List<Twinkle>

        init {
            val r = Rng(1225)
            val parts = ArrayList<Part>()
            var s = DEER_IN + 6
            while (s < DEER_OUT + 6) {
                val back = 4 + r.next() * 50
                val front = r.next() < .5
                val p = pairPt(max(DEER_IN, s - back), front)
                val sc = pathV(PS, s) / 152
                val spread = (14 + back * 1.3) * sc
                val x = p[0] + (r.next() - .5) * spread * 1.6
                val y = p[1] + (r.next() - .5) * spread * 1.4
                val star = r.next() < .55
                val life = if (star) 30 + r.next() * 18 else 22 + r.next() * 16
                val fall = (18 + r.next() * 40) * (.5 + sc)
                val drift = (r.next() - .5) * 24
                val sz = if (star) (26 + r.next().pow(2.0) * 80) * (.5 + sc) else (18 + r.next() * 30) * (.5 + sc)
                val spin = if (star) (r.next() - .5) * 60 else 0.0
                parts += Part(s, star, life, x, y, x + drift, y + (if (star) fall else fall * .8), sz, spin)
                s += 1.6 + r.next() * 1.6
            }
            PARTS = parts
            TWINKLES = List(12) { k ->
                val bb = BELLS[k % 3]
                val st = DEER_IN + k * 12 + r.next() * 6; val a = r.next() * 6.28; val dd = 90 + r.next() * 120
                Twinkle(st, bb[0] + cos(a) * dd, bb[1] + 110 * bb[2] + sin(a) * dd * .8, 20 + r.next() * 28)
            }
        }

        private fun glowPaint(r: Int, g: Int, b: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 180f,
                intArrayOf(Color.argb(235, r, g, b), Color.argb(140, r, g, b), Color.argb(40, r, g, b), Color.argb(0, r, g, b)),
                floatArrayOf(0f, .3f, .65f, 1f), Shader.TileMode.CLAMP)
        }
    }
}
