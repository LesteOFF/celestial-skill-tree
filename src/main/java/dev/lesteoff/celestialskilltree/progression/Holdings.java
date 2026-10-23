package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Holdings {
    private final SkillGraphSnapshot graph;
    private final Set<String> nodeIds = new LinkedHashSet<>();
    private final Map<String, Integer> sectorPoints = new HashMap<>();
    private final Set<String> discoveries;
    private final boolean everythingDiscovered;

    private Holdings(SkillGraphSnapshot graph, Set<String> discoveries, boolean everythingDiscovered) {
        this.graph = graph;
        this.discoveries = discoveries;
        this.everythingDiscovered = everythingDiscovered;
    }

    public static Holdings replaying(SkillGraphSnapshot graph) {
        return new Holdings(graph, Set.of(), true);
    }

    public static Holdings of(SkillGraphSnapshot graph, PlayerSkillState state) {
        Holdings holdings = new Holdings(graph, state.discoveries(), false);
        state.allocations().values().forEach(allocation -> holdings.add(allocation.nodeId(), allocation.paidCost()));
        return holdings;
    }

    public void add(String nodeId, int paidCost) {
        nodeIds.add(nodeId);
        graph.node(nodeId).ifPresent(node -> sectorPoints.merge(node.sector(), paidCost, Integer::sum));
    }

    public boolean contains(String nodeId) {
        return nodeIds.contains(nodeId);
    }

    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(nodeIds);
    }

    public int pointsIn(String sector) {
        return sectorPoints.getOrDefault(sector, 0);
    }

    public boolean hasDiscovered(SkillNodeDefinition node) {
        return everythingDiscovered || node.traits().hiddenUntil().map(discoveries::contains).orElse(true);
    }

    public Optional<String> amplifierSealing(String nodeId) {
        if (contains(nodeId)) {
            return Optional.empty();
        }
        return graph.neighbours(nodeId).stream().filter(this::contains)
                .filter(neighbour -> graph.node(neighbour).filter(node -> node.traits().amplifiesNeighbours()).isPresent())
                .findFirst();
    }
}
