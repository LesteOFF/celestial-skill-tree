package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.render.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class PanelRenderer {
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 8;
    private static final int VERTICAL_PADDING = 12;
    private static final int ACTIONS_HEIGHT = 26;
    private static final int BUTTON_HEIGHT = 18;
    private static final int TEXT_HEIGHT = 8;
    private static final int DOCKED_MAX_WIDTH = 230;
    private static final int MODAL_MAX_WIDTH = 260;
    private static final int SCREEN_MARGIN = 40;
    private static final float ABOVE_WIDGETS = 400;

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

    void activateFirst() {
        if (!buttons.isEmpty()) {
            buttons.get(0).action().handler().run();
        }
    }

    void render(GuiGraphics graphics, Font font, View view, int screenWidth, int screenHeight, int controlsBottom,
                int mouseX, int mouseY) {
        buttons.clear();
        int textWidth = view.dockedRight() ? Math.min(DOCKED_MAX_WIDTH, screenWidth / 2 - 20)
                : Math.min(MODAL_MAX_WIDTH, screenWidth - SCREEN_MARGIN);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        view.lines().forEach(line -> wrapped.addAll(font.split(line, textWidth)));
        int actionsHeight = view.actions().isEmpty() ? 0 : ACTIONS_HEIGHT;
        int height = Math.min(screenHeight - controlsBottom - SCREEN_MARGIN,
                wrapped.size() * LINE_HEIGHT + VERTICAL_PADDING + actionsHeight);
        int width = textWidth + 2 * PADDING;
        int left = view.dockedRight() ? screenWidth - width - PADDING : (screenWidth - width) / 2;
        int top = view.dockedRight() ? controlsBottom + 2 : Math.max(controlsBottom + 4, (screenHeight - height) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, ABOVE_WIDGETS);
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, Palette.PANEL_EDGE);
        graphics.fill(left, top, left + width, top + height, Palette.PANEL);
        int visibleLines = Math.min((height - VERTICAL_PADDING - actionsHeight) / LINE_HEIGHT, wrapped.size());
        for (int i = 0; i < visibleLines; i++) {
            graphics.drawString(font, wrapped.get(i), left + PADDING, top + 6 + i * LINE_HEIGHT, Palette.TEXT, true);
        }
        layoutButtons(view.actions(), left, top + height - 22, width);
        buttons.forEach(button -> drawButton(graphics, font, button, mouseX, mouseY));
        graphics.pose().popPose();
    }

    private void layoutButtons(List<Action> actions, int left, int y, int panelWidth) {
        int buttonWidth = (panelWidth - 3 * PADDING) / 2;
        for (int i = 0; i < actions.size(); i++) {
            buttons.add(new Button(left + PADDING + i * (buttonWidth + PADDING), y, buttonWidth, actions.get(i)));
        }
    }

    private static void drawButton(GuiGraphics graphics, Font font, Button button, int mouseX, int mouseY) {
        int right = button.x() + button.width();
        int bottom = button.y() + BUTTON_HEIGHT;
        int edge = button.contains(mouseX, mouseY) ? Palette.GOLD : Palette.BRONZE;
        graphics.fill(button.x() - 1, button.y() - 1, right + 1, bottom + 1, edge);
        graphics.fill(button.x(), button.y(), right, bottom, button.action().primary() ? Palette.CONTROL_FILL : Palette.CONTROL_DISABLED);
        graphics.drawCenteredString(font, button.action().label(), button.x() + button.width() / 2,
                button.y() + (BUTTON_HEIGHT - TEXT_HEIGHT) / 2, Palette.TEXT);
    }
}
