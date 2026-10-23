package dev.lesteoff.celestialskilltree.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.ExclusiveGroup;
import dev.lesteoff.celestialskilltree.graph.Ids;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import dev.lesteoff.celestialskilltree.graph.NodeType;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledCatalogTest {
    private static final String NS = "celestialskilltree";
    private static final String TREE = NS + ":main";
    private static final Path DATA = Path.of(System.getProperty("celestialskilltree.dataRoot", "src/main/resources/data/celestialskilltree"));
    private static final Path LANG = Path.of("src/main/resources/assets/celestialskilltree/lang");
    private static final int SEEDS = 25;
    private static final int PAIRED_KEYSTONES = 2;
    private static final int MAGE_NODES = 120;
    private static final List<String> NEWER_NOTABLE_FIELDS = List.of("max_rank", "requires_mod", "neighbour_multiplier");
    private static final Map<String, Integer> NOTABLES_WITHOUT_MODS = Map.of(NS + ":arcanist", 16);
    private static final List<String> BASE_CLASSES = List.of("arcanist", "artificer", "berserker", "duelist", "guardian", "marksman");
    private static final Set<String> OWN_ATTRIBUTES = Set.of("crit_chance", "crit_damage", "dodge_chance",
            "light_weapon_damage", "heavy_weapon_damage", "projectile_damage", "long_range_damage", "crossbow_damage",
            "charged_shot_damage", "mining_speed", "tool_preservation", "potion_duration", "magic_resistance",
            "healing_received", "lifesteal", "damage_reduction", "low_health_damage", "spell_echo_chance", "borrowed_keystones", "harvest_bonus");
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
        return catalog(known, isModLoaded, files("skill_themes"));
    }

    private static Catalog catalog(KnownIds known, Predicate<String> isModLoaded, Map<String, JsonElement> themes) {
        return catalog(known, isModLoaded, themes, files("skill_keystones"));
    }

    private static Catalog catalog(KnownIds known, Predicate<String> isModLoaded, Map<String, JsonElement> themes,
                                   Map<String, JsonElement> keystones) {
        CatalogSources sources = new CatalogSources(files("skill_classes"), themes, keystones, Optional.empty());
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
                int notables = NOTABLES_WITHOUT_MODS.getOrDefault(classId, 14);
                assertEquals(notables, count(graph, classId, NodeType.NOTABLE), classId);
                assertEquals(3 + PAIRED_KEYSTONES, count(graph, classId, NodeType.KEYSTONE), classId);
                assertEquals(95 - notables, count(graph, classId, NodeType.MINOR), classId);
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
            List<SkillNodeDefinition> hidden = graph.nodes().values().stream()
                    .filter(node -> node.traits().hiddenUntil().isPresent()).toList();
            double treeBottom = graph.nodes().values().stream().filter(node -> !hidden.contains(node))
                    .mapToDouble(node -> node.position().y()).max().orElseThrow();
            assertFalse(hidden.isEmpty());
            hidden.forEach(node -> assertTrue(node.position().y() >= treeBottom + HiddenSection.DISTANCE_BELOW_TREE, node.id()));
            assertTrue(graph.nodes().values().stream()
                    .filter(node -> node.type() != NodeType.PORTAL && !hidden.contains(node)).count() <= 6 * (100 + PAIRED_KEYSTONES) + MAGE_NODES + PAIRED_KEYSTONES);
            assertEquals(8, graph.nodes().values().stream().filter(node -> node.type() == NodeType.PORTAL).count());
        }
    }

    @Test
    void eachMagicModAloneBringsAMageWithItsOwnKeystones() {
        for (String magic : List.of("irons_spellbooks", "ars_nouveau", "malum")) {
            Catalog catalog = catalog(KnownIds.PERMISSIVE, magic::equals);
            boolean mageJoins = ClassPool.select(catalog).stream().anyMatch(pool -> pool.definition().id().equals(NS + ":mage"));
            assertTrue(mageJoins, magic);
            assertTrue(catalog.keystonesOf(NS + ":mage").size() >= 2, magic);
        }
    }

    @Test
    void anArmorNotableOnlyJoinsWithItsArmorMod() {
        assertFalse(notableIds(catalog(WITHOUT_MODS, mod -> false)).contains("abyssal_diver"));
        assertFalse(notableIds(catalog(KnownIds.PERMISSIVE, mod -> !mod.equals("aquamirae"))).contains("abyssal_diver"));
        assertTrue(notableIds(catalog(KnownIds.PERMISSIVE, mod -> true)).containsAll(
                List.of("abyssal_diver", "terrible_wrath", "aredrite_bulwark", "infinite_weave")));
    }

    private static List<String> notableIds(Catalog catalog) {
        return catalog.themes().values().stream().flatMap(theme -> theme.notables().stream()).map(NotableDefinition::id).toList();
    }

    @Test
    void reopeningAWorldKeepsItsTree() {
        reopeningKeepsTheTreeOf(catalog(WITHOUT_MODS, mod -> false));
        reopeningKeepsTheTreeOf(catalog(KnownIds.PERMISSIVE, mod -> true));
    }

    @Test
    void addingSkillsKeepsEverySkillOfAnExistingWorld() {
        Map<String, JsonElement> olderThemes = new TreeMap<>();
        files("skill_themes").forEach((id, theme) -> olderThemes.put(id, withoutNewNotables(theme.getAsJsonObject())));
        Map<String, JsonElement> olderKeystones = new TreeMap<>(files("skill_keystones"));
        olderKeystones.values().removeIf(keystone -> keystone.getAsJsonObject().has("exclusive_with"));
        Catalog before = catalog(KnownIds.PERMISSIVE, mod -> true, olderThemes, olderKeystones);
        Catalog after = catalog(KnownIds.PERMISSIVE, mod -> true);
        for (long seed = 0; seed < SEEDS; seed++) {
            TreeGenerator.Result created = TreeGenerator.generate(TREE, before, seed, TreeDraw.EMPTY);
            TreeDraw saved = DrawJson.read(DrawJson.write(created.draw()));
            Set<String> reopened = TreeGenerator.generate(TREE, after, seed, saved).graph().orElseThrow().ids();
            long seedNumber = seed;
            created.graph().orElseThrow().ids().forEach(id -> assertTrue(reopened.contains(id), "seed " + seedNumber + " lost " + id));
        }
    }

    private static JsonObject withoutNewNotables(JsonObject theme) {
        JsonObject copy = theme.deepCopy();
        if (copy.has("notables")) {
            copy.getAsJsonArray("notables").asList().removeIf(notable -> NEWER_NOTABLE_FIELDS.stream()
                    .anyMatch(notable.getAsJsonObject()::has));
        }
        return copy;
    }

    private static void reopeningKeepsTheTreeOf(Catalog catalog) {
        for (long seed = 0; seed < SEEDS; seed++) {
            TreeGenerator.Result created = TreeGenerator.generate(TREE, catalog, seed, TreeDraw.EMPTY);
            TreeDraw saved = DrawJson.read(DrawJson.write(created.draw()));
            TreeGenerator.Result reopened = TreeGenerator.generate(TREE, catalog, seed, saved);
            assertEquals(created.draw(), saved, "seed " + seed + ": the draw changes when it is saved");
            assertEquals(created.graph().orElseThrow().ids(), reopened.graph().orElseThrow().ids(), "seed " + seed + ": skills change");
            assertEquals(created.graph().orElseThrow().fingerprint(), reopened.graph().orElseThrow().fingerprint(), "seed " + seed);
        }
    }

    @Test
    void everyClassOffersOneExclusiveKeystonePairAndOneBlackHole() {
        Catalog catalog = catalog(KnownIds.PERMISSIVE, mod -> true);
        for (long seed = 0; seed < SEEDS; seed++) {
            SkillGraphSnapshot graph = tree(catalog, seed);
            for (String classId : Stream.concat(BASE_CLASSES.stream(), Stream.of("mage")).map(name -> NS + ":" + name).toList()) {
                List<ExclusiveGroup> pairs = graph.exclusiveGroups().values().stream()
                        .filter(group -> graph.nodes().get(group.members().get(0)).sector().equals(classId)).toList();
                assertEquals(1, pairs.size(), "seed " + seed + ": exclusive pairs of " + classId);
                assertTrue(pairs.get(0).members().stream().allMatch(member -> graph.nodes().get(member).type() == NodeType.KEYSTONE));
                long blackHoles = graph.nodes().values().stream()
                        .filter(node -> node.sector().equals(classId) && node.traits().amplifiesNeighbours()).count();
                assertEquals(1, blackHoles, "seed " + seed + ": black holes of " + classId);
            }
        }
    }

    @Test
    void aSavedBlackHoleInsideABranchMovesOutWhenTheWorldReopens() {
        Catalog catalog = catalog(WITHOUT_MODS, mod -> false);
        TreeGenerator.Result created = TreeGenerator.generate(TREE, catalog, 0, TreeDraw.EMPTY);
        Set<String> blackHoles = created.graph().orElseThrow().nodes().values().stream()
                .filter(node -> node.traits().amplifiesNeighbours()).map(SkillNodeDefinition::id).collect(Collectors.toSet());
        TreeDraw misplaced = new TreeDraw(created.draw().classes().stream().map(drawn -> blackHoleAtABranchStart(drawn, blackHoles)).toList());
        assertFalse(misplaced.equals(created.draw()), "a black hole was moved to the start of a branch");
        TreeGenerator.Result reopened = TreeGenerator.generate(TREE, catalog, 0, misplaced);
        assertTrue(reopened.graph().isPresent(), () -> reopened.report().issues().toString());
    }

    private static TreeDraw.ClassDraw blackHoleAtABranchStart(TreeDraw.ClassDraw drawn, Set<String> blackHoles) {
        Map<String, TreeDraw.RibDraw> ribs = new TreeMap<>(drawn.ribs());
        Optional<String> tipRib = ribs.entrySet().stream().filter(rib -> rib.getValue().notable()
                .filter(choice -> blackHoles.contains(CatalogEntryParser.notableNodeId(choice.theme(), choice.notable()))).isPresent())
                .map(Map.Entry::getKey).findFirst();
        if (tipRib.isEmpty()) {
            return drawn;
        }
        Optional<String> openedRib = ribs.entrySet().stream()
                .filter(rib -> !rib.getKey().equals(tipRib.get()) && rib.getValue().opening().isPresent()).map(Map.Entry::getKey).findFirst();
        if (openedRib.isEmpty()) {
            return drawn;
        }
        TreeDraw.RibDraw tip = ribs.get(tipRib.get());
        TreeDraw.RibDraw opened = ribs.get(openedRib.get());
        ribs.put(tipRib.get(), new TreeDraw.RibDraw(tip.opening(), tip.middle(), opened.opening(), tip.minors()));
        ribs.put(openedRib.get(), new TreeDraw.RibDraw(tip.notable(), opened.middle(), opened.notable(), opened.minors()));
        return new TreeDraw.ClassDraw(drawn.classId(), drawn.trunk(), ribs, drawn.keystones());
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
