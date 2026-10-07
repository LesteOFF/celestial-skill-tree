package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.SkillEffect;

import java.util.List;
import java.util.Optional;

public record SkillTemplate(String name, String icon, List<SkillEffect> effects, Optional<String> text) {
    private static final int EFFECTS_THAT_NEED_PLAIN_WORDS = 3;

    public boolean isHardToRead() {
        boolean triggerAmongOthers = effects.size() > 1 && effects.stream().anyMatch(SkillEffect.Trigger.class::isInstance);
        return effects.size() >= EFFECTS_THAT_NEED_PLAIN_WORDS || triggerAmongOthers;
    }
}
