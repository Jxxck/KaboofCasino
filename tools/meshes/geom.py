#!/usr/bin/env python3
"""Extract float[]/double[] literal arrays from Vineflower output and analyse 23-float quads.
Usage: geom.py <java file> [--quads NAME ...]"""
import re, sys, math, json
src = open(sys.argv[1]).read()
# track nested class names to qualify arrays
arrays = {}
cls_stack = []
pat = re.compile(r'(?:static\s+)?(?:final\s+)?(float|double)\[\]\s+(\w+)\s*=\s*new\s+(?:float|double)\[\]\s*\{', re.S)
for m in pat.finditer(src):
    # find owning nested class: last "class X {" before m.start()
    pre = src[:m.start()]
    cm = list(re.finditer(r'class\s+(\w+)', pre))
    owner = cm[-1].group(1) if cm else '?'
    start = m.end()
    end = src.index('}', start)
    body = src[start:end]
    vals = [float(x.rstrip('FfDd')) for x in re.findall(r'-?\d+(?:\.\d+)?(?:[eE]-?\d+)?[FfDd]?', body)]
    arrays[owner + '.' + m.group(2)] = vals
def quads(data):
    out = []
    for q in range(0, len(data) - 22, 23):
        v = [(data[q+k*5], data[q+k*5+1], data[q+k*5+2], data[q+k*5+3], data[q+k*5+4]) for k in range(4)]
        n = data[q+20:q+23]
        out.append((v, n))
    return out
def sub(a,b): return [a[i]-b[i] for i in range(3)]
def dot(a,b): return sum(a[i]*b[i] for i in range(3))
def norm(a): return math.sqrt(dot(a,a))
def classify(v):
    p = [x[:3] for x in v]
    e = [sub(p[(i+1)%4], p[i]) for i in range(4)]
    L = [norm(x) for x in e]
    if min(L) < 1e-6: return 'degenerate'
    rect = all(abs(dot(e[i], e[(i+1)%4]))/(L[i]*L[(i+1)%4]) < 1e-3 for i in range(4))
    axis = all(sum(1 for c in x if abs(c) > 1e-6) == 1 for x in e)
    if rect and axis: return 'axis-rect'
    if rect: return 'rotated-rect'
    para = abs(L[0]-L[2]) < 1e-4 and abs(L[1]-L[3]) < 1e-4
    return 'parallelogram' if para else 'other(trapezoid/tri)'
if __name__ == '__main__':
    for k, v in arrays.items():
        line = f'{k}: {len(v)} values'
        if len(v) % 23 == 0 and len(v) >= 23:
            qs = quads(v)
            from collections import Counter
            c = Counter(classify(x[0]) for x in qs)
            xs = [p[0] for x in qs for p in x[0]]; ys = [p[1] for x in qs for p in x[0]]; zs = [p[2] for x in qs for p in x[0]]
            line += f' -> {len(qs)} quads {dict(c)} bbox x[{min(xs):.2f},{max(xs):.2f}] y[{min(ys):.2f},{max(ys):.2f}] z[{min(zs):.2f},{max(zs):.2f}]'
        print(line)
