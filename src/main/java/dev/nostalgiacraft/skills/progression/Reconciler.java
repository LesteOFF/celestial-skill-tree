package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Reconciler {
    public record Report(boolean changed, List<String> migrated, List<String> removed, int refunded) {
    }

    private Reconciler() {
    }

    public static Report reconcile(PlayerSkillState state, SkillGraphSnapshot graph, Map<String, String> migrations) {
        List<String> migrated = migrate(state, graph, migrations);
        Set<String> kept = survivors(state, graph, Set.of());
        List<String> removed = new ArrayList<>();
        int refunded = 0;
        for (Allocation allocation : state.orderedAllocations()) {
            if (!kept.contains(allocation.nodeId())) {
                refunded += state.refund(allocation.nodeId());
                removed.add(allocation.nodeId());
            }
        }
        if (kept.isEmpty()) {
            state.setStartingRoot(null);
        }
        boolean changed = !migrated.isEmpty() || !removed.isEmpty();
        if (changed) {
            state.bumpRevision();
            state.log("reconciled: migrated " + migrated.size() + ", removed " + removed.size() + ", refunded " + refunded);
        }
        state.setReconciledFingerprint(graph.fingerprint());
        return new Report(changed, migrated, removed, refunded);
    }

    private static List<String> migrate(PlayerSkillState state, SkillGraphSnapshot graph, Map<String, String> migrations) {
        List<String> migrated = new ArrayList<>();
        for (Allocation allocation : state.orderedAllocations()) {
            String source = allocation.nodeId();
            String target = migrations.get(source);
            if (!graph.contains(source) && target != null && graph.contains(target) && !state.isAllocated(target)) {
                String marker = "migrate:" + source + "->" + target;
                state.rename(source, target);
                state.addMarker(marker);
                migrated.add(marker);
            }
        }
        return migrated;
    }

    public static Set<String> survivors(PlayerSkillState state, SkillGraphSnapshot graph, Set<String> excluded) {
        Optional<String> start = validStartingRoot(state, graph).filter(root -> !excluded.contains(root));
        if (start.isEmpty()) {
            return Set.of();
        }
        Holdings kept = Holdings.replaying(graph);
        kept.add(start.get(), state.allocations().get(start.get()).paidCost());
        List<Allocation> candidates = state.orderedAllocations().stream()
                .filter(allocation -> !excluded.contains(allocation.nodeId()) && graph.contains(allocation.nodeId()))
                .toList();
        int firstUnkept = 0;
        boolean keptAnother = true;
        while (keptAnother) {
            while (firstUnkept < candidates.size() && kept.contains(candidates.get(firstUnkept).nodeId())) {
                firstUnkept++;
            }
            keptAnother = keepOldestValid(graph, candidates, firstUnkept, kept);
        }
        return kept.nodeIds();
    }

    private static Optional<String> validStartingRoot(PlayerSkillState state, SkillGraphSnapshot graph) {
        return state.startingRoot()
                .filter(state::isAllocated)
                .filter(root -> graph.node(root).filter(node -> node.isRoot()).isPresent());
    }

    private static boolean keepOldestValid(SkillGraphSnapshot graph, List<Allocation> candidates, int from, Holdings kept) {
        for (int index = from; index < candidates.size(); index++) {
            Allocation allocation = candidates.get(index);
            String id = allocation.nodeId();
            if (!kept.contains(id) && AllocationService.rejectionOf(graph, graph.nodes().get(id), kept).isEmpty()) {
                kept.add(id, allocation.paidCost());
                return true;
            }
        }
        return false;
    }
}
