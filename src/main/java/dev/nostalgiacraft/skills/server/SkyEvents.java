package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import dev.nostalgiacraft.skills.progression.SkyEventSchedule;
import dev.nostalgiacraft.skills.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class SkyEvents {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final String MESSAGE_PREFIX = "message.nostalgiaskills.";
    private static volatile SkyEvent current = SkyEvent.NONE;

    private SkyEvents() {
    }

    public static SkyEvent current() {
        return current;
    }

    public static boolean isEclipse() {
        return current == SkyEvent.ECLIPSE;
    }

    public static boolean darkensDay() {
        return isEclipse() && ServerConfig.ECLIPSE_MONSTERS_SPAWN.get();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            update(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        current = evaluate(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        current = SkyEvent.NONE;
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (isEclipse() && event.getLevel().dimension() == Level.OVERWORLD) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            event.getEntity().displayClientMessage(Component.translatable(MESSAGE_PREFIX + "eclipse_no_sleep"), true);
        }
    }

    public static void force(MinecraftServer server, SkyEvent forced) {
        WorldSkillData data = WorldSkillData.of(server);
        data.setForcedEvent(forced);
        if (forced == SkyEvent.NONE) {
            data.setEclipseDay(SkyEventSchedule.UNSCHEDULED);
            data.setShowerDay(SkyEventSchedule.UNSCHEDULED);
        }
        update(server);
    }

    public static void greet(ServerPlayer player) {
        if (current != SkyEvent.NONE) {
            player.sendSystemMessage(styled(current, "_started"));
        }
    }

    private static void update(MinecraftServer server) {
        setCurrent(server, evaluate(server));
    }

    private static SkyEvent evaluate(MinecraftServer server) {
        WorldSkillData data = WorldSkillData.of(server);
        ServerLevel overworld = server.overworld();
        long today = data.skyDay(overworld.getDayTime());
        boolean eclipses = ServerConfig.ECLIPSE_ENABLED.get();
        boolean showers = ServerConfig.STAR_SHOWER_ENABLED.get();
        if (eclipses && SkyEventSchedule.needsScheduling(data.eclipseDay(), today)) {
            data.setEclipseDay(nextDay(overworld, today, ServerConfig.ECLIPSE_AVERAGE_DAYS.get()));
        }
        if (showers && SkyEventSchedule.needsScheduling(data.showerDay(), today)) {
            data.setShowerDay(nextDay(overworld, today, ServerConfig.STAR_SHOWER_AVERAGE_DAYS.get()));
        }
        SkyEvent natural = SkyEventSchedule.current(eclipses ? data.eclipseDay() : SkyEventSchedule.UNSCHEDULED,
                showers ? data.showerDay() : SkyEventSchedule.UNSCHEDULED, today, overworld.getDayTime());
        return data.forcedEvent() == SkyEvent.NONE ? natural : data.forcedEvent();
    }

    private static long nextDay(ServerLevel overworld, long today, int averageDays) {
        return SkyEventSchedule.nextAfter(today, averageDays, overworld.getRandom().nextDouble());
    }

    private static void setCurrent(MinecraftServer server, SkyEvent now) {
        SkyEvent before = current;
        if (now == before) {
            return;
        }
        current = now;
        if (before != SkyEvent.NONE) {
            server.getPlayerList().broadcastSystemMessage(styled(before, "_ended"), false);
        }
        if (now != SkyEvent.NONE) {
            server.getPlayerList().broadcastSystemMessage(styled(now, "_started"), false);
            server.getPlayerList().getPlayers().forEach(player -> player.playNotifySound(startSound(now), SoundSource.AMBIENT, 1f, 1f));
        }
        server.getPlayerList().getPlayers().forEach(SkillService::sendState);
        SkillEvents.post(new SkillEvents.SkyEventChanged(server, before, now));
    }

    private static Component styled(SkyEvent event, String suffix) {
        ChatFormatting color = event == SkyEvent.ECLIPSE ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD;
        return Component.translatable(MESSAGE_PREFIX + event.id() + suffix).withStyle(color);
    }

    private static SoundEvent startSound(SkyEvent event) {
        return event == SkyEvent.ECLIPSE ? ModSounds.ECLIPSE_START.get() : ModSounds.STAR_SHOWER_START.get();
    }
}
