package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.lesteoff.celestialskilltree.graph.TestGraphs.id;
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
}
