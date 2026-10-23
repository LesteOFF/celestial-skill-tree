package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class SkillButton extends Button {
    static final WidgetSprites PLAIN = new WidgetSprites(HudSprites.BUTTON, HudSprites.BUTTON_DISABLED, HudSprites.BUTTON_HIGHLIGHTED);
    static final WidgetSprites PRIMARY = new WidgetSprites(HudSprites.BUTTON_PRIMARY, HudSprites.BUTTON_DISABLED,
            HudSprites.BUTTON_PRIMARY_HIGHLIGHTED);
    static final WidgetSprites BUY = new WidgetSprites(HudSprites.BUTTON_BUY, HudSprites.BUTTON_BUY_DISABLED,
            HudSprites.BUTTON_BUY_HIGHLIGHTED);
    private static final int TEXT_HEIGHT = 8;
    private static final float INACTIVE_ICON_SHADE = 0.45f;

    private final WidgetSprites sprites;
    private ResourceLocation icon;

    SkillButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        this(x, y, width, height, label, onPress, PLAIN);
    }

    SkillButton(int x, int y, int width, int height, Component label, OnPress onPress, WidgetSprites sprites) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.sprites = sprites;
    }

    static SkillButton icon(int x, int y, int size, ResourceLocation icon, Component label, OnPress onPress) {
        SkillButton button = new SkillButton(x, y, size, size, label, onPress);
        button.icon = icon;
        return button;
    }

    void setIcon(ResourceLocation icon) {
        this.icon = icon;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        graphics.blitSprite(sprites.get(active, isHoveredOrFocused()), getX(), getY(), width, height);
        if (icon == null) {
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - TEXT_HEIGHT) / 2, active ? Palette.TEXT : Palette.textDim());
        } else {
            float shade = active ? 1f : INACTIVE_ICON_SHADE;
            graphics.setColor(shade, shade, shade, 1f);
            graphics.blitSprite(icon, getX() + (width - HudSprites.ICON_SIZE) / 2, getY() + (height - HudSprites.ICON_SIZE) / 2,
                    HudSprites.ICON_SIZE, HudSprites.ICON_SIZE);
            graphics.setColor(1f, 1f, 1f, 1f);
        }
        RenderSystem.disableBlend();
    }
}
