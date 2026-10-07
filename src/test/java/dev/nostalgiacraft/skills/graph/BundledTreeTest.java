package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RefundService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

class BundledTreeTest {
    static final String NS = "nostalgiaskills";
    static GraphParser.Outcome outcome;
    static SkillGraphSnapshot g;

    static final Set<String> MOD_ATTRIBUTES = Set.of("crit_chance", "crit_damage", "dodge_chance",
            "light_weapon_damage", "heavy_weapon_damage", "projectile_damage", "long_range_damage", "crossbow_damage",
            "charged_shot_damage", "mining_speed", "tool_preservation", "potion_duration", "magic_resistance",
            "healing_received", "lifesteal", "damage_reduction", "low_health_damage");

    static Map<String, JsonElement> load(Path dir) throws IOException {
        return load(dir, mod -> false);
    }

    static Map<String, JsonElement> load(Path dir, Predicate<String> isModLoaded) throws IOException {
        Map<String, JsonElement> out = new HashMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                JsonElement file = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
                if (ModRequirement.isMet(file, isModLoaded)) {
                    out.put(NS + ":" + rel.substring(0, rel.length() - 5), file);
                }
            }
        }
        return out;
    }

    static GraphParser.Outcome parseWithEveryOptionalModLoaded() throws IOException {
        Path root = Path.of(System.getProperty("nostalgiaskills.dataRoot", "src/main/resources/data/nostalgiaskills"));
        return new GraphParser(KnownIds.PERMISSIVE).parse(NS + ":main", new GraphParser.Sources(
                load(root.resolve("skill_trees"), mod -> true), load(root.resolve("skill_nodes"), mod -> true),
                load(root.resolve("skill_connections"), mod -> true)));
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
    void optionalNodesFormAValidTreeWhenTheirModsAreLoaded() throws IOException {
        GraphParser.Outcome full = parseWithEveryOptionalModLoaded();
        assertTrue(full.snapshot().isPresent(), () -> full.report().errors().toString());
        assertTrue(full.report().warnings().isEmpty(), () -> full.report().warnings().toString());
        assertTrue(full.snapshot().get().nodes().size() > g.nodes().size());
    }

    @Test
    void hasExactDistribution() {
        assertEquals(484, g.nodes().size());
        for (Sector sector : g.sectors()) {
            Map<NodeType, Integer> c = new HashMap<>();
            g.nodes().values().stream().filter(n -> n.sector().equals(sector.id()) && n.type() != NodeType.PORTAL)
                    .forEach(n -> c.merge(n.type(), 1, Integer::sum));
            assertEquals(Map.of(NodeType.ROOT, 1, NodeType.MINOR, 64, NodeType.NOTABLE, 12, NodeType.KEYSTONE, 3), c, sector.id());
        }
        assertEquals(6, g.roots().size());
        assertEquals(0, g.exclusiveGroups().size(), "no skill excludes another");
        assertEquals(4, g.nodes().values().stream().filter(n -> n.type() == NodeType.PORTAL).count());
    }

    @Test
    void rootsSitOnTheGoldenRatioExpansionOfTheAuthoredHexagon() {
        Map<String, Vec2> want = Map.of("duelist", new Vec2(-471.2461, 0), "guardian", new Vec2(-235.3862, -408.5499),
                "arcanist", new Vec2(235.8598, -408.2766), "marksman", new Vec2(471.2461, 0),
                "artificer", new Vec2(235.3862, 408.5499), "berserker", new Vec2(-235.8598, 408.2766));
        want.forEach((s, v) -> assertEquals(v, g.nodes().get(NS + ":" + s + "/hub").position(), s));
    }

    @Test
    void everySkillExceptAGateHasARealEffect() {
        for (SkillNodeDefinition node : g.nodes().values()) {
            assertEquals(node.type() == NodeType.PORTAL, node.effects().isEmpty(), node.id());
        }
    }

    @Test
    void keystonesCombineAdvantageWithDrawback() {
        for (SkillNodeDefinition node : g.nodes().values()) {
            if (node.type() != NodeType.KEYSTONE) {
                continue;
            }
            assertTrue(node.effects().size() >= 2, node.id());
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
        PriorityQueue<Map.Entry<String, Integer>> priorityQueue = new PriorityQueue<>(Map.Entry.comparingByValue());
        dist.put(from, 0);
        priorityQueue.add(Map.entry(from, 0));
        while (!priorityQueue.isEmpty()) {
            Map.Entry<String, Integer> e = priorityQueue.poll();
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
                    priorityQueue.add(Map.entry(m, nd));
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    @Test
    void oneCharacterCanLearnEverySkill() {
        AllocationService.Expansion everything = AllocationService.expand(g, new PlayerSkillState(), g.ids());
        assertTrue(everything.rejected().isEmpty(), "blocked: " + everything.rejected().keySet());
        assertEquals(484, everything.accepted().size());
        assertEquals(593, everything.totalCost(), "points needed for the whole tree");
    }

    @Test
    void portalsComeInPairsThatLinkOppositeSidesOfTheTree() {
        for (String[] pair : new String[][]{{"east", "west"}, {"north", "south"}}) {
            String first = NS + ":portal/" + pair[0];
            String second = NS + ":portal/" + pair[1];
            assertEquals(Optional.of(second), g.portalDestination(first));
            assertEquals(Optional.of(first), g.portalDestination(second));
            assertTrue(g.neighbours(first).contains(second));
            assertTrue(g.neighbours(first).size() >= 2 && g.neighbours(second).size() >= 2, "each gate opens onto its own side");
            assertTrue(g.nodes().get(first).position().distanceTo(g.nodes().get(second).position()) > 2000);
            assertEquals(3, g.nodes().get(first).cost());
        }
    }

    @Test
    void learningOneGateOpensItsTwinForFreeAndRefundingEitherClosesBoth() {
        String east = NS + ":portal/east";
        String west = NS + ":portal/west";
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, 700, 10_000, false);
        assertTrue(AllocationService.confirm(g, state, List.of(g.roots().get(0))).ok());
        PathFinder.Route route = PathFinder.route(g, state, List.of(), east).orElseThrow();
        AllocationService.BatchResult bought = AllocationService.confirm(g, state, route.newNodes());
        assertTrue(bought.ok(), "route to the gate rejected: " + bought.rejection());
        assertEquals(route.cost(), bought.totalCost());
        assertEquals(3, state.allocations().get(east).paidCost());
        assertEquals(0, state.allocations().get(west).paidCost());
        assertTrue(g.neighbours(west).stream().filter(id -> !id.equals(east))
                .allMatch(id -> AllocationService.eligibility(g, state, List.of(), id).isEmpty()), "the far side is open");

        RefundService.Plan plan = RefundService.plan(g, state, west, true);
        assertTrue(plan.ok());
        assertTrue(plan.removed().containsAll(List.of(east, west)));
        assertEquals(3, plan.refund());
    }

    @Test
    void connectionsReferenceTheSameGraphUsedForEligibility() {
        for (SkillConnection connection : g.connections()) {
            assertTrue(g.accessFrom(connection.b()).contains(connection.a()));
            assertTrue(g.accessFrom(connection.a()).contains(connection.b()));
        }
    }

    @Test
    void networkCodecRoundTripsWithinPayloadBudget() throws Exception {
        byte[] bytes = GraphCodec.encode(g);
        System.out.println("graph sync payload: " + bytes.length + " bytes (compressed)");
        assertTrue(bytes.length < GraphCodec.MAX_BYTES, "payload " + bytes.length);
        SkillGraphSnapshot back = GraphCodec.decode(bytes);
        assertEquals(g.fingerprint(), back.fingerprint());
        assertEquals(g.nodes(), back.nodes());
        assertEquals(g.connections(), back.connections());
    }
}
