#!/bin/bash
# Run inside a stand-in (check.py): the PortMaster package as dArkOS / ArkOS lay it out - PortMaster in
# /opt/system/Tools/PortMaster, the games in /roms - with a stand-in control.txt. /pkg is the extracted package
# (PokeDaisy.sh + pokedaisy/), /diag the diagnostic ROMs. Prints PASS / FAIL lines; exits non-zero on a failure.
set -u
fail=0
check() { if eval "$2"; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }

mkdir -p /opt/system/Tools/PortMaster /roms/ports /roms/gba /roms/gb
cp -r /pkg/. /roms/ports/
chmod +x /roms/ports/PokeDaisy.sh /roms/ports/pokedaisy/pokedaisy-linux
cp /diag/diagnostic.gba /roms/gba/
cp /diag/diagnostic.gb /roms/gb/
cat > /opt/system/Tools/PortMaster/control.txt << 'EOF'
directory="roms"
get_controls() { sdl_controllerconfig="190000004b4800000010000001010000,GO-Super Gamepad,a:b1,b:b0,x:b2,y:b3"; }
GPTOKEYB="/opt/system/Tools/PortMaster/gptokeyb"
ESUDO=""
CFW_NAME=ArkOS
pm_finish() { :; }
EOF
printf '#!/bin/sh\necho "gptokeyb $*" > /tmp/gptokeyb.ran\n' > /opt/system/Tools/PortMaster/gptokeyb
chmod +x /opt/system/Tools/PortMaster/gptokeyb
export SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy SDL_RENDER_DRIVER=software POCKET_WINDOW=640x480

echo "glibc $(ldd --version | head -1 | grep -oE '[0-9]+\.[0-9]+$'), SDL $(basename "$(readlink -f /usr/lib/aarch64-linux-gnu/libSDL2-2.0.so.0)")"
timeout 8 bash /roms/ports/PokeDaisy.sh > /dev/null 2>&1
log=/roms/ports/pokedaisy/log.txt
check "the launcher opens the game list" "grep -q 'Video backend' $log 2> /dev/null"
check "the launcher makes roms / saves / states" "[ -d /roms/ports/pokedaisy/roms ] && [ -d /roms/ports/pokedaisy/saves ] && [ -d /roms/ports/pokedaisy/states ]"
check "the launcher starts gptokeyb" "grep -q 'gptokeyb pokedaisy-linux' /tmp/gptokeyb.ran 2> /dev/null"
cd /roms/ports/pokedaisy || exit 1
for rom in /roms/gba/diagnostic.gba /roms/gb/diagnostic.gb; do
  out=$(./pokedaisy-linux --data /roms/ports/pokedaisy "$rom" --frames 120 --shot /tmp/shot.png 2>&1)
  code=$?
  check "$(basename "$rom") runs 120 frames" "[ $code = 0 ] && [ -s /tmp/shot.png ]"
  [ $code = 0 ] || echo "$out" | tail -3 | sed 's/^/    /'
  rm -f /tmp/shot.png
done
check "the GB game's battery save is written" "ls /roms/ports/pokedaisy/saves | grep -q '^diagnostic'"
exit $fail
