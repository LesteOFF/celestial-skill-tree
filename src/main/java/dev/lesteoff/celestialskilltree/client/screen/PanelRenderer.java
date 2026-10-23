package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class PanelRenderer {
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 8;
    private static final int VERTICAL_PADDING = 16;
    private static final int TEXT_TOP = 8;
    private static final int ACTIONS_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_BOTTOM_GAP = 7;
    private static final int TEXT_HEIGHT = 8;
    private static final int DOCKED_MAX_WIDTH = 230;
    private static final int MODAL_MAX_WIDTH = 260;
    private static final int SCREEN_MARGIN = 40;
    private static final float ABOVE_WIDGETS = 400;
    private static final int SCROLL_LINES = 3;
    private static final Component MORE_ABOVE = Component.literal("▲");
    private static final Component MORE_BELOW = Component.literal("▼");

    record Action(Component label, Runnable handler, boolean primary) {
    }

    record View(List<Component> lines, List<Action> actions, boolean dockedRight) {
    }

    private record Button(int x, int y, int width, Action action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private int scrollOffset;
    private List<Component> wrappedLines = List.of();
    private int wrappedWidth;
    private List<FormattedCharSequence> wrapped = List.of();

    void clear() {
        buttons.clear();
    }

    boolean click(double mouseX, double mouseY) {
        for (Button button : buttons) {
            if (button.contains(mouseX, mouseY)) {
                button.action().handler().run();
                return true;
            }
        }
        return false;
    }

    void activateSafe() {
        buttons.stream().map(Button::action).filter(action -> !action.primary()).findFirst()
                .ifPresent(action -> action.handler().run());
    }

    void scroll(double amount) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(amount) * SCROLL_LINES);
    }

    void resetScroll() {
        scrollOffset = 0;
    }

    void render(GuiGraphics graphics, Font font, View view, int screenWidth, int screenHeight, int controlsBottom,
                int mouseX, int mouseY) {
        buttons.clear();
        int textWidth = view.dockedRight() ? Math.min(DOCKED_MAX_WIDTH, screenWidth / 2 - 20)
                : Math.min(MODAL_MAX_WIDTH, screenWidth - SCREEN_MARGIN);
        List<FormattedCharSequence> wrapped = wrapped(font, view.lines(), textWidth);
        int actionsHeight = view.actions().isEmpty() ? 0 : ACTIONS_HEIGHT;
        int height = Math.min(screenHeight - controlsBottom - SCREEN_MARGIN,
                wrapped.size() * LINE_HEIGHT + VERTICAL_PADDING + actionsHeight);
        int width = textWidth + 2 * PADDING;
        int left = view.dockedRight() ? screenWidth - width - PADDING : (screenWidth - width) / 2;
        int top = view.dockedRight() ? controlsBottom + 2 : Math.max(controlsBottom + 4, (screenHeight - height) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, ABOVE_WIDGETS);
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.PANEL, left, top, width, height);
        RenderSystem.disableBlend();
        int visibleLines = Math.min((height - VERTICAL_PADDING - actionsHeight) / LINE_HEIGHT, wrapped.size());
        scrollOffset = Math.min(scrollOffset, wrapped.size() - visibleLines);
        for (int i = 0; i < visibleLines; i++) {
            graphics.drawString(font, wrapped.get(scrollOffset + i), left + PADDING, top + TEXT_TOP + i * LINE_HEIGHT, Palette.TEXT, true);
        }
        int markX = left + width - PADDING;
        if (scrollOffset > 0) {
            graphics.drawString(font, MORE_ABOVE, markX, top + TEXT_TOP, Palette.GOLD, true);
        }
        if (scrollOffset + visibleLines < wrapped.size()) {
            graphics.drawString(font, MORE_BELOW, markX, top + TEXT_TOP + (visibleLines - 1) * LINE_HEIGHT, Palette.GOLD, true);
        }
        layoutButtons(view.actions(), left, top + height - BUTTON_HEIGHT - BUTTON_BOTTOM_GAP, width);
        buttons.forEach(button -> drawButton(graphics, font, button, mouseX, mouseY));
        graphics.pose().popPose();
    }

    private List<FormattedCharSequence> wrapped(Font font, List<Component> lines, int textWidth) {
        if (lines != wrappedLines || textWidth != wrappedWidth) {
            List<FormattedCharSequence> split = new ArrayList<>();
            lines.forEach(line -> split.addAll(font.split(line, textWidth)));
            wrapped = split;
            wrappedLines = lines;
            wrappedWidth = textWidth;
        }
        return wrapped;
    }

    private void layoutButtons(List<Action> actions, int left, int y, int panelWidth) {
        int buttonWidth = (panelWidth - 3 * PADDING) / 2;
        for (int i = 0; i < actions.size(); i++) {
            buttons.add(new Button(left + PADDING + i * (buttonWidth + PADDING), y, buttonWidth, actions.get(i)));
        }
    }

    private static void drawButton(GuiGraphics graphics, Font font, Button button, int mouseX, int mouseY) {
        WidgetSprites sprites = button.action().primary() ? SkillButton.PRIMARY : SkillButton.PLAIN;
        RenderSystem.enableBlend();
        graphics.blitSprite(sprites.get(true, button.contains(mouseX, mouseY)), button.x(), button.y(), button.width(), BUTTON_HEIGHT);
        RenderSystem.disableBlend();
        graphics.drawCenteredString(font, button.action().label(), button.x() + button.width() / 2,
                button.y() + (BUTTON_HEIGHT - TEXT_HEIGHT) / 2, Palette.TEXT);
    }
}
