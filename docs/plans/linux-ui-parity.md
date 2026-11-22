# Desktop UI parity with Android (Linux, Windows, Steam Deck, Linux handhelds)

The desktop build (the SDL host in `linux/`, built for Linux, Windows and PortMaster; the three libretro cores) draws its
companion, HUD and quick menu with the C code it shares with the 3DS (`3ds/core/pd_ui*.c`, `pd_style.c`,
`pd_settings.c`). Android is the reference: every screen a player sees there should look and work the same here, at
every screen size below. **Decision (2026-11-23, the user's): the desktop UI stays in that C engine** (one UI for
Windows, the Deck, the handhelds and the 3DS); Compose Desktop (`ui-preview`) was the alternative, left aside because it
can't run on the handhelds or the 3DS.

## Screen sizes

Every change is looked at with `python3 3ds/tools/desktop_preview.py` (after `make -C 3ds build/pd_preview`): the
companion's shots at the size the host gives it in each window below (`PD_PREVIEW_WINDOW`), both fonts, and
`3ds/build/desktop-shots/index.html` showing each at its real share of the window. `--only 1280x800,1920x1080` for a few.

| Window | What | Companion today |
| --- | --- | --- |
| 640x480 | Handheld 4:3 | fills the screen, 320x240 at 2x |
| 720x720 | Square handheld | window 320x270 at 2x |
| 1024x768 | TrimUI Brick | window at 2x |
| 1280x720 | 720p handheld | window at 2x |
| 1280x800 | Steam Deck | window 336x270 at 2x |
| 1366x768 | Small laptop | window at 2x |
| 1920x1080 | Monitor, docked Deck | window at 4x |
| 2560x1440 | 1440p | window at 5x |
| 3840x2160 | 4K (physical pixels: the exe is DPI-aware) | window at 8x |
| 3440x1440 | Ultrawide | window at 5x |
| 960x600 | A small desktop window | window at 2x |
| 800x1280 | Portrait window | fills a 400x480 canvas at 2x (CANVAS_MAX_H 480 leaves the bottom empty) |

The 3DS's own 320x240 (`make -C 3ds preview`) must stay as it is: the code is shared.

## Phases and status

- [ ] **0. Foundation**
  - [x] The size matrix (`desktop_preview.py`, `PD_PREVIEW_WINDOW`, `PD_PREVIEW_FONT`)
  - [x] Windows: per-monitor DPI awareness (`windows/pokedaisy.manifest`)
  - [x] Desktop window: opens at a size for the monitor (largest whole multiple of 240x160 in 85% of it), remembers its
        size, place and maximized state (`host.ini` `window=`, `window_pos=`, `maximized=`), F11 / Alt+Enter and a
        FULLSCREEN row (SCREEN group, companion SETTINGS and the game list's) on desktops only (`windowedHost`)
  - [x] FONT TYPE: PIXEL SMALL (Android's default since 2026-11-18) / PIXEL in the GB / GBA cores' SETTINGS (SCREEN, under
        DARK MODE; `settings.ini` `fontpixel=`), the companion only (the HUD and quick menu keep Pixel Operator)
  - [ ] FONT TYPE in the DS core (`linux/libretro-ds/companion_ui.cpp` keeps its own settings)
  - [ ] UI SIZE for big monitors (Android's FONT SIZE): the companion's rows per screen height
  - [ ] Portrait windows: a canvas past 480 rows
- [ ] **1. Look** (SUMMARY MINT under `retroStylePalette`'s blues, as Android since 2026-11-22)
  - [x] The palette in C (`pd_style.c`'s `RETRO`, light and dark), moved to a THEME's hue (`rehue` = NightColors.kt's
        `rehued`), turned on by the desktop cores (`pd_style_enable`; the 3DS keeps the OPTION look, pixel for pixel)
  - [x] Header bands (`pd_header_box`: SETTINGS, POKéDEX, SAVE STATES, NUZLOCKE, pick-lists, FLY / PLACES, notices),
        the list window's frame, striped rows, full-width section bands (`pd_section_title`), tab chips and the settings
        rail as keys (`pd_chip_face`), buttons as keys (`ui_button`); title windows stay white as in the app
  - [x] The dex's sections and MORE's tiles as keys (the summary's STATS / INFO stay Platinum buttons, as in the app)
  - [x] The DS core's companion in the style, with FONT TYPE in its SETTINGS (SCREEN, under DARK MODE)
  - [x] Battle INFO and SUGGESTIONS in cells (`pd_cell`, `pd_cells`): your card and moves blue, the foe's card and
        matchups red under a heading cell, the side words as tags
  - [ ] The game list (`linux/library.c`), FOE DETAILS' rows, the summary's MOVES cells
  - [ ] Folder tabs on the window's side (`FolderTab`), the header joined to its list (`OptionTitleWindow(joined)`)
  - [ ] `PanelHeader` bands, `GameTag` tags (12 px, notched corners, #424242 shadow)
  - [ ] Themes move the whole palette (`themedStylePalette`, `rehued`), the focus ring in the theme's opposite colour
  - [ ] GAME COLORS / game skins (LOOK > IN GAME), dark mode's windows
  - [ ] MODERN (after RETRO; behind `modernLook` until 1.7.0): needs anti-aliased corners in `pd_canvas`
- [ ] **2. Companion screens**: party (+ held item marker), summary + move page + LEARN, bag (favorites, sort, USE / GIVE /
      TAKE, TM learners), map (FLY, PLACES, region flip), guide, dex sections, battle INFO / SUGGESTIONS / FOE DETAILS,
      boxes (WITHDRAW / DEPOSIT, the move animation), card, states, nuzlocke, MORE's tile grid, HELP reader, achievements
      (rcheevos on desktop)
- [ ] **3. Settings**: `SETTINGS_GROUPS`' order, the QUALITY OF LIFE and BATTLE pages, sub-pages with chevrons,
      DESCRIPTIONS / GROUP TABS, NEW tags, search (a desktop has a keyboard), translations of the host's rows
- [ ] **4. Library and tools**: header band, RECENTLY PLAYED, LAST PLAYED / DATE ADDED, NEW tags, hidden games, INFO,
      RENAME, cover picker, WHAT'S NEW, first-time setup, folders; TOOLS' SAVE EDITOR / TRADE / COLLECTION from the 3DS's
      C editor (`pd_library_saves.c`)
- [ ] **5. Desktop and Deck behaviour**: hover, the wheel everywhere, right-click = a game's menu, text entry (Steam's
      keyboard in Game Mode), file pickers (Windows' own, the desktop portal / zenity on Linux), `%APPDATA%` or portable,
      Deck touch, button legends per controller
- [ ] **6. Optional**: the companion in a second window on a second monitor

## Found so far

- The desktop draws Pixel Operator everywhere; Android's companion is PIXEL SMALL by default.
- The companion's SETTINGS is the old OPTION look (white title window, grey list); only its rows are banded.
- Portrait: the menu canvas stops at 480 rows (`CANVAS_MAX_H`), so an 800x1280 window shows 400x480 at 2x with a third
  of the window empty.
- The host's rows (A/B BUTTONS, LCD FILTER ...) aren't translated (FULLSCREEN is, in `3ds/tools/tr_3ds.txt`).
- `make -C 3ds preview` and `test-scroll` already fail on dev (2026-11-23): the script's tap on SAVE STATE (id 602) finds
  no row since SETTINGS was regrouped. To fix with phase 3.
- `3ds/tools/parity_audit.py` covers the per-game data side of parity (which ROM has which tab / table); this plan is the
  UI side.
