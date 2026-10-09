package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.Allocation;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class StolenNodes {
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, Map<String, Long>> RETURN_TIMES = new HashMap<>();

    private StolenNodes() {
    }

    public static Set<String> of(ServerPlayer player) {
        return Set.copyOf(RETURN_TIMES.getOrDefault(player.getUUID(), Map.of()).keySet());
    }

    public static boolean stealNewest(ServerPlayer player, int seconds) {
        Optional<String> newest = newestHeld(player);
        newest.ifPresent(node -> {
            long returnsAt = player.serverLevel().getGameTime() + (long) seconds * TICKS_PER_SECOND;
            RETURN_TIMES.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(node, returnsAt);
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
        boolean checksNow = event.getEntity() instanceof ServerPlayer && event.getEntity().tickCount % TICKS_PER_SECOND == 0
                && RETURN_TIMES.containsKey(event.getEntity().getUUID());
        if (checksNow) {
            returnDueNodes((ServerPlayer) event.getEntity());
        }
    }

    private static void returnDueNodes(ServerPlayer player) {
        Map<String, Long> stolen = RETURN_TIMES.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        if (stolen.values().removeIf(returnsAt -> returnsAt <= now)) {
            if (stolen.isEmpty()) {
                RETURN_TIMES.remove(player.getUUID());
            }
            EffectEngine.refresh(player);
            player.sendSystemMessage(Component.translatable("message.nostalgiaskills.node_returned").withStyle(ChatFormatting.AQUA));
        }
    }

    static void forget(UUID player) {
        RETURN_TIMES.remove(player);
    }
}
