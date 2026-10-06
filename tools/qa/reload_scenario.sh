#!/usr/bin/env bash
# Scripted dedicated-server check of /reload behaviour (valid change, invalid JSON, recovery).
# Usage: tools/qa/reload_scenario.sh OUT_LOG   (run from the repository root; needs build/moddev/minecraft_assets.properties
# when resources.download.minecraft.net is unreachable, see PROGRESS.md)
set -u
OUT=${1:-reload_scenario.log}
RUN=run
FIFO=$(mktemp -u)
mkfifo "$FIFO"
rm -rf "$RUN/world"
mkdir -p "$RUN"
echo "eula=true" > "$RUN/eula.txt"
printf 'online-mode=false\nspawn-protection=0\nlevel-type=minecraft\\:flat\n' > "$RUN/server.properties"
./gradlew --no-daemon runServer -x downloadAssets < "$FIFO" > "$OUT" 2>&1 &
GRADLE=$!
exec 3>"$FIFO"
wait_for() { timeout 300 bash -c "until grep -q \"$1\" \"$OUT\"; do sleep 2; done"; }
say() { echo ">>> $1" >> "$OUT.cmds"; echo "$1" >&3; sleep 3; }
wait_for "Done ("
say "nostalgiaskills validate"
cp -r examples/datapacks/nostalgiaskills_invalid_test "$RUN/world/datapacks/"
say "reload"   # /reload rescans world/datapacks and enables newly found packs
wait_for "Skill tree reload rejected"
say "nostalgiaskills validate"
say "datapack disable \"file/nostalgiaskills_invalid_test\""
wait_for "Skill tree loaded: 480 nodes, revision 1" # back to the shipped tree after removing the invalid pack
cp -r examples/datapacks/nostalgiaskills_example_tweaks "$RUN/world/datapacks/"
say "reload"
wait_for "revision 2"
say "nostalgiaskills validate"
say "stop"
exec 3>&-
wait $GRADLE
rm -f "$FIFO"
