package com.pokedaisy.app.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pokedaisy.app.Prefs
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.PokedexSource
import com.pokedaisy.app.companion.data.PokedexState
import com.pokedaisy.app.companion.data.SnapshotView
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Playing without spoilers ([FlaggedFeatures.spoilerFree], docs/plans/spoiler-free.md): SETTINGS > SPOILERS per ROM, and
 * what the game has shown the player so far, so each screen hides the rest as "???".
 *
 * What counts as seen: the save's Pokédex seen marks, the species the companion itself has read in that ROM (party, boxes,
 * every battle's foes: the games whose Pokédex isn't read), and what the player tapped open ([reveal]). Prefs keeps the
 * choice, the reveals and that list per ROM CRC32. Off the flag, or never answered, nothing is hidden.
 *
 * The question ([asking]): a new or early save the first time the player is in it ([onSample]), else the first time a page
 * that can spoil opens ([pageOpened]). Never answered = SHOW.
 */
object Spoilers {
    const val UNANSWERED = -1
    const val SHOW = 0
    const val HIDE = 1

    private val choiceState = mutableIntStateOf(UNANSWERED)
    private val shownState = mutableStateOf<Set<String>>(emptySet())
    private val seenState = mutableStateOf<Set<Int>>(emptySet())
    /** The highest level of each species in the party and boxes, from the last sample (not kept). */
    private val levelsState = mutableStateOf<Map<Int, Int>>(emptyMap())
    private var prefs: Prefs? = null
    private var romKey: String? = null
    private val lock = SynchronizedObject()
    private var generation = 0
    /** Asked once a run at most, whatever the answer. */
    private var askedThisRun = false
    private var inGameSamples = 0

    /** The question is up ([com.pokedaisy.app.companion.ui.SpoilerAskHost]). */
    var asking by mutableStateOf(false)
    /** Asked over a page the companion opened (the companion's own screen, even with one screen), not where the tips go. */
    var askingOnCompanion by mutableStateOf(false)

    /** Samples in the game before an early save is asked: the field first, not the moment it fades in. */
    private const val ASK_AFTER_SAMPLES = 3

    /** Pages whose first opening asks a save that's further on (the companion's tab ids). Not BATTLE: it opens by itself
     * as a battle starts, when the player is busy. */
    val SPOILING_PAGES = setOf("DEX", "GUIDE")

    /** The running game's answer: [UNANSWERED], [SHOW] or [HIDE]. */
    val choice: Int get() = choiceState.intValue

    /** HIDE is on for the running game. */
    val hiding: Boolean get() = FlaggedFeatures.spoilerFree && choiceState.intValue == HIDE

    fun start(prefs: Prefs, romKey: String): Int = synchronized(lock) {
        this.prefs = prefs
        this.romKey = romKey
        choiceState.intValue = prefs.spoilersFor(romKey)
        shownState.value = prefs.spoilerShownFor(romKey).lines().filter { it.isNotBlank() }.toSet()
        seenState.value = prefs.spoilerSeenFor(romKey).split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        askedThisRun = false
        inGameSamples = 0
        asking = false
        ++generation
    }

    fun close(game: Int) = synchronized(lock) {
        if (game != generation) return@synchronized
        generation++
        prefs = null
        romKey = null
        choiceState.intValue = UNANSWERED
        shownState.value = emptySet()
        seenState.value = emptySet()
        levelsState.value = emptyMap()
        asking = false
    }

    /** Previews and tests: [choice] with nothing stored; [seen] species already read. */
    fun preview(choice: Int, seen: Set<Int> = emptySet(), shown: Set<String> = emptySet(), levels: Map<Int, Int> = emptyMap()): Int = synchronized(lock) {
        prefs = null
        romKey = null
        choiceState.intValue = choice
        seenState.value = seen
        shownState.value = shown
        levelsState.value = levels
        asking = false
        ++generation
    }

    /** SETTINGS > SPOILERS, or the question's answer: [hide] true = HIDE. */
    fun set(hide: Boolean) = synchronized(lock) {
        val v = if (hide) HIDE else SHOW
        choiceState.intValue = v
        romKey?.let { prefs?.setSpoilersFor(it, v) }
        asking = false
    }

    /** The question's answer; null = its bar ran out: nothing kept, asked again on a later start. */
    fun answer(hide: Boolean?) {
        if (hide == null) asking = false else set(hide)
    }

    /** Whether the player tapped [key] open (see [monKey], [moveKey]). */
    fun shown(key: String): Boolean = key in shownState.value

    /** [key] tapped open: it stays shown in this game. */
    fun reveal(key: String) = synchronized(lock) {
        if (key in shownState.value) return@synchronized
        shownState.value = shownState.value + key
        romKey?.let { k -> prefs?.setSpoilerShownFor(k, shownState.value.joinToString("\n")) }
    }

    fun monKey(species: Int) = "mon:$species"
    /** A level-up move of [species] at [level] (the summary's LEARN, the DEX's MOVES). */
    fun moveKey(species: Int, level: Int, move: Int) = "move:$species:$level:$move"

    /**
     * Whether [species] (the game's id) counts as seen. Not hiding: the Pokédex's seen mark, everything where there's no
     * Pokédex (the screens' old rule). Hiding: the mark, or the companion read it, or the player tapped it open.
     */
    fun seen(species: Int, dex: PokedexState?): Boolean {
        val n = dex?.let { PokedexSource.nationalOf(it.tables, species) } ?: 0
        return seenNational(n, species, dex)
    }

    /** [seen] for a Pokédex number [n] (0 = not in it) the caller already has, with the species it stands for. */
    fun seenNational(n: Int, species: Int, dex: PokedexState?): Boolean {
        val inDex = dex != null && n > 0 && n in dex.seen
        if (!hiding) return dex == null || inDex
        if (species <= 0) return inDex
        return inDex || species in seenState.value || shown(monKey(species))
    }

    /** The highest level the player has [species] at (party or boxes); 0 = none. The moves it learns past that are
     * still to come. */
    fun knownLevel(species: Int): Int = levelsState.value[species] ?: 0

    /** Whether a level-up move at [level] is still to come for a [species] the player has at [knownLevel] at best: level 1
     * moves are known from the start, a move on evolving (level 0) once you have one. Not hiding: never. */
    fun moveAhead(species: Int, level: Int, move: Int, knownLevel: Int): Boolean =
        hiding && !shown(moveKey(species, level, move)) && when (level) {
            0 -> knownLevel == 0
            else -> level > maxOf(1, knownLevel)
        }

    /** What [mon] the companion read counts as seen (not an egg's species). */
    private fun readSpecies(snapshot: SnapshotView): Set<Int> = buildSet {
        fun add(m: MonView?) { if (m != null && !m.isEgg && m.species > 0) add(m.species) }
        snapshot.party.forEach(::add)
        snapshot.battleOpponent.forEach(::add)
        snapshot.battlePlayer.forEach(::add)
        snapshot.pcBoxes?.boxes?.forEach { b -> b.mons.forEach(::add) }
    }

    /**
     * Each sample of the game [game] ([start]'s): the species read go on the seen list, and a new or early save that
     * never answered is asked once it's been in the game a moment.
     */
    fun onSample(game: Int, snapshot: SnapshotView) {
        if (!FlaggedFeatures.spoilerFree || snapshot.game == null || snapshot.unsupported) return
        synchronized(lock) {
            if (game != generation) return
            val levels = HashMap<Int, Int>()
            (snapshot.party + snapshot.pcBoxes?.boxes.orEmpty().flatMap { it.mons.filterNotNull() }).forEach { m ->
                if (!m.isEgg && m.species > 0) levels[m.species] = maxOf(levels[m.species] ?: 0, m.level)
            }
            if (levels != levelsState.value) levelsState.value = levels
            val read = readSpecies(snapshot)
            if (!seenState.value.containsAll(read)) {
                seenState.value = seenState.value + read
                romKey?.let { k -> prefs?.setSpoilerSeenFor(k, seenState.value.sorted().joinToString(",")) }
            }
            if (choiceState.intValue != UNANSWERED || askedThisRun) return
            val inGame = snapshot.connected && !snapshot.inBattle &&
                (snapshot.party.isNotEmpty() || snapshot.location.mapSecName.isNotEmpty())
            if (!inGame) { inGameSamples = 0; return }
            if (++inGameSamples < ASK_AFTER_SAMPLES || !earlySave(snapshot)) return
            askedThisRun = true
            askingOnCompanion = false
            asking = true
        }
    }

    /** A page of the companion opened ([SPOILING_PAGES]): a save that never answered is asked over it, once a run. */
    fun pageOpened(page: String) = synchronized(lock) {
        if (!FlaggedFeatures.spoilerFree || romKey == null || page !in SPOILING_PAGES) return@synchronized
        if (choiceState.intValue != UNANSWERED || askedThisRun) return@synchronized
        askedThisRun = true
        askingOnCompanion = true
        asking = true
    }

    /** A save just started: no more than one Pokémon to its name (a starter), the Pokédex the same. */
    fun earlySave(snapshot: SnapshotView): Boolean {
        val mons = snapshot.party.count { !it.isEgg } + (snapshot.pcBoxes?.boxes?.sumOf { it.count } ?: 0)
        val caught = snapshot.pokedex?.caught?.size ?: mons
        return mons <= 1 && caught <= 1
    }
}
