package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pokémon Crystal Advance Redux (22-11-25): FireRed 1.0 edited in place, run as FIRERED with its own names (ActiveTables'
 * fireRedHack, CrystalAdvanceReduxGen.kt). A new game played headless (its party and bag given by the game's own script
 * commands, saved in game; the fixtures' READMEs) and a scripted wild battle.
 */
class CrystalAdvanceReduxDecodeTest {
    @After fun tearDown() {
        fireRedHack = null
    }

    private fun use() {
        activeGame = GameKind.FIRERED
        fireRedHack = fireRedHackFor(NATIVE_CRYSTAL_ADVANCE_REDUX)
    }

    @Test
    fun `the new game's party and bag`() {
        use()
        val t = decodeNative("crystal_advance_redux", NATIVE_CRYSTAL_ADVANCE_REDUX)
        assertEquals(
            listOf(Triple("Cyndaquil", 5, 19), Triple("Totodile", 6, 23), Triple("Chikorita", 7, 25)),
            t.party.map { Triple(speciesName(it.species), it.level, it.maxHp) },
        )
        assertEquals(listOf("Leer", "Aqua Jet"), t.party[1].moves.drop(1).filter { it != 0 }.map { lookupMove(it).name })
        assertEquals(listOf("Potion" to 5, "Poké Ball" to 10), t.items.map { itemName(it.itemId) to it.quantity })
        assertEquals(13000L, t.money)
        assertFalse(t.inBattle)
        assertPocketsInRange(t)
    }

    @Test
    fun `its species past FireRed's and its Fairy type`() {
        use()
        assertEquals("Turtwig", speciesName(440))
        assertEquals("Fairy", typeName(lookupMove(463).type)) // Play Rough
    }

    @Test
    fun `a wild battle`() {
        use()
        val ram = FixtureMemoryReader.load("crystal_advance_redux_battle")
        val t = decodeNative("crystal_advance_redux_battle", NATIVE_CRYSTAL_ADVANCE_REDUX)
        assertTrue(t.inBattle)
        val foe = t.battleMons[BATTLE_POS_OPPONENT_LEFT]
        assertEquals("Sentret" to 3, speciesName(foe.species) to foe.level)
        assertEquals("Cyndaquil" to 5, speciesName(t.battleMons[BATTLE_POS_PLAYER_LEFT].species) to t.battleMons[BATTLE_POS_PLAYER_LEFT].level)
        assertEquals(0 to BATTLE_INPUT_ACTION_SELECT, readNativeBattleInputFast(ram, NATIVE_CRYSTAL_ADVANCE_REDUX))
    }
}
