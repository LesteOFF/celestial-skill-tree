package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.KnownIds;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.nostalgiacraft.skills.generation.CatalogFixtures.ARMOR;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.HEALTH;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.MAGE;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.MAGIC_MOD;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.ON_KILL;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.WARRIOR;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.classJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.keystoneJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.notableJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.themeJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogParserTest {
    private static final String ICON = "test:textures/icon.png";
    private static final String NEEDS_PLAIN_WORDS =
            "combines a trigger with other effects, or has three or more effects, but has no plain-language text";

    private static Catalog valid(CatalogParser.Outcome outcome) {
        assertTrue(outcome.catalog().isPresent(), () -> outcome.report().errors().toString());
        return outcome.catalog().get();
    }

    private static void assertRejected(CatalogParser.Outcome outcome, String source, String field, String messagePart) {
        assertTrue(outcome.catalog().isEmpty(), "expected the catalog to be rejected");
        boolean reported = outcome.report().errors().stream().anyMatch(issue -> issue.source().equals(source)
                && issue.field().equals(field) && issue.message().contains(messagePart));
        assertTrue(reported, () -> "no error for " + source + " [" + field + "] containing '" + messagePart + "' in "
                + outcome.report().errors());
    }

    private static List<String> warnings(CatalogParser.Outcome outcome) {
        return outcome.report().warnings().stream().map(ValidationReport.Issue::toString).toList();
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
    void readsThemesWhoseMinorAndNotablesInheritNameAndIcon() {
        Catalog catalog = valid(CatalogFixtures.standard().parseWithMods(MAGIC_MOD));
        ThemeDefinition might = catalog.themes().get("test:warrior/might");
        assertEquals(WARRIOR, might.classId());
        assertEquals("Might", might.name());
        assertEquals(10, might.weight());
        assertEquals(ICON, might.minor().icon());
        assertEquals(List.of("crusher"), might.notables().stream().map(NotableDefinition::id).toList());
        assertEquals(ICON, might.notables().get(0).skill().icon());
        assertTrue(catalog.themes().get("test:mage/mana").notables().isEmpty());
        assertEquals(List.of(might), catalog.themesOf(WARRIOR));
    }

    @Test
    void readsKeystonesWithTheirPlainTextAndTheClassIcon() {
        Catalog catalog = valid(CatalogFixtures.standard()
                .keystone("test:warrior/void", keystoneJson(WARRIOR, "Void",
                        "'text':'Hidden.','icon':'test:textures/void.png','weight':3,'neighbour_multiplier':2,'hidden_until':'minecraft:story/root',"))
                .parseWithMods(MAGIC_MOD));
        KeystoneDefinition titan = catalog.keystones().get("test:warrior/titan");
        assertEquals(Optional.of("You are huge."), titan.skill().text());
        assertEquals(ICON, titan.skill().icon());
        assertEquals(2, titan.skill().effects().size());
        KeystoneDefinition hidden = catalog.keystones().get("test:warrior/void");
        assertEquals("test:textures/void.png", hidden.skill().icon());
        assertEquals(3, hidden.weight());
        assertTrue(hidden.traits().amplifiesNeighbours());
        assertEquals(Optional.of("minecraft:story/root"), hidden.traits().hiddenUntil());
        assertEquals(2, catalog.keystonesOf(WARRIOR).size());
    }

    @Test
    void rulesDefaultWhenTheFileIsAbsentOrPartial() {
        assertEquals(TreeRules.DEFAULT, valid(CatalogFixtures.standard().parse()).rules());
        TreeRules partial = valid(CatalogFixtures.standard().rules("{'nodes_per_class':60,'portals':false}").parse()).rules();
        assertEquals(new TreeRules(60, TreeRules.DEFAULT.maxClasses(), TreeRules.DEFAULT.minThemesForOwnClass(), 5, true, false), partial);
        assertEquals(100, TreeRules.DEFAULT.nodesPerClass());
    }

    @Test
    void rulesOutsideTheirRangeAreRejectedAndUnknownRulesOnlyWarn() {
        assertRejected(CatalogFixtures.standard().rules("{'nodes_per_class':150}").parse(),
                "skill_rules/main", "nodes_per_class", "12..100");
        CatalogParser.Outcome typo = CatalogFixtures.standard().rules("{'node_per_class':50}").parse();
        assertEquals(TreeRules.DEFAULT, valid(typo).rules());
        assertEquals(List.of("WARNING skill_rules/main [node_per_class]: unknown rule, ignored"), warnings(typo));
    }

    @Test
    void aClassMaySetItsOwnSize() {
        Catalog catalog = valid(CatalogFixtures.standard().classFile(WARRIOR, classJson("Warrior", "'nodes':40,")).parse());
        assertEquals(Optional.of(40), catalog.classes().get(WARRIOR).skills());
        assertRejected(CatalogFixtures.standard().classFile(WARRIOR, classJson("Warrior", "'nodes':500,")).parse(),
                "skill_classes/" + WARRIOR, "nodes", "12..100");
    }

    @Test
    void aKeystoneWithoutPlainTextIsRejected() {
        assertRejected(CatalogFixtures.standard().keystone("test:warrior/mute", keystoneJson(WARRIOR, "Mute", "")).parse(),
                "skill_keystones/test:warrior/mute", "text", "plain words");
    }

    @Test
    void hardToReadSkillsWithoutPlainTextOnlyWarn() {
        String trigger = "'notables':[" + notableJson("reaper", ON_KILL + "," + HEALTH) + "],";
        String threeEffects = "'notables':[" + notableJson("bulwark", HEALTH + "," + ARMOR + "," + HEALTH) + "],";
        CatalogParser.Outcome outcome = CatalogFixtures.standard()
                .theme("test:warrior/reaping", themeJson(WARRIOR, "Reaping", trigger))
                .theme("test:warrior/walls", themeJson(WARRIOR, "Walls", threeEffects))
                .parse();
        valid(outcome);
        assertEquals(List.of(
                "WARNING skill_themes/test:warrior/reaping [notables[0].text]: " + NEEDS_PLAIN_WORDS,
                "WARNING skill_themes/test:warrior/walls [notables[0].text]: " + NEEDS_PLAIN_WORDS),
                warnings(outcome));
    }

    @Test
    void plainTextSilencesTheWarning() {
        String explained = "'notables':[{'id':'reaper','name':'Reaper','text':'Killing heals you.','effects':[" + ON_KILL + "," + HEALTH + "]}],";
        String alone = "'notables':[" + notableJson("lone", ON_KILL) + "],";
        assertTrue(warnings(CatalogFixtures.standard().theme("test:warrior/lone", themeJson(WARRIOR, "Lone", alone)).parse()).isEmpty());
        CatalogParser.Outcome outcome = CatalogFixtures.standard().theme("test:warrior/reaping", themeJson(WARRIOR, "Reaping", explained)).parse();
        assertTrue(warnings(outcome).isEmpty(), () -> warnings(outcome).toString());
        assertEquals(Optional.of("Killing heals you."),
                valid(outcome).themes().get("test:warrior/reaping").notables().get(0).skill().text());
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

    @Test
    void entriesThatRequireAMissingModAreSkippedBeforeTheirIdsAreChecked() {
        KnownIds onlyVanilla = new KnownIds() {
            @Override
            public boolean isAttribute(String id) {
                return Ids.namespace(id).equals("minecraft");
            }

            @Override
            public boolean isMobEffect(String id) {
                return true;
            }
        };
        String modded = "{'class':'" + WARRIOR + "','requires_mod':'" + MAGIC_MOD + "','name':'Mana','icon':'" + ICON
                + "','minor':{'effects':[{'type':'attribute','attribute':'magicmod:max_mana','operation':'add_value','amount':5}]}}";
        CatalogSources sources = CatalogFixtures.standard().theme("test:warrior/mana", modded).sources();
        CatalogParser parser = new CatalogParser(onlyVanilla);
        assertFalse(valid(parser.parse(sources.availableWith(mod -> false))).themes().containsKey("test:warrior/mana"));
        assertRejected(parser.parse(sources.availableWith(mod -> true)), "skill_themes/test:warrior/mana", "minor.effects[0].attribute",
                "unknown attribute 'magicmod:max_mana'");
    }

    @Test
    void aThemeOrKeystoneOfAnUnknownClassIsRejected() {
        assertRejected(CatalogFixtures.standard().theme("test:rogue/stealth", themeJson("test:rogue", "Stealth", "")).parse(),
                "skill_themes/test:rogue/stealth", "class", "unknown class 'test:rogue'");
        assertRejected(CatalogFixtures.standard().keystone("test:rogue/shadow", keystoneJson("test:rogue", "Shadow", "'text':'Dark.','icon':'" + ICON + "',")).parse(),
                "skill_keystones/test:rogue/shadow", "class", "unknown class 'test:rogue'");
    }

    @Test
    void aFallbackToAClassWithoutItsModsIsForgottenButAnUnknownOneIsRejected() {
        CatalogFixtures hunterFallsBackToMage = CatalogFixtures.standard()
                .classFile("test:hunter", classJson("Hunter", "'fallback_class':'" + MAGE + "',"));
        assertEquals(Optional.empty(), valid(hunterFallsBackToMage.parseWithMods()).classes().get("test:hunter").fallbackClass());
        assertEquals(Optional.of(MAGE), valid(hunterFallsBackToMage.parseWithMods(MAGIC_MOD)).classes().get("test:hunter").fallbackClass());
        assertRejected(CatalogFixtures.standard().classFile("test:hunter", classJson("Hunter", "'fallback_class':'test:nobody',")).parse(),
                "skill_classes/test:hunter", "fallback_class", "unknown class 'test:nobody'");
    }

    @Test
    void fallbackClassesMayNotLoop() {
        CatalogParser.Outcome outcome = CatalogFixtures.standard()
                .classFile(WARRIOR, classJson("Warrior", "'fallback_class':'" + MAGE + "',")).parseWithMods(MAGIC_MOD);
        assertRejected(outcome, "skill_classes/" + WARRIOR, "fallback_class", "loops back");
        assertRejected(outcome, "skill_classes/" + MAGE, "fallback_class", "loops back");
    }

    @Test
    void everySkillNeedsAtLeastOneEffect() {
        String noEffects = "{'class':'" + WARRIOR + "','name':'Empty','icon':'" + ICON + "','minor':{'effects':[]}}";
        assertRejected(CatalogFixtures.standard().theme("test:warrior/empty", noEffects).parse(),
                "skill_themes/test:warrior/empty", "minor.effects", "at least one effect");
        String missingTrunk = "{'name':'Hollow','icon':'" + ICON + "','hub':{'effects':[" + HEALTH + "]}}";
        assertRejected(CatalogFixtures.standard().classFile("test:hollow", missingTrunk).parse(),
                "skill_classes/test:hollow", "trunk", "missing object");
    }

    @Test
    void notableIdsAreLocalUniqueAndShort() {
        String duplicated = "'notables':[" + notableJson("crusher", HEALTH) + "," + notableJson("crusher", ARMOR) + "],";
        assertRejected(CatalogFixtures.standard().theme("test:warrior/twice", themeJson(WARRIOR, "Twice", duplicated)).parse(),
                "skill_themes/test:warrior/twice", "notables[1].id", "duplicate notable 'crusher'");
        String namespaced = "'notables':[" + notableJson("test:crusher", HEALTH) + "],";
        assertRejected(CatalogFixtures.standard().theme("test:warrior/odd", themeJson(WARRIOR, "Odd", namespaced)).parse(),
                "skill_themes/test:warrior/odd", "notables[0].id", "lowercase letters");
        String longId = "'notables':[" + notableJson("a".repeat(55), HEALTH) + "],";
        assertRejected(CatalogFixtures.standard().theme("test:warrior/long_theme", themeJson(WARRIOR, "Long", longId)).parse(),
                "skill_themes/test:warrior/long_theme", "notables[0].id", "64 characters");
    }

    @Test
    void aCatalogWithoutAnyUsableClassIsRejected() {
        assertRejected(CatalogFixtures.empty().parse(), "skill_classes", "", "no usable class");
        assertRejected(CatalogFixtures.empty().classFile(WARRIOR, "[]").parse(), "skill_classes/" + WARRIOR, "", "must be a JSON object");
    }

    @Test
    void aBrokenClassDoesNotAlsoBlameItsThemes() {
        CatalogParser.Outcome outcome = CatalogFixtures.standard().classFile(WARRIOR, "{'name':'Warrior'}").parse();
        assertTrue(outcome.catalog().isEmpty());
        assertTrue(outcome.report().errors().stream().allMatch(issue -> issue.source().startsWith("skill_classes")),
                () -> outcome.report().errors().toString());
    }
}
