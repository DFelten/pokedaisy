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

    fun formatFor(rom: RomIdentityReader, size: () -> Long): Gen3SaveFormat? {
        if (rom.isGameBoy()) return null
        val length = size()
        if (length > MAX_ROM_SIZE) return null
        val code = rom.gameCode()
        // Full-size hacks under a retail header can move save offsets or change Pokémon records.
        if (length == MAX_ROM_SIZE && (code == "BPEE" || code == "BPRE")) {
            val sha = rom.sha1()
            // A hack the editor knows: its own layout, its rules read from the ROM (once).
            HACKS[sha]?.let { hack -> return hack.format.takeIf { loadRules(hack, rom) } }
            if (sha in NOT_RETAIL_SAVES) return null
        }
        return Gen3SaveFormat.forGameCode(code)
    }

    /** A ROM hack whose saves the editor reads: its layout, the config naming its tables, its trade family. */
    private class Hack(val format: Gen3SaveFormat, val config: () -> com.pokedaisy.app.companion.data.NativeConfig, val family: String)

    private val HACKS: Map<String, Hack> by lazy {
        with(com.pokedaisy.app.companion.data.TelemetrySampler) {
            mapOf(
                EMERALD_LEGACY_V1_1_4_SHA1 to Hack(Gen3SaveFormat.EMERALD_LEGACY, { com.pokedaisy.app.companion.data.NATIVE_EMERALD_LEGACY }, "emerald-legacy"),
            )
        }
    }

    /** [hack]'s rules from [rom], installed once; false when the ROM can't give them (the editor then leaves it out). */
    private fun loadRules(hack: Hack, rom: RomIdentityReader): Boolean {
        if (Gen3Rules.isInstalled(hack.format)) return true
        return runCatching {
            val tables = gen3RomTables(hack.config(), hack.family) ?: return false
            val bytes = rom.readAll() ?: return false
            Gen3Rules.install(hack.format, RomGen3Rules(bytes, tables).also { it.lastMove })
            true
        }.getOrDefault(false)
    }

    /**
     * The 16 MB builds the Poller knows whose save isn't laid out as their header's retail game's:
     * (Emerald Legacy, whose 120-slot ITEMS pocket moves everything after it, has a layout of its own: [HACKS].)
     * - Emerald Seaglass: pokeemerald-expansion's records (National Dex species ids, experience in 21 bits with a nickname's
     *   11th letter above it, its own shiny odds; GitHub #1167: COLLECTION showed its Pokémon at level 100 and none shiny).
     * - Recharged Emerald / Recharged Yellow: SaveBlock1s of their own past the bag.
     * - Clover: FireRed's save under an Emerald header.
     */
    private val NOT_RETAIL_SAVES = with(com.pokedaisy.app.companion.data.TelemetrySampler) {
        setOf(
            EMERALD_SEAGLASS_V3_0_SHA1, CLOVER_V1_3_3_SHA1,
            RECHARGED_EMERALD_V2_2_5_SHA1, RECHARGED_EMERALD_V2_2_8_SHA1, RECHARGED_EMERALD_REBALANCED_V2_2_8_SHA1,
            RECHARGED_YELLOW_V1_8_0_SHA1, RECHARGED_YELLOW_V1_9_0_SHA1, RECHARGED_YELLOW_V1_9_4_SHA1, RECHARGED_YELLOW_V1_9_7_SHA1,
        )
    }

    fun open(path: Path, format: Gen3SaveFormat): Result {
        if (!path.isFile) return Result.Failed(tr("No save yet - save once in the game first."))
        val bytes = runCatching { path.readBytes() }.getOrNull()
            ?: return Result.Failed(tr("Can't read the save {0}", path.toString()))
        return when (val parsed = Gen3SaveFile.parse(bytes, format)) {
            is Gen3SaveFile.Parsed.Failed -> Result.Failed(parsed.reason)
            is Gen3SaveFile.Parsed.Ok -> {
                val mismatch = Gen3SaveData(parsed.save).mismatch()
                if (mismatch == null) Result.Ready(bytes, parsed.save)
                else Result.Failed(tr("This save doesn't look like a {0} save ({1}), so it's left alone.", format.label, mismatch))
            }
        }
    }
}
