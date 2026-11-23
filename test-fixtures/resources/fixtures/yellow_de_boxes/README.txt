captured: 2026-11-22 (headless mgba_dump, native-capture/boot/yellow_de_boxes.txt; Game Boy: wram.bin = C000-DFFF, hram.bin = FF80-FFFE,
  sram.bin = the cart's battery RAM, 4 banks of 0x2000 - where boxes 1-6 (bank 2) and 7-12 (bank 3) are kept)
rom: Pokemon - Gelbe Edition - Special Pikachu Edition (Germany) (GBC,SGB Enhanced).gb
save: scripts/gen1_save_from_fixture.py fixtures/yellow --yellow <out.sav> (six Pokemon, every box empty, never changed), copied fresh
  for the run: mgba_dump writes the save back.
what was done, all by the game's own functions (gbcallat from the overworld loop), none of it poked as box bytes:
  - two party Pokemon (slot 1 twice) deposited into BOX 1, the current box (wBoxDataStart), with MoveMon (PARTY_TO_BOX)
    and RemovePokemon, as Bill's PC does;
  - the first box change: EmptyAllSRAMBoxes, SetMapTextPointer with the map's own text, then ChangeBox past its menu with
    wCurrentMenuItem 7: BOX 1 into SRAM bank 2, BOX 8 the current one (wCurrentBoxNum 0x87), the game saved; the map's
    text pointer put back after;
  - one more party Pokemon deposited into BOX 8.
after it: party 3 (slots 0, 4, 5 of before), BOX 1 (bank 2) 2 Pokemon, BOX 8 (WRAM) 1. Saving in game and rebooting kept all of it.
sram.bin's bank 0 A000-A597 (the game's decompressed-picture buffers) zeroed after the capture: no game art in fixtures.
