package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.compat.Integrations;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.GeneratedTree;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.data.SkillTreeReloadListener;
import dev.lesteoff.celestialskilltree.effects.CombatEvents;
import dev.lesteoff.celestialskilltree.effects.ConsumptionEvents;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.effects.MiningEvents;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class LifecycleEvents {
    private LifecycleEvents() {
    }

    private static void reconcileAndSync(ServerPlayer player) {
        SkillService.reconcileAndRefresh(player);
        SkillService.sendState(player);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SkillTreeReloadListener());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        GeneratedTree.generateFor(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Integrations.logSummary();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SkillTreeManager.reset();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(SkillService::resync);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reconcileAndSync(player);
            SkyEvents.greet(player);
            TreeViewers.announceTo(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillRequests.forget(player);
            TreeViewers.forget(player);
            EffectEngine.forget(player);
            CombatEvents.forget(player.getUUID());
            ConsumptionEvents.forget(player.getUUID());
            MiningEvents.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && ServerConfig.KEEP_ON_DEATH.get()) {
            ModAttachments.setState(event.getEntity(), ModAttachments.state(event.getOriginal()).copy());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reconcileAndSync(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffectEngine.refresh(player);
            SkillService.sendState(player);
        }
    }
}
