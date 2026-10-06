package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerAndRespecTest {
    final SkillGraphSnapshot g = TestGraphs.standard().build();

    @Test
    void invariantHoldsThroughPurchasesAllocationsResetsAndAdminOps() {
        PlayerSkillState s = new PlayerSkillState();
        for (int i = 0; i < 5; i++) {
            ProgressionService.purchasePoint(s, 1_000_000, ProgressionService.Settings.DEFAULT);
            assertTrue(s.ledgerHolds());
        }
        AllocationService.confirm(g, s, List.of(id("a/hub"), id("a/m1"), id("a/m2"), id("a/n1")));
        assertTrue(s.ledgerHolds());
        assertEquals(4, s.activeSpentPoints());
        assertEquals(1, s.availablePoints());
        LedgerService.grant(s, 3, 120, false);
        assertTrue(s.ledgerHolds());
        LedgerService.setAvailable(s, 0, 120, false);
        assertTrue(s.ledgerHolds());
        assertEquals(5, s.purchasedPoints(), "admin operations never touch the purchase counter");
        RespecService.Preview p = RespecService.resetAll(s);
        assertEquals(4, p.refund());
        assertEquals(4, s.availablePoints());
        assertTrue(s.ledgerHolds());
        assertTrue(s.allocations().isEmpty());
        assertTrue(s.startingRoot().isEmpty(), "a new free root may be chosen");
    }

    @Test
    void resetCannotMultiplyPoints() {
        PlayerSkillState s = new PlayerSkillState();
        LedgerService.grant(s, 10, 120, false);
        AllocationService.confirm(g, s, List.of(id("a/hub"), id("a/m1"), id("a/m2")));
        RespecService.resetAll(s);
        RespecService.resetAll(s);
        assertEquals(10, s.availablePoints());
        AllocationService.confirm(g, s, List.of(id("b/hub"), id("b/m2")));
        assertEquals(0, s.allocations().get(id("b/hub")).paidCost(), "new starting root is free again");
        assertEquals(9, s.availablePoints());
        assertTrue(s.ledgerHolds());
    }

    @Test
    void grantsAboveTheLimitNeedAnExplicitOverride() {
        PlayerSkillState s = new PlayerSkillState();
        assertEquals(RejectReason.OVER_LIMIT_NEEDS_OVERRIDE, LedgerService.grant(s, 121, 120, false).reason().orElseThrow());
        assertTrue(LedgerService.grant(s, 121, 120, true).ok());
        assertEquals(RejectReason.INVALID_AMOUNT, LedgerService.setAvailable(s, -1, 120, true).reason().orElseThrow());
        assertEquals(RejectReason.INVALID_AMOUNT, LedgerService.grant(s, -500, 120, true).reason().orElseThrow());
    }

    @Test
    void setAvailablePreservesRecordedPurchaseCosts() {
        PlayerSkillState s = new PlayerSkillState();
        LedgerService.grant(s, 5, 120, false);
        AllocationService.confirm(g, s, List.of(id("a/hub"), id("a/m1"), id("a/m2")));
        LedgerService.setAvailable(s, 10, 120, false);
        assertEquals(10, s.availablePoints());
        assertEquals(2, s.activeSpentPoints());
        assertEquals(12, s.netGrantedPoints());
        assertTrue(s.ledgerHolds());
    }

    @Test
    void duplicateAndStaleRequestsAreRejected() {
        RequestGuard guard = new RequestGuard();
        UUID req = UUID.randomUUID();
        assertTrue(guard.admit(req, 3, 3, 7, 7, true).isEmpty());
        assertEquals(RejectReason.DUPLICATE_REQUEST, guard.admit(req, 3, 3, 7, 7, true).orElseThrow());
        assertEquals(RejectReason.STALE_STATE_REVISION, guard.admit(UUID.randomUUID(), 2, 3, 7, 7, true).orElseThrow());
        assertEquals(RejectReason.STALE_GRAPH_REVISION, guard.admit(UUID.randomUUID(), 3, 3, 6, 7, true).orElseThrow());
        assertTrue(guard.admit(UUID.randomUUID(), 3, 3, 6, 7, false).isEmpty(), "graph revision only matters for graph requests");
    }

    @Test
    void stateCopyAndRestorePreserveEverything() {
        PlayerSkillState s = new PlayerSkillState();
        LedgerService.grant(s, 6, 120, false);
        AllocationService.confirm(g, s, List.of(id("a/hub"), id("a/m1"), id("a/m2"), id("a/n1")));
        PlayerSkillState c = s.copy();
        assertEquals(s.allocations(), c.allocations());
        assertEquals(s.availablePoints(), c.availablePoints());
        assertEquals(s.stateRevision(), c.stateRevision());
        assertEquals(s.startingRoot(), c.startingRoot());
        assertFalse(c.allocations().isEmpty());
    }
}
