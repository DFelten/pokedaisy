package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pokémon Generation 3 Evolved v1.0.22 and v1.0.17: pokeemerald-expansion 1.16.3's FireRed build, its own RAM and names
 * (gen_expansion_tables.py gen3_evolved). A new game played headless in v1.0.22, loaded in both (the fixtures' READMEs say
 * what the game's own screens show). */
class Gen3EvolvedDecodeTest {
    private fun checkSave(fixture: String, cfg: NativeConfig) {
        activeGame = GameKind.GEN3_EVOLVED
        val t = decodeNative(fixture, cfg)
        assertEquals(listOf(Triple("Charmander", 12, 33), Triple("Pikachu", 10, 29), Triple("Gengar", 30, 79),
            Triple("Lucario", 25, 76)), t.party.map { Triple(speciesName(it.species), it.level, it.maxHp) })
        assertEquals(listOf("Oran Berry", "Leftovers"), t.party.drop(1).take(2).map { itemName(it.heldItem) })
        assertEquals(setOf("Repel" to 2, "Potion" to 5, "Oran Berry" to 4, "TM01" to 1, "Poké Ball" to 3),
            t.items.map { itemName(it.itemId) to it.quantity }.toSet())
        assertEquals(3000L, t.money)
        assertEquals("Pallet Town", activeMapSecData[t.regionMapSectionId]?.name)
        assertFalse(t.inBattle)
        assertPocketsInRange(t)
    }

    private fun checkBattle(fixture: String, cfg: NativeConfig) {
        activeGame = GameKind.GEN3_EVOLVED
        val ram = FixtureMemoryReader.load(fixture)
        val t = decodeNative(fixture, cfg)
        assertTrue(t.inBattle)
        val me = t.battleMons[BATTLE_POS_PLAYER_LEFT]
        val foe = t.battleMons[BATTLE_POS_OPPONENT_LEFT]
        assertEquals("Charmander" to 12, speciesName(me.species) to me.level)
        assertEquals(listOf("Growl", "Ember", "Smokescreen", "Dragon Breath"), me.moves.map { lookupMove(it).name })
        assertEquals("Pidgey" to 5, speciesName(foe.species) to foe.level)
        assertEquals(0 to BATTLE_INPUT_ACTION_SELECT, readNativeBattleInputFast(ram, cfg))
    }

    @Test fun `v1_0_22 - the save`() = checkSave("gen3_evolved", NATIVE_GEN3_EVOLVED)
    @Test fun `v1_0_22 - a wild battle`() = checkBattle("gen3_evolved_battle", NATIVE_GEN3_EVOLVED)
    @Test fun `v1_0_17 - the save`() = checkSave("gen3_evolved_v1_0_17", NATIVE_GEN3_EVOLVED_V1_0_17)
    @Test fun `v1_0_17 - a wild battle`() = checkBattle("gen3_evolved_v1_0_17_battle", NATIVE_GEN3_EVOLVED_V1_0_17)
}
