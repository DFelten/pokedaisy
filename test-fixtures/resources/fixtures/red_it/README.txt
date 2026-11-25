captured: 2026-11-25 (headless mgba_dump, by hand; Game Boy: wram.bin = C000-DFFF, hram.bin = FF80-FFFE)
rom: Pokemon - Versione Rossa (Italy) (SGB Enhanced).gb (~/Downloads/Game ROMs & Emulation/gbc/, sha1 65b97cf8...)
  - run as a Super Game Boy, like English Red.
save: ~/Documents/RetroArch/saves/mGBA/Pokemon - Versione Rossa (Italy) (SGB Enhanced).sav - no save of its own existed: the red_de fixture's
  save (the red fixture's game: Nelson, 8 badges, $999999, six Lv100s, 17 bag items) with this cart's own map header for map
  0x59 (at 0x2613) and tileset 6's header (at 0x27D7) written in and its checksum (0x3523) recomputed: the save keeps the cart's
  ROM pointers there, German Red's pointed at the wrong banks.
boot: native-capture/boot/red.txt: on the field in Vermilion City's Pokemon Center - wCurMap (0xD363) 0x59, wYCoord 5,
  wXCoord 3; wPartyCount (0xD168) 6.
