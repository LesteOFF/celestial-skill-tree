package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphParserTest {
    private static boolean mentions(GraphParser.Outcome o, String text) {
        return o.report().errors().stream().anyMatch(i -> i.toString().contains(text));
    }

    @Test
    void standardFixtureParses() {
        GraphParser.Outcome o = TestGraphs.standard().parse();
        assertTrue(o.snapshot().isPresent(), () -> o.report().errors().toString());
    }

    @Test
    void rejectsSelfEdgeAndDuplicateEdge() {
        TestGraphs t = TestGraphs.standard();
        t.edge("a/m1", "a/m1");
        t.edge("a/m2", "a/m1");
        GraphParser.Outcome o = t.parse();
        assertFalse(o.snapshot().isPresent());
        assertTrue(mentions(o, "self edge"));
        assertTrue(mentions(o, "duplicate edge"));
    }

    @Test
    void rejectsUnknownEndpointAndRequirementReference() {
        TestGraphs t = TestGraphs.standard();
        t.edge("a/m1", "a/ghost");
        t.requires("a/m3", List.of("t:a/nowhere"), List.of(), 0);
        GraphParser.Outcome o = t.parse();
        assertTrue(mentions(o, "unknown node 't:a/ghost'"));
        assertTrue(mentions(o, "unknown node 't:a/nowhere'"));
    }

    @Test
    void reportsFileFieldAndReasonForBadEffect() {
        TestGraphs t = TestGraphs.standard();
        JsonObject bad = t.node("a/bad", "a", "minor", 1);
        bad.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("operation", "multiply_everything");
        t.edge("a/m1", "a/bad");
        GraphParser.Outcome o = t.parse();
        assertTrue(o.report().errors().stream().anyMatch(i -> i.source().equals("skill_nodes/t:a/bad")
                && i.field().equals("effects[0].operation") && i.message().contains("unsupported operation")));
    }

    @Test
    void rejectsNonFiniteCoordinatesAndExplicitIdMismatch() {
        TestGraphs t = TestGraphs.standard();
        JsonObject n = t.node("a/odd", "a", "minor", 1);
        n.getAsJsonObject("position").addProperty("x", Double.POSITIVE_INFINITY);
        n.addProperty("id", "t:a/other");
        t.edge("a/m1", "a/odd");
        GraphParser.Outcome o = t.parse();
        assertTrue(mentions(o, "coordinates must be finite"));
        assertTrue(mentions(o, "disagrees with file path id"));
    }

    @Test
    void rejectsDisconnectedNode() {
        TestGraphs t = TestGraphs.standard();
        t.minor("a/island", "a");
        GraphParser.Outcome o = t.parse();
        assertTrue(mentions(o, "not reachable"));
    }

    @Test
    void rejectsCyclicMandatoryPrerequisites() {
        TestGraphs t = TestGraphs.standard();
        t.minor("a/c1", "a").minor("a/c2", "a");
        t.edge("a/m1", "a/c1");
        t.edge("a/m1", "a/c2");
        t.requires("a/c1", List.of("t:a/c2"), List.of(), 0);
        t.requires("a/c2", List.of("t:a/c1"), List.of(), 0);
        GraphParser.Outcome o = t.parse();
        assertTrue(mentions(o, "can never be satisfied"));
    }

    @Test
    void rejectsRoutesThatOnlyPassThroughExclusiveKeystones() {
        TestGraphs t = TestGraphs.standard();
        t.minor("a/behind", "a");
        t.edge("a/k1", "a/behind");
        GraphParser.Outcome o = t.parse();
        assertTrue(mentions(o, "only reachable through an exclusive choice"));
    }

    @Test
    void rejectsRanksAboveOneAndExcessiveCost() {
        TestGraphs t = TestGraphs.standard();
        JsonObject n = t.node("a/big", "a", "minor", 1000);
        n.addProperty("max_rank", 3);
        t.edge("a/m1", "a/big");
        GraphParser.Outcome o = t.parse();
        assertTrue(mentions(o, "only max_rank 1"));
        assertTrue(mentions(o, "cost"));
    }

    @Test
    void fingerprintIsStableAcrossParses() {
        String f1 = TestGraphs.standard().build().fingerprint();
        String f2 = TestGraphs.standard().build().fingerprint();
        assertTrue(f1.equals(f2));
    }
}
