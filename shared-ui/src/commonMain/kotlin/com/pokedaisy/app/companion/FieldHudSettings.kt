package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.i18n.tk

// SETTINGS > HUD's choices (the HUD itself is companion.ui.FieldHud): here, beside the other settings types,
// so Prefs and CompanionSettings can hold them.

/** Where the HUD's pieces sit on the game screen. */
enum class FieldHudLayout(val label: String) {
    /** A modern open-world game's corners: minimap top-right (the games' area names slide in top-left), party bottom-left, balls and buttons bottom-right. */
    CORNERS(tk("CORNERS")),

    /** CORNERS with the right side swapped: the minimap bottom right, the balls and buttons top right. */
    CORNERS_2(tk("CORNERS 2")),

    /** Two columns at the screen's sides, beside the GBA's own 3:2 (the black bars at ORIGINAL, the margins at EXPAND). */
    RAILS(tk("RAILS")),

    /** One bar along the bottom (party, balls, buttons), the minimap top-right. */
    DOCK(tk("DOCK")),
}

/** How the HUD's windows are drawn. */
enum class FieldHudLook(val label: String) {
    /** The running game's own look (its companion skin's HUD: HeartGold's green menu window, Black's navy panels...);
     * GLASS for a game without one. The default. */
    GAME(tk("GAME")),

    /** See-through dark panels with light text: as little of the game hidden as can be. */
    GLASS(tk("GLASS")),

    /** [GLASS] in white: see-through light panels with dark text. */
    WHITE_GLASS(tk("WHITE GLASS")),

    /** The OPTION screen's white title windows, like the rest of the app. */
    WINDOW(tk("WINDOW")),
}

/** How big the HUD is: its GBA pixel one whole screen pixel smaller or bigger than the companion's (then all drawn 12.5%
 * bigger, FieldHud's HUD_SCALE). */
enum class FieldHudSize(val label: String, val step: Int) {
    SMALL(tk("SMALL"), -1),
    NORMAL(tk("NORMAL"), 0),
    LARGE(tk("LARGE"), 1),
}

/** The parts a player can switch off (SETTINGS > HUD > SHOW), stored by name ([com.pokedaisy.app.Prefs.fieldHudStyle]). */
enum class FieldHudPart(val label: String) {
    PARTY(tk("PARTY")),
    MAP(tk("MINIMAP")),
    BALLS(tk("POKé BALLS")),
    /** The steps left on a REPEL in effect, counting down, beside the balls. */
    REPEL(tk("REPEL STEPS")),
    /** The game's own PC on the spot (ItemUse.openPc), where the BAG tab has PC. */
    PC(tk("PC")),
    BAG(tk("BAG")),
    WILD(tk("WILD POKéMON")),
    /** The card over the game while its summary asks which move to forget (MoveLearningHudCard). */
    MOVE_ADVICE(tk("MOVE ADVICE")),
}

/** The HUD's pieces EDIT LAYOUT moves, each as one block. */
enum class HudBlock(val label: String) {
    PARTY(tk("PARTY")),
    MAP(tk("MINIMAP")),
    /** The Poké Balls and the REPEL's steps. */
    BALLS(tk("POKé BALLS")),
    /** PC, BAG, WILD and COMPANION. */
    BUTTONS(tk("BUTTONS")),
}

/**
 * Where a block sits in one of the player's HUD layouts: [fx] / [fy] its share of the room it can move in (0 = against
 * the left / top edge, 1 = against the right / bottom one), so it stays whole on any screen and at any SIZE.
 */
data class HudPlace(val fx: Float, val fy: Float)

/** One of the player's HUD layouts (HUD > LAYOUT, made with EDIT LAYOUT): its name and its blocks' places. */
data class HudCustom(val name: String, val places: Map<HudBlock, HudPlace>) {
    fun encode(): String = name.filter { it != '\t' && it != '\n' && it != ';' } + "\t" +
        places.entries.joinToString(";") { (b, p) -> "${b.name}:${p.fx},${p.fy}" }

    companion object {
        fun decode(line: String): HudCustom? {
            val parts = line.split('\t')
            if (parts.size != 2 || parts[0].isBlank()) return null
            val places = parts[1].split(';').mapNotNull { e ->
                val (n, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val b = HudBlock.entries.firstOrNull { it.name == n } ?: return@mapNotNull null
                val xy = v.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
                b to HudPlace(xy[0].coerceIn(0f, 1f), xy[1].coerceIn(0f, 1f))
            }.toMap()
            return HudCustom(parts[0], places)
        }

        /** Where CORNERS puts the blocks: what a new layout starts from. */
        val CORNERS_PLACES = mapOf(
            HudBlock.PARTY to HudPlace(0f, 1f), HudBlock.MAP to HudPlace(1f, 0f),
            HudBlock.BALLS to HudPlace(1f, 0.82f), HudBlock.BUTTONS to HudPlace(1f, 1f),
        )
    }
}

/** The player's choices for the HUD (SETTINGS > HUD). */
data class FieldHudStyle(
    val layout: FieldHudLayout = FieldHudLayout.CORNERS,
    val look: FieldHudLook = FieldHudLook.GAME,
    val size: FieldHudSize = FieldHudSize.NORMAL,
    val hidden: Set<FieldHudPart> = emptySet(),
    /** The player's layouts (the user's call, 2026-11-23: several, kept whatever LAYOUT says), and which is in use: -1 =
     * [layout]. */
    val customs: List<HudCustom> = emptyList(),
    val custom: Int = -1,
) {
    fun shows(part: FieldHudPart) = part !in hidden

    /** The player's layout in use, or null for [layout]. */
    val currentCustom: HudCustom? get() = customs.getOrNull(custom)

    /** LAYOUT's value: the built-in one's name or the player's layout's. */
    val layoutLabel: String get() = currentCustom?.name ?: layout.label

    /** LAYOUT's next value: the built-in ones, then the player's. */
    fun nextLayout(): FieldHudStyle {
        val cur = currentCustom
        return when {
            cur != null -> if (custom + 1 < customs.size) copy(custom = custom + 1) else copy(custom = -1, layout = FieldHudLayout.entries.first())
            layout.ordinal + 1 < FieldHudLayout.entries.size -> copy(layout = FieldHudLayout.entries[layout.ordinal + 1])
            customs.isNotEmpty() -> copy(custom = 0)
            else -> copy(layout = FieldHudLayout.entries.first())
        }
    }

    /** A new layout of the player's, from CORNERS' places, in use (EDIT LAYOUT then moves it). */
    fun newCustom(): FieldHudStyle {
        val c = HudCustom(customLayoutName(customs.map { it.name }), HudCustom.CORNERS_PLACES)
        return copy(customs = customs + c, custom = customs.size)
    }

    /** EDIT LAYOUT's DONE: [places] for the layout in use (a new one when LAYOUT was a built-in one: never written over). */
    fun withPlaces(places: Map<HudBlock, HudPlace>): FieldHudStyle {
        val base = if (currentCustom != null) this else newCustom()
        return base.copy(customs = base.customs.mapIndexed { i, c -> if (i == base.custom) c.copy(places = places) else c })
    }

    /** DELETE LAYOUT: the layout in use goes, CORNERS (or the built-in one before) comes back. */
    fun deleteCustom(): FieldHudStyle =
        if (currentCustom == null) this else copy(customs = customs.filterIndexed { i, _ -> i != custom }, custom = -1)
}
