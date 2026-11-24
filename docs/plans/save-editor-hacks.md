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

- **CFRU (Unbound, Radical Red, Amethyst, Sword and Shield, GS Chronicles)**: a save model of its own. From CFRU's public
  `src/save.c` / `include/new/pokemon_storage_system.h` (Skeli789/Complete-Fire-Red-Upgrade), matching what the player's
  Unbound and Radical Red saves show:
  - Chunks of 0xFF0 bytes (`SECTOR_DATA_SIZE`), the footer where retail's is (id 0xFF4, checksum 0xFF6, signature 0xFF8,
    counter 0xFFC); the checksum sums the chunk's size only. Signature 0x08012025, or the hack's own (Unbound 0x01121999,
    `CUSTOM_FILE_SIGNATURE`).
  - `gSaveSectionOffsets`: id 0 SaveBlock2 0xF24; ids 1-4 SaveBlock1 at 0, 0xFF0, 0x1FE0, 0x2FD0 (0xFF0, 0xFF0, 0xFF0,
    0xD98); ids 5-12 PokemonStorage 8 x 0xFF0, id 13 storage +0x7F80, 0x450.
  - The "parasite" (RAM 0x0203B174, 0xEC4 bytes, not checksummed): 0xCC in sector id 0's tail (from 0xF24), 0x258 in id
    4's (from 0xD98), 0xBA0 in id 13's (from 0x450), in that order.
  - Sectors 30 and 31 (fixed, outside both slots, file 0x1E000 / 0x1F000): RAM 0x0203C038 on, 0xFF0 each.
  - Expanded flags 0x900-0x18FF and vars 0x5000-0x51FF (`gExpandedFlags` / `gExpandedVars`; their RAM isn't in save.c).
  - PC: boxes 1-19 in PokemonStorage (RAM 0x02029314: current box, then 58-byte records); boxes 20-25 at 0x0203CB44
    (+30 x 58, +60 x 58: inside sector 30 / 31's RAM), 0x02027434 (+30 x 58) and 0x02024638 (both inside the static save
    blocks' RAM in FireRed rev 0: SaveBlock1 0x0202552C, SaveBlock2 0x02024588) - checked with the game's SendMonToPC
    (`unbound_boxes` fixture). The record is `struct CompressedPokemon`, packed, plaintext: personality 0x00, otId 0x04,
    nickname 0x08, language 0x12, sanity 0x13, OT name 0x14, markings 0x1B, species 0x1C, item 0x1E, exp 0x20, PP bonuses
    0x24, friendship 0x25, ball 0x26, four 10-bit moves 0x27 (5 bytes), EVs 0x2C, pokérus 0x32, met location 0x33, met
    info 0x34, IVs word 0x36 (vanilla's bits; no PP: the game works it out from gBattleMoves). The companion already reads
    it (`BoxRecordFormat.CFRU`, `decodeBoxMon`).
  - Still to map: the bag (CFRU's own pockets and sizes), the Pokédex flags (999+ species), where TMs 51+ sit as items.
  - Rules: CFRU's tables are configs with `EvoScheme.CFRU` (16 evolutions per species), its own learnset shape and move ids
    past 511; `RomGen3Rules` needs those shapes.
  The player's Unbound and Radical Red saves have nothing in the PC: deposit some headless (the game's SendMonToPC, as
  `unbound_boxes`), save in game (START > SAVE), and check every box's place in the file before writing anything.
- **pokeemerald-expansion hacks (Lazarus, Seaglass, SoulGold, Heart and Soul, Imperium, TMT2, Elite Redux, Wishes, ...)**:
  their party records differ per build (language + flags at +0x12, the Mint nature bits, 21-bit EXP in 1.10+), species
  ids past 1000 and their tables are expansion's (gSpeciesInfo, u32 TM lists). Each needs its record layout in
  `Gen3BoxMon` and a rules reader for gSpeciesInfo. Probably one per expansion version.
- **Clover**: FireRed's code under an Emerald header; its tables follow fine (`InPlaceBase.FIRERED_REV0`), but its bag
  moved (gBagPockets 0x0203988C -> 0x02026DC0: Items 190, Key 30, Balls 26, TMs 58, Berries 52; `native-capture/boot/clover.txt`)
  and there's no save of its own to check the rest of SaveBlock1 against: play one headless and save in game first.
- **Let's Go**: 987 species ids, its Pokédex flags out of the save blocks (EWRAM 0x0203C0C8 / 0x0203C133 in RAM): where they
  land in the file isn't mapped.
- **Elysium (Parts A / B)**: its SaveBlock1 sector 1 is checksummed over 0xFE8+ bytes (data in the sector's tail, past the
  0xF80 the format has) and its other chunks are other sizes: a layout of its own to map (its tables follow fine).
- **Emerald Rogue, Quetzal, R.O.W.E., Monomyth, Mariomon**: their own party structs (the BOXES tab's notes in CLAUDE.md);
  not looked at for the editor.
