package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.graph.Condition;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies the confirmed build as transient attribute modifiers rebuilt from the persisted attachment.
 * Strategy: every refresh removes all modifiers this system owns (namespace nostalgiaskills, path "tree/" or
 * "cond/") and re-adds one aggregated modifier per (attribute, operation). Calling it any number of times yields
 * the same result, and nothing is saved with the entity, so bonuses can never duplicate after death, login or
 * reload. Base values and modifiers from equipment or other mods are never touched.
 */
public final class EffectEngine {
    private static final Map<UUID, CompiledBuild> BUILDS = new HashMap<>();
    private static final Map<UUID, Set<String>> ACTIVE_CONDITIONALS = new HashMap<>();
    private static final Map<String, String> CAPPED = Map.of(
            "nostalgiaskills:crit_chance", "crit", "nostalgiaskills:dodge_chance", "dodge",
            "nostalgiaskills:lifesteal", "lifesteal", "nostalgiaskills:damage_reduction", "reduction",
            "nostalgiaskills:magic_resistance", "magic");

    private EffectEngine() {
    }

    public static CompiledBuild build(Player p) {
        return BUILDS.getOrDefault(p.getUUID(), CompiledBuild.EMPTY);
    }

    public static void forget(Player p) {
        BUILDS.remove(p.getUUID());
        ACTIVE_CONDITIONALS.remove(p.getUUID());
    }

    static ResourceLocation modifierId(String kind, String key) {
        String path = kind + "/" + key.replace(':', '.').replace('|', '.').replace('#', '.');
        return ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, path);
    }

    public static void refresh(ServerPlayer p) {
        CompiledBuild cb = SkillTreeManager.graph()
                .map(g -> CompiledBuild.compile(g, ModAttachments.state(p))).orElse(CompiledBuild.EMPTY);
        BUILDS.put(p.getUUID(), cb);
        removeOwned(p, "tree/");
        removeOwned(p, "cond/");
        ACTIVE_CONDITIONALS.remove(p.getUUID());
        for (Map.Entry<String, Double> e : cb.attributeSums().entrySet()) {
            String[] parts = e.getKey().split("\\|");
            double amount = capped(parts[0], parts[1], e.getValue());
            if (amount == 0) {
                continue;
            }
            attribute(parts[0]).ifPresent(h -> {
                AttributeInstance inst = p.getAttribute(h);
                if (inst != null) {
                    inst.addTransientModifier(new AttributeModifier(modifierId("tree", e.getKey()), amount, op(parts[1])));
                }
            });
        }
        updateConditionals(p);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth()); // never leave health above a lowered maximum; never heals
        }
    }

    /** Caps limit only this mod's contribution (the summed modifier), never equipment or other mods. */
    static double capped(String attribute, String op, double amount) {
        if (attribute.equals("minecraft:generic.movement_speed") && op.equals("add_multiplied_base")) {
            return Math.min(amount, ServerConfig.CAP_MOVEMENT_SPEED.get());
        }
        String cap = CAPPED.get(attribute);
        if (cap == null || !op.equals("add_value")) {
            return amount;
        }
        double max = switch (cap) {
            case "crit" -> ServerConfig.CAP_CRIT_CHANCE.get();
            case "dodge" -> ServerConfig.CAP_DODGE_CHANCE.get();
            case "lifesteal" -> ServerConfig.CAP_LIFESTEAL.get();
            case "reduction" -> ServerConfig.CAP_DAMAGE_REDUCTION.get();
            default -> ServerConfig.CAP_MAGIC_RESISTANCE.get();
        };
        return Math.min(amount, max);
    }

    /** Re-evaluates conditional modifiers; cheap and only does work for players whose build has any. */
    public static void updateConditionals(ServerPlayer p) {
        CompiledBuild cb = build(p);
        if (cb.conditional().isEmpty()) {
            return;
        }
        Set<String> active = ACTIVE_CONDITIONALS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        for (CompiledBuild.Keyed<SkillEffect.ConditionalAttribute> k : cb.conditional()) {
            boolean on = holds(p, k.effect().condition(), null);
            boolean was = active.contains(k.key());
            if (on == was) {
                continue;
            }
            Optional<Holder<Attribute>> h = attribute(k.effect().attribute());
            AttributeInstance inst = h.map(p::getAttribute).orElse(null);
            if (inst == null) {
                continue;
            }
            ResourceLocation id = modifierId("cond", k.key());
            if (on) {
                inst.addOrUpdateTransientModifier(new AttributeModifier(id, k.effect().amount(), op(k.effect().operation())));
                active.add(k.key());
            } else {
                inst.removeModifier(id);
                active.remove(k.key());
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    private static void removeOwned(ServerPlayer p, String prefix) {
        for (Holder<Attribute> h : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance inst = p.getAttribute(h);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
                if (m.id().getNamespace().equals(NostalgiaSkills.MODID) && m.id().getPath().startsWith(prefix)) {
                    inst.removeModifier(m.id());
                }
            }
        }
    }

    static Optional<Holder<Attribute>> attribute(String id) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).map(h -> (Holder<Attribute>) h);
    }

    static AttributeModifier.Operation op(String op) {
        return switch (op) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    /**
     * Evaluates a condition for {@code p}. {@code ctx} carries damage context (target, shot distance) when the
     * condition is checked inside a damage event; contextual conditions are false without it.
     */
    public static boolean holds(Player p, Condition c, DamageContext ctx) {
        return switch (c.type()) {
            case "holding" -> p.getMainHandItem().is(ModTags.itemTag(c.string("tag")));
            case "sprinting" -> p.isSprinting();
            case "sneaking" -> p.isShiftKeyDown();
            case "blocking" -> p.isBlocking();
            case "below_y" -> p.getY() < c.number("y");
            case "health_below" -> p.getHealth() < p.getMaxHealth() * c.number("fraction");
            case "target_health_above" -> ctx != null && ctx.target() != null
                    && ctx.target().getHealth() > ctx.target().getMaxHealth() * c.number("fraction");
            case "shot_distance_over" -> ctx != null && ctx.shotDistance() >= 0 && ctx.shotDistance() > c.number("blocks");
            case "shot_distance_under" -> ctx != null && ctx.shotDistance() >= 0 && ctx.shotDistance() <= c.number("blocks");
            default -> false;
        };
    }

    public record DamageContext(LivingEntity target, double shotDistance) {
    }
}
