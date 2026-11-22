# pc_probe

A headless libretro frontend for the mkxp-z core (PC fan games, `docs/specs/pc-fan-games.md`): an EGL surfaceless
OpenGL context (Mesa's llvmpipe is enough) and an FBO for the core's hardware rendering, driven by a script.

```
cc -O2 -I../../third_party/mgba/src/platform/libretro -o pc_probe pc_probe.c -ldl -lEGL -lGL
pc_probe mkxp-z_libretro.so <game>/Game.ini <system dir> <save dir> script.txt
```

Script lines:

| Line | Does |
|---|---|
| `opt KEY VALUE` | a core option (read before the game loads, wherever it is in the script) |
| `run N` | N frames |
| `press BTN N` | hold a RetroPad button (A, B, X, Y, UP, DOWN, LEFT, RIGHT, START, SELECT, L, R...) for N frames |
| `shot FILE.ppm` | the frame (the core's own size, top row first) |
| `save FILE` / `load FILE` | `retro_serialize` / `retro_unserialize`, timed |
| `mbx FILE` | the companion's mailbox (`linux/pc_games/pokedaisy_companion.rb`): found by its magic, the JSON written to FILE |
| `cheat RUBY` | `retro_cheat_set`: the core runs the line as Ruby in the game (`$Trainer.party << Pokemon.new(:PIKACHU, 12)`) |
| `ram FILE` | `RETRO_MEMORY_SYSTEM_RAM` (the WASM heap Ruby lives in) |
| `time` | frames run since the last `time`, ms per frame |

Preload scripts go in `<system dir>/mkxp-z/Scripts/Preload/` (copy `linux/pc_games/*.rb` there) and are switched on
with `opt mkxp-z_preload-<file name in hex> enabled`; `pokedaisy_companion.rb` is
`mkxp-z_preload-706f6b6564616973795f636f6d70616e696f6e2e7262`. For states: `opt mkxp-z_threadedAudio disabled` and
`opt mkxp-z_saveStateSize 512` (Infinite Fusion's heap is ~400 MB). Run at least one frame before a `load`.

## Building the core (2026-11-17)

white-axe/mkxp-z, branch `libretro` (commit 650cb08), with `third_party/patches/mkxp-z-libretro-pokedaisy.patch`:

1. Stage 1 (`libretro/README.md`): WASI SDK **30** exactly, Binaryen 117+ (124 used), Universal Ctags, Ruby.
   `make -C libretro WASI_SDK=... WASM_OPT=.../wasm-opt CTAGS=...` (~15 min on 4 cores).
2. Stage 2: `meson setup build -Dlibretro=true -Dbuildtype=release && ninja -C build` (~10 min).

In a cloud session the proxy refused deb.debian.org, ftpmirror.gnu.org and GitHub's archive zips. What stood in
(scratch builds only, never a release): uchardet's orig tarball from archive.ubuntu.com (same SHA-256);
fluidsynth's gcem cloned into `subprojects/fluidsynth/gcem` at the revision `FindGCEM.cmake` names; libiconv and libidn
replaced by stub subprojects (glibc's iconv, the system libidn) - glibc doesn't know GNU's `UCS-4-INTERNAL`, so
`src/util/encoding.h` said `WCHAR_T` instead. On a machine that reaches those hosts, none of that is needed.
