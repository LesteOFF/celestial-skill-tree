package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.graph.Condition;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
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
import java.util.function.Supplier;

public final class EffectEngine {
    private static final String TREE_KIND = "tree";
    private static final String CONDITIONAL_KIND = "cond";
    private static final Map<UUID, CompiledBuild> BUILDS = new HashMap<>();
    private static final Map<UUID, Set<String>> ACTIVE_CONDITIONALS = new HashMap<>();
    private static final Map<String, Supplier<Double>> ADDITIVE_CAPS = Map.of(
            "nostalgiaskills:crit_chance", ServerConfig.CAP_CRIT_CHANCE,
            "nostalgiaskills:dodge_chance", ServerConfig.CAP_DODGE_CHANCE,
            "nostalgiaskills:lifesteal", ServerConfig.CAP_LIFESTEAL,
            "nostalgiaskills:damage_reduction", ServerConfig.CAP_DAMAGE_REDUCTION,
            "nostalgiaskills:magic_resistance", ServerConfig.CAP_MAGIC_RESISTANCE);

    public record DamageContext(LivingEntity target, double shotDistance) {
        boolean hasShot() {
            return shotDistance >= 0;
        }
    }

    private EffectEngine() {
    }

    public static CompiledBuild build(Player player) {
        return BUILDS.getOrDefault(player.getUUID(), CompiledBuild.EMPTY);
    }

    public static void forget(Player player) {
        BUILDS.remove(player.getUUID());
        ACTIVE_CONDITIONALS.remove(player.getUUID());
    }

    public static ResourceLocation modifierId(String kind, String key) {
        String path = kind + "/" + key.replace(':', '.').replace('|', '.').replace('#', '.');
        return ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, path);
    }

    public static void refresh(ServerPlayer player) {
        CompiledBuild build = SkillTreeManager.graph()
                .map(graph -> compile(graph, player)).orElse(CompiledBuild.EMPTY);
        BUILDS.put(player.getUUID(), build);
        removeOwnedModifiers(player);
        ACTIVE_CONDITIONALS.remove(player.getUUID());
        build.attributeSums().forEach((sumKey, sum) -> applyPermanent(player, sumKey, sum));
        updateConditionals(player);
        clampHealth(player);
    }

    private static CompiledBuild compile(SkillGraphSnapshot graph, ServerPlayer player) {
        PlayerSkillState state = ModAttachments.state(player);
        long communityXp = WorldSkillData.of(player.server).communityXp();
        return CompiledBuild.compile(graph, state, ExtraEffects.of(state, communityXp));
    }

    private static void applyPermanent(ServerPlayer player, String sumKey, double sum) {
        String[] attributeAndOperation = sumKey.split("\\|");
        double amount = capped(attributeAndOperation[0], attributeAndOperation[1], sum);
        if (amount == 0) {
            return;
        }
        instance(player, attributeAndOperation[0]).ifPresent(instance -> instance.addTransientModifier(
                new AttributeModifier(modifierId(TREE_KIND, sumKey), amount, op(attributeAndOperation[1]))));
    }

    public static double capped(String attribute, String operation, double amount) {
        if (attribute.equals("minecraft:generic.movement_speed") && operation.equals("add_multiplied_base")) {
            return Math.min(amount, ServerConfig.CAP_MOVEMENT_SPEED.get());
        }
        Supplier<Double> cap = ADDITIVE_CAPS.get(attribute);
        return cap == null || !operation.equals("add_value") ? amount : Math.min(amount, cap.get());
    }

    public static void updateConditionals(ServerPlayer player) {
        CompiledBuild build = build(player);
        if (build.conditional().isEmpty()) {
            return;
        }
        Set<String> active = ACTIVE_CONDITIONALS.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        for (CompiledBuild.Keyed<SkillEffect.ConditionalAttribute> keyed : build.conditional()) {
            boolean holds = holds(player, keyed.effect().condition(), null);
            if (holds != active.contains(keyed.key())) {
                instance(player, keyed.effect().attribute())
                        .ifPresent(instance -> toggleConditional(instance, keyed, holds, active));
            }
        }
        clampHealth(player);
    }

    private static void toggleConditional(AttributeInstance instance, CompiledBuild.Keyed<SkillEffect.ConditionalAttribute> keyed,
                                          boolean enable, Set<String> active) {
        ResourceLocation id = modifierId(CONDITIONAL_KIND, keyed.key());
        if (enable) {
            SkillEffect.ConditionalAttribute effect = keyed.effect();
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, effect.amount(), op(effect.operation())));
            active.add(keyed.key());
        } else {
            instance.removeModifier(id);
            active.remove(keyed.key());
        }
    }

    private static void clampHealth(ServerPlayer player) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void removeOwnedModifiers(ServerPlayer player) {
        for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (isOwned(modifier.id())) {
                    instance.removeModifier(modifier.id());
                }
            }
        }
    }

    private static boolean isOwned(ResourceLocation modifierId) {
        return modifierId.getNamespace().equals(NostalgiaSkills.MODID)
                && (modifierId.getPath().startsWith(TREE_KIND + "/") || modifierId.getPath().startsWith(CONDITIONAL_KIND + "/"));
    }

    private static Optional<AttributeInstance> instance(ServerPlayer player, String attributeId) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attributeId)).map(player::getAttribute);
    }

    public static AttributeModifier.Operation op(String operation) {
        return switch (operation) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    public static boolean holds(Player player, Condition condition, DamageContext context) {
        return switch (condition.type()) {
            case "holding" -> player.getMainHandItem().is(ModTags.itemTag(condition.string("tag")));
            case "sprinting" -> player.isSprinting();
            case "sneaking" -> player.isShiftKeyDown();
            case "blocking" -> player.isBlocking();
            case "below_y" -> player.getY() < condition.number("y");
            case "health_below" -> player.getHealth() < player.getMaxHealth() * condition.number("fraction");
            case "target_health_above" -> context != null && context.target() != null
                    && context.target().getHealth() > context.target().getMaxHealth() * condition.number("fraction");
            case "shot_distance_over" -> context != null && context.hasShot() && context.shotDistance() > condition.number("blocks");
            case "shot_distance_under" -> context != null && context.hasShot() && context.shotDistance() <= condition.number("blocks");
            default -> false;
        };
    }
}
