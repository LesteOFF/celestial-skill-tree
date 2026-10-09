package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModRequirementTest {
    private static final Set<String> LOADED = Set.of("irons_spellbooks", "curios");

    @Test
    void bothRequirementsMustHoldTogether() {
        assertTrue(isMet("{\"requires_mod\":\"curios\",\"requires_any_mod\":[\"ars_nouveau\",\"irons_spellbooks\"]}"));
        assertFalse(isMet("{\"requires_mod\":\"malum\",\"requires_any_mod\":[\"curios\"]}"));
        assertFalse(isMet("{\"requires_mod\":\"curios\",\"requires_any_mod\":[\"malum\"]}"));
    }

    private static boolean isMet(String json) {
        return ModRequirement.isMet(JsonParser.parseString(json), LOADED::contains);
    }
}
