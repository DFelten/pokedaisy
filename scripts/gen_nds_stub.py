#!/usr/bin/env python3
"""The DS games' in-game code and scripts, as bytes for the app (app/src/main/cpp/nds/pk_nds_stub_gen.h).

    python3 scripts/gen_nds_stub.py     # needs clang + LLVM's objcopy, and $DECOMPS/pokeplatinum built in Docker
    python3 scripts/gen_nds_stub.py --only PK_NDS_ITEM_CODE   # rebuilds one array, keeps the others' bytes as they are

- stub/pk_nds_hook.c: the function caller pk_nds_call.cpp hooks into the game's main loop - clang for armv5te, ARM
  mode, no jump tables, so the bytes run wherever they're copied (the script checks there's nothing to relocate).
- stub/pk_nds_skill.c: Gen 5's field-skill helper (Black's FLY), built the same way.
- stub/pk_nds_item.c: Gen 4's bag helper (USE / GIVE / TAKE on a party Pokémon), built the same way.
- stub/pk_nds_item5.c: Gen 5's (its effects from the app, the game's get / set and bag calls), built the same way.
- stub/pk_nds_evo.c: TRADE EVOLUTIONS on Platinum / HeartGold / SoulSilver (a field task of ours around the game's
  evolution scene), built the same way.
- stub/pk_nds_evo5.c: the same on Black / White / Black 2 / White 2 (a GMEVENT of ours around the scene's proc).
- stub/platinum_*.s: field scripts in the game's own script language, assembled with pret/pokeplatinum's own macros
  and make_script_bin.sh in its build image (pokeplatinum-build, the one that builds the retail ROM byte for byte).
- stub/heartgold_*.s: the same for HeartGold / SoulSilver, with pret/pokeheartgold's macros and makescript.sh (in the
  same image; $DECOMPS/pokeheartgold built first, for its generated text-bank headers).
  Text and menu ids are the game's, so a script only says which of the game's lines to show.
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STUB = os.path.join(ROOT, "app/src/main/cpp/nds/stub")
OUT = os.path.join(ROOT, "app/src/main/cpp/nds/pk_nds_stub_gen.h")
LLVM = os.environ.get("LLVM_BIN") or next(
    (d for d in ("/opt/homebrew/opt/llvm/bin", "/usr/bin") if os.path.exists(os.path.join(d, "llvm-objcopy"))), "/usr/bin")
CODE = [("PK_NDS_HOOK_CODE", "pk_nds_hook.c"), ("PK_NDS_SKILL_CODE", "pk_nds_skill.c"), ("PK_NDS_ITEM_CODE", "pk_nds_item.c"),
        ("PK_NDS_ITEM5_CODE", "pk_nds_item5.c"), ("PK_NDS_EVO_CODE", "pk_nds_evo.c"),
        ("PK_NDS_EVO5_CODE", "pk_nds_evo5.c")]
SCRIPTS = [("PK_NDS_PLATINUM_PC", "platinum_pc.s"), ("PK_NDS_HEARTGOLD_PC", "heartgold_pc.s")]


def run(cmd, **kw):
    subprocess.run(cmd, check=True, **kw)


def hook(tmp, src="pk_nds_hook.c"):
    obj = os.path.join(tmp, src[:-2] + ".o")
    run(["clang", "--target=armv5te-none-eabi", "-marm", "-O2", "-fno-jump-tables", "-ffreestanding", "-fno-builtin",
         "-nostdlib", "-c", os.path.join(STUB, src), "-o", obj])
    # Every relocation of .text.entry (its records are in .rel.text.entry: `--section` filters by that name, so it's
    # read whole here). A call out of the section (a static function not inlined, the runtime's divide) would land
    # nowhere once only .text.entry is copied into the game.
    relocs = subprocess.run([os.path.join(LLVM, "llvm-objdump"), "-r", obj], capture_output=True, text=True, check=True).stdout
    entry = relocs.split("RELOCATION RECORDS FOR [.text.entry]:", 1)[1].split("RELOCATION RECORDS FOR", 1)[0] if \
        "RELOCATION RECORDS FOR [.text.entry]:" in relocs else ""
    assert "R_ARM" not in entry, src + " needs relocating:\n" + entry
    out = os.path.join(tmp, src[:-2] + ".bin")
    run([os.path.join(LLVM, "llvm-objcopy"), "-O", "binary", "--only-section=.text.entry", obj, out])
    return open(out, "rb").read()


def heartgold_script(tmp, name):
    dec = os.path.join(os.environ.get("DECOMPS") or sys.exit("set DECOMPS (local.mk)"), "pokeheartgold")
    shutil.copy(os.path.join(STUB, name), tmp)
    inc = ["subprojects/NitroSDK-4.2.30001/include", "subprojects/NitroSDK-4.2.30001/include/nitro",
           "subprojects/NitroSystem-071126.1/include", "include", "files", "build/heartgold.us/files", "asm/include",
           "asm", "."]
    args = [a for i in inc for a in ("-i", i)]
    run(["docker", "run", "--rm", "--platform", "linux/amd64", "-v", f"{dec}:/src", "-v", f"{tmp}:/w", "-w", "/src",
         "pokeplatinum-build", "tools/scripts/makescript.sh", "-d", "/w"] + args + ["/w/" + name])
    return open(os.path.join(tmp, name[:-2] + ".bin"), "rb").read()


def script(tmp, name):
    if name.startswith("heartgold_"):
        return heartgold_script(tmp, name)
    dec = os.path.join(os.environ.get("DECOMPS") or sys.exit("set DECOMPS (local.mk)"), "pokeplatinum")
    shutil.copy(os.path.join(STUB, name), tmp)
    run(["docker", "run", "--rm", "--platform", "linux/amd64", "-v", f"{dec}:/src", "-v", f"{tmp}:/w", "-w", "/src/build",
         "pokeplatinum-build", "/src/tools/scripts/make_script_bin.sh", "-i", "../include", "-i", "../asm", "-i", ".",
         "--enumproc", "/src/build/tools/enumproc/enumproc", "--assembler", "/usr/bin/arm-none-eabi-gcc",
         "--objcopy", "/usr/bin/arm-none-eabi-objcopy", "--out-dir", "/w", "/w/" + name])
    return open(os.path.join(tmp, name[:-2]), "rb").read()


def array(name, data):
    rows = [", ".join(f"0x{b:02X}" for b in data[i:i + 16]) for i in range(0, len(data), 16)]
    return f"static const unsigned char {name}[{len(data)}] = {{\n" + "".join(f"    {r},\n" for r in rows) + "};\n"


def existing():
    """The arrays the header has now, by name, as their text."""
    import re
    if not os.path.exists(OUT):
        return {}
    text = open(OUT).read()
    return {m.group(1): m.group(0) for m in re.finditer(r"static const unsigned char (\w+)\[\d+\] = \{\n.*?\n\};\n", text, re.S)}


def main():
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None
    have = existing()
    with tempfile.TemporaryDirectory() as tmp:
        parts = []
        for sym, src in CODE:
            parts.append(have[sym] if only is not None and sym not in only and sym in have else array(sym, hook(tmp, src)))
        for sym, name in SCRIPTS:
            if only is not None and sym not in only and sym in have:
                parts.append(have[sym])
                continue
            data = script(tmp, name)
            assert len(data) <= 0x1000, (name, len(data))
            parts.append(array(sym, data))
    with open(OUT, "w") as f:
        f.write("// Generated by scripts/gen_nds_stub.py from app/src/main/cpp/nds/stub/ - do not edit.\n#pragma once\n\n")
        f.write("\n".join(parts))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
