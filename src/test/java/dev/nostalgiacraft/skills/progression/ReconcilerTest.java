package dev.nostalgiacraft.skills.progression;

import com.google.gson.JsonObject;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcilerTest {
    static PlayerSkillState build(SkillGraphSnapshot g, String... ids) {
        PlayerSkillState s = new PlayerSkillState();
        LedgerService.grant(s, 30, 120, false);
        List<String> req = new java.util.ArrayList<>(List.of(id("a/hub")));
        for (String i : ids) {
            req.add(id(i));
        }
        assertTrue(AllocationService.confirm(g, s, req).ok());
        return s;
    }

    @Test
    void unchangedGraphIsANoOp() {
        SkillGraphSnapshot g = TestGraphs.standard().build();
        PlayerSkillState s = build(g, "a/m1", "a/m2");
        long rev = s.stateRevision();
        Reconciler.Report r = Reconciler.reconcile(s, g, Map.of());
        assertFalse(r.changed());
        assertEquals(rev, s.stateRevision());
        assertEquals(g.fingerprint(), s.reconciledFingerprint());
    }

    @Test
    void removedNodeRefundsRecordedCostAndCascades() {
        SkillGraphSnapshot g = TestGraphs.standard().build();
        PlayerSkillState s = build(g, "a/m1", "a/m2", "a/n1", "a/k1");
        TestGraphs t = new TestGraphs().sector("a").sector("b");
        t.minor("a/m1", "a").notable("a/n1", "a").keystone("a/k1", "a", null).minor("a/m3", "a").minor("a/m4", "a")
                .minor("b/m1", "b").minor("b/m2", "b");
        t.chain("a/hub", "a/m1", "a/m3", "a/m4", "b/m1", "b/hub", "b/m2");
        t.edge("a/m3", "a/n1");
        t.edge("a/m3", "a/k1");
        SkillGraphSnapshot g2 = t.build(); // a/m2 removed: a/n1 and a/k1 lose their connection
        int before = s.availablePoints();
        Reconciler.Report r = Reconciler.reconcile(s, g2, Map.of());
        assertEquals(List.of(id("a/m2"), id("a/n1"), id("a/k1")), r.removed());
        assertEquals(1 + 2 + 3, r.refunded());
        assertEquals(before + 6, s.availablePoints());
        assertTrue(s.ledgerHolds());
        Reconciler.Report again = Reconciler.reconcile(s, g2, Map.of());
        assertFalse(again.changed(), "refunds happen exactly once");
    }

    @Test
    void refundUsesRecordedPriceAfterACostChange() {
        SkillGraphSnapshot g = TestGraphs.standard().build();
        PlayerSkillState s = build(g, "a/m1", "a/m2", "a/n1");
        TestGraphs t = TestGraphs.standard();
        JsonObject n1 = t.node("a/n1", "a", "notable", 9); // price raised to 9 and requirement added
        t.requires("a/n1", List.of("t:a/m3"), List.of(), 0);
        n1.addProperty("cost", 9);
        Reconciler.Report r = Reconciler.reconcile(s, t.build(), Map.of());
        assertEquals(List.of(id("a/n1")), r.removed());
        assertEquals(2, r.refunded(), "refund what was paid, not the new price");
    }

    @Test
    void migrationKeepsPriceAndOrderExactlyOnce() {
        SkillGraphSnapshot g = TestGraphs.standard().build();
        PlayerSkillState s = build(g, "a/m1", "a/m2", "a/n1");
        Allocation old = s.allocations().get(id("a/n1"));
        TestGraphs t = new TestGraphs().sector("a").sector("b");
        t.minor("a/m1", "a").minor("a/m2", "a").notable("a/n1_renamed", "a").minor("a/m3", "a").minor("a/m4", "a")
                .minor("b/m1", "b").minor("b/m2", "b");
        t.chain("a/hub", "a/m1", "a/m2", "a/n1_renamed");
        t.chain("a/m1", "a/m3", "a/m4", "b/m1", "b/hub", "b/m2");
        Reconciler.Report r = Reconciler.reconcile(s, t.build(), Map.of(id("a/n1"), id("a/n1_renamed")));
        assertEquals(1, r.migrated().size());
        assertTrue(r.removed().isEmpty());
        Allocation now = s.allocations().get(id("a/n1_renamed"));
        assertEquals(old.paidCost(), now.paidCost());
        assertEquals(old.acquisitionIndex(), now.acquisitionIndex());
        assertTrue(s.migrationMarkers().contains("migrate:" + id("a/n1") + "->" + id("a/n1_renamed")));
    }

    @Test
    void lostStartingRootRefundsEverythingAndAllowsNewFreeRoot() {
        SkillGraphSnapshot g = TestGraphs.standard().build();
        PlayerSkillState s = build(g, "a/m1", "a/m3");
        TestGraphs t = new TestGraphs().sector("b");
        t.minor("b/m1", "b").minor("b/m2", "b");
        t.chain("b/m1", "b/hub", "b/m2");
        SkillGraphSnapshot g2 = t.build();
        Reconciler.Report r = Reconciler.reconcile(s, g2, Map.of());
        assertEquals(2, r.refunded());
        assertTrue(s.allocations().isEmpty());
        assertTrue(s.startingRoot().isEmpty());
        assertTrue(AllocationService.confirm(g2, s, List.of(id("b/hub"))).ok());
        assertEquals(30, s.availablePoints());
        assertTrue(s.ledgerHolds());
    }

    @Test
    void newConflictKeepsTheOldestAcquisition() {
        TestGraphs base = TestGraphs.standard();
        base.keystone("a/k3", "a", null);
        base.edge("a/m2", "a/k3");
        SkillGraphSnapshot g = base.build();
        PlayerSkillState s = build(g, "a/m1", "a/m2", "a/k1");
        assertTrue(AllocationService.confirm(g, s, List.of(id("a/k3"))).ok());
        TestGraphs changed = TestGraphs.standard(); // now k3 joins group "vow" with k1
        changed.keystone("a/k3", "a", "vow");
        changed.edge("a/m2", "a/k3");
        Reconciler.Report r = Reconciler.reconcile(s, changed.build(), Map.of());
        assertEquals(List.of(id("a/k3")), r.removed());
        assertTrue(s.isAllocated(id("a/k1")));
    }
}
