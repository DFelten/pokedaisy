package com.pokedaisy.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pokedaisy.app.companion.ui.theme.LookImportError
import java.io.ByteArrayOutputStream

/** MY LOOK > CHOOSE A PICTURE: the picked image read at the size the screen needs. */
object LookPictures {
    /**
     * [uri]'s picture scaled to the screen's longer side (1280 to 2560 px) and kept as a JPEG: a phone photo is decoded
     * at a fraction of its size first (inSampleSize), so a 50 MP picture never fills the memory.
     */
    fun scaled(context: Context, uri: Uri): ByteArray {
        val metrics = context.resources.displayMetrics
        val longest = maxOf(metrics.widthPixels, metrics.heightPixels).coerceIn(1280, 2560)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw LookImportError("Not a picture: PNG, JPEG or WebP")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longest) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw LookImportError("Not a picture: PNG, JPEG or WebP")
        val k = minOf(1f, longest.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (k < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * k).toInt().coerceAtLeast(1), (decoded.height * k).toInt().coerceAtLeast(1), true)
        } else decoded
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
