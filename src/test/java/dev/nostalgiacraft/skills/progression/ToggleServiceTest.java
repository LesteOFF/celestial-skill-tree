package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.effects.CompiledBuild;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleServiceTest {
    private static final String HEALTH = CompiledBuild.sumKey("minecraft:generic.max_health", "add_value");

    private static SkillGraphSnapshot graph() {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a");
        layout.keystone("a/k1", "a", null).node("a/k1", "a", "keystone", 3).addProperty("toggleable", true);
        layout.chain("a/hub", "a/m1", "a/k1");
        return layout.build();
    }

    private static PlayerSkillState build(SkillGraphSnapshot graph, String... paths) {
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
    void aToggleableKeystoneSwitchesOffAndOnWithoutTouchingThePoints() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = build(graph, "a/hub", "a/m1", "a/k1");
        int available = state.availablePoints();
        long revision = state.stateRevision();

        assertEquals(Optional.empty(), ToggleService.toggle(graph, state, id("a/k1")));
        assertTrue(state.isDisabled(id("a/k1")));
        assertTrue(state.isAllocated(id("a/k1")), "a disabled keystone is still owned");
        assertEquals(2.0, health(graph, state), "hub 1 + m1 1, the keystone is silent");
        assertEquals(available, state.availablePoints());
        assertEquals(revision + 1, state.stateRevision());

        assertEquals(Optional.empty(), ToggleService.toggle(graph, state, id("a/k1")));
        assertFalse(state.isDisabled(id("a/k1")));
        assertEquals(3.0, health(graph, state));
        assertEquals(available, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void onlyOwnedToggleableNodesCanBeSwitched() {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = build(graph, "a/hub", "a/m1");
        long revision = state.stateRevision();
        assertEquals(Optional.of(RejectReason.NOT_TOGGLEABLE), ToggleService.toggle(graph, state, id("a/m1")));
        assertEquals(Optional.of(RejectReason.NOT_ALLOCATED), ToggleService.toggle(graph, state, id("a/k1")));
        assertEquals(Optional.of(RejectReason.UNKNOWN_NODE), ToggleService.toggle(graph, state, id("a/nope")));
        assertTrue(state.disabled().isEmpty());
        assertEquals(revision, state.stateRevision(), "a rejected toggle changes nothing");
    }

    @Test
    void theSwitchTravelsToTheClientAndARefundClearsIt() throws IOException {
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = build(graph, "a/hub", "a/m1", "a/k1");
        ToggleService.toggle(graph, state, id("a/k1"));
        assertTrue(GraphCodec.decode(GraphCodec.encode(graph)).nodes().get(id("a/k1")).traits().toggleable());

        assertTrue(state.copy().isDisabled(id("a/k1")), "the copy sent to the client carries the switch");

        RefundService.apply(graph, state, id("a/k1"), true);
        AllocationService.confirm(graph, state, List.of(id("a/k1")));
        assertFalse(state.isDisabled(id("a/k1")), "a keystone bought again starts switched on");
    }
}
