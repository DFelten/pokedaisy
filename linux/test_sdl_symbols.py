"""The host imports no SDL function past SDL 2.0.10, the oldest firmware SDL we meet (the old ArkOS's, Ubuntu 19.10):
1.6.2 called SDL_SetTextureScaleMode (2.0.12) and died there on a game's start. A newer call goes through dlsym at run
time with a fallback (main.c's texture_nearest). test_sdl_symbols.py BINARY; reads the ELF itself, so a cross build's
binary works without its objdump. Not an ELF (macOS, Windows): nothing to check."""
from pathlib import Path
import struct, sys

data = Path(sys.argv[1]).read_bytes()
if data[:4] != b'\x7fELF' or data[4] != 2 or data[5] != 1:
    print('SKIP not a 64-bit little-endian ELF')
    sys.exit(0)
shoff, = struct.unpack_from('<Q', data, 0x28)
shentsize, shnum = struct.unpack_from('<HH', data, 0x3A)
sections = [struct.unpack_from('<IIQQQQIIQQ', data, shoff + i * shentsize) for i in range(shnum)]
needed = set()
for name, kind, flags, addr, off, size, link, info, align, entsize in sections:
    if kind != 11:  # SHT_DYNSYM
        continue
    stroff = sections[link][4]
    for i in range(1, size // entsize):
        st_name, st_info, st_other, st_shndx = struct.unpack_from('<IBBH', data, off + i * entsize)
        if st_shndx != 0:  # defined here, not imported
            continue
        end = data.index(b'\0', stroff + st_name)
        sym = data[stroff + st_name:end].decode()
        if sym.startswith('SDL_'):
            needed.add(sym)
known = {l.strip() for l in (Path(__file__).parent / 'handheld/sdl-2.0.10-symbols.txt').read_text().splitlines()
         if l.strip() and not l.startswith('#')}
newer = sorted(needed - known)
assert needed, 'no SDL import found: is this pokedaisy-linux?'
assert not newer, f'past SDL 2.0.10 (look these up at run time, as texture_nearest does): {", ".join(newer)}'
print(f'PASS {len(needed)} SDL functions, all in SDL 2.0.10')
