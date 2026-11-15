#!/bin/bash
# guide-pages-test.sh: opens the guide as bot Alice (default test instance, Alice online, standing on open ground)
# and turns to every page 1..34; prints per page whether its dialog arrived, and its body length. Step 10.
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh; A=$T/out/bots/Alice.log
$P rcon "gamemode survival Alice" "clear Alice" "tp Alice 60.5 -57 30.5 0 0" "casino give casino_guide Alice 1" >/dev/null
sleep 0.8; $P b Alice hotbar 0; sleep 0.3
m=$(wc -l < "$A"); $P b Alice place 60 -58 32 up; sleep 1.2
tail -n +"$m" "$A" | grep -q "DIALOG type" || { echo "the guide did not open"; exit 1; }
ok=1 # the opening dialog is page 1
echo "page 1: dialog (opening)"
for p in $(seq 2 34) 1; do
  m=$(wc -l < "$A"); $P b Alice customclick kaboofcasino:guide do=turn to=$p; sleep 0.7
  d=$(tail -n +"$m" "$A" | grep "DIALOG type" | tail -1)
  if [ -n "$d" ]; then ok=$((ok + 1)); fi
  echo "page $p: $([ -n "$d" ] && echo "dialog, ${#d} chars" || echo "NO DIALOG")"
done
$P b Alice customclick kaboofcasino:guide do=close >/dev/null
echo "dialogs: $ok / 35 (34 pages, page 1 again at the end)"
