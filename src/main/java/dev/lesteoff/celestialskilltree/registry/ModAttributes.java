package dev.lesteoff.celestialskilltree.registry;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, CelestialSkillTree.MODID);
    public static final Map<String, DeferredHolder<Attribute, Attribute>> ALL = new LinkedHashMap<>();

    public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = reg("crit_chance", 0.0, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> CRIT_DAMAGE = reg("crit_damage", 0.5, 0.0, 5.0);
    public static final DeferredHolder<Attribute, Attribute> DODGE_CHANCE = reg("dodge_chance", 0.0, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> LIGHT_WEAPON_DAMAGE = reg("light_weapon_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> HEAVY_WEAPON_DAMAGE = reg("heavy_weapon_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE = reg("projectile_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> LONG_RANGE_DAMAGE = reg("long_range_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> CROSSBOW_DAMAGE = reg("crossbow_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> CHARGED_SHOT_DAMAGE = reg("charged_shot_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> MINING_SPEED = reg("mining_speed", 0.0, -0.9, 10.0);
    public static final DeferredHolder<Attribute, Attribute> TOOL_PRESERVATION = reg("tool_preservation", 0.0, -1.0, 0.9);
    public static final DeferredHolder<Attribute, Attribute> POTION_DURATION = reg("potion_duration", 0.0, -0.9, 5.0);
    public static final DeferredHolder<Attribute, Attribute> MAGIC_RESISTANCE = reg("magic_resistance", 0.0, -1.0, 0.9);
    public static final DeferredHolder<Attribute, Attribute> HEALING_RECEIVED = reg("healing_received", 0.0, -0.9, 5.0);
    public static final DeferredHolder<Attribute, Attribute> LIFESTEAL = reg("lifesteal", 0.0, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_REDUCTION = reg("damage_reduction", 0.0, -1.0, 0.9);
    public static final DeferredHolder<Attribute, Attribute> LOW_HEALTH_DAMAGE = reg("low_health_damage", 0.0, -1.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> SPELL_ECHO_CHANCE = reg("spell_echo_chance", 0.0, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> HARVEST_BONUS = reg("harvest_bonus", 0.0, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> BORROWED_KEYSTONES = reg("borrowed_keystones", 0.0, 0.0, 16.0);

    private ModAttributes() {
    }

    private static DeferredHolder<Attribute, Attribute> reg(String name, double def, double min, double max) {
        DeferredHolder<Attribute, Attribute> h = ATTRIBUTES.register(name,
                () -> new RangedAttribute("attribute.name." + CelestialSkillTree.MODID + "." + name, def, min, max).setSyncable(true));
        ALL.put(name, h);
        return h;
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(ModAttributes::addToPlayers);
    }

    private static void addToPlayers(EntityAttributeModificationEvent event) {
        for (Holder<Attribute> h : ALL.values()) {
            if (!event.has(EntityType.PLAYER, h)) {
                event.add(EntityType.PLAYER, h);
            }
        }
    }
}
