package dev.nostalgiacraft.skills.graph;

import java.util.List;

public record CommunityTier(String id, long xpRequired, List<SkillEffect> effects) {
    public static String nameKey(String tierId) {
        return translationKey(tierId, "name");
    }

    public static String descriptionKey(String tierId) {
        return translationKey(tierId, "description");
    }

    private static String translationKey(String tierId, String suffix) {
        return "community." + Ids.namespace(tierId) + "." + Ids.path(tierId).replace('/', '.') + "." + suffix;
    }
}
