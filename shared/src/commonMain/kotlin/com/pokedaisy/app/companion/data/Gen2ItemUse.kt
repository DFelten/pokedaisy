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
 * - The BICYCLE and the rods: their own field effects ([onTheSpot]).
 *
 * Everything else the game's bag can USE goes the game's own way ([openInGame], [fieldEffect]), as SELECT runs a registered
 * item (engine/overworld/select_menu.asm's UseRegisteredItem, far-called from HandleMap with wCurItem set): the vitamins,
 * PP UP, RARE CANDY, the stones and the bitter herbs (ITEMMENU_PARTY: ReanchorMap, FadeToMenu, the item's own party screen,
 * CloseSubmenu, CloseText), the COIN CASE and BLUE CARD (ITEMMENU_CURRENT: their text over the map), SACRED ASH, ESCAPE
 * ROPE, ITEMFINDER, SQUIRTBOTTLE and the two keys (ITEMMENU_CLOSE: the script their effect queues runs from the map's queue,
 * or the game's "not the time" text). TMs and HMs aren't SELECT items: [Gen2TmFns.routine] is what the bag's TM USE does
 * (AskTeachTMHM's move name, ChooseMonToLearnTMHM, TeachTMHM) inside SELECT's ITEMMENU_PARTY frame, run from WRAM0.
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
) {
    fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = signatures.all { (off, hex) ->
        val want = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        rom(off, want.size)?.contentEquals(want) == true
    }

    /** The items USE takes here. */
    fun usable(itemId: Int) = itemId in HP_ITEMS || itemId in STATUS_ITEMS || itemId == FULL_RESTORE ||
        itemId in REVIVES || itemId in PP_ONE_MOVE || itemId in PP_ALL_MOVES || itemId in REPELS || onTheSpot(itemId) ||
        inGame(itemId) || fieldEffect(itemId)

    /** TMs / HMs and the items whose own screen or text the game shows ([openInGame]): the companion hands over to it. */
    fun inGame(itemId: Int) = itemId in TM_ITEMS || itemId in GAME_PARTY || itemId in GAME_TEXT

    /** The items whose effect queues a script ([fieldEffect]). */
    fun fieldEffect(itemId: Int) = itemId in GAME_FIELD

    fun picksMon(itemId: Int) = usable(itemId) && itemId !in REPELS && !onTheSpot(itemId) && !inGame(itemId) && !fieldEffect(itemId)

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

    /**
     * USE of a TM / HM or an item with its own screen or text ([inGame]): the game's own flow started from the overworld loop
     * (a TM's routine written to [ROUTINE] first), wCurItem set once the call is set up. The rest is the game's - the
     * party pick, the item spent - so this only says it opened: IN_GAME for a screen, USED for a text.
     */
    fun openInGame(g: GameCalls, cfg: Gen2Config, itemId: Int): ItemUseOutcome {
        val tm = TM_ITEMS.indexOf(itemId)
        val left = when {
            tm >= 0 -> u8(g.read(cfg.tmsHms + tm, 1), 0)
            itemId in GAME_TEXT -> 1
            else -> quantity(g, cfg, itemId)
        }
        if (left == 0) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
        val set = if (tm >= 0) g.gbCallAt(at, ROUTINE, 0, 0, 0) else g.gbCallAt(at, FARCALL, USE_ITEM_BANK, 0, useRegisteredItem)
        if (!set) return ItemUseOutcome(ItemUseResult.FAILED)
        if (tm >= 0) g.write(ROUTINE.toLong(), this.tm.routine())
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
        /** UseRegisteredItem's bank. */
        private const val USE_ITEM_BANK = 0x04
        /** Where the TM routine runs: the stack's far end (wStackBottom, WRAM0), which the TM screens never reach (the
         * routine's last bytes run after them, headless). */
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
        /** TM01-TM50, HM01-HM07 in wTMsHMs' order (ITEM_C3 and ITEM_DC sit between them). */
        val TM_ITEMS: List<Int> = (0xBF..0xF9).filter { it != 0xC3 && it != 0xDC }
        /** ITEMMENU_PARTY items with their own screen: the stones (MOON, FIRE, THUNDER, WATER, LEAF, SUN), HP UP, PROTEIN,
         * IRON, CARBOS, CALCIUM, RARE CANDY, PP UP, ENERGYPOWDER, ENERGY ROOT, HEAL POWDER, REVIVAL HERB. */
        val GAME_PARTY = setOf(0x08, 0x16, 0x17, 0x18, 0x22, 0xA9, 0x1A, 0x1B, 0x1C, 0x1D, 0x1F, 0x20, 0x3E, 0x79, 0x7A, 0x7B, 0x7C)
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
 * The bag's TM USE (engine/items/pack.asm's TMHMSubmenu .UseItem: AskTeachTMHM's move name, ChooseMonToLearnTMHM,
 * TeachTMHM) in SELECT's ITEMMENU_PARTY frame (ReanchorMap, FadeToMenu, ..., CloseSubmenu, CloseText), with the party menu
 * coming back after a Pokémon that can't learn it, as the Move Tutor's does. Addresses in bank 0, the TM screens' in
 * bank 0x0B; wCurItem names the TM (TeachTMHM spends a TM, never an HM).
 */
data class Gen2TmFns(
    val reanchorMap: Int = 0x2DBA,
    val fadeToMenu: Int = 0x2B29,
    val clearBgPalettes: Int = 0x31F3,
    val clearScreen: Int = 0x0FDB,
    val delayFrame: Int = 0x045A,
    val getSgbLayout: Int = 0x3340,
    val getMoveName: Int = 0x34F8,
    val copyName1: Int = 0x30D6,
    val closeSubmenu: Int = 0x2B3C,
    val closeText: Int = 0x2DCF,
    val getTmhmItemMove: Int = 0x47B6,
    val chooseMon: Int = 0x47FB,
    val chooseMonNoRefresh: Int = 0x480A,
    val teach: Int = 0x4867,
) {
    /** The routine (SM83, 82 bytes), its far calls through FarCall_hl (rst $08). */
    fun routine(): ByteArray {
        val c = ArrayList<Int>()
        val fix = ArrayList<Pair<Int, String>>()
        val at = HashMap<String, Int>()
        fun le(x: Int) = listOf(x and 0xFF, x shr 8 and 0xFF)
        fun call(x: Int) { c += 0xCD; c += le(x) }
        fun far(x: Int) { c += listOf(0x3E, TM_BANK, 0x21) + le(x) + 0xCF }
        fun jr(op: Int, label: String) { c += listOf(op, 0); fix += c.size - 1 to label }
        call(reanchorMap); call(fadeToMenu); call(clearBgPalettes); call(clearScreen); call(delayFrame)
        c += listOf(0x06, SCGB_PACKPALS); call(getSgbLayout)
        far(getTmhmItemMove)
        c += listOf(0xFA) + le(TEMP_TMHM) + 0xEA + le(PUTATIVE_TMHM_MOVE)   // ld a, [wTempTMHM]; ld [wPutativeTMHMMove], a
        call(getMoveName); call(copyName1)
        c += listOf(0x21) + le(OPTIONS) + listOf(0x7E, 0xF5, 0xCB, 0xA6)     // ld hl, wOptions; ld a, [hl]; push af; res NO_TEXT_SCROLL, [hl]
        far(chooseMon); jr(0x38, "cancel"); jr(0x18, "teach")
        at["loop"] = c.size; far(chooseMonNoRefresh); jr(0x38, "cancel")
        at["teach"] = c.size; far(teach); jr(0x30, "loop")                   // carry: learned
        at["cancel"] = c.size; c += listOf(0xF1, 0xEA) + le(OPTIONS)          // pop af; ld [wOptions], a
        call(closeSubmenu); call(closeText); c += 0xC9
        for ((p, label) in fix) c[p] = (at.getValue(label) - (p + 1)) and 0xFF
        return ByteArray(c.size) { c[it].toByte() }
    }

    private companion object {
        const val TM_BANK = 0x0B
        const val SCGB_PACKPALS = 0x14
        const val TEMP_TMHM = 0xD265
        const val PUTATIVE_TMHM_MOVE = 0xD262
        const val OPTIONS = 0xCFCC
    }
}

/** The tables' first bytes and HandleMap, shared by every Crystal build: HealingHPAmounts (FRESH WATER 50), StatusHealingActions
 * (ANTIDOTE), Moves (POUND). */
private fun crystalItemSignatures(handleMap: String, healingHp: Int, statusHealing: Int, moves: Int, oldRod: Int,
                                  useRegisteredItem: Int, chooseMon: Int, getSgbLayout: Int, getMoveName: Int, copyName1: Int) = listOf(
    0x25 * 0x4000L + (0x6773 - 0x4000) to handleMap,
    Gen2ItemCalls.romOff(0x03, healingHp) to "2e32002f3c0030500010c800",
    Gen2ItemCalls.romOff(0x03, statusHealing) to "09f0080af1100bf2200cf307",
    Gen2ItemCalls.romOff(0x10, moves) to "01002800ff2300",
    Gen2ItemCalls.romOff(0x03, 0x50B3) to "cdbc50e67feaecd0c9cd2151",   // BikeFunction
    Gen2ItemCalls.romOff(0x03, oldRod) to "1e0018081e0118041e0218003e03218e4fcf",   // the rods' effects, farcall FishFunction
    0x045AL to "3e01eab3cf7600fab3cf",   // DelayFrame; GetSGBLayout, GetMoveName, CopyName1 by build below
    Gen2ItemCalls.romOff(0x04, useRegisteredItem) to "3e03215354cffa42d1",   // farcall CheckItemMenu, ld a, [wItemAttributeValue]
    Gen2ItemCalls.romOff(0x0B, chooseMon) to "2186d01166d0010c00cd",
    getSgbLayout.toLong() to "f0e6a72004f0e7a7c83e",
    getMoveName.toLong() to "e53e02ea61cffa65d2ea",
    copyName1.toLong() to "2186d01a1322fe5020f9",
)

/** Crystal (rev 0, rev 1, Australia): pret/pokecrystal's symbols. */
val GEN2_ITEM_CALLS_CRYSTAL = Gen2ItemCalls(
    tossItem = 0x2F53,
    signatures = listOf(0x2F53L to "e5d5c5f09df53e03d7cd0d52c1", 0x2DBAL to "cdbf1ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521087e", 0x73AF, 0x7071, 0x5AFB, 0x75A5, 0x73C3, 0x47FB, 0x3340, 0x34F8, 0x30D6) + listOf(
            0x2B29L to "afe0d4cd6e1d3e232184", 0x31F3L to "cd17333e01e0d40e04cd", 0x0FDBL to "3e0721d9cd016801cd41",
            0x2B3CL to "cdf331cdae2bcdd21acd", 0x2DCFL to "f0d8f53e01e0d8cde22d",
            Gen2ItemCalls.romOff(0x0B, 0x47B6) to "cda7473e0fcd832dc921", Gen2ItemCalls.romOff(0x0B, 0x480A) to "3e14214f40cf3e142105",
            Gen2ItemCalls.romOff(0x0B, 0x4867) to "3e0ecd832dc5fa09d121",
        ),
)

/** German Crystal (Kristall): English's RAM and _TossItem; TossItem, the tables and Moves a little earlier. */
val GEN2_ITEM_CALLS_CRYSTAL_DE = Gen2ItemCalls(
    tossItem = 0x2F3D,
    signatures = listOf(0x2F3DL to "e5d5c5f09df53e03d7cd0d52c1", 0x2DA4L to "cda91ff09df53e01d7") +
        crystalItemSignatures("cdb067cdc1673e2521097e", 0x737E, 0x7040, 0x5AEB, 0x7574, 0x73C8, 0x47FE, 0x332A, 0x34E2, 0x30C0) + listOf(
            0x2B13L to "afe0d4cd571d3e232184", 0x31DDL to "cd01333e01e0d40e04cd", 0x0FDBL to "3e0721d9cd016801cd2b",
            0x2B26L to "cddd31cd982bcdbb1acd", 0x2DB9L to "f0d8f53e01e0d8cdcc2d",
            Gen2ItemCalls.romOff(0x0B, 0x47B9) to "cdaa473e0fcd6d2dc921", Gen2ItemCalls.romOff(0x0B, 0x480D) to "3e14214f40cf3e1421fb",
            Gen2ItemCalls.romOff(0x0B, 0x486A) to "3e0ecd6d2dc5fa09d121",
        ),
    healingHp = Gen2ItemCalls.romOff(0x03, 0x737E),
    statusHealing = Gen2ItemCalls.romOff(0x03, 0x7040),
    moves = Gen2ItemCalls.romOff(0x10, 0x5AEB),
    reanchorMap = 0x2DA4,
    oldRodEffect = 0x7574,
    useRegisteredItem = 0x73C8,
    tm = Gen2TmFns(reanchorMap = 0x2DA4, fadeToMenu = 0x2B13, clearBgPalettes = 0x31DD, getSgbLayout = 0x332A, getMoveName = 0x34E2,
        copyName1 = 0x30C0, closeSubmenu = 0x2B26, closeText = 0x2DB9, getTmhmItemMove = 0x47B9, chooseMon = 0x47FE,
        chooseMonNoRefresh = 0x480D, teach = 0x486A),
)
