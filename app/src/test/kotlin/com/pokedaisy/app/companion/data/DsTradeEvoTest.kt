package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How TRADE EVOLUTIONS on the DS reads the game's answers (DsTradeEvo.kt); the jobs themselves were proved headless. */
class DsTradeEvoTest {
    private fun mon(personality: Long, egg: Boolean = false) = MonView(
        species = 1, name = "MON", level = 10, hp = 10, maxHp = 20, status = "", types = listOf("NORMAL"), iconAsset = null,
        moves = emptyList(), personality = personality, isEgg = egg,
    )

    @Test fun provedCartsOnly() {
        assertTrue(DsTradeEvo.proved("CPUE", 1))
        assertTrue(DsTradeEvo.proved("IPKE", 0))
        assertTrue(DsTradeEvo.proved("IPGE", 0))
        assertFalse(DsTradeEvo.proved("CPUE", 0))
        assertFalse(DsTradeEvo.proved("CPUF", 0))
        assertFalse(DsTradeEvo.proved("IPKD", 0))
        assertTrue(DsTradeEvo.proved("IRBO", 0))
        assertTrue(DsTradeEvo.proved("IRAO", 0))
        assertTrue(DsTradeEvo.proved("IREO", 0))
        assertTrue(DsTradeEvo.proved("IRDO", 0))
        assertFalse(DsTradeEvo.proved("IREO", 1))
        assertFalse(DsTradeEvo.proved("IRBF", 0))
    }

    @Test fun args() {
        assertEquals(listOf(DsTradeEvo.START or (2 shl 8), 0, 0xDEADBEEF.toInt(), 3),
            DsTradeEvo.args(DsTradeEvo.START, 2, 0xDEADBEEFL, 3).toList())
    }

    @Test fun targetsBySlot() {
        // the helper's answer, then each slot's target: KADABRA's ALAKAZAM in slot 0, nothing in 1, an egg's ignored in 2
        val res = IntArray(32).also { it[DsTradeEvo.RESULT + 1] = 65; it[DsTradeEvo.RESULT + 3] = 208 }
        val party = listOf(mon(0x11), mon(0x22), mon(0x33, egg = true))
        assertEquals(mapOf(0x11L to 65), DsTradeEvo.targets(res, party))
    }

    @Test fun answers() {
        assertEquals(TradeEvoResult.EVOLVING, DsTradeEvo.result(0))
        assertEquals(TradeEvoResult.NO_EVOLUTION, DsTradeEvo.result(1))
        assertEquals(TradeEvoResult.MOVED, DsTradeEvo.result(2))
        assertEquals(TradeEvoResult.NOT_NOW, DsTradeEvo.result(3))
    }
}
