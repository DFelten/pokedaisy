#!/usr/bin/env python3
"""Check a PortMaster package on dArkOS / ArkOS without the device: arm64 Docker stand-ins of their userland
(dArkOS: Debian 13, glibc 2.41; old ArkOS: Ubuntu 19.10, glibc 2.30, SDL 2.0.10), the package's own launcher in their
layout, the diagnostic ROMs played through it, and optionally how many instructions a real game's frame takes, turned
into an estimate for an R36S (RK3326: Cortex-A35). Needs Docker running arm64 images (native on Apple silicon).

  python3 linux/handheld/check.py --package pokedaisy-portmaster-aarch64.zip
  python3 linux/handheld/check.py --package <zip> --build              # this checkout's host in place of the package's
  python3 linux/handheld/check.py --package <zip> --rom <FireRed.gba> --save <its .sav / .srm>   # + the frame cost

What it can't see: the GPU / display driver (Mali, KMS, rotated panels), the real buttons, sleep. A stand-in renders
in software, so SDL's own cost is left out of the estimate (on the device the GPU scales the picture).
Never point --save at a save you play: it's copied first, but keep to copies anyway."""
import argparse, collections, os, re, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LINUX = HERE.parent
REPO = LINUX.parent
STANDINS = {'pd-darkos': 'Dockerfile.darkos', 'pd-arkos': 'Dockerfile.arkos'}

# Instructions a millisecond on the TrimUI Brick (Cortex-A53, 1.8 GHz), from its measured FireRed frames with EXPAND on
# (linux/README.md: armGBA 4.2 ms, mGBA 7.1 ms) over this script's count of the same scene (2.73 M / 10.22 M).
BRICK_RATE = {'armGBA': 2.73 / 4.2, 'mGBA': 10.22 / 7.1}
# An R36S against the Brick: RK3326 at 1.3 GHz (1.5 overclocked), a Cortex-A35 ~0.8 of an A53 a clock.
R36S = {'1.3 GHz': 1.3 / 1.8 * 0.8, '1.5 GHz': 1.5 / 1.8 * 0.8}


def sh(cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


def image(tag, dockerfile):
    sh(['docker', 'build', '-q', '-f', str(HERE / dockerfile), '-t', tag, str(HERE)], stdout=subprocess.DEVNULL)


def unpack(package, into):
    package = Path(package).resolve()
    if package.is_file():
        with zipfile.ZipFile(package) as z:
            z.extractall(into)
        package = into
    launcher = next(package.rglob('PokeDaisy.sh'), None)
    if not launcher:
        sys.exit(f'{package}: no PokeDaisy.sh (not a --portmaster package)')
    (launcher.parent / 'pokedaisy/pokedaisy-linux').chmod(0o755)  # zipfile drops the mode; PortMaster's installer keeps it
    return launcher.parent


def build_host(out):
    if not (REPO / 'third_party/mgba/CMakeLists.txt').exists() or not (REPO / 'third_party/zlib/zlib.h').exists():
        sys.exit('--build needs third_party/mgba and third_party/zlib: git submodule update --init third_party/mgba third_party/zlib')
    image('pd-build-arm', 'Dockerfile.build')
    sh(['docker', 'run', '--rm', '-v', f'{REPO}:/src:ro', '-v', f'{out}:/b', 'pd-build-arm', 'bash', '-c',
        'cmake -S /src/linux -B /b -DCMAKE_BUILD_TYPE=Release -DBUILD_LTO=OFF > /b/configure.log 2>&1 && '
        'cmake --build /b -j"$(nproc)" --target pokedaisy-linux > /b/build.log 2>&1 || { tail -30 /b/*.log; exit 1; }'])
    return out / 'pokedaisy-linux'


def by_object(path):
    """Self instructions by shared object in a callgrind file (calls= lines' costs are inclusive: skipped)."""
    names, per, ob, skip = {}, collections.Counter(), '?', False
    for line in open(path, errors='replace'):
        m = re.match(r'c?ob=\((\d+)\)(?: (.*))?', line.strip())
        if m:
            if m.group(2):
                names[m.group(1)] = m.group(2)
            if line.startswith('ob='):
                ob = Path(names.get(m.group(1), '?')).name
            continue
        if line.startswith('calls='):
            skip = True
            continue
        if line[:1] in '0123456789+-*':
            if skip:
                skip = False
                continue
            parts = line.split()
            if len(parts) >= 2:
                per[ob] += int(parts[1])
    return per


def frame_cost(pkg, rom, save, keys, first, last, work):
    w = work / 'cost'
    (w / 'data/saves').mkdir(parents=True)
    shutil.copy(rom, w / ('game' + Path(rom).suffix))
    if save:
        shutil.copy(save, w / 'data/saves' / 'game.sav')
    script = ('cd /pkg/pokedaisy; export SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy SDL_RENDER_DRIVER=software POCKET_WINDOW=640x480\n'
              f'export POCKET_KEYS="{keys}"\n'
              f'for n in {first} {last}; do t=/tmp/run$n; cp -r /w/data $t\n'
              f'  valgrind --tool=callgrind --smc-check=all --callgrind-out-file=/w/cg.$n ./pokedaisy-linux --data $t '
              f'--frames $n /w/game{Path(rom).suffix} > /w/run$n.log 2>&1 &\n'
              'done; wait\n'
              f'cp -r /w/data /tmp/shot; ./pokedaisy-linux --data /tmp/shot --frames {last} --shot /w/last.png '
              f'/w/game{Path(rom).suffix} > /dev/null 2>&1 || true\n')
    (w / 'cost.sh').write_text(script)
    print(f'Counting instructions over frames {first}-{last} under valgrind (several minutes)...', flush=True)
    sh(['docker', 'run', '--rm', '-v', f'{pkg}:/pkg:ro', '-v', f'{w}:/w', 'pd-darkos', 'bash', '/w/cost.sh'])
    for n in (first, last):
        if not (w / f'cg.{n}').exists():
            sys.exit(f'The {n}-frame run left no count:\n' + (w / f'run{n}.log').read_text()[-2000:])
    a, b = by_object(w / f'cg.{first}'), by_object(w / f'cg.{last}')
    n = last - first
    per = {k: (b[k] - a[k]) / n / 1e6 for k in set(a) | set(b)}
    shot = Path(tempfile.gettempdir()) / 'pokedaisy-handheld-last.png'
    if (w / 'last.png').exists():
        shutil.copy(w / 'last.png', shot)
    print(f'\nMillions of instructions a frame (frames {first}-{last}; frame {last} is {shot}):')
    for k, v in sorted(per.items(), key=lambda kv: -kv[1]):
        if v >= 0.05:
            note = '  (software rendering: the GPU on a device, left out)' if k.startswith('libSDL2') else ''
            print(f'  {v:7.2f}  {k}{note}')
    core = 'armGBA' if per.get('pokedaisy_gba_libretro.so', 0) > 0.05 else 'mGBA'
    cpu = sum(v for k, v in per.items() if not k.startswith('libSDL2'))
    rate = BRICK_RATE[core]
    if per.get('pokedaisy_ds_libretro.so', 0) > 0.05:
        print('  (a DS game: no device measurement to scale from, so the estimate is rough)')
        rate = BRICK_RATE['mGBA']
    print(f'\nEstimated frame time ({core}-like code, {cpu:.2f} M instructions, a frame has 16.7 ms):')
    print(f'  TrimUI Brick (A53 1.8 GHz)   {cpu / rate:5.1f} ms')
    for clock, scale in R36S.items():
        print(f'  R36S (A35 {clock})          {cpu / (rate * scale):5.1f} ms')
    print('  plus the device\'s GPU upload and present, which a stand-in can\'t measure.')


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    p.add_argument('--package', required=True, help='pokedaisy-portmaster-aarch64.zip or its extracted folder')
    p.add_argument('--binary', help='an aarch64 pokedaisy-linux to use in place of the package\'s')
    p.add_argument('--build', action='store_true', help='build this checkout\'s host (Debian 11, glibc 2.31) and use it')
    p.add_argument('--rom', help='a real game for the frame cost (FireRed / Emerald with --save reach the field by default)')
    p.add_argument('--save', help='a copy of its save')
    p.add_argument('--keys', default='700:start:6,900:start:6,1100:a:6,1300:a:6,1500:b:6,1900:b:6',
                   help='POCKET_KEYS to reach the scene to measure')
    p.add_argument('--frames', default='1700:2300', help='FIRST:LAST: the frames counted (the scene must hold between them)')
    p.add_argument('--skip-launch', action='store_true', help='only the frame cost')
    args = p.parse_args()

    for tag, dockerfile in STANDINS.items():
        image(tag, dockerfile)
    with tempfile.TemporaryDirectory(prefix='pd-handheld-', dir=os.environ.get('TMPDIR')) as work:
        work = Path(work)
        pkg = unpack(args.package, work / 'pkg')
        binary = build_host(work / 'build') if args.build else args.binary
        if binary:
            shutil.copy(binary, pkg / 'pokedaisy/pokedaisy-linux')
        diag = work / 'diag'
        diag.mkdir()
        sh([sys.executable, str(LINUX / 'make_diagnostic.py')], cwd=diag, stdout=subprocess.DEVNULL)
        sh([sys.executable, str(LINUX / 'dsperate/make_diagnostic.py'), str(diag / 'diagnostic.nds')])
        failed = False
        if not args.skip_launch:
            for tag in STANDINS:
                print(f'== {tag}', flush=True)
                r = subprocess.run(['docker', 'run', '--rm', '-v', f'{pkg}:/pkg:ro', '-v', f'{diag}:/diag:ro',
                                    '-v', f'{HERE / "launch_check.sh"}:/check.sh:ro', tag, 'bash', '/check.sh'])
                failed |= r.returncode != 0
        if args.rom:
            first, last = (int(x) for x in args.frames.split(':'))
            frame_cost(pkg, args.rom, args.save, args.keys, first, last, work)
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
