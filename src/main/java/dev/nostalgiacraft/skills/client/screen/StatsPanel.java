package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.effects.AttributeAliases;
import dev.nostalgiacraft.skills.effects.CompiledBuild;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.graph.SkillSign;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.ChatFormatting;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class StatsPanel {
    private static final double BLOCKS_PER_SECOND_PER_SPEED = 43.17;
    private static final String MOD_PREFIX = NostalgiaSkills.MODID + ":";
    private static final List<String> EXTRA_ATTRIBUTES = List.of("charged_shot_damage", "crossbow_damage",
            "damage_reduction", "dodge_chance", "harvest_bonus", "healing_received", "heavy_weapon_damage", "lifesteal",
            "light_weapon_damage", "long_range_damage", "low_health_damage", "magic_resistance", "potion_duration",
            "projectile_damage");

    private StatsPanel() {
    }

    private static MutableComponent text(String key, Object... arguments) {
        return GuiText.of("stats." + key, arguments);
    }

    public static List<Component> lines(Player player, PlayerSkillState state, SkillGraphSnapshot graph,
                                        Collection<String> pending, int pendingCost) {
        List<Component> lines = new ArrayList<>();
        lines.add(text("title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (player == null) {
            lines.add(text("no_world").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            addAttributes(lines, player);
        }
        state.sign().ifPresent(signId -> lines.add(text("sign", Component.translatable(SkillSign.nameKey(signId)),
                Component.translatable(ClientSkillState.worldRules().signDescriptionKey(signId))).withStyle(ChatFormatting.LIGHT_PURPLE)));
        addSection(lines, "conditional", conditionalEffects(state, graph));
        addSection(lines, "keystones", keystones(state, graph));
        lines.add(text("points", state.totalPoints(), state.activeSpentPoints(), state.availablePoints(), pendingCost)
                .withStyle(ChatFormatting.YELLOW));
        addPendingPreview(lines, player, graph, pending);
        return lines;
    }

    private static void addAttributes(List<Component> lines, Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double health = maxHealth.getValue();
            lines.add(text("max_health", number(health), number(health / 2), signed(health - AttributeMath.withoutTree(maxHealth))));
        }
        addVanilla(lines, player, Attributes.ATTACK_DAMAGE, "attack_damage");
        addVanilla(lines, player, Attributes.ATTACK_SPEED, "attack_speed");
        addVanilla(lines, player, Attributes.ARMOR, "armor");
        addVanilla(lines, player, Attributes.ARMOR_TOUGHNESS, "armor_toughness");
        addMovementSpeed(lines, player);
        addVanilla(lines, player, Attributes.KNOCKBACK_RESISTANCE, "knockback_resistance");
        lines.add(text("crit", percent(value(player, ModAttributes.CRIT_CHANCE)), number(1 + value(player, ModAttributes.CRIT_DAMAGE))));
        lines.add(text("mining", signedPercent(value(player, ModAttributes.MINING_SPEED)),
                signedPercent(value(player, ModAttributes.TOOL_PRESERVATION))));
        for (String name : EXTRA_ATTRIBUTES) {
            Holder<Attribute> attribute = ModAttributes.ALL.get(name);
            double amount = value(player, attribute);
            if (amount != 0) {
                lines.add(text("mod_line", Component.translatable(attribute.value().getDescriptionId()), signedPercent(amount)));
            }
        }
    }

    private static void addVanilla(List<Component> lines, Player player, Holder<Attribute> attribute, String key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            lines.add(text(key, number(instance.getValue()), signed(instance.getValue() - AttributeMath.withoutTree(instance))));
        }
    }

    private static void addMovementSpeed(List<Component> lines, Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double withoutTree = AttributeMath.withoutTree(speed);
            double treeShare = withoutTree == 0 ? 0 : speed.getValue() / withoutTree - 1;
            lines.add(text("movement_speed", number(speed.getValue() * BLOCKS_PER_SECOND_PER_SPEED), signedPercent(treeShare)));
        }
    }

    private static void addSection(List<Component> lines, String titleKey, List<Component> entries) {
        lines.add(text(titleKey).withStyle(ChatFormatting.AQUA));
        if (entries.isEmpty()) {
            lines.add(text("none").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.addAll(entries);
        }
    }

    private static List<SkillNodeDefinition> ownedNodes(PlayerSkillState state, SkillGraphSnapshot graph) {
        return state.allocations().keySet().stream().flatMap(id -> graph.node(id).stream()).toList();
    }

    private static List<Component> conditionalEffects(PlayerSkillState state, SkillGraphSnapshot graph) {
        List<Component> entries = new ArrayList<>();
        for (SkillNodeDefinition node : ownedNodes(state, graph)) {
            boolean hasConditionalEffect = node.effects().stream().anyMatch(effect -> !(effect instanceof SkillEffect.Attribute));
            if (node.type() != NodeType.KEYSTONE && hasConditionalEffect) {
                entries.add(Component.literal(" • ").append(SkillTexts.name(node)).append(": ")
                        .append(SkillTexts.description(node)).withStyle(ChatFormatting.GRAY));
            }
        }
        return entries;
    }

    private static List<Component> keystones(PlayerSkillState state, SkillGraphSnapshot graph) {
        List<Component> entries = new ArrayList<>();
        for (SkillNodeDefinition node : ownedNodes(state, graph)) {
            if (node.type() == NodeType.KEYSTONE) {
                entries.add(Component.literal(" ◆ ").append(SkillTexts.name(node)).withStyle(ChatFormatting.GOLD));
                entries.add(Component.literal("   ").append(SkillTexts.description(node)).withStyle(ChatFormatting.GRAY));
            }
        }
        return entries;
    }

    private static void addPendingPreview(List<Component> lines, Player player, SkillGraphSnapshot graph,
                                          Collection<String> pending) {
        Map<CompiledBuild.AttributeKey, Double> pendingSums = attributeSums(graph, pending);
        if (pendingSums.isEmpty()) {
            return;
        }
        lines.add(text("pending").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        List<MutableComponent> previews = player == null ? sumLines(pendingSums) : beforeAfterLines(player, pendingSums);
        previews.forEach(preview -> lines.add(
                Component.literal("   ").append(preview).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC)));
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

    private static List<MutableComponent> sumLines(Map<CompiledBuild.AttributeKey, Double> sums) {
        List<MutableComponent> lines = new ArrayList<>();
        sums.forEach((key, sum) -> {
            String attribute = key.attribute();
            String operation = key.operation();
            boolean relative = !operation.equals("add_value") || attribute.startsWith(MOD_PREFIX);
            BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attribute)).ifPresent(holder -> lines.add(
                    Component.translatable(holder.value().getDescriptionId()).append(" " + (relative ? signedPercent(EffectEngine.scaled(sum)) : signed(EffectEngine.scaled(sum))))));
        });
        return lines;
    }

    private static List<MutableComponent> beforeAfterLines(Player player, Map<CompiledBuild.AttributeKey, Double> pending) {
        Map<String, List<AttributeModifier>> replacements = new TreeMap<>();
        pending.forEach((key, pendingSum) -> {
            String attribute = key.attribute();
            String operation = key.operation();
            ResourceLocation modifierId = EffectEngine.modifierId("tree", key.id());
            double total = EffectEngine.capped(attribute, operation, appliedAmount(player, attribute, modifierId) + EffectEngine.scaled(pendingSum));
            replacements.computeIfAbsent(attribute, unused -> new ArrayList<>())
                    .add(new AttributeModifier(modifierId, total, EffectEngine.operationFor(attribute, operation)));
        });
        List<MutableComponent> lines = new ArrayList<>();
        replacements.forEach((attribute, modifiers) -> holder(attribute)
                .map(player::getAttribute).ifPresent(instance -> lines.add(beforeAfter(instance, modifiers, attribute.startsWith(MOD_PREFIX)))));
        return lines;
    }

    private static double appliedAmount(Player player, String attribute, ResourceLocation modifierId) {
        return holder(attribute).map(player::getAttribute)
                .map(instance -> instance.getModifier(modifierId)).map(AttributeModifier::amount).orElse(0.0);
    }

    private static MutableComponent beforeAfter(AttributeInstance instance, List<AttributeModifier> replacements, boolean percent) {
        return GuiText.of("preview", Component.translatable(instance.getAttribute().value().getDescriptionId()),
                SkillTooltips.format(instance.getValue(), percent),
                SkillTooltips.format(AttributeMath.withReplaced(instance, replacements), percent));
    }

    private static double value(Player player, Holder<Attribute> attribute) {
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

    private static String signedPercent(double fraction) {
        return (fraction >= 0 ? "+" : "") + percent(fraction);
    }
}
