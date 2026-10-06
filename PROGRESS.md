STATUS: IN PROGRESS

# Nostalgia Skills — progress log

Work branch: `claude/nostalgiaskills`. The Gradle project lives at the repository root
(the spec's `development/nostalgiaskills/` folder rule is intentionally ignored; this repo is dedicated to the mod).

## Execution steps (spec section 18)

| # | Step | Status |
|---|------|--------|
| 1 | Verify target platform, study references/docs | partial |
| 2 | Author layout manifest, art direction, graph design | pending |
| 3 | NeoForge project pinned to MC 1.21.1 | partial |
| 4 | Model, resource loader, validation, progression domain | pending |
| 5 | Persistence, server authority, transactions, networking | pending |
| 6 | Background, icons, frames, item art (+ sources) | pending |
| 7 | Rendering, controls, tooltips, previews | pending |
| 8 | Content and effects in every sector | pending |
| 9 | Point purchases, stats, respec, commands, config | pending |
| 10 | Compile, test client + dedicated server, fix | pending |
| 11 | Visual QA vs layout/art spec | pending |
| 12 | Deliver artifacts and verification evidence | pending |

## BLOCKED: NETWORK

The sandbox network policy denies these hosts (proxy answers `403` to CONNECT):

- `maven.neoforged.net` (NeoForge, NeoForm runtime, MDG dependencies)
- `libraries.minecraft.net`
- `piston-meta.mojang.com`
- `docs.neoforged.net` (cannot consult docs online)

Reachable: `plugins.gradle.org`, `services.gradle.org`, `repo1.maven.org` (but Maven Central answered HTTP 429 rate limit once).

Failing command (run 2026-10-06):

```
./gradlew --no-daemon build
> Task :createMinecraftArtifacts FAILED
Could not resolve net.neoforged:neoform-runtime:2.0.31.
Could not GET 'https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom'.
Received status code 403 from server: Forbidden
```

Remedy for the owner: allow the hosts above in the cloud environment's Network access settings.

## Pinned versions

- Minecraft 1.21.1, Java 21 (JDK 21.0.12 available locally).
- Gradle wrapper 8.14.3.
- ModDevGradle `net.neoforged.moddev` 2.0.148 — verified to exist on plugins.gradle.org this run.
- NeoForge 21.1.172 — NOT verified online (maven.neoforged.net blocked). Chosen from knowledge of the 21.1.x release line; re-check when network allows.
- Parchment 1.21.1 / 2024.11.17 — NOT verified online.

## Run log

### Run 1 (2026-10-06)
- Created branch, PROGRESS.md, Gradle scaffold (settings.gradle, build.gradle, gradle.properties,
  wrapper 8.14.3, neoforge.mods.toml template, .gitignore).
- Confirmed network block (see above).

## Next action

Continue step 2: write `tools/layout/generate_tree.py` (deterministic, seeded) that emits the 480-node graph
(positions, edges with intermediate points, clusters, keystone groups) into
`src/main/resources/data/nostalgiaskills/...` plus `docs/layout_manifest.json`, then `LAYOUT_MANIFEST.md`
and `ART_DIRECTION.md`.

## Known failures

- `./gradlew build` fails: NeoForge maven blocked (see BLOCKED: NETWORK).

## Checks run vs not run

- Ran: network probes (curl), `./gradlew --no-daemon build` (failed as above).
- Not run: any compile, unit test, client, dedicated server, in-game or visual check.
