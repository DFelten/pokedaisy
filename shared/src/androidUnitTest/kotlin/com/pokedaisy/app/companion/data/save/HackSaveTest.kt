package com.pokedaisy.app.companion.data.save

import com.pokedaisy.app.companion.data.NATIVE_EMERALD_LEGACY
import com.pokedaisy.app.companion.data.RETAIL_ROM_DIR
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ROM hacks' saves in the SAVE EDITOR, over the player's own ROMs and saves (skipped where they're missing). */
class HackSaveTest {
    private val roms = File(RETAIL_ROM_DIR)
    private val saves = File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA")

    private fun legacy(): Pair<ByteArray, ByteArray> {
        val rom = File(roms, "Pokemon Emerald Legacy (v1.1.4).gba")
        val save = File(saves, "Pokemon Emerald Legacy (v1.1.4).sav")
        assumeTrue("needs the Legacy ROM and save", rom.isFile && save.isFile)
        val rules = RomGen3Rules(rom.readBytes(), requireNotNull(gen3RomTables(NATIVE_EMERALD_LEGACY, "emerald-legacy")))
        Gen3Rules.install(Gen3SaveFormat.EMERALD_LEGACY, rules)
        return rom.readBytes() to save.readBytes()
    }

    @Test fun legacyRulesReadItsOwnRom() {
        legacy()
        val r = Gen3SaveFormat.EMERALD_LEGACY.rules
        assertEquals("BULBASAUR", r.speciesName(1)?.uppercase())
        assertEquals("POUND", r.moveName(1)?.uppercase())
        assertEquals(listOf(45, 49, 49, 45, 65, 65), r.baseStats(1).toList())
        assertEquals(415, r.eggSpecies)
        assertTrue(r.lastMove >= 354, "last move ${r.lastMove}")
        assertEquals(35, r.basePp(1))
        assertTrue(33 in r.learnable(1, 1), "Bulbasaur knows Tackle at Lv1")
        assertTrue(r.bagItems(BagPocket.BALLS).isNotEmpty())
        assertEquals(BagPocket.BALLS, r.pocketOf(1))
        println("Legacy: lastMove ${r.lastMove}, balls ${r.bagItems(BagPocket.BALLS).size}, items ${r.bagItems(BagPocket.ITEMS).size}, tmhm ${r.bagItems(BagPocket.TMHM).size}")
    }

    @Test fun legacySaveReadsAndEditsRoundTrip() {
        val (_, bytes) = legacy()
        val parsed = Gen3SaveFile.parse(bytes, Gen3SaveFormat.EMERALD_LEGACY)
        assertTrue(parsed is Gen3SaveFile.Parsed.Ok, (parsed as? Gen3SaveFile.Parsed.Failed)?.reason)
        val file = parsed.save
        val data = Gen3SaveData(file)
        assertEquals(null, data.mismatch())
        println("Legacy save: ${data.trainerName} ${data.trainerId} money ${data.money} party " +
            data.party.joinToString { "${data.rules.speciesName(it.mon.species)} Lv${it.level}" } + " balls ${data.pocket(BagPocket.BALLS)}")
        assertTrue(data.partyCount > 0)
        // Edits: the lead to the PC and back, a level, then the file re-read.
        val ed = Gen3SaveEditor(file)
        val lead = data.party[0]
        if (data.partyCount > 1) ed.move(MonRef.Party(0), MonRef.Pc(BoxSlot(13, 29)))
        ed.setMoney(123_456)
        val out = ed.build()
        val again = (Gen3SaveFile.parse(out, Gen3SaveFormat.EMERALD_LEGACY) as Gen3SaveFile.Parsed.Ok).save
        val d2 = Gen3SaveData(again)
        assertEquals(null, d2.mismatch())
        assertEquals(123_456, d2.money)
        if (data.partyCount > 1) assertEquals(lead.mon.personality, d2.mon(BoxSlot(13, 29)).personality)
    }
}
