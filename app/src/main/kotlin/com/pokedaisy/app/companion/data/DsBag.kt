package com.pokedaisy.app.companion.data

/**
 * The bag on a party Pokémon in the DS games (Platinum, HeartGold / SoulSilver): USE of medicine, PP items and the
 * berries a party menu takes, GIVE and TAKE - each run by the game's own party-menu functions in one job
 * (nds/pk_nds_call.cpp's PK_NDS_ACTION_ITEM, its helper nds/stub/pk_nds_item.c). This side says which items get
 * which button and what the helper's answers mean; the game decides whether an item does anything to a Pokémon.
 *
 * Only the carts each job was proved on headless ([CARTS]: a new game given two Pokémon and items by its own script
 * commands, every case run, then saved in game and rebooted from the save). Item ids are Gen 4's, the same in all three.
 */
object DsBag {
    /** Game code and revision of the carts the jobs were proved on. */
    val CARTS = setOf("CPUE" to 1, "IPKE" to 0, "IPGE" to 0)

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

    fun canUse(itemId: Int): Boolean = itemId in USABLE

    fun picksMove(itemId: Int): Boolean = itemId in ONE_MOVE

    /** GIVE: what a Pokémon can hold - no key items, TMs / HMs or Mail ([pocket] the item's, from the game's tables). */
    fun canGive(itemId: Int, pocket: Int?): Boolean =
        itemId > 0 && itemId !in KEEP_OUT && pocket != null &&
            pocket != POCKET_KEY_ITEMS && pocket != POCKET_TM_HM && pocket != POCKET_MAIL

    /** The CHECK job's argument for [itemId]: every move of every Pokémon for a one-move item. */
    fun checkArgs(itemId: Int, partyCount: Int): IntArray =
        intArrayOf(CHECK or ((if (picksMove(itemId)) EACH_MOVE else 0) shl 16), itemId, 0, partyCount)

    /** USE / GIVE / TAKE's arguments. */
    fun args(mode: Int, itemId: Int, slot: Int, move: Int, personality: Long, partyCount: Int): IntArray =
        intArrayOf(mode or (slot shl 8) or (move.coerceIn(0, 3) shl 16), itemId, personality.toInt(), partyCount)

    /**
     * The party pick from CHECK's [mask] (bit slot * 4 + move) and the party as the companion read it: eggs, Mail and
     * the item already held as the GBA's pick says them.
     */
    fun targets(itemId: Int, give: Boolean, mask: Long, party: List<MonView>): PartyTargets =
        PartyTargets(ItemUseResult.USED, party.take(6).mapIndexed { slot, m ->
            val held = m.heldItemId
            when {
                m.isEgg -> PartyTarget(slot, m.personality, held, SlotVerdict.EGG)
                give && held in KEEP_OUT -> PartyTarget(slot, m.personality, held, SlotVerdict.HOLDS_MAIL)
                give && held == itemId -> PartyTarget(slot, m.personality, held, SlotVerdict.HOLDS_IT)
                give -> PartyTarget(slot, m.personality, held, SlotVerdict.OK)
                picksMove(itemId) -> {
                    val moves = (0 until 4).map { k -> k < m.moves.size && m.moves[k].id != 0 && mask shr (slot * 4 + k) and 1L != 0L }
                    PartyTarget(slot, m.personality, held, if (moves.any { it }) SlotVerdict.OK else SlotVerdict.NO_EFFECT, moves)
                }
                else -> PartyTarget(slot, m.personality, held, if (mask shr (slot * 4) and 1L != 0L) SlotVerdict.OK else SlotVerdict.NO_EFFECT)
            }
        })

    /** TAKE's view of the party: what each holds, OK where TAKE may take it (the bag's room is the game's to say). */
    fun held(party: List<MonView>): PartyTargets =
        PartyTargets(ItemUseResult.USED, party.take(6).mapIndexed { slot, m ->
            val held = m.heldItemId
            PartyTarget(slot, m.personality, held, when {
                m.isEgg -> SlotVerdict.EGG
                held == 0 -> SlotVerdict.NO_EFFECT
                held in KEEP_OUT -> SlotVerdict.HOLDS_MAIL
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
