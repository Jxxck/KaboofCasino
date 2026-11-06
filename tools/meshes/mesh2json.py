#!/usr/bin/env python3
"""Convert a MineVegas BER quad mesh (23 floats per quad: 4 x (x,y,z,u,v) in px and 0..1 sheet UV, then a normal)
into a vanilla 26.3 block/item model (JSON elements), and verify the result by re-baking it with a Python port of
26.3 FaceBakery (FaceInfo vertex order, CuboidFace UV corners, Quadrant rotation, SingleAxisRotation about Y).

usage: mesh2json.py <java file> <Class.ARRAY> <texture id> <offset x,y,z px> <out.json>
"""
import sys, json, math, importlib.util
here = __import__('os').path.dirname(__file__)
spec = importlib.util.spec_from_file_location('g', here + '/geom.py')
argv0 = sys.argv[:]
sys.argv = ['geom', argv0[1]]
g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
sys.argv = argv0

# FaceInfo vertex order per direction: tuples of (x from 'min'|'max', y, z)
FACE = {
 'down':  [('min','min','max'),('min','min','min'),('max','min','min'),('max','min','max')],
 'up':    [('min','max','min'),('min','max','max'),('max','max','max'),('max','max','min')],
 'north': [('max','max','min'),('max','min','min'),('min','min','min'),('min','max','min')],
 'south': [('min','max','max'),('min','min','max'),('max','min','max'),('max','max','max')],
 'west':  [('min','max','min'),('min','min','min'),('min','min','max'),('min','max','max')],
 'east':  [('max','max','max'),('max','min','max'),('max','min','min'),('max','max','min')],
}
def vu(uvs, i): return uvs[0] if i in (0, 1) else uvs[2]
def vv(uvs, i): return uvs[1] if i in (0, 3) else uvs[3]

def roty(p, o, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    x, y, z = p[0]-o[0], p[1]-o[1], p[2]-o[2]
    # JOML Matrix4f.rotation(angle, 0,1,0): x' = c*x + s*z ; z' = -s*x + c*z
    return (c*x + s*z + o[0], y + o[1], -s*x + c*z + o[2])

def bake(frm, to, d, uvs, rot, rotation):
    out = []
    for i in range(4):
        sel = FACE[d][i]
        p = tuple((frm if sel[k] == 'min' else to)[k] for k in range(3))
        if rotation: p = roty(p, rotation[0], rotation[1])
        j = (i + rot // 90) % 4
        out.append((p, (vu(uvs, j) / 16.0, vv(uvs, j) / 16.0)))
    return out

def close(a, b, eps=1e-3): return all(abs(x-y) < eps for x, y in zip(a, b))

def match(baked, src):
    used = set()
    for p, uv in baked:
        ok = False
        for k, (q, quv) in enumerate(src):
            if k not in used and close(p, q) and close(uv, quv, 1e-4):
                used.add(k); ok = True; break
        if not ok: return False
    return True

def direction(n):
    ax = max(range(3), key=lambda k: abs(n[k]))
    return [('west','east'),('down','up'),('north','south')][ax][1 if n[ax] > 0 else 0]

def convert(quads, tex, off):
    elements = []; fails = 0; rotated = 0
    for v, n in quads:
        src = [((p[0]+off[0], p[1]+off[1], p[2]+off[2]), (p[3], p[4])) for p in v]
        rotation = None; local = [s[0] for s in src]
        kind = g.classify(v)
        if kind == 'rotated-rect':
            # rotation about Y that makes the quad axis-aligned (all rotated MineVegas quads turn about Y)
            if abs(n[1]) > 0.99:
                e = g.sub(v[1][:3], v[0][:3]); ang = math.degrees(math.atan2(-e[2], e[0]))
            else:
                ang = math.degrees(math.atan2(-n[2], n[0]))
            ang = ((ang + 45) % 90) - 45
            o = (8.0, 8.0, 8.0)
            local = [roty(p, o, -ang) for p in local]
            rotation = (o, ang); rotated += 1
            nn = roty((n[0], n[1], n[2]), (0, 0, 0), -ang)
        else:
            nn = n
        d = direction(nn)
        frm = tuple(min(p[k] for p in local) for k in range(3)); to = tuple(max(p[k] for p in local) for k in range(3))
        us = sorted({round(s[1][0]*16, 5) for s in src}); vs = sorted({round(s[1][1]*16, 5) for s in src})
        found = None
        for u1, u2 in ((us[0], us[-1]), (us[-1], us[0])):
            for v1, v2 in ((vs[0], vs[-1]), (vs[-1], vs[0])):
                for rot in (0, 90, 180, 270):
                    if match(bake(frm, to, d, (u1, v1, u2, v2), rot, rotation), src):
                        found = ((u1, v1, u2, v2), rot); break
                if found: break
            if found: break
        if not found:
            fails += 1; continue
        face = {'uv': [round(x, 4) for x in found[0]], 'texture': '#sheet'}
        if found[1]: face['rotation'] = found[1]
        el = {'from': [round(x, 4) for x in frm], 'to': [round(x, 4) for x in to], 'faces': {d: face}}
        if rotation: el['rotation'] = {'origin': list(rotation[0]), 'axis': 'y', 'angle': round(rotation[1], 4)}
        for c in el['from'] + el['to']:
            if c < -16 or c > 32: raise SystemExit('extent out of range: %s' % el)
        elements.append(el)
    return {'textures': {'sheet': tex, 'particle': tex}, 'elements': elements}, fails, rotated

if __name__ == '__main__':
    java, arr, tex, off, outp = sys.argv[1:6]
    off = tuple(float(x) for x in off.split(','))
    names = [a for a in g.arrays if a == arr or (arr.endswith('*') and a.startswith(arr[:-1]))]
    data = []
    for a in sorted(names): data += g.arrays[a]
    qs = g.quads(data)
    model, fails, rotated = convert(qs, tex, off)
    s = json.dumps(model, separators=(',', ':'))
    open(outp, 'w').write(s)
    print('%s: %d quads -> %d elements (%d rotated about Y), %d unmatched, %d bytes' % (arr, len(qs), len(model['elements']), rotated, fails, len(s)))
