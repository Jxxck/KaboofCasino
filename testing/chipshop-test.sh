#!/bin/bash
# chipshop-test.sh <step>: the Chip-Shop (step 11) on the default test instance, bot Alice online.
# Steps: place | buy | stack | machines | poor | full | limit | double
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing; P=$T/ptest.sh; A=$T/out/bots/Alice.log
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
q() { $P rcon "$@" >/dev/null; }
b() { $P b Alice "$@"; sleep 0.4; }
n() { grep -cE "$1" "$L"; }
open_shop() { q "tp Alice 44.5 -57 9.5 180 0"; sleep 0.6; b hotbar 8; $P bx Alice 5 'OPEN id' place 44 -57 6 south \
  | grep -o "OPEN id=[0-9]*"; sleep 0.4; }
chips() { q "clear Alice" "gamemode survival Alice" "casino give chip_100 Alice ${1:-10}" "casino give chip_25 Alice 4"; sleep 0.3; }
value() { local s=0; for d in 1 5 25 100 500 1000; do c=$($P rcon "clear Alice paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"kaboofcasino:chip_$d\"}}] 0" | grep -oE 'Found [0-9]+' | grep -oE '[0-9]+'); s=$((s + d * ${c:-0})); done; echo $s; }
has() { $P rcon "clear Alice paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"$1\"}}] 0" | grep -oE 'Found [0-9]+|No items' | head -1; }
last() { grep -E "chipshop BUY" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-200; }
bar() { local m=$1; tail -n +"$m" "$A" | grep -E "ACTIONBAR|CHAT" | tail -1 | cut -c32-170; }
case ${1:-} in
place)
  q "fill 44 -57 6 45 -56 6 air" "clear Alice" "gamemode survival Alice" "tp Alice 44.5 -57 9.5 180 0" "casino give chip_shop Alice 1"
  sleep 0.6; b hotbar 0; b place 44 -58 6 up; sleep 0.6
  grep -E "placed chip_shop" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //'
  echo "carriers: $($P rcon 'execute if block 44 -57 6 barrier' 'execute if block 45 -56 6 barrier' | grep -o 'Test passed\|Test failed' | tr '\n' ' ')"
  ;;
buy) # one piece of the first floor item
  chips; v0=$(value); open_shop; n0=$(n "chipshop BUY"); b click 9; m=$(wc -l < "$A"); b click 49; sleep 0.6
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | $(last)"; echo "  said: $(bar $m)"; v1=$(value); echo "  chips $v0 -> $v1"
  b close ;;
stack) # Umschalt-click: a stack of the first floor item
  chips; v0=$(value); open_shop; b click 9; m=$(wc -l < "$A"); b click 49 shift; sleep 0.6
  echo "$(last)"; echo "  said: $(bar $m)"; v1=$(value); echo "  chips $v0 -> $v1"; b close ;;
machines) # the machines tab, the slot machine (the first machine)
  chips 10; v0=$(value); open_shop; $P bx Alice 5 'OPEN id' click 5 | grep -o "OPEN id=[0-9]*"; sleep 0.5
  b click 9; m=$(wc -l < "$A"); b click 49; sleep 0.6
  echo "$(last)"; echo "  said: $(bar $m)"; echo "  slot machine: $(has slot_machine)"; v1=$(value); echo "  chips $v0 -> $v1"
  b close ;;
poor) # 10 chips, the slot machine
  q "clear Alice" "casino give chip_5 Alice 2"; sleep 0.3; open_shop; $P bx Alice 5 'OPEN id' click 5 >/dev/null; sleep 0.5
  b click 9; n0=$(n "chipshop BUY"); m=$(wc -l < "$A"); b click 49; sleep 0.6
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | said: $(bar $m)"; b close ;;
full) # the chips in slots 0-1, every other slot full of dirt: a stack does not fit
  chips 10; for s in $(seq 2 35); do q "item replace entity Alice container.$s with dirt 64"; done
  open_shop; b click 9; n0=$(n "chipshop BUY"); m=$(wc -l < "$A"); b click 49 shift; sleep 0.6
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | said: $(bar $m)"; b close; q "clear Alice dirt" ;;
double) # two KAUFEN clicks in one burst
  chips; open_shop; b click 9; n0=$(n "chipshop BUY"); printf 'click 49\nclick 49\n' >> "$T/out/in_Alice"; sleep 1.2
  echo "BUY rows from a double click: $(( $(n 'chipshop BUY') - n0 ))"; b close ;;
*) echo "usage: chipshop-test.sh place|buy|stack|machines|poor|full|double"; exit 2 ;;
esac
