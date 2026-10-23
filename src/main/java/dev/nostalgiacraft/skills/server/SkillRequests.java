package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.effects.Triggers;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.CommunityProgress;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.RefundService;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RequestGuard;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.ToggleService;
import dev.nostalgiacraft.skills.progression.XpMath;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SkillRequests {
    private static final Map<UUID, RequestGuard> GUARDS = new HashMap<>();
    private static final RateLimiter LIMITER = new RateLimiter();
    private static final Set<RejectReason> STATE_UNCHANGED = Set.of(RejectReason.RATE_LIMITED, RejectReason.DUPLICATE_REQUEST);

    private SkillRequests() {
    }

    private static RequestGuard guard(ServerPlayer player) {
        return GUARDS.computeIfAbsent(player.getUUID(), id -> new RequestGuard());
    }

    public static void forget(ServerPlayer player) {
        GUARDS.remove(player.getUUID());
        LIMITER.forget(player.getUUID());
    }

    public static void onRequestSync(Payloads.RequestSync requestSync, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !withinRateLimit(player)) {
            return;
        }
        boolean graphOutdated = SkillTreeManager.graph()
                .filter(graph -> !graph.fingerprint().equals(requestSync.knownFingerprint())).isPresent();
        if (graphOutdated) {
            SkillService.sendGraph(player);
        }
        SkillService.sendState(player);
    }

    private record Request(ServerPlayer player, UUID id) {
        void reject(RejectReason reason, Object... arguments) {
            SkillService.send(player, new Payloads.TransactionResult(id, false, reason.translationKey(), texts(arguments)));
            if (!STATE_UNCHANGED.contains(reason)) {
                SkillService.sendState(player);
            }
        }

        void accept(String messageKey, Object... arguments) {
            SkillService.send(player, new Payloads.TransactionResult(id, true, messageKey, texts(arguments)));
            SkillService.sendState(player);
        }

        private static List<String> texts(Object... arguments) {
            return Arrays.stream(arguments).map(String::valueOf).toList();
        }
    }

    private static Optional<Request> open(IPayloadContext context, UUID requestId) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        Request request = new Request(player, requestId);
        if (!withinRateLimit(player)) {
            request.reject(RejectReason.RATE_LIMITED);
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public static void onUseBoon(Payloads.UseBoon message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && withinRateLimit(player)) {
            Triggers.fire(player, Triggers.ON_BOON_KEY, null);
        }
    }

    public static boolean withinRateLimit(ServerPlayer player) {
        return LIMITER.allow(player.getUUID(), player.serverLevel().getGameTime(), ServerConfig.MAX_REQUESTS_PER_SECOND.get());
    }

    private static boolean admitted(Request request, long expectedStateRevision) {
        long stateRevision = ModAttachments.state(request.player()).stateRevision();
        Optional<RejectReason> denied = guard(request.player()).admit(request.id(), expectedStateRevision, stateRevision, 0, 0, false);
        denied.ifPresent(reason -> request.reject(reason));
        return denied.isEmpty();
    }

    private static boolean admitted(Request request, long expectedStateRevision, int expectedGraphRevision) {
        long stateRevision = ModAttachments.state(request.player()).stateRevision();
        Optional<RejectReason> denied = guard(request.player()).admit(request.id(), expectedStateRevision, stateRevision,
                expectedGraphRevision, SkillTreeManager.revision(), true);
        denied.ifPresent(reason -> {
            request.reject(reason);
            if (reason == RejectReason.STALE_GRAPH_REVISION) {
                SkillService.sendGraph(request.player());
            }
        });
        return denied.isEmpty();
    }

    private static Optional<SkillGraphSnapshot> activeGraph(Request request) {
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (graph.isEmpty()) {
            request.reject(RejectReason.TREE_UNAVAILABLE);
        }
        return graph;
    }

    public static void onBuyPoint(Payloads.BuyPoint message, IPayloadContext context) {
        open(context, message.requestId()).filter(request -> admitted(request, message.expectedStateRevision()))
                .ifPresent(request -> buyPoints(request, message.count()));
    }

    private static void buyPoints(Request request, int requestedCount) {
        ServerPlayer player = request.player();
        PlayerSkillState state = ModAttachments.state(player);
        long carried = XpMath.balance(player.experienceLevel, player.experienceProgress);
        long balance = carried + XpWallets.stored(player);
        ProgressionService.Settings settings = ServerConfig.progression();
        int count = Math.clamp(requestedCount, 1, settings.pointLimit());
        if (!SkillEvents.allows(new SkillEvents.PointsBuying(player, count))) {
            request.reject(RejectReason.BLOCKED);
            return;
        }
        ProgressionService.BulkPurchaseResult preview = ProgressionService.purchasePoints(state.copy(), balance, settings, count);
        if (!preview.ok()) {
            request.reject(preview.reason().orElseThrow(), settings.curve().cost(state.purchasedPoints()), balance);
            return;
        }
        long shortfall = Math.max(0, preview.totalCost() - carried);
        long paid = XpWallets.take(player, shortfall);
        if (paid < shortfall) {
            PlayerExperience.set(player, carried + paid);
            request.reject(RejectReason.INSUFFICIENT_XP, settings.curve().cost(state.purchasedPoints()), carried + paid);
            return;
        }
        ProgressionService.BulkPurchaseResult result = ProgressionService.purchasePoints(state, balance, settings, count);
        PlayerExperience.set(player, carried + paid - result.totalCost());
        SkillEvents.post(new SkillEvents.PointsBought(player, result.bought(), result.totalCost()));
        if (result.bought() == 1) {
            request.accept(Payloads.TransactionResult.POINT_BOUGHT, result.totalCost(), result.newXpTotal());
        } else {
            request.accept(Payloads.TransactionResult.POINTS_BOUGHT, result.bought(), result.totalCost(), result.newXpTotal());
        }
    }

    public static void onDonate(Payloads.DonateXp message, IPayloadContext context) {
        open(context, message.requestId()).filter(request -> admitted(request, message.expectedStateRevision()))
                .ifPresent(request -> donate(request, message.amount()));
    }

    private static void donate(Request request, int amount) {
        CommunityProgress.Donation donation = CommunityTree.donate(request.player(), amount);
        if (donation.ok()) {
            request.accept("message.nostalgiaskills.donated", donation.accepted());
        } else {
            request.reject(donation.reason().orElseThrow());
        }
    }

    public static void onConfirm(Payloads.ConfirmAllocation message, IPayloadContext context) {
        open(context, message.requestId()).ifPresent(request -> activeGraph(request)
                .filter(graph -> admitted(request, message.expectedStateRevision(), message.expectedGraphRevision()))
                .ifPresent(graph -> confirm(request, graph, message.nodes())));
    }

    private static void confirm(Request request, SkillGraphSnapshot graph, List<String> nodes) {
        if (nodes.size() > ServerConfig.MAX_BATCH_SIZE.get()) {
            request.reject(RejectReason.REQUEST_TOO_LARGE, nodes.size());
            return;
        }
        if (!SkillEvents.allows(new SkillEvents.NodesLearning(request.player(), nodes))) {
            request.reject(RejectReason.BLOCKED);
            return;
        }
        AllocationService.BatchResult result = AllocationService.confirm(graph, ModAttachments.state(request.player()), nodes);
        if (!result.ok()) {
            AllocationService.Rejection why = result.rejection().orElseThrow();
            request.reject(why.reason(), why.nodeId() + (why.detail().isEmpty() ? "" : " " + why.detail()));
            return;
        }
        EffectEngine.refresh(request.player());
        SkillEvents.post(new SkillEvents.NodesLearned(request.player(), result.nodeIds()));
        Gestures.keystonesLearned(request.player(), graph, result.nodeIds());
        request.accept(Payloads.TransactionResult.ALLOCATED, result.accepted().size(), result.totalCost());
    }

    public static void onReset(Payloads.RequestReset message, IPayloadContext context) {
        open(context, message.requestId())
                .filter(request -> admitted(request, message.expectedStateRevision(), message.expectedGraphRevision()))
                .ifPresent(SkillRequests::reset);
    }

    private static void reset(Request request) {
        ServerPlayer player = request.player();
        PlayerSkillState state = ModAttachments.state(player);
        if (RespecService.preview(state).isEmpty() && state.startingRoot().isEmpty()) {
            request.reject(RejectReason.NOTHING_TO_RESET);
            return;
        }
        Optional<ItemStack> orb = orbToConsume(request, ServerConfig.ORB_REQUIRED_FOR_RESET.get());
        if (orb.isEmpty()) {
            return;
        }
        RespecService.Preview done = RespecService.resetAll(state);
        consume(orb.get());
        EffectEngine.refresh(player);
        SkillEvents.post(new SkillEvents.BuildReset(player, done.removed(), done.refund()));
        request.accept("message.nostalgiaskills.reset_done", done.refund(), done.removed().size());
    }

    public static void onRefund(Payloads.RequestRefund message, IPayloadContext context) {
        open(context, message.requestId()).ifPresent(request -> activeGraph(request)
                .filter(graph -> admitted(request, message.expectedStateRevision(), message.expectedGraphRevision()))
                .ifPresent(graph -> refund(request, graph, message.nodeId())));
    }

    private static void refund(Request request, SkillGraphSnapshot graph, String nodeId) {
        PlayerSkillState state = ModAttachments.state(request.player());
        boolean enabled = ServerConfig.ALLOW_NODE_REFUNDS.get();
        RefundService.Plan plan = RefundService.plan(graph, state, nodeId, enabled);
        if (!plan.ok()) {
            request.reject(plan.reason().orElseThrow(), nodeId);
            return;
        }
        if (!SkillEvents.allows(new SkillEvents.RefundRequested(request.player(), nodeId, plan.removed(), plan.refund()))) {
            request.reject(RejectReason.BLOCKED);
            return;
        }
        Optional<ItemStack> orb = orbToConsume(request, ServerConfig.ORB_REQUIRED_FOR_NODE_REFUND.get());
        if (orb.isEmpty()) {
            return;
        }
        RefundService.Plan done = RefundService.apply(graph, state, nodeId, enabled);
        if (!done.ok()) {
            request.reject(done.reason().orElseThrow(), nodeId);
            return;
        }
        consume(orb.get());
        EffectEngine.refresh(request.player());
        SkillEvents.post(new SkillEvents.NodesRefunded(request.player(), done.removed(), done.refund()));
        request.accept("message.nostalgiaskills.refunded", done.refund(), done.removed().size());
    }

    public static void onToggle(Payloads.ToggleNode message, IPayloadContext context) {
        open(context, message.requestId()).ifPresent(request -> activeGraph(request)
                .filter(graph -> admitted(request, message.expectedStateRevision(), message.expectedGraphRevision()))
                .ifPresent(graph -> toggle(request, graph, message.nodeId())));
    }

    private static void toggle(Request request, SkillGraphSnapshot graph, String nodeId) {
        PlayerSkillState state = ModAttachments.state(request.player());
        Optional<RejectReason> rejection = ToggleService.toggle(graph, state, nodeId);
        if (rejection.isPresent()) {
            request.reject(rejection.get(), nodeId);
            return;
        }
        EffectEngine.refresh(request.player());
        request.accept("message.nostalgiaskills.skill_" + (state.isDisabled(nodeId) ? "disabled" : "enabled"));
    }

    private static Optional<ItemStack> orbToConsume(Request request, boolean orbRequired) {
        if (!SkillService.orbNeeded(orbRequired) || request.player().getAbilities().instabuild) {
            return Optional.of(ItemStack.EMPTY);
        }
        ItemStack orb = findOrb(request.player());
        if (orb.isEmpty()) {
            request.reject(RejectReason.MISSING_RESET_ITEM);
            return Optional.empty();
        }
        return Optional.of(orb);
    }

    private static void consume(ItemStack orb) {
        if (!orb.isEmpty()) {
            orb.shrink(1);
        }
    }

    private static ItemStack findOrb(ServerPlayer player) {
        List<ItemStack> candidates = new ArrayList<>(List.of(player.getMainHandItem(), player.getOffhandItem()));
        candidates.addAll(player.getInventory().items);
        return candidates.stream().filter(stack -> stack.is(ModItems.ORB_OF_RECONFIGURATION.get())).findFirst()
                .orElse(ItemStack.EMPTY);
    }
}
