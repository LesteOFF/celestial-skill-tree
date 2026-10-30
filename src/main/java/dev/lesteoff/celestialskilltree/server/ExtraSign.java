package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.PersistedPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class ExtraSign {
    private static final String GRANTED_KEY = "celestialskilltree_extra_sign";
    private static final String SOURCE_PREFIX = "extra_sign/";
    private static final int CHECK_PERIOD_TICKS = 40;
    private static final Map<UUID, Boolean> LAST_SEEN = new HashMap<>();

    private ExtraSign() {
    }

    public static boolean isGranted(ServerPlayer player) {
        return PersistedPlayerData.read(player).getBoolean(GRANTED_KEY);
    }

    public static void setGranted(ServerPlayer player, boolean granted) {
        PersistedPlayerData.update(player, persisted -> persisted.putBoolean(GRANTED_KEY, granted));
        settle(player);
    }

    public static boolean isActive(ServerPlayer player) {
        return isGranted(player) || isOnLastLife(player);
    }

    public static Map<String, List<SkillEffect>> effects(ServerPlayer player, PlayerSkillState state) {
        if (!isActive(player)) {
            return Map.of();
        }
        return of(state).map(sign -> Map.of(SOURCE_PREFIX + sign.id(), sign.allEffects())).orElse(Map.of());
    }

    public static Optional<SkillSign> of(PlayerSkillState state) {
        List<String> signs = SkillTreeManager.sortedSignIds();
        int born = state.sign().map(signs::indexOf).orElse(-1);
        if (born < 0 || signs.size() < 2) {
            return Optional.empty();
        }
        return SkillTreeManager.sign(signs.get((born + signs.size() / 2) % signs.size()));
    }

    private static boolean isOnLastLife(ServerPlayer player) {
        String objectiveName = ServerConfig.LAST_LIFE_OBJECTIVE.get();
        if (objectiveName.isEmpty()) {
            return false;
        }
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        ReadOnlyScoreInfo score = objective == null ? null : scoreboard.getPlayerScoreInfo(player, objective);
        return score != null && score.value() == ServerConfig.LAST_LIFE_SCORE.get();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_PERIOD_TICKS == 0) {
            settle(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SEEN.remove(event.getEntity().getUUID());
    }

    private static void settle(ServerPlayer player) {
        boolean active = isActive(player);
        Boolean before = LAST_SEEN.put(player.getUUID(), active);
        if (before == null ? !active : before == active) {
            return;
        }
        EffectEngine.refresh(player);
        of(ModAttachments.state(player)).ifPresent(sign -> player.sendSystemMessage(Component.translatable(
                active ? "message.celestialskilltree.extra_sign.gained" : "message.celestialskilltree.extra_sign.lost",
                Component.translatable(SkillSign.nameKey(sign.id()))).withStyle(ChatFormatting.AQUA)));
    }
}
