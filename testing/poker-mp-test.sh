#!/bin/bash
# poker-mp-test.sh <step>: the shared poker table #1 (90 -57 41, facing SOUTH) on the default test instance, casinobots
# Alice and Bob online (Carl for "three").
# Steps: shared | fold | allfold | timeout | quitback | quitaway | late | solo | three
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing; P=$T/ptest.sh
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
S=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
q() { $P rcon "$@" >/dev/null; }
b() { local who=$1; shift; $P b "$who" "$@"; sleep 0.3; }
n() { grep -cE "$1" "$L"; }
chips() { q "clear $1" "gamemode survival $1"; sleep 0.2; q "casino give chip_25 $1 10" "casino give chip_5 $1 10" "casino give chip_1 $1 30" "casino give chip_100 $1 5"; sleep 0.3; }
value() { local s=0; for d in 1 5 25 100 500 1000; do c=$($P rcon "clear $1 paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"kaboofcasino:chip_$d\"}}] 0" | grep -oE 'Found [0-9]+' | grep -oE '[0-9]+'); s=$((s + d * ${c:-0})); done; echo $s; }
at() { q "tp Alice 89.5 -57 44.5 180 20" "tp Bob 91.5 -57 44.5 180 20"; [ -n "$1" ] && q "tp $1 90.5 -57 44.5 180 20"; sleep 0.6; }
open() { b "$1" hotbar 8; $P bx "$1" 5 'OPEN id' place 90 -57 42 south | grep -o "OPEN id=[0-9]*" | head -1; sleep 0.3; }
bet() { b "$1" click 81 right; b "$1" click 82 right; b "$1" click 83 right; b "$1" click 49; } # 25 + 5 + 1 = 31
call() { b "$1" click 46; }
fold() { b "$1" click 51; }
since() { tail -n +"$1" "$L" | grep -E "$2" | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-170; }
waitfor() { local from=$1 re=$2 secs=${3:-40}; timeout "$secs" bash -c "until tail -n +$from $L | grep -qE '$re'; do sleep 0.3; done"; }
start2() { chips Alice; chips Bob; at; a0=$(value Alice); b0=$(value Bob); m=$(wc -l < "$L"); open Alice; bet Alice; open Bob; bet Bob; waitfor "$m" "decision open" 40 || echo "  NO DECISION"; }
finish() { waitfor "$m" "$1" "${2:-40}" || echo "  NOT ENDED"; sleep 4; since "$m" "betting window|joins poker|hand for|decision open|calls|folds|ends|left the hand|back in the hand|out of the hand|drops out"; }
case ${1:-} in
shared) start2; call Alice; call Bob; finish "hand of Bob ends.*called|hand of Alice ends.*called"; sleep 3
  echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob) (each staked 31 + 62)" ;;
fold) start2; call Alice; fold Bob; finish "hand of Alice ends.*called"; sleep 3
  echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob) (Bob folded: -31)" ;;
allfold) start2; fold Alice; fold Bob; sleep 6; since "$m" "ends|hand for"; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob) (both -31)" ;;
timeout) start2; echo "nobody decides: one 30 s window"; finish "hand of Bob ends FOLD" 45; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob)" ;;
quitback) start2; $P bot stop Alice | tail -1; sleep 2; "$S/casinobot.sh" Alice accept | tail -1; sleep 1; q "tp Alice 89.5 -57 44.5 180 20"; sleep 0.8
  $P bx Alice 5 'OPEN id' place 90 -57 42 south | grep -o "OPEN id=[0-9]*" | head -1; sleep 0.5; call Alice; call Bob
  finish "hand of (Alice|Bob) ends.*called.*" 40; sleep 3; echo "Alice $a0 -> $(value Alice), Bob $b0 -> $(value Bob)"
  since "$m" "settled \((QUIT|JOIN)" ;;
quitaway) start2; $P bot stop Alice | tail -1; call Bob; finish "hand of Bob ends.*called" 50; sleep 2
  "$S/casinobot.sh" Alice accept | tail -1; sleep 2; echo "Alice back: $(value Alice) (was $a0, ante 31 lost to the fold)"
  since "$m" "settled \((QUIT|JOIN_RECOVERY)" ;;
late) chips Alice; chips Bob; at; b0=$(value Bob); m=$(wc -l < "$L"); open Alice; bet Alice; open Bob
  waitfor "$m" "hand for" 25; since "$m" "hand for"; sleep 0.5; echo "Bob's chips untouched: $(value Bob) (was $b0)"
  waitfor "$m" "decision open" 30; fold Alice; sleep 5 ;;
solo) chips Alice; q "tp Bob 200 -57 200"; sleep 0.5; q "tp Alice 90.5 -57 44.5 180 20"; sleep 0.6; m=$(wc -l < "$L")
  open Alice; bet Alice; sleep 0.6; echo "solo: $(since "$m" 'deals poker|betting window')"; sleep 13; fold Alice; sleep 4; b Alice close ;;
three) chips Alice; chips Bob; chips Carl; at Carl; m=$(wc -l < "$L"); open Alice; bet Alice; open Bob; bet Bob; open Carl; bet Carl
  waitfor "$m" "decision open" 40; call Alice; fold Bob; call Carl; finish "hand of Carl ends.*called" 40 ;;
*) echo "usage: poker-mp-test.sh shared|fold|allfold|timeout|quitback|quitaway|late|solo|three"; exit 2 ;;
esac
