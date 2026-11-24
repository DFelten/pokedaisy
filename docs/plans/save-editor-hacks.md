# Save editor and trades for ROM hacks

Branch `feature/save-editor-hacks` (2026-11-25). The SAVE EDITOR / TRADE SAVES / MISSING / COLLECTION read a save with
the rules of the game it belongs to, not always retail's.

## How it works

- `Gen3Rules` (shared `companion/data/save/Gen3Rules.kt`) is everything the editor asks of a game: names, base stats,
  abilities, gender ratios, growth rates, learnsets, TM / HM moves, which items go in which pocket, the regional dex, the
  egg's species. Retail's come from the generated tables (`RetailGen3Rules` over `Gen3GameData` / `Gen3Legal`); a hack's
  are read from its ROM (`RomGen3Rules` over `Gen3RomTables`).
- The tables a hack's rules need come from its `NativeConfig` (`gen3RomTables`: the dex's evolutions / TM tables, the
  guide's learnsets, `itemDescs`' gItems) plus the names and gBattleMoves, which the configs don't carry: found by
  `Gen3RomScan` (a 32 MB ROM) or, for a hack edited in place, retail's own addresses (`InPlaceBase`).
- A save file carries its rules (`Gen3SaveFile.rules`, `Gen3SaveData.rules`): `Gen3SaveReader.open(path, format,
  rules)`. A hack edited in place gets its tables where retail's code points (`InPlaceBase.pools`: the Game Freak header's
  and the literal pools' words, a majority vote; a rebuild's words disagree and it keeps retail's). `Gen3SaveReader.formatFor` picks a hack's `Gen3SaveFormat` by the ROM's SHA-1 (`HACKS`); `rulesFor` gives a
  hack its rules, a non-retail English FireRed / Emerald cart (a randomizer's output, an unknown in-place edit) its own
  ROM's at its base cart's addresses with retail's family, and a retail dump retail's tables.
- `Gen3Rules.family`: trades only link two saves of one family (`Gen3Trade.linkBlocker`). Retail and randomized carts are
  `RETAIL`; each hack is its own. MISSING follows the hack's own dex; Pal Park takes only `RETAIL` saves.

## Done

| Hack | Format | Checked |
|---|---|---|
| Emerald Legacy v1.1.4 | `EMERALD_LEGACY` (its own SaveBlock1: 120-slot ITEMS, flags 0x13D8, vars 0x1510) | the save reads; an edited save (money, a Lv30 lead, balls) booted headless in mgba_dump and the game showed it |
| Emerald Kaizo v2.0 | `EMERALD_KAIZO` (retail's layout) | by hash; no save of its own (retail Emerald's boots in it) |
| Glazed 9.2.0 | `GLAZED` | the save reads (CHIMCHAR Lv6) |
| Orange Islands (both builds) | `ORANGE_ISLANDS` | the save reads (PIKACHU Lv10) |
| Ultra Violet v1.22 | `ULTRA_VIOLET` | the save reads (CHARMANDER Lv6) |
| Ash Gray (both builds) | `ASH_GRAY` | by hash; no save of its own (retail FireRed's boots in it) |
| Recharged Emerald v2.2.5 / v2.2.8 / Rebalanced v2.2.8 | `RECHARGED_EMERALD_*` (its own SaveBlock1, a Medicine pocket, no seen copies; Rebalanced's 4-byte lead, `blockLead`), its 48-byte gItems with no id (`ItemLayout.RECHARGED`) | all three saves read; edited ones (money, a level, a Medicine item) booted headless in mgba_dump and the game's RAM held each edit |
| FireRed Rocket Edition | retail FireRed's layout; its moved moves / ability names found by following rev 0's pointers (`InPlaceBase.follow`) | retail FireRed's save (which it boots) reads with its own names and moves; an edited one booted headless with the money and the item |
| Randomized FireRed / Emerald | retail's, rules from the ROM | over 100 species' learnsets differ from retail's in a UPR output |

`HackSaveTest` (shared androidUnitTest) covers these with the player's ROMs and saves (`-DromDir`), skipping quietly
without them. `HACK_SAVE_OUT=<file>` writes the edited Legacy save to boot.

## Not yet, and why

- **CFRU (Unbound, Radical Red, Amethyst, Sword and Shield, GS Chronicles, Odyssey / Gaia)**: the save isn't retail's.
  Unbound signs its sectors 0x01121999, keeps SaveBlock1 in 0xFF0-byte chunks per sector (sector 1 off 0 = SB1
  0x0202552C, sector 2 off 0 = SB1 + 0xFF0), and puts data no checksum covers in the sectors' tails; Radical Red checks
  0xFF0 / 0xFF4 bytes. The PC is CFRU's 58-byte compressed records (RAM 0x02029314; boxes 20-25 at 0x0203CB44, +30 x 58,
  +60 x 58, 0x02027434 (+30 x 58), 0x02024638), so where each RAM range lands in the file has to be mapped first. The
  player's Unbound and Radical Red saves have empty PCs: a save with boxed Pokémon is needed to check the mapping. Next
  step: deposit Pokémon headless (the game's SendMonToPC, like the `unbound_boxes` fixture) and save in game, then diff.
- **pokeemerald-expansion hacks (Lazarus, Seaglass, SoulGold, Heart and Soul, Imperium, TMT2, Elite Redux, Wishes, ...)**:
  their party records differ per build (language + flags at +0x12, the Mint nature bits, 21-bit EXP in 1.10+), species
  ids past 1000 and their tables are expansion's (gSpeciesInfo, u32 TM lists). Each needs its record layout in
  `Gen3BoxMon` and a rules reader for gSpeciesInfo. Probably one per expansion version.
- **Clover**: no species name table found by the scan (FireRed under an Emerald header; names elsewhere).
- **Let's Go**: 987 species ids, its Pokédex flags out of the save blocks (EWRAM 0x0203C0C8 / 0x0203C133 in RAM): where they
  land in the file isn't mapped.
- **Elysium (Parts A / B)**: its SaveBlock1 sector 1 is checksummed over 0xFE8+ bytes (data in the sector's tail, past the
  0xF80 the format has) and its other chunks are other sizes: a layout of its own to map (its tables follow fine).
- **Emerald Rogue, Quetzal, R.O.W.E., Monomyth, Mariomon**: their own party structs (the BOXES tab's notes in CLAUDE.md);
  not looked at for the editor.
