#!/usr/bin/env python3
"""The desktop companion (Linux, Windows, Steam Deck, Linux handhelds) at every screen size it has to look right on.

  python3 3ds/tools/desktop_preview.py [--out DIR] [--font small|normal|both] [--only WxH,...] [--fr ROM] [--em ROM]

For each window size, pd_preview (PD_PREVIEW_WINDOW) draws the companion's shots at the size the SDL host gives it there
(the window over the dimmed game, or the whole canvas on a small screen) from the RAM fixtures, no emulator. index.html
shows every shot at its real scale inside a picture of that window, so a size's proportions read at a glance (open it in
a browser; the windows are drawn shrunk to fit, the companion inside them at its true share of the window).
The ROMs feed MAP / DEX / GUIDE; without them those shots are left out. Needs `make -C 3ds build/pd_preview` first
(run from the repository's root or anywhere: paths are found from this file).
"""
import argparse
import html
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TDS = os.path.dirname(HERE)
REPO = os.path.dirname(TDS)
FIXTURES = os.path.join(REPO, "test-fixtures", "resources", "fixtures")
ROMS = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba")

# (size, what it is). Every class of screen the desktop build meets: handhelds, the Steam Deck, laptops and monitors
# (Windows reports them in physical pixels once the host is DPI-aware), an ultrawide, a small and a portrait window.
SIZES = [
    ("640x480", "Handheld 4:3 (TrimUI, RG35XX)"),
    ("720x720", "Square handheld (RG Cube)"),
    ("1024x768", "TrimUI Brick"),
    ("1280x720", "720p handheld / TrimUI Smart Pro"),
    ("1280x800", "Steam Deck"),
    ("1366x768", "Small laptop"),
    ("1920x1080", "1080p monitor, docked Deck"),
    ("2560x1440", "1440p monitor"),
    ("3840x2160", "4K monitor"),
    ("3440x1440", "Ultrawide"),
    ("960x600", "A small desktop window"),
    ("800x1280", "Portrait window / tablet"),
]

FIXTURE_SETS = [
    ("firered", "firered_vanilla", "BPRE", "fr"),
    ("emerald-battle", "emerald_shiny_battle", "BPEE", "em"),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(TDS, "build", "desktop-shots"))
    ap.add_argument("--font", default="both", choices=["small", "normal", "both"])
    ap.add_argument("--only", default="")
    ap.add_argument("--fr", default=os.path.join(ROMS, "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba"))
    ap.add_argument("--em", default=os.path.join(ROMS, "Pokemon - Emerald Version (USA, Europe).gba"))
    a = ap.parse_args()
    tool = os.path.join(TDS, "build", "pd_preview")
    if not os.path.exists(tool):
        sys.exit("desktop_preview: build pd_preview first: make -C 3ds build/pd_preview")
    only = set(filter(None, a.only.split(",")))
    sizes = [s for s in SIZES if not only or s[0] in only]
    fonts = ["small", "normal"] if a.font == "both" else [a.font]
    roms = {"fr": a.fr, "em": a.em}
    results = []  # (size, label, font, mode, scale, uiw, uih, [(name, png)])
    for size, label in sizes:
        for font in fonts:
            out = os.path.join(a.out, size, font)
            os.makedirs(out, exist_ok=True)
            mode, scale, shots = "?", 1, []
            for name, fixture, code, rom in FIXTURE_SETS:
                args = [tool, os.path.join(FIXTURES, fixture), code, out, name]
                if os.path.exists(roms[rom]):
                    args.append(roms[rom])
                env = dict(os.environ, PD_PREVIEW_WINDOW=size, PD_PREVIEW_FONT=font)
                r = subprocess.run(args, env=env, capture_output=True, text=True)
                if r.returncode:
                    sys.exit(f"desktop_preview: {size} {font} {fixture} failed:\n{r.stderr}")
                for line in r.stdout.splitlines():
                    if line.startswith("window "):
                        _, _, _, mode, k = line.split()
                        scale = int(k)
                    elif line.endswith(".png"):
                        shots.append((os.path.basename(line)[:-4], os.path.relpath(line, a.out)))
            results.append((size, label, font, mode, scale, shots))
            print(f"{size:>10} {font:6} {mode:6} x{scale}  {len(shots)} shots")
    write_index(a.out, results)
    print(os.path.join(a.out, "index.html"))


def write_index(out, results):
    # A window is drawn 640 px wide at most (shrunk by `f`), the companion inside it at k * f.
    rows = []
    for size, label, font, mode, k, shots in results:
        ww, wh = map(int, size.split("x"))
        f = min(640 / ww, 420 / wh)
        cells = []
        for name, rel in shots:
            cells.append(
                f'<figure><div class="win" style="width:{ww * f:.0f}px;height:{wh * f:.0f}px">'
                f'<img src="{html.escape(rel)}" style="zoom:{k * f:.4f}"></div>'
                f"<figcaption>{html.escape(name)}</figcaption></figure>"
            )
        what = "window over the game" if mode == "window" else "fills the screen"
        rows.append(
            f'<section data-font="{font}"><h2>{size} <small>{html.escape(label)} · {font} font · '
            f"companion {what}, {k}x</small></h2><div class=row>{''.join(cells)}</div></section>"
        )
    page = f"""<!doctype html><meta charset=utf-8><title>Desktop companion sizes</title>
<style>
body{{background:#15171d;color:#e8eaf0;font:14px system-ui;margin:16px}}
h2{{font-size:16px;margin:24px 0 8px}} small{{color:#9aa0ad;font-weight:400}}
.row{{display:flex;gap:16px;overflow-x:auto;padding-bottom:8px}}
figure{{margin:0}} figcaption{{color:#9aa0ad;font-size:12px;margin-top:4px}}
.win{{background:#2a3140;outline:1px solid #4a5160;display:flex;align-items:center;justify-content:center;overflow:hidden}}
img{{image-rendering:pixelated;box-shadow:0 0 0 1px #000}}
nav button{{background:#2a3140;color:inherit;border:1px solid #4a5160;padding:4px 10px;margin-right:6px;cursor:pointer}}
</style>
<nav><button onclick="show('')">Both fonts</button><button onclick="show('small')">PIXEL SMALL</button><button onclick="show('normal')">PIXEL</button></nav>
{''.join(rows)}
<script>function show(f){{for(const s of document.querySelectorAll('section'))s.hidden=f&&s.dataset.font!==f}}</script>
"""
    with open(os.path.join(out, "index.html"), "w") as fh:
        fh.write(page)


if __name__ == "__main__":
    main()
