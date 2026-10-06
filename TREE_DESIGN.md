# Tree Design — Nostalgia Skills

The tree is a 480-node constellation: **6 root hubs + 384 minors + 72 notables + 18 keystones**, 80 nodes per
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
| Purchasable points | 120 (server config `pointLimit`) |
| Cheapest route between adjacent hubs | 19–27 paid points (requirement ≥ 8) |

120 points buys roughly a quarter of the tree: a deep specialisation plus one hybrid excursion, never everything.

## Sectors and identities

| Hub | Sector | Identity | Signature notables | Keystones (exclusive pair marked *) |
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

## Example builds

Generated by `python3 tools/layout/builds.py` from the shipped data using the same cheapest-route rule as the
in-game "Shift-click: add route" action. All fit the 120-point budget; bonuses are the summed permanent attribute
effects; conditional effects and keystone penalties are listed by node.

<!-- BEGIN GENERATED BUILDS (tools/layout/builds.py) -->

### Fencer (Duelist)

- Start: `nostalgiaskills:duelist/hub` (free). Paid points: **53 / 120** (32 minors, 9 notables, 1 keystones).
- Targets in order: `swift_hands`, `flowing_strikes`, `en_garde`, `measured_cut`, `blade_dance`, `parry_and_return`, `fleet_footwork`, `quickstep`, `lunge_mastery`, `opportunist`
- Permanent bonuses: attack_speed +39% (base); movement_speed +7% (base); entity_interaction_range +0.5; crit_chance +6.0 pp; crit_damage +25.0%; light_weapon_damage +3.0%
- Conditional / triggered effects from: `duelist/blade_dance`, `duelist/en_garde`, `duelist/fleet_footwork`, `duelist/flowing_strikes`, `duelist/opportunist`, `duelist/parry_and_return`, `duelist/quickstep`, `duelist/swift_hands`
- Keystones (with penalties): `duelist/swift_hands`

### Bastion Warden (Guardian)

- Start: `nostalgiaskills:guardian/hub` (free). Paid points: **51 / 120** (30 minors, 9 notables, 1 keystones).
- Targets in order: `bastion`, `gatekeeper`, `inner_sanctum`, `iron_will`, `unbroken`, `shield_wall`, `watchtower`, `sentinel`, `warden`, `stalwart`
- Permanent bonuses: armor +10% (base); armor +6; armor_toughness +3; knockback_resistance +0.2; max_health +25% (base); max_health +23; movement_speed -8% (base); damage_reduction +3.0%
- Conditional / triggered effects from: `guardian/shield_wall`, `guardian/unbroken`, `guardian/watchtower`
- Keystones (with penalties): `guardian/bastion`

### Volatile Alchemist (Arcanist)

- Start: `nostalgiaskills:arcanist/hub` (free). Paid points: **51 / 120** (34 minors, 7 notables, 1 keystones).
- Targets in order: `steady_hand`, `catalyst`, `transmutation`, `quintessence`, `philosophers_eye`, `volatile_concentrate`, `herbalism`, `panacea`
- Permanent bonuses: luck +1.5; healing_received +35.0%; magic_resistance +52.0%; potion_duration +71.0%
- Conditional / triggered effects from: `arcanist/catalyst`, `arcanist/panacea`, `arcanist/volatile_concentrate`
- Keystones (with penalties): `arcanist/volatile_concentrate`

### Patient Hunter (Marksman)

- Start: `nostalgiaskills:marksman/hub` (free). Paid points: **46 / 120** (27 minors, 8 notables, 1 keystones).
- Targets in order: `eagle_eye`, `long_shot`, `nocking_point`, `rangefinder`, `upper_limb`, `taut_string`, `patient_aim`, `full_quiver`
- Permanent bonuses: movement_speed +3% (base); charged_shot_damage +23.0%; crossbow_damage +8.0%; long_range_damage +46.0%; projectile_damage +38.0%
- Conditional / triggered effects from: `marksman/full_quiver`, `marksman/patient_aim`, `marksman/taut_string`
- Keystones (with penalties): `marksman/patient_aim`

### Deep Delver (Artificer)

- Start: `nostalgiaskills:artificer/hub` (free). Paid points: **36 / 120** (19 minors, 7 notables, 1 keystones).
- Targets in order: `deep_breath`, `junction_lamp`, `rich_seams`, `foremans_post`, `lantern_post`, `tool_care`, `master_excavator`, `shoring`
- Permanent bonuses: fall_damage_multiplier -10% (base); movement_speed +2% (base); oxygen_bonus +1; safe_fall_distance +3; mining_speed +82.0%; tool_preservation +1.0 pp
- Conditional / triggered effects from: `artificer/junction_lamp`, `artificer/lantern_post`
- Keystones (with penalties): `artificer/master_excavator`

### Reckless Fury (Berserker)

- Start: `nostalgiaskills:berserker/hub` (free). Paid points: **42 / 120** (25 minors, 7 notables, 1 keystones).
- Targets in order: `battle_cry`, `skull_splitter`, `berserk`, `rising_rage`, `bloodletting`, `blood_pact`, `reckless_fury`, `frenzy`
- Permanent bonuses: attack_damage +35% (base); attack_knockback +0.15; heavy_weapon_damage +10.0%; lifesteal +4.5%; low_health_damage +17.0%
- Conditional / triggered effects from: `berserker/battle_cry`, `berserker/berserk`, `berserker/frenzy`, `berserker/reckless_fury`, `berserker/rising_rage`
- Keystones (with penalties): `berserker/reckless_fury`

### Raider (Berserker -> Duelist hybrid)

- Start: `nostalgiaskills:berserker/hub` (free). Paid points: **40 / 120** (27 minors, 6 notables, 0 keystones, 1 secondary hub(s) at 1 point: `nostalgiaskills:duelist/hub`).
- Targets in order: `battle_cry`, `scarred_hide`, `warlords_march`, `duelist/hub`, `duelist/sidestep`, `duelist/riposte_rhythm`, `duelist/disarming_touch`
- Permanent bonuses: attack_damage +12% (base); attack_knockback +0.25; attack_speed +3% (base); max_health +12; dodge_chance +3.5 pp; light_weapon_damage +30.0%
- Conditional / triggered effects from: `berserker/battle_cry`, `duelist/disarming_touch`, `duelist/riposte_rhythm`
- Keystones (with penalties): none

### Ranger (Marksman -> Artificer hybrid)

- Start: `nostalgiaskills:marksman/hub` (free). Paid points: **51 / 120** (34 minors, 7 notables, 1 keystones).
- Targets in order: `taut_string`, `windreader`, `artificer/trailblazer`, `artificer/prospectors_camp`, `artificer/wayfinder`, `crossbow_drill`
- Permanent bonuses: max_health -4; movement_speed +30% (base); safe_fall_distance +7.5; step_height +0.4; crossbow_damage +19.0%; long_range_damage +21.0%; mining_speed +8.0%; projectile_damage +3.0%; tool_preservation +14.0 pp
- Conditional / triggered effects from: `marksman/taut_string`, `marksman/windreader`
- Keystones (with penalties): `artificer/wayfinder`

<!-- END GENERATED BUILDS -->
