package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.effects.CompiledBuild;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;
import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class NodeTraitsTest {
    private static final String HEALTH = CompiledBuild.sumKey("minecraft:generic.max_health", "add_value");

    private static TestGraphs blackHoleLayout() {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a").minor("a/m2", "a").minor("a/m3", "a").minor("a/m4", "a");
        layout.keystone("a/k1", "a", null).node("a/k1", "a", "keystone", 3).addProperty("neighbour_multiplier", 2.0);
        layout.chain("a/hub", "a/m1", "a/m2", "a/k1");
        layout.edge("a/m2", "a/m3");
        layout.edge("a/k1", "a/m3");
        layout.edge("a/m2", "a/m4");
        return layout;
    }

    private static PlayerSkillState build(SkillGraphSnapshot graph, String... paths) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 30, 120, false);
        for (String path : paths) {
            AllocationService.BatchResult result = AllocationService.confirm(graph, state, List.of(id(path)));
            assertTrue(result.ok(), "fixture step " + path + " rejected: " + result.rejection());
        }
        return state;
    }

    private static double health(SkillGraphSnapshot graph, PlayerSkillState state) {
        return CompiledBuild.compile(graph, state).attributeSums().getOrDefault(HEALTH, 0.0);
    }

    private static RejectReason rejection(SkillGraphSnapshot graph, PlayerSkillState state, String path) {
        return AllocationService.validate(graph, state, List.of(id(path))).rejection().orElseThrow().reason();
    }

    @Test
    void aBlackHoleDoublesOwnedNeighboursAndSealsTheOthers() {
        SkillGraphSnapshot graph = blackHoleLayout().build();
        PlayerSkillState state = build(graph, "a/hub", "a/m1", "a/m2");
        assertEquals(3.0, health(graph, state));
        AllocationService.confirm(graph, state, List.of(id("a/k1")));
        assertEquals(5.0, health(graph, state), "hub 1 + m1 1 + m2 doubled 2 + keystone 1");
        assertEquals(RejectReason.CONSUMED, rejection(graph, state, "a/m3"));
        assertTrue(PathFinder.route(graph, state, List.of(), id("a/m3")).isEmpty(), "no route into a sealed skill");
        assertTrue(AllocationService.validate(graph, state, List.of(id("a/m4"))).ok(), "non-adjacent skills stay open");
    }
}
