package namvunhatle.r15.onboarding.sample

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import namvunhatle.r15.onboarding.A7OnboardingView
import namvunhatle.r15.onboarding.views.DesignFrame
import kotlin.math.ceil

/**
 * Demo host for the A7 onboarding library: one [A7OnboardingView] full screen + [MockAds], and the three screens the
 * onboarding leads to as static screenshots (Paywall → AI Ringtones · Home), with tap areas. A real app opens its own
 * screens from [A7OnboardingView.onExplore] / [A7OnboardingView.onBrowse].
 */
class MainActivity : Activity() {
    private lateinit var a7: A7OnboardingView
    private lateinit var dests: FrameLayout
    private lateinit var fab: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        val root = FrameLayout(this)
        a7 = A7OnboardingView(this)
        root.addView(a7, FrameLayout.LayoutParams(-1, -1))
        dests = FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(0xFF000000.toInt()) }
        root.addView(dests, FrameLayout.LayoutParams(-1, -1))
        fab = ImageButton(this).apply {
            setBackgroundResource(R.drawable.fab); setImageResource(R.drawable.ic_replay); contentDescription = "Replay"
            visibility = View.GONE
            setOnClickListener { replay() }
        }
        val d = resources.displayMetrics.density
        root.addView(fab, FrameLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt(), android.view.Gravity.BOTTOM or android.view.Gravity.END).apply { setMargins(0, 0, (16 * d).toInt(), (16 * d).toInt()) })
        setContentView(root)

        a7.ads = MockAds(this)
        a7.onExplore = { show(R.drawable.dest_paywall) } // AI branch: paywall first (user ruling 2026-09-23)
        a7.onBrowse = { show(R.drawable.dest_home) } // Home, no paywall
        a7.start()
        seekExtra()?.let(a7::seekFrozen)
    }

    /** A destination screenshot, in the 360 × 800 frame, sliding in from the right. */
    private fun show(res: Int) {
        val frame = DesignFrame(this, null)
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY; setImageBitmap(underStatusBar(res)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        frame.addView(img, FrameLayout.LayoutParams(px(360f), px(800f)))
        if (res == R.drawable.dest_paywall) {
            // close (300, 28, 52²) and subscribe (20, 638, 320 × 52) both lead on to the AI screen
            hot(frame, 300f, 28f, 52f, 52f, "Close paywall") { show(R.drawable.dest_ai) }
            hot(frame, 20f, 638f, 320f, 52f, "Subscribe") { show(R.drawable.dest_ai) }
        }
        dests.addView(frame, FrameLayout.LayoutParams(-1, -1))
        dests.visibility = View.VISIBLE
        fab.visibility = View.VISIBLE
        frame.post { frame.translationX = dests.width.toFloat(); frame.animate().translationX(0f).setDuration(400).start() }
    }

    private fun hot(parent: ViewGroup, x: Float, y: Float, w: Float, h: Float, label: String, onClick: () -> Unit) {
        parent.addView(View(this).apply { contentDescription = label; setOnClickListener { onClick() } },
            FrameLayout.LayoutParams(px(w), px(h)).apply { leftMargin = px(x); topMargin = px(y) })
    }

    /** The screenshots carry the Figma status bar in their top 40 dp: paint that band with the row below it, so only
     *  the real status bar shows. */
    private fun underStatusBar(res: Int): Bitmap {
        val b = BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false; inMutable = true })
        val band = ceil(b.height * 40f / 800f).toInt()
        val row = IntArray(b.width)
        b.getPixels(row, 0, b.width, 0, band, b.width, 1)
        for (y in 0 until band) b.setPixels(row, 0, b.width, 0, y, b.width, 1)
        return b
    }

    private fun replay() {
        dests.removeAllViews(); dests.visibility = View.GONE; fab.visibility = View.GONE
        a7.restart()
    }

    private fun px(v: Float) = (v * resources.displayMetrics.density).toInt()
}
