package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Red, Blue and Yellow's PC boxes (Gen1Boxes.kt) on the `*_boxes` fixtures, English and German (and translated Japanese Green): two Pokémon deposited into
 * BOX 1, the first box change to BOX 8 (BOX 1 into SRAM bank 2), one more deposited there - all by the game's own functions
 * (native-capture/boot/red_boxes.txt and its siblings). Then the PC's moves (Gen1BoxSwap.kt): their code in each cart, and the calls on a
 * stand-in game.
 */
class Gen1BoxesTest {
    @After
    fun tearDown() {
        romLanguage = 'E'
    }

    private val fixtures = listOf(
        Triple("red_boxes", GEN1_RED, GameKind.RED), Triple("red_de_boxes", GEN1_RED_DE, GameKind.RED),
        Triple("yellow_boxes", GEN1_YELLOW, GameKind.YELLOW), Triple("yellow_de_boxes", GEN1_YELLOW_DE, GameKind.YELLOW),
        // Japanese Green (translated): 8 boxes of 30, its 6-byte names, BOX 1 in bank 2 too.
        Triple("green_landar_boxes", GEN1_GREEN_LANDAR, GameKind.GREEN_JP),
    )

    private fun read(fixture: String, cfg: Gen1Config, game: GameKind): Telemetry {
        activeGame = game
        romLanguage = cfg.language
        return readGen1Telemetry(FixtureMemoryReader.load(fixture), cfg)
    }

    @Test
    fun `BOX 1 from SRAM bank 2, BOX 8 from WRAM, the rest empty, in every cart`() {
        for ((fixture, cfg, game) in fixtures) {
            val t = read(fixture, cfg, game)
            val boxes = t.pcBoxes
            assertNotNull(fixture, boxes)
            boxes!!
            val layout = gen1BoxLayoutFor(cfg)!!
            assertEquals(fixture, layout.numBoxes, boxes.boxes.size)
            assertEquals(fixture, 7, boxes.currentBox)
            assertEquals(fixture, "BOX1", boxes.boxes[0].name)
            assertEquals(fixture, "BOX${layout.numBoxes}", boxes.boxes.last().name)
            assertTrue(fixture, boxes.boxes.all { it.mons.size == layout.slots })
            assertEquals(fixture, setOf(0, 7), boxes.boxes.indices.filter { boxes.boxes[it].mons.any { m -> m != null } }.toSet())
            assertEquals(fixture, 2, boxes.boxes[0].mons.count { it != null })
            assertEquals(fixture, 1, boxes.boxes[7].mons.count { it != null })
            // The six of before: slots 1 and 2 in BOX 1, slot 3 in BOX 8, the party the other three.
            assertEquals(fixture, 3, t.party.size)
            val first = read(fixture.removeSuffix("_boxes"), cfg, game)
            assertTrue(fixture, first.party.size == 6)
            val before = first.party.map { it.personality }
            assertEquals(fixture, before.slice(1..2), boxes.boxes[0].mons.filterNotNull().map { it.personality })
            assertEquals(fixture, listOf(before[3]), boxes.boxes[7].mons.filterNotNull().map { it.personality })
            assertEquals(fixture, listOf(before[0], before[4], before[5]), t.party.map { it.personality })
            for (m in boxes.boxes.flatMap { it.mons }.filterNotNull()) {
                val p = first.party.first { it.personality == m.personality }
                assertEquals(fixture, p.species, m.species)
                assertEquals(fixture, p.level, m.level)
                assertEquals(fixture, p.nickname, m.nickname)
                assertEquals(fixture, p.moves.toList(), m.moves.toList())
            }
        }
    }

    @Test
    fun `boxes never changed read as empty, and the other Gen 1 carts get none`() {
        // The `red` fixture's save: BOX 1 current, bit 7 unset - banks 2 and 3 aren't read.
        val t = read("red", GEN1_RED, GameKind.RED)
        val boxes = t.pcBoxes!!
        assertEquals(0, boxes.currentBox)
        assertTrue(boxes.boxes.all { b -> b.mons.all { it == null } })
        assertNull(read("yellow_fr", GEN1_YELLOW_FR, GameKind.YELLOW).pcBoxes)
    }

    @Test
    fun `stats worked out from the cart's base stats, as the game's own WITHDRAW works them out`() {
        val rom = File(File(RETAIL_ROM_DIR).parentFile, "gbc/Pokemon - Rote Edition (Germany) (SGB Enhanced).gb")
        assumeTrue("no Rote Edition at $rom", rom.isFile)
        Gen1Dex.rom = rom.readBytes()
        try {
            val m = read("red_de_boxes", GEN1_RED_DE, GameKind.RED).pcBoxes!!.boxes[7].mons[0]!!
            // BOX 8's one Pokemon withdrawn headless from this state (MoveMon BOX_TO_PARTY + RemovePokemon): the party
            // struct the game wrote has max HP 393, 191 / 191 / 160 / 235 (CalcStats with its stat experience; the party's
            // stored stats from before the deposit were lower, worked out at its last level up) and HP 382, as kept.
            assertEquals(listOf(393, 191, 191, 160, 235, 235), m.stats!!.stats)
            assertEquals(393, m.maxHp)
            assertEquals(382, m.hp)
        } finally {
            Gen1Dex.rom = null
        }
    }

    @Test
    fun `the calls' code is in each cart, only its own`() {
        val dir = File(File(RETAIL_ROM_DIR).parentFile, "gbc")
        val carts = mapOf(
            GEN1_BOX_CALLS_RED to listOf("Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb", "Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb"),
            GEN1_BOX_CALLS_RED_DE to listOf("Pokemon - Rote Edition (Germany) (SGB Enhanced).gb", "Pokemon - Blaue Edition (Germany) (SGB Enhanced).gb"),
            GEN1_BOX_CALLS_YELLOW to listOf("Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb"),
            GEN1_BOX_CALLS_YELLOW_DE to listOf("Pokemon - Gelbe Edition - Special Pikachu Edition (Germany) (GBC,SGB Enhanced).gb"),
            GEN1_BOX_CALLS_GREEN_LANDAR to listOf("Pocket Monsters - Green Version (Japan) (En) (v1.0) (LandaR).gb"),
        )
        var seen = 0
        for ((calls, names) in carts) for (name in names) {
            val f = File(dir, name)
            if (!f.isFile) continue
            val rom = f.readBytes()
            for (other in carts.keys) {
                val ok = other.codeMatches { off, len -> rom.copyOfRange(off.toInt(), (off + len).toInt()) }
                assertEquals("$name / ${other.layout}", other === calls, ok)
            }
            seen++
        }
        assumeTrue("no Gen 1 cart in $dir", seen > 0)
    }

    @Test
    fun `each cart's field has its calls`() {
        assertTrue(GB_FIELD_RED_BLUE.pcBox === GEN1_BOX_CALLS_RED)
        assertTrue(GB_FIELD_RED_BLUE_DE.pcBox === GEN1_BOX_CALLS_RED_DE)
        assertTrue(GB_FIELD_YELLOW.pcBox === GEN1_BOX_CALLS_YELLOW)
        assertTrue(GB_FIELD_YELLOW_DE.pcBox === GEN1_BOX_CALLS_YELLOW_DE)
        assertTrue(GB_FIELD_GREEN_LANDAR.pcBox === GEN1_BOX_CALLS_GREEN_LANDAR)
        assertNull(GB_FIELD_YELLOW_FR.pcBox)
        assertNull(GB_FIELD_BROWN.pcBox)
    }

    /** A stand-in Red / Yellow: WRAM, the party and the current box, MoveMon / RemovePokemon run when the loop comes by. */
    private class Game(val calls: Gen1BoxCalls) : GameCalls {
        val ram = ByteArray(0x2000)   // C000-DFFF
        val ran = mutableListOf<Pair<Int, Int>>()
        var pending: Int? = null
        var onField = true

        operator fun get(addr: Long) = ram[(addr - 0xC000).toInt()].toInt() and 0xFF
        operator fun set(addr: Long, v: Int) { ram[(addr - 0xC000).toInt()] = v.toByte() }
        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int) = -1L
        override fun read(addr: Long, len: Int) = ByteArray(len) { get(addr + it).toByte() }
        override fun write(addr: Long, bytes: ByteArray): Boolean { bytes.forEachIndexed { i, b -> set(addr + i, b.toInt()) }; return true }
        override fun rom(off: Long, len: Int): ByteArray? = null

        override fun gbCallAt(at: Int, fn: Int, a: Int, b: Int, hl: Int): Boolean {
            if (!onField) return false
            assertEquals(calls.at, at)
            pending?.let { run(it) }
            pending = fn.takeIf { it == calls.moveMon || it == calls.removePokemon }
            return true
        }

        private val partyCount = calls.partyCount
        private val partyMons = partyCount + 8
        private val box = calls.layout.activeBox
        val party get() = (0 until this[partyCount]).map { this[partyMons + it * 44] }
        val boxed get() = (0 until this[box]).map { this[box + GEN1_BOX_MONS + it * 33] }

        private fun run(fn: Int) {
            val slot = this[calls.whichPokemon]
            val type = this[calls.moveMonType]
            ran += fn to type
            if (fn == calls.moveMon && type == 1) {         // party -> box's end
                val n = this[box]
                this[box] = n + 1; this[box + 1 + n] = this[calls.curPartySpecies]; this[box + 2 + n] = 0xFF
                write(box + GEN1_BOX_MONS + n * 33, read(partyMons + slot * 44, 33))
            } else if (fn == calls.moveMon && type == 0) {  // box -> party's end
                val n = this[partyCount]
                this[partyCount] = n + 1; this[partyCount + 1 + n] = this[calls.curPartySpecies]; this[partyCount + 2 + n] = 0xFF
                write(partyMons + n * 44, read(box + GEN1_BOX_MONS + slot * 33, 33) + ByteArray(11))
            } else if (fn == calls.removePokemon && type == 1) {
                val n = this[box]
                for (i in slot until n - 1) { this[box + 1 + i] = this[box + 2 + i]; write(box + GEN1_BOX_MONS + i * 33, read(box + GEN1_BOX_MONS + (i + 1) * 33, 33)) }
                this[box + n] = 0xFF; this[box] = n - 1
            } else if (fn == calls.removePokemon && type == 0) {
                val n = this[partyCount]
                for (i in slot until n - 1) { this[partyCount + 1 + i] = this[partyCount + 2 + i]; write(partyMons + i * 44, read(partyMons + (i + 1) * 44, 44)) }
                this[partyCount + n] = 0xFF; this[partyCount] = n - 1
            }
        }

        fun mon(species: Int, ot: Int) = ByteArray(44).also { it[0] = species.toByte(); it[12] = (ot shr 8).toByte(); it[13] = ot.toByte() }

        fun party(vararg mons: ByteArray, ots: List<String> = emptyList()) {
            this[partyCount] = mons.size
            mons.forEachIndexed { i, m -> this[partyCount + 1 + i] = m[0].toInt() and 0xFF; write(partyMons + i * 44, m) }
            this[partyCount + 1 + mons.size] = 0xFF
            ots.forEachIndexed { i, o -> write(partyMons + 6 * 44 + i * 11, o.toByteArray()) }
        }

        fun box(vararg mons: ByteArray) {
            this[box] = mons.size
            mons.forEachIndexed { i, m -> this[box + 1 + i] = m[0].toInt() and 0xFF; write(box + GEN1_BOX_MONS + i * 33, m.copyOf(33)) }
            this[box + 1 + mons.size] = 0xFF
        }

        fun partyKey(i: Int) = gen1MonKey(read(partyMons + i * 44, 44), 0)
        fun boxKey(i: Int) = gen1MonKey(read(box + GEN1_BOX_MONS + i * 33, 33), 0)
    }

    @Test
    fun `DEPOSIT - into the box, then out of the party`() {
        val g = Game(GEN1_BOX_CALLS_RED)
        g.party(g.mon(0x99, 1), g.mon(0x54, 2))
        g.box(g.mon(0x24, 3))
        assertEquals(BoxSwapResult.SWAPPED, GEN1_BOX_CALLS_RED.deposit(g, 0, g.partyKey(0)))
        assertEquals(listOf(GEN1_BOX_CALLS_RED.moveMon to 1, GEN1_BOX_CALLS_RED.removePokemon to 0), g.ran)
        assertEquals(listOf(0x54), g.party)
        assertEquals(listOf(0x24, 0x99), g.boxed)
        // The last one stays.
        assertEquals(BoxSwapResult.LAST_MON, GEN1_BOX_CALLS_RED.deposit(g, 0, g.partyKey(0)))
    }

    @Test
    fun `WITHDRAW and SWAP, refusals before any call`() {
        val c = GEN1_BOX_CALLS_RED_DE
        val g = Game(c)
        g.party(g.mon(0x99, 1))
        g.box(g.mon(0x24, 3), g.mon(0x25, 4))
        assertEquals(BoxSwapResult.CHANGED, c.withdraw(g, 1, g.boxKey(0)))
        assertEquals(BoxSwapResult.SWAPPED, c.withdraw(g, 1, g.boxKey(1)))
        assertEquals(listOf(0x99, 0x25), g.party)
        assertEquals(listOf(0x24), g.boxed)
        g.ran.clear()
        // Room in the party: the box one comes in first, so the party is never empty.
        assertEquals(BoxSwapResult.SWAPPED, c.swap(g, 0, g.boxKey(0), 0, g.partyKey(0)))
        assertEquals(listOf(c.moveMon to 0, c.removePokemon to 1, c.moveMon to 1, c.removePokemon to 0), g.ran)
        assertEquals(listOf(0x25, 0x24), g.party)
        assertEquals(listOf(0x99), g.boxed)
        g.party(*Array(6) { g.mon(0x10 + it, it) })
        assertEquals(BoxSwapResult.PARTY_FULL, c.withdraw(g, 0, g.boxKey(0)))
        g.box(*Array(20) { g.mon(0x30 + it, it) })
        assertEquals(BoxSwapResult.BOX_FULL, c.deposit(g, 0, g.partyKey(0)))
        g.onField = false
        g.box(g.mon(0x24, 3))
        assertEquals(BoxSwapResult.NOT_NOW, c.deposit(g, 0, g.partyKey(0)))
    }

    @Test
    fun `Yellow's starter PIKACHU stays where it is, another PIKACHU doesn't`() {
        val c = GEN1_BOX_CALLS_YELLOW
        val g = Game(c)
        g.write(c.playerName, "NELSON".toByteArray())
        g.write(c.playerId, byteArrayOf(0x12, 0x34))
        g.party(g.mon(0x54, 0x1234), g.mon(0x54, 0x1111), g.mon(0x99, 0x1234), ots = listOf("NELSON", "NELSON", "NELSON"))
        g.box()
        assertEquals(BoxSwapResult.STARTER_PIKACHU, c.deposit(g, 0, g.partyKey(0)))
        assertTrue(g.ran.isEmpty())
        assertEquals(BoxSwapResult.SWAPPED, c.deposit(g, 1, g.partyKey(1)))
        // Red has no starter PIKACHU.
        assertFalse(GEN1_BOX_CALLS_RED.playerId >= 0)
    }
}
