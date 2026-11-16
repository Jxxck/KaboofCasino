#!/bin/sh
# release-notes.sh v<version>: the text of a GitHub release on stdout. Fails if the tag does not match the built jar
# or CHANGELOG.md has no section for the version.
set -eu
tag=${1:?usage: release-notes.sh v<version>}
version=${tag#v}
jar="build/libs/KaboofCasino-${version}.jar"
[ -f "$jar" ] || { echo "$jar is missing: tag $tag does not match the version in build.gradle.kts" >&2; exit 1; }
notes=$(awk -v v="$version" '
    /^## \[/ { on = (index($0, "## [" v "]") == 1); next }
    on { print }' CHANGELOG.md)
[ -n "$(printf '%s' "$notes" | tr -d '[:space:]')" ] || { echo "CHANGELOG.md has no section for $version" >&2; exit 1; }
printf '%s\n\n' "$notes"
cat <<TEXT
---

KaboofCasino is a Paper port of the mod [MineVegas Casino](https://www.curseforge.com/minecraft/mc-mods/minevegas-casino) by byMehd.
Put the jar into \`plugins/\`; the [README](https://github.com/${GITHUB_REPOSITORY:-Jxxck/KaboofCasino}#installation) has the steps.

SHA-256 of \`KaboofCasino-${version}.jar\`: \`$(sha256sum "$jar" | cut -d' ' -f1)\`
TEXT
