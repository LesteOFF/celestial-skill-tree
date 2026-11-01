package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.effects.AttributeAliases;
import dev.lesteoff.celestialskilltree.effects.CompiledBuild;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.NodeType;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

public final class StatsPanel {
    private static final double BLOCKS_PER_SECOND_PER_SPEED = 43.17;
    private static final double NEGLIGIBLE = 1e-9;
    private static final String MOD_PREFIX = CelestialSkillTree.MODID + ":";
    private static final String OTHER_ICON = "star";
    private static final Pattern SPELL_SCHOOL = Pattern.compile("irons_spellbooks:(\\w+?)_(spell_power|magic_resist)");
    private static final Map<String, String> ICON_BY_ATTRIBUTE = Map.ofEntries(
            entry("minecraft:generic.luck", "luck"),
            entry("minecraft:generic.oxygen_bonus", "diver"),
            entry("minecraft:generic.safe_fall_distance", "feather"),
            entry("minecraft:generic.fall_damage_multiplier", "fall_guard"),
            entry("minecraft:generic.water_movement_efficiency", "wave"),
            entry("neoforge:swim_speed", "swim"),
            entry("minecraft:generic.burning_time", "burning"),
            entry("minecraft:player.block_break_speed", "cracked_block"),
            entry("minecraft:player.block_interaction_range", "reach_block"),
            entry("minecraft:player.entity_interaction_range", "reach_entity"),
            entry("minecraft:generic.attack_knockback", "fist"),
            entry("minecraft:generic.step_height", "stairs"),
            entry("minecraft:generic.jump_strength", "jump"),
            entry("minecraft:player.sneaking_speed", "sneak_shot"),
            entry("minecraft:player.submerged_mining_speed", "depth"),
            entry("minecraft:player.sweeping_damage_ratio", "sweep"),
            entry("minecraft:generic.movement_efficiency", "boot"),
            entry("minecraft:generic.explosion_knockback_resistance", "bomb"),
            entry("irons_spellbooks:max_mana", "crystal"),
            entry("irons_spellbooks:mana_regen", "mana_flow"),
            entry("irons_spellbooks:spell_power", "magic"),
            entry("irons_spellbooks:spell_resist", "ward_circle"),
            entry("irons_spellbooks:cooldown_reduction", "hourglass"),
            entry("irons_spellbooks:cast_time_reduction", "hourglass"),
            entry("irons_spellbooks:summon_damage", "soul"),
            entry("irons_spellbooks:casting_movespeed", "caster_step"),
            entry("ars_nouveau:ars_nouveau.perk.max_mana", "crystal"),
            entry("ars_nouveau:ars_nouveau.perk.mana_regen", "mana_flow"),
            entry("ars_nouveau:ars_nouveau.perk.spell_damage", "magic"),
            entry("ars_nouveau:ars_nouveau.perk.warding", "ward_circle"),
            entry("malum:soul_ward_capacity", "soul"),
            entry("malum:soul_ward_recovery_rate", "soul"),
            entry("malum:soul_ward_integrity", "soul"),
            entry("malum:spirit_spoils", "soul"),
            entry("malum:scythe_proficiency", "scythe"),
            entry("malum:arcane_resonance", "magic"),
            entry("malum:charge_recovery_rate", "hourglass"),
            entry("lodestone:magic_damage", "magic"),
            entry("lodestone:magic_proficiency", "magic"),
            entry("lodestone:magic_resistance", "ward_circle"),
            entry("forbidden_arcanus:generic.magic_protection", "ward_circle"),
            entry("forbidden_arcanus:generic.aureal_regeneration", "halo"),
            entry("curseofpandora:spell_tolerance", "ward_circle"),
            entry("apothic_attributes:fire_damage", "fire"),
            entry("apothic_attributes:cold_damage", "school_ice"),
            entry("apothic_attributes:experience_gained", "book"),
            entry("apothic_attributes:overheal", "heart_plus"),
            entry("apothic_attributes:current_hp_damage", "damage"),
            entry("apothic_attributes:ghost_health", "ghost"),
            entry("apothic_attributes:armor_pierce", "critical"),
            entry("apothic_attributes:armor_shred", "critical"),
            entry("apothic_attributes:prot_pierce", "critical"),
            entry("apothic_attributes:crit_chance", "critical"),
            entry("apothic_attributes:crit_damage", "point_blank"),
            entry("apothic_attributes:dodge_chance", "dodge"),
            entry("apothic_attributes:arrow_velocity", "projectile"),
            entry("apothic_attributes:draw_speed", "bow_grip"),
            entry("puffish_attributes:melee_damage", "melee"),
            entry("puffish_attributes:ranged_damage", "projectile"),
            entry("puffish_attributes:magic_damage", "magic"),
            entry("puffish_attributes:melee_resistance", "shield"),
            entry("puffish_attributes:ranged_resistance", "arrow_guard"),
            entry("puffish_attributes:magic_resistance", "ward_circle"),
            entry("puffish_attributes:tamed_damage", "footprint"),
            entry("puffish_attributes:tamed_resistance", "footprint"),
            entry("puffish_attributes:sprinting_speed", "mobility"),
            entry("puffish_attributes:stamina", "lunge"),
            entry("puffish_attributes:fortune", "luck"),
            entry("puffish_attributes:repair_cost", "tools"),
            entry("parcool:parcool.max_stamina", "lunge"),
            entry("parcool:parcool.stamina_recovery", "lunge"),
            entry("l2damagetracker:regen", "healing"),
            entry("l2damagetracker:bow_strength", "bow"),
            entry("ftbultimine:max_blocks_modifier", "mining"),
            entry("curios:ring", "halo"),
            entry("curios:charm", "halo"));

    private enum Group {
        DEFENSE, OFFENSE, UTILITY
    }

    private enum Format {
        NUMBER, PERCENT, MULTIPLIER, BLOCKS_PER_SECOND
    }

    private record Row(Group group, Holder<Attribute> attribute, String icon, Format format, boolean always) {
    }

    private static final List<Row> ROWS = List.of(
            new Row(Group.DEFENSE, Attributes.MAX_HEALTH, "health", Format.NUMBER, true),
            new Row(Group.DEFENSE, Attributes.ARMOR, "plate", Format.NUMBER, true),
            new Row(Group.DEFENSE, Attributes.ARMOR_TOUGHNESS, "shield", Format.NUMBER, true),
            new Row(Group.DEFENSE, Attributes.KNOCKBACK_RESISTANCE, "knockback", Format.NUMBER, true),
            new Row(Group.DEFENSE, ModAttributes.MAGIC_RESISTANCE, "ward_circle", Format.PERCENT, false),
            new Row(Group.DEFENSE, ModAttributes.DAMAGE_REDUCTION, "defense", Format.PERCENT, false),
            new Row(Group.DEFENSE, ModAttributes.DODGE_CHANCE, "dodge", Format.PERCENT, false),
            new Row(Group.DEFENSE, ModAttributes.HEALING_RECEIVED, "healing", Format.PERCENT, false),
            new Row(Group.DEFENSE, ModAttributes.LIFESTEAL, "lifesteal", Format.PERCENT, false),
            new Row(Group.OFFENSE, Attributes.ATTACK_DAMAGE, "damage", Format.NUMBER, true),
            new Row(Group.OFFENSE, Attributes.ATTACK_SPEED, "attack_speed", Format.NUMBER, true),
            new Row(Group.OFFENSE, ModAttributes.CRIT_CHANCE, "critical", Format.PERCENT, true),
            new Row(Group.OFFENSE, ModAttributes.CRIT_DAMAGE, "point_blank", Format.MULTIPLIER, true),
            new Row(Group.OFFENSE, ModAttributes.LIGHT_WEAPON_DAMAGE, "light_weapon", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.HEAVY_WEAPON_DAMAGE, "heavy_weapon", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.PROJECTILE_DAMAGE, "projectile", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.LONG_RANGE_DAMAGE, "range", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.CROSSBOW_DAMAGE, "crossbow", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.CHARGED_SHOT_DAMAGE, "charge", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.LOW_HEALTH_DAMAGE, "last_stand", Format.PERCENT, false),
            new Row(Group.OFFENSE, ModAttributes.SPELL_ECHO_CHANCE, "echo", Format.PERCENT, false),
            new Row(Group.UTILITY, Attributes.MOVEMENT_SPEED, "mobility", Format.BLOCKS_PER_SECOND, true),
            new Row(Group.UTILITY, ModAttributes.MINING_SPEED, "mining", Format.PERCENT, true),
            new Row(Group.UTILITY, ModAttributes.TOOL_PRESERVATION, "tools", Format.PERCENT, true),
            new Row(Group.UTILITY, ModAttributes.POTION_DURATION, "potion", Format.PERCENT, false),
            new Row(Group.UTILITY, ModAttributes.HARVEST_BONUS, "leaf", Format.PERCENT, false));

    private StatsPanel() {
    }

    private static MutableComponent text(String key, Object... arguments) {
        return GuiText.of("stats." + key, arguments);
    }

    static StatsSheet sheet(Player player, PlayerSkillState state, SkillGraphSnapshot graph, Collection<String> pending, int pendingCost) {
        Optional<StatsSheet.Sign> sign = state.sign().map(signId -> new StatsSheet.Sign(signId, Component.translatable(SkillSign.nameKey(signId))));
        StatsSheet.Points points = new StatsSheet.Points(state.totalPoints(), state.activeSpentPoints(),
                state.availablePoints() - pendingCost, pendingCost);
        List<StatsSheet.Section> sections = player == null ? List.of() : sections(player, pendingDeltas(player, graph, pending));
        Component name = player == null ? text("title") : player.getName();
        return new StatsSheet(name, sign, state.allocations().size(), points, sections,
                effects(state, graph, node -> node.type() != NodeType.KEYSTONE && hasConditionalEffect(node) && !hasTrigger(node)),
                effects(state, graph, node -> node.type() != NodeType.KEYSTONE && hasTrigger(node)),
                effects(state, graph, node -> node.type() == NodeType.KEYSTONE));
    }

    private static List<StatsSheet.Section> sections(Player player, Map<String, Double> deltas) {
        Map<Group, List<StatsSheet.Stat>> groups = new EnumMap<>(Group.class);
        Map<String, Double> unlisted = new TreeMap<>(deltas);
        for (Row row : ROWS) {
            String attribute = resolved(row.attribute());
            unlisted.remove(attribute);
            stat(player, row, deltas.getOrDefault(attribute, 0.0))
                    .ifPresent(stat -> groups.computeIfAbsent(row.group(), unused -> new ArrayList<>()).add(stat));
        }
        unlisted.forEach((attribute, delta) -> holder(attribute).ifPresent(holder -> stat(player,
                new Row(Group.UTILITY, holder, iconFor(attribute), attribute.startsWith(MOD_PREFIX) ? Format.PERCENT : Format.NUMBER, false), delta)
                .ifPresent(stat -> groups.computeIfAbsent(Group.UTILITY, unused -> new ArrayList<>()).add(stat))));
        List<StatsSheet.Section> sections = new ArrayList<>();
        groups.forEach((group, stats) -> sections.add(new StatsSheet.Section(text(group.name().toLowerCase(Locale.ROOT)), stats)));
        return sections;
    }

    private static String iconFor(String attribute) {
        Matcher school = SPELL_SCHOOL.matcher(attribute);
        if (school.matches()) {
            return "school_" + school.group(1) + (school.group(2).equals("magic_resist") ? "_ward" : "");
        }
        return ICON_BY_ATTRIBUTE.getOrDefault(attribute, OTHER_ICON);
    }

    private static Optional<StatsSheet.Stat> stat(Player player, Row row, double pendingDelta) {
        AttributeInstance instance = player.getAttribute(row.attribute());
        boolean own = row.attribute().getRegisteredName().startsWith(MOD_PREFIX);
        double current = own ? value(player, row.attribute()) : instance == null ? 0 : instance.getValue();
        double baseline = own ? row.attribute().value().getDefaultValue() : instance == null ? 0 : AttributeMath.withoutTree(instance);
        boolean changedByTree = Math.abs(current - baseline) > NEGLIGIBLE;
        boolean previewed = Math.abs(pendingDelta) > NEGLIGIBLE;
        if (!row.always() && !changedByTree && !previewed) {
            return Optional.empty();
        }
        return Optional.of(new StatsSheet.Stat(icon(row.icon()), label(row), format(row.format(), current),
                changedByTree ? Optional.of(bonus(row.format(), current, baseline)) : Optional.empty(),
                previewed ? Optional.of(format(row.format(), current + pendingDelta)) : Optional.empty()));
    }

    private static Component label(Row row) {
        return row.format() == Format.BLOCKS_PER_SECOND ? text("movement") : Component.translatable(row.attribute().value().getDescriptionId());
    }

    private static String format(Format format, double value) {
        return switch (format) {
            case NUMBER -> number(value);
            case PERCENT -> percent(value);
            case MULTIPLIER -> "×" + number(1 + value);
            case BLOCKS_PER_SECOND -> text("blocks_per_second", number(value * BLOCKS_PER_SECOND_PER_SPEED)).getString();
        };
    }

    private static String bonus(Format format, double value, double baseline) {
        return switch (format) {
            case NUMBER -> signed(value - baseline);
            case BLOCKS_PER_SECOND -> signedPercent(baseline == 0 ? 0 : value / baseline - 1);
            case PERCENT, MULTIPLIER -> signedPercent(value - baseline);
        };
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "textures/skill_icons/" + name + ".png");
    }

    private static boolean hasConditionalEffect(SkillNodeDefinition node) {
        return node.effects().stream().anyMatch(effect -> !(effect instanceof SkillEffect.Attribute));
    }

    private static boolean hasTrigger(SkillNodeDefinition node) {
        return node.effects().stream().anyMatch(SkillEffect.Trigger.class::isInstance);
    }

    private static List<StatsSheet.Effect> effects(PlayerSkillState state, SkillGraphSnapshot graph, Predicate<SkillNodeDefinition> kind) {
        Map<String, List<SkillNodeDefinition>> alike = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        state.allocations().keySet().stream().flatMap(id -> graph.node(id).stream()).filter(kind)
                .forEach(node -> alike.computeIfAbsent(SkillTexts.name(node).getString() + '\n' + SkillTexts.description(node).getString(),
                        key -> new ArrayList<>()).add(node));
        return alike.values().stream().map(nodes -> combined(graph, state, nodes)).toList();
    }

    private static StatsSheet.Effect combined(SkillGraphSnapshot graph, PlayerSkillState state, List<SkillNodeDefinition> nodes) {
        SkillNodeDefinition first = nodes.getFirst();
        double strength = nodes.stream().mapToDouble(node -> CompiledBuild.strength(graph, state, node)).sum();
        return new StatsSheet.Effect(SkillTexts.name(first), SkillTexts.description(first, strength), nodes.size());
    }

    private static Map<String, Double> pendingDeltas(Player player, SkillGraphSnapshot graph, Collection<String> pending) {
        Map<String, List<AttributeModifier>> replacements = new TreeMap<>();
        attributeSums(graph, pending).forEach((key, pendingSum) -> {
            String attribute = key.attribute();
            String operation = key.operation();
            ResourceLocation modifierId = EffectEngine.modifierId("tree", key.id());
            double total = EffectEngine.capped(attribute, operation, appliedAmount(player, attribute, modifierId) + EffectEngine.scaled(pendingSum));
            replacements.computeIfAbsent(attribute, unused -> new ArrayList<>())
                    .add(new AttributeModifier(modifierId, total, EffectEngine.operationFor(attribute, operation)));
        });
        Map<String, Double> deltas = new HashMap<>();
        replacements.forEach((attribute, modifiers) -> holder(attribute).ifPresent(holder -> {
            AttributeInstance instance = player.getAttribute(holder);
            if (instance != null) {
                deltas.merge(holder.getRegisteredName(), AttributeMath.withReplaced(instance, modifiers) - instance.getValue(), Double::sum);
            }
        }));
        return deltas;
    }

    private static Map<CompiledBuild.AttributeKey, Double> attributeSums(SkillGraphSnapshot graph, Collection<String> nodeIds) {
        Map<CompiledBuild.AttributeKey, Double> sums = new TreeMap<>();
        nodeIds.stream().flatMap(id -> graph.node(id).stream()).flatMap(node -> node.effects().stream()).forEach(effect -> {
            if (effect instanceof SkillEffect.Attribute attribute) {
                sums.merge(CompiledBuild.sumKey(attribute.attribute(), attribute.operation()), attribute.amount(), Double::sum);
            }
        });
        return sums;
    }

    private static double appliedAmount(Player player, String attribute, ResourceLocation modifierId) {
        return holder(attribute).map(player::getAttribute)
                .map(instance -> instance.getModifier(modifierId)).map(AttributeModifier::amount).orElse(0.0);
    }

    private static String resolved(Holder<Attribute> attribute) {
        return holder(attribute.getRegisteredName()).map(Holder::getRegisteredName).orElse(attribute.getRegisteredName());
    }

    public static double value(Player player, Holder<Attribute> attribute) {
        return AttributeAliases.active(attribute.getRegisteredName())
                .map(alias -> aliasedValue(player, alias))
                .orElseGet(() -> rawValue(player, Optional.of(attribute)));
    }

    private static double aliasedValue(Player player, AttributeAliases.Alias alias) {
        Optional<Holder.Reference<Attribute>> target = holder(alias.target());
        if (!alias.scalesAnotherValue()) {
            return rawValue(player, target) - alias.baseOffset();
        }
        return target.map(player::getAttribute).map(instance -> instance.getModifiers().stream()
                .filter(modifier -> modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                .mapToDouble(AttributeModifier::amount).sum()).orElse(0.0);
    }

    private static double rawValue(Player player, Optional<? extends Holder<Attribute>> attribute) {
        return attribute.filter(player.getAttributes()::hasAttribute).map(player::getAttributeValue).orElse(0.0);
    }

    private static Optional<Holder.Reference<Attribute>> holder(String attribute) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(AttributeAliases.resolve(attribute)));
    }

    private static String number(double value) {
        return SkillTooltips.format(value, false);
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "") + number(value);
    }

    private static String percent(double fraction) {
        return SkillTooltips.format(fraction, true);
    }

    public static String signedPercent(double fraction) {
        return (fraction >= 0 ? "+" : "") + percent(fraction);
    }
}
