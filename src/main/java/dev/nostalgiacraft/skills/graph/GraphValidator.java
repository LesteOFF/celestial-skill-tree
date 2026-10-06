package dev.nostalgiacraft.skills.graph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural validation of a parsed snapshot (design section 10.4). */
public final class GraphValidator {
    /** Logical frame radii used for overlap diagnostics (warnings only). */
    public static double radius(NodeType t) {
        return switch (t) {
            case MINOR -> 6.5;
            case NOTABLE -> 8.5;
            case KEYSTONE -> 12.0;
            case ROOT -> 15.0;
        };
    }

    public ValidationReport validate(SkillGraphSnapshot g) {
        ValidationReport r = new ValidationReport();
        if (g.nodes().isEmpty()) {
            r.error(g.treeId(), "", "tree has no nodes");
            return r;
        }
        checkSectors(g, r);
        checkConnections(g, r);
        checkGroups(g, r);
        checkRequirementReferences(g, r);
        if (r.hasErrors()) {
            return r;
        }
        checkReachability(g, r);
        checkExclusiveBlockade(g, r);
        checkSatisfiable(g, r);
        overlapDiagnostics(g, r);
        return r;
    }

    private static void checkSectors(SkillGraphSnapshot g, ValidationReport r) {
        Set<String> ids = new HashSet<>();
        for (Sector s : g.sectors()) {
            if (!ids.add(s.id())) {
                r.error("skill_trees/" + g.treeId(), "sectors", "duplicate sector '" + s.id() + "'");
            }
            SkillNodeDefinition root = g.nodes().get(s.root());
            if (root == null) {
                r.error("skill_trees/" + g.treeId(), "sectors." + s.id() + ".root", "root node '" + s.root() + "' does not exist");
            } else if (!root.isRoot()) {
                r.error("skill_trees/" + g.treeId(), "sectors." + s.id() + ".root", "'" + s.root() + "' is not of type root");
            }
        }
        if (g.roots().isEmpty()) {
            r.error("skill_trees/" + g.treeId(), "sectors", "tree has no root nodes");
        }
        for (SkillNodeDefinition n : g.nodes().values()) {
            if (!ids.contains(n.sector())) {
                r.error("skill_nodes/" + n.id(), "sector", "unknown sector '" + n.sector() + "'");
            }
            if (n.isRoot() && g.sectors().stream().noneMatch(s -> s.root().equals(n.id()))) {
                r.error("skill_nodes/" + n.id(), "type", "root node is not the root of any sector");
            }
        }
    }

    private static void checkConnections(SkillGraphSnapshot g, ValidationReport r) {
        Set<String> ids = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (SkillConnection c : g.connections()) {
            String src = "skill_connections/" + c.id();
            if (!ids.add(c.id())) {
                r.error(src, "id", "duplicate connection id");
            }
            if (c.a().equals(c.b())) {
                r.error(src, "b", "self edge on '" + c.a() + "'");
            }
            if (!g.contains(c.a())) {
                r.error(src, "a", "unknown node '" + c.a() + "'");
            }
            if (!g.contains(c.b())) {
                r.error(src, "b", "unknown node '" + c.b() + "'");
            }
            String key = c.a().compareTo(c.b()) < 0 ? c.a() + "|" + c.b() : c.b() + "|" + c.a();
            if (!pairs.add(key)) {
                r.error(src, "", "duplicate edge between '" + c.a() + "' and '" + c.b() + "'");
            }
            if (g.contains(c.a()) && g.contains(c.b())) {
                boolean crossSector = !g.nodes().get(c.a()).sector().equals(g.nodes().get(c.b()).sector());
                if (crossSector != c.bridge()) {
                    r.warn(src, "bridge", crossSector ? "connects two sectors but is not marked as a bridge"
                            : "marked as a bridge but stays inside one sector");
                }
            }
        }
    }

    private static void checkGroups(SkillGraphSnapshot g, ValidationReport r) {
        Map<String, String> memberOf = new HashMap<>();
        for (ExclusiveGroup eg : g.exclusiveGroups().values()) {
            for (String m : eg.members()) {
                SkillNodeDefinition n = g.nodes().get(m);
                String src = "skill_trees/" + g.treeId();
                if (n == null) {
                    r.error(src, "exclusive_groups." + eg.id(), "unknown member '" + m + "'");
                    continue;
                }
                if (memberOf.put(m, eg.id()) != null) {
                    r.error(src, "exclusive_groups." + eg.id(), "'" + m + "' belongs to more than one group");
                }
                if (!n.exclusiveGroup().equals(java.util.Optional.of(eg.id()))) {
                    r.error("skill_nodes/" + m, "exclusive_group", "node does not declare group '" + eg.id() + "'");
                }
                if (n.isRoot()) {
                    r.error("skill_nodes/" + m, "exclusive_group", "roots cannot be exclusive");
                }
            }
        }
        for (SkillNodeDefinition n : g.nodes().values()) {
            n.exclusiveGroup().ifPresent(gid -> {
                if (!g.exclusiveGroups().containsKey(gid)) {
                    r.error("skill_nodes/" + n.id(), "exclusive_group", "unknown group '" + gid + "'");
                } else if (!gid.equals(memberOf.get(n.id()))) {
                    r.error("skill_nodes/" + n.id(), "exclusive_group", "node is not listed in group '" + gid + "'");
                }
            });
        }
    }

    private static void checkRequirementReferences(SkillGraphSnapshot g, ValidationReport r) {
        for (SkillNodeDefinition n : g.nodes().values()) {
            for (List<String> terms : List.of(n.requirements().allOf(), n.requirements().anyOf())) {
                for (String t : terms) {
                    if (!t.startsWith("#") && !g.contains(t)) {
                        r.error("skill_nodes/" + n.id(), "requirements", "unknown node '" + t + "'");
                    }
                    if (t.equals(n.id())) {
                        r.error("skill_nodes/" + n.id(), "requirements", "node requires itself");
                    }
                }
            }
        }
    }

    /** Every node must be reachable from every root through access edges (exclusivity ignored here). */
    private static void checkReachability(SkillGraphSnapshot g, ValidationReport r) {
        for (String root : g.roots()) {
            Set<String> seen = reach(g, root, Set.of());
            for (String id : g.ids()) {
                if (!seen.contains(id)) {
                    r.error("skill_nodes/" + id, "", "not reachable from root '" + root + "'");
                    return;
                }
            }
        }
    }

    /**
     * Exclusive choices must never block a route: with every exclusive member removed, all other nodes must still
     * be reachable from every root.
     */
    private static void checkExclusiveBlockade(SkillGraphSnapshot g, ValidationReport r) {
        Set<String> exclusive = new HashSet<>();
        g.exclusiveGroups().values().forEach(eg -> exclusive.addAll(eg.members()));
        if (exclusive.isEmpty()) {
            return;
        }
        for (String root : g.roots()) {
            Set<String> seen = reach(g, root, exclusive);
            for (String id : g.ids()) {
                if (!exclusive.contains(id) && !seen.contains(id)) {
                    r.error("skill_nodes/" + id, "", "only reachable through an exclusive choice (from '" + root + "')");
                    return;
                }
            }
        }
    }

    static Set<String> reach(SkillGraphSnapshot g, String start, Set<String> blocked) {
        Map<String, List<String>> forward = new HashMap<>();
        for (SkillConnection c : g.connections()) {
            forward.computeIfAbsent(c.a(), k -> new java.util.ArrayList<>()).add(c.b());
            if (!c.directed()) {
                forward.computeIfAbsent(c.b(), k -> new java.util.ArrayList<>()).add(c.a());
            }
        }
        Set<String> seen = new HashSet<>();
        Deque<String> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            String k = q.poll();
            if (blocked.contains(k) && !k.equals(start)) {
                continue;
            }
            for (String m : forward.getOrDefault(k, List.of())) {
                if (seen.add(m)) {
                    q.add(m);
                }
            }
        }
        return seen;
    }

    /**
     * Fixed point over "achievable" nodes starting from all roots, honouring adjacency and requirements but not
     * exclusivity or point budgets. Anything never achievable has impossible (e.g. cyclic) mandatory prerequisites.
     */
    private static void checkSatisfiable(SkillGraphSnapshot g, ValidationReport r) {
        Set<String> got = new HashSet<>(g.roots());
        Map<String, Integer> sectorCost = new HashMap<>();
        for (String root : g.roots()) {
            sectorCost.merge(g.nodes().get(root).sector(), g.nodes().get(root).cost(), Integer::sum);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNodeDefinition n : g.nodes().values()) {
                if (got.contains(n.id())) {
                    continue;
                }
                boolean adjacent = g.accessFrom(n.id()).stream().anyMatch(got::contains);
                if (adjacent && RequirementCheck.satisfied(g, n, got, sectorCost.getOrDefault(n.sector(), 0))) {
                    got.add(n.id());
                    sectorCost.merge(n.sector(), n.cost(), Integer::sum);
                    changed = true;
                }
            }
        }
        for (String id : g.ids()) {
            if (!got.contains(id)) {
                r.error("skill_nodes/" + id, "requirements", "requirements can never be satisfied (cyclic or impossible)");
            }
        }
    }

    private static void overlapDiagnostics(SkillGraphSnapshot g, ValidationReport r) {
        List<SkillNodeDefinition> list = List.copyOf(g.nodes().values());
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                SkillNodeDefinition a = list.get(i);
                SkillNodeDefinition b = list.get(j);
                double need = radius(a.type()) + radius(b.type()) + 2;
                if (a.position().distanceTo(b.position()) < need) {
                    r.warn("skill_nodes/" + a.id(), "position", "visually overlaps '" + b.id() + "'");
                }
            }
        }
    }
}
