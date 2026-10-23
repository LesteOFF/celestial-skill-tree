package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class GraphJsonParser {
    public record Sources(Map<String, JsonElement> trees, Map<String, JsonElement> nodes,
                          Map<String, JsonElement> connectionFiles) {
    }

    private final NodeParser nodeParser;

    public GraphJsonParser(KnownIds known) {
        this.nodeParser = new NodeParser(known);
    }

    public GraphParser.Outcome parse(String treeId, Sources sources) {
        ValidationReport report = new ValidationReport();
        JsonElement treeJson = sources.trees().get(treeId);
        if (treeJson == null || !treeJson.isJsonObject()) {
            report.error(treeId, "", "tree definition missing (expected data/" + Ids.namespace(treeId)
                    + "/skill_trees/" + Ids.path(treeId) + ".json)");
            return GraphParser.Outcome.failed(report);
        }
        JsonFields tree = new JsonFields(treeJson.getAsJsonObject(), "skill_trees/" + treeId, "", report);
        tree.checkSchemaVersion();
        List<Sector> sectors = sectors(tree);
        List<ExclusiveGroup> groups = exclusiveGroups(tree);
        if (sources.nodes().size() > GraphParser.MAX_NODES) {
            report.error("skill_nodes", "", "too many node files: " + sources.nodes().size() + " > " + GraphParser.MAX_NODES);
            return GraphParser.Outcome.failed(report);
        }
        List<SkillNodeDefinition> nodes = nodes(treeId, sources.nodes(), report);
        List<SkillConnection> connections = connections(treeId, sources.connectionFiles(), report);
        if (report.hasErrors()) {
            return GraphParser.Outcome.failed(report);
        }
        SkillGraphSnapshot snapshot = new SkillGraphSnapshot(treeId, nodes, connections, sectors, groups);
        report.addAll(new GraphValidator().validate(snapshot));
        return new GraphParser.Outcome(report.hasErrors() ? Optional.empty() : Optional.of(snapshot), report);
    }

    private List<SkillNodeDefinition> nodes(String treeId, Map<String, JsonElement> files, ValidationReport report) {
        List<SkillNodeDefinition> nodes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String source = "skill_nodes/" + file.getKey();
            try {
                nodeParser.parse(file.getKey(), file.getValue(), source, report)
                        .filter(node -> node.tree().equals(treeId))
                        .ifPresent(nodes::add);
            } catch (RuntimeException unreadable) {
                report.error(source, "", "unreadable node: " + unreadable.getMessage());
            }
        }
        return nodes;
    }

    private static List<SkillConnection> connections(String treeId, Map<String, JsonElement> files,
                                                     ValidationReport report) {
        List<SkillConnection> connections = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String source = "skill_connections/" + file.getKey();
            try {
                connections.addAll(ConnectionParser.parse(treeId, file.getValue(), source, report));
            } catch (RuntimeException unreadable) {
                report.error(source, "", "unreadable connection file: " + unreadable.getMessage());
            }
        }
        if (connections.size() > GraphParser.MAX_CONNECTIONS) {
            report.error("skill_connections", "", "too many connections: " + connections.size());
        }
        return connections;
    }

    private static List<Sector> sectors(JsonFields tree) {
        List<Sector> sectors = new ArrayList<>();
        JsonArray array = tree.array("sectors").orElseGet(JsonArray::new);
        for (int i = 0; i < array.size(); i++) {
            JsonFields sector = tree.child(array.get(i).getAsJsonObject(), "sectors[" + i + "].");
            String id = sector.text("id");
            String root = sector.id("root");
            String name = sector.text("name");
            if (id != null && root != null && name != null) {
                sectors.add(new Sector(id, root, name));
            }
        }
        return sectors;
    }

    private static List<ExclusiveGroup> exclusiveGroups(JsonFields tree) {
        List<ExclusiveGroup> groups = new ArrayList<>();
        if (!tree.has("exclusive_groups")) {
            return groups;
        }
        JsonArray array = tree.array("exclusive_groups").orElseGet(JsonArray::new);
        for (int i = 0; i < array.size(); i++) {
            JsonFields group = tree.child(array.get(i).getAsJsonObject(), "exclusive_groups[" + i + "].");
            String id = group.text("id");
            String name = group.text("name");
            int maxActive = group.integer("max_active", 1);
            if (maxActive < 1) {
                group.error("max_active", "must be >= 1");
            }
            List<String> members = members(group);
            if (id != null && name != null) {
                groups.add(new ExclusiveGroup(id, name, maxActive, members));
            }
        }
        return groups;
    }

    private static List<String> members(JsonFields group) {
        List<String> members = new ArrayList<>();
        JsonArray array = group.array("members").orElseGet(JsonArray::new);
        for (int i = 0; i < array.size(); i++) {
            String member = array.get(i).getAsString();
            if (!Ids.isValid(member)) {
                group.error("members[" + i + "]", "invalid id '" + member + "'");
            }
            members.add(member);
        }
        return List.copyOf(members);
    }
}
