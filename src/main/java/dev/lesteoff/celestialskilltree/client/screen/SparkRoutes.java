package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.graph.SkillConnection;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.Vec2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SparkRoutes {
    private SparkRoutes() {
    }

    static List<List<Vec2>> of(SkillGraphSnapshot graph, Set<String> litBefore, List<String> confirmed) {
        Set<String> lit = new HashSet<>(litBefore);
        List<List<Vec2>> routes = new ArrayList<>();
        for (String nodeId : confirmed) {
            litConnection(graph, lit, nodeId).ifPresent(connection -> routes.add(path(graph, connection, nodeId)));
            lit.add(nodeId);
        }
        return routes;
    }

    private static Optional<SkillConnection> litConnection(SkillGraphSnapshot graph, Set<String> lit, String nodeId) {
        return graph.connections().stream()
                .filter(connection -> !connection.isPortal())
                .filter(connection -> connection.otherEnd(nodeId).filter(lit::contains).isPresent())
                .findFirst();
    }

    private static List<Vec2> path(SkillGraphSnapshot graph, SkillConnection connection, String target) {
        List<Vec2> path = new ArrayList<>();
        path.add(graph.nodes().get(connection.a()).position());
        path.addAll(connection.points());
        path.add(graph.nodes().get(connection.b()).position());
        return connection.b().equals(target) ? path : path.reversed();
    }

    static Vec2 along(List<Vec2> path, double fraction) {
        double remaining = Math.clamp(fraction, 0, 1) * length(path);
        for (int i = 1; i < path.size(); i++) {
            Vec2 from = path.get(i - 1);
            Vec2 to = path.get(i);
            double step = from.distanceTo(to);
            if (remaining <= step && step > 0) {
                double t = remaining / step;
                return new Vec2(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t);
            }
            remaining -= step;
        }
        return path.getLast();
    }

    private static double length(List<Vec2> path) {
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            total += path.get(i - 1).distanceTo(path.get(i));
        }
        return total;
    }
}
