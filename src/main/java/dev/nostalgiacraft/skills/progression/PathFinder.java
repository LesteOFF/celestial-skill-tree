package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Least-cost route preview from the current (confirmed + pending) build to a target node, weighted by the point
 * cost of every new allocation. Nodes blocked by an active exclusive choice are skipped. The route is then checked
 * with the real batch rules; if requirements cannot be met along it the result says so instead of offering it.
 */
public final class PathFinder {
    public record Route(List<String> newNodes, int cost, Optional<AllocationService.Rejection> unresolved) {
        public boolean valid() {
            return unresolved.isEmpty() && !newNodes.isEmpty();
        }
    }

    private PathFinder() {
    }

    public static Optional<Route> route(SkillGraphSnapshot g, PlayerSkillState state, Collection<String> pending,
                                        String target) {
        if (!g.contains(target) || state.isAllocated(target) || pending.contains(target)) {
            return Optional.empty();
        }
        Set<String> held = new HashSet<>(state.allocations().keySet());
        held.addAll(pending);
        if (held.isEmpty()) {
            return Optional.empty();
        }
        Set<String> blocked = new HashSet<>();
        g.exclusiveGroups().values().forEach(eg -> {
            long active = eg.members().stream().filter(held::contains).count();
            if (active >= eg.maxActive()) {
                eg.members().stream().filter(m -> !held.contains(m)).forEach(blocked::add);
            }
        });
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(
                Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
        for (String h : held) {
            dist.put(h, 0);
            pq.add(Map.entry(h, 0));
        }
        while (!pq.isEmpty()) {
            Map.Entry<String, Integer> cur = pq.poll();
            String k = cur.getKey();
            if (cur.getValue() > dist.getOrDefault(k, Integer.MAX_VALUE)) {
                continue;
            }
            if (k.equals(target)) {
                break;
            }
            for (String m : forward(g, k)) {
                if (blocked.contains(m)) {
                    continue;
                }
                SkillNodeDefinition n = g.nodes().get(m);
                int w = held.contains(m) ? 0 : n.cost();
                int nd = cur.getValue() + w;
                if (nd < dist.getOrDefault(m, Integer.MAX_VALUE)) {
                    dist.put(m, nd);
                    prev.put(m, k);
                    pq.add(Map.entry(m, nd));
                }
            }
        }
        if (!dist.containsKey(target)) {
            return Optional.empty();
        }
        List<String> nodes = new ArrayList<>();
        for (String k = target; k != null && !held.contains(k); k = prev.get(k)) {
            nodes.add(k);
        }
        Collections.reverse(nodes);
        List<String> batch = new ArrayList<>(pending);
        batch.addAll(nodes);
        AllocationService.Expansion ex = AllocationService.expand(g, state, batch);
        Optional<AllocationService.Rejection> problem = ex.rejected().values().stream().findFirst();
        return Optional.of(new Route(List.copyOf(nodes), dist.get(target), problem));
    }

    private static List<String> forward(SkillGraphSnapshot g, String from) {
        List<String> out = new ArrayList<>();
        for (String m : g.neighbours(from)) {
            if (g.accessFrom(m).contains(from)) {
                out.add(m);
            }
        }
        return out;
    }
}
