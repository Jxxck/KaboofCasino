#!/bin/bash
# Re-creates pack-src/meshes/slot_*.json from the decompiled MineVegas source (Vineflower output, text only - the
# mod's code is never run). mesh2json.py converts every 23-float quad into one model element and re-bakes it with a
# Python port of 26.3 FaceBakery to prove positions and UVs match (0 unmatched expected).
# Offsets: lever arm/ball in pivot-local px -> +8,8,8 (pivot = model centre, the display rotates about it);
# lamp in top-block coordinates -> y-32.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:?usage: convert-slot.sh <path to the decompiled fr/minevegas/casino/SlotMachineRenderGeometry.java>}
OUT=$HERE/../../pack-src/meshes
TEX=roulettecasino:block/casino_entity/slot_machine
python3 "$HERE/mesh2json.py" "$SRC" Arm.DATA "$TEX" 8,8,8 "$OUT/slot_arm.json"
python3 "$HERE/mesh2json.py" "$SRC" Ball.DATA "$TEX" 8,8,8 "$OUT/slot_ball.json"
python3 "$HERE/mesh2json.py" "$SRC" Lamp.DATA "$TEX" 0,-32,0 "$OUT/slot_lamp.json"
