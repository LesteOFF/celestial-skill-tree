package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeGeneratorTest {
    private static final String TREE = "test:main";
    private static final long SEED = 42;
    private static final int ENOUGH_THEMES = 18;

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
    void theSameSeedGivesTheSameTreeAndAnotherSeedADifferentOne() {
        Catalog catalog = CatalogFixtures.world(6, ENOUGH_THEMES, 1, 5).catalog();
        TreeGenerator.Result first = generate(catalog, SEED, TreeDraw.EMPTY);
        assertEquals(graph(first).fingerprint(), graph(generate(catalog, SEED, TreeDraw.EMPTY)).fingerprint());
        assertEquals(first.draw(), generate(catalog, SEED, TreeDraw.EMPTY).draw());
        assertNotEquals(first.draw(), generate(catalog, SEED + 1, TreeDraw.EMPTY).draw());
    }
}
