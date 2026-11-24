# Per-game settings: research and plan

Status: built (2026-11-23), with the user's answers to the open questions: the settings in the table below, game
volume left for every game; one switch at the top of the companion's SETTINGS; the top screen's Settings never shows or
changes a game's own settings (no GAME SETTINGS page). Code: `companion/GameSettings.kt` (`PerGameSettings`,
`GameSettingsStore`), `PrefsAndroid.kt` (`GameSettingsScope`), `CompanionSettingsScreen.kt` (`gameScopedRows`).

## What exists today

- All settings live in one SharedPreferences file ("pokedaisy") behind `PreferenceStore`
  (`shared-ui/.../companion/PreferenceStore.kt`), read through `Prefs` (`shared-ui/.../Prefs.kt`).
  Android's `Prefs(context)` (`app/.../PrefsAndroid.kt:8`) builds a new store wrapper on every call:
  `PokeDaisyActivity` alone calls it ~157 times, so most reads are live.
- A few things are already per game, keyed by the ROM's CRC32 (`romKey`; a 3DS title id for 3DS games):
  GAME SPEED (`speed_$romKey`), ZOOM (`zoom_$romKey`), PLAYER LOOK / TEXT LOOK, NUZLOCKE, bag favorites,
  SHINY ALERT's count, LAST BALL, cheats (`files/cheats/<crc>.cheats`), save states (`states/<crc>/`).
- The game applies settings in three places, which a per-game layer can reuse as is:
  - `PrefsCompanionSettings.changed(PICTURE / FIELD_OPTIONS / PLAYBACK)` (wired at `PokeDaisyActivity.kt:1223`):
    PICTURE runs `syncGameScreen()`, FIELD_OPTIONS runs `applyGameOptions`.
  - `syncGameScreen()` (`:3265`) re-reads every screen setting.
  - `onResume` (`:2362`) re-applies FF, rewind, field prefs, hotkeys, touch controls and the screen.
- Settings rows already carry a stable id (`SettingRow.setting: SharedSetting?`, `SettingRows.kt:68`), which is
  the natural key for "this row can be per game".

## Which settings should be per game

Only settings that are about *how a game plays or looks*, never the app, the library, accounts or devices.

| Group | Settings (key) |
| --- | --- |
| Speed / FF | FF SPEED `ff_max_speed`, FF MODE `ff_mode`, FF MUSIC `ff_music_mode`, REWIND `rewind`, SCENE SPEEDS `scene_speed_*` (GAME SPEED is already per game) |
| Screen | ASPECT / EXPAND `game_aspect`, AUTO ZOOM IN `expand_zoom_on_text`, LOOK AROUND `look_around`, OVERLAY `overlay`, FILTER `screen_filter`, GRID `grid_strength`, SMOOTHING `smoothing`, GBA COLORS `gba_colors`, UNLIT SCREEN `unlit_screen` |
| Gameplay (QUALITY OF LIFE page) | WILD ENCOUNTERS, INSTANT TEXT, AUTO-RUN, RUN INDOORS, AUTO REFILL, REUSABLE TMS, NO HMS NEEDED, EXP SHARE, LEVEL CAP, TRADE EVOS, SHINY ODDS, SHINY ALERT, BOSS RETRY, LOW HP BEEP, FIELD POISON, RUNNING SHOES |
| Makeover | FOLLOWER, DAY AND NIGHT, RIVAL LOOK, FLY STYLE, FLIGHT AREA, FLY ON, RIDE ON (PLAYER / TEXT LOOK already per game) |
| DS | DS CORE, DS RESOLUTION, DS CPU BOOST, DS LAYOUT |
| Battle | BATTLE STYLE, ONE-TAP MOVES, HIDE BATTLE MENU, BATTLE BUTTONS |

Stays global: GAME VOLUME (the user's call), THEME, DARK MODE, FONT, LANGUAGE, COMPANION LOOK, TWEAKS, HUD, QUICK MENU, TOUCH CONTROLS,
GAME BUTTONS / HOTKEYS (files), folders, accounts, library options. TOUCH CONTROLS and the button files could be a
second phase if players ask (a GBA and a DS game want different layouts).

## Design

### Storage: an override layer in the store

A `GameScopedStore(base, gameKey)` decorator over `PreferenceStore`:

- **Read**: for a key in `PerGameKeys` that has an override for this game (`game.<key>.<name>` present), return
  the override; else the global value. `contains` the same way (DS CPU BOOST's default depends on it).
- **Write**: while the row is in THIS GAME scope, a write to an eligible key goes to `game.<key>.<name>`;
  otherwise to the global key. A batch write (TOUCH STYLE writes several keys) is routed key by key.
- **keys()**: hides `game.*` from the prefix scans (`played_`, `romname_`).
- An override is removed (not set to the global value) when the player sets that setting back to ALL GAMES, so
  later global changes reach the game again.

Scope is per instance, never a process global: `PokeDaisyActivity` stays alive behind the library, and a global
"active game" would leak a game's overrides into the library and top-screen Settings. So the game builds its
`Prefs` with the decorator for its `romKey` (one helper replacing the activity's `Prefs(this)` calls, plus the
instances handed to `EmulatorEngine` / `GameRuntime` / `GameScreenView`). The library and top-screen Settings keep
the plain store and so always show the global values.

`PerGameKeys` is one list in commonMain (the table above), shared with iOS (`IosPreferences` uses the same store).

### UI (companion SETTINGS, the only place a game is open)

- A `SETTINGS FOR` switch at the top of SETTINGS: `ALL GAMES` / `THIS GAME`. In THIS GAME, only eligible rows
  are shown (others greyed with "for all games"), and a change writes an override.
- Rows with an override carry a small game tag (like NEW) in ALL GAMES view too, so a player sees why a game
  behaves differently; a long press (or the row's pick-list) offers `USE ALL GAMES' SETTING` to drop it.
- A `RESET THIS GAME` row clears every override of the game.
- Top-screen Settings: a `GAME SETTINGS` page (FOLDERS-like hub) listing games with overrides, each opening its
  list of overridden settings with a REMOVE per row. No editing there in phase 1 (rows need the game's config to
  know what's offered).

### Applying a change

Flipping scope or dropping an override calls the paths that already exist: `syncGameScreen()`,
`applyFieldPrefs()` / `applyGameOptions()`, the engine's FF setters, `GameVolume.load`. Singletons loaded once per
process need a reload on game start: `GameVolume`, `GameShinyOdds.selected`, the engine's cached FF fields, and
`dsCoreId` (read once per ROM load, which is the right moment anyway).

### Export / import

The settings file (built in this branch) carries every key, so `game.*` overrides are exported and restored with
no extra work.

## Risks

- Every place that reads a game setting must use the game's `Prefs`. The ~157 `Prefs(this)` calls become one
  `gamePrefs()`; a grep check in a unit test (no `Prefs(this` in `PokeDaisyActivity.kt`) keeps it that way.
- Compose state caches (`hudRevision`, `statusBarRevision`, the activity's `hudOn`, `battleStyle`...) only refresh
  through `syncGameScreen`, so the scope switch must call it.
- `battle_style` is an Int on Android and a name on iOS: the override keeps whatever type the host writes.
- The 3DS's `romKey` is 16 hex digits: keys must allow it.

## Phases

1. `GameScopedStore` + `PerGameKeys` + tests (commonMain): reads, writes, batches, `contains`, `keys()`.
2. The game's Prefs through it; reload of the cached singletons on game start.
3. Companion SETTINGS: the scope switch, the game tag, RESET THIS GAME. Manual, NEW tag, translations.
4. Top-screen GAME SETTINGS page (view / remove).
5. Later, if asked: TOUCH CONTROLS and button files per game.

## Open questions

1. Which settings go in? The table above is a proposal; anything to add or take out (TOUCH CONTROLS, HUD)?
2. Scope switch at the top of SETTINGS, or a per-row "this game only" toggle?
3. Should the top-screen Settings edit a game's overrides (needs the game's config to know what's offered), or
   only list and remove them?
