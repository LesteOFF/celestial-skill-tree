package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.ExclusiveGroup;
import dev.nostalgiacraft.skills.graph.RequirementCheck;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Batch allocation rules shared by the server (authoritative) and the client (preview only).
 * A batch is accepted when some purchase order exists: starting from the confirmed build, eligible nodes are
 * accepted repeatedly until nothing changes. Any node left over rejects the whole batch.
 */
public final class AllocationService {
    public static final int MAX_BATCH = 512;

    public record Accepted(String nodeId, int cost, boolean startingRoot) {
    }

    public record Rejection(RejectReason reason, String nodeId, String detail) {
    }

    /** Outcome of an expansion; {@code rejected} lists nodes that could not be placed, with their reasons. */
    public record Expansion(List<Accepted> accepted, Map<String, Rejection> rejected) {
        public int totalCost() {
            return accepted.stream().mapToInt(Accepted::cost).sum();
        }
    }

    public record BatchResult(boolean ok, List<Accepted> accepted, int totalCost, Optional<Rejection> rejection) {
        static BatchResult reject(RejectReason reason, String nodeId, String detail) {
            return new BatchResult(false, List.of(), 0, Optional.of(new Rejection(reason, nodeId, detail)));
        }
    }

    private AllocationService() {
    }

    /** Validates a full batch including costs. Does not mutate the state. */
    public static BatchResult validate(SkillGraphSnapshot g, PlayerSkillState state, Collection<String> request) {
        if (request.isEmpty()) {
            return BatchResult.reject(RejectReason.EMPTY_REQUEST, "", "");
        }
        if (request.size() > MAX_BATCH) {
            return BatchResult.reject(RejectReason.REQUEST_TOO_LARGE, "", String.valueOf(request.size()));
        }
        Set<String> ids = new TreeSet<>(request);
        for (String id : ids) {
            if (!g.contains(id)) {
                return BatchResult.reject(RejectReason.UNKNOWN_NODE, id, "");
            }
            if (state.isAllocated(id)) {
                return BatchResult.reject(RejectReason.ALREADY_ALLOCATED, id, "");
            }
        }
        if (state.startingRoot().isEmpty()) {
            long roots = ids.stream().filter(id -> g.nodes().get(id).isRoot()).count();
            if (roots == 0) {
                return BatchResult.reject(RejectReason.STARTING_ROOT_REQUIRED, ids.iterator().next(), "");
            }
            if (roots > 1) {
                return BatchResult.reject(RejectReason.MULTIPLE_STARTING_ROOTS, "", String.valueOf(roots));
            }
        }
        Expansion ex = expand(g, state, ids);
        if (!ex.rejected().isEmpty()) {
            Rejection first = ex.rejected().values().iterator().next();
            return new BatchResult(false, List.of(), 0, Optional.of(first));
        }
        int total = ex.totalCost();
        if (total > state.availablePoints()) {
            return BatchResult.reject(RejectReason.INSUFFICIENT_POINTS, "", total + "/" + state.availablePoints());
        }
        return new BatchResult(true, ex.accepted(), total, Optional.empty());
    }

    /**
     * Applies a previously validated batch atomically: debit, allocations with recorded prices, revision bump.
     * Re-validates first so a stale result can never be applied.
     */
    public static BatchResult confirm(SkillGraphSnapshot g, PlayerSkillState state, Collection<String> request) {
        BatchResult r = validate(g, state, request);
        if (!r.ok()) {
            return r;
        }
        for (Accepted a : r.accepted()) {
            if (a.startingRoot()) {
                state.setStartingRoot(a.nodeId());
            }
            state.allocate(a.nodeId(), a.cost());
        }
        state.bumpRevision();
        state.log("allocated " + r.accepted().size() + " node(s) for " + r.totalCost() + " point(s)");
        return r;
    }

    /** Places as many requested nodes as possible in a valid order, ignoring point costs. */
    public static Expansion expand(SkillGraphSnapshot g, PlayerSkillState state, Collection<String> request) {
        Set<String> held = new LinkedHashSet<>(state.allocations().keySet());
        Map<String, Integer> sectorPoints = new HashMap<>();
        for (Allocation a : state.allocations().values()) {
            g.node(a.nodeId()).ifPresent(n -> sectorPoints.merge(n.sector(), a.paidCost(), Integer::sum));
        }
        boolean needStart = state.startingRoot().isEmpty();
        Set<String> remaining = new TreeSet<>(request);
        remaining.removeAll(held);
        List<Accepted> accepted = new ArrayList<>();
        boolean progress = true;
        while (progress && !remaining.isEmpty()) {
            progress = false;
            for (String id : List.copyOf(remaining)) {
                SkillNodeDefinition n = g.nodes().get(id);
                if (n == null) {
                    continue;
                }
                boolean asStart = needStart && n.isRoot();
                if (asStart || reason(g, n, held, sectorPoints) == null) {
                    int cost = asStart ? 0 : n.cost();
                    accepted.add(new Accepted(id, cost, asStart));
                    held.add(id);
                    sectorPoints.merge(n.sector(), cost, Integer::sum);
                    remaining.remove(id);
                    if (asStart) {
                        needStart = false;
                    }
                    progress = true;
                }
            }
        }
        Map<String, Rejection> rejected = new java.util.LinkedHashMap<>();
        for (String id : remaining) {
            SkillNodeDefinition n = g.nodes().get(id);
            if (n == null) {
                rejected.put(id, new Rejection(RejectReason.UNKNOWN_NODE, id, ""));
            } else {
                Rejection why = reason(g, n, held, sectorPoints);
                rejected.put(id, why != null ? why : new Rejection(RejectReason.NOT_CONNECTED, id, ""));
            }
        }
        return new Expansion(List.copyOf(accepted), rejected);
    }

    /** Why {@code n} cannot be allocated on top of {@code held}; null when it can. Cost is not considered. */
    public static Rejection reason(SkillGraphSnapshot g, SkillNodeDefinition n, Set<String> held,
                                   Map<String, Integer> sectorPoints) {
        Optional<ExclusiveGroup> group = g.groupOf(n.id());
        if (group.isPresent()) {
            List<String> active = group.get().members().stream().filter(held::contains).toList();
            if (active.size() >= group.get().maxActive()) {
                return new Rejection(RejectReason.EXCLUSIVE_CONFLICT, n.id(), active.get(0));
            }
        }
        boolean connected = g.accessFrom(n.id()).stream().anyMatch(held::contains);
        if (!connected) {
            return new Rejection(RejectReason.NOT_CONNECTED, n.id(), "");
        }
        String unmet = RequirementCheck.firstUnmet(g, n, held, sectorPoints.getOrDefault(n.sector(), 0));
        if (unmet != null) {
            return new Rejection(RejectReason.REQUIREMENT_UNMET, n.id(), unmet);
        }
        return null;
    }

    /** Convenience for UI state: may this node be added on top of confirmed + pending? (no cost check) */
    public static Rejection eligibility(SkillGraphSnapshot g, PlayerSkillState state, Collection<String> pending,
                                        String id) {
        SkillNodeDefinition n = g.nodes().get(id);
        if (n == null) {
            return new Rejection(RejectReason.UNKNOWN_NODE, id, "");
        }
        if (state.isAllocated(id)) {
            return new Rejection(RejectReason.ALREADY_ALLOCATED, id, "");
        }
        Expansion ex = expand(g, state, pending);
        boolean startTaken = state.startingRoot().isPresent() || ex.accepted().stream().anyMatch(Accepted::startingRoot);
        if (!startTaken) {
            return n.isRoot() ? null : new Rejection(RejectReason.STARTING_ROOT_REQUIRED, id, "");
        }
        Set<String> held = new LinkedHashSet<>(state.allocations().keySet());
        Map<String, Integer> sectorPoints = new HashMap<>();
        for (Allocation a : state.allocations().values()) {
            g.node(a.nodeId()).ifPresent(d -> sectorPoints.merge(d.sector(), a.paidCost(), Integer::sum));
        }
        for (Accepted a : ex.accepted()) {
            held.add(a.nodeId());
            sectorPoints.merge(g.nodes().get(a.nodeId()).sector(), a.cost(), Integer::sum);
        }
        held.remove(id);
        return reason(g, n, held, sectorPoints);
    }

    /**
     * Pending nodes that stop being placeable when {@code removed} leaves the pending selection
     * (the "dependent chain" offered for removal together with it).
     */
    public static Set<String> dependentsOfPendingRemoval(SkillGraphSnapshot g, PlayerSkillState state,
                                                        Collection<String> pending, String removed) {
        Set<String> rest = new TreeSet<>(pending);
        rest.remove(removed);
        return new TreeSet<>(expand(g, state, rest).rejected().keySet());
    }
}
