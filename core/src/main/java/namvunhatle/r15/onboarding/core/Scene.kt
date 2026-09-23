package namvunhatle.r15.onboarding.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A box in the 360×800 design frame, in dp. */
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
}

/** A burst genre tile (P04 keyframe 05). */
data class Mini(val wall: String, val label: String, val c0: Int, val c1: Int, val dx: Float, val dy: Float, val rot: Float, val tw: Float)

/** A stroked circle: diameter + stroke in dp, white at [alpha]. */
data class Ring(val id: String, val d: Float, val stroke: Float, val alpha: Float)

data class Track(val id: String, val title: String, val artist: String, val bpm: Int, val preroll: Double)

/**
 * Everything both UIs need to lay the scene out: sprite boxes (Figma export of section `15552:115354`,
 * 360×800 frame), the HTML-built parts' geometry, and the element ids the timeline animates.
 * Ported from prototype-a7 v1.3.3 (src/App.tsx + src/index.css).
 */
class Scene(ctx: Context) {
    val pos: Map<String, Box>
    private val bbox: Map<String, Box>
    val tracks: List<Track>

    init {
        val m = JSONObject(ctx.assets.open("manifest.json").bufferedReader().readText())
        val p = HashMap<String, Box>(); val b = HashMap<String, Box>()
        for (k in m.keys()) {
            val e = m.getJSONObject(k)
            p[k] = e.getJSONArray("pos").box(); b[k] = e.getJSONArray("bbox").box()
        }
        pos = p; bbox = b
        val t = JSONArray(ctx.assets.open("tracks.json").bufferedReader().readText())
        tracks = (0 until t.length()).map { i ->
            val o = t.getJSONObject(i)
            Track(o.getString("id"), o.getString("title"), o.getString("artist"), o.getInt("bpm"), o.getDouble("preroll"))
        }
    }

    private fun JSONArray.box() = Box(getDouble(0).toFloat(), getDouble(1).toFloat(), getDouble(2).toFloat(), getDouble(3).toFloat())

    /** Sprite resource name (drawable-nodpi, lowercase). */
    fun res(k: String) = k.lowercase()

    fun center(k: String) = pos.getValue(k).let { it.cx to it.cy }
    /** Centre of the full (unclipped) wall tile — `bbox` keeps the part outside the screen. */
    fun wallCenter(k: String) = bbox.getValue(k).let { it.cx to it.cy }

    /** The 4 genre tiles of the burst, built natively (Figma fills of `Tile · *`), centred on their wall slot. */
    fun gtileBox(m: Mini) = wallCenter(m.wall).let { (cx, cy) -> Box(cx - 84f, cy - 60f, 168f, 120f) }

    companion object {
        const val W = 360f
        const val H = 800f
        const val ACCENT = 0xFFBB4ABF.toInt()

        // Splash logo centre — the camera flies through this point into G01.
        const val LX = 180f
        const val LY = 298f

        val TILES = listOf("tile_hiphop", "tile_rock", "tile_country", "tile_holiday", "tile_alarm", "tile_rnb", "tile_sfx", "tile_baby", "tile_msg")
        val G02_STICKERS = listOf("st_mia", "st_leo", "st_zoe", "st_noah")
        val G04_STICKERS = listOf("g04_sam", "g04_emma", "g04_jake", "g04_mia", "g04_leo", "g04_zoe")
        val HEADS = listOf("g01_head", "g02a_head", "g02b_head", "g03_head")
        val BGS = listOf("bg_G01", "bg_G02a", "bg_G02b", "bg_G02c", "bg_G03", "bg_G04")

        // P04 keyframe 05 (`15560:138820`): dx/dy = offset from the burst centre, rot = keyframe rotation; wall tiles sit at 12°.
        val MINIS = listOf(
            Mini("tile_hiphop", "Hip-Hop", 0xFFED8002.toInt(), 0xFF590023.toInt(), -62.3f, -45.2f, 18f, 70.56f),
            Mini("tile_rock", "Rock", 0xFFE94E75.toInt(), 0xFF46004E.toInt(), 58.9f, -79.5f, -10f, 77.28f),
            Mini("tile_holiday", "Holiday", 0xFF3FAB53.toInt(), 0xFF003932.toInt(), -35.2f, 48.1f, -8f, 63.84f),
            Mini("tile_country", "Country", 0xFFC99A2C.toInt(), 0xFF571400.toInt(), 56.7f, 71.5f, 12f, 73.92f),
        )
        val MINI_WALLS = MINIS.map { it.wall }
        val REST_TILES = TILES.filter { it !in MINI_WALLS }

        // A7 / Progress Wave — bar heights copied from the Figma component.
        val WAVE_H = intArrayOf(7, 8, 4, 6, 9, 10, 5, 16, 13, 11, 9, 17, 16, 7, 14, 10, 17, 9, 16, 15, 9, 7, 23, 23, 8, 14, 23, 9, 13, 15, 16, 10, 8, 19, 11, 8, 11, 8, 6, 12, 7, 7, 4, 10)
        val WAVE_BARS = WAVE_H.indices.map { "wave_bar_$it" }

        // Logo rings (P01): id, diameter, stroke, white alpha
        val EMITS = listOf(Ring("emit0", 136f, 1.5f, 0.45f), Ring("emit1", 136f, 1.5f, 0.45f))
        val SRINGS = listOf(Ring("sring0", 136f, 1f, 0.18f), Ring("sring1", 184f, 1f, 0.18f), Ring("sring2", 232f, 1f, 0.18f))
        // P04 keyframe 05: ring strokes 55 / 30 / 14 % white, 2 / 2 / 1.5 px, centred on the logo
        val BRINGS = listOf(Ring("bring0", 300f, 2f, 0.55f), Ring("bring1", 520f, 2f, 0.30f), Ring("bring2", 800f, 1.5f, 0.14f))
        // G03 rings under the phone, centred (180, 318)
        val G3RINGS = listOf(Ring("g3ring0", 224f, 1.5f, 0.28f), Ring("g3ring1", 300f, 1.5f, 0.28f), Ring("g3ring2", 380f, 1.5f, 0.28f))
        const val G3_CX = 180f
        const val G3_CY = 318f

        val GHOSTS = listOf("ghost0", "ghost1", "ghost2")

        // HTML-built parts (index.css)
        val CARD = Box(24f, 414f, 312f, 230f)
        // Web box is 137,283 203×94 with the pill centred and overflowing (white-space: nowrap). Android clips a
        // fading layer to its bounds, so the box is grown around the same centre; the origin below keeps the
        // web's pivot (15 % 60 % of the original box = 167.45, 339.4).
        val BUBBLE = Box(37f, 223f, 403f, 214f)
        val WAVE = Box(16f, 62f, 328f, 28f)
        val DIVIDER = Box(16f, 463f, 328f, 1f)
        val NATIVE = Box(16f, 480f, 328f, 256f)
        val SP_TRACK = Box(24f, 644f, 312f, 8f)
        val FLASH = Box(LX - 150f, LY - 150f, 300f, 300f)
        val PUNCH = Box(167.5f, 7.5f, 25f, 25f) // 22 px hole + 1.5 px black ring (box-shadow spread)

        // Hotspots
        val HOT_CTA = Box(15f, 343f, 330f, 58f)
        val HOT_SECONDARY = Box(16f, 407f, 328f, 48f)
        val HOT_SKIP = Box(248f, 12f, 110f, 44f)
        val HOT_PAYWALL_CLOSE = Box(300f, 28f, 52f, 52f)
        val HOT_SUBSCRIBE = Box(20f, 638f, 320f, 52f)

        /** Transform origins that are not the box centre, as fractions of the element's box. */
        val ORIGIN = mapOf(
            "splash_bg" to (LX / W to LY / H),
            "g03_bubble" to ((137f + 0.15f * 203f - 37f) / 403f to (283f + 0.6f * 94f - 223f) / 214f),
            "g02_phone" to (0.5f to 0f),
            "g03_phone" to (0.5f to 0f),
            "pill_sam" to (0f to 0.5f), "pill_emma" to (0f to 0.5f), "pill_jake" to (0f to 0.5f),
            "sp_fill" to (0f to 0.5f),
            "bw_hey" to (0.5f to 0.7f), "bw_sam" to (0.5f to 0.7f), "bw_call" to (0.5f to 0.7f),
        )
        fun origin(id: String) = ORIGIN[id] ?: (0.5f to 0.5f)
    }
}
