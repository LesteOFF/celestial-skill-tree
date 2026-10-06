# Tree Design — Nostalgia Skills

The tree is a 484-node constellation: **6 root hubs + 384 minors + 72 notables + 18 keystones + 4 gates**, 80 nodes per
sector. All content is data (`src/main/resources/data/nostalgiaskills/skill_nodes/**`) generated from the authored
tables in `tools/layout/content.py`; positions and connections come from `tools/layout/blueprints.py`
(see [LAYOUT_MANIFEST.md](LAYOUT_MANIFEST.md)). Every node has a real effect; descriptions are generated from the
same effect data, so text cannot drift from behaviour.

## Economy

| Rule | Value |
|---|---|
| Minor / notable / keystone cost | 1 / 2 / 3 points |
| Starting hub | free, exactly once (a reset grants one new free choice) |
| Other hubs reached through the graph | 1 point |
| Point price in raw XP | `100 + 20p + 2p²` (p = points bought so far): 100, 122, 148, … 500 after 10 |
| Gate (portal) | 3 points per pair; learning one end opens its twin for free |
| Purchasable points | 10,000 by default (server config `pointLimit`), i.e. no practical cap |
| Whole tree | 593 points from any starting hub |
| Cheapest route between adjacent hubs | 19–27 paid points (requirement ≥ 8) |

Nothing excludes anything else: the six keystone pairs that used to be exclusive are all learnable together, and with
enough points one character can own the whole tree. The rising XP price of each point is the only brake. The four
gates (east ↔ west, north ↔ south) sit at 60% of the tree's radius and let a build cross to the opposite side for 3
points instead of walking around.

## Sectors and identities

| Hub | Sector | Identity | Signature notables | Keystones (* marks the former exclusive pairs) |
|---|---|---|---|---|
| West | Duelist | precision, attack speed, light weapons, evasion | Flowing Strikes (light-weapon speed), En Garde (heal after crit), Fleet Footwork (speed after a dodge), Measured Cut | Swift Hands*, Thousand Cuts*, Wind Dancer |
| Upper left | Guardian | health, armor, toughness, shields | Gatekeeper, Iron Will (max health), Shield Wall (Resistance after a shield block), Watchtower (heal on block), Shield Drill | Bastion*, Living Aegis*, Rooted Stance |
| Upper right | Arcanist / Alchemist | drunk potions, healing, magic resistance | Steady Hand / Quintessence (potion duration), Catalyst / Sympathetic Link (on-drink support), Panacea (fire), Warded Path (magic) | Volatile Concentrate*, Elixir of Ages*, Arcane Ward |
| Right | Marksman | bows, crossbows, range | Eagle Eye / Long Shot / Rangefinder (beyond 12 blocks), Lower Limb / Crossbow Drill, Upper Limb / Taut String (fully charged shots) | Patient Aim*, Volley Doctrine*, Heavy Bolts |
| Lower right | Artificer / Explorer | mining, tool durability, exploration | Rich Seams / Lantern Post (mining with tagged tools), Tool Care / Reinforced Handles (durability), Trailblazer / Prospector's Camp (mobility) | Master Excavator*, Tinker's Bargain*, Wayfinder |
| Lower left | Berserker | melee damage, heavy weapons, risk | Skull Splitter / Heavy Handed (heavy weapons + speed compensation), Berserk (below 50% health), Bloodletting (lifesteal), Battle Cry | Reckless Fury*, Juggernaut*, Death Wish |

Each sector has at least two play styles (e.g. Marksman: long-range bow vs crossbow; Artificer: deep mining vs
exploration mobility), three arteries feeding about eight clusters, and keystones on optional terminal spurs.

### The six specified keystones (default values, all configurable through data)

| Keystone | Advantage | Drawback | Implementation notes |
|---|---|---|---|
| Reckless Fury | +20% melee damage dealt (player melee attacks only) | +10% damage taken (all sources) | environmental damage is never treated as weapon damage |
| Bastion | +20% max health (of base) | −8% movement speed (of base) | attribute modifiers; health clamped, never healed, when max drops |
| Swift Hands | +15% attack speed while holding a light weapon | −8% melee damage with light weapons | `nostalgiaskills:light_weapons` tag (default `#minecraft:swords`) |
| Patient Aim | +20% projectile damage when the shot travelled >12 blocks | −10% at ≤12 blocks | distance from the recorded firing origin to the victim; piercing hits each measure from the same origin |
| Volatile Concentrate | +25% duration of beneficial drunk potions | +15% magic damage taken (`#nostalgiaskills:magic`) | duration target is computed from the potion, so re-drinking refreshes but never compounds |
| Master Excavator | +20% mining speed with eligible tools | +15% chance of one extra wear point per mined block | wear settled once per block at end of tick; never charged twice |

## Effect vocabulary

| Type | Meaning |
|---|---|
| `attribute` | Permanent modifier on a vanilla or `nostalgiaskills:*` attribute (`add_value`, `add_multiplied_base`, `add_multiplied_total`). |
| `conditional_attribute` | Same, present only while a condition holds (`holding` tag, `sprinting`, `sneaking`, `blocking`, `below_y`, `health_below`). Re-evaluated every 10 ticks and on equipment change, only for players whose build has one. |
| `damage_modifier` | Relative change of damage dealt/taken in a scope (`melee`, `projectile`, `magic`, `fire`, `all`), optionally conditional (`target_health_above`, `shot_distance_over/under`, `health_below`, …). |
| `trigger` | On `on_crit`, `on_dodge`, `on_block`, `on_kill`, `on_projectile_kill`, `on_hurt`, `on_low_health`, `on_potion`, `on_full_charge_hit`, `on_block_break`: heal, give the player a mob effect or give the target one, with a persisted per-effect cooldown. |

### Units and bases

- Percent attributes (`light_weapon_damage`, `projectile_damage`, …) are fractions summed into one multiplier
  applied to the damage amount after vanilla weapon, enchantment and vanilla-critical calculation and before armor:
  `final = incoming × (1 + Σ bonuses)`. "+10% damage" therefore means 10% of that documented base.
- `crit_chance` and `dodge_chance` are probabilities; minors add **percentage points** (5% → 8% is +3 pp).
- `add_multiplied_base` vanilla bonuses are percentages of the attribute's base value (e.g. +1% movement speed of
  0.1 base).

### Critical hits

The mod's critical hit rolls `crit_chance` on player melee hits that are **not already vanilla (jump) critical
hits**, multiplying damage by `1 + crit_damage` (base 0.5 → ×1.5). The two never stack multiplicatively; both kinds
count as critical hits for `on_crit` triggers.

### Lifesteal

Heals `lifesteal × damage actually dealt` (after armor; `LivingDamageEvent.Post`, so invulnerable or fully blocked
targets heal nothing), melee only, at most once per `lifestealCooldownTicks` (default 10).

### Dodge

A dodge cancels an incoming hit from a living attacker or projectile (never environmental or
invulnerability-bypassing damage) with probability `dodge_chance`, plays a sound and fires `on_dodge`.

### Recursion safety

Trigger actions only heal or apply mob effects; nothing the tree does deals damage, so no effect can re-trigger
itself through damage events.

## Effects from outside the tree

Birth signs and shared-constellation tiers add their effects to the same compiled build as allocated nodes, so caps,
conditions and triggers treat them exactly like tree effects.

| Birth sign | Bonus | | Shared tier | XP donated | Bonus for everyone |
|---|---|---|---|---|---|
| The Anvil | +1 Armor | | Kindling | 2,000 | +1 Max Health |
| The Archer | +5% Projectile Damage | | Hearth | 8,000 | +3% Mining Speed |
| The Chalice | +8% Healing Received | | Beacon | 20,000 | +2% Movement Speed |
| The Comet | +3% Movement Speed | | Zenith | 50,000 | +5% Healing Received |
| The Ember | +4% Attack Damage | | | | |
| The Fang | +8% Low-Health Damage | | | | |
| The Gale | +3% Attack Speed | | | | |
| The Lantern | +8% Mining Speed | | | | |
| The Mirror | +8% Magic Resistance | | | | |
| The Needle | +3 pp Critical Chance | | | | |
| The Oak | +2 Max Health | | | | |
| The Phial | +6% Potion Duration | | | | |

The sign is `world day mod 12` over the sign ids in alphabetical order, assigned once.

**Black hole** (`neighbour_multiplier`): attribute, conditional-attribute and damage-modifier amounts of adjacent
allocated nodes are multiplied; triggers are not. Adjacent nodes that are not allocated are rejected with `consumed`
while the black hole is held. **Hidden** (`hidden_until`): the node cannot be allocated, routed through or seen until
the advancement is earned; skills already learned are never removed for being hidden later.

## Caps (server config `[caps]`; they limit only this mod's contribution)

| Stat | Cap |
|---|---|
| Critical chance | 50% |
| Dodge chance | 30% |
| Lifesteal | 10% |
| Damage reduction | 40% |
| Magic resistance | 60% |
| Movement-speed multiplier bonus | +50% |

Equipment, potions and other mods' modifiers are never clamped; the cap applies to the summed tree modifier.
