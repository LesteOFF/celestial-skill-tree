package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.render.BackgroundRenderer;
import dev.nostalgiacraft.skills.client.render.GraphRenderer;
import dev.nostalgiacraft.skills.client.render.Palette;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.Allocation;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.XpMath;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The skill tree screen. It never decides purchases: it keeps a local pending selection, previews it with the
 * shared allocation rules and sends intents; the confirmed build only changes when the server's state arrives.
 * Does not pause the game.
 */
public final class SkillTreeScreen extends Screen {
    private enum Panel { NONE, STATS, RESET, DISCARD, DEPENDENTS }

    private static final double DRAG_THRESHOLD_WINDOW_PX = 4.0;
    private static final double HIT_MARGIN_GUI_PX = 2.0;

    private final Camera cam = new Camera();
    private final Set<String> pending = new LinkedHashSet<>();
    private final Map<String, NodeState> states = new HashMap<>();
    private final Set<String> searchHits = new LinkedHashSet<>();
    private boolean statesDirty = true;
    private boolean cameraPlaced;
    private int seenGraphRevision = -1;
    private long seenStateRevision = -1;
    private int pendingCost;

    private Panel panel = Panel.NONE;
    private String dependentsOf;
    private Set<String> dependents = Set.of();

    private String hoverId;
    private String focusId;
    private String routeFor;
    private Optional<PathFinder.Route> route = Optional.empty();
    private List<Component> tooltipCache;
    private String tooltipFor;
    private long tooltipStateRev = -1;

    private boolean pressed;
    private boolean dragging;
    private int pressButton;
    private double pressX;
    private double pressY;

    private Component message;
    private long messageUntil;
    private int searchIndex = -1;

    private Button buyButton;
    private Button pointsButton;
    private Button statsButton;
    private Button confirmButton;
    private Button cancelButton;
    private Button homeButton;
    private Button fitButton;
    private EditBox search;
    private final List<PanelButton> panelButtons = new ArrayList<>();
    private int controlsBottom;
    private final long openedAt = Util.getMillis();

    public SkillTreeScreen() {
        super(Component.translatable("gui.nostalgiaskills.title"));
    }

    private static net.minecraft.network.chat.MutableComponent t(String key, Object... args) {
        return Component.translatable("gui." + NostalgiaSkills.MODID + "." + key, args);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ lifecycle & layout

    @Override
    protected void init() {
        ClientSkillState.setListeners(this::onResult, () -> statesDirty = true);
        PacketDistributor.sendToServer(new Payloads.RequestSync(ClientSkillState.graph().map(SkillGraphSnapshot::fingerprint).orElse("")));
        Component buyLabel = t("buy_point");
        Component pointsLabel = t("points_left", 888);
        Component statsLabel = t("show_stats");
        int margin = 8;
        int gap = 6;
        int h = 20;
        int textW = Math.max(font.width(buyLabel), Math.max(font.width(pointsLabel), Math.max(font.width(statsLabel),
                Math.max(font.width(t("confirm")), font.width(t("cancel")))))) + 20;
        int w = Mth.clamp(textW, 132, 150);
        if (3 * w + 2 * gap > width - 2 * margin) {
            w = Math.max(60, (width - 2 * margin - 2 * gap) / 3);
        }
        int groupW = 3 * w + 2 * gap;
        int x0 = (width - groupW) / 2;
        int y1 = 6;
        int y2 = y1 + h + 4;
        buyButton = addRenderableWidget(new SkillButton(x0, y1, w, h, buyLabel, b -> buyPoint()));
        pointsButton = addRenderableWidget(new SkillButton(x0 + w + gap, y1, w, h, Component.empty(), b -> { }));
        pointsButton.active = false;
        statsButton = addRenderableWidget(new SkillButton(x0 + 2 * (w + gap), y1, w, h, statsLabel,
                b -> panel = panel == Panel.STATS ? Panel.NONE : Panel.STATS));
        int row2x = (width - (2 * w + gap)) / 2;
        confirmButton = addRenderableWidget(new SkillButton(row2x, y2, w, h, t("confirm"), b -> confirm()));
        cancelButton = addRenderableWidget(new SkillButton(row2x + w + gap, y2, w, h, t("cancel"), b -> cancelPending()));
        // secondary toolbar: search on the left, camera actions on the right; drops to a third row when tight
        int sw = Math.min(120, Math.max(60, x0 - 2 * margin));
        int camW = 44;
        boolean fitsBeside = x0 - margin >= 60 && width - (x0 + groupW) - margin >= 2 * camW + gap;
        int sy = fitsBeside ? y1 : y2 + h + 4;
        search = new EditBox(font, margin, sy, fitsBeside ? sw : Math.min(140, width / 3), h, t("search"));
        search.setHint(t("search_hint"));
        search.setResponder(this::onSearch);
        addRenderableWidget(search);
        int rx = width - margin - 2 * camW - gap;
        homeButton = addRenderableWidget(new SkillButton(rx, sy, camW, h, t("home"), b -> home()));
        fitButton = addRenderableWidget(new SkillButton(rx + camW + gap, sy, camW, h, t("fit"), b -> fit()));
        homeButton.setTooltip(Tooltip.create(t("home_tooltip")));
        fitButton.setTooltip(Tooltip.create(t("fit_tooltip")));
        controlsBottom = (fitsBeside ? y2 + h : sy + h) + 4;
        double oldCx = cam.centerX(), oldCy = cam.centerY();
        cam.setViewport(0, controlsBottom, width, height - 34);
        if (cameraPlaced) {
            cam.centerOn(oldCx, oldCy); // resizing keeps the same tree point centred
        }
        updateControls();
    }

    @Override
    public void removed() {
        ClientSkillState.clearListeners();
        super.removed();
    }

    private void placeCamera() {
        Optional<SkillGraphSnapshot> g = ClientSkillState.graph();
        if (cameraPlaced || g.isEmpty() || ClientSkillState.state().isEmpty()) {
            return;
        }
        cameraPlaced = true;
        if (ClientSkillState.state().get().startingRoot().isPresent()) {
            cam.zoom = 1.0;
            home();
        } else {
            fit();
        }
    }

    private void home() {
        ClientSkillState.graph().ifPresent(g -> {
            String root = ClientSkillState.state().flatMap(PlayerSkillState::startingRoot).orElse(null);
            if (root != null && g.contains(root)) {
                cam.centerOn(g.nodes().get(root).position().x(), g.nodes().get(root).position().y());
            } else {
                cam.centerOn(0, 0);
            }
        });
    }

    private void fit() {
        ClientSkillState.graph().ifPresent(g -> cam.fit(g.minX(), g.minY(), g.maxX(), g.maxY(), 40));
    }

    // ------------------------------------------------------------------ state

    private void refreshStates() {
        Optional<SkillGraphSnapshot> og = ClientSkillState.graph();
        Optional<PlayerSkillState> os = ClientSkillState.state();
        if (og.isEmpty() || os.isEmpty()) {
            return;
        }
        SkillGraphSnapshot g = og.get();
        PlayerSkillState s = os.get();
        if (seenGraphRevision != -1 && seenGraphRevision != ClientSkillState.graphRevision() && !pending.isEmpty()) {
            pending.clear();
            flash(t("pending_invalidated"));
        }
        seenGraphRevision = ClientSkillState.graphRevision();
        if (s.stateRevision() != seenStateRevision) {
            seenStateRevision = s.stateRevision();
            pending.removeIf(s::isAllocated);
            pending.removeIf(id -> !g.contains(id));
        }
        states.clear();
        AllocationService.Expansion ex = AllocationService.expand(g, s, pending);
        Set<String> held = new LinkedHashSet<>(s.allocations().keySet());
        Map<String, Integer> sectorPoints = new HashMap<>();
        for (Allocation a : s.allocations().values()) {
            g.node(a.nodeId()).ifPresent(n -> sectorPoints.merge(n.sector(), a.paidCost(), Integer::sum));
        }
        boolean startChosen = s.startingRoot().isPresent();
        for (AllocationService.Accepted a : ex.accepted()) {
            held.add(a.nodeId());
            sectorPoints.merge(g.nodes().get(a.nodeId()).sector(), a.cost(), Integer::sum);
            startChosen |= a.startingRoot();
        }
        pendingCost = ex.totalCost();
        for (SkillNodeDefinition n : g.nodes().values()) {
            NodeState st;
            if (s.isAllocated(n.id())) {
                st = NodeState.ALLOCATED;
            } else if (pending.contains(n.id())) {
                st = NodeState.PENDING;
            } else if (!startChosen) {
                st = n.isRoot() ? NodeState.AVAILABLE : NodeState.LOCKED;
            } else {
                AllocationService.Rejection why = AllocationService.reason(g, n, held, sectorPoints);
                st = why == null ? NodeState.AVAILABLE
                        : why.reason() == RejectReason.EXCLUSIVE_CONFLICT ? NodeState.CONFLICTING : NodeState.LOCKED;
            }
            states.put(n.id(), st);
        }
        statesDirty = false;
        tooltipCache = null;
        routeFor = null;
        updateControls();
    }

    private NodeState state(String id) {
        return states.getOrDefault(id, NodeState.LOCKED);
    }

    private boolean affordable(SkillNodeDefinition n, PlayerSkillState s) {
        boolean freeStart = n.isRoot() && s.startingRoot().isEmpty() && pending.stream().noneMatch(p -> ClientSkillState.graph().get().nodes().get(p).isRoot());
        int cost = freeStart ? 0 : n.cost();
        return s.availablePoints() - pendingCost >= cost;
    }

    private void updateControls() {
        boolean ready = ClientSkillState.ready();
        boolean busy = ClientSkillState.requestInFlight();
        PlayerSkillState s = ClientSkillState.state().orElse(null);
        Payloads.Economy eco = ClientSkillState.economy();
        int available = s == null ? 0 : s.availablePoints();
        if (pointsButton != null) {
            pointsButton.setMessage(t("points_left", available - pendingCost));
            pointsButton.setTooltip(Tooltip.create(t("points_tooltip", available, pendingCost)));
        }
        if (buyButton != null) {
            net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
            long balance = p == null ? 0 : XpMath.balance(p.experienceLevel, p.experienceProgress);
            int cost = eco.nextPointCost();
            boolean capped = s != null && s.purchasedPoints() >= eco.pointLimit();
            buyButton.active = ready && !busy && eco.xpPurchaseEnabled() && !capped && balance >= cost;
            Component tip;
            if (!ready) {
                tip = t("loading");
            } else if (!eco.xpPurchaseEnabled()) {
                tip = Component.translatable(RejectReason.PROGRESSION_DISABLED.translationKey());
            } else if (capped) {
                tip = t("buy_tooltip_max", eco.pointLimit());
            } else {
                tip = t("buy_tooltip", cost, balance, Math.max(0, balance - cost));
                if (balance < cost) {
                    tip = Component.empty().append(tip).append("\n").append(t("buy_tooltip_short", cost - balance).withStyle(ChatFormatting.RED));
                }
            }
            buyButton.setTooltip(Tooltip.create(tip));
        }
        if (confirmButton != null) {
            confirmButton.active = ready && !busy && !pending.isEmpty() && pendingCost <= available;
            confirmButton.setTooltip(Tooltip.create(pending.isEmpty() ? t("confirm_tooltip_empty")
                    : busy ? t("confirm_tooltip_busy") : pendingCost > available ? t("confirm_tooltip_cost", pendingCost, available)
                    : t("confirm_tooltip", pending.size(), pendingCost)));
            cancelButton.active = !pending.isEmpty();
            cancelButton.setTooltip(Tooltip.create(pending.isEmpty() ? t("cancel_tooltip_empty") : t("cancel_tooltip")));
            statsButton.active = ready;
        }
    }

    // ------------------------------------------------------------------ actions

    private void flash(Component c) {
        message = c;
        messageUntil = Util.getMillis() + 4000;
    }

    private void onResult(Payloads.TransactionResult r) {
        if (r.ok() && r.messageKey().equals("message.nostalgiaskills.allocated")) {
            pending.clear();
        }
        String[] d = r.detail().split("/");
        flash(Component.translatable(r.messageKey(), (Object[]) d).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (r.ok() && panel == Panel.RESET) {
            panel = Panel.NONE;
        }
        statesDirty = true;
    }

    private void buyPoint() {
        ClientSkillState.state().ifPresent(s -> {
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.BuyPoint(id, s.stateRevision()));
            updateControls();
        });
    }

    private void confirm() {
        if (pending.isEmpty() || ClientSkillState.requestInFlight() || !ClientSkillState.ready()) {
            return;
        }
        PlayerSkillState s = ClientSkillState.state().get();
        UUID id = UUID.randomUUID();
        ClientSkillState.markInFlight(id);
        PacketDistributor.sendToServer(new Payloads.ConfirmAllocation(id, s.stateRevision(), ClientSkillState.graphRevision(),
                List.copyOf(pending)));
        updateControls();
    }

    private void cancelPending() {
        pending.clear();
        statesDirty = true;
    }

    public void openResetPanel() {
        panel = Panel.RESET;
    }

    private void sendReset() {
        ClientSkillState.state().ifPresent(s -> {
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.RequestReset(id, s.stateRevision(), ClientSkillState.graphRevision()));
        });
    }

    private void toggle(String id, boolean addRoute) {
        if (!ClientSkillState.ready()) {
            return;
        }
        SkillGraphSnapshot g = ClientSkillState.graph().get();
        PlayerSkillState s = ClientSkillState.state().get();
        SkillNodeDefinition n = g.nodes().get(id);
        switch (state(id)) {
            case ALLOCATED -> flash(t("already_allocated", Component.translatable(n.nameKey())));
            case PENDING -> {
                Set<String> deps = AllocationService.dependentsOfPendingRemoval(g, s, pending, id);
                if (deps.isEmpty()) {
                    pending.remove(id);
                    statesDirty = true;
                } else {
                    dependentsOf = id;
                    dependents = deps;
                    panel = Panel.DEPENDENTS;
                }
            }
            case AVAILABLE -> {
                if (affordable(n, s)) {
                    pending.add(id);
                    statesDirty = true;
                } else {
                    flash(Component.translatable(RejectReason.INSUFFICIENT_POINTS.translationKey()).withStyle(ChatFormatting.RED));
                }
            }
            case CONFLICTING, LOCKED -> {
                if (addRoute) {
                    Optional<PathFinder.Route> r = PathFinder.route(g, s, pending, id);
                    if (r.isPresent() && r.get().valid()) {
                        int cost = r.get().cost();
                        if (s.availablePoints() - pendingCost >= cost) {
                            pending.addAll(r.get().newNodes());
                            statesDirty = true;
                            flash(t("route.added", r.get().newNodes().size(), cost));
                        } else {
                            flash(t("confirm_tooltip_cost", pendingCost + cost, s.availablePoints()).withStyle(ChatFormatting.RED));
                        }
                    } else {
                        flash(t("route.none").withStyle(ChatFormatting.RED));
                    }
                } else {
                    AllocationService.Rejection why = AllocationService.eligibility(g, s, pending, id);
                    flash(Component.translatable(why == null ? RejectReason.NOT_CONNECTED.translationKey()
                            : why.reason().translationKey()).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private void onSearch(String text) {
        searchHits.clear();
        searchIndex = -1;
        String q = text.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return;
        }
        ClientSkillState.graph().ifPresent(g -> {
            for (SkillNodeDefinition n : g.nodes().values()) {
                String hay = (Component.translatable(n.nameKey()).getString() + " " + Component.translatable(n.descriptionKey()).getString()
                        + " " + String.join(" ", n.tags()) + " " + n.effects().toString()).toLowerCase(Locale.ROOT);
                if (hay.contains(q)) {
                    searchHits.add(n.id());
                }
            }
        });
    }

    private void nextSearchHit() {
        if (searchHits.isEmpty()) {
            return;
        }
        List<String> l = List.copyOf(searchHits);
        searchIndex = (searchIndex + 1) % l.size();
        focus(l.get(searchIndex), true);
    }

    private void focus(String id, boolean center) {
        focusId = id;
        ClientSkillState.graph().ifPresent(g -> {
            SkillNodeDefinition n = g.nodes().get(id);
            if (n == null) {
                return;
            }
            if (center) {
                cam.centerOn(n.position().x(), n.position().y());
            }
            if (minecraft != null && minecraft.getNarrator().isActive()) {
                minecraft.getNarrator().sayNow(Component.translatable(n.nameKey()).append(". ")
                        .append(t("state." + state(id).name().toLowerCase(Locale.ROOT))).append(". ")
                        .append(Component.translatable(n.descriptionKey())));
            }
        });
    }

    /** Arrow-key focus: nearest node whose direction lies within 60 degrees of the arrow. */
    private void moveFocus(double dx, double dy) {
        Optional<SkillGraphSnapshot> og = ClientSkillState.graph();
        if (og.isEmpty()) {
            return;
        }
        SkillGraphSnapshot g = og.get();
        if (focusId == null || !g.contains(focusId)) {
            String start = ClientSkillState.state().flatMap(PlayerSkillState::startingRoot).orElse(g.roots().get(0));
            focus(start, true);
            return;
        }
        SkillNodeDefinition cur = g.nodes().get(focusId);
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (SkillNodeDefinition n : g.nodes().values()) {
            double vx = n.position().x() - cur.position().x();
            double vy = n.position().y() - cur.position().y();
            double d = Math.hypot(vx, vy);
            if (d < 1e-6) {
                continue;
            }
            double cos = (vx * dx + vy * dy) / d;
            if (cos < 0.5) {
                continue;
            }
            double score = d * (2 - cos) + (g.neighbours(focusId).contains(n.id()) ? -15 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = n.id();
            }
        }
        if (best != null) {
            focus(best, true);
        }
    }

    // ------------------------------------------------------------------ input

    private String nodeAt(double mx, double my) {
        Optional<SkillGraphSnapshot> og = ClientSkillState.graph();
        if (og.isEmpty() || !cam.inViewport(mx, my)) {
            return null;
        }
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (SkillNodeDefinition n : og.get().nodes().values()) {
            double sx = cam.toScreenX(n.position().x());
            double sy = cam.toScreenY(n.position().y());
            double d = Math.hypot(mx - sx, my - sy);
            double reach = GraphRenderer.screenRadius(n, cam) + HIT_MARGIN_GUI_PX;
            if (d <= reach && d < bestD) {
                bestD = d;
                best = n.id();
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (panel != Panel.NONE && panel != Panel.STATS) {
            for (PanelButton b : panelButtons) {
                if (b.contains(mx, my)) {
                    b.action.run();
                    return true;
                }
            }
            return true; // modal panels swallow clicks
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (search.isFocused()) {
            search.setFocused(false);
            setFocused(null);
        }
        if (cam.inViewport(mx, my) && (button == 0 || button == 2)) {
            pressed = true;
            dragging = false;
            pressButton = button;
            pressX = mx;
            pressY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressed && button == pressButton) {
            double threshold = DRAG_THRESHOLD_WINDOW_PX / Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
            if (!dragging && Math.hypot(mx - pressX, my - pressY) > threshold) {
                dragging = true;
            }
            if (dragging) {
                cam.pan(dx, dy);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressed && button == pressButton) {
            pressed = false;
            if (!dragging && button == 0) {
                String id = nodeAt(mx, my);
                if (id != null) {
                    focusId = id;
                    toggle(id, hasShiftDown());
                }
            }
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (panel == Panel.STATS && mx > width * 0.62) {
            return true;
        }
        if (cam.inViewport(mx, my) && sy != 0) {
            cam.zoomAt(mx, my, Math.pow(1.15, sy), Math.min(ClientConfig.ZOOM_MIN.get(), cam.zoomValue()), ClientConfig.ZOOM_MAX.get());
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (panel != Panel.NONE) {
                panel = Panel.NONE;
                return true;
            }
            if (!pending.isEmpty()) {
                panel = Panel.DISCARD;
                return true;
            }
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                nextSearchHit();
                return true;
            }
            return search.keyPressed(key, scan, mods);
        }
        if (panel != Panel.NONE && panel != Panel.STATS) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!panelButtons.isEmpty()) {
                    panelButtons.get(0).action.run();
                }
                return true;
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                confirm();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                home();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> moveFocus(-1, 0);
            case GLFW.GLFW_KEY_RIGHT -> moveFocus(1, 0);
            case GLFW.GLFW_KEY_UP -> moveFocus(0, -1);
            case GLFW.GLFW_KEY_DOWN -> moveFocus(0, 1);
            case GLFW.GLFW_KEY_SPACE -> {
                if (focusId != null) {
                    toggle(focusId, hasShiftDown());
                }
            }
            case GLFW.GLFW_KEY_F -> {
                if (hasControlDown()) {
                    setFocused(search);
                    search.setFocused(true);
                } else {
                    fit();
                }
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        double time = (Util.getMillis() - openedAt) / 1000.0;
        BackgroundRenderer.render(g, width, height, cam.centerX(), cam.centerY(), time);
        if (statesDirty) {
            refreshStates();
        }
        placeCamera();
        Optional<SkillGraphSnapshot> og = ClientSkillState.graph();
        if (og.isEmpty() || ClientSkillState.state().isEmpty()) {
            g.drawCenteredString(font, t("loading"), width / 2, height / 2, Palette.TEXT);
            return;
        }
        SkillGraphSnapshot graph = og.get();
        PlayerSkillState s = ClientSkillState.state().get();
        hoverId = panel == Panel.NONE || panel == Panel.STATS ? nodeAt(mouseX, mouseY) : null;
        Set<String> preview = Set.of();
        if (hoverId != null && (state(hoverId) == NodeState.LOCKED || state(hoverId) == NodeState.CONFLICTING)) {
            if (!hoverId.equals(routeFor)) {
                routeFor = hoverId;
                route = PathFinder.route(graph, s, pending, hoverId);
            }
            if (route.isPresent() && route.get().valid()) {
                Set<String> r = new LinkedHashSet<>(route.get().newNodes());
                r.addAll(s.allocations().keySet());
                r.addAll(pending);
                preview = r;
            }
        }
        g.enableScissor((int) cam.left(), (int) cam.top(), (int) cam.right(), (int) cam.bottom());
        GraphRenderer.edges(g, graph, cam, this::state, preview, time);
        for (SkillNodeDefinition n : graph.nodes().values()) {
            GraphRenderer.node(g, n, cam, state(n.id()), affordable(n, s), n.id().equals(focusId) || n.id().equals(hoverId),
                    searchHits.contains(n.id()), time);
        }
        g.disableScissor();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderProgressBar(g, mouseX, mouseY);
        String zoomText = String.format(Locale.ROOT, "%.2f×", cam.zoomValue());
        g.drawString(font, t("zoom", zoomText), width - font.width(t("zoom", zoomText)) - 8, height - 12, Palette.TEXT_DIM, true);
        if (message != null && Util.getMillis() < messageUntil) {
            g.drawCenteredString(font, message, width / 2, controlsBottom + 4, Palette.TEXT);
        }
        renderPanel(g, mouseX, mouseY);
        if (hoverId != null && ClientSkillState.ready() && panel != Panel.STATS) {
            renderNodeTooltip(g, mouseX, mouseY);
        }
    }

    private void renderNodeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        SkillGraphSnapshot graph = ClientSkillState.graph().get();
        PlayerSkillState s = ClientSkillState.state().get();
        if (tooltipCache == null || !hoverId.equals(tooltipFor) || tooltipStateRev != s.stateRevision()) {
            tooltipFor = hoverId;
            tooltipStateRev = s.stateRevision();
            Optional<PathFinder.Route> r = hoverId.equals(routeFor) ? route : Optional.empty();
            tooltipCache = SkillTooltips.build(graph, s, pending, graph.nodes().get(hoverId), state(hoverId), pendingCost, r,
                    ClientConfig.DETAILED_TOOLTIPS.get());
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        int maxW = Math.max(120, Math.min(260, width / 2 - 12));
        for (Component c : tooltipCache) {
            lines.addAll(font.split(c, maxW));
        }
        g.renderTooltip(font, lines, mouseX, mouseY); // vanilla positioner keeps it inside the window
    }

    /** Segmented gold bar: progress of current XP toward the next skill point purchase. */
    private void renderProgressBar(GuiGraphics g, int mouseX, int mouseY) {
        PlayerSkillState s = ClientSkillState.state().orElse(null);
        net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
        if (s == null || p == null) {
            return;
        }
        Payloads.Economy eco = ClientSkillState.economy();
        int barW = Math.min(320, width - 160);
        int x0 = (width - barW) / 2;
        int y0 = height - 18;
        long balance = XpMath.balance(p.experienceLevel, p.experienceProgress);
        boolean max = s.purchasedPoints() >= eco.pointLimit();
        double frac = max ? 1.0 : Math.min(1.0, balance / (double) Math.max(1, eco.nextPointCost()));
        g.fill(x0 - 2, y0 - 2, x0 + barW + 2, y0 + 8, 0xFF000000 | (Palette.BRONZE & 0xFFFFFF));
        g.fill(x0 - 1, y0 - 1, x0 + barW + 1, y0 + 7, 0xFF120E08);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            int sx0 = x0 + i * barW / segments;
            int sx1 = x0 + (i + 1) * barW / segments - 1;
            double segFill = Mth.clamp(frac * segments - i, 0, 1);
            if (segFill > 0) {
                g.fill(sx0, y0, sx0 + (int) Math.round((sx1 - sx0) * segFill), y0 + 6, Palette.GOLD);
                g.fill(sx0, y0 + 4, sx0 + (int) Math.round((sx1 - sx0) * segFill), y0 + 6, Palette.ALLOCATED);
            }
        }
        g.drawString(font, String.valueOf(s.purchasedPoints()), x0 - 6 - font.width(String.valueOf(s.purchasedPoints())), y0 - 1, Palette.GOLD, true);
        Component right = max ? t("maximum") : Component.literal(String.valueOf(s.purchasedPoints() + 1));
        g.drawString(font, right, x0 + barW + 6, y0 - 1, Palette.GOLD, true);
        Component mid = max ? t("maximum") : Component.literal(String.format(Locale.ROOT, "%d%%", (int) Math.floor(frac * 100)));
        g.drawCenteredString(font, mid, width / 2, y0 - 11, Palette.TEXT);
        if (mouseX >= x0 - 2 && mouseX <= x0 + barW + 2 && mouseY >= y0 - 12 && mouseY <= y0 + 8) {
            g.renderTooltip(font, font.split(max ? t("bar_tooltip_max", eco.pointLimit())
                    : t("bar_tooltip", balance, eco.nextPointCost(), s.purchasedPoints()), 220), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ panels

    private record PanelButton(int x, int y, int w, int h, Component label, Runnable action, boolean primary) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        panelButtons.clear();
        if (panel == Panel.NONE || !ClientSkillState.ready()) {
            return;
        }
        SkillGraphSnapshot graph = ClientSkillState.graph().get();
        PlayerSkillState s = ClientSkillState.state().get();
        List<Component> lines = new ArrayList<>();
        switch (panel) {
            case STATS -> lines.addAll(StatsPanel.lines(Minecraft.getInstance().player, s, graph, pending, pendingCost));
            case RESET -> {
                RespecService.Preview pv = RespecService.preview(s);
                lines.add(t("reset.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(t("reset.refund", pv.refund(), pv.removed().size()));
                lines.add(t("reset.keeps").withStyle(ChatFormatting.GRAY));
                pv.removed().stream().limit(10).forEach(id -> lines.add(Component.literal(" - ")
                        .append(Component.translatable(graph.node(id).map(SkillNodeDefinition::nameKey).orElse(id))).withStyle(ChatFormatting.GRAY)));
                if (pv.removed().size() > 10) {
                    lines.add(t("reset.more", pv.removed().size() - 10).withStyle(ChatFormatting.DARK_GRAY));
                }
                if (ClientSkillState.economy().orbRequired()) {
                    lines.add(t("reset.orb").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            case DISCARD -> {
                lines.add(t("discard.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(t("discard.body", pending.size(), pendingCost));
            }
            case DEPENDENTS -> {
                lines.add(t("dependents.title", Component.translatable(graph.nodes().get(dependentsOf).nameKey())).withStyle(ChatFormatting.GOLD));
                dependents.stream().limit(10).forEach(id -> lines.add(Component.literal(" - ")
                        .append(Component.translatable(graph.nodes().get(id).nameKey())).withStyle(ChatFormatting.GRAY)));
                int refund = dependents.stream().mapToInt(id -> graph.nodes().get(id).cost()).sum() + graph.nodes().get(dependentsOf).cost();
                lines.add(t("dependents.cost", dependents.size() + 1, refund));
            }
            default -> {
            }
        }
        int maxW = panel == Panel.STATS ? Math.min(230, width / 2 - 20) : Math.min(260, width - 40);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component c : lines) {
            wrapped.addAll(font.split(c, maxW));
        }
        int lineH = 10;
        int buttonsH = panel == Panel.STATS ? 0 : 26;
        int ph = Math.min(height - controlsBottom - 40, wrapped.size() * lineH + 12 + buttonsH);
        int pw = maxW + 16;
        int px = panel == Panel.STATS ? width - pw - 8 : (width - pw) / 2;
        int py = panel == Panel.STATS ? controlsBottom + 2 : Math.max(controlsBottom + 4, (height - ph) / 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Palette.PANEL_EDGE);
        g.fill(px, py, px + pw, py + ph, Palette.PANEL);
        int y = py + 6;
        int maxLines = (ph - 12 - buttonsH) / lineH;
        for (int i = 0; i < Math.min(maxLines, wrapped.size()); i++) {
            g.drawString(font, wrapped.get(i), px + 8, y, Palette.TEXT, true);
            y += lineH;
        }
        int by = py + ph - 22;
        switch (panel) {
            case RESET -> {
                boolean busy = ClientSkillState.requestInFlight();
                addPanelButton(px + 8, by, (pw - 24) / 2, t("reset.confirm"), () -> {
                    if (!busy) {
                        sendReset();
                    }
                }, true);
                addPanelButton(px + 16 + (pw - 24) / 2, by, (pw - 24) / 2, t("cancel"), () -> panel = Panel.NONE, false);
            }
            case DISCARD -> {
                addPanelButton(px + 8, by, (pw - 24) / 2, t("discard.discard"), () -> {
                    pending.clear();
                    panel = Panel.NONE;
                    onClose();
                }, true);
                addPanelButton(px + 16 + (pw - 24) / 2, by, (pw - 24) / 2, t("discard.keep"), () -> panel = Panel.NONE, false);
            }
            case DEPENDENTS -> {
                addPanelButton(px + 8, by, (pw - 24) / 2, t("dependents.remove"), () -> {
                    pending.remove(dependentsOf);
                    pending.removeAll(dependents);
                    statesDirty = true;
                    panel = Panel.NONE;
                }, true);
                addPanelButton(px + 16 + (pw - 24) / 2, by, (pw - 24) / 2, t("cancel"), () -> panel = Panel.NONE, false);
            }
            default -> {
            }
        }
        for (PanelButton b : panelButtons) {
            boolean hover = b.contains(mouseX, mouseY);
            g.fill(b.x - 1, b.y - 1, b.x + b.w + 1, b.y + b.h + 1, hover ? Palette.GOLD : Palette.BRONZE);
            g.fill(b.x, b.y, b.x + b.w, b.y + b.h, b.primary ? Palette.CONTROL_FILL : Palette.CONTROL_DISABLED);
            g.drawCenteredString(font, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, Palette.TEXT);
        }
        g.pose().popPose();
    }

    private void addPanelButton(int x, int y, int w, Component label, Runnable action, boolean primary) {
        panelButtons.add(new PanelButton(x, y, w, 18, label, action, primary));
    }

    @Override
    public void tick() {
        super.tick();
        updateControls();
    }

    /** Dark red fill with bronze-gold edging; darker neutral fill when disabled (label stays readable). */
    static final class SkillButton extends Button {
        SkillButton(int x, int y, int w, int h, Component label, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hover = isHoveredOrFocused() && active;
            int edge = active ? (hover ? Palette.GOLD : Palette.BRONZE) : 0xFF4A4038;
            g.fill(getX(), getY(), getX() + width, getY() + height, edge);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, active ? Palette.CONTROL_FILL : Palette.CONTROL_DISABLED);
            if (active) {
                g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, 0x40FFFFFF);
            }
            int color = active ? Palette.TEXT : Palette.TEXT_DIM;
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, color);
        }
    }
}
