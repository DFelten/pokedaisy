package com.pokedaisy.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PC fan games' files (FeatureFlags.pcGames, a prototype). Everything sits under the app's external files, where adb
 * and a file manager reach it:
 *
 * - `pc/cores/mkxp-z_libretro_android.so`: the mkxp-z core, built by the player (native-capture/pc_probe/README.md),
 *   never bundled. Copied into the app's private files before it loads (Android won't load code from shared storage).
 * - `pc/games/<folder>/Game.ini` (or one folder deeper): each game, as its developers ship it.
 * - `pc/saves/`: the core's save folder; the games' own saves land in `mkxp-z/Saves/<title>/`, backed up into
 *   `pokedaisy-backups/mkxp-z/<title>/<time>/` before a game opens, as linux/main.c does.
 */
object PcGames {
    private const val TAG = "pokedaisy/pc"
    const val CORE_NAME = "mkxp-z_libretro_android.so"
    private const val BACKUPS_KEPT = 10

    class Game(val ini: File, val title: String)

    fun root(context: Context): File = File(AppStorage.root(context), "pc")
    fun coresDir(context: Context) = File(root(context), "cores")
    fun gamesDir(context: Context) = File(root(context), "games")
    fun saveDir(context: Context) = File(root(context), "saves")
    private fun systemDir(context: Context) = File(context.filesDir, "pc/system")

    /** The games in `pc/games`: a Game.ini in a folder there, or in a folder inside one (a repo's zip unpacks so). */
    fun list(context: Context): List<Game> {
        val dirs = gamesDir(context).apply { mkdirs() }.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.mapNotNull { dir ->
            val ini = File(dir, "Game.ini").takeIf { it.isFile }
                ?: dir.listFiles()?.filter { it.isDirectory }?.map { File(it, "Game.ini") }?.firstOrNull { it.isFile }
            ini?.let { Game(it, titleOf(it) ?: dir.name) }
        }.sortedBy { it.title.lowercase() }
    }

    /** Game.ini's `Title=`, the name the game shows (and the core names its save folder after). */
    fun titleOf(ini: File): String? = runCatching {
        ini.readLines(Charsets.UTF_8).firstOrNull { it.trim().startsWith("Title=", ignoreCase = true) }
            ?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** The core where it can load (copied from `pc/cores` when that one changed), or null if there is none. */
    fun installCore(context: Context): File? {
        val src = File(coresDir(context).apply { mkdirs() }, CORE_NAME)
        val dst = File(context.filesDir, "pc/$CORE_NAME")
        if (!src.isFile) return dst.takeIf { it.isFile }
        if (dst.isFile && dst.length() == src.length() && dst.lastModified() >= src.lastModified()) return dst
        dst.parentFile?.mkdirs()
        val tmp = File(dst.path + ".tmp")
        src.copyTo(tmp, overwrite = true)
        if (!tmp.renameTo(dst)) return null
        Log.i(TAG, "core installed: ${dst.length()} bytes")
        return dst
    }

    /** The system folder with our preloads where the core reads them (`mkxp-z/Scripts/Preload`); rewritten each start. */
    fun installPreloads(context: Context): File {
        val sys = systemDir(context)
        val dir = File(sys, "mkxp-z/Scripts/Preload").apply { mkdirs() }
        context.assets.list("pc_preload").orEmpty().filter { it.endsWith(".rb") }.forEach { name ->
            context.assets.open("pc_preload/$name").use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
        }
        return sys
    }

    /**
     * Copies each game save folder (`mkxp-z/Saves/<title>`) to the backups when it differs from the newest copy there,
     * keeping the newest [BACKUPS_KEPT]. False if a copy failed: the game then stays closed, as for every save.
     */
    fun backupSaves(context: Context): Boolean {
        val saves = File(saveDir(context), "mkxp-z/Saves")
        val folders = saves.listFiles()?.filter { it.isDirectory }.orEmpty()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return folders.all { game ->
            runCatching {
                val dest = File(saveDir(context), "pokedaisy-backups/mkxp-z/${game.name}").apply { mkdirs() }
                val copies = dest.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
                if (copies.lastOrNull()?.let { sameFiles(game, it) } == true) return@runCatching true
                if (game.listFiles().isNullOrEmpty()) return@runCatching true
                val tmp = File(dest, ".$stamp.tmp")
                game.copyRecursively(tmp, overwrite = true)
                check(tmp.renameTo(File(dest, stamp)))
                (copies + File(dest, stamp)).sortedBy { it.name }.dropLast(BACKUPS_KEPT).forEach { it.deleteRecursively() }
                true
            }.getOrElse {
                Log.w(TAG, "save backup of ${game.name} failed", it)
                false
            }
        }
    }

    /** A game's resume state (gzip; see pk_pc.c) and its sidecar ([sidecarFor]). */
    fun resumeState(context: Context, ini: File): File =
        File(root(context), "states/${ini.parentFile?.name ?: "game"}.resume.gz").apply { parentFile?.mkdirs() }

    fun sidecarFor(state: File) = File(state.path + ".json")

    /**
     * The games' save folders as they are now: every file's path, size and CRC32. A resume state remembers it, and is
     * loaded only while it still matches, as the GBA side's pkStateMatchesSave: a state holds the game's progress, and
     * resumed after a newer in-game save, an in-game save from it would write the older progress over the newer.
     * Every game's saves count (one PC game saving drops another's resume): the core names the folders, not us.
     */
    fun savesFingerprint(context: Context): String {
        val dir = File(saveDir(context), "mkxp-z/Saves")
        val crc = java.util.zip.CRC32()
        dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(dir).path }.forEach { f ->
            crc.update(f.relativeTo(dir).path.toByteArray())
            crc.update(f.length().toString().toByteArray())
            runCatching { f.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n <= 0) break; crc.update(b, 0, n) } } }
        }
        return "%08x".format(crc.value)
    }

    private fun sameFiles(a: File, b: File): Boolean {
        val fa = a.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(a).path }
        val fb = b.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(b).path }
        return fa.keys == fb.keys && fa.all { (k, f) -> f.length() == fb.getValue(k).length() && f.readBytes().contentEquals(fb.getValue(k).readBytes()) }
    }
}
