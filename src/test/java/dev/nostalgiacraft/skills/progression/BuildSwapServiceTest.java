package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import dev.nostalgiacraft.skills.progression.BuildSwapService.Build;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void aBuildThatNoLongerFitsLeavesThePlayerUntouched() {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 1, 120, false);
        AllocationService.confirm(g, state, List.of(id("a/hub"), id("a/m1")));
        Build tooExpensive = new Build(Optional.of(id("b/hub")), List.of(id("b/m2"), id("a/m2"), id("a/m1")));
        BuildSwapService.Result result = BuildSwapService.swap(g, state, tooExpensive);
        assertFalse(result.ok());
        assertEquals(Optional.of(id("a/hub")), state.startingRoot());
        assertTrue(state.isAllocated(id("a/m1")));
        assertEquals(0, state.availablePoints());
    }
}
