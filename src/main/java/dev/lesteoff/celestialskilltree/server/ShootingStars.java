package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.entity.FallingStar;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.progression.WorldClock;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class ShootingStars {
    private static final double SECONDS_PER_MINUTE = 60.0;

    private ShootingStars() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        boolean rollNow = event.getServer().getTickCount() % SharedConstants.TICKS_PER_SECOND == 0;
        boolean showering = SkyEvents.current() == SkyEvent.STAR_SHOWER;
        if (!rollNow || !ServerConfig.SHOOTING_STARS_ENABLED.get() || !(showering || WorldClock.isNight(overworld.getDayTime()))) {
            return;
        }
        double chancePerSecond = starsPerMinute(showering) / SECONDS_PER_MINUTE;
        for (ServerPlayer player : overworld.players()) {
            if (!player.isSpectator() && overworld.getRandom().nextDouble() < chancePerSecond) {
                FallingStar.spawnNear(player).filter(star -> overworld.canSeeSky(player.blockPosition()))
                        .ifPresent(star -> Gestures.starAppeared(player));
            }
        }
    }

    private static double starsPerMinute(boolean showering) {
        return showering ? ServerConfig.STAR_SHOWER_STARS_PER_MINUTE.get() : 1.0 / ServerConfig.SHOOTING_STAR_AVERAGE_MINUTES.get();
    }
}
