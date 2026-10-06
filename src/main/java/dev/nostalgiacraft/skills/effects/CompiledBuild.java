package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.graph.NodeTraits;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record CompiledBuild(Map<String, Double> attributeSums, List<Keyed<SkillEffect.ConditionalAttribute>> conditional,
                            List<Keyed<SkillEffect.DamageModifier>> damage, List<Keyed<SkillEffect.Trigger>> triggers) {
    public static final CompiledBuild EMPTY = new CompiledBuild(Map.of(), List.of(), List.of(), List.of());

    public record Keyed<T>(String key, T effect) {
    }

    public static String sumKey(String attribute, String op) {
        return attribute + "|" + op;
    }

    public static CompiledBuild compile(SkillGraphSnapshot graph, PlayerSkillState state) {
        return compile(graph, state, Map.of());
    }

    public static CompiledBuild compile(SkillGraphSnapshot graph, PlayerSkillState state,
                                        Map<String, List<SkillEffect>> extraSources) {
        Collector collector = new Collector();
        for (String id : state.allocations().keySet()) {
            double factor = amplification(graph, state, id);
            graph.node(id).ifPresent(node -> collector.addAll(id, node.effects().stream().map(effect -> scaled(effect, factor)).toList()));
        }
        extraSources.forEach(collector::addAll);
        return collector.build();
    }

    private static double amplification(SkillGraphSnapshot graph, PlayerSkillState state, String nodeId) {
        return graph.neighbours(nodeId).stream().filter(state::isAllocated).flatMap(neighbour -> graph.node(neighbour).stream())
                .mapToDouble(neighbour -> neighbour.traits().neighbourMultiplier()).max().orElse(NodeTraits.NO_MULTIPLIER);
    }

    private static SkillEffect scaled(SkillEffect effect, double factor) {
        return switch (effect) {
            case SkillEffect.Attribute attribute ->
                    new SkillEffect.Attribute(attribute.attribute(), attribute.operation(), attribute.amount() * factor);
            case SkillEffect.ConditionalAttribute conditional -> new SkillEffect.ConditionalAttribute(conditional.attribute(),
                    conditional.operation(), conditional.amount() * factor, conditional.condition());
            case SkillEffect.DamageModifier damage ->
                    new SkillEffect.DamageModifier(damage.dealt(), damage.scope(), damage.amount() * factor, damage.condition());
            case SkillEffect.Trigger trigger -> trigger;
        };
    }

    private static final class Collector {
        private final Map<String, Double> attributeSums = new TreeMap<>();
        private final List<Keyed<SkillEffect.ConditionalAttribute>> conditional = new ArrayList<>();
        private final List<Keyed<SkillEffect.DamageModifier>> damage = new ArrayList<>();
        private final List<Keyed<SkillEffect.Trigger>> triggers = new ArrayList<>();

        void addAll(String sourceId, List<SkillEffect> effects) {
            for (int i = 0; i < effects.size(); i++) {
                add(sourceId + "#" + i, effects.get(i));
            }
        }

        private void add(String key, SkillEffect effect) {
            switch (effect) {
                case SkillEffect.Attribute attribute ->
                        attributeSums.merge(sumKey(attribute.attribute(), attribute.operation()), attribute.amount(), Double::sum);
                case SkillEffect.ConditionalAttribute conditionalAttribute -> conditional.add(new Keyed<>(key, conditionalAttribute));
                case SkillEffect.DamageModifier damageModifier -> damage.add(new Keyed<>(key, damageModifier));
                case SkillEffect.Trigger trigger -> triggers.add(new Keyed<>(key, trigger));
            }
        }

        CompiledBuild build() {
            return new CompiledBuild(Map.copyOf(attributeSums), List.copyOf(conditional), List.copyOf(damage), List.copyOf(triggers));
        }
    }
}
