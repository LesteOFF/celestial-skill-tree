package dev.lesteoff.celestialskilltree.network;

import dev.lesteoff.celestialskilltree.server.SkillRequests;
import dev.lesteoff.celestialskilltree.server.TreeViewers;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkSetup {
    public static final String VERSION = "10";

    private NetworkSetup() {
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar payloadRegistrar = e.registrar(VERSION);
        payloadRegistrar.playToServer(Payloads.RequestSync.TYPE, Payloads.RequestSync.CODEC, SkillRequests::onRequestSync);
        payloadRegistrar.playToServer(Payloads.BuyPoint.TYPE, Payloads.BuyPoint.CODEC, SkillRequests::onBuyPoint);
        payloadRegistrar.playToServer(Payloads.ConfirmAllocation.TYPE, Payloads.ConfirmAllocation.CODEC, SkillRequests::onConfirm);
        payloadRegistrar.playToServer(Payloads.RequestReset.TYPE, Payloads.RequestReset.CODEC, SkillRequests::onReset);
        payloadRegistrar.playToServer(Payloads.RequestRefund.TYPE, Payloads.RequestRefund.CODEC, SkillRequests::onRefund);
        payloadRegistrar.playToServer(Payloads.ToggleNode.TYPE, Payloads.ToggleNode.CODEC, SkillRequests::onToggle);
        payloadRegistrar.playToServer(Payloads.DonateXp.TYPE, Payloads.DonateXp.CODEC, SkillRequests::onDonate);
        payloadRegistrar.playToServer(Payloads.TreeViewing.TYPE, Payloads.TreeViewing.CODEC, TreeViewers::onViewing);
        payloadRegistrar.playToServer(Payloads.UseBoon.TYPE, Payloads.UseBoon.CODEC, SkillRequests::onUseBoon);
        payloadRegistrar.playToClient(Payloads.TreeViewer.TYPE, Payloads.TreeViewer.CODEC, (msg, ctx) -> ClientHandlers.viewer(msg));
        payloadRegistrar.playToClient(Payloads.Gesture.TYPE, Payloads.Gesture.CODEC, (msg, ctx) -> ClientHandlers.gesture(msg));
        payloadRegistrar.playToClient(Payloads.Stargaze.TYPE, Payloads.Stargaze.CODEC, (msg, ctx) -> ClientHandlers.stargaze());
        payloadRegistrar.playToClient(Payloads.GraphSync.TYPE, Payloads.GraphSync.CODEC, (msg, ctx) -> ClientHandlers.graph(msg));
        payloadRegistrar.playToClient(Payloads.StateSync.TYPE, Payloads.StateSync.CODEC, (msg, ctx) -> ClientHandlers.state(msg));
        payloadRegistrar.playToClient(Payloads.CommunityProgressSync.TYPE, Payloads.CommunityProgressSync.CODEC,
                (msg, ctx) -> ClientHandlers.community(msg));
        payloadRegistrar.playToClient(Payloads.TransactionResult.TYPE, Payloads.TransactionResult.CODEC, (msg, ctx) -> ClientHandlers.result(msg));
        payloadRegistrar.playToClient(Payloads.OpenScreen.TYPE, Payloads.OpenScreen.CODEC, (msg, ctx) -> ClientHandlers.open(msg));
    }
}
