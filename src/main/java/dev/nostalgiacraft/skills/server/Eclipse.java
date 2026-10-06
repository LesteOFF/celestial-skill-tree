package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.progression.EclipseSchedule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class Eclipse {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final long TICKS_PER_DAY = 24_000;
    private static volatile boolean active;

    private Eclipse() {
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            update(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        active = false;
    }

    public static void force(MinecraftServer server, boolean forced) {
        WorldSkillData data = WorldSkillData.of(server);
        data.setEclipseForced(forced);
        if (!forced) {
            data.setEclipseDay(EclipseSchedule.UNSCHEDULED);
        }
        update(server);
    }

    private static void update(MinecraftServer server) {
        WorldSkillData data = WorldSkillData.of(server);
        ServerLevel overworld = server.overworld();
        long worldDay = overworld.getDayTime() / TICKS_PER_DAY;
        boolean scheduled = ServerConfig.ECLIPSE_ENABLED.get();
        if (scheduled && EclipseSchedule.needsScheduling(data.eclipseDay(), worldDay)) {
            data.setEclipseDay(EclipseSchedule.nextAfter(worldDay, ServerConfig.ECLIPSE_AVERAGE_DAYS.get(), overworld.getRandom().nextDouble()));
        }
        boolean natural = scheduled && EclipseSchedule.isActive(data.eclipseDay(), worldDay, overworld.isNight());
        setActive(server, data.eclipseForced() || natural);
    }

    private static void setActive(MinecraftServer server, boolean nowActive) {
        if (nowActive == active) {
            return;
        }
        active = nowActive;
        String messageKey = nowActive ? "message.nostalgiaskills.eclipse_started" : "message.nostalgiaskills.eclipse_ended";
        server.getPlayerList().broadcastSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_PURPLE), false);
        server.getPlayerList().getPlayers().forEach(SkillService::sendState);
    }
}
