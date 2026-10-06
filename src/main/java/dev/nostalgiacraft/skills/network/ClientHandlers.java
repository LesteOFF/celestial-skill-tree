package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.client.ClientSkillState;

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

    static void open(Payloads.OpenScreen openScreen) {
        ClientSkillState.onOpen(openScreen);
    }
}
