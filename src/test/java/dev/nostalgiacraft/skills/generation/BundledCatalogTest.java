package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.KnownIds;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledCatalogTest {
    private static final String NS = "nostalgiaskills";
    private static final String TREE = NS + ":main";
    private static final Path DATA = Path.of(System.getProperty("nostalgiaskills.dataRoot", "src/main/resources/data/nostalgiaskills"));
    private static final Path LANG = Path.of("src/main/resources/assets/nostalgiaskills/lang");
    private static final int SEEDS = 25;
    private static final List<String> BASE_CLASSES = List.of("arcanist", "artificer", "berserker", "duelist", "guardian", "marksman");
    private static final Set<String> OWN_ATTRIBUTES = Set.of("crit_chance", "crit_damage", "dodge_chance",
            "light_weapon_damage", "heavy_weapon_damage", "projectile_damage", "long_range_damage", "crossbow_damage",
            "charged_shot_damage", "mining_speed", "tool_preservation", "potion_duration", "magic_resistance",
            "healing_received", "lifesteal", "damage_reduction", "low_health_damage");
    private static final KnownIds WITHOUT_MODS = new KnownIds() {
        @Override
        public boolean isAttribute(String id) {
            return id.startsWith("minecraft:generic.") || id.startsWith("minecraft:player.")
                    || (id.startsWith(NS + ":") && OWN_ATTRIBUTES.contains(Ids.path(id)));
        }

        @Override
        public boolean isMobEffect(String id) {
            return id.startsWith("minecraft:");
        }
    };

    private static Map<String, JsonElement> files(String directory) {
        Map<String, JsonElement> files = new TreeMap<>();
        Path base = DATA.resolve(directory);
        try (Stream<Path> paths = Files.walk(base)) {
            for (Path path : paths.filter(found -> found.toString().endsWith(".json")).toList()) {
                String relative = base.relativize(path).toString().replace('\\', '/');
                files.put(NS + ":" + relative.substring(0, relative.length() - ".json".length()), read(path));
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
        return files;
    }

    private static JsonElement read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Catalog catalog(KnownIds known, Predicate<String> isModLoaded) {
        CatalogSources sources = new CatalogSources(files("skill_classes"), files("skill_themes"), files("skill_keystones"), Optional.empty());
        CatalogParser.Outcome outcome = new CatalogParser(known).parse(sources.availableWith(isModLoaded));
        assertTrue(outcome.report().issues().isEmpty(), () -> outcome.report().issues().toString());
        return outcome.catalog().orElseThrow();
    }

    private static SkillGraphSnapshot tree(Catalog catalog, long seed) {
        TreeGenerator.Result result = TreeGenerator.generate(TREE, catalog, seed, TreeDraw.EMPTY);
        assertTrue(result.report().issues().isEmpty(), () -> "seed " + seed + ": " + result.report().issues());
        return result.graph().orElseThrow();
    }

    private static long count(SkillGraphSnapshot graph, String classId, NodeType type) {
        return graph.nodes().values().stream().filter(node -> node.sector().equals(classId) && node.type() == type).count();
    }

    @Test
    void withoutOtherModsEveryWorldGetsTheSixClassesComplete() {
        Catalog catalog = catalog(WITHOUT_MODS, mod -> false);
        assertEquals(BASE_CLASSES.stream().map(id -> NS + ":" + id).toList(), List.copyOf(catalog.classes().keySet()));
        for (long seed = 0; seed < SEEDS; seed++) {
            SkillGraphSnapshot graph = tree(catalog, seed);
            assertEquals(6, graph.roots().size());
            for (String classId : catalog.classes().keySet()) {
                assertEquals(12, count(graph, classId, NodeType.NOTABLE), classId);
                assertEquals(3, count(graph, classId, NodeType.KEYSTONE), classId);
                assertEquals(83, count(graph, classId, NodeType.MINOR), classId);
            }
        }
    }

    @Test
    void everySkillHasAnEffectAndEveryKeystoneSpeaksPlainly() {
        SkillGraphSnapshot graph = tree(catalog(KnownIds.PERMISSIVE, mod -> true), 1);
        for (SkillNodeDefinition node : graph.nodes().values()) {
            assertEquals(node.type() == NodeType.PORTAL, node.effects().isEmpty(), node.id());
            if (node.type() == NodeType.KEYSTONE) {
                assertFalse(node.descriptionKey().isEmpty(), node.id());
            }
        }
    }

    @Test
    void withEveryIntegratedModTheMageJoinsAndTreesStayValid() {
        Catalog catalog = catalog(KnownIds.PERMISSIVE, mod -> true);
        assertTrue(catalog.classes().containsKey(NS + ":mage"));
        for (long seed = 0; seed < SEEDS; seed++) {
            SkillGraphSnapshot graph = tree(catalog, seed);
            assertEquals(7, graph.roots().size());
            assertTrue(graph.nodes().values().stream().filter(node -> node.type() != NodeType.PORTAL).count() <= 7 * 100);
            assertEquals(8, graph.nodes().values().stream().filter(node -> node.type() == NodeType.PORTAL).count());
        }
    }

    @Test
    void everyThemeNotableAndKeystoneOfTheCatalogAppearsInEveryWorld() {
        Catalog catalog = catalog(KnownIds.PERMISSIVE, mod -> true);
        for (long seed = 0; seed < SEEDS; seed++) {
            SkillGraphSnapshot graph = tree(catalog, seed);
            for (ThemeDefinition theme : catalog.themes().values()) {
                assertTrue(graph.contains(theme.id() + "_1"), () -> "theme " + theme.id());
                for (NotableDefinition notable : theme.notables()) {
                    assertTrue(graph.contains(CatalogEntryParser.notableNodeId(theme.id(), notable.id())), () -> "notable " + notable.id());
                }
            }
            catalog.keystones().keySet().forEach(keystone -> assertTrue(graph.contains(keystone), keystone));
        }
    }

    @Test
    void aLoneMagicModLendsItsThemesToTheArcanist() {
        Catalog catalog = catalog(KnownIds.PERMISSIVE, Set.of("lodestone")::contains);
        assertTrue(catalog.classes().containsKey(NS + ":mage"));
        List<ClassPool> pools = ClassPool.select(catalog);
        assertEquals(6, pools.size());
        ClassPool arcanist = pools.stream().filter(pool -> pool.definition().id().equals(NS + ":arcanist")).findFirst().orElseThrow();
        assertTrue(arcanist.theme(NS + ":mage/lodestar").isPresent());
    }

    @Test
    void theExampleDatapackExtendsTheShippedCatalog() throws IOException {
        Path example = Path.of("examples/datapacks/nostalgiaskills_catalog_example/data");
        Map<String, JsonElement> themes = files("skill_themes");
        Map<String, JsonElement> keystones = files("skill_keystones");
        themes.put("examplepack:guardian/thorns", read(example.resolve("examplepack/skill_themes/guardian/thorns.json")));
        keystones.put("examplepack:guardian/sleeping_giant",
                read(example.resolve("examplepack/skill_keystones/guardian/sleeping_giant.json")));
        JsonElement rules = read(example.resolve("nostalgiaskills/skill_rules/main.json"));
        CatalogSources sources = new CatalogSources(files("skill_classes"), themes, keystones, Optional.of(rules));
        CatalogParser.Outcome outcome = new CatalogParser(WITHOUT_MODS).parse(sources.availableWith(mod -> false));
        assertTrue(outcome.report().issues().isEmpty(), () -> outcome.report().issues().toString());
        Catalog catalog = outcome.catalog().orElseThrow();
        assertEquals(60, catalog.rules().nodesPerClass());
        assertTrue(catalog.keystones().get("examplepack:guardian/sleeping_giant").traits().hiddenUntil().isPresent());
        SkillGraphSnapshot graph = tree(catalog, 5);
        assertTrue(graph.nodes().size() <= 6 * 61);
    }

    @Test
    void differentWorldsGetDifferentTreesOnceModsAddChoice() {
        Catalog catalog = catalog(KnownIds.PERMISSIVE, mod -> true);
        assertFalse(tree(catalog, 1).fingerprint().equals(tree(catalog, 2).fingerprint()));
    }

    @Test
    void everyNameAndTextIsTranslatedInBothLanguages() throws IOException {
        SkillGraphSnapshot graph = tree(catalog(KnownIds.PERMISSIVE, mod -> true), 3);
        for (String language : List.of("en_us", "pt_br")) {
            JsonObject translations = read(LANG.resolve(language + ".json")).getAsJsonObject();
            for (SkillNodeDefinition node : graph.nodes().values()) {
                assertTrue(translations.has(node.nameKey()), () -> language + " lacks " + node.nameKey());
                assertTrue(node.descriptionKey().isEmpty() || translations.has(node.descriptionKey()),
                        () -> language + " lacks " + node.descriptionKey());
            }
            graph.sectors().forEach(sector -> assertTrue(translations.has(sector.nameKey()), sector.nameKey()));
        }
    }
}
