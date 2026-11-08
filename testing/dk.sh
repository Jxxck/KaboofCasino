#!/bin/bash
# dk.sh: duck derby bot helpers (derby #1 at 81 -57 58 facing SOUTH; bets: lane slots row1 9/12/14/17)
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing; P=$T/ptest.sh
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
LANE=(9 12 14 17)
open() { $P b $1 hotbar 8; $P bx $1 5 'OPEN id' place ${2:-81} -57 ${3:-58} south | grep -o "OPEN id=[0-9]*"; }
bet() { $P b $1 click ${LANE[$2]}; sleep 0.25; for i in $(seq 1 $3); do $P b $1 click ${4:-81} right; sleep 0.2; done; }
group() { $P b $1 click 42; }
solo() { $P b $1 click 37; }
withdraw() { $P b $1 click 42; }
ledger() { $P rcon "save-all flush" >/dev/null; sleep 1; python3 -I -B /root/Jack_DONT_TOUCH/plugins-src/KaboofCasino/testing/datsum.py $1 | python3 -I -B -c "import json,sys; d=json.load(sys.stdin); print('$1', d['inventory'], 'owed', (d['ledger'] or {}).get('kaboofcasino:owed')); [print('   ', r['kaboofcasino:state'], r['kaboofcasino:stake']['kaboofcasino:count'], r['kaboofcasino:stake']['kaboofcasino:item'], [p['kaboofcasino:count'] for p in r['kaboofcasino:payout']], r['kaboofcasino:detail']) for r in (d['ledger'] or {}).get('kaboofcasino:rounds',[])]"; }
nover() { grep -c 'race over' $L; }
over() { local n0=$1; timeout ${2:-40} bash -c "until [ \$(grep -c 'race over' $L) -gt $n0 ]; do sleep 0.5; done"; grep 'race over' $L | tail -1 | cut -c1-120; }
"$@"
