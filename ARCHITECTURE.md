# Architecture — Nostalgia Skills

NeoForge 21.1.256 for Minecraft 1.21.1, Java 21, ModDevGradle 2.0.148. Package root `dev.nostalgiacraft.skills`.

```text
graph/        pure Java: node/edge/effect model, GraphParser (JSON), GraphValidator, SkillGraphSnapshot, GraphCodec
progression/  pure Java: PlayerSkillState (ledger), AllocationService, PathFinder, XpMath, XpCurve,
              ProgressionService, LedgerService, RespecService, RefundService, Reconciler, RequestGuard,
              RejectReason
data/         server resource loading: SkillTreeReloadListener, SkillTreeManager, BundledTree, McKnownIds
registry/     attributes, item tags, attachments + codecs, persisted cooldowns, Orb of Reconfiguration
network/      payload records + registration (client-bound handlers reach client code only through ClientHandlers)
server/       SkillService (authoritative intents), LifecycleEvents, RateLimiter
effects/      CompiledBuild, EffectEngine (attribute modifiers), GameplayEvents (combat/potions/mining), Triggers
command/      /nostalgiaskills
config/       ServerConfig (gameplay, synced), ClientConfig (presentation only)
client/       ClientSkillState, ClientSetup (key mapping), screen/, render/, VisualQa + InWorldQa (dev harness)
```

`graph` and `progression` contain no Minecraft classes; they are unit-tested directly (`./gradlew test`, 64 tests,
including validation of the full bundled 480-node dataset). The same classes run on the server (authoritative) and
on the client (previews only), so the client shows exactly the rules the server enforces.

## Data and loading

| Location | Content |
|---|---|
| `data/<ns>/skill_trees/<name>.json` | tree id, sectors (id, root, name key), exclusive groups, default costs, bounds |
| `data/<ns>/skill_nodes/<path>.json` | one node per file; id = `<ns>:<path>` (explicit `id` optional but must match) |
| `data/<ns>/skill_connections/<name>.json` | `connections[]`: `a`, `b`, optional `id` (else derived from sorted endpoints), `directed`, `style`, `points[]`, `bridge`, `corridor` |
| `data/<ns>/skill_migrations/<name>.json` | `{"schema_version":1,"migrations":{"old:id":"new:id"}}` |

Node schema (version 1) is exactly what `tools/layout/export.py` writes; see any file under
`src/main/resources/data/nostalgiaskills/skill_nodes/` and the example datapack in `examples/datapacks/`.
`requirements.all_of` / `any_of` take node ids or `#tag` terms; empty lists impose nothing; when both are non-empty
both must hold. `minimum_sector_points` counts recorded paid points already held in the node's sector, including
nodes accepted earlier in the same batch, never the node itself. Adjacency is checked separately.

**Reload strategy.** `SkillTreeReloadListener` is registered through `AddReloadListenerEvent`, so it runs on server
start and on every `/reload`. `prepare` reads JSON off-thread; `apply` parses, validates and builds a new immutable
`SkillGraphSnapshot`. Only a fully valid snapshot replaces the active one (`SkillTreeManager`):

- invalid custom data during a session → the previous snapshot stays active; every problem is logged with file,
  field and reason and is shown by `/nostalgiaskills validate`;
- invalid data on the initial load → the verified tree bundled in the jar is loaded from the classpath
  (`BundledTree`, using `nostalgiaskills_bundled_index.json`), with a loud error; if that fails too, skill features
  are disabled with `error.nostalgiaskills.tree_unavailable` (no silent empty tree).

The graph revision (`SkillTreeManager.revision()`) increments whenever the content fingerprint changes. After a
reload `OnDatapackSyncEvent` fires for all players: each is reconciled, effects are rebuilt and the new graph and
state are sent. Clients drop pending selections made against an older revision.

**Validation** (`GraphValidator`, before activation): unique ids, valid namespaced ids (node ids ≤ 64 chars),
existing references (edges, requirements, sector roots, group members), finite coordinates, cost 0–100, max rank 1,
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
| `buy_point` | request UUID, expected state revision |
| `confirm_allocation` | request UUID, expected state revision, expected graph revision, ≤400 node ids (≤64 chars; worst case < 32 KiB) |
| `request_reset` | request UUID, expected state revision, expected graph revision |
| `request_refund` | request UUID, expected state revision, expected graph revision, one node id (≤64 chars) |

| Server → client | Fields |
|---|---|
| `graph_sync` | graph revision + deflated binary snapshot (`GraphCodec`; 25 KB for the shipped tree, cap 900 KB < 1 MiB) |
| `state_sync` | graph revision, full player state, economy values for display (next cost, limit, flags) |
| `transaction_result` | request UUID, ok flag, translatable message key, detail |
| `open_screen` | opens the tree or the reset confirmation |

Every handler runs on the server thread (`HandlerThread.MAIN`), takes the player from the connection context and
performs, in one uninterrupted step: rate limit → `RequestGuard` (bounded duplicate-id memory, stale state/graph
revision) → validation → mutation → effect refresh → revision bump → reply + state sync. Nothing is debited or
applied before validation passes.

- **Buy point:** balance = exact raw XP derived from level + bar progress (`XpMath`, matching vanilla
  `getXpNeededForNextLevel`, bar rounded down); cost from `XpCurve`; on success level, progress and
  `totalExperience` are rewritten together from one exact integer, so the XP bar, level and balance never disagree.
- **Confirm batch:** `AllocationService` repeatedly accepts eligible nodes starting from the confirmed build, so a
  valid batch is accepted in any order; any leftover node rejects the whole batch with its reason. Each allocation
  records its actual price and acquisition order.
- **Single-skill refund** (`RefundService`): the node plus the allocations that would stop being valid without it
  (computed with `Reconciler.survivors`, the same replay used after a datapack change, so removal and reconciliation
  can never disagree). Only the recorded paid costs are returned, and `PlayerSkillState.refund` drops the allocation
  in the same step that credits the points, so a replayed request finds nothing to remove and credits nothing. The
  starting root is refused (`root_needs_reset`): it is free and the whole build hangs from it. Disabled with
  `allowNodeRefunds = false`; `orbRequiredForNodeRefund = true` consumes one orb, only if the refund succeeds.
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

- **Attributes** (`EffectEngine.refresh`): remove every modifier this mod owns (namespace `nostalgiaskills`, path
  `tree/…` or `cond/…`), then add one transient modifier per (attribute, operation) with the summed amount. Transient
  modifiers are never saved, the attachment is the source of truth, and refresh is idempotent, so bonuses cannot
  duplicate after death, login or reload. Base values and equipment/other-mod modifiers are untouched. After a max
  health decrease, health is clamped without healing. Caps clamp only the tree's summed modifier.
- **Custom attributes** (`ModAttributes`, all synced): crit_chance, crit_damage (base 0.5), dodge_chance,
  light/heavy_weapon_damage, projectile/long_range/crossbow/charged_shot_damage, mining_speed, tool_preservation,
  potion_duration, magic_resistance, healing_received, lifesteal, damage_reduction, low_health_damage.
- **Conditional attributes:** evaluated every 10 ticks and on equipment change, only for players whose compiled
  build has any (`CompiledBuild` is rebuilt only when the build or graph changes; nothing scans 480 nodes per tick).
- **Damage** (`GameplayEvents.onIncomingDamage`, `LivingIncomingDamageEvent`, low priority): defender side —
  dodge roll, taken modifiers, magic resistance, damage reduction; attacker side — only player melee
  (`minecraft:player_attack` with the player as direct entity) or player projectiles (`#minecraft:is_projectile`,
  owner = player) are weapon damage; environmental damage never is. Projectiles get their firing origin recorded on
  spawn for range conditions (piercing hits measure from the same origin). Crossbow bonus uses the arrow's weapon item.
- **Triggers** (`Triggers.fire`): crit, dodge, shield block (`LivingShieldBlockEvent`), kill, projectile kill, hurt,
  crossing below 30% health, drinking a beneficial potion, fully charged arrow hit, mining a block with an eligible
  tool. Actions heal or apply mob effects only (no recursion).
- **Potions:** on `LivingEntityUseItemEvent.Finish` for potion items, beneficial non-instant effects are extended to
  `potionDuration × (1 + bonus)` computed from the potion — refreshing never compounds.
- **Mining:** `PlayerEvent.BreakSpeed` (both sides, synced attribute) for tools in `#nostalgiaskills:mining_tools`
  that are correct for the block. Tool wear is settled once per broken block at the end of the tick.
- **Respec and refunds:** removing allocations and calling `refresh` removes every effect of the removed nodes
  (a single-skill refund rebuilds the whole modifier set, so nothing of the removed branch can linger); cooldowns
  keep running.

## Client

`SkillTreeScreen` (no pause, no container) keeps a local pending set, previews it with `AllocationService`, and
recomputes node states only when the state or pending set changes. Hit testing applies the inverse camera transform
with a 2 GUI-pixel margin and picks the nearest node; controls and panels take precedence. Drag threshold is 4
window pixels converted through the GUI scale. Draw order: backdrop layers → motes → inactive edges → lit routes →
node frames/icons → state marks and focus → controls and progress bar → panels → tooltips; the graph is scissored to
its viewport and rendering state (blend, shader colour) is restored. Client-only classes are referenced from common
code only through `ClientHandlers` (invoked solely for client-bound payloads) and `@EventBusSubscriber(value =
Dist.CLIENT)` classes, which is why the dedicated server starts without loading graphical classes (verified).

## Development harness (not part of gameplay)

`-Pvisualqa` adds `-Dnostalgiaskills.visualqa=true` to `runClient`. `VisualQa` renders the real screen with fixture
builds (fresh, focused, hybrid, pending, conflict, insufficient points; nothing is purchased, no server involved) at
1000×563, 1280×720, 1920×1080 and 2560×1080 with GUI scales 2 and 3, then `InWorldQa` creates a flat singleplayer
world and drives the real network path (XP → points, pending + cancel, confirm, stats with live attributes, death and
respawn, orb reset), writing `run/nostalgiaskills_qa_ingame.txt`.
