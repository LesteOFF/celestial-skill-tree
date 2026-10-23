package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.lesteoff.celestialskilltree.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
