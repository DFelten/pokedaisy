package com.pokedaisy.app.companion.ui

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing

/**
 * The library's TOOLS: what works on games rather than playing them (PATCH A ROM, LINK PLAY, TRADE SAVES, ...). [label]
 * and [blurb] are English (translated where drawn); [newBadge] is NewFeatures' tag while the tool is new.
 */
class LibraryTool(val id: String, val label: String, val blurb: String, val newBadge: String?, val open: () -> Unit)

/**
 * The TOOLS page where the games are: a card per tool, as many columns as fit ([TOOL_CARD_MIN] each), one on a phone, and
 * every card as tall as the tallest (a blurb that wraps made its card taller, and the row's others left a ragged gap: the
 * user's call). The gaps between cards, across and down, are the header's space above them (8 GBA pixels): one margin.
 */
@Composable
fun ToolsPage(
    tools: List<LibraryTool>, m: GbaTextMetrics, small: GbaTextMetrics, contentPadding: PaddingValues, modifier: Modifier = Modifier,
    /** The dual-screen launcher's cursor (its keys move it, not Compose focus), -1 = none; [onColumns] gets the layout's columns. */
    cursor: Int = -1, onColumns: (Int) -> Unit = {},
) {
    val gap = m.u * if (LocalShortScreen.current) 4 else 8
    SubcomposeLayout(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)) { c ->
        val g = gap.roundToPx()
        val cols = ((c.maxWidth + g) / (TOOL_CARD_MIN.roundToPx() + g)).coerceIn(1, tools.size.coerceAtLeast(1))
        val w = ((c.maxWidth - (cols - 1) * g) / cols).coerceAtLeast(0)
        onColumns(cols)
        val probe = subcompose("probe") { tools.forEach { ToolCard(it, m, small) } }.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
        val h = probe.maxOfOrNull { it.height } ?: 0
        val cards = subcompose("cards") { tools.forEachIndexed { i, t -> ToolCard(t, m, small, cursor = i == cursor) } }.map { it.measure(Constraints.fixed(w, h)) }
        val rows = (cards.size + cols - 1) / cols
        // At least the page's height (its weight asks for it), the cards from the top: a smaller layout was centred in it.
        layout(c.maxWidth, (rows * h + (rows - 1).coerceAtLeast(0) * g).coerceAtLeast(c.minHeight)) {
            cards.forEachIndexed { i, card -> card.place((i % cols) * (w + g), (i / cols) * (h + g)) }
        }
    }
}

/** A tool's card: the title window's white box, its icon, the name (and NEW) over a line saying what it does,
 * both from the card's top-left padding, so every card's icon and name line up whatever its blurb's length. */
@Composable
private fun ToolCard(tool: LibraryTool, m: GbaTextMetrics, small: GbaTextMetrics, cursor: Boolean = false) {
    val u = m.u
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value || cursor
    val label = tr(tool.label)
    val short = LocalShortScreen.current
    Row(
        Modifier
            .fillMaxWidth()
            .gbaFocusRing(focused, PixelRoundedShape(u * 3))
            .drawBehind {
                val px = u.toPx()
                drawLayeredBox(OptionColors.titleLayers.inPx(px), if (focused) OptionColors.rowSelected else OptionColors.titleFill, radius = 3 * px)
                // RETRO: the tool's colour down the card's left edge, inside its frame (a little colour, still pixel art).
                if (!modernView) ModernGlyph.forTool(tool.id)?.let { g ->
                    val frame = OptionColors.titleLayers.sumOf { it.second } * px
                    drawRect(g.low, androidx.compose.ui.geometry.Offset(frame, frame + 2 * px), androidx.compose.ui.geometry.Size(2 * px, size.height - 2 * frame - 4 * px))
                }
            }
            .keyDrivenFocus()
            .soundClickable(interactionSource = interaction, indication = null, onClick = tool.open)
            .semantics { contentDescription = label }
            .padding(u * if (short) 4 else 6),
        verticalAlignment = Alignment.Top,
    ) {
        // The icon on the card itself, no well round it, at the rail's grain (not two GBA pixels an icon pixel: the user's call).
        // MODERN: the tool's own app-icon tile.
        if (modernView) ModernGlyph.forTool(tool.id)?.let { g ->
            ModernTile(g, Modifier.size(u * if (short) SHORT_ICON else TOOL_ICON))
            Spacer(Modifier.width(u * if (short) 4 else 6))
        } else ToolIcons.of(tool.id)?.let { icon ->
            PixelArt(icon.bitmap, icon.palette, Modifier.size(u * if (short) SHORT_ICON else TOOL_ICON))
            Spacer(Modifier.width(u * if (short) 4 else 6))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The list's smaller text, the blurb a step under it (the rows' size read too loud: the user's call).
                GbaText(label, OptionColors.label, OptionColors.labelShadow, small, bold = modernView)
                tool.newBadge?.let {
                    OptionBadge(it, small, Modifier.padding(start = u * 4), fill = OptionColors.newBadge)
                }
            }
            Spacer(Modifier.height(u * 2))
            GbaText(tr(tool.blurb), OptionColors.muted, OptionColors.mutedShadow, rememberGbaTextMetrics(0.85f), maxLines = 2)
        }
    }
}

/** The library's places, in its rail / bar ([LibraryNav]): the games, the tools, the app's settings. */
typealias LibrarySection = com.pokedaisy.app.companion.LibrarySection

/**
 * GAMES / TOOLS / SETTINGS as a rail down the left of a wide screen ([vertical]) or a bar along the bottom of a tall one
 * (the players' pick in a Discord poll, 2026-11-02, over a TOOLS button by the quick views): each item its icon over its
 * name, the open one a white block with a red label. Docked to the screen's edge (the user's call, 2026-11-03): the
 * window's frame only on the side facing the page, its other sides run off the screen. [top] puts the rail's first item
 * level with the page's header.
 */
@Composable
fun LibraryNav(
    open: LibrarySection, vertical: Boolean, m: GbaTextMetrics, small: GbaTextMetrics, toolsBadge: Boolean,
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
    /** The dual-screen launcher's cursor on the rail (its keys move it, not Compose focus), or null. */
    cursor: LibrarySection? = null,
    /** MENU BAR's other edge: the rail down the right ([vertical]), the bar along the top (else). */
    far: Boolean = false,
    onPick: (LibrarySection) -> Unit,
) {
    val u = m.u
    // A short screen (the RG DS), or MENU BAR SIZE COMPACT: the icons alone, smaller, so the rail is a strip and the games
    // get the width.
    val short = LocalShortScreen.current || com.pokedaisy.app.companion.ui.theme.CustomLook.menuBarCompact
    val items = @Composable { weighted: Modifier ->
        for (s in LibrarySection.entries) {
            NavItem(s, s == open, m, small, badge = toolsBadge && s == LibrarySection.TOOLS, weighted, cursor = s == cursor) { onPick(s) }
        }
    }
    val frame = if (modernView) Modifier.drawBehind {
        // MODERN: a white panel, a hairline and a soft shadow on the side facing the page.
        drawRect(OptionColors.listFill)
        val line = OptionColors.divider
        val hair = 1.dp.toPx()
        // The shadow and hairline on the side facing the page (the rail's right, or its left down the right edge; the bar's
        // top, or its bottom along the top).
        when {
            vertical && !far -> {
                for (i in 1..4) drawRect(Color(0x0C1B2A55), androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Size(i * 1.5f, size.height))
                drawRect(line, androidx.compose.ui.geometry.Offset(size.width - hair, 0f), androidx.compose.ui.geometry.Size(hair, size.height))
            }
            vertical -> {
                for (i in 1..4) drawRect(Color(0x0C1B2A55), androidx.compose.ui.geometry.Offset(-i * 1.5f, 0f), androidx.compose.ui.geometry.Size(i * 1.5f, size.height))
                drawRect(line, size = androidx.compose.ui.geometry.Size(hair, size.height))
            }
            !far -> {
                for (i in 1..4) drawRect(Color(0x0C1B2A55), androidx.compose.ui.geometry.Offset(0f, -i * 1.5f), androidx.compose.ui.geometry.Size(size.width, i * 1.5f))
                drawRect(line, size = androidx.compose.ui.geometry.Size(size.width, hair))
            }
            else -> {
                for (i in 1..4) drawRect(Color(0x0C1B2A55), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Size(size.width, i * 1.5f))
                drawRect(line, androidx.compose.ui.geometry.Offset(0f, size.height - hair), androidx.compose.ui.geometry.Size(size.width, hair))
            }
        }
    } else Modifier.drawBehind {
        drawLayeredBox(
            OptionColors.listLayers.inPx(u.toPx()), OptionColors.listFill, radius = 0f,
            // Framed only on the side facing the page.
            openLeft = !(vertical && far), openRight = !(vertical && !far), openTop = !(!vertical && !far), openBottom = !(!vertical && far),
        )
    }
    if (vertical) {
        // As wide as its widest label needs (SETTINGS was cut short on the Thor, whose 2.3 density makes the pixel font
        // wider in dp than ui-preview's 2.5), at least NAV_RAIL_ITEM.
        Column(
            modifier.fillMaxHeight().width(IntrinsicSize.Max).then(frame)
                .padding(start = u * (if (far) (if (short) 6 else 10) else if (short) 3 else 5), end = u * (if (far) (if (short) 3 else 5) else if (short) 6 else 10), top = top, bottom = u * 5),
            verticalArrangement = Arrangement.spacedBy(u * if (short) 3 else 4),
        ) {
            items(Modifier.widthIn(min = if (short) 0.dp else NAV_RAIL_ITEM).fillMaxWidth())
        }
    } else {
        Row(
            modifier.fillMaxWidth().then(frame).padding(start = u * 5, end = u * 5,
                top = u * (if (far) (if (short) 3 else 5) else if (short) 6 else 10), bottom = u * (if (far) (if (short) 6 else 10) else if (short) 3 else 5)),
            horizontalArrangement = Arrangement.spacedBy(u * 4),
        ) {
            items(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    section: LibrarySection, on: Boolean, m: GbaTextMetrics, small: GbaTextMetrics, badge: Boolean, modifier: Modifier, cursor: Boolean = false,
    onClick: () -> Unit,
) {
    val u = m.u
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value || cursor
    val label = tr(section.label)
    if (modernView) { ModernNavItem(section, on, m, small, badge, modifier, focused, interaction, label, onClick); return }
    // A short screen or MENU BAR SIZE COMPACT: the icon alone.
    val short = LocalShortScreen.current || com.pokedaisy.app.companion.ui.theme.CustomLook.menuBarCompact
    Box(
        modifier
            .gbaFocusRing(focused, PixelRoundedShape(u * 3))
            // No frame round the items (the user's call): the open one is a white block, the others the window's own grey.
            .drawBehind {
                if (!on) return@drawBehind
                // Without its red label (a short screen) the open one needs the white block's frame to stand out.
                if (short) drawLayeredBox(OptionColors.titleLayers.inPx(u.toPx()), OptionColors.rowSelected, radius = 3 * u.toPx())
                else drawPixelRoundRect(OptionColors.titleFill, radius = 3 * u.toPx(), step = u.toPx())
            }
            .keyDrivenFocus()
            .soundClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = u * if (short) 4 else 5, horizontal = u * 3),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val icon = when (section) {
                LibrarySection.GAMES -> ToolIcons.games
                LibrarySection.TOOLS -> ToolIcons.toolbox
                LibrarySection.SETTINGS -> ToolIcons.settings
            }
            // Finer than the label's GBA pixel (PixelArt keeps whole screen pixels per icon pixel): a 32-pixel drawing, not a
            // blown-up 9-pixel glyph.
            // A short screen's own small size; COMPACT elsewhere: two thirds of the usual (no name under it).
            val iconSize = if (LocalShortScreen.current) SHORT_ICON else if (short) COMPACT_ICON else NAV_ICON
            PixelArt(icon.bitmap, icon.palette, Modifier.size(u * iconSize))
            // No room for the names on a short screen; the icons say it (the open one sits in the white block).
            if (!short) Spacer(Modifier.height(u * 3))
            if (!short) GbaText(label, if (on) OptionColors.value else OptionColors.label, if (on) OptionColors.valueShadow else OptionColors.labelShadow, small, bold = gameBoldLabels())
        }
        if (badge) Box(
            Modifier.align(Alignment.TopEnd).size(u * if (short) 4 else 5)
                .drawBehind { drawPixelRoundRect(OptionColors.newBadge, radius = size.width / 2, step = u.toPx()) },
        )
    }
}

/** MODERN's rail item: its glyph over its name, the open one's glyph in a pale blue pill and both in blue. */
@Composable
private fun ModernNavItem(
    section: LibrarySection, on: Boolean, m: GbaTextMetrics, small: GbaTextMetrics, badge: Boolean, modifier: Modifier,
    focused: Boolean, interaction: MutableInteractionSource, label: String, onClick: () -> Unit,
) {
    val u = m.u
    val accent = OptionColors.value
    Box(
        modifier
            .gbaFocusRing(focused, androidx.compose.foundation.shape.RoundedCornerShape(u * 6))
            .keyDrivenFocus()
            .soundClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = u * 4, horizontal = u * 3),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val glyph = when (section) {
                LibrarySection.GAMES -> ModernGlyph.GAMES
                LibrarySection.TOOLS -> ModernGlyph.TOOLS
                LibrarySection.SETTINGS -> ModernGlyph.SETTINGS
            }
            Box(
                Modifier.size(u * NAV_ICON * 1.5f, u * NAV_ICON * 0.95f).drawBehind {
                    if (on) drawRoundRect(OptionColors.rowSelected, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
                },
                contentAlignment = Alignment.Center,
            ) { ModernIcon(glyph, if (on) accent else OptionColors.muted, Modifier.size(u * NAV_ICON * 0.68f)) }
            if (!com.pokedaisy.app.companion.ui.theme.CustomLook.menuBarCompact) {
                Spacer(Modifier.height(u * 3))
                GbaText(label, if (on) accent else OptionColors.muted, Color.Transparent, small, bold = true)
            }
        }
        if (badge) Box(
            Modifier.align(Alignment.TopEnd).size(u * 5)
                .drawBehind { drawCircle(OptionColors.newBadge) },
        )
    }
}

/** A tool card's least width: as many columns as fit, one on a phone held upright. */
private val TOOL_CARD_MIN: Dp = 300.dp

/** The rail's / bar's icons, in GBA pixels (their 32 pixels a whole screen pixel or two each). */
private const val NAV_ICON = 36

/** MENU BAR SIZE COMPACT's icons (not a short screen), in GBA pixels. */
private const val COMPACT_ICON = 24

/** The rail's and tool cards' icons on a short screen ([LocalShortScreen]), in GBA pixels: the 32-pixel drawings a screen
 * pixel each on the RG DS. */
private const val SHORT_ICON = 16

/** A tool card's icon, in GBA pixels: about the name and its two lines under it (three screen pixels an icon pixel on a 1080p screen). */
private const val TOOL_ICON = 36

/** The rail's items' width (a wide screen). */
private val NAV_RAIL_ITEM: Dp = 76.dp

/**
 * The tools' own drawings, [TabIcons]' kind but 32 px (finer than the tabs' 16: the user's call, 2026-11-16), 'k' the outline:
 * never the games' art. Built from shapes, mirrored about the middle where the thing is symmetrical, lit from the top.
 */
internal object ToolIcons {
    private val OUTLINE = Color(0xFF303038)
    private val WHITE = Color(0xFFF8F8F8)

    /** PATCH A ROM: a grey cartridge with a green plus on its label. */
    private val patch = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "......kkkkkkkkkkkkkkkkkkkk......",
            ".....khhhhhhhhhhhhhhhhhhhhk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kssskkkkkkkkkkkkkksssk.....",
            ".....ksskwwwwwwwwwwwwwwkssk.....",
            ".....ksskwwwwwwwwwwwwwwkssk.....",
            ".....ksskwwwwwkkkkwwwwwkssk.....",
            ".....ksskwwwwwkGGkwwwwwkssk.....",
            ".....ksskwwwwwkggkwwwwwkssk.....",
            ".....ksskwkkkkGggGkkkkwkssk.....",
            ".....ksskwkGGGggggGGGkwkssk.....",
            ".....ksskwkggggggggggkwkssk.....",
            ".....ksskwkggggggggggkwkssk.....",
            ".....ksskwkkkkggggkkkkwkssk.....",
            ".....ksskwwwwwkggkwwwwwkssk.....",
            ".....ksskwwwwwkggkwwwwwkssk.....",
            ".....ksskwwwwwkkkkwwwwwkssk.....",
            ".....ksskwwwwwwwwwwwwwwkssk.....",
            ".....ksskllllllllllllllkssk.....",
            ".....kssskkkkkkkkkkkkkksssk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kskksskksskksskksskksk.....",
            ".....kskksskksskksskksskksk.....",
            ".....kskksskksskksskksskksk.....",
            ".....kddddddddddddddddddddk.....",
            "......kkkkkkkkkkkkkkkkkkkk......",
            "................................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 's' to Color(0xFFB4B4C4), 'h' to Color(0xFFE0E0E8), 'd' to Color(0xFF84849A), 'w' to WHITE, 'l' to Color(0xFFD8D8E0), 'g' to Color(0xFF3FA34D), 'G' to Color(0xFF7FD08A)),
    )

    /** LINK PLAY: two purple plugs and the cable between them. */
    private val link = TabIcons.TabIcon(
        listOf(
            "................................",
            "....kk...kk..........kk...kk....",
            "....kk...kk..........kk...kk....",
            "....kk...kk..........kk...kk....",
            "....kk...kk..........kk...kk....",
            "...kkkkkkkkk........kkkkkkkkk...",
            "..kBBBBBBBBBk......kBBBBBBBBBk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kbbbbbbbbbk......kbbbbbbbbbk..",
            "..kDDDDDDDDDk......kDDDDDDDDDk..",
            "...kkkkkkkkk........kkkkkkkkk...",
            "......kCCCk..........kCCCk......",
            "......kccck..........kccck......",
            "......kcccCk........kCccck......",
            ".......kcccCk......kCccck.......",
            ".......kccccCkkkkkkCcccck.......",
            "........kccccCCCCCCcccck........",
            ".........kcccccccccccck.........",
            "..........kkcccccccckk..........",
            "............kkkkkkkk............",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'b' to Color(0xFF8058D0), 'B' to Color(0xFFA888F0), 'D' to Color(0xFF5A3AA0), 'c' to Color(0xFF5A3AA0), 'C' to Color(0xFF7A58C0)),
    )

    /** TRADE SAVES: an orange arrow out, a blue one back. */
    private val trade = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "..................k.............",
            "..................kk............",
            "..................kOk...........",
            "..................koOk..........",
            "..kkkkkkkkkkkkkkkkOooOk.........",
            "..kOOOOOOOOOOOOOOOooooOk........",
            "..kooooooooooooooooooooOk.......",
            "..koooooooooooooooooooork.......",
            "..krrrrrrrrrrrrrrroooork........",
            "..kkkkkkkkkkkkkkkkroork.........",
            "..................kork..........",
            "..................krk...........",
            "..................kk............",
            ".............k....k.............",
            "............kk..................",
            "...........kBk..................",
            "..........kBbk..................",
            ".........kBbbk..................",
            "........kBbbbBkkkkkkkkkkkkkkkk..",
            ".......kBbbbbbBBBBBBBBBBBBBBBk..",
            "......kBbbbbbbbbbbbbbbbbbbbbbk..",
            "......knbbbbbbbbbbbbbbbbbbbbbk..",
            ".......knbbbbbnnnnnnnnnnnnnnnk..",
            "........knbbbnkkkkkkkkkkkkkkkk..",
            ".........knbbk..................",
            "..........knbk..................",
            "...........knk..................",
            "............kk..................",
            ".............k..................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'o' to Color(0xFFF08830), 'O' to Color(0xFFF8B870), 'r' to Color(0xFFC06018), 'b' to Color(0xFF3878E0), 'B' to Color(0xFF80B0F8), 'n' to Color(0xFF2458B0)),
    )

    /** SAVE EDITOR: a blue floppy disk, its shutter and label. */
    private val edit = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "................................",
            "....kkkkkkkkkkkkkkkkkkkkk.......",
            "...kBBBBBksssssssssssskBBk......",
            "...kbbbbbkssssssskkksskbbBk.....",
            "...kbbbbbkssssssskkksskbbbBk....",
            "...kbbbbbkssssssskkksskbbbbBk...",
            "...kbbbbbkssssssskkksskbbbbbk...",
            "...kbbbbbkssssssskkksskbbbbbk...",
            "...kbbbbbkddddddddddddkbbbbbk...",
            "...kbbbbbkkkkkkkkkkkkkkbbbbbk...",
            "...kbbbbbbbbbbbbbbbbbbbbbbbbk...",
            "...kbbbbbbbbbbbbbbbbbbbbbbbbk...",
            "...kbbbbbbbbbbbbbbbbbbbbbbbbk...",
            "...kbbbbbbbbbbbbbbbbbbbbbbbbk...",
            "...kbbbkkkkkkkkkkkkkkkkkkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwllllllllllllwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwllllllllllllwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...kbbbkwwwwwwwwwwwwwwwwkbbbk...",
            "...knnnkwwwwwwwwwwwwwwwwknnnk...",
            "....kkkkkkkkkkkkkkkkkkkkkkkk....",
            "................................",
            "................................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'b' to Color(0xFF3878E0), 'B' to Color(0xFF80B0F8), 'n' to Color(0xFF2458B0), 's' to Color(0xFFC8C8D4), 'd' to Color(0xFF9898A8), 'w' to WHITE, 'l' to Color(0xFFA8A8B8)),
    )

    /** SPRITE PACK: a trainer from head to toe, red cap, blue jacket (our own drawing). */
    private val sprites = TabIcons.TabIcon(
        listOf(
            "................................",
            ".............kkkkkk.............",
            "............kHHHHHHk............",
            "..........kkHrrrrrrHkk..........",
            "..........kHrrrrrrrrHk..........",
            ".........kkkkkkkkkkkkkk.........",
            "........kRRRRRRRRRRRRRRk........",
            ".........kkkkkkkkkkkkkk.........",
            ".........kdksssssssskdk.........",
            ".........kdksksssskskdk.........",
            ".........kkSskssssksSkk.........",
            "...........kSSssssSSk...........",
            "...........kkkSSSSkkk...........",
            "........k..kBBkkkkBBk..k........",
            ".......kBkkBbbkkkkbbBkkBk.......",
            "......kBbBkbbbbnnbbbbkBbBk......",
            "......kbbbkbbbbnnbbbbkbbbk......",
            "......kbbbkbbbbnnbbbbkbbbk......",
            "......kbbbkbbbbnnbbbbkbbbk......",
            "......knbnknbbbnnbbbnknbnk......",
            ".......kkk.knnnnnnnnk.kkk.......",
            "......kssskkkkkkkkkkkksssk......",
            "......ksssk.kppppppk.ksssk......",
            "......ksssk.kppppppk.ksssk......",
            ".......kkk..kppppppk..kkk.......",
            "............kppppppk............",
            "............kppPPppk............",
            "...........kkkkkkkkkk...........",
            "..........kHHHHkkHHHHk..........",
            "..........krrrrkkrrrrk..........",
            "...........kkkk..kkkk...........",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'r' to Color(0xFFE04040), 'H' to Color(0xFFF88080), 'R' to Color(0xFFA82828), 's' to Color(0xFFF8C8A0), 'S' to Color(0xFFE0A880), 'd' to Color(0xFF603820), 'b' to Color(0xFF3878E0), 'B' to Color(0xFF80B0F8), 'n' to Color(0xFF2458B0), 'p' to Color(0xFF404858), 'P' to Color(0xFF2C3240)),
    )

    /** PAL PARK: a leafy tree on the grass, a bush and a flower beside it (our own drawing). */
    private val palPark = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "..........kkkkkk................",
            "........kkhhhhhhkk..............",
            ".......khhhhhhhhhhk.............",
            "......khhhhgggghhhhk............",
            "......khhhhhgghhhghk............",
            ".....khghhhhggghggghkkk.........",
            ".....khgghhgggggggghhhhkk.......",
            ".....kggggggggggggghhhhhhk......",
            ".....kgggggggggggghhhgghhk......",
            "....khggggggggggggghggggghk.....",
            "...khhhhggggggggggggggggghk.....",
            "..khhhhhggggggggggggggggggk.....",
            "..khgggggggggggggggggggggdk.....",
            "..kggggggggggggggddgggddgdk.....",
            "..kdgggggggggggggddgggdddk......",
            "..kddgggggddgggggddddddddk......",
            "...kdddddddggggggddddddkk.......",
            "....kdddddddddddddkkkkk.........",
            "...kkkkkkkkddddddkTk....kk......",
            "..kFyFk....kkkkkkTk...kkhhkk....",
            "..kfyfk......kttTk....khgghhkkk.",
            "...kkk.......kttk....khggggghhk.",
            "....d........kttk....kdggggggghk",
            "....d........kttk.....kdggggggdk",
            "....d..kkkkkkkTTkkk...kkddddddk.",
            "kkkkdkkLLLLLkkkkkkLkkkkkkkkkkkkk",
            "kLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLk",
            "kLLLLLLllllllllllllLLLLLLLLLLLLk",
            "kllllllllllllllllllllllllllllllk",
            "kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk",
        ),
        mapOf('k' to OUTLINE, 'g' to Color(0xFF48A848), 'h' to Color(0xFF90D870), 'd' to Color(0xFF2E7838), 't' to Color(0xFF8A5A30), 'T' to Color(0xFF603C20), 'l' to Color(0xFF70C050), 'L' to Color(0xFFA8E088), 'f' to Color(0xFFF06890), 'F' to Color(0xFFF8A8C0), 'y' to Color(0xFFF8D030)),
    )

    /** COLLECTION: an open album, a creature's round icon in each pocket (our own drawing). */
    private val collection = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "...kkkkkkkkkkkk..kkkkkkkkkkkk...",
            "...kwwwwwwwwwwk..kwwwwwwwwwwk...",
            "..kkwwkkkkkwwwkkkkwwwkkkkkwwkk..",
            ".kpkwkRRRRRkwwkppkwwkGGGGGkwkpk.",
            ".kpkkRrrrrrRkwkppkwkGgggggGkkpk.",
            ".kpkkrrrrrrrkwkppkwkgggggggkkpk.",
            ".kpkkrrrrrrrkwkppkwkgggggggkkpk.",
            ".kpkkrrrrrrrkwkppkwkgggggggkkpk.",
            ".kpkkrrrrrrrkwkppkwkgggggggkkpk.",
            ".kpkwkrrrrrkwwkppkwwkgggggkwkpk.",
            ".kpkwwkkkkkwwwkppkwwwkkkkkwwkpk.",
            ".kpkwwwwwwwwwwkppkwwwwwwwwwwkpk.",
            ".kpkwwwwwwwwwwkppkwwwwwwwwwwkpk.",
            ".kpkwwkkkkkwwwkppkwwwkkkkkwwkpk.",
            ".kpkwkBBBBBkwwkppkwwkYYYYYkwkpk.",
            ".kpkkBbbbbbBkwkppkwkYyyyyyYkkpk.",
            ".kpkkbbbbbbbkwkppkwkyyyyyyykkpk.",
            ".kpkkbbbbbbbkwkppkwkyyyyyyykkpk.",
            ".kpkkbbbbbbbkwkppkwkyyyyyyykkpk.",
            ".kpkkbbbbbbbkwkppkwkyyyyyyykkpk.",
            ".kpkwkbbbbbkwwkppkwwkyyyyykwkpk.",
            ".kpkwwkkkkkwwwkppkwwwkkkkkwwkpk.",
            ".kpkllllllllllkppkllllllllllkpk.",
            ".kpkkkkkkkkkkkkppkkkkkkkkkkkkpk.",
            ".kppppppppppppppppppppppppppppk.",
            ".kPPPPPPPPPPPPPPPPPPPPPPPPPPPPk.",
            "..kkkkkkkkkkkkkkkkkkkkkkkkkkkk..",
            "................................",
            "................................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'w' to WHITE, 'l' to Color(0xFFD8D8E0), 'p' to Color(0xFF9060C8), 'P' to Color(0xFF6A40A0), 'r' to Color(0xFFE85050), 'R' to Color(0xFFF89898), 'g' to Color(0xFF58B858), 'G' to Color(0xFF98E098), 'b' to Color(0xFF4880E0), 'B' to Color(0xFF90B8F8), 'y' to Color(0xFFF0C030), 'Y' to Color(0xFFF8E088)),
    )

    /** POKé TRANSFER: one game card sending a ball's worth of light into another, an arrow between (our own drawing). */
    private val transfer = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "...kkkkkkkkk........kkkkkkkkk...",
            "..kGGGGGGGGGk......kBBBBBBBBBk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "..kggkkkkkggk......kbbkkkkkbbk..",
            "..kggkwwwkggk......kbbkwwwkbbk..",
            "..kggkwwwkggk......kbbkwwwkbbk..",
            "..kggkwwwkggk......kbbkwwwkbbk..",
            "..kggkkkkkggk......kbbkkkkkbbk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "..kgggggggggk......kbbbbbbbbbk..",
            "...kkkkkkkkk........kkkkkkkkk...",
            "..................k.............",
            "..................kk............",
            "..................kAk...........",
            "..................kaAk..........",
            "....kkkkkkkkkkkkkkAaaAk.........",
            "....kAAAAAAAAAAAAAaaaaAk........",
            "....kaaaaaaaaaaaaaaaaaaAk.......",
            "....kaaaaaaaaaaaaaaaaaaRk.......",
            "....kRRRRRRRRRRRRRaaaaRk........",
            "....kkkkkkkkkkkkkkRaaRk.........",
            "..................kaRk..........",
            "..................kRk...........",
            "..................kk............",
            "..................k.............",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'g' to Color(0xFFD8B040), 'G' to Color(0xFFF0D480), 'b' to Color(0xFF4878D8), 'B' to Color(0xFF88B0F0), 'w' to WHITE, 'a' to Color(0xFFE85050), 'A' to Color(0xFFF89090), 'R' to Color(0xFFB83030)),
    )

    fun of(id: String): TabIcons.TabIcon? = when (id) {
        "PATCH" -> patch
        "LINK" -> link
        "EDIT" -> edit
        "TRADE" -> trade
        "PALPARK" -> palPark
        "COLLECTION" -> collection
        "TRANSFER" -> transfer
        "SPRITES" -> sprites
        else -> null
    }

    /** GAMES (the rail / bar): a grey cartridge with a blue label. */
    val games = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "......kkkkkkkkkkkkkkkkkkkk......",
            ".....khhhhhhhhhhhhhhhhhhhhk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kssskkkkkkkkkkkkkksssk.....",
            ".....ksskBBBBBBBBBBBBBBkssk.....",
            ".....ksskbbbbbbbbbbbbbbkssk.....",
            ".....ksskbbbbbbbbbbbbbbkssk.....",
            ".....ksskbbbbbkkkkbbbbbkssk.....",
            ".....ksskbbbbkYYYYkbbbbkssk.....",
            ".....ksskbbbkYyyyyYkbbbkssk.....",
            ".....ksskbbbkyyyyyykbbbkssk.....",
            ".....ksskbbkYyyyyyyYkbbkssk.....",
            ".....ksskbbbkyyyyyykbbbkssk.....",
            ".....ksskbbbkyyyyyykbbbkssk.....",
            ".....ksskbbbbkyyyykbbbbkssk.....",
            ".....ksskbbbbbkkkkbbbbbkssk.....",
            ".....ksskbbbbbbbbbbbbbbkssk.....",
            ".....ksskbbbbbbbbbbbbbbkssk.....",
            ".....kssknnnnnnnnnnnnnnkssk.....",
            ".....kssskkkkkkkkkkkkkksssk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kssssssssssssssssssssk.....",
            ".....kskksskksskksskksskksk.....",
            ".....kskksskksskksskksskksk.....",
            ".....kskksskksskksskksskksk.....",
            ".....kddddddddddddddddddddk.....",
            "......kkkkkkkkkkkkkkkkkkkk......",
            "................................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 's' to Color(0xFFB4B4C4), 'h' to Color(0xFFE0E0E8), 'd' to Color(0xFF84849A), 'b' to Color(0xFF3878E0), 'B' to Color(0xFF80B0F8), 'n' to Color(0xFF2458B0), 'y' to Color(0xFFF8D030), 'Y' to Color(0xFFF8E890)),
    )

    /** TOOLS (the rail / bar): a red toolbox, its grey handle and a gold latch. */
    val toolbox = TabIcons.TabIcon(
        listOf(
            "................................",
            "................................",
            "..........kkkkkkkkkkkk..........",
            ".........kGGGGGGGGGGGGk.........",
            ".........kggkkkkkkkkggk.........",
            ".........kgk........kgk.........",
            ".........kgk........kgk.........",
            ".........kgk........kgk.........",
            "...kkkkkkkkkkkkkkkkkkkkkkkkkk...",
            "..khhhhhhhhhhhhhhhhhhhhhhhhhhk..",
            "..khhhhhhhhhhhhhhhhhhhhhhhhhhk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrkkkkkkrrrrrrrrrrk..",
            "..krrrrrrrrrrkYYYYkrrrrrrrrrrk..",
            "..krrrrrrrrrrkyyyykrrrrrrrrrrk..",
            "..krrrrrrrrrrkyyyykrrrrrrrrrrk..",
            "..krrrrrrrrrrkyyyykrrrrrrrrrrk..",
            "..krrrrrrrrrrkkkkkkrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..krrrrrrrrrrrrrrrrrrrrrrrrrrk..",
            "..kddddddddddddddddddddddddddk..",
            "...kkkkkkkkkkkkkkkkkkkkkkkkkk...",
            "................................",
            "................................",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'g' to Color(0xFFA8A8B8), 'G' to Color(0xFFD0D0DC), 'r' to Color(0xFFE04040), 'h' to Color(0xFFFF9080), 'd' to Color(0xFFA02828), 'y' to Color(0xFFF8D030), 'Y' to Color(0xFFF8E890)),
    )

    /** SETTINGS (the rail / bar): a steel gear, lit from the top left. */
    val settings = TabIcons.TabIcon(
        listOf(
            "................................",
            ".............kkkkkk.............",
            ".............khhhhk.............",
            ".............khhhhk.............",
            ".......k.....kssssk.....k.......",
            "......khk....kssssk....khk......",
            ".....khhhk..khsssshk..khhhk.....",
            "....kddshhkkhhsssshhkkhhsddk....",
            ".....kddshhhhsssssshhhhsddk.....",
            "......kdsshhsddddddshhssdk......",
            ".......kssssddddddddssssk.......",
            ".......ksssddkkkkkkddsssk.......",
            "......khsssdk......kdssshk......",
            ".kkkkkhhsssk........kssshhkkkkk.",
            ".khhhhhssssk........ksssshhhhhk.",
            ".khhhhsssssk........kssssshhhhk.",
            ".kddddsssssk........ksssssddddk.",
            ".kdddddssssk........kssssdddddk.",
            ".kkkkkddsssk........ksssddkkkkk.",
            "......kdssshk......khsssdk......",
            ".......kssshhkkkkkkhhsssk.......",
            ".......ksssshhhhhhhhssssk.......",
            "......khssddshhhhhhsddsshk......",
            ".....khhsddddssssssddddshhk.....",
            "....kddsddkkddssssddkkddsddk....",
            ".....kdddk..kdssssdk..kdddk.....",
            "......kdk....kssssk....kdk......",
            ".......k.....kssssk.....k.......",
            ".............kddddk.............",
            ".............kddddk.............",
            ".............kkkkkk.............",
            "................................",
        ),
        mapOf('k' to OUTLINE, 'h' to Color(0xFFD8E0EC), 's' to Color(0xFFA4AEC0), 'd' to Color(0xFF6E788C)),
    )
}

/**
 * A tool's header (SAVE EDITOR, TRADE, PAL PARK...): the app's own header band along the screen's top edge, the whole
 * width ([LibraryHeaderBar], the user's call, 2026-11-22), its title a step larger with the tool's icon before it, and
 * [trailing] (whose saves) at its right end. Put inside a page with a [margin] all round, it reaches past it to the
 * screen's edges, and leaves a gap under it.
 */
@Composable
fun ToolHeader(
    title: String, toolId: String?, m: GbaTextMetrics, trailing: String? = null, onBack: (() -> Unit)? = null,
    margin: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val big = rememberGbaTextMetrics(1.5f)
    val gap = m.u * 4
    LibraryHeaderBar(
        title, big, onBack = onBack, logo = false,
        leading = toolId?.let { id -> {
            val size = big.lineHeight * 1.15f
            if (modernView) ModernGlyph.forTool(id)?.let { ModernTile(it, Modifier.size(size)) }
            else ToolIcons.of(id)?.let { PixelArt(it.bitmap, it.palette, Modifier.size(size)) }
        } },
        modifier = Modifier.layout { measurable, constraints ->
            val extra = margin.roundToPx()
            val w = constraints.maxWidth + 2 * extra
            val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
            // Placed over the page's margin (left, right and top), its height counted from the page's top edge, plus a gap.
            layout(constraints.maxWidth, (placeable.height - extra + gap.roundToPx()).coerceAtLeast(0)) { placeable.place(-extra, -extra) }
        },
    ) {
        trailing?.let { GbaText(it, OptionColors.headerText, OptionColors.headerShadow, m, maxLines = 1) }
    }
}
