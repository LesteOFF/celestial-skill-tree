package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.client.ClientSkillState;

/**
 * Thin indirection so the common payload registration never links client classes on a dedicated server:
 * these methods are only invoked for client-bound payloads, which a dedicated server never receives.
 */
final class ClientHandlers {
    private ClientHandlers() {
    }

    static void graph(Payloads.GraphSync msg) {
        ClientSkillState.onGraph(msg);
    }

    static void state(Payloads.StateSync msg) {
        ClientSkillState.onState(msg);
    }

    static void result(Payloads.TransactionResult msg) {
        ClientSkillState.onResult(msg);
    }

    static void open(Payloads.OpenScreen msg) {
        ClientSkillState.onOpen(msg);
    }
}
