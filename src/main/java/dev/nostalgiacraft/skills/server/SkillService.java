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
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.Reconciler;
import dev.nostalgiacraft.skills.progression.SignService;
import dev.nostalgiacraft.skills.progression.WorldClock;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

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
        List<Payloads.TierInfo> tiers = SkillTreeManager.communityTiers().stream().limit(Payloads.WorldRules.MAX_TIERS)
                .map(tier -> new Payloads.TierInfo(tier.id(), tier.xpRequired())).toList();
        List<Payloads.SignInfo> signs = SkillTreeManager.sortedSignIds().stream().limit(Payloads.WorldRules.MAX_SIGNS)
                .flatMap(signId -> SkillTreeManager.sign(signId).stream())
                .map(sign -> new Payloads.SignInfo(sign.id(), sign.descriptionKey())).toList();
        return new Payloads.WorldRules(SkyEvents.current(), WorldSkillData.of(player.server).communityXp(), tiers, signs);
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
        long worldDay = WorldClock.day(player.serverLevel().getDayTime());
        SignService.assignIfMissing(ModAttachments.state(player), SkillTreeManager.sortedSignIds(), worldDay, player.getUUID())
                .flatMap(SkillTreeManager::sign)
                .ifPresent(sign -> player.sendSystemMessage(Component.translatable("message.nostalgiaskills.sign_assigned",
                        Component.translatable(SkillSign.nameKey(sign.id())), Component.translatable(sign.descriptionKey()))));
    }
}
