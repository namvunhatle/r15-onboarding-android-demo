package namvunhatle.r15.onboarding.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import java.io.File
import kotlin.math.ceil

/**
 * Every scene element that v1.3.3 shipped as a Figma sprite, rebuilt as native drawing ([FigmaArt]). Both UIs ask for
 * an element by its timeline id and draw it in the same box the sprite used.
 *
 * What stays a bitmap is what is a bitmap in Figma too: the splash's image fill and smoke photo, and the phone
 * screens (image fills of `A7 / Phone`). The interstitial and the three destination mocks are other products'
 * screens and keep their screenshots.
 */
class A7Native(private val ctx: Context, private val scene: Scene) {
    private fun font(id: Int): Typeface = ResourcesCompat.getFont(ctx, id)!!
    private fun bitmap(id: Int): Bitmap = BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })

    private val anton = font(R.font.anton_regular)
    private val be400 = font(R.font.bevietnampro_regular)
    private val be500 = font(R.font.bevietnampro_medium)
    private val be600 = font(R.font.bevietnampro_semibold)

    // Figma text styles used by the scene
    private val name20 = Type(be600, 20f, 28f, -0.72f)
    private val tile32 = Type(be600, 32f, 40f, -1.44f)

    private val screens by lazy { mapOf("g01_phone" to bitmap(R.drawable.screen_g01), "g02_phone" to bitmap(R.drawable.screen_g02), "g03_phone" to bitmap(R.drawable.screen_g03)) }
    private val spots = HashMap<String, Bitmap>()

    fun art(id: String): Drawable {
        val box = scene.pos.getValue(id)
        return when (id) {
            "splash_bg" -> SplashBg(
                box, bitmap(R.drawable.splash_base), A7Glow.bake(Scene.W, Scene.H, null, SplashBg.GLOWS), bitmap(R.drawable.splash_wisps),
                PathParser.createPathFromPathData(FigmaPaths.SPLASH_GRID),
            )
            in Spotlight.SPOTS -> Spotlight(box, spots.getOrPut(id) { Spotlight.bake(id) })
            // P01's text sits where the v1.3.3 sprites put it: 1.5 / 0.5 dp above the current Figma boxes (446 / 660).
            "tagline" -> TextArt(box, name20, argb(0.96f), listOf(Line("A world of ringtones.", 180.5f, 444.5f, true), Line("Personalized for you.", 180.5f, 472.5f, true)))
            "sp_note" -> TextArt(box, Type(be400, 10f, 16f, -0.12f), argb(0.8f), listOf(Line("This action may contain ads.", 179.5f, 659.5f, true)))
            "sp_strip" -> BannerAd(box, Type(be600, 12f, 16f, -0.28f), Type(be400, 10f, 16f, -0.12f), Type(be500, 10f, 16f, -0.12f))
            "statusbar" -> StatusBar(
                box, Type(Typeface.create("sans-serif", Typeface.NORMAL), 14f, 20f, 0.25f),
                vector(R.drawable.ic_sb_wifi), vector(R.drawable.ic_sb_signal), vector(R.drawable.ic_sb_battery),
            )
            in TILES -> TILES.getValue(id).let { (title, colors) ->
                val (cx, cy) = scene.wallCenter(id)
                GenreTile(box, cx, cy, colors.first, colors.second, tile32, title)
            }
            in HEADS -> Headline(box, anton, HEADS.getValue(id))
            "g01_phone" -> Phone(box, RectF(64f, 516f, 296f, 1012f), screens.getValue(id))
            "g02_phone" -> Phone(box, RectF(64f, 192f, 296f, 688f), screens.getValue(id))
            "g03_phone" -> Phone(box, RectF(120.16f, 189.9f, 239.84f, 445.1f), screens.getValue(id))
            in STICKERS -> STICKERS.getValue(id).let { s -> Sticker(box, name20, s.name, s.w, s.cx, s.cy, s.rot, s.stroke) }
            "g04_center" -> NameCenter(box, anton, name20)
            "g04_cta" -> CtaButton(box, Type(be600, 16f, 24f, -0.4f), "Explore AI Ringtones")
            "g04_secondary" -> FlatButton(box, Type(be600, 14f, 20f, -0.32f), "Browse ringtones", 421f, argb(0.8f))
            else -> error("no native art for $id")
        }
    }

    private fun vector(id: Int) = ResourcesCompat.getDrawable(ctx.resources, id, null)!!

    /**
     * Review tool: render every native element at 2× its box into [dir] (`<id>.png`), for a pixel diff against the
     * v1.3.3 sprites.
     */
    fun dump(dir: File) {
        dir.mkdirs()
        for (id in IDS) {
            val b = scene.pos.getValue(id)
            val bmp = createBitmap(ceil(b.w * 2).toInt(), ceil(b.h * 2).toInt())
            art(id).apply { setBounds(0, 0, bmp.width, bmp.height) }.draw(Canvas(bmp))
            File(dir, "$id.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private class StickerSpec(val name: String, val w: Float, val cx: Float, val cy: Float, val rot: Float, val stroke: Int)

    companion object {
        private fun argb(a: Float, rgb: Int = 0xFFFFFF) = ((a * 255f + .5f).toInt() shl 24) or rgb

        private const val YELLOW = 0xFFFFBA1A.toInt()
        private const val CYAN = 0xFF10AEDA.toInt()
        private const val PINK = 0xFFE854B2.toInt()
        private const val GREEN = 0xFF3FAB53.toInt()

        /** Wall tiles drawn from sprites in v1.3.3 (the 4 burst tiles were already native). Titles wrap as in Figma. */
        private val TILES = mapOf(
            "tile_alarm" to (listOf("Alarm") to GenreTile.BLUE),
            "tile_rnb" to (listOf("R&B") to GenreTile.PINK),
            "tile_sfx" to (listOf("Sound", "Effects") to GenreTile.VIOLET),
            "tile_baby" to (listOf("Baby") to GenreTile.PINK),
            "tile_msg" to (listOf("Message", "Tones") to GenreTile.ORANGE),
        )

        private const val W = NameCenter.WHITE
        private fun head(text: String, e1: Int, e2: Int) = listOf(
            Headline.Echo(Line(text, 180f, 126f, true), e1, 1.5f),
            Headline.Echo(Line(text, 180f, 132f, true), e2, 1.5f),
            Headline.Echo(Line(text, 180f, 120f, true), W),
        )

        /**
         * Headline frames of G01–G03. G02c reuses the G02b stack, as the sprite build did. "Headline" lines are solid
         * white and "Headline · line 2" is 96 % — what the v1.3.3 export shows (Figma now has 96 % on both).
         */
        private val HEADS = mapOf(
            "g01_head" to listOf(
                Headline.Echo(Line("THOUSANDS OF", 180f, 120f, true), W),
                Headline.Echo(Line("RINGTONES", 180f, 180f, true), 0xFFBB4ABF.toInt(), 1.5f, Align.OUTSIDE),
                Headline.Echo(Line("RINGTONES", 180f, 174f, true), 0xFF4752F0.toInt(), 1.5f, Align.OUTSIDE),
                Headline.Echo(Line("RINGTONES", 180f, 168f, true), NameCenter.WHITE_96),
            ),
            "g02a_head" to head("SAME SONG.", 0xFFBB4ABF.toInt(), 0xFF4752F0.toInt()),
            "g02b_head" to head("ANY NAME.", 0xFFE854B2.toInt(), 0xFF7755E7.toInt()),
            "g03_head" to head("RINGS FOR YOU.", 0xFF7755E7.toInt(), 0xFFE854B2.toInt()),
        )

        /** Name stickers: G02b/c (`st_*`) and G04 (`g04_*`). Width, centre and rotation from the instances. */
        private val STICKERS = mapOf(
            "st_mia" to StickerSpec("Mia", 67f, 36.112f, 270.448f, -8f, YELLOW),
            "st_leo" to StickerSpec("Leo", 68f, 294.428f, 333.692f, 7f, CYAN),
            "st_zoe" to StickerSpec("Zoe", 70f, 47.108f, 402.221f, 6f, PINK),
            "st_noah" to StickerSpec("Noah", 83f, 284.973f, 238.217f, -6f, GREEN),
            "g04_sam" to StickerSpec("Sam", 75f, 56f, 114f, -8f, YELLOW),
            "g04_emma" to StickerSpec("Emma", 92f, 296f, 74f, 6f, CYAN),
            "g04_jake" to StickerSpec("Jake", 76f, 50f, 206f, 6f, PINK),
            "g04_mia" to StickerSpec("Mia", 67f, 308f, 174f, -7f, GREEN),
            "g04_leo" to StickerSpec("Leo", 68f, 62f, 296f, -5f, YELLOW),
            "g04_zoe" to StickerSpec("Zoe", 70f, 300f, 276f, 8f, CYAN),
        )

        /**
         * Elements whose drawing is worth caching: paths, text, shadow blurs. The UIs give each one its own GPU layer, so
         * it is drawn once at device resolution and the timeline then only moves, scales and fades that layer — the cost
         * per frame of a bitmap sprite, with the content still drawn by code. The spotlights are a single bitmap draw
         * already and skip the layer (six full-screen layers would cost ~60 MB of GPU memory for nothing).
         */
        val LAYERED: Set<String> by lazy { (IDS - Scene.BGS.toSet()).toSet() }

        /** Ids drawn by [art] — every other sprite id is still a bitmap. */
        val IDS: List<String> = listOf("splash_bg", "tagline", "sp_note", "sp_strip", "statusbar") + Scene.BGS + TILES.keys +
            Scene.HEADS + listOf("g01_phone", "g02_phone", "g03_phone") + STICKERS.keys + listOf("g04_center", "g04_cta", "g04_secondary")
    }
}
