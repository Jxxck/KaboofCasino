#!/bin/bash
# jukebox-test.sh <step>: the casino jukebox on the default test instance (casinobots Alice, Bob online), placed at
# 50 -57 20 facing the player. Steps: place | play | listen | controls | away | break
T=${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../PostMarkt/testing" && pwd)}; P=$T/ptest.sh
INST=$($P status | sed -n 's/^instance: \([^ ]*\).*/\1/p'); L=$INST/logs/latest.log
B=$T/out/bots
q() { $P rcon "$@" >/dev/null; }
b() { local who=$1; shift; $P b "$who" "$@"; sleep 0.4; }
since() { tail -n +"$1" "$L" | grep -E "$2" | sed -E 's/^.*\[KaboofCasino\] //' | cut -c1-170; }
bot() { tail -n +"$2" "$B/$1.log" | grep -E "$3" | cut -c1-170; }
open() { b "$1" hotbar 8; $P bx "$1" 5 'OPEN id' place 50 -57 20 south | grep -oE "OPEN id=[0-9]+.*" | cut -c1-120; sleep 0.4; }
case ${1:-} in
place)
  q "fill 49 -57 19 51 -55 21 air" "clear Alice" "gamemode survival Alice" "casino give jukebox Alice 1" \
    "tp Alice 50.5 -57 23.5 180 0"; sleep 0.6
  M=$(wc -l < "$L"); b Alice hotbar 0; b Alice place 50 -58 20 up; sleep 0.6
  since "$M" "placed jukebox|jukebox"
  echo "carriers: $($P rcon 'execute if block 50 -57 20 barrier' 'execute if block 50 -56 20 barrier' | grep -o 'Test passed\|Test failed' | tr '\n' ' ')" ;;
play) # tab 1 (Schallplatten), first track (disc 13)
  q "tp Alice 50.5 -57 23.5 180 0"; sleep 0.5; M=$(wc -l < "$L"); A=$(wc -l < "$B/Alice.log")
  open Alice; b Alice click 1; sleep 0.5; b Alice click 9; sleep 1.5
  since "$M" "jukebox #"; echo "Alice heard:"; bot Alice "$A" "jukebox\.|ACTIONBAR|STOP" | tail -6; b Alice close ;;
listen) # Bob walks in later: hears the same track from its beginning
  q "tp Bob 50.5 -57 200" ; sleep 0.5; Bb=$(wc -l < "$B/Bob.log"); q "tp Bob 52.5 -57 24.5 180 0"; sleep 2
  echo "Bob heard:"; bot Bob "$Bb" "jukebox\.|ACTIONBAR" | tail -4 ;;
controls) # stop, start, next, mode, range
  q "tp Alice 50.5 -57 23.5 180 0"; sleep 0.5; M=$(wc -l < "$L"); A=$(wc -l < "$B/Alice.log")
  open Alice; b Alice click 49; sleep 0.8; b Alice click 49; sleep 0.8; b Alice click 52; sleep 0.8
  b Alice click 47; sleep 0.5; b Alice click 53; sleep 0.8
  since "$M" "jukebox #"; echo "Alice:"; bot Alice "$A" "jukebox\.|STOP" | tail -8; b Alice close ;;
away) # out of the 48-block range: the music stops
  A=$(wc -l < "$B/Alice.log"); q "tp Alice 50.5 -57 120 180 0"; sleep 1.5
  echo "after leaving:"; bot Alice "$A" "STOP|jukebox\." | tail -3; q "tp Alice 50.5 -57 23.5 180 0" ;;
break)
  q "tp Alice 50.5 -57 22.5 180 0" "gamemode survival Alice" "clear Alice"; sleep 0.5; M=$(wc -l < "$L")
  b Alice dig 50 -57 20 6000; sleep 6
  since "$M" "broke|removed|jukebox"; echo "jukebox items: $($P rcon 'clear Alice paper[custom_data~{PublicBukkitValues:{"kaboofcasino:item":"jukebox"}}] 0' | grep -oE 'Found [0-9]+|No items')" ;;
*) echo "usage: jukebox-test.sh place|play|listen|controls|away|break"; exit 2 ;;
esac
