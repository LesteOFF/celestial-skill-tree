package dev.lesteoff.celestialskilltree.network;

import dev.lesteoff.celestialskilltree.client.ClientScreens;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.ClientViewers;
import dev.lesteoff.celestialskilltree.client.PlayerGestures;

final class ClientHandlers {
    private ClientHandlers() {
    }

    static void graph(Payloads.GraphSync graphSync) {
        ClientSkillState.onGraph(graphSync);
    }

    static void state(Payloads.StateSync stateSync) {
        ClientSkillState.onState(stateSync);
    }

    static void community(Payloads.CommunityProgressSync progress) {
        ClientSkillState.onCommunityProgress(progress.communityXp());
    }

    static void result(Payloads.TransactionResult transactionResult) {
        ClientSkillState.onResult(transactionResult);
    }

    static void gesture(Payloads.Gesture gesture) {
        PlayerGestures.play(gesture.player(), gesture.animation());
    }

    static void stargaze() {
        ClientScreens.stargaze();
    }

    static void viewer(Payloads.TreeViewer viewer) {
        ClientViewers.set(viewer.player(), viewer.open(), viewer.sectorKey());
    }

    static void open(Payloads.OpenScreen openScreen) {
        ClientSkillState.onOpen(openScreen);
    }
}
