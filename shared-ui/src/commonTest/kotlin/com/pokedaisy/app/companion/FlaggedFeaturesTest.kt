package com.pokedaisy.app.companion

import com.pokedaisy.app.Prefs
import com.pokedaisy.app.companion.ui.SettingRow
import com.pokedaisy.app.companion.ui.groupTitle
import kotlin.test.*

class FlaggedFeaturesTest {
    private class Store(val values: MutableMap<String, Any?> = mutableMapOf()) : PreferenceStore {
        override fun keys() = values.keys.toSet()
        override fun contains(key: String) = values.containsKey(key)
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun write(values: Map<String, Any?>) {
            for ((k, v) in values) if (v == null) this.values.remove(k) else this.values[k] = v
        }
    }

    @AfterTest fun off() { FlaggedFeatures.modernLook = false; FlaggedFeatures.settingsPlus = false; FlaggedFeatures.shinyHunt = false
        ShinyHunts.close(ShinyHunts.preview(com.pokedaisy.app.companion.data.HuntLog())) }

    @Test fun offTheHuntTabAndItsGuardAreNotThere() {
        FlaggedFeatures.shinyHunt = false
        assertFalse("HUNT" in COMPANION_TABS)
        ShinyHunts.shinyShown(inBattle = true)
        assertFalse(ShinyHunts.loadHeld())
        FlaggedFeatures.shinyHunt = true
        assertTrue("HUNT" in COMPANION_TABS)
        assertTrue(ShinyHunts.loadHeld())
    }

    @Test fun offTheLookSettingsReadAsBeforeAndOnAsSaved() {
        val store = Store(mutableMapOf("app_view" to 1, "font_size" to 3, "side_bars" to 2, "tab_icons" to true,
            "library_view_mode" to 4, "app_view_asked" to false, "custom_font" to "x.ttf"))
        val prefs = Prefs(store)
        FlaggedFeatures.modernLook = false
        assertEquals(0, prefs.appView); assertEquals(1, prefs.fontSize); assertEquals(0, prefs.sideBars)
        assertFalse(prefs.tabIcons); assertEquals(0, prefs.libraryViewMode); assertTrue(prefs.appViewAsked); assertEquals("", prefs.customFont)
        FlaggedFeatures.modernLook = true
        assertEquals(1, prefs.appView); assertEquals(3, prefs.fontSize); assertEquals(2, prefs.sideBars)
        assertTrue(prefs.tabIcons); assertEquals(4, prefs.libraryViewMode); assertFalse(prefs.appViewAsked)
    }

    @Test fun offTheBatchesRowsAreLeftOutWithTheirEmptyGroups() {
        fun row(l: String) = SettingRow(l, null) {}
        val home = listOf(groupTitle("LOOK"), row("VIEW"), row("FONT SIZE"), groupTitle("SCREEN"), row("THEME"), row("MY LOOK"),
            groupTitle("APP"), row("SETTINGS FILE"), row("HELP"))
        assertEquals(listOf("SCREEN", "THEME", "APP", "HELP"), com.pokedaisy.app.companion.ui.flaggedRows(home).map { it.label })
        val tabs = listOf(row("SHOW AS"), row("POSITION"), row("SIZE"), groupTitle("TABS"), row("PARTY"))
        assertEquals(listOf("PARTY"), com.pokedaisy.app.companion.ui.flaggedRows(tabs, "TABS").map { it.label })
        // SIZE elsewhere (HUD's) stays.
        assertEquals(listOf("SIZE"), com.pokedaisy.app.companion.ui.flaggedRows(listOf(row("SIZE")), "FIELD_HUD").map { it.label })
        FlaggedFeatures.modernLook = true; FlaggedFeatures.settingsPlus = true
        assertEquals(home, com.pokedaisy.app.companion.ui.flaggedRows(home))
    }

    @Test fun offTheViewButtonFlipsListAndGridWithNoPickList() {
        var dates: Map<String, LibraryAddedDates.Entry>? = null
        val prefs = LibraryPreferences(Prefs(Store()), LibraryAddedDates({ dates }, { dates = it }))
        val library = LibraryCoordinator({ prefs })
        library.toggleGrid()
        assertNull(library.dialog)
        assertEquals(LibraryLayout.GRID, library.layout)
        library.toggleGrid()
        assertEquals(LibraryLayout.LIST, library.layout)
    }
}
