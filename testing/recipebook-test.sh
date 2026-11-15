#!/bin/bash
# recipebook-test.sh <step>: clicks in the native recipe book with chips FIRST in the inventory (default test instance,
# casinobot Alice online; "scan" discovers every recipe first). Prints the grid after the click and what came out.
# Steps: scan | reel | reelmax | machine | deco | shop | vanilla
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh; A=$T/out/bots/Alice.log
q() { $P rcon "$@" >/dev/null; }; b() { $P b Alice "$@"; sleep 0.35; }
# the book's entry of a text: the exact one (Composite display), not its undiscovered twin
idof() { b recipes "$1"; sleep 0.4; grep -a "RECIPE id=.* match=$1 " "$A" | grep -aE "result=.*$1" | grep -a "$2" | tail -1 | sed -E 's/.*RECIPE id=([0-9]+).*/\1/'; }
grid() { tail -n +"$1" "$A" | grep -aE 'SLOT id=[0-9]+ slot=[0-9] ' | sed -E 's/^.*(slot=[0-9]) /\1 /' | cut -c1-48 | awk '{a[$1]=$0} END {for (i=0;i<10;i++) if (("slot=" i) in a) print "   " a["slot=" i]}'; }
item() { echo "paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"$1\"}}]"; }
count() { $P rcon "clear Alice $1 0" | grep -oE 'Found [0-9]+|No items' | head -1; }
setup() { q "clear Alice" "gamemode survival Alice" "setblock 48 -57 9 crafting_table" "tp Alice 47.5 -57 9.5 -90 0" "casino give chip_25 Alice 10" "casino give casinodeco:casino_bottles Alice 2"; sleep 0.3; q "$@"; sleep 0.7; }
click() { local id=$1 mode=${2:-}; m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b placerecipe "$id" $mode; sleep 0.9; grid "$m"; b click 0 shift; sleep 0.4; b close; sleep 0.4; }
case ${1:-} in
scan) q "recipe give Alice *"; sleep 2; echo "book entries seen: $(grep -ac 'RECIPE' "$A")" ;;
reel) id=$(idof slot_machine_reel Composite); echo "recipe $id"; setup "give Alice paper 1" "give Alice iron_ingot 1" "give Alice diamond 1" "give Alice red_wool 1"; click "$id"
  echo "   reel: $(count "$(item slot_machine_reel)") plain paper left: $(count 'paper[!custom_data]') chips: $(count "$(item kaboofcasino:chip_25)")" ;;
reelmax) id=$(idof slot_machine_reel Composite); setup "give Alice paper 5" "give Alice iron_ingot 5" "give Alice diamond 5" "give Alice red_wool 5"; click "$id" max
  echo "   reels (5 expected): $(count "$(item slot_machine_reel)") plain paper left: $(count 'paper[!custom_data]') chips: $(count "$(item kaboofcasino:chip_25)")" ;;
machine) id=$(idof slot_machine_mechanism Composite); echo "$id" >/dev/null; id=$(idof '"slot_machine"' Composite); echo "recipe $id"; setup "give Alice glass 2" "give Alice red_wool 1" "give Alice gold_block 2" "casino give slot_machine_mechanism Alice 1" "give Alice oak_planks 2" "give Alice redstone 1"; click "$id"
  echo "   slot machine: $(count "$(item slot_machine)") mechanism left: $(count "$(item slot_machine_mechanism)") chips: $(count "$(item kaboofcasino:chip_25)")" ;;
deco) id=$(idof black_marble_bar Composite); echo "recipe $id"; setup "casino give casinodeco:obsidian_marble Alice 6" "give Alice gold_nugget 3"; click "$id"
  echo "   bars (6 expected): $(count "$(item casinodeco:black_marble_bar)") marble left: $($P rcon 'data get entity Alice Inventory' | grep -c obsidian_marble) chips: $(count "$(item kaboofcasino:chip_25)")" ;;
shop) id=$(idof chip_shop Composite); echo "recipe $id"; setup "give Alice glass_pane 2" "casino give casinodeco:small_casino_sign Alice 1" "casino give casinodeco:black_marble_bar Alice 2" "casino give casinodeco:casino_chip_pile Alice 1"; click "$id"
  echo "   shop: $(count "$(item chip_shop)") chips: $(count "$(item kaboofcasino:chip_25)")" ;;
vanilla) id=${2:-$(idof 'id=1146,' Shapeless)}; echo "recipe $id"; setup "give Alice paper 3" "give Alice leather 1"; click "$id"
  echo "   book: $(count book) plain paper left: $(count 'paper[!custom_data]') leather: $(count leather) chips: $(count "$(item kaboofcasino:chip_25)")"
  setup "give Alice leather 1"; m=$(wc -l < "$A"); b place 48 -57 9 west; sleep 0.5; b placerecipe "$id"; sleep 0.9; echo "   only chips, no paper:"; grid "$m"; b close; sleep 0.3; echo "   chips still: $(count "$(item kaboofcasino:chip_25)")" ;;
*) echo "usage: recipebook-test.sh scan|reel|reelmax|machine|deco|shop|vanilla"; exit 2 ;;
esac
