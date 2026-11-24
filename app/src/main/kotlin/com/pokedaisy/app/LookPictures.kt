package com.pokedaisy.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pokedaisy.app.companion.ui.theme.LookImportError
import com.pokedaisy.app.companion.ui.theme.LookLimits
import com.pokedaisy.app.companion.ui.theme.PictureKind
import com.pokedaisy.app.companion.ui.theme.PictureMotion
import java.io.ByteArrayOutputStream

/** MY LOOK's pictures: a picked file checked ([LookLimits]) and read at the size the screen needs. */
object LookPictures {
    /** What a picked picture became: a still one (scaled, JPEG / PNG bytes) or a moving one (the file as it was). */
    class Picked(val bytes: ByteArray, val animated: Boolean)

    /**
     * [uri]'s picture, checked by its bytes (PNG, JPEG, WebP, GIF, BMP), its size and its shape. A moving GIF / WebP is
     * kept as it is (at most [LookLimits.ANIMATION_BYTES], its frames [LookLimits.ANIMATION_MAX_SIDE] a side) when
     * [allowAnimated]; anything else is scaled to the screen's longer side (1280 to 2560 px) and kept as a JPEG (a PNG
     * when it's see-through): a phone photo is decoded at a fraction of its size first (inSampleSize), so a 50 MP
     * picture never fills the memory.
     */
    fun pick(context: Context, uri: Uri, allowAnimated: Boolean = true): Picked =
        fromBytes(context, read(context, uri, LookLimits.PICTURE_BYTES, "picture"), allowAnimated)

    /** [pick] for bytes already read (a theme pack's pictures). */
    fun fromBytes(context: Context, raw: ByteArray, allowAnimated: Boolean = true): Picked {
        LookLimits.checkSize(raw.size.toLong(), LookLimits.PICTURE_BYTES, "picture")
        PictureKind.of(raw) ?: throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val animated = allowAnimated && PictureMotion.isAnimated(raw)
        LookLimits.checkPictureSize(bounds.outWidth, bounds.outHeight, animated)
        if (animated) {
            LookLimits.checkSize(raw.size.toLong(), LookLimits.ANIMATION_BYTES, "moving picture")
            return Picked(raw, animated = true)
        }
        return Picked(scaled(context, raw, bounds), animated = false)
    }

    private fun scaled(context: Context, raw: ByteArray, bounds: BitmapFactory.Options): ByteArray {
        val metrics = context.resources.displayMetrics
        val longest = maxOf(metrics.widthPixels, metrics.heightPixels).coerceIn(1280, 2560)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longest) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
        val k = minOf(1f, longest.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (k < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * k).toInt().coerceAtLeast(1), (decoded.height * k).toInt().coerceAtLeast(1), true)
        } else decoded
        // A see-through picture stays see-through (a PNG); the rest are JPEGs, a fraction of the size.
        val format = if (scaled.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        return ByteArrayOutputStream().also { scaled.compress(format, 90, it) }.toByteArray()
    }

    /** A picked file's bytes, refused past [max] (a "[what]" in the message) without reading the rest. */
    fun read(context: Context, uri: Uri, max: Int, what: String): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) LookLimits.checkSize(out.size().toLong(), max, what)
            }
            out.toByteArray()
        } ?: throw LookImportError("That file couldn't be read.")
        LookLimits.checkSize(bytes.size.toLong(), max, what)
        return bytes
    }
}
