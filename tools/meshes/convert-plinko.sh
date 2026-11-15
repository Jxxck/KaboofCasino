#!/bin/bash
# Re-creates pack-src/meshes/plinko_*.json from the decompiled MineVegas source (Vineflower output, text only - the
# mod's code is never run). mesh2json.py converts every 23-float quad into one model element and re-bakes it with a
# Python port of 26.3 FaceBakery to prove positions and UVs match (0 unmatched expected).
# Offsets: every mesh is drawn by PlinkoRenderer.quads()/flat() at (x, y, z) + mesh coords, so its own origin -> +8,8,8
# (model centre) and the display translation is the renderer's (x, y, z) in board px:
#   ball  origin = ball centre,  at PlinkoDrop.Ball.position() / chute (24, 38.5), z 12
#   lamp  origin = lamp centre,  at PINS[2k], PINS[2k+1], z 13.5 (LAMP_Z)
#   bulb  origin = back-face centre (mesh z 0..0.5), at BULBS[2k], BULBS[2k+1], z 11.5 (BULB_Z)
#   rim / glow: no array in the mod - PlinkoRenderer.flat() builds them from PLATE_HALF/PLATE_BOTTOM/PLATE_TOP/RIM and
#   RIM_UV/GLOW_UV. They are rebuilt here as 23-float quads around the pocket-plate centre (pocketX(k), 12.5) with
#   z 0 = RIM_Z 14.1125 / GLOW_Z 14.0925, in flat()'s vertex/UV order. The normal is written as (0,0,1) (the face
#   direction); the mod passes (0,1,0) only as a lighting normal.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:?usage: convert-plinko.sh <path to the decompiled fr/minevegas/casino/PlinkoRenderGeometry.java>}
OUT=$HERE/../../pack-src/meshes
TEX=roulettecasino:block/casino_entity/plinko
PY="python3 -I -B"
$PY "$HERE/mesh2json.py" "$SRC" Ball.DATA "$TEX" 8,8,8 "$OUT/plinko_ball.json"
$PY "$HERE/mesh2json.py" "$SRC" Lamp.DATA "$TEX" 8,8,8 "$OUT/plinko_lamp.json"
$PY "$HERE/mesh2json.py" "$SRC" Bulb.DATA "$TEX" 8,8,8 "$OUT/plinko_bulb.json"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
$PY - "$SRC" "$TMP/PlinkoPocketFlats.java" <<'EOF'
import re, sys
src = open(sys.argv[1]).read()
def num(name): return float(re.search(r'double\s+%s\s*=\s*(-?[\d.]+)' % name, src).group(1))
def uv(name): return [float(x.rstrip('F')) for x in re.search(r'float\[\]\s+%s\s*=\s*new float\[\]\{([^}]*)\}' % name, src).group(1).replace(' ', '').split(',')]
half, rim, bottom, top = num('PLATE_HALF'), num('RIM'), num('PLATE_BOTTOM'), num('PLATE_TOP')
cy = (bottom + top) / 2
def flat(x0, y0, x1, y1, u):  # PlinkoRenderer.flat(): (x0,y1) (x0,y0) (x1,y0) (x1,y1), uv (0,1) (0,3) (2,3) (2,1)
    y0 -= cy; y1 -= cy
    q = [x0, y1, 0, u[0], u[1], x0, y0, 0, u[0], u[3], x1, y0, 0, u[2], u[3], x1, y1, 0, u[2], u[1], 0, 0, 1]
    return ', '.join('%rF' % float(v) for v in q)
r = uv('RIM_UV'); g = uv('GLOW_UV')
rims = [flat(-half, bottom, -half + rim, top, r), flat(half - rim, bottom, half, top, r),
        flat(-half + rim, top - rim, half - rim, top, r), flat(-half + rim, bottom, half - rim, bottom + rim, r)]
glow = [flat(-half, bottom, half, top, g)]
with open(sys.argv[2], 'w') as f:
    f.write('final class Rim {\n   static final float[] DATA = new float[]{%s};\n}\n' % ', '.join(rims))
    f.write('final class Glow {\n   static final float[] DATA = new float[]{%s};\n}\n' % ', '.join(glow))
EOF
$PY "$HERE/mesh2json.py" "$TMP/PlinkoPocketFlats.java" Rim.DATA "$TEX" 8,8,8 "$OUT/plinko_rim.json"
$PY "$HERE/mesh2json.py" "$TMP/PlinkoPocketFlats.java" Glow.DATA "$TEX" 8,8,8 "$OUT/plinko_glow.json"
