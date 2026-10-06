package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphParserTest {
    private static boolean mentions(GraphParser.Outcome outcome, String text) {
        return outcome.report().errors().stream().anyMatch(i -> i.toString().contains(text));
    }

    @Test
    void standardFixtureParses() {
        GraphParser.Outcome outcome = TestGraphs.standard().parse();
        assertTrue(outcome.snapshot().isPresent(), () -> outcome.report().errors().toString());
    }

    @Test
    void rejectsSelfEdgeAndDuplicateEdge() {
        TestGraphs testGraphs = TestGraphs.standard();
        testGraphs.edge("a/m1", "a/m1");
        testGraphs.edge("a/m2", "a/m1");
        GraphParser.Outcome outcome = testGraphs.parse();
        assertFalse(outcome.snapshot().isPresent());
        assertTrue(mentions(outcome, "self edge"));
        assertTrue(mentions(outcome, "duplicate edge"));
    }

    @Test
    void rejectsUnknownEndpointAndRequirementReference() {
        TestGraphs testGraphs = TestGraphs.standard();
        testGraphs.edge("a/m1", "a/ghost");
        testGraphs.requires("a/m3", List.of("t:a/nowhere"), List.of(), 0);
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(mentions(outcome, "unknown node 't:a/ghost'"));
        assertTrue(mentions(outcome, "unknown node 't:a/nowhere'"));
    }

    @Test
    void reportsFileFieldAndReasonForBadEffect() {
        TestGraphs testGraphs = TestGraphs.standard();
        JsonObject bad = testGraphs.node("a/bad", "a", "minor", 1);
        bad.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("operation", "multiply_everything");
        testGraphs.edge("a/m1", "a/bad");
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(outcome.report().errors().stream().anyMatch(i -> i.source().equals("skill_nodes/t:a/bad")
                && i.field().equals("effects[0].operation") && i.message().contains("unsupported operation")));
    }

    @Test
    void rejectsNonFiniteCoordinatesAndExplicitIdMismatch() {
        TestGraphs testGraphs = TestGraphs.standard();
        JsonObject json = testGraphs.node("a/odd", "a", "minor", 1);
        json.getAsJsonObject("position").addProperty("x", Double.POSITIVE_INFINITY);
        json.addProperty("id", "t:a/other");
        testGraphs.edge("a/m1", "a/odd");
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(mentions(outcome, "coordinates must be finite"));
        assertTrue(mentions(outcome, "disagrees with file path id"));
    }

    @Test
    void rejectsDisconnectedNode() {
        TestGraphs testGraphs = TestGraphs.standard();
        testGraphs.minor("a/island", "a");
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(mentions(outcome, "not reachable"));
    }

    @Test
    void rejectsCyclicMandatoryPrerequisites() {
        TestGraphs testGraphs = TestGraphs.standard();
        testGraphs.minor("a/c1", "a").minor("a/c2", "a");
        testGraphs.edge("a/m1", "a/c1");
        testGraphs.edge("a/m1", "a/c2");
        testGraphs.requires("a/c1", List.of("t:a/c2"), List.of(), 0);
        testGraphs.requires("a/c2", List.of("t:a/c1"), List.of(), 0);
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(mentions(outcome, "can never be satisfied"));
    }

    @Test
    void rejectsRoutesThatOnlyPassThroughExclusiveKeystones() {
        TestGraphs testGraphs = TestGraphs.standard();
        testGraphs.minor("a/behind", "a");
        testGraphs.edge("a/k1", "a/behind");
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(mentions(outcome, "only reachable through an exclusive choice"));
    }

    @Test
    void rejectsRanksAboveOneAndExcessiveCost() {
        TestGraphs testGraphs = TestGraphs.standard();
        JsonObject json = testGraphs.node("a/big", "a", "minor", 1000);
        json.addProperty("max_rank", 3);
        testGraphs.edge("a/m1", "a/big");
        GraphParser.Outcome outcome = testGraphs.parse();
        assertTrue(mentions(outcome, "only max_rank 1"));
        assertTrue(mentions(outcome, "cost"));
    }

    @Test
    void fingerprintIsStableAcrossParses() {
        String f1 = TestGraphs.standard().build().fingerprint();
        String f2 = TestGraphs.standard().build().fingerprint();
        assertTrue(f1.equals(f2));
    }
}
