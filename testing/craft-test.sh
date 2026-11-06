#!/bin/bash
# craft-test.sh: on the running default test instance with bot Alice online (survival, near 0 -57 0): a casino item
# (paper underneath) must not work as paper in vanilla recipes, the reel recipe must work with plain paper only.
set -u
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing
cd "$T"
b() { ./ptest.sh b Alice "$@"; sleep 0.25; }
result() { tail -n +"$1" out/bots/Alice.log | grep -E 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-90; }
./ptest.sh rcon "clear Alice" "setblock -2 -57 0 crafting_table" "casino give slot_machine Alice" "give Alice paper 64" \
  "give Alice iron_ingot 1" "give Alice diamond 1" "give Alice red_wool 1" "give Alice leather 1" >/dev/null
sleep 0.5
# A: book = 3 paper + leather; one "paper" is the slot machine item
L0=$(wc -l < out/bots/Alice.log); b place -2 -57 0 north; sleep 0.5
b click 37; b click 1; b click 38; b click 2 right; b click 3 right; b click 38; b click 42; b click 4
echo "A  book with a casino item as paper -> result: $(result $L0)"
L1=$(wc -l < out/bots/Alice.log)
b click 1; b click 37; b click 38; b click 1 right; b click 38
echo "A' book with plain paper only     -> result: $(result $L1)"
b close; sleep 0.5
# B: reel = " P " / "ID " / " W " with plain paper
L2=$(wc -l < out/bots/Alice.log); b place -2 -57 0 north; sleep 0.5
b click 38; b click 2 right; b click 38; b click 39; b click 4; b click 40; b click 5; b click 41; b click 8
echo "B  reel with plain paper          -> result: $(result $L2)"
L3=$(wc -l < out/bots/Alice.log)
b click 2; b click 38; b click 37; b click 2
echo "C  reel with a casino item as P   -> result: $(result $L3)"
b close
