package com.pokedaisy.app.companion.data

import kotlin.concurrent.Volatile

/**
 * A PC fan game's Town Map ([GameKind.ESSENTIALS]) for the MAP tab: the region the player is in, as the game's own
 * Town Map data has it (Essentials' town_map.dat / GameData::TownMap, sent by our preload): its picture, rebuilt into the
 * art cache by the app, and its places, each a tile ([Point]). [activeRegionMap] hands [model] to the MAP tab while one
 * runs; [version] moves whenever it changes (another region, a place shown by its switch, a new picture).
 */
object PcTownMap {
    @Volatile var model: RegionMapModel? = null
        private set

    /** The player's mark on the map (regionmap/<head>.png: the game's own icon); null = none. */
    @Volatile var head: String? = null

    @Volatile var version: Int = 0
        private set

    /** The section the player stands in when no place has their tile (a route the map leaves unnamed): no name, so
     * the PLACES list leaves it out, and the label shows the game's own map name. */
    const val PLAYER_ONLY = 0xFFFE

    /** One place's tile: its name, and the point of interest the game names under it (a cave, a tower), if any. */
    data class Point(val x: Int, val y: Int, val name: String, val poi: String?)

    fun set(model: RegionMapModel?) {
        this.model = model
        version++
    }

    fun clear() {
        head = null
        set(null)
    }

    /**
     * The MAP tab's model for one region: [picture] (a regionmap asset), [tile] map pixels a tile, a tile's middle
     * [centre] pixels from where it starts (Essentials' own map centres a tile's sprites on tile * size: 0, so a tile
     * starts half a tile back; Marin's Better Region Map, Infinite Fusion's, on tile * size + size / 2). Each place name
     * is a section, its tiles on the grid's first layer; points of interest are the second (FireRed's dungeons: a
     * second line in the label). [hidden]: the points' indexes the game doesn't show yet (their switch is off).
     */
    fun build(picture: String, tile: Int, centre: Int, points: List<Point>, hidden: Set<Int> = emptySet()): RegionMapModel? {
        val shown = points.filterIndexed { i, p -> i !in hidden && p.x >= 0 && p.y >= 0 && p.name.isNotBlank() }
        if (shown.isEmpty() || tile <= 0) return null
        val ids = LinkedHashMap<String, Int>()
        fun id(name: String) = ids.getOrPut(name) { ids.size + 1 }
        val w = shown.maxOf { it.x } + 1
        val h = shown.maxOf { it.y } + 1
        val places = ByteArray(w * h * 2) { 0xFF.toByte() }
        val pois = ByteArray(w * h * 2) { 0xFF.toByte() }
        var anyPoi = false
        fun put(cells: ByteArray, p: Point, id: Int) {
            val i = (p.y * w + p.x) * 2
            cells[i] = (id and 0xFF).toByte()
            cells[i + 1] = (id shr 8).toByte()
        }
        for (p in shown) {
            put(places, p, id(p.name))
            p.poi?.takeIf { it.isNotBlank() }?.let { put(pois, p, id(it)); anyPoi = true }
        }
        // A place whose tiles fill a rectangle is that rectangle (one cursor frame round a town); else its tiles, one by one.
        val tiles = HashMap<String, MutableSet<Pair<Int, Int>>>()
        for (p in shown) {
            tiles.getOrPut(p.name) { HashSet() } += p.x to p.y
            p.poi?.takeIf { it.isNotBlank() }?.let { tiles.getOrPut(it) { HashSet() } += p.x to p.y }
        }
        fun info(name: String): MapSecInfo {
            val t = tiles[name].orEmpty()
            val x0 = t.minOf { it.first }
            val y0 = t.minOf { it.second }
            val w0 = t.maxOf { it.first } - x0 + 1
            val h0 = t.maxOf { it.second } - y0 + 1
            return if (t.size == w0 * h0) MapSecInfo(name, 0, x0, y0, w0, h0) else MapSecInfo(name, 0, 0, 0, 0, 0)
        }
        val sections = ids.entries.associate { (name, id) -> id to info(name) } +
            (PLAYER_ONLY to MapSecInfo("", 0, 0, 0, 0, 0))
        return RegionMapModel(
            images = arrayOf(picture),
            sections = sections,
            layouts = listOf(RegionLayout(0, 0, w, h, NONE, if (anyPoi) listOf(places, pois) else listOf(places), cellBytes = 2)),
            tile = tile,
            originX = centre - tile / 2,
            originY = centre - tile / 2,
        )
    }

    /** The place at the player's tile ([build]'s section id), or [PLAYER_ONLY]. */
    fun sectionAt(model: RegionMapModel, x: Int, y: Int): Int =
        model.pick(0, x, y)?.mapsec?.takeIf { it > 0 } ?: PLAYER_ONLY

    private const val NONE = 0xFFFF
}
