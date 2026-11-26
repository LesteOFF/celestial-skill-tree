package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class CommunityDonationBar {
    private static final int SMALL_DONATION = 100;
    private static final int LARGE_DONATION = 1000;
    private static final long CONFIRM_WINDOW_MILLIS = 3000;
    private static final long BALANCE_SAMPLE = 9_999_999;
    private static final int SECTION_GAP = 6;
    private static final int ICON_TEXT_GAP = 4;

    private final List<SkillButton> buttons = new ArrayList<>();
    private Font font;
    private IntConsumer donate;
    private SkillButton donateAll;
    private long donateAllArmedUntil;
    private Component target = Component.empty();
    private boolean showTarget;
    private int x;
    private int y;
    private int width;

    void layout(Font font, int centerX, int screenWidth, int screenHeight, Consumer<AbstractWidget> add, IntConsumer donate) {
        this.font = font;
        this.donate = donate;
        buttons.clear();
        SkillButton small = amountButton(SMALL_DONATION, add);
        SkillButton large = amountButton(LARGE_DONATION, add);
        donateAll = textButton(GuiText.of("community.donate_all"), confirmWidth(), this::donateAll, add);
        donateAll.setTooltip(Tooltip.create(GuiText.of("community.donate_all_tooltip")));
        int buttonsWidth = buttons.stream().mapToInt(SkillButton::getWidth).sum() + (buttons.size() - 1) * TreeHud.GAP;
        int fixedWidth = 2 * TreeHud.CONTENT_LEFT + buttonsWidth + SECTION_GAP + balanceWidth();
        int targetWidth = widestTarget() + SECTION_GAP;
        showTarget = fixedWidth + targetWidth <= screenWidth - 2 * TreeHud.EDGE;
        width = fixedWidth + (showTarget ? targetWidth : 0);
        x = Mth.clamp(centerX - width / 2, TreeHud.EDGE, Math.max(TreeHud.EDGE, screenWidth - TreeHud.EDGE - width));
        y = screenHeight - TreeHud.EDGE - TreeHud.ISLAND_HEIGHT;
        int buttonX = x + TreeHud.CONTENT_LEFT + (showTarget ? targetWidth : 0);
        for (SkillButton button : List.of(small, large, donateAll)) {
            button.setPosition(buttonX, y + TreeHud.PAD);
            buttonX += button.getWidth() + TreeHud.GAP;
        }
    }

    private SkillButton amountButton(int amount, Consumer<AbstractWidget> add) {
        Component label = GuiText.of("community.donate", GuiText.number(amount));
        SkillButton button = textButton(label, font.width(label), () -> donate.accept(amount), add);
        button.setTooltip(Tooltip.create(GuiText.of("community.donate_tooltip", GuiText.number(amount))));
        return button;
    }

    private SkillButton textButton(Component label, int textWidth, Runnable action, Consumer<AbstractWidget> add) {
        SkillButton button = new SkillButton(0, 0, textWidth + TreeHud.TEXT_BUTTON_PADDING, TreeHud.CONTROL, label, pressed -> action.run());
        buttons.add(button);
        add.accept(button);
        return button;
    }

    private int confirmWidth() {
        return Math.max(font.width(GuiText.of("community.donate_all")), font.width(GuiText.of("community.donate_all_confirm")));
    }

    private int balanceWidth() {
        return HudSprites.ICON_SIZE + ICON_TEXT_GAP + font.width(GuiText.number(BALANCE_SAMPLE));
    }

    private int widestTarget() {
        int widestName = CommunityConstellation.stars().stream()
                .mapToInt(star -> font.width(GuiText.of("community.target", CommunityConstellation.name(star.id())))).max().orElse(0);
        return Math.max(widestName, font.width(GuiText.of("community.no_target")));
    }

    private void donateAll() {
        if (Util.getMillis() < donateAllArmedUntil) {
            donateAllArmedUntil = 0;
            donate.accept(Integer.MAX_VALUE);
        } else {
            donateAllArmedUntil = Util.getMillis() + CONFIRM_WINDOW_MILLIS;
        }
    }

    int top() {
        return y;
    }

    int left() {
        return x;
    }

    int right() {
        return x + width;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TreeHud.ISLAND_HEIGHT;
    }

    void update(Component target, boolean canDonate) {
        this.target = target;
        buttons.forEach(button -> button.active = canDonate);
        boolean armed = Util.getMillis() < donateAllArmedUntil;
        donateAll.setMessage(GuiText.of(armed ? "community.donate_all_confirm" : "community.donate_all")
                .withStyle(armed ? ChatFormatting.YELLOW : ChatFormatting.RESET));
    }

    void renderPanel(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.PANEL, x, y, width, TreeHud.ISLAND_HEIGHT);
        int iconX = x + width - TreeHud.CONTENT_LEFT - balanceWidth();
        graphics.blitSprite(HudSprites.POINT, iconX, y + TreeHud.ICON_TOP, HudSprites.ICON_SIZE, HudSprites.ICON_SIZE);
        RenderSystem.disableBlend();
    }

    void renderText(GuiGraphics graphics) {
        if (showTarget) {
            graphics.drawString(font, target, x + TreeHud.CONTENT_LEFT, y + TreeHud.TEXT_TOP, Palette.TEXT, true);
        }
        int textX = x + width - TreeHud.CONTENT_LEFT - balanceWidth() + HudSprites.ICON_SIZE + ICON_TEXT_GAP;
        graphics.drawString(font, GuiText.number(ClientSkillState.xpBalance()), textX, y + TreeHud.TEXT_TOP, Palette.GOLD, true);
    }
}
