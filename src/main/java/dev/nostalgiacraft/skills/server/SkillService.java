package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillSign;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.CommunityProgress;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.Reconciler;
import dev.nostalgiacraft.skills.progression.RefundService;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RequestGuard;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.SignService;
import dev.nostalgiacraft.skills.progression.XpMath;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SkillService {
    private static final Map<UUID, RequestGuard> GUARDS = new HashMap<>();
    private static final RateLimiter LIMITER = new RateLimiter();
    private static final long TICKS_PER_DAY = 24_000;
    private static byte[] encodedGraph;
    private static String encodedFingerprint = "";

    private SkillService() {
    }

    private static RequestGuard guard(ServerPlayer player) {
        return GUARDS.computeIfAbsent(player.getUUID(), id -> new RequestGuard());
    }

    public static void forget(ServerPlayer player) {
        GUARDS.remove(player.getUUID());
        LIMITER.forget(player.getUUID());
    }

    public static Payloads.Economy economy(PlayerSkillState state) {
        ProgressionService.Settings settings = ServerConfig.progression();
        return new Payloads.Economy(settings.curve().cost(state.purchasedPoints()), settings.pointLimit(), settings.xpPurchaseEnabled(),
                orbNeeded(ServerConfig.ORB_REQUIRED_FOR_RESET.get()), ServerConfig.ALLOW_NODE_REFUNDS.get(),
                orbNeeded(ServerConfig.ORB_REQUIRED_FOR_NODE_REFUND.get()));
    }

    private static boolean orbNeeded(boolean configured) {
        return configured && !Eclipse.isActive();
    }

    private static Payloads.WorldRules worldRules(ServerPlayer player) {
        List<Payloads.TierInfo> tiers = SkillTreeManager.communityTiers().stream().limit(Payloads.WorldRules.MAX_TIERS)
                .map(tier -> new Payloads.TierInfo(tier.id(), tier.xpRequired())).toList();
        List<String> signIds = SkillTreeManager.sortedSignIds().stream().limit(Payloads.WorldRules.MAX_SIGNS).toList();
        return new Payloads.WorldRules(Eclipse.isActive(), WorldSkillData.of(player.server).communityXp(), tiers, signIds);
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendState(ServerPlayer player) {
        PlayerSkillState state = ModAttachments.state(player);
        send(player, new Payloads.StateSync(SkillTreeManager.revision(), state.copy(), economy(state),
                ServerConfig.progression().curve(), worldRules(player)));
    }

    public static synchronized void sendGraph(ServerPlayer player) {
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (graph.isEmpty()) {
            return;
        }
        if (encodedGraph == null || !encodedFingerprint.equals(graph.get().fingerprint())) {
            encodedGraph = GraphCodec.encode(graph.get());
            encodedFingerprint = graph.get().fingerprint();
        }
        send(player, new Payloads.GraphSync(SkillTreeManager.revision(), encodedGraph));
    }

    public static void reconcileAndRefresh(ServerPlayer player) {
        SkillTreeManager.graph().ifPresent(graph -> {
            PlayerSkillState state = ModAttachments.state(player);
            if (!graph.fingerprint().equals(state.reconciledFingerprint())) {
                Reconciler.Report report = Reconciler.reconcile(state, graph, SkillTreeManager.migrations());
                if (report.changed()) {
                    NostalgiaSkills.LOGGER.info("Reconciled {}: migrated {}, removed {}, refunded {} point(s)",
                            player.getScoreboardName(), report.migrated().size(), report.removed().size(), report.refunded());
                }
            }
        });
        assignSign(player);
        Discoveries.syncFromAdvancements(player);
        EffectEngine.refresh(player);
    }

    private static void assignSign(ServerPlayer player) {
        long worldDay = player.serverLevel().getDayTime() / TICKS_PER_DAY;
        SignService.assignIfMissing(ModAttachments.state(player), SkillTreeManager.sortedSignIds(), worldDay)
                .ifPresent(signId -> player.sendSystemMessage(Component.translatable("message.nostalgiaskills.sign_assigned",
                        Component.translatable(SkillSign.nameKey(signId)), Component.translatable(SkillSign.descriptionKey(signId)))));
    }

    public static void onRequestSync(Payloads.RequestSync requestSync, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !withinRateLimit(player)) {
            return;
        }
        boolean graphOutdated = SkillTreeManager.graph()
                .filter(graph -> !graph.fingerprint().equals(requestSync.knownFingerprint())).isPresent();
        if (graphOutdated) {
            sendGraph(player);
        }
        sendState(player);
    }

    private record Request(ServerPlayer player, UUID id) {
        void reject(RejectReason reason, String detail) {
            send(player, new Payloads.TransactionResult(id, false, reason.translationKey(), detail));
            sendState(player);
        }

        void accept(String messageKey, String detail) {
            send(player, new Payloads.TransactionResult(id, true, messageKey, detail));
            sendState(player);
        }
    }

    private static Optional<Request> open(IPayloadContext context, UUID requestId) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        Request request = new Request(player, requestId);
        if (!withinRateLimit(player)) {
            request.reject(RejectReason.RATE_LIMITED, "");
            return Optional.empty();
        }
        return Optional.of(request);
    }

    private static boolean withinRateLimit(ServerPlayer player) {
        return LIMITER.allow(player.getUUID(), player.serverLevel().getGameTime(), ServerConfig.MAX_REQUESTS_PER_SECOND.get());
    }

    private static boolean admitted(Request request, long expectedStateRevision) {
        long stateRevision = ModAttachments.state(request.player()).stateRevision();
        Optional<RejectReason> denied = guard(request.player()).admit(request.id(), expectedStateRevision, stateRevision, 0, 0, false);
        denied.ifPresent(reason -> request.reject(reason, ""));
        return denied.isEmpty();
    }

    private static boolean admitted(Request request, long expectedStateRevision, int expectedGraphRevision) {
        long stateRevision = ModAttachments.state(request.player()).stateRevision();
        Optional<RejectReason> denied = guard(request.player()).admit(request.id(), expectedStateRevision, stateRevision,
                expectedGraphRevision, SkillTreeManager.revision(), true);
        denied.ifPresent(reason -> {
            request.reject(reason, "");
            if (reason == RejectReason.STALE_GRAPH_REVISION) {
                sendGraph(request.player());
            }
        });
        return denied.isEmpty();
    }

    private static Optional<SkillGraphSnapshot> activeGraph(Request request) {
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (graph.isEmpty()) {
            request.reject(RejectReason.TREE_UNAVAILABLE, "");
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
        long balance = XpMath.balance(player.experienceLevel, player.experienceProgress);
        ProgressionService.Settings settings = ServerConfig.progression();
        int count = Math.clamp(requestedCount, 1, settings.pointLimit());
        ProgressionService.BulkPurchaseResult result = ProgressionService.purchasePoints(state, balance, settings, count);
        if (!result.ok()) {
            request.reject(result.reason().orElseThrow(), settings.curve().cost(state.purchasedPoints()) + "/" + balance);
            return;
        }
        setExperience(player, result.newXpTotal());
        if (result.bought() == 1) {
            request.accept("message.nostalgiaskills.point_bought", result.totalCost() + "/" + result.newXpTotal());
        } else {
            request.accept("message.nostalgiaskills.points_bought",
                    result.bought() + "/" + result.totalCost() + "/" + result.newXpTotal());
        }
    }

    public static void setExperience(ServerPlayer player, long total) {
        XpMath.LevelState levelState = XpMath.fromTotal(Math.max(0, total));
        player.setExperienceLevels(levelState.level());
        player.setExperiencePoints((int) (total - XpMath.totalForLevel(levelState.level())));
        player.totalExperience = levelState.total();
    }

    public static void onDonate(Payloads.DonateXp message, IPayloadContext context) {
        open(context, message.requestId()).filter(request -> admitted(request, message.expectedStateRevision()))
                .ifPresent(request -> donate(request, message.amount()));
    }

    private static void donate(Request request, int amount) {
        CommunityProgress.Donation donation = CommunityTree.donate(request.player(), amount);
        if (donation.ok()) {
            request.accept("message.nostalgiaskills.donated", String.valueOf(donation.accepted()));
        } else {
            request.reject(donation.reason().orElseThrow(), "");
        }
    }

    public static void onConfirm(Payloads.ConfirmAllocation message, IPayloadContext context) {
        open(context, message.requestId()).ifPresent(request -> activeGraph(request)
                .filter(graph -> admitted(request, message.expectedStateRevision(), message.expectedGraphRevision()))
                .ifPresent(graph -> confirm(request, graph, message.nodes())));
    }

    private static void confirm(Request request, SkillGraphSnapshot graph, List<String> nodes) {
        if (nodes.size() > ServerConfig.MAX_BATCH_SIZE.get()) {
            request.reject(RejectReason.REQUEST_TOO_LARGE, String.valueOf(nodes.size()));
            return;
        }
        AllocationService.BatchResult result = AllocationService.confirm(graph, ModAttachments.state(request.player()), nodes);
        if (!result.ok()) {
            AllocationService.Rejection why = result.rejection().orElseThrow();
            request.reject(why.reason(), why.nodeId() + (why.detail().isEmpty() ? "" : " " + why.detail()));
            return;
        }
        EffectEngine.refresh(request.player());
        request.accept("message.nostalgiaskills.allocated", result.accepted().size() + "/" + result.totalCost());
    }

    public static void onReset(Payloads.RequestReset message, IPayloadContext context) {
        open(context, message.requestId())
                .filter(request -> admitted(request, message.expectedStateRevision(), message.expectedGraphRevision()))
                .ifPresent(SkillService::reset);
    }

    private static void reset(Request request) {
        ServerPlayer player = request.player();
        PlayerSkillState state = ModAttachments.state(player);
        if (RespecService.preview(state).isEmpty() && state.startingRoot().isEmpty()) {
            request.reject(RejectReason.NOTHING_TO_RESET, "");
            return;
        }
        Optional<ItemStack> orb = orbToConsume(request, ServerConfig.ORB_REQUIRED_FOR_RESET.get());
        if (orb.isEmpty()) {
            return;
        }
        RespecService.Preview done = RespecService.resetAll(state);
        consume(orb.get());
        EffectEngine.refresh(player);
        request.accept("message.nostalgiaskills.reset_done", done.refund() + "/" + done.removed().size());
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
        request.accept("message.nostalgiaskills.refunded", done.refund() + "/" + done.removed().size());
    }

    private static Optional<ItemStack> orbToConsume(Request request, boolean orbRequired) {
        if (!orbNeeded(orbRequired) || request.player().getAbilities().instabuild) {
            return Optional.of(ItemStack.EMPTY);
        }
        ItemStack orb = findOrb(request.player());
        if (orb.isEmpty()) {
            request.reject(RejectReason.MISSING_RESET_ITEM, "");
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
