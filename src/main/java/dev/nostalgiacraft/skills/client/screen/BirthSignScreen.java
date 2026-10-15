package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.render.BackgroundRenderer;
import dev.nostalgiacraft.skills.client.render.ConstellationGlyph;
import dev.nostalgiacraft.skills.client.render.LineBatch;
import dev.nostalgiacraft.skills.client.render.Shapes;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.SkillSign;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BirthSignScreen extends Screen {
    private static final int NIGHT_TINT = 0xB0140A24;
    private static final int OWN_COLOR = 0xFFF3D15C;
    private static final int OWN_LINE = 0xFFD28C2E;
    private static final int OTHER_COLOR = 0xFFB9C6E8;
    private static final int OTHER_LINE = 0x805B6A9A;
    private static final int HOVER_COLOR = 0xFF7FD6E8;
    private static final int RING_COLOR = 0xFF6F5BA8;
    private static final int TICK_COLOR = 0x80A977DC;
    private static final int TEXT_COLOR = 0xFFE9E3D8;
    private static final double WHEEL_FRACTION = 0.3;
    private static final double GLYPH_FRACTION = 0.13;
    private static final double CENTER_GLYPH_FRACTION = 0.3;
    private static final double RING_GAP_FRACTION = 0.22;
    private static final double DRIFT_RADIANS_PER_SECOND = 0.05;
    private static final int TICKS = 72;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final float TITLE_SCALE = 1.5f;
    private static final int TOOLTIP_WIDTH = 200;

    private record Placed(String signId, double x, double y) {
    }

    private final Screen parent;
    private final long openedAt = Util.getMillis();

    public BirthSignScreen(Screen parent) {
        super(GuiText.of("sign.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new SkillButton((width - BUTTON_WIDTH) / 2, height - BUTTON_HEIGHT - 8, BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiText.of("back"), button -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double seconds() {
        return ClientConfig.REDUCED_MOTION.get() ? 0 : (Util.getMillis() - openedAt) / 1000.0;
    }

    private double centerY() {
        return height / 2.0 + 2;
    }

    private double wheelRadius() {
        return Math.min(width, height) * WHEEL_FRACTION;
    }

    private static Optional<String> ownSign() {
        return ClientSkillState.state().flatMap(PlayerSkillState::sign);
    }

    private List<Placed> placedSigns() {
        List<String> signs = ClientSkillState.worldRules().signIds();
        int ownIndex = Math.max(0, ownSign().map(signs::indexOf).orElse(0));
        List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < signs.size(); i++) {
            double angle = -Math.PI / 2 + (i - ownIndex) * 2 * Math.PI / signs.size();
            placed.add(new Placed(signs.get(i), width / 2.0 + Math.cos(angle) * wheelRadius(), centerY() + Math.sin(angle) * wheelRadius()));
        }
        return placed;
    }

    private Optional<Placed> hovered(List<Placed> placed, int mouseX, int mouseY) {
        double reach = wheelRadius() * GLYPH_FRACTION * 1.3;
        return placed.stream().filter(sign -> Math.hypot(mouseX - sign.x(), mouseY - sign.y()) <= reach).findFirst();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BackgroundRenderer.render(graphics, width, height, 0, 0, seconds());
        graphics.fill(0, 0, width, height, NIGHT_TINT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Placed> placed = placedSigns();
        Optional<Placed> hovered = hovered(placed, mouseX, mouseY);
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), width / 2, 8, TEXT_COLOR);
        graphics.drawCenteredString(font, GuiText.of("sign.permanent").withStyle(ChatFormatting.GRAY), width / 2, 20, TEXT_COLOR);
        drawWheel(graphics, placed, hovered);
        drawCenter(graphics);
        hovered.ifPresent(sign -> TooltipPanel.render(graphics, font, font.split(tooltip(sign.signId()), TOOLTIP_WIDTH), mouseX, mouseY));
    }

    private void drawWheel(GuiGraphics graphics, List<Placed> placed, Optional<Placed> hovered) {
        double radius = wheelRadius();
        double centerX = width / 2.0;
        double drift = seconds() * DRIFT_RADIANS_PER_SECOND;
        LineBatch batch = new LineBatch(graphics);
        Shapes.ring(batch, centerX, centerY(), radius * (1 + RING_GAP_FRACTION), 1.5f, RING_COLOR);
        Shapes.ring(batch, centerX, centerY(), radius * (1 - RING_GAP_FRACTION), 1f, RING_COLOR);
        for (int i = 0; i < TICKS; i++) {
            double angle = drift + i * 2 * Math.PI / TICKS;
            double length = i % 6 == 0 ? 0.06 : 0.025;
            Shapes.spoke(batch, centerX, centerY(), angle, radius * (1 + RING_GAP_FRACTION), radius * (1 + RING_GAP_FRACTION + length), 1f, TICK_COLOR);
        }
        String own = ownSign().orElse("");
        for (Placed sign : placed) {
            boolean isOwn = sign.signId().equals(own);
            boolean isHovered = hovered.filter(sign::equals).isPresent();
            int starColor = isOwn ? OWN_COLOR : isHovered ? HOVER_COLOR : OTHER_COLOR;
            if (isOwn) {
                double pulse = 0.5 + 0.5 * Math.sin(seconds() * 2);
                Shapes.ring(batch, sign.x(), sign.y(), radius * GLYPH_FRACTION * (1.45 + 0.08 * pulse), 2f, Shapes.withAlpha(OWN_COLOR, 0.5 + 0.4 * pulse));
            }
            ConstellationGlyph.of(sign.signId()).draw(batch, sign.x(), sign.y(), radius * GLYPH_FRACTION, isOwn ? OWN_LINE : OTHER_LINE,
                    starColor, seconds());
        }
        batch.draw();
    }

    private void drawCenter(GuiGraphics graphics) {
        Optional<String> own = ownSign();
        int centerX = width / 2;
        int top = (int) centerY();
        if (own.isEmpty()) {
            graphics.drawCenteredString(font, GuiText.of("sign.none"), centerX, top, TEXT_COLOR);
            return;
        }
        LineBatch batch = new LineBatch(graphics);
        double glyphScale = wheelRadius() * CENTER_GLYPH_FRACTION;
        ConstellationGlyph.of(own.get()).draw(batch, centerX, top - glyphScale * 0.55, glyphScale, OWN_LINE, OWN_COLOR, seconds());
        batch.draw();
        int textTop = (int) (top + glyphScale * 0.55);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, textTop, 0);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1f);
        graphics.drawCenteredString(font, Component.translatable(SkillSign.nameKey(own.get())).withStyle(ChatFormatting.GOLD), 0, 0, TEXT_COLOR);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, description(own.get()),
                centerX, textTop + 16, TEXT_COLOR);
    }

    private static Component tooltip(String signId) {
        boolean own = ownSign().filter(signId::equals).isPresent();
        return Component.empty()
                .append(Component.translatable(SkillSign.nameKey(signId)).withStyle(own ? ChatFormatting.GOLD : ChatFormatting.AQUA))
                .append("\n").append(description(signId))
                .append("\n").append(GuiText.of(own ? "sign.yours" : "sign.other").withStyle(ChatFormatting.GRAY));
    }

    private static Component description(String signId) {
        return Component.translatable(ClientSkillState.worldRules().signDescriptionKey(signId)).withStyle(ChatFormatting.GREEN);
    }
}
