package com.pokedaisy.app

import android.content.Context
import android.net.Uri
import com.pokedaisy.app.companion.ui.theme.LookImportError
import com.pokedaisy.app.companion.ui.theme.LookLimits
import com.pokedaisy.app.companion.ui.theme.ThemePack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A theme as a file on Android: a theme pack (.zip: [ThemePack.THEME_FILE] and the files it names) or a bare theme
 * (.json). Read with every limit of [LookLimits] (the zip's size, its files' count, names and what they unpack to: a zip
 * bomb stops at the cap, not at the end), written for EXPORT. What the files are is [ThemePack]'s job.
 */
object ThemePackFiles {
    /** [uri]'s files by name: a zip's entries, or a JSON file as [ThemePack.THEME_FILE]. */
    fun read(context: Context, uri: Uri): Map<String, ByteArray> {
        val raw = LookPictures.read(context, uri, LookLimits.PACK_BYTES, "theme")
        return if (isZip(raw)) unzip(raw) else {
            LookLimits.checkSize(raw.size.toLong(), LookLimits.THEME_CHARS, "theme")
            mapOf(ThemePack.THEME_FILE to raw)
        }
    }

    /** A pack's still pictures scaled to the screen like a picked one ([LookPictures.fromBytes]); moving ones and the rest as they are. */
    fun prepare(context: Context, files: Map<String, ByteArray>): Map<String, ByteArray> = files.mapValues { (name, bytes) ->
        if (com.pokedaisy.app.companion.ui.theme.LookSlot.parse(name) == null) bytes
        else LookPictures.fromBytes(context, bytes).bytes
    }.let { prepared ->
        // A still picture may come back as another kind (a BMP kept as a JPEG): its name follows its bytes.
        prepared.entries.associate { (name, bytes) ->
            val slot = com.pokedaisy.app.companion.ui.theme.LookSlot.parse(name)?.first
            val kind = if (slot != null) com.pokedaisy.app.companion.ui.theme.PictureKind.of(bytes) else null
            (if (slot != null && kind != null) slot.base + kind.extension else name) to bytes
        }
    }

    fun isZip(b: ByteArray) = b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte()

    fun unzip(raw: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                // A pack's files sit at its top (a folder some zip tools add around them is dropped); anything else is refused.
                val name = entry.name.substringAfterLast('/')
                if (name.startsWith("._") || entry.name.startsWith("__MACOSX/")) continue
                if (!ThemePack.allowedName(name)) throw LookImportError("A theme pack can't hold \"{0}\"", name.take(40))
                if (out.size >= LookLimits.PACK_ENTRIES) throw LookImportError("That theme pack has too many files")
                if (name in out) throw LookImportError("That theme pack has \"{0}\" twice", name)
                val bytes = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    bytes.write(buf, 0, n)
                    total += n
                    if (total > LookLimits.PACK_UNPACKED_BYTES || bytes.size() > ThemePack.maxBytes(name)) {
                        throw LookImportError("That theme file is too big ({0} at most)", LookLimits.mb(LookLimits.PACK_UNPACKED_BYTES))
                    }
                }
                out[name] = bytes.toByteArray()
            }
        }
        if (out.isEmpty()) throw LookImportError("Not a theme file: it isn't JSON")
        return out
    }

    /** EXPORT: [files] as a zip, written to [to]. */
    fun write(files: Map<String, ByteArray>, to: OutputStream) {
        ZipOutputStream(to).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
