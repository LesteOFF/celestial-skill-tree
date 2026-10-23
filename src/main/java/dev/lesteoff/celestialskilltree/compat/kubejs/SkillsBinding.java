package dev.lesteoff.celestialskilltree.compat.kubejs;

import dev.lesteoff.celestialskilltree.effects.EliteMobs;
import dev.lesteoff.celestialskilltree.effects.Triggers;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.server.ExtraSign;
import dev.lesteoff.celestialskilltree.server.SkyEvents;
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

    public static boolean hasExtraSign(ServerPlayer player) {
        return ExtraSign.isActive(player);
    }

    public static void setExtraSign(ServerPlayer player, boolean granted) {
        ExtraSign.setGranted(player, granted);
    }

    public static boolean isConstellated(LivingEntity entity) {
        return EliteMobs.isElite(entity);
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
