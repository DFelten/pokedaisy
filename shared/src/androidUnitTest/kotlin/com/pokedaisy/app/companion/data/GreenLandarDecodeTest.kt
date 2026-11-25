package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Japanese Green v1.0 in LandaR's English translation, on a save played in it headless (the green_landar* fixtures'
 * READMEs): the Japanese cart's RAM (6-byte names), English's ids and names, the translation's font for nicknames and the
 * Japanese battle menu (ITEM top right, POKéMON bottom left, the moves two rows apart).
 */
class GreenLandarDecodeTest {
    private val cfg = GEN1_GREEN_LANDAR

    @Before fun setUp() {
        activeGame = GameKind.GREEN_JP
        romLanguage = cfg.language
        romGameCode = cfg.gameCode
    }

    @After fun tearDown() {
        romLanguage = 'E'
        romGameCode = ""
    }

    private fun load(fixture: String) = FixtureMemoryReader.load(fixture)

    @Test
    fun `party, money, bag and place`() {
        val t = readGen1Telemetry(load("green_landar"), cfg)
        assertFalse(t.inBattle)
        assertEquals(listOf("BULBASAUR", "CHARMANDER", "SQUIRTLE", "PIKACHU", "PIDGEY", "RATTATA"), t.party.map { speciesName(it.species) })
        assertEquals(listOf("Bulba", "Char", "Squi", "Pikac", "Pid", "Ratt"), t.party.map { it.nickname })
        assertEquals(listOf(5, 6, 7, 8, 4, 3), t.party.map { it.level })
        assertEquals(listOf(20, 20, 23, 24, 17), t.party.take(5).map { it.hp })
        assertEquals(listOf("TACKLE", "GROWL"), t.party[0].moves.filter { it != 0 }.map { lookupMove(it).name })
        assertEquals(3000L, t.money)
        assertEquals(listOf(0x14 to 5, 0x04 to 10, 0x0B to 2, 0x0F to 1, 0x1D to 1), t.items.map { it.itemId to it.quantity })
        assertEquals(listOf("POTION", "POKé BALL", "ANTIDOTE"), t.items.take(3).map { itemName(it.itemId) })
        assertEquals(0x26, t.regionMapSectionId)   // REDS_HOUSE_2F
        // The save's POKéDEX says 6: the six given are seen and caught.
        assertEquals(setOf(1, 4, 7, 16, 19, 25), t.pokedex!!.caught)
        assertEquals(t.pokedex!!.caught, t.pokedex!!.seen)
    }

    @Test
    fun `battle - both battlers and the Japanese menus`() {
        val battle = load("green_landar_battle_menu")
        val t = readGen1Telemetry(battle, cfg)
        assertTrue(t.inBattle)
        assertEquals(false, t.isTrainerBattle)
        val mine = t.battleMons[BATTLE_POS_PLAYER_LEFT]
        assertEquals("BULBASAUR", speciesName(mine.species))
        assertEquals(5 to 20, mine.level to mine.hp)
        val foe = t.battleMons[BATTLE_POS_OPPONENT_LEFT]
        assertEquals("PIDGEY", speciesName(foe.species))
        assertEquals(4, foe.level)
        // attk (top left), then run (bottom right) - the same cells as English's FIGHT / RUN.
        assertEquals(BATTLE_INPUT_ACTION_SELECT to 0, readGen1BattleInput(battle, cfg))
        assertEquals(BATTLE_INPUT_ACTION_SELECT to 3, readGen1BattleInput(load("green_landar_battle_menu_run"), cfg))
        // The move list (wTopMenuItemY 8, X 1, a move every second row), its cursor on TACKLE.
        assertEquals(BATTLE_INPUT_MOVE_SELECT to 0, readGen1BattleInput(load("green_landar_battle_moves"), cfg))
        assertEquals(BATTLE_INPUT_NONE to -1, readGen1BattleInput(load("green_landar"), cfg))
        // English's menu read the same RAM finds nothing: the menus sit elsewhere.
        assertEquals(BATTLE_INPUT_BUSY to -1, readGen1BattleInput(load("green_landar_battle_moves"), cfg.copy(moveMenuTop = 0x0C, moveMenuX = 5, moveMenuRowStep = 1)))
    }

    @Test
    fun `the battle menu's cells - ITEM top right, POKéMON bottom left`() {
        val s = Gen1PartySwitch(cfg)
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { s.actionCell(it) })
        assertEquals(listOf(0, 2, 1, 3), (0..3).map { Gen1PartySwitch(GEN1_RED).actionCell(it) })
    }

    @Test
    fun `the POKéMON switch - its menus, the party by key and the one out`() {
        assertEquals(GbPartyMenu(submenu = false, cursor = 0), readGen1PartyMenu(load("green_landar_battle_party"), cfg))
        assertEquals(GbPartyMenu(submenu = true, cursor = 0), readGen1PartyMenu(load("green_landar_battle_switch_menu"), cfg))
        for (f in listOf("green_landar_battle_menu", "green_landar_battle_moves", "green_landar")) assertNull(f, readGen1PartyMenu(load(f), cfg))
        val s = Gen1PartySwitch(cfg)
        val r = load("green_landar_battle_party")
        assertEquals(readGen1Telemetry(r, cfg).party.map { it.personality }, s.party(r).map { it.key })
        assertEquals(6, s.party(r).map { it.key }.toSet().size)
        assertEquals(0, s.outSlot(r))
    }

    private val rom: ByteArray? get() = File(GB_DIR + GREEN_ROM).takeIf { it.isFile }?.readBytes()
    private val english: ByteArray? get() = listOfNotNull(
        File(GB_DIR + "Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb"),
        System.getenv("DECOMPS")?.let { File(it, "pokered/pokered.gbc") },
    ).firstOrNull { it.isFile }?.readBytes()

    @Test
    fun `art - the cart's icons, town map, player and its Latin font`() {
        val rom = rom
        assumeTrue("Green (LandaR) not on this machine", rom != null)
        val icons = Gen1Art.icons(rom!!, Gen1Art.GREEN)
        assertEquals(151, (1..151).count { icons[it] != null })
        english?.let { en ->
            val enIcons = Gen1Art.icons(en, Gen1Art.RED)
            for (dex in 1..151) assertArrayEquals("icon $dex", enIcons[dex]!!.argb, icons[dex]!!.argb)
        }
        val map = Gen1Art.townMap(rom, Gen1Art.GREEN)
        assertTrue(map.argb.toSet().size >= 3)
        assertTrue(Gen1Art.player(rom, Gen1Art.GREEN).argb.toSet().size >= 3)
        val fontClass = Class.forName("java.awt.Font")
        val font = fontClass.getMethod("createFont", Int::class.java, java.io.InputStream::class.java)
            .invoke(null, 0, Gen1Art.font(rom, Gen1Art.GREEN).inputStream())
        val canDisplay = fontClass.getMethod("canDisplay", Char::class.java)
        for (c in "Bulba Pikac 0123456789 AZaz") assertTrue("glyph $c", canDisplay.invoke(font, c) as Boolean)
    }

    @Test
    fun `dex pages from the cart - metric, the text in place, only the ten translated ones with text`() {
        val rom = rom
        assumeTrue("Green (LandaR) not on this machine", rom != null)
        Gen1Dex.rom = rom
        val t = POKEDEX_GREEN_LANDAR.gen1!!
        try {
            val bulbasaur = Gen1Dex.entry(t, 1)!!
            assertEquals("Se", bulbasaur.category)   // the translation's two-letter category
            assertEquals(7 to 69, bulbasaur.heightDm to bulbasaur.weightHg)
            assertEquals("A seed was planted on Bulba at its birth", bulbasaur.description)
            assertEquals(listOf(45, 49, 49, 45, 65), bulbasaur.baseStats)
            // MEW's page is still Japanese in v1.0, which the Latin font can't show: no text, the rest read.
            val mew = Gen1Dex.entry(t, 151)!!
            assertEquals("" to "", mew.category to mew.description)
            assertEquals(listOf(100, 100, 100, 100, 100), mew.baseStats)
            val out = File("build/gen1-art-green-dex").apply { mkdirs() }
            var withText = 0
            for (s in 1..151) {
                val e = Gen1Dex.entry(t, s)
                assertNotNull("entry $s", e)
                if (e!!.description.isNotEmpty()) withText++
                val pic = Gen1Dex.frontPixels(t, s)
                assertTrue("pic $s", pic!!.count { it != 0 } > 100)
                if (s in listOf(1, 25, 41, 74, 115, 139, 151)) File(out, "$s.png").writeBytes(RomArt.Image(56, 56, pic).png())
            }
            // v1.0 translated ten pages; the rest are Japanese text the Latin font would turn into nonsense.
            assertEquals(10, withText)
        } finally {
            Gen1Dex.rom = null
        }
    }

    @Test
    fun `STEADY music - Red's songs and park, the cart's own PlayMusic`() {
        assertEquals(GEN1_MUSIC_GREEN_LANDAR, Gen1Music.forSha1(TelemetrySampler.GREEN_LANDAR_V1_0_SHA1))
        assertEquals(GEN1_MUSIC_RED.songs, GEN1_MUSIC_GREEN_LANDAR.songs)
        val rom = rom
        assumeTrue("Green (LandaR) not on this machine", rom != null)
        // English or German Red: the same PlayMusic at the same place (GEN1_MUSIC_RED_DE).
        val en = english ?: File(GB_DIR + "Pokemon - Rote Edition (Germany) (SGB Enhanced).gb").takeIf { it.isFile }?.readBytes()
        assumeTrue("Red not on this machine", en != null)
        // PlayMusic is Red's code where the config says, wNewSoundID (Red CFCC) at the Japanese RAM's CFAE.
        fun at(b: ByteArray, addr: Int, newSoundId: Int) = b.copyOfRange(addr, addr + 48).toList().windowed(2, 1)
            .map { (lo, hi) -> ((lo.toInt() and 0xFF) or ((hi.toInt() and 0xFF) shl 8)) == newSoundId }
            .let { hits -> b.copyOfRange(addr, addr + 48).mapIndexed { i, x -> if (hits.getOrElse(i) { false } || hits.getOrElse(i - 1) { false }) 0 else x.toInt() } }
        assertEquals(at(en!!, GEN1_MUSIC_RED.playMusic, 0xCFCC), at(rom!!, GEN1_MUSIC_GREEN_LANDAR.playMusic, 0xCFAE))
    }

    private companion object {
        val GB_DIR = File(RETAIL_ROM_DIR).parentFile!!.path + "/gbc/"
        const val GREEN_ROM = "Pocket Monsters - Green Version (Japan) (En) (v1.0) (LandaR).gb"
    }
}
