package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.client.render.SkyTint;
import dev.lesteoff.celestialskilltree.graph.GraphCodec;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.XpCurve;
import dev.lesteoff.celestialskilltree.progression.XpMath;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class ClientSkillState {
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;
    private static final Payloads.Economy DEFAULT_ECONOMY = new Payloads.Economy(100, 120, true, true, true, false);
    private static SkillGraphSnapshot graph;
    private static int graphRevision = -1;
    private static PlayerSkillState state;
    private static int stateGraphRevision = -1;
    private static Payloads.Economy economy = DEFAULT_ECONOMY;
    private static XpCurve curve = XpCurve.DEFAULT;
    private static Payloads.WorldRules worldRules = Payloads.WorldRules.NONE;
    private static UUID inFlight;
    private static long inFlightSince;
    private static String lastAnswer = "";
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

    public static String lastAnswer() {
        return lastAnswer;
    }

    public static boolean requestInFlight() {
        if (inFlight != null && Util.getMillis() - inFlightSince > REQUEST_TIMEOUT_MILLIS) {
            inFlight = null;
        }
        return inFlight != null;
    }

    public static void markInFlight(UUID id) {
        inFlight = id;
        inFlightSince = Util.getMillis();
    }

    public static MutableComponent message(Payloads.TransactionResult result) {
        return Component.translatable(result.messageKey(), result.arguments().toArray());
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
        if (fixture) {
            return XpMath.balance(fixtureLevel, fixtureProgress);
        }
        Player player = Minecraft.getInstance().player;
        return player == null ? 0 : XpMath.balance(player.experienceLevel, player.experienceProgress) + economy.walletXp();
    }

    public static void reset() {
        fixture = false;
        graph = null;
        graphRevision = -1;
        state = null;
        stateGraphRevision = -1;
        economy = DEFAULT_ECONOMY;
        curve = XpCurve.DEFAULT;
        worldRules = Payloads.WorldRules.NONE;
        inFlight = null;
        SkyTint.clear();
    }

    public static void onGraph(Payloads.GraphSync graphSync) {
        if (fixture) {
            return;
        }
        try {
            graph = GraphCodec.decode(graphSync.data());
            graphRevision = graphSync.revision();
            changeListener.run();
        } catch (Exception e) {
            CelestialSkillTree.LOGGER.error("Rejected malformed skill graph from server", e);
        }
    }

    public static void onState(Payloads.StateSync stateSync) {
        if (fixture) {
            return;
        }
        state = stateSync.state();
        stateGraphRevision = stateSync.graphRevision();
        economy = stateSync.economy();
        curve = stateSync.curve();
        worldRules = stateSync.worldRules();
        changeListener.run();
    }

    public static void onCommunityProgress(long communityXp) {
        if (fixture) {
            return;
        }
        worldRules = worldRules.withCommunityXp(communityXp);
        changeListener.run();
    }

    public static void onResult(Payloads.TransactionResult result) {
        if (result.requestId().equals(inFlight)) {
            inFlight = null;
        }
        lastAnswer = result.messageKey() + " " + String.join("/", result.arguments());
        resultListener.accept(result);
        if (Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message(result).withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
    }

    public static void onOpen(Payloads.OpenScreen openScreen) {
        ClientScreens.open(openScreen.resetMode());
    }
}
