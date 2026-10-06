package dev.nostalgiacraft.skills.graph;

import java.util.List;

public record SkillSign(String id, List<SkillEffect> effects) {
    public static String nameKey(String signId) {
        return translationKey(signId, "name");
    }

    public static String descriptionKey(String signId) {
        return translationKey(signId, "description");
    }

    private static String translationKey(String signId, String suffix) {
        return "sign." + Ids.namespace(signId) + "." + Ids.path(signId).replace('/', '.') + "." + suffix;
    }
}
