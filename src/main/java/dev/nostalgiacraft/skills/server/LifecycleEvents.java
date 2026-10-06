package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeReloadListener;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.effects.GameplayEvents;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Player lifecycle: every path that creates or reloads a player entity ends in reconcile + effect refresh + sync,
 * so the persisted attachment is always the single source of confirmed allocations.
 */
@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class LifecycleEvents {
    private LifecycleEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent e) {
        e.addListener(new SkillTreeReloadListener());
    }

    /** Fires after /reload (all players) and for each joining player. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent e) {
        e.getRelevantPlayers().forEach(p -> {
            SkillService.reconcileAndRefresh(p);
            SkillService.sendGraph(p);
            SkillService.sendState(p);
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            SkillService.reconcileAndRefresh(p);
            SkillService.sendState(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            SkillService.forget(p);
            EffectEngine.forget(p);
            GameplayEvents.forget(p.getUUID());
        }
    }

    /**
     * Death: copy the state exactly once here when keepOnDeath is enabled (the attachment is not copyOnDeath).
     * Non-death clones (returning from the End) are copied by NeoForge itself.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (e.isWasDeath() && ServerConfig.KEEP_ON_DEATH.get()) {
            ModAttachments.setState(e.getEntity(), ModAttachments.state(e.getOriginal()).copy());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            SkillService.reconcileAndRefresh(p);
            SkillService.sendState(p);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            EffectEngine.refresh(p);
            SkillService.sendState(p);
        }
    }
}
