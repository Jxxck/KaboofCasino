#!/bin/bash
# roulette-mp-test.sh <step>: the shared roulette spin (default test instance, casinobots Alice and Bob online).
# Steps: shared | quit | twice | late | solo | stop
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing; P=$T/ptest.sh
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
S=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
q() { $P rcon "$@" >/dev/null; }
b() { local who=$1; shift; $P b "$who" "$@"; sleep 0.35; }
n() { grep -cE "$1" "$L"; }
chips() { q "clear $1" "gamemode survival $1"; sleep 0.2; q "casino give chip_25 $1 10" "casino give chip_5 $1 10" "casino give chip_1 $1 30" "casino give chip_100 $1 5" "casino give chip_1000 $1 2"; sleep 0.3; }
value() { local s=0; for d in 1 5 25 100 500 1000; do c=$($P rcon "clear $1 paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"kaboofcasino:chip_$d\"}}] 0" | grep -oE 'Found [0-9]+' | grep -oE '[0-9]+'); s=$((s + d * ${c:-0})); done; echo $s; }
at() { q "tp Alice 14.5 -57 15.5 180 0" "tp Bob 15.5 -57 15.5 180 0"; sleep 0.6; }
open() { b "$1" hotbar 8; $P bx "$1" 5 'OPEN id' place 14 -57 13 south | grep -o "OPEN id=[0-9]*" | head -1; sleep 0.3; }
red() { b "$1" click 84 right; b "$1" click 81 right; b "$1" click 82 right; b "$1" click 49; } # 100 + 25 + 5 = 130 on the first colour
black() { b "$1" click 22; b "$1" click 84 right; b "$1" click 49; } # 100 on black
wait_for() { timeout "$3" bash -c "until [ \$(grep -cE '$1' $L) -gt $2 ]; do sleep 0.3; done" && grep -E "$1" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-150 || echo "(nothing within $3 s)"; }
last_bar() { tail -n +"$2" "$T/out/bots/$1.log" | grep -aE "ACTIONBAR" | tail -1 | cut -c32-140; }
case ${1:-} in
shared) chips Alice; chips Bob; at; a0=$(value Alice); b0=$(value Bob); n0=$(n "spins for"); s0=$(n "settled \(NORMAL\).*colour=")
  open Alice; red Alice; sleep 0.4; echo "Alice after her bet: $(value Alice) (was $a0)"; grep -E "betting window opened" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //'
  open Bob; black Bob; echo "Bob after his bet: $(value Bob) (was $b0)"
  echo "spin: $(wait_for "spins for" "$n0" 12)"; sleep 8.5; echo "settled rows: $(( $(n "settled \(NORMAL\).*colour=") - s0 ))"; grep -E "settled \(NORMAL\).*colour=" "$L" | tail -4 | sed -E 's/^.*settled \(NORMAL\): //' | cut -c1-110
  sleep 2; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob)" ;;
quit) chips Alice; chips Bob; at; a0=$(value Alice); n0=$(n "left the betting window"); open Alice; red Alice; sleep 0.4; echo "Alice after her bet: $(value Alice) (was $a0)"
  $P bot stop Alice | tail -1; sleep 1.5; grep -E "left the betting window|closed without a spin" "$L" | tail -2 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-110
  "$S/casinobot.sh" Alice accept | tail -1; sleep 1; echo "Alice back: $(value Alice) (was $a0)" ;;
twice) chips Alice; chips Bob; at; open Alice; red Alice; sleep 0.4; m=$(wc -l < "$T/out/bots/Alice.log"); b Alice hotbar 8; b Alice place 14 -57 13 south; sleep 0.5; echo "second click: $(last_bar Alice "$m")"; n0=$(n "spins for"); echo "window ends: $(wait_for "spins for" "$n0" 20)"; sleep 11 ;;
late) chips Alice; chips Bob; at; n0=$(n "spins for"); open Alice; red Alice; open Bob; m=$(wc -l < "$T/out/bots/Bob.log"); echo "Bob keeps his menu open..."; echo "spin: $(wait_for "spins for" "$n0" 20)"; sleep 0.6; echo "Bob's menu closed: $(tail -n +"$m" "$T/out/bots/Bob.log" | grep -acE 'CLOSE|CLOSED')"; echo "Bob's chips untouched: $(value Bob)"; sleep 10 ;;
solo) chips Alice; q "tp Bob 200 -57 200"; sleep 0.5; q "tp Alice 14.5 -57 15.5 180 0"; sleep 0.6; n0=$(n "spins roulette"); open Alice; red Alice; echo "solo: $(wait_for "spins roulette" "$n0" 4)"; sleep 11 ;;
stop) chips Alice; chips Bob; at; a0=$(value Alice); open Alice; red Alice; sleep 0.4; echo "Alice after her bet: $(value Alice) (was $a0)"; $P stop | tail -1; $P start after-stop | tail -1; "$S/casinobot.sh" Alice accept | tail -1; sleep 1; echo "Alice after the restart: $(value Alice) (was $a0)" ;;
*) echo "usage: roulette-mp-test.sh shared|quit|twice|late|solo|stop"; exit 2 ;;
esac
