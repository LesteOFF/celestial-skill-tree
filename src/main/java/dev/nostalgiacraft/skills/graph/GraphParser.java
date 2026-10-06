package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns raw JSON (already grouped by resource id) into a {@link SkillGraphSnapshot}.
 * <ul>
 *   <li>Node files: {@code data/<ns>/skill_nodes/<path>.json}; the id is {@code <ns>:<path>}. An explicit
 *   {@code id} field is optional but must agree with the path.</li>
 *   <li>Connection files: {@code data/<ns>/skill_connections/<name>.json}, each with a {@code connections} array.</li>
 *   <li>Tree files: {@code data/<ns>/skill_trees/<name>.json}.</li>
 * </ul>
 * Every problem is reported with its source file, field and reason; nothing is silently dropped.
 */
public final class GraphParser {
    public static final int SUPPORTED_SCHEMA = 1;
    public static final int MAX_NODES = 4096;
    public static final int MAX_CONNECTIONS = 16384;
    public static final int MAX_EFFECTS = 16;
    public static final int MAX_TAGS = 16;
    public static final int MAX_POINTS = 32;
    public static final int MAX_TEXT = 256;
    /** Node ids are sent in allocation requests, so they are kept short (network budget). */
    public static final int MAX_NODE_ID = 64;
    public static final int MAX_COST = 100;
    public static final double MAX_COORDINATE = 100_000;

    public record Sources(Map<String, JsonElement> trees, Map<String, JsonElement> nodes,
                          Map<String, JsonElement> connectionFiles) {
    }

    public record Outcome(Optional<SkillGraphSnapshot> snapshot, ValidationReport report) {
    }

    private final KnownIds known;

    public GraphParser(KnownIds known) {
        this.known = known;
    }

    public Outcome parse(String treeId, Sources sources) {
        ValidationReport report = new ValidationReport();
        JsonElement treeJson = sources.trees().get(treeId);
        if (treeJson == null || !treeJson.isJsonObject()) {
            report.error(treeId, "", "tree definition missing (expected data/" + Ids.namespace(treeId)
                    + "/skill_trees/" + Ids.path(treeId) + ".json)");
            return new Outcome(Optional.empty(), report);
        }
        JsonObject tree = treeJson.getAsJsonObject();
        String treeSrc = "skill_trees/" + treeId;
        checkSchema(tree, treeSrc, report);
        List<Sector> sectors = parseSectors(tree, treeSrc, report);
        List<ExclusiveGroup> groups = parseGroups(tree, treeSrc, report);

        if (sources.nodes().size() > MAX_NODES) {
            report.error("skill_nodes", "", "too many node files: " + sources.nodes().size() + " > " + MAX_NODES);
            return new Outcome(Optional.empty(), report);
        }
        List<SkillNodeDefinition> nodes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(sources.nodes()).entrySet()) {
            String src = "skill_nodes/" + e.getKey();
            try {
                SkillNodeDefinition n = parseNode(e.getKey(), e.getValue(), src, report);
                if (n != null && n.tree().equals(treeId)) {
                    nodes.add(n);
                }
            } catch (RuntimeException ex) {
                report.error(src, "", "unreadable node: " + ex.getMessage());
            }
        }
        List<SkillConnection> connections = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(sources.connectionFiles()).entrySet()) {
            String src = "skill_connections/" + e.getKey();
            try {
                parseConnections(treeId, e.getValue(), src, connections, report);
            } catch (RuntimeException ex) {
                report.error(src, "", "unreadable connection file: " + ex.getMessage());
            }
        }
        if (connections.size() > MAX_CONNECTIONS) {
            report.error("skill_connections", "", "too many connections: " + connections.size());
        }
        if (report.hasErrors()) {
            return new Outcome(Optional.empty(), report);
        }
        SkillGraphSnapshot snapshot = new SkillGraphSnapshot(treeId, nodes, connections, sectors, groups);
        report.addAll(new GraphValidator().validate(snapshot));
        return new Outcome(report.hasErrors() ? Optional.empty() : Optional.of(snapshot), report);
    }

    // ------------------------------------------------------------------ tree

    private static void checkSchema(JsonObject o, String src, ValidationReport r) {
        if (!o.has("schema_version")) {
            r.error(src, "schema_version", "missing");
        } else if (o.get("schema_version").getAsInt() != SUPPORTED_SCHEMA) {
            r.error(src, "schema_version", "unsupported version " + o.get("schema_version") + " (supported: "
                    + SUPPORTED_SCHEMA + ")");
        }
    }

    private List<Sector> parseSectors(JsonObject tree, String src, ValidationReport r) {
        List<Sector> out = new ArrayList<>();
        JsonArray arr = array(tree, "sectors", src, r);
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject s = arr.get(i).getAsJsonObject();
            String f = "sectors[" + i + "]";
            String id = text(s, "id", src, f + ".id", r);
            String root = id(s, "root", src, f + ".root", r);
            String name = text(s, "name", src, f + ".name", r);
            if (id != null && root != null && name != null) {
                out.add(new Sector(id, root, name));
            }
        }
        return out;
    }

    private List<ExclusiveGroup> parseGroups(JsonObject tree, String src, ValidationReport r) {
        List<ExclusiveGroup> out = new ArrayList<>();
        if (!tree.has("exclusive_groups")) {
            return out;
        }
        JsonArray arr = array(tree, "exclusive_groups", src, r);
        for (int i = 0; arr != null && i < arr.size(); i++) {
            JsonObject g = arr.get(i).getAsJsonObject();
            String f = "exclusive_groups[" + i + "]";
            String id = text(g, "id", src, f + ".id", r);
            String name = text(g, "name", src, f + ".name", r);
            int max = g.has("max_active") ? g.get("max_active").getAsInt() : 1;
            if (max < 1) {
                r.error(src, f + ".max_active", "must be >= 1");
            }
            List<String> members = new ArrayList<>();
            JsonArray m = array(g, "members", src, r);
            for (int j = 0; m != null && j < m.size(); j++) {
                String mid = m.get(j).getAsString();
                if (!Ids.isValid(mid)) {
                    r.error(src, f + ".members[" + j + "]", "invalid id '" + mid + "'");
                }
                members.add(mid);
            }
            if (id != null && name != null) {
                out.add(new ExclusiveGroup(id, name, max, List.copyOf(members)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ nodes

    private SkillNodeDefinition parseNode(String id, JsonElement el, String src, ValidationReport r) {
        if (!el.isJsonObject()) {
            r.error(src, "", "node file must be a JSON object");
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        int before = r.errors().size();
        checkSchema(o, src, r);
        if (!Ids.isValid(id) || id.length() > MAX_NODE_ID) {
            r.error(src, "id", "invalid id derived from path (max " + MAX_NODE_ID + " chars): '" + id + "'");
        }
        if (o.has("id") && !o.get("id").getAsString().equals(id)) {
            r.error(src, "id", "explicit id '" + o.get("id").getAsString() + "' disagrees with file path id '" + id + "'");
        }
        String tree = id(o, "tree", src, "tree", r);
        String sector = text(o, "sector", src, "sector", r);
        NodeType type = null;
        try {
            type = NodeType.parse(text(o, "type", src, "type", r));
        } catch (IllegalArgumentException ex) {
            r.error(src, "type", ex.getMessage());
        }
        Vec2 pos = null;
        JsonObject p = obj(o, "position", src, r);
        if (p != null) {
            pos = new Vec2(number(p, "x", src, "position.x", r), number(p, "y", src, "position.y", r));
            if (!pos.isFinite() || Math.abs(pos.x()) > MAX_COORDINATE || Math.abs(pos.y()) > MAX_COORDINATE) {
                r.error(src, "position", "coordinates must be finite and within +/-" + MAX_COORDINATE);
            }
        }
        String name = text(o, "name", src, "name", r);
        String desc = text(o, "description", src, "description", r);
        String icon = id(o, "icon", src, "icon", r);
        String frame = text(o, "frame", src, "frame", r);
        int cost = type == null ? 0 : (o.has("cost") ? o.get("cost").getAsInt() : type.defaultCost());
        if (cost < 0 || cost > MAX_COST) {
            r.error(src, "cost", "must be within 0.." + MAX_COST);
        }
        int maxRank = o.has("max_rank") ? o.get("max_rank").getAsInt() : 1;
        if (maxRank != 1) {
            r.error(src, "max_rank", "only max_rank 1 is supported by this version");
        }
        Requirements req = parseRequirements(o, src, r);
        List<SkillEffect> effects = new ArrayList<>();
        JsonArray eff = array(o, "effects", src, r);
        if (eff != null) {
            if (eff.size() > MAX_EFFECTS) {
                r.error(src, "effects", "at most " + MAX_EFFECTS + " effects");
            }
            for (int i = 0; i < eff.size(); i++) {
                SkillEffect e = parseEffect(eff.get(i), src, "effects[" + i + "]", r);
                if (e != null) {
                    effects.add(e);
                }
            }
        }
        Set<String> tags = new TreeSet<>();
        if (o.has("tags")) {
            JsonArray t = o.getAsJsonArray("tags");
            if (t.size() > MAX_TAGS) {
                r.error(src, "tags", "at most " + MAX_TAGS + " tags");
            }
            for (JsonElement te : t) {
                String tag = te.getAsString();
                if (tag.isEmpty() || tag.length() > 64) {
                    r.error(src, "tags", "tag length must be 1..64");
                }
                tags.add(tag);
            }
        }
        String cluster = o.has("cluster") ? o.get("cluster").getAsString() : "";
        Optional<String> group = o.has("exclusive_group") ? Optional.of(o.get("exclusive_group").getAsString())
                : Optional.empty();
        if (r.errors().size() > before) {
            return null;
        }
        return new SkillNodeDefinition(id, tree, sector, type, pos, name, desc, icon, frame, cost, maxRank, req,
                List.copyOf(effects), Collections.unmodifiableSet(tags), cluster, group);
    }

    private Requirements parseRequirements(JsonObject o, String src, ValidationReport r) {
        if (!o.has("requirements")) {
            return Requirements.NONE;
        }
        JsonObject q = o.getAsJsonObject("requirements");
        List<String> all = terms(q, "all_of", src, r);
        List<String> any = terms(q, "any_of", src, r);
        int min = q.has("minimum_sector_points") ? q.get("minimum_sector_points").getAsInt() : 0;
        if (min < 0 || min > 10_000) {
            r.error(src, "requirements.minimum_sector_points", "must be within 0..10000");
        }
        return new Requirements(all, any, min);
    }

    private static List<String> terms(JsonObject q, String key, String src, ValidationReport r) {
        List<String> out = new ArrayList<>();
        if (!q.has(key)) {
            return List.of();
        }
        JsonArray a = q.getAsJsonArray(key);
        for (int i = 0; i < a.size(); i++) {
            String t = a.get(i).getAsString();
            boolean ok = t.startsWith("#") ? t.length() > 1 && t.length() <= 65 : Ids.isValid(t);
            if (!ok) {
                r.error(src, "requirements." + key + "[" + i + "]", "expected a node id or #tag, got '" + t + "'");
            }
            out.add(t);
        }
        return List.copyOf(out);
    }

    SkillEffect parseEffect(JsonElement el, String src, String f, ValidationReport r) {
        JsonObject e = el.getAsJsonObject();
        String type = text(e, "type", src, f + ".type", r);
        if (type == null) {
            return null;
        }
        switch (type) {
            case "attribute", "conditional_attribute" -> {
                String attr = id(e, "attribute", src, f + ".attribute", r);
                String op = text(e, "operation", src, f + ".operation", r);
                double amount = number(e, "amount", src, f + ".amount", r);
                if (attr != null && !known.isAttribute(attr)) {
                    r.error(src, f + ".attribute", "unknown attribute '" + attr + "'");
                }
                if (op != null && !SkillEffect.OPERATIONS.contains(op)) {
                    r.error(src, f + ".operation", "unsupported operation '" + op + "' (use one of "
                            + new TreeSet<>(SkillEffect.OPERATIONS) + ")");
                }
                finite(amount, src, f + ".amount", r);
                if (type.equals("attribute")) {
                    return new SkillEffect.Attribute(attr, op, amount);
                }
                Condition c = parseCondition(e, src, f + ".condition", r);
                return c == null ? null : new SkillEffect.ConditionalAttribute(attr, op, amount, c);
            }
            case "damage_modifier" -> {
                String dir = text(e, "direction", src, f + ".direction", r);
                if (dir != null && !dir.equals("dealt") && !dir.equals("taken")) {
                    r.error(src, f + ".direction", "must be 'dealt' or 'taken'");
                }
                String scope = text(e, "scope", src, f + ".scope", r);
                if (scope != null && !SkillEffect.DAMAGE_SCOPES.contains(scope)) {
                    r.error(src, f + ".scope", "unknown scope '" + scope + "'");
                }
                double amount = number(e, "amount", src, f + ".amount", r);
                finite(amount, src, f + ".amount", r);
                if (amount < -0.95 || amount > 10) {
                    r.error(src, f + ".amount", "must be within -0.95..10");
                }
                Optional<Condition> c = e.has("condition")
                        ? Optional.ofNullable(parseCondition(e, src, f + ".condition", r)) : Optional.empty();
                return new SkillEffect.DamageModifier("dealt".equals(dir), scope, amount, c);
            }
            case "trigger" -> {
                String trig = text(e, "trigger", src, f + ".trigger", r);
                if (trig != null && !SkillEffect.TRIGGERS.contains(trig)) {
                    r.error(src, f + ".trigger", "unknown trigger '" + trig + "'");
                }
                int cd = e.has("cooldown_ticks") ? e.get("cooldown_ticks").getAsInt() : 0;
                if (cd < 0 || cd > 72_000) {
                    r.error(src, f + ".cooldown_ticks", "must be within 0..72000");
                }
                JsonObject a = obj(e, "action", src, r);
                if (a == null) {
                    return null;
                }
                String at = text(a, "type", src, f + ".action.type", r);
                if (at == null || !SkillEffect.Action.TYPES.contains(at)) {
                    r.error(src, f + ".action.type", "unknown action '" + at + "'");
                    return null;
                }
                if (at.equals("heal")) {
                    double amount = number(a, "amount", src, f + ".action.amount", r);
                    if (!(amount > 0 && amount <= 100)) {
                        r.error(src, f + ".action.amount", "heal amount must be within (0, 100]");
                    }
                    return new SkillEffect.Trigger(trig, new SkillEffect.Action(at, amount, "", 0, 0), cd);
                }
                String eff = id(a, "effect", src, f + ".action.effect", r);
                if (eff != null && !known.isMobEffect(eff)) {
                    r.error(src, f + ".action.effect", "unknown mob effect '" + eff + "'");
                }
                int dur = a.has("duration_ticks") ? a.get("duration_ticks").getAsInt() : 0;
                int amp = a.has("amplifier") ? a.get("amplifier").getAsInt() : 0;
                if (dur < 1 || dur > 12_000) {
                    r.error(src, f + ".action.duration_ticks", "must be within 1..12000");
                }
                if (amp < 0 || amp > 4) {
                    r.error(src, f + ".action.amplifier", "must be within 0..4");
                }
                return new SkillEffect.Trigger(trig, new SkillEffect.Action(at, 0, eff, dur, amp), cd);
            }
            default -> {
                r.error(src, f + ".type", "unknown effect type '" + type + "'");
                return null;
            }
        }
    }

    private static Condition parseCondition(JsonObject e, String src, String f, ValidationReport r) {
        if (!e.has("condition") || !e.get("condition").isJsonObject()) {
            r.error(src, f, "missing condition object");
            return null;
        }
        JsonObject c = e.getAsJsonObject("condition");
        String type = c.has("type") ? c.get("type").getAsString() : null;
        if (type == null || !Condition.TYPES.contains(type)) {
            r.error(src, f + ".type", "unknown condition '" + type + "'");
            return null;
        }
        Map<String, Object> params = new TreeMap<>();
        for (Map.Entry<String, JsonElement> p : c.entrySet()) {
            if (p.getKey().equals("type")) {
                continue;
            }
            JsonPrimitive v = p.getValue().getAsJsonPrimitive();
            params.put(p.getKey(), v.isNumber() ? (Object) v.getAsDouble() : v.getAsString());
        }
        switch (type) {
            case "holding" -> {
                Object tag = params.get("tag");
                if (!(tag instanceof String s) || !Ids.isValid(s)) {
                    r.error(src, f + ".tag", "holding needs an item tag id");
                }
            }
            case "below_y" -> requireNumber(params, "y", -2048, 2048, src, f, r);
            case "health_below", "target_health_above" -> requireNumber(params, "fraction", 0.01, 1.0, src, f, r);
            case "shot_distance_over", "shot_distance_under" -> requireNumber(params, "blocks", 0, 512, src, f, r);
            default -> {
            }
        }
        return new Condition(type, Collections.unmodifiableMap(params));
    }

    private static void requireNumber(Map<String, Object> params, String key, double lo, double hi, String src,
                                      String f, ValidationReport r) {
        Object v = params.get(key);
        if (!(v instanceof Double d) || !Double.isFinite(d) || d < lo || d > hi) {
            r.error(src, f + "." + key, "must be a number within " + lo + ".." + hi);
        }
    }

    // ------------------------------------------------------------------ connections

    private static void parseConnections(String treeId, JsonElement el, String src, List<SkillConnection> out,
                                         ValidationReport r) {
        JsonObject o = el.getAsJsonObject();
        checkSchema(o, src, r);
        String tree = o.has("tree") ? o.get("tree").getAsString() : treeId;
        if (!tree.equals(treeId)) {
            return;
        }
        JsonArray arr = array(o, "connections", src, r);
        for (int i = 0; arr != null && i < arr.size(); i++) {
            JsonObject c = arr.get(i).getAsJsonObject();
            String f = "connections[" + i + "]";
            String a = id(c, "a", src, f + ".a", r);
            String b = id(c, "b", src, f + ".b", r);
            if (a == null || b == null) {
                continue;
            }
            String id = c.has("id") ? c.get("id").getAsString() : derivedId(a, b);
            if (!Ids.isValid(id)) {
                r.error(src, f + ".id", "invalid id '" + id + "'");
            }
            boolean directed = c.has("directed") && c.get("directed").getAsBoolean();
            String style = c.has("style") ? c.get("style").getAsString() : "path";
            List<Vec2> pts = new ArrayList<>();
            if (c.has("points")) {
                JsonArray pa = c.getAsJsonArray("points");
                if (pa.size() > MAX_POINTS) {
                    r.error(src, f + ".points", "at most " + MAX_POINTS + " intermediate points");
                }
                for (int j = 0; j < pa.size(); j++) {
                    JsonObject p = pa.get(j).getAsJsonObject();
                    Vec2 v = new Vec2(p.get("x").getAsDouble(), p.get("y").getAsDouble());
                    if (!v.isFinite()) {
                        r.error(src, f + ".points[" + j + "]", "coordinates must be finite");
                    }
                    pts.add(v);
                }
            }
            boolean bridge = c.has("bridge") && c.get("bridge").getAsBoolean();
            Optional<String> corridor = c.has("corridor") ? Optional.of(c.get("corridor").getAsString())
                    : Optional.empty();
            out.add(new SkillConnection(id, a, b, directed, style, List.copyOf(pts), bridge, corridor));
        }
    }

    /** Deterministic id for connections that do not declare one: endpoints sorted, joined by "--". */
    public static String derivedId(String a, String b) {
        String x = a.compareTo(b) <= 0 ? a : b;
        String y = a.compareTo(b) <= 0 ? b : a;
        return Ids.namespace(x) + ":edge/" + Ids.path(x).replace('/', '.') + "--" + Ids.path(y).replace('/', '.');
    }

    // ------------------------------------------------------------------ json helpers

    private static JsonArray array(JsonObject o, String key, String src, ValidationReport r) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            r.error(src, key, "missing array");
            return null;
        }
        return o.getAsJsonArray(key);
    }

    private static JsonObject obj(JsonObject o, String key, String src, ValidationReport r) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            r.error(src, key, "missing object");
            return null;
        }
        return o.getAsJsonObject(key);
    }

    private static String text(JsonObject o, String key, String src, String field, ValidationReport r) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            r.error(src, field, "missing string");
            return null;
        }
        String s = o.get(key).getAsString();
        if (s.isEmpty() || s.length() > MAX_TEXT) {
            r.error(src, field, "length must be 1.." + MAX_TEXT);
            return null;
        }
        return s;
    }

    private static String id(JsonObject o, String key, String src, String field, ValidationReport r) {
        String s = text(o, key, src, field, r);
        if (s != null && !Ids.isValid(s)) {
            r.error(src, field, "invalid namespaced id '" + s + "'");
            return null;
        }
        return s;
    }

    private static double number(JsonObject o, String key, String src, String field, ValidationReport r) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber()) {
            r.error(src, field, "missing number");
            return Double.NaN;
        }
        return o.get(key).getAsDouble();
    }

    private static void finite(double v, String src, String field, ValidationReport r) {
        if (!Double.isFinite(v) || Math.abs(v) > 1_000) {
            r.error(src, field, "must be finite and within +/-1000");
        }
    }
}
