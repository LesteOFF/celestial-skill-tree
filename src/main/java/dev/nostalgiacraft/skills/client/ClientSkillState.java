package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.XpCurve;
import dev.nostalgiacraft.skills.progression.XpMath;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class ClientSkillState {
    private static SkillGraphSnapshot graph;
    private static int graphRevision = -1;
    private static PlayerSkillState state;
    private static int stateGraphRevision = -1;
    private static Payloads.Economy economy = new Payloads.Economy(100, 120, true, true, true, false);
    private static XpCurve curve = XpCurve.DEFAULT;
    private static Payloads.WorldRules worldRules = Payloads.WorldRules.NONE;
    private static UUID inFlight;
    private static Consumer<Payloads.TransactionResult> resultListener = r -> { };
    private static Runnable changeListener = () -> { };
    private static boolean fixture;
    private static int fixtureLevel;
    private static float fixtureProgress;

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

    public static XpCurve curve() {
        return curve;
    }

    public static Payloads.WorldRules worldRules() {
        return worldRules;
    }

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

    public static void loadFixture(SkillGraphSnapshot fixtureGraph, PlayerSkillState fixtureState, Payloads.Economy fixtureEconomy, int level, float progress) {
        graph = fixtureGraph;
        graphRevision = 1;
        state = fixtureState;
        stateGraphRevision = 1;
        economy = fixtureEconomy;
        curve = XpCurve.DEFAULT;
        worldRules = Payloads.WorldRules.NONE;
        inFlight = null;
        fixture = true;
        fixtureLevel = level;
        fixtureProgress = progress;
        changeListener.run();
    }

    public static boolean isFixture() {
        return fixture;
    }

    public static long xpBalance() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            return XpMath.balance(player.experienceLevel, player.experienceProgress) + economy.walletXp();
        }
        return fixture ? XpMath.balance(fixtureLevel, fixtureProgress) : 0;
    }

    public static void reset() {
        fixture = false;
        graph = null;
        graphRevision = -1;
        state = null;
        inFlight = null;
    }

    public static void onGraph(Payloads.GraphSync graphSync) {
        try {
            graph = GraphCodec.decode(graphSync.data());
            graphRevision = graphSync.revision();
            changeListener.run();
        } catch (Exception e) {
            NostalgiaSkills.LOGGER.error("Rejected malformed skill graph from server", e);
        }
    }

    public static void onState(Payloads.StateSync stateSync) {
        state = stateSync.state();
        stateGraphRevision = stateSync.graphRevision();
        economy = stateSync.economy();
        curve = stateSync.curve();
        worldRules = stateSync.worldRules();
        changeListener.run();
    }

    public static void onResult(Payloads.TransactionResult result) {
        if (result.requestId().equals(inFlight)) {
            inFlight = null;
        }
        resultListener.accept(result);
        if (!result.ok() && Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(result.messageKey()), true);
        }
    }

    public static void onOpen(Payloads.OpenScreen openScreen) {
        ClientScreens.open(openScreen.resetMode());
    }
}
