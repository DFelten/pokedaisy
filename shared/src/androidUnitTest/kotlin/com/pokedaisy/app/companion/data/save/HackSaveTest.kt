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
        // For booting in the game headless (HACK_SAVE_OUT=<file>): the lead Lv30, 123,456 money, 5 POKé BALLS.
        System.getenv("HACK_SAVE_OUT")?.let { path ->
            val e2 = Gen3SaveEditor(file)
            e2.setLevel(MonRef.Party(0), 30)
            e2.setMoney(123_456)
            e2.addItem(BagPocket.BALLS, 4, 5)
            File(path).writeBytes(e2.build())
        }
    }

    /**
     * Every hack the editor opens, through the app's own path ([Gen3SaveReader]: the ROM recognised, its rules read
     * from it): the save reads (no mismatch), and an edit saved and read back keeps the rest.
     */
    @Test fun everyHackSaveOpensThroughTheReader() {
        val cases = listOf(
            Triple("Pokemon Emerald Legacy (v1.1.4).gba", "Pokemon Emerald Legacy (v1.1.4).sav", Gen3SaveFormat.EMERALD_LEGACY),
            Triple("Pokemon Glazed (v9.2.0).gba", "Pokemon Glazed (v9.2.0).sav", Gen3SaveFormat.GLAZED),
            Triple("Pokemon Orange Islands.gba", "Pokemon Orange Islands.srm", Gen3SaveFormat.ORANGE_ISLANDS),
            Triple("Pokemon Ultra Violet (v1.22).gba", "Pokemon Ultra Violet (v1.22).sav", Gen3SaveFormat.ULTRA_VIOLET),
        )
        var ran = 0
        for ((romName, saveName, expected) in cases) {
            val rom = File(roms, romName)
            val save = File(saves, saveName)
            if (!rom.isFile || !save.isFile) continue
            ran++
            val reader = com.pokedaisy.app.companion.data.RomIdentityReader.forPath(okio.Path.Companion.run { rom.absolutePath.toPath() })
            val format = Gen3SaveReader.formatFor(reader) { rom.length() }
            assertEquals(expected, format, romName)
            val result = Gen3SaveReader.open(okio.Path.Companion.run { save.absolutePath.toPath() }, format!!)
            assertTrue(result is Gen3SaveReader.Result.Ready, (result as? Gen3SaveReader.Result.Failed)?.message)
            val data = Gen3SaveData(result.save)
            println("${format.label}: ${data.trainerName} money ${data.money} party " +
                data.party.joinToString { "${data.rules.speciesName(it.mon.species)} Lv${it.level} ${it.mon.moves.map { m -> data.rules.moveName(m) }}" })
            val ed = Gen3SaveEditor(result.save)
            ed.setMoney(4321)
            val again = (Gen3SaveFile.parse(ed.build(), format) as Gen3SaveFile.Parsed.Ok).save
            assertEquals(4321, Gen3SaveData(again).money)
            assertEquals(data.party.map { it.mon.personality }, Gen3SaveData(again).party.map { it.mon.personality })
        }
        assumeTrue("no hack ROM and save here", ran > 0)
    }

    /** A randomizer's output keeps retail's layout and species numbers, but its learnsets and abilities are its own: read
     * from its ROM. A retail dump keeps retail's generated tables. */
    @Test fun aRandomizedCartReadsItsOwnRulesAndRetailKeepsItsTables() {
        val randomized = File(roms, "Pokemon Emerald Randomized (v4.6.1) (seed 13578136444066).gba")
        val retail = File(roms, "Pokemon - Emerald Version (USA, Europe).gba")
        assumeTrue("needs the randomized and retail Emerald ROMs", randomized.isFile && retail.isFile)
        fun reader(f: File) = com.pokedaisy.app.companion.data.RomIdentityReader.forPath(okio.Path.Companion.run { f.absolutePath.toPath() })
        val r = Gen3SaveReader.rulesFor(reader(randomized), Gen3SaveFormat.EMERALD, randomized.path)
        assertTrue(r is RomGen3Rules, "a randomized cart reads its own ROM")
        assertEquals(RETAIL, r.family)
        val learnsDiffer = (1 until 387).count { r.learnable(it, 50) != Gen3SaveFormat.EMERALD.rules.learnable(it, 50) }
        assertTrue(learnsDiffer > 100, "randomized learnsets: $learnsDiffer species differ")
        assertTrue(Gen3SaveReader.rulesFor(reader(retail), Gen3SaveFormat.EMERALD, retail.path) !is RomGen3Rules, "retail keeps its tables")
    }
}
