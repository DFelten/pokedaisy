#!/usr/bin/env python3
"""Where a Red / Blue / Yellow cart keeps its TRAINER CARD's pieces (Gen1TrainerCard.Tables), read from the
immediates of the card's own code (pokered / pokeyellow's DrawTrainerInfo), so a cart in another language needs no
symbol file: python3 scripts/find_gen1_card.py <rom>..."""
import re
import sys


def addr(o):
    return "0x%02X to 0x%04X" % (o // 0x4000, o % 0x4000 + (0x4000 if o >= 0x4000 else 0))


def find(rom):
    # DrawTrainerInfo: ld de, RedPicFront / lb bc, BANK(RedPicFront), 1 / predef DisplayPicCenteredOrUpperRight.
    m = next((m for m in re.finditer(rb"\x11(..)\x01\x01(.)\x3e\x3b", rom, re.S) if m.start() // 0x4000 == 4), None)
    if m is None:
        return None
    code = rom[m.start():m.start() + 0x100]
    bank = m.start() // 0x4000

    def word(pat):
        r = re.search(pat, code, re.S)
        return int.from_bytes(r.group(1), "little") if r else None

    def far(local, b):
        return b * 0x4000 + local - 0x4000

    # TrainerInfo_FarCopyData: ld a, BANK(TrainerInfoTextBoxTileGraphics) / jp FarCopyData, the call target of the copies.
    copy = word(rb"\x11\x70\x97\x01\x80\x00\xc5\xcd(..)")
    tb_bank = rom[far(copy, bank) + 1]
    out = {
        "pic": far(int.from_bytes(m.group(1), "little"), m.group(2)[0]),
        "textBox": far(word(rb"\x21(..)\x11\x70\x97"), tb_bank),
        "names": far(word(rb"\x21(..)\x11\x00\x96"), tb_bank),
        "numbers": far(word(rb"\x21(..)\x11\x80\x8d"), tb_bank),
        "faces": far(word(rb"\x21(..)\x11\x00\x92\x01\x00\x04\x3e"), code[re.search(rb"\x11\x00\x92\x01\x00\x04\x3e", code).end()]),
        "boxTiles": far(word(rb"\x21(..)\x11\xd0\x00\x19"), code[re.search(rb"\x01\x10\x00\x3e", code).end()]),
        "badgesText": far(word(rb"\x21\x5a\xc4\x11(..)"), bank),
        "nameMoneyTime": far(word(rb"\x21\xca\xc3\x11(..)"), bank),
    }
    # BlkPacket_TrainerCard (ATTR_BLK 10, its first block the BOULDERBADGE's at 3, 12 - 4, 13), in bank 0x1C. The palettes
    # (SuperPalettes / CGBBasePalettes) are the POKeDEX's, Gen1DexTables.cgbPalettes.
    blk = rom.find(bytes.fromhex("240a0200030c040d"), 0x1C * 0x4000, 0x1D * 0x4000)
    if blk >= 0:
        out["blocks"] = blk
    return out


for path in sys.argv[1:]:
    rom = open(path, "rb").read()
    t = find(rom)
    print(path)
    if t is None:
        print("  no DrawTrainerInfo")
        continue
    for k, v in t.items():
        print("  %s = %s," % (k, addr(v)))
