package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * MWisBest's "32-bit RTC + Timer RNG Fix" v1.0 of retail Emerald (USA, Europe): retail byte for byte but 76 bytes (AgbMain
 * calls a new routine, one callback pointer, the routine in retail's zero space), read as retail Emerald
 * (NATIVE_EMERALD_RETAIL). The fixtures are the user's own save on it (`emerald_mwisbest*`, their READMEs); the ROM checks
 * skip where the ROMs aren't on this machine.
 */
class MWisBestDecodeTest {
    private val romName = "Pokemon - Emerald Version (USA, Europe) (32-bit RTC+TIMER RNG Fix) (v1.0) (MWisBest).gba"
    private val retailName = "Pokemon - Emerald Version (USA, Europe).gba"

    @Before fun emerald() {
        activeGame = GameKind.EMERALD
        romGameCode = ""
    }

    @Test
    fun `it is supported, as retail Emerald`() {
        assertTrue(TelemetrySampler.EMERALD_MWISBEST_V1_0_SHA1 in TelemetrySampler.SUPPORTED_HACK_SHA1S)
        // EXPAND and the field reads go by retail Emerald's CRC; nothing else is mapped.
        assertEquals(0x1f1c08fb, TelemetrySampler.retailTablesCrc(0x67c39b5a))
        assertEquals(null, TelemetrySampler.retailTablesCrc(0x1f1c08fb))
    }

    @Test
    fun `the field decodes - Route 101, MUDKIP, the bag and money`() {
        val t = decodeNative("emerald_mwisbest", NATIVE_EMERALD_RETAIL)
        assertEquals(listOf("Mudkip" to 7), t.party.map { activeSpeciesNames[it.species] to it.level })
        assertEquals(listOf("TACKLE", "GROWL", "MUD SLAP"), t.party[0].moves.filter { it != 0 }.map { lookupMove(it).name })
        assertEquals(listOf("Potion" to 2, "Poke Ball" to 5), t.items.map { activeItemNames[it.itemId] to it.quantity })
        assertPocketsInRange(t)
        assertEquals(Triple(0, 16, 16), Triple(t.mapGroup, t.mapNum, t.regionMapSectionId))
        assertEquals(3300L, readMoney(FixtureMemoryReader.load("emerald_mwisbest"), NATIVE_EMERALD_RETAIL))
        assertFalse(t.inBattle)
    }

    @Test
    fun `a wild battle decodes, stopped at the action menu with FIGHT on the cursor`() {
        val ram = FixtureMemoryReader.load("emerald_mwisbest_battle")
        val t = readNativeTelemetry(ram, NATIVE_EMERALD_RETAIL)
        assertTrue(t.inBattle)
        assertEquals("Mudkip" to 7, activeSpeciesNames[t.battleMons[BATTLE_POS_PLAYER_LEFT].species] to t.battleMons[BATTLE_POS_PLAYER_LEFT].level)
        assertEquals("Pikachu" to 5, activeSpeciesNames[t.battleMons[BATTLE_POS_OPPONENT_LEFT].species] to t.battleMons[BATTLE_POS_OPPONENT_LEFT].level)
        assertEquals(0, ram.readCoreMemory(NATIVE_EMERALD_RETAIL.actionCursor, 1)[0].toInt())
    }

    @Test
    fun `its ROM keeps every function the field features call, and their sites`() {
        val b = File(RETAIL_ROM_DIR + romName).takeIf { it.isFile }?.readBytes()
        assumeTrue("MWisBest's Emerald isn't here", b != null)
        val read = { addr: Long, len: Int -> b!!.copyOfRange((addr - 0x08000000).toInt(), (addr - 0x08000000).toInt() + len) }
        val fns = fieldItemsFor("BPEE", 0)!!
        assertTrue(fieldItemCodeMatches(fns, read))
        assertTrue(flyCodeMatches(fns.fly!!, read))
        assertTrue(pcCodeMatches(fns.pc!!, read))
        assertTrue(tradeEvoCodeMatches(TRADE_EVO_EMERALD, read))
        assertTrue(tmCodeMatches(tmFnsFor(GameKind.EMERALD, "BPEE", 0, hack = false)!!, read))
        assertTrue(flashCodeMatches(flashFnsFor(GameKind.EMERALD, "BPEE", 0, hack = false)!!, read))
        // The tables the DEX and GUIDE check, as the Poller does.
        val rom = RomFileReader.load(RETAIL_ROM_DIR + romName)!!
        assertTrue(pokedexMatchesRom(rom, NATIVE_EMERALD_RETAIL.pokedex!!))
        assertTrue(guideTablesMatchRom(rom, NATIVE_EMERALD_RETAIL.guideTables!!))
        // WILD ENCOUNTERS' switch, REUSABLE TMS' and INSTANT TEXT's sites: found by their code, where retail's are.
        val retail = File(RETAIL_ROM_DIR + retailName).takeIf { it.isFile }?.toOkioPath() ?: return
        val hack = File(RETAIL_ROM_DIR + romName).toOkioPath()
        assertNotEquals(0L, WildEncounters.find(hack))
        assertEquals(WildEncounters.find(retail), WildEncounters.find(hack))
        assertEquals(ReusableTms.find(retail).map { it.at }, ReusableTms.find(hack).map { it.at })
        val text = { x: okio.Path -> InstantText.find(x)?.writes?.map { it.addr to it.value.toList() } }
        assertTrue(text(hack) != null)
        assertEquals(text(retail), text(hack))
    }
}
