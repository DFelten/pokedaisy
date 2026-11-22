package com.pokedaisy.app.companion.data

/**
 * The bag on a party Pokémon in the DS games: USE of medicine, PP items and the berries a party menu takes, GIVE and
 * TAKE, in one job each (nds/pk_nds_call.cpp's PK_NDS_ACTION_ITEM). Platinum and HeartGold / SoulSilver run the game's
 * own party-menu functions (nds/stub/pk_nds_item.c): the game decides what an item does. Black, White and their sequels
 * keep their item effects in an overlay the field doesn't have loaded, so there USE applies the effect [effect] gives
 * the item - HP, a revive, status, PP - through the game's own get / set of a Pokémon and its own bag calls
 * (nds/stub/pk_nds_item5.c); GIVE and TAKE are the game's calls alone.
 *
 * Only the carts each job was proved on headless ([CARTS]): Gen 4 on new games given Pokémon and items by their own
 * script commands, Gen 5 on the player's own Black and Black 2 saves (White on Black's, White 2 on Black 2's), every
 * case run, then saved in game and rebooted from the save. Item ids are the same in all of them.
 */
object DsBag {
    /** Game code and revision of the carts the jobs were proved on. */
    val CARTS = setOf("CPUE" to 1, "IPKE" to 0, "IPGE" to 0, "IRBO" to 0, "IRAO" to 0, "IREO" to 0, "IRDO" to 0)

    /** Gen 5 (Black, White and their sequels): USE by [effect], GIVE / TAKE keep the form items out. */
    fun gen5(code: String): Boolean = code.startsWith("IR")

    /** What Gen 5's USE does with an item (pk_nds_call.h's PkNdsItem5Effect). */
    data class Effect(
        val heal: Int = 0, val healDiv: Int = 0, val revive: Int = 0, val statusMask: Int = 0, val pp: Int = 0, val ppAll: Boolean = false,
    ) {
        fun words() = intArrayOf(heal, healDiv, revive, statusMask, pp, if (ppAll) 1 else 0)
    }

    /** "All the way": HP or PP to the maximum. */
    const val FULL = 0xFFFF
    // status bits: sleep 0-2, poison 3, burn 4, freeze 5, paralysis 6, bad poison 7
    private const val SLEEP = 0x07
    private const val POISON = 0x88
    private const val BURN = 0x10
    private const val FREEZE = 0x20
    private const val PARALYSIS = 0x40
    private const val ANY_STATUS = 0xFF

    /** Gen 5's USE items and what each does, as Black / White's item data has them. Left out: the herbs (they cost
     * friendship too), Sacred Ash, vitamins, PP Up / Max, Rare Candy and the berries that lower EVs - the game's bag. */
    private val GEN5_EFFECTS: Map<Int, Effect> = mapOf(
        17 to Effect(heal = 20), 18 to Effect(statusMask = POISON), 19 to Effect(statusMask = BURN),
        20 to Effect(statusMask = FREEZE), 21 to Effect(statusMask = SLEEP), 22 to Effect(statusMask = PARALYSIS),
        23 to Effect(heal = FULL, statusMask = ANY_STATUS), 24 to Effect(heal = FULL), 25 to Effect(heal = 200),
        26 to Effect(heal = 50), 27 to Effect(statusMask = ANY_STATUS), 28 to Effect(revive = 2), 29 to Effect(revive = 1),
        30 to Effect(heal = 50), 31 to Effect(heal = 60), 32 to Effect(heal = 80), 33 to Effect(heal = 100),
        38 to Effect(pp = 10), 39 to Effect(pp = FULL), 40 to Effect(pp = 10, ppAll = true), 41 to Effect(pp = FULL, ppAll = true),
        42 to Effect(statusMask = ANY_STATUS), 43 to Effect(heal = 20), 54 to Effect(statusMask = ANY_STATUS),
        149 to Effect(statusMask = PARALYSIS), 150 to Effect(statusMask = SLEEP), 151 to Effect(statusMask = POISON),
        152 to Effect(statusMask = BURN), 153 to Effect(statusMask = FREEZE), 154 to Effect(pp = 10), 155 to Effect(heal = 10),
        157 to Effect(statusMask = ANY_STATUS), 158 to Effect(heal = 1, healDiv = 4),
        494 to Effect(heal = 20), 591 to Effect(statusMask = ANY_STATUS),
    )

    /** Gen 5's effect for [itemId]; null = no USE there. */
    fun effect(itemId: Int): Effect? = GEN5_EFFECTS[itemId]

    /** The Drives and the Plates: Gen 5's GIVE / TAKE leave them (and the Griseous Orb) to the game's menus. */
    private val GEN5_FORM_ITEMS: Set<Int> = (116..119).toSet() + (298..313)

    // pk_nds_call_abi.h's PK_NDS_ITEM_* (the helper's modes and answers)
    const val CHECK = 0
    const val USE = 1
    const val GIVE = 2
    const val TAKE = 3
    const val EACH_MOVE = 0xFF
    private const val DONE = 0
    private const val NO_EFFECT = 1
    private const val NONE_LEFT = 2
    private const val CHANGED = 3
    private const val HOLDS_IT = 4
    private const val REFUSED = 5
    private const val BAG_FULL = 6
    private const val NOTHING = 7

    /** Where the helper's answer is among the job's results (pk_nds_call.h's PK_NDS_ITEM_RESULT); the old held item next. */
    const val RESULT = 9

    /** USE: what the party menu applies on the field - HP, status, PP, the vitamins and the berries that do the same
     * (Potion .. Old Gateau, Cheri .. Sitrus, Pomeg .. Tamato) - without Sacred Ash (the whole party), Rare Candy (a
     * level-up screen, moves and evolution) or the evolution stones, which the game's own screens play out. */
    private val USABLE: Set<Int> = (17..43).toSet() + (45..49) + (51..54) + (149..158) + (169..174)

    /** Ether, Max Ether, PP Up, PP Max, Leppa Berry: one move, picked after the Pokémon. */
    private val ONE_MOVE = setOf(38, 39, 51, 53, 154)

    /** Mail (written on the game's own screen) and the Griseous Orb (the game changes Giratina's form with it, but not
     * in the Distortion World): left to the game's menus, both ways. */
    private val KEEP_OUT: Set<Int> = (137..148).toSet() + 112

    fun proved(code: String, revision: Int?): Boolean = (code to revision) in CARTS

    fun canUse(itemId: Int, gen5: Boolean = false): Boolean = if (gen5) itemId in GEN5_EFFECTS else itemId in USABLE

    fun picksMove(itemId: Int, gen5: Boolean = false): Boolean =
        if (gen5) GEN5_EFFECTS[itemId]?.let { it.pp != 0 && !it.ppAll } == true else itemId in ONE_MOVE

    /** GIVE: what a Pokémon can hold - no key items, TMs / HMs or Mail ([pocket] the item's, from the game's tables). */
    fun canGive(itemId: Int, pocket: Int?, gen5: Boolean = false): Boolean =
        itemId > 0 && itemId !in KEEP_OUT && !(gen5 && itemId in GEN5_FORM_ITEMS) && pocket != null &&
            pocket != POCKET_KEY_ITEMS && pocket != POCKET_TM_HM && pocket != POCKET_MAIL

    /** The CHECK job's argument for [itemId]: every move of every Pokémon for a one-move item. */
    fun checkArgs(itemId: Int, partyCount: Int, gen5: Boolean = false): IntArray =
        intArrayOf(CHECK or ((if (picksMove(itemId, gen5)) EACH_MOVE else 0) shl 16), itemId, 0, partyCount)

    /** USE / GIVE / TAKE's arguments. */
    fun args(mode: Int, itemId: Int, slot: Int, move: Int, personality: Long, partyCount: Int): IntArray =
        intArrayOf(mode or (slot shl 8) or (move.coerceIn(0, 3) shl 16), itemId, personality.toInt(), partyCount)

    /**
     * The party pick from CHECK's [mask] (bit slot * 4 + move) and the party as the companion read it: eggs, Mail and
     * the item already held as the GBA's pick says them.
     */
    fun targets(itemId: Int, give: Boolean, mask: Long, party: List<MonView>, gen5: Boolean = false): PartyTargets =
        PartyTargets(ItemUseResult.USED, party.take(6).mapIndexed { slot, m ->
            val held = m.heldItemId
            when {
                m.isEgg -> PartyTarget(slot, m.personality, held, SlotVerdict.EGG)
                give && held in KEEP_OUT -> PartyTarget(slot, m.personality, held, SlotVerdict.HOLDS_MAIL)
                give && held == itemId -> PartyTarget(slot, m.personality, held, SlotVerdict.HOLDS_IT)
                give -> PartyTarget(slot, m.personality, held, SlotVerdict.OK)
                picksMove(itemId, gen5) -> {
                    val moves = (0 until 4).map { k -> k < m.moves.size && m.moves[k].id != 0 && mask shr (slot * 4 + k) and 1L != 0L }
                    PartyTarget(slot, m.personality, held, if (moves.any { it }) SlotVerdict.OK else SlotVerdict.NO_EFFECT, moves)
                }
                // any of the slot's bits: Gen 5's check gives an all-moves PP item a bit per move
                else -> PartyTarget(slot, m.personality, held, if (mask shr (slot * 4) and 0xFL != 0L) SlotVerdict.OK else SlotVerdict.NO_EFFECT)
            }
        })

    /** TAKE's view of the party: what each holds, OK where TAKE may take it (the bag's room is the game's to say). */
    fun held(party: List<MonView>, gen5: Boolean = false): PartyTargets =
        PartyTargets(ItemUseResult.USED, party.take(6).mapIndexed { slot, m ->
            val held = m.heldItemId
            PartyTarget(slot, m.personality, held, when {
                m.isEgg -> SlotVerdict.EGG
                held == 0 -> SlotVerdict.NO_EFFECT
                held in KEEP_OUT || (gen5 && held in GEN5_FORM_ITEMS) -> SlotVerdict.HOLDS_MAIL
                else -> SlotVerdict.OK
            })
        })

    /** What USE / GIVE / TAKE's answer [code] (with the item held before, [held]) means to the bag screen. */
    fun outcome(mode: Int, code: Int, held: Int): ItemUseOutcome = when (code) {
        DONE -> when (mode) {
            GIVE -> ItemUseOutcome(ItemUseResult.GIVEN, swapped = held)
            TAKE -> ItemUseOutcome(ItemUseResult.TAKEN, swapped = held)
            else -> ItemUseOutcome(ItemUseResult.USED)
        }
        NO_EFFECT -> ItemUseOutcome(ItemUseResult.NO_EFFECT)
        NONE_LEFT -> ItemUseOutcome(ItemUseResult.NONE_LEFT)
        CHANGED -> ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        BAG_FULL -> ItemUseOutcome(ItemUseResult.BAG_FULL)
        // HOLDS_IT / REFUSED / NOTHING: the pick already greys these out, so only a party changed under it gets here
        HOLDS_IT, REFUSED, NOTHING -> ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        else -> ItemUseOutcome(ItemUseResult.FAILED)
    }
}
