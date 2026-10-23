package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.XpCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

        void search(String text);
    }

    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final int ROW_SPACING = 4;
    private static final int TOP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_TEXT_PADDING = 20;
    private static final int MIN_BUTTON_WIDTH = 132;
    private static final int MAX_BUTTON_WIDTH = 150;
    private static final int NARROW_WIDTH = 60;
    private static final int CAMERA_BUTTON_WIDTH = 44;
    private static final int BESIDE_SEARCH_WIDTH = 120;
    private static final int BELOW_SEARCH_WIDTH = 140;
    private static final int COMMUNITY_BUTTON_WIDTH = 72;
    private static final int COMMUNITY_BUTTON_BOTTOM_OFFSET = 26;
    private static final int SIGN_BUTTON_BOTTOM_OFFSET = 50;
    private static final int HIDDEN_BUTTON_BOTTOM_OFFSET = 74;
    private static final int POINTS_LABEL_SAMPLE = 888;

    private final Actions actions;
    private Button buyButton;
    private Button pointsButton;
    private Button statsButton;
    private Button confirmButton;
    private Button cancelButton;
    private Button communityButton;
    private Button signButton;
    private Button hiddenButton;
    private EditBox search;
    private int controlsBottom;
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

    int controlsBottom() {
        return controlsBottom;
    }

    void layout(Font font, int width, int height, Consumer<AbstractWidget> add) {
        int buttonWidth = mainButtonWidth(font, width);
        int groupWidth = 3 * buttonWidth + 2 * GAP;
        int groupLeft = (width - groupWidth) / 2;
        int secondRowY = TOP + BUTTON_HEIGHT + ROW_SPACING;
        addMainButtons(width, groupLeft, secondRowY, buttonWidth, add);
        boolean toolsFitBeside = groupLeft - MARGIN >= NARROW_WIDTH
                && width - (groupLeft + groupWidth) - MARGIN >= 2 * CAMERA_BUTTON_WIDTH + GAP;
        int toolsY = toolsFitBeside ? TOP : secondRowY + BUTTON_HEIGHT + ROW_SPACING;
        int searchWidth = toolsFitBeside ? Math.min(BESIDE_SEARCH_WIDTH, Math.max(NARROW_WIDTH, groupLeft - 2 * MARGIN))
                : Math.min(BELOW_SEARCH_WIDTH, width / 3);
        addTools(font, width, toolsY, searchWidth, add);
        communityButton = corner(height - COMMUNITY_BUTTON_BOTTOM_OFFSET, "community", actions::openCommunity, add);
        signButton = corner(height - SIGN_BUTTON_BOTTOM_OFFSET, "sign", actions::openSign, add);
        hiddenButton = corner(height - HIDDEN_BUTTON_BOTTOM_OFFSET, "hidden", actions::toggleHiddenRegion, add);
        controlsBottom = (toolsFitBeside ? secondRowY : toolsY) + BUTTON_HEIGHT + ROW_SPACING;
        shown = List.of();
    }

    private static int mainButtonWidth(Font font, int width) {
        int widestLabel = Stream.of(text("buy_point"), text("points_left", POINTS_LABEL_SAMPLE), text("show_stats"),
                text("confirm"), text("cancel")).mapToInt(font::width).max().orElse(0);
        int preferred = Mth.clamp(widestLabel + BUTTON_TEXT_PADDING, MIN_BUTTON_WIDTH, MAX_BUTTON_WIDTH);
        int available = width - 2 * MARGIN;
        return 3 * preferred + 2 * GAP > available ? Math.max(NARROW_WIDTH, (available - 2 * GAP) / 3) : preferred;
    }

    private void addMainButtons(int width, int groupLeft, int secondRowY, int buttonWidth, Consumer<AbstractWidget> add) {
        int step = buttonWidth + GAP;
        buyButton = button(groupLeft, TOP, buttonWidth, text("buy_point"),
                () -> actions.buy(Screen.hasShiftDown() ? bulkBuyTarget() : 1), add);
        pointsButton = button(groupLeft + step, TOP, buttonWidth, Component.empty(), () -> { }, add);
        pointsButton.active = false;
        statsButton = button(groupLeft + 2 * step, TOP, buttonWidth, text("show_stats"), actions::toggleStats, add);
        int secondRowLeft = (width - (2 * buttonWidth + GAP)) / 2;
        confirmButton = button(secondRowLeft, secondRowY, buttonWidth, text("confirm"), actions::confirm, add);
        cancelButton = button(secondRowLeft + step, secondRowY, buttonWidth, text("cancel"), actions::cancel, add);
    }

    private void addTools(Font font, int width, int y, int searchWidth, Consumer<AbstractWidget> add) {
        search = new EditBox(font, MARGIN, y, searchWidth, BUTTON_HEIGHT, text("search"));
        search.setHint(text("search_hint"));
        search.setResponder(actions::search);
        add.accept(search);
        int cameraLeft = width - MARGIN - 2 * CAMERA_BUTTON_WIDTH - GAP;
        button(cameraLeft, y, CAMERA_BUTTON_WIDTH, text("home"), actions::home, add).setTooltip(Tooltip.create(text("home_tooltip")));
        button(cameraLeft + CAMERA_BUTTON_WIDTH + GAP, y, CAMERA_BUTTON_WIDTH, text("fit"), actions::fit, add)
                .setTooltip(Tooltip.create(text("fit_tooltip")));
    }

    private static Button corner(int y, String key, Runnable action, Consumer<AbstractWidget> add) {
        Button button = button(MARGIN, y, COMMUNITY_BUTTON_WIDTH, text(key), action, add);
        button.setTooltip(Tooltip.create(text(key + "_tooltip")));
        return button;
    }

    private static Button button(int x, int y, int width, Component label, Runnable action, Consumer<AbstractWidget> add) {
        Button button = new SkillButton(x, y, width, BUTTON_HEIGHT, label, pressed -> action.run());
        add.accept(button);
        return button;
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
        pointsButton.setMessage(text("points_left", available - pendingCost));
        pointsButton.setTooltip(Tooltip.create(text("points_tooltip", available, pendingCost)));
        updateBuyButton(ready && !busy, state == null ? 0 : state.purchasedPoints());
        confirmButton.active = ready && !busy && !pending.isEmpty() && pendingCost <= available;
        confirmButton.setTooltip(Tooltip.create(confirmTooltip(pending, pendingCost, busy, available)));
        cancelButton.active = !pending.isEmpty();
        cancelButton.setTooltip(Tooltip.create(text(pending.isEmpty() ? "cancel_tooltip_empty" : "cancel_tooltip")));
        statsButton.active = ready;
        communityButton.visible = !ClientSkillState.worldRules().communityTiers().isEmpty();
        signButton.visible = state != null && state.sign().isPresent();
        hiddenButton.visible = ClientSkillState.graph().flatMap(TreeRegion::hidden).isPresent();
    }

    private static Component confirmTooltip(PendingSelection pending, int pendingCost, boolean busy, int available) {
        if (pending.isEmpty()) {
            return text("confirm_tooltip_empty");
        }
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
        MutableComponent tooltip = Component.empty().append(text("buy_tooltip", cost, balance, Math.max(0, balance - cost)));
        if (balance < cost) {
            tooltip.append("\n").append(text("buy_tooltip_short", cost - balance).withStyle(ChatFormatting.RED));
        }
        XpCurve curve = ClientSkillState.curve();
        int bulk = curve.affordableCount(purchased, balance, bulkBuyTarget());
        if (bulk > 1) {
            tooltip.append("\n").append(text("buy_tooltip_bulk", bulk, curve.totalCost(purchased, bulk)).withStyle(ChatFormatting.AQUA));
        }
        return tooltip;
    }

    private static int bulkBuyTarget() {
        int purchased = ClientSkillState.state().map(PlayerSkillState::purchasedPoints).orElse(0);
        return Math.max(1, ClientSkillState.economy().pointLimit() - purchased);
    }
}
