# Changelog

## [Unreleased]

### Added

- Legendary Tabs: a Celestial Skill Tree tab in the inventory opens the star chart.

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
