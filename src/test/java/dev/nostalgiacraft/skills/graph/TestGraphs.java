package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestGraphs {
    public static final String TREE = "t:main";

    private final Map<String, JsonElement> nodes = new LinkedHashMap<>();
    private final JsonArray connections = new JsonArray();
    private final JsonArray sectors = new JsonArray();
    private final JsonArray groups = new JsonArray();
    private double nextX = 0;

    public TestGraphs sector(String sector) {
        JsonObject json = new JsonObject();
        json.addProperty("id", sector);
        json.addProperty("root", "t:" + sector + "/hub");
        json.addProperty("name", "sector." + sector);
        sectors.add(json);
        node(sector + "/hub", sector, "root", 1);
        return this;
    }

    public JsonObject node(String path, String sector, String type, int cost) {
        JsonObject json = new JsonObject();
        json.addProperty("schema_version", 1);
        json.addProperty("tree", TREE);
        json.addProperty("sector", sector);
        json.addProperty("type", type);
        JsonObject p = new JsonObject();
        p.addProperty("x", nextX);
        p.addProperty("y", 0.0);
        nextX += 40;
        json.add("position", p);
        json.addProperty("name", "n." + path);
        json.addProperty("description", "d." + path);
        json.addProperty("icon", "t:textures/i.png");
        json.addProperty("frame", "bronze_circle");
        json.addProperty("cost", cost);
        json.addProperty("max_rank", 1);
        JsonArray effects = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("type", "attribute");
        e.addProperty("attribute", "minecraft:generic.max_health");
        e.addProperty("operation", "add_value");
        e.addProperty("amount", 1.0);
        effects.add(e);
        json.add("effects", effects);
        json.add("tags", new JsonArray());
        nodes.put("t:" + path, json);
        return json;
    }

    public TestGraphs minor(String path, String sector) {
        node(path, sector, "minor", 1);
        return this;
    }

    public TestGraphs notable(String path, String sector) {
        node(path, sector, "notable", 2);
        return this;
    }

    public TestGraphs keystone(String path, String sector, String group) {
        JsonObject json = node(path, sector, "keystone", 3);
        if (group != null) {
            json.addProperty("exclusive_group", group);
            JsonObject g = null;
            for (JsonElement element : groups) {
                if (element.getAsJsonObject().get("id").getAsString().equals(group)) {
                    g = element.getAsJsonObject();
                }
            }
            if (g == null) {
                g = new JsonObject();
                g.addProperty("id", group);
                g.addProperty("name", "g." + group);
                g.addProperty("max_active", 1);
                g.add("members", new JsonArray());
                groups.add(g);
            }
            g.getAsJsonArray("members").add("t:" + path);
        }
        return this;
    }

    public TestGraphs tag(String path, String tag) {
        nodes.get("t:" + path).getAsJsonObject().getAsJsonArray("tags").add(tag);
        return this;
    }

    public TestGraphs requires(String path, List<String> allOf, List<String> anyOf, int sectorPoints) {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        allOf.forEach(array::add);
        JsonArray b = new JsonArray();
        anyOf.forEach(b::add);
        json.add("all_of", array);
        json.add("any_of", b);
        json.addProperty("minimum_sector_points", sectorPoints);
        nodes.get("t:" + path).getAsJsonObject().add("requirements", json);
        return this;
    }

    public TestGraphs edge(String a, String b) {
        JsonObject json = new JsonObject();
        json.addProperty("a", "t:" + a);
        json.addProperty("b", "t:" + b);
        connections.add(json);
        return this;
    }

    public TestGraphs chain(String... paths) {
        for (int i = 0; i + 1 < paths.length; i++) {
            edge(paths[i], paths[i + 1]);
        }
        return this;
    }

    public GraphParser.Outcome parse() {
        JsonObject tree = new JsonObject();
        tree.addProperty("schema_version", 1);
        tree.add("sectors", sectors);
        tree.add("exclusive_groups", groups);
        JsonObject conn = new JsonObject();
        conn.addProperty("schema_version", 1);
        conn.add("connections", connections);
        return new GraphParser(KnownIds.PERMISSIVE).parse(TREE,
                new GraphParser.Sources(Map.of(TREE, tree), nodes, Map.of("t:main", conn)));
    }

    public SkillGraphSnapshot build() {
        GraphParser.Outcome outcome = parse();
        if (outcome.snapshot().isEmpty()) {
            throw new AssertionError("test graph invalid: " + outcome.report().errors());
        }
        return outcome.snapshot().get();
    }

    public static String id(String path) {
        return "t:" + path;
    }

    public static TestGraphs standard() {
        TestGraphs testGraphs = new TestGraphs().sector("a").sector("b");
        testGraphs.minor("a/m1", "a").minor("a/m2", "a").notable("a/n1", "a").minor("a/m3", "a").minor("a/m4", "a")
                .keystone("a/k1", "a", "vow").keystone("a/k2", "a", "vow").minor("b/m1", "b").minor("b/m2", "b")
                .minor("a/req_all", "a").minor("a/req_any", "a").minor("a/sector5", "a").minor("a/fire", "a");
        testGraphs.tag("a/fire", "fire");
        testGraphs.requires("a/req_all", List.of("t:a/n1"), List.of(), 0);
        testGraphs.requires("a/req_any", List.of(), List.of("#fire", "t:a/k1"), 0);
        testGraphs.requires("a/sector5", List.of(), List.of(), 5);
        testGraphs.chain("a/hub", "a/m1", "a/m2", "a/n1");
        testGraphs.edge("a/m2", "a/k1");
        testGraphs.edge("a/m2", "a/k2");
        testGraphs.chain("a/m1", "a/m3", "a/m4", "b/m1", "b/hub", "b/m2");
        testGraphs.edge("a/m1", "a/req_all");
        testGraphs.edge("a/m1", "a/req_any");
        testGraphs.edge("a/m1", "a/sector5");
        testGraphs.edge("a/m3", "a/fire");
        return testGraphs;
    }
}
