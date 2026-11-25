package com.pokedaisy.app.companion.data

import kotlinx.atomicfu.locks.synchronized

/**
 * Red, Blue and Yellow's PC boxes (English and German), pret/pokered's and pokeyellow's sram.asm / save.asm:
 * - the box the PC is on is WRAM's wBoxDataStart: deposits and withdrawals change it there, and the game copies it into
 *   the save (sCurBoxData) only when it's saved. So unlike Crystal's sBox it's read from RAM, not the cart;
 * - boxes 1-6 are SRAM bank 2's, 7-12 bank 3's, 0x462 bytes apart from A000. A box change (ChangeBox) copies the current
 *   box into its place there, the new one into wBoxDataStart and saves the game: the current box's own place is stale.
 * - wCurrentBoxNum: the box in bits 0-6, bit 7 (BIT_HAS_CHANGED_BOXES) set once the boxes were changed the first time,
 *   when EmptyAllSRAMBoxes set them all up. Until then banks 2 and 3 hold whatever the cart had: they're shown empty.
 * A box is `box` (macros/ram.asm): a count, 20 species + 0xFF, 20 box_structs (the party_struct's first 33 bytes, HP and
 * status kept: Gen 1's PC doesn't heal), 20 OT names and 20 nicknames of 11 bytes. Boxes have no names: BOX 1 .. BOX 12.
 */
data class Gen1BoxLayout(
    val curBox: Long,      // wCurrentBoxNum
    val activeBox: Long,   // wBoxDataStart
    /** NUM_BOXES, MONS_PER_BOX, NAME_LENGTH and how many boxes an SRAM bank holds: the Japanese carts' 8 of 30, 6-byte names, 4 a bank. */
    val numBoxes: Int = GEN1_NUM_BOXES,
    val slots: Int = GEN1_BOX_SLOTS,
    val nameLength: Int = 11,
    val perBank: Int = GEN1_BOXES_PER_BANK,
) {
    /** A box's bytes: the count, the species list + 0xFF, the structs, the OT names, the nicknames. */
    val boxSize get() = 2 + slots + slots * (GEN1_BOX_MON_SIZE + 2 * nameLength)
    val monsOff get() = 2 + slots
    val nicknamesOff get() = monsOff + slots * (GEN1_BOX_MON_SIZE + nameLength)
}

/** English Red / Blue: pokered.sym. */
val GEN1_RED_BOXES = Gen1BoxLayout(curBox = 0xD5A0L, activeBox = 0xDA80L)
/** English Yellow: pokeyellow.sym, Red's - 1. */
val GEN1_YELLOW_BOXES = Gen1BoxLayout(curBox = 0xD59FL, activeBox = 0xDA7FL)
/** German Rote / Blaue Edition: Red's + 5 (ChangeBox's own operands). */
val GEN1_RED_DE_BOXES = Gen1BoxLayout(curBox = 0xD5A5L, activeBox = 0xDA85L)
/** German Gelbe Edition: Yellow's + 5. */
val GEN1_YELLOW_DE_BOXES = Gen1BoxLayout(curBox = 0xD5A4L, activeBox = 0xDA84L)
/**
 * Japanese Green in LandaR's translation (pret/pokegreen's pokegreen.sym, sram.asm): 8 boxes of 30, 6-byte names, 0x566
 * bytes each; boxes 1-4 in bank 2, 5-8 in bank 3.
 */
val GEN1_GREEN_BOXES = Gen1BoxLayout(curBox = 0xD51FL, activeBox = 0xD9B2L, numBoxes = 8, slots = 30, nameLength = 6, perBank = 4)

/** The running cart's boxes, or null (the other languages and the hacks aren't mapped). */
fun gen1BoxLayoutFor(cfg: Gen1Config): Gen1BoxLayout? = when {
    cfg === GEN1_YELLOW -> GEN1_YELLOW_BOXES
    cfg === GEN1_YELLOW_DE -> GEN1_YELLOW_DE_BOXES
    cfg === GEN1_RED || cfg === GEN1_BLUE -> GEN1_RED_BOXES
    cfg === GEN1_RED_DE || cfg === GEN1_BLUE_DE -> GEN1_RED_DE_BOXES
    cfg === GEN1_GREEN_LANDAR -> GEN1_GREEN_BOXES
    else -> null
}

internal const val GEN1_NUM_BOXES = 12
internal const val GEN1_BOX_SLOTS = 20
internal const val GEN1_BOX_SIZE = 0x462
internal const val GEN1_BOX_MON_SIZE = 33
internal const val GEN1_BOX_MONS = 0x16
internal const val GEN1_BOXES_PER_BANK = 6
internal const val GEN1_BOX_CHANGED = 0x80

/** The boxes as [readGen1Boxes] last decoded them, and the bytes they came from: the same bytes, the same object. */
private var lastGen1Raw: ByteArray? = null
private var lastGen1Boxes: PcBoxes? = null

fun readGen1Boxes(r: MemoryReader, cfg: Gen1Config, layout: Gen1BoxLayout): PcBoxes? = runCatching { readGen1BoxesRaw(r, cfg, layout) }.getOrNull()
private fun readGen1BoxesRaw(r: MemoryReader, cfg: Gen1Config, layout: Gen1BoxLayout): PcBoxes? = run {
    val num = r.readCoreMemory(layout.curBox, 1)[0].toInt() and 0xFF
    val current = (num and GEN1_BOX_CHANGED.inv()).takeIf { it < layout.numBoxes } ?: return null
    val changed = num and GEN1_BOX_CHANGED != 0
    val size = layout.boxSize
    val raw = ByteArray(layout.numBoxes * size)
    for (b in 0 until layout.numBoxes) {
        val at = when {
            b == current -> layout.activeBox
            // Never set up: an empty box (count 0), as EmptyAllSRAMBoxes will make it.
            !changed -> continue
            else -> cartRam(2 + b / layout.perBank, 0xA000 + (b % layout.perBank) * size)
        }
        r.readCoreMemory(at, size).copyInto(raw, b * size)
    }
    val key = byteArrayOf(num.toByte()) + raw
    synchronized(pcBoxesLock) { lastGen1Boxes.takeIf { key.contentEquals(lastGen1Raw) } }?.let { return it }
    val charset = cfg.charset ?: gen1Charset(cfg.language)
    PcBoxes(
        currentBox = current,
        boxes = List(layout.numBoxes) { b ->
            val o = b * size
            val count = (raw[o].toInt() and 0xFF).takeIf { it <= layout.slots } ?: 0
            PcBox(
                name = "BOX${b + 1}",
                wallpaper = -1,
                mons = List(layout.slots) { s ->
                    if (s >= count) null else decodeGen1BoxMon(raw, o + layout.monsOff + s * GEN1_BOX_MON_SIZE, cfg)
                        ?.copy(nickname = gen1Nickname(raw, o + layout.nicknamesOff + s * layout.nameLength, charset, layout.nameLength))
                },
            )
        },
    ).also { b ->
        // Kept for the next sample only once every stat could be worked out: the cart (the base stats) can come in later.
        if (b.boxes.all { box -> box.mons.all { it == null || it.stats != null } }) {
            synchronized(pcBoxesLock) { lastGen1Raw = key; lastGen1Boxes = b }
        }
    }
}

/**
 * A box_struct at [off]: [decodeGen1PartyMon]'s fields, its level the struct's own (BoxLevel), its stats worked out
 * ([gen1BoxStats]) and its HP as it was stored.
 */
fun decodeGen1BoxMon(raw: ByteArray, off: Int, cfg: Gen1Config): Mon? {
    // The party decoder reads past the box struct only for the level and the stats: a party struct with the level put in.
    val party = ByteArray(GEN1_PARTY_MON_SIZE).also {
        raw.copyInto(it, 0, off, off + GEN1_BOX_MON_SIZE)
        it[33] = raw[off + 3]
    }
    val mon = decodeGen1PartyMon(party, 0, cfg) ?: return null
    val dvs = mon.stats?.ivs ?: return mon.copy(stats = null)
    val stats = gen1BoxStats(cfg, mon.species, mon.level, dvs, mon.stats.evs) ?: return mon.copy(stats = null)
    return mon.copy(hp = mon.hp.coerceAtMost(stats[STAT_HP]), maxHp = stats[STAT_HP], stats = mon.stats.copy(stats = stats))
}

/**
 * CalcStats with stat experience, Gen 1's way: ((base + DV) * 2 + ceil(sqrt(stat exp)) / 4) * level / 100 + 5, HP +
 * level + 10 instead of 5, at most 999 (the square root stops at 255); in [MonStats.stats]' order (HP, Attack, Defense,
 * Speed, Special twice). Base stats from the cart (the DEX's entry: HP, ATTACK, DEFENSE, SPEED, SPECIAL); null while it
 * isn't loaded.
 */
internal fun gen1BoxStats(cfg: Gen1Config, species: Int, level: Int, dvs: List<Int>, statExp: List<Int>): List<Int>? {
    val tables = cfg.pokedex ?: return null
    val base = PokedexSource.entry(tables, species)?.baseStats?.takeIf { it.size == 5 } ?: return null
    val five = List(5) { i ->
        val exp = kotlin.math.ceil(kotlin.math.sqrt(statExp[i].toDouble())).toInt().coerceAtMost(255) / 4
        val core = ((base[i] + dvs[i]) * 2 + exp) * level / 100
        (if (i == STAT_HP) core + level + 10 else core + 5).coerceAtMost(999)
    }
    return five + five[4]
}
