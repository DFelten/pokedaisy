package com.pokedaisy.app.companion.data

import kotlin.math.ln
import kotlin.math.pow

/**
 * SHINY HUNT (the HUNT tab, behind the `shinyHunt` flag until 1.7.0): a game's hunts, the one going and the ones before it.
 * Kept per ROM (Prefs' `hunts_<crc>`, [HuntLog.encode]), so the SETTINGS FILE carries them. Counting is [HuntCounter]'s.
 */
enum class HuntMethod {
    /** Every wild Pokémon met in a battle (grass, caves, water, fishing, a static one walked into): a double battle counts 2. */
    ENCOUNTERS,
    /**
     * A starter, a gift or a legendary, LOAD STATE (or RESTART GAME) between tries: a try counts once the Pokémon after a load
     * has been read (its battle's foe, or the gift joining the party), and not when it's the same one as the last try (Emerald's
     * and the DS games' reloads can repeat it: [HuntCounter.Step.repeat]).
     */
    RESETS,
    /** Every egg that hatches in the party (Masuda method too). */
    EGGS,
    /** Only the + / - buttons count. */
    BY_HAND,
}

enum class HuntStatus { ACTIVE, ON_HOLD, FOUND }

/** A shiny met during a hunt: its species, the count it came at, when (epoch ms). */
data class HuntShiny(val species: Int, val name: String, val count: Int, val at: Long)

data class Hunt(
    /** When it was started (epoch ms): also its id. */
    val id: Long,
    val method: HuntMethod = HuntMethod.ENCOUNTERS,
    /** The species hunted (the game's id), 0 = any shiny ends it. */
    val target: Int = 0,
    val targetName: String = "",
    val count: Int = 0,
    /** 1 in [odds] per try; 0 = the game's own ([HuntOdds.forGame]). */
    val odds: Int = 0,
    /** Play time while it was the game's active hunt. */
    val playMillis: Long = 0,
    /** Shinies of another species on the way (the hunt went on). */
    val phases: List<HuntShiny> = emptyList(),
    val found: HuntShiny? = null,
    val status: HuntStatus = HuntStatus.ACTIVE,
    val ended: Long = 0,
) {
    /** Encounters an hour of play, null before a minute of it. */
    val perHour: Int? get() = if (playMillis < 60_000) null else (count * 3_600_000.0 / playMillis).toInt()

    /** Whether a shiny of [species] ends this hunt (else it's a phase). */
    fun wants(species: Int) = target == 0 || species == target
}

/** A game's hunts, newest first; at most one [HuntStatus.ACTIVE]. */
data class HuntLog(val hunts: List<Hunt> = emptyList()) {
    val active: Hunt? get() = hunts.firstOrNull { it.status == HuntStatus.ACTIVE }

    /** [h] in place of the hunt with its id, or first when new. Making one ACTIVE puts the one before ON HOLD. */
    fun with(h: Hunt): HuntLog {
        val others = hunts.filter { it.id != h.id }.map {
            if (h.status == HuntStatus.ACTIVE && it.status == HuntStatus.ACTIVE) it.copy(status = HuntStatus.ON_HOLD) else it
        }
        val at = hunts.indexOfFirst { it.id == h.id }
        return HuntLog(if (at < 0) listOf(h) + others else others.toMutableList().apply { add(at, h) })
    }

    fun without(id: Long) = HuntLog(hunts.filter { it.id != id })

    fun encode(): String = buildString {
        append("v1\n")
        hunts.forEach { h ->
            append("hunt\t${h.id}\t${h.method.name}\t${h.target}\t${clean(h.targetName)}\t${h.count}\t${h.odds}\t")
            append("${h.playMillis}\t${h.status.name}\t${h.ended}\n")
            h.phases.forEach { append("phase\t${shiny(it)}\n") }
            h.found?.let { append("found\t${shiny(it)}\n") }
        }
    }

    companion object {
        /** Hunts kept per game; the oldest finished ones go first. */
        const val MAX_HUNTS = 200

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        private fun shiny(s: HuntShiny) = "${s.species}\t${clean(s.name)}\t${s.count}\t${s.at}"
        private fun shinyOf(f: List<String>) = HuntShiny(f[1].toInt(), f[2], f[3].toInt(), f[4].toLong())

        /** [encode]'s text back; a line that doesn't parse is skipped. */
        fun decode(text: String?): HuntLog {
            if (text.isNullOrBlank()) return HuntLog()
            val hunts = mutableListOf<Hunt>()
            for (line in text.lines()) {
                val f = line.split('\t')
                runCatching {
                    when (f[0]) {
                        "hunt" -> hunts += Hunt(
                            id = f[1].toLong(), method = HuntMethod.valueOf(f[2]), target = f[3].toInt(), targetName = f[4],
                            count = f[5].toInt().coerceAtLeast(0), odds = f[6].toInt().coerceAtLeast(0),
                            playMillis = f[7].toLong().coerceAtLeast(0), status = HuntStatus.valueOf(f[8]), ended = f[9].toLong(),
                        )
                        "phase" -> hunts[hunts.lastIndex] = hunts.last().let { it.copy(phases = it.phases + shinyOf(f)) }
                        "found" -> hunts[hunts.lastIndex] = hunts.last().copy(found = shinyOf(f))
                    }
                }
            }
            // One active at most: the first wins.
            var seen = false
            return HuntLog(hunts.map {
                if (it.status != HuntStatus.ACTIVE) it else if (seen) it.copy(status = HuntStatus.ON_HOLD) else it.also { seen = true }
            })
        }
    }
}

/** A hunt's odds and how its luck stands. */
object HuntOdds {
    /** What the ODDS row offers: the games' own, then the usual bonuses (Gen 5's Shiny Charm, Masuda Gen 4 / 5, hacks' 1 in 4096). */
    val CHOICES = listOf(0, 8192, 4096, 2731, 1638, 1365, 1024, 512)

    /** The game's own odds: SHINY ODDS when it's set, else 1 in 8192 (Gen 2 to 5). */
    fun forGame(shinyOdds: Int?): Int = shinyOdds?.takeIf { it > 0 } ?: ShinyOdds.NORMAL

    /** The chance of at least one shiny in [count] tries at 1 in [odds], 0..1. */
    fun chanceBy(count: Int, odds: Int): Double = if (odds <= 1) 1.0 else 1.0 - (1.0 - 1.0 / odds).pow(count)

    /** The tries it takes for a [chance] (0..1) of at least one shiny at 1 in [odds]: 50% at 1 in 8192 is 5,678. */
    fun countFor(chance: Double, odds: Int): Int = if (odds <= 1) 1 else kotlin.math.ceil(ln(1.0 - chance) / ln(1.0 - 1.0 / odds)).toInt()
}

/**
 * Counts the active hunt from samples and events, the way its [HuntMethod] says ([sample], [reset]), and spots the shinies
 * it can meet: a wild battle's foe, a hatched egg and, hunting by RESETS, a new party member (a gift, a starter: not one
 * just caught, whose battle said it already). Pure: the caller keeps the [Hunt].
 */
class HuntCounter {
    /** What one sample did: tries to add, the shiny met (null = none), [repeat] = a reset gave the last try's Pokémon again. */
    data class Step(val add: Int, val shiny: MonView?, val repeat: Boolean = false)

    private var battleCounted = false
    private var battleShinyReported = false
    /** Samples since the last battle: a Pokémon that joins right after one was caught in it. */
    private var sinceBattle = Int.MAX_VALUE
    /** The party as last seen, by personality: an egg or not. Null until a party has been read. */
    private var party: Map<Long, Boolean>? = null
    /** RESETS: a load happened and its Pokémon hasn't been read yet. */
    private var armed = false
    /** RESETS: the last try's Pokémon (personality), 0 = none. */
    private var lastRoll = 0L

    /** A state was loaded or the game restarted: what's on screen now is another moment (the battle and party start over). */
    fun reset() {
        battleCounted = false
        battleShinyReported = false
        sinceBattle = Int.MAX_VALUE
        party = null
        armed = true
    }

    /** A new hunt (or one picked up again): no try pending, no last Pokémon. */
    fun restart() {
        reset()
        armed = false
        lastRoll = 0L
    }

    fun sample(method: HuntMethod, inBattle: Boolean, wild: Boolean, foes: List<MonView>, partyNow: List<MonView>): Step {
        var add = 0
        var shiny: MonView? = null
        var repeat = false
        // RESETS: the first Pokémon read after a load is the try; the same one as last time isn't a new one.
        fun rolled(mon: MonView) {
            if (method != HuntMethod.RESETS || !armed) return
            armed = false
            if (mon.personality != 0L && mon.personality == lastRoll) repeat = true else add++
            lastRoll = mon.personality
        }
        if (inBattle) {
            sinceBattle = 0
            if (wild && foes.isNotEmpty()) {
                if (!battleCounted) {
                    battleCounted = true
                    if (method == HuntMethod.ENCOUNTERS) add += foes.size
                    rolled(foes.first())
                }
                if (!battleShinyReported) foes.firstOrNull { it.shiny }?.let { shiny = it; battleShinyReported = true }
            }
        } else {
            battleCounted = false
            battleShinyReported = false
            if (sinceBattle < Int.MAX_VALUE) sinceBattle++
        }
        // The party: hatches and new members. Read only out of battle (a battle's party is the battle's order).
        if (!inBattle && partyNow.isNotEmpty() && partyNow.all { it.personality != 0L }) {
            val before = party
            if (before != null) {
                for (mon in partyNow) {
                    val wasEgg = before[mon.personality]
                    val hatched = wasEgg == true && !mon.isEgg
                    // A gift or a starter: only a RESETS hunt looks (else a shiny taken out of the PC would count).
                    val joined = method == HuntMethod.RESETS && wasEgg == null && !mon.isEgg && sinceBattle > JUST_CAUGHT_SAMPLES
                    if (hatched && method == HuntMethod.EGGS) add++
                    if (joined) rolled(mon)
                    if ((hatched || joined) && mon.shiny && shiny == null) shiny = mon
                }
            }
            party = partyNow.associate { it.personality to it.isEgg }
        }
        return if (add == 0 && shiny == null && !repeat) NONE else Step(add, shiny, repeat)
    }

    companion object {
        private val NONE = Step(0, null)
        /** Samples (about a second each) after a battle in which a new party member is taken as caught there. */
        const val JUST_CAUGHT_SAMPLES = 20
    }
}
