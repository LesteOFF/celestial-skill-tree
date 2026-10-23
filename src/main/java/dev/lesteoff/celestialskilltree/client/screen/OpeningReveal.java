package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import net.minecraft.Util;

final class OpeningReveal {
    private static final double WAVE_PIXELS_PER_SECOND = 700;
    private static final double GROW_SECONDS = 0.3;
    private static final double LAST_SECONDS = 2.5;

    private long startedAt = -1;

    GraphRenderer.Fade fade(double originX, double originY) {
        if (ClientConfig.REDUCED_MOTION.get()) {
            return GraphRenderer.Fade.NONE;
        }
        long now = Util.getMillis();
        if (startedAt < 0) {
            startedAt = now;
        }
        double elapsed = (now - startedAt) / 1000.0;
        if (elapsed > LAST_SECONDS) {
            return GraphRenderer.Fade.NONE;
        }
        return (x, y) -> grown(elapsed - Math.hypot(x - originX, y - originY) / WAVE_PIXELS_PER_SECOND);
    }

    static float grown(double secondsSinceWave) {
        return (float) Math.clamp(secondsSinceWave / GROW_SECONDS, 0, 1);
    }
}
