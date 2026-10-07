package dev.nostalgiacraft.skills.graph;

import java.util.Map;
import java.util.Set;

public record Condition(String type, Map<String, Object> params) {
    public static final Set<String> TYPES = Set.of(
            "holding", "sprinting", "sneaking", "blocking", "below_y",
            "health_below", "target_health_above", "shot_distance_over", "shot_distance_under",
            "in_dimension", "in_biome_tag", "sky_event", "wearing", "attribute_at_least", "dual_wielding", "has_curio");
    public static final Set<String> SKY_EVENTS = Set.of("eclipse", "star_shower");

    public double number(String key) {
        return ((Number) params.get(key)).doubleValue();
    }

    public String string(String key) {
        return (String) params.get(key);
    }
}
