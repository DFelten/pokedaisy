package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.data.*
import com.pokedaisy.app.game.GameSession
import kotlin.test.*

class FieldQuickHatchTest {
    private class Harness {
        val party = PARTY_ITEMS_EMERALD.copy(codeCrcs = emptyList())
        var fields: FieldItemSupport? = FieldItemSupport(FIELD_ITEMS_EMERALD.copy(codeCrcs = emptyList(), party = party), emptySet())
        val config = NATIVE_EMERALD_RETAIL.copy(saveBlock1Ptr = SB1_PTR, staticSaveBlocks = false)
        var counterOff: Int? = EMERALD_DAYCARE_STEP_COUNTER
        var enabled = true
        var onField = true
        var locked = false
        var egg = true
        var cycles = 20
        var handoffs = 0
        val callsMade = mutableListOf<Long>()
        val ram = mutableMapOf<Long, Byte>()
        val deliveries = mutableListOf<() -> Unit>()
        val calls = object : GameCalls {
            override fun read(addr: Long, len: Int): ByteArray = when (addr) {
                party.partyCount -> byteArrayOf(1).copyOf(len)
                SB1_PTR -> byteArrayOf(0x00, 0x10, 0x02, 0x02).copyOf(len)
                else -> ByteArray(len) { ram[addr + it] ?: 0 }
            }
            override fun write(addr: Long, bytes: ByteArray): Boolean {
                bytes.forEachIndexed { i, b -> ram[addr + i] = b }
                return true
            }
            override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int): Long {
                callsMade += fn
                return when (fn) {
                    fields!!.fns.fieldControlsLocked -> if (locked) 1 else 0
                    party.getMonData -> when (a1) {
                        0 -> PERSONALITY
                        party.isEggField -> if (egg) 1 else 0
                        32 + party.monFieldShift -> cycles.toLong()
                        else -> 0
                    }
                    party.setMonData -> { cycles = ram[a2.toLong() and 0xFFFFFFFFL]?.toInt() ?: 0; 0 }
                    else -> 0
                }
            }
        }
        val game = GameSession(calls, { true }) { _, error -> throw error }
        val items = FieldItemUse({ fields }, { game }, { false }) { deliveries += it }
        val hatch = FieldQuickHatch(items, items, { counterOff }, { config }, { enabled }, { onField }, { handoffs++ })
        val counter get() = (ram[SB1 + EMERALD_DAYCARE_STEP_COUNTER] ?: 0).toInt() and 0xFF
        fun frame() = game.serviceField(true, true)
        fun deliver() { deliveries.toList().also { deliveries.clear() }.forEach { it() } }
        fun run(): HatchResult? { var r: HatchResult? = null; hatch.hatch(0, PERSONALITY) { r = it }; frame(); deliver(); return r }
    }

    @Test fun offUnknownOrWithoutPartyCallsIsNeverOffered() {
        for (case in 0..2) {
            val h = Harness()
            when (case) { 0 -> h.enabled = false; 1 -> h.counterOff = null; else -> h.fields = null }
            assertFalse(h.hatch.canHatch())
            assertEquals(HatchResult.FAILED, h.run()); assertTrue(h.callsMade.isEmpty()); assertEquals(0, h.handoffs)
        }
    }

    @Test fun hatchSetsTheCyclesAndCounterThenHandsOver() {
        val h = Harness()
        assertTrue(h.hatch.canHatch())
        assertEquals(HatchResult.HATCHING, h.run())
        assertEquals(0, h.cycles); assertEquals(254, h.counter); assertEquals(1, h.handoffs)
    }

    @Test fun offTheFieldOrLockedLeavesTheEggAlone() {
        for (case in 0..1) {
            val h = Harness(); if (case == 0) h.onField = false else h.locked = true
            assertEquals(HatchResult.NOT_NOW, h.run())
            assertEquals(20, h.cycles); assertEquals(0, h.counter); assertEquals(0, h.handoffs)
        }
    }

    @Test fun notAnEggIsRefused() {
        val h = Harness(); h.egg = false
        assertEquals(HatchResult.NOT_EGG, h.run()); assertEquals(20, h.cycles); assertEquals(0, h.counter)
    }

    @Test fun turningTheSettingOffBeforeQueuedWorkRunsPreventsMutation() {
        val h = Harness(); var r: HatchResult? = null
        h.hatch.hatch(0, PERSONALITY) { r = it }; h.enabled = false; h.frame(); h.deliver()
        assertEquals(HatchResult.FAILED, r); assertTrue(h.callsMade.isEmpty()); assertEquals(0, h.handoffs)
    }

    private companion object {
        const val PERSONALITY = 0xDEADBEEFL
        const val SB1_PTR = 0x03005D8CL
        const val SB1 = 0x02021000L
    }
}
