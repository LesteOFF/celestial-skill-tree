STATUS: IN PROGRESS

# Nostalgia Skills — progress log

Work branch: `claude/nostalgiaskills`. The Gradle project lives at the repository root
(the spec's `development/nostalgiaskills/` folder rule is intentionally ignored; this repo is dedicated to the mod).

## Execution steps (spec section 18)

| # | Step | Status |
|---|------|--------|
| 1 | Verify target platform, study references/docs | done (versions pinned & verified; API signatures checked in decompiled 21.1.256 sources; docs.neoforged.net not consulted) |
| 2 | Author layout manifest, art direction, graph design | partial (layout + content + manifest done; ART_DIRECTION.md pending) |
| 3 | NeoForge project pinned to MC 1.21.1 | done (empty mod builds) |
| 4 | Model, resource loader, validation, progression domain | done (domain + reload listener + bundled fallback; 56 tests pass) |
| 5 | Persistence, server authority, transactions, networking | done in code (attachments, payloads, SkillService); not exercised in-game yet |
| 6 | Background, icons, frames, item art (+ sources) | done (tools/art generators; 8 backdrop layers, 11 frame/overlay sprites, 51 icons, orb item) |
| 7 | Rendering, controls, tooltips, previews | done in code (SkillTreeScreen etc.); not yet seen running |
| 8 | Content and effects in every sector | done in code (EffectEngine, GameplayEvents, Triggers); not exercised in-game yet |
| 9 | Point purchases, stats, respec, commands, config | done in code (buy/confirm/reset payloads, stats panel, orb item+recipe, 7 commands, server/client config) |
| 10 | Compile, test client + dedicated server, fix | partial (compiles; dedicated server starts and loads 480 nodes; client not run yet) |
| 11 | Visual QA vs layout/art spec | pending |
| 12 | Deliver artifacts and verification evidence | pending |

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

## BLOCKED: NETWORK (partial)

- `resources.download.minecraft.net` is still denied (proxy 403 on CONNECT). MDG's `downloadAssets` cannot fetch the
  3888 asset objects. Workaround used for server runs: write `build/moddev/minecraft_assets.properties` with
  `asset_index=17` and `assets_root=/root/.gradle/caches/neoformruntime/assets`, then `./gradlew runServer -x downloadAssets`.
  A client run would lack sounds and non-English languages; owner may allow this host to remove the limitation.
- `api.minecraftservices.com` denied (only affects Yggdrasil key fetch log noise; offline-mode server works).

## Next action

1. Client visual QA under Xvfb (Xvfb + Mesa are installed): add a dev-only harness gated by system property
   `nostalgiaskills.visualqa` that opens SkillTreeScreen with fixture states and saves screenshots at 1000x563,
   1280x720, 1920x1080 and two GUI scales into `docs/screenshots/`; guard `PacketDistributor.sendToServer` when there
   is no connection. Run with `xvfb-run ./gradlew runClient -x downloadAssets`.
2. NeoForge GameTests (`runGameTestServer`) with mock players: XP purchase, batch confirm, effects per sector,
   death/respawn persistence, orb reset, two players independent, reload with invalid JSON.
3. Docs: README.md, ARCHITECTURE.md, TREE_DESIGN.md (example builds via a script), ART_DIRECTION.md; example datapack.

## Known failures

- None currently.

## Checks run vs not run

- Run 1: network probes, build (failed, network).
- Run 2: network probes, `./gradlew build` (scaffold), layout analysis + export (clean), `./gradlew test` (56/56 pass),
  `./gradlew compileJava` (whole mod), dedicated server start (`runServer -x downloadAssets`, success).
- Not run yet: client, GameTests, in-game behaviour, visual QA, FPS measurements.
- Not run: any compile, unit test, client, dedicated server, in-game or visual check.
