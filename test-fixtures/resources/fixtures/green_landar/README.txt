captured: 2026-11-25 (headless mgba_dump, by hand; Game Boy: wram.bin = C000-DFFF, hram.bin = FF80-FFFE)
rom: Pocket Monsters - Green Version (Japan) (En) (v1.0) (LandaR).gb (~/Downloads/Game ROMs & Emulation/gbc/, sha1 a2fcbaea...)
  - the Japanese cart in LandaR's English translation; run as a Super Game Boy, like Red.
save: ~/Documents/RetroArch/saves/mGBA/Pocket Monsters - Green Version (Japan) (En) (v1.0) (LandaR).sav - no save of its own existed:
  a new game played headless (player and rival AAAAA), six Pokemon given through the game's own AddPartyMon (gbcallat), five items
  through AddItemToInventory, $3000 poked into wPlayerMoney, then saved in game in the player's room and loaded with CONTINUE.
boot: native-capture/boot/green_landar.txt (wait 1600, START, wait 200, A, wait 120, A, wait 300): on the field in the player's room
  (REDS_HOUSE_2F) - wCurMap (0xD2DD) 0x26; wPartyCount (0xD123) 6: Bulba Lv5, Char Lv6, Squi Lv7, Pikac Lv8, Pid Lv4, Ratt Lv3
  (the game's own default names, 5 tiles); bag (wNumBagItems 0xD2A1) POTION x5, POKe BALL x10, ANTIDOTE x2, PARLYZ HEAL, ESCAPE ROPE.
