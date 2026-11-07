#!/usr/bin/env python3
"""Self-test of mushroom_census.py on a synthetic world (written to a temporary directory, nothing else is touched).

Run: python3 -I -B tools/census/test_mushroom_census.py
"""
import gzip
import math
import os
import struct
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mushroom_census as census  # noqa: E402

FACES = ('down', 'east', 'north', 'south', 'up', 'west')


# ---- minimal NBT writer ------------------------------------------------------------------------------------------
def _str(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _tag_type(v):
    if isinstance(v, dict):
        return 10
    if isinstance(v, str):
        return 8
    if isinstance(v, bool) or isinstance(v, int):
        return 3
    if isinstance(v, LongArray):
        return 12
    if isinstance(v, list):
        return 9
    raise TypeError(type(v))


class LongArray(list):
    pass


def _payload(v):
    t = _tag_type(v)
    if t == 10:
        out = b''
        for k, x in v.items():
            out += bytes([_tag_type(x)]) + _str(k) + _payload(x)
        return out + b'\x00'
    if t == 8:
        return _str(v)
    if t == 3:
        return struct.pack('>i', int(v))
    if t == 12:
        return struct.pack('>i', len(v)) + b''.join(struct.pack('>q', x) for x in v)
    if t == 9:
        if not v:
            return bytes([0]) + struct.pack('>i', 0)
        types = {_tag_type(x) for x in v}
        if len(types) > 1:  # 26.x heterogeneous list: every element wrapped as {"": value}
            v = [x if isinstance(x, dict) else {'': x} for x in v]
            types = {10}
        return bytes([types.pop()]) + struct.pack('>i', len(v)) + b''.join(_payload(x) for x in v)
    raise TypeError(t)


def nbt(root):
    return b'\x0a' + _str('') + _payload(root)


def pack_indices(indices, palette_len):
    bits = max(4, math.ceil(math.log2(palette_len)))
    per = 64 // bits
    longs = [0] * ((4096 + per - 1) // per)
    for n, k in enumerate(indices):
        longs[n // per] |= k << ((n % per) * bits)
    return LongArray([x - (1 << 64) if x >= (1 << 63) else x for x in longs])


def state(block, **props):
    full = {f: props.get(f, 'true') for f in FACES}
    return {'id': 'minecraft:' + block, 'properties': full}


RESERVED = 'minecraft:red_mushroom_block[down=true,east=false,north=false,south=false,up=false,west=false]'
NATURAL = dict(down='false', east='false', north='true', south='false', up='true', west='false')


def write_region(path, chunks, external=None):
    """chunks: {(local x, local z): bytes payload (already compressed incl. type byte) or 'BADOFFSET'}"""
    header = bytearray(8192)
    body = bytearray()
    sector = 2
    for (lx, lz), payload in chunks.items():
        i = lx + lz * 32
        if payload == 'BADOFFSET':
            header[i * 4:i * 4 + 4] = (500).to_bytes(3, 'big') + bytes([1])
            continue
        blob = struct.pack('>I', len(payload)) + payload
        n = (len(blob) + 4095) // 4096
        blob += b'\x00' * (n * 4096 - len(blob))
        header[i * 4:i * 4 + 4] = sector.to_bytes(3, 'big') + bytes([n])
        body += blob
        sector += n
    with open(path, 'wb') as f:
        f.write(bytes(header) + bytes(body))
    for name, data in (external or {}).items():
        with open(os.path.join(os.path.dirname(path), name), 'wb') as f:
            f.write(data)


def chunk(cx, cz, sections):
    return {'DataVersion': 5023, 'xPos': cx, 'zPos': cz, 'Status': 'minecraft:full', 'sections': sections}


class CensusTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        w = self.tmp.name
        over = os.path.join(w, 'dimensions', 'minecraft', 'overworld', 'region')
        nether = os.path.join(w, 'dimensions', 'minecraft', 'the_nether', 'region')
        os.makedirs(over)
        os.makedirs(nether)
        # chunk 0,0: one reserved red state at index 0 of section y=4 (block 0,64,0) and at the last index (15,79,15),
        # 10 natural caps; a whole section of natural stems at y=-1
        pal = ['minecraft:air', state('red_mushroom_block', down='true', east='false', north='false', south='false',
                                      up='false', west='false'), state('red_mushroom_block', **NATURAL)]
        idx = [0] * 4096
        idx[0] = 1
        idx[4095] = 1
        for n in range(100, 110):
            idx[n] = 2
        sec4 = {'Y': 4, 'block_states': {'palette': pal, 'data': pack_indices(idx, len(pal))}}
        stem = state('mushroom_stem', down='false', up='false')
        secm1 = {'Y': -1, 'block_states': {'palette': [stem]}}
        c00 = b'\x02' + zlib.compress(nbt(chunk(0, 0, [sec4, secm1])))
        # chunk 1,0: 26.x palette with a bare default state (all-true, vanilla placement), gzip compressed
        pal2 = ['minecraft:stone', 'minecraft:brown_mushroom_block']
        idx2 = [0] * 4096
        idx2[5] = 1
        c10 = b'\x01' + gzip.compress(nbt(chunk(1, 0, [{'Y': 0, 'block_states': {
            'palette': pal2, 'data': pack_indices(idx2, len(pal2))}}])))
        # chunk 2,0: broken zlib stream (a chunk half written while we read)
        c20 = b'\x02' + zlib.compress(nbt(chunk(2, 0, [])))[:-6] + b'\x00' * 6
        # chunk 3,0: no mushrooms at all
        c30 = b'\x02' + zlib.compress(nbt(chunk(3, 0, [{'Y': 0, 'block_states': {'palette': ['minecraft:stone']}}])))
        # chunk 4,0: external .mcc (oversized chunk), one reserved state
        ext = zlib.compress(nbt(chunk(4, 0, [{'Y': 1, 'block_states': {'palette': [
            'minecraft:air', state('red_mushroom_block', down='true', east='false', north='false', south='false',
                                   up='false', west='false')], 'data': pack_indices([1] + [0] * 4095, 2)}}])))
        c40 = bytes([2 | 128])
        write_region(os.path.join(over, 'r.0.0.mca'),
                     {(0, 0): c00, (1, 0): c10, (2, 0): c20, (3, 0): c30, (4, 0): c40, (5, 0): 'BADOFFSET'},
                     external={'c.4.0.mcc': ext})
        open(os.path.join(nether, 'r.0.0.mca'), 'wb').close()  # empty file: no chunks, no error
        self.chosen = os.path.join(w, 'chosen.txt')
        with open(self.chosen, 'w') as f:
            f.write('# comment\n' + RESERVED + ' # casinodeco:dark_oak_paneling\n')
        self.world = w

    def tearDown(self):
        self.tmp.cleanup()

    def test_counts_positions_and_unreadable_chunks(self):
        out = census.census(self.world, census.load_chosen(self.chosen), 1)
        o = out['dimensions']['minecraft:overworld']
        self.assertEqual(6, o['chunks'])
        self.assertEqual(3, o['chunks_parsed_with_mushroom_bytes'])
        self.assertEqual(2, o['unreadable_chunks'])
        reasons = sorted(u['reason'].split(':')[0] for u in o['unreadable'])
        self.assertEqual(['ValueError', 'error'], reasons)
        self.assertEqual({(2, 0), (5, 0)}, {tuple(u['chunk']) for u in o['unreadable']})
        natural = census.state_key('minecraft:red_mushroom_block', NATURAL)
        stem = census.state_key('minecraft:mushroom_stem', {'down': 'false', 'up': 'false'})
        default = 'minecraft:brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=true]'
        self.assertEqual({RESERVED: 3, natural: 10, stem: 4096, default: 1}, o['states'])
        self.assertEqual({RESERVED: 3}, o['down_true_non_default'])
        self.assertEqual(3, o['chosen'][RESERVED]['count'])
        self.assertIn([0, 64, 0], o['chosen'][RESERVED]['positions'])
        self.assertIn([15, 79, 15], o['chosen'][RESERVED]['positions'])
        self.assertIn([64, 16, 0], o['chosen'][RESERVED]['positions'])
        n = out['dimensions']['minecraft:the_nether']
        self.assertEqual(0, n['chunks'])
        self.assertEqual(0, n['unreadable_chunks'])

    def test_palette_forms(self):
        self.assertEqual(('minecraft:stone', {}), census.palette_entry('minecraft:stone'))
        self.assertEqual(('minecraft:stone', {}), census.palette_entry({'': 'minecraft:stone'}))
        self.assertEqual(('minecraft:x', {'a': 'b'}), census.palette_entry({'Name': 'minecraft:x',
                                                                            'Properties': {'a': 'b'}}))
        self.assertEqual('minecraft:mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]',
                         census.state_key('minecraft:mushroom_stem', {}))


if __name__ == '__main__':
    unittest.main()
