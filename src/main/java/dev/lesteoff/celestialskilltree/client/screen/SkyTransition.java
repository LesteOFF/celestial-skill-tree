package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.client.render.Palette;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

final class SkyTransition {
    static final double FADE_OUT_MILLIS = 180;
    static final double FADE_IN_MILLIS = 320;
    private static final int OPAQUE = 255;

    private long startedAt = -1;
    private Runnable swap;
    private float darkness;

    void start(Runnable swap) {
        this.swap = swap;
        startedAt = Util.getMillis() - Math.round(darkness * FADE_OUT_MILLIS);
    }

    void render(GuiGraphics graphics, int width, int height) {
        if (startedAt < 0) {
            return;
        }
        double elapsed = Util.getMillis() - startedAt;
        if (elapsed >= FADE_OUT_MILLIS && swap != null) {
            swap.run();
            swap = null;
        }
        darkness = darknessAt(elapsed);
        if (elapsed >= FADE_OUT_MILLIS + FADE_IN_MILLIS) {
            startedAt = -1;
        }
        graphics.fill(0, 0, width, height, Math.round(OPAQUE * darkness) << 24 | Palette.DEEP_SPACE & 0xFFFFFF);
    }

    static float darknessAt(double elapsedMillis) {
        double darkening = elapsedMillis / FADE_OUT_MILLIS;
        double brightening = (elapsedMillis - FADE_OUT_MILLIS) / FADE_IN_MILLIS;
        return (float) Math.clamp(elapsedMillis < FADE_OUT_MILLIS ? darkening : 1 - brightening, 0, 1);
    }
}
