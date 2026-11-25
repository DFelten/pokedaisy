captured: 2026-11-25 (headless mgba_dump, native-capture/boot/green_landar_boxes.txt; Game Boy: wram.bin = C000-DFFF, hram.bin = FF80-FFFE,
  sram.bin = the cart's battery RAM, 4 banks of 0x2000 - where boxes 1-4 (bank 2) and 5-8 (bank 3) are kept, 0x566 bytes each)
rom: Pocket Monsters - Green Version (Japan) (En) (v1.0) (LandaR).gb (~/Downloads/Game ROMs & Emulation/gbc/, sha1 a2fcbaea...)
save: the green_landar fixture's (~/Documents/RetroArch/saves/mGBA/Pocket Monsters - Green Version (Japan) (En) (v1.0) (LandaR).sav,
  six Pokemon, every box empty, never changed), copied fresh for the run: mgba_dump writes the save back.
what was done, all by the game's own functions (gbcallat from the overworld loop at 0x1E22), none of it poked as box bytes:
  - two party Pokemon (slot 1 twice: Char, then Squi) deposited into BOX 1, the current box (wBoxDataStart 0xD9B2), with MoveMon
    (PARTY_TO_BOX) and RemovePokemon, as Bill's PC does;
  - the first box change: EmptyAllSRAMBoxes, SetMapTextPointer with the map's own text (0x40CF), then ChangeBox past its menu
    (1C:7D3D, the save sound first) with wCurrentMenuItem 7: BOX 1 into SRAM bank 2, BOX 8 the current one (wCurrentBoxNum
    0xD51F = 0x87), the game saved; the map's text pointer put back after;
  - one more party Pokemon (Pikac) deposited into BOX 8.
after it: party 3 (Bulba, Pid, Ratt), BOX 1 (bank 2) 2 Pokemon, BOX 8 (WRAM) 1. Rebooted from the save ChangeBox wrote: party 4,
  wCurrentBoxNum 0x87, BOX 1 in bank 2 (the last deposit wasn't saved in game, as on a cart).
sram.bin's bank 0 A000-A597 (the game's decompressed-picture buffers) zeroed after the capture: no game art in fixtures.
