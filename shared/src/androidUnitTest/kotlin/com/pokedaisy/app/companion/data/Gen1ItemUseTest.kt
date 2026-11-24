package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Red's, Blue's and Yellow's bag USE (Gen1ItemUse.kt): its code in the German carts (skipped without them), and every item
 * kind on a stand-in game whose RemoveItemFromInventory does what the game's does once the loop comes by again - the rules,
 * the bag, Yellow's starter Pikachu, and nothing changed when an item would have no effect or the removal can't run.
 */
class Gen1ItemUseTest {
    private val dir = File(File(RETAIL_ROM_DIR).parentFile, "gbc")

    @Test
    fun `the code is in the German Red and Blue carts, and only Red's config matches there`() {
        val carts = listOf("Pokemon - Rote Edition (Germany) (SGB Enhanced).gb", "Pokemon - Blaue Edition (Germany) (SGB Enhanced).gb")
            .map { File(dir, it) }.filter { it.isFile }
        assumeTrue("no German Red / Blue cart", carts.isNotEmpty())
        for (f in carts) {
            val rom = f.readBytes()
            fun Gen1ItemCalls.on() = codeMatches { off, len -> rom.copyOfRange(off.toInt(), (off + len).toInt()) }
            assertTrue(GEN1_ITEM_CALLS_RED_DE.on())
            assertFalse(GEN1_ITEM_CALLS_RED.on())
            assertFalse(GEN1_ITEM_CALLS_YELLOW.on())
            assertFalse(GEN1_ITEM_CALLS_YELLOW_DE.on())
        }
        assertTrue(GB_FIELD_RED_BLUE_DE.bagUse === GEN1_ITEM_CALLS_RED_DE && GB_FIELD_RED_BLUE.bagUse === GEN1_ITEM_CALLS_RED)
        assertTrue(GB_FIELD_YELLOW.bagUse === GEN1_ITEM_CALLS_YELLOW && GB_FIELD_YELLOW_DE.bagUse === GEN1_ITEM_CALLS_YELLOW_DE)
    }

    /** WRAM, a bit of cart (Moves, Yellow's happiness tables) and RemoveItemFromInventory's effect on the bag hl names. */
    private class Game(val calls: Gen1ItemCalls) : GameCalls {
        val ram = ByteArray(0x2000)
        val cart = HashMap<Long, Byte>()
        var onField = true
        var removeWorks = true
        private var pending: (() -> Unit)? = null
        val called = mutableListOf<Int>()

        init {
            fun put(off: Long, hex: String) = hex.chunked(2).forEachIndexed { i, h -> cart[off + i] = h.toInt(16).toByte() }
            // POUND 35 PP, KARATE CHOP 25 PP, DOUBLESLAP 10 PP, COMET PUNCH 15 PP
            put(0x0E * 0x4000L, "01002800ff23" + "02003200ff19" + "03000f00ff0a" + "04001200ff0f")
            calls.pikachu?.let { put(it.happinessTable, HAPPINESS) }
            val m = calls.machines
            // TM01 teaches DOUBLESLAP, TM24 (THUNDERBOLT's place) DOUBLESLAP too, HM01 COMET PUNCH.
            put(m.technicalMachines, "03"); put(m.technicalMachines + 23, "03"); put(m.technicalMachines + 50, "04")
            put(m.pokedexOrder + 0x99 - 1, "01"); put(m.pokedexOrder + 0x54 - 1, "19"); put(m.pokedexOrder + 0x15 - 1, "97")
            put(m.pokedexOrder + 0x05 - 1, "02")   // dex 2: no bits
            // BULBASAUR: TM01 and HM01 (bit 50 = byte 6, bit 2); PIKACHU: TM24; MEW: TM01.
            put(m.baseStats + 20, "01000000000004")
            put(m.baseStats + 24 * 28 + 20, "00008000000000")
            put((m.mewBaseStats ?: (m.baseStats + 150 * 28)) + 20, "01000000000000")
            put(calls.bike.bikeRidingTilesets.toLong(), "00030b0e11ff")
            this[calls.partyCount + 7] = 0xFF
            this[calls.numBagItems + 1] = 0xFF
        }

        operator fun get(addr: Long) = ram[(addr - 0xC000).toInt()].toInt() and 0xFF
        operator fun set(addr: Long, v: Int) { ram[(addr - 0xC000).toInt()] = v.toByte() }
        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int) = -1L
        override fun read(addr: Long, len: Int) = ByteArray(len) { get(addr + it).toByte() }
        override fun write(addr: Long, bytes: ByteArray): Boolean { bytes.forEachIndexed { i, b -> set(addr + i, b.toInt()) }; return true }
        override fun rom(off: Long, len: Int) = ByteArray(len) { cart[off + it] ?: 0 }

        override fun gbCallAt(at: Int, fn: Int, a: Int, b: Int, hl: Int): Boolean {
            if (!onField) return false
            assertEquals(calls.at, at)
            pending?.invoke()
            pending = null
            called += fn
            if (fn == calls.removeItem) pending = { if (removeWorks) remove(hl.toLong()) }
            else assertTrue(fn == calls.ret || fn == calls.bike.playDefaultMusic || fn == calls.bike.loadPlayerSpriteGraphics ||
                fn in Gen1GameUse.ROUTINE until Gen1GameUse.ROUTINE + 0x80)
            return true
        }

        /** RemoveItemFromInventory_: wItemQuantity off slot [wWhichPokemon], an emptied slot taken out. */
        private fun remove(inv: Long) {
            val slot = this[calls.whichPokemon]
            val at = inv + 1 + 2 * slot
            val left = this[at + 1] - this[calls.itemQuantity]
            this[at + 1] = left
            if (left != 0) return
            var i = at
            while (true) {
                val v = this[i + 2]
                this[i] = v
                if (v == 0xFF) break
                i++
            }
            this[inv] = this[inv] - 1
        }

        fun bag(vararg items: Pair<Int, Int>) {
            this[calls.numBagItems] = items.size
            items.forEachIndexed { i, (id, n) -> this[calls.numBagItems + 1 + 2 * i] = id; this[calls.numBagItems + 2 + 2 * i] = n }
            this[calls.numBagItems + 1 + 2 * items.size] = 0xFF
        }

        fun qty(id: Int) = (0 until this[calls.numBagItems]).filter { this[calls.numBagItems + 1 + 2 * it] == id }
            .sumOf { this[calls.numBagItems + 2 + 2 * it] }

        val mons get() = calls.partyCount + 8
        fun mon(slot: Int) = mons + slot * 44

        fun party(vararg hp: Pair<Int, Int>, species: Int = 0x99) {
            this[calls.partyCount] = hp.size
            hp.forEachIndexed { i, (now, max) ->
                this[calls.partyCount + 1 + i] = species
                val m = mon(i)
                this[m] = species
                this[m + 1] = now shr 8; this[m + 2] = now and 0xFF
                this[m + 0x22] = max shr 8; this[m + 0x23] = max and 0xFF
                this[m + 8] = 1; this[m + 9] = 2   // POUND, KARATE CHOP
                this[m + 0x1D] = 35; this[m + 0x1E] = 25
                this[m + 12] = 0x12; this[m + 13] = 0x34 + i   // OT ID
            }
            this[calls.partyCount + 1 + hp.size] = 0xFF
        }

        fun hp(slot: Int) = this[mon(slot) + 1] shl 8 or this[mon(slot) + 2]
    }

    private fun key(g: Game, slot: Int) = gen1MonKey(g.read(g.mons, 6 * 44), slot * 44)
    private fun on(g: Game, slot: Int) = PartyTarget(slot, key(g, slot), 0, SlotVerdict.OK)

    @Test
    fun `a POTION heals 20 up to the max HP, the bag loses one, and a full or fainted Pokemon is left alone`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(30 to 100, 95 to 100, 100 to 100, 0 to 100)
        g.bag(0x14 to 3)
        val t = calls.targets(g, 0x14)
        assertEquals(ItemUseResult.USED, t.result)
        assertEquals(listOf(SlotVerdict.OK, SlotVerdict.OK, SlotVerdict.NO_EFFECT, SlotVerdict.NO_EFFECT), t.targets.map { it.verdict })
        assertEquals(ItemUseResult.USED, calls.use(g, 0x14, on(g, 0), 0).result)
        assertEquals(50, g.hp(0))
        assertEquals(ItemUseResult.USED, calls.use(g, 0x14, on(g, 1), 0).result)
        assertEquals(100, g.hp(1))
        assertEquals(1, g.qty(0x14))
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0x14, on(g, 2), 0).result)
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0x14, on(g, 3), 0).result)
        assertEquals(1, g.qty(0x14))
        // The last one goes, and its slot with it.
        assertEquals(ItemUseResult.USED, calls.use(g, 0x14, on(g, 0), 0).result)
        assertEquals(0, g[calls.numBagItems])
        assertEquals(ItemUseResult.NONE_LEFT, calls.targets(g, 0x14).result)
    }

    @Test
    fun `MAX POTION fills HP, FULL RESTORE also clears the status and at full HP only cures it`() {
        val calls = GEN1_ITEM_CALLS_RED_DE
        val g = Game(calls)
        g.party(10 to 200, 200 to 200, 200 to 200)
        g[g.mon(0) + 4] = 0x08
        g[g.mon(1) + 4] = 0x40
        g.bag(0x11 to 1, 0x10 to 5)
        assertEquals(ItemUseResult.USED, calls.use(g, 0x11, on(g, 0), 0).result)
        assertEquals(200, g.hp(0))
        assertEquals(0x08, g[g.mon(0) + 4])
        assertEquals(0, g.qty(0x11))
        g[g.mon(0) + 2] = 50
        assertEquals(ItemUseResult.USED, calls.use(g, 0x10, on(g, 0), 0).result)
        assertEquals(200, g.hp(0))
        assertEquals(0, g[g.mon(0) + 4])
        assertEquals(ItemUseResult.USED, calls.use(g, 0x10, on(g, 1), 0).result)
        assertEquals(0, g[g.mon(1) + 4])
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0x10, on(g, 2), 0).result)
        assertEquals(3, g.qty(0x10))
    }

    @Test
    fun `status items cure only their own status, FULL HEAL any`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(50 to 100, 50 to 100)
        g[g.mon(0) + 4] = 0x03   // asleep
        g[g.mon(1) + 4] = 0x10   // burned
        g.bag(0x0B to 2, 0x0E to 1, 0x34 to 1)
        assertEquals(listOf(SlotVerdict.NO_EFFECT, SlotVerdict.NO_EFFECT), calls.targets(g, 0x0B).targets.map { it.verdict })
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0x0B, on(g, 0), 0).result)
        assertEquals(ItemUseResult.USED, calls.use(g, 0x0E, on(g, 0), 0).result)
        assertEquals(0, g[g.mon(0) + 4])
        assertEquals(ItemUseResult.USED, calls.use(g, 0x34, on(g, 1), 0).result)
        assertEquals(0, g[g.mon(1) + 4])
        assertEquals(2, g.qty(0x0B))
    }

    @Test
    fun `REVIVE gives half the max HP, MAX REVIVE all of it, only to a fainted Pokemon`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(0 to 101, 0 to 80, 5 to 80)
        g.bag(0x35 to 2, 0x36 to 1)
        assertEquals(listOf(SlotVerdict.OK, SlotVerdict.OK, SlotVerdict.NO_EFFECT), calls.targets(g, 0x35).targets.map { it.verdict })
        assertEquals(ItemUseResult.USED, calls.use(g, 0x35, on(g, 0), 0).result)
        assertEquals(50, g.hp(0))
        assertEquals(ItemUseResult.USED, calls.use(g, 0x36, on(g, 1), 0).result)
        assertEquals(80, g.hp(1))
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0x35, on(g, 2), 0).result)
        assertEquals(1, g.qty(0x35))
    }

    @Test
    fun `ETHER restores 10 on one move, MAX ELIXER every move, PP Up bits kept`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(0 to 100)
        g[g.mon(0) + 0x1D] = 0x40 or 20   // POUND: one PP Up, max 35 + 7 = 42
        g[g.mon(0) + 0x1E] = 3            // KARATE CHOP: max 25
        g.bag(0x50 to 1, 0x53 to 1)
        val t = calls.targets(g, 0x50).targets.single()
        assertEquals(SlotVerdict.OK, t.verdict)   // a fainted Pokémon's moves take PP back too
        assertEquals(listOf(true, true, false, false), t.moves)
        assertEquals(ItemUseResult.USED, calls.use(g, 0x50, on(g, 0), 0).result)
        assertEquals(0x40 or 30, g[g.mon(0) + 0x1D])
        assertEquals(3, g[g.mon(0) + 0x1E])
        assertEquals(ItemUseResult.USED, calls.use(g, 0x53, on(g, 0), 0).result)
        assertEquals(0x40 or 42, g[g.mon(0) + 0x1D])
        assertEquals(25, g[g.mon(0) + 0x1E])
        assertEquals(0, g[calls.numBagItems])
    }

    @Test
    fun `PP UP adds a PP Up and its PP, never past three`() {
        val calls = GEN1_ITEM_CALLS_YELLOW
        val g = Game(calls)
        g.party(50 to 100)
        g[g.mon(0) + 0x1D] = 0xC0 or 40
        g.bag(0x4F to 2)
        assertEquals(listOf(false, true, false, false), calls.targets(g, 0x4F).targets.single().moves)
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0x4F, on(g, 0), 0).result)
        assertEquals(ItemUseResult.USED, calls.use(g, 0x4F, on(g, 0), 1).result)
        assertEquals(0x40 or (25 + 5), g[g.mon(0) + 0x1E])
        assertEquals(1, g.qty(0x4F))
    }

    @Test
    fun `a Repel sets its steps, even over one still working, and nothing changes off the field or when the bag doesn't`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(50 to 100)
        g.bag(0x38 to 2, 0x14 to 1)
        g[calls.repelSteps] = 12
        assertEquals(ItemUseOutcome(ItemUseResult.USED, 200), calls.use(g, 0x38, null, 0))
        assertEquals(200, g[calls.repelSteps])
        assertEquals(1, g.qty(0x38))
        g.removeWorks = false
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0x38, null, 0).result)
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0x14, on(g, 0), 0).result)
        assertEquals(50, g.hp(0))
        g.removeWorks = true
        g.onField = false
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0x14, on(g, 0), 0).result)
        assertEquals(50, g.hp(0))
        assertEquals(1, g.qty(0x14))
    }

    @Test
    fun `a changed party is refused`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(50 to 100, 50 to 100)
        g.bag(0x14 to 1)
        assertEquals(ItemUseResult.PARTY_CHANGED, calls.use(g, 0x14, PartyTarget(0, key(g, 1), 0, SlotVerdict.OK), 0).result)
        assertEquals(ItemUseResult.PARTY_CHANGED, calls.use(g, 0x14, PartyTarget(4, key(g, 0), 0, SlotVerdict.OK), 0).result)
        assertEquals(1, g.qty(0x14))
    }

    @Test
    fun `Yellow's starter Pikachu gets its happiness and mood from medicine, and follows again once revived`() {
        val calls = GEN1_ITEM_CALLS_YELLOW
        val pika = calls.pikachu!!
        val g = Game(calls)
        g.party(10 to 60, 0 to 60, species = 0x54)
        // The player: ID 1234, name "ASH" + terminator; slot 0 is theirs, slot 1 another trainer's PIKACHU.
        g[pika.playerId] = 0x12; g[pika.playerId + 1] = 0x34
        "ASH".forEachIndexed { i, c -> g[pika.playerName + i] = c.code + 0x3F }; g[pika.playerName + 3] = 0x50
        val ot = g.mons + 6 * 44
        for (i in 0 until 5) g[ot + i] = g[pika.playerName + i]
        g[pika.happiness] = 150
        g[pika.mood] = 0x80
        g.bag(0x14 to 1, 0x35 to 1)
        assertEquals(ItemUseResult.USED, calls.use(g, 0x14, on(g, 0), 0).result)
        assertEquals(153, g[pika.happiness])
        assertEquals(0x83, g[pika.mood])
        // Slot 1's OT ID is 1235: not the starter, and a REVIVE isn't an item up to CALCIUM anyway.
        assertEquals(ItemUseResult.USED, calls.use(g, 0x35, on(g, 1), 0).result)
        assertEquals(153, g[pika.happiness])
        assertEquals(0, g[pika.spawnState])
        // The starter fainted and revived: it follows again.
        g[g.mon(0) + 1] = 0; g[g.mon(0) + 2] = 0
        g.bag(0x35 to 1)
        assertEquals(ItemUseResult.USED, calls.use(g, 0x35, on(g, 0), 0).result)
        assertEquals(3, g[pika.spawnState])
        assertEquals(153, g[pika.happiness])
    }

    @Test
    fun `the uses are the ones the bag offers`() {
        val calls = GEN1_ITEM_CALLS_RED
        for (id in listOf(0x14, 0x10, 0x0B, 0x35, 0x50, 0x52, 0x4F)) assertTrue(calls.usable(id) && calls.picksMon(id))
        for (id in listOf(0x1E, 0x38, 0x39)) assertTrue(calls.usable(id) && !calls.picksMon(id))
        // RARE CANDY, HP UP, MOON STONE, ESCAPE ROPE, OLD ROD: the game's own UseItem, no party pick here.
        for (id in listOf(0x28, 0x23, 0x0A, 0x1D, 0x4C)) assertTrue(calls.usable(id) && calls.inGame(id) && !calls.picksMon(id))
        assertFalse(calls.inGame(0x14) || calls.inGame(0x06) || calls.inGame(0xC9))
        assertTrue(calls.picksMove(0x50) && calls.picksMove(0x4F) && !calls.picksMove(0x52))
        assertTrue(calls.usable(0x06) && !calls.picksMon(0x06))   // BICYCLE: on the spot
        for (id in listOf(0xC4, 0xC8, 0xC9, 0xFA)) assertTrue(calls.usable(id) && calls.picksMon(id) && calls.picksMove(id))
        assertEquals(listOf(50, 54, 0, 49, -1, -1), listOf(0xC4, 0xC8, 0xC9, 0xFA, 0xC3, 0xFB).map { Gen1ItemCalls.tmIndex(it) })
    }

    @Test
    fun `the game's own UseItem routine, German Red's bytes as run headless, its closing lowest`() {
        val (entry, code) = GEN1_ITEM_CALLS_RED_DE.game.routine(0x20, 1, GEN1_ITEM_CALLS_RED_DE.whichPokemon, GEN1_ITEM_CALLS_RED_DE.bike.statusFlags6)
        assertEquals(0xDF57, entry)
        assertEquals("fa5ed0a7c0fa37d7e6182006cdee3dcdd83d3e90e0b0cdba20afe0ba0605215b78cdf03521c9cfcb86fa37d7cb5fcc9709cdaa0c3e01ea" +
            "d0cfcd2924c9060121b670cdf035afea57d13e20ea96cf3e01ea97cf211adfe5c3d630", code.joinToString("") { "%02x".format(it) })
        // Yellow's InitMapSprites is a home homecall: called directly, no Bankswitch.
        val (yEntry, yCode) = GEN1_ITEM_CALLS_YELLOW_DE.game.routine(0x28, 2, GEN1_ITEM_CALLS_YELLOW_DE.whichPokemon, GEN1_ITEM_CALLS_YELLOW_DE.bike.statusFlags6)
        assertEquals(0xDF52, yEntry)
        assertEquals(85, yCode.size)
        // Every routine stays under the deepest stack seen (0xDF91), its closing (all that outlasts UseItem) well under it.
        for (c in listOf(GEN1_ITEM_CALLS_RED, GEN1_ITEM_CALLS_RED_DE, GEN1_ITEM_CALLS_YELLOW, GEN1_ITEM_CALLS_YELLOW_DE)) {
            val (e, b) = c.game.routine(0x28, 19, c.whichPokemon, c.bike.statusFlags6)
            assertTrue(e < 0xDF60 && Gen1GameUse.ROUTINE + b.size < 0xDF80)
        }
    }

    @Test
    fun `an item the game runs is handed over, the routine written once the call is set up, nothing spent here`() {
        val g = Game(GEN1_ITEM_CALLS_RED_DE)
        g.party(30 to 50)
        g.bag(0x14 to 1, 0x28 to 2)
        val out = GEN1_ITEM_CALLS_RED_DE.use(g, 0x28, null, 0)
        assertEquals(ItemUseResult.IN_GAME, out.result)
        val (entry, code) = GEN1_ITEM_CALLS_RED_DE.game.routine(0x28, 1, GEN1_ITEM_CALLS_RED_DE.whichPokemon, GEN1_ITEM_CALLS_RED_DE.bike.statusFlags6)
        assertEquals(listOf(entry), g.called)
        assertTrue(g.read(Gen1GameUse.ROUTINE.toLong(), code.size).contentEquals(code))
        assertEquals(2, g.qty(0x28))
        assertEquals(ItemUseResult.NONE_LEFT, GEN1_ITEM_CALLS_RED_DE.use(g, 0x0A, null, 0).result)
        g.onField = false
        assertEquals(ItemUseResult.FAILED, GEN1_ITEM_CALLS_RED_DE.use(g, 0x28, null, 0).result)
    }

    @Test
    fun `a TM teaches its move by the base data's bit, into an empty slot with the move's PP, and is spent`() {
        val calls = GEN1_ITEM_CALLS_RED_DE
        val g = Game(calls)
        g.party(50 to 100, 50 to 100, 50 to 100)
        g[g.mon(1)] = 0x05            // a species without TM01's bit
        g[g.mon(2) + 9] = 3           // knows DOUBLESLAP already
        g.bag(0xC9 to 2)
        val t = calls.targets(g, 0xC9)
        assertEquals(3, t.move)
        assertEquals(listOf(SlotVerdict.OK, SlotVerdict.NO_EFFECT, SlotVerdict.NO_EFFECT), t.targets.map { it.verdict })
        assertTrue(t.targets.all { it.moves.isEmpty() })   // a free slot: nothing to forget
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0xC9, on(g, 1), 0).result)
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0xC9, on(g, 2), 0).result)
        assertEquals(2, g.qty(0xC9))
        assertEquals(ItemUseResult.USED, calls.use(g, 0xC9, on(g, 0), 0).result)
        assertEquals(listOf(1, 2, 3, 0), (0 until 4).map { g[g.mon(0) + 8 + it] })
        assertEquals(10, g[g.mon(0) + 0x1D + 2])
        assertEquals(1, g.qty(0xC9))
        // Not on the field: nothing taught, nothing spent.
        g[g.mon(0) + 10] = 0
        g.onField = false
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0xC9, on(g, 0), 0).result)
        assertEquals(0, g[g.mon(0) + 10])
        assertEquals(1, g.qty(0xC9))
    }

    @Test
    fun `with four moves a TM replaces the one picked, never an HM's, and an HM stays in the bag`() {
        val calls = GEN1_ITEM_CALLS_RED
        val g = Game(calls)
        g.party(50 to 100)
        val m = g.mon(0)
        g[m + 8] = 1; g[m + 9] = 4; g[m + 10] = 2; g[m + 11] = 2   // POUND, COMET PUNCH (HM01's), KARATE CHOP x2
        g[m + 0x1D + 2] = 0xC5   // three PP Ups, 5 PP
        g.bag(0xC9 to 1, 0xC4 to 1)
        val t = calls.targets(g, 0xC9)
        assertEquals(listOf(true, false, true, true), t.targets[0].moves)
        assertEquals(ItemUseResult.NO_EFFECT, calls.use(g, 0xC9, on(g, 0), 1).result)
        assertEquals(ItemUseResult.USED, calls.use(g, 0xC9, on(g, 0), 2).result)
        assertEquals(3, g[m + 10])
        assertEquals(10, g[m + 0x1D + 2])   // the move's PP, the PP Ups gone
        assertEquals(0, g.qty(0xC9))
        // HM01: it knows COMET PUNCH already.
        assertEquals(SlotVerdict.NO_EFFECT, calls.targets(g, 0xC4).targets[0].verdict)
        g[m + 9] = 0
        assertEquals(ItemUseResult.USED, calls.use(g, 0xC4, on(g, 0), 0).result)
        assertEquals(4, g[m + 9])
        assertEquals(15, g[m + 0x1D + 1])
        assertEquals(1, g.qty(0xC4))
    }

    @Test
    fun `Red's MEW learns by MewBaseStats, Yellow's starter Pikachu is glad of THUNDERBOLT`() {
        val red = GEN1_ITEM_CALLS_RED
        val g = Game(red)
        g.party(50 to 100, species = 0x15)
        g.bag(0xC9 to 1)
        assertEquals(SlotVerdict.OK, red.targets(g, 0xC9).targets[0].verdict)

        val calls = GEN1_ITEM_CALLS_YELLOW
        val y = Game(calls)
        val pika = calls.pikachu!!
        y.party(50 to 100, species = 0x54)
        y[y.mon(0) + 12] = 0x12; y[y.mon(0) + 13] = 0x34
        y[pika.playerId] = 0x12; y[pika.playerId + 1] = 0x34
        for (i in 0 until 5) { y[pika.playerName + i] = 0x80 + i; y[y.mons + 6 * 44 + i] = 0x80 + i }
        y[pika.happiness] = 90
        y.bag(0xE0 to 1)
        assertEquals(ItemUseResult.USED, calls.use(y, 0xE0, on(y, 0), 0).result)
        assertEquals(3, y[y.mon(0) + 10])
        assertEquals(91, y[pika.happiness])   // PIKAHAPPY_USEDTMHM's row: +1 under 100
        assertEquals(5, y[pika.emotionModifier])
        assertEquals(0x85, y[pika.mood])
    }

    @Test
    fun `the BICYCLE gets on where the game allows it and off anywhere, with the music and the sprite`() {
        val calls = GEN1_ITEM_CALLS_YELLOW_DE
        val b = calls.bike
        val g = Game(calls)
        g.bag(0x06 to 1)
        g[b.curMap] = 0x00; g[b.curMapTileset] = 0x0B   // a tileset in BikeRidingTilesets
        assertEquals(ItemUseResult.USED, calls.use(g, 0x06, null, 0).result)
        assertEquals(1, g[b.walkBikeSurfState])
        assertTrue(b.playDefaultMusic in g.called && b.loadPlayerSpriteGraphics in g.called)
        assertEquals(1, g.qty(0x06))
        g[calls.pikachu!!.spawnState] = 7
        assertEquals(ItemUseResult.USED, calls.use(g, 0x06, null, 0).result)
        assertEquals(0, g[b.walkBikeSurfState])
        assertEquals(0, g[calls.pikachu!!.spawnState])
        // Indoors: no. ROUTE 23: yes.
        g[b.curMapTileset] = 0x05
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0x06, null, 0).result)
        assertEquals(0, g[b.walkBikeSurfState])
        g[b.curMap] = 0x22
        assertEquals(ItemUseResult.USED, calls.use(g, 0x06, null, 0).result)
        assertEquals(1, g[b.walkBikeSurfState])
        // Cycling Road holds the player on it; surfing, no bike.
        g[b.statusFlags6] = 1 shl 5
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0x06, null, 0).result)
        assertEquals(1, g[b.walkBikeSurfState])
        g[b.walkBikeSurfState] = 2
        assertEquals(ItemUseResult.FAILED, calls.use(g, 0x06, null, 0).result)
        assertEquals(2, g[b.walkBikeSurfState])
        g.bag()
        g[b.walkBikeSurfState] = 0
        assertEquals(ItemUseResult.NONE_LEFT, calls.use(g, 0x06, null, 0).result)
    }

    private companion object {
        const val HAPPINESS = "050302050302010100030201010100020101fdfdfbfffffffbfbf6fbfbf6f6f6ec8a8380809480626c626c00"
    }
}
