# Changelog

## [2.1.0] - 2026-10-23

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
