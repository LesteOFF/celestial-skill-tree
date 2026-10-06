package dev.nostalgiacraft.skills.graph;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Immutable definition of one node, exactly as validated from data. */
public record SkillNodeDefinition(
        String id,
        String tree,
        String sector,
        NodeType type,
        Vec2 position,
        String nameKey,
        String descriptionKey,
        String icon,
        String frame,
        int cost,
        int maxRank,
        Requirements requirements,
        List<SkillEffect> effects,
        Set<String> tags,
        String cluster,
        Optional<String> exclusiveGroup) {

    public boolean isRoot() {
        return type == NodeType.ROOT;
    }
}
