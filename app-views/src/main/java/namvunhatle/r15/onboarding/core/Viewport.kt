package namvunhatle.r15.onboarding.core

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import kotlin.math.min

/**
 * The screen around the 360×800 design frame, in design dp. The frame keeps its v1.3.3 scale (fit, never distorted);
 * on a phone that is not 20:9 the leftover goes to [mx] on each side (shorter screens, 16:9 = 45) or [my] above and
 * below (taller ones, 21:9 = 20). One of the two is always 0.
 *
 * The scene fills that margin instead of letterboxing it: backgrounds bleed, the genre wall gets more tiles, the
 * status bar / headlines / wave hold the top edge and the splash's progress block + banner hold the bottom edge.
 * Beyond [MAX] (tablets, landscape) the rest stays letterboxed — phones only.
 */
class Viewport(mx: Float, my: Float) {
    // sub-half-dp margins (20:9 panels a few pixels off) snap to the exact v1.3.3 layout
    val mx = if (mx < 0.5f) 0f else min(mx, MAX)
    val my = if (my < 0.5f) 0f else min(my, MAX)
    val isFrame get() = mx == 0f && my == 0f

    /** The visible area in frame coordinates. */
    val view get() = Box(-mx, -my, Scene.W + 2 * mx, Scene.H + 2 * my)

    companion object {
        const val MAX = 60f
        val FRAME = Viewport(0f, 0f)

        /** Viewport of a [w]×[h] px screen (any unit, only the ratio counts). */
        fun of(w: Float, h: Float): Viewport {
            val s = min(w / Scene.W, h / Scene.H)
            return Viewport((w / s - Scene.W) / 2, (h / s - Scene.H) / 2)
        }
    }
}

/** This window's viewport. The activities are full-screen, portrait-locked and draw behind the cutout. */
fun Activity.viewport(): Viewport {
    val (w, h) = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
    else DisplayMetrics().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    return Viewport.of(w.toFloat(), h.toFloat())
}
