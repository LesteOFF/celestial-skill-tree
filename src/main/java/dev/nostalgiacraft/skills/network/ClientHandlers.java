package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.client.ClientScreens;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.ClientViewers;
import dev.nostalgiacraft.skills.client.PlayerGestures;

final class ClientHandlers {
    private ClientHandlers() {
    }

    static void graph(Payloads.GraphSync graphSync) {
        ClientSkillState.onGraph(graphSync);
    }

    static void state(Payloads.StateSync stateSync) {
        ClientSkillState.onState(stateSync);
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
