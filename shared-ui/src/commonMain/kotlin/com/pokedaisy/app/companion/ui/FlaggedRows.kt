package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.FlaggedFeatures

/** [FlaggedFeatures.modernLook]'s rows on any list. */
private val LOOK_ROWS = setOf("VIEW", "FONT SIZE", "MY LOOK", "SIDE BARS", "COMPANION BACKGROUND", "MENU BAR", "MENU BAR SIZE")

/** [FlaggedFeatures.modernLook]'s rows on one page only (their labels mean something else elsewhere): by the page's name. */
private val LOOK_PAGE_ROWS = mapOf(
    "TABS" to setOf("SHOW AS", "POSITION", "SIZE", "TABS"),
    "FIELD_HUD" to setOf("EDIT LAYOUT", "DELETE LAYOUT"),
    "TOUCH" to setOf("NEW LAYOUT", "DELETE LAYOUT"),
)

/** [FlaggedFeatures.settingsPlus]' rows. */
private val SETTINGS_PLUS_ROWS = setOf("SETTINGS FILE")

/** [rows] without the rows of a batch that's off (on [page], a page's name; null = a home list), and without the group
 * titles left with nothing under them. */
fun flaggedRows(rows: List<SettingRow>, page: String? = null): List<SettingRow> {
    if (FlaggedFeatures.modernLook && FlaggedFeatures.settingsPlus) return rows
    val pageRows = page?.let { LOOK_PAGE_ROWS[it] }.orEmpty()
    val kept = rows.filter { r ->
        when {
            !FlaggedFeatures.modernLook && (r.label in LOOK_ROWS || r.label in pageRows) -> false
            !FlaggedFeatures.settingsPlus && !r.header && r.label in SETTINGS_PLUS_ROWS -> false
            else -> true
        }
    }
    if (kept.size == rows.size) return rows
    return kept.filterIndexed { i, r -> !r.header || kept.getOrNull(i + 1)?.let { !it.header } ?: false }
}
