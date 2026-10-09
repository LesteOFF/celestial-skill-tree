package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.BossRewards;
import dev.nostalgiacraft.skills.progression.DiscoveryService;
import dev.nostalgiacraft.skills.registry.ModAttachments;
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

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class Discoveries {
    public static final String KILL_PREFIX = NostalgiaSkills.MODID + ":kill/";

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
            announce(player);
            SkillService.sendState(player);
        }
    }

    static void syncFromAdvancements(ServerPlayer player) {
        boolean discovered = SkillTreeManager.graph().filter(graph -> DiscoveryService.discoverAll(
                ModAttachments.state(player), graph, key -> hasEarned(player, key))).isPresent();
        if (discovered) {
            announce(player);
        }
    }

    private static boolean hasEarned(ServerPlayer player, String advancementId) {
        AdvancementHolder advancement = player.server.getAdvancements().get(ResourceLocation.parse(advancementId));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void announce(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.nostalgiaskills.constellation_revealed").withStyle(ChatFormatting.AQUA), true);
    }
}
