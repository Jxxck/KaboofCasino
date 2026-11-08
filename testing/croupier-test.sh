#!/bin/bash
# croupier-test.sh <step>: croupier (step 8, option A) bot test on the default test instance, bot Alice online.
# Counter A at 132 -57 66, counter B at 138 -57 66 (chunk 8,4).
set -u
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing
P=$T/ptest.sh
BL=$T/out/bots/Alice.log
say() { $P rcon "$@"; }
q() { $P rcon "$@" >/dev/null; }
b() { $P b Alice "$@"; sleep 0.35; }
mark() { wc -l < "$BL"; }
since() { tail -n +"$1" "$BL"; }
check() { # check <execute-condition> -> yes/no
  local tag=CHK$RANDOM
  q "execute $1 run say $tag"; sleep 0.3
  $P log 6 | grep -q "$tag" && echo yes || echo no
}
eid() { # eid <type> <x> <z>: the bot's tracked entity of that type nearest (x, z)
  local m; m=$(mark); b entities; sleep 0.3
  since "$m" | python3 -I -B -c "
import sys,re
best=None
for l in sys.stdin:
  r=re.search(r'ENTITY id=(\d+) type=(\S+) at ([-\d.]+),([-\d.]+),([-\d.]+)',l)
  if r and r.group(2)=='$1':
    d=(float(r.group(3))-$2)**2+(float(r.group(5))-$3)**2
    if best is None or d<best[0]: best=(d,r.group(1))
print(best[1] if best else 'none')"
}
villagers() { say "execute as @e[type=villager,x=135,y=-57,z=66,distance=..12] run data get entity @s Pos" | sed -E 's/^.*has the following entity data: //'; }

case ${1:-} in
setup)
  q "fill 125 -58 58 145 -58 75 stone" "fill 125 -57 58 145 -50 75 air" \
    "kill @e[type=villager,x=135,y=-57,z=66,distance=..20]" "kill @e[type=item,x=135,y=-57,z=66,distance=..20]" \
    "gamemode creative Alice" "clear Alice" "tp Alice 135.5 -57 69.5 180 30" "time set 1000"
  sleep 1
  q "casino give dealer_station Alice 2"; sleep 0.4
  b hotbar 0; b place 132 -58 66 up; sleep 0.5; b place 138 -58 66 up; sleep 0.8
  echo "counter A barrier: $(check 'if block 132 -57 66 barrier')  counter B barrier: $(check 'if block 138 -57 66 barrier')"
  q "gamemode survival Alice"
  ;;
refuse) # every refusal on counter A (free), one at a time
  for c in "nitwit|{VillagerData:{profession:\"minecraft:nitwit\",level:1,type:\"minecraft:plains\"}}" \
           "farmer-xp1|{VillagerData:{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"},Xp:1}" \
           "trades|{Offers:{Recipes:[{buy:{id:\"minecraft:emerald\",count:1},sell:{id:\"minecraft:stick\",count:1}}]}}" \
           "level2|{VillagerData:{profession:\"minecraft:none\",level:2,type:\"minecraft:plains\"}}" \
           "xp|{Xp:5}" \
           "baby|{Age:-24000}" \
           "named|{CustomName:\"Bob\"}" \
           "passenger|{Passengers:[{id:\"minecraft:chicken\",NoAI:1b}]}"; do
    name=${c%%|*}; nbt=${c#*|}
    q "summon villager 132.5 -56 66.5 ${nbt%\}},Tags:[\"crt\"]}"
    sleep 1.8
    why=$($P log 40 | grep "does not become a croupier" | grep -v GONE | tail -1 | sed -E 's/.*croupier: //')
    alive=$(check 'if entity @e[type=villager,tag=crt]')
    echo "refuse $name -> ${why:-nothing logged}  villager still there: $alive"
    q "kill @e[tag=crt]" "kill @e[type=chicken,x=132,y=-56,z=66,distance=..3]"; sleep 0.3
  done
  # riding: a villager in a minecart on the counter
  q "summon minecart 132.5 -56 66.5 {Tags:[\"crt\"],Passengers:[{id:\"minecraft:villager\",Tags:[\"crt\"]}]}"; sleep 1.8
  echo "refuse riding -> $($P log 40 | grep 'does not become a croupier' | grep -v GONE | tail -1 | sed -E 's/.*croupier: //')  (feet are above the counter top when riding: NOT_ON_TOP is silent)"
  echo "  villager still there: $(check 'if entity @e[type=villager,tag=crt]')"
  q "kill @e[tag=crt]"; sleep 0.3
  # no player near: Alice 20 blocks away
  q "tp Alice 160.5 -57 66.5"; sleep 0.6
  q "summon villager 132.5 -56 66.5 {Tags:[\"crt\"]}"; sleep 1.8
  echo "refuse no-player -> $($P log 40 | grep 'does not become a croupier' | grep -v GONE | tail -1 | sed -E 's/.*croupier: //')  villager still there: $(check 'if entity @e[type=villager,tag=crt]')"
  q "kill @e[tag=crt]"; sleep 0.3
  q "tp Alice 135.5 -57 69.5 180 30"; sleep 0.6
  ;;
leash) # a leashed villager (on Alice's lead) on counter A
  q "give Alice lead 1"; sleep 0.3
  q "summon villager 132.5 -57 68.5 {Tags:[\"crl\"],NoAI:1b}"; sleep 0.8
  b hotbar 0; id=$(eid villager 132.5 68.5); b rclick "$id"; sleep 0.6
  echo "leashed: $(check 'if entity @e[type=villager,tag=crl]') (lead gone from inventory: $($P rcon 'clear Alice lead 0'))"
  q "tp @e[tag=crl] 132.5 -56 66.5"; q "data merge entity @e[tag=crl,limit=1] {NoAI:0b}"; sleep 1.8
  echo "refuse leashed -> $($P log 40 | grep 'does not become a croupier' | grep -v GONE | tail -1 | sed -E 's/.*croupier: //')  villager still there: $(check 'if entity @e[type=villager,tag=crl]')"
  q "kill @e[tag=crl]" "kill @e[type=leash_knot]" "kill @e[type=item,x=132,y=-57,z=66,distance=..6]" "clear Alice lead"
  ;;
push) # Alice pushes an unemployed villager from a stone step onto counter A
  q "setblock 133 -57 66 stone" "setblock 134 -57 66 stone" "setblock 135 -57 66 stone" "tp Alice 135.5 -56 66.5 90 0"
  sleep 0.8
  q "summon villager 133.5 -56 66.5 {Tags:[\"crp\"]}"; sleep 0.4
  m=$(mark)
  for x in 134.6 134.4 134.2 134.0 133.8 133.6 133.5 133.5 133.5 133.5; do b move $x -56 66.5; done
  sleep 1.6
  echo "server log:"; $P log 30 | grep -E "croupier|Croupier" | tail -4
  echo "villager gone: $(check 'unless entity @e[tag=crp]')"
  echo "rig entities near 131.5 -57 66.5: $($P rcon 'execute if entity @e[type=item_display,x=131.5,y=-56,z=66.5,distance=..2]' | tr -d '\n')"
  ;;
trade) # 24 emeralds -> hiring card
  q "clear Alice" "give Alice emerald 30"; sleep 0.4
  b hotbar 4
  id=$(eid interaction 131.5 66.5); echo "croupier click box: $id"
  m=$(mark); b rclick "$id"; sleep 0.8
  since "$m" | grep -E "OPEN|MERCHANT" | cut -c1-700
  b selecttrade 0; sleep 0.5
  b click 2 shift; sleep 0.6
  b close; sleep 0.4
  b invdump; sleep 0.3
  since "$m" | grep -E "HAVE|SOUND|ACTIONBAR" | grep -iE "emerald|hiring|card|villager|ACTIONBAR" | cut -c1-200
  echo "emeralds left: $($P rcon 'clear Alice emerald 0')"
  $P log 20 | grep -iE "croupier|trade" | tail -3
  ;;
reopen) # second look at the screen: uses and xp after one trade
  id=$(eid interaction 131.5 66.5); m=$(mark); b rclick "$id"; sleep 0.8
  since "$m" | grep -E "MERCHANT" | cut -c1-700; b close
  ;;
breakA) # break counter A after a trade: the croupier keeps its profession
  b hotbar 8; m=$(mark); b dig 132 -57 66 6500; sleep 7.5
  echo "counter A gone: $(check 'if block 132 -57 66 air')"
  $P log 20 | grep -iE "croupier|counter" | tail -3
  echo "croupier rig still there: $($P rcon 'execute if entity @e[type=item_display,x=131.5,y=-56,z=66.5,distance=..2]' | tr -d '\n')"
  echo "villager came back: $(check 'if entity @e[type=villager,x=131.5,y=-57,z=66.5,distance=..3]')"
  ;;
reattach) # put counter A back next to the counterless croupier
  q "gamemode creative Alice" "casino give dealer_station Alice 1"; sleep 0.4
  b hotbar 0; b place 132 -58 66 up; sleep 1.5
  $P log 20 | grep -iE "croupier" | tail -3
  q "gamemode survival Alice"
  ;;
hire) # the hiring card on the croupier: badge, effects, croupier gone, recipes
  q "recipe take Alice kaboofcasino:blackjack_table" "recipe take Alice kaboofcasino:poker_table"; sleep 0.3
  echo "before: blackjack recipe known? $($P rcon 'recipe take Alice kaboofcasino:blackjack_table')"
  b hotbar 8; b invdump
  m=$(mark)
  id=$(eid interaction 131.5 66.5); echo "croupier click box: $id"
  # the card is in hotbar slot 0 after the trade (the first free slot)
  b rclick "$id"; sleep 2.5
  since "$m" | grep -E "SOUND|PARTICLE|ACTIONBAR|CHAT" | sort | uniq -c | sort -rn | head -14 | cut -c1-160
  b invdump; sleep 0.3
  since "$m" | grep -E "HAVE" | cut -c1-160
  $P log 20 | grep -iE "croupier|hire|badge" | tail -3
  echo "croupier rig gone: $(check 'unless entity @e[type=item_display,x=131.5,y=-56,z=66.5,distance=..2]')"
  echo "recipes: blackjack $($P rcon 'recipe take Alice kaboofcasino:blackjack_table')  poker $($P rcon 'recipe take Alice kaboofcasino:poker_table')"
  ;;
fresh) # an unemployed villager put onto counter B becomes croupier 2 (stays at XP 0)
  q "tp Alice 140.5 -57 69.5 180 30"; sleep 0.6
  q "summon villager 138.5 -56 66.5 {Tags:[\"crf\"]}"; sleep 1.8
  $P log 20 | grep -iE "croupier" | tail -2
  echo "villager gone: $(check 'unless entity @e[tag=crf]')"
  ;;
breakB) # break counter B under the fresh croupier: the villager comes back
  b hotbar 5; b dig 138 -57 66 6500; sleep 7.5
  echo "counter B gone: $(check 'if block 138 -57 66 air')"
  $P log 20 | grep -iE "croupier|villager" | tail -3
  villagers
  echo "rig gone: $(check 'unless entity @e[type=interaction,x=138.5,y=-57,z=66.5,distance=..2.5]')"
  ;;
*) echo "usage: croupier-test.sh setup|refuse|leash|push|trade|reopen|breakA|reattach|hire|fresh|breakB"; exit 2;;
esac
