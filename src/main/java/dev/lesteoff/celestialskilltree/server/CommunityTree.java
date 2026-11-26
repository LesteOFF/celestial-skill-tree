package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.data.WorldSkillData;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress;
import dev.lesteoff.celestialskilltree.progression.RejectReason;
import dev.lesteoff.celestialskilltree.progression.XpMath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class CommunityTree {
    private static final String PATRON_MARK = "✦ ";

    private CommunityTree() {
    }

    public static List<CommunityTier> stars() {
        return SkillTreeManager.communityTiers();
    }

    private static WorldSkillData data(MinecraftServer server) {
        WorldSkillData data = WorldSkillData.of(server);
        if (!stars().isEmpty()) {
            migrateLegacyProgress(data);
        }
        return data;
    }

    private static void migrateLegacyProgress(WorldSkillData data) {
        long legacy = data.takeLegacyCommunityXp();
        if (legacy > 0) {
            CommunityProgress.Poured poured = CommunityProgress.pour(stars(), data.communityStars(), legacy);
            data.setCommunityStars(poured.progress());
            poured.given().forEach((starId, xp) -> data.credit(CommunityProgress.ANONYMOUS, "", starId, xp));
        }
    }

    public static Map<String, Long> progress(MinecraftServer server) {
        return data(server).communityStars();
    }

    public static Map<UUID, CommunityProgress.Patron> patrons(MinecraftServer server) {
        return data(server).patrons();
    }

    public static Set<String> litStars(MinecraftServer server) {
        return CommunityProgress.lit(stars(), progress(server));
    }

    public static CommunityProgress.Donation donate(ServerPlayer player, String starId, long requested) {
        if (!ServerConfig.COMMUNITY_ENABLED.get()) {
            return new CommunityProgress.Donation(0, Optional.of(RejectReason.PROGRESSION_DISABLED));
        }
        WorldSkillData data = data(player.server);
        long balance = XpMath.balance(player.experienceLevel, player.experienceProgress);
        CommunityProgress.Donation donation = CommunityProgress.donate(stars(), data.communityStars(), starId, balance, requested);
        if (donation.ok()) {
            PlayerExperience.set(player, balance - donation.accepted());
            data.credit(player.getUUID(), player.getGameProfile().getName(), starId, donation.accepted());
            setProgress(player.server, added(data.communityStars(), starId, donation.accepted()));
            refreshPatronMarks(player.server);
            Milestones.reach(player, Milestones.DONATION);
        }
        return donation;
    }

    public static long remaining(MinecraftServer server) {
        boolean open = server != null && ServerConfig.COMMUNITY_ENABLED.get();
        return open ? CommunityProgress.pour(stars(), progress(server), Long.MAX_VALUE).accepted() : 0;
    }

    public static long contribute(MinecraftServer server, long experience) {
        if (remaining(server) == 0) {
            return 0;
        }
        WorldSkillData data = data(server);
        CommunityProgress.Poured poured = CommunityProgress.pour(stars(), data.communityStars(), experience);
        poured.given().forEach((starId, xp) -> data.credit(CommunityProgress.ANONYMOUS, "", starId, xp));
        setProgress(server, poured.progress());
        return poured.accepted();
    }

    public static void setStar(MinecraftServer server, String starId, long xp) {
        setProgress(server, with(progress(server), starId, Math.max(0, xp)));
    }

    public static void setProgress(MinecraftServer server, Map<String, Long> progress) {
        WorldSkillData data = data(server);
        Set<String> litBefore = CommunityProgress.lit(stars(), data.communityStars());
        data.setCommunityStars(progress);
        Set<String> litNow = CommunityProgress.lit(stars(), progress);
        stars().stream().filter(star -> litNow.contains(star.id()) && !litBefore.contains(star.id()))
                .forEach(star -> announce(server, star));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (litNow.equals(litBefore)) {
                SkillService.send(player, new Payloads.CommunityProgressSync(SkillService.community(player)));
            } else {
                EffectEngine.refresh(player);
                SkillService.sendState(player);
            }
        }
    }

    private static void announce(MinecraftServer server, CommunityTier star) {
        SkillEvents.post(new SkillEvents.CommunityTierUnlocked(server, star.id()));
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.celestialskilltree.community_unlocked",
                Component.translatable(CommunityTier.nameKey(star.id())),
                Component.translatable(CommunityTier.descriptionKey(star.id()))).withStyle(ChatFormatting.GOLD), false);
    }

    private static Map<String, Long> added(Map<String, Long> progress, String starId, long xp) {
        return with(progress, starId, progress.getOrDefault(starId, 0L) + xp);
    }

    private static Map<String, Long> with(Map<String, Long> progress, String starId, long xp) {
        Map<String, Long> updated = new HashMap<>(progress);
        updated.put(starId, xp);
        return updated;
    }

    public static Optional<UUID> topPatron(MinecraftServer server) {
        return CommunityProgress.ranking(patrons(server)).stream().findFirst();
    }

    private static void refreshPatronMarks(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(ServerPlayer::refreshTabListName);
    }

    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || topPatron(player.server).filter(player.getUUID()::equals).isEmpty()) {
            return;
        }
        Component name = event.getDisplayName() == null ? player.getDisplayName() : event.getDisplayName();
        event.setDisplayName(Component.literal(PATRON_MARK).withStyle(ChatFormatting.GOLD).append(name));
    }
}
