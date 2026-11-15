#!/bin/bash
# croupier-restock-test.sh: vanilla restock timing for a croupier standing at 137 -57 66 (counter 138 -57 66, made
# with croupier-test.sh wait); bot Alice online, gamerule advance_time false (the test instance), about 6 minutes.
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh; BL=$T/out/bots/Alice.log
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
q() { $P rcon "$@" >/dev/null; }
b() { $P b Alice "$@"; sleep 0.35; }
ts() { date -u +%T; }
gt() { $P rcon "time query gametime" | grep -o '[0-9]* tick' | grep -o '[0-9]*'; }
eid() { local m; m=$(wc -l < "$BL"); b entities; sleep 0.3
  tail -n +"$m" "$BL" | grep -E "ENTITY id=[0-9]+ type=interaction at 137.5" | tail -1 | grep -oE "id=[0-9]+" | cut -d= -f2; }
trade() { q "give Alice emerald 24"; sleep 0.3; b hotbar 3; local id; id=$(eid); b rclick "$id"; sleep 0.8
  b selecttrade 0; sleep 0.4; b click 2 shift; sleep 0.5; b close; sleep 0.3
  echo "$(ts) gt=$(gt) traded one card; emeralds left $($P rcon 'clear Alice emerald 0' | grep -oE 'Found [0-9]+|No items')"; q "clear Alice paper"; }
restocks() { grep -c "restocked (" "$L"; }
waitfor() { # waitfor <n0> <sec>: until a new restock line or timeout; prints it or "none"
  if timeout "$2" bash -c "until [ \$(grep -c 'restocked (' $L) -gt $1 ]; do sleep 0.5; done"; then
    echo "$(ts) gt=$(gt) $(grep 'restocked (' $L | tail -1 | sed -E 's/.*Croupier [0-9a-f-]+ at [^)]*\) //')"
  else echo "$(ts) gt=$(gt) no restock within $2 s"; fi; }
q "clear Alice" "gamemode survival Alice" "tp Alice 140.5 -57 66.5 90 10" "time set 27000"
sleep 1
echo "$(ts) time: $($P rcon 'time query day' | tail -1)"
n=$(restocks); trade; echo "restock #1 (first work check, none today):"; waitfor "$n" 20
n=$(restocks); trade; t0=$(date +%s); echo "restock #2 not before 2400 ticks after #1:"; waitfor "$n" 60
echo "... still waiting:"; waitfor "$n" 100
n=$(restocks); trade; echo "restock #3 the same day: never (waiting 2400 + 300 ticks):"; waitfor "$n" 140
q "time set 49000"; echo "$(ts) day 2, time of day $($P rcon 'time query day' | tail -1 | grep -oE '[0-9]+ tick') (before work): "; waitfor "$n" 20
q "time set 51000"; echo "$(ts) day 2, time of day 3000 (work): the count reset, restock:"; waitfor "$n" 20
