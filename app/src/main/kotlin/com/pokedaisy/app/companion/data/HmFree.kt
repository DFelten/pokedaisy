package com.pokedaisy.app.companion.data

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.Path

/**
 * NO HMS NEEDED (SETTINGS > GAMEPLAY): the field moves work without a Pokémon that knows them, once the game's badge
 * for them is in - the most loved change of the ROM hacks (no "HM slave" in the party). The game's own way stays: press
 * A at a tree, a rock, a boulder, the water, a waterfall or a dive spot, the game asks, the first Pokémon that isn't an
 * egg does the cut-in. One that knows the move still goes first.
 *
 * Every field move script runs `checkpartymove MOVE_X` after its badge check, and gives up when it answers PARTY_SIZE.
 * Its command, ScrCmd_checkpartymove, jumps to a replacement in the ROM's padding ([CHECK_CODE], scripts/hm_free.s): nobody
 * knowing the move, and the HM for it in the bag (CheckBagHasItem, found through gScriptCmdTable's checkitem), it answers
 * the first Pokémon that isn't an egg. Without the HM the game's rule stays: the HM has to be found first. Surf has one more check before its script: the A
 * press on water asks PartyHasMonWithSurf (field_control_avatar.c), whose `beq` past "this one knows it" turns into a
 * `nop` ([SURF_NOP]), so any Pokémon will do there (the script's `checkpartymove` after it still wants the HM). Emerald's secret bases also ask `checkpartymove`, for a TM
 * (MOVE_SECRET_POWER): that one keeps the game's rule. Nothing else in the games asks it.
 *
 * Found in the ROM by agbcc's build of both functions ([find]), all or nothing: retail FireRed / LeafGreen / Emerald /
 * Ruby / Sapphire in every language and revision, and the hacks on that code. The two must call the same GetMonData and
 * MonKnowsMove and read the same gPlayerParty, and gSpecialVar_Result must sit 0x10 past gSpecialVar_0x8004 (the
 * rewrite reaches both through one word), and a 4 KB run of 0xFF must be free for the replacement. The bag check needs
 * vanilla's HMs (their moves at the end of the TM / HM table, items 339-346); a ROM without them gets the replacement
 * without it ([NO_BAG_CHECK]): the badge alone for the moves in its HMs' list. gcc builds (expansion, Recharged) have other shapes: N/A.
 *
 * FLY, FREE FLIGHT and FLASH from the companion ask for the move themselves (Fly.kt, Flash.kt): while this is on they
 * take the same Pokémon when the HM is in the bag ([lets]).
 *
 * Patched through [com.pokedaisy.app.MgbaCore.pkRomPatch] in 4-byte pieces with REUSABLE TMS and INSTANT TEXT, the
 * file's bytes kept for the Poller's hash and RetroAchievements' (pk_cheats.c). Played headless with `rompatch` on
 * FireRed rev 1 (Cut with nobody knowing it, Surf with and without Lapras knowing it) and Emerald (Rock Smash).
 */
object HmFree {
    /** [checkBagHasItem]: the GBA ROM's CheckBagHasItem the replacement asks (0 on a Game Boy cart). */
    class Patch(val writes: List<InstantText.Write>, val checkBagHasItem: Long = 0)

    /** The option is on and the running ROM took the patch: FLY / FREE FLIGHT / FLASH don't ask for the move either. Set
     * by the activity (any thread reads it). */
    @Volatile var active = false

    /** The running GBA ROM's CheckBagHasItem ([Patch.checkBagHasItem]), set with [active]; 0 = the patch has no bag check. */
    @Volatile var bagCheck = 0L

    /** ITEM_HM01 ... HM08 in every game this goes on (they're checked: the HMs' moves end the TM / HM table). */
    const val ITEM_HM02_FLY = 340
    const val ITEM_HM03_SURF = 341
    const val ITEM_HM05_FLASH = 343

    /** NO HMS NEEDED lets anyone use the move [hmItem] teaches: it's on and the HM is in the bag, like the patched command
     * asks. Emu thread (a game call). */
    fun lets(hmItem: Int, g: GameCalls): Boolean =
        active && (bagCheck == 0L || g.call(bagCheck, hmItem, 1) != 0L)

    private const val ROM_BASE = 0x08000000L
    private const val ANY = -1

    /** ScrCmd_checkpartymove as agbcc built it, 124 bytes with its literals: calls and literals left out. */
    private val CHECK = sig(
        "f0b5 ????????0004070c0249062008800026 1ce0 ????????" +
            "201c2d21 ???????? 002811d1201c391c ???????? 0006000e012809d1024806800248058017e00000 ????????????????" +
            "701c0006060e052e0dd86420311c4143 0748 0c18201c0b210022 ???????? 0004050c002dd4d10020f0bc02bc08470000 ????????",
    )
    private const val C_READ_HALFWORD = 0x02
    private const val C_GET_EGG = 0x1C
    private const val C_KNOWS = 0x28
    private const val C_GET_SPECIES = 0x62
    private const val C_RESULT = 0x14
    private const val C_RESULT2 = 0x40
    private const val C_VAR_8004 = 0x44
    private const val C_PARTY = 0x78

    /** PartyHasMonWithSurf, 0x50 bytes with its literal. */
    private val SURF = sig(
        "30b50820 ???????? 000600281ad100250be0201c3921 ???????? 0006002801d0012010e0681c0006050e052d0ad8" +
            "6420291c4143 0548 0c18201c0b21 ???????? 0028e6d1002030bc02bc0847 ????????",
    )
    private const val S_KNOWS = 0x16
    private const val S_GET_SPECIES = 0x3C
    private const val S_PARTY = 0x4C

    /** Where its `beq` (01 D0: past `return TRUE` when the Pokémon doesn't know Surf) sits, and the `nop` (mov r8, r8). */
    private const val S_BEQ = 0x1E
    private val SURF_NOP = byteArrayOf(0xC0.toByte(), 0x46)

    /**
     * scripts/hm_free.s assembled: ScrCmd_checkpartymove's replacement, put in the ROM's padding ([PAD_SLACK] bytes
     * before the end of its longest run of 0xFF) and reached by the original's first 8 bytes ([JUMP]). Position-free;
     * its words are filled per ROM: gPlayerParty, gSpecialVar_0x8004, ScriptReadHalfword, GetMonData, MonKnowsMove,
     * CheckBagHasItem (the calls with their Thumb bit). ITEM_HM01 (339) and the eight HMs' moves end it.
     */
    private val CHECK_CODE = bytes(
        0xF0, 0xB5, 0x29, 0x4B, 0x00, 0xF0, 0x4B, 0xF8, 0x00, 0x04, 0x07, 0x0C, 0x06, 0x25, 0x00, 0x26, 0x23, 0x4C, 0x05, 0x2E,
        0x1A, 0xD8, 0x20, 0x1C, 0x0B, 0x21, 0x24, 0x4B, 0x00, 0xF0, 0x3F, 0xF8, 0x00, 0x28, 0x13, 0xD0, 0x20, 0x1C, 0x2D, 0x21,
        0x20, 0x4B, 0x00, 0xF0, 0x38, 0xF8, 0x00, 0x28, 0x09, 0xD1, 0x06, 0x2D, 0x00, 0xD1, 0x35, 0x1C, 0x20, 0x1C, 0x39, 0x1C,
        0x1C, 0x4B, 0x00, 0xF0, 0x2E, 0xF8, 0x00, 0x28, 0x18, 0xD1, 0x76, 0x1C, 0x64, 0x34, 0xE2, 0xE7, 0x06, 0x2D, 0x14, 0xD0,
        0x1A, 0xA2, 0x00, 0x20, 0x11, 0x5A, 0xB9, 0x42, 0x03, 0xD0, 0x02, 0x30, 0x10, 0x28, 0xF9, 0xD3, 0x08, 0xE0, 0x40, 0x08,
        0x14, 0x49, 0x40, 0x18, 0x01, 0x21, 0x12, 0x4B, 0x00, 0xF0, 0x17, 0xF8, 0x00, 0x28, 0x02, 0xD1, 0x06, 0x25, 0x00, 0xE0,
        0x35, 0x1C, 0x0A, 0x49, 0x0D, 0x82, 0x06, 0x2D, 0x09, 0xD0, 0x64, 0x20, 0x68, 0x43, 0x06, 0x49, 0x40, 0x18, 0x0B, 0x21,
        0x07, 0x4B, 0x00, 0xF0, 0x06, 0xF8, 0x04, 0x49, 0x08, 0x80, 0x00, 0x20, 0xF0, 0xBC, 0x02, 0xBC, 0x08, 0x47, 0x18, 0x47,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x53, 0x01, 0x00, 0x00, 0x0F, 0x00, 0x13, 0x00, 0x39, 0x00, 0x46, 0x00, 0x94, 0x00, 0xF9, 0x00,
        0x7F, 0x00, 0x23, 0x01,
    )
    private const val N_PARTY = 0xA0
    private const val N_VAR_8004 = 0xA4
    private const val N_READ_HALFWORD = 0xA8
    private const val N_GET_MON_DATA = 0xAC
    private const val N_KNOWS = 0xB0
    private const val N_HAS_ITEM = 0xB4

    /** `have:`'s first instruction (an HM found: ask the bag) made `b set`: the replacement without the bag check, the
     * HMs' list still deciding which moves anyone may do. */
    private const val N_SKIP_BAG = 0x62
    private val NO_BAG_CHECK = bytes(0x0A, 0xE0)

    /** `ldr r3, [pc, #0]; bx r3`, then the word: the original's first 8 bytes ([CHECK]'s start is 4-aligned). */
    private val JUMP = bytes(0x00, 0x4B, 0x18, 0x47)

    /** The HMs' moves in the ROM's TM / HM move table (sTMHMMoves' last eight, u16): the HM items are vanilla's 339-346. */
    private val HM_MOVES = sig("0f00 1300 3900 4600 9400 f900 7f00 2301")

    /** gScriptCmdTable: checkpartymove's opcode and checkitem's (the same in every Gen 3 game). */
    private const val OP_CHECKPARTYMOVE = 0x7C
    private const val OP_CHECKITEM = 0x47

    /** The padding the replacement goes into: a run of 0xFF at least [PAD_MIN] long; it sits [PAD_SLACK] bytes before its end. */
    private const val PAD_MIN = 0x1000
    private const val PAD_SLACK = 0x100

    /** What [find] found: both functions' places and what the rewrite calls / reads. */
    class Found(
        val check: Long,
        val surf: Long,
        val getMonData: Long,
        val monKnowsMove: Long,
        val party: Long,
        val var8004: Long,
        /** CheckBagHasItem (checkitem's call), which the replacement asks for the HM; 0 = no bag check (the HMs aren't
         * vanilla's, or checkitem isn't found): the badge alone. */
        val checkBagHasItem: Long,
        /** Where the replacement goes. */
        val at: Long,
        val patch: Patch,
    )

    private val cache = HashMap<String, Found?>()

    private val lock = SynchronizedObject()

    /** The patch for [rom], or null. Reads the file (a window at a time, ~150 ms for 32 MB): off the UI thread; cached per file. */
    fun find(rom: Path): Patch? = locate(rom)?.patch

    fun locate(rom: Path): Found? {
        val key = fileKey(rom)
        synchronized(lock) { if (key in cache) return cache[key] }
        val found = runCatching {
            withRomFile(rom) { locate(FileLittleEndian(it)) }
        }.getOrNull()
        synchronized(lock) { cache[key] = found }
        return found
    }

    fun locate(rom: LittleEndianReader): Found? {
        val checks = matches(rom, CHECK)
        val surfs = matches(rom, SURF)
        if (checks.size != 1 || surfs.size != 1) return null
        val c = checks[0]
        val s = surfs[0]
        if (c % 4 != 0) return null   // CHECK_CODE's `ldr`s count on it
        val readHalfword = blTarget(rom, c + C_READ_HALFWORD)
        val getMonData = blTarget(rom, c + C_GET_EGG)
        val knows = blTarget(rom, c + C_KNOWS)
        if (readHalfword == null || getMonData == null || knows == null) return null
        if (blTarget(rom, c + C_GET_SPECIES) != getMonData) return null
        if (blTarget(rom, s + S_KNOWS) != knows || blTarget(rom, s + S_GET_SPECIES) != getMonData) return null
        val result = word(rom, c + C_RESULT)
        val var8004 = word(rom, c + C_VAR_8004)
        val party = word(rom, c + C_PARTY)
        if (word(rom, c + C_RESULT2) != result || result != var8004 + 0x10 || word(rom, s + S_PARTY) != party) return null

        // Without vanilla's HMs (or a checkitem to ask), it goes on without the bag check: the badge alone, as before.
        val hasItem = if (matches(rom, HM_MOVES).isEmpty()) null else checkBagHasItem(rom, c, readHalfword)
        val at = padding(rom, CHECK_CODE.size) ?: return null

        fun thumb(off: Int) = ROM_BASE + off + 1
        val code = CHECK_CODE.copyOf()
        le32(party).copyInto(code, N_PARTY)
        le32(var8004).copyInto(code, N_VAR_8004)
        le32(thumb(readHalfword)).copyInto(code, N_READ_HALFWORD)
        le32(thumb(getMonData)).copyInto(code, N_GET_MON_DATA)
        le32(thumb(knows)).copyInto(code, N_KNOWS)
        if (hasItem != null) le32(thumb(hasItem)).copyInto(code, N_HAS_ITEM) else NO_BAG_CHECK.copyInto(code, N_SKIP_BAG)
        fun write(off: Int, value: ByteArray) =
            InstantText.Write(ROM_BASE + off, ByteArray(value.size) { rom.get(off + it) }, value)
        // The replacement first, 4 bytes at a time into the padding, then the jump to it, then Surf's `nop`.
        val writes = (code.indices step 4).map { i -> write(at + i, code.copyOfRange(i, i + 4)) } +
            write(c, JUMP) + write(c + 4, le32(thumb(at))) +
            write(s + S_BEQ, SURF_NOP)
        return Found(
            ROM_BASE + c, ROM_BASE + s, ROM_BASE + getMonData, ROM_BASE + knows, party, var8004,
            hasItem?.let { ROM_BASE + it } ?: 0L, ROM_BASE + at, Patch(writes, hasItem?.let { ROM_BASE + it } ?: 0L),
        )
    }

    /**
     * CheckBagHasItem, through gScriptCmdTable: the table's checkpartymove entry is the one word in the ROM pointing at
     * [check] (Thumb), its checkitem entry ([OP_CHECKITEM]) points at ScrCmd_checkitem, whose calls are
     * ScriptReadHalfword, VarGet, ScriptReadHalfword, VarGet, CheckBagHasItem. Null for anything else.
     */
    private fun checkBagHasItem(rom: LittleEndianReader, check: Int, readHalfword: Int): Int? {
        val want = (ROM_BASE + check + 1).toInt()
        var entry = -1
        var o = 0
        while (o + 4 <= rom.limit()) {
            if (rom.getInt(o) == want) { if (entry >= 0) return null; entry = o }
            o += 4
        }
        if (entry < 0) return null
        val itemEntry = entry - (OP_CHECKPARTYMOVE - OP_CHECKITEM) * 4
        if (itemEntry < 0) return null
        val fn = word(rom, itemEntry) - ROM_BASE - 1
        if (fn !in 0L until rom.limit().toLong() || fn % 2 != 0L) return null
        val calls = ArrayList<Int>()
        var i = fn.toInt()
        while (i < fn + 0x40 && calls.size < 5 && i + 4 <= rom.limit()) {
            val t = blTarget(rom, i)
            if (t != null) { calls += t; i += 4 } else i += 2
        }
        if (calls.size != 5 || calls[0] != readHalfword || calls[2] != readHalfword || calls[1] != calls[3]) return null
        return calls[4]
    }

    /** A 4-aligned place for [size] bytes, [PAD_SLACK] before the end of the ROM's longest run of 0xFF, or null. */
    private fun padding(rom: LittleEndianReader, size: Int): Int? {
        var bestStart = 0
        var bestLen = 0
        var start = -1
        val ff = 0xFF.toByte()
        for (i in 0..rom.limit()) {
            val inRun = i < rom.limit() && rom.get(i) == ff
            if (inRun && start < 0) start = i
            if (!inRun && start >= 0) {
                if (i - start > bestLen) { bestLen = i - start; bestStart = start }
                start = -1
            }
        }
        if (bestLen < PAD_MIN) return null
        return (bestStart + bestLen - PAD_SLACK - size) and 3.inv()
    }

    /** The places [sig] matches in [rom], on 2-byte steps. */
    private fun matches(rom: LittleEndianReader, sig: IntArray): List<Int> {
        val out = ArrayList<Int>()
        val b0 = sig[0].toByte()
        val b1 = sig[1].toByte()
        val end = rom.limit() - sig.size
        var o = 0
        while (o <= end) {
            if (rom.get(o) == b0 && rom.get(o + 1) == b1 &&
                sig.indices.all { i -> sig[i] == ANY || (rom.get(o + i).toInt() and 0xFF) == sig[i] }
            ) out += o
            o += 2
        }
        return out
    }

    /** Hex with `??` for a byte that may be anything (spaces ignored). */
    private fun sig(hex: String): IntArray {
        val h = hex.replace(" ", "")
        return IntArray(h.length / 2) { i ->
            val p = h.substring(i * 2, i * 2 + 2)
            if (p == "??") ANY else p.toInt(16)
        }
    }

    private fun word(rom: LittleEndianReader, at: Int): Long = rom.getInt(at).toLong() and 0xFFFFFFFFL

    private fun le32(v: Long) = ByteArray(4) { (v shr (8 * it)).toByte() }

    /** Where the `bl` at [at] goes (a ROM offset), or null if it isn't one. */
    private fun blTarget(rom: LittleEndianReader, at: Int): Int? {
        val h1 = rom.getShort(at).toInt() and 0xFFFF
        val h2 = rom.getShort(at + 2).toInt() and 0xFFFF
        if (h1 and 0xF800 != 0xF000 || h2 and 0xF800 != 0xF800) return null
        var hi = h1 and 0x7FF
        if (hi and 0x400 != 0) hi -= 0x800
        return at + 4 + (hi shl 12) + ((h2 and 0x7FF) shl 1)
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
