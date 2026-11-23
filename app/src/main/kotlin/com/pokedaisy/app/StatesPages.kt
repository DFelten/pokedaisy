package com.pokedaisy.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.GbaText
import com.pokedaisy.app.companion.ui.GbaTextMetrics
import com.pokedaisy.app.companion.ui.OptionButton
import com.pokedaisy.app.companion.ui.OptionColors
import com.pokedaisy.app.companion.ui.OptionListWindow
import com.pokedaisy.app.companion.ui.PixelRoundedShape
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A state's name on the SAVE STATES pages. */
internal fun stateLabel(slot: Int): String = when (slot) {
    StatesBrowser.RESUME -> tk("RESUME")
    StatesBrowser.RETRY -> tk("BOSS RETRY")
    else -> tr("SLOT {0}", slot)
}

private fun dateLabel(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** A state's picture, read off the UI thread; null while it loads or when there's none. */
@Composable
private fun rememberThumb(file: File?, tick: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, file, tick) {
        value = file?.let { f ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    return bitmap
}

/** A state's picture at the game's own shape, crisp (each game pixel a block), or a plain well while there's none. */
@Composable
private fun StateThumb(file: File?, tick: Int, maxWidth: Dp, modifier: Modifier = Modifier, maxHeight: Dp = Dp.Infinity) {
    val bitmap = rememberThumb(file, tick)
    val ratio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1.5f
    // A tall picture (a DS game's two screens) keeps within [maxHeight].
    val width = if (maxHeight == Dp.Infinity) maxWidth else minOf(maxWidth, maxHeight * ratio)
    val shape = PixelRoundedShape(width / 40)
    Box(modifier.width(width).aspectRatio(ratio), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxWidth().aspectRatio(ratio),
                contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(OptionColors.muted.copy(alpha = 0.25f), shape))
        }
    }
}

/** A row or tile that the D-pad can land on: a ring while focused. */
@Composable
private fun Modifier.pick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    return this.gbaFocusRing(focused, PixelRoundedShape(Dp(4f)))
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** What the SAVE STATES page shows: the folder in use and every game's states in it. */
internal data class StatesModel(
    val root: File,
    val custom: Boolean,
    /** Null while listing. */
    val games: List<StatesBrowser.Game>?,
    /** "12 / 40" while states move to another folder. */
    val moving: String? = null,
)

/** SAVE STATES: where states go (CHANGE FOLDER / USE DEFAULT), then a game per row with its newest picture. */
@Composable
internal fun StatesFolderPage(
    model: StatesModel,
    tick: Int,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onChangeFolder: () -> Unit,
    onUseDefault: () -> Unit,
    onCopyPath: () -> Unit,
    onOpenGame: (StatesBrowser.Game) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                GbaText(tr("Where every game's save states and their pictures go, a folder for each game."),
                    OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE)
                GbaText(tr("A folder of your own keeps them when the app is removed. Changing it moves the states already made."),
                    OptionColors.muted, OptionColors.mutedShadow, small, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(top = m.u * 2))
                GbaText(if (model.custom) tk("CUSTOM") else tk("DEFAULT"), OptionColors.value, OptionColors.valueShadow, small,
                    modifier = Modifier.padding(top = m.u * 4))
                GbaText(shortPath(model.root.absolutePath), OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 3)
                model.moving?.let {
                    GbaText(tr("MOVING STATES… {0}", it), OptionColors.value, OptionColors.valueShadow, small, modifier = Modifier.padding(top = m.u * 2))
                }
                Row(Modifier.padding(vertical = m.u * 4), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                    OptionButton(tk("CHANGE FOLDER"), small, enabled = model.moving == null, onClick = onChangeFolder)
                    if (model.custom) OptionButton(tk("USE DEFAULT"), small, enabled = model.moving == null, onClick = onUseDefault)
                    OptionButton(tk("COPY PATH"), small, onClick = onCopyPath)
                }
            }
        }
        Spacer(Modifier.height(m.u * 4))
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                val games = model.games
                when {
                    games == null -> GbaText(tr("LOOKING FOR STATES…"), OptionColors.muted, OptionColors.mutedShadow, small)
                    games.isEmpty() -> GbaText(tr("No save states yet. Make one in a game: the companion's STATES tab, or a hotkey."),
                        OptionColors.muted, OptionColors.mutedShadow, small, maxLines = Int.MAX_VALUE)
                    else -> {
                        GbaText(tr("{0} GAME(S) · {1}", games.size, SettingsActivity.humanSize(games.sumOf { it.bytes })),
                            OptionColors.muted, OptionColors.mutedShadow, small)
                        games.forEach { g ->
                            Row(Modifier.fillMaxWidth().padding(top = m.u * 4).pick { onOpenGame(g) },
                                verticalAlignment = Alignment.CenterVertically) {
                                StateThumb(g.cover, tick, m.u * 48)
                                Spacer(Modifier.width(m.u * 6))
                                Column(Modifier.weight(1f)) {
                                    GbaText(g.name ?: tr("UNKNOWN GAME ({0})", g.key.uppercase()), OptionColors.label, OptionColors.labelShadow, m, maxLines = 2)
                                    GbaText(tr("{0} STATE(S) · {1}", g.states.size, SettingsActivity.humanSize(g.bytes)),
                                        OptionColors.muted, OptionColors.mutedShadow, small)
                                    GbaText(dateLabel(g.newest), OptionColors.muted, OptionColors.mutedShadow, small)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(m.u * 4))
            }
        }
    }
}

/** One game's states: a picture each, its name and date; a tap opens it large ([StatePreview]). */
@Composable
internal fun StatesGamePage(
    game: StatesBrowser.Game,
    tick: Int,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onOpen: (StatesBrowser.State) -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                GbaText(game.name ?: tr("UNKNOWN GAME ({0})", game.key.uppercase()), OptionColors.label, OptionColors.labelShadow, m, maxLines = 2)
                GbaText(tr("{0} STATE(S) · {1}", game.states.size, SettingsActivity.humanSize(game.bytes)),
                    OptionColors.muted, OptionColors.mutedShadow, small)
                GbaText(shortPath(game.dir.absolutePath), OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 3)
                Row(Modifier.padding(vertical = m.u * 4)) {
                    OptionButton(tk("DELETE ALL"), small, onClick = onDeleteAll)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = m.u * 6
                    // As many pictures a row as fit at least ~100 GBA pixels wide each.
                    val perRow = ((maxWidth + gap) / (m.u * 100 + gap)).toInt().coerceIn(1, 5)
                    val width = (maxWidth - gap * (perRow - 1)) / perRow
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        game.states.chunked(perRow).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { s ->
                                    Column(Modifier.width(width).pick { onOpen(s) }) {
                                        StateThumb(s.thumb, tick, width)
                                        GbaText(stateLabel(s.slot), OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.padding(top = m.u * 2))
                                        GbaText(dateLabel(s.savedAt), OptionColors.muted, OptionColors.mutedShadow, small)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(m.u * 4))
            }
        }
    }
}

/** A state's picture large, with DELETE. */
@Composable
internal fun StatePreview(
    game: StatesBrowser.Game,
    state: StatesBrowser.State,
    tick: Int,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.pokedaisy.app.companion.ui.OptionOverlay(onDismiss, Modifier.widthIn(max = m.u * 260)) {
        Column {
            com.pokedaisy.app.companion.ui.OptionTitleWindow(stateLabel(state.slot), m, onBack = onDismiss)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = m.u * 4)) {
                    // At the GBA pixel's size (whole screen pixels a picture pixel), never past the screen.
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StateThumb(state.thumb, tick, minOf(maxWidth, m.u * 240), maxHeight = m.u * 160)
                    }
                    GbaText(game.name ?: game.key.uppercase(), OptionColors.label, OptionColors.labelShadow, small,
                        maxLines = 2, modifier = Modifier.padding(top = m.u * 4))
                    GbaText("${dateLabel(state.savedAt)} · ${SettingsActivity.humanSize(state.bytes)}", OptionColors.muted, OptionColors.mutedShadow, small)
                    Row(Modifier.fillMaxWidth().padding(vertical = m.u * 4), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                        Spacer(Modifier.weight(1f))
                        OptionButton(tk("DELETE"), small, onClick = onDelete)
                        OptionButton(tk("CLOSE"), small, emphasis = true, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

/** SETTINGS FILE: every setting into one file to keep, and back from one. */
@Composable
internal fun SettingsFilePage(
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    busy: Boolean,
    message: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                SettingsFileText(small)
                Row(Modifier.padding(vertical = m.u * 4), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                    OptionButton(tk("EXPORT"), small, emphasis = true, enabled = !busy, onClick = onExport)
                    OptionButton(tk("IMPORT"), small, enabled = !busy, onClick = onImport)
                }
                message?.let { GbaText(it, OptionColors.value, OptionColors.valueShadow, small, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(bottom = m.u * 4)) }
            }
        }
    }
}

/** What a settings file holds and leaves out: the SETTINGS FILE page's and the setup step's words. */
@Composable
internal fun SettingsFileText(small: GbaTextMetrics) {
    GbaText(tr("Save every setting to one file: keep it to put them all back after a reinstall or on another device."),
        OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE)
    GbaText(tr("It holds your options, buttons, hotkeys, cheats, game names and folders. Sign-ins and keys stay out: sign in again after an import."),
        OptionColors.muted, OptionColors.mutedShadow, small, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(top = m2(small)))
}

private fun m2(small: GbaTextMetrics): Dp = small.u * 2
