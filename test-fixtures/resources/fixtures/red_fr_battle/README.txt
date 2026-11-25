captured: 2026-11-25 (headless mgba_dump, by hand; Game Boy: wram.bin = C000-DFFF, hram.bin = FF80-FFFE)
rom: Pokemon - Version Rouge (France) (SGB Enhanced).gb (~/Downloads/Game ROMs & Emulation/gbc/, sha1 47a7622f...)
  - run as a Super Game Boy, like English Red.
save: as the red_fr fixture.
boot: as the red_fr fixture, then a wild battle poked in on the field (wCurEnemyLevel 0xD12C = 12, wCurOpponent 0xD05E = 0xB0,
  CHARMANDER's internal id), wait 600, A, wait 300: the battle menu (ATTAQ / PkMn / OBJET / FUITE).
  Then A, wait 60: the move list, cursor on TONNERRE (wTopMenuItemY 12, X 5, wCurrentMenuItem 1, ▶ at (5, 13)).
  Joseph PIKACHU Lv100 against a wild SALAMECHE Lv12.
