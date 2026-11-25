package com.pokedaisy.app.companion.data

/**
 * The PC's moves on Red, Blue and Yellow (English and German), the current box only: the game's own MoveMon and
 * RemovePokemon (home bank; pokered's / pokeyellow's home/move_mon.asm), the pair Bill's PC runs for DEPOSIT and
 * WITHDRAW (engine/pokemon/bills_pc.asm), called from the overworld loop with [GameCalls.gbCallAt] like Gen 1's PC
 * anywhere. Their arguments are written into wWhichPokemon / wCurPartySpecies / wMoveMonType (also wRemoveMonFromBox)
 * once the call is set up; the next call waits for the loop to come by again, so the one before has returned.
 *
 * The current box is wBoxDataStart in WRAM ([Gen1BoxLayout]): a move changes only RAM, which the next in-game save
 * writes into the cart, party and box together, as the game's own PC does. CHANGE BOX ([changeBox]) is ChangeBox
 * from past its menu (engine/menus/save.asm): the current box into its place in SRAM, the new one into wBoxDataStart,
 * then the whole game saved.
 *
 * Yellow's starter Pikachu stays where it is: the game's PC makes it a case of its own (it follows the player, its
 * happiness drops when it's deposited, a Pikachu that isn't following can't be), so it's refused here
 * ([BoxSwapResult.STARTER_PIKACHU]). Checked headless on all four carts (mgba_dump's `gbcallat`), then saved in the game
 * and rebooted.
 */
class Gen1BoxCalls(
    /** OverworldLoopLessDelay past its `call DelayFrame`: where a call is injected. */
    val at: Int,
    val bankswitch: Int,
    val moveMon: Int,
    val removePokemon: Int,
    /** A `ret` in the home bank (Bankswitch's last byte): a call that does nothing. */
    val ret: Int,
    /** SetMapTextPointer (home): hSavedMapTextPtr = wCurMapTextPtr, which ChangeBox saves the game with. */
    val setMapTextPointer: Int,
    /** ChangeBox past its menu (bank 0x1C): `call GetBoxSRAMLocation` (Yellow: the save sound first). */
    val changeBoxEntry: Int,
    /** EmptyAllSRAMBoxes (bank 0x1C): ChangeBox's first time. */
    val emptyAllBoxes: Int,
    val curPartySpecies: Long,
    val whichPokemon: Long,
    /** wMoveMonType: PARTY_TO_BOX 1 / BOX_TO_PARTY 0; RemovePokemon reads it as wRemoveMonFromBox. */
    val moveMonType: Long,
    val curMapTextPtr: Long,
    val partyCount: Long,
    val layout: Gen1BoxLayout,
    /** Yellow's wPlayerName / wPlayerID, for its starter Pikachu (-1: Red / Blue). */
    val playerName: Long = -1L,
    val playerId: Long = -1L,
    val signatures: List<Pair<Long, String>>,
) : GbBoxMoves {
    override fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = signatures.all { (off, hex) ->
        val want = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        rom(off, want.size)?.contentEquals(want) == true
    }

    private companion object {
        const val CURRENT_MENU_ITEM = 0xCC26L   // the same in every Gen 1 cart
        const val SAVE_BANK = 0x1C
        const val PARTY_MON_SIZE = 44
        const val PARTY_MAX = 6
        const val PIKACHU = 0x54                 // Yellow's STARTER, internal id
        const val PARTY_TO_BOX = 1
        const val BOX_TO_PARTY = 0
        const val FROM_PARTY = 0
        const val FROM_BOX = 1
        /** The save and its sound take about a second: the loop comes by again only then. */
        const val SAVE_SETTLE_TRIES = 160
    }

    private val partyMons get() = partyCount + 8
    private val partyOt get() = partyMons + PARTY_MAX * PARTY_MON_SIZE
    private val boxMons get() = layout.activeBox + layout.monsOff
    private val boxOt get() = boxMons + layout.slots * GEN1_BOX_MON_SIZE

    private fun u8(g: GameCalls, addr: Long) = g.read(addr, 1)[0].toInt() and 0xFF

    private class State(val partyCount: Int, val partySpecies: ByteArray, val party: ByteArray, val boxCount: Int, val box: ByteArray)

    private fun state(g: GameCalls): State {
        val count = u8(g, partyCount).coerceAtMost(PARTY_MAX)
        val box = g.read(layout.activeBox, layout.boxSize)
        return State(count, g.read(partyCount + 1, PARTY_MAX), g.read(partyMons, PARTY_MAX * PARTY_MON_SIZE),
            (box[0].toInt() and 0xFF).coerceAtMost(layout.slots), box)
    }

    private fun partyKey(s: State, slot: Int) = gen1MonKey(s.party, slot * PARTY_MON_SIZE)
    private fun boxKey(s: State, slot: Int) = gen1MonKey(s.box, layout.monsOff + slot * GEN1_BOX_MON_SIZE)
    private fun boxSpecies(s: State, slot: Int) = s.box[1 + slot].toInt() and 0xFF

    /** IsThisMonStarterPikachu: a PIKACHU with the player's ID and the first five letters of the player's name as OT. */
    private fun starterPikachu(g: GameCalls, mon: Long, ot: Long): Boolean {
        if (playerId < 0 || u8(g, mon) != PIKACHU) return false
        if (!g.read(mon + 12, 2).contentEquals(g.read(playerId, 2))) return false
        return g.read(ot, 5).contentEquals(g.read(playerName, 5))
    }

    /** Calls [fn] (home bank) from the loop: false = the loop didn't come by (not on the field). */
    private fun call(g: GameCalls, fn: Int, slot: Int, species: Int, type: Int): Boolean {
        if (!g.gbCallAt(at, fn, 0, 0, 0)) return false
        g.write(whichPokemon, byteArrayOf(slot.toByte()))
        g.write(curPartySpecies, byteArrayOf(species.toByte()))
        g.write(moveMonType, byteArrayOf(type.toByte()))
        return true
    }

    /** Lets the call before finish: the loop coming by again means it has returned. */
    private fun settle(g: GameCalls, tries: Int = 6): Boolean = (0 until tries).any { g.gbCallAt(at, ret, 0, 0, 0) }

    /**
     * CHANGE BOX: box [to] becomes the current one, the game's way (ChangeBox): on the first change every SRAM box set up
     * empty (EmptyAllSRAMBoxes), then the current box into its place, wCurrentBoxNum, the new one into wBoxDataStart, and
     * the whole game saved (SaveGameData), party and all.
     */
    override fun changeBox(g: GameCalls, to: Int): BoxChangeResult {
        val num = u8(g, layout.curBox)
        val current = num and GEN1_BOX_CHANGED.inv()
        if (to !in 0 until layout.numBoxes || current !in 0 until layout.numBoxes) return BoxChangeResult.FAILED
        if (to == current) return BoxChangeResult.CHANGED
        if (num and GEN1_BOX_CHANGED == 0) {
            if (!g.gbCallAt(at, bankswitch, 0, SAVE_BANK, emptyAllBoxes)) return BoxChangeResult.NOT_NOW
            if (!settle(g)) return BoxChangeResult.FAILED
        }
        // ChangeBox saves the game with hSavedMapTextPtr as the map's text: from the PC that's the map's own (the PC's
        // text put its own in its place), from the field whatever text was shown last. SetMapTextPointer with the map's
        // own pointer makes them the same and changes nothing else.
        val text = g.read(curMapTextPtr, 2)
        val hl = (text[0].toInt() and 0xFF) or ((text[1].toInt() and 0xFF) shl 8)
        if (!g.gbCallAt(at, setMapTextPointer, 0, 0, hl)) return BoxChangeResult.NOT_NOW
        if (!settle(g)) return BoxChangeResult.FAILED
        if (!g.gbCallAt(at, bankswitch, 0, SAVE_BANK, changeBoxEntry)) return BoxChangeResult.NOT_NOW
        g.write(CURRENT_MENU_ITEM, byteArrayOf(to.toByte()))
        if (!settle(g, SAVE_SETTLE_TRIES)) return BoxChangeResult.FAILED
        // ChangeBox ends on SetMapTextPointer(wChangeBoxSavedMapTextPointer), which the PC's text undoes once it closes;
        // here no PC closes after it, so the map's own text goes back by hand (else talking to anyone prints garbage).
        g.write(curMapTextPtr, text)
        return if (u8(g, layout.curBox) == (to or GEN1_BOX_CHANGED)) BoxChangeResult.CHANGED else BoxChangeResult.FAILED
    }

    /** WITHDRAW: the current box's slot [slot] ([key]) into the party. */
    override fun withdraw(g: GameCalls, slot: Int, key: Long): BoxSwapResult {
        val s = state(g)
        if (slot !in 0 until s.boxCount || boxKey(s, slot) != key) return BoxSwapResult.CHANGED
        if (s.partyCount >= PARTY_MAX) return BoxSwapResult.PARTY_FULL
        if (starterPikachu(g, boxMons + slot * GEN1_BOX_MON_SIZE, boxOt + slot * layout.nameLength)) return BoxSwapResult.STARTER_PIKACHU
        return moveIn(g, s, slot)
    }

    private fun moveIn(g: GameCalls, s: State, slot: Int): BoxSwapResult {
        if (!call(g, moveMon, slot, boxSpecies(s, slot), BOX_TO_PARTY)) return BoxSwapResult.NOT_NOW
        if (!settle(g) || u8(g, partyCount) != s.partyCount + 1) return BoxSwapResult.FAILED
        if (!call(g, removePokemon, slot, boxSpecies(s, slot), FROM_BOX) || !settle(g)) return BoxSwapResult.FAILED
        return if (u8(g, layout.activeBox) == s.boxCount - 1) BoxSwapResult.SWAPPED else BoxSwapResult.FAILED
    }

    /** DEPOSIT: party slot [partySlot] ([key]) into the current box. */
    override fun deposit(g: GameCalls, partySlot: Int, key: Long): BoxSwapResult {
        val s = state(g)
        refuseOut(g, s, partySlot, key, incoming = false)?.let { return it }
        return moveOut(g, s, partySlot)
    }

    /** What keeps party slot [partySlot] in the party; [incoming]: a box Pokémon takes its place. */
    private fun refuseOut(g: GameCalls, s: State, partySlot: Int, key: Long, incoming: Boolean): BoxSwapResult? = when {
        partySlot !in 0 until s.partyCount || partyKey(s, partySlot) != key -> BoxSwapResult.CHANGED
        // BillsPCDeposit's own rule: never the last one, fainted or not.
        !incoming && s.partyCount <= 1 -> BoxSwapResult.LAST_MON
        starterPikachu(g, partyMons + partySlot * PARTY_MON_SIZE, partyOt + partySlot * layout.nameLength) -> BoxSwapResult.STARTER_PIKACHU
        else -> null
    }

    private fun moveOut(g: GameCalls, s: State, partySlot: Int): BoxSwapResult {
        if (s.boxCount >= layout.slots) return BoxSwapResult.BOX_FULL
        val species = s.partySpecies[partySlot].toInt() and 0xFF
        if (!call(g, moveMon, partySlot, species, PARTY_TO_BOX)) return BoxSwapResult.NOT_NOW
        if (!settle(g) || u8(g, layout.activeBox) != s.boxCount + 1) return BoxSwapResult.FAILED
        if (!call(g, removePokemon, partySlot, species, FROM_PARTY) || !settle(g)) return BoxSwapResult.FAILED
        return if (u8(g, partyCount) == s.partyCount - 1) BoxSwapResult.SWAPPED else BoxSwapResult.FAILED
    }

    /**
     * SWAP: party slot [partySlot] ([partyKey]) and box slot [slot] ([boxKey]) trade places, the box one first while the
     * party has room (so the party is never empty in between), else the party one first.
     */
    override fun swap(g: GameCalls, slot: Int, boxKey: Long, partySlot: Int, partyKey: Long): BoxSwapResult {
        val s = state(g)
        if (slot !in 0 until s.boxCount || boxKey(s, slot) != boxKey) return BoxSwapResult.CHANGED
        refuseOut(g, s, partySlot, partyKey, incoming = true)?.let { return it }
        if (starterPikachu(g, boxMons + slot * GEN1_BOX_MON_SIZE, boxOt + slot * layout.nameLength)) return BoxSwapResult.STARTER_PIKACHU
        if (s.partyCount < PARTY_MAX) {
            val got = moveIn(g, s, slot)
            if (got != BoxSwapResult.SWAPPED) return got
            return moveOut(g, state(g), partySlot)
        }
        val out = moveOut(g, s, partySlot)
        if (out != BoxSwapResult.SWAPPED) return out
        return moveIn(g, state(g), slot)
    }
}

/** English Red / Blue: pokered.sym (Blue's code is Red's at the same places). */
val GEN1_BOX_CALLS_RED = Gen1BoxCalls(
    at = 0x0405, bankswitch = 0x35D6, moveMon = 0x3A68, removePokemon = 0x391F, ret = 0x35EB, setMapTextPointer = 0x3F0F,
    changeBoxEntry = 0x78CD, emptyAllBoxes = 0x7A29,
    curPartySpecies = 0xCF91L, whichPokemon = 0xCF92L, moveMonType = 0xCF95L, curMapTextPtr = 0xD36CL, partyCount = 0xD163L,
    layout = GEN1_RED_BOXES,
    signatures = listOf(
        0x3A68L to "f0b8f53e03e0b8ea0020cd",            // MoveMon: bank 3's _MoveMon
        0x391FL to "21687b0601c3d635e5",                // RemovePokemon: jpfar _RemovePokemon
        0x35EBL to "c9",
        0x3F0FL to "fa6cd3e0ecfa6dd3e0ed7dea",          // SetMapTextPointer
        0x1C * 0x4000L + (0x78CD - 0x4000) to "cd7b785d542180dacd0e79fa26cc", // ChangeBox past its menu
        0x1C * 0x4000L + (0x7A29 - 0x4000) to "3e0aea00003e01ea",             // EmptyAllSRAMBoxes
    ),
)

/** German Rote / Blaue Edition: Red's code, its RAM + 5 from wOnSGB up, the home bank and bank 0x1C moved. */
val GEN1_BOX_CALLS_RED_DE = Gen1BoxCalls(
    at = 0x0405, bankswitch = 0x35F0, moveMon = 0x3A82, removePokemon = 0x3939, ret = 0x3605, setMapTextPointer = 0x3F29,
    changeBoxEntry = 0x78A6, emptyAllBoxes = 0x7A02,
    curPartySpecies = 0xCF96L, whichPokemon = 0xCF97L, moveMonType = 0xCF9AL, curMapTextPtr = 0xD371L, partyCount = 0xD168L,
    layout = GEN1_RED_DE_BOXES,
    signatures = listOf(
        0x3A82L to "f0b8f53e03e0b8ea0020cd",
        0x3939L to "21667b0601c3f035e5",
        0x3605L to "c9",
        0x3F29L to "fa71d3e0ecfa72d3e0ed7dea",
        0x1C * 0x4000L + (0x78A6 - 0x4000) to "cd54785d542185dacde778fa26cc",
        0x1C * 0x4000L + (0x7A02 - 0x4000) to "3e0aea00003e01ea",
    ),
)

/** English Yellow: pokeyellow.sym. */
val GEN1_BOX_CALLS_YELLOW = Gen1BoxCalls(
    at = 0x0248, bankswitch = 0x3E84, moveMon = 0x3A59, removePokemon = 0x3914, ret = 0x3E97, setMapTextPointer = 0x3F54,
    changeBoxEntry = 0x7C16, emptyAllBoxes = 0x7D65,
    curPartySpecies = 0xCF90L, whichPokemon = 0xCF91L, moveMonType = 0xCF94L, curMapTextPtr = 0xD36BL, partyCount = 0xD162L,
    layout = GEN1_YELLOW_BOXES, playerName = 0xD157L, playerId = 0xD358L,
    signatures = listOf(
        0x3A59L to "f0b8f53e03cd7e3ecda473",
        0x3914L to "210f7a0601c3843ee5",
        0x3E97L to "c9",
        0x3F54L to "fa6bd3e0ecfa6cd3e0ed7dea",
        0x1C * 0x4000L + (0x7C16 - 0x4000) to "3eb6cd3637cd3e37cdc47b5d5421", // ChangeBox past its menu: SFX_SAVE first
        0x1C * 0x4000L + (0x7D65 - 0x4000) to "cd9f7e3e02ea0040",
    ),
)

/** German Gelbe Edition: Yellow's code, its RAM + 5, the home bank and bank 0x1C moved. */
val GEN1_BOX_CALLS_YELLOW_DE = Gen1BoxCalls(
    at = 0x0248, bankswitch = 0x3E89, moveMon = 0x3A5E, removePokemon = 0x3919, ret = 0x3E9C, setMapTextPointer = 0x3F59,
    changeBoxEntry = 0x7BF5, emptyAllBoxes = 0x7D44,
    curPartySpecies = 0xCF95L, whichPokemon = 0xCF96L, moveMonType = 0xCF99L, curMapTextPtr = 0xD370L, partyCount = 0xD167L,
    layout = GEN1_YELLOW_DE_BOXES, playerName = 0xD15CL, playerId = 0xD35DL,
    signatures = listOf(
        0x3A5EL to "f0b8f53e03cd833ecd9973",
        0x3919L to "21f2790601c3893ee5",
        0x3E9CL to "c9",
        0x3F59L to "fa70d3e0ecfa71d3e0ed7dea",
        0x1C * 0x4000L + (0x7BF5 - 0x4000) to "3eb6cd3b37cd4337cda37b5d5421",
        0x1C * 0x4000L + (0x7D44 - 0x4000) to "cd7e7e3e02ea0040",
    ),
)

/**
 * Japanese Green in LandaR's translation: pret/pokegreen.sym, the Japanese RAM and box ([GEN1_GREEN_BOXES]: 8 of 30).
 * ChangeBox past its menu starts on the save sound, like Yellow's.
 */
val GEN1_BOX_CALLS_GREEN_LANDAR = Gen1BoxCalls(
    at = 0x1E22, bankswitch = 0x3620, moveMon = 0x3AB2, removePokemon = 0x3969, ret = 0x3635, setMapTextPointer = 0x3F3F,
    changeBoxEntry = 0x7D3D, emptyAllBoxes = 0x7E9C,
    curPartySpecies = 0xCF78L, whichPokemon = 0xCF79L, moveMonType = 0xCF7CL, curMapTextPtr = 0xD2EBL, partyCount = 0xD123L,
    layout = GEN1_GREEN_BOXES,
    signatures = listOf(
        0x3AB2L to "f0b8f53e03e0b8ea0020cd",
        0x3969L to "211a7f0601c32036e5",
        0x3635L to "c9",
        0x3F3FL to "faebd2e0ecfaecd2e0ed7dea",
        0x1C * 0x4000L + (0x7D3D - 0x4000) to "3eb6cd8837cd9037cdf97c5d5421",
        0x1C * 0x4000L + (0x7E9C - 0x4000) to "3e0aea00003e01ea",
    ),
)

/**
 * A Game Boy game's PC moves on its current box: the slots and keys ([gbMonKey]) the companion showed, refused with
 * [BoxSwapResult.CHANGED] when they aren't what the game has any more.
 */
interface GbBoxMoves {
    fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean
    fun withdraw(g: GameCalls, slot: Int, key: Long): BoxSwapResult
    fun deposit(g: GameCalls, partySlot: Int, key: Long): BoxSwapResult
    fun swap(g: GameCalls, slot: Int, boxKey: Long, partySlot: Int, partyKey: Long): BoxSwapResult
    fun changeBox(g: GameCalls, to: Int): BoxChangeResult
}

/** Crystal's / Gold and Silver's [Gen2BoxCalls] as [GbBoxMoves], with the cart's config. */
class Gen2BoxMoves(private val calls: Gen2BoxCalls, private val cfg: Gen2Config) : GbBoxMoves {
    override fun codeMatches(rom: (Long, Int) -> ByteArray?) = calls.codeMatches(rom)
    override fun withdraw(g: GameCalls, slot: Int, key: Long) = calls.withdraw(g, cfg, slot, key)
    override fun deposit(g: GameCalls, partySlot: Int, key: Long) = calls.deposit(g, cfg, partySlot, key)
    override fun swap(g: GameCalls, slot: Int, boxKey: Long, partySlot: Int, partyKey: Long) =
        calls.swap(g, cfg, slot, boxKey, partySlot, partyKey)
    override fun changeBox(g: GameCalls, to: Int) = calls.changeBox(g, to)
}
