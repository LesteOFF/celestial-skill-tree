package dev.lesteoff.celestialskilltree.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;

public final class RateLimiter {
    private final Map<UUID, long[]> windows = new HashMap<>();

    public boolean allow(UUID player, long gameTime, int perSecond) {
        long[] w = windows.computeIfAbsent(player, k -> new long[]{gameTime, 0});
        if (gameTime - w[0] >= SharedConstants.TICKS_PER_SECOND || gameTime < w[0]) {
            w[0] = gameTime;
            w[1] = 0;
        }
        return ++w[1] <= perSecond;
    }

    public void forget(UUID player) {
        windows.remove(player);
    }
}
