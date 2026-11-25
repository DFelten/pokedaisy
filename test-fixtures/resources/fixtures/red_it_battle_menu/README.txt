captured: 2026-11-25 (headless mgba_dump, by hand; Game Boy: wram.bin = C000-DFFF, hram.bin = FF80-FFFE)
rom: Pokemon - Versione Rossa (Italy) (SGB Enhanced).gb (~/Downloads/Game ROMs & Emulation/gbc/, sha1 65b97cf8...)
  - run as a Super Game Boy, like English Red.
save: as the red_it fixture.
boot: as the red_it fixture, then a wild battle poked in on the field (wCurEnemyLevel 0xD12C = 12, wCurOpponent 0xD05E = 0xB0,
  CHARMANDER's internal id), wait 600, A, wait 300: the battle menu (LOTTA / PkMn / STRUM. / FUGA).
  Then RIGHT, wait 30: the cursor on PkMn (wTopMenuItemY 14, X 14, wCurrentMenuItem 0, ▶ at (14, 14); the left column is at X 7).
