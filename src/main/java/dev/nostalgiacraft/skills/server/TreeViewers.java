package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.network.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TreeViewers {
    private static final Map<UUID, String> SECTOR_BY_VIEWER = new ConcurrentHashMap<>();

    private TreeViewers() {
    }

    public static void onViewing(Payloads.TreeViewing message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && (!message.open() || SkillService.withinRateLimit(player))) {
            set(player, message.open(), knownSector(message.sectorKey()) ? message.sectorKey() : "");
        }
    }

    public static void forget(ServerPlayer player) {
        set(player, false, "");
    }

    public static void announceTo(ServerPlayer joiner) {
        SECTOR_BY_VIEWER.forEach((viewer, sectorKey) -> SkillService.send(joiner, new Payloads.TreeViewer(viewer, true, sectorKey)));
    }

    private static boolean knownSector(String sectorKey) {
        return SkillTreeManager.graph().filter(graph -> graph.sectors().stream().anyMatch(sector -> sector.nameKey().equals(sectorKey)))
                .isPresent();
    }

    private static void set(ServerPlayer player, boolean open, String sectorKey) {
        String before = open ? SECTOR_BY_VIEWER.put(player.getUUID(), sectorKey) : SECTOR_BY_VIEWER.remove(player.getUUID());
        boolean changed = open ? !sectorKey.equals(before) : before != null;
        if (changed) {
            Payloads.TreeViewer update = new Payloads.TreeViewer(player.getUUID(), open, sectorKey);
            player.server.getPlayerList().getPlayers().forEach(other -> SkillService.send(other, update));
        }
    }
}
