package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.data.WorldSkillData;
import dev.lesteoff.celestialskilltree.graph.Condition;
import dev.lesteoff.celestialskilltree.graph.Ids;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.ModTags;
import dev.lesteoff.celestialskilltree.server.ExtraSign;
import dev.lesteoff.celestialskilltree.server.SkyEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public final class EffectEngine {
    private static final String TREE_KIND = "tree";
    private static final String CONDITIONAL_KIND = "cond";
    private static final Map<UUID, CompiledBuild> BUILDS = new HashMap<>();
    private static final Map<UUID, Set<String>> ACTIVE_CONDITIONALS = new HashMap<>();
    private static final Map<UUID, Set<OwnedModifier>> OWNED = new HashMap<>();

    private record OwnedModifier(Holder<Attribute> attribute, ResourceLocation id) {
    }
    private static final Map<String, Supplier<Double>> ADDITIVE_CAPS = Map.of(
            "celestialskilltree:crit_chance", ServerConfig.CAP_CRIT_CHANCE,
            "celestialskilltree:dodge_chance", ServerConfig.CAP_DODGE_CHANCE,
            "celestialskilltree:lifesteal", ServerConfig.CAP_LIFESTEAL,
            "celestialskilltree:damage_reduction", ServerConfig.CAP_DAMAGE_REDUCTION,
            "celestialskilltree:magic_resistance", ServerConfig.CAP_MAGIC_RESISTANCE);

    private static final Map<String, AttributeTarget> ATTRIBUTE_TARGETS = new ConcurrentHashMap<>();
    private static final Map<String, BiPredicate<Player, Condition>> EXTRA_CONDITIONS = new ConcurrentHashMap<>();

    public interface AttributeTarget {
        void apply(ServerPlayer player, String attribute, AttributeModifier modifier);

        void clear(ServerPlayer player);

        void forget(UUID player);
    }

    public static void registerAttributeTarget(String namespace, AttributeTarget target) {
        ATTRIBUTE_TARGETS.put(namespace, target);
    }

    public static boolean hasAttributeTarget(String attribute) {
        return ATTRIBUTE_TARGETS.containsKey(Ids.namespace(attribute));
    }

    public static void registerCondition(String type, BiPredicate<Player, Condition> test) {
        EXTRA_CONDITIONS.put(type, test);
    }

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
        OWNED.remove(player.getUUID());
        BorrowedNodes.forget(player.getUUID());
        ATTRIBUTE_TARGETS.values().forEach(target -> target.forget(player.getUUID()));
    }

    public static ResourceLocation modifierId(String kind, String key) {
        String path = kind + "/" + key.replace(':', '.').replace('|', '.').replace('#', '.');
        return ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, path);
    }

    public static double attributeValue(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttributes().hasAttribute(attribute) ? entity.getAttributeValue(attribute) : 0.0;
    }

    public static boolean isOwnModifier(ResourceLocation modifierId, String kind) {
        return modifierId.getNamespace().equals(CelestialSkillTree.MODID) && modifierId.getPath().startsWith(kind + "/");
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
        Map<String, List<SkillEffect>> extras = ExtraEffects.of(state, communityXp);
        extras.putAll(BorrowedNodes.effects(graph, player));
        extras.putAll(ExtraSign.effects(player, state));
        return CompiledBuild.compile(graph, state, extras, StolenNodes.of(player));
    }

    private static void applyPermanent(ServerPlayer player, CompiledBuild.AttributeKey key, double sum) {
        double amount = capped(key.attribute(), key.operation(), scaled(sum));
        if (amount == 0) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(modifierId(TREE_KIND, key.id()), amount,
                operationFor(key.attribute(), key.operation()));
        AttributeTarget target = ATTRIBUTE_TARGETS.get(Ids.namespace(key.attribute()));
        if (target != null) {
            target.apply(player, key.attribute(), modifier);
            return;
        }
        holder(key.attribute()).ifPresent(attribute -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.addTransientModifier(modifier);
                owned(player).add(new OwnedModifier(attribute, modifier.id()));
            }
        });
    }

    public static double scaled(double amount) {
        return amount * ServerConfig.ATTRIBUTE_MULTIPLIER.get();
    }

    public static double capped(String attribute, String operation, double amount) {
        if (attribute.equals("minecraft:generic.movement_speed") && operation.equals("add_multiplied_base")) {
            return Math.min(amount, scaledCap(ServerConfig.CAP_MOVEMENT_SPEED));
        }
        Supplier<Double> cap = ADDITIVE_CAPS.get(attribute);
        return cap == null || !operation.equals("add_value") ? amount : Math.min(amount, scaledCap(cap));
    }

    private static double scaledCap(Supplier<Double> cap) {
        return cap.get() * ServerConfig.CAP_MULTIPLIER.get();
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
                holder(keyed.effect().attribute()).ifPresent(attribute -> toggleConditional(player, attribute, keyed, holds, active));
            }
        }
        clampHealth(player);
    }

    private static void toggleConditional(ServerPlayer player, Holder<Attribute> attribute,
                                          CompiledBuild.Keyed<SkillEffect.ConditionalAttribute> keyed, boolean enable, Set<String> active) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        ResourceLocation id = modifierId(CONDITIONAL_KIND, keyed.key());
        if (enable) {
            SkillEffect.ConditionalAttribute effect = keyed.effect();
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, scaled(effect.amount()),
                    operationFor(effect.attribute(), effect.operation())));
            owned(player).add(new OwnedModifier(attribute, id));
            active.add(keyed.key());
        } else {
            instance.removeModifier(id);
            owned(player).remove(new OwnedModifier(attribute, id));
            active.remove(keyed.key());
        }
    }

    private static void clampHealth(ServerPlayer player) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void removeOwnedModifiers(ServerPlayer player) {
        ATTRIBUTE_TARGETS.values().forEach(target -> target.clear(player));
        Set<OwnedModifier> owned = OWNED.remove(player.getUUID());
        if (owned == null) {
            return;
        }
        for (OwnedModifier modifier : owned) {
            AttributeInstance instance = player.getAttribute(modifier.attribute());
            if (instance != null) {
                instance.removeModifier(modifier.id());
            }
        }
    }

    private static Set<OwnedModifier> owned(ServerPlayer player) {
        return OWNED.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
    }

    private static Optional<Holder.Reference<Attribute>> holder(String attributeId) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(AttributeAliases.resolve(attributeId)));
    }

    public static AttributeModifier.Operation operationFor(String attribute, String operation) {
        return op(AttributeAliases.operation(attribute, operation));
    }

    public static AttributeModifier.Operation op(String operation) {
        return switch (operation) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private static boolean wears(Player player, TagKey<Item> tag) {
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean holds(Player player, Condition condition, DamageContext context) {
        return switch (condition.type()) {
            case "holding" -> WeaponClasses.matches(player.getMainHandItem(), condition.string("tag"));
            case "sprinting" -> player.isSprinting();
            case "wet" -> player.isInWaterOrRain();
            case "sneaking" -> player.isShiftKeyDown();
            case "blocking" -> player.isBlocking();
            case "below_y" -> player.getY() < condition.number("y");
            case "health_below" -> player.getHealth() < player.getMaxHealth() * condition.number("fraction");
            case "target_health_above" -> context != null && context.target() != null
                    && context.target().getHealth() > context.target().getMaxHealth() * condition.number("fraction");
            case "target_type" -> context != null && context.target() != null
                    && context.target().getType().is(ModTags.entityTypeTag(condition.string("tag")));
            case "shot_distance_over" -> context != null && context.hasShot() && context.shotDistance() > condition.number("blocks");
            case "shot_distance_under" -> context != null && context.hasShot() && context.shotDistance() <= condition.number("blocks");
            case "in_dimension" -> player.level().dimension().location().toString().equals(condition.string("dimension"));
            case "in_biome_tag" -> player.level().getBiome(player.blockPosition()).is(ModTags.biomeTag(condition.string("tag")));
            case "sky_event" -> player.level().dimension() == Level.OVERWORLD && SkyEvents.current().id().equals(condition.string("event"));
            case "wearing" -> wears(player, ModTags.itemTag(condition.string("tag")));
            case "dual_wielding" -> WeaponClasses.isWeapon(player.getMainHandItem()) && WeaponClasses.isWeapon(player.getOffhandItem());
            case "attribute_at_least" -> BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(condition.string("attribute")))
                    .filter(attribute -> attributeValue(player, attribute) >= condition.number("value")).isPresent();
            default -> EXTRA_CONDITIONS.getOrDefault(condition.type(), (anyPlayer, anyCondition) -> false).test(player, condition);
        };
    }
}
