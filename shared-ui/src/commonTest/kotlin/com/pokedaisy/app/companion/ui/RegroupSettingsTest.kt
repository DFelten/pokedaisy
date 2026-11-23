package com.pokedaisy.app.companion.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/** [regroupSettings]: both screens' rows placed in [SETTINGS_GROUPS], none lost, none twice. */
class RegroupSettingsTest {
    private fun row(label: String) = SettingRow(label, null) {}

    private fun groupsOf(rows: List<SettingRow>): List<Pair<String, List<String>>> {
        val out = mutableListOf<Pair<String, MutableList<String>>>()
        for (r in rows) if (r.header) out += r.label to mutableListOf() else out.lastOrNull()?.second?.add(r.label)
        return out
    }

    @Test
    fun rowsMoveToTheirGroupsInOrder() {
        val rows = listOf(
            row("DESCRIPTIONS"),
            groupTitle("FF / SPEED"), row("FF SPEED"),
            groupTitle("GAMEPLAY"), row("SHINY ODDS"), row("WILD ENCOUNTERS"), row("NUZLOCKE"),
            groupTitle("SCREEN"), row("THEME"), row("HUD"), row("ASPECT"), row("SOMETHING NEW"), row("VIEW"), row("DS LAYOUT"),
            groupTitle("COMPANION"), row("BATTLE HINTS"), row("TAB BAR"), row("ACHIEVEMENT POPUPS"),
            groupTitle("ONLINE"), row("RetroAchievements"),
            groupTitle("APP"), row("HELP"),
        )
        val out = regroupSettings(rows)
        assertEquals("DESCRIPTIONS", out.first().label)
        assertEquals(
            listOf(
                "FF / SPEED" to listOf("FF SPEED"),
                "GAMEPLAY" to listOf("NUZLOCKE", "SHINY ODDS", "WILD ENCOUNTERS"),
                "GAME SCREEN" to listOf("ASPECT", "SOMETHING NEW"),
                "ON THE GAME" to listOf("HUD"),
                "DS" to listOf("DS LAYOUT"),
                "LOOK" to listOf("VIEW", "THEME"),
                "BATTLE" to listOf("BATTLE HINTS"),
                "COMPANION" to listOf("TAB BAR"),
                "ACHIEVEMENTS" to listOf("RetroAchievements", "ACHIEVEMENT POPUPS"),
                "APP" to listOf("HELP"),
            ),
            groupsOf(out),
        )
        assertEquals(rows.count { !it.header }, out.count { !it.header })
    }

    @Test
    fun pagesLeaveTheListForOneRowThatCountsWhatsOn() {
        fun on(label: String, v: String) = SettingRow(label, v) {}
        val rows = listOf(
            groupTitle("GAMEPLAY"), on("WILD ENCOUNTERS", "ON"), on("INSTANT TEXT", "ON"), on("AUTO-RUN", "OFF"),
            on("LOW HP BEEP", "OFF"), on("FIELD POISON", "ON"),
            groupTitle("COMPANION"), on("TAB BAR", "5"), on("BATTLE HINTS", "ON"),
        )
        var opened: String? = null
        val out = regroupSettings(rows) { opened = it }
        assertEquals(
            listOf("GAMEPLAY" to listOf(QOL_PAGE), "COMPANION" to listOf(BATTLE_PAGE, "TAB BAR")),
            groupsOf(out),
        )
        assertEquals(com.pokedaisy.app.companion.i18n.tr("{0} ON", 2), out.first { it.label == QOL_PAGE }.value)
        out.first { it.label == BATTLE_PAGE }.onClick()
        assertEquals(BATTLE_PAGE, opened)
        // WILD ENCOUNTERS / FIELD POISON on their game's own ON don't count; LOW HP BEEP's OFF does.
        assertEquals(
            listOf("WILD ENCOUNTERS", "INSTANT TEXT", "AUTO-RUN", "LOW HP BEEP", "FIELD POISON"),
            settingsPageRows(rows, QOL_PAGE).map { it.label },
        )
    }

    @Test
    fun wildPinNeverCountsAsAHelperOn() {
        // Its switch only shows or hides the PINs: neither ON nor OFF changes the game.
        fun on(label: String, v: String) = SettingRow(label, v) {}
        assertEquals(false, qolInUse(on("WILD PIN", "ON")))
        assertEquals(false, qolInUse(on("WILD PIN", "OFF")))
        assertEquals(true, qolInUse(on("INSTANT TEXT", "ON")))
        val out = regroupSettings(listOf(groupTitle("GAMEPLAY"), on("WILD PIN", "OFF"), on("AUTO-RUN", "ON"))) {}
        assertEquals(com.pokedaisy.app.companion.i18n.tr("{0} ON", 1), out.first { it.label == QOL_PAGE }.value)
    }

    @Test
    fun aFeaturedGroupKeepsItsTitleRowAndComesFirst() {
        val makeover = featuredGroupTitle("MAKEOVER", "A fresh look")
        val out = regroupSettings(listOf(groupTitle("SOUND"), row("MUTE GAME"), makeover, row("PLAYER LOOK")))
        assertTrue(out.first() === makeover)
        assertEquals(listOf("MAKEOVER", "PLAYER LOOK", "SOUND", "MUTE GAME"), out.map { it.label })
    }

    @Test
    fun everyListedRowIsInOneGroup() {
        val all = SETTINGS_GROUPS.flatMap { it.second }
        assertEquals(all.size, all.toSet().size)
    }
}
