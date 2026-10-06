package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationServiceTest {
    SkillGraphSnapshot g;
    PlayerSkillState s;

    @BeforeEach
    void setUp() {
        g = TestGraphs.standard().build();
        s = new PlayerSkillState();
        LedgerService.grant(s, 20, 120, false);
        assertTrue(AllocationService.confirm(g, s, List.of(id("a/hub"))).ok());
    }

    static RejectReason reason(AllocationService.BatchResult r) {
        return r.rejection().orElseThrow().reason();
    }

    @Test
    void startingRootIsFreeAndRequiredFirst() {
        PlayerSkillState fresh = new PlayerSkillState();
        LedgerService.grant(fresh, 5, 120, false);
        assertEquals(RejectReason.STARTING_ROOT_REQUIRED, reason(AllocationService.validate(g, fresh, List.of(id("a/m1")))));
        assertEquals(RejectReason.MULTIPLE_STARTING_ROOTS,
                reason(AllocationService.validate(g, fresh, List.of(id("a/hub"), id("b/hub")))));
        AllocationService.BatchResult r = AllocationService.confirm(g, fresh, List.of(id("a/hub"), id("a/m1")));
        assertTrue(r.ok());
        assertEquals(1, r.totalCost());
        assertEquals(0, fresh.allocations().get(id("a/hub")).paidCost());
        assertEquals(id("a/hub"), fresh.startingRoot().orElseThrow());
    }

    @Test
    void purchasesValidAdjacentNode() {
        AllocationService.BatchResult r = AllocationService.confirm(g, s, List.of(id("a/m1")));
        assertTrue(r.ok());
        assertEquals(19, s.availablePoints());
        assertTrue(s.isAllocated(id("a/m1")));
        assertTrue(s.ledgerHolds());
    }

    @Test
    void rejectsDisconnectedNode() {
        assertEquals(RejectReason.NOT_CONNECTED, reason(AllocationService.validate(g, s, List.of(id("a/m2")))));
    }

    @Test
    void acceptsPathBatchReceivedOutOfOrder() {
        AllocationService.BatchResult r = AllocationService.confirm(g, s, List.of(id("a/n1"), id("a/m2"), id("a/m1")));
        assertTrue(r.ok(), () -> r.rejection().toString());
        assertEquals(4, r.totalCost());
        assertEquals(List.of(id("a/m1"), id("a/m2"), id("a/n1")), r.accepted().stream().map(AllocationService.Accepted::nodeId).toList());
    }

    @Test
    void exactBalanceSucceedsAndOneShortFails() {
        PlayerSkillState t = new PlayerSkillState();
        LedgerService.grant(t, 3, 120, false);
        AllocationService.confirm(g, t, List.of(id("a/hub")));
        assertEquals(RejectReason.INSUFFICIENT_POINTS,
                reason(AllocationService.validate(g, t, List.of(id("a/m1"), id("a/m2"), id("a/n1")))));
        assertTrue(AllocationService.confirm(g, t, List.of(id("a/m1"), id("a/m2"))).ok());
        assertEquals(1, t.availablePoints());
    }

    @Test
    void partiallyInvalidBatchIsRejectedAtomically() {
        long rev = s.stateRevision();
        int before = s.availablePoints();
        AllocationService.BatchResult r = AllocationService.confirm(g, s, List.of(id("a/m1"), id("b/m2")));
        assertFalse(r.ok());
        assertEquals(RejectReason.NOT_CONNECTED, reason(r));
        assertEquals(id("b/m2"), r.rejection().get().nodeId());
        assertFalse(s.isAllocated(id("a/m1")));
        assertEquals(before, s.availablePoints());
        assertEquals(rev, s.stateRevision());
    }

    @Test
    void emptyRequirementListsImposeNothing() {
        assertTrue(AllocationService.confirm(g, s, List.of(id("a/m1"), id("a/m3"))).ok());
    }

    @Test
    void allOfRequiresEveryTerm() {
        assertEquals(RejectReason.REQUIREMENT_UNMET,
                reason(AllocationService.validate(g, s, List.of(id("a/m1"), id("a/req_all")))));
        assertTrue(AllocationService.validate(g, s, List.of(id("a/m1"), id("a/m2"), id("a/n1"), id("a/req_all"))).ok());
    }

    @Test
    void anyOfAcceptsTagOrNode() {
        assertEquals(RejectReason.REQUIREMENT_UNMET,
                reason(AllocationService.validate(g, s, List.of(id("a/m1"), id("a/req_any")))));
        assertTrue(AllocationService.validate(g, s,
                List.of(id("a/m1"), id("a/m3"), id("a/fire"), id("a/req_any"))).ok());
        assertTrue(AllocationService.validate(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/k1"), id("a/req_any"))).ok());
    }

    @Test
    void sectorPointsNeverCountTheNodeItself() {
        // a/m1 + a/m2 + a/n1 = 4 points in sector a: not enough for a/sector5 (needs 5 already invested)
        assertEquals(RejectReason.REQUIREMENT_UNMET, reason(AllocationService.validate(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/n1"), id("a/sector5")))));
        assertTrue(AllocationService.validate(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/n1"), id("a/m3"), id("a/sector5"))).ok());
    }

    @Test
    void keystoneExclusivity() {
        assertEquals(RejectReason.EXCLUSIVE_CONFLICT, reason(AllocationService.validate(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/k1"), id("a/k2")))));
        assertTrue(AllocationService.confirm(g, s, List.of(id("a/m1"), id("a/m2"), id("a/k1"))).ok());
        AllocationService.BatchResult r = AllocationService.validate(g, s, List.of(id("a/k2")));
        assertEquals(RejectReason.EXCLUSIVE_CONFLICT, reason(r));
        assertEquals(id("a/k1"), r.rejection().get().detail());
    }

    @Test
    void secondRootCostsOnePointThroughTheGraph() {
        AllocationService.BatchResult r = AllocationService.confirm(g, s,
                List.of(id("a/m1"), id("a/m3"), id("a/m4"), id("b/m1"), id("b/hub")));
        assertTrue(r.ok());
        assertEquals(1, s.allocations().get(id("b/hub")).paidCost());
        assertEquals(id("a/hub"), s.startingRoot().orElseThrow());
    }

    @Test
    void removingPendingNodeReportsItsDependentChain() {
        Set<String> deps = AllocationService.dependentsOfPendingRemoval(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/n1"), id("a/m3")), id("a/m2"));
        assertEquals(Set.of(id("a/n1")), deps);
        Set<String> all = AllocationService.dependentsOfPendingRemoval(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/n1"), id("a/m3")), id("a/m1"));
        assertEquals(Set.of(id("a/m2"), id("a/n1"), id("a/m3")), all);
    }

    @Test
    void eligibilityConsidersPendingSelection() {
        assertNull(AllocationService.eligibility(g, s, List.of(id("a/m1")), id("a/m2")));
        assertEquals(RejectReason.NOT_CONNECTED, AllocationService.eligibility(g, s, List.of(), id("a/m2")).reason());
    }

    @Test
    void unknownAndAlreadyAllocatedAreRejected() {
        assertEquals(RejectReason.UNKNOWN_NODE, reason(AllocationService.validate(g, s, List.of("t:a/none"))));
        assertEquals(RejectReason.ALREADY_ALLOCATED, reason(AllocationService.validate(g, s, List.of(id("a/hub")))));
        assertEquals(RejectReason.EMPTY_REQUEST, reason(AllocationService.validate(g, s, List.of())));
    }

    @Test
    void suggestedRouteIsCheapestAndValidated() {
        PathFinder.Route r = PathFinder.route(g, s, List.of(), id("b/m1")).orElseThrow();
        assertEquals(List.of(id("a/m1"), id("a/m3"), id("a/m4"), id("b/m1")), r.newNodes());
        assertEquals(4, r.cost());
        assertTrue(r.valid());
        PathFinder.Route req = PathFinder.route(g, s, List.of(), id("a/req_all")).orElseThrow();
        assertFalse(req.valid(), "route ignoring a/n1 requirement must be flagged as unresolved");
    }
}
