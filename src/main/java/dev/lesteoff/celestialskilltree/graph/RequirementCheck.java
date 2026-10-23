package dev.lesteoff.celestialskilltree.graph;

import java.util.Collection;

public final class RequirementCheck {
    private RequirementCheck() {
    }

    public static boolean satisfied(SkillGraphSnapshot graph, SkillNodeDefinition node, Collection<String> allocated,
                                    int sectorPoints) {
        return firstUnmet(graph, node, allocated, sectorPoints) == null;
    }

    public static String firstUnmet(SkillGraphSnapshot graph, SkillNodeDefinition node, Collection<String> allocated,
                                    int sectorPoints) {
        Requirements requirements = node.requirements();
        for (String t : requirements.allOf()) {
            if (!holds(graph, t, allocated, node.id())) {
                return t;
            }
        }
        if (!requirements.anyOf().isEmpty() && requirements.anyOf().stream().noneMatch(t -> holds(graph, t, allocated, node.id()))) {
            return String.join("|", requirements.anyOf());
        }
        if (sectorPoints < requirements.minimumSectorPoints()) {
            return "sector_points:" + requirements.minimumSectorPoints();
        }
        return null;
    }

    private static boolean holds(SkillGraphSnapshot graph, String term, Collection<String> allocated, String self) {
        if (term.startsWith("#")) {
            String tag = term.substring(1);
            for (String id : allocated) {
                if (!id.equals(self) && graph.node(id).map(d -> d.tags().contains(tag)).orElse(false)) {
                    return true;
                }
            }
            return false;
        }
        return !term.equals(self) && allocated.contains(term);
    }
}
