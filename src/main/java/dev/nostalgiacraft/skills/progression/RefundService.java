package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Individual-node refunds (the "remove this skill" operation), as opposed to the full reset in
 * {@link RespecService}.
 * <p>
 * A single node is removed only when the remaining build stays valid and connected to the starting root. When other
 * allocations depend on it, the plan lists that dependent chain so the interface can show the resulting build, its
 * refund and its size before anything is confirmed. The chain is computed with exactly the rules used after a
 * datapack change ({@link Reconciler#survivors}), so removal and reconciliation can never disagree.
 * <p>
 * Refunds always return the recorded paid costs, never current JSON prices, and a removed allocation is refunded
 * exactly once: {@link PlayerSkillState#refund(String)} drops the allocation in the same step that credits the
 * points, so a replayed request finds nothing to remove.
 * <p>
 * The starting root is never refundable on its own (it is free and the whole build hangs from it); choosing a new
 * root is a full reset.
 */
public final class RefundService {
    /**
     * A removal that may be confirmed. {@code removed} is ordered oldest acquisition first and always starts with
     * the requested node; {@code refund} is the sum of the recorded paid costs of those allocations.
     */
    public record Plan(boolean ok, Optional<RejectReason> reason, String target, List<String> removed, int refund) {
        static Plan reject(RejectReason reason, String target) {
            return new Plan(false, Optional.of(reason), target, List.of(), 0);
        }

        /** True when other allocations must go with the requested node. */
        public boolean chained() {
            return removed.size() > 1;
        }
    }

    private RefundService() {
    }

    /** Computes the removal without touching the state (used for the server check and the client preview). */
    public static Plan plan(SkillGraphSnapshot g, PlayerSkillState s, String nodeId, boolean enabled) {
        if (!enabled) {
            return Plan.reject(RejectReason.REFUND_DISABLED, nodeId);
        }
        if (!s.isAllocated(nodeId)) {
            return Plan.reject(RejectReason.NOT_ALLOCATED, nodeId);
        }
        if (s.startingRoot().filter(nodeId::equals).isPresent()) {
            return Plan.reject(RejectReason.ROOT_NEEDS_RESET, nodeId);
        }
        Set<String> kept = Reconciler.survivors(s, g, Set.of(nodeId));
        List<String> removed = new ArrayList<>();
        removed.add(nodeId);
        int refund = s.allocations().get(nodeId).paidCost();
        for (Allocation a : s.orderedAllocations()) {
            if (!a.nodeId().equals(nodeId) && !kept.contains(a.nodeId())) {
                removed.add(a.nodeId());
                refund += a.paidCost();
            }
        }
        return new Plan(true, Optional.empty(), nodeId, List.copyOf(removed), refund);
    }

    /**
     * Applies the removal atomically: re-plans first (so a stale plan can never be applied), then refunds the
     * recorded costs of every removed allocation and bumps the player-state revision.
     */
    public static Plan apply(SkillGraphSnapshot g, PlayerSkillState s, String nodeId, boolean enabled) {
        Plan p = plan(g, s, nodeId, enabled);
        if (!p.ok()) {
            return p;
        }
        int refunded = 0;
        for (String id : p.removed()) {
            refunded += s.refund(id);
        }
        s.bumpRevision();
        s.log("refunded " + p.removed().size() + " node(s) for " + refunded + " point(s) (removed " + nodeId + ")");
        return new Plan(true, Optional.empty(), nodeId, p.removed(), refunded);
    }
}
