package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExtraEffects {
    private ExtraEffects() {
    }

    public static Map<String, List<SkillEffect>> of(PlayerSkillState state, Set<String> litStars) {
        Map<String, List<SkillEffect>> sources = new LinkedHashMap<>();
        state.sign().flatMap(SkillTreeManager::sign).ifPresent(sign -> sources.put("sign/" + sign.id(), sign.allEffects()));
        SkillTreeManager.communityTiers().stream().filter(tier -> litStars.contains(tier.id()))
                .forEach(tier -> sources.put("community/" + tier.id(), tier.effects()));
        return sources;
    }
}
