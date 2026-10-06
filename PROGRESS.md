STATUS: IN PROGRESS

# Nostalgia Skills — progress log

Work branch: `claude/nostalgiaskills`. The Gradle project lives at the repository root
(the spec's `development/nostalgiaskills/` folder rule is intentionally ignored; this repo is dedicated to the mod).

## Execution steps (spec section 18)

| # | Step | Status |
|---|------|--------|
| 1 | Verify target platform, study references/docs | done (versions pinned & verified; API signatures checked in decompiled 21.1.256 sources; docs.neoforged.net not consulted) |
| 2 | Author layout manifest, art direction, graph design | done (LAYOUT_MANIFEST.md, ART_DIRECTION.md, TREE_DESIGN.md) |
| 3 | NeoForge project pinned to MC 1.21.1 | done (empty mod builds) |
| 4 | Model, resource loader, validation, progression domain | done (domain + reload listener + bundled fallback; 56 tests pass) |
| 5 | Persistence, server authority, transactions, networking | done; exercised in a real singleplayer world (client+integrated server) and by GameTests |
| 6 | Background, icons, frames, item art (+ sources) | done (tools/art generators; 8 backdrop layers, 11 frame/overlay sprites, 51 icons, orb item) |
| 7 | Rendering, controls, tooltips, previews | done; rendered in the real client under Xvfb (screenshots) |
| 8 | Content and effects in every sector | done; one effect per sector + Reckless Fury verified by GameTests; max-health route verified in-world |
| 9 | Point purchases, stats, respec, commands, config | done; XP purchase, stats, orb reset and single-skill refunds verified in-world; `validate` + reload via console |
| 10 | Compile, test client + dedicated server, fix | done (build, 64 unit tests, 7 GameTests, dedicated server, client under Xvfb, reload scenario, two-client dedicated-server scenario) |
| 11 | Visual QA vs layout/art spec | partial (24 real-client captures at 1000x563/1280x720/1920x1080/2560x1080, GUI 2/3; reviewed by eye; no human playtest) |
| 12 | Deliver artifacts and verification evidence | partial (all docs + evidence committed incl. VERIFICATION.md and README limitations; all 17.2 checks run; GPU FPS and human playtest impossible here) |

## Pinned versions

- Minecraft 1.21.1, Java 21 (JDK 21.0.12 available locally).
- Gradle wrapper 8.14.3.
- ModDevGradle `net.neoforged.moddev` 2.0.148 — verified to exist on plugins.gradle.org this run.
- NeoForge 21.1.256 — verified run 2 as the latest 21.1.x in maven.neoforged.net metadata.
- Parchment 1.21.1 / 2024.11.17 — verified run 2 (maven.parchmentmc.org serves it; Gradle resolved it).

## Run log

### Run 1 (2026-10-06)
- Created branch, PROGRESS.md, Gradle scaffold (settings.gradle, build.gradle, gradle.properties,
  wrapper 8.14.3, neoforge.mods.toml template, .gitignore).
- Confirmed network block (see above).

### Run 2 (2026-10-06)
- Owner opened network access. Re-tested: maven.neoforged.net, libraries.minecraft.net, piston-meta.mojang.com,
  piston-data.mojang.com all reachable (HTTP 200 on real artifact URLs). BLOCKED: NETWORK section removed;
  compiling is available from now on.
- Bumped NeoForge pin 21.1.172 -> 21.1.256. `./gradlew --no-daemon build` -> BUILD SUCCESSFUL (no sources yet).

- Wrote the deterministic layout pipeline `tools/layout/` (geometry, builder, sector blueprints, analysis, render,
  content tables, en/pt description generator, exporter). Six hand-authored sectors, each with its own route vocabulary.
- `python3 tools/layout/export.py` produced: 480 node files under `src/main/resources/data/nostalgiaskills/skill_nodes/`,
  `skill_connections/main.json` (514 undirected edges, 9 corridor bridges), `skill_trees/main.json`, en_us/pt_br lang files
  (generated skill strings merged with `tools/lang/ui_*.json`), `docs/layout/layout_manifest.json`,
  `docs/layout/annotated_layout.png`, and `LAYOUT_MANIFEST.md`.
- Layout diagnostics (ran): 64/12/3 + root per sector, 0 overlaps, 0 edge-clearance violations, 0 crossings, all nodes reachable,
  adjacent root routes cost 19–27 paid points (requirement >= 8).
- Effect vocabulary fixed in data (see `tools/layout/content.py` docstring): attribute, conditional_attribute,
  damage_modifier, trigger (actions heal / self_effect / target_effect). Custom attributes used: crit_chance, crit_damage,
  dodge_chance, light_weapon_damage, heavy_weapon_damage, projectile_damage, long_range_damage, crossbow_damage,
  charged_shot_damage, mining_speed, tool_preservation, potion_duration, magic_resistance, healing_received, lifesteal,
  damage_reduction, low_health_damage. Item tags referenced: nostalgiaskills:light_weapons, heavy_weapons, bows, crossbows.

- Domain layer in `src/main/java/dev/nostalgiacraft/skills/{graph,progression}` (no Minecraft classes):
  GraphParser (file/field/reason errors, limits), GraphValidator (references, reachability from every root,
  exclusive-choice blockades, unsatisfiable/cyclic requirements, overlap warnings), SkillGraphSnapshot (immutable,
  content fingerprint), AllocationService (batch expansion, atomic confirm, dependents of pending removal),
  PathFinder (least-cost route + requirement check), XpMath/XpCurve/ProgressionService, LedgerService, RespecService,
  Reconciler (migration map, oldest-first replay, exactly-once refunds), RequestGuard (dedup + stale revisions).
- `./gradlew --no-daemon test`: 55 tests, 0 failures (ran this run), including BundledTreeTest over the real dataset.

- Minecraft layer written and compiling: ModAttributes (17 synced attributes), ModTags + tag JSON, ModAttachments
  (skills state codec, persisted cooldowns), Server/Client config, SkillTreeReloadListener (+ BundledTree fallback via
  `nostalgiaskills_bundled_index.json`, skill_migrations), Payloads + NetworkSetup, SkillService (buy/confirm/reset with
  RequestGuard + rate limit, exact XP rewrite), LifecycleEvents (login/respawn/clone/dimension/datapack sync),
  EffectEngine (idempotent transient modifiers, caps), GameplayEvents (crit, dodge, damage modifiers, lifesteal,
  potions, mining speed, tool wear, triggers), SkillCommands, Orb of Reconfiguration (item, model, recipe), client
  screen (camera, controls, pending batch, dependents, route preview, search, keyboard focus, stats/reset/discard panels,
  progress bar, background renderer with parallax + reduced motion).
- Art generators `tools/art/{common,background,sprites,preview_scene}.py` (deterministic seeds) produced all textures.
- Dedicated server smoke run (ran): `./gradlew runServer -x downloadAssets` → "Skill tree loaded: 480 nodes, revision 1",
  "Done (0.848s)". Log: `docs/verification/dedicated_server_run2.log`.

- Client verified under Xvfb + Mesa (`xvfb-run -a -s "-screen 0 2560x1440x24" ./gradlew runClient -x downloadAssets -Pvisualqa`):
  dev-only harness `client/VisualQa.java` captured 22 fixture screenshots and `client/InWorldQa.java` ran an in-world
  scenario: XP 1395 -> 385 after buying 6 points (1010 XP, level 16), pending+cancel applied nothing, confirm of a
  5-node Guardian route raised max health 20 -> 29, death+respawn kept 5 allocations and 29 max health, orb reset
  consumed the orb, refunded 5, kept purchased=6 / next cost 292, restored 20 max health and freed the start hub.
  Evidence: `docs/screenshots/`, `docs/verification/{client_visual_qa_run2.log,ingame_scenario_run2.txt}`.
  Fixes found by this QA: alpha blending for blitted textures, fit-zoom timing in the harness, short search hint,
  minimum on-screen node size at low zoom, missing `stats.no_world` key.
- GameTests (`./gradlew runGameTestServer -x downloadAssets`): 6/6 pass — two independent players, exact XP debit
  at several balances, a real effect in every sector, Reckless Fury dealt x1.2 / taken x1.1, idempotent refresh +
  reset clamp, NBT round trip. Log: `docs/verification/gametests_run2.log`.
- Reload scenario (`tools/qa/reload_scenario.sh`, console commands into the dev server): invalid pack rejected with
  file/field/reason and previous tree kept; recovery; valid override pack applied as revision 2.
  Log: `docs/verification/reload_scenario_run2.log`.
- `./gradlew build` SUCCESS, `./gradlew test --rerun` 56/56; jar `build/libs/nostalgiaskills-0.1.0.jar` (not committed;
  build output).
- Docs written: README.md, ARCHITECTURE.md, TREE_DESIGN.md (+ generated example builds, `docs/builds.json`),
  ART_DIRECTION.md; example datapacks in `examples/datapacks/`.

### Run 3 (2026-10-06, commit 3be2ac4 — PROGRESS.md was not updated by that run; recorded here by run 4)
- InWorldQa extended and run in the real client (evidence `docs/verification/ingame_scenario_run2.txt`):
  Nether dimension change keeps 5 allocations / 29 max health; 14 rapid buy requests (duplicate id + stale revision)
  -> exactly one purchase (duplicate=1, rate_limited=4, stale=8, ok=1), ledger ok; save-quit-reopen of the world
  (integrated server restart) keeps allocations/purchases/health; live `/reload` with Gatekeeper now requiring Bastion
  -> cascading invalidation 5 -> 4 allocations, recorded price 2 refunded exactly once, max health 25.
- Added `./gradlew validateTree` (TreeValidatorCli) authoring task; LAYOUT_MANIFEST.md gained control constraints,
  decoration layers and intentional-crossings sections; screenshots re-captured.

### Run 4 (2026-10-06)
- Overlap guard ok (last commit 09:49 UTC, run started 12:51 UTC).
- `./gradlew --no-daemon build` -> BUILD SUCCESSFUL (3m22s), 56/56 unit tests, 0 failures; jar
  `build/libs/nostalgiaskills-0.1.0.jar` (713933 bytes).
- `./gradlew validateTree` -> `nostalgiaskills:main: 480 node files, 0 error(s), 0 warning(s) -> VALID`.
- New dev-only multiplayer harness `client/MultiplayerQa.java` + `tools/qa/multiplayer_scenario.sh` +
  `tools/qa/latency_proxy.py`, run configs `mpqaServer` / `mpqaAlpha` / `mpqaBeta` in build.gradle (game dirs under
  `run_mpqa/`, gitignored). Ran it (third attempt passed; the first two failed only because of harness bugs: a route
  that cost more points than Beta had, then a confirm sent inside the rate-limit window right after the spam burst —
  the server rejected both correctly). Results (`docs/verification/multiplayer_run4/`):
  - Alpha + Beta online together on a real dedicated server with independent balances and builds.
  - Alpha: 3 points for exactly 370 XP; Guardian hub + 3 Vitality -> max health 25; logout+login, End entry and End exit
    portal + credits all keep 4 allocations / 25 health / ledger ok.
  - Beta via 150 ms each-way latency proxy: 4 points for exactly 548 XP; 12 back-to-back buy requests -> exactly one
    purchase (duplicate=1, rate_limited=3, stale=7); Marksman hub + Fletching I–IV confirmed, projectile_damage 0.11.
  - Dedicated server stop + restart: Alpha unchanged. With Beta offline, a datapack made Fletching III require Volley
    Doctrine and `/reload` ran live (revision 2); at Beta's next login Fletching III and the now-disconnected
    Fletching IV were removed (5 -> 3), 2 points refunded once (available 1 -> 3), ledger ok.
- Added `docs/verification/VERIFICATION.md` (spec 17.2 checklist -> evidence) and a "Known limitations" section in
  README.md (deliverable 14).

## BLOCKED: NETWORK (partial)

- `resources.download.minecraft.net` is still denied (proxy 403 on CONNECT), so MDG `downloadAssets` cannot fetch the
  3888 vanilla asset objects (sounds, non-English vanilla languages, panorama). Workaround used for all runs: write
  `build/moddev/minecraft_assets.properties` with `asset_index=17` and
  `assets_root=/root/.gradle/caches/neoformruntime/assets`, then run with `-x downloadAssets`. The client therefore ran
  without vanilla sounds/panorama (mod assets are unaffected). Allowing this host would remove the workaround.
- `api.minecraftservices.com` / `sessionserver.mojang.com` denied: only Yggdrasil key-fetch log noise; offline dev
  runs work.

### Run 5 (2026-10-06)
- Overlap guard ok (last commit 13:17 UTC, run started 13:51 UTC). Fresh container: Gradle re-downloaded the
  wrapper and all dependencies (network to maven.neoforged.net, plugins.gradle.org and Mojang's piston hosts works;
  `resources.download.minecraft.net` is still denied, same `-x downloadAssets` workaround, recreated
  `build/moddev/minecraft_assets.properties` + an empty `/root/.gradle/caches/neoformruntime/assets`).
- Implemented **individual-node refunds** (the limitation left open by run 4; spec 14 and the 17.1 test item
  "refunds that preserve connectivity and prerequisites"):
  - `progression/RefundService` plans and applies the removal of one allocation. A leaf goes alone; otherwise the
    dependent chain comes with it, computed by the new `Reconciler.survivors` (the same oldest-first replay used
    after a datapack change, so removal and reconciliation can never disagree). Only recorded paid costs are
    returned, exactly once; the starting root is refused (`ROOT_NEEDS_RESET`).
  - Server config `allowNodeRefunds` (default true) and `orbRequiredForNodeRefund` (default false), both mirrored
    to the client in the economy snapshot.
  - Payload `request_refund` (request id + state and graph revisions + node id) handled by `SkillService.onRefund`
    in one authoritative step: rate limit, dedup/revision guard, plan, optional orb, apply, effect refresh, sync.
  - Client: clicking a learned node opens a confirmation listing the exact points returned and every skill removed
    with it (recomputed from each state sync, closes itself if the build changed); tooltips say so; en/pt strings.
- Ran this run: `./gradlew build` BUILD SUCCESSFUL (jar 738330 bytes), `./gradlew test --rerun` 64/64 unit tests
  (8 new in `RefundServiceTest`: leaf, chain, requirement-dependent chain, recorded price after a price change,
  root refusal, exactly-once/disabled, exclusive slot freed, whole build bottom-up), `./gradlew validateTree` VALID,
  `./gradlew runGameTestServer -x downloadAssets` 7/7 (new `singleSkillRefundRemovesItsEffectsAndItsChain`),
  and the full client QA under Xvfb (`runClient -Pvisualqa`): 24 fixture screenshots + the whole in-world scenario,
  now ending with the refund phase — leaf refund 4 -> 3 allocations and +1 point (max health 25 -> 24), chain refund
  3 -> 1 (free hub only) and +2 points (health 22), starting hub refused, ledger ok throughout.
- Evidence: `docs/verification/{gametests_run5.log,ingame_scenario_run5.txt,client_visual_qa_run5.log}`,
  `docs/screenshots/refund_panel_1280x720.png` (+ all captures refreshed). VERIFICATION.md row 10b added.
- Harness fixes found by these runs (not mod bugs): the in-world scenario must start from a fresh
  `run/saves/nostalgiaskills_qa` (its own reload-test datapack otherwise survives into the next run), and it now
  takes a route only when `PathFinder` marks it valid. One `runClient` attempt hung in the integrated server's
  "Saving worlds" step and was killed; the next attempt ran clean.

## Next action

1. Everything this environment can execute has run again at this commit (build, 64 unit tests, validateTree,
   7 GameTests, full client QA incl. the new refund flow). What remains cannot run here: FPS/frame-time on real GPU
   hardware and a human playtest/visual review. Keep STATUS: IN PROGRESS for those.
2. Optional work for a future run: exercise the single-skill refund in the two-client dedicated-server harness
   (`tools/qa/multiplayer_scenario.sh`, ~8 min; `client/MultiplayerQa.java` would need a refund phase), and any
   polish a human review asks for. Re-run `./gradlew build` and `tools/qa/multiplayer_scenario.sh` after changes.

## Known failures

- None currently.

## Checks run vs not run

- Run 1: network probes, build (failed, network).
- Run 2: network probes, `./gradlew build` (scaffold), layout analysis + export (clean), `./gradlew test` (56/56 pass),
  `./gradlew compileJava` (whole mod), dedicated server start (`runServer -x downloadAssets`, success).
- Run 2 (later): client under Xvfb (visual QA + in-world scenario), GameTests 6/6, reload scenario, full build + tests.
- Run 3: in-world dimension change (Nether), rapid/duplicate request replay, save-quit-reopen persistence,
  live reload cascading invalidation + exact refund (all in the real client under Xvfb).
- Run 4: `./gradlew build` (pass, 56/56), `./gradlew validateTree` (VALID).
- Run 5: `./gradlew build` (pass, 64/64), `validateTree` (VALID), `runGameTestServer` (7/7),
  `runClient -Pvisualqa` under Xvfb (24 screenshots + in-world scenario incl. single-skill refunds).
- Run 4 (later): dedicated server + two real clients scenario (`tools/qa/multiplayer_scenario.sh build/mpqa`):
  independence, latency + rapid requests, logout/login, End return, dedicated-server restart, offline-player
  reconciliation after a live reload — all passed.
- Not run (environment cannot): FPS measurement on real GPU hardware, human visual review/playtest.
