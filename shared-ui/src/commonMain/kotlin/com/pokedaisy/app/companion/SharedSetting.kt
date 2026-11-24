package com.pokedaisy.app.companion

/** Stable identities for settings implemented by both mobile hosts. Each screen owns its display labels. */
enum class SharedSetting {
    CLICK_SOUND,
    BATTLE_HINTS,
    FOE_IVS,
    TAB_BAR,
    DARK_MODE,
    THEME,
    LANGUAGE,
    TIPS,
    HELP,
    FF,
    FF_SPEED,
    GAME_SPEED,
    GAME_VOLUME,
    MUTE_GAME,
    ONE_TAP_MOVES,
    BATTLE_INFO,
    BATTLE_STYLE,
    BATTLE_VIEW,
    BATTLE_FOCUS,
    LAST_BALL,
    RESUME_GAMES,
    TOUCH_CONTROLS,
    COMPANION_VIEW,
    SPEED_OPTIONS,
    INSTANT_TEXT,
    REUSABLE_TMS,
    AUTO_RUN,
    RUN_INDOORS,
    WILD_ENCOUNTERS,
    GAME_BUTTONS,
    HOTKEYS,
    QUICK_MENU,
    HUD,
    BATTLE_BUTTONS,
    REWIND,
    BOSS_RETRY,
    CHEATS,
    ASPECT,
    ZOOM,
    AUTO_ZOOM_IN,
    LOOK_AROUND,
    TRADE_EVOS,
    NO_HMS_NEEDED,
    EXP_SHARE,
    LEVEL_CAP,
    DISPLAY_OPTIONS,
    FPS_CPU,
    STATUS_BAR,
    OVERLAY,
    SHADERS,
    FILTER,
    GRID,
    GBA_COLORS,
    UNLIT_SCREEN,
    ON_COMPANION,
    SMOOTHING,
    DAY_AND_NIGHT,
    SHINY_ODDS,
    SHINY_ALERT,
    LOW_HP_BEEP,
    FIELD_POISON,
    NUZLOCKE,
    FLY_STYLE,
    FLIGHT_AREA,
    FLY_ON,
    FOLLOWER,
    PLAYER_LOOK,
    RIVAL_LOOK,
    TEXT_LOOK,
    RIDE_ON,
    AUTO_REFILL,
    POKEMON_PICTURES,
    // Rows given an id for per-game settings (GameSettings.kt); the Android companion's alone so far ([ANDROID_ONLY]).
    FF_MODE,
    FF_MUSIC,
    SCENE_SPEEDS,
    RUNNING_SHOES,
    DS_CORE,
    DS_RESOLUTION,
    DS_CPU_BOOST,
    DS_LAYOUT,
    HIDE_BATTLE_MENU,
    QUICK_HATCH,
    /** SETTINGS FOR: ALL GAMES / THIS GAME. */
    SETTINGS_FOR;

    companion object {
        /** Rows only the Android companion draws: an iPad's SETTINGS (its shownRows) leaves them out as before. */
        val ANDROID_ONLY = setOf(RUNNING_SHOES, DS_CORE, DS_RESOLUTION, DS_CPU_BOOST, DS_LAYOUT, HIDE_BATTLE_MENU, QUICK_HATCH, SETTINGS_FOR)

        fun available(running: Boolean, cheats: Boolean, gba: Boolean): Set<SharedSetting> =
            entries.filterTo(mutableSetOf()) {
                it !in ANDROID_ONLY && when (it) {
                    GAME_SPEED, FF -> running
                    CHEATS -> cheats
                    BOSS_RETRY -> gba
                    else -> true
                }
            }
    }
}
