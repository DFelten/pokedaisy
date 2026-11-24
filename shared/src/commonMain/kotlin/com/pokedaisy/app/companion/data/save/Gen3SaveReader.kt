package com.pokedaisy.app.companion.data.save

import com.pokedaisy.app.companion.data.RomIdentityReader
import com.pokedaisy.app.companion.data.TelemetrySampler
import com.pokedaisy.app.companion.data.isFile
import com.pokedaisy.app.companion.data.readBytes
import com.pokedaisy.app.companion.i18n.tr
import okio.Path

/** The same cartridge eligibility and save validation on both hosts. Read on a worker with the game closed. */
object Gen3SaveReader {
    private const val MAX_ROM_SIZE = 0x1000000L

    sealed interface Result {
        class Ready(val bytes: ByteArray, val save: Gen3SaveFile) : Result
        class Failed(val message: String) : Result
    }

    /**
     * The layout [rom]'s saves are read in, or null when the editor doesn't read them. [key] (the file's path, size and
     * date) keeps a big ROM's hash for the app's life: the game lists ask for every ROM each time they're built.
     */
    fun formatFor(rom: RomIdentityReader, key: String? = null, size: () -> Long): Gen3SaveFormat? {
        if (rom.isGameBoy()) return null
        val length = size()
        if (length > 2 * MAX_ROM_SIZE) return null
        val code = rom.gameCode()
        // Full-size hacks under a retail header (or Ultra Violet's own) can move save offsets or change Pokémon records;
        // past 16 MB only the hacks the editor knows ([HACKS]) open.
        if (length >= MAX_ROM_SIZE && (code == "BPEE" || code == "BPRE" || code == "PUVV")) {
            val sha = if (key == null) rom.sha1() else synchronized(shaCache) { shaCache.getOrPut(key) { rom.sha1() } }
            // A hack the editor knows: its own layout, its rules read from the ROM (once).
            HACKS[sha]?.let { hack -> return hack.format.takeIf { loadRules(hack, rom) } }
            if (length > MAX_ROM_SIZE || sha in NOT_RETAIL_SAVES) return null
        }
        if (length > MAX_ROM_SIZE) return null
        return Gen3SaveFormat.forGameCode(code)
    }

    private val shaCache = HashMap<String, String?>()

    /** A ROM hack whose saves the editor reads: its layout, the config naming its tables, its trade family. */
    private class Hack(
        val format: Gen3SaveFormat, val config: () -> com.pokedaisy.app.companion.data.NativeConfig, val family: String,
        /** Edited in place: its tables at this retail cart's addresses. */
        val inPlace: InPlaceBase? = null,
        val itemLayout: ItemLayout = ItemLayout.VANILLA,
    )

    private val HACKS: Map<String, Hack> by lazy {
        with(com.pokedaisy.app.companion.data.TelemetrySampler) {
            mapOf(
                EMERALD_LEGACY_V1_1_4_SHA1 to Hack(Gen3SaveFormat.EMERALD_LEGACY, { com.pokedaisy.app.companion.data.NATIVE_EMERALD_LEGACY }, "emerald-legacy"),
                EMERALD_KAIZO_V2_0_SHA1 to Hack(Gen3SaveFormat.EMERALD_KAIZO, { com.pokedaisy.app.companion.data.NATIVE_EMERALD_KAIZO }, "emerald-kaizo", InPlaceBase.EMERALD),
                GLAZED_V9_2_0_SHA1 to Hack(Gen3SaveFormat.GLAZED, { com.pokedaisy.app.companion.data.NATIVE_GLAZED }, "glazed", InPlaceBase.EMERALD),
                ORANGE_ISLANDS_V5_7_BETA_SHA1 to Hack(Gen3SaveFormat.ORANGE_ISLANDS, { com.pokedaisy.app.companion.data.NATIVE_ORANGE_ISLANDS }, "orange-islands", InPlaceBase.FIRERED_REV0),
                ORANGE_ISLANDS_UNVERSIONED_SHA1 to Hack(Gen3SaveFormat.ORANGE_ISLANDS, { com.pokedaisy.app.companion.data.NATIVE_ORANGE_ISLANDS }, "orange-islands", InPlaceBase.FIRERED_REV0),
                ULTRA_VIOLET_V1_22_SHA1 to Hack(Gen3SaveFormat.ULTRA_VIOLET, { com.pokedaisy.app.companion.data.NATIVE_ULTRA_VIOLET }, "ultra-violet", InPlaceBase.FIRERED_REV0),
                ASH_GRAY_V4_5_3_SHA1 to Hack(Gen3SaveFormat.ASH_GRAY, { com.pokedaisy.app.companion.data.NATIVE_ASH_GRAY }, "ash-gray", InPlaceBase.FIRERED_REV0),
                ASH_GRAY_FORK_BUG_FIX_V1_0_SHA1 to Hack(Gen3SaveFormat.ASH_GRAY, { com.pokedaisy.app.companion.data.NATIVE_ASH_GRAY }, "ash-gray", InPlaceBase.FIRERED_REV0),
                FIRERED_ROCKET_EDITION_UNVERSIONED_SHA1 to Hack(Gen3SaveFormat.ROCKET_EDITION, { com.pokedaisy.app.companion.data.NATIVE_ROCKET_EDITION }, "rocket-edition", InPlaceBase.FIRERED_REV0),
                // One family: the three builds are one game (Rebalanced only changes moves and types).
                RECHARGED_EMERALD_V2_2_5_SHA1 to Hack(Gen3SaveFormat.RECHARGED_EMERALD_V2_2_5, { com.pokedaisy.app.companion.data.NATIVE_RECHARGED_EMERALD_V2_2_5 }, "recharged-emerald", itemLayout = ItemLayout.RECHARGED),
                RECHARGED_EMERALD_V2_2_8_SHA1 to Hack(Gen3SaveFormat.RECHARGED_EMERALD_V2_2_8, { com.pokedaisy.app.companion.data.NATIVE_RECHARGED_EMERALD_V2_2_8 }, "recharged-emerald", itemLayout = ItemLayout.RECHARGED),
                RECHARGED_EMERALD_REBALANCED_V2_2_8_SHA1 to Hack(Gen3SaveFormat.RECHARGED_EMERALD_REBALANCED, { com.pokedaisy.app.companion.data.NATIVE_RECHARGED_EMERALD_REBALANCED_V2_2_8 }, "recharged-emerald", itemLayout = ItemLayout.RECHARGED),
            )
        }
    }

    /** [hack]'s rules from [rom], installed once; false when the ROM can't give them (the editor then leaves it out). */
    private fun loadRules(hack: Hack, rom: RomIdentityReader): Boolean {
        if (Gen3Rules.isInstalled(hack.format)) return true
        return runCatching {
            val bytes = rom.readAll() ?: return false
            val tables = gen3RomTables(hack.config(), hack.family, hack.inPlace, hack.itemLayout)
                ?.let { t -> hack.inPlace?.let { it.follow(bytes, t) ?: return false } ?: t } ?: return false
            Gen3Rules.install(hack.format, RomGen3Rules(bytes, tables).also { it.lastMove })
            true
        }.getOrDefault(false)
    }

    /**
     * The 16 MB builds the Poller knows whose save isn't laid out as their header's retail game's:
     * (Emerald Legacy, whose 120-slot ITEMS pocket moves everything after it, has a layout of its own: [HACKS].)
     * - Emerald Seaglass: pokeemerald-expansion's records (National Dex species ids, experience in 21 bits with a nickname's
     *   11th letter above it, its own shiny odds; GitHub #1167: COLLECTION showed its Pokémon at level 100 and none shiny).
     * - Recharged Yellow: a SaveBlock1 of its own past the bag (Recharged Emerald's is in [HACKS]).
     * - Clover: FireRed's save under an Emerald header.
     */
    private val NOT_RETAIL_SAVES = with(com.pokedaisy.app.companion.data.TelemetrySampler) {
        setOf(
            EMERALD_SEAGLASS_V3_0_SHA1, CLOVER_V1_3_3_SHA1,
            RECHARGED_YELLOW_V1_8_0_SHA1, RECHARGED_YELLOW_V1_9_0_SHA1, RECHARGED_YELLOW_V1_9_4_SHA1, RECHARGED_YELLOW_V1_9_7_SHA1,
        )
    }

    /**
     * The rules [rom]'s saves read with: a known hack's ([HACKS]), else - a cart that isn't a retail dump, edited in place
     * (a randomizer's output, a small hack) - its own ROM's at its base cart's addresses ([InPlaceBase]: never worse than
     * retail's, the tables it didn't move still retail's), else retail's. [key] keeps them for the app's life.
     */
    fun rulesFor(rom: RomIdentityReader, format: Gen3SaveFormat, key: String? = null): Gen3Rules {
        if (!format.retail) return format.rules
        val base = when (rom.gameCode()) {
            "BPEE" -> InPlaceBase.EMERALD
            "BPRE" -> if (rom.revision() == 1) InPlaceBase.FIRERED_REV1 else InPlaceBase.FIRERED_REV0
            else -> null
        } ?: return format.rules
        val cached = key?.let { synchronized(inPlace) { inPlace[it] } }
        if (cached != null) return cached
        val sha = if (key == null) rom.sha1() else synchronized(shaCache) { shaCache.getOrPut(key) { rom.sha1() } }
        if (sha == null || com.pokedaisy.app.GameTitleCatalog.isRetail(sha)) return format.rules
        val cfg = if (base == InPlaceBase.EMERALD) com.pokedaisy.app.companion.data.NATIVE_EMERALD_RETAIL
            else if (base == InPlaceBase.FIRERED_REV1) com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
            else com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV0_RETAIL
        val rules = runCatching {
            val bytes = rom.readAll() ?: return format.rules
            // Its tables where its code points (a rebuild's don't follow: retail's then).
            val tables = gen3RomTables(cfg, RETAIL, base)?.let { base.follow(bytes, it) } ?: return format.rules
            RomGen3Rules(bytes, tables).also { it.lastMove }
        }.getOrNull() ?: return format.rules
        key?.let { synchronized(inPlace) { inPlace[it] = rules } }
        return rules
    }

    private val inPlace = HashMap<String, Gen3Rules>()

    fun open(path: Path, format: Gen3SaveFormat, rules: Gen3Rules = format.rules): Result {
        if (!path.isFile) return Result.Failed(tr("No save yet - save once in the game first."))
        val bytes = runCatching { path.readBytes() }.getOrNull()
            ?: return Result.Failed(tr("Can't read the save {0}", path.toString()))
        return when (val parsed = Gen3SaveFile.parse(bytes, format, rules)) {
            is Gen3SaveFile.Parsed.Failed -> Result.Failed(parsed.reason)
            is Gen3SaveFile.Parsed.Ok -> {
                val mismatch = Gen3SaveData(parsed.save).mismatch()
                if (mismatch == null) Result.Ready(bytes, parsed.save)
                else Result.Failed(tr("This save doesn't look like a {0} save ({1}), so it's left alone.", format.label, mismatch))
            }
        }
    }
}
