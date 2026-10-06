"""Generates en_us / pt_br effect descriptions directly from effect data, so text can never
drift from what the mod actually applies."""
import re

# attribute id -> (en, pt, display) ; display: flat | hp | pp (percentage points) | pct (fraction shown as %)
ATTRS = {
    "minecraft:generic.max_health": ("Maximum Health", "Vida Máxima", "hp"),
    "minecraft:generic.armor": ("Armor", "Armadura", "flat"),
    "minecraft:generic.armor_toughness": ("Armor Toughness", "Resistência da Armadura", "flat"),
    "minecraft:generic.knockback_resistance": ("Knockback Resistance", "Resistência a Repulsão", "flat"),
    "minecraft:generic.movement_speed": ("Movement Speed", "Velocidade de Movimento", "flat"),
    "minecraft:generic.attack_damage": ("Attack Damage", "Dano de Ataque", "flat"),
    "minecraft:generic.attack_speed": ("Attack Speed", "Velocidade de Ataque", "flat"),
    "minecraft:generic.attack_knockback": ("Attack Knockback", "Repulsão de Ataque", "flat"),
    "minecraft:generic.luck": ("Luck", "Sorte", "flat"),
    "minecraft:generic.safe_fall_distance": ("Safe Fall Distance (blocks)", "Distância de Queda Segura (blocos)", "flat"),
    "minecraft:generic.fall_damage_multiplier": ("Fall Damage", "Dano de Queda", "flat"),
    "minecraft:generic.oxygen_bonus": ("Oxygen Bonus", "Bônus de Oxigênio", "flat"),
    "minecraft:generic.jump_strength": ("Jump Strength", "Força do Pulo", "flat"),
    "minecraft:generic.step_height": ("Step Height (blocks)", "Altura de Passo (blocos)", "flat"),
    "minecraft:player.block_interaction_range": ("Block Reach (blocks)", "Alcance de Blocos (blocos)", "flat"),
    "minecraft:player.entity_interaction_range": ("Attack Reach (blocks)", "Alcance de Ataque (blocos)", "flat"),
    "minecraft:player.submerged_mining_speed": ("Underwater Mining Speed", "Velocidade de Mineração Submersa", "flat"),
    "minecraft:player.sweeping_damage_ratio": ("Sweeping Damage Ratio", "Proporção de Dano de Varredura", "flat"),
    "nostalgiaskills:crit_chance": ("Critical Chance", "Chance de Crítico", "pp"),
    "nostalgiaskills:crit_damage": ("Critical Damage", "Dano Crítico", "pct"),
    "nostalgiaskills:dodge_chance": ("Dodge Chance", "Chance de Esquiva", "pp"),
    "nostalgiaskills:light_weapon_damage": ("Light Weapon Damage", "Dano com Armas Leves", "pct"),
    "nostalgiaskills:heavy_weapon_damage": ("Heavy Weapon Damage", "Dano com Armas Pesadas", "pct"),
    "nostalgiaskills:projectile_damage": ("Projectile Damage", "Dano de Projéteis", "pct"),
    "nostalgiaskills:long_range_damage": ("Long-Range Projectile Damage (over 12 blocks)", "Dano de Projéteis a Longa Distância (mais de 12 blocos)", "pct"),
    "nostalgiaskills:crossbow_damage": ("Crossbow Damage", "Dano de Besta", "pct"),
    "nostalgiaskills:charged_shot_damage": ("Fully Charged Arrow Damage", "Dano de Flecha Totalmente Carregada", "pct"),
    "nostalgiaskills:mining_speed": ("Mining Speed with Eligible Tools", "Velocidade de Mineração com Ferramentas Elegíveis", "pct"),
    "nostalgiaskills:tool_preservation": ("Tool Durability Preservation Chance", "Chance de Preservar Durabilidade", "pp"),
    "nostalgiaskills:potion_duration": ("Duration of Beneficial Drunk Potions", "Duração de Poções Benéficas Bebidas", "pct"),
    "nostalgiaskills:magic_resistance": ("Magic Damage Reduction", "Redução de Dano Mágico", "pct"),
    "nostalgiaskills:healing_received": ("Healing Received", "Cura Recebida", "pct"),
    "nostalgiaskills:lifesteal": ("Melee Lifesteal", "Roubo de Vida Corpo a Corpo", "pct"),
    "nostalgiaskills:damage_reduction": ("Damage Reduction", "Redução de Dano", "pct"),
    "nostalgiaskills:low_health_damage": ("Melee Damage below 50% Health", "Dano Corpo a Corpo abaixo de 50% de Vida", "pct"),
}

TAG_NAMES = {
    "nostalgiaskills:light_weapons": ("a light weapon", "uma arma leve"),
    "nostalgiaskills:heavy_weapons": ("a heavy weapon", "uma arma pesada"),
    "nostalgiaskills:bows": ("a bow", "um arco"),
    "nostalgiaskills:crossbows": ("a crossbow", "uma besta"),
}

SCOPES = {
    "melee": ("melee damage", "dano corpo a corpo"), "projectile": ("projectile damage", "dano de projéteis"),
    "magic": ("magic damage", "dano mágico"), "fire": ("fire damage", "dano de fogo"), "all": ("damage", "dano"),
}

TRIGGERS = {
    "on_crit": ("After landing a critical hit", "Após acertar um golpe crítico"),
    "on_dodge": ("After dodging an attack", "Após esquivar de um ataque"),
    "on_block": ("After blocking an attack with a shield", "Após bloquear um ataque com escudo"),
    "on_kill": ("After killing a creature", "Após matar uma criatura"),
    "on_projectile_kill": ("After killing a creature with a projectile", "Após matar uma criatura com um projétil"),
    "on_hurt": ("After taking damage", "Após sofrer dano"),
    "on_low_health": ("When your health falls below 30%", "Quando sua vida cai abaixo de 30%"),
    "on_potion": ("After drinking a beneficial potion", "Após beber uma poção benéfica"),
    "on_full_charge_hit": ("After hitting with a fully charged arrow", "Após acertar uma flecha totalmente carregada"),
    "on_block_break": ("After mining a block with an eligible tool", "Após minerar um bloco com uma ferramenta elegível"),
}

EFFECTS = {
    "minecraft:strength": ("Strength", "Força"), "minecraft:speed": ("Speed", "Velocidade"),
    "minecraft:resistance": ("Resistance", "Resistência"), "minecraft:regeneration": ("Regeneration", "Regeneração"),
    "minecraft:absorption": ("Absorption", "Absorção"), "minecraft:haste": ("Haste", "Pressa"),
    "minecraft:weakness": ("Weakness", "Fraqueza"),
}

ROMAN = ["I", "II", "III", "IV", "V"]


def _num(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return s


def _signed(v, suffix=""):
    return ("+" if v >= 0 else "-") + _num(abs(v)) + suffix


def _secs(ticks):
    return _num(ticks / 20)


def condition_text(c, lang):
    en = lang == "en"
    t = c["type"]
    if t == "holding":
        n = TAG_NAMES[c["tag"]][0 if en else 1]
        return f"while holding {n}" if en else f"enquanto segura {n}"
    if t == "sprinting":
        return "while sprinting" if en else "enquanto corre"
    if t == "sneaking":
        return "while sneaking" if en else "enquanto agachado"
    if t == "blocking":
        return "while blocking with a shield" if en else "enquanto bloqueia com escudo"
    if t == "below_y":
        return f"while below Y={c['y']}" if en else f"enquanto abaixo de Y={c['y']}"
    if t == "health_below":
        p = _num(c["fraction"] * 100)
        return f"while below {p}% health" if en else f"enquanto abaixo de {p}% de vida"
    if t == "target_health_above":
        p = _num(c["fraction"] * 100)
        return f"against targets above {p}% health" if en else f"contra alvos acima de {p}% de vida"
    if t == "shot_distance_over":
        b = _num(c["blocks"])
        return (f"when the projectile travelled more than {b} blocks from where it was fired" if en else
                f"quando o projétil percorreu mais de {b} blocos desde o disparo")
    if t == "shot_distance_under":
        b = _num(c["blocks"])
        return (f"when the projectile travelled {b} blocks or less" if en else
                f"quando o projétil percorreu {b} blocos ou menos")
    raise ValueError(t)


def attribute_text(e, lang):
    en = lang == "en"
    if e["attribute"] == "nostalgiaskills:tool_preservation" and e["amount"] < 0:
        p = _num(-e["amount"] * 100)
        return (f"{p}% chance of one extra point of tool wear per mined block" if en else
                f"{p}% de chance de um ponto extra de desgaste da ferramenta por bloco minerado")
    name_en, name_pt, display = ATTRS[e["attribute"]]
    name = name_en if en else name_pt
    amt, op = e["amount"], e["operation"]
    if op == "add_multiplied_base":
        base = " (of base value)" if en else " (do valor base)"
        return f"{_signed(amt * 100, '%')} {name}{base}"
    if op == "add_multiplied_total":
        tot = " (of total value)" if en else " (do valor total)"
        return f"{_signed(amt * 100, '%')} {name}{tot}"
    if display == "hp":
        hearts = abs(amt) / 2
        h = (f" ({_num(hearts)} heart{'s' if hearts != 1 else ''})" if en else
             f" ({_num(hearts)} coraç{'ões' if hearts != 1 else 'ão'})")
        return f"{_signed(amt)} {name}{h}"
    if display == "pp":
        one = abs(amt * 100) == 1
        unit = ("percentage point of" if one else "percentage points of") if en else \
               ("ponto percentual de" if one else "pontos percentuais de")
        return f"{_signed(amt * 100)} {unit} {name}"
    if display == "pct":
        return f"{_signed(amt * 100, '%')} {name}"
    return f"{_signed(amt)} {name}"


def action_text(a, lang):
    en = lang == "en"
    if a["type"] == "heal":
        hp = a["amount"]
        return (f"heal {_num(hp)} health ({_num(hp / 2)} heart{'s' if hp != 2 else ''})" if en else
                f"cura {_num(hp)} de vida ({_num(hp / 2)} coraç{'ões' if hp != 2 else 'ão'})")
    eff = EFFECTS[a["effect"]][0 if en else 1]
    lvl = ROMAN[a["amplifier"]]
    if a["type"] == "self_effect":
        return (f"gain {eff} {lvl} for {_secs(a['duration_ticks'])}s" if en else
                f"ganha {eff} {lvl} por {_secs(a['duration_ticks'])}s")
    return (f"the target receives {eff} {lvl} for {_secs(a['duration_ticks'])}s" if en else
            f"o alvo recebe {eff} {lvl} por {_secs(a['duration_ticks'])}s")


def effect_text(e, lang):
    en = lang == "en"
    t = e["type"]
    if t == "attribute":
        return attribute_text(e, lang)
    if t == "conditional_attribute":
        return f"{attribute_text(e, lang)} {condition_text(e['condition'], lang)}"
    if t == "damage_modifier":
        scope = SCOPES[e["scope"]][0 if en else 1]
        verb = ("dealt" if e["direction"] == "dealt" else "taken") if en else \
               ("causado" if e["direction"] == "dealt" else "recebido")
        txt = f"{_signed(e['amount'] * 100, '%')} {scope} {verb}"
        if "condition" in e:
            txt += " " + condition_text(e["condition"], lang)
        return txt
    if t == "trigger":
        trig = TRIGGERS[e["trigger"]][0 if en else 1]
        cd = _secs(e["cooldown_ticks"])
        return f"{trig}, {action_text(e['action'], lang)} ({'cooldown' if en else 'recarga'} {cd}s)"
    raise ValueError(t)


def describe(effects, lang):
    text = "; ".join(effect_text(e, lang)[0].upper() + effect_text(e, lang)[1:] for e in effects) + "."
    if lang == "pt":
        text = re.sub(r"(\d)\.(\d)", r"\1,\2", text)  # pt_br decimal comma
    return text
