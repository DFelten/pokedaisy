package com.pokedaisy.app.companion.data

import kotlin.concurrent.Volatile

/**
 * A PC fan game's own types ([GameKind.ESSENTIALS]): their names by the game's type ids and their chart, from the type
 * table its data sends (PcGameActivity). Read through [activeTypeNames] / [activeTypeEffectiveness] while one runs.
 */
object PcGameTypes {
    /** Type id -> its name as the game spells it ("Fire": [com.pokedaisy.app.companion.ui.theme.AppColors.typeColors] keys). */
    @Volatile var names: Map<Int, String> = emptyMap()

    /** attacking id * 100 + defending id -> percent (100 = 1x), only the pairs that aren't 1x. */
    @Volatile var chart: Map<Int, Int> = emptyMap()

    /** [names] and [chart] from each defending type's weaknesses, resistances and immunities (by attacking type id). */
    fun set(types: List<Type>) {
        names = types.associate { it.id to it.name }
        chart = buildMap {
            for (t in types) {
                t.weak.forEach { put(it * 100 + t.id, 200) }
                t.resist.forEach { put(it * 100 + t.id, 50) }
                t.immune.forEach { put(it * 100 + t.id, 0) }
            }
        }
    }

    data class Type(val id: Int, val name: String, val weak: List<Int>, val resist: List<Int>, val immune: List<Int>)
}
