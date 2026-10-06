package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.render.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class SkillButton extends Button {
    private static final int DISABLED_EDGE = 0xFF4A4038;
    private static final int HIGHLIGHT = 0x40FFFFFF;
    private static final int TEXT_HEIGHT = 8;

    SkillButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHoveredOrFocused() && active;
        int edge = active ? (highlighted ? Palette.GOLD : Palette.BRONZE) : DISABLED_EDGE;
        int left = getX();
        int top = getY();
        graphics.fill(left, top, left + width, top + height, edge);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, active ? Palette.CONTROL_FILL : Palette.CONTROL_DISABLED);
        if (active) {
            graphics.fill(left + 1, top + 1, left + width - 1, top + 2, HIGHLIGHT);
        }
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), left + width / 2,
                top + (height - TEXT_HEIGHT) / 2, active ? Palette.TEXT : Palette.TEXT_DIM);
    }
}
