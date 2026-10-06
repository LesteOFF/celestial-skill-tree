package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundServiceTest {
    final SkillGraphSnapshot g = TestGraphs.standard().build();

    private PlayerSkillState withBuild(String... paths) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 20, 120, false);
        for (String path : paths) {
            AllocationService.BatchResult result = AllocationService.confirm(g, state, List.of(id(path)));
            assertTrue(result.ok(), "fixture build step " + path + " rejected: " + result.rejection());
        }
        return state;
    }

    @Test
    void aLeafIsRemovedAloneAndRefundsItsRecordedCost() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m2", "a/n1");
        int before = state.availablePoints();
        long rev = state.stateRevision();

        RefundService.Plan plan = RefundService.plan(g, state, id("a/n1"), true);
        assertTrue(plan.ok());
        assertFalse(plan.chained(), "nothing depends on the notable");
        assertEquals(List.of(id("a/n1")), plan.removed());
        assertEquals(2, plan.refund(), "the notable was paid at 2 points");

        RefundService.Plan done = RefundService.apply(g, state, id("a/n1"), true);
        assertEquals(plan.removed(), done.removed());
        assertEquals(2, done.refund());
        assertEquals(before + 2, state.availablePoints());
        assertEquals(List.of(id("a/hub"), id("a/m1"), id("a/m2")), state.orderedAllocations().stream()
                .map(Allocation::nodeId).toList());
        assertTrue(state.ledgerHolds());
        assertEquals(rev + 1, state.stateRevision());
        assertEquals(2, state.lifetimeRefunded());
    }

    @Test
    void theRemainingBuildStaysConnectedToTheStartingRoot() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m3", "a/m4", "b/m1", "b/hub");
        RefundService.Plan plan = RefundService.plan(g, state, id("a/m3"), true);
        assertTrue(plan.ok());
        assertTrue(plan.chained());
        assertEquals(List.of(id("a/m3"), id("a/m4"), id("b/m1"), id("b/hub")), plan.removed(),
                "the dependent chain is listed oldest acquisition first, after the requested node");
        assertEquals(4, plan.refund(), "four minors/roots paid 1 point each");

        RefundService.apply(g, state, id("a/m3"), true);
        assertEquals(List.of(id("a/hub"), id("a/m1")), state.orderedAllocations().stream().map(Allocation::nodeId).toList());
        assertEquals(id("a/hub"), state.startingRoot().orElseThrow());
        assertTrue(state.ledgerHolds());
        Reconciler.Report report = Reconciler.reconcile(state, g, Map.of());
        assertFalse(report.changed(), "the build left behind is already valid");
    }

    @Test
    void dependentsThroughRequirementsAreRemovedEvenWhenStillConnected() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m2", "a/n1", "a/req_all");
        RefundService.Plan plan = RefundService.plan(g, state, id("a/n1"), true);
        assertEquals(List.of(id("a/n1"), id("a/req_all")), plan.removed());
        assertEquals(3, plan.refund());
        RefundService.apply(g, state, id("a/n1"), true);
        assertFalse(state.isAllocated(id("a/req_all")));
        assertTrue(state.isAllocated(id("a/m1")));
        assertTrue(state.ledgerHolds());
    }

    @Test
    void refundUsesTheRecordedPriceNotTheCurrentOne() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m2");
        assertEquals(1, state.allocations().get(id("a/m2")).paidCost());
        TestGraphs expensive = TestGraphs.standard();
        expensive.node("a/m2", "a", "minor", 7);
        SkillGraphSnapshot repriced = expensive.build();
        assertEquals(7, repriced.nodes().get(id("a/m2")).cost(), "the datapack now asks 7 points");

        int before = state.availablePoints();
        RefundService.Plan done = RefundService.apply(repriced, state, id("a/m2"), true);
        assertEquals(1, done.refund(), "a price increase must not pay out points the player never spent");
        assertEquals(before + 1, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void theStartingRootNeedsAFullReset() {
        PlayerSkillState state = withBuild("a/hub", "a/m1");
        RefundService.Plan plan = RefundService.plan(g, state, id("a/hub"), true);
        assertFalse(plan.ok());
        assertEquals(RejectReason.ROOT_NEEDS_RESET, plan.reason().orElseThrow());
        assertEquals(2, state.allocations().size(), "a rejected plan changes nothing");
    }

    @Test
    void everyRemovalIsRefundedExactlyOnceAndDisabledRefundsDoNothing() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m2");
        int before = state.availablePoints();
        assertTrue(RefundService.apply(g, state, id("a/m2"), true).ok());
        assertEquals(before + 1, state.availablePoints());
        RefundService.Plan replay = RefundService.apply(g, state, id("a/m2"), true);
        assertFalse(replay.ok());
        assertEquals(RejectReason.NOT_ALLOCATED, replay.reason().orElseThrow());
        assertEquals(before + 1, state.availablePoints(), "a replayed refund credits nothing");

        RefundService.Plan off = RefundService.apply(g, state, id("a/m1"), false);
        assertEquals(RejectReason.REFUND_DISABLED, off.reason().orElseThrow());
        assertTrue(state.isAllocated(id("a/m1")));
        assertTrue(state.ledgerHolds());
    }

    @Test
    void aRefundedKeystoneFreesItsExclusiveSlotAndIsRepurchasedAtTheCurrentPrice() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m2", "a/k1");
        assertEquals(RejectReason.EXCLUSIVE_CONFLICT,
                AllocationService.validate(g, state, List.of(id("a/k2"))).rejection().orElseThrow().reason());
        RefundService.apply(g, state, id("a/k1"), true);
        AllocationService.BatchResult result = AllocationService.confirm(g, state, List.of(id("a/k2")));
        assertTrue(result.ok(), "the vow slot is free again");
        assertEquals(3, result.totalCost(), "the new keystone is paid at its current price");
        assertTrue(state.ledgerHolds());
        assertEquals(3, state.lifetimeRefunded());
    }

    @Test
    void refundingTheWholeBuildBottomUpLeavesOnlyTheFreeRoot() {
        PlayerSkillState state = withBuild("a/hub", "a/m1", "a/m2", "a/n1");
        int spent = state.activeSpentPoints();
        int before = state.availablePoints();
        RefundService.apply(g, state, id("a/m1"), true);
        assertEquals(List.of(id("a/hub")), state.orderedAllocations().stream().map(Allocation::nodeId).toList());
        assertEquals(before + spent, state.availablePoints());
        assertEquals(0, state.activeSpentPoints());
        assertEquals(id("a/hub"), state.startingRoot().orElseThrow(), "the free root is kept; only a reset replaces it");
        assertTrue(state.ledgerHolds());
    }
}
