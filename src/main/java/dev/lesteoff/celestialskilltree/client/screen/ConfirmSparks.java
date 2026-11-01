package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import dev.lesteoff.celestialskilltree.client.render.LineBatch;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.graph.Vec2;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class ConfirmSparks {
    private static final double TRAVEL_SECONDS = 0.16;
    private static final double FLASH_SECONDS = 0.5;
    private static final double TAIL = 0.45;
    private static final int TAIL_SEGMENTS = 8;
    private static final float COMET_EXTRA_WIDTH = 1f;
    private static final int COMET_RGB = 0xFFE7A8;
    private static final double HEAD_PER_WIDTH = 1.3;
    private static final float[] WARM_WHITE = {1f, 0.95f, 0.8f};
    private static final float[] GOLD = {1f, 0.8f, 0.35f};
    private static final double RING_START = 1.05;
    private static final double RING_GROWTH = 0.8;
    private static final double CORE_SHARE = 0.4;
    private static final float CORE_BRIGHTNESS = 0.45f;
    private static final double CORE_PER_RADIUS = 0.85;
    private static final int SPARKLES = 4;
    private static final double SPARKLE_REACH = 1.1;
    private static final double SPARKLE_PER_RADIUS = 0.2;
    private static final double MIN_SPARKLE_RADIUS = 0.8;
    private static final int OPAQUE = 255;

    private List<SparkRoutes.Spark> sparks = List.of();
    private long startedAt;

    void start(List<SparkRoutes.Spark> newSparks) {
        if (ClientConfig.REDUCED_MOTION.get() || newSparks.isEmpty()) {
            return;
        }
        sparks = newSparks;
        startedAt = Util.getMillis();
    }

    void render(GuiGraphics graphics, Camera camera) {
        if (sparks.isEmpty()) {
            return;
        }
        double elapsed = (Util.getMillis() - startedAt) / 1000.0;
        if (elapsed > sparks.size() * TRAVEL_SECONDS + FLASH_SECONDS) {
            sparks = List.of();
            return;
        }
        float width = GraphRenderer.edgeWidth(camera) + COMET_EXTRA_WIDTH;
        LineBatch comets = new LineBatch(graphics);
        for (int i = 0; i < sparks.size(); i++) {
            double progress = elapsed / TRAVEL_SECONDS - i;
            if (progress >= 0 && progress < 1 + TAIL) {
                addComet(comets, camera, sparks.get(i).path(), progress, width);
            }
        }
        comets.draw();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        for (int i = 0; i < sparks.size(); i++) {
            SparkRoutes.Spark spark = sparks.get(i);
            double progress = elapsed / TRAVEL_SECONDS - i;
            if (progress >= 0 && progress < 1) {
                drawHead(graphics, camera, SparkRoutes.along(spark.path(), progress), width);
            }
            double sinceArrival = elapsed - (i + 1) * TRAVEL_SECONDS;
            if (sinceArrival >= 0 && sinceArrival < FLASH_SECONDS) {
                drawFlash(graphics, camera, spark.target(), sinceArrival / FLASH_SECONDS);
            }
        }
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static void addComet(LineBatch batch, Camera camera, List<Vec2> path, double progress, float width) {
        double head = Math.min(progress, 1);
        double tailEnd = Math.max(0, progress - TAIL);
        double draining = progress > 1 ? 1 - (progress - 1) / TAIL : 1;
        for (int segment = 0; segment < TAIL_SEGMENTS; segment++) {
            Vec2 from = SparkRoutes.along(path, tailEnd + (head - tailEnd) * segment / TAIL_SEGMENTS);
            Vec2 to = SparkRoutes.along(path, tailEnd + (head - tailEnd) * (segment + 1) / TAIL_SEGMENTS);
            double alpha = draining * (segment + 1) / TAIL_SEGMENTS;
            batch.segment(camera.toScreenX(from.x()), camera.toScreenY(from.y()), camera.toScreenX(to.x()), camera.toScreenY(to.y()),
                    width, (int) Math.round(OPAQUE * alpha) << 24 | COMET_RGB);
        }
    }

    private static void drawHead(GuiGraphics graphics, Camera camera, Vec2 point, float width) {
        tint(graphics, WARM_WHITE, 1f);
        GraphRenderer.draw(graphics, pip(), camera.toScreenX(point.x()), camera.toScreenY(point.y()), width * HEAD_PER_WIDTH);
    }

    private static void drawFlash(GuiGraphics graphics, Camera camera, SkillNodeDefinition target, double fraction) {
        double x = camera.toScreenX(target.position().x());
        double y = camera.toScreenY(target.position().y());
        double radius = GraphRenderer.screenRadius(target, camera);
        double eased = 1 - Math.pow(1 - fraction, 3);
        float fading = (float) (1 - fraction);
        tint(graphics, GOLD, fading);
        GraphRenderer.draw(graphics, GraphRenderer.frameTex("ring"), x, y, radius * (RING_START + RING_GROWTH * eased));
        if (fraction < CORE_SHARE) {
            tint(graphics, WARM_WHITE, (float) (CORE_BRIGHTNESS * (1 - fraction / CORE_SHARE)));
            GraphRenderer.draw(graphics, pip(), x, y, radius * CORE_PER_RADIUS);
        }
        tint(graphics, WARM_WHITE, fading);
        double reach = radius * (1 + SPARKLE_REACH * eased);
        double sparkleRadius = Math.max(MIN_SPARKLE_RADIUS, radius * SPARKLE_PER_RADIUS * fading);
        for (int sparkle = 0; sparkle < SPARKLES; sparkle++) {
            double angle = Math.PI / 4 + sparkle * Math.PI / 2;
            GraphRenderer.draw(graphics, pip(), x + Math.cos(angle) * reach, y + Math.sin(angle) * reach, sparkleRadius);
        }
    }

    private static void tint(GuiGraphics graphics, float[] color, float alpha) {
        graphics.setColor(color[0], color[1], color[2], alpha);
    }

    private static ResourceLocation pip() {
        return GraphRenderer.frameTex("pip");
    }
}
