package dev.nostalgiacraft.skills.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nostalgiacraft.skills.client.ClientRequests;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.render.BackgroundRenderer;
import dev.nostalgiacraft.skills.client.render.GraphRenderer;
import dev.nostalgiacraft.skills.client.render.Palette;
import dev.nostalgiacraft.skills.client.render.SpriteBatch;
import dev.nostalgiacraft.skills.client.sound.TreeSounds;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.Sector;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.graph.Vec2;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RefundService;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.ToggleService;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SkillTreeScreen extends Screen {
    enum Panel {
        NONE, STATS, RESET, DISCARD, DEPENDENTS, REFUND;

        boolean isModal() {
            return this != NONE && this != STATS;
        }
    }

    private static final double DRAG_THRESHOLD_WINDOW_PX = 4.0;
    private static final double HIT_MARGIN_GUI_PX = 2.0;
    private static final long MESSAGE_MILLIS = 4000;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final double ZOOM_STEP = 1.15;
    private static final double OPENING_ZOOM = 0.6;
    private static final double FIT_PADDING = 40;
    private static final double STATS_PANEL_LEFT_FRACTION = 0.62;
    private static final double MIN_FOCUS_DIRECTION_COSINE = 0.5;
    private static final double NEIGHBOUR_FOCUS_BONUS = 15;
    private static final int BOTTOM_BAR_HEIGHT = 34;
    private static final int ZOOM_LABEL_MARGIN = 8;
    private static final float STARGAZING_PITCH = -70f;
    private static final int REAL_SKY_TINT = 0x40050C10;
    private static final int MIN_TOOLTIP_WIDTH = 120;
    private static final int MAX_TOOLTIP_WIDTH = 260;
    private static final long STATS_REFRESH_MILLIS = 500;
    private static final Set<String> POINT_BOUGHT_MESSAGES = Set.of(Payloads.TransactionResult.POINT_BOUGHT,
            Payloads.TransactionResult.POINTS_BOUGHT);

    final Camera camera = new Camera();
    private final SpriteBatch sprites = new SpriteBatch();
    private final TreeHud hud = new TreeHud(new TreeHud.Actions() {
        @Override
        public void buy(int count) {
            buyPoints(count);
        }

        @Override
        public void toggleStats() {
            panel = panel == Panel.STATS ? Panel.NONE : Panel.STATS;
        }

        @Override
        public void confirm() {
            SkillTreeScreen.this.confirm();
        }

        @Override
        public void cancel() {
            cancelPending();
        }

        @Override
        public void openCommunity() {
            minecraft.setScreen(new CommunityScreen(SkillTreeScreen.this));
        }

        @Override
        public void openSign() {
            minecraft.setScreen(new BirthSignScreen(SkillTreeScreen.this));
        }

        @Override
        public void toggleHiddenRegion() {
            SkillTreeScreen.this.toggleHiddenRegion();
        }

        @Override
        public void home() {
            SkillTreeScreen.this.home();
        }

        @Override
        public void fit() {
            SkillTreeScreen.this.fit();
        }

        @Override
        public void search(String query) {
            onSearch(query);
        }
    });
    private List<Component> statsCache = List.of();
    private List<Object> statsKey = List.of();
    private long statsAt;
    final PendingSelection pending = new PendingSelection();
    private final Set<String> searchHits = new LinkedHashSet<>();
    private final PanelRenderer panelRenderer = new PanelRenderer();
    private final long openedAt = Util.getMillis();
    private final OpeningReveal reveal = new OpeningReveal();
    private final ConfirmSparks sparks = new ConfirmSparks();
    private boolean openingSoundPlayed;
    private List<String> confirming = List.of();
    NodeStates nodeStates = NodeStates.EMPTY;
    boolean statesDirty = true;
    boolean cameraPlaced;
    private int seenGraphRevision = -1;
    private long seenStateRevision = -1;

    Panel panel = Panel.NONE;
    private String dependentsOf;
    private Set<String> dependents = Set.of();
    private String refundOf;
    private RefundService.Plan refundPlan;

    private String hoverId;
    private String focusId;
    private String routeFor;
    private Optional<PathFinder.Route> route = Optional.empty();
    private TooltipCard tooltipCache;
    private String tooltipFor;
    private long tooltipStateRevision = -1;

    private boolean pressed;
    private boolean dragging;
    private int pressButton;
    private double pressX;
    private double pressY;

    private Component message;
    private long messageUntil;
    private int searchIndex = -1;

    private boolean stargazing;
    private float pitchBeforeStargazing;

    double simulatedMouseX = Double.NaN;
    double simulatedMouseY = Double.NaN;

    public SkillTreeScreen() {
        super(Component.translatable("gui.nostalgiaskills.title"));
    }

    public static SkillTreeScreen underTheRealSky() {
        SkillTreeScreen screen = new SkillTreeScreen();
        LocalPlayer stargazer = Minecraft.getInstance().player;
        screen.stargazing = true;
        screen.pitchBeforeStargazing = stargazer == null ? 0 : stargazer.getXRot();
        if (stargazer != null) {
            stargazer.setXRot(STARGAZING_PITCH);
        }
        return screen;
    }

    private static MutableComponent text(String key, Object... arguments) {
        return GuiText.of(key, arguments);
    }

    private static MutableComponent error(RejectReason reason) {
        return Component.translatable(reason.translationKey()).withStyle(ChatFormatting.RED);
    }

    static SkillGraphSnapshot graph() {
        return ClientSkillState.graph().orElseThrow();
    }

    static PlayerSkillState playerState() {
        return ClientSkillState.state().orElseThrow();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        ClientSkillState.setListeners(this::onResult, () -> statesDirty = true);
        ClientRequests.send(new Payloads.RequestSync(ClientSkillState.graph().map(SkillGraphSnapshot::fingerprint).orElse("")));
        playOpeningSoundOnce();
        hud.layout(font, width, height, this::addRenderableWidget);
        double centerX = camera.centerX();
        double centerY = camera.centerY();
        camera.setViewport(0, hud.controlsBottom(), width, height - BOTTOM_BAR_HEIGHT);
        if (cameraPlaced) {
            camera.centerOn(centerX, centerY);
        }
        hud.update(pending, nodeStates);
    }

    private void playOpeningSoundOnce() {
        if (!openingSoundPlayed) {
            openingSoundPlayed = true;
            TreeSounds.opened();
        }
    }

    @Override
    public void removed() {
        ClientSkillState.clearListeners();
        if (stargazing && minecraft != null && minecraft.player != null) {
            minecraft.player.setXRot(pitchBeforeStargazing);
        }
        super.removed();
    }

    public String viewedSectorKey() {
        return ClientSkillState.graph().flatMap(graph -> graph.nodes().values().stream()
                .min(Comparator.comparingDouble(this::distanceToCameraCenter))
                .flatMap(nearest -> graph.sectors().stream().filter(sector -> sector.id().equals(nearest.sector())).findFirst()))
                .map(Sector::nameKey).orElse("");
    }

    private double distanceToCameraCenter(SkillNodeDefinition node) {
        return Math.hypot(node.position().x() - camera.centerX(), node.position().y() - camera.centerY());
    }

    private void placeCamera() {
        if (cameraPlaced || !ClientSkillState.ready()) {
            return;
        }
        cameraPlaced = true;
        if (playerState().startingRoot().isPresent()) {
            camera.zoom = OPENING_ZOOM;
            home();
        } else {
            fit();
        }
    }

    void centerOn(SkillNodeDefinition node) {
        camera.centerOn(node.position().x(), node.position().y());
    }

    private void home() {
        ClientSkillState.graph().ifPresent(graph -> ClientSkillState.state().flatMap(PlayerSkillState::startingRoot)
                .flatMap(graph::node).ifPresentOrElse(this::centerOn, () -> camera.centerOn(0, 0)));
    }

    void fit() {
        ClientSkillState.graph().flatMap(TreeRegion::main).ifPresent(this::fit);
    }

    void fit(TreeRegion region) {
        camera.fit(region.minX(), region.minY(), region.maxX(), region.maxY(), FIT_PADDING);
    }

    private void toggleHiddenRegion() {
        if (hiddenPresence() > 0) {
            fit();
        } else {
            ClientSkillState.graph().flatMap(TreeRegion::hidden).ifPresent(this::fit);
        }
    }

    private double hiddenPresence() {
        return ClientSkillState.graph().flatMap(TreeRegion::hidden)
                .map(region -> region.presenceAt(camera.centerX(), camera.centerY())).orElse(0.0);
    }

    void refreshStatesIfDirty() {
        if (statesDirty) {
            refreshStates();
        }
    }

    private void refreshStates() {
        if (ClientSkillState.graph().isEmpty() || ClientSkillState.state().isEmpty()) {
            return;
        }
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = playerState();
        dropOutdatedPending(graph, state);
        nodeStates = NodeStates.compute(graph, state, pending.request());
        if (panel == Panel.REFUND) {
            refundPlan = RefundService.plan(graph, state, refundOf, ClientSkillState.economy().nodeRefundsEnabled());
            if (!refundPlan.ok()) {
                panel = Panel.NONE;
            }
        }
        refreshSearchHits();
        statesDirty = false;
        tooltipCache = null;
        routeFor = null;
        hud.update(pending, nodeStates);
    }

    private void refreshSearchHits() {
        if (hud.search() == null) {
            return;
        }
        int kept = searchIndex;
        onSearch(hud.search().getValue());
        searchIndex = Math.min(kept, searchHits.size() - 1);
    }

    private void dropOutdatedPending(SkillGraphSnapshot graph, PlayerSkillState state) {
        boolean graphChanged = seenGraphRevision != -1 && seenGraphRevision != ClientSkillState.graphRevision();
        if (graphChanged && !pending.isEmpty()) {
            pending.clear();
            flash(text("pending_invalidated"));
        }
        seenGraphRevision = ClientSkillState.graphRevision();
        if (state.stateRevision() != seenStateRevision) {
            seenStateRevision = state.stateRevision();
            pending.retainValid(graph, state);
        }
    }

    private boolean affordable(SkillNodeDefinition node, PlayerSkillState state) {
        return state.availablePoints() - nodeStates.pendingCost() >= AllocationService.priceWithFreeStart(graph(), state, pending.nodes(), node);
    }

    private void flash(Component text) {
        message = text;
        messageUntil = Util.getMillis() + MESSAGE_MILLIS;
    }

    private void onResult(Payloads.TransactionResult result) {
        if (result.ok() && result.messageKey().equals(Payloads.TransactionResult.ALLOCATED)) {
            celebrateAllocation();
            pending.clear();
        }
        if (result.ok() && POINT_BOUGHT_MESSAGES.contains(result.messageKey())) {
            TreeSounds.pointBought();
        }
        flash(ClientSkillState.message(result).withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (result.ok() && (panel == Panel.RESET || panel == Panel.REFUND)) {
            panel = Panel.NONE;
        }
        statesDirty = true;
    }

    private void celebrateAllocation() {
        if (!ClientSkillState.ready() || confirming.isEmpty()) {
            return;
        }
        SkillGraphSnapshot graph = graph();
        Set<String> litBefore = new HashSet<>(playerState().allocations().keySet());
        confirming.forEach(litBefore::remove);
        sparks.start(SparkRoutes.of(graph, litBefore, confirming.stream().distinct().toList()));
        TreeSounds.confirmed(confirming.stream().map(graph.nodes()::get)
                .anyMatch(node -> node != null && node.type() == NodeType.KEYSTONE));
        confirming = List.of();
    }

    void buyPoints(int count) {
        ClientSkillState.state().ifPresent(state -> {
            ClientRequests.send(new Payloads.BuyPoint(ClientRequests.newRequest(), state.stateRevision(), count));
            hud.update(pending, nodeStates);
        });
    }

    void confirm() {
        if (pending.isEmpty() || ClientSkillState.requestInFlight() || !ClientSkillState.ready()) {
            return;
        }
        confirming = pending.request();
        ClientRequests.send(new Payloads.ConfirmAllocation(ClientRequests.newRequest(), playerState().stateRevision(), ClientSkillState.graphRevision(),
                confirming));
        hud.update(pending, nodeStates);
    }

    private void cancelPending() {
        pending.clear();
        statesDirty = true;
    }

    public void openResetPanel() {
        panel = Panel.RESET;
    }

    void sendReset() {
        ClientSkillState.state().ifPresent(state -> ClientRequests.send(
                new Payloads.RequestReset(ClientRequests.newRequest(), state.stateRevision(), ClientSkillState.graphRevision())));
    }

    void openRefundPanel(String nodeId) {
        RefundService.Plan plan = RefundService.plan(graph(), playerState(), nodeId, ClientSkillState.economy().nodeRefundsEnabled());
        if (!plan.ok()) {
            flash(error(plan.reason().orElseThrow()));
            return;
        }
        refundOf = nodeId;
        refundPlan = plan;
        panel = Panel.REFUND;
    }

    void sendRefund() {
        if (refundOf == null || ClientSkillState.requestInFlight()) {
            return;
        }
        ClientSkillState.state().ifPresent(state -> ClientRequests.send(new Payloads.RequestRefund(ClientRequests.newRequest(), state.stateRevision(),
                ClientSkillState.graphRevision(), refundOf)));
    }

    private void sendToggle(String nodeId) {
        if (nodeId == null || !ClientSkillState.ready() || ClientSkillState.requestInFlight()) {
            return;
        }
        PlayerSkillState state = playerState();
        Optional<RejectReason> rejection = ToggleService.rejection(graph(), state, nodeId);
        if (rejection.isPresent()) {
            flash(error(rejection.get()));
            return;
        }
        ClientRequests.send(new Payloads.ToggleNode(ClientRequests.newRequest(), state.stateRevision(),
                ClientSkillState.graphRevision(), nodeId));
    }

    private void toggle(String nodeId, boolean addRoute) {
        if (!ClientSkillState.ready()) {
            return;
        }
        switch (nodeStates.of(nodeId)) {
            case ALLOCATED -> toggleAllocated(nodeId, addRoute);
            case PENDING -> togglePending(nodeId);
            case AVAILABLE -> toggleAvailable(nodeId);
            case CONFLICTING, LOCKED -> toggleLocked(nodeId, addRoute);
            case HIDDEN -> {
            }
        }
    }

    private void toggleAllocated(String nodeId, boolean shiftHeld) {
        if (!shiftHeld && AllocationService.canRankUp(graph(), playerState(), nodeId)) {
            toggleRankUp(nodeId);
        } else if (ClientSkillState.economy().nodeRefundsEnabled()) {
            openRefundPanel(nodeId);
        } else {
            flash(text("already_allocated", Component.translatable(graph().nodes().get(nodeId).nameKey())));
        }
    }

    private void toggleRankUp(String nodeId) {
        if (pending.extraRanks(nodeId) >= AllocationService.rankRoom(graph(), playerState(), nodeId)) {
            pending.clearRanks(nodeId);
            statesDirty = true;
        } else {
            addPendingRank(nodeId);
        }
    }

    private void addPendingRank(String nodeId) {
        if (playerState().availablePoints() - nodeStates.pendingCost() >= graph().nodes().get(nodeId).cost()) {
            pending.addRank(nodeId);
            statesDirty = true;
            TreeSounds.nodeMarked();
        } else {
            flash(error(RejectReason.INSUFFICIENT_POINTS));
        }
    }

    private boolean canMarkAnotherRank(String nodeId) {
        return pending.contains(nodeId) && graph().nodes().get(nodeId).ranked()
                && pending.extraRanks(nodeId) < AllocationService.rankRoom(graph(), playerState(), nodeId);
    }

    private void togglePending(String clickedId) {
        if (canMarkAnotherRank(clickedId)) {
            addPendingRank(clickedId);
            return;
        }
        String nodeId = pending.contains(clickedId) ? clickedId : graph().portalDestination(clickedId).orElse(clickedId);
        Set<String> chain = AllocationService.dependentsOfPendingRemoval(graph(), playerState(), pending.nodes(), nodeId);
        if (chain.isEmpty()) {
            pending.remove(nodeId);
            statesDirty = true;
        } else {
            dependentsOf = nodeId;
            dependents = chain;
            panel = Panel.DEPENDENTS;
        }
    }

    private void toggleAvailable(String nodeId) {
        if (affordable(graph().nodes().get(nodeId), playerState())) {
            pending.add(nodeId);
            statesDirty = true;
            TreeSounds.nodeMarked();
        } else {
            flash(error(RejectReason.INSUFFICIENT_POINTS));
        }
    }

    private void toggleLocked(String nodeId, boolean addRoute) {
        if (addRoute) {
            addRouteTo(nodeId);
            return;
        }
        RejectReason why = AllocationService.eligibility(graph(), playerState(), pending.nodes(), nodeId)
                .map(AllocationService.Rejection::reason).orElse(RejectReason.NOT_CONNECTED);
        flash(error(why));
    }

    private void addRouteTo(String nodeId) {
        PlayerSkillState state = playerState();
        Optional<PathFinder.Route> found = PathFinder.route(graph(), state, pending.nodes(), nodeId).filter(PathFinder.Route::valid);
        if (found.isEmpty()) {
            flash(text("route.none").withStyle(ChatFormatting.RED));
            return;
        }
        int cost = found.get().cost();
        int pendingCost = nodeStates.pendingCost();
        if (state.availablePoints() - pendingCost < cost) {
            flash(text("confirm_tooltip_cost", pendingCost + cost, state.availablePoints()).withStyle(ChatFormatting.RED));
            return;
        }
        pending.addAll(found.get().newNodes());
        statesDirty = true;
        TreeSounds.nodeMarked();
        flash(text("route.added", found.get().newNodes().size(), cost));
    }

    private void onSearch(String text) {
        searchHits.clear();
        searchIndex = -1;
        String query = text.trim().toLowerCase(Locale.ROOT);
        if (query.length() < MIN_SEARCH_LENGTH) {
            return;
        }
        ClientSkillState.graph().ifPresent(graph -> graph.nodes().values().stream()
                .filter(node -> isVisible(node) && searchableText(node).contains(query))
                .forEach(node -> searchHits.add(node.id())));
    }

    private boolean isVisible(SkillNodeDefinition node) {
        return nodeStates.of(node.id()) != NodeState.HIDDEN;
    }

    private static String searchableText(SkillNodeDefinition node) {
        return (SkillTexts.name(node).getString() + " " + SkillTexts.description(node).getString()
                + " " + String.join(" ", node.tags()) + " " + effectText(node)).toLowerCase(Locale.ROOT);
    }

    private static String effectText(SkillNodeDefinition node) {
        return SkillTexts.effectLines(node).stream().map(line -> line.text().getString()).collect(Collectors.joining(" "));
    }

    private void nextSearchHit() {
        if (searchHits.isEmpty()) {
            return;
        }
        List<String> hits = List.copyOf(searchHits);
        searchIndex = (searchIndex + 1) % hits.size();
        focus(hits.get(searchIndex));
    }

    private void focus(String nodeId) {
        focusId = nodeId;
        ClientSkillState.graph().flatMap(graph -> graph.node(nodeId)).ifPresent(node -> {
            centerOn(node);
            narrate(node);
        });
    }

    private void narrate(SkillNodeDefinition node) {
        if (minecraft != null && minecraft.getNarrator().isActive()) {
            String stateKey = "state." + nodeStates.of(node.id()).name().toLowerCase(Locale.ROOT);
            minecraft.getNarrator().sayNow(SkillTexts.name(node).append(". ").append(text(stateKey))
                    .append(". ").append(SkillTexts.description(node)));
        }
    }

    private void moveFocus(double directionX, double directionY) {
        if (ClientSkillState.graph().isEmpty()) {
            return;
        }
        SkillGraphSnapshot graph = graph();
        if (focusId == null || !graph.contains(focusId)) {
            focus(ClientSkillState.state().flatMap(PlayerSkillState::startingRoot).orElse(graph.roots().get(0)));
            return;
        }
        SkillNodeDefinition current = graph.nodes().get(focusId);
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (SkillNodeDefinition candidate : graph.nodes().values()) {
            double score = isVisible(candidate) ? focusScore(graph, current, candidate, directionX, directionY) : Double.MAX_VALUE;
            if (score < bestScore) {
                bestScore = score;
                best = candidate.id();
            }
        }
        if (best != null) {
            focus(best);
        }
    }

    private static double focusScore(SkillGraphSnapshot graph, SkillNodeDefinition current, SkillNodeDefinition candidate,
                                     double directionX, double directionY) {
        double offsetX = candidate.position().x() - current.position().x();
        double offsetY = candidate.position().y() - current.position().y();
        double distance = Math.hypot(offsetX, offsetY);
        if (distance < 1e-6) {
            return Double.MAX_VALUE;
        }
        double cosine = (offsetX * directionX + offsetY * directionY) / distance;
        if (cosine < MIN_FOCUS_DIRECTION_COSINE) {
            return Double.MAX_VALUE;
        }
        boolean neighbour = graph.neighbours(current.id()).contains(candidate.id());
        return distance * (2 - cosine) - (neighbour ? NEIGHBOUR_FOCUS_BONUS : 0);
    }

    private String nodeAt(double mouseX, double mouseY) {
        if (ClientSkillState.graph().isEmpty() || !camera.inViewport(mouseX, mouseY)) {
            return null;
        }
        String nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SkillNodeDefinition node : graph().nodes().values()) {
            double distance = Math.hypot(mouseX - camera.toScreenX(node.position().x()),
                    mouseY - camera.toScreenY(node.position().y()));
            double reach = GraphRenderer.screenRadius(node, camera) + HIT_MARGIN_GUI_PX;
            if (isVisible(node) && distance <= reach && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = node.id();
            }
        }
        return nearest;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (panel.isModal()) {
            panelRenderer.click(mouseX, mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (hud.search().isFocused()) {
            hud.search().setFocused(false);
            setFocused(null);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            sendToggle(nodeAt(mouseX, mouseY));
            return true;
        }
        boolean panButton = button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        if (camera.inViewport(mouseX, mouseY) && panButton) {
            pressed = true;
            dragging = false;
            pressButton = button;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!pressed || button != pressButton) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        double threshold = DRAG_THRESHOLD_WINDOW_PX / Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        dragging |= Math.hypot(mouseX - pressX, mouseY - pressY) > threshold;
        if (dragging) {
            camera.pan(dragX, dragY);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed || button != pressButton) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        pressed = false;
        String clicked = dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT ? null : nodeAt(mouseX, mouseY);
        if (clicked != null) {
            focusId = clicked;
            toggle(clicked, hasShiftDown());
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panel == Panel.STATS && mouseX > width * STATS_PANEL_LEFT_FRACTION) {
            panelRenderer.scroll(scrollY);
            return true;
        }
        if (camera.inViewport(mouseX, mouseY) && scrollY != 0) {
            double minZoom = Math.min(ClientConfig.ZOOM_MIN.get(), camera.zoomValue());
            camera.zoomAt(mouseX, mouseY, Math.pow(ZOOM_STEP, scrollY), minZoom, ClientConfig.ZOOM_MAX.get());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && hud.search().isFocused()) {
            hud.search().setFocused(false);
            setFocused(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeTopmost();
            return true;
        }
        if (hud.search().isFocused()) {
            if (isEnter(key)) {
                nextSearchHit();
                return true;
            }
            return hud.search().keyPressed(key, scanCode, modifiers);
        }
        if (panel.isModal()) {
            if (isEnter(key)) {
                panelRenderer.activateSafe();
            }
            return true;
        }
        return treeKeyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    private void closeTopmost() {
        if (panel != Panel.NONE) {
            panel = Panel.NONE;
        } else if (!pending.isEmpty()) {
            panel = Panel.DISCARD;
        } else {
            onClose();
        }
    }

    private boolean treeKeyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirm();
            case GLFW.GLFW_KEY_HOME -> home();
            case GLFW.GLFW_KEY_LEFT -> moveFocus(-1, 0);
            case GLFW.GLFW_KEY_RIGHT -> moveFocus(1, 0);
            case GLFW.GLFW_KEY_UP -> moveFocus(0, -1);
            case GLFW.GLFW_KEY_DOWN -> moveFocus(0, 1);
            case GLFW.GLFW_KEY_SPACE -> toggleFocused();
            case GLFW.GLFW_KEY_F -> focusSearchOrFit();
            case GLFW.GLFW_KEY_T -> sendToggle(focusId);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void toggleFocused() {
        if (focusId != null) {
            toggle(focusId, hasShiftDown());
        }
    }

    private void focusSearchOrFit() {
        if (hasControlDown()) {
            setFocused(hud.search());
            hud.search().setFocused(true);
        } else {
            fit();
        }
    }

    private int effectiveMouseX(int mouseX) {
        return Double.isNaN(simulatedMouseX) ? mouseX : (int) simulatedMouseX;
    }

    private int effectiveMouseY(int mouseY) {
        return Double.isNaN(simulatedMouseY) ? mouseY : (int) simulatedMouseY;
    }

    private double secondsOpen() {
        return (Util.getMillis() - openedAt) / 1000.0;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        double time = secondsOpen();
        if (stargazing) {
            graphics.fill(0, 0, width, height, REAL_SKY_TINT);
        } else {
            BackgroundRenderer.render(graphics, width, height, camera.centerX(), camera.centerY(), time);
        }
        BackgroundRenderer.veil(graphics, width, height, hiddenPresence());
        refreshStatesIfDirty();
        placeCamera();
        if (ClientSkillState.graph().isEmpty() || ClientSkillState.state().isEmpty()) {
            graphics.drawCenteredString(font, text("loading"), width / 2, height / 2, Palette.TEXT);
            return;
        }
        SkillGraphSnapshot graph = graph();
        PlayerSkillState state = playerState();
        hoverId = panel.isModal() ? null : nodeAt(effectiveMouseX(mouseX), effectiveMouseY(mouseY));
        Vec2 origin = revealOrigin(graph, state);
        GraphRenderer.Fade fade = reveal.fade(origin.x(), origin.y());
        graphics.enableScissor((int) camera.left(), (int) camera.top(), (int) camera.right(), (int) camera.bottom());
        GraphRenderer.edges(graphics, graph, camera, nodeStates::of, routePreview(graph, state), fade);
        GraphRenderer.portalLinks(graphics, graph, camera, nodeStates::of, fade, time);
        Set<String> activePortals = activePortals(graph);
        int budget = state.availablePoints() - nodeStates.pendingCost();
        boolean freeStart = AllocationService.freeStartOpen(graph, state, pending.nodes());
        for (SkillNodeDefinition node : graph.nodes().values()) {
            boolean highlighted = node.id().equals(focusId) || node.id().equals(hoverId);
            boolean affordable = budget >= (freeStart && node.isRoot() ? 0 : AllocationService.priceOf(graph, node, state::isAllocated));
            GraphRenderer.node(sprites, node, camera, nodeStates.of(node.id()), affordable, highlighted,
                    searchHits.contains(node.id()), activePortals.contains(node.id()), state.isDisabled(node.id()), ranksOf(node, state),
                    fade, time);
        }
        sprites.draw(graphics);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        sparks.render(graphics, camera);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        graphics.disableScissor();
    }

    private GraphRenderer.Ranks ranksOf(SkillNodeDefinition node, PlayerSkillState state) {
        int marked = pending.marked(node.id());
        return node.ranked() ? new GraphRenderer.Ranks(state.rank(node.id()), marked, node.maxRank())
                : GraphRenderer.Ranks.NONE;
    }

    private Vec2 revealOrigin(SkillGraphSnapshot graph, PlayerSkillState state) {
        return state.startingRoot().flatMap(graph::node)
                .map(hub -> new Vec2(camera.toScreenX(hub.position().x()), camera.toScreenY(hub.position().y())))
                .orElse(new Vec2(camera.vcx(), camera.vcy()));
    }

    private Set<String> activePortals(SkillGraphSnapshot graph) {
        if (hoverId == null) {
            return Set.of();
        }
        return graph.portalDestination(hoverId).map(twin -> Set.of(hoverId, twin)).orElse(Set.of());
    }

    private Set<String> routePreview(SkillGraphSnapshot graph, PlayerSkillState state) {
        NodeState hovered = hoverId == null ? null : nodeStates.of(hoverId);
        if (hovered != NodeState.LOCKED && hovered != NodeState.CONFLICTING && hovered != NodeState.SEALED) {
            return Set.of();
        }
        if (!hoverId.equals(routeFor)) {
            routeFor = hoverId;
            route = PathFinder.route(graph, state, pending.nodes(), hoverId);
        }
        if (route.filter(PathFinder.Route::valid).isEmpty()) {
            return Set.of();
        }
        Set<String> preview = new LinkedHashSet<>(route.get().newNodes());
        preview.addAll(state.allocations().keySet());
        preview.addAll(pending.nodes());
        return preview;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int pointerX = effectiveMouseX(mouseX);
        int pointerY = effectiveMouseY(mouseY);
        super.render(graphics, pointerX, pointerY, partialTick);
        ProgressBar.render(graphics, font, width, height, pointerX, pointerY);
        Component zoom = text("zoom", String.format(Locale.ROOT, "%.2f×", camera.zoomValue()));
        graphics.drawString(font, zoom, width - font.width(zoom) - ZOOM_LABEL_MARGIN, height - 12, Palette.TEXT_DIM, true);
        if (message != null && Util.getMillis() < messageUntil) {
            graphics.drawCenteredString(font, message, width / 2, hud.controlsBottom() + 4, Palette.TEXT);
        }
        renderPanel(graphics, pointerX, pointerY);
        if (hoverId != null && ClientSkillState.ready() && panel != Panel.STATS) {
            renderNodeTooltip(graphics, pointerX, pointerY);
        }
    }

    private void renderNodeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerSkillState state = playerState();
        if (tooltipCache == null || !hoverId.equals(tooltipFor) || tooltipStateRevision != state.stateRevision()) {
            tooltipFor = hoverId;
            tooltipStateRevision = state.stateRevision();
            Optional<PathFinder.Route> hoveredRoute = hoverId.equals(routeFor) ? route : Optional.empty();
            tooltipCache = SkillTooltips.build(graph(), state, pending.request(), graph().nodes().get(hoverId), nodeStates.of(hoverId),
                    nodeStates.pendingCost(), hoveredRoute, ClientConfig.DETAILED_TOOLTIPS.get());
        }
        int maxWidth = Math.max(MIN_TOOLTIP_WIDTH, Math.min(MAX_TOOLTIP_WIDTH, width / 2 - 12));
        TooltipPanel.render(graphics, font, tooltipCache, maxWidth, mouseX, mouseY);
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        panelRenderer.clear();
        if (panel == Panel.NONE || !ClientSkillState.ready()) {
            panelRenderer.resetScroll();
            return;
        }
        if (panel == Panel.REFUND && !refundStillValid()) {
            panel = Panel.NONE;
            return;
        }
        panelRenderer.render(graphics, font, panelView(), width, height, hud.controlsBottom(), mouseX, mouseY);
    }

    private boolean refundStillValid() {
        return refundPlan != null && refundPlan.ok() && refundOf != null && playerState().isAllocated(refundOf)
                && graph().contains(refundOf);
    }

    List<Component> statsLines() {
        List<Object> key = List.of(playerState().stateRevision(), pending.request(), nodeStates);
        long now = Util.getMillis();
        if (!key.equals(statsKey) || now - statsAt > STATS_REFRESH_MILLIS) {
            statsCache = StatsPanel.lines(Minecraft.getInstance().player, playerState(), graph(), pending.request(), nodeStates.pendingCost());
            statsKey = key;
            statsAt = now;
        }
        return statsCache;
    }

    private PanelRenderer.View panelView() {
        Payloads.Economy economy = ClientSkillState.economy();
        return switch (panel) {
            case STATS -> new PanelRenderer.View(statsLines(), List.of(), true);
            case RESET -> modal(PanelContents.reset(graph(), playerState(), economy),
                    new PanelRenderer.Action(text("reset.confirm"), this::sendResetIfIdle, true));
            case REFUND -> modal(PanelContents.refund(graph(), playerState(), refundPlan, economy),
                    new PanelRenderer.Action(text("refund.confirm"), this::sendRefund, true));
            case DISCARD -> new PanelRenderer.View(PanelContents.discard(pending.request().size(), nodeStates.pendingCost()), List.of(
                    new PanelRenderer.Action(text("discard.discard"), this::discardAndClose, true),
                    new PanelRenderer.Action(text("discard.keep"), this::closePanel, false)), false);
            case DEPENDENTS -> modal(PanelContents.dependents(graph(), dependentsOf, dependents),
                    new PanelRenderer.Action(text("dependents.remove"), this::removeDependentChain, true));
            case NONE -> throw new IllegalStateException("no panel is open");
        };
    }

    private PanelRenderer.View modal(List<Component> lines, PanelRenderer.Action primary) {
        return new PanelRenderer.View(lines, List.of(primary, new PanelRenderer.Action(text("cancel"), this::closePanel, false)), false);
    }

    private void closePanel() {
        panel = Panel.NONE;
    }

    private void sendResetIfIdle() {
        if (!ClientSkillState.requestInFlight()) {
            sendReset();
        }
    }

    private void discardAndClose() {
        pending.clear();
        panel = Panel.NONE;
        onClose();
    }

    private void removeDependentChain() {
        pending.remove(dependentsOf);
        pending.removeAll(dependents);
        statesDirty = true;
        panel = Panel.NONE;
    }

    @Override
    public void tick() {
        super.tick();
        hud.updateIfChanged(pending, nodeStates);
    }

    private List<Object> controlsKey() {
        long stateRevision = ClientSkillState.state().map(PlayerSkillState::stateRevision).orElse(-1L);
        return List.of(ClientSkillState.ready(), ClientSkillState.requestInFlight(), stateRevision, nodeStates, ClientSkillState.xpBalance(),
                ClientSkillState.economy(), ClientSkillState.curve(), ClientSkillState.worldRules(), pending.request().size());
    }
}
