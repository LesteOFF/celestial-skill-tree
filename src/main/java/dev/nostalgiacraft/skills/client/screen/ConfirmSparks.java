package dev.nostalgiacraft.skills.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nostalgiacraft.skills.client.render.GraphRenderer;
import dev.nostalgiacraft.skills.client.render.LineBatch;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.Vec2;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

final class ConfirmSparks {
    private static final double SEGMENT_SECONDS = 0.14;
    private static final double BURST_SECONDS = 0.35;
    private static final double BURST_RADIUS = 14;
    private static final int TRAIL_LENGTH = 4;
    private static final double TRAIL_STEP = 0.07;
    private static final float HEAD_SIZE = 2.5f;
    private static final int SPARK_RGB = 0xF3D15C;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE = 255;

    private List<List<Vec2>> routes = List.of();
    private long startedAt;

    void start(List<List<Vec2>> newRoutes) {
        if (ClientConfig.REDUCED_MOTION.get() || newRoutes.isEmpty()) {
            return;
        }
        routes = newRoutes;
        startedAt = Util.getMillis();
    }

    void render(GuiGraphics graphics, Camera camera) {
        if (routes.isEmpty()) {
            return;
        }
        double elapsed = (Util.getMillis() - startedAt) / 1000.0;
        if (elapsed > routes.size() * SEGMENT_SECONDS + BURST_SECONDS) {
            routes = List.of();
            return;
        }
        LineBatch heads = new LineBatch(graphics);
        for (int i = 0; i < routes.size(); i++) {
            double progress = elapsed / SEGMENT_SECONDS - i;
            if (progress >= 0 && progress < 1) {
                drawTrail(heads, camera, routes.get(i), progress);
            }
        }
        heads.draw();
        RenderSystem.enableBlend();
        for (int i = 0; i < routes.size(); i++) {
            double sinceArrival = elapsed - (i + 1) * SEGMENT_SECONDS;
            if (sinceArrival >= 0 && sinceArrival < BURST_SECONDS) {
                drawBurst(graphics, camera, routes.get(i).getLast(), sinceArrival / BURST_SECONDS);
            }
        }
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static void drawTrail(LineBatch batch, Camera camera, List<Vec2> route, double progress) {
        for (int step = 0; step < TRAIL_LENGTH; step++) {
            double fraction = progress - step * TRAIL_STEP;
            if (fraction < 0) {
                return;
            }
            Vec2 point = SparkRoutes.along(route, fraction);
            float x = (float) camera.toScreenX(point.x());
            float y = (float) camera.toScreenY(point.y());
            float size = HEAD_SIZE * (TRAIL_LENGTH - step) / TRAIL_LENGTH;
            int alpha = OPAQUE * (TRAIL_LENGTH - step) / TRAIL_LENGTH;
            batch.rect(x - size, y - size, x + size, y + size, alpha << ALPHA_SHIFT | SPARK_RGB);
        }
    }

    private static void drawBurst(GuiGraphics graphics, Camera camera, Vec2 target, double fraction) {
        double radius = BURST_RADIUS * Math.max(1, Math.sqrt(camera.zoomValue())) * (0.5 + fraction);
        graphics.setColor(1f, 0.85f, 0.4f, (float) (1 - fraction));
        GraphRenderer.draw(graphics, GraphRenderer.frameTex("ring_allocated"), camera.toScreenX(target.x()),
                camera.toScreenY(target.y()), radius);
    }
}
