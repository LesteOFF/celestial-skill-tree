# Verification record

Every entry below was actually executed in this repository's cloud container (4 vCPU Xeon, no GPU, Mesa llvmpipe
under Xvfb; see `client_environment.txt`). Nothing here is assumed.

## Spec 17.2 in-game checks

| # | Check | How it ran | Evidence |
|---|-------|-----------|----------|
| 1 | Build | `./gradlew build` (run 4: BUILD SUCCESSFUL, 56/56 unit tests); `./gradlew validateTree` (480 nodes VALID) | PROGRESS.md |
| 2 | Dev client starts | real client under Xvfb, runs 2–4 | `client_visual_qa_run2.log`, `multiplayer_run4/client_*.qa.log` |
| 3 | Dedicated server without client classes | `runServer` / `runMpqaServer` | `dedicated_server_run2.log`, `multiplayer_run4/server_phase*.log` |
| 4 | Two players, independent balances and builds | GameTest (mock players) **and** two real clients (Alpha, Beta) online together on a dedicated server | `gametests_run2.log`, `multiplayer_run4/nostalgiaskills_mpqa_{alpha,beta}.txt` |
| 5 | XP purchase, selection, batch confirm | singleplayer + dedicated server; exact XP debits (370 for 3 points, 548 for 4) | `ingame_scenario_run2.txt`, `multiplayer_run4/` |
| 6 | Cancel pending without effects | singleplayer client | `ingame_scenario_run2.txt` |
| 7 | Real effect in every sector | GameTests (one effect per sector + Reckless Fury) | `gametests_run2.log` |
| 8 | Death/respawn, logout/login, dimension changes | death+respawn and Nether (singleplayer); logout+login, End entry and End exit portal + credits (dedicated server) | `ingame_scenario_run2.txt`, `multiplayer_run4/nostalgiaskills_mpqa_alpha.txt` |
| 9 | Server restart preserves state | integrated server save-quit-reopen; dedicated server stop + restart, both players reconnect | `ingame_scenario_run2.txt`, `multiplayer_run4/*_verify.txt` |
| 10 | Respec orb refund | singleplayer client: orb consumed, refund 4 points, purchase curve kept | `ingame_scenario_run2.txt` |
| 11 | Valid and invalid reload | dedicated server console script | `reload_scenario_run2.log` |
| 12 | Existing build after node change, cascading invalidation | live `/reload` with player online (singleplayer); live `/reload` with Beta **offline**, reconciled at next login: Fletching III now requires Volley Doctrine → Fletching III and the disconnected Fletching IV removed, 5 → 3 allocations, 2 points refunded once, ledger holds | `ingame_scenario_run2.txt`, `multiplayer_run4/nostalgiaskills_mpqa_beta_verify.txt` |
| 13 | Repeated requests under latency / rapid submission | singleplayer burst of 14; Beta behind `tools/qa/latency_proxy.py` (150 ms each way) sends 12 back-to-back buy requests → exactly 1 purchase (duplicate=1, rate_limited=3, stale_revision=7) | `ingame_scenario_run2.txt`, `multiplayer_run4/nostalgiaskills_mpqa_beta.txt` |
| 14 | No optional mods | every run above has only NeoForge + this mod | all logs |

Reproduce: `tools/qa/multiplayer_scenario.sh build/mpqa` (dedicated server + two clients, ~8 min),
`tools/qa/reload_scenario.sh`, `./gradlew runGameTestServer`, `./gradlew runClient -Pvisualqa`.

## Not verified here

- FPS / frame time on real GPU hardware (only llvmpipe software rendering exists in this container).
- A human playtest and human visual review; screenshots were reviewed by the agent only.
- Vanilla sounds and the vanilla panorama during dev runs (asset host `resources.download.minecraft.net` is blocked;
  mod assets are unaffected).
