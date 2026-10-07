package namvunhatle.r15.onboarding.sample

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The host's window for the onboarding: edge to edge under the REAL status bar (the scene keeps its top 40 dp free for
 * it), light icons on the dark scene. The navigation bar stays hidden — swipe from the edge to peek. Since 1.6.6 the
 * onboarding no longer paints a Figma status bar ("9:30", fake icons, camera dot).
 */
fun Activity.immersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.navigationBars())
        isAppearanceLightStatusBars = false
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

/** `adb shell am start -n <pkg>/.MainActivity --ef t 12.4` opens frozen at 12.4 s, like the web's `?t=12.4`. */
fun Activity.seekExtra(): Double? = intent?.getFloatExtra("t", Float.NaN)?.takeIf { !it.isNaN() }?.toDouble()
