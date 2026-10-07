# Nostalgia Skills

A NeoForge mod for **Minecraft Java 1.21.1** that adds a passive skill tree generated for each world, presented as a constellation
chart inside **The Astral Observatory**. Buy skill points with vanilla experience, plan a route across the
classes of your world, confirm it in one batch, and reshape your build with the Orb of Reconfiguration.

- 6 classes without other mods — Duelist, Guardian, Arcanist, Marksman, Artificer, Berserker — and a Mage class when a
  supported magic mod is installed. Every class has the same shape: a hub, a straight trunk, themed branches that end
  in a notable, and up to 3 keystones at the tip. Together they form a web of up to 100 skills per class.
- The tree is drawn from a catalog using the world seed, so another world, or another set of mods, gets another tree.
- Every node has a real gameplay effect; keystones trade a strong advantage for a drawback.
- Server-authoritative: the client only previews; the server validates every purchase, allocation and reset.
- English (`en_us`) and Brazilian Portuguese (`pt_br`).

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 or newer 21.1.x (`[21.1.256,21.2)`) |
| Java | 21 (64-bit) |

Install the jar in `mods/` on **both** the client and the server. No other mods are required; KubeJS (2101.x) is
the only optional integration.

## Building

```bash
./gradlew build            # Windows: gradlew.bat build  → build/libs/nostalgiaskills-0.5.0.jar
./gradlew test             # 174 unit tests, including trees generated from the shipped catalog
./gradlew validateTree     # validates the catalog and generates one tree outside the game
./gradlew previewTree      # draws the tree shape to build/preview/tree.png
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
  10,000, so one character can eventually learn every skill. Each skill must still connect to one you
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
  caps on what the tree grants (crit 50%, dodge 30%, lifesteal 10%, damage reduction 40%, magic resistance 60%,
  movement +50%; equipment is never clamped), `[tuning]` multipliers for every attribute the tree grants and for the caps, lifesteal
  cooldown, weapon/tool classification tags, request rate and batch-size limits. Section `[events]`: falling stars
  (on, one per ~30 minutes of night per player), Constellated monster chance (2%) and size (8 skills), `eliteSpawnerMobs` (off: spawner monsters are never Constellated), eclipses (on,
  every ~20 days, `eclipseMonstersSpawn` on), star showers (on, every ~12 days, 4 stars per minute per player),
  `dropStarOnDeath` (`OFF`, `PVP_ONLY`, `ALWAYS`) and the shared constellation (on).
- **Client** (`config/nostalgiaskills-client.toml`): frame scale, zoom limits, glow intensity, background decoration,
  reduced motion, detailed tooltips, high contrast. Client settings never affect costs, rules or bonuses.

## Data packs

The tree is generated from a **catalog** of ordinary data files, then saved with the world:

| Folder | Content |
|---|---|
| `data/<ns>/skill_classes/<id>.json` | a class: `name`, `icon`, `hub` (its effects), `trunk` (`name` and effects of the skill repeated along the trunk); optional `priority`, `fallback_class`, `nodes` |
| `data/<ns>/skill_themes/<path>.json` | a branch theme: `class`, `name`, `icon`, `minor` (effects of the numbered skills), optional `weight` and `notables` (`id`, `name`, effects) |
| `data/<ns>/skill_keystones/<path>.json` | a keystone: `class`, `name`, `text` (required plain-language sentence), effects; optional `icon`, `weight`, `neighbour_multiplier`, `hidden_until` |
| `data/nostalgiaskills/skill_rules/main.json` | `nodes_per_class` (12 to 100, default 100), `max_classes` (default 10), `min_themes_for_own_class` (default 4), `max_copies_per_skill` (default 5), `vary_by_seed`, `portals` |

- **Mods.** Any file may carry `"requires_mod"` (one id or a list, all needed) or `"requires_any_mod"` (one of the list
  is enough). A file whose mod is missing is skipped before it is read, so it may use that mod's attributes.
- **Which classes a world gets.** Classes with at least `min_themes_for_own_class` themes enter, highest `priority`
  first, up to `max_classes`. Themes and keystones of a class that did not enter move to its `fallback_class`.
- **The draw.** Everything the catalog offers a class appears in every world, as long as it fits: each branch ends
  in a notable, the notables left over open a branch (and, on long branches, sit in the middle), and up to 100
  keystones fan out at the tip. Which notable goes where is drawn by `weight` with the world seed. The minor skills
  never repeat inside one trunk or branch while the class has other themes to offer, the least used ones first, and
  the skill next to a notable is of that notable's theme. No skill appears more than `max_copies_per_skill` times in
  a class. Notables or keystones that still do not fit are reported as a warning. A class whose themes cannot fill
  its size under that limit becomes smaller. With `vary_by_seed` off every world of the pack gets the same tree.
- **Changes.** After `/reload` or a changed mod list, branches whose theme still exists stay as they are and only the
  emptied ones are drawn again. Skills that no longer exist are refunded at their paid price on the next login. A
  class entering or leaving changes the width of every slice, which shortens or lengthens the branches.
- **Texts.** A skill without `text` is described from its effects ("+5% Attack Speed (of base value) while holding a
  weapon in each hand"). `text` replaces that with a written sentence and the tooltip still shows the exact numbers
  under it. Names and texts are translation keys; a missing key is shown as written.
- **Validation.** Invalid data is rejected with file, field and reason and the last valid tree stays active;
  `/nostalgiaskills validate` shows the result. A skill that combines a trigger with other effects, or has three or
  more effects, only warns when it has no `text`.

Skill ids are `<class>/hub`, `<class>/trunk_<n>`, `<theme>_<n>`, `<theme folder>/<notable id>`, the keystone file id and
`<class>/portal`.

Birth signs live in `skill_signs` (`effects` only) and shared-constellation tiers in `skill_community` (`xp_required`
plus `effects`); both use the same effect vocabulary and take their texts from `sign.<ns>.<id>.name` /
`.description` and `community.<ns>.<id>.name` / `.description`. `skill_migrations` renames skill ids.

Example: `examples/datapacks/nostalgiaskills_catalog_example/` — smaller classes, one new Guardian theme and a
keystone that stays hidden until an advancement.

### Integration hooks

An optional KubeJS integration ships (see below); packs and addons can build any other with these:

- **Damage type tags as scope.** A `damage_modifier` takes `"scope": "#<damage type tag>"` beside `melee`,
  `projectile`, `magic`, `fire` and `all`. A tag scope matches any damage of that tag the player deals or takes (spells,
  guns), while the named scopes still count only melee and projectile hits as dealt damage.
- **Conditions.** `in_dimension` (`dimension`), `in_biome_tag` (`tag`), `sky_event` (`event`: `eclipse` or
  `star_shower`, whatever dimension the player is in), `wearing` (`tag`, any armour slot), `attribute_at_least`
  (`attribute`, `value`), `dual_wielding` (both hands hold an item of the light or heavy weapon tag) and `has_curio`
  (`tag`; always false without Curios).
- **Triggers from other mods.** A `trigger` may be any namespaced id, e.g. `"mypack:on_spell_cast"`. It fires when
  code calls `Triggers.fire(player, "mypack:on_spell_cast", target)` (`dev.nostalgiacraft.skills.effects.Triggers`);
  an id nobody fires simply never runs.
- **Spell triggers and actions.** Iron's Spells 'n Spellbooks fires `nostalgiaskills:on_spell_cast` and
  `nostalgiaskills:on_mana_empty`; Ars Nouveau fires `nostalgiaskills:on_glyph_cast` and
  `nostalgiaskills:on_spell_resolve`. `nostalgiaskills:on_spell_crit` fires when the tree's critical chance lands on
  spell damage. The action `restore_mana` (`amount` 0 to 10,000) refills Iron's mana and does nothing without that mod.
  Both integrations talk to the other mod by reflection, so they need no extra dependency, and switch themselves off
  with one warning if the installed version changes its API.
- **Spell damage.** Damage types in `#nostalgiaskills:spell_damage` (Iron's schools and Ars Nouveau spells by default)
  roll the tree's critical chance and critical damage and feed lifesteal like melee does. Other mods add their types
  to that tag. Malum scythes (`#malum:scythe`) count as heavy weapons.
- **Events** on the NeoForge bus, all nested in `dev.nostalgiacraft.skills.api.SkillEvents`, server side and not
  cancellable: `PointsBought`, `NodesLearned`, `NodesRefunded`, `BuildReset`, `SkyEventChanged`, `StarLanded`,
  `EliteSpawned`. Skills removed by reconciliation after a data pack change, or dropped as a Fallen Star, post no
  event.
- **Apothic Attributes.** With that mod installed, skills granting critical chance, critical damage, dodge, lifesteal,
  mining speed and healing received raise the Apothic attribute of the same name instead of this mod's own, so each
  stat is rolled once and shown as one number. The caps still limit what the tree contributes, but the mechanics are
  then Apothic's: its mining speed applies to every tool, its lifesteal has no cooldown, and its critical hits do not
  fire `on_crit`. Turn it off with `redirectToApothicAttributes` in the `[integrations]` section.
- **L2DamageTracker.** Critical chance and critical damage raise its Weapon Crit Rate and Weapon Crit Damage
  (`redirectToL2DamageTracker`). It wins over Apothic Attributes for these two stats, so the tree feeds only one of them
  and a hit is never rolled twice.
- **Pufferfish's Attributes.** Lifesteal, mining speed, healing received and projectile damage are redirected the same
  way (`redirectToPuffishAttributes`); a flat bonus becomes a multiplier there, because those attributes scale another
  value. Apothic Attributes wins where both mods overlap. Resistances are not redirected: their formulas differ.
- **Combat mods.** Better Combat fires `nostalgiaskills:on_combo_finisher` on the last hit of a combo. Scorched Guns
  fires `nostalgiaskills:on_headshot` and `nostalgiaskills:on_reload`, and its bullets count as the player's projectiles
  through `#nostalgiaskills:counts_as_projectile` (add other guns' damage types there). ParCool fires
  `nostalgiaskills:on_wall_jump`, `nostalgiaskills:on_roll` (breakfall) and the built-in `on_dodge`. Dummmmmmy shows a
  critical hit rolled by the tree as a critical number.
- **Curios.** An attribute effect on `curios:<slot>` (for example `curios:ring`) adds or removes slots of that type.
- **Other elite systems.** With `eliteStacking = EXCLUSIVE` (default) a monster that Champions or L2Hostility already
  made special is not Constellated as well; `STACK` keeps the rolls independent.
- **Events.** `SkillEvents.TreeCriticalHit` (`getPlayer`, `getVictim`, `getMultiplier`) is posted for every critical
  hit rolled by the tree.
- **Data for other mods.** The jar ships content that only the matching mod reads: an Apotheosis gem (`gems/stellar`)
  and affix (`affixes/constellation`), L2Artifacts stat types, a Truly Modular material made of Star Fragments and
  Iron's Jewelry materials. The `astral` enchantment is plain vanilla data. All of them grant this mod's own
  attributes directly, so they are not covered by the Apothic or Pufferfish redirection above. Apothic's attribute
  screen shows every attribute of this mod with a description.
- **Boss constellations.** With the matching mod installed, 27 keystones stay hidden until the player earns the
  boss advancement (`hidden_until`): the Aether dungeons and Deep Aether's Brass Dungeon, Twilight Forest (Naga, Lich,
  Hydra, Ur-Ghast, Snow Queen), Cataclysm (six bosses), Ice and Fire (dragon, hydra, sea serpent), Mowzie's Mobs
  (Frostmaw, Ferrous Wroughtnaut, Umvuthi), Block Factory's Bosses (Kraken, Sandworm, Underworld Knight), Born in Chaos
  (Spider Warlord, Pumpkin Lord) and Aquamirae (Shipbreaker). A class holds up to 100 keystones; beyond the
  first nine they sit in wider rows of the crown, and every keystone takes one of the class's node slots.
- **World themes.** `skyborn` (Artificer, Aether), `frostborn` (Guardian, cold biomes), `dune_runner` (Marksman, deserts)
  and `nether_forged` (Berserker, Nether) use `in_dimension` and `in_biome_tag` through the common tags `c:is_cold`,
  `c:is_desert` and `minecraft:is_nether`. `hearty_table` (Guardian, Farmer's Delight) fires Farmer's Delight's
  Comfort and Nourishment from triggers.
- **Loot.** Two global loot modifiers add Star Fragments and, rarely, Orbs: 6% and 1% on loot generated inside the
  structures of `#nostalgiaskills:loot_structures` (vanilla, Moog's, Formations, Luki's and Aether Villages; Lootr uses
  the same loot tables), and 1% of a fragment per night catch with a fishing rod. Starcatcher gets a night-only epic
  catch (`starcatcher/fish/star_fragment`). Selling Bin is told the three items are worth 0, so they cannot be sold.
- **Tooltips.** Obscure Tooltips draws every item of this mod with a violet and gold frame (`tooltips/` in the assets).
- **Item tags.** `#nostalgiaskills:firearms` (Scorched Guns tiers by default) is meant for `holding`. Truly Modular
  weapons and tools are listed in the weapon, bow, crossbow and mining tool tags.
- **AttributeFix.** No conflict: it leaves the range of this mod's attributes untouched unless its own config says
  otherwise, and the caps in this mod's config only limit what the tree contributes.
- **Entity type tags.** `#nostalgiaskills:never_constellated` keeps a monster from ever being Constellated (bosses).
  `#nostalgiaskills:always_drops_fragment` makes every kill by a player drop a Star Fragment; if that mob can be
  farmed, so can skill points. Both ship empty.

### KubeJS

KubeJS is optional: without it nothing changes, with it server scripts get the events and a binding below. The
integration is built against the first KubeJS 2101 release (`2101.7.0-build.126`) and checked against the latest
(`2101.7.2-build.379`); it uses only the plugin, event group and binding registration API, which is identical in
both. If a KubeJS build ever breaks that API the integration logs one warning and switches itself off, the rest of
the mod keeps working.

- **Events** (`NostalgiaSkillsEvents`, server scripts, not cancellable): `pointsBought` (`player`, `points`,
  `xpSpent`), `nodesLearned` (`player`, `nodes`), `nodesRefunded` and `buildReset` (`player`, `nodes`,
  `pointsReturned`), `skyEventChanged` (`server`, `before`, `now` as `none`, `eclipse` or `star_shower`),
  `starLanded` (`level`, `fragment`) and `eliteSpawned` (`monster`).
- **Binding** (`NostalgiaSkills`, server scripts): `hasNode(player, nodeId)`, `learnedNodes(player)`,
  `availablePoints(player)`, `sign(player)` (`null` before a sign is assigned), `skyEvent()` and
  `fireTrigger(player, trigger[, target])`, which runs every owned skill listening to that namespaced trigger.
- **Changing a build** from a script goes through the admin commands, e.g.
  `server.runCommandSilent('nostalgiaskills unlock ' + player.username + ' nostalgiaskills:some_node')`, so the
  ledger, effects and client sync stay consistent.
- **Catalog** (`NostalgiaSkillsEvents.catalog`, server scripts): `addClass(id, {...})`, `addTheme(id, {...})`,
  `addKeystone(id, {...})`, `removeClass(id)`, `removeTheme(id)`, `removeKeystone(id)` and `rules({...})`. The objects
  use the same fields as the data files above and go through the same validation. Adding an existing id replaces it,
  removing a class also drops its themes and keystones, and `rules` replaces the whole rules file. Other mods can do
  the same through the NeoForge event `SkillEvents.CatalogLoading`.

Example: `examples/kubejs/server_scripts/nostalgiaskills_example.js`.

## Known limitations

- Single-skill refunds remove the chosen skill together with every learned skill that depends on it; picking which
  dependent to keep, or moving a build sideways without paying again, is not offered. Server operators can switch
  single-skill refunds off (`allowNodeRefunds = false`) or charge an orb for them (`orbRequiredForNodeRefund = true`),
  leaving the full reset as the only route.
- KubeJS is the only optional integration; compatibility was tested only with NeoForge 21.1.256 and this mod alone
  or with KubeJS `2101.7.0-build.126` and `2101.7.2-build.379`. Of the KubeJS events, `nodesLearned`, `buildReset`
  and `skyEventChanged` were exercised in a running server (simulated players); `pointsBought`, `nodesRefunded`,
  `starLanded` and `eliteSpawned` share the same forwarding code but have not been fired with KubeJS installed.
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
