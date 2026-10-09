package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class Gestures {
    private static final String LEARN_KEYSTONE = "learn_keystone";
    private static final String USE_ORB = "use_orb";
    private static final String POINT_AT_SKY = "point_at_sky";

    private Gestures() {
    }

    public static void keystonesLearned(ServerPlayer player, SkillGraphSnapshot graph, List<String> learned) {
        boolean keystone = learned.stream().anyMatch(id -> graph.node(id).filter(node -> node.type() == NodeType.KEYSTONE).isPresent());
        if (keystone) {
            show(player, LEARN_KEYSTONE);
        }
    }

    public static void orbUsed(ServerPlayer player) {
        show(player, USE_ORB);
    }

    public static void starAppeared(ServerPlayer player) {
        show(player, POINT_AT_SKY);
    }

    private static void show(ServerPlayer player, String animation) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Payloads.Gesture(player.getUUID(), animation));
    }
}
