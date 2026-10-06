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
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Development-only multiplayer scenario against a real dedicated server (tools/qa/multiplayer_scenario.sh).
 * Active only when the JVM is started with -Dnostalgiaskills.mpqa=ROLE (alpha, beta, alpha_verify, beta_verify) and
 * -Dnostalgiaskills.mpqa.address=HOST:PORT. Every action goes over the network to the dedicated server's authoritative
 * services; results are written to nostalgiaskills_mpqa_ROLE.txt in the client's game directory.
 */
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

    private MultiplayerQa() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static PlayerSkillState st() {
        return ClientSkillState.state().orElse(null);
    }

    private static void log(String line) {
        REPORT.add(line);
        NostalgiaSkills.LOGGER.info("Multiplayer QA [{}]: {}", ROLE, line);
    }

    private static double maxHealth() {
        return mc().player.getAttributeValue(Attributes.MAX_HEALTH);
    }

    private static double projectile() {
        return mc().player.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE);
    }

    private static String summary() {
        PlayerSkillState s = st();
        return "player=" + mc().player.getGameProfile().getName() + ", purchased=" + s.purchasedPoints() + ", available="
                + s.availablePoints() + ", allocations=" + s.allocations().size() + " " + new java.util.TreeSet<>(s.allocations().keySet())
                + ", max health=" + maxHealth() + ", projectile_damage=" + projectile() + ", ledger ok=" + s.ledgerHolds();
    }

    private static boolean inWorld() {
        return mc().player != null && mc().getConnection() != null && ClientSkillState.ready() && !ClientSkillState.isFixture();
    }

    private static void connect() {
        ClientSkillState.reset();
        ServerData data = new ServerData("nostalgiaskills qa", ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), mc(), ServerAddress.parseString(ADDRESS), data, false, null);
    }

    private static Step connectStep() {
        return new Step("connect to " + ADDRESS, MultiplayerQa::connect, MultiplayerQa::inWorld, 6000);
    }

    /** Root plus the cheapest route to {@code target}, truncated to {@code limit} nodes. */
    private static List<String> route(String root, String target, int limit) {
        SkillGraphSnapshot g = ClientSkillState.graph().orElseThrow();
        PlayerSkillState tmp = st().copy();
        AllocationService.confirm(g, tmp, List.of(root));
        List<String> out = new ArrayList<>();
        out.add(root);
        PathFinder.route(g, tmp, List.of(), target).ifPresent(r -> out.addAll(r.newNodes()));
        return List.copyOf(out.subList(0, Math.min(limit, out.size())));
    }

    private static Step buyPoints(int target) {
        return new Step("buy points up to " + target, () -> xpBefore = ClientSkillState.xpBalance(), () -> {
            if (st().purchasedPoints() >= target) {
                return true;
            }
            if (!ClientSkillState.requestInFlight()) {
                UUID id = UUID.randomUUID();
                ClientSkillState.markInFlight(id);
                PacketDistributor.sendToServer(new Payloads.BuyPoint(id, st().stateRevision()));
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
                STEPS.add(new Step("settle", () -> { }, () -> ticks >= 60, 100)); // let effect attributes sync
                STEPS.add(note("report after server restart", () -> log("after dedicated server restart: " + summary())));
            }
            default -> log("unknown role " + ROLE);
        }
        STEPS.add(note("finish", MultiplayerQa::finish));
    }

    private static void waitForPeer() {
        STEPS.add(new Step("wait for the other player", () -> { }, () -> mc().getConnection().getOnlinePlayers().size() >= 2, 6000));
        STEPS.add(note("report join", () -> log("joined dedicated server; online players=" + mc().getConnection().getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName()).sorted().toList() + "; " + summary())));
    }

    private static void alpha() {
        waitForPeer();
        STEPS.add(new Step("xp", () -> mc().player.connection.sendCommand("xp add @s 30 levels"), () -> mc().player.experienceLevel >= 30, 400));
        STEPS.add(buyPoints(3));
        STEPS.add(note("report purchase", () -> log("bought 3 points: xp raw " + xpBefore + " -> " + ClientSkillState.xpBalance()
                + " (expected -" + (100 + 122 + 148) + "), next cost=" + ClientSkillState.economy().nextPointCost())));
        STEPS.add(new Step("guardian route", () -> {
            List<String> nodes = route("nostalgiaskills:guardian/hub", GUARDIAN_TARGET, 4);
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.ConfirmAllocation(id, st().stateRevision(), ClientSkillState.graphRevision(), nodes));
            log("confirm sent for " + nodes);
        }, () -> st().allocations().size() == 4 && maxHealth() > 20.0, 1200));
        STEPS.add(note("report build", () -> log("after confirm: " + summary() + " (expected max health 25: hub +2, 3x Vitality +1)")));
        STEPS.add(new Step("logout", () -> {
            mc().level.disconnect();
            mc().disconnect(new TitleScreen());
        }, () -> mc().level == null && mc().getConnection() == null, 1200));
        STEPS.add(connectStep());
        STEPS.add(new Step("effects after login", () -> { }, () -> maxHealth() == 25.0, 400));
        STEPS.add(note("report relog", () -> log("after logout + login: " + summary())));
        STEPS.add(new Step("go to the End", () -> mc().player.connection.sendCommand("execute in minecraft:the_end run tp @s 100 52 0"),
                () -> mc().level.dimension() == Level.END && maxHealth() == 25.0, 1200));
        STEPS.add(note("report End", () -> log("in the End: " + summary())));
        STEPS.add(new Step("leave the End through an exit portal", () -> mc().player.connection.sendCommand(
                "execute at @s run fill ~-1 ~-4 ~-1 ~1 ~1 ~1 minecraft:end_portal"), () -> {
            if (mc().screen instanceof WinScreen win) {
                win.onClose(); // skip the credits like a player pressing Escape: sends the respawn request
            }
            return mc().level != null && mc().level.dimension() == Level.OVERWORLD && mc().player.isAlive()
                    && ClientSkillState.ready() && maxHealth() == 25.0;
        }, 2400));
        STEPS.add(note("report End return", () -> log("after End exit portal + credits (End return): " + summary())));
    }

    private static void beta() {
        waitForPeer();
        STEPS.add(new Step("xp", () -> mc().player.connection.sendCommand("xp add @s 30 levels"), () -> mc().player.experienceLevel >= 30, 400));
        STEPS.add(buyPoints(4));
        STEPS.add(note("report purchase", () -> log("bought 4 points through the latency proxy: xp raw " + xpBefore + " -> "
                + ClientSkillState.xpBalance() + " (expected -" + (100 + 122 + 148 + 178) + "), next cost=" + ClientSkillState.economy().nextPointCost())));
        STEPS.add(new Step("rapid repeated buy requests", () -> {
            RESULTS.clear();
            counter = st().purchasedPoints();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey()), () -> { });
            long rev = st().stateRevision();
            UUID same = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                PacketDistributor.sendToServer(new Payloads.BuyPoint(same, rev));
            }
            for (int i = 0; i < 10; i++) {
                PacketDistributor.sendToServer(new Payloads.BuyPoint(UUID.randomUUID(), rev));
            }
        }, () -> RESULTS.size() >= 12, 1200));
        STEPS.add(note("report rapid", () -> {
            Map<String, Integer> counts = new TreeMap<>();
            RESULTS.forEach(r -> counts.merge(r, 1, Integer::sum));
            log("12 buy requests sent back-to-back under latency (2 share one id, all expect the same revision): " + counts
                    + "; purchased " + counter + " -> " + st().purchasedPoints() + " (exactly one purchase expected), ledger ok=" + st().ledgerHolds());
            ClientSkillState.clearListeners();
        }));
        // let the per-second request limiter window pass and the last state sync arrive before confirming
        STEPS.add(new Step("cool down", () -> { }, () -> ticks >= 60, 100));
        STEPS.add(new Step("marksman route", () -> {
            List<String> nodes = route("nostalgiaskills:marksman/hub", MARKSMAN_TARGET, 5);
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey() + " " + r.detail()), () -> { });
            UUID id = UUID.randomUUID();
            ClientSkillState.markInFlight(id);
            PacketDistributor.sendToServer(new Payloads.ConfirmAllocation(id, st().stateRevision(), ClientSkillState.graphRevision(), nodes));
            log("confirm sent for " + nodes);
        }, () -> st().isAllocated(MARKSMAN_LAST) || !RESULTS.isEmpty() && !RESULTS.contains("ok"), 1200));
        STEPS.add(note("report confirm result", () -> {
            log("server answer to confirm: " + RESULTS);
            ClientSkillState.clearListeners();
        }));
        STEPS.add(new Step("effects", () -> { }, () -> projectile() > 0.0, 400));
        STEPS.add(note("report build", () -> log("after confirm: " + summary() + " (no guardian nodes expected: builds are per player)")));
    }

    private static void finish() {
        try {
            Files.write(mc().gameDirectory.toPath().resolve("nostalgiaskills_mpqa_" + ROLE + ".txt"), REPORT);
        } catch (IOException e) {
            NostalgiaSkills.LOGGER.error("could not write multiplayer QA report", e);
        }
        NostalgiaSkills.LOGGER.info("Multiplayer QA [{}]: finished", ROLE);
        if (mc().level != null) {
            mc().level.disconnect();
        }
        mc().stop();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (ROLE.isEmpty()) {
            return;
        }
        if (!built) {
            if (!(mc().screen instanceof TitleScreen)) {
                return; // wait for the game to finish loading
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
                log("FAILED (timeout): " + s.name() + (mc().player != null && st() != null ? "; " + summary() : ""));
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
