package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Game Boy games' learnsets and TMs ([GbLearnsets]) on the player's own carts (`gbc/` beside the GBA ROM folder; each
 * skipped where it isn't here): a few Pokémon checked against what the games teach.
 */
class GbLearnsetsTest {
    private val dir = File(File(RETAIL_ROM_DIR).parentFile, "gbc")

    private fun cart(name: String) = dir.resolve(name).takeIf { it.isFile }?.readBytes()

    @Test
    fun gen1() {
        val carts = listOf(
            "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb" to POKEDEX_YELLOW,
            "Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb" to POKEDEX_RED,
            "Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb" to POKEDEX_BLUE,
            // The Gen II Graphics hacks keep the learnsets: Red's at Red's addresses, Yellow's behind a moved pointer table.
            "Pokemon Red Gen II Graphics (v2.0).gb" to POKEDEX_RED_GEN2_GRAPHICS,
            "Pokemon Yellow Gen II Graphics (v1.1).gb" to POKEDEX_YELLOW_GEN2_GRAPHICS,
        ).mapNotNull { (n, t) -> cart(n)?.let { Triple(n, t, it) } }
        assumeTrue(carts.isNotEmpty())
        for ((name, t, rom) in carts) {
            assertTrue(name, GbLearnsets.available(t))
            // BULBASAUR: TACKLE and GROWL to start with (its BaseStats' moves, as level 1), then LEECH SEED at 7, VINE
            // WHIP at 13 ... SOLARBEAM at 48 (Yellow's the same).
            val bulba = GbLearnsets.levelUp(t, 1, rom)!!
            assertEquals(name, listOf(1 to 33, 1 to 45, 7 to 73, 13 to 22, 20 to 77, 27 to 75, 34 to 74, 41 to 79, 48 to 76), bulba.map { it.level to it.move })
            // MEW starts with POUND (Red / Blue's MEW record is apart), then TRANSFORM at 10.
            assertEquals(name, listOf(1 to 1, 10 to 144), GbLearnsets.levelUp(t, 151, rom)!!.take(2).map { it.level to it.move })
            // Its TMs include SWORDS DANCE (TM03) and CUT (HM01), not THUNDERBOLT (TM24).
            val tms = GbLearnsets.machines(t, 1, rom)!!
            assertTrue(name, TeachMove("TM03", 14) in tms)
            assertTrue(name, TeachMove("HM01", 15) in tms)
            assertFalse(name, tms.any { it.label == "TM24" })
            // MEW learns all 55 (Red / Blue keep it apart).
            assertEquals(name, 55, GbLearnsets.machines(t, 151, rom)!!.size)
            // KADABRA's learnset comes after its trade evolution entry (its starting moves, TELEPORT first, before it).
            val kadabra = GbLearnsets.levelUp(t, 64, rom)!!
            assertEquals(name, 1 to 100, kadabra.first().let { it.level to it.move })
            assertEquals(name, 16 to 93, kadabra.first { it.level > 1 }.let { it.level to it.move })
        }
        assertEquals("TM01", GbLearnsets.machineLabel(POKEDEX_YELLOW, 0xC9))
        assertEquals("HM05", GbLearnsets.machineLabel(POKEDEX_YELLOW, 0xC8))
        assertNull(GbLearnsets.machineLabel(POKEDEX_YELLOW, 0x14))
    }

    @Test
    fun gen2() {
        val carts = listOf(
            "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc" to POKEDEX_CRYSTAL,
            "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc" to GEN2_GOLD.pokedex!!,
            "Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc" to GEN2_SILVER.pokedex!!,
        ).mapNotNull { (n, t) -> cart(n)?.let { Triple(n, t, it) } }
        assumeTrue(carts.isNotEmpty())
        for ((name, t, rom) in carts) {
            assertTrue(name, GbLearnsets.available(t))
            // CHIKORITA: TACKLE and GROWL at 1, RAZOR LEAF at 8, REFLECT at 12.
            val chiko = GbLearnsets.levelUp(t, 152, rom)!!.map { it.level to it.move }
            assertEquals(name, listOf(1 to 33, 1 to 45, 8 to 75, 12 to 115), chiko.take(4))
            // CUT (HM01) for CHIKORITA; PIKACHU's THUNDER is TM25, its THUNDERBOLT only Crystal's tutor (unlabelled).
            assertTrue(name, TeachMove("HM01", 15) in GbLearnsets.machines(t, 152, rom)!!)
            val pika = GbLearnsets.machines(t, 25, rom)!!
            assertEquals(name, t === POKEDEX_CRYSTAL, TeachMove("", 85) in pika)
            assertTrue(name, TeachMove("TM25", 87) in pika)
        }
        assertEquals("TM01", GbLearnsets.machineLabel(POKEDEX_CRYSTAL, 0xBF))
    }

    @Test
    fun otherLanguagesAndRebuiltHacksAreLeftOut() {
        assertFalse(GbLearnsets.available(POKEDEX_YELLOW.copy(gen1 = POKEDEX_YELLOW.gen1!!.copy(evosMoves = null))))
        assertFalse(GbLearnsets.available(POKEDEX_CRYSTAL_DE))
        assertFalse(GbLearnsets.available(POKEDEX_BLUE_DE))
        // Hacks with learnsets of their own (no addresses mapped for them) stay out; the Gen II Graphics ones are in.
        assertFalse(GbLearnsets.available(POKEDEX_PURE_RED))
        assertFalse(GbLearnsets.available(POKEDEX_YELLOW_LEGACY))
        assertFalse(GbLearnsets.available(POKEDEX_BROWN))
        assertTrue(GbLearnsets.available(POKEDEX_RED_GEN2_GRAPHICS))
        assertTrue(GbLearnsets.available(POKEDEX_YELLOW_GEN2_GRAPHICS))
    }
}
