package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.server.SkillService;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkSetup {
    public static final String VERSION = "3";

    private NetworkSetup() {
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar payloadRegistrar = e.registrar(VERSION);
        payloadRegistrar.playToServer(Payloads.RequestSync.TYPE, Payloads.RequestSync.CODEC, SkillService::onRequestSync);
        payloadRegistrar.playToServer(Payloads.BuyPoint.TYPE, Payloads.BuyPoint.CODEC, SkillService::onBuyPoint);
        payloadRegistrar.playToServer(Payloads.ConfirmAllocation.TYPE, Payloads.ConfirmAllocation.CODEC, SkillService::onConfirm);
        payloadRegistrar.playToServer(Payloads.RequestReset.TYPE, Payloads.RequestReset.CODEC, SkillService::onReset);
        payloadRegistrar.playToServer(Payloads.RequestRefund.TYPE, Payloads.RequestRefund.CODEC, SkillService::onRefund);
        payloadRegistrar.playToServer(Payloads.DonateXp.TYPE, Payloads.DonateXp.CODEC, SkillService::onDonate);
        payloadRegistrar.playToClient(Payloads.GraphSync.TYPE, Payloads.GraphSync.CODEC, (msg, ctx) -> ClientHandlers.graph(msg));
        payloadRegistrar.playToClient(Payloads.StateSync.TYPE, Payloads.StateSync.CODEC, (msg, ctx) -> ClientHandlers.state(msg));
        payloadRegistrar.playToClient(Payloads.TransactionResult.TYPE, Payloads.TransactionResult.CODEC, (msg, ctx) -> ClientHandlers.result(msg));
        payloadRegistrar.playToClient(Payloads.OpenScreen.TYPE, Payloads.OpenScreen.CODEC, (msg, ctx) -> ClientHandlers.open(msg));
    }
}
