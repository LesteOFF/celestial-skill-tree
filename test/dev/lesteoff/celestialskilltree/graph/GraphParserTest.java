package dev.lesteoff.celestialskilltree.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphParserTest {
    private static boolean mentions(GraphParser.Outcome outcome, String text) {
        return outcome.report().errors().stream().anyMatch(i -> i.toString().contains(text));
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
}
