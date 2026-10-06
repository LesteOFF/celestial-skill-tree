package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class DiscoveryService {
    private DiscoveryService() {
    }

    public static Set<String> keys(SkillGraphSnapshot graph) {
        return graph.nodes().values().stream().flatMap(node -> node.traits().hiddenUntil().stream()).collect(Collectors.toSet());
    }

    public static boolean discover(PlayerSkillState state, SkillGraphSnapshot graph, String key) {
        if (!keys(graph).contains(key) || !state.discover(key)) {
            return false;
        }
        state.bumpRevision();
        state.log("discovered " + key);
        return true;
    }

    public static boolean discoverAll(PlayerSkillState state, SkillGraphSnapshot graph, Predicate<String> alreadyEarned) {
        boolean changed = false;
        for (String key : keys(graph)) {
            if (alreadyEarned.test(key)) {
                changed |= discover(state, graph, key);
            }
        }
        return changed;
    }
}
