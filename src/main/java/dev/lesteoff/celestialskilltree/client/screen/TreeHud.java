package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.RejectReason;
import dev.lesteoff.celestialskilltree.progression.XpCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;

final class TreeHud {
    interface Actions {
        void buy(int count);

        void toggleStats();

        void confirm();

        void cancel();

        void openCommunity();

        void openSign();

        void toggleHiddenRegion();

        void home();

        void fit();

        void toggleHelp();

        void search(String text);
    }

    private record Island(int x, int y, int width, int height) {
        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    static final int EDGE = 6;
    private static final int ISLAND_GAP = 4;
    private static final int PAD = 3;
    private static final int CONTROL = 13;
    private static final int ISLAND_HEIGHT = CONTROL + 2 * PAD;
    private static final int TOOL = 20;
    private static final int TOP_HEIGHT = TOOL + 2 * PAD;
    private static final int TOP_TEXT_TOP = (TOP_HEIGHT - 8) / 2 + 1;
    private static final int SEARCH_ICON_TOP = (TOP_HEIGHT - HudSprites.TOOL_ICON_SIZE) / 2;
    private static final int GAP = 2;
    private static final int SECTION_GAP = 6;
    private static final int ICON_TOP = (ISLAND_HEIGHT - HudSprites.ICON_SIZE) / 2;
    private static final int TEXT_TOP = (ISLAND_HEIGHT - 8) / 2 + 1;
    private static final int CONTENT_LEFT = PAD + 2;
    private static final int TEXT_BUTTON_PADDING = 14;
    private static final int MIN_SEARCH_WIDTH = 130;
    private static final int MAX_SEARCH_WIDTH = 220;
    private static final int SEARCH_TEXT_LEFT = CONTENT_LEFT + HudSprites.TOOL_ICON_SIZE + 4;
    private static final int COUNTER_SPACE = 30;
    private static final int ICON_TEXT_GAP = 3;
    private static final int POINTS_SAMPLE = 888;
    private static final int XP_WIDTH = 120;
    private static final int MIN_XP_WIDTH = 50;
    private static final int TOOLTIP_WIDTH = 220;

    private final Actions actions;
    private Font font;
    private SkillButton buyButton;
    private SkillButton statsButton;
    private SkillButton communityButton;
    private SkillButton signButton;
    private SkillButton hiddenButton;
    private SkillButton filterButton;
    private SkillButton confirmButton;
    private SkillButton cancelButton;
    private List<SkillButton> tools = List.of();
    private EditBox search;
    private SearchFilter filter = SearchFilter.ALL;
    private int width;
    private int height;
    private Island searchIsland;
    private Island dock;
    private Island bottomIsland;
    private Island pendingIsland;
    private int pointsWidth;
    private int xpLeft;
    private int xpRight;
    private int pointsLeft;
    private Component pointsTooltip = Component.empty();
    private List<Object> shown = List.of();

    TreeHud(Actions actions) {
        this.actions = actions;
    }

    private static MutableComponent text(String key, Object... arguments) {
        return GuiText.of(key, arguments);
    }

    EditBox search() {
        return search;
    }

    SearchFilter filter() {
        return filter;
    }

    int panelTop() {
        return EDGE;
    }

    int messageY() {
        return EDGE + TOP_TEXT_TOP;
    }

    boolean covers(double mouseX, double mouseY) {
        return searchIsland.contains(mouseX, mouseY) || dock.contains(mouseX, mouseY) || bottomIsland.contains(mouseX, mouseY)
                || confirmButton.visible && pendingIsland.contains(mouseX, mouseY);
    }

    void layout(Font font, int width, int height, Consumer<AbstractWidget> add) {
        this.font = font;
        this.width = width;
        this.height = height;
        addSearchIsland(add);
        addDock(add);
        addBottomIsland(add);
        addPendingIsland(add);
        shown = List.of();
    }

    private void addSearchIsland(Consumer<AbstractWidget> add) {
        int searchWidth = Mth.clamp(width / 4, MIN_SEARCH_WIDTH, MAX_SEARCH_WIDTH);
        searchIsland = new Island(EDGE, EDGE, searchWidth, TOP_HEIGHT);
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, EDGE + SEARCH_TEXT_LEFT, EDGE + TOP_TEXT_TOP,
                searchWidth - SEARCH_TEXT_LEFT - PAD - TOOL - COUNTER_SPACE, font.lineHeight, text("search"));
        search.setBordered(false);
        search.setTextColor(Palette.TEXT);
        search.setHint(text("search_hint"));
        search.setValue(previous);
        search.setResponder(actions::search);
        add.accept(search);
        filterButton = icon(filter.icon(), "filter", this::cycleFilter, add);
        filterButton.setPosition(EDGE + searchWidth - PAD - TOOL, EDGE + PAD);
        filterButton.setTooltip(Tooltip.create(filterTooltip()));
    }

    private void addDock(Consumer<AbstractWidget> add) {
        statsButton = icon(HudSprites.STATS, "show_stats", actions::toggleStats, add);
        communityButton = icon(HudSprites.COMMUNITY, "community", actions::openCommunity, add);
        signButton = icon(HudSprites.SIGN, "sign", actions::openSign, add);
        hiddenButton = icon(HudSprites.HIDDEN, "hidden", actions::toggleHiddenRegion, add);
        tools = List.of(statsButton, communityButton, signButton, hiddenButton, icon(HudSprites.HOME, "home", actions::home, add),
                icon(HudSprites.FIT, "fit", actions::fit, add), icon(HudSprites.HELP, "help", actions::toggleHelp, add));
        arrangeDock();
    }

    private void arrangeDock() {
        int top = EDGE + TOP_HEIGHT + ISLAND_GAP;
        int y = top + PAD;
        for (Button button : tools) {
            if (button.visible) {
                button.setPosition(EDGE + PAD, y);
                y += TOOL + GAP;
            }
        }
        dock = new Island(EDGE, top, TOP_HEIGHT, y - GAP + PAD - top);
    }

    private void addBottomIsland(Consumer<AbstractWidget> add) {
        pointsWidth = HudSprites.ICON_SIZE + ICON_TEXT_GAP + font.width(String.valueOf(POINTS_SAMPLE));
        Component buyLabel = text("buy_points");
        int buyWidth = textWidth(buyLabel);
        int fixedWidth = 2 * CONTENT_LEFT + pointsWidth + buyWidth + 2 * SECTION_GAP;
        int xpWidth = Mth.clamp(width - 2 * EDGE - fixedWidth, MIN_XP_WIDTH, XP_WIDTH);
        int islandWidth = fixedWidth + xpWidth;
        bottomIsland = new Island((width - islandWidth) / 2, height - EDGE - ISLAND_HEIGHT, islandWidth, ISLAND_HEIGHT);
        int buyX = bottomIsland.x() + CONTENT_LEFT + pointsWidth + SECTION_GAP;
        buyButton = textButton(buyX, bottomIsland.y() + PAD, buyLabel,
                () -> actions.buy(Screen.hasShiftDown() ? bulkBuyTarget() : 1), SkillButton.BUY, add);
        xpLeft = buyButton.getRight() + SECTION_GAP;
        xpRight = xpLeft + xpWidth;
    }

    private void addPendingIsland(Consumer<AbstractWidget> add) {
        confirmButton = textButton(0, 0, text("confirm"), actions::confirm, SkillButton.PRIMARY, add);
        cancelButton = textButton(0, 0, text("cancel"), actions::cancel, SkillButton.PLAIN, add);
        arrangePendingIsland();
    }

    private void arrangePendingIsland() {
        confirmButton.setWidth(textWidth(confirmButton.getMessage()));
        int islandWidth = 2 * PAD + confirmButton.getWidth() + GAP + cancelButton.getWidth();
        pendingIsland = new Island((width - islandWidth) / 2, bottomIsland.y() - ISLAND_GAP - ISLAND_HEIGHT, islandWidth, ISLAND_HEIGHT);
        confirmButton.setPosition(pendingIsland.x() + PAD, pendingIsland.y() + PAD);
        cancelButton.setPosition(confirmButton.getRight() + GAP, pendingIsland.y() + PAD);
    }

    private int textWidth(Component label) {
        return font.width(label) + TEXT_BUTTON_PADDING;
    }

    private static SkillButton icon(ResourceLocation sprite, String key, Runnable action, Consumer<AbstractWidget> add) {
        SkillButton button = SkillButton.icon(0, 0, TOOL, sprite, text(key), pressed -> action.run());
        button.setTooltip(Tooltip.create(text(key + "_tooltip")));
        add.accept(button);
        return button;
    }

    private SkillButton textButton(int x, int y, Component label, Runnable action, WidgetSprites sprites, Consumer<AbstractWidget> add) {
        SkillButton button = new SkillButton(x, y, textWidth(label), CONTROL, label, pressed -> action.run(), sprites);
        add.accept(button);
        return button;
    }

    private void cycleFilter() {
        filter = filter.next();
        filterButton.setIcon(filter.icon());
        filterButton.setTooltip(Tooltip.create(filterTooltip()));
        actions.search(search.getValue());
    }

    private Component filterTooltip() {
        return text("filter_tooltip", text("filter." + filter.key()).withStyle(ChatFormatting.YELLOW));
    }

    void renderIslands(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        boolean typing = search != null && search.isFocused();
        panel(graphics, searchIsland, typing ? HudSprites.PANEL_HIGHLIGHTED : HudSprites.PANEL);
        panel(graphics, dock, HudSprites.PANEL);
        panel(graphics, bottomIsland, HudSprites.PANEL);
        if (confirmButton.visible) {
            panel(graphics, pendingIsland, HudSprites.PANEL);
        }
        graphics.blitSprite(HudSprites.SEARCH, searchIsland.x() + CONTENT_LEFT, searchIsland.y() + SEARCH_ICON_TOP,
                HudSprites.TOOL_ICON_SIZE, HudSprites.TOOL_ICON_SIZE);
        graphics.blitSprite(HudSprites.POINT, bottomIsland.x() + CONTENT_LEFT, bottomIsland.y() + ICON_TOP, HudSprites.ICON_SIZE,
                HudSprites.ICON_SIZE);
        RenderSystem.disableBlend();
    }

    private static void panel(GuiGraphics graphics, Island island, ResourceLocation sprite) {
        graphics.blitSprite(sprite, island.x(), island.y(), island.width(), island.height());
    }

    void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, int hitIndex, int hitCount, boolean searching) {
        renderSearchCounter(graphics, hitIndex, hitCount, searching);
        int pointsX = bottomIsland.x() + CONTENT_LEFT;
        graphics.drawString(font, String.valueOf(pointsLeft), pointsX + HudSprites.ICON_SIZE + ICON_TEXT_GAP, bottomIsland.y() + TEXT_TOP,
                pointsLeft > 0 ? Palette.GOLD : Palette.textDim(), true);
        ProgressBar.render(graphics, font, xpLeft, xpRight, bottomIsland.y() + ISLAND_HEIGHT / 2, mouseX, mouseY);
        boolean overPoints = mouseX >= pointsX && mouseX < pointsX + pointsWidth && bottomIsland.contains(mouseX, mouseY);
        if (overPoints) {
            TooltipPanel.render(graphics, font, font.split(pointsTooltip, TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    private void renderSearchCounter(GuiGraphics graphics, int hitIndex, int hitCount, boolean searching) {
        if (!searching) {
            return;
        }
        Component counter = hitCount == 0 ? text("search_none").withStyle(ChatFormatting.RED)
                : Component.literal(hitIndex < 0 ? String.valueOf(hitCount) : (hitIndex + 1) + "/" + hitCount);
        graphics.drawString(font, counter, filterButton.getX() - GAP - font.width(counter), searchIsland.y() + TOP_TEXT_TOP,
                Palette.textDim(), false);
    }

    void updateIfChanged(PendingSelection pending, NodeStates nodeStates) {
        if (!key(pending, nodeStates).equals(shown)) {
            update(pending, nodeStates);
        }
    }

    private static List<Object> key(PendingSelection pending, NodeStates nodeStates) {
        long stateRevision = ClientSkillState.state().map(PlayerSkillState::stateRevision).orElse(-1L);
        return List.of(ClientSkillState.ready(), ClientSkillState.requestInFlight(), stateRevision, nodeStates, ClientSkillState.xpBalance(),
                ClientSkillState.economy(), ClientSkillState.curve(), ClientSkillState.worldRules(), pending.request().size());
    }

    void update(PendingSelection pending, NodeStates nodeStates) {
        if (confirmButton == null) {
            return;
        }
        shown = key(pending, nodeStates);
        boolean ready = ClientSkillState.ready();
        boolean busy = ClientSkillState.requestInFlight();
        PlayerSkillState state = ClientSkillState.state().orElse(null);
        int available = state == null ? 0 : state.availablePoints();
        int pendingCost = nodeStates.pendingCost();
        pointsLeft = available - pendingCost;
        pointsTooltip = text("points_left", pointsLeft).withStyle(ChatFormatting.GOLD).append("\n")
                .append(text("points_tooltip", available, pendingCost).withStyle(ChatFormatting.GRAY));
        updateBuyButton(ready && !busy, state == null ? 0 : state.purchasedPoints());
        updatePendingIsland(pending, pendingCost, busy, ready, available);
        statsButton.active = ready;
        offerWhen(communityButton, !ClientSkillState.worldRules().communityTiers().isEmpty(), "community");
        offerWhen(hiddenButton, ClientSkillState.graph().flatMap(TreeRegion::hidden).isPresent(), "hidden");
        signButton.visible = state != null && state.sign().isPresent();
        arrangeDock();
    }

    private static void offerWhen(SkillButton button, boolean available, String key) {
        button.active = available;
        button.setTooltip(Tooltip.create(text(available ? key + "_tooltip" : key + "_unavailable_tooltip")));
    }

    private void updatePendingIsland(PendingSelection pending, int pendingCost, boolean busy, boolean ready, int available) {
        boolean hasPending = !pending.isEmpty();
        confirmButton.visible = hasPending;
        cancelButton.visible = hasPending;
        confirmButton.active = ready && !busy && pendingCost <= available;
        confirmButton.setMessage(text("confirm_count", pending.request().size()));
        confirmButton.setTooltip(Tooltip.create(confirmTooltip(pending, pendingCost, busy, available)));
        cancelButton.setTooltip(Tooltip.create(text("cancel_tooltip")));
        arrangePendingIsland();
    }

    private static Component confirmTooltip(PendingSelection pending, int pendingCost, boolean busy, int available) {
        if (busy) {
            return text("confirm_tooltip_busy");
        }
        return pendingCost > available ? text("confirm_tooltip_cost", pendingCost, available)
                : text("confirm_tooltip", pending.request().size(), pendingCost);
    }

    private void updateBuyButton(boolean idle, int purchased) {
        Payloads.Economy economy = ClientSkillState.economy();
        long balance = ClientSkillState.xpBalance();
        int cost = economy.nextPointCost();
        boolean atLimit = purchased >= economy.pointLimit();
        buyButton.active = idle && economy.xpPurchaseEnabled() && !atLimit && balance >= cost;
        buyButton.setTooltip(Tooltip.create(buyTooltip(economy, balance, purchased, atLimit)));
    }

    private static Component buyTooltip(Payloads.Economy economy, long balance, int purchased, boolean atLimit) {
        if (!ClientSkillState.ready()) {
            return text("loading");
        }
        if (!economy.xpPurchaseEnabled()) {
            return Component.translatable(RejectReason.PROGRESSION_DISABLED.translationKey());
        }
        if (atLimit) {
            return text("buy_tooltip_max", economy.pointLimit());
        }
        int cost = economy.nextPointCost();
        MutableComponent tooltip = text("buy_point").withStyle(ChatFormatting.GOLD).append("\n")
                .append(text("buy_tooltip", GuiText.number(cost), GuiText.number(balance), GuiText.number(Math.max(0, balance - cost))));
        if (balance < cost) {
            tooltip.append("\n").append(text("buy_tooltip_short", GuiText.number(cost - balance)).withStyle(ChatFormatting.RED));
        }
        XpCurve curve = ClientSkillState.curve();
        int bulk = curve.affordableCount(purchased, balance, bulkBuyTarget());
        if (bulk > 1) {
            tooltip.append("\n").append(text("buy_tooltip_bulk", bulk, GuiText.number(curve.totalCost(purchased, bulk)))
                    .withStyle(ChatFormatting.AQUA));
        }
        return tooltip;
    }

    private static int bulkBuyTarget() {
        int purchased = ClientSkillState.state().map(PlayerSkillState::purchasedPoints).orElse(0);
        return Math.max(1, ClientSkillState.economy().pointLimit() - purchased);
    }
}
