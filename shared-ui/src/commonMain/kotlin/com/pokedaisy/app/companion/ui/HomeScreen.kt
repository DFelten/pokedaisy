package com.pokedaisy.app.companion.ui

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.pokedaisy.app.companion.HomeCard
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.TrainerCardInfo
import com.pokedaisy.app.companion.data.gameCase
import com.pokedaisy.app.companion.i18n.groupedNumber
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.HomeLayouts.Form
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing
import kotlin.time.Clock

/**
 * The HOME tab (docs/plans/companion-home.md): the player's [cards] where [HomeLayouts] puts them for this screen, the
 * [shortcuts] (tab ids) along the bottom. A party slot opens that Pokémon's summary ([onOpenMon]), a card or a shortcut
 * its tab ([onOpenTab]). Along the top, a status bar like the game screen's: [gameName], money, the clock and the battery -
 * left out while STATUS BAR shows the same one over the companion ([statusBarShown]). Built from the menus' own pieces:
 * SUMMARY MINT's windows and cells, MODERN's through them.
 */
@Composable
internal fun HomeScreen(
    snapshot: SnapshotView,
    cards: List<HomeCard>,
    shortcuts: List<String>,
    trainerCard: TrainerCardInfo?,
    statusBarShown: Boolean,
    /** The game's name, the status bar's first word (the Library's). */
    gameName: String,
    onOpenMon: (Int) -> Unit,
    onOpenTab: (String) -> Unit,
    /** Where TRAINER goes: CARD where the game has it, else DEX; null = TRAINER opens nothing. */
    trainerTab: String?,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(1f)
    val u = m.u
    val mm = rememberMiniMap(snapshot)
    val money = snapshot.money ?: trainerCard?.money
    // What the game has something for: a card with nothing to show isn't drawn (and its room goes to the others).
    val shown = cards.filter { c ->
        when (c) {
            HomeCard.PARTY -> true
            HomeCard.MAP -> mm.drawn || snapshot.location.mapSecName.isNotEmpty()
            HomeCard.TRAINER -> trainerCard != null || snapshot.pokedex != null
        }
    }
    Column(modifier.testTag("home")) {
    // The status bar: where you are only when the MAP card doesn't say it.
    if (!statusBarShown) {
        val place = snapshot.location.mapSecName.takeIf { HomeCard.MAP !in shown && it.isNotEmpty() }?.let { gameCase(it) }
        HomeStatusBar(gameName, place, money, Modifier.padding(start = u * HomeLayouts.GAP, end = u * HomeLayouts.GAP, top = u * HomeLayouts.GAP))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val short = LocalShortCompanion.current
        val text = HomeLayouts.Text(line = m.lineHeight / u, small = small.lineHeight / u)
        val w = maxWidth / u
        val h = maxHeight / u
        val picture = mm.drawn
        val layout = remember(w, h, short, shown, shortcuts.size, text, picture) { HomeLayouts.layout(w, h, short, shown, shortcuts.size, text, picture) }
        fun Modifier.at(b: HomeLayouts.Box) = offset(u * b.x, u * b.y).size(u * b.w, u * b.h)
        layout.party?.let { b ->
            HomeParty(snapshot.party, layout.partyColumns, layout.partyMini, m, small, Modifier.at(b), onOpenMon)
        }
        layout.map?.let { b -> HomeMap(snapshot, mm, b, small, Modifier.at(b)) { onOpenTab("MAP") } }
        for (p in layout.cards) {
            val open = when (p.card) {
                HomeCard.TRAINER -> trainerTab
                HomeCard.MAP -> "MAP"
                else -> null
            }
            HomeSmallCard(p, Modifier.at(p.box), open?.let { { onOpenTab(it) } }) {
                when (p.card) {
                    HomeCard.TRAINER -> TrainerCardContent(p.form, trainerCard, snapshot, small)
                    // No map picture for the game: where you are, on one line.
                    HomeCard.MAP -> PlaceLine(snapshot.location.mapSecName, small)
                    else -> {}
                }
            }
        }
        layout.shortcuts?.let { b ->
            Row(Modifier.at(b), horizontalArrangement = Arrangement.spacedBy(u * HomeLayouts.GAP)) {
                for (id in shortcuts.take(layout.shortcutCount)) {
                    AppTile(id, small, enabled = true, modifier = Modifier.width(u * layout.shortcutTileW).height(u * b.h), label = layout.shortcutLabels, tag = "home") { onOpenTab(id) }
                }
            }
        }
    }
    }
}

/** A focusable, clickable window: the ring the D-pad shows on it, its click. */
@Composable
private fun Modifier.homeClickable(u: Dp, onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    return gbaFocusRing(focused, PixelRoundedShape(u * 2)).soundClickable(interactionSource = interaction, indication = null, onClick = onClick)
}

// ---- PARTY --------------------------------------------------------------------------------------

/** The six in one window, [columns] across: a named slot (icon, name, level, HP bar) or, [mini], a row (icon, level, HP). */
@Composable
private fun HomeParty(party: List<MonView>, columns: Int, mini: Boolean, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier, onOpen: (Int) -> Unit) {
    val u = m.u
    OptionListWindow(m, modifier) {
        val rows = (6 + columns - 1) / columns
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(u * HomeLayouts.SLOT_GAP)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * HomeLayouts.SLOT_GAP)) {
                    for (c in 0 until columns) {
                        val i = r * columns + c
                        HomeSlot(party.getOrNull(i), i, mini, small, Modifier.weight(1f).fillMaxSize().focusStart(i == 0 && party.isNotEmpty())) { onOpen(i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSlot(mon: MonView?, index: Int, mini: Boolean, small: GbaTextMetrics, modifier: Modifier, onOpen: () -> Unit) {
    val u = small.u
    val c = OptionColors.cells ?: com.pokedaisy.app.companion.ui.theme.CellColors(
        OptionColors.divider, OptionColors.listFill, OptionColors.well, OptionColors.value, androidx.compose.ui.graphics.Color.White, OptionColors.valueShadow,
    )
    val fill = if (index % 2 == 0) c.a else c.b
    val base = modifier.cell(fill, c.line, u, top = true, bottom = true)
    if (mon == null) {
        // An empty place, kept so the six line up.
        Box(base.alpha(0.5f))
        return
    }
    val icon = u * HomeLayouts.ICON
    val line = small.lineHeight
    BoxWithConstraints(base.homeClickable(u, onOpen).testTag("home-slot-$index").padding(horizontal = u * 3, vertical = u * 2)) {
        // A slot tall enough for it (the Thor's, the side panel's) stacks the icon and level over the name, the HP bar and
        // its numbers; a shorter one puts the icon beside them; [mini] is one row.
        val stacked = !mini && maxHeight >= icon + line * 2 + u * 13
        val numbers = maxHeight >= line * 3 + u * 8
        when {
            mini -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                SlotIcon(mon, icon, u)
                Spacer(Modifier.width(u * 2))
                if (!mon.isEgg) {
                    GbaText(tr("Lv{0}", mon.level), OptionColors.label, OptionColors.labelShadow, small)
                    Spacer(Modifier.width(u * 3))
                    HudHpBar(mon.hp, mon.maxHp, Modifier.weight(1f).height(u * 5))
                } else GbaText(mon.shownName, OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.weight(1f).narrowToFit(small))
            }
            stacked -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SlotIcon(mon, icon, u)
                    Spacer(Modifier.weight(1f))
                    if (!mon.isEgg) GbaText(tr("Lv{0}", mon.level), OptionColors.value, OptionColors.valueShadow, small)
                }
                GbaText(mon.shownName, OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.fillMaxWidth().narrowToFit(small))
                if (!mon.isEgg) {
                    HudHpBar(mon.hp, mon.maxHp, Modifier.fillMaxWidth().height(u * 5))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        GbaText("${mon.hp}/${mon.maxHp}", OptionColors.label, OptionColors.labelShadow, small)
                    }
                }
            }
            // The icon beside the name over the level and HP bar (and the HP numbers where there's room): the name has the width
            // to itself.
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                SlotIcon(mon, icon, u)
                Spacer(Modifier.width(u * 2))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(u * 2)) {
                    GbaText(mon.shownName, OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.fillMaxWidth().narrowToFit(small))
                    if (!mon.isEgg) Row(verticalAlignment = Alignment.CenterVertically) {
                        GbaText(tr("Lv{0}", mon.level), OptionColors.value, OptionColors.valueShadow, small)
                        Spacer(Modifier.width(u * 3))
                        HudHpBar(mon.hp, mon.maxHp, Modifier.weight(1f).height(u * 5))
                    }
                    if (!mon.isEgg && numbers) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        GbaText("${mon.hp}/${mon.maxHp}", OptionColors.label, OptionColors.labelShadow, small)
                    }
                }
            }
        }
    }
}

/** The party icon (its status tag, a fainted one faded): its 32 x 32 frame's width kept (the sprite fills it), its height
 * free to spill past a short slot, like the game's. */
@Composable
private fun SlotIcon(mon: MonView, icon: Dp, u: Dp) {
    Box(Modifier.size(icon * 32 / 24, icon), contentAlignment = Alignment.Center) {
        Box(Modifier.wrapContentSize(unbounded = true)) { HudMonIcon(mon, icon, u) }
    }
}

/** MAP with no picture for the game: a pin and the place's name. */
@Composable
private fun PlaceLine(name: String, small: GbaTextMetrics) {
    val cell = small.lineHeight / 16
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PixelArt(MAP_PIN, mapOf('o' to OptionColors.value, 's' to OptionColors.valueShadow), Modifier.size(cell * MAP_PIN[0].length, cell * MAP_PIN.size))
        Spacer(Modifier.width(cell * 3))
        GbaText(gameCase(name).uppercase().replace('É', 'é'), OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.weight(1f).narrowToFit(small))
    }
}

// ---- MAP ----------------------------------------------------------------------------------------

/** The minimap filling [box] (GBA pixels), the place's name under it; no map = the name alone. */
@Composable
private fun HomeMap(snapshot: SnapshotView, mm: MiniMap, box: HomeLayouts.Box, small: GbaTextMetrics, modifier: Modifier, onOpen: () -> Unit) {
    val u = small.u
    val name = snapshot.location.mapSecName
    OptionListWindow(small, modifier.homeClickable(u, onOpen).testTag("home-map")) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val nameH = if (name.isNotEmpty()) small.lineHeight / u + 2f else 0f
            val mapW = (box.w - 2 * HomeLayouts.FRAME).toInt()
            val mapH = (box.h - 2 * HomeLayouts.FRAME - nameH).toInt()
            if (mm.drawn && mapW > 8 && mapH > 8) {
                MiniMapPicture(snapshot, mm, mapW, mapH, u)
                Spacer(Modifier.height(u * 2))
            }
            if (name.isNotEmpty()) {
                GbaText(
                    gameCase(name).uppercase().replace('É', 'é'), OptionColors.label, OptionColors.labelShadow, small,
                    modifier = Modifier.fillMaxWidth().narrowToFit(small),
                )
            }
        }
    }
}

// ---- Small cards --------------------------------------------------------------------------------

@Composable
private fun HomeSmallCard(p: HomeLayouts.Placed, modifier: Modifier, onOpen: (() -> Unit)?, content: @Composable () -> Unit) {
    val small = rememberGbaTextMetrics(1f)
    val u = small.u
    // A LINE sits closer to its frame: one line of text in the smallest window.
    OptionListWindow(small, modifier.homeClickable(u, onOpen).testTag("home-${p.card.id}"), margin = if (p.form == Form.LINE) -1 else 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = if (p.form == Form.LINE) Alignment.CenterStart else Alignment.TopStart) { content() }
    }
}

/** A heading in the card's corner: the values' colour, like a settings group's title. */
@Composable
private fun CardHeading(text: String, small: GbaTextMetrics) =
    GbaText(text, OptionColors.value, OptionColors.valueShadow, small, modifier = Modifier.narrowToFit(small))

@Composable
private fun LabelValue(label: String, value: String, small: GbaTextMetrics, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GbaText(label, OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.weight(1f).narrowToFit(small))
        GbaText(value, OptionColors.label, OptionColors.labelShadow, small)
    }
}

/** TRAINER: its heading and the play time, the badges, the POKéDEX's count. A LINE: the badges and the play time. */
@Composable
private fun TrainerCardContent(form: Form, card: TrainerCardInfo?, snapshot: SnapshotView, small: GbaTextMetrics) {
    val u = small.u
    val time = card?.let { "${it.hours}:${it.minutes.toString().padStart(2, '0')}" }
    val badges = card?.badges
    if (form == Form.LINE) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u * 6)) {
            badges?.let { BadgePips(it, small, Modifier.weight(1f).height(u * 6)) } ?: Spacer(Modifier.weight(1f))
            time?.let { GbaText(it, OptionColors.label, OptionColors.labelShadow, small) }
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(u * 2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardHeading(tr("TRAINER"), small) }
            time?.let { GbaText(it, OptionColors.label, OptionColors.labelShadow, small) }
        }
        Spacer(Modifier.height(u))
        Box(Modifier.fillMaxWidth().height(small.lineHeight), contentAlignment = Alignment.CenterStart) {
            if (badges != null) BadgePips(badges, small, Modifier.fillMaxWidth().height(u * 7))
        }
        snapshot.pokedex?.let { dex ->
            LabelValue(tr("POKéDEX"), "${dex.caught.size} / ${dex.seen.size}", small)
        }
    }
}

/** The badges as pips, got ones gold: 8, or 16 when the game has two regions' (a bit past the eighth is set). */
@Composable
private fun BadgePips(bits: Int, small: GbaTextMetrics, modifier: Modifier) {
    val count = if (bits ushr 8 != 0) 16 else 8
    val got = OptionColors.favorite
    val empty = OptionColors.divider
    val u = small.u
    Canvas(modifier) {
        val px = u.toPx()
        val d = minOf(size.height, (size.width - (count - 1) * px) / count).coerceAtLeast(px)
        val gap = ((size.width - count * d) / (count - 1)).coerceIn(px, d)
        for (i in 0 until count) {
            val on = bits shr i and 1 == 1
            drawPixelRoundRect(if (on) got else empty, Offset(i * (d + gap), (size.height - d) / 2f), Size(d, d), radius = d / 2f, step = px)
        }
    }
}

/**
 * HOME's status bar, the game screen's ([GameStatusBar]): the game's name, where you are (when the MAP card isn't showing
 * it), money, the clock and the battery. The clock is read again once a second by an infinite transition: a delay loop
 * never lets Compose tests (ui-preview, Paparazzi) go idle.
 */
@Composable
private fun HomeStatusBar(gameName: String, location: String?, money: Long?, modifier: Modifier = Modifier) {
    val second by androidx.compose.animation.core.rememberInfiniteTransition(label = "home-clock").animateValue(
        0, 60, Int.VectorConverter,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(60_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "home-clock-second",
    )
    val time = remember(second) { deviceTime() }
    GameStatusBar(gameName, location, money, time, modifier.testTag("home-status"))
}

private fun deviceTime(): String {
    val now = Clock.System.now().toEpochMilliseconds()
    return DeviceDates.current?.shortTime(now) ?: ""
}
