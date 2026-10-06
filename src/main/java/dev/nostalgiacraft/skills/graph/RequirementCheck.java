package dev.nostalgiacraft.skills.graph;

import java.util.Collection;

/** Shared evaluation of a node's extra requirements (never includes graph adjacency). */
public final class RequirementCheck {
    private RequirementCheck() {
    }

    /**
     * @param allocated   nodes already held (confirmed plus earlier nodes of the same transaction), never {@code n}
     * @param sectorPoints recorded paid points already invested in {@code n}'s sector, excluding {@code n} itself
     */
    public static boolean satisfied(SkillGraphSnapshot g, SkillNodeDefinition n, Collection<String> allocated,
                                    int sectorPoints) {
        return firstUnmet(g, n, allocated, sectorPoints) == null;
    }

    /** Human-oriented description of the first unmet requirement, or null when all are met. */
    public static String firstUnmet(SkillGraphSnapshot g, SkillNodeDefinition n, Collection<String> allocated,
                                    int sectorPoints) {
        Requirements q = n.requirements();
        for (String t : q.allOf()) {
            if (!holds(g, t, allocated, n.id())) {
                return t;
            }
        }
        if (!q.anyOf().isEmpty() && q.anyOf().stream().noneMatch(t -> holds(g, t, allocated, n.id()))) {
            return String.join("|", q.anyOf());
        }
        if (sectorPoints < q.minimumSectorPoints()) {
            return "sector_points:" + q.minimumSectorPoints();
        }
        return null;
    }

    private static boolean holds(SkillGraphSnapshot g, String term, Collection<String> allocated, String self) {
        if (term.startsWith("#")) {
            String tag = term.substring(1);
            for (String id : allocated) {
                if (!id.equals(self) && g.node(id).map(d -> d.tags().contains(tag)).orElse(false)) {
                    return true;
                }
            }
            return false;
        }
        return !term.equals(self) && allocated.contains(term);
    }
}
