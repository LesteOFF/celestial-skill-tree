package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    private static GraphParser.Outcome withEffects(String... effects) {
        TestGraphs testGraphs = TestGraphs.standard();
        JsonArray array = new JsonArray();
        for (String effect : effects) {
            array.add(JsonParser.parseString(effect));
        }
        testGraphs.node("a/compat", "a", "minor", 1).add("effects", array);
        testGraphs.edge("a/m1", "a/compat");
        return testGraphs.parse();
    }

    @Test
    void acceptsDamageTagScopesNamespacedTriggersAndWorldConditions() {
        GraphParser.Outcome outcome = withEffects(
                "{'type':'damage_modifier','direction':'dealt','scope':'#othermod:spells','amount':0.1}",
                "{'type':'trigger','trigger':'othermod:on_spell_cast','action':{'type':'heal','amount':1}}",
                "{'type':'damage_modifier','direction':'taken','scope':'all','amount':-0.1,"
                        + "'condition':{'type':'in_dimension','dimension':'aether:the_aether'}}",
                "{'type':'damage_modifier','direction':'taken','scope':'all','amount':-0.1,"
                        + "'condition':{'type':'in_biome_tag','tag':'c:is_cold'}}",
                "{'type':'damage_modifier','direction':'dealt','scope':'melee','amount':0.1,"
                        + "'condition':{'type':'sky_event','event':'eclipse'}}",
                "{'type':'damage_modifier','direction':'dealt','scope':'melee','amount':0.1,"
                        + "'condition':{'type':'wearing','tag':'othermod:robes'}}",
                "{'type':'damage_modifier','direction':'dealt','scope':'melee','amount':0.1,"
                        + "'condition':{'type':'attribute_at_least','attribute':'othermod:max_mana','value':200}}");
        assertTrue(outcome.snapshot().isPresent(), () -> outcome.report().errors().toString());
    }

    @Test
    void acceptsManaRestoreOnSpellTriggersAndRejectsAnEmptyAmount() {
        GraphParser.Outcome valid = withEffects(
                "{'type':'trigger','trigger':'nostalgiaskills:on_mana_empty','cooldown_ticks':600,"
                        + "'action':{'type':'restore_mana','amount':50}}");
        assertTrue(valid.snapshot().isPresent(), () -> valid.report().errors().toString());
        GraphParser.Outcome invalid = withEffects(
                "{'type':'trigger','trigger':'nostalgiaskills:on_spell_cast','action':{'type':'restore_mana','amount':0}}");
        assertTrue(mentions(invalid, "restore_mana amount must be within"));
    }

    @Test
    void acceptsDualWieldingAndCurioConditionsAndRejectsACurioWithoutTag() {
        GraphParser.Outcome valid = withEffects(
                "{'type':'conditional_attribute','attribute':'minecraft:generic.attack_speed','operation':'add_multiplied_base',"
                        + "'amount':0.05,'condition':{'type':'dual_wielding'}}",
                "{'type':'conditional_attribute','attribute':'minecraft:generic.armor','operation':'add_value',"
                        + "'amount':1,'condition':{'type':'has_curio','tag':'curios:ring'}}");
        assertTrue(valid.snapshot().isPresent(), () -> valid.report().errors().toString());
        GraphParser.Outcome invalid = withEffects(
                "{'type':'conditional_attribute','attribute':'minecraft:generic.armor','operation':'add_value',"
                        + "'amount':1,'condition':{'type':'has_curio'}}");
        assertTrue(mentions(invalid, "an item tag id"));
    }

    @Test
    void rejectsMalformedScopesTriggersAndWorldConditions() {
        GraphParser.Outcome outcome = withEffects(
                "{'type':'damage_modifier','direction':'dealt','scope':'#not a tag','amount':0.1}",
                "{'type':'trigger','trigger':'on_spell_cast','action':{'type':'heal','amount':1}}",
                "{'type':'damage_modifier','direction':'dealt','scope':'melee','amount':0.1,"
                        + "'condition':{'type':'sky_event','event':'blood_moon'}}",
                "{'type':'damage_modifier','direction':'dealt','scope':'melee','amount':0.1,"
                        + "'condition':{'type':'attribute_at_least','attribute':'othermod:max_mana'}}");
        assertTrue(mentions(outcome, "unknown scope '#not a tag'"));
        assertTrue(mentions(outcome, "unknown trigger 'on_spell_cast'"));
        assertTrue(mentions(outcome, "must be one of [eclipse, star_shower]"));
        assertTrue(mentions(outcome, "must be a number within"));
    }

    @Test
    void fingerprintIsStableAcrossParses() {
        String f1 = TestGraphs.standard().build().fingerprint();
        String f2 = TestGraphs.standard().build().fingerprint();
        assertTrue(f1.equals(f2));
    }
}
