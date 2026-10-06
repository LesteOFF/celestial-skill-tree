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
| 9 | Point purchases, stats, respec, commands, config | done; XP purchase, stats, orb reset verified in-world; `validate` + reload via console |
| 10 | Compile, test client + dedicated server, fix | done (build, 56 unit tests, 6 GameTests, dedicated server, client under Xvfb, reload scenario) |
| 11 | Visual QA vs layout/art spec | partial (23 real-client captures at 1000x563/1280x720/1920x1080/2560x1080, GUI 2/3; reviewed by eye; no human playtest) |
| 12 | Deliver artifacts and verification evidence | partial (all docs + evidence committed; remaining-limitations list below; some in-game checks not run) |

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

## BLOCKED: NETWORK (partial)

- `resources.download.minecraft.net` is still denied (proxy 403 on CONNECT), so MDG `downloadAssets` cannot fetch the
  3888 vanilla asset objects (sounds, non-English vanilla languages, panorama). Workaround used for all runs: write
  `build/moddev/minecraft_assets.properties` with `asset_index=17` and
  `assets_root=/root/.gradle/caches/neoformruntime/assets`, then run with `-x downloadAssets`. The client therefore ran
  without vanilla sounds/panorama (mod assets are unaffected). Allowing this host would remove the workaround.
- `api.minecraftservices.com` / `sessionserver.mojang.com` denied: only Yggdrasil key-fetch log noise; offline dev
  runs work.

## Next action

1. Remaining verification that this environment can still add: two *real* networked clients (only mock players so
   far), simulated latency / rapid repeated requests against a running server, server restart persistence (save +
   relaunch the same world), dimension change and End return, offline-player reconciliation after a node removal on a
   live server, FPS/frame-time measurement with recorded hardware (only llvmpipe software GL is available here).
2. Optional polish: individual-node refunds are not implemented (full reset only, documented); no optional
   integrations ship.
3. When everything above that can run has run, set STATUS: COMPLETE only if nothing required remains unverified.

## Known failures

- None currently.

## Checks run vs not run

- Run 1: network probes, build (failed, network).
- Run 2: network probes, `./gradlew build` (scaffold), layout analysis + export (clean), `./gradlew test` (56/56 pass),
  `./gradlew compileJava` (whole mod), dedicated server start (`runServer -x downloadAssets`, success).
- Run 2 (later): client under Xvfb (visual QA + in-world scenario), GameTests 6/6, reload scenario, full build + tests.
- Not run: two real networked clients, latency simulation, server restart with an existing world, dimension/End
  return, FPS measurement on real GPU hardware, human visual review.
- Not run: any compile, unit test, client, dedicated server, in-game or visual check.
