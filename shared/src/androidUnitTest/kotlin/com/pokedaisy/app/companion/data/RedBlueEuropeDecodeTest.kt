package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * French, Italian and Spanish Red and Blue on the German Red save with each cart's own map and tileset headers written in
 * (the red_fr .. blue_es fixtures' READMEs): German Red's RAM, the European Yellows' names.
 */
class RedBlueEuropeDecodeTest {
    private class Cart(
        val key: String, val game: GameKind, val cfg: Gen1Config, val art: Gen1Art.Gen1ArtTables, val field: GbField.Gen1, val rom: String,
        val party: List<String>, val vermilion: String, val shipTicket: String, val moves: List<String>, val foe: String, val foeHp: Int,
        val letters: String,
    )

    private val fr = listOf("PIKACHU", "SABLAIREAU", "RONFLEX", "LOKHLASS", "ROUCARNAGE", "RATTATAC")
    private val itEs = listOf("PIKACHU", "SANDSLASH", "SNORLAX", "LAPRAS", "PIDGEOT", "RATICATE")
    private val frMoves = listOf("TONNERRE", "FATAL-FOUDRE", "SOUPLESSE", "MUR LUMIERE")
    private val itMoves = listOf("FULMINE", "TUONO", "SCHIANTO", "SCHERMO-LUCE")
    private val esMoves = listOf("RAYO", "TRUENO", "PORTAZO", "PANTALLA LUZ")

    private val carts = listOf(
        Cart("red_fr", GameKind.RED, GEN1_RED_FR, Gen1Art.RED_FR, GB_FIELD_RED_BLUE_FR, "Pokemon - Version Rouge (France) (SGB Enhanced).gb",
            fr, "CARMIN SUR MER", "PASSE BATEAU", frMoves, "SALAMECHE", 31, "àèéùçâêîôû"),
        Cart("blue_fr", GameKind.BLUE, GEN1_BLUE_FR, Gen1Art.BLUE_FR, GB_FIELD_RED_BLUE_FR, "Pokemon - Version Bleue (France) (SGB Enhanced).gb",
            fr, "CARMIN SUR MER", "PASSE BATEAU", frMoves, "SALAMECHE", 33, "àèéùçâêîôû"),
        Cart("red_it", GameKind.RED, GEN1_RED_IT, Gen1Art.RED_IT, GB_FIELD_RED_BLUE_IT, "Pokemon - Versione Rossa (Italy) (SGB Enhanced).gb",
            itEs, "ARANCIOPOLI", "BIGL. NAVE", itMoves, "CHARMANDER", 31, "àèéìòù"),
        Cart("blue_it", GameKind.BLUE, GEN1_BLUE_IT, Gen1Art.BLUE_IT, GB_FIELD_RED_BLUE_IT, "Pokemon - Versione Blu (Italy) (SGB Enhanced).gb",
            itEs, "ARANCIOPOLI", "BIGL. NAVE", itMoves, "CHARMANDER", 34, "àèéìòù"),
        Cart("red_es", GameKind.RED, GEN1_RED_ES, Gen1Art.RED_ES, GB_FIELD_RED_BLUE_ES, "Pokemon - Edicion Roja (Spain) (SGB Enhanced).gb",
            itEs, "CIUDAD CARMÍN", "TICKET BARCO", esMoves, "CHARMANDER", 32, "ÁÉÍÑÓÚáéíñóú¿¡"),
        Cart("blue_es", GameKind.BLUE, GEN1_BLUE_ES, Gen1Art.BLUE_ES, GB_FIELD_RED_BLUE_ES, "Pokemon - Edicion Azul (Spain) (SGB Enhanced).gb",
            itEs, "CIUDAD CARMÍN", "TICKET BARCO", esMoves, "CHARMANDER", 31, "ÁÉÍÑÓÚáéíñóú¿¡"),
    )

    private fun use(c: Cart) {
        activeGame = c.game
        romLanguage = c.cfg.language
        romGameCode = c.cfg.gameCode
    }

    @After fun tearDown() {
        romLanguage = 'E'
        romGameCode = ""
    }

    private fun load(fixture: String) = FixtureMemoryReader.load(fixture)

    @Test
    fun `party, money, bag and place - in each cart's own names`() = carts.forEach { c ->
        use(c)
        val t = readGen1Telemetry(load(c.key), c.cfg)
        assertFalse(c.key, t.inBattle)
        assertEquals(c.key, c.party, t.party.map { speciesName(it.species) })
        assertEquals(c.key, listOf("Joseph", "Liam", "Fred", "Henry", "Seth", "Richard"), t.party.map { it.nickname })
        assertEquals(c.key, listOf(182, 274, 450, 382, 300, 236), t.party.map { it.hp })
        assertEquals(c.key, c.moves, t.party[0].moves.map { lookupMove(it).name })
        assertEquals(c.key, 999999L, t.money)
        assertEquals(c.key, 17, t.items.size)
        assertEquals(c.key, c.shipTicket, itemName(t.items[1].itemId))
        assertEquals(c.key, 0x59, t.regionMapSectionId)
        assertEquals(c.key, c.vermilion, lookupLocation(t.regionMapSectionId).mapSecName)
        assertEquals(c.key, 10 to 106, t.pokedex!!.caught.size to t.pokedex!!.seen.size)
        assertEquals(c.key, "", itemDescription(20))
    }

    @Test
    fun `battle - both battlers and the menus`() = carts.forEach { c ->
        use(c)
        val battle = load("${c.key}_battle")
        val t = readGen1Telemetry(battle, c.cfg)
        assertTrue(c.key, t.inBattle)
        assertEquals(c.key, 25 to 100, t.battleMons[BATTLE_POS_PLAYER_LEFT].let { it.species to it.level })
        val foe = t.battleMons[BATTLE_POS_OPPONENT_LEFT]
        assertEquals(c.key, c.foe, speciesName(foe.species))
        assertEquals(c.key, 12 to c.foeHp, foe.level to foe.hp)
        assertEquals(c.key, BATTLE_INPUT_MOVE_SELECT to 0, readGen1BattleInput(battle, c.cfg))
        // The battle menu, cursor on PkMn: column 1, row 0, at each language's own column.
        assertEquals(c.key, BATTLE_INPUT_ACTION_SELECT to 1, readGen1BattleInput(load("${c.key}_battle_menu"), c.cfg))
        assertEquals(c.key, BATTLE_INPUT_NONE to -1, readGen1BattleInput(load(c.key), c.cfg))
    }

    @Test
    fun `each cart's field config, by language`() = carts.forEach { c ->
        use(c)
        assertTrue(c.key, GbField.forGame(c.game) === c.field)
    }

    private fun romOf(c: Cart): ByteArray? = File(GB_DIR + c.rom).takeIf { it.isFile }?.readBytes()
    private val english: ByteArray? get() = listOfNotNull(
        File(GB_DIR + "Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb"),
        System.getenv("DECOMPS")?.let { File(it, "pokered/pokered.gbc") },
    ).firstOrNull { it.isFile }?.readBytes()

    @Test
    fun `dex pages - metric, their own text, Red's pictures`() {
        val en = english
        assumeTrue("Red ROMs not on this machine", en != null)
        assumeTrue("French / Italian / Spanish Red not on this machine", carts.any { romOf(it) != null })
        for (c in carts) {
            val rom = romOf(c) ?: continue
            use(c)
            Gen1Dex.rom = rom
            val t = c.cfg.pokedex!!.gen1!!
            val bulbasaur = Gen1Dex.entry(t, 1)!!
            assertEquals(c.key, 7 to 69, bulbasaur.heightDm to bulbasaur.weightHg)
            assertNull(c.key, bulbasaur.heightIn)
            assertTrue(c.key, bulbasaur.description.length > 20)
            assertTrue(c.key, Gen1Dex.entry(t, 151)!!.description.length > 20)
            val pictures = (1..151).map { Gen1Dex.frontPixels(t, it)!! }
            Gen1Dex.rom = en
            for (dex in 1..151) assertArrayEquals("${c.key} $dex", Gen1Dex.frontPixels(POKEDEX_RED.gen1!!, dex), pictures[dex - 1])
        }
        Gen1Dex.rom = null
    }

    @Test
    fun `art - Red's icons, town map and player, a font with their letters`() {
        val en = english
        assumeTrue("Red ROMs not on this machine", en != null)
        assumeTrue("French / Italian / Spanish Red not on this machine", carts.any { romOf(it) != null })
        val enIcons = Gen1Art.icons(en!!, Gen1Art.RED)
        val enMap = Gen1Art.townMap(en, Gen1Art.RED)
        val enPlayer = Gen1Art.player(en, Gen1Art.RED)
        for (c in carts) {
            val rom = romOf(c) ?: continue
            val icons = Gen1Art.icons(rom, c.art)
            for (dex in 1..151) assertArrayEquals("${c.key} icon $dex", enIcons[dex]!!.argb, icons[dex]!!.argb)
            assertArrayEquals(c.key, enMap.argb, Gen1Art.townMap(rom, c.art).argb)
            assertArrayEquals(c.key, enPlayer.argb, Gen1Art.player(rom, c.art).argb)
            val fontClass = Class.forName("java.awt.Font")
            val font = fontClass.getMethod("createFont", Int::class.java, java.io.InputStream::class.java)
                .invoke(null, 0, Gen1Art.font(rom, c.art).inputStream())
            val canDisplay = fontClass.getMethod("canDisplay", Char::class.java)
            for (ch in "PIKACHU 182/182 ${c.letters}") assertTrue("${c.key} glyph $ch", canDisplay.invoke(font, ch) as Boolean)
        }
    }

    private companion object {
        val GB_DIR = File(RETAIL_ROM_DIR).parentFile!!.path + "/gbc/"
    }
}
