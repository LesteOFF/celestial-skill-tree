package dev.lesteoff.celestialskilltree.server;

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

import java.util.List;
import java.util.Optional;

public final class CommunityTree {
    private CommunityTree() {
    }

    static List<Long> thresholds() {
        return SkillTreeManager.communityTiers().stream().map(CommunityTier::xpRequired).toList();
    }

    public static CommunityProgress.Donation donate(ServerPlayer player, long requested) {
        if (!ServerConfig.COMMUNITY_ENABLED.get()) {
            return new CommunityProgress.Donation(0, Optional.of(RejectReason.PROGRESSION_DISABLED));
        }
        WorldSkillData data = WorldSkillData.of(player.server);
        long balance = XpMath.balance(player.experienceLevel, player.experienceProgress);
        CommunityProgress.Donation donation = CommunityProgress.donate(thresholds(), data.communityXp(), balance, requested);
        if (donation.ok()) {
            PlayerExperience.set(player, balance - donation.accepted());
            setProgress(player.server, data.communityXp() + donation.accepted());
        }
        return donation;
    }

    public static long remaining(MinecraftServer server) {
        boolean open = server != null && ServerConfig.COMMUNITY_ENABLED.get();
        return open ? Math.max(0, CommunityProgress.goal(thresholds()) - WorldSkillData.of(server).communityXp()) : 0;
    }

    public static long contribute(MinecraftServer server, long experience) {
        long accepted = Math.min(Math.max(0, experience), remaining(server));
        if (accepted > 0) {
            setProgress(server, WorldSkillData.of(server).communityXp() + accepted);
        }
        return accepted;
    }

    public static void setProgress(MinecraftServer server, long communityXp) {
        WorldSkillData data = WorldSkillData.of(server);
        int unlockedBefore = CommunityProgress.unlockedCount(thresholds(), data.communityXp());
        data.setCommunityXp(communityXp);
        int unlockedNow = CommunityProgress.unlockedCount(thresholds(), data.communityXp());
        List<CommunityTier> tiers = SkillTreeManager.communityTiers();
        for (int index = unlockedBefore; index < unlockedNow; index++) {
            SkillEvents.post(new SkillEvents.CommunityTierUnlocked(server, tiers.get(index).id()));
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.celestialskilltree.community_unlocked",
                    Component.translatable(CommunityTier.nameKey(tiers.get(index).id())),
                    Component.translatable(CommunityTier.descriptionKey(tiers.get(index).id()))).withStyle(ChatFormatting.GOLD), false);
        }
        Payloads.CommunityProgressSync progress = new Payloads.CommunityProgressSync(data.communityXp());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (unlockedNow != unlockedBefore) {
                EffectEngine.refresh(player);
                SkillService.sendState(player);
            } else {
                SkillService.send(player, progress);
            }
        }
    }
}
