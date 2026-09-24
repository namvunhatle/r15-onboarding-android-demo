package namvunhatle.r15.onboarding.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Figma `LAYER_BLUR` on ellipses (the spotlight and splash glows), rendered once at start-up.
 *
 * A Gaussian this wide (σ ≈ 38–77 dp) costs a full-screen blur pass per frame if it runs live, and the glows only ever
 * fade or scale — so they are computed from the Figma geometry into a small bitmap and drawn with filtering. The
 * content is smooth, so half a pixel per dp is plenty.
 *
 * Fit against the Figma export of the same nodes (mean error 0.8 / 255 over the four spotlight screens): σ = 0.42 ×
 * Figma radius, applied to the *whole* ellipse with transparency around it, then cut by the frame — so a glow that
 * runs off the screen edge dims there, as in Figma.
 */
object A7Glow {
    const val SIGMA_PER_RADIUS = 0.42f

    /** An ellipse [w]×[h] placed by a Figma `relativeTransform` ([m] = m00, m01, m02, m10, m11, m12). */
    class Ellipse(val w: Float, val h: Float, val m: FloatArray, val color: Int, val opacity: Float, val blur: Float) {
        constructor(x: Float, y: Float, w: Float, h: Float, color: Int, opacity: Float, blur: Float) :
            this(w, h, floatArrayOf(1f, 0f, x, 0f, 1f, y), color, opacity, blur)
    }

    /** A [w]×[h] dp frame: [base] fill (or transparent) with the blurred ellipses on top, in order. */
    fun bake(w: Float, h: Float, base: Int?, ellipses: List<Ellipse>, pxPerDp: Float = 0.5f): Bitmap {
        val bw = ceil(w * pxPerDp).toInt(); val bh = ceil(h * pxPerDp).toInt(); val n = bw * bh
        // premultiplied, 0..1
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n); val a = FloatArray(n)
        if (base != null) {
            r.fill(ch(base, 16)); g.fill(ch(base, 8)); b.fill(ch(base, 0)); a.fill(1f)
        }
        for (e in ellipses) {
            val sigma = e.blur * SIGMA_PER_RADIUS * pxPerDp
            val pad = ceil(3 * sigma).toInt()
            val pw = bw + 2 * pad; val ph = bh + 2 * pad
            val mask = FloatArray(pw * ph)
            coverage(e, pw, ph, pad, pxPerDp, mask)
            gaussian(mask, FloatArray(pw * ph), pw, ph, sigma)
            val cr = ch(e.color, 16); val cg = ch(e.color, 8); val cb = ch(e.color, 0)
            for (i in 0 until n) {
                val k = mask[(i / bw + pad) * pw + i % bw + pad] * e.opacity
                r[i] = r[i] * (1 - k) + cr * k; g[i] = g[i] * (1 - k) + cg * k; b[i] = b[i] * (1 - k) + cb * k
                a[i] = a[i] * (1 - k) + k
            }
        }
        val px = IntArray(n) { i ->
            val al = a[i]
            if (al <= 0f) 0 else {
                val inv = 1f / al
                (byte(al) shl 24) or (byte(r[i] * inv) shl 16) or (byte(g[i] * inv) shl 8) or byte(b[i] * inv)
            }
        }
        return Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
    }

    private fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF) / 255f
    private fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255f).roundToInt()

    /** Antialiased ellipse coverage on the frame grown by [pad] px each side (enough for the blur's reach). */
    private fun coverage(e: Ellipse, bw: Int, bh: Int, pad: Int, s: Float, out: FloatArray) {
        val bmp = createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        c.translate(pad.toFloat(), pad.toFloat())
        c.scale(s, s)
        c.concat(Matrix().apply { setValues(floatArrayOf(e.m[0], e.m[1], e.m[2], e.m[3], e.m[4], e.m[5], 0f, 0f, 1f)) })
        c.drawOval(0f, 0f, e.w, e.h, Paint(Paint.ANTI_ALIAS_FLAG))
        val buf = ByteBuffer.allocate(bmp.rowBytes * bh)
        bmp.copyPixelsToBuffer(buf)
        val bytes = buf.array(); val stride = bmp.rowBytes
        for (y in 0 until bh) for (x in 0 until bw) out[y * bw + x] = (bytes[y * stride + x].toInt() and 0xFF) / 255f
        bmp.recycle()
    }

    /** Gaussian as three box blurs per axis (Kovesi), transparent beyond the edges. In place; [tmp] is scratch. */
    private fun gaussian(v: FloatArray, tmp: FloatArray, w: Int, h: Int, sigma: Float) {
        for (size in boxes(sigma)) {
            val rad = (size - 1) / 2
            for (y in 0 until h) box(v, tmp, y * w, 1, w, rad)
            for (x in 0 until w) box(tmp, v, x, w, h, rad)
        }
    }

    private fun boxes(sigma: Float, n: Int = 3): IntArray {
        val ideal = sqrt(12.0 * sigma * sigma / n + 1)
        var wl = floor(ideal).toInt(); if (wl % 2 == 0) wl--
        val wu = wl + 2
        val m = ((12.0 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4 * wl - 4)).roundToInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun box(src: FloatArray, dst: FloatArray, off: Int, step: Int, len: Int, rad: Int) {
        fun at(i: Int) = if (i < 0 || i >= len) 0f else src[off + i * step]
        val inv = 1f / (2 * rad + 1)
        var acc = 0f
        for (j in -rad..rad) acc += at(j)
        for (i in 0 until len) {
            dst[off + i * step] = acc * inv
            acc += at(i + rad + 1) - at(i - rad)
        }
    }
}
