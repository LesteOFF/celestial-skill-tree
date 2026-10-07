package dev.nostalgiacraft.skills.compat.kubejs;

import dev.nostalgiacraft.skills.effects.Triggers;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.server.SkyEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class SkillsBinding {
    private SkillsBinding() {
    }

    public static boolean hasNode(ServerPlayer player, String nodeId) {
        return ModAttachments.state(player).isAllocated(nodeId);
    }

    public static List<String> learnedNodes(ServerPlayer player) {
        return List.copyOf(ModAttachments.state(player).allocations().keySet());
    }

    public static int availablePoints(ServerPlayer player) {
        return ModAttachments.state(player).availablePoints();
    }

    public static String sign(ServerPlayer player) {
        return ModAttachments.state(player).sign().orElse(null);
    }

    public static String skyEvent() {
        return SkyEvents.current().id();
    }

    public static void fireTrigger(ServerPlayer player, String trigger) {
        Triggers.fire(player, trigger, null);
    }

    public static void fireTrigger(ServerPlayer player, String trigger, LivingEntity target) {
        Triggers.fire(player, trigger, target);
    }
}
