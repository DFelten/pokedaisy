package com.pokedaisy.app

import android.content.Context
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * Settings > FOLDERS > SAVE STATES: every game's states, grouped by game (its folder is the game's CRC32, [SaveStates]),
 * named from the library or the folder's [SaveStates.NAME_FILE], each state with its picture; and moving them all when the
 * player picks another states folder. Blocking: off the UI thread.
 */
object StatesBrowser {
    /** RESUME = the state a closed game picks up from; RETRY = BOSS RETRY's. Slots are 0..9. */
    const val RESUME = -1
    const val RETRY = -2

    data class State(val slot: Int, val file: File, val thumb: File?, val savedAt: Long, val bytes: Long)

    data class Game(
        val key: String,
        val dir: File,
        /** The library's name, else the folder's note, else null (shown as an unknown game). */
        val name: String?,
        /** The library's ROM with this key, when there is one. */
        val rom: File?,
        val states: List<State>,
        /** Everything in the game's folder, undo copies and pictures included. */
        val bytes: Long,
    ) {
        val newest: Long get() = states.maxOfOrNull { it.savedAt } ?: 0L
        /** The newest state's picture, for the game's row. */
        val cover: File? get() = states.filter { it.thumb != null }.maxByOrNull { it.savedAt }?.thumb
    }

    /** The library's ROMs by their state key (CRC32), from the CRC cache only: a ROM never read here isn't named by it. */
    fun libraryKeys(context: Context, prefs: Prefs): Map<String, File> =
        RomFolder.libraryRoms(context, prefs).mapNotNull { rom ->
            RomCrcs.cached(context, rom)?.let { "%08x".format(it) to rom }
        }.toMap()

    /** Every game folder in [root] holding at least one state, newest first. */
    fun list(root: File, library: Map<String, File>, label: (File) -> String): List<Game> =
        root.listFiles().orEmpty().filter { it.isDirectory && SaveStates.isGameDir(it.name) }.mapNotNull { dir ->
            val states = statesIn(dir)
            if (states.isEmpty()) return@mapNotNull null
            val rom = library[dir.name]
            val note = runCatching { File(dir, SaveStates.NAME_FILE).takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
            Game(dir.name, dir, rom?.let(label) ?: note?.takeIf { it.isNotEmpty() }, rom, states,
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
        }.sortedByDescending { it.newest }

    /** [dir]'s states: the resume state, the ten slots, BOSS RETRY's - each with its picture when it has one. */
    fun statesIn(dir: File): List<State> {
        fun state(slot: Int, name: String): State? {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) return null
            return State(slot, f, File(dir, "$name.png").takeIf { it.isFile }, f.lastModified(), f.length())
        }
        return listOfNotNull(state(RESUME, "resume")) +
            (SaveStates.SLOT_MIN..SaveStates.SLOT_MAX).mapNotNull { state(it, "ss$it") } +
            listOfNotNull(state(RETRY, "battle_retry"))
    }

    /** Deletes [state] with its picture, its companion copy and its other sidecars (not a slot's undo copy). */
    fun delete(game: Game, state: State) {
        val states = SaveStates(game.dir.parentFile ?: return, game.key)
        states.files.removeState(state.file.toOkioPath())
    }

    /** Deletes every state of [game] (its whole folder). */
    fun deleteGame(game: Game) {
        game.dir.deleteRecursively()
    }

    data class Moved(val games: Int, val files: Int, val failed: Int)

    /**
     * Moves every game folder from [from] into [to], file by file: a file [to] already has is kept when it's as new,
     * else replaced; a source file is deleted only once its copy is the same size. Folders that aren't a game's are left.
     * [progress] gets (done, total) files.
     */
    fun move(from: File, to: File, progress: (Int, Int) -> Unit = { _, _ -> }): Moved {
        if (from.canonicalPath == to.canonicalPath) return Moved(0, 0, 0)
        val games = from.listFiles().orEmpty().filter { it.isDirectory && SaveStates.isGameDir(it.name) }
        val files = games.flatMap { g -> g.walkTopDown().filter { it.isFile }.map { g to it }.toList() }
        var moved = 0
        var failed = 0
        files.forEachIndexed { i, (game, src) ->
            val dst = File(File(to, game.name), src.relativeTo(game).path)
            val ok = runCatching {
                if (!(dst.isFile && dst.lastModified() >= src.lastModified())) {
                    dst.parentFile?.mkdirs()
                    val tmp = File(dst.path + ".moving")
                    src.copyTo(tmp, overwrite = true)
                    if (tmp.length() != src.length()) { tmp.delete(); error("short copy") }
                    if (dst.exists()) dst.delete()
                    check(tmp.renameTo(dst))
                    dst.setLastModified(src.lastModified())
                }
                src.delete()
            }.isSuccess
            if (ok) moved++ else failed++
            progress(i + 1, files.size)
        }
        // Empty game folders left behind go too.
        games.forEach { g -> g.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() } }
        return Moved(games.size, moved, failed)
    }
}
