#!/usr/bin/env python3
"""datsum.py <Name>: inventory item counts and the casino ledger from the player's .dat of the default test instance
(read only, through PostMarkt/testing/lib/nbt.py). Run with python3 -I."""
import json, subprocess, sys
name = sys.argv[1]
out = subprocess.run(['/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing/ptest.sh', 'dat', name, '--full'],
                     capture_output=True, text=True).stdout
start = out.index('\n{') + 1
tag = json.loads(out[start:])
inv = {}
for s in tag.get('Inventory', []):
    inv[s['id']] = inv.get(s['id'], 0) + s.get('count', 1)
ledger = (tag.get('BukkitValues') or {}).get('kaboofcasino:ledger')
summary = {'inventory': dict(sorted(inv.items())), 'ledger': ledger}
print(json.dumps(summary, ensure_ascii=False, default=str))
