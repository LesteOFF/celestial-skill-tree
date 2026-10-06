package dev.nostalgiacraft.skills.progression;

import com.google.gson.JsonObject;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;
import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
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
    void unchangedGraphIsANoOp() {
        SkillGraphSnapshot graph = TestGraphs.standard().build();
        PlayerSkillState state = build(graph, "a/m1", "a/m2");
        long rev = state.stateRevision();
        Reconciler.Report report = Reconciler.reconcile(state, graph, Map.of());
        assertFalse(report.changed());
        assertEquals(rev, state.stateRevision());
        assertEquals(graph.fingerprint(), state.reconciledFingerprint());
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

    @Test
    void refundUsesRecordedPriceAfterACostChange() {
        SkillGraphSnapshot graph = TestGraphs.standard().build();
        PlayerSkillState state = build(graph, "a/m1", "a/m2", "a/n1");
        TestGraphs testGraphs = TestGraphs.standard();
        JsonObject json = testGraphs.node("a/n1", "a", "notable", 9);
        testGraphs.requires("a/n1", List.of("t:a/m3"), List.of(), 0);
        json.addProperty("cost", 9);
        Reconciler.Report report = Reconciler.reconcile(state, testGraphs.build(), Map.of());
        assertEquals(List.of(id("a/n1")), report.removed());
        assertEquals(2, report.refunded(), "refund what was paid, not the new price");
    }

    @Test
    void migrationKeepsPriceAndOrderExactlyOnce() {
        SkillGraphSnapshot graph = TestGraphs.standard().build();
        PlayerSkillState state = build(graph, "a/m1", "a/m2", "a/n1");
        Allocation old = state.allocations().get(id("a/n1"));
        TestGraphs testGraphs = new TestGraphs().sector("a").sector("b");
        testGraphs.minor("a/m1", "a").minor("a/m2", "a").notable("a/n1_renamed", "a").minor("a/m3", "a").minor("a/m4", "a")
                .minor("b/m1", "b").minor("b/m2", "b");
        testGraphs.chain("a/hub", "a/m1", "a/m2", "a/n1_renamed");
        testGraphs.chain("a/m1", "a/m3", "a/m4", "b/m1", "b/hub", "b/m2");
        Reconciler.Report report = Reconciler.reconcile(state, testGraphs.build(), Map.of(id("a/n1"), id("a/n1_renamed")));
        assertEquals(1, report.migrated().size());
        assertTrue(report.removed().isEmpty());
        Allocation now = state.allocations().get(id("a/n1_renamed"));
        assertEquals(old.paidCost(), now.paidCost());
        assertEquals(old.acquisitionIndex(), now.acquisitionIndex());
        assertTrue(state.migrationMarkers().contains("migrate:" + id("a/n1") + "->" + id("a/n1_renamed")));
    }

    @Test
    void lostStartingRootRefundsEverythingAndAllowsNewFreeRoot() {
        SkillGraphSnapshot graph = TestGraphs.standard().build();
        PlayerSkillState state = build(graph, "a/m1", "a/m3");
        TestGraphs testGraphs = new TestGraphs().sector("b");
        testGraphs.minor("b/m1", "b").minor("b/m2", "b");
        testGraphs.chain("b/m1", "b/hub", "b/m2");
        SkillGraphSnapshot g2 = testGraphs.build();
        Reconciler.Report report = Reconciler.reconcile(state, g2, Map.of());
        assertEquals(2, report.refunded());
        assertTrue(state.allocations().isEmpty());
        assertTrue(state.startingRoot().isEmpty());
        assertTrue(AllocationService.confirm(g2, state, List.of(id("b/hub"))).ok());
        assertEquals(30, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void newConflictKeepsTheOldestAcquisition() {
        TestGraphs base = TestGraphs.standard();
        base.keystone("a/k3", "a", null);
        base.edge("a/m2", "a/k3");
        SkillGraphSnapshot graph = base.build();
        PlayerSkillState state = build(graph, "a/m1", "a/m2", "a/k1");
        assertTrue(AllocationService.confirm(graph, state, List.of(id("a/k3"))).ok());
        TestGraphs changed = TestGraphs.standard();
        changed.keystone("a/k3", "a", "vow");
        changed.edge("a/m2", "a/k3");
        Reconciler.Report report = Reconciler.reconcile(state, changed.build(), Map.of());
        assertEquals(List.of(id("a/k3")), report.removed());
        assertTrue(state.isAllocated(id("a/k1")));
    }
}
