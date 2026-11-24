package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.HomeCard
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where everything on the HOME tab goes (docs/plans/companion-home.md), in GBA pixels of the tab's own room: pure, so
 * `HomeLayoutTest` can check every companion size. The shape follows the room, never a device:
 * - [Shape.COMPACT]: a short companion (the RG DS, a phone upright): the party a column of six, the map beside it, small
 *   cards one line each, the shortcuts icons alone.
 * - [Shape.STANDARD]: the Thor, the Duo: the party 2 x 3 on the left, the map top right, small cards under it.
 * - [Shape.WIDE]: past [WIDE_ASPECT] (SWAP SCREENS' top screen): party, map, a column of small cards.
 * - [Shape.TALL]: taller than wide (the side panel, a phone's companion pulled up): the map across the top, the party
 *   under it, the small cards two to a row.
 * Small cards follow the player's order, each at the biggest form that fits ([Form.BOX], else [Form.LINE]); what doesn't
 * fit this screen is [HomeLayout.dropped] - left out, never cut. Nothing scrolls.
 */
object HomeLayouts {
    enum class Shape { COMPACT, STANDARD, WIDE, TALL }
    enum class Form { BOX, LINE }

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right get() = x + w
        val bottom get() = y + h
        fun overlaps(o: Box) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
    }

    data class Placed(val card: HomeCard, val box: Box, val form: Form)

    data class HomeLayout(
        val shape: Shape,
        val party: Box?,
        /** The party's grid: columns x rows of slots, and whether a slot is the small one (icon, level, HP; no name). */
        val partyColumns: Int,
        val partyMini: Boolean,
        val map: Box?,
        val cards: List<Placed>,
        val shortcuts: Box?,
        /** How many of the shortcuts fit, whether their names show, and each tile's width (the same however many there are). */
        val shortcutCount: Int,
        val shortcutLabels: Boolean,
        val dropped: List<HomeCard>,
        val shortcutTileW: Float = 0f,
    ) {
        /** Every placed box, for the checks. */
        val boxes: List<Box> get() = listOfNotNull(party, map, shortcuts) + cards.map { it.box }
    }

    /**
     * The text's sizes in GBA pixels (the font follows FONT SIZE, so the layout asks): [line] the main text's line,
     * [small] the secondary text's.
     */
    data class Text(val line: Float, val small: Float)

    /** Room between two things, and round the whole. */
    const val GAP = 4f
    /** A window's frame and padding, each side (OptionListWindow's, a little over). */
    const val FRAME = 6f
    /** Past this width / height the room is WIDE (the Duo's 4:3 companion, ~1.6, stays STANDARD). */
    const val WIDE_ASPECT = 1.7f
    /** ...and at least this wide: three columns need it. */
    const val WIDE_MIN_W = 520f
    /** Under this tall (the tab's room) a companion that isn't short still gets COMPACT. */
    const val COMPACT_MAX_H = 230f
    /** A party icon (the games' 32 x 32 sprite frame, its picture about 24 across). */
    const val ICON = 24f
    /** A named slot's narrowest: the icon and a ten-letter name. */
    const val SLOT_MIN_W = 84f
    /** A small slot's lowest (one row: icon, level, HP bar): the icon may spill past it, like the game's. */
    const val MINI_SLOT_MIN_H = 16f
    /** The map's smallest; under it the map gives way to nothing, the small cards do. */
    const val MAP_MIN_W = 64f
    const val MAP_MIN_H = 48f
    /** A small card's narrowest as a box (two of them side by side need twice this). */
    const val CARD_MIN_W = 96f
    /** A shortcut tile's narrowest, with its name / icon alone. */
    const val TILE_MIN_W = 44f
    const val TILE_MIN_W_ICON = 26f
    /** A tile's icon (16 x 16, one GBA pixel each). */
    const val TILE_ICON = 16f

    /** Between two party slots. */
    const val SLOT_GAP = 3f

    /** A named slot's height: the name over the level and HP bar, beside the icon. */
    fun slotHeight(t: Text) = max(ICON, t.small * 2 + 7f) + 4f

    /** [card]'s height as a [form]. A MAP among the small cards (no map picture for the game) is its name's line. */
    fun cardHeight(card: HomeCard, form: Form, t: Text): Float = when (if (card == HomeCard.MAP) Form.LINE else form) {
        Form.LINE -> 2 * FRAME + t.small
        Form.BOX -> 2 * FRAME + when (card) {
            // Its heading and the play time, then the badges and the dex.
            HomeCard.TRAINER -> t.small + 3f + 2 * (t.small + 2f)
            else -> 0f
        }
    }

    fun shortcutHeight(labels: Boolean, t: Text) = 2 * 4f + TILE_ICON + if (labels) 3f + t.small else 0f

    /**
     * The layout for a tab [w] x [h] GBA pixels big. [short] = the companion is a short one (LocalShortCompanion);
     * [cards] the player's cards that have something to show, in order; [shortcuts] how many shortcuts there are;
     * [mapPicture] false = the game has no map to draw, so MAP is the place's name, one line among the small cards.
     */
    fun layout(w: Float, h: Float, short: Boolean, cards: List<HomeCard>, shortcuts: Int, t: Text, mapPicture: Boolean = true): HomeLayout {
        val shape = when {
            short || h < COMPACT_MAX_H -> Shape.COMPACT
            w < h -> Shape.TALL
            w / h >= WIDE_ASPECT && w >= WIDE_MIN_W -> Shape.WIDE
            else -> Shape.STANDARD
        }
        val labels = shape != Shape.COMPACT
        val tileMin = if (labels) TILE_MIN_W else TILE_MIN_W_ICON
        // Tiles as wide as six (or as many as fit) would be, so a few don't stretch; the row starts at the left.
        val capacity = min(com.pokedaisy.app.companion.HomeTab.MAX_SHORTCUTS, floor((w - 2 * GAP + GAP) / (tileMin + GAP)).toInt()).coerceAtLeast(1)
        val tileW = (w - 2 * GAP - (capacity - 1) * GAP) / capacity
        val shortcutCount = min(shortcuts, capacity).coerceAtLeast(0)
        val stripH = if (shortcutCount > 0) shortcutHeight(labels, t) else 0f
        val strip = if (shortcutCount > 0) Box(GAP, h - GAP - stripH, shortcutCount * tileW + (shortcutCount - 1) * GAP, stripH) else null
        // The cards' room: the tab, less its edge gap and the shortcuts.
        val room = Box(GAP, GAP, w - 2 * GAP, (strip?.y ?: (h - GAP)) - (if (strip != null) GAP else 0f) - GAP)
        val party = HomeCard.PARTY in cards
        val map = HomeCard.MAP in cards && mapPicture
        val smalls = cards.filter { !it.big || it == HomeCard.MAP && !mapPicture }
        val l = when (shape) {
            Shape.COMPACT -> compact(room, party, map, smalls, t)
            Shape.STANDARD -> columns(room, party, map, smalls, t, wide = false)
            Shape.WIDE -> columns(room, party, map, smalls, t, wide = true)
            Shape.TALL -> tall(room, party, map, smalls, t)
        }
        return l.copy(shape = shape, shortcuts = strip, shortcutCount = shortcutCount, shortcutLabels = labels, shortcutTileW = tileW)
    }

    private fun result(party: Box?, columns: Int, mini: Boolean, map: Box?, placed: List<Placed>, smalls: List<HomeCard>) = HomeLayout(
        Shape.STANDARD, party, columns, mini, map, placed, null, 0, true, smalls.filter { s -> placed.none { it.card == s } },
    )

    /**
     * Small cards stacked down [col] from its top (or up from its bottom, [fromBottom]), each a BOX while the stack leaves
     * [keep] of the column, else a LINE, else left out. Returns them and the height they took.
     */
    private fun stack(col: Box, smalls: List<HomeCard>, t: Text, keep: Float, fromBottom: Boolean, lineOnly: Boolean = false): Pair<List<Placed>, Float> {
        val out = mutableListOf<Placed>()
        var used = 0f
        for (c in smalls) {
            val form = listOf(Form.BOX, Form.LINE).filter { !lineOnly || it == Form.LINE }.firstOrNull { f ->
                used + cardHeight(c, f, t) + (if (out.isEmpty()) 0f else GAP) <= col.h - keep
            } ?: continue
            val ch = cardHeight(c, form, t)
            val top = if (out.isEmpty()) 0f else GAP
            val y = if (fromBottom) col.bottom - used - top - ch else col.y + used + top
            out += Placed(c, Box(col.x, y, col.w, ch), form)
            used += top + ch
        }
        return out to used
    }

    /** The party's columns for a box [w] wide. */
    private fun partyColumns(w: Float, max: Int) = (1..max).last { n -> n == 1 || (w - 2 * FRAME - (n - 1) * SLOT_GAP) / n >= SLOT_MIN_W }

    /** A slot's height in [box] with [columns]: the rows share what the frame leaves. */
    fun slotHeightIn(box: Box, columns: Int): Float {
        val rows = (6 + columns - 1) / columns
        return (box.h - 2 * FRAME - (rows - 1) * SLOT_GAP) / rows
    }

    private fun compact(room: Box, party: Boolean, map: Boolean, smalls: List<HomeCard>, t: Text): HomeLayout {
        // The party a narrow column of six on the left; the rest of the width the map's, the small cards one line each under it.
        // Too short for six rows (the smallest phone companion): two columns of three, wider.
        val twoUp = (room.h - 2 * FRAME - 5 * SLOT_GAP) / 6 < MINI_SLOT_MIN_H
        val partyW = if (!party) 0f else if (twoUp) (room.w * 0.5f).coerceIn(120f, 220f) else (room.w * 0.36f).coerceIn(72f, 128f)
        val partyBox = if (party) Box(room.x, room.y, partyW, room.h) else null
        val right = if (party) Box(room.x + partyW + GAP, room.y, room.w - partyW - GAP, room.h) else room
        val keep = if (map) MAP_MIN_H + GAP else 0f
        val (placed, used) = stack(right, smalls, t, keep, fromBottom = map, lineOnly = true)
        val mapBox = if (map) Box(right.x, right.y, right.w, right.h - used - (if (placed.isEmpty()) 0f else GAP)) else null
        // Six small slots one under the other (or two-up).
        return result(partyBox, if (twoUp) 2 else 1, true, mapBox, placed, smalls)
    }

    private fun columns(room: Box, party: Boolean, map: Boolean, smalls: List<HomeCard>, t: Text, wide: Boolean): HomeLayout {
        if (wide && party && map) {
            // Party | map | a column of small cards.
            val partyW = floor(room.w * 0.32f)
            val sideW = if (smalls.isEmpty()) 0f else floor(room.w * 0.28f)
            val mapW = room.w - partyW - sideW - GAP - (if (sideW > 0f) GAP else 0f)
            val side = Box(room.x + partyW + GAP + mapW + GAP, room.y, sideW, room.h)
            val (placed, _) = if (sideW > 0f) stack(side, smalls, t, 0f, fromBottom = false) else emptyList<Placed>() to 0f
            val partyBox = Box(room.x, room.y, partyW, room.h)
            return result(partyBox, partyColumns(partyW, 2), partyMini(partyBox, partyColumns(partyW, 2), t), Box(room.x + partyW + GAP, room.y, mapW, room.h), placed, smalls)
        }
        val half = floor((room.w - GAP) / 2f)
        val left = Box(room.x, room.y, half, room.h)
        val right = Box(room.x + half + GAP, room.y, room.w - half - GAP, room.h)
        return when {
            party && map -> {
                // The map keeps the top of the right half: at least [MAP_MIN_H] and two fifths of it.
                val keep = max(MAP_MIN_H, room.h * 0.4f) + GAP
                val (placed, used) = stack(right, smalls, t, keep, fromBottom = true)
                val cols = partyColumns(left.w, 2)
                result(left, cols, partyMini(left, cols, t), Box(right.x, right.y, right.w, right.h - used - (if (placed.isEmpty()) 0f else GAP)), placed, smalls)
            }
            party || map -> {
                val (placed, _) = stack(right, smalls, t, 0f, fromBottom = false)
                // Alone, the big card takes the whole width when nothing else is on.
                val big = if (smalls.isEmpty()) room else left
                if (party) {
                    val cols = partyColumns(big.w, 3)
                    result(big, cols, partyMini(big, cols, t), null, placed, smalls)
                } else result(null, 1, false, big, placed, smalls)
            }
            else -> {
                // Small cards only: two columns of boxes.
                val (a, _) = stack(left, smalls.filterIndexed { i, _ -> i % 2 == 0 }, t, 0f, fromBottom = false)
                val (b, _) = stack(right, smalls.filterIndexed { i, _ -> i % 2 == 1 }, t, 0f, fromBottom = false)
                result(null, 1, false, null, a + b, smalls)
            }
        }
    }

    private fun tall(room: Box, party: Boolean, map: Boolean, smalls: List<HomeCard>, t: Text): HomeLayout {
        val cols = if (party) partyColumns(room.w, 3) else 1
        val rows = (6 + cols - 1) / cols
        val partyH = if (party) 2 * FRAME + rows * slotHeight(t) + (rows - 1) * SLOT_GAP else 0f
        // Small cards two to a row where two fit, in the player's order, under the party; the map takes what's left on top.
        val pair = room.w >= 2 * CARD_MIN_W + GAP
        val colW = if (pair) floor((room.w - GAP) / 2f) else room.w
        val mapKeep = if (map) max(MAP_MIN_H, room.w * 0.4f) + GAP else 0f
        val below = room.h - partyH - (if (party) GAP else 0f) - mapKeep
        val cardRows = mutableListOf<Pair<List<HomeCard>, Form>>()
        var used = 0f
        val queue = smalls.toMutableList()
        while (queue.isNotEmpty()) {
            val row = if (pair) queue.take(2) else queue.take(1)
            val top = if (cardRows.isEmpty()) 0f else GAP
            val form = listOf(Form.BOX, Form.LINE).firstOrNull { f -> used + top + row.maxOf { cardHeight(it, f, t) } <= below } ?: break
            cardRows += row to form
            used += top + row.maxOf { cardHeight(it, form, t) }
            repeat(row.size) { queue.removeAt(0) }
        }
        // With a map: the party and the cards at the bottom, the map over them. Without: everything from the top.
        val cardsTop = if (map) room.bottom - used else room.y + partyH + (if (party) GAP else 0f)
        val placed = mutableListOf<Placed>()
        var y = cardsTop
        cardRows.forEachIndexed { i, (row, form) ->
            if (i > 0) y += GAP
            val rh = row.maxOf { cardHeight(it, form, t) }
            row.forEachIndexed { j, c -> placed += Placed(c, Box(room.x + j * (colW + GAP), y, if (row.size == 1) room.w else colW, rh), form) }
            y += rh
        }
        val partyBox = if (!party) null
            else if (map) Box(room.x, cardsTop - (if (placed.isEmpty()) 0f else GAP) - partyH, room.w, partyH)
            else Box(room.x, room.y, room.w, partyH)
        val mapBox = if (map) Box(room.x, room.y, room.w, (partyBox?.y ?: (cardsTop - if (placed.isEmpty()) 0f else GAP)) - GAP - room.y) else null
        return result(partyBox, cols, partyMini(partyBox ?: room, cols, t), mapBox, placed, smalls)
    }

    /** Small slots when a named one doesn't fit the box's rows. */
    private fun partyMini(box: Box, columns: Int, t: Text): Boolean = slotHeightIn(box, columns) < slotHeight(t) - 4f
}
