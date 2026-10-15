package dev.nostalgiacraft.skills.graph;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        Optional<String> exclusiveGroup,
        NodeTraits traits) {
    public static final int SINGLE_RANK = 1;
    public static final int MAX_RANK = 5;

    public boolean isRoot() {
        return type == NodeType.ROOT;
    }

    public boolean ranked() {
        return maxRank > SINGLE_RANK;
    }

    public SkillNodeDefinition withCost(int newCost) {
        return new SkillNodeDefinition(id, tree, sector, type, position, nameKey, descriptionKey, icon, frame, newCost, maxRank,
                requirements, effects, tags, cluster, exclusiveGroup, traits);
    }
}
