package com.pokedaisy.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pokedaisy.app.companion.ui.theme.AnimatedPicture
import com.pokedaisy.app.companion.ui.theme.AnimatedPictureHost
import java.io.File
import okio.Path

/**
 * MY LOOK's moving pictures on Android 9+ ([AnimatedImageDrawable], GIF and animated WebP): decoded at the size shown
 * (ImageDecoder's target size, never the file's), each frame drawn on a thread of its own into one of two bitmaps when
 * the drawable says it's due, and handed to Compose. An older Android gets null (the first frame shows, still).
 */
class AndroidAnimatedPictures : AnimatedPictureHost {
    override fun open(file: Path, maxSide: Int, onFrame: () -> Unit): AnimatedPicture? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching { Player(File(file.toString()), maxSide, onFrame) }.getOrNull()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private class Player(file: File, maxSide: Int, private val onFrame: () -> Unit) : AnimatedPicture {
        private val thread = HandlerThread("pokedaisy-look-anim").apply { start() }
        private val worker = Handler(thread.looper)
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var shown: ImageBitmap? = null
        @Volatile private var closed = false
        private var drawable: AnimatedImageDrawable? = null
        private val buffers = arrayOfNulls<Bitmap>(2)
        private var next = 0

        init {
            worker.post {
                runCatching {
                    val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val w = info.size.width; val h = info.size.height
                        val k = minOf(1f, maxSide.toFloat() / maxOf(w, h))
                        if (k < 1f) decoder.setTargetSize((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
                        // A software bitmap: the frames are drawn into ours, not onto the screen's canvas.
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    } as? AnimatedImageDrawable ?: return@runCatching
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    d.callback = object : Drawable.Callback {
                        override fun invalidateDrawable(who: Drawable) { worker.post(::render) }
                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                            worker.postAtTime(what, `when`)
                        }
                        override fun unscheduleDrawable(who: Drawable, what: Runnable) { worker.removeCallbacks(what) }
                    }
                    d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable = d
                    d.start()
                    render()
                }
            }
        }

        private fun render() {
            val d = drawable ?: return
            if (closed) return
            val w = d.intrinsicWidth.coerceAtLeast(1); val h = d.intrinsicHeight.coerceAtLeast(1)
            val bmp = buffers[next]?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { buffers[next] = it }
            bmp.eraseColor(0)
            // A software canvas: the drawable draws the frame that's due now and schedules the next (scheduleDrawable).
            d.draw(Canvas(bmp))
            next = 1 - next
            shown = bmp.asImageBitmap()
            main.post { if (!closed) onFrame() }
        }

        override fun frame(): ImageBitmap? = shown

        override fun close() {
            closed = true
            worker.post {
                drawable?.stop()
                drawable?.callback = null
                drawable = null
                thread.quitSafely()
            }
        }
    }
}
