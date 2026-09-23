package namvunhatle.r15.onboarding.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** CSS helpers so both UIs draw the web's gradients and shadows with the same numbers. */
object Css {
    /** `linear-gradient(<angle>deg, …)` over a w×h box → gradient line start/end points (x0, y0, x1, y1). */
    fun linear(angleDeg: Float, w: Float, h: Float): FloatArray {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
        val len = abs(w * dx) + abs(h * dy)
        val cx = w / 2; val cy = h / 2
        return floatArrayOf(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2)
    }

    /** CSS shadow blur (px) → android.graphics.Paint.setShadowLayer radius (Skia: sigma = 0.57735·r + 0.5; CSS: sigma = blur / 2). */
    fun shadowRadius(blur: Float) = ((blur / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
}
