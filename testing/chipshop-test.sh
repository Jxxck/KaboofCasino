#!/bin/bash
# chipshop-test.sh <step>: the Chip-Shop (step 11) on the default test instance, bot Alice online.
# Steps: place | buy | stack | machines | poor | full | double | confirm | overcap | craft
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh; A=$T/out/bots/Alice.log
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
  b click 10; m=$(wc -l < "$A"); b click 49; sleep 0.6
  echo "$(last)"; echo "  said: $(bar $m)"; echo "  slot machine: $(has slot_machine)"; v1=$(value); echo "  chips $v0 -> $v1"
  b close ;;
poor) # 10 chips, the slot machine
  q "clear Alice" "casino give chip_5 Alice 2"; sleep 0.3; open_shop; $P bx Alice 5 'OPEN id' click 5 >/dev/null; sleep 0.5
  b click 10; n0=$(n "chipshop BUY"); m=$(wc -l < "$A"); b click 49; sleep 0.6
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | said: $(bar $m)"; b close ;;
full) # the chips in slots 0-1, every other slot full of dirt: a stack does not fit
  chips 10; for s in $(seq 2 35); do q "item replace entity Alice container.$s with dirt 64"; done
  open_shop; b click 9; n0=$(n "chipshop BUY"); m=$(wc -l < "$A"); b click 49 shift; sleep 0.6
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | said: $(bar $m)"; b close; q "clear Alice dirt" ;;
double) # two KAUFEN clicks in one burst
  chips; open_shop; b click 9; n0=$(n "chipshop BUY"); printf 'click 49\nclick 49\n' >> "$T/out/in_Alice"; sleep 1.2
  echo "BUY rows from a double click: $(( $(n 'chipshop BUY') - n0 ))"; b close ;;
confirm) # a stack of roulette tables (above 1000 chips): armed by the first click, a reflex click confirms nothing
  q "clear Alice" "gamemode survival Alice" "casino give chip_1000 Alice 40"; sleep 0.3; v0=$(value); open_shop
  $P bx Alice 5 'OPEN id' click 5 >/dev/null; sleep 0.5; b click 16; n0=$(n "chipshop BUY"); m=$(wc -l < "$A")
  printf 'click 49 shift\nclick 49 shift\n' >> "$T/out/in_Alice"; sleep 0.3
  echo "double click: BUY rows $(( $(n 'chipshop BUY') - n0 )) | said: $(bar $m)"; sleep 4
  b click 49 shift; sleep 0.2; echo "after the window ran out, one click: BUY rows $(( $(n 'chipshop BUY') - n0 ))"
  sleep 0.8; b click 11; sleep 0.2; b click 16; sleep 0.2; b click 49 shift; sleep 0.2
  echo "armed, other choice, click again: BUY rows $(( $(n 'chipshop BUY') - n0 ))"
  sleep 0.8; b click 49 shift; sleep 0.6; echo "second click after 1 s: BUY rows $(( $(n 'chipshop BUY') - n0 )) | $(last)"
  v1=$(value); echo "  chips $v0 -> $v1"; b close ;;
overcap) # a fixed price whose stack is above the cap of 100 000 000: refused with a normal message
  C=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/plugins/KaboofCasino/config.yml
  python3 - "$C" <<'PY'
import re,sys
s=open(sys.argv[1],encoding='utf-8').read()
s2,k=re.subn(r'(?m)^(chipshop:(?:.*\n)*?  items:) \[\]\n',r"\1\n  - item: casinodeco:ruby_trellis\n    price: 5000000\n",s,count=1)
assert k==1
open(sys.argv[1],'w',encoding='utf-8').write(s2)
PY
  q "casino reload"; sleep 0.5; chips; open_shop; b click 9; n0=$(n "chipshop BUY"); b click 49 shift; sleep 1; m=$(wc -l < "$A"); b click 49 shift; sleep 0.6
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | said: $(bar $m)"; b close
  python3 - "$C" <<'PY'
import re,sys
s=open(sys.argv[1],encoding='utf-8').read()
s2,k=re.subn(r'(?m)^  - item: casinodeco:ruby_trellis\n    price: 5000000\n','',s,count=1)
assert k==1
s2,k=re.subn(r'(?m)^(chipshop:(?:.*\n)*?  items:)\n(?=\S)',r"\1 []\n",s2,count=1)
open(sys.argv[1],'w',encoding='utf-8').write(s2)
PY
  q "casino reload" ;;
craft) # the shop counter's recipe in a crafting table (chipshop.craftable: true): GSG / BCB
  q "clear Alice" "gamemode survival Alice" "setblock 48 -57 9 crafting_table" "tp Alice 47.5 -57 9.5 -90 0" "give Alice glass_pane 2" \
    "casino give casinodeco:small_casino_sign Alice 1" "casino give casinodeco:black_marble_bar Alice 2" "casino give casinodeco:casino_chip_pile Alice 1"; sleep 0.6
  m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b click 37; b click 1 right; b click 3; b click 38; b click 2; b click 39; b click 4 right; b click 6; b click 40; b click 5; sleep 0.4
  echo "result: $(tail -n +"$m" "$A" | grep -E 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-120)"
  b click 0; b click 44; sleep 0.3; b close; sleep 0.3; echo "chip_shop in the inventory: $(has chip_shop)" ;;
book) # a click on the recipe-book entry with chips in the inventory: the right items land in the grid, the shop comes out
  id=$(grep -a 'RECIPE id=' "$A" | tail -1 | sed -E 's/.*RECIPE id=([0-9]+).*/\1/')
  q "clear Alice" "gamemode survival Alice" "setblock 48 -57 9 crafting_table" "tp Alice 47.5 -57 9.5 -90 0" "casino give chip_25 Alice 10" "give Alice glass_pane 2" \
    "casino give casinodeco:small_casino_sign Alice 1" "casino give casinodeco:black_marble_bar Alice 2" "casino give casinodeco:casino_chip_pile Alice 1"; sleep 0.8
  m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b placerecipe "$id"; sleep 0.8
  echo "result: $(tail -n +"$m" "$A" | grep -aE 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-120)"
  b click 0; b click 44; sleep 0.3; b close; sleep 0.3
  echo "chip_shop: $(has chip_shop) | sign: $(has casinodeco:small_casino_sign) | bars: $(has casinodeco:black_marble_bar) | pile: $(has casinodeco:casino_chip_pile) | chips: $(has kaboofcasino:chip_25)" ;;
stale) # by hand with an older stack of the chip pile (other item name, same id): crafts and is consumed
  q "clear Alice" "gamemode survival Alice" "setblock 48 -57 9 crafting_table" "tp Alice 47.5 -57 9.5 -90 0" "give Alice glass_pane 2" \
    "casino give casinodeco:small_casino_sign Alice 1" "casino give casinodeco:black_marble_bar Alice 2" \
    "give Alice paper[item_name='alt',item_model='casinodeco:casino_chip_pile',custom_data={PublicBukkitValues:{\"kaboofcasino:item\":\"casinodeco:casino_chip_pile\"}}] 1"; sleep 0.6
  m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b click 37; b click 1 right; b click 3; b click 38; b click 2; b click 39; b click 4 right; b click 6; b click 40; b click 5; sleep 0.4
  echo "result: $(tail -n +"$m" "$A" | grep -E 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-120)"
  b click 0; b click 44; sleep 0.3; b close; sleep 0.3
  echo "chip_shop: $(has chip_shop) | sign: $(has casinodeco:small_casino_sign) | bars: $(has casinodeco:black_marble_bar) | pile: $(has casinodeco:casino_chip_pile)" ;;
outfit) # the Outfits tab: one of each piece of the smoking, put on with a right click
  chips 10; v0=$(value); open_shop; $P bx Alice 5 'OPEN id' click 6 | grep -o "OPEN id=[0-9]*"; sleep 0.5
  for k in 9 10 11 12; do b click $k; b click 49; sleep 0.7; echo "  $(last | sed -E 's/.*: 1x/1x/; s/, paid.*//')"; done
  v1=$(value); echo "  chips $v0 -> $v1"; b close; sleep 0.3
  for s in 2 3 4 5; do b hotbar $s; b use; sleep 0.3; done; sleep 0.3
  $P rcon "data get entity Alice equipment" | grep -oE '(head|chest|legs|feet): \{[^}]*(equippable|item_model)[^}]*' | cut -c1-150
  $P rcon "data get entity Alice equipment.chest.components" | grep -oE 'minecraft:equippable[^}]*\}' | cut -c1-160 ;;
outfitstack) # the toggle on a stack with a piece of clothing chosen: buys one
  chips 10; open_shop; $P bx Alice 5 'OPEN id' click 6 >/dev/null; sleep 0.5; b click 10; b click 47; n0=$(n "chipshop BUY"); b click 49; sleep 0.7
  echo "BUY rows: $(( $(n 'chipshop BUY') - n0 )) | $(last | sed -E 's/.*: ([0-9]+x)/\1/; s/, paid.*//')"; b close ;;
outfitcraft) # by hand from wool, then the same with a recipe-book click
  id=$(grep -a 'RECIPE id=' "$A" | grep -a 'outfit_smoking_jacket' | tail -1 | sed -E 's/.*RECIPE id=([0-9]+).*/\1/')
  q "clear Alice" "gamemode survival Alice" "setblock 48 -57 9 crafting_table" "tp Alice 47.5 -57 9.5 -90 0" "give Alice black_wool 7" "give Alice white_wool 1"; sleep 0.6
  m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b click 37; for g in 1 3 4 6 7 8 9; do b click $g right; done; b click 38; b click 5; sleep 0.4
  echo "by hand: $(tail -n +"$m" "$A" | grep -aE 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-60)"; b click 0; b click 44; sleep 0.3; b close; sleep 0.3
  jack() { $P rcon "clear Alice leather_chestplate[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"casinodeco:outfit_smoking_jacket\"}}] 0" | grep -oE 'Found [0-9]+|No items' | head -1; }
  echo "jacket: $(jack) | wool left: $($P rcon 'clear Alice black_wool 0' | grep -oE 'Found [0-9]+|No items' | head -1)"
  q "give Alice black_wool 7" "give Alice white_wool 1"; sleep 0.5; m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b placerecipe "$id"; sleep 0.8
  echo "recipe book (id $id): $(tail -n +"$m" "$A" | grep -aE 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-60)"; b close ;;
outfitveto) # a jacket with red dye and two jackets together: no result in the crafting grid
  q "clear Alice" "gamemode survival Alice" "setblock 48 -57 9 crafting_table" "tp Alice 47.5 -57 9.5 -90 0" "casino give casinodeco:outfit_smoking_jacket Alice 1" "casino give casinodeco:outfit_rubin_jacket Alice 1" "give Alice red_dye 1"; sleep 0.6
  m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b click 37; b click 1; b click 39; b click 2; sleep 0.5
  echo "jacket + dye: $(tail -n +"$m" "$A" | grep -aE 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-60)"
  b click 2; b click 39; m=$(wc -l < "$A"); b click 38; b click 2; sleep 0.5
  echo "two jackets:  $(tail -n +"$m" "$A" | grep -aE 'SLOT id=[0-9]+ slot=0 ' | tail -1 | sed -E 's/.*slot=0 //' | cut -c1-60)"; b close ;;
*) echo "usage: chipshop-test.sh place|buy|stack|machines|poor|full|double|confirm|overcap|craft|book|stale|outfit|outfitstack|outfitcraft|outfitveto"; exit 2 ;;
esac
