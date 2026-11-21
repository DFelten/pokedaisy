package com.pokedaisy.app

import android.content.Context
import android.net.Uri

/** The app's LookPictures (BitmapFactory scaling a picked picture): no file picker on the desktop, and a pack's pictures
 * go through as they are. */
object LookPictures {
    class Picked(val bytes: ByteArray, val animated: Boolean)

    fun pick(context: Context, uri: Uri, allowAnimated: Boolean = true): Picked = throw UnsupportedOperationException("no pictures in previews")
    fun fromBytes(context: Context, raw: ByteArray, allowAnimated: Boolean = true): Picked =
        Picked(raw, allowAnimated && com.pokedaisy.app.companion.ui.theme.PictureMotion.isAnimated(raw))
    fun read(context: Context, uri: Uri, max: Int, what: String): ByteArray = throw UnsupportedOperationException("no files in previews")
}
