package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.data.WorldSkillData;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.graph.GraphCodec;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.ProgressionService;
import dev.lesteoff.celestialskilltree.progression.Reconciler;
import dev.lesteoff.celestialskilltree.progression.SignService;
import dev.lesteoff.celestialskilltree.progression.WorldClock;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SkillService {
    private static byte[] encodedGraph;
    private static String encodedFingerprint = "";

    private SkillService() {
    }

    public static Payloads.Economy economy(PlayerSkillState state) {
        ProgressionService.Settings settings = ServerConfig.progression();
        return new Payloads.Economy(settings.curve().cost(state.purchasedPoints()), settings.pointLimit(), settings.xpPurchaseEnabled(),
                orbNeeded(ServerConfig.ORB_REQUIRED_FOR_RESET.get()), ServerConfig.ALLOW_NODE_REFUNDS.get(),
                orbNeeded(ServerConfig.ORB_REQUIRED_FOR_NODE_REFUND.get()));
    }

    static boolean orbNeeded(boolean configured) {
        return configured && !SkyEvents.isEclipse();
    }

    private static Payloads.WorldRules worldRules(ServerPlayer player) {
        List<Payloads.TierInfo> tiers = CommunityTree.stars().stream().map(SkillService::tierInfo).toList();
        List<Payloads.SignInfo> signs = SkillTreeManager.sortedSignIds().stream().limit(Payloads.WorldRules.MAX_SIGNS)
                .flatMap(signId -> SkillTreeManager.sign(signId).stream())
                .map(sign -> signInfo(player, sign)).toList();
        return new Payloads.WorldRules(SkyEvents.current(), tiers, community(player), signs);
    }

    private static Payloads.TierInfo tierInfo(CommunityTier star) {
        return new Payloads.TierInfo(star.id(), star.xpRequired(), star.parent().orElse(""), star.exclusive().orElse(""), star.icon(),
                new Payloads.Position((float) star.position().x(), (float) star.position().y()));
    }

    static Payloads.Community community(ServerPlayer player) {
        List<CommunityTier> stars = CommunityTree.stars();
        Map<String, Long> progress = CommunityTree.progress(player.server);
        Map<UUID, CommunityProgress.Patron> patrons = CommunityTree.patrons(player.server);
        List<Payloads.StarProgress> starProgress = stars.stream()
                .map(star -> starProgress(stars, progress, patrons.values(), star)).toList();
        List<UUID> ranking = CommunityProgress.ranking(patrons);
        List<Payloads.Patron> top = ranking.stream().limit(Payloads.Community.MAX_RANKING).map(patrons::get)
                .map(patron -> new Payloads.Patron(patron.name(), patron.total())).toList();
        return new Payloads.Community(starProgress, top, ranking.indexOf(player.getUUID()) + 1, total(patrons, player.getUUID()),
                total(patrons, CommunityProgress.ANONYMOUS), ranking.size());
    }

    private static long total(Map<UUID, CommunityProgress.Patron> patrons, UUID id) {
        CommunityProgress.Patron patron = patrons.get(id);
        return patron == null ? 0 : patron.total();
    }

    private static Payloads.StarProgress starProgress(List<CommunityTier> stars, Map<String, Long> progress,
                                                      Collection<CommunityProgress.Patron> patrons, CommunityTier star) {
        List<Payloads.Patron> donors = CommunityProgress.topDonors(patrons, star.id(), Payloads.StarProgress.MAX_PATRONS).stream()
                .map(patron -> new Payloads.Patron(patron.name(), patron.gaveTo(star.id()))).toList();
        return new Payloads.StarProgress(star.id(), CommunityProgress.donated(star, progress),
                CommunityProgress.status(stars, progress, star), donors);
    }

    private static Payloads.SignInfo signInfo(ServerPlayer player, SkillSign sign) {
        List<Payloads.SignBonus> bonuses = sign.bonuses().stream().limit(Payloads.SignInfo.MAX_BONUSES)
                .map(bonus -> new Payloads.SignBonus(bonus.descriptionKey(), isActive(player, bonus))).toList();
        return new Payloads.SignInfo(sign.id(), sign.descriptionKey(), bonuses);
    }

    private static boolean isActive(ServerPlayer player, SkillSign.Bonus bonus) {
        return bonus.effects().stream().allMatch(effect -> !(effect instanceof SkillEffect.ConditionalAttribute conditional)
                || EffectEngine.holds(player, conditional.condition(), null));
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendState(ServerPlayer player) {
        PlayerSkillState state = ModAttachments.state(player);
        Payloads.Economy economy = economy(state).withWalletXp(XpWallets.stored(player));
        send(player, new Payloads.StateSync(SkillTreeManager.revision(), state.forClient(), economy,
                ServerConfig.progression().curve(), worldRules(player)));
    }

    public static void sendGraph(ServerPlayer player) {
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

    public static void resync(ServerPlayer player) {
        reconcileAndRefresh(player);
        sendGraph(player);
        sendState(player);
    }

    public static void reconcileAndRefresh(ServerPlayer player) {
        SkillTreeManager.graph().ifPresent(graph -> {
            PlayerSkillState state = ModAttachments.state(player);
            if (!graph.fingerprint().equals(state.reconciledFingerprint())) {
                Reconciler.Report report = Reconciler.reconcile(state, graph, SkillTreeManager.migrations());
                if (report.changed()) {
                    CelestialSkillTree.LOGGER.info("Reconciled {}: migrated {}, removed {}, refunded {} point(s)",
                            player.getScoreboardName(), report.migrated().size(), report.removed().size(), report.refunded());
                }
            }
        });
        assignSign(player);
        Discoveries.syncFromAdvancements(player);
        EffectEngine.refresh(player);
    }

    private static void assignSign(ServerPlayer player) {
        long worldDay = WorldClock.day(player.serverLevel().getDayTime());
        SignService.assignIfMissing(ModAttachments.state(player), SkillTreeManager.sortedSignIds(), worldDay, player.getUUID())
                .flatMap(SkillTreeManager::sign)
                .ifPresent(sign -> player.sendSystemMessage(Component.translatable("message.celestialskilltree.sign_assigned",
                        Component.translatable(SkillSign.nameKey(sign.id())), Payloads.SignInfo.describe(sign.descriptionKeys()))));
    }
}
