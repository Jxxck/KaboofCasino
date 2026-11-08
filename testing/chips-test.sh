#!/bin/bash
# chips-test.sh <step>: the seven games with chips (step 9) on the default test instance (machines of the test world).
# Bots Alice (and Bob for the derby group) online, survival.
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing; P=$T/ptest.sh
L=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p')/logs/latest.log
S=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)   # pk.sh and dk.sh
q() { $P rcon "$@" >/dev/null; }
b() { local who=$1; shift; $P b "$who" "$@"; sleep 0.35; }
n() { grep -cE "$1" "$L"; }
wait_for() { # wait_for <regex> <n0> <sec>: the next matching log line
  if timeout "$3" bash -c "until [ \$(grep -cE '$1' $L) -gt $2 ]; do sleep 0.3; done"; then
    grep -E "$1" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-230
  else echo "(nothing within $3 s)"; fi
}
chips() { # chips <player>: hotbar 0: 10x25, 1: 10x5, 2: 30x1, 3: 5x100, 4: 2x1000
  q "clear $1" "gamemode survival $1"; sleep 0.2
  q "casino give chip_25 $1 10" "casino give chip_5 $1 10" "casino give chip_1 $1 30" "casino give chip_100 $1 5" \
    "casino give chip_1000 $1 2"; sleep 0.3
}
have() { # have <player>: chips per denomination
  local out=""
  for d in 1 5 25 100 500 1000; do
    local c; c=$($P rcon "clear $1 paper[custom_data~{PublicBukkitValues:{\"kaboofcasino:item\":\"kaboofcasino:chip_$d\"}}] 0" \
      | grep -oE 'Found [0-9]+' | grep -oE '[0-9]+'); out="$out ${d}:${c:-0}"
  done; echo "$out"
}
value() { # value <player>: total chip value
  have "$1" | tr ' ' '\n' | awk -F: 'NF==2 {s += $1 * $2} END {print s}'
}
open_at() { # open_at <player> <tp> <x y z face>
  local who=$1 tp=$2; shift 2
  q "tp $who $tp"; sleep 0.6; b "$who" hotbar 8
  $P bx "$who" 5 'OPEN id' place "$@" | grep -o "OPEN id=[0-9]*"
}
case ${1:-} in
slot) # 32 chips (25 + 5 + 1 + 1) on the slot machine (slot.win-chance 100: a win, paid in mixed chips)
  chips Alice; v0=$(value Alice); echo "before: $(have Alice) = $v0"
  n0=$(n "spins slot machine"); s0=$(n "settled \(NORMAL\).*stops=")
  open_at Alice "2.5 -57 5.5 180 0" 2 -57 3 south
  b Alice click 81 right; b Alice click 82 right; b Alice click 83 right; b Alice click 83 right
  b Alice dump; b Alice click 40
  echo "slot: $(wait_for "spins slot machine" "$n0" 10)"
  echo "settled: $(wait_for "settled \(NORMAL\).*stops=" "$s0" 15)"
  sleep 1; b Alice close; v1=$(value Alice); echo "after: $(have Alice) = $v1 (delta $((v1 - v0)))"
  ;;
wheel) # 7 chips (5 + 1 + 1) per spin until a quarter segment (x0.25 / x0.5 / x0.75 / x1.5) is hit
  chips Alice
  for i in 1 2 3 4 5 6 7 8; do
    v0=$(value Alice); n0=$(n "spins fortune wheel"); s0=$(n "settled \(NORMAL\).*seg=")
    open_at Alice "11.5 -57 5.5 180 0" 11 -57 3 south >/dev/null
    b Alice click 82 right; b Alice click 83 right; b Alice click 83 right; b Alice click 49
    line=$(wait_for "spins fortune wheel" "$n0" 10); echo "wheel $i: $line"
    echo "  settled: $(wait_for "settled \(NORMAL\).*seg=" "$s0" 20)"
    sleep 1; b Alice close; v1=$(value Alice); echo "  value $v0 -> $v1 (delta $((v1 - v0)))"
    echo "$line" | grep -qE "×0,25|×0,5|×0,75|×1,5" && break
  done
  ;;
plinko) # 9 chips (5 + 4 x 1)
  chips Alice; v0=$(value Alice); n0=$(n "drops [0-9]+ ball"); s0=$(n "settled \(NORMAL\).*pocket=[0-9]+ ×")
  open_at Alice "22.5 -57 6.5 0 0" 22 -56 8 north
  b Alice click 82 right; for i in 1 2 3 4; do b Alice click 83 right; done; b Alice click 49
  echo "plinko: $(wait_for "drops [0-9]+ ball" "$n0" 10)"
  echo "  settled: $(wait_for "settled \(NORMAL\).*pocket=[0-9]+ ×" "$s0" 20)"
  sleep 1; b Alice close; v1=$(value Alice); echo "  value $v0 -> $v1 (delta $((v1 - v0)))"
  ;;
roulette) # 130 chips (100 + 25 + 5) on the first colour
  chips Alice; v0=$(value Alice); n0=$(n "spins roulette"); s0=$(n "settled \(NORMAL\).*colour=")
  open_at Alice "14.5 -57 15.5 180 0" 14 -57 13 south
  b Alice click 84 right; b Alice click 81 right; b Alice click 82 right; b Alice click 49
  echo "roulette: $(wait_for "spins roulette" "$n0" 10)"
  echo "  settled: $(wait_for "settled \(NORMAL\).*colour=" "$s0" 25)"
  sleep 1; b Alice close; v1=$(value Alice); echo "  value $v0 -> $v1 (delta $((v1 - v0)))"
  ;;
blackjack) # 7 chips (5 + 1 + 1) per hand until a natural blackjack (blackjack.bias 100 helps); stand otherwise
  chips Alice
  for i in $(seq 1 ${2:-30}); do
    v0=$(value Alice); n0=$(n "blackjack #[0-9]+: hand of Alice ends")
    open_at Alice "29.5 -57 8.5 180 0" 29 -57 6 south >/dev/null
    b Alice click 82 right; b Alice click 83 right; b Alice click 83 right; b Alice click 49
    sleep 9; b Alice click 49
    end=$(wait_for "blackjack #[0-9]+: hand of Alice ends" "$n0" 20)
    sleep 1; b Alice close; v1=$(value Alice)
    echo "hand $i: $end | value $v0 -> $v1 (delta $((v1 - v0)))"
    echo "$end" | grep -q "ends BLACKJACK" && { grep -E "settled .*result BLACKJACK" "$L" | tail -1 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-230; break; }
  done
  ;;
poker) # 25 + 5 + 1 = 31 ante, deal, call (2 x 31 more, paid by value)
  chips Alice; v0=$(value Alice); pn=$($S/pk.sh nends)
  q "tp Alice 90.5 -57 44.5 180 20"; sleep 0.6
  $S/pk.sh open Alice >/dev/null; b Alice click 81 right; b Alice click 82 right; b Alice click 83 right
  echo "poker: $($S/pk.sh deal Alice | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-200)"
  sleep 11; $S/pk.sh call Alice; sleep 0.5; echo "  after the call: $(have Alice)"
  echo "  end: $($S/pk.sh ends "$pn" 40 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-200)"
  sleep 1; b Alice close; v1=$(value Alice); echo "  value $v0 -> $v1 (delta $((v1 - v0)))"
  ;;
derby) # a group race: Alice 30 on lane 0, Bob 55 on lane 1
  chips Alice; chips Bob; a0=$(value Alice); b0=$(value Bob); dn=$($S/dk.sh nover)
  q "tp Alice 81.5 -57 60.5 180 0" "tp Bob 80.5 -57 60.5 180 0"; sleep 0.8
  $S/dk.sh open Alice >/dev/null; b Alice click 9; b Alice click 81 right; b Alice click 82 right; $S/dk.sh group Alice
  sleep 1
  $S/dk.sh open Bob >/dev/null; b Bob click 12; b Bob click 81 right; b Bob click 81 right; b Bob click 82 right
  $S/dk.sh group Bob
  sleep 1; grep -E "derby #1" "$L" | tail -4 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-200
  echo "derby: $($S/dk.sh over "$dn" 120)"
  sleep 3; b Alice close; b Bob close
  a1=$(value Alice); b1=$(value Bob); echo "  Alice $a0 -> $a1 ($((a1 - a0))), Bob $b0 -> $b1 ($((b1 - b0)))"
  grep -E "settled .*lane=" "$L" | tail -2 | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-200
  ;;
*) echo "usage: chips-test.sh slot|wheel|plinko|roulette|blackjack|poker|derby"; exit 2;;
esac
