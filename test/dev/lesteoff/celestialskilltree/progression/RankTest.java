package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.effects.CompiledBuild;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.lesteoff.celestialskilltree.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankTest {
    private static final CompiledBuild.AttributeKey HEALTH = CompiledBuild.sumKey("minecraft:generic.max_health", "add_value");
    private static final int RANKED_COST = 2;
    private static final int MAX_RANK = 3;

    private static SkillGraphSnapshot graph() {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a");
        layout.node("a/r1", "a", "notable", RANKED_COST).addProperty("max_rank", MAX_RANK);
        layout.minor("a/m2", "a");
        layout.chain("a/hub", "a/m1", "a/r1", "a/m2");
        return layout.build();
    }

    private static PlayerSkillState learned(SkillGraphSnapshot graph, String... paths) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 30, 120, false);
        for (String path : paths) {
            assertTrue(AllocationService.confirm(graph, state, List.of(id(path))).ok(), "fixture step " + path);
        }
        return state;
    }

    private static double health(SkillGraphSnapshot graph, PlayerSkillState state) {
        return CompiledBuild.compile(graph, state).attributeSums().getOrDefault(HEALTH, 0.0);
    }

    @Test
    void confirmingALearnedRankedNodeBuysTheNextRankAtTheNodePrice() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1");
        int before = state.availablePoints();

        AllocationService.BatchResult result = AllocationService.confirm(graph, state, List.of(id("a/r1")));

        assertTrue(result.ok(), () -> "rejected: " + result.rejection());
        assertEquals(2, state.rank(id("a/r1")));
        assertEquals(RANKED_COST, result.totalCost());
        assertEquals(before - RANKED_COST, state.availablePoints());
        assertEquals(2 * RANKED_COST, state.allocations().get(id("a/r1")).paidCost());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void aRankedNodeStopsAtItsMaxRank() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1", "a/r1", "a/r1");

        AllocationService.BatchResult result = AllocationService.confirm(graph, state, List.of(id("a/r1")));

        assertEquals(MAX_RANK, state.rank(id("a/r1")));
        assertFalse(result.ok());
        assertEquals(RejectReason.ALREADY_ALLOCATED, result.rejection().orElseThrow().reason());
        assertFalse(AllocationService.canRankUp(graph, state, id("a/r1")));
    }

    @Test
    void aSingleRankNodeCannotBeBoughtTwice() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1");

        assertFalse(AllocationService.canRankUp(graph, state, id("a/m1")));
        assertEquals(RejectReason.ALREADY_ALLOCATED,
                AllocationService.confirm(graph, state, List.of(id("a/m1"))).rejection().orElseThrow().reason());
    }

    @Test
    void effectsGrowWithEveryRank() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1");
        double oneRank = health(graph, state);

        AllocationService.confirm(graph, state, List.of(id("a/r1")));

        assertEquals(oneRank + 1.0, health(graph, state), 1e-9, "a second rank adds the node's effect once more");
    }

    @Test
    void refundingARankedNodeReturnsEveryRank() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1", "a/r1", "a/r1");
        int before = state.availablePoints();

        RefundService.Plan plan = RefundService.apply(graph, state, id("a/r1"), true);

        assertTrue(plan.ok());
        assertEquals(MAX_RANK * RANKED_COST, plan.refund());
        assertEquals(before + MAX_RANK * RANKED_COST, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void oneBatchCanLearnANewNodeAndRankUpAnother() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1");

        assertEquals(RANKED_COST, AllocationService.rankUpCost(graph, state, Set.of(id("a/r1"), id("a/m1"), id("a/m2"))));
        AllocationService.BatchResult result = AllocationService.confirm(graph, state, List.of(id("a/r1"), id("a/m2")));

        assertTrue(result.ok(), () -> "rejected: " + result.rejection());
        assertEquals(RANKED_COST + graph.nodes().get(id("a/m2")).cost(), result.totalCost());
        assertEquals(2, state.rank(id("a/r1")));
        assertEquals(1, state.rank(id("a/m2")));
    }

    @Test
    void repeatingALearnedRankedNodeBuysSeveralRanksInOneBatch() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1");

        AllocationService.BatchResult result = AllocationService.confirm(graph, state, List.of(id("a/r1"), id("a/r1")));

        assertTrue(result.ok(), () -> "rejected: " + result.rejection());
        assertEquals(MAX_RANK, state.rank(id("a/r1")));
        assertEquals(2 * RANKED_COST, result.totalCost());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void askingForMoreRanksThanAreLeftIsRejected() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1", "a/r1");

        AllocationService.BatchResult result = AllocationService.confirm(graph, state, List.of(id("a/r1"), id("a/r1"), id("a/r1")));

        assertFalse(result.ok());
        assertEquals(RejectReason.ALREADY_ALLOCATED, result.rejection().orElseThrow().reason());
        assertEquals(1, state.rank(id("a/r1")), "a rejected batch changes nothing");
    }

    @Test
    void aNewRankedNodeCanBeLearnedStraightToItsSecondRank() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = learned(graph, "a/hub", "a/m1");
        List<String> request = List.of(id("a/r1"), id("a/r1"));

        assertEquals(RANKED_COST, AllocationService.rankUpCost(graph, state, request), "only the second rank is an extra rank");
        AllocationService.BatchResult result = AllocationService.confirm(graph, state, request);

        assertTrue(result.ok(), () -> "rejected: " + result.rejection());
        assertEquals(2, state.rank(id("a/r1")));
        assertEquals(2 * RANKED_COST, state.allocations().get(id("a/r1")).paidCost());
    }

    @Test
    void aFallenStarGivesBackTheRanksItWasWorth() {
        SkillGraphSnapshot graph = graph();
        assertEquals(MAX_RANK, FallenStarService.paidRank(graph.nodes().get(id("a/r1")), MAX_RANK * RANKED_COST));
        assertEquals(1, FallenStarService.paidRank(graph.nodes().get(id("a/m1")), 7));
    }
}
