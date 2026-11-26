package dev.lesteoff.celestialskilltree.graph;

import java.util.List;
import java.util.Optional;

public record CommunityTier(String id, long xpRequired, Optional<String> parent, Optional<String> exclusive, String icon, Vec2 position,
                            List<SkillEffect> effects) {
    public static final String DEFAULT_ICON = "celestialskilltree:textures/skill_icons/star.png";

    public boolean rivals(CommunityTier other) {
        return !id.equals(other.id) && exclusive.isPresent() && exclusive.equals(other.exclusive);
    }

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
