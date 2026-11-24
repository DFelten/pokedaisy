# Playing without spoilers: plan

Status: phase 1 built (2026-11-23), behind `FlaggedFeatures.spoilerFree` (`FeatureFlags.spoilerFree`: `-Ppokedaisy.spoilerFree=true`
or marker `spoiler-free`), for 1.7.0. The user's idea: play with PokeDaisy but see in the companion only what the game itself has shown you.

## The rule

With SPOILERS on HIDE, the companion shows what the game has shown you, and anything past that as "???":

- a Pokémon you haven't seen (the save's Pokédex seen marks, plus the companion's own list of what it read: party, boxes, every
  battle's foes - for games whose Pokédex isn't read);
- moves your Pokémon will learn later, the evolutions you haven't seen;
- a trainer's Pokémon still in their balls, and what the game never shows of a foe (ability, held item, nature, stats, moves);
- the bosses after the next one.

A tap on a "???" shows that one thing, and only it stays shown (`Spoilers.reveal`, kept per game). HIDE never takes away what the game
itself lets you see (the town map's names, your own Pokémon's numbers, the bag) or what a helper does (PC anywhere, FLY: their own
switches). Type matchups stay (the user's call: game knowledge, not a spoiler). Your own IVs / EVs stay; a foe's numbers go.

## Where it's kept

`Prefs.spoilersFor(romKey)`: unanswered / SHOW / HIDE per ROM CRC32 (a tenth FireRed run wants spoilers, a new hack doesn't), and with
it the reveals and the companion's own seen list (`spoiler_shown_<crc>`, `spoiler_seen_<crc>`). They go in the SETTINGS FILE like any pref.
The row is companion SETTINGS > COMPANION > SPOILERS (SHOW / HIDE), NEW tag `SPOILER_FREE`; off the flag it isn't there and everything
reads SHOW.

## When it's asked

- A new or early save (party of one or none, at most one caught): the first time the player is in the game, a card like the tips'
  (`SpoilerAskHost`): on the companion's screen with two screens, over the game with one. NO SPOILERS / SHOW EVERYTHING; the bar
  running out leaves it unanswered, asked again on the next start while the save is still early.
- A save further on that never answered: once, the first time DEX or GUIDE opens (not BATTLE: it opens by itself as a battle starts), the same card on the companion.
- Never in first-time setup or the library (the choice is per game). Unanswered = SHOW: nothing changes for players who never see it.

## What each screen does (phase 1)

| Where | HIDE |
|---|---|
| DEX list | an unseen entry is `-----`, no picture; its page says so with SHOW IT. The NAME filter's letters only from seen entries |
| DEX EVOLVE / GUIDE EVOLUTIONS | an unseen family member is ??? (no picture, no jump) |
| DEX MOVES | level-up moves past the level of your best one of it are ??? (a tap shows one) |
| Summary LEARN | the next level's moves are "a new move" until tapped |
| GUIDE NEXT BOSS | only the next boss (the ones after are a count) |
| GUIDE HERE / HUD WILD / HUNT's targets | seen = the knowledge check above (games without Pokédex flags too) |
| GUIDE HERE's people | a gift / trade / egg of an unseen Pokémon doesn't name it until tapped |
| BATTLE | an unsent foe can't be opened; FOE DETAILS keeps the matchups, drops ability / item / nature / stats / moves |

## Next (phase 2, not built)

- The MAP's PLACES and the DEX's AREA: only places visited (the games' town maps name all, so kept for now).
- NEXT BOSS's level line, GUIDE's WHERE IS for places not reached yet.
- The 3DS / Linux / iOS companions.
