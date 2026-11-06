#!/bin/bash
# casinobot.sh <Name> [accept|decline|fail|ignore]
# Starts the pack-aware bot against the DEFAULT PostMarkt test instance (127.0.0.1:25572) with the same argv markers
# and input file as "ptest.sh bot start", so "ptest.sh b|bx|bot stop" work with it. Waits for "LOGIN ok".
set -u
name=${1:?name}; mode=${2:-accept}
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
OUT=$T/out; mkdir -p $OUT/bots
[ -f "$HERE/bot/build/classes/QuestBot.class" ] || "$HERE/bot/build.sh"
CP=$(cat "$HERE/bot/build/cp.txt")
log=$OUT/bots/$name.log
: > "$OUT/in_$name"; touch "$log"; n0=$(wc -l < "$log")
echo "=== casinobot $name ($mode) started $(date -u +%H:%M:%S.%3N)Z" >> "$log"
( cd "$HERE/bot/build/classes" && exec setsid nohup env BI="$OUT/in_$name" BN="$name" BC="$CP" BM="$mode" \
    systemd-run --scope -q -p MemoryMax=1G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
    /bin/bash -c 'tail -f -n +1 "$BI" | exec /usr/lib/jvm/temurin-25-jdk-amd64/bin/java -Xmx256m -Dptest.bot="$BN" -Dport=25572 -Dpackmode="$BM" \
      -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp ".:$BC" QuestBot "$BN"' ) >> "$log" 2>&1 < /dev/null &
for i in $(seq 1 160); do
  sleep 0.25
  if tail -n +$((n0 + 1)) "$log" | grep -q 'LOGIN ok'; then echo "bot $name logged in ($mode)"; exit 0; fi
  if tail -n +$((n0 + 1)) "$log" | grep -q 'DISCONNECTED'; then tail -n 5 "$log"; echo "bot $name was disconnected"; exit 1; fi
done
tail -n 10 "$log"; echo "bot $name: no login after 40 s"; exit 1
