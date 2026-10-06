package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the complete bundled 480-node dataset exactly as the server loads it. */
class BundledTreeTest {
    static final String NS = "nostalgiaskills";
    static GraphParser.Outcome outcome;
    static SkillGraphSnapshot g;

    static final Set<String> MOD_ATTRIBUTES = Set.of("crit_chance", "crit_damage", "dodge_chance",
            "light_weapon_damage", "heavy_weapon_damage", "projectile_damage", "long_range_damage", "crossbow_damage",
            "charged_shot_damage", "mining_speed", "tool_preservation", "potion_duration", "magic_resistance",
            "healing_received", "lifesteal", "damage_reduction", "low_health_damage");

    static Map<String, JsonElement> load(Path dir) throws IOException {
        Map<String, JsonElement> out = new HashMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                out.put(NS + ":" + rel.substring(0, rel.length() - 5),
                        JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        return out;
    }

    @BeforeAll
    static void loadBundled() throws IOException {
        Path root = Path.of(System.getProperty("nostalgiaskills.dataRoot", "src/main/resources/data/nostalgiaskills"));
        KnownIds known = new KnownIds() {
            @Override
            public boolean isAttribute(String id) {
                return id.startsWith("minecraft:generic.") || id.startsWith("minecraft:player.")
                        || (id.startsWith(NS + ":") && MOD_ATTRIBUTES.contains(Ids.path(id)));
            }

            @Override
            public boolean isMobEffect(String id) {
                return id.startsWith("minecraft:");
            }
        };
        outcome = new GraphParser(known).parse(NS + ":main", new GraphParser.Sources(load(root.resolve("skill_trees")),
                load(root.resolve("skill_nodes")), load(root.resolve("skill_connections"))));
        g = outcome.snapshot().orElse(null);
    }

    @Test
    void loadsWithoutErrorsOrOverlapWarnings() {
        assertTrue(outcome.snapshot().isPresent(), () -> outcome.report().errors().toString());
        assertTrue(outcome.report().warnings().isEmpty(), () -> outcome.report().warnings().toString());
    }

    @Test
    void hasExactDistribution() {
        assertEquals(480, g.nodes().size());
        for (Sector s : g.sectors()) {
            Map<NodeType, Integer> c = new HashMap<>();
            g.nodes().values().stream().filter(n -> n.sector().equals(s.id())).forEach(n -> c.merge(n.type(), 1, Integer::sum));
            assertEquals(Map.of(NodeType.ROOT, 1, NodeType.MINOR, 64, NodeType.NOTABLE, 12, NodeType.KEYSTONE, 3), c, s.id());
        }
        assertEquals(6, g.roots().size());
        assertEquals(6, g.exclusiveGroups().size());
    }

    @Test
    void rootsUseAuthoredCoordinates() {
        Map<String, Vec2> want = Map.of("duelist", new Vec2(-180, 0), "guardian", new Vec2(-90, -156),
                "arcanist", new Vec2(90, -156), "marksman", new Vec2(180, 0), "artificer", new Vec2(90, 156),
                "berserker", new Vec2(-90, 156));
        want.forEach((s, v) -> assertEquals(v, g.nodes().get(NS + ":" + s + "/hub").position(), s));
    }

    @Test
    void everyNodeHasARealEffect() {
        for (SkillNodeDefinition n : g.nodes().values()) {
            assertFalse(n.effects().isEmpty(), n.id());
        }
    }

    @Test
    void keystonesCombineAdvantageWithDrawback() {
        for (SkillNodeDefinition n : g.nodes().values()) {
            if (n.type() != NodeType.KEYSTONE) {
                continue;
            }
            assertTrue(n.effects().size() >= 2, n.id());
        }
    }

    @Test
    void adjacentRootsAreAtLeastEightPaidPointsApart() {
        List<String> order = List.of("marksman", "artificer", "berserker", "duelist", "guardian", "arcanist");
        for (int i = 0; i < 6; i++) {
            String a = NS + ":" + order.get(i) + "/hub";
            String b = NS + ":" + order.get((i + 1) % 6) + "/hub";
            int c = cheapest(a, b);
            assertTrue(c >= 8, a + " -> " + b + " costs " + c);
        }
    }

    static int cheapest(String from, String to) {
        Map<String, Integer> dist = new HashMap<>();
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(Map.Entry.comparingByValue());
        dist.put(from, 0);
        pq.add(Map.entry(from, 0));
        while (!pq.isEmpty()) {
            Map.Entry<String, Integer> e = pq.poll();
            if (e.getKey().equals(to)) {
                return e.getValue();
            }
            if (e.getValue() > dist.get(e.getKey())) {
                continue;
            }
            for (String m : g.neighbours(e.getKey())) {
                int nd = e.getValue() + g.nodes().get(m).cost();
                if (nd < dist.getOrDefault(m, Integer.MAX_VALUE)) {
                    dist.put(m, nd);
                    pq.add(Map.entry(m, nd));
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    @Test
    void connectionsReferenceTheSameGraphUsedForEligibility() {
        for (SkillConnection c : g.connections()) {
            assertTrue(g.accessFrom(c.b()).contains(c.a()));
            assertTrue(g.accessFrom(c.a()).contains(c.b()));
        }
    }
}
