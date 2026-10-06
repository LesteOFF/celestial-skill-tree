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
        PlayerSkillState s = new PlayerSkillState();
        LedgerService.grant(s, 20, 120, false);
        for (String path : paths) {
            AllocationService.BatchResult r = AllocationService.confirm(g, s, List.of(id(path)));
            assertTrue(r.ok(), "fixture build step " + path + " rejected: " + r.rejection());
        }
        return s;
    }

    @Test
    void aLeafIsRemovedAloneAndRefundsItsRecordedCost() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m2", "a/n1");
        int before = s.availablePoints();
        long rev = s.stateRevision();

        RefundService.Plan plan = RefundService.plan(g, s, id("a/n1"), true);
        assertTrue(plan.ok());
        assertFalse(plan.chained(), "nothing depends on the notable");
        assertEquals(List.of(id("a/n1")), plan.removed());
        assertEquals(2, plan.refund(), "the notable was paid at 2 points");

        RefundService.Plan done = RefundService.apply(g, s, id("a/n1"), true);
        assertEquals(plan.removed(), done.removed());
        assertEquals(2, done.refund());
        assertEquals(before + 2, s.availablePoints());
        assertEquals(List.of(id("a/hub"), id("a/m1"), id("a/m2")), s.orderedAllocations().stream()
                .map(Allocation::nodeId).toList());
        assertTrue(s.ledgerHolds());
        assertEquals(rev + 1, s.stateRevision());
        assertEquals(2, s.lifetimeRefunded());
    }

    @Test
    void theRemainingBuildStaysConnectedToTheStartingRoot() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m3", "a/m4", "b/m1", "b/hub");
        // a/m3 carries the corridor to sector b: removing it takes the whole branch behind it.
        RefundService.Plan plan = RefundService.plan(g, s, id("a/m3"), true);
        assertTrue(plan.ok());
        assertTrue(plan.chained());
        assertEquals(List.of(id("a/m3"), id("a/m4"), id("b/m1"), id("b/hub")), plan.removed(),
                "the dependent chain is listed oldest acquisition first, after the requested node");
        assertEquals(4, plan.refund(), "four minors/roots paid 1 point each");

        RefundService.apply(g, s, id("a/m3"), true);
        assertEquals(List.of(id("a/hub"), id("a/m1")), s.orderedAllocations().stream().map(Allocation::nodeId).toList());
        assertEquals(id("a/hub"), s.startingRoot().orElseThrow());
        assertTrue(s.ledgerHolds());
        // the result is exactly what a reload would keep: reconciliation finds nothing more to do
        Reconciler.Report r = Reconciler.reconcile(s, g, Map.of());
        assertFalse(r.changed(), "the build left behind is already valid");
    }

    @Test
    void dependentsThroughRequirementsAreRemovedEvenWhenStillConnected() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m2", "a/n1", "a/req_all");
        // a/req_all hangs off a/m1 but needs a/n1; it must go with it.
        RefundService.Plan plan = RefundService.plan(g, s, id("a/n1"), true);
        assertEquals(List.of(id("a/n1"), id("a/req_all")), plan.removed());
        assertEquals(3, plan.refund());
        RefundService.apply(g, s, id("a/n1"), true);
        assertFalse(s.isAllocated(id("a/req_all")));
        assertTrue(s.isAllocated(id("a/m1")));
        assertTrue(s.ledgerHolds());
    }

    @Test
    void refundUsesTheRecordedPriceNotTheCurrentOne() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m2");
        assertEquals(1, s.allocations().get(id("a/m2")).paidCost());
        TestGraphs expensive = TestGraphs.standard();
        expensive.node("a/m2", "a", "minor", 7);
        SkillGraphSnapshot repriced = expensive.build();
        assertEquals(7, repriced.nodes().get(id("a/m2")).cost(), "the datapack now asks 7 points");

        int before = s.availablePoints();
        RefundService.Plan done = RefundService.apply(repriced, s, id("a/m2"), true);
        assertEquals(1, done.refund(), "a price increase must not pay out points the player never spent");
        assertEquals(before + 1, s.availablePoints());
        assertTrue(s.ledgerHolds());
    }

    @Test
    void theStartingRootNeedsAFullReset() {
        PlayerSkillState s = withBuild("a/hub", "a/m1");
        RefundService.Plan plan = RefundService.plan(g, s, id("a/hub"), true);
        assertFalse(plan.ok());
        assertEquals(RejectReason.ROOT_NEEDS_RESET, plan.reason().orElseThrow());
        assertEquals(2, s.allocations().size(), "a rejected plan changes nothing");
    }

    @Test
    void everyRemovalIsRefundedExactlyOnceAndDisabledRefundsDoNothing() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m2");
        int before = s.availablePoints();
        assertTrue(RefundService.apply(g, s, id("a/m2"), true).ok());
        assertEquals(before + 1, s.availablePoints());
        RefundService.Plan replay = RefundService.apply(g, s, id("a/m2"), true);
        assertFalse(replay.ok());
        assertEquals(RejectReason.NOT_ALLOCATED, replay.reason().orElseThrow());
        assertEquals(before + 1, s.availablePoints(), "a replayed refund credits nothing");

        RefundService.Plan off = RefundService.apply(g, s, id("a/m1"), false);
        assertEquals(RejectReason.REFUND_DISABLED, off.reason().orElseThrow());
        assertTrue(s.isAllocated(id("a/m1")));
        assertTrue(s.ledgerHolds());
    }

    @Test
    void aRefundedKeystoneFreesItsExclusiveSlotAndIsRepurchasedAtTheCurrentPrice() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m2", "a/k1");
        assertEquals(RejectReason.EXCLUSIVE_CONFLICT,
                AllocationService.validate(g, s, List.of(id("a/k2"))).rejection().orElseThrow().reason());
        RefundService.apply(g, s, id("a/k1"), true);
        AllocationService.BatchResult r = AllocationService.confirm(g, s, List.of(id("a/k2")));
        assertTrue(r.ok(), "the vow slot is free again");
        assertEquals(3, r.totalCost(), "the new keystone is paid at its current price");
        assertTrue(s.ledgerHolds());
        assertEquals(3, s.lifetimeRefunded());
    }

    @Test
    void refundingTheWholeBuildBottomUpLeavesOnlyTheFreeRoot() {
        PlayerSkillState s = withBuild("a/hub", "a/m1", "a/m2", "a/n1");
        int spent = s.activeSpentPoints();
        int before = s.availablePoints();
        RefundService.apply(g, s, id("a/m1"), true);
        assertEquals(List.of(id("a/hub")), s.orderedAllocations().stream().map(Allocation::nodeId).toList());
        assertEquals(before + spent, s.availablePoints());
        assertEquals(0, s.activeSpentPoints());
        assertEquals(id("a/hub"), s.startingRoot().orElseThrow(), "the free root is kept; only a reset replaces it");
        assertTrue(s.ledgerHolds());
    }
}
