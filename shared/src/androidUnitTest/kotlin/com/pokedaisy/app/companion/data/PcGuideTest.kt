package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A PC fan game's HERE ([PcGuide]): the game's encounter kinds under every game's headings. */
class PcGuideTest {
    private fun slot(name: String, seen: Boolean? = true, owned: Boolean? = false) = PcGuide.Slot(name, 3, 3, 50, seen, owned, null)

    @Test fun headings() {
        val page = PcGuide.herePage("Mossy Cave", listOf(
            PcGuide.Kind("Cave", listOf(slot("GLOOMBAT"))),
            PcGuide.Kind("Water", listOf(slot("BUBBLEFIN"))),
            PcGuide.Kind("HeadbuttHigh", listOf(slot("THORNLET"))),
            PcGuide.Kind("SpecialSpot", listOf(slot("FINLING"))),
        ))
        assertEquals(listOf("MOSSY CAVE", "GRASS / CAVE", "SURFING", "HEADBUTT (RARE TREES)", "SPECIALSPOT"), page.sections.map { it.heading })
    }

    @Test fun nothingHere() {
        val page = PcGuide.herePage("", emptyList())
        assertEquals("THIS MAP", page.sections.single().heading)
        assertEquals("No wild POKéMON on this map.", page.sections.single().note)
    }

    @Test fun noPokedexNoCount() {
        val page = PcGuide.herePage("Route 2", listOf(PcGuide.Kind("Land", listOf(slot("EMBERPUP", seen = null, owned = null)))))
        assertNull(page.sections[0].note)
        // Nothing to hide it by: named.
        assertEquals("EMBERPUP", page.sections[1].entries.single().title)
        assertEquals(0, page.sections[1].entries.single().steps)
    }

    @Test fun anUnseenOneIsHidden() {
        val e = PcGuide.herePage("Route 2", listOf(PcGuide.Kind("Land", listOf(slot("FINLING", seen = false))))).sections[1].entries.single()
        assertEquals("???", e.title)
        assertEquals(1, e.steps)
        assertTrue(e.answer == "FINLING")
    }
}
