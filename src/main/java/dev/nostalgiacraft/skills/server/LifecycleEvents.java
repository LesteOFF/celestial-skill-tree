package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.GeneratedTree;
import dev.nostalgiacraft.skills.data.SkillTreeReloadListener;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.effects.GameplayEvents;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
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
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            SkillService.reconcileAndRefresh(player);
            SkillService.sendGraph(player);
            SkillService.sendState(player);
        });
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
            SkillService.forget(player);
            TreeViewers.forget(player);
            EffectEngine.forget(player);
            GameplayEvents.forget(player.getUUID());
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
