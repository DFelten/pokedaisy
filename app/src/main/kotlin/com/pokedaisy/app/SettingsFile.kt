package com.pokedaisy.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pokedaisy.app.cheats.CheatStore
import java.io.File
import java.util.Calendar
import okio.Path.Companion.toOkioPath

/**
 * The settings file on Android ([SettingsBackup] has the format): written to / read from a file the player picks
 * (Android's document picker), from Settings > APP > SETTINGS FILE and the first-time setup's first step. Blocking:
 * off the UI thread.
 */
object SettingsFile {
    const val MIME = "application/json"

    fun suggestedName(): String = Calendar.getInstance().let {
        SettingsBackup.fileName(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    private fun prefsFile(context: Context) = context.getSharedPreferences("pokedaisy", Context.MODE_PRIVATE)

    /** The text files a settings file carries, by their name in it. */
    private fun textFiles(context: Context): Map<String, File> {
        val controls = File(context.controlsDir.toString())
        val cheats = File(CheatStore.dir(context.filesDir.toOkioPath()).toString())
        return buildMap {
            put(SettingsBackup.CONTROLS_FILE, File(controls, SettingsBackup.CONTROLS_FILE))
            put(SettingsBackup.HOTKEYS_FILE, File(controls, SettingsBackup.HOTKEYS_FILE))
            cheats.listFiles().orEmpty().forEach { put("cheats/${it.name}", it) }
        }.filterKeys(SettingsBackup::fileAllowed)
    }

    /** Writes the settings file to [uri]. */
    fun export(context: androidx.activity.ComponentActivity, uri: Uri): Result<Unit> = runCatching {
        val files = textFiles(context).filterValues { it.isFile && it.length() < SettingsBackup.MAX_BYTES }
            .mapValues { it.value.readText() }
        val text = SettingsBackup.encode(prefsFile(context).all, files, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("can't write")
    }

    sealed interface Imported {
        data class Done(val settings: Int, val files: Int) : Imported
        data class Refused(val message: String) : Imported
    }

    /** Puts the settings file at [uri] back: every setting it has written (committed, not queued), the rest of the
     * device's settings back at their defaults, its button / hotkey / cheat files written. Then [restart]. */
    fun import(context: androidx.activity.ComponentActivity, uri: Uri): Imported {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val bytes = s.readNBytesCompat(SettingsBackup.MAX_BYTES + 1)
                if (bytes.size > SettingsBackup.MAX_BYTES) null else String(bytes)
            }
        }.getOrNull() ?: return Imported.Refused(com.pokedaisy.app.companion.i18n.tr("Can't read that file"))
        val backup = try {
            SettingsBackup.decode(text)
        } catch (e: SettingsBackup.NotSettings) {
            return Imported.Refused(when (e.refusal) {
                SettingsBackup.Refusal.NEWER_APP -> com.pokedaisy.app.companion.i18n.tr("That settings file is from a newer PokeDaisy: update the app first")
                SettingsBackup.Refusal.NOT_SETTINGS -> com.pokedaisy.app.companion.i18n.tr("That isn't a PokeDaisy settings file")
            })
        }
        val prefs = prefsFile(context)
        val edits = SettingsBackup.restoreEdits(prefs.all.keys, backup) { File(it).isDirectory }
        val edit = prefs.edit()
        for ((key, value) in edits) when (value) {
            null -> edit.remove(key)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
        }
        // The library shouldn't open the first-time setup over a restored library.
        edit.putBoolean("setup_done", true).remove("setup_requested")
        if (!edit.commit()) return Imported.Refused(com.pokedaisy.app.companion.i18n.tr("Can't write the settings"))
        var files = 0
        val targets = textFiles(context)
        for ((name, body) in backup.files) {
            val target = targets[name] ?: if (name.startsWith("cheats/"))
                File(File(CheatStore.dir(context.filesDir.toOkioPath()).toString()), name.removePrefix("cheats/")) else continue
            if (runCatching { target.parentFile?.mkdirs(); target.writeText(body) }.isSuccess) files++
        }
        // Buttons and hotkeys at their defaults in the file: back to the defaults here too.
        for (name in listOf(SettingsBackup.CONTROLS_FILE, SettingsBackup.HOTKEYS_FILE)) {
            if (name !in backup.files) targets[name]?.delete()
        }
        return Imported.Done(backup.prefs.size, files)
    }

    /** Starts the app again on its library: language, theme, fonts and every screen read the settings at start. */
    fun restart(activity: androidx.activity.ComponentActivity) {
        val intent = Intent(activity, LibraryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
