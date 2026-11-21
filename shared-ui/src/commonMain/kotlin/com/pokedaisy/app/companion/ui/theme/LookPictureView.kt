package com.pokedaisy.app.companion.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pokedaisy.app.companion.data.isFile
import com.pokedaisy.app.companion.ui.NIGHT
import com.pokedaisy.app.companion.data.readBytes
import com.pokedaisy.app.companion.ui.decodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import okio.Path

/** A moving picture being played: its frame now ([frame]; a new one is told by the host's onFrame). */
interface AnimatedPicture {
    fun frame(): ImageBitmap?
    fun close()
}

/**
 * Plays MY LOOK's moving pictures (GIF / WebP): the platform's ([host], set at start). [open] decodes [file] at most
 * [maxSide] pixels a side, off the UI thread, and calls onFrame (on the UI thread) whenever a frame is ready; null = it
 * can't play it (an old Android, previews), and its first frame shows as a still one.
 */
interface AnimatedPictureHost {
    fun open(file: Path, maxSide: Int, onFrame: () -> Unit): AnimatedPicture?
}

object AnimatedPictures {
    @Volatile var host: AnimatedPictureHost? = null
    /** Hold moving pictures on their first frame (LIGHT COMPANION: a weak device keeps its time for the game). */
    var still by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * A picture of the look ([LookPicture]) filling [modifier], cropped to its shape and smoothly scaled; [scrim] over it
 * (DARK MODE's night). A moving one plays; a still one is decoded off the UI thread once per change and kept.
 */
@Composable
fun LookPictureImage(picture: LookPicture, modifier: Modifier = Modifier.fillMaxSize(), scrim: Float = 0f, maxSide: Int = 1280, still: Boolean = false) {
    val playing = picture.animated && !still && !AnimatedPictures.still && AnimatedPictures.host != null
    if (playing) {
        var tick by remember(picture) { mutableIntStateOf(0) }
        val player = remember(picture) { AnimatedPictures.host?.open(picture.file, maxSide) { tick++ } }
        DisposableEffect(player) { onDispose { player?.close() } }
        if (player != null) {
            Canvas(modifier) {
                @Suppress("UNUSED_EXPRESSION") tick
                player.frame()?.let { drawCropped(it, scrim) }
            }
            return
        }
    }
    val still = rememberStill(picture)
    Canvas(modifier) { still?.let { drawCropped(it, scrim) } }
}

/** [image] filling the area, cropped to its shape, centred, [scrim] of night over it. */
fun DrawScope.drawCropped(image: ImageBitmap, scrim: Float = 0f) {
    val k = maxOf(size.width / image.width, size.height / image.height)
    val w = (image.width * k).roundToInt()
    val h = (image.height * k).roundToInt()
    drawImage(
        image, dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w, h), filterQuality = FilterQuality.Medium,
    )
    if (scrim > 0f) drawRect(NIGHT.copy(alpha = scrim))
}

@Composable
private fun rememberStill(picture: LookPicture): ImageBitmap? {
    val key = picture.file to picture.generation
    val image by produceState(stillCache[picture.file]?.takeIf { it.first == picture.generation }?.second, key) {
        stillCache[picture.file]?.takeIf { it.first == picture.generation }?.let { value = it.second; return@produceState }
        value = withContext(Dispatchers.IO) { runCatching { if (picture.file.isFile) decodeImage(picture.file.readBytes()) else null }.getOrNull() }
        value?.let { stillCache[picture.file] = picture.generation to it }
    }
    return image
}

// Decoded still pictures (a few: two backgrounds, two bars, a theme's), kept while the process lives.
private val stillCache = com.pokedaisy.app.companion.data.LockedMap<Path, Pair<Int, ImageBitmap>>()

/** The picture behind a screen, as [LookPictureImage] draws it, or a preview's stand-in; null = the look's own backdrop. */
@Composable
fun LookBackdrop(companion: Boolean, modifier: Modifier = Modifier.fillMaxSize(), scrim: Float = 0f): Boolean {
    val preview = if (companion) CustomLook.previewCompanion else CustomLook.previewApp
    if (preview != null) {
        Canvas(modifier) { drawCropped(preview, scrim) }
        return true
    }
    val picture = (if (companion) CustomLook.companionPicture else CustomLook.appPicture) ?: return false
    // LIGHT COMPANION keeps the companion's moving picture on its first frame.
    LookPictureImage(picture, modifier, scrim, still = companion && com.pokedaisy.app.companion.LightCompanion.on)
    return true
}

