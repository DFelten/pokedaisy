package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/**
 * VIEW MODERN's icons (the user's ask, 2026-11-19: "new modern icons"): our own drawings, smooth, on a 24-unit grid - a
 * glyph in one colour ([ModernIcon]: the nav rail, the header) or white on a gradient rounded square, like an app icon
 * ([ModernTile]: TOOLS, MORE). RETRO keeps the pixel drawings ([TabIcons], [ToolIcons]). No game's art or logo.
 */
enum class ModernGlyph(val top: Color, val low: Color) {
    GAMES(Color(0xFF6A9BF4), Color(0xFF3463D6)),
    TOOLS(Color(0xFFFFAE5C), Color(0xFFEE6A1E)),
    SETTINGS(Color(0xFF97A3BC), Color(0xFF5C6780)),
    PATCH(Color(0xFF5ED49A), Color(0xFF1F9E62)),
    LINK(Color(0xFFB08AF5), Color(0xFF7246D6)),
    EDIT(Color(0xFF5CCBF8), Color(0xFF1E7FE0)),
    TRADE(Color(0xFFFF9474), Color(0xFFE2471F)),
    PALPARK(Color(0xFF86D263), Color(0xFF3A9A2C)),
    TRANSFER(Color(0xFF52D6C8), Color(0xFF00897B)),
    COLLECTION(Color(0xFFF69BBE), Color(0xFFD5205F)),
    SPRITES(Color(0xFFFFD866), Color(0xFFF2A21C)),
    HOME(Color(0xFF7FB2FF), Color(0xFF3D6FE0)),
    PARTY(Color(0xFFFF7A7A), Color(0xFFD63838)),
    DEX(Color(0xFFFF9466), Color(0xFFDD4A26)),
    MAP(Color(0xFF66D2A4), Color(0xFF1C9A6A)),
    BAG(Color(0xFFFFC85C), Color(0xFFEE861A)),
    BOXES(Color(0xFF7AB0FF), Color(0xFF3B6CE0)),
    GUIDE(Color(0xFFA596FF), Color(0xFF5848DE)),
    NUZLOCKE(Color(0xFFA2AABB), Color(0xFF5E6676)),
    HUNT(Color(0xFFFFE07A), Color(0xFFE8A010)),
    CARD(Color(0xFF5AD8EC), Color(0xFF169ABD)),
    STATES(Color(0xFFCB96F4), Color(0xFF8A48D6)),
    ACHIEVEMENTS(Color(0xFFFFD866), Color(0xFFE09C14)),
    BATTLE(Color(0xFFFF8A6B), Color(0xFFD23A2A)),
    MORE(Color(0xFF97A3BC), Color(0xFF5C6780)),
    SORT(Color.Unspecified, Color.Unspecified),
    SEARCH(Color.Unspecified, Color.Unspecified),
    GRID(Color.Unspecified, Color.Unspecified),
    LIST(Color.Unspecified, Color.Unspecified),
    ADD(Color.Unspecified, Color.Unspecified),
    REFRESH(Color.Unspecified, Color.Unspecified),
    HELP(Color.Unspecified, Color.Unspecified),
    BACK(Color.Unspecified, Color.Unspecified),
    STAR(Color.Unspecified, Color.Unspecified),
    STAR_OUTLINE(Color.Unspecified, Color.Unspecified),
    SAVE(Color.Unspecified, Color.Unspecified),
    CHEVRON(Color.Unspecified, Color.Unspecified),
    CLOSE(Color.Unspecified, Color.Unspecified),
    CHECK(Color.Unspecified, Color.Unspecified),
    FILTER(Color.Unspecified, Color.Unspecified),
    ARROW_UP(Color.Unspecified, Color.Unspecified),
    INFO(Color.Unspecified, Color.Unspecified),
    ZOOM_IN(Color.Unspecified, Color.Unspecified),
    ZOOM_OUT(Color.Unspecified, Color.Unspecified),
    EXPAND(Color.Unspecified, Color.Unspecified),
    SHRINK(Color.Unspecified, Color.Unspecified),
    HEART(Color.Unspecified, Color.Unspecified),
    LOCK(Color.Unspecified, Color.Unspecified),
    CLOCK(Color.Unspecified, Color.Unspecified),
    CURSOR_LEFT(Color.Unspecified, Color.Unspecified),
    CURSOR_RIGHT(Color.Unspecified, Color.Unspecified),
    ROTATE(Color.Unspecified, Color.Unspecified),
    RESIZE(Color.Unspecified, Color.Unspecified);

    companion object {
        /** A companion tab's glyph (its id, `"ITEMS"`...), null for one without. */
        fun forTab(id: String): ModernGlyph? = when (id) {
            "HOME" -> HOME; "PARTY" -> PARTY; "DEX" -> DEX; "MAP" -> MAP; "ITEMS" -> BAG; "BOXES" -> BOXES; "GUIDE" -> GUIDE
            "NUZLOCKE" -> NUZLOCKE; "HUNT" -> HUNT; "CARD" -> CARD; "STATES" -> STATES; "ACHIEVEMENTS" -> ACHIEVEMENTS
            "BATTLE" -> BATTLE; "SETTINGS" -> SETTINGS; "MORE" -> MORE
            else -> null
        }

        /** The smooth twin of one of [PixelIcons]' one-colour drawings (by identity), drawn in its place in MODERN; null =
         * the pixel one stays. */
        fun forPixel(bitmap: List<String>): ModernGlyph? = PIXEL_TWINS.firstOrNull { it.first === bitmap }?.second

        // By identity: a drawing's twin, not any drawing with the same pixels.
        private val PIXEL_TWINS: List<Pair<List<String>, ModernGlyph>> by lazy {
            buildList<Pair<List<String>, ModernGlyph>> {
                fun put(k: List<String>, v: ModernGlyph) = add(k to v)
                put(PixelIcons.gear, SETTINGS); put(PixelIcons.more, MORE); put(PixelIcons.home, HOME); put(PixelIcons.arrowUp, ARROW_UP)
                put(PixelIcons.back, BACK); put(PixelIcons.arrowBack, BACK); put(PixelIcons.expand, EXPAND)
                put(PixelIcons.shrink, SHRINK); put(PixelIcons.filter, FILTER); put(PixelIcons.star, STAR); put(PixelIcons.starOutline, STAR_OUTLINE)
                put(PixelIcons.clock, CLOCK); put(PixelIcons.close, CLOSE); put(PixelIcons.check, CHECK)
                put(PixelIcons.tick, CHECK); put(PixelIcons.trophy, ACHIEVEMENTS); put(PixelIcons.chevronRight, CHEVRON)
                put(PixelIcons.info, INFO); put(PixelIcons.search, SEARCH); put(PixelIcons.zoomIn, ZOOM_IN)
                put(PixelIcons.zoomOut, ZOOM_OUT); put(PixelIcons.heart, HEART); put(PixelIcons.lockClosed, LOCK)
                put(PixelIcons.cursorLeft, CURSOR_LEFT); put(PixelIcons.cursorRight, CURSOR_RIGHT); put(PixelIcons.rotate, ROTATE)
                put(PixelIcons.resizeVertical, RESIZE)
            }
        }

        /** A library tool's glyph ([LibraryTool.id]). */
        fun forTool(id: String): ModernGlyph? = when (id) {
            "PATCH" -> PATCH; "LINK" -> LINK; "EDIT" -> EDIT; "TRADE" -> TRADE; "PALPARK" -> PALPARK
            "TRANSFER" -> TRANSFER; "COLLECTION" -> COLLECTION; "SPRITES" -> SPRITES
            else -> null
        }
    }
}

/** [glyph] drawn in [tint], filling the modifier's square. */
@Composable
fun ModernIcon(glyph: ModernGlyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawGlyph(glyph, tint) }
}

/** [glyph] white on its gradient rounded square (TOOLS' cards, MORE's tiles). */
@Composable
fun ModernTile(glyph: ModernGlyph, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.28f
            // Its shadow, its face top to bottom, a light rim along the top.
            drawRoundRect(Color(0x261B2A55), Offset(0f, size.height * 0.04f), size, CornerRadius(r))
            drawRoundRect(Brush.verticalGradient(listOf(glyph.top, glyph.low)), size = Size(size.width, size.height * 0.98f), cornerRadius = CornerRadius(r))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)), endY = size.height * 0.5f),
                size = Size(size.width, size.height * 0.5f), cornerRadius = CornerRadius(r),
            )
            val inset = size.minDimension * 0.2f
            withTransform({ translate(inset, inset); scale((size.width - 2 * inset) / size.width, (size.height - 2 * inset) / size.height, Offset.Zero) }) {
                drawGlyph(glyph, Color.White)
            }
        }
    }
}

/** The glyphs, each on a 24-unit square ([s] a unit), strokes 2 units, round ends. */
fun DrawScope.drawGlyph(glyph: ModernGlyph, c: Color) {
    val s = size.minDimension / 24f
    val ox = (size.width - 24 * s) / 2f
    val oy = (size.height - 24 * s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 2f) =
        drawLine(c, p(x0, y0), p(x1, y1), strokeWidth = w * s, cap = StrokeCap.Round)
    fun stroke(w: Float = 2f) = Stroke(w * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = true, sw: Float = 2f, color: Color = c) =
        if (fill) drawRoundRect(color, p(x, y), Size(w * s, h * s), CornerRadius(r * s))
        else drawRoundRect(color, p(x, y), Size(w * s, h * s), CornerRadius(r * s), style = stroke(sw))
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = true, sw: Float = 2f, color: Color = c) =
        if (fill) drawCircle(color, r * s, p(x, y)) else drawCircle(color, r * s, p(x, y), style = stroke(sw))
    fun path(fill: Boolean = false, sw: Float = 2f, build: Path.() -> Unit) {
        val path = Path().apply(build)
        if (fill) drawPath(path, c) else drawPath(path, c, style = stroke(sw))
    }
    fun Path.m(x: Float, y: Float) = moveTo(ox + x * s, oy + y * s)
    fun Path.l(x: Float, y: Float) = lineTo(ox + x * s, oy + y * s)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticBezierTo(ox + cx * s, oy + cy * s, ox + x * s, oy + y * s)
    // Cut-outs in a filled shape: the background's clear, so draw them with BlendMode.Clear's look - here a hole in the
    // glyph's own colour can't be clear on a tile, so holes are drawn as outlines instead.
    when (glyph) {
        ModernGlyph.GAMES -> {
            // A handheld: body, screen, D-pad, two buttons.
            rrect(5f, 2f, 14f, 20f, 3f, fill = false)
            rrect(8f, 5f, 8f, 6.5f, 1.2f)
            line(9.5f, 16f, 9.5f, 19f, 1.8f); line(8f, 17.5f, 11f, 17.5f, 1.8f)
            circle(14.5f, 18.4f, 1.1f); circle(16f, 16.2f, 1.1f)
        }
        ModernGlyph.TOOLS -> {
            // A toolbox: handle, lid line, latch.
            path { m(9f, 7f); l(9f, 5f); q(9f, 4f, 10f, 4f); l(14f, 4f); q(15f, 4f, 15f, 5f); l(15f, 7f) }
            rrect(3f, 7f, 18f, 13f, 2.5f, fill = false)
            line(3.5f, 12f, 20.5f, 12f)
            rrect(10.5f, 10.5f, 3f, 3.5f, 0.8f)
        }
        ModernGlyph.SETTINGS, ModernGlyph.MORE -> if (glyph == ModernGlyph.MORE) {
            for (i in 0 until 3) for (j in 0 until 3) circle(6f + i * 6f, 6f + j * 6f, 1.9f)
        } else {
            // A gear: eight teeth round a ring, a hole in the middle.
            for (i in 0 until 8) rotate(i * 45f, p(12f, 12f)) { rrect(10.4f, 2.2f, 3.2f, 5f, 1f) }
            circle(12f, 12f, 6.4f, fill = false, sw = 3.2f)
            circle(12f, 12f, 1.7f)
        }
        ModernGlyph.PATCH -> {
            // A bandage across, its dotted pad.
            rotate(-40f, p(12f, 12f)) {
                rrect(2.5f, 8f, 19f, 8f, 4f, fill = false)
                line(9f, 8.5f, 9f, 15.5f, 1.6f); line(15f, 8.5f, 15f, 15.5f, 1.6f)
                circle(11f, 11f, 0.8f); circle(13f, 13f, 0.8f); circle(13f, 11f, 0.8f); circle(11f, 13f, 0.8f)
            }
        }
        ModernGlyph.LINK -> {
            // Two chain links.
            rotate(-45f, p(12f, 12f)) {
                rrect(2.5f, 8.5f, 11f, 7f, 3.5f, fill = false)
                rrect(10.5f, 8.5f, 11f, 7f, 3.5f, fill = false)
            }
        }
        ModernGlyph.EDIT -> {
            // A save card and a pencil over its corner.
            path { m(4f, 4f); l(15f, 4f); l(19f, 8f); l(19f, 11f) }
            path { m(10f, 20f); l(5f, 20f); q(4f, 20f, 4f, 19f); l(4f, 5f); q(4f, 4f, 5f, 4f) }
            rrect(7f, 4f, 7f, 4.5f, 0.8f)
            path { m(13f, 21f); l(13.6f, 18.2f); l(19.5f, 12.3f); l(21.7f, 14.5f); l(15.8f, 20.4f); l(13f, 21f) }
        }
        ModernGlyph.TRADE -> {
            // Two arrows the other way round.
            line(4f, 8f, 19f, 8f); path { m(15f, 4f); l(19f, 8f); l(15f, 12f) }
            line(20f, 16f, 5f, 16f); path { m(9f, 12f); l(5f, 16f); l(9f, 20f) }
        }
        ModernGlyph.PALPARK -> {
            // A tree on the grass.
            circle(12f, 9f, 6f)
            circle(8f, 11.5f, 3.6f); circle(16f, 11.5f, 3.6f)
            line(12f, 13f, 12f, 20f, 2.4f)
            line(4f, 20.5f, 20f, 20.5f)
        }
        ModernGlyph.TRANSFER -> {
            // An arrow up out of a tray.
            line(12f, 15f, 12f, 3.5f); path { m(7.5f, 8f); l(12f, 3.5f); l(16.5f, 8f) }
            path { m(4f, 13f); l(4f, 19f); q(4f, 20.5f, 5.5f, 20.5f); l(18.5f, 20.5f); q(20f, 20.5f, 20f, 19f); l(20f, 13f) }
        }
        ModernGlyph.COLLECTION -> {
            // Four balls in a grid.
            for ((x, y) in listOf(7f to 7f, 17f to 7f, 7f to 17f, 17f to 17f)) {
                circle(x, y, 4.2f, fill = false, sw = 1.8f); line(x - 4f, y, x + 4f, y, 1.6f); circle(x, y, 1.2f)
            }
        }
        ModernGlyph.SPRITES -> {
            // A shirt: the look a player wears.
            path(fill = true) {
                m(8.5f, 3.5f); q(12f, 6.5f, 15.5f, 3.5f); l(21f, 7f); l(18.5f, 11f); l(17f, 10.2f); l(17f, 20.5f)
                l(7f, 20.5f); l(7f, 10.2f); l(5.5f, 11f); l(3f, 7f); close()
            }
        }
        ModernGlyph.PARTY -> {
            // A capsule ball: its band and button.
            circle(12f, 12f, 8.5f, fill = false)
            line(3.8f, 12f, 8.6f, 12f); line(15.4f, 12f, 20.2f, 12f)
            circle(12f, 12f, 3f, fill = false, sw = 2f)
            drawArc(c, 180f, 180f, true, p(3.5f, 3.5f), Size(17f * s, 17f * s), alpha = 0.35f)
        }
        ModernGlyph.DEX -> {
            // A handheld dex: lid, lens, screen.
            rrect(4f, 3f, 16f, 18f, 3f, fill = false)
            circle(8.5f, 7.5f, 2f)
            circle(12.5f, 7f, 0.9f); circle(15f, 7f, 0.9f)
            rrect(7f, 11f, 10f, 6.5f, 1.2f, fill = false, sw = 1.6f)
        }
        ModernGlyph.HOME -> {
            // A house: its roof, walls, a door.
            path { m(3f, 11f); l(12f, 3.5f); l(21f, 11f) }
            path { m(5.5f, 9.5f); l(5.5f, 19f); q(5.5f, 20.5f, 7f, 20.5f); l(17f, 20.5f); q(18.5f, 20.5f, 18.5f, 19f); l(18.5f, 9.5f) }
            rrect(10f, 14f, 4f, 6.5f, 1f)
        }
        ModernGlyph.MAP -> {
            // A folded map with a pin.
            path { m(3f, 6f); l(9f, 4f); l(15f, 6f); l(21f, 4f); l(21f, 18f); l(15f, 20f); l(9f, 18f); l(3f, 20f); close() }
            line(9f, 4.5f, 9f, 17.5f, 1.6f); line(15f, 6.5f, 15f, 19.5f, 1.6f)
        }
        ModernGlyph.BAG -> {
            // A backpack: its flap, a pocket.
            path { m(9f, 6f); l(9f, 4.5f); q(9f, 3f, 10.5f, 3f); l(13.5f, 3f); q(15f, 3f, 15f, 4.5f); l(15f, 6f) }
            rrect(4.5f, 6f, 15f, 15f, 4f, fill = false)
            rrect(8f, 13f, 8f, 5f, 1.4f, fill = false, sw = 1.6f)
            line(5f, 11f, 19f, 11f, 1.6f)
        }
        ModernGlyph.BOXES -> {
            // A storage box, its lid and label.
            rrect(3f, 4f, 18f, 5f, 1.4f, fill = false)
            path { m(5f, 9f); l(5f, 19f); q(5f, 20.5f, 6.5f, 20.5f); l(17.5f, 20.5f); q(19f, 20.5f, 19f, 19f); l(19f, 9f) }
            line(10f, 13f, 14f, 13f)
        }
        ModernGlyph.GUIDE -> {
            // A compass.
            circle(12f, 12f, 9f, fill = false)
            path(fill = true) { m(15.8f, 8.2f); l(13.4f, 13.4f); l(8.2f, 15.8f); l(10.6f, 10.6f); close() }
        }
        ModernGlyph.NUZLOCKE -> {
            // A gravestone with a cross.
            path { m(6f, 21f); l(6f, 9f); q(6f, 3f, 12f, 3f); q(18f, 3f, 18f, 9f); l(18f, 21f); close() }
            line(12f, 7.5f, 12f, 15f, 1.8f); line(9.2f, 10f, 14.8f, 10f, 1.8f)
            line(3f, 21f, 21f, 21f)
        }
        ModernGlyph.CARD -> {
            // A trainer card: photo and lines.
            rrect(2.5f, 5f, 19f, 14f, 2.5f, fill = false)
            circle(8f, 10.5f, 2f); path(fill = true) { m(4.8f, 16.2f); q(8f, 11.5f, 11.2f, 16.2f); close() }
            line(13.5f, 10f, 18.5f, 10f, 1.6f); line(13.5f, 14f, 17f, 14f, 1.6f)
        }
        ModernGlyph.STATES -> {
            // A bookmark over a clock face: a saved moment.
            circle(12f, 12.5f, 8.5f, fill = false)
            line(12f, 12.5f, 12f, 8f); line(12f, 12.5f, 15f, 14.5f)
            line(9f, 2.5f, 15f, 2.5f)
        }
        ModernGlyph.ACHIEVEMENTS -> {
            // A trophy.
            path { m(7f, 4f); l(17f, 4f); l(17f, 9f); q(17f, 14f, 12f, 14f); q(7f, 14f, 7f, 9f); close() }
            path { m(7f, 6f); l(4f, 6f); q(4f, 10f, 7.5f, 11f) }
            path { m(17f, 6f); l(20f, 6f); q(20f, 10f, 16.5f, 11f) }
            line(12f, 14f, 12f, 18f); rrect(8f, 18f, 8f, 3f, 1f)
        }
        ModernGlyph.HUNT -> {
            // Sparkles: a shiny's shine.
            path(fill = true) { m(10f, 2f); q(10.8f, 8.2f, 17f, 9f); q(10.8f, 9.8f, 10f, 16f); q(9.2f, 9.8f, 3f, 9f); q(9.2f, 8.2f, 10f, 2f); close() }
            path(fill = true) { m(18f, 13f); q(18.4f, 16.6f, 22f, 17f); q(18.4f, 17.4f, 18f, 21f); q(17.6f, 17.4f, 14f, 17f); q(17.6f, 16.6f, 18f, 13f); close() }
        }
        ModernGlyph.BATTLE -> {
            // A spark: a clash.
            path(fill = true) { m(13.5f, 2f); l(5f, 13.5f); l(11f, 13.5f); l(9.5f, 22f); l(19f, 9.5f); l(13f, 9.5f); close() }
        }
        ModernGlyph.SORT -> { line(4f, 7f, 20f, 7f); line(4f, 12f, 15f, 12f); line(4f, 17f, 10f, 17f) }
        ModernGlyph.SEARCH -> { circle(10.5f, 10.5f, 6.5f, fill = false, sw = 2.4f); line(15.5f, 15.5f, 20.5f, 20.5f, 2.6f) }
        ModernGlyph.GRID -> for ((x, y) in listOf(4f to 4f, 13.5f to 4f, 4f to 13.5f, 13.5f to 13.5f)) rrect(x, y, 6.5f, 6.5f, 1.8f)
        ModernGlyph.LIST -> for (i in 0 until 3) { val y = 6f + i * 6f; circle(5f, y, 1.5f); line(9f, y, 20f, y) }
        ModernGlyph.ADD -> { line(12f, 5f, 12f, 19f, 2.6f); line(5f, 12f, 19f, 12f, 2.6f) }
        ModernGlyph.REFRESH -> {
            drawArc(c, -60f, 290f, false, p(4.5f, 4.5f), Size(15f * s, 15f * s), style = stroke(2.4f))
            val a = (-60.0 * kotlin.math.PI / 180.0)
            val end = p(12f + 7.5f * cos(a).toFloat(), 12f + 7.5f * sin(a).toFloat())
            drawPath(Path().apply {
                moveTo(end.x + 3.6f * s, end.y - 1.6f * s); lineTo(end.x, end.y); lineTo(end.x - 0.6f * s, end.y - 4f * s)
            }, c, style = stroke(2.4f))
        }
        ModernGlyph.HELP -> {
            circle(12f, 12f, 9f, fill = false)
            path { m(9.2f, 9.5f); q(9.2f, 6.6f, 12f, 6.6f); q(14.8f, 6.6f, 14.8f, 9.2f); q(14.8f, 11f, 12f, 12.4f); l(12f, 13.6f) }
            circle(12f, 17f, 1.3f)
        }
        ModernGlyph.BACK -> { line(19f, 12f, 5.5f, 12f, 2.4f); path(sw = 2.4f) { m(11f, 6f); l(5f, 12f); l(11f, 18f) } }
        ModernGlyph.STAR, ModernGlyph.STAR_OUTLINE -> path(fill = glyph == ModernGlyph.STAR, sw = 2f) {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 10f else 4.2f
                val a = ((-90.0 + i * 36.0) * kotlin.math.PI / 180.0)
                val x = 12f + r * cos(a).toFloat()
                val y = 12.6f + r * sin(a).toFloat()
                if (i == 0) m(x, y) else l(x, y)
            }
            close()
        }
        ModernGlyph.CHEVRON -> path(sw = 2.6f) { m(9f, 5f); l(16f, 12f); l(9f, 19f) }
        ModernGlyph.CLOSE -> { line(6f, 6f, 18f, 18f, 2.6f); line(18f, 6f, 6f, 18f, 2.6f) }
        ModernGlyph.CHECK -> path(sw = 2.8f) { m(4.5f, 12.5f); l(9.5f, 17.5f); l(19.5f, 6.5f) }
        ModernGlyph.FILTER -> path(fill = true) { m(3f, 5f); l(21f, 5f); l(14f, 13f); l(14f, 19.5f); l(10f, 21f); l(10f, 13f); close() }
        ModernGlyph.ARROW_UP -> { line(12f, 20f, 12f, 5f, 2.4f); path(sw = 2.4f) { m(6f, 11f); l(12f, 5f); l(18f, 11f) } }
        ModernGlyph.INFO -> { circle(12f, 12f, 9f, fill = false); line(12f, 11f, 12f, 17f, 2.4f); circle(12f, 7.4f, 1.4f) }
        ModernGlyph.ZOOM_IN, ModernGlyph.ZOOM_OUT -> {
            circle(10.5f, 10.5f, 6.5f, fill = false, sw = 2.2f); line(15.5f, 15.5f, 20.5f, 20.5f, 2.6f)
            line(7.5f, 10.5f, 13.5f, 10.5f, 2f); if (glyph == ModernGlyph.ZOOM_IN) line(10.5f, 7.5f, 10.5f, 13.5f, 2f)
        }
        ModernGlyph.EXPAND -> { path { m(4f, 10f); l(4f, 4f); l(10f, 4f) }; path { m(14f, 20f); l(20f, 20f); l(20f, 14f) }; line(4.5f, 4.5f, 10f, 10f); line(19.5f, 19.5f, 14f, 14f) }
        ModernGlyph.SHRINK -> { path { m(10f, 4f); l(10f, 10f); l(4f, 10f) }; path { m(14f, 20f); l(14f, 14f); l(20f, 14f) }; line(4.5f, 4.5f, 10f, 10f); line(19.5f, 19.5f, 14f, 14f) }
        ModernGlyph.HEART -> path(fill = true) {
            m(12f, 20.5f); q(3f, 14.5f, 3f, 9f); q(3f, 4.5f, 7.5f, 4.5f); q(10.5f, 4.5f, 12f, 7.5f)
            q(13.5f, 4.5f, 16.5f, 4.5f); q(21f, 4.5f, 21f, 9f); q(21f, 14.5f, 12f, 20.5f); close()
        }
        ModernGlyph.LOCK -> { rrect(5f, 10.5f, 14f, 10f, 2.5f); path { m(8f, 10.5f); l(8f, 7.5f); q(8f, 3.5f, 12f, 3.5f); q(16f, 3.5f, 16f, 7.5f); l(16f, 10.5f) } }
        ModernGlyph.CLOCK -> { circle(12f, 12f, 9f, fill = false); line(12f, 12f, 12f, 7f); line(12f, 12f, 15.5f, 14f) }
        ModernGlyph.CURSOR_LEFT -> path(fill = true) { m(17f, 4f); l(17f, 20f); l(6f, 12f); close() }
        ModernGlyph.CURSOR_RIGHT -> path(fill = true) { m(7f, 4f); l(7f, 20f); l(18f, 12f); close() }
        ModernGlyph.ROTATE -> {
            drawArc(c, 200f, 260f, false, p(4.5f, 4.5f), Size(15f * s, 15f * s), style = stroke(2.4f))
            path(sw = 2.4f) { m(3.5f, 5f); l(4.6f, 9.6f); l(9.2f, 8.4f) }
        }
        ModernGlyph.RESIZE -> {
            line(12f, 3.5f, 12f, 20.5f, 2.2f); path(sw = 2.2f) { m(8f, 7.5f); l(12f, 3.5f); l(16f, 7.5f) }; path(sw = 2.2f) { m(8f, 16.5f); l(12f, 20.5f); l(16f, 16.5f) }
            line(4f, 12f, 20f, 12f, 1.6f)
        }
        ModernGlyph.SAVE -> {
            path { m(4f, 5.5f); q(4f, 4f, 5.5f, 4f); l(16f, 4f); l(20f, 8f); l(20f, 18.5f); q(20f, 20f, 18.5f, 20f); l(5.5f, 20f); q(4f, 20f, 4f, 18.5f); close() }
            rrect(7.5f, 4f, 7.5f, 5f, 0.8f)
            circle(12f, 14.5f, 2.6f)
        }
    }
}

/** The glyphs' bounds on their 24-unit grid, for laying one out beside text. */
val GLYPH_GRID = Rect(0f, 0f, 24f, 24f)

/** VIEW MODERN's capsule ball in the pixel one's colours: [top] over white, a band and a button in [ink]. */
fun DrawScope.drawModernBall(top: Color?, low: Color?, ink: Color?) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val k = ink ?: Color(0xFF202020)
    drawCircle(k, r, c)
    val inner = r * 0.86f
    drawArc(top ?: Color(0xFFE83838), 180f, 180f, true, Offset(c.x - inner, c.y - inner), Size(inner * 2, inner * 2))
    drawArc(low ?: Color.White, 0f, 180f, true, Offset(c.x - inner, c.y - inner), Size(inner * 2, inner * 2))
    drawRect(k, Offset(c.x - r, c.y - r * 0.08f), Size(2 * r, r * 0.16f))
    drawCircle(k, r * 0.32f, c)
    drawCircle(low ?: Color.White, r * 0.2f, c)
}

/** Our own simple smooth twins of the sites' marks (no logo artwork): a cup on a saucer with steam. */
fun DrawScope.drawCoffeeMark(ink: Color) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset((size.width - 24 * u) / 2 + x * u, (size.height - 24 * u) / 2 + y * u)
    drawRoundRect(Color(0xFFFFDD00), o(5f, 9f), Size(11f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u))
    drawRoundRect(ink, o(5f, 9f), Size(11f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = Stroke(1.6f * u))
    drawArc(ink, -90f, 180f, false, o(14f, 11f), Size(5f * u, 5f * u), style = Stroke(1.6f * u))
    drawLine(ink, o(3.5f, 21f), o(19.5f, 21f), 1.8f * u, StrokeCap.Round)
    drawLine(Color(0xFF909090), o(8.5f, 3.5f), o(8.5f, 6.5f), 1.4f * u, StrokeCap.Round)
    drawLine(Color(0xFF909090), o(12.5f, 3.5f), o(12.5f, 6.5f), 1.4f * u, StrokeCap.Round)
}

/** A mug with a heart. */
fun DrawScope.drawKofiMark(ink: Color) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset((size.width - 24 * u) / 2 + x * u, (size.height - 24 * u) / 2 + y * u)
    drawRoundRect(Color.White, o(3f, 6f), Size(14f * u, 13f * u), androidx.compose.ui.geometry.CornerRadius(3f * u))
    drawRoundRect(ink, o(3f, 6f), Size(14f * u, 13f * u), androidx.compose.ui.geometry.CornerRadius(3f * u), style = Stroke(1.6f * u))
    drawArc(ink, -90f, 180f, false, o(15f, 8.5f), Size(6f * u, 6.5f * u), style = Stroke(1.6f * u))
    val heart = Path().apply {
        moveTo(o(10f, 16.5f).x, o(10f, 16.5f).y)
        cubicTo(o(5f, 13f).x, o(5f, 13f).y, o(6f, 8.5f).x, o(6f, 8.5f).y, o(10f, 10.5f).x, o(10f, 10.5f).y)
        cubicTo(o(14f, 8.5f).x, o(14f, 8.5f).y, o(15f, 13f).x, o(15f, 13f).y, o(10f, 16.5f).x, o(10f, 16.5f).y)
        close()
    }
    drawPath(heart, Color(0xFFFF5E5B))
}

/** A ring and a bar, in the site's two colours. */
fun DrawScope.drawPatreonMark(ink: Color) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset((size.width - 24 * u) / 2 + x * u, (size.height - 24 * u) / 2 + y * u)
    drawCircle(Color(0xFFFF424D), 6.5f * u, o(14f, 9.5f))
    drawRoundRect(ink, o(3f, 3f), Size(3.5f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(1f * u))
}

/** A round cat face: ears and a tail, in one colour. */
fun DrawScope.drawGitHubMark(ink: Color) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset((size.width - 24 * u) / 2 + x * u, (size.height - 24 * u) / 2 + y * u)
    drawCircle(ink, 10f * u, o(12f, 12f))
    val face = Color.White.takeIf { ink.luminance() < 0.5f } ?: Color(0xFF202020)
    val head = Path().apply {
        moveTo(o(7f, 18f).x, o(7f, 18f).y)
        lineTo(o(7f, 10f).x, o(7f, 10f).y); lineTo(o(7.5f, 6f).x, o(7.5f, 6f).y); lineTo(o(10f, 7.8f).x, o(10f, 7.8f).y)
        lineTo(o(14f, 7.8f).x, o(14f, 7.8f).y); lineTo(o(16.5f, 6f).x, o(16.5f, 6f).y); lineTo(o(17f, 10f).x, o(17f, 10f).y)
        lineTo(o(17f, 14f).x, o(17f, 14f).y); quadraticBezierTo(o(17f, 16.5f).x, o(17f, 16.5f).y, o(14.5f, 17f).x, o(14.5f, 17f).y)
        lineTo(o(14.5f, 21.5f).x, o(14.5f, 21.5f).y); lineTo(o(9.5f, 21.5f).x, o(9.5f, 21.5f).y); lineTo(o(9.5f, 17f).x, o(9.5f, 17f).y)
        quadraticBezierTo(o(7f, 16.5f).x, o(7f, 16.5f).y, o(7f, 14f).x, o(7f, 14f).y); close()
    }
    drawPath(head, face)
}

/** A rounded game-pad face with two eyes. */
fun DrawScope.drawDiscordMark(ink: Color) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset((size.width - 24 * u) / 2 + x * u, (size.height - 24 * u) / 2 + y * u)
    val body = Path().apply {
        moveTo(o(5f, 6.5f).x, o(5f, 6.5f).y)
        quadraticBezierTo(o(8f, 5f).x, o(8f, 5f).y, o(9.5f, 5.2f).x, o(9.5f, 5.2f).y)
        lineTo(o(10.2f, 6.6f).x, o(10.2f, 6.6f).y); lineTo(o(13.8f, 6.6f).x, o(13.8f, 6.6f).y)
        lineTo(o(14.5f, 5.2f).x, o(14.5f, 5.2f).y)
        quadraticBezierTo(o(16f, 5f).x, o(16f, 5f).y, o(19f, 6.5f).x, o(19f, 6.5f).y)
        quadraticBezierTo(o(21.5f, 11f).x, o(21.5f, 11f).y, o(21f, 16.5f).x, o(21f, 16.5f).y)
        quadraticBezierTo(o(18.5f, 18.5f).x, o(18.5f, 18.5f).y, o(16f, 19f).x, o(16f, 19f).y)
        lineTo(o(15f, 17.3f).x, o(15f, 17.3f).y)
        quadraticBezierTo(o(12f, 18f).x, o(12f, 18f).y, o(9f, 17.3f).x, o(9f, 17.3f).y)
        lineTo(o(8f, 19f).x, o(8f, 19f).y)
        quadraticBezierTo(o(5.5f, 18.5f).x, o(5.5f, 18.5f).y, o(3f, 16.5f).x, o(3f, 16.5f).y)
        quadraticBezierTo(o(2.5f, 11f).x, o(2.5f, 11f).y, o(5f, 6.5f).x, o(5f, 6.5f).y)
        close()
    }
    drawPath(body, ink)
    drawOval(Color.White, o(7.6f, 10.5f), Size(3f * u, 3.4f * u))
    drawOval(Color.White, o(13.4f, 10.5f), Size(3f * u, 3.4f * u))
}

/** The PokeDaisy flower: the pixel one ([LOGO_ROWS]) in RETRO, its smooth twin ([drawModernLogo]) in MODERN. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    if (modernView) Canvas(modifier) { drawModernLogo() } else PixelArt(LOGO_ROWS, LOGO_PALETTE, modifier)
}

/**
 * The flower drawn smooth (our own, after the pixel logo): eight round petals, the top ones red and the bottom ones white,
 * split by a dark band across the middle, a yellow ring with a white button in the centre, a dark outline round it all.
 */
fun DrawScope.drawModernLogo() {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val ink = Color(0xFF26222A)
    val red = Color(0xFFE8382C)
    val redLow = Color(0xFFB8221A)
    val white = Color(0xFFFFFFFF)
    val whiteLow = Color(0xFFC9CDD8)
    // Each petal runs from the centre out and keeps its own outline all the way in (the pixel logo's dark lines between
    // the petals, meeting under the centre).
    val inner = s * 0.03f
    val petalLen = s * 0.40f
    val petalW = s * 0.22f
    val out = s * 0.04f
    fun petal(angle: Float, fillTop: Color, fillLow: Color, half: Boolean? = null) {
        rotate(angle, c) {
            val rect = Rect(c.x + inner, c.y - petalW / 2f, c.x + inner + petalLen, c.y + petalW / 2f)
            fun fill() = drawOval(Brush.linearGradient(listOf(fillTop, fillLow), start = rect.topLeft, end = rect.bottomRight), rect.topLeft, rect.size)
            fill()
            drawOval(ink, rect.topLeft, rect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(out))
        }
    }
    val angles = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    // A dark silhouette under them all, so the flower has an outer outline too.
    for (a in angles) rotate(a, c) {
        drawOval(ink, Offset(c.x + inner - out, c.y - petalW / 2f - out), Size(petalLen + 2 * out, petalW + 2 * out))
    }
    // Canvas angles: 0 right, 90 down. The upper half red, the lower white; the two side petals split by the band.
    // Diagonals first, then the straight ones over them, as the pixel logo layers them.
    for (a in listOf(45f, 135f, 225f, 315f, 90f, 270f)) {
        if (a > 180f) petal(a, red, redLow) else petal(a, white, whiteLow)
    }
    for (a in listOf(0f, 180f)) {
        clipRect(bottom = c.y) { petal(a, red, redLow) }
        clipRect(top = c.y) { petal(a, white, whiteLow) }
    }
    // The band across the middle.
    drawRect(ink, Offset(c.x - s * 0.47f, c.y - s * 0.022f), Size(s * 0.94f, s * 0.044f))
    // The centre: a yellow ring, a white button.
    drawCircle(ink, s * 0.2f, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF0A0), Color(0xFFF2C21C)), c, s * 0.18f), s * 0.17f, c)
    drawCircle(ink, s * 0.095f, c)
    drawCircle(white, s * 0.065f, c)
}
