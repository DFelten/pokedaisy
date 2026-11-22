package com.pokedaisy.app.companion.ui.theme

/**
 * What the app takes from a file the player adds to its look (MY LOOK, a theme pack): how big, what kind, what shape
 * (the user's rule, 2026-11-23: every file checked, with a size cap). Every reader goes through here; a file that fails
 * gets a [LookImportError] with the reason in plain words, and nothing of it is kept.
 */
object LookLimits {
    /** A picture as picked (it's scaled down before it's kept). */
    const val PICTURE_BYTES = 40 * 1024 * 1024
    /** A moving picture (GIF / WebP), kept as it is. */
    const val ANIMATION_BYTES = 30 * 1024 * 1024
    /** A picture's sides: at least this... */
    const val PICTURE_MIN_SIDE = 16
    /** ...at most this, and this many pixels in all (a still picture; it's decoded at a fraction first). */
    const val PICTURE_MAX_SIDE = 20_000
    const val PICTURE_MAX_PIXELS = 200_000_000L
    /** A moving picture's frame: decoded every frame, so far smaller. */
    const val ANIMATION_MAX_SIDE = 4096
    const val ANIMATION_MAX_PIXELS = 4096L * 2304L
    /** A font file. */
    const val FONT_BYTES = 10 * 1024 * 1024
    /** A theme's JSON. */
    const val THEME_CHARS = 16 * 1024
    /** A theme pack (.zip) as picked, its files, and what they unpack to in all. */
    const val PACK_BYTES = 80 * 1024 * 1024
    const val PACK_ENTRIES = 16
    const val PACK_UNPACKED_BYTES = 100 * 1024 * 1024
    /** How many of each the app keeps. */
    const val MAX_FONTS = 30
    const val MAX_THEMES = 60

    fun mb(bytes: Int): String = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} MB" else "${bytes / 1024} KB"

    /** Throws when [size] bytes are over [max] for a [what] ("picture", "moving picture", "font", "theme"). */
    fun checkSize(size: Long, max: Int, what: String) {
        if (size > max) throw LookImportError(when (what) {
            "font" -> "That font is too big ({0} at most)"
            "moving picture" -> "That moving picture is too big ({0} at most)"
            "theme" -> "That theme file is too big ({0} at most)"
            else -> "That picture is too big ({0} at most)"
        }, mb(max))
        if (size <= 0L) throw LookImportError("That file is empty")
    }

    /** Throws unless a picture of [w] x [h] can be shown ([animated]: decoded every frame, so smaller). */
    fun checkPictureSize(w: Int, h: Int, animated: Boolean) {
        if (w <= 0 || h <= 0) throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
        if (w < PICTURE_MIN_SIDE || h < PICTURE_MIN_SIDE) throw LookImportError("That picture is too small ({0} pixels at least)", "$PICTURE_MIN_SIDE")
        val maxSide = if (animated) ANIMATION_MAX_SIDE else PICTURE_MAX_SIDE
        val maxPixels = if (animated) ANIMATION_MAX_PIXELS else PICTURE_MAX_PIXELS
        if (w > maxSide || h > maxSide || w.toLong() * h > maxPixels) {
            throw LookImportError("That picture is too large ({0} pixels a side at most)", "$maxSide")
        }
    }
}

/** A picture file's kind, by its first bytes (never its name). */
enum class PictureKind(val extension: String) {
    PNG(".png"), JPEG(".jpg"), WEBP(".webp"), GIF(".gif"), BMP(".bmp");

    companion object {
        fun of(b: ByteArray): PictureKind? = when {
            b.size < 12 -> null
            b.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
            b.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
            b.startsWith(0x52, 0x49, 0x46, 0x46) && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
                b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte() -> WEBP
            b.startsWith(0x47, 0x49, 0x46, 0x38) -> GIF
            b.startsWith(0x42, 0x4D) -> BMP
            else -> null
        }

        private fun ByteArray.startsWith(vararg v: Int) = v.indices.all { this[it].toInt() and 0xFF == v[it] }
    }
}

/** Does a picture move: a GIF with more than one frame, or a WebP with an animation chunk. */
object PictureMotion {
    fun isAnimated(b: ByteArray): Boolean = when (PictureKind.of(b)) {
        PictureKind.GIF -> gifFrames(b, stopAt = 2) >= 2
        PictureKind.WEBP -> webpAnimated(b)
        else -> false
    }

    /** A GIF's frames counted by its image descriptors, walking its blocks (up to [stopAt]). Malformed: what was counted. */
    fun gifFrames(b: ByteArray, stopAt: Int = Int.MAX_VALUE): Int {
        if (b.size < 13) return 0
        var at = 13
        val flags = b[10].toInt() and 0xFF
        if (flags and 0x80 != 0) at += 3 * (1 shl ((flags and 7) + 1))   // global colour table
        var frames = 0
        while (at < b.size && frames < stopAt) {
            when (b[at].toInt() and 0xFF) {
                0x21 -> { at += 2; at = skipSubBlocks(b, at) ?: return frames }   // extension
                0x2C -> {                                                          // image
                    frames++
                    if (at + 10 > b.size) return frames
                    val f = b[at + 9].toInt() and 0xFF
                    at += 10
                    if (f and 0x80 != 0) at += 3 * (1 shl ((f and 7) + 1))
                    at += 1   // LZW minimum code size
                    at = skipSubBlocks(b, at) ?: return frames
                }
                0x3B -> return frames
                else -> return frames
            }
        }
        return frames
    }

    /** A GIF's or WebP's size from its header (the logical screen; VP8X's canvas, VP8 / VP8L's frame), else null. */
    fun size(b: ByteArray): Pair<Int, Int>? = when (PictureKind.of(b)) {
        PictureKind.GIF -> if (b.size < 10) null else (u8(b, 6) or (u8(b, 7) shl 8)) to (u8(b, 8) or (u8(b, 9) shl 8))
        PictureKind.WEBP -> webpSize(b)
        else -> null
    }

    private fun u8(b: ByteArray, at: Int) = b[at].toInt() and 0xFF

    private fun webpSize(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 30) return null
        val tag = (12 until 16).map { u8(b, it).toChar() }.joinToString("")
        return when (tag) {
            "VP8X" -> (1 + (u8(b, 24) or (u8(b, 25) shl 8) or (u8(b, 26) shl 16))) to (1 + (u8(b, 27) or (u8(b, 28) shl 8) or (u8(b, 29) shl 16)))
            "VP8 " -> ((u8(b, 26) or (u8(b, 27) shl 8)) and 0x3FFF) to ((u8(b, 28) or (u8(b, 29) shl 8)) and 0x3FFF)
            "VP8L" -> {
                val bits = u8(b, 21) or (u8(b, 22) shl 8) or (u8(b, 23) shl 16) or (u8(b, 24) shl 24)
                (1 + (bits and 0x3FFF)) to (1 + ((bits ushr 14) and 0x3FFF))
            }
            else -> null
        }
    }

    private fun skipSubBlocks(b: ByteArray, from: Int): Int? {
        var at = from
        while (at < b.size) {
            val n = b[at].toInt() and 0xFF
            at += 1 + n
            if (n == 0) return at
        }
        return null
    }

    private fun webpAnimated(b: ByteArray): Boolean {
        // RIFF....WEBP then chunks; VP8X's flags byte has the animation bit (0x02), and an ANIM chunk follows.
        var at = 12
        while (at + 8 <= b.size) {
            val tag = (0 until 4).map { (b[at + it].toInt() and 0xFF).toChar() }.joinToString("")
            val len = (b[at + 4].toInt() and 0xFF) or ((b[at + 5].toInt() and 0xFF) shl 8) or
                ((b[at + 6].toInt() and 0xFF) shl 16) or ((b[at + 7].toInt() and 0xFF) shl 24)
            if (tag == "VP8X" && at + 8 < b.size && (b[at + 8].toInt() and 0x02) != 0) return true
            if (tag == "ANIM" || tag == "ANMF") return true
            if (len < 0) return false
            at += 8 + len + (len and 1)
        }
        return false
    }
}
