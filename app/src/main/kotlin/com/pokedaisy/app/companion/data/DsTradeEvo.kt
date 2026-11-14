package com.pokedaisy.app.companion.data

/**
 * TRADE EVOLUTIONS on Platinum and HeartGold / SoulSilver (pk_nds_call's PK_NDS_ACTION_TRADE_EVO, stub/pk_nds_evo.c):
 * the game's own trade rules say which party Pokémon would evolve (CHECK: each slot's target, the held item counted), and
 * START plays the game's own evolution scene as its link trade does - the held item an evolution needs used up, the
 * Pokédex entry, a move learned - from a field task that fades the map out and back in around it. Checked headless on the
 * US carts: ONIX with a Metal Coat to STEELIX (its coat gone, the field free after), a stale pick refused.
 *
 * US carts only so far (the other languages' functions aren't mapped); not the hacks (Renegade Platinum keeps the US
 * header, Origin HeartGold has its own engine), nor Black / White (no decomp: their evolution isn't traced).
 */
object DsTradeEvo {
    /** The carts it was checked on: code to revision. */
    val CARTS = setOf("CPUE" to 1, "IPKE" to 0, "IPGE" to 0)

    const val CHECK = 0
    const val START = 1

    /** Where the helper's answer is among the job's results (pk_nds_call.h's PK_NDS_EVO_RESULT); the six slots follow. */
    const val RESULT = 4

    /** The helper's answers (pk_nds_call_abi.h's PK_NDS_EVO_*). */
    private const val STARTED = 0
    private const val NONE = 1
    private const val MOVED = 2

    fun proved(code: String, revision: Int?): Boolean = (code to revision) in CARTS

    fun args(mode: Int, slot: Int, personality: Long, partyCount: Int): IntArray =
        intArrayOf(mode or (slot.coerceIn(0, 5) shl 8), 0, personality.toInt(), partyCount)

    /** CHECK's results as the summary wants them: the evolving Pokémon by personality, each to its target species. */
    fun targets(results: IntArray, party: List<MonView>): Map<Long, Int> {
        val out = LinkedHashMap<Long, Int>()
        party.take(6).forEachIndexed { slot, m ->
            val target = results.getOrNull(RESULT + 1 + slot)?.and(0xFFFF) ?: 0
            if (target != 0 && !m.isEgg) out[m.personality] = target
        }
        return out
    }

    /** START's answer in the summary's terms. */
    fun result(code: Int): TradeEvoResult = when (code) {
        STARTED -> TradeEvoResult.EVOLVING
        NONE -> TradeEvoResult.NO_EVOLUTION
        MOVED -> TradeEvoResult.MOVED
        else -> TradeEvoResult.NOT_NOW
    }
}
