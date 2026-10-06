package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RefundService {
    public record Plan(boolean ok, Optional<RejectReason> reason, String target, List<String> removed, int refund) {
        static Plan reject(RejectReason reason, String target) {
            return new Plan(false, Optional.of(reason), target, List.of(), 0);
        }

        public boolean chained() {
            return removed.size() > 1;
        }
    }

    private RefundService() {
    }

    public static Plan plan(SkillGraphSnapshot graph, PlayerSkillState state, String nodeId, boolean enabled) {
        if (!enabled) {
            return Plan.reject(RejectReason.REFUND_DISABLED, nodeId);
        }
        if (!state.isAllocated(nodeId)) {
            return Plan.reject(RejectReason.NOT_ALLOCATED, nodeId);
        }
        if (state.startingRoot().filter(nodeId::equals).isPresent()) {
            return Plan.reject(RejectReason.ROOT_NEEDS_RESET, nodeId);
        }
        Set<String> excluded = new HashSet<>(Set.of(nodeId));
        graph.portalDestination(nodeId).ifPresent(excluded::add);
        Set<String> kept = Reconciler.survivors(state, graph, excluded);
        List<String> removed = new ArrayList<>();
        removed.add(nodeId);
        int refund = state.allocations().get(nodeId).paidCost();
        for (Allocation allocation : state.orderedAllocations()) {
            if (!allocation.nodeId().equals(nodeId) && !kept.contains(allocation.nodeId())) {
                removed.add(allocation.nodeId());
                refund += allocation.paidCost();
            }
        }
        return new Plan(true, Optional.empty(), nodeId, List.copyOf(removed), refund);
    }

    public static Plan apply(SkillGraphSnapshot graph, PlayerSkillState state, String nodeId, boolean enabled) {
        Plan planned = plan(graph, state, nodeId, enabled);
        if (!planned.ok()) {
            return planned;
        }
        int refunded = 0;
        for (String id : planned.removed()) {
            refunded += state.refund(id);
        }
        state.bumpRevision();
        state.log("refunded " + planned.removed().size() + " node(s) for " + refunded + " point(s) (removed " + nodeId + ")");
        return new Plan(true, Optional.empty(), nodeId, planned.removed(), refunded);
    }
}
