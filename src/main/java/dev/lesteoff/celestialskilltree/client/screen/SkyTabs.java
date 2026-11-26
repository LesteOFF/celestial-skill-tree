package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

final class SkyTabs {
    enum Tab {
        CHART(HudSprites.CHART, HudSprites.TAB_CHART, Palette.GOLD, "chart"),
        HIDDEN(HudSprites.HIDDEN, HudSprites.TAB_HIDDEN, Palette.VIOLET, "hidden"),
        COMMUNITY(HudSprites.COMMUNITY, HudSprites.TAB_COMMUNITY, Palette.PENDING, "community");

        final ResourceLocation icon;
        final ResourceLocation glass;
        final int accent;
        final String key;

        Tab(ResourceLocation icon, ResourceLocation glass, int accent, String key) {
            this.icon = icon;
            this.glass = glass;
            this.accent = accent;
            this.key = key;
        }
    }

    interface Actions {
        void showChart();

        void showHiddenRegion();

        void openCommunity();
    }

    static final int BOTTOM = TreeHud.EDGE + TreeHud.TOP_HEIGHT;
    private static final float OPEN_MILLIS = 160f;
    private static Tab lastSelected = Tab.CHART;
    private static long lastFrame;

    private final Map<Tab, TabButton> buttons = new EnumMap<>(Tab.class);
    private int screenWidth;
    private int minLeft;
    private int left;
    private int islandWidth;

    void layout(int screenWidth, int minLeft, Tab selected, Actions actions, Consumer<AbstractWidget> add) {
        this.screenWidth = screenWidth;
        this.minLeft = minLeft;
        buttons.clear();
        for (Tab tab : Tab.values()) {
            TabButton button = new TabButton(tab, pressed -> open(tab, actions));
            buttons.put(tab, button);
            add.accept(button);
        }
        refresh();
        select(lastSelected);
        buttons.values().forEach(button -> button.ease(1f));
        select(selected);
        arrange();
    }

    private void open(Tab tab, Actions actions) {
        if (buttons.get(tab).selected()) {
            return;
        }
        switch (tab) {
            case CHART -> actions.showChart();
            case HIDDEN -> actions.showHiddenRegion();
            case COMMUNITY -> actions.openCommunity();
        }
    }

    void select(Tab tab) {
        lastSelected = tab;
        buttons.forEach((key, button) -> button.selected = key == tab);
    }

    void refresh() {
        offer(Tab.CHART, true);
        offer(Tab.HIDDEN, ClientSkillState.graph().flatMap(TreeRegion::hidden).isPresent());
        offer(Tab.COMMUNITY, !ClientSkillState.worldRules().communityTiers().isEmpty());
    }

    private void offer(Tab tab, boolean available) {
        TabButton button = buttons.get(tab);
        button.active = available;
        button.setTooltip(Tooltip.create(GuiText.of(available ? tab.key + "_tooltip" : tab.key + "_unavailable_tooltip")));
    }

    void render(GuiGraphics graphics) {
        long now = Util.getMillis();
        float step = ClientConfig.REDUCED_MOTION.get() ? 1f : (now - lastFrame) / OPEN_MILLIS;
        lastFrame = now;
        buttons.values().forEach(button -> button.ease(step));
        arrange();
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.PANEL, left, TreeHud.EDGE, islandWidth, TreeHud.TOP_HEIGHT);
        RenderSystem.disableBlend();
    }

    private void arrange() {
        int buttonsWidth = buttons.values().stream().mapToInt(Button::getWidth).sum();
        islandWidth = 2 * TreeHud.PAD + buttonsWidth + (buttons.size() - 1) * TreeHud.GAP;
        left = Math.max(minLeft, (screenWidth - islandWidth) / 2);
        int x = left + TreeHud.PAD;
        for (TabButton button : buttons.values()) {
            button.setPosition(x, TreeHud.EDGE + TreeHud.PAD);
            x += button.getWidth() + TreeHud.GAP;
        }
    }

    int right() {
        return left + islandWidth;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + islandWidth && mouseY >= TreeHud.EDGE && mouseY < BOTTOM;
    }

    static final class TabButton extends Button {
        private static final int ICON_INSET = (TreeHud.TOOL - HudSprites.TOOL_ICON_SIZE) / 2;
        private static final int LABEL_TOP = (TreeHud.TOOL - 8) / 2;
        private static final int LABEL_MARGIN = 5;
        private static final int LOCK_WIDTH = 7;
        private static final int LOCK_HEIGHT = 8;

        private final Tab tab;
        private final int labelWidth;
        private boolean selected;
        private float openness;

        private TabButton(Tab tab, OnPress onPress) {
            super(0, 0, TreeHud.TOOL, TreeHud.TOOL, GuiText.of(tab.key), onPress, DEFAULT_NARRATION);
            this.tab = tab;
            labelWidth = Minecraft.getInstance().font.width(getMessage()) + LABEL_MARGIN;
        }

        boolean selected() {
            return selected;
        }

        private void ease(float step) {
            openness = Mth.clamp(openness + (selected ? step : -step), 0f, 1f);
            setWidth(TreeHud.TOOL + Math.round(openness * labelWidth));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableBlend();
            graphics.blitSprite(selected ? tab.glass : SkillButton.PLAIN.get(active, isHoveredOrFocused()), getX(), getY(), width, height);
            renderIcon(graphics);
            if (width > TreeHud.TOOL) {
                renderLabel(graphics);
            }
            RenderSystem.disableBlend();
        }

        private void renderIcon(GuiGraphics graphics) {
            float shade = active ? 1f : SkillButton.INACTIVE_ICON_SHADE;
            graphics.setColor(shade, shade, shade, 1f);
            graphics.blitSprite(tab.icon, getX() + ICON_INSET, getY() + ICON_INSET, HudSprites.TOOL_ICON_SIZE, HudSprites.TOOL_ICON_SIZE);
            graphics.setColor(1f, 1f, 1f, 1f);
            if (!active) {
                graphics.blitSprite(HudSprites.TAB_LOCK, getRight() - LOCK_WIDTH - 1, getBottom() - LOCK_HEIGHT - 1, LOCK_WIDTH, LOCK_HEIGHT);
            }
        }

        private void renderLabel(GuiGraphics graphics) {
            graphics.enableScissor(getX(), getY(), getRight() - 1, getBottom());
            graphics.drawString(Minecraft.getInstance().font, getMessage(), getX() + TreeHud.TOOL, getY() + LABEL_TOP, tab.accent, true);
            graphics.disableScissor();
        }
    }
}
