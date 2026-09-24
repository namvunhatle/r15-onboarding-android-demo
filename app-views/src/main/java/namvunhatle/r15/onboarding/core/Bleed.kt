package namvunhatle.r15.onboarding.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The screen margins ([Viewport]) around a full-screen screenshot — the interstitial and the destinations, other
 * products' screens that cannot lay themselves out again. Each margin continues the screenshot's own edge: its outer
 * [DEPTH] px averaged into [N] steps along the edge, smoothed, then stretched across the margin. Drawn with bounds =
 * the visible screen; the screenshot itself sits on top at the frame.
 */
class Bleed(src: Bitmap, private val vp: Viewport) : Drawable() {
    private val a: Bitmap? // left or top
    private val b: Bitmap? // right or bottom
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        a = when { vp.mx > 0f -> edge(src, 0, true); vp.my > 0f -> edge(src, 0, false); else -> null }
        b = when { vp.mx > 0f -> edge(src, src.width - DEPTH, true); vp.my > 0f -> edge(src, src.height - DEPTH, false); else -> null }
    }

    override fun draw(canvas: Canvas) {
        if (a == null || b == null) return
        val r = bounds
        val s = r.width() / vp.view.w
        val w = r.width().toFloat(); val h = r.height().toFloat()
        if (vp.mx > 0f) {
            val m = vp.mx * s
            canvas.drawBitmap(a, null, RectF(r.left.toFloat(), r.top.toFloat(), r.left + m, r.top + h), paint)
            canvas.drawBitmap(b, null, RectF(r.left + w - m, r.top.toFloat(), r.left + w, r.top + h), paint)
        } else {
            val m = vp.my * s
            canvas.drawBitmap(a, null, RectF(r.left.toFloat(), r.top.toFloat(), r.left + w, r.top + m), paint)
            canvas.drawBitmap(b, null, RectF(r.left.toFloat(), r.top + h - m, r.left + w, r.top + h), paint)
        }
    }

    /** The [DEPTH]-px strip at [at] (a column when [vertical], else a row), as a 1×[N] (or [N]×1) bitmap. */
    private fun edge(src: Bitmap, at: Int, vertical: Boolean): Bitmap {
        val len = if (vertical) src.height else src.width
        val px = IntArray(DEPTH * len)
        if (vertical) src.getPixels(px, 0, DEPTH, at, 0, DEPTH, len) else src.getPixels(px, 0, len, 0, at, len, DEPTH)
        val sum = Array(N) { FloatArray(3) }; val cnt = IntArray(N)
        for (i in px.indices) {
            val along = if (vertical) i / DEPTH else i % len
            val k = along * N / len
            val c = px[i]
            sum[k][0] += (c shr 16 and 0xFF).toFloat(); sum[k][1] += (c shr 8 and 0xFF).toFloat(); sum[k][2] += (c and 0xFF).toFloat()
            cnt[k]++
        }
        val avg = Array(N) { k -> FloatArray(3) { sum[k][it] / cnt[k] } }
        val out = IntArray(N) { k ->
            // 5-tap box over 12 steps: a busy edge (the ad) becomes a soft ramp, a flat one (the app screens) stays flat
            val rgb = FloatArray(3)
            var n = 0
            for (j in (k - 2).coerceAtLeast(0)..(k + 2).coerceAtMost(N - 1)) { for (ch in 0..2) rgb[ch] += avg[j][ch]; n++ }
            (0xFF shl 24) or ((rgb[0] / n).toInt() shl 16) or ((rgb[1] / n).toInt() shl 8) or (rgb[2] / n).toInt()
        }
        return if (vertical) Bitmap.createBitmap(out, 1, N, Bitmap.Config.ARGB_8888) else Bitmap.createBitmap(out, N, 1, Bitmap.Config.ARGB_8888)
    }

    // Opacity is animated on the element's layer, never on the drawable.
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    private companion object {
        const val DEPTH = 8
        const val N = 12
    }
}
