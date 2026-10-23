package dev.nostalgiacraft.skills.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.render.Palette;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ProgressBar {
    static final String WIDEST_LABEL = "100%";
    private static final int TRACK_HEIGHT = 5;
    private static final int FILL_INSET = 1;
    private static final int LABEL_GAP = 4;
    private static final int TEXT_HEIGHT = 8;
    private static final int TOOLTIP_WIDTH = 220;

    private ProgressBar() {
    }

    static void render(GuiGraphics graphics, Font font, int left, int right, int centerY, int mouseX, int mouseY) {
        PlayerSkillState state = ClientSkillState.state().orElse(null);
        boolean hasBalance = Minecraft.getInstance().player != null || ClientSkillState.isFixture();
        if (state == null || !hasBalance) {
            return;
        }
        Payloads.Economy economy = ClientSkillState.economy();
        long balance = ClientSkillState.xpBalance();
        boolean atLimit = state.purchasedPoints() >= economy.pointLimit();
        double fraction = atLimit ? 1.0 : Math.min(1.0, balance / (double) Math.max(1, economy.nextPointCost()));
        Component progress = atLimit ? GuiText.of("maximum") : Component.literal((int) Math.floor(fraction * 100) + "%");
        int trackRight = right - font.width(WIDEST_LABEL) - LABEL_GAP;
        drawTrack(graphics, left, trackRight, centerY - TRACK_HEIGHT / 2, fraction);
        graphics.drawString(font, progress, trackRight + LABEL_GAP, centerY - TEXT_HEIGHT / 2, Palette.GOLD, true);
        boolean hovered = mouseX >= left && mouseX < right && Math.abs(mouseY - centerY) <= TEXT_HEIGHT;
        if (hovered) {
            Component tooltip = atLimit ? GuiText.of("bar_tooltip_max", economy.pointLimit())
                    : GuiText.of("bar_tooltip", GuiText.number(balance), GuiText.number(economy.nextPointCost()), state.purchasedPoints());
            TooltipPanel.render(graphics, font, font.split(tooltip, TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    private static void drawTrack(GuiGraphics graphics, int left, int right, int top, double fraction) {
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.XP_TRACK, left, top, right - left, TRACK_HEIGHT);
        int innerWidth = right - left - 2 * FILL_INSET;
        int filled = (int) Math.round(innerWidth * fraction);
        if (filled > 0) {
            graphics.blitSprite(HudSprites.XP_FILL, left + FILL_INSET, top + FILL_INSET, filled, TRACK_HEIGHT - 2 * FILL_INSET);
        }
        RenderSystem.disableBlend();
    }
}
