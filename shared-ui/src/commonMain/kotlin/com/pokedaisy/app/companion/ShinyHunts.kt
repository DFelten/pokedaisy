package com.pokedaisy.app.companion

import androidx.compose.runtime.mutableStateOf
import com.pokedaisy.app.Prefs
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.Hunt
import com.pokedaisy.app.companion.data.HuntCounter
import com.pokedaisy.app.companion.data.HuntLog
import com.pokedaisy.app.companion.data.HuntMethod
import com.pokedaisy.app.companion.data.HuntShiny
import com.pokedaisy.app.companion.data.HuntStatus
import com.pokedaisy.app.companion.data.ShinyHunt
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.isGameBoyGame
import com.pokedaisy.app.companion.data.isGen2
import com.pokedaisy.app.companion.i18n.tr
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * The HUNT tab's game ([FlaggedFeatures.shinyHunt]): the running ROM's [HuntLog] as Compose state, its active hunt counted
 * from the activity's samples ([onSample]) and state loads ([stateLoaded]), and LOAD STATE held over a shiny ([loadHeld]):
 * a slip of the load button must not throw one away (a player's request). Prefs keeps the log per ROM CRC32; previews
 * store nothing ([preview]).
 */
object ShinyHunts {
    private val logState = mutableStateOf(HuntLog())
    private var save: (String) -> Unit = {}
    private val counter = HuntCounter()
    private val lock = SynchronizedObject()
    private var generation = 0
    private var lastSampleAt = 0L
    /** LOAD STATE waits until then (epoch ms) and while [shinyBattle] lasts. */
    private var holdUntil = 0L
    private var shinyBattle = false

    const val NOTICE_MILLIS = 8000L
    /** How long LOAD STATE / RESTART stay held after a shiny shows (and after its battle). */
    const val HOLD_MILLIS = 10_000L
    /** Sample gaps longer than this (a pause, sleep) don't count as hunting time. */
    private const val MAX_GAP_MILLIS = 5_000L

    val log: HuntLog get() = logState.value

    private val gameOddsState = androidx.compose.runtime.mutableIntStateOf(com.pokedaisy.app.companion.data.ShinyOdds.NORMAL)
    /** The running game's own odds (SHINY ODDS' pick where it's on), for a hunt that didn't set its own. */
    var gameOdds: Int
        get() = gameOddsState.intValue
        set(v) { if (gameOddsState.intValue != v) gameOddsState.intValue = v }
    val active: Hunt? get() = logState.value.active

    /** Games with a shiny to find (Gen 1 has none): the tab's there, counting by hand at least. */
    fun supports(game: GameKind?): Boolean = game != null && !(game.isGameBoyGame && !game.isGen2)

    /**
     * What the game screen should say: a shiny met by the active hunt ([phase]: not the target, the hunt goes on), or
     * [repeat]: a reset gave the same Pokémon again.
     */
    data class Found(val name: String, val count: Int, val phase: Boolean, val repeat: Boolean = false) {
        val shiny get() = !repeat
        fun message() = when {
            repeat -> tr("Same {0} as last time: wait a moment longer before you go", name)
            phase -> tr("SHINY {0}! A phase after {1}: the hunt goes on", name, count)
            else -> tr("SHINY {0}! Found after {1}", name, count)
        }
    }

    fun start(prefs: Prefs, romKey: String): Int = synchronized(lock) {
        load(prefs.huntsFor(romKey)) { prefs.setHuntsFor(romKey, it) }
        ++generation
    }

    fun close(game: Int) = synchronized(lock) {
        if (game == generation) {
            generation++
            load(null) {}
        }
    }

    private fun load(stored: String?, save: (String) -> Unit) {
        this.save = save
        logState.value = HuntLog.decode(stored)
        // A game starting (a launch, RESTART GAME) is a reset too: its first Pokémon is a try.
        counter.restart()
        counter.reset()
        lastSampleAt = 0L
        holdUntil = 0L
        shinyBattle = false
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    /**
     * A fresh sample (the activity's afterSample, any thread): the active hunt's count and time move, and a shiny it met
     * comes back for the notice (FF stopped, the fanfare). Nothing without an active hunt.
     */
    fun onSample(game: Int, s: SnapshotView, reads: Boolean, now: Long = now()): Found? = synchronized(lock) {
        if (!s.inBattle) shinyBattle = false
        val h = logState.value.active
        if (game != generation || h == null || !s.connected || s.unsupported || !supports(s.game)) {
            lastSampleAt = 0L
            return@synchronized null
        }
        val gap = if (lastSampleAt == 0L) 0L else (now - lastSampleAt).coerceIn(0L, MAX_GAP_MILLIS)
        lastSampleAt = now
        // [reads]: the game's shinies are read (SHINY ALERT's games); else only the time and the buttons count.
        val step = if (reads) counter.sample(h.method, s.inBattle, ShinyHunt.wild(s), s.battleOpponent, s.party) else HuntCounter.Step(0, null)
        var next = h.copy(count = h.count + step.add, playMillis = h.playMillis + gap)
        var found: Found? = null
        step.shiny?.let { mon ->
            holdUntil = now + HOLD_MILLIS
            if (s.inBattle) shinyBattle = true
            val met = HuntShiny(mon.species, mon.shownName, next.count, now)
            found = if (h.wants(mon.species)) {
                next = next.copy(found = met, status = HuntStatus.FOUND, ended = now)
                Found(mon.shownName, next.count, phase = false)
            } else {
                next = next.copy(phases = next.phases + met)
                Found(mon.shownName, next.count, phase = true)
            }
        }
        if (step.repeat && found == null) found = Found((s.battleOpponent.firstOrNull() ?: s.party.lastOrNull())?.shownName.orEmpty(), next.count, phase = false, repeat = true)
        if (next != h) store(logState.value.with(next))
        found
    }

    /** A state was loaded or the game restarted: the battle and party start over, and a RESETS hunt waits for its next try. */
    fun stateLoaded(game: Int) = synchronized(lock) {
        if (game != generation) return@synchronized
        counter.reset()
        shinyBattle = false
    }

    /** Whether LOAD STATE / RESTART GAME should wait: a shiny is on screen (its battle, or a few seconds after it showed). */
    fun loadHeld(now: Long = now()): Boolean = synchronized(lock) { FlaggedFeatures.shinyHunt && (shinyBattle || now < holdUntil) }

    /** SHINY ALERT's shiny (no hunt going): held all the same. */
    fun shinyShown(inBattle: Boolean, now: Long = now()) = synchronized(lock) {
        holdUntil = now + HOLD_MILLIS
        if (inBattle) shinyBattle = true
    }

    // The tab's changes.

    fun newHunt(method: HuntMethod, target: Int, targetName: String, odds: Int) = synchronized(lock) {
        counter.restart()
        store(logState.value.with(Hunt(id = now(), method = method, target = target, targetName = targetName, odds = odds)))
    }

    fun update(h: Hunt) = synchronized(lock) { store(logState.value.with(h)) }

    /** The + / - buttons (and the hunt's count set by hand): never below 0. */
    fun add(n: Int) = synchronized(lock) {
        val h = logState.value.active ?: return@synchronized
        store(logState.value.with(h.copy(count = (h.count + n).coerceAtLeast(0))))
    }

    fun hold(h: Hunt) = update(h.copy(status = HuntStatus.ON_HOLD))
    fun resume(h: Hunt) = synchronized(lock) {
        counter.restart()
        store(logState.value.with(h.copy(status = HuntStatus.ACTIVE, found = null, ended = 0)))
    }
    fun markFound(h: Hunt, species: Int, name: String) =
        update(h.copy(status = HuntStatus.FOUND, found = HuntShiny(species, name, h.count, now()), ended = now()))
    fun delete(h: Hunt) = synchronized(lock) { store(logState.value.without(h.id)) }

    private fun store(l: HuntLog) {
        // Long logs lose their oldest finished hunts first.
        val kept = if (l.hunts.size <= HuntLog.MAX_HUNTS) l else {
            val drop = l.hunts.asReversed().filter { it.status == HuntStatus.FOUND }.take(l.hunts.size - HuntLog.MAX_HUNTS).map { it.id }.toSet()
            HuntLog(l.hunts.filter { it.id !in drop })
        }
        logState.value = kept
        save(kept.encode())
    }

    /** Screenshot tests and ui-preview: [l] shown, stored nowhere. */
    fun preview(l: HuntLog): Int = synchronized(lock) {
        load(l.encode()) {}
        ++generation
    }
}
