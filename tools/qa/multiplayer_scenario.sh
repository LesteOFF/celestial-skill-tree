#!/usr/bin/env bash
# Two real clients against a real dedicated server (dev-only; see client/MultiplayerQa.java).
# Phase 1: Alpha (direct) and Beta (through a 150 ms each-way latency proxy) join together, buy points with XP and
#          confirm different builds; Beta fires rapid repeated buy requests; Alpha logs out/in and returns from the End.
# Phase 2: the server is stopped and restarted; with Beta offline, a datapack makes Beta's notable require a node Beta
#          does not own and /reload runs live; then both reconnect and report their state.
# Usage (repository root): tools/qa/multiplayer_scenario.sh OUT_DIR
# Needs build/moddev/minecraft_assets.properties when resources.download.minecraft.net is unreachable (see PROGRESS.md).
set -u
OUT=${1:-build/mpqa}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
SRV=run_mpqa/server
rm -rf run_mpqa
mkdir -p "$SRV" run_mpqa/alpha run_mpqa/beta
echo "eula=true" > "$SRV/eula.txt"
printf 'online-mode=false\nspawn-protection=0\nlevel-type=minecraft\\:flat\nmax-players=4\ndifficulty=peaceful\n' > "$SRV/server.properties"
# offline-mode UUIDs, as the server derives them
python3 - "$SRV/ops.json" <<'PY'
import hashlib, json, sys, uuid
def offline(name):
    h = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))
json.dump([{"uuid": offline(n), "name": n, "level": 4, "bypassesPlayerLimit": False} for n in ("Alpha", "Beta")], open(sys.argv[1], "w"))
PY
for c in alpha beta; do
  printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\npauseOnLostFocus:false\nsoundCategory_master:0.0\n' > "run_mpqa/$c/options.txt"
done

GW="./gradlew --no-daemon -x downloadAssets"
FIFO=$(mktemp -u); mkfifo "$FIFO"
start_server() { # $1 = log
  $GW runMpqaServer < "$FIFO" > "$1" 2>&1 &
  SERVER=$!
  exec 3>"$FIFO"
  timeout 600 bash -c "until grep -q 'Done (' '$1'; do sleep 2; done" || echo "server did not start" >> "$OUT/scenario.txt"
}
say() { echo ">>> $1" >> "$OUT/console_commands.txt"; echo "$1" >&3; sleep 3; }
stop_server() { say "stop"; exec 3>&-; wait "$SERVER"; }

python3 tools/qa/latency_proxy.py 25566 25565 150 > "$OUT/latency_proxy.log" 2>&1 &
PROXY=$!

# ---- phase 1
start_server "$OUT/server_phase1.log"
xvfb-run -a -s "-screen 0 1280x720x24" timeout 1500 $GW runMpqaAlpha -PmpqaAlpha=alpha > "$OUT/client_alpha.log" 2>&1 &
A=$!
sleep 20 # let the alpha invocation finish writing its run files before the second Gradle build starts
xvfb-run -a -s "-screen 0 1280x720x24" timeout 1500 $GW runMpqaBeta -PmpqaBeta=beta > "$OUT/client_beta.log" 2>&1 &
B=$!
wait $A $B
stop_server
cp run_mpqa/alpha/nostalgiaskills_mpqa_alpha.txt run_mpqa/beta/nostalgiaskills_mpqa_beta.txt "$OUT/" 2>/dev/null

# ---- phase 2: restart, live reload while Beta is offline, then reconnect
start_server "$OUT/server_phase2.log"
PACK="$SRV/world/datapacks/qa_offline_reconcile"
NODE="$PACK/data/nostalgiaskills/skill_nodes/marksman/nocking_point.json"
mkdir -p "$(dirname "$NODE")"
echo '{"pack":{"pack_format":48,"description":"QA: nocking point now requires volley doctrine"}}' > "$PACK/pack.mcmeta"
python3 - src/main/resources/data/nostalgiaskills/skill_nodes/marksman/nocking_point.json "$NODE" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); d["requirements"]["all_of"] = ["nostalgiaskills:marksman/volley_doctrine"]
json.dump(d, open(sys.argv[2], "w"), indent=2)
PY
say "reload"
timeout 120 bash -c "until grep -q 'revision 2' '$OUT/server_phase2.log'; do sleep 2; done"
xvfb-run -a -s "-screen 0 1280x720x24" timeout 1200 $GW runMpqaAlpha -PmpqaAlpha=alpha_verify > "$OUT/client_alpha_verify.log" 2>&1 &
A=$!
sleep 20
xvfb-run -a -s "-screen 0 1280x720x24" timeout 1200 $GW runMpqaBeta -PmpqaBeta=beta_verify > "$OUT/client_beta_verify.log" 2>&1 &
B=$!
wait $A $B
stop_server
cp run_mpqa/alpha/nostalgiaskills_mpqa_alpha_verify.txt run_mpqa/beta/nostalgiaskills_mpqa_beta_verify.txt "$OUT/" 2>/dev/null
kill $PROXY
rm -f "$FIFO"
