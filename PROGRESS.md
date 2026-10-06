STATUS: IN PROGRESS

# Nostalgia Skills — progress log

Work branch: `claude/nostalgiaskills`. The Gradle project lives at the repository root
(the spec's `development/nostalgiaskills/` folder rule is intentionally ignored; this repo is dedicated to the mod).

## Execution steps (spec section 18)

| # | Step | Status |
|---|------|--------|
| 1 | Verify target platform, study references/docs | partial |
| 2 | Author layout manifest, art direction, graph design | pending |
| 3 | NeoForge project pinned to MC 1.21.1 | done (empty mod builds) |
| 4 | Model, resource loader, validation, progression domain | pending |
| 5 | Persistence, server authority, transactions, networking | pending |
| 6 | Background, icons, frames, item art (+ sources) | pending |
| 7 | Rendering, controls, tooltips, previews | pending |
| 8 | Content and effects in every sector | pending |
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

## Next action

Continue step 2: write `tools/layout/generate_tree.py` (deterministic, seeded) that emits the 480-node graph
(positions, edges with intermediate points, clusters, keystone groups) into
`src/main/resources/data/nostalgiaskills/...` plus `docs/layout_manifest.json`, then `LAYOUT_MANIFEST.md`
and `ART_DIRECTION.md`.

## Known failures

- None currently.

## Checks run vs not run

- Run 1: network probes, build (failed, network).
- Run 2: network probes (all OK), `./gradlew --no-daemon build` (SUCCESS, scaffold only).
- Not run: any compile, unit test, client, dedicated server, in-game or visual check.
