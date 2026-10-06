package dev.nostalgiacraft.skills.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Simple per-player window limiter for mutating requests (server thread only). */
public final class RateLimiter {
    private final Map<UUID, long[]> windows = new HashMap<>();

    public boolean allow(UUID player, long gameTime, int perSecond) {
        long[] w = windows.computeIfAbsent(player, k -> new long[]{gameTime, 0});
        if (gameTime - w[0] >= 20 || gameTime < w[0]) {
            w[0] = gameTime;
            w[1] = 0;
        }
        return ++w[1] <= perSecond;
    }

    public void forget(UUID player) {
        windows.remove(player);
    }
}
