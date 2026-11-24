package com.pokedaisy.app.companion

import kotlin.concurrent.Volatile

/**
 * Batches of features merged but not shown to players yet (the host's FeatureFlags sets these at start: a build
 * property or a marker file on the device). Off, their settings rows are left out ([com.pokedaisy.app.companion.ui.flaggedRows]) and their settings read
 * as the values they had before ([com.pokedaisy.app.Prefs]), so nothing of them shows. ui-preview turns both on.
 */
object FlaggedFeatures {
    /** SETTINGS FILE (export / import, setup's first step), SAVE STATES' folder and by-game page, per-game settings. */
    @Volatile var settingsPlus = false

    /** VIEW MODERN, MY LOOK (themes, backgrounds, fonts), FONT SIZE, SIDE BARS, the tab bar's and menu bar's places, kept
     * HUD and touch layouts, the library's immersive views and pill order, the regrouped settings, ScreenScraper. */
    @Volatile var modernLook = false

    /** The shiny hunt counter: the HUNT tab, its HUD counter, LOAD STATE held over a shiny. */
    @Volatile var shinyHunt = false

    /** Playing without spoilers: SETTINGS > SPOILERS, its question, and what each screen hides ([Spoilers]). */
    @Volatile var spoilerFree = false
}
