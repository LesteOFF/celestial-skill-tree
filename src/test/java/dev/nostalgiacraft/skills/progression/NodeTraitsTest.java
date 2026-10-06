package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.effects.CompiledBuild;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.NodeTraits;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import org.junit.jupiter.api.Test;
import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class NodeTraitsTest {
    private static final String HEALTH = CompiledBuild.sumKey("minecraft:generic.max_health", "add_value");
    private static final String ADVANCEMENT = "t:found_it";

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

    private static TestGraphs hiddenLayout(boolean hidden) {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a").minor("a/m5", "a").minor("a/h2", "a");
        if (hidden) {
            layout.node("a/h1", "a", "minor", 1).addProperty("hidden_until", ADVANCEMENT);
            layout.node("a/h2", "a", "minor", 1).addProperty("hidden_until", ADVANCEMENT);
        } else {
            layout.minor("a/h1", "a");
        }
        layout.chain("a/hub", "a/m1", "a/h1", "a/h2");
        layout.edge("a/m1", "a/m5");
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

    @Test
    void neighboursOwnedBeforeTheBlackHoleAreKeptAndDoubledUntilItIsRefunded() {
        SkillGraphSnapshot graph = blackHoleLayout().build();
        PlayerSkillState state = build(graph, "a/hub", "a/m1", "a/m2", "a/m3", "a/k1");
        assertEquals(7.0, health(graph, state), "hub 1 + m1 1 + m2 2 + m3 2 + keystone 1");
        assertEquals(5, Reconciler.survivors(state, graph, Set.of()).size(), "replaying the build keeps everything");
        RefundService.Plan plan = RefundService.apply(graph, state, id("a/k1"), true);
        assertEquals(List.of(id("a/k1")), plan.removed());
        assertEquals(4.0, health(graph, state));
        assertTrue(state.ledgerHolds());
    }

    @Test
    void hiddenSkillsNeedTheirDiscovery() {
        SkillGraphSnapshot graph = hiddenLayout(true).build();
        PlayerSkillState state = build(graph, "a/hub", "a/m1");
        assertEquals(RejectReason.UNDISCOVERED, rejection(graph, state, "a/h1"));
        assertTrue(PathFinder.route(graph, state, List.of(), id("a/h2")).isEmpty(), "no route through undiscovered skills");
        long revision = state.stateRevision();
        assertFalse(DiscoveryService.discover(state, graph, "t:unrelated"));
        assertTrue(DiscoveryService.discover(state, graph, ADVANCEMENT));
        assertFalse(DiscoveryService.discover(state, graph, ADVANCEMENT), "a discovery is recorded once");
        assertEquals(revision + 1, state.stateRevision());
        assertTrue(AllocationService.confirm(graph, state, List.of(id("a/h1"), id("a/h2"))).ok());
        assertTrue(state.copy().discoveries().contains(ADVANCEMENT));
    }

    @Test
    void skillsLearnedBeforeBeingHiddenSurviveReconciliation() {
        PlayerSkillState state = build(hiddenLayout(false).build(), "a/hub", "a/m1", "a/h1", "a/h2");
        SkillGraphSnapshot nowHidden = hiddenLayout(true).build();
        Reconciler.Report report = Reconciler.reconcile(state, nowHidden, Map.of());
        assertTrue(report.removed().isEmpty(), "removed " + report.removed());
        assertEquals(4, state.allocations().size());
    }

    @Test
    void aHiddenSkillMayNotBeTheOnlyWayToAVisibleOne() {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a").minor("a/visible", "a");
        layout.node("a/h1", "a", "minor", 1).addProperty("hidden_until", ADVANCEMENT);
        layout.chain("a/hub", "a/m1", "a/h1", "a/visible");
        GraphParser.Outcome outcome = layout.parse();
        assertTrue(outcome.snapshot().isEmpty());
        assertTrue(outcome.report().errors().toString().contains("only reachable through a hidden skill"));
    }

    @Test
    void traitsSurviveTheNetworkCodecAndBadMultipliersAreRejected() throws IOException {
        SkillGraphSnapshot blackHole = GraphCodec.decode(GraphCodec.encode(blackHoleLayout().build()));
        assertEquals(2.0, blackHole.nodes().get(id("a/k1")).traits().neighbourMultiplier());
        assertEquals(NodeTraits.NONE, blackHole.nodes().get(id("a/m1")).traits());
        SkillGraphSnapshot hidden = GraphCodec.decode(GraphCodec.encode(hiddenLayout(true).build()));
        assertEquals(Optional.of(ADVANCEMENT), hidden.nodes().get(id("a/h1")).traits().hiddenUntil());
        TestGraphs invalid = new TestGraphs().sector("a");
        invalid.node("a/k1", "a", "keystone", 3).addProperty("neighbour_multiplier", 9.0);
        invalid.edge("a/hub", "a/k1");
        assertTrue(invalid.parse().report().errors().toString().contains("neighbour_multiplier"));
    }
}
