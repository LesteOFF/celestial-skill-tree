package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.render.Palette;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

final class ProgressBar {
    private static final int MAX_WIDTH = 320;
    private static final int SIDE_SPACE = 160;
    private static final int BOTTOM_OFFSET = 18;
    private static final int HEIGHT = 6;
    private static final int SEGMENTS = 12;
    private static final int TRACK_COLOR = 0xFF120E08;
    private static final int TOOLTIP_WIDTH = 220;

    private ProgressBar() {
    }

    static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        PlayerSkillState state = ClientSkillState.state().orElse(null);
        boolean hasBalance = Minecraft.getInstance().player != null || ClientSkillState.isFixture();
        if (state == null || !hasBalance) {
            return;
        }
        Payloads.Economy economy = ClientSkillState.economy();
        int width = Math.min(MAX_WIDTH, screenWidth - SIDE_SPACE);
        int left = (screenWidth - width) / 2;
        int top = screenHeight - BOTTOM_OFFSET;
        long balance = ClientSkillState.xpBalance();
        int purchased = state.purchasedPoints();
        boolean atLimit = purchased >= economy.pointLimit();
        double fraction = atLimit ? 1.0 : Math.min(1.0, balance / (double) Math.max(1, economy.nextPointCost()));
        drawTrack(graphics, left, top, width);
        drawSegments(graphics, left, top, width, fraction);
        String from = String.valueOf(purchased);
        Component to = atLimit ? GuiText.of("maximum") : Component.literal(String.valueOf(purchased + 1));
        Component progress = atLimit ? GuiText.of("maximum")
                : Component.literal(String.format(Locale.ROOT, "%d%%", (int) Math.floor(fraction * 100)));
        graphics.drawString(font, from, left - 6 - font.width(from), top - 1, Palette.GOLD, true);
        graphics.drawString(font, to, left + width + 6, top - 1, Palette.GOLD, true);
        graphics.drawCenteredString(font, progress, screenWidth / 2, top - 11, Palette.TEXT);
        boolean hovered = mouseX >= left - 2 && mouseX <= left + width + 2 && mouseY >= top - 12 && mouseY <= top + 8;
        if (hovered) {
            Component tooltip = atLimit ? GuiText.of("bar_tooltip_max", economy.pointLimit())
                    : GuiText.of("bar_tooltip", balance, economy.nextPointCost(), purchased);
            graphics.renderTooltip(font, font.split(tooltip, TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    private static void drawTrack(GuiGraphics graphics, int left, int top, int width) {
        graphics.fill(left - 2, top - 2, left + width + 2, top + HEIGHT + 2, Palette.BRONZE);
        graphics.fill(left - 1, top - 1, left + width + 1, top + HEIGHT + 1, TRACK_COLOR);
    }

    private static void drawSegments(GuiGraphics graphics, int left, int top, int width, double fraction) {
        for (int i = 0; i < SEGMENTS; i++) {
            int segmentLeft = left + i * width / SEGMENTS;
            int segmentRight = left + (i + 1) * width / SEGMENTS - 1;
            double fill = Mth.clamp(fraction * SEGMENTS - i, 0, 1);
            if (fill > 0) {
                int filledRight = segmentLeft + (int) Math.round((segmentRight - segmentLeft) * fill);
                graphics.fill(segmentLeft, top, filledRight, top + HEIGHT, Palette.GOLD);
                graphics.fill(segmentLeft, top + 4, filledRight, top + HEIGHT, Palette.ALLOCATED);
            }
        }
    }
}
