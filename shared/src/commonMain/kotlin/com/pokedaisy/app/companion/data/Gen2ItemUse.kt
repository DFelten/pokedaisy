package com.pokedaisy.app.companion.data

/**
 * USE on Crystal's ITEMS tab (English rev 0 / rev 1 / Australia, German Kristall): the medicine, the PP items and the
 * Repels, by pokecrystal's engine/items/item_effects.asm. Its effect routines can't be called on their own - each opens
 * the party menu (UseItem_SelectMon) and prints its text - so the bag goes the game's way and the rest as they'd write it:
 *
 * - The game's own TossItem (home) takes one out of the ITEMS pocket, far-called from the overworld loop (HandleMap) with
 *   [GameCalls.gbCallAt] like the PC's moves (Gen2BoxSwap.kt): wCurItem and wItemQuantityChange written once the call is
 *   set up, the pocket read back. Only then does the item do anything.
 * - HP: HealingHPAmounts' amount (read from the cart) added, no more than the max HP (RestoreHealth); no effect on a fainted
 *   Pokémon or one at full HP. FULL RESTORE: full HP and no status, or only the status at full HP (FullyHealStatus).
 * - Status: StatusHealingActions' mask (read from the cart) against the status byte, which then goes to 0 (UseStatusHealer);
 *   no effect when it has none of those, or on a fainted Pokémon.
 * - REVIVE / MAX REVIVE: a fainted Pokémon only, half its max HP (rounded down) or all of it (ReviveHalfHP / ReviveFullHP).
 * - ETHER 10 / MYSTERYBERRY 5 / MAX ETHER all on one move, ELIXER / MAX ELIXER on each (RestorePP): up to the move's max
 *   PP, its base PP from the cart's Moves table plus min(base / 5, 7) for each PP Up (ComputeMaxPP), the PP Up bits kept.
 * - Repels: wRepelEffect set to 100 / 200 / 250 steps, refused while one still works (UseRepel).
 *
 * Left out: ENERGYPOWDER, ENERGY ROOT, HEAL POWDER and REVIVAL HERB (they also lower friendship), SACRED ASH, PP UP, the
 * evolution stones, vitamins and RARE CANDY (their own screens).
 */
class Gen2ItemCalls(
    /** TossItem's address in bank 0. */
    val tossItem: Int,
    /** Cart offsets and the bytes the code / data there must have. */
    val signatures: List<Pair<Long, String>>,
    /** HandleMap's first instruction, as (bank << 16) | addr. */
    val at: Int = 0x256773,
    val healingHp: Long = romOff(0x03, 0x73AF),
    val statusHealing: Long = romOff(0x03, 0x7071),
    val moves: Long = romOff(0x10, 0x5AFB),
    val curItem: Long = wramx(1, 0xD106),
    val quantityChange: Long = wramx(1, 0xD10C),
    val repelEffect: Long = wramx(1, 0xDCA1),
    /** BikeFunction (bank 3): the BICYCLE's field effect, the same in every Crystal build. */
    val bikeFunction: Int = 0x50B3,
    /** OldRodEffect (bank 3): `ld e, 0` + FishFunction; GoodRodEffect and SuperRodEffect follow, 4 bytes apart. */
    val oldRodEffect: Int = 0x75A5,
    /** ReanchorMap (bank 0): what SELECT's item use runs first - the BG map put back at the screen, so the get-on /
     * get-off script's CloseText redraws the map where it is (without it, EXPAND lost its place after getting off). */
    val reanchorMap: Int = 0x2DBA,
    /** wQueuedScriptBank, then Addr (lo, hi): where BikeFunction queues its get-on / get-off script. */
    val queuedScript: Long = wramx(1, 0xD0E8),
    /** wFieldMoveSucceeded: BikeFunction's / FishFunction's answer, 1 = a script is queued, 0 = not here. */
    val fieldMoveSucceeded: Long = wramx(1, 0xD0EC),
    /** wUsingItemWithSelect: set, BikeFunction queues the short scripts SELECT uses (no text box). */
    val usingItemWithSelect: Long = wramx(1, 0xD0EF),
) {
    fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = signatures.all { (off, hex) ->
        val want = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        rom(off, want.size)?.contentEquals(want) == true
    }

    /** The items USE takes here. */
    fun usable(itemId: Int) = itemId in HP_ITEMS || itemId in STATUS_ITEMS || itemId == FULL_RESTORE ||
        itemId in REVIVES || itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES || itemId in REPELS || onTheSpot(itemId)

    fun picksMon(itemId: Int) = usable(itemId) && itemId !in REPELS && !onTheSpot(itemId)

    /** The BICYCLE and the rods: the game's own field effect, which queues a script of its own. */
    fun onTheSpot(itemId: Int) = itemId == BICYCLE || itemId in RODS
    fun picksMove(itemId: Int) = itemId in PP_ONE_MOVE

    private class Party(val count: Int, val species: ByteArray, val mons: ByteArray)

    private fun party(g: GameCalls, cfg: Gen2Config): Party {
        val count = (g.read(cfg.partyCount, 1)[0].toInt() and 0xFF).coerceAtMost(PARTY_MAX)
        return Party(count, g.read(cfg.partyCount + 1, PARTY_MAX), g.read(cfg.partyMons, PARTY_MAX * MON_SIZE))
    }

    private fun be16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) shl 8 or (b[o + 1].toInt() and 0xFF)
    private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF

    /** The cart's {item, word} / {item, text, mask} table entry for [itemId], or null. */
    private fun tableEntry(g: GameCalls, table: Long, stride: Int, itemId: Int): ByteArray? {
        val raw = g.rom(table, stride * 32) ?: return null
        for (i in 0 until 32) {
            val id = u8(raw, i * stride)
            if (id == 0xFF) return null
            if (id == itemId) return raw.copyOfRange(i * stride, i * stride + stride)
        }
        return null
    }

    private fun hpAmount(g: GameCalls, itemId: Int) = tableEntry(g, healingHp, 3, itemId)?.let { u8(it, 1) or (u8(it, 2) shl 8) }
    private fun statusMask(g: GameCalls, itemId: Int) = tableEntry(g, statusHealing, 3, itemId)?.let { u8(it, 2) }

    /** A move's max PP: base PP + min(base / 5, 7) per PP Up (ComputeMaxPP). */
    private fun maxPp(g: GameCalls, move: Int, ppByte: Int): Int {
        val base = g.rom(moves + (move - 1).toLong() * MOVE_LENGTH + MOVE_PP, 1)?.let { it[0].toInt() and 0xFF } ?: return 0
        return base + (ppByte shr 6) * minOf(base / 5, 7)
    }

    /** Which of slot [slot]'s moves [itemId] would raise. */
    private fun ppMoves(g: GameCalls, p: Party, slot: Int): List<Boolean> {
        val o = slot * MON_SIZE
        return (0 until 4).map { m ->
            val move = u8(p.mons, o + MOVES + m)
            val pp = u8(p.mons, o + PP + m)
            move != 0 && (pp and PP_MASK) < maxPp(g, move, pp)
        }
    }

    private fun verdict(g: GameCalls, p: Party, slot: Int, itemId: Int): SlotVerdict {
        if (u8(p.species, slot) == EGG) return SlotVerdict.EGG
        val o = slot * MON_SIZE
        val hp = be16(p.mons, o + HP)
        val max = be16(p.mons, o + MAX_HP)
        val status = u8(p.mons, o + STATUS)
        val ok = when {
            itemId in REVIVES -> hp == 0
            hp == 0 -> false
            itemId == FULL_RESTORE -> hp < max || status != 0
            itemId in HP_ITEMS -> hp < max
            itemId in STATUS_ITEMS -> status and (statusMask(g, itemId) ?: 0) != 0
            itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES -> ppMoves(g, p, slot).any { it }
            else -> false
        }
        return if (ok) SlotVerdict.OK else SlotVerdict.NO_EFFECT
    }

    /** USE's party pick for [itemId]. */
    fun targets(g: GameCalls, cfg: Gen2Config, itemId: Int): PartyTargets {
        if (!picksMon(itemId)) return PartyTargets(ItemUseResult.FAILED)
        if (quantity(g, cfg, itemId) == 0) return PartyTargets(ItemUseResult.NONE_LEFT)
        val p = party(g, cfg)
        return PartyTargets(ItemUseResult.USED, (0 until p.count).map { slot ->
            PartyTarget(slot, gen2MonKey(p.mons, slot * MON_SIZE), u8(p.mons, slot * MON_SIZE + HELD_ITEM),
                verdict(g, p, slot, itemId), if (itemId in PP_ONE_MOVE) ppMoves(g, p, slot) else emptyList())
        })
    }

    /** How many [itemId] the ITEMS pocket holds. */
    private fun quantity(g: GameCalls, cfg: Gen2Config, itemId: Int): Int {
        val raw = g.read(cfg.numItems, 2 + 2 * cfg.itemsMax)
        val n = u8(raw, 0).coerceAtMost(cfg.itemsMax)
        return (0 until n).filter { u8(raw, 1 + 2 * it) == itemId }.sumOf { u8(raw, 2 + 2 * it) }
    }

    /** One [itemId] out of the bag by the game's TossItem: false = it didn't go (not on the field, or the bag didn't change). */
    private fun toss(g: GameCalls, cfg: Gen2Config, itemId: Int, before: Int): Boolean {
        if (!g.gbCallAt(at, tossItem, 0, 0, (cfg.numItems and 0xFFFF).toInt())) return false
        g.write(curItem, byteArrayOf(itemId.toByte()))
        g.write(quantityChange, byteArrayOf(1))
        // The loop coming by again means TossItem has returned.
        if (!(0 until SETTLE_TRIES).any { g.gbCallAt(at, SETTLE, 0, 0, 0) }) return false
        return quantity(g, cfg, itemId) == before - 1
    }

    /** USE of a Repel on the spot. */
    fun useRepel(g: GameCalls, cfg: Gen2Config, itemId: Int): ItemUseOutcome {
        val steps = REPELS[itemId] ?: return ItemUseOutcome(ItemUseResult.FAILED)
        if (g.read(repelEffect, 1)[0].toInt() != 0) return ItemUseOutcome(ItemUseResult.STILL_ACTIVE)
        val before = quantity(g, cfg, itemId)
        if (before == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (!toss(g, cfg, itemId, before)) return ItemUseOutcome(ItemUseResult.FAILED)
        g.write(repelEffect, byteArrayOf(steps.toByte()))
        return ItemUseOutcome(ItemUseResult.USED, steps)
    }

    /**
     * The BICYCLE: the game's own BikeFunction far-called from the overworld loop, as SELECT runs it - its checks (outdoors, a
     * cave or a gate, a plain floor tile; Cycling Road keeps you on), the music, then the get-on / get-off script it queues.
     * The rods: OLD / GOOD / SUPER ROD's item effect (FishFunction: facing water, the map's fishing group, the bite), which
     * queues the cast and either a bite's battle or "Not even a nibble!".
     * That queue is the START / SELECT menus', so its script comes back here (bank, address) for the map's own queue
     * ([GbField.CrystalEngine]); null = not here.
     */
    fun onTheSpot(g: GameCalls, itemId: Int): Pair<Int, Int>? {
        val fn = if (itemId == BICYCLE) bikeFunction else oldRodEffect + 4 * (RODS.indexOf(itemId).takeIf { it >= 0 } ?: return null)
        if (!(0 until SETTLE_TRIES).any { g.gbCallAt(at, reanchorMap, 0, 0, 0) }) return null
        if (!(0 until REANCHOR_TRIES).any { g.gbCallAt(at, FARCALL, FIELD_BANK, 0, fn) }) return null
        g.write(fieldMoveSucceeded, byteArrayOf(0xFF.toByte()))
        g.write(usingItemWithSelect, byteArrayOf(if (itemId == BICYCLE) 1 else 0))
        val ran = (0 until SETTLE_TRIES).any { g.gbCallAt(at, SETTLE, 0, 0, 0) }
        g.write(usingItemWithSelect, byteArrayOf(0))
        if (!ran || g.read(fieldMoveSucceeded, 1)[0].toInt() != 1) return null
        val q = g.read(queuedScript, 3)
        return (q[0].toInt() and 0xFF) to ((q[1].toInt() and 0xFF) or ((q[2].toInt() and 0xFF) shl 8))
    }

    /** USE of [itemId] on [target] ([moveIndex] for a one-move PP item). */
    fun useOn(g: GameCalls, cfg: Gen2Config, itemId: Int, target: PartyTarget, moveIndex: Int): ItemUseOutcome {
        if (!picksMon(itemId)) return ItemUseOutcome(ItemUseResult.FAILED)
        val p = party(g, cfg)
        val slot = target.slot
        if (slot !in 0 until p.count || gen2MonKey(p.mons, slot * MON_SIZE) != target.personality) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        if (verdict(g, p, slot, itemId) != SlotVerdict.OK) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (itemId in PP_ONE_MOVE && ppMoves(g, p, slot).getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        val amount = if (itemId in HP_ITEMS) hpAmount(g, itemId) ?: return ItemUseOutcome(ItemUseResult.FAILED) else 0
        val before = quantity(g, cfg, itemId)
        if (before == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (!toss(g, cfg, itemId, before)) return ItemUseOutcome(ItemUseResult.FAILED)

        val o = slot * MON_SIZE
        val mon = cfg.partyMons + o
        val hp = be16(p.mons, o + HP)
        val max = be16(p.mons, o + MAX_HP)
        fun writeHp(v: Int) = g.write(mon + HP, byteArrayOf((v shr 8).toByte(), v.toByte()))
        when {
            itemId in REVIVES -> writeHp(if (itemId == REVIVE) max shr 1 else max)
            itemId == FULL_RESTORE -> {
                if (hp < max) writeHp(max)
                g.write(mon + STATUS, byteArrayOf(0, 0))
            }
            itemId in HP_ITEMS -> writeHp(minOf(hp + amount, max))
            itemId in STATUS_ITEMS -> g.write(mon + STATUS, byteArrayOf(0))
            else -> {
                val restore = PP_ONE_MOVE[itemId] ?: PP_ALL_MOVES.getValue(itemId)
                val which = if (itemId in PP_ONE_MOVE) listOf(moveIndex) else (0 until 4).toList()
                for (m in which) {
                    val move = u8(p.mons, o + MOVES + m)
                    val pp = u8(p.mons, o + PP + m)
                    val full = maxPp(g, move, pp)
                    if (move == 0 || (pp and PP_MASK) >= full) continue
                    val now = if (restore == ALL) full else minOf((pp and PP_MASK) + restore, full)
                    g.write(mon + PP + m, byteArrayOf(((pp and PP_UP_MASK) or now).toByte()))
                }
            }
        }
        return ItemUseOutcome(ItemUseResult.USED)
    }

    companion object {
        /** rst $10's (Bankswitch's) closing `ret`: a call that does nothing (Gen2BoxCalls' settle). */
        private const val FARCALL = 0x0008   // FarCall_hl: a = bank, hl = address
        private const val SETTLE = 0x0015
        private const val SETTLE_TRIES = 6
        /** ReanchorMap waits a few frames (5 tries headless on Kristall) before the loop is back at HandleMap. */
        private const val REANCHOR_TRIES = 12
        private const val PARTY_MAX = 6
        private const val MON_SIZE = 0x30
        private const val HELD_ITEM = 0x01
        private const val MOVES = 0x02
        private const val PP = 0x17
        private const val STATUS = 0x20
        private const val HP = 0x22
        private const val MAX_HP = 0x24
        private const val EGG = 0xFD
        private const val PP_MASK = 0x3F
        private const val PP_UP_MASK = 0xC0
        private const val MOVE_LENGTH = 7
        private const val MOVE_PP = 5
        private const val ALL = -1

        const val BICYCLE = 0x07
        /** OLD ROD, GOOD ROD, SUPER ROD, in OldRodEffect's order. */
        val RODS = listOf(0x3A, 0x3B, 0x3D)
        private const val FIELD_BANK = 0x03
        const val FULL_RESTORE = 0x0E
        const val REVIVE = 0x27
        val REVIVES = setOf(REVIVE, 0x28)
        /** RestoreHPEffect's items (HealingHPAmounts, without ENERGYPOWDER / ENERGY ROOT and FULL RESTORE). */
        val HP_ITEMS = setOf(0x0F, 0x10, 0x11, 0x12, 0x2E, 0x2F, 0x30, 0x48, 0xAD, 0xAE, 0x72, 0x8B)
        /** StatusHealingEffect's items (StatusHealingActions, without FULL RESTORE and HEAL POWDER). */
        val STATUS_ITEMS = setOf(0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x26, 0x4A, 0x4E, 0x4F, 0x50, 0x54, 0x6D)
        /** ETHER, MAX ETHER, MYSTERYBERRY: PP back on one move. */
        val PP_ONE_MOVE = mapOf(0x3F to 10, 0x40 to ALL, 0x96 to 5)
        /** ELIXER, MAX ELIXER: on every move. */
        val PP_ALL_MOVES = mapOf(0x41 to 10, 0x15 to ALL)
        /** REPEL, SUPER REPEL, MAX REPEL and their steps. */
        val REPELS = mapOf(0x14 to 100, 0x2A to 200, 0x2B to 250)

        fun romOff(bank: Int, addr: Int) = if (bank == 0) addr.toLong() else bank * 0x4000L + (addr - 0x4000)
    }
}

/** The tables' first bytes and HandleMap, shared by every Crystal build: HealingHPAmounts (FRESH WATER 50), StatusHealingActions
 * (ANTIDOTE), Moves (POUND). */
private fun crystalItemSignatures(handleMap: String, healingHp: Int, statusHealing: Int, moves: Int, oldRod: Int) = listOf(
    0x25 * 0x4000L + (0x6773 - 0x4000) to handleMap,
    Gen2ItemCalls.romOff(0x03, healingHp) to "2e32002f3c0030500010c800",
    Gen2ItemCalls.romOff(0x03, statusHealing) to "09f0080af1100bf2200cf307",
    Gen2ItemCalls.romOff(0x10, moves) to "01002800ff2300",
    Gen2ItemCalls.romOff(0x03, 0x50B3) to "cdbc50e67feaecd0c9cd2151",   // BikeFunction
    Gen2ItemCalls.romOff(0x03, oldRod) to "1e0018081e0118041e0218003e03218e4fcf",   // the rods' effects, farcall FishFunction
)

/** Crystal (rev 0, rev 1, Australia): pret/pokecrystal's symbols. */
val GEN2_ITEM_CALLS_CRYSTAL = Gen2ItemCalls(
    tossItem = 0x2F53,
    signatures = listOf(0x2F53L to "e5d5c5f09df53e03d7cd0d52c1", 0x2DBAL to "cdbf1ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521087e", 0x73AF, 0x7071, 0x5AFB, 0x75A5),
)

/** German Crystal (Kristall): English's RAM and _TossItem; TossItem, the tables and Moves a little earlier. */
val GEN2_ITEM_CALLS_CRYSTAL_DE = Gen2ItemCalls(
    tossItem = 0x2F3D,
    signatures = listOf(0x2F3DL to "e5d5c5f09df53e03d7cd0d52c1", 0x2DA4L to "cda91ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521097e", 0x737E, 0x7040, 0x5AEB, 0x7574),
    healingHp = Gen2ItemCalls.romOff(0x03, 0x737E),
    statusHealing = Gen2ItemCalls.romOff(0x03, 0x7040),
    moves = Gen2ItemCalls.romOff(0x10, 0x5AEB),
    reanchorMap = 0x2DA4,
    oldRodEffect = 0x7574,
)
