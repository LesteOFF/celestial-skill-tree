package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import dev.lesteoff.celestialskilltree.client.render.LineBatch;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import dev.lesteoff.celestialskilltree.client.render.Shapes;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress.StarStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Optional;

final class CommunityConstellation {
    private static final int UNLIT_PATH = 0xFF4A3A28;
    private static final int EXCLUDED_PATH = 0xFF2A211A;
    private static final int LIT_GLOW = 0x55F3D15C;
    private static final double NODE_RADIUS = 11;
    private static final double TIP_RADIUS = 14;
    private static final double ICON_SCALE = 0.55;
    private static final double FOCUS_SCALE = 1.2;
    private static final double FOCUS_EXTRA = 2;
    private static final int NAME_GAP = 2;
    private static final int NAME_HEIGHT = 9;
    private static final double MAX_SCALE = 1.6;
    private static final double FULL_SIZE_SCALE = 1.3;
    private static final double MIN_NODE_SCALE = 0.6;
    private static final double NAMES_SCALE = 1.0;
    private static final double HOVER_EXTRA = 3;
    private static final float LOCKED_SHADE = 0.42f;
    private static final float LOCKED_SHADE_HC = 0.66f;
    private static final float EXCLUDED_SHADE = 0.3f;

    private record Point(double x, double y) {
    }

    private double originX;
    private double originY;
    private double scale;
    private double nodeScale = 1;
    private double centerX;
    private double centerY;

    static int labelSpace() {
        return (int) Math.ceil(focusRadius(TIP_RADIUS)) + NAME_GAP + NAME_HEIGHT;
    }

    void layout(double left, double top, double right, double bottom) {
        DoubleSummaryStatistics x = stars().stream().mapToDouble(Payloads.TierInfo::x).summaryStatistics();
        DoubleSummaryStatistics y = stars().stream().mapToDouble(Payloads.TierInfo::y).summaryStatistics();
        double margin = TIP_RADIUS;
        double innerTop = top + margin;
        double innerBottom = bottom - labelSpace();
        scale = Math.max(0, Math.min(MAX_SCALE, Math.min((right - left - 2 * margin) / Math.max(1, x.getMax() - x.getMin()),
                (innerBottom - innerTop) / Math.max(1, y.getMax() - y.getMin()))));
        nodeScale = Mth.clamp(scale / FULL_SIZE_SCALE, MIN_NODE_SCALE, 1);
        originX = (left + right) / 2;
        originY = (innerTop + innerBottom) / 2;
        centerX = (x.getMin() + x.getMax()) / 2;
        centerY = (y.getMin() + y.getMax()) / 2;
    }

    private Point at(Payloads.TierInfo star) {
        return new Point(originX + (star.x() - centerX) * scale, originY + (star.y() - centerY) * scale);
    }

    Optional<Payloads.TierInfo> starAt(double mouseX, double mouseY) {
        return stars().stream().filter(star -> distance(star, mouseX, mouseY) <= radius(star) + HOVER_EXTRA)
                .min(Comparator.comparingDouble(star -> distance(star, mouseX, mouseY)));
    }

    private double distance(Payloads.TierInfo star, double x, double y) {
        Point point = at(star);
        return Math.hypot(x - point.x(), y - point.y());
    }

    void render(GuiGraphics graphics, Font font, String selected, double seconds) {
        drawLinks(graphics);
        LineBatch rings = new LineBatch(graphics);
        double pulse = 0.5 + 0.5 * Math.sin(seconds * 2.4);
        RenderSystem.enableBlend();
        for (Payloads.TierInfo star : stars()) {
            drawStar(graphics, rings, star, pulse);
        }
        stars().stream().filter(star -> star.id().equals(selected)).findFirst().ifPresent(star -> drawFocus(graphics, star));
        RenderSystem.disableBlend();
        rings.draw();
        stars().stream().filter(star -> scale >= NAMES_SCALE || star.id().equals(selected))
                .forEach(star -> drawName(graphics, font, star));
    }

    private void drawLinks(GuiGraphics graphics) {
        LineBatch batch = new LineBatch(graphics);
        for (Payloads.TierInfo star : stars()) {
            ClientSkillState.worldRules().communityTier(star.parent()).ifPresent(parent -> drawLink(batch, at(parent), at(star), star));
        }
        batch.draw();
    }

    private static void drawLink(LineBatch batch, Point from, Point to, Payloads.TierInfo star) {
        boolean excluded = status(star) == StarStatus.EXCLUDED;
        batch.segment(from.x(), from.y(), to.x(), to.y(), excluded ? 1.5f : 2f, excluded ? EXCLUDED_PATH : UNLIT_PATH);
        double lit = fraction(star);
        if (lit > 0) {
            double litX = from.x() + (to.x() - from.x()) * lit;
            double litY = from.y() + (to.y() - from.y()) * lit;
            batch.segment(from.x(), from.y(), litX, litY, 7f, LIT_GLOW);
            batch.segment(from.x(), from.y(), litX, litY, 3f, Palette.GOLD);
        }
    }

    private void drawStar(GuiGraphics graphics, LineBatch rings, Payloads.TierInfo star, double pulse) {
        Point point = at(star);
        double radius = radius(star);
        StarStatus status = status(star);
        float shade = shade(status);
        graphics.setColor(shade, shade, shade, 1f);
        GraphRenderer.drawNodeFrame(graphics, GraphRenderer.frameTex(isTip(star) ? "keystone" : "notable"), point.x(), point.y(), radius,
                status == StarStatus.LIT);
        GraphRenderer.draw(graphics, GraphRenderer.iconTex(star.icon()), point.x(), point.y(), radius * ICON_SCALE);
        graphics.setColor(1f, 1f, 1f, 1f);
        switch (status) {
            case LIT -> Shapes.ring(rings, point.x(), point.y(), radius * (1.15 + 0.1 * pulse), 2f,
                    Shapes.withAlpha(Palette.GOLD, 0.3 + 0.3 * pulse));
            case OPEN -> {
                Shapes.ring(rings, point.x(), point.y(), radius * 1.1, 1.5f, Shapes.withAlpha(Palette.PENDING, 0.5 + 0.4 * pulse));
                Shapes.arc(rings, point.x(), point.y(), radius * 1.3, -Math.PI / 2, -Math.PI / 2 + 2 * Math.PI * fraction(star), 2f,
                        Palette.GOLD);
            }
            case LOCKED -> GraphRenderer.drawMark(graphics, GraphRenderer.Mark.LOCK, point.x() + radius * 0.62, point.y() + radius * 0.62,
                    Math.max(3, radius * 0.42));
            case EXCLUDED -> GraphRenderer.drawMark(graphics, GraphRenderer.Mark.CONFLICT, point.x(), point.y(), radius * 0.9);
        }
    }

    private static float shade(StarStatus status) {
        return switch (status) {
            case LOCKED -> ClientConfig.HIGH_CONTRAST.get() ? LOCKED_SHADE_HC : LOCKED_SHADE;
            case EXCLUDED -> EXCLUDED_SHADE;
            default -> 1f;
        };
    }

    private void drawFocus(GuiGraphics graphics, Payloads.TierInfo star) {
        Point point = at(star);
        GraphRenderer.draw(graphics, GraphRenderer.frameTex("focus"), point.x(), point.y(), focusRadius(radius(star)));
    }

    private void drawName(GuiGraphics graphics, Font font, Payloads.TierInfo star) {
        Point point = at(star);
        int top = (int) Math.ceil(point.y() + focusRadius(radius(star))) + NAME_GAP;
        graphics.drawCenteredString(font, name(star.id()).withStyle(nameStyle(status(star))), (int) point.x(), top, Palette.TEXT);
    }

    private static double focusRadius(double radius) {
        return radius * FOCUS_SCALE + FOCUS_EXTRA;
    }

    private double radius(Payloads.TierInfo star) {
        return (isTip(star) ? TIP_RADIUS : NODE_RADIUS) * nodeScale;
    }

    private static boolean isTip(Payloads.TierInfo star) {
        return stars().stream().noneMatch(other -> other.parent().equals(star.id()));
    }

    static ChatFormatting nameStyle(StarStatus status) {
        return switch (status) {
            case LIT -> ChatFormatting.GOLD;
            case OPEN -> ChatFormatting.AQUA;
            case LOCKED -> ChatFormatting.GRAY;
            case EXCLUDED -> ChatFormatting.DARK_GRAY;
        };
    }

    static MutableComponent name(String starId) {
        return Component.translatable(CommunityTier.nameKey(starId));
    }

    static StarStatus status(Payloads.TierInfo star) {
        return community().status(star.id());
    }

    static double fraction(Payloads.TierInfo star) {
        return Math.min(1, donated(star) / (double) Math.max(1, star.xpRequired()));
    }

    static long donated(Payloads.TierInfo star) {
        return community().progress(star.id()).map(Payloads.StarProgress::donated).orElse(0L);
    }

    static Payloads.Community community() {
        return ClientSkillState.worldRules().community();
    }

    static List<Payloads.TierInfo> stars() {
        return ClientSkillState.worldRules().communityTiers();
    }
}
