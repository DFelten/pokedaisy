package com.pokedaisy.app.companion.data

import okio.Path.Companion.toOkioPath
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
    private val rom = File(File(RETAIL_ROM_DIR).parentFile, "nds/Pokemon - Black Version 2 (USA, Europe) (NDSi Enhanced) [b].nds")
    private var dir: File? = null

    @After fun tearDown() {
        NdsArt.dir = null
        dir?.deleteRecursively()
    }

    @Test fun `level-up moves and the bag's machines`() {
        assumeTrue("no Black 2 ROM at $rom", rom.isFile)
        activeGame = GameKind.BLACK2
        dir = kotlin.io.path.createTempDirectory("nds-art").toFile().also { assertTrue(NdsArt.extractTo(rom.toOkioPath(), it.toOkioPath())); NdsArt.dir = it.toOkioPath() }
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
        // The bag's machines: each one the lead can learn and doesn't know, found by item id (on this English cart the
        // labels are also the items' own names).
        val tms = MoveLearning.options(t, v.guideTables, lead, v.items).filter { it.tm.isNotEmpty() }
        assertTrue(tms.isNotEmpty())
        val teach = DexDetails.teachable(t, v.guideTables, lead.species)!!.associate { it.label to it.move }
        val bagLabels = v.items.mapNotNull { MoveLearning.machineLabel(t, it.itemId) }.toSet()
        val bagNames = v.items.map { it.name.uppercase().replace(" ", "") }.toSet()
        tms.forEach { o ->
            assertEquals(teach[o.tm], o.move)
            assertTrue(o.tm in bagLabels)
            assertTrue(o.tm in bagNames)
            assertTrue(lead.moves.none { it.id == o.move })
        }
    }

    /** Machines by item id, whatever the cart calls them (CT / MT in French / Spanish / Italian, kana, Hangul). */
    @Test fun `machine labels by item id`() {
        assertEquals("TM01", MoveLearning.machineLabel(POKEDEX_BLACK, 328))
        assertEquals("TM92", MoveLearning.machineLabel(POKEDEX_BLACK, 419))
        assertEquals("HM06", MoveLearning.machineLabel(POKEDEX_BLACK, 425))
        assertEquals(null, MoveLearning.machineLabel(POKEDEX_BLACK, 426))
        assertEquals("TM93", MoveLearning.machineLabel(POKEDEX_BLACK2, 618))
        assertEquals("TM95", MoveLearning.machineLabel(POKEDEX_BLACK2, 620))
        assertEquals("HM08", MoveLearning.machineLabel(POKEDEX_HEARTGOLD, 427))
        assertEquals(null, MoveLearning.machineLabel(POKEDEX_PLATINUM, 618))
        assertEquals(null, MoveLearning.machineLabel(POKEDEX_PLATINUM, 537))
        assertEquals("TM93", MoveLearning.machineLabel(POKEDEX_ORIGIN, 537))
        assertEquals("TM130", MoveLearning.machineLabel(POKEDEX_ORIGIN, 574))
        assertEquals(null, MoveLearning.machineLabel(POKEDEX_BLACK, 17))
    }
}
