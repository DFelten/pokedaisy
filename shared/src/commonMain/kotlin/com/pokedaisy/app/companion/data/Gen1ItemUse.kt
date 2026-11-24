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
 * - TMs / HMs (ItemUseTMHM): a Pokémon whose base data has the machine's bit (CanLearnTM: the cart's BaseStats, Red's MEW
 *   in MewBaseStats) and that doesn't know the move (CheckIfMoveIsKnown); LearnMove puts it in the first empty slot, else
 *   in the one picked to forget (never an HM's move: IsMoveHM), its PP the move's base PP (PP Ups gone). A TM is spent, an
 *   HM stays; with an HM the loop is checked first, so the move is taught only on the field. On Yellow the starter Pikachu
 *   gets PIKAHAPPY_USEDTMHM, and THUNDERBOLT / THUNDER make it glad (wPikachuEmotionModifier 5, wPikachuMood 0x85).
 * - The BICYCLE (ItemUseBicycle): not while surfing (or riding where BIT_ALWAYS_ON_BIKE holds the player on it, as the
 *   START menu refuses); off = walking, on = only where IsBikeRidingAllowed (ROUTE 23, INDIGO PLATEAU or a tileset in
 *   BikeRidingTilesets), then PlayDefaultMusic and LoadPlayerSpriteGraphics (which the game's text box closing runs) from
 *   the loop; Yellow's Pikachu comes back on getting off (PIKACHU_SPAWN_DEFAULT).
 *
 * Left out: the vitamins, RARE CANDY and the stones (their stats / evolution are the game's own sums and screens), the
 * rods and the ESCAPE ROPE.
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
    val machines: Gen1Machines,
    val bike: Gen1Bike,
    val signatures: List<Pair<Long, String>>,
) : GbBagUse {
    override fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = signatures.all { (off, hex) ->
        val want = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        rom(off, want.size)?.contentEquals(want) == true
    }

    override fun usable(itemId: Int) = itemId in HP_ITEMS || itemId in STATUS_ITEMS || itemId == FULL_RESTORE ||
        itemId in REVIVES || itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES || itemId == PP_UP || itemId in REPELS ||
        tmIndex(itemId) >= 0 || itemId == BICYCLE
    override fun inGame(itemId: Int) = false
    override fun picksMon(itemId: Int) = usable(itemId) && itemId !in REPELS && itemId != BICYCLE
    override fun picksMove(itemId: Int) = itemId in PP_ONE_MOVE || itemId == PP_UP || tmIndex(itemId) >= 0

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

    /** The move TM / HM [tm] (TechnicalMachines' index: TM01-50, then HM01-05) teaches. */
    private fun tmMove(g: GameCalls, tm: Int) = g.rom(machines.technicalMachines + tm, 1)?.let { u8(it, 0) } ?: 0

    /** CanLearnTM: [tm]'s bit in the species' base data (by its Pokédex number), and CheckIfMoveIsKnown. */
    private fun tmVerdict(g: GameCalls, p: Party, slot: Int, tm: Int): SlotVerdict {
        val o = slot * MON_SIZE
        val bits = machines.learnset(g, u8(p.mons, o)) ?: return SlotVerdict.NO_EFFECT
        val move = tmMove(g, tm)
        val knows = (0 until 4).any { u8(p.mons, o + MOVES + it) == move }
        return if ((u8(bits, tm / 8) shr (tm % 8)) and 1 == 1 && move != 0 && !knows) SlotVerdict.OK else SlotVerdict.NO_EFFECT
    }

    /** With four moves, the ones it may forget for a TM / HM (not an HM's move); else none (LearnMove takes the empty slot). */
    private fun forgettable(g: GameCalls, p: Party, slot: Int): List<Boolean> {
        val moves = (0 until 4).map { u8(p.mons, slot * MON_SIZE + MOVES + it) }
        if (moves.any { it == 0 }) return emptyList()
        val hms = (NUM_TMS until NUM_TMS + NUM_HMS).map { tmMove(g, it) }.toSet()
        return moves.map { it !in hms }
    }

    override fun targets(g: GameCalls, itemId: Int): PartyTargets {
        if (!picksMon(itemId)) return PartyTargets(ItemUseResult.FAILED)
        if (bagSlot(g, itemId) < 0) return PartyTargets(ItemUseResult.NONE_LEFT)
        val p = party(g)
        val tm = tmIndex(itemId)
        return PartyTargets(ItemUseResult.USED, move = if (tm >= 0) tmMove(g, tm) else 0, targets = (0 until p.count).map { slot ->
            PartyTarget(slot, gen1MonKey(p.mons, slot * MON_SIZE), 0,
                if (tm >= 0) tmVerdict(g, p, slot, tm) else verdict(g, p, slot, itemId),
                when {
                    tm >= 0 -> forgettable(g, p, slot)
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
        if (!settle(g)) return false
        return quantity(g, itemId) == before - 1
    }

    override fun use(g: GameCalls, itemId: Int, target: PartyTarget?, moveIndex: Int): ItemUseOutcome = when {
        target != null -> if (tmIndex(itemId) >= 0) teach(g, itemId, target, moveIndex) else useOn(g, itemId, target, moveIndex)
        itemId == BICYCLE -> useBike(g)
        else -> useRepel(g, itemId)
    }

    /** The loop coming by: the game is on the field and whatever was called before has returned. */
    private fun settle(g: GameCalls) = (0 until SETTLE_TRIES).any { g.gbCallAt(at, ret, 0, 0, 0) }

    /** TM / HM [itemId] taught to [target], forgetting move [moveIndex] when it has four (see the class's notes). */
    fun teach(g: GameCalls, itemId: Int, target: PartyTarget, moveIndex: Int): ItemUseOutcome {
        val tm = tmIndex(itemId).takeIf { it >= 0 } ?: return ItemUseOutcome(ItemUseResult.FAILED)
        val p = party(g)
        val slot = target.slot
        if (slot !in 0 until p.count || gen1MonKey(p.mons, slot * MON_SIZE) != target.personality) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        if (tmVerdict(g, p, slot, tm) != SlotVerdict.OK) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        val forget = forgettable(g, p, slot)
        if (forget.isNotEmpty() && forget.getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        val move = tmMove(g, tm)
        val pp = basePp(g, move).takeIf { it > 0 } ?: return ItemUseOutcome(ItemUseResult.FAILED)
        val starter = pikachu?.takeIf { it.isStarter(g, partyMons + slot * MON_SIZE, partyOt + slot * NAME_LENGTH) }
        val happy = if (starter != null) starter.change(g, Gen1PikachuItems.USED_TMHM) ?: return ItemUseOutcome(ItemUseResult.FAILED) else null
        if (bagSlot(g, itemId) < 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (tm < NUM_TMS) { if (!remove(g, itemId)) return ItemUseOutcome(ItemUseResult.FAILED) }
        else if (!settle(g)) return ItemUseOutcome(ItemUseResult.FAILED)
        val o = slot * MON_SIZE
        val into = (0 until 4).firstOrNull { u8(p.mons, o + MOVES + it) == 0 } ?: moveIndex
        val mon = partyMons + o
        g.write(mon + MOVES + into, byteArrayOf(move.toByte()))
        g.write(mon + PP + into, byteArrayOf(pp.toByte()))
        if (starter != null && happy != null) {
            starter.apply(g, happy)
            if (itemId == TM_THUNDERBOLT || itemId == TM_THUNDER) starter.glad(g)
        }
        return ItemUseOutcome(ItemUseResult.USED)
    }

    /** The BICYCLE on the spot: on or off by ItemUseBicycle's rules (see the class's notes). */
    fun useBike(g: GameCalls): ItemUseOutcome {
        if (bagSlot(g, BICYCLE) < 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        val b = bike
        val state = u8(g.read(b.walkBikeSurfState, 1), 0)
        val off = state == 1
        if (state == 2) return ItemUseOutcome(ItemUseResult.FAILED)
        if (off && (u8(g.read(b.statusFlags6, 1), 0) shr ALWAYS_ON_BIKE) and 1 == 1) return ItemUseOutcome(ItemUseResult.FAILED)
        if (!off && !b.allowed(g)) return ItemUseOutcome(ItemUseResult.FAILED)
        if (!g.gbCallAt(at, b.playDefaultMusic, 0, 0, 0)) return ItemUseOutcome(ItemUseResult.FAILED)
        g.write(b.walkBikeSurfState, byteArrayOf(if (off) 0 else 1))
        if (off) pikachu?.let { g.write(it.spawnState, byteArrayOf(0)) }
        // PlayDefaultMusic picks the song by the state written above; the player's sprite follows it.
        if (!(0 until SETTLE_TRIES).any { g.gbCallAt(at, b.loadPlayerSpriteGraphics, 0, 0, 0) }) return ItemUseOutcome(ItemUseResult.FAILED)
        if (!settle(g)) return ItemUseOutcome(ItemUseResult.FAILED)
        return ItemUseOutcome(ItemUseResult.USED)
    }

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
        val happy = if (starter != null && itemId <= CALCIUM) starter.change(g, Gen1PikachuItems.USED_ITEM) ?: return ItemUseOutcome(ItemUseResult.FAILED) else null
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
        const val BICYCLE = 0x06
        private const val NUM_TMS = 50
        private const val NUM_HMS = 5
        private const val HM01 = 0xC4
        private const val TM01 = 0xC9
        private const val TM_THUNDERBOLT = 0xE0
        private const val TM_THUNDER = 0xE1
        private const val ALWAYS_ON_BIKE = 5
        /** TechnicalMachines' index of a TM / HM item (TM01-50 = 0-49, HM01-05 = 50-54), -1 for anything else. */
        fun tmIndex(itemId: Int) = when (itemId) {
            in TM01 until TM01 + NUM_TMS -> itemId - TM01
            in HM01 until TM01 -> NUM_TMS + itemId - HM01
            else -> -1
        }
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

    /** ModifyPikachuHappiness's [which] (PIKAHAPPY_*): (happiness, mood) after the change, null without the cart's tables. */
    fun change(g: GameCalls, which: Int): Pair<Int, Int>? {
        val row = g.rom(happinessTable + (which - 1) * 3L, 3) ?: return null
        val moodTo = g.rom(moods + (which - 1).toLong(), 1)?.let { it[0].toInt() and 0xFF } ?: return null
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

    /** ItemUseTMHM's THUNDERBOLT / THUNDER on the starter: wPikachuEmotionModifier 5, wPikachuMood 0x85. */
    fun glad(g: GameCalls) {
        g.write(emotionModifier, byteArrayOf(5))
        g.write(mood, byteArrayOf(0x85.toByte()))
    }

    companion object {
        private const val PIKACHU = 0x54
        /** PIKAHAPPY_USEDITEM, PIKAHAPPY_USEDTMHM. */
        const val USED_ITEM = 2
        const val USED_TMHM = 5
        private const val SPAWN_DELAYED = 3
    }
}

/** The TM / HM tables: TechnicalMachines, the base data by Pokédex number (PokedexOrder: internal id -> number). */
class Gen1Machines(
    val technicalMachines: Long,
    val pokedexOrder: Long,
    /** Red / Blue keep MEW's base data apart (MewBaseStats); Yellow has it in BaseStats (null). */
    val mewBaseStats: Long? = null,
    val baseStats: Long = 0x0E * 0x4000L + (0x43DE - 0x4000),
) {
    /** The species' 7 bytes of TM / HM bits (BASE_TMHM), null for one without a Pokédex number. */
    fun learnset(g: GameCalls, species: Int): ByteArray? {
        if (species == 0) return null
        val dex = g.rom(pokedexOrder + species - 1, 1)?.let { it[0].toInt() and 0xFF } ?: return null
        if (dex !in 1..MEW) return null
        val base = if (dex == MEW && mewBaseStats != null) mewBaseStats else baseStats + (dex - 1) * BASE_SIZE
        return g.rom(base + BASE_TMHM, 7)
    }

    private companion object {
        const val MEW = 151
        const val BASE_SIZE = 28
        const val BASE_TMHM = 20
    }
}

/** The BICYCLE: the home bank's PlayDefaultMusic / LoadPlayerSpriteGraphics / BikeRidingTilesets and their RAM. */
class Gen1Bike(
    val playDefaultMusic: Int,
    val loadPlayerSpriteGraphics: Int,
    val bikeRidingTilesets: Int,
    val curMap: Long,
    val curMapTileset: Long,
    val walkBikeSurfState: Long,
    val statusFlags6: Long,
) {
    /** IsBikeRidingAllowed: ROUTE 23, INDIGO PLATEAU, or a tileset in BikeRidingTilesets (0xFF-terminated). */
    fun allowed(g: GameCalls): Boolean {
        val map = g.read(curMap, 1)[0].toInt() and 0xFF
        if (map == ROUTE_23 || map == INDIGO_PLATEAU) return true
        val tileset = g.read(curMapTileset, 1)[0].toInt() and 0xFF
        val list = g.rom(bikeRidingTilesets.toLong(), 16) ?: return false
        for (b in list) { val t = b.toInt() and 0xFF; if (t == 0xFF) return false; if (t == tileset) return true }
        return false
    }

    private companion object {
        const val ROUTE_23 = 0x22
        const val INDIGO_PLATEAU = 0x09
    }
}

/** RemoveItemFromInventory_ (bank 3 on Red / Blue, its RAM in it) and the Moves table's first move, POUND. */
private const val GEN1_MOVES_POUND = "01002800ff2302003200ff19"

/** TechnicalMachines' first ten moves and BULBASAUR's base data, the same in every Gen 1 cart. */
private const val GEN1_TMS = "050d0e12195c20222426"
private const val GEN1_BULBASAUR = "012d31312d4116032d40550040"
/** IsBikeRidingAllowed's code past its first RAM read, then BikeRidingTilesets. */
private const val GEN1_BIKE_TAIL = "fe222814fe092810fa"

/** English Red / Blue: pokered.sym. */
val GEN1_ITEM_CALLS_RED = Gen1ItemCalls(
    at = 0x0405, removeItem = 0x2BBB, ret = 0x35EB,
    whichPokemon = 0xCF92L, itemQuantity = 0xCF96L, numBagItems = 0xD31DL, partyCount = 0xD163L, repelSteps = 0xD0DBL,
    machines = Gen1Machines(technicalMachines = 0x04 * 0x4000L + (0x7773 - 0x4000), pokedexOrder = 0x10 * 0x4000L + (0x5024 - 0x4000),
        mewBaseStats = 0x01 * 0x4000L + (0x425B - 0x4000)),
    bike = Gen1Bike(playDefaultMusic = 0x2307, loadPlayerSpriteGraphics = 0x0997, bikeRidingTilesets = 0x09E2,
        curMap = 0xD35EL, curMapTileset = 0xD367L, walkBikeSurfState = 0xD700L, statusFlags6 = 0xD732L),
    signatures = listOf(
        0x2BBBL to "f0b8f53e03e0b8ea0020cd744ef1e0b8",           // RemoveItemFromInventory: homecall bank 3
        0x03 * 0x4000L + (0x4E74 - 0x4000) to "e523fa92cfcb27856f30012423fa96cf",   // RemoveItemFromInventory_
        0x35EBL to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
        0x04 * 0x4000L + (0x7773 - 0x4000) to GEN1_TMS,
        0x10 * 0x4000L + (0x5024 - 0x4000) to "7073202315642250",           // PokedexOrder: RHYDON, KANGASKHAN, ...
        0x0E * 0x4000L + (0x43DE - 0x4000) to GEN1_BULBASAUR,
        0x01 * 0x4000L + (0x425B - 0x4000) to "97646464646418182d",          // MewBaseStats
        0x2307L to "cd4837af4f57eacacf18120e",                               // PlayDefaultMusic
        0x0997L to "fa00d73d2807f0d7a7201118",                               // LoadPlayerSpriteGraphics
        0x09C5L to "fa5ed3$GEN1_BIKE_TAIL" + "67d34721e2092ab828053c20f9a7c937c900030b0e11ff",
    ),
)

/** German Rote / Blaue Edition: Red's code, its RAM + 5 from wOnSGB up, the home bank moved. */
val GEN1_ITEM_CALLS_RED_DE = Gen1ItemCalls(
    at = 0x0405, removeItem = 0x2BD5, ret = 0x3605,
    whichPokemon = 0xCF97L, itemQuantity = 0xCF9BL, numBagItems = 0xD322L, partyCount = 0xD168L, repelSteps = 0xD0E0L,
    machines = Gen1Machines(technicalMachines = 0x04 * 0x4000L + (0x7774 - 0x4000), pokedexOrder = 0x10 * 0x4000L + (0x4F96 - 0x4000),
        mewBaseStats = 0x01 * 0x4000L + (0x425B - 0x4000)),
    bike = Gen1Bike(playDefaultMusic = 0x2307, loadPlayerSpriteGraphics = 0x0997, bikeRidingTilesets = 0x09E2,
        curMap = 0xD363L, curMapTileset = 0xD36CL, walkBikeSurfState = 0xD705L, statusFlags6 = 0xD737L),
    signatures = listOf(
        0x2BD5L to "f0b8f53e03e0b8ea0020cd744ef1e0b8",
        0x03 * 0x4000L + (0x4E74 - 0x4000) to "e523fa97cfcb27856f30012423fa9bcf",
        0x3605L to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
        0x04 * 0x4000L + (0x7774 - 0x4000) to GEN1_TMS,
        0x10 * 0x4000L + (0x4F96 - 0x4000) to "7073202315642250",
        0x0E * 0x4000L + (0x43DE - 0x4000) to GEN1_BULBASAUR,
        0x01 * 0x4000L + (0x425B - 0x4000) to "97646464646418182d",
        0x2307L to "cd6237af4f57eacfcf18120e",
        0x0997L to "fa05d73d2807f0d7a7201118",
        0x09C5L to "fa63d3$GEN1_BIKE_TAIL" + "6cd34721e2092ab828053c20f9a7c937c900030b0e11ff",
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
    machines = Gen1Machines(technicalMachines = 0x04 * 0x4000L + (0x632D - 0x4000), pokedexOrder = 0x10 * 0x4000L + (0x50B1 - 0x4000)),
    bike = Gen1Bike(playDefaultMusic = 0x216B, loadPlayerSpriteGraphics = 0x07D7, bikeRidingTilesets = 0x0822,
        curMap = 0xD35DL, curMapTileset = 0xD366L, walkBikeSurfState = 0xD6FFL, statusFlags6 = 0xD731L),
    signatures = listOf(
        0x2ABDL to "f0b8f53e3dcd7e3ecde15bf1cd7e3ec9",           // RemoveItemFromInventory: homecall bank 0x3D
        0x3D * 0x4000L + (0x5BE1 - 0x4000) to "e523fa91cf87856f30012423fa95cf",     // RemoveItemFromInventory_
        0x3E97L to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
        0x3D * 0x4000L + (0x4385 - 0x4000) to YELLOW_HAPPINESS,
        0x3D * 0x4000L + (0x430A - 0x4000) to "7afe042811fe06280dd521184e063fcd843ed1d0180bd521b84d063fcd843ed1d0d51e00fa6fd4fe",
        0x04 * 0x4000L + (0x632D - 0x4000) to GEN1_TMS,
        0x10 * 0x4000L + (0x50B1 - 0x4000) to "7073202315642250",
        0x0E * 0x4000L + (0x43DE - 0x4000) to GEN1_BULBASAUR,
        0x216BL to "cd3e37af4f57eac9cf18120e",
        0x07D7L to "faffd63d2807f0d7a7201118",
        0x0805L to "fa5dd3$GEN1_BIKE_TAIL" + "66d3472122082ab828053c20f9a7c937c900030b0e11ff",
    ),
)

/** German Gelbe Edition: Yellow's code, its RAM + 5, the home bank moved. */
val GEN1_ITEM_CALLS_YELLOW_DE = Gen1ItemCalls(
    at = 0x0248, removeItem = 0x2AC2, ret = 0x3E9C,
    whichPokemon = 0xCF96L, itemQuantity = 0xCF9AL, numBagItems = 0xD321L, partyCount = 0xD167L, repelSteps = 0xD0DFL,
    pikachu = Gen1PikachuItems(playerName = 0xD15CL, playerId = 0xD35DL, happiness = 0xD474L, mood = 0xD475L,
        emotionModifier = 0xD4A0L, spawnState = 0xD435L),
    machines = Gen1Machines(technicalMachines = 0x04 * 0x4000L + (0x632E - 0x4000), pokedexOrder = 0x10 * 0x4000L + (0x5023 - 0x4000)),
    bike = Gen1Bike(playDefaultMusic = 0x2170, loadPlayerSpriteGraphics = 0x07D7, bikeRidingTilesets = 0x0822,
        curMap = 0xD362L, curMapTileset = 0xD36BL, walkBikeSurfState = 0xD704L, statusFlags6 = 0xD736L),
    signatures = listOf(
        0x2AC2L to "f0b8f53e3dcd833ecdda5bf1cd833ec9",
        0x3D * 0x4000L + (0x5BDA - 0x4000) to "e523fa96cf87856f30012423fa9acf",
        0x3E9CL to "c9",
        0x0E * 0x4000L to GEN1_MOVES_POUND,
        0x3D * 0x4000L + (0x4385 - 0x4000) to YELLOW_HAPPINESS,
        0x3D * 0x4000L + (0x430A - 0x4000) to "7afe042811fe06280dd521184e063fcd893ed1d0180bd521b84d063fcd893ed1d0d51e00fa74d4fe",
        0x04 * 0x4000L + (0x632E - 0x4000) to GEN1_TMS,
        0x10 * 0x4000L + (0x5023 - 0x4000) to "7073202315642250",
        0x0E * 0x4000L + (0x43DE - 0x4000) to GEN1_BULBASAUR,
        0x2170L to "cd4337af4f57eacecf18120e",
        0x07D7L to "fa04d73d2807f0d7a7201118",
        0x0805L to "fa62d3$GEN1_BIKE_TAIL" + "6bd3472122082ab828053c20f9a7c937c900030b0e11ff",
    ),
)
