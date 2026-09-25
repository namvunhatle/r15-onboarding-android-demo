package namvunhatle.r15.onboarding.compose

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import namvunhatle.r15.onboarding.core.A7Art
import namvunhatle.r15.onboarding.core.A7Native
import namvunhatle.r15.onboarding.core.A7Player
import namvunhatle.r15.onboarding.core.A7Visual
import namvunhatle.r15.onboarding.core.Bleed
import namvunhatle.r15.onboarding.core.Box as DBox
import namvunhatle.r15.onboarding.core.Css
import namvunhatle.r15.onboarding.core.Dest
import namvunhatle.r15.onboarding.core.El
import namvunhatle.r15.onboarding.core.Prop
import namvunhatle.r15.onboarding.core.R
import namvunhatle.r15.onboarding.core.Ring
import namvunhatle.r15.onboarding.core.Scene
import namvunhatle.r15.onboarding.core.Scene.Companion.ACCENT
import namvunhatle.r15.onboarding.core.TextToken
import namvunhatle.r15.onboarding.core.Zen
import namvunhatle.r15.onboarding.core.ZenText
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

private val Be = FontFamily(
    Font(R.font.bevietnampro_regular, FontWeight.W400),
    Font(R.font.bevietnampro_medium, FontWeight.W500),
    Font(R.font.bevietnampro_semibold, FontWeight.W600),
)
private val Accent = Color(ACCENT)

/** CSS-like text: fixed line box, half-leading centred, no font padding. Sizes are design px (fontScale locked to 1). */
private fun ts(t: TextToken, weight: Float, color: Int) = ts(t.size, t.line, weight.toInt(), Color(color), t.tracking)

private fun ts(size: Float, line: Float, weight: Int, color: Color, ls: Float = 0f) = TextStyle(
    fontFamily = Be, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), color = color,
    letterSpacing = if (ls == 0f) TextUnit.Unspecified else ls.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
private fun Text(
    text: String, modifier: Modifier = Modifier, style: TextStyle, maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true, overflow: TextOverflow = TextOverflow.Clip,
) = BasicText(text, modifier, style, overflow = overflow, softWrap = softWrap, maxLines = maxLines)

/** Frame clock + store handle shared by every element. */
private class Ctx(val player: A7Player, val art: A7Art, val native: A7Native, val tick: MutableLongState) {
    val store = player.store
}

/**
 * Place at a design box (dp in the 360×800 frame). Unbounded like CSS absolute positioning: a box larger than its
 * parent (the 520/800 dp burst rings, the widened bubble box) keeps its own size instead of being squeezed.
 */
private fun Modifier.at(b: DBox) =
    absoluteOffset(b.x.dp, b.y.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(b.w.dp, b.h.dp)

/** Bind an element's transform + opacity to the store. Runs in the draw phase only — no recomposition per frame. */
/**
 * [leaf] = one element drawn in one pass: opacity is applied to its draw calls (ModulateAlpha) instead of an
 * offscreen layer. Besides being cheaper, the layer would be clipped to the element's bounds while it fades —
 * cutting off glows and shadows that overflow the box (CSS never clips those).
 */
private fun Modifier.anim(c: Ctx, id: String, leaf: Boolean = false, cached: Boolean = false, scaleFrom: (El) -> Float = { 1f }): Modifier {
    val el = c.store[id]
    val (ox, oy) = c.player.scene.origin(id)
    return graphicsLayer {
        c.tick.longValue // read → re-run this block every frame
        val k = scaleFrom(el)
        translationX = el.x.dp.toPx(); translationY = el.y.dp.toPx()
        scaleX = el.sx * k; scaleY = el.sy * k
        rotationZ = el.rot
        alpha = el.alpha.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(ox, oy)
        compositingStrategy = when {
            cached -> CompositingStrategy.Offscreen
            leaf -> CompositingStrategy.ModulateAlpha
            else -> CompositingStrategy.Auto
        }
    }
}

@Composable
fun A7Screen(player: A7Player, art: A7Art, native: A7Native) {
    val tick = remember { mutableLongStateOf(0L) }
    var ui by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) {
        while (true) withFrameNanos { n ->
            player.frame(n)
            tick.longValue = n
            if (player.uiVersion != ui) ui = player.uiVersion
        }
    }
    val c = remember(player) { Ctx(player, art, native, tick) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(A7Visual.LETTERBOX))) {
        // Fit the 360×800 design frame (20:9) to the screen, never distort; the design dp becomes s × a real dp.
        // The scene fills the screen around the frame up to the viewport ([Scene.vp]); only past that is it cut.
        val s = min(maxWidth.value / Scene.W, maxHeight.value / Scene.H)
        val outer = LocalDensity.current
        val vp = player.scene.vp
        Box(Modifier.align(Alignment.Center).size((vp.view.w * s).dp, (vp.view.h * s).dp).clipToBounds()) {
            Box(Modifier.absoluteOffset((vp.mx * s).dp, (vp.my * s).dp).size((Scene.W * s).dp, (Scene.H * s).dp)) {
                CompositionLocalProvider(LocalDensity provides Density(outer.density * s, 1f)) {
                    Device(c, ui)
                }
            }
        }
        if (ui >= 0 && (player.atAd || player.dest != null)) ReplayFab(c, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun Device(c: Ctx, ui: Int) {
    val scene = c.player.scene
    Box(Modifier.size(Scene.W.dp, Scene.H.dp)) {
        Box(Modifier.size(Scene.W.dp, Scene.H.dp).anim(c, "cam")) {
            Sprite(c, "splash_bg")
            Scene.BGS.forEach { Sprite(c, it) }
            Logo(c)
            Sprite(c, "tagline")
            SplashTrack(c)
            Sprite(c, "sp_note")
            Sprite(c, "sp_strip")
            Scene.BRINGS.forEach { RingEl(c, it, Scene.LX, Scene.LY) }
            Box(Modifier.at(Scene.FLASH).anim(c, "flash", leaf = true).drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(A7Visual.FLASH_CORE), .35f to Color(A7Visual.FLASH_MID), .7f to Color(A7Visual.FLASH_EDGE),
                        center = center, radius = size.width / 2 * 1.41421f, // circle, farthest-corner
                    ),
                    radius = size.width / 2,
                )
            })
            // G01 genre wall
            Box(Modifier.size(Scene.W.dp, Scene.H.dp).anim(c, "tiles")) {
                scene.restTiles.forEach { Sprite(c, it) }
                Scene.MINIS.forEach { m ->
                    val b = scene.gtileBox(m)
                    val (x0, y0, x1, y1) = Css.linear(118f, 168f, 120f).toList()
                    Box(
                        Modifier.at(b).anim(c, m.wall).clip(RoundedCornerShape(Zen.CornerRadius.Large.dp)).drawBehind {
                            val px = size.width / 168f
                            drawRect(Brush.linearGradient(.128f to Color(m.c0), .872f to Color(m.c1), start = Offset(x0 * px, y0 * px), end = Offset(x1 * px, y1 * px)))
                        },
                    ) {
                        Text(
                            m.label, Modifier.padding(start = Zen.Spacing.Padding.Medium.dp, top = 64.dp),
                            style = ts(ZenText.Heading1, Zen.Emphasis.FontWeight.Bold, Zen.Color.Content.OnDarkOverlay.Strongest), maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
            Scene.G3RINGS.forEach { RingEl(c, it, Scene.G3_CX, Scene.G3_CY) }
            Sprite(c, "g01_phone"); Sprite(c, "g02_phone"); Sprite(c, "g03_phone")
            Scene.G02_STICKERS.forEach { Sprite(c, it) }
            LyricCard(c)
            Bubble(c)
            Scene.HEADS.forEach { Sprite(c, it) }
            Wave(c)
            Box(Modifier.at(Scene.DIVIDER).anim(c, "divider", leaf = true).background(Color(A7Visual.DIVIDER)))
            NativeAd(c, "nat1", 135f, A7Visual.NATIVE_TINT_1)
            NativeAd(c, "nat2", 135f, A7Visual.NATIVE_TINT_2)
            Scene.G04_STICKERS.forEach { Sprite(c, it) }
            Sprite(c, "g04_center"); Sprite(c, "g04_cta"); Sprite(c, "g04_secondary")
            Hot(c, Scene.HOT_CTA, pressId = "g04_cta", enabled = { c.player.ctaEnabled() && c.player.dest == null }) { c.player.go(Dest.PAYWALL) }
            Hot(c, Scene.HOT_SECONDARY, pressId = "g04_secondary", enabled = { c.player.ctaEnabled() && c.player.dest == null }) { c.player.go(Dest.HOME) }
            Scene.GHOSTS.forEach { Baked(c, it, c.art.ghost) }
            Baked(c, "dot", c.art.dot)
        }
        Sprite(c, "statusbar")
        // the Figma status bar marks the camera as a white dot — a real punch-hole is black
        Box(Modifier.at(scene.punch).drawBehind {
            drawCircle(Color(A7Visual.PUNCH))
            val u = size.width / 25f
            drawCircle(
                Brush.radialGradient(0f to Color(A7Visual.PUNCH_LIGHT), .55f to Color(A7Visual.PUNCH_MID), 1f to Color(A7Visual.PUNCH), center = Offset((1.5f + 22f * .38f) * u, (1.5f + 22f * .35f) * u), radius = 19.8f * u),
                radius = 11f * u,
            )
        })
        // Interstitial — third-party, R15 does not control it. Creative + SDK chrome, drawn natively over the screen.
        Box(Modifier.size(Scene.W.dp, Scene.H.dp).anim(c, "bridge")) {
            Sprite(c, "bridge_full")
            // A hidden layer must not take touches (CSS visibility: hidden does that for free; Compose does not):
            // hotspots of overlays exist only while their layer is up, re-read on every ui change.
            if (ui >= 0 && c.player.atAd) Hot(c, scene.hotSkip, enabled = { c.player.atAd }) { c.player.skipAd() }
        }
        // Destinations — the one opened last is on top
        val order = if (ui >= 0) c.player.destOrder.toList() else emptyList()
        (Dest.entries.filter { it !in order } + order).forEach { d ->
            Screenshot(c, A7Player.destId(d), "dest_" + d.name.lowercase()) {
                if (d == Dest.PAYWALL && c.player.dest == Dest.PAYWALL) {
                    Hot(c, Scene.HOT_PAYWALL_CLOSE, enabled = { c.player.dest == Dest.PAYWALL }) { c.player.go(Dest.AI) }
                    Hot(c, Scene.HOT_SUBSCRIBE, enabled = { c.player.dest == Dest.PAYWALL }) { c.player.go(Dest.AI) }
                }
            }
        }
    }
}

@SuppressLint("DiscouragedApi")
@Composable
private fun spriteBitmap(k: String): ImageBitmap {
    val ctx = LocalContext.current
    val id = remember(k) { ctx.resources.getIdentifier(k.lowercase(), "drawable", ctx.packageName) }
    return ImageBitmap.imageResource(id)
}

@Composable
private fun SpriteImage(k: String, modifier: Modifier) {
    val bmp = spriteBitmap(k)
    Image(remember(bmp) { BitmapPainter(bmp) }, null, modifier, contentScale = ContentScale.FillBounds)
}

/**
 * A full-screen screenshot [k] (a destination) on layer [id], at the frame; the screen margins around it continue its edges ([Bleed]).
 * [content] (hotspots) is laid out in the frame.
 */
@Composable
private fun Screenshot(c: Ctx, id: String, k: String, content: @Composable () -> Unit) {
    val scene = c.player.scene
    val bmp = spriteBitmap(k)
    val bleed = remember(bmp) { if (scene.vp.isFrame) null else Bleed(bmp.asAndroidBitmap(), scene.vp) }
    Box(Modifier.at(scene.view).anim(c, id).drawBehind {
        bleed?.run { setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt()); drawIntoCanvas { draw(it.nativeCanvas) } }
    }) {
        Box(Modifier.at(DBox(-scene.view.x, -scene.view.y, Scene.W, Scene.H))) {
            SpriteImage(k, Modifier.size(Scene.W.dp, Scene.H.dp))
            content()
        }
    }
}

/**
 * A scene element in its v1.3.3 sprite box. Native elements ([A7Native]) draw Figma geometry, text and effects; they
 * fade through the layer (not per draw call) because they are several overlapping draws, and the box already holds
 * every shadow, so the layer clips nothing. [A7Native.LAYERED] ones keep that layer (Offscreen): drawn once, then only
 * transformed. Only the interstitial and destinations are still screenshots.
 */
@Composable
private fun Sprite(c: Ctx, k: String) {
    val box = Modifier.at(c.player.scene.pos.getValue(k))
    if (k !in c.native.ids) return SpriteImage(k, box.anim(c, k, leaf = true))
    val art = remember(k) { c.native.art(k) }
    Box(box.anim(c, k, cached = k in c.native.layered).drawBehind {
        art.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { art.draw(it.nativeCanvas) }
    })
}

/** Draw a baked bitmap centred on this element's box, at its own dp size (it may overflow the box, like a CSS shadow). */
private fun DrawScope.baked(b: A7Art.Baked, img: ImageBitmap) {
    val d = b.dp.dp.toPx()
    drawImage(img, dstOffset = IntOffset((center.x - d / 2).roundToInt(), (center.y - d / 2).roundToInt()), dstSize = IntSize(d.roundToInt(), d.roundToInt()))
}

@Composable
private fun Baked(c: Ctx, id: String, b: A7Art.Baked) {
    val img = remember(b) { b.bitmap.asImageBitmap() }
    Box(Modifier.at(DBox(0f, 0f, 16f, 16f)).anim(c, id, leaf = true).drawBehind { baked(b, img) })
}

@Composable
private fun RingEl(c: Ctx, r: Ring, cx: Float, cy: Float) {
    Box(Modifier.at(DBox(cx - r.d / 2, cy - r.d / 2, r.d, r.d)).anim(c, r.id, leaf = true).drawBehind {
        val w = r.stroke.dp.toPx()
        drawCircle(Color(A7Visual.RING).copy(alpha = r.alpha), radius = size.width / 2 - w / 2, style = Stroke(w))
    })
}

/** Splash logo: rings + record, one group that the camera zooms through (scale = e^k, log-space zoom). */
@Composable
private fun Logo(c: Ctx) {
    val zoom = c.store["zoom"]
    val g = 232f
    val box = DBox(Scene.LX - g / 2, Scene.LY - g / 2, g, g)
    val icon = remember { c.art.icon.bitmap.asImageBitmap() }
    val glow = remember { c.art.iconGlow.bitmap.asImageBitmap() }
    val blur = remember { c.art.iconBlur.bitmap.asImageBitmap() }
    Box(Modifier.at(box).anim(c, "logo", scaleFrom = { exp(zoom[Prop.K]) })) {
        (Scene.EMITS + Scene.SRINGS).forEach { RingEl(c, it, g / 2, g / 2) }
        Box(Modifier.at(DBox(g / 2 - 48, g / 2 - 48, 96f, 96f)).anim(c, "icon", leaf = true).drawBehind {
            baked(c.art.iconGlow, glow)
            baked(c.art.icon, icon)
        })
    }
    // pre-blurred twin: crossfading to it = a blur ramp with no per-frame filter work
    Box(Modifier.at(box).anim(c, "logoB", leaf = true, scaleFrom = { exp(zoom[Prop.K]) }).drawBehind { baked(c.art.iconBlur, blur) })
}

@Composable
private fun SplashTrack(c: Ctx) {
    Box(Modifier.at(c.player.scene.spTrack).anim(c, "sp_track").clip(CircleShape).background(Color(Zen.Color.Background.Neutral.Subtle.Default))) {
        Box(Modifier.fillMaxSize().anim(c, "sp_fill").background(Accent, CircleShape))
    }
}

/** A7 / Lyric Card. Opaque on purpose: any see-through lets the phone's own "SAM" ghost through. */
@Composable
private fun LyricCard(c: Ctx) {
    val shape = RoundedCornerShape(Zen.CornerRadius._2XLarge.dp)
    val bold = Zen.Emphasis.FontWeight.Bold
    val text = Zen.Color.Content.OnDarkOverlay
    Box(
        Modifier.at(Scene.CARD).anim(c, "card")
            .drawBehind { cssShadow(0f, 20f, 40f, Color(A7Visual.SHADOW), Zen.CornerRadius._2XLarge) }
            .background(Brush.verticalGradient(listOf(Color(A7Visual.CARD_TOP), Color(A7Visual.CARD_BOTTOM))), shape)
            .border(1.dp, Color(Zen.Color.Border.Overlay.Subtle.Default), shape),
    ) {
        Column(Modifier.padding(Zen.Spacing.Padding.XLarge.dp), verticalArrangement = Arrangement.spacedBy(Zen.Spacing.Gap.Medium.dp)) {
            Row(Modifier.height(Zen.Tag.Size.Medium.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Zen.Spacing.Gap.XSmall.dp)) {
                Text("Morning Glow", style = ts(ZenText.BodyBase, bold, text.Strongest))
                Text("· Pop", style = ts(ZenText.BodyBase, Zen.Emphasis.FontWeight.Regular, text.Base))
                val tagPad = Zen.Tag.Spacing.Medium.HorizontalPadding + Zen.Tag.Spacing.Medium.TextWrapperPadding
                Text(
                    "Made by AI",
                    Modifier.background(Color(Zen.Tag.Background.Default), CircleShape).border(1.dp, Color(Zen.Tag.Border.Default), CircleShape)
                        .padding(horizontal = tagPad.dp, vertical = Zen.Tag.Spacing.Medium.VerticalPadding.dp),
                    style = ts(ZenText.BodyBase, Zen.Emphasis.FontWeight.Medium, Zen.Color.Content.Neutral.Strongest),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Zen.Spacing.Gap.XSmall.dp)) {
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hey", style = ts(ZenText.Heading2, bold, text.Strongest))
                    Spacer(Modifier.width(Zen.Spacing.Gap.XSmall.dp))
                    Box(Modifier.height(40.dp).fillMaxWidth()) {
                        for ((id, name) in listOf("pill_sam" to "Sam", "pill_emma" to "Emma", "pill_jake" to "Jake")) {
                            Text(
                                name,
                                Modifier.anim(c, id).height(40.dp).background(Accent, RoundedCornerShape(Zen.CornerRadius.Base.dp))
                                    .padding(horizontal = Zen.Spacing.Padding.Small.dp, vertical = Zen.Spacing.Padding._3XSmall.dp),
                                style = ts(ZenText.Heading2, bold, Zen.Color.Content.OnAccent.Default), maxLines = 1, softWrap = false,
                            )
                        }
                    }
                }
                Text("calling, lighting up", style = ts(ZenText.Heading4, bold, text.Base))
                Text("your phone, don’t act busy", style = ts(ZenText.Heading4, bold, text.Light))
            }
            Box(Modifier.size(120.dp, 8.dp).background(Color(Zen.Color.Background.Neutral.Subtle.Default), CircleShape)) {
                Box(Modifier.size(48.dp, 8.dp).background(Color(Zen.Color.Background.Active.Accent.Solid)))
            }
        }
    }
}

/** CSS `box-shadow: x y blur color` under a rounded box, via Paint.setShadowLayer (hardware-accelerated on API 28+). */
private fun DrawScope.cssShadow(dx: Float, dy: Float, blur: Float, color: Color, radius: Float) {
    drawIntoCanvas { cv ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(Css.shadowRadius(blur.dp.toPx()), dx.dp.toPx(), dy.dp.toPx(), android.graphics.Color.argb(color.alpha, color.red, color.green, color.blue))
        }
        val r = radius.dp.toPx()
        cv.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
    }
}

/** G03 bubble: “Hey · Sam · calling…” lit word by word; the glow is a static copy whose opacity animates. */
@Composable
private fun Bubble(c: Ctx) {
    // On-Accent text on the Accent pill (tokens); 18/24 −0.4 is v1.3.3's — Figma's bubble binds Body-Base 14/20
    val word = ts(18f, 24f, 600, Color(Zen.Color.Content.OnAccent.Default), -.4f)
    val glowTop = word.copy(shadow = Shadow(Color(A7Visual.BUBBLE_GLOW_TOP), Offset.Zero, Css.shadowRadius(10f)))
    val glowUnder = word.copy(shadow = Shadow(Color(A7Visual.BUBBLE_GLOW_UNDER), Offset.Zero, Css.shadowRadius(22f)))

    @Composable
    fun Word(id: String, text: String, modifier: Modifier = Modifier, lit: Boolean = true, chip: Boolean = false) {
        Box(modifier.anim(c, id)) {
            Text(text, style = word, maxLines = 1, softWrap = false)
            if (lit) Box(Modifier.anim(c, "${id}_g", leaf = true)) { // the text-shadow overflows the word
                Text(text, style = glowUnder, maxLines = 1, softWrap = false)
                Text(text, style = glowTop, maxLines = 1, softWrap = false)
            }
            if (chip) Box(
                Modifier.matchParentSize().outset(6f, 1f).anim(c, "chip").background(Color(A7Visual.BUBBLE_CHIP), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(text, style = word.copy(color = Accent), maxLines = 1, softWrap = false) }
        }
    }

    Box(Modifier.at(Scene.BUBBLE).anim(c, "g03_bubble"), contentAlignment = Alignment.Center) {
        Box(
            Modifier.graphicsLayer { rotationZ = 4f }
                .drawBehind { cssShadow(0f, 12f, 24f, Color(A7Visual.SHADOW), size.height / 2 / density) }
                .background(Accent, CircleShape)
                .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 8.dp),
        ) {
            Row {
                Word("bw_hey", "“Hey")
                Text(" ", style = word)
                Word("bw_sam", "Sam", Modifier.padding(start = 3.dp, end = 5.dp), lit = false, chip = true)
                Word("bw_call", " calling…”") // no comma after Sam (user 2026-09-25)
            }
        }
    }
}

/** CSS `inset: -v -h` — grow past the parent's box without changing its layout. */
private fun Modifier.outset(h: Float, v: Float) = layout { m, cons ->
    val dx = h.dp.roundToPx(); val dy = v.dp.roundToPx()
    val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(cons.maxWidth + 2 * dx, cons.maxHeight + 2 * dy))
    layout(cons.maxWidth, cons.maxHeight) { p.place(-dx, -dy) }
}

/** A7 / Progress Wave: 44 bars (played = accent, head = white + glow) and a "-0:SS" countdown, G01 → G04. */
@Composable
private fun Wave(c: Ctx) {
    val clock = c.store["clock"]
    val bars = remember { Scene.WAVE_BARS.map { c.store[it] } }
    val span = c.player.times.tG04 - c.player.times.tG01
    val left by remember(span) { derivedStateOf { c.tick.longValue; ((1 - clock[Prop.P]) * span).roundToInt().coerceAtLeast(0) } }
    val tc = c.store["wave_tc"]
    Box(Modifier.at(c.player.scene.wave).anim(c, "wave")) {
        Canvas(Modifier.at(DBox(0f, 0f, 282f, 28f))) {
            c.tick.longValue
            val played = (clock[Prop.P] * bars.size).roundToInt()
            val u = 1.dp.toPx()
            val step = (282f - 3f) / (bars.size - 1)
            bars.forEachIndexed { i, el ->
                val h = Scene.WAVE_H[i] * el.sy * u
                if (h <= 0f) return@forEachIndexed
                val x = i * step * u
                val top = 14f * u - h / 2
                val r = min(1.5f * u, h / 2)
                val col = when {
                    i == played - 1 -> Color(Zen.Color.Content.OnDarkOverlay.Strongest)
                    i < played - 1 -> Color(Zen.Color.Background.Active.Accent.Solid)
                    else -> Color(Zen.Color.Content.OnDarkOverlay.Disabled)
                }
                if (i == played - 1) drawIntoCanvas { cv ->
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = ACCENT; maskFilter = BlurMaskFilter(Css.shadowRadius(8f * u), BlurMaskFilter.Blur.NORMAL)
                    }
                    cv.nativeCanvas.drawRoundRect(x - u, top - u, x + 4 * u, top + h + u, r + u, r + u, p)
                }
                drawRoundRect(col, Offset(x, top), androidx.compose.ui.geometry.Size(3f * u, h), androidx.compose.ui.geometry.CornerRadius(r))
            }
        }
        Text(
            "-0:" + left.toString().padStart(2, '0'),
            Modifier.at(DBox(284f, 4f, 44f, 20f)).graphicsLayer {
                c.tick.longValue
                translationX = tc.x.dp.toPx(); alpha = tc.alpha
            },
            // Body-Base size / line and On-Dark-Overlay/Base; v1.3.3 sets no letter spacing (Figma: Body-Base −0.32)
            style = ts(ZenText.BodyBase.size, ZenText.BodyBase.line, 400, Color(Zen.Color.Content.OnDarkOverlay.Base)).copy(textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
            maxLines = 1, softWrap = false,
        )
    }
}

/** Native ad mock (A7 / Ad Slot). Two slots, two requests, two tints — the demo shows they are different ads. */
@Composable
private fun NativeAd(c: Ctx, id: String, angle: Float, tint: IntArray) {
    val t0 = Color(tint[0]); val t1 = Color(tint[1])
    val (x0, y0, x1, y1) = Css.linear(angle, 40f, 40f).toList()
    fun tint(w: Float, h: Float) = Css.linear(angle, w, h).let { g -> { px: Float -> Brush.linearGradient(listOf(t0, t1), Offset(g[0] * px, g[1] * px), Offset(g[2] * px, g[3] * px)) } }
    val media = tint(304f, 128f)
    Box(Modifier.at(Scene.NATIVE).anim(c, id).clip(RoundedCornerShape(Zen.CornerRadius.Large.dp)).background(Color(Zen.Color.Background.WhiteSolid.Default))) {
        Box(Modifier.at(DBox(12f, 12f, 40f, 40f)).clip(RoundedCornerShape(Zen.CornerRadius.Small.dp)).drawBehind {
            val px = size.width / 40f
            drawRect(Brush.linearGradient(listOf(t0, t1), Offset(x0 * px, y0 * px), Offset(x1 * px, y1 * px)))
        })
        Column(Modifier.at(DBox(64f, 14f, 252f, 36f))) {
            Text("Advertiser headline", style = ts(14f, 18f, 600, Color(Zen.Color.Content.OnWhiteOverlay.Strongest)))
            Text("Body text from the ad network, up to…", style = ts(12f, 16f, 400, Color(A7Visual.NATIVE_BODY)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Ad", Modifier.absoluteOffset(x = (328f - 12f).dp, y = 12.dp).layout { m, cons -> val p = m.measure(cons); layout(p.width, p.height) { p.place(-p.width, 0) } }
                .background(Color(A7Visual.NATIVE_BADGE), CircleShape).padding(horizontal = 6.dp),
            style = ts(11f, 16f, 500, Color(A7Visual.NATIVE_BADGE_TEXT)),
        )
        Box(Modifier.at(DBox(12f, 60f, 304f, 128f)).clip(RoundedCornerShape(8.dp)).drawBehind { drawRect(media(size.width / 304f)) })
        Box(Modifier.at(DBox(12f, 196f, 304f, 48f)).background(Color(A7Visual.NATIVE_INSTALL), CircleShape), contentAlignment = Alignment.Center) {
            Text("Install", style = ts(16f, 24f, 600, Color(A7Visual.NATIVE_INSTALL_TEXT)))
        }
        if (id == "nat2") Box(Modifier.fillMaxSize().anim(c, "nat2_skel").background(Color(Zen.Color.Background.WhiteSolid.Default))) {
            val skel = Color(A7Visual.NATIVE_SKELETON)
            listOf(
                DBox(12f, 12f, 40f, 40f) to 8f, DBox(64f, 16f, 150f, 12f) to 6f,
                DBox(12f, 60f, 304f, 128f) to 8f, DBox(12f, 196f, 304f, 48f) to 24f,
            ).forEachIndexed { i, (b, r) ->
                Box(Modifier.at(b).anim(c, "nat2_skel_$i").background(skel, RoundedCornerShape(r.dp)))
            }
        }
    }
}

/** Invisible hotspot over a sprite button (the web's `.hot`), with the web's press scale on [pressId]. */
@Composable
private fun Hot(c: Ctx, b: DBox, pressId: String? = null, enabled: () -> Boolean, onClick: () -> Unit) {
    Box(Modifier.at(b).pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                if (enabled() && pressId != null) {
                    c.player.press(pressId, true)
                    tryAwaitRelease()
                    c.player.press(pressId, false)
                }
            },
            onTap = { if (enabled()) onClick() },
        )
    })
}

/** Replay (mobile web's FAB). Long-press = next track — a review tool, not part of the onboarding. */
@Composable
private fun ReplayFab(c: Ctx, modifier: Modifier) {
    val ctx = LocalContext.current
    Box(
        modifier.padding(16.dp).size(44.dp).clip(CircleShape).background(Color(A7Visual.FAB)).border(1.dp, Color(A7Visual.FAB_RIM), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { c.player.replay() },
                    onLongPress = { Toast.makeText(ctx, "Nhạc: " + c.player.nextTrack(), Toast.LENGTH_SHORT).show() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Image(painterResource(R.drawable.ic_replay), "Replay") }
}
