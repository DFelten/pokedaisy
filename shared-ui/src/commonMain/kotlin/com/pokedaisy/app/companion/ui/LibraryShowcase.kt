package com.pokedaisy.app.companion.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.LibraryLayout
import com.pokedaisy.app.companion.data.RomCategory
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.theme.drawCropped
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What an immersive library view ([LibraryLayout.immersive]) shows of a host's game [T]: its card facts ([game]), the
 * console it's for ([shape]: a cartridge's shape), its cover and art (loaded by the host, null meanwhile: [backdrop] =
 * ScreenScraper's fanart / screenshot where there's one, [logo] its wheel), a line or two of facts, PLAY and its menu.
 */
class LibraryShowcase<T>(
    val game: (T) -> LibraryGame,
    val shape: (T) -> RomCategory?,
    val cover: @Composable (T) -> ImageBitmap?,
    val backdrop: @Composable (T) -> ImageBitmap? = { null },
    val logo: @Composable (T) -> ImageBitmap? = { null },
    val facts: (T) -> List<String> = { emptyList() },
    val play: (T) -> Unit,
    /** The game's menu (the list's ⋮), anchored where it's called: open, and how to close it. */
    val menu: @Composable (T, Boolean, () -> Unit) -> Unit,
)

/**
 * The immersive library views (the user's call, 2026-11-23: more animated, like other frontends): CARTRIDGES, CAROUSEL
 * and SPOTLIGHT. One game is in front ([selected]: a tap or the D-pad's focus moves it, a tap on it plays, a long press
 * opens its menu); the row scrolls so it's in the middle. UP from the row goes to the header ([headerUp]).
 */
@Composable
fun <T> LibraryShowcaseView(
    games: List<T>, layout: LibraryLayout, showcase: LibraryShowcase<T>, key: (T) -> String,
    m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier = Modifier, headerUp: () -> FocusRequester? = { null },
) {
    if (games.isEmpty()) return
    // The game in front, kept by its key while the list changes (a sort, a new cover).
    var selectedKey by remember { mutableStateOf(key(games.first())) }
    val selected = games.indexOfFirst { key(it) == selectedKey }.takeIf { it >= 0 } ?: 0
    val select = { i: Int -> selectedKey = key(games[i.coerceIn(0, games.lastIndex)]) }
    val list = rememberLazyListState(selected)
    when (layout) {
        LibraryLayout.CARTRIDGES -> CartridgeRack(games, selected, select, showcase, key, list, m, small, modifier, headerUp)
        LibraryLayout.CAROUSEL -> CoverCarousel(games, selected, select, showcase, key, list, m, small, modifier, headerUp)
        else -> Spotlight(games, selected, select, showcase, key, list, m, small, modifier, headerUp)
    }
}

/** Keeps item [selected] in the middle of the row: the row's padding is half the room beside one item, so an item
 * scrolled to its start is in the middle. */
@Composable
private fun CenterOn(list: LazyListState, selected: Int, @Suppress("UNUSED_PARAMETER") itemPx: Float) {
    LaunchedEffect(selected) {
        if (list.layoutInfo.viewportSize.width <= 0) list.scrollToItem(selected) else list.animateScrollToItem(selected)
    }
}

/** A card of the row: focus (the D-pad) selects it, a tap selects it or plays it when it's in front, a long press opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> Modifier.showcaseCard(
    index: Int, selected: Boolean, game: T, showcase: LibraryShowcase<T>, select: (Int) -> Unit, openMenu: () -> Unit,
    headerUp: () -> FocusRequester?, play: () -> Unit = { showcase.play(game) },
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .focusProperties { headerUp()?.let { up = it } }
        .onFocusChanged { if (it.isFocused && !selected) select(index) }
        .combinedClickable(source, indication = null, onLongClick = openMenu) { if (selected) play() else select(index) }
}

// ---- CARTRIDGES ---------------------------------------------------------------------------------

@Composable
private fun <T> CartridgeRack(
    games: List<T>, selected: Int, select: (Int) -> Unit, showcase: LibraryShowcase<T>, key: (T) -> String,
    list: LazyListState, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier, headerUp: () -> FocusRequester?,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val fullW = maxWidth
        // A slot's width and the cartridges' height from the room: about five slots across, the rack in the lower half.
        // Upright: fewer, bigger slots (two and a bit across).
        val slotW = minOf(fullW / if (maxHeight > fullW) 2.4f else 3.8f, maxHeight * 0.5f)
        val cartH = slotW * 0.86f
        // A slot: the cart risen out of it at its tallest, and the socket.
        val rackH = cartH * 1.45f
        val itemPx = with(d) { slotW.toPx() }
        CenterOn(list, selected, itemPx)
        Column(Modifier.fillMaxSize()) {
            // The game in front: its name and facts over the rack.
            ShowcaseTitle(showcase, games[selected], m, small, Modifier.weight(1f).fillMaxWidth().padding(horizontal = libraryMargin), center = true)
            Box(Modifier.fillMaxWidth().height(rackH)) {
                // The rack's body behind the sockets: one long dark shelf.
                Canvas(Modifier.fillMaxWidth().height(rackH * SOCKET_SHARE).align(Alignment.BottomCenter)) { drawRack(this, modernView) }
                LazyRow(
                    state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = (fullW - slotW) / 2),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    itemsIndexed(games, key = { _, g -> key(g) }) { i, g ->
                        var menu by remember { mutableStateOf(false) }
                        var inserting by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        val front = i == selected
                        // Out of its socket when it's in front; pushed in (and played) by a tap on it.
                        val lift by animateFloatAsState(if (inserting) -0.12f else if (front) 0.34f else 0f, tween(if (inserting) 160 else 260), label = "cart-lift")
                        val cover = showcase.cover(g)
                        val shape = showcase.shape(g)
                        val category = shape
                        Box(
                            Modifier.width(slotW).height(rackH)
                                .showcaseCard(i, front, g, showcase, select, { menu = true }, headerUp, play = {
                                    inserting = true
                                    scope.launch { kotlinx.coroutines.delay(200); inserting = false; showcase.play(g) }
                                }),
                        ) {
                            // The cart's box in this slot (px): a share of it inside the socket, less when it's in front.
                            val boxW = with(d) { slotW.toPx() }
                            val boxH = with(d) { rackH.toPx() }
                            val socketH = boxH * SOCKET_SHARE
                            val cart = cartSize(shape, boxW * 0.8f, with(d) { cartH.toPx() })
                            val inset = 0.42f - lift
                            val cartRect = Rect(
                                (boxW - cart.width) / 2f, boxH - socketH - cart.height * (1f - inset),
                                (boxW + cart.width) / 2f, boxH - socketH + cart.height * inset,
                            )
                            val socket = Rect(boxW * 0.05f, boxH - socketH, boxW * 0.95f, boxH)
                            val label = labelRect(shape, cartRect)
                            Canvas(Modifier.fillMaxSize()) {
                                drawSocket(this, socket, back = true, modern = modernView)
                                if (front) drawCartShadow(this, cartRect)
                                drawCartShell(this, shape, cartRect, modernView)
                            }
                            // The label: the game's cover, or its console's tile while it has none.
                            Box(
                                Modifier.offset { IntOffset(label.left.roundToInt(), label.top.roundToInt()) }
                                    .size(with(d) { label.width.toDp() }, with(d) { label.height.toDp() })
                                    .background(Color(0xFFE8E8EE)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (cover != null) Canvas(Modifier.fillMaxSize()) { drawCropped(cover) }
                                else if (category != null) CategoryArt(category, Modifier.fillMaxSize(0.8f))
                            }
                            Canvas(Modifier.fillMaxSize()) {
                                drawLabelShine(this, label)
                                if (!front) drawCartShell(this, shape, cartRect, modernView, shadeOnly = true)
                                drawSocket(this, socket, back = false, modern = modernView)
                            }
                            showcase.menu(g, menu) { menu = false }
                        }
                    }
                }
            }
            Spacer(Modifier.height(m.u * 6))
        }
    }
}

/** A cartridge's outline for [shape] ([RomCategory]: the console it goes in) fitting [maxW] x [maxH], its own proportions. */
private fun cartSize(shape: RomCategory?, maxW: Float, maxH: Float): Size {
    val aspect = when (shape) {
        RomCategory.GB, RomCategory.GBC -> 0.88f
        RomCategory.NDS, RomCategory.N3DS -> 1.06f
        else -> 1.63f   // GBA and its hacks
    }
    val h = minOf(maxH, maxW / aspect)
    return Size(h * aspect, h)
}

/** The share of a rack slot's height its socket takes. */
private const val SOCKET_SHARE = 0.24f

/** A cartridge's colours: its shell, a light edge, a dark one (each console's kind of shell, never its maker's art or marks). */
private fun cartColors(shape: RomCategory?) = when (shape) {
    RomCategory.GB -> Triple(Color(0xFFB8B8C0), Color(0xFFD8D8E0), Color(0xFF8C8C96))
    RomCategory.GBC -> Triple(Color(0xFF4A4060), Color(0xFF6E6488), Color(0xFF2C2640))
    RomCategory.NDS, RomCategory.N3DS -> Triple(Color(0xFF3A3A42), Color(0xFF5A5A64), Color(0xFF222228))
    else -> Triple(Color(0xFF5E5E6A), Color(0xFF80808C), Color(0xFF3C3C46))
}

private fun isGameBoyCart(shape: RomCategory?) = shape == RomCategory.GB || shape == RomCategory.GBC

/** The cart's outline: a Game Boy cart's cut top right corner, a GBA / DS card's bevelled top corners. */
private fun cartPath(shape: RomCategory?, at: Rect, modern: Boolean) = Path().apply {
    val h = at.height; val w = at.width
    val r = if (modern) h * 0.07f else 0f
    if (isGameBoyCart(shape)) {
        val cut = w * 0.12f
        moveTo(at.left, at.top + r); lineTo(at.left, at.bottom - r); quadraticBezierTo(at.left, at.bottom, at.left + r, at.bottom)
        lineTo(at.right - r, at.bottom); quadraticBezierTo(at.right, at.bottom, at.right, at.bottom - r)
        lineTo(at.right, at.top + cut); lineTo(at.right - cut, at.top); lineTo(at.left + r, at.top)
        quadraticBezierTo(at.left, at.top, at.left, at.top + r); close()
    } else {
        val bevel = h * 0.08f
        moveTo(at.left, at.top + bevel); lineTo(at.left, at.bottom - r); quadraticBezierTo(at.left, at.bottom, at.left + r, at.bottom)
        lineTo(at.right - r, at.bottom); quadraticBezierTo(at.right, at.bottom, at.right, at.bottom - r)
        lineTo(at.right, at.top + bevel); lineTo(at.right - bevel, at.top); lineTo(at.left + bevel, at.top); close()
    }
}

/** Where a cart's label sits on it (the game's cover goes there). */
private fun labelRect(shape: RomCategory?, at: Rect): Rect {
    val w = at.width; val h = at.height
    return when {
        isGameBoyCart(shape) -> Rect(at.left + w * 0.11f, at.top + h * 0.12f, at.right - w * 0.11f, at.top + h * 0.7f)
        shape == RomCategory.NDS || shape == RomCategory.N3DS -> Rect(at.left + w * 0.1f, at.top + h * 0.24f, at.right - w * 0.1f, at.bottom - h * 0.08f)
        else -> Rect(at.left + w * 0.09f, at.top + h * 0.24f, at.right - w * 0.09f, at.bottom - h * 0.1f)
    }
}

/**
 * A cartridge of our own drawing: the shell in its console's kind of colour, moulded edges, grip ridges and the label's
 * recess (the label itself is drawn over it). [shadeOnly]: just the shade that sits over a cart not in front.
 */
private fun drawCartShell(s: DrawScope, shape: RomCategory?, at: Rect, modern: Boolean, shadeOnly: Boolean = false) = with(s) {
    val body = cartPath(shape, at, modern)
    if (shadeOnly) { drawPath(body, Color.Black.copy(alpha = 0.3f)); return@with }
    val (shell, light, dark) = cartColors(shape)
    val w = at.width; val h = at.height
    val px = maxOf(1f, h / 64f)
    drawPath(body, shell)
    drawPath(body, Brush.verticalGradient(listOf(light.copy(alpha = 0.55f), Color.Transparent, dark.copy(alpha = 0.5f)), at.top, at.bottom))
    drawPath(body, dark, style = Stroke(px * 1.5f))
    // Grip ridges: along the top on a GBA / DS card, along the bottom on a Game Boy cart.
    val ridgeTop = if (isGameBoyCart(shape)) at.bottom - h * 0.16f else at.top + h * 0.06f
    for (i in 0 until 3) {
        val y = ridgeTop + i * h * 0.035f
        drawLine(dark, Offset(at.left + w * 0.14f, y), Offset(at.right - w * 0.14f, y), px)
        drawLine(light.copy(alpha = 0.6f), Offset(at.left + w * 0.14f, y + px), Offset(at.right - w * 0.14f, y + px), px)
    }
    val label = labelRect(shape, at)
    drawRect(dark, label.topLeft - Offset(px * 1.5f, px * 1.5f), Size(label.width + 3 * px, label.height + 3 * px))
}

/** A shine across a label's top third. */
private fun drawLabelShine(s: DrawScope, label: Rect) = with(s) {
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent), label.top, label.top + label.height * 0.4f),
        label.topLeft, Size(label.width, label.height * 0.4f))
}

private fun drawCartShadow(s: DrawScope, at: Rect) = with(s) {
    drawOval(Color.Black.copy(alpha = 0.28f), Offset(at.left + at.width * 0.05f, at.bottom - at.height * 0.02f), Size(at.width * 0.9f, at.height * 0.12f))
}

/** A socket of the rack under a cartridge: its dark slot ([back]) and the lip in front that hides the cart's foot. */
private fun drawSocket(s: DrawScope, at: Rect, back: Boolean, modern: Boolean) = with(s) {
    val r = if (modern) at.height * 0.18f else 0f
    if (back) {
        drawRoundRect(Color(0xFF14161C), at.topLeft, at.size, androidx.compose.ui.geometry.CornerRadius(r, r))
        return@with
    }
    val lip = Rect(at.left, at.top + at.height * 0.08f, at.right, at.bottom)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A3E4A), Color(0xFF22252E)), lip.top, lip.bottom), lip.topLeft, lip.size,
        androidx.compose.ui.geometry.CornerRadius(r, r))
    drawLine(Color(0xFF5C6272), Offset(lip.left + r, lip.top), Offset(lip.right - r, lip.top), maxOf(1f, at.height / 24f))
}

private fun drawRack(s: DrawScope, modern: Boolean) = with(s) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2D36), Color(0xFF15171D))))
    drawLine(Color(0xFF454A58), Offset(0f, 0f), Offset(size.width, 0f), maxOf(1f, size.height / 40f))
    if (!modern) for (x in 0 until size.width.toInt() step maxOf(8, (size.height / 3f).toInt())) {
        drawLine(Color(0x22FFFFFF), Offset(x.toFloat(), size.height * 0.55f), Offset(x.toFloat() + size.height * 0.1f, size.height * 0.55f), 1f)
    }
}

// ---- CAROUSEL -----------------------------------------------------------------------------------

@Composable
private fun <T> CoverCarousel(
    games: List<T>, selected: Int, select: (Int) -> Unit, showcase: LibraryShowcase<T>, key: (T) -> String,
    list: LazyListState, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier, headerUp: () -> FocusRequester?,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val d = LocalDensity.current
        val fullW = maxWidth
        val coverW = minOf(maxHeight * 0.56f, fullW * 0.34f)
        val itemW = coverW * 0.78f
        val itemPx = with(d) { itemW.toPx() }
        CenterOn(list, selected, itemPx)
        // Behind: the game in front's art, soft and dim, crossfading as the front changes.
        ShowcaseBackdrop(showcase, games[selected], blurred = true, drift = false)
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(0.25f))
            LazyRow(
                state = list, modifier = Modifier.fillMaxWidth().height(coverW * 1.08f),
                contentPadding = PaddingValues(horizontal = (fullW - itemW) / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(games, key = { _, g -> key(g) }) { i, g ->
                    var menu by remember { mutableStateOf(false) }
                    val cover = showcase.cover(g)
                    Box(
                        Modifier.width(itemW).height(coverW)
                            .graphicsLayer {
                                // How far from the middle, in covers: turned and shrunk the further it is, like a flip-through.
                                val info = list.layoutInfo
                                val item = info.visibleItemsInfo.firstOrNull { it.index == i }
                                // Item offsets count from the row's content start (after its padding): the middle in those terms.
                                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val dist = if (item == null) 3f else ((item.offset + item.size / 2f) - mid) / itemPx
                                val ad = abs(dist).coerceAtMost(3f)
                                rotationY = (-dist * 38f).coerceIn(-62f, 62f)
                                val k = 1f - ad.coerceAtMost(2f) * 0.17f
                                scaleX = k; scaleY = k
                                alpha = 1f - ad * 0.18f
                                cameraDistance = 14f * density
                            }
                            .showcaseCard(i, i == selected, g, showcase, select, { menu = true }, headerUp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ShowcaseCover(cover, showcase.shape(g), Modifier.size(coverW), shadow = true)
                        showcase.menu(g, menu) { menu = false }
                    }
                }
            }
            Spacer(Modifier.height(m.u * 8))
            ShowcaseTitle(showcase, games[selected], m, small, Modifier.fillMaxWidth().padding(horizontal = libraryMargin), center = true, light = true)
            Spacer(Modifier.weight(0.4f))
        }
    }
}

// ---- SPOTLIGHT ----------------------------------------------------------------------------------

@Composable
private fun <T> Spotlight(
    games: List<T>, selected: Int, select: (Int) -> Unit, showcase: LibraryShowcase<T>, key: (T) -> String,
    list: LazyListState, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier, headerUp: () -> FocusRequester?,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val d = LocalDensity.current
        val fullW = maxWidth
        val tile = minOf(maxHeight * 0.24f, fullW / 5.5f)
        val gap = m.u * 6
        val itemPx = with(d) { (tile + gap).toPx() }
        CenterOn(list, selected, itemPx)
        val front = games[selected]
        // The game in front across the whole view, slowly drifting, darker toward the text.
        ShowcaseBackdrop(showcase, front, blurred = false, drift = true)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.15f)), 0f, size.width * 0.75f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), size.height * 0.45f, size.height))
        }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = libraryMargin), verticalArrangement = Arrangement.Center) {
                val logo = showcase.logo(front)
                if (logo != null) Canvas(Modifier.width(fullW * 0.36f).height(tile * 0.7f)) { drawFitted(logo) }
                else ShowcaseTitle(showcase, front, rememberGbaTextMetrics(2f), small, Modifier.fillMaxWidth(0.6f), center = false, light = true, factsBelow = false)
                Spacer(Modifier.height(m.u * 4))
                for (line in showcase.facts(front)) GbaText(line, Color.White, Color.Black.copy(alpha = 0.6f), small)
                Spacer(Modifier.height(m.u * 8))
                var menu by remember(selected) { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(m.u * 4), verticalAlignment = Alignment.CenterVertically) {
                    OptionButton(tr("PLAY"), m, onClick = { showcase.play(front) }, emphasis = true)
                    Box {
                        OptionButton(null, m, onClick = { menu = true }, icon = androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = tr("ROM options"))
                        showcase.menu(front, menu) { menu = false }
                    }
                }
            }
            LazyRow(
                state = list, modifier = Modifier.fillMaxWidth().height(tile * 1.25f),
                contentPadding = PaddingValues(horizontal = (fullW - tile) / 2),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(games, key = { _, g -> key(g) }) { i, g ->
                    var menu by remember { mutableStateOf(false) }
                    val cover = showcase.cover(g)
                    val on = i == selected
                    val lift by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "spot-lift")
                    Box(
                        Modifier.size(tile)
                            .graphicsLayer {
                                val k = 0.86f + 0.14f * lift
                                scaleX = k; scaleY = k
                                translationY = -lift * size.height * 0.08f
                                alpha = 0.7f + 0.3f * lift
                            }
                            .showcaseCard(i, on, g, showcase, select, { menu = true }, headerUp),
                    ) {
                        ShowcaseCover(cover, showcase.shape(g), Modifier.fillMaxSize(), ring = on)
                        showcase.menu(g, menu) { menu = false }
                    }
                }
            }
            Spacer(Modifier.height(m.u * 6))
        }
    }
}

/** A game's cover square, or its console's tile while it has none; a drop [shadow], a [ring] round the one in front. */
@Composable
private fun ShowcaseCover(cover: ImageBitmap?, shape: RomCategory?, modifier: Modifier, shadow: Boolean = false, ring: Boolean = false) {
    Box(modifier.drawBehind {
        if (shadow) drawRect(Color.Black.copy(alpha = 0.35f), Offset(size.width * 0.03f, size.height * 0.04f), size)
    }.background(Color(0xFFE8E8EE)).drawWithContent {
        drawContent()
        drawRect(if (ring) com.pokedaisy.app.companion.ui.theme.AppColors.focusRing else Color.White.copy(alpha = 0.5f),
            style = Stroke(if (ring) maxOf(2f, size.width / 30f) else maxOf(1f, size.width / 120f)))
    }, contentAlignment = Alignment.Center) {
        if (cover != null) Canvas(Modifier.fillMaxSize()) { drawCropped(cover) }
        else if (shape != null) CategoryArt(shape, Modifier.fillMaxSize(0.8f))
    }
}

/** [image] whole in the area, kept to its shape, at the left. */
private fun DrawScope.drawFitted(image: ImageBitmap) {
    val k = minOf(size.width / image.width, size.height / image.height)
    drawImage(image, dstOffset = IntOffset(0, ((size.height - image.height * k) / 2f).roundToInt()),
        dstSize = IntSize((image.width * k).roundToInt(), (image.height * k).roundToInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
}

/** The art behind the game in front (its backdrop, else its cover), crossfading as it changes; [drift]: a slow pan. */
@Composable
private fun <T> ShowcaseBackdrop(showcase: LibraryShowcase<T>, game: T, blurred: Boolean, drift: Boolean) {
    val art = showcase.backdrop(game) ?: showcase.cover(game)
    val move = if (drift) rememberInfiniteTransition(label = "spot-drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "spot-drift-k",
    ).value else 0f
    Crossfade(art, Modifier.fillMaxSize(), animationSpec = tween(420), label = "showcase-art") { image ->
        Box(Modifier.fillMaxSize().background(Color(0xFF101318))) {
            if (image != null) Canvas(
                Modifier.fillMaxSize()
                    .then(if (blurred) Modifier.blur(18.dp) else Modifier)
                    .graphicsLayer {
                        val k = 1.06f + 0.06f * move
                        scaleX = k; scaleY = k
                        translationX = (move - 0.5f) * size.width * 0.04f
                    },
            ) {
                drawCropped(image, scrim = if (blurred) 0.45f else 0.1f)
            }
        }
    }
}

/** The game in front's name (narrowed to fit) and, under it ([factsBelow]), its facts. [light]: over art (white text). */
@Composable
private fun <T> ShowcaseTitle(
    showcase: LibraryShowcase<T>, game: T, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier,
    center: Boolean, light: Boolean = false, factsBelow: Boolean = true,
) {
    val g = showcase.game(game)
    val ink = if (light) Color.White else OptionColors.onBackdrop
    val shade = if (light) Color.Black.copy(alpha = 0.6f) else OptionColors.onBackdropShadow
    Column(modifier, verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        GbaText(g.label, ink, shade, m, maxLines = 2)
        if (factsBelow) for (line in showcase.facts(game)) GbaText(line, ink, shade, small, maxLines = 1)
    }
}

