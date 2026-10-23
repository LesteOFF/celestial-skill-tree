package dev.lesteoff.celestialskilltree.generation;

import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.WARRIOR;
import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.classJson;
import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.keystoneJson;
import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.themeJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogEditorTest {
    private static CatalogEditor editor(String... loadedMods) {
        Predicate<String> isModLoaded = Set.of(loadedMods)::contains;
        return new CatalogEditor(CatalogFixtures.standard().sources().availableWith(isModLoaded), isModLoaded);
    }

    private static Catalog parsed(CatalogEditor editor) {
        CatalogParser.Outcome outcome = new CatalogParser(KnownIds.PERMISSIVE).parse(editor.sources());
        assertTrue(outcome.catalog().isPresent(), () -> outcome.report().errors().toString());
        assertTrue(outcome.report().issues().isEmpty(), () -> outcome.report().issues().toString());
        return outcome.catalog().get();
    }

    @Test
    void aScriptMayAddAClassWithItsContentAndChangeTheRules() {
        CatalogEditor editor = editor();
        editor.addClass("pack:rogue", JsonParser.parseString(classJson("Rogue", "")));
        editor.addTheme("pack:rogue/stealth", JsonParser.parseString(themeJson("pack:rogue", "Stealth", "")));
        editor.addKeystone("pack:rogue/shadow", JsonParser.parseString(keystoneJson("pack:rogue", "Shadow", "'text':'Dark.',")));
        editor.rules(JsonParser.parseString("{'nodes_per_class':40}"));
        Catalog catalog = parsed(editor);
        assertEquals(Set.of(WARRIOR, "pack:rogue"), catalog.classes().keySet());
        assertEquals(1, catalog.themesOf("pack:rogue").size());
        assertEquals(1, catalog.keystonesOf("pack:rogue").size());
        assertEquals(40, catalog.rules().nodesPerClass());
    }
}
