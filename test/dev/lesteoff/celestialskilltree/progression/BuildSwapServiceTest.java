package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.TestGraphs;
import dev.lesteoff.celestialskilltree.progression.BuildSwapService.Build;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.lesteoff.celestialskilltree.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSwapServiceTest {
    final SkillGraphSnapshot g = TestGraphs.standard().build();

    @Test
    void swappingTwiceBringsTheFirstBuildBackWithoutCreatingPoints() {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 6, 120, false);
        AllocationService.confirm(g, state, List.of(id("a/hub"), id("a/m1"), id("a/m2")));
        BuildSwapService.Result stored = BuildSwapService.swap(g, state, Build.EMPTY);
        assertTrue(stored.ok());
        assertTrue(state.allocations().isEmpty());
        assertEquals(6, state.availablePoints());
        AllocationService.confirm(g, state, List.of(id("b/hub"), id("b/m2")));
        BuildSwapService.Result back = BuildSwapService.swap(g, state, stored.previous());
        assertTrue(back.ok());
        assertEquals(Optional.of(id("b/hub")), back.previous().startingRoot());
        assertEquals(List.of(id("b/m2")), back.previous().nodes());
        assertEquals(Optional.of(id("a/hub")), state.startingRoot());
        assertTrue(state.isAllocated(id("a/m2")));
        assertEquals(4, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void aSwappedBuildKeepsItsRanksAndSwitchedOffKeystones() {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a");
        layout.node("a/r1", "a", "notable", 2).addProperty("max_rank", 3);
        layout.keystone("a/k1", "a", null).node("a/k1", "a", "keystone", 3).addProperty("toggleable", true);
        layout.chain("a/hub", "a/m1", "a/r1", "a/k1");
        SkillGraphSnapshot graph = layout.build();
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 30, 120, false);
        AllocationService.confirm(graph, state, List.of(id("a/hub"), id("a/m1"), id("a/r1"), id("a/r1"), id("a/k1")));
        ToggleService.toggle(graph, state, id("a/k1"));

        BuildSwapService.Result stored = BuildSwapService.swap(graph, state, Build.EMPTY);
        BuildSwapService.Result back = BuildSwapService.swap(graph, state, stored.previous());

        assertTrue(back.ok(), () -> "rejected: " + back.rejection());
        assertEquals(2, state.rank(id("a/r1")));
        assertTrue(state.isDisabled(id("a/k1")));
        assertEquals(4, stored.previous().size());
        assertTrue(state.ledgerHolds());
    }
}
