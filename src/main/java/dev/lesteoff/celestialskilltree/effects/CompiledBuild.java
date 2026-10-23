package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.graph.NodeTraits;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CompiledBuild(Map<AttributeKey, Double> attributeSums, List<Keyed<SkillEffect.ConditionalAttribute>> conditional,
                            List<Keyed<SkillEffect.DamageModifier>> damage, List<Keyed<SkillEffect.Trigger>> triggers) {
    public static final CompiledBuild EMPTY = new CompiledBuild(Map.of(), List.of(), List.of(), List.of());

    public record Keyed<T>(String key, T effect) {
    }

    public record AttributeKey(String attribute, String operation) implements Comparable<AttributeKey> {
        public String id() {
            return attribute + "|" + operation;
        }

        @Override
        public int compareTo(AttributeKey other) {
            return id().compareTo(other.id());
        }
    }

    public static AttributeKey sumKey(String attribute, String operation) {
        return new AttributeKey(attribute, operation);
    }

    public static CompiledBuild compile(SkillGraphSnapshot graph, PlayerSkillState state) {
        return compile(graph, state, Map.of());
    }

    public static CompiledBuild compile(SkillGraphSnapshot graph, PlayerSkillState state,
                                        Map<String, List<SkillEffect>> extraSources) {
        return compile(graph, state, extraSources, Set.of());
    }

    public static CompiledBuild compile(SkillGraphSnapshot graph, PlayerSkillState state,
                                        Map<String, List<SkillEffect>> extraSources, Set<String> silenced) {
        Collector collector = new Collector();
        for (String id : state.allocations().keySet()) {
            if (silenced.contains(id) || state.isDisabled(id)) {
                continue;
            }
            SkillNodeDefinition node = graph.nodes().get(id);
            if (node != null) {
                collector.addAll(id, node.effects(), amplification(graph, state, id) * ranks(node, state));
            }
        }
        extraSources.forEach((sourceId, effects) -> collector.addAll(sourceId, effects, 1.0));
        return collector.build();
    }

    private static int ranks(SkillNodeDefinition node, PlayerSkillState state) {
        return Math.clamp(state.rank(node.id()), SkillNodeDefinition.SINGLE_RANK, Math.max(SkillNodeDefinition.SINGLE_RANK, node.maxRank()));
    }

    private static double amplification(SkillGraphSnapshot graph, PlayerSkillState state, String nodeId) {
        double strongest = NodeTraits.NO_MULTIPLIER;
        for (String neighbour : graph.neighbours(nodeId)) {
            SkillNodeDefinition next = graph.nodes().get(neighbour);
            if (next != null && state.isAllocated(neighbour)) {
                strongest = Math.max(strongest, next.traits().neighbourMultiplier());
            }
        }
        return strongest;
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
        private final Map<AttributeKey, Double> attributeSums = new HashMap<>();
        private final List<Keyed<SkillEffect.ConditionalAttribute>> conditional = new ArrayList<>();
        private final List<Keyed<SkillEffect.DamageModifier>> damage = new ArrayList<>();
        private final List<Keyed<SkillEffect.Trigger>> triggers = new ArrayList<>();

        void addAll(String sourceId, List<SkillEffect> effects, double factor) {
            for (int i = 0; i < effects.size(); i++) {
                SkillEffect effect = effects.get(i);
                add(sourceId, i, factor == 1.0 ? effect : scaled(effect, factor));
            }
        }

        private void add(String sourceId, int index, SkillEffect effect) {
            switch (effect) {
                case SkillEffect.Attribute attribute ->
                        attributeSums.merge(sumKey(attribute.attribute(), attribute.operation()), attribute.amount(), Double::sum);
                case SkillEffect.ConditionalAttribute conditionalAttribute -> conditional.add(new Keyed<>(key(sourceId, index), conditionalAttribute));
                case SkillEffect.DamageModifier damageModifier -> damage.add(new Keyed<>(key(sourceId, index), damageModifier));
                case SkillEffect.Trigger trigger -> triggers.add(new Keyed<>(key(sourceId, index), trigger));
            }
        }

        private static String key(String sourceId, int index) {
            return sourceId + "#" + index;
        }

        CompiledBuild build() {
            return new CompiledBuild(Map.copyOf(attributeSums), List.copyOf(conditional), List.copyOf(damage), List.copyOf(triggers));
        }
    }
}
