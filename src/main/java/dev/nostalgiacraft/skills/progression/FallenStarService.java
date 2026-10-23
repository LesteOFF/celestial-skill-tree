package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.List;
import java.util.Optional;

public final class FallenStarService {
    public enum Recovery { RELEARNED, POINTS_RETURNED, POINTS_TAKEN, REJECTED }

    private FallenStarService() {
    }

    public static Optional<Allocation> dropNewest(PlayerSkillState state, SkillGraphSnapshot graph) {
        Optional<Allocation> dropped = newestDroppable(state, graph);
        dropped.ifPresent(allocation -> drop(state, allocation));
        return dropped;
    }

    public static Optional<Allocation> newestDroppable(PlayerSkillState state, SkillGraphSnapshot graph) {
        return state.orderedAllocations().reversed().stream().filter(allocation -> standsAlone(state, graph, allocation)).findFirst();
    }

    public static void drop(PlayerSkillState state, Allocation allocation) {
        state.forfeit(allocation.nodeId());
        state.bumpRevision();
        state.log("dropped " + allocation.nodeId() + " as a fallen star worth " + allocation.paidCost());
    }

    private static boolean standsAlone(PlayerSkillState state, SkillGraphSnapshot graph, Allocation allocation) {
        RefundService.Plan plan = RefundService.plan(graph, state, allocation.nodeId(), true);
        return plan.ok() && !plan.chained();
    }

    public static Recovery recoverOwn(PlayerSkillState state, SkillGraphSnapshot graph, String nodeId, int paidCost) {
        Optional<SkillNodeDefinition> node = graph.node(nodeId).filter(found -> !state.isAllocated(nodeId));
        boolean placeable = node.filter(found -> AllocationService.rejectionOf(graph, found, Holdings.of(graph, state)).isEmpty()).isPresent();
        if (placeable) {
            state.reclaim(nodeId, paidRank(node.get(), paidCost), paidCost);
        } else {
            state.adjustGranted(paidCost);
        }
        state.bumpRevision();
        state.log("recovered the fallen star " + nodeId + (placeable ? " as a skill" : " as " + paidCost + " point(s)"));
        return placeable ? Recovery.RELEARNED : Recovery.POINTS_RETURNED;
    }

    static int paidRank(SkillNodeDefinition node, int paidCost) {
        return node.cost() <= 0 ? SkillNodeDefinition.SINGLE_RANK
                : Math.clamp(paidCost / node.cost(), SkillNodeDefinition.SINGLE_RANK, Math.max(SkillNodeDefinition.SINGLE_RANK, node.maxRank()));
    }

    public static Recovery claimForeign(PlayerSkillState state, int paidCost, int pointLimit) {
        return LedgerService.grant(state, paidCost, pointLimit, false).ok() ? Recovery.POINTS_TAKEN : Recovery.REJECTED;
    }
}
