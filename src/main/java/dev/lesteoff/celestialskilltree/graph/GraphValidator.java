package dev.lesteoff.celestialskilltree.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GraphValidator {
    private static final double OVERLAP_MARGIN = 2;

    public static double radius(NodeType nodeType) {
        return switch (nodeType) {
            case MINOR -> 6.5;
            case NOTABLE -> 8.5;
            case KEYSTONE -> 12.0;
            case ROOT -> 15.0;
            case PORTAL -> 30.0;
        };
    }

    public ValidationReport validate(SkillGraphSnapshot graph) {
        ValidationReport report = new ValidationReport();
        if (graph.nodes().isEmpty()) {
            report.error(graph.treeId(), "", "tree has no nodes");
            return report;
        }
        checkSectors(graph, report);
        checkConnections(graph, report);
        checkGroups(graph, report);
        checkRequirementReferences(graph, report);
        if (report.hasErrors()) {
            return report;
        }
        checkReachability(graph, report);
        checkExclusiveBlockade(graph, report);
        checkBlackHoleBlockade(graph, report);
        checkTraits(graph, report);
        checkSatisfiable(graph, report);
        overlapDiagnostics(graph, report);
        return report;
    }

    private static void checkSectors(SkillGraphSnapshot graph, ValidationReport report) {
        String source = "skill_trees/" + graph.treeId();
        Set<String> ids = new HashSet<>();
        for (Sector sector : graph.sectors()) {
            if (!ids.add(sector.id())) {
                report.error(source, "sectors", "duplicate sector '" + sector.id() + "'");
            }
            String rootField = "sectors." + sector.id() + ".root";
            SkillNodeDefinition root = graph.nodes().get(sector.root());
            if (sector.isRootless()) {
                continue;
            }
            if (root == null) {
                report.error(source, rootField, "root node '" + sector.root() + "' does not exist");
            } else if (!root.isRoot()) {
                report.error(source, rootField, "'" + sector.root() + "' is not of type root");
            }
        }
        if (graph.roots().isEmpty()) {
            report.error(source, "sectors", "tree has no root nodes");
        }
        for (SkillNodeDefinition node : graph.nodes().values()) {
            if (!ids.contains(node.sector())) {
                report.error("skill_nodes/" + node.id(), "sector", "unknown sector '" + node.sector() + "'");
            }
            if (node.isRoot() && graph.sectors().stream().noneMatch(sector -> sector.root().equals(node.id()))) {
                report.error("skill_nodes/" + node.id(), "type", "root node is not the root of any sector");
            }
        }
    }

    private static void checkConnections(SkillGraphSnapshot graph, ValidationReport report) {
        Set<String> ids = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (SkillConnection connection : graph.connections()) {
            String source = "skill_connections/" + connection.id();
            if (!ids.add(connection.id())) {
                report.error(source, "id", "duplicate connection id");
            }
            if (connection.a().equals(connection.b())) {
                report.error(source, "b", "self edge on '" + connection.a() + "'");
            }
            if (!graph.contains(connection.a())) {
                report.error(source, "a", "unknown node '" + connection.a() + "'");
            }
            if (!graph.contains(connection.b())) {
                report.error(source, "b", "unknown node '" + connection.b() + "'");
            }
            if (!pairs.add(unorderedPair(connection.a(), connection.b()))) {
                report.error(source, "", "duplicate edge between '" + connection.a() + "' and '" + connection.b() + "'");
            }
            if (graph.contains(connection.a()) && graph.contains(connection.b())) {
                String sectorA = graph.nodes().get(connection.a()).sector();
                boolean crossSector = !sectorA.equals(graph.nodes().get(connection.b()).sector());
                if (crossSector != connection.bridge()) {
                    report.warn(source, "bridge", crossSector ? "connects two sectors but is not marked as a bridge"
                            : "marked as a bridge but stays inside one sector");
                }
            }
        }
    }

    private static String unorderedPair(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static void checkGroups(SkillGraphSnapshot graph, ValidationReport report) {
        Map<String, String> memberOf = new HashMap<>();
        for (ExclusiveGroup group : graph.exclusiveGroups().values()) {
            String groupField = "exclusive_groups." + group.id();
            for (String member : group.members()) {
                SkillNodeDefinition node = graph.nodes().get(member);
                String source = "skill_trees/" + graph.treeId();
                if (node == null) {
                    report.error(source, groupField, "unknown member '" + member + "'");
                    continue;
                }
                if (memberOf.put(member, group.id()) != null) {
                    report.error(source, groupField, "'" + member + "' belongs to more than one group");
                }
                if (!node.exclusiveGroup().equals(Optional.of(group.id()))) {
                    report.error("skill_nodes/" + member, "exclusive_group", "node does not declare group '" + group.id() + "'");
                }
                if (node.isRoot()) {
                    report.error("skill_nodes/" + member, "exclusive_group", "roots cannot be exclusive");
                }
            }
        }
        for (SkillNodeDefinition node : graph.nodes().values()) {
            node.exclusiveGroup().ifPresent(groupId -> {
                if (!graph.exclusiveGroups().containsKey(groupId)) {
                    report.error("skill_nodes/" + node.id(), "exclusive_group", "unknown group '" + groupId + "'");
                } else if (!groupId.equals(memberOf.get(node.id()))) {
                    report.error("skill_nodes/" + node.id(), "exclusive_group", "node is not listed in group '" + groupId + "'");
                }
            });
        }
    }

    private static void checkRequirementReferences(SkillGraphSnapshot graph, ValidationReport report) {
        for (SkillNodeDefinition node : graph.nodes().values()) {
            for (List<String> terms : List.of(node.requirements().allOf(), node.requirements().anyOf())) {
                for (String term : terms) {
                    if (!term.startsWith("#") && !graph.contains(term)) {
                        report.error("skill_nodes/" + node.id(), "requirements", "unknown node '" + term + "'");
                    }
                    if (term.equals(node.id())) {
                        report.error("skill_nodes/" + node.id(), "requirements", "node requires itself");
                    }
                }
            }
        }
    }

    private static void checkReachability(SkillGraphSnapshot graph, ValidationReport report) {
        for (String root : graph.roots()) {
            Set<String> seen = reach(graph, root, Set.of());
            for (String id : graph.ids()) {
                if (!seen.contains(id) && !standsAlone(graph, id)) {
                    report.error("skill_nodes/" + id, "", "not reachable from root '" + root + "'");
                    return;
                }
            }
        }
    }

    private static void checkExclusiveBlockade(SkillGraphSnapshot graph, ValidationReport report) {
        Set<String> exclusive = new HashSet<>();
        graph.exclusiveGroups().values().forEach(group -> exclusive.addAll(group.members()));
        if (exclusive.isEmpty()) {
            return;
        }
        for (String root : graph.roots()) {
            Set<String> seen = reach(graph, root, exclusive);
            for (String id : graph.ids()) {
                if (!exclusive.contains(id) && !seen.contains(id) && !standsAlone(graph, id)) {
                    report.error("skill_nodes/" + id, "", "only reachable through an exclusive choice (from '" + root + "')");
                    return;
                }
            }
        }
    }

    private static void checkBlackHoleBlockade(SkillGraphSnapshot graph, ValidationReport report) {
        Set<String> blackHoles = new HashSet<>();
        graph.nodes().values().stream().filter(node -> node.traits().amplifiesNeighbours()).forEach(node -> blackHoles.add(node.id()));
        if (blackHoles.isEmpty()) {
            return;
        }
        for (String root : graph.roots()) {
            Set<String> seen = reach(graph, root, blackHoles);
            for (String id : graph.ids()) {
                if (!blackHoles.contains(id) && !seen.contains(id) && !standsAlone(graph, id)) {
                    report.error("skill_nodes/" + id, "", "only reachable through a black hole, which would seal it (from '" + root + "')");
                    return;
                }
            }
        }
    }

    private static void checkTraits(SkillGraphSnapshot graph, ValidationReport report) {
        Set<String> hidden = new HashSet<>();
        for (SkillNodeDefinition node : graph.nodes().values()) {
            boolean special = node.traits().hiddenUntil().isPresent() || node.traits().amplifiesNeighbours();
            if (node.isRoot() && special) {
                report.error("skill_nodes/" + node.id(), "type", "roots cannot be hidden or amplify their neighbours");
            }
            if (node.traits().hiddenUntil().isPresent()) {
                hidden.add(node.id());
            }
        }
        if (hidden.isEmpty()) {
            return;
        }
        for (String root : graph.roots()) {
            Set<String> seen = reach(graph, root, hidden);
            for (String id : graph.ids()) {
                if (!hidden.contains(id) && !seen.contains(id)) {
                    report.error("skill_nodes/" + id, "", "only reachable through a hidden skill (from '" + root + "')");
                    return;
                }
            }
        }
    }

    private static boolean standsAlone(SkillGraphSnapshot graph, String id) {
        return graph.nodes().get(id).traits().standsAlone();
    }

    static Set<String> reach(SkillGraphSnapshot graph, String start, Set<String> blocked) {
        Map<String, List<String>> forward = new HashMap<>();
        for (SkillConnection connection : graph.connections()) {
            forward.computeIfAbsent(connection.a(), from -> new ArrayList<>()).add(connection.b());
            if (!connection.directed()) {
                forward.computeIfAbsent(connection.b(), from -> new ArrayList<>()).add(connection.a());
            }
        }
        Set<String> seen = new HashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        seen.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            String current = frontier.poll();
            if (blocked.contains(current) && !current.equals(start)) {
                continue;
            }
            for (String next : forward.getOrDefault(current, List.of())) {
                if (seen.add(next)) {
                    frontier.add(next);
                }
            }
        }
        return seen;
    }

    private static void checkSatisfiable(SkillGraphSnapshot graph, ValidationReport report) {
        Set<String> obtainable = new HashSet<>(graph.roots());
        Map<String, Integer> sectorCost = new HashMap<>();
        for (String root : graph.roots()) {
            sectorCost.merge(graph.nodes().get(root).sector(), graph.nodes().get(root).cost(), Integer::sum);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNodeDefinition node : graph.nodes().values()) {
                if (obtainable.contains(node.id())) {
                    continue;
                }
                boolean adjacent = node.traits().standsAlone() || graph.accessFrom(node.id()).stream().anyMatch(obtainable::contains);
                int sectorPoints = sectorCost.getOrDefault(node.sector(), 0);
                if (adjacent && RequirementCheck.satisfied(graph, node, obtainable, sectorPoints)) {
                    obtainable.add(node.id());
                    sectorCost.merge(node.sector(), node.cost(), Integer::sum);
                    changed = true;
                }
            }
        }
        for (String id : graph.ids()) {
            if (!obtainable.contains(id)) {
                report.error("skill_nodes/" + id, "requirements", "requirements can never be satisfied (cyclic or impossible)");
            }
        }
    }

    private static void overlapDiagnostics(SkillGraphSnapshot graph, ValidationReport report) {
        List<SkillNodeDefinition> nodes = List.copyOf(graph.nodes().values());
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                SkillNodeDefinition node = nodes.get(i);
                SkillNodeDefinition other = nodes.get(j);
                double clearance = radius(node.type()) + radius(other.type()) + OVERLAP_MARGIN;
                if (node.position().distanceTo(other.position()) < clearance) {
                    report.warn("skill_nodes/" + node.id(), "position", "visually overlaps '" + other.id() + "'");
                }
            }
        }
    }
}
