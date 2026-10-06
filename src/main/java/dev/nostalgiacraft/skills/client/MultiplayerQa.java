package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class MultiplayerQa {
    private record Step(String name, Runnable action, BooleanSupplier done, int timeoutTicks) {
    }

    private static final String ROLE = System.getProperty("nostalgiaskills.mpqa", "");
    private static final String ADDRESS = System.getProperty("nostalgiaskills.mpqa.address", "127.0.0.1:25565");
    private static final String GUARDIAN_TARGET = "nostalgiaskills:guardian/gatekeeper";
    private static final String MARKSMAN_TARGET = "nostalgiaskills:marksman/eagle_eye";
    private static final String MARKSMAN_LAST = "nostalgiaskills:marksman/fletching_4";

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static final List<String> RESULTS = new ArrayList<>();
    private static boolean built;
    private static int step;
    private static int ticks;
    private static boolean started;
    private static int counter;
    private static long xpBefore;
    private static String refundTarget;
    private static double statBefore;

    private MultiplayerQa() {
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static PlayerSkillState state() {
        return ClientSkillState.state().orElse(null);
    }

    private static void log(String line) {
        REPORT.add(line);
        NostalgiaSkills.LOGGER.info("Multiplayer QA [{}]: {}", ROLE, line);
    }

    private static double maxHealth() {
        return minecraft().player.getAttributeValue(Attributes.MAX_HEALTH);
    }

    private static double projectile() {
        return minecraft().player.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE);
    }

    private static String summary() {
        PlayerSkillState state = state();
        return "player=" + minecraft().player.getGameProfile().getName() + ", purchased=" + state.purchasedPoints() + ", available="
                + state.availablePoints() + ", allocations=" + state.allocations().size() + " " + new TreeSet<>(state.allocations().keySet())
                + ", max health=" + maxHealth() + ", projectile_damage=" + projectile() + ", ledger ok=" + state.ledgerHolds();
    }

    private static boolean inWorld() {
        return minecraft().player != null && minecraft().getConnection() != null && ClientSkillState.ready() && !ClientSkillState.isFixture();
    }

    private static void connect() {
        ClientSkillState.reset();
        ServerData data = new ServerData("nostalgiaskills qa", ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft(), ServerAddress.parseString(ADDRESS), data, false, null);
    }

    private static Step connectStep() {
        return new Step("connect to " + ADDRESS, MultiplayerQa::connect, MultiplayerQa::inWorld, 6000);
    }

    private static List<String> route(String root, String target, int limit) {
        SkillGraphSnapshot graph = ClientSkillState.graph().orElseThrow();
        PlayerSkillState state = state().copy();
        AllocationService.confirm(graph, state, List.of(root));
        List<String> out = new ArrayList<>();
        out.add(root);
        PathFinder.route(graph, state, List.of(), target).ifPresent(r -> out.addAll(r.newNodes()));
        return List.copyOf(out.subList(0, Math.min(limit, out.size())));
    }

    private static Step buyPoints(int target) {
        return new Step("buy points up to " + target, () -> xpBefore = ClientSkillState.xpBalance(), () -> {
            if (state().purchasedPoints() >= target) {
                return true;
            }
            if (!ClientSkillState.requestInFlight()) {
                UUID id = UUID.randomUUID();
                ClientSkillState.markInFlight(id);
                PacketDistributor.sendToServer(new Payloads.BuyPoint(id, state().stateRevision(), 1));
            }
            return false;
        }, 1200);
    }

    private static Step note(String name, Runnable r) {
        return new Step(name, r, () -> true, 5);
    }

    private static void build() {
        STEPS.add(connectStep());
        switch (ROLE) {
            case "alpha" -> alpha();
            case "beta" -> beta();
            case "alpha_verify", "beta_verify" -> {
                STEPS.add(new Step("settle", () -> { }, () -> ticks >= 60, 100));
                STEPS.add(note("report after server restart", () -> log("after dedicated server restart: " + summary())));
            }
            default -> log("unknown role " + ROLE);
        }
        STEPS.add(note("finish", MultiplayerQa::finish));
    }

    private static void waitForPeer() {
        STEPS.add(new Step("wait for the other player", () -> { }, () -> minecraft().getConnection().getOnlinePlayers().size() >= 2, 6000));
        STEPS.add(note("report join", () -> log("joined dedicated server; online players=" + minecraft().getConnection().getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName()).sorted().toList() + "; " + summary())));
    }

    private static void alpha() {
        waitForPeer();
        STEPS.add(new Step("xp", () -> minecraft().player.connection.sendCommand("xp add @s 30 levels"), () -> minecraft().player.experienceLevel >= 30, 400));
        STEPS.add(buyPoints(3));
        STEPS.add(note("report purchase", () -> log("bought 3 points: xp raw " + xpBefore + " -> " + ClientSkillState.xpBalance()
                + " (expected -" + (100 + 122 + 148) + "), next cost=" + ClientSkillState.economy().nextPointCost())));
        STEPS.add(new Step("guardian route", () -> {
            List<String> nodes = route("nostalgiaskills:guardian/hub", GUARDIAN_TARGET, 4);
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.ConfirmAllocation(id, state().stateRevision(), ClientSkillState.graphRevision(), nodes));
            log("confirm sent for " + nodes);
        }, () -> state().allocations().size() == 4 && maxHealth() > 20.0, 1200));
        STEPS.add(note("report build", () -> log("after confirm: " + summary() + " (expected max health 25: hub +2, 3x Vitality +1)")));
        STEPS.add(new Step("logout", () -> {
            minecraft().level.disconnect();
            minecraft().disconnect(new TitleScreen());
        }, () -> minecraft().level == null && minecraft().getConnection() == null, 1200));
        STEPS.add(connectStep());
        STEPS.add(new Step("effects after login", () -> { }, () -> maxHealth() == 25.0, 400));
        STEPS.add(note("report relog", () -> log("after logout + login: " + summary())));
        STEPS.add(new Step("go to the End", () -> minecraft().player.connection.sendCommand("execute in minecraft:the_end run tp @s 100 52 0"),
                () -> minecraft().level.dimension() == Level.END && maxHealth() == 25.0, 1200));
        STEPS.add(note("report End", () -> log("in the End: " + summary())));
        STEPS.add(new Step("leave the End through an exit portal", () -> minecraft().player.connection.sendCommand(
                "execute at @s run fill ~-1 ~-4 ~-1 ~1 ~1 ~1 minecraft:end_portal"), () -> {
            if (minecraft().screen instanceof WinScreen win) {
                win.onClose();
            }
            return minecraft().level != null && minecraft().level.dimension() == Level.OVERWORLD && minecraft().player.isAlive()
                    && ClientSkillState.ready() && maxHealth() == 25.0;
        }, 2400));
        STEPS.add(note("report End return", () -> log("after End exit portal + credits (End return): " + summary())));
        STEPS.add(new Step("refund the last learned skill", () -> {
            counter = state().allocations().size();
            statBefore = maxHealth();
            refundTarget = state().orderedAllocations().get(counter - 1).nodeId();
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.RequestRefund(id, state().stateRevision(),
                    ClientSkillState.graphRevision(), refundTarget));
            log("refund requested for " + refundTarget + " (paid " + state().allocations().get(refundTarget).paidCost()
                    + "), max health " + statBefore);
        }, () -> !state().isAllocated(refundTarget) && maxHealth() < statBefore, 1200));
        STEPS.add(note("report refund", () -> log("after refunding one skill: allocations " + counter + " -> "
                + state().allocations().size() + ", max health " + statBefore + " -> " + maxHealth() + "; " + summary())));
    }

    private static void beta() {
        waitForPeer();
        STEPS.add(new Step("xp", () -> minecraft().player.connection.sendCommand("xp add @s 30 levels"), () -> minecraft().player.experienceLevel >= 30, 400));
        STEPS.add(buyPoints(4));
        STEPS.add(note("report purchase", () -> log("bought 4 points through the latency proxy: xp raw " + xpBefore + " -> "
                + ClientSkillState.xpBalance() + " (expected -" + (100 + 122 + 148 + 178) + "), next cost=" + ClientSkillState.economy().nextPointCost())));
        STEPS.add(new Step("rapid repeated buy requests", () -> {
            RESULTS.clear();
            counter = state().purchasedPoints();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey()), () -> { });
            long rev = state().stateRevision();
            UUID same = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                PacketDistributor.sendToServer(new Payloads.BuyPoint(same, rev, 1));
            }
            for (int i = 0; i < 10; i++) {
                PacketDistributor.sendToServer(new Payloads.BuyPoint(UUID.randomUUID(), rev, 1));
            }
        }, () -> RESULTS.size() >= 12, 1200));
        STEPS.add(note("report rapid", () -> {
            Map<String, Integer> counts = new TreeMap<>();
            RESULTS.forEach(r -> counts.merge(r, 1, Integer::sum));
            log("12 buy requests sent back-to-back under latency (2 share one id, all expect the same revision): " + counts
                    + "; purchased " + counter + " -> " + state().purchasedPoints() + " (exactly one purchase expected), ledger ok=" + state().ledgerHolds());
            ClientSkillState.clearListeners();
        }));
        STEPS.add(new Step("cool down", () -> { }, () -> ticks >= 60, 100));
        STEPS.add(new Step("marksman route", () -> {
            List<String> nodes = route("nostalgiaskills:marksman/hub", MARKSMAN_TARGET, 5);
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey() + " " + r.detail()), () -> { });
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.ConfirmAllocation(id, state().stateRevision(), ClientSkillState.graphRevision(), nodes));
            log("confirm sent for " + nodes);
        }, () -> state().isAllocated(MARKSMAN_LAST) || !RESULTS.isEmpty() && !RESULTS.contains("ok"), 1200));
        STEPS.add(note("report confirm result", () -> {
            log("server answer to confirm: " + RESULTS);
            ClientSkillState.clearListeners();
        }));
        STEPS.add(new Step("effects", () -> { }, () -> projectile() > 0.0, 400));
        STEPS.add(note("report build", () -> log("after confirm: " + summary() + " (no guardian nodes expected: builds are per player)")));
        STEPS.add(new Step("cool down", () -> { }, () -> ticks >= 60, 100));
        STEPS.add(new Step("refund a mid-branch skill, replayed under latency", () -> {
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey()), () -> { });
            counter = state().availablePoints();
            statBefore = projectile();
            refundTarget = state().orderedAllocations().get(2).nodeId();
            long rev = state().stateRevision();
            int graphRev = ClientSkillState.graphRevision();
            UUID same = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                PacketDistributor.sendToServer(new Payloads.RequestRefund(same, rev, graphRev, refundTarget));
            }
            for (int i = 0; i < 3; i++) {
                PacketDistributor.sendToServer(new Payloads.RequestRefund(UUID.randomUUID(), rev, graphRev, refundTarget));
            }
            log("5 refund requests for " + refundTarget + " (2 share one id, all expect state revision " + rev + ")");
        }, () -> RESULTS.size() >= 5, 1200));
        STEPS.add(note("report refund", () -> {
            Map<String, Integer> counts = new TreeMap<>();
            RESULTS.forEach(r -> counts.merge(r, 1, Integer::sum));
            log("refund replay results " + counts + "; available " + counter + " -> " + state().availablePoints()
                    + ", projectile_damage " + statBefore + " -> " + projectile() + " (" + refundTarget
                    + " and everything that depended on it removed); " + summary());
            ClientSkillState.clearListeners();
        }));
        STEPS.add(new Step("cool down", () -> { }, () -> ticks >= 60, 100));
        STEPS.add(new Step("learn the refunded branch again", () -> {
            SkillGraphSnapshot graph = ClientSkillState.graph().orElseThrow();
            List<String> nodes = PathFinder.route(graph, state(), List.of(), MARKSMAN_LAST)
                    .filter(PathFinder.Route::valid).map(PathFinder.Route::newNodes).orElse(List.of());
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.ConfirmAllocation(id, state().stateRevision(),
                    ClientSkillState.graphRevision(), nodes));
            log("learning the refunded branch again: " + nodes);
        }, () -> state().isAllocated(MARKSMAN_LAST), 1200));
        STEPS.add(note("report relearn", () -> log("after learning the refunded branch again at the current prices: "
                + summary())));
    }

    private static void finish() {
        try {
            Files.write(minecraft().gameDirectory.toPath().resolve("nostalgiaskills_mpqa_" + ROLE + ".txt"), REPORT);
        } catch (IOException e) {
            NostalgiaSkills.LOGGER.error("could not write multiplayer QA report", e);
        }
        NostalgiaSkills.LOGGER.info("Multiplayer QA [{}]: finished", ROLE);
        if (minecraft().level != null) {
            minecraft().level.disconnect();
        }
        minecraft().stop();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (ROLE.isEmpty()) {
            return;
        }
        if (!built) {
            if (!(minecraft().screen instanceof TitleScreen)) {
                return;
            }
            built = true;
            build();
        }
        if (step >= STEPS.size()) {
            return;
        }
        Step s = STEPS.get(step);
        try {
            if (!started) {
                started = true;
                s.action().run();
                return;
            }
            ticks++;
            if (s.done().getAsBoolean()) {
                step++;
                ticks = 0;
                started = false;
            } else if (ticks > s.timeoutTicks()) {
                log("FAILED (timeout): " + s.name() + (minecraft().player != null && state() != null ? "; " + summary() : ""));
                step = STEPS.size() - 1;
                ticks = 0;
                started = false;
            }
        } catch (Exception ex) {
            log("FAILED (exception) in " + s.name() + ": " + ex);
            NostalgiaSkills.LOGGER.error("Multiplayer QA step failed", ex);
            step = STEPS.size() - 1;
            ticks = 0;
            started = false;
        }
    }
}
