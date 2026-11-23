package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Crystal's bag USE (Gen2ItemUse.kt): its code and tables in both carts (skipped without them), and every item kind on a
 * stand-in game whose TossItem does what the game's does once the loop comes by again - the rules, the bag, and nothing
 * changed when an item would have no effect or TossItem can't run.
 */
class Gen2ItemUseTest {
    private val dir = File(File(RETAIL_ROM_DIR).parentFile, "gbc")

    @Test
    fun `the code and tables are in the English and the German cart, each only its own`() {
        val en = File(dir, "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc")
        val de = File(dir, "Pokemon - Kristall-Edition (Germany).gbc")
        assumeTrue("no Crystal cart", en.isFile || de.isFile)
        fun Gen2ItemCalls.on(f: File) = f.readBytes().let { rom -> codeMatches { off, len -> rom.copyOfRange(off.toInt(), (off + len).toInt()) } }
        if (en.isFile) {
            assertTrue(GEN2_ITEM_CALLS_CRYSTAL.on(en))
            assertFalse(GEN2_ITEM_CALLS_CRYSTAL_DE.on(en))
        }
        if (de.isFile) {
            assertTrue(GEN2_ITEM_CALLS_CRYSTAL_DE.on(de))
            assertFalse(GEN2_ITEM_CALLS_CRYSTAL.on(de))
        }
        assertTrue(GbField.Crystal.itemCalls === GEN2_ITEM_CALLS_CRYSTAL && GbField.CrystalDe.itemCalls === GEN2_ITEM_CALLS_CRYSTAL_DE)
    }

    @Test
    fun `the German cart's tables hold the same amounts as the English one's`() {
        val en = File(dir, "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc")
        val de = File(dir, "Pokemon - Kristall-Edition (Germany).gbc")
        assumeTrue("no Crystal carts", en.isFile && de.isFile)
        val e = en.readBytes()
        val d = de.readBytes()
        fun same(a: Long, b: Long, n: Int) = assertTrue(e.copyOfRange(a.toInt(), a.toInt() + n).contentEquals(d.copyOfRange(b.toInt(), b.toInt() + n)))
        same(GEN2_ITEM_CALLS_CRYSTAL.healingHp, GEN2_ITEM_CALLS_CRYSTAL_DE.healingHp, 46)
        same(GEN2_ITEM_CALLS_CRYSTAL.statusHealing, GEN2_ITEM_CALLS_CRYSTAL_DE.statusHealing, 43)
        same(GEN2_ITEM_CALLS_CRYSTAL.moves, GEN2_ITEM_CALLS_CRYSTAL_DE.moves, 251 * 7)
    }

    private val cfg = GEN2_CRYSTAL
    private val calls = GEN2_ITEM_CALLS_CRYSTAL

    /** WRAM bank 1, a bit of cart (the two tables and two moves) and TossItem's effect on the pocket hl names. */
    private class Game(val cfg: Gen2Config, val calls: Gen2ItemCalls) : GameCalls {
        val wram = ByteArray(0x1000)
        val cart = HashMap<Long, Byte>()
        var onField = true
        var tossed = 0
        /** Where BikeFunction may put you on / off the bike (else it answers 0), and the script it queues. */
        var bikeHere = true
        var bikeCalls = 0
        private var pending: (() -> Unit)? = null
        private var reanchored = false

        init {
            fun put(off: Long, hex: String) = hex.chunked(2).forEachIndexed { i, h -> cart[off + i] = h.toInt(16).toByte() }
            put(calls.healingHp, "2e32002f3c0030500010c8001132001214000fe7030ee703486400ad0a00ae1e007932007ac8007214008b1400ff")
            put(calls.statusHealing, "09f0080af1100bf2200cf3070df44026f6ff0ef6ff7bf6ff4af0084ef4404ff22050f11054f3076df6ffff")
            put(calls.moves, "01002800ff2300" + "02003201ff1900")   // POUND 35 PP, KARATE CHOP 25 PP
        }

        /** WRAM0 (C000-CFFF): where the TM routine goes. */
        val low = HashMap<Int, Byte>()
        operator fun get(addr: Long): Int {
            val a = (addr and 0xFFFF).toInt()
            return (if (a < 0xD000) low[a] ?: 0 else wram[a - 0xD000]).toInt() and 0xFF
        }
        operator fun set(addr: Long, v: Int) {
            val a = (addr and 0xFFFF).toInt()
            if (a < 0xD000) low[a] = v.toByte() else wram[a - 0xD000] = v.toByte()
        }
        /** Calls set up through UseRegisteredItem / the TM routine, and whether a field item's effect works here. */
        val gameCalls = mutableListOf<Int>()
        var fieldWorks = true
        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int) = -1L
        override fun read(addr: Long, len: Int) = ByteArray(len) { get(addr + it).toByte() }
        override fun write(addr: Long, bytes: ByteArray): Boolean { bytes.forEachIndexed { i, b -> set(addr + i, b.toInt()) }; return true }
        override fun rom(off: Long, len: Int) = ByteArray(len) { cart[off + it] ?: 0 }

        override fun gbCallAt(at: Int, fn: Int, a: Int, b: Int, hl: Int): Boolean {
            if (!onField) return false
            assertEquals(calls.at, at)
            pending?.invoke()
            pending = null
            if (fn == calls.tossItem) pending = { toss(hl) }
            if (fn == calls.reanchorMap) reanchored = true
            if (fn == Gen2ItemCalls.ROUTINE) gameCalls += fn
            if (fn == 0x0008 && a == 0x04 && hl == calls.useRegisteredItem) { gameCalls += hl; pending = { fieldItem() } }
            if (fn == 0x0008 && a == 0x03 && hl == calls.bikeFunction) pending = { bike() }
            if (fn == 0x0008 && a == 0x03 && hl in calls.oldRodEffect..calls.oldRodEffect + 8) pending = { fish(hl - calls.oldRodEffect) }
            return true
        }

        /** UseRegisteredItem's CLOSE kind: the effect answers in wItemEffectSucceeded, a script queued when it works. */
        private fun fieldItem() {
            this[calls.fieldMoveSucceeded] = if (fieldWorks) 1 else 0
            if (fieldWorks) { this[calls.queuedScript] = 0x04; this[calls.queuedScript + 1] = 0xA8; this[calls.queuedScript + 2] = 0x65 }
        }

        var waterHere = true
        val rodsCast = mutableListOf<Int>()

        /** OldRodEffect + 4 * rod: FishFunction queues Script_GotABite facing water, 0 elsewhere. */
        private fun fish(rodOff: Int) {
            assertTrue("ReanchorMap first, as SELECT's item use does", reanchored)
            rodsCast += rodOff / 4
            this[calls.fieldMoveSucceeded] = if (waterHere) 1 else 0
            if (waterHere) { this[calls.queuedScript] = 0x03; this[calls.queuedScript + 1] = 0x35; this[calls.queuedScript + 2] = 0x50 }
        }

        /** BikeFunction: Script_GetOnBike_Register queued (SELECT's, as wUsingItemWithSelect asks), or 0 off a floor. */
        private fun bike() {
            bikeCalls++
            assertTrue("ReanchorMap first, as SELECT's item use does", reanchored)
            assertEquals(1, this[calls.usingItemWithSelect])
            this[calls.fieldMoveSucceeded] = if (bikeHere) 1 else 0
            if (bikeHere) { this[calls.queuedScript] = 0x03; this[calls.queuedScript + 1] = 0x4E; this[calls.queuedScript + 2] = 0x51 }
        }

        /** _TossItem's .remove on the pocket at [hl]: wItemQuantityChange of wCurItem, the slot gone at 0. */
        private fun toss(hl: Int) {
            val pocket = wramx(1, hl)
            assertEquals(cfg.numItems, pocket)
            val item = this[calls.curItem]
            val n = this[pocket]
            val i = (0 until n).first { this[pocket + 1 + 2 * it] == item }
            val q = this[pocket + 2 + 2 * i] - this[calls.quantityChange]
            if (q > 0) this[pocket + 2 + 2 * i] = q
            else {
                for (k in i until n) { this[pocket + 1 + 2 * k] = this[pocket + 3 + 2 * k]; this[pocket + 2 + 2 * k] = this[pocket + 4 + 2 * k] }
                this[pocket] = n - 1
            }
            tossed++
        }

        fun bag(vararg items: Pair<Int, Int>) {
            this[cfg.numItems] = items.size
            items.forEachIndexed { i, (id, q) -> this[cfg.numItems + 1 + 2 * i] = id; this[cfg.numItems + 2 + 2 * i] = q }
            this[cfg.numItems + 1 + 2 * items.size] = 0xFF
        }
        fun qty(id: Int) = (0 until this[cfg.numItems]).filter { this[cfg.numItems + 1 + 2 * it] == id }.sumOf { this[cfg.numItems + 2 + 2 * it] }

        /** Party slot [slot]: [species], [hp] of [max], [status], moves POUND / KARATE CHOP with [pp] bytes. */
        fun mon(slot: Int, species: Int = 0x9B, hp: Int = 50, max: Int = 50, status: Int = 0, pp: IntArray = intArrayOf(35, 25)) {
            this[cfg.partyCount] = maxOf(this[cfg.partyCount], slot + 1)
            this[cfg.partyCount + 1 + slot] = species
            val o = cfg.partyMons + slot * 0x30
            this[o] = if (species == 0xFD) 0x9B else species
            this[o + 2] = 1; this[o + 3] = 2
            this[o + 6] = 0x12; this[o + 7] = slot   // OT ID: tells the slots apart
            this[o + 0x17] = pp[0]; this[o + 0x18] = pp[1]
            this[o + 0x20] = status
            this[o + 0x22] = hp shr 8; this[o + 0x23] = hp and 0xFF
            this[o + 0x24] = max shr 8; this[o + 0x25] = max and 0xFF
        }
        fun hp(slot: Int) = (this[cfg.partyMons + slot * 0x30 + 0x22] shl 8) or this[cfg.partyMons + slot * 0x30 + 0x23]
        fun status(slot: Int) = this[cfg.partyMons + slot * 0x30 + 0x20]
        fun pp(slot: Int, m: Int) = this[cfg.partyMons + slot * 0x30 + 0x17 + m]
    }

    private fun game() = Game(cfg, calls)
    private fun Game.target(slot: Int) = calls.targets(this, cfg, this[cfg.numItems + 1]).targets.first { it.slot == slot }

    private val POTION = 0x12
    private val MAX_POTION = 0x0F
    private val ANTIDOTE = 0x09
    private val FULL_HEAL = 0x26
    private val FULL_RESTORE = 0x0E
    private val REVIVE = 0x27
    private val MAX_REVIVE = 0x28
    private val ETHER = 0x3F
    private val MAX_ETHER = 0x40
    private val ELIXER = 0x41
    private val REPEL = 0x14

    @Test
    fun `a Potion heals 20 up to the max HP, and the game takes it out of the bag`() {
        val g = game()
        g.bag(POTION to 2, ANTIDOTE to 1)
        g.mon(0, hp = 10); g.mon(1, hp = 45)
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, POTION, g.target(0), 0).result)
        assertEquals(30, g.hp(0))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, POTION, g.target(1), 0).result)
        assertEquals(50, g.hp(1))
        assertEquals(0, g.qty(POTION))
        assertEquals(1, g.qty(ANTIDOTE))
        assertEquals(2, g.tossed)
    }

    @Test
    fun `no effect, an egg or a changed party leave the bag and the Pokemon alone`() {
        val g = game()
        g.bag(POTION to 3, REVIVE to 1, ANTIDOTE to 1)
        g.mon(0); g.mon(1, hp = 0); g.mon(2, species = 0xFD, hp = 10)
        val t = calls.targets(g, cfg, POTION).targets
        assertEquals(listOf(SlotVerdict.NO_EFFECT, SlotVerdict.NO_EFFECT, SlotVerdict.EGG), t.map { it.verdict })
        assertEquals(ItemUseResult.NO_EFFECT, calls.useOn(g, cfg, POTION, t[0], 0).result)
        assertEquals(ItemUseResult.NO_EFFECT, calls.useOn(g, cfg, POTION, t[1], 0).result)   // fainted: REVIVE's job
        assertEquals(ItemUseResult.NO_EFFECT, calls.useOn(g, cfg, ANTIDOTE, t[0], 0).result)  // nothing to cure
        assertEquals(ItemUseResult.NO_EFFECT, calls.useOn(g, cfg, REVIVE, t[0], 0).result)   // not fainted
        assertEquals(ItemUseResult.PARTY_CHANGED, calls.useOn(g, cfg, POTION, t[0].copy(personality = 1), 0).result)
        assertEquals(0, g.tossed)
        assertEquals(3, g.qty(POTION))
    }

    @Test
    fun `status items cure only their status, FULL HEAL any`() {
        val g = game()
        g.bag(ANTIDOTE to 1, FULL_HEAL to 1)
        g.mon(0, status = 0x40)   // paralyzed
        g.mon(1, status = 0x08)   // poisoned
        assertEquals(SlotVerdict.NO_EFFECT, calls.targets(g, cfg, ANTIDOTE).targets[0].verdict)
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, ANTIDOTE, g.target(1), 0).result)
        assertEquals(0, g.status(1))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, FULL_HEAL, g.target(0), 0).result)
        assertEquals(0, g.status(0))
    }

    @Test
    fun `FULL RESTORE heals and cures, or only cures at full HP, and MAX POTION fills up`() {
        val g = game()
        g.bag(FULL_RESTORE to 2, MAX_POTION to 1)
        g.mon(0, hp = 1, max = 300, status = 0x10); g.mon(1, status = 0x20); g.mon(2, hp = 7, max = 120)
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, FULL_RESTORE, g.target(0), 0).result)
        assertEquals(300 to 0, g.hp(0) to g.status(0))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, FULL_RESTORE, g.target(1), 0).result)
        assertEquals(50 to 0, g.hp(1) to g.status(1))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, MAX_POTION, g.target(2), 0).result)
        assertEquals(120, g.hp(2))
    }

    @Test
    fun `REVIVE brings back half the max HP rounded down, MAX REVIVE all`() {
        val g = game()
        g.bag(REVIVE to 1, MAX_REVIVE to 1)
        g.mon(0, hp = 0, max = 51); g.mon(1, hp = 0, max = 51)
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, REVIVE, g.target(0), 0).result)
        assertEquals(25, g.hp(0))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, MAX_REVIVE, g.target(1), 0).result)
        assertEquals(51, g.hp(1))
    }

    @Test
    fun `PP items restore up to the max PP with the PP Ups, keeping their bits`() {
        val g = game()
        g.bag(ETHER to 1, MAX_ETHER to 1, ELIXER to 1)
        // POUND with one PP Up (35 + 7 = 42) at 20; KARATE CHOP full (25).
        g.mon(0, pp = intArrayOf(0x40 or 20, 25))
        val t = calls.targets(g, cfg, ETHER).targets[0]
        assertEquals(listOf(true, false, false, false), t.moves)
        assertEquals(ItemUseResult.NO_EFFECT, calls.useOn(g, cfg, ETHER, t, 1).result)
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, ETHER, t, 0).result)
        assertEquals(0x40 or 30, g.pp(0, 0))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, MAX_ETHER, g.target(0), 0).result)
        assertEquals(0x40 or 42, g.pp(0, 0))
        g.mon(1, pp = intArrayOf(30, 20))
        assertEquals(ItemUseResult.USED, calls.useOn(g, cfg, ELIXER, g.target(1), 0).result)
        assertEquals(35 to 25, g.pp(1, 0) to g.pp(1, 1))
    }

    @Test
    fun `a Repel works once, then waits for the last one to wear off`() {
        val g = game()
        g.bag(REPEL to 2)
        val out = calls.useRepel(g, cfg, REPEL)
        assertEquals(ItemUseResult.USED to 100, out.result to out.steps)
        assertEquals(100, g[calls.repelEffect])
        assertEquals(ItemUseResult.STILL_ACTIVE, calls.useRepel(g, cfg, REPEL).result)
        assertEquals(1, g.qty(REPEL))
    }

    @Test
    fun `off the field nothing changes, and items left out aren't offered`() {
        val g = game()
        g.bag(POTION to 1, REPEL to 1)
        g.mon(0, hp = 10)
        val t = g.target(0)
        g.onField = false
        assertEquals(ItemUseResult.FAILED, calls.useOn(g, cfg, POTION, t, 0).result)
        assertEquals(ItemUseResult.FAILED, calls.useRepel(g, cfg, REPEL).result)
        assertEquals(10 to 0, g.hp(0) to g[calls.repelEffect])
        assertEquals(1, g.qty(POTION))
        assertFalse(calls.usable(0xA7) || calls.usable(0xA8))   // NORMAL BOX, GORGEOUS BOX
        assertTrue(calls.usable(REPEL) && !calls.picksMon(REPEL) && calls.picksMove(ETHER) && !calls.picksMove(ELIXER))
        g.onField = true
        g.bag()
        assertEquals(ItemUseResult.NONE_LEFT, calls.targets(g, cfg, POTION).result)
    }

    @Test
    fun `the BICYCLE runs the game's BikeFunction and hands back the script it queued, nothing when it says not here`() {
        val g = game()
        g.bag()
        assertTrue(calls.usable(Gen2ItemCalls.BICYCLE) && !calls.picksMon(Gen2ItemCalls.BICYCLE))
        assertEquals(0x03 to 0x514E, calls.onTheSpot(g, Gen2ItemCalls.BICYCLE))
        assertEquals(0, g[calls.usingItemWithSelect])
        g.bikeHere = false
        assertEquals(null, calls.onTheSpot(g, Gen2ItemCalls.BICYCLE))
        g.onField = false
        assertEquals(null, calls.onTheSpot(g, Gen2ItemCalls.BICYCLE))
        assertEquals(2, g.bikeCalls)
    }

    @Test
    fun `a rod runs its own item effect and hands back FishFunction's script, nothing away from water`() {
        val g = game()
        g.bag()
        for (rod in Gen2ItemCalls.RODS) assertTrue(calls.usable(rod) && !calls.picksMon(rod))
        assertEquals(0x03 to 0x5035, calls.onTheSpot(g, 0x3D))
        assertEquals(0x03 to 0x5035, calls.onTheSpot(g, 0x3A))
        g.waterHere = false
        assertEquals(null, calls.onTheSpot(g, 0x3B))
        assertEquals(listOf(2, 0, 1), g.rodsCast)
        assertEquals(0, g[calls.usingItemWithSelect])
    }

    private val TM01 = 0xBF
    private val HM01 = 0xF3
    private val RARE_CANDY = 0x20
    private val COIN_CASE = 0x36
    private val ITEMFINDER = 0x37
    private val ESCAPE_ROPE = 0x13

    @Test
    fun `the TM routine is the one checked in the German cart`() {
        val de = "cdf22d3e0b21b947cffa65d2ea62d2cde234cdc03021cccf7ef5cba63e0b216a48cff1eacccfcdb92dc9"
        assertEquals(de, GEN2_ITEM_CALLS_CRYSTAL_DE.tm.routine().joinToString("") { "%02x".format(it) })
        assertEquals(42, GEN2_ITEM_CALLS_CRYSTAL.tm.routine().size)
    }

    /** TM01 (DYNAMICPUNCH) for species 0x9B only, HM01 (CUT) for both; TMHMMoves' TM01 and HMs. */
    private fun Game.tmTables() {
        cart[calls.tm.tmHmMoves] = 0xDF.toByte()
        listOf(0x0F, 0x13, 0x39, 0x46, 0x94, 0xFA, 0x7F).forEachIndexed { i, m -> cart[calls.tm.tmHmMoves + 50 + i] = m.toByte() }
        cart[calls.tm.baseData + 0x9A * 0x20 + 0x18] = 0x01
        cart[calls.tm.baseData + 0x9A * 0x20 + 0x18 + 6] = 0x04   // bit 50
        cart[calls.tm.baseData + 0x0F * 0x20 + 0x18 + 6] = 0x04
    }

    @Test
    fun `a TM's pick shows who can learn it, not one that knows it, and with four moves which may go`() {
        val g = game()
        g.bag(); g.tmTables()
        assertTrue(calls.usable(TM01) && calls.picksMon(TM01) && calls.picksMove(TM01) && !calls.inGame(TM01))
        assertFalse(calls.usable(0xC3) || calls.usable(0xDC))
        g.mon(0); g.mon(1, species = 0x10); g.mon(2, species = 0xFD); g.mon(3)
        g[cfg.partyMons + 3 * 0x30 + 3] = 0xDF   // slot 3 knows DYNAMICPUNCH
        assertEquals(ItemUseResult.NONE_LEFT, calls.targets(g, cfg, TM01).result)
        g[cfg.tmsHms] = 1
        val t = calls.targets(g, cfg, TM01).targets
        assertEquals(listOf(SlotVerdict.OK, SlotVerdict.NO_EFFECT, SlotVerdict.EGG, SlotVerdict.NO_EFFECT), t.map { it.verdict })
        assertTrue(t[0].moves.isEmpty())
        // Four moves, one of them an HM's: that one can't go.
        (0 until 4).forEach { g[cfg.partyMons + 2 + it] = listOf(0x01, 0x0F, 0x02, 0x03)[it] }
        assertEquals(listOf(true, false, true, true), calls.targets(g, cfg, TM01).targets[0].moves)
        g[cfg.tmsHms + 50] = 1
        assertEquals(SlotVerdict.OK, calls.targets(g, cfg, HM01).targets[1].verdict)
    }

    @Test
    fun `a TM is taught by the game's TeachTMHM to the picked Pokemon, the forgotten move's slot emptied first`() {
        val g = game()
        g.bag(); g.tmTables()
        g.mon(0); g.mon(1)
        g[cfg.tmsHms] = 1
        val two = calls.targets(g, cfg, TM01).targets[1]
        assertEquals(ItemUseResult.TEACHING, calls.useOn(g, cfg, TM01, two, 0).result)
        assertEquals(listOf(Gen2ItemCalls.ROUTINE), g.gameCalls)
        assertEquals(listOf(TM01, 0x9B, 1), listOf(g[calls.curItem], g[calls.curPartySpecies], g[calls.curPartyMon]))
        val routine = calls.tm.routine()
        assertTrue(routine.indices.all { g[Gen2ItemCalls.ROUTINE.toLong() + it] == routine[it].toInt() and 0xFF })
        assertEquals(listOf(1, 2), (0 until 2).map { g[cfg.partyMons + 0x30 + 2 + it] })   // under four: nothing emptied
        assertEquals(1, g[cfg.tmsHms])   // TeachTMHM spends it, not us

        (0 until 4).forEach { g[cfg.partyMons + 2 + it] = listOf(0x01, 0x0F, 0x02, 0x03)[it] }
        val one = calls.targets(g, cfg, TM01).targets[0]
        assertEquals(ItemUseResult.NO_EFFECT, calls.useOn(g, cfg, TM01, one, 1).result)   // CUT stays
        assertEquals(ItemUseResult.TEACHING, calls.useOn(g, cfg, TM01, one, 2).result)
        assertEquals(listOf(0x01, 0x0F, 0x00, 0x03), (0 until 4).map { g[cfg.partyMons + 2 + it] })
        assertEquals(ItemUseResult.PARTY_CHANGED, calls.useOn(g, cfg, TM01, one.copy(personality = 7), 2).result)
        g[cfg.tmsHms] = 0
        assertEquals(ItemUseResult.NONE_LEFT, calls.useOn(g, cfg, TM01, two, 0).result)
    }

    @Test
    fun `a party item opens the game's party menu, a text item just shows its text`() {
        val g = game()
        g.bag(RARE_CANDY to 1)
        assertTrue(calls.inGame(RARE_CANDY) && !calls.picksMon(RARE_CANDY))
        assertEquals(ItemUseResult.IN_GAME, calls.openInGame(g, cfg, RARE_CANDY).result)
        assertEquals(RARE_CANDY, g[calls.curItem])
        assertEquals(1, g.qty(RARE_CANDY))
        assertEquals(ItemUseResult.USED, calls.openInGame(g, cfg, COIN_CASE).result)
        assertEquals(listOf(calls.useRegisteredItem, calls.useRegisteredItem), g.gameCalls)
        g.bag()
        assertEquals(ItemUseResult.NONE_LEFT, calls.openInGame(g, cfg, RARE_CANDY).result)
    }

    @Test
    fun `a field item hands back the script its effect queued, nothing where the game says not now`() {
        val g = game()
        g.bag(ESCAPE_ROPE to 1)
        assertTrue(calls.fieldEffect(ITEMFINDER) && calls.usable(ESCAPE_ROPE) && !calls.inGame(ESCAPE_ROPE))
        assertEquals(0x04 to 0x65A8, calls.fieldEffect(g, cfg, ITEMFINDER))
        assertEquals(ITEMFINDER, g[calls.curItem])
        g.fieldWorks = false
        assertEquals(null, calls.fieldEffect(g, cfg, ESCAPE_ROPE))
        g.bag()
        g.gameCalls.clear()
        assertEquals(null, calls.fieldEffect(g, cfg, ESCAPE_ROPE))
        assertTrue(g.gameCalls.isEmpty())
    }
}
