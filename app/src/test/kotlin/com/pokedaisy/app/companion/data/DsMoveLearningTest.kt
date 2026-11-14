package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The summary's LEARN list on a DS game (Black 2's fixture, the cart's learnsets and machines): the next level-up moves,
 * then the bag's TMs / HMs the Pokémon can learn - no verdicts, which are the original GBA games' alone.
 * Skips without the ROM (`-DromDir=`'s ../nds/).
 */
class DsMoveLearningTest {
    private val rom = File(File(RETAIL_ROM_DIR).parentFile, "nds/Pokemon - Black Version 2 (USA, Europe) (NDSi Enhanced).nds")
    private var dir: File? = null

    @After fun tearDown() {
        NdsArt.dir = null
        dir?.deleteRecursively()
    }

    @Test fun `level-up moves and the bag's machines`() {
        assumeTrue("no Black 2 ROM at $rom", rom.isFile)
        activeGame = GameKind.BLACK2
        dir = kotlin.io.path.createTempDirectory("nds-art").toFile().also { assertTrue(NdsArt.extractTo(rom, it)); NdsArt.dir = it }
        val v = buildSnapshotView(readGen5Telemetry(FixtureMemoryReader.load("black2"), GEN5_BLACK2))
        val t = v.pokedex!!.tables
        assertTrue(MoveLearning.available(t, v.guideTables))
        assertFalse(MoveLearning.advises)
        val lead = v.party[0]
        // At level 1 every learnset move it doesn't know is still to come: the first four, in level order.
        val young = lead.copy(level = 1)
        val levels = MoveLearning.options(t, v.guideTables, young, emptyList())
        val expected = DexDetails.levelUp(t, v.guideTables, lead.species)!!
            .filter { m -> lead.moves.none { it.id == m.move } }.distinctBy { it.move }.take(4)
        assertEquals(expected.map { it.move }, levels.map { it.move })
        assertTrue(levels.all { it.tm.isEmpty() })
        // The bag's machines: each one the lead can learn and doesn't know, under the bag's own name.
        val tms = MoveLearning.options(t, v.guideTables, lead, v.items).filter { it.tm.isNotEmpty() }
        assertTrue(tms.isNotEmpty())
        val teach = DexDetails.teachable(t, v.guideTables, lead.species)!!.associate { it.label to it.move }
        val bagNames = v.items.map { it.name.uppercase().replace(" ", "") }.toSet()
        tms.forEach { o ->
            assertEquals(teach[o.tm], o.move)
            assertTrue(o.tm in bagNames)
            assertTrue(lead.moves.none { it.id == o.move })
        }
    }
}
