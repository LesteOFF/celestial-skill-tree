package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.Reconciler;
import dev.nostalgiacraft.skills.progression.RefundService;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RequestGuard;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.XpMath;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative handling of every mutating intent. Each handler runs on the server thread and performs
 * validation, debit/credit, allocation, effect reconciliation and revision bump as one uninterrupted step.
 */
public final class SkillService {
    private static final Map<UUID, RequestGuard> GUARDS = new HashMap<>();
    private static final RateLimiter LIMITER = new RateLimiter();
    private static byte[] encodedGraph;
    private static String encodedFingerprint = "";

    private SkillService() {
    }

    private static RequestGuard guard(ServerPlayer p) {
        return GUARDS.computeIfAbsent(p.getUUID(), k -> new RequestGuard());
    }

    public static void forget(ServerPlayer p) {
        GUARDS.remove(p.getUUID());
        LIMITER.forget(p.getUUID());
    }

    // ------------------------------------------------------------------ sync

    public static Payloads.Economy economy(PlayerSkillState s) {
        ProgressionService.Settings st = ServerConfig.progression();
        return new Payloads.Economy(st.curve().cost(s.purchasedPoints()), st.pointLimit(), st.xpPurchaseEnabled(),
                ServerConfig.ORB_REQUIRED_FOR_RESET.get(), ServerConfig.ALLOW_NODE_REFUNDS.get(),
                ServerConfig.ORB_REQUIRED_FOR_NODE_REFUND.get());
    }

    /** Sends only when the client negotiated the channel (mock/test players and foreign clients are skipped). */
    static void send(ServerPlayer p, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (p.connection != null && p.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    public static void sendState(ServerPlayer p) {
        PlayerSkillState s = ModAttachments.state(p);
        send(p, new Payloads.StateSync(SkillTreeManager.revision(), s.copy(), economy(s)));
    }

    public static synchronized void sendGraph(ServerPlayer p) {
        Optional<SkillGraphSnapshot> g = SkillTreeManager.graph();
        if (g.isEmpty()) {
            return;
        }
        if (encodedGraph == null || !encodedFingerprint.equals(g.get().fingerprint())) {
            encodedGraph = GraphCodec.encode(g.get());
            encodedFingerprint = g.get().fingerprint();
        }
        send(p, new Payloads.GraphSync(SkillTreeManager.revision(), encodedGraph));
    }

    /** Login, respawn, reload: reconcile the saved build against the active graph, then rebuild effects. */
    public static void reconcileAndRefresh(ServerPlayer p) {
        SkillTreeManager.graph().ifPresent(g -> {
            PlayerSkillState s = ModAttachments.state(p);
            if (!g.fingerprint().equals(s.reconciledFingerprint())) {
                Reconciler.Report r = Reconciler.reconcile(s, g, SkillTreeManager.migrations());
                if (r.changed()) {
                    NostalgiaSkills.LOGGER.info("Reconciled {}: migrated {}, removed {}, refunded {} point(s)",
                            p.getScoreboardName(), r.migrated().size(), r.removed().size(), r.refunded());
                }
            }
        });
        EffectEngine.refresh(p);
    }

    public static void onRequestSync(Payloads.RequestSync msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p)) {
            return;
        }
        if (!LIMITER.allow(p.getUUID(), p.serverLevel().getGameTime(), ServerConfig.MAX_REQUESTS_PER_SECOND.get())) {
            return;
        }
        SkillTreeManager.graph().ifPresent(g -> {
            if (!g.fingerprint().equals(msg.knownFingerprint())) {
                sendGraph(p);
            }
        });
        sendState(p);
    }

    // ------------------------------------------------------------------ intents

    private static void reply(ServerPlayer p, UUID req, RejectReason reason, String detail) {
        send(p, new Payloads.TransactionResult(req, false, reason.translationKey(), detail));
        sendState(p);
    }

    private static void ok(ServerPlayer p, UUID req, String key, String detail) {
        send(p, new Payloads.TransactionResult(req, true, key, detail));
        sendState(p);
    }

    private static boolean limited(ServerPlayer p, UUID req) {
        if (!LIMITER.allow(p.getUUID(), p.serverLevel().getGameTime(), ServerConfig.MAX_REQUESTS_PER_SECOND.get())) {
            reply(p, req, RejectReason.RATE_LIMITED, "");
            return true;
        }
        return false;
    }

    public static void onBuyPoint(Payloads.BuyPoint msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p) || limited(p, msg.requestId())) {
            return;
        }
        PlayerSkillState s = ModAttachments.state(p);
        Optional<RejectReason> denied = guard(p).admit(msg.requestId(), msg.expectedStateRevision(), s.stateRevision(),
                0, 0, false);
        if (denied.isPresent()) {
            reply(p, msg.requestId(), denied.get(), "");
            return;
        }
        long balance = XpMath.balance(p.experienceLevel, p.experienceProgress);
        ProgressionService.PurchaseResult r = ProgressionService.purchasePoint(s, balance, ServerConfig.progression());
        if (!r.ok()) {
            reply(p, msg.requestId(), r.reason().orElseThrow(), r.cost() + "/" + balance);
            return;
        }
        setExperience(p, r.newXpTotal());
        ok(p, msg.requestId(), "message.nostalgiaskills.point_bought", r.cost() + "/" + r.newXpTotal());
    }

    /** Rewrites level, bar progress and total experience together from one exact raw-XP total. */
    public static void setExperience(ServerPlayer p, long total) {
        XpMath.LevelState st = XpMath.fromTotal(Math.max(0, total));
        p.setExperienceLevels(st.level());
        long inBar = total - XpMath.totalForLevel(st.level());
        p.setExperiencePoints((int) inBar);
        p.totalExperience = st.total();
    }

    public static void onConfirm(Payloads.ConfirmAllocation msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p) || limited(p, msg.requestId())) {
            return;
        }
        Optional<SkillGraphSnapshot> g = SkillTreeManager.graph();
        PlayerSkillState s = ModAttachments.state(p);
        if (g.isEmpty()) {
            reply(p, msg.requestId(), RejectReason.TREE_UNAVAILABLE, "");
            return;
        }
        Optional<RejectReason> denied = guard(p).admit(msg.requestId(), msg.expectedStateRevision(), s.stateRevision(),
                msg.expectedGraphRevision(), SkillTreeManager.revision(), true);
        if (denied.isPresent()) {
            reply(p, msg.requestId(), denied.get(), "");
            if (denied.get() == RejectReason.STALE_GRAPH_REVISION) {
                sendGraph(p);
            }
            return;
        }
        if (msg.nodes().size() > ServerConfig.MAX_BATCH_SIZE.get()) {
            reply(p, msg.requestId(), RejectReason.REQUEST_TOO_LARGE, String.valueOf(msg.nodes().size()));
            return;
        }
        AllocationService.BatchResult r = AllocationService.confirm(g.get(), s, msg.nodes());
        if (!r.ok()) {
            AllocationService.Rejection why = r.rejection().orElseThrow();
            reply(p, msg.requestId(), why.reason(), why.nodeId() + (why.detail().isEmpty() ? "" : " " + why.detail()));
            return;
        }
        EffectEngine.refresh(p);
        ok(p, msg.requestId(), "message.nostalgiaskills.allocated", r.accepted().size() + "/" + r.totalCost());
    }

    public static void onReset(Payloads.RequestReset msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p) || limited(p, msg.requestId())) {
            return;
        }
        PlayerSkillState s = ModAttachments.state(p);
        Optional<RejectReason> denied = guard(p).admit(msg.requestId(), msg.expectedStateRevision(), s.stateRevision(),
                msg.expectedGraphRevision(), SkillTreeManager.revision(), true);
        if (denied.isPresent()) {
            reply(p, msg.requestId(), denied.get(), "");
            return;
        }
        if (RespecService.preview(s).isEmpty() && s.startingRoot().isEmpty()) {
            reply(p, msg.requestId(), RejectReason.NOTHING_TO_RESET, "");
            return;
        }
        ItemStack orb = ItemStack.EMPTY;
        if (ServerConfig.ORB_REQUIRED_FOR_RESET.get() && !p.getAbilities().instabuild) {
            orb = findOrb(p);
            if (orb.isEmpty()) {
                reply(p, msg.requestId(), RejectReason.MISSING_RESET_ITEM, "");
                return;
            }
        }
        RespecService.Preview done = RespecService.resetAll(s);
        if (!orb.isEmpty()) {
            orb.shrink(1);
        }
        EffectEngine.refresh(p);
        ok(p, msg.requestId(), "message.nostalgiaskills.reset_done", done.refund() + "/" + done.removed().size());
    }

    /**
     * Single-skill refund. One authoritative step: plan against the live state, consume the orb only when the plan
     * holds, refund the recorded costs of the node and its dependent chain, rebuild effects, bump and sync.
     */
    public static void onRefund(Payloads.RequestRefund msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p) || limited(p, msg.requestId())) {
            return;
        }
        Optional<SkillGraphSnapshot> g = SkillTreeManager.graph();
        PlayerSkillState s = ModAttachments.state(p);
        if (g.isEmpty()) {
            reply(p, msg.requestId(), RejectReason.TREE_UNAVAILABLE, "");
            return;
        }
        Optional<RejectReason> denied = guard(p).admit(msg.requestId(), msg.expectedStateRevision(), s.stateRevision(),
                msg.expectedGraphRevision(), SkillTreeManager.revision(), true);
        if (denied.isPresent()) {
            reply(p, msg.requestId(), denied.get(), "");
            if (denied.get() == RejectReason.STALE_GRAPH_REVISION) {
                sendGraph(p);
            }
            return;
        }
        boolean enabled = ServerConfig.ALLOW_NODE_REFUNDS.get();
        RefundService.Plan plan = RefundService.plan(g.get(), s, msg.nodeId(), enabled);
        if (!plan.ok()) {
            reply(p, msg.requestId(), plan.reason().orElseThrow(), msg.nodeId());
            return;
        }
        ItemStack orb = ItemStack.EMPTY;
        if (ServerConfig.ORB_REQUIRED_FOR_NODE_REFUND.get() && !p.getAbilities().instabuild) {
            orb = findOrb(p);
            if (orb.isEmpty()) {
                reply(p, msg.requestId(), RejectReason.MISSING_RESET_ITEM, "");
                return;
            }
        }
        RefundService.Plan done = RefundService.apply(g.get(), s, msg.nodeId(), enabled);
        if (!done.ok()) {
            reply(p, msg.requestId(), done.reason().orElseThrow(), msg.nodeId());
            return;
        }
        if (!orb.isEmpty()) {
            orb.shrink(1);
        }
        EffectEngine.refresh(p);
        ok(p, msg.requestId(), "message.nostalgiaskills.refunded", done.refund() + "/" + done.removed().size());
    }

    private static ItemStack findOrb(ServerPlayer p) {
        for (ItemStack st : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (st.is(ModItems.ORB_OF_RECONFIGURATION.get())) {
                return st;
            }
        }
        for (ItemStack st : p.getInventory().items) {
            if (st.is(ModItems.ORB_OF_RECONFIGURATION.get())) {
                return st;
            }
        }
        return ItemStack.EMPTY;
    }
}
