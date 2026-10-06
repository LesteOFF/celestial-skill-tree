STATUS: IN PROGRESS

# Nostalgia Skills — progress log

Work branch: `claude/nostalgiaskills`. The Gradle project lives at the repository root
(the spec's `development/nostalgiaskills/` folder rule is intentionally ignored; this repo is dedicated to the mod).

## Execution steps (spec section 18)

| # | Step | Status |
|---|------|--------|
| 1 | Verify target platform, study references/docs | partial |
| 2 | Author layout manifest, art direction, graph design | partial (layout + content + manifest done; ART_DIRECTION.md pending) |
| 3 | NeoForge project pinned to MC 1.21.1 | done (empty mod builds) |
| 4 | Model, resource loader, validation, progression domain | partial (pure-Java domain + 55 passing tests; Minecraft resource loader pending) |
| 5 | Persistence, server authority, transactions, networking | pending |
| 6 | Background, icons, frames, item art (+ sources) | pending |
| 7 | Rendering, controls, tooltips, previews | pending |
| 8 | Content and effects in every sector | partial (all 480 nodes have authored effect data; Java effect implementations pending) |
| 9 | Point purchases, stats, respec, commands, config | pending |
| 10 | Compile, test client + dedicated server, fix | pending |
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

## Next action

Step 4/5 Minecraft layer: `NostalgiaSkills` @Mod class; register custom attributes (17 ids listed above) and add them
to players (EntityAttributeModificationEvent); item tags `nostalgiaskills:light_weapons|heavy_weapons|bows|crossbows`
and damage-type tag `nostalgiaskills:magic`; server reload listener (AddReloadListenerEvent) reading
skill_trees/skill_nodes/skill_connections into GraphParser.Sources, keep last valid snapshot, fallback to bundled
tree; player data attachment (Codec for PlayerSkillState, copyOnDeath); payloads (RegisterPayloadHandlersEvent);
then effects engine, commands, config, Orb of Reconfiguration, client screen.

## Known failures

- None currently.

## Checks run vs not run

- Run 1: network probes, build (failed, network).
- Run 2: network probes (all OK), `./gradlew --no-daemon build` (SUCCESS, scaffold only), layout analysis + export (clean), `./gradlew test` (55/55 pass).
- Not run: any compile, unit test, client, dedicated server, in-game or visual check.
