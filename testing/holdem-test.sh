#!/bin/bash
# holdem-test.sh <step>: Texas Hold'em at poker table #1 (90 -57 41, facing SOUTH) on the default test instance,
# casinobots Alice and Bob online (Carl for "three"). "setup" shortens the waits in the instance config.
# Steps: setup | showdown | showcards | fold | allin | quit | leave | runout | solo | three
# Every step but solo ends with everybody standing up and checks that the chips of all bots together are unchanged.
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing; P=$T/ptest.sh; B=$T/out/bots
INST=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p'); L=$INST/logs/latest.log
S=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
q() { $P rcon "$@" >/dev/null; }
b() { local who=$1; shift; $P b "$who" "$@"; sleep 0.3; }
n() { grep -cE "$1" "$L"; }
chips() { q "clear $1" "gamemode survival $1"; sleep 0.2; q "casino give chip_100 $1 10" "casino give chip_25 $1 4"; sleep 0.3; }
value() { local s=0; for d in 1 5 25 100 500 1000; do c=$($P rcon "clear $1 paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"kaboofcasino:chip_$d\"}}] 0" | grep -oE 'Found [0-9]+' | grep -oE '[0-9]+'); s=$((s + d * ${c:-0})); done; echo $s; }
at() { q "tp Alice 89.5 -57 44.5 180 20" "tp Bob 91.5 -57 44.5 180 20"; [ -n "$1" ] && q "tp $1 90.5 -57 44.5 180 20"; sleep 0.6; }
open() { b "$1" hotbar 8; $P bx "$1" 5 'OPEN id' place 90 -57 42 south | grep -o "OPEN id=[0-9]*" | head -1; sleep 0.3; }
# buyin <who> <hundreds>: right-click the chip_100 stack (hotbar slot 0 = raw 81) n times, then HINSETZEN (49)
buyin() { local i; for i in $(seq 1 "$2"); do b "$1" click 81 right; done; b "$1" click 49; }
since() { tail -n +"$1" "$L" | grep -E "$2" | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-190; }
waitfor() { local from=$1 re=$2 secs=${3:-40}; timeout "$secs" bash -c "until tail -n +$from $L | grep -qE '$re'; do sleep 0.3; done"; }
act() { case $2 in fold) b "$1" click 46 ;; call|check) b "$1" click 49 ;; raise) b "$1" click 52 ;; allin) b "$1" click 25; b "$1" click 52 ;; quit) $P bot stop "$1" | tail -1 ;; none) ;; esac; }
# play <from line> <Alice's first action> <Bob's first action> [Carl's]: answers every "turn of" until the hand is decided;
# after its first action each player checks or calls
play() { local from=$1 aa=$2 ab=$3 ac=${4:-call} seen guard=0 who c
  seen=$(tail -n +"$from" "$L" | grep -c "turn of")
  while ! tail -n +"$from" "$L" | grep -q "decided" && [ $guard -lt 600 ]; do guard=$((guard + 1))
    c=$(tail -n +"$from" "$L" | grep -c "turn of")
    if [ "$c" -gt "$seen" ]; then seen=$c; who=$(tail -n +"$from" "$L" | grep "turn of" | tail -1 | sed -E 's/.*turn of ([A-Za-z]+).*/\1/'); sleep 0.6
      case $who in Alice) act Alice "$aa"; aa=call ;; Bob) act Bob "$ab"; ab=call ;; Carl) act Carl "$ac"; ac=call ;; esac; fi
    sleep 0.25; done; }
standup() { local who; for who in "$@"; do $P bx "$who" 5 'OPEN id' place 90 -57 42 south >/dev/null 2>&1; sleep 0.5; b "$who" click 46; b "$who" close; done; }
total() { local s=0 w; for w in "$@"; do s=$((s + $(value "$w"))); done; echo $s; }
check() { local before=$1; shift; local after; after=$(total "$@"); echo "chips of $* together: $before -> $after $([ "$before" = "$after" ] && echo OK || echo MISMATCH)"; }
sit2() { chips Alice; chips Bob; at; T0=$(total Alice Bob); M=$(wc -l < "$L"); open Alice; buyin Alice 2; open Bob; buyin Bob 2; waitfor "$M" "hand [0-9]+ for" 30 || echo "  NO HAND"; }
case ${1:-} in
setup)
  python3 - "$INST/plugins/KaboofCasino/config.yml" <<'PY'
import re,sys
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
for k,v in (('start-seconds','2'),('next-hand-seconds','3'),('turn-seconds','15')):
    s,n=re.subn(r'(?m)^(    '+k+r':) \d+',r'\1 '+v,s,count=1); assert n==1,k
open(p,'w',encoding='utf-8').write(s)
PY
  $P rcon "casino reload" | tail -1 ;;
showdown) sit2; play "$M" call call; sleep 2; since "$M" "sits down|hand [0-9]+ for|turn of|CALL|CHECK|RAISE|FOLD|ALL_IN|decided|leaves"
  standup Alice Bob; sleep 1; since "$M" "leaves seat|settled \(NORMAL\).*holdem" | tail -4; check "$T0" Alice Bob ;;
showcards) # at the showdown each menu shows the other's two cards (slots 19/22/25, cmd = card ids) and his head's tooltip
  sit2; play "$M" call call; sleep 0.5; D=$(wc -l < "$B/Alice.log"); b Alice dump; sleep 0.5
  tail -n +"$D" "$B/Alice.log" | grep -E "^  slot (19|22|25|26|8|27|30|33):|Karten:|Hand:" | cut -c1-120
  sleep 4; standup Alice Bob; sleep 1; check "$T0" Alice Bob ;;
fold) sit2; play "$M" raise fold; sleep 1; since "$M" "turn of|RAISE|FOLD|decided"; standup Alice Bob; sleep 1; check "$T0" Alice Bob ;;
allin) sit2; play "$M" allin call; sleep 3; since "$M" "ALL_IN|CALL|decided|leaves"; standup Alice Bob; sleep 1; check "$T0" Alice Bob ;;
quit) sit2; F=$(wc -l < "$L"); waitfor "$F" "turn of" 30; who=$(tail -n +"$F" "$L" | grep "turn of" | tail -1 | sed -E 's/.*turn of ([A-Za-z]+).*/\1/')
  other=$([ "$who" = Alice ] && echo Bob || echo Alice); act "$who" call; sleep 1; echo "$other quits in the hand"; act "$other" quit; sleep 2
  since "$M" "decided|leaves|settled \(QUIT\)"; "$S/casinobot.sh" "$other" accept | tail -1; sleep 1; standup "$who"; sleep 1; check "$T0" Alice Bob ;;
leave) sit2; play "$M" call call; sleep 4; F=$(wc -l < "$L"); standup Bob; sleep 1; since "$F" "leaves seat"
  sleep 4; echo "alone at the table now: $(since "$F" 'hand [0-9]+ for' | wc -l) new hand(s) (want 0)"; standup Alice; sleep 1; check "$T0" Alice Bob ;;
runout) # Alice (400) and Bob (200) all-in; Alice drops during the run-out: her result is booked from the shoe
  chips Alice; chips Bob; at; T0=$(total Alice Bob); M=$(wc -l < "$L"); open Alice; buyin Alice 4; open Bob; buyin Bob 2
  waitfor "$M" "hand [0-9]+ for" 30; F=$(wc -l < "$L"); waitfor "$F" "turn of" 30
  first=$(tail -n +"$F" "$L" | grep "turn of" | tail -1 | sed -E 's/.*turn of ([A-Za-z]+).*/\1/'); second=$([ "$first" = Alice ] && echo Bob || echo Alice)
  sleep 0.6; act "$first" allin; G=$(wc -l < "$L"); waitfor "$G" "turn of $second" 10; sleep 0.6; act "$second" call; sleep 2
  echo "Alice drops during the run-out"; act Alice quit; sleep 9
  since "$M" "ALL_IN|CALL|decided|leaves seat|settled \\(QUIT\\)"; "$S/casinobot.sh" Alice accept | tail -1; sleep 1
  standup Bob; sleep 1; check "$T0" Alice Bob ;;
solo) chips Alice; q "tp Bob 200 -57 200"; sleep 0.5; q "tp Alice 90.5 -57 44.5 180 20"; sleep 0.6; M=$(wc -l < "$L")
  open Alice; b Alice click 81 right; b Alice click 49; sleep 0.6; echo "solo: $(since "$M" 'deals poker|sits down')"; sleep 14; b Alice click 51; sleep 4; b Alice close ;;
three) chips Alice; chips Bob; chips Carl; at Carl; T0=$(total Alice Bob Carl); M=$(wc -l < "$L"); open Alice; buyin Alice 2; open Bob; buyin Bob 2
  open Carl; buyin Carl 2; waitfor "$M" "hand [0-9]+ for" 30; play "$M" raise call fold; sleep 2
  since "$M" "hand [0-9]+ for|RAISE|CALL|FOLD|decided"; standup Alice Bob Carl; sleep 1; check "$T0" Alice Bob Carl ;;
*) echo "usage: holdem-test.sh setup|showdown|showcards|fold|allin|quit|leave|runout|solo|three"; exit 2 ;;
esac
