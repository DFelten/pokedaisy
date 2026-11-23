package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.pokedaisy.app.companion.ui.theme.CustomLook
import com.pokedaisy.app.companion.ui.theme.LookPicture
import com.pokedaisy.app.companion.ui.theme.LookPictureImage
import com.pokedaisy.app.companion.ui.theme.drawCropped

/**
 * SIDE BARS ([CustomLook.bars]) behind a game that doesn't fill its area: black, with the bars' pictures cropped to the
 * room left and right of [game] (above and below it, when that's where the room is). [game] is in this area's pixels;
 * null = not laid out yet (black). A side narrower than 2 px gets nothing.
 */
@Composable
fun SideBarsFill(bars: CustomLook.Bars, game: Rect?) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (bars.black || game == null) return@BoxWithConstraints
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val sideways = game.left + (w - game.right) >= game.top + (h - game.bottom)
        val first = if (sideways) Rect(0f, 0f, game.left, h) else Rect(0f, 0f, w, game.top)
        val second = if (sideways) Rect(game.right, 0f, w, h) else Rect(0f, game.bottom, w, h)
        val preview = CustomLook.previewBars
        BarPicture(bars.first, preview?.first, first)
        BarPicture(bars.second, preview?.second ?: preview?.first, second)
    }
}

@Composable
private fun BarPicture(picture: LookPicture?, preview: androidx.compose.ui.graphics.ImageBitmap?, at: Rect) {
    if (at.width < 2f || at.height < 2f) return
    val d = LocalDensity.current
    val m = with(d) { Modifier.offset { IntOffset(at.left.toInt(), at.top.toInt()) }.size(at.width.toDp(), at.height.toDp()) }
    when {
        preview != null -> androidx.compose.foundation.Canvas(m) { drawCropped(preview) }
        picture != null -> Box(m) { LookPictureImage(picture, Modifier.fillMaxSize(), maxSide = 1080) }
    }
}
