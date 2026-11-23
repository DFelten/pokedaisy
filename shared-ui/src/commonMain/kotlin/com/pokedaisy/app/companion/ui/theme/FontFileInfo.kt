package com.pokedaisy.app.companion.ui.theme

/** What the app reads from a font file: is it one, and its capitals' height against its em (OS/2's sCapHeight). */
object FontFileInfo {
    fun isFont(b: ByteArray): Boolean = problem(b) == null

    /**
     * Why [b] isn't a font the app can draw with, or null: an sfnt (TTF / OTF) whose table directory fits the file, with
     * the tables every font needs (head with its magic number and a sane em, cmap, hhea, hmtx, maxp with glyphs, and the
     * outlines: glyf + loca or CFF). A collection, WOFF or a renamed file is refused before any renderer sees it.
     */
    fun problem(b: ByteArray): String? {
        if (extension(b) == null) return "Not a font file: TTF or OTF only"
        val n = u16(b, 4)
        if (n !in 4..MAX_TABLES || 12 + n * 16 > b.size) return "That font file is damaged"
        val found = HashMap<String, Pair<Int, Int>>()
        for (i in 0 until n) {
            val at = 12 + i * 16
            val tag = (0 until 4).map { (b[at + it].toInt() and 0xFF).toChar() }.joinToString("")
            val offset = u32(b, at + 8)
            val length = u32(b, at + 12)
            if (offset < 0 || length < 0 || offset + length > b.size) return "That font file is damaged"
            found[tag] = offset.toInt() to length.toInt()
        }
        val needed = listOf("head", "cmap", "hhea", "hmtx", "maxp")
        if (needed.any { it !in found }) return "That font file is damaged"
        if (!(("glyf" in found && "loca" in found) || "CFF " in found || "CFF2" in found)) return "That font has no letters in it"
        val (head, headLen) = found.getValue("head")
        if (headLen < 54 || u32(b, head + 12) != 0x5F0F3CF5L) return "That font file is damaged"
        if (u16(b, head + 18) !in 16..16384) return "That font file is damaged"
        val (maxp, maxpLen) = found.getValue("maxp")
        if (maxpLen < 6 || u16(b, maxp + 4) == 0) return "That font has no letters in it"
        return null
    }

    /** Throws [LookImportError] unless [b] is a font ([problem]) of at most [LookLimits.FONT_BYTES]. */
    fun check(b: ByteArray) {
        LookLimits.checkSize(b.size.toLong(), LookLimits.FONT_BYTES, "font")
        problem(b)?.let { throw LookImportError(it) }
    }

    /** ".ttf" / ".otf" by the file's first bytes, else null (WOFF and collections aren't read). */
    fun extension(b: ByteArray): String? {
        if (b.size < 12) return null
        val tag = u32(b, 0)
        return when (tag) {
            0x00010000L, 0x74727565L -> ".ttf"   // 1.0, 'true'
            0x4F54544FL -> ".otf"                // 'OTTO'
            else -> null
        }
    }

    /** The capitals' height as a share of the em; 0.7 when the font doesn't say (OS/2 before version 2). */
    fun capHeight(b: ByteArray): Float = runCatching {
        val t = tables(b)
        val head = t["head"] ?: return@runCatching DEFAULT_CAP
        val unitsPerEm = u16(b, head + 18)
        val os2 = t["OS/2"] ?: return@runCatching DEFAULT_CAP
        if (u16(b, os2) < 2 || unitsPerEm <= 0) return@runCatching DEFAULT_CAP
        val cap = u16(b, os2 + 88)
        if (cap <= 0) DEFAULT_CAP else (cap.toFloat() / unitsPerEm).coerceIn(0.3f, 1f)
    }.getOrDefault(DEFAULT_CAP)

    private fun tables(b: ByteArray): Map<String, Int> {
        if (b.size < 12) return emptyMap()
        val n = u16(b, 4)
        val out = HashMap<String, Int>()
        for (i in 0 until n) {
            val at = 12 + i * 16
            if (at + 16 > b.size) break
            val tag = (0 until 4).map { (b[at + it].toInt() and 0xFF).toChar() }.joinToString("")
            val offset = u32(b, at + 8).toInt()
            if (offset in 0 until b.size) out[tag] = offset
        }
        return out
    }

    private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private const val DEFAULT_CAP = 0.7f
    private const val MAX_TABLES = 64
}
