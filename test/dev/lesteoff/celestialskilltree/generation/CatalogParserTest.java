package dev.lesteoff.celestialskilltree.generation;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.MAGE;
import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.MAGIC_MOD;
import static dev.lesteoff.celestialskilltree.generation.CatalogFixtures.WARRIOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogParserTest {
    private static final String ICON = "test:textures/icon.png";

    private static Catalog valid(CatalogParser.Outcome outcome) {
        assertTrue(outcome.catalog().isPresent(), () -> outcome.report().errors().toString());
        return outcome.catalog().get();
    }

    @Test
    void readsClassesWithTheirHubAndTrunk() {
        Catalog catalog = valid(CatalogFixtures.standard().parseWithMods(MAGIC_MOD));
        ClassDefinition mage = catalog.classes().get(MAGE);
        assertEquals(Set.of(WARRIOR, MAGE), catalog.classes().keySet());
        assertEquals("Mage", mage.name());
        assertEquals(5, mage.priority());
        assertEquals(Optional.of(WARRIOR), mage.fallbackClass());
        assertEquals(Optional.empty(), mage.skills());
        assertEquals(new SkillTemplate("Mage", ICON, mage.hub().effects(), Optional.empty()), mage.hub());
        assertEquals("Mage Training", mage.trunk().name());
        assertEquals(ICON, mage.trunk().icon());
        assertEquals(1, mage.trunk().effects().size());
        assertEquals(0, catalog.classes().get(WARRIOR).priority());
    }

    @Test
    void contentOfAClassWithoutItsModsIsDroppedQuietly() {
        CatalogParser.Outcome outcome = CatalogFixtures.standard().parseWithMods();
        Catalog catalog = valid(outcome);
        assertTrue(outcome.report().issues().isEmpty(), () -> outcome.report().issues().toString());
        assertEquals(Set.of(WARRIOR), catalog.classes().keySet());
        assertEquals(Set.of("test:warrior/might"), catalog.themes().keySet());
        assertEquals(Set.of("test:warrior/titan"), catalog.keystones().keySet());
    }
}
