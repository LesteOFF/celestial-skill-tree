package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class EliteBossBars {
    public static final String TITLE_KEY = "bossbar.celestialskilltree.constellated";
    private static final int UPDATE_PERIOD_TICKS = 10;
    private static final double SHOW_WITHIN_BLOCKS = 24;
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private EliteBossBars() {
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        boolean updatesNow = event.getEntity() instanceof Monster monster && monster.level() instanceof ServerLevel
                && monster.tickCount % UPDATE_PERIOD_TICKS == 0 && ServerConfig.ELITE_BOSS_BAR.get() && EliteMobs.isElite(monster);
        if (updatesNow) {
            update((Monster) event.getEntity());
        }
    }

    private static void update(Monster monster) {
        ServerBossEvent bar = BARS.computeIfAbsent(monster.getUUID(), id -> new ServerBossEvent(Component.translatable(TITLE_KEY),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
        bar.setProgress(Math.clamp(monster.getHealth() / monster.getMaxHealth(), 0f, 1f));
        List<ServerPlayer> near = ((ServerLevel) monster.level()).getPlayers(
                player -> player.distanceToSqr(monster) <= SHOW_WITHIN_BLOCKS * SHOW_WITHIN_BLOCKS && player.hasLineOfSight(monster));
        List.copyOf(bar.getPlayers()).stream().filter(player -> !near.contains(player)).forEach(bar::removePlayer);
        near.forEach(bar::addPlayer);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        ServerBossEvent bar = event.getLevel().isClientSide() ? null : BARS.remove(event.getEntity().getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }
}
