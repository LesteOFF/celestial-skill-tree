# Nostalgia Skills

A NeoForge mod for **Minecraft Java 1.21.1** that adds a 480-node passive skill tree, presented as a constellation
chart inside **The Astral Observatory**. Buy skill points with vanilla experience, plan a route across six
specialisations, confirm it in one batch, and reshape your build with the Orb of Reconfiguration.

- 6 sectors — Duelist, Guardian, Arcanist/Alchemist, Marksman, Artificer/Explorer, Berserker — each with its own
  route shapes: 64 minors, 12 notables and 3 keystones per sector, plus a root hub.
- Every node has a real gameplay effect; keystones trade a strong advantage for a drawback.
- Server-authoritative: the client only previews; the server validates every purchase, allocation and reset.
- English (`en_us`) and Brazilian Portuguese (`pt_br`).

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 or newer 21.1.x (`[21.1.256,21.2)`) |
| Java | 21 (64-bit) |

Install the jar in `mods/` on **both** the client and the server. No other mods are required; there are no
optional integrations in this version.

## Building

```bash
./gradlew build            # Windows: gradlew.bat build  → build/libs/nostalgiaskills-0.1.0.jar
./gradlew test             # 56 domain unit tests, including validation of the full 480-node dataset
./gradlew runGameTestServer  # 6 in-game tests on a real server with mock players
./gradlew runClient / runServer
```

Pinned toolchain: Gradle 8.14.3 wrapper, ModDevGradle 2.0.148, NeoForge 21.1.256, Parchment 1.21.1-2024.11.17.

## Playing

1. Press **O** (rebindable under Controls → Nostalgia Skills) or run `/nostalgiaskills open`.
2. Click a **root hub** to choose your free starting point.
3. Earn XP normally, then press **Buy Skill Point**. The first point costs 100 raw XP; each further point costs
   `100 + 20p + 2p²` (p = points already bought). The tooltip shows the cost, your XP and what remains.
4. Click glowing (available) nodes to add them to a **pending** selection — nothing is applied yet. Pending nodes
   can chain into routes. **Shift-click** a distant node to add the cheapest valid route (its point cost is shown
   first).
5. **Confirm** (or Enter) sends the whole batch to the server, which accepts all of it or none of it.
   **Cancel** clears the pending selection.
6. **Show Stats** lists your final attributes, the tree's share, conditional effects, active keystones, and points.
7. To unlearn **one** skill, click it in the tree: the confirmation lists exactly how many points come back (the
   price you actually paid, not the current one) and every learned skill that has to go with it, because the rest of
   your build must stay connected to your starting hub. Your starting hub itself can only be changed by a full reset.
8. To respec everything, use an **Orb of Reconfiguration** (crafted from 4 amethyst shards, an eye of ender and a
   gold ingot): all spent points are refunded and you may pick a new free starting hub. XP is not refunded and point
   prices do not restart. The orb is consumed only if the reset succeeds.

### Controls

| Input | Action |
|---|---|
| Left-click node | pending node: add / remove (removing a node others depend on offers to remove the chain); learned node: open the unlearn confirmation |
| Shift + click | add the cheapest route to that node |
| Drag (left on empty space, or middle button) | pan |
| Mouse wheel | zoom around the cursor (0.2×–3×; **Fit** may go lower) |
| Home / **Home** button | recentre on your starting hub |
| F / **Fit** button | frame the whole tree |
| Ctrl+F, then Enter | search names, descriptions, effects and tags; Enter jumps to the next result |
| Arrow keys, Space | move keyboard focus between nodes, toggle the focused node |
| Enter | confirm the pending batch |
| Esc | close (asks to discard or keep editing when changes are pending) |

Node states: locked (dim, padlock), available (bright rim), allocated (gold halo, lit route), pending (rotating dashed
cyan ring), conflicting (red ✕, an exclusive keystone is already chosen). The screen does not pause the game.

## Commands

| Command | Permission |
|---|---|
| `/nostalgiaskills open` | everyone |
| `/nostalgiaskills inspect <player>` | level 2 |
| `/nostalgiaskills points add <player> <amount> [override_limit]` | level 2 |
| `/nostalgiaskills points set <player> <available_amount> [override_limit]` | level 2 |
| `/nostalgiaskills unlock <player> <node_id> [grant_cost]` | level 2 — prerequisites and connectivity always apply; `grant_cost` first grants exactly the node's price |
| `/nostalgiaskills reset <player>` | level 2 (no orb needed) |
| `/nostalgiaskills validate` | level 2 — load result, revision, and the first errors with file/field/reason |

`points set` changes the available balance and books the difference as a net administrative grant; purchased points
and recorded prices are untouched (`available + spent = purchased + granted` always holds). Going above the point
limit requires the explicit `true` override argument.

## Configuration

- **Server** (`world/serverconfig/nostalgiaskills-server.toml`, synced to clients): XP purchase on/off, point limit
  (120), curve coefficients and discount, per-type cost overrides, keep progression on death, orb requirement,
  caps (crit 50%, dodge 30%, lifesteal 10%, damage reduction 40%, magic resistance 60%, movement +50%), lifesteal
  cooldown, weapon/tool classification tags, request rate and batch-size limits.
- **Client** (`config/nostalgiaskills-client.toml`): frame scale, zoom limits, glow intensity, background decoration,
  reduced motion, detailed tooltips, high contrast. Client settings never affect costs, rules or bonuses.

## Data packs

The tree is ordinary data: `data/<namespace>/skill_trees`, `skill_nodes`, `skill_connections`, `skill_migrations`.
Override a node by shipping the same path in a higher-priority pack, then `/reload`. Invalid data is rejected with
file, field and reason and the last valid tree stays active. Examples:

- `examples/datapacks/nostalgiaskills_example_tweaks/` — makes *Keen Edge I* stronger and shows a migration entry.
- `examples/datapacks/nostalgiaskills_invalid_test/` — deliberately broken, for testing rejection.

Tools: `python3 tools/layout/export.py` regenerates all node/connection data, translations and the layout manifest
from the authored blueprints; `python3 tools/layout/builds.py` recomputes the example builds.

## Known limitations

- Single-skill refunds remove the chosen skill together with every learned skill that depends on it; picking which
  dependent to keep, or moving a build sideways without paying again, is not offered. Server operators can switch
  single-skill refunds off (`allowNodeRefunds = false`) or charge an orb for them (`orbRequiredForNodeRefund = true`),
  leaving the full reset as the only route.
- No optional integrations ship; compatibility was tested only with NeoForge 21.1.256 and this mod alone.
- Verified in a cloud container without a GPU: performance on real hardware (FPS, frame time) has not been measured,
  and no human playtest or human visual review has been done. See `docs/verification/VERIFICATION.md` for exactly
  what ran.

## Documentation

- [TREE_DESIGN.md](TREE_DESIGN.md) — sectors, effects, caps, example builds
- [ARCHITECTURE.md](ARCHITECTURE.md) — persistence, networking, transactions, effects, reloading
- [LAYOUT_MANIFEST.md](LAYOUT_MANIFEST.md) — every node and edge, coordinates, validation results
- [ART_DIRECTION.md](ART_DIRECTION.md) — backdrop composition, palette, sprites, asset pipeline
- [PROGRESS.md](PROGRESS.md) — what was verified, how, and what remains
- `docs/screenshots/`, `docs/verification/` — captures and test logs from real runs ([VERIFICATION.md](docs/verification/VERIFICATION.md) maps each in-game check to its evidence)

## License

All Rights Reserved (see `gradle.properties` → `mod_license`). All art, data and code were created for this project.
