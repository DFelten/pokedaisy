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
| `key KEY N` | a keyboard key held N frames (the core's keyboard callback): `RETURN`, `ESCAPE`, `BACKSPACE` or one character |
| `cheat RUBY` | `retro_cheat_set`: the core runs the line as Ruby in the game (`$Trainer.party << Pokemon.new(:PIKACHU, 12)`); lines up to 8 KB |
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
(scratch builds only, never a release): uchardet's and libidn's orig tarballs from archive.ubuntu.com (same SHA-256 as
the wraps'; put in `subprojects/packagecache/` as `uchardet.tar.xz` / `libidn.tar.gz`; libidn's unpacks as
`libidn-v1.43`, so copy it over the wrap's `libidn-1.43`); fluidsynth's gcem cloned into `subprojects/fluidsynth/gcem`
at the revision `FindGCEM.cmake` names; libiconv replaced by a stub subproject (glibc's iconv) - glibc doesn't know
GNU's `UCS-4-INTERNAL`, so `src/util/encoding.h` said `WCHAR_T` instead. On a machine that reaches those hosts, none of
that is needed.

### Linux aarch64

Stage 1's output is the same for every target. Stage 2 with a cross file (`gcc-aarch64-linux-gnu`,
`g++-aarch64-linux-gnu`), ~6.5 min on 4 cores:

```
[binaries]
c = 'aarch64-linux-gnu-gcc'
cpp = 'aarch64-linux-gnu-g++'
ar = 'aarch64-linux-gnu-ar'
strip = 'aarch64-linux-gnu-strip'
pkg-config = 'false'
cmake = 'cmake'
[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'generic-armv8-a'
endian = 'little'
[cmake]
CMAKE_SYSTEM_PROCESSOR = 'aarch64'
```

`meson setup build-a64 --cross-file cross-a64.ini -Dlibretro=true -Dlibretro_save_states=true
-Demscripten_threaded=false -Dbuildtype=release -Db_lto=false -Dstatic_libstdcpp=true && ninja -C build-a64`. The
probe: `aarch64-linux-gnu-gcc ... -ldl -lEGL -lGL` against the arm64 Mesa packages (`dpkg --add-architecture arm64`,
ports.ubuntu.com), run with `qemu-aarch64`. The glibc the core needs is the cross compiler's (2.38 on Ubuntu 24.04).
