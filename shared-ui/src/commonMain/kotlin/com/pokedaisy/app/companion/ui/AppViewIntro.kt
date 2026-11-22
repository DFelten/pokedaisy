package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.theme.AppView

/**
 * Shown once, on the Library, after the update that brought VIEW (and on a new install once setup is done): the two
 * looks drawn side by side as little libraries, a button under each, and KEEP MY CURRENT LOOK under them. A pick puts the
 * view on at once ([com.pokedaisy.app.companion.ui.theme.AppViews.set], its font with it); any answer marks it asked
 * (Prefs.appViewAsked). SETTINGS > VIEW changes it later, on either screen.
 */
@Composable
fun AppViewIntro(
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    current: AppView,
    onPick: (AppView) -> Unit,
    onKeep: () -> Unit,
) {
    OptionOverlay(onKeep, Modifier.widthIn(max = 720.dp)) {
      androidx.compose.foundation.layout.BoxWithConstraints {
        // A short screen (the RG DS's 480 px) gets smaller miniatures, so every button fits without scrolling.
        val short = constraints.hasBoundedHeight && maxHeight < 400.dp
        Column {
            OptionTitleWindow(tr("NEW: PICK A VIEW"), m, onBack = onKeep)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                // Scrolls on a short screen (the RG DS), so KEEP MY CURRENT LOOK is always there to reach.
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(m.u * 4)) {
                    GbaText(
                        tr("PokéDaisy now comes in two looks: RETRO, the pixel look you know, and MODERN, smooth and round like the newer games.") +
                            " " + tr("Change it any time in SETTINGS > VIEW."),
                        OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE,
                        modifier = Modifier.padding(horizontal = m.u * 4),
                    )
                    Spacer(Modifier.height(m.u * if (short) 3 else 6))
                    Row(horizontalArrangement = Arrangement.spacedBy(m.u * 8), modifier = Modifier.fillMaxWidth()) {
                        for (view in AppView.entries) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                AppViewPreview(
                                    view,
                                    Modifier.fillMaxWidth(if (short) 0.32f else 0.86f).aspectRatio(16f / 10f)
                                        .soundClickable { onPick(view) },
                                )
                                Spacer(Modifier.height(m.u * if (short) 2 else 4))
                                OptionButton(
                                    view.label, m, onClick = { onPick(view) }, emphasis = view == current,
                                    modifier = Modifier.fillMaxWidth().height(m.rowHeight * if (short) 1.25f else 1.3f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(m.u * if (short) 3 else 6))
                    OptionButton(
                        tr("KEEP MY CURRENT LOOK"), m, onClick = onKeep,
                        modifier = Modifier.fillMaxWidth().height(m.rowHeight * if (short) 1.2f else 1.2f),
                    )
                }
            }
        }
      }
    }
}

/**
 * A little library in [view]'s look: header, three games, the nav rail. Drawn here by hand in each look's own colours
 * (both side by side, whatever the view now), not with the app's pieces, which draw in the current one.
 */
@Composable
fun AppViewPreview(view: AppView, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (view == AppView.MODERN) drawModernMini() else drawRetroMini()
    }
}

private val COVERS = listOf(Color(0xFFD63A3A), Color(0xFF6A3CB8), Color(0xFF2FA88E))

private fun DrawScope.drawModernMini() {
    val w = size.width
    val h = size.height
    val r = w * 0.035f
    // The page: a soft blue-white wash.
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3F6FD), Color(0xFFE4EBFA))), cornerRadius = CornerRadius(r))
    // The header: the blue bar along the top edge.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF5585EE), Color(0xFF3463D6))),
        size = Size(w, h * 0.17f), cornerRadius = CornerRadius(r),
    )
    drawRect(Color(0xFF3463D6), Offset(0f, h * 0.1f), Size(w, h * 0.07f))
    drawRoundRect(Color.White, Offset(w * 0.05f, h * 0.065f), Size(w * 0.24f, h * 0.045f), CornerRadius(h))
    // The nav rail: three round icons down the left.
    val railW = w * 0.15f
    for (i in 0 until 3) {
        val c = Offset(railW / 2f, h * (0.3f + i * 0.2f))
        drawCircle(if (i == 0) Color(0xFF3D6FE0) else Color(0xFFC6D0E4), radius = w * 0.035f, center = c)
    }
    // Three game cards: white, a soft shadow, a rounded cover and two lines of text.
    val left = railW + w * 0.02f
    val cardW = w - left - w * 0.04f
    val cardH = h * 0.2f
    for (i in 0 until 3) {
        val top = h * 0.23f + i * (cardH + h * 0.045f)
        for (s in 3 downTo 1) {
            drawRoundRect(
                Color(0x141B2A55), Offset(left - s * 0.6f, top + s * 1.2f), Size(cardW + s * 1.2f, cardH + s * 0.6f),
                CornerRadius(r * 1.4f + s),
            )
        }
        drawRoundRect(if (i == 0) Color(0xFFE5EDFF) else Color.White, Offset(left, top), Size(cardW, cardH), CornerRadius(r * 1.4f))
        val pad = cardH * 0.14f
        val cover = cardH - 2 * pad
        drawRoundRect(COVERS[i], Offset(left + pad, top + pad), Size(cover, cover), CornerRadius(cover * 0.22f))
        val tx = left + pad * 2 + cover
        drawRoundRect(Color(0xFF232B40), Offset(tx, top + cardH * 0.3f), Size(cardW * 0.42f, cardH * 0.14f), CornerRadius(h))
        drawRoundRect(Color(0xFFB4BDD0), Offset(tx, top + cardH * 0.58f), Size(cardW * 0.28f, cardH * 0.1f), CornerRadius(h))
    }
}

private fun DrawScope.drawRetroMini() {
    val w = size.width
    val h = size.height
    // One "GBA pixel" of the miniature: everything snaps to it, corners step by it.
    val p = (w / 120f).coerceAtLeast(1f)
    fun box(color: Color, x: Float, y: Float, bw: Float, bh: Float, notch: Boolean = true) {
        if (bw <= 0f || bh <= 0f) return
        if (!notch || bw < 3 * p || bh < 3 * p) { drawRect(color, Offset(x, y), Size(bw, bh)); return }
        drawRect(color, Offset(x + p, y), Size(bw - 2 * p, bh))
        drawRect(color, Offset(x, y + p), Size(bw, bh - 2 * p))
    }
    fun framed(x: Float, y: Float, bw: Float, bh: Float, frame: Color, mid: Color, fill: Color) {
        box(frame, x, y, bw, bh)
        box(mid, x + p, y + p, bw - 2 * p, bh - 2 * p)
        box(fill, x + 2 * p, y + 2 * p, bw - 4 * p, bh - 4 * p)
    }
    // The daisy page: pale, a dotted grid.
    box(Color(0xFFF6F2F7), 0f, 0f, w, h)
    var y = 0f
    while (y < h) {
        var x = 0f
        while (x < w) { drawRect(Color(0xFFE4DCE8), Offset(x, y), Size(p, p)); x += p * 6 }
        y += p * 6
    }
    // The header window: RETRO's blue in its dark rim.
    val railW = w * 0.15f
    framed(railW + p * 3, p * 3, w - railW - p * 6, h * 0.15f, Color(0xFF001A52), Color(0xFF9CB1DE), Color(0xFF314E8C))
    box(Color.White, railW + p * 8, p * 3 + h * 0.055f, w * 0.2f, p * 3, notch = false)
    // The rail: pixel icons down the left.
    for (i in 0 until 3) {
        val c = if (i == 0) Color(0xFFD63A3A) else Color(0xFF8C8C94)
        box(c, railW / 2f - p * 4, h * (0.27f + i * 0.2f), p * 8, p * 8)
    }
    // Three game cards: mint frames, a stepped cover, pixel text lines.
    val left = railW + p * 3
    val cardW = w - left - p * 3
    val cardH = h * 0.2f
    for (i in 0 until 3) {
        val top = h * 0.24f + i * (cardH + p * 3)
        framed(left, top, cardW, cardH, Color(0xFF00226B), Color(0xFF9CB1DE), if (i == 0) Color(0xFFFFE7FF) else Color(0xFFF7FFFF))
        val pad = p * 4
        val cover = cardH - 2 * pad
        box(Color(0xFF29313A), left + pad, top + pad, cover, cover)
        box(COVERS[i], left + pad + p, top + pad + p, cover - 2 * p, cover - 2 * p)
        val tx = left + pad * 2 + cover
        box(Color(0xFF4A4239), tx, top + cardH * 0.3f, cardW * 0.42f, p * 3, notch = false)
        box(Color(0xFFA9A49C), tx, top + cardH * 0.6f, cardW * 0.28f, p * 2, notch = false)
    }
}
