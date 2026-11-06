#!/bin/bash
# Re-creates pack-src/meshes/wheel_*.json from the decompiled MineVegas source (Vineflower output, text only - the
# mod's code is never run). mesh2json.py converts every 23-float quad into one model element and re-bakes it with a
# Python port of 26.3 FaceBakery to prove positions and UVs match (0 unmatched expected).
# The fortune wheel has one 23-float quad array: the pennant/flapper (Pennant.DATA, 52 quads). The plate (one quad
# per blur level) and the 16 bulbs are built in FortuneWheelRenderer code, not stored as quad arrays.
# Offset: pennant in hinge-local px (the renderer translates to HINGE (8,30,0) px, then rotates about Z) -> +8,8,8
# (hinge = model centre, the display rotates about it; model z - 8 = wheel-local z, i.e. 12.5625..14.0625).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:-/root/Jack_DONT_TOUCH/plugins-src/Casino-work/mod/src/fr/minevegas/casino/FortuneWheelRenderGeometry.java}
OUT=$HERE/../../pack-src/meshes
TEX=roulettecasino:block/casino_entity/fortune_wheel
python3 -I "$HERE/mesh2json.py" "$SRC" Pennant.DATA "$TEX" 8,8,8 "$OUT/wheel_pennant.json"
