package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallenStarServiceTest {
    private final SkillGraphSnapshot graph = TestGraphs.standard().build();

    private PlayerSkillState build(String... paths) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 20, 120, false);
        for (String path : paths) {
            assertTrue(AllocationService.confirm(graph, state, List.of(id(path))).ok(), "fixture step " + path);
        }
        return state;
    }

    @Test
    void theOwnerRelearnsTheSkillOrGetsThePointsWhenItNoLongerFits() {
        PlayerSkillState state = build("a/hub", "a/m1", "a/m2", "a/n1");
        int total = state.totalPoints();
        Allocation dropped = FallenStarService.dropNewest(state, graph).orElseThrow();
        assertEquals(FallenStarService.Recovery.RELEARNED,
                FallenStarService.recoverOwn(state, graph, dropped.nodeId(), dropped.paidCost()));
        assertTrue(state.isAllocated(id("a/n1")));
        assertEquals(total, state.totalPoints());
        assertTrue(state.ledgerHolds());

        Allocation again = FallenStarService.dropNewest(state, graph).orElseThrow();
        RefundService.apply(graph, state, id("a/m2"), true);
        int available = state.availablePoints();
        assertEquals(FallenStarService.Recovery.POINTS_RETURNED,
                FallenStarService.recoverOwn(state, graph, again.nodeId(), again.paidCost()));
        assertFalse(state.isAllocated(id("a/n1")), "the skill is no longer connected");
        assertEquals(available + 2, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }
}
