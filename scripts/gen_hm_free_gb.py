#!/usr/bin/env python3
"""NO HMS NEEDED's Game Boy patches (app/src/main/kotlin/com/pokedaisy/app/companion/data/HmFreeGbGen.kt).

    DECOMPS=<dir> python3 scripts/gen_hm_free_gb.py      # needs rgbasm / rgblink (RGBDS) on PATH or in $RGBDS

scripts/hm_free_gb.asm assembled once per English cart with the addresses from pret's .sym for that build
($DECOMPS/pokered: pokered.sym / pokeblue.sym, pokeyellow, pokegold: pokegold.sym / pokesilver.sym, pokecrystal:
pokecrystal11.sym - rev 0 and Australia keep the same addresses). Each patch is a list of writes at file offsets with the
bytes they replace, which the app checks against the cart before writing anything: our code over a bank's unused zeros,
and the jump to it in the game's own routine. Facts only: pret's addresses and our own code's bytes.
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASM = os.path.join(ROOT, "scripts", "hm_free_gb.asm")
OUT = os.path.join(ROOT, "app/src/main/kotlin/com/pokedaisy/app/companion/data/HmFreeGbGen.kt")
DECOMPS = os.environ.get("DECOMPS") or os.path.expanduser("~/Projects/tests/my-rom-hacks/build")
RGBDS = os.environ.get("RGBDS", "")
HERE = 0x7F00   # where our code goes in its bank: every cart's bank is zeros from well before it

# name, sha-1 constant(s) in TelemetrySampler, .sym (under $DECOMPS), its built ROM (for the bytes a patch replaces)
CARTS = [
    ("RED", "setOf(TelemetrySampler.RED_SHA1)", "pokered/pokered.sym", "pokered/pokered.gbc", 1),
    ("BLUE", "setOf(TelemetrySampler.BLUE_SHA1)", "pokered/pokeblue.sym", "pokered/pokeblue.gbc", 1),
    ("YELLOW", "setOf(TelemetrySampler.YELLOW_SHA1)", "pokeyellow/pokeyellow.sym", "pokeyellow/pokeyellow.gbc", 1),
    ("GOLD", "setOf(TelemetrySampler.GOLD_SHA1)", "pokegold/pokegold.sym", "pokegold/pokegold.gbc", 2),
    ("SILVER", "setOf(TelemetrySampler.SILVER_SHA1)", "pokegold/pokesilver.sym", "pokegold/pokesilver.gbc", 2),
    ("CRYSTAL", "TelemetrySampler.CRYSTAL_SHA1S", "pokecrystal/pokecrystal11.sym", "pokecrystal/pokecrystal11.gbc", 2),
]


def tool(name):
    return os.path.join(RGBDS, name) if RGBDS else (shutil.which(name) or sys.exit(f"{name} not found: set RGBDS"))


def symbols(path):
    out = {}
    for line in open(path):
        line = line.split(";")[0].strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) != 2 or ":" not in parts[0]:
            continue
        where, name = parts
        bank, addr = where.split(":")
        out[name] = (int(bank, 16), int(addr, 16))
    return out


def offset(bank, addr):
    return addr if addr < 0x4000 else bank * 0x4000 + addr - 0x4000


def assemble(tmp, flag, bank, defs):
    obj = os.path.join(tmp, flag + ".o")
    rom = os.path.join(tmp, flag + ".gb")
    args = [tool("rgbasm"), "-D", flag, "-D", f"HERE={HERE}", "-D", f"BANKNO={bank}"]
    args += [a for k, v in defs.items() for a in ("-D", f"{k}={v}")]
    subprocess.run(args + ["-o", obj, ASM], check=True)
    sym = os.path.join(tmp, flag + ".sym")
    subprocess.run([tool("rgblink"), "-o", rom, "-n", sym, obj], check=True)
    end = symbols(sym)["HmEnd"][1]
    data = open(rom, "rb").read()
    start = offset(bank, HERE)
    return data[start:start + end - HERE]


def main():
    tmp = tempfile.mkdtemp()
    entries = []
    for name, sha, sym, built, gen in CARTS:
        s = symbols(os.path.join(DECOMPS, sym))
        rom = open(os.path.join(DECOMPS, built), "rb").read()
        w = s.get
        writes = []

        def put(at_bank, at_addr, value):
            o = offset(at_bank, at_addr)
            writes.append((o, rom[o:o + len(value)], value))

        if gen == 1:
            bank, fm = w("GetMonFieldMoves")
            defs = {k: w(k)[1] for k in ("GetMonFieldMoves", "wFieldMoves", "wNumFieldMoves",
                                         "wFieldMovesLeftmostXCoord", "wObtainedBadges")}
            code = assemble(tmp, "GEN1", bank, defs)
            put(bank, HERE, code)
            # DisplayFieldMoveMonMenu's `call GetMonFieldMoves`
            b, start = w("DisplayFieldMoveMonMenu")
            o = offset(b, start)
            call = bytes([0xCD, fm & 0xFF, fm >> 8])
            at = rom.index(call, o, o + 0x40)
            writes.append((at + 1, rom[at + 1:at + 3], bytes([HERE & 0xFF, HERE >> 8])))
        else:
            bank, cpm = w("CheckPartyMove")
            defs = {k: w(k)[1] for k in ("AddNTimes", "wCurPartyMon", "wPartySpecies", "wPartyMon1Moves")}
            code = assemble(tmp, "GEN2_CHECK", bank, defs)
            put(bank, HERE, code)
            o = offset(bank, cpm)
            assert rom[o:o + 3] == bytes([0x1E, 0x00, 0xAF]), name   # ld e, 0 / xor a
            put(bank, cpm, bytes([0xC3, HERE & 0xFF, HERE >> 8]))
            mbank, skip = w("GetMonSubmenuItems.skip_moves")
            defs = {k: w(k)[1] for k in ("AddMonMenuItem", "wLinkMode", "wJohtoBadges", "wMonSubmenuCount",
                                         "wMonSubmenuItems")}
            code = assemble(tmp, "GEN2_MENU", mbank, defs)
            put(mbank, HERE, code)
            add = w("AddMonMenuItem")[1]
            o = offset(mbank, skip)
            assert rom[o:o + 5] == bytes([0x3E, 15, 0xCD, add & 0xFF, add >> 8]), name   # ld a, STATS / call AddMonMenuItem
            put(mbank, skip, bytes([0xCD, HERE & 0xFF, HERE >> 8, 0x00, 0x00]))   # call HmMenu2 / nop / nop
        for o, old, new in writes:
            assert len(old) == len(new), name
        entries.append((name, sha, writes))
    shutil.rmtree(tmp)

    lines = [
        "// Generated by scripts/gen_hm_free_gb.py from scripts/hm_free_gb.asm and pret's .sym: do not edit.",
        "package com.pokedaisy.app.companion.data",
        "",
        "/** NO HMS NEEDED on the English Game Boy carts ([HmFreeGb]): each cart's writes at file offsets, with the bytes they",
        " * replace (null: as many zeros, a bank's unused end). */",
        "internal val HM_FREE_GB: List<HmFreeGb.Cart> = listOf(",
    ]
    for name, sha, writes in entries:
        lines.append(f"    HmFreeGb.Cart(\"{name}\", {sha}, listOf(")
        for o, old, new in writes:
            was = "null" if not old.strip(b"\x00") else f'"{old.hex()}"'   # null: zeros the game never reads
            h = new.hex()
            parts = [h[i:i + 96] for i in range(0, len(h), 96)]
            if len(parts) == 1:
                lines.append(f"        HmFreeGb.Edit(0x{o:X}, {was}, \"{h}\"),")
            else:
                lines.append(f"        HmFreeGb.Edit(0x{o:X}, {was},")
                lines += [f"            \"{p}\"" + (" +" if i < len(parts) - 1 else ",") for i, p in enumerate(parts)]
                lines.append("        ),")
        lines.append("    )),")
    lines.append(")")
    open(OUT, "w").write("\n".join(lines) + "\n")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
