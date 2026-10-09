package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.network.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TreeViewers {
    private static final int MAX_SECTOR_KEY_LENGTH = 128;
    private static final Map<UUID, String> SECTOR_BY_VIEWER = new ConcurrentHashMap<>();

    private TreeViewers() {
    }

    public static void onViewing(Payloads.TreeViewing message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            set(player, message.open(), message.sectorKey());
        }
    }

    public static void forget(ServerPlayer player) {
        set(player, false, "");
    }

    public static void announceTo(ServerPlayer joiner) {
        SECTOR_BY_VIEWER.forEach((viewer, sectorKey) -> SkillService.send(joiner, new Payloads.TreeViewer(viewer, true, sectorKey)));
    }

    private static void set(ServerPlayer player, boolean open, String sectorKey) {
        String shown = sectorKey.length() > MAX_SECTOR_KEY_LENGTH ? "" : sectorKey;
        String before = open ? SECTOR_BY_VIEWER.put(player.getUUID(), shown) : SECTOR_BY_VIEWER.remove(player.getUUID());
        boolean changed = open ? !shown.equals(before) : before != null;
        if (changed) {
            Payloads.TreeViewer update = new Payloads.TreeViewer(player.getUUID(), open, shown);
            player.server.getPlayerList().getPlayers().forEach(other -> SkillService.send(other, update));
        }
    }
}
