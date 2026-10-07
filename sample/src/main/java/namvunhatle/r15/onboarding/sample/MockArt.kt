package namvunhatle.r15.onboarding.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import namvunhatle.r15.onboarding.core.A7Visual
import namvunhatle.r15.onboarding.core.AdKit
import namvunhatle.r15.onboarding.core.Box
import namvunhatle.r15.onboarding.core.FigmaArt
import namvunhatle.r15.onboarding.core.Line
import namvunhatle.r15.onboarding.core.Type
import namvunhatle.r15.onboarding.core.Zen
import namvunhatle.r15.onboarding.core.Zen.Color.Background.Support
import namvunhatle.r15.onboarding.core.rect
import namvunhatle.r15.onboarding.core.text

/* Mock ad art for the demo (MockAds) — moved out of the library in 1.6.6: the onboarding no longer draws ads, the
   host's ad SDK does. Same Figma drawing as v1.3.3–1.3.7. */

private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/** A7 / Ad Slot · Banner (`15553:121168`) as placed under the P01 progress block. */
class BannerAd(box: Box, private val semi12: Type, private val reg10: Type, private val med10: Type) : FigmaArt(box) {
    private val white = Paint().apply { color = Zen.Color.Background.WhiteSolid.Default }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Support.Neutral.Subtle }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Zen.Color.Background.Warning.Solid.Default }
    private val head = semi12.paint(Zen.Color.Content.OnWhiteOverlay.Strongest)
    private val body = reg10.paint(Zen.Color.Content.OnWhiteOverlay.Base)
    private val ad = med10.paint(Zen.Color.Content.OnBrights)
    private val r = Zen.CornerRadius.Small

    override fun paint(c: Canvas) {
        c.drawRect(box.x, 692f, box.right, 752f, white) // full width of the screen
        c.drawRoundRect(12f, 704f, 48f, 740f, r, r, icon)
        c.text(Line("Advertiser headline", 60f, 706f), semi12, head)
        c.text(Line("One line from the ad network", 60f, 722f), reg10, body)
        c.drawRoundRect(322f, 714f, 348f, 730f, r, r, badge) // Rounded, on a 16 dp badge
        c.text(Line("Ad", 328f, 714f), med10, ad)
    }
}

/**
 * 03 · Interstitial (SDK) (`15560:138551`): the ad creative is the frame's image fill (FILL = cover, radius 24), with
 * the SDK chrome on top — "Ads progress" (two 4 px round-capped lines, 70 px played + the rest at 25 %) and the
 * "Skip ads" pill (white 17 %, Mona Sans Medium 12/16 +4 %, close-circle-fill). The creative is a bitmap in Figma too.
 *
 * [box] is the visible screen: the creative covers it and the chrome holds the top edge (the pill the right side,
 * the progress spans the width). Past the rounded corners is black — a real SDK activity's window.
 */
class Interstitial(box: Box, private val creative: Bitmap, type: Type, private val closeIcon: Path) : FigmaArt(box) {
    private val t = type
    private val black = Paint().apply { color = A7Visual.AD_WINDOW }
    private val frame = Path().apply { addRoundRect(box.rect(), Zen.CornerRadius._2XLarge, Zen.CornerRadius._2XLarge, Path.Direction.CW) }
    private val cover = (maxOf(box.w / creative.width, box.h / creative.height)).let { k ->
        RectF(box.cx - creative.width * k / 2, box.cy - creative.height * k / 2, box.cx + creative.width * k / 2, box.cy + creative.height * k / 2)
    }
    // as exported: the round caps sit inside each line's length, centred 6 dp down (not on the frame's 8 dp padding)
    private val top = box.y + 6f
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; color = A7Visual.AD_PROGRESS }
    private val rest = Paint(played).apply {
        val x0 = box.x + 16f + 74f; val x1 = box.right - 16f
        shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(A7Visual.AD_PROGRESS, A7Visual.AD_PROGRESS, A7Visual.AD_PROGRESS_REST, A7Visual.AD_PROGRESS_REST), floatArrayOf(0f, 0.25f, 0.25f, 1f), Shader.TileMode.CLAMP)
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AdKit.Background.BgOverlay }
    private val ink = t.paint(AdKit.Content.CtEmphasis)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AdKit.Content.CtMedium }

    override fun paint(c: Canvas) {
        c.drawRect(box.rect(), black)
        c.withClip(frame) { drawBitmap(creative, null, cover, FILTER) }
        c.drawLine(box.x + 16f + 2f, top, box.x + 16f + 70f - 2f, top, played)
        c.drawLine(box.x + 16f + 74f + 2f, top, box.right - 16f - 2f, top, rest)
        val px = box.right - 16f - 89f; val py = box.y + 16f
        c.drawRoundRect(px, py, px + 89f, py + 27f, 13.5f, 13.5f, pill)
        c.text(Line("Skip ads", px + 8f, py + 5.5f), t, ink)
        c.withTranslation(px + 67f + 4f / 3f, py + 5.5f + 4f / 3f) { drawPath(closeIcon, icon) }
    }

    companion object {
        /** close-circle-fill (13.33 × 13.33). */
        const val CLOSE = "M6.66667 13.3333 C2.98477 13.3333 0 10.3485 0 6.66667 C0 2.98477 2.98477 0 6.66667 0 C10.3485 0 13.3333 2.98477 13.3333 6.66667 C13.3333 10.3485 10.3485 13.3333 6.66667 13.3333 Z M6.66667 5.72387 L4.78105 3.83824 L3.83824 4.78105 L5.72387 6.66667 L3.83824 8.55227 L4.78105 9.49507 L6.66667 7.60947 L8.55227 9.49507 L9.49507 8.55227 L7.60947 6.66667 L9.49507 4.78105 L8.55227 3.83824 L6.66667 5.72387 Z"
    }
}
