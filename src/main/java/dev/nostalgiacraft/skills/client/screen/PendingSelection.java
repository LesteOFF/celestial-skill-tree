package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PendingSelection {
    private static final int HISTORY_LIMIT = 64;

    private record Snapshot(Set<String> nodes, Map<String, Integer> ranks) {
    }

    private final Set<String> nodes = new LinkedHashSet<>();
    private final Map<String, Integer> ranks = new LinkedHashMap<>();
    private final Deque<Snapshot> undone = new ArrayDeque<>();
    private final Deque<Snapshot> history = new ArrayDeque<>();

    boolean isEmpty() {
        return nodes.isEmpty() && ranks.isEmpty();
    }

    void clear() {
        if (!isEmpty()) {
            remember();
            nodes.clear();
            ranks.clear();
        }
    }

    void forget() {
        nodes.clear();
        ranks.clear();
        history.clear();
        undone.clear();
    }

    boolean contains(String nodeId) {
        return nodes.contains(nodeId);
    }

    Set<String> nodes() {
        return Collections.unmodifiableSet(nodes);
    }

    void add(String nodeId) {
        remember();
        nodes.add(nodeId);
    }

    void addAll(Collection<String> nodeIds) {
        remember();
        nodes.addAll(nodeIds);
    }

    void remove(String nodeId) {
        remember();
        nodes.remove(nodeId);
        ranks.remove(nodeId);
    }

    void removeAll(Collection<String> nodeIds) {
        remember();
        nodes.removeAll(nodeIds);
        ranks.keySet().removeAll(nodeIds);
    }

    int extraRanks(String nodeId) {
        return ranks.getOrDefault(nodeId, 0);
    }

    void addRank(String nodeId) {
        remember();
        ranks.merge(nodeId, 1, Integer::sum);
    }

    void clearRanks(String nodeId) {
        remember();
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

    boolean undo() {
        return travel(history, undone);
    }

    boolean redo() {
        return travel(undone, history);
    }

    private boolean travel(Deque<Snapshot> from, Deque<Snapshot> to) {
        if (from.isEmpty()) {
            return false;
        }
        to.push(snapshot());
        Snapshot target = from.pop();
        nodes.clear();
        nodes.addAll(target.nodes());
        ranks.clear();
        ranks.putAll(target.ranks());
        return true;
    }

    private void remember() {
        history.push(snapshot());
        if (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
        undone.clear();
    }

    private Snapshot snapshot() {
        return new Snapshot(new LinkedHashSet<>(nodes), new LinkedHashMap<>(ranks));
    }
}
