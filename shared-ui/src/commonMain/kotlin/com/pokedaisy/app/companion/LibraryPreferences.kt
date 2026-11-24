package com.pokedaisy.app.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pokedaisy.app.companion.data.LibraryView
import kotlin.time.Clock

/** Stable sort IDs used by the library hosts. */
enum class LibrarySort(val id: Int, val label: String) {
    NAME(0, "NAME"), LAST_PLAYED(1, "LAST PLAYED"), DATE_ADDED(2, "DATE ADDED");
    companion object { fun of(id: Int) = entries.firstOrNull { it.id == id } ?: NAME }
}

/** Settings > GAME PATHS: what the library's list shows under a game's name (Prefs.libraryPathMode, by [id]). [MATCHING]
 * (the default, the user's call): the file's name, only for games whose shown names are the same. */
enum class LibraryPathMode(val id: Int, val label: String) {
    MATCHING(0, "WHEN NAMES MATCH"), FULL(1, "FULL PATH"), OFF(2, "OFF");
    companion object { fun of(id: Int) = entries.firstOrNull { it.id == id } ?: MATCHING }
}

/** Library choices, favorites, history, sorting and filtering for both hosts. Storage codecs preserve installed formats. */
class LibraryPreferences private constructor(private val storage: Storage, private val now: () -> Long) {
    private data class Storage(val prefs: com.pokedaisy.app.Prefs, val added: LibraryAddedDates)
    constructor(prefs: com.pokedaisy.app.Prefs, added: LibraryAddedDates,
                now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) : this(Storage(prefs, added), now)
    constructor(read: () -> String?, write: (String) -> Unit,
                now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) : this(legacy(read, write), now)
    private val prefs get() = storage.prefs
    private data class Record(val layout: LibraryLayout, val sort: LibrarySort, val view: LibraryView,
        val favorites: Set<String>, val played: Map<String, Long>, val added: Map<String, Long>, val last: String?,
        val names: Map<String, String>, val viewOrder: List<Int>)
    private fun read() = Record(LibraryLayout.of(prefs.libraryViewMode), LibrarySort.of(prefs.librarySort), LibraryView.of(prefs.libraryView),
        prefs.favoriteRoms, prefs.romPlayTimes(), storage.added.all().mapValues { it.value.millis }, prefs.lastRomPath, prefs.romDisplayNames(),
        prefs.libraryViewOrder.split(',').mapNotNull { it.trim().toIntOrNull() })
    private var record by mutableStateOf(read())
    /** The grid of covers (the classic library's other view; a host without the immersive ones shows them as it). */
    val grid get() = record.layout == LibraryLayout.GRID
    /** LIBRARY VIEW: list, grid or one of the immersive views (Prefs.libraryViewMode, by ordinal). */
    val layout get() = record.layout
    val sort get() = record.sort
    val view get() = record.view
    val favorites get() = record.favorites
    val played get() = record.played
    val added get() = record.added
    val last get() = record.last
    /** The quick views' saved order (Compose state: a drag redraws the pills). */
    val viewOrder get() = record.viewOrder
    fun reload() { storage.added.reload(); record = read() }
    fun setGrid(on: Boolean) = setLayout(if (on) LibraryLayout.GRID else LibraryLayout.LIST)
    fun setLayout(layout: LibraryLayout) { prefs.libraryViewMode = layout.ordinal; record = read() }
    fun setSort(sort: LibrarySort) { prefs.librarySort = sort.id; record = read() }
    fun setView(view: LibraryView) { prefs.libraryView = view.id; record = read() }
    /** The quick views dragged into [order] (the pills shown now; others keep their place after them). */
    fun setViewOrder(order: List<LibraryView>) {
        val rest = record.viewOrder.filter { id -> order.none { it.id == id } }
        prefs.libraryViewOrder = (order.map { it.id } + rest).joinToString(",")
        record = read()
    }
    /** [views] in the player's order: the ones they placed first, as placed; any other in its default place among them. */
    fun orderViews(views: List<LibraryView>): List<LibraryView> {
        val saved = record.viewOrder
        if (saved.isEmpty()) return views
        val placed = views.filter { it.id in saved }.sortedBy { saved.indexOf(it.id) }.toMutableList()
        for (v in views.filter { it.id !in saved }) placed.add(minOf(views.indexOf(v), placed.size), v)
        return placed
    }
    fun setFavorite(id: String, on: Boolean) {
        prefs.favoriteRoms = if (on) favorites + id else favorites - id
        record = read()
    }
    fun toggleFavorite(id: String) = setFavorite(id, id !in favorites)
    fun recordPlay(id: String) { prefs.recordRomPlayed(id, now()); record = read() }
    fun recordAdded(ids: List<String>, initial: Boolean = false) { storage.added.record(ids, initial, now()); record = read() }
    fun forget(id: String) {
        storage.added.forget(id); prefs.favoriteRoms = favorites - id
        if (last == id) prefs.lastRomPath = null
        record = read()
    }
    /** Display-only aliases use the persisted filename key on both platforms. */
    fun displayName(id: String, fallback: String): String = record.names[id] ?: fallback

    fun rename(id: String, proposed: String, fallback: String) {
        val name = proposed.trim().takeUnless { it.isBlank() || it == fallback }
        if (name == record.names[id]) return
        prefs.setRomDisplayName(id, name)
        record = read()
    }

    fun isNew(id: String) = storage.added.isNew(id, now())

    fun <T> ordered(roms: List<T>, order: LibrarySort = sort, id: (T) -> String): List<T> {
        val recent = prefs.recentRomPaths()
        val sorted = when (order) {
            LibrarySort.NAME -> roms
            LibrarySort.LAST_PLAYED -> roms.sortedWith(compareByDescending<T> { played[id(it)] ?: 0L }
                .thenBy { recent.indexOf(id(it)).let { i -> if (i < 0) Int.MAX_VALUE else i } })
            LibrarySort.DATE_ADDED -> roms.sortedByDescending { added[id(it)] ?: 0L }
        }
        val (top, rest) = sorted.partition { id(it) in favorites }
        return top + rest
    }
    fun <T> filtered(roms: List<T>, view: LibraryView, id: (T) -> String,
                     platform: (T) -> com.pokedaisy.app.companion.data.RomCategory?,
                     category: (T) -> com.pokedaisy.app.companion.data.RomCategory? = platform): List<T> = when (view) {
        LibraryView.ALL -> roms
        LibraryView.FAVORITES -> roms.filter { id(it) in favorites }
        LibraryView.RECENT -> {
            val recent = prefs.recentRomPaths()
            roms.filter { id(it) in played || id(it) in recent }.sortedWith(compareByDescending<T> { played[id(it)] ?: 0L }
                .thenBy { recent.indexOf(id(it)).let { i -> if (i < 0) Int.MAX_VALUE else i } })
        }
        LibraryView.HACKS -> roms.filter { category(it) == com.pokedaisy.app.companion.data.RomCategory.HACK }
        else -> roms.filter { platform(it) == view.platform }
    }
    private companion object {
        fun legacy(read: () -> String?, write: (String) -> Unit): Storage {
            val backend = LegacyLibraryStore(read, write)
            return Storage(com.pokedaisy.app.Prefs(backend, com.pokedaisy.app.Prefs.Defaults(libraryListsJson = true)), backend.added)
        }
    }
}

fun matchesLibrarySearch(query: String, names: List<String>): Boolean {
    fun searchable(value: String) = com.pokedaisy.app.companion.i18n.stripAccents(value).lowercase()
    val all = names.map(::searchable)
    return searchable(query).split(' ').filter { it.isNotEmpty() }.all { word -> all.any { word in it } }
}

/**
 * How the library shows its games (the header's view button, Prefs.libraryViewMode by [ordinal]: 0 and 1 were the list
 * and the grid, so they keep their meaning). The last three are the immersive views (the user's call, 2026-11-23, after
 * other frontends): one game in front at a time, the rest in a row.
 */
enum class LibraryLayout(val label: String) {
    LIST("LIST"), GRID("GRID"),
    /** Each game a cartridge standing in a socket of a rack; the one picked rises out of its slot, and goes in to play. */
    CARTRIDGES("CARTRIDGES"),
    /** The covers in a row turned like a flip-through, the one in front big, its art behind. */
    CAROUSEL("CAROUSEL"),
    /** The picked game across the screen (its art, its name, PLAY), the rest in a row along the bottom. */
    SPOTLIGHT("SPOTLIGHT");

    val immersive get() = ordinal >= CARTRIDGES.ordinal

    companion object {
        fun of(i: Int): LibraryLayout = entries.getOrNull(i) ?: LIST
    }
}
