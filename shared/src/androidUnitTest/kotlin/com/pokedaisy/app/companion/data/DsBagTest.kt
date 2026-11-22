package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DS bag's rules and how it reads the game's answers (DsBag.kt); the jobs themselves were proved headless. */
class DsBagTest {
    private fun mon(personality: Long, held: Int = 0, egg: Boolean = false, moves: Int = 4) = MonView(
        species = 1, name = "MON", level = 10, hp = 10, maxHp = 20, status = "", types = listOf("NORMAL"), iconAsset = null,
        moves = (1..moves).map { MoveView(name = "M$it", type = "NORMAL", pp = 10, power = 40, id = it) },
        personality = personality, heldItemId = held, isEgg = egg,
    )

    @Test fun provedCartsOnly() {
        assertTrue(DsBag.proved("CPUE", 1))
        assertFalse(DsBag.proved("CPUE", 0))
        assertTrue(DsBag.proved("IPKE", 0))
        assertTrue(DsBag.proved("IPGE", 0))
        assertFalse(DsBag.proved("IPKD", 0))
        listOf("IRBO", "IRAO", "IREO", "IRDO").forEach { assertTrue(it, DsBag.proved(it, 0)) }
        assertTrue(DsBag.gen5("IREO"))
        assertFalse(DsBag.gen5("IPKE"))
    }

    @Test fun gen5Effects() {
        // what the helper applies: Black / White's amounts
        assertEquals(DsBag.Effect(heal = 20), DsBag.effect(17))
        assertEquals(DsBag.Effect(heal = 50), DsBag.effect(26))
        assertEquals(DsBag.Effect(heal = DsBag.FULL, statusMask = 0xFF), DsBag.effect(23))
        assertEquals(DsBag.Effect(revive = 2), DsBag.effect(28))
        assertEquals(DsBag.Effect(heal = 1, healDiv = 4), DsBag.effect(158))   // Sitrus: a quarter
        assertEquals(intArrayOf(0, 0, 0, 0, 10, 1).toList(), DsBag.effect(40)!!.words().toList())   // Elixir
        // herbs (friendship), vitamins, PP Up, Rare Candy: the game's bag
        listOf(34, 35, 36, 37, 45, 50, 51, 169).forEach { assertFalse("$it", DsBag.canUse(it, gen5 = true)) }
        assertTrue(DsBag.picksMove(38, gen5 = true))
        assertFalse(DsBag.picksMove(40, gen5 = true))
        assertFalse(DsBag.picksMove(51, gen5 = true))
        // form items stay out of Gen 5's GIVE / TAKE
        assertFalse(DsBag.canGive(117, POCKET_ITEMS, gen5 = true))
        assertFalse(DsBag.canGive(300, POCKET_ITEMS, gen5 = true))
        assertTrue(DsBag.canGive(300, POCKET_ITEMS))
        assertEquals(SlotVerdict.HOLDS_MAIL, DsBag.held(listOf(mon(1, held = 117)), gen5 = true).targets[0].verdict)
        // an all-moves item: any move's bit counts
        assertEquals(SlotVerdict.OK, DsBag.targets(40, false, 1L shl 2, listOf(mon(1)), gen5 = true).targets[0].verdict)
    }

    @Test fun useAndGiveRules() {
        // medicine, PP, vitamins and field berries; never Sacred Ash, Rare Candy or a stone
        listOf(17, 18, 23, 28, 38, 41, 45, 51, 53, 54, 149, 154, 158, 169, 174).forEach { assertTrue("$it", DsBag.canUse(it)) }
        listOf(44, 50, 80, 82, 112, 137, 159, 175, 328).forEach { assertFalse("$it", DsBag.canUse(it)) }
        assertEquals(setOf(38, 39, 51, 53, 154), (1..500).filter(DsBag::picksMove).toSet())
        assertTrue(DsBag.canGive(17, POCKET_MEDICINE))
        assertTrue(DsBag.canGive(155, POCKET_BERRIES))
        assertFalse(DsBag.canGive(137, POCKET_MAIL))
        assertFalse(DsBag.canGive(112, POCKET_ITEMS))     // the Griseous Orb
        assertFalse(DsBag.canGive(328, POCKET_TM_HM))
        assertFalse(DsBag.canGive(428, POCKET_KEY_ITEMS))
        assertFalse(DsBag.canGive(17, null))
    }

    @Test fun argumentsPackLikeTheNativeSide() {
        val a = DsBag.args(DsBag.GIVE, 17, slot = 3, move = 2, personality = 0xC7F45D2EL, partyCount = 6)
        assertEquals(DsBag.GIVE or (3 shl 8) or (2 shl 16), a[0])
        assertEquals(17, a[1])
        assertEquals(0xC7F45D2E.toInt(), a[2])
        assertEquals(6, a[3])
        assertEquals(DsBag.CHECK or (DsBag.EACH_MOVE shl 16), DsBag.checkArgs(38, 2)[0])
        assertEquals(DsBag.CHECK, DsBag.checkArgs(17, 2)[0])
    }

    @Test fun thePickFromTheGamesMask() {
        val party = listOf(mon(1), mon(2, held = 155), mon(3, egg = true), mon(4, held = 140))
        // Potion: slot 0 and slot 3 would heal (bits 0 and 12)
        val use = DsBag.targets(17, false, (1L shl 0) or (1L shl 12), party).targets.map { it.verdict }
        assertEquals(listOf(SlotVerdict.OK, SlotVerdict.NO_EFFECT, SlotVerdict.EGG, SlotVerdict.OK), use)
        // Ether: slot 1's moves 2 and 4
        val ether = DsBag.targets(38, false, (1L shl 5) or (1L shl 7), party).targets[1]
        assertEquals(SlotVerdict.OK, ether.verdict)
        assertEquals(listOf(false, true, false, true), ether.moves)
        // GIVE an Oran Berry: slot 1 holds one already, slot 3 holds Mail
        val give = DsBag.targets(155, true, 0, party).targets.map { it.verdict }
        assertEquals(listOf(SlotVerdict.OK, SlotVerdict.HOLDS_IT, SlotVerdict.EGG, SlotVerdict.HOLDS_MAIL), give)
        val held = DsBag.held(party).targets.map { it.verdict }
        assertEquals(listOf(SlotVerdict.NO_EFFECT, SlotVerdict.OK, SlotVerdict.EGG, SlotVerdict.HOLDS_MAIL), held)
    }

    @Test fun theGamesAnswers() {
        assertEquals(ItemUseOutcome(ItemUseResult.USED), DsBag.outcome(DsBag.USE, 0, 0))
        assertEquals(ItemUseOutcome(ItemUseResult.GIVEN, swapped = 155), DsBag.outcome(DsBag.GIVE, 0, 155))
        assertEquals(ItemUseOutcome(ItemUseResult.TAKEN, swapped = 17), DsBag.outcome(DsBag.TAKE, 0, 17))
        assertEquals(ItemUseResult.NO_EFFECT, DsBag.outcome(DsBag.USE, 1, 0).result)
        assertEquals(ItemUseResult.NONE_LEFT, DsBag.outcome(DsBag.USE, 2, 0).result)
        assertEquals(ItemUseResult.PARTY_CHANGED, DsBag.outcome(DsBag.USE, 3, 0).result)
        assertEquals(ItemUseResult.BAG_FULL, DsBag.outcome(DsBag.TAKE, 6, 17).result)
        assertEquals(ItemUseResult.FAILED, DsBag.outcome(DsBag.USE, 99, 0).result)
    }
}
