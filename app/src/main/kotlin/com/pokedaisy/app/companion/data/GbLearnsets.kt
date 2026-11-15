package com.pokedaisy.app.companion.data

/**
 * The Game Boy games' level-up moves and TMs / HMs, read from the cart ([Gen1Dex.rom]) for the DEX's MOVES and the
 * summary's LEARN list (pret's pokered / pokeyellow / pokecrystal / pokegold layouts):
 *
 * - Gen 1: EvosMovesPointerTable (by internal species - 1) points at each Pokémon's evolutions (EVOLVE_LEVEL / TRADE
 *   3 bytes, EVOLVE_ITEM 4, a 0 after) and then its learnset, {level, move} pairs ending in 0. The TM / HM bits are its
 *   BaseStats' 7 bytes at +0x14 (by Dex number; Red / Blue's MEW in a record of its own), TechnicalMachines the move
 *   each of the 55 teaches; TM01 is item 0xC9, HM01 0xC4.
 * - Gen 2: EvosAttacksPointers (by species - 1, bank 0x10) the same way (EVOLVE_STAT is 4 bytes, the rest 3), the bits
 *   BaseData's 8 bytes at +0x18 over TMHMMoves (50 TMs, 7 HMs, then Crystal's 3 tutor moves), the items [Gen2DexTables.tmItems].
 *
 * Only where the tables name these ([Gen1DexTables.evosMoves] / [Gen2DexTables.evosAttacks]: the English carts).
 */
object GbLearnsets {
    private fun off(bank: Int, addr: Int) = if (bank == 0) addr else bank * 0x4000 + (addr - 0x4000)
    private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF
    private fun u16(b: ByteArray, o: Int) = u8(b, o) or (u8(b, o + 1) shl 8)

    /** Whether [t] reads them (no ROM read). */
    fun available(t: PokedexTables): Boolean = t.gen1?.evosMoves != null || t.gen2?.evosAttacks != null

    /** [species]' (a Dex number) level-up moves in learning order; null = not read here. */
    fun levelUp(t: PokedexTables, species: Int, rom: ByteArray? = Gen1Dex.rom): List<LevelMove>? {
        rom ?: return null
        t.gen1?.let { g ->
            val (bank, addr) = g.evosMoves ?: return null
            val internal = g.internalToDex.indexOfFirst { it == species }.takeIf { it > 0 } ?: return null
            return learnset(rom, off(bank, addr) + (internal - 1) * 2, bank) { type -> if (type == 2) 4 else 3 }
        }
        t.gen2?.let { g ->
            val (bank, addr) = g.evosAttacks ?: return null
            if (species !in 1..251) return null
            return learnset(rom, off(bank, addr) + (species - 1) * 2, bank) { type -> if (type == 5) 4 else 3 }
        }
        return null
    }

    private fun learnset(rom: ByteArray, ptrAt: Int, bank: Int, evoLen: (Int) -> Int): List<LevelMove>? {
        val p = u16(rom, ptrAt).takeIf { it in 0x4000..0x7FFF } ?: return null
        var i = off(bank, p)
        val end = bank * 0x4000 + 0x4000
        while (i < end && u8(rom, i) != 0) i += evoLen(u8(rom, i))
        i++
        val out = ArrayList<LevelMove>()
        while (i + 1 < end && u8(rom, i) != 0 && out.size < 64) {
            val level = u8(rom, i)
            val move = u8(rom, i + 1)
            if (level in 1..100 && move != 0) out += LevelMove(level, move)
            i += 2
        }
        return out
    }

    /** The TMs / HMs (and Crystal's tutor moves, unlabelled) [species] (a Dex number) can learn, in the machines' order. */
    fun machines(t: PokedexTables, species: Int, rom: ByteArray? = Gen1Dex.rom): List<TeachMove>? {
        rom ?: return null
        t.gen1?.let { g ->
            val (tmBank, tmAddr) = g.tmMoves ?: return null
            if (species !in 1..151) return null
            val base = if (species == 151 && g.mewBaseStats != null) off(g.mewBaseStats.first, g.mewBaseStats.second)
            else off(g.baseStats.first, g.baseStats.second) + (species - 1) * g.baseStatsSize
            val bits = base + 0x14
            val moves = off(tmBank, tmAddr)
            return (0 until GEN1_MACHINES).filter { u8(rom, bits + it / 8) shr (it % 8) and 1 != 0 }
                .map { TeachMove(if (it < 50) "TM%02d".format(it + 1) else "HM%02d".format(it - 49), u8(rom, moves + it)) }
                .filter { it.move != 0 }
        }
        t.gen2?.let { g ->
            if (g.evosAttacks == null || species !in 1..251) return null
            val bits = off(g.baseData.first, g.baseData.second) + (species - 1) * g.baseDataSize + 0x18
            val moves = off(g.tmHmMoves.first, g.tmHmMoves.second)
            val machines = g.tmItems.size
            return (0 until GEN2_BITS).takeWhile { u8(rom, moves + it) != 0 }
                .filter { u8(rom, bits + it / 8) shr (it % 8) and 1 != 0 }
                .map {
                    val label = when {
                        it < 50 -> "TM%02d".format(it + 1)
                        it < machines -> "HM%02d".format(it - 49)
                        else -> ""   // a move tutor's
                    }
                    TeachMove(label, u8(rom, moves + it))
                }
        }
        return null
    }

    /** A bag item's machine label ("TM06" / "HM01"), by its id; null = not a TM / HM (or not a Game Boy game). */
    fun machineLabel(t: PokedexTables, itemId: Int): String? {
        t.gen1?.let {
            return when (itemId) {
                in GEN1_HM01 until GEN1_HM01 + 5 -> "HM%02d".format(itemId - GEN1_HM01 + 1)
                in GEN1_TM01 until GEN1_TM01 + 50 -> "TM%02d".format(itemId - GEN1_TM01 + 1)
                else -> null
            }
        }
        t.gen2?.let { g ->
            val i = g.tmItems.indexOf(itemId).takeIf { it >= 0 } ?: return null
            return if (i < 50) "TM%02d".format(i + 1) else "HM%02d".format(i - 49)
        }
        return null
    }

    private const val GEN1_MACHINES = 55
    private const val GEN1_TM01 = 0xC9
    private const val GEN1_HM01 = 0xC4
    private const val GEN2_BITS = 64
}
