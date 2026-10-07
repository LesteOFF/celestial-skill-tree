# Nostalgia Skills

A NeoForge mod for **Minecraft Java 1.21.1** that adds a 484-node passive skill tree, presented as a constellation
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
./gradlew build            # Windows: gradlew.bat build  → build/libs/nostalgiaskills-0.5.0.jar
./gradlew test             # 91 domain unit tests, including validation of the full 484-node dataset
./gradlew runGameTestServer  # 12 in-game tests on a real server with mock players
./gradlew runClient / runServer
```

Pinned toolchain: Gradle 8.14.3 wrapper, ModDevGradle 2.0.148, NeoForge 21.1.256, Parchment 1.21.1-2024.11.17.

## Playing

1. Press **O** (rebindable under Controls → Nostalgia Skills) or run `/nostalgiaskills open`.
2. Click a **root hub** to choose your free starting point.
3. Earn XP normally, then press **Buy Skill Point**. The first point costs 100 raw XP; each further point costs
   `100 + 20p + 2p²` (p = points already bought). The tooltip shows the cost, your XP and what remains.
   **Shift-click** buys as many points as your XP pays for in one go (the tooltip shows the count and the total XP
   first).
4. Click glowing (available) nodes to add them to a **pending** selection — nothing is applied yet. Pending nodes
   can chain into routes. **Shift-click** a distant node to add the cheapest valid route (its point cost is shown
   first).
5. **Confirm** (or Enter) sends the whole batch to the server, which accepts all of it or none of it.
   **Cancel** clears the pending selection.
6. **Show Stats** lists your final attributes, the tree's share, conditional effects, active keystones, and points.
   With a pending selection it also shows each affected attribute before → after, caps included.
7. To unlearn **one** skill, click it in the tree: the confirmation lists exactly how many points come back (the
   price you actually paid, not the current one) and every learned skill that has to go with it, because the rest of
   your build must stay connected to your starting hub. Your starting hub itself can only be changed by a full reset.
8. To respec everything, use an **Orb of Reconfiguration** (crafted from 4 amethyst shards, an eye of ender and a
   gold ingot): all spent points are refunded and you may pick a new free starting hub. XP is not refunded and point
   prices do not restart. The orb is consumed only if the reset succeeds.

### Gates and freedom of choice

- **Gates.** Four portal skills sit halfway out on each side of the tree: the Eastern and Western Gates are linked to
  each other, and so are the Northern and Southern Gates. A gate costs 3 points, grants no bonus of its own and is drawn larger than a starting hub;
  learning one opens its twin on the far side at no extra cost, so the skills around both ends become available, and
  unlearning either end closes both. Hovering a gate lights up its twin; while the pair is selected or learned, two lanes
  of arrows flow both ways between them. The tooltip names the destination.
- **No caps, no exclusive choices.** Nothing in the shipped tree excludes anything else and the default point limit is
  10,000, so one character can eventually learn all 484 skills (593 points). Each skill must still connect to one you
  already own. The XP price of a point keeps rising with every point bought (`100 + 20p + 2p²`); lower
  `curveQuadratic` in the server config if the whole tree should be a realistic goal.

### World features

- **Birth sign.** On your first login the world day decides one of twelve signs. Its small passive bonus is permanent,
  survives resets and is listed in **Show Stats**. The **Birth Sign** button (bottom left) opens your birth chart: a
  wheel of the twelve constellations with yours at the top; hover any sign to read its bonus.
- **Falling stars.** At night a falling star occasionally crosses the sky near a player in the Overworld: a bright
  head with a streak and a trail of sparks, a sound that follows it, and a burst where it lands. It leaves a
  Star Fragment. Using a fragment grants 1 skill point, never beyond the server's point limit.
- **Star shower.** Some nights are announced as a star shower: the sky and clouds are tinted, the stars shine
  brighter and falling stars come several times a minute until dawn.
- **Constellated monsters.** Rarely a monster spawns Constellated: +50% health, +25% damage and the attribute bonuses of
  a small random constellation. It drops a Star Fragment. Only monsters the world spawns on its own qualify (natural,
  world generation, structures, patrols, raids, reinforcements, jockeys); spawners, trial spawners, spawn eggs,
  commands and machines never produce one.
- **Eclipse.** Some days the moon slides over the sun at dawn and stays until dusk. The sky, clouds and fog turn
  dark, the stars come out and the world is lit like night: monsters spawn on the surface, undead do not burn and
  beds cannot be used. In exchange, resets and single-skill refunds need no Orb of Reconfiguration.
- **Shared constellation.** The **Community** button (bottom left) opens a path of stars, one per tier, that lights
  up as the server donates XP. Donate 100, 1,000 or everything you can; each tier reached gives every player a bonus.
  Donated XP is never returned.
- **Fallen Star** (off by default). If the server enables it, dying drops your newest stand-alone skill as a Fallen
  Star. Using your own star teaches the skill again (or returns its points if it no longer fits); anyone else gains its
  points instead.
- **Black hole skills and hidden constellations** are available to data packs (see below); the shipped tree has none.

### Controls

| Input | Action |
|---|---|
| Left-click node | pending node: add / remove (removing a node others depend on offers to remove the chain); learned node: open the unlearn confirmation |
| Shift + click | add the cheapest route to that node |
| Drag (left on empty space, or middle button) | pan |
| Mouse wheel | zoom around the cursor (0.08×–3×; **Fit** may go lower) |
| Home / **Home** button | recentre on your starting hub |
| F / **Fit** button | frame the whole tree |
| Ctrl+F, then Enter | search names, descriptions, effects and tags; Enter jumps to the next result |
| Arrow keys, Space | move keyboard focus between nodes, toggle the focused node |
| Enter | confirm the pending batch |
| Esc | close (asks to discard or keep editing when changes are pending) |

Node states: locked (dim, padlock), available (bright rim), allocated (gold halo, lit route), pending (rotating dashed
cyan ring), conflicting (red ✕; only data packs that add exclusive groups or black holes produce it). The screen
does not pause the game.

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
| `/nostalgiaskills star <player>` | level 2 — a falling star lands near the player |
| `/nostalgiaskills eclipse start\|stop` | level 2 — force an eclipse, or end the current sky event |
| `/nostalgiaskills shower start\|stop` | level 2 — force a star shower, or end the current sky event |
| `/nostalgiaskills community set <xp>` | level 2 — set the shared constellation's donated XP |

`points set` changes the available balance and books the difference as a net administrative grant; purchased points
and recorded prices are untouched (`available + spent = purchased + granted` always holds). Going above the point
limit requires the explicit `true` override argument.

## Configuration

- **Server** (`world/serverconfig/nostalgiaskills-server.toml`, synced to clients): XP purchase on/off, point limit
  (10,000, effectively none), curve coefficients and discount, per-type cost overrides, keep progression on death, orb requirement,
  caps (crit 50%, dodge 30%, lifesteal 10%, damage reduction 40%, magic resistance 60%, movement +50%), lifesteal
  cooldown, weapon/tool classification tags, request rate and batch-size limits. Section `[events]`: falling stars
  (on, one per ~30 minutes of night per player), Constellated monster chance (2%) and size (8 skills), eclipses (on,
  every ~20 days, `eclipseMonstersSpawn` on), star showers (on, every ~12 days, 4 stars per minute per player),
  `dropStarOnDeath` (`OFF`, `PVP_ONLY`, `ALWAYS`) and the shared constellation (on).
- **Client** (`config/nostalgiaskills-client.toml`): frame scale, zoom limits, glow intensity, background decoration,
  reduced motion, detailed tooltips, high contrast. Client settings never affect costs, rules or bonuses.

## Data packs

The tree is ordinary data: `data/<namespace>/skill_trees`, `skill_nodes`, `skill_connections`, `skill_migrations`.
Birth signs live in `skill_signs` (`effects` only) and shared-constellation tiers in `skill_community` (`xp_required`
plus `effects`); both use the same effect vocabulary as nodes and take their texts from `sign.<ns>.<id>.name` /
`.description` and `community.<ns>.<id>.name` / `.description`. A node may also carry `"neighbour_multiplier": 2.0`
(a black hole: adjacent skills you own count that many times, adjacent skills you do not own are sealed while it is
active) or `"hidden_until": "<advancement id>"` (the node is invisible until the player earns that advancement).
Any file in these folders may carry `"requires_mod": "modid"` (or a list of mod ids): it is skipped unless every
listed mod is installed, so a node can use another mod's attributes or effects without breaking the tree when that
mod is absent. Put the connections of such nodes in a connection file with the same `requires_mod`. Skills a player
owns from a mod that was removed are refunded at their paid price on the next login.
Override a node by shipping the same path in a higher-priority pack, then `/reload`. Invalid data is rejected with
file, field and reason and the last valid tree stays active. Examples:

- `examples/datapacks/nostalgiaskills_example_tweaks/` — makes *Keen Edge I* stronger and shows a migration entry.
- `examples/datapacks/nostalgiaskills_invalid_test/` — deliberately broken, for testing rejection.
- `examples/datapacks/nostalgiaskills_black_hole/` — turns the Death Wish keystone into a black hole.
- `examples/datapacks/nostalgiaskills_hidden_keystone/` — hides Arcane Ward until you brew a potion.

### Integration hooks

No integration with another mod ships, but packs and addons can build one with these:

- **Damage type tags as scope.** A `damage_modifier` takes `"scope": "#<damage type tag>"` beside `melee`,
  `projectile`, `magic`, `fire` and `all`. A tag scope matches any damage of that tag the player deals or takes (spells,
  guns), while the named scopes still count only melee and projectile hits as dealt damage.
- **Conditions.** `in_dimension` (`dimension`), `in_biome_tag` (`tag`), `sky_event` (`event`: `eclipse` or
  `star_shower`, whatever dimension the player is in), `wearing` (`tag`, any armour slot) and `attribute_at_least`
  (`attribute`, `value`).
- **Triggers from other mods.** A `trigger` may be any namespaced id, e.g. `"mypack:on_spell_cast"`. It fires when
  code calls `Triggers.fire(player, "mypack:on_spell_cast", target)` (`dev.nostalgiacraft.skills.effects.Triggers`);
  an id nobody fires simply never runs.
- **Events** on the NeoForge bus, all nested in `dev.nostalgiacraft.skills.api.SkillEvents`, server side and not
  cancellable: `PointsBought`, `NodesLearned`, `NodesRefunded`, `BuildReset`, `SkyEventChanged`, `StarLanded`,
  `EliteSpawned`. KubeJS reaches them with `NativeEvents.onEvent`. Skills removed by reconciliation after a data pack
  change, or dropped as a Fallen Star, post no event.
- **Entity type tags.** `#nostalgiaskills:never_constellated` keeps a monster from ever being Constellated (bosses).
  `#nostalgiaskills:always_drops_fragment` makes every kill by a player drop a Star Fragment; if that mob can be
  farmed, so can skill points. Both ship empty.

Tools: `python3 tools/layout/export.py` regenerates all node/connection data, translations and the layout manifest
from the authored blueprints. The shipped tree uses the Fibonacci layout (the blueprint
positions expanded by φ² with a logarithmic twist; same nodes, edges and costs). Running `export.py` writes the
blueprint positions back, and `gerar_fibonacci.ps1` reads its input from the shipped data, so run it only right
after `export.py` and then copy `resource_override/data` over `src/main/resources/data` again. Afterwards
`python tools/layout/portals.py` places the four gates in free space and links them, and the exclusive groups have
to be removed again (the blueprints still declare them).

## Known limitations

- Single-skill refunds remove the chosen skill together with every learned skill that depends on it; picking which
  dependent to keep, or moving a build sideways without paying again, is not offered. Server operators can switch
  single-skill refunds off (`allowNodeRefunds = false`) or charge an orb for them (`orbRequiredForNodeRefund = true`),
  leaving the full reset as the only route.
- No optional integrations ship; compatibility was tested only with NeoForge 21.1.256 and this mod alone.
- Verified in a cloud container without a GPU: performance on real hardware (FPS, frame time) has not been measured,
  and no human playtest or human visual review has been done.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — persistence, networking, transactions, effects, reloading

## License

All Rights Reserved (see `gradle.properties` → `mod_license`). All art, data and code were created for this project,
with one exception: the sounds in `assets/nostalgiaskills/sounds/` (falling star loop and impacts, eclipse
and star shower stingers) come from Hyxcate 1.2.4 by the Elite Modding Team, a fork of Nyx by Ellpeck (MIT). The
eclipse and falling star behaviour is also modelled on that mod. Confirm the fork's licence and keep this credit
before distributing a build that ships those files.
