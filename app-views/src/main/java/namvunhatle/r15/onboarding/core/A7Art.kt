package namvunhatle.r15.onboarding.core

import android.content.Context
import namvunhatle.r15.onboarding.views.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pre-rendered pieces the web built with CSS blur / box-shadow. Animating a blur radius or a shadow per frame
 * costs a re-render every frame; here they are baked once at start-up and only their opacity/transform animates
 * (same rule as the web prototype: "crossfade to a pre-blurred copy, never animate filter").
 *
 * Every [Baked] is square, centred on its element's centre, [dp] wide in the design frame. All are baked on the start-up
 * pool ([Later]) in the order the splash needs them: record + glow on the first frame, the blurred twin at the zoom
 * (4.4 s), dot and ghosts after the ad.
 */
class A7Art(ctx: Context) {
    class Baked(val bitmap: Bitmap, val dp: Float)

    private val res = ctx.resources
    private val src = Later.of { BitmapFactory.decodeResource(res, R.drawable.logo_src, BitmapFactory.Options().apply { inScaled = false }) }

    /** `.logo-icon` box-shadow `0 0 34px rgba(255,255,255,.2)` (122 px at the ×3.6 build size). */
    val iconGlow = Later.of { glow(96f / 2, 34f / 2, A7Visual.ICON_GLOW) }
    /** Splash record, circle-cropped, 96 dp. */
    val icon = Later.of { Baked(circle(src.value, 96f, 0f), 96f) }
    /** `.logo-icon-b` — the record under `filter: blur(28px)` at ×3.6 = 7.8 dp at rest. */
    val iconBlur = Later.of { circle(src.value, 96f, pad = 3 * 7.8f).let { Baked(blur(it, 7.8f * PX), 96f + 6 * 7.8f) } }
    /** `.dot` — white 16 dp + `0 0 18px 6px accent, 0 0 4px 1px #fff`. */
    val dot = Later.of { layered(16f, listOf(Triple(8f + 6f, 9f, Scene.ACCENT), Triple(8f + 1f, 2f, A7Visual.DOT)), A7Visual.DOT) }
    /** `.ghost` — #f3d6ff 16 dp + `0 0 14px 4px accent`. */
    val ghost = Later.of { layered(16f, listOf(Triple(8f + 4f, 7f, Scene.ACCENT)), A7Visual.GHOST) }

    private fun circle(src: Bitmap, dDp: Float, pad: Float): Bitmap {
        val size = ((dDp + 2 * pad) * PX).roundToInt()
        val out = createBitmap(size, size)
        val d = dDp * PX
        val m = Matrix().apply { setScale(d / src.width, d / src.height); postTranslate(pad * PX, pad * PX) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        Canvas(out).drawCircle(size / 2f, size / 2f, d / 2f, p)
        return out
    }

    /** A blurred disc (box-shadow of a round element). r = disc radius incl. spread, sigma = blur / 2, in dp. */
    private fun glow(rDp: Float, sigmaDp: Float, color: Int): Baked {
        val half = rDp + 3 * sigmaDp
        return Baked(blur(disc(half, rDp, color), sigmaDp * PX, color), half * 2)
    }

    private fun disc(halfDp: Float, rDp: Float, color: Int): Bitmap {
        val size = (halfDp * 2 * PX).roundToInt()
        val b = createBitmap(size, size)
        Canvas(b).drawCircle(size / 2f, size / 2f, rDp * PX, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return b
    }

    /** Shadows listed like CSS (first = on top), painted under a [dDp] disc of [fill]. */
    private fun layered(dDp: Float, shadows: List<Triple<Float, Float, Int>>, fill: Int): Baked {
        val half = shadows.maxOf { it.first + 3 * it.second }
        val size = (half * 2 * PX).roundToInt()
        val out = createBitmap(size, size)
        val c = Canvas(out)
        for ((r, sigma, color) in shadows.reversed()) {
            val g = blur(disc(half, r, color), sigma * PX, color)
            c.drawBitmap(g, 0f, 0f, null)
        }
        c.drawCircle(size / 2f, size / 2f, dDp / 2 * PX, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        return Baked(out, half * 2)
    }

    companion object {
        /** Bake resolution: px per design dp (≈ xxhdpi after the ×1.1–1.2 fit-to-screen scale). */
        const val PX = 3f

        /**
         * Gaussian blur ≈ 3 box blurs (premultiplied ARGB, so edges do not darken). A one-colour [src] (the disc of a
         * shadow) passes its [color]: only its alpha is blurred, a quarter of the work, and the colour is put back.
         */
        fun blur(src: Bitmap, sigma: Float, color: Int? = null): Bitmap {
            val w = src.width; val h = src.height
            val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
            val ch = Array(if (color == null) 4 else 1) { FloatArray(w * h) }
            for (i in px.indices) {
                val c = px[i]; val a = (c ushr 24) / 255f
                ch[0][i] = a
                if (color == null) { ch[1][i] = ((c shr 16) and 255) / 255f * a; ch[2][i] = ((c shr 8) and 255) / 255f * a; ch[3][i] = (c and 255) / 255f * a }
            }
            val boxes = boxesForGauss(sigma)
            val tmp = FloatArray(w * h)
            for (c in ch) for (r in boxes) { boxH(c, tmp, w, h, r); boxV(tmp, c, w, h, r) }
            val rgb = (color ?: 0) and 0xFFFFFF
            for (i in px.indices) {
                val a = ch[0][i].coerceIn(0f, 1f)
                fun u(v: Float) = if (a <= 0f) 0 else (v / a * 255f).roundToInt().coerceIn(0, 255)
                px[i] = ((a * 255f).roundToInt() shl 24) or (if (color != null) rgb else (u(ch[1][i]) shl 16) or (u(ch[2][i]) shl 8) or u(ch[3][i]))
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }

        private fun boxesForGauss(sigma: Float, n: Int = 3): IntArray {
            val wIdeal = sqrt(12.0 * sigma * sigma / n + 1)
            var wl = wIdeal.toInt(); if (wl % 2 == 0) wl--
            val wu = wl + 2
            val mIdeal = (12.0 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4 * wl - 4)
            val m = Math.round(mIdeal).toInt()
            return IntArray(n) { max(0, (if (it < m) wl else wu) / 2) }
        }

        private fun boxH(s: FloatArray, d: FloatArray, w: Int, h: Int, r: Int) {
            val k = 1f / (2 * r + 1)
            for (y in 0 until h) {
                val o = y * w
                var acc = 0f
                for (x in -r..r) acc += s[o + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    d[o + x] = acc * k
                    acc += s[o + (x + r + 1).coerceAtMost(w - 1)] - s[o + (x - r).coerceAtLeast(0)]
                }
            }
        }

        private fun boxV(s: FloatArray, d: FloatArray, w: Int, h: Int, r: Int) {
            val k = 1f / (2 * r + 1)
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += s[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    d[y * w + x] = acc * k
                    acc += s[(y + r + 1).coerceAtMost(h - 1) * w + x] - s[(y - r).coerceAtLeast(0) * w + x]
                }
            }
        }
    }
}
