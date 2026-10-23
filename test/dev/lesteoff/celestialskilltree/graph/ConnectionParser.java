package dev.lesteoff.celestialskilltree.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ConnectionParser {
    private ConnectionParser() {
    }

    static List<SkillConnection> parse(String treeId, JsonElement element, String source, ValidationReport report) {
        JsonFields file = new JsonFields(element.getAsJsonObject(), source, "", report);
        file.checkSchemaVersion();
        if (!file.rawText("tree", treeId).equals(treeId)) {
            return List.of();
        }
        List<SkillConnection> connections = new ArrayList<>();
        JsonArray array = file.array("connections").orElseGet(JsonArray::new);
        for (int i = 0; i < array.size(); i++) {
            JsonFields connection = file.child(array.get(i).getAsJsonObject(), "connections[" + i + "].");
            connection(connection).ifPresent(connections::add);
        }
        return connections;
    }

    private static Optional<SkillConnection> connection(JsonFields connection) {
        String a = connection.id("a");
        String b = connection.id("b");
        if (a == null || b == null) {
            return Optional.empty();
        }
        String id = connection.rawText("id", GraphParser.derivedId(a, b));
        if (!Ids.isValid(id)) {
            connection.error("id", "invalid id '" + id + "'");
        }
        return Optional.of(new SkillConnection(id, a, b, connection.flag("directed"), connection.rawText("style", "path"),
                points(connection), connection.flag("bridge"), connection.optionalRawText("corridor")));
    }

    private static List<Vec2> points(JsonFields connection) {
        if (!connection.has("points")) {
            return List.of();
        }
        JsonArray array = connection.json().getAsJsonArray("points");
        if (array.size() > GraphParser.MAX_POINTS) {
            connection.error("points", "at most " + GraphParser.MAX_POINTS + " intermediate points");
        }
        List<Vec2> points = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject json = array.get(i).getAsJsonObject();
            Vec2 point = new Vec2(json.get("x").getAsDouble(), json.get("y").getAsDouble());
            if (!point.isFinite()) {
                connection.error("points[" + i + "]", "coordinates must be finite");
            }
            points.add(point);
        }
        return List.copyOf(points);
    }
}
