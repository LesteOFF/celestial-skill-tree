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
        PlayerSkillState state = new PlayerSkillState();
        for (int i = 0; i < 5; i++) {
            ProgressionService.purchasePoint(state, 1_000_000, ProgressionService.Settings.DEFAULT);
            assertTrue(state.ledgerHolds());
        }
        AllocationService.confirm(g, state, List.of(id("a/hub"), id("a/m1"), id("a/m2"), id("a/n1")));
        assertTrue(state.ledgerHolds());
        assertEquals(4, state.activeSpentPoints());
        assertEquals(1, state.availablePoints());
        LedgerService.grant(state, 3, 120, false);
        assertTrue(state.ledgerHolds());
        LedgerService.setAvailable(state, 0, 120, false);
        assertTrue(state.ledgerHolds());
        assertEquals(5, state.purchasedPoints(), "admin operations never touch the purchase counter");
        RespecService.Preview preview = RespecService.resetAll(state);
        assertEquals(4, preview.refund());
        assertEquals(4, state.availablePoints());
        assertTrue(state.ledgerHolds());
        assertTrue(state.allocations().isEmpty());
        assertTrue(state.startingRoot().isEmpty(), "a new free root may be chosen");
    }

    @Test
    void resetCannotMultiplyPoints() {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 10, 120, false);
        AllocationService.confirm(g, state, List.of(id("a/hub"), id("a/m1"), id("a/m2")));
        RespecService.resetAll(state);
        RespecService.resetAll(state);
        assertEquals(10, state.availablePoints());
        AllocationService.confirm(g, state, List.of(id("b/hub"), id("b/m2")));
        assertEquals(0, state.allocations().get(id("b/hub")).paidCost(), "new starting root is free again");
        assertEquals(9, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void grantsAboveTheLimitNeedAnExplicitOverride() {
        PlayerSkillState state = new PlayerSkillState();
        assertEquals(RejectReason.OVER_LIMIT_NEEDS_OVERRIDE, LedgerService.grant(state, 121, 120, false).reason().orElseThrow());
        assertTrue(LedgerService.grant(state, 121, 120, true).ok());
        assertEquals(RejectReason.INVALID_AMOUNT, LedgerService.setAvailable(state, -1, 120, true).reason().orElseThrow());
        assertEquals(RejectReason.INVALID_AMOUNT, LedgerService.grant(state, -500, 120, true).reason().orElseThrow());
    }

    @Test
    void setAvailablePreservesRecordedPurchaseCosts() {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 5, 120, false);
        AllocationService.confirm(g, state, List.of(id("a/hub"), id("a/m1"), id("a/m2")));
        LedgerService.setAvailable(state, 10, 120, false);
        assertEquals(10, state.availablePoints());
        assertEquals(2, state.activeSpentPoints());
        assertEquals(12, state.netGrantedPoints());
        assertTrue(state.ledgerHolds());
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
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 6, 120, false);
        AllocationService.confirm(g, state, List.of(id("a/hub"), id("a/m1"), id("a/m2"), id("a/n1")));
        PlayerSkillState c = state.copy();
        assertEquals(state.allocations(), c.allocations());
        assertEquals(state.availablePoints(), c.availablePoints());
        assertEquals(state.stateRevision(), c.stateRevision());
        assertEquals(state.startingRoot(), c.startingRoot());
        assertFalse(c.allocations().isEmpty());
    }
}
