package com.pokedaisy.app

import android.content.Context
import android.net.Uri

/** The app's LookPictures (BitmapFactory scaling a picked picture): no file picker on the desktop. */
object LookPictures {
    fun scaled(context: Context, uri: Uri): ByteArray = throw UnsupportedOperationException("no pictures in previews")
}
