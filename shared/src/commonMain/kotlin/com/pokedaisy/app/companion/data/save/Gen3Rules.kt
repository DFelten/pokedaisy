package com.pokedaisy.app.companion.data.save

import com.pokedaisy.app.companion.data.Gen3Text
import com.pokedaisy.app.companion.data.gen3ExperienceTables

/**
 * What a Gen 3 save's game knows that its save doesn't hold - names, base stats, abilities, growth rates - and what it
 * allows: the bag's items, the moves a Pokémon may learn. Retail's ([Gen3Rules.retail]) come from the decomps' generated
 * tables; a ROM hack's ([RomGen3Rules]) from its own ROM, so its editor offers only what that hack could hold.
 */
/** [Gen3Rules.family] of the five retail games. */
const val RETAIL = "retail"

interface Gen3Rules : SaveGameData {
    /** The egg's species id: ids 1 until it are Pokémon. */
    val eggSpecies: Int
    /** The last move id the game has. */
    val lastMove: Int
    /** The National Pokédex's size (the dex bits a save keeps). */
    val nationalCount: Int get() = 386
    /** Saves trade only with saves of the same family: "retail" for the five retail games, a hack's own name for a hack. */
    val family: String
    /** The items [pocket] may hold, by id. KEY is never offered. */
    fun bagItems(pocket: BagPocket): List<Int>
    /** The pocket [item] goes in, or null (a key item, or no item of this game). */
    fun pocketOf(item: Int): BagPocket?
    /** Every move [species] could know at [level]. */
    fun learnable(species: Int, level: Int): Set<Int>
    /** The move a TM / HM item teaches, or null. */
    fun tmHmMove(item: Int): Int?
    /** An HM item: handed out once, never given to hold or taken out. */
    fun isHm(item: Int): Boolean
    /** Whether [species] is in the game's regional Pokédex. */
    fun inRegionalDex(species: Int): Boolean

    /** What a Pokémon may be given to hold: bag items but HMs and mail. */
    fun holdable(): List<Int> =
        listOf(BagPocket.ITEMS, BagPocket.MEDICINE, BagPocket.BALLS, BagPocket.TMHM, BagPocket.BERRIES).flatMap { bagItems(it) }
            .filter { !isHm(it) && !isMail(it) }.distinct().sorted()

    /** GetAbilityBySpecies: the second ability when the bit is set and the species has one, else the first. */
    fun ability(species: Int, abilityNum: Int): Int = abilities(species).let { list -> list.getOrElse(abilityNum) { list[0] } }

    /** GetGenderFromSpeciesAndPersonality. */
    fun gender(species: Int, personality: Long): Gen3Gender = when (val ratio = genderRatio(species)) {
        0 -> Gen3Gender.MALE
        254 -> Gen3Gender.FEMALE
        255 -> Gen3Gender.GENDERLESS
        else -> if ((personality and 0xFF) < ratio) Gen3Gender.FEMALE else Gen3Gender.MALE
    }

    companion object {
        /** The five retail games' species data (the same in all of them): what a record reads with when nothing else is said. */
        val RETAIL: Gen3Rules get() = retail(Gen3SaveFormat.EMERALD)

        private val retailRules = HashMap<Gen3SaveFormat, Gen3Rules>()
        private val installed = HashMap<Gen3SaveFormat, Gen3Rules>()

        /** [format]'s rules: retail's tables, or a hack's read from its ROM ([install]). */
        fun of(format: Gen3SaveFormat): Gen3Rules = synchronized(installed) {
            installed[format] ?: if (format.retail) retail(format) else error("${format.label}'s rules aren't loaded")
        }

        /** A hack's rules, read from its ROM once: every save of [format] reads and edits with them. */
        fun install(format: Gen3SaveFormat, rules: Gen3Rules) = synchronized(installed) { installed[format] = rules }

        fun isInstalled(format: Gen3SaveFormat): Boolean = format.retail || synchronized(installed) { format in installed }

        fun retail(format: Gen3SaveFormat): Gen3Rules = synchronized(retailRules) {
            retailRules.getOrPut(format) { RetailGen3Rules(format) }
        }
    }
}

/** The retail games' rules: [Gen3Legal]'s and [Gen3Species]' generated tables. */
private class RetailGen3Rules(private val format: Gen3SaveFormat) : Gen3Rules {
    override fun speciesName(species: Int): String? = Gen3GameData.speciesName(species)
    override fun moveName(move: Int): String? = Gen3GameData.moveName(move)
    override fun itemName(item: Int): String? = Gen3GameData.itemName(item)
    override fun abilityName(ability: Int): String = Gen3GameData.abilityName(ability)
    override fun nationalDex(species: Int): Int? = Gen3GameData.nationalDex(species)
    override fun abilities(species: Int): List<Int> = Gen3GameData.abilities(species)
    override fun genderRatio(species: Int): Int = Gen3GameData.genderRatio(species)
    override fun baseStats(species: Int): IntArray = Gen3GameData.baseStats(species)
    override fun expForLevel(species: Int, level: Int): Long = Gen3GameData.expForLevel(species, level)
    override fun basePp(move: Int): Int = Gen3GameData.basePp(move)
    override fun isMail(item: Int): Boolean = Gen3GameData.isMail(item)
    override val unown: Int get() = Gen3GameData.unown
    override val wurmpleLine: IntRange get() = Gen3GameData.wurmpleLine
    override val shedinja: Int get() = Gen3GameData.shedinja
    override fun levelFor(species: Int, exp: Long): Int = Gen3BoxMon.levelFor(species, exp)

    override val eggSpecies: Int get() = Gen3SaveData.MAX_SPECIES
    override val lastMove: Int get() = Gen3Legal.LAST_MOVE
    override val family: String get() = RETAIL
    override fun bagItems(pocket: BagPocket): List<Int> = Gen3Legal.bagItems(format, pocket)
    override fun pocketOf(item: Int): BagPocket? = Gen3Legal.pocketOf(format, item)
    override fun learnable(species: Int, level: Int): Set<Int> = Gen3Legal.learnable(format, species, level)
    override fun tmHmMove(item: Int): Int? = Gen3Legal.tmHmMove(item)
    override fun isHm(item: Int): Boolean = item >= Gen3SaveFormat.FIRST_HM && pocketOf(item) == BagPocket.TMHM
    override fun inRegionalDex(species: Int): Boolean = Gen3Trade.inRegionalDex(species, format)
    override fun holdable(): List<Int> = Gen3Legal.holdable(format)
}

/**
 * Where a vanilla-shaped hack keeps the tables its rules come from: its gSpeciesInfo (28-byte gBaseStats), level-up
 * learnsets (a pointer per species to `level << 9 | move` words ending 0xFFFF), TM / HM compatibility (u64 per species)
 * and moves, evolutions (5 x 8 bytes per species), ability names, species -> National Dex numbers and regional order.
 * The names, gBattleMoves and gItems are found in the ROM by their first entries ([Gen3RomScan]).
 */
data class Gen3RomTables(
    val family: String,
    val speciesInfo: Long,
    val learnsets: Long,
    val tmhmLearnsets: Long,
    val tmhmMoves: Long,
    val evolutions: Long,
    val abilityNames: Long,
    val speciesToNational: Long,
    val regionalOrder: Long,
    val regionalCount: Int,
    /** The egg's species id: ids 1 until it are Pokémon. */
    val eggSpecies: Int,
    /** gItems, when known (else found in the ROM). */
    val items: Long = 0,
    /** gSpeciesNames, gMoveNames, gBattleMoves, when known (else found in the ROM: [Gen3RomScan]). */
    val speciesNames: Long = 0,
    val moveNames: Long = 0,
    val battleMoves: Long = 0,
    /** gItems' record shape: its size, where its pocket number sits, and what each number is. */
    val itemLayout: ItemLayout = ItemLayout.VANILLA,
)

/**
 * A struct Item's size, its pocket byte and the pockets by number: vanilla's 44 bytes with the pocket at 0x1A (1 Items,
 * 2 Poké Balls, 3 TMs, 4 Berries, 5 Key Items), Recharged Emerald's 48 (a u32 price: the pocket at 0x1E, numbered 1 Items,
 * 2 Medicine, 3 Poké Balls, 4 TMs, 5 Berries, 6 Key Items, as its gBagPockets).
 */
class ItemLayout(
    val size: Int, val pocketOff: Int, val pockets: Map<Int, BagPocket>,
    /** The record's own id (vanilla's +0x0E), or -1: no id, the table ends at the first record whose pocket isn't one. */
    val idOff: Int = 0x0E,
    val nameLength: Int = 14,
) {
    companion object {
        val VANILLA = ItemLayout(44, 0x1A, BagPocket.entries.filter { it != BagPocket.MEDICINE }.associateBy { it.id })
        /** Recharged Emerald's: an 18-byte name, a u16 price at +0x12, the description at +0x18, no id (408 records in v2.2.8). */
        val RECHARGED = ItemLayout(
            48, 0x1E, idOff = -1, nameLength = 18, pockets =
            mapOf(1 to BagPocket.ITEMS, 2 to BagPocket.MEDICINE, 3 to BagPocket.BALLS, 4 to BagPocket.TMHM, 5 to BagPocket.BERRIES, 6 to BagPocket.KEY),
        )
    }
}

/** A hack's [Gen3Rules], read once from its ROM [rom] at [t]'s tables. */
class RomGen3Rules(private val rom: ByteArray, private val t: Gen3RomTables) : Gen3Rules {
    private fun off(addr: Long): Int = (addr - 0x08000000L).toInt()
    private fun u8(addr: Long): Int = rom[off(addr)].toInt() and 0xFF
    private fun u16a(addr: Long): Int = u16(rom, off(addr))
    private fun u32a(addr: Long): Long = u32(rom, off(addr))

    private val speciesNames: Long = t.speciesNames.takeIf { it != 0L } ?: Gen3RomScan.speciesNames(rom) ?: error("no species names in the ROM")
    private val moveNames: Long = t.moveNames.takeIf { it != 0L } ?: Gen3RomScan.moveNames(rom) ?: error("no move names in the ROM")
    private val battleMoves: Long = t.battleMoves.takeIf { it != 0L } ?: Gen3RomScan.battleMoves(rom) ?: error("no gBattleMoves in the ROM")
    private val items: Long = t.items.takeIf { it != 0L } ?: Gen3RomScan.items(rom) ?: error("no gItems in the ROM")

    override val eggSpecies: Int get() = t.eggSpecies
    override val family: String get() = t.family

    /** The moves with a name and PP, from Pound on: the last of them. */
    override val lastMove: Int by lazy {
        var last = 0
        for (m in 1 until 1024) {
            val name = Gen3Text.decode(rom, off(moveNames) + m * 13, 13).trim()
            if (name.isEmpty() || name.all { it == '?' }) break
            if (u8(battleMoves + m * 12L + 4) == 0) break
            last = m
        }
        last
    }

    /** gItems' records by id: name, pocket. An unused one keeps ITEM_NONE's id 0 (null here); the table ends at the
     * first record that is neither its own id nor 0 (a layout with no id: at the first whose pocket isn't one). */
    private class Item(val name: String, val pocket: BagPocket?)
    private val itemTable: List<Item?> by lazy {
        val out = ArrayList<Item?>()
        for (i in 0 until 2048) {
            val l = t.itemLayout
            val base = items + i * l.size
            if (off(base) + l.size > rom.size) break
            val pocket = l.pockets[u8(base + l.pocketOff)]
            fun item() = Item(Gen3Text.decode(rom, off(base), l.nameLength).trim(), pocket)
            if (l.idOff < 0) {
                if (pocket == null) break
                out += item()
                continue
            }
            when (u16a(base + l.idOff)) {
                i -> out += item()
                0 -> out += null
                else -> break
            }
        }
        // Trailing unused records aren't items.
        while (out.isNotEmpty() && out.last() == null) out.removeAt(out.size - 1)
        out
    }

    override fun speciesName(species: Int): String? =
        if (species in 1 until t.eggSpecies) Gen3Text.decode(rom, off(speciesNames) + species * 11, 11).trim().ifEmpty { null } else null
    override fun moveName(move: Int): String? =
        if (move in 1..lastMove) Gen3Text.decode(rom, off(moveNames) + move * 13, 13).trim().ifEmpty { null } else null
    override fun itemName(item: Int): String? = itemTable.getOrNull(item)?.name?.takeIf { item > 0 && it.isNotEmpty() }
    override fun abilityName(ability: Int): String = Gen3Text.decode(rom, off(t.abilityNames) + ability * 13, 13).trim()
    override fun nationalDex(species: Int): Int? =
        if (species in 1 until t.eggSpecies) u16a(t.speciesToNational + (species - 1) * 2L).takeIf { it in 1..nationalCount } else null

    private fun info(species: Int): Long = t.speciesInfo + species * BASE_STATS_SIZE.toLong()
    override fun abilities(species: Int): List<Int> {
        if (species !in 1 until t.eggSpecies) return listOf(0)
        val a = u8(info(species) + 22)
        val b = u8(info(species) + 23)
        return if (b == 0 || b == a) listOf(a) else listOf(a, b)
    }
    override fun genderRatio(species: Int): Int = if (species in 1 until t.eggSpecies) u8(info(species) + 16) else 255
    override fun baseStats(species: Int): IntArray =
        if (species in 1 until t.eggSpecies) IntArray(6) { u8(info(species) + it) } else IntArray(6)
    override fun expForLevel(species: Int, level: Int): Long {
        if (species !in 1 until t.eggSpecies) return 0
        val rate = u8(info(species) + 19)
        return gen3ExperienceTables.getOrNull(rate)?.getOrNull(level)?.toLong() ?: 0
    }
    override fun basePp(move: Int): Int = if (move in 1..lastMove) u8(battleMoves + move * 12L + 4) else 0
    override fun isMail(item: Int): Boolean = Gen3Legal.isMail(item)
    override val unown: Int get() = 201
    override val wurmpleLine: IntRange get() = 265..269
    override val shedinja: Int get() = 303

    private val tmMoves: IntArray by lazy { IntArray(TMHM_COUNT) { u16a(t.tmhmMoves + it * 2L) } }

    /** TM01..HM08 are items 289..346 in every vanilla-shaped Gen 3 game. */
    override fun tmHmMove(item: Int): Int? = tmMoves.getOrNull(item - FIRST_TM)?.takeIf { it in 1..lastMove }
    override fun isHm(item: Int): Boolean = item in Gen3SaveFormat.FIRST_HM until FIRST_TM + TMHM_COUNT

    override fun bagItems(pocket: BagPocket): List<Int> = itemTable.indices.filter { id ->
        id > 0 && id != ITEM_ENIGMA_BERRY && itemTable[id]?.pocket == pocket && pocket != BagPocket.KEY &&
            itemTable[id]?.name?.let { it.isNotEmpty() && !it.all { c -> c == '?' } } == true
    }
    override fun pocketOf(item: Int): BagPocket? =
        itemTable.getOrNull(item)?.pocket?.takeIf { item > 0 && it != BagPocket.KEY }

    /** Each species' pre-evolution, from the evolution table. */
    private val preEvolution: IntArray by lazy {
        val pre = IntArray(t.eggSpecies + 1)
        for (s in 1 until t.eggSpecies) for (e in 0 until EVOS_PER_MON) {
            val base = t.evolutions + (s * EVOS_PER_MON + e) * 8L
            if (u16a(base) == 0) continue
            val target = u16a(base + 4)
            if (target in 1 until t.eggSpecies && pre[target] == 0) pre[target] = s
        }
        pre
    }

    override fun learnable(species: Int, level: Int): Set<Int> {
        if (species == SPECIES_SMEARGLE) return (1..lastMove).filter { it != MOVE_STRUGGLE }.toSet()
        val out = HashSet<Int>()
        var s = species
        var guard = 0
        while (s in 1 until t.eggSpecies && guard++ < 4) {
            var p = u32a(t.learnsets + s * 4L)
            var n = 0
            while (p in 0x08000000L until 0x08000000L + rom.size && n++ < 64) {
                val w = u16a(p)
                if (w == 0xFFFF) break
                if (w shr 9 <= level) (w and 0x1FF).takeIf { it in 1..lastMove }?.let { out += it }
                p += 2
            }
            val bits = u32a(t.tmhmLearnsets + s * 8L) or (u32a(t.tmhmLearnsets + s * 8L + 4) shl 32)
            for (b in 0 until TMHM_COUNT) if ((bits shr b) and 1L == 1L) tmMoves[b].takeIf { it in 1..lastMove }?.let { out += it }
            s = preEvolution[s]
        }
        return out
    }

    private val regional: Set<Int> by lazy {
        if (t.regionalOrder == 0L) (1..151).toSet()
        else (0 until t.regionalCount).map { u16a(t.regionalOrder + it * 2L) }.toSet()
    }
    override fun inRegionalDex(species: Int): Boolean = nationalDex(species)?.let { it in regional } == true

    companion object {
        private const val BASE_STATS_SIZE = 28
        private const val FIRST_TM = 289
        private const val TMHM_COUNT = 58
        private const val EVOS_PER_MON = 5
        private const val ITEM_ENIGMA_BERRY = 175
        private const val SPECIES_SMEARGLE = 235
        private const val MOVE_STRUGGLE = 165
    }
}

/**
 * The vanilla-shaped tables a hack keeps but moved, found by their first entries: gSpeciesNames ("??????????",
 * BULBASAUR, IVYSAUR), gMoveNames ("-", POUND, KARATE CHOP), gBattleMoves (an empty move 0, then Pound's 12 bytes) and
 * gItems (an empty item 0, then MASTER BALL with its own id). Each one the first match on a word boundary.
 */
object Gen3RomScan {
    private fun enc(s: String): ByteArray = ByteArray(s.length) { i ->
        val c = s[i]
        when (c) {
            in 'A'..'Z' -> (0xBB + (c - 'A')).toByte()
            in 'a'..'z' -> (0xD5 + (c - 'a')).toByte()
            ' ' -> 0
            else -> error("no byte for $c")
        }
    }

    private fun find(rom: ByteArray, pattern: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..rom.size - pattern.size) {
            for (j in pattern.indices) if (rom[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    /** Every place [pattern] starts. */
    private fun all(rom: ByteArray, pattern: ByteArray): Sequence<Int> = generateSequence(find(rom, pattern)) { find(rom, pattern, it + 1).takeIf { n -> n >= 0 } }
        .takeWhile { it >= 0 }

    private fun named(rom: ByteArray, first: String, second: String, stride: Int): Long? {
        val cases: List<(String) -> String> = listOf(
            { s -> s.uppercase() },
            { s -> s.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } } },
        )
        for (case in cases) {
            val a = enc(case(first)) + byteArrayOf(0xFF.toByte())
            val b = enc(case(second))
            for (i in all(rom, a)) {
                if (i - stride < 0 || i + stride + b.size > rom.size) continue
                if ((i + stride until i + stride + b.size).all { rom[it] == b[it - i - stride] }) return 0x08000000L + i - stride
            }
        }
        return null
    }

    fun speciesNames(rom: ByteArray): Long? = named(rom, "BULBASAUR", "IVYSAUR", 11)
    fun moveNames(rom: ByteArray): Long? = named(rom, "POUND", "KARATE CHOP", 13)

    fun battleMoves(rom: ByteArray): Long? {
        val pattern = ByteArray(12) + byteArrayOf(0, 40, 0, 100, 35)
        return all(rom, pattern).firstOrNull { it % 4 == 0 }?.let { 0x08000000L + it }
    }

    fun items(rom: ByteArray): Long? {
        for (name in listOf("MASTER BALL", "Master Ball")) {
            val a = enc(name) + byteArrayOf(0xFF.toByte())
            for (i in all(rom, a)) {
                if (i % 4 != 0 || i < 44 || u16(rom, i + 14) != 1 || u16(rom, i - 44 + 14) != 0) continue
                return 0x08000000L + i - 44
            }
        }
        return null
    }
}

/**
 * A vanilla-shaped hack's [Gen3RomTables] from the companion's config for it ([com.pokedaisy.app.companion.data.NativeConfig]):
 * its Pokédex and GUIDE tables name every table the editor's rules read; gItems is its item descriptions' table.
 * Null when the config lacks one.
 */
fun gen3RomTables(
    cfg: com.pokedaisy.app.companion.data.NativeConfig, family: String, inPlace: InPlaceBase? = null,
    itemLayout: ItemLayout = ItemLayout.VANILLA,
): Gen3RomTables? {
    val dex = cfg.pokedex ?: return null
    val guide = cfg.guideTables ?: return null
    // The evolution table: vanilla's, or a layout's of the same shape (5 records of 8 bytes, the target at +4) whose
    // methods are numbered its own way (Recharged Emerald's) - only the targets are read here.
    val evolutions = dex.evolutions.takeIf { it != 0L }
        ?: dex.evoLayout?.takeIf { it.perMon == 5 && it.record == 8 && it.table != 0L }?.table ?: 0L
    if (dex.expansion != null || dex.tmhmLearnsets == 0L || dex.tmhmMoves == 0L || evolutions == 0L || guide.learnsetPairs) return null
    return Gen3RomTables(
        family = family, speciesInfo = dex.speciesInfo, learnsets = guide.learnsets, tmhmLearnsets = dex.tmhmLearnsets,
        tmhmMoves = dex.tmhmMoves, evolutions = evolutions, abilityNames = dex.abilityNames,
        speciesToNational = dex.speciesToNational, regionalOrder = dex.regionalOrder, regionalCount = dex.regionalCount,
        eggSpecies = cfg.eggSpecies.takeIf { it > 0 } ?: dex.speciesCount, items = inPlace?.items ?: cfg.itemDescs?.base ?: 0,
        speciesNames = inPlace?.speciesNames ?: 0, moveNames = inPlace?.moveNames ?: 0, battleMoves = inPlace?.battleMoves ?: 0,
        itemLayout = itemLayout,
    )
}

/**
 * A retail cart's gSpeciesNames, gMoveNames, gBattleMoves and gItems: where a hack edited in place keeps its own (Glazed
 * renamed and rebalanced in those very tables, so no first entry can find them).
 */
enum class InPlaceBase(val speciesNames: Long, val moveNames: Long, val battleMoves: Long, val items: Long) {
    EMERALD(0x083185C8, 0x0831977C, 0x0831C898, 0x085839A0),
    FIRERED_REV0(0x08245EE0, 0x08247094, 0x08250C04, 0x083DB028),
    FIRERED_REV1(0x08245F50, 0x08247104, 0x08250C74, 0x083DB098),
}
