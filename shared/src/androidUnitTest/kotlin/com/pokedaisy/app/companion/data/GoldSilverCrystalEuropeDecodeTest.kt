package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * French, Italian and Spanish Crystal, Gold and Silver on the German saves under each cart's name (the crystal_fr ..
 * silver_es fixtures' READMEs): English's RAM, each cart's own names and battle menu columns; and, from the player's own
 * carts (skipped without them), their dex pages, art and field code.
 */
class GoldSilverCrystalEuropeDecodeTest {
    private class Cart(
        val key: String, val game: GameKind, val cfg: Gen2Config, val art: Gen2Art.Gen2ArtTables, val field: GbField, val rom: String,
        val party: List<String>, val moves: List<String>, val berry: String, val items: List<Pair<String, Int>>,
        val route: String, val home: String, val foe: Pair<String, Int>, val dragon: String,
    )

    private val crystal = listOf(
        Cart("crystal_fr", GameKind.CRYSTAL, GEN2_CRYSTAL_FR, Gen2Art.CRYSTAL_FR, GbField.CrystalFr, "Pokemon - Version Cristal (France).gbc",
            listOf("GERMIGNON", "ROUCOOL"), listOf("CHARGE", "RUGISSEMENT", "-", "-"), "BAIE", listOf("POTION" to 1, "POKé BALL" to 4),
            "ROUTE 29", "BOURG GEON", "ROUCOOL" to 2, "DRAGON"),
        Cart("crystal_it", GameKind.CRYSTAL, GEN2_CRYSTAL_IT, Gen2Art.CRYSTAL_IT, GbField.CrystalIt, "Pokemon - Versione Cristallo (Italy).gbc",
            listOf("CHIKORITA", "PIDGEY"), listOf("AZIONE", "RUGGITO", "-", "-"), "BACCA", listOf("POZIONE" to 1, "POKé BALL" to 4),
            "PERCORSO 29", "BORGO FOGLIANOVA", "SENTRET" to 2, "DRAGO"),
        Cart("crystal_es", GameKind.CRYSTAL, GEN2_CRYSTAL_ES, Gen2Art.CRYSTAL_ES, GbField.CrystalEs, "Pokemon - Edicion Cristal (Spain).gbc",
            listOf("CHIKORITA", "PIDGEY"), listOf("PLACAJE", "GRUÑIDO", "-", "-"), "BAYA", listOf("POCIÓN" to 1, "POKé BALL" to 4),
            "RUTA 29", "PUEBLO PRIMAVERA", "PIDGEY" to 2, "DRAGÓN"),
    )
    private val goldSilver = listOf(
        Cart("gold_fr", GameKind.GOLD, GEN2_GOLD_FR, Gen2Art.GOLD_FR, GbField.GoldFr, "Pokemon - Version Or (France) (SGB Enhanced).gbc",
            listOf("HERICENDRE", "ROUCOOL"), listOf("CHARGE", "GROZ'YEUX", "-", "-"), "BAIE", listOf("POTION" to 1, "POKé BALL" to 3),
            "ROUTE 29", "BOURG GEON", "ROUCOOL" to 3, "DRAGON"),
        Cart("silver_fr", GameKind.SILVER, GEN2_SILVER_FR, Gen2Art.SILVER_FR, GbField.SilverFr, "Pokemon - Version Argent (France) (SGB Enhanced).gbc",
            listOf("HERICENDRE", "ROUCOOL"), listOf("CHARGE", "GROZ'YEUX", "-", "-"), "BAIE", listOf("POTION" to 1, "POKé BALL" to 3),
            "ROUTE 29", "BOURG GEON", "ROUCOOL" to 3, "DRAGON"),
        Cart("gold_it", GameKind.GOLD, GEN2_GOLD_IT, Gen2Art.GOLD_IT, GbField.GoldIt, "Pokemon - Versione Oro (Italy) (SGB Enhanced).gbc",
            listOf("CYNDAQUIL", "PIDGEY"), listOf("AZIONE", "FULMISGUARDO", "-", "-"), "BACCA", listOf("POZIONE" to 1, "POKé BALL" to 3),
            "PERCORSO 29", "BORGO FOGLIANOVA", "SENTRET" to 3, "DRAGO"),
        Cart("silver_it", GameKind.SILVER, GEN2_SILVER_IT, Gen2Art.SILVER_IT, GbField.SilverIt, "Pokemon - Versione Argento (Italy) (SGB Enhanced).gbc",
            listOf("CYNDAQUIL", "PIDGEY"), listOf("AZIONE", "FULMISGUARDO", "-", "-"), "BACCA", listOf("POZIONE" to 1, "POKé BALL" to 3),
            "PERCORSO 29", "BORGO FOGLIANOVA", "SENTRET" to 3, "DRAGO"),
        Cart("gold_es", GameKind.GOLD, GEN2_GOLD_ES, Gen2Art.GOLD_ES, GbField.GoldEs, "Pokemon - Edicion Oro (Spain) (SGB Enhanced).gbc",
            listOf("CYNDAQUIL", "PIDGEY"), listOf("PLACAJE", "MALICIOSO", "-", "-"), "BAYA", listOf("POCIÓN" to 1, "POKé BALL" to 3),
            "RUTA 29", "PUEBLO PRIMAVERA", "SENTRET" to 3, "DRAGÓN"),
        Cart("silver_es", GameKind.SILVER, GEN2_SILVER_ES, Gen2Art.SILVER_ES, GbField.SilverEs, "Pokemon - Edicion Plata (Spain) (SGB Enhanced).gbc",
            listOf("CYNDAQUIL", "PIDGEY"), listOf("PLACAJE", "MALICIOSO", "-", "-"), "BAYA", listOf("POCIÓN" to 1, "POKé BALL" to 3),
            "RUTA 29", "PUEBLO PRIMAVERA", "SENTRET" to 2, "DRAGÓN"),
    )
    private val carts = crystal + goldSilver

    private fun use(c: Cart) {
        activeGame = c.game
        activeGen2 = c.cfg
        romLanguage = c.cfg.language!!
        romGameCode = c.cfg.gameCode ?: ""
    }

    @After
    fun english() {
        activeGen2 = GEN2_CRYSTAL
        romLanguage = 'E'
        romGameCode = ""
    }

    private fun load(fixture: String) = FixtureMemoryReader.load(fixture)

    @Test
    fun `party, bag and place in each cart's own words`() = carts.forEach { c ->
        use(c)
        val t = readGen2Telemetry(load(c.key), c.cfg)
        assertFalse(c.key, t.inBattle)
        assertEquals(c.key, c.party, t.party.map { speciesName(it.species) })
        val lead = t.party[0]
        val crystalCart = c.game == GameKind.CRYSTAL
        assertEquals(c.key, (if (crystalCart) 152 else 155) to 5, lead.species to lead.level)
        assertEquals(c.key, if (crystalCart) 19 to 19 else 17 to 20, lead.hp to lead.maxHp)
        assertEquals(c.key, c.moves, lead.moves.map { lookupMove(it).name })
        assertEquals(c.key, c.berry, itemName(lead.heldItem))
        assertEquals(c.key, 3000L, t.money)
        assertEquals(c.key, c.items, t.items.map { itemName(it.itemId) to it.quantity })
        assertEquals(c.key, 24 to 3, t.mapGroup to t.mapNum)
        assertEquals(c.key, c.route, lookupLocation(t.regionMapSectionId).mapSecName)
        assertEquals(c.key, c.home, lookupLocation(1).mapSecName)
        assertEquals(c.key, setOf(16, lead.species), t.pokedex!!.caught)
        // Moves keep English's types and powers under their own names.
        assertEquals(c.key, "NORMAL" to 35, typeName(lookupMove(33).type) to lookupMove(33).power)
        assertEquals(c.key, c.dragon, typeLabel("DRAGON"))
    }

    @Test
    fun `battle - both battlers, the menus at each cart's columns`() = carts.forEach { c ->
        use(c)
        val t = readGen2Telemetry(load("${c.key}_battle"), c.cfg)
        assertTrue(c.key, t.inBattle)
        assertEquals(c.key, c.party[0], speciesName(t.battleMons[BATTLE_POS_PLAYER_LEFT].species))
        val foe = t.battleMons[BATTLE_POS_OPPONENT_LEFT]
        assertEquals(c.key, c.foe, speciesName(foe.species) to foe.level)
        assertEquals(c.key, BATTLE_INPUT_MOVE_SELECT to 1, readGen2BattleInput(load("${c.key}_battle"), c.cfg))
        // The cursor on RUN, at each language's own column (German's is another).
        assertEquals(c.key, BATTLE_INPUT_ACTION_SELECT to 3, readGen2BattleInput(load("${c.key}_battle_menu"), c.cfg))
        assertTrue(c.key, readGen2BattleInput(load("${c.key}_battle_menu"), GEN2_CRYSTAL_DE).first != BATTLE_INPUT_ACTION_SELECT)
        assertEquals(c.key, BATTLE_INPUT_NONE to -1, readGen2BattleInput(load(c.key), c.cfg))
    }

    @Test
    fun `each cart's field config, by language`() = carts.forEach { c ->
        use(c)
        assertTrue(c.key, GbField.forGame(c.game) === c.field)
    }

    private fun romOf(c: Cart): ByteArray? = File(GBC_DIR, c.rom).takeIf { it.isFile }?.readBytes()
    private fun english(name: String): ByteArray? = File(GBC_DIR, name).takeIf { it.isFile }?.readBytes()

    @Test
    fun `dex pages from the cart - metric, every entry there`() {
        assumeTrue("French / Italian / Spanish carts not on this machine", carts.any { romOf(it) != null })
        for (c in carts) {
            val rom = romOf(c) ?: continue
            val dex = c.cfg.pokedex!!.gen2!!
            Gen1Dex.rom = rom
            try {
                val crystalCart = c.game == GameKind.CRYSTAL
                val e = Gen2Dex.entry(dex, if (crystalCart) 152 else 155)!!
                assertEquals(c.key, if (crystalCart) 9 to 64 else 5 to 79, e.heightDm to e.weightHg)
                assertTrue(c.key, e.category.isNotEmpty() && e.description.length > 20)
                assertEquals(c.key, if (crystalCart) listOf(45, 49, 65, 49, 65, 45) else listOf(39, 52, 43, 60, 50, 65), e.baseStats)
                for (s in 1..251) assertTrue("${c.key} entry $s", Gen2Dex.entry(dex, s) != null)
                assertTrue(c.key, Gen2Dex.itemDescription(dex, 0x12).isNotEmpty())
            } finally {
                Gen1Dex.rom = null
            }
        }
    }

    @Test
    fun `art - English's icons and town maps, found where each cart keeps them`() {
        val pairs = listOf(
            "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc" to Gen2Art.CRYSTAL,
            "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc" to Gen2Art.GOLD,
            "Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc" to Gen2Art.SILVER,
        )
        assumeTrue("French / Italian / Spanish carts not on this machine", carts.any { romOf(it) != null })
        for (c in carts) {
            val rom = romOf(c) ?: continue
            val (enName, enT) = pairs[when (c.game) { GameKind.CRYSTAL -> 0; GameKind.GOLD -> 1; else -> 2 }]
            val en = english(enName) ?: continue
            for (i in 0..1) {
                assertTrue("${c.key} town map $i", Gen2Art.townMap(rom, c.art, i)!!.argb.contentEquals(Gen2Art.townMap(en, enT, i)!!.argb))
            }
            val enIcons = Gen2Art.icons(en, enT)
            val icons = Gen2Art.icons(rom, c.art)
            assertEquals(c.key, enIcons.keys, icons.keys)
            for ((dex, img) in enIcons) assertTrue("${c.key} icon $dex", img.argb.contentEquals(icons.getValue(dex).argb))
        }
    }

    @Test
    fun `field - each cart's own config's code, and not English's or German's`() {
        assumeTrue("French / Italian / Spanish carts not on this machine", carts.any { romOf(it) != null })
        fun GbField.matches(rom: ByteArray) =
            codeMatches { off, len -> if (off + len <= rom.size) rom.copyOfRange(off.toInt(), (off + len).toInt()) else null }
        for (c in carts) {
            val rom = romOf(c) ?: continue
            assertTrue(c.key, c.field.matches(rom))
            val others = if (c.game == GameKind.CRYSTAL) listOf(GbField.Crystal, GbField.CrystalDe) else listOf(GbField.Gold, GbField.Silver, GbField.GoldDe, GbField.SilverDe)
            for (o in others) assertFalse("${c.key} ${o.javaClass.simpleName}", o.matches(rom))
            for (o in carts) if (o.field !== c.field) assertFalse("${c.key} ${o.key}", o.field.matches(rom))
        }
    }

    private companion object {
        val GBC_DIR = File(File(RETAIL_ROM_DIR).parentFile, "gbc")
    }
}
