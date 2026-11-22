# PC fan games (Pokemon Essentials): feasibility spike

Working notes, 2026-11-17: whether and how, then the first spike's results (Linux, "Spike results" below). Not player-facing (no manual / NEW tags).

## Verdict

**Feasible, and less work than feared, because a libretro core of mkxp-z already exists with save states.** The
best route is the same one the Linux port already takes for GBA: load a libretro core. The Linux host is the place to
prototype it. The companion would get its data from a small Ruby script we load into the game, not from memory
addresses. The real cost is per game: every big fan game is its own fork of Essentials, so each needs its own adapter,
like the ROM hacks do today.

## What these games are

Pokemon Essentials is a starter kit for RPG Maker XP (RGSS1), written in Ruby. A game is a folder holding `Game.exe`,
`Game.ini`, `Data/Scripts.rxdata` (Ruby, Marshal-packed) or `Data/Scripts/*.rb`, compiled data (`Data/*.dat`,
`*.rxdata`), PBS text files, `Graphics/`, `Audio/`, sometimes all packed into an encrypted `Game.rgssad`.

Essentials versions that matter (Maruno17/pokemon-essentials releases page, read 2026-11-17):

| Version | Released | Runtime |
|---|---|---|
| up to v18 | before 2021 | stock RGSS1 (Ruby 1.8), Windows-only, Win32API everywhere |
| v19 | 2021-04-27 | "Added mkxp-z v2.1.1 and upgraded to Ruby 3.0", "Removed all references to Win32API" |
| v19.1 | 2021-05-22 | mkxp-z 2.2.0 |
| v20, v20.1, v21, v21.1 | 2022-05 to 2023-07 | mkxp-z (latest release v21.1) |

So **v19 and newer are mkxp-z games already**, with no Windows calls. Older ones need Win32API stubs (below).

## Runtime options

| Option | License | Android | Save states | Notes |
|---|---|---|---|---|
| **mkxp-z libretro core** (white-axe/mkxp-z, branch `libretro`, last commit 2026-06-30) | GPLv2+ | yes: `Makefile.libretro` has `android-arm`, `android-arm64`, `android-x86(_64)`, plus iOS, Switch, Vita, miyoo, emscripten | **yes** (`retro_serialize`, `savestate_features = "deterministic"`) | Ruby 3.3.10 built for WASI, then wasm2c'd and run in a sandbox (`binding-sandbox/`). That's how states work: the whole Ruby heap is one linear memory. Listed in RetroArch's core info (`mkxp-z_libretro.info`) and docs. |
| mkxp-z native (mkxp-z/mkxp-z `dev`) | GPLv2+ (GPLv3 binaries with OpenSSL on) | not supported upstream (README: Windows, Linux x86 / ARM / POWER, macOS) | no | MRI Ruby 3.1.3 native, Win32API as an FFI. What the games themselves ship. |
| JoiPlay + RPG Maker plugin | closed source | yes | no | What players use today. Play Store build lacks mkxp-z, so v19+ games need the side-loaded one. Can't be embedded. |
| xperia64/android-mkxp | GPL (old mkxp) | yes | no | Old, original mkxp; not mkxp-z. Not a base. |
| Empo (iOS) | ? | iOS only | ? | Wraps mkxp-z in a SwiftUI app with a touch overlay: proof that embedding it in a mobile app works. |

**Pick: the libretro core.** GPLv2+ is compatible with our GPLv3. It already builds for Android arm64 and Linux
aarch64, needs GL 2.0 / GLES 2.0, and gives us save states (so RESUME, STATES, and likely REWIND) without us inventing
anything.

What the libretro core does, from its source and docs (`docs/library/mkxp-z.md` in libretro/docs):

- **Loading**: a `Game.ini`, an `mkxp.json`, an empty `.rxproj` beside them, or the whole game in a `.zip` / `.7z` /
  `.mkxpz` (stored uncompressed, especially `.rgssad` and fonts, or it crawls). Encrypted archives work.
- **Timing**: RPG Maker XP games run at 40 fps, 640x480 max, 4:3, 44.1 kHz. (Essentials changes the frame rate and
  screen size in its own scripts; check what the core reports for a real game.)
- **Rewind / netplay** need the "Threaded Audio" option off.
- **Preload / postload scripts**: Ruby files in `<system>/mkxp-z/Scripts/Preload`, each a core option, run before the
  game's scripts. **This is our hook.** It also ships Ancurio's CC0 `win32_wrap.rb` and other compatibility preloads.
- **Native cheats are Ruby scripts** (`retro_cheat_set` queues code that runs in the game, e.g. on `$game_switches`).
  Possibly a ready-made way for the host to run a command in the game (heal, open PC); check when it runs.
- **Memory**: `retro_get_memory_data(SYSTEM_RAM)` is the WASM linear memory (the Ruby heap). Readable, but object
  layouts move, so it's no good for addresses. Fine for a mailbox (below).
- **Win32API**: the sandbox binding *raises* "Win32API is not supported in libretro builds of mkxp-z". `win32_wrap.rb`
  covers a subset in Ruby (keys and so on). v19+ games don't care; older games may need more stubs.
- Build: two stages. Stage 1 (platform-independent) needs WASI SDK **exactly 30** ("using a different WASI SDK version
  breaks save state compatibility"), Binaryen, ctags, Ruby. Stage 2 is plain Meson + Ninja + CMake per target.
- Saves go to `<save dir>/mkxp-z/Saves/<game title>/`, not next to the game. Our "save files are sacred" rules (backup
  on start, never overwrite from a state without the check) need an equivalent for that folder.

## The games

Confirmed means read on a page or in a repo; the rest is open (rebornevo.com, hackmd.io, codeberg.org and
docs.libretro.com were blocked from this session; raw.githubusercontent.com and github.com worked).

| Game | Essentials | Runtime it ships | Source public? | Android today | Status |
|---|---|---|---|---|---|
| **Infinite Fusion** (game v6.4.5 in the repo) | **19.1.dev** (`Data/Scripts/001_Settings.rb`: `Essentials::VERSION = "19.1.dev"`), confirmed | `Game.exe` + `x64-msvcrt-ruby300.dll` / `ruby310.dll` (mkxp-z's Windows Ruby DLLs), `Game.ini` still names `RGSS100J.dll`. 19.1 needs Ruby 3, so mkxp-z: inference, not stated | **yes**, github.com/infinitefusion/infinitefusion-e18, scripts as `.rb` files | JoiPlay, engine set to mkxp-z | best first target: open scripts, newest base, `$Trainer` global, 40 PC boxes |
| **Reborn** | not confirmed (old, heavily rewritten) | its own mkxp-z build: E18.3 was "the first version" on Mac / Linux without Wine; E19 ships `game-z.exe` | not confirmed | JoiPlay (forum guide exists) | second target; Ruby stdlib work done by its team |
| **Rejuvenation** | not confirmed (shares Reborn's tech) | Reborn's mkxp base, Windows / Linux / Mac builds | not confirmed | JoiPlay | with Reborn |
| **Insurgence** | not confirmed (pre-v19) | stock RGSS `Game.exe` | no | JoiPlay with workarounds (reports disagree) | needs Win32API stubs |
| **Uranium** | not confirmed (pre-v19) | stock RGSS | no | JoiPlay | development over; needs Win32API stubs |

## How it would fit PokeDaisy

### Linux first

`pokedaisy-linux` is already a libretro frontend (`linux/main.c` loads `retro_*` symbols). What it lacks for mkxp-z:

- **Hardware rendering** (`RETRO_ENVIRONMENT_SET_HW_RENDER`, a GL context the core draws into). The host has no
  `hw_render` today; the GBA / GBC cores are software. That's the main new piece.
- Picking a game *folder* (or a zip) instead of a ROM file, and the core's system dir (`mkxp-z/Scripts/Preload`).
- 40 fps pacing (the host paces 58-62 Hz screens, else a timer) and 640x480 instead of 240x160.

### Android second

The app links its cores directly (mGBA, melonDS, DSperate through `pk_nds_core.cpp`), not as libretro `.so`s. Two ways:
build the libretro core for arm64 and host it behind a small libretro shim of our own (`retro_*` calls, a GL context on
the emu thread like `pk_nds_gl.cpp` does for melonDS's GL renderer), or link mkxp-z's sources the melonDS way. The shim
is less work and keeps us on the core's upstream.

### Companion data: a Ruby script, not addresses

A preload script of ours (`pokedaisy_companion.rb`, our own code, GPLv3) that hooks the game's update loop and, a few
times a second, packs what the companion shows:

- party (`$Trainer.party` in v19, `$player.party` in v20+): species, form, level, HP, status, moves + PP, item, nature,
  IVs / EVs, shiny, fusion parts for Infinite Fusion;
- bag (`$PokemonBag` / `$bag`), money, badges, play time, map id + name (`$game_map`), position;
- battle (`$scene` is a `PokeBattle_Scene` / battle object): battlers, foe party, field.

Getting that to the host, cheapest first:

1. **A mailbox in the Ruby heap**: the script keeps one long-lived String starting with a magic header and rewrites
   its bytes; the host finds it once by scanning SYSTEM_RAM (like `FfMenuWatch` finds gMain) and reads it each frame.
   No change to the core. Risk: the GC may move or reallocate it; checkable headless.
2. **A patch to the core** (kept like `third_party/patches/melonds-pokedaisy.patch`): a `PokeDaisy.publish(str)`
   binding that copies into a host buffer. Cleanest; one small C++ file in `binding-sandbox/`.

Actions (heal, PC, repel, FLY to a town) would go the other way: Ruby the host asks the game to run (the cheat queue or
the same patch), calling the game's own methods (`pbHealAll`, `pbPokeCenterPC`...). Same rule as today: the game's own
code, its own checks.

Names, dex text, sprites and maps come **from the player's game folder** (PBS files, `Graphics/Pokemon`,
`Graphics/Pictures/town map`), never bundled. Same rule as ROM art.

### What carries over and what doesn't

| Works | Needs new work | Doesn't apply |
|---|---|---|
| PARTY, BAG, DEX, battle INFO / SUGGESTIONS, HUD, QUICK MENU, STATES / RESUME, FF, the touch pad, status bar, themes / skins | MAP (Essentials' town map data per game), GUIDE > HERE (`encounters.txt`), the save editor (Marshal saves), RetroAchievements (none for these games) | EXPAND (these run at 512x384 or wider already), every ROM-patch option (REUSABLE TMS, INSTANT TEXT, LEVEL CAP, PLAYER LOOK...: would be Ruby rewrites, per game), STEADY FF music (different sound engine), LINK PLAY |

## Risks

- **Per-game adapters.** Essentials globals and classes changed between v18, v19, v20 and v21, and the big games
  rewrote further. Budget one adapter per game, checked on a save from that game.
- **Speed on handhelds.** Ruby in WASM through wasm2c is slower than native MRI. Not measured on a handheld yet (see
  the spike's results). Infinite Fusion is the worst case (huge sprite sets, big bitmaps past Mali's 16k texture limit would fail). Measure on the RP6 / Thor early.
- **The libretro branch lives on a fork**, not mkxp-z's `dev`; its issues go to mkxp-z. Pin a commit, as with melonDS.
- **WASI SDK 30 pinned** for state compatibility: CI needs that exact toolchain, and a core update can break old states.
- **Saves** live in the core's folder; RESUME's "state older than the save" check needs a per-game equivalent.
- **Developers' wishes.** These are community games; ask the big teams (Infinite Fusion, Reborn) before advertising
  support, and keep the BlockedGames mechanism ready. Uranium was taken down by a DMCA notice in 2016.
- **Win32API** in pre-v19 games: `win32_wrap.rb` covers some; the rest is per game.

## Spike results (2026-11-17, Linux x86_64, 4 cores, Mesa llvmpipe: OpenGL on the CPU)

The core built from white-axe/mkxp-z `libretro` (650cb08) + `third_party/patches/mkxp-z-libretro-pokedaisy.patch`,
how in `native-capture/pc_probe/README.md`. Infinite Fusion 6.4.5 (its public repo) in `pc_probe` and in
`pokedaisy-linux` built with `-DPOCKET_PC_GAMES=ON`:

- **It runs.** Boots to the title and into a new game; draws at 512x384 (the core's max is 640x480). The first 120
  frames take ~15 s (the game compiling its scripts), then 1.8-4 ms a frame here. A handheld is still unmeasured.
- **Three things the game needed**, all in our preload `linux/pc_games/pokedaisy_ruby32_compat.rb`:
  - Ruby 3.2 removed `File.exists?` (the core runs Ruby 3.3; the game's own build, 3.0 / 3.1).
  - The core's `Kernel#caller` takes no arguments, though native mkxp-z's takes any. Essentials' `echoln` calls
    `caller(1..1)`. Report upstream.
  - mkxp-z's `HTTPLite` isn't in the libretro build. A stub answers "offline", so the game takes its no-internet path:
    no Mystery Gift, Wonder Trade or sprite downloads.
- **The mailbox works.** `pokedaisy_companion.rb` publishes its JSON (1-6 KB) up to 4 times a second, only when something changed: trainer, money, badges,
  party (species, nickname, level, HP, status, nature, ability, stats, moves + PP, held item, egg / shiny; fusions too,
  e.g. CHARSAUR = B1H4, dex 505), bag pockets, map id / name / position, battle flag, scene. The host finds it once by
  its magic (250 ms over the 400 MB heap here; once per load), then reads in place: `linux/pc_mailbox.c` writes it to
  `DATA/pc-companion.json`.
- **Commands work too.** `retro_cheat_set` runs a line of Ruby in the game: that's how the test gave the party and
  items. It's the channel for HEAL / PC / items later.
- **Save states work with our patch.**
  - Raw size: the game's Ruby heap is 396-414 MB, so states need `mkxp-z_saveStateSize` 512. A state is 512 MB raw:
    280-480 ms to save, 75-260 ms to load in the same session, 0.7-0.9 s into a fresh core. gzip -1 makes it 99 MB in
    3.8 s.
  - Same-session load: refused while the game held a directory stream open (Infinite Fusion does), and the failure
    path then asserted in the core. Our patch closes such streams first, and fixes an index typo in that loop.
  - Fresh core: loading before the first `retro_run` traps in the WASM. After one frame it works, which RESUME must
    respect.
  - Threaded audio off.
  - Checked: money and party back as saved after changing both.
- A frontend must call `retro_unload_game` before `retro_deinit`; without it the core crashed in its audio destructor
  at exit.
- `pokedaisy-linux`: `hw_gl.c` gives the core an offscreen EGL context and FBO, reads each frame back into the frame
  texture the other cores fill (filters, overlay, menus unchanged), and brackets every core call so SDL's GL context
  is current again afterwards. A `Game.ini` / `mkxp.json` / `.mkxpz` opens `mkxp-z_libretro.so`. Our preloads are built
  into the binary and installed into the core's `Scripts/Preload`; states, threaded audio and preload options are set
  by the host. Off by default: `-DPOCKET_PC_GAMES=ON`.

What this changes in the risks:

- **Memory** is the big one now. ~400 MB of heap for Infinite Fusion, a 512 MB state buffer, plus the frame and
  textures: too much for a 2 GB handheld, and states want compression and fewer slots.
- **Ruby version drift** is real but cheap: one preload of shims so far.

## The companion on Linux (2026-11-17)

`pokedaisy-linux -DPOCKET_PC_GAMES=ON` draws PARTY, a Pokemon's summary (moves / stats), BAG and TRAINER over a PC game:

- **The shared pieces, not new ones.** `linux/pc_companion.c` hands the snapshot to `3ds/core`'s own views, as the DS
  companion does (`linux/dsperate/party_view.cpp`): `pd_ui_party_view` with a `pd_party_source`, `pd_ui_summary_page` /
  `_footer` with a `pd_summary_view`, `pd_ui_bag_view` with a `pd_bag_source`. Ids handed to them are places in the
  snapshot (species = party slot + 1, item = bag row + 1), and the sources give the names back. So the look is the
  companion's (the OPTION windows, FireRed's party slots, the bag's pocket tabs), every word the game's own.
- **The game's types.** The snapshot carries its type table (names and matchups by `id_number`), handed to the summary
  as a `pd_text` (`typeNames`, `typeChart`): Infinite Fusion's Fairy and its own types read and match up right
  (GYARADOS: Electric 4x).
- **Icons from the game folder** (`linux/pc_png.c`, a small PNG reader on zlib): the Ruby side asks the game for each
  icon's file (`GameData::Species.icon_filename*`, `GameData::Item.icon_filename`, cached there); a fusion's is built as
  its `createFusionIcon` does (the head's frame, the body's rows from the middle + `FUSION_ICON_SPRITE_OFFSET` down),
  checked pixel for pixel against the same composition done apart. A zipped `.mkxpz` game gets no icons.
- **Natures** go by `id_number`, Gen 3's order in Essentials, so the summary names them with their stat arrows.
- **Buttons**: a lone SELECT tap (the host's MODIFIER, whose tap otherwise goes to the game; Essentials maps nothing to
  SELECT) opens and closes it; the game runs on without input meanwhile. L / R tabs, D-pad (the party grid's 2 or 3
  columns, as `ui_party_grid` picks), A summary / its page / an item's details, X the bag's sort, B back, START closes.
- **Checked** in the real host, headless (`POCKET_KEYS` + test hooks `POCKET_PC_CHEAT="FRAME:ruby"`, run through the
  core's cheats, and `POCKET_PC_COMPANION=1`, open from the start): Infinite Fusion's intro played to the new game, a
  party given, SELECT, the D-pad to a fusion's summary, R1 to the bag and its pockets, SELECT again back to the game.
  `pc-companion-shot SNAPSHOT.json GAME_DIR OUT_DIR [WxH]` draws every page from a saved snapshot (`pc_probe`'s `mbx`).
- **Not yet**: the HUD over the field, MAP / DEX / GUIDE, battle INFO, item icons in the bag, an ability line (the
  shared summary has no field for it), DS and GBA cores' settings / states tabs.

## Next steps

1. Build the core for Android arm64 and Linux aarch64 (`Makefile.libretro`'s platforms) and time Infinite Fusion on the
   RP6 and a TrimUI: boot, ms a frame, heap, state save / load.
2. Report the `caller` arity, the directory-stream load and the load-before-first-frame trap upstream (mkxp-z issues),
   with the patch.
3. States: compress them (zstd), keep a slot count that fits the storage, and back up the core's own save folder
   (`<saves>/mkxp-z/Saves/<title>/`) like every battery save.
4. Reborn / Rejuvenation: confirm their Essentials base and what the compat preload needs for them.

Behind a feature flag (`FeatureFlags.pcGames`, off) when it reaches the app, like co-op and 3DS.

## Sources

- mkxp-z: <https://github.com/mkxp-z/mkxp-z> (README, `meson.build`, `subprojects/ruby-3.1.wrap`)
- libretro core: <https://github.com/white-axe/mkxp-z/tree/libretro> (`Makefile.libretro`, `libretro/README.md`,
  `libretro/Makefile`, `src/core.cpp`, `binding-sandbox/binding-sandbox.cpp`, `scripts/preload/`)
- core info: <https://github.com/libretro/libretro-core-info/blob/master/mkxp-z_libretro.info>; docs:
  <https://github.com/libretro/docs/blob/master/docs/library/mkxp-z.md>
- Essentials releases: <https://github.com/Maruno17/pokemon-essentials/releases>
- Infinite Fusion scripts: <https://github.com/infinitefusion/infinitefusion-e18>
- Reborn on mkxp: <https://www.rebornevo.com/forums/topic/51542-e183-has-been-released-for-all-oses/>,
  <https://www.rebornevo.com/forums/topic/60004-e19-community-open-beta-downloads/>
- JoiPlay and mkxp-z for v19+: <https://www.pokecommunity.com/showthread.php?p=10620794>,
  <https://joiplay.net/forum/d/88-various-pokémon-essentials-issues>
- Empo (mkxp-z on iOS): <https://awesome.ecosyste.ms/projects/github.com%2Fmateo-m%2Fempo-app>
