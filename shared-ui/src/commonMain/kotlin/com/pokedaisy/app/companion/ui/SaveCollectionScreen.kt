package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.data.save.Gen3SaveEditor
import com.pokedaisy.app.companion.data.save.Gen4SaveEditor
import com.pokedaisy.app.companion.data.save.MonRef
import com.pokedaisy.app.companion.data.save.SaveCollection
import com.pokedaisy.app.companion.data.save.TradeSide
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr

/**
 * TOOLS > COLLECTION: every Pokémon in the player's saves in one list, found by name, game or shininess, in Pokédex,
 * level or save order. A tap offers what can be done with it: OPEN its save in the editor on it, TRADE it to another
 * save of its generation, or PAL PARK (a GBA game's PC Pokémon, to a DS game).
 */
class CollectionState {
    /** The saves read (null while they're being read). */
    var sides by mutableStateOf<List<TradeSide>?>(null)
    var items by mutableStateOf<List<SaveCollection.Item>>(emptyList())
    var query by mutableStateOf("")
    /** The save shown (an index into [sides]), or -1 for all. */
    var game by mutableIntStateOf(-1)
    var shinyOnly by mutableStateOf(false)
    var sort by mutableStateOf(SaveCollection.Sort.DEX)
    /** The Pokémon whose OPEN / TRADE / PAL PARK choice is up. */
    var picked by mutableStateOf<SaveCollection.Item?>(null)
    var pickingGame by mutableStateOf(false)
    var pickingSort by mutableStateOf(false)
    /** LIST (a row each), GRID (sprites in a grid) or CARDS (a card each with everything about it). */
    var view by mutableStateOf(CollectionView.LIST)
    var pickingView by mutableStateOf(false)

    fun load(read: List<TradeSide>, gathered: List<SaveCollection.Item> = SaveCollection.gather(read)) {
        sides = read
        items = gathered
        if (game >= read.size) game = -1
    }

    /** What the list shows now. */
    fun shown(): List<SaveCollection.Item> {
        val q = query.trim().lowercase()
        val list = items.filter { item ->
            (game < 0 || item.source == game) &&
                (!shinyOnly || (item.mon.isShiny && !item.mon.isEgg)) &&
                (q.isEmpty() || monLabel(item.mon).lowercase().contains(q) ||
                    (!item.mon.isEgg && speciesLabel(item.mon.species, item.mon.game).lowercase().contains(q)))
        }
        return SaveCollection.sorted(list, sort)
    }

    /** Whether [item] has a save of its own generation to trade with. */
    fun canTrade(item: SaveCollection.Item): Boolean {
        val all = sides ?: return false
        val rules = all[item.source].rules
        return all.withIndex().any { (i, s) -> i != item.source && s.rules == rules }
    }

    /** Whether [item] could go through Pal Park: a GBA game's PC Pokémon, and a DS game among the saves. */
    fun canPalPark(item: SaveCollection.Item): Boolean =
        item.palParkable && sides.orEmpty().any { it.editor is Gen4SaveEditor } && sides!![item.source].editor is Gen3SaveEditor
}

@Composable
fun CollectionScreen(
    state: CollectionState,
    m: GbaTextMetrics,
    onBack: () -> Unit,
    icons: (Int) -> MonIconLoader?,
    onOpen: (SaveCollection.Item) -> Unit,
    onTrade: (SaveCollection.Item) -> Unit,
    onPalPark: ((SaveCollection.Item) -> Unit)? = null,
) {
    val small = rememberGbaTextMetrics(textScale = 1f)
    val sides = state.sides
    val shown = state.shown()
    // Room for the line under each row (its game and where it is).
    val rowHeight = m.rowHeight + subtitleMetrics(m).lineHeight + m.u * 2
    Box(Modifier.fillMaxSize()) {
        AppBackdrop()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ToolHeader(
                tk("COLLECTION"), "COLLECTION", m, onBack = onBack,
                trailing = sides?.let { tr("{0} POKéMON · {1} KINDS", state.items.size, SaveCollection.species(state.items)) },
            )
            Spacer(Modifier.height(m.u * 4))
            // The search box beside the buttons; on a narrow screen (a phone held upright) above them.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val narrow = maxWidth < 640.dp
                val search: @Composable (Modifier) -> Unit = { mod ->
                    OptionTextField(
                        state.query, { state.query = it.take(12) }, m, mod,
                        placeholder = tr("A name or a species"), leadingIcon = PixelIcons.search,
                    )
                }
                val buttons: @Composable () -> Unit = {
                    OptionButton(
                        if (state.game < 0) tk("ALL GAMES") else sides?.getOrNull(state.game)?.label ?: tk("ALL GAMES"), m,
                        onClick = { state.pickingGame = true }, enabled = sides != null,
                    )
                    OptionButton(tk("SHINY"), m, onClick = { state.shinyOnly = !state.shinyOnly }, emphasis = state.shinyOnly)
                    OptionButton(sortLabel(state.sort), m, onClick = { state.pickingSort = true })
                    OptionButton(viewLabel(state.view), m, onClick = { state.pickingView = true })
                }
                if (narrow) Column(verticalArrangement = Arrangement.spacedBy(m.u * 4)) {
                    search(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(m.u * 4), verticalAlignment = Alignment.CenterVertically) { buttons() }
                } else Row(horizontalArrangement = Arrangement.spacedBy(m.u * 4), verticalAlignment = Alignment.CenterVertically) {
                    search(Modifier.weight(1f))
                    buttons()
                }
            }
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.weight(1f).fillMaxWidth()) {
                when {
                    sides == null -> CollectionMessage(tr("Reading your saves…"), m, loading = true)
                    sides.isEmpty() -> CollectionMessage(tr("No game here has a save the save editor can read yet."), m)
                    shown.isEmpty() -> CollectionMessage(tr("No Pokémon like that in your saves."), m)
                    state.view != CollectionView.LIST -> CollectionTiles(shown, sides, state.view, m, small, icons) { state.picked = it }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(shown.size, key = { "${shown[it].source}:${shown[it].at}" }) { index ->
                            val item = shown[index]
                            val side = sides[item.source]
                            val mon = item.mon
                            val name = monLabel(mon)
                            val species = if (mon.isEgg) null else speciesLabel(mon.species, mon.game)
                            val label = buildString {
                                if (item.national > 0 && !mon.isEgg) append("No.${item.national.toString().padStart(3, '0')} ")
                                append(name)
                                if (species != null && species != name) append(" ($species)")
                            }
                            OptionLine(
                                label, if (mon.isEgg) null else tr("Lv{0}", item.level), selected = false, m,
                                height = rowHeight, labelWeight = 0.75f, divider = true, stripe = index % 2 == 1,
                                valueBadge = if (mon.isShiny && !mon.isEgg) tk("SHINY") else null,
                                subtitle = "${side.label} · ${whereIn(item, side)}",
                                leading = icons(item.source)?.let { loader -> @Composable { BoxMonIcon(loader, mon, m.rowHeight) } },
                            ) { state.picked = item }
                        }
                    }
                }
            }
            if (sides != null) {
                Spacer(Modifier.height(m.u * 3))
                val hint = if (onPalPark != null) tr("{0} shown · tap one to open its save, trade it or send it to Pal Park", shown.size)
                    else tr("{0} shown · tap one to open its save or trade it", shown.size)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GbaText(hint, OptionColors.onBackdrop, OptionColors.onBackdropShadow, small, Modifier.weight(1f), maxLines = 2)
                    // GRID: names under the sprites or not, like the other box grids.
                    if (state.view == CollectionView.GRID) MonNamesButton(m)
                }
            }
        }

        state.picked?.let { item ->
            val mon = item.mon
            OptionOverlay({ state.picked = null }, Modifier.widthIn(max = 520.dp)) {
                Column {
                    OptionTitleWindow(monLabel(mon), m, trailing = sides?.getOrNull(item.source)?.label)
                    Spacer(Modifier.height(m.u * 4))
                    OptionListWindow(m, Modifier.fillMaxWidth()) {
                        Column {
                            OptionLine(tk("OPEN"), null, selected = false, m, height = rowHeight, divider = true, chevron = true,
                                subtitle = tr("Its save in the editor, on this Pokémon")) { state.picked = null; onOpen(item) }
                            OptionLine(tk("TRADE"), null, selected = false, m, height = rowHeight, divider = true, chevron = true, enabled = state.canTrade(item),
                                subtitle = tr("To another of your saves")) { state.picked = null; onTrade(item) }
                            if (onPalPark != null && state.canPalPark(item)) {
                                OptionLine(tk("PAL PARK"), null, selected = false, m, height = rowHeight, divider = true, chevron = true,
                                    subtitle = tr("To Sinnoh or Johto")) { state.picked = null; onPalPark(item) }
                            }
                            OptionLine(tk("CANCEL"), null, selected = false, m) { state.picked = null }
                        }
                    }
                }
            }
        }
        if (state.pickingGame && sides != null) {
            OptionSelector(
                tk("GAME"), listOf(-1) + sides.indices.toList(), state.game, { if (it < 0) tk("ALL GAMES") else sides[it].label }, m,
                onPick = { state.game = it; state.pickingGame = false }, onDismiss = { state.pickingGame = false },
            )
        }
        if (state.pickingView) {
            OptionSelector(
                tk("VIEW"), CollectionView.entries, state.view, ::viewLabel, m,
                onPick = { state.view = it; state.pickingView = false }, onDismiss = { state.pickingView = false },
            )
        }
        if (state.pickingSort) {
            OptionSelector(
                tk("SORT"), SaveCollection.Sort.entries, state.sort, ::sortLabel, m,
                onPick = { state.sort = it; state.pickingSort = false }, onDismiss = { state.pickingSort = false },
            )
        }
    }
}

/** How COLLECTION lays its Pokémon out. */
enum class CollectionView { LIST, GRID, CARDS }

private fun viewLabel(v: CollectionView): String = when (v) {
    CollectionView.LIST -> tk("LIST")
    CollectionView.GRID -> tk("GRID")
    CollectionView.CARDS -> tk("CARDS")
}

/**
 * GRID and CARDS: GRID a tile each - the sprite as large as the tile lets it be, its level in the corner, its name under it
 * (each game's own sprite: a Pokémon looks as its own game draws it); CARDS a wider card each - the sprite beside its name,
 * species, level, game and where it is in that save.
 */
@Composable
private fun CollectionTiles(
    shown: List<SaveCollection.Item>, sides: List<TradeSide>, view: CollectionView, m: GbaTextMetrics, small: GbaTextMetrics,
    icons: (Int) -> MonIconLoader?, onPick: (SaveCollection.Item) -> Unit,
) {
    val cards = view == CollectionView.CARDS
    val u = m.u
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (cards) 260.dp else 96.dp),
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(u * 3),
        verticalArrangement = Arrangement.spacedBy(u * 3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(u * 2),
    ) {
        items(shown.size, key = { "${shown[it].source}:${shown[it].at}" }) { i ->
            val item = shown[i]
            val mon = item.mon
            val side = sides[item.source]
            val loader = icons(item.source)
            val level = if (mon.isEgg) "" else tr("Lv{0}", item.level)
            val shiny = mon.isShiny && !mon.isEgg
            Box(
                Modifier
                    .then(if (cards) Modifier else Modifier.aspectRatio(0.86f))
                    .collectionCard(u)
                    .soundClickable { onPick(item) }
                    .padding(u * 3),
            ) {
                if (cards) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(m.lineHeight * 3), contentAlignment = Alignment.Center) {
                        loader?.let { BoxMonIcon(it, mon, m.lineHeight * 3) }
                    }
                    Spacer(Modifier.width(u * 4))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GbaText(monLabel(mon), OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f, fill = false), bold = modernView)
                            if (shiny) PixelIcon(PixelIcons.sparkle, OptionColors.favorite, Modifier.padding(start = u * 2).size(m.lineHeight * 0.7f))
                        }
                        val species = if (mon.isEgg) null else speciesLabel(mon.species, mon.game)
                        GbaText(
                            listOfNotNull(
                                item.national.takeIf { it > 0 && !mon.isEgg }?.let { "No.${it.toString().padStart(3, '0')}" },
                                species?.takeIf { it != monLabel(mon) }, level.ifEmpty { null },
                            ).joinToString(" · "),
                            OptionColors.value, OptionColors.valueShadow, small,
                        )
                        GbaText("${side.label} · ${whereIn(item, side)}", OptionColors.muted, OptionColors.mutedShadow, small)
                    }
                } else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val names = MonCellNames.shown
                    val sprite = (if (names) maxHeight - small.lineHeight else maxHeight).coerceAtMost(maxWidth)
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            loader?.let { BoxMonIcon(it, mon, sprite) }
                        }
                        if (names) GbaText(monLabel(mon), OptionColors.label, OptionColors.labelShadow, small)
                    }
                    Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                        if (level.isNotEmpty()) GbaText(level, OptionColors.value, OptionColors.valueShadow, small)
                        if (shiny) PixelIcon(PixelIcons.sparkle, OptionColors.favorite, Modifier.size(small.lineHeight * 0.7f))
                    }
                }
            }
        }
    }
}

private fun sortLabel(s: SaveCollection.Sort): String = when (s) {
    SaveCollection.Sort.DEX -> tk("BY NUMBER")
    SaveCollection.Sort.LEVEL -> tk("BY LEVEL")
    SaveCollection.Sort.GAME -> tk("BY GAME")
}

/** Where in its save it is: PARTY, or its box's name. */
private fun whereIn(item: SaveCollection.Item, side: TradeSide): String = when (val at = item.at) {
    is MonRef.Party -> tr("PARTY")
    is MonRef.Pc -> side.editor.data.boxName(at.at.box).trim().ifEmpty { "BOX ${at.at.box + 1}" }
}

/** A tile / card: the title window's paper in its frame, like the editor's slots. */
private fun Modifier.collectionCard(u: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    drawLayeredBox(OptionColors.titleLayers.inPx(u.toPx()), OptionColors.titleFill, radius = 3 * u.toPx())
}

@Composable
private fun CollectionMessage(text: String, m: GbaTextMetrics, loading: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(m.u * 4), verticalAlignment = Alignment.CenterVertically) {
        // The app's "working on it", while every save is read (a big library takes a while).
        if (loading) RockingPokeBall(Modifier.padding(end = m.u * 4).size(m.lineHeight * 1.4f))
        GbaText(text, OptionColors.label, OptionColors.labelShadow, m, maxLines = Int.MAX_VALUE)
    }
}
