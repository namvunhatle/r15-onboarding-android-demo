package namvunhatle.r15.onboarding.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** GSAP 3 eases, same curves as the web prototype (power1 = quad, power2 = cubic, power3 = quart). */
fun interface Ease {
    fun at(p: Float): Float

    companion object {
        val none = Ease { it }
        val power1In = powIn(2); val power1Out = powOut(2); val power1InOut = powInOut(2)
        val power2In = powIn(3); val power2Out = powOut(3); val power2InOut = powInOut(3)
        val power3In = powIn(4); val power3Out = powOut(4); val power3InOut = powInOut(4)
        val sineIn = Ease { 1f - cos(it * PI.toFloat() / 2f) }
        val sineInOut = Ease { -(cos(PI.toFloat() * it) - 1f) / 2f }

        /** GSAP's default ease when a tween names none. */
        val default = power1Out

        fun backOut(s: Float = 1.70158f) = Ease { p0 -> val p = p0 - 1f; p * p * ((s + 1f) * p + s) + 1f }

        private fun powIn(n: Int) = Ease { it.pow(n) }
        private fun powOut(n: Int) = Ease { 1f - (1f - it).pow(n) }
        private fun powInOut(n: Int) = Ease { if (it < 0.5f) (2f * it).pow(n) / 2f else 1f - (2f * (1f - it)).pow(n) / 2f }
    }
}
