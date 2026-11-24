package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pokémon Heart and Soul Legends M9.16 (GameKind.HNS_LEGENDS): Heart and Soul's save booted in it (the fixture's README). */
class HnsLegendsDecodeTest {
    @Test
    fun `party, bag, money and place`() {
        activeGame = GameKind.HNS_LEGENDS
        val t = decodeNative("heart_and_soul_legends", NATIVE_HNS_LEGENDS)
        // The game's own party screen: SHIINOTIC Lv5, 21/21.
        assertEquals(listOf(Triple("SHIINOTIC", 5, 21)), t.party.map { Triple(speciesName(it.species), it.level, it.maxHp) })
        assertEquals(21, t.party[0].hp)
        assertEquals(3000L, t.money)
        assertEquals("Cherrygrove City", activeMapSecData[t.regionMapSectionId]?.name)
        assertFalse(t.inBattle)
        assertPocketsInRange(t)
        assert(t.items.isNotEmpty())
        t.items.forEach { assert(!itemName(it.itemId).startsWith("#")) { "item ${it.itemId}" } }
    }
}
