package com.pokedaisy.app.companion

import com.pokedaisy.app.Prefs
import kotlin.test.*

class GameSettingsTest {
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

    @Test fun allGamesWritesEveryGamesValueAndThisGameWritesItsOwn() {
        val base = Store()
        var game: String? = "0123abcd"
        val scoped = GameSettingsStore(base) { game }
        val prefs = Prefs(scoped)
        prefs.rewind = true                                // ALL GAMES
        assertEquals(true, base.values["rewind"])
        scoped.setThisGame(true)
        prefs.rewind = false                               // THIS GAME: its own value
        prefs.appTheme = 41                                // not a per-game setting: every game's
        assertEquals(true, base.values["rewind"]); assertEquals(false, base.values["game.0123abcd.rewind"])
        assertEquals(41, base.values["app_theme"])
        assertFalse(prefs.rewind)
        assertTrue(Prefs(base).rewind, "the library sees every game's value")
        game = "89abcdef"                                  // another game, ALL GAMES there
        assertTrue(prefs.rewind)
        assertEquals(setOf(SharedSetting.REWIND), PerGameSettings.overridden("0123abcd", base.keys()))
    }

    @Test fun aSettingWithItsOwnValueStaysTheGamesInAllGamesToo() {
        val base = Store(mutableMapOf("game.0123abcd.wild_encounters" to 0, "wild_encounters" to 100))
        val prefs = Prefs(GameSettingsStore(base) { "0123abcd" })
        assertEquals(0, prefs.wildEncounters)
        prefs.wildEncounters = 100                         // the QUICK MENU's WILD, say: still the game's
        assertEquals(100, base.values["game.0123abcd.wild_encounters"])
        assertEquals(100, base.values["wild_encounters"])
    }

    @Test fun forgetAndResetDropTheGamesOwnValues() {
        val base = Store(mutableMapOf("game.0123abcd.rewind" to true, "game.0123abcd.ff_max_speed" to 4f,
            "game.89abcdef.rewind" to false, PerGameSettings.scopeKey("0123abcd") to true, "rewind" to false))
        val scoped = GameSettingsStore(base) { "0123abcd" }
        assertEquals(setOf(SharedSetting.REWIND, SharedSetting.FF_SPEED), scoped.overridden())
        scoped.forget(SharedSetting.REWIND)
        assertFalse(Prefs(scoped).rewind)
        scoped.forgetAll()
        assertEquals(setOf("game.89abcdef.rewind", "rewind"), base.keys())
        assertFalse(scoped.thisGame())
    }

    @Test fun containsFollowsTheGamesOwnValue() {
        val base = Store(mutableMapOf("game.0123abcd.ds_cpu_boost" to false))
        assertTrue(GameSettingsStore(base) { "0123abcd" }.contains("ds_cpu_boost"))
        assertFalse(GameSettingsStore(base) { null }.contains("ds_cpu_boost"))
    }

    @Test fun everyKeyIsAPrefsKey() {
        // A typo here would make a setting silently stay every game's.
        val known = setOf("ff_max_speed", "ff_mode", "ff_music_mode", "ff_music_enabled", "scene_speed_battle", "scene_speed_text",
            "scene_speed_menu", "scene_speed_bike", "scene_speed_surf", "rewind", "game_aspect", "stretch_game", "expand_zoom_on_text",
            "look_around", "overlay", "screen_filter", "grid_strength", "smoothing", "gba_colors", "unlit_screen", "wild_encounters",
            "wild_encounters_before_off", "instant_text", "auto_run", "run_indoors", "repel_refill", "reusable_tms", "hm_free",
            "exp_share", "level_cap", "trade_evolutions", "shiny_odds", "shiny_alert", "battle_retry", "low_hp_beep", "field_poison",
            "running_shoes", "follower", "day_night", "rival_look", "fly_style", "flight_area", "flight_vehicle", "ride_vehicle",
            "ds_core", "ds_resolution", "ds_cpu_boost", "ds_layout", "battle_style", "one_tap_moves", "hide_battle_menu", "battle_buttons")
        assertEquals(known, PerGameSettings.PREF_KEYS)
        assertFalse("game_volume" in PerGameSettings.PREF_KEYS)
    }
}
