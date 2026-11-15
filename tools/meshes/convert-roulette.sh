#!/bin/bash
# Re-creates pack-src/meshes/roulette_*.json from the decompiled MineVegas source (Vineflower output, text only - the
# mod's code is never run). mesh2json.py converts every 23-float quad into one model element and re-bakes it with a
# Python port of 26.3 FaceBakery to prove positions and UVs match (0 unmatched expected).
# The roulette has two 23-float quad arrays, both drawn by RouletteBetRenderer.submitWheel with roulette_wheel.png
# (256x272, entityCutout, block light of the table):
#   rotor = Rotor0..Rotor20.DATA joined (1808 quads, 216 of them rotated about Y). 'Rotor*' takes them in name order
#           (Rotor0, Rotor1, Rotor10, ...); the order does not matter, no two axis-aligned faces share a plane area.
#   ball  = Ball0.DATA (32 quads, a 1 px ball, mesh coords -0.5..0.5).
# Offsets: both meshes are in pivot-local px -> +8,8,8 (pivot = model centre, the display rotates about it):
#   rotor: the renderer translates to (1, 0, 1) block = the table centre at the table's bottom and rotates about Y
#          there, so the rotation axis is mesh x=0,z=0 -> model x=8,z=8; model y - 8 = px above the table bottom
#          (rotor y 11.5..15.75 -> model 19.5..23.75). Display at origin + (1, 0, 1), left_rotation about Y.
#   ball:  mesh origin = ball centre (the renderer rotates it about its origin) -> model 8,8,8.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:?usage: convert-roulette.sh <path to the decompiled fr/minevegas/casino/RouletteRenderGeometry.java>}
OUT=$HERE/../../pack-src/meshes
TEX=roulettecasino:block/casino_entity/roulette_wheel
PY="python3 -I -B"
$PY "$HERE/mesh2json.py" "$SRC" 'Rotor*' "$TEX" 8,8,8 "$OUT/roulette_rotor.json"
$PY "$HERE/mesh2json.py" "$SRC" Ball0.DATA "$TEX" 8,8,8 "$OUT/roulette_ball.json"
