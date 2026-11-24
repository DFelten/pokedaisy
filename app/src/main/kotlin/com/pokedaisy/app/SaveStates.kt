package com.pokedaisy.app

import com.pokedaisy.app.companion.DsCore
import java.io.File
import okio.Path.Companion.toOkioPath
import com.pokedaisy.app.companion.data.StateFiles
import java.util.zip.CRC32

/**
 * Per-game savestate storage: `<states folder>/<rom-crc32>/ss<N>` + `ss<N>.png` (the folder is [root]: the app's own
 * `files/states/` or the player's, Settings > FOLDERS > SAVE STATES)
 * thumbnail, for slots [SLOT_MIN]..[SLOT_MAX]. A dedicated `resume` slot backs
 * suspend-on-background.
 *
 * The `.ss` blobs are mGBA's own extended (mpack) state format, so they also
 * load in desktop mGBA via "Load state from file".
 */
class SaveStates(statesRoot: File, romCrc: String) {

    internal val files = StateFiles(File(statesRoot, romCrc).toOkioPath(), extraSidecars = listOf(StateFiles.ACHIEVEMENT_PROGRESS_SUFFIX))

    fun stateFile(slot: Int): File = File(files.stateFile(slot).toString())
    fun thumbFile(slot: Int): File = File(files.thumbFile(slot).toString())
    fun bakFile(slot: Int): File = File(files.bakFile(slot).toString())        // one-level undo of a save
    fun bakThumbFile(slot: Int): File = File(files.bakThumbFile(slot).toString())
    val resumeFile: File get() = File(files.resumeFile.toString())
    val undoLoadFile: File get() = File(files.undoLoadFile.toString())          // pre-load snapshot
    /** BOSS RETRY: the state taken as a trainer battle began (never a slot; one per game, overwritten by the next). */
    val retryFile: File get() = File(files.retryFile.toString())
    /** Set when the library loads another save file into this game ([GameSaves.load]):
     * the next start boots from that save instead of resuming, since a resume (or
     * manual) state still holds the old save's progress and would write it back. */
    val freshBootFile: File get() = File(files.freshBootFile.toString())

    fun exists(slot: Int): Boolean = files.exists(slot)

    /** Writes the game's name beside its states ([NAME_FILE]) when it isn't there yet or changed. Never throws. */
    fun noteGame(name: String) {
        if (!com.pokedaisy.app.companion.FlaggedFeatures.settingsPlus) return
        runCatching {
            val f = File(files.directory.toString(), NAME_FILE)
            if (!f.isFile || f.readText() != name) f.writeText(name)
        }
    }

    fun slotInfo(slot: Int): SlotInfo {
        val f = stateFile(slot)
        return SlotInfo(
            slot = slot,
            present = f.isFile && f.length() > 0,
            savedAt = if (f.isFile) f.lastModified() else 0L,
            thumb = thumbFile(slot).takeIf { it.isFile },
        )
    }

    fun allSlots(): List<SlotInfo> = (SLOT_MIN..SLOT_MAX).map { slotInfo(it) }

    /**
     * The file to auto-load on a fresh boot: whichever of [resumeFile] (written
     * on the last clean close/background) or a manual save slot was written
     * most recently. The auto-suspend snapshot is normally the newest by
     * definition (it captures the live game state, which already reflects any
     * manual saves made before closing) — this fallback only matters if the
     * last session ended without a clean close (crash, force-kill), leaving
     * [resumeFile] stale or missing while a manual slot is actually newer.
     * Returns null if there's nothing to resume from at all.
     */
    fun latestResumeSource(): File? {
        return files.latestResumeSource()?.let { File(it.toString()) }
    }

    data class SlotInfo(
        val slot: Int,
        val present: Boolean,
        val savedAt: Long,
        val thumb: File?,
    )

    companion object {
        /** The app's own states folder: `<external files>/states` (internal files where there's no external storage). */
        fun defaultRoot(context: android.content.Context): File =
            File(AppStorage.root(context), "states")

        /** Where every game's states folder is: the player's SAVE STATES folder while it can be written to, else
         * [defaultRoot]. A folder that went away (an SD card out) never takes the states with it: the default is used meanwhile. */
        fun root(context: android.content.Context, prefs: Prefs = Prefs(context)): File {
            val custom = prefs.statesDirOverride?.takeIf { it.isNotBlank() && com.pokedaisy.app.companion.FlaggedFeatures.settingsPlus }?.let(::File)
            if (custom != null && (custom.isDirectory || custom.mkdirs()) && custom.canWrite()) return custom
            return defaultRoot(context)
        }

        /** [rom]'s states at [crc] in the current [root]. */
        fun of(context: android.content.Context, crc: String, prefs: Prefs = Prefs(context)) = SaveStates(root(context, prefs), crc)

        /** Games open in this process (PokeDaisyActivity's onCreate / onDestroy): the SAVE STATES folder isn't moved
         * while one is, since its states (its resume state) are written where it started. */
        val openGames = java.util.concurrent.atomic.AtomicInteger(0)

        /** A game's states folder name - a CRC32 (8 hex digits) or a 3DS title id (16) - what [StatesBrowser] lists. */
        fun isGameDir(name: String) = (name.length == 8 || name.length == 16) && name.all { it in '0'..'9' || it in 'a'..'f' }

        /** The file in a game's states folder naming the game, for the SAVE STATES page and anyone looking at the folder. */
        const val NAME_FILE = "game.txt"

        const val SLOT_MIN = 0
        const val SLOT_MAX = 9

        /** Where a DS game's resume state ([resumeFile]) waits for DS CORE's [core] (DsCore.CORE_*) while the other
         * core runs the game: states don't cross cores, so the engine sets it aside here and the next start on that core
         * resumes it (PokeDaisyActivity.startGame), if the save file still matches it as any resume must. */
        fun parkedResume(resume: File, core: Int): File =
            File(resume.parentFile, when (core) {
                DsCore.CORE_DSPERATE -> "resume.dsperate"
                DsCore.CORE_BANANADS -> "resume.bananads"
                else -> "resume.melonds"
            })

        /** The DS core whose state [state] is (its first word: pk_nds_cores.h's PK_NDS_STATE_MAGIC_*), MELONDS if unknown. */
        fun stateCore(state: File): Int {
            val b = runCatching { state.inputStream().use { s -> ByteArray(4).also { if (s.read(it) != 4) return DsCore.CORE_MELONDS } } }
                .getOrNull() ?: return DsCore.CORE_MELONDS
            val magic = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
            return when (magic) {
                0x50444B50 -> DsCore.CORE_DSPERATE   // "PKDP"
                0x42444B50 -> DsCore.CORE_BANANADS   // "PKDB"
                else -> DsCore.CORE_MELONDS          // "PKDS"
            }
        }

        /** CRC32 of the ROM file (an archive's ROM, see [RomArchive]), as 8 lowercase hex digits. */
        fun crc32(rom: File): String {
            val crc = CRC32()
            RomArchive.open(rom).buffered(1 shl 16).use { s ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    crc.update(buf, 0, n)
                }
            }
            return "%08x".format(crc.value)
        }
    }
}
