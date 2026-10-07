package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.graph.KnownIds;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static dev.nostalgiacraft.skills.generation.CatalogFixtures.MAGE;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.MAGIC_MOD;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.WARRIOR;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.classJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.keystoneJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.themeJson;
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

    @Test
    void removingAClassAlsoDropsItsContentWithoutErrors() {
        CatalogEditor editor = editor(MAGIC_MOD);
        editor.removeClass(MAGE);
        Catalog catalog = parsed(editor);
        assertEquals(Set.of(WARRIOR), catalog.classes().keySet());
        assertEquals(Set.of("test:warrior/might"), catalog.themes().keySet());
        assertEquals(Set.of("test:warrior/titan"), catalog.keystones().keySet());
    }

    @Test
    void singleEntriesMayBeRemovedOrReplaced() {
        CatalogEditor editor = editor();
        editor.removeKeystone("test:warrior/titan");
        editor.addTheme("test:warrior/might", JsonParser.parseString(themeJson(WARRIOR, "Brawn", "")));
        Catalog catalog = parsed(editor);
        assertTrue(catalog.keystones().isEmpty());
        assertEquals("Brawn", catalog.themes().get("test:warrior/might").name());
    }

    @Test
    void addedEntriesThatRequireAMissingModAreIgnored() {
        CatalogEditor editor = editor();
        editor.addClass("pack:gunner", JsonParser.parseString(classJson("Gunner", "'requires_mod':'guns',")));
        editor.addTheme("pack:gunner/aim", JsonParser.parseString(themeJson("pack:gunner", "Aim", "")));
        editor.addTheme("test:warrior/mana", JsonParser.parseString(themeJson(WARRIOR, "Mana", "'requires_mod':'" + MAGIC_MOD + "',")));
        Catalog catalog = parsed(editor);
        assertEquals(Set.of(WARRIOR), catalog.classes().keySet());
        assertEquals(Set.of("test:warrior/might"), catalog.themes().keySet());
    }

    @Test
    void theDrawSurvivesBeingWrittenToTheSave() {
        Catalog catalog = CatalogFixtures.world(4, 6, 1, 3).withoutThemeNotables("test:c0", 1).catalog();
        TreeDraw draw = TreeGenerator.generate("test:main", catalog, 7, TreeDraw.EMPTY).draw();
        assertTrue(draw.classes().get(0).ribs().values().stream().anyMatch(choice -> choice.notable().isEmpty()));
        assertEquals(draw, DrawJson.read(DrawJson.write(draw)));
        assertEquals(TreeDraw.EMPTY, DrawJson.read(""));
        assertEquals(TreeDraw.EMPTY, DrawJson.read("not json"));
    }
}
