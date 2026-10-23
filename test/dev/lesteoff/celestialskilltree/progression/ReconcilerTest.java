package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.TestGraphs;
import org.junit.jupiter.api.Test;
import static dev.lesteoff.celestialskilltree.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ReconcilerTest {
    static PlayerSkillState build(SkillGraphSnapshot graph, String... ids) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 30, 120, false);
        List<String> req = new ArrayList<>(List.of(id("a/hub")));
        for (String i : ids) {
            req.add(id(i));
        }
        assertTrue(AllocationService.confirm(graph, state, req).ok());
        return state;
    }

    @Test
    void removedNodeRefundsRecordedCostAndCascades() {
        SkillGraphSnapshot graph = TestGraphs.standard().build();
        PlayerSkillState state = build(graph, "a/m1", "a/m2", "a/n1", "a/k1");
        TestGraphs testGraphs = new TestGraphs().sector("a").sector("b");
        testGraphs.minor("a/m1", "a").notable("a/n1", "a").keystone("a/k1", "a", null).minor("a/m3", "a").minor("a/m4", "a")
                .minor("b/m1", "b").minor("b/m2", "b");
        testGraphs.chain("a/hub", "a/m1", "a/m3", "a/m4", "b/m1", "b/hub", "b/m2");
        testGraphs.edge("a/m3", "a/n1");
        testGraphs.edge("a/m3", "a/k1");
        SkillGraphSnapshot g2 = testGraphs.build();
        int before = state.availablePoints();
        Reconciler.Report report = Reconciler.reconcile(state, g2, Map.of());
        assertEquals(List.of(id("a/m2"), id("a/n1"), id("a/k1")), report.removed());
        assertEquals(1 + 2 + 3, report.refunded());
        assertEquals(before + 6, state.availablePoints());
        assertTrue(state.ledgerHolds());
        Reconciler.Report again = Reconciler.reconcile(state, g2, Map.of());
        assertFalse(again.changed(), "refunds happen exactly once");
    }
}
