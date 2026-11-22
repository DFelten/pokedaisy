package com.pokedaisy.app.companion.data

import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import kotlin.concurrent.Volatile

/**
 * A PC fan game's GUIDE ([GameKind.ESSENTIALS]): HERE, the wild Pokémon of the map the player is on, as the game's own
 * encounter tables have them right now (our preload's `wild`: the time of day's lists, after any mode or randomizer of
 * the game's). Built by the app from each snapshot ([set]); the GUIDE tab reads [guide] while one runs, [version]
 * moving with it. Names, levels and odds are the game's; a Pokémon the Pokédex hasn't seen stays ??? until tapped,
 * as on every game's HERE.
 */
object PcGuide {
    @Volatile var guide: GameGuide? = null
        private set

    @Volatile var version: Int = 0
        private set

    /** One Pokémon of a list: [seen] / [owned] null = the game has no Pokédex to say. [icon]: its party icon's art path. */
    data class Slot(val name: String, val min: Int, val max: Int, val percent: Int, val seen: Boolean?, val owned: Boolean?, val icon: String?)

    /** One way to meet them: Essentials' encounter kind ("Land", "Water", "OldRod"...) and its list. */
    data class Kind(val kind: String, val slots: List<Slot>)

    fun set(guide: GameGuide?) {
        this.guide = guide
        version++
    }

    fun clear() = set(null)

    /** The GUIDE for the map named [place] with these [kinds]. */
    fun of(place: String, kinds: List<Kind>): GameGuide = GameGuide(listOf(herePage(place, kinds)))

    /** HERE: a line naming the place (and how many of its Pokémon are caught), then a list per way to meet them; kinds
     * that read the same (a game's several grass lists) share one, each Pokémon once. */
    fun herePage(place: String, kinds: List<Kind>): GuidePage {
        val byHeading = LinkedHashMap<String, MutableList<Slot>>()
        for (k in kinds) {
            val list = byHeading.getOrPut(heading(k.kind)) { ArrayList() }
            for (s in k.slots) if (list.none { it.name == s.name }) list += s
        }
        val all = byHeading.values.flatten().distinctBy { it.name }
        val caught = all.count { it.owned == true }
        val note = when {
            all.isEmpty() -> tr("No wild POKéMON on this map.")
            all.all { it.owned != null } -> tr("{0} of {1} caught here.", caught, all.size)
            else -> null
        }
        val intro = GuideSection(place.ifEmpty { tk("THIS MAP") }.uppercase(), emptyList(), note = note)
        return GuidePage(
            tk("HERE"),
            listOf(intro) + byHeading.filterValues { it.isNotEmpty() }.map { (h, slots) -> GuideSection(h, slots.map(::entry)) },
        )
    }

    private fun entry(s: Slot): GuideEntry {
        val seen = s.seen != false
        return GuideEntry(
            title = if (seen) s.name else "???",
            answer = if (seen) "" else s.name,
            detail = "${tr("LV {0}", if (s.min == s.max) "${s.min}" else "${s.min}-${s.max}")}  ${s.percent}%",
            owned = s.owned == true,
            iconAsset = s.icon,
        )
    }

    /** The heading every game's HERE uses for a kind; one it doesn't know, as the game names it. */
    private fun heading(kind: String): String = when (kind) {
        "Land", "Land1", "Land2", "Land3", "Cave" -> tk("GRASS / CAVE")
        "Water" -> tk("SURFING")
        "RockSmash" -> tk("ROCK SMASH")
        "OldRod" -> tk("OLD ROD")
        "GoodRod" -> tk("GOOD ROD")
        "SuperRod" -> tk("SUPER ROD")
        "HeadbuttLow" -> tk("HEADBUTT")
        "HeadbuttHigh" -> tk("HEADBUTT (RARE TREES)")
        else -> kind.uppercase()
    }
}
