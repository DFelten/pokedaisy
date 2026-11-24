package com.pokedaisy.app

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Where the app keeps its own files (the docs' `<external files>`: saves, states, imported ROMs, covers, media, STEADY's
 * clips, controls): getExternalFilesDir(null), which players can reach over USB, else the app's internal filesDir.
 *
 * Android hands back the external path even when it couldn't make the folder (an Anbernic RG Rotate, 2026-11-24:
 * `Android/data/com.pokedaisy.app` was never made, so no save, state or cover could be written there, and 1.6.2 crashed
 * opening every game). Such a folder is given up for filesDir, and for good ([MARKER] in filesDir): a later start where
 * the external folder works again must not move the saves and states back to a place where none of them are. A folder
 * that's there is never given up (its files would vanish from the app), and no external storage at all (null) is
 * filesDir for this process only, as before.
 */
object AppStorage {
    /** In filesDir: the external folder couldn't be made once, so the app's files live in filesDir from then on. */
    const val MARKER = "storage-internal"

    @Volatile private var chosen: File? = null

    fun root(context: Context): File = chosen ?: synchronized(this) {
        chosen ?: run {
            val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
            val pick = choose(external, context.filesDir)
            if (pick != external) Log.w("pokedaisy/storage", "external files folder $external unusable: files go to $pick")
            pick.also { chosen = it }
        }
    }

    /** [external] while it's a folder or can be made into one, else [internal] (kept, see [MARKER]). */
    internal fun choose(external: File?, internal: File): File {
        val marker = File(internal, MARKER)
        if (marker.isFile) return internal
        if (external == null) return internal
        if (external.isDirectory || external.mkdirs()) return external
        runCatching { internal.mkdirs(); marker.writeText("$external couldn't be made\n") }
        return internal
    }
}
