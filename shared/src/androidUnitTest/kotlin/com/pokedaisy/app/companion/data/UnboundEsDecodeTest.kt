package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Unbound v2.1.1.1 ES (the Spanish translation v1.25, its "No ZA" build): English Unbound's RAM, its names in Spanish. The fixture
 * is the user's English save booted in it (see its README). */
class UnboundEsDecodeTest {
    @After fun reset() {
        romGameCode = ""
        romLanguage = 'E'
    }

    private fun select() {
        activeGame = GameKind.UNBOUND
        romGameCode = NATIVE_UNBOUND_ES.gameCode
        romLanguage = NATIVE_UNBOUND_ES.language
    }

    @Test
    fun `party, bag and location decode, in Spanish`() {
        select()
        val t = decodeNative("unbound_es", NATIVE_UNBOUND_ES)
        assertEquals(listOf("Gible" to 11), t.party.map { speciesName(it.species) to it.level })
        assertEquals(listOf(496), t.party.map { it.species }) // Unbound's own ids, as in English
        assertEquals(listOf("Placaje", "Ataque Arena", "Garra Metal"), t.party[0].moves.filter { it != 0 }.map { lookupMove(it).name })
        // A move keeps Unbound's type / power under its Italian name.
        assertEquals(moveDataUnbound.getValue(t.party[0].moves[0]).copy(name = "Placaje"), activeMoveData.getValue(t.party[0].moves[0]))
        assertEquals(
            listOf("Poción" to 2, "Repelente" to 2, "Zumo de Baya" to 1, "Caja Trajes" to 1, "Poké Ball" to 5),
            t.items.map { itemName(it.itemId) to it.quantity },
        )
        assertEquals(3 to 66, t.mapGroup to t.mapNum)
        assertEquals("Cumbres Heladas", activeMapSecData[t.regionMapSectionId]!!.name)
        assertEquals(3848L, t.money)
        assertFalse(t.inBattle)
        assertPocketsInRange(t)
        // Its natures are Spanish too (the summary's STATS).
        assertEquals("Fuerte", localText!!.natures[0])
        assertEquals("Pueblo Bellin", activeMapSecData[89]!!.name)
    }

    @Test
    fun `its POKéDEX page is metric with a decimal comma`() {
        assertEquals("0,7 m", formatDexHeight(POKEDEX_UNBOUND_ES, 7))
        assertEquals("69,0 kg", formatDexWeight(POKEDEX_UNBOUND_ES, 690))
    }
}
