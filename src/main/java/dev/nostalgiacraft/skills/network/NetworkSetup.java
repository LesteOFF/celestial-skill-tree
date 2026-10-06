package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.server.SkillService;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers payloads; client-bound handlers are routed through {@link ClientHandlers} (client-only class). */
public final class NetworkSetup {
    public static final String VERSION = "1";

    private NetworkSetup() {
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar(VERSION);
        r.playToServer(Payloads.RequestSync.TYPE, Payloads.RequestSync.CODEC, SkillService::onRequestSync);
        r.playToServer(Payloads.BuyPoint.TYPE, Payloads.BuyPoint.CODEC, SkillService::onBuyPoint);
        r.playToServer(Payloads.ConfirmAllocation.TYPE, Payloads.ConfirmAllocation.CODEC, SkillService::onConfirm);
        r.playToServer(Payloads.RequestReset.TYPE, Payloads.RequestReset.CODEC, SkillService::onReset);
        r.playToServer(Payloads.RequestRefund.TYPE, Payloads.RequestRefund.CODEC, SkillService::onRefund);
        r.playToClient(Payloads.GraphSync.TYPE, Payloads.GraphSync.CODEC, (msg, ctx) -> ClientHandlers.graph(msg));
        r.playToClient(Payloads.StateSync.TYPE, Payloads.StateSync.CODEC, (msg, ctx) -> ClientHandlers.state(msg));
        r.playToClient(Payloads.TransactionResult.TYPE, Payloads.TransactionResult.CODEC, (msg, ctx) -> ClientHandlers.result(msg));
        r.playToClient(Payloads.OpenScreen.TYPE, Payloads.OpenScreen.CODEC, (msg, ctx) -> ClientHandlers.open(msg));
    }
}
