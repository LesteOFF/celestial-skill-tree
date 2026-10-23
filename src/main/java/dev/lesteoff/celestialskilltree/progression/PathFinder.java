package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.ExclusiveGroup;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class PathFinder {
    public record Route(List<String> newNodes, int cost, Optional<AllocationService.Rejection> unresolved) {
        public boolean valid() {
            return unresolved.isEmpty() && !newNodes.isEmpty();
        }
    }

    private record Reached(String nodeId, int cost) {
    }

    private PathFinder() {
    }

    public static Optional<Route> route(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> pending,
                                        String target) {
        if (!graph.contains(target) || state.isAllocated(target) || pending.contains(target)) {
            return Optional.empty();
        }
        Set<String> held = new HashSet<>(state.allocations().keySet());
        held.addAll(pending);
        if (held.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> costs = new HashMap<>();
        Set<String> blocked = blockedByExclusiveChoices(graph, held);
        blocked.addAll(sealedOrUndiscovered(graph, state, pending));
        Map<String, String> previous = cheapestPaths(graph, held, blocked, target, costs);
        if (!costs.containsKey(target)) {
            return Optional.empty();
        }
        List<String> newNodes = new ArrayList<>();
        for (String id = target; id != null && !held.contains(id); id = previous.get(id)) {
            newNodes.add(id);
        }
        Collections.reverse(newNodes);
        List<String> batch = new ArrayList<>(pending);
        batch.addAll(newNodes);
        Optional<AllocationService.Rejection> problem = AllocationService.expand(graph, state, batch)
                .rejected().values().stream().findFirst();
        return Optional.of(new Route(List.copyOf(newNodes), costs.get(target), problem));
    }

    private static Map<String, String> cheapestPaths(SkillGraphSnapshot graph, Set<String> held, Set<String> blocked,
                                                     String target, Map<String, Integer> costs) {
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Reached> frontier = new PriorityQueue<>(
                Comparator.comparingInt(Reached::cost).thenComparing(Reached::nodeId));
        for (String id : held) {
            costs.put(id, 0);
            frontier.add(new Reached(id, 0));
        }
        while (!frontier.isEmpty()) {
            Reached current = frontier.poll();
            if (current.cost() > costs.getOrDefault(current.nodeId(), Integer.MAX_VALUE)) {
                continue;
            }
            if (current.nodeId().equals(target)) {
                break;
            }
            for (String next : reachableFrom(graph, current.nodeId())) {
                if (blocked.contains(next)) {
                    continue;
                }
                boolean free = held.contains(next) || graph.portalDestination(current.nodeId()).filter(next::equals).isPresent();
                int cost = current.cost() + (free ? 0 : graph.nodes().get(next).cost());
                if (cost < costs.getOrDefault(next, Integer.MAX_VALUE)) {
                    costs.put(next, cost);
                    previous.put(next, current.nodeId());
                    frontier.add(new Reached(next, cost));
                }
            }
        }
        return previous;
    }

    private static Set<String> blockedByExclusiveChoices(SkillGraphSnapshot graph, Set<String> held) {
        Set<String> blocked = new HashSet<>();
        for (ExclusiveGroup group : graph.exclusiveGroups().values()) {
            long active = group.members().stream().filter(held::contains).count();
            if (active >= group.maxActive()) {
                group.members().stream().filter(member -> !held.contains(member)).forEach(blocked::add);
            }
        }
        return blocked;
    }

    private static Set<String> sealedOrUndiscovered(SkillGraphSnapshot graph, PlayerSkillState state,
                                                    Collection<String> pending) {
        Holdings holdings = AllocationService.holdingsAfter(graph, state, AllocationService.expand(graph, state, pending));
        Set<String> blocked = new HashSet<>();
        for (SkillNodeDefinition node : graph.nodes().values()) {
            if (!holdings.hasDiscovered(node) || holdings.amplifierSealing(node.id()).isPresent()) {
                blocked.add(node.id());
            }
        }
        return blocked;
    }

    private static List<String> reachableFrom(SkillGraphSnapshot graph, String from) {
        return graph.neighbours(from).stream().filter(neighbour -> graph.accessFrom(neighbour).contains(from)).toList();
    }
}
