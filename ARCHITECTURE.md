# Architecture — Celestial Skill Tree

NeoForge 21.1.256 for Minecraft 1.21.1, Java 21, ModDevGradle 2.0.148. Package root `dev.lesteoff.celestialskilltree`.

```text
generation/   pure Java: catalog model and CatalogParser, TreeShapeGenerator (the web layout), TreeDrawer
              (seeded draw), TreeAssembler and TreeGenerator (catalog + seed -> validated graph), DrawJson
text/         pure Java: EffectPhrases and PhraseRenderer turn effects into sentences
graph/        pure Java: node/edge/effect model, EffectParser and JsonFields (JSON effects), GraphValidator,
              SkillGraphSnapshot, GraphCodec
progression/  pure Java: PlayerSkillState (ledger), AllocationService, Holdings, PathFinder, XpMath, XpCurve,
              ProgressionService, LedgerService, RespecService, RefundService, Reconciler, RequestGuard,
              RejectReason
data/         server resource loading: SkillTreeReloadListener (reads the catalog), GeneratedTree (generates
              per world), SkillTreeManager, WorldSkillData (saves the draw), McKnownIds
registry/     attributes, item tags, attachments + codecs, persisted cooldowns, Orb of Reconfiguration
network/      payload records + registration (client-bound handlers reach client code only through ClientHandlers)
server/       SkillRequests (authoritative intents), SkillService (state and graph sync), PlayerExperience, LifecycleEvents, RateLimiter, SkyEvents, ShootingStars,
              CommunityTree, XpWallets, TreeViewers
effects/      CompiledBuild, EffectEngine (attribute modifiers), CombatEvents, ConsumptionEvents, MiningEvents, EquipmentEvents, Triggers,
              BoonActions, EliteMobs, PetInheritance, BorrowedNodes, StolenNodes
block/        Telescope, Stellar Condenser and Community Basin (XpBasinBlockEntity)
entity/       FallingStar
api/          SkillEvents: NeoForge events other mods and scripts may listen to (cancellable "pre" events and "post" events)
compat/       one package per optional mod (apotheosis, arsnouveau, bettercombat, curios, ironsspellbooks, jade,
              occultism, …), registered by Integrations and guarded by OptionalEvents; most reach the other mod by
              reflection (Reflection). kubejs/ is the KubeJS plugin (script events + CelestialSkillTree binding), loaded
              by KubeJS itself through kubejs.plugins.txt
mixin/        LevelMixin (eclipse darkness on the server) and ClientLevelMixin (sky tint on the client)
command/      /celestialskilltree
config/       ServerConfig (gameplay, synced), ClientConfig (presentation only)
client/       ClientSkillState, ClientSetup (key mapping), screen/, render/
```

`src/devHarness` (not in the release jar) holds `gametest/` and the client QA harness (`VisualQa`, `InWorldQa`,
`MultiplayerQa`, `QaScript`) in the same packages; every dev run uses that source set.

`graph` and `progression` contain no Minecraft classes; they are unit-tested directly (`./gradlew test`, 49 tests in `test/`,
including trees generated from the shipped catalog for many seeds). The same classes run on the server (authoritative) and
on the client (previews only), so the client shows exactly the rules the server enforces.

## Data and loading

The tree is not authored node by node. `SkillTreeReloadListener` reads the catalog (`skill_classes`, `skill_themes`,
`skill_keystones`, `skill_rules`; formats in the README), drops files whose mod is missing, posts
`SkillEvents.CatalogLoading` so scripts and mods can edit the raw entries, and parses the result with
`CatalogParser`. `GeneratedTree` then calls `TreeGenerator` with the world seed and the draw saved in
`WorldSkillData`, on `ServerStartingEvent` (the seed and the save are needed), again on every `/reload`, and when the
server config is reloaded (`ServerConfigReload`, so new cost overrides apply at once with the same draw).

`TreeGenerator`: `ClassPool.select` picks the classes and moves orphan themes to fallback classes;
`TreeShapeGenerator` lays out hub, trunk, arc-shaped ribs, keystones and portals for that number of classes;
`TreeDrawer` gives each rib a notable and lets `MinorDeck` fill trunk and ribs with minor skills that do not repeat inside a run, at
most `max_copies_per_skill` of each, keeping every previous choice that is still valid; the class budget shrinks
until its themes can fill it;
`HiddenSection` places every keystone with `hidden_until` in fixed rows below the shape, outside the draw and the
class budget (`ClassPool.hidden`), unlinked and in the rootless `celestialskilltree:hidden` sector
(`NodeTraits.standsAlone` waives the connection rule);
`TreeAssembler` turns slots into `SkillNodeDefinition`s and connections; `GraphValidator` has the last word. Skill
ids come from the catalog entry (`<theme>_<n>`, not from the position), so `Reconciler` refunds exactly what
disappeared.

Only a fully valid graph replaces the active one (`SkillTreeManager`). Invalid data during a session keeps the
previous graph; on the first load skill features stay disabled with `error.celestialskilltree.tree_unavailable`. Every
problem is logged with file, field and reason and shown by `/celestialskilltree validate`.

`GraphJsonParser`, `NodeParser` and `ConnectionParser` (one JSON file per node) now live in the test sources only:
the unit tests of `progression` still build their small graphs with them.

The graph revision (`SkillTreeManager.revision()`) increments whenever the content fingerprint changes. After a
reload `OnDatapackSyncEvent` fires for all players: each is reconciled, effects are rebuilt and the new graph and
state are sent. Clients drop pending selections made against an older revision.

**Validation** (`GraphValidator`, before activation): unique ids, valid namespaced ids (node ids ≤ 64 chars),
existing references (edges, requirements, sector roots, group members), finite coordinates, cost 0–100, max rank 1–5,
no self/duplicate edges, every node reachable from every root, no node reachable only through an exclusive keystone,
no unsatisfiable (e.g. cyclic) mandatory requirements, recognised effect/condition/trigger types with finite,
bounded values and supported operations, registry checks for attributes and mob effects, size limits (4096 nodes,
16384 connections, 16 effects, 16 tags, 32 points per edge, 256-char text). Visual overlaps are warnings.

## Persistence

`ModAttachments.SKILLS` (`AttachmentType<PlayerSkillState>`, Codec `StateCodecs.STATE`, schema version stored) holds:
tree id, starting root, allocations (node, rank, **paid cost**, acquisition order), next acquisition index,
purchased points, net granted points, available points, lifetime spent/refunded, state revision, the graph
fingerprint last reconciled, migration markers and a bounded audit log. Ledger invariant:
`available + activeSpent = purchased + netGranted`; a state that violates it on load is repaired by booking the
difference as an administrative adjustment (never by touching purchases).

`ModAttachments.COOLDOWNS` stores trigger cooldowns as absolute game-time expiries; it is `copyOnDeath` and saved
with the player, so logging out, dying or reconnecting cannot reset a running cooldown. A reset (orb or command)
does not clear cooldowns.

Death: the skills attachment is **not** `copyOnDeath`; `LifecycleEvents.onClone` copies it once when
`keepOnDeath=true`. Non-death clones (leaving the End) are copied by NeoForge automatically. The two mechanisms never
both run. Pending selections live only on the client and are never persisted.

## Server authority and transactions

Clients send intents only (`network/Payloads.java`):

| Client → server | Fields |
|---|---|
| `request_sync` | known graph fingerprint (graph resent only if different) |
| `buy_point` | request UUID, expected state revision, point count (clamped by the server to 1..point limit) |
| `confirm_allocation` | request UUID, expected state revision, expected graph revision, ≤400 node ids (≤64 chars; worst case < 32 KiB) |
| `request_reset` | request UUID, expected state revision, expected graph revision |
| `request_refund` | request UUID, expected state revision, expected graph revision, one node id (≤64 chars) |
| `donate_xp` | request UUID, expected state revision, amount |

| Server → client | Fields |
|---|---|
| `graph_sync` | graph revision + deflated binary snapshot (`GraphCodec`; 25 KB for the shipped tree, cap 900 KB < 1 MiB) |
| `state_sync` | graph revision, full player state, economy values for display (next cost, limit, flags), XP curve coefficients, world rules (current sky event, shared-constellation XP and tier thresholds) |
| `transaction_result` | request UUID, ok flag, translatable message key, detail |
| `open_screen` | opens the tree or the reset confirmation |

Every handler runs on the server thread (`HandlerThread.MAIN`), takes the player from the connection context and
performs, in one uninterrupted step: rate limit → `RequestGuard` (bounded duplicate-id memory, stale state/graph
revision) → validation → mutation → effect refresh → revision bump → reply + state sync. Nothing is debited or
applied before validation passes.

- **Buy point:** balance = exact raw XP derived from level + bar progress (`XpMath`, matching vanilla
  `getXpNeededForNextLevel`, bar rounded down); cost from `XpCurve`; on success level, progress and
  `totalExperience` are rewritten together from one exact integer, so the XP bar, level and balance never disagree.
  A request may ask for several points: `ProgressionService.purchasePoints` buys them one by one along the curve
  until the count, the XP or the point limit runs out, and the XP is rewritten once for the whole purchase. Buying
  fewer than asked is a success; buying none is a rejection with the first reason.
- **Confirm batch:** `AllocationService` repeatedly accepts eligible nodes starting from the confirmed build, so a
  valid batch is accepted in any order; any leftover node rejects the whole batch with its reason. Each allocation
  records its actual price and acquisition order.
- **Single-skill refund** (`RefundService`): the node plus the allocations that would stop being valid without it
  (computed with `Reconciler.survivors`, the same replay used after a datapack change, so removal and reconciliation
  can never disagree). Only the recorded paid costs are returned, and `PlayerSkillState.refund` drops the allocation
  in the same step that credits the points, so a replayed request finds nothing to remove and credits nothing. The
  starting root is refused (`root_needs_reset`): it is free and the whole build hangs from it. Off by default;
  enabled with `allowSingleSkillRefunds = true`; `orbRequiredForNodeRefund = true` consumes one orb, only if the refund succeeds.
  The client previews the very same plan (shared code) and recomputes it from every authoritative state sync.
- **Reset:** requires an Orb of Reconfiguration (unless disabled or creative); refunds the recorded costs, clears
  allocations and the starting root, keeps purchase counters (the XP curve never restarts), then consumes exactly one
  orb — all in the same server-thread step, so two concurrent requests cannot consume one orb twice or refund twice.

## Reconciliation and migration

`Reconciler.reconcile` runs on login, respawn and after every graph change when the stored fingerprint differs:

1. Removed ids are renamed through the migration map (recorded price and order kept; marker stored).
2. If the starting root no longer exists, every allocation is refunded at its recorded cost and a new free root may
   be chosen.
3. Otherwise the build is replayed oldest acquisition first (ties by id): a node survives only if it is connected to
   survivors, its requirements hold and no older survivor of its exclusive group blocks it. Passes repeat until
   stable, so cascades resolve deterministically and the oldest valid acquisitions win.
4. Each removed node refunds its recorded paid cost exactly once (later reconciles find nothing to refund).

Offline players go through the same path on their next login, before effects are applied.

## Effects

- **Attributes** (`EffectEngine.refresh`): remove every modifier the engine applied to this player (it remembers each
  attribute and id it added, paths `tree/…` and `cond/…`, so nothing scans the attribute registry), then add one
  transient modifier per (attribute, operation) with the summed amount. Transient
  modifiers are never saved, the attachment is the source of truth, and refresh is idempotent, so bonuses cannot
  duplicate after death, login or reload. Base values and equipment/other-mod modifiers are untouched. After a max
  health decrease, health is clamped without healing. Caps clamp only the tree's summed modifier.
- **Custom attributes** (`ModAttributes`, all synced): crit_chance, crit_damage (base 0.5), dodge_chance,
  light/heavy_weapon_damage, projectile/long_range/crossbow/charged_shot_damage, mining_speed, tool_preservation,
  potion_duration, magic_resistance, healing_received, lifesteal, damage_reduction, low_health_damage.
- **Conditional attributes:** evaluated every 10 ticks and on equipment change, only for players whose compiled
  build has any (`CompiledBuild` is rebuilt only when the build or graph changes; nothing scans the whole tree per tick).
- **Damage** (`CombatEvents.onIncomingDamage`, `LivingIncomingDamageEvent`, low priority): defender side —
  dodge roll, taken modifiers, magic resistance, damage reduction; attacker side — only player melee
  (`minecraft:player_attack` with the player as direct entity) or player projectiles (`#minecraft:is_projectile`,
  owner = player) are weapon damage; environmental damage never is. Projectiles get their firing origin recorded on
  spawn for range conditions (piercing hits measure from the same origin). Crossbow bonus uses the arrow's weapon item.
- **Triggers** (`Triggers.fire`): crit, dodge, shield block (`LivingShieldBlockEvent`), kill, projectile kill, hurt,
  crossing below 30% health, drinking a beneficial potion, fully charged arrow hit, mining a block with an eligible
  tool, eating, the Hidden Power key, a fatal blow and any namespaced id another mod fires. Actions: `heal`,
  `self_effect`, `target_effect`, `stacking_self_effect`, `area_effect`, `ignite_target`, `gust`, `pull`, `burst`,
  `blink_behind_target`, `launch_target`, `smite_target`, `chaos_target`, `lunge`, `web_target`, `cheat_death`,
  `raise_ally`, plus actions integrations register (`restore_mana`, `blood_for_mana`, `sunstrike`). An action never
  fires a trigger directly; actions that can kill or spawn need a cooldown of at least 20 ticks, so a kill chain
  cannot loop.
- **Potions:** on `LivingEntityUseItemEvent.Finish` for potion items, beneficial non-instant effects are extended to
  `potionDuration × (1 + bonus)` computed from the potion — refreshing never compounds.
- **Mining:** `PlayerEvent.BreakSpeed` (both sides, synced attribute) for tools in `#celestialskilltree:mining_tools`
  that are correct for the block. Tool wear is settled once per broken block at the end of the tick.
- **Respec and refunds:** removing allocations and calling `refresh` removes every effect of the removed nodes
  (a single-skill refund rebuilds the whole modifier set, so nothing of the removed branch can linger); cooldowns
  keep running.

## Client

`SkillTreeScreen` (no pause, no container; node states in `NodeStates`, the pending selection with its undo/redo
history in `PendingSelection`, the floating glass panels (search, tool dock, points capsule, pending actions) in `TreeHud`, panels in `PanelContents` and
`PanelRenderer`, the XP bar in `ProgressBar`, rebindable shortcuts in `TreeKeys`) keeps a local pending set, previews it with `AllocationService`, and
recomputes node states only when the state or pending set changes. Hit testing applies the inverse camera transform
with a 2 GUI-pixel margin and picks the nearest node; the floating panels and dialogs take precedence (nothing under them is hovered or clicked). Drag threshold is 4
window pixels converted through the GUI scale. Draw order: backdrop layers → motes → inactive edges → lit routes →
node frames/icons → state marks and focus → controls and progress bar → panels → tooltips; the graph is scissored to
its viewport and rendering state (blend, shader colour) is restored. Every HUD texture is a GUI sprite under `textures/gui/sprites/hud/` (nine-slice glass panels and buttons, the XP track, 9×9 icons,
the animated point star), named in `HudSprites` and drawn by `SkillButton`, `TreeHud` and `PanelRenderer`.
`TreeBonusTooltip` adds the tree's weapon, ranged and tool bonuses to item tooltips. `CelestialSkillTreeClient`
registers NeoForge's configuration screen. Client-only classes are referenced from common
code only through `ClientHandlers` (invoked solely for client-bound payloads) and `@EventBusSubscriber(value =
Dist.CLIENT)` classes, which is why the dedicated server starts without loading graphical classes (verified).

## World features

- **Extra effect sources** (`effects/ExtraEffects`): `CompiledBuild.compile` takes extra named effect lists beside the
  allocated nodes. The player's birth sign and every reached shared-constellation tier are such sources, so
  `EffectEngine`, the gameplay event classes and `Triggers` need no special cases.
- **Birth sign**: `SignParser` reads `skill_signs`, swapping in the first `partner_effects` entry whose mod is loaded
  (its description key reaches the client in `WorldRules.signs`); `SignService.assignIfMissing` picks by world day plus player UUID on login; the id is
  stored in `PlayerSkillState.sign` (optional codec field, old saves load unchanged).
- **World data** (`data/WorldSkillData`, a `SavedData` on the Overworld): next eclipse day, next star shower day,
  forced sky event and donated XP.
- **Sky events**: `progression/WorldClock` decides day and night from the clock alone (never `Level.isNight()`, which
  a thunderstorm or the eclipse itself would flip); `SkyEventSchedule` says which `SkyEvent` (`ECLIPSE` during the
  daylight of its day, `STAR_SHOWER` during the night of its day) is due. `server/SkyEvents` re-evaluates once a
  second, announces changes with a chat line and a stinger, blocks beds during an eclipse and syncs the event in
  `WorldRules`; during an eclipse `SkillRequests` asks for no orb. `mixin/LevelMixin` raises the Overworld's
  `skyDarken` to night level while an eclipse is active, which is what lets monsters spawn and stops undead burning.
  On the client `client/render/SkyTint` holds one eased 0..1 strength; `mixin/ClientLevelMixin` feeds it into sky
  colour, cloud colour, sky light and star brightness, and `SkyEventRenderer` tints the fog and draws the moon disc
  and corona over the sun after the sky pass, sliding into place as the strength rises.
- **Community**: `server/CommunityTree` applies donations (pure rules in `CommunityProgress`) and refreshes everyone
  when a tier is reached.
- **Star fragments**: `StarFragmentItem.redeem` grants one point through `LedgerService.grant` (limit respected).
  `server/ShootingStars` rolls once a second per Overworld player at night (far more often during a star shower) and
  calls `entity/FallingStar.spawnNear`: the entity starts 50-80 blocks above a landing point near the player, moves
  along a fixed diagonal and, on hitting a block or fluid, plays the impact, bursts particles for every viewer and
  leaves the fragment as an `ItemEntity`. `client/render/FallingStarRenderer` draws an additive head and
  streak at any distance; `client/sound/StarSounds` loops the falling sound on the star. `effects/EliteMobs` marks a fresh
  monster once (persistent data), adds permanent modifiers from a random constellation and adds the fragment to its drops.
- **Fallen Star**: `FallenStarService.dropNewest` forfeits the newest allocation whose refund plan is not chained,
  taking its paid points out of the ledger (`available + spent = purchased + granted` still holds). The item carries
  owner, node and paid cost in `CUSTOM_DATA`; the owner re-learns it, anyone else is granted the points.
- **Node traits** (`graph/NodeTraits`): `neighbour_multiplier`, `hidden_until` and `toggleable` travel through `NodeParser`,
  `GraphCodec` (format 3) and `GraphValidator`. A toggleable node the player switched off stays in
  `PlayerSkillState.disabled` (`ToggleService`, `Payloads.ToggleNode`) and `CompiledBuild` skips its effects. `Holdings` answers both rules for `AllocationService.rejectionOf`
  (`UNDISCOVERED`, `CONSUMED`); `Reconciler` replays with everything discovered so learned skills are never lost.
  `server/Discoveries` records advancements as they are earned and on login; the client hides `NodeState.HIDDEN` nodes
  and their edges.

## Hooks other integrations plug into

- `effects/BorrowedNodes`: lenders return node ids equipment lends to a player; they become extra
  effect sources, on top of the node itself when the player also learned it, and the build is recompiled when the lent set changes.
- `effects/StolenNodes`: node ids silenced until a game time; `CompiledBuild.compile` skips them.
- `server/XpWallets`: sources of experience outside the player's bar (`FluidXpWallet`, the XP Scroll); the purchase
  spends the player's experience first and takes only the shortfall, and a wallet may overpay (the change returns as
  experience).
- `registry/WornItems`: stacks carried outside the vanilla inventory (Curios).
- `server/Discoveries.reveal`: discovery keys that are not advancements (`spell/`, `worn/`, `kill/`).
- `effects/EliteMobs.registerRival` and `registerNodeBonus`: other elite systems claim a monster or enlarge its
  constellation; a Constellated monster remembers its own UUID, so a copy is demoted.
- `effects/PetInheritance`: owned creatures of `#celestialskilltree:inherits_tree` copy a share of the owner's summed
  attribute modifiers.
- `block/XpBasinBlockEntity`: one block entity for the Stellar Condenser and the Community Basin, exposing a fluid
  tank and an extract-only item slot as capabilities.

## Development harness (not part of gameplay)

The harness drives the tree screen through `TreeScreenQa`, which lives in `src/devHarness` in the screen's own package and
reaches its package-private state, so the screen itself carries no test hooks.

`-Pvisualqa` adds `-Dcelestialskilltree.visualqa=true` to `runClient`. The run is one `QaScript`: a list of steps that
each wait for a condition or time out, with `expect` checks that are counted as PASS or FAIL. `InWorldQa` deletes and
recreates a flat singleplayer world (the tree only exists once a world has generated it), `VisualQa` then renders the
real screen with fixture builds (fresh, focused, hybrid, pending, conflict, insufficient points; nothing is purchased)
at 1000×563, 1280×720, 1920×1080 and 2560×1080 with GUI scales 2 and 3, and `InWorldQa` goes back to the live state
and drives the real network path (XP → points, pending + cancel, confirm, death and respawn, dimension change, request
burst, world restart, catalog change + `/reload`, orb reset, single and chained refunds, bulk purchase, eclipse,
donation). The report is `run/celestialskilltree_qa_ingame.txt`; its first line is `RESULT: PASSED` or `RESULT: FAILED`
with the number of checks, and every screenshot taken is checked to exist. `MultiplayerQa` uses the same script for
two clients on a dedicated server.

`-Pgallery` runs a shorter script for the mod page: the same fixture builds and in-world scenes at 1920×1080 with GUI scale 3, the HUD hidden outside the tree, saved as `run/screenshots/gallery_*.png`.
