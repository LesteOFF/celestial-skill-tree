package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds small graphs through the real JSON parser so tests exercise the same path as datapacks. */
public final class TestGraphs {
    public static final String TREE = "t:main";

    private final Map<String, JsonElement> nodes = new LinkedHashMap<>();
    private final JsonArray connections = new JsonArray();
    private final JsonArray sectors = new JsonArray();
    private final JsonArray groups = new JsonArray();
    private double nextX = 0;

    public TestGraphs sector(String sector) {
        JsonObject s = new JsonObject();
        s.addProperty("id", sector);
        s.addProperty("root", "t:" + sector + "/hub");
        s.addProperty("name", "sector." + sector);
        sectors.add(s);
        node(sector + "/hub", sector, "root", 1);
        return this;
    }

    public JsonObject node(String path, String sector, String type, int cost) {
        JsonObject o = new JsonObject();
        o.addProperty("schema_version", 1);
        o.addProperty("tree", TREE);
        o.addProperty("sector", sector);
        o.addProperty("type", type);
        JsonObject p = new JsonObject();
        p.addProperty("x", nextX);
        p.addProperty("y", 0.0);
        nextX += 40;
        o.add("position", p);
        o.addProperty("name", "n." + path);
        o.addProperty("description", "d." + path);
        o.addProperty("icon", "t:textures/i.png");
        o.addProperty("frame", "bronze_circle");
        o.addProperty("cost", cost);
        o.addProperty("max_rank", 1);
        JsonArray effects = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("type", "attribute");
        e.addProperty("attribute", "minecraft:generic.max_health");
        e.addProperty("operation", "add_value");
        e.addProperty("amount", 1.0);
        effects.add(e);
        o.add("effects", effects);
        o.add("tags", new JsonArray());
        nodes.put("t:" + path, o);
        return o;
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
        JsonObject o = node(path, sector, "keystone", 3);
        if (group != null) {
            o.addProperty("exclusive_group", group);
            JsonObject g = null;
            for (JsonElement el : groups) {
                if (el.getAsJsonObject().get("id").getAsString().equals(group)) {
                    g = el.getAsJsonObject();
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
        JsonObject q = new JsonObject();
        JsonArray a = new JsonArray();
        allOf.forEach(a::add);
        JsonArray b = new JsonArray();
        anyOf.forEach(b::add);
        q.add("all_of", a);
        q.add("any_of", b);
        q.addProperty("minimum_sector_points", sectorPoints);
        nodes.get("t:" + path).getAsJsonObject().add("requirements", q);
        return this;
    }

    public TestGraphs edge(String a, String b) {
        JsonObject c = new JsonObject();
        c.addProperty("a", "t:" + a);
        c.addProperty("b", "t:" + b);
        connections.add(c);
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
        GraphParser.Outcome o = parse();
        if (o.snapshot().isEmpty()) {
            throw new AssertionError("test graph invalid: " + o.report().errors());
        }
        return o.snapshot().get();
    }

    public static String id(String path) {
        return "t:" + path;
    }

    /**
     * Standard fixture:
     * a/hub - a/m1 - a/m2 - a/n1(notable) ; a/m2 - a/k1, a/m2 - a/k2 (exclusive "vow")
     * a/m1 - a/m3 - a/m4 - b/m1 - b/hub ; b/hub - b/m2
     * a/m1 - a/req_all (all_of a/n1) ; a/m1 - a/req_any (any_of #fire | a/k1) ; a/m1 - a/sector5 (5 sector pts)
     */
    public static TestGraphs standard() {
        TestGraphs t = new TestGraphs().sector("a").sector("b");
        t.minor("a/m1", "a").minor("a/m2", "a").notable("a/n1", "a").minor("a/m3", "a").minor("a/m4", "a")
                .keystone("a/k1", "a", "vow").keystone("a/k2", "a", "vow").minor("b/m1", "b").minor("b/m2", "b")
                .minor("a/req_all", "a").minor("a/req_any", "a").minor("a/sector5", "a").minor("a/fire", "a");
        t.tag("a/fire", "fire");
        t.requires("a/req_all", List.of("t:a/n1"), List.of(), 0);
        t.requires("a/req_any", List.of(), List.of("#fire", "t:a/k1"), 0);
        t.requires("a/sector5", List.of(), List.of(), 5);
        t.chain("a/hub", "a/m1", "a/m2", "a/n1");
        t.edge("a/m2", "a/k1");
        t.edge("a/m2", "a/k2");
        t.chain("a/m1", "a/m3", "a/m4", "b/m1", "b/hub", "b/m2");
        t.edge("a/m1", "a/req_all");
        t.edge("a/m1", "a/req_any");
        t.edge("a/m1", "a/sector5");
        t.edge("a/m3", "a/fire");
        return t;
    }
}
