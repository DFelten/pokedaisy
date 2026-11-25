package com.pokedaisy.app.companion.data

/**
 * USE on Crystal's ITEMS tab (English rev 0 / rev 1 / Australia, German Kristall) and Gold / Silver's (English and German:
 * pret/pokegold's engine is Crystal's, every address its own): the medicine, the PP items and the Repels, by pokecrystal's
 * engine/items/item_effects.asm. Its effect routines can't be called on their own - each opens
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
 *   PP, its base PP from the cart's Moves table plus min(base / 5, 7) for each PP Up (ComputeMaxPP), the PP Up bits kept;
 *   a fainted Pokémon too (RestorePPEffect doesn't check).
 * - Repels: wRepelEffect set to 100 / 200 / 250 steps, refused while one still works (UseRepel).
 * - The bitter herbs: ENERGYPOWDER / ENERGY ROOT as HP items, HEAL POWDER as a status cure, REVIVAL HERB as a full revive,
 *   then the friendship they cost (ChangeHappiness: HappinessChanges' row from the cart by the friendship's band).
 * - The vitamins (VitaminEffect): no effect once that stat's experience has reached 25600 (its high byte 100), else +2560,
 *   the stats worked out again by the game's own UpdateStatsAfterItem (after RareCandy_StatBooster_GetParameters, from
 *   WRAM0 like the TM routine: [vitaminRoutine]), then a vitamin's friendship. An egg can't; a fainted Pokémon can.
 * - PP UP (RestorePPEffect): one move, not SKETCH, with fewer than three PP Ups; its PP Up count + 1 and ApplyPPUp's
 *   ComputeMaxPP, which with wUsePPUp (= wTempPP) at 1 adds min(base PP / 5, 7) to the move's PP once.
 * - RARE CANDY and the stones ([levelOrStone]): the Pokémon picked here (below level 100; a stone only for one whose
 *   EvosAttacks has that stone and that doesn't hold an EVERSTONE), then the game's own screens from WRAM0 as SELECT
 *   opens a party item - its party menu drawn without its pick (ChooseMonToUseItemOn up to PartyMenuSelect), the picked
 *   Pokémon in wCurPartyMon / wCurPartySpecies, and RareCandyEffect / EvoStoneEffect entered just past their
 *   UseItem_SelectMon: the level-up text, the stats, LearnLevelMoves, the evolution, the item spent ([partyItemRoutine]).
 *
 * - The BICYCLE and the rods: their own field effects ([onTheSpot]).
 *
 * Everything else the game's bag can USE goes the game's own way ([openInGame], [fieldEffect]), as SELECT runs a registered
 * item (engine/overworld/select_menu.asm's UseRegisteredItem, far-called from HandleMap with wCurItem set): the COIN CASE
 * and BLUE CARD (ITEMMENU_CURRENT: their text over the map), SACRED ASH, ESCAPE
 * ROPE, ITEMFINDER, SQUIRTBOTTLE and the two keys (ITEMMENU_CLOSE: the script their effect queues runs from the map's queue,
 * or the game's "not the time" text).
 *
 * TMs and HMs ([teach]): the Pokémon is picked here - who can learn it by the cart's base data (CanLearnTMHMMove), not one
 * that knows it (KnowsMove) - and, with four moves, the move it forgets (never an HM's, as ForgetMove refuses). The game's own
 * TeachTMHM runs from WRAM0 over the map ([Gen2TmFns.routine]): its text, the TM spent. The routine empties the forgotten slot
 * just before, so LearnMove puts the new move in its place without its forget menu (which leaves the map's colours wrong over
 * the field).
 *
 * Left out: NORMAL BOX and GORGEOUS BOX (Mystery Gift's, their text needs the bag's window).
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
    val curPartySpecies: Long = wramx(1, 0xD108),
    val curPartyMon: Long = wramx(1, 0xD109),
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
    /** UseRegisteredItem (bank 4): SELECT's item use, by the item's menu type. */
    val useRegisteredItem: Int = 0x73C3,
    /** The functions the TM routine calls. */
    val tm: Gen2TmFns = Gen2TmFns(),
    /** RareCandy_StatBooster_GetParameters and UpdateStatsAfterItem (bank 3): a vitamin's stats worked out again. */
    val statBoosterParams: Int = 0x6EF5,
    val updateStatsAfterItem: Int = 0x6E8C,
    /** HappinessChanges (bank 1): three bytes per HAPPINESS_* change, by friendship < 100, < 200, the rest. */
    val happinessChanges: Long = romOff(0x01, 0x7221),
    /** What RARE CANDY and the stones run ([partyItemRoutine]). */
    val party: Gen2PartyItemFns = Gen2PartyItemFns(),
    /** Where the routines run: the stack's far end, which the game's screens never reach (Crystal's wStackBottom in WRAM0,
     * Gold / Silver's at 0xDF03). */
    val routine: Int = ROUTINE,
) {
    /** Every signature's bytes at its place (`??` = any byte), the party items' too. */
    fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = (signatures + party.signatures()).all { (off, hex) ->
        val want = hex.chunked(2)
        val got = rom(off, want.size) ?: return@all false
        want.indices.all { want[it] == "??" || got[it] == want[it].toInt(16).toByte() }
    }

    /** The items USE takes here. */
    fun usable(itemId: Int) = itemId in HP_ITEMS || itemId in STATUS_ITEMS || itemId == FULL_RESTORE ||
        itemId in REVIVES || itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES || itemId in REPELS || onTheSpot(itemId) ||
        inGame(itemId) || fieldEffect(itemId) || itemId in TM_ITEMS || itemId in BITTER || itemId in VITAMINS || itemId == PP_UP ||
        itemId == RARE_CANDY || itemId in STONES

    /** The items whose own screen or text the game shows ([openInGame]): the companion hands over to it. */
    fun inGame(itemId: Int) = itemId in GAME_TEXT

    /** The items whose effect queues a script ([fieldEffect]). */
    fun fieldEffect(itemId: Int) = itemId in GAME_FIELD

    fun picksMon(itemId: Int) = usable(itemId) && itemId !in REPELS && !onTheSpot(itemId) && !inGame(itemId) && !fieldEffect(itemId)

    /** The BICYCLE and the rods: the game's own field effect, which queues a script of its own. */
    fun onTheSpot(itemId: Int) = itemId == BICYCLE || itemId in RODS
    /** A one-move PP item's move, or a TM's move to forget (only offered for a Pokémon with four: [PartyTarget.moves]). */
    fun picksMove(itemId: Int) = itemId in PP_ONE_MOVE || itemId == PP_UP || itemId in TM_ITEMS

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

    /** Which of slot [slot]'s moves PP UP can raise: not SKETCH, fewer than three PP Ups (RestorePPEffect). */
    private fun ppUpMoves(p: Party, slot: Int): List<Boolean> {
        val o = slot * MON_SIZE
        return (0 until 4).map { m ->
            val move = u8(p.mons, o + MOVES + m)
            move != 0 && move != SKETCH && u8(p.mons, o + PP + m) < PP_UP_MASK
        }
    }

    /** Whether [species] evolves with stone [itemId]: an EVOLVE_ITEM entry for it in its EvosAttacks (EvolveAfterBattle). */
    private fun evolvesWith(g: GameCalls, species: Int, itemId: Int): Boolean {
        if (species == 0 || species > LAST_SPECIES) return false
        val ptr = g.rom(party.evosAttacksPointers + (species - 1) * 2L, 2) ?: return false
        val evos = g.rom(romOff(EVOS_BANK, u8(ptr, 0) or (u8(ptr, 1) shl 8)), EVOS_MAX) ?: return false
        var i = 0
        while (i < evos.size) {
            when (u8(evos, i)) {
                0 -> return false
                EVOLVE_ITEM -> if (i + 1 < evos.size && u8(evos, i + 1) == itemId) return true
            }
            i += if (u8(evos, i) == EVOLVE_STAT) 4 else 3
        }
        return false
    }

    private fun verdict(g: GameCalls, p: Party, slot: Int, itemId: Int): SlotVerdict {
        if (u8(p.species, slot) == EGG) return SlotVerdict.EGG
        val o = slot * MON_SIZE
        val hp = be16(p.mons, o + HP)
        val max = be16(p.mons, o + MAX_HP)
        val status = u8(p.mons, o + STATUS)
        val ok = when {
            itemId in VITAMINS -> u8(p.mons, o + STAT_EXP + VITAMINS.getValue(itemId)) < VITAMIN_CAP
            itemId == PP_UP -> ppUpMoves(p, slot).any { it }
            itemId == RARE_CANDY -> u8(p.mons, o + LEVEL) < MAX_LEVEL
            itemId in STONES -> u8(p.mons, o + HELD_ITEM) != EVERSTONE && evolvesWith(g, u8(p.species, slot), itemId)
            itemId in REVIVES || itemId == REVIVAL_HERB -> hp == 0
            // RestorePPEffect has no fainted check: a fainted Pokémon's moves take PP back too.
            itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES -> ppMoves(g, p, slot).any { it }
            hp == 0 -> false
            itemId == FULL_RESTORE -> hp < max || status != 0
            itemId in HP_ITEMS || itemId in BITTER_HP -> hp < max
            itemId in STATUS_ITEMS || itemId == HEAL_POWDER -> status and (statusMask(g, itemId) ?: 0) != 0
            else -> false
        }
        return if (ok) SlotVerdict.OK else SlotVerdict.NO_EFFECT
    }

    /** USE's party pick for [itemId]. */
    fun targets(g: GameCalls, cfg: Gen2Config, itemId: Int): PartyTargets {
        if (!picksMon(itemId)) return PartyTargets(ItemUseResult.FAILED)
        val tm = TM_ITEMS.indexOf(itemId)
        if ((if (tm >= 0) tmCount(g, cfg, tm) else quantity(g, cfg, itemId)) == 0) return PartyTargets(ItemUseResult.NONE_LEFT)
        val p = party(g, cfg)
        return PartyTargets(ItemUseResult.USED, move = if (tm >= 0) tmMove(g, tm) else 0, targets = (0 until p.count).map { slot ->
            PartyTarget(slot, gen2MonKey(p.mons, slot * MON_SIZE), u8(p.mons, slot * MON_SIZE + HELD_ITEM),
                if (tm >= 0) tmVerdict(g, p, slot, tm) else verdict(g, p, slot, itemId),
                when {
                    tm >= 0 -> forgettable(g, p, slot)
                    itemId in PP_ONE_MOVE -> ppMoves(g, p, slot)
                    itemId == PP_UP -> ppUpMoves(p, slot)
                    else -> emptyList()
                })
        })
    }

    private fun tmCount(g: GameCalls, cfg: Gen2Config, tm: Int) = u8(g.read(cfg.tmsHms + tm, 1), 0)

    /** The move TM / HM [tm] (wTMsHMs' index) teaches: TMHMMoves in the cart. */
    private fun tmMove(g: GameCalls, tm: Int) = g.rom(this.tm.tmHmMoves + tm, 1)?.let { u8(it, 0) } ?: 0

    /** CanLearnTMHMMove: [tm]'s bit in the species' base data; an egg can't, nor a Pokémon that knows it (KnowsMove). */
    private fun tmVerdict(g: GameCalls, p: Party, slot: Int, tm: Int): SlotVerdict {
        val species = u8(p.species, slot)
        if (species == EGG) return SlotVerdict.EGG
        if (species == 0 || species > LAST_SPECIES) return SlotVerdict.NO_EFFECT
        val bits = g.rom(this.tm.baseData + (species - 1) * BASE_DATA_SIZE + BASE_TMHM + tm / 8, 1)?.let { u8(it, 0) } ?: 0
        val move = tmMove(g, tm)
        val knows = (0 until 4).any { u8(p.mons, slot * MON_SIZE + MOVES + it) == move }
        return if ((bits shr (tm % 8)) and 1 == 1 && move != 0 && !knows) SlotVerdict.OK else SlotVerdict.NO_EFFECT
    }

    /** With four moves, the ones it may forget for a TM (not an HM's: ForgetMove's "HM moves can't be forgotten"); else none. */
    private fun forgettable(g: GameCalls, p: Party, slot: Int): List<Boolean> {
        val moves = (0 until 4).map { u8(p.mons, slot * MON_SIZE + MOVES + it) }
        if (moves.any { it == 0 }) return emptyList()
        val hms = (TM_ITEMS.size - HM_COUNT until TM_ITEMS.size).map { tmMove(g, it) }.toSet()
        return moves.map { it !in hms }
    }

    /**
     * TM / HM [itemId] taught to [target], forgetting move [moveIndex] when it has four: the game's TeachTMHM over the map
     * (its text, the TM spent; an HM stays), with wCurPartyMon / wCurPartySpecies the picked Pokémon. The rest is the game's,
     * so this says it started: TEACHING.
     */
    fun teach(g: GameCalls, cfg: Gen2Config, itemId: Int, target: PartyTarget, moveIndex: Int): ItemUseOutcome {
        val tm = TM_ITEMS.indexOf(itemId).takeIf { it >= 0 } ?: return ItemUseOutcome(ItemUseResult.FAILED)
        val p = party(g, cfg)
        val slot = target.slot
        if (slot !in 0 until p.count || gen2MonKey(p.mons, slot * MON_SIZE) != target.personality) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        if (tmVerdict(g, p, slot, tm) != SlotVerdict.OK) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        val forget = forgettable(g, p, slot)
        if (forget.isNotEmpty() && forget.getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (tmCount(g, cfg, tm) == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        // LearnMove takes the first empty slot: the forgotten move's, as its forget menu would have replaced it. The routine
        // empties it right before TeachTMHM, so a call that never runs (a state loaded, the loop gone) leaves the move alone.
        val forgetAt = if (forget.isEmpty()) 0 else ((cfg.partyMons + slot * MON_SIZE + MOVES + moveIndex) and 0xFFFF).toInt()
        if (!g.gbCallAt(at, routine, 0, 0, 0)) return ItemUseOutcome(ItemUseResult.FAILED)
        g.write(routine.toLong(), this.tm.routine(forgetAt))
        g.write(curItem, byteArrayOf(itemId.toByte()))
        g.write(curPartySpecies, byteArrayOf(p.species[slot]))
        g.write(curPartyMon, byteArrayOf(slot.toByte()))
        return ItemUseOutcome(ItemUseResult.TEACHING)
    }

    /**
     * RARE CANDY or a stone on [target]: the game's own screens over the map ([partyItemRoutine]) with wCurItem the item.
     * The rest is the game's - the level-up, a new move, the evolution, the item spent - so this says it started.
     */
    fun levelOrStone(g: GameCalls, cfg: Gen2Config, itemId: Int, target: PartyTarget): ItemUseOutcome {
        val p = party(g, cfg)
        val slot = target.slot
        if (slot !in 0 until p.count || gen2MonKey(p.mons, slot * MON_SIZE) != target.personality) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        if (verdict(g, p, slot, itemId) != SlotVerdict.OK) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (quantity(g, cfg, itemId) == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (!g.gbCallAt(at, routine, 0, 0, 0)) return ItemUseOutcome(ItemUseResult.FAILED)
        g.write(routine.toLong(), partyItemRoutine(itemId, slot, u8(p.species, slot)))
        g.write(curItem, byteArrayOf(itemId.toByte()))
        return ItemUseOutcome(ItemUseResult.USING_IN_GAME)
    }

    /**
     * SELECT's ITEMMENU_PARTY (ReanchorMap, FadeToMenu ... CloseSubmenu, CloseText) with UseItem_SelectMon's party menu
     * drawn but not asked (ClearBGPalettes, ChooseMonToUseItemOn up to PartyMenuSelect; the cursor on [slot]), the
     * Pokémon set, and RareCandyEffect / EvoStoneEffect entered past their pick ([Gen2PartyItemFns.effectEntry]).
     */
    fun partyItemRoutine(itemId: Int, slot: Int, species: Int): ByteArray = with(party) {
        val c = ArrayList<Int>()
        fun le(x: Int) = listOf(x and 0xFF, x shr 8 and 0xFF)
        fun call(x: Int) { c += 0xCD; c += le(x) }
        fun far(bank: Int, x: Int) { c += listOf(0x3E, bank, 0x21) + le(x) + 0xCF }
        fun store(v: Int, addr: Long) { c += listOf(0x3E, v and 0xFF, 0xEA) + le((addr and 0xFFFF).toInt()) }
        call(reanchorMap); call(fadeToMenu)
        store(if (itemId == RARE_CANDY) PARTYMENUACTION_HEALING_ITEM else PARTYMENUACTION_EVO_STONE, partyMenuActionText)
        store(slot + 1, partyMenuCursor)
        call(clearBgPalettes)
        for (f in listOf(loadPartyMenuGfx, initPartyMenuWithCancel, initPartyMenuGfx, writePartyMenuTilemap, placePartyMenuText)) far(PARTY_MENU_BANK, f)
        call(waitBgMap)
        if (getSgbLayout != 0 && itemId != RARE_CANDY) { c += listOf(0x06, SCGB_PARTY_MENU); call(getSgbLayout) }
        call(setDefaultBgpAndObp); call(delayFrame)
        store(slot, curPartyMon); store(species, curPartySpecies)
        far(FIELD_BANK, (if (itemId == RARE_CANDY) rareCandyEffect else evoStoneEffect) + EFFECT_PAST_PICK)
        call(closeSubmenu); call(tm.closeText); c += 0xC9
        ByteArray(c.size) { c[it].toByte() }
    }

    /** How many [itemId] the ITEMS pocket holds. */
    private fun quantity(g: GameCalls, cfg: Gen2Config, itemId: Int): Int {
        val raw = g.read(cfg.numItems, 2 + 2 * cfg.itemsMax)
        val n = u8(raw, 0).coerceAtMost(cfg.itemsMax)
        return (0 until n).filter { u8(raw, 1 + 2 * it) == itemId }.sumOf { u8(raw, 2 + 2 * it) }
    }

    /** One [itemId] out of the bag by the game's TossItem: false = it didn't go (not on the field, or the bag didn't change).
     * [settle] is the next call set up from the loop (a do-nothing one unless the caller has its own). */
    private fun toss(g: GameCalls, cfg: Gen2Config, itemId: Int, before: Int,
                     settle: () -> Boolean = { g.gbCallAt(at, SETTLE, 0, 0, 0) }): Boolean {
        if (!g.gbCallAt(at, tossItem, 0, 0, (cfg.numItems and 0xFFFF).toInt())) return false
        g.write(curItem, byteArrayOf(itemId.toByte()))
        g.write(quantityChange, byteArrayOf(1))
        // The loop coming by again means TossItem has returned.
        if (!(0 until SETTLE_TRIES).any { settle() }) return false
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

    /**
     * USE of an item with its own screen or text ([inGame]): the game's own flow started from the overworld loop, wCurItem
     * set once the call is set up. The rest is the game's - the party pick, the item spent - so this only says it opened:
     * IN_GAME for a screen, USED for a text.
     */
    fun openInGame(g: GameCalls, cfg: Gen2Config, itemId: Int): ItemUseOutcome {
        if (!inGame(itemId)) return ItemUseOutcome(ItemUseResult.FAILED)
        if (itemId !in GAME_TEXT && quantity(g, cfg, itemId) == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        if (!g.gbCallAt(at, FARCALL, USE_ITEM_BANK, 0, useRegisteredItem)) return ItemUseOutcome(ItemUseResult.FAILED)
        g.write(curItem, byteArrayOf(itemId.toByte()))
        return ItemUseOutcome(if (itemId in GAME_TEXT) ItemUseResult.USED else ItemUseResult.IN_GAME)
    }

    /**
     * USE of SACRED ASH, ESCAPE ROPE, ITEMFINDER, SQUIRTBOTTLE or a key ([fieldEffect]): UseRegisteredItem's ITEMMENU_CLOSE
     * (ReanchorMap, the item's effect, which spends it itself), then the script it queued (bank, address) for the map's own
     * queue. Null = not here: the game says so itself ("not the time"), or the loop didn't come back.
     */
    fun fieldEffect(g: GameCalls, cfg: Gen2Config, itemId: Int): Pair<Int, Int>? {
        if (itemId in ITEM_POCKET_FIELD && quantity(g, cfg, itemId) == 0) return null
        if (!g.gbCallAt(at, FARCALL, USE_ITEM_BANK, 0, useRegisteredItem)) return null
        g.write(curItem, byteArrayOf(itemId.toByte()))
        g.write(fieldMoveSucceeded, byteArrayOf(0))
        if (!(0 until REANCHOR_TRIES).any { g.gbCallAt(at, SETTLE, 0, 0, 0) }) return null
        if (g.read(fieldMoveSucceeded, 1)[0].toInt() != 1) return null
        val q = g.read(queuedScript, 3)
        return (q[0].toInt() and 0xFF) to ((q[1].toInt() and 0xFF) or ((q[2].toInt() and 0xFF) shl 8))
    }

    /** USE of [itemId] on [target] ([moveIndex] for a one-move PP item). */
    fun useOn(g: GameCalls, cfg: Gen2Config, itemId: Int, target: PartyTarget, moveIndex: Int): ItemUseOutcome {
        if (!picksMon(itemId)) return ItemUseOutcome(ItemUseResult.FAILED)
        if (itemId in TM_ITEMS) return teach(g, cfg, itemId, target, moveIndex)
        if (itemId == RARE_CANDY || itemId in STONES) return levelOrStone(g, cfg, itemId, target)
        val p = party(g, cfg)
        val slot = target.slot
        if (slot !in 0 until p.count || gen2MonKey(p.mons, slot * MON_SIZE) != target.personality) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        if (verdict(g, p, slot, itemId) != SlotVerdict.OK) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (itemId in PP_ONE_MOVE && ppMoves(g, p, slot).getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        if (itemId == PP_UP && ppUpMoves(p, slot).getOrNull(moveIndex) != true) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
        val happiness = if (itemId in VITAMINS) HAPPINESS_USEDITEM else BITTER[itemId]
        // The friendship change read before anything is spent: a cart without the table is refused, not half done.
        val change = happiness?.let { happinessChange(g, it, u8(p.mons, slot * MON_SIZE + HAPPINESS)) ?: return ItemUseOutcome(ItemUseResult.FAILED) }
        val amount = if (itemId in HP_ITEMS || itemId in BITTER_HP) hpAmount(g, itemId) ?: return ItemUseOutcome(ItemUseResult.FAILED) else 0
        val before = quantity(g, cfg, itemId)
        if (before == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        val vitamin = itemId in VITAMINS
        // A vitamin's stats are the game's own sums (UpdateStatsAfterItem from WRAM0, like the TM routine). That routine is
        // set up as TossItem's settle, so once the item is gone the sums are certain to run, on the stat experience written
        // below before the loop comes by again. If the bag didn't change they run on the old one, which changes nothing.
        val tossed = if (!vitamin) toss(g, cfg, itemId, before) else toss(g, cfg, itemId, before) {
            g.gbCallAt(at, routine, 0, 0, 0).also { set ->
                if (set) {
                    g.write(routine.toLong(), vitaminRoutine())
                    g.write(curPartySpecies, byteArrayOf(p.species[slot]))
                    g.write(curPartyMon, byteArrayOf(slot.toByte()))
                }
            }
        }
        if (!tossed) return ItemUseOutcome(ItemUseResult.FAILED)

        val o = slot * MON_SIZE
        val mon = cfg.partyMons + o
        val hp = be16(p.mons, o + HP)
        val max = be16(p.mons, o + MAX_HP)
        fun writeHp(v: Int) = g.write(mon + HP, byteArrayOf((v shr 8).toByte(), v.toByte()))
        when {
            itemId in REVIVES || itemId == REVIVAL_HERB -> writeHp(if (itemId == REVIVE) max shr 1 else max)
            itemId in VITAMINS -> {
                val exp = STAT_EXP + VITAMINS.getValue(itemId)   // the stat experience's high byte (big-endian)
                g.write(mon + exp, byteArrayOf((u8(p.mons, o + exp) + VITAMIN_STEP).toByte()))
            }
            itemId == PP_UP -> {
                val move = u8(p.mons, o + MOVES + moveIndex)
                val base = g.rom(moves + (move - 1).toLong() * MOVE_LENGTH + MOVE_PP, 1)?.let { u8(it, 0) } ?: 0
                val pp = u8(p.mons, o + PP + moveIndex) + PP_UP_ONE
                g.write(mon + PP + moveIndex, byteArrayOf((pp + minOf(base / 5, 7)).toByte()))
            }
            itemId == FULL_RESTORE -> {
                if (hp < max) writeHp(max)
                g.write(mon + STATUS, byteArrayOf(0, 0))
            }
            itemId in HP_ITEMS || itemId in BITTER_HP -> writeHp(minOf(hp + amount, max))
            itemId in STATUS_ITEMS || itemId == HEAL_POWDER -> g.write(mon + STATUS, byteArrayOf(0))
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
        change?.let { g.write(mon + HAPPINESS, byteArrayOf(it.toByte())) }
        // Waits for the vitamin's sums, so the next read has the new stats. They're already set up, so a loop that's slow to
        // come back only delays them: the item, its stat experience and the stats still go together, USED either way.
        if (vitamin) (0 until SETTLE_TRIES).any { g.gbCallAt(at, SETTLE, 0, 0, 0) }
        return ItemUseOutcome(ItemUseResult.USED)
    }

    /** ChangeHappiness: change [kind]'s row of HappinessChanges by the friendship's band, a byte of 100+ taken off (floor 0),
     * else added (ceiling 255). Null without the cart's table. */
    private fun happinessChange(g: GameCalls, kind: Int, now: Int): Int? {
        val row = g.rom(happinessChanges + (kind - 1) * 3L, 3) ?: return null
        val d = u8(row, if (now < 100) 0 else if (now < 200) 1 else 2)
        return if (d >= 0x64) maxOf(now + d - 0x100, 0) else minOf(now + d, 0xFF)
    }

    /** RareCandy_StatBooster_GetParameters + UpdateStatsAfterItem, both far-called (bank 3), then `ret`: 13 bytes. */
    fun vitaminRoutine(): ByteArray {
        fun far(x: Int) = listOf(0x3E, FIELD_BANK, 0x21, x and 0xFF, x shr 8 and 0xFF, 0xCF)
        val c = far(statBoosterParams) + far(updateStatsAfterItem) + 0xC9
        return ByteArray(c.size) { c[it].toByte() }
    }

    companion object {
        /** rst $10's (Bankswitch's) closing `ret`: a call that does nothing (Gen2BoxCalls' settle). */
        private const val FARCALL = 0x0008   // FarCall_hl: a = bank, hl = address
        private const val SETTLE = 0x0015
        private const val SETTLE_TRIES = 6
        /** ReanchorMap waits a few frames (5 tries headless on Kristall) before the loop is back at HandleMap. */
        private const val REANCHOR_TRIES = 12
        /** UseRegisteredItem's bank. */
        private const val USE_ITEM_BANK = 0x04
        /** Crystal's [routine]: the stack's far end (wStackBottom, WRAM0), which the TM screens never reach (the routine's
         * last bytes run after them, headless). */
        const val ROUTINE = 0xC000
        private const val PARTY_MAX = 6
        private const val MON_SIZE = 0x30
        private const val HELD_ITEM = 0x01
        private const val MOVES = 0x02
        private const val PP = 0x17
        private const val STATUS = 0x20
        private const val HP = 0x22
        private const val MAX_HP = 0x24
        private const val EGG = 0xFD
        private const val LAST_SPECIES = 0xFB
        private const val BASE_DATA_SIZE = 0x20
        private const val BASE_TMHM = 0x18
        private const val HM_COUNT = 7
        private const val PP_MASK = 0x3F
        private const val PP_UP_MASK = 0xC0
        private const val PP_UP_ONE = 0x40
        private const val SKETCH = 0xA6
        private const val STAT_EXP = 0x0B
        private const val HAPPINESS = 0x1B
        private const val VITAMIN_CAP = 100
        private const val VITAMIN_STEP = 10
        private const val LEVEL = 0x1F
        private const val MAX_LEVEL = 100
        private const val EVERSTONE = 0x70
        private const val EVOLVE_ITEM = 2
        private const val EVOLVE_STAT = 5
        private const val EVOS_BANK = 0x10
        /** More than the longest EvosAttacks' evolutions (EEVEE's five) need. */
        private const val EVOS_MAX = 24
        private const val PARTY_MENU_BANK = 0x14
        private const val PARTYMENUACTION_HEALING_ITEM = 1
        private const val PARTYMENUACTION_EVO_STONE = 5
        private const val SCGB_PARTY_MENU = 0x0A
        /** `ld b, PARTYMENUACTION_*; call UseItem_SelectMon; jp c, ...`: where both effects go on with the picked Pokémon. */
        private const val EFFECT_PAST_PICK = 8
        private const val HAPPINESS_USEDITEM = 0x02
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
        /** TM01-TM50, HM01-HM07 in wTMsHMs' order (ITEM_C3 and ITEM_DC sit between them). */
        val TM_ITEMS: List<Int> = (0xBF..0xF9).filter { it != 0xC3 && it != 0xDC }
        const val RARE_CANDY = 0x20
        /** MOON, FIRE, THUNDER, WATER, LEAF, SUN STONE. */
        val STONES = setOf(0x08, 0x16, 0x17, 0x18, 0x22, 0xA9)
        /** HP UP, PROTEIN, IRON, CARBOS, CALCIUM and their stat experience's place (StatExpItemPointerOffsets). */
        val VITAMINS = mapOf(0x1A to 0, 0x1B to 2, 0x1C to 4, 0x1D to 6, 0x1F to 8)
        const val PP_UP = 0x3E
        /** The bitter herbs and the HAPPINESS_* change each costs: ENERGYPOWDER, ENERGY ROOT, HEAL POWDER, REVIVAL HERB. */
        val BITTER = mapOf(0x79 to 0x0F, 0x7A to 0x10, 0x7B to 0x0F, 0x7C to 0x11)
        private val BITTER_HP = setOf(0x79, 0x7A)
        private const val HEAL_POWDER = 0x7B
        private const val REVIVAL_HERB = 0x7C
        /** ITEMMENU_CURRENT items with a text: COIN CASE, BLUE CARD. */
        val GAME_TEXT = setOf(0x36, 0x74)
        /** ITEMMENU_CLOSE items that queue a script: ESCAPE ROPE, SACRED ASH, ITEMFINDER, SQUIRTBOTTLE, CARD KEY, BASEMENT KEY. */
        val GAME_FIELD = setOf(0x13, 0x9C, 0x37, 0xAF, 0x7F, 0x85)
        /** Of those, the ITEMS pocket's (the rest are key items, never spent). */
        private val ITEM_POCKET_FIELD = setOf(0x13, 0x9C)

        fun romOff(bank: Int, addr: Int) = if (bank == 0) addr.toLong() else bank * 0x4000L + (addr - 0x4000)
    }
}

/**
 * The TM routine's functions (bank 0; GetTMHMItemMove and TeachTMHM in bank 0x0B) and the tables the pick reads: BaseData
 * (a species' TM / HM bits at +0x18) and TMHMMoves (the move each teaches), as cart offsets. The routine is TMHMSubmenu's
 * move name (GetTMHMItemMove, GetMoveName, CopyName1) and TeachTMHM between OpenText and CloseText, as SELECT's
 * ITEMMENU_CURRENT runs an item over the map; wOptions' NO_TEXT_SCROLL off meanwhile, as the bag has it.
 */
data class Gen2TmFns(
    val openText: Int = 0x2E08,
    val getMoveName: Int = 0x34F8,
    val copyName1: Int = 0x30D6,
    val closeText: Int = 0x2DCF,
    val getTmhmItemMove: Int = 0x47B6,
    val teach: Int = 0x4867,
    val baseData: Long = Gen2ItemCalls.romOff(0x14, 0x5424),
    val tmHmMoves: Long = Gen2ItemCalls.romOff(0x04, 0x567A),
    /** wTempTMHM, wPutativeTMHMMove and wOptions (Crystal's; Gold / Silver's are their own). */
    val tempTmhm: Int = 0xD265,
    val putativeTmhmMove: Int = 0xD262,
    val options: Int = 0xCFCC,
) {
    /** The routine (SM83, 42 bytes; 47 with [forgetAt], the party move slot it zeroes right before TeachTMHM, so LearnMove
     * fills it), its far calls through FarCall_hl (rst $08). */
    fun routine(forgetAt: Int = 0): ByteArray {
        val c = ArrayList<Int>()
        fun le(x: Int) = listOf(x and 0xFF, x shr 8 and 0xFF)
        fun call(x: Int) { c += 0xCD; c += le(x) }
        fun far(x: Int) { c += listOf(0x3E, TM_BANK, 0x21) + le(x) + 0xCF }
        call(openText)
        far(getTmhmItemMove)
        c += listOf(0xFA) + le(tempTmhm) + 0xEA + le(putativeTmhmMove)   // ld a, [wTempTMHM]; ld [wPutativeTMHMMove], a
        call(getMoveName); call(copyName1)
        c += listOf(0x21) + le(options) + listOf(0x7E, 0xF5, 0xCB, 0xA6)     // ld hl, wOptions; ld a, [hl]; push af; res NO_TEXT_SCROLL, [hl]
        if (forgetAt != 0) c += listOf(0x21) + le(forgetAt) + listOf(0x36, 0x00)   // ld hl, the move; ld [hl], 0 (far() sets hl again)
        far(teach)
        c += listOf(0xF1, 0xEA) + le(options)                                 // pop af; ld [wOptions], a
        call(closeText); c += 0xC9
        return ByteArray(c.size) { c[it].toByte() }
    }

    private companion object {
        const val TM_BANK = 0x0B
    }
}

/**
 * RARE CANDY's and the stones' functions: SELECT's ITEMMENU_PARTY ones (bank 0), ChooseMonToUseItemOn's party menu (bank
 * 0x14), the two effects (bank 3) and EvosAttacksPointers (bank 0x10, a cart offset).
 */
data class Gen2PartyItemFns(
    val fadeToMenu: Int = 0x2B29,
    val closeSubmenu: Int = 0x2B3C,
    val clearBgPalettes: Int = 0x31F3,
    val waitBgMap: Int = 0x31F6,
    val setDefaultBgpAndObp: Int = 0x32F9,
    /** GetSGBLayout, called with SCGB_PARTY_MENU after the party menu is drawn (0 = not called). Gold / Silver's stone menu
     * draws no HP bars, the only part of it that sets the party menu's colours, so the map's palette layout stayed under it
     * (its paths and ledges as green stripes over the menu); Crystal's sets them itself. Not for RARE CANDY: its menu's HP bars
     * set them, and with the call too the candy wasn't spent (checked headless). */
    val getSgbLayout: Int = 0,
    val delayFrame: Int = 0x045A,
    val loadPartyMenuGfx: Int = 0x404F,
    val initPartyMenuWithCancel: Int = 0x4405,
    val initPartyMenuGfx: Int = 0x43E0,
    val writePartyMenuTilemap: Int = 0x405F,
    val placePartyMenuText: Int = 0x449A,
    val rareCandyEffect: Int = 0x6F14,
    val evoStoneEffect: Int = 0x6E0F,
    val evosAttacksPointers: Long = Gen2ItemCalls.romOff(0x10, 0x65B1),
    val partyMenuActionText: Long = wramx(1, 0xD141),
    val partyMenuCursor: Long = wramx(1, 0xD0D8),
    /** EvosAttacksPointers' first three words (BULBASAUR, IVYSAUR, VENUSAUR), as hex. */
    val evosHead: String = "a767c267e167",
    /** The checks instead of Crystal's ([signatures]): Gold / Silver's are in their [Gen2ItemCalls.signatures]. */
    val code: List<Pair<Long, String>>? = null,
) {
    /** Their first bytes, calls' and loads' addresses left out (`??`), and EvosAttacksPointers' first three words. */
    fun signatures(): List<Pair<Long, String>> = code ?: listOf(
        Gen2ItemCalls.romOff(0x03, rareCandyEffect) to "0601cd????da????cd",   // ld b, PARTYMENUACTION_HEALING_ITEM; call UseItem_SelectMon; jp c
        Gen2ItemCalls.romOff(0x03, evoStoneEffect) to "0605cd????da????3e01cd",  // ld b, PARTYMENUACTION_EVO_STONE; ...; ld a, MON_ITEM
        fadeToMenu.toLong() to "afe0d4cd????3e23",
        closeSubmenu.toLong() to "cd????cd????cd",
        clearBgPalettes.toLong() to "cd????3e01e0d40e",
        waitBgMap.toLong() to "3e01e0d40e04cd",
        setDefaultBgpAndObp.toLong() to "f0e6a7200b3ee4e0",
        delayFrame.toLong() to "3e01eab3cf7600",
        Gen2ItemCalls.romOff(0x14, loadPartyMenuGfx) to "cd580e21d14a3e02",
        Gen2ItemCalls.romOff(0x14, initPartyMenuWithCancel) to "afeae3d011??44cd",
        Gen2ItemCalls.romOff(0x14, initPartyMenuGfx) to "21d7dc2aa7c84faf",
        Gen2ItemCalls.romOff(0x14, writePartyMenuTilemap) to "21cccf7ef5cbe6af",
        Gen2ItemCalls.romOff(0x14, placePartyMenuText) to "21b8c5011202cd",
        evosAttacksPointers to evosHead,
    )
}

/** The tables' first bytes and HandleMap, shared by every Crystal build: HealingHPAmounts (FRESH WATER 50), StatusHealingActions
 * (ANTIDOTE), Moves (POUND). */
private fun crystalItemSignatures(handleMap: String, healingHp: Int, statusHealing: Int, moves: Int, oldRod: Int,
                                  useRegisteredItem: Int, getMoveName: Int, copyName1: Int, baseData: Int, tmHmMoves: Int,
                                  statBoosterParams: Int, updateStats: Int, happiness: Int, bike: Int = 0x50B3,
                                  bikeCode: String = "cdbc50e67feaecd0c9cd2151", oldRodCode: String = "1e0018081e0118041e0218003e03218e4fcf",
                                  useRegisteredCode: String = "3e03215354cffa42d1") = listOf(
    0x25 * 0x4000L + (0x6773 - 0x4000) to handleMap,
    Gen2ItemCalls.romOff(0x03, healingHp) to "2e32002f3c0030500010c800",
    Gen2ItemCalls.romOff(0x03, statusHealing) to "09f0080af1100bf2200cf307",
    Gen2ItemCalls.romOff(0x10, moves) to "01002800ff2300",
    Gen2ItemCalls.romOff(0x03, bike) to bikeCode,   // BikeFunction
    Gen2ItemCalls.romOff(0x03, oldRod) to oldRodCode,   // the rods' effects, farcall FishFunction
    Gen2ItemCalls.romOff(0x04, useRegisteredItem) to useRegisteredCode,   // farcall CheckItemMenu, ld a, [wItemAttributeValue]
    getMoveName.toLong() to "e53e02ea61cffa65d2ea",
    copyName1.toLong() to "2186d01a1322fe5020f9",
    Gen2ItemCalls.romOff(0x14, baseData) to "012d31312d414116032d",   // BULBASAUR
    Gen2ItemCalls.romOff(0x04, tmHmMoves) to "df1daecd2e5cc0f9f4ed",  // DYNAMICPUNCH, HEADBUTT, CURSE, ...
    Gen2ItemCalls.romOff(0x04, tmHmMoves + 50) to "0f133946" + "94fa7f",  // the HMs' moves
    Gen2ItemCalls.romOff(0x03, statBoosterParams) to "fa08d1ea60cfea65d23e1f",   // RareCandy_StatBooster_GetParameters
    Gen2ItemCalls.romOff(0x03, updateStats) to "3e24cd",   // UpdateStatsAfterItem: ld a, MON_MAXHP; call GetPartyParamLocation
    Gen2ItemCalls.romOff(0x01, happiness) to "050302050302010100",   // HappinessChanges: a level, a vitamin, an X item
)

/** Crystal (rev 0, rev 1, Australia): pret/pokecrystal's symbols. */
val GEN2_ITEM_CALLS_CRYSTAL = Gen2ItemCalls(
    tossItem = 0x2F53,
    signatures = listOf(0x2F53L to "e5d5c5f09df53e03d7cd0d52c1", 0x2DBAL to "cdbf1ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521087e", 0x73AF, 0x7071, 0x5AFB, 0x75A5, 0x73C3, 0x34F8, 0x30D6, 0x5424, 0x567A,
        0x6EF5, 0x6E8C, 0x7221) + listOf(
            0x2E08L to "cdbf1ff09df53e01d7cd", 0x2DCFL to "f0d8f53e01e0d8cde22d",
            Gen2ItemCalls.romOff(0x0B, 0x47B6) to "cda7473e0fcd832dc921", Gen2ItemCalls.romOff(0x0B, 0x4867) to "3e0ecd832dc5fa09d121",
        ),
)

/** German Crystal (Kristall): English's RAM and _TossItem; TossItem, the tables and Moves a little earlier. */
val GEN2_ITEM_CALLS_CRYSTAL_DE = Gen2ItemCalls(
    tossItem = 0x2F3D,
    signatures = listOf(0x2F3DL to "e5d5c5f09df53e03d7cd0d52c1", 0x2DA4L to "cda91ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521097e", 0x737E, 0x7040, 0x5AEB, 0x7574, 0x73C8, 0x34E2, 0x30C0, 0x540E, 0x5671,
        0x6EC4, 0x6E60, 0x7242) + listOf(
            0x2DF2L to "cda91ff09df53e01d7cd", 0x2DB9L to "f0d8f53e01e0d8cdcc2d",
            Gen2ItemCalls.romOff(0x0B, 0x47B9) to "cdaa473e0fcd6d2dc921", Gen2ItemCalls.romOff(0x0B, 0x486A) to "3e0ecd6d2dc5fa09d121",
        ),
    healingHp = Gen2ItemCalls.romOff(0x03, 0x737E),
    statusHealing = Gen2ItemCalls.romOff(0x03, 0x7040),
    moves = Gen2ItemCalls.romOff(0x10, 0x5AEB),
    reanchorMap = 0x2DA4,
    oldRodEffect = 0x7574,
    useRegisteredItem = 0x73C8,
    tm = Gen2TmFns(openText = 0x2DF2, getMoveName = 0x34E2, copyName1 = 0x30C0, closeText = 0x2DB9, getTmhmItemMove = 0x47B9,
        teach = 0x486A, baseData = Gen2ItemCalls.romOff(0x14, 0x540E), tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5671)),
    statBoosterParams = 0x6EC4,
    updateStatsAfterItem = 0x6E60,
    happinessChanges = Gen2ItemCalls.romOff(0x01, 0x7242),
    party = Gen2PartyItemFns(fadeToMenu = 0x2B13, closeSubmenu = 0x2B26, clearBgPalettes = 0x31DD, waitBgMap = 0x31E0,
        setDefaultBgpAndObp = 0x32E3, initPartyMenuWithCancel = 0x43FB, initPartyMenuGfx = 0x43D6, placePartyMenuText = 0x4490,
        rareCandyEffect = 0x6EE3, evoStoneEffect = 0x6DE3, evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x65A1),
        evosHead = "9767b267d167"),
)

/** Gold (English): pret/pokegold's symbols. */
val GEN2_ITEM_CALLS_GOLD = goldSilverItemCalls(
    tossItem = 0x3069, reanchorMap = 0x2E80, healingHp = 0x7405, statusHealing = 0x70C7,
    moves = 0x5AFE, bikeFunction = 0x50C0, oldRodEffect = 0x75EC, useRegisteredItem = 0x7785,
    statBoosterParams = 0x6F49, updateStatsAfterItem = 0x6EE0, happinessChanges = 0x7300,
    tm = Gen2TmFns(openText = 0x2ECB, getMoveName = 0x373D, copyName1 = 0x317B, closeText = 0x2E95,
        getTmhmItemMove = 0x45C1, teach = 0x4672, baseData = Gen2ItemCalls.romOff(0x14, 0x5B0B),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A66), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C0E, closeSubmenu = 0x2C21, clearBgPalettes = 0x3449,
        waitBgMap = 0x344C, setDefaultBgpAndObp = 0x3546, getSgbLayout = 0x3583, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437A, initPartyMenuGfx = 0x4355,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x440F,
        rareCandyEffect = 0x6F68, evoStoneEffect = 0x6E63,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67BD), evosHead = "b369ce69ed69",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x3069L to "e5d5c5f09ff53e03d7cd",
        0x373DL to "e53e02ea61cefa51d1ea",
        0x317BL to "217ecf1a1322fe5020f9",
        0x2C0EL to "afe0d6cde01b3e2321ab",
        0x2C21L to "cd4934cd872ccd6f19cd",
        0x3449L to "cd64353e01e0d60e04cd",
        0x344CL to "3e01e0d60e04cd3c03c9",
        0x3546L to "f0e8a7200b3ee4e0473e",
        0x3583L to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e252139",
        Gen2ItemCalls.romOff(0x03, 0x7405) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x70C7) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5AFE) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50C0) to "cdc950e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75EC) to "1e0018081e0118041e0218003e0321914fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45C1) to "cdb2453e0fcd492ec921",
        Gen2ItemCalls.romOff(0x0B, 0x4672) to "3e0ecd492ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B0B) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A66) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F49) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EE0) to "3e24cd3a3b545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x7300) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437A) to "afead3cf11c443cd4e1a",
        Gen2ItemCalls.romOff(0x14, 0x4355) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x440F) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F68) to "0601cd4f72daf36ecd49",
        Gen2ItemCalls.romOff(0x03, 0x6E63) to "0605cd4f72da8c6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67BD) to "b369ce69ed690b6a246a",
        0x2E80L to "cd291ef09ff53e01d7cd",
        0x2ECBL to "cd291ef09ff53e01d7cd",
        0x2E95L to "f0daf53e01e0dacda32e",
        Gen2ItemCalls.romOff(0x04, 0x7785) to "3e03216054cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A98) to "0f133946" + "94fa7f",   // the HMs' moves
    ),
)

/** Gold (German): pret/pokegold's symbols, matched in the German cart. */
val GEN2_ITEM_CALLS_GOLD_DE = goldSilverItemCalls(
    tossItem = 0x3096, reanchorMap = 0x2EAD, healingHp = 0x73D0, statusHealing = 0x7092,
    moves = 0x5AF1, bikeFunction = 0x50C0, oldRodEffect = 0x75B7, useRegisteredItem = 0x7771,
    statBoosterParams = 0x6F14, updateStatsAfterItem = 0x6EB4, happinessChanges = 0x7337,
    tm = Gen2TmFns(openText = 0x2EF8, getMoveName = 0x3734, copyName1 = 0x31A8, closeText = 0x2EC2,
        getTmhmItemMove = 0x45C1, teach = 0x4672, baseData = Gen2ItemCalls.romOff(0x14, 0x5B00),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A5D), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C3B, closeSubmenu = 0x2C4E, clearBgPalettes = 0x3440,
        waitBgMap = 0x3443, setDefaultBgpAndObp = 0x353D, getSgbLayout = 0x357A, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x4370, initPartyMenuGfx = 0x434B,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x4405,
        rareCandyEffect = 0x6F33, evoStoneEffect = 0x6E37,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67B0), evosHead = "a669c169e069",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x3096L to "e5d5c5f09ff53e03d7cd",
        0x3734L to "e53e02ea61cefa51d1ea",
        0x31A8L to "217ecf1a1322fe5020f9",
        0x2C3BL to "afe0d6cd041c3e2321ad",
        0x2C4EL to "cd4034cdb42ccd9319cd",
        0x3440L to "cd5b353e01e0d60e04cd",
        0x3443L to "3e01e0d60e04cd3c03c9",
        0x353DL to "f0e8a7200b3ee4e0473e",
        0x357AL to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e252158",
        Gen2ItemCalls.romOff(0x03, 0x73D0) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x7092) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5AF1) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50C0) to "cdc950e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75B7) to "1e0018081e0118041e0218003e0321914fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45C1) to "cdb2453e0fcd762ec921",
        Gen2ItemCalls.romOff(0x0B, 0x4672) to "3e0ecd762ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B00) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A5D) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F14) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EB4) to "3e24cd313b545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x7337) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x4370) to "afead3cf11ba43cd721a",
        Gen2ItemCalls.romOff(0x14, 0x434B) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x4405) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F33) to "0601cd1a72dac76ecd14",
        Gen2ItemCalls.romOff(0x03, 0x6E37) to "0605cd1a72da606e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67B0) to "a669c169e069fe69176a",
        0x2EADL to "cd561ef09ff53e01d7cd",
        0x2EF8L to "cd561ef09ff53e01d7cd",
        0x2EC2L to "f0daf53e01e0dacdd02e",
        Gen2ItemCalls.romOff(0x04, 0x7771) to "3e03216054cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A8F) to "0f133946" + "94fa7f",   // the HMs' moves
    ),
)

/** Silver (English): pret/pokegold's symbols. */
val GEN2_ITEM_CALLS_SILVER = goldSilverItemCalls(
    tossItem = 0x3069, reanchorMap = 0x2E80, healingHp = 0x7403, statusHealing = 0x70C5,
    moves = 0x5AFE, bikeFunction = 0x50BE, oldRodEffect = 0x75EA, useRegisteredItem = 0x7785,
    statBoosterParams = 0x6F47, updateStatsAfterItem = 0x6EDE, happinessChanges = 0x72C6,
    tm = Gen2TmFns(openText = 0x2ECB, getMoveName = 0x373D, copyName1 = 0x317B, closeText = 0x2E95,
        getTmhmItemMove = 0x45C1, teach = 0x4672, baseData = Gen2ItemCalls.romOff(0x14, 0x5B0B),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A66), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C0E, closeSubmenu = 0x2C21, clearBgPalettes = 0x3449,
        waitBgMap = 0x344C, setDefaultBgpAndObp = 0x3546, getSgbLayout = 0x3583, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437A, initPartyMenuGfx = 0x4355,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x440F,
        rareCandyEffect = 0x6F66, evoStoneEffect = 0x6E61,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67BD), evosHead = "b369ce69ed69",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x3069L to "e5d5c5f09ff53e03d7cd",
        0x373DL to "e53e02ea61cefa51d1ea",
        0x317BL to "217ecf1a1322fe5020f9",
        0x2C0EL to "afe0d6cde01b3e2321ab",
        0x2C21L to "cd4934cd872ccd6f19cd",
        0x3449L to "cd64353e01e0d60e04cd",
        0x344CL to "3e01e0d60e04cd3c03c9",
        0x3546L to "f0e8a7200b3ee4e0473e",
        0x3583L to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e252139",
        Gen2ItemCalls.romOff(0x03, 0x7403) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x70C5) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5AFE) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50BE) to "cdc750e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75EA) to "1e0018081e0118041e0218003e03218f4fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45C1) to "cdb2453e0fcd492ec921",
        Gen2ItemCalls.romOff(0x0B, 0x4672) to "3e0ecd492ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B0B) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A66) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F47) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EDE) to "3e24cd3a3b545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x72C6) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437A) to "afead3cf11c443cd4e1a",
        Gen2ItemCalls.romOff(0x14, 0x4355) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x440F) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F66) to "0601cd4d72daf16ecd47",
        Gen2ItemCalls.romOff(0x03, 0x6E61) to "0605cd4d72da8a6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67BD) to "b369ce69ed690b6a246a",
        0x2E80L to "cd291ef09ff53e01d7cd",
        0x2ECBL to "cd291ef09ff53e01d7cd",
        0x2E95L to "f0daf53e01e0dacda32e",
        Gen2ItemCalls.romOff(0x04, 0x7785) to "3e03215e54cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A98) to "0f133946" + "94fa7f",   // the HMs' moves
    ),
)

/** Silver (German): pret/pokegold's symbols, matched in the German cart. */
val GEN2_ITEM_CALLS_SILVER_DE = goldSilverItemCalls(
    tossItem = 0x3096, reanchorMap = 0x2EAD, healingHp = 0x73CE, statusHealing = 0x7090,
    moves = 0x5AF1, bikeFunction = 0x50BE, oldRodEffect = 0x75B5, useRegisteredItem = 0x7771,
    statBoosterParams = 0x6F12, updateStatsAfterItem = 0x6EB2, happinessChanges = 0x72FE,
    tm = Gen2TmFns(openText = 0x2EF8, getMoveName = 0x3734, copyName1 = 0x31A8, closeText = 0x2EC2,
        getTmhmItemMove = 0x45C1, teach = 0x4672, baseData = Gen2ItemCalls.romOff(0x14, 0x5B00),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A5D), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C3B, closeSubmenu = 0x2C4E, clearBgPalettes = 0x3440,
        waitBgMap = 0x3443, setDefaultBgpAndObp = 0x353D, getSgbLayout = 0x357A, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x4370, initPartyMenuGfx = 0x434B,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x4405,
        rareCandyEffect = 0x6F31, evoStoneEffect = 0x6E35,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67B0), evosHead = "a669c169e069",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x3096L to "e5d5c5f09ff53e03d7cd",
        0x3734L to "e53e02ea61cefa51d1ea",
        0x31A8L to "217ecf1a1322fe5020f9",
        0x2C3BL to "afe0d6cd041c3e2321ad",
        0x2C4EL to "cd4034cdb42ccd9319cd",
        0x3440L to "cd5b353e01e0d60e04cd",
        0x3443L to "3e01e0d60e04cd3c03c9",
        0x353DL to "f0e8a7200b3ee4e0473e",
        0x357AL to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e252158",
        Gen2ItemCalls.romOff(0x03, 0x73CE) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x7090) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5AF1) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50BE) to "cdc750e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75B5) to "1e0018081e0118041e0218003e03218f4fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45C1) to "cdb2453e0fcd762ec921",
        Gen2ItemCalls.romOff(0x0B, 0x4672) to "3e0ecd762ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B00) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A5D) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F12) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EB2) to "3e24cd313b545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x72FE) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x4370) to "afead3cf11ba43cd721a",
        Gen2ItemCalls.romOff(0x14, 0x434B) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x4405) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F31) to "0601cd1872dac56ecd12",
        Gen2ItemCalls.romOff(0x03, 0x6E35) to "0605cd1872da5e6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67B0) to "a669c169e069fe69176a",
        0x2EADL to "cd561ef09ff53e01d7cd",
        0x2EF8L to "cd561ef09ff53e01d7cd",
        0x2EC2L to "f0daf53e01e0dacdd02e",
        Gen2ItemCalls.romOff(0x04, 0x7771) to "3e03215e54cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A8F) to "0f133946" + "94fa7f",   // the HMs' moves
    ),
)

/** Gold / Silver: Crystal's engine at their own addresses (every check the cart's own bytes), their RAM in every build. */
private fun goldSilverItemCalls(
    tossItem: Int, reanchorMap: Int, healingHp: Int, statusHealing: Int, moves: Int, bikeFunction: Int, oldRodEffect: Int,
    useRegisteredItem: Int, statBoosterParams: Int, updateStatsAfterItem: Int, happinessChanges: Int, tm: Gen2TmFns,
    party: Gen2PartyItemFns, signatures: List<Pair<Long, String>>,
) = Gen2ItemCalls(
    tossItem = tossItem,
    signatures = signatures,
    at = 0x2566B8,
    healingHp = Gen2ItemCalls.romOff(0x03, healingHp),
    statusHealing = Gen2ItemCalls.romOff(0x03, statusHealing),
    moves = Gen2ItemCalls.romOff(0x10, moves),
    curItem = wramx(1, 0xD002),
    curPartySpecies = wramx(1, 0xD004),
    curPartyMon = wramx(1, 0xD005),
    quantityChange = wramx(1, 0xD009),
    repelEffect = wramx(1, 0xD9EB),
    bikeFunction = bikeFunction,
    oldRodEffect = oldRodEffect,
    reanchorMap = reanchorMap,
    queuedScript = 0xCFD8L,
    fieldMoveSucceeded = 0xCFE4L,
    usingItemWithSelect = 0xCFEBL,
    useRegisteredItem = useRegisteredItem,
    tm = tm,
    statBoosterParams = statBoosterParams,
    updateStatsAfterItem = updateStatsAfterItem,
    happinessChanges = Gen2ItemCalls.romOff(0x01, happinessChanges),
    party = party,
    routine = 0xDF03,
)

/** French, Italian and Spanish Crystal / Gold / Silver: the German carts' functions, each found in the cart by its code. */
val GEN2_ITEM_CALLS_CRYSTAL_FR = Gen2ItemCalls(
    tossItem = 0x2F40,
    signatures = listOf(0x2F40L to "e5d5c5f09df53e03d7cddc51c1", 0x2D9BL to "cda01ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521097e", 0x7354, 0x7016, 0x5B06, 0x754A, 0x73E5, 0x34E5, 0x30C3, 0x5417, 0x5679,
        0x6E9A, 0x6E31, 0x723B, bike = 0x5082, bikeCode = "cd8b50e67feaecd0c9cdf050",
        oldRodCode = "1e0018081e0118041e0218003e03215d4fcf", useRegisteredCode = "3e03212254cffa42d1") + listOf(
            0x2DE9L to "cda01ff09df53e01d7cd", 0x2DB0L to "f0d8f53e01e0d8cdc32d",
            Gen2ItemCalls.romOff(0x0B, 0x47C4) to "cdb5473e0fcd642dc921", Gen2ItemCalls.romOff(0x0B, 0x4875) to "3e0ecd642dc5fa09d121",
        ),
    healingHp = Gen2ItemCalls.romOff(0x03, 0x7354),
    statusHealing = Gen2ItemCalls.romOff(0x03, 0x7016),
    moves = Gen2ItemCalls.romOff(0x10, 0x5B06),
    bikeFunction = 0x5082,
    reanchorMap = 0x2D9B,
    oldRodEffect = 0x754A,
    useRegisteredItem = 0x73E5,
    tm = Gen2TmFns(openText = 0x2DE9, getMoveName = 0x34E5, copyName1 = 0x30C3, closeText = 0x2DB0, getTmhmItemMove = 0x47C4,
        teach = 0x4875, baseData = Gen2ItemCalls.romOff(0x14, 0x5417), tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5679)),
    statBoosterParams = 0x6E9A,
    updateStatsAfterItem = 0x6E31,
    happinessChanges = Gen2ItemCalls.romOff(0x01, 0x723B),
    party = Gen2PartyItemFns(fadeToMenu = 0x2B0A, closeSubmenu = 0x2B1D, clearBgPalettes = 0x31E0, waitBgMap = 0x31E3,
        setDefaultBgpAndObp = 0x32E6, initPartyMenuWithCancel = 0x4405, initPartyMenuGfx = 0x43E0, placePartyMenuText = 0x449A,
        rareCandyEffect = 0x6EB9, evoStoneEffect = 0x6DB4, evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x65BC),
        evosHead = "b267cd67ec67"),
)

val GEN2_ITEM_CALLS_CRYSTAL_IT = Gen2ItemCalls(
    tossItem = 0x2F41,
    signatures = listOf(0x2F41L to "e5d5c5f09df53e03d7cd0e52c1", 0x2D9CL to "cda11ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521077e", 0x7385, 0x7047, 0x5AFA, 0x757B, 0x7438, 0x34E6, 0x30C4, 0x5433, 0x5679,
        0x6ECB, 0x6E5E, 0x723A, bike = 0x50B4, bikeCode = "cdbd50e67feaecd0c9cd2251",
        oldRodCode = "1e0018081e0118041e0218003e03218f4fcf", useRegisteredCode = "3e03215454cffa42d1") + listOf(
            0x2DEAL to "cda11ff09df53e01d7cd", 0x2DB1L to "f0d8f53e01e0d8cdc42d",
            Gen2ItemCalls.romOff(0x0B, 0x47CC) to "cdbd473e0fcd652dc921", Gen2ItemCalls.romOff(0x0B, 0x487D) to "3e0ecd652dc5fa09d121",
        ),
    healingHp = Gen2ItemCalls.romOff(0x03, 0x7385),
    statusHealing = Gen2ItemCalls.romOff(0x03, 0x7047),
    moves = Gen2ItemCalls.romOff(0x10, 0x5AFA),
    bikeFunction = 0x50B4,
    reanchorMap = 0x2D9C,
    oldRodEffect = 0x757B,
    useRegisteredItem = 0x7438,
    tm = Gen2TmFns(openText = 0x2DEA, getMoveName = 0x34E6, copyName1 = 0x30C4, closeText = 0x2DB1, getTmhmItemMove = 0x47CC,
        teach = 0x487D, baseData = Gen2ItemCalls.romOff(0x14, 0x5433), tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5679)),
    statBoosterParams = 0x6ECB,
    updateStatsAfterItem = 0x6E5E,
    happinessChanges = Gen2ItemCalls.romOff(0x01, 0x723A),
    party = Gen2PartyItemFns(fadeToMenu = 0x2B0B, closeSubmenu = 0x2B1E, clearBgPalettes = 0x31E1, waitBgMap = 0x31E4,
        setDefaultBgpAndObp = 0x32E7, initPartyMenuWithCancel = 0x4408, initPartyMenuGfx = 0x43E3, placePartyMenuText = 0x449D,
        rareCandyEffect = 0x6EEA, evoStoneEffect = 0x6DE1, evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x65B0),
        evosHead = "a667c167e067"),
)

val GEN2_ITEM_CALLS_CRYSTAL_ES = Gen2ItemCalls(
    tossItem = 0x2F3D,
    signatures = listOf(0x2F3DL to "e5d5c5f09df53e03d7cddb51c1", 0x2D98L to "cd9d1ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521017e", 0x7354, 0x7016, 0x5B0E, 0x754A, 0x745E, 0x34E2, 0x30C0, 0x542D, 0x568D,
        0x6E9A, 0x6E2E, 0x7240, bike = 0x5081, bikeCode = "cd8a50e67feaecd0c9cdef50",
        oldRodCode = "1e0018081e0118041e0218003e03215c4fcf", useRegisteredCode = "3e03212154cffa42d1") + listOf(
            0x2DE6L to "cd9d1ff09df53e01d7cd", 0x2DADL to "f0d8f53e01e0d8cdc02d",
            Gen2ItemCalls.romOff(0x0B, 0x47C0) to "cdb1473e0fcd612dc921", Gen2ItemCalls.romOff(0x0B, 0x4871) to "3e0ecd612dc5fa09d121",
        ),
    healingHp = Gen2ItemCalls.romOff(0x03, 0x7354),
    statusHealing = Gen2ItemCalls.romOff(0x03, 0x7016),
    moves = Gen2ItemCalls.romOff(0x10, 0x5B0E),
    bikeFunction = 0x5081,
    reanchorMap = 0x2D98,
    oldRodEffect = 0x754A,
    useRegisteredItem = 0x745E,
    tm = Gen2TmFns(openText = 0x2DE6, getMoveName = 0x34E2, copyName1 = 0x30C0, closeText = 0x2DAD, getTmhmItemMove = 0x47C0,
        teach = 0x4871, baseData = Gen2ItemCalls.romOff(0x14, 0x542D), tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x568D)),
    statBoosterParams = 0x6E9A,
    updateStatsAfterItem = 0x6E2E,
    happinessChanges = Gen2ItemCalls.romOff(0x01, 0x7240),
    party = Gen2PartyItemFns(fadeToMenu = 0x2B07, closeSubmenu = 0x2B1A, clearBgPalettes = 0x31DD, waitBgMap = 0x31E0,
        setDefaultBgpAndObp = 0x32E3, initPartyMenuWithCancel = 0x4407, initPartyMenuGfx = 0x43E2, placePartyMenuText = 0x449C,
        rareCandyEffect = 0x6EB9, evoStoneEffect = 0x6DB1, evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x65C4),
        evosHead = "ba67d567f467"),
)

val GEN2_ITEM_CALLS_GOLD_FR = goldSilverItemCalls(
    tossItem = 0x307B, reanchorMap = 0x2E92, healingHp = 0x73D8, statusHealing = 0x709A,
    moves = 0x5B09, bikeFunction = 0x50C0, oldRodEffect = 0x75BF, useRegisteredItem = 0x777F,
    statBoosterParams = 0x6F1C, updateStatsAfterItem = 0x6EB3, happinessChanges = 0x7335,
    tm = Gen2TmFns(openText = 0x2EDD, getMoveName = 0x36E8, copyName1 = 0x318D, closeText = 0x2EA7,
        getTmhmItemMove = 0x45CB, teach = 0x467C, baseData = Gen2ItemCalls.romOff(0x14, 0x5B10),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A65), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C20, closeSubmenu = 0x2C33, clearBgPalettes = 0x33F4,
        waitBgMap = 0x33F7, setDefaultBgpAndObp = 0x34F1, getSgbLayout = 0x352E, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437A, initPartyMenuGfx = 0x4355,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x440F,
        rareCandyEffect = 0x6F3B, evoStoneEffect = 0x6E36,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67C8), evosHead = "be69d969f869",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x307BL to "e5d5c5f09ff53e03d7cd",
        0x36E8L to "e53e02ea61cefa51d1ea",
        0x318DL to "217ecf1a1322fe5020f9",
        0x2C20L to "afe0d6cde91b3e2321ad",
        0x2C33L to "cdf433cd992ccd7819cd",
        0x33F4L to "cd0f353e01e0d60e04cd",
        0x33F7L to "3e01e0d60e04cd3c03c9",
        0x34F1L to "f0e8a7200b3ee4e0473e",
        0x352EL to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e2521587c",
        Gen2ItemCalls.romOff(0x03, 0x73D8) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x709A) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5B09) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50C0) to "cdc950e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75BF) to "1e0018081e0118041e0218003e0321914fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45CB) to "cdbc453e0fcd5b2ec921",
        Gen2ItemCalls.romOff(0x0B, 0x467C) to "3e0ecd5b2ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B10) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A65) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F1C) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EB3) to "3e24cdc93a545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x7335) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437A) to "afead3cf11c443cd571a",
        Gen2ItemCalls.romOff(0x14, 0x4355) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x440F) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F3B) to "0601cd2272dac66ecd1c",
        Gen2ItemCalls.romOff(0x03, 0x6E36) to "0605cd2272da5f6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67C8) to "be69d969f869",
        0x2E92L to "cd3b1ef09ff53e01d7cd",
        0x2EDDL to "cd3b1ef09ff53e01d7cd",
        0x2EA7L to "f0daf53e01e0dacdb52e",
        Gen2ItemCalls.romOff(0x04, 0x777F) to "3e03216054cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A97) to "0f13394694fa7f",   // the HMs' moves
    ),
)

val GEN2_ITEM_CALLS_SILVER_FR = goldSilverItemCalls(
    tossItem = 0x307B, reanchorMap = 0x2E92, healingHp = 0x73D6, statusHealing = 0x7098,
    moves = 0x5B09, bikeFunction = 0x50BE, oldRodEffect = 0x75BD, useRegisteredItem = 0x777F,
    statBoosterParams = 0x6F1A, updateStatsAfterItem = 0x6EB1, happinessChanges = 0x72FB,
    tm = Gen2TmFns(openText = 0x2EDD, getMoveName = 0x36E8, copyName1 = 0x318D, closeText = 0x2EA7,
        getTmhmItemMove = 0x45CB, teach = 0x467C, baseData = Gen2ItemCalls.romOff(0x14, 0x5B10),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A65), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C20, closeSubmenu = 0x2C33, clearBgPalettes = 0x33F4,
        waitBgMap = 0x33F7, setDefaultBgpAndObp = 0x34F1, getSgbLayout = 0x352E, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437A, initPartyMenuGfx = 0x4355,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x440F,
        rareCandyEffect = 0x6F39, evoStoneEffect = 0x6E34,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67C8), evosHead = "be69d969f869",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x307BL to "e5d5c5f09ff53e03d7cd",
        0x36E8L to "e53e02ea61cefa51d1ea",
        0x318DL to "217ecf1a1322fe5020f9",
        0x2C20L to "afe0d6cde91b3e2321ad",
        0x2C33L to "cdf433cd992ccd7819cd",
        0x33F4L to "cd0f353e01e0d60e04cd",
        0x33F7L to "3e01e0d60e04cd3c03c9",
        0x34F1L to "f0e8a7200b3ee4e0473e",
        0x352EL to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e2521587c",
        Gen2ItemCalls.romOff(0x03, 0x73D6) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x7098) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5B09) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50BE) to "cdc750e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75BD) to "1e0018081e0118041e0218003e03218f4fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45CB) to "cdbc453e0fcd5b2ec921",
        Gen2ItemCalls.romOff(0x0B, 0x467C) to "3e0ecd5b2ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B10) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A65) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F1A) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EB1) to "3e24cdc93a545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x72FB) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437A) to "afead3cf11c443cd571a",
        Gen2ItemCalls.romOff(0x14, 0x4355) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x440F) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F39) to "0601cd2072dac46ecd1a",
        Gen2ItemCalls.romOff(0x03, 0x6E34) to "0605cd2072da5d6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67C8) to "be69d969f869",
        0x2E92L to "cd3b1ef09ff53e01d7cd",
        0x2EDDL to "cd3b1ef09ff53e01d7cd",
        0x2EA7L to "f0daf53e01e0dacdb52e",
        Gen2ItemCalls.romOff(0x04, 0x777F) to "3e03215e54cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A97) to "0f13394694fa7f",   // the HMs' moves
    ),
)

val GEN2_ITEM_CALLS_GOLD_IT = goldSilverItemCalls(
    tossItem = 0x308E, reanchorMap = 0x2EA5, healingHp = 0x73D8, statusHealing = 0x709A,
    moves = 0x5AFD, bikeFunction = 0x50C1, oldRodEffect = 0x75BF, useRegisteredItem = 0x77D1,
    statBoosterParams = 0x6F1C, updateStatsAfterItem = 0x6EAF, happinessChanges = 0x7332,
    tm = Gen2TmFns(openText = 0x2EF0, getMoveName = 0x372C, copyName1 = 0x31A0, closeText = 0x2EBA,
        getTmhmItemMove = 0x45CB, teach = 0x467C, baseData = Gen2ItemCalls.romOff(0x14, 0x5B19),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A65), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C33, closeSubmenu = 0x2C46, clearBgPalettes = 0x3438,
        waitBgMap = 0x343B, setDefaultBgpAndObp = 0x3535, getSgbLayout = 0x3572, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437D, initPartyMenuGfx = 0x4358,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x4412,
        rareCandyEffect = 0x6F3B, evoStoneEffect = 0x6E32,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67BC), evosHead = "b269cd69ec69",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x308EL to "e5d5c5f09ff53e03d7cd",
        0x372CL to "e53e02ea61cefa51d1ea",
        0x31A0L to "217ecf1a1322fe5020f9",
        0x2C33L to "afe0d6cdfe1b3e2321a9",
        0x2C46L to "cd3834cdac2ccd8d19cd",
        0x3438L to "cd53353e01e0d60e04cd",
        0x343BL to "3e01e0d60e04cd3c03c9",
        0x3535L to "f0e8a7200b3ee4e0473e",
        0x3572L to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e2521567c",
        Gen2ItemCalls.romOff(0x03, 0x73D8) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x709A) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5AFD) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50C1) to "cdca50e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75BF) to "1e0018081e0118041e0218003e0321924fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45CB) to "cdbc453e0fcd6e2ec921",
        Gen2ItemCalls.romOff(0x0B, 0x467C) to "3e0ecd6e2ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B19) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A65) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F1C) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EAF) to "3e24cd293b545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x7332) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437D) to "afead3cf11c743cd6c1a",
        Gen2ItemCalls.romOff(0x14, 0x4358) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x4412) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F3B) to "0601cd2272dac26ecd1c",
        Gen2ItemCalls.romOff(0x03, 0x6E32) to "0605cd2272da5b6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67BC) to "b269cd69ec69",
        0x2EA5L to "cd4e1ef09ff53e01d7cd",
        0x2EF0L to "cd4e1ef09ff53e01d7cd",
        0x2EBAL to "f0daf53e01e0dacdc82e",
        Gen2ItemCalls.romOff(0x04, 0x77D1) to "3e03216154cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A97) to "0f13394694fa7f",   // the HMs' moves
    ),
)

val GEN2_ITEM_CALLS_SILVER_IT = goldSilverItemCalls(
    tossItem = 0x308E, reanchorMap = 0x2EA5, healingHp = 0x73D4, statusHealing = 0x7096,
    moves = 0x5AFD, bikeFunction = 0x50BD, oldRodEffect = 0x75BB, useRegisteredItem = 0x77D1,
    statBoosterParams = 0x6F18, updateStatsAfterItem = 0x6EAB, happinessChanges = 0x72F8,
    tm = Gen2TmFns(openText = 0x2EF0, getMoveName = 0x372C, copyName1 = 0x31A0, closeText = 0x2EBA,
        getTmhmItemMove = 0x45CB, teach = 0x467C, baseData = Gen2ItemCalls.romOff(0x14, 0x5B19),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A65), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C33, closeSubmenu = 0x2C46, clearBgPalettes = 0x3438,
        waitBgMap = 0x343B, setDefaultBgpAndObp = 0x3535, getSgbLayout = 0x3572, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437D, initPartyMenuGfx = 0x4358,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x4412,
        rareCandyEffect = 0x6F37, evoStoneEffect = 0x6E2E,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67BC), evosHead = "b269cd69ec69",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x308EL to "e5d5c5f09ff53e03d7cd",
        0x372CL to "e53e02ea61cefa51d1ea",
        0x31A0L to "217ecf1a1322fe5020f9",
        0x2C33L to "afe0d6cdfe1b3e2321a9",
        0x2C46L to "cd3834cdac2ccd8d19cd",
        0x3438L to "cd53353e01e0d60e04cd",
        0x343BL to "3e01e0d60e04cd3c03c9",
        0x3535L to "f0e8a7200b3ee4e0473e",
        0x3572L to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e2521567c",
        Gen2ItemCalls.romOff(0x03, 0x73D4) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x7096) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5AFD) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50BD) to "cdc650e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75BB) to "1e0018081e0118041e0218003e03218e4fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45CB) to "cdbc453e0fcd6e2ec921",
        Gen2ItemCalls.romOff(0x0B, 0x467C) to "3e0ecd6e2ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B19) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A65) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F18) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EAB) to "3e24cd293b545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x72F8) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437D) to "afead3cf11c743cd6c1a",
        Gen2ItemCalls.romOff(0x14, 0x4358) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x4412) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F37) to "0601cd1e72dabe6ecd18",
        Gen2ItemCalls.romOff(0x03, 0x6E2E) to "0605cd1e72da576e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67BC) to "b269cd69ec69",
        0x2EA5L to "cd4e1ef09ff53e01d7cd",
        0x2EF0L to "cd4e1ef09ff53e01d7cd",
        0x2EBAL to "f0daf53e01e0dacdc82e",
        Gen2ItemCalls.romOff(0x04, 0x77D1) to "3e03215d54cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5A97) to "0f13394694fa7f",   // the HMs' moves
    ),
)

val GEN2_ITEM_CALLS_GOLD_ES = goldSilverItemCalls(
    tossItem = 0x308D, reanchorMap = 0x2EA4, healingHp = 0x73D8, statusHealing = 0x709A,
    moves = 0x5B0F, bikeFunction = 0x50BF, oldRodEffect = 0x75BF, useRegisteredItem = 0x77F0,
    statBoosterParams = 0x6F1C, updateStatsAfterItem = 0x6EB0, happinessChanges = 0x7348,
    tm = Gen2TmFns(openText = 0x2EEF, getMoveName = 0x36FA, copyName1 = 0x319F, closeText = 0x2EB9,
        getTmhmItemMove = 0x45C1, teach = 0x4672, baseData = Gen2ItemCalls.romOff(0x14, 0x5B19),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A79), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C32, closeSubmenu = 0x2C45, clearBgPalettes = 0x3406,
        waitBgMap = 0x3409, setDefaultBgpAndObp = 0x3503, getSgbLayout = 0x3540, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437C, initPartyMenuGfx = 0x4357,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x4411,
        rareCandyEffect = 0x6F3B, evoStoneEffect = 0x6E33,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67CE), evosHead = "c469df69fe69",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x308DL to "e5d5c5f09ff53e03d7cd",
        0x36FAL to "e53e02ea61cefa51d1ea",
        0x319FL to "217ecf1a1322fe5020f9",
        0x2C32L to "afe0d6cdfd1b3e2321a9",
        0x2C45L to "cd0634cdab2ccd8c19cd",
        0x3406L to "cd21353e01e0d60e04cd",
        0x3409L to "3e01e0d60e04cd3c03c9",
        0x3503L to "f0e8a7200b3ee4e0473e",
        0x3540L to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e2521507c",
        Gen2ItemCalls.romOff(0x03, 0x73D8) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x709A) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5B0F) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50BF) to "cdc850e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75BF) to "1e0018081e0118041e0218003e0321904fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45C1) to "cdb2453e0fcd6d2ec921",
        Gen2ItemCalls.romOff(0x0B, 0x4672) to "3e0ecd6d2ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B19) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A79) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F1C) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EB0) to "3e24cddb3a545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x7348) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437C) to "afead3cf11c643cd6b1a",
        Gen2ItemCalls.romOff(0x14, 0x4357) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x4411) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F3B) to "0601cd2272dac36ecd1c",
        Gen2ItemCalls.romOff(0x03, 0x6E33) to "0605cd2272da5c6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67CE) to "c469df69fe69",
        0x2EA4L to "cd4d1ef09ff53e01d7cd",
        0x2EEFL to "cd4d1ef09ff53e01d7cd",
        0x2EB9L to "f0daf53e01e0dacdc72e",
        Gen2ItemCalls.romOff(0x04, 0x77F0) to "3e03215f54cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5AAB) to "0f13394694fa7f",   // the HMs' moves
    ),
)

val GEN2_ITEM_CALLS_SILVER_ES = goldSilverItemCalls(
    tossItem = 0x308D, reanchorMap = 0x2EA4, healingHp = 0x73D6, statusHealing = 0x7098,
    moves = 0x5B0F, bikeFunction = 0x50BD, oldRodEffect = 0x75BD, useRegisteredItem = 0x77F0,
    statBoosterParams = 0x6F1A, updateStatsAfterItem = 0x6EAE, happinessChanges = 0x7310,
    tm = Gen2TmFns(openText = 0x2EEF, getMoveName = 0x36FA, copyName1 = 0x319F, closeText = 0x2EB9,
        getTmhmItemMove = 0x45C1, teach = 0x4672, baseData = Gen2ItemCalls.romOff(0x14, 0x5B19),
        tmHmMoves = Gen2ItemCalls.romOff(0x04, 0x5A79), tempTmhm = 0xD151, putativeTmhmMove = 0xD14D, options = 0xD199),
    party = Gen2PartyItemFns(fadeToMenu = 0x2C32, closeSubmenu = 0x2C45, clearBgPalettes = 0x3406,
        waitBgMap = 0x3409, setDefaultBgpAndObp = 0x3503, getSgbLayout = 0x3540, delayFrame = 0x032E,
        loadPartyMenuGfx = 0x404F, initPartyMenuWithCancel = 0x437C, initPartyMenuGfx = 0x4357,
        writePartyMenuTilemap = 0x405F, placePartyMenuText = 0x4411,
        rareCandyEffect = 0x6F39, evoStoneEffect = 0x6E31,
        evosAttacksPointers = Gen2ItemCalls.romOff(0x10, 0x67CE), evosHead = "c469df69fe69",
        partyMenuActionText = wramx(1, 0xD03E), partyMenuCursor = 0xCFC9L, code = emptyList()),
    signatures = listOf(
        0x308DL to "e5d5c5f09ff53e03d7cd",
        0x36FAL to "e53e02ea61cefa51d1ea",
        0x319FL to "217ecf1a1322fe5020f9",
        0x2C32L to "afe0d6cdfd1b3e2321a9",
        0x2C45L to "cd0634cdab2ccd8c19cd",
        0x3406L to "cd21353e01e0d60e04cd",
        0x3409L to "3e01e0d60e04cd3c03c9",
        0x3503L to "f0e8a7200b3ee4e0473e",
        0x3540L to "f0e8a72004f0e9a7c83e",
        0x032EL to "3e01eaeace7600faeace",
        Gen2ItemCalls.romOff(0x25, 0x66B8) to "cdf566cd06673e2521507c",
        Gen2ItemCalls.romOff(0x03, 0x73D6) to "2e32002f3c0030500010",
        Gen2ItemCalls.romOff(0x03, 0x7098) to "09f0080af1100bf2200c",
        Gen2ItemCalls.romOff(0x10, 0x5B0F) to "01002800ff2300020032",
        Gen2ItemCalls.romOff(0x03, 0x50BD) to "cdc650e67feae4cfc9cd",
        Gen2ItemCalls.romOff(0x03, 0x75BD) to "1e0018081e0118041e0218003e03218e4fcf",
        Gen2ItemCalls.romOff(0x0B, 0x45C1) to "cdb2453e0fcd6d2ec921",
        Gen2ItemCalls.romOff(0x0B, 0x4672) to "3e0ecd6d2ec5fa05d021",
        Gen2ItemCalls.romOff(0x14, 0x5B19) to "012d31312d414116032d",
        Gen2ItemCalls.romOff(0x04, 0x5A79) to "df1daecd2e5cc0f9f4ed",
        Gen2ItemCalls.romOff(0x03, 0x6F1A) to "fa04d0ea60ceea51d13e",
        Gen2ItemCalls.romOff(0x03, 0x6EAE) to "3e24cddb3a545d3e0acd",
        Gen2ItemCalls.romOff(0x01, 0x7310) to "05030205030201010003",
        Gen2ItemCalls.romOff(0x14, 0x404F) to "cda50d21bb503e02cf21",
        Gen2ItemCalls.romOff(0x14, 0x437C) to "afead3cf11c643cd6b1a",
        Gen2ItemCalls.romOff(0x14, 0x4357) to "2122da2aa7c84fafe0b2",
        Gen2ItemCalls.romOff(0x14, 0x405F) to "2199d17ef5cbe6afe0d6",
        Gen2ItemCalls.romOff(0x14, 0x4411) to "21b8c4011202cdef0efa",
        Gen2ItemCalls.romOff(0x03, 0x6F39) to "0601cd2072dac16ecd1a",
        Gen2ItemCalls.romOff(0x03, 0x6E31) to "0605cd2072da5a6e3e01",
        Gen2ItemCalls.romOff(0x10, 0x67CE) to "c469df69fe69",
        0x2EA4L to "cd4d1ef09ff53e01d7cd",
        0x2EEFL to "cd4d1ef09ff53e01d7cd",
        0x2EB9L to "f0daf53e01e0dacdc72e",
        Gen2ItemCalls.romOff(0x04, 0x77F0) to "3e03215d54cffa3fd021",
        Gen2ItemCalls.romOff(0x04, 0x5AAB) to "0f13394694fa7f",   // the HMs' moves
    ),
)
