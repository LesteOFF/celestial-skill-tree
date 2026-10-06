package dev.nostalgiacraft.skills.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class ClientRequests {
    private ClientRequests() {
    }

    public static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }

    public static UUID newRequest() {
        UUID requestId = UUID.randomUUID();
        ClientSkillState.markInFlight(requestId);
        return requestId;
    }
}
