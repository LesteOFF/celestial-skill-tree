# Celestial Skill Tree

A NeoForge mod for **Minecraft Java 1.21.1** that turns your progression into a skill tree generated for each world, presented as a constellation
chart inside **The Astral Observatory**. Buy skill points with vanilla experience, plan a route across the
classes of your world, confirm it in one batch, and reshape your build with the Orb of Reconfiguration.

- 6 classes without other mods — Duelist, Guardian, Arcanist, Marksman, Artificer, Berserker — and a Mage class when
  Iron's Spells 'n Spellbooks, Ars Nouveau or Malum is installed (any one of them alone brings at least six themes and
  keystones of its own). Every class has the same shape: a hub, a straight trunk, themed branches that end in a
  notable, and up to 3 keystones at the tip. Together they form a web of up to 100 skills per class (120 for the Mage,
  which has the most content when every magic mod is present).
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

Install the jar in `mods/` on **both** the client and the server. No other mods are required. Every integration is
optional: KubeJS (2101.x) and the mods listed under [Integration hooks](#integration-hooks) are used only when installed.

## Building

```bash
./gradlew build            # Windows: gradlew.bat build  → build/libs/celestialskilltree-2.1.0.jar
./gradlew test             # 57 unit tests in test/, including trees generated from the shipped catalog
./gradlew validateTree     # validates the catalog and generates one tree outside the game
./gradlew previewTree      # draws the tree shape to build/preview/tree.png
./gradlew runGameTestServer  # 44 in-game tests on a real server with mock players
./gradlew runPackGameTestServer  # the same tests plus 3 integration tests, with the pack's mods (pack_mods_dir)
./gradlew runClient / runServer
```

The game tests and the in-game QA harness live in the `devHarness` source set (`src/devHarness`): every dev run loads
them, the release jar does not. `runPackGameTestServer` reads the pack's mods folder from `pack_mods_dir` in
`~/.gradle/gradle.properties` or from the `PACK_MODS_DIR` environment variable; nothing else needs it. The pack's jars
are found by name prefix (the `packTestMods` list in `build.gradle`), so updating a mod in the pack needs no change.
Mods the pack lacks go in `run-pack/mods`, which the game test server loads as its own mods folder: with Astral Sorcery
(plus ObserverLib) or No Man's Land (plus Mixed Litter) there, `SkyCompatGameTests` checks the sign bonus on discovery,
the eclipse on Astral Sorcery's solar eclipse day, the star shower on its lunar eclipse, the research tier condition and
rewards, and the sun dogs ending with an eclipse.

Releasing: `.github/workflows/celestial-skill-tree-ci.yml` runs `build validateTree` on every push and pull request that
touches the mod. Pushing a `celestial-skill-tree-v<version>` tag runs the release workflow: it attaches the jar to a GitHub
release and, when the repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` are set (with the
secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`), publishes it to Modrinth and CurseForge with the `CHANGELOG.md`
section of `mod_version` as the release notes.

Performance checks, each printing a median per call or per frame:

```bash
./gradlew benchProgression            # reconcile, refund, fallen star, batch confirm and bulk purchase on the shipped tree
./gradlew runGameTestServer -Pbench   # server hot paths with a full build (report: run/celestialskilltree_bench_server.txt)
./gradlew runClient -Prenderbench     # tree screen frame times at 1920x1080 (report: run/celestialskilltree_bench_client.txt)
```

With the Legendary Tabs jar in `run/mods`, `./gradlew runClient -Plegendarytabs` opens the inventory, checks the
Celestial Skill Tree tab (icon, tooltip) and clicks it (report: `run/celestialskilltree_legendarytabs.txt`, screenshots
`run/screenshots/gallery_legendary_tabs.png` and `run/screenshots/celestialskilltree_legendarytabs_tree.png`).

Pinned toolchain: Gradle 8.14.3 wrapper, ModDevGradle 2.0.148, NeoForge 21.1.256, Parchment 1.21.1-2024.11.17.

## Playing

1. Press **O** (rebindable under Controls → Celestial Skill Tree) or run `/celestialskilltree open`.
2. Click a **root hub** to choose your free starting point.
3. Earn XP normally, then press the gold **Buy points** button at the bottom of the screen. The first point costs 100 raw XP; each further point costs
   `100 + 20p + 2p²` (p = points already bought). The tooltip shows the cost, your XP and what remains.
   **Shift-click** buys as many points as your XP pays for in one go (the tooltip shows the count and the total XP
   first).
4. Click glowing (available) nodes to add them to a **pending** selection — nothing is applied yet. Pending nodes
   can chain into routes. **Shift-click** a distant node to add the cheapest valid route (its point cost is shown
   first).
5. **Confirm** (or Enter) sends the whole batch to the server, which accepts all of it or none of it.
   **Cancel** clears the pending selection.
6. The **stats** button (first in the left dock) lists your final attributes, the tree's share, conditional effects, active keystones, and points.
   With a pending selection it also shows each affected attribute before → after, caps included.
7. **Ranked skills.** Some notables have 2 to 5 ranks: Practiced Draw 5, Seasoned Hands 4, Layered Plates, Slow
   Steeping, Brute Training, Tool Care, Spell Hardening and Warding Rites 3, Blade Discipline, Iron Will, Blood Pact,
   Herbalism and Arcane Reservoir 2. Small
   pips under the node show learned ranks in gold and the pending one in cyan. Each click on a ranked skill marks one more rank
   (every rank costs the skill's price again and adds its effect once more), so one Confirm can buy several ranks; once
   every rank left is marked, the next click unmarks the extra ranks.
8. To unlearn **one** skill, click it in the tree (on a ranked skill that can still rank up, **shift-click** it): the confirmation lists exactly how many points come back (the
   price you actually paid, not the current one) and every learned skill that has to go with it, because the rest of
   your build must stay connected to your starting hub. Your starting hub itself can only be changed by a full reset.
9. To respec everything, use an **Orb of Reconfiguration** (crafted from 4 amethyst shards, an eye of ender and a
   gold ingot; with Astral Sorcery installed, infused from an eye of ender in its Starlight Infuser instead): all spent points are refunded and you may pick a new free starting hub. XP is not refunded and point
   prices do not restart. The orb is consumed only if the reset succeeds.

### Gates and freedom of choice

- **Gates.** Four portal skills sit halfway out on each side of the tree: the Eastern and Western Gates are linked to
  each other, and so are the Northern and Southern Gates. A gate costs 3 points, grants no bonus of its own and is drawn larger than a starting hub;
  learning one opens its twin on the far side at no extra cost, so the skills around both ends become available, and
  unlearning either end closes both. Hovering a gate lights up its twin; while the pair is selected or learned, two lanes
  of arrows flow both ways between them. The tooltip names the destination.
- **Two real choices per class, no caps.** Each class offers one **exclusive keystone pair** in its crown (for example
  the Berserker's Controlled Rage *or* Blind Rage): learning one shuts the other out until you unlearn it. Each class
  also has one **black hole** notable at the end of a branch, drawn as a gravity well (a small spinning accretion disk, matter spiralling in and a breathing lens ring): while you own it,
  the neighbouring skills you already have count extra (×1.25 to ×2 depending on the class; the tooltip shows the
  multiplier) and the ones you do not have are sealed (purple ⊘). Black holes
  only sit on dead-end tips, never on the way to other skills. Everything else can be learned, and the default point limit is 10,000, so one
  character can eventually learn every other skill. Each skill must still connect to one you already own. The XP price of a point keeps rising with every point bought (`100 + 20p + 2p²`); lower
  `curveQuadratic` in the server config if the whole tree should be a realistic goal.

### World features

- **Birth sign.** On your first login the world day and who you are decide one of twelve signs. Its small passive bonus is permanent,
  survives resets and is listed in **Show Stats**. The **Birth Sign** button (bottom left) opens your birth chart: a
  wheel of the twelve constellations with yours at the top; hover any sign to read its bonus.
- **Falling stars.** At night a falling star occasionally crosses the sky near a player in the Overworld: a bright
  head with a streak and a trail of sparks, a sound that follows it, and a burst where it lands. It leaves a
  Star Fragment. Using a fragment grants 1 skill point, never beyond the server's point limit.
- **Star shower.** Some nights are announced as a star shower: the sky and clouds are tinted, the stars shine
  brighter and falling stars come several times a minute until dawn.
- **Constellated monsters.** Rarely a monster spawns Constellated: +50% health, +25% damage (`eliteHealthBonus`, `eliteDamageBonus`) and the attribute bonuses of
  a small random constellation. It drops a Star Fragment, but only when a real player kills it: lava, falls and
  fake players (machines) get nothing. With `eliteRadarTeam` it joins a light purple scoreboard team, which radar mods
  such as Xaero's Minimap list as another team. Only monsters the world spawns on its own qualify (natural,
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

### Controls

The tree fills the whole screen and the controls float over it in small glass panels: search and the highlight filter
at the top left, a dock below it with stats, the shared constellation (greyed out when the server has none), your birth
sign, the hidden sky (greyed out until a boss mod adds hidden constellations), home, fit and help, and a capsule at the bottom with your unspent points (a twinkling star), **Buy points** and the XP progress to
the next point. **Confirm** and **Cancel** appear above it only while something is pending. Every keyboard shortcut below can be rebound
under Controls → Celestial Skill Tree; **H** or the **?** button lists them in game.

| Input | Action |
|---|---|
| Left-click node | pending node: add / remove (removing a node others depend on offers to remove the chain); learned node: open the unlearn confirmation |
| Shift + click | add the cheapest route to that node |
| Drag (left on empty space, or middle button), W A S D | pan |
| Mouse wheel, + / − | zoom (0.08×–3×; **Fit** may go lower) |
| Ctrl+Z / Ctrl+Y | undo / redo changes to the pending selection |
| Home / **Home** button | recentre on your starting hub |
| F / **Fit** button | frame the whole tree |
| Ctrl+F, then Enter (Shift+Enter: back) | search names, descriptions, effects, tags and class names; the field shows "3/12" or "none" |
| Filter button | highlight only available, learned or locked skills (alone or combined with the search) |
| Tab / Shift+Tab | jump to the next / previous result, zooming in if the tree is far away |
| Arrow keys, Space, T | move keyboard focus between nodes, mark the focused node, switch a toggleable node |
| Enter | confirm the pending batch |
| Shift (hovering) | show every effect of a skill in its tooltip |
| Esc, O | close (asks to discard or keep editing when changes are pending) |

Node states: locked (dim, padlock), available (bright rim), allocated (gold halo, lit route), pending (rotating dashed
cyan ring), conflicting (red ✕: the other keystone of a pair you took), sealed (purple ⊘: next to a black hole you own).
The screen does not pause the game. A skill's tooltip is a small card: the skill itself with its name, class · type and
price; what it does, one effect per line (▲ green for a gain, ▼ red for a cost); special rules (black hole with the
neighbours it boosts or seals, "boosted ×1.5 by", exclusive pair, portal, requirements); and what a click does now.

## Commands

| Command | Permission |
|---|---|
| `/celestialskilltree open` | everyone |
| `/celestialskilltree inspect <player>` | level 2 — ledger, sign, discoveries, switched-off skills, lifetime totals and the next point's price |
| `/celestialskilltree audit <player> [lines]` | level 2 — the last entries of the player's audit log (10 by default, up to 100) |
| `/celestialskilltree points add <targets> <amount> [override_limit]` | level 3 |
| `/celestialskilltree points set <targets> <available_amount> [override_limit]` | level 3 |
| `/celestialskilltree unlock <player> <node_id> [grant_cost]` | level 3 — prerequisites and connectivity always apply; `grant_cost` first grants exactly the node's price |
| `/celestialskilltree refund <player> <node_id>` | level 3 — unlearns the skill and everything that depends on it, refunding what was paid, even when player refunds are off and without an orb |
| `/celestialskilltree toggle <player> <node_id>` | level 3 — switches a toggleable skill on or off |
| `/celestialskilltree reset <targets>` | level 3 (no orb needed) |
| `/celestialskilltree cooldowns clear <targets>` | level 3 — every skill cooldown is ready again |
| `/celestialskilltree discoveries reveal <targets> <key>` | level 3 — reveals the hidden skills waiting for that key (an advancement id or a `celestialskilltree:kill/…`, `spell/…`, `worn/…` key) |
| `/celestialskilltree discoveries clear <targets>` | level 3 — forgets every discovery |
| `/celestialskilltree sign set <targets> <sign>` | level 3 — sets the birth sign |
| `/celestialskilltree sign reroll <targets>` | level 3 — draws another birth sign at random |
| `/celestialskilltree extrasign <targets> <true\|false>` | level 3 — grants or takes the second birth sign |
| `/celestialskilltree validate` | level 2 — load result, revision, and the first errors with file/field/reason |
| `/celestialskilltree integrations` | level 2 — installed integrations and whether each one is active or switched off |
| `/celestialskilltree regenerate confirm` | level 3 — draws the whole tree again from the current catalog, as a new world with this seed would get it; skills that leave the tree are refunded (offline players at their next login). Without `confirm` it only explains what will happen |
| `/celestialskilltree star <player>` | level 2 — a falling star lands near the player |
| `/celestialskilltree eclipse start\|stop` | level 2 — force an eclipse, or end the current sky event |
| `/celestialskilltree shower start\|stop` | level 2 — force a star shower, or end the current sky event |
| `/celestialskilltree community set\|add <xp>` | level 3 — set the shared constellation's donated XP, or add to it |

Every command also works with the shorter `/celestial` (for example `/celestial open`).

`points set` changes the available balance and books the difference as a net administrative grant; purchased points
and recorded prices are untouched (`available + spent = purchased + granted` always holds). Going above the point
limit requires the explicit `true` override argument. `<targets>` accepts any player selector (`@a`, a name, `@p`…).
Reloading the server config (editing the file while the server runs) re-prices the tree with the new cost overrides,
re-applies caps and tuning to every online player and resyncs them; no regeneration is needed.

## Configuration

- **Server** (`world/serverconfig/celestialskilltree-server.toml`, synced to clients): XP purchase on/off, point limit
  (10,000, effectively none), curve coefficients and discount, per-type cost overrides, keep progression on death, orb requirement,
  caps on what the tree grants (crit 50%, dodge 30%, lifesteal 10%, damage reduction 40%, magic resistance 60%,
  movement +50%; equipment is never clamped), `[tuning]` multipliers for every attribute the tree grants and for the caps, lifesteal
  cooldown, weapon/tool classification tags, request rate and batch-size limits. Section `[events]`: falling stars
  (on, one per ~30 minutes of night per player), Constellated monster chance (2%) and size (8 skills), `eliteSpawnerMobs` (off: spawner monsters are never Constellated), eclipses (on,
  every ~20 days, `eclipseMonstersSpawn` on, `eclipseFollowsAstralSorcery` on), star showers (on, every ~12 days, 4 stars per minute per player,
  `starShowerFollowsAstralSorcery` on),
  `dropStarOnDeath` (`OFF`, `PVP_ONLY`, `ALWAYS`) and the shared constellation (on).
- **Client** (`config/celestialskilltree-client.toml`, also editable in game from the Mods list): `[tree]` frame scale,
  zoom limits, zoom step, WASD pan speed, always-detailed tooltips (otherwise hold Shift); `[look]` glow intensity (0
  turns every glow off), background decoration, reduced motion, high contrast (brighter locked nodes, connections and
  secondary text); `[feedback]` interface sound volume. Client settings never affect
  costs, rules or bonuses.

## Data packs

The tree is generated from a **catalog** of ordinary data files, then saved with the world:

| Folder | Content |
|---|---|
| `data/<ns>/skill_classes/<id>.json` | a class: `name`, `icon`, `hub` (its effects), `trunk` (`name` and effects of the skill repeated along the trunk); optional `priority`, `fallback_class`, `nodes` |
| `data/<ns>/skill_themes/<path>.json` | a branch theme: `class`, `name`, `icon`, `minor` (effects of the numbered skills), optional `weight` and `notables` (`id`, `name`, effects, optional `max_rank` 1–5: every rank costs the skill's price and adds its effects once more, optional `neighbour_multiplier` above 1 to make it a black hole, which the generator only places on a dead-end tip) |
| `data/<ns>/skill_keystones/<path>.json` | a keystone: `class`, `name`, `text` (required plain-language sentence), effects; optional `icon`, `weight`, `neighbour_multiplier`, `hidden_until`, `toggleable` (the owner switches it off and on for free), `exclusive_with` (another keystone id of the same class: when both are drawn they form a pair and only one may be learned; paired keystones do not take room from the branches) |
| `data/celestialskilltree/skill_rules/main.json` | `nodes_per_class` (12 to 130, default 100; a class file's `nodes` overrides it for that class), `max_classes` (default 10), `min_themes_for_own_class` (default 4), `max_copies_per_skill` (default 5), `vary_by_seed`, `portals`. The mod ships this file with the defaults |

- **Format version.** Any data file may carry `"schema_version": 1`; without it version 1 is assumed, and any other
  number is rejected with a clear error.
- **Mods.** Any file may carry `"requires_mod"` (one id or a list, all needed) or `"requires_any_mod"` (one of the list
  is enough). A file whose mod is missing is skipped before it is read, so it may use that mod's attributes.
- **Which classes a world gets.** Classes with at least `min_themes_for_own_class` themes enter, highest `priority`
  first, up to `max_classes`. Themes and keystones of a class that did not enter move to its `fallback_class`.
- **The draw.** Everything the catalog offers a class appears in every world, as long as it fits: each branch ends
  in a notable, the notables left over open a branch (and, on long branches, sit in the middle), and up to 100
  keystones fan out at the tip. Which notable goes where is drawn by `weight` with the world seed. The minor skills
  never repeat inside one trunk or branch while the class has other themes to offer, the least used ones first, and
  the skill next to a notable is of that notable's theme. No skill appears more than `max_copies_per_skill` times in
  a class. An existing world keeps its draw: a notable added to the catalog later only fills a notable spot that the
  world never had (a new world, or a branch that did not exist before), so no learned skill disappears. To give an existing world the new catalog entries, run
  `/celestialskilltree regenerate confirm`. Notables or keystones that still do not fit are reported as a warning. A class whose themes cannot fill
  its size under that limit becomes smaller. With `vary_by_seed` off every world of the pack gets the same tree.
- **Changes.** After `/reload` or a changed mod list, branches whose theme still exists stay as they are and only the
  emptied ones are drawn again. Skills that no longer exist are refunded at their paid price on the next login. A
  class entering or leaving changes the width of every slice, which shortens or lengthens the branches.
- **Texts.** A skill without `text` is described from its effects ("+5% Attack Speed (of base value) while holding a
  weapon in each hand"). `text` replaces that with a written sentence and the tooltip still shows the exact numbers (with detailed tooltips on)
  under it. Names and texts are translation keys; a missing key is shown as written.
- **Validation.** Invalid data is rejected with file, field and reason and the last valid tree stays active;
  `/celestialskilltree validate` shows the result. A skill that combines a trigger with other effects, or has three or
  more effects, only warns when it has no `text`.

Skill ids are `<class>/hub`, `<class>/trunk_<n>`, `<theme>_<n>`, `<theme folder>/<notable id>`, the keystone file id and
`<class>/portal`.

Birth signs live in `skill_signs` (`effects`, plus an optional `partner_effects` list of
`{ "requires_mod", "description", "effects" }`: the first entry whose mod is installed replaces the sign's effects and
description text, and an optional `bonus_effects` list of the same shape whose every installed entry adds its effects and
text) and shared-constellation tiers in `skill_community` (`xp_required`
plus `effects`); both use the same effect vocabulary and take their texts from `sign.<ns>.<id>.name` /
`.description` and `community.<ns>.<id>.name` / `.description`. `skill_migrations` renames skill ids.

Example: `examples/datapacks/celestialskilltree_catalog_example/` — smaller classes, one new Guardian theme and a
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
  code calls `Triggers.fire(player, "mypack:on_spell_cast", target)` (`dev.lesteoff.celestialskilltree.effects.Triggers`);
  an id nobody fires simply never runs.
- **Spell triggers and actions.** Iron's Spells 'n Spellbooks fires `celestialskilltree:on_spell_cast` and
  `celestialskilltree:on_mana_empty`; Ars Nouveau fires `celestialskilltree:on_glyph_cast` and
  `celestialskilltree:on_spell_resolve`. `celestialskilltree:on_spell_crit` fires when the tree's critical chance lands on
  spell damage. The action `restore_mana` (`amount` 0 to 10,000) refills Iron's mana and does nothing without that mod.
  Both integrations talk to the other mod by reflection, so they need no extra dependency, and switch themselves off
  with one warning if the installed version changes its API.
- **Spell damage.** Damage types in `#celestialskilltree:spell_damage` (Iron's schools and Ars Nouveau spells by default)
  roll the tree's critical chance and critical damage and feed lifesteal like melee does. Other mods add their types
  to that tag. Malum scythes (`#malum:scythe`) count as heavy weapons.
- **Events** on the NeoForge bus, all nested in `dev.lesteoff.celestialskilltree.api.SkillEvents`, server side.
  - Cancellable, posted before the change: `PointsBuying` (the purchase is refused with "blocked"), `NodesLearning`,
    `RefundRequested` (with the skills and points the refund would move), `EliteSpawning` (`setNodeCount` changes the
    size of the monster's constellation), `StarFalling`, `FallenStarDropping` (the skill is kept) and `TriggerFiring`
    (the action does not run; the cooldown is still spent).
  - After the change: `PointsBought`, `NodesLearned`, `NodesRefunded`, `BuildReset`, `NodesLost` (dropped as a Fallen
    Star), `PointsGranted` (`getPoints` may be negative, `getSource` is `COMMAND`, `ITEM` or `INTEGRATION`: fragments,
    Fallen Stars, the Cursed Ring and the admin commands), `DiscoveriesRevealed`, `CommunityTierUnlocked`,
    `SkyEventChanged`, `StarLanded` and `EliteSpawned`. Skills removed by reconciliation after a data pack change post
    no event.
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
- **Combat mods.** Better Combat fires `celestialskilltree:on_combo_finisher` on the last hit of a combo, and its weapon
  category classifies any weapon that is in neither the light nor the heavy weapon tag (the tag always wins): `sword`,
  `cutlass`, `dagger`, `rapier`, `sickle`, `claw`, `fist`, `katana`, `twin_blade`, `soul_knife` and `coral_blade` are
  light; `claymore`, `hammer`, `mace`, `axe`, `double_axe`, `heavy_axe`, `halberd`, `glaive`, `scythe`, `spear`, `lance`,
  `trident`, `anchor` and `battlestaff` are heavy. Scorched Guns
  fires `celestialskilltree:on_headshot` and `celestialskilltree:on_reload`, and its bullets count as the player's projectiles
  through `#celestialskilltree:counts_as_projectile` (add other guns' damage types there; a type in that tag counts even
  when the player is the direct attacker, which is how L2Complements' Sonic Shooter and its `minecraft:sonic_boom`
  get in). ParCool fires
  `celestialskilltree:on_wall_jump`, `celestialskilltree:on_roll` (breakfall) and the built-in `on_dodge`. Dummmmmmy shows a
  critical hit rolled by the tree as a critical number.
- **Curios.** An attribute effect on `curios:<slot>` (for example `curios:ring`) adds or removes slots of that type.
- **Other elite systems.** With `eliteStacking = EXCLUSIVE` (default) a monster that Champions or L2Hostility already
  made special, or that is an Apotheosis boss, is not Constellated as well; `STACK` keeps the rolls independent.
- **Tons of Enchants.** XP Sword Master, XP Mining Master and XP Bow Master only raise the experience earned; the
  price of a point does not change. Its Lifesteal is a bow enchantment that heals through the mod's own code, so it
  adds to the tree's lifesteal and is not limited by the 10% cap, which only clamps what the tree grants.
- **Events.** `SkillEvents.TreeCriticalHit` (`getPlayer`, `getVictim`, `getMultiplier`) is posted for every critical
  hit rolled by the tree.
- **Data for other mods.** The jar ships content that only the matching mod reads: an Apotheosis gem (`gems/stellar`)
  and affix (`affixes/constellation`), L2Artifacts stat types, a Truly Modular material made of Star Fragments and
  Iron's Jewelry materials. The `astral` enchantment is plain vanilla data. The Apotheosis gem and affix grant
  Apothic's critical chance, critical damage and dodge, since Apotheosis always ships with Apothic Attributes; the gem's
  model lives in `assets/apotheosis/models/item/gems`, the only place Apotheosis looks. The others grant this mod's own
  attributes directly, so they are not covered by the Apothic or Pufferfish redirection above. Apothic's attribute
  screen shows every attribute of this mod with a description.
- **Boss constellations.** With the matching mod installed, 28 keystones stay hidden until the player earns the
  boss advancement (`hidden_until`): the Aether dungeons and Deep Aether's Brass Dungeon, Twilight Forest (Naga, Lich,
  Hydra, Ur-Ghast, Snow Queen), Cataclysm (six bosses), Ice and Fire (dragon, hydra, sea serpent), Mowzie's Mobs
  (Frostmaw, Ferrous Wroughtnaut, Umvuthi), Block Factory's Bosses (Kraken, Sandworm, Underworld Knight), Born in Chaos
  (Spider Warlord, Pumpkin Lord) and Aquamirae (Shipbreaker). A class holds up to 100 keystones; beyond the
  first nine they sit in wider rows of the crown, and every drawn keystone takes one of the class's node slots
  (hidden keystones take none, see below).
- **Hidden Sky.** A keystone with `hidden_until` is never drawn by the procedural generator and takes no node slot of
  its class. All of them sit in a fixed section far below the tree, one row per class in id order, so they are in the
  same place in every world. They belong to a sector of their own, **Hidden Sky**, and hang from nothing: a hidden
  keystone can be learned as soon as it is discovered, whatever the player owns of its class (this holds for any skill
  with `hidden_until`, hand-written trees included). A file whose `requires_mod` is missing is dropped before the tree is built, so the section only holds
  what the installed mods can unlock, and it does not exist at all when nothing qualifies. The **Hidden Sky** button
  (bottom left, shown only when the section exists) flies the camera there and back; the section is drawn in dark
  violet, undiscovered keystones show as dark silhouettes, and Fit frames only the main tree.
- **World themes.** `twilit` (Duelist, Twilight Forest), `faewild` (Arcanist, biomes of `c:is_magical`) and
  `otherworldly` (Artificer, Allthemodium's three dimensions) join `skyborn` (Artificer, Aether), `frostborn` (Guardian, cold biomes), `dune_runner` (Marksman, deserts)
  and `nether_forged` (Berserker, Nether) use `in_dimension` and `in_biome_tag` through the common tags `c:is_cold`,
  `c:is_desert` and `minecraft:is_nether`. `hearty_table` (Guardian, Farmer's Delight) fires Farmer's Delight's
  Comfort and Nourishment from triggers.
- **Loot.** Two global loot modifiers add Star Fragments and, rarely, Orbs: 6% and 1% on loot generated inside the
  structures of `#celestialskilltree:loot_structures` (vanilla, Moog's, Formations, Luki's and Aether Villages; Lootr uses
  the same loot tables), and 1% of a fragment per night catch with a fishing rod. Starcatcher gets a night-only epic
  catch (`starcatcher/fish/star_fragment`). Selling Bin is told the three items are worth 0, so they cannot be sold.
- **Other pack mods.** FTB Ultimine's `ftbultimine:max_blocks_modifier` is a skill effect (`veinbreaker`).
  Starcatcher's minigame attributes feed the `angler` theme. Tool Preservation now rolls
  once per block, so it also protects the tool when FTB Ultimine breaks many blocks in one tick.
- **Eating.** The `on_eat` trigger fires after any food or drink; Potion Duration lengthens the beneficial effects of
  food and wine too (Farmer's Delight, Vinery), the food tooltip says by how much, and the `gourmet` theme (Arcanist)
  builds on both.
- **Jade.** Looking at a player shows their sign and skill points spent; looking at a Constellated monster lists its
  bonuses. Everyone with the tree open gets "(looking at the stars: <class>)" beside their name tag, naming the class at the
  centre of their view, with or without WATUT. Looking at a Stellar Condenser or a Community Basin shows the points
  absorbed towards the next fragment or waiting to be donated; the tank and the output slot come from Jade itself.
- **Legendary Tabs.** A Celestial Skill Tree tab in the inventory opens the star chart. It is a datapack tab
  (`data/legendarytabs/tabs/celestial_skill_tree.json`), so it appears only with Legendary Tabs installed.
- **Sky events** (eclipse, star showers) and the `sky_event` condition only exist in the Overworld, so the Twilight
  Forest, the Aether and the other dimensions keep their own skies.
- **Tooltips.** Obscure Tooltips draws every item of this mod with a violet and gold frame (`tooltips/` in the assets).
  Simply Tooltips gives them its own `cosmic` theme (`assets/simplytooltips/item_themes/celestialskilltree.json`).
- **Item tags.** `#celestialskilltree:firearms` (Scorched Guns tiers and the Meka-Gun by default) is meant for `holding`.
  Truly Modular weapons and tools are listed in the weapon, bow, crossbow and mining tool tags; its modular armour
  matches `wearing` through miapi's own tags (`#miapi:armor`, `#miapi:helmets`, `#miapi:chestplates`,
  `#miapi:leggings`, `#miapi:boots`). Simply Swords follows its own tiers (light and medium weapons and the uniques
  are light, heavy weapons are heavy), Simply Cataclysm's claymores, greataxes, greathammers, glaives, halberds and
  scythes are heavy as well as light, and Mekanism Weapons' Meka-Tana and Meka-Bow are a light weapon and a bow. The
  Meka-Gun's laser names no attacker, so its shots get no projectile bonus. Farmer's Delight knives are light
  weapons. Three armour set tags ship for `wearing`, used by no bundled skill yet: `#celestialskilltree:armor/aredrite`
  (Oathbound Relics), `armor/terrible` and `armor/abyssal` (Aquamirae) and `armor/infinity` (Re-Avaritia).
- **Machines and farms.** A Constellated monster killed by a damage type of `#celestialskilltree:machine_damage` (LTX
  Industries turrets, which hurt in their owner's name) drops no fragment. A monster that spawns on a block of
  `#celestialskilltree:counts_as_spawner` (Excessive Utilities' Cursed Earth) follows `eliteSpawnerMobs` like a spawner
  mob. Fake players never fire `on_block_break`. Does Potato Tick stops block entities in the chunks it freezes, so a
  Stellar Condenser or Community Basin there stops absorbing until the chunk ticks again.
- **AttributeFix.** No conflict: it leaves the range of this mod's attributes untouched unless its own config says
  otherwise, and the caps in this mod's config only limit what the tree contributes.
- **Entity type tags.** `#celestialskilltree:never_constellated` keeps a monster from ever being Constellated; it ships
  with `#c:bosses`, the Wither, the Ender Dragon, the Warden and the Elder Guardian.
  `#celestialskilltree:first_kill_fragment` (same list plus Ice and Fire dragons and Aquamirae bosses) gives
  `bossFragments` Star Fragments (2 by default) the first time each player kills each of those monsters, straight to
  their inventory; the kill is remembered per player and survives death. `#celestialskilltree:always_drops_fragment`
  makes every kill by a player drop a Star Fragment; if that mob can be farmed, so can skill points. Mobs from spawners
  (or standing on `#celestialskilltree:counts_as_spawner`) drop nothing extra unless `eliteSpawnerMobs` is on. It ships empty.

- **Hidden keys beyond advancements.** `hidden_until` also takes `celestialskilltree:spell/<namespace>/<spell>` (the
  player casts that Iron's spell), `celestialskilltree:worn/<namespace>/<item>` (the player wears that item, as armour,
  as a curio or as a charm inside a worn Pandora necklace or bracelet; `has_curio` sees those charms too) and `celestialskilltree:kill/<namespace>/<entity>` (the player kills that creature; with L2Hostility,
  `celestialskilltree:kill/l2hostility/<trait>` is also revealed for every trait of the slain mob). Shipped: `void_whisper`
  (Eldritch Blast), seven `stigma_of_*` keystones (one per Curse of Pandora curse, active only while the curse is
  worn), `chaos_heart` (Draconic Evolution's Chaos Guardian), `undying_spite` (a mob with L2Hostility's Undying
  trait) and over forty more gated on spells, bosses, artifacts, etchings, vestiges and runes of the pack's mods;
  each keystone's key is its `hidden_until` field in `data/celestialskilltree/skill_keystones/`.
- **Boss boons.** Trigger actions beside `heal`, `self_effect` and `target_effect`: `raise_ally` (`entity`,
  `duration_ticks`: a tamable creature rises where the trigger's target stood, tamed to the player, with Strength for
  that long; each player keeps at most 3 raised allies nearby and the oldest leaves when a fourth rises), `ignite_target` (`duration_ticks`), `gust` and `pull` (`amount` is the radius; enemies around are thrown back or
  dragged in), `burst` (`amount` of blast damage around the target), `stacking_self_effect` (as `self_effect`, one level higher
  each time up to `amplifier`), `blink_behind_target`, `launch_target` (`amount` is the upward speed), `smite_target` (a lightning bolt for
  `amount` damage), `lunge` (the player is thrown where they look at `amount` speed), `chaos_target` (at random:
  lightning, a launch, fire or a blast around the target), `area_effect` (a `target_effect` on every enemy within `amount` blocks), `web_target` (a
  cobweb at the target's feet, only where the player may build: claims and spawn protection apply), `cheat_death`
  (`amount` is the health left; like a totem it also puts out fire and gives 2 seconds of invulnerability) and, with Mowzie's Mobs, `sunstrike` (`amount` is the reach).
  `on_kill` passes the victim as target; `celestialskilltree:on_boon_key` fires when the player presses the Hidden Power
  key (G by default) and `celestialskilltree:on_fatal_blow` when damage would kill them. The condition `wet` holds in
  water or rain, and a trigger may carry a `condition` of its own. `cheat_death`, `burst`, `smite_target`,
  `chaos_target` and `raise_ally` need a `cooldown_ticks` of at least 20. Actions that hit "enemies around" spare
  villagers and other NPCs, armour stands, every owned creature, and other players unless PvP lets the player hurt them.
- **Iron's keystones.** `blood_for_mana` (action `blood_for_mana`: when mana runs out, health becomes mana) and
  `stellar_echo` (attribute `celestialskilltree:spell_echo_chance`, the chance a spell skips its cooldown).
- **Occultism.** The ritual `recipe/occultism/summon_constellated.json` runs `/celestialskilltree constellate`, which
  summons a monster of `#celestialskilltree:ritual_constellated` already Constellated. Sneak-using an Orb of
  Reconfiguration on your own familiar swaps your build with the one it keeps (`familiarSwapCooldownSeconds`).
- **Borrowed keystones.** Apotheosis affixes named `<namespace>:keystone/<path>` lend the keystone
  `<namespace>:<path>` while the item is equipped; 22 mythic affixes ship, four of them only with Curios or Iron's Spellbooks installed
  (`neoforge:conditions`). For a player who already learned it the lent
  keystone counts as one rank past the limit (shown as 2/1) and its effects apply twice; several items with the same
  affix still lend a single rank.
- **Other elite systems, continued.** A Constellated monster learns one more skill per `l2hostilityLevelsPerNode`
  L2Hostility levels, attacks faster in Better Mob Combat Reimagined, and shows a boss bar (`eliteBossBar`) that
  Enhanced Boss Bars draws with this mod's frame. Champions (Unofficial) gets a real affix, `celestialskilltree:astral`:
  each hit silences the newest skill of the player for a while. A copy of a Constellated monster (Industrial
  Foregoing's Mob Duplicator) loses the status.
- **Experience.** Relics share `relicXpShare` of their experience with the bearer. Enigmatic Legacy+'s XP Scroll and
  Scroll of Ignorance Curse, Reliquary's Hero's Medallion, Forbidden Arcanus' Xpetrified Orb (whole orbs are broken
  and the change comes back as experience) and
  any carried tank of a `#c:experience` fluid (`liquidXpMbPerPoint`) count towards buying points: the player's own
  experience is spent first. The Ring of the Seven Curses lends `cursedRingPoints` points while worn.
- **Items and blocks.** Living Fragment (grows with the experience you collect, then worth 2 points), Stellar Ammo
  (Scorched Guns: empowers the next gunshots), Stellar Condenser (liquid experience in, Star Fragments out; this
  automates points, `condenserXpPerFragment = 0` disables it), Community Basin (liquid experience to the shared
  constellation) and Telescope (at night under open sky it opens the tree over the real sky). With Create, the orb
  and Stellar Ammo also have Sequenced Assembly recipes (`recipe/create/`).
- **Bosses and pets.** Every player who damaged a `#celestialskilltree:first_kill_fragment` monster gets its first-kill
  fragments. Tamed creatures of `#celestialskilltree:inherits_tree` (Ice and Fire dragons, Companions) receive
  `petTreeShare` of their owner's tree bonuses. The condition `target_type` (`tag`, an entity type tag) and the theme
  `dragon_slayer` add damage against `#celestialskilltree:dragons`; dragon scales craft the orb.
- **Small ones.** `essence_tender` (Mystical Agriculture, attribute `celestialskilltree:harvest_bonus`); a second birth
  sign while `lastLifeObjective` says the player is on their last life (or `/celestialskilltree extrasign`); gestures
  with playerAnimator when a keystone is learned, the orb is used or a falling star appears over a player under open
  sky; a Supplementaries globe named "Celestial" shows
  the sky events.
- **Other skies.** With Astral Sorcery the eclipse happens on the day of its solar eclipse
  (`eclipseFollowsAstralSorcery`), every birth sign gains a bonus once its paired constellation is discovered (the
  birth chart tooltip names the constellation and says whether the bonus is active), and the
  conditions `astral_attuned` and `astral_discovered` (`constellation`, an Astral Sorcery constellation id;
  `astral_attuned` without one holds for any attunement) and `astral_tier` (`tier`, `shimmer` to `radiance`) are
  available. The star shower falls on the night of its lunar eclipse (`starShowerFollowsAstralSorcery`), each new
  research tier gives Star Fragments (`astralTierFragments`, 2) and so does each memorized constellation
  (`astralConstellationFragments`, 1; both in `[integrations]`, 0 turns them off). Three keystones exist only with it:
  Starborn (arcanist), Armara's Bulwark (guardian) and Radiant Sight (marksman), and three themes put its minor skills
  along the branches and a notable at a tip: Starlit Plating (guardian), Bootes' Hunt (marksman) and Starlight Lore
  (arcanist). The Orb of Reconfiguration is then made
  only in its Starlight Infuser. No Man's Land's sun dogs end when an eclipse starts. With Cirrus' angled orbits the moon disc still covers
  the sun.

Each integration was written against the classes, ids and data formats of the partner's jar. At start-up it checks
that the classes it uses exist and switches itself off with one warning if the installed version differs; an
unexpected error while running is logged once and only switches it off after 20 repeats. The log lists the active and
switched-off integrations when the server starts, and `/celestialskilltree integrations` (level 2) shows the same. `./gradlew runPackGameTestServer` starts a game test server with
a modpack's jars (path in `pack_mods_dir`, see Building) and checks that none of the installed integrations switched
itself off, that Relics experience reaches its bearer, that every KubeJS event reaches a script and that the KubeJS
catalog event can add a theme; the regular game tests also run there, with Apothic Attributes redirecting the tree's
attributes. Integrations whose mod is not in the pack (for example Pufferfish's Attributes) are not exercised.

### KubeJS

KubeJS is optional: without it nothing changes, with it server scripts get the events and a binding below. The
integration is built against the first KubeJS 2101 release (`2101.7.0-build.126`, Rhino `2100.2.5-build.46`) and
checked against the pack's (`2101.7.2-build.377`, Rhino `2101.2.7-build.85`); it uses only the plugin, event group and
binding registration API, which is identical in both. If a KubeJS build ever breaks that API the integration logs one
warning and switches itself off, the rest of the mod keeps working.

- **Events** (`CelestialSkillTreeEvents`, server scripts): `pointsBought` (`player`, `points`, `xpSpent`),
  `nodesLearned` (`player`, `nodes`), `nodesRefunded`, `buildReset` and `nodesLost` (`player`, `nodes`,
  `pointsReturned`), `skyEventChanged` (`server`, `before`, `now` as `none`, `eclipse` or `star_shower`),
  `starLanded` (`level`, `fragment`) and `eliteSpawned` (`monster`). `pointsGranted`, `discoveriesRevealed` and
  `communityTierUnlocked` hand the NeoForge event over as `event.event` (e.g. `event.event.player`,
  `event.event.points`). `pointsBuying`, `nodesLearning`, `refundRequested`, `eliteSpawning`, `starFalling`,
  `fallenStarDropping` and `triggerFiring` do the same and can be stopped with `event.cancel()`.
- **Binding** (`CelestialSkillTree`, server scripts): `hasNode(player, nodeId)`, `learnedNodes(player)`,
  `availablePoints(player)`, `sign(player)` (`null` before a sign is assigned), `hasExtraSign(player)`,
  `setExtraSign(player, granted)`, `skyEvent()`, `isConstellated(entity)` (for LootJS entity predicates) and
  `fireTrigger(player, trigger[, target])`, which runs every owned skill listening to that namespaced trigger.
- **Changing a build** from a script goes through the admin commands, e.g.
  `server.runCommandSilent('celestialskilltree unlock ' + player.username + ' celestialskilltree:some_node')`, so the
  ledger, effects and client sync stay consistent.
- **Catalog** (`CelestialSkillTreeEvents.catalog`, server scripts): `addClass(id, {...})`, `addTheme(id, {...})`,
  `addKeystone(id, {...})`, `removeClass(id)`, `removeTheme(id)`, `removeKeystone(id)` and `rules({...})`. The objects
  use the same fields as the data files above and go through the same validation. Adding an existing id replaces it,
  removing a class also drops its themes and keystones, and `rules` replaces the whole rules file. Other mods can do
  the same through the NeoForge event `SkillEvents.CatalogLoading`.

Example: `examples/kubejs/server_scripts/celestialskilltree_example.js`.

## Known limitations

- Learned skills can only be unlearned through a full reset with the Orb of Reconfiguration. Server operators can
  turn on single-skill refunds (`allowSingleSkillRefunds = true`), optionally charging an orb for them
  (`orbRequiredForNodeRefund = true`); a refund removes the chosen skill together with every learned skill that depends
  on it.
- The pack test run checks that each integration loads and stays enabled and exercises Relics, KubeJS and the
  attribute redirect; the gameplay of most other integrations (spell triggers, affixes, curios conditions) is still
  only checked by hand in game.
- Verified in a cloud container without a GPU: performance on real hardware (FPS, frame time) has not been measured,
  and no human playtest or human visual review has been done.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — persistence, networking, transactions, effects, reloading

## License

All Rights Reserved (see [LICENSE](LICENSE)). All art, data, sounds and code were created for this
project; every sound in `assets/celestialskilltree/sounds/` is synthesized. The eclipse, falling star and star shower
events and their sounds are inspired by Nyx by Ellpeck (MIT) and its port Hyxcate; no code or sound is copied from
them. The skill tree itself is inspired by Passive Skill Tree by Daripher.
