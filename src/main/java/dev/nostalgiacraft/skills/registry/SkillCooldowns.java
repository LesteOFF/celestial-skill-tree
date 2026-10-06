package dev.nostalgiacraft.skills.registry;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

/**
 * Trigger cooldowns stored as absolute game-time expiry, persisted with the player and copied on death,
 * so logging out, dying or reconnecting cannot reset a running cooldown.
 */
public final class SkillCooldowns {
    public static final Codec<SkillCooldowns> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
            .xmap(SkillCooldowns::new, c -> c.expiry);
    private static final int MAX_ENTRIES = 512;

    private final Map<String, Long> expiry;

    public SkillCooldowns() {
        this(Map.of());
    }

    private SkillCooldowns(Map<String, Long> m) {
        this.expiry = new HashMap<>(m);
    }

    /** True (and starts the cooldown) when the key is ready at {@code now}. */
    public boolean tryUse(String key, long now, int cooldownTicks) {
        Long until = expiry.get(key);
        if (until != null && until > now) {
            return false;
        }
        if (expiry.size() >= MAX_ENTRIES) {
            expiry.values().removeIf(t -> t <= now);
        }
        if (cooldownTicks > 0) {
            expiry.put(key, now + cooldownTicks);
        }
        return true;
    }

    public void clear() {
        expiry.clear();
    }
}
