"""Authored gameplay content: minor-node themes, notables, keystones and hub effects.

Every effect here maps to an effect type implemented by the mod (see ARCHITECTURE.md, "Effects"):
  attribute              permanent attribute modifier (vanilla or nostalgiaskills:* attribute)
  conditional_attribute  attribute modifier present only while `condition` holds
  damage_modifier        multiplies damage dealt/taken in a scope while an optional condition holds
  trigger                runs an action (heal / self effect / target effect) on a gameplay trigger, with cooldown
Names are given as (en_us, pt_br).
"""

NS = "nostalgiaskills"
LIGHT = f"{NS}:light_weapons"
HEAVY = f"{NS}:heavy_weapons"
BOWS = f"{NS}:bows"
CROSSBOWS = f"{NS}:crossbows"


def attr(attribute, amount, op="add_value"):
    if ":" not in attribute:
        attribute = f"{NS}:{attribute}"
    return {"type": "attribute", "attribute": attribute, "operation": op, "amount": amount}


def cattr(attribute, amount, condition, op="add_value"):
    e = attr(attribute, amount, op)
    e["type"] = "conditional_attribute"
    e["condition"] = condition
    return e


def dmg(direction, scope, amount, condition=None):
    e = {"type": "damage_modifier", "direction": direction, "scope": scope, "amount": amount}
    if condition:
        e["condition"] = condition
    return e


def trig(trigger, action, cooldown):
    return {"type": "trigger", "trigger": trigger, "action": action, "cooldown_ticks": cooldown}


def heal(amount):
    return {"type": "heal", "amount": amount}


def self_fx(effect, ticks, amp=0):
    return {"type": "self_effect", "effect": f"minecraft:{effect}", "duration_ticks": ticks, "amplifier": amp}


def target_fx(effect, ticks, amp=0):
    return {"type": "target_effect", "effect": f"minecraft:{effect}", "duration_ticks": ticks, "amplifier": amp}


def holding(tag):
    return {"type": "holding", "tag": tag}


def health_below(f):
    return {"type": "health_below", "fraction": f}


MB = "add_multiplied_base"
MAXHP = "minecraft:generic.max_health"
ARMOR = "minecraft:generic.armor"
TOUGH = "minecraft:generic.armor_toughness"
KBR = "minecraft:generic.knockback_resistance"
SPEED = "minecraft:generic.movement_speed"
ATK = "minecraft:generic.attack_damage"
ATKSPD = "minecraft:generic.attack_speed"

# minor themes: key -> (names, effect, tags)
THEMES = {
    # Duelist
    "quick_hands": (("Quick Hands", "Mãos Rápidas"), attr(ATKSPD, 0.02, MB), ["attack_speed"]),
    "light_step": (("Light Step", "Passo Leve"), attr(SPEED, 0.01, MB), ["mobility"]),
    "fencers_poise": (("Fencer's Poise", "Postura do Esgrimista"), attr("light_weapon_damage", 0.03), ["light_weapon", "damage"]),
    "keen_edge": (("Keen Edge", "Fio Afiado"), attr("crit_chance", 0.01), ["critical"]),
    "cutting_remark": (("Cutting Remark", "Comentário Cortante"), attr("crit_damage", 0.05), ["critical"]),
    "nimble": (("Nimble", "Ágil"), attr("dodge_chance", 0.005), ["dodge", "defense"]),
    # Guardian
    "vitality": (("Vitality", "Vitalidade"), attr(MAXHP, 1.0), ["health", "defense"]),
    "plating": (("Plating", "Blindagem"), attr(ARMOR, 0.5), ["armor", "defense"]),
    "tempered": (("Tempered", "Temperado"), attr(TOUGH, 0.5), ["armor", "defense"]),
    "rooted": (("Rooted", "Enraizado"), attr(KBR, 0.02), ["knockback", "defense"]),
    "bulwark_drill": (("Bulwark Drill", "Treino de Baluarte"), attr("damage_reduction", 0.005), ["defense"]),
    # Arcanist / Alchemist
    "distillation": (("Distillation", "Destilação"), attr("potion_duration", 0.04), ["potion"]),
    "warding_salts": (("Warding Salts", "Sais de Proteção"), attr("magic_resistance", 0.02), ["magic", "defense"]),
    "restorative_brew": (("Restorative Brew", "Infusão Restauradora"), attr("healing_received", 0.03), ["healing"]),
    "clear_mind": (("Lucid Mind", "Mente Lúcida"), attr("minecraft:generic.luck", 0.25), ["luck"]),
    # Marksman
    "fletching": (("Fletching", "Empenagem"), attr("projectile_damage", 0.02), ["projectile", "damage"]),
    "steady_draw": (("Steady Draw", "Puxada Firme"), attr("long_range_damage", 0.03), ["projectile", "range"]),
    "hunters_stride": (("Hunter's Stride", "Passada do Caçador"), attr(SPEED, 0.01, MB), ["mobility"]),
    "bolt_smith": (("Bolt Smith", "Ferreiro de Virotes"), attr("crossbow_damage", 0.03), ["crossbow", "damage"]),
    "draw_weight": (("Draw Weight", "Tensão do Arco"), attr("charged_shot_damage", 0.03), ["bow", "damage"]),
    # Artificer / Explorer
    "prospector": (("Prospector", "Garimpeiro"), attr("mining_speed", 0.03), ["mining"]),
    "careful_hands": (("Careful Hands", "Mãos Cuidadosas"), attr("tool_preservation", 0.02), ["tools"]),
    "pathfinder": (("Pathfinder", "Desbravador"), attr(SPEED, 0.01, MB), ["mobility", "exploration"]),
    "sure_footing": (("Sure Footing", "Pisada Firme"), attr("minecraft:generic.safe_fall_distance", 0.5), ["exploration"]),
    "lung_capacity": (("Lung Capacity", "Capacidade Pulmonar"), attr("minecraft:generic.oxygen_bonus", 0.25), ["exploration"]),
    "long_reach": (("Long Reach", "Alcance Longo"), attr("minecraft:player.block_interaction_range", 0.1), ["tools"]),
    # Berserker
    "brute_force": (("Brute Force", "Força Bruta"), attr(ATK, 0.02, MB), ["damage", "melee"]),
    "heavy_swing": (("Heavy Swing", "Golpe Pesado"), attr("heavy_weapon_damage", 0.03), ["heavy_weapon", "damage"]),
    "thick_hide": (("Thick Hide", "Couro Grosso"), attr(MAXHP, 1.0), ["health"]),
    "bloodthirst": (("Bloodthirst", "Sede de Sangue"), attr("lifesteal", 0.005), ["lifesteal"]),
    "fury": (("Fury", "Fúria"), attr("low_health_damage", 0.03), ["damage", "risk"]),
    "momentum": (("Momentum", "Ímpeto"), attr("minecraft:generic.attack_knockback", 0.05), ["melee"]),
}

HUBS = {
    "duelist": (("Duelist's Crest", "Brasão do Duelista"), [attr(ATKSPD, 0.03, MB)]),
    "guardian": (("Guardian's Crest", "Brasão do Guardião"), [attr(MAXHP, 2.0)]),
    "arcanist": (("Alchemist's Crest", "Brasão do Alquimista"), [attr("potion_duration", 0.05)]),
    "marksman": (("Marksman's Crest", "Brasão do Atirador"), [attr("projectile_damage", 0.03)]),
    "artificer": (("Artificer's Crest", "Brasão do Artífice"), [attr("mining_speed", 0.05)]),
    "berserker": (("Berserker's Crest", "Brasão do Berserker"), [attr(ATK, 0.03, MB)]),
}

SECTOR_NAMES = {
    "duelist": ("Duelist", "Duelista"), "guardian": ("Guardian", "Guardião"),
    "arcanist": ("Arcanist / Alchemist", "Arcanista / Alquimista"), "marksman": ("Marksman", "Atirador"),
    "artificer": ("Artificer / Explorer", "Artífice / Explorador"), "berserker": ("Berserker", "Berserker"),
}

# notables and keystones: slug -> (names, effects, tags)
SPECIALS = {
    # ---------------- Duelist ----------------
    "flowing_strikes": (("Flowing Strikes", "Golpes Fluidos"), [cattr(ATKSPD, 0.06, holding(LIGHT), MB)], ["light_weapon"]),
    "quickstep": (("Quickstep", "Passo Ligeiro"), [cattr(SPEED, 0.05, {"type": "sprinting"}, MB)], ["mobility"]),
    "parry_and_return": (("Parry and Return", "Aparar e Revidar"), [trig("on_dodge", self_fx("strength", 60), 160)], ["dodge"]),
    "opportunist": (("Opportunist", "Oportunista"), [dmg("dealt", "melee", 0.15, {"type": "target_health_above", "fraction": 0.9})], ["damage"]),
    "fleet_footwork": (("Fleet Footwork", "Pés Velozes"), [trig("on_dodge", self_fx("speed", 60, 1), 160)], ["dodge", "mobility"]),
    "blade_dance": (("Blade Dance", "Dança das Lâminas"), [trig("on_crit", self_fx("speed", 40), 100)], ["critical", "mobility"]),
    "sidestep": (("Sidestep", "Esquiva Lateral"), [attr("dodge_chance", 0.025)], ["dodge"]),
    "riposte_rhythm": (("Riposte Rhythm", "Ritmo da Resposta"), [trig("on_dodge", heal(2.0), 100)], ["dodge", "healing"]),
    "measured_cut": (("Measured Cut", "Corte Medido"), [attr("crit_chance", 0.03), attr("crit_damage", 0.10)], ["critical"]),
    "disarming_touch": (("Disarming Touch", "Toque Desarmante"), [trig("on_crit", target_fx("weakness", 60), 100)], ["critical"]),
    "lunge_mastery": (("Lunge Mastery", "Maestria da Estocada"), [attr("minecraft:player.entity_interaction_range", 0.5)], ["melee"]),
    "en_garde": (("En Garde", "Em Guarda"), [trig("on_crit", heal(1.0), 40)], ["critical", "healing"]),
    "swift_hands": (("Swift Hands", "Mãos Ligeiras"), [cattr(ATKSPD, 0.15, holding(LIGHT), MB), dmg("dealt", "melee", -0.08, holding(LIGHT))], ["keystone", "light_weapon"]),
    "thousand_cuts": (("Thousand Cuts", "Mil Cortes"), [attr("crit_chance", 0.10), attr("crit_damage", -0.25)], ["keystone", "critical"]),
    "wind_dancer": (("Wind Dancer", "Dançarino do Vento"), [attr("dodge_chance", 0.08), attr(MAXHP, -4.0)], ["keystone", "dodge"]),
    # ---------------- Guardian ----------------
    "gatekeeper": (("Gatekeeper", "Guardião do Portão"), [attr(MAXHP, 4.0)], ["health"]),
    "inner_sanctum": (("Inner Sanctum", "Santuário Interno"), [attr(ARMOR, 2.0), attr(TOUGH, 1.0)], ["armor"]),
    "unbroken": (("Unbroken", "Inquebrável"), [attr(KBR, 0.1), trig("on_low_health", self_fx("resistance", 100, 1), 1200)], ["defense"]),
    "iron_will": (("Iron Will", "Vontade de Ferro"), [attr(MAXHP, 0.05, MB)], ["health"]),
    "shield_wall": (("Shield Wall", "Muralha de Escudos"), [trig("on_block", self_fx("resistance", 60), 200)], ["shield"]),
    "stalwart": (("Stalwart", "Inabalável"), [attr("damage_reduction", 0.03)], ["defense"]),
    "watchtower": (("Watchtower", "Torre de Vigia"), [trig("on_block", heal(2.0), 60)], ["shield", "healing"]),
    "oathbound": (("Oathbound", "Juramentado"), [cattr(ARMOR, 3.0, {"type": "sneaking"})], ["armor"]),
    "warden": (("Warden", "Sentinela"), [attr(MAXHP, 2.0), attr(ARMOR, 1.0)], ["health", "armor"]),
    "beacon_keep": (("Beacon Keep", "Fortaleza do Farol"), [trig("on_hurt", self_fx("regeneration", 60), 300)], ["healing"]),
    "shield_drill": (("Shield Drill", "Treino de Escudo"), [cattr(KBR, 0.3, {"type": "blocking"})], ["shield"]),
    "sentinel": (("Sentinel", "Vigia"), [attr(ARMOR, 0.10, MB)], ["armor"]),
    "bastion": (("Bastion", "Bastião"), [attr(MAXHP, 0.20, MB), attr(SPEED, -0.08, MB)], ["keystone", "health"]),
    "living_aegis": (("Living Aegis", "Égide Viva"), [attr("damage_reduction", 0.10), attr(ATK, -0.15, MB)], ["keystone", "defense"]),
    "rooted_stance": (("Rooted Stance", "Postura Enraizada"), [attr(KBR, 0.4), attr(ARMOR, 4.0), attr("minecraft:generic.jump_strength", -0.15, MB)], ["keystone", "armor"]),
    # ---------------- Arcanist / Alchemist ----------------
    "steady_hand": (("Steady Hand", "Mão Firme"), [attr("potion_duration", 0.10)], ["potion"]),
    "catalyst": (("Catalyst", "Catalisador"), [trig("on_potion", self_fx("regeneration", 60), 200)], ["potion", "healing"]),
    "transmutation": (("Transmutation", "Transmutação"), [attr("magic_resistance", 0.06), attr("healing_received", 0.05)], ["magic"]),
    "quintessence": (("Quintessence", "Quintessência"), [attr("potion_duration", 0.15)], ["potion"]),
    "philosophers_eye": (("Philosopher's Eye", "Olho do Filósofo"), [attr("minecraft:generic.luck", 1.0), attr("healing_received", 0.05)], ["luck"]),
    "sympathetic_link": (("Sympathetic Link", "Vínculo Simpático"), [trig("on_potion", self_fx("speed", 100), 200)], ["potion", "mobility"]),
    "resonance": (("Resonance", "Ressonância"), [attr("magic_resistance", 0.08)], ["magic"]),
    "herbalism": (("Herbalism", "Herbalismo"), [attr("healing_received", 0.10)], ["healing"]),
    "distillers_patience": (("Distiller's Patience", "Paciência do Destilador"), [trig("on_potion", self_fx("absorption", 200), 600)], ["potion"]),
    "panacea": (("Panacea", "Panaceia"), [dmg("taken", "fire", -0.25)], ["fire", "defense"]),
    "warded_path": (("Warded Path", "Caminho Protegido"), [dmg("taken", "magic", -0.10)], ["magic", "defense"]),
    "tincture_mastery": (("Tincture Mastery", "Maestria das Tinturas"), [attr("potion_duration", 0.12), trig("on_potion", heal(1.0), 100)], ["potion"]),
    "volatile_concentrate": (("Volatile Concentrate", "Concentrado Volátil"), [attr("potion_duration", 0.25), dmg("taken", "magic", 0.15)], ["keystone", "potion"]),
    "elixir_of_ages": (("Elixir of Ages", "Elixir das Eras"), [attr("healing_received", 0.30), attr(MAXHP, -0.10, MB)], ["keystone", "healing"]),
    "arcane_ward": (("Arcane Ward", "Égide Arcana"), [attr("magic_resistance", 0.25), attr(ATK, -0.10, MB)], ["keystone", "magic"]),
    # ---------------- Marksman ----------------
    "nocking_point": (("Nocking Point", "Ponto de Encaixe"), [attr("projectile_damage", 0.06)], ["projectile"]),
    "upper_limb": (("Upper Limb", "Lâmina Superior"), [attr("charged_shot_damage", 0.08)], ["bow"]),
    "lower_limb": (("Lower Limb", "Lâmina Inferior"), [attr("crossbow_damage", 0.08)], ["crossbow"]),
    "taut_string": (("Taut String", "Corda Tensa"), [trig("on_full_charge_hit", self_fx("speed", 40), 100)], ["bow", "mobility"]),
    "crossbow_drill": (("Crossbow Drill", "Treino de Besta"), [attr("crossbow_damage", 0.10)], ["crossbow"]),
    "eagle_eye": (("Eagle Eye", "Olho de Águia"), [attr("long_range_damage", 0.10)], ["range"]),
    "long_shot": (("Long Shot", "Tiro Longo"), [attr("long_range_damage", 0.06), attr("projectile_damage", 0.03)], ["range"]),
    "full_quiver": (("Full Quiver", "Aljava Cheia"), [trig("on_projectile_kill", heal(2.0), 100)], ["projectile", "healing"]),
    "hunters_blind": (("Hunter's Blind", "Esconderijo do Caçador"), [dmg("dealt", "projectile", 0.10, {"type": "sneaking"})], ["projectile"]),
    "bolt_workshop": (("Bolt Workshop", "Oficina de Virotes"), [attr("crossbow_damage", 0.05), attr("projectile_damage", 0.05)], ["crossbow"]),
    "windreader": (("Windreader", "Leitor do Vento"), [cattr(SPEED, 0.05, holding(BOWS), MB)], ["bow", "mobility"]),
    "rangefinder": (("Rangefinder", "Telêmetro"), [attr("long_range_damage", 0.12)], ["range"]),
    "patient_aim": (("Patient Aim", "Mira Paciente"), [dmg("dealt", "projectile", 0.20, {"type": "shot_distance_over", "blocks": 12.0}), dmg("dealt", "projectile", -0.10, {"type": "shot_distance_under", "blocks": 12.0})], ["keystone", "range"]),
    "volley_doctrine": (("Volley Doctrine", "Doutrina da Saraivada"), [attr("projectile_damage", 0.15), dmg("dealt", "melee", -0.10)], ["keystone", "projectile"]),
    "heavy_bolts": (("Heavy Bolts", "Virotes Pesados"), [attr("crossbow_damage", 0.25), cattr(SPEED, -0.10, holding(CROSSBOWS), MB)], ["keystone", "crossbow"]),
    # ---------------- Artificer / Explorer ----------------
    "deep_breath": (("Deep Breath", "Fôlego Profundo"), [attr("minecraft:generic.oxygen_bonus", 1.0)], ["exploration"]),
    "junction_lamp": (("Junction Lamp", "Lampião do Entroncamento"), [trig("on_block_break", self_fx("haste", 60), 200)], ["mining"]),
    "tool_care": (("Tool Care", "Cuidado com Ferramentas"), [attr("tool_preservation", 0.08)], ["tools"]),
    "rich_seams": (("Rich Seams", "Veios Ricos"), [attr("mining_speed", 0.10)], ["mining"]),
    "foremans_post": (("Foreman's Post", "Posto do Capataz"), [attr("mining_speed", 0.08), attr("tool_preservation", 0.04)], ["mining", "tools"]),
    "trailblazer": (("Trailblazer", "Pioneiro"), [attr(SPEED, 0.05, MB)], ["mobility", "exploration"]),
    "reinforced_handles": (("Reinforced Handles", "Cabos Reforçados"), [attr("tool_preservation", 0.10)], ["tools"]),
    "lantern_post": (("Lantern Post", "Poste de Lanterna"), [cattr("mining_speed", 0.15, {"type": "below_y", "y": 0})], ["mining"]),
    "shoring": (("Shoring", "Escoramento"), [attr("minecraft:generic.safe_fall_distance", 2.0), attr("minecraft:generic.fall_damage_multiplier", -0.10, MB)], ["exploration"]),
    "fresh_air": (("Fresh Air", "Ar Fresco"), [attr("minecraft:player.submerged_mining_speed", 0.3)], ["mining"]),
    "tinkerer": (("Tinkerer", "Engenhoqueiro"), [attr("minecraft:player.block_interaction_range", 0.5)], ["tools"]),
    "prospectors_camp": (("Prospector's Camp", "Acampamento do Garimpeiro"), [attr("minecraft:generic.step_height", 0.4)], ["exploration"]),
    "master_excavator": (("Master Excavator", "Mestre Escavador"), [attr("mining_speed", 0.20), attr("tool_preservation", -0.15)], ["keystone", "mining"]),
    "tinkers_bargain": (("Tinker's Bargain", "Barganha do Funileiro"), [attr("tool_preservation", 0.25), attr("mining_speed", -0.10)], ["keystone", "tools"]),
    "wayfinder": (("Wayfinder", "Desbravador de Rotas"), [attr(SPEED, 0.10, MB), attr("minecraft:generic.safe_fall_distance", 3.0), attr(MAXHP, -4.0)], ["keystone", "exploration"]),
    # ---------------- Berserker ----------------
    "battle_cry": (("Battle Cry", "Grito de Guerra"), [trig("on_kill", self_fx("strength", 100), 300)], ["melee"]),
    "skull_splitter": (("Skull Splitter", "Racha-Crânios"), [attr("heavy_weapon_damage", 0.10)], ["heavy_weapon"]),
    "berserk": (("Berserk", "Frenesi Berserker"), [dmg("dealt", "melee", 0.15, health_below(0.5))], ["risk", "damage"]),
    "scarred_hide": (("Scarred Hide", "Couro Marcado"), [attr(MAXHP, 4.0)], ["health"]),
    "cleaving_blows": (("Cleaving Blows", "Golpes Fendentes"), [attr("minecraft:player.sweeping_damage_ratio", 0.25)], ["melee"]),
    "rising_rage": (("Rising Rage", "Ira Crescente"), [trig("on_hurt", self_fx("strength", 60), 200)], ["risk"]),
    "bloodletting": (("Bloodletting", "Sangria"), [attr("lifesteal", 0.02)], ["lifesteal"]),
    "frenzy": (("Frenzy", "Frenesi"), [trig("on_kill", self_fx("speed", 60, 1), 200)], ["mobility"]),
    "blood_pact": (("Blood Pact", "Pacto de Sangue"), [attr("lifesteal", 0.015), attr("low_health_damage", 0.05)], ["lifesteal", "risk"]),
    "scar_tissue": (("Scar Tissue", "Tecido Cicatricial"), [trig("on_low_health", self_fx("regeneration", 100, 1), 1200)], ["healing"]),
    "warlords_march": (("Warlord's March", "Marcha do Senhor da Guerra"), [attr(ATK, 0.05, MB)], ["damage"]),
    "heavy_handed": (("Heavy Handed", "Mão Pesada"), [cattr(ATKSPD, 0.10, holding(HEAVY), MB)], ["heavy_weapon"]),
    "reckless_fury": (("Reckless Fury", "Fúria Imprudente"), [dmg("dealt", "melee", 0.20), dmg("taken", "all", 0.10)], ["keystone", "risk"]),
    "juggernaut": (("Juggernaut", "Rolo Compressor"), [attr("heavy_weapon_damage", 0.20), attr(KBR, 0.3), attr(ATKSPD, -0.15, MB)], ["keystone", "heavy_weapon"]),
    "death_wish": (("Death Wish", "Desejo de Morte"), [dmg("dealt", "melee", 0.25, health_below(0.3)), attr("healing_received", -0.25)], ["keystone", "risk"]),
}

EXCLUSIVE_GROUP_NAMES = {
    "duelist_style": ("Duelist Style", "Estilo do Duelista"),
    "guardian_vow": ("Guardian Vow", "Voto do Guardião"),
    "arcanist_formula": ("Alchemical Formula", "Fórmula Alquímica"),
    "marksman_discipline": ("Marksman Discipline", "Disciplina do Atirador"),
    "artificer_craft": ("Artificer Craft", "Ofício do Artífice"),
    "berserker_oath": ("Berserker Oath", "Juramento Berserker"),
}
