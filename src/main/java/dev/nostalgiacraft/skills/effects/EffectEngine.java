package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.graph.Condition;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModTags;
import dev.nostalgiacraft.skills.server.SkyEvents;
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
    private static final Map<String, Supplier<Double>> ADDITIVE_CAPS = Map.of(
            "nostalgiaskills:crit_chance", ServerConfig.CAP_CRIT_CHANCE,
            "nostalgiaskills:dodge_chance", ServerConfig.CAP_DODGE_CHANCE,
            "nostalgiaskills:lifesteal", ServerConfig.CAP_LIFESTEAL,
            "nostalgiaskills:damage_reduction", ServerConfig.CAP_DAMAGE_REDUCTION,
            "nostalgiaskills:magic_resistance", ServerConfig.CAP_MAGIC_RESISTANCE);

    private static final Map<String, AttributeTarget> ATTRIBUTE_TARGETS = new ConcurrentHashMap<>();
    private static final Map<String, BiPredicate<Player, Condition>> EXTRA_CONDITIONS = new ConcurrentHashMap<>();

    public interface AttributeTarget {
        void apply(ServerPlayer player, String attribute, AttributeModifier modifier);

        void clear(ServerPlayer player);
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
        double amount = capped(attributeAndOperation[0], attributeAndOperation[1], scaled(sum));
        if (amount == 0) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(modifierId(TREE_KIND, sumKey), amount,
                operationFor(attributeAndOperation[0], attributeAndOperation[1]));
        AttributeTarget target = ATTRIBUTE_TARGETS.get(Ids.namespace(attributeAndOperation[0]));
        if (target != null) {
            target.apply(player, attributeAndOperation[0], modifier);
            return;
        }
        instance(player, attributeAndOperation[0]).ifPresent(instance -> instance.addTransientModifier(modifier));
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
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, scaled(effect.amount()),
                    operationFor(effect.attribute(), effect.operation())));
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
        ATTRIBUTE_TARGETS.values().forEach(target -> target.clear(player));
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
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(AttributeAliases.resolve(attributeId)))
                .map(player::getAttribute);
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
            case "in_dimension" -> player.level().dimension().location().toString().equals(condition.string("dimension"));
            case "in_biome_tag" -> player.level().getBiome(player.blockPosition()).is(ModTags.biomeTag(condition.string("tag")));
            case "sky_event" -> SkyEvents.current().id().equals(condition.string("event"));
            case "wearing" -> wears(player, ModTags.itemTag(condition.string("tag")));
            case "dual_wielding" -> isWeapon(player.getMainHandItem()) && isWeapon(player.getOffhandItem());
            case "attribute_at_least" -> BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(condition.string("attribute")))
                    .filter(attribute -> GameplayEvents.attributeValue(player, attribute) >= condition.number("value")).isPresent();
            default -> EXTRA_CONDITIONS.getOrDefault(condition.type(), (anyPlayer, anyCondition) -> false).test(player, condition);
        };
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ModTags.itemTag(ServerConfig.LIGHT_WEAPON_TAG.get())) || stack.is(ModTags.itemTag(ServerConfig.HEAVY_WEAPON_TAG.get()));
    }
}
