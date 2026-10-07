package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModRequirementTest {
    private static final Set<String> LOADED = Set.of("irons_spellbooks", "curios");

    @Test
    void aFileWithoutTheFieldIsAlwaysLoaded() {
        assertTrue(isMet("{\"schema_version\":1}"));
        assertTrue(isMet("[]"));
    }

    @Test
    void aSingleModMustBeLoaded() {
        assertTrue(isMet("{\"requires_mod\":\"curios\"}"));
        assertFalse(isMet("{\"requires_mod\":\"ars_nouveau\"}"));
    }

    @Test
    void everyModOfAListMustBeLoaded() {
        assertTrue(isMet("{\"requires_mod\":[\"curios\",\"irons_spellbooks\"]}"));
        assertFalse(isMet("{\"requires_mod\":[\"curios\",\"ars_nouveau\"]}"));
    }

    private static boolean isMet(String json) {
        return ModRequirement.isMet(JsonParser.parseString(json), LOADED::contains);
    }
}
