package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.Allocation;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class StolenNodes {

    private StolenNodes() {
    }

    public static Set<String> of(ServerPlayer player) {
        return player.getExistingData(ModAttachments.STOLEN_NODES).<Set<String>>map(stolen -> Set.copyOf(stolen.keySet())).orElse(Set.of());
    }

    public static boolean stealNewest(ServerPlayer player, int seconds) {
        Optional<String> newest = newestHeld(player);
        newest.ifPresent(node -> {
            long returnsAt = player.serverLevel().getGameTime() + (long) seconds * SharedConstants.TICKS_PER_SECOND;
            player.getData(ModAttachments.STOLEN_NODES).put(node, returnsAt);
            EffectEngine.refresh(player);
            player.sendSystemMessage(Component.translatable("message.nostalgiaskills.node_stolen", nameOf(node), seconds)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        });
        return newest.isPresent();
    }

    private static Optional<String> newestHeld(ServerPlayer player) {
        PlayerSkillState state = ModAttachments.state(player);
        Set<String> stolen = of(player);
        List<String> oldestFirst = state.orderedAllocations().stream().map(Allocation::nodeId)
                .filter(id -> !stolen.contains(id) && !state.startingRoot().map(id::equals).orElse(false)).toList();
        return oldestFirst.isEmpty() ? Optional.empty() : Optional.of(oldestFirst.get(oldestFirst.size() - 1));
    }

    private static Component nameOf(String nodeId) {
        return SkillTreeManager.graph().flatMap(graph -> graph.node(nodeId)).map(SkillNodeDefinition::nameKey)
                .<Component>map(Component::translatable).orElse(Component.literal(nodeId));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        boolean checksNow = event.getEntity() instanceof ServerPlayer && event.getEntity().tickCount % SharedConstants.TICKS_PER_SECOND == 0
                && event.getEntity().getExistingData(ModAttachments.STOLEN_NODES).filter(stolen -> !stolen.isEmpty()).isPresent();
        if (checksNow) {
            returnDueNodes((ServerPlayer) event.getEntity());
        }
    }

    private static void returnDueNodes(ServerPlayer player) {
        Map<String, Long> stolen = player.getData(ModAttachments.STOLEN_NODES);
        long now = player.serverLevel().getGameTime();
        if (stolen.values().removeIf(returnsAt -> returnsAt <= now)) {
            EffectEngine.refresh(player);
            player.sendSystemMessage(Component.translatable("message.nostalgiaskills.node_returned").withStyle(ChatFormatting.AQUA));
        }
    }
}
