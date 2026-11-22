package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.ShinyHunts
import com.pokedaisy.app.companion.data.Hunt
import com.pokedaisy.app.companion.data.HuntMethod
import com.pokedaisy.app.companion.data.HuntOdds
import com.pokedaisy.app.companion.data.HuntStatus
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.speciesName
import com.pokedaisy.app.companion.data.spriteAsset
import com.pokedaisy.app.companion.i18n.groupedNumber
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr

/**
 * The HUNT tab ([ShinyHunts], SHINY HUNT): the active hunt's counter (its target, the count in big numbers, + / -), how
 * its luck stands (the odds, the chance of a shiny by now, the next milestone, time and pace), its phases, its settings
 * (target, method, odds) and this game's earlier hunts. With no hunt going: what it does and START A HUNT.
 */
@Composable
internal fun HuntScreen(
    snapshot: SnapshotView,
    /** A RESETS hunt's LOAD button loads the current slot (the guard in the engine holds it over a shiny). */
    slots: com.pokedaisy.app.companion.StateSlots? = null,
    /** A RESETS hunt's RESTART button: the game booted again from its save (RESTART GAME). */
    restart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val m = rememberGbaTextMetrics()
    val log = ShinyHunts.log
    val hunt = log.active
    var cursor by rememberSaveable { mutableStateOf(1) }
    var options by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf<HuntPick?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    val wildHere = rememberWildHere(snapshot)
    // What a target can be: any shiny, the foes out now, this map's wild Pokémon.
    val targets = remember(snapshot.battleOpponent, wildHere) {
        (listOf(0) + snapshot.battleOpponent.map { it.species } + wildHere.orEmpty().map { it.species }).distinct()
    }
    val gameOdds = ShinyHunts.gameOdds
    Box(modifier.fillMaxSize()) {
        if (hunt == null) {
            HuntNone(m, log.hunts, onStart = { picking = HuntPick.START }, onOpen = { picking = HuntPick.Earlier(it) })
        } else if (options) {
            // OPTIONS: what's set once or seldom, a list that may scroll; BACK returns to the counter.
            val icon: (Int) -> @Composable () -> Unit = { species ->
                { Box { SpeciesIcon(huntIcon(species), m.lineHeight * 1.6f, Modifier.requiredSize(m.lineHeight * 1.6f)) } }
            }
            val odds = if (hunt.odds > 0) hunt.odds else gameOdds
            val rows = buildList {
                add(groupTitle(tk("THIS HUNT")))
                add(SettingRow(tk("TARGET"), targetLabel(hunt), labelIcon = if (hunt.target != 0) icon(hunt.target) else null) { picking = HuntPick.TARGET })
                add(SettingRow(tk("METHOD"), methodLabel(hunt.method), subtitle = methodLine(hunt.method)) { picking = HuntPick.METHOD })
                add(SettingRow(tk("ODDS"), oddsLabel(odds), subtitle = if (hunt.odds == 0) tk("The game's own") else null) { picking = HuntPick.ODDS })
                add(SettingRow(tk("COUNT"), groupedNumber(hunt.count), subtitle = tk("Add or take away many at once")) { picking = HuntPick.COUNT })
                if (hunt.phases.isNotEmpty()) {
                    add(groupTitle(tr("PHASES ({0})", hunt.phases.size)))
                    hunt.phases.asReversed().forEach { p -> add(SettingRow(p.name, groupedNumber(p.count), labelIcon = icon(p.species)) {}) }
                }
                add(groupTitle(tk("DONE?")))
                add(SettingRow(tk("PUT ON HOLD"), null, subtitle = tk("Keeps its count for later")) { ShinyHunts.hold(hunt); options = false })
                add(SettingRow(tk("NEW HUNT"), null) { picking = HuntPick.START })
                val earlier = log.hunts.filter { it.id != hunt.id }
                if (earlier.isNotEmpty()) {
                    add(groupTitle(tk("EARLIER HUNTS")))
                    earlier.forEach { h -> add(earlierRow(h, icon) { picking = HuntPick.Earlier(h) }) }
                }
            }
            Column(Modifier.fillMaxSize()) {
                OptionTitleWindow(tk("HUNT OPTIONS"), m, onBack = { options = false }, joined = true)
                WindowGap(m)
                OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                    val at = cursor.coerceIn(0, rows.lastIndex)
                    GroupedRows(rows, m, at, onClick = { i -> cursor = i; rows[i].onClick() }, labelWeight = 0.6f, onFocused = { cursor = it })
                }
            }
        } else {
            HuntDashboard(
                hunt, if (hunt.odds > 0) hunt.odds else gameOdds, m,
                slots = slots.takeIf { hunt.method == HuntMethod.RESETS },
                restart = restart?.takeIf { hunt.method == HuntMethod.RESETS }?.let { r -> {
                    confirm = Triple(tk("RESTART GAME?"), tr("Reboots the game and reloads its save from disk. Progress since your last in-game save will be lost.")) { r() }
                } },
                onTarget = { picking = HuntPick.TARGET },
                onFound = {
                    confirm = Triple(tr("FOUND IT?"), tr("Ends this hunt at {0}, its shiny found.", groupedNumber(hunt.count))) {
                        ShinyHunts.markFound(hunt, hunt.target, hunt.targetName)
                    }
                },
                onOptions = { options = true },
            )
        }
        when (val p = picking) {
            HuntPick.START -> OptionSelector(
                tk("HOW ARE YOU HUNTING?"), HuntMethod.entries.toList(), HuntMethod.ENCOUNTERS, { methodLabel(it) }, m,
                onPick = { method ->
                    picking = null
                    // In a battle, its foe is the likely target.
                    val foe = snapshot.battleOpponent.firstOrNull()
                    options = false
                    ShinyHunts.newHunt(method, foe?.species ?: 0, foe?.let { speciesName(it.species) }.orEmpty(), 0)
                },
                onDismiss = { picking = null },
            )
            HuntPick.METHOD -> if (hunt != null) OptionSelector(
                tk("METHOD"), HuntMethod.entries.toList(), hunt.method, { methodLabel(it) }, m,
                onPick = { picking = null; ShinyHunts.update(hunt.copy(method = it)) }, onDismiss = { picking = null },
            )
            HuntPick.TARGET -> if (hunt != null) OptionSelector(
                tk("TARGET"), targets, hunt.target, { if (it == 0) tk("ANY SHINY") else speciesName(it) }, m,
                onPick = { picking = null; ShinyHunts.update(hunt.copy(target = it, targetName = if (it == 0) "" else speciesName(it))) },
                onDismiss = { picking = null },
            )
            HuntPick.ODDS -> if (hunt != null) OptionSelector(
                tk("ODDS"), HuntOdds.CHOICES, hunt.odds, { if (it == 0) tr("GAME'S ({0})", oddsLabel(gameOdds)) else oddsLabel(it) }, m,
                onPick = { picking = null; ShinyHunts.update(hunt.copy(odds = it)) }, onDismiss = { picking = null },
                note = tk("A Shiny Charm, the Masuda method or a hack's own odds"),
            )
            HuntPick.COUNT -> if (hunt != null) OptionSelector<Int?>(
                tk("COUNT"), COUNT_STEPS, null, { n -> if (n == null) "" else if (n > 0) "+${groupedNumber(n)}" else "-${groupedNumber(-n)}" }, m,
                onPick = { picking = null; if (it != null) ShinyHunts.add(it) }, onDismiss = { picking = null },
            )
            is HuntPick.Earlier -> OptionSelector<EarlierAction?>(
                targetLabel(p.hunt), listOf(EarlierAction.CONTINUE, EarlierAction.DELETE), null,
                { if (it == EarlierAction.CONTINUE) tk("CONTINUE THIS HUNT") else tk("DELETE") }, m,
                onPick = { action ->
                    picking = null
                    if (action == EarlierAction.CONTINUE) { ShinyHunts.resume(p.hunt); options = false }
                    else confirm = Triple(tr("DELETE THIS HUNT?"), tr("Forgets this hunt and its count for good.")) { ShinyHunts.delete(p.hunt) }
                },
                onDismiss = { picking = null },
            )
            null -> {}
        }
        confirm?.let { (title, message, action) ->
            OptionConfirm(title, message, tk("YES"), m, onConfirm = { confirm = null; action() }, onDismiss = { confirm = null })
        }
    }
}

private sealed interface HuntPick {
    data object START : HuntPick
    data object METHOD : HuntPick
    data object TARGET : HuntPick
    data object ODDS : HuntPick
    data object COUNT : HuntPick
    data class Earlier(val hunt: Hunt) : HuntPick
}

private enum class EarlierAction { CONTINUE, DELETE }

/** The COUNT row's steps: a double battle's +2 is the reader's, these are for counts kept elsewhere before. */
private val COUNT_STEPS = listOf(10, 100, 1000, -1, -10, -100)

/**
 * The HUNT tab with a hunt going, one screen with nothing to scroll (the Thor's bottom screen and larger): the target as it
 * looks shiny (its front picture from the ROM, its icon where the game has none, sparkles for any shiny; a tap picks the
 * target) with its name under it; beside it the count in big digits, the odds, -1 / +1 and the hunt's numbers in a block of
 * cells (the chance by now, the next milestone, time, pace, phases, method). Under them a RESETS hunt's LOAD SLOT / RESTART
 * GAME, then FOUND IT and OPTIONS (the rest: [HuntScreen]'s options page).
 */
@Composable
private fun HuntDashboard(
    hunt: Hunt, odds: Int, m: GbaTextMetrics,
    slots: com.pokedaisy.app.companion.StateSlots?, restart: (() -> Unit)?,
    onTarget: () -> Unit, onFound: () -> Unit, onOptions: () -> Unit,
) {
    val u = m.u
    val big = rememberGbaTextMetrics(3f)
    val small = rememberGbaTextMetrics(1f)
    val dex = LocalPartyDex.current
    val picture = if (dex != null && hunt.target != 0) rememberRomBitmap(dex, hunt.target,
        com.pokedaisy.app.companion.data.PokedexSource::cachedHuntSprite, com.pokedaisy.app.companion.data.PokedexSource::huntSprite) else null
    val buttonH = m.rowHeight * 1.3f
    Column(Modifier.fillMaxSize()) {
        OptionTitleWindow(tk("SHINY HUNT"), m, trailing = methodLabel(hunt.method), joined = true)
        WindowGap(m)
        OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(u * 6), verticalArrangement = Arrangement.spacedBy(u * 4)) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 6)) {
                    // The target, as big as its half allows; a tap picks another.
                    Column(Modifier.weight(1f).fillMaxHeight().soundClickable(onClick = onTarget),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            when {
                                picture != null -> TrimmedSprite(picture, Modifier.fillMaxSize(0.9f))
                                hunt.target != 0 -> SpeciesIcon(huntIcon(hunt.target), big.lineHeight * 2f, Modifier.requiredSize(big.lineHeight * 2f))
                                else -> PixelArt(TabIcons.sparkles.bitmap, TabIcons.sparkles.palette, Modifier.size(big.lineHeight * 1.6f))
                            }
                        }
                        GbaText(targetLabel(hunt), OptionColors.label, OptionColors.labelShadow, m)
                    }
                    Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 3)) {
                        // The count with +1 / -1 beside it (not under: a 4:3 screen has no row to spare).
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u * 4)) {
                            Column(Modifier.weight(1f)) {
                                GbaText(groupedNumber(hunt.count), OptionColors.value, OptionColors.valueShadow, big)
                                GbaText(oddsLabel(odds), OptionColors.muted, OptionColors.mutedShadow, small)
                            }
                            Column(Modifier.width(m.rowHeight * 2.4f), verticalArrangement = Arrangement.spacedBy(u * 3)) {
                                // Where the D-pad starts: +1 is what a counter is pressed for.
                                OptionButton("+1", m, onClick = { ShinyHunts.add(1) }, emphasis = true,
                                    modifier = Modifier.fillMaxWidth().height(buttonH).focusStart(true))
                                OptionButton("-1", m, onClick = { ShinyHunts.add(-1) }, enabled = hunt.count > 0,
                                    modifier = Modifier.fillMaxWidth().height(m.rowHeight * 1.05f))
                            }
                        }
                        Spacer(Modifier.weight(1f, fill = false))
                        HuntNumbers(hunt, odds, m, small, Modifier.fillMaxWidth(), onPhases = onOptions)
                    }
                }
                if (slots != null || restart != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 4)) {
                    if (slots != null) OptionButton(tr("LOAD SLOT {0}", slots.currentIndex), m, onClick = { slots.requestLoad(slots.currentIndex) },
                        emphasis = true, modifier = Modifier.weight(1f).height(buttonH))
                    if (restart != null) OptionButton(tk("RESTART GAME"), m, onClick = restart, modifier = Modifier.weight(1f).height(buttonH))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 4)) {
                    OptionButton(tk("FOUND IT"), m, onClick = onFound, modifier = Modifier.weight(1f).height(buttonH))
                    OptionButton(tk("OPTIONS"), m, onClick = onOptions, modifier = Modifier.weight(1f).height(buttonH))
                }
            }
        }
    }
}

/**
 * The hunt's numbers as a block of cells, two to a row, each a label and its value: the chance of a shiny by now, the next
 * milestone (75% AT 11,356), time, pace and, once there are some, phases (a tap: OPTIONS, which lists them). Plain rows in a
 * look without cells.
 */
@Composable
private fun HuntNumbers(hunt: Hunt, odds: Int, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier, onPhases: () -> Unit) {
    val u = m.u
    val next = nextMilestone(hunt.count, odds)
    val cells = listOf(
        Triple(tk("BY NOW"), percent(HuntOdds.chanceBy(hunt.count, odds)), null),
        Triple(next?.let { tr("{0} AT", percent(it.first)) } ?: tk("BY NOW"), next?.second?.let(::groupedNumber) ?: "99%+", null),
        Triple(tk("TIME"), duration(hunt.playMillis), null),
        Triple(tk("PER HOUR"), hunt.perHour?.let(::groupedNumber) ?: "-", null),
    ) + listOfNotNull(Triple(tk("PHASES"), "${hunt.phases.size}", onPhases).takeIf { hunt.phases.isNotEmpty() })
    val c = OptionColors.cells
    val rows = cells.chunked(2)
    Column(modifier) {
        rows.forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                for (j in 0 until 2) {
                    val cell = pair.getOrNull(j)
                    var mod = Modifier.weight(1f).fillMaxHeight()
                    if (c != null) mod = mod.cell(if (r % 2 == 0) c.a else c.b, c.line, u, top = r == 0, bottom = r == rows.lastIndex, left = j == 0, right = j == 1)
                    cell?.third?.let { open -> mod = mod.soundClickable(onClick = open) }
                    // One line a cell: its label, its value at the right end.
                    Row(mod.padding(horizontal = u * 4, vertical = u * 3), verticalAlignment = Alignment.CenterVertically) {
                        if (cell != null) {
                            GbaText(cell.first, OptionColors.muted, OptionColors.mutedShadow, small, Modifier.weight(1f))
                            GbaText(cell.second, OptionColors.label, OptionColors.labelShadow, m)
                        }
                    }
                }
            }
        }
    }
}

/** No hunt going: what a hunt does, START A HUNT, and this game's earlier hunts to pick one up again. */
@Composable
private fun HuntNone(m: GbaTextMetrics, hunts: List<Hunt>, onStart: () -> Unit, onOpen: (Hunt) -> Unit) {
    val u = m.u
    var cursor by rememberSaveable { mutableStateOf(1) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 640.dp)) {
            OptionTitleWindow(tk("SHINY HUNT"), m, trailing = tk("NO HUNT"))
            Spacer(Modifier.height(u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.padding(horizontal = u * 8, vertical = u * 6), verticalArrangement = Arrangement.spacedBy(u * 6)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(u * 6)) {
                        GbaText(
                            tr("Start a hunt and the companion counts it for you: wild encounters, resets or hatched eggs. It keeps your odds and time, stops fast forward when a shiny shows up, and holds LOAD STATE for a moment so a slip can't lose it."),
                            OptionColors.label, OptionColors.labelShadow, m, maxLines = Int.MAX_VALUE,
                        )
                    }
                    OptionButton(tk("START A HUNT"), m, onClick = onStart, emphasis = true,
                        modifier = Modifier.fillMaxWidth().height(m.rowHeight * 1.4f).focusStart(true))
                }
            }
            if (hunts.isNotEmpty()) {
                Spacer(Modifier.height(u * 4))
                val icon: (Int) -> @Composable () -> Unit = { species ->
                    { Box { SpeciesIcon(huntIcon(species), m.lineHeight * 1.6f, Modifier.requiredSize(m.lineHeight * 1.6f)) } }
                }
                val rows = listOf(groupTitle(tk("EARLIER HUNTS"))) + hunts.map { h -> earlierRow(h, icon) { onOpen(h) } }
                OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    GroupedRows(rows, m, cursor.coerceIn(0, rows.lastIndex), onClick = { i -> cursor = i; rows[i].onClick() },
                        labelWeight = 0.6f, onFocused = { cursor = it })
                }
            }
        }
    }
}

private fun earlierRow(h: Hunt, icon: (Int) -> @Composable () -> Unit, onClick: () -> Unit): SettingRow {
    val shown = h.found?.species ?: h.target
    return SettingRow(
        h.found?.name?.takeIf { it.isNotEmpty() } ?: targetLabel(h), groupedNumber(h.count),
        labelIcon = if (shown != 0) icon(shown) else null,
        subtitle = when (h.status) {
            HuntStatus.FOUND -> tr("FOUND · {0}", methodLabel(h.method))
            HuntStatus.ON_HOLD -> tr("ON HOLD · {0}", methodLabel(h.method))
            HuntStatus.ACTIVE -> methodLabel(h.method)
        },
        onClick = onClick,
    )
}

/** A hunt's Pokémon's party icon, in its shiny colours where the game has them. */
internal fun huntIcon(species: Int): String? =
    com.pokedaisy.app.companion.data.shinyIconAsset(species) ?: spriteAsset("pokemon", species)

private fun targetLabel(h: Hunt) = if (h.target == 0) tk("ANY SHINY") else h.targetName.ifEmpty { speciesName(h.target) }

internal fun methodLabel(m: HuntMethod) = when (m) {
    HuntMethod.ENCOUNTERS -> tk("ENCOUNTERS")
    HuntMethod.RESETS -> tk("RESETS")
    HuntMethod.EGGS -> tk("EGGS")
    HuntMethod.BY_HAND -> tk("BY HAND")
}

private fun methodLine(m: HuntMethod) = when (m) {
    HuntMethod.ENCOUNTERS -> tk("Every wild POKéMON you meet counts")
    HuntMethod.RESETS -> tk("A gift, a starter or a legendary: each new one after LOAD STATE counts")
    HuntMethod.EGGS -> tk("Every egg that hatches counts")
    HuntMethod.BY_HAND -> tk("Only + and - count")
}

private fun oddsLabel(odds: Int) = tr("1 IN {0}", groupedNumber(odds))

private fun percent(p: Double): String = when {
    p >= 0.999 -> "99%+"
    p > 0 && p < 0.01 -> "<1%"
    else -> "${(p * 100).toInt()}%"
}

/** The next of 50 / 75 / 90 / 99% not reached yet and the count it comes at; null past them all. */
private fun nextMilestone(count: Int, odds: Int): Pair<Double, Int>? =
    listOf(0.5, 0.75, 0.9, 0.99).map { it to HuntOdds.countFor(it, odds) }.firstOrNull { it.second > count }

private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return tr("{0}H {1}M", minutes / 60, (minutes % 60).toString().padStart(2, '0'))
}
