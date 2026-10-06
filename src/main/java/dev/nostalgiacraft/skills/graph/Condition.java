package dev.nostalgiacraft.skills.graph;

import java.util.Map;
import java.util.Set;

/**
 * A runtime condition used by conditional attributes, damage modifiers and triggers.
 * Parameters are validated per type by {@link GraphParser}.
 */
public record Condition(String type, Map<String, Object> params) {
    /** Condition types the effect engine evaluates; anything else is rejected while loading. */
    public static final Set<String> TYPES = Set.of(
            "holding", "sprinting", "sneaking", "blocking", "below_y",
            "health_below", "target_health_above", "shot_distance_over", "shot_distance_under");

    public double number(String key) {
        return ((Number) params.get(key)).doubleValue();
    }

    public String string(String key) {
        return (String) params.get(key);
    }
}
