package namvunhatle.r15.onboarding.sample

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import namvunhatle.r15.onboarding.A7Ads
import namvunhatle.r15.onboarding.core.A7Visual
import namvunhatle.r15.onboarding.core.AdKit
import namvunhatle.r15.onboarding.core.Box
import namvunhatle.r15.onboarding.core.Type
import namvunhatle.r15.onboarding.core.ZenText
import namvunhatle.r15.onboarding.views.CssBox

/**
 * The demo's ads: no SDK, drawn mocks with the timings of the web prototype. A real app passes its own [A7Ads]
 * (see module a7onboarding-admob).
 *
 * - interstitial: a full-screen dialog (like an SDK activity: no status bar) with the Figma ad frame; waits for "Skip ads".
 * - native #1: in at once (preloaded); native #2: arrives 0.9 s after its slot comes in, so the loading state shows.
 * - banner: the Figma ad slot under the splash's progress block.
 */
class MockAds(private val activity: Activity) : A7Ads {
    private val main = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null

    override fun banner(activity: Activity, slot: ViewGroup) {
        val f = { id: Int -> ResourcesCompat.getFont(activity, id)!! }
        val art = BannerAd(
            Box(0f, 692f, 360f, 60f),
            Type(f(namvunhatle.r15.onboarding.R.font.a7_bevietnampro_semibold), ZenText.BodySmall),
            Type(f(namvunhatle.r15.onboarding.R.font.a7_bevietnampro_regular), ZenText.Caption),
            Type(f(namvunhatle.r15.onboarding.R.font.a7_bevietnampro_medium), ZenText.Caption),
        )
        slot.addView(View(activity).apply { background = art }, FrameLayout.LayoutParams(-1, -1))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun showInterstitial(activity: Activity, onClosed: () -> Unit) {
        val creative = BitmapFactory.decodeResource(activity.resources, R.drawable.inter_fill, BitmapFactory.Options().apply { inScaled = false })
        val art = Interstitial(
            Box(0f, 0f, 360f, 800f), creative,
            Type(ResourcesCompat.getFont(activity, R.font.mona_sans_medium)!!, AdKit.Size.SizeLabelMd, AdKit.Height.HeightLabelMd, 0.48f),
            PathParser.createPathFromPathData(Interstitial.CLOSE),
        )
        val d = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(activity).apply { setBackgroundColor(A7Visual.AD_WINDOW) }
        val ad = View(activity).apply { background = art; contentDescription = "Ad" }
        root.addView(ad, FrameLayout.LayoutParams(-1, -1))
        // "Skip ads" = the pill in the top-right corner (248, 12, 110 × 44 in the 360 × 800 frame)
        val skip = View(activity).apply { contentDescription = "Skip ads"; isClickable = true }
        root.addView(skip)
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val sx = (r - l) / 360f; val sy = (b - t) / 800f
            skip.layoutParams = FrameLayout.LayoutParams((110 * sx).toInt(), (44 * sy).toInt()).apply { leftMargin = (248 * sx).toInt(); topMargin = (12 * sy).toInt() }
        }
        skip.setOnClickListener { d.dismiss() }
        d.setContentView(root)
        d.setCancelable(false)
        d.setOnDismissListener { dialog = null; onClosed() }
        d.window?.let { w ->
            w.setLayout(-1, -1)
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        // like an SDK activity: no system bars over the ad (the window must be showing for the controller to apply)
        d.setOnShowListener { d.window?.let { w -> WindowCompat.getInsetsController(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars()) } }
        dialog = d
        d.show()
    }

    override fun native(activity: Activity, slot: ViewGroup, which: Int, onResult: (Boolean) -> Unit) {
        val v = LayoutInflater.from(activity).inflate(R.layout.mock_native_ad, slot, false)
        val (c0, c1) = if (which == 1) A7Visual.NATIVE_TINT_1 else A7Visual.NATIVE_TINT_2
        v.findViewById<CssBox>(R.id.nat_icon).tint(c0, c1, 135f)
        v.findViewById<CssBox>(R.id.nat_media).tint(c0, c1, 135f)
        // under the slot's loading skeleton (index 0), so the skeleton fades off it
        slot.addView(v, 0, FrameLayout.LayoutParams(-1, -1))
        if (which == 1) onResult(true) else main.postDelayed({ onResult(true) }, 900)
    }

    override fun release() {
        main.removeCallbacksAndMessages(null)
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
    }
}
