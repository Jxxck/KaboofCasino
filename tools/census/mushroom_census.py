#!/usr/bin/env python3
"""Read-only census of mushroom-block states in a Minecraft 26.x world (KaboofCasino full-block deco, step 4).

KaboofCasino draws its full-cube deco blocks on reserved states of red_mushroom_block, brown_mushroom_block and
mushroom_stem (all with down=true, never the all-true default). Such a state already present in the world would show
as a casino block once the pack is active. This tool counts every mushroom-block state in every region file so that
can be ruled out (or the positions listed) before the deco goes live.

Usage (python only as `python3 -I -B`; wrap long runs in the usual systemd-run scope, MemoryMax=2G):

    python3 -I -B tools/census/mushroom_census.py --world /path/to/server/world \\
        --chosen tools/census/chosen-states.txt --out census.json [--workers 4]

  --world    the world folder; every <world>/dimensions/<ns>/<dim>/region/*.mca is read
  --chosen   the reserved states (one `minecraft:<block>[k=v,...]` per line, '#' comments), the plugin's state table
             (DecoFullStatesTest keeps the file equal to FullDecoStates)
  --out      JSON result: per dimension the chunk counts, every mushroom state with its count, the chosen states with
             count and up to 50 block positions each, and every chunk that could not be read with the reason
  --workers  parallel processes (default 4)

Safety: region files are opened read-only ('rb', one read() of the whole file, no locks, no seeks into a live
handle, nothing is ever written next to the world). A server that writes a region file while it is being read can
leave a chunk half-written in the copy we read: that chunk fails its zlib/gzip checksum or its NBT parse and is
reported under "unreadable" with region file, chunk coordinates and reason - never guessed. Chunks whose
decompressed bytes do not contain "mushroom_" cannot hold a mushroom block and are not NBT-parsed (their integrity is
proven by the zlib/gzip checksum). External chunks (.mcc, compression type | 128) are read the same way.

When: on a running server the 5-minute autosave rewrites the region files of loaded chunks (spawn area, players), so
run it right after an autosave (newest .mca mtime) - a scan takes about 30 s - and check that no .mca changed during
the scan. A file whose mtime or size changes while it is being read is read again (up to 3 times, listed under
"reread_files").

Palette formats: 26.x writes a block-state palette entry as a bare string for the block's default state and as
{id, properties} otherwise (inside a heterogeneous list every element is wrapped as {"": value}); older chunks use
{Name, Properties}. A missing mushroom property is read as its default (true), so a bare name is the all-true state.
"""
import argparse
import gzip
import json
import math
import os
import struct
import sys
import time
import zlib
from collections import Counter
from multiprocessing import Pool

BLOCKS = ('minecraft:red_mushroom_block', 'minecraft:brown_mushroom_block', 'minecraft:mushroom_stem')
FACES = ('down', 'east', 'north', 'south', 'up', 'west')  # alphabetical = the order of a state key
NEEDLE = b'mushroom_'
MAX_POSITIONS = 50


class Nbt:
    """Minimal big-endian NBT reader (enough for chunk data)."""
    __slots__ = ('d', 'i')

    def __init__(self, data):
        self.d = data
        self.i = 0

    def u8(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def i32(self):
        v, = struct.unpack_from('>i', self.d, self.i)
        self.i += 4
        return v

    def string(self):
        n, = struct.unpack_from('>H', self.d, self.i)
        self.i += 2
        v = self.d[self.i:self.i + n].decode('utf-8', 'replace')
        self.i += n
        return v

    def payload(self, t):
        d = self.d
        if t == 1:
            v, = struct.unpack_from('>b', d, self.i); self.i += 1; return v
        if t == 2:
            v, = struct.unpack_from('>h', d, self.i); self.i += 2; return v
        if t == 3:
            return self.i32()
        if t == 4:
            v, = struct.unpack_from('>q', d, self.i); self.i += 8; return v
        if t == 5:
            v, = struct.unpack_from('>f', d, self.i); self.i += 4; return v
        if t == 6:
            v, = struct.unpack_from('>d', d, self.i); self.i += 8; return v
        if t == 7:
            n = self.i32()
            if n < 0 or self.i + n > len(d):
                raise ValueError('byte array out of range')
            self.i += n
            return None
        if t == 8:
            return self.string()
        if t == 9:
            it = self.u8()
            n = self.i32()
            if n < 0:
                raise ValueError('negative list length')
            return [self.payload(it) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = self.u8()
                if tt == 0:
                    return out
                k = self.string()
                out[k] = self.payload(tt)
        if t == 11:
            n = self.i32()
            v = struct.unpack_from('>%di' % n, d, self.i); self.i += 4 * n; return v
        if t == 12:
            n = self.i32()
            v = struct.unpack_from('>%dq' % n, d, self.i); self.i += 8 * n; return v
        raise ValueError('unknown tag type %d at %d' % (t, self.i))

    def root(self):
        t = self.u8()
        if t != 10:
            raise ValueError('root is tag %d, not a compound' % t)
        self.string()
        return self.payload(10)


def palette_entry(e):
    """(block id, {property: value}) of a palette entry in any of the known encodings."""
    if isinstance(e, str):
        return e, {}
    if isinstance(e, dict):
        if '' in e and len(e) == 1:
            return palette_entry(e[''])
        name = e.get('id', e.get('Name'))
        props = e.get('properties', e.get('Properties')) or {}
        if isinstance(name, str):
            return name, {str(k): str(v) for k, v in props.items()}
    raise ValueError('unknown palette entry %r' % (e,))


def state_key(name, props):
    """Canonical key: all six faces, missing ones at their default (true)."""
    faces = {f: props.get(f, 'true') for f in FACES}
    return name + '[' + ','.join('%s=%s' % (f, faces[f]) for f in FACES) + ']'


def raw_shape(e):
    if isinstance(e, str):
        return 'bare-string'
    if isinstance(e, dict) and '' in e and len(e) == 1:
        return 'wrapped:' + raw_shape(e[''])
    if isinstance(e, dict):
        props = e.get('properties', e.get('Properties'))
        return 'compound(%s)' % ('props=' + str(len(props)) if props else 'no props')
    return type(e).__name__


def count_sections(root, cx, cz, counts, positions, shapes):
    sections = root.get('sections', root.get('Sections')) or []
    for sec in sections:
        bs = sec.get('block_states')
        if not bs:
            continue
        pal = bs.get('palette') or []
        wanted = {}
        for idx, e in enumerate(pal):
            name, props = palette_entry(e)
            if name in BLOCKS:
                wanted[idx] = state_key(name, props)
                shapes[raw_shape(e)] += 1
        if not wanted:
            continue
        sy = sec.get('Y', sec.get('y'))
        if len(pal) == 1:
            key = wanted[0]
            counts[key] += 4096
            if needs_position(key):
                lst = positions.setdefault(key, [])
                if len(lst) < MAX_POSITIONS:
                    lst.append([cx * 16, sy * 16, cz * 16, 'whole section'])
            continue
        data = bs.get('data')
        if data is None:
            raise ValueError('section %s: palette of %d without data' % (sy, len(pal)))
        bits = max(4, math.ceil(math.log2(len(pal))))
        per = 64 // bits
        if len(data) != (4096 + per - 1) // per:
            raise ValueError('section %s: %d longs for %d bits' % (sy, len(data), bits))
        mask = (1 << bits) - 1
        n = 0
        for lv in data:
            v = lv & 0xFFFFFFFFFFFFFFFF
            for _ in range(per):
                if n >= 4096:
                    break
                k = v & mask
                if k in wanted:
                    key = wanted[k]
                    counts[key] += 1
                    if needs_position(key):
                        lst = positions.setdefault(key, [])
                        if len(lst) < MAX_POSITIONS:
                            lst.append([cx * 16 + (n & 15), sy * 16 + (n >> 8), cz * 16 + ((n >> 4) & 15)])
                elif k >= len(pal):
                    raise ValueError('section %s: palette index %d of %d' % (sy, k, len(pal)))
                v >>= bits
                n += 1


def needs_position(key):
    """down=true states other than the all-true default: the reserved range."""
    return 'down=true' in key and 'false' in key


def decompress(ct, raw):
    if ct == 2:
        return zlib.decompress(raw)
    if ct == 1:
        return gzip.decompress(raw)
    if ct == 3:
        return raw
    raise ValueError('compression type %d' % ct)


def read_file(path):
    with open(path, 'rb') as f:
        return f.read()


def stamp(path):
    st = os.stat(path)
    return st.st_mtime_ns, st.st_size


def scan_region(path, attempts=3):
    """Counts of one region file; never raises (problems are returned as unreadable entries). A file whose mtime or
    size changed while it was read (the server's autosave) is read again, up to {attempts} times."""
    res = None
    for attempt in range(attempts):
        try:
            before = stamp(path)
        except OSError:
            before = None
        res = scan_region_once(path)
        try:
            after = stamp(path)
        except OSError:
            after = None
        res['attempts'] = attempt + 1
        res['changed_during_read'] = before is None or before != after
        if not res['changed_during_read']:
            return res
    return res


def scan_region_once(path):
    res = {'path': path, 'chunks': 0, 'parsed': 0, 'full': 0, 'counts': Counter(), 'positions': {},
           'unreadable': [], 'shapes': Counter(), 'data_versions': Counter()}
    try:
        buf = read_file(path)
    except OSError as e:
        res['unreadable'].append({'region': os.path.basename(path), 'chunk': None, 'reason': 'open: %s' % e})
        return res
    if len(buf) == 0:
        return res
    if len(buf) < 8192:
        res['unreadable'].append({'region': os.path.basename(path), 'chunk': None,
                                  'reason': 'header truncated (%d bytes)' % len(buf)})
        return res
    name = os.path.basename(path).split('.')
    rx, rz = int(name[1]), int(name[2])
    for i in range(1024):
        loc = int.from_bytes(buf[i * 4:i * 4 + 3], 'big')
        sectors = buf[i * 4 + 3]
        if loc == 0 and sectors == 0:
            continue
        cx, cz = rx * 32 + (i & 31), rz * 32 + (i >> 5)
        res['chunks'] += 1
        try:
            o = loc * 4096
            if loc < 2 or o + 5 > len(buf):
                raise ValueError('offset %d outside the file (%d bytes)' % (o, len(buf)))
            ln = int.from_bytes(buf[o:o + 4], 'big')
            ct = buf[o + 4]
            if ct & 128:
                ext = os.path.join(os.path.dirname(path), 'c.%d.%d.mcc' % (cx, cz))
                raw = read_file(ext)
                ct &= 127
            else:
                if ln < 1 or o + 4 + ln > len(buf) or ln > sectors * 4096:
                    raise ValueError('length %d does not fit (%d sectors, file %d bytes)' % (ln, sectors, len(buf)))
                raw = buf[o + 5:o + 4 + ln]
            data = decompress(ct, raw)
            if b'minecraft:full' in data:
                res['full'] += 1
            if NEEDLE not in data:
                continue
            root = Nbt(data).root()
            res['parsed'] += 1
            dv = root.get('DataVersion')
            res['data_versions'][str(dv)] += 1
            count_sections(root, cx, cz, res['counts'], res['positions'], res['shapes'])
        except Exception as e:  # noqa: BLE001 - every failure is reported per chunk
            res['unreadable'].append({'region': os.path.basename(path), 'chunk': [cx, cz],
                                      'reason': '%s: %s' % (type(e).__name__, e)})
    return res


def region_files(world):
    out = []
    base = os.path.join(world, 'dimensions')
    for ns in sorted(os.listdir(base)):
        for dim in sorted(os.listdir(os.path.join(base, ns))):
            rdir = os.path.join(base, ns, dim, 'region')
            if not os.path.isdir(rdir):
                continue
            for f in sorted(os.listdir(rdir)):
                if f.startswith('r.') and f.endswith('.mca'):
                    out.append((ns + ':' + dim, os.path.join(rdir, f)))
    return out


def load_chosen(path):
    chosen = []
    if not path:
        return chosen
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.split('#', 1)[0].strip()
            if not line:
                continue
            name, rest = line.split('[', 1)
            props = dict(kv.split('=') for kv in rest.rstrip(']').split(','))
            chosen.append(state_key(name, props))
    return chosen


def census(world, chosen, workers):
    files = region_files(world)
    dims = {}
    started = time.time()
    with Pool(workers) as pool:
        for (dim, _), res in zip(files, pool.imap(scan_region, [p for _, p in files], chunksize=2)):
            d = dims.setdefault(dim, {'region_files': 0, 'chunks': 0, 'chunks_parsed': 0, 'full_chunks': 0,
                                      'states': Counter(), 'positions': {}, 'unreadable': [], 'shapes': Counter(),
                                      'data_versions': Counter(), 'reread': []})
            d['region_files'] += 1
            d['chunks'] += res['chunks']
            d['chunks_parsed'] += res['parsed']
            d['full_chunks'] += res['full']
            d['states'].update(res['counts'])
            d['shapes'].update(res['shapes'])
            d['data_versions'].update(res['data_versions'])
            d['unreadable'].extend(res['unreadable'])
            if res['attempts'] > 1 or res['changed_during_read']:
                d['reread'].append({'region': os.path.basename(res['path']), 'attempts': res['attempts'],
                                    'still_changing': res['changed_during_read']})
            for k, v in res['positions'].items():
                lst = d['positions'].setdefault(k, [])
                lst.extend(v[:MAX_POSITIONS - len(lst)])
    out = {'world': world, 'started_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(started)),
           'finished_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime()), 'dimensions': {}}
    for dim, d in dims.items():
        states = d['states']
        out['dimensions'][dim] = {
            'region_files': d['region_files'], 'chunks': d['chunks'], 'full_chunks': d['full_chunks'],
            'chunks_parsed_with_mushroom_bytes': d['chunks_parsed'], 'unreadable_chunks': len(d['unreadable']),
            'unreadable': d['unreadable'], 'reread_files': d['reread'],
            'mushroom_blocks': sum(states.values()),
            'states': dict(sorted(states.items(), key=lambda kv: (-kv[1], kv[0]))),
            'down_true_non_default': {k: v for k, v in sorted(states.items()) if needs_position(k)},
            'chosen': {k: {'count': states.get(k, 0), 'positions': d['positions'].get(k, [])} for k in chosen},
            'palette_entry_shapes': dict(d['shapes']), 'data_versions': dict(d['data_versions']),
        }
    return out


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('--world', required=True)
    ap.add_argument('--chosen')
    ap.add_argument('--out', required=True)
    ap.add_argument('--workers', type=int, default=4)
    a = ap.parse_args(argv)
    out = census(a.world, load_chosen(a.chosen), a.workers)
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(out, f, indent=1)
    print('census %s .. %s UTC' % (out['started_utc'], out['finished_utc']))
    for dim, d in out['dimensions'].items():
        chosen_total = sum(c['count'] for c in d['chosen'].values())
        print('%-22s regions %4d chunks %7d (full %7d) parsed %6d unreadable %3d re-read %d | mushroom blocks %8d '
              'in %3d states | down=true non-default %d | chosen %d' % (
                  dim, d['region_files'], d['chunks'], d['full_chunks'], d['chunks_parsed_with_mushroom_bytes'],
                  d['unreadable_chunks'], len(d['reread_files']), d['mushroom_blocks'], len(d['states']),
                  sum(d['down_true_non_default'].values()), chosen_total))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
