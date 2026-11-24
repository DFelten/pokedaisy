package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.i18n.tk

/**
 * The companion's HOME tab (docs/plans/companion-home.md): the useful things in one place, as cards, with a row of
 * shortcuts under them. Which cards and in which order, and which shortcuts, are the player's (SETTINGS > HOME TAB),
 * kept for every game like the tab bar ([com.pokedaisy.app.Prefs.homeCards], [com.pokedaisy.app.Prefs.homeShortcuts]).
 */
enum class HomeCard(val id: String, val label: String) {
    /** The six: icon, level, HP bar, status. A slot opens that Pokémon's summary. */
    PARTY("PARTY", tk("PARTY")),
    /** The region map round the player, the place's name. Opens MAP. */
    MAP("MAP", tk("MAP")),
    /** Money, play time, badges, the POKéDEX's count. Opens CARD (else DEX). */
    TRAINER("TRAINER", tk("TRAINER")),
    /** The device's clock and battery. Left out while the status bar is over the companion (it shows them). */
    DEVICE("DEVICE", tk("CLOCK AND BATTERY")),
    ;

    /** PARTY and MAP take the big places; the others share what's left. */
    val big: Boolean get() = this == PARTY || this == MAP

    companion object {
        fun of(id: String): HomeCard? = entries.firstOrNull { it.id == id }
    }
}

object HomeTab {
    /** The tab's id, in [COMPANION_TABS] and the tab bar. */
    const val ID = "HOME"

    /** At most this many shortcuts; a screen with less room shows the first ones. */
    const val MAX_SHORTCUTS = 6

    val DEFAULT_CARDS: List<String> = HomeCard.entries.map { it.id }

    /** Tabs that start off the bar (and NUZLOCKE, while it's on), each only where the game has it. */
    val DEFAULT_SHORTCUTS: List<String> = listOf("BOXES", "NUZLOCKE", "ACHIEVEMENTS", "STATES", "CARD", "GUIDE")

    /** The tabs a shortcut can open: every tab but HOME itself (BATTLE, MORE and SETTINGS aren't in [COMPANION_TABS]). */
    val shortcutTargets: List<String> get() = COMPANION_TABS.filter { it != ID }

    /** [stored] read back as cards, in order, each once; unknown ids (a card a newer build added, then went back) dropped. */
    fun cards(stored: List<String>): List<HomeCard> = stored.mapNotNull(HomeCard::of).distinct()

    /** [stored] read back as six places, "" = empty, each tab at most once. */
    fun places(stored: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return (0 until MAX_SHORTCUTS).map { i ->
            stored.getOrNull(i)?.takeIf { it in shortcutTargets && seen.add(it) }.orEmpty()
        }
    }

    /** The shortcuts drawn for a game that has [available] tabs: the filled places it has, in order. */
    fun shortcuts(stored: List<String>, available: List<String>): List<String> =
        places(stored).filter { it.isNotEmpty() && it in available }

    /** [cards] with [card] moved one place up ([by] -1) or down (+1), the order of the others kept. */
    fun move(cards: List<String>, card: String, by: Int): List<String> {
        val i = cards.indexOf(card)
        val j = i + by
        if (i < 0 || j !in cards.indices) return cards
        return cards.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** [cards] with [card] turned off, or turned on at the end (MOVE UP takes it further). */
    fun toggle(cards: List<String>, card: String): List<String> = if (card in cards) cards - card else cards + card
}
