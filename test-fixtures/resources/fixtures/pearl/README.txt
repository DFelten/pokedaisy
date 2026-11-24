captured: 2026-11-24 (headless native-capture/nds_dump, by hand; mainram.bin = the DS's 4 MB main RAM at 0x02000000,
  zeroed but for what Gen4Reader reads: 0x021C5000-0x021C5FFF (sSaveDataPtr 0x021C59C8) and the SaveData at
  0x0226D2B0 (its body +0x214, 0xC100 bytes)) - the rest is unrelated memory, and zeros keep the file tiny in git.
rom: Pokemon - Pearl Version (USA) (Rev 5) (~/Downloads/Game ROMs & Emulation/nds/, sha1 99083bf1..., the build
  pret/pokediamond makes)
save: Diamond's: the new game played headless to the player's room (~/Downloads/Game ROMs & Emulation/nds/Pokemon - Diamond Version
  (USA) (Rev 5).sav, player AA), edited by the save editor's own test (Gen4SaveTest.aRealDiamondSave, DIAMOND_EDIT_OUT):
  a Pokemon from the player's HeartGold traded into its party, money 123456, POTION x5. The game's own trainer card and
  bag show those; its X menu has no POKEMON entry yet (the starter's flag isn't set on this save).
boot: from that .sav: wait 250 + START (held 3), three times, wait 200, A (held 3), wait 600: in TWINLEAF TOWN's
  player house 2F (map header 415) at (4, 6).
