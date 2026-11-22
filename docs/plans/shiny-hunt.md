# Shiny hunt counter: research and plan

Status: phase 1 built (2026-11-23), behind `FlaggedFeatures.shinyHunt` (`FeatureFlags.shinyHunt`: `-Ppokedaisy.shinyHunt=true`
or marker `shiny-hunt`), for 1.7.0. Asked for by players: a Discord thread ("Shiny Hunt Tracker": count resets when spamming
LOAD STATE, an alert, LOAD STATE held over a shiny) and r/PokeDaisy's "Shiny Counter" mock-up (target sprite, count on both
screens, methods, step size, saved hunts, a tap-to-count overlay on one screen).

## What hunters want (research, 2026-11-23)

From counter apps (Shiny Hunting Tracker on PokeTools, Nevapps Shiny Counter, PokeCount, Shiny Chain, Auto Shiny Counter),
PokeCommunity / Smogon threads and the PokeBot Gen3 project, most wanted first:

1. A +1 that can't be wiped by mistake: -1 / undo, a guarded reset, the count editable.
2. Odds per game and method, and the chance of having met one by now, 1 - (1 - 1/odds)^n, with milestones (50 / 75 / 90 / 99%:
   5,678 / 11,356 / 18,862 / 37,724 at 1 in 8192). "Over odds" is normal (63% at 8192).
3. Several hunts, each with a target species and its picture.
4. A log of finished hunts (count, date, phases).
5. Phases: an off-target shiny on the way, the count going on.
6. Time and pace: play time, encounters per hour.
7. Counting by itself (apps charge for it; we read it from RAM for free).
8. Bigger steps (+5 / +10 / +50), hotkeys.
9. Stream output (OBS text files), notifications.

Odds: Gen 2-5 1 in 8192; Gen 4 Masuda ~1 in 1638, Gen 5 Masuda ~1 in 1365, B2W2 Shiny Charm ~1 in 2731, both 1 in 1024; Gen 4
Poke Radar chain of 40 ~1 in 200. Gen 1 has no shinies. Shiny-locked: Victini, Reshiram / Zekrom in Gen 5, most events.

Resets from states: Gen 3's RNG moves every frame until the battle, so a later press gives another Pokémon; Emerald seeds from 0 at
boot, so a reset timed the same repeats one. Gen 4 / 5 reseed at boot and move on events: a reload mostly replays the same result.
Hence: a RESETS try counts once the new Pokémon is read, and the same personality as the last try is a repeat (not counted, a hint).

## Built (phase 1)

- `HuntLog` / `Hunt` / `HuntCounter` / `HuntOdds` (`shared/.../data/HuntLog.kt`, `HuntLogTest`): the log per ROM in Prefs
  (`hunts_<crc>`, so the SETTINGS FILE carries it), methods ENCOUNTERS (wild foes, doubles count 2), RESETS (the first Pokémon read
  after a load / restart, a repeat not counted), EGGS (hatches), BY HAND; shinies from a wild foe, a hatch, a RESETS gift; target
  (0 = any) vs phases; play time from sample gaps (capped 5 s).
- `ShinyHunts` (`shared-ui/.../companion/ShinyHunts.kt`): Compose state of the running ROM's log, fed by `GameRuntime`'s sampler;
  `loadHeld()`: LOAD STATE (`EmulatorEngine`'s pending load) and RESTART GAME refused while a shiny's battle lasts and 10 s after it
  showed (also SHINY ALERT's shiny, no hunt going).
- The HUNT tab (`HuntScreen.kt`, under MORE, on the bar while a hunt goes; not on Gen 1): the counter (target icon, big count, +1 / -1,
  odds and chance by now), TARGET (this battle's foes, the map's wild Pokémon, ANY SHINY), METHOD, ODDS, COUNT (+10 ... -100), BY NOW,
  the next milestone, TIME, PER HOUR, PHASES, FOUND IT / PUT ON HOLD / NEW HUNT, EARLIER HUNTS (continue / delete).
- The target drawn as it looks shiny: `PokedexSource.huntSprite` (Gen 2's shiny pair, the GBA games' gMonShinyPaletteTable found
  right after gMonPaletteTable by its tags, expansion's shinyPalette; the DS games and the rest in their usual colours), its own cache
  so the PARTY tab's pictures don't change while the flag is off (`HuntSpriteTest`); the icons by `shinyIconAsset`.
- A RESETS hunt's LOAD SLOT n / RESTART GAME buttons under the count (the reset from the companion, guarded like the hotkey's).
- The HUD's count (`FieldHudPart.HUNT`, beside the REPEL steps): a tap opens the companion, or counts one BY HAND.
- The alert: an active hunt's shiny stops FF and turbo, plays the fanfare, puts a line on the game screen (SHINY ALERT's own stays
  for games without a hunt going).

## Next (phase 2, not built)

- A HUNT +1 hotkey and a QUICK MENU tile; +N step per hunt.
- The count on the BATTLE tab and over a one-screen battle (the HUD only shows on the field).
- Poke Radar chain length read from RAM (DPPt / HG / SS) as a method.
- Shiny-locked species refused as targets.
- Every game's hunts in one list (the library's TOOLS), export as text / JSON, a stream text file.
- Gen 2 breeding odds (1 in 64 with a shiny parent) as an ODDS choice.
- 3DS / Linux / iOS ports.
