#!/usr/bin/env python3
"""Finds what Crystal's TRAINER CARD draws in a Crystal cart (Gen2TrainerCard.Tables), by the card's own code.

pret/pokecrystal's engine/menus/trainer_card.asm, engine/gfx/player_gfx.asm (GetCardPic) and engine/gfx/color.asm
(GetTrainerPalettePointer, GetPredefPal) load every piece with an immediate address; this reads those immediates, so a
translated cart (German Kristall: English's code, its data a few bytes apart) maps the same way. English rev 0 / rev 1 /
AU give pokecrystal.sym's addresses (checked against it).

    python3 scripts/find_crystal_card.py <crystal.gbc> [...]
"""
import sys


def find_all(rom, pat):
    """Offsets where `pat` (bytes, None = any) matches."""
    out, n = [], len(pat)
    first = pat[0]
    i = rom.find(bytes([first]))
    while 0 <= i <= len(rom) - n:
        if all(p is None or rom[i + k] == p for k, p in enumerate(pat)):
            out.append(i)
        i = rom.find(bytes([first]), i + 1)
    return out


def one(rom, pat, what, near=None):
    """The one match of `pat` (within 0x800 bytes after `near`, the card's code, when given)."""
    hits = [h for h in find_all(rom, pat) if near is None or near <= h < near + 0x800]
    if len(hits) != 1:
        sys.exit(f"{what}: {len(hits)} matches")
    return hits[0]


def word(rom, o):
    return rom[o] | rom[o + 1] << 8


def bank_of(o):
    return o // 0x4000


def at(bank, addr):
    return f"0x{bank:02X} to 0x{addr:04X}"


def scan(rom):
    _ = None
    r = {}
    # Gold / Silver (pret/pokegold): the player's picture and the card's frame are one blob (ChrisPicAndTrainerCardGFX,
    # 35 + 6 tiles to vTiles2), drawn row by row, with no corner tile of its own; the rest is Crystal's code.
    gold = find_all(rom, [0x21, _, _, 0x11, 0x00, 0x90, 0x01, 0x90, 0x02, 0x3E, _])
    gold = [h for h in gold if find_all(rom[h + 11:h + 30], [0x21, _, _, 0x11, 0x90, 0x92, 0x01, 0x60, 0x05, 0x3E])]
    player_name = 0xD1A3 if gold else 0xD47D
    tm = 0xC3A0 if gold else 0xC4A0  # wTilemap
    attr = 0xCCD9 if gold else 0xCDD9  # wAttrmap

    def coord(x, y):
        return tm + 20 * y + x
    # TrainerCard.InitRAM: ld hl, CardStatusGFX / ld de, vTiles2 tile $29 / ld bc, 86 tiles / ld a, BANK.
    o = one(rom, [0x21, _, _, 0x11, 0x90, 0x92, 0x01, 0x60, 0x05, 0x3E, _], "CardStatusGFX")
    code_bank = rom[o + 10]
    card = o - 0x80  # TrainerCard.InitRAM, then the whole card's code
    r["status"] = (code_bank, word(rom, o + 1))
    if gold:
        assert len(gold) == 1, gold
        pic = word(rom, gold[0] + 1)
        r["chris"] = r["kris"] = (rom[gold[0] + 10], pic)
        r["frame"] = (rom[gold[0] + 10], pic + 0x23 * 16)
        # The corner: Gold's border draws its picture's tile $04 at the boxes' top right (the bottom box's shows; the top
        # box's is under the picture).
        r["corner"] = (rom[gold[0] + 10], pic + 0x04 * 16)
    else:
        o = one(rom, [0x21, _, _, 0x11, 0xC0, 0x91, 0x01, 0x10, 0x00, 0x3E, code_bank], "CardRightCornerGFX")
        r["corner"] = (code_bank, word(rom, o + 1))
    # Page 2: ld de, LeaderGFX / ld hl, vTiles2 tile $29 / lb bc, BANK, 86 (the first match is page 1's CardStatusGFX).
    hits = find_all(rom, [0x11, _, _, 0x21, 0x90, 0x92, 0x01, 0x56, code_bank])
    assert len(hits) == 3, hits
    assert word(rom, hits[0] + 1) == r["status"][1]
    r["leaders"] = (code_bank, word(rom, hits[1] + 1))
    hits = find_all(rom, [0x11, _, _, 0x21, 0x00, 0x80, 0x01, 0x2C, code_bank])
    assert len(hits) == 2, hits
    r["badges"] = (code_bank, word(rom, hits[0] + 1))
    # TrainerCard_PrintTopHalfOfCard: hlcoord 2, 2 / ld de, .Name_Money; hlcoord X, 2 / ld de, wPlayerName.
    # (German Kristall prints the name at 2, 2 as well, with no NAME/ before it.)
    hits = [h for h in find_all(rom, [0x21, coord(2, 2) & 0xFF, coord(2, 2) >> 8, 0x11, _, _, 0xCD]) if card <= h < card + 0x800 and word(rom, h + 4) != player_name]
    assert len(hits) == 1, hits
    r["nameMoney"] = (code_bank, word(rom, hits[0] + 4))
    o = one(rom, [0x21, _, coord(0, 2) >> 8, 0x11, player_name & 0xFF, player_name >> 8, 0xCD], "player name", near=card)
    r["nameX"] = (word(rom, o + 1) - tm) % 20
    # TrainerCard_Page1_PrintDexCaught_GameTime: hlcoord 2, 10 / .Dex_PlayTime; hlcoord 10, 15 / .Badges.
    o = one(rom, [0x21, coord(2, 10) & 0xFF, coord(2, 10) >> 8, 0x11, _, _, 0xCD], "Dex_PlayTime", near=card)
    r["dexPlayTime"] = (code_bank, word(rom, o + 4))
    hits = [h for h in find_all(rom, [0x21, _, coord(10, 15) >> 8, 0x11, _, _, 0xCD]) if card <= h < card + 0x800 and word(rom, h + 1) in (coord(10, 15), coord(12, 15))]
    assert len(hits) == 1, hits
    r["badgesText"] = (code_bank, word(rom, hits[0] + 4))
    r["badgesX"] = (word(rom, hits[0] + 1) - tm) % 20
    # GetCardPic: ld hl, ChrisCardPic / ld a, [wPlayerGender] / bit 0, a / jr z / ld hl, KrisCardPic / ld de, vTiles2.
    if gold:
        r["rowMajor"] = True
    else:
     o = one(rom, [0x21, _, _, 0xFA, 0x72, 0xD4, 0xCB, 0x47, 0x28, 0x03, 0x21, _, _, 0x11, 0x00, 0x90, 0x01, 0x30, 0x02, 0x3E, _], "GetCardPic")
     pic_bank = rom[o + 20]
     r["chris"] = (pic_bank, word(rom, o + 1))
     r["kris"] = (pic_bank, word(rom, o + 11))
     o = one(rom, [0x21, _, _, 0x11, 0x30, 0x92, 0x01, 0x60, 0x00, 0x3E, pic_bank], "TrainerCardGFX")
     r["frame"] = (pic_bank, word(rom, o + 1))
    # GetTrainerPalettePointer: ld l, a / ld h, 0 / add hl, hl x2 / ld bc, TrainerPalettes / add hl, bc / ret.
    o = one(rom, [0x6F, 0x26, 0x00, 0x29, 0x29, 0x01, _, _, 0x09, 0xC9], "GetTrainerPalettePointer")
    r["trainerPals"] = (bank_of(o), word(rom, o + 6))
    r["trainerPals_fn"] = o
    # _CGB_TrainerCard: ld a, PREDEFPAL_CGB_BADGE / call GetPredefPal / call LoadHLPaletteIntoDE / hlcoord 0, 0, wAttrmap
    # (the opposite gender's border fill comes next); GetPredefPal: ld l, a / ld h, 0 / add hl, hl x3 / ld bc, PredefPals.
    gtpp = 0x4000 + r["trainerPals_fn"] % 0x4000
    cgb = one(rom, [0xAF, 0xCD, gtpp & 0xFF, gtpp >> 8, 0xCD, _, _, 0x3E, 0x01, 0xCD, gtpp & 0xFF, gtpp >> 8], "_CGB_TrainerCard")
    o = min(h for h in find_all(rom, [0x3E, _, 0xCD, _, _, 0xCD, _, _, 0x21, attr & 0xFF, attr >> 8]) if h > cgb)  # its own, the first after it
    assert o - cgb < 0x80, hex(o - cgb)
    get_predef = bank_of(o) * 0x4000 + word(rom, o + 3) - 0x4000
    assert rom[get_predef:get_predef + 6] == bytes([0x6F, 0x26, 0x00, 0x29, 0x29, 0x29]) and rom[get_predef + 6] == 0x01
    predef = (bank_of(o), word(rom, get_predef + 7))
    r["badgePal"] = (predef[0], predef[1] + 8 * rom[o + 1])
    # Font (LoadStandardFont's): Gen2Art's own table.
    return r


for path in sys.argv[1:]:
    rom = open(path, "rb").read()
    print(path)
    for k, v in scan(rom).items():
        if k.endswith("_fn"):
            continue
        print(f"    {k} = {at(*v) if isinstance(v, tuple) else v},")
