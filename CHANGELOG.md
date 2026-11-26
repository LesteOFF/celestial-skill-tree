# Changelog

## [2.1.4]

> **Updating an existing world:** the XP already donated to the shared constellation moves onto its main path in
> order (Kindling, Hearth, Beacon, then Zenith), so every world keeps the community bonuses it had. Those donations
> are listed as "Earlier patrons and basins".

### Added

- The shared constellation is now a constellation of ten stars with branches instead of a straight path. Click a star
  to pick it and donate to it; a star opens once the one before it is lit.
- Rival stars: the Forge and the Hunt, and the Bulwark and the Mirage, close each other. Once one of them is lit, the
  server has made its choice and the other one closes for good.
- New community stars: Forge, Deep Vein, Hunt, Blood Moon, Bulwark and Mirage.
- Patrons: the server remembers how much XP each player donated. A star's tooltip shows who lit it, the community
  screen ranks the top five patrons and your own place, and the top patron gets a golden ✦ before their name in the
  player list.

### Changed

- The first tab of the bar is now named Skill Tree, and the JEI pages of the Telescope and the Orb of Reconfiguration
  speak of the skill tree too.
- The community screen now looks like the skill tree: the same sky, stars drawn as nodes with an icon, a side column
  with a card for the selected star and the patrons ranking that open and close, a donation bar, a help panel and a
  list of every patron. `communityPanelSide` in the client config moves the column to the right.
- The Community Basin feeds the next star on the main path and never picks a fork; the players choose those.
- `/celestialskilltree community set|add` now takes the star: `community set <star> <xp>`.
- Data packs: `xp_required` in `skill_community` files is now what that one star needs, and stars take a `parent`, a
  `position`, an optional `exclusive` group and an optional `icon` (a skill icon texture, a star when left out).
  `communityTierUnlocked` (KubeJS) and `CommunityTierUnlocked` fire once
  for each star that lights.

### Fixed

- The Skill Tree tab on the community screen brings you back to the main tree even when you came from the Hidden Sky.
- The tabs now open and close smoothly when you move between the tree and the community screen.
- Zenith's description lists what it really gives: +2 Max Health and +10% Healing Received.

## [2.1.3]

### Added

- Legendary Tabs: a Celestial Skill Tree tab in the inventory opens the star chart.
- JEI: every item and block has an information page saying where it comes from and what it does.
- Advancements in a Celestial Skill Tree tab: learn your first skill, learn a keystone, learn 50 skills, pick up a Star
  Fragment, slay a Constellated monster, witness an eclipse and donate to the shared constellation. Players of existing
  worlds earn the skill milestones they already reached at their next login.

### Changed

- Hidden Sky and Community are now tabs in a bar at the top center of the star chart, next to a new Star Chart tab
  that brings you back. The open tab shows its name in its own color and the community screen shows the same bar. The
  left dock keeps stats, birth sign, home, fit and help.
- The stats panel is now a character sheet in the tooltip style, drawn smaller than the rest of the HUD: your name
  beside your birth sign's constellation, points at a glance (total = spent + to spend + free, with free already
  leaving out the pending selection like the counter under the tree), attributes grouped into Defense, Offense and Utility with
  an icon each (new ones for reach, burning time, fall damage, swim speed and attack knockback, and the matching skill
  icon for luck, oxygen, safe fall, spell schools, mana and the other attributes of supported mods), a hover tooltip
  with the full name when a name is cut short, the tree's share in its own column, the pending selection previewed on
  the attribute it changes, and
  conditional bonuses, triggered effects and keystones in their own lists by skill name. A skill learned more than once
  is shown once with its count (×2) and its bonus added up the way the game applies it (ranks and black holes
  included). It scrolls wherever the mouse is over it or with a scrollbar you can drag, and no longer covers the
  Cancel button.
- Constellated monsters are rolled faster, and tamed creatures that inherit the tree only touch their attributes
  when their owner's tree changes.
- Confirming skills plays a new animation: a small golden comet runs along each new link and the skill lights up with
  a thin ring and four sparkles, all sized to the skills on screen instead of a large white blur.

### Fixed

- Switching between the Star Chart and Hidden Sky tabs fades the sky out and back in instead of jumping.
- Esc on the community screen or the birth chart closes the star chart like it does on the tree, asking first when a
  selection is pending. Their Back button and the tabs still return to the tree.

- Joining a new world no longer freezes the server forever. A mob spawned with a chunk was checked for a spawner
  block under its feet before that chunk had finished loading, so the server waited on itself. Constellated mobs are
  now empowered on the next tick, so scripts and addons listening to their spawn never run mid chunk load either.
- A shooting star no longer makes the server wait for a chunk that is still generating to pick its landing spot.
- Leaving a world forgets the boss bars of its Constellated monsters, so reopening it no longer reuses stale ones.
- With Enhanced Boss Bars 1.1 or newer, a Constellated monster's boss bar shows its own frame again: a new
  constellation design with a golden star, which Enhanced Boss Bars now picks up as a resource like its own bars.

## [2.1.2]

> **Updating an existing world:** a world keeps the tree it was drawn with, so the new Astral Sorcery skills only
> appear after an operator runs `/celestialskilltree regenerate confirm`. It draws the whole tree again from the current
> catalog. Learned skills that are still in the tree and still connected stay learned; every learned skill that leaves
> the tree or loses its connection is refunded at the price paid (offline players at their next login), so no one
> loses points. New worlds get the new skills on their own.

### Added

- Astral Sorcery: each new research tier gives Star Fragments (`astralTierFragments`, 2 per tier) and each memorized
  constellation gives more (`astralConstellationFragments`, 1).
- Astral Sorcery: the star shower happens on the night of its lunar eclipse (`starShowerFollowsAstralSorcery`).
- The `astral_tier` condition (`tier`: `shimmer` to `radiance`), and `astral_attuned` without a `constellation` holds
  for any attunement.
- Three keystones that only exist with Astral Sorcery: Starborn (arcanist), Armara's Bulwark (guardian) and Radiant Sight
  (marksman).
- Three Astral Sorcery themes spread minor skills along the branches and put a notable at a branch tip: Starlit
  Plating with Resonant Aegis (guardian), Bootes' Hunt with Guiding Star (marksman) and Starlight Lore with Illuminated
  Draughts (arcanist).
- With Astral Sorcery installed, the Orb of Reconfiguration is made only in its Starlight Infuser, from an eye of ender;
  the crafting table, Create and Ice and Fire recipes turn off.
- Astral Sorcery: the eclipse follows its solar eclipse (`eclipseFollowsAstralSorcery`), each birth sign doubles its
  bonus once its paired constellation is discovered, and the `astral_attuned` and `astral_discovered` conditions.
- `bonus_effects` in birth sign files: every entry whose mod is installed adds its effects and text.
- The birth chart tooltip is now a card like the skill tooltips: the sign's constellation twinkles in its header, a
  counter shows how many bonuses are active, and each bonus has its own line marked discovered or not yet discovered.
- Game tests for the Astral Sorcery and No Man's Land integrations, run with the mods placed in `run-pack/mods`.

### Fixed

- No Man's Land's sun dogs no longer shine around an eclipsed sun.
- With No Man's Land installed, the eclipse darkens the clouds and shows the stars again (its cloud colour and star
  brightness hooks used to replace the eclipse's).
- With Cirrus' angled sun orbit the eclipse disc covers the sun again, Cirrus' clouds lose their sunlight and its
  sky palette no longer brightens the fog during an eclipse.
- The birth chart gallery shot shows the twelve signs again, and the eclipse shot always clears the weather first.

## [2.1.1]

### Changed

- Every skill icon, HUD icon and tooltip marker was redrawn.
- New node frames: each node type has its own frame, dim until the skill is learned and lit once it is.
- Rings, rank pips, lock, conflict and sealed marks were redrawn to match the new frames.
- The search bar and the tool dock are larger, so their icons show at full size.
- Black holes now show a single purple swirl around the node instead of the stacked layers.
- The arrows between two linked astral gates only show while one of the gates is selected.
- New skill tree background and planet, dimmed so the tree stands out.
- Hiding the game interface also hides the skill tree HUD, for clean screenshots.
- Learned skills are unlearned only through a full reset with the Orb of Reconfiguration. Server owners can turn
  single-skill refunds back on with `allowSingleSkillRefunds = true`.

### Fixed

- Skill icons no longer spill out of their frames, including the astral gate.
- Sealed skills show their mark again instead of a missing texture.

## [2.1.0]

### Added

- Mage class for Ars Nouveau and Malum as well as Iron's Spells 'n Spellbooks, with new themes and keystones for each.
- Cancellable pre-events, more API events and admin commands for audit, refund, toggle, cooldown, discovery, birth sign
  and community, all with player selectors.
- Floating astral-glass HUD with search filters, a help panel, undo and redo, rebindable shortcuts and an in-game
  config screen.
- Bonus summaries in weapon and tool tooltips.
- `schema_version` in every data file and a shipped `skill_rules/main.json`.
- New models and textures for the telescope, the stellar condenser, the community basin, the living fragment and the
  stellar ammo.
- Optional integrations, links, logo and credits in the mod metadata.

### Changed

- Renamed from Nostalgia Skills to Celestial Skill Tree: the mod id is now `celestialskilltree`, the command
  `/celestialskilltree` (or `/celestial`) and the config files `celestialskilltree-*.toml`.
- Rebalanced ranks, black hole multipliers, healing and potion bonuses.
- The server config reload re-prices the tree and re-applies the caps.
- Faster tree rendering, sky events, reconcile and point purchases.
- The QA harness and game tests are no longer in the released jar.
- The skill tree background is open space only: the stone platform and floating ruins are gone.

### Fixed

- Wallet experience is charged before skill points are granted.
- Cheat death runs before other death handlers and acts like a totem.
- Stolen skills, ranks and switched off keystones survive logout, death and build swaps.
- Request flooding, permission levels for economy commands, claims in boon actions and spawner fragment farms.

## [2.0.0]

- Ranked skills, exclusive keystone pairs and black holes.
- Skill tooltips as cards and a command to draw the tree again.
- Sounds, the opening wave and confirm sparks in the skill tree.
- Pack game tests for the integrations.
