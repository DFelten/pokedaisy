package com.pokedaisy.app.companion.data

/**
 * The PC's moves on Crystal (English rev 0 / rev 1 / Australia, German Kristall), the current box only: the game's own
 * SendGetMonIntoFromBox and RemoveMonFromPartyOrBox (pokecrystal's engine/pokemon/move_mon.asm, both bank 3), the pair the
 * PC's DEPOSIT / WITHDRAW run, far-called from the overworld loop (HandleMap) with [GameCalls.gbCallAt] like Gen 1's PC.
 * Only the current box: the game keeps it in sBox (SRAM bank 1), the others only on a box change, which saves the game.
 *
 * Each call is injected at HandleMap, its arguments written into wCurPartyMon / wCurPartySpecies /
 * wPokemonWithdrawDepositParameter before it runs; the next call waits for the loop to come by again, so the one before
 * has returned. A `ret` far-called there ([SETTLE]) lets a call finish before its result is read. A move is the copy,
 * its count checked, then the removal, its count checked: refused before either if the party or the box isn't what the
 * companion showed. Like the game's PC, the box is in the battery RAM at once and the party once the game is saved.
 * Checked headless on both carts (mgba_dump's `gbcallat` / `gbsram`), then saved in the game and rebooted.
 */
class Gen2BoxCalls(
    /** SendGetMonIntoFromBox's address, bank 3. */
    val send: Int = 0x5B3F,
    /** RemoveMonFromPartyOrBox's address, bank 3. */
    val remove: Int,
    /** Cart offsets and the bytes the code there must have. */
    val signatures: List<Pair<Long, String>>,
    /** HandleMap's first instruction, as (bank << 16) | addr: the overworld loop, where a call is injected. */
    val at: Int = 0x256773,
    val curPartySpecies: Long = wramx(1, 0xD108),
    val curPartyMon: Long = wramx(1, 0xD109),
    val withdrawDeposit: Long = wramx(1, 0xD10B),
    val box: Long = GEN2_CRYSTAL_BOXES.activeBox,
    /** SaveBox / LoadBox / SaveGameData, bank 5: what the PC's CHANGE BOX runs around its save (ChangeBoxSaveGame). */
    val saveBox: Int = 0x4E0C,
    val loadBox: Int = 0x5021,
    val saveGame: Int = 0x4B85,
    val curBox: Long = GEN2_CRYSTAL_BOXES.curBox,
    /** wPlayerID and the save's copy of it (sPlayerData's first word), and sCheckValue1 (99 once the game was saved). */
    val playerId: Long = wramx(1, 0xD47B),
    val savedPlayerId: Long = cartRam(1, 0xA009),
    val saveCheck: Long = cartRam(1, 0xA008),
    /** _SwitchPartyMons.SwapMonAndMail, bank 0x14: the party menu's SWITCH without its screen and sound. */
    val switchMons: Int = 0x4F62,
    /** wSwitchMonTo / wSwitchMonFrom: the two slots it trades, 0-based. */
    val switchTo: Long = wramx(1, 0xD1EB),
    val switchFrom: Long = wramx(1, 0xD1EC),
    /** SwapMonAndMail's cart offset and bytes: checked on its own ([switchMatches]), so a miss there leaves the PC's moves on. */
    val switchSignature: Pair<Long, String>? = null,
) {
    fun codeMatches(rom: (Long, Int) -> ByteArray?): Boolean = signatures.all { matches(rom, it) }

    /** The party menu's SWITCH ([swapParty]) is where [switchSignature] says. */
    fun switchMatches(rom: (Long, Int) -> ByteArray?): Boolean = switchSignature?.let { matches(rom, it) } == true

    private fun matches(rom: (Long, Int) -> ByteArray?, sig: Pair<Long, String>): Boolean {
        val want = sig.second.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return rom(sig.first, want.size)?.contentEquals(want) == true
    }

    private companion object {
        const val FARCALL = 0x0008   // FarCall_hl: a = bank, hl = address
        const val SETTLE = 0x0015    // rst $10's (Bankswitch's) closing `ret`: a call that does nothing
        const val BANK = 3
        const val PARTY_MON_SIZE = 0x30
        const val BOX_MON_SIZE = 32
        const val BOX_MONS = 0x16
        const val BOX_SLOTS = 20
        const val PARTY_MAX = 6
        const val EGG = 0xFD
        const val HP = 0x22
        const val ITEM = 1
        // wPokemonWithdrawDepositParameter: Send's PC_WITHDRAW 0 / PC_DEPOSIT 1, Remove's REMOVE_PARTY 0 / REMOVE_BOX 1.
        const val TO_PARTY = 0
        const val TO_BOX = 1
        const val FROM_PARTY = 0
        const val FROM_BOX = 1
        const val SAVE_BANK = 5
        const val SWITCH_BANK = 0x14
        const val NUM_BOXES = 14
        const val SAVE_CHECK_VALUE = 99
        /** The save takes about a dozen frames: the loop comes by again only then. */
        const val SAVE_SETTLE_TRIES = 40
        const val SAVE_TRIES = 5
    }

    private fun u8(g: GameCalls, addr: Long) = g.read(addr, 1)[0].toInt() and 0xFF

    private class State(val partyCount: Int, val partySpecies: ByteArray, val party: ByteArray, val boxCount: Int, val box: ByteArray)

    private fun state(g: GameCalls, cfg: Gen2Config): State {
        val count = u8(g, cfg.partyCount).coerceAtMost(PARTY_MAX)
        val box = g.read(this.box, BOX_MONS + BOX_SLOTS * BOX_MON_SIZE)
        return State(count, g.read(cfg.partyCount + 1, PARTY_MAX), g.read(cfg.partyMons, PARTY_MAX * PARTY_MON_SIZE),
            (box[0].toInt() and 0xFF).coerceAtMost(BOX_SLOTS), box)
    }

    private fun partyKey(s: State, slot: Int) = gen2MonKey(s.party, slot * PARTY_MON_SIZE)
    private fun boxKey(s: State, slot: Int) = gen2MonKey(s.box, BOX_MONS + slot * BOX_MON_SIZE)
    private fun boxSpecies(s: State, slot: Int) = s.box[1 + slot].toInt() and 0xFF

    /** A party Pokémon that could fight: not an egg, HP left. */
    private fun fighter(s: State, slot: Int) = (s.partySpecies[slot].toInt() and 0xFF) != EGG &&
        ((s.party[slot * PARTY_MON_SIZE + HP].toInt() and 0xFF) shl 8 or (s.party[slot * PARTY_MON_SIZE + HP + 1].toInt() and 0xFF)) > 0

    /** Far-calls [fn] with its arguments: false = the loop didn't come by (not on the field). */
    private fun call(g: GameCalls, fn: Int, slot: Int, species: Int, param: Int): Boolean {
        if (!g.gbCallAt(at, FARCALL, BANK, 0, fn)) return false
        g.write(curPartyMon, byteArrayOf(slot.toByte()))
        g.write(curPartySpecies, byteArrayOf(species.toByte()))
        g.write(withdrawDeposit, byteArrayOf(param.toByte()))
        return true
    }

    /** Lets the call before finish: the loop coming by again means it has returned. */
    private fun settle(g: GameCalls, tries: Int = 6): Boolean = (0 until tries).any { g.gbCallAt(at, SETTLE, 0, 0, 0) }

    /**
     * CHANGE BOX: box [to] becomes the current one, the game's way (ChangeBoxSaveGame): the current box into its place
     * (SaveBox), wCurBox, the new one into sBox (LoadBox), then the whole game saved (SaveGameData), party and all. Refused
     * like AskOverwriteSaveFile would: with no save, or another player's on the cart ([BoxChangeResult.OTHER_SAVE]).
     */
    fun changeBox(g: GameCalls, to: Int): BoxChangeResult {
        val current = u8(g, curBox)
        if (to !in 0 until NUM_BOXES || current !in 0 until NUM_BOXES) return BoxChangeResult.FAILED
        if (to == current) return BoxChangeResult.CHANGED
        if (u8(g, saveCheck) != SAVE_CHECK_VALUE || !g.read(playerId, 2).contentEquals(g.read(savedPlayerId, 2))) return BoxChangeResult.OTHER_SAVE
        if (!g.gbCallAt(at, FARCALL, SAVE_BANK, 0, saveBox)) return BoxChangeResult.NOT_NOW
        if (!settle(g)) return BoxChangeResult.FAILED
        g.write(curBox, byteArrayOf(to.toByte()))
        // LoadBox never ran: sBox still holds the current box (SaveBox put it in its place too), which keeps its number.
        if (!g.gbCallAt(at, FARCALL, SAVE_BANK, 0, loadBox)) return BoxChangeResult.FAILED.also { g.write(curBox, byteArrayOf(current.toByte())) }
        if (!settle(g)) return BoxChangeResult.FAILED.also { loadBack(g, current, to) }
        // The save has to follow: until it does, the cart's sBox is the new box under a save that names the old one.
        if (!(0 until SAVE_TRIES).any { g.gbCallAt(at, FARCALL, SAVE_BANK, 0, saveGame) }) {
            if (g.gbCallAt(at, FARCALL, SAVE_BANK, 0, saveBox)) settle(g)
            return BoxChangeResult.FAILED.also { loadBack(g, current, to) }
        }
        if (!settle(g, SAVE_SETTLE_TRIES)) return BoxChangeResult.FAILED
        return if (u8(g, curBox) == to) BoxChangeResult.CHANGED else BoxChangeResult.FAILED
    }

    /**
     * A box change that stopped half way, box [to] in sBox: wCurBox back to [current] and that box into sBox again, as the
     * save has them. If LoadBox can't run, wCurBox stays [to], the box sBox holds: naming it [current] would let the next
     * save's SaveBox write [to]'s Pokémon over [current]'s.
     */
    private fun loadBack(g: GameCalls, current: Int, to: Int) {
        g.write(curBox, byteArrayOf(current.toByte()))
        if ((0 until SAVE_TRIES).any { g.gbCallAt(at, FARCALL, SAVE_BANK, 0, loadBox) }) settle(g)
        else g.write(curBox, byteArrayOf(to.toByte()))
    }

    /** WITHDRAW: the current box's slot [slot] ([key]) into the party. */
    fun withdraw(g: GameCalls, cfg: Gen2Config, slot: Int, key: Long): BoxSwapResult {
        val s = state(g, cfg)
        if (slot !in 0 until s.boxCount || boxKey(s, slot) != key) return BoxSwapResult.CHANGED
        if (s.partyCount >= PARTY_MAX) return BoxSwapResult.PARTY_FULL
        return moveIn(g, cfg, s, slot)
    }

    private fun moveIn(g: GameCalls, cfg: Gen2Config, s: State, slot: Int): BoxSwapResult {
        if (!call(g, send, slot, boxSpecies(s, slot), TO_PARTY)) return BoxSwapResult.NOT_NOW
        if (!settle(g) || u8(g, cfg.partyCount) != s.partyCount + 1) return BoxSwapResult.FAILED
        if (!call(g, remove, slot, boxSpecies(s, slot), FROM_BOX) || !settle(g)) return BoxSwapResult.FAILED
        return if ((g.read(box, 1)[0].toInt() and 0xFF) == s.boxCount - 1) BoxSwapResult.SWAPPED else BoxSwapResult.FAILED
    }

    /** DEPOSIT: party slot [partySlot] ([key]) into the current box. */
    fun deposit(g: GameCalls, cfg: Gen2Config, partySlot: Int, key: Long): BoxSwapResult {
        val s = state(g, cfg)
        refuseOut(cfg, s, partySlot, key, incomingFights = false)?.let { return it }
        return moveOut(g, cfg, s, partySlot)
    }

    /** What keeps party slot [partySlot] in the party; [incomingFights]: a box Pokémon that can fight takes its place. */
    private fun refuseOut(cfg: Gen2Config, s: State, partySlot: Int, key: Long, incomingFights: Boolean): BoxSwapResult? = when {
        partySlot !in 0 until s.partyCount || partyKey(s, partySlot) != key -> BoxSwapResult.CHANGED
        (s.party[partySlot * PARTY_MON_SIZE + ITEM].toInt() and 0xFF) in cfg.mailItems -> BoxSwapResult.HOLDS_MAIL
        !incomingFights && (0 until s.partyCount).none { it != partySlot && fighter(s, it) } -> BoxSwapResult.LAST_MON
        s.boxCount >= BOX_SLOTS -> BoxSwapResult.BOX_FULL
        else -> null
    }

    private fun moveOut(g: GameCalls, cfg: Gen2Config, s: State, partySlot: Int): BoxSwapResult {
        val species = s.partySpecies[partySlot].toInt() and 0xFF
        if (!call(g, send, partySlot, species, TO_BOX)) return BoxSwapResult.NOT_NOW
        if (!settle(g) || (g.read(box, 1)[0].toInt() and 0xFF) != s.boxCount + 1) return BoxSwapResult.FAILED
        if (!call(g, remove, partySlot, species, FROM_PARTY) || !settle(g)) return BoxSwapResult.FAILED
        return if (u8(g, cfg.partyCount) == s.partyCount - 1) BoxSwapResult.SWAPPED else BoxSwapResult.FAILED
    }

    /**
     * Party slots [a] ([keyA]) and [b] ([keyB]) traded, the party menu's SWITCH: the game's own SwapMonAndMail trades their
     * species entries, records, OT names, nicknames and mail (sPartyMail, in the battery RAM like the game's). Refused if
     * the party isn't what the companion showed; read back. The party reaches the save at the next in-game save.
     */
    fun swapParty(g: GameCalls, cfg: Gen2Config, a: Int, keyA: Long, b: Int, keyB: Long): PartySwapResult {
        val s = state(g, cfg)
        if (a == b || a !in 0 until s.partyCount || b !in 0 until s.partyCount) return PartySwapResult.FAILED
        if (partyKey(s, a) != keyA || partyKey(s, b) != keyB) return PartySwapResult.FAILED
        if (!g.gbCallAt(at, FARCALL, SWITCH_BANK, 0, switchMons)) return PartySwapResult.NOT_NOW
        g.write(switchTo, byteArrayOf(a.toByte()))
        g.write(switchFrom, byteArrayOf(b.toByte()))
        if (!settle(g)) return PartySwapResult.FAILED
        val after = state(g, cfg)
        return if (after.partyCount == s.partyCount && partyKey(after, a) == keyB && partyKey(after, b) == keyA &&
            after.partySpecies[a] == s.partySpecies[b] && after.partySpecies[b] == s.partySpecies[a]) PartySwapResult.SWAPPED
        else PartySwapResult.FAILED
    }

    /** SWAP: party slot [partySlot] ([partyKey]) into the box, then box slot [slot] ([boxKey]) out (it keeps its slot:
     * a deposit goes to the box's end). */
    fun swap(g: GameCalls, cfg: Gen2Config, slot: Int, boxKey: Long, partySlot: Int, partyKey: Long): BoxSwapResult {
        val s = state(g, cfg)
        if (slot !in 0 until s.boxCount || boxKey(s, slot) != boxKey) return BoxSwapResult.CHANGED
        refuseOut(cfg, s, partySlot, partyKey, incomingFights = boxSpecies(s, slot) != EGG)?.let { return it }
        val out = moveOut(g, cfg, s, partySlot)
        if (out != BoxSwapResult.SWAPPED) return out
        return moveIn(g, cfg, state(g, cfg), slot)
    }
}

/** What [Gen2BoxCalls.changeBox] did. */
enum class BoxChangeResult {
    /** The box is the current one now and the game is saved. */
    CHANGED,
    /** Not on the field with the controls free. */
    NOT_NOW,
    IN_BATTLE,
    /** The game was never saved, or the save on the cart is another player's: the game would ask first. */
    OTHER_SAVE,
    /** The save couldn't be backed up first: nothing was changed. */
    NO_BACKUP,
    FAILED,
}

/** SwapMonAndMail's start (push hl / de / bc, ld bc, wPartySpecies, ld a, [wSwitchMonTo] ... [wSwitchMonFrom]). */
private const val CRYSTAL_SWITCH_CODE = "e5d5c501d8dcfaebd16f260009545dfaecd1"
private const val GOLD_SILVER_SWITCH_CODE = "e5d5c50123dafad4d06f260009545dfad5d0"

/** SaveBox, LoadBox and SaveGameData: the same bytes in every Crystal cart these calls are for. */
private val SAVE_SIGNATURES = listOf(
    0x05 * 0x4000L + (0x4E0C - 0x4000) to "cdd850cdf950c9",   // SaveBox
    0x05 * 0x4000L + (0x5021 - 0x4000) to "cdd850cd7d51c9",   // LoadBox
    0x05 * 0x4000L + (0x4B85 - 0x4000) to "cd104cc9",         // SaveGameData
)

/** Crystal rev 0 / rev 1 / Australia: pret/pokecrystal's symbols, the same in all three. */
val GEN2_BOX_CALLS_CRYSTAL = Gen2BoxCalls(
    remove = 0x6039,
    signatures = listOf(
        0x03 * 0x4000L + (0x5B3F - 0x4000) to "3e01cdcb2ffa0bd1a7",   // SendGetMonIntoFromBox: OpenSRAM, the parameter
        0x03 * 0x4000L + (0x6039 - 0x4000) to "21d7dcfa0bd1a72808",   // RemoveMonFromPartyOrBox: wPartyCount, the parameter
        0x25 * 0x4000L + (0x6773 - 0x4000) to "cdb067cdc1673e2521087e", // HandleMap
    ) + SAVE_SIGNATURES,
    switchSignature = 0x14 * 0x4000L + (0x4F62 - 0x4000) to CRYSTAL_SWITCH_CODE,  // SwapMonAndMail
)

/** German Kristall: Crystal's, RemoveMonFromPartyOrBox a byte earlier, SwapMonAndMail 0x16 bytes earlier. */
val GEN2_BOX_CALLS_CRYSTAL_DE = Gen2BoxCalls(
    remove = 0x6038,
    switchMons = 0x4F4C,
    signatures = listOf(
        0x03 * 0x4000L + (0x5B3F - 0x4000) to "3e01cdb52ffa0bd1a7",
        0x03 * 0x4000L + (0x6038 - 0x4000) to "21d7dcfa0bd1a72808",
        0x25 * 0x4000L + (0x6773 - 0x4000) to "cdb067cdc1673e2521097e",
    ) + SAVE_SIGNATURES,
    switchSignature = 0x14 * 0x4000L + (0x4F4C - 0x4000) to CRYSTAL_SWITCH_CODE,
)

/**
 * Gold / Silver: pret/pokegold's symbols (its builds are the English carts byte for byte), the same functions as Crystal's at
 * their own addresses; Silver's bank 3 code sits 2 bytes before Gold's (SwapMonAndMail, bank 0x14, is at the same place in both). German Gold / Silver keep English's RAM and bank 3
 * addresses but RemoveMonFromPartyOrBox (a byte earlier) and bank 5's SaveBox / LoadBox / SaveGameData, found by their code.
 * Each checked headless on its cart (the `gold_boxes` ... fixtures, a box change saved and rebooted).
 */
private fun goldSilverBoxCalls(send: Int, remove: Int, sendCode: String, handleMap: String, saveBox: Int, loadBox: Int, saveGame: Int,
                               saveCode: List<String>, switchMons: Int) = Gen2BoxCalls(
    send = send,
    remove = remove,
    signatures = listOf(
        0x03 * 0x4000L + (send - 0x4000) to sendCode,                 // SendGetMonIntoFromBox: OpenSRAM, the parameter
        0x03 * 0x4000L + (remove - 0x4000) to "2122dafa08d0a72808",   // RemoveMonFromPartyOrBox: wPartyCount, the parameter
        0x25 * 0x4000L + (0x66B8 - 0x4000) to handleMap,              // HandleMap
        0x05 * 0x4000L + (saveBox - 0x4000) to saveCode[0],           // SaveBox
        0x05 * 0x4000L + (loadBox - 0x4000) to saveCode[1],           // LoadBox
        0x05 * 0x4000L + (saveGame - 0x4000) to saveCode[2],          // SaveGameData
    ),
    switchSignature = 0x14 * 0x4000L + (switchMons - 0x4000) to GOLD_SILVER_SWITCH_CODE, // SwapMonAndMail
    at = 0x2566B8,
    curPartySpecies = wramx(1, 0xD004),
    curPartyMon = wramx(1, 0xD005),
    withdrawDeposit = wramx(1, 0xD008),
    box = GEN2_GOLD_SILVER_BOXES.activeBox,
    saveBox = saveBox,
    loadBox = loadBox,
    saveGame = saveGame,
    curBox = GEN2_GOLD_SILVER_BOXES.curBox,
    playerId = wramx(1, 0xD1A1),
    switchMons = switchMons,
    switchTo = wramx(1, 0xD0D4),
    switchFrom = wramx(1, 0xD0D5),
)

private val GOLD_SILVER_SAVE_CODE = listOf("cd4651cd6751c9", "cd4651cdeb51c9", "cdcc4cc9")
private val GOLD_SILVER_DE_SAVE_CODE = listOf("cd5b51cd7c51c9", "cd5b51cd0052c9", "cdc94cc9")

val GEN2_BOX_CALLS_GOLD = goldSilverBoxCalls(0x5B45, 0x603F, "3e01cde130fa08d0a7", "cdf566cd06673e2521397c", 0x4DF6, 0x5065, 0x4C23, GOLD_SILVER_SAVE_CODE, 0x566B)
val GEN2_BOX_CALLS_SILVER = goldSilverBoxCalls(0x5B43, 0x603D, "3e01cde130fa08d0a7", "cdf566cd06673e2521397c", 0x4DF6, 0x5065, 0x4C23, GOLD_SILVER_SAVE_CODE, 0x566B)
val GEN2_BOX_CALLS_GOLD_DE = goldSilverBoxCalls(0x5B45, 0x603E, "3e01cd0e31fa08d0a7", "cdf566cd06673e2521587c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5660)
val GEN2_BOX_CALLS_SILVER_DE = goldSilverBoxCalls(0x5B43, 0x603C, "3e01cd0e31fa08d0a7", "cdf566cd06673e2521587c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5660)

/** French, Italian and Spanish Crystal / Gold / Silver: German's code and English's RAM at each cart's own addresses (each found by its code, checked headless). */
val GEN2_BOX_CALLS_CRYSTAL_FR = Gen2BoxCalls(
    send = 0x5B0E,
    remove = 0x6009,
    switchMons = 0x4F55,
    signatures = listOf(
        0x03 * 0x4000L + (0x5B0E - 0x4000) to "3e01cdb82ffa0bd1a7",
        0x03 * 0x4000L + (0x6009 - 0x4000) to "21d7dcfa0bd1a72808",
        0x25 * 0x4000L + (0x6773 - 0x4000) to "cdb067cdc1673e2521097e",
    ) + SAVE_SIGNATURES,
    switchSignature = 0x14 * 0x4000L + (0x4F55 - 0x4000) to CRYSTAL_SWITCH_CODE,
)
val GEN2_BOX_CALLS_CRYSTAL_IT = Gen2BoxCalls(
    send = 0x5B40,
    remove = 0x603B,
    switchMons = 0x4F71,
    signatures = listOf(
        0x03 * 0x4000L + (0x5B40 - 0x4000) to "3e01cdb92ffa0bd1a7",
        0x03 * 0x4000L + (0x603B - 0x4000) to "21d7dcfa0bd1a72808",
        0x25 * 0x4000L + (0x6773 - 0x4000) to "cdb067cdc1673e2521077e",
    ) + SAVE_SIGNATURES,
    switchSignature = 0x14 * 0x4000L + (0x4F71 - 0x4000) to CRYSTAL_SWITCH_CODE,
)
val GEN2_BOX_CALLS_CRYSTAL_ES = Gen2BoxCalls(
    send = 0x5B0D,
    remove = 0x6009,
    switchMons = 0x4F6B,
    signatures = listOf(
        0x03 * 0x4000L + (0x5B0D - 0x4000) to "3e01cdb52ffa0bd1a7",
        0x03 * 0x4000L + (0x6009 - 0x4000) to "21d7dcfa0bd1a72808",
        0x25 * 0x4000L + (0x6773 - 0x4000) to "cdb067cdc1673e2521017e",
    ) + SAVE_SIGNATURES,
    switchSignature = 0x14 * 0x4000L + (0x4F6B - 0x4000) to CRYSTAL_SWITCH_CODE,
)
val GEN2_BOX_CALLS_GOLD_FR = goldSilverBoxCalls(0x5B45, 0x6040, "3e01cdf330fa08d0a7", "cdf566cd06673e2521587c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5670)
val GEN2_BOX_CALLS_SILVER_FR = goldSilverBoxCalls(0x5B43, 0x603E, "3e01cdf330fa08d0a7", "cdf566cd06673e2521587c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5670)
val GEN2_BOX_CALLS_GOLD_IT = goldSilverBoxCalls(0x5B46, 0x6041, "3e01cd0631fa08d0a7", "cdf566cd06673e2521567c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5679)
val GEN2_BOX_CALLS_SILVER_IT = goldSilverBoxCalls(0x5B42, 0x603D, "3e01cd0631fa08d0a7", "cdf566cd06673e2521567c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5679)
val GEN2_BOX_CALLS_GOLD_ES = goldSilverBoxCalls(0x5B44, 0x6040, "3e01cd0531fa08d0a7", "cdf566cd06673e2521507c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5679)
val GEN2_BOX_CALLS_SILVER_ES = goldSilverBoxCalls(0x5B42, 0x603E, "3e01cd0531fa08d0a7", "cdf566cd06673e2521507c", 0x4E05, 0x507A, 0x4C20, GOLD_SILVER_DE_SAVE_CODE, 0x5679)
