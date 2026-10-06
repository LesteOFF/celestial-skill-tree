package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A player's confirmed build flattened into effect lists. Rebuilt only when the build or the graph changes, so
 * nothing scans all 480 nodes per tick.
 */
public record CompiledBuild(Map<String, Double> attributeSums, List<Keyed<SkillEffect.ConditionalAttribute>> conditional,
                            List<Keyed<SkillEffect.DamageModifier>> damage, List<Keyed<SkillEffect.Trigger>> triggers) {
    public static final CompiledBuild EMPTY = new CompiledBuild(Map.of(), List.of(), List.of(), List.of());

    /** An effect with a stable key (nodeId#index) used for modifier ids and cooldowns. */
    public record Keyed<T>(String key, T effect) {
    }

    /** Key "attribute|operation" -> summed amount. */
    public static String sumKey(String attribute, String op) {
        return attribute + "|" + op;
    }

    public static CompiledBuild compile(SkillGraphSnapshot g, PlayerSkillState s) {
        Map<String, Double> sums = new TreeMap<>();
        List<Keyed<SkillEffect.ConditionalAttribute>> cond = new ArrayList<>();
        List<Keyed<SkillEffect.DamageModifier>> dmg = new ArrayList<>();
        List<Keyed<SkillEffect.Trigger>> trig = new ArrayList<>();
        for (String id : s.allocations().keySet()) {
            SkillNodeDefinition n = g.nodes().get(id);
            if (n == null) {
                continue;
            }
            for (int i = 0; i < n.effects().size(); i++) {
                String key = id + "#" + i;
                switch (n.effects().get(i)) {
                    case SkillEffect.Attribute a -> sums.merge(sumKey(a.attribute(), a.operation()), a.amount(), Double::sum);
                    case SkillEffect.ConditionalAttribute c -> cond.add(new Keyed<>(key, c));
                    case SkillEffect.DamageModifier d -> dmg.add(new Keyed<>(key, d));
                    case SkillEffect.Trigger t -> trig.add(new Keyed<>(key, t));
                }
            }
        }
        return new CompiledBuild(Map.copyOf(sums), List.copyOf(cond), List.copyOf(dmg), List.copyOf(trig));
    }
}
