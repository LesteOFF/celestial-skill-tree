package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.effects.BossRewards;
import dev.lesteoff.celestialskilltree.progression.DiscoveryService;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class Discoveries {
    public static final String KILL_PREFIX = CelestialSkillTree.MODID + ":kill/";

    private Discoveries() {
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reveal(player, event.getAdvancement().id().toString());
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (BossRewards.isRealPlayer(event.getSource().getEntity())) {
            ResourceLocation slain = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            reveal((ServerPlayer) event.getSource().getEntity(), KILL_PREFIX + slain.getNamespace() + "/" + slain.getPath());
        }
    }

    public static void reveal(ServerPlayer player, String key) {
        boolean discovered = SkillTreeManager.graph()
                .filter(graph -> DiscoveryService.discover(ModAttachments.state(player), graph, key)).isPresent();
        if (discovered) {
            SkillEvents.post(new SkillEvents.DiscoveriesRevealed(player, Set.of(key)));
            announce(player);
            SkillService.sendState(player);
        }
    }

    static void syncFromAdvancements(ServerPlayer player) {
        Set<String> before = Set.copyOf(ModAttachments.state(player).discoveries());
        boolean discovered = SkillTreeManager.graph().filter(graph -> DiscoveryService.discoverAll(
                ModAttachments.state(player), graph, key -> hasEarned(player, key))).isPresent();
        if (discovered) {
            Set<String> revealed = new HashSet<>(ModAttachments.state(player).discoveries());
            revealed.removeAll(before);
            SkillEvents.post(new SkillEvents.DiscoveriesRevealed(player, revealed));
            announce(player);
        }
    }

    private static boolean hasEarned(ServerPlayer player, String advancementId) {
        AdvancementHolder advancement = player.server.getAdvancements().get(ResourceLocation.parse(advancementId));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void announce(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.celestialskilltree.constellation_revealed").withStyle(ChatFormatting.AQUA), true);
    }
}
