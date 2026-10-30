package dev.lesteoff.celestialskilltree.graph;

import java.util.List;
import java.util.stream.Stream;

public record SkillSign(String id, List<SkillEffect> effects, String descriptionKey, List<Bonus> bonuses) {
    public record Bonus(String descriptionKey, List<SkillEffect> effects) {
    }

    public static String nameKey(String signId) {
        return translationKey(signId, "name");
    }

    public static String defaultDescriptionKey(String signId) {
        return translationKey(signId, "description");
    }

    public List<SkillEffect> allEffects() {
        return Stream.concat(effects.stream(), bonuses.stream().flatMap(bonus -> bonus.effects().stream())).toList();
    }

    public List<String> descriptionKeys() {
        return Stream.concat(Stream.of(descriptionKey), bonuses.stream().map(Bonus::descriptionKey)).toList();
    }

    private static String translationKey(String signId, String suffix) {
        return "sign." + Ids.namespace(signId) + "." + Ids.path(signId).replace('/', '.') + "." + suffix;
    }
}
