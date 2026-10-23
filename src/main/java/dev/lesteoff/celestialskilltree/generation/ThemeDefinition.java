package dev.lesteoff.celestialskilltree.generation;

import java.util.List;

public record ThemeDefinition(String id, String classId, int weight, SkillTemplate minor, List<NotableDefinition> notables) {
    public String name() {
        return minor.name();
    }
}
