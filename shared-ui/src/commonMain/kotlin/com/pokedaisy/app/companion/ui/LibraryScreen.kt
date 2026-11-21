package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import com.pokedaisy.app.companion.data.LibraryView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.data.RomCategory
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing

enum class LibrarySupport { FULL, PARTIAL, UNSUPPORTED, BLOCKED }

/** Data the library draws. Native files, permissions, import and launching belong to its host. */
data class LibraryGame(
    val id: String, val label: String, val path: String = id,
    val playing: Boolean = false, val isNew: Boolean = false, val hasSave: Boolean = false,
    val favorite: Boolean = false, val support: LibrarySupport = LibrarySupport.FULL,
)

/**
 * The dual-screen launcher drives its own cursors from the keys (LibraryActivity): under it the library's cards, rail,
 * pills and header buttons take no Compose focus, else the D-pad leaving touch mode put Compose's ring on the first one
 * (GAMES) beside the launcher's cursor - two markers at once.
 */
val LocalKeyDriven = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Not a Compose focus stop under [LocalKeyDriven]. */
@Composable
fun Modifier.keyDrivenFocus(): Modifier = if (LocalKeyDriven.current) focusProperties { canFocus = false } else this

object LibraryBadgeColors {
    val blocked = Color(0xFF202020)
    val blockedShadow = Color(0x99000000)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LibraryCard(
    selected: Boolean,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Where D-pad UP goes from the top row, if there is no card above it. */
    upTo: () -> FocusRequester? = { null },
    /** The launcher's cursor is on it (the launcher moves its own, not Compose focus): ring and white fill. */
    cursor: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val here = remember { FocusRequester() }
    val hasFocus by interaction.collectIsFocusedAsState()
    val focused = hasFocus || cursor
    // RETRO's list: one long window, a row a part of it, a line between rows (not a box per game: the user's call).
    val segment = LocalListSegment.current
    if (segment != null && !modernView) {
        val u = m.u
        val layers = OptionColors.listLayers
        val frame = layers.sumOf { it.second }
        Box(
            modifier
                .gbaFocusRing(focused, PixelRoundedShape(m.u * 2))
                .focusProperties { upTo()?.let { up = it } }
                .keyDrivenFocus()
                .drawBehind {
                    val px = u.toPx()
                    drawLayeredBox(
                        layers.inPx(px), if (selected || focused) OptionColors.rowSelected
                        else if (segment.index % 2 == 1) OptionColors.style?.stripe?.let { OptionColors.tint(it) } ?: OptionColors.listFill
                        else OptionColors.listFill,
                        radius = 2 * px, openTop = !segment.first, openBottom = !segment.last,
                    )
                    if (!segment.last) {
                        val inset = (frame + 2) * px
                        drawRect(OptionColors.divider, Offset(inset, size.height - px), androidx.compose.ui.geometry.Size(size.width - 2 * inset, px))
                    }
                }
                .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
                .padding(
                    start = u * (frame + 2), end = u * (frame + 2),
                    top = u * (if (segment.first) frame + 2 else 4), bottom = u * (if (segment.last) frame + 2 else 4),
                ),
        ) { content() }
        return
    }
    OptionListWindow(
        m,
        modifier
            // The list window's own corner (OptionListWindow draws its frame at a 2u radius).
            .gbaFocusRing(focused, PixelRoundedShape(m.u * 2))
            .focusProperties { upTo()?.let { up = it } }
            .keyDrivenFocus()
            // A tap leaves the D-pad's next press starting here ([FocusJump.from]).
            .focusRequester(here)
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick?.let { l -> { FocusJump.from = here; l() } },
                onClick = { FocusJump.from = here; onClick() }),
        fill = if (selected || focused) OptionColors.rowSelected else OptionColors.listFill,
    ) { content() }
}

@Composable
private fun LibraryFavoriteStar(size: Dp) {
    PixelIcon(PixelIcons.star, OptionColors.favorite,
        Modifier.size(size).semantics { contentDescription = tr("Favorite") })
}

@Composable
fun LibraryRomRow(
    game: LibraryGame, m: GbaTextMetrics, small: GbaTextMetrics,
    selected: Boolean = game.playing, cursor: Boolean = false, upTo: () -> FocusRequester? = { null },
    /** The grey line under the name (a file name or path), or null for none. */
    path: String? = game.path,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    cover: @Composable (Modifier) -> Unit, menu: @Composable () -> Unit,
) {
    val isLast = game.playing
    val isNew = game.isNew
    val display = game.label
    val hasSave = game.hasSave
    val favorite = game.favorite
    val short = LocalShortScreen.current
    LibraryCard(
        // In the launcher only its cursor gets the white fill (two white rows read as two cursors).
        selected, m, Modifier.fillMaxWidth().semantics { contentDescription = display },
        onLongClick = onLongClick, upTo = upTo,
        cursor = cursor,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            cover(Modifier.height(if (short) 32.dp else 56.dp))
            Spacer(Modifier.width(m.u * if (short) 4 else 6))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GbaText(display, OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f, fill = false))
                    if (favorite) {
                        Spacer(Modifier.width(m.u * 4))
                        LibraryFavoriteStar(small.lineHeight)
                    }
                    if (hasSave) {
                        Spacer(Modifier.width(m.u * 4))
                        Icon(
                            LibraryIcons.Save, contentDescription = tr("Save file exists"),
                            tint = OptionColors.label, modifier = Modifier.size(small.lineHeight),
                        )
                    }
                    if (isLast) {
                        Spacer(Modifier.width(m.u * 4))
                        GbaText(tr("PLAYING"), OptionColors.value, OptionColors.valueShadow, small)
                    }
                    if (isNew) {
                        Spacer(Modifier.width(m.u * 4))
                        OptionBadge(tk("NEW"), m, fill = OptionColors.newBadge)
                    }
                    if (game.support == LibrarySupport.BLOCKED) {
                        Spacer(Modifier.width(m.u * 4))
                        OptionBadge(tk("BLOCKED"), m, fill = LibraryBadgeColors.blocked)
                    } else if (game.support == LibrarySupport.UNSUPPORTED) {
                        Spacer(Modifier.width(m.u * 4))
                        OptionBadge(tk("NOT SUPPORTED"), m)
                    } else if (game.support == LibrarySupport.PARTIAL) {
                        Spacer(Modifier.width(m.u * 4))
                        OptionBadge(tk("PARTIALLY SUPPORTED"), m, fill = OptionColors.muted)
                    }
                }
                // A short screen's rows are one line: the name alone (the path is in INFO).
                if (path != null && !short) {
                    GbaText(
                        path,
                        OptionColors.muted, OptionColors.mutedShadow, small,
                    )
                }
            }
            menu()
        }
    }
}


@Composable
fun LibraryRomTile(
    game: LibraryGame, m: GbaTextMetrics, small: GbaTextMetrics,
    selected: Boolean = game.playing, cursor: Boolean = false, upTo: () -> FocusRequester? = { null },
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    cover: @Composable (Modifier) -> Unit, menu: @Composable () -> Unit,
) {
    val isNew = game.isNew
    val display = game.label
    val hasSave = game.hasSave
    val favorite = game.favorite
    Box {
        LibraryCard(
            selected, m, Modifier.fillMaxWidth().semantics { contentDescription = display },
            onLongClick = onLongClick,
            upTo = upTo,
            cursor = cursor,
            onClick = onClick,
        ) {
            val support = when (game.support) {
                LibrarySupport.BLOCKED -> tk("BLOCKED") to LibraryBadgeColors.blocked
                LibrarySupport.UNSUPPORTED -> tk("NOT SUPPORTED") to OptionColors.value
                LibrarySupport.PARTIAL -> tk("PARTIALLY SUPPORTED") to OptionColors.muted
                else -> null
            }
            Column {
                // On the cover, inside its frame, so nothing takes a line of its own: the favorite's star and the save's disk
                // together on one small window in the top-left corner, the tags (NEW, BLOCKED...) along the bottom.
                Box {
                    cover(Modifier.fillMaxWidth())
                    if (favorite || hasSave) {
                        val shape = if (modernView) RoundedCornerShape(m.u * 4) else PixelRoundedShape(m.u * 2)
                        Row(
                            Modifier.align(Alignment.TopStart).padding(m.u * 3)
                                .background(OptionColors.titleFill.copy(alpha = 0.92f), shape)
                                .padding(horizontal = m.u * 2.5f, vertical = m.u * 1.5f),
                            horizontalArrangement = Arrangement.spacedBy(m.u * 2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (favorite) LibraryFavoriteStar(small.lineHeight)
                            if (hasSave) Icon(
                                LibraryIcons.Save, contentDescription = tr("Save file exists"),
                                tint = OptionColors.label, modifier = Modifier.size(small.lineHeight * 0.95f),
                            )
                        }
                    }
                    if (isNew || support != null) {
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(m.u * 3),
                            horizontalArrangement = Arrangement.spacedBy(m.u * 2),
                        ) {
                            if (isNew) OptionBadge(tk("NEW"), m, fill = OptionColors.newBadge)
                            support?.let { (label, fill) -> OptionBadge(label, m, fill = fill) }
                        }
                    }
                }
                Spacer(Modifier.height(m.u * 3))
                GbaText(display, OptionColors.label, OptionColors.labelShadow, small, Modifier.fillMaxWidth())
            }
        }
        menu()
    }
}


@Composable
fun LibraryCover(bitmap: ImageBitmap?, category: RomCategory?, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    val u = m.u
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(PixelRoundedShape(u * 3))
            .background(OptionColors.imageWell)
            .drawWithContent {
                drawContent()
                val px = u.toPx()
                drawLayeredFrame(listOf(OptionColors.frameDark to px, OptionColors.frameLight to px), radius = 3 * px)
            },
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
            )
        } else {
            // No art: the game's kind (GB / GBC / GBA / NDS / HACK) as a little pixel picture, once the background
            // title pass has looked at the file (GameTitles.identify); the plain icon until then.
            if (category != null) {
                CategoryArt(category, Modifier.fillMaxSize())
            } else {
                Icon(
                    LibraryIcons.VideogameAsset, contentDescription = null,
                    tint = OptionColors.muted, modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** The library's margin round its pills and games: half as much on a short screen ([LocalShortScreen]). */
val libraryMargin: Dp @Composable get() = if (LocalShortScreen.current) 8.dp else 16.dp

/** A grid tile's least width: smaller on a short screen, where 150 dp left one tile the width of the page (three across
 * on the RG DS; four cut every name to its first letters). */
val libraryTileMin: Dp @Composable get() = if (LocalShortScreen.current) 112.dp else 150.dp

/** The library's existing header and GAMES / TOOLS / SETTINGS rail, with platform work supplied as content/actions. */
@Composable
fun LibraryFrame(
    section: LibrarySection, m: GbaTextMetrics, small: GbaTextMetrics,
    onSection: (LibrarySection) -> Unit, modifier: Modifier = Modifier,
    toolsBadge: Boolean = false, compactTitle: Boolean = false, headerGap: Dp = m.u * 8, showHeader: Boolean = true,
    header: @Composable RowScope.() -> Unit,
    /** [header] for when it would cut the title short ([LibraryHeaderBar]'s compactActions). */
    compactHeader: (@Composable RowScope.() -> Unit)? = null,
    overlays: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= maxHeight
        AppBackdrop(logos = true)
        Row(Modifier.fillMaxSize()) {
            if (wide) LibraryNav(section, vertical = true, m, small, toolsBadge = toolsBadge, top = libraryMargin, onPick = onSection)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (showHeader) {
                    LibraryHeaderBar(if (compactTitle) "" else "POKéDAISY", m, compactActions = compactHeader, actions = header)
                    Spacer(Modifier.height(headerGap))
                }
                content()
                if (!wide) LibraryNav(section, vertical = false, m, small, toolsBadge = toolsBadge, onPick = onSection)
            }
        }
        overlays()
    }
}

/**
 * The library's / TOOLS' / Settings' / the tools' header: a band along the top of the screen, its full width (the user's
 * call, 2026-11-22: docked to the edge, not a window floating in the page), the title window's colours with its frame
 * along the bottom only (VIEW MODERN: its blue, a soft shadow under it). The logo (or [leading]) and [title] on the left,
 * [actions] on the right.
 */
@Composable
fun LibraryHeaderBar(
    title: String, m: GbaTextMetrics, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** The PokeDaisy flower before the title (the library's, Settings' HOME); a sub-page has its back arrow alone. */
    logo: Boolean = true,
    /** Drawn before the title in the logo's place (a tool's own icon: [ToolHeader]). */
    leading: (@Composable () -> Unit)? = null,
    /** [actions] for when they'd cut the title short (the user's call: fewer, narrower buttons, the whole name kept). */
    compactActions: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val u = m.u
    val short = LocalShortScreen.current
    val sidePad = if (short) 8.dp else 12.dp
    val endPad = if (short) 8.dp else 16.dp
    // Whether [title] fits beside the full [actions]: their width is taken whenever they're the ones shown, so switching
    // to the compact ones (narrower) never flips it back.
    var barWidth by remember { mutableIntStateOf(0) }
    var fullActions by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val font = com.pokedaisy.app.companion.ui.theme.pixelFontFamily()
    val shownTitle = if (title.isEmpty()) "" else tr(title)
    val titleWidth = remember(shownTitle, m, font) {
        if (shownTitle.isEmpty()) 0 else measurer.measure(shownTitle, gbaTextStyle(font, m)).size.width + m.fontPixel.toInt()
    }
    val fixed = with(density) {
        (sidePad + endPad + (if (onBack != null) m.lineHeight + u * 6 else 0.dp) + (if (logo) m.lineHeight + u * 6 else 0.dp)).roundToPx()
    }
    val compact = compactActions != null && barWidth > 0 && fullActions > 0 && fixed + titleWidth + fullActions > barWidth
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { barWidth = it.width }
            .drawBehind {
                val px = m.u.toPx()
                if (modernView) {
                    // A soft shadow under it, its blue top to bottom.
                    for (i in 1..4) drawRect(Color(0x101B2A55), Offset(0f, size.height), androidx.compose.ui.geometry.Size(size.width, i * px * 0.8f))
                    val fill = OptionColors.headerFill
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(fill, Color.White, 0.14f), fill)))
                } else drawLayeredBox(
                    OptionColors.headerLayers.inPx(px), OptionColors.headerFill, radius = 0f,
                    openTop = true, openLeft = true, openRight = true,
                )
            }
            .padding(start = sidePad, end = endPad, top = u * (if (short) 3 else 7), bottom = u * (if (short) 3 else 7)),
    ) {
        if (onBack != null) {
            CompanionBackHandler(onBack = onBack)
            // Not a D-pad stop: B / BACK is the same thing.
            Box(Modifier.focusProperties { canFocus = false }.soundClickable(onClick = onBack).padding(end = u * 6)) {
                if (modernView) ModernIcon(ModernGlyph.BACK, OptionColors.headerText, Modifier.size(m.lineHeight * 1.2f))
                else {
                    val cell = with(androidx.compose.ui.platform.LocalDensity.current) { m.fontPixel.toDp() }
                    Box(Modifier.size(m.lineHeight).semantics { contentDescription = tr("Back") }, contentAlignment = Alignment.Center) {
                        ShadowedPixelIcon(PixelIcons.arrowBack, OptionColors.headerText, OptionColors.headerShadow, m, cell = cell)
                    }
                }
            }
        }
        if (logo) {
            AppLogo(Modifier.size(m.lineHeight * if (modernView) 1.25f else 1f))
            Spacer(Modifier.width(u * 6))
        }
        leading?.let { it(); Spacer(Modifier.width(u * 6)) }
        Box(Modifier.weight(1f)) {
            if (title.isNotEmpty()) GbaText(shownTitle, OptionColors.headerText, OptionColors.headerShadow, m, bold = modernView)
        }
        Row(Modifier.onSizeChanged { if (!compact) fullActions = it.width }, verticalAlignment = Alignment.CenterVertically) {
            if (compact) compactActions?.invoke(this) else actions()
        }
    }
}

@Composable
fun LibraryHeaderIcon(icon: ImageVector, label: String, m: GbaTextMetrics, modifier: Modifier = Modifier,
                      compact: Boolean = false, cursor: Boolean = false, onClick: () -> Unit) {
    // A short screen's header is lower: narrow buttons, less room over and under their icons.
    val short = LocalShortScreen.current
    val narrow = compact || short
    Spacer(Modifier.width(m.u * if (narrow) 2 else 4))
    if (modernView) {
        // A round see-through button on the blue band, the icon white.
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val circle = m.rowHeight * if (short) 0.9f else 1.05f
        Box(
            modifier.size(circle)
                .gbaFocusRing(cursor || focused, androidx.compose.foundation.shape.CircleShape)
                .drawBehind { drawCircle(Color.White.copy(alpha = if (cursor || focused) 0.32f else 0.16f)) }
                .keyDrivenFocus()
                .soundClickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = OptionColors.headerText, modifier = Modifier.size(circle * 0.56f)) }
        return
    }
    OptionButton(null, m, onClick = onClick, modifier = modifier.keyDrivenFocus().gbaFocusRing(cursor, PixelRoundedShape(m.u * 3)),
        icon = icon, contentDescription = label, sidePadding = if (narrow) 4 else 8, verticalPadding = if (short) 2 else 4)
}

/** A host's available game actions, drawn by one shared menu. [opens] lets a launcher retain its cursor for a page. */
class LibraryMenuItem(
    val label: String, val icon: ImageVector, val danger: Boolean = false, val opens: Boolean = false,
    val pixel: List<String>? = null, val onClick: () -> Unit,
)

private val LibraryMenuBorder get() = if (OptionColors.dark) Color(0xFF4A5262) else Color(0xFFA8A8B0)

@Composable
fun LibraryGameMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<LibraryMenuItem>, m: GbaTextMetrics, screenSize: DpSize) {
    @Composable
    fun MenuItem(item: LibraryMenuItem, odd: Boolean = items.indexOf(item) % 2 == 1) {
        val fg = if (item.danger) OptionColors.value else OptionColors.label
        DropdownMenuItem(
            // A step smaller than the list's text, in roomier rows (the user's call).
            text = { GbaText(item.label, fg, if (item.danger) OptionColors.valueShadow else OptionColors.labelShadow, rememberGbaTextMetrics(1f)) },
            // Every other row banded, so a long menu is easy to follow across.
            modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 240.dp)
                .then(if (odd) Modifier.background(OptionColors.rowStripe) else Modifier),
            leadingIcon = {
                val px = item.pixel
                if (px != null) com.pokedaisy.app.companion.ui.PixelIcon(px, fg, Modifier.size(24.dp)) else Icon(item.icon, null, tint = fg)
            },
            onClick = { onDismiss(); item.onClick() },
        )
    }
    // Two columns where they fit across the screen and one would run off it, or fill it: the list had grown past a
    // landscape screen's height, and held upright it took the whole screen (the user's call).
    val itemHeight = maxOf(48.dp, m.lineHeight + 16.dp)
    // Upright the columns share the screen's width (a phone has no room for two of 280.dp).
    val upright = screenSize.height > screenSize.width && screenSize.width >= 320.dp
    val twoColumns = upright || (
        screenSize.width >= 280.dp * 2 + 32.dp && itemHeight * items.size + 16.dp > screenSize.height - 32.dp
        )
    // Square corners on the popup's own surface (its shape must be a
    // CornerBasedShape - no pixel steps possible), and a subtle 1u frame
    // just inside it rather than Material's smooth-rounded edge.
    val u = m.u
    MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(0.dp))) {
        DropdownMenu(
            expanded = expanded, onDismissRequest = onDismiss,
            modifier = Modifier
                .background(OptionColors.titleFill)
                .drawWithContent {
                    drawContent()
                    drawLayeredFrame(listOf(LibraryMenuBorder to u.toPx()), radius = 0f)
                },
        ) {
            if (!twoColumns) {
                items.forEach { MenuItem(it) }
            } else {
                // Down the first column, then the second (DELETE last, bottom right); a 1u rule between.
                val half = (items.size + 1) / 2
                val rowWidth = if (upright) Modifier.width(screenSize.width - 32.dp) else Modifier
                Row(rowWidth.height(IntrinsicSize.Min)) {
                    val column = if (upright) Modifier.weight(1f) else Modifier.width(IntrinsicSize.Max)
                    // Banded like a chess board: a row's band flips from one column to the next.
                    Column(column) { items.take(half).forEachIndexed { i, it -> MenuItem(it, odd = i % 2 == 1) } }
                    Box(Modifier.fillMaxHeight().padding(vertical = u * 4).width(u).background(LibraryMenuBorder))
                    Column(column) { items.drop(half).forEachIndexed { i, it -> MenuItem(it, odd = i % 2 == 0) } }
                }
            }
        }
    }
}


@Composable
fun LibraryMenuDots(m: GbaTextMetrics, onClick: () -> Unit) {
    Box(Modifier.clip(PixelRoundedShape(m.u * 3)).keyDrivenFocus().clickable(onClick = onClick).padding(m.u * 3)) {
        Icon(Icons.Filled.MoreVert, contentDescription = tr("ROM options"), tint = OptionColors.label,
            modifier = Modifier.size(m.lineHeight))
    }
}

@Composable
fun libraryLongPress(open: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { haptic.performHapticFeedback(HapticFeedbackType.LongPress); open() }
}

/** Where a row sits in RETRO's one long library list ([LibraryCard]): its place and whether it starts / ends the window. */
class ListSegment(val index: Int, val first: Boolean, val last: Boolean)

/** Set round each row of a library list (null: a card of its own, as in the grid and in MODERN). */
val LocalListSegment = androidx.compose.runtime.compositionLocalOf<ListSegment?> { null }

/** The same list/grid spacing, stable keys and UP-to-header routing on every host. Items keep their native identity. */
@Composable
fun <T> LibraryCollection(
    games: List<T>, grid: Boolean, m: GbaTextMetrics, key: (T) -> String,
    modifier: Modifier = Modifier, headerUp: () -> FocusRequester? = { null },
    item: @Composable (T, () -> FocusRequester?) -> Unit,
) {
    val margin = libraryMargin
    val padding = PaddingValues(start = margin, end = margin, bottom = margin)
    val gap = Arrangement.spacedBy(m.u * if (LocalShortScreen.current) 3 else 4)
    if (grid) {
        val state = rememberLazyGridState()
        LazyVerticalGrid(GridCells.Adaptive(libraryTileMin), state = state, modifier = modifier.fillMaxWidth(),
            contentPadding = padding, horizontalArrangement = gap, verticalArrangement = gap) {
            gridItemsIndexed(games, key = { _, game -> key(game) }) { index, game ->
                item(game) { headerUp()?.takeIf { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.row == 0 } }
            }
        }
    } else {
        // RETRO: the rows one window, no gaps; MODERN: cards apart.
        LazyColumn(modifier.fillMaxWidth(), contentPadding = padding, verticalArrangement = if (modernView) gap else Arrangement.Top) {
            itemsIndexed(games, key = { _, game -> key(game) }) { index, game ->
                androidx.compose.runtime.CompositionLocalProvider(LocalListSegment provides ListSegment(index, index == 0, index == games.lastIndex)) {
                    item(game) { headerUp().takeIf { index == 0 } }
                }
            }
        }
    }
}

/** Display state for one library screen, independent of each host's file and preference types. */
data class LibraryPresentation<T>(
    val games: List<T>, val grid: Boolean, val section: LibrarySection,
    val views: List<LibraryView>, val view: LibraryView, val counts: Map<LibraryView, Int>,
    val hasCatalog: Boolean, val query: String, val emptyText: String,
    val tools: List<LibraryTool> = emptyList(),
)

typealias LibraryActions = com.pokedaisy.app.companion.LibraryActions

class LibraryFocus(val header: FocusRequester = FocusRequester(), val views: FocusRequester = FocusRequester()) {
    var headerAttached = false
    var viewsAttached = false
    val up get() = views.takeIf { viewsAttached } ?: header.takeIf { headerAttached }
}

/** One library screen on every platform. Hosts supply data, supported actions, native settings and card callbacks. */
/** The library header's buttons ([LibraryScreen]); [help] false leaves HELP out. */
@Composable
private fun <T> RowScope.LibraryHeaderButtons(
    state: LibraryPresentation<T>, actions: LibraryActions, m: GbaTextMetrics, focus: LibraryFocus, compact: Boolean, help: Boolean,
) {
    if (state.section == LibrarySection.GAMES) {
        LibraryHeaderIcon(LibraryIcons.Sort, tr("Sort"), m,
            Modifier.focusRequester(focus.header), compact = compact, onClick = actions.sort)
        DisposableEffect(focus) { focus.headerAttached = true; onDispose { focus.headerAttached = false } }
        LibraryHeaderIcon(Icons.Filled.Search, tr("SEARCH"), m, compact = compact, onClick = actions.search)
        LibraryHeaderIcon(if (state.grid) LibraryIcons.ViewList else LibraryIcons.GridView,
            tr("Toggle view"), m, compact = compact, onClick = actions.toggleGrid)
        LibraryHeaderIcon(Icons.Filled.Add, tr("Add games"), m, compact = compact, onClick = actions.add)
        LibraryHeaderIcon(Icons.Filled.Refresh, tr("Refresh"), m, compact = compact, onClick = actions.refresh)
    }
    if (help) LibraryHeaderIcon(LibraryIcons.HelpOutline, tr("Help"), m, compact = compact, onClick = actions.help)
}

@Composable
fun <T> LibraryScreen(
    state: LibraryPresentation<T>, actions: LibraryActions, m: GbaTextMetrics, small: GbaTextMetrics,
    key: (T) -> String, modifier: Modifier = Modifier, focus: LibraryFocus = remember { LibraryFocus() },
    overlays: @Composable () -> Unit = {}, settings: @Composable ColumnScope.() -> Unit = {},
    item: @Composable (T, () -> FocusRequester?) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val short = LocalShortScreen.current
        val margin = libraryMargin
        val toolsOpen = state.section == LibrarySection.TOOLS
        LibraryFrame(state.section, m, small, actions.section,
            toolsBadge = state.tools.any { it.newBadge != null } && !toolsOpen, showHeader = state.section != LibrarySection.SETTINGS,
            compactTitle = compact && !toolsOpen && state.section == LibrarySection.GAMES,
            headerGap = m.u * if (short) 3 else if (state.hasCatalog || state.section != LibrarySection.GAMES) 8 else 4,
            // The buttons; [narrow]: narrower and without HELP, where the full ones would cut POKéDAISY short (the user's
            // call, 2026-11-22: HELP is also in Settings).
            header = { LibraryHeaderButtons(state, actions, m, focus, compact, help = true) },
            compactHeader = { LibraryHeaderButtons(state, actions, m, focus, compact = true, help = state.section != LibrarySection.GAMES) },
            overlays = overlays,
        ) {
            when (state.section) {
                LibrarySection.SETTINGS -> settings()
                LibrarySection.TOOLS -> ToolsPage(state.tools, m, small,
                    PaddingValues(start = margin, end = margin, bottom = margin), Modifier.weight(1f))
                LibrarySection.GAMES -> {
                    LibrarySearchStrip(state.query, m, actions.search, actions.query)
                    if (state.hasCatalog) {
                        QuickViewPills(state.views, state.view, state.counts, m, small, actions.view,
                            contentPadding = PaddingValues(horizontal = margin), focusRequester = focus.views,
                            onReorder = actions.reorderViews)
                        DisposableEffect(focus) { focus.viewsAttached = true; onDispose { focus.viewsAttached = false } }
                        Spacer(Modifier.height(m.u * if (short) 3 else 8))
                    }
                    if (state.games.isEmpty()) {
                        OptionListWindow(m, Modifier.fillMaxWidth().padding(horizontal = margin)) {
                            GbaText(if (state.hasCatalog) libraryEmptyViewText(state.view)
                                else if (state.query.isNotBlank()) tr("No games match your search.") else state.emptyText,
                                OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE,
                                modifier = Modifier.padding(m.u * 4))
                        }
                    } else androidx.compose.runtime.key(state.view) {
                        LibraryCollection(state.games, state.grid, m, key, Modifier.weight(1f), headerUp = { focus.up }, item = item)
                    }
                }
            }
        }
    }
}

fun libraryEmptyViewText(view: LibraryView) =
    if (view == LibraryView.FAVORITES) tr("No favorites yet. Long press a game and pick FAVORITE to keep it here.")
    else tr("No games played yet. The games you play show here, the last one first.")

@Composable
fun LibrarySearchDialog(query: String, onQuery: (String) -> Unit, m: GbaTextMetrics, small: GbaTextMetrics, onDismiss: () -> Unit) {
    OptionOverlay(onDismiss, Modifier.widthIn(max = 520.dp)) {
        Column {
            OptionTitleWindow(tr("SEARCH"), m)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column {
                    OptionTextField(
                        query, onQuery, small,
                        Modifier.padding(horizontal = m.u * 8, vertical = m.u * 2),
                        placeholder = tr("SEARCH GAMES"), leadingIcon = com.pokedaisy.app.companion.ui.PixelIcons.search,
                        // Opened to type in: the field and its keyboard at once.
                        typeAtOnce = true,
                    )
                    OptionLine(tr("CLEAR"), null, selected = false, m, height = m.rowHeight * 1.3f) { onQuery(""); onDismiss() }
                    OptionLine(tr("DONE"), null, selected = true, m, height = m.rowHeight * 1.3f, onClick = onDismiss)
                }
            }
        }
    }
}


@Composable
fun LibrarySearchStrip(query: String, m: GbaTextMetrics, onSearch: () -> Unit, onQuery: (String) -> Unit) {
    if (query.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = libraryMargin).padding(bottom = m.u * 4),
        horizontalArrangement = Arrangement.spacedBy(m.u * 4),
    ) {
        OptionButton(tr("SEARCH") + ": " + query, m, modifier = Modifier.weight(1f, fill = false), onClick = onSearch)
        OptionButton(tr("CLEAR"), m, onClick = { onQuery("") })
    }
}
