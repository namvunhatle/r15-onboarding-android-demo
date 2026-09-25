package namvunhatle.r15.onboarding.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import namvunhatle.r15.onboarding.core.Zen.Color.Background.Support

/** A box in the 360×800 design frame, in dp. */
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
    val right get() = x + w
    val bottom get() = y + h
    fun dy(d: Float) = if (d == 0f) this else copy(y = y + d)
    fun outset(d: Float) = Box(x - d, y - d, w + 2 * d, h + 2 * d)
    fun intersect(o: Box): Box {
        val l = maxOf(x, o.x); val t = maxOf(y, o.y)
        return Box(l, t, (minOf(right, o.right) - l).coerceAtLeast(0f), (minOf(bottom, o.bottom) - t).coerceAtLeast(0f))
    }
}

/** A burst genre tile (P04 keyframe 05). */
data class Mini(val wall: String, val label: String, val c0: Int, val c1: Int, val dx: Float, val dy: Float, val rot: Float, val tw: Float)

/** A stroked circle: diameter + stroke in dp, white at [alpha]. */
data class Ring(val id: String, val d: Float, val stroke: Float, val alpha: Float)

/** [introFrames] / [loopFrames]: exact file lengths at [sampleRate], to trim codec padding off the decoded PCM. */
data class Track(
    val id: String, val title: String, val artist: String, val bpm: Int, val preroll: Double,
    val sampleRate: Int, val introFrames: Int, val loopFrames: Int,
)

/**
 * Everything both UIs need to lay the scene out: sprite boxes (Figma export of section `15552:115354`,
 * 360×800 frame), the HTML-built parts' geometry, and the element ids the timeline animates.
 * Ported from prototype-a7 v1.3.3 (src/App.tsx + src/index.css).
 *
 * [vp] adapts it to the phone's screen ([Viewport]); on a 20:9 screen every box is the v1.3.3 one.
 */
class Scene(ctx: Context, val vp: Viewport = Viewport.FRAME) {
    val pos: Map<String, Box>
    private val bbox: Map<String, Box>
    private val pos0: Map<String, Box>
    private val shift = HashMap<String, Float>()
    val tracks: List<Track>

    /** The visible area, in frame coordinates. */
    val view = vp.view
    /** Wall tiles added past the Figma wall's ends when the screen is wider than 20:9: id → the tile it repeats. */
    val extraTiles: Map<String, String>
    val tiles: List<String>
    val restTiles: List<String>

    // anchored HTML-built parts
    val wave = WAVE.dy(-vp.my)
    val spTrack = SP_TRACK.dy(vp.my)
    val punch = PUNCH.dy(-vp.my)
    /** "Skip ads" pill: holds the top-right corner. */
    val hotSkip = HOT_SKIP.copy(x = HOT_SKIP.x + vp.mx).dy(-vp.my)

    init {
        val m = JSONObject(ctx.assets.open("manifest.json").bufferedReader().readText())
        val p = HashMap<String, Box>(); val b = HashMap<String, Box>()
        for (k in m.keys()) {
            val e = m.getJSONObject(k)
            p[k] = e.getJSONArray("pos").box(); b[k] = e.getJSONArray("bbox").box()
        }
        pos0 = HashMap(p)
        val extra = LinkedHashMap<String, String>()
        if (!vp.isFrame) {
            BLEED.forEach { p[it] = view }
            FULL_WIDTH.forEach { k -> p.getValue(k).let { p[k] = grow(it, Box(view.x, it.y, view.w, it.h)) } }
            // cut by the frame edge in the v1.3.3 export: grow (about the same centre, so the motion is unchanged)
            // until the node and its shadow are whole again, as far as the screen shows
            (REST_TILES + G02_STICKERS + G04_STICKERS + "g01_phone").forEach { k -> p[k] = grow(p.getValue(k), b.getValue(k).outset(24f).intersect(view)) }
            TOP.forEach { k -> p[k] = p.getValue(k).dy(-vp.my); shift[k] = -vp.my }
            BOTTOM.forEach { k -> p[k] = p.getValue(k).dy(vp.my); shift[k] = vp.my }
            // the wall repeats row by row: one more tile at each end of a row, as far as the screen shows
            val step = b.getValue(TILES[1]).cx - b.getValue(TILES[0]).cx to b.getValue(TILES[1]).cy - b.getValue(TILES[0]).cy
            WALL_ROWS.forEachIndexed { r, row ->
                for ((side, from, copy) in listOf(Triple(-1, row.first(), row.last()), Triple(1, row.last(), row.first()))) {
                    val c = b.getValue(from)
                    val t = Box(c.cx + side * step.first - c.w / 2, c.cy + side * step.second - c.h / 2, c.w, c.h)
                    val vis = t.intersect(view)
                    if (vis.w <= 0f || vis.h <= 0f || (vis.x >= 0f && vis.right <= W && vis.y >= 0f && vis.bottom <= H)) continue
                    val id = "tile_x$r${if (side < 0) "l" else "r"}"
                    extra[id] = copy; b[id] = t; p[id] = vis
                }
            }
        }
        pos = p; bbox = b
        extraTiles = extra
        tiles = TILES + extra.keys
        restTiles = REST_TILES + extra.keys
        val t = JSONArray(ctx.assets.open("tracks.json").bufferedReader().readText())
        tracks = (0 until t.length()).map { i ->
            val o = t.getJSONObject(i)
            Track(
                o.getString("id"), o.getString("title"), o.getString("artist"), o.getInt("bpm"), o.getDouble("preroll"),
                o.getInt("sampleRate"), o.getInt("introFrames"), o.getInt("loopFrames"),
            )
        }
    }

    /** [b] grown symmetrically until it covers [need] on the sides where it touches the 360×800 frame. */
    private fun grow(b: Box, need: Box): Box {
        val dx = maxOf(if (b.x <= 0.5f) b.x - need.x else 0f, if (b.right >= W - 0.5f) need.right - b.right else 0f, 0f)
        val dy = maxOf(if (b.y <= 0.5f) b.y - need.y else 0f, if (b.bottom >= H - 0.5f) need.bottom - b.bottom else 0f, 0f)
        return Box(b.x - dx, b.y - dy, b.w + 2 * dx, b.h + 2 * dy)
    }

    /** The box an element's art is drawn for: [pos] before any anchoring move, so the art keeps frame coordinates. */
    fun artBox(id: String) = pos.getValue(id).dy(-(shift[id] ?: 0f))

    /** Transform origin as fractions of the element's box: the v1.3.3 pivot, carried along if the element moved. */
    fun origin(id: String): Pair<Float, Float> {
        val o = ORIGIN[id] ?: (0.5f to 0.5f)
        val b0 = pos0[id] ?: return o
        val b = pos.getValue(id)
        if (b == b0) return o
        return (b0.x + o.first * b0.w - b.x) / b.w to (b0.y + (shift[id] ?: 0f) + o.second * b0.h - b.y) / b.h
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
        /** The brand accent: Color/Background/Accent/Solid/Default (wave, progress, glows, pills). */
        val ACCENT = Zen.Color.Background.Accent.Solid.Default

        // Splash logo centre — the camera flies through this point into G01.
        const val LX = 180f
        const val LY = 298f

        val TILES = listOf("tile_hiphop", "tile_rock", "tile_country", "tile_holiday", "tile_alarm", "tile_rnb", "tile_sfx", "tile_baby", "tile_msg")
        private val WALL_ROWS = TILES.chunked(3)
        val G02_STICKERS = listOf("st_mia", "st_leo", "st_zoe", "st_noah")
        val G04_STICKERS = listOf("g04_sam", "g04_emma", "g04_jake", "g04_mia", "g04_leo", "g04_zoe")
        val HEADS = listOf("g01_head", "g02a_head", "g02b_head", "g03_head")
        val BGS = listOf("bg_G01", "bg_G02a", "bg_G02b", "bg_G02c", "bg_G03", "bg_G04")

        // P04 keyframe 05 (`15560:138820`): dx/dy = offset from the burst centre, rot = keyframe rotation; wall tiles sit at 12°.
        val MINIS = listOf(
            Mini("tile_hiphop", "Hip-Hop", Support.Orange.Solid, Support.Crimson.Deep, -62.3f, -45.2f, 18f, 70.56f),
            Mini("tile_rock", "Rock", Support.Crimson.Solid, Support.Plum.Deep, 58.9f, -79.5f, -10f, 77.28f),
            Mini("tile_holiday", "Holiday", Support.Green.Solid, Support.Teal.Deep, -35.2f, 48.1f, -8f, 63.84f),
            Mini("tile_country", "Country", Support.Golden.Solid, Support.Bronze.Deep, 56.7f, 71.5f, 12f, 73.92f),
        )
        val MINI_WALLS = MINIS.map { it.wall }
        val REST_TILES = TILES.filter { it !in MINI_WALLS }

        // A7 / Progress Wave — bar heights copied from the Figma component.
        val WAVE_H = intArrayOf(7, 8, 4, 6, 9, 10, 5, 16, 13, 11, 9, 17, 16, 7, 14, 10, 17, 9, 16, 15, 9, 7, 23, 23, 8, 14, 23, 9, 13, 15, 16, 10, 8, 19, 11, 8, 11, 8, 6, 12, 7, 7, 4, 10)
        val WAVE_BARS = WAVE_H.indices.map { "wave_bar_$it" }

        // Logo rings (P01): id, diameter, stroke, white alpha
        val EMITS = listOf(Ring("emit0", 136f, 1.5f, 0.45f), Ring("emit1", 136f, 1.5f, 0.45f))
        val SRINGS = listOf(136f, 184f, 232f).mapIndexed { i, d -> Ring("sring$i", d, 1f, A7Visual.SPLASH_RING_OPACITY) }
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

        // Viewport responses (see [Viewport])
        private val BLEED = listOf("splash_bg", "bridge_full") + BGS
        private val FULL_WIDTH = listOf("sp_strip", "statusbar")
        private val TOP = listOf("statusbar") + HEADS
        private val BOTTOM = listOf("sp_note", "sp_strip")
    }
}
