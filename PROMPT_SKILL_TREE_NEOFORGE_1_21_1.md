# Complete Implementation Prompt — Minecraft 1.21.1 Skill Tree Mod for NeoForge

Build the mod described below as a complete, original project. This document is a standalone specification for its gameplay, graph layout, interface, technical architecture, and visual assets. Create the entire background environment from scratch, including its composition, textures, layers, and optional animation.

---

## 1. Role, objective, and required outcome

You are a senior engineer specializing in Minecraft Java mods, NeoForge, RPG progression systems, graph algorithms, pixel art interfaces, and multiplayer architecture. Implement a complete, compilable, playable mod for **Minecraft Java Edition 1.21.1, using NeoForge and Java 21**, named **Nostalgia Skills**, with the mod ID **`nostalgiaskills`**.

The main feature is a large passive skill tree with a precise radial layout, six specialization sectors, interconnected paths, and meaningful build choices. The interface is presented as a celestial map inside an original fantasy environment called **The Astral Observatory**. Players must be able to explore the graph, purchase skill points, prepare allocations, confirm a batch, inspect statistics, and respec their character.

Precision means every node has a stable ID, explicit position, declared connections, validated prerequisites, an actual gameplay effect, and a predictable interaction area. The graph displayed by the interface must be the same graph used by the server to validate purchases.

Deliver working code, complete tree data, original visual assets, documentation, and evidence of validation. A static mockup, an HTML page, an interface without gameplay logic, or a small example tree does not fulfill the task.

When a workspace is available, create and modify the required files, compile the project, and fix the problems found. If the environment prevents a particular verification, identify that limitation precisely and supply the necessary files and commands. Report only checks that actually ran.

If the workspace is a modpack instance without an existing Java project, work in the isolated folder `development/nostalgiaskills/`. Preserve existing worlds, scripts, configurations, and mods. Produce a reviewable JAR before treating installation into the instance as complete.

Write the project documentation and primary interface strings in English. Provide `en_us` localization and a separate `pt_br` localization file. Keep identifiers, commands, and code terminology consistent throughout the project.

## 2. Reference mods and their intended use

Study these projects for architectural and gameplay ideas:

1. [Passive Skill Trees](https://www.curseforge.com/minecraft/mc-mods/passive-skill-tree): inspiration for extensive passive progression, interconnected routes, and configurable tree content. Verify any version-specific behavior before adapting implementation ideas to NeoForge 1.21.1.
2. [Skill Tree (RPG Series)](https://www.curseforge.com/minecraft/mc-mods/skill-tree): inspiration for specialization choices, synergistic effects, and build-defining abilities. Its integration with Pufferfish Skills and RPG Series mods is relevant architectural context.
3. [Pufferfish's Skills](https://modrinth.com/mod/skills): inspiration for separating the skill framework from its content and using configurable data with authoring tools. Its framework model requires separate tree content.
4. [Pufferfish's Skills tree creation documentation](https://puffish.net/skillsmod/docs/creators/tutorials/creating-skill-trees): inspiration for separating definitions, positions, and connections into maintainable data.

The default deliverable must be a **standalone mod** that works with Minecraft and NeoForge and includes its complete skill tree. Treat the projects above as conceptual references. Do not introduce their dependencies into the core merely because their gameplay is similar.

Create the background environment, icons, frames, and visual composition specifically for this project. Use the game's built-in font where appropriate. Respect the relevant licenses if external implementation material is actually reused.

Compatibility with Pufferfish, RPG Series, KubeJS, or magic mods may be implemented through optional modules. Each delivered integration needs a verified target version, an actual implementation, and a dedicated test scenario.

## 3. Platform and version discipline

- Minecraft version: exactly **1.21.1**.
- Loader: **NeoForge**, using a release compatible with Minecraft 1.21.1 and pinned after checking the official MDK.
- Java: **JDK 21 and a 64-bit JVM**.
- Project foundation: an official MDK appropriate for this version, preferably ModDevGradle, with the Gradle Wrapper included.
- Metadata: `META-INF/neoforge.mods.toml`, dependency ranges, and resources consistent with the target platform.
- Shared gameplay code and client-only rendering code must be separated so a dedicated server can start successfully.
- Pin dependency, Gradle plugin, and mapping versions. Avoid dynamic `latest` dependencies.

Check API signatures against the sources supplied by the pinned MDK. Consult the [NeoForge getting started documentation for 1.21.1](https://docs.neoforged.net/docs/1.21.1/gettingstarted/). Newer documentation can contain APIs unavailable in this version, and older Forge snippets can require substantial changes.

## 4. Exact graph layout and authored topology

### 4.1. Overall composition

Create a dense radial constellation of skills arranged around a decorative center at logical position **`(0, 0)`**. Six large circular root hubs form a regular hexagonal foundation. Each hub leads outward into a distinctive sector with curved routes, branches, loops, notable clusters, and terminal keystones.

The overall structure is radial, but individual sectors must be deliberately asymmetric. Different route shapes should make the sectors recognizable before reading their labels. Use a mixture of arcs, short chains, forked paths, small rings, and connecting corridors.

The central decorative element is a faint astrolabe emblem. It is not an additional purchasable node and must not create a gameplay shortcut between roots.

### 4.2. Root hub coordinates

Use the following authored positions in tree-space coordinates, where positive X points right and positive Y points down:

| Hub | Sector | X | Y |
|---|---|---:|---:|
| West | Duelist | -180 | 0 |
| Northwest | Guardian | -90 | -156 |
| Northeast | Arcanist / Alchemist | 90 | -156 |
| East | Marksman | 180 | 0 |
| Southeast | Artificer / Explorer | 90 | 156 |
| Southwest | Berserker | -90 | 156 |

Store these coordinates explicitly. The displayed positions depend on the viewport, camera offset, zoom, and GUI scale; they are not fixed window coordinates.

Start authoring within an approximate logical canvas of **1,800 × 1,600 units**. Compute actual graph bounds from the final data. The interface must support panning beyond the initial viewport and fitting the complete graph when requested.

### 4.3. Node count and distribution

Deliver **480 playable nodes**, distributed as follows in each of the six sectors:

- 1 root hub.
- 64 minor nodes.
- 12 notable nodes.
- 3 keystones.

The complete distribution is **6 roots + 384 minors + 72 notables + 18 keystones = 480 nodes**. The decorative center and background objects are not counted as playable nodes.

Use three main arteries per sector, feeding approximately eight small groups of minor nodes. Vary the route shapes and arrange the twelve notables around distinct tactical themes. Place keystones on optional branches or endpoints rather than mandatory corridors between sectors.

Repeated minor-effect templates are acceptable. Repeated sector geometry is not: do not rotate one finished branch six times and consider the layout complete.

### 4.4. Connectivity and traversal

Declare every edge by its endpoint IDs. A line crossing another line does not create a connection. For curved routes, store intermediate points or another deterministic representation that preserves the authored shape.

Provide connecting corridors between adjacent sectors and selected longer routes that support hybrid builds. The cheapest valid route from one root to an adjacent root should require at least **8 paid points**, including the destination root's cost, under the initial rules. Validate this requirement with the graph's actual node costs.

All roots must belong to the same traversable component before exclusivity choices are applied. Every ordinary node must be reachable from a valid starting root. Avoid disconnected filler clusters, accidental zero-cost bridges, and mandatory routes blocked by mutually exclusive keystones.

Keep typical node degree between 2 and 4, with exceptions for endpoints and intentional junctions. Use higher-degree hubs sparingly. A sector needs meaningful alternative routes rather than one long corridor with a decorative fork at the end.

### 4.5. Spacing and layout stability

Use approximately 28–40 logical units between consecutive minor-node centers, adjusting around curves. Distances between node centers must exceed the sum of their visual radii plus an authored gap, unless an intentional composition explicitly requires otherwise.

Route edges so they do not pass through unrelated node icons. Prefer a clean bend to an ambiguous intersection. Reserve additional breathing room around roots, notable clusters, and keystones.

Positions, icons, and connections must remain identical across opening the screen, reconnecting, changing dimensions, or restarting. Do not run force-directed layout or random graph placement during gameplay.

An offline layout generator is acceptable if it exports fixed JSON data that is subsequently reviewed and refined. The committed layout is authoritative.

### 4.6. Layout manifest

Deliver `LAYOUT_MANIFEST.md` and a machine-readable manifest containing:

- Every node ID, sector, type, coordinate, frame, and icon.
- Every edge and its authored intermediate points.
- Root positions and overall graph bounds.
- Cluster assignments, traversal corridors, and keystone groups.
- Degree distribution and reachability results.
- Detected overlaps and intentional exceptions.
- Example build routes and their total paid costs.

Produce an annotated render of the authored graph for debugging. This diagram is generated from the final data and must agree with server-side connectivity.

## 5. Original background environment and rendering

### 5.1. Create The Astral Observatory from scratch

Design and produce a complete original fantasy backdrop called **The Astral Observatory**. The setting is an ancient celestial observatory suspended above a fractured moon, surrounded by a quiet cosmic expanse. The skill tree feels like a constellation chart projected from this abandoned structure.

The environment needs a coherent scene, depth, and recognizable details. A flat black fill with randomly scattered white dots is only a fallback, not the finished background.

Create its textures, decorative shapes, star distribution, stone fragments, lighting, and composition specifically for the mod. Include editable source assets or reproducible generation scripts with their settings. The shipped mod must load bundled local assets and render the scene without downloading anything or calling an external generation service at runtime.

### 5.2. Scene composition

Build the scene from the following original elements:

1. **Deep celestial space:** a nearly black base with restrained blue-green variation and a soft vignette. The central reading area is darker and calmer than the perimeter.
2. **Distant star field:** many tiny, mostly dim stars with sparse brighter points. Avoid uniform grids or excessive bright white pixels behind node icons.
3. **Nebula ribbons:** broad, low-contrast teal and muted violet wisps flowing diagonally from the upper-right perimeter toward the lower-middle distance. Keep the middle of the graph visually quiet.
4. **Fractured moon:** a large crescent-shaped celestial body visible near the lower-left perimeter, with stepped pixel-art shading, a broken ridge, and a thin cool rim light. It should occupy approximately 18–24% of the scene width while remaining mostly outside the central interaction area.
5. **Observatory remnants:** a weathered stone platform edge along the bottom and three or four small floating arch fragments near the outer corners. Add carved celestial markings and subtle metallic inlays. Keep their silhouettes subdued beneath the playable graph.
6. **Distant astrolabe:** an extremely faint, broken circular ornament near the background center. Its markings are decorative and must remain visually distinct from clickable nodes and connections.
7. **Sparse astral motes:** a few dim particles drifting slowly along the outer edges. They must never compete with the allocated-node highlights.

Use normalized scene anchors to keep composition consistent across aspect ratios: place the moon's center near `(0.10, 0.91)`, the main nebula's origin near `(0.82, 0.15)`, and the distant astrolabe near `(0.50, 0.52)`. These positions belong to the background composition, not to the graph's node coordinates.

Preserve a relatively calm area across approximately the central 60% of the scene width and the middle 65% of its height. Detailed scenery should concentrate near the outer edges and bottom. The background may be beautiful, but the skill tree remains the main subject.

### 5.3. Visual style and lighting

Use carefully authored pixel art with stepped shading, restrained dithering, sharp silhouettes, and a limited color family. The scene should feel compatible with Minecraft while having its own fantasy identity.

Light the environment with a soft cyan rim from the upper-right distance and a faint amber reflection on selected observatory carvings. Keep deep shadows near the center. Avoid large white light sources or bright nebula blooms beneath skill text.

Use dark blue-green for distance, muted stone gray for ruins, restrained bronze for ancient instruments, and violet for subtle astral accents. Reserve the strongest gold and purple highlights for interactive tree elements.

### 5.4. Layered assets and reproducibility

Provide independently composited layers, for example:

```text
assets/nostalgiaskills/textures/gui/skill_tree/background/
  space_base.png
  nebula_far.png
  moon_crescent.png
  observatory_ruins.png
  astrolabe_ghost.png
  astral_motes.png
```

Use a suitable working size such as **2,048 × 1,152 pixels** for a 16:9 base composition, with transparent overlays where needed. This is an asset-authoring resolution, not a required window size. An equivalent tiled or atlas-based implementation is acceptable if it preserves composition and quality.

Document texture dimensions, color palette, blend rules, and export settings in `ART_DIRECTION.md`. If procedural tools create any layer, include the generator, seed, and parameters under `tools/art/` so the asset can be reproduced.

Use a deterministic local seed for procedural stars. Keep the same visual identity across screen openings. Pixel-art assets should use appropriate sampling and mipmap settings; decorative scaling must remain sharp without introducing visible seams.

Scale and crop the backdrop with aspect-ratio preservation. Reposition peripheral layers where necessary for ultrawide or tall windows. Avoid stretching the moon into an ellipse or exposing an empty strip around a moving layer.

### 5.5. Subtle parallax and animation

Optional parallax should be much weaker than graph movement:

- Distant stars: approximately 2% of camera motion.
- Nebula layer: approximately 4%.
- Moon and distant fragments: approximately 6%.
- Foreground observatory rim: approximately 8%.

Clamp decorative movement so it cannot reveal unpainted texture edges. Graph zoom must not make the backdrop expand like a node layer; control the scenery's scale independently.

Animate only a few elements, such as slow mote drift or a restrained star pulse. Use frame-rate-independent timing and avoid flashing. Provide a reduced-motion setting that disables parallax, drifting particles, and pulsing highlights while preserving the full static scene.

The static layered backdrop is mandatory. Animation is an enhancement and must not delay delivery of a complete scene or a working mod.

### 5.6. Interface palette

Use these authored colors as starting values:

| Purpose | Initial color |
|---|---|
| Deep space | `#050C10` |
| Nebula shadow | `#12323F` |
| Inactive connections | `#35291D` |
| Bronze frame | `#88642C` |
| Allocated route | `#D28C2E` |
| Important gold highlight | `#F3D15C` |
| Notable violet frame | `#A977DC` |
| Root hub stone-metal tone | `#7C8D88` |
| Enabled control fill | `#7C0D05` |
| Primary text | `#E9E3D8` |

Tune colors for readability over the actual finished scene. Interactive states must also differ through outlines, shape, symbols, or emphasis so they remain distinguishable without relying exclusively on hue.

### 5.7. Node frames and connections

At camera zoom 1.0, use approximately these logical GUI dimensions:

- Minor circles: 12–14 units in diameter.
- Notable violet frames: 16–18 units.
- Ornate keystones: 22–26 units.
- Root hubs: 28–32 units.
- Standard connection strokes: 1–2 units before scaling.

Actual window pixels also depend on GUI scale. Keep those coordinate systems explicit in rendering and hit testing.

Create original pixel-art frames with recognizable silhouettes and icons. Give minors small bronze rings, notables violet square or faceted frames, keystones ornate gold frames, and roots larger stone-metal medallions. Their categories must be explained in the interface.

Render connections as deterministic segments or polylines. Small chevrons may decorate long traversal corridors, but they must not imply a directional gameplay rule unless that edge is explicitly defined as directional.

### 5.8. Draw order and clipping

1. Base space layer.
2. Distant stars and nebulae.
3. Moon, distant astrolabe, and ruins.
4. Decorative motes.
5. Inactive graph connections.
6. Allocated and pending route highlights.
7. Node frames and icons.
8. Selection and focus indicators.
9. Fixed controls and bottom progression bar.
10. Open panels, messages, and tooltips.

Clip the graph to its interaction viewport. Restore any rendering state that the screen changes. Fixed controls and tooltips must not inherit the graph camera transform.

### 5.9. Background acceptance criteria

The original background is complete only when:

- All required scene layers exist as usable bundled assets or implemented local renderers.
- The environment clearly reads as a celestial observatory rather than an empty star field.
- Source assets or generation tools are included.
- Bright decorations do not obscure skill states, icons, edges, or tooltips.
- The scene works at 16:9, 16:10, and ultrawide aspect ratios without distortion or uncovered areas.
- Reduced-motion mode retains the finished static art.
- Background objects cannot be mistaken for purchasable nodes.
- Rendering requires no internet access or runtime asset generation service.

## 6. Controls and user experience

### 6.1. Fixed upper controls

Place a compact control group above the graph viewport:

| First row | Role |
|---|---|
| `Buy Skill Point` | Purchase one available point using the configured progression resource |
| `Points left: N` | Display the available balance; this is a read-only indicator |
| `Show Stats` | Open the character and skill contribution panel |

Place `Confirm` and `Cancel` on a second row beneath the purchase and balance controls. Keep the group coherent and easy to reach without covering the graph's central hubs.

Use the game's pixel font, subtle text shadows, a dark red fill with bronze-gold edging for enabled actions, and a darker neutral fill for disabled controls. Disabled labels remain readable and their tooltip explains the reason.

Compute button widths from localized text, available space, and GUI scale. Use equal-width controls where practical, approximately 132–150 logical units wide and 20–24 units high on a comfortably sized viewport. Adapt the layout instead of forcing these dimensions onto a small window.

On narrow viewports, wrap the group or use a compact row while preserving clear labels. Search and camera actions can occupy a small secondary toolbar. No control should overlap another control or consume the central reading area unnecessarily.

### 6.2. Bottom progression bar

Create a segmented gold progression bar anchored near the bottom center. Its meaning is **progress toward purchasing the next skill point**.

Show the number of points earned through purchases on the left, the next purchase milestone on the right, and the percentage of currently available vanilla XP relative to the next point's cost in the center. Clamp the displayed percentage to 100%, but show the actual XP balance and required amount in the tooltip.

When the point cap is reached, display `Maximum`. Use a separate, labeled zoom indicator so camera scale is not confused with progression.

Keep the bar fixed while the graph moves. Leave sufficient margins for text, the platform artwork, and windows with a small logical height.

### 6.3. Input and camera behavior

- Default configurable key binding: `O` to open the skill screen.
- Players must be able to rebind it through the standard controls menu, including in modpacks with conflicting bindings.
- Left-click an eligible node to add it to pending allocations.
- Left-click a pending node to attempt removal while validating pending dependents.
- Drag empty space with the left mouse button, or drag with the middle mouse button, to pan.
- Use an approximately 4-pixel initial drag threshold to distinguish dragging from clicking, converted correctly for the input coordinate space.
- Mouse wheel zooms around the cursor position.
- Start with a normal interaction zoom range of approximately 0.20× to 3.0×. Allow the fit-all action to extend the lower range when the viewport requires it, with a documented safe lower bound.
- `Home` recenters on the chosen starting hub.
- Provide a separate `Fit Tree` action that frames all authored graph bounds.
- `Enter` confirms a pending allocation when focus permits it.
- `Esc` closes the screen. If allocations are pending, offer clear choices to discard them or keep editing.
- Search by skill name, description, effect, and tag; highlight results and allow navigation to them.
- Support keyboard focus, narration, and a visible focus indicator.

Apply the inverse camera transform for hit testing. A small interaction margin is acceptable, but it must not capture neighboring nodes unpredictably. Prioritize controls and panels over graph interaction, and use the nearest node when several candidates overlap the pointer margin.

Resizing the window or changing GUI scale must preserve a meaningful camera focus without modifying the graph. The screen should not pause gameplay by default; verify this in singleplayer and multiplayer.

### 6.4. Node states

Implement and demonstrate:

1. **Locked:** unmet access conditions, subdued appearance, and a precise tooltip reason.
2. **Available:** connected and eligible for allocation, with a clear selectable indication.
3. **Allocated:** server-confirmed purchase and an active effect, with its route highlighted.
4. **Pending:** included in the local batch, with a distinct outline; its effect is not active yet.
5. **Focused:** hovered or keyboard-focused, as an overlay independent of allocation state.
6. **Conflicting:** unavailable because of an exclusive choice or another declared conflict.

Display insufficient points as an additional affordability condition. Preserve the distinction between a node being reachable and a player being able to pay for it.

### 6.5. Tooltips

Each tooltip must include:

- Name and specialization category.
- Node type and point cost.
- Current and maximum rank where relevant.
- Actual effect, unit, and activation condition.
- Before-and-after values when a reliable preview can be computed.
- Unmet prerequisites.
- Conflicting keystones or choices.
- A clear reason when the node cannot be selected.
- Pending or confirmed status.

Keep tooltips within the window and support line wrapping. Distinguish percentage-point changes from relative multipliers. A critical chance increase from 5% to 8% adds 3 percentage points; a 10% damage increase uses the specifically documented damage base.

### 6.6. Statistics panel

`Show Stats` opens a panel that can be closed without losing the camera position. Display relevant values including:

- Maximum health in health points and hearts.
- Attack damage and attack speed.
- Armor and armor toughness.
- Movement speed.
- Knockback resistance.
- The mod's critical chance and critical damage multiplier.
- Mining speed modifiers.
- Conditional effects and their activation rules.
- Active keystones and their penalties.
- Total, spent, available, and locally reserved points.

Distinguish the player's final value from the contribution of this tree. Use server-authoritative attributes where equipment and other mods affect results. Show pending changes as a preview that is clearly marked as unapplied.

## 7. Graph Rules and Skill Allocation

### 7.1. Core model

The tree contains six specialization hubs, each serving as a root for its sector. On the first interaction, the player chooses exactly one starting root. Unlock that root for free once, making its neighboring nodes eligible for allocation. Changing roots must never accumulate permanent free bonuses from previous roots.

Players reach the other hubs through the graph. An additional hub costs 1 point to allocate. Do not unlock all six roots automatically: the routes between sectors must retain their traversal cost.

Access edges are **undirected by default**. A normal node can be purchased when it has at least one allocated neighbor connected to the starting root and satisfies its additional requirements. Requirements expressed through `all_of`, `any_of`, tags, minimum points invested in a sector, and exclusivity are separate conditions from graph adjacency.

Every node must be reachable from an appropriate root. The complete graph must permit travel into the other sectors, subject to explicitly declared exclusive choices. Traversal cycles are allowed; this graph is not required to be an acyclic hierarchy. Mandatory prerequisite relationships must nevertheless remain satisfiable.

### 7.2. Default costs

- Minor node: 1 point.
- Notable node: 2 points.
- Keystone: 3 points.
- Selected starting root: free.
- Another root reached through the graph: 1 point.

All **480 nodes** initially have a maximum rank of 1. The data format may support additional ranks later, but the initial content must not conceal several purchases inside a single node without a visible rank indicator.

Keystones within a sector may belong to an exclusive group allowing at most one active member. Keep exclusive choices optional or terminal so they cannot create an impossible blockade on the mandatory route into another sector.

### 7.3. Batch allocation

Clicking a node creates a local pending selection. It does not apply gameplay bonuses or debit the confirmed point balance. The selection may form an entire route by considering both confirmed allocations and pending nodes.

`Confirm` submits the intent to purchase the batch. The server validates the complete set, calculates its cost, and authorizes the transaction. Accept nodes received out of acquisition order when a valid purchase sequence exists: repeatedly expand the eligible set from the current confirmed build while evaluating the applicable requirements.

If any node is invalid, reject the entire batch with an explicit reason. Do not silently purchase only part of the requested skills. Point debit, confirmed allocations, recorded purchase costs, effect reconciliation, and the new player-state revision must describe the same successful transaction.

`Cancel` clears the pending selection without changing confirmed allocations. Removing a pending node that supports other pending nodes must identify the affected dependents and offer removal of that pending chain while preserving the confirmed build.

Update the final client build only after the authoritative server response. Prevent repeated confirmations while the corresponding request is in flight, and retain server-side duplicate-request protection.

### 7.4. Suggested paths

Provide a least-cost path preview to a distant node, weighted by the point costs of the required new allocations. Routes must respect prerequisites and exclusive choices. If the path algorithm cannot resolve a requirement combination, explain that limitation rather than presenting an unverified route as valid.

Hovering a distant skill must not purchase its route. Adding a suggested route to the pending selection requires an explicit action and must display the total point cost before confirmation.

## 8. Progression and the Point Economy

### 8.1. Default progression mode

The default mode is **manual purchase using vanilla experience**. `Buy Skill Point` converts the player's XP into exactly one available skill point.

Charge raw XP points rather than an integer number of experience levels. Levels have nonlinear costs. The purchase tooltip must show the raw XP cost, the available raw XP balance, and the balance that would remain.

Use this configurable initial cost curve:

```text
nextPointCost(p) = 100 + 20*p + 2*p*p
```

Here, `p` is the lifetime number of skill points obtained by purchase during that player's progression. The first purchase costs 100 XP; after 1 purchase, the next costs 122 XP; after 10 purchases, the next costs 500 XP.

Document and test the curve. Make its coefficients, progression limit, and global discount configurable by the server. Validate the resulting costs so configuration and rounding cannot create negative costs or unintended free purchases.

The default earned-point limit is **120 points**. Players must create partial builds with meaningful specialization and travel costs; normal progression must not permit allocation of the entire 480-node tree.

A point purchase requires all of the following:

1. A valid player obtained from the network connection context.
2. A progression mode allowed by the server.
3. Sufficient raw XP.
4. An earned-point count below the configured limit.
5. A current request and expected player-state revision.
6. An XP debit and credit of exactly 1 skill point in the same authoritative operation.

Respecialization does not return previously paid XP and does not reduce the lifetime purchase counter. Repeated resets must not restart the cost curve.

### 8.2. Consistent vanilla experience handling

Inspect the target-version sources to establish how experience level, fractional progress, and total XP are maintained. Use consistent conversions, including the level 15/16 and 30/31 boundaries. Do not debit one field while leaving the visible XP bar, level, and effective balance inconsistent.

Test an exact-cost purchase, a balance that is short by 1 XP, level 0, a high balance, and several consecutive requests. Rounding must neither create negative experience nor grant free points.

### 8.3. Ledger and invariants

Keep enough state to distinguish:

- Skill points earned through purchases.
- Points granted through administration or integrations.
- Active points spent: the sum of the recorded paid costs of currently active allocations, excluding allocations already refunded.
- Available points.
- The free starting root.
- Refunds that have already been processed.

Maintain this normal invariant:

```text
availablePoints + activeSpentPoints = purchasedPoints + netGrantedPoints
```

Record the actual price paid for each node and rank. A later JSON cost change must never refund more points than the player actually paid. The free starting root has a recorded point cost of zero.

The audit history may retain earlier purchases, but a refunded allocation no longer contributes to `activeSpentPoints`. Lifetime cumulative spending is a separate metric from the active expense used in the invariant.

Administrative grants do not reset the purchase curve. Operations exceeding the configured limit require an explicitly identified administrative override. Keep grants, adjustments, active expenditure, and available balance consistent after commands and migrations.

If a later extension introduces automatically earned custom skill XP, keep it mutually exclusive with vanilla-XP purchases by default and document all of its XP sources. Complete the default purchase mode before implementing this optional extension.

## 9. Tree Content and Build Identity

Assign the six sectors to their hubs as follows. All skill names, effects, routes, and descriptions are original authored content for this mod.

| Hub | Sector | Main identity |
|---|---|---|
| Left | Duelist | Precision, attack speed, mobility, and light weapons |
| Upper left | Guardian | Health, armor, shields, toughness, and protection |
| Upper right | Arcanist / Alchemist | Potions, beneficial effects, and optional arcane specialization |
| Right | Marksman | Bows, crossbows, projectiles, and ranged precision |
| Lower right | Artificer / Explorer | Mining, tools, exploration, and utility |
| Lower left | Berserker | Melee damage, heavy weapons, and offensive risk |

Sector names may be refined if their functional identities and hub assignments remain intact. English is the primary language for names and interface labels; provide complete `en_us` and `pt_br` localization through translation keys rather than hardcoded prose.

### 9.1. Minor nodes

Create **384 minor nodes**, 64 per sector. Use small, understandable benefits that accumulate into a build. Initial examples include:

- `+1` maximum-health point, equivalent to half a heart.
- `+2%` attack damage using the documented attribute operation.
- `+2%` attack speed.
- `+1%` movement speed.
- `+0.5` armor.
- `+0.5` armor toughness.
- `+0.02` knockback resistance.
- `+3%` mining speed when using an eligible tool.
- `+2%` projectile damage attributable to the player.
- `+1` percentage point of the mod's own critical-hit chance.

Define the calculation base, unit, and operation precisely. Repeated templates are acceptable for minor nodes when every node has its own stable ID, authored position, suitable name, and accurate description. Do not fill the graph with nonfunctional nodes, identical bonuses in every sector, or placeholder names such as `Skill 001`.

### 9.2. Notable nodes

Create **72 notable nodes**, 12 per sector, with recognizable names and implemented effects. Each sector needs subgroups supporting at least two play styles. Develop these directions:

- Duelist: recovery after a critical hit, speed with eligible light weapons, and mobility following a fully defined dodge action.
- Guardian: maximum health, blocking efficiency, and temporary resistance after a valid shield block.
- Arcanist / Alchemist: longer beneficial effects from consumed potions, support efficiency, and configured thematic resistance.
- Marksman: long-range damage, crossbow specialization, and benefits from fully charged shots.
- Artificer / Explorer: mining bonuses through tool and block tags, tool durability, and exploration mobility.
- Berserker: powerful attacks, health-conditioned damage, and compensation for using eligible heavy weapons.

When a mechanic such as dodging or magic is absent from base Minecraft, implement it completely or select an effective vanilla-compatible alternative. Every shipped description must correspond to real gameplay behavior. The standalone content must remain functional without optional magic mods.

### 9.3. Keystones

Create **18 keystones**, three per sector. Each must change a strategy by combining a meaningful advantage with a disadvantage. Keystones must be optional, visually distinctive, and governed by clear requirements.

Use these six initial designs:

1. **Reckless Fury — Berserker:** `+20%` eligible melee damage and `+10%` damage taken. Do not classify environmental damage as weapon damage dealt by the player.
2. **Bastion — Guardian:** `+20%` maximum health and `-8%` movement speed.
3. **Swift Hands — Duelist:** `+15%` attack speed with eligible light weapons and `-8%` damage from those weapons.
4. **Patient Aim — Marksman:** `+20%` damage from eligible projectiles when the valid firing origin is more than 12 blocks from the impact, and `-10%` at short range. Define how the original firing position and piercing impacts are handled.
5. **Volatile Concentrate — Alchemist:** `+25%` duration of beneficial effects from ingested potions and `+15%` incoming magic damage identified by supported damage tags. Refreshing an effect must not repeatedly compound its duration without a bound.
6. **Master Excavator — Artificer:** `+20%` mining speed under valid conditions and `+15%` tool wear caused by mining. Integrate durability handling without charging the same wear twice.

Treat these values as configurable starting points. The other twelve keystones must have distinct identities, work with the independent core, and have verifiable implementations. Together with six roots, 384 minor nodes, and 72 notables, they complete the **480-node tree**.

### 9.4. Balance and example builds

Deliver at least six example builds, one for each sector, plus two hybrid builds. Specify the routes and point budgets, including traversal and paid secondary-root costs. Every example must fit within **120 points**, with its bonuses and penalties calculated.

Document caps for the mod's own critical-hit chance, lifesteal, damage reduction, and mobility modifiers. Caps must limit this mod's contribution without erasing equipment effects or changes from other mods.

If lifesteal is implemented, calculate healing from eligible damage actually inflicted, with documented conditions and cooldowns. Initial damage against an invulnerable target must not generate healing.

Damage-triggered effects must prevent recursion: secondary damage cannot retrigger the same effect indefinitely. Explain how custom critical hits interact with vanilla critical hits.

Do not use nonexistent vanilla attributes such as `minecraft:critical_chance`. Register a namespaced custom attribute where appropriate, or implement the mechanic through events and explicit state.

## 10. JSON Data, Validation, and Reloading

### 10.1. Organization

Choose an original, versioned, documented format. An example structure for a server resource loader is:

```text
src/main/resources/
  assets/nostalgiaskills/
    lang/pt_br.json
    lang/en_us.json
    textures/gui/skill_tree/
    textures/skill_icons/
  data/nostalgiaskills/
    skill_trees/main.json
    skill_nodes/<node_id>.json
    skill_connections/main.json
```

The mod must explicitly load these custom directories. Placing JSON files in these locations does not automatically create a registry or progression rules.

Keep visual positions separate from effects, costs, and prerequisites. Use namespaced IDs so external datapacks can provide their own namespaces and override definitions according to pack priority. Document whether a definition's ID comes from its resource path or an explicit field, and validate their agreement if both are present.

### 10.2. Example Node

The following example describes the **proposed schema for this new mod**, not the schema used by Pufferfish or Passive Skill Trees. Implement the corresponding parser or codec, and update the final example to match the delivered format:

```json
{
  "schema_version": 1,
  "id": "nostalgiaskills:guardian/vitality_01",
  "tree": "nostalgiaskills:main",
  "sector": "guardian",
  "type": "minor",
  "position": { "x": -88.0, "y": -186.0 },
  "name": "skill.nostalgiaskills.guardian.vitality_01.name",
  "description": "skill.nostalgiaskills.guardian.vitality_01.description",
  "icon": "nostalgiaskills:textures/skill_icons/vitality.png",
  "frame": "bronze_circle",
  "cost": 1,
  "max_rank": 1,
  "requirements": {
    "all_of": [],
    "any_of": [],
    "minimum_sector_points": 0
  },
  "effects": [
    {
      "type": "attribute",
      "attribute": "minecraft:generic.max_health",
      "operation": "add_value",
      "amount": 1.0
    }
  ],
  "tags": ["health", "defense"]
}
```

Verify attribute IDs against the Minecraft 1.21.1 sources. Explicitly map the JSON `operation` field to a supported operation; this field is not an automatic NeoForge API.

Define both `all_of: []` and `any_of: []` as the absence of that restriction. A nonempty `all_of` list requires every condition; a nonempty `any_of` list requires at least one condition. If both lists are nonempty, both groups must be satisfied. Validate path adjacency separately.

Define whether sector-point requirements count only confirmed allocations or also valid earlier purchases within the current transaction. During batch validation, a node must not satisfy its own prerequisite with the points spent on that same purchase.

### 10.3. Connections

Each connection must declare:

- An optional stable ID, or an ID deterministically derived from its endpoints.
- Node A and node B.
- Whether the edge is undirected or an explicitly directed exception.
- Its visual style.
- Optional intermediate points.
- Whether it forms a bridge between sectors, when relevant.

Rendering and eligibility checks must use the same validated graph. Do not maintain one set of edges for drawing and a different set for unlocking skills.

### 10.4. Required Validation

Before replacing the active tree, validate:

- Unique, valid IDs.
- Existing tree, node, effect, and prerequisite references.
- Finite coordinates.
- Costs and ranks within allowed limits.
- No duplicate edges or accidental self-edges.
- Existing roots and reachability of the intended graph components.
- Consistent exclusivity groups.
- No impossible cycles of mandatory prerequisites, even though traversal cycles are allowed.
- Recognized effect types, finite values, and supported operations.
- Data-size and text-length limits.
- Visual overlaps and problematic crossings, reported as authoring diagnostics.

Textures are client resources. Provide icon fallbacks and client diagnostics without requiring graphical classes on the server.

### 10.5. Reloading During a Session

The mod must support updates through **`/reload`**. Implement a server resource loader that validates definitions and replaces an immutable snapshot. For 1.21.1, verify `AddReloadListenerEvent` and `OnDatapackSyncEvent` in the official sources: [reload listener](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/event/AddReloadListenerEvent.java) and [datapack synchronization](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/event/OnDatapackSyncEvent.java).

Registering a datapack registry that loads at world startup does not by itself implement hot reload. Complete the chosen loading and synchronization strategy so connected clients receive the new definitions.

Build and validate a new snapshot before committing the replacement. If a custom definition is invalid, retain the last valid snapshot for the current session and report the file, field, and reason. During initial loading, fall back to a verified internal tree if custom loading fails and the game can safely continue; otherwise, report a clear failure instead of silently creating an empty tree. Do not conceal failures in unrelated vanilla or third-party resource systems.

Increment the graph revision and invalidate pending selections based on the previous revision. Reconcile existing builds and remove obsolete bonuses. Migrate removed nodes through an explicit ID map when possible; otherwise, refund their actual recorded purchase costs exactly once and record the migration.

Also revalidate allocations whose IDs still exist: they may become disconnected, lose prerequisites, or conflict after a definition change. Continue reconciliation until the build is valid, refunding cascading removals at their recorded costs. Resolve new conflicts in a documented deterministic order that preserves the oldest valid acquisition, with node ID as the tie-breaker. If the initial root disappears without a valid migration, refund invalidated purchases and allow selection of one new free root.

Preserve historical progression counters throughout migration. Offline players must undergo the same reconciliation on their next login before their bonuses or purchase operations become active. Refunds increase the available balance and remove the corresponding costs from active spending; they do not create additional historically earned points.

## 11. Persistence and Player Lifecycle

Use a persistent player **Data Attachment** with appropriate serialization and a schema version. In the target version, attachments require explicit client synchronization; see [Data Attachments for 1.21.1](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/).

Persist at least:

- Schema version.
- Tree IDs and the initial root ID.
- Confirmed nodes and ranks.
- Actual purchase costs for each allocation.
- Acquisition order needed for deterministic conflict reconciliation.
- Available points and historical progression counters.
- Relevant administrative grants and net adjustments.
- Player-state revision and the graph revision last reconciled.
- Completed migration and refund markers.

Pending selections remain on the client and must not be persisted as purchases. Document the cooldown strategy: preserve or reconstruct any state necessary to prevent reconnecting from improperly resetting significant cooldowns.

Preserve progression after death by default. Configure `copyOnDeath` or perform the necessary copying through the clone event; do not run both mechanisms in a way that duplicates state. Distinguish death from returning from the End according to the target API.

Verify login, logout, respawn, dimension travel, returning from the End, server restarts, and datapack updates. The persisted attachment is the source of confirmed allocations; attribute modifiers must be reconciled from it.

## 12. Multiplayer and Protocol

### 12.1. Server Authority

The client sends intentions. The server determines balances, costs, eligibility, effects, refunds, and item consumption. Received IDs or values never replace validation against actual server state.

Obtain the acting player from the connection context. Ordinary allocation requests must not accept another player's UUID as their target.

Do not send arbitrary console commands or executable class names as effects. Effects must use types registered and recognized by the server.

### 12.2. Suggested Payloads

Client to server:

- Request a snapshot when opening the interface.
- Purchase one skill point.
- Confirm a batch of node allocations.
- Request an allowed respec or refund operation.

Server to client:

- Graph definitions or a graph snapshot with its revision.
- Player state.
- A transaction result containing the request ID.
- A rejection reason and an updated snapshot when required.
- Values needed for previews and client presentation.

Every mutating operation must contain a request ID and the expected player-state revision. Allocation, refund, and other graph-dependent requests must also contain the expected graph revision. Keep these revisions distinct.

Maintain bounded request deduplication or sequential processing that prevents the same purchase from being applied twice. Stale revisions must produce an understandable rejection and a state update. Do not debit resources or apply effects before the complete transaction has passed validation.

### 12.3. APIs and Limits

Use `CustomPacketPayload`, `StreamCodec`, and registration compatible with `RegisterPayloadHandlersEvent` in the target version. Validate payload size, collection cardinality, and IDs before processing. Execute player mutations on the correct thread. See the [official 1.21.1 payload documentation](https://docs.neoforged.net/docs/1.21.1/networking/payload/).

Do not resend hundreds of node definitions on every click. Send definitions when a player joins or the graph revision changes; use small state snapshots or deltas for purchases. Respect the version's payload limits: server-to-client payloads may contain at most 1 MiB, and client-to-server payloads must contain less than 32 KiB. If the complete graph exceeds the selected transmission budget, use bounded fragmentation with validated reassembly, timeouts, and limits on pending data.

Synchronize state after point purchases, allocation confirmations, refunds, administrative commands, respawn, and relevant lifecycle events. When the screen opens, show a short loading state and prevent actions until the authoritative snapshot has arrived.

Return translatable error codes for insufficient points or XP, unknown nodes, invalid paths, missing prerequisites, conflicts, reached limits, stale revisions, and duplicate requests.

## 13. Implementing Real Effects

### 13.1. Attributes

Use modifiers with deterministic `ResourceLocation` identifiers in the mod's namespace. The migration to resource identifiers is documented in the [official Minecraft 1.21 primer](https://docs.neoforged.net/primer/docs/1.21/).

Recalculating the tree must be idempotent. Choose one consistent strategy: transient modifiers reconstructed from the attachment, or persistent modifiers reconciled rigorously. Do not retain obsolete persistent modifiers and add transient duplicates on top.

Remove or update only modifiers owned by this system. Preserve base values and equipment or third-party bonuses. Do not change an attribute's global base value to represent a skill.

After maximum health decreases, clamp current health to a valid value without granting free healing. Remove conditional effects when their activation conditions no longer hold.

### 13.2. Event Effects

Separate permanent effects from effects triggered by damage, blocking, drinking potions, mining, or tool use. Calculate gameplay effects on the server.

Document the following for every event effect:

- Trigger.
- Activation conditions and tags.
- Amount and unit.
- Cooldown.
- Stacking limit.
- Interaction with vanilla critical hits, armor, and other multipliers.
- Prevention of recursion or duplicate application.
- Removal behavior during respec.

Identify the player responsible for projectiles without assuming that the direct damaging entity is the player. Distinguish melee, projectile, magic, and environmental damage using sources and tags supported by the target version.

### 13.3. Optional Integrations

Prefer configurable project tags for light weapons, heavy weapons, tools, and projectiles. Do not classify items by text in their display names.

Missing external mods must not cause `ClassNotFoundException` or prevent the core mod from working. Isolate integration classes behind controlled loading paths and declare optional dependencies correctly.

If an external effect is unavailable, make that limitation explicit in diagnostics and content. The base 480-node tree must remain functional with Minecraft and NeoForge alone; required skills must not silently depend on third-party magic systems.

## 14. Respecialization and Refunds

Include an original item named **Orb of Reconfiguration**, with its own texture, crafting recipe, and `en_us` and `pt_br` translations. The server validates consumption, and the item is consumed only when the reset successfully completes.

The default reset must:

1. Remove all paid allocations from the selected tree.
2. Remove their effects and reconcile cooldowns according to the documented policy.
3. Refund exactly the recorded point costs of the removed active allocations.
4. Preserve the lifetime earned-point count and the progressive cost of future point purchases.
5. Permit selection of exactly one new free starting root, retaining no bonuses from the previous root.
6. Increment the player-state revision and synchronize the result.

Before committing, show how many points will be returned and which skills will be removed. Canceling the reset interface does not consume the item or modify the confirmed build. A reset refunds skill points, not the vanilla XP used to acquire them.

If individual-node refunds are implemented, allow removal only when the remaining build stays valid and connected to the starting root. Otherwise, offer removal of the dependent chain and show its cost and resulting build before confirmation. Use the actual recorded costs rather than current JSON prices, and mark refunds processed exactly once.

Two concurrent requests must not consume a single orb while granting two refunds. Orb consumption, allocation removal, point credit, effect reconciliation, revision advancement, and request deduplication must form one coherent authoritative operation.

## 15. Commands, Configuration, and Authoring

### 15.1. Commands

Implement these commands with argument suggestions and clear, translatable feedback:

```text
/nostalgiaskills open
/nostalgiaskills inspect <player>
/nostalgiaskills points add <player> <amount>
/nostalgiaskills points set <player> <available_amount>
/nostalgiaskills unlock <player> <node_id>
/nostalgiaskills reset <player>
/nostalgiaskills validate
```

Players may use `open` for themselves. Operations targeting other players, point grants, administrative resets, and detailed validation require suitable permission, normally permission level 2.

Define `points set` as adjustment of the available balance accompanied by the corresponding change to net granted points, preserving recorded active purchase costs. Do not let a command silently violate the ledger invariant:

```text
availablePoints + activeSpentPoints = purchasedPoints + netGrantedPoints
```

Administrative grants and balance adjustments must not reduce the lifetime purchase counter or restart the XP cost curve. Feedback must identify the resulting balance and any explicit administrative override of the configured progression limit.

`unlock` respects prerequisites by default. If an administrative force-unlock mode exists, require an explicit option and reconcile the necessary conditions, costs, and ledger entries. Invalid builds must not become normal player state.

### 15.2. Configuration

Server configuration controls:

- Earned-point limit, defaulting to 120.
- XP cost-curve coefficients and permitted global discounts.
- Default point costs by node type.
- Progression preservation after death.
- Reset and refund rules.
- Caps for this mod's own effects.
- Weapon and tool classification tags.
- Optional integrations.
- Request-rate and data-size limits.

Client configuration controls:

- Visual scale and zoom limits.
- Glow intensity.
- Background decoration and reduced-motion behavior.
- Detailed tooltip display.
- Contrast alternatives.

The server determines all values that affect gameplay. A local client setting must not lower purchase costs, change prerequisite rules, or increase bonuses. Validate configuration values and provide understandable diagnostics for unsupported settings.

### 15.3. Authoring tools

The mandatory core includes a documented importer/exporter or validation utility for editing tree data outside the game. A visual development editor with node dragging, connection creation, effect inspection, and datapack export is desirable.

The editor must not displace the work needed to finish the playable mod. Deliver the complete authored 480-node tree; do not replace it with a promise that the user can design the content later. Authoring exports must retain stable IDs, explicit coordinates, valid connections, costs, requirements, and translation keys.

## 16. Code Organization and Performance

Separate responsibilities. Use this conceptual package structure, adapting it to the actual implementation:

```text
dev.nostalgiacraft.skills
  NostalgiaSkills
  registry/
  data/
  graph/
  progression/
  effects/
  network/
  command/
  config/
  compat/
  client/
    screen/
    render/
    input/
```

Suitable classes include `SkillNodeDefinition`, `SkillGraphSnapshot`, `PlayerSkillState`, `AllocationService`, `ProgressionService`, `RespecService`, and a registry of supported effect types. Adjust names to the actual project; do not create empty abstractions merely to fill the package outline.

Use immutable snapshots for graph definitions and testable domain state separated from rendering. The screen must not own the authoritative economy, final eligibility checks, or point ledger. Keep client-only graphics and input classes outside dedicated-server loading paths.

Performance requirements:

- Do not scan all 480 nodes to recalculate every player's bonuses on every tick.
- Recalculate permanent effects when the build or applicable definitions change.
- Use events or bounded updates for dynamic conditions.
- Cull nodes and edges outside the camera viewport while retaining visible portions of connections crossing its boundary.
- Reuse line and icon data where practical.
- Use ID lookup tables and adjacency lists.
- Avoid rebuilding tooltips and large collections unnecessarily every frame.
- Record the hardware, settings, and test scenario behind every FPS or frame-time measurement.

Maintain responsive interaction throughout the full tree. Numerical performance targets may be declared satisfied only after actual measurement.

For a GUI without an inventory, use a dedicated `Screen`. Consult the official [Screens 1.21.1 documentation](https://docs.neoforged.net/docs/1.21.1/gui/screens/) and [Key Mappings 1.21.1 documentation](https://docs.neoforged.net/docs/1.21.1/misc/keymappings/). Do not introduce an inventory container solely to open the skill tree.

## 17. Tests and Acceptance Criteria

### 17.1. Domain Tests

Automate meaningful tests for:

- Purchasing a valid adjacent node.
- Rejecting a disconnected node.
- Accepting a valid path batch received out of acquisition order.
- Exact balances and insufficient balances.
- Atomic rejection of a partially invalid batch.
- `all_of` and `any_of` requirements, including empty lists.
- Keystone exclusivity.
- Selection and removal of dependent pending nodes.
- Refunds that preserve connectivity and prerequisites.
- Refunding recorded purchase costs after a definition's price changes.
- XP curves and progression limits.
- Ledger consistency before and after purchases, refunds, and administrative adjustments.
- Duplicate requests and stale player or graph revisions.
- ID migrations and exactly-once refunds.
- Reconciliation after root removal, prerequisite changes, or new conflicts.
- Idempotent calculation, application, and removal of effects.

Validate the complete 480-node dataset during the build or through a dedicated task. Tests must verify meaningful behavior instead of merely repeating implementation logic.

### 17.2. In-Game Tests

Execute and record:

1. A successful build using `gradlew.bat build` on Windows or the equivalent wrapper command for the environment.
2. A development client starting without errors.
3. A dedicated server starting without loading graphical classes.
4. Two players maintaining independent balances and builds.
5. Purchasing points with vanilla XP, selecting nodes, and confirming batches.
6. Canceling pending changes without applying effects.
7. Actual effects from at least one node in every sector.
8. Death, respawn, logout and login, and dimension changes.
9. A server restart preserving player state.
10. Using the respec orb and receiving the correct refund.
11. A valid reload and a reload containing invalid custom JSON.
12. An existing build after removing or migrating a node, including cascading invalidation when applicable.
13. Repeated purchase requests under simulated latency or rapid request submission.
14. Operation without optional mods installed.

Add a separate scenario for every delivered integration. Do not claim compatibility with every modpack without testing the relevant environment.

### 17.3. Visual QA Against the Authored Layout

Define an original layout specification and record the final node positions, connections, control constraints, spacing rules, and decoration layers in `LAYOUT_MANIFEST.md`. Document the original background's composition, palette, asset layers, and readability requirements in `ART_DIRECTION.md`.

Capture the implemented interface at **1000 × 563**, **1280 × 720**, and **1920 × 1080**, using more than one GUI scale. Validate the implementation against its authored specifications:

- The center and six hubs use the documented logical coordinates and camera transform.
- Controls obey their layout constraints and remain usable after translation or resizing.
- Node frames have consistent relative sizes and clear visual hierarchy.
- Dense branches remain readable without unintended node overlap.
- Stars, nebula layers, the fractured moon, and observatory ruins are original background assets created from scratch and exported from the delivered production sources.
- Background contrast and decorative motion preserve node, path, text, and tooltip legibility.
- Highlighted routes are continuous and correspond to valid graph connections.
- The bottom progression bar remains visible and legible.
- Tooltips remain within the window near all four edges.
- Pan and zoom preserve graph topology, cursor hit testing, and fixed-control placement.
- Keyboard focus, narration, and reduced-motion or contrast settings behave as specified.

Generate an annotated layout diagram and a position-and-connectivity report from the delivered graph data. Every declared node and edge must be represented consistently in the authored layout, runtime rendering, and progression checks. Document intentional line crossings; do not treat a crossing as an additional graph connection.

Provide development fixtures for a fresh character, a focused build, a hybrid build, insufficient points, conflicting choices, and pending changes. Keep fixtures clearly identified as test scenarios and isolated from real player progression.

Decoration and animation must not conceal structural errors. Acceptance depends on the documented layout, readable connectivity, usable controls, original asset production, and correct interaction across the tested resolutions.

### 17.4. Conditions That Prevent Declaring Completion

- The project does not compile.
- The dedicated server fails because of a client-class dependency.
- The tree is empty or consists only of example nodes.
- Gameplay nodes lack real implemented effects; documented structural roots and purely decorative elements are exceptions.
- The layout changes randomly during gameplay.
- XP debits leave inconsistent experience state.
- Purchases are decided solely by the client.
- Bonuses duplicate after death, login, or reload.
- Resetting multiplies points or improperly resets purchase prices.
- Rendered connections differ from the graph used for eligibility.
- Visual QA against the authored layout has not been demonstrated.
- The background or required art assets have not been created and delivered.
- Mandatory dependencies are undeclared.

## 18. Deliverables and Execution Sequence

Deliver:

1. The complete project with the Gradle Wrapper, Java sources, metadata, and pinned versions.
2. The final JAR in `build/libs/`, produced by a successful build.
3. The complete JSON tree, including a catalog of nodes, effects, costs, and connections.
4. Original icon and frame sprites, the respec item art, and an original space background created from scratch.
5. Editable production source files for the background and other authored assets, together with the documented export pipeline. If assets are generated with code, include the generation sources, deterministic settings, and export commands.
6. `pt_br` and `en_us` translations.
7. Configuration files and example datapacks.
8. `README.md` covering installation, controls, commands, and requirements.
9. `ARCHITECTURE.md` covering persistence, networking, effects, transaction rules, and reloading.
10. `TREE_DESIGN.md` covering sectors, balance, and example builds.
11. `LAYOUT_MANIFEST.md` containing the authored node and edge layout, logical coordinates, control constraints, intentional crossings, and validation rules.
12. `ART_DIRECTION.md` explaining the original visual direction, background composition and layers, palette, typography, sprite conventions, and asset production process.
13. In-game captures, an annotated layout diagram, validation reports, and records of the tests actually executed.
14. A concise, exact list of any remaining limitations.

Execute the work in stages; do not stop after producing a plan:

1. Verify the target platform and study the linked mod and technical documentation.
2. Author the original layout manifest, art direction, and graph design.
3. Set up the NeoForge project pinned to Minecraft 1.21.1.
4. Implement the model, resource loader, validation, and progression domain.
5. Implement persistence, server authority, transactions, and networking.
6. Create the original background, icons, frames, and item art, preserving their production sources.
7. Implement rendering, controls, tooltips, and previews.
8. Complete the content and effects in every sector.
9. Implement point purchases, statistics, respec, commands, and configuration.
10. Compile, test the client and dedicated server, and fix failures.
11. Validate the interface against the authored layout and art specifications, then correct visual or interaction defects.
12. Deliver the artifacts and evidence of actual verification.

When presenting the final result, describe working behavior first, followed by delivered files, tests actually performed, and material limitations. If the environment blocks a step, explicitly identify the unverified result. Do not replace required code with pseudocode or present TODOs as completed features.

**Final priority:** deliver an extensive, original skill tree with stable data, correct server behavior, meaningful build choices, a precise authored layout, and a fully functional interface supported by original visual assets.
