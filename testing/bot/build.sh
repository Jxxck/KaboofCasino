#!/bin/bash
# Builds the pack-aware QuestBot (copy of PostMarkt/testing/bot/QuestBot.java plus resource-pack answers from the
# render research: -Dpackmode=accept|decline|fail|ignore) against PostMarkt's resolved MCProtocolLib classpath.
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CP=$(cat "${PTEST_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../PostMarkt/testing" && pwd)}/bot/build/cp.txt")
JDK=/usr/lib/jvm/temurin-25-jdk-amd64
rm -rf "$HERE/build/classes" && mkdir -p "$HERE/build/classes"
systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
  $JDK/bin/javac --release 25 -Xlint:all -Xlint:-options -encoding UTF-8 -d "$HERE/build/classes" -cp "$CP" "$HERE/QuestBot.java"
echo "$CP" > "$HERE/build/cp.txt"
echo "built $HERE/build/classes"
