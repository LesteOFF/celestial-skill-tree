package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client mirror of the server's graph and player state. It is only a presentation cache: every purchase, refund
 * or allocation is decided by the server and arrives here through StateSync.
 */
public final class ClientSkillState {
    private static SkillGraphSnapshot graph;
    private static int graphRevision = -1;
    private static PlayerSkillState state;
    private static int stateGraphRevision = -1;
    private static Payloads.Economy economy = new Payloads.Economy(100, 120, true, true);
    private static UUID inFlight;
    private static Consumer<Payloads.TransactionResult> resultListener = r -> { };
    private static Runnable changeListener = () -> { };

    private ClientSkillState() {
    }

    public static Optional<SkillGraphSnapshot> graph() {
        return Optional.ofNullable(graph);
    }

    public static int graphRevision() {
        return graphRevision;
    }

    public static Optional<PlayerSkillState> state() {
        return Optional.ofNullable(state);
    }

    public static Payloads.Economy economy() {
        return economy;
    }

    /** True once both the graph and a state for the same graph revision have arrived. */
    public static boolean ready() {
        return graph != null && state != null && stateGraphRevision == graphRevision;
    }

    public static boolean requestInFlight() {
        return inFlight != null;
    }

    public static void markInFlight(UUID id) {
        inFlight = id;
    }

    public static void setListeners(Consumer<Payloads.TransactionResult> onResult, Runnable onChange) {
        resultListener = onResult;
        changeListener = onChange;
    }

    public static void clearListeners() {
        resultListener = r -> { };
        changeListener = () -> { };
    }

    public static void reset() {
        graph = null;
        graphRevision = -1;
        state = null;
        inFlight = null;
    }

    public static void onGraph(Payloads.GraphSync msg) {
        try {
            graph = GraphCodec.decode(msg.data());
            graphRevision = msg.revision();
            changeListener.run();
        } catch (Exception e) {
            NostalgiaSkills.LOGGER.error("Rejected malformed skill graph from server", e);
        }
    }

    public static void onState(Payloads.StateSync msg) {
        state = msg.state();
        stateGraphRevision = msg.graphRevision();
        economy = msg.economy();
        changeListener.run();
    }

    public static void onResult(Payloads.TransactionResult msg) {
        if (msg.requestId().equals(inFlight)) {
            inFlight = null;
        }
        resultListener.accept(msg);
        if (!msg.ok() && Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(msg.messageKey()), true);
        }
    }

    public static void onOpen(Payloads.OpenScreen msg) {
        ClientScreens.open(msg.resetMode());
    }
}
