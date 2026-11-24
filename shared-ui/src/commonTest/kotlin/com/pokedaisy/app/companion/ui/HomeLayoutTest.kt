package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.HomeCard
import com.pokedaisy.app.companion.ui.HomeLayouts.Form
import com.pokedaisy.app.companion.ui.HomeLayouts.Shape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HOME on every companion size (docs/plans/companion-home.md's table): the tab's room in GBA pixels, the tab bar taken
 * off. Nothing outside the tab, nothing on top of anything, every slot / tile / map big enough, and the shape each gets.
 */
class HomeLayoutTest {
    private class Screen(val name: String, val w: Float, val h: Float, val short: Boolean, val shape: Shape, val text: HomeLayouts.Text)

    // The text's sizes in GBA pixels: the main text 4 screen px a font pixel on a 3-px GBA pixel (21.3), 3 with the
    // window's step down or on a 2-px one (16); the small text always one GBA pixel a font pixel (16).
    private val big = HomeLayouts.Text(line = 21.4f, small = 16f)
    private val stepped = HomeLayouts.Text(line = 16f, small = 16f)
    // FONT SIZE LARGE.
    private val large = HomeLayouts.Text(line = 26.7f, small = 21.4f)

    private val screens = listOf(
        Screen("RG DS", 320f, 206f, true, Shape.COMPACT, stepped),
        Screen("phone upright, default", 360f, 265f, true, Shape.COMPACT, stepped),
        Screen("phone upright, smallest", 360f, 170f, true, Shape.COMPACT, stepped),
        Screen("Retroid Pocket Duo", 427f, 272f, false, Shape.STANDARD, big),
        Screen("AYN Thor", 413f, 312f, false, Shape.STANDARD, big),
        Screen("RP Classic window", 363f, 270f, false, Shape.STANDARD, stepped),
        Screen("RP6 window", 443f, 270f, false, Shape.STANDARD, stepped),
        Screen("side panel", 480f, 490f, false, Shape.TALL, big),
        Screen("side panel 4:3", 320f, 430f, false, Shape.TALL, big),
        Screen("phone upright, full", 360f, 480f, false, Shape.TALL, stepped),
        Screen("Thor top screen (SWAP SCREENS)", 640f, 312f, false, Shape.WIDE, big),
    )

    private val all = HomeCard.entries.toList()

    private fun subsets(): List<List<HomeCard>> {
        val out = mutableListOf<List<HomeCard>>()
        for (mask in 0 until (1 shl all.size)) out += all.filterIndexed { i, _ -> mask shr i and 1 == 1 }
        // The player's own order too: small cards first, reversed.
        out += all.reversed()
        return out
    }

    private fun check(s: Screen, cards: List<HomeCard>, shortcuts: Int, t: HomeLayouts.Text, mapPicture: Boolean = true) {
        val l = HomeLayouts.layout(s.w, s.h, s.short, cards, shortcuts, t, mapPicture)
        val what = "${s.name}, ${cards.map { it.id }}, $shortcuts shortcuts"
        assertEquals(s.shape, l.shape, what)
        for (b in l.boxes) {
            assertTrue(b.x >= 0f && b.y >= 0f && b.right <= s.w + 0.01f && b.bottom <= s.h + 0.01f, "$what: $b off the tab")
            assertTrue(b.w > 0f && b.h > 0f, "$what: $b empty")
        }
        l.boxes.forEachIndexed { i, a -> l.boxes.drop(i + 1).forEach { b -> assertTrue(!a.overlaps(b), "$what: $a on $b") } }
        // Each card placed at most once, and placed or dropped.
        assertEquals(cards.filter { !it.big || it == HomeCard.MAP && !mapPicture }.toSet(), (l.cards.map { it.card } + l.dropped).toSet(), what)
        if (!mapPicture) assertEquals(null, l.map, what)
        assertEquals(l.cards.size, l.cards.map { it.card }.toSet().size, what)
        // At least its form's height (two side by side share the taller one's).
        for (p in l.cards) assertTrue(p.box.h >= HomeLayouts.cardHeight(p.card, p.form, t) - 0.01f, "$what: ${p.card} ${p.box.h}")
        l.map?.let { assertTrue(it.w >= HomeLayouts.MAP_MIN_W && it.h >= HomeLayouts.MAP_MIN_H, "$what: map $it too small") }
        l.party?.let { p ->
            val slot = HomeLayouts.slotHeightIn(p, l.partyColumns)
            val need = if (l.partyMini) HomeLayouts.MINI_SLOT_MIN_H else HomeLayouts.slotHeight(t) - 4f
            assertTrue(slot >= need, "$what: party slot $slot under $need")
            if (!l.partyMini) assertTrue((p.w - 2 * HomeLayouts.FRAME - (l.partyColumns - 1) * HomeLayouts.SLOT_GAP) / l.partyColumns >= HomeLayouts.SLOT_MIN_W || l.partyColumns == 1, "$what: slots too narrow")
        }
        if (shortcuts > 0) {
            val strip = l.shortcuts!!
            assertTrue(l.shortcutCount >= minOf(shortcuts, 4), "$what: only ${l.shortcutCount} shortcuts")
            val tile = l.shortcutTileW
            assertEquals(strip.w, l.shortcutCount * tile + (l.shortcutCount - 1) * HomeLayouts.GAP, 0.01f, what)
            assertTrue(tile >= if (l.shortcutLabels) HomeLayouts.TILE_MIN_W else HomeLayouts.TILE_MIN_W_ICON, "$what: tiles $tile wide")
        } else assertEquals(null, l.shortcuts, what)
    }

    @Test fun everySizeEveryCardChoice() {
        for (s in screens) for (cards in subsets()) for (n in listOf(0, 3, 6)) check(s, cards, n, s.text)
    }

    @Test fun aGameWithoutAMapPictureGetsItsPlaceAsALine() {
        for (s in screens) for (cards in subsets()) check(s, cards, 6, s.text, mapPicture = false)
    }

    @Test fun fontSizeLargeStillFits() {
        for (s in screens.filter { !it.short }) for (cards in subsets()) check(s, cards, 6, large)
    }

    @Test fun theDefaultCardsAllShowBeyondTheSmallestScreens() {
        // Every default card has a place everywhere but the phone's smallest companion (there the map and the party
        // keep their room, and one small card may go).
        for (s in screens.filter { it.name != "phone upright, smallest" }) {
            val l = HomeLayouts.layout(s.w, s.h, s.short, all, 6, s.text)
            assertEquals(emptyList(), l.dropped, s.name)
            assertEquals(6, l.shortcutCount, s.name)
        }
    }

    @Test fun theThorKeepsBothSmallCardsAsBoxesAndANamedParty() {
        val thor = screens.first { it.name == "AYN Thor" }
        val l = HomeLayouts.layout(thor.w, thor.h, false, all, 6, thor.text)
        assertEquals(listOf(Form.BOX, Form.BOX), l.cards.map { it.form })
        assertEquals(2, l.partyColumns)
        assertTrue(!l.partyMini)
    }
}
