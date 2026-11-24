# The companion's HOME tab: plan

Status: phase 1 built (2026-11-24) for 1.7.0, no feature flag (the user's call), with the 1.7.0 UI batches (`settingsPlus`, `modernLook`) turned on by default so it is built on the look that ships. The user's idea: a first tab that puts the useful things in one place - the map,
the party, money, the clock, the battery and more - instead of hopping between tabs, with a little room to make it your own
(shortcuts to BOXES, NUZLOCKE, ACHIEVEMENTS, ...). It has to work on every companion size, from the RG DS's 640x480 to the Thor and the
Retroid Pocket Duo, and in the one-screen window, the side panel and a phone held upright.

Wireframes of every size below, drawn at one scale: `companion-home-mockups.html` (beside this file).

## What HOME is

Along its top, a **status bar** like the game screen's (`GameStatusBar`, the user's call 2026-11-24): the game's name, the place (when the MAP card isn't on), money, the clock and the battery; left out while STATUS BAR is on COMPANION. Money, clock and battery were cards before.

A page of **cards** over the companion's usual backdrop, drawn in the SUMMARY MINT pieces (`OptionListWindow`, `PanelHeader`, `cell()`,
MODERN's pills under `modernView`), never scrolling. Two cards are big (**PARTY**, **MAP**); the rest are small and take what room is
left, in the order the player put them. Each card has a **BOX** form (a few lines) and a **LINE** form (one row); the layout uses the
biggest form that fits and leaves out what doesn't fit this screen (SETTINGS > HOME says which, "shows on a bigger screen").

A tap on a card opens its tab (PARTY's slot opens that Pokémon's summary, as the PARTY tab does; MAP opens MAP; NEXT BOSS opens GUIDE >
NEXT BOSS; ...). Under the cards, a row of **SHORTCUTS**.

### The cards

| Card | Shows | Tap | When it's there |
|---|---|---|---|
| PARTY (big) | the six: icon, level, HP bar, status / fainted, a held-item mark; names where there's room | the slot's summary (`detailIndex`) | always (empty slots before a party) |
| MAP (big) | the region map round the player with their head, the place's name | MAP | the game has a region map (`activeRegionMap()`); else the name alone in a LINE |
| TRAINER | play time, badges (dots), POKéDEX caught / seen | CARD (else DEX) | the card or the Pokédex reads |
| NEXT BOSS | name, badge, their top level (+ the LEVEL CAP when it's on) | GUIDE > NEXT BOSS | the game has a NEXT BOSS list (`GameGuide.bosses`) |
| HERE | this map's wild Pokémon as icons, "N of M caught" | GUIDE > HERE | HERE has wild data (`rememberWildHere`) |
| BAG | Poké Balls left, the keep-away spray's steps (the HUD's REPEL), favourite items | ITEMS | the bag reads |
| NUZLOCKE | alive / fallen, this area's encounter (used / free) | NUZLOCKE | the tracker is on |
| ACHIEVEMENTS | N of M unlocked, the newest unlock's badge, a running challenge | ACHIEVEMENTS | signed in and the set loaded |
| HUNT | the hunt's count and target | HUNT | a hunt is going (`ShinyHunts.active`, its flag) |

Everything follows the rules the tabs already follow: SPOILERS (HERE's unseen species are ???, NEXT BOSS shows no team, `Spoilers`),
BATTLE HINTS, DARK MODE, a THEME, the game's skin (`CompanionSkins`), Gen 1 / 2's black-on-white windows, LIGHT COMPANION (no blink,
no motion; the card only redraws when what it shows changes).

### The shortcuts

Up to **six** tiles, the MORE tab's coloured `TabIcons` (our own drawings) with the name under them where there's room (icons alone
on a short companion; a long name like NUZLOCKE narrows its letters first, `FittedGbaText`, as the battle buttons do). Two kinds:

- **a tab**: BOXES, NUZLOCKE, ACHIEVEMENTS, CARD, STATES, GUIDE, DEX, HUNT, ... (any `COMPANION_TABS` id the game has; one the game
  doesn't have isn't drawn);
- **an action** (phase 2): PC, HEAL, FLY, SAVE STATE, LOAD STATE, WILD ENCOUNTERS, REPEL - the QUICK MENU's `QuickAction`s, offered and
  dimmed by the same rules (`QuickActions.offered`, `quickBlocked`), run by the activity through a new `HomeActions` (like `fly` /
  `itemUse` today). A screen that shows fewer tiles shows the first ones.

Default: BOXES, ACHIEVEMENTS, STATES, CARD (each only where the game has it); NUZLOCKE takes the first place while the tracker is on.

## Where it sits in the tab bar

The user's calls (2026-11-24): HOME is a new tab added to **every** player as the **first** tab, drawn as an icon chip (a house,
`PixelIcons.home`); it counts as one of the five, so where the bar is full the last chosen tab moves under MORE (`Prefs.companionTabs`
puts HOME first on a pick saved before it, once: `companion_tabs_home`). The default bar is HOME, PARTY, DEX, MAP, BAG (GUIDE moves
under MORE and is one of HOME's shortcuts). It can be hidden in TAB BAR like any tab (then it waits under MORE and the companion opens
on the first tab kept). The companion opens on the bar's first tab (`initialTab` empty); BATTLE still takes the last chosen tab's
place and hands back to the tab that was open (HOME included) when the battle ends.

## Layouts per screen

The layout is picked from the room the tab really gets (a measuring `Layout`, like `PartyGrid`'s), in GBA pixels
(`gbaPixelPx()`: the unit `isShortCompanion` uses), never from a device name. Four shapes:

| Shape | When | Party | Map | Small cards | Shortcuts |
|---|---|---|---|---|---|
| COMPACT | under 316 GBA px tall (`LocalShortCompanion`) | a column of 6 (icon + HP bar + level, no names) | right, the rest of the width | one, as a LINE under the map (or the status line) | 4 icons, no names |
| STANDARD | about 1.1:1 to 1.45:1 | 2 x 3 slots with names, left half | right, top | 1-2 BOXes under the map | a strip along the bottom, 5-6 with names |
| WIDE | past 1.6:1 | 2 x 3, left third | middle third, as tall as fits | a column of 2-3 BOXes on the right | strip along the bottom, 6 |
| TALL | under 1:1 | 3 x 2 under the map | full width on top | 2-4 BOXes, two to a row | strip along the bottom, 6 |

Every screen the companion runs on, with the room its tab gets (GBA px, the tab bar taken off) and the shape it gets:

| Where | Screen | GBA px (whole) | Tab area | Shape |
|---|---|---|---|---|
| Anbernic RG DS bottom screen | 640x480 @1.5 | 320x240 | ~320x206 | COMPACT |
| Phone upright, companion at its default height | 1080x940 @2.625 | 360x313 | ~360x265 | COMPACT (short) |
| Phone upright, companion dragged to the smallest (`MIN_RATIO` 0.6) | 1080x648 | 360x216 | ~360x170 | COMPACT, no small card |
| Phone upright, companion dragged to the full height | 1080x~1600 | 360x~530 | ~360x480 | TALL |
| Retroid Pocket Duo / Duo Lite bottom screen | 1280x960 @2.75 | 427x320 | ~427x272 | STANDARD (its 4:3 squeeze: 1 small card, shortcuts beside the cards' end) |
| AYN Thor bottom screen | 1240x1080 @2.75 | 413x360 | ~413x312 | STANDARD |
| Retroid Pocket Classic window | ~1090x950 @2.0 | ~363x317 | ~363x270 | STANDARD |
| RP6 window (1.4:1, text a step smaller) | ~1330x950 @2.3 | ~443x317 | ~443x270 | STANDARD |
| One-screen side panel (half of 1920x1080) | 960x1080 @~1.9 | 480x540 | ~480x460 under the status bar | STANDARD |
| Side panel on a 4:3 screen | 640x960 | ~320x480 | ~320x430 | TALL (party 2 x 3) |
| SWAP SCREENS: the Thor's top screen | 1920x1080 @2.0 | 640x360 | ~640x312 | WIDE |
| Tablet window | 1920x1200 @1.5 | window inside | - | STANDARD or WIDE by the window |
| Fold BOOK / TABLETOP | the page / the half | measured | - | whatever its room is |

(Screen sizes are the screenshot tests'; the Retroid Pocket Duo's bottom screen is the Duo Lite's 1280x960, the user confirmed.)

The rules: no card cut off, no overlap, nothing past the tab's edge, text never below a font pixel a GBA pixel (the pixel font's
floor), at least one GBA px of the window's `margin` round each card, PARTY's slots and the shortcuts big enough to tap (at least
`NavButtonSize`'s height). `HomeLayoutTest` checks those for every row of the table above and every card count (like
`TouchGeometryTest`).

## Making it your own

Phase 1 is a page, phase 3 is editing on HOME itself.

- **SETTINGS > HOME** (companion SETTINGS, in COMPANION beside TAB BAR; a page, `Page.HOME`, NEW tag `HOME_TAB`): CARDS (each card ON /
  OFF, with a MOVE UP / MOVE DOWN pair on the picked row: the rows TAB BAR already uses), SHORTCUTS (six places, each a pick-list of the
  tabs and actions, or EMPTY), RESET TO DEFAULT. A card this screen can't fit says so in its grey line. PARTY and MAP can be turned off
  too: the small cards then take their room.
- Prefs: `homeCards` (the ON cards in order, ids like `companion_tabs`), `homeShortcuts` (six ids or empty), `homeTab` (the chip).
  Shared by every game like the tab bar; `PerGameSettings.KEYS` gets them later, so SETTINGS FOR: THIS GAME can keep its own HOME.
- Into the SETTINGS FILE like any pref; `SETTING_KEYWORDS` gets "HOME" words ("dashboard", "overview", "shortcuts", "widgets").
- Phase 3: an EDIT button on HOME: cards and tiles get a frame, a long press (or A on the D-pad) picks one up, the D-pad or a drag moves
  it, A / a drop puts it down, a + tile adds a hidden card. What you see is the screen you're on.

## Every game

HOME shows only what the running game has, so it needs no per-game code: GBA (retail, hacks, BEST EFFORT with its parts off: the cards
for those parts go), Game Boy (its windows and town map; Gen 1's badges from `SaveProgress`), DS (Gen 4 / 5 town maps, Black's
figure), 3DS games (`N3DS_TABS`: PARTY + BAG + shortcuts, the game's smooth skin), PC fan games (PARTY, BAG, MAP when `PcTownMap` has
the region). LOADING / WAITING / NOT SUPPORTED / BLOCKED apply as on PARTY (HOME joins `WAITING_TABS`).

## The D-pad and the one-screen window

Every card and tile is a stop with its focus ring (`gbaFocusRing`); the party slots are each a stop; the first slot is the start
(`focusStart`). UP / DOWN / LEFT / RIGHT go by place on the screen (the layout says each stop's neighbours: `focusProperties`, the
way MAP's buttons do). L / R step the tabs as now. In the window, BACK from a tab HOME opened returns to HOME (like MORE's `backTab`).

## Building it

New files: `companion/HomeSettings.kt` (cards, shortcuts, defaults, pure), `companion/ui/HomeLayout.kt` (the shapes and the packing,
pure, tested), `companion/ui/HomeScreen.kt` (the cards). Reused: `HudMiniMap` (made internal, its map read moved out of FieldHud),
`PartyGrid`'s slot or a smaller one built from `GbaPartySlot` / `partyPaletteFor`, `GameStatusBar`'s money / clock / `BatteryIndicator`,
`TabIcons`, `rememberWildHere`, the guide's boss list, `Nuzlocke.run`, `CompanionAchievements.state`. In `CompanionScreen`: "HOME"
in the tab list before the bar, the chip, its pane, `companionTabLabel`.

### Phase 1 (one change)

1. `HomeLayout` + `HomeLayoutTest` over the size table.
2. HOME chip, tab, opening on it, TAB BAR's HOME row; cards PARTY, MAP, TRAINER under a status bar (money, clock, battery); shortcuts to tabs only.
3. SETTINGS > HOME page, prefs, NEW tag, eight translations of every new word (TrCompanion / TrSettings), keywords.
4. Manual: the companion tabs page's HOME section + SETTINGS > HOME, a `Shot` in `DocsShots` (ui-preview `docsShots`), invented
   creatures only.
5. Renders (below), then on the Thor and the RG DS.

### Phase 2

NEXT BOSS, HERE, BAG, NUZLOCKE, ACHIEVEMENTS, HUNT cards; action shortcuts through `HomeActions` (PokeDaisyActivity, the QUICK
MENU's code path, so a tile and the QUICK MENU never disagree).

### Phase 3

Editing on HOME itself; per-game HOME; then the 3DS / Linux companion (`3ds/core/`, C, 320x240 = COMPACT) as its own change.

## Looking at it

Each size gets a render before it's called done (UI work's rule): Paparazzi `CompanionScreenshotTest.home*` (FireRed, Emerald,
Yellow, Crystal, Platinum, Black, a hack, X; empty party; Nuzlocke on; signed in; SPOILERS on HIDE), which `DuoLiteCompanionScreenshotTest`,
`RgDsCompanionScreenshotTest`, `WideCompanionScreenshotTest` and `DarkCompanionScreenshotTest` re-run by themselves; and a HOME case in
`WindowRp6ScreenshotTest`, `WindowRpcScreenshotTest`, `SidePanelScreenshotTest`, `SidePanel43ScreenshotTest`, `PortraitScreenshotTest`
(default, smallest, full) and `LocalizedScreenshotTest`. ui-preview: `home*` shots, `-PcompanionW/-PcompanionH/-PcompanionDensity` for
each row, `-Pview=MODERN`, `-Pdark=true`. Each class is run alone (one class is not a full run).

## Decided

1. HOME: the first tab for everyone, an icon chip counted in the five, hideable in TAB BAR.
2. No feature flag: it ships in 1.7.0, with the `settingsPlus` / `modernLook` batches, now on by default.
3. The Retroid Pocket Duo's bottom screen is 1280x960.
4. Shortcuts open tabs in phase 1; the QUICK MENU's actions come in phase 2.
