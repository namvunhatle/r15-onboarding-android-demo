package namvunhatle.r15.onboarding.core

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The design frame carries its own status bar (and every destination screenshot has one baked in),
 * so the prototype hides the system bars — swipe from the edge to peek. A shipping build would keep the
 * real status bar and drop the painted one.
 */
fun Activity.immersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

/** `adb shell am start -n <pkg>/.MainActivity --ef t 12.4` opens frozen at 12.4 s, like the web's `?t=12.4`. */
fun Activity.seekExtra(): Double? = intent?.getFloatExtra("t", Float.NaN)?.takeIf { !it.isNaN() }?.toDouble()
