captured: 2026-11-22 (headless mgba_dump, native-capture/boot/silver_de_boxes.txt; Game Boy Color: wram.bin = C000-DFFF as the
  bus had it, hram.bin = FF80-FFFE, wramx.bin = all 8 WRAM banks, sram.bin = the cart's battery RAM, 4 banks of 0x2000 -
  where Gold and Silver keep their PC boxes)
rom: Pokemon - Silberne Edition (Germany) (SGB Enhanced) (GB Compatible) (~/Downloads/Game ROMs & Emulation/gbc/)
save: ~/Documents/RetroArch/saves/mGBA/Pokemon - Goldene Edition (Germany) (SGB Enhanced) (GB Compatible).sav (the gold
  fixture's game; English's save byte for byte, so it loads in all four carts), copied fresh for the run: mgba_dump writes
  the save back.
what was done, all by the game's own functions far-called from HandleMap (gbcallat, the app's way), no box bytes poked:
  - four Pokémon added to the party with TryAddMonToParty, then sent to BOX 1 / BOX 8 with SendGetMonIntoFromBox and
    RemoveMonFromPartyOrBox, switching wCurBox through SaveBox / LoadBox: BOX 1 = GEODUDE Lv8, MAGIKARP Lv5 (SRAM bank 2),
    BOX 8 = ZUBAT Lv6, HOOTHOOT Lv4 (bank 3);
  - BOX 8 renamed TEAM in wBoxNames (WRAM bank 1, D8FE);
  - BOX 3 left as the current box (wCurBox 2) with a PIDGEY Lv3 in it: sBox (bank 1, AD6C) holds it.
party after it: CYNDAQUIL, PIDGEY. A box change (SaveBox, wCurBox, LoadBox, SaveGameData) on this sram was saved and
  rebooted headless on the English and German Gold carts: the new box came back as the current one.
