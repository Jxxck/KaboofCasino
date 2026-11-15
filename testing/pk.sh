#!/bin/bash
# pk.sh: poker bot test helpers on the default PostMarkt test instance (table #1 at 90 -57 41, facing SOUTH)
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
open() { $P b $1 hotbar 8; $P bx $1 5 'OPEN id' place ${2:-90} -57 42 south | grep -o "OPEN.*"; }
deal() { local n0=$(grep -c "deals poker" $L); $P bx $1 5 'OPEN id' click 49 | grep -o "OPEN.*"; timeout 10 bash -c "until [ \$(grep -c 'deals poker' $L) -gt $n0 ]; do sleep 0.2; done"; grep 'deals poker' $L | tail -1; }
decision() { sleep ${1:-11}; }
call() { $P b $1 click 46; }
fold() { $P b $1 click 51; }
ends() { local n0=$1; timeout ${2:-30} bash -c "until [ \$(grep -c 'hand of .* ends' $L) -gt $n0 ]; do sleep 0.3; done"; grep 'hand of .* ends' $L | tail -1; }
nends() { grep -c 'hand of .* ends' $L; }
ledger() { $P rcon "save-all flush" >/dev/null; sleep 1; python3 -I -B "$(dirname "${BASH_SOURCE[0]}")/datsum.py" $1; }
"$@"
