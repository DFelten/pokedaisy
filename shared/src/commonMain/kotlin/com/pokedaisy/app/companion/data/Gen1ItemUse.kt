package com.pokedaisy.app.companion.data

/**
 * USE on Red's, Blue's and Yellow's ITEMS tab (English and German): the medicine, the PP items, PP UP and the Repels, by
 * pokered's / pokeyellow's engine/items/item_effects.asm. Their routines open the party menu (DisplayPartyMenu) and print
 * their text, so, as on Crystal (Gen2ItemUse.kt), the bag goes the game's way and the rest as they'd write it:
 *
 * - The game's own RemoveItemFromInventory (home) takes one out of the bag, called from the overworld loop with
 *   [GameCalls.gbCallAt] like Gen 1's PC moves (Gen1BoxSwap.kt): wWhichPokemon (the item's place in the bag) and
 *   wItemQuantity written once the call is set up, the bag read back. Only then does the item do anything.
 * - HP (ItemUseMedicine): POTION 20, SUPER POTION 50, HYPER POTION 200, FRESH WATER 50, SODA POP 60, LEMONADE 80, MAX
 *   POTION all of it, no more than the max HP; no effect on a fainted Pokémon or one at full HP. FULL RESTORE: full HP and
 *   no status, or at full HP only the status (as a FULL HEAL).
 * - Status: ANTIDOTE / BURN HEAL / ICE HEAL / AWAKENING / PARLYZ HEAL against their status bit (or the sleep counter),
 *   FULL HEAL against any; the status then goes to 0. No effect without one (a fainted Pokémon has none).
 * - REVIVE / MAX REVIVE: a fainted Pokémon only, half its max HP (rounded down) or all of it.
 * - ETHER 10 / MAX ETHER all on one move, ELIXER / MAX ELIXER on each (ItemUsePPRestore): up to the move's max PP, its base
 *   PP from the cart's Moves table plus min(base / 5, 7) per PP Up (GetMaxPP, AddBonusPP), the PP Up bits kept; a fainted
 *   Pokémon too.
 * - PP UP: one move with fewer than three PP Ups; its PP Up count + 1 and its PP + min(base / 5, 7) (RestoreBonusPP with
 *   wUsingPPUp set).
 * - Repels: wRepelRemainingSteps set to 100 / 200 / 250 (ItemUseRepelCommon, which doesn't mind one still working).
 * - Yellow's starter Pikachu: an item up to CALCIUM raises its happiness and mood (ModifyPikachuHappiness with
 *   PIKAHAPPY_USEDITEM, HappinessChangeTable / PikachuMoods read from the cart), and a REVIVE brings it back to follow the
 *   player (RespawnOverworldPikachu: wPikachuSpawnState = PIKACHU_SPAWN_DELAYED).
 *
 * Left out: the vitamins, RARE CANDY and the stones (their stats / evolution are the game's own sums and screens), the
 * BICYCLE, the rods and the ESCAPE ROPE.
 */
class Gen1ItemCalls(
    /** OverworldLoopLessDelay past its `call DelayFrame`: where a call is injected. */
    val at: Int,
    /** RemoveItemFromInventory (home): hl = the inventory, [wWhichPokemon] = the item's place, [wItemQuantity] = how many. */
    val removeItem: Int,
    /** A `ret` in the home bank (Bankswitch's last byte): a call that does nothing. */
    val ret: Int,
    val whichPokemon: Long,
    val itemQuantity: Long,
    val numBagItems: Long,
    val partyCount: Long,
    val repelSteps: Long,
    /** Yellow's starter Pikachu (null: Red / Blue). */
    val pikachu: Gen1PikachuItems? = null,
    val signatures: List<Pair<Long, String>>,
) : GbBagUse {
    override fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = signatures.all { (off, hex) ->
        val want = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        rom(off, want.size)?.contentEquals(want) == true
    }

    override fun usable(itemId: Int) = itemId in HP_ITEMS || itemId in STATUS_ITEMS || itemId == FULL_RESTORE ||
        itemId in REVIVES || itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES || itemId == PP_UP || itemId in REPELS
    override fun inGame(itemId: Int) = false
    override fun picksMon(itemId: Int) = usable(itemId) && itemId !in REPELS
    override fun picksMove(itemId: Int) = itemId in PP_ONE_MOVE || itemId == PP_UP

    private val partyMons get() = partyCount + 8
    private val partyOt get() = partyMons + PARTY_MAX * MON_SIZE

    private class Party(val count: Int, val mons: ByteArray)

    private fun party(g: GameCalls): Party {
        val count = u8(g.read(partyCount, 1), 0).coerceAtMost(PARTY_MAX)
        return Party(count, g.read(partyMons, PARTY_MAX * MON_SIZE))
    }

    private fun be16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) shl 8 or (b[o + 1].toInt() and 0xFF)
    private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF

    /** A move's base PP from the cart's Moves table. */
    private fun basePp(g: GameCalls, move: Int) =
        g.rom(MOVES_TABLE + (move - 1).toLong() * MOVE_LENGTH + MOVE_PP, 1)?.let { u8(it, 0) } ?: 0

    /** GetMaxPP: base PP + min(base / 5, 7) per PP Up. */
    private fun maxPp(g: GameCalls, move: Int, ppByte: Int): Int {
        val base = basePp(g, move)
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

    /** Which of slot [slot]'s moves PP UP can raise: fewer than three PP Ups (`cp 3 << 6`). */
    private fun ppUpMoves(p: Party, slot: Int): List<Boolean> {
        val o = slot * MON_SIZE
        return (0 until 4).map { m -> u8(p.mons, o + MOVES + m) != 0 && u8(p.mons, o + PP + m) < PP_UP_MASK }
    }

    private fun verdict(g: GameCalls, p: Party, slot: Int, itemId: Int): SlotVerdict {
        val o = slot * MON_SIZE
        val hp = be16(p.mons, o + HP)
        val max = be16(p.mons, o + MAX_HP)
        val status = u8(p.mons, o + STATUS)
        val ok = when {
            itemId == PP_UP -> ppUpMoves(p, slot).any { it }
            itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES -> ppMoves(g, p, slot).any { it }
            itemId in REVIVES -> hp == 0
            hp == 0 -> false
            itemId == FULL_RESTORE -> hp < max || status != 0
            itemId in HP_ITEMS -> hp < max
            itemId in STATUS_ITEMS -> status and STATUS_ITEMS.getValue(itemId) != 0
            else -> false
        }
        return if (ok) SlotVerdict.OK else SlotVerdict.NO_EFFECT
    }

    override fun targets(g: GameCalls, itemId: Int): PartyTargets {
        if (!picksMon(itemId)) return PartyTargets(ItemUseResult.FAILED)
        if (bagSlot(g, itemId) < 0) return PartyTargets(ItemUseResult.NONE_LEFT)
        val p = party(g)
        return PartyTargets(ItemUseResult.USED, targets = (0 until p.count).map { slot ->
            PartyTarget(slot, gen1MonKey(p.mons, slot * MON_SIZE), 0, verdict(g, p, slot, itemId),
                when {
                    itemId in PP_ONE_MOVE -> ppMoves(g, p, slot)
                    itemId == PP_UP -> ppUpMoves(p, slot)
                    else -> emptyList()
                })
        })
    }

    /** [itemId]'s place in the bag, or -1. */
    private fun bagSlot(g: GameCalls, itemId: Int): Int {
        val raw = g.read(numBagItems, 1 + 2 * BAG_MAX + 1)
        val n = u8(raw, 0).coerceAtMost(BAG_MAX)
        return (0 until n).firstOrNull { u8(raw, 1 + 2 * it) == itemId && u8(raw, 2 + 2 * it) > 0 } ?: -1
    }

    private fun quantity(g: GameCalls, itemId: Int): Int {
        val raw = g.read(numBagItems, 1 + 2 * BAG_MAX + 1)
        val n = u8(raw, 0).coerceAtMost(BAG_MAX)
        return (0 until n).filter { u8(raw, 1 + 2 * it) == itemId }.sumOf { u8(raw, 2 + 2 * it) }
    }

    /** One [itemId] out of the bag by the game's RemoveItemFromInventory: false = it didn't go. */
    private fun remove(g: GameCalls, itemId: Int): Boolean {
        val slot = bagSlot(g, itemId).takeIf { it >= 0 } ?: return false
        val before = quantity(g, itemId)
        if (!g.gbCallAt(at, removeItem, 0, 0, (numBagItems and 0xFFFF).toInt())) return false
        g.write(whichPokemon, byteArrayOf(slot.toByte()))
        g.write(itemQuantity, byteArrayOf(1))
        // The loop coming by again means RemoveItemFromInventory has returned.
        if (!(0 until SETTLE_TRIES).any { g.gbCallAt(at, ret, 0, 0, 0) }) return false
        return quantity(g, itemId) == before - 1
    }

    override fun use(g: GameCalls, itemId: Int, target: PartyTarget?, moveIndex: Int): ItemUseOutcome =
        if (target == null) useRepel(g, itemId) else useOn(g, itemId, target, moveIndex)

    /** USE of a Repel on the spot. */
    fun useRepel(g: GameCalls, itemId: Int): ItemUseOutcome {
        val steps = REPELS[itemId] ?: return ItemUseOutcome(ItemUseResult.FAILED)
        if (bagSlot(g, itemId) < 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (!remove(g, itemId)) return ItemUseOutcome(ItemUseResult.FAILED)
        g.write(repelSteps, byteArrayOf(steps.toByte()))
        return ItemUseOutcome(ItemUseResult.USED, steps)
    }

    /** USE of [itemId] on [target] ([moveIndex] for a one-move PP item or PP UP). */
    fun useOn(g: GameCalls, itemId: Int, target: PartyTarget, moveIndex: Int): ItemUseOutcome {
        if (!picksMon(itemId)) return ItemUseOutcome(ItemUseResult.FAILED)
        val p = party(g)
        val slot = target.slot
        if (slot !in 0 until p.count || gen1MonKey(p.mons, slot * MON_SIZE) != target.personality) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        if (verdict(g, p, slot, itemId) != SlotVerdict.OK) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (itemId in PP_ONE_MOVE && ppMoves(g, p, slot).getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (itemId == PP_UP && ppUpMoves(p, slot).getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        // Yellow's starter Pikachu: its happiness table read before anything is spent, so a cart without it is refused.
        val starter = pikachu?.takeIf { it.isStarter(g, partyMons + slot * MON_SIZE, partyOt + slot * NAME_LENGTH) }
        val happy = if (starter != null && itemId <= CALCIUM) starter.usedItem(g) ?: return ItemUseOutcome(ItemUseResult.FAILED) else null
        if (bagSlot(g, itemId) < 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (!remove(g, itemId)) return ItemUseOutcome(ItemUseResult.FAILED)

        val o = slot * MON_SIZE
        val mon = partyMons + o
        val hp = be16(p.mons, o + HP)
        val max = be16(p.mons, o + MAX_HP)
        fun writeHp(v: Int) = g.write(mon + HP, byteArrayOf((v shr 8).toByte(), v.toByte()))
        when {
            itemId in REVIVES -> {
                writeHp(if (itemId == REVIVE) max shr 1 else max)
                starter?.respawn(g)
            }
            itemId == PP_UP -> {
                val move = u8(p.mons, o + MOVES + moveIndex)
                val base = basePp(g, move)
                val pp = u8(p.mons, o + PP + moveIndex) + PP_UP_ONE
                g.write(mon + PP + moveIndex, byteArrayOf((pp + minOf(base / 5, 7)).toByte()))
            }
            itemId == FULL_RESTORE -> {
                if (hp < max) writeHp(max)
                g.write(mon + STATUS, byteArrayOf(0))
            }
            itemId in HP_ITEMS -> writeHp(HP_ITEMS.getValue(itemId).let { if (it == ALL) max else minOf(hp + it, max) })
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
        happy?.let { starter?.apply(g, it) }
        return ItemUseOutcome(ItemUseResult.USED)
    }

    companion object {
        private const val SETTLE_TRIES = 6
        private const val PARTY_MAX = 6
        private const val BAG_MAX = 20
        private const val MON_SIZE = 44
        private const val NAME_LENGTH = 11
        private const val HP = 0x01
        private const val STATUS = 0x04
        private const val MOVES = 0x08
        private const val PP = 0x1D
        private const val MAX_HP = 0x22
        private const val PP_MASK = 0x3F
        private const val PP_UP_MASK = 0xC0
        private const val PP_UP_ONE = 0x40
        /** Moves (bank 0x0E, the same in every Gen 1 cart): 6 bytes a move, the PP last. */
        private const val MOVES_TABLE = 0x0E * 0x4000L
        private const val MOVE_LENGTH = 6
        private const val MOVE_PP = 5
        private const val ALL = -1
        private const val CALCIUM = 0x27

        const val FULL_RESTORE = 0x10
        const val REVIVE = 0x35
        val REVIVES = setOf(REVIVE, 0x36)
        /** MAX POTION, HYPER POTION, SUPER POTION, POTION, FRESH WATER, SODA POP, LEMONADE and what each heals. */
        val HP_ITEMS = mapOf(0x11 to ALL, 0x12 to 200, 0x13 to 50, 0x14 to 20, 0x3C to 50, 0x3D to 60, 0x3E to 80)
        /** ANTIDOTE (PSN), BURN HEAL (BRN), ICE HEAL (FRZ), AWAKENING (the sleep counter), PARLYZ HEAL (PAR), FULL HEAL. */
        val STATUS_ITEMS = mapOf(0x0B to 0x08, 0x0C to 0x10, 0x0D to 0x20, 0x0E to 0x07, 0x0F to 0x40, 0x34 to 0xFF)
        /** ETHER, MAX ETHER: PP back on one move. */
        val PP_ONE_MOVE = mapOf(0x50 to 10, 0x51 to ALL)
        /** ELIXER, MAX ELIXER: on every move. */
        val PP_ALL_MOVES = mapOf(0x52 to 10, 0x53 to ALL)
        const val PP_UP = 0x4F
        /** REPEL, SUPER REPEL, MAX REPEL and their steps. */
        val REPELS = mapOf(0x1E to 100, 0x38 to 200, 0x39 to 250)
    }
}

/**
 * Yellow's starter Pikachu and the bag (pokeyellow): IsThisPartyMonStarterPikachu (a PIKACHU with the player's ID and the
 * first five letters of the player's name as OT), ModifyPikachuHappiness's PIKAHAPPY_USEDITEM (its HappinessChangeTable row
 * by happiness / 100, its PikachuMoods entry, the mood raised only while wPikachuEmotionModifier is 0) and
 * RespawnOverworldPikachu.
 */
class Gen1PikachuItems(
    val playerName: Long,
    val playerId: Long,
    val happiness: Long,
    val mood: Long,
    val emotionModifier: Long,
    val spawnState: Long,
    /** HappinessChangeTable and PikachuMoods (bank 0x3D) as cart offsets. */
    val happinessTable: Long = 0x3D * 0x4000L + (0x4385 - 0x4000),
    val moods: Long = 0x3D * 0x4000L + (0x43A6 - 0x4000),
) {
    private fun u8(g: GameCalls, addr: Long) = g.read(addr, 1)[0].toInt() and 0xFF

    fun isStarter(g: GameCalls, mon: Long, ot: Long): Boolean {
        if (u8(g, mon) != PIKACHU) return false
        if (!g.read(mon + 12, 2).contentEquals(g.read(playerId, 2))) return false
        return g.read(ot, 5).contentEquals(g.read(playerName, 5))
    }

    /** PIKAHAPPY_USEDITEM's (happiness, mood) after the change, null without the cart's tables. */
    fun usedItem(g: GameCalls): Pair<Int, Int>? {
        val row = g.rom(happinessTable + (USED_ITEM - 1) * 3L, 3) ?: return null
        val moodTo = g.rom(moods + (USED_ITEM - 1).toLong(), 1)?.let { it[0].toInt() and 0xFF } ?: return null
        val now = u8(g, happiness)
        val d = row[if (now < 100) 0 else if (now < 200) 1 else 2].toInt() and 0xFF
        val sum = now + d
        // "cp 100": 100 and over are the negative changes.
        val next = if (d < 100) minOf(sum, 0xFF) else if (sum > 0xFF) sum - 0x100 else 0
        var mood = u8(g, mood)
        if (moodTo != 0x80) {
            if (moodTo > 0x80) { if (mood < moodTo && u8(g, emotionModifier) == 0) mood = moodTo }
            else if (mood >= moodTo) mood = moodTo
        }
        return next to mood
    }

    fun apply(g: GameCalls, v: Pair<Int, Int>) {
        g.write(happiness, byteArrayOf(v.first.toByte()))
        g.write(mood, byteArrayOf(v.second.toByte()))
    }

    fun respawn(g: GameCalls) = g.write(spawnState, byteArrayOf(SPAWN_DELAYED.toByte()))

    private companion object {
        const val PIKACHU = 0x54
        const val USED_ITEM = 2
        const val SPAWN_DELAYED = 3
    }
}

/** RemoveItemFromInventory_ (bank 3 on Red / Blue, its RAM in it) and the Moves table's first move, POUND. */
private const val GEN1_MOVES_POUND = "01002800ff2302003200ff19"

/** English Red / Blue: pokered.sym. */
val GEN1_ITEM_CALLS_RED = Gen1ItemCalls(
    at = 0x0405, removeItem = 0x2BBB, ret = 0x35EB,
    whichPokemon = 0xCF92L, itemQuantity = 0xCF96L, numBagItems = 0xD31DL, partyCount = 0xD163L, repelSteps = 0xD0DBL,
    signatures = listOf(
        0x2BBBL to "f0b8f53e03e0b8ea0020cd744ef1e0b8",           // RemoveItemFromInventory: homecall bank 3
        0x03 * 0x4000L + (0x4E74 - 0x4000) to "e523fa92cfcb27856f30012423fa96cf",   // RemoveItemFromInventory_
        0x35EBL to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
    ),
)

/** German Rote / Blaue Edition: Red's code, its RAM + 5 from wOnSGB up, the home bank moved. */
val GEN1_ITEM_CALLS_RED_DE = Gen1ItemCalls(
    at = 0x0405, removeItem = 0x2BD5, ret = 0x3605,
    whichPokemon = 0xCF97L, itemQuantity = 0xCF9BL, numBagItems = 0xD322L, partyCount = 0xD168L, repelSteps = 0xD0E0L,
    signatures = listOf(
        0x2BD5L to "f0b8f53e03e0b8ea0020cd744ef1e0b8",
        0x03 * 0x4000L + (0x4E74 - 0x4000) to "e523fa97cfcb27856f30012423fa9bcf",
        0x3605L to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
    ),
)

/** HappinessChangeTable's rows and PikachuMoods (bank 0x3D), the same in English and German Yellow. */
private const val YELLOW_HAPPINESS = "050302050302010100030201010100020101fdfdfbfffffffbfbf6fbfbf6f6f6ec8a8380809480626c626c00"

/** English Yellow: pokeyellow.sym. */
val GEN1_ITEM_CALLS_YELLOW = Gen1ItemCalls(
    at = 0x0248, removeItem = 0x2ABD, ret = 0x3E97,
    whichPokemon = 0xCF91L, itemQuantity = 0xCF95L, numBagItems = 0xD31CL, partyCount = 0xD162L, repelSteps = 0xD0DAL,
    pikachu = Gen1PikachuItems(playerName = 0xD157L, playerId = 0xD358L, happiness = 0xD46FL, mood = 0xD470L,
        emotionModifier = 0xD49BL, spawnState = 0xD430L),
    signatures = listOf(
        0x2ABDL to "f0b8f53e3dcd7e3ecde15bf1cd7e3ec9",           // RemoveItemFromInventory: homecall bank 0x3D
        0x3D * 0x4000L + (0x5BE1 - 0x4000) to "e523fa91cf87856f30012423fa95cf",     // RemoveItemFromInventory_
        0x3E97L to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
        0x3D * 0x4000L + (0x4385 - 0x4000) to YELLOW_HAPPINESS,
        0x3D * 0x4000L + (0x430A - 0x4000) to "7afe042811fe06280dd521184e063fcd843ed1d0180bd521b84d063fcd843ed1d0d51e00fa6fd4fe",
    ),
)

/** German Gelbe Edition: Yellow's code, its RAM + 5, the home bank moved. */
val GEN1_ITEM_CALLS_YELLOW_DE = Gen1ItemCalls(
    at = 0x0248, removeItem = 0x2AC2, ret = 0x3E9C,
    whichPokemon = 0xCF96L, itemQuantity = 0xCF9AL, numBagItems = 0xD321L, partyCount = 0xD167L, repelSteps = 0xD0DFL,
    pikachu = Gen1PikachuItems(playerName = 0xD15CL, playerId = 0xD35DL, happiness = 0xD474L, mood = 0xD475L,
        emotionModifier = 0xD4A0L, spawnState = 0xD435L),
    signatures = listOf(
        0x2AC2L to "f0b8f53e3dcd833ecdda5bf1cd833ec9",
        0x3D * 0x4000L + (0x5BDA - 0x4000) to "e523fa96cf87856f30012423fa9acf",
        0x3E9CL to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
        0x3D * 0x4000L + (0x4385 - 0x4000) to YELLOW_HAPPINESS,
        0x3D * 0x4000L + (0x430A - 0x4000) to "7afe042811fe06280dd521184e063fcd893ed1d0180bd521b84d063fcd893ed1d0d51e00fa74d4fe",
    ),
)
