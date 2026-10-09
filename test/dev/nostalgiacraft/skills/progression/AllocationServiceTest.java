package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    static RejectReason reason(AllocationService.BatchResult result) {
        return result.rejection().orElseThrow().reason();
    }

    @Test
    void startingRootIsFreeAndRequiredFirst() {
        PlayerSkillState fresh = new PlayerSkillState();
        LedgerService.grant(fresh, 5, 120, false);
        assertEquals(RejectReason.STARTING_ROOT_REQUIRED, reason(AllocationService.validate(g, fresh, List.of(id("a/m1")))));
        assertEquals(RejectReason.MULTIPLE_STARTING_ROOTS,
                reason(AllocationService.validate(g, fresh, List.of(id("a/hub"), id("b/hub")))));
        AllocationService.BatchResult result = AllocationService.confirm(g, fresh, List.of(id("a/hub"), id("a/m1")));
        assertTrue(result.ok());
        assertEquals(1, result.totalCost());
        assertEquals(0, fresh.allocations().get(id("a/hub")).paidCost());
        assertEquals(id("a/hub"), fresh.startingRoot().orElseThrow());
    }

    @Test
    void partiallyInvalidBatchIsRejectedAtomically() {
        long rev = s.stateRevision();
        int before = s.availablePoints();
        AllocationService.BatchResult result = AllocationService.confirm(g, s, List.of(id("a/m1"), id("b/m2")));
        assertFalse(result.ok());
        assertEquals(RejectReason.NOT_CONNECTED, reason(result));
        assertEquals(id("b/m2"), result.rejection().get().nodeId());
        assertFalse(s.isAllocated(id("a/m1")));
        assertEquals(before, s.availablePoints());
        assertEquals(rev, s.stateRevision());
    }

    @Test
    void keystoneExclusivity() {
        assertEquals(RejectReason.EXCLUSIVE_CONFLICT, reason(AllocationService.validate(g, s,
                List.of(id("a/m1"), id("a/m2"), id("a/k1"), id("a/k2")))));
        assertTrue(AllocationService.confirm(g, s, List.of(id("a/m1"), id("a/m2"), id("a/k1"))).ok());
        AllocationService.BatchResult result = AllocationService.validate(g, s, List.of(id("a/k2")));
        assertEquals(RejectReason.EXCLUSIVE_CONFLICT, reason(result));
        assertEquals(id("a/k1"), result.rejection().get().detail());
    }
}
