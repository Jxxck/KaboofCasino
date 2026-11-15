#!/bin/bash
# blackjack-mp-test.sh <step>: the shared blackjack table (default test instance, casinobots Alice and Bob online).
# Steps: shared | hit | double | quit | timeout | solo | late
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
S=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
q() { $P rcon "$@" >/dev/null; }
b() { local who=$1; shift; $P b "$who" "$@"; sleep 0.3; }
n() { grep -cE "$1" "$L"; }
chips() { q "clear $1" "gamemode survival $1"; sleep 0.2; q "casino give chip_25 $1 10" "casino give chip_5 $1 10" "casino give chip_1 $1 30" "casino give chip_100 $1 5" "casino give chip_1000 $1 2"; sleep 0.3; }
value() { local s=0; for d in 1 5 25 100 500 1000; do c=$($P rcon "clear $1 paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"kaboofcasino:chip_$d\"}}] 0" | grep -oE 'Found [0-9]+' | grep -oE '[0-9]+'); s=$((s + d * ${c:-0})); done; echo $s; }
at() { q "tp Alice 28.5 -57 8.5 180 0" "tp Bob 31.5 -57 8.5 180 0"; sleep 0.6; }
open() { b "$1" hotbar 8; $P bx "$1" 5 'OPEN id' place 29 -57 6 south | grep -o "OPEN id=[0-9]*" | head -1; sleep 0.3; }
bet() { b "$1" click 82 right; b "$1" click 83 right; b "$1" click 83 right; b "$1" click 49; } # 5 + 1 + 1 = 7
act() { case $2 in stand) b "$1" click 49 ;; hit) b "$1" click 46 ;; double) b "$1" click 52 ;; quit) $P bot stop "$1" | tail -1 ;; none) ;; esac; }
ended() { [ "$(n 'hand of (Alice|Bob) ends')" -ge "$1" ]; }
# play <hands at the end> <Alice's action> <Bob's action>: answers every "turn of" line until all hands ended
play() { local want=$1 aa=$2 ab=$3 seen=$(n "turn of") guard=0 who c
  while ! ended "$want" && [ $guard -lt 400 ]; do guard=$((guard + 1)); c=$(n "turn of")
    if [ "$c" -gt "$seen" ]; then seen=$c; who=$(grep -E "turn of" "$L" | tail -1 | sed -E 's/.*turn of //'); sleep 0.8; echo "  turn: $who"; if [ "$who" = Alice ]; then act Alice "$aa"; aa=stand; else act Bob "$ab"; ab=stand; fi; fi
    sleep 0.25; done; }
show() { grep -E "hand of (Alice|Bob) ends" "$L" | tail -"$1" | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-130; }
round() { local aa=$1 ab=$2; chips Alice; chips Bob; at; a0=$(value Alice); b0=$(value Bob); e0=$(n 'hand of (Alice|Bob) ends'); open Alice; bet Alice; sleep 0.3; echo "Alice after her bet: $(value Alice) (was $a0)"; grep -E "betting window opened" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //'; open Bob; bet Bob; play $((e0 + 2)) "$aa" "$ab"; show 2; sleep 3; }
case ${1:-} in
shared) round stand stand; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob)" ;;
hit) round hit stand; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob)" ;;
double) round double double; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob)"; grep -E "settled \(NORMAL\).*(x2).*result" "$L" | tail -2 | sed -E 's/^.*settled \(NORMAL\): //' | cut -c1-120 ;;
quit) chips Alice; chips Bob; at; a0=$(value Alice); e0=$(n 'hand of (Alice|Bob) ends'); open Alice; bet Alice; open Bob; bet Bob; play $((e0 + 2)) quit stand; show 2; grep -E "left the table" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //'; "$S/casinobot.sh" Alice accept | tail -1; sleep 1; echo "Alice back: $(value Alice) (was $a0, staked 7)"; grep -E "settled \(QUIT\)" "$L" | tail -3 | sed -E 's/^.*settled \(QUIT\): //' | cut -c1-110; sleep 2 ;;
timeout) chips Alice; chips Bob; at; e0=$(n 'hand of (Alice|Bob) ends'); open Alice; bet Alice; open Bob; bet Bob; echo "nobody acts: two 30 s timeouts"; timeout 90 bash -c "until [ \$(grep -cE 'hand of (Alice|Bob) ends' $L) -ge $((e0 + 2)) ]; do sleep 1; done"; show 2; sleep 4 ;;
solo) chips Alice; q "tp Bob 200 -57 200"; sleep 0.5; q "tp Alice 28.5 -57 8.5 180 0"; sleep 0.6; n0=$(n "deals blackjack"); open Alice; bet Alice; sleep 0.6; echo "solo: $(( $(n 'deals blackjack') - n0 )) hand dealt at once"; sleep 9; b Alice click 49; sleep 12; b Alice close ;;
late) chips Alice; chips Bob; at; open Alice; bet Alice; open Bob; n0=$(n "round for"); timeout 20 bash -c "until [ \$(grep -c 'round for' $L) -gt $n0 ]; do sleep 0.3; done"; grep -E "round for" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //'; echo "Bob's chips untouched: $(value Bob)"; sleep 1; e0=$(n 'hand of Alice ends'); play $((e0 + 1)) stand stand; sleep 4 ;;
*) echo "usage: blackjack-mp-test.sh shared|hit|double|quit|timeout|solo|late"; exit 2 ;;
esac
