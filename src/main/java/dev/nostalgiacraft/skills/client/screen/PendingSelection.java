package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PendingSelection {
    private final Set<String> nodes = new LinkedHashSet<>();
    private final Map<String, Integer> ranks = new LinkedHashMap<>();

    boolean isEmpty() {
        return nodes.isEmpty() && ranks.isEmpty();
    }

    void clear() {
        nodes.clear();
        ranks.clear();
    }

    boolean contains(String nodeId) {
        return nodes.contains(nodeId);
    }

    Set<String> nodes() {
        return Collections.unmodifiableSet(nodes);
    }

    void add(String nodeId) {
        nodes.add(nodeId);
    }

    void addAll(Collection<String> nodeIds) {
        nodes.addAll(nodeIds);
    }

    void remove(String nodeId) {
        nodes.remove(nodeId);
        ranks.remove(nodeId);
    }

    void removeAll(Collection<String> nodeIds) {
        nodes.removeAll(nodeIds);
        ranks.keySet().removeAll(nodeIds);
    }

    int extraRanks(String nodeId) {
        return ranks.getOrDefault(nodeId, 0);
    }

    void addRank(String nodeId) {
        ranks.merge(nodeId, 1, Integer::sum);
    }

    void clearRanks(String nodeId) {
        ranks.remove(nodeId);
    }

    int marked(String nodeId) {
        return (nodes.contains(nodeId) ? 1 : 0) + extraRanks(nodeId);
    }

    List<String> request() {
        List<String> request = new ArrayList<>(nodes);
        ranks.forEach((nodeId, count) -> request.addAll(Collections.nCopies(count, nodeId)));
        return request;
    }

    void retainValid(SkillGraphSnapshot graph, PlayerSkillState state) {
        nodes.removeIf(id -> !graph.contains(id) || state.isAllocated(id));
        ranks.entrySet().removeIf(marked -> !graph.contains(marked.getKey())
                || !state.isAllocated(marked.getKey()) && !nodes.contains(marked.getKey())
                || marked.getValue() > AllocationService.rankRoom(graph, state, marked.getKey()));
    }
}
