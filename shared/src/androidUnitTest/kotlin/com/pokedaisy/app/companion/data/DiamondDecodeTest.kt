package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Pokémon Diamond and Pearl (Nintendo DS) on Platinum's reader with their own config - see the diamond / pearl
 * fixtures' READMEs (one save, edited by the save editor's own test, booted in each). */
class DiamondDecodeTest {
    @After fun tearDown() { diamondPearl = false }

    private fun read(fixture: String, code: String, cfg: Gen4Config): Telemetry {
        activeGame = GameKind.PLATINUM
        diamondPearl = true
        romLanguage = 'E'
        romGameCode = code
        return readGen4Telemetry(FixtureMemoryReader.load(fixture), cfg)
    }

    private fun check(t: Telemetry) {
        assertFalse(t.inBattle)
        val gyarados = t.party.single()
        assertEquals("GYARADOS" to 30, speciesName(gyarados.species) to gyarados.level)
        assertEquals(104 to 104, gyarados.hp to gyarados.maxHp)
        assertEquals(listOf("Bite", "Dragon Rage", "Leer", "Twister"), gyarados.moves.map { lookupMove(it).name })
        assertFalse(gyarados.isEgg)
        assertEquals(listOf(Triple("Potion", 5, POCKET_ITEMS)), t.items.map { Triple(itemName(it.itemId), it.quantity, it.pocket) })
        assertEquals(123456L, t.money)
        assertEquals(415, t.mapNum)
        assertEquals(4 to 6, t.x to t.y)
        assertEquals("Twinleaf Town", t.mapSecName)
        assertNull(t.pokedex)
    }

    @Test fun `Diamond - party, bag, money, place`() = check(read("diamond", "ADAE", GEN4_DIAMOND_US))
    @Test fun `Pearl - the same save, the same addresses`() = check(read("pearl", "APAE", GEN4_PEARL_US))

    @Test
    fun `the carts pick their config and leave the guide off`() {
        assertSame(GEN4_DIAMOND_US, gen4ConfigFor(GameKind.PLATINUM, "ADAE"))
        assertSame(GEN4_PEARL_US, gen4ConfigFor(GameKind.PLATINUM, "APAE"))
        activeGame = GameKind.PLATINUM
        diamondPearl = true
        assertEquals(NdsArt.DIAMOND_ICONS, activeMonSpriteDir)
        assertEquals("Mystery Zone", mapPlacesDiamond[0])
        assertEquals("Twinleaf Town", mapPlacesDiamond[415])
        assertNull(guideId(GameKind.PLATINUM, null))
    }
}
