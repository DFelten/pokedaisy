package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.data.LibraryView
import com.pokedaisy.app.companion.i18n.tr
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing

/**
 * The library's quick views: a row of pills over the games, one per [LibraryView], each in its own colour - the
 * consoles and HACKS in their [CategoryArt] colours (GB grey, GBC yellow, GBA purple, DS turquoise, HACKS red), ALL
 * slate, FAVORITES the favorite star's gold, RECENT blue. The open one is filled with its colour and [CategoryArt]'s stripes, the
 * others a white pill framed in it. [counts]: how many games each view holds, shown after its name.
 */
@Composable
fun QuickViewPills(
    views: List<LibraryView>,
    selected: LibraryView,
    counts: Map<LibraryView, Int>,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onPick: (LibraryView) -> Unit,
    modifier: Modifier = Modifier,
    /** Inside the scroll, so a pill slides off the screen's edge rather than an inner box's. */
    contentPadding: PaddingValues = PaddingValues(),
    /** Put on the open pill: where D-pad UP from the games' top row lands. */
    focusRequester: FocusRequester? = null,
    /** The launcher's cursor is on the row (its own, not Compose focus): the open pill gets the focus ring. */
    cursor: Boolean = false,
    /** A long press on a pill, then a drag, moves it along the row; the new order when it's let go (null: fixed order). */
    onReorder: ((List<LibraryView>) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    // Each pill's left and right edge in the row's content (inside its padding).
    val edges = remember { mutableStateMapOf<LibraryView, IntRange>() }
    // The open pill scrolled into view (a phone's row is wider than the screen) when it's picked or the library
    // opens on it: just far enough, a pill's gap to spare.
    val density = LocalDensity.current
    val gap = with(density) { (m.u * 4).roundToPx() }
    // The edges are the Row's; the scroll's content starts at its padding.
    val start = with(density) { contentPadding.calculateLeftPadding(LayoutDirection.Ltr).roundToPx() }
    val open = edges[selected]?.let { (it.first + start)..(it.last + start) }
    LaunchedEffect(selected, open, viewport) {
        if (open == null || viewport == 0) return@LaunchedEffect
        val target = when {
            open.first - gap < scroll.value -> open.first - gap
            open.last + gap > scroll.value + viewport -> open.last + gap - viewport
            else -> return@LaunchedEffect
        }
        scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
    }
    // The order while a pill is dragged (the views' own otherwise), the dragged one and how far it's moved from its place.
    var order by remember(views) { androidx.compose.runtime.mutableStateOf(views) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf<LibraryView?>(null) }
    var dragX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var fingerX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var settledAt by remember { androidx.compose.runtime.mutableFloatStateOf(Float.NaN) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // The drag's callbacks outlive recompositions: they read the newest views and callback, never a stale copy.
    val currentViews by androidx.compose.runtime.rememberUpdatedState(views)
    val currentReorder by androidx.compose.runtime.rememberUpdatedState(onReorder)
    Row(
        modifier.onSizeChanged { viewport = it.width }.horizontalScroll(scroll, enabled = dragging == null).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(m.u * 4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each pill keyed by its view, so a swap mid-drag moves the pill (and its running gesture) along, rather than
        // handing its place to the neighbour - which cancelled the drag and snapped it back.
        for (view in order) androidx.compose.runtime.key(view) {
            val on = view == selected
            val lifted = dragging == view
            val drag = if (onReorder == null) Modifier else Modifier.pointerInput(view) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = view
                        val me = edges[view]
                        fingerX = me?.let { (it.first + it.last) / 2f } ?: 0f
                        settledAt = Float.NaN
                        dragX = 0f
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        // Where the finger holds the pill's middle, in the row; the pill drawn there from wherever the
                        // layout has it now.
                        fingerX += amount.x
                        val me = edges[view] ?: return@detectDragGesturesAfterLongPress
                        val mid = (me.first + me.last) / 2f
                        dragX = fingerX - mid
                        // Just swapped and not laid out again yet: the neighbours' places are a frame old.
                        if (mid == settledAt) return@detectDragGesturesAfterLongPress
                        val i = order.indexOf(view)
                        val next = order.getOrNull(i + 1)?.let { edges[it] }
                        val prev = order.getOrNull(i - 1)?.let { edges[it] }
                        if (next != null && fingerX > (next.first + next.last) / 2f) {
                            order = order.toMutableList().apply { removeAt(i); add(i + 1, view) }
                            settledAt = mid
                        } else if (prev != null && fingerX < (prev.first + prev.last) / 2f) {
                            order = order.toMutableList().apply { removeAt(i); add(i - 1, view) }
                            settledAt = mid
                        }
                    },
                    onDragEnd = { dragging = null; dragX = 0f; if (order != currentViews) currentReorder?.invoke(order) },
                    onDragCancel = { dragging = null; dragX = 0f; order = currentViews },
                )
            }
            QuickViewPill(
                view, on, counts[view], m, small, cursor = on && cursor,
                Modifier.onPlaced { c -> c.parentLayoutCoordinates?.let { row ->
                    val x = row.localPositionOf(c, androidx.compose.ui.geometry.Offset.Zero).x.toInt()
                    edges[view] = x..(x + c.size.width)
                } }.then(if (on && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (lifted) edges[view]?.let { fingerX - (it.first + it.last) / 2f } ?: dragX else 0f
                        val k = if (lifted) 1.08f else 1f
                        scaleX = k; scaleY = k
                        shadowElevation = if (lifted) 6.dp.toPx() else 0f
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                    }
                    .then(drag),
            ) { onPick(view) }
        }
    }
}

@Composable
private fun QuickViewPill(
    view: LibraryView, on: Boolean, count: Int?, m: GbaTextMetrics, small: GbaTextMetrics, cursor: Boolean, modifier: Modifier,
    onClick: () -> Unit,
) {
    val look = QuickViewLooks.of(view)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val u = m.u
    if (modernView) { ModernQuickViewPill(view, look, on, count, m, small, focused || cursor, interaction, modifier, onClick); return }
    val dark = OptionColors.dark
    val text = if (on) Color.White else if (dark) OptionColors.titleText else look.shadow
    val shadow = if (on) look.shadow else OptionColors.titleShadow
    val fill = OptionColors.titleFill
    Row(
        modifier
            .gbaFocusRing(focused || cursor, PixelRoundedShape(u * PILL_RADIUS))
            .semantics { selected = on }
            .keyDrivenFocus()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .drawBehind {
                val px = u.toPx()
                val r = PILL_RADIUS * px
                if (on) {
                    // CategoryArt's backdrop: two shades in diagonal stripes, a cell of 2 GBA pixels.
                    drawPixelRoundRect(look.back, radius = r)
                    clipPath(pixelRoundRectPath(size, r, px)) {
                        val cell = 2 * px
                        val cols = kotlin.math.ceil(size.width / cell).toInt()
                        val rows = kotlin.math.ceil(size.height / cell).toInt()
                        for (y in 0 until rows) for (x in 0 until cols) {
                            if ((x + y) % 4 < 1) drawRect(look.stripe, Offset(x * cell, y * cell), Size(cell, cell))
                        }
                    }
                    drawPixelRoundFrame(look.shadow, Offset.Zero, size, r, 2 * px)
                } else {
                    drawPixelRoundRect(fill, radius = r)
                    drawPixelRoundFrame(look.back, Offset.Zero, size, r, 3 * px)
                }
            }
            // Lower and narrower on a short screen (the RG DS), where every row of height counts.
            .padding(horizontal = u * (if (LocalShortScreen.current) 5 else 7), vertical = u * (if (LocalShortScreen.current) 2 else 5)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (view) {
            LibraryView.FAVORITES -> PixelIcons.star
            LibraryView.RECENT -> PixelIcons.clock
            else -> null
        }
        if (icon != null) {
            PixelIcon(icon, if (on) Color.White else look.back, Modifier.size(small.lineHeight))
            Spacer(Modifier.width(u * 3))
        }
        // The list's smaller text (the paths'): the rows' size made the row too loud on a big screen.
        GbaText(quickViewLabel(view), text, shadow, small)
        if (count != null) {
            Spacer(Modifier.width(u * 3))
            GbaText(count.toString(), text.copy(alpha = 0.7f), Color.Transparent, small)
        }
    }
}

/** [view]'s name in the app's language (literal keys, for the translation check); the consoles' stay as they are. */
fun quickViewLabel(view: LibraryView): String = when (view) {
    LibraryView.ALL -> tr("ALL")
    LibraryView.FAVORITES -> tr("FAVORITES")
    LibraryView.RECENT -> tr("RECENT")
    LibraryView.HACKS -> tr("HACKS")
    else -> view.label
}

/** A pill's corner, in GBA pixels: a rounded box rather than a full pill. */
private const val PILL_RADIUS = 5

/** Each quick view's colours: [CategoryLook]s, the consoles' being [CategoryLooks]' own. */
object QuickViewLooks {
    private fun c(rgb: Long) = Color(0xFF000000 or rgb)

    private val ALL = CategoryLook(c(0x4A5670), c(0x404B63), c(0xFFFFFF), c(0x1C2233))
    private val FAVORITES = CategoryLook(c(0xE0A010), c(0xCC9008), c(0xFFFFFF), c(0x6E4A00))
    private val RECENT = CategoryLook(c(0x2F7FD8), c(0x2670C4), c(0xFFFFFF), c(0x0C315E))

    fun of(view: LibraryView): CategoryLook = when (view) {
        LibraryView.ALL -> ALL
        LibraryView.FAVORITES -> FAVORITES
        LibraryView.RECENT -> RECENT
        else -> CategoryLooks.of(view.platform ?: view.category!!)
    }
}

/**
 * VIEW MODERN's pill: a full pill, the open one filled with its colour top to bottom and white words, the others white
 * on a soft shadow with a dot (or the star / clock) in their colour and the count in a little bubble.
 */
@Composable
private fun ModernQuickViewPill(
    view: LibraryView, look: CategoryLook, on: Boolean, count: Int?, m: GbaTextMetrics, small: GbaTextMetrics, focused: Boolean,
    interaction: MutableInteractionSource, modifier: Modifier, onClick: () -> Unit,
) {
    val u = m.u
    val dark = OptionColors.dark
    val ink = if (on) Color.White else OptionColors.label
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Row(
        modifier
            .gbaFocusRing(focused, shape)
            .semantics { selected = on }
            .keyDrivenFocus()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .drawBehind {
                val r = size.height / 2f
                CompanionSkins.MODERN.dropShadow?.let { drawSoftShadow(it, r) }
                if (on) drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(look.back, Color.White, 0.18f), look.back)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                ) else {
                    drawRoundRect(OptionColors.titleFill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                    drawRoundRect(
                        OptionColors.titleLayers.first().first, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
            }
            .padding(horizontal = u * 8, vertical = u * 4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glyph = when (view) {
            LibraryView.FAVORITES -> ModernGlyph.STAR
            LibraryView.RECENT -> ModernGlyph.STATES
            else -> null
        }
        val mark = if (on) Color.White else look.back.let { if (dark) androidx.compose.ui.graphics.lerp(it, Color.White, 0.25f) else it }
        if (glyph != null) ModernIcon(glyph, mark, Modifier.size(small.lineHeight * 1.1f))
        else androidx.compose.foundation.Canvas(Modifier.size(small.lineHeight * 0.55f)) { drawCircle(mark) }
        Spacer(Modifier.width(u * 4))
        GbaText(quickViewLabel(view), ink, Color.Transparent, small, bold = true)
        if (count != null) {
            Spacer(Modifier.width(u * 4))
            androidx.compose.foundation.layout.Box(
                Modifier.drawBehind {
                    drawRoundRect(
                        if (on) Color.White.copy(alpha = 0.25f) else OptionColors.well,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                    )
                }.padding(horizontal = u * 4, vertical = u * 0.5f),
            ) {
                GbaText(count.toString(), if (on) Color.White else OptionColors.muted, Color.Transparent, small)
            }
        }
    }
}
