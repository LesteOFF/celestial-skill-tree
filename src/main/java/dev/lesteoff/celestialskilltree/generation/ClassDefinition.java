package dev.lesteoff.celestialskilltree.generation;

import java.util.Optional;

public record ClassDefinition(String id, int priority, Optional<String> fallbackClass, Optional<Integer> skills,
                              SkillTemplate hub, SkillTemplate trunk) {
    public String name() {
        return hub.name();
    }
}
