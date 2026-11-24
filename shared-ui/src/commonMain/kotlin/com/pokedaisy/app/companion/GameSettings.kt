package com.pokedaisy.app.companion

/**
 * Per-game settings (companion SETTINGS > SETTINGS FOR: ALL GAMES / THIS GAME). A game can keep its own value of the
 * settings about how it plays and looks ([PerGameSettings.KEYS]), stored beside the normal one as
 * `game.<game key>.<pref key>` (the game key is the ROM's CRC32, a 3DS game's title id: SaveStates' key).
 *
 * Only the game sees them: its Prefs goes through [GameSettingsStore]; the library and the top screen's Settings read
 * the plain store, so they always show (and change) the values every game uses. The user's call, 2026-11-23: a game's
 * own settings are made in that game only.
 */
object PerGameSettings {
    /** Each setting a game can have its own value of, with the pref keys it reads and writes (legacy keys too). Game
     * volume stays for every game (the user's call). */
    val KEYS: Map<SharedSetting, List<String>> = mapOf(
        SharedSetting.FF_SPEED to listOf("ff_max_speed"),
        SharedSetting.FF_MODE to listOf("ff_mode"),
        SharedSetting.FF_MUSIC to listOf("ff_music_mode", "ff_music_enabled"),
        SharedSetting.SCENE_SPEEDS to listOf("scene_speed_battle", "scene_speed_text", "scene_speed_menu", "scene_speed_bike", "scene_speed_surf"),
        SharedSetting.REWIND to listOf("rewind"),
        SharedSetting.ASPECT to listOf("game_aspect", "stretch_game"),
        SharedSetting.AUTO_ZOOM_IN to listOf("expand_zoom_on_text"),
        SharedSetting.LOOK_AROUND to listOf("look_around"),
        SharedSetting.OVERLAY to listOf("overlay"),
        SharedSetting.FILTER to listOf("screen_filter"),
        SharedSetting.GRID to listOf("grid_strength"),
        SharedSetting.SMOOTHING to listOf("smoothing"),
        SharedSetting.GBA_COLORS to listOf("gba_colors"),
        SharedSetting.UNLIT_SCREEN to listOf("unlit_screen"),
        SharedSetting.WILD_ENCOUNTERS to listOf("wild_encounters", "wild_encounters_before_off"),
        SharedSetting.INSTANT_TEXT to listOf("instant_text"),
        SharedSetting.AUTO_RUN to listOf("auto_run"),
        SharedSetting.RUN_INDOORS to listOf("run_indoors"),
        SharedSetting.AUTO_REFILL to listOf("repel_refill"),
        SharedSetting.REUSABLE_TMS to listOf("reusable_tms"),
        SharedSetting.NO_HMS_NEEDED to listOf("hm_free"),
        SharedSetting.EXP_SHARE to listOf("exp_share"),
        SharedSetting.LEVEL_CAP to listOf("level_cap"),
        SharedSetting.TRADE_EVOS to listOf("trade_evolutions"),
        SharedSetting.QUICK_HATCH to listOf("quick_hatch"),
        SharedSetting.SHINY_ODDS to listOf("shiny_odds"),
        SharedSetting.SHINY_ALERT to listOf("shiny_alert"),
        SharedSetting.BOSS_RETRY to listOf("battle_retry"),
        SharedSetting.LOW_HP_BEEP to listOf("low_hp_beep"),
        SharedSetting.FIELD_POISON to listOf("field_poison"),
        SharedSetting.RUNNING_SHOES to listOf("running_shoes"),
        SharedSetting.FOLLOWER to listOf("follower"),
        SharedSetting.DAY_AND_NIGHT to listOf("day_night"),
        SharedSetting.RIVAL_LOOK to listOf("rival_look"),
        SharedSetting.FLY_STYLE to listOf("fly_style"),
        SharedSetting.FLIGHT_AREA to listOf("flight_area"),
        SharedSetting.FLY_ON to listOf("flight_vehicle"),
        SharedSetting.RIDE_ON to listOf("ride_vehicle"),
        SharedSetting.DS_CORE to listOf("ds_core"),
        SharedSetting.DS_RESOLUTION to listOf("ds_resolution"),
        SharedSetting.DS_CPU_BOOST to listOf("ds_cpu_boost"),
        SharedSetting.DS_LAYOUT to listOf("ds_layout"),
        SharedSetting.BATTLE_STYLE to listOf("battle_style"),
        SharedSetting.ONE_TAP_MOVES to listOf("one_tap_moves"),
        SharedSetting.HIDE_BATTLE_MENU to listOf("hide_battle_menu"),
        SharedSetting.BATTLE_BUTTONS to listOf("battle_buttons"),
    )

    /** Every pref key a game can have its own value of. */
    val PREF_KEYS: Set<String> = KEYS.values.flatten().toSet()

    /** Settings that were always kept for each game (by their own keys): THIS GAME lists them too. */
    val ALWAYS_PER_GAME: Set<SharedSetting> = setOf(
        SharedSetting.GAME_SPEED, SharedSetting.ZOOM, SharedSetting.PLAYER_LOOK, SharedSetting.TEXT_LOOK, SharedSetting.NUZLOCKE,
    )

    /** Page rows SETTINGS FOR > THIS GAME keeps: their pages hold per-game settings. */
    val PAGES: Set<SharedSetting> = setOf(SharedSetting.SPEED_OPTIONS, SharedSetting.DISPLAY_OPTIONS, SharedSetting.SHADERS)

    private const val PREFIX = "game."

    /** Where [game]'s own value of [key] is kept. */
    fun key(game: String, key: String) = "$PREFIX$game.$key"

    /** The pref saying [game]'s SETTINGS edit its own values (THIS GAME), kept for that game. */
    fun scopeKey(game: String) = "game_settings_this_game_$game"

    /** The keys of [game]'s own values among [keys] (a store's), with the scope switch. */
    fun ownKeys(game: String, keys: Set<String>): Set<String> =
        keys.filterTo(mutableSetOf()) { it.startsWith("$PREFIX$game.") || it == scopeKey(game) }

    /** The settings [game] has its own value of. */
    fun overridden(game: String, keys: Set<String>): Set<SharedSetting> =
        KEYS.filterValues { prefKeys -> prefKeys.any { key(game, it) in keys } }.keys
}

/**
 * The game's view of the settings: for a [PerGameSettings.PREF_KEYS] key, the game's own value when it has one, else the
 * value every game uses. A write goes to the game's own value when it already has one, or while its SETTINGS edit THIS
 * GAME ([PerGameSettings.scopeKey]); else to every game's. [game] is read on each call, so a ROM switched in place
 * (a frontend's next game) gets its own values at once; null = no game, the plain store.
 */
class GameSettingsStore(private val base: PreferenceStore, private val game: () -> String?) : PreferenceStore {
    private fun read(key: String): String {
        val g = game()?.takeIf { it.isNotEmpty() } ?: return key
        if (key !in PerGameSettings.PREF_KEYS) return key
        val own = PerGameSettings.key(g, key)
        return if (base.contains(own)) own else key
    }

    override fun keys(): Set<String> = base.keys()
    override fun contains(key: String) = base.contains(read(key))
    override fun getBoolean(key: String, default: Boolean) = base.getBoolean(read(key), default)
    override fun getInt(key: String, default: Int) = base.getInt(read(key), default)
    override fun getLong(key: String, default: Long) = base.getLong(read(key), default)
    override fun getFloat(key: String, default: Float) = base.getFloat(read(key), default)
    override fun getString(key: String, default: String?) = base.getString(read(key), default)

    override fun write(values: Map<String, Any?>) {
        val g = game()?.takeIf { it.isNotEmpty() }
        if (g == null) return base.write(values)
        val thisGame = base.getBoolean(PerGameSettings.scopeKey(g), false)
        base.write(values.mapKeys { (key, _) ->
            if (key !in PerGameSettings.PREF_KEYS) key
            else PerGameSettings.key(g, key).takeIf { thisGame || base.contains(it) } ?: key
        })
    }

    /** SETTINGS FOR: whether [game]'s SETTINGS edit its own values. */
    fun thisGame(): Boolean = game()?.let { base.getBoolean(PerGameSettings.scopeKey(it), false) } ?: false
    fun setThisGame(on: Boolean) {
        val g = game() ?: return
        base.write(mapOf(PerGameSettings.scopeKey(g) to if (on) true else null))
    }

    /** The settings the game has its own value of. */
    fun overridden(): Set<SharedSetting> = game()?.let { PerGameSettings.overridden(it, base.keys()) } ?: emptySet()

    /** Drops the game's own value of [setting] (back to every game's). */
    fun forget(setting: SharedSetting) {
        val g = game() ?: return
        base.write(PerGameSettings.KEYS[setting].orEmpty().associate { PerGameSettings.key(g, it) to null })
    }

    /** RESET THIS GAME: every own value dropped and SETTINGS FOR back to ALL GAMES. */
    fun forgetAll() {
        val g = game() ?: return
        base.write(PerGameSettings.ownKeys(g, base.keys()).associateWith { null })
    }
}
