#!/bin/bash
# Re-creates pack-src/meshes/derby_{duck,gate}.json from the decompiled MineVegas source (Vineflower output, text only -
# the mod's code is never run). mesh2json.py converts every 23-float quad into one model element and re-bakes it with a
# Python port of 26.3 FaceBakery to prove positions and UVs match (0 unmatched expected).
# DuckDerbyRenderer.quads() draws a vertex at (data + offset) / 16 under the pose:
#   duck: translate((LANE_CENTERS[lane] - 24, 13 + lift, z - 56) / 16), rotationX(pitch), offset (0, -0.5, 0) and the
#         lane's UV offset LANE_UV (added by the pack tool, DerbyPack) -> mesh offset +8,+7.5,+8: the duck's pivot
#         (lane centre, 13 + lift, z) is the model centre (8, 8, 8); the display turns it about X by the pitch.
#   gate: offset (GATE_X[lane] - 24, -sink, -56) from the anchor's corner + (0.5, 0, 0.5): the mesh (x 0..10, y 15..16,
#         z 51..52) is moved by -16,-7.5,-43.5 so that machine px (24, 15.5, 51.5) is the model centre; the pack tool
#         places one copy per lane at +GATE_X (x -15..31) in one model, the display sinks it by 3.6 px x gate.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:-/root/Jack_DONT_TOUCH/plugins-src/Casino-work/mod/src/fr/minevegas/casino/DuckDerbyGeometry.java}
OUT=$HERE/../../pack-src/meshes
TEX=roulettecasino:block/casino_entity/duck_derby
PY="python3 -I -B"
$PY "$HERE/mesh2json.py" "$SRC" DuckDerbyGeometry.DUCK "$TEX" 8,7.5,8 "$OUT/derby_duck.json"
$PY "$HERE/mesh2json.py" "$SRC" DuckDerbyGeometry.GATE "$TEX" -16,-7.5,-43.5 "$OUT/derby_gate.json"
