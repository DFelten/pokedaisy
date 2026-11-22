---
title: PC fan games
summary: How to play fan games made for computers with Pokémon Essentials, where their files go, and how your progress is kept.
section: player
order: 21
keywords: [pc games, fan games, essentials, rpg maker, mkxp, mkxp-z, joiplay, game.ini, computer games]
---

> DRAFT, not published. PC fan games are a prototype behind `FeatureFlags.pcGames` (off by default), so this page
> lives in `docs/manual-drafts/` and the manual doesn't list it. When the flag goes: move this file into `docs/manual/`,
> set `order` to fit the Playing section, add its search words to `synonyms.yml`, name the TOOLS card in "Games, tools
> and settings", give it a NEW tag and add a `Shot` to `ui-preview/src/render/DocsShots.kt`. Write in how players get
> the core then: for now it has to be built by hand (`native-capture/pc_probe/README.md`), which no player should need.
> Delete this note when it moves.

Some fan games are made for computers rather than for a console. Most of them are built with Pokémon Essentials, a kit
for making these games. PokeDaisy can play the ones made with Essentials version 19 or newer.

## What you need

- The game's folder, as its makers ship it. It holds a file called `Game.ini` next to folders such as `Data`,
  `Graphics` and `Audio`.
- The game core, a file called `mkxp-z_libretro_android.so`.

## Adding a game

1. Connect your device to a computer, or use a file manager on the device.
2. Open `Android/data/com.pokedaisy.app/files/pc/` on the device.
3. Put the core in the `cores` folder.
4. Put the game's whole folder in the `games` folder. One folder per game.
5. Open PokeDaisy and go to **TOOLS**. Each game you added has a card there. Tap it to play.

The first start of a game takes a while, as the game gets its own code ready. After that it starts much faster.

## Playing

- The buttons and the stick work as in the game itself: A to pick, B to go back.
- On a screen with no controller, the on-screen controls show, the same ones (and the same `TOUCH CONTROLS` settings)
  as in your other games, with X and Y too.
- Press BACK twice to close the game.
- With a second screen, the companion is there: your team (tap one for its summary, its moves and its stats), your
  bag and the MAP, every name and type as the game has them. The MAP is the game's own Town Map with where you are on it:
  tap a spot to see what it's called, or pick a place from `PLACES`. In a battle the BATTLE tab joins them: both sides' Pokémon, how well
  each move works on the other side, what the foe is weak to, and a trainer's whole team. When the game asks what to
  do, the companion's buttons answer it: FIGHT and a move, RUN, or POKéMON to pick who switches in. BAG opens the
  game's own bag.

## Your progress

- Save in the game's own menu, as you would on a computer. The game keeps its saves in `pc/saves/`.
- Before a game opens, PokeDaisy copies its saves to `pc/saves/pokedaisy-backups/`. The newest 10 copies are kept.
- When you leave the game, PokeDaisy remembers where you were. With `RESUME GAMES` on, the next start puts you back
  there.
- If the game's saves changed since you left (you saved in another copy of the game, or copied a save in), the game
  starts from its own save instead, so older progress can't come back over newer.
- If you quit from the game's own menu, it starts from its title next time.

## What doesn't work yet

- The companion's GUIDE and its settings.
- The MAP only shows once the game's Town Map has a spot for where you are.
- Double battles: their picks stay in the game's own menu.
- Games made with older versions of Essentials.
