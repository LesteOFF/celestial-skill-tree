package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.TreeDraw.ClassDraw;
import dev.nostalgiacraft.skills.generation.TreeDraw.RibDraw;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.nostalgiacraft.skills.generation.CatalogFixtures.classJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.keystoneJson;
import static dev.nostalgiacraft.skills.generation.CatalogFixtures.themeJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeGeneratorTest {
    private static final String TREE = "test:main";
    private static final long SEED = 42;
    private static final int ENOUGH_THEMES = 18;
    private static final int MAX_COPIES = 5;

    private static TreeGenerator.Result generate(Catalog catalog, long seed, TreeDraw previous) {
        return TreeGenerator.generate(TREE, catalog, seed, previous);
    }

    private static SkillGraphSnapshot graph(TreeGenerator.Result result) {
        assertTrue(result.graph().isPresent(), () -> result.report().errors().toString());
        return result.graph().get();
    }

    private static long count(SkillGraphSnapshot graph, String sector, NodeType type) {
        return graph.nodes().values().stream().filter(node -> node.sector().equals(sector) && node.type() == type).count();
    }

    private static List<List<String>> runs(ClassDraw drawn) {
        List<List<String>> runs = new ArrayList<>();
        runs.add(drawn.trunk());
        drawn.ribs().values().forEach(rib -> runs.add(rib.minors()));
        return runs;
    }

    @Test
    void sixFullClassesBecomeAValidTreeWithoutWarnings() {
        TreeGenerator.Result result = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), SEED, TreeDraw.EMPTY);
        SkillGraphSnapshot graph = graph(result);
        assertTrue(result.report().issues().isEmpty(), () -> result.report().issues().toString());
        assertEquals(6, graph.roots().size());
        assertEquals(6 * 100, graph.nodes().size());
        assertEquals(ENOUGH_THEMES, count(graph, "test:c0", NodeType.NOTABLE));
        assertEquals(3, count(graph, "test:c0", NodeType.KEYSTONE));
        assertEquals(1, count(graph, "test:c0", NodeType.PORTAL));
        assertEquals(19 + 76 - ENOUGH_THEMES, count(graph, "test:c0", NodeType.MINOR));
    }

    @Test
    void everyNotableAndKeystoneOfTheCatalogAppears() {
        Catalog catalog = CatalogFixtures.world(6, ENOUGH_THEMES, 1, 7).catalog();
        for (long seed = 0; seed < 10; seed++) {
            SkillGraphSnapshot graph = graph(generate(catalog, seed, TreeDraw.EMPTY));
            for (int theme = 0; theme < ENOUGH_THEMES; theme++) {
                assertTrue(graph.contains("test:c0/n" + theme), "notable " + theme);
            }
            for (int keystone = 0; keystone < 7; keystone++) {
                assertTrue(graph.contains("test:c0/k" + keystone), "keystone " + keystone);
            }
            assertTrue(graph.nodes().values().stream().filter(node -> node.sector().equals("test:c0")
                    && node.type() != NodeType.PORTAL).count() <= 100);
        }
    }

    @Test
    void aClassWithManyKeystonesPlacesAllOfThem() {
        Catalog catalog = CatalogFixtures.world(6, ENOUGH_THEMES, 1, 30).catalog();
        for (long seed = 0; seed < 5; seed++) {
            SkillGraphSnapshot graph = graph(generate(catalog, seed, TreeDraw.EMPTY));
            for (int keystone = 0; keystone < 30; keystone++) {
                assertTrue(graph.contains("test:c0/k" + keystone), "keystone " + keystone);
            }
            assertEquals(30, count(graph, "test:c0", NodeType.KEYSTONE));
        }
    }

    @Test
    void anExtraNotableOpensABranchFollowedByASkillOfItsOwnTheme() {
        TreeDraw draw = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), SEED, TreeDraw.EMPTY).draw();
        ClassDraw drawn = draw.classes().get(0);
        long openings = drawn.ribs().values().stream().filter(rib -> rib.opening().isPresent()).count();
        assertEquals(ENOUGH_THEMES - 12, openings);
        assertTrue(drawn.ribs().get("5a").opening().isPresent());
        assertTrue(drawn.ribs().get("1a").opening().isEmpty());
        for (RibDraw rib : drawn.ribs().values()) {
            rib.opening().ifPresent(opening -> assertEquals(opening.theme(), rib.minors().get(0)));
        }
    }

    @Test
    void everySkillOfATrunkOrBranchIsDifferentFromTheOthersInIt() {
        for (long seed = 0; seed < 20; seed++) {
            TreeDraw draw = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), seed, TreeDraw.EMPTY).draw();
            for (ClassDraw drawn : draw.classes()) {
                for (List<String> run : runs(drawn)) {
                    assertEquals(run.size(), new HashSet<>(run).size(), drawn.classId() + " " + run);
                }
            }
        }
    }

    @Test
    void everyThemeOfTheClassIsUsedBeforeAnyIsUsedTwiceInARow() {
        TreeDraw draw = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), SEED, TreeDraw.EMPTY).draw();
        ClassDraw drawn = draw.classes().get(0);
        assertEquals(ENOUGH_THEMES + 1, new HashSet<>(drawn.trunk()).size());
        Map<String, Integer> copies = new HashMap<>();
        runs(drawn).forEach(run -> run.forEach(skill -> copies.merge(skill, 1, Integer::sum)));
        assertEquals(ENOUGH_THEMES + 1, copies.size());
        assertTrue(copies.values().stream().allMatch(count -> count >= 3), copies.toString());
    }

    @Test
    void aSmallClassStillNeverPutsTheSameSkillSideBySide() {
        TreeDraw draw = generate(CatalogFixtures.world(6, 6, 2, 3).catalog(), SEED, TreeDraw.EMPTY).draw();
        for (ClassDraw drawn : draw.classes()) {
            for (List<String> run : runs(drawn)) {
                for (int position = 1; position < run.size(); position++) {
                    assertNotEquals(run.get(position - 1), run.get(position), drawn.classId() + " " + run);
                }
            }
        }
    }

    @Test
    void noSkillAppearsMoreThanFiveTimes() {
        for (long seed = 0; seed < 20; seed++) {
            TreeGenerator.Result result = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), seed, TreeDraw.EMPTY);
            for (ClassDraw drawn : result.draw().classes()) {
                Map<String, Integer> copies = new HashMap<>();
                runs(drawn).forEach(run -> run.forEach(skill -> copies.merge(skill, 1, Integer::sum)));
                assertTrue(copies.values().stream().allMatch(count -> count <= MAX_COPIES), copies.toString());
            }
            SkillGraphSnapshot graph = graph(result);
            assertFalse(graph.contains("test:c0/trunk_6"));
            assertTrue(graph.ids().stream().noneMatch(id -> id.matches(".*/t\\d+_6")));
        }
    }

    @Test
    void theSkillNextToANotableIsOfTheSameTheme() {
        TreeDraw draw = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), SEED, TreeDraw.EMPTY).draw();
        for (RibDraw rib : draw.classes().get(0).ribs().values()) {
            assertEquals(rib.notable().orElseThrow().theme(), rib.minors().get(rib.minors().size() - 1));
        }
        assertEquals(TreeDraw.TRUNK_SKILL, draw.classes().get(0).trunk().get(0));
    }

    @Test
    void aClassWithFewThemesShrinksInsteadOfRepeatingMore() {
        TreeGenerator.Result result = generate(CatalogFixtures.world(6, 6, 2, 3).catalog(), SEED, TreeDraw.EMPTY);
        SkillGraphSnapshot graph = graph(result);
        assertTrue(result.report().issues().isEmpty(), () -> result.report().issues().toString());
        long minors = count(graph, "test:c0", NodeType.MINOR);
        assertTrue(minors <= 7 * MAX_COPIES && minors >= 7 * MAX_COPIES - 8, () -> "minors " + minors);
        assertTrue(graph.ids().stream().noneMatch(id -> id.matches(".*_6")));
    }

    @Test
    void theCopyLimitIsARule() {
        Catalog catalog = CatalogFixtures.world(6, 9, 2, 3).rules("{'max_copies_per_skill':10}").catalog();
        SkillGraphSnapshot graph = graph(generate(catalog, SEED, TreeDraw.EMPTY));
        assertEquals(19 + 76 - 18, count(graph, "test:c0", NodeType.MINOR));
        assertTrue(graph.ids().stream().anyMatch(id -> id.matches(".*/t\\d+_6")));
    }

    @Test
    void theLargestAllowedTreeFitsTheNetworkBudget() throws Exception {
        Catalog catalog = CatalogFixtures.world(16, ENOUGH_THEMES, 1, 3).rules("{'max_classes':16}").catalog();
        SkillGraphSnapshot graph = graph(generate(catalog, SEED, TreeDraw.EMPTY));
        byte[] encoded = GraphCodec.encode(graph);
        assertTrue(encoded.length < GraphCodec.MAX_BYTES, () -> "payload " + encoded.length);
        assertEquals(graph.fingerprint(), GraphCodec.decode(encoded).fingerprint());
    }

    @Test
    void skillsTakeTheirNameAndIdFromTheirTheme() {
        SkillGraphSnapshot graph = graph(generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3).catalog(), SEED, TreeDraw.EMPTY));
        assertEquals(NodeType.ROOT, graph.node("test:c0/hub").orElseThrow().type());
        assertEquals("Class 0 Training", graph.node("test:c0/trunk_1").orElseThrow().nameKey());
        SkillNodeDefinition notable = graph.nodes().values().stream().filter(node -> node.type() == NodeType.NOTABLE).findFirst().orElseThrow();
        assertTrue(notable.id().matches("test:c\\d/n\\d+"), notable.id());
        assertEquals("", notable.descriptionKey());
        SkillNodeDefinition minor = graph.nodes().values().stream()
                .filter(node -> node.id().matches("test:c0/t\\d+_3")).findFirst().orElseThrow();
        assertEquals("Theme " + minor.id().replaceAll("test:c0/t(\\d+)_3", "$1"), minor.nameKey());
        assertTrue(graph.contains(minor.id().replace("_3", "_2")) && graph.contains(minor.id().replace("_3", "_1")));
    }

    @Test
    void theSameSeedGivesTheSameTreeAndAnotherSeedADifferentOne() {
        Catalog catalog = CatalogFixtures.world(6, ENOUGH_THEMES, 1, 5).catalog();
        TreeGenerator.Result first = generate(catalog, SEED, TreeDraw.EMPTY);
        assertEquals(graph(first).fingerprint(), graph(generate(catalog, SEED, TreeDraw.EMPTY)).fingerprint());
        assertEquals(first.draw(), generate(catalog, SEED, TreeDraw.EMPTY).draw());
        assertNotEquals(first.draw(), generate(catalog, SEED + 1, TreeDraw.EMPTY).draw());
    }

    @Test
    void aPackMayUseOneTreeForEveryWorld() {
        Catalog catalog = CatalogFixtures.world(6, ENOUGH_THEMES, 1, 5).rules("{'vary_by_seed':false}").catalog();
        assertEquals(generate(catalog, 1, TreeDraw.EMPTY).draw(), generate(catalog, 2, TreeDraw.EMPTY).draw());
    }

    @Test
    void aClassWithTooFewThemesLendsItsContentToItsFallback() {
        Catalog catalog = CatalogFixtures.world(3, 6, 2, 3)
                .classFile("test:small", classJson("Small", "'fallback_class':'test:c0',"))
                .theme("test:small/only", themeJson("test:small", "Only", "'weight':1000,'notables':[" + CatalogFixtures.notableJson("gift", CatalogFixtures.HEALTH) + "],"))
                .keystone("test:small/relic", keystoneJson("test:small", "Relic", "'text':'Plain.','weight':1000,"))
                .catalog();
        SkillGraphSnapshot graph = graph(generate(catalog, SEED, TreeDraw.EMPTY));
        assertEquals(3, graph.roots().size());
        assertFalse(graph.contains("test:small/hub"));
        assertEquals("test:c0", graph.node("test:small/gift").orElseThrow().sector());
        assertEquals("test:c0", graph.node("test:small/relic").orElseThrow().sector());
    }

    @Test
    void contentOfAClassWithoutFallbackThatDoesNotEnterIsLeftOut() {
        Catalog catalog = CatalogFixtures.world(2, 6, 2, 3)
                .classFile("test:small", classJson("Small", ""))
                .theme("test:small/only", themeJson("test:small", "Only", "'weight':1000,"))
                .catalog();
        SkillGraphSnapshot graph = graph(generate(catalog, SEED, TreeDraw.EMPTY));
        assertTrue(graph.ids().stream().noneMatch(id -> id.startsWith("test:small")));
    }

    @Test
    void whenThereAreTooManyClassesTheHighestPriorityStay() {
        Catalog catalog = CatalogFixtures.world(4, 6, 2, 3)
                .classFile("test:c3", classJson("Class 3", "'priority':9,"))
                .rules("{'max_classes':2}").catalog();
        SkillGraphSnapshot graph = graph(generate(catalog, SEED, TreeDraw.EMPTY));
        assertEquals(List.of("test:c0/hub", "test:c3/hub"), graph.roots());
    }

    @Test
    void ribsBeyondTheAvailableNotablesEndInAnOrdinarySkill() {
        SkillGraphSnapshot graph = graph(generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 3)
                .withoutThemeNotables("test:c0", 6).catalog(), SEED, TreeDraw.EMPTY));
        assertEquals(6, count(graph, "test:c0", NodeType.NOTABLE));
        assertEquals(19 + 70, count(graph, "test:c0", NodeType.MINOR));
    }

    @Test
    void aClassMayBeSmallerThanTheOthers() {
        Catalog catalog = CatalogFixtures.world(3, ENOUGH_THEMES, 1, 3).classFile("test:c1", classJson("Class 1", "'nodes':30,")).catalog();
        SkillGraphSnapshot graph = graph(generate(catalog, SEED, TreeDraw.EMPTY));
        long small = graph.nodes().values().stream().filter(node -> node.sector().equals("test:c1") && node.type() != NodeType.PORTAL).count();
        assertTrue(small <= 30 && small >= 24, () -> "class of " + small);
    }

    @Test
    void removingAThemeRedrawsOnlyTheSkillsThatUsedIt() {
        CatalogFixtures fixtures = CatalogFixtures.world(6, 20, 1, 5);
        TreeDraw before = generate(fixtures.catalog(), SEED, TreeDraw.EMPTY).draw();
        ClassDraw classBefore = before.of("test:c0").orElseThrow();
        String removed = classBefore.ribs().get("3a").notable().orElseThrow().theme();
        TreeDraw after = generate(fixtures.withoutTheme(removed).catalog(), SEED, before).draw();
        ClassDraw classAfter = after.of("test:c0").orElseThrow();
        assertTrue(runs(classAfter).stream().noneMatch(run -> run.contains(removed)));
        int kept = 0;
        int total = 0;
        for (Map.Entry<String, RibDraw> rib : classBefore.ribs().entrySet()) {
            RibDraw now = classAfter.ribs().get(rib.getKey());
            if (!rib.getValue().notable().orElseThrow().theme().equals(removed)) {
                assertEquals(rib.getValue().notable(), now.notable(), rib.getKey());
            }
            for (int position = 0; position < Math.min(now.minors().size(), rib.getValue().minors().size()); position++) {
                total++;
                kept += now.minors().get(position).equals(rib.getValue().minors().get(position)) ? 1 : 0;
            }
        }
        int unchanged = kept;
        int compared = total;
        assertTrue(unchanged >= compared - 3 * MAX_COPIES, () -> unchanged + " of " + compared + " skills kept");
        for (int classIndex = 1; classIndex < 6; classIndex++) {
            assertEquals(before.of("test:c" + classIndex), after.of("test:c" + classIndex));
        }
    }

    @Test
    void addingAClassKeepsEveryChoiceThatStillFits() {
        TreeDraw before = generate(CatalogFixtures.world(5, ENOUGH_THEMES, 1, 5).catalog(), SEED, TreeDraw.EMPTY).draw();
        TreeGenerator.Result after = generate(CatalogFixtures.world(6, ENOUGH_THEMES, 1, 5).catalog(), SEED, before);
        graph(after);
        for (ClassDraw drawn : before.classes()) {
            ClassDraw now = after.draw().of(drawn.classId()).orElseThrow();
            assertEquals(drawn.keystones(), now.keystones());
            now.ribs().forEach((rib, choice) -> {
                if (drawn.ribs().containsKey(rib)) {
                    assertEquals(drawn.ribs().get(rib).notable(), choice.notable(), rib);
                }
            });
        }
        assertEquals(6, after.draw().classes().size());
    }

    @Test
    void aRemovedKeystoneIsReplacedAndTheOthersStay() {
        CatalogFixtures fixtures = CatalogFixtures.world(3, 6, 2, 6);
        TreeDraw before = generate(fixtures.catalog(), SEED, TreeDraw.EMPTY).draw();
        List<String> keystonesBefore = before.of("test:c0").orElseThrow().keystones();
        TreeDraw after = generate(fixtures.withoutKeystone(keystonesBefore.get(1)).catalog(), SEED, before).draw();
        List<String> keystonesAfter = after.of("test:c0").orElseThrow().keystones();
        assertEquals(keystonesBefore.get(0), keystonesAfter.get(0));
        assertEquals(keystonesBefore.get(2), keystonesAfter.get(2));
        assertFalse(keystonesAfter.contains(keystonesBefore.get(1)));
        assertEquals(5, new HashSet<>(keystonesAfter).size());
    }

    @Test
    void aCatalogWhereNoClassQualifiesIsRejectedWithAReason() {
        TreeGenerator.Result result = generate(CatalogFixtures.world(3, 2, 1, 1).catalog(), SEED, TreeDraw.EMPTY);
        assertTrue(result.graph().isEmpty());
        assertTrue(result.report().errors().get(0).message().contains("min_themes_for_own_class"));
    }

    @Test
    void twoEntriesThatProduceTheSameSkillIdAreRejected() {
        Catalog catalog = CatalogFixtures.world(2, 6, 2, 0)
                .keystone("test:c0/hub", keystoneJson("test:c0", "Impostor", "'text':'Plain.',")).catalog();
        TreeGenerator.Result result = generate(catalog, SEED, TreeDraw.EMPTY);
        assertTrue(result.graph().isEmpty());
        assertEquals("skill_nodes/test:c0/hub", result.report().errors().get(0).source());
    }

    @Test
    void theDrawOfAnOlderVersionIsIgnoredInsteadOfBreaking() {
        assertEquals(TreeDraw.EMPTY, DrawJson.read("[{\"class\":\"test:c0\",\"ribs\":{},\"keystones\":[]}]"));
        assertEquals(Optional.empty(), TreeDraw.EMPTY.of("test:c0"));
    }
}
